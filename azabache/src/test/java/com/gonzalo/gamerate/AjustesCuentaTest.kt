package com.gonzalo.gamerate

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Lo que eliges en Azabache y no viajaba (Descubrir, los duelos, tus guías y dónde te quedaste en ellas, los sprints y el editor de Escribir,
 * los avisos, los objetivos de cada año, las recomendaciones ya vistas, tu nombre…) vive en tu cuenta (`ajustes/azabache_*`) y llega a tus otros
 * dispositivos ([otra]). Con la cuenta de verdad (el emulador de Firebase con las reglas de `nube/reglas.json`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AjustesCuentaTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var otra: Familia
    private lateinit var store: Store

    private fun espera(que: String, cond: () -> Boolean) {
        val hasta = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < hasta) { shadowOf(Looper.getMainLooper()).idle(); if (runCatching(cond).getOrDefault(false)) return; Thread.sleep(50) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(que, cond())
    }

    @Before fun prepara() {
        Emulador.limpia()
        Emulador.cuenta("gonzalo")
        otra = Emulador.dispositivo("gonzalo")
        listOf("ajustes", "azabache").forEach { otra.coleccion(it) }
    }

    private fun entra() {
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.configPruebas = Emulador.cfg
        EspejoCuenta.olvidaParaPruebas()
        Cosas.olvidaParaPruebas(); Cosas.migrarEnElMomento = true
        Propias.olvidaParaPruebas(); Propias.migrarEnElMomento = true
        LaFamilia.inicia(ctx)
        store = Store(ctx)
        runBlocking { Account.signIn(store, "gonzalo", "secreto1") }
        LaFamilia.coleccion("ajustes")
        EspejoCuenta.inicia(ctx, AjustesEnLaCuenta.TODOS)
        val hasta = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < hasta) { LaFamilia.sincronizaYa(); if (EspejoCuenta.intenta(ctx)) break; Thread.sleep(100) }
        assertTrue("lo de antes pasa a la cuenta", EspejoCuenta.intenta(ctx))
        sincroniza()
    }

    @After fun suelta() { LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = null; EspejoCuenta.olvidaParaPruebas() }

    private fun sincroniza() { assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otra.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok); shadowOf(Looper.getMainLooper()).idle() }

    private fun enLaCuenta(nodo: String) = otra.coleccion("ajustes").nodo(nodo)

    @Test fun loQueEligesAquiLlegaAlOtroYAlReves() {
        entra()
        // Aquí: recomendaciones vistas, objetivo de juegos, un aviso, tus guías de un juego, el diario con todo, tu nombre…
        store.recsDone = setOf(11L, 12L)
        store.setGoal(0, 2026, 30)
        store.avisoDiario = 21
        store.notifyPartner = true
        Guides.setLocal(ctx, 42L, listOf(Guide("Mapa", "https://ejemplo.org/mapa", "movil")))
        store.myName = "Gonzalo"
        // … Descubrir (filtros y preguntas vistas), unos duelos a medias, el editor y un sprint de Escribir, la web de imágenes
        EspejoCuenta.prefs(ctx, AjustesEnLaCuenta.DESCUBRIR).edit().putString("f_years", "1990-1999").putString("recent_0", "q1,q2").putString("rawg_parents", "{}").apply()
        DuelosEstado(ctx).let { d -> d.pool = listOf(1L, 2L, 3L); d.duelos = listOf(Duelos.Duelo(1L, 2L, 1)); d.ordenados = setOf(9L) }
        EspejoCuenta.prefs(ctx, AjustesEnLaCuenta.ESCRIBIR).edit().putFloat("tam", 22f).putBoolean("maquina", true)
            .putString("sprint", "{\"pid\":\"x\"}").putString("sprints", JSONArray().put(JSONObject().put("pid", "obra").put("desde", 5L).put("min", 25)).toString()).apply()
        EspejoCuenta.prefs(ctx, AjustesEnLaCuenta.INTERNET).edit().putString("fuente", "pinterest").apply()
        KDrive.setShots(ctx, false); Actualizador.ponActivo(ctx, false)
        sincroniza()
        enLaCuenta("azabache_general")!!.let { n ->
            assertEquals("11,12", n.texto("recsdone")); assertEquals(30L, n.entero("goal_0_2026")); assertEquals(21L, n.entero("aviso_diario"))
            assertEquals(true, n["pnotify"]); assertEquals("Gonzalo", n.texto("myname"))
            assertEquals("https://ejemplo.org/mapa", JSONArray(n.texto("guides_42")).getJSONObject(0).getString("u"))
            assertTrue("lo de la sesión y lo ya subido no viajan", n["accuid"] == null && n["dirtyAt"] == null && n["devid"] == null)
        }
        enLaCuenta("azabache_descubrir")!!.let { n -> assertEquals("1990-1999", n.texto("f_years")); assertEquals("q1,q2", n.texto("recent_0")); assertTrue("la caché de RAWG, no", n["rawg_parents"] == null) }
        enLaCuenta("azabache_duelos")!!.let { n -> assertEquals("[1,2,3]", n.texto("pool")); assertEquals("[9]", n.texto("ordenados")); assertEquals("[[1,2,1]]", n.texto("duelos")) }
        enLaCuenta("azabache_escribir")!!.let { n -> assertEquals(22.0, n.decimal("tam"), 0.0); assertEquals(true, n["maquina"]); assertTrue("el sprint que corre aquí, no", n["sprint"] == null); assertTrue(n.texto("sprints").contains("obra")) }
        assertEquals("pinterest", enLaCuenta("azabache_internet")!!.texto("fuente"))
        assertEquals(false, enLaCuenta("azabache_nube")!!["shots"])
        assertEquals(false, enLaCuenta("azabache_actualizador")!!["on"])
        AzabacheNube.sube(ctx, store)
        assertEquals("tu nombre, donde lo lee tu pareja", "Gonzalo", Emulador.lee("/users/${store.accUid}/name"))

        // Otro dispositivo (o Azabache del ordenador) cambia cosas: llegan aquí
        val a = otra.coleccion("ajustes")
        a.pon("azabache_general", mapOf("recsdone" to "11,12,13", "goal_1_2026" to 12L, "diario_todo" to false, "guide_last" to JSONObject().put("https://g", JSONObject().put("u", "https://g/3").put("t", "Capítulo 3").put("at", System.currentTimeMillis())).toString()))
        a.pon("azabache_descubrir", mapOf("f_plats" to "pc,switch", "f_years" to ""))
        a.pon("azabache_duelos", mapOf("pool" to "[1,2,3,4]", "saltados" to "[[3,4]]"))
        a.pon("azabache_escribir", mapOf("fuente" to 2L, "tam" to 18.5))
        a.pon("azabache_actualizador", mapOf("on" to true))
        sincroniza()
        espera("llegan las recomendaciones vistas") { store.recsDone == setOf(11L, 12L, 13L) }
        assertEquals(12, store.goalPropio(1, 2026)); assertEquals(30, store.goalPropio(0, 2026))
        assertEquals(false, ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE).getBoolean("diario_todo", true))
        assertEquals("https://g/3", WebActivity.lastPage(ctx, "https://g")?.first)
        val d = ctx.getSharedPreferences("descubrir", Context.MODE_PRIVATE)
        assertEquals("pc,switch", d.getString("f_plats", "")); assertTrue("el filtro que se quitó allí, también aquí", !d.contains("f_years"))
        DuelosEstado(ctx).let { e -> assertEquals(listOf(1L, 2L, 3L, 4L), e.pool); assertEquals(setOf(3L to 4L), e.saltados); assertEquals(setOf(9L), e.ordenados) }
        val e = ctx.getSharedPreferences("escribir", Context.MODE_PRIVATE)
        assertEquals(2, e.getInt("fuente", 0)); assertEquals(18.5f, e.getFloat("tam", 0f)); assertEquals(true, e.getBoolean("maquina", false))
        assertEquals("{\"pid\":\"x\"}", e.getString("sprint", ""))
        assertTrue(Actualizador.activo(ctx))
    }

    @Test fun cambiosALaVezGanaElMasReciente() {
        entra()
        val a = otra.coleccion("ajustes")
        store.avisoLectura = 20
        a.pon("azabache_general", mapOf("aviso_lectura" to 8L, "aviso_diario" to 22L), System.currentTimeMillis() - 60_000L)
        sincroniza()
        espera("de cada ajuste, el más reciente") { store.avisoDiario == 22 }
        assertEquals(20, store.avisoLectura)
        assertEquals(20L, enLaCuenta("azabache_general")!!.entero("aviso_lectura"))
        store.avisoLectura = 19
        a.pon("azabache_general", mapOf("aviso_lectura" to 7L), System.currentTimeMillis() + 60_000L)
        sincroniza()
        espera("gana lo de allí") { store.avisoLectura == 7 }
    }

    @Test fun loDeAntesDeLosDosDispositivosSeJuntaSinPerderNada() {
        val antes = System.currentTimeMillis() - 3_600_000L
        // El otro dispositivo ya pasó lo suyo: unas recomendaciones vistas, libros ya ordenados por duelos, un sprint, una guía y un aviso
        val o = otra.coleccion("ajustes")
        o.pon("azabache_general", mapOf("recsdone" to "1,2", "guides_7" to JSONArray().put(JSONObject().put("n", "Wiki").put("u", "https://wiki")).toString(),
            "guide_last" to JSONObject().put("https://a", JSONObject().put("u", "https://a/9").put("at", antes + 5)).toString(), "aviso_diario" to 21L), antes - 60_000L)
        o.pon("azabache_duelos", mapOf("ordenados" to "[4,5]"), antes - 60_000L)
        o.pon("azabache_escribir", mapOf("sprints" to JSONArray().put(JSONObject().put("pid", "obra").put("desde", 1L)).toString()), antes - 60_000L)
        assertTrue(otra.sincroniza().ok)
        // Este móvil tenía lo suyo (la versión que aún no lo subía)
        ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE).edit().putString("recsdone", "2,3")
            .putString("guides_7", JSONArray().put(JSONObject().put("n", "Mapa").put("u", "https://mapa")).toString())
            .putString("guide_last", JSONObject().put("https://a", JSONObject().put("u", "https://a/2").put("at", antes)).put("https://b", JSONObject().put("u", "https://b/1").put("at", antes)).toString())
            .putInt("aviso_diario", 20).commit()
        ctx.getSharedPreferences("duelos", Context.MODE_PRIVATE).edit().putString("ordenados", "[5,6]").commit()
        ctx.getSharedPreferences("escribir", Context.MODE_PRIVATE).edit().putString("sprints", JSONArray().put(JSONObject().put("pid", "obra").put("desde", 2L)).toString()).commit()
        ctx.getSharedPreferences("espejo_cuenta", Context.MODE_PRIVATE).edit().apply { listOf("gamerate", "duelos", "escribir").forEach { putLong("hora:$it", antes) } }.commit()
        entra()
        enLaCuenta("azabache_general")!!.let { n ->
            assertEquals("las recomendaciones vistas de los dos", setOf("1", "2", "3"), n.texto("recsdone").split(',').toSet())
            assertEquals("las guías de los dos", setOf("https://wiki", "https://mapa"), JSONArray(n.texto("guides_7")).let { x -> (0 until x.length()).map { x.getJSONObject(it).getString("u") }.toSet() })
            val ul = JSONObject(n.texto("guide_last"))
            assertEquals("de cada guía, lo más reciente", "https://a/9", ul.getJSONObject("https://a").getString("u")); assertEquals("https://b/1", ul.getJSONObject("https://b").getString("u"))
            assertEquals("un ajuste suelto: el que se cambió más tarde", 20L, n.entero("aviso_diario"))
        }
        assertEquals(setOf(4L, 5L, 6L), JSONArray(enLaCuenta("azabache_duelos")!!.texto("ordenados")).let { x -> (0 until x.length()).map { x.getLong(it) }.toSet() })
        assertEquals(2, JSONArray(enLaCuenta("azabache_escribir")!!.texto("sprints")).length())
        espera("y aquí, lo de los dos") { store.recsDone == setOf(1L, 2L, 3L) }
        assertEquals(setOf(4L, 5L, 6L), DuelosEstado(ctx).ordenados)
        assertEquals(2, SprintEscribir.historial(ctx).size)
    }

    /** Lo último que te salió en Descubrir (que ya subía para tu pareja) también llega a tus otros dispositivos: de cada tipo, lo más reciente. */
    @Test fun elUltimoTestDeDescubrirLlegaATusOtrosDispositivos() {
        val s = Store(ctx)
        s.saveQuiz(1, listOf(QuizItem(1, false, 1L, "Solaris", "", "1961", "", "Lem", match = 90)))
        val aqui = JSONObject(s.syncData()).getJSONObject("quiz")
        val otro = JSONObject().put("0", quizRunToJson(QuizRun(System.currentTimeMillis() + 1_000, listOf(QuizItem(0, false, 5L, "Hades", "", "2020", "", "Supergiant", match = 80)))))
            .put("1", quizRunToJson(QuizRun(1L, emptyList())))
        s.mergeRemote(JSONObject().put("quiz", otro).toString())
        assertEquals("Hades", s.quizzes[0]!!.items.single().title)
        assertEquals("el de aquí, más nuevo, se queda", aqui.getJSONObject("1").getLong("t"), s.quizzes[1]!!.time)
    }
}
