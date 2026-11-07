package com.gonzalo.azabache.pc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * La ficha: su portada, cómo está, tu nota (la general y los criterios de su categoría), tu reseña y sus datos. Se cambia aquí y se
 * guarda con «Guardar»: a la cuenta va solo lo que has cambiado (lo que mientras haya cambiado Hiedra o Marfil no se pisa).
 */
@Composable
fun PantDetalle(k: Kind, id: Long, volver: Pantalla, nav: Nav, disenar: Boolean = false) {
    LaFamilia.cambios
    val f = Cuenta.ficha(k.id, id)
    if (f == null) {
        Pagina { VolverRevista({ nav.ir(volver) }); Vacio("Esta ficha ya no está", "Se ha borrado (aquí o en otro dispositivo).") }
        return
    }
    var base by remember(id) { mutableStateOf(f) }
    var o by remember(id) { mutableStateOf(f.o) }
    var valorada by remember(id) { mutableStateOf(f.valorada) }
    var conNota by remember(id) { mutableStateOf(!f.sinValorar) }
    val sucio = canon(o) != canon(base.o) || valorada != base.valorada
    // Lo que llega de fuera mientras la ves, si no estás cambiando nada, se ve al momento
    LaunchedEffect(canon(f.o), f.valorada, f.portada) { if (!sucio) { base = f; o = f.o; valorada = f.valorada; conNota = !f.sinValorar } }
    fun pon(c: String, v: Any?) { o = LinkedHashMap(o).also { it[c] = v } }
    fun n(c: String) = o[c].comoLong()
    fun t(c: String) = (o[c] as? String).orEmpty()
    val scope = rememberCoroutineScope()
    var portadaAbierta by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf(false) }
    var diario by remember { mutableStateOf(false) }
    var saga by remember { mutableStateOf(false) }
    var notaAbierta by remember { mutableStateOf<Nota?>(null) }
    // Se abre en modo lectura (la página de revista, como en el móvil); «Editar» abre los datos y la nota, y «Diseñar», la maqueta
    var editando by remember(id) { mutableStateOf(false) }
    var disenando by remember(id) { mutableStateOf(disenar) }
    var recomendar by remember { mutableStateOf(false) }
    // Tu pareja (para «Recomendar»: como en el móvil, los juegos y los libros)
    val pareja = Cuenta.uidParejaSabido()
    var nombrePareja by remember(pareja) { mutableStateOf("tu pareja") }
    LaunchedEffect(pareja) { if (pareja.isNotBlank()) nombrePareja = withContext(Dispatchers.IO) { runCatching { Cuenta.nombreDe(pareja) }.getOrDefault("tu pareja") } }
    fun guarda() {
        val nueva = if (valorada && !conNota) LinkedHashMap(o).also { m -> NOTAS.forEach { m[it] = 0L } } else o
        Cuenta.guarda(k.id, base, nueva, valorada)
        base = Ficha(k.id, nueva, valorada, base.ref, base.portada, base.portadaUrl, base.sinNota && !conNota, base.mostaza)
        o = nueva
        nav.aviso("Guardado en la cuenta")
        editando = false
    }
    fun descarta() { o = base.o; valorada = base.valorada; conNota = !base.sinValorar }
    // «Terminar y valorar» (o «Valorar»): pasa a terminado, con la nota a la vista, y abre los datos
    fun valora() {
        valorada = true; conNota = true
        if (NOTAS.all { n(it) == 0L }) NOTAS.forEach { pon(it, 50L) }
        if (n("end") <= 0) pon("end", LocalDate.now().toEpochDay())
        editando = true
    }

    if (!editando) PaginaDeFicha(
        k, f, volver, nav, sucio,
        AccionesFicha(
            editar = { editando = true }, valorar = { valora() }, portada = { portadaAbierta = true }, diario = { diario = true },
            saga = { saga = true }, nota = { notaAbierta = it },
            recomendar = if (pareja.isNotBlank() && (k.isGame || k.isBook)) ({ recomendar = true }) else null, pareja = nombrePareja,
        ),
        onGuardar = { guarda() }, onDescartar = { descarta() },
        disenando = disenando, onDisenar = { disenando = it },
    ) else Pagina {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { VolverRevista({ editando = false }, texto = "La página") }
            if (sucio) {
                Text("Sin guardar", color = Acento, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(14.dp))
                EnlaceRevista("Descartar") { descarta() }
                Spacer(Modifier.width(14.dp))
            }
            if (sucio) BotonAcento("Guardar", alto = 42.dp, modifier = Modifier.width(140.dp)) { guarda() }
            else Text("Guardado en la cuenta", color = Muted, fontFamily = Body, fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
        Row {
            // La portada y lo que se hace con la ficha
            Column(Modifier.width(300.dp)) {
                PortadaCosa(f.portada, f.portadaWeb, t("name"), Modifier.fillMaxWidth(), pie = k.unit)
                Spacer(Modifier.height(12.dp))
                EnlaceRevista("Cambiar portada") { portadaAbierta = true }
                Spacer(Modifier.height(8.dp))
                EnlaceRevista("Escribir en el diario") { diario = true }
                Spacer(Modifier.height(8.dp))
                EnlaceRevista("Añadir a una saga") { saga = true }
                Spacer(Modifier.height(8.dp))
                EnlaceRevista("Borrar", acento = true) { borrar = true }
                if (f.ref != null) {
                    Spacer(Modifier.height(16.dp))
                    Cuerpo(mismaQueEnSuApp(k, f), tam = 12)
                }
            }
            Spacer(Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                RevistaHeadline(t("name").ifBlank { "Sin nombre" }, k.title + " · " + estadoDe(Ficha(k.id, o, valorada), k))
                Spacer(Modifier.height(16.dp))
                // Cómo está
                val opciones = listOf(k.doneWord.removeSuffix("s"), k.statuses[1], k.statuses[0], k.statuses[2])
                val sel = if (valorada) 0 else when (n("status").toInt()) { 1 -> 1; 2 -> 3; else -> 2 }
                MagChoice(opciones, sel, "Cómo está") { i ->
                    valorada = i == 0
                    if (i != 0) pon("status", when (i) { 1 -> 1L; 3 -> 2L; else -> 0L })
                    if (i == 0 && n("end") <= 0) pon("end", LocalDate.now().toEpochDay())
                    if (i == 1 && n("start") <= 0) pon("start", LocalDate.now().toEpochDay())
                }
                // La nota
                if (valorada) {
                    Spacer(Modifier.height(22.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
                    Antetitulo("Tu nota")
                    Spacer(Modifier.height(8.dp))
                    if (!conNota) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Sin valorar", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 30.sp)
                            Spacer(Modifier.width(24.dp))
                            BotonSuave("Valorar") { conNota = true; if (NOTAS.all { n(it) == 0L }) NOTAS.forEach { pon(it, 50L) } }
                        }
                    } else {
                        NOTAS.forEachIndexed { i, c ->
                            if (i == 0 || i <= k.crit) BarraNota(k.labels.getOrElse(i) { c }, n(c).toInt(), grande = i == 0) { pon(c, it.toLong()) }
                        }
                    }
                }
                // La reseña
                Spacer(Modifier.height(22.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
                Antetitulo(if (valorada) "Tu reseña" else "Lo que te parece")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(t("review"), { pon("review", it) }, Modifier.fillMaxWidth().heightIn(min = 120.dp), placeholder = { Text("Escribe lo que quieras…") })
                // Los datos
                Spacer(Modifier.height(22.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
                Antetitulo("Datos")
                Spacer(Modifier.height(8.dp))
                Campo("Nombre", t("name")) { pon("name", it) }
                if (k.id != GAMES.id) Campo(k.creatorWord, t("creator")) { pon("creator", it) }
                if (k.id == GAMES.id || k.custom) Campo(k.platformWord, t("platform"), sugerencias = k.platforms) { pon("platform", it) }
                if (k.id == BOOKS.id) CampoNumero("Páginas", n("pages")) { pon("pages", it) }
                if (k.id == GAMES.id) {
                    Text("Lo que suele durar (horas)", color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("ttbMain" to "Historia", "ttbExtra" to "Con extras", "ttbFull" to "Al completo").forEach { (c, etq) ->
                            CampoNumero(etq, n(c) / 60, Modifier.weight(1f)) { pon(c, it * 60) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CampoFecha("Empezado", n("start"), Modifier.weight(1f)) { pon("start", it) }
                    if (valorada) CampoFecha("Terminado", n("end"), Modifier.weight(1f)) { pon("end", it) } else Spacer(Modifier.weight(1f))
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Favorito", Modifier.weight(1f), color = Charcoal, fontFamily = Display, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Interruptor(o["fav"] == true) { pon("fav", it) }
                }
                // El tiempo (de Hiedra, Marfil, Steam…: se suman los dispositivos)
                if (f.minutos > 0 || f.ref != null) {
                    Spacer(Modifier.height(22.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
                    Antetitulo(if (k.id == GAMES.id) "Tiempo jugado" else if (k.id == BOOKS.id) "Tiempo leyendo" else if (f.mostaza) "Tiempo viendo" else "Tiempo")
                    Spacer(Modifier.height(6.dp))
                    MagFact("En total", duracion(f.minutos.toLong()))
                    val dias = f.ref?.let { Cuenta.porDia(it) }.orEmpty().entries.sortedByDescending { it.key }.take(7)
                    dias.forEach { (d, ms) -> MagFact(runCatching { diaLargo(LocalDate.parse(d)) }.getOrDefault(d), duracion(ms / 60_000L)) }
                    if (k.id == BOOKS.id && f.paginas > 0) MagFact("Páginas", f.paginas.toString())
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (portadaAbierta) DialogoPortada(k, f, nav) { portadaAbierta = false }
    if (diario) DialogoNota(null, listOf("${k.id}:$id"), nav) { diario = false }
    notaAbierta?.let { n0 -> DialogoNota(n0, n0.sobre, nav) { notaAbierta = null } }
    if (saga) DialogoSaga(k, f, nav) { saga = false }
    if (recomendar) DialogoRecomendar(f, k, nombrePareja, nav) { recomendar = false }
    if (borrar) Dialogo("¿Borrar «${f.nombre}»?", { borrar = false }, botones = {
        BotonSuave("Cancelar") { borrar = false }
        BotonSuave("Borrar", peligro = true) {
            borrar = false
            scope.launch { withContext(Dispatchers.IO) { Cuenta.borra(f) }; nav.aviso("Borrado"); nav.ir(volver) }
        }
    }) {
        Cuerpo(when {
            f.mostaza -> "Se quita de Hathor (queda borrada en la cuenta y en tus otros dispositivos: no vuelve sola aunque la veas; solo «Añadir» la recupera, con lo suyo)."
            f.ref != null -> "Se borra de la cuenta: deja de estar también en ${if (k.id == GAMES.id) "Bastet" else "Seshat"} y en tus otros dispositivos."
            else -> "Se borra de la cuenta (y de tus otros dispositivos)."
        }, tam = 14)
    }
}

/** Una nota con su barra (0–100). */
@Composable
fun BarraNota(nombre: String, v: Int, grande: Boolean = false, onCambia: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(nombre, Modifier.width(150.dp), color = Charcoal, fontFamily = if (grande) Display else Body, fontSize = if (grande) 20.sp else 14.sp, fontWeight = if (grande) FontWeight.SemiBold else FontWeight.Normal)
        Slider(v.toFloat(), { onCambia(it.toInt()) }, Modifier.weight(1f), valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = Acento, activeTrackColor = Acento, inactiveTrackColor = Sand))
        Spacer(Modifier.width(14.dp))
        Text(v.toString(), Modifier.width(52.dp), color = if (grande) Acento else Charcoal, fontFamily = Display, fontSize = if (grande) 34.sp else 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Campo(etiqueta: String, v: String, modifier: Modifier = Modifier.fillMaxWidth(), sugerencias: List<String> = emptyList(), onCambia: (String) -> Unit) {
    Column(modifier.padding(vertical = 4.dp)) {
        OutlinedTextField(v, onCambia, Modifier.fillMaxWidth(), label = { Text(etiqueta) }, singleLine = true)
        if (sugerencias.isNotEmpty()) FiltrosRevista(sugerencias, sugerencias.indexOf(v), Modifier.padding(top = 4.dp)) { onCambia(sugerencias[it]) }
    }
}

@Composable
fun CampoNumero(etiqueta: String, v: Long, modifier: Modifier = Modifier.fillMaxWidth(), onCambia: (Long) -> Unit) {
    var texto by remember(v) { mutableStateOf(if (v > 0) v.toString() else "") }
    OutlinedTextField(texto, { s -> texto = s.filter { it.isDigit() }.take(6); onCambia(texto.toLongOrNull() ?: 0L) }, modifier.padding(vertical = 4.dp), label = { Text(etiqueta) }, singleLine = true)
}

/** Una fecha (AAAA-MM-DD) que se guarda como día desde 1970; vacía, sin fecha. */
@Composable
fun CampoFecha(etiqueta: String, dia: Long, modifier: Modifier = Modifier, onCambia: (Long) -> Unit) {
    var texto by remember(dia) { mutableStateOf(if (dia > 0) LocalDate.ofEpochDay(dia).toString() else "") }
    val mal = texto.isNotBlank() && runCatching { LocalDate.parse(texto.trim()) }.isFailure
    OutlinedTextField(texto, { s -> texto = s; if (s.isBlank()) onCambia(0L) else runCatching { LocalDate.parse(s.trim()) }.getOrNull()?.let { onCambia(it.toEpochDay()) } },
        modifier.padding(vertical = 4.dp), label = { Text(etiqueta) }, singleLine = true, isError = mal, placeholder = { Text("AAAA-MM-DD") },
        supportingText = { Text(if (mal) "Escribe la fecha así: 2026-11-05" else fecha(dia)) })
}
