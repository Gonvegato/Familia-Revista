package com.gonzalo.cobalto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * El contrato con Azabache y Marfil (docs/CONTRATO-DATOS.md), de verdad: lo que Cobalto publica (el PATCH entero), tal cual lo escribe,
 * se deja en `build/outputs/contrato/cobalto.json`. Azabache lo lee con su prueba `ContratoHermanasTest` (copia en `herramientas/casos/hermanas/`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContratoTest {
    @Test fun loQuePublicaCobalto() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Escritos.reiniciaParaPruebas(); File(ctx.filesDir, "escritos").deleteRecursively(); Cuenta.cargar(ctx)
        val e = Escritos.get(ctx)
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "La casa"); put("autor", "Gonzalo") }
        val parte = e.nuevo(Escritos.T_PARTE, o.id) { put("titulo", "Primera parte"); put("hecho", 1) }
        val c1 = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "El regreso"); put("aid", parte.id) }
        val c2 = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Sin terminar") }
        e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c1.id); put("titulo", "a"); put("texto", "Volvió **tarde**.") }
        e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c1.id); put("titulo", "b"); put("texto", "Y *nadie* lo vio.") }
        e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c2.id); put("titulo", "c"); put("texto", "Borrador.") }
        e.pon(JSONObject().put("id", "l_${o.id}_20454").put("t", Escritos.T_LOG).put("pid", o.id).put("dia", 20454L).put("dev", JSONObject().put(Cuenta.dispositivo, 500)))
        e.pon(JSONObject().put("id", "l_${o.id}_20456").put("t", Escritos.T_LOG).put("pid", o.id).put("dia", 20456L).put("dev", JSONObject().put(Cuenta.dispositivo, 100).put("otro", 900)))
        val env = Publicar.envio(e, JSONObject(), "", "cobalto-A")
        assertEquals(500, env.cuerpo.getJSONObject("palabras/cobalto-A").getInt("2026-01-01"))
        // El PATCH, puesto como queda en la cuenta: lectura/<obra>/<cap>, palabras/<dev>, stamps/<dev>
        val cuenta = JSONObject()
        for (k in env.cuerpo.keys()) {
            val partes = k.split('/'); var n = cuenta
            for (p in partes.dropLast(1)) n = n.optJSONObject(p) ?: JSONObject().also { n.put(p, it) }
            n.put(partes.last(), env.cuerpo.get(k))
        }
        assertEquals(1, cuenta.getJSONObject("lectura").getJSONObject(o.id).length())
        assertEquals("2026-01-01", Publicar.fecha(20454L))
        val out = JSONObject().put("app", "cobalto").put("version", VERSION).put("obra", o.id).put("capitulo", c1.id).put("cuenta", cuenta)
        val f = File("build/outputs/contrato/cobalto.json").apply { parentFile.mkdirs() }
        f.writeText(out.toString(2))
        assertTrue(f.length() > 100)
    }
}
