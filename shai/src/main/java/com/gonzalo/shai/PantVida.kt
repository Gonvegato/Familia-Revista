package com.gonzalo.shai

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** La foto de tu personaje: solo en este móvil (no va a la cuenta). */
object Fotos {
    var version by mutableIntStateOf(0)
    private fun archivo(ctx: Context, id: String) = java.io.File(java.io.File(ctx.filesDir, "fotos").apply { mkdirs() }, "$id.jpg")
    fun carga(ctx: Context, id: String): android.graphics.Bitmap? { version; return archivo(ctx, id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }
    fun guarda(ctx: Context, id: String, uri: Uri) {
        val b = android.util.Base64.decode(fotoParaClaude(ctx, uri), android.util.Base64.NO_WRAP)
        archivo(ctx, id).writeBytes(b); version++
    }
    fun borra(ctx: Context, id: String) { archivo(ctx, id).delete(); version++ }
}

@Composable
fun TabCifras(u: Universo) {
    val ps = Carreras.partidos(u.id)
    if (ps.isEmpty()) { Nota("Sin partidos todavía."); return }
    CerrarTemporada(u)
    RecordsEHitos(u, ps.map { it.second })
    RecordsHistoricos(u, ps.map { it.second })
    val t = Estadisticas.total(ps.map { it.second })
    Antetitulo("Toda la carrera")
    LineaCifras(u, t)
    Estadisticas.porTemporada(ps.map { it.second }).forEach { (total, comps) ->
        Spacer(Modifier.height(14.dp)); Filete(2.dp)
        Text("Temporada ${total.temporada}", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 10.dp))
        LineaCifras(u, total)
        comps.forEach { c ->
            Text(c.competicion, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
            Text(listOfNotNull("${c.jugados} PJ", "${c.victorias}V ${c.empates}E ${c.derrotas}D", if (u.modo == Rol.JUGADOR) "${c.goles} G · ${c.asistencias} A" else "${c.golesFavor}-${c.golesContra}",
                c.notaMedia.takeIf { it > 0 }?.let { "nota ${Formato.decimal(it)}" }).joinToString(" · ").uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, lineHeight = 15.sp)
        }
    }
    if (u.personaje.historial.size > 1) {
        Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
        Antetitulo("Tu media en FC")
        Text(u.personaje.historial.joinToString("  →  ") { "${it.second}" }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 4.dp))
    }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
    Antetitulo("Los partidos")
    ps.reversed().forEach { (e, p) ->
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("${if (p.local) "vs" else "en"} ${p.rival}  ${p.resultado}", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp)
            Text(listOfNotNull("J${e.paso}", p.competicion.ifBlank { null }, p.letra, if (u.modo == Rol.JUGADOR) "${p.minutos}' · ${p.goles} G · ${p.asistencias} A" else p.goleadores.ifBlank { null },
                p.nota.takeIf { it > 0 }?.let { Formato.decimal(it) }).joinToString(" · ").uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, lineHeight = 15.sp)
        }
        Filete(1.dp, Line)
    }
}

