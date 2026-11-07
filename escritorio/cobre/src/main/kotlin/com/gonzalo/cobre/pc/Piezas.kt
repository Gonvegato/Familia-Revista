package com.gonzalo.cobre.pc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.rememberImagenWeb
import com.gonzalo.familia.pc.revista.*
import com.gonzalo.iptv.Programa
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * Las piezas de las pantallas de Cobre, con el aspecto de revista de la familia (el mismo que Cobre del móvil y que Mostaza del ordenador):
 * pósteres, logos, la barra de lo que va de programa. Todo lo que se pulsa se puede pulsar también con el teclado o el mando: se enfoca con
 * las flechas (o la cruceta), se ve enfocado (un marco del color del acento) y se pulsa con Intro (o A).
 */

/** Un marco del acento cuando está enfocado con el teclado o el mando (con el ratón no hace falta: ya se ve dónde está). */
@Composable
fun Modifier.marcoFoco(fuente: MutableInteractionSource, forma: Shape = esquina(6.dp)): Modifier {
    val enfocado by fuente.collectIsFocusedAsState()
    return if (enfocado) this.border(3.dp, Acento, forma) else this
}

/** Lo que se pulsa: con el ratón (la mano), con el teclado (Intro) y con el mando (A), con su marco al enfocarlo. */
@Composable
fun Modifier.pulsable(enabled: Boolean = true, forma: Shape = esquina(6.dp), alPulsar: () -> Unit): Modifier {
    val f = remember { MutableInteractionSource() }
    return this.marcoFoco(f, forma).pointerHoverIcon(PointerIcon.Hand).clickable(f, null, enabled = enabled, onClick = alPulsar)
}

/** Una imagen de internet ([url]: un póster, un logo); si no hay, el título compuesto como una portada de revista. */
@Composable
fun ImagenCosa(url: String, titulo: String, modifier: Modifier = Modifier, pie: String = "", ajuste: ContentScale = ContentScale.Crop) {
    val img = if (url.startsWith("http")) rememberImagenWeb(url) else null
    Box(modifier.background(Sand)) {
        if (img != null) Image(img, titulo, Modifier.fillMaxSize(), contentScale = ajuste)
        else Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.width(22.dp).height(3.dp).background(Acento))
            Text(titulo, color = Charcoal, fontFamily = Display, fontSize = 16.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic, maxLines = 6, overflow = TextOverflow.Ellipsis)
            Text(pie.uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, letterSpacing = 1.5.sp, maxLines = 1)
        }
    }
}

/** El logo de un canal (o sus iniciales, si no tiene). */
@Composable
fun Logo(url: String, nombre: String, tam: Dp = 44.dp) {
    val img = if (url.startsWith("http")) rememberImagenWeb(url) else null
    val forma = esquina(6.dp)
    Box(Modifier.size(tam).clip(forma).background(Sand).border(1.dp, Line, forma), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, nombre, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
        else Text(nombre.filter { it.isLetterOrDigit() }.take(2).uppercase(), color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (tam.value * 0.36f).sp)
    }
}

/** La barra fina de lo visto (o de lo que va de programa). */
@Composable
fun Barra(f: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Line)) { Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
}

