package com.gonzalo.ptah

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/*
 * Las composiciones pequeñas: 1×1 (~59 × 59 dp), de pie (1×2 y 1×3: ~59 × 120 y 59 × 178) y cuadrado mediano (2×2: ~120 × 120). Como las
 * cabeceras en papel de las hermanas: el marco interior fino (lo pone [Lienzo.terminar]), arriba la cabecera con el cuadradito del acento
 * y su antetítulo en versalitas, y la raya gruesa; luego lo de cada tipo, en «columna de periódico», con sus filetes finos, el número del
 * día o de la revista, la fecha en versalitas espaciadas y las cursivas de la familia para lo secundario. Lo que no cabe entero se quita
 * (lo menos importante primero), pero la cabecera y los filetes se quedan; y las partes que ocultas en el editor no salen.
 */
object Chico {
    private val ES = Locale("es", "ES")

    /** Dibuja [l] con la composición pequeña de su tipo. false: ese tipo no tiene (se usa la de siempre). */
    fun pinta(l: Lienzo, t: TipoWidget): Boolean {
        if (!l.chico) return false
        when (t.id) {
            RelojDigital.id -> digital(l)
            RelojAgujas.id -> agujas(l)
            Calendario.id -> calendario(l)
            Tiempo.id -> tiempo(l)
            TipoBateria.id -> bateria(l)
            CuentaAtras.id -> cuentaAtras(l)
            TipoNota.id -> nota(l)
            Buscador.id -> buscador(l)
            Accesos.id -> accesos(l)
            Foto.id -> foto(l)
            else -> return false
        }
        return true
    }

    // ================================================================ las piezas

    /** El sitio que va quedando, de arriba abajo y de abajo arriba. */
    private class Hueco(l: Lienzo) {
        /** Lo que se puede usar: en un widget redondo, el cuadrado inscrito en el círculo (para que nada toque la curva). */
        val a: RectF = if (l.redondo) {
            val lado = (min(l.w, l.h) - 2f * l.pad * 0.5f) / kotlin.math.sqrt(2f)
            RectF(l.w / 2 - lado / 2, l.h / 2 - lado / 2, l.w / 2 + lado / 2, l.h / 2 + lado / 2).apply { intersect(l.area) }
        } else l.area
        // En la columna de pie las esquinas son muy redondas: lo de arriba y lo de abajo, un poco hacia dentro para no rozar la curva
        // (y abajo en todos, que es donde lo de abajo se acerca a la curva del marco)
        private val curva = if (l.chico && !l.redondo) ((l.radio - l.pad) * 0.35f).coerceAtLeast(0f) else 0f
        var top = a.top + if (l.proporcion == Lienzo.Proporcion.VERTICAL || l.radio > min(l.w, l.h) * 0.4f) curva else 0f
        var bottom = a.bottom - curva
        val ancho get() = a.width()
        val alto get() = bottom - top
        fun rect(alto: Float = this.alto) = RectF(a.left, top, a.right, top + alto)
    }

    /** Redondo del todo (un círculo, o casi): ahí la cabecera iría contra la curva. */
    private val Lienzo.redondo get() = proporcion != Lienzo.Proporcion.VERTICAL && radio >= min(w, h) / 2f - 0.5f && kotlin.math.abs(w - h) < min(w, h) * 0.15f
    private val Lienzo.alto3 get() = proporcion == Lienzo.Proporcion.VERTICAL && h >= 150f
    private val Lienzo.vertical get() = proporcion == Lienzo.Proporcion.VERTICAL
    private val Lienzo.mediano get() = proporcion == Lienzo.Proporcion.MEDIANO
    private val Lienzo.ahoraL get() = datos.ahora()
    /** El tamaño de base de las versalitas: más pequeñas en lo estrecho, para que quepan enteras. */
    private val Lienzo.sv get() = if (area.width() < 60f) 6.8f else 8f

    /**
     * La cabecera: el cuadradito del acento, el antetítulo (el primero de [izq] que quepa entero) y, si queda sitio, lo de [der] a la
     * derecha; debajo, la raya gruesa. Si el diseño oculta el antetítulo, solo la raya.
     */
    private fun cabecera(l: Lienzo, h: Hueco, izq: List<String>, der: List<String> = emptyList()) {
        val s = l.sv * l.k
        val conAnte = l.e.ve("antetitulo") && l.e.ve("ante")
        if (conAnte) {
            val p = l.tp(l.fNegrita, s, l.tinta, 0.16f)
            val alto = p.fontMetrics.let { it.descent - it.ascent }
            val q = s * 0.82f
            l.c.drawRect(h.a.left, h.top + (alto - q) / 2, h.a.left + q, h.top + (alto + q) / 2, l.lleno(l.pieza("cuadro", l.acento)))
            val x = h.a.left + q + 4f
            val sitio = h.a.right - x
            val t = izq.map { it.uppercase(ES) }.firstOrNull { l.cabe(it, l.fNegrita, s, sitio, 0.16f) != null }
            var usado = 0f
            if (t != null) {
                val ss = l.cabe(t, l.fNegrita, s, sitio, 0.16f)!!
                val pp = l.tp(l.fNegrita, ss, l.pieza("ante", l.tinta), 0.16f)
                usado = pp.measureText(t)
                l.pon(l.capa(t, pp, usado + 2f, 1, 1f, Layout.Alignment.ALIGN_NORMAL), x, h.top + (alto - pp.fontMetrics.let { it.descent - it.ascent }) / 2)
            }
            val resto = sitio - usado - 8f
            der.map { it.uppercase(ES) }.firstOrNull { l.cabe(it, l.fNegrita, s, resto, 0.16f) != null }?.let { d ->
                val ss = l.cabe(d, l.fNegrita, s, resto, 0.16f)!!
                val pp = l.tp(l.fNegrita, ss, l.gris, 0.16f)
                l.pon(l.capa(d, pp, resto, 1, 1f, Layout.Alignment.ALIGN_OPPOSITE), h.a.right - resto, h.top + (alto - pp.fontMetrics.let { it.descent - it.ascent }) / 2)
            }
            h.top += alto + 2.5f
        }
        if (l.e.ve("rayas")) { l.filete(h.top, if (l.area.width() < 60f) 1.4f else 1.8f); h.top += 1.8f }
        h.top += 4f
    }