/** El botón de cerrar la temporada (con su confirmación): balance, premios, vacaciones y mercado de verano. */
@Composable
fun CerrarTemporada(u: Universo, preguntando0: Boolean = false) {
    val ambito = rememberCoroutineScope()
    var preguntando by remember { mutableStateOf(preguntando0) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val va = EnCurso.ids["t" + u.id]
    val t = u.temporada.ifBlank { "esta temporada" }
    MagLink(va ?: "Cerrar la temporada $t", if (va == null) "Balance, premios, vacaciones y el mercado de verano; luego empieza la siguiente" else null, accent = true) { if (va == null) preguntando = true }
    aviso?.let { Nota(it, Modifier.padding(top = 4.dp), color = Acento) }
    if (preguntando) AlertDialog(onDismissRequest = { preguntando = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Cerrar la temporada $t?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text("Shai escribe el balance con tus cifras, los premios (de medios inventados), tus vacaciones y el mercado de verano, y pasas a ${Motor.siguienteTemporada(u.temporada).ifBlank { "la siguiente" }} con un año más." +
            (u.contrato?.takeIf { Contratos.quedan(it, u.temporada) <= 1 }?.let { " Tu contrato con el ${it.club} acaba con esta temporada: si no has renovado, quedarás libre." } ?: "") +
            " No se puede deshacer.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({
            preguntando = false; EnCurso.ids["t" + u.id] = "Cerrando la temporada…"; aviso = null
            ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.cierraTemporada(u.id) }; EnCurso.ids.remove("t" + u.id) }
        }) { Text("CERRARLA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ preguntando = false }) { Text("AÚN NO", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } })
    Spacer(Modifier.height(8.dp))
}

private fun partidos(n: Int) = if (n == 1) "1 partido" else "$n partidos"

/** Récords e hitos: los de tu carrera en Shai (con tus cifras). */
@Composable
fun RecordsEHitos(u: Universo, ps: List<Partido>) {
    val r = Hitos.records(u, ps)
    Antetitulo("Récords e hitos")
    MagFact("Partidos", Formato.cifra(r.partidos))
    if (u.modo == Rol.JUGADOR) {
        MagFact("Goles", Formato.cifra(r.goles)); MagFact("Hat-tricks", Formato.cifra(r.hatTricks)); MagFact("Más goles en un partido", Formato.cifra(r.masGolesPartido))
        MagFact("Racha más larga marcando", partidos(r.rachaMarcando))
    } else MagFact("Victorias", Formato.cifra(r.victorias))
    MagFact("Racha más larga sin perder", partidos(r.rachaInvicto))
    r.mejorTemporada?.let { MagFact("Mejor temporada", "${it.first} (${it.second}${if (u.modo == Rol.JUGADOR) " goles" else " victorias"})") }
    if (r.titulos.isNotEmpty()) MagFact("Títulos", r.titulos.joinToString(", "))
    u.hitos.filter { it.real == 0 }.reversed().take(12).forEach { h ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text((if (h.precoz) "★ " else "") + h.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp)
            Text(h.texto, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
            Etiqueta(listOfNotNull("J${h.paso}", h.temporada.ifBlank { null }, "${h.edad} años", if (h.precoz) "precoz" else null).joinToString(" · "))
        }
        Filete(1.dp, Line)
    }
    Text("Son los de tu carrera en Shai, con las cifras que metes; no los del fútbol de verdad.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
}

/** Tus rivales: los que han surgido solos y los que metes tú (si es un futbolista de verdad, la rivalidad solo es deportiva). */
@Composable
fun TusRivales(u: Universo) {
    var anadiendo by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    var club by remember { mutableStateOf("") }
    var real by remember { mutableStateOf(true) }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
    Antetitulo("Tus rivales")
    if (u.rivales.isEmpty()) Text("Aún ninguno. Surgen solos: el del club que siempre te marca, el central que te anula, el que compite por tu puesto, el que te disputa el trofeo de goleador…",
        color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
    u.rivales.sortedByDescending { it.intensidad }.forEach { r ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(r.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp)
            Etiqueta(listOfNotNull(r.club.ifBlank { null }, r.posicion.ifBlank { null }, "${r.intensidad}/100 · ${r.grado}", if (r.real) "real: solo deportiva" else null).joinToString(" · "))
            if (r.origen.isNotBlank()) Text(r.origen, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
            r.choques.takeLast(2).reversed().forEach { c -> Text("J${c.paso}: ${c.texto}", color = Charcoal, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp)) }
            EnlaceRevista("Quitar", Modifier.padding(top = 2.dp)) { Carreras.guarda(Rivales.quita(Carreras.de(u.id) ?: u, r.id)) }
        }
        Filete(1.dp, Line)
    }
    if (!anadiendo) MagLink("Añadir un rival", "Uno que tú elijas") { anadiendo = true }
    else {
        Campo("Nombre", nombre, "El rival") { nombre = it.take(60) }
        Campo("Club", club, "Su club") { club = it.take(60) }
        SiNo("Es un futbolista de verdad", "Entonces la rivalidad es solo deportiva: la prensa compara cifras, sin frases ni mal rollo.", real) { real = it }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonAcento("Añadir", modifier = Modifier.weight(1f)) {
                if (nombre.isNotBlank()) Carreras.guarda(Rivales.anade(Carreras.de(u.id) ?: u, nombre, club, real))
                nombre = ""; club = ""; anadiendo = false
            }
            BotonSuave("Cancelar", modifier = Modifier.weight(1f)) { anadiendo = false }
        }
    }
}

/** Los récords del fútbol de verdad (los de tu lista): los que ya son tuyos, los que tienes a tiro y la lista entera. */
@Composable
fun RecordsHistoricos(u: Universo, ps: List<Partido>, abierta0: Boolean = false) {
    var abierta by rememberSaveable { mutableStateOf(abierta0) }
    Antetitulo("Récords históricos")
    Text("Tu carrera comparada con los récords del fútbol de verdad (tu lista de 200, temporada 2025/26).", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
    if (u.recordsSuyos.isNotEmpty()) {
        Etiqueta("Los que son tuyos", Modifier.padding(top = 10.dp))
        u.recordsSuyos.sortedByDescending { it.paso }.forEach { s ->
            val r = RecordsReales.de(s.n) ?: return@forEach
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("★ " + r.record, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp)
                Text((if (s.iguala) "Lo iguala" else "Ahora es suyo") + ": ${s.marca}. Lo tenía ${r.quien} (${r.marca}).", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
                Etiqueta(listOfNotNull("J${s.paso}", s.temporada.ifBlank { null }, "#${r.n}").joinToString(" · "))
            }
            Filete(1.dp, Line)
        }
    }
    u.hitos.filter { it.real > 0 && it.id.contains("_cerca_") }.takeLast(4).reversed().forEach { h ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(h.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp)
            Text(h.texto, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Filete(1.dp, Line)
    }
    val tiro = Historicos.aTiro(u, ps)
    if (tiro.isNotEmpty()) {
        Etiqueta("A tiro", Modifier.padding(top = 10.dp))
        tiro.take(8).forEach { c ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(c.r.record, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp)
                Text(c.texto, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Filete(1.dp, Line)
        }
    }
    if (Fechas.lee(u.personaje.nacimiento) == null && u.modo == Rol.JUGADOR)
        Nota("Pon tu fecha de nacimiento en FC (Perfil → Editar) y la fecha de los partidos para compararte con los récords de edad (el más joven, el más veterano).", Modifier.padding(top = 6.dp))
    MagLink(if (abierta) "Ocultar la lista" else "Ver los 200 récords", "Los dudosos no cuentan para batir nada") { abierta = !abierta }
    if (abierta) RecordReal.CATEGORIAS.forEachIndexed { i, nombre ->
        Text(nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 12.dp))
        RecordsReales.TODOS.filter { it.categoria == i + 1 }.forEach { r ->
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text("${r.n}. ${r.record}", color = if (r.dudoso) Muted else Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp)
                Text("${r.quien} · ${r.marca}", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
                if (r.dudoso || r.nota.isNotBlank()) Text((if (r.dudoso) "Dudoso: no cuenta. " else "") + r.nota, color = Acento, fontFamily = Body, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
}

@Composable
private fun LineaCifras(u: Universo, l: Linea) {
    MagFact("Partidos", "${l.jugados} (${l.victorias}V ${l.empates}E ${l.derrotas}D)")
    if (u.modo == Rol.JUGADOR) {
        MagFact("Goles", Formato.cifra(l.goles)); MagFact("Asistencias", Formato.cifra(l.asistencias)); MagFact("Minutos", Formato.cifra(l.minutos))
        if (l.notaMedia > 0) MagFact("Nota media", Formato.decimal(l.notaMedia))
    } else MagFact("Goles a favor y en contra", "${l.golesFavor} - ${l.golesContra}")
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TabVida(u: Universo) {
    var aviso by remember { mutableStateOf<String?>(null) }
    fun haz(f: () -> Universo) { aviso = try { Carreras.guarda(f()); null } catch (e: Exception) { Motor.mensaje(e) } }
    Antetitulo("Tu dinero")
    MagFact("En el banco", Formato.euros(u.dinero))
    MagFact("Sueldo a la semana", u.contrato?.let { Formato.euros(it.sueldo) } ?: "Libre: sin sueldo")
    if (u.patrocinio > 0) MagFact("Patrocinios a la semana", Formato.euros(u.patrocinio))
    u.contrato?.takeIf { it.porGol > 0 }?.let { MagFact("Por cada gol", Formato.euros(it.porGol)) }
    val rentas = u.bienes.sumOf { it.renta }
    if (rentas != 0L) MagFact("Tus cosas, cada jornada", (if (rentas > 0) "+" else "") + Formato.euros(rentas))
    MagFact("Patrimonio", Formato.euros(u.patrimonio))
    u.contrato?.let { MagFact("Contrato", "${it.club}, hasta ${it.hasta}") }
    aviso?.let { Nota(it, Modifier.padding(top = 4.dp), color = Acento) }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
    Antetitulo("Fuera del campo")
    Fuera.TODOS.forEach { (k, n) -> BarraValor(n, u.valorFuera(k)) }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
    Antetitulo("Lo tuyo")
    if (u.bienes.isEmpty()) Nota("Nada todavía.", Modifier.padding(vertical = 4.dp))
    u.bienes.forEach { b ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(b.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp)
                Text(listOf("vale ${Formato.eurosCorto(b.valor)}", "pagaste ${Formato.eurosCorto(b.compra)}", (if (b.renta >= 0) "+" else "") + Formato.eurosCorto(b.renta) + "/jornada").joinToString(" · ").uppercase(),
                    color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, lineHeight = 14.sp)
            }
            EnlaceRevista("Vender", Modifier.padding(start = 10.dp)) { haz { Mercado.vende(u, b.id) } }
        }
        Filete(1.dp, Line)
    }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
    Antetitulo("La bolsa")
    Cuerpo("Empresas inventadas; se mueven con cada jornada.", tam = 12)
    Mercado.VALORES.forEach { v ->
        val h = u.precios[v.simbolo].orEmpty()
        val p = h.lastOrNull() ?: v.inicial
        val antes = h.getOrNull(h.size - 2) ?: p
        val cambio = if (antes > 0) (p - antes) * 100.0 / antes else 0.0
        val tiene = u.cartera[v.simbolo] ?: 0L
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${v.nombre} ", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
                Text(Formato.centimos(p), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(listOfNotNull(v.simbolo, v.sector, (if (cambio >= 0) "+" else "") + Formato.decimal(cambio) + " %", if (tiene > 0) "tienes ${Formato.cifra(tiene)} (${Formato.eurosCorto(tiene * p / 100)})" else null).joinToString(" · ").uppercase(),
                color = if (cambio >= 0) Muted else Acento, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, lineHeight = 14.sp)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(10L, 100L, 1000L).forEach { n -> EnlaceRevista("Comprar ${Formato.cifra(n)}", Modifier.padding(top = 6.dp), acento = true) { haz { Mercado.compraAcciones(Carreras.de(u.id) ?: u, v.simbolo, n) } } }
                if (tiene > 0) EnlaceRevista("Vender todas", Modifier.padding(top = 6.dp)) { haz { Mercado.vendeAcciones(Carreras.de(u.id) ?: u, v.simbolo, tiene) } }
            }
        }
        Filete(1.dp, Line)
    }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
    Antetitulo("De compras")
    Cuerpo("Lo que te puedes permitir y lo que tienes cerca; según crezca tu dinero, habrá más.", tam = 12)
    val catalogo = Mercado.catalogo(u)
    listOf(Bien.CASA to "Casas", Bien.COCHE to "Coches y caprichos", Bien.NEGOCIO to "Negocios").forEach { (t, n) ->
        Text(n, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 10.dp))
        catalogo.filter { it.tipo == t }.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp)
                    Text(a.descripcion, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
                    Text((Formato.euros(a.precio) + " · " + (if (a.renta >= 0) "deja +" else "cuesta ") + Formato.eurosCorto(kotlin.math.abs(a.renta)) + "/jornada").uppercase(),
                        color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, lineHeight = 14.sp)
                }
                if (Mercado.llega(u, a)) EnlaceRevista("Comprar", Modifier.padding(start = 10.dp), acento = true) { haz { Mercado.compra(Carreras.de(u.id) ?: u, a) } }
                else Text("NO TE LLEGA", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 10.dp))
            }
            Filete(1.dp, Line)
        }
    }
}

@Composable
fun BarraValor(nombre: String, v: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row { Etiqueta(nombre, Modifier.weight(1f)); Text("$v", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(5.dp).background(Line)) { Box(Modifier.fillMaxWidth(v / 100f).fillMaxHeight().background(Dorado)) }
    }
}

@Composable
fun TabPerfil(u: Universo, alBorrada: () -> Unit) {
    val ctx = LocalContext.current
    var editando by rememberSaveable { mutableStateOf(false) }
    var borrar by remember { mutableStateOf(false) }
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) runCatching { Fotos.guarda(ctx, u.id, uri) } }
    val pj = u.personaje
    val foto = Fotos.carga(ctx, u.id)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(96.dp).clip(esquina(6.dp)).background(Sand).tocable { elegir.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
            if (foto != null) Image(foto.asImageBitmap(), "Foto", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text("FOTO", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.5.sp)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(pj.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp)
            Text(listOfNotNull("${pj.edad} años", Fechas.lee(pj.nacimiento)?.let { "nació el ${Fechas.texto(it)}" }, pj.nacionalidad.ifBlank { null }, pj.posicion.ifBlank { null }, pj.dorsal.takeIf { it > 0 }?.let { "dorsal $it" }).joinToString(" · ").uppercase(),
                color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, lineHeight = 15.sp)
            if (foto != null) EnlaceRevista("Quitar la foto", Modifier.padding(top = 4.dp)) { Fotos.borra(ctx, u.id) }
        }
    }
    if (pj.rasgos.isNotBlank()) Text(pj.rasgos, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp))
    if (u.trayectoria.isNotBlank()) Text(u.trayectoria, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
    // Su nivel y su alcance (lo de ahora, cómo ha subido)
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
    Antetitulo("Tu nivel")
    val nv = u.nivelActual
    Text(nv.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, modifier = Modifier.padding(top = 2.dp))
    Text(nv.explica + ".", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
    MagFact("Seguidores", Alcance.texto(Alcance.seguidores(u)))
    MagFact("Fama", "${u.valorFuera(Fuera.FAMA)} / 100")
    Nivel.entries.firstOrNull { it.desde > u.valorFuera(Fuera.FAMA) }?.let { sig -> MagFact("Para ${sig.nombre.lowercase()}", "fama ${sig.desde} (te faltan ${sig.desde - u.valorFuera(Fuera.FAMA)})") }
    u.nivelDe?.takeIf { it != nv }?.let { MagFact("Empezaste como", it.nombre) }
    Text("Cómo se te ve: " + Percepcion.de(u, Carreras.partidos(u.id).map { it.second }.filter { it.temporada == u.temporada }) + ".", color = Charcoal, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
    Text("Te cubren: ${nv.medios}.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
    if (u.famaHist.size > 1) Text("Cómo ha subido tu fama: " + u.famaHist.takeLast(12).joinToString(" → ") { "J${it.first} ${it.second}" }, color = Charcoal, fontFamily = Body, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
    Text("Sube poco a poco con lo que haces (tus partidos, tus goles y notas, tus decisiones y tus líos), con un tope de ${Vida.topeFama(pj.edad)} por jornada a tus ${pj.edad} años (a los 21 o menos, un buen tramo pesa mucho más; a partir de los 28, poco)." , color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
    TusRivales(u)
    // Su contrato (el de Shai)
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
    Antetitulo("Tu contrato")
    val c = u.contrato
    if (c == null) Nota("Eres agente libre: no cobras sueldo de ningún club y cualquiera puede ficharte. Las ofertas llegarán.", Modifier.padding(vertical = 4.dp), color = Acento)
    else {
        Text(c.club + (if (c.nota.isNotBlank()) " · ${c.nota.lowercase()}" else ""), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 2.dp))
        MagFact("Sueldo a la semana", Formato.euros(c.sueldo))
        val q = Contratos.quedan(c, u.temporada)
        MagFact("Hasta", "${c.hasta} (" + (if (q <= 1) "es la última" else "quedan $q temporadas") + ")")
        if (c.prima > 0) MagFact("Prima de firma", Formato.euros(c.prima))
        if (c.clausula > 0) MagFact("Cláusula", Formato.eurosCorto(c.clausula))
        if (c.porGol > 0) MagFact("Por cada gol", Formato.euros(c.porGol))
        if (c.objetivos.isNotBlank()) Text(c.objetivos, color = Charcoal, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        if (c.imagen.isNotBlank()) Text(c.imagen, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        Text("Es el contrato de Shai, no el de FC: de aquí sale lo que cobras cada jornada, y el club o tu representante hablarán de renovar cuando toque.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
    }
    // Lo que Shai recuerda
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
    Antetitulo("Lo que Shai recuerda")
    if (u.memoria.isBlank()) Nota("Todavía nada: cada ${Motor.CADA_MEMORIA} jornadas y al cerrar la temporada, Shai resume las tramas abiertas, tu gente y lo que has decidido.", Modifier.padding(vertical = 4.dp))
    else {
        u.memoria.lines().filter { it.isNotBlank() }.forEach { l -> Text(l, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp)) }
        Etiqueta("Escrito en la jornada ${u.memoriaPaso}", Modifier.padding(top = 4.dp))
    }
    val ambitoM = rememberCoroutineScope()
    var avisoM by remember { mutableStateOf<String?>(null) }
    val vaM = EnCurso.ids["m" + u.id]
    MagLink(vaM ?: "Rehacerlo ahora", if (vaM == null) (if (Prueba.activo) "Modo prueba: gratis" else "Sonnet, cosa de un céntimo") else null) {
        if (vaM != null || !ClaveApi.hay) return@MagLink
        EnCurso.ids["m" + u.id] = "Recordando…"
        ambitoM.launch { avisoM = withContext(Dispatchers.IO) { Motor.recuerda(u.id) }; EnCurso.ids.remove("m" + u.id) }
    }
    avisoM?.let { Nota(it, Modifier.padding(top = 4.dp), color = Acento) }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
    Antetitulo(if (u.modo == Rol.MISTER) "Tus cifras" else "Tu carta de FC")
    if (!editando) {
        if (u.modo == Rol.JUGADOR) MagFact("Media", if (pj.media > 0) "${pj.media}" else "—")
        (if (u.modo == Rol.MISTER) Cifras.MISTER else Cifras.JUGADOR).forEach { (k, n) -> MagFact(n, pj.atributos[k]?.toString() ?: "—") }
        EnlaceRevista("Cambiar (como en tu partida)", Modifier.padding(top = 6.dp), acento = true) { editando = true }
    } else EditarCarta(u) { editando = false }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
    Antetitulo("Tu gente")
    if (u.entorno.isEmpty()) Nota("Todavía no hay nadie: llegarán con la presentación y con lo que pase.", Modifier.padding(vertical = 4.dp))
    u.entorno.sortedByDescending { it.afinidad }.forEach { p ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(p.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp)
            Etiqueta(p.rol)
            if (p.nota.isNotBlank()) Text(p.nota, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
            BarraValor("Afinidad", p.afinidad)
        }
        Filete(1.dp, Line)
    }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(6.dp))
    SiNo("Lo de entre partidos, solo", "Tras cada partido, dos cosas que pasan mientras tanto (por lotes, a mitad de precio).", u.autoEventos) { Carreras.guarda(u.copy(autoEventos = it)) }
    MagLink("Borrar esta carrera", "Y todo lo que pasó en ella, en todos tus dispositivos") { borrar = true }
    if (borrar) AlertDialog(onDismissRequest = { borrar = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Borrar la carrera de ${u.nombre}?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text("Se borra con sus partidos, su prensa y sus mensajes, en todos tus dispositivos. No tiene vuelta atrás.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({ borrar = false; Carreras.borra(u.id); Fotos.borra(ctx, u.id); alBorrada() }) { Text("BORRAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ borrar = false }) { Text("NO", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } })
}

@Composable
private fun EditarCarta(u: Universo, alAcabar: () -> Unit) {
    val pj = u.personaje
    var media by remember { mutableStateOf(if (pj.media > 0) "${pj.media}" else "") }
    val cifras = remember { mutableStateMapOf<String, String>().apply { pj.atributos.forEach { (k, v) -> put(k, "$v") } } }
    var rasgos by remember { mutableStateOf(pj.rasgos) }
    var trayectoria by remember { mutableStateOf(u.trayectoria) }
    var edad by remember { mutableStateOf("${pj.edad}") }
    var nacimiento by remember { mutableStateOf(Fechas.lee(pj.nacimiento)?.let { Fechas.texto(it) } ?: "") }
    if (u.modo == Rol.JUGADOR) Campo("Media", media, "—", teclado = KeyboardType.Number) { media = it.filter { c -> c.isDigit() }.take(2) }
    (if (u.modo == Rol.MISTER) Cifras.MISTER else Cifras.JUGADOR).chunked(3).forEach { fila ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            fila.forEach { (k, n) -> Campo(n, cifras[k].orEmpty(), "—", Modifier.weight(1f), teclado = KeyboardType.Number) { cifras[k] = it.filter { c -> c.isDigit() }.take(2) } }
            repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Campo("Edad", edad, "18", teclado = KeyboardType.Number) { edad = it.filter { c -> c.isDigit() }.take(2) }
    Campo("Fecha de nacimiento en FC", nacimiento, "dd/mm/aaaa (opcional)") { nacimiento = it.take(20) }
    if (nacimiento.isNotBlank() && Fechas.lee(nacimiento) == null) Nota("No entiendo esa fecha: ponla como 12/08/2008.", color = Acento)
    Campo("Su forma de ser", rasgos, "", lineas = 3) { rasgos = it }
    Campo("Quién es (su historia, su palmarés)", trayectoria, "23 años, Balón de Oro, canterano que debuta…", lineas = 4) { trayectoria = it }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BotonAcento("Guardar", modifier = Modifier.weight(1f)) {
            val m = media.toIntOrNull() ?: 0
            val nuevo = pj.copy(media = m, atributos = cifras.mapValues { it.value.toIntOrNull() ?: 0 }.filterValues { it > 0 }, rasgos = rasgos.trim(), edad = edad.toIntOrNull() ?: pj.edad,
                nacimiento = Fechas.lee(nacimiento)?.let { Fechas.iso(it) } ?: if (nacimiento.isBlank()) "" else pj.nacimiento,
                historial = if (m > 0 && m != pj.media) (pj.historial + (u.paso to m)).takeLast(60) else pj.historial)
            Carreras.guarda((Carreras.de(u.id) ?: u).copy(personaje = nuevo, trayectoria = trayectoria.trim())); alAcabar()
        }
        BotonSuave("Cancelar", modifier = Modifier.weight(1f)) { alAcabar() }
    }
}
