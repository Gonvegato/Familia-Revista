package main

import (
	"strings"
	"testing"
	"time"
)

var madrid, _ = time.LoadLocation("Europe/Madrid")

func libroRojo() LibroKobo {
	return LibroKobo{Titulo: "Amanecer rojo", Autor: "Pierce Brown", Porcentaje: 43, Estado: 1,
		UltimaLectura: time.Date(2026, 11, 15, 23, 30, 0, 0, time.UTC), Segundos: 3600}
}

const kRojo = "6955e596a64e0c22d3c8"

func TestLibroNuevoSeCrea(t *testing.T) {
	ahora := time.Date(2026, 11, 16, 9, 0, 0, 0, time.UTC)
	p := planea([]LibroKobo{libroRojo()}, map[string]Remoto{}, ahora, madrid, "kobo")
	ul := libroRojo().UltimaLectura.UnixMilli()
	quiere := map[string]any{
		"familia/libros/" + kRojo + "/clave":      "libro:amanecer rojo:pierce brown",
		"familia/libros/" + kRojo + "/t":          "Amanecer rojo",
		"familia/libros/" + kRojo + "/estado":     "en_curso",
		"familia/libros/" + kRojo + "/ul":         ul,
		"familia/libros/" + kRojo + "/fr":         0.43,
		"familia/libros/" + kRojo + "/m/fr":       ul,
		"familia/tiempo/" + kRojo + "~kobo/total": int64(3600000),
		"familia/tiempo/" + kRojo + "~kobo/ref":   kRojo,
	}
	for c, v := range quiere {
		if p.Cambios[c] != v {
			t.Errorf("%s = %v, quiero %v", c, p.Cambios[c], v)
		}
	}
	// La primera vez no se reparte el tiempo por días
	for c := range p.Cambios {
		if strings.Contains(c, "/d2026") {
			t.Errorf("no debería poner días la primera vez: %s", c)
		}
	}
	if p.Cambios["familia_sello/libros"] == nil || p.Cambios["familia_sello/tiempo"] == nil || len(p.Resumen) != 1 {
		t.Errorf("faltan sellos o resumen: %v %v", p.Cambios, p.Resumen)
	}
}

func TestSoloLoMasNuevo(t *testing.T) {
	l := libroRojo()
	ul := l.UltimaLectura.UnixMilli()
	// Seshat lo leyó después (más adelante): no se toca nada del libro
	r := Remoto{Libro: Nodo{"t": "Amanecer rojo", "estado": "en_curso", "ul": float64(ul + 1000), "fr": 0.6,
		"m": map[string]any{"ul": float64(ul + 1000), "fr": float64(ul + 1000), "estado": float64(ul - 5)}}}
	p := planea([]LibroKobo{l}, map[string]Remoto{kRojo: r}, time.Now(), madrid, "kobo")
	for c := range p.Cambios {
		if len(c) > 15 && c[:15] == "familia/libros/" {
			t.Errorf("no debería cambiar el libro: %s", c)
		}
	}
	// El Kobo es más nuevo: avanza ul y fr
	r.Libro = Nodo{"estado": "en_curso", "ul": float64(ul - 1000), "fr": 0.2, "m": map[string]any{"ul": float64(ul - 1000), "fr": float64(ul - 1000)}}
	p = planea([]LibroKobo{l}, map[string]Remoto{kRojo: r}, time.Now(), madrid, "kobo")
	if p.Cambios["familia/libros/"+kRojo+"/fr"] != 0.43 || p.Cambios["familia/libros/"+kRojo+"/ul"] != ul {
		t.Errorf("debería avanzar: %v", p.Cambios)
	}
	if _, ok := p.Cambios["familia/libros/"+kRojo+"/estado"]; ok {
		t.Errorf("el estado no ha cambiado")
	}
}

func TestBorradoNoSeResucita(t *testing.T) {
	r := Remoto{Libro: Nodo{"b": float64(1), "m": map[string]any{"b": float64(5)}}, Tiempo: Nodo{"total": float64(3600000)}}
	p := planea([]LibroKobo{libroRojo()}, map[string]Remoto{kRojo: r}, time.Now(), madrid, "kobo")
	if len(p.Cambios) != 0 {
		t.Errorf("%v", p.Cambios)
	}
}

