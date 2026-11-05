package com.gonzalo.cobalto

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Widgets de la pantalla de inicio de Cobalto: los de Marfil y Carmesí (los de Atrio) con la misma estética de revista — papel y tinta,
 * titulares grandes, esquinas redondas y el reborde fino de tinta — pero con lo que escribes: las palabras de hoy con tu racha, la obra
 * en la que estás y una frase de tu manuscrito. Cada widget se dibuja entero y encima lleva zonas transparentes para tocar.
 * Los colores son los del tema que tengas puesto: los de Cobalto o los de Atrio (invertidos o no).
 */

/** Los widgets que hay. */
enum class WTipo { HOY, OBRA, CITA, MARCA, MARCA_PAPEL }

abstract class WBase(private val tipo: WTipo) : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.pintar(ctx, tipo, ids, goAsync())
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle) =
        Widgets.pintar(ctx, tipo, intArrayOf(id), goAsync())
    override fun onDeleted(ctx: Context, ids: IntArray) = Widgets.olvidar(ctx, ids)
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == Widgets.ACCION) {
            val id = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
            Widgets.accion(ctx, id, i.getStringExtra("op").orEmpty())
            Widgets.pintar(ctx, tipo, intArrayOf(id), goAsync())
        } else super.onReceive(ctx, i)
    }
}

class WHoy : WBase(WTipo.HOY)
class WObra : WBase(WTipo.OBRA)
class WCita : WBase(WTipo.CITA)
class WMarca : WBase(WTipo.MARCA)
class WMarcaPapel : WBase(WTipo.MARCA_PAPEL)

/** Una zona que se puede tocar (en dp, desde la esquina de arriba a la izquierda); con [celdas], una rejilla. */
class Zona(
    val x: Float, val y: Float, val w: Float, val h: Float, val toque: PendingIntent?, val desc: String = "",
    val celdas: List<List<Pair<PendingIntent?, String>>>? = null
) {
    fun vista(ctx: Context, ancho: Float, alto: Float, d: Float): RemoteViews {
        fun px(v: Float) = (max(0f, v) * d).roundToInt()
        val (l, t, r, b) = listOf(px(x), px(y), px(ancho - x - w), px(alto - y - h))
        val rejilla = celdas
        if (rejilla == null) {
            val v = RemoteViews(ctx.packageName, R.layout.w_zona)
            v.setViewPadding(R.id.w_hueco, l, t, r, b)
            toque?.let { v.setOnClickPendingIntent(R.id.w_toque, it) }
            if (desc.isNotEmpty()) v.setContentDescription(R.id.w_toque, desc)
            return v
        }
        val v = RemoteViews(ctx.packageName, R.layout.w_rejilla)
        v.setViewPadding(R.id.w_hueco, l, t, r, b)
        rejilla.forEach { fila ->
            val f = RemoteViews(ctx.packageName, R.layout.w_fila)
            fila.forEach { (pi, ds) ->
                val c = RemoteViews(ctx.packageName, R.layout.w_celda)
                pi?.let { c.setOnClickPendingIntent(R.id.w_celda, it) }
                if (ds.isNotEmpty()) c.setContentDescription(R.id.w_celda, ds)
                f.addView(R.id.w_fila, c)
            }
            v.addView(R.id.w_filas, f)
        }
        return v
    }
}

/** Un widget listo para dibujar: sus zonas y cómo se pinta (en claro y en oscuro). */
class Pagina(val zonas: List<Zona>, val dibujo: Hoja.() -> Unit) {
    /** Sin papel ni esquinas: solo lo dibujado, sobre el fondo de pantalla. */
    var sinFondo = false
    fun transparente() = apply { sinFondo = true }
    fun bitmap(ctx: Context, w: Float, h: Float, noche: Boolean, s: Float): Bitmap = Hoja(ctx, w, h, noche, s).apply { dibujo(); if (!sinFondo) terminar() }.bm
}

private val ES = Locale("es", "ES")

