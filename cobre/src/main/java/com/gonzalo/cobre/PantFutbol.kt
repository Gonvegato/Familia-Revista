package com.gonzalo.cobre

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
    val eqs = AjustesFutbol.equipos(ctx).map { it.first.id }
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
private fun <T> rememberDato(vararg claves: Any?, leer: (Boolean) -> T?): T? {
    val r by produceState<T?>(null, *claves, Futbol.version) {
        value = withContext(Dispatchers.IO) { leer(false) }
        withContext(Dispatchers.IO) { leer(true) }?.let { value = it }
    }
    return r
}

// ---------------------------------------------------------------- piezas

/** El escudo de un equipo (sin escudo, o mientras llega, sus siglas en un círculo de papel). */
@Composable
fun Escudo(e: Equipo, tam: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(tam), contentAlignment = Alignment.Center) {
        @Composable fun Siglas() = Box(Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape).background(Sand), contentAlignment = Alignment.Center) {
            Text(e.siglas.ifBlank { iniciales(e.nombre) }.take(3), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = (tam.value * 0.28f).sp, lineHeight = (tam.value * 0.3f).sp, maxLines = 1, softWrap = false,
                style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)))
        }
        if (e.escudo.isBlank()) Siglas()
        else SubcomposeAsyncImage(model = e.escudo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), loading = { Siglas() }, error = { Siglas() })
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

/** Un equipo y sus goles, en una línea (el nombre, en hasta dos líneas: con la letra grande no se corta). */
@Composable
private fun LineaEquipo(e: Equipo, goles: Int?, tam: Dp, letra: Int, ganador: Boolean, mio: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Escudo(e, tam)
        Spacer(Modifier.width(10.dp))
        Text(e.nombre, color = if (mio) Charcoal else Charcoal.copy(alpha = 0.88f), fontFamily = Display, fontWeight = if (ganador || mio) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = letra.sp, lineHeight = (letra * 1.15f).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (goles != null) Text("$goles", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (letra + 6).sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 10.dp))
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
    val vivo = p.estado.enDirecto
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.fillMaxWidth().tocable(onClick = alAbrir).semantics { contentDescription = "${p.local.nombre} contra ${p.visitante.nombre}" }.padding(vertical = 4.dp)) {
            LineaPartido(p, ahora, Modifier.padding(bottom = 6.dp))
            val gl = p.golesLocal; val gv = p.golesVisitante
            LineaEquipo(p.local, gl, if (vivo) 38.dp else 32.dp, if (vivo) 22 else 20, gl != null && gv != null && gl > gv && p.estado.terminado, p.local.id in mios)
            LineaEquipo(p.visitante, gv, if (vivo) 38.dp else 32.dp, if (vivo) 22 else 20, gl != null && gv != null && gv > gl && p.estado.terminado, p.visitante.id in mios)
            if (p.penaltis.isNotBlank()) Etiqueta("Penaltis: ${p.penaltis}", Modifier.padding(top = 2.dp))
        }
        if (!p.estado.terminado && p.estado != Estado.CANCELADO) BotonesCanal(p, alConfigurar, Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(12.dp))
        Filete(1.dp, Line)
    }
}

/** Un partido en una línea (las listas de «Fútbol»): a la izquierda, el día y la hora (o «Final»); los dos equipos con sus goles. */
@Composable
fun FilaPartido(p: Partido, ahora: Long, mios: Set<String>, alAbrir: () -> Unit) {
    Row(Modifier.fillMaxWidth().tocable(onClick = alAbrir).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.widthIn(min = 78.dp).padding(end = 10.dp)) {
            val (a, b) = when {
                p.estado.enDirecto -> "En directo" to p.cuando(ahora)
                p.estado == Estado.POR_JUGAR -> Hora.diaRelativo(p.inicio, ahora) to Hora.reloj(p.inicio)
                else -> Hora.dia(p.inicio) to p.estado.texto
            }
            Text(a.uppercase(), color = if (p.estado.enDirecto) Acento else Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, maxLines = 1, softWrap = false)
            Text(b, color = if (p.estado.enDirecto) Acento else Charcoal, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Text(p.competicion.corto.uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, softWrap = false)
        }
        Column(Modifier.weight(1f)) {
            val gl = p.golesLocal; val gv = p.golesVisitante
            LineaEquipo(p.local, gl, 22.dp, 16, gl != null && gv != null && gl > gv, p.local.id in mios)
            LineaEquipo(p.visitante, gv, 22.dp, 16, gl != null && gv != null && gv > gl, p.visitante.id in mios)
        }
    }
    Filete(1.dp, Line)
}

