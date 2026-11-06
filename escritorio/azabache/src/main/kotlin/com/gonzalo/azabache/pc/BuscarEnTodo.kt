package com.gonzalo.azabache.pc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import java.time.LocalDate

/*
 * Dos piezas de la portada del móvil: «Buscar en todo» (BuscadorGeneral: juegos, libros, pelis, series y tus categorías a la vez, sin
 * fijarse en tildes, y lo que has escrito en tu diario) y «Tal día como hoy» (lo que terminaste un día como hoy de otros años).
 */

/** ¿Están todas las palabras de [q] en alguno de los campos? (sin tildes ni mayúsculas: «fundacion asimov» encuentra «Fundación»). */
fun coincide(q: String, vararg campos: String): Boolean {
    if (q.isBlank()) return true
    fun sin(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
    val todo = campos.joinToString(" ") { sin(it) }
    return sin(q).split(' ').filter { it.isNotBlank() }.all { todo.contains(it) }
}

/** Lo que terminaste un día como hoy de otros años, con cuántos años hace (lo más reciente primero), como `efemerides` del móvil. */
fun efemerides(todas: List<Pair<Kind, Ficha>>, hoy: LocalDate = LocalDate.now()): List<Triple<Kind, Ficha, Int>> = todas.mapNotNull { (k, f) ->
    if (!f.valorada || f.terminado <= 0L) return@mapNotNull null
    val d = LocalDate.ofEpochDay(f.terminado)
    if (d.monthValue == hoy.monthValue && d.dayOfMonth == hoy.dayOfMonth && d.year < hoy.year) Triple(k, f, hoy.year - d.year) else null
}.sortedBy { it.third }

/** «Lo pasaste», «Lo leíste», «La viste»… */
fun verboDeTerminar(k: Kind): String = when { k.isGame -> "Lo pasaste"; k.isBook -> "Lo leíste"; k.isMovie || k.isTv -> "La viste"; else -> "Lo terminaste" }

/** «Tal día como hoy», en la portada (si hay algo). */
@Composable
fun TalDiaComoHoy(todas: List<Pair<Kind, Ficha>>, nav: Nav) {
    val hits = remember(todas) { efemerides(todas) }
    if (hits.isEmpty()) return
    Seccion("Tal día como hoy")
    hits.take(4).forEachIndexed { i, (k, f, anos) ->
        if (i > 0) Filete(1.dp, Line)
        Row(Modifier.fillMaxWidth().clickable { nav.ir(Pantalla.Detalle(k.id, f.id, Pantalla.Inicio)) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(86.dp)) {
                Text("$anos", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 44.sp)
                Text(if (anos == 1) "AÑO" else "AÑOS", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            }
            MiniPortada(f, Modifier.width(40.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                FitTitle(f.nombre, 22.sp, 25.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(verboDeTerminar(k), if (!f.sinValorar) "nota ${f.general}" else null).joinToString(" · ").uppercase(), color = Acento, fontFamily = Body,
                    fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            }
        }
    }
}

/**
 * Buscar en todo lo tuyo a la vez: tus categorías (por nombre, autor, estudio o plataforma) y tu diario. Sin escribir, lo último que has
 * tocado. Intro abre lo primero; Esc cierra.
 */
@Composable
fun DialogoBuscarEnTodo(nav: Nav, onCerrar: () -> Unit) {
    LaFamilia.cambios
    var q by remember { mutableStateOf("") }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    val kinds = Cuenta.kinds()
    val todo = remember(LaFamilia.cambios) { kinds.flatMap { k -> Cuenta.fichas(k.id).map { k to it } } }
    val res = remember(q, todo) { if (q.isBlank()) todo.sortedByDescending { it.second.actividad }.take(8) else todo.filter { (_, f) -> coincide(q, f.nombre, f.creador, f.plataforma) }.sortedByDescending { it.second.actividad }.take(80) }
    val notas = remember(q, LaFamilia.cambios) { if (q.trim().length < 3) emptyList() else Cuenta.notas().filter { coincide(q, it.texto) }.take(20) }
    fun abre(k: Kind, f: Ficha) { onCerrar(); nav.ir(Pantalla.Detalle(k.id, f.id, Pantalla.Inicio)) }
    Dialogo("Buscar en todo", onCerrar, ancho = 820.dp, botones = { BotonSuave("Cerrar") { onCerrar() } }) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().focusRequester(foco).onPreviewKeyEvent { e ->
            when {
                e.type != KeyEventType.KeyDown -> false
                e.key == Key.Escape -> { onCerrar(); true }
                e.key == Key.Enter -> { res.firstOrNull()?.let { (k, f) -> abre(k, f) }; true }
                else -> false
            }
        }, placeholder = { Text("Título, autor, estudio, plataforma o algo que escribiste en tu diario") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            if (q.isBlank()) item { Box(Modifier.padding(top = 10.dp, bottom = 4.dp)) { Antetitulo("Lo último", tam = 12.sp) } }
            else if (res.isEmpty() && notas.isEmpty()) item { MagVacio("Nada con «${q.trim()}».", 18) }
            val grupos = if (q.isBlank()) listOf(null to res) else kinds.map { k -> k to res.filter { it.first.id == k.id } }.filter { it.second.isNotEmpty() }
            grupos.forEach { (k, l) ->
                if (k != null) item(key = "k${k.id}") { Box(Modifier.padding(top = 14.dp, bottom = 4.dp)) { Antetitulo("${k.title} · ${l.size}", tam = 12.sp) } }
                items(l, key = { (kk, f) -> "f${kk.id}-${f.id}" }) { (kk, f) ->
                    Row(Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand).clickable { abre(kk, f) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniPortada(f, Modifier.width(36.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(kk.title, estadoDe(f, kk), f.creador, f.plataforma).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontFamily = Body, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (f.valorada && !f.sinValorar) Text("${f.general}", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    }
                    Filete(1.dp, Line)
                }
            }
            if (notas.isNotEmpty()) {
                item(key = "notas") { Box(Modifier.padding(top = 14.dp, bottom = 4.dp)) { Antetitulo("En tu diario · ${notas.size}", tam = 12.sp) } }
                items(notas, key = { "n${it.id}" }) { n ->
                    Column(Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand).clickable { onCerrar(); nav.ir(Pantalla.Diario) }.padding(vertical = 8.dp)) {
                        Pie(if (n.dia == LocalDate.now().toEpochDay()) "Hoy" else diaLargo(LocalDate.ofEpochDay(n.dia)))
                        Text(n.texto.let { if (it.length > 220) it.take(220).trimEnd() + "…" else it }, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Normal, fontSize = 17.sp, lineHeight = 23.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    Filete(1.dp, Line)
                }
            }
        }
    }
}
