package com.gonzalo.ptah

import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Los relojes: el digital (con tres cabeceras de revista), el de agujas (una esfera de revista, con romanos, árabes o rayas y cada pieza de
 * su color) y el calendario del mes (con hoy en el acento). En el widget, la hora y la fecha son un `TextClock` y las agujas un
 * `AnalogClock` ([Vivo]): se ponen al día solos, sin despertar a Ptah.
 */

/** El formato de la hora con el móvil en 24 horas y en 12 (los dos iguales, salvo «como el móvil»). */
private fun formatoHora(d: Diseno): Pair<String, String> = when (d.texto("formato", "24")) {
    "24cero" -> Formatos.HORA_CERO to Formatos.HORA_CERO
    "12" -> Formatos.HORA_12 to Formatos.HORA_12
    "movil" -> Formatos.HORA to Formatos.HORA_12
    else -> Formatos.HORA to Formatos.HORA
}

/** La letra de la hora: la que elijas de las de la familia o, de serie, la que más se parece a la de títulos del diseño. */
private fun letraHora(l: Lienzo): LetraFija = LetraFija.entries.firstOrNull { it.name == l.d.texto("letraHora") } ?: l.letraTitulosViva()

val OPCIONES_FORMATO = listOf("24" to "9:41", "24cero" to "09:41", "12" to "12 horas", "movil" to "Como el móvil")
val OPCIONES_LETRA_HORA = listOf("" to "La de los títulos") + LetraFija.entries.filter { it != LetraFija.VERSALITAS }.map { it.name to it.nombre }

object RelojDigital : TipoWidget {
    override val id = "digital"
    override val nombre = "Reloj digital"
    override val grupo = "Relojes"
    override val descripcion = "La hora en grande, con la fecha debajo. Un toque y estás en tus alarmas."
    override val muestra = Tamanos.Celdas(4, 2)
    override val piezas = listOf("hora" to "La hora", "minutos" to "Los minutos (en vertical)", "fecha" to "La fecha", "rayas" to "Las rayas", "cuadro" to "El cuadradito")
    override val partes = listOf("antetitulo" to "El antetítulo", "fecha" to "La fecha", "rayas" to "Las rayas", "numero" to "El número del día")
    val CABECERAS = listOf("portada" to "Portada", "columna" to "Columna", "titular" to "Titular con fecha")

    override fun opcionesDeSerie() = mapOf("cabecera" to "portada", "formato" to "24", "ante" to "La hora", "letraHora" to "")
    override fun necesita(d: Diseno) = if (d.estilo.ve("numero") && d.texto("cabecera") == "columna") setOf(Necesidad.MEDIANOCHE) else emptySet()
    override fun accion(d: Diseno) = Accion.Alarmas
    override fun descripcionToque(d: Diseno) = "Abrir las alarmas"

    override fun pinta(l: Lienzo) {
        val d = l.d
        if (l.proporcion == Lienzo.Proporcion.PEQUENO) { pequeno(l); return }
        if (l.proporcion == Lienzo.Proporcion.VERTICAL) { vertical(l); return }
        when (d.texto("cabecera", "portada")) {
            "columna" -> columna(l)
            "titular" -> titular(l)
            else -> portada(l)
        }
    }

    /** En un cuadrado pequeño (1×1): el cuadradito y el día arriba, la hora a todo lo ancho, la raya y la fecha corta. */
    private fun pequeno(l: Lienzo) {
        val a = l.area; val e = l.e
        val (fmt, fmt12) = formatoHora(l.d)
        val col = l.pieza("fecha", l.tinta)
        val ts = min(9f * l.k, a.height() * 0.16f)
        val altoT = l.altoLinea(LetraFija.VERSALITAS, ts)
        var top = a.top; var bottom = a.bottom
        if (e.ve("antetitulo")) {
            val q = ts * 0.8f
            l.c.drawRect(a.left, top + (altoT - q) / 2, a.left + q, top + (altoT + q) / 2, l.lleno(l.pieza("cuadro", l.acento)))
            l.vivoQueLlena("EEE", LetraFija.VERSALITAS, RectF(a.left + q + 4f, top, a.right, top + altoT), col, ts, Alineacion.IZQUIERDA)
            top += altoT + 2f
        }
        if (e.ve("fecha")) {
            if (l.vivoQueLlena("d MMM", LetraFija.VERSALITAS, RectF(a.left, bottom - altoT, a.right, bottom), col, ts) > 0f) {
                bottom -= altoT + 3f
                if (e.ve("rayas")) l.filete(bottom - 1f, 1f)
                bottom -= 3f
            }
        }
        l.vivoQueLlena(fmt, letraHora(l), RectF(a.left, top, a.right, bottom), l.pieza("hora", l.tinta), formato12 = fmt12)
    }

