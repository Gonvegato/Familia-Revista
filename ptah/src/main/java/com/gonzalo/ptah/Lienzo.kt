package com.gonzalo.ptah

import android.content.Context
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
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * El lienzo de un diseño: se dibuja en dp (el tamaño del widget), con los colores y las letras del diseño. El mismo dibujo sirve para
 * la vista previa del editor (y las miniaturas de la portada) y para el widget de la pantalla de inicio: así lo que ves al diseñarlo es
 * lo que sale.
 *
 * Lo que se mueve solo (la hora, la fecha, las agujas) no se dibuja en el widget: un dibujo no se vuelve a pintar cada minuto de forma
 * fiable (Android no despierta la app para eso). En su sitio el widget lleva un `TextClock` o un `AnalogClock` de Android, que el
 * lanzador pone al día solo ([Vivo]). En la app se dibuja con la misma letra, el mismo tamaño y el mismo sitio.
 */

private val ES = Locale("es", "ES")

/** Lo que pasa al tocar una zona de un widget. */
sealed class Accion {
    /** Abre Ptah (su portada). */
    data object AbrirPtah : Accion()
    /** Abre el editor de ese diseño. */
    data class Editar(val id: String) : Accion()
    /** Abre esa app. */
    data class App(val paquete: String) : Accion()
    /** Abre esa dirección en el navegador. */
    data class Web(val url: String) : Accion()
    /** El buscador: el de serie (ACTION_WEB_SEARCH) o el navegador elegido ([navegador], su paquete). */
    data class Buscar(val navegador: String) : Accion()
    /** La búsqueda por voz. */
    data object BuscarVoz : Accion()
    /** Las alarmas (la app del reloj). */
    data object Alarmas : Accion()
    /** El calendario, en hoy. */
    data object Calendario : Accion()
    /** El uso de la batería. */
    data object Bateria : Accion()
    /** Elegir el diseño de este widget (el que no tiene, o el que se borró). */
    data object Configurar : Accion()
}

/** Una zona que se puede tocar (en dp). */
class Zona(val r: RectF, val accion: Accion, val desc: String)

/** Lo que el lanzador pone al día solo, encima del dibujo (en dp). */
sealed class Vivo {
    abstract val r: RectF

    /** Un `TextClock`: la hora o la fecha con su formato ([formato], el de `android.text.format.DateFormat`). [tam] en dp. */
    class Texto(override val r: RectF, val formato: String, val letra: LetraFija, val tam: Float, val color: Int, val al: Alineacion, val formato12: String = formato) : Vivo()

    /** Un `AnalogClock` con las agujas del diseño (cuadrado: [r] es el de la esfera). [segundero]: null, sin él. El centro va del color de la minutera. */
    class Agujas(override val r: RectF, val forma: FormaAgujas, val horaria: Int, val minutera: Int, val segundero: Int?, val centro: Int) : Vivo()
}

/** Cómo son las agujas. */
enum class FormaAgujas(val nombre: String) { BASTON("Bastón"), HOJA("Hoja"), FINA("Fina") }

/** Lo que hace falta saber para dibujar (la hora, el tiempo, la batería, los iconos y las fotos): en las pruebas, de mentira. */
interface Datos {
    fun ahora(): ZonedDateTime
    fun tiempo(d: Diseno): Prevision?
    fun bateria(): Bateria?
    fun iconoApp(paquete: String): Bitmap?
    fun nombreApp(paquete: String): String?
    fun foto(id: String): Bitmap?
}

data class Bateria(val nivel: Int, val cargando: Boolean)

