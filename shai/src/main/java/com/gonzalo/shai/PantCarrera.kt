package com.gonzalo.shai

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que se está pidiendo a Claude ahora mismo (para no pedirlo dos veces y enseñar que va). */
object EnCurso {
    val ids = androidx.compose.runtime.mutableStateMapOf<String, String>()
}

/** Las secciones de una carrera (los índices de [PantCarrera]): las cuatro primeras van en la barra de abajo; las tres últimas, en «Más». */
val PESTANAS = listOf("Portada", "Red", "Mensajes", "Ofertas", "Cifras", "Vida", "Perfil")

/** Lo que ya has leído de cada conversación (en este móvil): cuántos mensajes suyos has visto. */
object Leidos {
    private const val PREFS = "shai_leidos"
    var version by mutableIntStateOf(0)
        private set
    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun vistos(c: Context, universo: String, chat: String): Int { version; return runCatching { p(c).getInt("$universo|$chat", 0) }.getOrDefault(0) }
    fun marca(c: Context, universo: String, chat: String, n: Int) {
        if (vistos(c, universo, chat) >= n) return
        runCatching { p(c).edit().putInt("$universo|$chat", n).apply() }; version++
    }
}

// ---------------------------------------------------------------- las conversaciones (los mensajes y las decisiones, por persona o grupo)

/** Lo de una vez en una conversación: los mensajes de una entrada y, si es de aquí, su decisión. */
class ItemChat(val entrada: Entrada, val dms: List<Dm>, val decision: Decision?)

class Conversacion(val nombre: String, val items: List<ItemChat>) {
    val ultima: Long get() = items.maxOf { maxOf(it.entrada.creado, it.entrada.visibleDesde) }
    /** Los mensajes que te han mandado (para lo de «sin leer»). */
    val recibidos: Int get() = items.sumOf { i -> i.dms.count { !it.mio } }
    val abierta: Pair<Entrada, Decision>? get() = items.lastOrNull { it.decision?.abierta == true }?.let { it.entrada to it.decision!! }
    /** Quién escribe (sin ti). */
    val personas: List<String> get() = items.flatMap { i -> i.dms.filter { !it.mio }.map { it.de } }.distinct()
    val grupo: Boolean get() = personas.size > 1
}

/** El chat de una decisión: el de quien la manda. */
private fun chatDe(d: Decision): String = d.de.ifBlank { "Tu gente" }

fun conversaciones(entradas: List<Entrada>): List<Conversacion> {
    val mapa = LinkedHashMap<String, MutableList<ItemChat>>()
    entradas.sortedBy { maxOf(it.creado, it.visibleDesde) }.forEach { e ->
        val porChat = e.dms.groupBy { it.conversacion.ifBlank { "Alguien" } }
        val dc = e.decision?.let { chatDe(it) }
        porChat.forEach { (c, ms) -> mapa.getOrPut(c) { mutableListOf() }.add(ItemChat(e, ms, if (c == dc) e.decision else null)) }
        if (dc != null && dc !in porChat) mapa.getOrPut(dc) { mutableListOf() }.add(ItemChat(e, emptyList(), e.decision))
    }
    return mapa.map { Conversacion(it.key, it.value) }.sortedByDescending { it.ultima }
}

/** Quién es alguien de un chat («Representante · afinidad 66», «Grupo · 4 personas», «Rival · Real Madrid»). */
private fun rolDe(u: Universo, c: Conversacion): String {
    u.entorno.firstOrNull { Vida.mismo(it.nombre, c.nombre) }?.let { return it.rol.replaceFirstChar { x -> x.uppercase() } + " · afinidad ${it.afinidad}" }
    u.rivales.firstOrNull { Vida.mismo(it.nombre, c.nombre) }?.let { return "Rival · ${it.club}" }
    if (c.grupo) return "Grupo · ${c.personas.size + 1} personas"
    return ""
}

// ---------------------------------------------------------------- la pantalla de una carrera

/**
 * Una carrera: la cabecera de revista (el club, la temporada y la jornada, el nombre y «Meter el partido») y, abajo, la barra con Inicio, Red,
 * Mensajes, Ofertas y Más (Cifras, Vida y Perfil). Una conversación se abre a pantalla completa, como en el móvil.
 */
