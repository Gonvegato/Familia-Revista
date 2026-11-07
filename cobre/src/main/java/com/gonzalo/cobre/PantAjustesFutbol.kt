package com.gonzalo.cobre

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
    val equipos = AjustesFutbol.equipos(ctx)
    val grupos = AjustesFutbol.grupos(ctx)

    // ------------------------------------------------ tus equipos
    Antetitulo("Tus equipos", Modifier.padding(bottom = 4.dp))
    if (equipos.isEmpty()) {
        Text("Aún no sigues a ninguno. Sus partidos salen arriba de la portada, con sus canales.", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
        MagLink("FC Barcelona", "Sugerido: un toque y es tuyo", accent = true) { cambia { AjustesFutbol.alterna(ctx, CanalesFutbol.SUGERIDO, Competiciones.LALIGA.slug) } }
    }
    equipos.forEachIndexed { i, (e, liga) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Escudo(e, 30.dp); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Etiqueta(Competiciones.de(liga).nombre)
            }
            if (i > 0) EnlaceRevista("↑", Modifier.padding(horizontal = 6.dp)) { cambia { AjustesFutbol.mueve(ctx, e.id, -1) } }
            EnlaceRevista("Quitar", Modifier.padding(start = 6.dp)) { cambia { AjustesFutbol.alterna(ctx, e, liga) } }
        }
        Filete(1.dp, Line)
    }
    var anadir by rememberSaveable { mutableStateOf(false) }
    EnlaceRevista(if (anadir) "Cerrar la lista de equipos" else "+ Añadir un equipo", Modifier.padding(top = 4.dp), acento = true) { anadir = !anadir }
    if (anadir) ElegirEquipo { v++ }

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
            Text(g.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            EnlaceRevista("Nombre", Modifier.padding(start = 8.dp)) { renombrando = if (renombrando == g.id) null else g.id }
            EnlaceRevista("Borrar", Modifier.padding(start = 12.dp)) { cambia { AjustesFutbol.ponGrupos(ctx, grupos.filter { it.id != g.id }) } }
        }
        Filete(2.dp)
        if (renombrando == g.id) {
            var nombre by remember(g.id) { mutableStateOf(g.nombre) }
            CampoRevista("Nombre", nombre, "Movistar LaLiga", teclado = KeyboardType.Text) { nombre = it }
            EnlaceRevista("Guardar", acento = true) { if (nombre.isNotBlank()) cambia { AjustesFutbol.cambiaGrupo(ctx, g.id) { it.copy(nombre = nombre.trim()) } }; renombrando = null }
        }
        if (g.canales.isEmpty()) Nota("Vacío: añade sus canales.", Modifier.padding(top = 6.dp))
        g.canales.forEachIndexed { i, e ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 26.dp))
                Text(e.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (i > 0) EnlaceRevista("↑", Modifier.padding(horizontal = 6.dp)) { cambia { AjustesFutbol.cambiaGrupo(ctx, g.id) { CanalesFutbol.mueve(it, i, -1) } } }
                EnlaceRevista("Quitar", Modifier.padding(start = 6.dp)) { cambia { AjustesFutbol.cambiaGrupo(ctx, g.id) { CanalesFutbol.quita(it, i) } } }
            }
            Calidad.entries.forEach { q ->
                val r = e.en(q)
                Row(Modifier.fillMaxWidth().padding(start = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(q.texto.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 70.dp))
                    Text(r?.nombre ?: "—", color = if (r == null) Muted else Charcoal, fontFamily = Body, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (r != null) EnlaceRevista("✕", Modifier.padding(start = 6.dp)) { cambia { AjustesFutbol.cambiaGrupo(ctx, g.id) { CanalesFutbol.pon(it, i, q, null) } } }
                    else EnlaceRevista("Elegir", Modifier.padding(start = 6.dp)) { busqueda = Busqueda(g.id, i, q) }
                }
            }
            Filete(1.dp, Line)
        }
        val aqui = busqueda?.takeIf { it.grupo == g.id }
        if (aqui != null) {
            Etiqueta(if (aqui.calidad != null) "Elige el canal en ${aqui.calidad.texto} de la entrada ${aqui.entrada + 1}" else "Elige un canal para «${g.nombre}»", Modifier.padding(top = 10.dp))
            BuscadorCanales(alCerrar = { busqueda = null }) { l, c, todos ->
                val r = RefCanal(l.id, c.clave, c.nombre)
                cambia {
                    AjustesFutbol.cambiaGrupo(ctx, g.id) { x ->
                        if (aqui.calidad != null) CanalesFutbol.pon(x, aqui.entrada, aqui.calidad, r)
                        else CanalesFutbol.anade(x, CanalesFutbol.entradaCon(r, todos.map { RefCanal(l.id, it.clave, it.nombre) }))
                    }
                }
                busqueda = null
            }
        } else EnlaceRevista("+ Añadir un canal", Modifier.padding(top = 4.dp), acento = true) { busqueda = Busqueda(g.id) }
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
        Text(comp.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            grupos.forEach { g -> val k = suyos.indexOf(g.id); FiltroRevista(if (k >= 0) "${k + 1}. ${g.nombre}" else g.nombre, k >= 0) { cambia { AjustesFutbol.alternaAsignado(ctx, comp.slug, g.id) } } }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("Los partidos, las clasificaciones y los goleadores salen de ESPN (su servicio público, sin cuenta). Tus equipos y tus grupos van con tu cuenta: de cada canal, solo su nombre y su clave, nunca la dirección de tu lista ni tu contraseña.",
        color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp)
}

