package com.gonzalo.mesa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * La dirección D, «la mesa y la estantería»: las seis apps de la familia se usan igual y solo cambian de tinta.
 *  - Abajo, la barra ([Andamio]): Hoy, la estantería, el botón de añadir o buscar y Tú.
 *  - Hoy es la mesa: lo que tienes a medias, grande, con un solo botón ([TarjetaAhora]); debajo, tu semana ([Semana]) y lo siguiente.
 *  - La estantería: todas las portadas en una pantalla, con el buscador arriba ([Buscador]) y los estados como filtros ([Filtros]).
 *  - La ficha: la portada sobre un lavado de su color ([Lavado]), el botón, el estado ([Estados]) y los datos ([Dato]).
 * Texto en minúsculas normales, fondo neutro, tarjetas redondas sin borde: el color lo ponen las portadas y el acento de cada app.
 */
class Tinta(
    val fondo: Color, val superficie: Color, val tinta: Color, val apagado: Color, val linea: Color, val chip: Color,
    val acento: Color, val sobreAcento: Color, val titulos: FontFamily, val texto: FontFamily,
)

val LocalTinta = staticCompositionLocalOf<Tinta> { error("Falta la Tinta: envuelve la pantalla en ConTinta") }

/** Los colores y las letras de la app, para todo lo de [com.gonzalo.mesa]. */
@Composable
fun ConTinta(t: Tinta, contenido: @Composable () -> Unit) = CompositionLocalProvider(LocalTinta provides t, content = contenido)

/** Las medidas de la casa. */
object Medida {
    val margen = 20.dp
    val tarjeta = RoundedCornerShape(22.dp)
    val boton = RoundedCornerShape(16.dp)
    val portada = RoundedCornerShape(8.dp)
    val pildora = RoundedCornerShape(50)
}

// ───────────────────────────────────────────────────────────── texto

/** Un título (la letra de títulos, gruesa). Nunca cortado: si no cabe, baja de línea. */
@Composable
fun TituloM(t: String, modifier: Modifier = Modifier, tam: Int = 24, color: Color = LocalTinta.current.tinta, lineas: Int = 3, centro: Boolean = false) =
    Text(t, modifier, color = color, fontFamily = LocalTinta.current.titulos, fontWeight = FontWeight.Bold, fontSize = tam.sp, lineHeight = (tam * 1.12f).sp,
        maxLines = lineas, overflow = TextOverflow.Ellipsis, textAlign = if (centro) TextAlign.Center else TextAlign.Start)

/** Texto normal. */
@Composable
fun TextoM(t: String, modifier: Modifier = Modifier, tam: Int = 15, color: Color = LocalTinta.current.tinta, lineas: Int = Int.MAX_VALUE, peso: FontWeight = FontWeight.Normal, centro: Boolean = false) =
    Text(t, modifier, color = color, fontFamily = LocalTinta.current.texto, fontWeight = peso, fontSize = tam.sp, lineHeight = (tam * 1.4f).sp,
        maxLines = lineas, overflow = TextOverflow.Ellipsis, textAlign = if (centro) TextAlign.Center else TextAlign.Start)

/** Texto secundario (apagado). */
@Composable
fun NotaM(t: String, modifier: Modifier = Modifier, tam: Int = 13, lineas: Int = Int.MAX_VALUE, centro: Boolean = false) =
    TextoM(t, modifier, tam, LocalTinta.current.apagado, lineas, centro = centro)

/** El rótulo de un bloque («Leyendo», «Esta semana · 4 h»), con un enlace opcional a la derecha. */
@Composable
fun Rotulo(t: String, modifier: Modifier = Modifier, enlace: String? = null, alEnlace: () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextoM(t, Modifier.weight(1f), 13, LocalTinta.current.apagado, peso = FontWeight.SemiBold)
        if (enlace != null) TextoM(enlace, Modifier.clip(Medida.pildora).clickable(onClick = alEnlace).padding(horizontal = 8.dp, vertical = 6.dp), 13, LocalTinta.current.acento, 1, FontWeight.SemiBold)
    }
}