    /**
     * En vertical y estrecho (1×2): arriba, el día como cabecera de revista («SÁB 7» y la raya); en medio, las horas y los minutos
     * apilados, enormes; abajo, la raya fina y el mes.
     */
    private fun vertical(l: Lienzo) {
        val a = l.area; val e = l.e
        val formato = l.d.texto("formato", "24")
        val horas = if (formato == "12") "hh" else "HH"
        val col = l.pieza("fecha", l.tinta)
        val ts = min(10f * l.k, a.width() * 0.2f)
        val altoT = l.altoLinea(LetraFija.VERSALITAS, ts)
        var top = a.top; var bottom = a.bottom
        if (e.ve("antetitulo") || e.ve("fecha")) {
            val h = l.vivoQueLlena("EEE d", LetraFija.VERSALITAS, RectF(a.left, top, a.right, top + altoT), col, ts)
            if (h > 0f) { top += altoT + 3f; if (e.ve("rayas")) { l.filete(top, 1.8f); top += 1.8f }; top += 5f }
        }
        if (e.ve("fecha")) {
            val f = if (l.cabeVivo("MMMM", LetraFija.VERSALITAS, ts * 0.85f, a.width())) "MMMM" else "MMM"
            if (l.vivoQueLlena(f, LetraFija.VERSALITAS, RectF(a.left, bottom - altoT, a.right, bottom), l.gris, ts * 0.85f) > 0f) {
                bottom -= altoT + 4f
                if (e.ve("rayas")) l.filete(bottom - 0.8f, 0.8f)
                bottom -= 5f
            }
        }
        val letra = letraHora(l)
        val fila = (bottom - top - 2f) / 2f
        l.vivoQueLlena(horas, letra, RectF(a.left, top, a.right, top + fila), l.pieza("hora", l.tinta))
        l.vivoQueLlena("mm", letra, RectF(a.left, bottom - fila, a.right, bottom), l.pieza("minutos", l.acento))
    }

    /** «Portada»: la hora enorme y, debajo de una raya, la fecha en versalitas. */
    private fun portada(l: Lienzo) {
        val a = l.area; val e = l.e; val d = l.d
        val (fmt, fmt12) = formatoHora(d)
        var top = a.top; var bottom = a.bottom
        val conAnte = e.ve("antetitulo") && d.texto("ante").isNotBlank() && a.height() >= 90f && a.width() >= 110f
        if (conAnte) top += l.seccion(d.texto("ante"), a.left, top, a.width()) + 6f
        // La fecha, si cabe entera (la larga o, si no, la corta: «sáb 7 nov»), nunca cortada
        val fechaF = if (!e.ve("fecha") || (bottom - top) < 52f) null else listOf(Formatos.FECHA_LARGA, Formatos.FECHA_CORTA).firstOrNull { f ->
            l.cabeVivo(f, LetraFija.VERSALITAS, l.tamanoQueCabe(Formatos.muestras(f), LetraFija.VERSALITAS, a.width(), 30f, min(13f, max(8.5f, a.height() * 0.09f)) * l.k).takeIf { it >= 6.5f } ?: return@firstOrNull false, a.width())
        }
        if (fechaF != null) {
            val tf = l.tamanoQueCabe(Formatos.muestras(fechaF), LetraFija.VERSALITAS, a.width(), 30f, min(13f, max(8.5f, a.height() * 0.09f)) * l.k)
            val af = l.altoLinea(LetraFija.VERSALITAS, tf)
            l.textoVivo(RectF(a.left, bottom - af, a.right, bottom), fechaF, LetraFija.VERSALITAS, tf, l.pieza("fecha", l.tinta))
            bottom -= af + 5f
            if (e.ve("rayas")) l.filete(bottom - 1.6f, 1.6f)
            bottom -= 6f
        }
        val letra = letraHora(l)
        val tam = l.tamanoQueCabe((Formatos.muestras(fmt) + Formatos.muestras(fmt12)), letra, a.width(), bottom - top, 400f)
        val alto = l.altoLinea(letra, tam)
        l.textoVivo(RectF(a.left, top + (bottom - top - alto) / 2, a.right, top + (bottom - top + alto) / 2), fmt, letra, tam, l.pieza("hora", l.tinta), formato12 = fmt12)
    }

