package com.gonzalo.azabache.pc

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.Portadas
import com.gonzalo.familia.pc.revista.*
import com.gonzalo.familia.pc.rememberImagenWeb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Una portada de internet pequeña (o el hueco, si no tiene). */
@Composable
fun MiniWeb(url: String, titulo: String, modifier: Modifier = Modifier) = PortadaCosa("", url, titulo, modifier)

/**
 * Añadir: se busca en internet (donde busca el Azabache del móvil) o se pone a mano. [estadoInicial]: la pestaña desde la que
 * se añade (0 terminado, 1 en curso, 2 pendiente, 3 abandonado).
 */
@Composable
fun DialogoAnadir(k: Kind, estadoInicial: Int, nav: Nav, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var hallazgos by remember { mutableStateOf<List<Hallazgo>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var aMano by remember { mutableStateOf(!(k.id == GAMES.id || k.id == BOOKS.id || k.isMovie || k.isTv)) }
    var nombre by remember { mutableStateOf("") }
    var creador by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf(estadoInicial) }
    fun busca() {
        if (q.isBlank() || buscando) return
        buscando = true; error = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Buscar.en(k, q) } }
            buscando = false
            r.onSuccess { hallazgos = it; if (it.isEmpty()) error = "No sale nada con «$q»." }.onFailure { error = it.message ?: "No se ha podido buscar." }
        }
    }
    fun crea(id: Long, nombreF: String, creadorF: String, portada: String, paginas: Int, plataforma: String = "") {
        val existe = Cuenta.fichas(k.id).firstOrNull { normName(it.nombre) == normName(nombreF) && (creadorF.isBlank() || it.creador.isBlank() || normName(it.creador) == normName(creadorF)) }
        if (existe != null) { onCerrar(); nav.aviso("Ya lo tienes"); nav.ir(Pantalla.Detalle(k.id, existe.id, Pantalla.Lista(k.id))); return }
        val hoy = LocalDate.now().toEpochDay()
        val valorada = estado == 0
        val o = linkedMapOf<String, Any?>(
            "id" to id, "name" to nombreF.trim(), "cover" to (if (k.custom) portada else ""), "hero" to "", "creator" to creadorF.trim(), "platform" to plataforma,
            "pages" to paginas.toLong(), "start" to (if (estado == 1 || estado == 0) hoy else 0L), "end" to (if (valorada) hoy else 0L), "updated" to System.currentTimeMillis())
        if (valorada) NOTAS.forEach { o[it] = 0L } else { o["status"] = when (estado) { 1 -> 1L; 3 -> 2L; else -> 0L }; o["entries"] = emptyList<Any?>() }
        scope.launch {
            withContext(Dispatchers.IO) {
                Cuenta.guarda(k.id, null, o, valorada)
                // La portada de internet, a la cuenta (la ven también Hiedra y Marfil)
                if (!k.custom && portada.startsWith("http")) runCatching { Cuenta.ficha(k.id, id)?.let { f -> Cuenta.ponPortada(f, Buscar.bytes(portada), portada) } }
            }
            onCerrar(); nav.aviso("Añadido: ${nombreF.trim()}")
            nav.ir(Pantalla.Detalle(k.id, id, Pantalla.Lista(k.id)))
        }
    }
    Dialogo("Añadir a ${k.title.lowercase()}", onCerrar, ancho = 720.dp, botones = {
        if (!aMano) EnlaceRevista("Ponerlo a mano") { aMano = true; nombre = q }
        else if (k.id == GAMES.id || k.id == BOOKS.id || k.isMovie || k.isTv) EnlaceRevista("Buscar en internet") { aMano = false }
        Spacer(Modifier.weight(1f))
        BotonSuave("Cancelar") { onCerrar() }
        if (aMano) BotonAcento("Añadir", modifier = Modifier.width(140.dp), alto = 44.dp) { if (nombre.isNotBlank()) crea(if (k.custom) customItemId(nombre) else System.currentTimeMillis(), nombre, creador, "", 0) }
    }) {
        MagChoice(listOf(k.doneWord.removeSuffix("s"), k.statuses[1], k.statuses[0], k.statuses[2]), estado, "Cómo está") { estado = it }
        Spacer(Modifier.height(12.dp))
        if (aMano) {
            Campo("Nombre", nombre) { nombre = it }
            if (k.id != GAMES.id) Campo(k.creatorWord, creador) { creador = it }
        } else {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Busca por nombre") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { busca() }),
                trailingIcon = { EnlaceRevista(if (buscando) "Buscando…" else "Buscar", acento = true) { busca() } })
            if (error.isNotBlank()) Text(error, color = Acento, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(hallazgos) { h ->
                    Row(Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            // Los juegos de SteamGridDB traen la portada aparte: la primera de las cajas
                            val portada = if (k.id == GAMES.id) withContext(Dispatchers.IO) { runCatching { Buscar.portadasJuego(h.id).firstOrNull()?.second }.getOrNull().orEmpty() } else h.portada
                            crea(h.id, h.nombre, h.creador, portada, h.paginas)
                        }
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniWeb(h.portada, h.nombre, Modifier.width(46.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.nombre, color = Charcoal, fontFamily = Display, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (h.sub.isNotBlank()) Text(h.sub, color = Muted, fontFamily = Body, fontSize = 12.sp, maxLines = 1)
                        }
                        Text("Añadir →", color = Acento, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Filete(1.dp, Line)
                }
            }
        }
    }
}