/** El nombre de la app con su punto de color, y algo a la derecha (la fecha, un icono). */
@Composable
fun CabeceraM(nombre: String, modifier: Modifier = Modifier, derecha: @Composable RowScope.() -> Unit = {}) {
    val c = LocalTinta.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            TituloM(nombre, tam = 26, lineas = 1)
            TituloM(".", tam = 26, color = c.acento, lineas = 1)
        }
        derecha()
    }
}

// ───────────────────────────────────────────────────────────── piezas

/** Una tarjeta: superficie redonda, sin borde. */
@Composable
fun TarjetaM(modifier: Modifier = Modifier, alHacer: (() -> Unit)? = null, relleno: Dp = 16.dp, contenido: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(Medida.tarjeta).background(LocalTinta.current.superficie).let { if (alHacer != null) it.clickable(onClick = alHacer) else it }.padding(relleno), content = contenido)
}

/** El botón: relleno del acento (el principal) o del chip (el secundario), con el texto a la izquierda y la flecha a la derecha. */
@Composable
fun BotonM(texto: String, modifier: Modifier = Modifier, principal: Boolean = true, icono: ImageVector? = null, flecha: Boolean = principal, activo: Boolean = true, alHacer: () -> Unit) {
    val c = LocalTinta.current
    val fondo = if (principal) c.acento else c.chip
    val letra = if (principal) c.sobreAcento else c.tinta
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).clip(Medida.boton).background(if (activo) fondo else fondo.copy(alpha = 0.4f)).clickable(enabled = activo, role = Role.Button, onClick = alHacer)
        .padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icono != null) { Icon(icono, null, tint = letra, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        TextoM(texto, Modifier.weight(1f), 16, letra, 2, FontWeight.SemiBold)
        if (flecha) TextoM("→", tam = 18, color = letra, peso = FontWeight.SemiBold)
    }
}

/** Un botón pequeño de texto (acciones de segunda fila: «Editar», «Imágenes»). */
@Composable
fun BotonTextoM(texto: String, modifier: Modifier = Modifier, peligro: Boolean = false, icono: ImageVector? = null, alHacer: () -> Unit) {
    val c = LocalTinta.current
    Row(modifier.heightIn(min = 44.dp).clip(Medida.pildora).background(c.chip).clickable(role = Role.Button, onClick = alHacer).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icono != null) { Icon(icono, null, tint = if (peligro) c.acento else c.tinta, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        TextoM(texto, tam = 14, color = if (peligro) c.acento else c.tinta, lineas = 1, peso = FontWeight.SemiBold)
    }
}

/** Un icono que se pulsa (44 dp de dedo). */
@Composable
fun IconoM(icono: ImageVector, descripcion: String, modifier: Modifier = Modifier, color: Color = LocalTinta.current.tinta, conFondo: Boolean = false, alHacer: () -> Unit) {
    Box(modifier.size(44.dp).clip(CircleShape).let { if (conFondo) it.background(LocalTinta.current.chip) else it }.clickable(role = Role.Button, onClick = alHacer).semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center) { Icon(icono, null, tint = color, modifier = Modifier.size(22.dp)) }
}

/** La barra de avance. */
@Composable
fun AvanceM(f: Float, modifier: Modifier = Modifier, alto: Dp = 5.dp) {
    val c = LocalTinta.current
    Box(modifier.fillMaxWidth().height(alto).clip(Medida.pildora).background(c.chip)) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().clip(Medida.pildora).background(c.acento))
    }
}

