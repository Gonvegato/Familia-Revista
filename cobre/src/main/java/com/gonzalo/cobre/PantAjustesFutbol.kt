package com.gonzalo.cobre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.futbol.Calidad
import com.gonzalo.iptv.futbol.CanalesFutbol
import com.gonzalo.iptv.futbol.Competiciones
import com.gonzalo.iptv.futbol.GrupoCanales
import com.gonzalo.iptv.futbol.RefCanal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Para qué se busca un canal: una entrada nueva del grupo, o una calidad de una entrada que ya está. */
private data class Busqueda(val grupo: String, val entrada: Int = -1, val calidad: Calidad? = null)

/**
 * Ajustes → Fútbol: tus equipos (y añadir otros, de la lista de cada competición), tus grupos de canales (cada entrada, en Ultra HD, HD y
 * SD), qué grupos tiene cada competición y la calidad (automática o la que elijas). Todo viaja con tu cuenta, sin nada secreto.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeccionFutbol() {
    val ctx = LocalContext.current
    var v by remember { mutableIntStateOf(0) }
    v; EspejoCuenta.version
    fun cambia(f: () -> Unit) { f(); v++ }
    val grupos = AjustesFutbol.grupos(ctx)

    // ------------------------------------------------ tus equipos y tus rivales
    ListaEquipos(rival = false) { v++ }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    ListaEquipos(rival = true) { v++ }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))

    // ------------------------------------------------ las competiciones de los partidos del día
    Antetitulo("Las competiciones del día", Modifier.padding(bottom = 4.dp))
    Text("En este orden salen en la portada (después de tus equipos). Las que escondes no se piden ni salen, salvo los partidos de los tuyos.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    val ocultas = AjustesFutbol.ocultas(ctx)
    AjustesFutbol.competiciones(ctx).forEachIndexed { i, c ->
        val oculta = c.slug in ocultas
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.nombre, color = if (oculta) Muted else Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (i > 0) EnlaceRevista("↑", Modifier.padding(horizontal = 8.dp)) { cambia { AjustesFutbol.mueveCompeticion(ctx, c.slug, -1) } }
            Interruptor(!oculta) { cambia { AjustesFutbol.alternaOculta(ctx, c.slug) } }
        }
        Filete(1.dp, Line)
    }

    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))

    // ------------------------------------------------ la calidad
    Antetitulo("La calidad", Modifier.padding(bottom = 4.dp))
    val ops = listOf<Calidad?>(null) + Calidad.entries
    MagChoice(ops.map { it?.texto ?: "Automática" }, ops.indexOf(AjustesFutbol.calidad(ctx)).coerceAtLeast(0), "Al poner un partido") { k -> cambia { AjustesFutbol.ponCalidad(ctx, ops[k]) } }
    Text("Automática: con wifi o cable rápido (25 Mb/s o más), Ultra HD; con datos del móvil, una red con límite o menos de 25 Mb/s, HD; con menos de 3 Mb/s o el ahorro de datos encendido, SD. Si no está la que toca, la siguiente que haya; y si un canal no arranca, otra calidad y luego el canal siguiente del grupo. En el reproductor se cambia con «Calidad».",
        color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)

    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))

    // ------------------------------------------------ los grupos de canales
    Antetitulo("Tus canales del fútbol", Modifier.padding(bottom = 4.dp))
    Text("En cada partido, un botón por grupo: un toque y se pone el primero; los demás son el 2, el 3… Cada canal, en hasta tres calidades (al elegir uno, las otras se buscan solas en tu lista).",
        color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    var busqueda by remember { mutableStateOf<Busqueda?>(null) }
    var renombrando by remember { mutableStateOf<String?>(null) }
    grupos.forEach { g ->
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(g.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            EnlaceRevista("Nombre", Modifier.padding(start = 8.dp)) { renombrando = if (renombrando == g.id) null else g.id }
            EnlaceRevista("Borrar", Modifier.padding(start = 12.dp)) { cambia { AjustesFutbol.ponGrupos(ctx, grupos.filter { it.id != g.id }) } }
        }
        Filete(2.dp)
        if (renombrando == g.id) {
            var nombre by remember(g.id) { mutableStateOf(g.nombre) }
            CampoRevista("Nombre", nombre, "Movistar LaLiga", teclado = KeyboardType.Text) { nombre = it }
            EnlaceRevista("Guardar", acento = true) { if (nombre.isNotBlank()) cambia { AjustesFutbol.cambiaGrupo(ctx, g.id) { it.copy(nombre = nombre.trim()) } }; renombrando = null }
        }
        if (g.canales.isEmpty()) Nota("Vacío: elige sus canales de tu lista.", Modifier.padding(top = 6.dp))
        g.canales.forEachIndexed { i, e ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 26.dp))
                Text(e.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (i > 0) EnlaceRevista("↑", Modifier.padding(horizontal = 6.dp)) { cambia { AjustesFutbol.cambiaGrupo(ctx, g.id) { CanalesFutbol.mueve(it, i, -1) } } }
                EnlaceRevista("Quitar", Modifier.padding(start = 6.dp)) { cambia { AjustesFutbol.cambiaGrupo(ctx, g.id) { CanalesFutbol.quita(it, i) } } }
            }
            Calidad.entries.forEach { q ->
                val r = e.en(q)
                Row(Modifier.fillMaxWidth().padding(start = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(q.texto.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 70.dp))
                    if (r != null) {
                        Text(r.nombre, color = Charcoal, fontFamily = Body, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).tocable { busqueda = Busqueda(g.id, i, q) }.padding(vertical = 6.dp))
                        EnlaceRevista("✕", Modifier.padding(start = 6.dp)) { cambia { AjustesFutbol.cambiaGrupo(ctx, g.id) { CanalesFutbol.pon(it, i, q, null) } } }
                    } else Text("Elige el canal de ${q.name} de tu lista →", color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 18.sp,
                        modifier = Modifier.weight(1f).tocable { busqueda = Busqueda(g.id, i, q) }.padding(vertical = 6.dp))
                }
            }
            Filete(1.dp, Line)
        }
        if (g.canales.isEmpty()) BotonSuave("Elegir canal", modifier = Modifier.padding(top = 8.dp)) { busqueda = Busqueda(g.id) }
        else EnlaceRevista("+ Elegir otro canal", Modifier.padding(top = 4.dp), acento = true) { busqueda = Busqueda(g.id) }
    }
    // El selector de canales, encima de todo (se navega: favoritos y categorías de tus listas; buscar es solo un atajo)
    busqueda?.let { aqui ->
        val g = grupos.firstOrNull { it.id == aqui.grupo }
        if (g != null) SelectorCanal(
            titulo = if (aqui.calidad != null) "El canal de ${aqui.calidad.name}" else "Un canal para «${g.nombre}»",
            sub = if (aqui.calidad != null) "${g.nombre} · ${g.canales.getOrNull(aqui.entrada)?.nombre.orEmpty()}" else "Sus otras calidades se buscan solas",
            alCerrar = { busqueda = null },
        ) { l, c, todos ->
            val r = RefCanal(l.id, c.clave, c.nombre)
            cambia {
                AjustesFutbol.cambiaGrupo(ctx, g.id) { x ->
                    if (aqui.calidad != null) CanalesFutbol.pon(x, aqui.entrada, aqui.calidad, r)
                    else CanalesFutbol.anade(x, CanalesFutbol.entradaCon(r, todos.map { RefCanal(l.id, it.clave, it.nombre) }))
                }
            }
            busqueda = null
        }
    }
    var nuevo by remember { mutableStateOf("") }
    Spacer(Modifier.height(10.dp))
    CampoRevista("Grupo nuevo", nuevo, "M+ Liga de Campeones", teclado = KeyboardType.Text) { nuevo = it }
    EnlaceRevista("+ Crear el grupo", acento = true) {
        val n = nuevo.trim()
        if (n.isNotBlank()) cambia { AjustesFutbol.ponGrupos(ctx, grupos + GrupoCanales(CanalesFutbol.idNuevo(n, grupos.map { it.id }), n)); nuevo = "" }
    }

    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))

    // ------------------------------------------------ qué grupos tiene cada competición
    Antetitulo("Los canales de cada competición", Modifier.padding(bottom = 4.dp))
    Text("Toca un grupo para ponerlo o quitarlo. Los botones salen en este orden.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    val asig = AjustesFutbol.asignacion(ctx)
    Competiciones.TODAS.forEach { comp ->
        val suyos = asig[comp.slug].orEmpty()
        Text(comp.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            grupos.forEach { g -> val k = suyos.indexOf(g.id); FiltroRevista(if (k >= 0) "${k + 1}. ${g.nombre}" else g.nombre, k >= 0) { cambia { AjustesFutbol.alternaAsignado(ctx, comp.slug, g.id) } } }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("Los partidos, las clasificaciones y los goleadores salen de ESPN (su servicio público, sin cuenta). Tus equipos y tus grupos van con tu cuenta: de cada canal, solo su nombre y su clave, nunca la dirección de tu lista ni tu contraseña.",
        color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp)
}

/** «Tus equipos» o «Tus rivales»: la lista (subir, quitar) y «+ Añadir» con los de cada competición (de clubes o de selecciones). */
@Composable
private fun ListaEquipos(rival: Boolean, alCambiar: () -> Unit) {
    val ctx = LocalContext.current
    var v by remember { mutableIntStateOf(0) }
    v; EspejoCuenta.version
    fun cambia(f: () -> Unit) { f(); v++; alCambiar() }
    val l = if (rival) AjustesFutbol.rivales(ctx) else AjustesFutbol.equipos(ctx)
    Antetitulo(if (rival) "Tus rivales" else "Tus equipos", Modifier.padding(bottom = 4.dp))
    if (l.isEmpty()) {
        if (rival) Text("Los equipos que quieres ver perder: sus partidos salen también (en la portada, solo si juegan hoy), marcados como «Rival».", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
        else {
            Text("Aún no sigues a ninguno. Sus partidos salen arriba de la portada, con sus canales.", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
            MagLink("FC Barcelona", "Sugerido: un toque y es tuyo", accent = true) { cambia { AjustesFutbol.alterna(ctx, CanalesFutbol.SUGERIDO, Competiciones.LALIGA.slug) } }
        }
    }
    l.forEachIndexed { i, (e, liga) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Escudo(e, 30.dp); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.nombre, color = Charcoal, fontFamily = Grotesca, fontStyle = if (rival) FontStyle.Italic else null, fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Etiqueta(if (Competiciones.esDeSelecciones(liga)) "Selección" else Competiciones.de(liga).nombre)
            }
            if (i > 0) EnlaceRevista("↑", Modifier.padding(horizontal = 6.dp)) { cambia { AjustesFutbol.mueve(ctx, e.id, -1, rival) } }
            EnlaceRevista("Quitar", Modifier.padding(start = 6.dp)) { cambia { AjustesFutbol.alterna(ctx, e, liga, rival) } }
        }
        Filete(1.dp, Line)
    }
    var anadir by rememberSaveable(rival) { mutableStateOf(false) }
    EnlaceRevista(if (anadir) "Cerrar la lista de equipos" else if (rival) "+ Añadir un rival" else "+ Añadir un equipo", Modifier.padding(top = 4.dp), acento = true) { anadir = !anadir }
    if (anadir) ElegirEquipo(rival) { v++; alCambiar() }
}