/** El lienzo de un widget: se dibuja en dp, con los colores del tema y las letras de la revista. */
class Hoja(val ctx: Context, val w: Float, val h: Float, val noche: Boolean, s: Float) {
    val bm: Bitmap = Bitmap.createBitmap(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()), Bitmap.Config.ARGB_8888)
    val c = Canvas(bm).apply { scale(bm.width / w, bm.height / h) }

    // Los colores del tema que tengas (los de Cobalto o los de Atrio, invertidos o no), en claro o en oscuro
    private val tonos = Skin.tonosPara(noche)
    val papel = tonos.cPaper.toArgb()
    val tinta = tonos.cInk.toArgb()
    val crudo = tonos.cCrudo.toArgb()
    val gris = tonos.cMuted.toArgb()
    val linea = tonos.cLine.toArgb()
    val arena = tonos.cSand.toArgb()
    val acento = tonos.cGeneral().toArgb()
    val radio = 22f
    /** El color del reborde (por defecto, el del texto). */
    var reborde: Int? = null

    private val letras = Widgets.letrasActuales(ctx)
    val fTitular: Typeface get() = letras.titular
    val fCursiva: Typeface get() = letras.cursiva
    val fNegrita: Typeface get() = letras.negrita
    val fTexto: Typeface get() = letras.texto

    /** Recorta las esquinas (suaves, con antialias) y traza el reborde: una línea fina del color del texto, metida hacia dentro. */
    fun terminar() {
        val m = 6f
        c.drawRoundRect(RectF(m, m, w - m, h - m), radio - m, radio - m, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.1f; color = reborde ?: tinta })
        val p = Path().apply { addRoundRect(RectF(0f, 0f, w, h), radio, radio, Path.Direction.CW); fillType = Path.FillType.INVERSE_WINDING }
        c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
    }

    fun fondo(color: Int = papel) = c.drawColor(color)

    fun lleno(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    fun tp(f: Typeface, size: Float, color: Int, track: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = f; textSize = size; this.color = color; letterSpacing = track; textLocale = ES
        }

    fun capa(t: CharSequence, p: TextPaint, ancho: Float, lineas: Int = Int.MAX_VALUE, inter: Float = 1f, al: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(t, 0, t.length, p, max(1, ancho.toInt())).setAlignment(al).setMaxLines(lineas)
            .setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, inter).setIncludePad(false).build()

    /** Pinta un texto ya compuesto; devuelve su alto. */
    fun pon(l: Layout, x: Float, y: Float): Float { c.save(); c.translate(x, y); l.draw(c); c.restore(); return l.height.toFloat() }

    /** Un titular tan grande como quepa (sin partir palabras) en [lineas] y [alto]. */
    fun titular(
        t: String, ancho: Float, alto: Float, maxS: Float, minS: Float, lineas: Int, color: Int = tinta,
        f: Typeface = fTitular, inter: Float = 0.95f, al: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout {
        var s = maxS
        while (true) {
            val p = tp(f, s, color, -0.01f)
            val palabra = t.split(' ').maxOfOrNull { p.measureText(it) } ?: 0f
            val l = capa(t, p, ancho, Int.MAX_VALUE, inter, al)
            if (l.lineCount <= lineas && l.height <= alto && palabra <= ancho) return l
            if (s <= minS) return capa(t, p, ancho, max(1, min(lineas, (alto / (s * 1.15f)).toInt())), inter, al)
            s = max(minS, s - max(0.5f, s * 0.06f))
        }
    }

    /** Antetítulo: mayúsculas pequeñas, negras y espaciadas. Devuelve el alto. */
    fun ante(t: String, x: Float, y: Float, ancho: Float, color: Int = tinta, size: Float = 9f, al: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): Float =
        pon(capa(t.uppercase(ES), tp(fNegrita, size, color, 0.16f), ancho, 1, 1f, al), x, y)

    fun anchoAnte(t: String, size: Float = 9f) = tp(fNegrita, size, tinta, 0.16f).measureText(t.uppercase(ES))

    /** Cabecera de sección: el cuadradito de acento y el antetítulo. */
    fun seccion(t: String, x: Float, y: Float, ancho: Float, color: Int = tinta, cuadro: Int = acento) {
        c.drawRect(x, y + 1.6f, x + 7.4f, y + 9f, lleno(cuadro))
        ante(t, x + 12f, y, ancho - 12f, color)
    }

    /** Degradado vertical. */
    fun velo(top: Float, bottom: Float, de: Int, a: Int, medio: Int? = null) {
        val cs = if (medio != null) intArrayOf(de, medio, a) else intArrayOf(de, a)
        val p = Paint().apply { shader = LinearGradient(0f, top, 0f, bottom, cs, null, Shader.TileMode.CLAMP) }
        c.drawRect(0f, top, w, bottom, p)
    }

    /** «2/5 ›»: hay más, y tocando aquí se pasa al siguiente. */
    fun paginas(i: Int, n: Int, xDer: Float, y: Float, color: Int = gris) {
        if (n < 2) return
        ante("${i + 1}/$n  ›", xDer - 80f, y, 80f, color, 9f, Layout.Alignment.ALIGN_OPPOSITE)
    }

    /** La franja de color de una obra (su lomo). */
    fun lomo(x: Float, top: Float, bottom: Float, color: Int, ancho: Float = 6f) = c.drawRect(x, top, x + ancho, bottom, lleno(color))
}

/** Lo que hay guardado en la app, como lo ven los widgets. */
class WDatos(val ctx: Context) {
    val e: Escritos = Escritos.get(ctx)
    val resumen: ResumenEscritura by lazy { e.resumen(14) }
    /** La obra en la que estás (o null si no hay ninguna en marcha). */
    val actual: Doc? by lazy { e.obraActual() }
    /** Las obras en marcha, la actual primero. */
    val enMarcha: List<Doc> by lazy { e.obrasDe(EstadoObra.ESCRIBIENDO) }
    /** Las frases de tu manuscrito que se leen bien sueltas. */
    val frases: List<Frase> by lazy { e.frases() }
    fun colorDe(o: Doc): Int = colorEscrito(o.s("color")).toArgb()
}

object Widgets {
    const val ACCION = "com.gonzalo.cobalto.WIDGET"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var pendiente: Job? = null
    private val fuentes = HashMap<Int, Typeface>()

    fun clase(t: WTipo): Class<out AppWidgetProvider> = when (t) {
        WTipo.HOY -> WHoy::class.java
        WTipo.OBRA -> WObra::class.java
        WTipo.CITA -> WCita::class.java
        WTipo.MARCA -> WMarca::class.java
        WTipo.MARCA_PAPEL -> WMarcaPapel::class.java
    }

    @Synchronized fun fuente(ctx: Context, id: Int): Typeface =
        fuentes.getOrPut(id) { runCatching { ctx.resources.getFont(id) }.getOrDefault(Typeface.SERIF) }

    // ------------------------------------------------------------------ letras

    /** Las cuatro letras de un widget: titulares, cursiva, negrita (antetítulos) y texto. */
    class Letras(val titular: Typeface, val cursiva: Typeface, val negrita: Typeface, val texto: Typeface)

    @Volatile private var letras: Letras? = null
    private val bajadas = HashMap<String, Typeface>()

    private fun porDefecto(ctx: Context) = Letras(fuente(ctx, R.font.display_semibold), fuente(ctx, R.font.display_italic), fuente(ctx, R.font.body_bold), fuente(ctx, R.font.body_medium))

    /** Las letras ya preparadas (las del tema) o, si aún no se han preparado, las que vienen con la app. */
    fun letrasActuales(ctx: Context): Letras = letras ?: porDefecto(ctx)

    /** Prepara las letras del tema que tengas (Skin.display para titulares, Skin.body para el resto). */
    suspend fun cargarLetras(ctx: Context) {
        val d = porDefecto(ctx)
        val dis = Skin.display; val cue = Skin.body
        val titular = tipografia(ctx, dis, 600, false, DISPLAY_FONTS)
        val cursiva = if (dis == "Atrio") null else tipografia(ctx, dis, 500, true, DISPLAY_FONTS) ?: tipografia(ctx, dis, 400, false, DISPLAY_FONTS)
        letras = Letras(
            titular = titular ?: d.titular,
            cursiva = cursiva ?: if (dis == "Atrio" || titular == null) d.cursiva else titular,
            negrita = d.negrita,
            texto = tipografia(ctx, cue, 500, false, BODY_FONTS) ?: d.texto
        )
    }

    private suspend fun tipografia(ctx: Context, nombre: String, peso: Int, cursiva: Boolean, lista: List<FontChoice>): Typeface? {
        if (nombre.isBlank() || nombre == "Atrio") return null
        if (nombre == "Vitrina") return vitrina(ctx, if (peso >= 600) 700 else 500)
        val pesos = lista.firstOrNull { it.name == nombre }?.weights?.takeIf { it.isNotEmpty() } ?: intArrayOf(400, 700)
        val w = pesos.minByOrNull { kotlin.math.abs(it - peso) } ?: 400
        val clave = "$nombre|$w|$cursiva"
        synchronized(bajadas) { bajadas[clave]?.let { return it } }
        val t = bajar(ctx, nombre, w, cursiva) ?: return null
        synchronized(bajadas) { bajadas[clave] = t }
        return t
    }

    private fun vitrina(ctx: Context, peso: Int): Typeface? = runCatching {
        val clave = "vitrina|$peso"
        synchronized(bajadas) { bajadas[clave]?.let { return it } }
        val f = File(ctx.cacheDir, "bricolage.ttf")
        if (!f.exists()) ctx.resources.openRawResource(R.font.bricolage).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        Typeface.Builder(f).setFontVariationSettings("'wght' $peso, 'wdth' ${if (peso >= 700) 92 else 100}").build()
            .also { synchronized(bajadas) { bajadas[clave] = it } }
    }.getOrNull()

    private suspend fun bajar(ctx: Context, nombre: String, peso: Int, cursiva: Boolean): Typeface? = withTimeoutOrNull(5_000) {
        suspendCancellableCoroutine { k ->
            val th = HandlerThread("letras-widget").also { it.start() }
            try {
                val pet = FontRequest("com.google.android.gms.fonts", "com.google.android.gms", "name=$nombre&weight=$peso" + if (cursiva) "&italic=1" else "", R.array.com_google_android_gms_fonts_certs)
                FontsContractCompat.requestFont(ctx, pet, object : FontsContractCompat.FontRequestCallback() {
                    override fun onTypefaceRetrieved(typeface: Typeface) { if (k.isActive) k.resume(typeface); th.quitSafely() }
                    override fun onTypefaceRequestFailed(reason: Int) { if (k.isActive) k.resume(null); th.quitSafely() }
                }, Handler(th.looper))
            } catch (e: Exception) { th.quitSafely(); if (k.isActive) k.resume(null) }
            k.invokeOnCancellation { th.quitSafely() }
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cobalto_widgets", Context.MODE_PRIVATE)
    private fun estado(ctx: Context, id: Int, k: String, def: Int) = prefs(ctx).getInt("${id}_$k", def)

    /** Lo que se toca dentro del widget: el siguiente, otra frase. */
    fun accion(ctx: Context, id: Int, op: String) {
        val p = prefs(ctx)
        if (op == "sig") p.edit().putInt("${id}_i", p.getInt("${id}_i", 0) + 1).apply()
    }

    fun olvidar(ctx: Context, ids: IntArray) {
        val ed = prefs(ctx).edit()
        ids.forEach { id -> ed.remove("${id}_i"); ed.remove("${id}_fijo") }
        ed.apply()
    }

    /** La obra que elegiste para ese widget al ponerlo (o null: la que toque). */
    fun fijoDe(ctx: Context, id: Int): Doc? = prefs(ctx).getString("${id}_fijo", null)?.let { Escritos.get(ctx).get(it) }
    fun ponFijo(ctx: Context, id: Int, obraId: String?) { prefs(ctx).edit().apply { if (obraId == null) remove("${id}_fijo") else putString("${id}_fijo", obraId) }.apply() }

    /** Lee lo guardado (el tema, la cuenta, lo escrito y los colores de Atrio): un widget puede pintarse sin que la app esté abierta. */
    fun preparar(ctx: Context) {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Escritos.get(ctx)
        if (TemaAtrio.tonos == null) TemaAtrio.cargarGuardado(ctx)
    }

    /** Repinta esos widgets (en segundo plano). */
    fun pintar(ctx: Context, tipo: WTipo, ids: IntArray, res: BroadcastReceiver.PendingResult?) {
        val app = ctx.applicationContext
        scope.launch {
            try {
                withTimeoutOrNull(9_000) {
                    preparar(app)
                    runCatching { TemaAtrio.refrescar(app) }
                    ids.forEach { id -> try { pintarUno(app, tipo, id) } catch (e: CancellationException) { throw e } catch (e: Exception) { } }
                }
            } finally { res?.finish() }
        }
    }

    /** Algo cambió en la app (lo escrito, el tema, los colores de Atrio): se repintan los widgets que haya, sin prisa. */
    fun pedir(ctx: Context) {
        val app = ctx.applicationContext
        val mgr = runCatching { AppWidgetManager.getInstance(app) }.getOrNull() ?: return
        val hay = WTipo.entries.mapNotNull { t ->
            runCatching { mgr.getAppWidgetIds(ComponentName(app, clase(t))) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { t to it }
        }
        if (hay.isEmpty()) return
        pendiente?.cancel()
        pendiente = scope.launch {
            delay(1_200)
            hay.forEach { (t, ids) -> ids.forEach { id -> try { pintarUno(app, t, id) } catch (e: CancellationException) { throw e } catch (e: Exception) { } } }
        }
    }

    suspend fun pintarUno(ctx: Context, tipo: WTipo, id: Int) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val (w, h) = tamano(ctx, mgr.getAppWidgetOptions(id), tipo)
        mgr.updateAppWidget(id, vistas(ctx, tipo, id, w, h))
    }

    /** Claro, oscuro, o null = como el sistema (se mandan los dos y el sistema elige). Con los colores de Atrio, que no dependen de eso, uno solo. */
    fun noche(): Boolean? = if (Skin.coloresFijos) false else when (Modo.eleccion) { 1 -> false; 2 -> true; else -> null }

    /** El tamaño del widget en dp (en vertical: el ancho mínimo y el alto máximo, como hace Android). */
    fun tamano(ctx: Context, o: Bundle?, tipo: WTipo): Pair<Float, Float> {
        val vertical = ctx.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val w = o?.getInt(if (vertical) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0
        val h = o?.getInt(if (vertical) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        val (dw, dh) = porDefecto(tipo)
        return (if (w > 40) w.toFloat() else dw) to (if (h > 40) h.toFloat() else dh)
    }

    fun porDefecto(t: WTipo): Pair<Float, Float> = when (t) {
        WTipo.MARCA -> 330f to 80f
        WTipo.CITA -> 330f to 200f
        else -> 330f to 165f
    }

    /** Píxeles por dp del dibujo: los de la pantalla, sin pasarse de memoria (Android limita lo que pesa un widget). */
    fun escala(ctx: Context, w: Float, h: Float, copias: Int): Float {
        val d = ctx.resources.displayMetrics.density
        val tope = if (copias == 2) 1_100_000f else 1_800_000f
        return min(d, sqrt(tope / (w * h)))
    }

    suspend fun vistas(ctx: Context, tipo: WTipo, id: Int, w: Float, h: Float): RemoteViews {
        cargarLetras(ctx)
        val pag = componer(ctx, tipo, id, w, h)
        val rv = RemoteViews(ctx.packageName, R.layout.w_marco)
        when (val n = noche()) {
            null -> {
                val s = escala(ctx, w, h, 2)
                rv.setImageViewBitmap(R.id.w_dia, pag.bitmap(ctx, w, h, false, s))
                rv.setImageViewBitmap(R.id.w_noche, pag.bitmap(ctx, w, h, true, s))
            }
            else -> rv.setImageViewBitmap(R.id.w_img, pag.bitmap(ctx, w, h, n, escala(ctx, w, h, 1)))
        }
        val d = ctx.resources.displayMetrics.density
        pag.zonas.forEach { rv.addView(R.id.w_zonas, it.vista(ctx, w, h, d)) }
        return rv
    }

    fun componer(ctx: Context, tipo: WTipo, id: Int, w: Float, h: Float): Pagina {
        val d = WDatos(ctx)
        return when (tipo) {
            WTipo.HOY -> hoy(d, w, h)
            WTipo.OBRA -> obra(d, id, w, h, fijo = fijoDe(ctx, id))
            WTipo.CITA -> cita(d, id, w, h)
            WTipo.MARCA -> marca(d, w, h)
            WTipo.MARCA_PAPEL -> marcaPapel(d, w, h)
        }
    }

    // ------------------------------------------------------------------ toques

    private const val PI = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun op(ctx: Context, tipo: WTipo, id: Int, op: String): PendingIntent {
        val i = Intent(ctx, clase(tipo)).setAction(ACCION).setData(Uri.parse("cobalto://w/$id/$op"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("op", op)
        return PendingIntent.getBroadcast(ctx, id, i, PI)
    }

    /** Abre Cobalto en su portada. */
    fun abrirApp(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 1, i, PI)
    }

    /** Abre una obra (y, si se dice, una escena dentro, o el sprint). */
    fun abrirObra(ctx: Context, obra: String, escena: String? = null, sprint: Boolean = false): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("cobalto://obra/$obra/${escena.orEmpty()}/$sprint"))
            .putExtra("obra", obra).putExtra("escena", escena).putExtra("sprint", sprint).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 2, i, PI)
    }

    /** Un widget sin nada que enseñar todavía: lo dice con la misma letra y lleva a donde empezar. */
    private fun vacio(ctx: Context, sec: String, titulo: String, texto: String, w: Float, h: Float): Pagina =
        Pagina(listOf(Zona(0f, 0f, w, h, abrirApp(ctx), titulo))) {
            fondo()
            val pad = 16f
            seccion(sec, pad, pad, w - 2 * pad)
            val l = titular(titulo, w - 2 * pad, h * 0.42f, 28f, 13f, 3)
            pon(l, pad, pad + 22f)
            val t = capa(texto, tp(fTexto, 11.5f, gris), w - 2 * pad, max(1, ((h - pad * 2 - 30f - l.height) / 15f).toInt()), 1.05f)
            pon(t, pad, pad + 28f + l.height)
        }

    private fun barra(h: Hoja, x: Float, y: Float, ancho: Float, f: Float, color: Int = h.acento) {
        h.c.drawRect(x, y, x + ancho, y + 2.2f, h.lleno(h.linea))
        h.c.drawRect(x, y, x + ancho * f.coerceIn(0f, 1f), y + 2.2f, h.lleno(color))
    }

    // ------------------------------------------------------------------ Palabras de hoy

    private fun hoy(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val r = d.resumen
        val sem = r.dias.takeLast(14)
        val hoyDia = LocalDate.now()
        return Pagina(listOf(Zona(0f, 0f, w, h, abrirApp(ctx), "Abrir Cobalto"))) {
            fondo()
            val pad = if (h < 90f) 12f else 16f
            seccion("Escritura", pad, pad, w - 2 * pad, tinta, acento)
            pon(capa("Cobalto", tp(fTitular, 13f, tinta), 80f, 1, 1f, Layout.Alignment.ALIGN_OPPOSITE), w - pad - 80f, pad - 3f)
            c.drawRect(pad, pad + 14.5f, w - pad, pad + 15.2f, lleno(linea))
            val top = pad + 26f
            val ancho = w - 2 * pad
            val izqW = if (w >= 230f) ancho * 0.4f else 0f
            if (izqW > 0f) {
                val big = titular(cifraE(r.hoy), izqW - 6f, 52f, 36f, 14f, 1, if (r.diaria > 0 && r.hoy >= r.diaria) acento else tinta)
                pon(big, pad, top)
                ante(if (r.diaria > 0) "hoy · de ${cifraE(r.diaria)}" else "palabras hoy", pad, top + big.height + 2f, izqW, gris, 8.5f)
                var y = top + big.height + 20f
                val extra = listOfNotNull(
                    if (r.diaria > 0 && r.hoy >= r.diaria) "Objetivo cumplido" else null,
                    if (r.racha >= 2) "${r.racha} días seguidos" else if (r.racha == 1) "1 día seguido" else null,
                    if (r.total > 0) "${cifraE(r.total)} en total" else null
                )
                for (t in extra) { if (y + 14f < h - pad) y += pon(capa(t, tp(fCursiva, 12f, gris), izqW, 1), pad, y) + 2f }
            }
            val x0 = pad + izqW + if (izqW > 0f) 14f else 0f
            val bw = w - pad - x0
            val alto = h - top - pad - 14f
            val tope = max(sem.maxOrNull() ?: 0, max(r.diaria, 1)).toFloat()
            val n = sem.size.coerceAtLeast(1)
            val slot = bw / n
            sem.forEachIndexed { k, m ->
                val bx = x0 + slot * k + slot * 0.2f
                val bwid = slot * 0.6f
                val hh = if (m > 0) max(3f, alto * m / tope) else 1f
                c.drawRect(bx, top + alto - hh, bx + bwid, top + alto, lleno(if (m > 0) (if (r.diaria > 0 && m >= r.diaria) acento else tinta) else linea))
                val dia = hoyDia.minusDays((n - 1 - k).toLong())
                if (n <= 7 || k % 2 == (n - 1) % 2) ante(dia.dayOfWeek.getDisplayName(TextStyle.NARROW, ES), bx - slot * 0.2f, top + alto + 3f, slot, if (k == n - 1) acento else gris, 8f, Layout.Alignment.ALIGN_CENTER)
            }
            if (r.diaria > 0) { val yy = top + alto - alto * r.diaria / tope; c.drawRect(x0, yy, w - pad, yy + 0.7f, lleno(acento and 0xFFFFFF or 0x99000000.toInt())) }
        }
    }

    // ------------------------------------------------------------------ Sigue escribiendo

    private fun obra(d: WDatos, id: Int, w: Float, h: Float, fijo: Doc? = null): Pagina {
        val ctx = d.ctx
        val lista = if (fijo != null) listOf(fijo) else listOfNotNull(d.actual) + d.enMarcha.filter { it.id != d.actual?.id }
        if (lista.isEmpty()) return vacio(ctx, "Sigue escribiendo", "Nada entre manos", "Crea una obra en Cobalto y la tendrás aquí, a un toque de seguir.", w, h)
        val i = Math.floorMod(estado(ctx, id, "i", 0), lista.size)
        val o = lista[i]
        val e = d.e
        val escena = e.ultimaEscenaDe(o.id)
        val palabras = e.palabrasObra(o.id); val meta = o.i("meta"); val hoyObra = e.escritas(Escritos.hoy(), o.id)
        val color = d.colorDe(o)
        val zonas = mutableListOf(
            Zona(0f, 0f, w, h, abrirObra(ctx, o.id), "Abrir ${o.s("titulo")}"),
            Zona(0f, h - 44f, w * 0.5f, 44f, abrirObra(ctx, o.id, escena?.id), "Seguir escribiendo ${o.s("titulo")}"),
            Zona(w * 0.5f, h - 44f, w * 0.5f, 44f, abrirObra(ctx, o.id, sprint = true), "Sprint en ${o.s("titulo")}")
        )
        if (lista.size > 1) zonas += Zona(w - 72f, 0f, 72f, 42f, op(ctx, WTipo.OBRA, id, "sig"), "Otra obra")
        return Pagina(zonas) {
            fondo()
            val pad = if (h < 90f) 12f else 14f
            lomo(pad, pad, h - pad, color, 6f)
            val x0 = pad + 6f + 14f
            val rw = w - x0 - pad
            seccion("Sigue escribiendo", x0, pad + 1f, rw - (if (lista.size > 1) 50f else 0f), tinta, acento)
            if (lista.size > 1) paginas(i, lista.size, w - pad, pad)
            val altoAnte = capa("L", tp(fNegrita, 9f, tinta, 0.16f), 40f, 1).height.toFloat()
            val bajo = h < 110f
            val pieY = if (bajo) h - pad - altoAnte else h - pad - 17f
            ante("Seguir  →", x0, pieY, rw * 0.5f, acento)
            ante("Sprint  ›", x0 + rw * 0.5f, pieY, rw * 0.5f, gris, 9f, Layout.Alignment.ALIGN_OPPOSITE)
            if (!bajo) c.drawRect(x0, h - pad - 25f, w - pad, h - pad - 24.4f, lleno(linea))
            val arriba = pad + 1f + altoAnte
            val aire = if (bajo) 7f else 10f
            val hueco = pieY - arriba - 2 * aire - if (bajo) 0f else 4f
            val conDatos = h >= 130f
            val datos = if (conDatos) capa(if (meta > 0) "${cifraE(palabras)} de ${cifraE(meta)} palabras · ${(palabras * 100L / meta).coerceAtMost(999)} %" else "${cifraE(palabras)} palabras" + (if (hoyObra > 0) " · ${cifraE(hoyObra)} hoy" else ""), tp(fNegrita, 8.5f, gris, 0.12f), rw, 1) else null
            val altoDatos = datos?.let { it.height + (if (meta > 0) 10f else 6f) } ?: 0f
            val lin = escena?.let { s -> s.s("titulo").ifBlank { null }?.let { capa("Por «$it»", tp(fCursiva, 12.5f, gris), rw, 1) } }
            val altoLin = lin?.let { it.height + 4f } ?: 0f
            val l = titular(o.s("titulo").ifBlank { "Sin título" }, rw, hueco - altoLin - altoDatos, (h * 0.19f).coerceIn(26f, 42f), if (bajo) 12f else 13f, if (bajo) 1 else 3)
            val y = arriba + (pieY - arriba - l.height - altoLin - altoDatos) / 2
            pon(l, x0, y)
            lin?.let { pon(it, x0, y + l.height + 4f) }
            datos?.let { dl ->
                val yy = y + l.height + altoLin + 6f
                pon(dl, x0, yy)
                if (meta > 0) barra(this, x0, yy + dl.height + 4f, rw, palabras.toFloat() / meta, color)
            }
        }
    }

    // ------------------------------------------------------------------ Una frase tuya

    private fun cita(d: WDatos, id: Int, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val lista = d.frases.sortedBy { (it.texto.hashCode().toLong() * 2654435761L) and 0xFFFFFF }
        if (lista.isEmpty()) return vacio(ctx, "Una frase tuya", "Aún no hay frases", "Los párrafos de tu manuscrito saldrán aquí, uno cada día.", w, h)
        val i = Math.floorMod(LocalDate.now().toEpochDay() + estado(ctx, id, "i", 0), lista.size.toLong()).toInt()
        val f = lista[i]
        val zonas = mutableListOf(Zona(0f, 0f, w, h, abrirObra(ctx, f.obra.id, f.escena.id), "Abrir «${f.escena.s("titulo")}»"))
        if (lista.size > 1) zonas += Zona(w - 80f, 0f, 80f, 44f, op(ctx, WTipo.CITA, id, "sig"), "Otra frase")
        return Pagina(zonas) {
            fondo()
            val pad = 16f
            seccion("Una frase tuya", pad, pad, w - 2 * pad - 60f, tinta, acento)
            if (lista.size > 1) paginas(i % lista.size, lista.size, w - pad, pad)
            val pie = capa(f.obra.s("titulo").ifBlank { "Sin título" }, tp(fTitular, 13f, tinta), w - 2 * pad, 1)
            val esc = f.escena.s("titulo").ifBlank { null }?.let { capa(it, tp(fCursiva, 12f, gris), w - 2 * pad, 1) }
            val altoPie = pie.height + (esc?.height ?: 0) + 10f
            val top = pad + 22f
            val q = titular("«${f.texto}»", w - 2 * pad, h - top - pad - altoPie, 24f, 10.5f, 12, tinta, f = fCursiva, inter = 1.04f)
            pon(q, pad, top)
            c.drawRect(pad, h - pad - altoPie - 3f, pad + 26f, h - pad - altoPie - 2.2f, lleno(d.colorDe(f.obra)))
            pon(pie, pad, h - pad - altoPie + 2f)
            esc?.let { pon(it, pad, h - pad - it.height) }
        }
    }

    // ------------------------------------------------------------------ La marca

    private fun marca(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val hoy = LocalDate.now()
        val fecha = "${hoy.dayOfWeek.getDisplayName(TextStyle.FULL, ES)} ${hoy.dayOfMonth} de ${hoy.month.getDisplayName(TextStyle.FULL, ES)}"
        return Pagina(listOf(Zona(0f, 0f, w, h, abrirApp(ctx), "Abrir Cobalto"))) {
            // Solo la palabra, sobre el fondo de pantalla (con una sombra suave para que se lea en cualquiera)
            val color = if (noche) 0xFFF6F1E7.toInt() else tinta
            val sombra = if (noche) 0x99000000.toInt() else 0x55FFFFFF
            val pie = if (h >= 84f) 16f else 0f
            val p = tp(fTitular, 10f, color, -0.03f)
            val ancho = p.measureText("COBALTO")
            val cuadro = 0.16f
            var s = min((w - 8f) / (ancho / 10f * (1f + cuadro)), (h - pie - 4f) / 0.95f)
            s = s.coerceAtLeast(10f)
            val tpa = tp(fTitular, s, color, -0.03f).apply { setShadowLayer(s * 0.06f, 0f, s * 0.02f, sombra) }
            val fm = tpa.fontMetrics
            val tw = tpa.measureText("COBALTO")
            val total = tw + s * cuadro
            val x = (w - total) / 2
            val capH = s * 0.72f
            val base = (h - pie) / 2 + capH / 2
            c.drawText("COBALTO", x, base, tpa)
            // El punto de la revista: un cuadradito de acento
            val q = s * 0.12f
            c.drawRect(x + tw + s * 0.03f, base - q, x + tw + s * 0.03f + q, base, lleno(acento).apply { setShadowLayer(s * 0.04f, 0f, s * 0.015f, sombra) })
            if (pie > 0f) {
                val l = capa(("Tus obras · $fecha").uppercase(ES), tp(fNegrita, 8.5f, color, 0.2f).apply { setShadowLayer(2f, 0f, 0.6f, sombra) }, w - 8f, 1, 1f, Layout.Alignment.ALIGN_CENTER)
                pon(l, 4f, min(h - l.height - 2f, base + fm.descent * 0.4f + 2f))
            }
        }.transparente()
    }

    /** COBALTO en papel: la cabecera de la portada (el nombre, el número del día y la fecha), con su papel de fondo. */
    private fun marcaPapel(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val hoy = LocalDate.now()
        val fecha = "${hoy.dayOfWeek.getDisplayName(TextStyle.FULL, ES)} ${hoy.dayOfMonth} de ${hoy.month.getDisplayName(TextStyle.FULL, ES)}"
        return Pagina(listOf(Zona(0f, 0f, w, h, abrirApp(ctx), "Abrir Cobalto"))) {
            fondo()
            val pad = if (h < 90f) 12f else 16f
            val conLinea = h >= 70f
            val conEntradilla = h >= 140f
            val bajo = (if (conLinea) 22f else 0f) + (if (conEntradilla) 24f else 0f)
            val alto = h - 2 * pad - bajo
            val p = tp(fTitular, 10f, tinta, -0.03f)
            val s = min((w - 2 * pad) / (p.measureText("COBALTO") / 10f), alto / 0.78f).coerceAtLeast(10f)
            val tpa = tp(fTitular, s, tinta, -0.03f)
            val capH = s * 0.72f
            val bloque = capH + s * 0.08f + 4f + bajo
            val base = max(pad, (h - bloque) / 2) + capH
            val tw = tpa.measureText("COBALTO")
            c.drawText("COBALTO", (w - tw) / 2, base, tpa)
            var y = base + s * 0.08f + 4f
            if (conLinea) {
                c.drawRect(pad, y, w - pad, y + 1.6f, lleno(tinta))
                y += 5f
                val iz = "Nº ${hoy.dayOfYear}"
                ante(iz, pad, y, w / 2, tinta, 8.5f)
                val sitio = w - 2 * pad - anchoAnte(iz, 8.5f) - 12f
                val corta = "${hoy.dayOfWeek.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')} ${hoy.dayOfMonth} ${hoy.month.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')}"
                ante(if (anchoAnte(fecha, 8.5f) > sitio) corta else fecha, pad + anchoAnte(iz, 8.5f) + 12f, y, w - 2 * pad - anchoAnte(iz, 8.5f) - 12f, tinta, 8.5f, Layout.Alignment.ALIGN_OPPOSITE)
                y += 14f
                c.drawRect(pad, y, w - pad, y + 0.6f, lleno(tinta))
                y += 6f
            }
            if (conEntradilla) pon(capa("Tus obras, al día", tp(fCursiva, 13f, gris), w - 2 * pad, 1), pad, y)
        }
    }
}
