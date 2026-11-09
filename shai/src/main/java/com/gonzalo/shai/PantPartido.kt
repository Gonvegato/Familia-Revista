package com.gonzalo.shai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** «7,5» o «7.5» → 7.5 (de 0 a 10). */
fun nota(t: String): Double = t.replace(',', '.').filter { it.isDigit() || it == '.' }.toDoubleOrNull()?.coerceIn(0.0, 10.0) ?: 0.0

/** Una foto, lista para Claude: como mucho 1568 px de lado, en JPEG (base64). */
fun fotoParaClaude(ctx: Context, uri: Uri): String {
    val op = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, op) }
    var muestra = 1
    while (maxOf(op.outWidth, op.outHeight) / (muestra * 2) >= 1568) muestra *= 2
    val b0 = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra }) } ?: error("No se pudo abrir la foto")
    val f = 1568f / maxOf(b0.width, b0.height)
    val b = if (f < 1f) Bitmap.createScaledBitmap(b0, (b0.width * f).toInt(), (b0.height * f).toInt(), true) else b0
    val out = java.io.ByteArrayOutputStream()
    b.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

/**
 * Meter un partido: rápido (resultado, rival, competición, local o fuera, minutos, goles, asistencias y nota; o, de míster, resultado y
 * goleadores), completo (además, los datos del partido, el rendimiento, lo que pasó y la media) o con una foto de la pantalla de FC (Claude
 * la lee y tú lo revisas antes de guardar).
 */
/**
 * La competición, en un desplegable: las de tu club (de la búsqueda), las que has añadido, «Amistoso» y «Selección», y «Otra…» para
 * escribir una que no está (se añade a la lista).
 */
