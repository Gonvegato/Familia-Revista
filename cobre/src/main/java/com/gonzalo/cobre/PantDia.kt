package com.gonzalo.cobre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.iptv.futbol.Competiciones
import com.gonzalo.iptv.futbol.Dia
import com.gonzalo.iptv.futbol.Equipo
import com.gonzalo.iptv.futbol.Estado
import com.gonzalo.iptv.futbol.Hora
import com.gonzalo.iptv.futbol.Jugador
import com.gonzalo.iptv.futbol.Orden
import com.gonzalo.iptv.futbol.Partido
import com.gonzalo.iptv.futbol.Seccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

/*
 * Nut es una app de fútbol que además pone los partidos: la portada son los partidos del día (de tus equipos y de las competiciones que
 * sigues, cada una plegable), un toque abre la ficha con «Dónde verlo». El menú lleva a «Competiciones» (cada una con sus partidos por día,
 * su clasificación y sus goleadores), «Tus equipos» (cada uno con lo suyo) y «Tele» (lo de siempre: directo, favoritos, guía, listas).
 */

// ---------------------------------------------------------------- los datos

/** Los partidos de [dia]: primero lo guardado y luego al día; hoy, con algo en juego, cada minuto mientras se ve. */
@Composable
fun rememberDia(dia: LocalDate): List<Partido>? {
    val ctx = LocalContext.current
    val v = Futbol.version
    EspejoCuenta.version
    val r by produceState<List<Partido>?>(null, dia, v) {
        value = withContext(Dispatchers.IO) { Futbol.dia(ctx, dia, fresco = false) }
        withContext(Dispatchers.IO) { Futbol.dia(ctx, dia, fresco = true) }.let { if (it != value) value = it }
        while (dia == Hora.diaDe(System.currentTimeMillis()) && value.orEmpty().any { Orden.quizaEnDirecto(it, System.currentTimeMillis()) }) {
            delay(60_000L)
            withContext(Dispatchers.IO) { Futbol.dia(ctx, dia, fresco = true, vivoCada = 50_000L) }.let { if (it != value) { value = it; Widgets.pedir(ctx) } }
        }
    }
    return r
}

// ---------------------------------------------------------------- piezas

