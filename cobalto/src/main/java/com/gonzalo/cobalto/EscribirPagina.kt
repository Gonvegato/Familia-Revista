@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gonzalo.cobalto

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/*
 * La página del editor de escenas, lo que no es el texto: cómo se ve (letra, tamaño, interlineado, ancho, sangría), la
 * concentración a pantalla completa, las cuentas de abajo, la vista previa de una ficha nombrada, los atajos de teclado y lo
 * que se encuentra al buscar en toda la obra.
 */

/** Una letra para escribir: su nombre y de dónde sale (la de la revista, la del texto, máquina o una de Google Fonts). */
class LetraEscribir(val nombre: String, private val google: String? = null, private val cual: Int = 0) {
    val familia: FontFamily get() = when {
        google != null -> Skin.family(google) ?: Display
        cual == 1 -> Body
        cual == 2 -> FontFamily.Monospace
        else -> Display
    }
}

/**
 * Cómo se ve la página del editor en este dispositivo (como en el PC, son preferencias de cada uno, no de la obra): letra,
 * tamaño, interlineado, ancho, sangría de libro, máquina de escribir, tipografía automática y lo de la concentración.
 */
class PaginaEscribir(private val prefs: SharedPreferences) {
    var fuente by mutableIntStateOf(prefs.getInt("fuente", 0).coerceIn(0, LETRAS.lastIndex))
    var tam by mutableFloatStateOf(prefs.getFloat("tam", 19f).coerceIn(TAM_MIN, TAM_MAX))
    var interlineado by mutableFloatStateOf(prefs.getFloat("interlineado", INTERLINEADOS[1].second))
    var ancho by mutableIntStateOf(prefs.getInt("ancho", 1).coerceIn(0, ANCHOS.lastIndex))
    var sangria by mutableStateOf(prefs.getBoolean("sangria", false))
    var maquina by mutableStateOf(prefs.getBoolean("maquina", false))
    var tipografia by mutableStateOf(prefs.getBoolean("tipografia", true))
    /** En concentración, todo apagado salvo el párrafo en el que escribes. */
    var atenuar by mutableStateOf(prefs.getBoolean("atenuar", true))
    /** En concentración, las cuentas abajo (muy discretas). */
    var cuentasFoco by mutableStateOf(prefs.getBoolean("cuentasFoco", true))

    val letra: LetraEscribir get() = LETRAS[fuente]
    val anchoMax get() = ANCHOS[ancho].second

    fun guarda() {
        prefs.edit().putInt("fuente", fuente).putFloat("tam", tam).putFloat("interlineado", interlineado).putInt("ancho", ancho)
            .putBoolean("sangria", sangria).putBoolean("maquina", maquina).putBoolean("tipografia", tipografia)
            .putBoolean("atenuar", atenuar).putBoolean("cuentasFoco", cuentasFoco).apply()
    }

    fun letraMas() { tam = (tam + 1f).coerceAtMost(TAM_MAX); guarda() }
    fun letraMenos() { tam = (tam - 1f).coerceAtLeast(TAM_MIN); guarda() }

    companion object {
        const val TAM_MIN = 14f
        const val TAM_MAX = 32f
        /** Las tres primeras son las de siempre (con serifa, de palo seco y máquina): su número no cambia. */
        val LETRAS = listOf(
            LetraEscribir("La de la revista"), LetraEscribir("Palo seco", cual = 1), LetraEscribir("Máquina", cual = 2),
            LetraEscribir("Literata", "Literata"), LetraEscribir("Garamond", "EB Garamond"), LetraEscribir("Lora", "Lora"),
            LetraEscribir("Merriweather", "Merriweather"), LetraEscribir("Courier Prime", "Courier Prime"),
        )
        val INTERLINEADOS = listOf("Junto" to 1.42f, "Normal" to 1.62f, "Amplio" to 1.85f, "Doble" to 2.1f)
        val ANCHOS = listOf("Estrecha" to 560.dp, "Normal" to 700.dp, "Ancha" to 860.dp)

        fun de(ctx: Context) = PaginaEscribir(ctx.getSharedPreferences("escribir", Context.MODE_PRIVATE))
    }
}

