package com.gonzalo.cobre

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import com.gonzalo.familia.filaDe
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.gonzalo.iptv.futbol.Alineacion
import com.gonzalo.iptv.futbol.Clasificacion
import com.gonzalo.iptv.futbol.Competiciones
import com.gonzalo.iptv.futbol.Equipo
import com.gonzalo.iptv.futbol.Estado
import com.gonzalo.iptv.futbol.Evento
import com.gonzalo.iptv.futbol.FichaPartido
import com.gonzalo.iptv.futbol.GrupoCanales
import com.gonzalo.iptv.futbol.Hora
import com.gonzalo.iptv.futbol.Jugador
import com.gonzalo.iptv.futbol.Lideres
import com.gonzalo.iptv.futbol.Orden
import com.gonzalo.iptv.futbol.Partido
import com.gonzalo.iptv.futbol.TipoEvento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * El fútbol en pantalla: «Tus partidos» (arriba de la portada), la página «Fútbol» (tus partidos, clasificaciones y goleadores) y la
 * ficha de un partido (marcador, lo que pasa, alineaciones y cifras; en juego, se pone al día sola). En cada partido, un botón por cada
 * grupo de canales de su competición: un toque y lo estás viendo.
 */

// ---------------------------------------------------------------- los datos, en pantalla

/**
 * Los partidos de tus equipos: primero lo guardado (al momento) y luego lo que haya que pedir; mientras haya uno que pueda estar en
 * juego, su marcador cada minuto (solo mientras se ve esta pantalla).
 */
@Composable
fun rememberPartidos(): List<Partido>? {
    val ctx = LocalContext.current
    val v = Futbol.version
    EspejoCuenta.version
    val eqs = (AjustesFutbol.equipos(ctx) + AjustesFutbol.rivales(ctx)).map { it.first.id }
    val r by produceState<List<Partido>?>(null, eqs, v) {
        value = withContext(Dispatchers.IO) { Futbol.partidos(ctx, fresco = false) }
        val nuevos = withContext(Dispatchers.IO) { Futbol.partidos(ctx, fresco = true) }
        if (nuevos != value) { value = nuevos; Widgets.pedir(ctx) }
        while (value.orEmpty().any { Orden.quizaEnDirecto(it, System.currentTimeMillis()) }) {
            delay(60_000L)
            val x = withContext(Dispatchers.IO) { Futbol.partidos(ctx, fresco = true, vivoCada = 50_000L) }
            if (x != value) { value = x; Widgets.pedir(ctx) }
        }
    }
    return r
}

/** Algo que se lee una vez (de lo guardado) y luego se pone al día ([leer] con `fresco`). */
@Composable
fun <T> rememberDato(vararg claves: Any?, leer: (Boolean) -> T?): T? {
    val r by produceState<T?>(null, *claves, Futbol.version) {
        value = withContext(Dispatchers.IO) { leer(false) }
        withContext(Dispatchers.IO) { leer(true) }?.let { value = it }
    }
    return r
}

// ---------------------------------------------------------------- piezas

/**
 * El escudo de un equipo de ESPN (sin escudo, o mientras llega, una forma de escudo con sus colores; nunca unas siglas en un círculo).
 * [oscuro]: sobre fondo oscuro, la variante «dark» de ESPN (si no la hay, la normal).
 */
@Composable
fun Escudo(e: Equipo, tam: Dp, modifier: Modifier = Modifier, oscuro: Boolean = false) {
    Box(modifier.size(tam), contentAlignment = Alignment.Center) {
        if (e.escudo.isBlank()) EscudoGenerico(e, tam)
        else {
            val oscura = if (oscuro) ImagenesEquipo.escudoOscuroEspn(e.escudo) else null
            SubcomposeAsyncImage(model = oscura ?: e.escudo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                loading = { EscudoGenerico(e, tam) },
                error = { if (oscura != null) SubcomposeAsyncImage(model = e.escudo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), error = { EscudoGenerico(e, tam) }) else EscudoGenerico(e, tam) })
        }
    }
}

/** «LALIGA · MAÑANA · 21:00», o en juego «EN DIRECTO · 67'» (en acento). */
@Composable
private fun LineaPartido(p: Partido, ahora: Long, modifier: Modifier = Modifier) {
    val vivo = p.estado.enDirecto
    val t = listOf(p.competicion.corto, if (vivo) "En directo · " + p.cuando(ahora) else p.cuando(ahora)).joinToString(" · ")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (vivo) { Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Acento)); Spacer(Modifier.width(6.dp)) }
        Text(t.uppercase(), color = if (vivo) Acento else Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** La marca de lo que es de un rival: «RIVAL» o «CONTRA TU RIVAL», en un recuadro de tinta (sin el acento de lo tuyo). */
@Composable
fun MarcaRival(texto: String, modifier: Modifier = Modifier) =
    Text(texto.uppercase(), color = Charcoal, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1, softWrap = false,
        modifier = modifier.border(1.dp, Charcoal, esquina(2.dp)).padding(horizontal = 5.dp, vertical = 1.dp))

/** Lo que dice un partido de tus rivales: «Contra tu rival» (uno tuyo contra un rival), «Rival» (un rival sin los tuyos) o nada. */
@Composable
fun marcaDe(p: Partido): String? {
    val ctx = LocalContext.current
    val mios = AjustesFutbol.equipos(ctx).map { it.first.id }; val riv = AjustesFutbol.rivales(ctx).map { it.first.id }
    return when {
        Orden.contraRival(p, mios, riv) -> "Contra tu rival"
        riv.any { p.juega(it) } && mios.none { p.juega(it) } -> "Rival"
        else -> null
    }
}

/** Un equipo y sus goles, en una línea (el nombre, en hasta dos líneas: con la letra grande no se corta). Un rival, en cursiva. */
@Composable
fun LineaEquipo(e: Equipo, goles: Int?, tam: Dp, letra: Int, ganador: Boolean, mio: Boolean, rival: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.5.dp), verticalAlignment = Alignment.CenterVertically) {
        EscudoEquipo(e, tam)
        Spacer(Modifier.width(9.dp))
        Text(e.nombre, color = Charcoal, fontFamily = Grotesca, fontStyle = if (rival) FontStyle.Italic else null, fontWeight = if (ganador) FontWeight.Black else if (mio) FontWeight.ExtraBold else FontWeight.Bold,
            fontSize = letra.sp, lineHeight = (letra * 1.18f).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (goles != null) Text("$goles", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Black, fontSize = (letra + 1).sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 10.dp))
    }
}

/**
 * Un partido en grande (los de «Tus partidos»): la competición y cuándo (o en directo, con el minuto), los dos equipos con sus goles y
 * los botones de sus canales. Un toque en el partido abre su ficha.
 */
