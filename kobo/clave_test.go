package main

import "testing"

// Las claves y los kid de Seshat para estos libros, calculados con el mismo código que usa Seshat (Java: Normalizer NFD + SHA-1):
// si alguno no coincide, los libros del Kobo no casarían con los de Seshat.
func TestClaveYKidComoSeshat(t *testing.T) {
	casos := [][4]string{
		{"Amanecer rojo", "Pierce Brown", "libro:amanecer rojo:pierce brown", "6955e596a64e0c22d3c8"},
		{"El nombre del viento", "Patrick Rothfuss", "libro:el nombre del viento:patrick rothfuss", "f84464827d5f5d9f12a0"},
		{"¿Quién mató a Palomino Molero?", "Mario Vargas Llosa", "libro:quien mato a palomino molero:mario vargas llosa", "8659a09d04867ddca1cf"},
		{"  Ñandú — «Ü» 1984!! ", "José-Luis O'Brien", "libro:nandu u 1984:jose luis o brien", "48ade051cf5722fffa78"},
		{"Mañana, y mañana, y mañana", "Gabrielle Zevin", "libro:manana y manana y manana:gabrielle zevin", "5164831c922379b39742"},
		{"L'Étranger", "Albert Camus", "libro:l etranger:albert camus", "4e5e95e5657268c604f3"},
	}
	for _, c := range casos {
		if k := claveDe(c[0], c[1]); k != c[2] {
			t.Errorf("clave de %q: %q, Seshat da %q", c[0], k, c[2])
		}
		if k := kid(c[2]); k != c[3] {
			t.Errorf("kid de %q: %q, Seshat da %q", c[2], k, c[3])
		}
	}
}