/** «La página»: elegir la letra, el tamaño, el interlineado, el ancho y lo demás, viendo un párrafo de prueba al momento. */
@Composable
fun HojaPagina(p: PaginaEscribir, ancha: Boolean, onCerrar: () -> Unit) {
    ModalBottomSheet(onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Crudo, shape = shp(28.dp)) {
        Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("La página", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Charcoal)
            // Un párrafo de prueba con todo lo elegido
            val muestra = "—¿Y si no vuelve? —preguntó Elena.\nNadie contestó. La ciudad seguía despertando despacio, como si cada ventana tuviera que decidir por su cuenta si merecía la pena abrirse aquella mañana."
            Column(Modifier.fillMaxWidth().clip(shp(14.dp)).background(Paper).border(1.dp, Line, shp(14.dp)).padding(horizontal = 18.dp, vertical = 14.dp)) {
                muestra.split('\n').forEach { l ->
                    Text(l, fontFamily = p.letra.familia, fontSize = p.tam.sp, lineHeight = (p.tam * p.interlineado).sp, color = Charcoal,
                        style = androidx.compose.ui.text.TextStyle(textIndent = if (p.sangria) TextIndent(firstLine = (p.tam * 1.6f).sp) else null))
                }
            }
            EtiquetaEscribir("Letra")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PaginaEscribir.LETRAS.forEachIndexed { i, l ->
                    val on = p.fuente == i
                    Text(l.nombre, fontFamily = l.familia, fontSize = 17.sp, color = if (on) Acento else Charcoal, maxLines = 1,
                        modifier = Modifier.press(haptic = false) { p.fuente = i; p.guarda() }
                            .drawBehind { if (on) drawRect(Acento, Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 1.5.dp.toPx())) }
                            .padding(vertical = 8.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                EtiquetaEscribir("Tamaño")
                Spacer(Modifier.weight(1f))
                Text("${p.tam.toInt()}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Charcoal)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A", fontFamily = Display, fontSize = 14.sp, color = Muted, modifier = Modifier.press(haptic = false) { p.letraMenos() }.padding(8.dp))
                Slider(p.tam, { p.tam = Math.round(it).toFloat() }, Modifier.weight(1f), valueRange = PaginaEscribir.TAM_MIN..PaginaEscribir.TAM_MAX,
                    steps = (PaginaEscribir.TAM_MAX - PaginaEscribir.TAM_MIN).toInt() - 1, onValueChangeFinished = { p.guarda() },
                    colors = SliderDefaults.colors(thumbColor = Acento, activeTrackColor = Charcoal, inactiveTrackColor = Line, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent))
                Text("A", fontFamily = Display, fontSize = 24.sp, color = Muted, modifier = Modifier.press(haptic = false) { p.letraMas() }.padding(8.dp))
            }
            EtiquetaEscribir("Interlineado")
            FiltrosRevista(PaginaEscribir.INTERLINEADOS.map { it.first }, PaginaEscribir.INTERLINEADOS.indexOfFirst { kotlin.math.abs(it.second - p.interlineado) < 0.01f }) { i ->
                p.interlineado = PaginaEscribir.INTERLINEADOS[i].second; p.guarda()
            }
            if (ancha) {
                EtiquetaEscribir("Ancho de la página")
                FiltrosRevista(PaginaEscribir.ANCHOS.map { it.first }, p.ancho) { i -> p.ancho = i; p.guarda() }
            }
            Filete(1.dp, Line)
            Fila("Sangría de libro", "La primera línea de cada párrafo, un poco hacia dentro") { Interruptor(p.sangria) { p.sangria = it; p.guarda() } }
            Fila("Máquina de escribir", "La línea en la que escribes se queda a media altura") { Interruptor(p.maquina) { p.maquina = it; p.guarda() } }
            Fila("Tipografía automática", "-- es una raya, ... son puntos suspensivos y las comillas rectas son « »") { Interruptor(p.tipografia) { p.tipografia = it; p.guarda() } }
            Filete(1.dp, Line)
            EtiquetaEscribir("En concentración")
            Fila("Apagar lo demás", "Solo se ve claro el párrafo que escribes") { Interruptor(p.atenuar) { p.atenuar = it; p.guarda() } }
            Fila("Las cuentas, abajo", "Palabras, sesión y lo de hoy, muy discretas") { Interruptor(p.cuentasFoco) { p.cuentasFoco = it; p.guarda() } }
            Text("Se guarda en este dispositivo: en la tableta puedes tener otra letra que en el móvil.", color = Muted, fontSize = 12.sp)
        }
    }
}

