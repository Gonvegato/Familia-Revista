package com.gonzalo.cobalto.pc

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.cobalto.Doc
import com.gonzalo.cobalto.EscribirEstructura
import com.gonzalo.cobalto.Formato
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.launch

/** Un trozo del libro para leerlo de corrido: el comienzo de una parte, el de un capítulo o una escena. */
private sealed class Trozo {
    class Parte(val d: Doc) : Trozo()
    class Capitulo(val d: Doc, val n: Int) : Trozo()
    class Escena(val d: Doc, val primera: Boolean) : Trozo()
}

/**
 * Leer la obra entera, como un libro: la portada, las partes, los capítulos (en página nueva, con su número) y las escenas, con
 * el texto justificado y sangrado y los saltos de escena. A la izquierda, el índice para ir a un capítulo; «Escribir aquí» en cada
 * escena la abre en el editor. El tamaño de la letra es el del editor.
 */
@Composable
fun LecturaObra(obra: Doc, abrirEscena: (String) -> Unit) {
    val e = Cuaderno.escritos
    val ver = e.version
    val pid = obra.id
    PrefsEditor.asegura()
    val est = remember(ver, pid) { EscribirEstructura.estructura(e.deTipo(com.gonzalo.cobalto.Escritos.T_CAP, pid), e.partes(pid)) }
    val trozos = remember(ver, pid) {
        val l = ArrayList<Trozo>()
        var n = 0
        fun cap(c: Doc) { n++; l += Trozo.Capitulo(c, n); e.escenas(c.id).filter { it.s("texto").isNotBlank() }.forEachIndexed { i, s -> l += Trozo.Escena(s, i == 0) } }
        est.items.forEach { when (it) { is EscribirEstructura.Item.Parte -> { l += Trozo.Parte(it.parte); it.caps.forEach { c -> cap(c) } }; is EscribirEstructura.Item.Cap -> cap(it.cap) } }
        l
    }
    val lista = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val total = e.palabrasObra(pid)
    val tam = (PrefsEditor.tam - 1).toInt().coerceIn(15, 30)
    Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
        // El índice
        val scrollIndice = rememberScrollState()
        Column(Modifier.width(250.dp).fillMaxHeight().verticalScroll(scrollIndice).padding(end = 14.dp, bottom = 30.dp)) {
            Antetitulo("Índice", tam = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
            trozos.forEachIndexed { i, t ->
                when (t) {
                    is Trozo.Parte -> Text(t.d.s("titulo").ifBlank { "Parte" }.uppercase(), Modifier.padding(top = 12.dp, bottom = 2.dp).pointerHoverIcon(PointerIcon.Hand).clickable { scope.launch { lista.animateScrollToItem(i + 1) } },
                        color = Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    is Trozo.Capitulo -> Text("${t.n}. " + t.d.s("titulo").ifBlank { "Sin título" }, Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand).clickable { scope.launch { lista.animateScrollToItem(i + 1) } }.padding(vertical = 4.dp),
                        color = Charcoal, fontFamily = Display, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    else -> {}
                }
            }
            Spacer(Modifier.height(16.dp))
            val min = maxOf(1, (total + 219) / 220)
            Text("${Formato.cifra(total)} palabras · " + (if (min < 60) "$min min de lectura" else "unas ${(min + 30) / 60} h de lectura"), color = Muted, fontFamily = Body, fontSize = 11.sp)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            LazyColumn(Modifier.fillMaxSize(), state = lista, horizontalAlignment = Alignment.CenterHorizontally, contentPadding = PaddingValues(bottom = 120.dp)) {
                item("portada") {
                    Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = 32.dp).padding(top = 70.dp, bottom = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (obra.s("autor").isNotBlank()) Text(obra.s("autor").uppercase(), color = Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Spacer(Modifier.height(14.dp))
                        Text(obra.s("titulo").ifBlank { "Sin título" }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 52.sp, lineHeight = 58.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        Box(Modifier.width(60.dp).height(3.dp).background(Acento))
                        if (obra.s("sinopsis").isNotBlank()) Text(obra.s("sinopsis"), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 25.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 22.dp))
                        if (trozos.none { it is Trozo.Escena }) Text("Aún no hay nada escrito que leer.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.padding(top = 40.dp))
                    }
                }
                items(trozos.size) { i ->
                    val col = Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = 32.dp)
                    when (val t = trozos[i]) {
                        is Trozo.Parte -> Column(col.padding(top = 90.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PARTE", color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                            Text(t.d.s("titulo").ifBlank { "Sin título" }, color = Charcoal, fontFamily = Display, fontSize = 40.sp, lineHeight = 46.sp, textAlign = TextAlign.Center)
                        }
                        is Trozo.Capitulo -> Column(col.padding(top = 80.dp, bottom = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CAPÍTULO ${t.n}", color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(t.d.s("titulo").ifBlank { "" }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, textAlign = TextAlign.Center)
                        }
                        is Trozo.Escena -> Column(col) {
                            if (!t.primera) Text("* * *", Modifier.fillMaxWidth().padding(vertical = 18.dp), color = Muted, fontFamily = Display, fontSize = tam.sp, textAlign = TextAlign.Center, letterSpacing = 6.sp)
                            TextoLectura(t.d.s("texto"), tam = tam, libro = true)
                            Text("ESCRIBIR AQUÍ →", Modifier.align(Alignment.End).pointerHoverIcon(PointerIcon.Hand).clickable { abrirEscena(t.d.id) }.padding(top = 6.dp, bottom = 4.dp),
                                color = Muted.copy(alpha = 0.6f), fontFamily = Body, fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                        }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(lista), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

