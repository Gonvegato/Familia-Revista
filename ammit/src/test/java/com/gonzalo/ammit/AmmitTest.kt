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

    @Before fun limpia() { ctx.getSharedPreferences("ammit", Context.MODE_PRIVATE).edit().clear().commit(); Ammit.cargar(ctx) }

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
        Ammit.encender(ctx, true)
        val inicio = Robolectric.buildActivity(InicioActivity::class.java).setup().get()
        inicio.apps = Apps.todas(inicio)
        shadowOf(android.os.Looper.getMainLooper()).idle()
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
        sembrarApps(); Ammit.encender(ctx, true)
        val inicio = Robolectric.buildActivity(InicioActivity::class.java).setup().get()
        inicio.apps = Apps.todas(inicio)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        inicio.window.decorView.rootView.captureRoboImage("build/outputs/roborazzi/ammit_inicio_vertical.png")
        @Suppress("UNUSED_VARIABLE") val m = Modifier.fillMaxSize()
    }
}