    /** «Columna»: el antetítulo, la raya gruesa, la hora, la raya fina, el día de la semana en cursiva y la fecha. */
    private fun columna(l: Lienzo) {
        val a = l.area; val e = l.e; val d = l.d
        val (fmt, fmt12) = formatoHora(d)
        var y = a.top
        val alto = a.height()
        if (e.ve("antetitulo") && d.texto("ante").isNotBlank() && alto >= 100f) {
            y += l.seccion(d.texto("ante"), a.left, y, a.width())
            if (e.ve("numero") && a.width() >= 150f) {
                val n = "Nº ${l.datos.ahora().dayOfYear}"
                l.ante(n, a.left, a.top, a.width(), l.gris, 9f, Layout.Alignment.ALIGN_OPPOSITE)
            }
            y += 4f
            if (e.ve("rayas")) { l.filete(y, 2.4f); y += 2.4f }
            y += 6f
        }
        val conFecha = e.ve("fecha") && alto >= 100f && l.cabeVivo(Formatos.DIA_SEMANA, LetraFija.CURSIVA, 11f, a.width())
        val resto = a.bottom - y
        val altoHora = if (conFecha) resto * 0.5f else resto
        val letra = letraHora(l)
        val tam = l.tamanoQueCabe((Formatos.muestras(fmt) + Formatos.muestras(fmt12)), letra, a.width(), altoHora, 300f)
        val ah = l.altoLinea(letra, tam)
        // (sin la fecha, la hora va en medio)
        if (!conFecha) { l.textoVivo(RectF(a.left, y + (resto - ah) / 2, a.right, y + (resto + ah) / 2), fmt, letra, tam, l.pieza("hora", l.tinta), formato12 = fmt12); return }
        l.textoVivo(RectF(a.left, y, a.right, y + ah), fmt, letra, tam, l.pieza("hora", l.tinta), formato12 = fmt12)
        y += ah + 4f
        if (e.ve("rayas")) { l.filete(y, 0.8f); y += 0.8f }
        y += 5f
        val sitio = a.bottom - y
        val td = l.tamanoQueCabe(Formatos.muestras(Formatos.DIA_SEMANA), LetraFija.CURSIVA, a.width(), sitio * 0.55f, 26f * l.k)
        val ad = l.altoLinea(LetraFija.CURSIVA, td)
        l.textoVivo(RectF(a.left, y, a.right, y + ad), Formatos.DIA_SEMANA, LetraFija.CURSIVA, td, l.pieza("fecha", l.tinta))
        y += ad + 2f
        val tf = l.tamanoQueCabe(Formatos.muestras(Formatos.DIA_MES), LetraFija.VERSALITAS, a.width(), max(8f, a.bottom - y), 11f * l.k)
        if (a.bottom - y >= l.altoLinea(LetraFija.VERSALITAS, tf) && l.cabeVivo(Formatos.DIA_MES, LetraFija.VERSALITAS, tf, a.width()) && tf >= 6.5f) l.textoVivo(RectF(a.left, y, a.right, y + l.altoLinea(LetraFija.VERSALITAS, tf)), Formatos.DIA_MES, LetraFija.VERSALITAS, tf, l.gris)
    }