class Lienzo(
    val ctx: Context, val d: Diseno, val w: Float, val h: Float, val noche: Boolean, s: Float,
    val datos: Datos,
    /** Para el widget: lo que se mueve solo se apunta en [vivos] en lugar de dibujarse. */
    val enWidget: Boolean = false,
    /**
     * Solo en el widget: lo que se mueve solo va como `TextClock`/`AnalogClock` (true) o dibujado aquí, con la letra de verdad (false: el
     * widget se vuelve a pintar cada minuto, [Minutero]). En la app siempre se dibuja.
     */
    val relojVivo: Boolean = false,
) {
    private val apuntaVivos get() = enWidget && relojVivo
    val bm: Bitmap = Bitmap.createBitmap(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()), Bitmap.Config.ARGB_8888)
    val c = Canvas(bm).apply { scale(bm.width / w, bm.height / h) }
    val e: Estilo get() = d.estilo
    val zonas = ArrayList<Zona>()
    val vivos = ArrayList<Vivo>()

    // ---- colores
    val tonos: Tones = Skin.tonosPara(noche)
    private fun col(spec: String, def: Int) = Col.resuelve(spec, tonos, def)
    val papel: Int = tonos.paper.toInt()
    val tinta: Int = col(e.tinta, tonos.ink.toInt())
    val acento: Int = col(e.acento, tonos.general.toInt())
    val gris: Int = col(e.secundario, mezcla(papelDeVerdad(), tinta, 0.55f))
    /** El color de una pieza (si el diseño le da uno; si no, [def]). */
    fun pieza(id: String, def: Int): Int = col(e.pieza(id), def)
    /** Lo que hay debajo del texto (para elegir un color que se lea encima de un fondo transparente). */
    fun papelDeVerdad(): Int = when (e.fondo) {
        TipoFondo.PAPEL -> papel
        TipoFondo.COLOR -> col(e.colorFondo, papel)
        TipoFondo.DEGRADADO -> mezcla(col(e.colorFondo, papel), col(e.colorFondo2, acento), 0.5f)
        TipoFondo.TRANSPARENTE -> if (noche) 0xFF000000.toInt() else 0xFF888888.toInt()
    }

    // ---- letras
    val fTitular: Typeface = Letras.titular(ctx, e.letraTitulos)
    val fCursiva: Typeface = Letras.cursiva(ctx, e.letraTitulos)
    val fTexto: Typeface = Letras.texto(ctx, e.letraTexto)
    val fNegrita: Typeface = Letras.negrita(ctx)
    /** El tamaño relativo del texto: el del diseño y, en los widgets grandes, algo más (un 4×4 no lleva la letra de un 2×1). */
    val k: Float = e.escala * (min(w, h) / 170f).coerceIn(1f, 1.3f)

    // ---- márgenes
    val radio: Float = min(e.radio, min(w, h) / 2f)
    val pad: Float = (if (min(w, h) < 100f) 9f else 14f) + (if (e.marco) 4f else 0f) + (if (e.borde) e.grosorBorde * 0.5f else 0f)
    /** El sitio para lo de dentro. */
    val area = RectF(pad, pad, w - pad, h - pad)
    val alineacion: Layout.Alignment get() = when (e.alineacion) { Alineacion.IZQUIERDA -> Layout.Alignment.ALIGN_NORMAL; Alineacion.CENTRO -> Layout.Alignment.ALIGN_CENTER; Alineacion.DERECHA -> Layout.Alignment.ALIGN_OPPOSITE }

    private val forma: Path get() = Path().apply { addRoundRect(RectF(0f, 0f, w, h), radio, radio, Path.Direction.CW) }

    /** El fondo del diseño (papel, color, degradado o nada), con su opacidad. */
    fun fondo() {
        if (e.fondo == TipoFondo.TRANSPARENTE || e.opacidad <= 0) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val a = (e.opacidad.coerceIn(0, 100) * 255 / 100)
        when (e.fondo) {
            TipoFondo.PAPEL -> p.color = conAlfa(papel, a)
            TipoFondo.COLOR -> p.color = conAlfa(col(e.colorFondo, papel), a)
            TipoFondo.DEGRADADO -> {
                val ang = Math.toRadians(e.angulo.toDouble())
                val dx = sin(ang).toFloat(); val dy = cos(ang).toFloat()
                val cx = w / 2; val cy = h / 2; val r = (kotlin.math.abs(dx) * w + kotlin.math.abs(dy) * h) / 2
                p.shader = LinearGradient(cx - dx * r, cy - dy * r, cx + dx * r, cy + dy * r, conAlfa(col(e.colorFondo, papel), a), conAlfa(col(e.colorFondo2, acento), a), Shader.TileMode.CLAMP)
            }
            TipoFondo.TRANSPARENTE -> return
        }
        c.drawPath(forma, p)
    }

    /** El borde, el marco de revista y las esquinas (recortadas suaves). Se llama al acabar. */
    fun terminar() {
        if (e.marco) {
            val m = (if (e.borde) e.grosorBorde else 0f) + 4.5f
            val rr = max(0f, radio - m)
            c.drawRoundRect(RectF(m, m, w - m, h - m), rr, rr, trazo(pieza("marco", tinta), 0.6f))
        }
        if (e.borde) {
            val g = e.grosorBorde
            val m = g / 2f + 0.4f
            c.drawRoundRect(RectF(m, m, w - m, h - m), max(0f, radio - m), max(0f, radio - m), trazo(col(e.colorBorde, tinta), g))
        }
        if (radio > 0f) {
            val p = Path().apply { addRoundRect(RectF(0f, 0f, w, h), radio, radio, Path.Direction.CW); fillType = Path.FillType.INVERSE_WINDING }
            c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
        }
    }

    // ---- pinturas
    fun lleno(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    fun trazo(color: Int, grueso: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = grueso }

    /** Una pintura de texto ([size] en dp, sin la escala del diseño: [tpk] la pone). */
    fun tp(f: Typeface, size: Float, color: Int, track: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = f; textSize = size; this.color = color; letterSpacing = track; textLocale = ES
        }
    fun tpk(f: Typeface, size: Float, color: Int, track: Float = 0f) = tp(f, size * k, color, track)

    fun capa(t: CharSequence, p: TextPaint, ancho: Float, lineas: Int = Int.MAX_VALUE, inter: Float = 1f, al: Layout.Alignment = alineacion): StaticLayout =
        StaticLayout.Builder.obtain(t, 0, t.length, p, max(1, ancho.toInt())).setAlignment(al).setMaxLines(max(1, lineas))
            .setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, inter).setIncludePad(false).build()

    /** Pinta un texto ya compuesto; devuelve su alto. */
    fun pon(l: Layout, x: Float, y: Float): Float { c.save(); c.translate(x, y); l.draw(c); c.restore(); return l.height.toFloat() }

    /** Un titular tan grande como quepa (sin partir palabras) en [lineas] y [alto] ([maxS] y [minS] ya con la escala del diseño). */
    fun titular(
        t: String, ancho: Float, alto: Float, maxS: Float, minS: Float, lineas: Int, color: Int = tinta,
        f: Typeface = fTitular, inter: Float = 0.95f, al: Layout.Alignment = alineacion, track: Float = -0.01f,
    ): StaticLayout {
        var s = maxS * k
        val minimo = min(minS * k, s)
        while (true) {
            val p = tp(f, s, color, track)
            val palabra = t.split(' ').maxOfOrNull { p.measureText(it) } ?: 0f
            val l = capa(t, p, ancho, Int.MAX_VALUE, inter, al)
            if (l.lineCount <= lineas && l.height <= alto && palabra <= ancho) return l
            if (s <= minimo) return capa(t, p, ancho, max(1, min(lineas, (alto / (s * 1.15f)).toInt())), inter, al)
            s = max(minimo, s - max(0.5f, s * 0.06f))
        }
    }

    /** Antetítulo: mayúsculas pequeñas, negras y espaciadas. Devuelve el alto. */
    /**
     * Antetítulo: mayúsculas pequeñas, negras y espaciadas. Si no cabe, se hace algo más pequeño (hasta un 70 %); si ni así cabe, no se
     * pone (nunca cortado). Devuelve el alto (0 si no se ha puesto).
     */
    fun ante(t: String, x: Float, y: Float, ancho: Float, color: Int = tinta, size: Float = 9f, al: Layout.Alignment = alineacion): Float {
        val s = cabe(t.uppercase(ES), fNegrita, size * k, ancho, 0.16f) ?: return 0f
        return pon(capa(t.uppercase(ES), tp(fNegrita, s, color, 0.16f), ancho + 1f, 1, 1f, al), x, y)
    }
    fun anchoAnte(t: String, size: Float = 9f) = tpk(fNegrita, size, tinta, 0.16f).measureText(t.uppercase(ES))
    fun altoAnte(size: Float = 9f) = capa("H", tpk(fNegrita, size, tinta, 0.16f), 100f, 1).height.toFloat()

    /** El tamaño (como mucho [size], como poco su 70 %) con el que [t] cabe en una línea de [ancho]; null si no cabe. */
    fun cabe(t: String, f: Typeface, size: Float, ancho: Float, track: Float = 0f): Float? {
        var s = size
        while (s >= size * 0.7f - 0.01f) {
            if (tp(f, s, 0, track).measureText(t) <= ancho) return s
            s -= max(0.25f, size * 0.05f)
        }
        return null
    }

    /** Una línea de texto que, si no cabe, se hace algo más pequeña y, si ni así, no se pone. Devuelve el alto (0 si no se ha puesto). */
    fun linea(t: String, f: Typeface, size: Float, color: Int, x: Float, y: Float, ancho: Float, al: Layout.Alignment = alineacion, track: Float = 0f): Float {
        val s = cabe(t, f, size * k, ancho, track) ?: return 0f
        return pon(capa(t, tp(f, s, color, track), ancho + 1f, 1, 1f, al), x, y)
    }
    /** El alto que ocupará esa línea (0 si no cabe). */
    fun altoLinea(t: String, f: Typeface, size: Float, ancho: Float, track: Float = 0f): Float {
        val s = cabe(t, f, size * k, ancho, track) ?: return 0f
        return tp(f, s, 0, track).fontMetrics.let { it.descent - it.ascent }
    }

    /** La cabecera de sección: el cuadradito de acento y el antetítulo. Devuelve el alto (0 si no cabe: entonces no se pone). */
    fun seccion(t: String, x: Float, y: Float, ancho: Float, color: Int = tinta, cuadro: Int = pieza("cuadro", acento), size: Float = 9f): Float {
        val s = cabe(t.uppercase(ES), fNegrita, size * k, ancho - size * k * 0.82f - 5f, 0.16f) ?: return 0f
        val q = s * 0.82f
        val alto = capa("H", tp(fNegrita, s, tinta, 0.16f), 100f, 1).height.toFloat()
        val anchoT = tp(fNegrita, s, tinta, 0.16f).measureText(t.uppercase(ES))
        val x0 = when (alineacion) { Layout.Alignment.ALIGN_CENTER -> x + (ancho - anchoT - q - 5f) / 2; Layout.Alignment.ALIGN_OPPOSITE -> x + ancho - anchoT - q - 5f; else -> x }
        c.drawRect(x0, y + (alto - q) / 2, x0 + q, y + (alto + q) / 2, lleno(cuadro))
        pon(capa(t.uppercase(ES), tp(fNegrita, s, color, 0.16f), anchoT + 2f, 1, 1f, Layout.Alignment.ALIGN_NORMAL), x0 + q + 5f, y)
        return alto
    }

    /** ¿Cabe lo que pondría un `TextClock` con esa letra a ese tamaño (lo más ancho que puede salir)? */
    fun cabeVivo(formato: String, l: LetraFija, tam: Float, ancho: Float): Boolean {
        val p = tp(letraFija(l), tam, 0, l.espaciado)
        return Formatos.muestras(formato).maxOf { p.measureText(if (l.mayusculas) it.uppercase(ES) else it) } <= ancho
    }

    /** Un filete (raya) a todo lo ancho del área. */
    fun filete(y: Float, grueso: Float = 1.6f, color: Int = pieza("rayas", tinta), x0: Float = area.left, x1: Float = area.right) = c.drawRect(x0, y, x1, y + grueso, lleno(color))

    /** Una foto recortada (centrada) en [r], con esquinas de radio [rad]. */
    fun foto(img: Bitmap, r: RectF, rad: Float = 0f) {
        val s = max(r.width() / img.width, r.height() / img.height)
        val m = Matrix().apply { setScale(s, s); postTranslate(r.left + (r.width() - img.width * s) / 2, r.top + (r.height() - img.height * s) / 2) }
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = BitmapShader(img, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) } }
        c.drawRoundRect(r, rad, rad, p)
    }

    /** Una zona que se puede tocar. */
    fun toque(r: RectF, a: Accion, desc: String) { zonas += Zona(RectF(r), a, desc) }

    // ---- lo que se mueve solo

    /** La x donde empieza un texto de [ancho] en [r] según la alineación. */
    fun xAlineada(r: RectF, ancho: Float, al: Alineacion = e.alineacion) = when (al) { Alineacion.IZQUIERDA -> r.left; Alineacion.CENTRO -> r.left + (r.width() - ancho) / 2; Alineacion.DERECHA -> r.right - ancho }

    /** La letra de lo que va en un `TextClock`. */
    fun letraFija(l: LetraFija): Typeface = Letras.fuente(ctx, l.fuente)

    /**
     * El tamaño (dp) con el que cabe [muestra] (el texto más ancho que puede salir: «23:59», «miércoles 30 de septiembre»…) en [ancho] × [alto]
     * con esa letra. Así un `TextClock` nunca se corta: ni con el día más largo.
     */
    fun tamanoQueCabe(muestras: List<String>, l: LetraFija, ancho: Float, alto: Float, max: Float): Float {
        var s = max
        while (s > 6f) {
            val p = tp(letraFija(l), s, 0, l.espaciado)
            val anchoT = muestras.maxOf { p.measureText(if (l.mayusculas) it.uppercase(ES) else it) }
            val fm = p.fontMetrics
            if (anchoT <= ancho * 0.98f && (fm.descent - fm.ascent) <= alto) break
            s -= max(0.5f, s * 0.04f)
        }
        return s
    }

    /** El alto de una línea de esa letra a ese tamaño (lo que ocupa un `TextClock` sin el relleno de la letra). */
    fun altoLinea(l: LetraFija, tam: Float): Float = tp(letraFija(l), tam, 0, l.espaciado).fontMetrics.let { it.descent - it.ascent }

    /**
     * La hora o la fecha que se pone al día sola ([formato] de `DateFormat`: «H:mm», «EEEE d 'de' MMMM»…), centrada en alto en [r].
     * En el widget es un `TextClock`; aquí se dibuja la de ahora.
     */
    fun textoVivo(r: RectF, formato: String, l: LetraFija, tam: Float, color: Int, al: Alineacion = e.alineacion, formato12: String = formato) {
        if (apuntaVivos) { vivos += Vivo.Texto(RectF(r), formato, l, tam, color, al, formato12); return }
        val f = if (formato12 != formato && !android.text.format.DateFormat.is24HourFormat(ctx)) formato12 else formato
        val t = Formatos.formatea(f, datos.ahora()).let { if (l.mayusculas) it.uppercase(ES) else it }
        val p = tp(letraFija(l), tam, color, l.espaciado)
        val lay = capa(t, p, r.width(), 1, 1f, when (al) { Alineacion.IZQUIERDA -> Layout.Alignment.ALIGN_NORMAL; Alineacion.CENTRO -> Layout.Alignment.ALIGN_CENTER; Alineacion.DERECHA -> Layout.Alignment.ALIGN_OPPOSITE })
        pon(lay, r.left, r.top + (r.height() - lay.height) / 2)
    }

    /** Las agujas, en la esfera [r] (cuadrada). En el widget es un `AnalogClock`; aquí se dibujan las de ahora. */
    fun agujas(r: RectF, forma: FormaAgujas, horaria: Int, minutera: Int, segundero: Int?, centro: Int) {
        if (apuntaVivos) { vivos += Vivo.Agujas(RectF(r), forma, horaria, minutera, segundero, centro); return }
        val t = datos.ahora()
        val rad = r.width() / 2f
        val m = t.minute + t.second / 60f
        val hAng = ((t.hour % 12) + m / 60f) * 30f
        Agujas.pinta(c, r.centerX(), r.centerY(), rad, hAng, Agujas.Pieza.HORA, forma, horaria)
        Agujas.pinta(c, r.centerX(), r.centerY(), rad, m * 6f, Agujas.Pieza.MINUTO, forma, minutera)
        // (en el widget dibujado no hay segundero: se pinta una vez por minuto, se quedaría quieto)
        if (segundero != null && !enWidget) Agujas.pinta(c, r.centerX(), r.centerY(), rad, t.second * 6f, Agujas.Pieza.SEGUNDO, forma, segundero)
        Agujas.pinta(c, r.centerX(), r.centerY(), rad, 0f, Agujas.Pieza.CENTRO, forma, centro)
    }

    companion object {
        fun conAlfa(c: Int, a: Int) = (c and 0x00FFFFFF) or ((((c ushr 24) * a) / 255) shl 24)
        fun mezcla(a: Int, b: Int, t: Float): Int {
            fun ch(x: Int, s: Int) = (x shr s) and 0xFF
            fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).roundToInt().coerceIn(0, 255)
            return (0xFF shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
        }
        fun luz(c: Int): Float = (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f
    }
}

