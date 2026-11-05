package com.gonzalo.gamerate

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
 * El contrato al revés: lo que Azabache sube a la cuenta (`dev/<dispositivo>/data`, su `syncData()` tal cual), con una ficha de juego
 * valorada, un libro valorado y una ficha reflejada de Hiedra aún sin nota. Se deja en `build/outputs/contrato/azabache.json`; Hiedra y Marfil
 * lo leen con su prueba `NotaAzabacheContratoTest` (copia en su `herramientas/casos/`), que es como ven «En Azabache».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContratoTest {
    @Test fun loQueSubeAzabache() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val s = Store(ctx)
        val hiedra = Hermanas.juegosDe(mapOf("hiedra-A" to JSONObject().put("juegos", org.json.JSONArray().put(
            JSONObject().put("id", "j7").put("t", "Celeste").put("es", "PASADO").put("mod", 100).put("fi", 1_700_000_000_000L)
                .put("ln", org.json.JSONArray().put(JSONObject().put("tp", "ROM").put("s", "gba").put("a", "Celeste.gba")))))))
        // Celeste llega reflejada de Hiedra (sin nota aún); Hades II la valoras tú; Dune es un libro valorado
        val e = Hermanas.reflejarJuegos(hiedra, emptyList(), emptyList(), HashMap(), ahora = 1)
        s.save(e.games + Game(customItemId("Hades II"), "Hades II", "", general = 91, gfx = 90, story = 88, music = 95, gameplay = 92, review = "Mejor que el primero.", creator = "Supergiant", end = 20_700L, updated = 1_700_000_000_000L), 0)
        s.save(listOf(Game(customItemId("Dune"), "Dune", "", general = 89, gfx = 80, story = 95, music = 85, gameplay = 90, review = "La especia.", creator = "Frank Herbert", end = 20_700L, updated = 1_700_000_000_000L)), 1)
        val data = s.syncData()
        val b = JSONObject(data)
        assertEquals(2, b.getJSONArray("games").length()); assertEquals(1, b.getJSONArray("books").length())
        val out = JSONObject().put("app", "azabache").put("version", runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrDefault("?"))
            .put("idHades", customItemId("Hades II")).put("idDune", customItemId("Dune")).put("idCeleste", customItemId("Celeste"))
            .put("dev", JSONObject().put("azabache-A", JSONObject().put("data", data)))
        val f = File("build/outputs/contrato/azabache.json").apply { parentFile.mkdirs() }
        f.writeText(out.toString(2))
        assertTrue(f.length() > 100)
    }
}