    /** «Titular con fecha»: el día como titular («sábado 7»), el mes en cursiva y, tras la raya, la hora en el acento. */
    private fun titular(l: Lienzo) {
        val a = l.area; val e = l.e; val d = l.d
        val (fmt, fmt12) = formatoHora(d)
        var y = a.top
        if (e.ve("antetitulo") && d.texto("ante").isNotBlank() && a.height() >= 120f) y += l.seccion(d.texto("ante"), a.left, y, a.width()) + 6f
        val resto = a.bottom - y
        if (!e.ve("fecha") || resto < 70f || a.width() < 110f) {
            val letra = letraHora(l)
            val tam = l.tamanoQueCabe((Formatos.muestras(fmt) + Formatos.muestras(fmt12)), letra, a.width(), resto, 300f)
            l.textoVivo(RectF(a.left, y, a.right, a.bottom), fmt, letra, tam, l.pieza("hora", l.acento), formato12 = fmt12)
            return
        }
        val tit = l.letraTitulosViva()
        val f1 = "EEEE d"
        val t1 = l.tamanoQueCabe(Formatos.muestras(f1), tit, a.width(), resto * 0.42f, 60f * l.k)
        val a1 = l.altoLinea(tit, t1)
        l.textoVivo(RectF(a.left, y, a.right, y + a1), f1, tit, t1, l.pieza("fecha", l.tinta))
        y += a1
        val f2 = "'de' MMMM"
        val t2 = l.tamanoQueCabe(Formatos.muestras(f2), LetraFija.CURSIVA, a.width(), resto * 0.2f, t1 * 0.55f)
        val a2 = l.altoLinea(LetraFija.CURSIVA, t2)
        l.textoVivo(RectF(a.left, y, a.right, y + a2), f2, LetraFija.CURSIVA, t2, l.gris)
        y += a2 + 5f
        if (e.ve("rayas")) { l.filete(y, 1.6f); y += 1.6f }
        y += 5f
        val letra = letraHora(l)
        val th = l.tamanoQueCabe((Formatos.muestras(fmt) + Formatos.muestras(fmt12)), letra, a.width(), a.bottom - y, 200f)
        val ah = l.altoLinea(letra, th)
        l.textoVivo(RectF(a.left, y + (a.bottom - y - ah) / 2, a.right, y + (a.bottom - y + ah) / 2), fmt, letra, th, l.pieza("hora", l.acento), formato12 = fmt12)
    }

    @Composable override fun Editor(d: Diseno, cambia: (Diseno) -> Unit) {
        Elige("La cabecera", CABECERAS, d.texto("cabecera", "portada")) { cambia(d.con("cabecera", it)) }
        Elige("Las horas", OPCIONES_FORMATO, d.texto("formato", "24")) { cambia(d.con("formato", it)) }
        Elige("La letra de la hora", OPCIONES_LETRA_HORA, d.texto("letraHora")) { cambia(d.con("letraHora", it)) }
        Nota("En tu pantalla de inicio, Ptah vuelve a pintar el reloj al empezar cada minuto, con la pantalla encendida (Ajustes → Avanzado: «La hora de tus relojes»).",
            Modifier.padding(top = 4.dp))
        Campo("Antetítulo", d.texto("ante"), "La hora") { cambia(d.con("ante", it)) }
    }
}

object RelojAgujas : TipoWidget {
    override val id = "agujas"
    override val nombre = "Reloj de agujas"
    override val grupo = "Relojes"
    override val descripcion = "Una esfera de revista, con romanos, números o rayas, y cada aguja del color que quieras. Un toque y estás en tus alarmas."
    override val muestra = Tamanos.Celdas(2, 2)
    override val piezas = listOf("esfera" to "La esfera", "aro" to "El aro", "numeros" to "Los números", "marcas" to "Las rayas de la esfera",
        "horaria" to "La aguja de las horas", "minutera" to "La de los minutos", "segundero" to "El segundero", "texto" to "El nombre de la esfera", "fecha" to "La fecha")
    override val partes = listOf("aro" to "El aro", "marcas" to "Las rayas de la esfera", "numeros" to "Los números", "texto" to "El nombre de la esfera", "fecha" to "El día (si hay sitio)")
    val NUMEROS = listOf("romanos" to "Romanos", "arabes" to "Números", "cuatro" to "Solo 12, 3, 6 y 9", "rayas" to "Solo rayas")
    private val ROMANOS = listOf("XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI")

    override fun opcionesDeSerie() = mapOf("numeros" to "romanos", "forma" to FormaAgujas.HOJA.name, "segundero" to false, "texto" to "Ptah")
    override fun accion(d: Diseno) = Accion.Alarmas
    override fun descripcionToque(d: Diseno) = "Abrir las alarmas"