@Composable
fun ElegirCompeticion(u: Universo, lista: List<String>, valor: String, abierto0: Boolean = false, alCambiar: (String) -> Unit) {
    var abierto by remember { mutableStateOf(abierto0) }
    var otra by remember { mutableStateOf(false) }
    var texto by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Etiqueta("Competición")
        Box {
            Row(Modifier.fillMaxWidth().tocable { abierto = true }.padding(top = 4.dp, bottom = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(valor.ifBlank { "Elige la competición" }, color = if (valor.isBlank()) Muted else Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic,
                    fontSize = 18.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, "Elegir", tint = Muted)
            }
            DropdownMenu(abierto, { abierto = false }, containerColor = Crudo) {
                lista.forEach { c ->
                    DropdownMenuItem(text = { Text(c, color = Charcoal, fontFamily = Body, fontWeight = if (c == valor) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp) },
                        onClick = { alCambiar(c); abierto = false; otra = false })
                }
                DropdownMenuItem(text = { Text("Otra…", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }, onClick = { abierto = false; otra = true })
            }
        }
        Filete(1.5.dp)
        if (otra) {
            Campo("¿Cuál?", texto, "El nombre de la competición") { texto = it.take(60) }
            if (texto.isNotBlank()) BotonRedondo("Añadirla a la lista", Modifier.fillMaxWidth().padding(top = 6.dp)) {
                alCambiar(Motor.anadeCompeticion(u.id, texto)); otra = false; texto = ""
            }
        }
    }
}

/** «Busca las competiciones de tu club»: si aún no están (o son las de tu club de antes). */
@Composable
fun BuscarCompeticiones(u: Universo, modifier: Modifier = Modifier) {
    if (!Motor.faltanCompeticiones(u) || !ClaveApi.hay) return
    val ambito = rememberCoroutineScope()
    val va = EnCurso.ids["k" + u.id]
    var aviso by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth()) {
        Text("Para elegirlas aquí, busca las competiciones que juega ${u.club} esta temporada" + (if (Prueba.activo) " (modo prueba: de ejemplo, gratis)." else " (Sonnet y una o dos búsquedas web: unos céntimos)."),
            color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
        PildoraMarco(va ?: "Buscar sus competiciones", Modifier.padding(top = 6.dp)) {
            if (va != null) return@PildoraMarco
            EnCurso.ids["k" + u.id] = "Buscando sus competiciones…"; aviso = null
            ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.buscaCompeticiones(u.id) }; EnCurso.ids.remove("k" + u.id) }
        }
        aviso?.let { Text(it, color = Acento, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
fun PantPartido(id: String, alVolver: () -> Unit, alGuardado: () -> Unit, comoInicial: Int = 0, desplegable: Boolean = false) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    val u = Carreras.de(id) ?: run { PaginaRevista { RevistaTopBar(alVolver); Nota("Esta carrera ya no está.") }; return }
    val anteriores = remember(id) { Carreras.partidos(id).map { it.second } }
    var como by rememberSaveable { mutableIntStateOf(comoInicial) }
    var competicion by rememberSaveable { mutableStateOf(anteriores.lastOrNull()?.competicion.orEmpty()) }
    var rival by rememberSaveable { mutableStateOf("") }
    var local by rememberSaveable { mutableStateOf(true) }
    var gf by rememberSaveable { mutableStateOf("") }
    var gc by rememberSaveable { mutableStateOf("") }
    var minutos by rememberSaveable { mutableStateOf("90") }
    var goles by rememberSaveable { mutableStateOf("") }
    var asist by rememberSaveable { mutableStateOf("") }
    var laNota by rememberSaveable { mutableStateOf("") }
    var goleadores by rememberSaveable { mutableStateOf("") }
    var rendimiento by rememberSaveable { mutableStateOf("") }
    var momentos by rememberSaveable { mutableStateOf("") }
    var titulo by rememberSaveable { mutableStateOf("") }
    var media by rememberSaveable { mutableStateOf("") }
    var temporada by rememberSaveable { mutableStateOf(u.temporada) }
    var fecha by rememberSaveable { mutableStateOf("") }
    val nuestro = remember { mutableStateMapOf<String, String>() }
    val suyo = remember { mutableStateMapOf<String, String>() }
    var ocupado by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var guardado by remember { mutableStateOf(false) }
    var deFoto by rememberSaveable { mutableStateOf(false) }
    var mvp by rememberSaveable { mutableStateOf(false) }

    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        ocupado = "Leyendo la foto…"; aviso = null
        ambito.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Motor.foto(id, fotoParaClaude(ctx, uri), "image/jpeg") } }
            ocupado = null
            r.onFailure { aviso = Motor.mensaje(it) }.onSuccess { f ->
                if (!f.seVe) { aviso = "En esa foto no se ve una pantalla de estadísticas de FC." + (if (f.dudas.isNotBlank()) " ${f.dudas}" else ""); return@onSuccess }
                gf = f.golesFavor.toString(); gc = f.golesContra.toString()
                if (f.rival.isNotBlank()) rival = f.rival
                if (f.minutos > 0) minutos = f.minutos.toString()
                if (f.goles > 0) goles = f.goles.toString()
                if (f.asistencias > 0) asist = f.asistencias.toString()
                if (f.nota > 0) laNota = Formato.decimal(f.nota)
                f.equipo.forEach { (k, v) ->
                    val partes = v.split('/', '-', '–').map { it.trim() }
                    val clave = Partido.DATOS_EQUIPO.firstOrNull { it.equals(k, true) } ?: k
                    nuestro[clave] = partes.getOrElse(0) { v }; if (partes.size > 1) suyo[clave] = partes[1]
                }
                if (f.rendimiento.isNotBlank()) rendimiento = f.rendimiento
                if (f.mvp) mvp = true
                deFoto = true; como = 1
                aviso = "Revisa lo leído antes de guardar." + (if (f.dudas.isNotBlank()) " Claude no está seguro de esto: ${f.dudas}" else "")
            }
        }
    }

    PaginaRevista {
        RevistaTopBar(alVolver, u.nombre)
        Antetitulo("Jornada ${u.paso + 1}", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle("El partido", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
        Filete(3.dp)
        MagChoice(listOf("Rápido", "Completo", "Con una foto"), como, "Cómo lo metes") { i ->
            if (i == 2) { elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } else como = i
        }
        if (como == 2 || ocupado == "Leyendo la foto…") Cuerpo("Elige una foto o una captura de «Datos del partido» o de «Rendimiento» de FC 26. Claude (Haiku) la lee y rellena la plantilla; tú lo revisas.", Modifier.padding(top = 4.dp))
        if (deFoto) Cuerpo("Relleno con la foto. Revísalo.", Modifier.padding(top = 4.dp), color = Acento)

        BloqueEditor("El resultado")
        val lista = Competiciones.lista(u, anteriores)
        ElegirCompeticion(u, lista, competicion, desplegable) { competicion = it }
        BuscarCompeticiones(u, Modifier.padding(top = 6.dp))
        val conSeleccion = Competiciones.deSelecciones(competicion)
        val tuEquipo = if (conSeleccion) u.personaje.suSeleccion.ifBlank { "tu selección" } else u.club
        if (conSeleccion) Tarjeta(Modifier.padding(top = 8.dp), bordeIzq = Acento) {
            TextoTarjeta("Con la selección${u.personaje.suSeleccion.takeIf { it.isNotBlank() }?.let { " de $it" } ?: ""}, no con ${u.club}: la prensa, la afición y la red serán las de tu país, " +
                "y en tu club lo seguirán desde lejos. No cobras la prima por gol de tu club." + (if (u.personaje.suSeleccion.isBlank()) " (Pon tu nacionalidad o tu selección en el perfil.)" else ""), color = Charcoal)
        }
        Campo("Rival", rival, if (conSeleccion) "Qué selección" else "Contra quién") { rival = it }
        MagChoice(listOf("En casa", "Fuera"), if (local) 0 else 1, "Dónde") { local = it == 0 }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Campo("Goles de $tuEquipo", gf, "0", Modifier.weight(1f), teclado = KeyboardType.Number) { gf = it.filter { c -> c.isDigit() }.take(2) }
            Campo("Del rival", gc, "0", Modifier.weight(1f), teclado = KeyboardType.Number) { gc = it.filter { c -> c.isDigit() }.take(2) }
        }
        if (u.modo == Rol.JUGADOR) {
            BloqueEditor("Tu partido")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Campo("Minutos", minutos, "90", Modifier.weight(1f), teclado = KeyboardType.Number) { minutos = it.filter { c -> c.isDigit() }.take(3) }
                Campo("Nota", laNota, "7,5", Modifier.weight(1f), teclado = KeyboardType.Decimal) { laNota = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(4) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Campo("Goles", goles, "0", Modifier.weight(1f), teclado = KeyboardType.Number) { goles = it.filter { c -> c.isDigit() }.take(2) }
                Campo("Asistencias", asist, "0", Modifier.weight(1f), teclado = KeyboardType.Number) { asist = it.filter { c -> c.isDigit() }.take(2) }
            }
            Spacer(Modifier.height(4.dp))
            SiNo("He sido el MVP", "El jugador del partido: cuenta en tus cifras, tus hitos y tu fama", mvp) { mvp = it }
        } else Campo("Goleadores", goleadores, "Quién marcó (y el minuto, si quieres)", lineas = 2) { goleadores = it }

        if (como == 1) {
            BloqueEditor("Datos del partido")
            Cuerpo("Los de la pantalla «Datos del partido» de FC: el de ${u.club} y el del rival. Los que dejes vacíos no cuentan.", tam = 12)
            Partido.DATOS_EQUIPO.forEach { d ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                    Campo(d, nuestro[d].orEmpty(), u.club.take(14), Modifier.weight(1f)) { nuestro[d] = it.take(8) }
                    Campo("", suyo[d].orEmpty(), "rival", Modifier.weight(1f)) { suyo[d] = it.take(8) }
                }
            }
            BloqueEditor("Lo demás")
            if (u.modo == Rol.JUGADOR) Campo("Más del rendimiento", rendimiento, "Tiros, pases, regates… con tus palabras", lineas = 3) { rendimiento = it }
            Campo("Lo que pasó", momentos, "El golazo, la remontada, la tangana…", lineas = 3) { momentos = it }
            if (u.modo == Rol.JUGADOR) Campo("Tu media ahora en FC", media, if (u.personaje.media > 0) "${u.personaje.media}" else "Si ha cambiado", teclado = KeyboardType.Number) { media = it.filter { c -> c.isDigit() }.take(2) }
            Campo("Temporada", temporada, "2026/27") { temporada = it }
        }
        // La fecha del calendario de FC (en las dos plantillas): con ella y la de nacimiento, la edad exacta para los récords
        val estimada = remember(id) { Motor.fechaEstimada(id) }
        Campo("Fecha del partido en FC", fecha, estimada?.let { "Si no la pones: ${Fechas.texto(it)} (la anterior + 7 días)" } ?: "dd/mm/aaaa, la del calendario del juego") { fecha = it.take(20) }
        if (fecha.isNotBlank() && Fechas.lee(fecha) == null) Nota("No entiendo esa fecha: ponla como 24/08/2026.", color = Acento)
        else if (fecha.isBlank() && estimada == null) Text("Con poner la del primer partido, FcFeed calcula las siguientes (una semana después). Sirve para los récords de edad.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        Campo("¿Has ganado un título con este partido?", titulo, "LaLiga, Copa del Rey… (si no, déjalo vacío)") { titulo = it }
        Spacer(Modifier.height(22.dp))
        val listo = rival.isNotBlank() && gf.isNotBlank() && gc.isNotBlank()
        AccionGrande(null, ocupado ?: if (guardado) "Ver la carrera" else "Guardar el partido", Modifier.fillMaxWidth(), enabled = (listo || guardado) && ocupado == null) {
            if (guardado) { alGuardado(); return@AccionGrande }
            if (!listo || ocupado != null) return@AccionGrande
            val equipo = Partido.DATOS_EQUIPO.mapNotNull { d ->
                val a = nuestro[d].orEmpty().trim(); val b = suyo[d].orEmpty().trim()
                if (a.isBlank() && b.isBlank()) null else d to listOf(a.ifBlank { "?" }, b.ifBlank { "?" }).joinToString(" / ")
            }.toMap()
            val p = Partido(temporada.trim().ifBlank { u.temporada }, competicion.trim(), rival.trim(), local, gf.toIntOrNull() ?: 0, gc.toIntOrNull() ?: 0,
                if (u.modo == Rol.JUGADOR) minutos.toIntOrNull() ?: 0 else 90, goles.toIntOrNull() ?: 0, asist.toIntOrNull() ?: 0, nota(laNota), goleadores.trim(),
                if (como == 1) equipo else emptyMap(), if (como == 1) rendimiento.trim() else "", if (como == 1) momentos.trim() else "",
                if (como == 1) media.toIntOrNull() ?: 0 else 0, Fechas.lee(fecha)?.let { Fechas.iso(it) } ?: "", if (deFoto) "foto" else if (como == 1) "completa" else "rapida", titulo.trim(),
                mvp = u.modo == Rol.JUGADOR && mvp)
            ocupado = "Escribiendo la prensa…"; aviso = null
            ambito.launch {
                val r = withContext(Dispatchers.IO) { runCatching { Motor.partido(id, p) } }
                ocupado = null
                Eventos.programa(ctx)
                r.onFailure { aviso = Motor.mensaje(it) }.onSuccess { (_, fallo) ->
                    if (fallo == null) alGuardado() else { guardado = true; aviso = "El partido está guardado, pero falta algo de lo de después: $fallo Lo puedes volver a pedir desde la carrera." }
                }
            }
        }
        if (!listo && !guardado) Text("Falta el rival y el resultado.", color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(10.dp))
        Text("Tras guardar: la red y los mensajes (Haiku), la prensa, las ofertas y las decisiones (Sonnet), y después, por lotes y a mitad de precio, dos cosas que pasan mientras tanto.",
            color = Muted, fontFamily = Body, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    }
}
