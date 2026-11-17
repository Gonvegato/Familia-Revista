package main

import (
	"crypto/md5"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

// NickelInkSync (plugin del Kobo, MIT: github.com/dkuester/NickelInkSync) lleva la posición de cada libro entre el Kobo y un servidor
// KOSync, y Seshat la lee de ahí. Solo manda la posición de los libros que estén en su índice (`.adds/NickelInkSync/library.json`, que
// normalmente hace un ordenador con «device prepare»): aquí se rehace solo con los EPUB del Kobo, cada vez que cambian, para que un
// libro nuevo se sincronice sin tener que enchufar el Kobo a nada. Un kepub no entra (su archivo no es el de Seshat).

type libroIndice struct {
	Hash    string   `json:"hash"`
	Title   string   `json:"title,omitempty"`
	Authors []string `json:"authors,omitempty"`
}

type indice struct {
	Format  int           `json:"format"`
	Created string        `json:"created"`
	Books   []libroIndice `json:"books"`
}

// huellaKOReader es la huella con la que KOSync conoce un libro (la «binary» de KOReader): MD5 de trozos de 1 KiB del archivo en 0,
// 1 KiB, 4 KiB, 16 KiB… (cada uno cuatro veces el anterior, hasta 1 GiB), parando en el primero que pase del final.
func huellaKOReader(ruta string) (string, error) {
	f, err := os.Open(ruta)
	if err != nil {
		return "", err
	}
	defer f.Close()
	st, err := f.Stat()
	if err != nil {
		return "", err
	}
	h := md5.New()
	buf := make([]byte, 1024)
	offs := []int64{0}
	for i := 0; i <= 10; i++ {
		offs = append(offs, int64(1024)<<(2*i))
	}
	for _, o := range offs {
		if o >= st.Size() {
			break
		}
		n, err := f.ReadAt(buf, o)
		if err != nil && err != io.EOF {
			return "", err
		}
		h.Write(buf[:n])
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

// rutaDeContenido: el archivo de un libro copiado al Kobo (ContentID «file:///mnt/onboard/…»), dentro de [raiz] (la memoria del Kobo).
func rutaDeContenido(id, raiz string) string {
	p, ok := strings.CutPrefix(id, "file:///mnt/onboard/")
	if !ok {
		return ""
	}
	return filepath.Join(raiz, filepath.FromSlash(p))
}

// actualizaIndiceNIS rehace el índice de NickelInkSync si está instalado (existe su carpeta) y ha cambiado algo. [raiz] es la
// memoria del Kobo (/mnt/onboard). Las huellas ya calculadas se guardan en [cache] (no se vuelve a leer un archivo que no ha cambiado).
func actualizaIndiceNIS(rutaBD, raiz, cache string) (cambiado bool, n int, err error) {
	dir := filepath.Join(raiz, ".adds", "NickelInkSync")
	if st, err := os.Stat(dir); err != nil || !st.IsDir() {
		return false, 0, nil
	}
	tmp, err := os.MkdirTemp("", "seshat-nis-")
	if err != nil {
		return false, 0, err
	}
	defer os.RemoveAll(tmp)
	copia := filepath.Join(tmp, "KoboReader.sqlite")
	if err := copiaArchivo(rutaBD, copia); err != nil {
		return false, 0, err
	}
	for _, ext := range []string{"-wal", "-shm"} {
		if _, err := os.Stat(rutaBD + ext); err == nil {
			_ = copiaArchivo(rutaBD+ext, copia+ext)
		}
	}
	db, err := sql.Open("sqlite", "file:"+copia)
	if err != nil {
		return false, 0, err
	}
	defer db.Close()
	filas, err := db.Query(`SELECT ContentID, COALESCE(Title, ''), COALESCE(Attribution, '') FROM content WHERE ContentType = 6 AND ContentID LIKE 'file:///mnt/onboard/%'`)
	if err != nil {
		return false, 0, err
	}
	type fila struct{ id, t, a string }
	var fs []fila
	for filas.Next() {
		var f fila
		if filas.Scan(&f.id, &f.t, &f.a) == nil {
			fs = append(fs, f)
		}
	}
	filas.Close()

	viejas := map[string]string{} // «ruta|tamaño|hora» → huella
	if b, err := os.ReadFile(cache); err == nil {
		_ = json.Unmarshal(b, &viejas)
	}
	nuevas := map[string]string{}
	var libros []libroIndice
	vistos := map[string]bool{}
	for _, f := range fs {
		low := strings.ToLower(f.id)
		if !strings.HasSuffix(low, ".epub") || strings.HasSuffix(low, ".kepub.epub") {
			continue
		}
		ruta := rutaDeContenido(f.id, raiz)
		st, err := os.Stat(ruta)
		if err != nil {
			continue
		}
		k := fmt.Sprintf("%s|%d|%d", ruta, st.Size(), st.ModTime().Unix())
		h := viejas[k]
		if h == "" {
			if h, err = huellaKOReader(ruta); err != nil {
				continue
			}
		}
		nuevas[k] = h
		if vistos[h] {
			continue
		}
		vistos[h] = true
		l := libroIndice{Hash: h, Title: strings.TrimSpace(f.t)}
		if a := strings.TrimSpace(f.a); a != "" {
			l.Authors = []string{a}
		}
		libros = append(libros, l)
	}
	if b, err := json.Marshal(nuevas); err == nil {
		_ = os.WriteFile(cache, b, 0o644)
	}
	n = len(libros)

	// Lo que ya hubiera en el índice se queda (si lo preparó el ordenador con NickelInkSync, sus originales siguen ahí): solo se añade
	destino := filepath.Join(dir, "library.json")
	var actual indice
	if b, err := os.ReadFile(destino); err == nil {
		if json.Unmarshal(b, &actual) != nil || actual.Format > 1 {
			return false, n, fmt.Errorf("library.json de NickelInkSync es de otra versión: no se toca")
		}
	}
	for _, l := range actual.Books {
		if !vistos[l.Hash] {
			vistos[l.Hash] = true
			libros = append(libros, l)
		}
	}
	sort.Slice(libros, func(i, j int) bool { return libros[i].Hash < libros[j].Hash })
	if actual.Format == 1 && mismosLibros(actual.Books, libros) {
		return false, n, nil
	}
	if libros == nil {
		libros = []libroIndice{}
	}
	b, _ := json.MarshalIndent(indice{Format: 1, Created: time.Now().UTC().Format("2006-01-02T15:04:05Z"), Books: libros}, "", "  ")
	t := destino + ".nuevo"
	if err := os.WriteFile(t, b, 0o644); err != nil {
		return false, 0, err
	}
	return true, n, os.Rename(t, destino)
}

func mismosLibros(a, b []libroIndice) bool {
	if len(a) != len(b) {
		return false
	}
	ha := make([]string, len(a))
	for i := range a {
		ha[i] = a[i].Hash
	}
	sort.Strings(ha)
	for i := range b {
		if ha[i] != b[i].Hash {
			return false
		}
	}
	return true
}