/** Cómo se dibuja cada aguja (en la app y, para el `AnalogClock` del widget, cada una en su máscara). */
object Agujas {
    enum class Pieza { HORA, MINUTO, SEGUNDO, CENTRO }

    /** Una aguja de la esfera de radio [rad] con centro ([cx], [cy]), girada [grados] desde las 12. */
    fun pinta(c: Canvas, cx: Float, cy: Float, rad: Float, grados: Float, pieza: Pieza, forma: FormaAgujas, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        if (pieza == Pieza.CENTRO) { c.drawCircle(cx, cy, rad * if (forma == FormaAgujas.FINA) 0.035f else 0.055f, p); return }
        c.save(); c.rotate(grados, cx, cy)
        val (largo, ancho, cola) = when (pieza) {
            Pieza.HORA -> Triple(0.52f, if (forma == FormaAgujas.FINA) 0.035f else 0.075f, 0.1f)
            Pieza.MINUTO -> Triple(0.8f, if (forma == FormaAgujas.FINA) 0.025f else 0.05f, 0.12f)
            else -> Triple(0.86f, 0.014f, 0.2f)
        }
        val L = rad * largo; val W = max(0.8f, rad * ancho); val T = rad * cola
        if (pieza == Pieza.SEGUNDO) {
            c.drawRect(cx - W / 2, cy - L, cx + W / 2, cy + T, p)
            c.drawCircle(cx, cy + T * 0.75f, W * 2.2f, p)
        } else when (forma) {
            FormaAgujas.BASTON, FormaAgujas.FINA -> c.drawRoundRect(RectF(cx - W / 2, cy - L, cx + W / 2, cy + T), W / 2, W / 2, p)
            FormaAgujas.HOJA -> c.drawPath(Path().apply {
                moveTo(cx, cy - L); quadTo(cx + W * 1.1f, cy - L * 0.45f, cx, cy + T); quadTo(cx - W * 1.1f, cy - L * 0.45f, cx, cy - L); close()
            }, p)
        }
        c.restore()
    }

