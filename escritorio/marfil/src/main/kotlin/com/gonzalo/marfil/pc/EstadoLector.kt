package com.gonzalo.marfil.pc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Los paneles del lector (a la derecha) y el de la letra. */
enum class Panel { INDICE, NOTAS, MARCADORES, BUSCAR, LETRA }

/** Los colores del lector con el tema que toca (los del móvil) y el acento de la app. */
class Colores(val papel: Color, val tinta: Color, val suave: Color, val superficie: Color, val acento: Color, val oscuro: Boolean) {
    val linea: Color get() = tinta.copy(alpha = 0.13f)
    val velo: Color get() = Color.Black.copy(alpha = if (oscuro) 0.5f else 0.32f)
}

/**
 * Lo seleccionado con el ratón en una sección: desde la palabra donde empezaste ([anclaBloque], letras [anclaDesde]–[anclaHasta]) hasta
 * donde está el ratón ([focoBloque], [focoLetra]), por palabras enteras como en el móvil.
 */
data class Seleccion(val seccion: Int, val anclaBloque: Int, val anclaDesde: Int, val anclaHasta: Int, val focoBloque: Int, val focoLetra: Int, val terminada: Boolean = false) {
    private val focoAntes get() = focoBloque < anclaBloque || (focoBloque == anclaBloque && focoLetra < anclaDesde)
    val desde: Pair<Int, Int> get() = if (focoAntes) focoBloque to focoLetra else anclaBloque to anclaDesde
    val hasta: Pair<Int, Int> get() = if (focoAntes) anclaBloque to anclaHasta else focoBloque to maxOf(focoLetra, if (focoBloque == anclaBloque) anclaHasta else 0)
    /** El tramo de un bloque que cae dentro (o null). */
    fun en(bloque: Int, largo: Int): IntRange? {
        val (b1, l1) = desde; val (b2, l2) = hasta
        if (bloque < b1 || bloque > b2) return null
        val s = if (bloque == b1) l1 else 0; val e = if (bloque == b2) l2 else largo
        return if (e > s) s until e else null
    }
}

/** Un subrayado (o una selección) con su menú abierto, y dónde (en la ventana) se abrió. */
class MenuTexto(val marca: Marca?, val seleccion: Seleccion?, val x: Float, val y: Float)

/** Un aviso del lector (abajo), con su botón si lo tiene. */
class AvisoLector(val texto: String, val boton: String = "", val accion: (() -> Unit)? = null, val hasta: Long = System.currentTimeMillis() + (if (accion != null) 6000 else 2200))

/** Una nota al pie abierta: su tipo, su texto y adónde lleva. */
class NotaAbierta(val nombre: String, val textos: List<AnnotatedString>, val enlace: Epub.Enlace, val x: Float, val y: Float)

/**
 * El estado del lector de un libro: por dónde vas, cómo se ve, lo que está abierto encima y las páginas de cada capítulo (para el tamaño de
 * ventana y los ajustes de ahora).
 */
class EstadoLector(val l: Libro, val libro: LibroAbierto, inicio: Sitio, val anclaInicial: Double) {
    val epub: Epub get() = libro.epub
    var ajustes by mutableStateOf(AjustesLectura.de(l.id))
    /** El principio de lo que se ve. */
    var sitio by mutableStateOf(inicio)
    /** La primera página que se ve en su capítulo (-1: aún sin calcular; se busca la del [sitio]). */
    var pagina by mutableStateOf(-1)
    /** Hacia dónde se pasó la última página (para la animación). */
    var direccion by mutableStateOf(1)
    var panel by mutableStateOf<Panel?>(null)
    var seleccion by mutableStateOf<Seleccion?>(null)
    var menu by mutableStateOf<MenuTexto?>(null)
    var editando by mutableStateOf<Marca?>(null)
    var nota by mutableStateOf<NotaAbierta?>(null)
    /** Volver a donde estabas tras un salto (índice, búsqueda, notas, la barra…), mientras no pases unas cuantas páginas. */
    var vuelta by mutableStateOf<Pair<Sitio, Double>?>(null)
    var pasosDesdeSalto = 0
    /** Lo encontrado al buscar, marcado unos segundos al llegar. */
    var destello by mutableStateOf<Pair<Sitio, Sitio>?>(null)
    var aviso by mutableStateOf<AvisoLector?>(null)
    /** Sin barras: solo el libro (tecla F). */
    var inmersivo by mutableStateOf(false)
    var busqueda by mutableStateOf("")
    var hallazgos by mutableStateOf<List<Hallazgo>?>(null)
    var buscando by mutableStateOf(false)
    /** Por dónde ibas leyendo de verdad (para saber si un movimiento es un salto largo, como en el móvil). */
    var ancla: Double = anclaInicial
    var preguntado = false
    /** Los capítulos ya en páginas, para [clave] (la letra, el tamaño, la ventana…). */
    val paginados = mutableStateMapOf<Int, Paginado>()
    var clave by mutableStateOf("")
    /** Cuántas páginas se ven a la vez (2: el libro abierto). */
    var porVista by mutableStateOf(1)
    /** Dónde empieza la zona del libro en la ventana (los menús que salen junto al texto se colocan con ella). */
    var origenArea = androidx.compose.ui.geometry.Offset.Zero

