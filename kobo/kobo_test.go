package main

import (
	"database/sql"
	"path/filepath"
	"testing"
	"time"
)

// Una base de datos como la del Kobo (solo las columnas que se leen y alguna más), con un libro leyendo, uno terminado, uno sin
// abrir y un capítulo (ContentType 9), que no deben salir.
func baseDePrueba(t *testing.T, conTiempo bool) string {
	t.Helper()
	ruta := filepath.Join(t.TempDir(), "KoboReader.sqlite")
	db, err := sql.Open("sqlite", "file:"+ruta)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	cols := `ContentID TEXT, ContentType TEXT, Title TEXT, BookTitle TEXT, Attribution TEXT, ___PercentRead INTEGER,
		ReadStatus INTEGER, DateLastRead TEXT, LastTimeFinishedReading TEXT, ImageId TEXT`
	if conTiempo {
		cols += ", TimeSpentReading INTEGER"
	}
	if _, err := db.Exec("CREATE TABLE content (" + cols + ")"); err != nil {
		t.Fatal(err)
	}
	filas := [][]any{
		{"file:///mnt/onboard/rojo.epub", "6", "Amanecer rojo", "", "Pierce Brown", 43, 1, "2026-11-15T21:10:05Z", nil, "x"},
		{"file:///mnt/onboard/palomino.epub", "6", "¿Quién mató a Palomino Molero?", "", "Mario Vargas Llosa", 100, 2, "2026-11-10T08:00:00.000", "2026-11-10 08:00:00.000+00:00", "x"},
		{"file:///mnt/onboard/nuevo.epub", "6", "Sin abrir", "", "Nadie", 0, 0, nil, nil, "x"},
		{"file:///mnt/onboard/rojo.epub!cap1", "9", "Capítulo 1", "Amanecer rojo", "", 50, 1, nil, nil, "x"},
		{"kepub-id", "6", "", "Solo BookTitle", "Alguien", 5, 1, "2026-11-01T10:00:00Z", nil, "x"},
	}
	for i, f := range filas {
		q := "INSERT INTO content VALUES (?,?,?,?,?,?,?,?,?,?"
		if conTiempo {
			q += ",?"
			f = append(f, []int{3600, 25000, 0, 0, 60}[i])
		}
		if _, err := db.Exec(q+")", f...); err != nil {
			t.Fatal(err)
		}
	}
	return ruta
}

func TestLeeKobo(t *testing.T) {
	libros, err := leeKobo(baseDePrueba(t, true))
	if err != nil {
		t.Fatal(err)
	}
	if len(libros) != 3 {
		t.Fatalf("3 libros, salen %d: %+v", len(libros), libros)
	}
	rojo, palomino, solo := libros[0], libros[1], libros[2]
	if rojo.Titulo != "Amanecer rojo" || rojo.Autor != "Pierce Brown" || rojo.Porcentaje != 43 || rojo.Estado != 1 || rojo.Segundos != 3600 {
		t.Errorf("rojo: %+v", rojo)
	}
	if !rojo.UltimaLectura.Equal(time.Date(2026, 11, 15, 21, 10, 5, 0, time.UTC)) {
		t.Errorf("rojo ul: %v", rojo.UltimaLectura)
	}
	if palomino.Estado != 2 || !palomino.Terminado.Equal(time.Date(2026, 11, 10, 8, 0, 0, 0, time.UTC)) ||
		!palomino.UltimaLectura.Equal(time.Date(2026, 11, 10, 8, 0, 0, 0, time.UTC)) {
		t.Errorf("palomino: %+v", palomino)
	}
	if solo.Titulo != "Solo BookTitle" {
		t.Errorf("sin Title debe usar BookTitle: %+v", solo)
	}
}

// Un Kobo sin la columna del tiempo (otro firmware) se lee igual, sin tiempo.
func TestLeeKoboSinColumnaDeTiempo(t *testing.T) {
	libros, err := leeKobo(baseDePrueba(t, false))
	if err != nil {
		t.Fatal(err)
	}
	if len(libros) != 3 || libros[0].Segundos != 0 {
		t.Fatalf("%+v", libros)
	}
}
