package com.gonzalo.cobre.pc

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.mov.filaDe
import com.gonzalo.familia.pc.revista.*
import com.gonzalo.iptv.futbol.Clasificacion
import com.gonzalo.iptv.futbol.Competiciones
import com.gonzalo.iptv.futbol.Equipo
import com.gonzalo.iptv.futbol.Estado
import com.gonzalo.iptv.futbol.Evento
import com.gonzalo.iptv.futbol.FichaPartido
import com.gonzalo.iptv.futbol.Hora
import com.gonzalo.iptv.futbol.Orden
import com.gonzalo.iptv.futbol.Partido
import com.gonzalo.iptv.futbol.TipoEvento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Una ficha del fútbol, encima de las páginas (y unas sobre otras: de un partido a un equipo, a otro partido…). */
sealed class FichaF {
    class DePartido(val partido: Partido) : FichaF()
    class DeEquipo(val equipo: Equipo, val liga: String) : FichaF()
    class DeCompeticion(val liga: String) : FichaF()
    /** La clave de la fila de la que nace (la de un partido, un equipo o una competición se apunta con la misma). */
    val clave: String get() = when (this) { is DePartido -> "p:" + partido.id; is DeEquipo -> "e:" + equipo.id; is DeCompeticion -> "c:" + liga }
}

/** Algo del fútbol leído fuera de la ventana: primero lo guardado, luego lo fresco; otra vez cuando cambian tus ajustes. */
@Composable
fun <T> rememberFutbol(vararg claves: Any?, leer: (Boolean) -> T?): T? {
    val v = Futbol.version
    LaFamilia.cambios
    val r by produceState<T?>(null, *claves, v) {
        value = withContext(Dispatchers.IO) { runCatching { leer(false) }.getOrNull() }
        withContext(Dispatchers.IO) { runCatching { leer(true) }.getOrNull() }?.let { value = it }
    }
    return r
}

/** Tus partidos y los de tus rivales (con el marcador al día mientras se juega alguno: cada minuto mientras miras). */
@Composable
fun rememberPartidos(): List<Partido>? {
    val v = Futbol.version
    LaFamilia.cambios
    val eqs = AjustesFutbol.equipos() + AjustesFutbol.rivales()
    val r by produceState<List<Partido>?>(null, v, eqs) {
        value = withContext(Dispatchers.IO) { runCatching { Futbol.partidos(fresco = false) }.getOrNull() }
        while (true) {
            withContext(Dispatchers.IO) { runCatching { Futbol.partidos(fresco = true) }.getOrNull() }?.let { value = it }
            if (value.orEmpty().none { Orden.quizaEnDirecto(it, System.currentTimeMillis()) }) break
            delay(60_000L)
        }
    }
    return r
}

/** «VER EN MOVISTAR →» (el primer grupo con canal para la competición) o «ELEGIR CANAL»; pone el canal en mpv. */
@Composable
fun BotonVerCanal(p: Partido, nav: Nav, modifier: Modifier = Modifier) {
    LaFamilia.cambios
    val scope = rememberCoroutineScope()
    val ver = AjustesFutbol.donde(p.liga).firstOrNull { it is com.gonzalo.iptv.futbol.Donde.Ver } as? com.gonzalo.iptv.futbol.Donde.Ver
    BotonC(if (ver != null) "Ver en ${ver.grupo.boton}" else "Elige sus canales en Nut del móvil", modifier, fondo = Acento, letra = SobreAcento) {
        if (ver != null) scope.launch { withContext(Dispatchers.IO) { Futbol.pon(ver.grupo) }?.let { nav.aviso(it) } }
        else nav.aviso("Los canales de cada competición se eligen en Nut del móvil (Ajustes → Fútbol): llegan aquí solos.")
    }
}

/** Los botones pequeños de los canales de un partido de los tuyos (uno por grupo; 2, 3… ponen los siguientes). */
@Composable
fun BotonesCanal(p: Partido, nav: Nav, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val grupos = AjustesFutbol.gruposDe(p.liga).filter { it.canales.isNotEmpty() }
    if (grupos.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        grupos.forEachIndexed { gi, g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val lleno = gi == 0
                Text("▶ " + g.boton.uppercase(), color = if (lleno) Paper else Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1,
                    modifier = Modifier.levanta(alto = 0.dp, hundido = 0.95f) { scope.launch { withContext(Dispatchers.IO) { Futbol.pon(g, 0) }?.let { nav.aviso(it) } } }
                        .let { if (lleno) it.background(Charcoal) else it.border(1.5.dp, Charcoal) }.padding(horizontal = 10.dp, vertical = 6.dp))
                (2..minOf(g.canales.size, 4)).forEach { n ->
                    Text("$n", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.levanta(alto = 0.dp) { scope.launch { withContext(Dispatchers.IO) { Futbol.pon(g, n - 1) }?.let { nav.aviso(it) } } }.padding(horizontal = 8.dp, vertical = 6.dp))
                }
            }
        }
    }
}

