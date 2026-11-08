package com.gonzalo.shai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que se está pidiendo a Claude ahora mismo (para no pedirlo dos veces y enseñar que va). */
object EnCurso {
    val ids = androidx.compose.runtime.mutableStateMapOf<String, String>()
}

val PESTANAS = listOf("Portada", "Red", "Mensajes", "Ofertas", "Cifras", "Vida", "Perfil")

/** Una carrera, en pestañas: la portada (la prensa y lo que pasa), la red, los mensajes y las decisiones, las ofertas, las cifras, la vida y el perfil. */
@Composable
fun PantCarrera(id: String, alVolver: () -> Unit, alPartido: () -> Unit, alAjustes: (Int) -> Unit, inicial: Int = 0) {
    Carreras.version
    var pestana by rememberSaveable { mutableIntStateOf(inicial) }
    val u = Carreras.de(id) ?: run { PaginaRevista { RevistaTopBar(alVolver, "Shai"); Nota("Esta carrera ya no está.") }; return }
    val entradas = Carreras.entradasDe(id)
    val decisiones = entradas.count { it.decision?.abierta == true }
    val ofertas = entradas.sumOf { e -> e.ofertas.count { it.estado == Oferta.ABIERTA } }
    PaginaRevista {
        RevistaTopBar(alVolver, "Shai") { EnlaceRevista("Ajustes") { alAjustes(0) } }
        Antetitulo(listOfNotNull(u.club, u.temporada.ifBlank { null }, "jornada\u00A0${u.paso}").joinToString(" · "), Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle(u.nombre, 48.sp, 52.sp, letterSpacing = (-1.4).sp, modifier = Modifier.padding(top = 2.dp))
        Filete(3.dp)
        Spacer(Modifier.height(12.dp))
        AccionGrande(Icons.Filled.SportsSoccer, "Meter el partido", Modifier.fillMaxWidth()) { alPartido() }
        Spacer(Modifier.height(4.dp))
        PestanasRevista(PESTANAS, pestana, cuentas = listOf(null, null, decisiones.takeIf { it > 0 }, ofertas.takeIf { it > 0 }, null, null, null)) { pestana = it }
        Spacer(Modifier.height(10.dp))
        when (pestana) {
            0 -> TabPortada(u, entradas, alMensajes = { pestana = 2 }, alOfertas = { pestana = 3 }, alAjustes = alAjustes)
            1 -> TabRed(entradas)
            2 -> TabMensajes(u, entradas)
            3 -> TabOfertas(u, entradas)
            4 -> TabCifras(u)
            5 -> TabVida(u)
            else -> TabPerfil(u, alBorrada = alVolver)
        }
    }
}

/** «+3 fama · −2 disciplina · +25.000 €». */
fun textoEfectos(e: Efectos): String = buildList {
    if (e.dinero != 0L) add((if (e.dinero > 0) "+" else "") + Formato.eurosCorto(e.dinero))
    Fuera.TODOS.forEach { (k, n) -> e.fuera[k]?.takeIf { it != 0 }?.let { add((if (it > 0) "+" else "−") + kotlin.math.abs(it) + " " + n.lowercase()) } }
    e.relaciones.forEach { (n, d) -> if (d != 0) add("$n ${if (d > 0) "+" else "−"}${kotlin.math.abs(d)}") }
    e.nuevas.forEach { add("nueva: ${it.nombre}") }
}.joinToString(" · ")

/** La portada del periódico. */
@Composable
fun PortadaPrensa(p: Prensa) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(p.cabecera.uppercase(), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 3.sp, lineHeight = 19.sp)
        Filete(2.dp, modifier = Modifier.padding(top = 4.dp))
        FitTitle(p.titular, 34.sp, 37.sp, letterSpacing = (-0.8).sp, modifier = Modifier.padding(top = 8.dp))
        if (p.entradilla.isNotBlank()) Text(p.entradilla, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 6.dp))
        p.otros.forEach { o ->
            Filete(1.dp, Line, Modifier.padding(top = 8.dp))
            Text(o, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun TabPortada(u: Universo, entradas: List<Entrada>, alMensajes: () -> Unit, alOfertas: () -> Unit, alAjustes: (Int) -> Unit) {
    val ambito = rememberCoroutineScope()
    var aviso by remember { mutableStateOf<String?>(null) }
    if (!ClaveApi.hay) Panel(alHacer = { alAjustes(2) }) {
        Antetitulo("Falta tu clave de la API")
        Cuerpo("Sin ella, los partidos se guardan pero nadie escribe lo de fuera del campo.", Modifier.padding(top = 4.dp))
        MagLink("Ponerla en Ajustes → IA", accent = true) { alAjustes(2) }
    }
    entradas.filter { it.pendiente.isNotBlank() }.take(3).forEach { e ->
        Panel {
            Antetitulo("Falta lo de después")
            Cuerpo("Del partido contra ${e.partido?.rival.orEmpty()} (${e.partido?.resultado.orEmpty()}) falta " +
                e.pendiente.split(',').joinToString(" y ") { if (it == "red") "la red" else "la prensa" } + ".", Modifier.padding(top = 4.dp))
            val va = EnCurso.ids[e.id]
            MagLink(va ?: "Volver a pedirlo", accent = true) {
                if (va != null) return@MagLink
                EnCurso.ids[e.id] = "Pidiéndolo…"
                ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.reintenta(e.id) }; EnCurso.ids.remove(e.id) }
            }
        }
    }
    val decisiones = entradas.count { it.decision?.abierta == true }
    val ofertas = entradas.sumOf { e -> e.ofertas.count { it.estado == Oferta.ABIERTA } }
    if (decisiones > 0) MagLink(if (decisiones == 1) "Tienes una decisión pendiente" else "Tienes $decisiones decisiones pendientes", "En Mensajes", accent = true) { alMensajes() }
    if (ofertas > 0) MagLink(if (ofertas == 1) "Tienes una oferta sobre la mesa" else "Tienes $ofertas ofertas sobre la mesa", "En Ofertas", accent = true) { alOfertas() }
    entradas.firstOrNull { it.prensa != null }?.prensa?.let { PortadaPrensa(it) }
    Spacer(Modifier.height(8.dp))
    // Lo de entre partidos que está por llegar
    val ahora = System.currentTimeMillis()
    val porSalir = Carreras.entradasDe(u.id, todas = true).filter { !it.visible(ahora) }
    if (u.lotes.isNotEmpty() || porSalir.isNotEmpty()) {
        Nota(when {
            u.lotes.isNotEmpty() -> "Mientras tanto están pasando cosas: llegan en un rato (por lotes, a mitad de precio)."
            else -> "Algo más pasará hacia las " + java.text.SimpleDateFormat("HH:mm", java.util.Locale("es", "ES")).format(java.util.Date(porSalir.minOf { it.visibleDesde })) + "."
        }, Modifier.padding(vertical = 6.dp))
    }
    val va = EnCurso.ids[u.id]
    MagLink(va ?: "¿Qué ha pasado esta semana?", if (va == null) "Una cosa más, ahora (Sonnet, unos céntimos)" else null) {
        if (va != null || !ClaveApi.hay) return@MagLink
        EnCurso.ids[u.id] = "Escribiendo lo que pasa…"; aviso = null
        ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.eventoAhora(u.id).second }; EnCurso.ids.remove(u.id) }
    }
    aviso?.let { Nota(it, Modifier.padding(top = 4.dp), color = Acento) }
    Spacer(Modifier.height(14.dp))
    Antetitulo("Lo que ha pasado")
    Spacer(Modifier.height(4.dp))
    if (entradas.isEmpty()) Nota("Aún nada. Mete tu primer partido.", Modifier.padding(vertical = 8.dp))
    entradas.forEach { e -> TarjetaEntrada(u, e) }
}

