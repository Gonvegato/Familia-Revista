package com.gonzalo.shai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
fun PantCarrera(id: String, alVolver: () -> Unit, alPartido: () -> Unit, alAjustes: (Int) -> Unit, inicial: Int = 0, articuloAbierto: Boolean = false) {
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
        if (Prueba.activo) AvisoPrueba(alAjustes, corto = true)
        Spacer(Modifier.height(12.dp))
        AccionGrande(Icons.Filled.SportsSoccer, "Meter el partido", Modifier.fillMaxWidth()) { alPartido() }
        Spacer(Modifier.height(4.dp))
        PestanasRevista(PESTANAS, pestana, cuentas = listOf(null, null, decisiones.takeIf { it > 0 }, ofertas.takeIf { it > 0 }, null, null, null)) { pestana = it }
        Spacer(Modifier.height(10.dp))
        when (pestana) {
            0 -> TabPortada(u, entradas, alMensajes = { pestana = 2 }, alOfertas = { pestana = 3 }, alAjustes = alAjustes, articuloAbierto = articuloAbierto)
            1 -> TabRed(u, entradas)
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

/** La portada del periódico: al tocarla, se lee la noticia entera. */
@Composable
fun PortadaPrensa(p: Prensa, abierta0: Boolean = false) {
    var abierta by rememberSaveable(p.titular) { mutableStateOf(abierta0) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).let { if (p.articulo.isNotBlank()) it.tocable { abierta = !abierta } else it }) {
        Text(p.cabecera.uppercase(), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 3.sp, lineHeight = 19.sp)
        Filete(2.dp, modifier = Modifier.padding(top = 4.dp))
        FitTitle(p.titular, 34.sp, 37.sp, letterSpacing = (-0.8).sp, modifier = Modifier.padding(top = 8.dp))
        if (p.entradilla.isNotBlank()) Text(p.entradilla, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 6.dp))
        if (p.articulo.isNotBlank()) {
            if (abierta) p.articulo.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotBlank() }.forEach { par ->
                Text(par, color = Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Text(if (abierta) "Cerrar la noticia ↑" else "Leer la noticia →", color = Acento, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
    p.otros.forEachIndexed { i, o ->
        Filete(1.dp, Line, Modifier.padding(top = 8.dp))
        Text(o, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 6.dp))
        p.entradillas.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp)) }
    }
}

