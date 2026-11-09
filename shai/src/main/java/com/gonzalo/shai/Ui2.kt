package com.gonzalo.shai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/*
 * Las piezas de una red social: tarjetas con su línea fina, avatares de colores, burbujas, la barra de abajo solo con iconos, el botón
 * flotante. Los colores son los del tema (Paper, Charcoal, Muted, Acento…): negro de noche y blanco de día.
 */

/** El fondo de una tarjeta (un pelo más claro u oscuro que el papel). */
val FondoTarjeta: Color get() = Crudo
/** Las burbujas de los demás (un poco más que la tarjeta). */
val FondoBurbuja: Color get() = Sand
/** Lo urgente (lo que hay que decidir). */
val Coral = Color(0xFFF4212E)
/** El sello de la prensa (como el de una cuenta verificada). */
val AzulPrensa: Color get() = Acento
private val TintaAvatar = Color(0xFF14201A)
private val COLORES_AVATAR = listOf(0xFFC9A227, 0xFFD9A07B, 0xFFD98CA8, 0xFF8FC0A0, 0xFF7FB2D9, 0xFFC9B8E0, 0xFFE0C08F, 0xFFB4C4B8, 0xFFE89A8A, 0xFF9FD0C8)

/** Una tarjeta redonda; con [bordeIzq], una franja de color a la izquierda; con [borde], un marco. */
@Composable
fun Tarjeta(modifier: Modifier = Modifier, bordeIzq: Color? = null, borde: Color? = null, relleno: Dp = 14.dp, alHacer: (() -> Unit)? = null, contenido: @Composable ColumnScope.() -> Unit) {
    val forma = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().clip(forma).background(FondoTarjeta)
            .border(if (borde != null) 1.5.dp else 1.dp, borde ?: Line, forma)
            .let { m -> bordeIzq?.let { c -> m.drawBehind { drawRect(c, Offset.Zero, Size(4.dp.toPx(), size.height)) } } ?: m }
            .let { if (alHacer != null) it.tocable(onClick = alHacer) else it }
            .padding(start = if (bordeIzq != null) relleno + 4.dp else relleno, end = relleno, top = relleno, bottom = relleno),
        content = contenido
    )
}

/** Un rótulo de sección («Esta semana», «Por decidir · 2»). */
@Composable
fun Rotulo(t: String, modifier: Modifier = Modifier, color: Color = Acento) =
    Text(t, modifier, color = if (color == Acento) Charcoal else color, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 23.sp)

/** Las iniciales de un nombre («Toni Vidal» → «TV»; «el Niño» → «N»). */
fun iniciales(n: String): String {
    val todas = n.removePrefix("@").split(' ', '-').filter { it.isNotBlank() }
    val ps = todas.filter { it.lowercase() !in setOf("el", "la", "los", "las", "de", "del", "y") }
    // «Los del barrio» → «LB» (la primera y la última); «el Niño» → «N»
    val l = if (ps.size < 2 && todas.size >= 3) listOf(todas.first(), todas.last()) else ps
    return l.take(2).joinToString("") { it.first().uppercase() }.ifBlank { n.take(1).uppercase() }
}

/** El color de alguien: siempre el mismo para el mismo nombre. */
fun colorDe(n: String): Color = Color(COLORES_AVATAR[Math.floorMod(n.trim().lowercase().hashCode(), COLORES_AVATAR.size)])

@Composable
fun Avatar(nombre: String, tam: Dp = 42.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(tam).clip(CircleShape).background(colorDe(nombre)), contentAlignment = Alignment.Center) {
        Text(iniciales(nombre), color = TintaAvatar, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = (tam.value * 0.36f).sp, maxLines = 1)
    }
}

/** Un número en una píldora (lo que falta por ver). */
@Composable
fun Insignia(n: Int, modifier: Modifier = Modifier, color: Color = Acento) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 7.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
        Text(if (n > 99) "99+" else "$n", color = if (color == Acento) SobreAcento else Color.White, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp)
    }
}

/** Una etiqueta («DECIDE», «TIENES QUE DECIDIR»). */
@Composable
fun Sello(t: String, color: Color = Acento, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 9.dp, vertical = 2.dp)) {
        Text(t, color = if (color == Acento) SobreAcento else Color.White, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp)
    }
}

