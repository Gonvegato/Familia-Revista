package com.gonzalo.mostaza.pc

import androidx.compose.foundation.Image
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.gonzalo.familia.pc.rememberPortada
import com.gonzalo.familia.pc.revista.*

/*
 * Las piezas de las pantallas de Mostaza, con el aspecto de revista de la familia (el mismo que Mostaza del móvil): pósteres, filas que se
 * deslizan, el destacado de la portada, la barra de lo visto. Todo lo que se pulsa se puede pulsar también con el teclado o el mando: se
 * enfoca con las flechas (o la cruceta), se ve enfocado (un marco del color del acento) y se pulsa con Intro (o A).
 */

/** Un marco del acento cuando está enfocado con el teclado o el mando (con el ratón no hace falta: ya se ve dónde está). */
@Composable
fun Modifier.marcoFoco(fuente: MutableInteractionSource, forma: androidx.compose.ui.graphics.Shape = esquina(6.dp)): Modifier {
    val enfocado by fuente.collectIsFocusedAsState()
    return if (enfocado) this.border(3.dp, Acento, forma) else this
}

/** Lo que se pulsa: con el ratón (la mano), con el teclado (Intro) y con el mando (A), con su marco al enfocarlo. */
@Composable
fun Modifier.pulsable(enabled: Boolean = true, forma: androidx.compose.ui.graphics.Shape = esquina(6.dp), alPulsar: () -> Unit): Modifier {
    val f = remember { MutableInteractionSource() }
    return this.marcoFoco(f, forma).pointerHoverIcon(PointerIcon.Hand).clickable(f, null, enabled = enabled, onClick = alPulsar)
}

/** Una imagen de la cuenta ([hash]) o de internet ([url]); si no hay ninguna, el título compuesto como una portada de revista. */
@Composable
fun ImagenCosa(hash: String, url: String, titulo: String, modifier: Modifier = Modifier, pie: String = "") {
    val img = if (hash.isNotBlank()) rememberPortada(hash) ?: (if (url.isNotBlank()) rememberImagenWeb(url) else null) else if (url.isNotBlank()) rememberImagenWeb(url) else null
    Box(modifier.background(Sand)) {
        if (img != null) Image(img, titulo, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.width(22.dp).height(3.dp).background(Acento))
            Text(titulo, color = Charcoal, fontFamily = Display, fontSize = 16.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic, maxLines = 6, overflow = TextOverflow.Ellipsis)
            Text(pie.uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, letterSpacing = 1.5.sp, maxLines = 1)
        }
    }
}

/** La barra fina de lo visto. */
@Composable
fun Barra(f: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Line)) { Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
}

