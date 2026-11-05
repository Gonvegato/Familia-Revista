package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Lo que Azabache enseña de sus hermanas: los capítulos terminados de Cobalto (para leerlos), la lectura de un libro según Marfil y
 * «Jugar» (que abre Hiedra).
 */

/** «Lectura» en la ficha de un libro: lo que Marfil cuenta (páginas, tiempo) y el enlace para seguir leyendo allí. */
@Composable
fun LecturaMarfil(id: Long, nombre: String, paginas: Int, minutos: Int) {
    val ctx = LocalContext.current
    val leido = remember(id, Hermanas.version) { Hermanas.leidoPorDia(ctx).values.sumOf { it[id] ?: 0L } }
    val min = maxOf(minutos.toLong(), leido / 60_000L)
    if (min > 0) {
        Text(readTime(min), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp)
        Text("LEYENDO, SEGÚN MARFIL", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    } else Text("Marfil aún no ha contado tiempo de este libro.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp)
    if (paginas > 0) Text("$paginas páginas", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(6.dp))
    if (Hermanas.instalada(ctx, Hermanas.PAQ_MARFIL)) MagLink("Leer en Marfil", accent = true) { Hermanas.leerEnMarfil(ctx, id) }
    else Text("Se lee en Marfil (no está en este móvil).", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp)
}

/** «Jugar» en la ficha de un juego: abre Hiedra, que es quien lo lanza (en el móvil, en el PC o en streaming). */
@Composable
fun JugarHiedra(nombre: String) {
    val ctx = LocalContext.current
    if (Hermanas.instalada(ctx, Hermanas.PAQ_HIEDRA)) {
        MagLink("Jugar en Hiedra", "en el móvil, en el PC o en streaming", accent = true) { Hermanas.abrir(ctx, Hermanas.PAQ_HIEDRA) }
    } else Text("Se juega desde Hiedra (no está en este móvil).", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp)
}

/** «Escritos» en la portada: el último capítulo que diste por terminado en Cobalto, y cuántas palabras llevas esta semana. */
@Composable
fun RevistaEscritos(irAEscritos: () -> Unit) {
    val ctx = LocalContext.current
    val caps = remember(Hermanas.version) { Hermanas.capitulos(ctx) }
    val palabras = remember(Hermanas.version) { Hermanas.palabras(ctx) }
    val hoy = today()
    val semana = (0..6).sumOf { palabras[hoy - it] ?: 0 }
    val ultimo = caps.maxByOrNull { it.u } ?: return
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(38.dp))
        Text("ESCRITOS", color = ColGeneral, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 10.dp))
        FitTitle(ultimo.titulo.ifBlank { "Sin título" }, 30.sp, 33.sp, color = Charcoal, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clickable(onClick = irAEscritos))
        Text(listOfNotNull("último capítulo terminado: «${ultimo.nombre.ifBlank { "capítulo ${ultimo.numero}" }}»", if (semana > 0) "esta semana ${cifraE(semana)} palabras" else null).joinToString(" · "),
            color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        val marfil = remember { Hermanas.instalada(ctx, Hermanas.PAQ_MARFIL) }
        Text(if (marfil) "Leer en Marfil →" else "Leer lo terminado →", color = ColGeneral, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.padding(top = 10.dp).clickable { if (!marfil || !Hermanas.leerEnMarfil(ctx, ultimo.obra)) irAEscritos() })
    }
}

