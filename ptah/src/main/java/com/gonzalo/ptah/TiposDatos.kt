package com.gonzalo.ptah

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Los datos: el tiempo (de Open-Meteo, con sus iconos de revista) y la batería.
 */

/** «12°», «−2°» (con el signo menos de verdad). */
fun grados(t: Double): String { val r = t.roundToInt(); return (if (r < 0) "−${-r}" else "$r") + "°" }

object Tiempo : TipoWidget {
    override val id = "tiempo"
    override val nombre = "El tiempo"
    override val grupo = "Datos"
    override val descripcion = "El tiempo de ahora en tu ciudad, las máximas y las mínimas y los próximos días. Un toque y lo cambias en Widgets."
    override val muestra = Tamanos.Celdas(4, 2)
    override val piezas = listOf("temp" to "La temperatura", "icono" to "Los iconos", "sol" to "El sol (y el rayo)", "dias" to "Los próximos días")
    override val partes = listOf("lugar" to "El nombre del sitio", "estado" to "Cómo está el cielo", "maxmin" to "Máxima y mínima", "dias" to "Los próximos días")

    override fun opcionesDeSerie() = mapOf("lugar" to "ciudad", "ciudad" to "Madrid", "region" to "Comunidad de Madrid", "lat" to 40.4165, "lon" to -3.70256, "dias" to 4)
    override fun necesita(d: Diseno) = setOf(Necesidad.TIEMPO)

    fun nombreSitio(d: Diseno) = if (d.texto("lugar") == "aqui") "Donde estás" else d.texto("ciudad").ifBlank { "Tu ciudad" }

