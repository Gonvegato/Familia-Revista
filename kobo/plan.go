package main

import (
	"fmt"
	"math"
	"sort"
	"strings"
	"time"
)

// Nodo es una cosa de la cuenta tal como llega (`familia/<colección>/<ref>`): sus campos, `m` (la hora de cada campo) y `u`.
type Nodo map[string]any

// Lo que dice la cuenta de los libros del Kobo antes de cambiar nada: su libro (`libros/<kid>`) y su tiempo en el Kobo
// (`tiempo/<kid>~<dispositivo>`). Un nodo que no está es nil.
type Remoto struct {
	Libro  Nodo
	Tiempo Nodo
}

// Plan es lo que se sube: un PATCH a `/users/<uid>` (ruta → valor) y, para el registro, una línea por libro.
type Plan struct {
	Cambios map[string]any
	Resumen []string
}

const (
	estadoPendiente = "pendiente"
	estadoLeyendo   = "en_curso"
	estadoTerminado = "terminado"
)

func ms(t time.Time) int64 {
	if t.IsZero() {
		return 0
	}
	return t.UnixMilli()
}

func numero(v any) float64 {
	switch x := v.(type) {
	case float64:
		return x
	case int64:
		return float64(x)
	case int:
		return float64(x)
	}
	return 0
}

func texto(v any) string { s, _ := v.(string); return s }

// horaDe es la hora con la que se cambió [campo] por última vez (la `m` del nodo); 0 si nunca.
func horaDe(n Nodo, campo string) int64 {
	m, _ := n["m"].(map[string]any)
	return int64(numero(m[campo]))
}

func maxHora(n Nodo) int64 {
	m, _ := n["m"].(map[string]any)
	var x int64
	for _, v := range m {
		if h := int64(numero(v)); h > x {
			x = h
		}
	}
	return x
}