    /** Un filete fino a lo ancho, arriba del hueco. */
    private fun fino(l: Lienzo, h: Hueco, abajo: Boolean = false) {
        if (!l.e.ve("rayas")) { if (abajo) h.bottom -= 3f else h.top += 3f; return }
        if (abajo) { l.filete(h.bottom - 0.7f, 0.7f); h.bottom -= 0.7f + 4f } else { l.filete(h.top, 0.7f); h.top += 0.7f + 4f }
    }

    /** Una línea en versalitas (la primera de [t] que quepa entera), arriba o abajo. Devuelve si se ha puesto. */
    private fun versal(l: Lienzo, h: Hueco, t: List<String>, abajo: Boolean = false, color: Int = l.tinta, base: Float = l.sv, al: Layout.Alignment = l.alineacion): Boolean {
        val s = base * l.k
        val txt = t.map { it.uppercase(ES) }.firstOrNull { l.cabe(it, l.fNegrita, s, h.ancho, 0.16f) != null } ?: return false
        val ss = l.cabe(txt, l.fNegrita, s, h.ancho, 0.16f)!!
        val p = l.tp(l.fNegrita, ss, color, 0.16f)
        val alto = p.fontMetrics.let { it.descent - it.ascent }
        if (alto > h.alto * 0.4f) return false
        val y = if (abajo) h.bottom - alto else h.top
        l.pon(l.capa(txt, p, h.ancho + 1f, 1, 1f, al), h.a.left, y)
        if (abajo) h.bottom -= alto + 2f else h.top += alto + 2f
        return true
    }

    /** Una línea en cursiva (la primera de [t] que quepa entera). */
    private fun cursiva(l: Lienzo, h: Hueco, t: List<String>, abajo: Boolean = false, color: Int = l.gris, base: Float = 10.5f, al: Layout.Alignment = l.alineacion, lineas: Int = 1): Boolean {
        val s = base * l.k
        for (txt in t) {
            val p = l.tp(l.fCursiva, s, color)
            val lay = l.capa(txt, p, h.ancho, lineas, 1f, al)
            val entera = lay.getEllipsisCount(lay.lineCount - 1) == 0 && txt.split(' ').all { w -> p.measureText(w) <= h.ancho }
            if (!entera) {
                val ss = (if (lineas == 1) l.cabe(txt, l.fCursiva, s, h.ancho) else null) ?: continue
                val p2 = l.tp(l.fCursiva, ss, color)
                val lay2 = l.capa(txt, p2, h.ancho + 1f, 1, 1f, al)
                if (lay2.height > h.alto * 0.45f) continue
                l.pon(lay2, h.a.left, if (abajo) h.bottom - lay2.height else h.top)
                if (abajo) h.bottom -= lay2.height + 2f else h.top += lay2.height + 2f
                return true
            }
            if (lay.height > h.alto * 0.5f) continue
            l.pon(lay, h.a.left, if (abajo) h.bottom - lay.height else h.top)
            if (abajo) h.bottom -= lay.height + 2f else h.top += lay.height + 2f
            return true
        }
        return false
    }

    /** La hora (o lo que sea en formato de hora) tan grande como quepa en [r]: dibujada con la letra del diseño, o TextClock. */
    private fun hora(l: Lienzo, formato: String, r: RectF, color: Int, formato12: String = formato) {
        if (!l.dibujaReloj) { l.vivoQueLlena(formato, letraHora(l), r, color, formato12 = formato12); return }
        val f = if (formato12 != formato && !android.text.format.DateFormat.is24HourFormat(l.ctx)) formato12 else formato
        l.grande(Formatos.formatea(f, l.ahoraL), l.letraFija(letraHora(l)), r, color)
    }

    private fun nDia(l: Lienzo) = "Nº ${l.ahoraL.dayOfYear}"
    private fun diaCorto(l: Lienzo) = Formatos.diaCorto(l.ahoraL.dayOfWeek)
    private fun diaLargo(l: Lienzo) = Formatos.dia(l.ahoraL.dayOfWeek)
    private fun mes(l: Lienzo) = Formatos.mes(l.ahoraL.monthValue)
    private fun mesCorto(l: Lienzo) = Formatos.formatea("MMM", l.ahoraL)
    private fun semana(l: Lienzo) = l.ahoraL.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /** El color que se lee sobre el fondo: [c] si contrasta lo bastante; si no, la tinta. */
    private fun legible(l: Lienzo, c: Int) = if (Lienzo.contraste(c, l.papelDeVerdad()) >= 2.5f) c else l.tinta

    /** La tira de la semana (L M X J V S D) con hoy marcado en el acento. Devuelve el alto que ocupa. */
    private fun tiraSemana(l: Lienzo, h: Hueco, abajo: Boolean = true): Float {
        val lunes = l.d.si("lunes", true)
        val letras = if (lunes) listOf("L", "M", "X", "J", "V", "S", "D") else listOf("D", "L", "M", "X", "J", "V", "S")
        val hoy = l.ahoraL.dayOfWeek.value.let { if (lunes) it - 1 else it % 7 }
        // En lo estrecho, en dos filas (cuatro y tres) para que las letras se lean
        val porFila = if (h.ancho / 7f * 0.7f < 6f) 4 else 7
        val cw = h.ancho / porFila
        val s = min(cw * 0.7f, 8f * l.k)
        if (s < 4.5f) return 0f
        val alto = s * 1.5f
        val filas = (letras.size + porFila - 1) / porFila
        val total = filas * alto + (filas - 1) * 2f
        val y0 = if (abajo) h.bottom - total else h.top
        letras.forEachIndexed { i, t ->
            val fila = i / porFila; val col = i % porFila
            val enFila = min(porFila, letras.size - fila * porFila)
            val x0 = h.a.left + (h.ancho - enFila * cw) / 2
            val cx = x0 + col * cw + cw / 2
            val y = y0 + fila * (alto + 2f)
            val es = i == hoy
            if (es) l.c.drawRoundRect(RectF(cx - cw * 0.44f, y, cx + cw * 0.44f, y + alto), 1.5f, 1.5f, l.lleno(l.pieza("hoy", l.acento)))
            val p = l.tp(l.fNegrita, s, if (es) (if (Lienzo.luz(l.pieza("hoy", l.acento)) > 0.6f) 0xFF15120F.toInt() else 0xFFFFFFFF.toInt()) else l.gris).apply { textAlign = Paint.Align.CENTER }
            val fm = p.fontMetrics
            l.c.drawText(t, cx, y + alto / 2 - (fm.ascent + fm.descent) / 2, p)
        }
        if (abajo) h.bottom -= total + 3f else h.top += total + 3f
        return total
    }