/** La cabecera de la revista: buscar canales y ajustes, NUT enorme, el número y la fecha, y el menú (la página de ahora, en acento). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Mancheta(aqui: Destino, alIr: (Destino) -> Unit) {
    val ahora = System.currentTimeMillis()
    val hoy = Hora.diaDe(ahora)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        EnlaceRevista("Tele") { alIr(Destino.Tele) }
        EnlaceRevista("Buscar canal", Modifier.padding(start = 14.dp)) { alIr(Destino.Buscar) }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.Settings, "Ajustes", tint = Charcoal, modifier = Modifier.padding(start = 6.dp).tocable { alIr(Destino.Ajustes) }.padding(8.dp).size(22.dp))
    }
    val ancho = minOf(LocalConfiguration.current.screenWidthDp, 1080)
    // (en la tele, más pequeña: lo que importa, los partidos, tiene que verse en la primera pantalla)
    val cfg = LocalConfiguration.current
    val bajita = enTele() || (cfg.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION || cfg.screenHeightDp < 600
    Cabecera(if (bajita) 56.sp else if (ancho >= 700) 112.sp else 84.sp, Modifier.padding(top = if (bajita) 0.dp else 8.dp))
    FileteCabecera(3.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("Nº ${hoy.dayOfYear}", color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, softWrap = false)
        Spacer(Modifier.weight(1f))
        Text(Hora.largo(hoy).uppercase(), color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, softWrap = false)
    }
    Filete(1.dp)
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Filete(1.dp)
        FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            seccionesMenu().forEachIndexed { i, (t, x) ->
                Text(t.uppercase(), color = if (x == aqui) Acento else Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, maxLines = 1,
                    modifier = Modifier.let { if (i == 0) it.focoInicial() else it }.tocable { alIr(x) }.padding(vertical = 9.dp))
            }
        }
        Filete(1.dp)
    }
}

/** La tira de días (el elegido y dos a cada lado, ‹ ›) y «Fecha». El elegido, en acento y subrayado. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TiraDias(dia: LocalDate, alElegir: (LocalDate) -> Unit) {
    val ahora = System.currentTimeMillis()
    val dias = remember(Hora.diaDe(ahora)) { Hora.tira(ahora, 3, 10) }
    var calendario by remember { mutableStateOf(false) }
    // Sin deslizar (una tira que se desliza se veía cortada por el borde): el día elegido con dos a cada lado, ‹ › para moverse
    // de uno en uno y «Fecha» para cualquier otro. Si no caben en una línea (letra grande), siguen en la siguiente, enteros.
    val ventana = (-2..2).map { dia.plusDays(it.toLong()) }
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.Center) {
        EnlaceRevista("‹", Modifier.semantics { contentDescription = "Día anterior" }) { alElegir(dia.minusDays(1)) }
        ventana.forEach { d -> FiltroRevista(if (d in dias) Hora.nombreDia(d, ahora) else Hora.dia(d.atStartOfDay(Hora.ESPANA).toInstant().toEpochMilli()), d == dia) { alElegir(d) } }
        EnlaceRevista("›", Modifier.semantics { contentDescription = "Día siguiente" }) { alElegir(dia.plusDays(1)) }
        EnlaceRevista("Fecha", acento = true) { calendario = true }
    }
    Filete(1.dp, Line)
    if (calendario) {
        val st = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = dia.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        androidx.compose.material3.DatePickerDialog(onDismissRequest = { calendario = false },
            confirmButton = { EnlaceRevista("Ver ese día", Modifier.padding(horizontal = 16.dp), acento = true) {
                st.selectedDateMillis?.let { alElegir(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()) }; calendario = false } },
            dismissButton = { EnlaceRevista("Cancelar", Modifier.padding(horizontal = 16.dp)) { calendario = false } },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = Paper)) {
            androidx.compose.material3.DatePicker(st, colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = Paper, selectedDayContainerColor = Acento, todayDateBorderColor = Acento, todayContentColor = Acento))
        }
    }
}

/**
 * Un partido del día en una línea: a la izquierda la hora (en España) o el minuto, «DESC» o «FINAL»; los dos equipos con sus escudos y
 * sus goles. Los tuyos, en negrita; los rivales, en cursiva (y su marca). Un toque abre la ficha.
 */
@Composable
fun FilaDia(p: Partido, mios: Set<String>, rivales: Set<String>, alAbrir: () -> Unit) {
    val vivo = p.estado.enDirecto
    Row(Modifier.fillMaxWidth().tocable(onClick = alAbrir).semantics { contentDescription = "${p.local.nombre} contra ${p.visitante.nombre}" }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.widthIn(min = 60.dp).padding(end = 8.dp)) {
            Text(Dia.cuando(p), color = if (vivo) Acento else if (p.estado.terminado) Muted else Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, softWrap = false)
            marcaDe(p)?.let { MarcaRival(if (it == "Rival") it else "Tu rival", Modifier.padding(top = 3.dp)) }
        }
        Column(Modifier.weight(1f)) {
            val gl = p.golesLocal; val gv = p.golesVisitante
            LineaEquipo(p.local, gl, 30.dp, 16, gl != null && gv != null && gl > gv && p.estado.terminado, p.local.id in mios, p.local.id in rivales)
            LineaEquipo(p.visitante, gv, 30.dp, 16, gl != null && gv != null && gv > gl && p.estado.terminado, p.visitante.id in mios, p.visitante.id in rivales)
        }
    }
    Filete(1.dp, Line)
}

// ---------------------------------------------------------------- la portada: los partidos del día

