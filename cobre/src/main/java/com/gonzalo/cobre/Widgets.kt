package com.gonzalo.cobre

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
 * Widgets de la pantalla de inicio de Nut, con la estética de revista de las hermanas (Bastet, Seshat, Hathor): papel y tinta,
 * titulares grandes en la letra del tema, esquinas redondas y el reborde fino de tinta. Cada widget se dibuja entero (así la letra
 * queda como en la app) y encima lleva zonas transparentes para tocar. Los colores son los del tema de Apariencia.
 *
 * La cabecera (NUT y NUT en papel) y «Próximo partido» (el de tus equipos, con los botones de sus canales). Para añadir otro: su tipo en [WTipo], su clase (`class WLoQueSea : WBase(...)`) y su
 * receiver en el AndroidManifest, su `res/xml/w_<tipo>.xml` con su vista previa (la dibuja WidgetsTest), su caso en [Widgets.clase]
 * y en [Widgets.componer], y lo que lea en [WDatos]. Lo que cambie en la app y se vea en un widget, que llame a [Widgets.pedir].
 * Nada de tus listas (que llevan usuario y contraseña) llega aquí.
 */

/** Los widgets que hay. */
enum class WTipo { MARCA, MARCA_PAPEL, PROXIMO, HOY }

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

class WMarca : WBase(WTipo.MARCA)
class WMarcaPapel : WBase(WTipo.MARCA_PAPEL)
class WProximo : WBase(WTipo.PROXIMO)
class WHoy : WBase(WTipo.HOY)

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
    /** Sin papel ni esquinas: solo lo dibujado, sobre el fondo de pantalla (la cabecera HATHOR). */
    var sinFondo = false
    fun transparente() = apply { sinFondo = true }
    fun bitmap(ctx: Context, w: Float, h: Float, noche: Boolean, s: Float): Bitmap = Hoja(ctx, w, h, noche, s).apply { dibujo(); if (!sinFondo) terminar() }.bm
}

private val ES = Locale("es", "ES")