    // ================================================================ los tipos

    /** El reloj digital. */
    private fun digital(l: Lienzo) {
        val h = Hueco(l); val e = l.e
        val (fmt, fmt12) = formatoHora(l.d)
        val colHora = l.pieza("hora", l.tinta)
        val ante = l.d.texto("ante").ifBlank { "La hora" }
        when {
            l.vertical -> {
                // Columna de periódico: «Nº 312 · DOM» y la raya; las horas y los minutos enormes, con un filete fino entre ellos; el día en
                // versalitas, la fecha en cursiva, la raya fina y el pie
                cabecera(l, h, listOf("${nDia(l)} · ${diaCorto(l)}", nDia(l), diaCorto(l)))
                if (e.ve("fecha")) {
                    if (l.alto3) versal(l, h, listOf("Semana ${semana(l)}", "Sem. ${semana(l)}"), abajo = true, color = l.gris)
                    if (l.alto3) {
                        fino(l, h, abajo = true)
                        cursiva(l, h, listOf("${l.ahoraL.dayOfMonth} de ${mes(l)}", "${l.ahoraL.dayOfMonth} ${mesCorto(l)}"), abajo = true, color = l.pieza("fecha", l.gris))
                    }
                    val dia = versal(l, h, if (l.alto3) listOf(diaLargo(l), diaCorto(l)) else listOf("${diaCorto(l)} ${l.ahoraL.dayOfMonth}", "${l.ahoraL.dayOfMonth}"), abajo = true, color = l.pieza("fecha", l.tinta))
                    if (!l.alto3 && dia) fino(l, h, abajo = true)
                }
                val horas = if (l.d.texto("formato") == "12") "hh" else "HH"
                val fila = (h.alto - 5f) / 2
                hora(l, horas, RectF(h.a.left, h.top, h.a.right, h.top + fila), colHora)
                if (e.ve("rayas")) l.filete(h.top + fila + 2f, 0.7f, x0 = h.a.left + h.ancho * 0.25f, x1 = h.a.right - h.ancho * 0.25f)
                hora(l, "mm", RectF(h.a.left, h.bottom - fila, h.a.right, h.bottom), l.pieza("minutos", legible(l, l.acento)))
            }
            l.mediano -> {
                cabecera(l, h, listOf(ante, "Hora"), listOf(nDia(l)))
                if (e.ve("fecha")) {
                    cursiva(l, h, listOf("${l.ahoraL.dayOfMonth} de ${mes(l)}", "${l.ahoraL.dayOfMonth} ${mesCorto(l)}"), abajo = true, color = l.pieza("fecha", l.gris))
                    versal(l, h, listOf(diaLargo(l), diaCorto(l)), abajo = true, color = l.pieza("fecha", l.tinta))
                    fino(l, h, abajo = true)
                }
                hora(l, fmt, h.rect(), colHora, fmt12)
            }
            else -> {
                // 1×1: «■ SÁB 7» y la raya gruesa (la fecha hace de antetítulo, como en las cabeceras de las hermanas), la hora a todo lo
                // ancho y la raya fina al pie; sin la fecha, «■ LA HORA»
                cabecera(l, h, if (e.ve("fecha")) listOf("${diaCorto(l)} ${l.ahoraL.dayOfMonth}", "${l.ahoraL.dayOfMonth}") else listOf(ante, "Hora"))
                fino(l, h, abajo = true)
                hora(l, fmt, h.rect(), colHora, fmt12)
            }
        }
    }

    /** El reloj de agujas. */
    private fun agujas(l: Lienzo) {
        val h = Hueco(l); val e = l.e; val d = l.d
        val forma = FormaAgujas.entries.firstOrNull { it.name == d.texto("forma") } ?: FormaAgujas.HOJA
        fun esfera(lado: Float, cx: Float, top: Float) {
            val r = RectF(cx - lado / 2, top, cx + lado / 2, top + lado)
            RelojAgujas.esfera(l, r)
            l.agujas(r, forma, l.pieza("horaria", l.tinta), l.pieza("minutera", l.tinta), if (d.si("segundero")) l.pieza("segundero", l.acento) else null, l.pieza("minutera", l.tinta))
        }
        when {
            l.redondo -> {
                // Un reloj redondo: la esfera a todo el círculo y, como la ventanita de la fecha de un reloj de verdad, «SÁB 7» bajo el centro
                val lado = min(l.w, l.h) - 2f * l.pad
                val top = (l.h - lado) / 2
                if (e.ve("antetitulo") && e.ve("ante") && e.ve("fecha") && lado >= 46f) {
                    val t = "${diaCorto(l)} ${l.ahoraL.dayOfMonth}".uppercase(ES)
                    val ancho = lado * 0.4f
                    val ss = l.cabe(t, l.fNegrita, lado * 0.075f, ancho, 0.16f)
                    RelojAgujas.esfera(l, RectF(l.w / 2 - lado / 2, top, l.w / 2 + lado / 2, top + lado))
                    if (ss != null) {
                        val lay = l.capa(t, l.tp(l.fNegrita, ss, l.pieza("fecha", l.gris), 0.16f), ancho + 1f, 1, 1f, Layout.Alignment.ALIGN_CENTER)
                        l.pon(lay, l.w / 2 - ancho / 2, top + lado * 0.68f - lay.height / 2)
                    }
                    val r = RectF(l.w / 2 - lado / 2, top, l.w / 2 + lado / 2, top + lado)
                    l.agujas(r, forma, l.pieza("horaria", l.tinta), l.pieza("minutera", l.tinta), if (d.si("segundero")) l.pieza("segundero", l.acento) else null, l.pieza("minutera", l.tinta))
                } else esfera(lado, l.w / 2, top)
            }
            l.vertical -> {
                cabecera(l, h, listOf("${nDia(l)} · ${diaCorto(l)}", nDia(l), diaCorto(l)))
                val lado = min(h.ancho, h.alto * 0.5f)
                esfera(lado, h.a.centerX(), h.top)
                h.top += lado + 4f
                if (!e.ve("fecha")) return
                fino(l, h)
                if (l.alto3) { versal(l, h, listOf(diaLargo(l), diaCorto(l)), abajo = true, color = l.gris, al = Layout.Alignment.ALIGN_CENTER); fino(l, h, abajo = true) }
                cursiva(l, h, listOf(mes(l), mesCorto(l)), abajo = true, color = l.pieza("fecha", l.gris), al = Layout.Alignment.ALIGN_CENTER)
                l.grande("${l.ahoraL.dayOfMonth}", l.fTitular, h.rect(), l.pieza("fecha", l.tinta), Layout.Alignment.ALIGN_CENTER)
            }
            l.mediano -> {
                cabecera(l, h, listOf(d.texto("ante").ifBlank { "La hora" }, "Hora"), listOf("${diaCorto(l)} ${l.ahoraL.dayOfMonth}", "${l.ahoraL.dayOfMonth}"))
                val lado = min(h.ancho, h.alto)
                esfera(lado, h.a.centerX(), h.top + (h.alto - lado) / 2)
            }
            else -> {
                cabecera(l, h, listOf("${diaCorto(l)} ${l.ahoraL.dayOfMonth}", diaCorto(l), "Hoy"))
                val lado = min(h.ancho, h.alto)
                esfera(lado, h.a.centerX(), h.top + (h.alto - lado) / 2)
            }
        }
    }