/** Una página del fútbol, con su barra de desplazamiento y su contenido hasta 1440 de ancho. */
@Composable
fun PaginaF(contenido: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 1440.dp).fillMaxWidth().padding(horizontal = 48.dp).padding(top = 28.dp, bottom = 48.dp), content = contenido)
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

// ---------------------------------------------------------------- la portada

/**
 * La portada a dos lados: a la izquierda, tu partido (el que se juega ahora o el próximo) con los colores de los dos equipos, «Ver en …» y
 * los partidos de hoy; a la derecha, la racha de tus equipos y tus equipos.
 */
@Composable
fun PaginaPortadaF(nav: Nav, alFicha: (FichaF) -> Unit, alPagina: (Int) -> Unit) {
    LaFamilia.cambios
    val eqs = AjustesFutbol.equipos()
    val ids = eqs.map { it.first.id }
    val mios = ids.toSet(); val riv = AjustesFutbol.rivales().map { it.first.id }.toSet()
    val todos = rememberPartidos()
    val ahora = System.currentTimeMillis()
    val cartel = todos?.let { Orden.tusPartidos(it, ids, ahora).firstOrNull() }
    val hoyL = rememberFutbol(Hora.diaDe(ahora)) { f -> Futbol.dia(Hora.diaDe(ahora), fresco = f) }
    val deHoy = hoyL?.sortedWith(compareBy({ !it.estado.enDirecto }, { !(mios.any { m -> it.juega(m) }) }, { it.estado.terminado }, { it.inicio }))
    PaginaF {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
            Column(Modifier.weight(1.4f)) {
                when {
                    eqs.isEmpty() -> Vacio("Elige tus equipos en Nut del móvil", "Ajustes → Fútbol: tus equipos llegan aquí solos, con sus canales.")
                    cartel != null -> {
                        Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min).filaDe("p:" + cartel.id).levanta(alto = 2.dp, hundido = 0.99f) { alFicha(FichaF.DePartido(cartel)) }) {
                            FondoDuelo(cartel.local, cartel.visitante, Modifier.matchParentSize())
                            Column(Modifier.fillMaxWidth().padding(28.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    EtiquetaEstado(cartel, ahora, siNoVivo = if (cartel.estado == Estado.POR_JUGAR) "Tu próximo partido · " + Hora.diaYHora(cartel.inicio, ahora) else null)
                                    Spacer(Modifier.weight(1f))
                                    Text(cartel.competicion.corto.uppercase(), color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.6.sp)
                                }
                                Duelo(cartel, 84.dp, modifier = Modifier.padding(top = 26.dp, bottom = 12.dp))
                                val canal = (AjustesFutbol.donde(cartel.liga).firstOrNull { it is com.gonzalo.iptv.futbol.Donde.Ver } as? com.gonzalo.iptv.futbol.Donde.Ver)?.grupo?.boton
                                val donde = listOfNotNull(cartel.estadio.ifBlank { null }, canal?.let { "en $it" }).joinToString(" · ")
                                if (donde.isNotBlank()) Text(donde, color = Color.White.copy(alpha = 0.8f), fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        if (!cartel.estado.terminado && cartel.estado != Estado.CANCELADO) BotonVerCanal(cartel, nav)
                    }
                    else -> Vacio(if (todos == null) "Mirando el calendario de tus equipos…" else "Tus equipos no tienen partidos a la vista")
                }
                Spacer(Modifier.height(30.dp))
                TituloC("Hoy", derecha = deHoy?.let { "${it.size} partidos →" }, alDerecha = { alPagina(1) })
                when {
                    deHoy == null -> Etiqueta("Mirando los partidos de hoy…")
                    deHoy.isEmpty() -> Etiqueta("Hoy no hay partidos en tus competiciones.")
                    else -> deHoy.take(6).forEach { p -> FilaPartido(p, mios, riv) { alFicha(FichaF.DePartido(p)) } }
                }
            }
            Column(Modifier.weight(1f)) {
                if (eqs.isNotEmpty()) {
                    TituloC("La racha de tus equipos")
                    eqs.forEach { (e, liga) ->
                        val suLiga = Competiciones.para(liga).first().slug
                        val tabla = if (Competiciones.tieneTabla(suLiga)) rememberFutbol(suLiga, "tabla") { f -> Futbol.clasificacion(suLiga, f) } else null
                        val puesto = tabla?.tablas?.firstNotNullOfOrNull { (_, f) -> f.firstOrNull { it.equipo.id == e.id }?.puesto }
                        Row(Modifier.fillMaxWidth().filaDe("e:" + e.id).levanta(alto = 1.dp) { alFicha(FichaF.DeEquipo(e, liga)) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Escudo(e, 30.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(e.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(puesto?.let { "${it}º" }, Competiciones.de(suLiga).corto).joinToString(" · ").uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp)
                            }
                            if (todos == null) Text("…", color = Muted) else BloquesForma(formaDe(todos, e.id))
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    TituloC("Tus equipos", derecha = "Todos →", alDerecha = { alPagina(3) })
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        eqs.forEach { (e, liga) ->
                            Column(Modifier.width(88.dp).filaDe("e:" + e.id).levanta { alFicha(FichaF.DeEquipo(e, liga)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Escudo(e, 64.dp)
                                Text(e.corto, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PieFuente() }
    }
}

// ---------------------------------------------------------------- hoy

/** «Hoy»: la fila de días y los partidos de ese día por competición (primero los tuyos, con sus canales dentro de su partido). */
@Composable
fun PaginaHoy(nav: Nav, alFicha: (FichaF) -> Unit) {
    LaFamilia.cambios
    val ahora = System.currentTimeMillis()
    var diaN by remember { mutableLongStateOf(Hora.diaDe(ahora).toEpochDay()) }
    val dia = LocalDate.ofEpochDay(diaN)
    val l = rememberFutbol(diaN) { f -> Futbol.dia(dia, fresco = f) }
    val mios = AjustesFutbol.equipos().map { it.first.id }
    val riv = AjustesFutbol.rivales().map { it.first.id }.toSet()
    val secciones = remember(l, mios) { l?.let { com.gonzalo.iptv.futbol.Dia.agrupa(it, null, mios, AjustesFutbol.competiciones(), AjustesFutbol.ocultas()) } }
    var plegadas by remember { mutableStateOf(setOf<String>()) }
    PaginaF {
        val dias = (-2..2).map { dia.plusDays(it.toLong()) }
        val tira = Hora.tira(ahora, 3, 10)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PildoraDia("‹", false) { diaN-- }
            dias.forEach { d -> PildoraDia(if (d in tira) Hora.nombreDia(d, ahora) else Hora.dia(d.atStartOfDay(Hora.zona).toInstant().toEpochMilli()), d == dia) { diaN = d.toEpochDay() } }
            PildoraDia("›", false) { diaN++ }
            Spacer(Modifier.width(18.dp))
            Text(Hora.largo(dia).uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp)
        }
        Spacer(Modifier.height(10.dp))
        when {
            secciones == null -> Etiqueta("Mirando los partidos…", Modifier.padding(top = 16.dp))
            secciones.isEmpty() -> Etiqueta("Sin partidos este día en tus competiciones (o sin conexión con los datos del fútbol).", Modifier.padding(top = 16.dp))
            else -> {
                // A dos columnas: las competiciones se reparten (cada una entera en su columna)
                val (a, b) = secciones.withIndex().partition { it.index % 2 == 0 }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                    listOf(a, b).forEach { col ->
                        Column(Modifier.weight(1f)) {
                            col.forEach { (_, s) ->
                                val c = s.competicion
                                val plegada = s.clave in plegadas
                                Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f).let { m -> if (c != null) m.filaDe("c:" + c.slug).levanta(alto = 0.dp) { alFicha(FichaF.DeCompeticion(c.slug)) } else m }) {
                                        AntetituloRaya(s.titulo + if (c != null) " →" else "")
                                    }
                                    Text("${s.partidos.size} " + if (plegada) "▸" else "▾", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp,
                                        modifier = Modifier.levanta(alto = 0.dp) { plegadas = if (plegada) plegadas - s.clave else plegadas + s.clave }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                                }
                                if (!plegada) s.partidos.forEach { p ->
                                    FilaPartido(p, mios.toSet(), riv, debajo = {
                                        if (c == null && !p.estado.terminado && p.estado != Estado.CANCELADO) BotonesCanal(p, nav, Modifier.padding(start = 64.dp, bottom = 10.dp))
                                    }) { alFicha(FichaF.DePartido(p)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        PieFuente()
    }
}

// ---------------------------------------------------------------- competiciones y tus equipos

/** «Competiciones»: las de clubes y las de selecciones; una pulsación abre su página (sus partidos, su tabla, sus goleadores). */
@Composable
fun PaginaCompeticiones(alFicha: (FichaF) -> Unit) {
    LaFamilia.cambios
    val todas = AjustesFutbol.competiciones()
    val ocultas = AjustesFutbol.ocultas()
    PaginaF {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
            listOf("De clubes" to todas.filter { !Competiciones.esDeSelecciones(it.slug) }, "De selecciones" to todas.filter { Competiciones.esDeSelecciones(it.slug) }).forEach { (t, l) ->
                Column(Modifier.weight(1f)) {
                    AntetituloRaya(t)
                    l.forEach { c ->
                        Column(Modifier.fillMaxWidth().filaDe("c:" + c.slug).levanta(alto = 1.dp) { alFicha(FichaF.DeCompeticion(c.slug)) }.padding(vertical = 12.dp)) {
                            Text(c.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Text((if (c.slug in ocultas) "Escondida en los partidos del día" else if (Competiciones.tieneTabla(c.slug)) "Partidos · clasificación · goleadores" else "Partidos · goleadores").uppercase(),
                                color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                        Filete(1.dp, Line)
                    }
                }
            }
        }
        PieFuente()
    }
}

/** «Tus equipos»: tus equipos y tus rivales a la izquierda; sus partidos (los que vienen y los jugados) a la derecha. */
@Composable
fun PaginaTusEquipos(nav: Nav, alFicha: (FichaF) -> Unit) {
    LaFamilia.cambios
    val eqs = AjustesFutbol.equipos(); val rivs = AjustesFutbol.rivales()
    val mios = eqs.map { it.first.id }.toSet(); val riv = rivs.map { it.first.id }.toSet()
    val todos = rememberPartidos()
    val ahora = System.currentTimeMillis()
    PaginaF {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
            Column(Modifier.weight(1f)) {
                listOf("Tus equipos" to eqs, "Tus rivales" to rivs).forEach { (t, l) ->
                    AntetituloRaya(t, Modifier.padding(top = 6.dp))
                    if (l.isEmpty()) Etiqueta(if (t == "Tus rivales") "Sin rivales todavía." else "Aún no sigues a ningún equipo (se eligen en Nut del móvil).", Modifier.padding(vertical = 10.dp))
                    l.forEach { (e, liga) ->
                        Row(Modifier.fillMaxWidth().filaDe("e:" + e.id).levanta(alto = 1.dp) { alFicha(FichaF.DeEquipo(e, liga)) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Escudo(e, 36.dp)
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(e.nombre, color = Charcoal, fontFamily = Grotesca, fontStyle = if (t == "Tus rivales") FontStyle.Italic else null, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text((if (Competiciones.esDeSelecciones(liga)) "Selección" else Competiciones.de(liga).nombre).uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp)
                            }
                            if (todos != null) BloquesForma(formaDe(todos, e.id))
                        }
                        Filete(1.dp, Line)
                    }
                    Spacer(Modifier.height(22.dp))
                }
            }
            Column(Modifier.weight(1.3f)) {
                val vienen = todos?.let { l -> l.filter { !it.estado.terminado && (mios + riv).any { m -> it.juega(m) } } }.orEmpty().take(10)
                val jugados = todos?.let { l -> l.filter { it.estado.terminado }.sortedByDescending { it.inicio } }.orEmpty().take(10)
                AntetituloRaya("Lo que viene", Modifier.padding(top = 6.dp))
                if (todos == null) Etiqueta("Mirando los calendarios…", Modifier.padding(vertical = 10.dp))
                vienen.forEach { p -> FilaPartido(p, mios, riv, conDia = true, debajo = { if (mios.any { p.juega(it) } && p.estado != Estado.CANCELADO) BotonesCanal(p, nav, Modifier.padding(start = 96.dp, bottom = 10.dp)) }) { alFicha(FichaF.DePartido(p)) } }
                Spacer(Modifier.height(22.dp))
                AntetituloRaya("Lo último")
                jugados.forEach { p -> FilaPartido(p, mios, riv, conDia = true) { alFicha(FichaF.DePartido(p)) } }
            }
        }
        PieFuente()
    }
}

// ---------------------------------------------------------------- la ficha del partido

/**
 * La ficha de un partido: la apertura con los colores de los dos equipos (el estado, el marcador, quién marcó y las pestañas Resumen ·
 * Alineaciones · Estadísticas); «Ver en …»; y en el resumen, solo goles y rojas (lo demás, en «ver todos los momentos»), las cifras que más
 * dicen, la clasificación y el cara a cara. Mientras se juega, se pone al día sola (cada medio minuto, solo mientras está abierta).
 */
@Composable
fun FichaPartidoC(p0: Partido, nav: Nav, alCerrar: () -> Unit, alFicha: (FichaF) -> Unit) {
    val f by produceState<FichaPartido?>(null, p0.id) {
        value = withContext(Dispatchers.IO) { Futbol.ficha(p0, fresco = false) }
        withContext(Dispatchers.IO) { Futbol.ficha(p0, fresco = true) }?.let { value = it }
        while (Orden.quizaEnDirecto(value?.partido ?: p0, System.currentTimeMillis())) {
            delay(30_000L)
            withContext(Dispatchers.IO) { Futbol.ficha(value?.partido ?: p0, fresco = true) }?.let { value = it }
        }
    }
    val p = f?.partido ?: p0
    val ahora = System.currentTimeMillis()
    var pestana by remember(p0.id) { mutableIntStateOf(0) }
    var todos by remember(p0.id) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(Paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                FondoDuelo(p.local, p.visitante, Modifier.matchParentSize())
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp).padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        EnlaceCabecera("← Volver", Color.White.copy(alpha = 0.92f), atajo = "esc") { alCerrar() }
                        Spacer(Modifier.weight(1f))
                        Text(p.competicion.nombre.uppercase(), color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) { EtiquetaEstado(p, ahora) }
                    Duelo(p, 104.dp, { e -> alFicha(FichaF.DeEquipo(e, p.liga)) }, Modifier.widthIn(max = 1000.dp).align(Alignment.CenterHorizontally).padding(horizontal = 48.dp).padding(top = 24.dp, bottom = 12.dp))
                    val marcaron = f?.let { goleadores(it) }.orEmpty()
                    val linea = marcaron.ifBlank { listOfNotNull(if (p.estado != Estado.POR_JUGAR) Hora.diaYHora(p.inicio, ahora) else null, p.estadio.ifBlank { null }).joinToString(" · ") }
                    if (linea.isNotBlank()) Text(linea, color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp).background(Color.Black.copy(alpha = 0.25f))) {
                        listOf("Resumen", "Alineaciones", "Estadísticas").forEachIndexed { i, t ->
                            val on = i == pestana
                            Box(Modifier.weight(1f).levanta(alto = 0.dp, hundido = 0.98f) { pestana = i }.drawBehind {
                                if (on) drawRect(Color.White, topLeft = Offset(0f, size.height - 3.dp.toPx()), size = Size(size.width, 3.dp.toPx()))
                            }.padding(vertical = 15.dp), contentAlignment = Alignment.Center) {
                                Text(t.uppercase(), color = if (on) Color.White else Color.White.copy(alpha = 0.68f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.2.sp)
                            }
                        }
                    }
                }
            }
            if (!p.estado.terminado && p.estado != Estado.CANCELADO) BotonVerCanal(p, nav)
            Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 48.dp).padding(top = 26.dp, bottom = 48.dp)) {
                val x = f
                when (pestana) {
                    0 -> Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                        Column(Modifier.weight(1f)) {
                            TituloC("Lo importante")
                            when {
                                p.estado == Estado.POR_JUGAR -> Etiqueta("Aún no ha empezado.")
                                x == null -> Etiqueta("Leyendo la ficha…")
                                else -> {
                                    val importantes = x.eventos.filter { it.tipo.esGol || it.tipo == TipoEvento.ROJA || it.tipo == TipoEvento.SEGUNDA_AMARILLA }.reversed()
                                    val lista = if (todos) (if (p.estado.enDirecto) x.eventos.reversed() else x.eventos) else importantes
                                    if (lista.isEmpty()) Etiqueta(if (x.eventos.isEmpty()) "Sin nada que contar todavía." else "Sin goles ni rojas por ahora.")
                                    lista.forEach { FilaEvento(it, p) }
                                    if (x.eventos.size > importantes.size) Text(if (todos) "SOLO LO IMPORTANTE ←" else "VER TODOS LOS MOMENTOS (CAMBIOS Y TARJETAS) →", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black,
                                        fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.levanta(alto = 0.dp) { todos = !todos }.padding(vertical = 12.dp))
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            if (x != null && x.cifras.isNotEmpty()) {
                                TituloC("En números", derecha = "Todas →", alDerecha = { pestana = 2 })
                                val ca = colorDuelo(p.local); val cb = colorDuelo(p.visitante)
                                val claves = listOf("posesi", "tiros a puerta", "tiros")
                                claves.mapNotNull { k -> x.cifras.firstOrNull { it.nombre.lowercase().startsWith(k) } }.distinct().take(3).ifEmpty { x.cifras.take(2) }
                                    .forEach { c -> BarraCifra(c.nombre, c.local, c.visitante, ca, cb) }
                                Spacer(Modifier.height(24.dp))
                            }
                            TituloC("Clasificación")
                            val c = if (Competiciones.tieneTabla(p.liga)) rememberFutbol(p.liga, "tabla") { fr -> Futbol.clasificacion(p.liga, fr) } else null
                            if (c == null) Etiqueta(if (Competiciones.tieneTabla(p.liga)) "Leyendo la clasificación…" else "Esta competición no tiene tabla.")
                            else TablaC(c, AjustesFutbol.equipos().map { it.first.id }.toSet(), setOf(p.local.id, p.visitante.id), cerca = setOf(p.local.id, p.visitante.id)) { e -> alFicha(FichaF.DeEquipo(e, p.liga)) }
                            if (x != null && x.caraACara.isNotEmpty()) {
                                Spacer(Modifier.height(24.dp)); TituloC("Cara a cara")
                                x.caraACara.forEach { q -> FilaPartido(q, emptySet(), emptySet(), conDia = true) { alFicha(FichaF.DePartido(q)) } }
                            }
                        }
                    }
                    1 -> if (x == null || x.alineaciones.isEmpty()) Etiqueta("Las alineaciones salen un rato antes del partido.") else Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                        x.alineaciones.forEach { a ->
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Escudo(a.equipo, 30.dp); Spacer(Modifier.width(10.dp))
                                    Text(a.equipo.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (a.formacion.isNotBlank()) Text(a.formacion, color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                }
                                Filete(2.dp, modifier = Modifier.padding(top = 8.dp))
                                a.titulares.forEach { FilaJugador(it) }
                                if (a.suplentes.isNotEmpty()) { Etiqueta("SUPLENTES", Modifier.padding(top = 14.dp, bottom = 4.dp)); a.suplentes.forEach { FilaJugador(it) } }
                            }
                        }
                    }
                    else -> if (x == null || x.cifras.isEmpty()) Etiqueta("Las cifras salen cuando empieza el partido.") else Column(Modifier.widthIn(max = 760.dp).align(Alignment.CenterHorizontally)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            Text(p.local.corto.uppercase(), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                            Text(p.visitante.corto.uppercase(), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        }
                        Filete(2.dp)
                        val ca = colorDuelo(p.local); val cb = colorDuelo(p.visitante)
                        x.cifras.forEach { c -> BarraCifra(c.nombre, c.local, c.visitante, ca, cb) }
                    }
                }
                PieFuente()
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** Lo que pasa en un partido: un gol, con quién lo marcó en grande y el equipo y quién asistió debajo; lo demás, su frase. */
@Composable
private fun FilaEvento(e: Evento, p: Partido) {
    if (e.tipo.esHito) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(Line))
            Text(e.frase().uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(horizontal = 10.dp))
            Box(Modifier.weight(1f).height(1.dp).background(Line))
        }
        return
    }
    val equipo = if (e.equipo == p.local.id) p.local else if (e.equipo == p.visitante.id) p.visitante else null
    val gol = e.tipo.esGol && e.jugador.isNotBlank()
    val color = when { e.tipo.esGol -> Acento; e.tipo == TipoEvento.AMARILLA -> Color(0xFFE2B007); e.tipo == TipoEvento.ROJA || e.tipo == TipoEvento.SEGUNDA_AMARILLA -> Color(0xFFC62828); else -> Muted }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Text(e.minuto, color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.width(52.dp))
        Box(Modifier.padding(top = 5.dp, end = 12.dp).size(width = if (e.tipo.esTarjeta) 9.dp else 10.dp, height = if (e.tipo.esTarjeta) 13.dp else 10.dp).background(color))
        Column(Modifier.weight(1f)) {
            val como = when (e.tipo) { TipoEvento.GOL_CABEZA -> "de cabeza"; TipoEvento.GOL_FALTA -> "de falta"; TipoEvento.GOL_PENALTI -> "de penalti"; TipoEvento.GOL_PROPIA -> "en propia puerta"; else -> "" }
            Text(if (gol) e.jugador else e.frase().removePrefix("${e.minuto} "), color = Charcoal, fontFamily = Grotesca, fontWeight = if (e.tipo.esGol) FontWeight.ExtraBold else FontWeight.Bold, fontSize = if (gol) 17.sp else 15.sp)
            val sub = listOfNotNull(equipo?.nombre, if (gol && e.otro.isNotBlank()) "asiste ${e.otro}" else null, if (gol) como.ifBlank { null } else null).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub.uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
    Filete(1.dp, Line)
}

@Composable
private fun FilaJugador(j: com.gonzalo.iptv.futbol.Jugador) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(j.dorsal, color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(30.dp).padding(end = 10.dp))
        Text(j.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val marca = when { j.entra -> "↑ entra"; j.sale -> "↓ sale"; else -> "" }
        if (marca.isNotBlank()) Text(marca.uppercase(), color = if (j.entra) Acento else Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 0.8.sp)
    }
    Filete(1.dp, Line)
}

/** Una clasificación: puesto, escudo, equipo, PJ, DG y puntos. [cerca]: solo los de alrededor de esos equipos (en la ficha de un partido). */
@Composable
fun TablaC(c: Clasificacion, mios: Set<String>, resaltar: Set<String>, cerca: Set<String>? = null, alEquipo: (Equipo) -> Unit) {
    c.tablas.forEach { (nombre, filas0) ->
        val filas = if (cerca == null) filas0 else {
            val i = filas0.indexOfFirst { it.equipo.id in cerca }
            if (i < 0) return@forEach
            val j = filas0.indexOfLast { it.equipo.id in cerca }
            filas0.subList(maxOf(0, i - 2), minOf(filas0.size, j + 3))
        }
        if (c.tablas.size > 1 && nombre.isNotBlank()) AntetituloRaya(nombre, Modifier.padding(top = 14.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Etiqueta("#", Modifier.width(32.dp)); Etiqueta("EQUIPO", Modifier.weight(1f))
            listOf("PJ" to 44.dp, "DG" to 56.dp, "PTS" to 52.dp).forEach { (t, w) -> Text(t, color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, textAlign = TextAlign.End, modifier = Modifier.width(w)) }
        }
        Filete(2.dp)
        filas.forEach { f ->
            val mio = f.equipo.id in mios
            Row(Modifier.fillMaxWidth().let { if (f.equipo.id in resaltar) it.background(Sand) else it }.levanta(alto = 0.dp, hundido = 0.99f) { alEquipo(f.equipo) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${f.puesto}", color = if (mio) Acento else Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.width(32.dp))
                Escudo(f.equipo, 24.dp); Spacer(Modifier.width(10.dp))
                Text(f.equipo.nombre, color = if (mio) Acento else Charcoal, fontFamily = Grotesca, fontWeight = if (mio) FontWeight.ExtraBold else FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${f.jugados}", color = Charcoal, fontFamily = Body, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                Text((if (f.diferencia > 0) "+" else "") + f.diferencia, color = Charcoal, fontFamily = Body, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
                Text("${f.puntos}", color = if (mio) Acento else Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
            }
            Filete(1.dp, Line)
        }
    }
}

// ---------------------------------------------------------------- equipo y competición

/** La página de un equipo: su escudo y su nombre sobre su color, su racha, lo que viene y lo último, su clasificación y su plantilla. */
@Composable
fun FichaEquipoC(e: Equipo, liga: String, nav: Nav, alCerrar: () -> Unit, alFicha: (FichaF) -> Unit) {
    LaFamilia.cambios
    val todos = rememberFutbol(e.id, liga) { f -> Futbol.deEquipo(e, liga, f) }
    val suLiga = Competiciones.para(liga).first().slug
    val tabla = if (Competiciones.tieneTabla(suLiga)) rememberFutbol(suLiga, "tabla") { f -> Futbol.clasificacion(suLiga, f) } else null
    val puesto = tabla?.tablas?.firstNotNullOfOrNull { (_, f) -> f.firstOrNull { it.equipo.id == e.id }?.puesto }
    val mios = AjustesFutbol.equipos().map { it.first.id }.toSet(); val riv = AjustesFutbol.rivales().map { it.first.id }.toSet()
    val ahora = System.currentTimeMillis()
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(Paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            val c = colorDuelo(e)
            Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c, Color(0xFF0B0A09))))) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EnlaceCabecera("← Volver", Color.White.copy(alpha = 0.92f), atajo = "esc") { alCerrar() }
                        Spacer(Modifier.weight(1f))
                        val mio = e.id in mios; val esRival = e.id in riv
                        EnlaceCabecera(if (mio) "✓ Tuyo" else "+ Seguir", Color.White) { AjustesFutbol.alterna(e, liga) }
                        Spacer(Modifier.width(22.dp))
                        EnlaceCabecera(if (esRival) "✓ Rival" else "Rival", Color.White.copy(alpha = 0.85f)) { AjustesFutbol.alterna(e, liga, rival = true) }
                    }
                    Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Escudo(e, 120.dp, oscuro = true)
                        Column(Modifier.padding(start = 28.dp)) {
                            Text((if (Competiciones.esDeSelecciones(liga)) "Selección" else Competiciones.de(suLiga).nombre).uppercase() + (puesto?.let { " · ${it}º en la tabla" } ?: "").uppercase(),
                                color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.5.sp)
                            Text(e.nombre, color = Color.White, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp, lineHeight = 58.sp, modifier = Modifier.padding(top = 6.dp))
                            if (todos != null) BloquesForma(formaDe(todos, e.id), Modifier.padding(top = 12.dp), lado = 24.dp)
                        }
                    }
                }
            }
            val prox = todos?.let { Orden.proximos(it, e.id, ahora) }.orEmpty()
            prox.firstOrNull()?.takeIf { !it.estado.terminado }?.let { q -> BotonC(if (q.estado.enDirecto) "Su partido, en directo" else "Su próximo partido: " + Hora.diaYHora(q.inicio, ahora)) { alFicha(FichaF.DePartido(q)) } }
            Row(Modifier.widthIn(max = 1440.dp).fillMaxWidth().padding(horizontal = 48.dp).padding(top = 26.dp, bottom = 48.dp), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                Column(Modifier.weight(1f)) {
                    TituloC("Lo que viene")
                    if (todos == null) Etiqueta("Mirando su calendario…")
                    prox.take(8).forEach { q -> FilaPartido(q, mios, riv, conDia = true) { alFicha(FichaF.DePartido(q)) } }
                    Spacer(Modifier.height(24.dp)); TituloC("Lo último")
                    todos?.let { Orden.jugados(it, e.id) }.orEmpty().take(8).forEach { q -> FilaPartido(q, mios, riv, conDia = true) { alFicha(FichaF.DePartido(q)) } }
                }
                Column(Modifier.weight(1f)) {
                    TituloC("Clasificación")
                    if (tabla == null) Etiqueta(if (Competiciones.tieneTabla(suLiga)) "Leyendo la clasificación…" else "Su competición no tiene tabla.")
                    else TablaC(tabla, mios, setOf(e.id)) { x -> alFicha(FichaF.DeEquipo(x, suLiga)) }
                    val plantilla = rememberFutbol(e.id, "plantilla") { _ -> Futbol.plantilla(e, liga) }
                    if (!plantilla.isNullOrEmpty()) {
                        Spacer(Modifier.height(24.dp)); TituloC("Plantilla")
                        plantilla.forEach { FilaJugador(it) }
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PieFuente(Modifier.padding(bottom = 30.dp)) }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** La página de una competición: sus partidos del día (con su fila de días), su clasificación y sus goleadores. */
@Composable
fun FichaCompeticionC(liga: String, alCerrar: () -> Unit, alFicha: (FichaF) -> Unit) {
    val comp = Competiciones.de(liga)
    val ahora = System.currentTimeMillis()
    var diaN by remember { mutableLongStateOf(Hora.diaDe(ahora).toEpochDay()) }
    val dia = LocalDate.ofEpochDay(diaN)
    val ps = rememberFutbol(liga, diaN) { f -> Futbol.marcadoresDe(liga, dia, f) }
    val tabla = if (Competiciones.tieneTabla(liga)) rememberFutbol(liga, "tabla") { f -> Futbol.clasificacion(liga, f) } else null
    val lideres = rememberFutbol(liga, "lideres") { f -> Futbol.lideres(liga, f) }
    val mios = AjustesFutbol.equipos().map { it.first.id }.toSet(); val riv = AjustesFutbol.rivales().map { it.first.id }.toSet()
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(Paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Column(Modifier.fillMaxWidth().background(Charcoal).padding(horizontal = 48.dp, vertical = 22.dp)) {
                EnlaceCabecera("← Volver", Paper, atajo = "esc") { alCerrar() }
                Text(comp.nombre, color = Paper, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 60.sp, lineHeight = 62.sp, modifier = Modifier.padding(top = 14.dp))
            }
            Row(Modifier.widthIn(max = 1440.dp).fillMaxWidth().padding(horizontal = 48.dp).padding(top = 26.dp, bottom = 48.dp), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PildoraDia("‹", false) { diaN-- }
                        (-1..1).map { dia.plusDays(it.toLong()) }.forEach { d -> PildoraDia(Hora.nombreDia(d, ahora), d == dia) { diaN = d.toEpochDay() } }
                        PildoraDia("›", false) { diaN++ }
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        ps == null -> Etiqueta("Mirando los partidos…")
                        ps.isEmpty() -> Etiqueta("Sin partidos este día.", Modifier.padding(top = 10.dp))
                        else -> ps.forEach { p -> FilaPartido(p, mios, riv) { alFicha(FichaF.DePartido(p)) } }
                    }
                    if (lideres != null && lideres.goles.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp)); TituloC("Goleadores")
                        lideres.goles.take(10).forEachIndexed { i, l ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.width(30.dp))
                                Escudo(l.equipo, 22.dp); Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(l.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    Text(l.equipo.nombre.uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
                                }
                                Text("${l.cifra}", color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            }
                            Filete(1.dp, Line)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    TituloC("Clasificación")
                    if (tabla == null) Etiqueta(if (Competiciones.tieneTabla(liga)) "Leyendo la clasificación…" else "Esta competición no tiene tabla.")
                    else TablaC(tabla, mios, mios) { x -> alFicha(FichaF.DeEquipo(x, liga)) }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PieFuente(Modifier.padding(bottom = 30.dp)) }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
