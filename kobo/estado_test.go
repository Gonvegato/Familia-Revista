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

// El tiempo leído como lo lleva el programa: lo nuevo, en su día; si el Kobo vuelve a cero, no se pierde; lo ya subido, fuera.
func TestRegistraContadores(t *testing.T) {
	madrid, _ := time.LoadLocation("Europe/Madrid")
	e := cargaEstado(filepath.Join(t.TempDir(), "no-esta.json"))
	l := LibroKobo{Titulo: "Amanecer rojo", Autor: "Pierce Brown", Segundos: 3600, UltimaLectura: time.Date(2026, 11, 14, 20, 0, 0, 0, time.UTC)}
	ahora := time.Date(2026, 11, 16, 9, 0, 0, 0, time.UTC)
	ls := []LibroKobo{l}
	e.registra(ls, madrid, ahora)
	if ls[0].Total != 3600000 || len(ls[0].Dias) != 0 {
		t.Fatalf("la primera vez, solo el total: %+v", ls[0])
	}
	// Lee 10 min el 15
	l.Segundos, l.UltimaLectura = 4200, time.Date(2026, 11, 15, 21, 0, 0, 0, time.UTC)
	ls = []LibroKobo{l}
	e.registra(ls, madrid, ahora)
	if ls[0].Total != 4200000 || ls[0].Dias["2026-11-15"] != 600000 {
		t.Fatalf("%+v", ls[0])
	}
	// El Kobo vuelve a cero (el libro se borró y se volvió a copiar) y lee 2 min más el 16 (00:30 en Madrid)
	l.Segundos, l.UltimaLectura = 120, time.Date(2026, 11, 15, 23, 30, 0, 0, time.UTC)
	ls = []LibroKobo{l}
	e.registra(ls, madrid, ahora)
	if ls[0].Total != 4200000+120000 || ls[0].Dias["2026-11-16"] != 120000 || ls[0].Dias["2026-11-15"] != 600000 {
		t.Fatalf("tras volver a cero: %+v", ls[0])
	}
	// Subido: los días pendientes, fuera; el total sigue
	e.subidos(ls)
	ls = []LibroKobo{l}
	e.registra(ls, madrid, ahora)
	if ls[0].Total != 4320000 || len(ls[0].Dias) != 0 {
		t.Fatalf("tras subir: %+v", ls[0])
	}
	// Una lectura con la hora del Kobo en el futuro va al día de hoy
	l.Segundos, l.UltimaLectura = 180, ahora.Add(72*time.Hour)
	ls = []LibroKobo{l}
	e.registra(ls, madrid, ahora)
	if ls[0].Dias["2026-11-16"] != 60000 {
		t.Fatalf("futuro: %+v", ls[0].Dias)
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
	if e2.Huellas["x"] != "h" || e2.Contadores[kid(claveDe(l.Titulo, l.Autor))].S != 180 {
		t.Fatalf("%+v", e2)
	}
	e2.deCuenta("u2", "https://a")
	if len(e2.Huellas) != 0 || e2.Contadores[kid(claveDe(l.Titulo, l.Autor))] == nil {
		t.Fatalf("otra cuenta: %+v", e2)
	}
	// El formato de antes (un mapa de huellas) se toma por vacío
	os.WriteFile(f, []byte(`{"abc":"123"}`), 0o644)
	if e3 := cargaEstado(f); len(e3.Huellas) != 0 {
		t.Fatalf("formato viejo: %+v", e3)
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
