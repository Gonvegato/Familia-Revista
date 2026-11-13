package com.gonzalo.familia.pc.revista

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.rememberImagenWeb
import com.gonzalo.familia.pc.rememberPortada

/*
 * Lo común de las pantallas de escritorio, con el aspecto de revista de las apps del móvil: la columna de la izquierda (la cabecera
 * de la app y sus secciones), la portada de una cosa y una página con su barra de desplazamiento.
 */

/** Una sección de la columna de la izquierda. [grupo]: un antetítulo encima (si cambia respecto a la anterior). */
class Entrada(val texto: String, val cuenta: Int? = null, val grupo: String? = null)

/** La columna de la izquierda: la cabecera de la app, sus secciones y, abajo, cómo va la cuenta. */
@Composable
fun Lateral(nombre: String, entradas: List<Entrada>, sel: Int, modifier: Modifier = Modifier, onSel: (Int) -> Unit) {
    val scroll = rememberScrollState()
    Column(modifier.width(236.dp).fillMaxHeight().background(Crudo)) {
        Column(Modifier.padding(start = 22.dp, end = 18.dp, top = 26.dp, bottom = 6.dp)) {
            Cabecera(40.sp, texto = nombre.uppercase())
            FileteCabecera()
        }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 10.dp, vertical = 8.dp)) {
            var grupo: String? = null
            entradas.forEachIndexed { i, e ->
                if (e.grupo != null && e.grupo != grupo) {
                    grupo = e.grupo
                    Spacer(Modifier.height(14.dp))
                    Antetitulo(e.grupo, Modifier.padding(horizontal = 12.dp), tam = 11.sp)
                    Spacer(Modifier.height(4.dp))
                }
                val elegida = i == sel
                val hover = remember { MutableInteractionSource() }
                val encima by hover.collectIsHoveredAsState()
                // (el fondo y la marca cambian con una transición corta: la columna no da saltos)
                val fondo by androidx.compose.animation.animateColorAsState(if (elegida) Sand else if (encima) lerp(Crudo, Sand, 0.5f) else Crudo.copy(alpha = 0f), androidx.compose.animation.core.tween(160))
                val marca by androidx.compose.animation.animateColorAsState(if (elegida) Acento else Acento.copy(alpha = 0f), androidx.compose.animation.core.tween(200))
                Row(
                    Modifier.fillMaxWidth().clip(esquina(6.dp)).background(fondo)
                        .hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable { onSel(i) }.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(3.dp).height(18.dp).background(marca))
                    Spacer(Modifier.width(10.dp))
                    Text(e.texto, Modifier.weight(1f), color = Charcoal, fontFamily = Display, fontSize = 17.sp,
                        fontWeight = if (elegida) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (e.cuenta != null) Text(e.cuenta.toString(), color = if (elegida) Acento else Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        // Las hermanas de este ordenador: un clic y se abre (o sale al frente si ya estaba abierta)
        Hermanas(LaFamilia.app)
        // Cómo va la cuenta
        LaFamilia.cambios
        val estado = LaFamilia.estado
        Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
            Filete(1.dp, Line)
            Spacer(Modifier.height(8.dp))
            Text(LaFamilia.usuario.ifBlank { "Sin cuenta" }, color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(estado.ifBlank { "Conectando…" }, color = if (estado.startsWith("Sin conexión")) Acento else Muted, fontFamily = Body, fontSize = 11.sp, maxLines = 2)
        }
    }
}

/** Las apps de la familia (las instaladas en este ordenador), con la de ahora marcada: como el cambio entre hermanas del móvil. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Hermanas(actual: String, modifier: Modifier = Modifier) {
    val aqui = remember { com.gonzalo.familia.pc.HermanasPc.todas.filter { (a, _) -> a == actual || com.gonzalo.familia.pc.HermanasPc.esta(a) } }
    if (aqui.size < 2) return
    Column(modifier.padding(horizontal = 22.dp).padding(top = 6.dp)) {
        Filete(1.dp, Line)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            aqui.forEach { (a, n) ->
                val esta = a == actual
                val hover = remember { MutableInteractionSource() }
                val encima by hover.collectIsHoveredAsState()
                Text(n.uppercase(), color = if (esta) Acento else if (encima) Charcoal else Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                    modifier = if (esta) Modifier else Modifier.hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable { com.gonzalo.familia.pc.HermanasPc.abre(a) })
            }
        }
    }
}

/** Una página con barra de desplazamiento a la derecha y el ancho de lectura de una revista. */
@Composable
fun Pagina(modifier: Modifier = Modifier, ancho: Dp = 1440.dp, scroll: ScrollState = rememberScrollState(), contenido: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = ancho).fillMaxWidth().padding(horizontal = 48.dp).padding(top = 30.dp, bottom = 48.dp), content = contenido)
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
    }
}

/**
 * La portada de una cosa: la imagen de la cuenta ([hash], la misma en todas las apps), o la de internet ([url]); si no hay ninguna,
 * su título compuesto como la tapa de un libro.
 */
@Composable
fun PortadaCosa(hash: String, url: String, titulo: String, modifier: Modifier = Modifier, deUid: String? = null, pie: String = "") {
    val img: ImageBitmap? = if (hash.isNotBlank()) rememberPortada(hash, deUid) ?: (if (url.isNotBlank()) rememberImagenWeb(url) else null) else if (url.isNotBlank()) rememberImagenWeb(url) else null
    val forma = esquina(6.dp)
    Box(modifier.aspectRatio(2f / 3f).clip(forma).background(Sand).border(1.dp, Line, forma)) {
        if (img != null) Image(img, titulo, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.width(22.dp).height(3.dp).background(Acento))
            Text(titulo, color = Charcoal, fontFamily = Display, fontSize = 17.sp, lineHeight = 19.sp, fontStyle = FontStyle.Italic, maxLines = 6, overflow = TextOverflow.Ellipsis)
            Text(pie.uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, letterSpacing = 1.5.sp, maxLines = 1)
        }
    }
}

/** Un número grande con su etiqueta debajo (las cifras de las portadas). */
@Composable
fun Cifra(valor: String, etiqueta: String, modifier: Modifier = Modifier, acento: Boolean = false) {
    Column(modifier) {
        Text(valor, color = if (acento) Acento else Charcoal, fontFamily = Grotesca, fontSize = 44.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp)
        Text(etiqueta.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Un aviso vacío («Aún no hay nada aquí»), centrado y discreto. */
@Composable
fun Vacio(titulo: String, texto: String = "", modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(titulo, color = Charcoal, fontFamily = Display, fontSize = 26.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
        if (texto.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(texto, color = Muted, fontFamily = Body, fontSize = 14.sp, textAlign = TextAlign.Center) }
    }
}
