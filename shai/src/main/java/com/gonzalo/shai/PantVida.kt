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
    MagFact("Sueldo a la semana", Formato.euros(u.salario))
    val rentas = u.bienes.sumOf { it.renta }
    if (rentas != 0L) MagFact("Tus cosas, cada jornada", (if (rentas > 0) "+" else "") + Formato.euros(rentas))
    MagFact("Patrimonio", Formato.euros(u.patrimonio))
    if (u.contratoHasta.isNotBlank()) MagFact("Contrato", u.contratoHasta)
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
    listOf(Bien.CASA to "Casas", Bien.COCHE to "Coches", Bien.NEGOCIO to "Negocios").forEach { (t, n) ->
        Text(n, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 10.dp))
        Mercado.TIENDA.filter { it.tipo == t }.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp)
                    Text(a.descripcion, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
                    Text((Formato.euros(a.precio) + " · " + (if (a.renta >= 0) "deja +" else "cuesta ") + Formato.eurosCorto(kotlin.math.abs(a.renta)) + "/jornada").uppercase(),
                        color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, lineHeight = 14.sp)
                }
                EnlaceRevista("Comprar", Modifier.padding(start = 10.dp), acento = a.precio <= u.dinero) { haz { Mercado.compra(Carreras.de(u.id) ?: u, a) } }
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
            Text(listOfNotNull("${pj.edad} años", pj.nacionalidad.ifBlank { null }, pj.posicion.ifBlank { null }, pj.dorsal.takeIf { it > 0 }?.let { "dorsal $it" }).joinToString(" · ").uppercase(),
                color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, lineHeight = 15.sp)
            if (foto != null) EnlaceRevista("Quitar la foto", Modifier.padding(top = 4.dp)) { Fotos.borra(ctx, u.id) }
        }
    }
    if (pj.rasgos.isNotBlank()) Text(pj.rasgos, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp))
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
    var edad by remember { mutableStateOf("${pj.edad}") }
    if (u.modo == Rol.JUGADOR) Campo("Media", media, "—", teclado = KeyboardType.Number) { media = it.filter { c -> c.isDigit() }.take(2) }
    (if (u.modo == Rol.MISTER) Cifras.MISTER else Cifras.JUGADOR).chunked(3).forEach { fila ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            fila.forEach { (k, n) -> Campo(n, cifras[k].orEmpty(), "—", Modifier.weight(1f), teclado = KeyboardType.Number) { cifras[k] = it.filter { c -> c.isDigit() }.take(2) } }
            repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Campo("Edad", edad, "18", teclado = KeyboardType.Number) { edad = it.filter { c -> c.isDigit() }.take(2) }
    Campo("Su forma de ser", rasgos, "", lineas = 3) { rasgos = it }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BotonAcento("Guardar", modifier = Modifier.weight(1f)) {
            val m = media.toIntOrNull() ?: 0
            val nuevo = pj.copy(media = m, atributos = cifras.mapValues { it.value.toIntOrNull() ?: 0 }.filterValues { it > 0 }, rasgos = rasgos.trim(), edad = edad.toIntOrNull() ?: pj.edad,
                historial = if (m > 0 && m != pj.media) (pj.historial + (u.paso to m)).takeLast(60) else pj.historial)
            Carreras.guarda(u.copy(personaje = nuevo)); alAcabar()
        }
        BotonSuave("Cancelar", modifier = Modifier.weight(1f)) { alAcabar() }
    }
}