private fun Context.actividad(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.actividad(); else -> null }

/**
 * La concentración de verdad: sin barra de estado ni de navegación (vuelven un momento si deslizas desde el borde) y la
 * pantalla sin apagarse mientras escribes. Al salir, todo vuelve a como estaba.
 */
@Composable
fun PantallaCompleta(activa: Boolean) {
    val vista = LocalView.current
    DisposableEffect(activa) {
        val ventana = vista.context.actividad()?.window
        val control = ventana?.let { WindowCompat.getInsetsController(it, vista) }
        if (activa) {
            control?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            control?.hide(WindowInsetsCompat.Type.systemBars())
            vista.keepScreenOn = true
        }
        onDispose {
            if (activa) { control?.show(WindowInsetsCompat.Type.systemBars()); vista.keepScreenOn = false }
        }
    }
}

/** Lo que cuentan las cuentas del editor. */
class CuentasEscena(val palabras: Int, val seleccion: Int, val sesion: Int, val meta: Int, val hoy: Int, val diaria: Int) {
    val minutos: Int get() = maxOf(1, (palabras + 219) / 220)
}

private val VERDE = Color(0xFF5B8C5A)

/**
 * Las cuentas, siempre a la vista: palabras (o las elegidas de cuántas), minutos de lectura, lo de esta sesión, la meta de la
 * escena y, a la derecha, lo escrito hoy frente a la meta del día. En concentración, en una línea muy discreta.
 */
@Composable
fun CuentasEditor(c: CuentasEscena, discreto: Boolean, modifier: Modifier = Modifier, extra: @Composable RowScope.() -> Unit = {}) {
    val tinta = if (discreto) Muted else Charcoal
    val gris = if (discreto) Muted.copy(alpha = 0.75f) else Muted
    fun cifra(b: AnnotatedString.Builder, n: String, color: Color = tinta) = b.withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(n) }
    val izquierda = buildAnnotatedString {
        withStyle(SpanStyle(color = gris)) {
            if (c.seleccion > 0) { cifra(this, cifraE(c.seleccion)); append(" de ${cifraE(c.palabras)} palabras") }
            else { cifra(this, cifraE(c.palabras)); append(if (c.palabras == 1) " palabra" else " palabras") }
            append("  ·  "); cifra(this, "${c.minutos}"); append(" min")
            if (c.sesion != 0) { append("  ·  "); cifra(this, (if (c.sesion > 0) "+" else "") + cifraE(c.sesion), if (c.sesion > 0) Acento else tinta); if (!discreto) append(" en esta sesión") }
            if (c.meta > 0) {
                append("  ·  meta "); cifra(this, "${(c.palabras * 100L / c.meta).coerceAtMost(999)} %", if (c.palabras >= c.meta) VERDE else tinta)
            }
        }
    }
    val derecha = if (c.diaria > 0) buildAnnotatedString {
        withStyle(SpanStyle(color = gris)) { append("hoy "); cifra(this, cifraE(c.hoy), if (c.hoy >= c.diaria) VERDE else tinta); append(" / ${cifraE(c.diaria)}") }
    } else if (c.hoy > 0) buildAnnotatedString { withStyle(SpanStyle(color = gris)) { append("hoy "); cifra(this, cifraE(c.hoy)) } } else null
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Text(izquierda, fontFamily = Body, fontSize = if (discreto) 11.sp else 12.sp, maxLines = 1, softWrap = false)
            extra()
        }
        if (derecha != null) {
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(derecha, fontFamily = Body, fontSize = if (discreto) 11.sp else 12.sp, maxLines = 1, softWrap = false)
                if (c.diaria > 0) Box(Modifier.padding(top = 3.dp).width(64.dp).height(2.dp).background(Line)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((c.hoy.toFloat() / c.diaria).coerceIn(0f, 1f)).background(if (c.hoy >= c.diaria) VERDE else Acento))
                }
            }
        }
    }
}

/**
 * La ficha que nombras: al poner el cursor en un nombre (o un alias) subrayado, su vista previa sobre las cuentas, con lo
 * esencial (qué es, la frase, unos datos y con quién está unida). Tocarla abre la ficha para leerla.
 */
