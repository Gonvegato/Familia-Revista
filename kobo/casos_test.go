package main

import (
	"encoding/json"
	"flag"
	"os"
	"reflect"
	"strings"
	"testing"
	"time"
)

// testdata/casos.json: casos con lo que hay en el Kobo, lo que hay en la cuenta y lo que hay que subir. Los genera este programa
// (go test -run TestCasos -actualiza) y los comprueba también Seshat (KoboTest.kt): así los dos caminos (Wi-Fi y cable) deciden igual.
var actualiza = flag.Bool("actualiza", false, "rehace testdata/casos.json")

type libroCaso struct {
	Titulo     string  `json:"titulo"`
	Autor      string  `json:"autor"`
	Porcentaje float64 `json:"porcentaje"`
	Estado     int     `json:"estado"`
	Ul         int64   `json:"ul"`
	Segundos   int64   `json:"segundos"`
	Terminado  int64   `json:"terminado"`
	Marca      string  `json:"marca,omitempty"`
	Huella     string  `json:"huella,omitempty"`
}

type caso struct {
	Nombre  string          `json:"nombre"`
	Ahora   int64           `json:"ahora"`
	Zona    string          `json:"zona"`
	Disp    string          `json:"disp"`
	Libros  []libroCaso     `json:"libros"`
	Cuenta  map[string]Nodo `json:"cuentaLibros"`
	Tiempo  map[string]Nodo `json:"cuentaTiempo"`
	Cambios map[string]any  `json:"cambios"`
}

func aTiempo(ms int64) time.Time {
	if ms == 0 {
		return time.Time{}
	}
	return time.UnixMilli(ms).UTC()
}

func resuelve(c caso) map[string]any {
	zona, _ := time.LoadLocation(c.Zona)
	var libros []LibroKobo
	remotos := map[string]Remoto{}
	for _, l := range c.Libros {
		lk := LibroKobo{Titulo: l.Titulo, Autor: l.Autor, Porcentaje: l.Porcentaje, Estado: l.Estado, UltimaLectura: aTiempo(l.Ul), Segundos: l.Segundos, Terminado: aTiempo(l.Terminado), Marca: l.Marca, HuellaKepub: l.Huella}
		lk.Kid = buscaKid(lk, c.Cuenta)
		libros = append(libros, lk)
		remotos[lk.Kid] = Remoto{Libro: c.Cuenta[lk.Kid], Tiempo: c.Tiempo[lk.Kid+"~"+c.Disp]}
	}
	p := planea(libros, remotos, time.UnixMilli(c.Ahora), zona, c.Disp)
	r := map[string]any{}
	for k, v := range p.Cambios {
		if strings.HasPrefix(k, "familia_sello/") || strings.HasSuffix(k, "/u") {
			continue
		}
		r[k] = v
	}
	// (por JSON, para comparar igual que al leerlo)
	b, _ := json.Marshal(r)
	var x map[string]any
	_ = json.Unmarshal(b, &x)
	return x
}