/** Un filtro en píldora. */
@Composable
fun ChipFiltro(t: String, on: Boolean, modifier: Modifier = Modifier, cuenta: Int? = null, alHacer: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(50)).background(if (on) Charcoal else Color.Transparent).border(1.dp, if (on) Charcoal else Edge, RoundedCornerShape(50))
        .tocable(onClick = alHacer).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(t, color = if (on) Paper else Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)
        if (cuenta != null && cuenta > 0) { Spacer(Modifier.width(6.dp)); Insignia(cuenta, color = if (on) Charcoal else Acento) }
    }
}

/** «hace 2 h», «ayer», «hace 3 días». */
fun hace(t: Long, ahora: Long = System.currentTimeMillis()): String {
    val m = ((ahora - t) / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "ahora"
        m < 60 -> "hace $m min"
        m < 24 * 60 -> "hace ${m / 60} h"
        m < 48 * 60 -> "ayer"
        m < 30 * 24 * 60 -> "hace ${m / (24 * 60)} días"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale("es", "ES")).format(java.util.Date(t))
    }
}

// ---------------------------------------------------------------- la barra de abajo

/** Las cinco de abajo: el feed, explorar (la portada, lo de esta semana, los récords), las ofertas, los mensajes y el perfil. */
fun itemsNav(mensajes: Int, ofertas: Int) = listOf(
    ItemNav(Icons.Outlined.Home, "Inicio", iconoOn = Icons.Filled.Home), ItemNav(Icons.Outlined.Search, "Explorar", iconoOn = Icons.Filled.Search),
    ItemNav(Icons.Outlined.Notifications, "Ofertas y avisos", ofertas, Icons.Filled.Notifications), ItemNav(Icons.Outlined.MailOutline, "Mensajes", mensajes, Icons.Filled.Mail),
    ItemNav(Icons.Outlined.Person, "Perfil", iconoOn = Icons.Filled.Person))

class ItemNav(val icono: ImageVector, val nombre: String, val cuenta: Int = 0, val iconoOn: ImageVector = icono)

/** La barra de abajo, como la de una red social: solo iconos (el nombre, para la accesibilidad), con su número si hay algo nuevo. */
@Composable
fun BarraNav(items: List<ItemNav>, sel: Int, modifier: Modifier = Modifier, alElegir: (Int) -> Unit) {
    val linea = Line
    Row(modifier.fillMaxWidth().background(Paper).drawBehind { drawRect(linea, Offset.Zero, Size(size.width, 1.dp.toPx())) }
        .navigationBarsPadding().padding(top = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceAround) {
        items.forEachIndexed { i, it ->
            val on = i == sel
            Box(Modifier.weight(1f).tocable { alElegir(i) }.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                Box {
                    Icon(if (on) it.iconoOn else it.icono, it.nombre, tint = Charcoal, modifier = Modifier.size(27.dp))
                    if (it.cuenta > 0) Insignia(it.cuenta, Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-6).dp))
                }
            }
        }
    }
}

/** El botón flotante «+» (meter el partido), abajo a la derecha. */
@Composable
fun BotonFlotante(descripcion: String, modifier: Modifier = Modifier, alHacer: () -> Unit) {
    Box(modifier.size(58.dp).clip(CircleShape).background(Acento).tocable(onClick = alHacer).semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(30.dp))
    }
}

// ---------------------------------------------------------------- la red

/** ¿Es de un medio? (el sello azul) */
fun Post.esPrensa(): Boolean = tipo == "prensa"

@Composable
private fun Cifra(icono: ImageVector, n: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icono, null, tint = Muted, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(n, color = Muted, fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, softWrap = false)
    }
}