/** Un póster: la imagen, el título y una línea debajo (y, si se está viendo, lo visto). */
@Composable
fun TarjetaPoster(hash: String, url: String, nombre: String, ancho: Dp, sub: String = "", progreso: Float? = null, modifier: Modifier = Modifier, alAbrir: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val encima by hover.collectIsHoveredAsState()
    val foco = remember { MutableInteractionSource() }
    val enfocado by foco.collectIsFocusedAsState()
    val forma = esquina(4.dp)
    Column((if (ancho == Dp.Unspecified) modifier.fillMaxWidth() else modifier.width(ancho)).hoverable(hover).pointerHoverIcon(PointerIcon.Hand)
        .clickable(foco, null, onClick = alAbrir)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).graphicsLayer { val e = if (encima || enfocado) 1.03f else 1f; scaleX = e; scaleY = e }
            .clip(forma).border(if (enfocado) 3.dp else 1.dp, if (enfocado) Acento else Line, forma)) {
            ImagenCosa(hash, url, nombre, Modifier.fillMaxSize())
            if (progreso != null && progreso > 0f) Barra(progreso, Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.height(7.dp))
        Text(nombre, color = Charcoal, fontFamily = Grotesca, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        if (sub.isNotBlank()) Text(sub, color = Muted, fontFamily = Body, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Una fila de pósteres con su antetítulo (y «Ver todo →»), que se desliza de lado (con la rueda, la barra de abajo o las flechas). */
@Composable
fun FilaPosters(titulo: String, cosas: List<MetaBreve>, alAbrir: (MetaBreve) -> Unit, verTodo: (() -> Unit)? = null, sub: (MetaBreve) -> String = { it.ano },
                progreso: (MetaBreve) -> Float? = { null }, portada: (MetaBreve) -> String = { "" }) {
    if (cosas.isEmpty()) return
    Spacer(Modifier.height(26.dp))
    Filete(2.dp)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Antetitulo(titulo, Modifier.weight(1f))
        if (verTodo != null) EnlaceRevista("Ver todo →", acento = true) { verTodo() }
    }
    val estado = rememberLazyListState()
    Column {
        LazyRow(state = estado, horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
            items(cosas, key = { it.tipo + "|" + it.id }) { b -> TarjetaPoster(portada(b), b.poster, b.nombre, 150.dp, sub(b), progreso(b)) { alAbrir(b) } }
        }
        HorizontalScrollbar(rememberScrollbarAdapter(estado), Modifier.fillMaxWidth())
    }
}

/** El destacado de la portada: la imagen grande de lo primero de «Tendencias», con su título; a la ficha al pulsarlo. */
@Composable
fun Destacado(b: MetaBreve, alAbrir: (MetaBreve) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2.6f).clip(esquina(3.dp)).border(1.dp, Line, esquina(3.dp)).pulsable(forma = esquina(3.dp)) { alAbrir(b) }) {
            ImagenCosa("", b.fondo.ifBlank { b.poster }, "", Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.85f))))
            Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                Text("DESTACADO · ${tipoNombre(b.tipo).uppercase()}", color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, maxLines = 1)
                Text(b.nombre, color = Color.White, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val datos = listOfNotNull(b.ano.ifBlank { null }, b.generos.take(2).joinToString(", ").ifBlank { null }, b.nota.ifBlank { null }?.let { "★ $it" })
                if (datos.isNotEmpty()) Text(datos.joinToString(" · "), color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontSize = 13.sp, maxLines = 1)
            }
        }
        if (b.descripcion.isNotBlank()) Text(b.descripcion, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Una nota en cursiva (lo que se dice cuando no hay nada, o mientras carga). */
@Composable
fun Nota(t: String, modifier: Modifier = Modifier) = Text(t, modifier, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp)

/** La cabecera de una página: antetítulo, el titular grande y su filete. */
@Composable
fun CabeceraPagina(antetitulo: String, titulo: String, volver: (() -> Unit)? = null, derecha: @Composable RowScope.() -> Unit = {}) {
    // Una pestaña de arriba (sin «volver»): su nombre ya está en la cabecera de tinta, así que aquí va pequeño, como en Seshat
    if (volver == null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            com.gonzalo.familia.pc.revista.TituloPagina(titulo)
            Spacer(Modifier.weight(1f))
            derecha()
        }
        Filete(2.dp)
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        VolverRevista(volver)
        Spacer(Modifier.weight(1f))
        derecha()
    }
    Antetitulo(antetitulo, Modifier.padding(top = 6.dp), color = Acento, conCuadro = false)
    Text(titulo, color = Charcoal, fontFamily = Grotesca, fontSize = 60.sp, lineHeight = 62.sp, letterSpacing = (-1.5).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Filete(3.dp)
}

/** Un campo de texto de la revista (las claves, oculto, con «Ver»). */
@Composable
fun CampoRevista(etiqueta: String, valor: String, pista: String = "", secreto: Boolean = false, modifier: Modifier = Modifier, alCambiar: (String) -> Unit) {
    var ver by remember { mutableStateOf(!secreto) }
    OutlinedTextField(valor, alCambiar, modifier.fillMaxWidth(), label = { Text(etiqueta) }, placeholder = { if (pista.isNotBlank()) Text(pista) }, singleLine = true,
        visualTransformation = if (ver) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { if (secreto) EnlaceRevista(if (ver) "Ocultar" else "Ver", Modifier.padding(end = 10.dp)) { ver = !ver } })
}

/**
 * Abre una dirección en el navegador. Primero por Java (`Desktop.browse`: en el Mac y en GNOME no pasa por la línea de órdenes de otro
 * programa, que cualquiera del ordenador puede leer: importa en las páginas de configurar un addon, que llevan tu clave del debrid); si no
 * se puede, con el programa del sistema (`xdg-open` / `open`).
 */
fun abreEnNavegador(url: String): Boolean {
    val porJava = runCatching {
        if (!java.awt.Desktop.isDesktopSupported()) return@runCatching false
        val d = java.awt.Desktop.getDesktop()
        if (d.isSupported(java.awt.Desktop.Action.BROWSE)) { d.browse(java.net.URI(url)); true } else false
    }.getOrDefault(false)
    return porJava || com.gonzalo.familia.pc.Sitio.abre(url)
}
