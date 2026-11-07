package com.gonzalo.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Qué canal de la guía es cada canal de tu lista: primero por su tvg-id, luego por su nombre normalizado. */
class EmparejarTest {
    private val guia = listOf(
        CanalGuia("La1.es", listOf("La 1", "TVE La 1 HD")), CanalGuia("La2.es", listOf("La 2")), CanalGuia("Antena3.es", listOf("Antena 3")),
        CanalGuia("Cuatro.es", listOf("Cuatro")), CanalGuia("telecinco.es", listOf("Telecinco")), CanalGuia("TeleMadrid.es", listOf("Telemadrid")),
        CanalGuia("Movistar.Plus1.es", listOf("Movistar Plus+ 1")), CanalGuia("Antena3+1.es", listOf("Antena 3 +1")), CanalGuia("Cosmo.es", listOf("Canal Cosmo & Co")),
    )
    private val e = Emparejador(guia)
    private fun b(nombre: String, tvgId: String = "", tvgNombre: String = "") = e.busca(Canal("x", nombre, tvgId = tvgId, tvgNombre = tvgNombre))

    @Test fun porElId() {
        assertEquals("La1.es", b("Lo que sea", "La1.es"))
        assertEquals("sin mirar mayúsculas", "telecinco.es", b("Lo que sea", "Telecinco.es"))
        assertEquals("el id normalizado", "Antena3.es", b("Otro nombre", "ANTENA3"))
    }

    @Test fun porElNombre() {
        assertEquals("La1.es", b("ES: La 1 HD"))
        assertEquals("La1.es", b("|ES| LA 1 FHD"))
        assertEquals("La2.es", b("La 2 (1080p)"))
        assertEquals("con acentos y sin ellos", "TeleMadrid.es", b("TELEMADRÍD [Backup]"))
        assertEquals("el tvg-name antes que el que se ve", "Cuatro.es", b("Canal 4", tvgNombre = "Cuatro HD"))
        assertEquals("el +1 es otro canal", "Antena3+1.es", b("ES | Antena 3 +1 HD"))
        assertEquals("Antena3.es", b("Antena 3 HEVC"))
        assertEquals("Cosmo.es", b("Canal Cosmo and Co"))
        assertEquals("Movistar.Plus1.es", b("Movistar Plus+ 1 UHD"))
        assertNull(b("Canal que no está")); assertNull(b(""))
        assertEquals("un id que no está, con un nombre que sí", "Cuatro.es", b("Cuatro", "cuatro.es.desconocido"))
    }

    @Test fun normalizar() {
        assertEquals("la1", Emparejador.normaliza("ES: La 1 HD"))
        assertEquals("antena3+1", Emparejador.normaliza("[ES] Antena 3 +1 (FHD)"))
        assertEquals("telemadrid", Emparejador.normaliza("TeleMadrid H.265"))
    }
}