/** El lienzo de un widget: se dibuja en dp, con los colores del tema y las letras de la revista. */
class Hoja(val ctx: Context, val w: Float, val h: Float, val noche: Boolean, s: Float) {
    val bm: Bitmap = Bitmap.createBitmap(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()), Bitmap.Config.ARGB_8888)
    val c = Canvas(bm).apply { scale(bm.width / w, bm.height / h) }

    // Los colores del tema que tengas (Apariencia: los de Nut o los de Jonsu, invertidos o no, con tu fondo y tus colores), en claro o en oscuro
    private val tonos = Skin.tonosPara(noche)
    val papel = tonos.cPaper.toArgb()
    val tinta = tonos.cInk.toArgb()
    val gris = tonos.cMuted.toArgb()
    val linea = tonos.cLine.toArgb()
    val arena = tonos.cSand.toArgb()
    /** El acento tal cual (el elegido). */
    val acentoBloque = tonos.cGeneral().toArgb()
    /** El acento legible sobre el papel del widget (como en la app: aclarado u oscurecido lo justo para el 4,5:1). */
    val acento = acentoLegible(androidx.compose.ui.graphics.Color(acentoBloque), androidx.compose.ui.graphics.Color(papel), androidx.compose.ui.graphics.Color(tinta)).toArgb()
    /** El acento sobre una foto oscura. */
    val acentoFoto = Skin.tonosPara(true).cGeneral().toArgb()
    val radio = 22f
    /** El color del reborde (por defecto, el del texto; sobre un póster a sangre, blanco). */
    var reborde: Int? = null

    // Las letras del tema (Ajustes → Estilo): las que vienen con la app, Vitrina o las de Google Fonts; ver Widgets.cargarLetras
    private val letras = Widgets.letrasActuales(ctx)
    val fTitular: Typeface get() = letras.titular
    val fCursiva: Typeface get() = letras.cursiva
    val fNegrita: Typeface get() = letras.negrita
    val fTexto: Typeface get() = letras.texto

    /** Recorta las esquinas (suaves, con antialias) y traza el reborde: una línea fina del color del texto, metida hacia dentro. */
    fun terminar() {
        // Dirección C (como Seshat, Bastet y Hathor): sin el reborde fino de la revista; solo una línea si se pide (sobre una foto)
        reborde?.let { col ->
            val m = 6f
            c.drawRoundRect(RectF(m, m, w - m, h - m), radio - m, radio - m, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.1f; color = col })
        }
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

    /** «2/5 ›»: hay más, y tocando aquí se pasa al siguiente. */
    fun paginas(i: Int, n: Int, xDer: Float, y: Float, color: Int = gris) {
        if (n < 2) return
        ante("${i + 1}/$n  ›", xDer - 80f, y, 80f, color, 9f, Layout.Alignment.ALIGN_OPPOSITE)
    }

    /** Cabecera de sección: el cuadradito de acento y el antetítulo. */
    fun seccion(t: String, x: Float, y: Float, ancho: Float, color: Int = tinta, cuadro: Int = acento) {
        // Dirección C: sin cuadradito; el antetítulo en el acento (o en el color que se pida, sobre una foto)
        ante(t, x, y, ancho, if (color == tinta) cuadro else color)
    }

    /** Un póster recortado (centrado) con esquinas redondas; sin imagen, un póster de letra. */
    fun poster(img: Bitmap?, r: RectF, nombre: String, rad: Float = 9f, filo: Boolean = true, soloInicial: Boolean = false) {
        if (img == null || img.width <= 0) { if (soloInicial) tapa(r, "", rad, nombre) else tapa(r, nombre, rad); return }
        val s = max(r.width() / img.width, r.height() / img.height)
        val m = Matrix().apply { setScale(s, s); postTranslate(r.left + (r.width() - img.width * s) / 2, r.top + (r.height() - img.height * s) / 2) }
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = BitmapShader(img, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) } }
        c.drawRoundRect(r, rad, rad, p)
        if (filo) c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = 0x26000000 })
    }

    /** Póster de letra (como en Hiedra, para lo que no tiene imagen): la inicial grande en acento y el nombre. Sin nombre (a sangre, con texto encima), solo la inicial, tenue. */
    fun tapa(r: RectF, nombre: String, rad: Float = 9f, inicial: String = nombre) {
        c.drawRoundRect(r, rad, rad, lleno(arena))
        val ini = inicial.trim().firstOrNull()?.uppercase() ?: "·"
        val tenue = nombre.isBlank()
        val big = capa(ini, tp(fTitular, r.height() * 0.42f, if (tenue) (acento and 0x00FFFFFF) or 0x55000000 else acento), r.width(), 1, 1f, Layout.Alignment.ALIGN_CENTER)
        val conNombre = !tenue && r.width() >= 56f && r.height() >= 70f
        pon(big, r.left, r.top + (r.height() - big.height) / 2 - if (conNombre) r.height() * 0.1f else 0f)
        if (conNombre) {
            val n = capa(nombre, tp(fTitular, min(11f, r.width() / 6.5f), tinta), r.width() - 12f, 2, 0.95f, Layout.Alignment.ALIGN_CENTER)
            pon(n, r.left + 6f, r.bottom - n.height - 8f)
        }
    }

    /** Degradado vertical (para leer encima de una foto). */
    fun velo(top: Float, bottom: Float, de: Int, a: Int, medio: Int? = null) {
        val cs = if (medio != null) intArrayOf(de, medio, a) else intArrayOf(de, a)
        val p = Paint().apply { shader = LinearGradient(0f, top, 0f, bottom, cs, null, Shader.TileMode.CLAMP) }
        c.drawRect(0f, top, w, bottom, p)
    }

    /** La barra de lo que llevas (como la de Marfil): la línea fina y, encima, lo visto en acento. */
    fun barra(x: Float, y: Float, ancho: Float, f: Float, fondo: Int = linea, color: Int = acento) {
        c.drawRect(x, y, x + ancho, y + 2.2f, lleno(fondo))
        c.drawRect(x, y, x + ancho * f.coerceIn(0f, 1f), y + 2.2f, lleno(color))
    }
}