/** Una píldora de filtro o de estado. */
@Composable
fun Pildora(texto: String, elegida: Boolean, modifier: Modifier = Modifier, cuenta: Int? = null, alHacer: () -> Unit) {
    val c = LocalTinta.current
    Row(modifier.heightIn(min = 38.dp).clip(Medida.pildora).background(if (elegida) c.tinta else c.chip).clickable(role = Role.Tab, onClick = alHacer)
        .semantics { selected = elegida }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextoM(texto, tam = 14, color = if (elegida) c.fondo else c.tinta, lineas = 1, peso = FontWeight.SemiBold)
        if (cuenta != null) { Spacer(Modifier.width(6.dp)); TextoM("$cuenta", tam = 13, color = if (elegida) c.fondo.copy(alpha = 0.7f) else c.apagado, lineas = 1) }
    }
}

/** Los filtros de la estantería (bajan de línea si no caben: nunca cortados). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Filtros(opciones: List<Pair<String, Int?>>, elegido: Int, modifier: Modifier = Modifier, alElegir: (Int) -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEachIndexed { i, (t, n) -> Pildora(t, i == elegido, cuenta = n) { alElegir(i) } }
    }
}

/**
 * El estado de una cosa (pendiente, a medias, terminado, abandonado): casillas dentro de una caja; la elegida, en superficie. Hasta tres van
 * en una fila; con más, de dos en dos (así «Abandonado» cabe entero también con la letra grande: nunca cortado).
 */
@Composable
fun Estados(opciones: List<String>, elegido: Int, modifier: Modifier = Modifier, alElegir: (Int) -> Unit) {
    val c = LocalTinta.current
    Column(modifier.fillMaxWidth().clip(Medida.boton).background(c.chip).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        opciones.indices.chunked(if (opciones.size <= 3) 3 else 2).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                fila.forEach { i ->
                    Box(Modifier.weight(1f).heightIn(min = 42.dp).clip(RoundedCornerShape(12.dp)).background(if (i == elegido) c.superficie else Color.Transparent)
                        .clickable(role = Role.Tab) { alElegir(i) }.semantics { selected = i == elegido }.padding(horizontal = 8.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        TextoM(opciones[i], tam = 14, color = if (i == elegido) c.tinta else c.apagado, lineas = 2, peso = FontWeight.SemiBold, centro = true)
                    }
                }
                repeat((if (opciones.size <= 3) 0 else 2) - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Un dato de la ficha: la etiqueta a la izquierda y el valor a la derecha, con una línea debajo. */
@Composable
fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier, alHacer: (() -> Unit)? = null) {
    val c = LocalTinta.current
    Column(modifier.fillMaxWidth().let { if (alHacer != null) it.clickable(onClick = alHacer) else it }) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
            TextoM(etiqueta, Modifier.padding(end = 16.dp), 14, c.apagado)
            TextoM(valor, Modifier.weight(1f), 14, if (alHacer != null) c.acento else c.tinta, peso = FontWeight.Medium, centro = false)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.linea))
    }
}

