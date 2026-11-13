package com.gonzalo.marfil

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.FormaEntre
import com.gonzalo.familia.Origen
import com.gonzalo.familia.Vibra
import com.gonzalo.familia.entrada
import com.gonzalo.familia.pulsable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Normalizer

/** Lo último que has buscado (en este dispositivo), para tenerlo a mano al abrir el buscador. */
object Recientes {
    private fun p(c: Context) = c.getSharedPreferences("seshat_busquedas", Context.MODE_PRIVATE)
    fun de(c: Context): List<String> = p(c).getString("r", "").orEmpty().split('\n').filter { it.isNotBlank() }
    fun apunta(c: Context, q: String) {
        val t = q.trim(); if (t.length < 2) return
        val l = (listOf(t) + de(c).filterNot { it.equals(t, true) }).take(6)
        p(c).edit().putString("r", l.joinToString("\n")).apply()
    }
}

/** Una letra «normalizada» (sin tildes, en minúscula; lo que no es letra ni número, espacio), una por una: así lo encontrado se marca en su sitio. */
private fun normPorLetra(s: String): String = buildString(s.length) {
    s.forEach { c ->
        val n = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
        append(if (n.length == 1 && n[0].isLetterOrDigit()) n[0] else ' ')
    }
}

/** [texto] con lo que coincide con [q] (ya normalizada) subrayado en el acento. */
private fun marcado(texto: String, q: String): AnnotatedString = buildAnnotatedString {
    append(texto)
    if (q.isBlank()) return@buildAnnotatedString
    val i = normPorLetra(texto).indexOf(q)
    if (i >= 0) addStyle(SpanStyle(background = Acento.copy(alpha = 0.32f)), i, (i + q.length).coerceAtMost(texto.length))
}

/** Un subrayado, para buscar dentro (de qué libro, el texto, su nota y dónde está). */
private class Subrayado(val libro: Libro, val texto: String, val nota: String, val donde: String, val ir: String)

/**
 * El buscador de Seshat. Crece desde la palabra BUSCAR (o desde donde se toque, [desde]) hasta ocupar la pantalla, con el campo arriba, sobre
 * la franja de tinta, y el teclado listo. Antes de escribir: lo último que buscaste, atajos a cada sección y los libros que sigues. Al
 * escribir, busca en títulos, autores y sagas **y en tus subrayados**, y marca lo que coincide. Un libro abre su ficha encima (al cerrarla,
 * vuelves aquí); un subrayado abre el lector en ese sitio. «Cerrar» (o Atrás) lo recoge otra vez hacia la palabra.
 */