    override fun pinta(l: Lienzo) {
        val a = l.area; val e = l.e; val d = l.d
        // Ancho (4×2, 4×1): la esfera a la izquierda y el día al lado; alto (2×2 en vertical): la esfera arriba y el día debajo
        val ancho = e.ve("fecha") && a.width() >= a.height() * 1.15f && a.width() * 0.42f >= 84f && a.height() >= 60f
        val alto = !ancho && e.ve("fecha") && a.height() >= a.width() * 1.3f && a.height() - a.width() >= 46f
        val lado = when { ancho -> min(a.height(), a.width() * 0.58f); alto -> min(a.width(), a.height() * 0.72f); else -> min(a.width(), a.height()) }
        val r = when {
            ancho -> RectF(a.left, a.centerY() - lado / 2, a.left + lado, a.centerY() + lado / 2)
            alto -> RectF(a.centerX() - lado / 2, a.top, a.centerX() + lado / 2, a.top + lado)
            else -> RectF(a.centerX() - lado / 2, a.centerY() - lado / 2, a.centerX() + lado / 2, a.centerY() + lado / 2)
        }
        esfera(l, r)
        val forma = FormaAgujas.entries.firstOrNull { it.name == d.texto("forma") } ?: FormaAgujas.HOJA
        l.agujas(r, forma, l.pieza("horaria", l.tinta), l.pieza("minutera", l.tinta), if (d.si("segundero")) l.pieza("segundero", l.acento) else null, l.pieza("minutera", l.tinta))
        val tit = l.letraTitulosViva()
        if (ancho) {
            val col = RectF(r.right + 14f, a.top, a.right, a.bottom)
            var y = a.top + (a.height() * 0.1f)
            y += l.seccion(d.texto("ante", "Hoy").ifBlank { "Hoy" }, col.left, y, col.width()).let { if (it > 0f) it + 6f else 0f }
            val t1 = l.tamanoQueCabe(Formatos.muestras(Formatos.DIA_SEMANA), tit, col.width(), (a.bottom - y) * 0.4f, 34f * l.k)
            if (!l.cabeVivo(Formatos.DIA_SEMANA, tit, t1, col.width()) || t1 < 9f) return
            val a1 = l.altoLinea(tit, t1)
            l.textoVivo(RectF(col.left, y, col.right, y + a1), Formatos.DIA_SEMANA, tit, t1, l.pieza("fecha", l.tinta))
            y += a1 + 3f
            l.filete(y, 0.8f, x0 = col.left, x1 = col.right); y += 5f
            val t2 = l.tamanoQueCabe(Formatos.muestras(Formatos.DIA_MES), LetraFija.VERSALITAS, col.width(), max(9f, (a.bottom - y) * 0.5f), 11f * l.k)
            if (l.cabeVivo(Formatos.DIA_MES, LetraFija.VERSALITAS, t2, col.width()) && t2 >= 6.5f)
                l.textoVivo(RectF(col.left, y, col.right, y + l.altoLinea(LetraFija.VERSALITAS, t2)), Formatos.DIA_MES, LetraFija.VERSALITAS, t2, l.gris)
        } else if (alto && a.width() < 84f) {
            // Vertical y estrecho: debajo de la esfera, el día de la semana, el número enorme y el mes, apilados
            var y = r.bottom + 6f
            val ts = min(9f * l.k, a.width() * 0.2f)
            val altoT = l.altoLinea(LetraFija.VERSALITAS, ts)
            if (l.vivoQueLlena("EEE", LetraFija.VERSALITAS, RectF(a.left, y, a.right, y + altoT), l.pieza("fecha", l.tinta), ts, Alineacion.CENTRO) > 0f) y += altoT + 2f
            val mesH = if (a.bottom - y > altoT * 3) altoT else 0f
            l.vivoQueLlena("d", tit, RectF(a.left, y, a.right, a.bottom - mesH - (if (mesH > 0f) 2f else 0f)), l.pieza("fecha", l.tinta), al = Alineacion.CENTRO)
            if (mesH > 0f) l.vivoQueLlena("MMM", LetraFija.VERSALITAS, RectF(a.left, a.bottom - mesH, a.right, a.bottom), l.gris, ts, Alineacion.CENTRO)
        } else if (alto) {
            var y = r.bottom + 6f
            val sitio = a.bottom - y
            val t1 = l.tamanoQueCabe(Formatos.muestras(Formatos.DIA_SEMANA), tit, a.width(), sitio * 0.55f, 26f * l.k)
            if (!l.cabeVivo(Formatos.DIA_SEMANA, tit, t1, a.width()) || t1 < 9f) return
            val a1 = l.altoLinea(tit, t1)
            l.textoVivo(RectF(a.left, y, a.right, y + a1), Formatos.DIA_SEMANA, tit, t1, l.pieza("fecha", l.tinta), Alineacion.CENTRO)
            y += a1 + 1f
            val t2 = l.tamanoQueCabe(Formatos.muestras(Formatos.DIA_MES), LetraFija.VERSALITAS, a.width(), max(8f, a.bottom - y), 10f * l.k)
            if (a.bottom - y >= l.altoLinea(LetraFija.VERSALITAS, t2) && l.cabeVivo(Formatos.DIA_MES, LetraFija.VERSALITAS, t2, a.width()) && t2 >= 6.5f)
                l.textoVivo(RectF(a.left, y, a.right, y + l.altoLinea(LetraFija.VERSALITAS, t2)), Formatos.DIA_MES, LetraFija.VERSALITAS, t2, l.gris, Alineacion.CENTRO)
        }
    }