@Composable
fun PreviaFicha(e: Escritos, f: Doc?, onLeer: (Doc) -> Unit, onCerrar: (Doc) -> Unit) {
    // (se recuerda la última, para que al irse se vea salir y no desaparezca de golpe)
    var ultima by remember { mutableStateOf(f) }
    if (f != null) ultima = f
    AnimatedVisibility(f != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val x = ultima ?: return@AnimatedVisibility
        val color = colorEscrito(x.s("color"))
        val datos = remember(x.u, x.id) {
            x.o.optJSONArray("campos")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { it.optString("k").trim() to it.optString("v").trim() }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() } }.orEmpty().take(3)
        }
        val unidas = remember(x.id, e.version) {
            e.relaciones(x.pid).filter { it.s("a") == x.id || it.s("b") == x.id }.mapNotNull { r -> e.get(if (r.s("a") == x.id) r.s("b") else r.s("a"))?.let { r.s("titulo") to it.s("nombre") } }.take(3)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(shp(12.dp)).background(Crudo).border(1.dp, Line, shp(12.dp)).press(haptic = false) { onLeer(x) }) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(color))
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(singularFicha(x.s("tipo")).uppercase() + if (x.s("alias").isNotBlank()) " · también ${x.s("alias")}" else "", color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(x.s("nombre"), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Charcoal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (x.s("resumen").isNotBlank()) Text(x.s("resumen"), fontFamily = Display, fontStyle = FontStyle.Italic, color = Charcoal, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (datos.isNotEmpty()) Text(datos.joinToString("  ·  ") { (k, v) -> "$k: $v" }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (unidas.isNotEmpty()) Text(unidas.joinToString("  ·  ") { (t, n) -> if (t.isBlank()) n else "${t.uppercase()} · $n" }, color = Acento, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("✕", color = Muted, fontSize = 14.sp, modifier = Modifier.press(haptic = false) { onCerrar(x) }.padding(10.dp))
                    Text("LEER →", color = Acento, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp, modifier = Modifier.padding(end = 10.dp, top = 6.dp))
                }
            }
        }
    }
}

/** Los atajos con un teclado físico (en la tableta, con su teclado). */
val ATAJOS_EDITOR = listOf(
    "Ctrl + B" to "Negrita", "Ctrl + I" to "Cursiva", "Ctrl + Z" to "Deshacer", "Ctrl + Y · Ctrl + Mayús + Z" to "Rehacer",
    "Ctrl + F" to "Buscar en la escena", "Ctrl + H" to "Buscar y cambiar", "F3 · Ctrl + G" to "Siguiente (con Mayús, el anterior)",
    "Ctrl + S" to "Guardar una versión ahora", "Ctrl + 1 · 2 · 3" to "Título grande, mediano o pequeño", "Ctrl + Mayús + C" to "Cita",
    "Ctrl + Intro" to "Salto de escena (***)", "Ctrl + D" to "Raya de diálogo (—)", "Ctrl + E" to "Comillas « »",
    "Alt + ↑ · Alt + ↓" to "Subir o bajar el párrafo", "Ctrl + RePág · AvPág" to "Escena anterior o siguiente",
    "Ctrl + Mayús + F · F11" to "Concentración", "Ctrl + T" to "Máquina de escribir", "Ctrl + P" to "Panel de la escena",
    "Ctrl + + · Ctrl + −" to "Letra más grande o más pequeña", "Ctrl + K" to "Sinónimos de la palabra", "Ctrl + / · F1" to "Estos atajos",
    "Esc" to "Cerrar lo abierto o salir de la concentración",
)

@Composable
fun AtajosDialogo(onCerrar: () -> Unit) {
    HojaEscribir("Atajos de teclado", onCerrar) {
        Text("Con un teclado físico (el de la tableta, uno por Bluetooth). En un Mac con teclado de Apple, Ctrl es también ⌘.", color = Muted, fontSize = 13.sp)
        ATAJOS_EDITOR.forEach { (k, que) -> MagFact(que, k) }
    }
}

/** Las veces que sale lo buscado en una escena (para la lista de toda la obra). */
class EnEscena(val escena: Doc, val capitulo: Doc?, val veces: List<IntRange>)