/** Un póster: la imagen, el título y una línea debajo (y, si se está viendo, lo visto). */
@Composable
fun TarjetaPoster(url: String, nombre: String, ancho: Dp, sub: String = "", progreso: Float? = null, modifier: Modifier = Modifier, alAbrir: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val encima by hover.collectIsHoveredAsState()
    val foco = remember { MutableInteractionSource() }
    val enfocado by foco.collectIsFocusedAsState()
    val forma = esquina(4.dp)
    Column((if (ancho == Dp.Unspecified) modifier.fillMaxWidth() else modifier.width(ancho)).hoverable(hover).pointerHoverIcon(PointerIcon.Hand)
        .clickable(foco, null, onClick = alAbrir)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).graphicsLayer { val e = if (encima || enfocado) 1.03f else 1f; scaleX = e; scaleY = e }
            .clip(forma).border(if (enfocado) 3.dp else 1.dp, if (enfocado) Acento else Line, forma)) {
            ImagenCosa(url, nombre, Modifier.fillMaxSize())
            if (progreso != null && progreso > 0f) Barra(progreso, Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.height(7.dp))
        Text(nombre, color = Charcoal, fontFamily = Display, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        if (sub.isNotBlank()) Text(sub, color = Muted, fontFamily = Body, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Una nota en cursiva (lo que se dice cuando no hay nada, o mientras carga). */
@Composable
fun Nota(t: String, modifier: Modifier = Modifier) = Text(t, modifier, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp)

/** La cabecera de una página: antetítulo, el titular grande y su filete. */
@Composable
fun CabeceraPagina(antetitulo: String, titulo: String, volver: (() -> Unit)? = null, derecha: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (volver != null) VolverRevista(volver)
        Spacer(Modifier.weight(1f))
        derecha()
    }
    Antetitulo(antetitulo, Modifier.padding(top = 6.dp), color = Acento, conCuadro = false)
    Text(titulo, color = Charcoal, fontFamily = Display, fontSize = 56.sp, lineHeight = 58.sp, letterSpacing = (-1.5).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Filete(3.dp)
}

/** Una casilla de texto de la revista; las secretas (contraseñas y direcciones, que suelen llevarlas), tapadas, con «Ver» para comprobar lo escrito. */
@Composable
fun CampoRevista(etiqueta: String, valor: String, pista: String = "", secreto: Boolean = false, modifier: Modifier = Modifier, alCambiar: (String) -> Unit) {
    var ver by remember { mutableStateOf(!secreto) }
    OutlinedTextField(valor, alCambiar, modifier.fillMaxWidth(), label = { Text(etiqueta) }, placeholder = { if (pista.isNotBlank()) Text(pista) }, singleLine = true,
        visualTransformation = if (ver) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { if (secreto) EnlaceRevista(if (ver) "Tapar" else "Ver", Modifier.padding(end = 10.dp)) { ver = !ver } })
}

/** Lo que se emite ahora en un canal: el título, lo que va (una barra) y lo siguiente. */
@Composable
fun AhoraYDespues(ahora: Programa?, despues: Programa?, t: Long, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (ahora != null) {
            Text(ahora.titulo, color = Charcoal, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Barra(ahora.avance(t), Modifier.width(90.dp))
                Spacer(Modifier.width(8.dp))
                Text("hasta las ${Formato.hora(ahora.fin)}", color = Muted, fontFamily = Body, fontSize = 11.sp, maxLines = 1)
            }
        }
        if (despues != null) Text("Después, ${Formato.hora(despues.inicio)}: ${despues.titulo}", color = Muted, fontFamily = Body, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Fechas, horas y cifras, como en el resto de la familia: «16 oct 2026» en los textos, «21:30» las horas de la guía y las cifras con punto
 * de millar («4.010 canales»). Los meses van escritos aquí.
 */
object Formato {
    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")
    private val DIAS = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

    fun fecha(d: LocalDate): String = "${d.dayOfMonth} ${MESES[d.monthValue - 1]} ${d.year}"
    fun fecha(ms: Long): String = fecha(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate())
    fun hora(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
    /** «hoy», «mañana» o «sábado 8». */
    fun dia(ms: Long, hoy: LocalDate = LocalDate.now()): String {
        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
        return when (d) { hoy -> "hoy"; hoy.plusDays(1) -> "mañana"; hoy.minusDays(1) -> "ayer"; else -> "${DIAS[d.dayOfWeek.value - 1]} ${d.dayOfMonth}" }
    }
    /** «1 h 52 min», «45 min». */
    fun duracion(ms: Long): String { val m = ms / 60_000; return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }
    fun tiempo(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

    fun cifra(n: Long): String {
        val s = kotlin.math.abs(n).toString()
        val out = StringBuilder()
        s.forEachIndexed { i, ch -> if (i > 0 && (s.length - i) % 3 == 0) out.append('.'); out.append(ch) }
        return (if (n < 0) "-" else "") + out
    }
    fun cifra(n: Int): String = cifra(n.toLong())
}