/** Un botón de canal: relleno de acento (el primero) o con el contorno de tinta; en versalitas y en una línea. */
@Composable
private fun BotonCanal(texto: String, lleno: Boolean, alHacer: () -> Unit) {
    val col = if (lleno) SobreAcento else Charcoal
    Row(Modifier.heightIn(min = 40.dp).clip(pildora()).let { if (lleno) it.background(Acento) else it.border(1.5.dp, Charcoal, pildora()) }
        .semantics { role = Role.Button }.tocable(onClick = alHacer).padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.PlayArrow, null, tint = col, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(texto.uppercase(), color = col, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.2.sp, maxLines = 1, softWrap = false)
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
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    }
}

// ---------------------------------------------------------------- «Fútbol»

/** «Fútbol»: tus partidos (los que vienen y los jugados), las clasificaciones y los goleadores. */
@Composable
fun PantFutbol(alVolver: () -> Unit, alIr: (Destino) -> Unit, alFicha: (Ficha) -> Unit) {
    val ctx = LocalContext.current
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    val m = margen()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = m)) {
        CabeceraSeccion("Fútbol", "Tus equipos", alVolver) { EnlaceRevista("Ajustes", acento = true) { alIr(Destino.AjustesFutbol) } }
        PestanasRevista(listOf("Partidos", "Clasificación", "Goleadores"), pestana) { pestana = it }
        Column(Modifier.widthIn(max = 900.dp)) {
            when (pestana) {
                0 -> SeccionPartidos(alIr, alFicha)
                1 -> SeccionClasificacion()
                else -> SeccionGoleadores()
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
        vienen.forEach { p -> FilaPartido(p, ahora, mios) { alFicha(Ficha.DePartido(p)) } }
    }
    if (jugados.isNotEmpty()) {
        Antetitulo("Los últimos", Modifier.padding(top = 22.dp)); Filete(1.dp)
        jugados.forEach { p -> FilaPartido(p, ahora, mios) { alFicha(Ficha.DePartido(p)) } }
    }
}

@Composable
private fun SeccionClasificacion() {
    val ctx = LocalContext.current
    val comps = Competiciones.CON_TABLA
    var i by rememberSaveable { mutableIntStateOf(0) }
    Spacer(Modifier.height(8.dp))
    FiltrosRevista(comps.map { it.corto }, i) { i = it }
    val liga = comps[i].slug
    val c = rememberDato(liga) { f -> Futbol.clasificacion(ctx, liga, f) }
    val mios = AjustesFutbol.equipos(ctx).map { it.first.id }.toSet()
    if (c == null) { Nota("Leyendo la clasificación…", Modifier.padding(top = 12.dp)); return }
    TablaClasificacion(c, mios)
}

@Composable
private fun TablaClasificacion(c: Clasificacion, mios: Set<String>) {
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
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${f.puesto}", color = if (mio) Acento else Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, softWrap = false, modifier = Modifier.width(28.dp))
                Escudo(f.equipo, 22.dp)
                Spacer(Modifier.width(8.dp))
                Text(f.equipo.nombre, color = if (mio) Acento else Charcoal, fontFamily = Display, fontWeight = if (mio) FontWeight.Bold else FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 18.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Num("${f.jugados}", 40.dp)
                Num(if (f.diferencia > 0) "+${f.diferencia}" else "${f.diferencia}", 54.dp, color = Muted)
                Num("${f.puntos}", 48.dp, negrita = true)
            }
            Filete(1.dp, Line)
        }
        val notas = filas.map { it.nota }.filter { it.isNotBlank() }.distinct()
        if (notas.isNotEmpty()) Text(notas.joinToString(" · "), color = Muted, fontFamily = Body, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SeccionGoleadores() {
    val ctx = LocalContext.current
    val comps = Competiciones.CON_TABLA + Competiciones.COPA
    var i by rememberSaveable { mutableIntStateOf(0) }
    Spacer(Modifier.height(8.dp))
    FiltrosRevista(comps.map { it.corto }, i) { i = it }
    val liga = comps[i].slug
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
                    Text(x.nombre, color = if (x.equipo.id in mios) Acento else Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text((x.equipo.nombre + if (x.partidos > 0) " · ${x.partidos} partidos" else "").uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 2)
                }
                Text("${x.cifra}", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 10.dp))
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
fun PantFichaPartido(p0: Partido, alCerrar: () -> Unit, alConfigurar: () -> Unit) {
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
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    val m = margen()
    Box(Modifier.fillMaxSize().background(Paper).atrapaFoco()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = m)) {
            RevistaTopBar(alCerrar, "Volver")
            Column(Modifier.widthIn(max = 900.dp)) {
                Text(p.competicion.nombre.uppercase(), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                Filete(3.dp, modifier = Modifier.padding(top = 4.dp))
                Marcador(p, ahora)
                Filete(1.dp)
                if (!p.estado.terminado && p.estado != Estado.CANCELADO) BotonesCanal(p, alConfigurar, Modifier.padding(top = 14.dp).focoInicial(p.id))
                Spacer(Modifier.height(10.dp))
                PestanasRevista(listOf("Lo que pasa", "Alineaciones", "Cifras"), pestana) { pestana = it }
                val x = f
                when {
                    x == null -> Nota(if (p.estado == Estado.POR_JUGAR) "Aún no ha empezado." else "Leyendo la ficha…", Modifier.padding(top = 14.dp))
                    pestana == 0 -> Eventos(x)
                    pestana == 1 -> Alineaciones(x)
                    else -> Cifras(x)
                }
                PieFuente()
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Los dos equipos, grandes, con el marcador (o la hora) en medio; debajo, cómo va y dónde se juega. */
@Composable
private fun Marcador(p: Partido, ahora: Long) {
    val grande = esAncha()
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.Top) {
        @Composable fun Lado(e: Equipo, mod: Modifier) = Column(mod, horizontalAlignment = Alignment.CenterHorizontally) {
            Escudo(e, if (grande) 84.dp else 60.dp)
            Text(e.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        }
        Lado(p.local, Modifier.weight(1f))
        Column(Modifier.padding(horizontal = 8.dp).widthIn(min = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val vivo = p.estado.enDirecto
            if (p.golesLocal != null && p.golesVisitante != null)
                Text("${p.golesLocal}–${p.golesVisitante}", color = if (vivo) Acento else Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = if (grande) 64.sp else 46.sp, maxLines = 1, softWrap = false)
            else Text(Hora.reloj(p.inicio), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = if (grande) 48.sp else 34.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 8.dp))
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
        Text(e.minuto, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 52.dp).padding(top = 2.dp, end = 6.dp))
        Box(Modifier.padding(top = 5.dp, end = 10.dp).size(width = if (e.tipo.esTarjeta) 8.dp else 10.dp, height = if (e.tipo.esTarjeta) 12.dp else 10.dp)
            .clip(if (e.tipo.esGol) androidx.compose.foundation.shape.CircleShape else esquina(2.dp)).background(colorEvento(e.tipo)))
        Column(Modifier.weight(1f)) {
            val texto = e.frase().removePrefix("${e.minuto} ")
            Text(texto, color = Charcoal, fontFamily = Display, fontWeight = if (e.tipo.esGol) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp)
            if (equipo != null) Text(equipo.nombre.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Text(a.equipo.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
        Text(j.nombre, color = Charcoal, fontFamily = Display, fontSize = 16.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
    f.cifras.forEach { c ->
        val a = c.local.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 0f
        val b = c.visitante.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 0f
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(c.local, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1, softWrap = false)
                Text(c.nombre, color = Muted, fontFamily = Body, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Text(c.visitante, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1, softWrap = false)
            }
            if (a + b > 0f) Row(Modifier.fillMaxWidth().padding(top = 5.dp).height(4.dp)) {
                Box(Modifier.weight(maxOf(a, 0.001f)).fillMaxHeight().background(if (a >= b) Acento else Line))
                Spacer(Modifier.width(3.dp))
                Box(Modifier.weight(maxOf(b, 0.001f)).fillMaxHeight().background(if (b > a) Acento else Line))
            }
        }
    }
}