    /** El calendario. */
    private fun calendario(l: Lienzo) {
        val h = Hueco(l); val e = l.e
        val hoy = l.ahoraL
        val colNum = l.pieza("numeros", l.tinta)
        when {
            l.vertical -> {
                cabecera(l, h, listOf("Hoy"), listOf(nDia(l)))
                versal(l, h, listOf(diaLargo(l), diaCorto(l)), color = l.pieza("hoy", l.acento))
                if (l.alto3 && e.ve("semana") && tiraSemana(l, h) > 0f) fino(l, h, abajo = true)
                if (l.alto3 && e.ve("anio")) versal(l, h, listOf("${hoy.year}"), abajo = true, color = l.gris)
                cursiva(l, h, listOf(mes(l), mesCorto(l)), abajo = true, color = l.gris, base = 12f)
                l.grande("${hoy.dayOfMonth}", l.fTitular, h.rect(), colNum)
            }
            l.mediano -> {
                cabecera(l, h, listOf("Hoy"), listOf(nDia(l)))
                if (e.ve("semana") && tiraSemana(l, h) > 0f) fino(l, h, abajo = true)
                // El número a la izquierda; a su lado, el día, el mes en cursiva y el año
                val izq = RectF(h.a.left, h.top, h.a.left + h.ancho * 0.5f, h.bottom)
                l.grande("${hoy.dayOfMonth}", l.fTitular, izq, colNum)
                val col = RectF(izq.right + 6f, h.top, h.a.right, h.bottom)
                val sub = object { var y = col.top + col.height() * 0.15f }
                fun linea(t: String, f: android.graphics.Typeface, s: Float, color: Int, track: Float) {
                    val ss = l.cabe(t, f, s * l.k, col.width(), track) ?: return
                    val p = l.tp(f, ss, color, track)
                    sub.y += l.pon(l.capa(t, p, col.width() + 1f, 1, 1f, Layout.Alignment.ALIGN_NORMAL), col.left, sub.y) + 3f
                }
                linea(diaLargo(l).uppercase(ES).let { if (l.cabe(it, l.fNegrita, 8f * l.k, col.width(), 0.16f) != null) it else diaCorto(l).uppercase(ES) }, l.fNegrita, 8f, l.pieza("hoy", l.acento), 0.16f)
                linea(mes(l), l.fCursiva, 12f, l.gris, 0f)
                if (e.ve("anio")) linea("${hoy.year}", l.fNegrita, 8f, l.gris, 0.16f)
            }
            else -> {
                // 1×1: «■ HOY  Nº 312», la raya, el número grande y a su lado el día y el mes
                cabecera(l, h, listOf("Hoy"), listOf(nDia(l)))
                val izq = RectF(h.a.left, h.top, h.a.left + h.ancho * 0.52f, h.bottom)
                l.grande("${hoy.dayOfMonth}", l.fTitular, izq, colNum)
                val col = RectF(izq.right + 3f, h.top, h.a.right, h.bottom)
                val s1 = l.cabe(diaCorto(l).uppercase(ES), l.fNegrita, l.sv * l.k, col.width(), 0.16f)
                val s2 = l.cabe(mesCorto(l), l.fCursiva, 11f * l.k, col.width())
                if (s1 != null && s2 != null) {
                    val a = l.capa(diaCorto(l).uppercase(ES), l.tp(l.fNegrita, s1, l.pieza("hoy", l.acento), 0.16f), col.width() + 1f, 1, 1f, Layout.Alignment.ALIGN_NORMAL)
                    val b = l.capa(mesCorto(l), l.tp(l.fCursiva, s2, l.gris), col.width() + 1f, 1, 1f, Layout.Alignment.ALIGN_NORMAL)
                    val y = col.centerY() - (a.height + b.height + 1f) / 2
                    l.pon(a, col.left, y); l.pon(b, col.left, y + a.height + 1f)
                }
            }
        }
    }

