package com.gonzalo.marfil.pc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.nucleo.Nodo
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*

/**
 * Lo que escribes en Cobalto, para leerlo aquí: los capítulos que das por terminados (`capitulos/<obra>~<capítulo>`, la copia de solo
 * lectura que publica Cobalto), por obras y en su orden.
 */
@Composable
fun PantEscritos(nav: Nav) {
    LaFamilia.cambios
    val caps = LaFamilia.coleccion("capitulos").vivos()
    val obras = caps.groupBy { it.texto("ref").ifBlank { it.ref.substringBefore('~') } }
    PaginaC(ancho = 1000.dp) {
        Text("ESCRITOS", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text("Los capítulos que das por terminados en Thot, para leerlos aquí.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
        if (obras.isEmpty()) Vacio("Nada todavía", "En Thot, marca un capítulo como terminado y aparece aquí.")
        obras.forEach { (_, l) ->
            val o = l.first()
            Spacer(Modifier.height(20.dp)); Filete(2.dp)
            Text(o.texto("t"), color = Charcoal, fontFamily = Grotesca, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 10.dp))
            if (o.texto("a").isNotBlank()) Text(o.texto("a"), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
            l.sortedBy { it.entero("n") }.forEach { c ->
                Row(Modifier.fillMaxWidth().levanta(alto = 1.dp) { nav.ir(Pantalla.Capitulo(c.ref)) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${c.entero("n")}. " + c.texto("c").ifBlank { "Capítulo" }, Modifier.weight(1f), color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${c.entero("p")} palabras", color = Muted, fontFamily = Body, fontSize = 12.sp)
                }
            }
        }
    }
}

/** Un capítulo de Cobalto, para leer (su Markdown ligero: títulos, citas, cursivas, negritas y saltos de escena). */
@Composable
fun PantCapitulo(ref: String, nav: Nav) {
    LaFamilia.cambios
    val c: Nodo? = LaFamilia.coleccion("capitulos").nodo(ref)
    Pagina(ancho = 820.dp) {
        VolverRevista({ nav.ir(Pantalla.Escritos) }, "Escritos")
        if (c == null || c.borrado) { Vacio("Este capítulo ya no está terminado en Thot"); return@Pagina }
        Spacer(Modifier.height(20.dp))
        Text(c.texto("t").uppercase(), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text(c.texto("c"), color = Charcoal, fontFamily = Display, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
        SelectionContainer {
            Column {
                c.texto("x").split('\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { p ->
                    when {
                        p == "***" || p == "* * *" -> Text("* * *", Modifier.fillMaxWidth().padding(vertical = 14.dp), color = Muted, fontFamily = Display, fontSize = 20.sp, textAlign = TextAlign.Center)
                        p.startsWith("#") -> Text(p.trimStart('#').trim(), Modifier.padding(vertical = 10.dp), color = Charcoal, fontFamily = Display, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        p.startsWith("> ") -> Text(enLinea(p.removePrefix("> ")), Modifier.padding(start = 28.dp, bottom = 12.dp), color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 32.sp)
                        else -> Text(enLinea(p), Modifier.padding(bottom = 12.dp), color = Charcoal, fontFamily = Display, fontSize = 20.sp, lineHeight = 32.sp, textAlign = TextAlign.Justify)
                    }
                }
            }
        }
    }
}

/** `*cursiva*` y `**negrita**` de una línea. */
fun enLinea(t: String): AnnotatedString {
    val b = AnnotatedString.Builder()
    var i = 0; var neg = false; var cur = false
    while (i < t.length) {
        if (t.startsWith("**", i)) { if (neg) b.pop() else b.pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); neg = !neg; i += 2; continue }
        if (t[i] == '*') { if (cur) b.pop() else b.pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); cur = !cur; i++; continue }
        b.append(t[i]); i++
    }
    return b.toAnnotatedString()
}
