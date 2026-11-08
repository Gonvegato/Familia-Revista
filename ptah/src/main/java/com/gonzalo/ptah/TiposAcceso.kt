package com.gonzalo.ptah

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.text.Layout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 * Buscar y abrir: la barra del buscador (abre el buscador de serie, o el navegador que elijas) y los accesos directos (las apps que elijas
 * de las que tienes, o webs), con su icono, su nombre en versalitas o las dos cosas.
 */

/** Las apps que se pueden abrir (las del cajón de apps) y los navegadores. */
object Apps {
    data class App(val paquete: String, val nombre: String)

    fun lanzables(ctx: Context): List<App> = runCatching {
        val pm = ctx.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString()) }.distinctBy { it.paquete }.sortedBy { it.nombre.lowercase() }
    }.getOrDefault(emptyList())

    fun navegadores(ctx: Context): List<App> = runCatching {
        val pm = ctx.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.ejemplo.com")), 0)
            .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString()) }.distinctBy { it.paquete }.sortedBy { it.nombre.lowercase() }
    }.getOrDefault(emptyList())
}

/** Un acceso de la lista («app:<paquete>» o «web:<dirección>|<nombre>»). */
data class Acceso(val esWeb: Boolean, val destino: String, val nombre: String) {
    fun aTexto() = if (esWeb) "web:$destino|$nombre" else "app:$destino"
    companion object {
        fun de(t: String): Acceso? = when {
            t.startsWith("app:") -> Acceso(false, t.removePrefix("app:"), "")
            t.startsWith("web:") -> t.removePrefix("web:").let { r -> Acceso(true, r.substringBefore('|'), r.substringAfter('|', "")) }
            else -> null
        }
        /** «ejemplo.com» → «https://ejemplo.com». */
        fun direccion(t: String): String { val s = t.trim(); return if (s.contains("://")) s else "https://$s" }
        /** El nombre de una web sin poner: «www.elpais.com/…» → «elpais». */
        fun nombreWeb(url: String): String = Uri.parse(direccion(url)).host.orEmpty().removePrefix("www.").substringBefore('.').ifBlank { url }
    }
}

object Buscador : TipoWidget {
    override val id = "buscador"
    override val nombre = "Buscador"
    override val grupo = "Buscar y abrir"
    override val descripcion = "Una barra de revista para buscar en internet. Un toque y estás buscando."
    override val muestra = Tamanos.Celdas(4, 1)
    override val piezas = listOf("barra" to "La barra", "dentro" to "El fondo de la barra", "lupa" to "La lupa y el micrófono", "pista" to "El texto de la barra")
    override val partes = listOf("ante" to "El antetítulo", "lupa" to "La lupa")

    override fun opcionesDeSerie() = mapOf("texto" to "Buscar en internet", "voz" to true, "navegador" to "", "ante" to "Buscar")
    override fun accion(d: Diseno) = Accion.Buscar(d.texto("navegador"))
    override fun descripcionToque(d: Diseno) = "Buscar en internet"

    /** La lupa, centrada en ([cx], [cy]), de lado [t]. */
    internal fun lupa(l: Lienzo, cx0: Float, cy0: Float, t: Float, color: Int) {
        val r = t * 0.3f; val cx = cx0 - t * 0.08f; val cy = cy0 - t * 0.08f
        val p = l.trazo(color, max(1.1f, t * 0.09f)).apply { strokeCap = Paint.Cap.ROUND }
        l.c.drawCircle(cx, cy, r, p)
        l.c.drawLine(cx + r * 0.72f, cy + r * 0.72f, cx + r * 1.6f, cy + r * 1.6f, p)
    }

    /** El micrófono, centrado en ([mx], [my]), de alto [t]. */
    internal fun micro(l: Lienzo, mx: Float, my0: Float, t: Float, color: Int) {
        val p = l.trazo(color, max(1.1f, t * 0.08f)).apply { strokeCap = Paint.Cap.ROUND }
        val mw = t * 0.3f; val mh = t * 0.52f; val my = my0 - t * 0.08f
        l.c.drawRoundRect(RectF(mx - mw / 2, my - mh * 0.75f, mx + mw / 2, my + mh * 0.25f), mw / 2, mw / 2, l.lleno(color))
        l.c.drawArc(RectF(mx - mw, my - mh * 0.5f, mx + mw, my + mh * 0.45f), 20f, 140f, false, p)
        l.c.drawLine(mx, my + mh * 0.45f, mx, my + mh * 0.7f, p)
    }