@Composable
fun TarjetaPartido(p: Partido, ahora: Long, alAbrir: () -> Unit, alConfigurar: () -> Unit) {
    val ctx = LocalContext.current
    val mios = AjustesFutbol.equipos(ctx).map { it.first.id }.toSet()
    val riv = AjustesFutbol.rivales(ctx).map { it.first.id }.toSet()
    val marca = marcaDe(p)
    val vivo = p.estado.enDirecto
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.fillMaxWidth().filaDe("p:" + p.id).tocable(onClick = alAbrir).semantics { contentDescription = "${p.local.nombre} contra ${p.visitante.nombre}" + (marca?.let { ", ${it.lowercase()}" } ?: "") }.padding(vertical = 4.dp)) {
            if (marca != null) MarcaRival(marca, Modifier.padding(bottom = 6.dp))
            LineaPartido(p, ahora, Modifier.padding(bottom = 6.dp))
            val gl = p.golesLocal; val gv = p.golesVisitante
            LineaEquipo(p.local, gl, if (vivo) 30.dp else 26.dp, if (vivo) 19 else 17, gl != null && gv != null && gl > gv && p.estado.terminado, p.local.id in mios, p.local.id in riv)
            LineaEquipo(p.visitante, gv, if (vivo) 30.dp else 26.dp, if (vivo) 19 else 17, gl != null && gv != null && gv > gl && p.estado.terminado, p.visitante.id in mios, p.visitante.id in riv)
            if (p.penaltis.isNotBlank()) Etiqueta("Penaltis: ${p.penaltis}", Modifier.padding(top = 2.dp))
        }
        if (!p.estado.terminado && p.estado != Estado.CANCELADO) BotonesCanal(p, alConfigurar, Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(12.dp))
        Filete(1.dp, Line)
    }
}

/** Un partido en una línea (las listas de «Fútbol»): a la izquierda, el día y la hora (o «Final»); los dos equipos con sus goles. */
@Composable
fun FilaPartido(p: Partido, ahora: Long, mios: Set<String>, rivales: Set<String> = emptySet(), alAbrir: () -> Unit) {
    Row(Modifier.fillMaxWidth().filaDe("p:" + p.id).tocable(onClick = alAbrir).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.widthIn(min = 78.dp).padding(end = 10.dp)) {
            val (a, b) = when {
                p.estado.enDirecto -> "En directo" to p.cuando(ahora)
                p.estado == Estado.POR_JUGAR -> Hora.diaRelativo(p.inicio, ahora) to Hora.reloj(p.inicio)
                else -> Hora.dia(p.inicio) to p.estado.texto
            }
            Text(a.uppercase(), color = if (p.estado.enDirecto) VIVO else Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, maxLines = 1, softWrap = false)
            Text(b, color = if (p.estado.enDirecto) VIVO else Charcoal, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Text(p.competicion.corto.uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, softWrap = false)
            marcaDe(p)?.let { MarcaRival(if (it == "Rival") it else "Tu rival", Modifier.padding(top = 3.dp)) }
        }
        Column(Modifier.weight(1f)) {
            val gl = p.golesLocal; val gv = p.golesVisitante
            LineaEquipo(p.local, gl, 22.dp, 15, gl != null && gv != null && gl > gv, p.local.id in mios, p.local.id in rivales)
            LineaEquipo(p.visitante, gv, 22.dp, 15, gl != null && gv != null && gv > gl, p.visitante.id in mios, p.visitante.id in rivales)
        }
    }
    Filete(1.dp, Line)
}

/** Un botón de canal: relleno de acento (el primero) o con el contorno de tinta; en versalitas y en una línea. */
@Composable
private fun BotonCanal(texto: String, lleno: Boolean, alHacer: () -> Unit) {
    val col = if (lleno) Paper else Charcoal
    Row(Modifier.heightIn(min = 32.dp).semantics { role = Role.Button }.tocable(onClick = alHacer).let { if (lleno) it.background(Charcoal) else it.border(1.5.dp, Charcoal) }
        .padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.PlayArrow, null, tint = col, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(texto.uppercase(), color = col, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.5.sp, letterSpacing = 1.sp, maxLines = 1, softWrap = false)
    }
}

/**
 * Los botones de los canales de un partido: uno por cada grupo de su competición (LaLiga: «Movistar» y «DAZN»). Un toque pone el
 * primer canal del grupo; al lado, «2», «3»… ponen los siguientes (y en el reproductor, arriba y abajo pasan por ellos). Si un grupo no
 * tiene canales, el botón lleva a elegirlos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BotonesCanal(p: Partido, alConfigurar: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    EspejoCuenta.version; Futbol.version
    val grupos = AjustesFutbol.gruposDe(ctx, p.liga)
    val ambito = rememberCoroutineScope()
    if (grupos.isEmpty()) { EnlaceRevista("Elegir sus canales →", modifier, acento = true, onClick = alConfigurar); return }
    fun pon(g: GrupoCanales, i: Int) = ambito.launch {
        val aviso = withContext(Dispatchers.IO) { Futbol.pon(ctx, g, i) }
        if (aviso != null) Toast.makeText(ctx, aviso, Toast.LENGTH_LONG).show()
    }
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        grupos.forEachIndexed { gi, g ->
            if (g.canales.isEmpty()) EnlaceRevista("${g.boton}: elegir canales →", onClick = alConfigurar)
            else Row(verticalAlignment = Alignment.CenterVertically) {
                BotonCanal(g.boton, gi == 0) { pon(g, 0) }
                (2..minOf(g.canales.size, 4)).forEach { n ->
                    Text("$n", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, softWrap = false,
                        modifier = Modifier.semantics { contentDescription = "${g.boton}, canal $n" }.tocable { pon(g, n - 1) }.padding(horizontal = 9.dp, vertical = 10.dp))
                }
            }
        }
    }
}

/** El pie de lo que sale de la fuente: «Datos: ESPN». */
@Composable
fun PieFuente(modifier: Modifier = Modifier) = Text(Futbol.FUENTE, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 12.sp, modifier = modifier.padding(top = 14.dp))

// ---------------------------------------------------------------- «Tus partidos» (portada)

