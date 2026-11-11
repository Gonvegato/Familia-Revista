package com.gonzalo.ammit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ammit: quién pasa el juicio, cómo se colocan las apps, y cómo se ven sus pantallas (con -Proborazzi.test.record=true, en build/outputs/roborazzi). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-xhdpi")
class AmmitTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val grabar = System.getProperty("roborazzi.test.record") == "true"

    @Before fun limpia() { Diseno.sinCapas = true; Vigia.vivo = null; Cortina.reiniciar(); limpia0() }
    private fun limpia0() { ctx.getSharedPreferences("ammit", Context.MODE_PRIVATE).edit().clear().commit(); Ammit.cargar(ctx) }

    @Test fun pasanLaFamiliaYLoImprescindible() {
        listOf("com.gonzalo.gamerate", "com.gonzalo.hiedra", "com.gonzalo.marfil", "com.gonzalo.cobalto", "com.gonzalo.mostaza", "com.gonzalo.cobre")
            .forEach { assertTrue(it, Ammit.pasa(it)) }
        assertTrue(Ammit.pasa("com.gonzalo.ammit")); assertTrue(Ammit.pasa("com.android.systemui"))
        assertTrue("el instalador (las versiones nuevas de la familia)", Ammit.pasa("com.google.android.packageinstaller"))
        listOf("com.instagram.android", "com.google.android.youtube", "com.android.settings", "com.sec.android.app.camera", "com.gonzalo.ptah")
            .forEach { assertFalse(it, Ammit.pasa(it)) }
        assertTrue("el teclado que tengas", Ammit.pasa("com.samsung.android.honeyboard", setOf("com.samsung.android.honeyboard")))
    }

    @Test fun reconoceSusVersionesEnElKDrive() {
        assertEquals(listOf(0, 3, 0), Actualizador.versionDe("Ammit-0.3.0.apk"))
        assertEquals(null, Actualizador.versionDe("Jonsu-1.8.6.apk"))
        assertEquals("versiones", KDrive.CARPETA)
        val u = Actualizador.ultima(listOf("Ammit-0.2.0.apk" to 1L, "Ammit-0.10.0.apk" to 2L, "Nut-0.9.0.apk" to 3L))
        assertEquals("Ammit-0.10.0.apk", u?.first)
    }

    @Test fun lasAppsSeColocanComoLasDejaste() {
        val l = listOf("b", "a", "c", "d")
        assertEquals(listOf("c", "a", "b", "d"), Ammit.ordena(l, { it }, { it }, listOf("c", "a")))
        assertEquals(listOf("a", "b", "c", "d"), Ammit.ordena(l, { it }, { it }, emptyList()))
    }

    @Test fun seEnciendeYSeAcuerda() {
        assertFalse(Ammit.encendida)
        Ammit.encender(ctx, true)
        Ammit.cargar(ctx)
        assertTrue(Ammit.encendida)
        Ammit.widgets(ctx).let { assertTrue(it.isEmpty()) }
        Ammit.ponWidgets(ctx, listOf(Ammit.Widget(3, 170)))
        assertEquals(listOf(Ammit.Widget(3, 170)), Ammit.widgets(ctx))
    }

    @Test fun alEncenderYApagarHayUnaAnimacionUnaSolaVez() {
        Ammit.encender(ctx, true)
        assertEquals("entrar", Ammit.tomaAnimacion(ctx))
        assertEquals(null, Ammit.tomaAnimacion(ctx))
        Ammit.encender(ctx, true)
        assertEquals("sin cambio, sin animación", null, Ammit.tomaAnimacion(ctx))
        Ammit.encender(ctx, false)
        assertEquals("salir", Ammit.tomaAnimacion(ctx))
    }

    @Test fun apagadaElInicioPasaAlDeSiempre() {
        val pm = shadowOf(ctx.packageManager)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.addResolveInfoForIntent(home, resolve("com.sec.android.app.launcher", "com.android.launcher3.uioverrides.QuickstepLauncher", "One UI Home"))
        assertEquals(ComponentName("com.sec.android.app.launcher", "com.android.launcher3.uioverrides.QuickstepLauncher"), Ammit.inicioNormal(ctx))
    }

    private fun resolve(paquete: String, clase: String, nombre: String) = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            packageName = paquete; name = clase; nonLocalizedLabel = nombre
            applicationInfo = ApplicationInfo().apply { packageName = paquete; nonLocalizedLabel = nombre }
        }
        nonLocalizedLabel = nombre
    }

    private fun sembrarApps() {
        val pm = shadowOf(ctx.packageManager)
        val l = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        listOf("com.gonzalo.gamerate" to "Jonsu", "com.gonzalo.hiedra" to "Bastet", "com.gonzalo.marfil" to "Seshat", "com.gonzalo.cobalto" to "Thot",
            "com.gonzalo.mostaza" to "Hathor", "com.gonzalo.cobre" to "Nut", "com.instagram.android" to "Instagram", "com.google.android.youtube" to "YouTube",
            "com.spotify.music" to "Spotify", "com.android.chrome" to "Chrome", "com.whatsapp" to "WhatsApp", "com.sec.android.app.camera" to "Cámara")
            .forEach { (p, n) -> pm.addResolveInfoForIntent(l, resolve(p, "$p.Main", n)) }
    }

    @Test fun capturas() {
        org.junit.Assume.assumeTrue(grabar)
        sembrarApps()
        Ammit.encender(ctx, true, animar = false)
        val inicio = Robolectric.buildActivity(InicioActivity::class.java).setup().get()
        inicio.apps = Apps.todas(inicio)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))
        inicio.window.decorView.rootView.captureRoboImage("build/outputs/roborazzi/ammit_inicio.png")
        inicio.colocando = true
        shadowOf(android.os.Looper.getMainLooper()).idle()
        inicio.window.decorView.rootView.captureRoboImage("build/outputs/roborazzi/ammit_inicio_colocando.png")

        val juicio = Robolectric.buildActivity(JuicioActivity::class.java, Intent(ctx, JuicioActivity::class.java).putExtra(JuicioActivity.EXTRA_APP, "YouTube")).setup().get()
        juicio.window.decorView.rootView.captureRoboImage("build/outputs/roborazzi/ammit_juicio.png")

        val aj = Robolectric.buildActivity(AjustesActivity::class.java).setup().get()
        aj.window.decorView.rootView.captureRoboImage("build/outputs/roborazzi/ammit_ajustes.png")
    }

    /** El campo de cadenas: siempre el mismo para el mismo tamaño, 82 cadenas, y todas cruzan la pantalla. */
    @Test fun elCampoEsSiempreElMismo() {
        val a = Campo.de(2560, 1600)
        assertEquals(82, a.n)
        val b = Campo.de(2560, 1600)
        assertTrue(a === b)
        a.cadenas.forEach { c ->
            // de fuera a fuera: los dos extremos, fuera de la pantalla
            fun fuera(x: Float, y: Float) = x < 0 || y < 0 || x > 2560 || y > 1600
            assertTrue(fuera(c.x1, c.y1) && fuera(c.x2, c.y2))
        }
        // los tiempos: el juicio acaba con todo tapado y apagado como el fondo
        assertEquals(1f, Tiempos.juicioBase(Tiempos.JUICIO_MS.toFloat()))
        assertEquals(Campo.OSCURO, Tiempos.juicioOscuro(Tiempos.JUICIO_MS.toFloat()))
        assertEquals(1f, Tiempos.juicio(Tiempos.JUICIO_MS.toFloat(), a.n - 1))
        assertEquals(0f, Tiempos.salir(Tiempos.salidaFin(a.n).toFloat(), 0, a.n))
    }

    /** El candado: 300 palabras desde que la encendiste (sumando dispositivos y días) o un capítulo leído de verdad. */
    @Test fun elCandadoCuentaPalabrasYCapitulos() {
        fun n(ref: String, vararg c: Pair<String, Any?>) = com.gonzalo.familia.nucleo.Nodo(ref, mapOf(*c))
        val dia = "2026-11-11"
        // Al encender: la tablet llevaba 500 palabras hoy; el móvil, 40. Un libro por el capítulo 3 con 20 min leídos hoy, y 2 h de un juego
        val palabras0 = listOf(n("tablet", "d2026-11-10" to 900, "d$dia" to 500), n("movil", "d$dia" to 40))
        val lectura0 = listOf(n("kid1", "bestLabel" to "Capítulo 3", "bestFrac" to 0.30))
        val tiempo0 = listOf(n("kid1~tablet", "ref" to "kid1", "d$dia" to 20 * 60_000), n("zelda~tablet", "ref" to "zelda", "d$dia" to 7_200_000))
        val base = Candado.base(dia, palabras0, tiempo0, lectura0)
        val desde = 1_000L
        // Nada hecho: 0 palabras, sin capítulo
        Candado.calcula(base, dia, desde, palabras0, tiempo0, lectura0, emptyList()).let { assertEquals(0, it.palabras); assertFalse(it.capitulo); assertFalse(it.puede) }
        // 150 en la tablet y 160 mañana en el móvil = 310: se suelta
        val palabras1 = listOf(n("tablet", "d2026-11-10" to 900, "d$dia" to 650), n("movil", "d$dia" to 40, "d2026-11-12" to 160))
        Candado.calcula(base, dia, desde, palabras1, tiempo0, lectura0, emptyList()).let { assertEquals(310, it.palabras); assertTrue(it.puede) }
        // Horas de juego no cuentan como lectura; saltar de capítulo sin leer, tampoco
        val tiempoJuego = listOf(n("kid1~tablet", "ref" to "kid1", "d$dia" to 21 * 60_000), n("zelda~tablet", "ref" to "zelda", "d$dia" to 9_000_000))
        val salto = listOf(n("kid1", "bestLabel" to "Capítulo 4", "bestFrac" to 0.36))
        Candado.calcula(base, dia, desde, palabras0, tiempoJuego, salto, emptyList()).let { assertFalse(it.capitulo); assertEquals(1, it.minutos) }
        // Pasar al capítulo 4 habiendo leído 6 min más: capítulo leído (también en otro dispositivo)
        val tiempoLeido = tiempo0 + n("kid1~movil", "ref" to "kid1", "d$dia" to 6 * 60_000)
        Candado.calcula(base, dia, desde, palabras0, tiempoLeido, salto, emptyList()).let { assertTrue(it.capitulo); assertTrue(it.puede) }
        // Leer mucho sin cambiar de capítulo no basta
        Candado.calcula(base, dia, desde, palabras0, tiempoLeido, lectura0, emptyList()).let { assertFalse(it.capitulo) }
        // Terminar un libro después de encenderla, sí; uno terminado antes, no
        Candado.calcula(base, dia, desde, palabras0, tiempo0, lectura0, listOf(n("kid9", "fin" to listOf(500L)))).let { assertFalse(it.capitulo) }
        Candado.calcula(base, dia, desde, palabras0, tiempo0, lectura0, listOf(n("kid9", "fin" to listOf(500L, 2_000L)))).let { assertTrue(it.capitulo) }
    }

    /** Desde «Ammit no te suelta», cuando ya se puede: se apaga CON la cortina de cadenas (la animación de apagar). */
    @Test fun alSoltarDesdeElCandadoHayAnimacion() {
        sembrarApps(); Ammit.encender(ctx, true, animar = false)
        val v = Robolectric.setupService(Vigia::class.java)
        v.javaClass.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(v)
        assertTrue(Vigia.vivo != null)
        val a = Robolectric.buildActivity(CandadoActivity::class.java).setup().get()
        a.apagar()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue("la cortina de salir tiene que estar puesta", Cortina.ocupada)
        assertFalse(Ammit.encendida)
    }

    /** El widget, apagada y encendida (y la vista previa para la lista de widgets). */
    @Test fun elWidget() {
        Ammit.encender(ctx, false, animar = false)
        val apagada = WidgetAmmit.dibujo(ctx, 300, 130)
        Ammit.encender(ctx, true, animar = false)
        val encendida = WidgetAmmit.dibujo(ctx, 300, 130)
        assertTrue(apagada.width > 0 && encendida.width > 0)
        if (grabar) {
            java.io.File("build/outputs/roborazzi").mkdirs()
            for ((n, b) in listOf("ammit_widget_apagada" to apagada, "ammit_widget_encendida" to encendida))
                java.io.FileOutputStream("build/outputs/roborazzi/$n.png").use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** Encendida y sin ser la pantalla de inicio: al ir a tu inicio de siempre, el vigilante pone la cortina y te lleva al de Ammit. Apagada, nada. */
    @Test fun alIrATuInicioTeLlevaAlDeAmmit() {
        val pm = shadowOf(ctx.packageManager)
        val casa = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.addResolveInfoForIntent(casa, resolve("com.oppo.launcher", "com.oppo.launcher.Launcher", "Inicio"))
        val v = Robolectric.setupService(Vigia::class.java)
        v.javaClass.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(v)
        fun evento() = android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = "com.oppo.launcher"; className = "com.oppo.launcher.Launcher" }
        Ammit.encender(ctx, false, animar = false)
        v.onAccessibilityEvent(evento())
        assertFalse("apagada no hace nada", Cortina.ocupada)
        Ammit.encender(ctx, true, animar = false)
        v.onAccessibilityEvent(evento())
        assertTrue("encendida, la cortina tapa tu inicio", Cortina.ocupada)
    }

    /** La pantalla de «Ammit no te suelta», con progreso a medias. */
    @Test fun capturaDelCandado() {
        org.junit.Assume.assumeTrue(grabar)
        captureRoboImage("build/outputs/roborazzi/ammit_candado.png") {
            PantCandado(null, Candado.Progreso(conCuenta = true, listo = true, palabras = 120, capitulo = false, libro = "El nombre del viento", minutos = 3))
        }
    }

    /** La cortina: cada modo, en varios momentos, pinta sin fallar (y con nombres de app larguísimos). Con grabar, deja las imágenes. */
    @Test fun laCortinaPintaSinFallar() {
        val (w, h) = Campo.pantalla(ctx)
        for ((modo, ms) in listOf("entrar" to listOf(0f, 300f, 900f, 1500f, 2000f), "salir" to listOf(0f, 100f, 600f, 900f, 1300f, 1800f),
            "juicio" to listOf(0f, 50f, 400f, 1000f, 2000f, 2700f, 3200f, 3700f, 3950f), "tapar" to listOf(0f))) {
            for (t in ms) for (app in listOf("Instagram", "Una aplicación con un nombre larguísimo que no cabe en ninguna parte")) {
                val v = VistaCortina(ctx, modo, app); v.relojFijo = t
                v.layout(0, 0, w, h)
                val bm = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val cv = android.graphics.Canvas(bm); cv.drawColor(0xFF5C7A9E.toInt())
                v.draw(cv)
                if (grabar && app == "Instagram") {
                    java.io.File("build/outputs/roborazzi").mkdirs()
                    java.io.FileOutputStream("build/outputs/roborazzi/cortina_${modo}_${t.toInt()}.png").use { bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                }
                bm.recycle()
            }
        }
    }

    /** El camino de encender y apagar: sin vigilante se hace a la antigua; con la cortina puesta, la pantalla de inicio espera y luego saca las apps. */
    @Test fun encenderYApagarSinVigilanteVaALaAntigua() {
        assertFalse(Cortina.cambiar(ctx, true) {})
        assertFalse(Cortina.ocupada)
        sembrarApps(); Ammit.encender(ctx, true, animar = false)
        Ammit.ponAnimacion(ctx, "cortina")
        val inicio = Robolectric.buildActivity(InicioActivity::class.java).setup().get()
        assertEquals("cortina", inicio.animacion)
        Cortina.alQuitar?.invoke()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        // (ya ha dejado de estar tapada: o salen las apps, o ya han salido)
        assertTrue(inicio.animacion == "aparecer" || inicio.animacion == null)
    }

    /** Fotogramas de encender, apagar y del juicio (como en la tablet). */
    @Test fun fotogramas() {
        org.junit.Assume.assumeTrue(grabar)
        fun campo(nombre: String, pinta: (Campo, android.graphics.Canvas) -> Unit) {
            val bm = android.graphics.Bitmap.createBitmap(1280, 800, android.graphics.Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(bm); cv.drawColor(0xFF5C7A9E.toInt())
            pinta(Campo.de(1280, 800), cv)
            java.io.File("build/outputs/roborazzi").mkdirs()
            java.io.FileOutputStream("build/outputs/roborazzi/$nombre.png").use { bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        listOf(500f, 900f, 1300f, 1700f).forEach { ms -> campo("ammit_entrar_${ms.toInt()}") { c, cv -> c.pinta(cv, Tiempos.entrarOscuro(ms), Tiempos.entrarBase(ms)) { Tiempos.entrar(ms, it) } } }
        listOf(0f, 400f, 800f, 1100f).forEach { ms -> campo("ammit_salir_${ms.toInt()}") { c, cv -> c.pinta(cv, Campo.OSCURO, Tiempos.salirBase(ms)) { Tiempos.salir(ms, it, c.n) } } }
        listOf(600f, 1500f, 2800f, 3300f, 3950f).forEach { ms ->
            captureRoboImage("build/outputs/roborazzi/juicio_${ms.toInt()}.png") { PantJuicio("Instagram", ms) }
        }
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun capturasDeNoche() {
        org.junit.Assume.assumeTrue(grabar)
        val juicio = Robolectric.buildActivity(JuicioActivity::class.java, Intent(ctx, JuicioActivity::class.java).putExtra(JuicioActivity.EXTRA_APP, "Instagram")).setup().get()
        juicio.window.decorView.rootView.captureRoboImage("build/outputs/roborazzi/ammit_juicio_noche.png")
        val aj = Robolectric.buildActivity(AjustesActivity::class.java).setup().get()
        aj.window.decorView.rootView.captureRoboImage("build/outputs/roborazzi/ammit_ajustes_noche.png")
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-port-xhdpi") fun capturaEnVertical() {
        org.junit.Assume.assumeTrue(grabar)
        sembrarApps(); Ammit.encender(ctx, true, animar = false)
        val inicio = Robolectric.buildActivity(InicioActivity::class.java).setup().get()
        inicio.apps = Apps.todas(inicio)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))
        inicio.window.decorView.rootView.captureRoboImage("build/outputs/roborazzi/ammit_inicio_vertical.png")
        @Suppress("UNUSED_VARIABLE") val m = Modifier.fillMaxSize()
    }
}