    /** El tiempo. */
    private fun tiempo(l: Lienzo) {
        val h = Hueco(l); val e = l.e; val d = l.d
        val p = l.datos.tiempo(d)
        val sitio = Tiempo.nombreSitio(d)
        cabecera(l, h, if (e.ve("lugar")) listOf(sitio, "Tiempo") else listOf("El tiempo", "Tiempo"), if (l.mediano && p != null) listOf(Formatos.formatea("HH:mm", p.hora.atZone(java.time.ZoneId.systemDefault()))) else emptyList())
        // (la atribución, siempre, pequeña: la pide la licencia de los datos)
        // centrada y sin llegar a los lados, que abajo el marco se curva
        val anchoAtt = h.ancho * 0.8f
        val att = listOf(OpenMeteo.ATRIBUCION, "Open-Meteo").firstOrNull { l.cabe(it.uppercase(ES), l.fNegrita, 5.2f, anchoAtt, 0.16f) != null }
        if (att != null) { l.ante(att, h.a.left + (h.ancho - anchoAtt) / 2, h.bottom - 5.5f, anchoAtt, Lienzo.conAlfa(l.gris, 200), 5.2f / l.k, Layout.Alignment.ALIGN_CENTER); h.bottom -= 7f }
        if (p == null) { cursiva(l, h, listOf("Sin datos todavía", "Sin datos"), color = l.gris, lineas = 3); return }
        val est = Wmo.de(p.codigo)
        val colTemp = l.pieza("temp", l.tinta); val colIco = l.pieza("icono", l.tinta); val colSol = l.pieza("sol", l.acento)
        val hoy = p.dias.firstOrNull { it.fecha == p.hora.toLocalDate() } ?: p.dias.firstOrNull()
        fun icono(r: RectF) = IconosTiempo.pinta(l.c, est.icono, r, colIco, colSol, l.papelDeVerdad(), !p.esDia)
        when {
            l.vertical -> {
                if (l.alto3 && e.ve("dias")) {
                    // Los dos próximos días, en mini
                    val luego = p.dias.filter { it.fecha.isAfter(p.hora.toLocalDate()) }.take(2)
                    val fila = 12f * l.k
                    for (dia in luego.reversed()) {
                        if (h.alto < fila * 6) break
                        val y = h.bottom - fila
                        val txt = Formatos.diaCorto(dia.fecha.dayOfWeek).uppercase(ES)
                        l.ante(txt, h.a.left, y + 2f, h.ancho * 0.45f, l.pieza("dias", l.tinta), 6.5f, Layout.Alignment.ALIGN_NORMAL)
                        IconosTiempo.pinta(l.c, Wmo.de(dia.codigo).icono, RectF(h.a.left + h.ancho * 0.42f, y, h.a.left + h.ancho * 0.42f + fila, y + fila), colIco, colSol, l.papelDeVerdad(), false)
                        l.pon(l.capa(grados(dia.max), l.tp(l.fTexto, 7.5f * l.k, l.pieza("dias", l.tinta)), h.ancho * 0.4f, 1, 1f, Layout.Alignment.ALIGN_OPPOSITE), h.a.right - h.ancho * 0.4f, y + 2f)
                        h.bottom -= fila + 2f
                    }
                    fino(l, h, abajo = true)
                }
                if (e.ve("maxmin") && hoy != null) { cursiva(l, h, listOf("${grados(hoy.max)} / ${grados(hoy.min)}", grados(hoy.max)), abajo = true, color = l.gris, al = Layout.Alignment.ALIGN_CENTER); fino(l, h, abajo = true) }
                val ico = min(h.ancho * 0.85f, h.alto * 0.48f)
                icono(RectF(h.a.centerX() - ico / 2, h.top, h.a.centerX() + ico / 2, h.top + ico))
                h.top += ico + 2f
                l.grande(grados(p.temp), l.fTitular, h.rect(), colTemp, Layout.Alignment.ALIGN_CENTER)
            }
            l.mediano -> {
                if (e.ve("maxmin") && hoy != null) versal(l, h, listOf("Máx ${grados(hoy.max)} · Mín ${grados(hoy.min)}", "${grados(hoy.max)} / ${grados(hoy.min)}"), abajo = true, color = l.gris)
                if (e.ve("estado")) cursiva(l, h, listOf(est.texto), abajo = true, color = l.tinta)
                fino(l, h, abajo = true)
                val ico = min(h.alto, h.ancho * 0.45f)
                icono(RectF(h.a.right - ico, h.top + (h.alto - ico) / 2, h.a.right, h.top + (h.alto + ico) / 2))
                l.grande(grados(p.temp), l.fTitular, RectF(h.a.left, h.top, h.a.right - ico - 4f, h.bottom), colTemp, Layout.Alignment.ALIGN_NORMAL)
            }
            else -> {
                val ico = min(h.alto, h.ancho * 0.46f)
                icono(RectF(h.a.left, h.top + (h.alto - ico) / 2, h.a.left + ico, h.top + (h.alto + ico) / 2))
                l.grande(grados(p.temp), l.fTitular, RectF(h.a.left + ico + 2f, h.top, h.a.right, h.bottom), colTemp, Layout.Alignment.ALIGN_OPPOSITE)
            }
        }
    }