/** Los capítulos terminados de Cobalto, por obra, para leerlos aquí (solo lectura: se escribe en Cobalto). */
@Composable
fun LecturaCobaltoScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val caps = remember(Hermanas.version) { Hermanas.capitulos(ctx) }
    var obra by remember { mutableStateOf<String?>(null) }
    var abierto by remember { mutableStateOf<Hermanas.Capitulo?>(null) }
    val cap = abierto
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
        if (cap != null) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) { VolverRevista({ abierto = null }, "Escritos") }
            Spacer(Modifier.height(14.dp))
            Text(cap.titulo.uppercase(), color = ColGeneral, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            FitTitle(cap.nombre.ifBlank { "Capítulo ${cap.numero}" }, 34.sp, 37.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Text(listOfNotNull(cap.parte.ifBlank { null }, "${cap.palabras} palabras").joinToString(" · "), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            TextoCapitulo(cap.texto)
            Spacer(Modifier.height(40.dp))
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) { VolverRevista(if (obra == null) onBack else ({ obra = null }), if (obra == null) "Azabache" else "Escritos") }
            Spacer(Modifier.height(14.dp))
            val o = obra
            if (o == null) {
                val nObras = caps.map { it.obra }.distinct().size
                Text("SECCIÓN · $nObras ${if (nObras == 1) "OBRA" else "OBRAS"}", color = ColGeneral, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                FitTitle("Escritos", 44.sp, 46.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                val marfil = remember { Hermanas.instalada(ctx, Hermanas.PAQ_MARFIL) }
                val cobalto = remember { Hermanas.instalada(ctx, Hermanas.PAQ_COBALTO) }
                Text(if (marfil) "Los capítulos que diste por terminados en Cobalto. Se leen con el lector de Marfil; aquí, solo de un vistazo." else "Los capítulos que diste por terminados en Cobalto. Aquí solo se leen (con Marfil en el móvil, con su lector).",
                    color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp, bottom = if (marfil) 8.dp else 16.dp))
                if (marfil) Text("Leer en Marfil →", color = ColGeneral, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 16.dp).clickable { Hermanas.leerEnMarfil(ctx) })
                val obras = caps.groupBy { it.obra }
                if (obras.isEmpty()) Text(if (Hermanas.ultimaVez(ctx) == 0L) "Todavía no se ha leído tu cuenta: entra en Ajustes → Cuenta y pareja." else "Cobalto aún no ha publicado ningún capítulo terminado.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                obras.forEach { (id, cs) ->
                    val t = cs.first()
                    Column(Modifier.fillMaxWidth().clickable { obra = id }.padding(vertical = 10.dp)) {
                        Text(t.titulo.ifBlank { "Sin título" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text(listOfNotNull(t.autor.ifBlank { null }, "${cs.size} ${if (cs.size == 1) "capítulo" else "capítulos"}", "${cifraE(cs.sumOf { it.palabras })} palabras").joinToString(" · "), color = Muted, fontSize = 13.sp)
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            if (marfil) Text("Leer en Marfil →", color = ColGeneral, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { Hermanas.leerEnMarfil(ctx, id) })
                            if (cobalto) Text("Escribir en Cobalto →", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { Hermanas.escribirEnCobalto(ctx, id) })
                        }
                    }
                    Filete()
                }
            } else {
                val cs = caps.filter { it.obra == o }
                val t = cs.firstOrNull()
                if (t != null) {
                    Text(t.autor.uppercase(), color = ColGeneral, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    FitTitle(t.titulo.ifBlank { "Sin título" }, 40.sp, 42.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
                }
                var parte = ""
                cs.forEach { c ->
                    if (c.parte.isNotBlank() && c.parte != parte) { parte = c.parte; Text(parte.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) }
                    Column(Modifier.fillMaxWidth().clickable { abierto = c }.padding(vertical = 10.dp)) {
                        Text("${c.numero}. ${c.nombre.ifBlank { "Capítulo ${c.numero}" }}", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        Text("${c.palabras} palabras", color = Muted, fontSize = 13.sp)
                    }
                    Filete()
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/** El texto de un capítulo con el Markdown ligero de Cobalto: títulos (#), citas (>), *cursiva*, **negrita** y *** (salto de escena). */
@Composable
fun TextoCapitulo(texto: String) {
    texto.split("\n").forEach { linea ->
        val l = linea.trimEnd()
        when {
            l.isBlank() -> Spacer(Modifier.height(10.dp))
            l == "***" || l == "---" -> Text("✻", color = Muted, fontSize = 18.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            l.startsWith("## ") -> Text(l.removePrefix("## "), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            l.startsWith("# ") -> Text(l.removePrefix("# "), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            l.startsWith("> ") -> Text(sinMarcas(l.removePrefix("> ")), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 26.sp, color = Muted, modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp))
            else -> Text(sinMarcas(l), fontFamily = Display, fontSize = 18.sp, lineHeight = 28.sp, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

@Composable
private fun Filete() = Box(Modifier.fillMaxWidth().height(1.dp).background(Charcoal.copy(alpha = 0.35f)))

private fun sinMarcas(s: String) = s.replace("***", "").replace("**", "").replace(Regex("(?<!\\*)\\*(?!\\*)"), "")