@Composable
fun PantCarrera(id: String, alVolver: () -> Unit, alPartido: () -> Unit, alAjustes: (Int) -> Unit, inicial: Int = 0, articuloAbierto: Boolean = false, chatInicial: String? = null) {
    Carreras.version
    val ctx = LocalContext.current
    var pestana by rememberSaveable { mutableIntStateOf(inicial) }
    var chat by rememberSaveable { mutableStateOf(chatInicial) }
    val u = Carreras.de(id) ?: run { PaginaRevista { RevistaTopBar(alVolver, "Shai"); Nota("Esta carrera ya no está.") }; return }
    val entradas = Carreras.entradasDe(id)
    val convs = conversaciones(entradas)
    Leidos.version
    val decisiones = entradas.count { it.decision?.abierta == true }
    val sinLeer = convs.count { it.recibidos > Leidos.vistos(ctx, u.id, it.nombre) }
    val ofertas = entradas.sumOf { e -> e.ofertas.count { it.estado == Oferta.ABIERTA } }
    chat?.let { c ->
        BackHandler { chat = null }
        PantChat(u, convs.firstOrNull { it.nombre == c } ?: Conversacion(c, emptyList())) { chat = null }
        return
    }
    BackHandler(enabled = pestana != 0) { pestana = 0 }
    Box(Modifier.fillMaxSize().background(Paper)) {
        PaginaRevista(pie = 100.dp) {
            RevistaTopBar(alVolver, "Shai") { EnlaceRevista("Ajustes") { alAjustes(0) } }
            Antetitulo(listOfNotNull(u.club, u.temporada.ifBlank { null }, "jornada ${u.paso}").joinToString(" · "), Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
            FitTitle(u.nombre, 48.sp, 52.sp, letterSpacing = (-1.4).sp, modifier = Modifier.padding(top = 2.dp))
            Filete(3.dp)
            if (Prueba.activo) AvisoPrueba(alAjustes, corto = true)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Acento).tocable { alPartido() }.padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SportsSoccer, null, tint = SobreAcento, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text("METER EL PARTIDO", color = SobreAcento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.2.sp)
            }
            Spacer(Modifier.height(14.dp))
            when (pestana) {
                0 -> TabInicio(u, entradas, convs, alPestana = { pestana = it }, alChat = { chat = it }, alAjustes = alAjustes, articuloAbierto = articuloAbierto)
                1 -> TabRed(u, entradas)
                2 -> TabMensajes(u, entradas, convs) { chat = it }
                3 -> TabOfertas(u, entradas)
                else -> {
                    // «Más»: Cifras, Vida y Perfil
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(4 to "Cifras", 5 to "Vida", 6 to "Perfil").forEach { (i, n) -> ChipFiltro(n, pestana == i) { pestana = i } }
                    }
                    Spacer(Modifier.height(14.dp))
                    when (pestana) {
                        4 -> TabCifras(u)
                        5 -> TabVida(u)
                        else -> TabPerfil(u, alBorrada = alVolver)
                    }
                }
            }
        }
        BarraNav(itemsNav(decisiones + sinLeer, ofertas), minOf(pestana, 4), Modifier.align(Alignment.BottomCenter)) { i -> pestana = if (i == 4) (if (pestana >= 4) pestana else 4) else i }
    }
}

/** «+3 fama · −2 disciplina · +25.000 €». */
fun textoEfectos(e: Efectos): String = buildList {
    if (e.dinero != 0L) add((if (e.dinero > 0) "+" else "") + Formato.eurosCorto(e.dinero))
    Fuera.TODOS.forEach { (k, n) -> e.fuera[k]?.takeIf { it != 0 }?.let { add((if (it > 0) "+" else "−") + kotlin.math.abs(it) + " " + n.lowercase()) } }
    e.relaciones.forEach { (n, d) -> if (d != 0) add("$n ${if (d > 0) "+" else "−"}${kotlin.math.abs(d)}") }
    e.nuevas.forEach { add("nueva: ${it.nombre}") }
}.joinToString(" · ")

/** «304 mil €» → «304 mil» (el «€» va en la etiqueta). */
fun sinEuro(t: String) = t.removeSuffix("€").trim()

/** El partido en una línea («Real Betis 3-1 Sevilla FC»). */
fun marcador(u: Universo, p: Partido) = if (p.local) "${u.club} ${p.golesFavor}-${p.golesContra} ${p.rival}" else "${p.rival} ${p.golesContra}-${p.golesFavor} ${u.club}"

// ---------------------------------------------------------------- Inicio