    /** En pequeño (1×1): una moneda con la lupa. En vertical (1×2): una píldora de pie con la lupa arriba y el micrófono abajo. */
    private fun columna(l: Lienzo) {
        val a = l.area; val d = l.d
        val colIco = l.pieza("lupa", l.tinta)
        val dentro = l.pieza("dentro", 0)
        val voz = d.si("voz", true) && l.proporcion == Lienzo.Proporcion.VERTICAL
        val ancho = min(a.width(), if (voz) a.height() * 0.5f else a.height())
        val alto = if (voz) min(a.height(), ancho * 2.4f) else ancho
        val r = RectF(a.centerX() - ancho / 2, a.centerY() - alto / 2, a.centerX() + ancho / 2, a.centerY() + alto / 2)
        if (dentro != 0) l.c.drawRoundRect(r, ancho / 2, ancho / 2, l.lleno(dentro))
        l.c.drawRoundRect(RectF(r.left + 0.8f, r.top + 0.8f, r.right - 0.8f, r.bottom - 0.8f), ancho / 2, ancho / 2, l.trazo(l.pieza("barra", l.tinta), 1.5f))
        if (!voz) { lupa(l, r.centerX(), r.centerY(), ancho * 0.55f, colIco); return }
        val mitad = r.height() / 2
        lupa(l, r.centerX(), r.top + mitad / 2 + 2f, ancho * 0.55f, colIco)
        l.c.drawLine(r.left + ancho * 0.25f, r.centerY(), r.right - ancho * 0.25f, r.centerY(), l.trazo(Lienzo.conAlfa(l.pieza("barra", l.tinta), 110), 0.8f))
        micro(l, r.centerX(), r.bottom - mitad / 2, ancho * 0.6f, colIco)
        l.toque(RectF(r.left, r.centerY(), r.right, r.bottom), Accion.BuscarVoz, "Buscar hablando")
    }

    override fun pinta(l: Lienzo) {
        if (l.proporcion != Lienzo.Proporcion.NORMAL) { columna(l); return }
        val a = l.area; val e = l.e; val d = l.d
        var top = a.top
        val barH0 = min(a.height(), 52f)
        if (e.ve("ante") && d.texto("ante").isNotBlank() && a.height() >= barH0 + 26f) top += l.seccion(d.texto("ante"), a.left, top, a.width()) + 6f
        val barH = min(a.bottom - top, max(30f, min(52f, (a.bottom - top)))).coerceAtLeast(20f)
        val y = top + (a.bottom - top - barH) / 2
        val bar = RectF(a.left, y, a.right, y + barH)
        val rad = min(barH / 2, l.e.radio.coerceAtLeast(2f))
        val dentro = l.pieza("dentro", 0)
        if (dentro != 0) l.c.drawRoundRect(bar, rad, rad, l.lleno(dentro))
        l.c.drawRoundRect(RectF(bar.left + 0.7f, bar.top + 0.7f, bar.right - 0.7f, bar.bottom - 0.7f), rad, rad, l.trazo(l.pieza("barra", l.tinta), 1.4f))
        val ico = barH * 0.42f
        val colIco = l.pieza("lupa", l.tinta)
        var x = bar.left + barH * 0.32f
        if (e.ve("lupa")) {
            val r = ico * 0.36f; val cx = x + r; val cy = bar.centerY() - ico * 0.1f
            val p = l.trazo(colIco, max(1.1f, ico * 0.11f)).apply { strokeCap = Paint.Cap.ROUND }
            l.c.drawCircle(cx, cy, r, p)
            l.c.drawLine(cx + r * 0.72f, cy + r * 0.72f, cx + r * 1.55f, cy + r * 1.55f, p)
            x += ico + barH * 0.18f
        }
        val conVoz = d.si("voz", true) && bar.width() >= 120f
        val finTexto = bar.right - (if (conVoz) barH * 0.95f else barH * 0.3f)
        val pista = d.texto("texto").ifBlank { "Buscar en internet" }
        val tamP = min(16f, barH * 0.34f)
        for (t in listOf(pista, "Buscar")) {
            val h0 = l.altoLinea(t, l.fCursiva, tamP, finTexto - x)
            if (h0 > 0f) { l.linea(t, l.fCursiva, tamP, l.pieza("pista", l.gris), x, bar.centerY() - h0 / 2f, finTexto - x, Layout.Alignment.ALIGN_NORMAL); break }
        }
        if (conVoz) {
            // El micrófono (búsqueda por voz), con su propia zona
            val mx = bar.right - barH * 0.55f; val my = bar.centerY()
            val p = l.trazo(colIco, max(1.1f, ico * 0.11f)).apply { strokeCap = Paint.Cap.ROUND }
            val mw = ico * 0.36f; val mh = ico * 0.62f
            l.c.drawRoundRect(RectF(mx - mw / 2, my - mh * 0.75f, mx + mw / 2, my + mh * 0.25f), mw / 2, mw / 2, l.lleno(colIco))
            l.c.drawArc(RectF(mx - mw, my - mh * 0.5f, mx + mw, my + mh * 0.45f), 20f, 140f, false, p)
            l.c.drawLine(mx, my + mh * 0.45f, mx, my + mh * 0.68f, p)
            l.toque(RectF(bar.right - barH * 1.05f, bar.top, bar.right, bar.bottom), Accion.BuscarVoz, "Buscar hablando")
        }
    }