/** Lo que se pinta en la portada (para la LazyColumn). */
private sealed class FilaPortada {
    object Cabeza : FilaPortada()
    class Titulo(val s: Seccion, val plegada: Boolean) : FilaPortada()
    class Uno(val p: Partido, val tuyo: Boolean) : FilaPortada()
    class Aviso(val t: String) : FilaPortada()
}

/**
 * La portada: los partidos del día elegido, por competición: primero «Tus equipos» (con los botones de sus canales), luego las
 * competiciones en tu orden (sin las que escondes), cada una plegable; su nombre abre su página. Arriba, la tira de días.
 */
@Composable
fun PantDia(alIr: (Destino) -> Unit, alFicha: (Ficha) -> Unit, enPagina: Boolean = false) {
    val ctx = LocalContext.current
    EspejoCuenta.version; Futbol.version
    var diaN by rememberSaveable { mutableLongStateOf(Hora.diaDe(System.currentTimeMillis()).toEpochDay()) }
    val dia = LocalDate.ofEpochDay(diaN)
    val l = rememberDia(dia)
    val mios = AjustesFutbol.equipos(ctx).map { it.first.id }
    val riv = AjustesFutbol.rivales(ctx).map { it.first.id }.toSet()
    val ocultas = AjustesFutbol.ocultas(ctx)
    var plegadas by remember { mutableStateOf(setOf<String>()) }
    val secciones = remember(l, mios, ocultas, EspejoCuenta.version) { l?.let { Dia.agrupa(it, null, mios, AjustesFutbol.competiciones(ctx), ocultas) } }
    val filas = remember(secciones, plegadas, l) {
        val f = ArrayList<FilaPortada>(); f += FilaPortada.Cabeza
        when {
            secciones == null -> f += FilaPortada.Aviso("Mirando los partidos…")
            secciones.isEmpty() -> f += FilaPortada.Aviso("Sin partidos este día en tus competiciones (o sin conexión con los datos del fútbol).")
            else -> secciones.forEach { s -> val pl = s.clave in plegadas; f += FilaPortada.Titulo(s, pl); if (!pl) s.partidos.forEach { f += FilaPortada.Uno(it, s.competicion == null) } }
        }
        f
    }
    val estado = rememberLazyListState()
    val plegada by remember { derivedStateOf { estado.firstVisibleItemIndex > 0 } }
    val m = margen()
    Box(Modifier.fillMaxSize().background(Paper)) {
        LazyColumn(Modifier.fillMaxSize().let { if (enPagina) it else it.statusBarsPadding() }, state = estado, contentPadding = PaddingValues(horizontal = m, vertical = 0.dp)) {
            items(filas.size, key = { k -> when (val x = filas[k]) { FilaPortada.Cabeza -> "cabeza"; is FilaPortada.Titulo -> "t" + x.s.clave; is FilaPortada.Uno -> "p" + x.p.id + (if (x.tuyo) "t" else ""); is FilaPortada.Aviso -> "a$k" } }) { k ->
                when (val x = filas[k]) {
                    FilaPortada.Cabeza -> Column {
                        if (!enPagina) Mancheta(Destino.Portada, alIr)
                        if (!Cuenta.vinculada) Nota("Sin cuenta todavía: entra en Ajustes → Cuenta (o en Jonsu, y Nut entra sola). Tus equipos, tus canales y lo que ves van con tu cuenta.", Modifier.padding(top = 14.dp))
                        if (mios.isEmpty()) {
                            Nota("Elige tus equipos y sus partidos saldrán los primeros, con sus canales.", Modifier.padding(top = 14.dp))
                            MagLink("Seguir al FC Barcelona", "O elige otros en Ajustes → Fútbol", accent = true) { AjustesFutbol.alterna(ctx, com.gonzalo.iptv.futbol.CanalesFutbol.SUGERIDO, Competiciones.LALIGA.slug) }
                        }
                        Spacer(Modifier.height(14.dp))
                        Antetitulo("Partidos · " + Hora.largo(dia), Modifier.padding(bottom = 2.dp))
                        TiraDias(dia) { diaN = it.toEpochDay() }
                    }
                    is FilaPortada.Titulo -> Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        val c = x.s.competicion
                        Row(Modifier.weight(1f).let { m2 -> if (c != null) m2.tocable { alFicha(Ficha.DeCompeticion(c.slug)) } else m2 }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Antetitulo(x.s.titulo, Modifier.weight(1f, fill = false), color = if (c == null) Acento else Charcoal)
                            if (c != null) Text(" →", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                        Text("${x.s.partidos.size} " + if (x.plegada) "▸" else "▾", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, softWrap = false,
                            modifier = Modifier.semantics { contentDescription = if (x.plegada) "Desplegar ${x.s.titulo}" else "Plegar ${x.s.titulo}" }
                                .tocable { plegadas = if (x.plegada) plegadas - x.s.clave else plegadas + x.s.clave }.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 2.dp))
                    }
                    is FilaPortada.Uno -> Column {
                        if (filas.getOrNull(k - 1) is FilaPortada.Titulo) Filete(2.dp)
                        FilaDia(x.p, mios.toSet(), riv) { alFicha(Ficha.DePartido(x.p)) }
                        // (los tuyos, con sus canales a un toque)
                        if (x.tuyo && !x.p.estado.terminado && x.p.estado != Estado.CANCELADO) BotonesCanal(x.p, { alIr(Destino.AjustesFutbol) }, Modifier.padding(start = 68.dp, top = 2.dp, bottom = 10.dp))
                    }
                    is FilaPortada.Aviso -> Nota(x.t, Modifier.padding(top = 16.dp))
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    PieFuente()
                    Text("Nut · hecho en casa", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                    Text("versión $VERSION", color = Muted.copy(alpha = 0.7f), fontFamily = Body, fontSize = 10.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
                }
            }
        }
        if (!enPagina) BarraPlegada("Nut", plegada) { EnlaceRevista("Tele") { alIr(Destino.Tele) }; EnlaceRevista("Hoy", Modifier.padding(start = 14.dp), acento = true) { diaN = Hora.diaDe(System.currentTimeMillis()).toEpochDay() } }
    }
}

// ---------------------------------------------------------------- Competiciones

/** «Competiciones»: todas las que sigue Nut (las de clubes y las de selecciones); un toque abre su página. */
@Composable
fun PantCompeticiones(alVolver: () -> Unit, alFicha: (Ficha) -> Unit, enPagina: Boolean = false) {
    val ctx = LocalContext.current
    EspejoCuenta.version
    val ocultas = AjustesFutbol.ocultas(ctx)
    val m = margen()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).let { if (enPagina) it else it.statusBarsPadding() }.navigationBarsPadding().padding(horizontal = m)) {
        if (!enPagina) CabeceraSeccion("Competiciones", "Fútbol", alVolver)
        val todas = AjustesFutbol.competiciones(ctx)
        listOf("De clubes" to todas.filter { !Competiciones.esDeSelecciones(it.slug) }, "De selecciones" to todas.filter { Competiciones.esDeSelecciones(it.slug) }).forEach { (t, l) ->
            Antetitulo(t, Modifier.padding(top = 18.dp, bottom = 4.dp)); Filete(1.dp)
            l.forEachIndexed { k, c ->
                FilaRevista(c.nombre, if (c.slug in ocultas) "Escondida en los partidos del día" else if (Competiciones.tieneTabla(c.slug)) "Partidos · clasificación · goleadores" else "Partidos · goleadores",
                    Modifier.let { if (k == 0 && t == "De clubes") it.focoInicial() else it }) { alFicha(Ficha.DeCompeticion(c.slug)) }
            }
        }
        PieFuente()
        Spacer(Modifier.height(30.dp))
    }
}

