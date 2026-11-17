package main

import (
	"crypto/sha1"
	"encoding/hex"
	"strings"
	"unicode"

	"golang.org/x/text/unicode/norm"
)

// normaliza hace lo mismo que `norm` de Seshat (marfil/Modelo.kt): minúsculas, sin acentos (NFD sin marcas) y todo lo que no es
// letra ni cifra, un espacio; sin espacios a los lados. Así el mismo libro tiene la misma clave en el Kobo y en Seshat.
func normaliza(s string) string {
	d := norm.NFD.String(minusculas(s))
	var b strings.Builder
	hueco := false
	for _, r := range d {
		if unicode.Is(unicode.M, r) {
			continue
		}
		if unicode.IsLetter(r) || unicode.IsNumber(r) {
			if hueco && b.Len() > 0 {
				b.WriteByte(' ')
			}
			hueco = false
			b.WriteRune(r)
		} else {
			hueco = true
		}
	}
	return b.String()
}

// minusculas es `lowercase()` de Kotlin/Java (Locale.ROOT): como strings.ToLower, pero la Σ griega al final de una palabra es «ς»
// (la regla de la sigma final de Unicode, que Go no aplica): «ΟΔΥΣΣΕΑΣ» → «οδυσσεας». Final: con una letra antes y ninguna después
// (saltando lo que no cuenta: apóstrofos, puntos, acentos sueltos…).
func minusculas(s string) string {
	rs := []rune(s)
	var b strings.Builder
	for i, r := range rs {
		if r == 'Σ' {
			if letraAntes(rs, i) && !letraDespues(rs, i) {
				b.WriteRune('ς')
			} else {
				b.WriteRune('σ')
			}
			continue
		}
		b.WriteString(strings.ToLower(string(r)))
	}
	return b.String()
}

func conCaso(r rune) bool { return unicode.IsUpper(r) || unicode.IsLower(r) || unicode.IsTitle(r) }

// (Case_Ignorable de Unicode: marcas, formatos, modificadores y los signos que pueden ir dentro de una palabra)
func ignorable(r rune) bool {
	return unicode.In(r, unicode.Mn, unicode.Me, unicode.Cf, unicode.Lm, unicode.Sk) ||
		strings.ContainsRune("'.:·\u2018\u2019\u2024\u00ad\ufe52\uff07\uff0e\u02bc", r)
}

func letraAntes(rs []rune, i int) bool {
	for j := i - 1; j >= 0; j-- {
		if !ignorable(rs[j]) {
			return conCaso(rs[j])
		}
	}
	return false
}

func letraDespues(rs []rune, i int) bool {
	for j := i + 1; j < len(rs); j++ {
		if !ignorable(rs[j]) {
			return conCaso(rs[j])
		}
	}
	return false
}

// claveDe es `claveDe` de Seshat: «libro:<título>:<autor>», normalizados.
func claveDe(titulo, autor string) string {
	return "libro:" + normaliza(titulo) + ":" + normaliza(autor)
}

// kid es `MezclaLectura.kid` de Seshat: los 20 primeros caracteres del SHA-1 (en hexadecimal) de la clave. Es el nombre del libro
// en la cuenta (`libros/<kid>`) y en su tiempo (`tiempo/<kid>~<dispositivo>`).
func kid(clave string) string {
	h := sha1.Sum([]byte(clave))
	return hex.EncodeToString(h[:])[:20]
}
