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
    var nacimiento by rememberSaveable { mutableStateOf("") }
    var club by rememberSaveable { mutableStateOf("") }
    var liga by rememberSaveable { mutableStateOf("") }
    var temporada by rememberSaveable { mutableStateOf("2026/27") }
    var nivel by rememberSaveable { mutableStateOf(Nivel.CANTERANO) }
    var trayectoria by rememberSaveable { mutableStateOf("") }
    var dinero by rememberSaveable { mutableStateOf(Formato.cifra(Nivel.CANTERANO.dinero)) }
    var media by rememberSaveable { mutableStateOf("") }
    var rasgos by rememberSaveable { mutableStateOf("Ambicioso, fiestero y mujeriego") }
    val cifras = remember { mutableStateMapOf<String, String>() }
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var creada by remember { mutableStateOf<String?>(null) }
    // El club de verdad (búsqueda web): lo encontrado, si se está buscando, desde dónde se pidió y si prefieres no identificarlo
    var info by remember { mutableStateOf<InfoClub?>(null) }
    var buscando by remember { mutableStateOf(false) }
    var abajo by remember { mutableStateOf(false) }
    var sinIdentificar by remember { mutableStateOf(false) }
    var avisoClub by remember { mutableStateOf<String?>(null) }
    val infoVale = info?.takeIf { it.es(club) }
    fun buscaClub(precisa: String = "", desdeAbajo: Boolean = false) {
        if (buscando || club.isBlank()) return
        buscando = true; abajo = desdeAbajo; avisoClub = null
        ambito.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Clubes.identifica(club, liga, precisa) } }
            buscando = false
            r.onSuccess { info = it }.onFailure { avisoClub = Motor.mensaje(it) }
        }
    }

    PaginaRevista {
        RevistaTopBar(alVolver, "FcFeed")
        Antetitulo("Nueva carrera", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle("Empieza tu historia", 44.sp, 48.sp, letterSpacing = (-1.2).sp, modifier = Modifier.padding(top = 2.dp))
        Filete(3.dp)
        Text("Pon lo de tu partida de FC tal cual: FcFeed no se inventa nada del campo.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic,
            fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
        BloqueEditor("Tu carrera")
        MagChoice(Rol.entries.map { it.nombre }, modo.ordinal, "Modo carrera") { modo = Rol.entries[it] }
        Campo(if (modo == Rol.MISTER) "Nombre del míster" else "Nombre del jugador", nombre, "Nombre y apellidos") { nombre = it }
        Campo("Apodo (si tiene)", apodo, "Como le llama la grada") { apodo = it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Campo("Edad", edad, "18", Modifier.weight(1f), teclado = KeyboardType.Number) { edad = it.filter { c -> c.isDigit() }.take(2) }
            Campo("Nacionalidad", nacionalidad, "España", Modifier.weight(2f)) { nacionalidad = it }
        }
        Campo("Fecha de nacimiento en FC (opcional)", nacimiento, "dd/mm/aaaa, la de tu jugador en el juego") { nacimiento = it.take(20) }
        val nac = Fechas.lee(nacimiento)
        Text(when {
            nacimiento.isBlank() -> "Con ella (y la fecha de cada partido) FcFeed cuenta tu edad exacta, en años y días, para compararte con los récords del fútbol de verdad: el goleador más joven de la Champions, el debutante más joven de LaLiga…"
            nac == null -> "No entiendo esa fecha: ponla como 12/08/2008."
            else -> "Nacido el ${Fechas.texto(nac)}. Con la fecha de cada partido, tu edad exacta para los récords."
        }, color = if (nacimiento.isNotBlank() && nac == null) Acento else Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        if (modo == Rol.JUGADOR) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Campo("Posición", posicion, "DC, MCO, LD…", Modifier.weight(2f)) { posicion = it }
            Campo("Dorsal", dorsal, "9", Modifier.weight(1f), teclado = KeyboardType.Number) { dorsal = it.filter { c -> c.isDigit() }.take(2) }
        }
        BloqueEditor("Dónde")
        Campo("Club", club, "El de tu partida") { club = it }
        Campo("Liga", liga, "LaLiga, Premier League…") { liga = it }
        when {
            buscando && !abajo -> Cursiva("Buscando tu club en la web…", Modifier.padding(top = 6.dp), color = Muted)
            infoVale?.confirmado == true -> Tarjeta(Modifier.padding(top = 8.dp), bordeIzq = Acento) {
                Rotulo("Club identificado"); TextoTarjeta(infoVale.resumen, Modifier.padding(top = 4.dp), color = Charcoal)
                PildoraMarco("Cambiar", Modifier.padding(top = 8.dp)) { info = infoVale.copy(confirmado = false) }
            }
            infoVale != null && !abajo -> Box(Modifier.padding(top = 8.dp)) { ConfirmaClub(infoVale, buscando, alSi = { info = infoVale.copy(confirmado = true) }, alOtro = { buscaClub(it) }) }
            infoVale == null && club.isNotBlank() && ClaveApi.hay -> BotonBuscarClub(if (Prueba.activo) "Identificar el club (modo prueba: de ejemplo)" else "Identificar el club (búsqueda web, unos céntimos)") { buscaClub() }
        }
        if (!abajo) avisoClub?.let { Nota(it, Modifier.padding(top = 6.dp), color = Acento) }
        Campo("Temporada", temporada, "2026/27") { temporada = it }
        BloqueEditor("Quién eres")
        MagChoice(Nivel.entries.map { it.nombre }, nivel.ordinal, "Cómo de conocido empiezas") { nivel = Nivel.entries[it]; dinero = Formato.cifra(nivel.dinero) }
        Text(nivel.explica + ". Fama de salida: ${nivel.fama}; seguidores: ${Alcance.texto(Alcance.seguidores(nivel.fama))}; te cubren ${nivel.medios}. Luego sube (o baja) poco a poco con lo que hagas.",
            color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        Campo("Tu historia (opcional)", trayectoria, "«23 años, ganador del Balón de Oro», «canterano que debuta», «vuelve tras una cesión»…", lineas = 4) { trayectoria = it }
        Text("Lo que cuentes aquí es tu pasado: FcFeed lo respeta (también los premios que digas haber ganado).", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        Campo("Dinero en el banco (euros)", dinero, "Lo que tiene ahora", teclado = KeyboardType.Number) { dinero = it.filter { c -> c.isDigit() } }
        val contrato = Contratos.primero(nivel, club.trim().ifBlank { "tu club" }, temporada.trim(), modo)
        Text("Tu primer contrato lo pone FcFeed (no el de FC): ${Formato.euros(contrato.sueldo)} a la semana hasta ${contrato.hasta}, prima de firma de ${Formato.euros(contrato.prima)} y cláusula de ${Formato.eurosCorto(contrato.clausula)}" +
            (if (contrato.porGol > 0) ", con ${Formato.euros(contrato.porGol)} por gol" else "") + ". Las renovaciones y los fichajes llegarán según te vaya.",
            color = Charcoal, fontFamily = Body, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
        BloqueEditor(if (modo == Rol.MISTER) "Tus cifras" else "Tu carta de FC")
        if (modo == Rol.JUGADOR) Campo("Media", media, "La de tu jugador en FC", teclado = KeyboardType.Number) { media = it.filter { c -> c.isDigit() }.take(2) }
        val lista = if (modo == Rol.MISTER) Cifras.MISTER else Cifras.JUGADOR
        lista.chunked(3).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                fila.forEach { (k, n) -> Campo(n, cifras[k].orEmpty(), "—", Modifier.weight(1f), teclado = KeyboardType.Number) { cifras[k] = it.filter { c -> c.isDigit() }.take(2) } }
                repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(if (modo == Rol.MISTER) "En FC el míster no tiene carta: estas cifras son de FcFeed (de 1 a 99), para que la prensa tenga de qué hablar." else
            "Cuando cambien en tu partida, las pones en la plantilla del partido (la media) o en tu perfil.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        BloqueEditor("Cómo es")
        Campo("Su forma de ser", rasgos, "Ambicioso, fiestero y mujeriego", lineas = 3) { rasgos = it }
        Spacer(Modifier.height(20.dp))
        val listo = nombre.isNotBlank() && club.isNotBlank()
        // Antes de empezar, el club: se busca y lo confirmas (o empiezas sin identificarlo)
        val faltaClub = listo && ClaveApi.hay && infoVale?.confirmado != true && !sinIdentificar && creada == null
        if (abajo && infoVale != null && !infoVale.confirmado) {
            ConfirmaClub(infoVale, buscando, alSi = { info = infoVale.copy(confirmado = true) }, alOtro = { buscaClub(it, desdeAbajo = true) }, textoSi = "Sí, es este") {
                BotonRedondo("Empezar sin identificarlo", Modifier.fillMaxWidth(), lleno = false) { sinIdentificar = true }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (abajo) avisoClub?.let {
            Nota(it, Modifier.padding(bottom = 6.dp), color = Acento)
            EnlaceRevista("Empezar sin identificar el club", Modifier.padding(bottom = 10.dp), acento = true) { sinIdentificar = true; avisoClub = null }
        }
        AccionGrande(null, when { ocupado -> "Escribiendo su presentación…"; creada != null -> "Ir a la carrera"; buscando -> "Buscando tu club…"
            faltaClub && infoVale == null -> "Identificar el club y empezar"; faltaClub -> "Confirma tu club arriba"; else -> "Empezar la carrera" },
            Modifier.fillMaxWidth(), enabled = listo && !ocupado && !buscando && !(faltaClub && infoVale != null)) {
            creada?.let { alCreada(it); return@AccionGrande }
            if (ocupado || !listo || buscando) return@AccionGrande
            if (faltaClub) { if (infoVale == null) buscaClub(desdeAbajo = true); return@AccionGrande }
            ocupado = true; aviso = null
            val pj = Personaje(nombre.trim(), apodo.trim(), edad.toIntOrNull() ?: 18, nacionalidad.trim(), if (modo == Rol.JUGADOR) posicion.trim() else "",
                if (modo == Rol.JUGADOR) dorsal.toIntOrNull() ?: 0 else 0, media.toIntOrNull() ?: 0,
                cifras.mapValues { it.value.toIntOrNull() ?: 0 }.filterValues { it > 0 }, rasgos.trim(),
                media.toIntOrNull()?.takeIf { it > 0 }?.let { listOf(0 to it) } ?: emptyList(), Fechas.lee(nacimiento)?.let { Fechas.iso(it) } ?: "")
            val c = Contratos.primero(nivel, club.trim(), temporada.trim(), modo)
            val u = Universo(Universo.nuevoId(), modo, pj, club.trim(), liga.trim(), temporada.trim(), c.sueldo, c.hasta, numero(dinero),
                fuera = Fuera.INICIAL + mapOf(Fuera.FAMA to nivel.fama, Fuera.AFICION to nivel.aficion, Fuera.CARISMA to nivel.carisma),
                nivel = nivel.name, trayectoria = trayectoria.trim(), contrato = c, clubInfo = infoVale?.takeIf { it.confirmado })
                .let { x -> if (x.liga.isBlank() && x.clubInfo != null) x.copy(liga = x.clubInfo.liga) else x }
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