/** Una cosa de las que pasan, en la portada. */
@Composable
fun TarjetaEntrada(u: Universo, e: Entrada) {
    Panel {
        Antetitulo(e.tipo.nombre + (if (e.paso > 0) " · jornada ${e.paso}" else ""), tam = 11.sp)
        val p = e.partido
        val titulo = when {
            p != null -> (if (p.local) "${u.club} ${p.golesFavor}-${p.golesContra} ${p.rival}" else "${p.rival} ${p.golesContra}-${p.golesFavor} ${u.club}")
            else -> e.titulo
        }
        if (titulo.isNotBlank()) Text(titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 4.dp))
        if (p != null) Text(listOfNotNull(p.competicion.ifBlank { null },
            if (u.modo == Rol.JUGADOR) listOfNotNull("${p.minutos}'", p.goles.takeIf { it > 0 }?.let { "$it gol${if (it > 1) "es" else ""}" }, p.asistencias.takeIf { it > 0 }?.let { "$it asist." }, p.nota.takeIf { it > 0 }?.let { "nota ${Formato.decimal(it)}" }).joinToString(" · ")
            else p.goleadores.ifBlank { null }).joinToString(" · ").uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp))
        e.prensa?.takeIf { p != null }?.let { Text("«${it.titular}»", color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp)) }
        if (e.texto.isNotBlank()) Text(e.texto, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
        e.decision?.let { d ->
            Text(if (d.abierta) "Tienes que decidir: ${d.texto}" else "Elegiste: «${d.elegida}»", color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
        }
        val ef = textoEfectos(e.efectos)
        if (ef.isNotBlank()) Text(ef.uppercase(), color = Dorado, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 6.dp))
        if (e.posts.isNotEmpty() || e.dms.isNotEmpty()) Etiqueta(listOfNotNull(e.posts.size.takeIf { it > 0 }?.let { "$it en la red" }, e.dms.size.takeIf { it > 0 }?.let { "$it mensaje${if (it > 1) "s" else ""}" }).joinToString(" · "), Modifier.padding(top = 6.dp))
    }
}