/** La página de una competición: sus partidos por día (con la tira de días), su clasificación y sus goleadores. */
@Composable
fun PantCompeticion(liga: String, alCerrar: () -> Unit, alFicha: (Ficha) -> Unit) {
    val ctx = LocalContext.current
    val c = Competiciones.de(liga)
    var pestana by rememberSaveable(liga) { mutableIntStateOf(0) }
    var diaN by rememberSaveable(liga) { mutableLongStateOf(Hora.diaDe(System.currentTimeMillis()).toEpochDay()) }
    val dia = LocalDate.ofEpochDay(diaN)
    val mios = AjustesFutbol.equipos(ctx).map { it.first.id }.toSet()
    val riv = AjustesFutbol.rivales(ctx).map { it.first.id }.toSet()
    val m = margen()
    Box(Modifier.fillMaxSize().background(Paper).atrapaFoco()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = m)) {
            RevistaTopBar(alCerrar, "Volver")
            Antetitulo(if (Competiciones.esDeSelecciones(liga)) "Selecciones" else "Competición", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
            FitTitle(c.nombre, 46.sp, 50.sp, letterSpacing = (-1.2).sp, modifier = Modifier.padding(top = 2.dp))
            Filete(3.dp)
            PestanasRevista(if (Competiciones.tieneTabla(liga)) listOf("Partidos", "Clasificación", "Goleadores") else listOf("Partidos", "Goleadores"), pestana) { pestana = it }
            Column(Modifier.widthIn(max = 900.dp)) {
                val tabla = Competiciones.tieneTabla(liga)
                when {
                    pestana == 0 -> {
                        Spacer(Modifier.height(6.dp))
                        TiraDias(dia) { diaN = it.toEpochDay() }
                        val ps = rememberDato(liga, dia) { f -> Futbol.marcadoresDe(ctx, liga, dia, f) }
                        when {
                            ps == null -> Nota("Mirando los partidos…", Modifier.padding(top = 12.dp))
                            ps.isEmpty() -> Nota("Sin partidos de ${c.nombre} el ${Hora.largo(dia).lowercase()}.", Modifier.padding(top = 12.dp))
                            else -> ps.forEach { p -> FilaDia(p, mios, riv) { alFicha(Ficha.DePartido(p)) } }
                        }
                    }
                    pestana == 1 && tabla -> ClasificacionDe(liga)
                    else -> GoleadoresDe(liga)
                }
                PieFuente()
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ---------------------------------------------------------------- un equipo

/**
 * La ficha de un equipo, en doble página como la de un partido: su fondo (o el color del club) con un velo, el escudo con paspartú, el
 * nombre enorme y su puesto; debajo, por secciones numeradas, su racha (los cinco últimos y los jugados), lo que viene, la plantilla y la
 * clasificación de su liga.
 */
@Composable
fun PantEquipo(e: Equipo, liga: String, alCerrar: () -> Unit, alFicha: (Ficha) -> Unit, alImagenes: (Equipo) -> Unit = {}) {
    val ctx = LocalContext.current
    EspejoCuenta.version
    val todos = rememberDato(e.id, liga) { f -> Futbol.deEquipo(ctx, e, liga, f) }
    val mios = AjustesFutbol.equipos(ctx).map { it.first.id }.toSet()
    val riv = AjustesFutbol.rivales(ctx).map { it.first.id }.toSet()
    val ahora = System.currentTimeMillis()
    val suLiga = Competiciones.para(liga).first().slug
    val conTabla = Competiciones.tieneTabla(suLiga)
    val tabla = if (conTabla) rememberDato(suLiga, "tabla") { f -> Futbol.clasificacion(ctx, suLiga, f) } else null
    val puesto = tabla?.let { FormaEquipo.puesto(it, e.id) }
    val m = margen()
    Box(Modifier.fillMaxSize().background(Paper).atrapaFoco()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                FondoEquipo(e, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)))
                Box(Modifier.matchParentSize().background(veloArriba()))
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = m).padding(bottom = 22.dp)) {
                    val blanco = Color.White.copy(alpha = 0.85f)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        EnlaceRevista("← Volver", color = blanco) { alCerrar() }
                        Spacer(Modifier.weight(1f))
                        val mio = e.id in mios; val esRival = e.id in riv
                        EnlaceRevista(if (mio) "✓ Tuyo" else "+ Seguir", color = if (mio) blanco else acentoSobreTinta(Color.Black)) { AjustesFutbol.alterna(ctx, e, liga) }
                        EnlaceRevista(if (esRival) "✓ Rival" else "Rival", Modifier.padding(start = 14.dp), color = blanco) { AjustesFutbol.alterna(ctx, e, liga, rival = true) }
                        EnlaceRevista("Imágenes", Modifier.padding(start = 14.dp), color = blanco) { alImagenes(e) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        EscudoConPaspartu(e, if (esAncha()) 140.dp else 112.dp)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text((if (Competiciones.esDeSelecciones(liga)) "Selección" else Competiciones.de(suLiga).nombre).uppercase(), color = blanco, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, maxLines = 2)
                            if (puesto != null) EtiquetaBloque("${puesto}º en la tabla", Modifier.padding(top = 8.dp))
                        }
                    }
                    FitTitle(e.nombre, 46.sp, 50.sp, color = Color.White, family = Grotesca, fontWeight = FontWeight.ExtraBold, maxLines = 2, minSize = 22f, letterSpacing = 0.sp, modifier = Modifier.padding(top = 14.dp))
                }
            }
            val prox = todos?.let { Orden.proximos(it, e.id, ahora) }.orEmpty()
            prox.firstOrNull()?.takeIf { !it.estado.terminado }?.let { BotonC(if (it.estado.enDirecto) "Su partido, en directo" else "Su próximo partido", fondo = AcentoBloque, letra = SobreBloque) { alFicha(Ficha.DePartido(it)) } }
            Column(Modifier.padding(horizontal = m).widthIn(max = 900.dp)) {
                val jugados = todos?.let { Orden.jugados(it, e.id) }
                SeccionNumerada(1, "Su racha")
                when {
                    jugados == null -> Nota("Mirando el calendario…")
                    jugados.isEmpty() -> Nota("Sin partidos jugados todavía.")
                    else -> {
                        BloquesForma(FormaEquipo.de(jugados, e.id), Modifier.padding(bottom = 8.dp), grande = true)
                        jugados.take(8).forEach { p -> FilaPartido(p, ahora, mios, riv) { alFicha(Ficha.DePartido(p)) } }
                    }
                }
                SeccionNumerada(2, "Lo que viene")
                when {
                    todos == null -> Nota("Mirando el calendario…")
                    prox.isEmpty() -> Nota("Sin partidos a la vista.")
                    else -> prox.take(10).forEach { p -> FilaPartido(p, ahora, mios, riv) { alFicha(Ficha.DePartido(p)) } }
                }
                SeccionNumerada(3, "Plantilla")
                Plantilla(e, liga)
                if (conTabla) { SeccionNumerada(4, "Clasificación"); ClasificacionDe(suLiga, setOf(e.id)) }
                PieFuente()
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun Plantilla(e: Equipo, liga: String) {
    val ctx = LocalContext.current
    val l: List<Jugador>? = rememberDato(e.id, "plantilla") { _ -> Futbol.plantilla(ctx, e, liga) }
    if (l == null) { Nota("Leyendo la plantilla…", Modifier.padding(top = 12.dp)); return }
    if (l.isEmpty()) { Nota("Sin plantilla en los datos del fútbol.", Modifier.padding(top = 12.dp)); return }
    listOf("POR" to "Porteros", "DEF" to "Defensas", "MED" to "Centrocampistas", "DEL" to "Delanteros", "" to "Otros").forEach { (k, t) ->
        val js = l.filter { it.posicion == k }
        if (js.isEmpty()) return@forEach
        Antetitulo(t, Modifier.padding(top = 18.dp, bottom = 4.dp)); Filete(1.dp)
        js.forEach { j ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(j.dorsal, color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 34.dp))
                Text(j.nombre, color = Charcoal, fontFamily = Display, fontSize = 17.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Filete(1.dp, Line)
        }
    }
}
