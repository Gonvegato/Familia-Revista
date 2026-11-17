package main

import (
	"database/sql"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	_ "modernc.org/sqlite"
)

// LibroKobo es lo que el Kobo sabe de un libro que has abierto: de la tabla `content` de KoboReader.sqlite (las filas con
// ContentType = 6, el libro entero; las de los capítulos son otras).
type LibroKobo struct {
	ID            string    // ContentID (en los libros que copias tú, la ruta: file:///mnt/onboard/...)
	Titulo        string    // Title (o BookTitle)
	Autor         string    // Attribution
	Porcentaje    float64   // ___PercentRead, de 0 a 100
	Estado        int       // ReadStatus: 0 sin abrir, 1 leyendo, 2 terminado
	UltimaLectura time.Time // DateLastRead
	Segundos      int64     // TimeSpentReading: el tiempo leído en total, en segundos
	Terminado     time.Time // LastTimeFinishedReading

	Kid string // el libro de la cuenta al que corresponde (lo pone buscaKid; vacío: el de su clave)
}

// leeKobo lee los libros de la base de datos del Kobo. No la toca: la copia (con sus archivos -wal y -shm, donde puede estar lo
// último) a una carpeta temporal y lee la copia. Kobo ha cambiado las columnas con los años: se pregunta cuáles tiene y se usan solo
// esas (las que falten cuentan como vacías), así una actualización del Kobo no lo rompe.
func leeKobo(ruta string) ([]LibroKobo, error) {
	tmp, err := os.MkdirTemp("", "seshat-kobo-")
	if err != nil {
		return nil, err
	}
	defer os.RemoveAll(tmp)
	copia := filepath.Join(tmp, "KoboReader.sqlite")
	if err := copiaArchivo(ruta, copia); err != nil {
		return nil, fmt.Errorf("no se pudo copiar la base de datos del Kobo: %w", err)
	}
	for _, ext := range []string{"-wal", "-shm"} {
		if _, err := os.Stat(ruta + ext); err == nil {
			_ = copiaArchivo(ruta+ext, copia+ext)
		}
	}
	db, err := sql.Open("sqlite", "file:"+copia)
	if err != nil {
		return nil, err
	}
	defer db.Close()

	cols := map[string]bool{}
	filas, err := db.Query("PRAGMA table_info(content)")
	if err != nil {
		return nil, fmt.Errorf("la base de datos del Kobo no tiene la tabla de libros: %w", err)
	}
	for filas.Next() {
		var cid, notnull, pk int
		var nombre, tipo string
		var def sql.NullString
		if err := filas.Scan(&cid, &nombre, &tipo, &notnull, &def, &pk); err == nil {
			cols[strings.ToLower(nombre)] = true
		}
	}
	filas.Close()
	if !cols["contentid"] || !cols["contenttype"] {
		return nil, fmt.Errorf("la tabla de libros del Kobo no es como se esperaba")
	}
	col := func(n string) string {
		if cols[strings.ToLower(n)] {
			return n
		}
		return "NULL"
	}
	titulo := "NULL"
	switch {
	case cols["title"] && cols["booktitle"]:
		titulo = "COALESCE(NULLIF(Title, ''), BookTitle)"
	case cols["title"]:
		titulo = "Title"
	case cols["booktitle"]:
		titulo = "BookTitle"
	}
	q := fmt.Sprintf(`SELECT ContentID, %s, %s, %s, %s, %s, %s, %s FROM content WHERE ContentType = 6`,
		titulo, col("Attribution"), col("___PercentRead"), col("ReadStatus"), col("DateLastRead"), col("TimeSpentReading"), col("LastTimeFinishedReading"))
	filas, err = db.Query(q)
	if err != nil {
		return nil, fmt.Errorf("no se pudieron leer los libros del Kobo: %w", err)
	}
	defer filas.Close()
	var libros []LibroKobo
	for filas.Next() {
		var id string
		var t, a, ul, fin sql.NullString
		var pct, est, seg sql.NullFloat64
		if err := filas.Scan(&id, &t, &a, &pct, &est, &ul, &seg, &fin); err != nil {
			continue
		}
		l := LibroKobo{ID: id, Titulo: strings.TrimSpace(t.String), Autor: strings.TrimSpace(a.String), Porcentaje: pct.Float64,
			Estado: int(est.Float64), UltimaLectura: fecha(ul.String), Segundos: int64(seg.Float64), Terminado: fecha(fin.String)}
		// Solo lo que has abierto alguna vez (lo demás no tiene nada que contar) y con título (sin él no se sabe qué libro es)
		if l.Titulo == "" || (l.Estado == 0 && l.Porcentaje <= 0 && l.Segundos <= 0) {
			continue
		}
		if l.Porcentaje < 0 {
			l.Porcentaje = 0
		}
		if l.Porcentaje > 100 {
			l.Porcentaje = 100
		}
		libros = append(libros, l)
	}
	return libros, filas.Err()
}

func copiaArchivo(de, a string) error {
	f, err := os.Open(de)
	if err != nil {
		return err
	}
	defer f.Close()
	g, err := os.Create(a)
	if err != nil {
		return err
	}
	if _, err := io.Copy(g, f); err != nil {
		g.Close()
		return err
	}
	return g.Close()
}

// fecha entiende las fechas del Kobo («2024-01-15T14:30:00Z», «2024-01-15T14:30:00.000», «2024-01-15 14:30:00.000+00:00»…).
// Sin zona, son UTC (el Kobo las guarda así). Si no se entiende, la fecha vacía.
func fecha(s string) time.Time {
	s = strings.TrimSpace(s)
	if s == "" {
		return time.Time{}
	}
	for _, f := range []string{time.RFC3339Nano, "2006-01-02T15:04:05.999999999", "2006-01-02 15:04:05.999999999Z07:00",
		"2006-01-02 15:04:05.999999999", "2006-01-02T15:04:05.999999999Z0700", "2006-01-02"} {
		if t, err := time.Parse(f, s); err == nil {
			return t.UTC()
		}
	}
	return time.Time{}
}