    /** La máscara de una aguja para el `AnalogClock` (apuntando a las 12, del tamaño de la esfera): solo su forma; el color lo pone el widget. */
    fun mascara(lado: Int, pieza: Pieza, forma: FormaAgujas): Bitmap {
        val b = Bitmap.createBitmap(lado, lado, Bitmap.Config.ALPHA_8)
        val c = Canvas(b)
        pinta(c, lado / 2f, lado / 2f, lado / 2f, 0f, pieza, forma, 0xFF000000.toInt())
        if (pieza == Pieza.MINUTO) pinta(c, lado / 2f, lado / 2f, lado / 2f, 0f, Pieza.CENTRO, forma, 0xFF000000.toInt())
        return b
    }
}

/** Los formatos de fecha y hora de `android.text.format.DateFormat`, en español, para dibujarlos en la app como los pondrá el widget. */
object Formatos {
    const val HORA = "H:mm"
    const val HORA_CERO = "HH:mm"
    const val HORA_12 = "h:mm"
    const val FECHA_LARGA = "EEEE d 'de' MMMM"
    const val FECHA_CORTA = "EEE d MMM"
    const val DIA_SEMANA = "EEEE"
    const val DIA_MES = "d 'de' MMMM"

    private val MESES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
    private val MESES_C = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")
    private val DIAS = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    private val DIAS_C = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
    fun mes(m: Int) = MESES[m - 1]
    fun dia(d: java.time.DayOfWeek) = DIAS[d.value - 1]
    fun diaCorto(d: java.time.DayOfWeek) = DIAS_C[d.value - 1]

