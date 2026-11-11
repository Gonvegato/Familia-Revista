package com.gonzalo.mostaza

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import com.gonzalo.familia.EstiloWidget
import com.gonzalo.familia.EstilosWidget
import com.gonzalo.familia.PreviasWidget
import com.gonzalo.familia.TonosWidget
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
 * Widgets de la pantalla de inicio de Mostaza: los de Hiedra y Marfil (los de Atrio) con la misma estética de revista — papel y tinta,
 * titulares grandes en Fraunces, pósteres recortados, esquinas redondas y el reborde fino de tinta — pero con tus películas y series.
 * Cada widget se dibuja entero (así la letra y los pósteres quedan como en la app) y encima lleva zonas transparentes para tocar. Los
 * colores son los del tema que tengas puesto en Apariencia: los de Mostaza o los de Azabache (invertidos o no), tu fondo y tus colores.
 * Nada de lo que hay en `/claves` (tus addons, con la clave de tu debrid) llega aquí: solo la biblioteca y lo visto (y «Próximos
 * capítulos», que lee lo que ya guardó [Proximos]: la clave de TMDB no pasa por los widgets).
 */

/** Los widgets que hay. */
enum class WTipo { SEGUIR, PENDIENTES, PORTADA, PROXIMOS, SEMANA, MARCA, MARCA_PAPEL }

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

class WSeguir : WBase(WTipo.SEGUIR)
class WPendientes : WBase(WTipo.PENDIENTES)
class WPortada : WBase(WTipo.PORTADA)
class WProximos : WBase(WTipo.PROXIMOS)
class WSemana : WBase(WTipo.SEMANA)
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
    /** Sin papel ni esquinas: solo lo dibujado, sobre el fondo de pantalla (la cabecera HATHOR). */
    var sinFondo = false
    fun transparente() = apply { sinFondo = true }
    fun bitmap(ctx: Context, w: Float, h: Float, noche: Boolean, s: Float, estilo: EstiloWidget = EstiloWidget.COMO_AHORA): Bitmap {
        // Sin papel (la cabecera sobre el fondo de pantalla) el estilo solo cambia el color del nombre
        val e = if (sinFondo) EstiloWidget.COMO_AHORA else estilo
        // Cabecera de color: primero se mide dónde caen las etiquetas de arriba (sin pintar de verdad) y luego se pinta la franja debajo
        val banda = if (e == EstiloWidget.CABECERA) Hoja(ctx, w, h, noche, 0.05f, e, midiendo = true).apply { dibujo() }.bandaMedida() else null
        return Hoja(ctx, w, h, noche, s, e, banda = banda, nombreEnColor = sinFondo && estilo == EstiloWidget.ACENTO).apply { dibujo(); if (!sinFondo) terminar() }.bm
    }
}

private val ES = Locale("es", "ES")