/** El nombre, el sello de prensa y el @usuario · cuándo. En dos líneas si no cabe en una. */
@Composable
private fun QuienPost(p: Post, cuando: String, tam: Int = 15) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.autor.ifBlank { p.usuario }, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = tam.sp, lineHeight = (tam + 4).sp, modifier = Modifier.weight(1f, fill = false))
            if (p.esPrensa()) { Spacer(Modifier.width(5.dp)); Box(Modifier.size(13.dp).clip(CircleShape).background(AzulPrensa)) }
        }
        Text(listOfNotNull(p.usuario.ifBlank { null }, cuando.ifBlank { null }).joinToString(" · "), color = Muted, fontFamily = Body, fontSize = (tam - 2).sp, lineHeight = (tam + 2).sp)
    }
}

/** Una respuesta, debajo de su post. */
@Composable
private fun Respuesta(r: Post) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Avatar(r.autor.ifBlank { r.usuario }, 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.autor.ifBlank { r.usuario }, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f, fill = false))
                if (r.usuario.isNotBlank()) Text("  " + r.usuario, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            Text(r.texto, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 1.dp))
            Cifra(Icons.Outlined.FavoriteBorder, corto(r.megusta), Modifier.padding(top = 3.dp))
        }
    }
}

/** Un post como en X: avatar, nombre, @usuario · cuándo, el texto, lo que cita, las cifras con iconos y sus respuestas (que se despliegan). */
@Composable
fun PostX(p: Post, cuando: String, abierto0: Boolean = false, destacado: Boolean = false) {
    var abierto by rememberSaveable(p.usuario + p.texto.take(40)) { mutableStateOf(abierto0) }
    Row(Modifier.fillMaxWidth().padding(vertical = if (destacado) 4.dp else 12.dp)) {
        if (!destacado) { Avatar(p.autor.ifBlank { p.usuario }); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            if (destacado) Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.autor.ifBlank { p.usuario }); Spacer(Modifier.width(12.dp)); QuienPost(p, cuando)
            } else QuienPost(p, cuando)
            Text(p.texto, color = Charcoal, fontFamily = Body, fontSize = if (destacado) 16.sp else 15.sp, lineHeight = if (destacado) 23.sp else 21.sp, modifier = Modifier.padding(top = if (destacado) 8.dp else 4.dp))
            p.cita?.let { c ->
                Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(10.dp)) {
                    QuienPost(c, "", tam = 13)
                    Text(c.texto, color = Charcoal, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Row(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Cifra(Icons.Outlined.ChatBubbleOutline, corto(p.respuestas.size + p.megusta / 90))
                Cifra(Icons.Outlined.Repeat, corto(p.reposts))
                Cifra(Icons.Outlined.FavoriteBorder, corto(p.megusta))
            }
            if (p.respuestas.isNotEmpty()) {
                val linea = Line
                if (abierto || destacado) Column(Modifier.padding(top = 4.dp).drawBehind { drawRect(linea, Offset.Zero, Size(2.dp.toPx(), size.height)) }.padding(start = 12.dp)) {
                    p.respuestas.forEach { Respuesta(it) }
                }
                if (!destacado) Text(if (abierto) "Ocultar las respuestas" else "Ver ${if (p.respuestas.size == 1) "la respuesta" else "las ${p.respuestas.size} respuestas"}",
                    color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 8.dp).tocable { abierto = !abierto })
            }
        }
    }
}

/** Un separador centrado («JORNADA 3 · TRAS EL 3-1 AL SEVILLA FC»). */
@Composable
fun Separador(t: String, modifier: Modifier = Modifier) =
    Text(t, modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), color = Muted, fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 13.sp,
        lineHeight = 17.sp, textAlign = TextAlign.Center)

// ---------------------------------------------------------------- los mensajes