/** De dónde se eligen: las ligas y, aparte, las selecciones (las de Europa, las del Mundial o todas). */
private val DE_CLUBES = Competiciones.CLUBES.filter { it != Competiciones.SUPERCOPA }.map { it.corto to it.slug }
private val DE_SELECCIONES = listOf("Europa" to Competiciones.NACIONES.slug, "Mundial" to Competiciones.MUNDIAL.slug, "Todas" to Competiciones.AMISTOSOS.slug)

/** Elegir equipos: las competiciones arriba (ligas y selecciones) y sus equipos (de ESPN), con los tuyos y tus rivales marcados. */
@Composable
private fun ElegirEquipo(rival: Boolean, alCambiar: () -> Unit) {
    val ctx = LocalContext.current
    var sel by rememberSaveable { mutableStateOf(false) }
    var i by rememberSaveable { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    var v by remember { mutableIntStateOf(0) }
    v
    Spacer(Modifier.height(6.dp))
    Etiqueta("Clubes")
    FiltrosRevista(DE_CLUBES.map { it.first }, if (sel) -1 else i) { sel = false; i = it }
    Etiqueta("Selecciones", Modifier.padding(top = 4.dp))
    FiltrosRevista(DE_SELECCIONES.map { it.first }, if (sel) i else -1) { sel = true; i = it }
    val liga = (if (sel) DE_SELECCIONES else DE_CLUBES).getOrElse(i) { DE_CLUBES[0] }.second
    val lista by produceState<List<com.gonzalo.iptv.futbol.Equipo>?>(null, liga) { value = null; value = withContext(Dispatchers.IO) { Futbol.equipos(ctx, liga) }.orEmpty() }
    val l = lista
    when {
        l == null -> Nota("Leyendo los equipos…")
        l.isEmpty() -> Nota("No se han podido leer los equipos (¿sin conexión?).")
        else -> {
            if (l.size > 30) CampoRevista("Filtrar", q, "España, Portugal…", teclado = KeyboardType.Text) { q = it }
            val n = com.gonzalo.iptv.Buscar.normal(q)
            l.filter { n.isBlank() || com.gonzalo.iptv.Buscar.normal(it.nombre).contains(n) }.forEach { e ->
                val mio = AjustesFutbol.esMio(ctx, e.id); val suRival = AjustesFutbol.esRival(ctx, e.id)
                val marcado = if (rival) suRival else mio
                Row(Modifier.fillMaxWidth().tocable { AjustesFutbol.alterna(ctx, e, liga, rival); v++; alCambiar() }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Escudo(e, 24.dp); Spacer(Modifier.width(10.dp))
                    Text(e.nombre, color = if (mio) Acento else Charcoal, fontFamily = Grotesca, fontStyle = if (suRival) FontStyle.Italic else null, fontWeight = if (mio || suRival) FontWeight.Bold else FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val t = when { marcado && rival -> "✓ RIVAL"; marcado -> "✓ TUYO"; rival && mio -> "TUYO · HACER RIVAL"; !rival && suRival -> "RIVAL · HACER TUYO"; rival -> "+ RIVAL"; else -> "+ SEGUIR" }
                    Text(t, color = if (marcado && !rival) Acento else if (marcado) Charcoal else Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.padding(start = 8.dp).widthIn(max = 130.dp))
                }
                Filete(1.dp, Line)
            }
        }
    }
}

/** Una fila del selector: qué hay que pintar (lo hace [SelectorCanal] con una LazyColumn, perezosa: listas de miles de canales). */
private sealed class FilaSel {
    class Titulo(val t: String) : FilaSel()
    class Grupo(val lista: Lista, val grupo: String, val n: Int, val abierto: Boolean) : FilaSel()
    class Canal1(val lista: Lista, val c: Canal, val i: Int) : FilaSel()
    class Aviso(val t: String) : FilaSel()
}

/**
 * Elegir un canal de tus listas sin escribir: a pantalla completa, primero «Tus favoritos» (los de la ★, en tu orden) y luego las
 * categorías de cada lista (como en «Directo», sin las que escondes), que se abren con un toque y enseñan sus canales con su número, su
 * logo y su nombre. Arriba, buscar por el nombre es solo un atajo. Un toque elige: [alElegir] con la lista, el canal y los demás de su
 * lista (para encontrar sus otras calidades por el nombre).
 */
@Composable
fun SelectorCanal(titulo: String, sub: String, alCerrar: () -> Unit, alElegir: (Lista, Canal, List<Canal>) -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = alCerrar, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CobreTheme { SelectorCanalContenido(titulo, sub, alCerrar, alElegir) }
    }
}

@Composable
fun SelectorCanalContenido(titulo: String, sub: String, alCerrar: () -> Unit, alElegir: (Lista, Canal, List<Canal>) -> Unit) {
    val ctx = LocalContext.current
    val (favs, cargadas) = rememberFavoritosVivos()
    val usables = Listas.usables()
    var q by remember { mutableStateOf("") }
    var abiertos by remember { mutableStateOf(setOf<String>()) }
    val r by produceState<List<Pair<Lista, Canal>>?>(null, q) {
        if (q.trim().length < 2) { value = null; return@produceState }
        delay(250)
        value = withContext(Dispatchers.IO) { Futbol.buscaCanales(q, tope = 200) }
    }
    // Las categorías de cada lista y cuántos canales tienen (una vez por lista, fuera del hilo principal)
    val grupos by produceState<Map<String, List<Pair<String, Int>>>>(emptyMap(), cargadas, EspejoCuenta.version) {
        value = withContext(Dispatchers.Default) {
            cargadas.mapNotNull { (id, cg) -> cg?.let { c ->
                val ocultos = MiCuenta.ocultos(id)
                val cuenta = c.canales.groupingBy { it.grupo }.eachCount()
                id to c.todosLosGrupos.filter { it !in ocultos && (cuenta[it] ?: 0) > 0 }.map { it to (cuenta[it] ?: 0) }
            } }.toMap()
        }
    }
    val filas = remember(q, r, favs, grupos, abiertos, cargadas) {
        val l = ArrayList<FilaSel>()
        val x = r
        if (q.trim().length >= 2) {
            if (x == null) l += FilaSel.Aviso("Buscando…") else if (x.isEmpty()) l += FilaSel.Aviso("Nada con «$q» en tus listas.")
            else x.forEachIndexed { i, (li, c) -> l += FilaSel.Canal1(li, c, i) }
        } else {
            if (favs.isNotEmpty()) { l += FilaSel.Titulo("Tus favoritos"); favs.forEachIndexed { i, (li, c, _) -> l += FilaSel.Canal1(li, c, i) } }
            if (usables.isEmpty()) l += FilaSel.Aviso("Aún no tienes listas en este dispositivo.")
            usables.forEach { li ->
                val gs = grupos[li.id]
                l += FilaSel.Titulo(if (usables.size > 1) li.nombre else "Las categorías de tu lista")
                if (gs == null) l += FilaSel.Aviso(if (cargadas.containsKey(li.id) && cargadas[li.id] == null) "Esta lista aún no tiene canales." else "Leyendo los canales…")
                else gs.forEach { (g, n) ->
                    val clave = li.id + "\u0000" + g
                    val abierto = clave in abiertos
                    l += FilaSel.Grupo(li, g, n, abierto)
                    if (abierto) cargadas[li.id]?.let { cg -> Tele.visibles(cg, ctx, g).forEachIndexed { i, c -> l += FilaSel.Canal1(li, c, i) } }
                }
            }
        }
        l
    }
    val m = margen()
    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = m)) {
        RevistaTopBar(alCerrar, "Cancelar")
        Antetitulo("Elegir canal", Modifier.padding(top = 8.dp), color = Acento, conCuadro = false)
        Text(titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (sub.isNotBlank()) Etiqueta(sub, Modifier.padding(top = 2.dp))
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)) {
            if (q.isEmpty()) Text("Buscar (si quieres): DAZN, M+…", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp), cursorBrush = SolidColor(Acento), modifier = Modifier.fillMaxWidth())
        }
        Filete(2.dp)
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
            items(filas.size, key = { k -> when (val f = filas[k]) { is FilaSel.Titulo -> "t$k"; is FilaSel.Aviso -> "a$k"; is FilaSel.Grupo -> "g${f.lista.id}\u0000${f.grupo}"; is FilaSel.Canal1 -> "c$k${f.lista.id}${f.c.clave}" } }) { k ->
                when (val f = filas[k]) {
                    is FilaSel.Titulo -> Antetitulo(f.t, Modifier.padding(top = 18.dp, bottom = 4.dp))
                    is FilaSel.Aviso -> Nota(f.t, Modifier.padding(vertical = 10.dp))
                    is FilaSel.Grupo -> {
                        Row(Modifier.fillMaxWidth().let { if (k == 0 || filas.getOrNull(k - 1) is FilaSel.Titulo) it.focoInicial(f.grupo) else it }
                            .tocable { val c = f.lista.id + "\u0000" + f.grupo; abiertos = if (c in abiertos) abiertos - c else abiertos + c }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (f.abierto) "▾" else "▸", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.widthIn(min = 22.dp))
                            Text(nombreGrupo(f.grupo), color = if (f.abierto) Acento else Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(Formato.cifra(f.n), color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
                        }
                        Filete(1.dp, Line)
                    }
                    is FilaSel.Canal1 -> FilaElegir(f.c, f.i) {
                        val todos = cargadas[f.lista.id]?.canales ?: Tele.enMemoria(f.lista.id)?.canales ?: listOf(f.c)
                        alElegir(f.lista, f.c, todos)
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

/** Un canal para elegir: su número (entero, en una línea), su logo, su nombre (hasta tres líneas: con letra grande se lee entero) y su calidad. */
@Composable
private fun FilaElegir(c: Canal, i: Int, alElegir: () -> Unit) {
    Row(Modifier.fillMaxWidth().tocable(onClick = alElegir).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${if (c.numero > 0) c.numero else i + 1}", color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 34.dp).padding(end = 8.dp))
        Logo(c.logo, c.nombre, Modifier.padding(end = 12.dp), 40.dp)
        Text(c.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(Calidades_texto(c.nombre), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
    }
    Filete(1.dp, Line)
}

/** La calidad que dice el nombre («UHD», «HD», «SD»), solo si la dice. */
private fun Calidades_texto(n: String) = if (com.gonzalo.iptv.futbol.Calidades.base(n) == n.trim()) "" else com.gonzalo.iptv.futbol.Calidades.deNombre(n).name