func casosBase() []caso {
	ul := time.Date(2026, 11, 15, 23, 30, 0, 0, time.UTC).UnixMilli()
	ahora := time.Date(2026, 11, 16, 9, 0, 0, 0, time.UTC).UnixMilli()
	rojo := libroCaso{Titulo: "Amanecer rojo", Autor: "Pierce Brown", Porcentaje: 43, Estado: 1, Ul: ul, Segundos: 3600}
	fin := rojo
	fin.Estado, fin.Porcentaje, fin.Terminado = 2, 100, ul
	kepub := rojo
	kepub.Marca, kepub.Huella = "OEBPS/c4.xhtml#kobo.17.1:204", "4cb7844f18a4df4f0a441fbcebf2661b"
	alReves := rojo
	alReves.Autor, alReves.Titulo = "Brown, Pierce", "AMANECER ROJO"
	m := func(kv ...any) map[string]any {
		r := map[string]any{}
		for i := 0; i+1 < len(kv); i += 2 {
			r[kv[i].(string)] = kv[i+1]
		}
		return r
	}
	const k = "6955e596a64e0c22d3c8"
	return []caso{
		{Nombre: "libro nuevo", Libros: []libroCaso{rojo, {Titulo: "¿Quién mató a Palomino Molero?", Autor: "Mario Vargas Llosa", Porcentaje: 12.345, Estado: 1, Ul: ul - 5000, Segundos: 90}}},
		{Nombre: "el Kobo va por delante", Libros: []libroCaso{rojo},
			Cuenta: map[string]Nodo{k: {"t": "Amanecer rojo", "a": "Pierce Brown", "estado": "pendiente", "ul": float64(ul - 9000), "fr": 0.1, "m": m("ul", ul-9000, "fr", ul-9000, "estado", ul-9000)}}},
		{Nombre: "Seshat va por delante", Libros: []libroCaso{rojo},
			Cuenta: map[string]Nodo{k: {"t": "Amanecer rojo", "a": "Pierce Brown", "estado": "en_curso", "ul": float64(ul + 9000), "fr": 0.6, "m": m("ul", ul+9000, "fr", ul+9000, "estado", ul-1)}}},
		{Nombre: "borrado en Seshat", Libros: []libroCaso{rojo},
			Cuenta: map[string]Nodo{k: {"t": "Amanecer rojo", "b": float64(5), "m": m("b", 5)}}},
		{Nombre: "terminado (relectura)", Libros: []libroCaso{fin},
			Cuenta: map[string]Nodo{k: {"t": "Amanecer rojo", "estado": "en_curso", "ul": float64(ul - 99999), "fr": 0.9, "fin": []any{float64(ul - 400*86400000)}, "m": m("estado", ul-99999, "fin", ul-400*86400000)}}},
		{Nombre: "terminado ya apuntado", Libros: []libroCaso{fin},
			Cuenta: map[string]Nodo{k: {"t": "Amanecer rojo", "estado": "terminado", "ul": float64(ul), "fr": 1.0, "fin": []any{float64(ul - 3*3600000)}, "m": m("estado", ul-3*3600000, "fin", ul-3*3600000, "ul", ul, "fr", ul)}}},
		{Nombre: "tiempo que crece", Libros: []libroCaso{rojo},
			Cuenta: map[string]Nodo{k: {"t": "Amanecer rojo", "estado": "en_curso", "ul": float64(ul), "fr": 0.43, "m": m("ul", ul, "fr", ul, "estado", ul)}},
			Tiempo: map[string]Nodo{k + "~kobo": {"ref": k, "fuente": "kobo", "total": float64(1800000), "d2026-11-16": float64(600000), "t": "Amanecer rojo", "a": "Pierce Brown", "m": m("total", ahora+50)}}},
		{Nombre: "kepub: por dónde vas, nuevo", Libros: []libroCaso{kepub}},
		{Nombre: "kepub: por dónde vas, cambia", Libros: []libroCaso{kepub},
			Cuenta: map[string]Nodo{k: {"t": "Amanecer rojo", "estado": "en_curso", "ul": float64(ul - 9000), "fr": 0.43, "kc": "OEBPS/c1.xhtml#kobo.3.1", "kh": "4cb7844f18a4df4f0a441fbcebf2661b", "m": m("ul", ul-9000, "fr", ul, "estado", ul, "kc", ul-9000, "kh", ul-9000)}}},
		{Nombre: "kepub: igual que en la cuenta", Libros: []libroCaso{kepub},
			Cuenta: map[string]Nodo{k: {"t": "Amanecer rojo", "estado": "en_curso", "ul": float64(ul), "fr": 0.43, "kc": kepub.Marca, "kh": kepub.Huella, "m": m("ul", ul, "fr", ul, "estado", ul, "kc", ul, "kh", ul)}}},
		{Nombre: "autor escrito al revés", Libros: []libroCaso{alReves},
			Cuenta: map[string]Nodo{"otroref": {"t": "Amanecer rojo", "a": "Pierce Brown", "estado": "pendiente", "m": m("estado", 10)}}},
	}
}

func TestCasos(t *testing.T) {
	ruta := "testdata/casos.json"
	if *actualiza {
		ahora := time.Date(2026, 11, 16, 9, 0, 0, 0, time.UTC).UnixMilli()
		cs := casosBase()
		for i := range cs {
			cs[i].Ahora, cs[i].Zona, cs[i].Disp = ahora, "Europe/Madrid", "kobo"
			// (la cuenta, por JSON: como la verá quien lo lea)
			b, _ := json.Marshal(cs[i])
			_ = json.Unmarshal(b, &cs[i])
			cs[i].Cambios = resuelve(cs[i])
		}
		b, _ := json.MarshalIndent(cs, "", " ")
		if err := os.WriteFile(ruta, b, 0o644); err != nil {
			t.Fatal(err)
		}
	}
	b, err := os.ReadFile(ruta)
	if err != nil {
		t.Fatal(err)
	}
	var cs []caso
	if err := json.Unmarshal(b, &cs); err != nil {
		t.Fatal(err)
	}
	for _, c := range cs {
		if g := resuelve(c); !reflect.DeepEqual(g, c.Cambios) {
			t.Errorf("%s:\n tengo %v\n quiero %v", c.Nombre, g, c.Cambios)
		}
	}
}