@Composable
private fun TabPortada(u: Universo, entradas: List<Entrada>, alMensajes: () -> Unit, alOfertas: () -> Unit, alAjustes: (Int) -> Unit, articuloAbierto: Boolean = false) {
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
    entradas.firstOrNull { it.prensa != null }?.prensa?.let { PortadaPrensa(it, articuloAbierto) }
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
    MagLink(va ?: "¿Qué ha pasado esta semana?", if (va == null) (if (Prueba.activo) "Una cosa más, ahora (modo prueba: gratis)" else "Una cosa más, ahora (Sonnet, unos céntimos)") else null) {
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
        Antetitulo((if (e.tipo == TipoEntrada.RECORD) "★ " else "") + e.tipo.nombre + (if (e.paso > 0) " · jornada ${e.paso}" else ""), tam = 11.sp,
            color = if (e.tipo == TipoEntrada.RECORD) Dorado else Charcoal)
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
            Text(when { d.abierta -> "Tienes que decidir: ${d.texto}"; d.caducada -> "No contestaste: «${d.titulo.ifBlank { d.texto }}»"; else -> "Elegiste: «${d.elegida}»" },
                color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
            if (d.caducada && d.consecuencia.isNotBlank()) Text(d.consecuencia, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
        e.premios.forEach { pr ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(if (pr.ganado) "★ " else "☆ ", color = Dorado, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Column(Modifier.weight(1f)) {
                    Text("${pr.nombre} · ${pr.medio}", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp)
                    Text((if (pr.ganado) "Lo gana. " else "Se queda a las puertas. ") + pr.texto, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
        if (e.tendencia.isNotBlank()) Etiqueta("Tendencia: ${e.tendencia} · ${corto(e.tendenciaPosts)} posts", Modifier.padding(top = 6.dp))
        val ef = textoEfectos(e.efectos)
        if (ef.isNotBlank()) Text(ef.uppercase(), color = Dorado, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 6.dp))
        if (e.posts.isNotEmpty() || e.dms.isNotEmpty()) Etiqueta(listOfNotNull(e.posts.size.takeIf { it > 0 }?.let { "$it en la red" }, e.dms.size.takeIf { it > 0 }?.let { "$it mensaje${if (it > 1) "s" else ""}" }).joinToString(" · "), Modifier.padding(top = 6.dp))
    }
}

/** Un post, como en X: quién, su @, lo que dice, lo que cita, lo que ha gustado y las respuestas de debajo. */
@Composable
fun PostRevista(p: Post) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Cabeza(p)
        Text(p.texto, color = Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 3.dp))
        p.cita?.let { c ->
            Column(Modifier.padding(top = 6.dp).fillMaxWidth().background(Sand).padding(10.dp)) {
                Cabeza(c, tam = 12)
                Text(c.texto, color = Charcoal, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Text("♥ ${corto(p.megusta)}   ⟲ ${corto(p.reposts)}" + (if (p.respuestas.isNotEmpty()) "   💬 ${p.respuestas.size}" else ""), color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        p.respuestas.forEach { r ->
            Row(Modifier.padding(top = 8.dp).fillMaxWidth()) {
                Box(Modifier.padding(end = 10.dp).width(2.dp).heightIn(min = 30.dp).background(Line))
                Column(Modifier.weight(1f)) {
                    Cabeza(r, tam = 12)
                    Text(r.texto, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 1.dp))
                    Text("♥ ${corto(r.megusta)}", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
    Filete(1.dp, Line)
}

@Composable
private fun Cabeza(p: Post, tam: Int = 14) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(p.autor.ifBlank { p.usuario }, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = tam.sp, modifier = Modifier.weight(1f, fill = false))
        if (p.usuario.isNotBlank()) Text("  ${p.usuario}", color = Muted, fontFamily = Body, fontSize = (tam - 2).sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}

/** «1,2 mil», «3,4 M». */
fun corto(n: Int): String = when {
    n >= 1_000_000 -> Formato.decimal(n / 1_000_000.0) + " M"
    n >= 10_000 -> Formato.cifra(Math.round(n / 1000.0)) + " mil"
    else -> Formato.cifra(n)
}

@Composable
private fun TabRed(u: Universo, entradas: List<Entrada>) {
    val con = entradas.filter { it.posts.isNotEmpty() }
    if (con.isEmpty()) { Nota("La red está en silencio. Mete un partido y verás."); return }
    Etiqueta("${Alcance.texto(Alcance.seguidores(u))} seguidores · ${u.nivelActual.nombre.lowercase()}", Modifier.padding(bottom = 4.dp))
    con.forEach { e ->
        Spacer(Modifier.height(10.dp)); Filete(2.dp)
        Antetitulo(when {
            e.partido != null -> "Tras el " + (if (e.partido.local) "${u.club} ${e.partido.golesFavor}-${e.partido.golesContra} ${e.partido.rival}" else "${e.partido.rival} ${e.partido.golesContra}-${e.partido.golesFavor} ${u.club}")
            else -> e.titulo.ifBlank { e.tipo.nombre }
        } + " · jornada ${e.paso}", Modifier.padding(top = 8.dp), tam = 11.sp)
        if (e.tendencia.isNotBlank()) Text("Tendencia: ${e.tendencia} · ${corto(e.tendenciaPosts)} posts", color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
        e.posts.forEach { PostRevista(it) }
    }
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
            val titulo = d.titulo.ifBlank { e.titulo }
            if (titulo.isNotBlank()) Text(titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 2.dp))
            val contexto = d.contexto.ifBlank { e.texto }
            if (contexto.isNotBlank()) Text(contexto, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
            if (d.enJuego.isNotBlank()) Text("Te juegas: ${d.enJuego}", color = Dorado, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
            Text(d.texto, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 6.dp))
            // (las opciones son frases enteras: se leen enteras, en varias líneas si hace falta)
            d.opciones.forEach { o -> Opcion(o, Modifier.fillMaxWidth().padding(top = 8.dp)) { contesta(o) } }
            Campo("O haz otra cosa", libre, "Lo que tú quieras", lineas = 3) { libre = it }
            if (libre.isNotBlank()) EnlaceRevista("Contestar", acento = true) { contesta(libre) }
            va?.let { Nota(it, Modifier.padding(top = 6.dp)) }
            aviso?.let { Nota(it, Modifier.padding(top = 6.dp), color = Acento) }
        }
    }
    // Las conversaciones (la más reciente arriba; dentro, de lo más viejo a lo más nuevo, con un corte por cada vez)
    val chats = LinkedHashMap<String, MutableList<Pair<Entrada, List<Dm>>>>()
    entradas.forEach { e -> e.dms.groupBy { it.conversacion.ifBlank { "Alguien" } }.forEach { (c, ms) -> chats.getOrPut(c) { mutableListOf() }.add(e to ms) } }
    if (chats.isEmpty() && abiertas.isEmpty()) { Nota("Ningún mensaje todavía."); return }
    if (chats.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Antetitulo("Conversaciones") }
    chats.forEach { (chat, tandas) ->
        Panel {
            Text(chat, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 23.sp)
            u.entorno.firstOrNull { Vida.mismo(it.nombre, chat) }?.let { Etiqueta(it.rol + " · afinidad ${it.afinidad}") }
            val grupo = tandas.flatMap { it.second }.filter { !it.mio }.map { it.de }.distinct().size > 1
            tandas.reversed().take(4).forEach { (e, ms) ->
                Etiqueta("Jornada ${e.paso} · " + (e.partido?.let { "tras el ${it.resultado} ante el ${it.rival}" } ?: e.titulo.ifBlank { e.tipo.nombre }), Modifier.padding(top = 8.dp))
                ms.forEach { m ->
                    Column(Modifier.fillMaxWidth().padding(top = 5.dp).let { if (m.mio) it.padding(start = 40.dp) else it.padding(end = 24.dp) }.background(if (m.mio) Sand else Paper).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        if (grupo || m.mio) Text(if (m.mio) "Tú" else m.de, color = if (m.mio) Acento else Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(m.texto, color = Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                }
            }
        }
    }
    val caducadas = entradas.filter { it.decision?.caducada == true }
    if (caducadas.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Antetitulo("No contestaste") }
    caducadas.forEach { e ->
        val d = e.decision!!
        Panel {
            Text(d.titulo.ifBlank { e.titulo }.ifBlank { d.texto }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp)
            Text("Te preguntaban: ${d.texto}", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
            Text(d.consecuencia.ifBlank { "Lo que pase por no contestar lo contará la prensa del próximo partido." }, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
    val resueltas = entradas.filter { it.decision?.let { d -> !d.abierta && !d.caducada } == true }
    if (resueltas.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Antetitulo("Lo que decidiste") }
    resueltas.forEach { e ->
        val d = e.decision!!
        Panel {
            Text(d.titulo.ifBlank { d.texto }, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp)
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
    u.contrato?.let { c -> MagFact("Tu contrato ahora", "${Formato.eurosCorto(c.sueldo)}/semana hasta ${c.hasta}") } ?: Nota("Eres agente libre: cualquier club puede ficharte.", Modifier.padding(bottom = 6.dp), color = Acento)
    todas.filter { it.second.estado != Oferta.NEGOCIADA }.sortedBy { if (it.second.estado == Oferta.ABIERTA) 0 else 1 }.forEach { (e, o) ->
        Panel {
            Antetitulo((Oferta.TIPOS[o.tipo] ?: o.tipo) + if (o.estado != Oferta.ABIERTA) " · ${o.estado}" else "", tam = 11.sp)
            Text(o.de, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, modifier = Modifier.padding(top = 4.dp))
            if (o.texto.isNotBlank()) Text(o.texto, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
            if (o.salario > 0) MagFact(if (o.tipo == "patrocinio") "A la semana, de más" else "Sueldo a la semana", Formato.euros(o.salario) + (if (o.tipo != "patrocinio" && u.salario > 0) " (ahora ${Formato.eurosCorto(u.salario)})" else ""))
            if (o.prima > 0) MagFact("Prima", Formato.euros(o.prima))
            if (o.anios > 0) MagFact("Contrato", "${o.anios} año${if (o.anios > 1) "s" else ""}")
            if (o.estado == Oferta.ABIERTA && o.tipo == "renovacion" && !o.negociada) EnlaceRevista("Pedir más (una vez)", Modifier.padding(top = 8.dp), acento = true) {
                ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.contraoferta(e.id, o.id) } }
            }
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

/** Una opción de una decisión: la frase entera, sin cortar. */
@Composable
fun Opcion(texto: String, modifier: Modifier = Modifier, alHacer: () -> Unit) {
    Box(modifier.heightIn(min = 44.dp).border(1.5.dp, Charcoal).tocable(onClick = alHacer).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
        Text(texto, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp)
    }
}
