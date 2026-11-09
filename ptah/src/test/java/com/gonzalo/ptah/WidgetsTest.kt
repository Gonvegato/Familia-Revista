package com.gonzalo.ptah

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.AnalogClock
import android.widget.FrameLayout
import android.widget.TextClock
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * El widget de Ptah: cada tipo se dibuja en todos los tamaños (de 40 × 40 dp a 4×4 y apaisados), en claro y en oscuro; los relojes llevan
 * su `TextClock` y su `AnalogClock` (con la letra de la familia y las agujas del diseño, comprobado inflando de verdad lo que se manda al
 * lanzador); las zonas hacen lo que dicen. Con -Proborazzi.test.record=true deja las vistas previas: `w_prev_ptah.png` (la del selector
 * de widgets, va a res/drawable-nodpi) y las de cada plantilla en varios tamaños (en build/outputs/widgets, o donde diga -Dcapturas).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetsTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun prepara() {
        Skin.cargar(ctx)
        Skin.origenAtrio = false; Skin.invertido = false; Skin.fondoId = ""; Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0L; Modo.eleccion = 0
        Widgets.datos = { DatosDeMentira(it) }
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After fun suelta() { Widgets.datos = null; Disenos.fijos = null; Modo.eleccion = 0; Minutero.ponLetraDelMovil(ctx, false) }

    private val tamanos = listOf(40f to 40f, 57f to 102f, 130f to 102f, 203f to 102f, 276f to 102f, 130f to 220f, 276f to 220f, 276f to 337f, 276f to 454f,
        554f to 117f, 412f to 51f, 350f to 60f, 60f to 350f, 110f to 110f, 70f to 70f, 57f to 80f, 64f to 150f, 70f to 146f, 48f to 120f)

    private fun todos(): List<Diseno> = Plantillas.TODAS.map { it.first } + Tipos.TODOS.map { it.nuevo() } +
        listOf(RelojDigital.nuevo().con("cabecera", "columna"), RelojDigital.nuevo().con("cabecera", "titular"), RelojDigital.nuevo().con("formato", "movil"),
            Accesos.nuevo().con("items", listOf("app:com.android.chrome", "app:com.whatsapp", "web:elpais.com|El País")).con("mostrar", "texto"),
            Foto.nuevo().con("sangre", true).con("pie", "El verano"), RelojAgujas.nuevo().con("numeros", "arabes").con("segundero", true))

    @Test fun cadaTipoSeDibujaEnTodosLosTamanos() {
        val datos = DatosDeMentira(ctx)
        for (d in todos()) for ((w, h) in tamanos) for (noche in listOf(false, true)) for (enWidget in listOf(false, true)) {
            val l = Pintor.pinta(ctx, d, w, h, noche, 1f, datos, enWidget)
            val que = "${d.nombre} (${d.tipo}) ${w}x$h ${if (noche) "noche" else "día"} ${if (enWidget) "widget" else "app"}"
            assertTrue(que, l.bm.width > 0 && l.bm.height > 0)
            // Lo primero, el toque de todo el widget; las zonas y lo vivo, dentro
            assertEquals(que, android.graphics.RectF(0f, 0f, w, h), l.zonas.first().r)
            assertTrue(que, l.zonas.all { it.r.left >= -0.5f && it.r.top >= -0.5f && it.r.right <= w + 0.5f && it.r.bottom <= h + 0.5f })
            assertTrue(que, l.vivos.all { it.r.left >= -0.5f && it.r.top >= -0.5f && it.r.right <= w + 0.5f && it.r.bottom <= h + 0.5f && it.r.width() > 0f })
            if (!enWidget) assertTrue("$que: en la app no hay nada vivo", l.vivos.isEmpty())
        }
    }

    @Test fun losRelojesConLaLetraDelMovilSeMuevenSolos() {
        val datos = DatosDeMentira(ctx)
        // El digital: la hora y la fecha son TextClock; la hora cabe entera con su letra (también «23:59»)
        val l = Pintor.pinta(ctx, RelojDigital.nuevo(), 276f, 220f, false, 1f, datos, enWidget = true, relojVivo = true)
        val textos = l.vivos.filterIsInstance<Vivo.Texto>()
        assertEquals("la fecha va dibujada; la hora, TextClock", listOf(Formatos.HORA), textos.map { it.formato })
        for (t in textos) {
            val p = l.tp(l.letraFija(t.letra), t.tam, 0, t.letra.espaciado)
            val ancho = Formatos.muestras(t.formato).maxOf { p.measureText(if (t.letra.mayusculas) it.uppercase() else it) }
            assertTrue("${t.formato} cabe a lo ancho", ancho <= t.r.width())
            assertTrue("${t.formato} cabe a lo alto", l.altoLinea(t.letra, t.tam) <= t.r.height() + 0.5f)
        }
        assertTrue("la hora, grande", textos.last().tam > 60f)
        // El de agujas: un AnalogClock cuadrado, con el segundero si se pide
        val a = Pintor.pinta(ctx, RelojAgujas.nuevo().con("segundero", true), 130f, 130f, false, 1f, datos, enWidget = true, relojVivo = true).vivos.single() as Vivo.Agujas
        assertEquals(a.r.width(), a.r.height(), 0.01f); assertNotNull(a.segundero)
        // «Como el móvil»: 24 horas o 12, lo que diga el móvil
        val m = Pintor.pinta(ctx, RelojDigital.nuevo().con("formato", "movil"), 276f, 220f, false, 1f, datos, enWidget = true, relojVivo = true).vivos.filterIsInstance<Vivo.Texto>().last()
        assertEquals(Formatos.HORA, m.formato); assertEquals(Formatos.HORA_12, m.formato12)
        // En la app, la hora se dibuja (la de mentira: 10:08): el dibujo cambia con la hora
        val b1 = Pintor.pinta(ctx, RelojDigital.nuevo(), 276f, 220f, false, 1f, datos).bm
        val otra = object : Datos by datos { override fun ahora() = DatosDeMentira.AHORA.withHour(23).withMinute(59) }
        val b2 = Pintor.pinta(ctx, RelojDigital.nuevo(), 276f, 220f, false, 1f, otra).bm
        assertFalse(b1.sameAs(b2))
    }

    /** Todas las vistas de un árbol. */
    private fun vistas(v: View): List<View> = listOf(v) + ((v as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { vistas(g.getChildAt(it)) } } ?: emptyList())

    @Test fun conLaLetraDelMovilSeMandanTextClock() {
        Minutero.ponLetraDelMovil(ctx, true)
        val d = RelojDigital.nuevo()
        Disenos.fijos = listOf(d)
        // (lo que hace el lanzador: inflar los RemoteViews con los recursos de Ptah y aplicar cada acción; un método que no se pueda llamar
        // desde un widget, o una letra que no se pueda poner, fallaría aquí)
        val v = Widgets.vistas(ctx, 5, d, 276f, 220f).apply(ctx, FrameLayout(ctx))
        val relojes = vistas(v).filterIsInstance<TextClock>()
        // con la apariencia «como el móvil»: el dibujo claro y el oscuro, cada uno con su hora (la fecha va dibujada: cambia a medianoche)
        assertEquals(2, relojes.size)
        assertTrue(relojes.none { it.format24Hour == Formatos.FECHA_LARGA })
        val hora = relojes.first { it.format24Hour == Formatos.HORA }
        assertEquals(Formatos.HORA, hora.format12Hour.toString())
        // La letra es la de la familia (la del recurso, no la del sistema)
        val titulos = Letras.fuente(ctx, R.font.display_semibold)
        assertNotEquals(Typeface.DEFAULT, hora.typeface)
        assertEquals(titulos, hora.typeface)
        // Su letra se ajusta a su hueco (autoSize): llena el sitio aunque el lanzador lo haga algo más grande
        assertEquals(android.widget.TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, hora.autoSizeTextType)
        // Con un color fijo (sin depender del claro y del oscuro), un solo dibujo
        val fijo = d.conEstilo(Estilo(fondo = TipoFondo.COLOR, colorFondo = "#12393A", tinta = "#F2E6CF", acento = "#D98A3D", secundario = "#AAAAAA"))
        assertEquals(false, Widgets.noche(fijo))
        val v2 = Widgets.vistas(ctx, 5, fijo, 276f, 220f).apply(ctx, FrameLayout(ctx))
        val r2 = vistas(v2).filterIsInstance<TextClock>()
        assertEquals(1, r2.size)
        assertEquals(0xFFF2E6CF.toInt(), r2.first { it.format24Hour == Formatos.HORA }.currentTextColor)
    }

    @Test fun lasAgujasSonLasDelDiseno() {
        Minutero.ponLetraDelMovil(ctx, true)
        val d = RelojAgujas.nuevo().con("segundero", true).conEstilo(Estilo(fondo = TipoFondo.COLOR, colorFondo = "#FFFFFF", tinta = "#111111", acento = "#C8372D", secundario = "#888888",
            piezas = mapOf("horaria" to "#123456", "minutera" to "#654321", "segundero" to "#FF0000")))
        val v = Widgets.vistas(ctx, 6, d, 130f, 130f).apply(ctx, FrameLayout(ctx))
        val reloj = vistas(v).filterIsInstance<AnalogClock>().single()
        // Android 12 o más: las agujas son las máscaras del diseño (del tamaño de la esfera vacía), cada una con su color
        val lado = Widgets.ladoAgujas(Pintor.pinta(ctx, d, 130f, 130f, false, 1f, DatosDeMentira(ctx), enWidget = true, relojVivo = true).vivos.single().r, ctx.resources.displayMetrics.density, 1)
        fun campo(n: String) = AnalogClock::class.java.getDeclaredField(n).apply { isAccessible = true }.get(reloj)
        val horaria = campo("mHourHand") as android.graphics.drawable.Drawable
        assertEquals(lado, horaria.intrinsicWidth)
        assertEquals(lado, (campo("mDial") as android.graphics.drawable.Drawable).intrinsicWidth)
        assertNotNull(campo("mSecondHand"))
        assertEquals(0xFF123456.toInt(), reloj.hourHandTintList!!.defaultColor)
        assertEquals(0xFF654321.toInt(), reloj.minuteHandTintList!!.defaultColor)
        assertEquals(0xFFFF0000.toInt(), reloj.secondHandTintList!!.defaultColor)
    }

    @Test fun lasZonasHacenLoQueDicen() {
        val datos = DatosDeMentira(ctx)
        fun zonas(d: Diseno, w: Float = 276f, h: Float = 102f) = Pintor.pinta(ctx, d, w, h, false, 1f, datos, enWidget = true).zonas.map { it.accion }
        assertEquals(listOf(Accion.Buscar(""), Accion.BuscarVoz), zonas(Buscador.nuevo()))
        assertEquals(listOf(Accion.Buscar("org.mozilla.firefox")), zonas(Buscador.nuevo().con("navegador", "org.mozilla.firefox").con("voz", false)))
        assertEquals(listOf(Accion.Editar("d1"), Accion.App("com.android.chrome"), Accion.Web("https://elpais.com")),
            zonas(Accesos.nuevo().copy(id = "d1").con("items", listOf("app:com.android.chrome", "web:elpais.com|El País"))))
        assertEquals(Accion.Alarmas, zonas(RelojDigital.nuevo()).single())
        assertEquals(Accion.Alarmas, zonas(RelojAgujas.nuevo()).single())
        assertEquals(Accion.Calendario, zonas(Calendario.nuevo()).single())
        assertEquals(Accion.Bateria, zonas(TipoBateria.nuevo()).single())
        val nota = TipoNota.nuevo()
        assertEquals(Accion.Editar(nota.id), zonas(nota).single())
        // Cada acción abre algo
        for (a in listOf(Accion.AbrirPtah, Accion.Editar("d1"), Accion.App("no.existe"), Accion.Web("https://elpais.com"), Accion.Buscar(""), Accion.BuscarVoz, Accion.Alarmas,
                Accion.Calendario, Accion.Bateria, Accion.Configurar)) assertNotNull("$a", Widgets.intent(ctx, a, 3))
        assertEquals(Intent.ACTION_WEB_SEARCH, Widgets.intent(ctx, Accion.Buscar(""))!!.action)
        assertEquals(android.provider.AlarmClock.ACTION_SHOW_ALARMS, Widgets.intent(ctx, Accion.Alarmas)!!.action)
        assertEquals("d1", Widgets.intent(ctx, Accion.Editar("d1"))!!.getStringExtra(Widgets.EXTRA_EDITAR))
        val conf = Widgets.intent(ctx, Accion.Configurar, 3)!!
        assertEquals(ConfigWidgetActivity::class.java.name, conf.component!!.className); assertEquals(3, conf.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
    }

    @Test fun unWidgetSinDisenoTeDejaElegirUno() {
        Disenos.fijos = emptyList()
        Widgets.asigna(ctx, 9, "dborrado0000")
        val v = Widgets.vistas(ctx, 9, Disenos.de(ctx, "dborrado0000"), 130f, 130f).apply(ctx, FrameLayout(ctx))
        assertTrue(vistas(v).any { it.contentDescription?.contains("Elige un diseño") == true || it.contentDescription?.contains("Widgets") == true })
        val l = Pintor.pinta(ctx, Diseno("sin9", "Elige un diseño", TipoNota.id), 130f, 130f, false, 1f, DatosDeMentira(ctx), enWidget = true)
        assertEquals("sin9", (l.zonas.first().accion as Accion.Editar).id)
        assertEquals(ConfigWidgetActivity::class.java.name, Widgets.intent(ctx, l.zonas.first().accion, 9)!!.component!!.className)
    }

    @Test fun losColoresDeApariencia() {
        val d = RelojDigital.nuevo()
        val dia = Pintor.pinta(ctx, d, 100f, 100f, false, 1f, DatosDeMentira(ctx))
        val noche = Pintor.pinta(ctx, d, 100f, 100f, true, 1f, DatosDeMentira(ctx))
        assertNotEquals("claro y oscuro tienen papeles distintos", dia.bm.getPixel(50, 50), noche.bm.getPixel(50, 50))
        Modo.eleccion = 0; assertNull(Widgets.noche(d))
        Modo.eleccion = 2; assertEquals(true, Widgets.noche(d))
        Modo.eleccion = 1; assertEquals(false, Widgets.noche(d))
        // Transparente: las esquinas y el fondo, sin nada
        val t = Pintor.pinta(ctx, Buscador.nuevo().conEstilo(Estilo(fondo = TipoFondo.TRANSPARENTE, borde = false)), 276f, 102f, false, 1f, DatosDeMentira(ctx))
        assertEquals(0, t.bm.getPixel(2, 2) ushr 24)
        // Con esquinas, la esquina queda recortada
        assertEquals(0, Pintor.pinta(ctx, TipoNota.nuevo(), 100f, 100f, false, 1f, DatosDeMentira(ctx)).bm.getPixel(0, 0) ushr 24)
    }

    // ---------------------------------------------------------------- la hora dibujada cada minuto (el fallo del OPPO)

    /** Lo que ve el lanzador: las vistas que no están ocultas. */
    private fun visibles(v: View): List<View> = if (v.visibility != View.VISIBLE) emptyList() else listOf(v) + ((v as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { visibles(g.getChildAt(it)) } } ?: emptyList())

    @Test fun laHoraVaDibujadaConLaLetraDelDisenoYSinDuplicados() {
        assertFalse(Minutero.relojVivo(ctx))
        for (d in listOf(RelojDigital.nuevo().con("cabecera", "columna"), RelojAgujas.nuevo().con("segundero", true))) {
            Disenos.fijos = listOf(d)
            // Como un lanzador que no vuelve a inflar el widget: lo nuevo se aplica encima de lo de antes, una y otra vez, con tamaños distintos
            val v = Widgets.vistas(ctx, 5, d, 130f, 220f).apply(ctx, FrameLayout(ctx))
            for ((w, h) in listOf(276f to 454f, 200f to 300f, 276f to 454f, 130f to 220f)) Widgets.vistas(ctx, 5, d, w, h).reapply(ctx, v)
            val todas = vistas(v)
            assertTrue("${d.tipo}: ningún TextClock", todas.none { it is TextClock })
            assertTrue("${d.tipo}: ningún AnalogClock", todas.none { it is AnalogClock })
            // Un solo dibujo (con la hora dentro), y las zonas de la última vez, no las de todas
            val imgs = visibles(v).filterIsInstance<android.widget.ImageView>().filter { it.drawable != null }
            assertEquals("${d.tipo}: un solo dibujo a la vista", 1, imgs.size)
            val zonas = Pintor.pinta(ctx, d, 130f, 220f, false, 1f, DatosDeMentira(ctx), enWidget = true).zonas.size
            assertEquals(zonas, (v.findViewById<ViewGroup>(R.id.w_zonas)).childCount)
            assertEquals(0, v.findViewById<ViewGroup>(R.id.w_vivos).childCount + v.findViewById<ViewGroup>(R.id.w_vivos_dia).childCount + v.findViewById<ViewGroup>(R.id.w_vivos_noche).childCount)
        }
        // El dibujo cambia con el minuto (10:08 → 10:09), con la letra del diseño
        val d = RelojDigital.nuevo()
        val b1 = Pintor.pinta(ctx, d, 276f, 220f, false, 1f, DatosDeMentira(ctx), enWidget = true).bm
        val luego = object : Datos by DatosDeMentira(ctx) { override fun ahora() = DatosDeMentira.AHORA.plusMinutes(1) }
        assertFalse(b1.sameAs(Pintor.pinta(ctx, d, 276f, 220f, false, 1f, luego, enWidget = true).bm))
    }

    @Test fun conLaLetraDelMovilHayUnaSolaHoraAunqueSeRepinte() {
        Minutero.ponLetraDelMovil(ctx, true)
        val d = RelojDigital.nuevo()
        Disenos.fijos = listOf(d)
        val v = Widgets.vistas(ctx, 5, d, 130f, 220f).apply(ctx, FrameLayout(ctx))
        for ((w, h) in listOf(276f to 454f, 200f to 300f, 276f to 454f)) Widgets.vistas(ctx, 5, d, w, h).reapply(ctx, v)
        val relojes = visibles(v).filterIsInstance<TextClock>()
        assertEquals("una hora a la vista", 1, relojes.size)
        assertEquals(1, relojes.count { it.format24Hour == Formatos.HORA })
        // (y en total, la de día y la de noche, nada más)
        assertEquals(2, vistas(v).count { it is TextClock })
        // En 1×2 de pie, con la letra del móvil: la cabecera («SÁB 7») y el mes, dibujados; las horas y los minutos, dos TextClock
        val pie = Pintor.pinta(ctx, d, 70f, 146f, false, 1f, DatosDeMentira(ctx), enWidget = true, relojVivo = true)
        assertEquals(listOf("HH", "mm"), pie.vivos.filterIsInstance<Vivo.Texto>().map { it.formato })
        val vacio = Pintor.pinta(ctx, d.conEstilo(d.estilo.copy(ocultas = setOf("antetitulo", "fecha"))), 70f, 146f, false, 1f, DatosDeMentira(ctx), enWidget = true, relojVivo = true)
        assertFalse("la cabecera se dibuja", pie.bm.sameAs(vacio.bm))
    }

    // ---------------------------------------------------------------- los tamaños que da el lanzador

    private fun opciones(minW: Int, maxW: Int, minH: Int, maxH: Int, tamanos: List<android.util.SizeF>? = null) = android.os.Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minW); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxW)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minH); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxH)
        if (tamanos != null) putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, ArrayList(tamanos))
    }

    @Test fun seMandaUnDibujoPorCadaTamanoDelLanzador() {
        // Con los tamaños que da el lanzador (Android 12 o más): esos, y nada más
        val dados = opciones(70, 150, 70, 150, listOf(android.util.SizeF(70f, 146f), android.util.SizeF(150f, 70f)))
        assertEquals(listOf(70f to 146f, 150f to 70f), Widgets.tamanosPara(ctx, dados))
        // Sin ellos: el de vertical (ancho mín. × alto máx.), el de apaisado y los de siempre
        val sin = Widgets.tamanosPara(ctx, opciones(64, 140, 70, 150))
        assertEquals(64f to 150f, sin[0]); assertEquals(140f to 70f, sin[1])
        assertTrue(Tamanos.dp(Tamanos.Celdas(1, 1)) in sin && Tamanos.dp(Tamanos.Celdas(1, 2)) in sin && Tamanos.dp(Tamanos.Celdas(4, 4)) in sin)
        // Cada variante, con su composición; y aplicándolo una y otra vez (con otros tamaños) no se acumula nada
        val d = RelojDigital.nuevo()
        Disenos.fijos = listOf(d)
        val notas = ArrayList<String>()
        val rv = Widgets.vistasDe(ctx, 5, d, dados, notas)
        assertEquals(2, notas.size)
        assertTrue(notas[0], notas[0].startsWith("70×146 dp: vertical")); assertTrue(notas[1], notas[1].startsWith("150×70 dp: normal"))
        val v = rv.apply(ctx, FrameLayout(ctx))
        for (o in listOf(dados, opciones(64, 140, 70, 150), dados, opciones(300, 300, 300, 300))) Widgets.vistasDe(ctx, 5, d, o).reapply(ctx, v)
        val imgs = visibles(v).filterIsInstance<android.widget.ImageView>().filter { it.drawable != null }
        assertEquals("un solo dibujo a la vista", 1, imgs.size)
        assertTrue(vistas(v).none { it is TextClock })
    }

    @Test fun elDiagnosticoLoCuentaSinSecretos() {
        val d = RelojDigital.nuevo().copy(nombre = "Mi reloj")
        Disenos.fijos = null
        Disenos.guarda(ctx, d)
        val mgr = shadowOf(AppWidgetManager.getInstance(ctx))
        val id = mgr.createWidget(WPtah::class.java, R.layout.w_marco)
        Widgets.asigna(ctx, id, d.id)
        kotlinx.coroutines.runBlocking { Widgets.pintarUno(ctx, id) }
        val t = Diagnostico.texto(ctx)
        println(t)
        for (que in listOf("== Widgets y este móvil ==", "Android", "Lanzador", "== La hora de los relojes ==", "Ahora: dibujada", "== Widget $id ==", "Mi reloj (${d.id}",
            "Ancho mín. / máx.", "Tamaños (OPTION_APPWIDGET_SIZES)", "Lo que se pintó", "hora dibujada", "== Tus diseños ==", "guardado aquí: ${d.id}")) assertTrue(que, t.contains(que))
        // Con la letra del móvil, lo dice y dice por qué
        Minutero.ponLetraDelMovil(ctx, true)
        assertTrue(Diagnostico.texto(ctx).contains("con la letra del móvil (TextClock), porque lo has elegido"))
        Minutero.ponLetraDelMovil(ctx, false)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertTrue(Diagnostico.texto(ctx).contains("porque Android no deja a Widgets poner alarmas exactas"))
        // Nada secreto: ni el uid, ni el usuario, ni el correo, ni claves
        assertFalse(t.contains("@")); assertFalse(t.lowercase().contains("token"))
        Disenos.borra(ctx, d.id)
    }

    @Test fun elMinuteroSoloAndaConRelojesDibujados() {
        val am = ctx.getSystemService(android.app.AlarmManager::class.java)
        val sombra = shadowOf(am)
        val mgr = shadowOf(AppWidgetManager.getInstance(ctx))
        val reloj = RelojDigital.nuevo(); val nota = TipoNota.nuevo()
        Disenos.fijos = listOf(reloj, nota)
        val id = mgr.createWidget(WPtah::class.java, R.layout.w_marco)
        Widgets.asigna(ctx, id, nota.id)
        assertFalse("sin relojes, sin alarma", Minutero.programa(ctx)); assertNull(sombra.peekNextScheduledAlarm())
        Widgets.asigna(ctx, id, reloj.id)
        assertTrue(Minutero.programa(ctx))
        val a = sombra.peekNextScheduledAlarm()!!
        assertEquals(android.app.AlarmManager.RTC, a.type)
        assertEquals(0L, (a.triggerAtTime - 150L) % 60_000L)
        assertTrue(a.triggerAtTime - System.currentTimeMillis() in 0L..60_200L)
        // Con «la letra del móvil», o sin permiso de alarmas exactas, no hace falta
        Minutero.ponLetraDelMovil(ctx, true)
        assertFalse(Minutero.programa(ctx)); assertNull(sombra.peekNextScheduledAlarm())
        Minutero.ponLetraDelMovil(ctx, false)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertTrue(Minutero.relojVivo(ctx)); assertFalse(Minutero.programa(ctx))
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertEquals(listOf(id), Minutero.relojes(ctx))
    }

    @Test fun loQueCuestaUnMinuto() {
        // Un reloj 4×4 a la densidad de un móvil (2,75): lo que se dibuja y lo que se manda al lanzador cada minuto
        kotlinx.coroutines.runBlocking { Letras.prepara(ctx, listOf(RelojDigital.nuevo())) }
        for (d in listOf(RelojDigital.nuevo().con("cabecera", "columna"), RelojAgujas.nuevo())) {
            val s = minOf(2.75f, kotlin.math.sqrt(1_800_000f / (276f * 454f)))  // (lo de Widgets.escala con la densidad de un móvil)
            repeat(3) { Pintor.pinta(ctx, d, 276f, 454f, false, s, DatosDeMentira(ctx), enWidget = true) }
            val t0 = System.nanoTime()
            val n = 10
            var bytes = 0
            repeat(n) { bytes = Pintor.pinta(ctx, d, 276f, 454f, false, s, DatosDeMentira(ctx), enWidget = true).bm.byteCount }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            println("MINUTERO ${d.tipo}: ${"%.1f".format(ms)} ms por dibujo (en el ordenador, con Robolectric), ${bytes / 1024} KB por envío")
            assertTrue("el dibujo de un minuto, rápido", ms < 400)
            assertTrue("y no pesa más de lo que deja Android", bytes <= 1_800_000 * 4)
        }
    }

    // ---------------------------------------------------------------- las vistas previas (solo con -Proborazzi.test.record=true)

    private val dir: String get() = System.getProperty("capturas")?.takeIf { it.isNotBlank() }?.let { "$it/widgets" } ?: "build/outputs/widgets"

    private fun guarda(b: Bitmap, nombre: String, d: String = dir) { File(d).mkdirs(); File("$d/$nombre.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }

    /** Un widget sobre un trozo de fondo de pantalla (para verlo como en el móvil). */
    private fun sobreFondo(b: Bitmap, s: Float): Bitmap {
        val m = (10 * s).toInt()
        val r = Bitmap.createBitmap(b.width + 2 * m, b.height + 2 * m, Bitmap.Config.ARGB_8888)
        val c = Canvas(r)
        c.drawRect(0f, 0f, r.width.toFloat(), r.height.toFloat(), android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, r.width.toFloat(), r.height.toFloat(), intArrayOf(0xFF4F6B68.toInt(), 0xFF1E2B33.toInt(), 0xFF3A2A33.toInt()), null, android.graphics.Shader.TileMode.CLAMP) })
        c.drawBitmap(b, m.toFloat(), m.toFloat(), null)
        return r
    }

    /** Los cuatro tamaños chicos del móvil de verdad (59×59, 59×120, 59×178 y 120×120 dp a 2,625): cada tipo y cada plantilla, en claro y en oscuro. */
    @Test fun dibujaLosChicos() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        kotlinx.coroutines.runBlocking { Letras.prepara(ctx, Plantillas.TODAS.map { it.first }) }
        val datos = DatosDeMentira(ctx)
        val s = 2.625f
        val ejemplos = Plantillas.TODAS.map { it.first.id.removePrefix("p") to it.first } + Tipos.TODOS.map { t ->
            "nuevo-" + t.id to (if (t.id == Accesos.id) t.nuevo().con("items", listOf("app:com.android.chrome", "app:com.whatsapp", "web:elpais.com|El País")) else t.nuevo()) }
        for ((n, d) in ejemplos) for ((w, h) in listOf(59f to 59f, 59f to 120f, 59f to 178f, 120f to 120f)) for (noche in listOf(false, true))
            guarda(sobreFondo(Pintor.pinta(ctx, d, w, h, noche, s, datos, enWidget = true).bm, s), "${n}_${w.toInt()}x${h.toInt()}${if (noche) "_oscuro" else ""}", "$dir/chicos")
    }

    @Test fun dibujaLasVistasPrevias() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        kotlinx.coroutines.runBlocking { Letras.prepara(ctx, Plantillas.TODAS.map { it.first }) }
        val datos = DatosDeMentira(ctx)
        val s = 2f
        fun pinta(d: Diseno, w: Float, h: Float, noche: Boolean) = Pintor.pinta(ctx, d, w, h, noche, s, datos).bm
        // La del selector de widgets: tres de las plantillas juntas (una esfera, la columna y el buscador), sobre transparente
        val p = Plantillas.TODAS.associate { it.first.id to it.first }
        val prev = Bitmap.createBitmap((276 * s).toInt(), (232 * s).toInt(), Bitmap.Config.ARGB_8888)
        Canvas(prev).apply {
            drawBitmap(pinta(p.getValue("pesfera"), 130f, 130f, false), 0f, 0f, null)
            drawBitmap(pinta(p.getValue("pcolumna"), 130f, 130f, false), 146f * s, 0f, null)
            drawBitmap(pinta(p.getValue("pbuscador").conEstilo(p.getValue("pbuscador").estilo.copy(tinta = Col.TINTA, secundario = "", piezas = emptyMap(), borde = true, fondo = TipoFondo.PAPEL)), 276f, 86f, false), 0f, 146f * s, null)
        }
        guarda(prev, "w_prev_ptah")
        // Cada plantilla a su tamaño, en claro y en oscuro; y en otros tamaños
        for ((d, c) in Plantillas.TODAS) {
            val (w, h) = Tamanos.dp(c)
            val n = d.id.removePrefix("p")
            guarda(sobreFondo(pinta(d, w, h, false), s), "${n}_${c.col}x${c.fil}")
            guarda(sobreFondo(pinta(d, w, h, true), s), "${n}_${c.col}x${c.fil}_oscuro")
            for (o in listOf(Tamanos.Celdas(1, 1), Tamanos.Celdas(2, 1), Tamanos.Celdas(4, 4))) if (o != c) {
                val (ow, oh) = Tamanos.dp(o)
                guarda(sobreFondo(pinta(d, ow, oh, false), s), "${n}_${o.col}x${o.fil}")
            }
        }
        // Cada tipo en 1×1 y en 1×2 de pie (las composiciones propias), en claro y en oscuro
        for (t in Tipos.TODOS) {
            val ejemplos = Plantillas.TODAS.filter { it.first.tipo == t.id }.map { it.first }.ifEmpty { listOf(t.nuevo()) }
            for ((k, d0) in ejemplos.withIndex()) {
                val d = if (t.id == Accesos.id) d0.con("items", listOf("app:com.android.chrome", "app:com.whatsapp", "web:elpais.com|El País")) else d0
                for ((nom, c) in listOf("1x1" to Tamanos.Celdas(1, 1), "1x2" to Tamanos.Celdas(1, 2))) for (noche in listOf(false, true)) {
                    val (w, h) = Tamanos.dp(c)
                    guarda(sobreFondo(pinta(d, w, h, noche), s), "${t.id}${if (k > 0) k + 1 else ""}_$nom${if (noche) "_oscuro" else ""}", "$dir/pequenos")
                }
            }
        }
        // Con la letra del móvil (TextClock): lo que de verdad pinta el lanzador, inflado y dibujado a su tamaño
        Minutero.ponLetraDelMovil(ctx, true)
        for ((nom, d) in listOf("digital" to RelojDigital.nuevo(), "depie" to Plantillas.TODAS.first { it.first.id == "pdepie" }.first, "agujas" to RelojAgujas.nuevo())) {
            for ((tn, c) in listOf("1x1" to Tamanos.Celdas(1, 1), "1x2" to Tamanos.Celdas(1, 2), "4x2" to Tamanos.Celdas(4, 2))) {
                val (w, h) = Tamanos.dp(c)
                val dens = ctx.resources.displayMetrics.density
                val v = Widgets.vistas(ctx, 5, d, w, h).apply(ctx, FrameLayout(ctx))
                val pw = (w * dens).toInt(); val ph = (h * dens).toInt()
                v.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY)); v.layout(0, 0, pw, ph)
                val b = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
                guarda(sobreFondo(b, dens), "textclock_${nom}_$tn", "$dir/textclock")
            }
        }
        Minutero.ponLetraDelMovil(ctx, false)
        // Lo de más: el digital en sus tres cabeceras en 4×2, y la foto con foto
        for (cab in listOf("portada", "columna", "titular")) guarda(sobreFondo(pinta(RelojDigital.nuevo().con("cabecera", cab), 276f, 220f, false), s), "digital_${cab}_4x2")
        guarda(sobreFondo(pinta(Foto.nuevo().con("pie", "Atardecer en Cádiz"), 130f, 220f, false), s), "foto_2x2_con_foto")
        guarda(sobreFondo(pinta(Foto.nuevo().con("pie", "Atardecer en Cádiz").con("sangre", true), 276f, 220f, false), s), "foto_4x2_a_sangre")
        guarda(sobreFondo(pinta(Accesos.nuevo().con("items", listOf("app:com.android.chrome", "app:com.whatsapp", "app:com.google.android.gm", "web:elpais.com|El País")).con("mostrar", "texto"), 203f, 220f, false), s), "accesos_indice_3x2")
    }
}