    @Composable override fun Editor(d: Diseno, cambia: (Diseno) -> Unit) {
        val ctx = LocalContext.current
        Campo("El texto de la barra", d.texto("texto"), "Buscar en internet") { cambia(d.con("texto", it)) }
        Campo("Antetítulo", d.texto("ante"), "Buscar") { cambia(d.con("ante", it)) }
        SiNo("Con micrófono", "Para buscar hablando.", d.si("voz", true)) { cambia(d.con("voz", it)) }
        val navs = remember { Apps.navegadores(ctx) }
        Elige("Al tocarla, se abre", listOf("" to "El buscador de serie") + navs.map { it.paquete to it.nombre }, d.texto("navegador")) { cambia(d.con("navegador", it)) }
    }
}

object Accesos : TipoWidget {
    override val id = "accesos"
    override val nombre = "Accesos directos"
    override val grupo = "Buscar y abrir"
    override val descripcion = "Tus apps y tus webs de siempre, con su icono, su nombre o las dos cosas. Un toque y estás dentro."
    override val muestra = Tamanos.Celdas(4, 1)
    override val piezas = listOf("nombres" to "Los nombres", "tapas" to "El fondo de los que no tienen icono", "inicial" to "La inicial")
    override val partes = listOf("ante" to "El antetítulo")
    val MOSTRAR = listOf("ambos" to "Icono y nombre", "icono" to "Solo el icono", "texto" to "Solo el nombre")

    override fun opcionesDeSerie() = mapOf("items" to emptyList<String>(), "mostrar" to "ambos", "ante" to "")

    fun accesos(d: Diseno) = d.lista("items").mapNotNull { Acceso.de(it) }

    internal fun nombreDe(l: Lienzo, a: Acceso) = if (a.esWeb) a.nombre.ifBlank { Acceso.nombreWeb(a.destino) } else l.datos.nombreApp(a.destino) ?: a.destino.substringAfterLast('.')