    /** Lo más ancho que puede salir con ese formato (para medir el sitio del `TextClock`). */
    fun muestras(formato: String): List<String> {
        val horas = listOf("00:00", "23:59", "20:48", "8:08", "12:58", "10:00")
        if ("E" !in formato && "M" !in formato && "d" !in formato) return horas.map { formatea(formato, ZonedDateTime.of(2026, 1, 1, it.substringBefore(':').toInt(), it.substringAfter(':').toInt(), 0, 0, java.time.ZoneId.of("UTC"))) }
        // los días y los meses más largos
        val fechas = listOf(java.time.LocalDate.of(2026, 9, 30), java.time.LocalDate.of(2026, 9, 26), java.time.LocalDate.of(2026, 12, 30), java.time.LocalDate.of(2026, 2, 28), java.time.LocalDate.of(2026, 11, 25))
        return fechas.map { formatea(formato, it.atTime(23, 58).atZone(java.time.ZoneId.of("UTC"))) }
    }

    /** Como `DateFormat.format` de Android (lo que pinta el `TextClock`), con los nombres en español. */
    fun formatea(f: String, t: ZonedDateTime): String {
        val sb = StringBuilder(); var i = 0
        while (i < f.length) {
            val ch = f[i]
            if (ch == '\'') { val j = f.indexOf('\'', i + 1).let { if (it < 0) f.length else it }; sb.append(f, i + 1, j); i = j + 1; continue }
            var n = 1; while (i + n < f.length && f[i + n] == ch) n++
            when (ch) {
                'H' -> sb.append(if (n >= 2) "%02d".format(t.hour) else "${t.hour}")
                'h' -> { val x = (t.hour % 12).let { if (it == 0) 12 else it }; sb.append(if (n >= 2) "%02d".format(x) else "$x") }
                'm' -> sb.append("%02d".format(t.minute))
                's' -> sb.append("%02d".format(t.second))
                'a' -> sb.append(if (t.hour < 12) "a. m." else "p. m.")
                'd' -> sb.append(if (n >= 2) "%02d".format(t.dayOfMonth) else "${t.dayOfMonth}")
                'E' -> sb.append(if (n >= 4) dia(t.dayOfWeek) else diaCorto(t.dayOfWeek))
                'M', 'L' -> sb.append(when { n >= 4 -> MESES[t.monthValue - 1]; n == 3 -> MESES_C[t.monthValue - 1]; n == 2 -> "%02d".format(t.monthValue); else -> "${t.monthValue}" })
                'y' -> sb.append(if (n == 2) "%02d".format(t.year % 100) else "${t.year}")
                else -> repeat(n) { sb.append(ch) }
            }
            i += n
        }
        return sb.toString()
    }
}