/** El lienzo de un widget: se dibuja en dp, con los colores del tema y las letras de la revista. */
class Hoja(val ctx: Context, val w: Float, val h: Float, val noche: Boolean, s: Float, val estilo: EstiloWidget = EstiloWidget.COMO_AHORA, val midiendo: Boolean = false, val banda: RectF? = null, val nombreEnColor: Boolean = false) {
    val bm: Bitmap = Bitmap.createBitmap(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()), Bitmap.Config.ARGB_8888)
    val c = Canvas(bm).apply { scale(bm.width / w, bm.height / h) }

    // Los colores del tema que tengas (Apariencia: los de Mostaza o los de Azabache, invertidos o no, con tu fondo y tus colores), en claro o en oscuro
    private val tonos = Skin.tonosPara(noche)
    private val papel0 = tonos.cPaper.toArgb()
    private val tinta0 = tonos.cInk.toArgb()
    private val gris0 = tonos.cMuted.toArgb()
    private val linea0 = tonos.cLine.toArgb()
    private val arena0 = tonos.cSand.toArgb()
    /** El acento tal cual (el elegido). */
    private val acentoBloque0 = tonos.cGeneral().toArgb()
    // El estilo elegido al poner el widget (familia/EstiloWidget.kt): «papel teñido» y «todo acento» cambian los colores; la cabecera y el marco se pintan aparte
    val est: EstiloWidget = estilo
    private val tonosEstilo = EstilosWidget.aplicar(est, noche, TonosWidget(papel0, tinta0, gris0, linea0, arena0, arena0, acentoBloque0))
    val papel = tonosEstilo.papel
    val tinta = tonosEstilo.tinta
    val gris = tonosEstilo.gris
    val linea = tonosEstilo.linea
    val arena = tonosEstilo.arena
    val acentoBloque = tonosEstilo.bloque
    /** El acento legible sobre el papel del widget (como en la app: aclarado u oscurecido lo justo para el 4,5:1). */
    val acento = acentoLegible(androidx.compose.ui.graphics.Color(acentoBloque), androidx.compose.ui.graphics.Color(papel), androidx.compose.ui.graphics.Color(tinta)).toArgb()
    /** El acento sobre un bloque de tinta (la cabecera). */
    val acentoSobreTinta = acentoLegible(androidx.compose.ui.graphics.Color(acentoBloque), androidx.compose.ui.graphics.Color(tinta), androidx.compose.ui.graphics.Color(papel)).toArgb()
    /** El acento sobre lo oscuro (la marca de noche, sobre el fondo de pantalla). */
    val acentoClaro = acentoLegible(androidx.compose.ui.graphics.Color(acentoBloque), androidx.compose.ui.graphics.Color(0xFF050404), androidx.compose.ui.graphics.Color.White).toArgb()
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
        // Dirección C: sin el reborde fino de la revista (los bloques ya separan); solo una línea si se pide (sobre una foto)
        reborde?.let { col ->
            val m = 6f
            c.drawRoundRect(RectF(m, m, w - m, h - m), radio - m, radio - m, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.1f; color = col })
        }
        pintaMarco()
        val p = Path().apply { addRoundRect(RectF(0f, 0f, w, h), radio, radio, Path.Direction.CW); fillType = Path.FillType.INVERSE_WINDING }
        c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
    }

    fun fondo(color: Int = papel) { c.drawColor(color); pintaBanda() }

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
    fun ante(t: String, x: Float, y: Float, ancho: Float, color: Int = tinta, size: Float = 9f, al: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): Float {
        val l = capa(t.uppercase(ES), tp(fNegrita, size, enBanda(x, y, color), 0.16f), ancho, 1, 1f, al)
        if (midiendo) apunta(l, x, y)
        return pon(l, x, y)
    }

    // ------------------------------------------------------------------ cabecera de color y marco (estilos del widget)

    private val etiquetas = ArrayList<RectF>()
    /** El color de la franja y del marco: el acento, visible sobre el papel (el granate de Nut, aclarado de noche). */
    val colorBanda: Int by lazy { EstilosWidget.colorBorde(TonosWidget(papel, tinta, gris, linea, arena, arena, acentoBloque)) }
    /** El texto sobre la franja: papel o negro, el que mejor se lea. */
    val textoBanda: Int by lazy { EstilosWidget.sobre(colorBanda, papel, tinta) }
    /** El nombre de la app en el color del acento (la cabecera sin papel, con «nombre en color»): legible sobre un fondo de pantalla claro u oscuro. */
    val acentoNombre: Int by lazy { EstilosWidget.bloqueVisible(acentoBloque, if (noche) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(), if (noche) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), 2.4) }
    private var bandaPuesta = false

    /** Lo que se escribe sobre la franja va en su color de texto. */
    // (solo los colores de sobre el papel: lo que va sobre una foto, en blanco, se queda como está)
    fun enBanda(@Suppress("UNUSED_PARAMETER") x: Float, y: Float, color: Int): Int =
        banda?.let { if (y < it.bottom && color in setOf(tinta, gris, acento, acentoBloque, linea)) textoBanda else color } ?: color

    /** Al medir: dónde cae cada etiqueta de arriba del todo. */
    private fun apunta(l: Layout, x: Float, y: Float) {
        if (y > 30f || l.lineCount == 0) return
        etiquetas += RectF(x + l.getLineLeft(0), y, x + l.getLineRight(0), y + l.height)
    }

    /** La franja: de lado a lado, hasta debajo de las etiquetas de arriba (si no hay, una tira fina); una portada a la izquierda queda encima. */
    fun bandaMedida(): RectF {
        if (etiquetas.isEmpty()) return RectF(0f, 0f, w, 7f)
        return RectF(0f, 0f, w, etiquetas.maxOf { it.bottom } + 7f)
    }

    /** El bloque de tinta de la cabecera con papel (fondo, letra): teñido de acento, o el acento con la letra que mejor se lea. */
    fun bloqueMarca(): Pair<Int, Int> = when (est) {
        EstiloWidget.ACENTO -> papel to tinta
        EstiloWidget.TENIDO -> EstilosWidget.mezcla(tinta, acentoBloque, if (noche) 0.30f else 0.35f) to papel
        else -> tinta to papel
    }

    /** La etiqueta de acento sobre una foto (con letra blanca): con «todo acento», el acento de la app (el claro de ese estilo no se leería). */
    val chapa: Int get() = if (est == EstiloWidget.ACENTO) papel else acento

    private fun pintaBanda() {
        val b = banda ?: return
        if (bandaPuesta) return
        bandaPuesta = true
        c.drawRect(b, lleno(colorBanda))
    }

    /** El marco de color (encima de todo, antes de recortar las esquinas). */
    private fun pintaMarco() {
        if (est != EstiloWidget.MARCO) return
        val m = 3f
        c.drawRoundRect(RectF(m, m, w - m, h - m), radio - m, radio - m, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = colorBanda })
    }

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
    fun poster(img: Bitmap?, r: RectF, nombre: String, rad: Float = 0f, filo: Boolean = true, soloInicial: Boolean = false) {
        if (img == null || img.width <= 0) { if (soloInicial) tapa(r, "", rad, nombre) else tapa(r, nombre, rad); return }
        val s = max(r.width() / img.width, r.height() / img.height)
        val m = Matrix().apply { setScale(s, s); postTranslate(r.left + (r.width() - img.width * s) / 2, r.top + (r.height() - img.height * s) / 2) }
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = BitmapShader(img, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) } }
        c.drawRoundRect(r, rad, rad, p)
        if (filo) c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = 0x26000000 })
    }

    /** Póster de letra (como en Hiedra, para lo que no tiene imagen): la inicial grande en acento y el nombre. Sin nombre (a sangre, con texto encima), solo la inicial, tenue. */
    fun tapa(r: RectF, nombre: String, rad: Float = 0f, inicial: String = nombre) {
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

/** Lo que hay en tu biblioteca, como lo ven los widgets (lo mismo que la portada de la app: [Biblioteca.seguirViendo] y [Biblioteca.pendientes]). */
class WDatos(val ctx: Context) {
    private val todos: List<Titulo> = Biblioteca.titulos.toList()
    /** Lo que estás viendo, lo último que tocaste primero. */
    val viendo: List<Titulo> = Biblioteca.seguirViendo(todos)
    /** Lo pendiente, lo último que añadiste primero. */
    val pendientes: List<Titulo> = Biblioteca.pendientes(todos)

    /** «T2 · E5 · quedan 20 min» (como en «Seguir viendo» de la portada). */
    fun seguir(t: Titulo): String = seguirTexto(t)
    /** Lo que llevas del último vídeo que tocaste (null si no hay o ya está visto: entonces no hay barra). */
    fun fraccion(t: Titulo): Float? = Biblioteca.ultimo(t.ref)?.takeIf { !it.visto }?.fraccion
    /** «Serie · 2011–2019», «Película · 2024». */
    fun origen(t: Titulo): String = tipoNombre(t.tipo) + if (t.ano.isNotBlank()) " · ${t.ano}" else ""
    /** Por dónde vas, para «En portada» (en una película, también el %): «T2 · E5 · quedan 20 min», «40 % · quedan 1 h 48 min». */
    fun porDonde(t: Titulo): String = porDonde(Biblioteca.ultimo(t.ref), t.esSerie, porcentaje = true)
    /** Los próximos episodios de tus series (lo guardado por [Proximos], sin red). */
    val proximos: List<Proximo> by lazy { Proximos.lista(ctx) }
    /** Lo visto estos siete días (de `tiempo`, todos tus dispositivos). */
    val semana: Semana by lazy { Semana.de(Biblioteca.ratos(), LocalDate.now()) { r -> todos.firstOrNull { it.ref == r }?.nombre } }
    fun titulo(ref: String): Titulo? = todos.firstOrNull { it.ref == ref }
}

object Widgets {
    const val ACCION = "com.gonzalo.mostaza.WIDGET"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var pendiente: Job? = null
    private val fuentes = HashMap<Int, Typeface>()

    fun clase(t: WTipo): Class<out AppWidgetProvider> = when (t) {
        WTipo.SEGUIR -> WSeguir::class.java
        WTipo.PENDIENTES -> WPendientes::class.java
        WTipo.PORTADA -> WPortada::class.java
        WTipo.PROXIMOS -> WProximos::class.java
        WTipo.SEMANA -> WSemana::class.java
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

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("mostaza_widgets", Context.MODE_PRIVATE)
    private fun estado(ctx: Context, id: Int, k: String, def: Int) = prefs(ctx).getInt("${id}_$k", def)

    /** Lo que se toca dentro del widget: el siguiente de lo que estás viendo. */
    fun accion(ctx: Context, id: Int, op: String) {
        val p = prefs(ctx)
        if (op == "sig") p.edit().putInt("${id}_i", p.getInt("${id}_i", 0) + 1).apply()
    }

    fun olvidar(ctx: Context, ids: IntArray) {
        EstilosWidget.olvidar(ctx, ids)
        val ed = prefs(ctx).edit()
        ids.forEach { id -> ed.remove("${id}_i") }
        ed.apply()
    }

    /** Lee lo guardado (el tema, la biblioteca y los colores de Azabache): un widget puede pintarse sin que la app esté abierta. */
    fun preparar(ctx: Context) {
        Skin.cargar(ctx); Biblioteca.cargar(ctx)
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
            // «Próximos capítulos»: si toca, se pregunta a TMDB después (sin hacer esperar al widget); al llegar, se repintan solos
            if (tipo == WTipo.PROXIMOS) runCatching { if (Proximos.toca(app)) Proximos.refresca(app) }
        }
    }

    /**
     * Con el reproductor abierto los widgets no se repintan: lo visto se apunta cada 15 segundos y cada repintado decodifica pósteres (y
     * quitaría fluidez al vídeo). Se hace al cerrarlo, como Marfil con su lector.
     */
    @Volatile var reproductorAbierto = false
    @Volatile private var pedidoViendo = false
    fun reproductorCerrado(ctx: Context) { reproductorAbierto = false; if (pedidoViendo) { pedidoViendo = false; pedir(ctx) } }

    /** Algo cambió (tu biblioteca o lo visto, aquí o desde otro dispositivo por la cuenta; el tema; los colores de Azabache): se repintan los widgets que haya, sin prisa. */
    fun pedir(ctx: Context) {
        if (reproductorAbierto) { pedidoViendo = true; return }
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

    // ------------------------------------------------------------------ estilo (se elige al poner el widget: ConfigWidgetActivity)

    /** Qué widget es ese id (por su receiver). */
    fun tipoDe(ctx: Context, id: Int): WTipo? {
        val n = runCatching { AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id)?.provider?.className }.getOrNull() ?: return null
        return WTipo.entries.firstOrNull { clase(it).name == n }
    }

    /** Para la pantalla de elegir el estilo: ese widget, dibujado con cada uno. */
    suspend fun previas(ctx: Context, id: Int): PreviasWidget? {
        val app = ctx.applicationContext
        val t = tipoDe(app, id) ?: return null
        preparar(app)
        cargarLetras(app)
        val (w, h) = tamano(app, AppWidgetManager.getInstance(app).getAppWidgetOptions(id), t)
        val pag = componer(app, t, id, w, h)
        val s = escala(app, w, h, 1)
        return PreviasWidget(w, h, pag.sinFondo) { e, n -> pag.bitmap(app, w, h, n, s, estilo = e) }
    }

    /** Repinta ese widget (al elegir su estilo). */
    fun repintar(ctx: Context, id: Int) { val t = tipoDe(ctx, id) ?: return; pintar(ctx, t, intArrayOf(id), null) }

    suspend fun pintarUno(ctx: Context, tipo: WTipo, id: Int) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val (w, h) = tamano(ctx, mgr.getAppWidgetOptions(id), tipo)
        mgr.updateAppWidget(id, vistas(ctx, tipo, id, w, h))
    }

    /** Claro, oscuro, o null = como el sistema (se mandan los dos y el sistema elige). Con colores que no dependen de eso (los de Azabache, un fondo), uno solo. */
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
        WTipo.PORTADA -> 330f to 250f
        WTipo.MARCA -> 330f to 80f
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
        val e = EstilosWidget.de(ctx, id)
        when (val n = noche()) {
            null -> {
                val s = escala(ctx, w, h, 2)
                rv.setImageViewBitmap(R.id.w_dia, pag.bitmap(ctx, w, h, false, s, estilo = e))
                rv.setImageViewBitmap(R.id.w_noche, pag.bitmap(ctx, w, h, true, s, estilo = e))
            }
            else -> rv.setImageViewBitmap(R.id.w_img, pag.bitmap(ctx, w, h, n, escala(ctx, w, h, 1), estilo = e))
        }
        val d = ctx.resources.displayMetrics.density
        pag.zonas.forEach { rv.addView(R.id.w_zonas, it.vista(ctx, w, h, d)) }
        return rv
    }

    suspend fun componer(ctx: Context, tipo: WTipo, id: Int, w: Float, h: Float): Pagina {
        val d = WDatos(ctx)
        return when (tipo) {
            WTipo.SEGUIR -> seguir(d, id, w, h)
            WTipo.PENDIENTES -> pendientes(d, w, h)
            WTipo.PORTADA -> enPortada(d, id, w, h)
            WTipo.PROXIMOS -> proximos(d, w, h)
            WTipo.SEMANA -> semana(d, w, h)
            WTipo.MARCA -> marca(d, w, h)
            WTipo.MARCA_PAPEL -> marcaPapel(d, w, h)
        }
    }

    // ------------------------------------------------------------------ toques

    private const val PI = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun op(ctx: Context, tipo: WTipo, id: Int, op: String): PendingIntent {
        val i = Intent(ctx, clase(tipo)).setAction(ACCION).setData(Uri.parse("mostaza://w/$id/$op"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("op", op)
        return PendingIntent.getBroadcast(ctx, id, i, PI)
    }

    /** Abre Hathor en su portada. */
    fun abrirApp(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 1, i, PI)
    }

    /** Abre la ficha de un título («ficha» = su ref, lo mismo que manda Azabache con «Ver en Mostaza»; la dirección distinta hace un toque distinto por título). */
    fun abrirFicha(ctx: Context, t: Titulo): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("mostaza://ficha/${Uri.encode(t.ref)}"))
            .putExtra("ficha", t.ref).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 2, i, PI)
    }

    /** Abre una sección de tu biblioteca («seccion» = el nombre del [Destino]). */
    fun abrirSeccion(ctx: Context, d: Destino): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("mostaza://seccion/${d.name}"))
            .putExtra("seccion", d.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 3, i, PI)
    }

    // ------------------------------------------------------------------ imágenes

    private fun px(ctx: Context, dp: Float) = (dp * ctx.resources.displayMetrics.density).roundToInt().coerceIn(32, 1400)

    private fun decodificar(path: String, lado: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (min(o.outWidth, o.outHeight) / (s * 2) >= lado) s *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    /** El póster de un título a unos [lado] píxeles: el de la cuenta si ya está en el móvil o, si no, bajado de su dirección; si no llega, null (póster de letra). */
    suspend fun imagen(ctx: Context, t: Titulo, lado: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (t.portada.isNotBlank()) decodificar(t.portada, if (t.encPortada.neutro) lado else lado * 2)?.let { return@withContext if (t.encPortada.neutro) it else recortarEncuadre(it, t.encPortada) }
            val u = t.poster
            when {
                u.startsWith("http") -> withTimeoutOrNull(4_500) {
                    val r = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(u).allowHardware(false).size(lado).build())
                    ((r as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                }
                u.startsWith("/") -> decodificar(u, lado)
                else -> null
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
    }

    /** La imagen de fondo de un título (la elegida o la de TMDB, apaisada) o, si no hay (o la quitaste), su póster. */
    suspend fun imagenFondo(ctx: Context, t: Titulo, lado: Int): Bitmap? = withContext(Dispatchers.IO) {
        val u = t.fondoVer
        val f = try {
            when {
                u.startsWith("http") -> withTimeoutOrNull(4_500) {
                    val r = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(u).allowHardware(false).size(lado).build())
                    ((r as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                }
                u.startsWith("/") -> decodificar(u, lado)
                else -> null
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        f ?: imagen(ctx, t, lado)
    }

    // ------------------------------------------------------------------ Seguir viendo

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

    private suspend fun seguir(d: WDatos, id: Int, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val lista = d.viendo
        if (lista.isEmpty()) return vacio(ctx, "Seguir viendo", "Nada a medias", "Empieza una película o una serie en Hathor y la tendrás aquí.", w, h)
        val i = Math.floorMod(estado(ctx, id, "i", 0), lista.size)
        val e = lista[i]
        val compacto = w < 200f && h < 200f
        val img = imagen(ctx, e, px(ctx, if (compacto) max(w, h) else h * 0.9f))
        val accion = "Seguir  →"
        val sigue = d.seguir(e)
        val frac = d.fraccion(e)
        // Todo el widget abre su ficha: allí está «Reproducir» (que elige la fuente) y el episodio que toca
        val zonas = mutableListOf(Zona(0f, 0f, w, h, abrirFicha(ctx, e), "Seguir viendo ${e.nombre}"))
        if (lista.size > 1) zonas += Zona(w - 72f, 0f, 72f, 42f, op(ctx, WTipo.SEGUIR, id, "sig"), "Otro título")
        return Pagina(zonas) {
            val pad = if (h < 90f) 12f else 14f
            when {
                compacto -> {
                    // El póster a sangre y el título encima (sin imagen: solo la inicial, tenue, para que no se pise con el texto)
                    poster(img, RectF(0f, 0f, w, h), e.nombre, 0f, false, soloInicial = true); reborde = 0xCCFFFFFF.toInt()
                    velo(h * 0.2f, h, 0x00000000, 0xF0000000.toInt(), 0x88000000.toInt())
                    velo(0f, 52f, 0xB0000000.toInt(), 0x00000000)
                    seccion(if (w < 150f) "Viendo" else "Seguir viendo", pad, pad, w - 2 * pad, 0xFFFFFFFF.toInt(), acentoFoto)
                    val bottom = h - pad - 4f
                    ante(accion, pad, bottom - 8f, w - 2 * pad, acentoFoto, 8.5f)
                    var y = bottom - 14f
                    if (frac != null) { barra(pad, y, w - 2 * pad, frac, 0x55FFFFFF, acentoFoto); y -= 6f }
                    if (sigue.isNotBlank() && h >= 120f) { y -= 11f; ante(sigue, pad, y, w - 2 * pad, 0xCCFFFFFF.toInt(), 8f); y -= 3f }
                    val l = titular(e.nombre, w - 2 * pad, y - pad - 22f, 24f, 12f, 3, 0xFFFFFFFF.toInt())
                    pon(l, pad, y - l.height)
                }
                h > w * 1.15f -> {
                    // Alto: el póster arriba y el título debajo
                    fondo()
                    seccion(if (w < 200f) "Viendo" else "Seguir viendo", pad, pad, w - 2 * pad - (if (lista.size > 1) 50f else 0f), tinta, acento)   // (en el estrecho no cabe «Seguir viendo»)
                    val top = pad + 20f
                    val ch = h * 0.5f - top
                    val cw = min(ch * 0.68f, w - 2 * pad)
                    poster(img, RectF(pad, top, pad + cw, top + ch), e.nombre)
                    var y = top + ch + 12f
                    val l = titular(e.nombre, w - 2 * pad, h - y - pad - 26f - 32f, 30f, 13f, 4)
                    y += pon(l, pad, y) + 3f
                    y += pon(capa(d.origen(e), tp(fCursiva, 12f, gris), w - 2 * pad, 1), pad, y)
                    if (sigue.isNotBlank()) ante(sigue, pad, h - pad - 42f, w - 2 * pad, gris, 8.5f)
                    barra(pad, h - pad - 28f, w - 2 * pad, frac ?: 0f)
                    ante(accion, pad, h - pad - 11f, w - 2 * pad, acento)
                }
                else -> {
                    // Ancho: el póster a la izquierda; a la derecha, la sección arriba, el pie abajo y el título entre los dos
                    fondo()
                    val ch = h - 2 * pad
                    val cw = min(ch * 0.68f, w * 0.36f)
                    poster(img, RectF(pad, pad, pad + cw, pad + ch), e.nombre)
                    val x0 = pad + cw + 14f
                    val rw = w - x0 - pad
                    seccion("Seguir viendo", x0, pad + 1f, rw, tinta, acento)
                    val altoAnte = capa("L", tp(fNegrita, 9f, tinta, 0.16f), 40f, 1).height.toFloat()
                    val bajo = h < 110f
                    val pieY = if (bajo) h - pad - altoAnte else h - pad - 17f
                    ante(accion, x0, pieY, rw, acento)
                    if (!bajo) c.drawRect(x0, h - pad - 25f, w - pad, h - pad - 24.4f, lleno(linea))
                    val arriba = pad + 1f + altoAnte
                    val aire = if (bajo) 7f else 10f
                    val hueco = pieY - arriba - 2 * aire - if (bajo) 0f else 4f
                    // Debajo del título: de dónde es (si cabe) y lo que llevas, con su barra
                    val origen = if (h >= 130f) capa(d.origen(e), tp(fCursiva, 12.5f, gris), rw, 1) else null
                    val altoOrigen = origen?.let { it.height + 4f } ?: 0f
                    val sig = if (sigue.isNotBlank() && !bajo) capa(sigue.uppercase(ES), tp(fNegrita, 8.5f, acento, 0.16f), rw, 1) else null
                    val altoSig = sig?.let { it.height + 6f + (if (frac != null) 6f else 0f) } ?: if (frac != null && !bajo) 8f else 0f
                    val l = titular(e.nombre, rw, hueco - altoOrigen - altoSig, (h * 0.19f).coerceIn(28f, 44f), if (bajo) 12f else 13f, if (bajo) 1 else 3)
                    var y = arriba + (pieY - arriba - l.height - altoOrigen - altoSig) / 2
                    y += pon(l, x0, y)
                    origen?.let { y += 4f; y += pon(it, x0, y) }
                    if (!bajo) {
                        y += 6f
                        sig?.let { y += pon(it, x0, y) + 4f }
                        frac?.let { barra(x0, y, rw, it) }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Pendientes (en fila, como la estantería de las hermanas)

    private suspend fun pendientes(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val pad = 14f
        val todas = d.pendientes
        if (todas.isEmpty()) return vacio(ctx, "Pendientes", "Nada pendiente", "Lo que añadas a tu lista en Hathor aparecerá aquí en fila, lo último primero.", w, h)
        val bajo = h < 150f
        val n = ((w - 2 * pad) / (if (bajo) 140f else 78f)).toInt().coerceIn(1, 6).coerceAtMost(todas.size)
        val items = todas.take(n)
        val slot = (w - 2 * pad) / n
        val top = pad + 22f
        val textos = if (bajo) 0f else 44f
        val ch = h - top - pad - textos
        val imgs = items.map { imagen(ctx, it, px(ctx, ch)) }
        val zonas = listOf(
            // La cabecera lleva a la sección entera; cada póster, a su ficha
            Zona(0f, 0f, w, top, abrirSeccion(ctx, Destino.Pendiente), "Abrir Pendientes en Hathor"),
            Zona(pad, top, w - 2 * pad, h - top, null, "", listOf(items.map { abrirFicha(ctx, it) to "Abrir ${it.nombre}" }))
        )
        return Pagina(zonas) {
            fondo()
            seccion("Pendientes", pad, pad, w - 2 * pad, tinta, acento)
            pon(capa("Hathor", tp(fTitular, 13f, tinta), 80f, 1, 1f, Layout.Alignment.ALIGN_OPPOSITE), w - pad - 80f, pad - 3f)
            c.drawRect(pad, pad + 14.5f, w - pad, pad + 15.2f, lleno(linea))
            items.forEachIndexed { k, e ->
                val gap = 5f
                val x = pad + slot * k + gap
                val cw = min(slot - 2 * gap, ch * 0.68f)
                poster(imgs[k], RectF(x, top, x + cw, top + ch), e.nombre, 0f)
                if (bajo) {
                    val tx = x + cw + 8f
                    val tw = slot - 2 * gap - cw - 8f
                    val est = capa(d.origen(e).uppercase(ES), tp(fNegrita, 7.5f, acento, 0.16f), tw, 2)
                    val l = titular(e.nombre, tw, ch - est.height - 4f, 15f, 10f, 3)
                    pon(l, tx, top)
                    pon(est, tx, top + ch - est.height)
                } else {
                    val tw = slot - 2 * gap
                    val l = capa(e.nombre, tp(fTitular, 12f, tinta, -0.01f), tw, 2, 0.95f)
                    pon(l, x, top + ch + 5f)
                    ante(d.origen(e), x, top + ch + 7f + l.height, tw, acento, 7.5f)
                }
            }
        }
    }

    // ------------------------------------------------------------------ En portada (lo que estás viendo, a toda página)

    private suspend fun enPortada(d: WDatos, id: Int, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val items = d.viendo
        if (items.isEmpty()) return vacio(ctx, "En portada", "Nada a medias", "Empieza una película o una serie en Hathor y saldrá aquí, a toda página.", w, h)
        val i = Math.floorMod(estado(ctx, id, "i", 0), items.size)
        val e = items[i]
        val img = imagenFondo(ctx, e, px(ctx, max(w, h)))
        val sigue = d.porDonde(e)
        val frac = d.fraccion(e)
        // Como «Seguir viendo»: todo el widget abre su ficha (allí está «Reproducir», con el episodio que toca); la esquina, el siguiente
        val zonas = mutableListOf(Zona(0f, 0f, w, h, abrirFicha(ctx, e), "Seguir viendo ${e.nombre}"))
        if (items.size > 1) zonas += Zona(w - 80f, 0f, 80f, 52f, op(ctx, WTipo.PORTADA, id, "sig"), "Siguiente")
        val fecha = LocalDate.now().let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')} ${it.year}" }
        return Pagina(zonas) {
            val pad = 16f
            val blanco = 0xFFFFFFFF.toInt()
            if (img != null) {
                poster(img, RectF(0f, 0f, w, h), e.nombre, 0f, false); reborde = 0xCCFFFFFF.toInt()
                velo(0f, min(96f, h * 0.42f), 0xC8000000.toInt(), 0x00000000, 0x70000000)
                velo(h * 0.18f, h, 0x00000000, 0xF2000000.toInt(), 0x99000000.toInt())
            } else { fondo(0xFF15120F.toInt()); reborde = 0xCCFFFFFF.toInt() }
            // Cabecera de la revista
            val mast = capa("Hathor", tp(fTitular, if (h < 180f) 22f else 28f, blanco, -0.02f), w * 0.6f, 1)
            pon(mast, pad, pad - 4f)
            val fw = w - 2 * pad - mast.getLineWidth(0) - 8f
            ante(if (fw < anchoAnte(fecha, 8.5f)) fecha.substringBeforeLast(' ') else fecha, w - pad - fw, pad, fw, 0xDDFFFFFF.toInt(), 8.5f, Layout.Alignment.ALIGN_OPPOSITE)
            paginas(i, items.size, w - pad, pad + 13f, 0xBBFFFFFF.toInt())
            c.drawRect(pad, pad + mast.height + 1f, w - pad, pad + mast.height + 1.8f, lleno(0x66FFFFFF))
            // El pie: de dónde es y «Seguir»; encima, lo que llevas
            var y = h - pad
            val pie = capa("Seguir  →".uppercase(ES), tp(fNegrita, 9f, acentoFoto, 0.16f), 90f, 1, 1f, Layout.Alignment.ALIGN_OPPOSITE)
            pon(pie, w - pad - 90f, y - pie.height)
            val meta = d.origen(e).takeIf { h >= 150f }
            if (meta != null) { val ml = capa(meta, tp(fCursiva, 12.5f, 0xCCFFFFFF.toInt()), w - 2 * pad - 96f, 1); pon(ml, pad, y - ml.height); y -= max(ml.height.toFloat(), pie.height.toFloat()) }
            else y -= pie.height
            if (frac != null) { y -= 9f; barra(pad, y, w - 2 * pad, frac, 0x55FFFFFF, acentoFoto); y -= 5f } else y -= 6f
            // El titular
            val l = titular(e.nombre, w - 2 * pad, min(h * 0.46f, y - pad - mast.height - 30f), if (w > 280f) 40f else 30f, 14f, 3, blanco, inter = 0.92f)
            y -= l.height
            pon(l, pad, y)
            // Por dónde vas, en una etiqueta de acento (se lee sobre cualquier foto)
            if (sigue.isNotBlank()) {
                val ew = min(w - 2 * pad, anchoAnte(sigue, 8.5f) + 12f)
                c.drawRect(pad, y - 19f, pad + ew, y - 4f, lleno(acentoBloque))
                ante(sigue, pad + 6f, y - 16.5f, ew - 10f, if (androidx.compose.ui.graphics.Color(acentoBloque).luminance() > 0.5f) 0xFF14151A.toInt() else 0xFFFFFFFF.toInt(), 8.5f)
            }
        }
    }

    // ------------------------------------------------------------------ Próximos capítulos

    private fun proximos(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val todos = d.proximos
        if (todos.isEmpty()) return vacio(ctx, "Próximos capítulos", "Nada a la vista",
            if (Proximos.fijos != null || Tmdb.activo) "Cuando una serie de tu biblioteca anuncie episodio nuevo, saldrá aquí con su fecha."
            else "Pon tu clave de TMDB en Hathor y aquí verás qué se estrena de tus series.", w, h)
        val pad = 14f
        val top = pad + 22f
        val bajo = h < 130f
        val fila = if (bajo) 24f else 36f
        val n = ((h - top - pad + 6f) / fila).toInt().coerceIn(1, 8).coerceAtMost(todos.size)
        val items = todos.take(n)
        val hoy = LocalDate.now()
        val zonas = mutableListOf(Zona(0f, 0f, w, top, abrirSeccion(ctx, Destino.Proximos), "Abrir Próximos en Hathor"))
        items.forEachIndexed { k, x ->
            val t = d.titulo(x.ref)
            zonas += Zona(0f, top + k * fila - 3f, w, fila, t?.let { abrirFicha(ctx, it) } ?: abrirApp(ctx), "Abrir ${x.serie}")
        }
        return Pagina(zonas) {
            fondo()
            seccion(if (w < 220f) "Próximos" else "Próximos capítulos", pad, pad, w - 2 * pad - 54f, tinta, acento)
            pon(capa("Hathor", tp(fTitular, 13f, tinta), 80f, 1, 1f, Layout.Alignment.ALIGN_OPPOSITE), w - pad - 80f, pad - 3f)
            c.drawRect(pad, pad + 14.5f, w - pad, pad + 15.2f, lleno(linea))
            val fw = min(84f, (w - 2 * pad) * 0.3f)
            val x0 = pad + fw + 8f
            val rw = w - pad - x0
            items.forEachIndexed { k, x ->
                val y = top + k * fila
                if (k > 0) c.drawRect(pad, y - 4f, w - pad, y - 3.4f, lleno(linea))
                val dia = x.dia ?: return@forEachIndexed
                val pronto = !dia.isAfter(hoy.plusDays(1))
                ante(Proximos.cuando(dia, hoy), pad, y + 3f, fw, if (pronto) acento else gris, 8.5f)
                val ep = Proximos.episodio(x)
                if (bajo) {
                    val corto = "T${x.temporada} · E${x.episodio}"
                    val ew = anchoAnte(corto, 8f) + 2f
                    pon(capa(x.serie, tp(fTitular, 13f, tinta, -0.01f), rw - ew - 8f, 1), x0, y)
                    ante(corto, w - pad - ew, y + 3f, ew, gris, 8f, Layout.Alignment.ALIGN_OPPOSITE)
                } else {
                    val l = capa(x.serie, tp(fTitular, 14f, tinta, -0.01f), rw, 1)
                    pon(l, x0, y - 1f)
                    pon(capa(ep, tp(fCursiva, 11.5f, gris), rw, 1), x0, y + l.height + 1f)
                }
            }
        }
    }

    // ------------------------------------------------------------------ Tu semana

    private fun semana(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val sem = d.semana
        if (sem.vacia) return vacio(ctx, "Tu semana", "Nada visto estos días", "Lo que veas en Hathor se irá sumando aquí, día a día.", w, h)
        val hoy = LocalDate.now()
        return Pagina(listOf(Zona(0f, 0f, w, h, abrirApp(ctx), "Abrir Hathor"))) {
            fondo()
            val pad = if (h < 90f) 12f else 16f
            seccion("Tu semana", pad, pad, w - 2 * pad - 54f, tinta, acento)
            pon(capa("Hathor", tp(fTitular, 13f, tinta), 80f, 1, 1f, Layout.Alignment.ALIGN_OPPOSITE), w - pad - 80f, pad - 3f)
            c.drawRect(pad, pad + 14.5f, w - pad, pad + 15.2f, lleno(linea))
            val top = pad + 26f
            val ancho = w - 2 * pad
            // A la izquierda (si cabe): el total y lo que más has visto
            val izqW = if (w >= 230f) ancho * 0.47f else 0f
            if (izqW > 0f) {
                val big = titular(Semana.horas(sem.total), izqW - 6f, 40f, 30f, 14f, 1, acento)
                pon(big, pad, top - 2f)
                var y = top + big.height
                y += ante("estos 7 días", pad, y + 1f, izqW, gris, 8.5f) + 8f
                for ((_, nombre, ms) in sem.titulos) {
                    val l = capa(nombre, tp(fTitular, 12f, tinta, -0.01f), izqW, 1)
                    if (y + l.height > h - pad) break
                    // (las horas en minúscula: «1,5 h», no «1,5 H»)
                    val hp = tp(fTexto, 10.5f, gris)
                    val tw = hp.measureText(Semana.corto(ms)) + 2f
                    pon(capa(nombre, tp(fTitular, 12f, tinta, -0.01f), izqW - tw - 6f, 1), pad, y)
                    pon(capa(Semana.corto(ms), hp, tw, 1, 1f, Layout.Alignment.ALIGN_OPPOSITE), pad + izqW - tw, y + 1.5f)
                    y += l.height + 4f
                }
            }
            // Las barras: lo visto cada día (hoy, en acento)
            val x0 = pad + izqW + if (izqW > 0f) 16f else 0f
            val bw = w - pad - x0
            val etiqueta = 12f
            val alto = h - top - pad - 14f - etiqueta
            val tope = max(sem.maximo, 60_000L).toFloat()
            val slot = bw / 7f
            sem.dias.forEachIndexed { k, (dia, ms) ->
                val bx = x0 + slot * k + slot * 0.19f
                val bwid = slot * 0.62f
                val base = top + etiqueta + alto
                val hh = if (ms >= 60_000L) max(3f, alto * ms / tope) else 1f
                c.drawRect(bx, base - hh, bx + bwid, base, lleno(if (ms >= 60_000L) (if (dia == hoy) acento else tinta) else linea))
                if (ms == sem.maximo && ms >= 60_000L) pon(capa(Semana.corto(ms), tp(fTexto, 9.5f, gris), slot + 24f, 1, 1f, Layout.Alignment.ALIGN_CENTER), x0 + slot * k - 12f, base - hh - 13f)
                ante(dia.dayOfWeek.getDisplayName(TextStyle.NARROW, ES), x0 + slot * k - 2f, base + 3f, slot + 4f, if (dia == hoy) acento else gris, 8.5f, Layout.Alignment.ALIGN_CENTER)
            }
        }
    }

    // ------------------------------------------------------------------ La marca

    private fun hoyLargo(): String {
        val hoy = LocalDate.now()
        return "${hoy.dayOfWeek.getDisplayName(TextStyle.FULL, ES)} ${hoy.dayOfMonth} de ${hoy.month.getDisplayName(TextStyle.FULL, ES)}"
    }

    /** HATHOR: solo la palabra (y el punto de acento), sobre el fondo de pantalla. Un toque y estás en la portada. */
    private fun marca(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val fecha = hoyLargo()
        return Pagina(listOf(Zona(0f, 0f, w, h, abrirApp(ctx), "Abrir Hathor"))) {
            // Solo la palabra, sobre el fondo de pantalla (con una sombra suave para que se lea en cualquiera)
            val color = if (nombreEnColor) acentoNombre else if (noche) 0xFFF6F1E7.toInt() else tinta
            val sombra = if (noche) 0x99000000.toInt() else 0x55FFFFFF
            val pie = if (h >= 84f) 16f else 0f
            val p = tp(fTitular, 10f, color, -0.04f)
            val ancho = p.measureText("HATHOR.")
            var s = min((w - 8f) / (ancho / 10f), (h - pie - 4f) / 0.95f)
            s = s.coerceAtLeast(10f)
            val tpa = tp(fTitular, s, color, -0.04f).apply { setShadowLayer(s * 0.06f, 0f, s * 0.02f, sombra) }
            val fm = tpa.fontMetrics
            val tw = tpa.measureText("HATHOR")
            val total = tpa.measureText("HATHOR.")
            val x = (w - total) / 2
            val capH = s * 0.72f
            val base = (h - pie) / 2 + capH / 2
            c.drawText("HATHOR", x, base, tpa)
            // «HATHOR.»: el punto, en el acento (la cabecera de la dirección C)
            c.drawText(".", x + tw, base, tp(fTitular, s, if (noche) acentoClaro else acento, -0.04f).apply { setShadowLayer(s * 0.04f, 0f, s * 0.015f, sombra) })
            if (pie > 0f) {
                val pp = tp(fNegrita, 8.5f, color, 0.2f).apply { setShadowLayer(2f, 0f, 0.6f, sombra) }
                val largo = "Películas y series · $fecha".uppercase(ES)
                val txt = if (pp.measureText(largo) <= w - 8f) largo else fecha.uppercase(ES)
                val l = capa(txt, pp, w - 8f, 1, 1f, Layout.Alignment.ALIGN_CENTER)
                pon(l, 4f, min(h - l.height - 2f, base + fm.descent * 0.4f + 2f))
            }
        }.transparente()
    }

    /** HATHOR en papel: la cabecera de la portada (el nombre, el número del día y la fecha), con su papel de fondo. */
    private fun marcaPapel(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val hoy = LocalDate.now()
        val fecha = hoyLargo()
        return Pagina(listOf(Zona(0f, 0f, w, h, abrirApp(ctx), "Abrir Hathor"))) {
            // Dirección C: la cabecera de la portada, en un bloque de tinta con el nombre del color del papel
            // Con «papel teñido» o «todo acento», el bloque de tinta también lo lleva
            val (tinta, papel) = bloqueMarca()
            fondo(tinta)
            val pad = if (h < 90f) 12f else 16f
            // Lo de debajo del nombre, según el sitio: la línea del número y la fecha; y la entradilla si cabe
            val conLinea = h >= 70f
            val conEntradilla = h >= 140f
            val bajo = (if (conLinea) 22f else 0f) + (if (conEntradilla) 24f else 0f)
            val alto = h - 2 * pad - bajo
            val p = tp(fTitular, 10f, papel, -0.04f)
            val s = min((w - 2 * pad) / (p.measureText("HATHOR.") / 10f), alto / 0.78f).coerceAtLeast(10f)
            val tpa = tp(fTitular, s, papel, -0.04f)
            val capH = s * 0.72f
            // Todo el bloque centrado en el widget (el nombre se mide por sus mayúsculas, no por la caja de la letra)
            val bloque = capH + s * 0.08f + 4f + bajo
            val base = max(pad, (h - bloque) / 2) + capH
            c.drawText("HATHOR", pad, base, tpa)
            c.drawText(".", pad + tpa.measureText("HATHOR"), base, tp(fTitular, s, acentoSobreTinta, -0.04f))
            var y = base + s * 0.08f + 4f
            if (conLinea) {
                c.drawRect(pad, y, w - pad, y + 2f, lleno(papel))
                y += 6f
                val iz = "Nº ${hoy.dayOfYear}"
                ante(iz, pad, y, w / 2, papel, 8.5f)
                val sitio = w - 2 * pad - anchoAnte(iz, 8.5f) - 12f
                val corta = "${hoy.dayOfWeek.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')} ${hoy.dayOfMonth} ${hoy.month.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')}"
                ante(if (anchoAnte(fecha, 8.5f) > sitio) corta else fecha, pad + anchoAnte(iz, 8.5f) + 12f, y, sitio, papel, 8.5f, Layout.Alignment.ALIGN_OPPOSITE)
                y += 14f
            }
            if (conEntradilla) pon(capa("Tus películas y series, al día", tp(fCursiva, 13f, (papel and 0x00FFFFFF) or 0xCC000000.toInt()), w - 2 * pad, 1), pad, y)
        }
    }
}