    override fun pinta(l: Lienzo) {
        val ar = l.area; val e = l.e; val d = l.d
        val lista = accesos(d)
        if (lista.isNotEmpty() && l.proporcion != Lienzo.Proporcion.NORMAL) { soloIconos(l, lista); return }
        var top = ar.top
        if (e.ve("ante") && d.texto("ante").isNotBlank() && ar.height() >= 90f) top += l.seccion(d.texto("ante"), ar.left, top, ar.width()) + 6f
        val a = RectF(ar.left, top, ar.right, ar.bottom)
        if (lista.isEmpty()) {
            val t = l.titular("Elige tus apps", a.width(), a.height() * 0.6f, 22f, 10f, 2)
            l.pon(t, a.left, a.top + (a.height() - t.height) / 2)
            return
        }
        val modo = d.texto("mostrar", "ambos")
        if (modo == "texto") { indice(l, a, lista); return }
        // La rejilla que deja los iconos más grandes
        val n = lista.size
        var etiqueta = if (modo == "ambos") 12f * l.k else 0f
        var mejor = 1; var lado = 0f
        for (cols in 1..n) {
            val filas = ceil(n / cols.toFloat()).toInt()
            val s = min(a.width() / cols, a.height() / filas - etiqueta)
            if (s > lado) { lado = s; mejor = cols }
        }
        val cols = mejor; val filas = ceil(n / cols.toFloat()).toInt()
        val cw = a.width() / cols; val ch = a.height() / filas
        // Los nombres, solo si caben todos enteros (si no, solo los iconos)
        val tamNombre = if (etiqueta > 0f) lista.map { l.cabe(nombreDe(l, it).uppercase(java.util.Locale("es", "ES")), l.fNegrita, 7.5f * l.k, cw - 4f, 0.1f) }.let { t -> if (t.any { it == null }) null else t.filterNotNull().minOrNull() } else null
        if (tamNombre == null) etiqueta = 0f
        val ico = (min(cw, ch - etiqueta) * 0.74f).coerceAtLeast(8f)
        lista.forEachIndexed { i, acc ->
            val cx = a.left + (i % cols) * cw + cw / 2
            val y0 = a.top + (i / cols) * ch + (ch - ico - etiqueta) / 2
            val r = RectF(cx - ico / 2, y0, cx + ico / 2, y0 + ico)
            icono(l, acc, r)
            val nombre = nombreDe(l, acc)
            if (etiqueta > 0f && tamNombre != null) l.pon(l.capa(nombre.uppercase(java.util.Locale("es", "ES")), l.tp(l.fNegrita, tamNombre, l.pieza("nombres", l.tinta), 0.1f), cw - 2f, 1, al = Layout.Alignment.ALIGN_CENTER), cx - cw / 2 + 1f, r.bottom + 3f)
            l.toque(RectF(cx - cw / 2, a.top + (i / cols) * ch, cx + cw / 2, a.top + (i / cols + 1) * ch), accion(acc), "Abrir $nombre")
        }
    }

    /** En pequeño (1×1, en rejilla) o de pie (1×2, en columna): solo los iconos, tan grandes como quepan. */
    private fun soloIconos(l: Lienzo, lista: List<Acceso>) {
        val a = l.area
        val n = lista.size
        val cols = if (l.proporcion == Lienzo.Proporcion.VERTICAL) 1 else ceil(kotlin.math.sqrt(n.toDouble())).toInt()
        val filas = ceil(n / cols.toFloat()).toInt()
        val cw = a.width() / cols; val ch = a.height() / filas
        val ico = min(cw, ch) * 0.86f
        lista.forEachIndexed { i, acc ->
            val cx = a.left + (i % cols) * cw + cw / 2; val cy = a.top + (i / cols) * ch + ch / 2
            icono(l, acc, RectF(cx - ico / 2, cy - ico / 2, cx + ico / 2, cy + ico / 2))
            l.toque(RectF(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2), accion(acc), "Abrir ${nombreDe(l, acc)}")
        }
    }

    internal fun accion(a: Acceso) = if (a.esWeb) Accion.Web(Acceso.direccion(a.destino)) else Accion.App(a.destino)

    /** Solo los nombres: como el índice de una revista, uno por línea, con su raya. */
    private fun indice(l: Lienzo, a: RectF, lista: List<Acceso>) {
        val filas = lista.size
        val ch = a.height() / filas
        val cols = if (ch < 22f && lista.size > 2) 2 else 1
        val porCol = ceil(filas / cols.toFloat()).toInt()
        val alto = a.height() / porCol
        val cw = a.width() / cols
        lista.forEachIndexed { i, acc ->
            val col = i / porCol; val fila = i % porCol
            val x = a.left + col * cw; val y = a.top + fila * alto
            val t = l.titular("${nombreDe(l, acc)} →", cw - 6f, alto * 0.8f, min(22f, alto * 0.62f), 6f, 2, l.pieza("nombres", l.tinta), al = Layout.Alignment.ALIGN_NORMAL)
            l.pon(t, x, y + (alto - t.height) / 2)
            if (fila < porCol - 1) l.filete(y + alto - 0.4f, 0.6f, l.pieza("rayas", Lienzo.conAlfa(l.tinta, 90)), x, x + cw - 6f)
            l.toque(RectF(x, y, x + cw, y + alto), accion(acc), "Abrir ${nombreDe(l, acc)}")
        }
    }