/** Arriba de la portada: el próximo partido de cada equipo tuyo (y el que se esté jugando, destacado y con su marcador). */
@Composable
fun TusPartidos(alIr: (Destino) -> Unit, alFicha: (Ficha) -> Unit) {
    val ctx = LocalContext.current
    EspejoCuenta.version
    val eqs = AjustesFutbol.equipos(ctx)
    val todos = rememberPartidos()
    Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Antetitulo("Tus partidos", Modifier.weight(1f)); EnlaceRevista("Fútbol →", Modifier.padding(start = 12.dp), acento = true) { alIr(Destino.Futbol) } }
        Filete(1.dp)
        if (eqs.isEmpty()) {
            Nota("Elige tus equipos y aquí tendrás su próximo partido, con sus canales: un toque y lo estás viendo.", Modifier.padding(top = 10.dp))
            MagLink("Seguir al FC Barcelona", "O elige otros en Ajustes → Fútbol", accent = true) { AjustesFutbol.alterna(ctx, com.gonzalo.iptv.futbol.CanalesFutbol.SUGERIDO, Competiciones.LALIGA.slug) }
            MagLink("Elegir mis equipos") { alIr(Destino.AjustesFutbol) }
            return@Column
        }
        val ahora = System.currentTimeMillis()
        val tus = todos?.let { Orden.tusPartidos(it, eqs.map { e -> e.first.id }, ahora) }
        // Y los de tus rivales, solo si se juegan ahora o hoy
        val deRivales = todos?.let { Orden.deRivales(it, AjustesFutbol.rivales(ctx).map { e -> e.first.id }, eqs.map { e -> e.first.id }, ahora, soloHoy = true) }.orEmpty()
        when {
            tus == null -> Nota("Mirando el calendario…", Modifier.padding(top = 10.dp))
            tus.isEmpty() -> Nota("Sin partidos a la vista (o sin conexión con los datos del fútbol).", Modifier.padding(top = 10.dp))
            else -> {
                val columnas = if (esAncha() && tus.size > 1) 2 else 1
                tus.chunked(columnas).forEach { fila ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        fila.forEach { p -> Box(Modifier.weight(1f)) { TarjetaPartido(p, ahora, { alFicha(Ficha.DePartido(p)) }, { alIr(Destino.AjustesFutbol) }) } }
                        repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        deRivales.forEach { p -> TarjetaPartido(p, ahora, { alFicha(Ficha.DePartido(p)) }, { alIr(Destino.AjustesFutbol) }) }
    }
}

// ---------------------------------------------------------------- «Fútbol»

/** «Fútbol»: tus partidos (los que vienen y los jugados), las clasificaciones y los goleadores. */
@Composable
fun PantFutbol(alVolver: () -> Unit, alIr: (Destino) -> Unit, alFicha: (Ficha) -> Unit, enPagina: Boolean = false) {
    val ctx = LocalContext.current
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    val m = margen()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).let { if (enPagina) it else it.statusBarsPadding() }.navigationBarsPadding().padding(horizontal = m)) {
        if (!enPagina) CabeceraSeccion("Tus equipos", "Fútbol", alVolver) { EnlaceRevista("Ajustes", acento = true) { alIr(Destino.AjustesFutbol) } }
        else Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("FÚTBOL", color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
            EnlaceRevista("Elegir equipos →", acento = true) { alIr(Destino.AjustesFutbol) }
        }
        PestanasRevista(listOf("Partidos", "Equipos"), pestana) { pestana = it }
        Column(Modifier.widthIn(max = 900.dp)) {
            when (pestana) {
                0 -> SeccionPartidos(alIr, alFicha)
                else -> SeccionEquipos(alIr, alFicha)
            }
        }
        PieFuente()
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SeccionPartidos(alIr: (Destino) -> Unit, alFicha: (Ficha) -> Unit) {
    val ctx = LocalContext.current
    val eqs = AjustesFutbol.equipos(ctx)
    val todos = rememberPartidos()
    if (eqs.isEmpty()) {
        Nota("Aún no sigues a ningún equipo.", Modifier.padding(top = 16.dp))
        MagLink("Seguir al FC Barcelona", "O elige otros en Ajustes → Fútbol", accent = true) { AjustesFutbol.alterna(ctx, com.gonzalo.iptv.futbol.CanalesFutbol.SUGERIDO, Competiciones.LALIGA.slug) }
        return
    }
    if (todos == null) { Nota("Mirando el calendario…", Modifier.padding(top = 16.dp)); return }
    val ahora = System.currentTimeMillis()
    val ids = eqs.map { it.first.id }
    val mios = ids.toSet()
    val riv = AjustesFutbol.rivales(ctx).map { it.first.id }
    val tus = Orden.tusPartidos(todos, ids, ahora)
    val vienen = todos.filter { p -> ids.any { p.juega(it) } && !p.estado.terminado && p.estado != Estado.CANCELADO && tus.none { it.id == p.id } && p.inicio >= ahora - Orden.DURACION }.take(12)
    val jugados = todos.filter { p -> ids.any { p.juega(it) } && p.estado.terminado }.sortedByDescending { it.inicio }.take(12)
    if (tus.isEmpty() && vienen.isEmpty() && jugados.isEmpty()) { Nota("Sin partidos (o sin conexión con los datos del fútbol).", Modifier.padding(top = 16.dp)); return }
    if (tus.isNotEmpty()) {
        Antetitulo("Ahora y lo siguiente", Modifier.padding(top = 18.dp)); Filete(1.dp)
        tus.forEach { p -> TarjetaPartido(p, ahora, { alFicha(Ficha.DePartido(p)) }, { alIr(Destino.AjustesFutbol) }) }
    }
    if (vienen.isNotEmpty()) {
        Antetitulo("Más adelante", Modifier.padding(top = 22.dp)); Filete(1.dp)
        vienen.forEach { p -> FilaPartido(p, ahora, mios, riv.toSet()) { alFicha(Ficha.DePartido(p)) } }
    }
    if (jugados.isNotEmpty()) {
        Antetitulo("Los últimos", Modifier.padding(top = 22.dp)); Filete(1.dp)
        jugados.forEach { p -> FilaPartido(p, ahora, mios, riv.toSet()) { alFicha(Ficha.DePartido(p)) } }
    }
    // Tus rivales: lo suyo que no es contra los tuyos (eso ya está arriba, «contra tu rival»)
    if (riv.isNotEmpty()) {
        val suyos = Orden.deRivales(todos, riv, ids, ahora)
        val suyosJugados = todos.filter { p -> riv.any { p.juega(it) } && ids.none { p.juega(it) } && p.estado.terminado }.sortedByDescending { it.inicio }.take(6)
        Antetitulo("Tus rivales", Modifier.padding(top = 26.dp)); Filete(1.dp)
        if (suyos.isEmpty() && suyosJugados.isEmpty()) Nota("Sin partidos de tus rivales a la vista.", Modifier.padding(top = 10.dp))
        suyos.forEach { p -> TarjetaPartido(p, ahora, { alFicha(Ficha.DePartido(p)) }, { alIr(Destino.AjustesFutbol) }) }
        suyosJugados.forEach { p -> FilaPartido(p, ahora, mios, riv.toSet()) { alFicha(Ficha.DePartido(p)) } }
    }
}

/** «Equipos»: los tuyos y tus rivales; un toque abre su página. */
@Composable
private fun SeccionEquipos(alIr: (Destino) -> Unit, alFicha: (Ficha) -> Unit) {
    val ctx = LocalContext.current
    EspejoCuenta.version
    listOf("Tus equipos" to AjustesFutbol.equipos(ctx), "Tus rivales" to AjustesFutbol.rivales(ctx)).forEach { (t, l) ->
        Antetitulo(t, Modifier.padding(top = 18.dp, bottom = 4.dp)); Filete(1.dp)
        if (l.isEmpty()) Nota(if (t == "Tus rivales") "Sin rivales todavía." else "Aún no sigues a ningún equipo.", Modifier.padding(top = 8.dp))
        l.forEach { (e, liga) ->
            Row(Modifier.fillMaxWidth().filaDe("e:" + e.id).tocable { alFicha(Ficha.DeEquipo(e, liga)) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Escudo(e, 34.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.nombre, color = Charcoal, fontFamily = Grotesca, fontStyle = if (t == "Tus rivales") FontStyle.Italic else null, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Etiqueta(if (Competiciones.esDeSelecciones(liga)) "Selección" else Competiciones.de(liga).nombre)
                }
                Text("→", color = Acento, fontFamily = Grotesca, fontSize = 20.sp)
            }
            Filete(1.dp, Line)
        }
    }
    MagLink("Elegir equipos y rivales", accent = true, modifier = Modifier.padding(top = 10.dp)) { alIr(Destino.AjustesFutbol) }
}

/** La clasificación de [liga] (leída de lo guardado y luego al día): tus equipos en acento, tus rivales marcados y [resaltar] destacados. */
@Composable
fun ClasificacionDe(liga: String, resaltar: Set<String> = emptySet()) {
    val ctx = LocalContext.current
    val c = rememberDato(liga) { f -> Futbol.clasificacion(ctx, liga, f) }
    val mios = AjustesFutbol.equipos(ctx).map { it.first.id }.toSet()
    val riv = AjustesFutbol.rivales(ctx).map { it.first.id }.toSet()
    if (!Competiciones.tieneTabla(liga)) { Nota("Esta competición se juega por eliminatorias: no tiene clasificación.", Modifier.padding(top = 12.dp)); return }
    if (c == null) { Nota("Leyendo la clasificación…", Modifier.padding(top = 12.dp)); return }
    TablaClasificacion(c, mios, riv, resaltar)
}

/** La tabla: los tuyos en acento; tus rivales, en cursiva con su marca «RIVAL». */
@Composable
fun TablaClasificacion(c: Clasificacion, mios: Set<String>, rivales: Set<String> = emptySet(), resaltar: Set<String> = emptySet()) {
    if (c.tablas.isEmpty()) { Nota("Esta competición aún no tiene clasificación.", Modifier.padding(top = 12.dp)); return }
    @Composable fun Num(t: String, w: Dp, negrita: Boolean = false, color: Color = Charcoal) =
        Text(t, color = color, fontFamily = Body, fontSize = 14.sp, fontWeight = if (negrita) FontWeight.Black else FontWeight.Medium, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.width(w))
    c.tablas.forEach { (nombre, filas) ->
        if (nombre.isNotBlank()) Antetitulo(nombre, Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Etiqueta("#", Modifier.width(28.dp)); Etiqueta("Equipo", Modifier.weight(1f))
            listOf("PJ", "DG", "Pts").forEach { Text(it.uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.width(when (it) { "Pts" -> 48.dp; "DG" -> 54.dp; else -> 40.dp })) }
        }
        Filete(2.dp)
        var nota = ""
        filas.forEach { f ->
            val mio = f.equipo.id in mios
            // (una raya fina donde cambia lo que da el puesto: Champions, Europa, descenso…)
            if (f.nota != nota && nota.isNotEmpty()) Filete(1.dp, Charcoal)
            nota = f.nota
            Row(Modifier.fillMaxWidth().let { if (f.equipo.id in resaltar) it.background(Sand) else it }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${f.puesto}", color = if (mio) Acento else Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, softWrap = false, modifier = Modifier.width(28.dp))
                EscudoEquipo(f.equipo, 26.dp)
                Spacer(Modifier.width(8.dp))
                val rival = f.equipo.id in rivales
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.equipo.nombre, color = if (mio) Acento else Charcoal, fontFamily = Grotesca, fontStyle = if (rival) FontStyle.Italic else null, fontWeight = if (mio || rival) FontWeight.Bold else FontWeight.Bold, fontSize = 16.sp, lineHeight = 18.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (rival) MarcaRival("Rival", Modifier.padding(start = 6.dp))
                }
                Num("${f.jugados}", 40.dp)
                Num(if (f.diferencia > 0) "+${f.diferencia}" else "${f.diferencia}", 54.dp, color = Muted)
                Text("${f.puntos}", color = if (mio) Acento else Charcoal, fontFamily = Grotesca, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
            }
            Filete(1.dp, Line)
        }
        val notas = filas.map { it.nota }.filter { it.isNotBlank() }.distinct()
        if (notas.isNotEmpty()) Text(notas.joinToString(" · "), color = Muted, fontFamily = Body, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Goleadores y asistentes de [liga]. */
@Composable
fun GoleadoresDe(liga: String) {
    val ctx = LocalContext.current
    val l: Lideres? = rememberDato(liga) { f -> Futbol.lideres(ctx, liga, f) }
    val mios = AjustesFutbol.equipos(ctx).map { it.first.id }.toSet()
    if (l == null) { Nota("Leyendo los goleadores…", Modifier.padding(top = 12.dp)); return }
    if (l.goles.isEmpty() && l.asistencias.isEmpty()) { Nota("Aún no hay goleadores en esta competición.", Modifier.padding(top = 12.dp)); return }
    listOf("Goles" to l.goles, "Asistencias" to l.asistencias).forEach { (t, lista) ->
        if (lista.isEmpty()) return@forEach
        Antetitulo(t, Modifier.padding(top = 18.dp, bottom = 4.dp)); Filete(1.dp)
        lista.take(15).forEachIndexed { k, x ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${k + 1}", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, softWrap = false, modifier = Modifier.width(28.dp))
                Escudo(x.equipo, 22.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(x.nombre, color = if (x.equipo.id in mios) Acento else Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text((x.equipo.nombre + if (x.partidos > 0) " · ${x.partidos} partidos" else "").uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 2)
                }
                Text("${x.cifra}", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 10.dp))
            }
            Filete(1.dp, Line)
        }
    }
}

// ---------------------------------------------------------------- la ficha del partido

/**
 * La ficha de un partido, por encima de la página: el marcador (o la hora), los botones de sus canales y, en pestañas, lo que pasa
 * (goles, tarjetas, cambios, VAR, con su minuto), las alineaciones (titulares y suplentes) y las cifras. Mientras se juega, se pone al
 * día sola (cada 40 segundos, solo mientras está abierta).
 */
@Composable
fun PantFichaPartido(p0: Partido, alCerrar: () -> Unit, alConfigurar: () -> Unit, alFicha: (Ficha) -> Unit = {}) {
    val ctx = LocalContext.current
    val f by produceState<FichaPartido?>(null, p0.id) {
        value = withContext(Dispatchers.IO) { Futbol.ficha(ctx, p0, fresco = false) }
        withContext(Dispatchers.IO) { Futbol.ficha(ctx, p0, fresco = true) }?.let { value = it }
        while (Orden.quizaEnDirecto(value?.partido ?: p0, System.currentTimeMillis())) {
            delay(40_000L)
            withContext(Dispatchers.IO) { Futbol.ficha(ctx, value?.partido ?: p0, fresco = true) }?.let { value = it }
        }
    }
    val p = f?.partido ?: p0
    val ahora = System.currentTimeMillis()
    val m = margen()
    var pestana by rememberSaveable(p0.id) { mutableIntStateOf(0) }
    var todos by rememberSaveable(p0.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Paper).atrapaFoco()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            // ---- La apertura: los colores de los dos en diagonal, el marcador, quién marcó y las pestañas
            AperturaPartido(p, ahora, f, pestana, { pestana = it }, alCerrar) { e -> alFicha(Ficha.DeEquipo(e, p.liga)) }
            if (!p.estado.terminado && p.estado != Estado.CANCELADO) BotonVerCanal(p)
            Column(Modifier.padding(horizontal = m).widthIn(max = 900.dp)) {
                val x = f
                when (pestana) {
                    0 -> {
                        TituloSeccion("Lo importante")
                        when {
                            p.estado == Estado.POR_JUGAR -> Nota("Aún no ha empezado.", Modifier.padding(top = 6.dp))
                            x == null -> Nota("Leyendo la ficha…", Modifier.padding(top = 6.dp))
                            else -> Momentos(x, todos) { todos = !todos }
                        }
                        if (x != null && x.cifras.isNotEmpty()) {
                            TituloSeccion("En números", derecha = "Todas →", alDerecha = { pestana = 2 })
                            CifrasCortas(x)
                        }
                        TituloSeccion("Dónde verlo")
                        DondeVerlo(p, conTitulo = false)
                        TituloSeccion("Clasificación")
                        ClasificacionDe(p.liga, setOf(p.local.id, p.visitante.id))
                        TituloSeccion("Cara a cara")
                        if (x == null) Nota("Leyendo los últimos partidos entre los dos…") else CaraACara(x) { q -> alFicha(Ficha.DePartido(q)) }
                    }
                    1 -> if (x == null) Nota("Aún sin alineaciones.", Modifier.padding(top = 18.dp)) else Alineaciones(x)
                    else -> if (x == null) Nota("Aún sin estadísticas.", Modifier.padding(top = 18.dp)) else Cifras(x)
                }
                PieFuente()
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Lo importante del partido: solo goles y rojas (lo último, arriba); «ver todos los momentos» enseña también cambios y tarjetas. */
@Composable
private fun Momentos(f: FichaPartido, todos: Boolean, alTodos: () -> Unit) {
    val p = f.partido
    val importantes = f.eventos.filter { it.tipo.esGol || it.tipo == TipoEvento.ROJA || it.tipo == TipoEvento.SEGUNDA_AMARILLA }.reversed()
    if (todos) Eventos(f)
    else if (importantes.isEmpty()) Nota(if (f.eventos.isEmpty()) "Sin nada que contar todavía." else "Sin goles ni rojas por ahora.", Modifier.padding(top = 6.dp))
    else importantes.forEach { FilaEvento(it, p) }
    if (f.eventos.size > importantes.size) Text(if (todos) "SOLO LO IMPORTANTE ←" else "VER TODOS LOS MOMENTOS (CAMBIOS Y TARJETAS) →", color = Acento, fontFamily = Body,
        fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.tocable(onClick = alTodos).padding(vertical = 12.dp))
}

/** Las dos o tres cifras que más dicen (posesión y tiros), con el color de cada equipo. */
@Composable
private fun CifrasCortas(f: FichaPartido) {
    val p = f.partido
    val ca = colorDuelo(p.local); val cb = colorDuelo(p.visitante)
    val claves = listOf("posesi", "tiros a puerta", "tiros")
    val elegidas = claves.mapNotNull { k -> f.cifras.firstOrNull { it.nombre.lowercase().startsWith(k) } }.distinct().take(3).ifEmpty { f.cifras.take(2) }
    elegidas.forEach { c -> BarraCifra(c.nombre, c.local, c.visitante, ca, cb) }
}

/** La etiqueta del estado de un partido en la apertura y en el cartel: «PRÓXIMO · SÁB 14 NOV · 21:00», «EN DIRECTO · 67'», «FINAL». */
fun etiquetaPartido(p: Partido, ahora: Long): String = when {
    p.estado.enDirecto -> "En directo · " + p.cuando(ahora)
    p.estado == Estado.POR_JUGAR -> "Próximo · " + Hora.diaYHora(p.inicio, ahora)
    else -> p.estado.texto
}.uppercase()

/** El encabezado de una sección de las fichas: su número (01, 02…) en el acento y el nombre en grotesca. */
@Composable
fun SeccionNumerada(@Suppress("UNUSED_PARAMETER") n: Int, texto: String, arriba: Dp = 26.dp) {
    // (desde la 0.9.0, sin el número ni la raya gruesa: un título limpio, como en la ficha del partido)
    TituloSeccion(texto, Modifier.padding(top = (arriba - 24.dp).coerceAtLeast(0.dp)))
}

/** La etiqueta en un bloque del acento (el estado del partido, «TU PRÓXIMO PARTIDO»…). */
@Composable
fun EtiquetaBloque(t: String, modifier: Modifier = Modifier) =
    Text(t.uppercase(), color = SobreBloque, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, maxLines = 2,
        modifier = modifier.background(AcentoBloque).padding(horizontal = 8.dp, vertical = 5.dp))

/**
 * Los dos equipos enfrentados sobre una imagen: sus escudos con paspartú, el marcador (o la hora) enorme en medio y sus nombres. Para el
 * cartel de la portada y la apertura de la ficha (letras claras: van sobre el velo oscuro).
 */
@Composable
fun Enfrentados(p: Partido, ahora: Long, tamEscudo: Dp, alEquipo: ((Equipo) -> Unit)? = null) {
    val blanco = Color.White
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        EscudoConPaspartu(p.local, tamEscudo, Modifier.let { if (alEquipo != null) it.tocable { alEquipo(p.local) } else it })
        Column(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val gl = p.golesLocal; val gv = p.golesVisitante
            val grande = gl != null && gv != null
            FitTitle(if (grande) "$gl–$gv" else Hora.reloj(p.inicio), if (grande) 64.sp else 44.sp, if (grande) 64.sp else 46.sp, color = blanco, family = Grotesca, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, unaLinea = true, minSize = 20f, letterSpacing = 0.sp, align = TextAlign.Center)
            if (p.penaltis.isNotBlank()) Text("Penaltis ${p.penaltis}", color = blanco.copy(alpha = 0.85f), fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        EscudoConPaspartu(p.visitante, tamEscudo, Modifier.let { if (alEquipo != null) it.tocable { alEquipo(p.visitante) } else it })
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(p.local.nombre, color = blanco, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(p.visitante.nombre, color = blanco, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/**
 * La apertura de la ficha de un partido: los colores de los dos equipos en diagonal con el velo, «← Volver» y la competición, el estado (en
 * directo, con el punto que late), los dos equipos con el marcador, quién marcó y las pestañas Resumen · Alineaciones · Estadísticas.
 */
@Composable
private fun AperturaPartido(p: Partido, ahora: Long, f: FichaPartido?, pestana: Int, alPestana: (Int) -> Unit, alCerrar: () -> Unit, alEquipo: (Equipo) -> Unit) {
    val m = margen()
    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        FondoDuelo(p.local, p.visitante, Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = m).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                EnlaceRevista("← Volver", color = Color.White.copy(alpha = 0.92f)) { alCerrar() }
                Spacer(Modifier.weight(1f))
                Text(p.competicion.corto.uppercase(), color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, maxLines = 1, softWrap = false)
            }
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, start = m, end = m), contentAlignment = Alignment.Center) { EtiquetaEstado(p, ahora) }
            Duelo(p, if (esAncha()) 96.dp else 72.dp, alEquipo, Modifier.padding(horizontal = m).padding(top = 20.dp, bottom = 10.dp))
            val marcaron = f?.let { goleadores(it) }.orEmpty()
            val linea = marcaron.ifBlank { listOfNotNull(if (p.estado != Estado.POR_JUGAR) Hora.diaYHora(p.inicio, ahora) else null, p.estadio.ifBlank { null }).joinToString(" · ") }
            if (linea.isNotBlank()) Text(linea, color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = m))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp).background(Color.Black.copy(alpha = 0.25f))) {
                listOf("Resumen", "Alineaciones", "Estadísticas").forEachIndexed { i, t ->
                    val on = i == pestana
                    Box(Modifier.weight(1f).tocable(fuera = 0.dp) { alPestana(i) }.drawBehind {
                        if (on) drawRect(Color.White, topLeft = Offset(0f, size.height - 3.dp.toPx()), size = Size(size.width, 3.dp.toPx()))
                    }.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                        Text(t.uppercase(), color = if (on) Color.White else Color.White.copy(alpha = 0.68f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.5.sp, letterSpacing = 1.1.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

/**
 * «VER EN TU CANAL →»: lo mismo que «Ver» de «Dónde verlo» (el primer canal de tus grupos para la competición, en la calidad que toque).
 * Sin canal todavía, «ELEGIR CANAL →» (se guarda en su grupo y se pone).
 */
@Composable
fun BotonVerCanal(p: Partido, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var v by remember { mutableIntStateOf(0) }
    v; EspejoCuenta.version; Futbol.version
    val ambito = rememberCoroutineScope()
    val ops = AjustesFutbol.donde(ctx, p.liga)
    val ver = ops.firstOrNull { it is com.gonzalo.iptv.futbol.Donde.Ver } as? com.gonzalo.iptv.futbol.Donde.Ver
    var eligiendo by remember { mutableStateOf<com.gonzalo.iptv.futbol.Donde?>(null) }
    fun pon(g: GrupoCanales) = ambito.launch {
        val aviso = withContext(Dispatchers.IO) { Futbol.pon(ctx, g, 0) }
        if (aviso != null) Toast.makeText(ctx, aviso, Toast.LENGTH_LONG).show()
    }
    BotonC(if (ver != null) "Ver en ${ver.grupo.boton}" else "Elegir canal", modifier.focoInicial(p.id + "ver"), fondo = AcentoBloque, letra = SobreBloque) {
        if (ver != null) pon(ver.grupo) else eligiendo = ops.firstOrNull()
    }
    eligiendo?.let { o -> EligeCanal(o, { eligiendo = null }) { g -> eligiendo = null; v++; pon(g) } }
}

/** El canal de un partido en corto, para el cartel: «MOVISTAR» (el primer grupo con canal) o null. */
@Composable
fun canalDe(p: Partido): String? {
    val ctx = LocalContext.current
    EspejoCuenta.version
    return (AjustesFutbol.donde(ctx, p.liga).firstOrNull { it is com.gonzalo.iptv.futbol.Donde.Ver } as? com.gonzalo.iptv.futbol.Donde.Ver)?.grupo?.boton
}

/** Los dos equipos, grandes, con el marcador (o la hora) en medio; debajo, cómo va y dónde se juega. */
@Composable
private fun Marcador(p: Partido, ahora: Long, alEquipo: (Equipo) -> Unit = {}) {
    val grande = esAncha()
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.Top) {
        @Composable fun Lado(e: Equipo, mod: Modifier) = Column(mod.tocable { alEquipo(e) }, horizontalAlignment = Alignment.CenterHorizontally) {
            Escudo(e, if (grande) 84.dp else 60.dp)
            Text(e.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        }
        Lado(p.local, Modifier.weight(1f))
        Column(Modifier.padding(horizontal = 8.dp).widthIn(min = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val vivo = p.estado.enDirecto
            if (p.golesLocal != null && p.golesVisitante != null)
                Text("${p.golesLocal}–${p.golesVisitante}", color = if (vivo) Acento else Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = if (grande) 64.sp else 46.sp, maxLines = 1, softWrap = false)
            else Text(Hora.reloj(p.inicio), color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = if (grande) 48.sp else 34.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 8.dp))
            val estado = when {
                vivo -> p.cuando(ahora)
                p.estado == Estado.POR_JUGAR -> Hora.diaRelativo(p.inicio, ahora)
                else -> p.estado.texto
            }
            Text(estado.uppercase(), color = if (vivo) Acento else Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, textAlign = TextAlign.Center, maxLines = 2)
            if (p.penaltis.isNotBlank()) Etiqueta("Penaltis ${p.penaltis}", Modifier.padding(top = 2.dp))
        }
        Lado(p.visitante, Modifier.weight(1f))
    }
    val donde = listOfNotNull(if (p.estado != Estado.POR_JUGAR) Hora.diaYHora(p.inicio, ahora) else null, p.estadio.ifBlank { null }).joinToString(" · ")
    if (donde.isNotBlank()) Text(donde, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
}

/** El color de lo que pasa: goles en acento, las amarillas y las rojas en su color, lo demás en gris. */
private fun colorEvento(t: TipoEvento): Color = when {
    t.esGol -> Acento
    t == TipoEvento.AMARILLA -> Color(0xFFE2B007)
    t == TipoEvento.ROJA || t == TipoEvento.SEGUNDA_AMARILLA -> Color(0xFFC62828)
    else -> Muted
}

@Composable
private fun Eventos(f: FichaPartido) {
    val p = f.partido
    if (f.eventos.isEmpty()) { Nota(if (p.estado == Estado.POR_JUGAR) "Aún no ha empezado." else "Sin nada que contar todavía.", Modifier.padding(top = 14.dp)); return }
    Spacer(Modifier.height(8.dp))
    // (lo último, arriba mientras se juega: es lo que se busca; terminado, en orden)
    val l = if (p.estado.enDirecto) f.eventos.reversed() else f.eventos
    l.forEach { e -> FilaEvento(e, p) }
}

@Composable
private fun FilaEvento(e: Evento, p: Partido) {
    if (e.tipo.esHito) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(Line))
            Text(e.frase().uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 10.dp))
            Box(Modifier.weight(1f).height(1.dp).background(Line))
        }
        return
    }
    val equipo = if (e.equipo == p.local.id) p.local else if (e.equipo == p.visitante.id) p.visitante else null
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(e.minuto, color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 44.dp).padding(top = 1.dp, end = 6.dp))
        Box(Modifier.padding(top = 5.dp, end = 10.dp).size(width = if (e.tipo.esTarjeta) 8.dp else 10.dp, height = if (e.tipo.esTarjeta) 12.dp else 10.dp)
            .clip(if (e.tipo.esGol) androidx.compose.foundation.shape.CircleShape else esquina(2.dp)).background(colorEvento(e.tipo)))
        Column(Modifier.weight(1f)) {
            // Un gol: quién lo marcó, en grande; debajo, el equipo y quién asistió (o cómo fue). Lo demás, su frase.
            val gol = e.tipo.esGol && e.jugador.isNotBlank()
            val texto = if (gol) e.jugador else e.frase().removePrefix("${e.minuto} ")
            val como = when (e.tipo) { TipoEvento.GOL_CABEZA -> "de cabeza"; TipoEvento.GOL_FALTA -> "de falta"; TipoEvento.GOL_PENALTI -> "de penalti"; TipoEvento.GOL_PROPIA -> "en propia puerta"; else -> "" }
            val sub = listOfNotNull(equipo?.nombre, if (gol && e.otro.isNotBlank()) "asiste ${e.otro}" else null, if (gol) como.ifBlank { null } else null).joinToString(" · ")
            Text(texto, color = Charcoal, fontFamily = Grotesca, fontWeight = if (e.tipo.esGol) FontWeight.ExtraBold else FontWeight.Bold, fontSize = if (gol) 15.sp else 14.sp, lineHeight = 19.sp)
            if (sub.isNotBlank()) Text(sub.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
    Filete(1.dp, Line)
}

@Composable
private fun Alineaciones(f: FichaPartido) {
    if (f.alineaciones.isEmpty()) { Nota("Las alineaciones salen un rato antes del partido.", Modifier.padding(top = 14.dp)); return }
    if (esAncha()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) { f.alineaciones.forEach { a -> Column(Modifier.weight(1f)) { Alineacion1(a) } } }
    else f.alineaciones.forEach { Alineacion1(it) }
}

@Composable
private fun Alineacion1(a: Alineacion) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Escudo(a.equipo, 26.dp); Spacer(Modifier.width(8.dp))
        Text(a.equipo.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Black, fontSize = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (a.formacion.isNotBlank()) Text(a.formacion, color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
    }
    Filete(2.dp)
    a.titulares.forEach { FilaJugador(it) }
    if (a.suplentes.isNotEmpty()) {
        Etiqueta("Suplentes", Modifier.padding(top = 12.dp, bottom = 2.dp))
        a.suplentes.forEach { FilaJugador(it) }
    }
}

/** Las posiciones de la fuente (G, CD-L, CM…) como se dicen aquí. */
private fun posicion(p: String): String = when (p.substringBefore('-').uppercase()) {
    "G", "GK" -> "POR"; "D", "CD", "LB", "RB", "CB", "WB" -> "DEF"; "M", "CM", "DM", "AM", "LM", "RM" -> "MED"; "F", "CF", "ST", "LW", "RW", "FW", "W" -> "DEL"; else -> ""
}

@Composable
private fun FilaJugador(j: Jugador) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(j.dorsal, color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 28.dp).padding(end = 10.dp))
        Text(j.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        val marca = when { j.entra -> "↑ entra"; j.sale -> "↓ sale"; else -> "" }
        if (marca.isNotBlank()) Text(marca.uppercase(), color = if (j.entra) Acento else Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
        val pos = posicion(j.posicion)
        if (pos.isNotBlank()) Text(pos, color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 10.dp))
    }
    Filete(1.dp, Line)
}

@Composable
private fun Cifras(f: FichaPartido) {
    if (f.cifras.isEmpty()) { Nota("Las cifras salen cuando empieza el partido.", Modifier.padding(top = 14.dp)); return }
    val p = f.partido
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp)) {
        Text(p.local.corto.uppercase(), color = Charcoal, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(p.visitante.corto.uppercase(), color = Charcoal, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
    Filete(2.dp)
    val ca = colorDuelo(p.local); val cb = colorDuelo(p.visitante)
    f.cifras.forEach { c -> BarraCifra(c.nombre, c.local, c.visitante, ca, cb) }
}

/** «Cara a cara»: los últimos entre los dos y los últimos de cada uno. Un toque abre su ficha. */
@Composable
private fun CaraACara(f: FichaPartido, alAbrir: (Partido) -> Unit) {
    val ctx = LocalContext.current
    val ahora = System.currentTimeMillis()
    val mios = AjustesFutbol.equipos(ctx).map { it.first.id }.toSet()
    val riv = AjustesFutbol.rivales(ctx).map { it.first.id }.toSet()
    if (f.caraACara.isEmpty() && f.ultimos.isEmpty()) { Nota("Sin partidos anteriores entre los dos.", Modifier.padding(top = 14.dp)); return }
    if (f.caraACara.isNotEmpty()) {
        val p = f.partido
        val gl = f.caraACara.count { q -> val g = q.golesLocal ?: 0; val v = q.golesVisitante ?: 0; (q.local.id == p.local.id && g > v) || (q.visitante.id == p.local.id && v > g) }
        val gv = f.caraACara.count { q -> val g = q.golesLocal ?: 0; val v = q.golesVisitante ?: 0; (q.local.id == p.visitante.id && g > v) || (q.visitante.id == p.visitante.id && v > g) }
        Antetitulo("Entre los dos", Modifier.padding(top = 18.dp, bottom = 2.dp))
        Etiqueta("${p.local.corto} $gl · empates ${f.caraACara.size - gl - gv} · ${p.visitante.corto} $gv", Modifier.padding(bottom = 4.dp))
        Filete(1.dp)
        f.caraACara.forEach { q -> FilaPartido(q, ahora, mios, riv) { alAbrir(q) } }
    }
    f.ultimos.forEach { (id, l) ->
        val e = if (id == f.partido.local.id) f.partido.local else if (id == f.partido.visitante.id) f.partido.visitante else return@forEach
        if (l.isEmpty()) return@forEach
        Antetitulo("Lo último de ${e.nombre}", Modifier.padding(top = 18.dp, bottom = 4.dp)); Filete(1.dp)
        l.forEach { q -> FilaPartido(q, ahora, mios, riv) { alAbrir(q) } }
    }
}

/**
 * «Dónde verlo»: tus canales para la competición del partido (honesto: no se sabe si lo dan ahí; son los que tú has puesto). Por cada
 * grupo con canal, «▶ VER EN MOVISTAR» (en la calidad que toque); un grupo sin canal, «ELEGIR CANAL» (se guarda en él y se pone); si la
 * competición no tiene ningún grupo, «ELEGIR CANAL» crea el suyo con su nombre, se lo asigna, lo guarda y lo pone.
 */
@Composable
fun DondeVerlo(p: Partido, conTitulo: Boolean = true) {
    val ctx = LocalContext.current
    var v by remember { mutableIntStateOf(0) }
    v; EspejoCuenta.version; Futbol.version
    val ambito = rememberCoroutineScope()
    val ops = AjustesFutbol.donde(ctx, p.liga)
    var eligiendo by remember { mutableStateOf<com.gonzalo.iptv.futbol.Donde?>(null) }
    fun pon(g: GrupoCanales, i: Int) = ambito.launch {
        val aviso = withContext(Dispatchers.IO) { Futbol.pon(ctx, g, i) }
        if (aviso != null) Toast.makeText(ctx, aviso, Toast.LENGTH_LONG).show()
    }
    Column(Modifier.fillMaxWidth().padding(top = if (conTitulo) 16.dp else 0.dp)) {
        if (conTitulo) Antetitulo("Dónde verlo")
        Text(com.gonzalo.iptv.futbol.DondeVer.titulo(p.liga), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        if (p.estado.terminado) { Nota("El partido ya ha terminado."); return@Column }
        ops.forEachIndexed { k, o ->
            val mod = Modifier.fillMaxWidth().padding(top = if (k == 0) 0.dp else 8.dp).let { if (k == 0) it.focoInicial(p.id) else it }
            when (o) {
                is com.gonzalo.iptv.futbol.Donde.Ver -> {
                    AccionGrande(Icons.Rounded.PlayArrow, "Ver en ${o.grupo.boton}", mod, acento = k == 0 || ops.none { it is com.gonzalo.iptv.futbol.Donde.Ver && ops.indexOf(it) < k }) { pon(o.grupo, 0) }
                    if (o.grupo.canales.size > 1) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Etiqueta("Otros canales de ${o.grupo.boton}:")
                        (2..minOf(o.grupo.canales.size, 5)).forEach { n ->
                            Text("$n", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, softWrap = false,
                                modifier = Modifier.semantics { contentDescription = "${o.grupo.boton}, canal $n" }.tocable { pon(o.grupo, n - 1) }.padding(horizontal = 10.dp, vertical = 10.dp))
                        }
                    }
                }
                is com.gonzalo.iptv.futbol.Donde.Elegir -> {
                    AccionGrande(null, "Elegir canal · ${o.grupo.boton}", mod, acento = false) { eligiendo = o }
                    Etiqueta("«${o.grupo.nombre}» aún no tiene canales", Modifier.padding(top = 3.dp))
                }
                is com.gonzalo.iptv.futbol.Donde.Crear -> {
                    AccionGrande(null, "Elegir canal", mod, acento = false) { eligiendo = o }
                    Nota("Aún no tienes canales para ${o.nombre}: elige uno de tu lista y se queda guardado para sus partidos.", Modifier.padding(top = 6.dp))
                }
            }
        }
    }
    eligiendo?.let { o -> EligeCanal(o, { eligiendo = null }) { g -> eligiendo = null; v++; pon(g, 0) } }
}

/** Elegir un canal para un grupo sin canales (o crear el de la competición): se guarda y se avisa con el grupo para ponerlo. */
@Composable
fun EligeCanal(o: com.gonzalo.iptv.futbol.Donde, alCerrar: () -> Unit, alGuardado: (GrupoCanales) -> Unit) {
    val ctx = LocalContext.current
    val nombre = when (o) { is com.gonzalo.iptv.futbol.Donde.Elegir -> o.grupo.nombre; is com.gonzalo.iptv.futbol.Donde.Crear -> o.nombre; else -> "" }
    SelectorCanal("Un canal para «$nombre»", "Se queda guardado y se pone ya", alCerrar = alCerrar) { l, c, todos ->
        val r = com.gonzalo.iptv.futbol.RefCanal(l.id, c.clave, c.nombre)
        val e = com.gonzalo.iptv.futbol.CanalesFutbol.entradaCon(r, todos.map { com.gonzalo.iptv.futbol.RefCanal(l.id, it.clave, it.nombre) })
        val g = when (o) {
            is com.gonzalo.iptv.futbol.Donde.Elegir -> AjustesFutbol.guardaEn(ctx, o.grupo.id, e)
            is com.gonzalo.iptv.futbol.Donde.Crear -> AjustesFutbol.creaGrupoPara(ctx, o.liga, e)
            else -> null
        }
        if (g != null) alGuardado(g) else alCerrar()
    }
}
