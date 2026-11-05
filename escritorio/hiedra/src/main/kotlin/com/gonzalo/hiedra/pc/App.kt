package com.gonzalo.hiedra.pc

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.AjustesCuenta
import com.gonzalo.familia.pc.AjustesVersion
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Portadas
import com.gonzalo.familia.pc.Sitio
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class Pantalla {
    data object Portada : Pantalla()
    data class Lista(val estado: Estado) : Pantalla()
    data object EsteOrdenador : Pantalla()
    data class Ficha(val id: String, val volver: Pantalla) : Pantalla()
    data object Ajustes : Pantalla()
}

class Nav(val ir: (Pantalla) -> Unit, val aviso: (String) -> Unit)
private val ES = Locale.forLanguageTag("es-ES")
fun horas(ms: Long): String { val m = ms / 60_000L; return if (m < 60) "$m min" else "${m / 60} h" + if (m % 60 > 0) " ${m % 60} min" else "" }
fun fecha(ms: Long): String = if (ms <= 0) "" else java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy", ES))

@Composable
fun AppHiedra(inicial: Pantalla = Pantalla.Portada) {
    LaFamilia.cambios
    var pantalla by remember { mutableStateOf(inicial) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val nav = remember { Nav({ pantalla = it }) { m -> scope.launch { snack.showSnackbar(m, withDismissAction = true) } } }
    val juegos = Juegos.todos()
    val estados = Estado.entries
    val entradas = buildList {
        add(Entrada("Portada", grupo = "Hiedra"))
        estados.forEach { e -> add(Entrada(if (e == Estado.JUGANDO) "Jugando" else e.nombre + "s", juegos.count { it.estado == e }, grupo = "Tus juegos")) }
        add(Entrada("En este ordenador", grupo = "Más")); add(Entrada("Ajustes", grupo = "Más"))
    }
    val sel = when (val p = pantalla) { Pantalla.Portada -> 0; is Pantalla.Lista -> 1 + estados.indexOf(p.estado); Pantalla.EsteOrdenador -> 5; Pantalla.Ajustes -> 6; is Pantalla.Ficha -> (p.volver as? Pantalla.Lista)?.let { 1 + estados.indexOf(it.estado) } ?: 0 }
    Row(Modifier.fillMaxSize()) {
        Lateral("Hiedra", entradas, sel) { i -> pantalla = when (i) { 0 -> Pantalla.Portada; in 1..4 -> Pantalla.Lista(estados[i - 1]); 5 -> Pantalla.EsteOrdenador; else -> Pantalla.Ajustes } }
        Filete(1.dp, Line, Modifier.fillMaxHeight().width(1.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Crossfade(pantalla, label = "pantalla") { p ->
                when (p) {
                    Pantalla.Portada -> PantPortada(nav)
                    is Pantalla.Lista -> PantLista(p.estado, nav)
                    Pantalla.EsteOrdenador -> PantOrdenador(nav)
                    is Pantalla.Ficha -> PantFicha(p.id, p.volver, nav)
                    Pantalla.Ajustes -> PantAjustes(nav)
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

@Composable
fun Tarjeta(j: Juego, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        PortadaCosa(j.portada, j.portadaUrl, j.titulo, Modifier.fillMaxWidth(), pie = j.plataforma.ifBlank { j.menu.nombre })
        Spacer(Modifier.height(8.dp))
        Text(j.titulo, color = Charcoal, fontFamily = Display, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOf(j.plataforma, if (j.ms > 0) horas(j.ms) else "").filter { it.isNotBlank() }.joinToString(" · ").ifBlank { j.estado.nombre }, color = Muted, fontFamily = Body, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun Fila(l: List<Juego>, nav: Nav, volver: Pantalla) {
    l.chunked(6).forEach { f ->
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            f.forEach { j -> Tarjeta(j, Modifier.weight(1f)) { nav.ir(Pantalla.Ficha(j.id, volver)) } }
            repeat(6 - f.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun PantPortada(nav: Nav) {
    LaFamilia.cambios
    val juegos = Juegos.todos()
    val jugando = juegos.filter { it.estado == Estado.JUGANDO }.sortedByDescending { it.ultima }
    val hoy = LocalDate.now().toString()
    val msHoy = juegos.sumOf { it.dias()[hoy] ?: 0L }
    val semana = (0..6).sumOf { d -> val k = LocalDate.now().minusDays(d.toLong()).toString(); juegos.sumOf { it.dias()[k] ?: 0L } }
    val p = Pc.ahora
    Pagina {
        Text("Número de ${LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES))}".uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
        Cabecera(96.sp, texto = "HIEDRA")
        FileteCabecera()
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
            Cifra(horas(msHoy), "jugado hoy", acento = true)
            Cifra(horas(semana), "esta semana")
            Cifra(jugando.size.toString(), "jugando")
            Cifra(juegos.size.toString(), "en tu biblioteca")
        }
        if (p != null) {
            Spacer(Modifier.height(22.dp)); Filete(2.dp)
            Antetitulo("Ahora en este ordenador", Modifier.padding(top = 10.dp))
            Text(p.nombre, color = Charcoal, fontFamily = Display, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text("Desde las " + java.time.Instant.ofEpochMilli(p.desde).atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5), color = Muted, fontFamily = Body, fontSize = 13.sp)
        }
        Spacer(Modifier.height(22.dp)); Filete(2.dp)
        Antetitulo("Jugando", Modifier.padding(vertical = 10.dp))
        if (jugando.isEmpty()) Vacio("Nada entre manos", "Lo que empieces aquí, en Hiedra del móvil o en Steam sale en esta portada.") else Fila(jugando.take(12), nav, Pantalla.Portada)
        val pasados = juegos.filter { it.estado == Estado.PASADO }.sortedByDescending { it.fin }.take(6)
        if (pasados.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Filete(2.dp); Antetitulo("Lo último que has pasado", Modifier.padding(vertical = 10.dp)); Fila(pasados, nav, Pantalla.Portada) }
    }
}

@Composable
fun PantLista(e: Estado, nav: Nav) {
    LaFamilia.cambios
    var menu by remember(e) { mutableStateOf(-1) }
    var buscar by remember(e) { mutableStateOf("") }
    val q = Juegos.norm(buscar)
    val l = Juegos.todos().filter { it.estado == e && (menu < 0 || it.menu == Menu.entries[menu]) && (q.isEmpty() || Juegos.norm(it.titulo).contains(q)) }
    val estado = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.fillMaxSize(), estado, contentPadding = PaddingValues(horizontal = 40.dp, vertical = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Cabecera(76.sp, texto = if (e == Estado.JUGANDO) "JUGANDO" else e.nombre.uppercase() + "S")
                    FileteCabecera()
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(buscar, { buscar = it }, Modifier.width(340.dp), placeholder = { Text("Buscar") }, singleLine = true)
                        Spacer(Modifier.width(24.dp))
                        FiltrosRevista(listOf("Todos") + Menu.entries.map { it.nombre }, menu + 1) { menu = it - 1 }
                    }
                    if (l.isEmpty()) Vacio("Aquí no hay ningún juego")
                }
            }
            items(l, key = { it.id }) { j -> Tarjeta(j) { nav.ir(Pantalla.Ficha(j.id, Pantalla.Lista(e))) } }
        }
        VerticalScrollbar(rememberScrollbarAdapter(estado), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** Lo que hay para jugar en este ordenador: Steam y tus programas. */
@Composable
fun PantOrdenador(nav: Nav) {
    LaFamilia.cambios
    val scope = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<JuegoPc>?>(null) }
    var refresca by remember { mutableStateOf(0) }
    LaunchedEffect(refresca) { lista = withContext(Dispatchers.IO) { Pc.todos() } }
    var nuevo by remember { mutableStateOf(false) }
    Pagina {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { Cabecera(72.sp, texto = "EN ESTE ORDENADOR") }
            BotonAcento("+ Programa", alto = 44.dp, modifier = Modifier.width(160.dp)) { nuevo = true }
        }
        FileteCabecera()
        Spacer(Modifier.height(8.dp))
        Text("Los juegos de Steam instalados aquí y tus programas. Los de Steam se abren con Steam (sus horas las cuenta Steam); tus programas, aquí, y su tiempo va a la cuenta. Tu Hiedra del móvil los ve en «Tu PC» y puede abrirlos desde allí.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 24.sp)
        Spacer(Modifier.height(10.dp))
        val l = lista
        if (l == null) Vacio("Mirando…") else if (l.isEmpty()) Vacio("No he encontrado nada", "No veo Steam en este ordenador. Añade tus juegos con «+ Programa».")
        else l.chunked(6).forEach { f ->
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                f.forEach { j ->
                    val enHiedra = Juegos.porClave("pc:${j.clave}")
                    Column(Modifier.weight(1f)) {
                        PortadaCosa(enHiedra?.portada.orEmpty(), enHiedra?.portadaUrl?.ifBlank { j.portada } ?: j.portada, j.nombre, Modifier.fillMaxWidth(), pie = if (j.steamId != null) "Steam" else "Programa")
                        Spacer(Modifier.height(8.dp))
                        Text(j.nombre, color = Charcoal, fontFamily = Display, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EnlaceRevista("Jugar", acento = true) { scope.launch { val m = withContext(Dispatchers.IO) { val id = Juegos.mete(j.clave, j.nombre, j.portada, jugando = true); Pc.juega(j, id) }; nav.aviso(m) } }
                            if (enHiedra != null) EnlaceRevista("Ficha") { nav.ir(Pantalla.Ficha(enHiedra.id, Pantalla.EsteOrdenador)) }
                            else EnlaceRevista("A Hiedra") { scope.launch { val id = withContext(Dispatchers.IO) { Juegos.mete(j.clave, j.nombre, j.portada).also { id -> portadaDe(j, id) } }; nav.aviso("En tu biblioteca"); nav.ir(Pantalla.Ficha(id, Pantalla.EsteOrdenador)) } }
                        }
                    }
                }
                repeat(6 - f.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (nuevo) DialogoPrograma(nav, { refresca++ }) { nuevo = false }
}

/** La portada de un juego de Steam, a la cuenta (la misma imagen en todas las apps). */
fun portadaDe(j: JuegoPc, id: String) {
    if (j.portada.isBlank() || Juegos.por(id)?.portada?.isNotBlank() == true) return
    runCatching {
        val b = java.net.URI(j.portada).toURL().openStream().use { it.readBytes() }
        Portadas.guardar(b)?.let { h -> Juegos.cambia(id, mapOf("portada" to h, "pu" to j.portada)) }
    }
}

@Composable
private fun DialogoPrograma(nav: Nav, alAnadir: () -> Unit, onCerrar: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var ruta by remember { mutableStateOf<java.io.File?>(null) }
    Dialogo("Añadir un programa", onCerrar, botones = {
        BotonSuave("Cancelar") { onCerrar() }
        BotonAcento("Añadir", modifier = Modifier.width(120.dp), alto = 44.dp) {
            val r = ruta ?: return@BotonAcento
            val j = Pc.anadePrograma(nombre.ifBlank { r.nameWithoutExtension }, r)
            Juegos.mete(j.clave, j.nombre)
            onCerrar(); alAnadir(); nav.aviso("Añadido: ${j.nombre}")
        }
    }) {
        Campo("Nombre del juego", nombre) { nombre = it }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ruta?.path ?: "Ningún archivo elegido", Modifier.weight(1f), color = Muted, fontFamily = Body, fontSize = 13.sp, maxLines = 2)
            BotonSuave("Elegir…") { com.gonzalo.familia.pc.revista.eligeArchivo("Elige el juego (el ejecutable, el .AppImage, la app…)")?.let { ruta = it; if (nombre.isBlank()) nombre = it.nameWithoutExtension.removeSuffix(".app") } }
        }
        Cuerpo("Al jugar desde aquí, Hiedra cuenta el tiempo hasta que lo cierras.", tam = 12)
    }
}

@Composable
fun Campo(etiqueta: String, v: String, onCambia: (String) -> Unit) = OutlinedTextField(v, onCambia, Modifier.fillMaxWidth(), label = { Text(etiqueta) }, singleLine = true)

/** La ficha de un juego: su portada, cómo va, sus horas (por fuente y por día) y jugar aquí. */
@Composable
fun PantFicha(id: String, volver: Pantalla, nav: Nav) {
    LaFamilia.cambios
    val j = Juegos.por(id) ?: run { Pagina { VolverRevista({ nav.ir(volver) }); Vacio("Este juego ya no está") }; return }
    val scope = rememberCoroutineScope()
    val dev = Juegos.dispositivo()
    val aqui = j.lanzadorAqui(dev)?.let { Pc.por(it.pcKey) }
    Pagina {
        VolverRevista({ nav.ir(volver) })
        Spacer(Modifier.height(18.dp))
        Row {
            Column(Modifier.width(280.dp)) {
                PortadaCosa(j.portada, j.portadaUrl, j.titulo, Modifier.fillMaxWidth(), pie = j.plataforma)
                Spacer(Modifier.height(14.dp))
                if (aqui != null) BotonAcento("Jugar", modifier = Modifier.fillMaxWidth()) { scope.launch { nav.aviso(withContext(Dispatchers.IO) { Pc.juega(aqui, j.id) }) } }
                else Cuerpo(if (j.lanzadores.isEmpty()) "Sin lanzador: es una ficha (se juega fuera de Hiedra)." else "Se juega en " + j.lanzadores.joinToString(", ") { when (it.tipo) { "ROM" -> "emulación (${it.sistema})"; "APP" -> "Android"; "WEB" -> "la web"; else -> "otro PC" } } + ".", tam = 13)
            }
            Spacer(Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                RevistaHeadline(j.titulo, listOf(j.plataforma, j.menu.nombre).filter { it.isNotBlank() }.distinct().joinToString(" · "))
                Spacer(Modifier.height(16.dp))
                MagChoice(Estado.entries.map { it.nombre }, Estado.entries.indexOf(j.estado), "Cómo va") { Juegos.ponEstado(j, Estado.entries[it]) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Favorito", Modifier.weight(1f), color = Charcoal, fontFamily = Display, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Interruptor(j.fav) { Juegos.cambia(id, mapOf("fav" to it)) }
                }
                Spacer(Modifier.height(18.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
                Antetitulo("Tus horas")
                Spacer(Modifier.height(6.dp))
                MagFact("En total", horas(j.ms))
                if (j.veces > 0) MagFact("Veces", j.veces.toString())
                j.horas.sortedByDescending { it.entero("total") }.forEach { h ->
                    val f = h.texto("fuente")
                    val nombre = when { f == "steam" -> "Steam"; f.startsWith("pc:") -> "En el PC"; f == dev -> "En este ordenador"; f.startsWith("carmesi:") -> "Carmesí"; f.startsWith("hiedra-linux-") || f.startsWith("hiedra-mac-") -> "En otro ordenador"; else -> "En un móvil" }
                    MagFact(nombre, horas(h.entero("total")))
                }
                val dias = j.dias().entries.sortedByDescending { it.key }.take(7)
                if (dias.isNotEmpty()) { Spacer(Modifier.height(8.dp)); dias.forEach { (d, ms) -> MagFact(runCatching { LocalDate.parse(d).format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)).replaceFirstChar { it.uppercase() } }.getOrDefault(d), horas(ms)) } }
                if (j.anadido > 0) MagFact("Añadido", fecha(j.anadido))
                if (j.fin > 0) MagFact("Pasado", fecha(j.fin))
                Spacer(Modifier.height(18.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
                Antetitulo("En Azabache")
                Text(j.nota?.toString() ?: "Sin valorar", color = if (j.nota != null) Acento else Muted, fontFamily = Display, fontSize = if (j.nota != null) 44.sp else 26.sp, fontStyle = if (j.nota == null) FontStyle.Italic else FontStyle.Normal)
            }
        }
    }
}

@Composable
fun PantAjustes(nav: Nav) {
    var pestana by remember { mutableStateOf(0) }
    Pagina(ancho = 900.dp) {
        Cabecera(84.sp, texto = "AJUSTES")
        FileteCabecera()
        Spacer(Modifier.height(14.dp))
        PestanasRevista(listOf("Cuenta", "Este PC", "Apariencia", "Versión"), pestana) { pestana = it }
        Spacer(Modifier.height(22.dp))
        when (pestana) {
            0 -> AjustesCuenta("hiedra")
            1 -> {
                Antetitulo("Este ordenador para tu Hiedra del móvil")
                Spacer(Modifier.height(6.dp))
                Cuerpo("Mientras Hiedra está abierta aquí, tu Hiedra del móvil ve este ordenador en «Tu PC»: su biblioteca (Steam y tus programas), a qué juegas, y puede abrir un juego desde el móvil.", tam = 14)
                MagFact("Nombre", Sitio.nombreEquipo)
                MagFact("Dispositivo", Juegos.dispositivo())
                MagFact("Steam", Pc.carpetasSteam().joinToString { it.path }.ifBlank { "no lo veo en este ordenador" })
                Spacer(Modifier.height(8.dp))
                BotonSuave("Volver a publicar la biblioteca") { Thread { TuPc.publicaBiblioteca() }.start(); nav.aviso("Publicada") }
            }
            2 -> {
                Antetitulo("Apariencia")
                Spacer(Modifier.height(6.dp))
                Cuerpo("Hiedra del ordenador tiene la apariencia de tu Hiedra del móvil (viaja con la cuenta). Aquí puedes elegir claro u oscuro.", tam = 14)
                Spacer(Modifier.height(10.dp))
                MagChoice(listOf("Como el sistema", "Claro", "Oscuro"), Modo.eleccion) { Modo.eleccion = it; Apariencia.guarda() }
            }
            else -> AjustesVersion("hiedra", "Hiedra")
        }
    }
}