/** La portada del periódico, en su tarjeta: al tocarla, se lee la noticia entera. */
@Composable
fun PortadaPrensa(p: Prensa, abierta0: Boolean = false) {
    var abierta by rememberSaveable(p.titular) { mutableStateOf(abierta0) }
    Tarjeta(alHacer = if (p.articulo.isNotBlank()) ({ abierta = !abierta }) else null) {
        Text(p.titular, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp)
        if (p.entradilla.isNotBlank()) Cursiva(p.entradilla, Modifier.padding(top = 6.dp), color = Muted)
        if (p.articulo.isNotBlank()) {
            if (abierta) p.articulo.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotBlank() }.forEach { par ->
                Text(par, color = Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Text(if (abierta) "Cerrar la noticia ↑" else "Leer la noticia →", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp))
        }
        if (p.otros.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            p.otros.forEachIndexed { i, o ->
                Filete(1.dp, Line, Modifier.padding(top = 8.dp))
                Text(o, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp))
                p.entradillas.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp)) }
            }
        }
    }
}

@Composable
private fun TabInicio(u: Universo, entradas: List<Entrada>, convs: List<Conversacion>, alPestana: (Int) -> Unit, alChat: (String) -> Unit, alAjustes: (Int) -> Unit, articuloAbierto: Boolean) {
    val ambito = rememberCoroutineScope()
    var aviso by remember { mutableStateOf<String?>(null) }
    val decisiones = entradas.count { it.decision?.abierta == true }
    val ofertas = entradas.sumOf { e -> e.ofertas.count { it.estado == Oferta.ABIERTA } }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Contador("$decisiones", if (decisiones == 1) "decisión espera" else "decisiones esperan", Modifier.weight(1f).fillMaxHeight(), destacado = decisiones > 0) { alPestana(2) }
        Contador("$ofertas", if (ofertas == 1) "oferta abierta" else "ofertas abiertas", Modifier.weight(1f).fillMaxHeight(), destacado = ofertas > 0) { alPestana(3) }
        Contador(Alcance.texto(Alcance.seguidores(u)), "seguidores", Modifier.weight(1f).fillMaxHeight()) { alPestana(1) }
    }
    if (!ClaveApi.hay) {
        Spacer(Modifier.height(10.dp))
        Tarjeta(bordeIzq = Coral, alHacer = { alAjustes(2) }) {
            TituloTarjeta("Falta tu clave de la API")
            TextoTarjeta("Sin ella, los partidos se guardan pero nadie escribe lo de fuera del campo.", Modifier.padding(top = 4.dp))
            Text("Ponerla en Ajustes → IA →", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
    // El club de verdad: si espera tu «Sí, es este» (tras un traspaso) o aún no está identificado
    if (ClaveApi.hay && !u.clubIdentificado) { Spacer(Modifier.height(10.dp)); SeccionClub(u, enInicio = true) }
    entradas.filter { it.pendiente.isNotBlank() }.take(3).forEach { e ->
        Spacer(Modifier.height(10.dp))
        val va = EnCurso.ids[e.id]
        Tarjeta(bordeIzq = Coral, alHacer = {
            if (va == null) { EnCurso.ids[e.id] = "Pidiéndolo…"; ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.reintenta(e.id) }; EnCurso.ids.remove(e.id) } }
        }) {
            TituloTarjeta("Falta lo de después")
            TextoTarjeta("Del partido contra ${e.partido?.rival.orEmpty()} (${e.partido?.resultado.orEmpty()}) falta " +
                e.pendiente.split(',').joinToString(" y ") { if (it == "red") "la red" else "la prensa" } + ".", Modifier.padding(top = 4.dp))
            Text(va ?: "Volver a pedirlo →", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
    // Los récords del fútbol de verdad que tiene a tiro: la cuenta atrás
    val ps = Carreras.partidos(u.id).map { it.second }
    val tiro = remember(Carreras.version, u.id) { runCatching { Historicos.aTiro(u, ps) }.getOrDefault(emptyList()) }.take(3)
    if (tiro.isNotEmpty()) {
        Spacer(Modifier.height(18.dp)); Rotulo("Récords a tiro"); Spacer(Modifier.height(8.dp))
        Tarjeta(alHacer = { alPestana(4) }) {
            tiro.forEachIndexed { i, c ->
                if (i > 0) Filete(1.dp, Line, Modifier.padding(vertical = 10.dp))
                val urg = Historicos.urgencia(u, c)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, null, tint = if (urg == Historicos.Urgencia.ALTA) Coral else Acento, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Historicos.queda(c) + (if (c.quedanDias >= 0) " para el récord de " else " le faltan para el récord de ") + c.r.quien.substringBefore(" (").substringBefore(" –"),
                            color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
                        Text(c.r.record + " · " + c.r.marca + (if (c.plazo.isNotBlank()) " · hasta el ${c.plazo}" else ""), color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    if (urg == Historicos.Urgencia.ALTA) { Spacer(Modifier.width(8.dp)); Sello(if (c.quedanDias in 0..7) "Última" else "Ya casi", Coral) }
                }
            }
        }
    }
    entradas.firstOrNull { it.prensa != null }?.prensa?.let {
        Spacer(Modifier.height(18.dp)); Rotulo("La portada de hoy · ${it.cabecera}"); Spacer(Modifier.height(8.dp))
        PortadaPrensa(it, articuloAbierto)
    }
    // Lo de entre partidos que está por llegar
    val ahora = System.currentTimeMillis()
    val porSalir = Carreras.entradasDe(u.id, todas = true).filter { !it.visible(ahora) }
    if (u.lotes.isNotEmpty() || porSalir.isNotEmpty()) Nota(when {
        u.lotes.isNotEmpty() -> "Mientras tanto están pasando cosas: llegan en un rato (por lotes, a mitad de precio)."
        else -> "Algo más pasará hacia las " + java.text.SimpleDateFormat("HH:mm", java.util.Locale("es", "ES")).format(java.util.Date(porSalir.minOf { it.visibleDesde })) + "."
    }, Modifier.padding(top = 12.dp))
    Spacer(Modifier.height(10.dp))
    val va = EnCurso.ids[u.id]
    Tarjeta(alHacer = {
        if (va == null && ClaveApi.hay) { EnCurso.ids[u.id] = "Escribiendo lo que pasa…"; aviso = null
            ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.eventoAhora(u.id).second }; EnCurso.ids.remove(u.id) } }
    }) {
        TituloTarjeta(va ?: "¿Qué ha pasado esta semana? →", tam = 17)
        if (va == null) TextoTarjeta(if (Prueba.activo) "Una cosa más, ahora (modo prueba: gratis)" else "Una cosa más, ahora (Sonnet, unos céntimos)", Modifier.padding(top = 2.dp))
    }
    aviso?.let { Nota(it, Modifier.padding(top = 6.dp), color = Acento) }
    // Esta semana (lo de la última jornada) y lo de antes, más recogido
    if (entradas.isEmpty()) { Nota("Aún nada. Mete tu primer partido.", Modifier.padding(vertical = 14.dp)); return }
    val esta = entradas.filter { it.paso == u.paso }
    val antes = entradas.filter { it.paso != u.paso }
    fun abre(e: Entrada): () -> Unit = {
        when {
            e.decision?.abierta == true -> alChat(chatDe(e.decision))
            e.dms.isNotEmpty() -> alChat(e.dms.first().conversacion)
            e.posts.isNotEmpty() -> alPestana(1)
        }
    }
    if (esta.isNotEmpty()) {
        Spacer(Modifier.height(18.dp)); Rotulo("Esta semana · jornada ${u.paso}")
        esta.forEach { e -> Spacer(Modifier.height(8.dp)); TarjetaEntrada(u, e, expandida = true, alRed = { alPestana(1) }, alChat = alChat, alResponder = abre(e)) }
    }
    if (antes.isNotEmpty()) {
        Spacer(Modifier.height(18.dp)); Rotulo("Antes")
        antes.forEach { e -> Spacer(Modifier.height(8.dp)); TarjetaEntrada(u, e, expandida = false, alRed = { alPestana(1) }, alChat = alChat, alResponder = abre(e)) }
    }
}

/** Una cosa de las que pasan: qué es, cuándo, el título y, abierta, todo lo demás. Se abre y se cierra al tocarla. */
@Composable
fun TarjetaEntrada(u: Universo, e: Entrada, expandida: Boolean = true, alRed: () -> Unit = {}, alChat: (String) -> Unit = {}, alResponder: () -> Unit = {}) {
    var abierta by rememberSaveable(e.id) { mutableStateOf(expandida) }
    val d = e.decision
    val decide = d?.abierta == true
    Tarjeta(bordeIzq = when { decide -> Acento; e.tipo == TipoEntrada.RECORD -> Dorado; else -> null }, alHacer = { abierta = !abierta }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (decide) { Sello("Decide"); Spacer(Modifier.width(8.dp)) }
            if (e.tipo == TipoEntrada.RECORD) { Sello("★ Récord", Dorado); Spacer(Modifier.width(8.dp)) }
            Text(listOfNotNull(e.tipo.nombre.takeIf { e.tipo != TipoEntrada.RECORD }, if (e.paso > 0) "jornada ${e.paso}" else null).joinToString(" · ").uppercase(),
                color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, lineHeight = 15.sp)
        }
        val p = e.partido
        val titulo = if (p != null) marcador(u, p) else e.titulo
        if (titulo.isNotBlank()) Text(titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 6.dp))
        if (p != null) Text(listOfNotNull(p.competicion.ifBlank { null },
            if (u.modo == Rol.JUGADOR) listOfNotNull("${p.minutos}'", p.goles.takeIf { it > 0 }?.let { "$it gol${if (it > 1) "es" else ""}" }, p.asistencias.takeIf { it > 0 }?.let { "$it asist." }, p.nota.takeIf { it > 0 }?.let { "nota ${Formato.decimal(it)}" }).joinToString(" · ")
            else p.goleadores.ifBlank { null }).joinToString(" · "), color = Muted, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        e.prensa?.takeIf { p != null }?.let { Cursiva("«${it.titular}»", Modifier.padding(top = 6.dp)) }
        if (abierta) {
            if (e.texto.isNotBlank()) TextoTarjeta(e.texto, Modifier.padding(top = 6.dp), color = Charcoal)
            d?.let {
                when {
                    it.abierta -> Text("Tienes que decidir: ${it.texto}", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp))
                    it.caducada -> { Text("No contestaste: «${it.titulo.ifBlank { it.texto }}»", color = Coral, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp))
                        if (it.consecuencia.isNotBlank()) TextoTarjeta(it.consecuencia, Modifier.padding(top = 2.dp)) }
                    else -> Text("Elegiste: «${it.elegida}»", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            e.premios.forEach { pr ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(if (pr.ganado) "★ " else "☆ ", color = Dorado, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Column(Modifier.weight(1f)) {
                        Text("${pr.nombre} · ${pr.medio}", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp)
                        TextoTarjeta((if (pr.ganado) "Lo gana. " else "Se queda a las puertas. ") + pr.texto)
                    }
                }
            }
        }
        val ef = textoEfectos(e.efectos)
        if (ef.isNotBlank()) Text(ef, color = Dorado, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
        // A dónde ir desde aquí
        val enlaces = buildList {
            if (decide) add("Responder →" to alResponder)
            if (e.posts.isNotEmpty()) add("${e.posts.size} en la red →" to alRed)
            if (e.dms.isNotEmpty() && !decide) { val c = e.dms.first().conversacion; add("${e.dms.size} mensaje${if (e.dms.size > 1) "s" else ""} →" to { alChat(c) }) }
        }
        if (enlaces.isNotEmpty()) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            enlaces.forEach { (t, f) -> Text(t, color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.tocable(onClick = f)) }
        }
        if (!abierta && (e.texto.isNotBlank() || d != null)) Text("Tocar para leerlo", color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** «1,2 mil», «3,4 M». */
fun corto(n: Int): String = when {
    n >= 1_000_000 -> Formato.decimal(n / 1_000_000.0) + " M"
    n >= 10_000 -> Formato.cifra(Math.round(n / 1000.0)) + " mil"
    else -> Formato.cifra(n)
}

// ---------------------------------------------------------------- Red

private val FILTROS_RED = listOf("Lo más comentado", "Sobre ti", "Prensa", "Tu gente", "Rivales")

private fun pasaFiltro(u: Universo, p: Post, f: Int): Boolean = when (f) {
    1 -> listOfNotNull(u.personaje.apodo.ifBlank { null }, u.personaje.nombre, u.personaje.nombre.substringBefore(' ')).any { n -> n.length > 2 && p.texto.contains(n, ignoreCase = true) }
    2 -> p.esPrensa()
    3 -> p.tipo == "entorno" || u.entorno.any { Vida.mismo(it.nombre, p.autor) }
    4 -> p.tipo == "rival" || u.rivales.any { r -> p.autor.contains(r.nombre, ignoreCase = true) || p.texto.contains(r.nombre, ignoreCase = true) }
    else -> true
}

@Composable
private fun TabRed(u: Universo, entradas: List<Entrada>) {
    val con = entradas.filter { it.posts.isNotEmpty() }
    if (con.isEmpty()) { Nota("La red está en silencio. Mete un partido y verás."); return }
    var filtro by rememberSaveable { mutableIntStateOf(0) }
    Text("${Alcance.texto(Alcance.seguidores(u))} seguidores · ${u.nivelActual.nombre}", color = Muted, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
    val filtros = FILTROS_RED.filterIndexed { i, _ -> i != 4 || u.rivales.isNotEmpty() }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        filtros.forEachIndexed { i, n -> ChipFiltro(n, filtro == i) { filtro = i } }
    }
    // Lo que más se comenta: el post más gordo de lo último, con su hilo abierto
    val ultima = con.first()
    val top = if (filtro == 0) ultima.posts.maxByOrNull { it.megusta + it.respuestas.size * 500 } else null
    if (top != null) {
        Spacer(Modifier.height(12.dp))
        Tarjeta {
            val t = con.firstOrNull { it.tendencia.isNotBlank() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocalFireDepartment, null, tint = Acento, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Rotulo("Lo que más se comenta" + (t?.let { " · ${it.tendencia} · ${corto(it.tendenciaPosts)} posts" } ?: ""), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            PostX(top, hace(maxOf(ultima.creado, ultima.visibleDesde)), destacado = true)
        }
    }
    var alguno = top != null
    con.forEach { e ->
        val ps = e.posts.filter { it !== top && pasaFiltro(u, it, filtro) }
        if (ps.isEmpty()) return@forEach
        alguno = true
        Separador((if (e.partido != null) "Tras el " + marcador(u, e.partido) else e.titulo.ifBlank { e.tipo.nombre }) + " · jornada ${e.paso}")
        ps.forEach { p -> PostX(p, hace(maxOf(e.creado, e.visibleDesde))); Filete(1.dp, Line) }
    }
    if (!alguno) Nota("Nada por aquí con este filtro.", Modifier.padding(top = 12.dp))
}

// ---------------------------------------------------------------- Mensajes

@Composable
private fun TabMensajes(u: Universo, entradas: List<Entrada>, convs: List<Conversacion>, alChat: (String) -> Unit) {
    val ctx = LocalContext.current
    val abiertas = entradas.filter { it.decision?.abierta == true }
    if (convs.isEmpty() && abiertas.isEmpty()) { Nota("Ningún mensaje todavía."); return }
    if (abiertas.isNotEmpty()) {
        Rotulo("Por decidir · ${abiertas.size}")
        abiertas.forEach { e ->
            val d = e.decision!!
            Spacer(Modifier.height(8.dp))
            Tarjeta(bordeIzq = Coral, alHacer = { alChat(chatDe(d)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(chatDe(d), 36.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chatDe(d), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp)
                        val rol = u.entorno.firstOrNull { Vida.mismo(it.nombre, chatDe(d)) }?.rol?.replaceFirstChar { it.uppercase() }
                        Text(listOfNotNull(rol, hace(maxOf(e.creado, e.visibleDesde))).joinToString(" · "), color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
                TituloTarjeta(d.titulo.ifBlank { e.titulo }.ifBlank { d.texto }, Modifier.padding(top = 10.dp))
                if (d.enJuego.isNotBlank()) Text("Te juegas: ${d.enJuego}", color = Dorado, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                Text("Responder →", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
    }
    val lista = convs.filter { c -> c.items.any { it.dms.isNotEmpty() } || c.abierta == null }
    if (lista.isNotEmpty()) Rotulo("Conversaciones")
    lista.forEach { c ->
        val sin = (c.recibidos - Leidos.vistos(ctx, u.id, c.nombre)).coerceAtLeast(0)
        val ultimo = c.items.lastOrNull { it.dms.isNotEmpty() }?.dms?.last()
        Row(Modifier.fillMaxWidth().tocable { alChat(c.nombre) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.nombre, 50.dp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(c.nombre, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                    Text(hace(c.ultima), color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 8.dp))
                }
                rolDe(u, c).takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    val t = ultimo?.let { (if (it.mio) "Tú: " else if (c.grupo) "${it.de.substringBefore(' ')}: " else "") + it.texto } ?: c.abierta?.second?.texto.orEmpty()
                    Text(t, color = if (sin > 0) Charcoal else Muted, fontFamily = Body, fontWeight = if (sin > 0) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (sin > 0) { Spacer(Modifier.width(8.dp)); Insignia(sin) }
                }
                if (c.abierta != null) Sello("Tienes que decidir", Coral, Modifier.padding(top = 5.dp))
            }
        }
        Filete(1.dp, Line)
    }
}

/** Una conversación entera, como en el móvil: burbujas por jornada, lo que decidiste o no contestaste, y la decisión abierta al final. */
@Composable
fun PantChat(u: Universo, c: Conversacion, alVolver: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(c.nombre, c.recibidos) { Leidos.marca(ctx, u.id, c.nombre, c.recibidos) }
    val scroll = rememberScrollState()
    LaunchedEffect(c.nombre) { scroll.scrollTo(scroll.maxValue) }
    val mg = margen()
    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = mg).padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Charcoal, modifier = Modifier.size(26.dp).tocable(onClick = alVolver))
            Spacer(Modifier.width(12.dp))
            Avatar(c.nombre, 42.dp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.nombre, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 21.sp)
                rolDe(u, c).takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp) }
            }
        }
        Filete(1.dp, Line)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = mg)) {
            if (c.items.isEmpty()) Nota("Aún no hay nada en esta conversación.", Modifier.padding(top = 16.dp))
            var corte = ""
            val abierta = c.abierta
            c.items.forEach { it ->
                val e = it.entrada
                val t = "Jornada ${e.paso} · " + (e.partido?.let { p -> "tras el ${p.resultado} ante el ${p.rival}" } ?: e.titulo.ifBlank { e.tipo.nombre })
                if (t != corte) { Separador(t); corte = t }
                it.dms.forEach { m -> Burbuja(m.texto, m.mio, if (c.grupo && !m.mio) m.de else null) }
                val d = it.decision
                if (d != null && !d.abierta) {
                    if (d.caducada) AvisoChat("No contestaste · ${d.titulo.ifBlank { d.texto }}", d.consecuencia.ifBlank { "Lo que pase por no contestar lo contará la prensa del próximo partido." }, Coral)
                    else AvisoChat("Decidiste · ${d.titulo.ifBlank { d.texto }}", "«${d.elegida}»" + if (d.consecuencia.isNotBlank()) "\n\n${d.consecuencia}" else "", Acento)
                }
            }
            abierta?.let { (e, d) -> Spacer(Modifier.height(12.dp)); TarjetaDecision(e, d) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** La decisión abierta, al final del chat: qué ha pasado, qué te juegas, las opciones (enteras) y lo que tú quieras escribir. */
@Composable
private fun TarjetaDecision(e: Entrada, d: Decision) {
    val ambito = rememberCoroutineScope()
    var libre by rememberSaveable(d.id) { mutableStateOf("") }
    var aviso by remember(d.id) { mutableStateOf<String?>(null) }
    val va = EnCurso.ids[d.id]
    fun contesta(t: String) {
        if (va != null || t.isBlank()) return
        EnCurso.ids[d.id] = "Pasando…"; aviso = null
        ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.decide(e.id, t) }; EnCurso.ids.remove(d.id) }
    }
    Tarjeta(borde = Acento) {
        Rotulo("Tienes que decidir" + if (d.enJuego.isNotBlank()) " · te juegas ${d.enJuego}" else "")
        val titulo = d.titulo.ifBlank { e.titulo }
        if (titulo.isNotBlank()) TituloTarjeta(titulo, Modifier.padding(top = 8.dp), tam = 20)
        val contexto = d.contexto.ifBlank { e.texto }
        if (contexto.isNotBlank()) TextoTarjeta(contexto, Modifier.padding(top = 6.dp), color = Charcoal)
        Cursiva(d.texto, Modifier.padding(top = 8.dp), tam = 17)
        d.opciones.forEach { o -> BotonMarco(o, Modifier.padding(top = 8.dp)) { contesta(o) } }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { Campo("O escribe lo que harías tú…", libre, "Lo que tú quieras", lineas = 3) { libre = it } }
            if (libre.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.padding(bottom = 6.dp).size(44.dp).clip(RoundedCornerShape(50)).background(Acento).tocable { contesta(libre) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Contestar", tint = SobreAcento, modifier = Modifier.size(20.dp))
                }
            }
        }
        va?.let { Nota(it, Modifier.padding(top = 6.dp)) }
        aviso?.let { Nota(it, Modifier.padding(top = 6.dp), color = Acento) }
    }
}

// ---------------------------------------------------------------- Ofertas

@Composable
private fun TabOfertas(u: Universo, entradas: List<Entrada>) {
    val ambito = rememberCoroutineScope()
    var aviso by remember { mutableStateOf<String?>(null) }
    u.contrato?.let { c ->
        Tarjeta {
            Rotulo("Tu contrato ahora", color = Muted)
            Text(c.club, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 4.dp))
            Text("${Formato.eurosCorto(c.sueldo)} a la semana · hasta ${c.hasta}" + (if (c.clausula > 0) " · cláusula ${Formato.eurosCorto(c.clausula)}" else ""),
                color = Muted, fontFamily = Body, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
        }
    } ?: Tarjeta(bordeIzq = Acento) { TituloTarjeta("Eres agente libre"); TextoTarjeta("Cualquier club puede ficharte.", Modifier.padding(top = 2.dp)) }
    Nota("La app manda: si aceptas aquí, hazlo también en tu partida de FC.", Modifier.padding(top = 10.dp, bottom = 4.dp))
    val todas = entradas.flatMap { e -> e.ofertas.map { e to it } }.filter { it.second.estado != Oferta.NEGOCIADA }
    if (todas.isEmpty()) { Nota("Ninguna oferta todavía. Ya llegarán.", Modifier.padding(top = 8.dp)); return }
    val abiertas = todas.filter { it.second.estado == Oferta.ABIERTA }
    val viejas = todas.filter { it.second.estado != Oferta.ABIERTA }
    if (abiertas.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Rotulo("Sobre la mesa · ${abiertas.size}") }
    abiertas.forEach { (e, o) ->
        Spacer(Modifier.height(8.dp))
        Tarjeta(bordeIzq = Acento) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(o.de, 46.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Sello(Oferta.TIPOS[o.tipo] ?: o.tipo)
                    Text(o.de, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (o.texto.isNotBlank()) TextoTarjeta(o.texto, Modifier.padding(top = 8.dp), color = Charcoal)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (o.salario > 0) DatoOferta(sinEuro(Formato.eurosCorto(o.salario)), if (o.tipo == "patrocinio") "€ a la semana, de más" else "€ a la semana", Modifier.weight(1f).fillMaxHeight())
                if (o.prima > 0) DatoOferta(sinEuro(Formato.eurosCorto(o.prima)), "€ de prima", Modifier.weight(1f).fillMaxHeight())
                if (o.anios > 0) DatoOferta("${o.anios}", if (o.anios == 1) "año" else "años", Modifier.weight(1f).fillMaxHeight())
            }
            if (o.salario > 0 && o.tipo != "patrocinio" && u.salario > 0) {
                val pct = Math.round((o.salario - u.salario) * 100.0 / u.salario)
                Text("Ahora cobras ${Formato.eurosCorto(u.salario)}: " + (if (pct >= 0) "+$pct %" else "$pct %"), color = if (pct >= 0) Acento else Coral,
                    fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
            }
            val va = EnCurso.ids[o.id]
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonRedondo(va ?: "Aceptar", Modifier.weight(1f)) {
                    if (va != null) return@BotonRedondo
                    EnCurso.ids[o.id] = "Firmando…"
                    ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.oferta(e.id, o.id, true) }; EnCurso.ids.remove(o.id) }
                }
                BotonRedondo("Rechazar", Modifier.weight(1f), lleno = false) { ambito.launch { withContext(Dispatchers.IO) { Motor.oferta(e.id, o.id, false) } } }
            }
            if (o.tipo == "renovacion" && !o.negociada) BotonRedondo("Pedir más (una vez)", Modifier.fillMaxWidth().padding(top = 8.dp), lleno = false) {
                ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.contraoferta(e.id, o.id) } }
            }
        }
    }
    aviso?.let { Nota(it, Modifier.padding(top = 6.dp), color = Acento) }
    if (viejas.isNotEmpty()) { Spacer(Modifier.height(18.dp)); Rotulo("Ya respondidas", color = Muted) }
    viejas.forEach { (_, o) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(o.de, 34.dp); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(o.de, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp)
                Text(listOfNotNull(Oferta.TIPOS[o.tipo] ?: o.tipo, o.salario.takeIf { it > 0 }?.let { "${Formato.eurosCorto(it)}/semana" }).joinToString(" · "), color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Sello(if (o.estado == Oferta.ACEPTADA) "Aceptada" else "Rechazada", if (o.estado == Oferta.ACEPTADA) Acento else Muted)
        }
        Filete(1.dp, Line)
    }
}

@Composable
private fun DatoOferta(valor: String, nombre: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(FondoBurbuja).padding(10.dp)) {
        // (en una línea: si no cabe, la letra baja; nunca «85 mil» arriba y «€» abajo)
        FitTitle(valor, 19.sp, 23.sp, Modifier.fillMaxWidth(), color = Charcoal, unaLinea = true, minSize = 11f, fontWeight = FontWeight.Bold)
        Text(nombre, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/** Una opción de una decisión: la frase entera, sin cortar. */
@Composable
fun Opcion(texto: String, modifier: Modifier = Modifier, alHacer: () -> Unit) = BotonMarco(texto, modifier, alHacer)