// planea decide qué subir: para cada libro del Kobo, los campos de su libro en la cuenta que el Kobo sabe de más tarde que lo que
// ya hay (la cuenta solo acepta un campo si su hora es más reciente que la guardada: si lo has leído después en Seshat, gana Seshat),
// y su tiempo en el Kobo. Pura: no habla con nadie (así se prueba).
func planea(libros []LibroKobo, remotos map[string]Remoto, ahora time.Time, zona *time.Location, disp string) Plan {
	p := Plan{Cambios: map[string]any{}}
	ahoraMs := ms(ahora)
	sv := map[string]any{".sv": "timestamp"}
	hayLibros, hayTiempo := false, false
	for _, l := range libros {
		k := l.Kid
		if k == "" {
			k = kid(claveDe(l.Titulo, l.Autor))
		}
		r := remotos[k]
		if r.Libro != nil && numero(r.Libro["b"]) > 0 {
			continue // borrado en Seshat: no se resucita
		}
		ul := ms(l.UltimaLectura)
		fin := ms(l.Terminado)
		fr := math.Round(l.Porcentaje*100) / 10000 // 0..1 con cuatro decimales, como Seshat
		estado := ""
		switch l.Estado {
		case 1:
			estado = estadoLeyendo
		case 2:
			estado = estadoTerminado
			fr = 1
		}
		campos := map[string][2]any{} // campo → {valor, hora}
		pon := func(c string, v any, hora int64) {
			if hora <= 0 || hora <= horaDe(r.Libro, c) {
				return
			}
			campos[c] = [2]any{v, hora}
		}
		if r.Libro == nil {
			// Un libro que Seshat no tiene: se crea con lo que sabe el Kobo
			hora := ul
			if hora == 0 {
				hora = ahoraMs
			}
			pon("clave", claveDe(l.Titulo, l.Autor), hora)
			pon("t", l.Titulo, hora)
			pon("a", l.Autor, hora)
			pon("an", hora, hora)
			if estado == "" {
				estado = estadoPendiente
			}
			pon("estado", estado, hora)
			if ul > 0 {
				pon("ul", ul, ul)
			}
			pon("fr", fr, hora)
			if fin > 0 && l.Estado == 2 {
				pon("fin", []any{fin}, fin)
			}
		} else if ul > 0 {
			if ul > int64(numero(r.Libro["ul"])) {
				pon("ul", ul, ul)
			}
			if math.Abs(fr-numero(r.Libro["fr"])) > 0.00005 {
				pon("fr", fr, ul)
			}
			if estado != "" && estado != texto(r.Libro["estado"]) {
				h := ul
				if l.Estado == 2 && fin > 0 {
					h = fin
				}
				pon("estado", estado, h)
			}
			if fin > 0 && l.Estado == 2 {
				lista, _ := r.Libro["fin"].([]any)
				ya := false
				for _, x := range lista {
					if math.Abs(numero(x)-float64(fin)) < 36*3600*1000 {
						ya = true
					}
				}
				if !ya {
					pon("fin", append(append([]any{}, lista...), fin), fin)
				}
			}
		}
		if len(campos) > 0 {
			hayLibros = true
			for c, vh := range campos {
				p.Cambios[fmt.Sprintf("familia/libros/%s/%s", k, c)] = vh[0]
				p.Cambios[fmt.Sprintf("familia/libros/%s/m/%s", k, c)] = vh[1]
			}
			p.Cambios[fmt.Sprintf("familia/libros/%s/u", k)] = sv
		}

		// El tiempo leído en el Kobo: su total y, lo que ha crecido desde la última vez, en el día de la última lectura. La primera vez
		// solo el total (el Kobo no sabe en qué días se leyó lo de antes: ponerlo todo en un día haría un pico falso)
		total := l.Segundos * 1000
		ref := k + "~" + disp
		viejo := int64(numero(r.Tiempo["total"]))
		tiempoCambia := false
		if total > viejo {
			hora := ahoraMs
			if h := maxHora(r.Tiempo) + 1; h > hora {
				hora = h
			}
			t, a := l.Titulo, l.Autor
		if r.Libro != nil && texto(r.Libro["t"]) != "" {
			t, a = texto(r.Libro["t"]), texto(r.Libro["a"]) // (como se llama en Seshat)
		}
		tc := map[string]any{"ref": k, "fuente": disp, "total": total, "t": t, "a": a}
			if r.Tiempo != nil {
				dia := ahora
				if !l.UltimaLectura.IsZero() {
					dia = l.UltimaLectura
				}
				d := "d" + dia.In(zona).Format("2006-01-02")
				tc[d] = int64(numero(r.Tiempo[d])) + (total - viejo)
			}
			for c, v := range tc {
				if r.Tiempo != nil && c != "total" && !(len(c) > 1 && c[0] == 'd') && fmt.Sprint(r.Tiempo[c]) == fmt.Sprint(v) {
					continue // (lo que no cambia no se vuelve a subir)
				}
				p.Cambios[fmt.Sprintf("familia/tiempo/%s/%s", ref, c)] = v
				p.Cambios[fmt.Sprintf("familia/tiempo/%s/m/%s", ref, c)] = hora
			}
			p.Cambios[fmt.Sprintf("familia/tiempo/%s/u", ref)] = sv
			hayTiempo, tiempoCambia = true, true
		}
		if len(campos) > 0 || tiempoCambia {
			p.Resumen = append(p.Resumen, fmt.Sprintf("%s: %s · %s", l.Titulo, cuanto(l), duracion(l.Segundos)))
		}
	}
	if hayLibros {
		p.Cambios["familia_sello/libros"] = sv
	}
	if hayTiempo {
		p.Cambios["familia_sello/tiempo"] = sv
	}
	sort.Strings(p.Resumen)
	return p
}

// buscaKid dice qué libro de la cuenta es [l]: el de su misma clave (título y autor) o, si no está, el único con el mismo título y
// las mismas palabras en el autor («Brown, Pierce» es «Pierce Brown»), o con el mismo título y sin autor en uno de los dos. Si no
// hay ninguno (o hay dudas), el de su clave: será un libro nuevo.
func buscaKid(l LibroKobo, libros map[string]Nodo) string {
	k := kid(claveDe(l.Titulo, l.Autor))
	if _, ok := libros[k]; ok {
		return k
	}
	t, a := normaliza(l.Titulo), palabras(l.Autor)
	var hallados []string
	for ref, n := range libros {
		if n == nil || numero(n["b"]) > 0 || normaliza(texto(n["t"])) != t {
			continue
		}
		b := palabras(texto(n["a"]))
		if a == b || a == "" || b == "" {
			hallados = append(hallados, ref)
		}
	}
	if len(hallados) == 1 {
		return hallados[0]
	}
	return k
}

// palabras: las palabras de [s] normalizadas y en orden (para comparar autores escritos al revés).
func palabras(s string) string {
	p := strings.Fields(normaliza(s))
	sort.Strings(p)
	return strings.Join(p, " ")
}

func cuanto(l LibroKobo) string {
	if l.Estado == 2 {
		return "terminado"
	}
	return fmt.Sprintf("%d %%", int(math.Round(l.Porcentaje)))
}

func duracion(s int64) string {
	m := s / 60
	if m < 60 {
		return fmt.Sprintf("%d min", m)
	}
	return fmt.Sprintf("%d h %d min", m/60, m%60)
}