/** Elegir equipos: las competiciones arriba y sus equipos (de ESPN), con los tuyos marcados. */
@Composable
private fun ElegirEquipo(alCambiar: () -> Unit) {
    val ctx = LocalContext.current
    var i by rememberSaveable { mutableIntStateOf(0) }
    var v by remember { mutableIntStateOf(0) }
    v
    val comps = Competiciones.TODAS.filter { it != Competiciones.SUPERCOPA }
    Spacer(Modifier.height(6.dp))
    FiltrosRevista(comps.map { it.corto }, i) { i = it }
    val liga = comps[i].slug
    val lista by produceState<List<com.gonzalo.iptv.futbol.Equipo>?>(null, liga) { value = withContext(Dispatchers.IO) { Futbol.equipos(ctx, liga) }.orEmpty() }
    val l = lista
    when {
        l == null -> Nota("Leyendo los equipos…")
        l.isEmpty() -> Nota("No se han podido leer los equipos (¿sin conexión?).")
        else -> l.forEach { e ->
            val mio = AjustesFutbol.esMio(ctx, e.id)
            Row(Modifier.fillMaxWidth().tocable { AjustesFutbol.alterna(ctx, e, liga); v++; alCambiar() }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Escudo(e, 24.dp); Spacer(Modifier.width(10.dp))
                Text(e.nombre, color = if (mio) Acento else Charcoal, fontFamily = Display, fontWeight = if (mio) FontWeight.Bold else FontWeight.Normal, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(if (mio) "✓ TUYO" else "+ SEGUIR", color = if (mio) Acento else Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1, softWrap = false)
            }
            Filete(1.dp, Line)
        }
    }
}

/**
 * El buscador de canales (el de «Buscar», solo canales): escribes y salen los de tus listas; al tocar uno, [alElegir] con su lista, el
 * canal y los de su lista que se llaman parecido (para encontrar sus otras calidades).
 */
@Composable
fun BuscadorCanales(alCerrar: () -> Unit, alElegir: (Lista, Canal, List<Canal>) -> Unit) {
    var q by remember { mutableStateOf("") }
    val r by produceState<List<Pair<Lista, Canal>>?>(null, q) {
        if (q.trim().length < 2) { value = null; return@produceState }
        delay(250)
        value = withContext(Dispatchers.IO) { Futbol.buscaCanales(q) }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (q.isEmpty()) Text("DAZN, M+ LaLiga…", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 20.sp)
        BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 20.sp), cursorBrush = SolidColor(Acento),
            modifier = Modifier.fillMaxWidth().focoInicial())
    }
    Filete(1.dp)
    val x = r
    when {
        x == null -> Nota(if (q.isBlank()) "Escribe al menos dos letras." else "Buscando…", Modifier.padding(top = 6.dp))
        x.isEmpty() -> Nota("Nada con «$q» en tus listas.", Modifier.padding(top = 6.dp))
        else -> x.take(40).forEach { (l, c) ->
            Row(Modifier.fillMaxWidth().tocable { alElegir(l, c, Tele.enMemoria(l.id)?.canales ?: x.filter { it.first.id == l.id }.map { it.second }) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Logo(c.logo, c.nombre, Modifier.padding(end = 10.dp), 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(c.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Etiqueta(listOf(l.nombre, c.grupo).filter { it.isNotBlank() }.joinToString(" · "))
                }
                Text(Calidades_texto(c.nombre), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1, softWrap = false)
            }
            Filete(1.dp, Line)
        }
    }
    EnlaceRevista("Cancelar", Modifier.padding(top = 4.dp)) { alCerrar() }
}

private fun Calidades_texto(n: String) = com.gonzalo.iptv.futbol.Calidades.deNombre(n).texto.uppercase()