/** El buscador de arriba de la estantería. */
@Composable
fun Buscador(valor: String, pista: String, modifier: Modifier = Modifier, icono: ImageVector? = null, alCambiar: (String) -> Unit) {
    val c = LocalTinta.current
    Row(modifier.fillMaxWidth().heightIn(min = 50.dp).clip(Medida.boton).background(c.superficie).border(1.dp, c.linea, Medida.boton).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icono != null) { Icon(icono, null, tint = c.apagado, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        Box(Modifier.weight(1f)) {
            if (valor.isEmpty()) TextoM(pista, tam = 15, color = c.apagado, lineas = 1)
            BasicTextField(valor, alCambiar, Modifier.fillMaxWidth(), singleLine = true, cursorBrush = SolidColor(c.acento),
                textStyle = TextStyle(color = c.tinta, fontFamily = c.texto, fontSize = 15.sp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
        }
        if (valor.isNotEmpty()) TextoM("✕", Modifier.clip(CircleShape).clickable { alCambiar("") }.padding(10.dp), 14, c.apagado)
    }
}

/** Una pantalla vacía: qué va a salir aquí y cómo poner lo primero. */
@Composable
fun VacioM(titulo: String, texto: String, modifier: Modifier = Modifier, boton: String? = null, alBoton: () -> Unit = {}) {
    TarjetaM(modifier, relleno = 22.dp) {
        TituloM(titulo, tam = 20)
        Spacer(Modifier.height(6.dp))
        NotaM(texto, tam = 14)
        if (boton != null) { Spacer(Modifier.height(16.dp)); BotonM(boton, alHacer = alBoton) }
    }
}

// ───────────────────────────────────────────────────────────── Hoy

/**
 * Lo que tienes a medias, grande: el rótulo, la portada con el título y una línea, el avance y un solo botón. Tocar la tarjeta abre su ficha.
 * [apaisada]: la portada es un fotograma (películas, canales) y va arriba, a lo ancho.
 */
@Composable
fun TarjetaAhora(rotulo: String, titulo: String, linea: String, boton: String, modifier: Modifier = Modifier, avance: Float? = null, apaisada: Boolean = false,
                 alAbrir: () -> Unit, alBoton: () -> Unit, portada: @Composable () -> Unit) {
    TarjetaM(modifier, alHacer = alAbrir) {
        Rotulo(rotulo)
        Spacer(Modifier.height(12.dp))
        if (apaisada) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(Medida.portada)) { portada() }
            Spacer(Modifier.height(12.dp))
            TituloM(titulo, tam = 24)
            if (linea.isNotBlank()) { Spacer(Modifier.height(4.dp)); NotaM(linea, tam = 14) }
        } else Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.width(96.dp).aspectRatio(2f / 3f).clip(Medida.portada)) { portada() }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                TituloM(titulo, tam = 24, lineas = 4)
                if (linea.isNotBlank()) { Spacer(Modifier.height(4.dp)); NotaM(linea, tam = 14) }
            }
        }
        if (avance != null) { Spacer(Modifier.height(14.dp)); AvanceM(avance) }
        Spacer(Modifier.height(14.dp))
        BotonM(boton, alHacer = alBoton)
    }
}

/** Tu semana: siete barras con la letra de su día ([letras]: de lunes a domingo, o los siete últimos días), la de hoy en el acento. [valores] en la unidad que sea; [hoy], cuál es hoy. */
@Composable
fun Semana(rotulo: String, valores: List<Float>, hoy: Int, modifier: Modifier = Modifier, alto: Dp = 64.dp, elegido: Int = -1, letras: List<String> = "LMXJVSD".map { "$it" }, alElegir: ((Int) -> Unit)? = null) {
    val c = LocalTinta.current
    val max = (valores.maxOrNull() ?: 0f).coerceAtLeast(0.0001f)
    Column(modifier.fillMaxWidth()) {
        Rotulo(rotulo)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(alto), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            valores.take(7).forEachIndexed { i, v ->
                Box(Modifier.weight(1f).fillMaxHeight().let { if (alElegir != null) it.clickable { alElegir(i) } else it }, contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight((v / max).coerceIn(0.06f, 1f)).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (i == hoy || i == elegido) c.acento else if (v > 0f) lerp(c.chip, c.tinta, 0.18f) else c.chip))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            letras.take(7).forEachIndexed { i, d -> TextoM(d, Modifier.weight(1f), 12, if (i == hoy) c.tinta else c.apagado, 1, if (i == hoy) FontWeight.Bold else FontWeight.Normal, centro = true) }
        }
    }
}

/** Una portada con su avance debajo (para la estantería y para «Lo siguiente»). [descripcion]: su título, para quien no ve la imagen. */
@Composable
fun PortadaConAvance(descripcion: String, modifier: Modifier = Modifier, avance: Float? = null, proporcion: Float = 2f / 3f, alAbrir: () -> Unit, portada: @Composable () -> Unit) {
    Column(modifier.clip(Medida.portada).clickable(onClickLabel = "Abrir", role = Role.Button, onClick = alAbrir).semantics(mergeDescendants = true) { contentDescription = descripcion }) {
        Box(Modifier.fillMaxWidth().aspectRatio(proporcion).clip(Medida.portada)) { portada() }
        if (avance != null && avance > 0f && avance < 1f) { Spacer(Modifier.height(5.dp)); AvanceM(avance, alto = 3.dp) }
    }
}