/** Lo buscado en todas las escenas de la obra, en el orden del libro. */
fun buscaEnObra(e: Escritos, pid: String, rx: Regex): List<EnEscena> =
    e.capitulos(pid).flatMap { c -> e.escenas(c.id).mapNotNull { s -> rx.findAll(s.s("texto")).map { it.range }.toList().takeIf { it.isNotEmpty() }?.let { EnEscena(s, c, it) } } }

/** Un trozo del texto alrededor de [r], con lo encontrado resaltado. */
fun extracto(t: String, r: IntRange, resalta: Color): AnnotatedString = buildAnnotatedString {
    val a = (r.first - 50).coerceAtLeast(0).let { i -> t.lastIndexOf(' ', i).takeIf { it in (i - 15)..i }?.plus(1) ?: i }
    val z = (r.last + 1 + 70).coerceAtMost(t.length).let { i -> t.indexOf(' ', i).takeIf { it in i..(i + 15) } ?: i }
    if (a > 0) append("…")
    append(t.substring(a, r.first).replace('\n', ' '))
    withStyle(SpanStyle(background = resalta.copy(alpha = 0.28f), fontWeight = FontWeight.Bold)) { append(t.substring(r.first, r.last + 1).replace('\n', ' ')) }
    append(t.substring(r.last + 1, z).replace('\n', ' '))
    if (z < t.length) append("…")
}

/** Lo que se encuentra en toda la obra: cada escena con sus veces; tocar una abre esa escena justo ahí. */
@Composable
fun ResultadosObra(e: Escritos, pid: String, consulta: String, rx: Regex, actual: String, onIr: (String, Int) -> Unit, onCerrar: () -> Unit) {
    val res = remember(rx, e.version) { buscaEnObra(e, pid, rx) }
    val total = res.sumOf { it.veces.size }
    HojaEscribir("«$consulta» en la obra", onCerrar) {
        Text(if (total == 0) "No sale en ninguna escena." else "$total ${if (total == 1) "vez" else "veces"} en ${res.size} ${if (res.size == 1) "escena" else "escenas"}. Toca una para ir allí.", color = Muted, fontSize = 13.sp)
        var cap = ""
        res.forEach { r ->
            val c = r.capitulo
            if (c != null && c.id != cap) {
                cap = c.id
                Text("CAPÍTULO ${numeroCapitulo(e, c)} · ${c.s("titulo").ifBlank { "Sin título" }}".uppercase(), color = Acento, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Column(Modifier.fillMaxWidth().clip(shp(12.dp)).border(1.dp, if (r.escena.id == actual) Acento else Line, shp(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.escena.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Charcoal, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (r.escena.id == actual) "ESTA · ${r.veces.size}" else "${r.veces.size}", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
                r.veces.take(4).forEachIndexed { k, v ->
                    Text(extracto(r.escena.s("texto"), v, Acento), fontFamily = Display, fontSize = 14.sp, lineHeight = 19.sp, color = Charcoal, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().press(haptic = false) { onIr(r.escena.id, k) }.padding(vertical = 2.dp))
                }
                if (r.veces.size > 4) Text("y ${r.veces.size - 4} más →", color = Acento, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.press(haptic = false) { onIr(r.escena.id, 4) }.padding(vertical = 2.dp))
            }
        }
    }
}

/** Cambia lo buscado ([rx]) por [por] en todas las escenas de la obra (guarda antes una versión de cada una). Devuelve cuántas veces. */
fun reemplazaEnObra(e: Escritos, pid: String, rx: Regex, por: String): Int {
    var n = 0
    e.escenasDe(pid).forEach { s ->
        val t = s.s("texto")
        val c = rx.findAll(t).count()
        if (c > 0) {
            n += c
            e.instantanea(s.id, t, fuerza = true)
            e.escribe(s.id, rx.replace(t, Regex.escapeReplacement(por)))
        }
    }
    return n
}

/** Lo que se busca: el texto, si mira las mayúsculas y si solo vale la palabra entera («Ana», no «Anabel»). */
fun regexBusqueda(q: String, mayus: Boolean, entera: Boolean): Regex? {
    if (q.isEmpty()) return null
    val p = Regex.escape(q).let { if (entera) "(?<![\\p{L}\\p{N}])$it(?![\\p{L}\\p{N}])" else it }
    return runCatching { Regex(p, if (mayus) emptySet() else setOf(RegexOption.IGNORE_CASE)) }.getOrNull()
}
