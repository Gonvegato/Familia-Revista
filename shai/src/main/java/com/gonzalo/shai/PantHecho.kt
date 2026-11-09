package com.gonzalo.shai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- el + con sus dos cosas

/** El botón + abierto: «Hecho histórico» y «Meter el partido», sobre un velo. */
@Composable
fun MenuMas(alCerrar: () -> Unit, alHecho: () -> Unit, alPartido: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).tocable(onClick = alCerrar).semantics { contentDescription = "Cerrar el menú" }) {
        Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 84.dp), horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OpcionMas("Hecho histórico", "Algo fuera de lo normal que lo cambia todo", Icons.Filled.Star, Dorado, alHecho)
            OpcionMas("Meter el partido", "Resultado, minutos, goles…", Icons.Filled.SportsSoccer, Acento, alPartido)
            Box(Modifier.size(58.dp).clip(CircleShape).background(Paper).tocable(onClick = alCerrar).semantics { contentDescription = "Cerrar" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, null, tint = Charcoal, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun OpcionMas(titulo: String, sub: String, icono: androidx.compose.ui.graphics.vector.ImageVector, color: Color, alHacer: () -> Unit) {
    Row(Modifier.tocable(onClick = alHacer).semantics { contentDescription = titulo }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(14.dp)).background(Paper).padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.End) {
            Text(titulo, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
            Text(sub, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(48.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) { Icon(icono, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
    }
}

// ---------------------------------------------------------------- el formulario

/**
 * Contar un hecho histórico: lo escribes con tus palabras, eliges hasta dónde llega el ruido y la app te enseña lo que ha entendido (lo
 * puedes desmarcar) antes de mandarlo. Al mandarlo: la comprobación en la web (si hay algo de precocidad) y la edición especial.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PantHecho(u: Universo, alVolver: () -> Unit, alHecho: () -> Unit, texto0: String = "", entendido0: Narrador.LoEntendido? = null) {
    val ambito = rememberCoroutineScope()
    var texto by rememberSaveable { mutableStateOf(texto0) }
    var ruido by rememberSaveable { mutableStateOf(Ruido.MUNDO.name) }
    var entendido by remember { mutableStateOf(entendido0) }
    val fuera = remember { mutableStateListOf<Int>() }
    var aviso by remember { mutableStateOf<String?>(null) }
    val clave = "h" + u.id
    val va = EnCurso.ids[clave]
    val r = Ruido.de(ruido)
    PaginaRevista {
        RevistaTopBar(alVolver, titulo = "Hecho histórico")
        Spacer(Modifier.height(6.dp))
        Cuerpo("Algo que te ha pasado en el juego y que lo cambia todo: una convocatoria que nadie esperaba, un debut imposible, un fichaje bomba. Cuéntalo como lo contarías; " +
            "es verdad en tu carrera y nadie lo va a cambiar.")
        Campo("¿Qué ha pasado?", texto, "Me convocan para el Mundial sin haber debutado…", lineas = 8) { texto = it.take(1200); if (entendido != null) { entendido = null; fuera.clear() } }
        Etiqueta("¿Hasta dónde llega el ruido?", Modifier.padding(top = 14.dp))
        FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Ruido.entries.forEach { x -> ChipFiltro(x.nombre, x == r) { ruido = x.name } }
        }
        Text(r.explica, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(14.dp))
        if (!ClaveApi.hay && !Prueba.activo) { Nota("Para esto hace falta tu clave de la API (Ajustes → IA), o el modo prueba."); return@PaginaRevista }
        val e = entendido
        if (e == null) {
            BotonRedondo(va ?: "Leerlo", Modifier.fillMaxWidth()) {
                if (va != null || texto.isBlank()) return@BotonRedondo
                EnCurso.ids[clave] = "Leyéndolo…"; aviso = null
                ambito.launch {
                    val (l, err) = withContext(Dispatchers.IO) { Motor.entiende(u.id, texto) }
                    EnCurso.ids.remove(clave); entendido = l; fuera.clear(); aviso = err
                }
            }
            Text(if (Prueba.activo) "Modo prueba: gratis." else "Primero la app te enseña lo que ha entendido (Haiku, menos de un céntimo); no se manda nada hasta que lo confirmes.",
                color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
            Tarjeta {
                Text("La app lo ha entendido así", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp)
                Text("Toca un punto para quitarlo si no es así.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
                e.hechos.forEachIndexed { i, (h, d) ->
                    val on = i !in fuera
                    Filete(1.dp, Line, Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth().tocable { if (on) fuera.add(i) else fuera.remove(i) }.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 1.dp).size(20.dp).clip(CircleShape).let { if (on) it.background(Acento) else it.border(1.5.dp, Edge, CircleShape) }, contentAlignment = Alignment.Center) {
                            if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h, color = if (on) Charcoal else Muted, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp)
                            if (d.isNotBlank()) Text(d, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
                if (e.hayPrecocidad) {
                    Filete(1.dp, Line, Modifier.padding(top = 8.dp))
                    Text("PRECOCIDAD · SE MIRA EN LA WEB", color = Dorado, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(e.precocidad, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp)
                    Text("Si ha pasado antes de verdad, dirá quién y cuándo; si no lo encuentra, lo dirá así, sin inventar.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            Text("¿No es eso? Cambia el texto y vuelve a leerlo.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
            val quedan = e.hechos.filterIndexed { i, _ -> i !in fuera }
            Spacer(Modifier.height(12.dp))
            BotonRedondo(va ?: "Que el mundo se entere", Modifier.fillMaxWidth()) {
                if (va != null || quedan.isEmpty()) return@BotonRedondo
                EnCurso.ids[clave] = if (e.hayPrecocidad) "Buscando en la web y escribiendo la edición especial…" else "Escribiendo la edición especial…"; aviso = null
                val conf = Narrador.LoEntendido(quedan, e.hayPrecocidad, e.precocidad, e.pregunta)
                ambito.launch {
                    val (ent, err) = withContext(Dispatchers.IO) { Motor.hecho(u.id, texto, conf, r) }
                    EnCurso.ids.remove(clave)
                    if (ent != null) alHecho() else aviso = err
                }
            }
            Text(when {
                Prueba.activo -> "Modo prueba: gratis, con textos de ejemplo."
                e.hayPrecocidad -> "Lo escribe Sonnet y busca en la web si ha pasado antes de verdad (una o dos búsquedas: unos céntimos más)."
                else -> "Lo escribe Sonnet (unos céntimos)."
            }, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        aviso?.let { Nota(it, Modifier.padding(top = 10.dp), color = Coral) }
    }
}

// ---------------------------------------------------------------- lo que sale en Inicio

/** La edición especial de un hecho histórico (arriba del todo en Inicio mientras sea de esta jornada): portada, la fama de golpe y lo que se mueve. */
@Composable
fun EdicionEspecial(u: Universo, e: Entrada, abierta0: Boolean = false, alRed: () -> Unit, alMensajes: () -> Unit) {
    val h = e.hecho ?: return
    var abierta by rememberSaveable(e.id) { mutableStateOf(abierta0) }
    val p = e.prensa
    Tarjeta(bordeIzq = Dorado, relleno = 0.dp, alHacer = if (p?.articulo?.isNotBlank() == true) ({ abierta = !abierta }) else null) {
        Row(Modifier.fillMaxWidth().background(Charcoal).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("★ EDICIÓN ESPECIAL", color = Paper, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
            Text(p?.cabecera?.uppercase().orEmpty().ifBlank { "HECHO HISTÓRICO" }, color = Dorado, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp,
                lineHeight = 15.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp).widthIn(max = 180.dp))
        }
        Column(Modifier.padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)) {
            Text(p?.titular ?: e.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp)
            (p?.entradilla?.ifBlank { null } ?: e.texto.ifBlank { null })?.let { Cursiva(it, Modifier.padding(top = 6.dp), color = Muted) }
            if (abierta && p != null) p.articulo.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotBlank() }.forEach { par ->
                Text(par, color = Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp))
            }
            if (p?.articulo?.isNotBlank() == true)
                Text(if (abierta) "Cerrar la noticia ↑" else "Leer la noticia →", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp))
            if (abierta) {
                Filete(1.dp, Line, Modifier.padding(top = 12.dp))
                Text("LO QUE CONTASTE", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 10.dp))
                Cursiva("«${h.texto}»", Modifier.padding(top = 4.dp), tam = 15)
            }
        }
    }
    // La fama, de golpe
    if (h.famaDespues != h.famaAntes) {
        Spacer(Modifier.height(10.dp))
        Tarjeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TituloTarjeta(if (Ruido.de(h.ruido).sinTope) "Tu fama, de golpe" else "Tu fama", Modifier.weight(1f), tam = 17)
                Text((if (h.famaDespues > h.famaAntes) "+" else "") + (h.famaDespues - h.famaAntes), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
            val a = h.famaAntes.coerceIn(0, 100) / 100f; val b = h.famaDespues.coerceIn(0, 100) / 100f
            Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Line)) {
                Box(Modifier.fillMaxWidth(maxOf(a, b)).height(8.dp).background(Acento))
                Box(Modifier.fillMaxWidth(minOf(a, b)).height(8.dp).background(Edge))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(h.nivelAntes.nombre, color = Muted, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (h.nivelDespues != h.nivelAntes) Text("→ ${h.nivelDespues.nombre}", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
    // Lo comprobado en la web
    if (h.precocidad.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Tarjeta(bordeIzq = Dorado) {
            Text("PRECOCIDAD", color = Dorado, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, lineHeight = 15.sp)
            TituloTarjeta(h.precocidad, Modifier.padding(top = 4.dp), tam = 17)
            TextoTarjeta(h.comprobado.ifBlank { "No se ha comprobado." }, Modifier.padding(top = 6.dp), color = Charcoal)
            if (h.fuentes.isNotEmpty()) Text("Fuentes: " + h.fuentes.take(3).joinToString(" · ") { it.first.take(60) }, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
    // Lo que se mueve
    val cosas = buildList {
        if (e.posts.isNotEmpty()) add("${e.posts.size} publicaciones en la red")
        if (e.dms.isNotEmpty()) add("${e.dms.size} mensajes")
        if (e.decision?.abierta == true) add("1 decisión")
        if (e.ofertas.isNotEmpty()) add("${e.ofertas.size} oferta${if (e.ofertas.size > 1) "s" else ""}")
        if (e.tendencia.isNotBlank()) add("${e.tendencia} es tendencia")
    }
    if (cosas.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Tarjeta {
            TituloTarjeta("Lo que se mueve", tam = 17)
            TextoTarjeta(cosas.joinToString(" · "), Modifier.padding(top = 4.dp), color = Charcoal)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (e.posts.isNotEmpty()) Text("La red →", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.tocable(onClick = alRed))
                if (e.dms.isNotEmpty() || e.decision?.abierta == true) Text("Mensajes →", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.tocable(onClick = alMensajes))
            }
        }
    }
}