/** Un post, como en X: quién, su @, lo que dice y lo que ha gustado. */
@Composable
fun PostRevista(p: Post) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(p.autor.ifBlank { p.usuario }, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
            if (p.usuario.isNotBlank()) Text("  ${p.usuario}", color = Muted, fontFamily = Body, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        Text(p.texto, color = Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 3.dp))
        Text("♥ ${corto(p.megusta)}   ⟲ ${corto(p.reposts)}", color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
    Filete(1.dp, Line)
}

/** «1,2 mil», «3,4 M». */
fun corto(n: Int): String = when {
    n >= 1_000_000 -> Formato.decimal(n / 1_000_000.0) + " M"
    n >= 10_000 -> Formato.cifra(Math.round(n / 1000.0)) + " mil"
    else -> Formato.cifra(n)
}

@Composable
private fun TabRed(entradas: List<Entrada>) {
    val posts = entradas.flatMap { it.posts }
    if (posts.isEmpty()) { Nota("La red está en silencio. Mete un partido y verás."); return }
    posts.forEach { PostRevista(it) }
}

@Composable
private fun TabMensajes(u: Universo, entradas: List<Entrada>) {
    val ambito = rememberCoroutineScope()
    val abiertas = entradas.filter { it.decision?.abierta == true }
    if (abiertas.isNotEmpty()) { Antetitulo("Por decidir"); Spacer(Modifier.height(4.dp)) }
    abiertas.forEach { e ->
        val d = e.decision!!
        var libre by rememberSaveable(d.id) { mutableStateOf("") }
        var aviso by remember(d.id) { mutableStateOf<String?>(null) }
        val va = EnCurso.ids[d.id]
        fun contesta(t: String) {
            if (va != null || t.isBlank()) return
            EnCurso.ids[d.id] = "Pasando…"; aviso = null
            ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.decide(e.id, t) }; EnCurso.ids.remove(d.id) }
        }
        Panel {
            if (d.de.isNotBlank()) Etiqueta("De ${d.de}")
            if (e.titulo.isNotBlank()) Text(e.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 2.dp))
            Text(d.texto, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 4.dp))
            d.opciones.forEach { o -> BotonSuave(o, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { contesta(o) } }
            Campo("O haz otra cosa", libre, "Lo que tú quieras", lineas = 3) { libre = it }
            if (libre.isNotBlank()) EnlaceRevista("Contestar", acento = true) { contesta(libre) }
            va?.let { Nota(it, Modifier.padding(top = 6.dp)) }
            aviso?.let { Nota(it, Modifier.padding(top = 6.dp), color = Acento) }
        }
    }
    // Los mensajes, por quién los manda (lo más nuevo, arriba)
    val porQuien = LinkedHashMap<String, MutableList<Dm>>()
    entradas.forEach { e -> e.dms.forEach { m -> porQuien.getOrPut(if (m.mio) "Tú" else m.de.ifBlank { "Alguien" }) { mutableListOf() }.add(m) } }
    porQuien.remove("Tú")
    if (porQuien.isEmpty() && abiertas.isEmpty()) { Nota("Ningún mensaje todavía."); return }
    if (porQuien.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Antetitulo("Mensajes") }
    porQuien.forEach { (quien, ms) ->
        Panel {
            Text(quien, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 23.sp)
            u.entorno.firstOrNull { Vida.mismo(it.nombre, quien) }?.let { Etiqueta(it.rol + " · afinidad ${it.afinidad}") }
            ms.forEach { m -> Text(m.texto, color = Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
    val resueltas = entradas.filter { it.decision?.abierta == false }
    if (resueltas.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Antetitulo("Lo que decidiste") }
    resueltas.forEach { e ->
        val d = e.decision!!
        Panel {
            Text(d.texto, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp)
            Text("Elegiste: ${d.elegida}", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
            if (d.consecuencia.isNotBlank()) Text(d.consecuencia, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun TabOfertas(u: Universo, entradas: List<Entrada>) {
    val ambito = rememberCoroutineScope()
    val todas = entradas.flatMap { e -> e.ofertas.map { e to it } }
    if (todas.isEmpty()) { Nota("Ninguna oferta todavía. Ya llegarán."); return }
    var aviso by remember { mutableStateOf<String?>(null) }
    Cuerpo("La app manda: si aceptas aquí, hazlo también en tu partida de FC.", Modifier.padding(bottom = 6.dp), tam = 13)
    todas.sortedBy { if (it.second.estado == Oferta.ABIERTA) 0 else 1 }.forEach { (e, o) ->
        Panel {
            Antetitulo((Oferta.TIPOS[o.tipo] ?: o.tipo) + if (o.estado != Oferta.ABIERTA) " · ${o.estado}" else "", tam = 11.sp)
            Text(o.de, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, modifier = Modifier.padding(top = 4.dp))
            if (o.texto.isNotBlank()) Text(o.texto, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
            if (o.salario > 0) MagFact(if (o.tipo == "patrocinio") "A la semana, de más" else "Sueldo a la semana", Formato.euros(o.salario) + (if (o.tipo != "patrocinio" && u.salario > 0) " (ahora ${Formato.eurosCorto(u.salario)})" else ""))
            if (o.prima > 0) MagFact("Prima", Formato.euros(o.prima))
            if (o.anios > 0) MagFact("Contrato", "${o.anios} año${if (o.anios > 1) "s" else ""}")
            if (o.estado == Oferta.ABIERTA) {
                val va = EnCurso.ids[o.id]
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BotonAcento(va ?: "Aceptar", modifier = Modifier.weight(1f)) {
                        if (va != null) return@BotonAcento
                        EnCurso.ids[o.id] = "Firmando…"
                        ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.oferta(e.id, o.id, true) }; EnCurso.ids.remove(o.id) }
                    }
                    BotonSuave("Rechazar", modifier = Modifier.weight(1f)) {
                        ambito.launch { withContext(Dispatchers.IO) { Motor.oferta(e.id, o.id, false) } }
                    }
                }
            }
        }
    }
    aviso?.let { Nota(it, Modifier.padding(top = 6.dp), color = Acento) }
}
