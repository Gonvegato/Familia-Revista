package com.gonzalo.shai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Un número de lo escrito («1.200.000» o «1200000» → 1200000). */
fun numero(t: String): Long = t.filter { it.isDigit() }.take(13).toLongOrNull() ?: 0L

/** Empezar una carrera: quién eres, dónde juegas y con qué cifras empiezas (las de tu partida de FC). */
@Composable
fun PantNueva(alVolver: () -> Unit, alCreada: (String) -> Unit) {
    val ambito = rememberCoroutineScope()
    var modo by rememberSaveable { mutableStateOf(Rol.JUGADOR) }
    var nombre by rememberSaveable { mutableStateOf("") }
    var apodo by rememberSaveable { mutableStateOf("") }
    var edad by rememberSaveable { mutableStateOf("18") }
    var nacionalidad by rememberSaveable { mutableStateOf("España") }
    var posicion by rememberSaveable { mutableStateOf("") }
    var dorsal by rememberSaveable { mutableStateOf("") }
    var club by rememberSaveable { mutableStateOf("") }
    var liga by rememberSaveable { mutableStateOf("") }
    var temporada by rememberSaveable { mutableStateOf("2026/27") }
    var salario by rememberSaveable { mutableStateOf("") }
    var dinero by rememberSaveable { mutableStateOf("") }
    var media by rememberSaveable { mutableStateOf("") }
    var rasgos by rememberSaveable { mutableStateOf("Ambicioso, fiestero y mujeriego") }
    val cifras = remember { mutableStateMapOf<String, String>() }
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var creada by remember { mutableStateOf<String?>(null) }

    PaginaRevista {
        RevistaTopBar(alVolver, "Shai")
        Antetitulo("Nueva carrera", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle("Empieza tu historia", 44.sp, 48.sp, letterSpacing = (-1.2).sp, modifier = Modifier.padding(top = 2.dp))
        Filete(3.dp)
        Text("Pon lo de tu partida de FC tal cual: Shai no se inventa nada del campo.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic,
            fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
        BloqueEditor("Tu carrera")
        MagChoice(Rol.entries.map { it.nombre }, modo.ordinal, "Modo carrera") { modo = Rol.entries[it] }
        Campo(if (modo == Rol.MISTER) "Nombre del míster" else "Nombre del jugador", nombre, "Nombre y apellidos") { nombre = it }
        Campo("Apodo (si tiene)", apodo, "Como le llama la grada") { apodo = it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Campo("Edad", edad, "18", Modifier.weight(1f), teclado = KeyboardType.Number) { edad = it.filter { c -> c.isDigit() }.take(2) }
            Campo("Nacionalidad", nacionalidad, "España", Modifier.weight(2f)) { nacionalidad = it }
        }
        if (modo == Rol.JUGADOR) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Campo("Posición", posicion, "DC, MCO, LD…", Modifier.weight(2f)) { posicion = it }
            Campo("Dorsal", dorsal, "9", Modifier.weight(1f), teclado = KeyboardType.Number) { dorsal = it.filter { c -> c.isDigit() }.take(2) }
        }
        BloqueEditor("Dónde")
        Campo("Club", club, "El de tu partida") { club = it }
        Campo("Liga", liga, "LaLiga, Premier League…") { liga = it }
        Campo("Temporada", temporada, "2026/27") { temporada = it }
        Campo("Sueldo (euros a la semana)", salario, "El de tu contrato en FC", teclado = KeyboardType.Number) { salario = it.filter { c -> c.isDigit() } }
        Campo("Dinero en el banco (euros)", dinero, "Lo que tiene ahora", teclado = KeyboardType.Number) { dinero = it.filter { c -> c.isDigit() } }
        BloqueEditor(if (modo == Rol.MISTER) "Tus cifras" else "Tu carta de FC")
        if (modo == Rol.JUGADOR) Campo("Media", media, "La de tu jugador en FC", teclado = KeyboardType.Number) { media = it.filter { c -> c.isDigit() }.take(2) }
        val lista = if (modo == Rol.MISTER) Cifras.MISTER else Cifras.JUGADOR
        lista.chunked(3).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                fila.forEach { (k, n) -> Campo(n, cifras[k].orEmpty(), "—", Modifier.weight(1f), teclado = KeyboardType.Number) { cifras[k] = it.filter { c -> c.isDigit() }.take(2) } }
                repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(if (modo == Rol.MISTER) "En FC el míster no tiene carta: estas cifras son de Shai (de 1 a 99), para que la prensa tenga de qué hablar." else
            "Cuando cambien en tu partida, las pones en la plantilla del partido (la media) o en tu perfil.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        BloqueEditor("Cómo es")
        Campo("Su forma de ser", rasgos, "Ambicioso, fiestero y mujeriego", lineas = 3) { rasgos = it }
        Spacer(Modifier.height(20.dp))
        val listo = nombre.isNotBlank() && club.isNotBlank()
        AccionGrande(null, when { ocupado -> "Escribiendo su presentación…"; creada != null -> "Ir a la carrera"; else -> "Empezar la carrera" }, Modifier.fillMaxWidth(), enabled = listo && !ocupado) {
            creada?.let { alCreada(it); return@AccionGrande }
            if (ocupado || !listo) return@AccionGrande
            ocupado = true; aviso = null
            val pj = Personaje(nombre.trim(), apodo.trim(), edad.toIntOrNull() ?: 18, nacionalidad.trim(), if (modo == Rol.JUGADOR) posicion.trim() else "",
                if (modo == Rol.JUGADOR) dorsal.toIntOrNull() ?: 0 else 0, media.toIntOrNull() ?: 0,
                cifras.mapValues { it.value.toIntOrNull() ?: 0 }.filterValues { it > 0 }, rasgos.trim(),
                media.toIntOrNull()?.takeIf { it > 0 }?.let { listOf(0 to it) } ?: emptyList())
            val u = Universo(Universo.nuevoId(), modo, pj, club.trim(), liga.trim(), temporada.trim(), numero(salario), "", numero(dinero))
            ambito.launch {
                val (n, fallo) = withContext(Dispatchers.IO) { Motor.crea(u) }
                ocupado = false
                if (fallo == null) alCreada(n.id) else { creada = n.id; aviso = "La carrera está creada, pero su presentación no ha llegado: $fallo" }
            }
        }
        if (!listo) Text("Falta el nombre y el club.", color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        aviso?.let { Nota(it, Modifier.padding(top = 8.dp), color = Acento) }
    }
}