    /** La esfera: el fondo, el aro, las rayas de los minutos y las horas, los números y el nombre. */
    private fun esfera(l: Lienzo, r: RectF) {
        val e = l.e; val d = l.d; val c = l.c
        val cx = r.centerX(); val cy = r.centerY(); val rad = r.width() / 2f
        val fondo = l.pieza("esfera", 0)
        if (fondo != 0) c.drawCircle(cx, cy, rad, l.lleno(fondo))
        if (e.ve("aro")) c.drawCircle(cx, cy, rad - rad * 0.012f, l.trazo(l.pieza("aro", l.tinta), max(0.8f, rad * 0.024f)))
        val num = d.texto("numeros", "romanos")
        if (e.ve("marcas")) {
            val col = l.pieza("marcas", l.tinta)
            for (i in 0 until 60) {
                val hora = i % 5 == 0
                if (!hora && rad < 34f) continue
                // (donde va un número, la raya se queda corta para no tocarlo)
                val largo = if (hora) rad * 0.09f else rad * 0.04f
                val grueso = if (hora) max(0.9f, rad * 0.025f) else max(0.5f, rad * 0.01f)
                val ang = Math.toRadians(i * 6.0)
                val r0 = rad * 0.94f; val r1 = r0 - largo
                c.drawLine(cx + (sin(ang) * r0).toFloat(), cy - (cos(ang) * r0).toFloat(), cx + (sin(ang) * r1).toFloat(), cy - (cos(ang) * r1).toFloat(),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (hora) col else Lienzo.conAlfa(col, 150); strokeWidth = grueso; strokeCap = Paint.Cap.BUTT })
            }
        }
        if (e.ve("numeros") && num != "rayas" && rad >= 26f) {
            val col = l.pieza("numeros", l.tinta)
            val cuales = if (num == "cuatro") listOf(0, 3, 6, 9) else (0 until 12).toList()
            val tam = rad * (if (num == "romanos") 0.17f else 0.2f) * l.k * (if (num == "cuatro") 1.25f else 1f)
            val p = l.tp(l.fTitular, tam, col, 0f).apply { textAlign = Paint.Align.CENTER }
            val rr = rad * (if (e.ve("marcas")) 0.7f else 0.8f)
            for (i in cuales) {
                val t = if (num == "romanos") ROMANOS[i] else (if (i == 0) "12" else "$i")
                val ang = Math.toRadians(i * 30.0)
                val fm = p.fontMetrics
                c.drawText(t, cx + (sin(ang) * rr).toFloat(), cy - (cos(ang) * rr).toFloat() - (fm.ascent + fm.descent) / 2, p)
            }
        }
        val texto = d.texto("texto")
        if (e.ve("texto") && texto.isNotBlank() && rad >= 40f) {
            val p = l.tp(l.fNegrita, max(5f, rad * 0.08f * l.k), l.pieza("texto", l.tinta), 0.2f).apply { textAlign = Paint.Align.CENTER }
            c.drawText(texto.uppercase(java.util.Locale("es", "ES")), cx, cy - rad * 0.34f, p)
        }
    }

    @Composable override fun Editor(d: Diseno, cambia: (Diseno) -> Unit) {
        Elige("Las horas de la esfera", NUMEROS, d.texto("numeros", "romanos")) { cambia(d.con("numeros", it)) }
        Elige("Las agujas", FormaAgujas.entries.map { it.name to it.nombre }, d.texto("forma", FormaAgujas.HOJA.name)) { cambia(d.con("forma", it)) }
        SiNo("Con segundero", "Una aguja fina más, la de los segundos: en la vista previa (y en tu pantalla de inicio solo con «la letra del móvil», en Android 12 o más; dibujado cada minuto se quedaría quieto).", d.si("segundero")) { cambia(d.con("segundero", it)) }
        Campo("El nombre de la esfera", d.texto("texto"), "Sin nombre") { cambia(d.con("texto", it)) }
        Campo("Antetítulo del día (si es ancho)", d.texto("ante"), "Hoy") { cambia(d.con("ante", it)) }
    }
}

