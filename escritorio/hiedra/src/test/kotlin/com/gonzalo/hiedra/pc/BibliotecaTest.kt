package com.gonzalo.hiedra.pc

import com.gonzalo.familia.nucleo.Nodo
import org.junit.Assert.*
import org.junit.Test

/** Lo que se ve en la sala: tus juegos de la cuenta juntos con lo de este ordenador, sin repetir nada. */
class BibliotecaTest {
    private fun juego(id: String, t: String, estado: String = "pendiente", fav: Boolean = false, ul: Long = 0, vararg ln: Map<String, Any?>) =
        Juego(Nodo(id, mapOf("t" to t, "estado" to estado, "fav" to fav, "ul" to ul, "ln" to ln.toList())), emptyList())
    private fun pc(k: String, dev: String) = mapOf("tp" to "PC", "pck" to k, "pcd" to dev)

    @Test fun cadaJuegoConLoQueLeTocaDeAqui() {
        val locales = listOf(
            JuegoPc("steam:1145360", "Hades", "", fuente = Fuente.STEAM),
            JuegoPc("lutris:3", "Celeste", "", fuente = Fuente.LUTRIS, etiqueta = "Lutris"),
            JuegoPc("heroic:legendary:Fortnite", "Fortnite", "", fuente = Fuente.HEROIC, etiqueta = "Epic"),
            Pc.deRom(Rom("/r/gba/Advance Wars (USA).gba", "gba", "Advance Wars (USA)")),
            JuegoPc("steam:620", "Portal 2", "", fuente = Fuente.STEAM),
        )
        val juegos = listOf(
            // De Steam en otro PC (Atrio): el mismo juego si está instalado aquí
            juego("j_1", "Hades", "en_curso", ul = 5, ln = arrayOf(pc("steam:1145360", "pc-atrio"))),
            // De Lutris en otro PC: su número es de aquel PC, no vale aquí
            juego("j_2", "Celeste", ln = arrayOf(pc("lutris:3", "otro-pc"))),
            // La ROM del móvil (la misma clave)
            juego("j_3", "Advance Wars", "en_curso", ul = 9, ln = arrayOf(mapOf("tp" to "ROM", "s" to "gba", "a" to "Advance Wars (USA)"))),
            // De Heroic: vale en cualquier PC
            juego("j_4", "Fortnite", fav = true, ln = arrayOf(pc("heroic:legendary:Fortnite", "pc-atrio"))),
            // De Switch: no se juega aquí
            juego("j_5", "Zelda", "en_curso", ul = 20),
        )
        val b = Biblioteca.de(juegos, locales, "hiedra-linux-pc1")
        assertEquals("steam:1145360", b.por("j_1")!!.local!!.clave)
        assertNull(b.por("j_2")!!.local)
        assertNotNull("el de Lutris de aquí sale aparte", b.por("lutris:3"))
        assertEquals("rom:gba:advancewarsusa", b.por("j_3")!!.local!!.clave)
        assertTrue(b.por("j_4")!!.jugable)
        assertFalse(b.por("j_5")!!.jugable)
        assertEquals(7, b.fichas.size)   // 5 de la cuenta + Celeste de Lutris y Portal 2, que no están en tu biblioteca
        assertFalse(b.por("steam:620")!!.enCuenta)
        // «Seguir jugando»: primero lo que se puede jugar aquí, lo último antes
        assertEquals(listOf("j_3", "j_1", "j_5"), b.jugando.map { it.clave })
        assertEquals("Seguir jugando", b.inicio().first().titulo)
        // Por tienda y por consola, solo lo de aquí
        assertEquals(listOf("Steam", "Epic", "Lutris", "Game Boy Advance"), b.porOrigen().map { it.titulo })
        // Buscar: primero lo que empieza así
        assertEquals(listOf("Fortnite"), b.busca("fort").map { it.titulo })
        assertEquals(listOf("Advance Wars"), b.busca("war").map { it.titulo })
        // Las letras de la biblioteca (sin «The» delante)
        assertEquals('Z', Ficha(juego("x", "The Zelda"), null).letra)
        assertEquals('#', Ficha(juego("y", "1942"), null).letra)
    }
}