// ───────────────────────────────────────────────────────────── la ficha

/** La cabecera de la ficha: un lavado del color de la portada (o del acento) que se funde con el fondo; dentro, la portada y el título. */
@Composable
fun Lavado(modifier: Modifier = Modifier, color: Color = LocalTinta.current.acento, contenido: @Composable ColumnScope.() -> Unit) {
    val c = LocalTinta.current
    Column(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(lerp(c.fondo, color, 0.38f), c.fondo))), horizontalAlignment = Alignment.CenterHorizontally, content = contenido)
}

// ───────────────────────────────────────────────────────────── la barra

/** Una pestaña de la barra. [principal]: el botón del medio (añadir, buscar), que va relleno del acento y no es una pantalla si [esAccion]. */
class Pestana(val nombre: String, val icono: ImageVector, val principal: Boolean = false)

/**
 * El andamio de la app: el contenido y, abajo, la barra con las pestañas (en pantallas anchas —tablet apaisada, tele— la barra va a la
 * izquierda, de arriba abajo). [elegida] es la pestaña que se ve; la principal avisa con [alElegir] como las demás (la app decide si es
 * una pantalla o una acción).
 */
@Composable
fun Andamio(pestanas: List<Pestana>, elegida: Int, alElegir: (Int) -> Unit, modifier: Modifier = Modifier, conBarra: Boolean = true, contenido: @Composable BoxScope.() -> Unit) {
    val c = LocalTinta.current
    val ancha = LocalConfiguration.current.let { it.screenWidthDp >= 720 && it.screenWidthDp > it.screenHeightDp }
    if (ancha) Row(modifier.fillMaxSize().background(c.fondo)) {
        if (conBarra) Column(Modifier.fillMaxHeight().width(104.dp).background(c.superficie).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            pestanas.forEachIndexed { i, p -> BotonBarra(p, i == elegida, Modifier.fillMaxWidth()) { alElegir(i) } }
        }
        Box(Modifier.weight(1f).fillMaxHeight(), content = contenido)
    } else Column(modifier.fillMaxSize().background(c.fondo)) {
        Box(Modifier.weight(1f).fillMaxWidth(), content = contenido)
        if (conBarra) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.linea))
            Row(Modifier.fillMaxWidth().background(c.superficie).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 6.dp, vertical = 6.dp)) {
                pestanas.forEachIndexed { i, p -> BotonBarra(p, i == elegida, Modifier.weight(1f)) { alElegir(i) } }
            }
        }
    }
}

@Composable
private fun BotonBarra(p: Pestana, elegida: Boolean, modifier: Modifier, alHacer: () -> Unit) {
    val c = LocalTinta.current
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Tab, onClick = alHacer).semantics { selected = elegida; contentDescription = p.nombre }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (p.principal) Box(Modifier.size(34.dp).clip(CircleShape).background(c.acento), contentAlignment = Alignment.Center) { Icon(p.icono, null, tint = c.sobreAcento, modifier = Modifier.size(20.dp)) }
        else Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { Icon(p.icono, null, tint = if (elegida) c.acento else c.apagado, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(2.dp))
        Text(p.nombre, color = if (elegida) c.acento else c.apagado, fontFamily = c.texto, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
    }
}

/** La columna de una pantalla de la mesa: márgenes, la barra de estado y el hueco entre bloques. Dentro va lo que sea (con scroll propio si hace falta). */
@Composable
fun PaginaM(modifier: Modifier = Modifier, hueco: Dp = 20.dp, contenido: @Composable ColumnScope.() -> Unit) {
    // (en pantallas anchas la columna no pasa de 760 dp: las tarjetas no se estiran de lado a lado)
    Column(modifier.fillMaxHeight().widthIn(max = 760.dp).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = Medida.margen), verticalArrangement = Arrangement.spacedBy(hueco), content = contenido)
}