    override fun pinta(l: Lienzo) {
        val a = l.area; val e = l.e; val d = l.d
        val p = l.datos.tiempo(d)
        val attH = if (a.height() >= 60f) 8f else 0f
        val bajo = RectF(a.left, a.top, a.right, a.bottom - attH)
        // La atribución (la pide la licencia de los datos): siempre, pequeña; en uno estrecho, solo «Open-Meteo»
        if (attH > 0f && l.ante(OpenMeteo.ATRIBUCION, a.left, a.bottom - 6f, a.width(), Lienzo.conAlfa(l.gris, 200), 5.5f, Layout.Alignment.ALIGN_OPPOSITE) == 0f)
            l.ante("Open-Meteo", a.left, a.bottom - 6f, a.width(), Lienzo.conAlfa(l.gris, 200), 5.5f, Layout.Alignment.ALIGN_OPPOSITE)
        if (p == null) {
            var y = bajo.top
            if (e.ve("lugar")) y += l.seccion(nombreSitio(d), a.left, y, a.width()) + 5f
            val t = l.titular(if (sitioDe(l.ctx, d) == null) "Elige dónde" else "Sin datos todavía", a.width(), (bajo.bottom - y) * 0.6f, 22f, 10f, 2)
            y += l.pon(t, a.left, y) + 3f
            if (bajo.bottom - y > 28f && a.width() >= 100f) l.pon(l.capa(if (sitioDe(l.ctx, d) == null) "Un toque y lo eliges en Widgets." else "Llegan en cuanto haya conexión.", l.tpk(l.fCursiva, 11f, l.gris), a.width(), max(1, ((bajo.bottom - y) / 14f).toInt())), a.left, y)
            return
        }
        val est = Wmo.de(p.codigo)
        val noche = !p.esDia
        val colTemp = l.pieza("temp", l.tinta); val colIco = l.pieza("icono", l.tinta); val colSol = l.pieza("sol", l.acento)
        if (l.proporcion == Lienzo.Proporcion.PEQUENO) {
            // 1×1: el icono grande arriba y la temperatura a todo lo ancho debajo
            val iconoH = bajo.height() * 0.5f
            val ico = min(iconoH, bajo.width() * 0.8f)
            IconosTiempo.pinta(l.c, est.icono, RectF(bajo.centerX() - ico / 2, bajo.top, bajo.centerX() + ico / 2, bajo.top + ico), colIco, colSol, l.papelDeVerdad(), noche)
            l.grande(grados(p.temp), l.fTitular, RectF(bajo.left, bajo.top + ico + 1f, bajo.right, bajo.bottom), colTemp, Layout.Alignment.ALIGN_CENTER)
            return
        }
        if (l.proporcion == Lienzo.Proporcion.VERTICAL) {
            // 1×2: el sitio, el icono, la temperatura enorme y la máxima y la mínima, apilados
            var y = bajo.top
            if (e.ve("lugar")) y += l.ante(nombreSitio(d), a.left, y, a.width(), l.tinta, 8f, Layout.Alignment.ALIGN_CENTER).let { if (it > 0f) it + 4f else 0f }
            val hoy = p.dias.firstOrNull { it.fecha == p.hora.toLocalDate() } ?: p.dias.firstOrNull()
            val mmH = if (e.ve("maxmin") && hoy != null) l.altoAnte(8.5f) * 2 + 6f else 0f
            val resto = bajo.bottom - y - mmH
            val ico = min(bajo.width(), resto * 0.48f)
            IconosTiempo.pinta(l.c, est.icono, RectF(bajo.centerX() - ico / 2, y, bajo.centerX() + ico / 2, y + ico), colIco, colSol, l.papelDeVerdad(), noche)
            y += ico + 2f
            l.grande(grados(p.temp), l.fTitular, RectF(bajo.left, y, bajo.right, bajo.bottom - mmH), colTemp, Layout.Alignment.ALIGN_CENTER)
            if (mmH > 0f && hoy != null) {
                var my = bajo.bottom - mmH + 4f
                my += l.ante("↑ ${grados(hoy.max)}", a.left, my, a.width(), l.tinta, 8.5f, Layout.Alignment.ALIGN_CENTER) + 2f
                l.ante("↓ ${grados(hoy.min)}", a.left, my, a.width(), l.gris, 8.5f, Layout.Alignment.ALIGN_CENTER)
            }
            return
        }
        if (bajo.height() < 100f || bajo.width() < 130f) {
            // En pequeño: el icono y la temperatura (y el sitio, si cabe)
            var y = bajo.top
            if (e.ve("lugar") && bajo.height() >= 70f) y += l.ante(nombreSitio(d), a.left, y, a.width(), l.tinta, 8f) + 3f
            val alto = bajo.bottom - y
            val ico = min(alto, bajo.width() * 0.42f)
            IconosTiempo.pinta(l.c, est.icono, RectF(a.left, y + (alto - ico) / 2, a.left + ico, y + (alto + ico) / 2), colIco, colSol, l.papelDeVerdad(), noche)
            val t = l.titular(grados(p.temp), bajo.width() - ico - 4f, alto, 60f, 10f, 1, colTemp, al = Layout.Alignment.ALIGN_OPPOSITE)
            l.pon(t, a.left + ico + 4f, y + (alto - t.height) / 2)
            return
        }
        var y = bajo.top
        if (e.ve("lugar")) {
            y += l.seccion(nombreSitio(d), a.left, y, a.width() - 60f)
            l.ante(Formatos.formatea("HH:mm", p.hora.atZone(java.time.ZoneId.systemDefault())), a.left, bajo.top, a.width(), l.gris, 8f, Layout.Alignment.ALIGN_OPPOSITE)
            y += 5f
        }
        val nDias = if (e.ve("dias")) d.entero("dias", 4).coerceIn(0, 6) else 0
        val conDias = nDias > 0 && bajo.bottom - y >= 110f
        val altoDias = if (conDias) min(56f * l.k, (bajo.bottom - y) * 0.38f) else 0f
        val fila = RectF(a.left, y, a.right, bajo.bottom - altoDias - (if (conDias) 6f else 0f))
        // La temperatura enorme a la izquierda y el icono a la derecha; debajo, cómo está el cielo y la máxima y la mínima
        val lineas = (if (e.ve("estado")) 1 else 0) + (if (e.ve("maxmin")) 1 else 0)
        val altoPie = lineas * 15f * l.k
        val ico = min(fila.height() - 2f, fila.width() * 0.38f).coerceAtLeast(20f)
        IconosTiempo.pinta(l.c, est.icono, RectF(fila.right - ico, fila.top + (fila.height() - ico) / 2, fila.right, fila.top + (fila.height() + ico) / 2), colIco, colSol, l.papelDeVerdad(), noche)
        val anchoT = fila.width() - ico - 8f
        val t = l.titular(grados(p.temp), anchoT, fila.height() - altoPie, 80f, 14f, 1, colTemp, al = Layout.Alignment.ALIGN_NORMAL, inter = 0.9f)
        var ty = fila.top + (fila.height() - altoPie - t.height) / 2
        ty += l.pon(t, fila.left, ty)
        if (e.ve("estado")) ty += l.pon(l.capa(est.texto, l.tpk(l.fCursiva, 12.5f, l.tinta), anchoT, 1, al = Layout.Alignment.ALIGN_NORMAL), fila.left, ty) + 1f
        val hoy = p.dias.firstOrNull { it.fecha == p.hora.toLocalDate() } ?: p.dias.firstOrNull()
        if (e.ve("maxmin") && hoy != null) l.ante("Máx ${grados(hoy.max)} · Mín ${grados(hoy.min)}", fila.left, ty + 2f, anchoT, l.gris, 8.5f, Layout.Alignment.ALIGN_NORMAL)
        if (conDias) {
            val luego = p.dias.filter { it.fecha.isAfter(p.hora.toLocalDate()) }.take(nDias)
            if (luego.isEmpty()) return
            val top = bajo.bottom - altoDias
            l.filete(top - 4f, 0.8f, l.pieza("rayas", l.tinta))
            val cw = a.width() / luego.size
            luego.forEachIndexed { i, dia ->
                val x = a.left + i * cw
                val colD = l.pieza("dias", l.tinta)
                l.ante(Formatos.diaCorto(dia.fecha.dayOfWeek), x, top + 1f, cw, colD, 7.5f, Layout.Alignment.ALIGN_CENTER)
                val ih = min(altoDias - 26f, cw * 0.5f).coerceAtLeast(10f)
                IconosTiempo.pinta(l.c, Wmo.de(dia.codigo).icono, RectF(x + (cw - ih) / 2, top + 12f, x + (cw + ih) / 2, top + 12f + ih), colIco, colSol, l.papelDeVerdad(), false)
                val tx = l.capa("${grados(dia.max)} ${grados(dia.min)}", l.tpk(l.fTexto, 9f, colD), cw, 1, al = Layout.Alignment.ALIGN_CENTER)
                l.pon(tx, x, top + 14f + ih)
            }
        }
    }

