package com.gonzalo.cobalto

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/** Lo de la dirección C en Thot 0.6.0: la meta del día y su cumplimiento, la semana, las páginas, el estado, el paspartú, el encuadre y el cobre. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThotCTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var e: Escritos

    @Before fun limpia() {
        ctx.getSharedPreferences("cobalto_ajustes", Context.MODE_PRIVATE).edit().clear().commit()
        Escritos.reiniciaParaPruebas(); File(ctx.filesDir, "escritos").deleteRecursively()
        e = Escritos.get(ctx)
    }

    private fun obra(titulo: String, meta: Int = 0): Doc = e.nuevo(Escritos.T_OBRA, "") { put("titulo", titulo); put("meta", meta) }
    private fun palabras(pid: String, atras: Long, n: Int) =
        e.pon(JSONObject().put("id", "l_${pid}_${Escritos.hoy() - atras}").put("t", Escritos.T_LOG).put("pid", pid).put("dia", Escritos.hoy() - atras).put("dev", JSONObject().put("movil", n)))

    @Test fun laMetaDelDiaEsDe500DeSerieYSeCambia() {
        assertEquals(500, MetaDiaria.de(ctx))
        MetaDiaria.pon(ctx, 750)
        assertEquals(750, MetaDiaria.de(ctx))
        // (ni cero ni disparates)
        MetaDiaria.pon(ctx, 0); assertEquals(50, MetaDiaria.de(ctx))
        MetaDiaria.pon(ctx, 1_000_000); assertEquals(20_000, MetaDiaria.de(ctx))
    }

    @Test fun cuentaLosDiasQueCumplisteLaMetaEnLosUltimos30() {
        val a = obra("A"); val b = obra("B")
        palabras(a.id, 0, 300); palabras(b.id, 0, 250)      // hoy: 550 entre las dos → cumplida
        palabras(a.id, 1, 499)                              // ayer: no llega
        palabras(a.id, 5, 500)                              // justo la meta: cumplida
        palabras(a.id, 29, 900)                             // el día 30 contando hoy: cuenta
        palabras(a.id, 30, 900)                             // el 31: ya no
        assertEquals(3, e.cumplidos(500))
        assertEquals(0, e.cumplidos(0))
        val d = datosEscritura(e, 500)
        assertEquals(550, d.hoy); assertTrue(d.cumplida); assertEquals(3, d.cumplidos30)
        assertEquals("¡META CUMPLIDA! Y 50 DE PROPINA", textoMeta(d))
        assertEquals("CUMPLIDA 3 DE LOS ÚLTIMOS 30 DÍAS", textoCumplidos(3))
        assertEquals("TE FALTAN 200", textoMeta(datosEscritura(e, 750)))
    }

    @Test fun laSemanaSonLosSieteUltimosDiasConHoyAlFinal() {
        val a = obra("A")
        palabras(a.id, 0, 120); palabras(a.id, 3, 812); palabras(a.id, 7, 999)
        val d = datosEscritura(e, 500)
        assertEquals(7, d.semana.size)
        assertEquals(LocalDate.now(), d.semana.last().first)
        assertEquals(LocalDate.now().minusDays(6), d.semana.first().first)
        assertEquals(listOf(0, 0, 0, 812, 0, 0, 120), d.semana.map { it.second })
        assertEquals(932, d.semanaTotal)
        assertEquals("812 palabras", palabrasDia(812)); assertEquals("nada", palabrasDia(0)); assertEquals("1 palabra", palabrasDia(1))
        assertEquals("1,2K", palabrasCorto(1234)); assertEquals("12K", palabrasCorto(12_345)); assertEquals("999", palabrasCorto(999))
    }

    @Test fun lasPaginasVanEnUnaFilaYCadaUnaTieneSusOrdenes() {
        assertEquals(listOf(Destino.Portada, Destino.Escribiendo, Destino.EnPausa, Destino.Terminadas, Destino.Archivo), PAGINAS)
        assertEquals(listOf(EstadoObra.ESCRIBIENDO, EstadoObra.PAUSA, EstadoObra.TERMINADA, EstadoObra.ARCHIVO), PAGINAS.drop(1).map { it.estado() })
        // La de «más avanzada» solo donde se escribe; en el archivo, nada de palabras
        assertTrue(OrdenObras.AVANCE in ordenesDe(EstadoObra.ESCRIBIENDO))
        assertFalse(OrdenObras.AVANCE in ordenesDe(EstadoObra.TERMINADA))
        assertFalse(OrdenObras.PALABRAS in ordenesDe(EstadoObra.ARCHIVO))
        EstadoObra.entries.forEach { assertEquals(OrdenObras.RECIENTES, ordenesDe(it).first()) }
        // El orden elegido se recuerda por página
        OrdenGuardado.pon(ctx, EstadoObra.PAUSA, OrdenObras.TITULO)
        assertEquals(OrdenObras.TITULO, OrdenGuardado.de(ctx, EstadoObra.PAUSA))
        assertEquals(OrdenObras.RECIENTES, OrdenGuardado.de(ctx, EstadoObra.ESCRIBIENDO))
        // Más avanzada: la que más lleva de su meta
        val a = obra("A", meta = 1000); val b = obra("B", meta = 100)
        val ca = e.nuevo(Escritos.T_CAP, a.id) { put("titulo", "1") }; e.nuevo(Escritos.T_ESCENA, a.id) { put("cid", ca.id); put("palabras", 200) }
        val cb = e.nuevo(Escritos.T_CAP, b.id) { put("titulo", "1") }; e.nuevo(Escritos.T_ESCENA, b.id) { put("cid", cb.id); put("palabras", 50) }
        assertEquals(listOf("B", "A"), ordenar(OrdenObras.AVANCE, e, e.obras()).map { it.s("titulo") })
    }

    @Test fun laEtiquetaDelEstadoDiceElCapituloPorElQueVas() {
        val a = obra("A")
        assertEquals("ESCRIBIENDO", etiquetaEstado(e, a))
        val c1 = e.nuevo(Escritos.T_CAP, a.id) { put("titulo", "Uno") }
        val c2 = e.nuevo(Escritos.T_CAP, a.id) { put("titulo", "Dos") }
        e.nuevo(Escritos.T_ESCENA, a.id) { put("cid", c1.id) }
        Thread.sleep(3)
        e.nuevo(Escritos.T_ESCENA, a.id) { put("cid", c2.id) }
        assertEquals("ESCRIBIENDO · CAP. 2", etiquetaEstado(e, e.get(a.id)!!))
        e.ponEstado(a.id, EstadoObra.PAUSA); assertEquals("EN PAUSA", etiquetaEstado(e, e.get(a.id)!!))
        e.ponEstado(a.id, EstadoObra.TERMINADA); assertEquals("TERMINADA", etiquetaEstado(e, e.get(a.id)!!)); assertEquals("Leer en Seshat", textoPrincipal(e, e.get(a.id)!!))
        e.ponEstado(a.id, EstadoObra.ESCRIBIENDO); assertEquals("Empezar a escribir", textoPrincipal(e, e.get(a.id)!!))
    }

    @Test fun elPaspartuEsCremaYOscuroSoloConPortadasCasiBlancas() {
        assertEquals(PASPARTU_CREMA, colorPaspartu(luminancia(Color(0xFF2F4A6B))))
        assertEquals(PASPARTU_CREMA, colorPaspartu(0.7f))
        assertEquals(PASPARTU_OSCURO, colorPaspartu(luminancia(Color(0xFFF7F4EC))))
        assertEquals(4.dpValor(), grosorPaspartu(androidx.compose.ui.unit.Dp(40f)).value, 0.01f)
        assertEquals(8.1f, grosorPaspartu(androidx.compose.ui.unit.Dp(150f)).value, 0.01f)
    }
    private fun Int.dpValor() = this.toFloat()

    @Test fun laPortadaYElFondoVanEnLaObraYElEncuadreEsDeCadaDispositivo() {
        val a = obra("A")
        val img = File(File(ctx.filesDir, "familia"), "img/abc123").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        e.edita(a.id) { put(ImagenesObra.PORTADA, "abc123") }
        val o = e.get(a.id)!!
        assertEquals(img.absolutePath, ImagenesObra.portada(ctx, o))
        assertEquals("", ImagenesObra.fondo(ctx, o))
        // El encuadre: en este dispositivo…
        Encuadres.almacen = "encuadres_movil"
        ImagenesObra.ponerEncuadre(ctx, o, false, Encuadre(2f, 0.3f, 0.6f))
        assertEquals(Encuadre(2f, 0.3f, 0.6f), ImagenesObra.encPortada(ctx, o))
        // …y en otro (la tableta), centrada hasta que la encuadres allí
        Encuadres.almacen = "encuadres_tableta"
        assertTrue(ImagenesObra.encPortada(ctx, o).neutro)
        // Si la imagen cambia, el encuadre de antes ya no vale
        Encuadres.almacen = "encuadres_movil"
        File(File(ctx.filesDir, "familia"), "img/def456").writeBytes(byteArrayOf(4))
        e.edita(a.id) { put(ImagenesObra.PORTADA, "def456") }
        assertTrue(ImagenesObra.encPortada(ctx, e.get(a.id)!!).neutro)
        // Quitarla deja la tapa de su color
        ImagenesObra.quitar(e, a.id, fondo = false)
        assertEquals("", ImagenesObra.portada(ctx, e.get(a.id)!!))
        Encuadres.almacen = "encuadres"
    }

    @Test fun laTelaEsLaDeSuColorOUnaFijaPorTitulo() {
        val roja = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "X"); put("color", "#C8372D") }
        assertEquals(Color(0xFFC8372D), telaDe(roja))
        val sin = obra("Sin color")
        assertEquals(telaDe(sin), telaDe(e.get(sin.id)!!))
    }

    @Test fun elCobreSeLeeSobreElAzulNocheYElDeAntesPasaAlCobre() {
        val noche = Color(0xFF171E2B); val texto = Color(0xFFF1EBE0); val cobre = Color(0xFFC8703F)
        assertTrue(contraste(cobre, noche) >= 4.5f)
        assertEquals(cobre, acentoLegible(cobre, noche, texto))
        // Sobre la cabecera crema, el cobre se oscurece lo justo para leerse
        val enTinta = acentoLegible(cobre, texto, noche)
        assertNotEquals(cobre, enTinta); assertTrue(contraste(enTinta, texto) >= 4.5f)
        // El acento de serie de antes (el dorado) pasa al cobre; lo demás elegido a mano se queda
        Skin.acentoPropio = 0xFFA79530; Skin.fondoPropio = 0xFF171E2B
        assertTrue(Skin.aCobre())
        assertEquals(0xFFC8703FL, Skin.acentoPropio); assertEquals(0xFF171E2BL, Skin.fondoPropio)
        assertFalse(Skin.aCobre())
        Skin.acentoPropio = 0xFF2F5D8A; assertFalse(Skin.aCobre()); assertEquals(0xFF2F5D8AL, Skin.acentoPropio)
        Skin.acentoPropio = 0L; Skin.fondoPropio = 0L
    }

    @Test fun losCapitulosDelAnoCuentanLosTerminados() {
        val a = obra("A"); val b = obra("B")
        e.nuevo(Escritos.T_CAP, a.id) { put("titulo", "1"); put("hecho", 1) }
        e.nuevo(Escritos.T_CAP, a.id) { put("titulo", "2"); put("hecho", 1) }
        e.nuevo(Escritos.T_CAP, a.id) { put("titulo", "3") }
        e.nuevo(Escritos.T_CAP, b.id) { put("titulo", "1") }
        val ano = anoDe(System.currentTimeMillis())
        assertEquals(mapOf(a.id to 2), e.capitulosDelAno(ano))
        assertTrue(e.capitulosDelAno(ano - 1).isEmpty())
    }
}