object Calendario : TipoWidget {
    override val id = "calendario"
    override val nombre = "Calendario del mes"
    override val grupo = "Relojes"
    override val descripcion = "El mes entero, con hoy en el acento. Un toque y estás en tu calendario."
    override val muestra = Tamanos.Celdas(3, 2)
    override val piezas = listOf("cabecera" to "El mes", "numeros" to "Los días", "hoy" to "Hoy", "sobrehoy" to "El número de hoy", "finde" to "El fin de semana", "semana" to "Los días de la semana")
    override val partes = listOf("anio" to "El año", "semana" to "Los días de la semana", "otros" to "Los días de otros meses", "rayas" to "Las rayas")

    override fun opcionesDeSerie() = mapOf("lunes" to true)
    override fun necesita(d: Diseno) = setOf(Necesidad.MEDIANOCHE)
    override fun accion(d: Diseno) = Accion.Calendario
    override fun descripcionToque(d: Diseno) = "Abrir el calendario"

    /** Los días de la rejilla del mes de [hoy] (semanas enteras: con los de los meses de al lado). */
    fun rejilla(hoy: LocalDate, lunes: Boolean = true): List<LocalDate> {
        val primero = hoy.withDayOfMonth(1)
        val desfase = if (lunes) primero.dayOfWeek.value - 1 else primero.dayOfWeek.value % 7
        val inicio = primero.minusDays(desfase.toLong())
        val ultimo = hoy.withDayOfMonth(hoy.lengthOfMonth())
        val semanas = ((ChronoUnit.DAYS.between(inicio, ultimo) / 7) + 1).toInt()
        return (0 until semanas * 7).map { inicio.plusDays(it.toLong()) }
    }