    @Composable override fun Editor(d: Diseno, cambia: (Diseno) -> Unit) {
        val ctx = LocalContext.current
        val ambito = rememberCoroutineScope()
        Elige("Dónde", listOf("ciudad" to "Una ciudad", "aqui" to "Donde estés"), d.texto("lugar", "ciudad")) { cambia(d.con("lugar", it)) }
        if (d.texto("lugar") == "aqui") {
            var permitida by remember { mutableStateOf(Ubicacion.permitida(ctx)) }
            val pide = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> permitida = ok; if (ok) kotlin.concurrent.thread { Ubicacion.actualiza(ctx.applicationContext); Widgets.pedir(ctx) } }
            if (!permitida) MagLink("Dejar a Widgets saber dónde estás", "Solo la ubicación aproximada; no sale de este móvil", accent = true) { pide.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) }
            else Nota("Se usa la ubicación aproximada de este móvil (no sale de él). Se mira cada vez que abres Widgets.", Modifier.padding(top = 4.dp))
        } else {
            var busca by remember { mutableStateOf("") }
            var res by remember { mutableStateOf<List<Lugar>?>(null) }
            var aviso by remember { mutableStateOf<String?>(null) }
            Etiqueta("Ahora", Modifier.padding(top = 8.dp))
            Titulo(d.texto("ciudad").ifBlank { "Sin elegir" }, 24)
            if (d.texto("region").isNotBlank()) Cuerpo(d.texto("region"), tam = 12)
            Campo("Buscar otra ciudad", busca, "León, Gijón, Cádiz…") { busca = it }
            MagLink("Buscar", accent = true) {
                if (busca.isBlank()) return@MagLink
                aviso = null
                ambito.launch {
                    try { res = withContext(Dispatchers.IO) { CacheTiempo.busca(busca) }; if (res!!.isEmpty()) aviso = "No encuentro «$busca». Prueba con otro nombre." }
                    catch (e: Exception) { aviso = "No se ha podido buscar (¿sin conexión?)." }
                }
            }
            res?.forEach { r ->
                MagLink(r.nombre, r.detalle) {
                    cambia(d.copy(opciones = d.opciones + mapOf("ciudad" to r.nombre, "region" to r.detalle, "lat" to r.lat, "lon" to r.lon)))
                    res = null; busca = ""
                }
            }
            aviso?.let { Nota(it, color = Acento) }
        }
        Elige("Los próximos días", listOf("0" to "Ninguno", "3" to "3", "4" to "4", "5" to "5", "6" to "6"), d.entero("dias", 4).toString()) { cambia(d.con("dias", it.toInt())) }
        Nota("${OpenMeteo.LICENCIA} Se pide cada 30–60 minutos.", Modifier.padding(top = 6.dp))
    }
}

