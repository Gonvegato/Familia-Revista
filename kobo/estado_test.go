package main

import (
	"errors"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// El tiempo leído como lo lleva el programa: por entrada del Kobo, lo nuevo en su día (todo lo de cada día, no solo lo pendiente); si
// el Kobo vuelve a cero, no se pierde; si una entrada desaparece, su tiempo sigue contando en su libro.
func TestRegistraContadores(t *testing.T) {
	madrid, _ := time.LoadLocation("Europe/Madrid")
	e := cargaEstado(filepath.Join(t.TempDir(), "no-esta.json"))
	l := LibroKobo{ID: "rojo", Titulo: "Amanecer rojo", Autor: "Pierce Brown", Segundos: 3600, UltimaLectura: time.Date(2026, 11, 14, 20, 0, 0, 0, time.UTC)}
	ahora := time.Date(2026, 11, 16, 9, 0, 0, 0, time.UTC)
	tiempo := func(es ...LibroKobo) LibroKobo {
		e.registra(es, madrid, ahora)
		g := agrupa(es, func(l LibroKobo) string { return kid(claveDe(l.Titulo, l.Autor)) })
		e.tiempos(g)
		return g[0]
	}
	if x := tiempo(l); x.Total != 3600000 || len(x.Dias) != 0 || !x.PorDias {
		t.Fatalf("la primera vez, solo el total: %+v", x)
	}
	// Lee 10 min el 15
	l.Segundos, l.UltimaLectura = 4200, time.Date(2026, 11, 15, 21, 0, 0, 0, time.UTC)
	if x := tiempo(l); x.Total != 4200000 || x.Dias["2026-11-15"] != 600000 {
		t.Fatalf("%+v", x)
	}
	// El Kobo vuelve a cero (el libro se borró y se volvió a copiar) y lee 2 min más el 16 (00:30 en Madrid)
	l.Segundos, l.UltimaLectura = 120, time.Date(2026, 11, 15, 23, 30, 0, 0, time.UTC)
	if x := tiempo(l); x.Total != 4320000 || x.Dias["2026-11-16"] != 120000 || x.Dias["2026-11-15"] != 600000 {
		t.Fatalf("tras volver a cero: %+v", x)
	}
	// Una lectura con la hora del Kobo en el futuro va al día de hoy
	l.Segundos, l.UltimaLectura = 180, ahora.Add(72*time.Hour)
	if x := tiempo(l); x.Dias["2026-11-16"] != 180000 {
		t.Fatalf("futuro: %+v", x.Dias)
	}
	// Aparece la muestra (otra entrada del mismo libro) con tiempo de antes: solo suma al total; se lee en ella, cuenta en su día; se
	// borra: su tiempo sigue en el libro
	m := LibroKobo{ID: "muestra", Titulo: "Amanecer rojo", Autor: "Pierce Brown", Segundos: 600, UltimaLectura: l.UltimaLectura}
	if x := tiempo(l, m); x.Total != 4380000+600000 || x.Dias["2026-11-16"] != 180000 {
		t.Fatalf("con la muestra: %+v", x)
	}
	m.Segundos = 660
	if x := tiempo(l, m); x.Total != 4380000+660000 || x.Dias["2026-11-16"] != 240000 {
		t.Fatalf("leyendo la muestra: %+v", x)
	}
	if x := tiempo(l); x.Total != 4380000+660000 || x.Dias["2026-11-16"] != 240000 {
		t.Fatalf("sin la muestra: %+v", x)
	}
	// Se guarda y se vuelve a leer igual; con otra cuenta, lo enviado se olvida (los contadores no)
	f := filepath.Join(t.TempDir(), "enviado.json")
	e.deCuenta("u1", "https://a")
	e.Huellas["x"] = "h"
	if err := e.guarda(f); err != nil {
		t.Fatal(err)
	}
	e2 := cargaEstado(f)
	e2.deCuenta("u1", "https://a")
	if e2.Huellas["x"] != "h" || e2.Entradas["rojo"].S != 180 || e2.Entradas["muestra"].S != 660 {
		t.Fatalf("%+v", e2)
	}
	e2.deCuenta("u2", "https://a")
	if len(e2.Huellas) != 0 || e2.Entradas["rojo"] == nil {
		t.Fatalf("otra cuenta: %+v", e2)
	}
	// Un formato que no entiende se toma por vacío
	os.WriteFile(f, []byte(`{"abc":"123"}`), 0o644)
	if e3 := cargaEstado(f); len(e3.Huellas) != 0 || len(e3.Entradas) != 0 {
		t.Fatalf("formato viejo: %+v", e3)
	}
	// Los días de hace mucho se olvidan (ya están en la cuenta)
	e.Entradas["rojo"].Dias["2024-01-01"] = 5
	e.registra(nil, madrid, ahora)
	if _, ok := e.Entradas["rojo"].Dias["2024-01-01"]; ok {
		t.Fatal("no se olvida")
	}
}

func TestCerrojo(t *testing.T) {
	t.Setenv("TMPDIR", t.TempDir())
	soltar, err := cerrojo("prueba.cerrojo")
	if err != nil {
		t.Fatal(err)
	}
	if err := conCerrojo(func() error { return nil }); err != nil {
		t.Fatal("otro cerrojo no debería estorbar: ", err)
	}
	if _, err := cerrojo("prueba.cerrojo"); !errors.Is(err, errOcupado) {
		t.Fatalf("debería estar ocupado: %v", err)
	}
	soltar()
	if s, err := cerrojo("prueba.cerrojo"); err != nil {
		t.Fatalf("suelto: %v", err)
	} else {
		s()
	}
}

// Un error de red no lleva el pase de la cuenta (acabaría en el registro, que se ve en el Kobo).
func TestErroresSinPase(t *testing.T) {
	c := &Cuenta{BaseDatos: "http://127.0.0.1:1", Uid: "u", pase: "PASE-SECRETO", cliente: &http.Client{Timeout: time.Second}}
	_, err := c.lee("/users/u/familia/libros")
	if err == nil || strings.Contains(err.Error(), "PASE-SECRETO") || strings.Contains(err.Error(), "auth=") {
		t.Fatalf("%v", err)
	}
	err = c.sube(map[string]any{"a": 1})
	if err == nil || strings.Contains(err.Error(), "PASE-SECRETO") {
		t.Fatalf("%v", err)
	}
}

func TestEscribeSeguroYMontado(t *testing.T) {
	d := t.TempDir()
	f := filepath.Join(d, "a.json")
	os.WriteFile(f, []byte("viejo"), 0o644)
	if err := escribeSeguro(f, []byte("nuevo"), 0o644); err != nil {
		t.Fatal(err)
	}
	if b, _ := os.ReadFile(f); string(b) != "nuevo" {
		t.Fatal(string(b))
	}
	if _, err := os.Stat(f + ".nuevo"); err == nil {
		t.Fatal("se ha quedado el temporal")
	}
	// Una memoria del Kobo que no está montada: no se escribe
	antes := memoriaKobo
	memoriaKobo = filepath.Join(d, "no-montada")
	defer func() { memoriaKobo = antes }()
	if err := escribeSeguro(filepath.Join(memoriaKobo, "x.json"), []byte("x"), 0o644); err == nil {
		t.Fatal("no debería escribir en una memoria sin montar")
	}
}