/** Una burbuja: las tuyas a la derecha, en el acento; las de los demás a la izquierda (con su nombre si es un grupo). */
@Composable
fun Burbuja(texto: String, mia: Boolean, quien: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = if (mia) Alignment.End else Alignment.Start) {
        val forma = if (mia) RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 5.dp)
        Column(Modifier.fillMaxWidth(0.82f).wrapContentWidth(if (mia) Alignment.End else Alignment.Start).clip(forma).background(if (mia) Acento else FondoBurbuja)
            .padding(horizontal = 13.dp, vertical = 9.dp)) {
            if (quien != null) Text(quien, color = colorDe(quien), fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp)
            Text(texto, color = if (mia) SobreAcento else Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
}

/** Un aviso del sistema en medio de un chat («No contestaste…», «Elegiste…»). */
@Composable
fun AvisoChat(titulo: String, texto: String, color: Color = Muted) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(titulo, color = color, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp, textAlign = TextAlign.Center)
        if (texto.isNotBlank()) Text(texto, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Un botón con marco, con el texto entero (las opciones de una decisión). */
@Composable
fun BotonMarco(t: String, modifier: Modifier = Modifier, alHacer: () -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).border(1.dp, Edge, RoundedCornerShape(22.dp)).tocable(onClick = alHacer)
        .padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text(t, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
    }
}

/** Un botón lleno (el acento) o con marco. */
@Composable
fun BotonRedondo(t: String, modifier: Modifier = Modifier, lleno: Boolean = true, alHacer: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(50)).let { if (lleno) it.background(Charcoal) else it.border(1.dp, Edge, RoundedCornerShape(50)) }
        .tocable(onClick = alHacer).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(t, color = if (lleno) Paper else Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
    }
}

/** Un dato grande en una tarjeta (los contadores del Inicio, las cifras de una oferta). */
@Composable
fun Contador(valor: String, nombre: String, modifier: Modifier = Modifier, destacado: Boolean = false, tamNombre: androidx.compose.ui.unit.TextUnit = 12.sp, alHacer: (() -> Unit)? = null) {
    Tarjeta(modifier, relleno = 12.dp, alHacer = alHacer) {
        FitTitle(valor, 22.sp, 26.sp, Modifier.fillMaxWidth(), color = if (destacado) Acento else Charcoal, unaLinea = true, minSize = 12f, fontWeight = FontWeight.Bold)
        Text(nombre, color = Muted, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = tamNombre, lineHeight = (tamNombre.value + 4).sp)
    }
}

/** Título de tarjeta (con la letra de los titulares). */
@Composable
fun TituloTarjeta(t: String, modifier: Modifier = Modifier, tam: Int = 19) =
    Text(t, modifier, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (tam - 2).sp, lineHeight = (tam + 3).sp)

@Composable
fun TextoTarjeta(t: String, modifier: Modifier = Modifier, color: Color = Muted) =
    Text(t, modifier, color = color, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp)

@Composable
fun Cursiva(t: String, modifier: Modifier = Modifier, color: Color = Charcoal, tam: Int = 16) =
    Text(t, modifier, color = color, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = tam.sp, lineHeight = (tam + 6).sp)

/** Una píldora con marco (las acciones pequeñas dentro de una tarjeta: «Comprar 10»). */
@Composable
fun PildoraMarco(t: String, modifier: Modifier = Modifier, alHacer: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(50)).border(1.dp, Edge, RoundedCornerShape(50)).tocable(onClick = alHacer).padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(t, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)
    }
}

/**
 * El tamaño de las etiquetas de una fila de [n] contadores para que su palabra más larga quepa entera (con la letra grande, en un móvil
 * estrecho, «seguidores» se partía en «seguidore / s»). Se calcula con el ancho de la fila, fuera de la fila (que mide con alto intrínseco).
 */
@Composable
fun tamEtiquetas(anchoFila: Dp, n: Int, etiquetas: List<String>): androidx.compose.ui.unit.TextUnit {
    val medidor = androidx.compose.ui.text.rememberTextMeasurer()
    val d = androidx.compose.ui.platform.LocalDensity.current
    val hueco = with(d) { ((anchoFila - 8.dp * (n - 1)) / n - 24.dp).roundToPx() }
    // (con el estilo de texto del tema, que trae su espaciado entre letras: medido sin él, la palabra «cabía» y luego se partía)
    val base = androidx.compose.material3.LocalTextStyle.current
    val t = remember(etiquetas, hueco, Body, base) {
        var x = 12f
        fun cabe(v: Float) = etiquetas.flatMap { it.split(' ') }.filter { it.isNotBlank() }.all { p ->
            medidor.measure(p, base.merge(androidx.compose.ui.text.TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = v.sp)), softWrap = false, maxLines = 1).size.width <= hueco }
        while (x > 8f && !cabe(x)) x -= 0.5f
        x
    }
    return t.sp
}