@Composable
fun Buscador(desde: () -> Rect?, apertura: Int, todos: List<Libro>, cuentas: List<Int?>, alIrA: (Int) -> Unit, alAbrir: (Libro) -> Unit, alCerrado: () -> Unit) {
    val ctx = LocalContext.current
    val vista = androidx.compose.ui.platform.LocalView.current
    val foco = remember { FocusRequester() }
    val teclado = LocalFocusManager.current
    var texto by rememberSaveable(apertura) { mutableStateOf("") }
    val t = remember { Animatable(0f) }
    var cerrando by remember { mutableStateOf(false) }
    val origen = remember(apertura) { desde()?.let { Rect(it.left - 10f, it.top - 6f, it.right + 10f, it.bottom + 6f) } }
    LaunchedEffect(apertura) {
        t.snapTo(0f)
        t.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 260f))
    }
    LaunchedEffect(apertura) { delay(140); runCatching { foco.requestFocus() } }
    LaunchedEffect(cerrando) {
        if (!cerrando) return@LaunchedEffect
        teclado.clearFocus()
        Vibra.tic(vista)
        t.animateTo(0f, spring(dampingRatio = 1f, stiffness = 340f))
        alCerrado()
    }
    BackHandler(enabled = !cerrando) { cerrando = true }
    // Los subrayados de todos los libros, una vez por apertura (fuera del hilo de la pantalla)
    var subrayados by remember(apertura) { mutableStateOf<List<Subrayado>>(emptyList()) }
    LaunchedEffect(apertura) {
        subrayados = withContext(Dispatchers.IO) {
            todos.flatMap { l -> runCatching { notasDe(EstadoLectura.estado(ctx, l.id)) }.getOrDefault(emptyList()).map { n ->
                Subrayado(l, n.texto, n.nota, listOfNotNull(n.etiqueta.ifBlank { null }, if (n.creada > 0) Formato.fecha(n.creada) else null).joinToString(" · "), n.value)
            } }
        }
    }
    val q = remember(texto) { normPorLetra(texto.trim()).trim() }
    val vacio = q.isEmpty()
    val libros = remember(q, todos) {
        if (vacio) emptyList() else todos.filter { l -> normPorLetra(l.titulo).contains(q) || normPorLetra(l.autor).contains(q) || Sagas.de(l.clave)?.let { normPorLetra(it.nombre).contains(q) } == true }
            .sortedWith(compareByDescending<Libro> { it.ultima }.thenByDescending { it.anadido })
    }
    val enSubrayados = remember(q, subrayados) { if (vacio) emptyList() else subrayados.filter { normPorLetra(it.texto).contains(q) || normPorLetra(it.nota).contains(q) }.take(30) }
    // (las entradas se repiten solo al pasar de vacío a con resultados y al revés: escribir letra a letra no hace parpadear nada)
    val tanda = "busca:$apertura:${if (vacio) "v" else "r"}"

    Column(Modifier.fillMaxSize().graphicsLayer {
        clip = true
        shape = FormaEntre(origen, t.value)
        alpha = if (origen == null) t.value.coerceIn(0f, 1f) else 1f
    }.background(Paper)) {
        // ---- El campo, arriba, sobre la franja de tinta
        Row(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).height(48.dp).background(Paper).border(2.dp, Acento).padding(horizontal = 12.dp)
                .graphicsLayer { val e = 0.6f + 0.4f * t.value.coerceIn(0f, 1f); scaleX = e; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f); alpha = t.value.coerceIn(0f, 1f) },
                verticalAlignment = Alignment.CenterVertically) {
                Text("⌕", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
                Box(Modifier.weight(1f)) {
                    if (texto.isEmpty()) Text("Título, autor o subrayado", color = Muted, fontFamily = Body, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicTextField(texto, { texto = it }, Modifier.fillMaxWidth().focusRequester(foco), singleLine = true,
                        textStyle = TextStyle(Charcoal, 15.sp, FontWeight.SemiBold, fontFamily = Body), cursorBrush = SolidColor(Acento),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { Recientes.apunta(ctx, texto); teclado.clearFocus() }))
                }
                if (texto.isNotEmpty()) Text("✕", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 15.sp,
                    modifier = Modifier.pulsable(vibra = Vibra::tic) { texto = ""; runCatching { foco.requestFocus() } }.padding(6.dp))
            }
            Text("CERRAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, maxLines = 1,
                modifier = Modifier.padding(start = 12.dp).pulsable(vibra = null) { cerrando = true }.padding(vertical = 10.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f).graphicsLayer { alpha = if (cerrando) ((t.value - 0.5f) / 0.5f).coerceIn(0f, 1f) else 1f }) {
            LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
                if (vacio) {
                    val recientes = Recientes.de(ctx)
                    if (recientes.isNotEmpty()) {
                        item { Grupo("Recientes", null, Modifier.entrada("$tanda:rec", 0)) }
                        item { Chips(recientes, lleno = false, Modifier.entrada("$tanda:recc", 1)) { texto = it } }
                    }
                    item { Grupo("Ir a", null, Modifier.entrada("$tanda:ir", 2)) }
                    item {
                        val atajos = listOf(1 to "Leyendo", 2 to "Pendientes", 3 to "Leídos", 6 to "Sagas")
                        Chips(atajos.map { (i, n) -> listOfNotNull(n, cuentas.getOrNull(i)?.toString()).joinToString(" ") }, lleno = true, Modifier.entrada("$tanda:irc", 3)) { s ->
                            atajos.firstOrNull { s.startsWith(it.second) }?.let { (i, _) -> alIrA(i); cerrando = true }
                        }
                    }
                    val sigues = todos.filter { it.estado == Estado.LEYENDO }.sortedByDescending { it.ultima }.take(5)
                    if (sigues.isNotEmpty()) {
                        item { Grupo("Sigues con", null, Modifier.entrada("$tanda:sig", 4)) }
                        items(sigues, key = { "s:" + it.id }) { l -> FilaBusqueda(l, "", Modifier.entrada("$tanda:${l.id}", 5)) { Recientes.apunta(ctx, texto); alAbrir(l) } }
                    }
                } else {
                    if (libros.isNotEmpty()) {
                        item { Grupo("Libros", libros.size, Modifier.entrada("$tanda:lib", 0)) }
                        items(libros, key = { "l:" + it.id }) { l -> FilaBusqueda(l, q, Modifier.entrada("$tanda:l:${l.id}", 1)) { Recientes.apunta(ctx, texto); alAbrir(l) } }
                    }
                    if (enSubrayados.isNotEmpty()) {
                        item { Grupo("En tus subrayados", enSubrayados.size, Modifier.entrada("$tanda:sub", 2)) }
                        items(enSubrayados.size, key = { "n:$it" }) { i ->
                            val n = enSubrayados[i]
                            Column(Modifier.fillMaxWidth().entrada("$tanda:n:$i", 3).pulsable { Recientes.apunta(ctx, texto); Lector.abrir(ctx, n.libro, goto = n.ir) }.padding(vertical = 10.dp)) {
                                Filete(2.dp)
                                Row(Modifier.padding(top = 10.dp)) {
                                    PortadaLibro(n.libro, Modifier.width(40.dp).height(60.dp), radio = 0, conTitulo = false)
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(marcado("«${n.texto}»", q), color = Charcoal, fontFamily = Display, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                        if (n.nota.isNotBlank()) Text(marcado(n.nota, q), color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                                        Text(listOf(n.libro.titulo, n.donde).filter { it.isNotBlank() }.joinToString(" · ").uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (libros.isEmpty() && enSubrayados.isEmpty()) item {
                        Text("Nada con «${texto.trim()}» en tus libros ni en tus subrayados.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp,
                            modifier = Modifier.padding(top = 26.dp).entrada("$tanda:nada", 0))
                    }
                }
            }
        }
    }
}

@Composable
private fun Grupo(texto: String, n: Int?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp)) {
        Text(texto.uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
        if (n != null) Text("$n", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(textos: List<String>, lleno: Boolean, modifier: Modifier = Modifier, alTocar: (String) -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        textos.forEach { s ->
            Text(s.uppercase(), color = if (lleno) Paper else Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, maxLines = 1,
                modifier = Modifier.pulsable(vibra = Vibra::tic) { alTocar(s) }.then(if (lleno) Modifier.background(Charcoal) else Modifier.border(2.dp, Charcoal)).padding(horizontal = 12.dp, vertical = 9.dp))
        }
    }
}

/** Un libro en los resultados: tapa pequeña, título y autor con lo encontrado marcado, y cómo va. Al tocarlo, su ficha nace de aquí. */
@Composable
private fun FilaBusqueda(l: Libro, q: String, modifier: Modifier = Modifier, alTocar: () -> Unit) {
    val sitio = remember { arrayOfNulls<LayoutCoordinates>(2) }
    Column(modifier.fillMaxWidth()) {
        Filete(2.dp)
        Row(Modifier.fillMaxWidth().onGloballyPositioned { sitio[0] = it }.pulsable { Origen.apunta(l.id, sitio[0], sitio[1]); alTocar() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PortadaLibro(l, Modifier.width(44.dp).height(66.dp).onGloballyPositioned { sitio[1] = it }.tapaQueVuela(l.id), radio = 0, conTitulo = false)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(marcado(l.titulo, q), color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (l.autor.isNotBlank()) Text(marcado(l.autor.uppercase(), q), color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            val est = when (l.estado) { Estado.LEYENDO -> porcentaje(l.fraccion); Estado.PENDIENTE -> "PENDIENTE"; Estado.LEIDO -> "LEÍDO"; Estado.ABANDONADO -> "ABANDONADO" }
            Text(est, color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
        }
    }
}