    /** La sección siguiente (o anterior) con algo que leer. */
    fun vecina(s: Int, dir: Int): Int? = libro.conTexto.let { c -> if (dir > 0) c.firstOrNull { it > s } else c.lastOrNull { it < s } }

    /** Lo leído de la sección que se ve (0–1): en páginas, la página; deslizando, las letras. */
    fun enSeccion(alFinal: Boolean = false): Double {
        val p = paginados[sitio.seccion]
        if (ajustes.flujo == "paginated" && p != null && pagina >= 0 && p.total > 0) return ((pagina + if (alFinal) porVista else 0).toDouble() / p.total).coerceIn(0.0, 1.0)
        return libro.fraccionEnSeccion(sitio)
    }

    /** El porcentaje del libro como lo guarda el móvil (el final de lo que se ve) y el del principio. */
    val fraccion: Double get() = libro.fraccion(sitio.seccion, enSeccion(alFinal = true))
    val fraccionInicio: Double get() = libro.fraccion(sitio.seccion, enSeccion())

    /** Ir a un sitio (la página en la que cae se busca al tenerla). */
    fun ve(x: Sitio, dir: Int = if (x >= sitio) 1 else -1) {
        direccion = dir
        sitio = x
        pagina = paginados[x.seccion]?.let { alinea(it.paginaDe(x.bloque, x.letra)) } ?: -1
    }

    /** Un salto (no es pasar página): con «volver a donde estabas». */
    fun saltaA(x: Sitio) {
        vuelta = sitio to fraccionInicio
        pasosDesdeSalto = 0
        ve(x)
    }

    fun alinea(p: Int) = if (porVista == 2) p - p % 2 else p

    fun siguiente(): Boolean {
        if (ajustes.flujo != "paginated") return false
        val p = paginados[sitio.seccion] ?: return false
        val actual = if (pagina >= 0) pagina else alinea(p.paginaDe(sitio.bloque, sitio.letra))
        direccion = 1
        if (actual + porVista < p.total) { pagina = actual + porVista; sitio = p.inicio(pagina)?.let { Sitio(sitio.seccion, it.first, it.second) } ?: sitio }
        else { val s = vecina(sitio.seccion, 1) ?: return false; sitio = Sitio(s, 0, 0); pagina = 0 }
        pasoDePagina()
        return true
    }

    fun anterior(): Boolean {
        if (ajustes.flujo != "paginated") return false
        val p = paginados[sitio.seccion] ?: return false
        val actual = if (pagina >= 0) pagina else alinea(p.paginaDe(sitio.bloque, sitio.letra))
        direccion = -1
        if (actual > 0) { pagina = alinea(actual - 1); sitio = p.inicio(pagina)?.let { Sitio(sitio.seccion, it.first, it.second) } ?: sitio }
        else {
            val s = vecina(sitio.seccion, -1) ?: return false
            val q = paginados[s]
            if (q != null) { pagina = alinea(q.total - 1); sitio = q.inicio(pagina)?.let { Sitio(s, it.first, it.second) } ?: Sitio(s, 0) }
            else { val bs = libro.bloques(s); sitio = Sitio(s, (bs.size - 1).coerceAtLeast(0), bs.lastOrNull()?.largo ?: 0); pagina = -1 }
        }
        pasoDePagina()
        return true
    }