func TestTerminadoAnadeFin(t *testing.T) {
	l := libroRojo()
	l.Estado, l.Porcentaje = 2, 100
	l.Terminado = l.UltimaLectura
	fin := l.Terminado.UnixMilli()
	vieja := float64(fin - 400*24*3600*1000) // lo terminó hace más de un año: es una relectura
	r := Remoto{Libro: Nodo{"estado": "en_curso", "ul": float64(fin - 99999), "fr": 0.9, "fin": []any{vieja},
		"m": map[string]any{"estado": float64(fin - 99999), "fin": vieja}}}
	p := planea([]LibroKobo{l}, map[string]Remoto{kRojo: r}, time.Now(), madrid, "kobo")
	if p.Cambios["familia/libros/"+kRojo+"/estado"] != "terminado" || p.Cambios["familia/libros/"+kRojo+"/fr"] != 1.0 {
		t.Errorf("%v", p.Cambios)
	}
	lista, _ := p.Cambios["familia/libros/"+kRojo+"/fin"].([]any)
	if len(lista) != 2 || lista[1] != fin {
		t.Errorf("fin: %v", lista)
	}
	// Ya tenía ese fin (de Seshat, unas horas antes): no se repite
	r.Libro["fin"] = []any{float64(fin - 3*3600*1000)}
	p = planea([]LibroKobo{l}, map[string]Remoto{kRojo: r}, time.Now(), madrid, "kobo")
	if _, ok := p.Cambios["familia/libros/"+kRojo+"/fin"]; ok {
		t.Errorf("fin repetido: %v", p.Cambios)
	}
}

func TestTiempoCreceEnElDia(t *testing.T) {
	l := libroRojo() // 23:30 UTC del 15 = 00:30 del 16 en Madrid
	ahora := time.Date(2026, 11, 16, 9, 0, 0, 0, time.UTC)
	r := Remoto{Libro: Nodo{"estado": "en_curso", "ul": float64(l.UltimaLectura.UnixMilli()), "fr": 0.43},
		Tiempo: Nodo{"ref": kRojo, "fuente": "kobo", "total": float64(1800000), "d2026-11-16": float64(600000), "t": "Amanecer rojo", "a": "Pierce Brown",
			"m": map[string]any{"total": float64(ahora.UnixMilli() + 50)}}}
	p := planea([]LibroKobo{l}, map[string]Remoto{kRojo: r}, ahora, madrid, "kobo")
	base := "familia/tiempo/" + kRojo + "~kobo/"
	if p.Cambios[base+"total"] != int64(3600000) || p.Cambios[base+"d2026-11-16"] != int64(600000+1800000) {
		t.Errorf("%v", p.Cambios)
	}
	if p.Cambios[base+"m/total"] != ahora.UnixMilli()+51 {
		t.Errorf("la hora debe ser mayor que la guardada: %v", p.Cambios[base+"m/total"])
	}
	if _, ok := p.Cambios[base+"t"]; ok {
		t.Errorf("lo que no cambia no se sube")
	}
	// Sin tiempo nuevo, nada
	r.Tiempo["total"] = float64(3600000)
	p = planea([]LibroKobo{l}, map[string]Remoto{kRojo: r}, ahora, madrid, "kobo")
	if len(p.Cambios) != 0 {
		t.Errorf("%v", p.Cambios)
	}
}

func TestBuscaKid(t *testing.T) {
	todos := map[string]Nodo{
		"aaa": {"t": "Amanecer rojo", "a": "Pierce Brown"},
		"bbb": {"t": "Poemas", "a": "Ana"},
		"ccc": {"t": "Poemas", "a": "Luis"},
		"ddd": {"t": "Borrado", "a": "X", "b": float64(3)},
	}
	casos := []struct {
		t, a, quiere string
	}{
		{"Amanecer Rojo", "Brown, Pierce", "aaa"},
		{"Amanecer rojo", "", "aaa"},
		{"Poemas", "", kid(claveDe("Poemas", ""))}, // dos posibles: libro nuevo
		{"Borrado", "X", kid(claveDe("Borrado", "X"))},
		{"Otro", "Y", kid(claveDe("Otro", "Y"))},
	}
	for _, c := range casos {
		if g := buscaKid(LibroKobo{Titulo: c.t, Autor: c.a}, todos); g != c.quiere {
			t.Errorf("%s / %s: %s, quiero %s", c.t, c.a, g, c.quiere)
		}
	}
}