object TipoBateria : TipoWidget {
    override val id = "bateria"
    override val nombre = "La batería"
    override val grupo = "Datos"
    override val descripcion = "Lo que te queda de batería, y si está cargando. Un toque y ves en qué se gasta."
    override val muestra = Tamanos.Celdas(2, 1)
    override val piezas = listOf("nivel" to "La carga", "bajo" to "La carga, cuando queda poca", "barra" to "La pila", "cifra" to "La cifra")
    override val partes = listOf("ante" to "El antetítulo", "barra" to "La pila", "estado" to "Si está cargando")
    override fun necesita(d: Diseno) = setOf(Necesidad.BATERIA)
    override fun accion(d: Diseno) = Accion.Bateria
    override fun descripcionToque(d: Diseno) = "Ver el uso de la batería"
    override fun opcionesDeSerie() = mapOf("ante" to "Batería")

    override fun pinta(l: Lienzo) {
        val a = l.area; val e = l.e; val d = l.d
        val b = l.datos.bateria() ?: Bateria(100, false)
        val colNivel = if (b.nivel <= 20 && !b.cargando) l.pieza("bajo", 0xFFC8372D.toInt()) else l.pieza("nivel", l.acento)
        if (l.proporcion != Lienzo.Proporcion.NORMAL) { pila(l, b, colNivel); return }
        var y = a.top
        if (e.ve("ante") && d.texto("ante").isNotBlank() && a.height() >= 80f) y += l.seccion(d.texto("ante"), a.left, y, a.width()) + 4f
        val conBarra = e.ve("barra") && a.height() - (y - a.top) >= 44f
        val altoBarra = if (conBarra) min(18f, a.height() * 0.18f) else 0f
        val conEstado = e.ve("estado") && b.cargando && a.height() - (y - a.top) >= 64f
        val altoEstado = if (conEstado) 15f * l.k else 0f
        val sitio = a.bottom - y - altoBarra - altoEstado - (if (conBarra) 6f else 0f)
        val t = l.titular("${b.nivel} %", a.width(), sitio, 110f, 12f, 1, l.pieza("cifra", l.tinta), inter = 0.9f)
        y += (sitio - t.height) / 2
        y += l.pon(t, a.left, y)
        if (conEstado) y += l.linea("Cargando", l.fCursiva, 12f, l.gris, a.left, y, a.width())
        if (conBarra) {
            val top = a.bottom - altoBarra
            val nub = altoBarra * 0.3f
            val r = RectF(a.left, top, a.right - nub - 1f, a.bottom)
            val g = max(1f, altoBarra * 0.08f)
            val colBarra = l.pieza("barra", l.tinta)
            l.c.drawRoundRect(RectF(r.left + g / 2, r.top + g / 2, r.right - g / 2, r.bottom - g / 2), altoBarra * 0.22f, altoBarra * 0.22f, l.trazo(colBarra, g))
            l.c.drawRoundRect(RectF(r.right + 1f, top + altoBarra * 0.3f, r.right + 1f + nub, a.bottom - altoBarra * 0.3f), nub * 0.3f, nub * 0.3f, l.lleno(colBarra))
            val dentro = RectF(r.left + g * 2, r.top + g * 2, r.right - g * 2, r.bottom - g * 2)
            l.c.drawRoundRect(RectF(dentro.left, dentro.top, dentro.left + dentro.width() * b.nivel / 100f, dentro.bottom), altoBarra * 0.12f, altoBarra * 0.12f, l.lleno(colNivel))
            if (b.cargando) {
                // El rayo, en el centro
                val cx = dentro.centerX(); val cy = dentro.centerY(); val hh = dentro.height() * 0.42f
                l.c.drawPath(Path().apply {
                    moveTo(cx + hh * 0.25f, cy - hh); lineTo(cx - hh * 0.45f, cy + hh * 0.1f); lineTo(cx, cy + hh * 0.1f)
                    lineTo(cx - hh * 0.25f, cy + hh); lineTo(cx + hh * 0.45f, cy - hh * 0.1f); lineTo(cx, cy - hh * 0.1f); close()
                }, l.lleno(if (Lienzo.luz(colNivel) > 0.55f || b.nivel < 50) l.tinta else 0xFFFFFFFF.toInt()))
            }
        }
    }