    /** La batería: la pila con sus rayitas de nivel y el porcentaje. */
    private fun bateria(l: Lienzo) {
        val h = Hueco(l); val e = l.e
        val b = l.datos.bateria() ?: Bateria(100, false)
        val colNivel = if (b.nivel <= 20 && !b.cargando) l.pieza("bajo", 0xFFC8372D.toInt()) else l.pieza("nivel", l.acento)
        val ante = l.d.texto("ante").ifBlank { "Batería" }
        cabecera(l, h, listOf(ante, "Pila"), if (l.mediano && b.cargando) listOf("Cargando") else emptyList())
        val estado = if (b.cargando) "cargando" else if (b.nivel <= 20) "queda poca" else "en uso"
        fun pila(r: RectF, vertical: Boolean) {
            val g = max(1f, min(r.width(), r.height()) * 0.07f)
            val col = l.pieza("barra", l.tinta)
            val nub = (if (vertical) r.width() else r.height()) * 0.34f
            val cuerpo = if (vertical) RectF(r.left, r.top + nub * 0.5f, r.right, r.bottom) else RectF(r.left, r.top, r.right - nub * 0.5f, r.bottom)
            if (vertical) l.c.drawRoundRect(RectF(r.centerX() - nub / 2, r.top, r.centerX() + nub / 2, cuerpo.top + 1f), 1f, 1f, l.lleno(col))
            else l.c.drawRoundRect(RectF(cuerpo.right - 1f, r.centerY() - nub / 2, r.right, r.centerY() + nub / 2), 1f, 1f, l.lleno(col))
            val rad = min(cuerpo.width(), cuerpo.height()) * 0.14f
            l.c.drawRoundRect(RectF(cuerpo.left + g / 2, cuerpo.top + g / 2, cuerpo.right - g / 2, cuerpo.bottom - g / 2), rad, rad, l.trazo(col, g))
            val dentro = RectF(cuerpo.left + g * 2, cuerpo.top + g * 2, cuerpo.right - g * 2, cuerpo.bottom - g * 2)
            val lleno = if (vertical) RectF(dentro.left, dentro.bottom - dentro.height() * b.nivel / 100f, dentro.right, dentro.bottom)
                else RectF(dentro.left, dentro.top, dentro.left + dentro.width() * b.nivel / 100f, dentro.bottom)
            l.c.drawRect(lleno, l.lleno(colNivel))
            // Las rayitas del nivel (cada 25 %)
            if (e.ve("barra")) for (k in 1..3) {
                if (vertical) { val y = dentro.bottom - dentro.height() * k / 4f; l.c.drawLine(dentro.left, y, dentro.left + dentro.width() * 0.3f, y, l.trazo(col, 0.6f)); l.c.drawLine(dentro.right - dentro.width() * 0.3f, y, dentro.right, y, l.trazo(col, 0.6f)) }
                else { val x = dentro.left + dentro.width() * k / 4f; l.c.drawLine(x, dentro.top, x, dentro.top + dentro.height() * 0.3f, l.trazo(col, 0.6f)); l.c.drawLine(x, dentro.bottom - dentro.height() * 0.3f, x, dentro.bottom, l.trazo(col, 0.6f)) }
            }
            if (b.cargando) {
                val cx = dentro.centerX(); val cy = dentro.centerY(); val hh = min(dentro.height(), dentro.width()) * 0.38f
                l.c.drawPath(Path().apply {
                    moveTo(cx + hh * 0.25f, cy - hh); lineTo(cx - hh * 0.45f, cy + hh * 0.1f); lineTo(cx, cy + hh * 0.1f)
                    lineTo(cx - hh * 0.25f, cy + hh); lineTo(cx + hh * 0.45f, cy - hh * 0.1f); lineTo(cx, cy - hh * 0.1f); close()
                }, l.lleno(l.tinta))
            }
        }
        val pct = "${b.nivel} %"
        when {
            l.vertical -> {
                if (e.ve("estado")) cursiva(l, h, listOf(estado), abajo = true, color = l.gris, al = Layout.Alignment.ALIGN_CENTER)
                val numH = min(h.alto * 0.25f, h.ancho * 0.55f)
                l.grande(pct, l.fTitular, RectF(h.a.left, h.bottom - numH, h.a.right, h.bottom), l.pieza("cifra", l.tinta), Layout.Alignment.ALIGN_CENTER)
                h.bottom -= numH + 3f
                fino(l, h, abajo = true)
                val pw = min(h.ancho * 0.6f, h.alto * 0.5f)
                pila(RectF(h.a.centerX() - pw / 2, h.top, h.a.centerX() + pw / 2, h.bottom), true)
            }
            l.mediano -> {
                if (e.ve("estado")) cursiva(l, h, listOf(estado), abajo = true, color = l.gris)
                fino(l, h, abajo = true)
                val pw = min(h.ancho * 0.3f, h.alto * 0.55f)
                pila(RectF(h.a.right - pw, h.top + 2f, h.a.right, h.bottom - 2f), true)
                l.grande(pct, l.fTitular, RectF(h.a.left, h.top, h.a.right - pw - 6f, h.bottom), l.pieza("cifra", l.tinta), Layout.Alignment.ALIGN_NORMAL)
            }
            else -> {
                val bh = min(h.alto * 0.3f, 12f)
                pila(RectF(h.a.left, h.bottom - bh, h.a.right, h.bottom), false)
                h.bottom -= bh + 3f
                l.grande(pct, l.fTitular, h.rect(), l.pieza("cifra", l.tinta))
            }
        }
    }

    /** La cuenta atrás. */
    private fun cuentaAtras(l: Lienzo) {
        val h = Hueco(l); val e = l.e; val d = l.d
        val f = CuentaAtras.fecha(d)
        val c = CuentaAtras.cuenta(l.ahoraL.toLocalDate(), f)
        val titulo = d.texto("titulo")
        val fechaCorta = Formato.fecha(f).substringBeforeLast(' ')
        cabecera(l, h, listOf(c.ante), if (l.mediano && e.ve("fecha")) listOf(fechaCorta) else emptyList())
        val frase = listOf(c.unidad, if (e.ve("titulo") && titulo.isNotBlank()) (if (c.pasada) "desde " else if (c.dias == 0L) "" else "para ") + titulo else "").filter { it.isNotBlank() }.joinToString(" ")
        if (l.vertical && e.ve("fecha")) { versal(l, h, listOf(fechaCorta), abajo = true, color = l.pieza("fecha", l.gris)); fino(l, h, abajo = true) }
        // En la columna alta, debajo de la cabecera, cuántas semanas son (lo que da de sí el hueco)
        if (l.alto3 && !c.pasada && c.unidad.startsWith("día") && c.dias >= 14) {
            val sem = (c.dias + 3) / 7
            if (versal(l, h, listOf("Unas $sem semanas", "$sem semanas", "$sem sem."), color = l.gris)) fino(l, h)
        }
        if (frase.isNotBlank()) {
            val ok = cursiva(l, h, listOf(frase), abajo = true, color = l.pieza("titulo", l.tinta), lineas = if (l.alto3) 4 else 2) ||
                cursiva(l, h, listOf(c.unidad), abajo = true, color = l.pieza("titulo", l.tinta))
            if (ok && !l.proporcion.equals(Lienzo.Proporcion.PEQUENO)) fino(l, h, abajo = true)
        }
        l.grande(if (c.dias == 0L) "¡Hoy!" else Formato.cifra(c.dias), l.fTitular, h.rect(), l.pieza("cifra", legible(l, l.acento)))
    }

