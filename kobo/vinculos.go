package main

import (
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

// Vincular kepub con EPUB (SesKobo): para llevar la posición de un kepub del Kobo, NickelInkSync necesita emparejarlo con su EPUB
// original (el que lee SesKobo) y una tabla para traducir las posiciones de uno a otro. Eso lo hace su programa con «map add» y
// «device prepare», normalmente en un ordenador. Aquí lo hace el propio Kobo: SesKobo deja el EPUB original en
// `.adds/seshat/originales/<huella>.orig` (con esa extensión el Kobo no lo toma por un libro y no sale repetido) y la lista en
// `.adds/seshat/vinculos.json`; este programa empareja los que falten y prepara el Kobo.

type vinculo struct {
	Kepub    string `json:"kepub"`    // ContentID del kepub en el Kobo («file:///mnt/onboard/…kepub.epub»)
	Original string `json:"original"` // el EPUB original, dentro de `originales` («<huella>.orig»)
	Titulo   string `json:"titulo,omitempty"`
}

type listaVinculos struct {
	Vinculos []vinculo `json:"vinculos"`
}

// ejecuta lanza el programa de NickelInkSync con su carpeta de datos (sus emparejamientos) en `.adds/seshat/nis`. (Se cambia en
// las pruebas.)
var ejecuta = func(cli string, entorno []string, args ...string) (string, error) {
	c := exec.Command(cli, args...)
	c.Env = append(os.Environ(), entorno...)
	c.Dir = "/"
	out, err := c.CombinedOutput()
	return strings.TrimSpace(string(out)), err
}

// vinculaKepubs empareja los kepub de `vinculos.json` que aún no lo estén y, si ha emparejado alguno, prepara el Kobo (las tablas
// de traducción y el índice). [raiz] es la memoria del Kobo. Devuelve cuántos ha emparejado ahora.
func vinculaKepubs(raiz string) (int, error) {
	dirS := filepath.Join(raiz, ".adds", "seshat")
	b, err := os.ReadFile(filepath.Join(dirS, "vinculos.json"))
	if err != nil {
		return 0, nil // (nada que vincular)
	}
	var lista listaVinculos
	if err := json.Unmarshal(b, &lista); err != nil {
		return 0, fmt.Errorf("vinculos.json está mal: %w", err)
	}
	cli := filepath.Join(raiz, ".adds", "NickelInkSync", "nickelinksync")
	if _, err := os.Stat(cli); err != nil {
		return 0, fmt.Errorf("falta NickelInkSync (prepara el Kobo desde SesKobo)")
	}
	originales := filepath.Join(dirS, "originales")
	datos := filepath.Join(dirS, "nis")
	_ = os.MkdirAll(datos, 0o755)
	entorno := []string{"NICKELINKSYNC_DATA_DIR=" + datos, "HOME=" + datos, "NICKELINKSYNC_CONFIG=" + filepath.Join(raiz, ".adds", "NickelInkSync", "config.ini")}

	hechos := map[string]bool{}
	fHechos := filepath.Join(dirS, "vinculos-hechos.json")
	if b, err := os.ReadFile(fHechos); err == nil {
		_ = json.Unmarshal(b, &hechos)
	}
	n := 0
	var fallos []string
	var nuevos []vinculo
	for _, v := range lista.Vinculos {
		k := v.Kepub + "|" + v.Original
		if hechos[k] || v.Kepub == "" || v.Original == "" || strings.ContainsAny(v.Original, `/\`) {
			continue
		}
		epub := filepath.Join(originales, v.Original)
		if _, err := os.Stat(epub); err != nil {
			fallos = append(fallos, v.Titulo+": falta su EPUB")
			continue
		}
		out, err := ejecuta(cli, entorno, "map", "add", "--device", raiz, "--content-id", v.Kepub, "--epub", epub)
		if err != nil {
			fallos = append(fallos, fmt.Sprintf("%s: %v %s", v.Titulo, err, out))
			continue
		}
		nuevos = append(nuevos, v)
	}
	if len(nuevos) > 0 {
		// Las tablas de traducción de lo emparejado (y el índice: lo completa luego actualizaIndiceNIS con los EPUB del Kobo). Acaba con
		// error si algún libro de la base de datos del Kobo ya no tiene archivo, aunque lo nuestro esté bien: lo que cuenta es que
		// esté la tabla de cada kepub
		out, err := ejecuta(cli, entorno, "device", "prepare", "--device", raiz, "--library", originales)
		for _, v := range nuevos {
			h, e := huellaKOReader(rutaDeContenido(v.Kepub, raiz))
			if _, e2 := os.Stat(filepath.Join(raiz, ".adds", "NickelInkSync", "tables", h+".json")); e != nil || e2 != nil {
				fallos = append(fallos, fmt.Sprintf("%s: sin tabla (%v %s)", v.Titulo, err, out))
				continue
			}
			hechos[v.Kepub+"|"+v.Original] = true
			n++
		}
		if b, err := json.Marshal(hechos); err == nil {
			_ = os.WriteFile(fHechos, b, 0o644)
		}
	}
	if len(fallos) > 0 {
		return n, fmt.Errorf("sin vincular: %s", strings.Join(fallos, "; "))
	}
	return n, nil
}