    /** En pequeño o en vertical: la pila de pie, llenándose desde abajo, y el porcentaje debajo. */
    private fun pila(l: Lienzo, b: Bateria, colNivel: Int) {
        val a = l.area
        val vertical = l.proporcion == Lienzo.Proporcion.VERTICAL
        val numH = if (vertical) min(a.height() * 0.3f, a.width() * 0.62f) else a.height() * 0.42f
        val sitio = RectF(a.left, a.top, a.right, a.bottom - numH - 3f)
        val pw = min(sitio.width() * (if (vertical) 0.62f else 0.5f), sitio.height() * 0.55f)
        val nub = pw * 0.32f
        val cuerpo = RectF(sitio.centerX() - pw / 2, sitio.top + nub * 0.45f, sitio.centerX() + pw / 2, sitio.bottom)
        val g = max(1.2f, pw * 0.07f)
        val colBarra = l.pieza("barra", l.tinta)
        l.c.drawRoundRect(RectF(sitio.centerX() - nub / 2, sitio.top, sitio.centerX() + nub / 2, cuerpo.top + 1f), nub * 0.2f, nub * 0.2f, l.lleno(colBarra))
        l.c.drawRoundRect(RectF(cuerpo.left + g / 2, cuerpo.top + g / 2, cuerpo.right - g / 2, cuerpo.bottom - g / 2), pw * 0.16f, pw * 0.16f, l.trazo(colBarra, g))
        val dentro = RectF(cuerpo.left + g * 2, cuerpo.top + g * 2, cuerpo.right - g * 2, cuerpo.bottom - g * 2)
        l.c.drawRoundRect(RectF(dentro.left, dentro.bottom - dentro.height() * b.nivel / 100f, dentro.right, dentro.bottom), pw * 0.08f, pw * 0.08f, l.lleno(colNivel))
        if (b.cargando) {
            val cx = dentro.centerX(); val cy = dentro.centerY(); val hh = min(dentro.height() * 0.22f, dentro.width() * 0.45f)
            l.c.drawPath(Path().apply {
                moveTo(cx + hh * 0.25f, cy - hh); lineTo(cx - hh * 0.45f, cy + hh * 0.1f); lineTo(cx, cy + hh * 0.1f)
                lineTo(cx - hh * 0.25f, cy + hh); lineTo(cx + hh * 0.45f, cy - hh * 0.1f); lineTo(cx, cy - hh * 0.1f); close()
            }, l.lleno(l.tinta))
        }
        l.grande("${b.nivel} %", l.fTitular, RectF(a.left, a.bottom - numH, a.right, a.bottom), l.pieza("cifra", l.tinta), Layout.Alignment.ALIGN_CENTER)
    }

    @Composable override fun Editor(d: Diseno, cambia: (Diseno) -> Unit) {
        Campo("Antetítulo", d.texto("ante"), "Batería") { cambia(d.con("ante", it)) }
        Nota("En la pantalla de inicio se pone al día cada 15 minutos y cada vez que abres Widgets (Android no deja a los widgets saberlo al momento).", Modifier.padding(top = 6.dp))
    }
}

