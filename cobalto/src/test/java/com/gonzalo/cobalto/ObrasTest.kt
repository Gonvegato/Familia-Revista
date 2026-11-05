package com.gonzalo.cobalto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Lo de Cobalto sobre las obras: los menús, la obra actual, las frases del manuscrito y el resumen de palabras. Sin red. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ObrasTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var e: Escritos

    @Before fun limpia() {
        Escritos.reiniciaParaPruebas()
        File(ctx.filesDir, "escritos").deleteRecursively()
        Cuenta.cargar(ctx)
        e = Escritos.get(ctx)
    }

    private fun obra(titulo: String, texto: String = "", diaria: Int = 0, meta: Int = 0): Doc {
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", titulo); put("diaria", diaria); put("meta", meta) }
        val c = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Capítulo 1") }
        e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("titulo", "Escena 1"); put("texto", texto) }
        return o
    }

    @Test fun elMenuSaleDeEstYArch() {
        val a = obra("A"); val b = obra("B"); val c = obra("C"); val d = obra("D")
        e.ponEstado(b.id, EstadoObra.PAUSA); e.ponEstado(c.id, EstadoObra.TERMINADA); e.ponEstado(d.id, EstadoObra.ARCHIVO)
        assertEquals(EstadoObra.ESCRIBIENDO, estadoDe(e.get(a.id)!!))
        assertEquals("pausa", e.get(b.id)!!.s("est")); assertEquals(EstadoObra.PAUSA, estadoDe(e.get(b.id)!!))
        assertEquals("fin", e.get(c.id)!!.s("est")); assertEquals(EstadoObra.TERMINADA, estadoDe(e.get(c.id)!!))
        assertTrue(e.get(d.id)!!.b("arch")); assertEquals(EstadoObra.ARCHIVO, estadoDe(e.get(d.id)!!))
        assertEquals(listOf(a.id), e.obrasDe(EstadoObra.ESCRIBIENDO).map { it.id })
        assertEquals(listOf(b.id), e.obrasDe(EstadoObra.PAUSA).map { it.id })
        // Volver a escribir quita las dos marcas
        e.ponEstado(d.id, EstadoObra.ESCRIBIENDO)
        val dd = e.get(d.id)!!
        assertFalse(dd.b("arch")); assertEquals("", dd.s("est")); assertEquals(EstadoObra.ESCRIBIENDO, estadoDe(dd))
    }

    @Test fun laObraActualEsLaDeLaUltimaEscenaQueSeEscribe() {
        val a = obra("A", "Hola"); val b = obra("B", "Adiós")
        Thread.sleep(2)
        e.escribe(e.ultimaEscenaDe(a.id)!!.id, "Hola de nuevo con más palabras")
        assertEquals(a.id, e.obraActual()?.id)
        // Si pasa a pausa, la actual es la otra que se escribe
        e.ponEstado(a.id, EstadoObra.PAUSA)
        assertEquals(b.id, e.obraActual()?.id)
        e.ponEstado(b.id, EstadoObra.TERMINADA)
        assertNull(e.obraActual())
    }

    @Test fun lasFrasesSonParrafosQueSeLeenSueltos() {
        val largo = "Una frase que se lee bien suelta, con sus sesenta letras y algo más para llegar al mínimo."
        obra("A", "# Título\n\ncorta\n\n$largo\n\n> cita que no vale aunque sea larga y tenga muchas letras para pasar el mínimo de longitud\n\n***\n\n**Con** *formato* y una longitud suficiente para que cuente como frase del manuscrito, sin duda.")
        val f = e.frases()
        assertEquals(2, f.size)
        assertEquals(largo, f[0].texto)
        assertEquals("Con formato y una longitud suficiente para que cuente como frase del manuscrito, sin duda.", f[1].texto)
        // Las archivadas no salen
        e.ponEstado(f[0].obra.id, EstadoObra.ARCHIVO)
        assertTrue(e.frases().isEmpty())
    }

    @Test fun elResumenCuentaLasPalabrasDeHoyLaRachaYElObjetivo() {
        val a = obra("A", "", diaria = 300); obra("B", "", diaria = 200)
        val hoy = Escritos.hoy()
        e.registra(a.id, 120)
        e.pon(JSONObject().put("id", "l_${a.id}_${hoy - 1}").put("t", Escritos.T_LOG).put("pid", a.id).put("dia", hoy - 1).put("dev", JSONObject().put("x", 50)))
        e.pon(JSONObject().put("id", "l_${a.id}_${hoy - 2}").put("t", Escritos.T_LOG).put("pid", a.id).put("dia", hoy - 2).put("dev", JSONObject().put("x", 80)))
        val r = e.resumen(14)
        assertEquals(120, r.hoy); assertEquals(3, r.racha); assertEquals(500, r.diaria)
        assertEquals(14, r.dias.size); assertEquals(120, r.dias.last()); assertEquals(50, r.dias[12]); assertEquals(80, r.dias[11])
    }

    @Test fun metaObraYCuenta() {
        val a = obra("A", "uno dos tres", meta = 6)
        assertEquals("3 de 6 palabras", metaObra(e, e.get(a.id)!!))
        assertEquals("1 capítulo · 1 escena", e.cuentaObra(a.id))
    }
}