    /** La nota. */
    private fun nota(l: Lienzo) {
        val h = Hueco(l); val e = l.e; val d = l.d
        cabecera(l, h, listOf(d.texto("ante").ifBlank { "Nota" }, "Nota"))
        val titulo = d.texto("titulo"); val texto = d.texto("texto")
        if (e.ve("titulo") && titulo.isNotBlank()) {
            val pequena = l.proporcion == Lienzo.Proporcion.PEQUENO
            // (en la de 1×1, el título en una línea y debajo, aunque sea una, la primera línea de la nota)
            val maxAlto = if (texto.isBlank()) h.alto else h.alto * (if (l.vertical) 0.42f else if (pequena) 0.45f else 0.55f)
            val t = l.titular(titulo, h.ancho, maxAlto, 40f, 7f, if (l.vertical) 6 else if (pequena && texto.isNotBlank()) 1 else 3, l.pieza("titulo", l.tinta))
            h.top += l.pon(t, h.a.left, h.top) + 2f
            if (texto.isNotBlank() && h.alto > 13f && !pequena) fino(l, h)
        }
        if (texto.isNotBlank() && h.alto > 8f) {
            val p = l.tpk(l.fCursiva, if (l.proporcion == Lienzo.Proporcion.PEQUENO) 8f else if (h.ancho < 60f) 9f else 11f, l.pieza("texto", l.gris))
            val alto = p.fontMetrics.let { it.descent - it.ascent }
            val n = ((h.alto + 2f) / alto).toInt()  // (lo de abajo del todo es sitio de sobra: el marco va más allá)
            if (n >= 1) l.pon(l.capa(texto, p, h.ancho, n, 1f), h.a.left, h.top)
        }
    }

    /** El buscador. */
    private fun buscador(l: Lienzo) {
        val h = Hueco(l); val d = l.d
        cabecera(l, h, listOf(d.texto("ante").ifBlank { "Buscar" }, "Buscar"))
        val colIco = l.pieza("lupa", l.tinta)
        val dentro = l.pieza("dentro", 0)
        val barra = l.pieza("barra", l.tinta)
        // Al pie, en cursiva, la pregunta de siempre (si cabe)
        if (l.vertical || l.mediano) {
            if (cursiva(l, h, listOf("¿Qué buscamos?", "¿Qué buscas?", "¿Qué?"), abajo = true, color = l.pieza("pista", l.gris), al = Layout.Alignment.ALIGN_CENTER)) fino(l, h, abajo = true)
        }
        when {
            l.vertical || l.mediano && false -> {
                val voz = d.si("voz", true)
                val ancho = min(h.ancho, h.alto * 0.48f)
                val alto = if (voz) min(h.alto, ancho * 2.3f) else ancho
                val r = RectF(h.a.centerX() - ancho / 2, h.top + (h.alto - alto) / 2, h.a.centerX() + ancho / 2, h.top + (h.alto + alto) / 2)
                if (dentro != 0) l.c.drawRoundRect(r, ancho / 2, ancho / 2, l.lleno(dentro))
                l.c.drawRoundRect(RectF(r.left + 0.7f, r.top + 0.7f, r.right - 0.7f, r.bottom - 0.7f), ancho / 2, ancho / 2, l.trazo(barra, 1.3f))
                if (!voz) { Buscador.lupa(l, r.centerX(), r.centerY(), ancho * 0.55f, colIco); return }
                val mitad = r.height() / 2
                Buscador.lupa(l, r.centerX(), r.top + mitad / 2 + 1f, ancho * 0.55f, colIco)
                l.c.drawLine(r.left + ancho * 0.25f, r.centerY(), r.right - ancho * 0.25f, r.centerY(), l.trazo(Lienzo.conAlfa(barra, 110), 0.7f))
                Buscador.micro(l, r.centerX(), r.bottom - mitad / 2, ancho * 0.58f, colIco)
                l.toque(RectF(r.left, r.centerY(), r.right, r.bottom), Accion.BuscarVoz, "Buscar hablando")
            }
            l.mediano -> {
                // Una barra de pie a lo ancho, con la lupa y «Buscar», y el micrófono
                val bh = min(h.alto * 0.5f, 40f)
                val r = RectF(h.a.left, h.top + (h.alto - bh) / 2, h.a.right, h.top + (h.alto + bh) / 2)
                if (dentro != 0) l.c.drawRoundRect(r, bh / 2, bh / 2, l.lleno(dentro))
                l.c.drawRoundRect(RectF(r.left + 0.7f, r.top + 0.7f, r.right - 0.7f, r.bottom - 0.7f), bh / 2, bh / 2, l.trazo(barra, 1.3f))
                Buscador.lupa(l, r.left + bh * 0.55f, r.centerY(), bh * 0.5f, colIco)
                val voz = d.si("voz", true)
                if (voz) { Buscador.micro(l, r.right - bh * 0.55f, r.centerY(), bh * 0.55f, colIco); l.toque(RectF(r.right - bh * 1.1f, r.top, r.right, r.bottom), Accion.BuscarVoz, "Buscar hablando") }
                val x = r.left + bh * 1.05f
                val fin = r.right - (if (voz) bh * 1.05f else bh * 0.4f)
                val t = listOf("Buscar", "…").firstOrNull { l.cabe(it, l.fCursiva, bh * 0.36f, fin - x) != null }
                if (t != null) { val hh = l.altoLinea(t, l.fCursiva, bh * 0.36f / l.k, fin - x); l.linea(t, l.fCursiva, bh * 0.36f / l.k, l.pieza("pista", l.gris), x, r.centerY() - hh / 2, fin - x, Layout.Alignment.ALIGN_NORMAL) }
            }
            else -> {
                val lado = min(h.ancho, h.alto)
                val r = RectF(h.a.centerX() - lado / 2, h.top + (h.alto - lado) / 2, h.a.centerX() + lado / 2, h.top + (h.alto + lado) / 2)
                if (dentro != 0) l.c.drawOval(r, l.lleno(dentro))
                l.c.drawOval(RectF(r.left + 0.7f, r.top + 0.7f, r.right - 0.7f, r.bottom - 0.7f), l.trazo(barra, 1.3f))
                Buscador.lupa(l, r.centerX(), r.centerY(), lado * 0.55f, colIco)
            }
        }
    }

