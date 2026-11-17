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
	d := norm.NFD.String(strings.ToLower(s))
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