/** Lo que hay guardado en la app, como lo ven los widgets: de momento, el próximo partido de tus equipos (de lo ya bajado: sin red). */
class WDatos(val ctx: Context) {
    /** El partido que toca (el que se juega o el siguiente de tus equipos) y los grupos de canales de su competición. */
    fun proximo(): Pair<com.gonzalo.iptv.futbol.Partido, List<com.gonzalo.iptv.futbol.GrupoCanales>>? {
        Futbol.cargar(ctx)
        val eqs = AjustesFutbol.equipos(ctx).map { it.first.id }
        if (eqs.isEmpty()) return null
        val p = com.gonzalo.iptv.futbol.Orden.tusPartidos(runCatching { Futbol.partidos(ctx, fresco = false) }.getOrDefault(emptyList()), eqs, System.currentTimeMillis()).firstOrNull() ?: return null
        return p to AjustesFutbol.gruposDe(ctx, p.liga)
    }
    val hayEquipos: Boolean get() = AjustesFutbol.equipos(ctx).isNotEmpty()

    /** Los partidos de hoy (de lo guardado), por secciones: tus equipos y tus competiciones, en tu orden. */
    fun hoy(): List<com.gonzalo.iptv.futbol.Seccion> {
        Futbol.cargar(ctx)
        val hoy = com.gonzalo.iptv.futbol.Hora.diaDe(System.currentTimeMillis())
        return com.gonzalo.iptv.futbol.Dia.agrupa(Futbol.diaGuardado(ctx, hoy), null, AjustesFutbol.equipos(ctx).map { it.first.id }, AjustesFutbol.competiciones(ctx), AjustesFutbol.ocultas(ctx))
    }
}

object Widgets {
    const val ACCION = "com.gonzalo.cobre.WIDGET"
    /** Abrir Nut poniendo un canal de un grupo del fútbol (extras `grupo` y `n`). */
    const val ACCION_CANAL = "com.gonzalo.cobre.FUTBOL_CANAL"
    /** Abrir Nut en una página (el nombre de un [Destino]). */
    const val EXTRA_DESTINO = "destino"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var pendiente: Job? = null
    private val fuentes = HashMap<Int, Typeface>()

    fun clase(t: WTipo): Class<out AppWidgetProvider> = when (t) {
        WTipo.MARCA -> WMarca::class.java
        WTipo.MARCA_PAPEL -> WMarcaPapel::class.java
        WTipo.PROXIMO -> WProximo::class.java
        WTipo.HOY -> WHoy::class.java
    }

    @Synchronized fun fuente(ctx: Context, id: Int): Typeface =
        fuentes.getOrPut(id) { runCatching { ctx.resources.getFont(id) }.getOrDefault(Typeface.SERIF) }

    // ------------------------------------------------------------------ letras

    /** Las cuatro letras de un widget: titulares, cursiva, negrita (antetítulos) y texto. */
    class Letras(val titular: Typeface, val cursiva: Typeface, val negrita: Typeface, val texto: Typeface)

    @Volatile private var letras: Letras? = null
    private val bajadas = HashMap<String, Typeface>()

    // Dirección C: los titulares de los widgets, en grotesca (Bricolage, muy negra) como la cabecera de la app
    private fun porDefecto(ctx: Context) = Letras(vitrina(ctx, 800) ?: fuente(ctx, R.font.display_semibold), fuente(ctx, R.font.display_italic), fuente(ctx, R.font.body_bold), fuente(ctx, R.font.body_medium))

    /** Las letras ya preparadas (las del tema) o, si aún no se han preparado, las que vienen con la app. */
    fun letrasActuales(ctx: Context): Letras = letras ?: porDefecto(ctx)

    /**
     * Prepara las letras del tema que tengas (Skin.display para titulares, Skin.body para el resto). Las de Google Fonts se piden
     * a los servicios de Google (si ya las ha bajado la app, salen al momento); si no llegan, se usa la letra de la app.
     */
    suspend fun cargarLetras(ctx: Context) {
        val d = porDefecto(ctx)
        val dis = Skin.display; val cue = Skin.body
        val titular = tipografia(ctx, dis, 600, false, DISPLAY_FONTS)
        val cursiva = if (dis == "Atrio") null else tipografia(ctx, dis, 500, true, DISPLAY_FONTS) ?: tipografia(ctx, dis, 400, false, DISPLAY_FONTS)
        letras = Letras(
            titular = titular ?: d.titular,
            cursiva = cursiva ?: if (dis == "Atrio" || titular == null) d.cursiva else titular,
            // Las etiquetas (SEGUIR →, los antetítulos) llevan siempre la negrita de la app, como en las hermanas: la del tema, bajada de Google Fonts, salía más fina
            negrita = d.negrita,
            texto = tipografia(ctx, cue, 500, false, BODY_FONTS) ?: d.texto
        )
    }