    /** Los accesos: los iconos (en rejilla, o en columna con filetes finos), y sus nombres si caben. */
    private fun accesos(l: Lienzo) {
        val h = Hueco(l); val d = l.d
        val lista = Accesos.accesos(d)
        cabecera(l, h, listOf(d.texto("ante").ifBlank { "A mano" }, "Apps"))
        if (lista.isEmpty()) { cursiva(l, h, listOf("Elige tus apps", "Elige"), color = l.gris, lineas = 3); return }
        val n = lista.size
        if (l.vertical) {
            val fila = h.alto / n
            val ico = min(h.ancho * 0.7f, fila - 6f).coerceAtLeast(8f)
            lista.forEachIndexed { i, acc ->
                val cy = h.top + i * fila + fila / 2
                Accesos.icono(l, acc, RectF(h.a.centerX() - ico / 2, cy - ico / 2, h.a.centerX() + ico / 2, cy + ico / 2))
                if (i < n - 1 && l.e.ve("rayas")) l.filete(h.top + (i + 1) * fila - 0.3f, 0.6f, Lienzo.conAlfa(l.tinta, 110), h.a.left + h.ancho * 0.15f, h.a.right - h.ancho * 0.15f)
                l.toque(RectF(h.a.left, h.top + i * fila, h.a.right, h.top + (i + 1) * fila), Accesos.accion(acc), "Abrir ${Accesos.nombreDe(l, acc)}")
            }
            return
        }
        // En la de 1×1, una fila con las tres primeras (lo de detrás es lo que menos importa): así se ven de un tamaño que se pueda tocar
        if (l.proporcion == Lienzo.Proporcion.PEQUENO && n > 1) {
            val vistas = lista.take(3)
            val cw = h.ancho / vistas.size
            val ico = min(cw * 0.86f, h.alto * 0.85f)
            vistas.forEachIndexed { i, acc ->
                val cx = h.a.left + i * cw + cw / 2; val y0 = h.top + (h.alto - ico) / 2
                Accesos.icono(l, acc, RectF(cx - ico / 2, y0, cx + ico / 2, y0 + ico))
                l.toque(RectF(cx - cw / 2, h.top, cx + cw / 2, h.bottom), Accesos.accion(acc), "Abrir ${Accesos.nombreDe(l, acc)}")
            }
            return
        }
        val cols = kotlin.math.ceil(kotlin.math.sqrt(n.toDouble())).toInt()
        val filas = kotlin.math.ceil(n / cols.toFloat()).toInt()
        val cw = h.ancho / cols; val ch = h.alto / filas
        // Los nombres, si caben todos enteros debajo de su icono (en el mediano)
        val sN = 6.2f * l.k
        val nombres = l.mediano && lista.all { l.cabe(Accesos.nombreDe(l, it).uppercase(ES), l.fNegrita, sN, cw - 2f, 0.1f) != null } && ch > 30f
        val eh = if (nombres) sN * 1.4f else 0f
        val ico = (min(cw, ch - eh) * 0.82f).coerceAtLeast(8f)
        lista.forEachIndexed { i, acc ->
            // (la última fila, si va incompleta, centrada)
            val enFila = min(cols, n - (i / cols) * cols)
            val cx = h.a.left + (h.ancho - enFila * cw) / 2 + (i % cols) * cw + cw / 2; val y0 = h.top + (i / cols) * ch + (ch - ico - eh) / 2
            Accesos.icono(l, acc, RectF(cx - ico / 2, y0, cx + ico / 2, y0 + ico))
            if (nombres) {
                val t = Accesos.nombreDe(l, acc).uppercase(ES)
                val ss = l.cabe(t, l.fNegrita, sN, cw - 2f, 0.1f)!!
                l.pon(l.capa(t, l.tp(l.fNegrita, ss, l.pieza("nombres", l.tinta), 0.1f), cw, 1, 1f, Layout.Alignment.ALIGN_CENTER), cx - cw / 2, y0 + ico + 2f)
            }
            l.toque(RectF(cx - cw / 2, h.top + (i / cols) * ch, cx + cw / 2, h.top + (i / cols + 1) * ch), Accesos.accion(acc), "Abrir ${Accesos.nombreDe(l, acc)}")
        }
    }

    /** La foto, con su marco de revista: la cabecera, la foto, el filete fino y el pie en cursiva (si cabe entero). */
    private fun foto(l: Lienzo) {
        val h = Hueco(l); val d = l.d; val e = l.e
        val pie = d.texto("pie")
        cabecera(l, h, listOf(pie.takeIf { it.isNotBlank() && it.length <= 14 } ?: "Foto", "Foto"))
        val conPie = e.ve("pie") && pie.isNotBlank() && !l.proporcion.equals(Lienzo.Proporcion.PEQUENO) &&
            cursiva(l, h, listOf(pie), abajo = true, color = l.pieza("pie", l.tinta), base = 9.5f, lineas = if (l.alto3) 3 else 2)
        if (conPie) fino(l, h, abajo = true)
        else if (l.vertical || l.mediano) {
            // Sin pie, la fecha como en el pie de foto de una revista
            val hoy = l.ahoraL
            if (versal(l, h, listOf("${diaCorto(l)} ${hoy.dayOfMonth} ${mesCorto(l)}", "${hoy.dayOfMonth} ${mesCorto(l)}"), abajo = true, color = l.gris)) fino(l, h, abajo = true)
        }
        val r = h.rect()
        val img = l.datos.foto(d.id)
        val rad = 2f
        if (img != null) {
            l.foto(img, r, rad)
            l.c.drawRoundRect(r, rad, rad, l.trazo(l.pieza("filo", Lienzo.conAlfa(l.tinta, 70)), 0.6f))
        } else {
            l.c.drawRoundRect(r, rad, rad, l.lleno(Lienzo.mezcla(l.papelDeVerdad(), l.tinta, 0.08f)))
            val hh = Hueco(l).apply { top = r.top + r.height() * 0.3f; bottom = r.bottom }
            cursiva(l, hh, listOf("Elige la foto", "Elige"), color = l.gris, al = Layout.Alignment.ALIGN_CENTER, lineas = 2)
        }
    }
}
