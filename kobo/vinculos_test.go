package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// Vincular (sin el programa de NickelInkSync: uno de mentira que apunta lo que le piden y deja la tabla como haría el de verdad).
// La prueba con el de verdad está en prueba-nis/ (prueba.js).
func TestVinculaKepubs(t *testing.T) {
	raiz := t.TempDir()
	ds := filepath.Join(raiz, ".adds", "seshat")
	os.MkdirAll(filepath.Join(ds, "originales"), 0o755)
	os.MkdirAll(filepath.Join(raiz, ".adds", "NickelInkSync"), 0o755)
	os.WriteFile(filepath.Join(raiz, ".adds", "NickelInkSync", "nickelinksync"), []byte("#!/bin/sh\n"), 0o755)
	os.MkdirAll(filepath.Join(raiz, "Libros"), 0o755)
	os.WriteFile(filepath.Join(raiz, "Libros", "rojo.kepub.epub"), []byte("kepub rojo"), 0o644)
	os.WriteFile(filepath.Join(ds, "originales", "abc.orig"), []byte("epub rojo"), 0o644)
	lista := listaVinculos{Vinculos: []vinculo{
		{Kepub: "file:///mnt/onboard/Libros/rojo.kepub.epub", Original: "abc.orig", Titulo: "Rojo"},
		{Kepub: "file:///mnt/onboard/Libros/falta.kepub.epub", Original: "nohay.orig", Titulo: "Falta"},
		{Kepub: "file:///mnt/onboard/Libros/x.kepub.epub", Original: "../../etc/passwd", Titulo: "Malo"},
	}}
	b, _ := json.Marshal(lista)
	os.WriteFile(filepath.Join(ds, "vinculos.json"), b, 0o644)

	var pedidos []string
	antes := ejecuta
	defer func() { ejecuta = antes }()
	ejecuta = func(cli string, entorno []string, args ...string) (string, error) {
		pedidos = append(pedidos, strings.Join(args, " "))
		if args[0] == "device" {
			h, _ := huellaKOReader(filepath.Join(raiz, "Libros", "rojo.kepub.epub"))
			os.MkdirAll(filepath.Join(raiz, ".adds", "NickelInkSync", "tables"), 0o755)
			os.WriteFile(filepath.Join(raiz, ".adds", "NickelInkSync", "tables", h+".json"), []byte("{}"), 0o644)
		}
		return "", nil
	}
	n, err := vinculaKepubs(raiz)
	if n != 1 || err == nil || !strings.Contains(err.Error(), "Falta") {
		t.Fatalf("n=%d err=%v", n, err)
	}
	if len(pedidos) != 2 || !strings.HasPrefix(pedidos[0], "map add --device "+raiz+" --content-id file:///mnt/onboard/Libros/rojo.kepub.epub --epub ") ||
		!strings.HasPrefix(pedidos[1], "device prepare --device "+raiz+" --library ") {
		t.Errorf("pedidos: %q", pedidos)
	}
	// Lo ya vinculado no se repite
	pedidos = nil
	if n, _ := vinculaKepubs(raiz); n != 0 || len(pedidos) != 0 {
		t.Errorf("repite: %d %q", n, pedidos)
	}
	// Sin NickelInkSync, error claro
	os.Remove(filepath.Join(raiz, ".adds", "NickelInkSync", "nickelinksync"))
	if _, err := vinculaKepubs(raiz); err == nil || !strings.Contains(err.Error(), "NickelInkSync") {
		t.Errorf("sin NickelInkSync: %v", err)
	}
}