    override fun pinta(l: Lienzo) {
        val a = l.area; val e = l.e; val d = l.d
        val hoy = l.datos.ahora().toLocalDate()
        if (l.proporcion != Lienzo.Proporcion.NORMAL || a.width() < 120f || a.height() < 110f) { pequeno(l, hoy); return }
        var y = a.top
        val mes = Formatos.mes(hoy.monthValue).replaceFirstChar { it.uppercase() }
        val anioAncho = if (e.ve("anio")) l.anchoAnte("${hoy.year}") + 6f else 0f
        val t = l.titular(mes, a.width() - anioAncho, min(34f, a.height() * 0.2f) * l.k, min(26f, a.height() * 0.16f), 11f, 1, l.pieza("cabecera", l.tinta), al = Layout.Alignment.ALIGN_NORMAL)
        l.pon(t, a.left, y)
        if (e.ve("anio")) l.ante("${hoy.year}", a.right - anioAncho, y + t.height - l.altoAnte() - 2f, anioAncho, l.gris, al = Layout.Alignment.ALIGN_OPPOSITE)
        y += t.height + 3f
        if (e.ve("rayas")) { l.filete(y, 1.6f); y += 1.6f }
        y += 4f
        val lunes = d.si("lunes", true)
        val letras = if (lunes) listOf("L", "M", "X", "J", "V", "S", "D") else listOf("D", "L", "M", "X", "J", "V", "S")
        val cw = a.width() / 7f
        if (e.ve("semana")) {
            letras.forEachIndexed { i, s -> l.ante(s, a.left + i * cw, y, cw, l.pieza("semana", l.gris), 8f, Layout.Alignment.ALIGN_CENTER) }
            y += l.altoAnte(8f) + 4f
        }
        val dias = rejilla(hoy, lunes)
        val filas = dias.size / 7
        val ch = (a.bottom - y) / filas
        val tam = min(ch * 0.55f, cw * 0.5f).coerceAtLeast(6f)
        dias.forEachIndexed { i, dia ->
            val fila = i / 7; val coln = i % 7
            val otro = dia.monthValue != hoy.monthValue
            if (otro && !e.ve("otros")) return@forEachIndexed
            val cx = a.left + coln * cw + cw / 2; val cy = y + fila * ch + ch / 2
            val finde = dia.dayOfWeek.value >= 6
            var color = if (finde) l.pieza("finde", l.pieza("numeros", l.tinta)) else l.pieza("numeros", l.tinta)
            if (otro) color = Lienzo.conAlfa(l.gris, 140)
            if (dia == hoy) {
                val q = min(cw, ch) * 0.86f
                l.c.drawRoundRect(RectF(cx - q / 2, cy - q / 2, cx + q / 2, cy + q / 2), q * 0.18f, q * 0.18f, l.lleno(l.pieza("hoy", l.acento)))
                color = l.pieza("sobrehoy", if (Lienzo.luz(l.pieza("hoy", l.acento)) > 0.6f) 0xFF15120F.toInt() else 0xFFFFFFFF.toInt())
            }
            val p = l.tp(if (dia == hoy) l.fNegrita else l.fTexto, tam * l.k, color).apply { textAlign = Paint.Align.CENTER }
            val fm = p.fontMetrics
            l.c.drawText("${dia.dayOfMonth}", cx, cy - (fm.ascent + fm.descent) / 2, p)
        }
    }

    /** En pequeño (1×1) o en vertical (1×2): el día de la semana arriba con su raya, el número enorme y el mes; en vertical, también el año. */
    private fun pequeno(l: Lienzo, hoy: LocalDate) {
        val a = l.area
        val vertical = l.proporcion == Lienzo.Proporcion.VERTICAL
        val ts = min(9f * l.k, a.width() * 0.2f)
        var top = a.top; var bottom = a.bottom
        val dia = (if (vertical && l.cabe(Formatos.dia(hoy.dayOfWeek).uppercase(), l.fNegrita, ts, a.width(), 0.16f) != null) Formatos.dia(hoy.dayOfWeek) else Formatos.diaCorto(hoy.dayOfWeek))
        top += l.ante(dia, a.left, top, a.width(), l.pieza("hoy", l.acento), ts / l.k).let { if (it > 0f) it + 3f else 0f }
        if (l.e.ve("rayas") && top > a.top && vertical) { l.filete(top, 1.6f); top += 5f }
        if (vertical && l.e.ve("anio")) { val h = l.ante("${hoy.year}", a.left, bottom - l.altoAnte(ts / l.k), a.width(), l.gris, ts / l.k); if (h > 0f) bottom -= h + 3f }
        val mes = Formatos.mes(hoy.monthValue)
        val mesT = if (vertical && l.cabe(mes, l.fCursiva, 12f * l.k, a.width()) != null) mes else Formatos.formatea("MMM", l.datos.ahora())
        val altoMes = l.altoLinea(mesT, l.fCursiva, if (vertical) 13f else 11.5f, a.width())
        if (altoMes > 0f && bottom - top > altoMes * 2f) {
            l.linea(mesT, l.fCursiva, if (vertical) 13f else 11.5f, l.gris, a.left, bottom - altoMes, a.width())
            bottom -= altoMes + 2f
        }
        l.grande("${hoy.dayOfMonth}", l.fTitular, RectF(a.left, top, a.right, bottom), l.pieza("numeros", l.tinta))
    }

    @Composable override fun Editor(d: Diseno, cambia: (Diseno) -> Unit) {
        SiNo("La semana empieza el lunes", "Si no, el domingo.", d.si("lunes", true)) { cambia(d.con("lunes", it)) }
        Nota("El mes se vuelve a dibujar solo cada medianoche.", Modifier.padding(top = 4.dp))
    }
}