/** Cambiar la portada: de un archivo de este ordenador, de una dirección o de lo que salga en internet. */
@Composable
fun DialogoPortada(k: Kind, f: Ficha, nav: Nav, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var opciones by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var trabajando by remember { mutableStateOf("") }
    fun pon(bytes: () -> ByteArray, de: String) {
        trabajando = "Poniendo la portada…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching {
                if (k.custom) { if (!de.startsWith("http")) error("En tus categorías la portada tiene que ser de internet (la ve también el móvil)."); Cuenta.ponPortadaWeb(f, de); "ok" }
                else Cuenta.ponPortada(f, bytes(), de) ?: error("Eso no es una imagen.")
            } }
            trabajando = ""
            r.onSuccess { onCerrar(); nav.aviso("Portada puesta") }.onFailure { nav.aviso(it.message ?: "No se ha podido poner.") }
        }
    }
    LaunchedEffect(Unit) {
        trabajando = "Buscando portadas…"
        opciones = withContext(Dispatchers.IO) { runCatching {
            when {
                k.id == GAMES.id -> Buscar.juegos(f.nombre).firstOrNull()?.let { Buscar.portadasJuego(it.id) }.orEmpty()
                else -> Buscar.en(k, listOf(f.nombre, f.creador).filter { it.isNotBlank() }.joinToString(" ")).mapNotNull { h -> h.portada.takeIf { it.isNotBlank() }?.let { it to it } }
            }
        }.getOrDefault(emptyList()).take(18) }
        trabajando = ""
    }
    Dialogo("La portada de «${f.nombre}»", onCerrar, ancho = 780.dp, botones = {
        if (!k.custom) BotonSuave("Elegir un archivo…") {
            val a = eligeArchivo("Elige una imagen", listOf("jpg", "jpeg", "png", "webp", "gif"))
            if (a != null) pon({ a.readBytes() }, "")
        }
        Spacer(Modifier.weight(1f))
        BotonSuave("Cerrar") { onCerrar() }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(url, { url = it }, Modifier.weight(1f), placeholder = { Text("O pega la dirección de una imagen (https://…)") }, singleLine = true)
            Spacer(Modifier.width(12.dp))
            BotonSuave("Poner") { if (url.startsWith("http")) pon({ Buscar.bytes(url) }, url) }
        }
        if (trabajando.isNotBlank()) Text(trabajando, color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.heightIn(max = 460.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(opciones) { (mini, grande) ->
                Box(Modifier.clickable { pon({ Buscar.bytes(grande) }, grande) }) { MiniWeb(mini, f.nombre, Modifier.fillMaxWidth()) }
            }
        }
    }
}