/** Los iconos del tiempo, de línea, como los de una revista: la tinta para las nubes y el acento para el sol y el rayo. */
object IconosTiempo {
    fun pinta(c: Canvas, i: IconoTiempo, r: RectF, tinta: Int, sol: Int, fondo: Int, noche: Boolean) {
        val s = min(r.width(), r.height())
        val cx = r.centerX(); val cy = r.centerY()
        val g = max(0.9f, s * 0.055f)
        val linea = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tinta; style = Paint.Style.STROKE; strokeWidth = g; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val lineaSol = Paint(linea).apply { color = sol }
        fun astro(x: Float, y: Float, rad: Float) {
            if (noche) {
                val p = Path().apply { addCircle(x, y, rad, Path.Direction.CW) }
                val q = Path().apply { addCircle(x + rad * 0.55f, y - rad * 0.4f, rad * 0.85f, Path.Direction.CW) }
                p.op(q, Path.Op.DIFFERENCE)
                c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sol })
            } else {
                c.drawCircle(x, y, rad * 0.62f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sol })
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val r0 = rad * 0.86f; val r1 = rad * 1.18f
                    c.drawLine(x + (cos(a) * r0).toFloat(), y + (sin(a) * r0).toFloat(), x + (cos(a) * r1).toFloat(), y + (sin(a) * r1).toFloat(), lineaSol)
                }
            }
        }
        fun nube(x: Float, y: Float, w: Float, relleno: Boolean = false) {
            // Una nube: tres círculos y la base plana
            val h = w * 0.55f
            val p = Path().apply {
                addCircle(x - w * 0.22f, y + h * 0.12f, w * 0.2f, Path.Direction.CW)
                op(Path().apply { addCircle(x + w * 0.04f, y - h * 0.12f, w * 0.27f, Path.Direction.CW) }, Path.Op.UNION)
                op(Path().apply { addCircle(x + w * 0.28f, y + h * 0.1f, w * 0.2f, Path.Direction.CW) }, Path.Op.UNION)
                op(Path().apply { addRoundRect(RectF(x - w * 0.42f, y + h * 0.05f, x + w * 0.48f, y + h * 0.48f), w * 0.2f, w * 0.2f, Path.Direction.CW) }, Path.Op.UNION)
            }
            c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (relleno) Lienzo.mezcla(fondo, tinta, 0.18f) else fondo })
            c.drawPath(p, linea)
        }
        fun gotas(n: Int, y0: Float, largo: Float, puntos: Boolean = false, copos: Boolean = false) {
            val paso = s * 0.17f
            val x0 = cx - paso * (n - 1) / 2f
            for (k in 0 until n) {
                val x = x0 + k * paso
                when {
                    copos -> { val q = s * 0.05f; c.drawLine(x - q, y0, x + q, y0, linea); c.drawLine(x, y0 - q, x, y0 + q, linea); c.drawLine(x - q * 0.7f, y0 - q * 0.7f, x + q * 0.7f, y0 + q * 0.7f, linea) }
                    puntos -> c.drawPoint(x, y0 + (k % 2) * largo * 0.5f, Paint(linea).apply { strokeWidth = g * 1.4f })
                    else -> c.drawLine(x + largo * 0.25f, y0, x - largo * 0.15f, y0 + largo, linea)
                }
            }
        }
        when (i) {
            IconoTiempo.DESPEJADO -> astro(cx, cy, s * 0.3f)
            IconoTiempo.POCAS_NUBES -> { astro(cx - s * 0.1f, cy - s * 0.1f, s * 0.24f); nube(cx + s * 0.12f, cy + s * 0.14f, s * 0.5f) }
            IconoTiempo.NUBES -> { astro(cx - s * 0.16f, cy - s * 0.16f, s * 0.18f); nube(cx + s * 0.04f, cy + s * 0.06f, s * 0.72f) }
            IconoTiempo.CUBIERTO -> { nube(cx + s * 0.12f, cy - s * 0.12f, s * 0.5f); nube(cx - s * 0.04f, cy + s * 0.06f, s * 0.78f, relleno = true) }
            IconoTiempo.NIEBLA -> for (k in 0 until 4) { val y = cy - s * 0.24f + k * s * 0.16f; val o = if (k % 2 == 0) 0f else s * 0.1f; c.drawLine(r.left + s * 0.12f + o, y, r.right - s * 0.12f - (s * 0.1f - o), y, linea) }
            IconoTiempo.LLOVIZNA -> { nube(cx, cy - s * 0.1f, s * 0.78f); gotas(4, cy + s * 0.26f, s * 0.12f, puntos = true) }
            IconoTiempo.LLUVIA -> { nube(cx, cy - s * 0.12f, s * 0.78f); gotas(4, cy + s * 0.2f, s * 0.2f) }
            IconoTiempo.CHUBASCOS -> { astro(cx - s * 0.18f, cy - s * 0.2f, s * 0.17f); nube(cx + s * 0.04f, cy - s * 0.06f, s * 0.7f); gotas(3, cy + s * 0.24f, s * 0.17f) }
            IconoTiempo.NIEVE -> { nube(cx, cy - s * 0.12f, s * 0.78f); gotas(3, cy + s * 0.3f, s * 0.1f, copos = true) }
            IconoTiempo.TORMENTA -> {
                nube(cx, cy - s * 0.14f, s * 0.78f)
                val y0 = cy + s * 0.12f
                c.drawPath(Path().apply {
                    moveTo(cx + s * 0.06f, y0); lineTo(cx - s * 0.1f, y0 + s * 0.18f); lineTo(cx + s * 0.0f, y0 + s * 0.18f)
                    lineTo(cx - s * 0.08f, y0 + s * 0.36f); lineTo(cx + s * 0.12f, y0 + s * 0.12f); lineTo(cx + s * 0.02f, y0 + s * 0.12f); close()
                }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sol })
            }
        }
    }
}