    /** Una letra del tema con el grueso más cercano al pedido; null = la de la app (o no se ha podido conseguir). */
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

    /** Bricolage Grotesque (la letra de Vitrina) es una letra variable: se pide su grueso y su anchura. */
    private fun vitrina(ctx: Context, peso: Int): Typeface? = runCatching {
        val clave = "vitrina|$peso"
        synchronized(bajadas) { bajadas[clave]?.let { return it } }
        val f = File(ctx.cacheDir, "bricolage.ttf")
        if (!f.exists()) ctx.resources.openRawResource(R.font.bricolage).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        Typeface.Builder(f).setFontVariationSettings("'wght' $peso, 'wdth' ${if (peso >= 700) 92 else 100}").build()
            .also { synchronized(bajadas) { bajadas[clave] = it } }
    }.getOrNull()

    /** Pide una letra de Google Fonts a los servicios de Google (como hace la app para sus pantallas). */
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

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cobre_widgets", Context.MODE_PRIVATE)
    @Suppress("unused") private fun estado(ctx: Context, id: Int, k: String, def: Int) = prefs(ctx).getInt("${id}_$k", def)

    /** Lo que se toca dentro del widget («sig»: el siguiente, para los que pasan de uno a otro). */
    fun accion(ctx: Context, id: Int, op: String) {
        val p = prefs(ctx)
        if (op == "sig") p.edit().putInt("${id}_i", p.getInt("${id}_i", 0) + 1).apply()
    }

    fun olvidar(ctx: Context, ids: IntArray) {
        val ed = prefs(ctx).edit()
        ids.forEach { id -> ed.remove("${id}_i") }
        ed.apply()
    }

    /** Lee lo guardado (el tema y los colores de Jonsu): un widget puede pintarse sin que la app esté abierta. */
    fun preparar(ctx: Context) {
        Skin.cargar(ctx)
        if (TemaAtrio.tonos == null) TemaAtrio.cargarGuardado(ctx)
    }