    /** Principio o final del capítulo. */
    fun extremoDelCapitulo(alFinal: Boolean) {
        val p = paginados[sitio.seccion]
        if (!alFinal) ve(Sitio(sitio.seccion, 0, 0), -1)
        else if (p != null) { direccion = 1; pagina = alinea(p.total - 1); sitio = p.inicio(pagina)?.let { Sitio(sitio.seccion, it.first, it.second) } ?: sitio }
    }

    private fun pasoDePagina() {
        menu = null; seleccion = null
        if (vuelta != null && ++pasosDesdeSalto > 5) vuelta = null
    }

    fun avisa(t: String, boton: String = "", accion: (() -> Unit)? = null) { aviso = AvisoLector(t, boton, accion) }
}

/** El tamaño de la letra base del lector en el ordenador (al 100 %): algo más grande que en el móvil, que se lee más lejos. */
const val LETRA_BASE = 20f

/** El estilo de cada bloque con los ajustes del lector (los mismos nombres y valores que en el móvil). */
fun estiloDe(a: AjustesLectura, d: Density, b: Bloque, anterior: Bloque?): EstiloBloque {
    val base = LETRA_BASE * a.tamano / 100f
    val em = with(d) { base.sp.toPx() }
    val familia = Letras.de(a.letra, a.gruesa)
    fun ts(tam: Float = 1f, peso: FontWeight = FontWeight.Normal, al: TextAlign = if (a.justificado) TextAlign.Justify else TextAlign.Start,
           inter: Double = a.interlineado, sangria: Float = 0f, cursiva: Boolean = false) = TextStyle(
        fontFamily = familia, fontSize = (base * tam).sp, lineHeight = inter.em, fontWeight = peso, textAlign = al,
        fontStyle = if (cursiva) FontStyle.Italic else FontStyle.Normal,
        textIndent = if (sangria > 0f) TextIndent(firstLine = sangria.em) else TextIndent.None,
        hyphens = if (a.guiones) Hyphens.Auto else Hyphens.None,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    val aire = when (a.parrafos) { 0 -> 0.25f; 1 -> 0.6f; else -> 1.1f }
    return when (b.tipo) {
        Bloque.Tipo.PARRAFO -> {
            val trasParrafo = anterior?.tipo == Bloque.Tipo.PARRAFO
            EstiloBloque(ts(sangria = if (a.parrafos == 2 || !trasParrafo) 0f else 1.4f), antes = (if (anterior?.esTitulo == true) maxOf(aire, 1.1f) else aire) * em)
        }
        Bloque.Tipo.CITA -> EstiloBloque(ts(tam = 0.96f), izquierda = 1.6f * em, derecha = 1.0f * em, antes = 0.8f * em)
        Bloque.Tipo.TITULO1 -> EstiloBloque(ts(1.75f, FontWeight.SemiBold, TextAlign.Center, 1.2), antes = 2.2f * em)
        Bloque.Tipo.TITULO2 -> EstiloBloque(ts(1.4f, FontWeight.SemiBold, TextAlign.Center, 1.25), antes = 1.8f * em)
        Bloque.Tipo.TITULO3 -> EstiloBloque(ts(1.15f, FontWeight.Bold, TextAlign.Start, 1.3), antes = 1.4f * em)
        Bloque.Tipo.SEPARADOR -> EstiloBloque(ts(al = TextAlign.Center), antes = 1.0f * em)
        Bloque.Tipo.IMAGEN -> EstiloBloque(ts(), antes = 0.9f * em)
    }
}

/** El texto de un bloque tal como se pinta: los enlaces del acento y los subrayados al revés (el fondo de la tinta y la letra del papel, como en el móvil). */
fun textoPintado(b: Bloque, subrayados: List<IntRange>, c: Colores): AnnotatedString {
    val sb = AnnotatedString.Builder(b.texto)
    if (b.tipo == Bloque.Tipo.SEPARADOR) sb.addStyle(SpanStyle(color = c.suave), 0, b.largo)
    b.texto.getStringAnnotations(ETIQUETA_ENLACE, 0, b.largo).forEach { sb.addStyle(SpanStyle(color = c.acento), it.start, it.end) }
    subrayados.forEach { r -> if (r.last >= r.first) sb.addStyle(SpanStyle(color = c.papel, background = c.tinta), r.first.coerceIn(0, b.largo), (r.last + 1).coerceIn(0, b.largo)) }
    return sb.toAnnotatedString()
}
