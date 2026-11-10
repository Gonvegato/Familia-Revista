package com.gonzalo.shai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Perfil y Cifras (0.5.0), con el lenguaje de la 0.4.0: tarjetas, jerarquía clara, cifras grandes, barras y un gráfico sencillo.
 */

// ---------------------------------------------------------------- piezas

/** Una barra redonda (de 0 a 1). */
@Composable
fun Barra(f: Float, modifier: Modifier = Modifier, color: Color = Acento, alto: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(alto).clip(RoundedCornerShape(50)).background(lerp(Paper, Charcoal, 0.16f))) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

/** Un valor de 0 a 100 con su barra («Fama · 34»). */
@Composable
fun BarraValor(nombre: String, v: Int, color: Color = Acento) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(nombre, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
            Text("$v", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp)
        }
        Barra(v / 100f, Modifier.padding(top = 4.dp), color)
    }
}

/** Una píldora de dato («17 años y 30 días», «DC», «#9»). */
@Composable
fun Dato(t: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(FondoBurbuja).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(t, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/** El título de una sección de Perfil o de Cifras. */
@Composable
private fun Seccion(t: String, extra: String? = null) {
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Rotulo(t, Modifier.weight(1f))
        if (extra != null) Text(extra, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
    }
    Spacer(Modifier.height(8.dp))
}

/** Un número grande con su nombre debajo, centrado (las cifras del resumen). */
@Composable
private fun CifraGrande(valor: String, nombre: String, modifier: Modifier = Modifier, color: Color = Charcoal) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FitTitle(valor, 30.sp, 34.sp, Modifier.fillMaxWidth(), color = color, unaLinea = true, minSize = 14f, fontWeight = FontWeight.Bold, align = TextAlign.Center)
        Text(nombre, color = Muted, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Lo que va en una fila de dos columnas («Sueldo a la semana · 18.000 €»). */
@Composable
private fun Par(nombre: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(nombre, color = Muted, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
        Text(valor, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp)
    }
}

/** V, E o D en un círculo de su color. */
@Composable
private fun Letra(l: String, tam: Dp = 26.dp) {
    val c = when (l) { "V" -> Acento; "D" -> Coral; else -> lerp(Paper, Charcoal, 0.4f) }
    Box(Modifier.size(tam).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
        Text(l, color = if (l == "V") SobreAcento else Color.White, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = (tam.value * 0.46f).sp)
    }
}

/** Su edad exacta (con la fecha de nacimiento y la del último partido) o sus años. */
fun edadExacta(u: Universo): String {
    val nac = Fechas.lee(u.personaje.nacimiento) ?: return "${u.personaje.edad} años"
    val f = Carreras.partidos(u.id).lastOrNull()?.second?.let { Fechas.lee(it.fecha) } ?: return "${u.personaje.edad} años"
    val (a, d) = Fechas.edad(nac, f)
    return Fechas.textoEdad(a, d)
}

// ---------------------------------------------------------------- Perfil

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabPerfil(u: Universo, alBorrada: () -> Unit, alChat: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    var editando by rememberSaveable { mutableStateOf(false) }
    var borrar by remember { mutableStateOf(false) }
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) runCatching { Fotos.guarda(ctx, u.id, uri) } }
    val pj = u.personaje
    if (editando) {
        Rotulo("Editar tu perfil"); Spacer(Modifier.height(8.dp))
        Tarjeta { EditarCarta(u) { editando = false } }
        return
    }
    // La ficha: foto, nombre, datos, club y nivel
    val foto = Fotos.carga(ctx, u.id)
    Tarjeta {
        Row {
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(colorDe(pj.nombre)).tocable { elegir.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center) {
                if (foto != null) Image(foto.asImageBitmap(), "Foto", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(iniciales(pj.nombre), color = Color(0xFF14201A), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 38.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(pj.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 27.sp)
                if (pj.apodo.isNotBlank()) Cursiva("«${pj.apodo}»", tam = 15)
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dato(edadExacta(u))
                    if (pj.nacionalidad.isNotBlank()) Dato(pj.nacionalidad)
                    if (pj.seleccion.isNotBlank() && !Vida.mismo(pj.seleccion, pj.nacionalidad)) Dato("Juega con ${pj.seleccion}")
                    if (pj.altura in 120..230) Dato("${pj.altura / 100},${"%02d".format(pj.altura % 100)} m")
                    if (pj.peso in 35..160) Dato("${pj.peso} kg")
                    if (pj.pie.isNotBlank()) Dato(pj.pie)
                    if (u.modo == Rol.JUGADOR && pj.posicion.isNotBlank()) Dato(pj.posicion)
                    if (pj.dorsal > 0) Dato("#${pj.dorsal}")
                    if (u.modo == Rol.MISTER) Dato("Míster")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(u.clubInfo?.takeIf { it.es(u.club) }?.nombre ?: u.club, 30.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(u.clubInfo?.takeIf { u.clubIdentificado }?.nombre ?: u.club, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp)
                Text(listOf(u.liga, u.temporada, "jornada ${u.paso}").filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (u.libre) Sello("Libre", Coral)
        }
        Filete(1.dp, Line, Modifier.padding(vertical = 12.dp))
        val nv = u.nivelActual
        val fama = u.valorFuera(Fuera.FAMA)
        val sig = Nivel.entries.firstOrNull { it.desde > fama }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sello(nv.nombre)
            Spacer(Modifier.width(8.dp))
            Text("${Alcance.texto(Alcance.seguidores(u))} seguidores", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        }
        Barra(if (sig == null) 1f else (fama - nv.desde).toFloat() / (sig.desde - nv.desde).coerceAtLeast(1), Modifier.padding(top = 10.dp), alto = 8.dp)
        Text(if (sig == null) "Fama $fama/100: lo más alto." else "Fama $fama/100 · para ${sig.nombre.lowercase()}, ${sig.desde} (te faltan ${sig.desde - fama})",
            color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
        if (foto != null) EnlaceRevista("Quitar la foto", Modifier.padding(top = 6.dp)) { Fotos.borra(ctx, u.id) }
    }
    Spacer(Modifier.height(10.dp))
    BotonRedondo("Editar perfil y carta", Modifier.fillMaxWidth(), lleno = false) { editando = true }
    BotonAgente(u, Modifier.padding(top = 8.dp))

    // La carta de FC
    Seccion(if (u.modo == Rol.MISTER) "Tus cifras" else "Tu carta de FC", if (u.modo == Rol.JUGADOR && pj.historial.size > 1) "media: " + pj.historial.takeLast(5).joinToString(" → ") { "${it.second}" } else null)
    CartaFC(u)

    Seccion("Tu club")
    SeccionClub(u)

    Seccion("Tu contrato", "el de FcFeed, no el de FC")
    TarjetaContrato(u)

    Seccion("Fuera del campo")
    Tarjeta {
        Fuera.TODOS.forEach { (k, n) -> BarraValor(n, u.valorFuera(k)) }
        Text("Cómo se te ve: " + Percepcion.de(u, Carreras.partidos(u.id).map { it.second }.filter { it.temporada == u.temporada }) + ".",
            color = Charcoal, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
        Text("Te cubren: ${u.nivelActual.medios}.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        if (u.famaHist.size > 1) { Rotulo("Cómo ha ido tu fama", Modifier.padding(top = 12.dp), color = Muted); val vs = u.famaHist.takeLast(30).map { it.second.toFloat() }
            Linea(vs, (vs.min() - 8f).coerceAtLeast(0f), (vs.max() + 8f).coerceAtMost(100f), Modifier.padding(top = 6.dp))
            Text("De ${vs.first().toInt()} a ${vs.last().toInt()} en ${vs.size - 1} jornadas", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp) }
        Text("Sube poco a poco con lo que haces, con un tope de ${Vida.topeFama(pj.edad)} por jornada a tus ${pj.edad} años (a los 21 o menos un buen tramo pesa mucho más; desde los 28, poco).",
            color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
    }

    TuGente(u, alChat)

    TusRivales(u)

    if (pj.rasgos.isNotBlank() || u.trayectoria.isNotBlank()) {
        Seccion("Quién eres")
        Tarjeta {
            if (pj.rasgos.isNotBlank()) Cursiva(pj.rasgos)
            if (u.trayectoria.isNotBlank()) TextoTarjeta(u.trayectoria, Modifier.padding(top = if (pj.rasgos.isNotBlank()) 6.dp else 0.dp), color = Charcoal)
            u.nivelDe?.takeIf { it != u.nivelActual }?.let { TextoTarjeta("Empezaste como ${it.nombre.lowercase()}.", Modifier.padding(top = 6.dp)) }
        }
    }

    Memoria(u)

    Seccion("Esta carrera")
    Tarjeta {
        SiNo("Lo de entre partidos, solo", "Tras cada partido, dos cosas que pasan mientras tanto (por lotes, a mitad de precio).", u.autoEventos) { Carreras.guarda(u.copy(autoEventos = it)) }
        Spacer(Modifier.height(8.dp))
        BotonRedondo("Borrar esta carrera", Modifier.fillMaxWidth(), lleno = false) { borrar = true }
    }
    if (borrar) AlertDialog(onDismissRequest = { borrar = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Borrar la carrera de ${u.nombre}?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text("Se borra con sus partidos, su prensa y sus mensajes, en todos tus dispositivos. No tiene vuelta atrás.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({ borrar = false; Carreras.borra(u.id); Fotos.borra(ctx, u.id); alBorrada() }) { Text("BORRAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ borrar = false }) { Text("NO", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } })
}

/** «Tu gente» (con los nuevos marcados; al tocar a alguien, su chat) y «Ya no está en tu vida» (con «Que vuelva»). */
@Composable
fun TuGente(u: Universo, alChat: (String) -> Unit = {}) {
    Seccion("Tu gente", if (u.entorno.isNotEmpty()) "${u.entorno.size} · afinidad" else null)
    if (u.entorno.isEmpty()) Tarjeta { TextoTarjeta("Todavía no hay nadie: llegarán con la presentación y con lo que pase.") }
    else Tarjeta {
        TextoTarjeta("Toca a alguien para escribirle lo que quieras (o, si sobra, darle puerta desde su chat). Con más fama, entra gente nueva.", Modifier.padding(bottom = 10.dp))
        u.entorno.sortedWith(compareByDescending<Persona> { Gente.nueva(u, it) }.thenByDescending { it.afinidad }).forEachIndexed { i, p ->
            if (i > 0) Filete(1.dp, Line, Modifier.padding(vertical = 10.dp))
            Row(Modifier.tocable { alChat(p.nombre) }, verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.nombre, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.nombre, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f, fill = false))
                        if (Gente.nueva(u, p)) { Spacer(Modifier.width(6.dp)); Sello("Nuevo") }
                    }
                    Text(p.rol, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
                    if (p.nota.isNotBlank()) Text(p.nota, color = Charcoal, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (p.actitud.isNotBlank()) Text("Ahora: ${p.actitud}", color = Muted, fontFamily = Body, fontStyle = FontStyle.Italic, fontSize = 12.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.width(56.dp), horizontalAlignment = Alignment.End) {
                    Text("${p.afinidad}", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Barra(p.afinidad / 100f, Modifier.padding(top = 4.dp), when { p.afinidad >= 60 -> Acento; p.afinidad < 35 -> Coral; else -> Muted }, 5.dp)
                }
            }
        }
    }

    AnadirCompanero(u)

    if (u.exEntorno.isNotEmpty()) {
        Seccion("Ya no está en tu vida", "${u.exEntorno.size}")
        Tarjeta {
            u.exEntorno.sortedByDescending { it.salio }.forEachIndexed { i, p ->
                if (i > 0) Filete(1.dp, Line, Modifier.padding(vertical = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.nombre, 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.nombre, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp)
                        Text("${p.rol} · le diste puerta en la jornada ${p.salio}" + (if (p.porque.isNotBlank()) ": «${p.porque}»" else ""), color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    PildoraMarco("Que vuelva") { Motor.recupera(u.id, p.id) }
                }
            }
        }
    }

}

/** «Añadir compañero de equipo»: alguien de tu club o de tu selección con quien empiezas a llevarte bien (de verdad o inventado). */
@Composable
fun AnadirCompanero(u: Universo, abierto0: Boolean = false) {
    var abierto by rememberSaveable { mutableStateOf(abierto0) }
    var nombre by rememberSaveable { mutableStateOf("") }
    var deSeleccion by rememberSaveable { mutableStateOf(false) }
    var real by rememberSaveable { mutableStateOf(true) }
    var como by rememberSaveable { mutableStateOf("") }
    val sel = u.personaje.suSeleccion.ifBlank { "tu selección" }
    if (!abierto) { BotonRedondo("Añadir compañero de equipo", Modifier.fillMaxWidth().padding(top = 8.dp), lleno = false) { abierto = true }; return }
    Tarjeta(Modifier.padding(top = 8.dp)) {
        TituloTarjeta("Compañero de equipo", tam = 17)
        TextoTarjeta("Alguien del vestuario con quien empiezas a llevarte bien. Entra en tu gente: te escribirá, saldrá en la red y podrás hablar con él.", Modifier.padding(top = 4.dp))
        Campo("Nombre", nombre, "Como le conoces") { nombre = it.take(60) }
        MagChoice(listOf(u.club, "Selección (${sel})"), if (deSeleccion) 1 else 0, "Dónde") { deSeleccion = it == 1 }
        SiNo("Es un futbolista de verdad", "Entonces la relación es siempre amable o neutra: vestuario, ánimos, bromas, quedar a cenar. Nada de líos, polémicas ni su vida privada.", real) { real = it }
        Campo("Cómo os lleváis (opcional)", como, "«Nos sentamos juntos en el autobús», «me echa un cable con la presión»…", lineas = 2) { como = it.take(200) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonRedondo("Añadir", Modifier.weight(1f)) {
                if (nombre.isBlank()) return@BotonRedondo
                val rol = if (deSeleccion) "Compañero en la selección (${sel})" else "Compañero en el ${u.club}"
                Carreras.guarda(Gente.anade(Carreras.de(u.id) ?: u, listOf(Persona("", nombre.trim(), rol, 60, como.trim(), real = real)), 1))
                nombre = ""; como = ""; abierto = false
            }
            BotonRedondo("Cancelar", Modifier.weight(1f), lleno = false) { abierto = false }
        }
    }
}

/** La carta de FC: la media grande y los atributos en rejilla (o las cifras del míster). */
@Composable
private fun CartaFC(u: Universo) {
    val pj = u.personaje
    val lista = if (u.modo == Rol.MISTER) Cifras.MISTER else Cifras.JUGADOR
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (u.modo == Rol.JUGADOR) {
                Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (pj.media > 0) "${pj.media}" else "—", color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 56.sp)
                    Text(pj.posicion.ifBlank { "MEDIA" }.uppercase(), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.2.sp)
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                lista.chunked(2).forEach { fila ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        fila.forEach { (k, n) ->
                            val v = pj.atributos[k]
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(if (u.modo == Rol.MISTER) n else k, color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                    Text(v?.toString() ?: "—", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp)
                                }
                                Barra((v ?: 0) / 99f, Modifier.padding(top = 3.dp), if ((v ?: 0) >= 80) Acento else lerp(Paper, Charcoal, 0.55f), 4.dp)
                            }
                        }
                        if (fila.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (pj.atributos.isEmpty() && pj.media == 0) TextoTarjeta(if (u.modo == Rol.MISTER) "En FC el míster no tiene carta: pon tus cifras de FcFeed en «Editar perfil y carta»." else "Pon tu media y tus atributos de FC en «Editar perfil y carta».", Modifier.padding(top = 8.dp))
    }
}

/** Tu contrato (el de FcFeed). */
@Composable
private fun TarjetaContrato(u: Universo) {
    val c = u.contrato
    if (c == null) { Tarjeta(bordeIzq = Coral) { TituloTarjeta("Agente libre"); TextoTarjeta("No cobras sueldo de ningún club y cualquiera puede ficharte. Las ofertas llegarán.", Modifier.padding(top = 4.dp)) }; return }
    val q = Contratos.quedan(c, u.temporada)
    Tarjeta(bordeIzq = if (q <= 1) Coral else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TituloTarjeta(c.club, Modifier.weight(1f), tam = 20)
            if (c.nota.isNotBlank()) Sello(c.nota)
        }
        Text("Hasta ${c.hasta} · " + (if (q <= 1) "es la última temporada" else "quedan $q temporadas"), color = if (q <= 1) Coral else Muted, fontFamily = Body, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Par("Sueldo a la semana", Formato.euros(c.sueldo), Modifier.weight(1f))
            Par("Cláusula", if (c.clausula > 0) Formato.eurosCorto(c.clausula) else "—", Modifier.weight(1f))
        }
        if (c.prima > 0 || c.porGol > 0) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Par("Prima de firma", if (c.prima > 0) Formato.euros(c.prima) else "—", Modifier.weight(1f))
            Par("Por cada gol", if (c.porGol > 0) Formato.euros(c.porGol) else "—", Modifier.weight(1f))
        }
        if (c.objetivos.isNotBlank()) TextoTarjeta(c.objetivos, Modifier.padding(top = 10.dp), color = Charcoal)
        if (c.imagen.isNotBlank()) TextoTarjeta(c.imagen, Modifier.padding(top = 4.dp))
        if (u.patrocinio > 0) TextoTarjeta("Patrocinios: ${Formato.euros(u.patrocinio)} a la semana.", Modifier.padding(top = 4.dp))
        TextoTarjeta("De aquí sale lo que cobras cada jornada; el club o tu representante hablarán de renovar cuando toque.", Modifier.padding(top = 8.dp))
    }
}

/** Lo que FcFeed recuerda: plegado, con la jornada en que se escribió. */
@Composable
private fun Memoria(u: Universo) {
    var abierta by rememberSaveable { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()
    var aviso by remember { mutableStateOf<String?>(null) }
    val va = EnCurso.ids["m" + u.id]
    Seccion("Lo que FcFeed recuerda", if (u.memoria.isNotBlank()) "jornada ${u.memoriaPaso}" else null)
    Tarjeta(alHacer = if (u.memoria.isNotBlank()) ({ abierta = !abierta }) else null) {
        if (u.memoria.isBlank()) TextoTarjeta("Todavía nada: cada ${Motor.CADA_MEMORIA} jornadas y al cerrar la temporada, FcFeed resume las tramas abiertas, tu gente y lo que has decidido.")
        else {
            val lineas = u.memoria.lines().filter { it.isNotBlank() }
            (if (abierta) lineas else lineas.take(2)).forEach { l -> Text("• " + l.trimStart('-', '•', ' '), color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 3.dp)) }
            if (lineas.size > 2) Text(if (abierta) "Ocultar" else "Ver todo (${lineas.size})", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        PildoraMarco(va ?: (if (Prueba.activo) "Rehacerlo ahora (gratis en modo prueba)" else "Rehacerlo ahora (un céntimo)")) {
            if (va != null || !ClaveApi.hay) return@PildoraMarco
            EnCurso.ids["m" + u.id] = "Recordando…"
            ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.recuerda(u.id) }; EnCurso.ids.remove("m" + u.id) }
        }
    }
    aviso?.let { Nota(it, Modifier.padding(top = 4.dp), color = Acento) }
}

/** Tus rivales: los que han surgido solos y los que metes tú (si es un futbolista de verdad, la rivalidad solo es deportiva). */
@Composable
fun TusRivales(u: Universo) {
    var anadiendo by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    var club by remember { mutableStateOf("") }
    var real by remember { mutableStateOf(true) }
    Seccion("Tus rivales", if (u.rivales.isNotEmpty()) "${u.rivales.size}" else null)
    if (u.rivales.isEmpty()) Tarjeta { TextoTarjeta("Aún ninguno. Surgen solos: el del club que siempre te marca, el central que te anula, el que compite por tu puesto, el que te disputa el trofeo de goleador…") }
    u.rivales.sortedByDescending { it.intensidad }.forEach { r ->
        Tarjeta(Modifier.padding(bottom = 8.dp), bordeIzq = if (r.intensidad >= 75) Coral else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(r.nombre, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.nombre, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp)
                    Text(listOf(r.club, r.posicion).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
                }
                Sello(r.grado, if (r.intensidad >= 50) Coral else Acento)
            }
            Barra(r.intensidad / 100f, Modifier.padding(top = 10.dp), Coral, 6.dp)
            Text("Intensidad ${r.intensidad}/100" + (if (r.real) " · futbolista de verdad: solo deportiva" else ""), color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
            if (r.origen.isNotBlank()) TextoTarjeta(r.origen, Modifier.padding(top = 6.dp), color = Charcoal)
            r.choques.takeLast(2).reversed().forEach { c -> Text("J${c.paso} · ${c.texto}", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp)) }
            PildoraMarco("Quitar", Modifier.padding(top = 10.dp)) { Carreras.guarda(Rivales.quita(Carreras.de(u.id) ?: u, r.id)) }
        }
    }
    if (!anadiendo) BotonRedondo("Añadir un rival", Modifier.fillMaxWidth().padding(top = 8.dp), lleno = false) { anadiendo = true }
    else Tarjeta(Modifier.padding(top = 8.dp)) {
        Campo("Nombre", nombre, "El rival") { nombre = it.take(60) }
        Campo("Club", club, "Su club") { club = it.take(60) }
        SiNo("Es un futbolista de verdad", "Entonces la rivalidad es solo deportiva: la prensa compara cifras, sin frases ni mal rollo.", real) { real = it }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonRedondo("Añadir", Modifier.weight(1f)) {
                if (nombre.isNotBlank()) Carreras.guarda(Rivales.anade(Carreras.de(u.id) ?: u, nombre, club, real))
                nombre = ""; club = ""; anadiendo = false
            }
            BotonRedondo("Cancelar", Modifier.weight(1f), lleno = false) { anadiendo = false }
        }
    }
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
    var nacionalidad by remember { mutableStateOf(pj.nacionalidad) }
    var seleccion by remember { mutableStateOf(pj.seleccion) }
    var altura by remember { mutableStateOf(if (pj.altura > 0) "${pj.altura}" else "") }
    var peso by remember { mutableStateOf(if (pj.peso > 0) "${pj.peso}" else "") }
    var pie by remember { mutableStateOf(pj.pie) }
    var aspecto by remember { mutableStateOf(pj.aspecto) }
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
    Campo("Nacionalidad", nacionalidad, "España") { nacionalidad = it.take(40) }
    Campo("Selección (si no es la de tu nacionalidad)", seleccion, "La de ${nacionalidad.trim().ifBlank { "tu país" }}") { seleccion = it.take(40) }
    if (u.modo == Rol.JUGADOR) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Campo("Altura (cm)", altura, "183", Modifier.weight(1f), teclado = KeyboardType.Number) { altura = it.filter { c -> c.isDigit() }.take(3) }
            Campo("Peso (kg)", peso, "76", Modifier.weight(1f), teclado = KeyboardType.Number) { peso = it.filter { c -> c.isDigit() }.take(3) }
        }
        MagChoice(listOf("Diestro", "Zurdo", "Ambidiestro"), listOf("Diestro", "Zurdo", "Ambidiestro").indexOf(pie), "Pie bueno") { pie = listOf("Diestro", "Zurdo", "Ambidiestro")[it] }
    }
    Campo("Cómo es por fuera", aspecto, "Moreno, barba corta, tatuajes…", lineas = 3) { aspecto = it.take(300) }
    Campo("Su forma de ser", rasgos, "", lineas = 3) { rasgos = it }
    Campo("Quién es (su historia, su palmarés)", trayectoria, "23 años, Balón de Oro, canterano que debuta…", lineas = 4) { trayectoria = it }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BotonRedondo("Guardar", Modifier.weight(1f)) {
            val m = media.toIntOrNull() ?: 0
            val nuevo = pj.copy(media = m, atributos = cifras.mapValues { it.value.toIntOrNull() ?: 0 }.filterValues { it > 0 }, rasgos = rasgos.trim(), edad = edad.toIntOrNull() ?: pj.edad,
                nacimiento = Fechas.lee(nacimiento)?.let { Fechas.iso(it) } ?: if (nacimiento.isBlank()) "" else pj.nacimiento,
                nacionalidad = nacionalidad.trim(), seleccion = seleccion.trim().takeIf { !Vida.mismo(it, nacionalidad) }.orEmpty(),
                altura = altura.toIntOrNull()?.takeIf { it in 120..230 } ?: 0, peso = peso.toIntOrNull()?.takeIf { it in 35..160 } ?: 0, pie = pie, aspecto = aspecto.trim(),
                historial = if (m > 0 && m != pj.media) (pj.historial + (u.paso to m)).takeLast(60) else pj.historial)
            Carreras.guarda((Carreras.de(u.id) ?: u).copy(personaje = nuevo, trayectoria = trayectoria.trim())); alAcabar()
        }
        BotonRedondo("Cancelar", Modifier.weight(1f), lleno = false) { alAcabar() }
    }
}

// ---------------------------------------------------------------- Cifras

/** Una línea sencilla (la fama, jornada a jornada). */
@Composable
private fun Linea(vs: List<Float>, min: Float, max: Float, modifier: Modifier = Modifier) {
    val tinta = Acento; val rejilla = Line
    Canvas(modifier.fillMaxWidth().height(70.dp)) {
        if (vs.size < 2) return@Canvas
        drawLine(rejilla, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        val p = Path()
        vs.forEachIndexed { i, v ->
            val x = size.width * i / (vs.size - 1); val y = size.height - (v - min) / (max - min) * size.height
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, tinta, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** El gráfico de la temporada: barras de goles (o goles a favor y en contra, de míster) y la nota en línea, jornada a jornada. */
@Composable
private fun Grafico(u: Universo, ps: List<Pair<Entrada, Partido>>) {
    val ult = ps.takeLast(20)
    if (ult.size < 2) return
    val barra = Acento; val contra = Coral; val linea = Charcoal; val rejilla = Line
    val maxG = maxOf(3, ult.maxOf { if (u.modo == Rol.JUGADOR) it.second.goles else maxOf(it.second.golesFavor, it.second.golesContra) })
    val conNota = u.modo == Rol.JUGADOR && ult.any { it.second.nota > 0 }
    Tarjeta {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = ult.size
            val ancho = size.width / n
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { f -> drawLine(rejilla, Offset(0f, size.height * (1 - f)), Offset(size.width, size.height * (1 - f)), 1.dp.toPx()) }
            ult.forEachIndexed { i, (_, p) ->
                val x0 = ancho * i + ancho * 0.18f
                if (u.modo == Rol.JUGADOR) {
                    val h = size.height * p.goles / maxG
                    if (h > 0) drawRoundRect(barra, Offset(x0, size.height - h), Size(ancho * 0.64f, h), CornerRadius(3.dp.toPx()))
                } else {
                    val w = ancho * 0.3f
                    val hf = size.height * p.golesFavor / maxG; val hc = size.height * p.golesContra / maxG
                    if (hf > 0) drawRoundRect(barra, Offset(x0, size.height - hf), Size(w, hf), CornerRadius(3.dp.toPx()))
                    if (hc > 0) drawRoundRect(contra, Offset(x0 + w + ancho * 0.04f, size.height - hc), Size(w, hc), CornerRadius(3.dp.toPx()))
                }
            }
            if (conNota) {
                val path = Path(); var empezado = false
                ult.forEachIndexed { i, (_, p) ->
                    if (p.nota <= 0) return@forEachIndexed
                    val x = ancho * i + ancho / 2; val y = size.height - (p.nota.toFloat() / 10f) * size.height
                    if (!empezado) { path.moveTo(x, y); empezado = true } else path.lineTo(x, y)
                    drawCircle(linea, 3.5.dp.toPx(), Offset(x, y))
                }
                drawPath(path, linea, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("J${ult.first().first.paso}", color = Muted, fontFamily = Body, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("J${ult.last().first.paso}", color = Muted, fontFamily = Body, fontSize = 11.sp)
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(barra)); Spacer(Modifier.width(6.dp))
            Text(if (u.modo == Rol.JUGADOR) "Goles (hasta $maxG)" else "A favor", color = Charcoal, fontFamily = Body, fontSize = 12.sp)
            Spacer(Modifier.width(14.dp))
            if (u.modo == Rol.MISTER) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(contra)); Spacer(Modifier.width(6.dp)); Text("En contra", color = Charcoal, fontFamily = Body, fontSize = 12.sp) }
            else if (conNota) { Box(Modifier.size(10.dp).clip(CircleShape).background(linea)); Spacer(Modifier.width(6.dp)); Text("Nota (de 0 a 10)", color = Charcoal, fontFamily = Body, fontSize = 12.sp) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabCifras(u: Universo) {
    val todos = Carreras.partidos(u.id)
    if (todos.isEmpty()) { Tarjeta { TituloTarjeta("Sin partidos todavía"); TextoTarjeta("Mete tu primer partido y aquí verás tus cifras, tus récords y cómo va la temporada.", Modifier.padding(top = 4.dp)) }; return }
    val temporadas = todos.map { it.second.temporada }.filter { it.isNotBlank() }.distinct()
    var temp by rememberSaveable { mutableStateOf(u.temporada.takeIf { it in temporadas } ?: "") }
    var comp by rememberSaveable { mutableStateOf("") }
    val deTemp = if (temp.isBlank()) todos else todos.filter { it.second.temporada == temp }
    val comps = deTemp.map { it.second.competicion }.filter { it.isNotBlank() }.distinct()
    if (comp.isNotBlank() && comp !in comps) comp = ""
    val lista = if (comp.isBlank()) deTemp else deTemp.filter { it.second.competicion == comp }
    // Qué se ve: la temporada y la competición
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipFiltro("Toda la carrera", temp.isBlank()) { temp = "" }
        temporadas.forEach { t -> ChipFiltro(t, temp == t) { temp = t } }
    }
    if (comps.size > 1) {
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipFiltro("Todas", comp.isBlank()) { comp = "" }
            comps.forEach { c -> ChipFiltro(c, comp == c) { comp = c } }
        }
    }
    val ps = lista.map { it.second }
    val l = Estadisticas.linea(temp, comp, ps)
    Seccion(listOf(temp.ifBlank { "Toda la carrera" }, comp).filter { it.isNotBlank() }.joinToString(" · "))
    Tarjeta {
        Row(Modifier.fillMaxWidth()) {
            CifraGrande("${l.jugados}", "partidos", Modifier.weight(1f))
            if (u.modo == Rol.JUGADOR) {
                CifraGrande("${l.goles}", "goles", Modifier.weight(1f), Acento)
                CifraGrande("${l.asistencias}", "asist.", Modifier.weight(1f))
                CifraGrande(if (l.notaMedia > 0) Formato.decimal(l.notaMedia) else "—", "nota", Modifier.weight(1f))
            } else {
                CifraGrande("${l.victorias}", "victorias", Modifier.weight(1f), Acento)
                CifraGrande("${l.golesFavor}-${l.golesContra}", "goles", Modifier.weight(1f))
                CifraGrande(if (l.jugados > 0) "${l.victorias * 100 / l.jugados} %" else "—", "ganados", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50))) {
            if (l.victorias > 0) Box(Modifier.weight(l.victorias.toFloat()).fillMaxHeight().background(Acento))
            if (l.empates > 0) Box(Modifier.weight(l.empates.toFloat()).fillMaxHeight().background(lerp(Paper, Charcoal, 0.4f)))
            if (l.derrotas > 0) Box(Modifier.weight(l.derrotas.toFloat()).fillMaxHeight().background(Coral))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            fun pl(n: Int, s: String) = "$n $s" + (if (n == 1) "" else "s")
            Text("${pl(l.victorias, "ganado")} · ${pl(l.empates, "empatado")} · ${pl(l.derrotas, "perdido")}", color = Charcoal, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        }
        if (u.modo == Rol.JUGADOR && l.minutos > 0) Text("${Formato.cifra(l.minutos)} minutos" + (if (l.goles > 0) " · un gol cada ${l.minutos / l.goles}'" else ""), color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
        if (u.modo == Rol.JUGADOR && l.mvps > 0) Text("🏅 MVP del partido ${if (l.mvps == 1) "1 vez" else "${l.mvps} veces"}", color = Acento, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Últimos", color = Muted, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            ps.takeLast(6).forEach { Letra(it.letra, 24.dp); Spacer(Modifier.width(5.dp)) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Grafico(u, lista)
    // Por competición
    if (comp.isBlank() && comps.size > 1) {
        Seccion("Por competición")
        Tarjeta {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Text("Competición", color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                (if (u.modo == Rol.JUGADOR) listOf("PJ", "G", "A", "MVP", "Nota") else listOf("PJ", "V", "E", "D")).forEach { h ->
                    Text(h, color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(if (h == "Nota" || h == "MVP") 40.dp else 30.dp))
                }
            }
            comps.forEach { c ->
                val x = Estadisticas.linea(temp, c, deTemp.map { it.second }.filter { it.competicion == c })
                Filete(1.dp, Line)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                    val vals = if (u.modo == Rol.JUGADOR) listOf("${x.jugados}", "${x.goles}", "${x.asistencias}", "${x.mvps}", if (x.notaMedia > 0) Formato.decimal(x.notaMedia) else "—")
                        else listOf("${x.jugados}", "${x.victorias}", "${x.empates}", "${x.derrotas}")
                    vals.forEachIndexed { i, v -> Text(v, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.width(if (u.modo == Rol.JUGADOR && i >= 3) 40.dp else 30.dp)) }
                }
            }
        }
    }
    RecordsEHitos(u, todos.map { it.second })
    RecordsHistoricos(u, todos.map { it.second })
    // Los partidos (al tocar uno, se corrige su competición o lo del MVP)
    var corrige by remember { mutableStateOf<String?>(null) }
    Seccion("Los partidos", "${lista.size}")
    Tarjeta {
        TextoTarjeta("Toca un partido para corregir su competición" + (if (u.modo == Rol.JUGADOR) " o si fuiste el MVP." else "."), Modifier.padding(bottom = 8.dp))
        lista.reversed().take(40).forEachIndexed { i, (e, p) ->
            if (i > 0) Filete(1.dp, Line, Modifier.padding(vertical = 8.dp))
            Row(Modifier.tocable { corrige = e.id }, verticalAlignment = Alignment.CenterVertically) {
                Letra(p.letra, 30.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${if (p.local) "vs" else "en"} ${p.rival} · ${p.resultado}", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp)
                    Text(listOfNotNull("J${e.paso}", p.competicion.ifBlank { null }, p.fecha.takeIf { it.isNotBlank() }?.let { Fechas.bonita(it) + (if (p.fechaEstimada) " (estimada)" else "") },
                        if (u.modo == Rol.JUGADOR) "${p.minutos}'" + (if (p.goles > 0) " · ${p.goles} G" else "") + (if (p.asistencias > 0) " · ${p.asistencias} A" else "") else p.goleadores.ifBlank { null }).joinToString(" · "),
                        color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
                    if (p.titulo.isNotBlank()) Text("🏆 ${p.titulo}", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
                    if (p.mvp) Text("🏅 MVP", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
                }
                if (p.nota > 0) { Spacer(Modifier.width(8.dp)); Text(Formato.decimal(p.nota), color = if (p.nota >= 8) Acento else Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
            }
        }
        if (lista.size > 40) TextoTarjeta("Y ${lista.size - 40} más.", Modifier.padding(top = 8.dp))
    }
    corrige?.let { id -> todos.firstOrNull { it.first.id == id }?.let { (e, p) -> CorrigePartido(u, e, p) { corrige = null } } }
    Spacer(Modifier.height(16.dp))
    CerrarTemporada(u)
}

/** Corregir un partido ya guardado: su competición (de la lista) y si fue el MVP. */
@Composable
fun CorrigePartido(u: Universo, e: Entrada, p: Partido, alCerrar: () -> Unit) {
    var comp by remember(e.id) { mutableStateOf(p.competicion) }
    var mvp by remember(e.id) { mutableStateOf(p.mvp) }
    val lista = Competiciones.lista(u, Carreras.partidos(u.id).map { it.second })
    AlertDialog(onDismissRequest = alCerrar, containerColor = Paper, shape = RoundedCornerShape(16.dp),
        title = { Text("${if (p.local) "vs" else "en"} ${p.rival} · ${p.resultado}", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 23.sp) },
        text = { Column {
            if (p.competicionOriginal.isNotBlank() && p.competicionOriginal != p.competicion) Text("Lo metiste como «${p.competicionOriginal}».", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
            ElegirCompeticion(u, lista, comp) { comp = it }
            if (u.modo == Rol.JUGADOR) SiNo("Fui el MVP", null, mvp) { mvp = it }
        } },
        confirmButton = { TextButton({ Motor.corrigePartido(e.id, comp, mvp); alCerrar() }) { Text("GUARDAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.sp) } },
        dismissButton = { TextButton(alCerrar) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.sp) } })
}

/** El botón de cerrar la temporada (con su confirmación): balance, premios, vacaciones y mercado de verano. */
@Composable
fun CerrarTemporada(u: Universo, preguntando0: Boolean = false) {
    val ambito = rememberCoroutineScope()
    var preguntando by remember { mutableStateOf(preguntando0) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val va = EnCurso.ids["t" + u.id]
    val t = u.temporada.ifBlank { "esta temporada" }
    Tarjeta(borde = Acento) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.EmojiEvents, null, tint = Acento, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(10.dp))
            TituloTarjeta("Cerrar la temporada $t", Modifier.weight(1f))
        }
        TextoTarjeta("Balance con tus cifras, premios de medios inventados, tus vacaciones y el mercado de verano; luego empieza la siguiente, con un año más.", Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp))
        BotonRedondo(va ?: "Cerrar la temporada", Modifier.fillMaxWidth()) { if (va == null) preguntando = true }
    }
    aviso?.let { Nota(it, Modifier.padding(top = 4.dp), color = Acento) }
    if (preguntando) AlertDialog(onDismissRequest = { preguntando = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Cerrar la temporada $t?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text("FcFeed escribe el balance con tus cifras, los premios (de medios inventados), tus vacaciones y el mercado de verano, y pasas a ${Motor.siguienteTemporada(u.temporada).ifBlank { "la siguiente" }} con un año más." +
            (u.contrato?.takeIf { Contratos.quedan(it, u.temporada) <= 1 }?.let { " Tu contrato con el ${it.club} acaba con esta temporada: si no has renovado, quedarás libre." } ?: "") +
            " No se puede deshacer.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({
            preguntando = false; EnCurso.ids["t" + u.id] = "Cerrando la temporada…"; aviso = null
            ambito.launch { aviso = withContext(Dispatchers.IO) { Motor.cierraTemporada(u.id) }; EnCurso.ids.remove("t" + u.id) }
        }) { Text("CERRARLA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ preguntando = false }) { Text("AÚN NO", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } })
}

/** Récords e hitos: los de tu carrera en FcFeed (con tus cifras). */
@Composable
fun RecordsEHitos(u: Universo, ps: List<Partido>) {
    val r = Hitos.records(u, ps)
    Seccion("Récords e hitos", "de tu carrera en FcFeed")
    // (de dos en dos: con cuatro en fila, «seguidos marcando» no cabe)
    val cifras = if (u.modo == Rol.JUGADOR) listOf("${r.goles}" to "goles", "${r.hatTricks}" to (if (r.hatTricks == 1) "hat-trick" else "hat-tricks"),
        "${r.rachaMarcando}" to "partidos seguidos marcando", "${r.rachaInvicto}" to "partidos seguidos sin perder")
        else listOf("${r.victorias}" to "victorias", "${r.partidos}" to "partidos", "${r.rachaInvicto}" to "partidos seguidos sin perder", "${r.titulos.size}" to "títulos")
    cifras.chunked(2).forEachIndexed { i, fila ->
        if (i > 0) Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fila.forEachIndexed { j, (v, n) -> Contador(v, n, Modifier.weight(1f).fillMaxHeight(), destacado = i == 0 && j == 0) }
        }
    }
    val extras = listOfNotNull(
        if (u.modo == Rol.JUGADOR && r.masGolesPartido > 0) "Más goles en un partido" to "${r.masGolesPartido}" else null,
        r.mejorTemporada?.let { "Mejor temporada" to "${it.first} (${it.second}${if (u.modo == Rol.JUGADOR) " goles" else " victorias"})" },
        if (r.titulos.isNotEmpty()) "Títulos" to r.titulos.joinToString(", ") else null,
    )
    val hitos = u.hitos.filter { it.real == 0 }.reversed().take(10)
    if (extras.isNotEmpty() || hitos.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Tarjeta {
            extras.forEach { (n, v) -> Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(n, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f)); Spacer(Modifier.width(8.dp))
                Text(v, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
            } }
            hitos.forEachIndexed { i, h ->
                if (i > 0 || extras.isNotEmpty()) Filete(1.dp, Line, Modifier.padding(vertical = 10.dp))
                Row {
                    Text(if (h.precoz) "★" else "•", color = Acento, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.titulo, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp)
                        Text(h.texto, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
                        Text(listOfNotNull("J${h.paso}", h.temporada.ifBlank { null }, "${h.edad} años", if (h.precoz) "precoz" else null).joinToString(" · "),
                            color = if (h.precoz) Acento else Muted, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

/** Los récords del fútbol de verdad (los de tu lista): los que ya son tuyos, los que tienes a tiro (con su cuenta atrás) y la lista entera. */
@Composable
fun RecordsHistoricos(u: Universo, ps: List<Partido>, abierta0: Boolean = false) {
    var abierta by rememberSaveable { mutableStateOf(abierta0) }
    Seccion("Récords históricos", "tu lista de 200")
    u.recordsSuyos.sortedByDescending { it.paso }.forEach { s ->
        val r = RecordsReales.de(s.n) ?: return@forEach
        Tarjeta(Modifier.padding(bottom = 8.dp), borde = Acento) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EmojiEvents, null, tint = Acento, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
                Text(r.record, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
            }
            TextoTarjeta((if (s.iguala) "Lo igualas" else "Ahora es tuyo") + ": ${s.marca}. Lo tenía ${r.quien} (${r.marca}).", Modifier.padding(top = 6.dp))
            Text(listOfNotNull("J${s.paso}", s.temporada.ifBlank { null }, "#${r.n}").joinToString(" · "), color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
    val cerca = u.hitos.filter { it.real > 0 && it.id.contains("_cerca_") }.takeLast(4).reversed()
    val tiro = Historicos.aTiro(u, ps)
    if (cerca.isNotEmpty() || tiro.isNotEmpty()) Tarjeta(Modifier.padding(bottom = 8.dp)) {
        cerca.forEachIndexed { i, h ->
            if (i > 0) Filete(1.dp, Line, Modifier.padding(vertical = 10.dp))
            Text(h.titulo, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp)
            Text(h.texto, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (tiro.isNotEmpty()) {
            if (cerca.isNotEmpty()) Filete(1.dp, Line, Modifier.padding(vertical = 10.dp))
            Rotulo("A tiro", color = Muted)
            tiro.take(8).forEach { c ->
                val urg = Historicos.urgencia(u, c)
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, null, tint = if (urg == Historicos.Urgencia.ALTA) Coral else Acento, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Historicos.queda(c) + (if (c.quedanDias >= 0) " para el récord de " else " le faltan para el récord de ") + c.r.quien.substringBefore(" (").substringBefore(" –"),
                            color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
                        Text(c.r.record + " · " + c.r.marca + (if (c.plazo.isNotBlank()) " · hasta el ${c.plazo}" else ""), color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    if (urg == Historicos.Urgencia.ALTA) { Spacer(Modifier.width(8.dp)); Sello(if (c.quedanDias in 0..7) "Última" else "Ya casi", Coral) }
                }
            }
        }
    }
    if (u.recordsSuyos.isEmpty() && cerca.isEmpty() && tiro.isEmpty()) Tarjeta(Modifier.padding(bottom = 8.dp)) { TextoTarjeta("Aún ninguno a tiro: cuando te acerques a uno de tu lista, aquí verás cuánto te falta.") }
    if (Fechas.lee(u.personaje.nacimiento) == null && u.modo == Rol.JUGADOR)
        Nota("Pon tu fecha de nacimiento en FC (Perfil → Editar perfil y carta) y la fecha de los partidos para compararte con los récords de edad.", Modifier.padding(bottom = 8.dp))
    BotonRedondo(if (abierta) "Ocultar la lista" else "Ver los 200 récords", Modifier.fillMaxWidth(), lleno = false) { abierta = !abierta }
    if (abierta) RecordReal.CATEGORIAS.forEachIndexed { i, nombre ->
        Spacer(Modifier.height(10.dp))
        Tarjeta {
            TituloTarjeta(nombre, tam = 17)
            RecordsReales.TODOS.filter { it.categoria == i + 1 }.forEach { r ->
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("${r.n}. ${r.record}", color = if (r.dudoso) Muted else Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp)
                    Text("${r.quien} · ${r.marca}", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
                    if (r.dudoso || r.nota.isNotBlank()) Text((if (r.dudoso) "Dudoso: no cuenta. " else "") + r.nota, color = Acento, fontFamily = Body, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}