    /** Repinta esos widgets (en segundo plano: los pósteres pueden venir de internet). */
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
            // «Próximo partido»: después de pintar lo guardado, se pide lo que haya caducado (y si cambia algo, se vuelve a pintar)
            if (tipo == WTipo.PROXIMO) runCatching { if (Futbol.refrescaSiToca(app)) ids.forEach { id -> runCatching { pintarUno(app, tipo, id) } } }
            // «Hoy»: lo mismo con los partidos de hoy
            if (tipo == WTipo.HOY) runCatching {
                val hoy = com.gonzalo.iptv.futbol.Hora.diaDe(System.currentTimeMillis())
                if (Futbol.fijo == null && Futbol.dia(app, hoy, fresco = true) != Futbol.diaGuardado(app, hoy)) ids.forEach { id -> runCatching { pintarUno(app, tipo, id) } }
            }
        }
    }

    /** Algo cambió (el tema, los colores de Jonsu; lo que lean los widgets que vengan): se repintan los widgets que haya, sin prisa. */
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

    /** Claro, oscuro, o null = como el sistema (se mandan los dos y el sistema elige). Con colores que no dependen de eso (los de Jonsu, un fondo), uno solo. */
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
        WTipo.MARCA_PAPEL -> 330f to 165f
        WTipo.PROXIMO -> 330f to 170f
        WTipo.HOY -> 330f to 250f
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
    suspend fun componer(ctx: Context, tipo: WTipo, id: Int, w: Float, h: Float): Pagina {
        val d = WDatos(ctx)
        return when (tipo) {
            WTipo.MARCA -> marca(d, w, h)
            WTipo.MARCA_PAPEL -> marcaPapel(d, w, h)
            WTipo.PROXIMO -> proximo(d, id, w, h)
            WTipo.HOY -> hoy(d, w, h)
        }
    }

    // ------------------------------------------------------------------ toques

    private const val PI = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    /** Un toque que se queda en el widget (lo recibe [WBase] y lo hace [accion]), para los que pasan de uno a otro. */
    @Suppress("unused")
    private fun op(ctx: Context, tipo: WTipo, id: Int, op: String): PendingIntent {
        val i = Intent(ctx, clase(tipo)).setAction(ACCION).setData(Uri.parse("cobre://w/$id/$op"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("op", op)
        return PendingIntent.getBroadcast(ctx, id, i, PI)
    }

    /** Abre Nut en su portada. */
    fun abrirApp(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 1, i, PI)
    }

    /** Abre Nut en una página ([Destino]). */
    fun abrirEn(ctx: Context, d: Destino, codigo: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("cobre://abrir/${d.name}"))
            .putExtra(EXTRA_DESTINO, d.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, codigo, i, PI)
    }

    /** Pone el canal [n] del grupo [grupo] (abre Nut, que lo pone en el reproductor). */
    fun ponCanal(ctx: Context, id: Int, grupo: String, n: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).setAction(ACCION_CANAL).setData(Uri.parse("cobre://futbol/$id/$grupo/$n"))
            .putExtra("grupo", grupo).putExtra("n", n).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 1000 + id * 16 + (grupo.hashCode() and 0xF), i, PI)
    }

    // ------------------------------------------------------------------ escudos

    /** Donde se guarda el escudo de [url] (los escudos son imágenes públicas de ESPN; se bajan una vez). */
    fun archivoEscudo(ctx: Context, url: String): File {
        val h = java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
        return File(File(ctx.cacheDir, "escudos"), "$h.png")
    }

    /** El escudo de [url] (del disco o, la primera vez, de internet; null si no hay). */
    fun escudo(ctx: Context, url: String): Bitmap? {
        if (url.isBlank()) return null
        val f = archivoEscudo(ctx, url)
        if (!f.isFile && Futbol.fijo == null) runCatching {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 4_000; c.readTimeout = 4_000
            try {
                if (c.responseCode == 200) {
                    val bm = android.graphics.BitmapFactory.decodeStream(c.inputStream) ?: return@runCatching
                    val chico = Bitmap.createScaledBitmap(bm, 128, (128f * bm.height / bm.width.coerceAtLeast(1)).roundToInt().coerceAtLeast(1), true)
                    f.parentFile?.mkdirs(); f.outputStream().use { chico.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            } finally { c.disconnect() }
        }
        return if (f.isFile) runCatching { android.graphics.BitmapFactory.decodeFile(f.path) }.getOrNull() else null
    }

    // ------------------------------------------------------------------ Próximo partido

    /**
     * «Próximo partido»: el de tus equipos que se juega ahora (con el marcador) o el siguiente: los escudos y los nombres, la competición,
     * el día y la hora (en la de España), y un botón por cada grupo de canales de su competición, que lo pone directamente. Un toque en lo
     * demás abre «Fútbol».
     */
    private fun proximo(d: WDatos, id: Int, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val x = d.proximo()
        if (x == null) {
            val v = vacio(ctx, "Próximo partido", if (d.hayEquipos) "Sin partidos a la vista" else "Elige tus equipos",
                if (d.hayEquipos) "En cuanto haya uno, saldrá aquí con sus canales." else "En Nut, Ajustes → Fútbol. Un toque y vas.", w, h)
            return Pagina(listOf(Zona(0f, 0f, w, h, abrirEn(ctx, if (d.hayEquipos) Destino.Futbol else Destino.AjustesFutbol, 2), "Abrir el fútbol de Nut")), v.dibujo)
        }
        val (p, grupos) = x
        val ahora = System.currentTimeMillis()
        val escL = escudo(ctx, p.local.escudo); val escV = escudo(ctx, p.visitante.escudo)
        val pad = if (h < 110f) 10f else 14f
        val conBotones = grupos.isNotEmpty() && h >= 96f
        val altoBoton = if (h >= 150f) 30f else 26f
        val bajoBotones = if (conBotones) altoBoton + 8f else 0f
        // Los botones: cada uno a lo ancho que le toque (como mucho, tres)
        val gs = grupos.take(3)
        val anchoB = if (gs.isEmpty()) 0f else (w - 2 * pad - 8f * (gs.size - 1)) / gs.size
        val zonas = mutableListOf(Zona(0f, 0f, w, h, abrirEn(ctx, Destino.Futbol, 3), "${p.local.nombre} contra ${p.visitante.nombre}"))
        if (conBotones) gs.forEachIndexed { k, g ->
            val bx = pad + k * (anchoB + 8f); val by = h - pad - altoBoton
            zonas += Zona(bx, by, anchoB, altoBoton, if (g.canales.isEmpty()) abrirEn(ctx, Destino.AjustesFutbol, 4) else ponCanal(ctx, id, g.id, 0), if (g.canales.isEmpty()) "Elegir los canales de ${g.nombre}" else "Ver en ${g.nombre}")
        }
        return Pagina(zonas) {
            fondo()
            val vivo = p.estado.enDirecto
            // Arriba: «PRÓXIMO PARTIDO» (o «EN DIRECTO») y la competición
            // (en uno bajo, cuándo va arriba, junto a la competición)
            val conLinea = h >= 130f
            val cuando = if (vivo) p.cuando(ahora) else p.cuando(ahora)
            val comp = if (conLinea) p.competicion.corto else "${p.competicion.corto} · $cuando"
            seccion(if (vivo) "En directo" else "Próximo partido", pad, pad, w - 2 * pad - anchoAnte(comp) - 8f, tinta, acento)
            ante(comp, w - pad - anchoAnte(comp) - 2f, pad, anchoAnte(comp) + 2f, if (vivo && !conLinea) acento else gris, al = Layout.Alignment.ALIGN_OPPOSITE)
            val top = pad + 16f
            val bottom = h - pad - bajoBotones - if (conLinea) 16f else 2f
            val filaH = ((bottom - top) / 2f).coerceIn(14f, 34f)
            val esc = (filaH - 4f).coerceIn(12f, 28f)
            val goles = p.golesLocal != null && p.golesVisitante != null
            fun fila(e: com.gonzalo.iptv.futbol.Equipo, bm: Bitmap?, g: Int?, y: Float) {
                val r = RectF(pad, y + (filaH - esc) / 2, pad + esc, y + (filaH - esc) / 2 + esc)
                if (bm != null) poster(bm, r, e.nombre, rad = 0f, filo = false) else {
                    c.drawOval(r, lleno(arena))
                    val t = capa(e.siglas.ifBlank { e.nombre.take(3) }.uppercase(ES), tp(fNegrita, esc * 0.32f, tinta), esc, 1, 1f, Layout.Alignment.ALIGN_CENTER)
                    pon(t, r.left, r.centerY() - t.height / 2f)
                }
                val anchoG = if (goles) 26f else 0f
                val n = titular(e.nombre, w - 2 * pad - esc - 8f - anchoG, filaH, min(20f, filaH * 0.7f), 10f, 1)
                pon(n, pad + esc + 8f, y + (filaH - n.height) / 2)
                if (g != null) {
                    val gl = capa("$g", tp(fTitular, min(22f, filaH * 0.8f), if (vivo) acento else tinta), anchoG, 1, 1f, Layout.Alignment.ALIGN_OPPOSITE)
                    pon(gl, w - pad - anchoG, y + (filaH - gl.height) / 2)
                }
            }
            fila(p.local, escL, p.golesLocal, top)
            fila(p.visitante, escV, p.golesVisitante, top + filaH)
            // Cuándo (en la hora de España) o el minuto
            if (conLinea) ante(cuando, pad, top + 2 * filaH + 3f, w - 2 * pad, if (vivo) acento else tinta, 9.5f)
            if (conBotones) gs.forEachIndexed { k, g ->
                val bx = pad + k * (anchoB + 8f); val by = h - pad - altoBoton
                val r = RectF(bx, by, bx + anchoB, by + altoBoton)
                val lleno0 = k == 0 && g.canales.isNotEmpty()
                if (lleno0) c.drawRoundRect(r, altoBoton / 2, altoBoton / 2, lleno(acento))
                else c.drawRoundRect(RectF(r.left + 0.7f, r.top + 0.7f, r.right - 0.7f, r.bottom - 0.7f), altoBoton / 2, altoBoton / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.3f; color = tinta })
                val txt = if (g.canales.isEmpty()) "${g.boton}: elegir" else "▶  ${g.boton}"
                val t = capa(txt.uppercase(ES), tp(fNegrita, 9.5f, if (lleno0) papel else tinta, 0.1f), anchoB - 10f, 1, 1f, Layout.Alignment.ALIGN_CENTER)
                pon(t, bx + 5f, by + (altoBoton - t.height) / 2)
            }
        }
    }

    // ------------------------------------------------------------------ Hoy

    /**
     * «Hoy»: los partidos de hoy de tus equipos y de tus competiciones (en tu orden), en líneas: la hora (o el minuto, en acento), los
     * equipos y el marcador; el nombre de cada competición encima de los suyos. Los que no caben, «y n más». Un toque abre la portada.
     */
    private fun hoy(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val secs = d.hoy()
        val zona = listOf(Zona(0f, 0f, w, h, abrirEn(ctx, Destino.Hoy, 5), "Abrir los partidos de hoy"))
        if (secs.isEmpty()) return Pagina(zona, vacio(ctx, "Hoy", "Sin partidos hoy", "En tus competiciones no se juega nada hoy (o aún no se ha mirado: abre Nut).", w, h).dibujo)
        val ahora = System.currentTimeMillis()
        val mios = AjustesFutbol.equipos(ctx).map { it.first.id }.toSet()
        return Pagina(zona) {
            fondo()
            val pad = 14f
            val dia = com.gonzalo.iptv.futbol.Hora.diaRelativo(ahora, ahora) + " · " + com.gonzalo.iptv.futbol.Hora.dia(ahora)
            seccion(dia, pad, pad, w - 2 * pad)
            var y = pad + 20f
            val fila = 17f
            val total = secs.sumOf { it.partidos.size }
            var puestos = 0
            loop@ for (s in secs) {
                if (y + 12f + fila > h - pad - 12f) break
                y += 4f
                ante(s.titulo, pad, y, w - 2 * pad, if (s.competicion == null) acento else gris, 8f)
                y += 12f
                for (p in s.partidos) {
                    if (y + fila > h - pad - (if (total - puestos > 1) 12f else 0f)) break@loop
                    val vivo = p.estado.enDirecto
                    val t = capa(com.gonzalo.iptv.futbol.Dia.cuando(p), tp(fNegrita, 9.5f, if (vivo) acento else if (p.estado.terminado) gris else tinta), 40f, 1)
                    pon(t, pad, y + 3f)
                    val marcador = if (p.golesLocal != null && p.golesVisitante != null) "${p.golesLocal}-${p.golesVisitante}" else ""
                    val anchoM = if (marcador.isBlank()) 0f else 30f
                    val mio = mios.any { p.juega(it) }
                    val n = capa("${p.local.corto} – ${p.visitante.corto}", tp(if (mio) fNegrita else fTexto, 11.5f, tinta), w - 2 * pad - 44f - anchoM, 1)
                    pon(n, pad + 44f, y + 1.5f)
                    if (marcador.isNotBlank()) pon(capa(marcador, tp(fTitular, 13f, if (vivo) acento else tinta), anchoM, 1, 1f, Layout.Alignment.ALIGN_OPPOSITE), w - pad - anchoM, y)
                    y += fila; puestos++
                }
            }
            if (total > puestos) ante("y ${total - puestos} más", pad, h - pad - 10f, w - 2 * pad, gris, 8f, Layout.Alignment.ALIGN_OPPOSITE)
        }
    }

    /** Un widget sin nada que enseñar todavía: lo dice con la misma letra y lleva a la app. */
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

    // ------------------------------------------------------------------ La marca

    private fun hoyLargo(): String {
        val hoy = LocalDate.now()
        return "${hoy.dayOfWeek.getDisplayName(TextStyle.FULL, ES)} ${hoy.dayOfMonth} de ${hoy.month.getDisplayName(TextStyle.FULL, ES)}"
    }

    /** NUT: solo la palabra (y el punto de acento), sobre el fondo de pantalla. Un toque y estás en la portada. */
    private fun marca(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val fecha = hoyLargo()
        return Pagina(listOf(Zona(0f, 0f, w, h, abrirApp(ctx), "Abrir Nut"))) {
            // Solo la palabra, sobre el fondo de pantalla (con una sombra suave para que se lea en cualquiera)
            val color = if (noche) 0xFFF6F1E7.toInt() else tinta
            val sombra = if (noche) 0x99000000.toInt() else 0x55FFFFFF
            val pie = if (h >= 84f) 16f else 0f
            val p = tp(fTitular, 10f, color, -0.03f)
            val ancho = p.measureText("NUT")
            val cuadro = 0.16f
            var s = min((w - 8f) / (ancho / 10f * (1f + cuadro)), (h - pie - 4f) / 0.95f)
            s = s.coerceAtLeast(10f)
            val tpa = tp(fTitular, s, color, -0.03f).apply { setShadowLayer(s * 0.06f, 0f, s * 0.02f, sombra) }
            val fm = tpa.fontMetrics
            val tw = tpa.measureText("NUT")
            val total = tw + s * cuadro
            val x = (w - total) / 2
            val capH = s * 0.72f
            val base = (h - pie) / 2 + capH / 2
            c.drawText("NUT", x, base, tpa)
            // El punto de la revista: un cuadradito de acento
            val q = s * 0.12f
            c.drawRect(x + tw + s * 0.03f, base - q, x + tw + s * 0.03f + q, base, lleno(acento).apply { setShadowLayer(s * 0.04f, 0f, s * 0.015f, sombra) })
            if (pie > 0f) {
                val pp = tp(fNegrita, 8.5f, color, 0.2f).apply { setShadowLayer(2f, 0f, 0.6f, sombra) }
                val largo = "Tu tele · $fecha".uppercase(ES)
                val txt = if (pp.measureText(largo) <= w - 8f) largo else fecha.uppercase(ES)
                val l = capa(txt, pp, w - 8f, 1, 1f, Layout.Alignment.ALIGN_CENTER)
                pon(l, 4f, min(h - l.height - 2f, base + fm.descent * 0.4f + 2f))
            }
        }.transparente()
    }

    /** NUT en papel: la cabecera de la portada (el nombre, el número del día y la fecha), con su papel de fondo. */
    private fun marcaPapel(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val hoy = LocalDate.now()
        val fecha = hoyLargo()
        return Pagina(listOf(Zona(0f, 0f, w, h, abrirApp(ctx), "Abrir Nut"))) {
            fondo()
            val pad = if (h < 90f) 12f else 16f
            // Lo de debajo del nombre, según el sitio: la línea del número y la fecha; y la entradilla si cabe
            val conLinea = h >= 70f
            val conEntradilla = h >= 140f
            val bajo = (if (conLinea) 22f else 0f) + (if (conEntradilla) 24f else 0f)
            val alto = h - 2 * pad - bajo
            val p = tp(fTitular, 10f, tinta, -0.03f)
            val s = min((w - 2 * pad) / (p.measureText("NUT") / 10f), alto / 0.78f).coerceAtLeast(10f)
            val tpa = tp(fTitular, s, tinta, -0.03f)
            val capH = s * 0.72f
            // Todo el bloque centrado en el widget (el nombre se mide por sus mayúsculas, no por la caja de la letra)
            val bloque = capH + s * 0.08f + 4f + bajo
            val base = max(pad, (h - bloque) / 2) + capH
            val tw = tpa.measureText("NUT")
            c.drawText("NUT", (w - tw) / 2, base, tpa)
            var y = base + s * 0.08f + 4f
            if (conLinea) {
                c.drawRect(pad, y, w - pad, y + 1.6f, lleno(tinta))
                y += 5f
                val iz = "Nº ${hoy.dayOfYear}"
                ante(iz, pad, y, w / 2, tinta, 8.5f)
                val sitio = w - 2 * pad - anchoAnte(iz, 8.5f) - 12f
                val corta = "${hoy.dayOfWeek.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')} ${hoy.dayOfMonth} ${hoy.month.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')}"
                ante(if (anchoAnte(fecha, 8.5f) > sitio) corta else fecha, pad + anchoAnte(iz, 8.5f) + 12f, y, sitio, tinta, 8.5f, Layout.Alignment.ALIGN_OPPOSITE)
                y += 14f
                c.drawRect(pad, y, w - pad, y + 0.6f, lleno(tinta))
                y += 6f
            }
            if (conEntradilla) pon(capa("Tu tele, al día", tp(fCursiva, 13f, gris), w - 2 * pad, 1), pad, y)
        }
    }
}
