package com.gonzalo.shai

import org.junit.Assert.*
import org.junit.Test

/** Dentro de la historia no existe el videojuego: ni la media ni nada que lo nombre llega a lo que se lee. */
class SinJuegoTest {
    @Test fun quitaLaMediaYElJuegoPeroNoElFutbolDeVerdad() {
        assertTrue(SinJuego.nombra("Con su media de 82 ya es de los mejores"))
        assertTrue(SinJuego.nombra("Ha subido su media tras el partido"))
        assertTrue(SinJuego.nombra("Ritmo 91, eso no se enseña"))
        assertTrue(SinJuego.nombra("Ya tiene carta oro en EA Sports FC"))
        assertFalse(SinJuego.nombra("Juega en LaLiga EA Sports"))
        assertFalse(SinJuego.nombra("La media de goles del equipo es de 1,8 por partido"))
        assertFalse(SinJuego.nombra("Media plantilla fue a la fiesta y a media tarde estaban todos"))
        assertFalse(SinJuego.nombra("La FIFA ha confirmado las sedes del Mundial"))
        assertFalse(SinJuego.nombra("Un 8 de nota en Marca y la grada en pie"))
        assertEquals("El Niño firmó un partidazo. La grada se lo pasó en grande.",
            SinJuego.texto("El Niño firmó un partidazo. Su media ya es de 84, una barbaridad. La grada se lo pasó en grande."))
    }

    @Test fun losPostsQueLaNombranFueraYLoDemasLimpio() {
        val m = mapOf("posts" to listOf(mapOf("autor" to "A", "texto" to "Media 85 con 19 años 🔥"), mapOf("autor" to "B", "texto" to "Qué golazo",
            "respuestas" to listOf(mapOf("texto" to "Su media va a explotar"), mapOf("texto" to "Brutal")))),
            "prensa" to mapOf("cabecera" to "Tribuna", "articulo" to "Gran partido. Su media sube a 80.\n\nEl club lo celebra."))
        @Suppress("UNCHECKED_CAST") val l = SinJuego.limpia(m) as Map<String, Any?>
        val posts = l["posts"] as List<Map<String, Any?>>
        assertEquals(1, posts.size); assertEquals(1, (posts[0]["respuestas"] as List<*>).size)
        assertEquals("Gran partido.\n\nEl club lo celebra.", (l["prensa"] as Map<*, *>)["articulo"])
    }

    @Test fun claudeRecibeSuNivelEnPalabrasYNuncaLaMedia() {
        val u = Ejemplo.universo().let { it.copy(personaje = it.personaje.copy(media = 79, atributos = mapOf("RIT" to 88, "TIR" to 74, "PAS" to 70, "REG" to 85, "DEF" to 35, "FIS" to 66),
            historial = listOf(1 to 75, 2 to 77, 3 to 79))) }
        val ctx = Narrador.contexto(u, emptyList(), emptyList())
        assertFalse(ctx, Regex("""\b79\b|\b88\b|media \d""").containsMatchIn(ctx))
        assertTrue(ctx.contains("de los buenos de su liga")); assertTrue(ctx.contains("va claramente a más"))
        assertTrue(ctx.contains("destaca por la velocidad y el regate")); assertTrue(ctx.contains("flojea en la defensa"))
        assertFalse(Narrador.partido(u, Partido("2026/27", "LaLiga EA Sports", "Girona FC", true, 2, 0, 90, 1, 0, 7.5, media = 80)).contains("media"))
    }
}