    /** El icono de la app (o, si no está en este móvil o es una web, su inicial sobre una tapa). */
    internal fun icono(l: Lienzo, a: Acceso, r: RectF) {
        val rad = r.width() * (l.e.radio / 22f).coerceIn(0f, 1f) * 0.24f
        val bm = if (a.esWeb) null else l.datos.iconoApp(a.destino)
        if (bm != null) { l.foto(bm, r, rad); return }
        l.c.drawRoundRect(r, rad, rad, l.lleno(l.pieza("tapas", Lienzo.mezcla(l.papelDeVerdad(), l.tinta, 0.1f))))
        val ini = nombreDe(l, a).trim().firstOrNull()?.uppercase() ?: "·"
        val p = l.tp(l.fTitular, r.height() * 0.5f, l.pieza("inicial", l.acento)).apply { textAlign = Paint.Align.CENTER }
        val fm = p.fontMetrics
        l.c.drawText(ini, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, p)
        if (a.esWeb) {
            // La esquina doblada de las webs
            val q = r.width() * 0.22f
            l.c.drawPath(Path().apply { moveTo(r.right - q, r.top); lineTo(r.right, r.top + q); lineTo(r.right - q, r.top + q); close() }, l.lleno(Lienzo.conAlfa(l.tinta, 70)))
        }
    }

    @Composable override fun Editor(d: Diseno, cambia: (Diseno) -> Unit) {
        val ctx = LocalContext.current
        val lista = accesos(d)
        fun pon(nueva: List<Acceso>) = cambia(d.con("items", nueva.map { it.aTexto() }))
        Elige("Cómo se ven", MOSTRAR, d.texto("mostrar", "ambos")) { cambia(d.con("mostrar", it)) }
        Campo("Antetítulo", d.texto("ante"), "Sin antetítulo") { cambia(d.con("ante", it)) }
        Etiqueta("Tus accesos", Modifier.padding(top = 12.dp))
        if (lista.isEmpty()) Nota("Todavía ninguno.")
        val nombres = remember(lista) { Apps.lanzables(ctx).associate { it.paquete to it.nombre } }
        lista.forEachIndexed { i, a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (a.esWeb) a.nombre.ifBlank { Acceso.nombreWeb(a.destino) } else nombres[a.destino] ?: a.destino, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text((if (a.esWeb) a.destino else "App").uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (i > 0) EnlaceRevista("↑", Modifier.padding(horizontal = 8.dp)) { pon(lista.toMutableList().also { m -> m.add(i - 1, m.removeAt(i)) }) }
                EnlaceRevista("Quitar", Modifier.padding(start = 6.dp)) { pon(lista.filterIndexed { j, _ -> j != i }) }
            }
            Filete(1.dp, Line)
        }
        var eligeApp by remember { mutableStateOf(false) }
        var web by remember { mutableStateOf(false) }
        MagLink("+ Añadir una app", accent = true) { eligeApp = true }
        MagLink("+ Añadir una web") { web = !web }
        if (web) {
            var url by remember { mutableStateOf("") }
            var nombre by remember { mutableStateOf("") }
            Campo("La dirección", url, "elpais.com", teclado = androidx.compose.ui.text.input.KeyboardType.Uri) { url = it }
            Campo("Su nombre", nombre, "El que quieras") { nombre = it }
            MagLink("Añadirla", accent = true) { if (url.isNotBlank()) { pon(lista + Acceso(true, url.trim(), nombre.trim())); web = false } }
        }
        if (eligeApp) ElegirApp(alElegir = { p -> pon(lista + Acceso(false, p, "")); eligeApp = false }, alCerrar = { eligeApp = false })
    }
}

/** La lista de tus apps, con un buscador, para elegir una. */
@Composable
fun ElegirApp(alElegir: (String) -> Unit, alCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val todas = remember { Apps.lanzables(ctx) }
    var filtro by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCerrar, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Elige una app", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Campo("Buscar", filtro, "Su nombre") { filtro = it }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(todas.filter { filtro.isBlank() || it.nombre.contains(filtro, ignoreCase = true) }) { a ->
                        Text(a.nombre, fontFamily = Display, fontSize = 19.sp, color = Charcoal, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable { alElegir(a.paquete) }.padding(vertical = 10.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(alCerrar) { Text("CERRAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}
