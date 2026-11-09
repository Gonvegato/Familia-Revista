package com.gonzalo.hiedra

import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Copia la imagen elegida a la carpeta de la app (reducida) y devuelve su ruta: así no depende de permisos. */
suspend fun guardarPortada(c: Context, id: String, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1; while (o.outWidth / s > 1000 || o.outHeight / s > 1400) s *= 2
        val b = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return@runCatching null
        val dir = File(c.filesDir, "portadas").apply { mkdirs() }
        val f = File(dir, "$id-${System.currentTimeMillis()}.jpg")
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        f.absolutePath
    }.getOrNull()
}

/** Igual, pero con los bytes de una imagen ya bajada de internet. null = no es una imagen. */
suspend fun guardarPortadaBytes(c: Context, id: String, bytes: ByteArray, original: Boolean = false): String? = withContext(Dispatchers.IO) {
    runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0) return@runCatching null
        // Original: los mismos bytes que bajó, sin reducir ni recomprimir (máxima calidad; lo usa la pantalla de inicio)
        if (original) {
            val f = File(File(c.filesDir, "portadas").apply { mkdirs() }, "$id-${System.currentTimeMillis()}.img")
            f.writeBytes(bytes)
            return@runCatching f.absolutePath
        }
        var s = 1; while (o.outWidth / s > 2000 || o.outHeight / s > 2800) s *= 2
        val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return@runCatching null
        val f = File(File(c.filesDir, "portadas").apply { mkdirs() }, "$id-${System.currentTimeMillis()}.jpg")
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        f.absolutePath
    }.getOrNull()
}

/** El encabezado de una sección de la ficha: su número (01, 02…) en el acento y el nombre en grotesca; a la derecha, su acción si la tiene. */
@Composable
private fun SeccionNumerada(n: Int, texto: String, accion: Pair<String, () -> Unit>? = null, arriba: androidx.compose.ui.unit.Dp = 26.dp) {
    Row(Modifier.fillMaxWidth().padding(top = arriba, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text("%02d".format(n), color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, maxLines = 1)
        Text(texto, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, maxLines = 2, lineHeight = 22.sp, modifier = Modifier.weight(1f).padding(start = 10.dp, bottom = 1.dp))
        accion?.let { (t, f) -> Text(t.uppercase(), color = Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1,
            modifier = Modifier.tocable(onClick = f).padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) }
    }
}

/** Dos casillas de revista, una al lado de la otra, con su filete en medio. */
@Composable
private fun DosCasillas(izq: @Composable ColumnScope.() -> Unit, der: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.weight(1f).padding(top = 6.dp, bottom = 18.dp, end = 14.dp), content = izq)
        Box(Modifier.fillMaxHeight().width(2.dp).background(Charcoal))
        Column(Modifier.weight(1f).padding(top = 6.dp, bottom = 18.dp, start = 16.dp), content = der)
    }
}

/** La etiqueta del estado en la apertura: «JUGANDO · 12 H 30», «PENDIENTE», «PASADO · 2 NOV 2026», «ABANDONADO · 3 H». (En Jugando nunca dice «terminado».) */
fun etiquetaEstado(j: Juego): String = when (j.estado) {
    Estado.JUGANDO, Estado.ABANDONADO -> j.estado.nombre.uppercase() + if (j.msJugados >= 60_000) " · " + horasCortas(j.msJugados) else ""
    Estado.PENDIENTE -> "PENDIENTE"
    Estado.PASADO -> "PASADO" + if (j.finalizado > 0) " · " + fechaJunta(j.finalizado).uppercase() else ""
}

/**
 * La ficha de un juego, como la de Seshat («doble página»): la portada ampliada (o tu fondo) como foto de apertura, con un velo arriba para
 * «← Volver · Imágenes · Editar · Borrar»; la etiqueta del estado pisando su borde, el título enorme y «de <estudio> · <saga, n>»; el botón
 * principal (jugar, seguir, bajar y jugar, en el PC…) y las secciones numeradas: 01 Tu partida, 02 La saga, 03 Cómo se abre, tus partidas en
 * Bastet, tiempo y duración, En Jonsu, Datos y En la nube. Con mando, el foco empieza en el botón principal.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Ficha(id: String, alCerrar: () -> Unit, alJugar: (Juego) -> Unit, abrirPortada: Boolean = false, alVerPortada: () -> Unit = {}, alVincular: (Juego) -> Unit = {}, alIrA: (String) -> Unit = {}, alAbrirSaga: (Saga) -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val j = Biblioteca.por(id)
    if (j == null) { LaunchedEffect(Unit) { alCerrar() }; return }
    var editar by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf(false) }
    var selector by remember { mutableStateOf(false) }
    var editarWeb by remember { mutableStateOf(false) }
    var imagenes by remember { mutableStateOf(abrirPortada) }
    var imagenesFondo by remember { mutableStateOf(false) }
    var editarHoras by remember { mutableStateOf(false) }
    var saga by remember { mutableStateOf(false) }
    // La valoración de Jonsu, solo para verla (se refresca de vez en cuando)
    var notas by remember(NotaAzabache.version) { mutableStateOf(NotaAzabache.cargar(ctx)) }
    LaunchedEffect(Unit) { if (Cuenta.vinculada && NotaAzabache.vieja(ctx)) withContext(Dispatchers.IO) { if (NotaAzabache.refrescar(ctx)) notas = NotaAzabache.cargar(ctx) } }
    LaunchedEffect(abrirPortada) { if (abrirPortada) alVerPortada() }
    // Los datos de fuera (fecha, estudio, géneros, lo que suele durar): se piden si faltan o son viejos
    var buscandoDatos by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) { Datos.cargar(ctx); if (Datos.activo && Datos.hacenFalta(id)) { buscandoDatos = true; runCatching { Datos.pedir(ctx, j) }; buscandoDatos = false } }
    val miSaga = Sagas.de(j)
    val datos = Datos.de[j.id]
    // Con mando: al abrir la ficha, el foco va al botón principal (A juega); y no se sale de la ficha con la cruceta hacia lo que hay debajo
    val focoJugar = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(id) { kotlinx.coroutines.delay(120); runCatching { focoJugar.requestFocus() } }
    val tablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700
    val margen = if (tablet) 40.dp else 20.dp

    // Las secciones van numeradas (01, 02…) en el orden en que salen
    var nSec = 0
    fun sig() = ++nSec

    Column(Modifier.fillMaxSize().atrapaFoco().background(Paper).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        // ---- «Doble página»: la portada ampliada como foto de apertura, a todo el ancho y recortada, sobre su color medio (o tu fondo)
        val tela = remember(j.portada, j.titulo) { colorDePortada(j.portada) ?: telaDe(j.titulo) }
        val conFondo = j.fondo.isNotBlank()
        Box(Modifier.fillMaxWidth().height(ALTO_APERTURA).clipToBounds()
            .background(Brush.verticalGradient(listOf(lerpColor(tela, Color.White, 0.12f), lerpColor(tela, Color.Black, 0.55f)))).testTag("apertura")) {
            if (conFondo) ImagenEncuadrada(j.fondo, j.encFondo, Modifier.fillMaxSize(), "Fondo de ${j.titulo}")
            else BoxWithConstraints(Modifier.fillMaxSize()) {
                // (más ancha que la pantalla y recortada: la tapa como foto de apertura)
                val w = minOf(maxWidth, 720.dp) + 24.dp
                Box(Modifier.align(Alignment.TopCenter).padding(top = 30.dp).requiredWidth(w).requiredHeight(w * 1.5f)) {
                    PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = 160)
                }
            }
            // Un velo arriba para que se lean «Volver» y las acciones sobre cualquier imagen
            Box(Modifier.fillMaxWidth().height(VELO_APERTURA).background(veloArriba()))
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = margen).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                EnlaceRevista("← Volver", color = Color.White) { alCerrar() }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    EnlaceRevista("Imágenes", Modifier.testTag("imagenes"), color = Color.White) { imagenesFondo = false; imagenes = true }
                    EnlaceRevista("Editar", color = Color.White) { editar = true }
                    EnlaceRevista("Borrar", color = Color.White) { borrar = true }
                }
            }
        }
        // La etiqueta del estado, en el acento, pisando el borde de la foto; con fondo, la portada (120×180) asoma abajo a la derecha
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(start = margen).offset(y = (-16).dp).background(Acento).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(etiquetaEstado(j), color = SobreAcento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp, maxLines = 1)
                }
                Column(Modifier.fillMaxWidth().padding(start = margen, end = if (conFondo) margen + 136.dp else margen)) {
                    FitTitle(j.titulo.uppercase(), 38.sp, 40.sp, color = Charcoal, family = Grotesca, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, maxLines = 4, minSize = 20f)
                    val estudio = datos?.desarrolladores?.firstOrNull()
                    val de = listOfNotNull(estudio?.let { "de $it" }, miSaga?.let { "${it.nombre}, ${it.juegos.indexOf(j.id) + 1}" }, if (estudio == null && miSaga == null) plataformaDe(j) else null).joinToString(" · ")
                    if (de.isNotBlank()) Text(de, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            // (en una caja de 116 dp que la deja salir 64 dp por arriba: así no estira el hueco hasta el botón)
            if (conFondo) Box(Modifier.align(Alignment.TopEnd).padding(end = margen).width(120.dp).height(116.dp)) {
                PortadaSobreFondo(j, Modifier.align(Alignment.BottomCenter).wrapContentHeight(Alignment.Bottom, unbounded = true).requiredHeight(180.dp).fillMaxWidth(), sombra = 14.dp,
                    alTocar = { imagenesFondo = false; imagenes = true })
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = margen)) {
            Spacer(Modifier.height(16.dp))
            // La acción principal, ancha y en el acento
            if (j.sinLanzador) BotonC("Añadir cómo se abre", Modifier.focusRequester(focoJugar).testTag("jugar"), fondo = Charcoal, letra = Paper) { alVincular(j) }
            else BotonC(textoJugar(j), Modifier.focusRequester(focoJugar).testTag("jugar"), fondo = Acento, letra = SobreAcento) { alJugar(j) }
            if (j.enOtroDispositivo) Text("La ROM está en otro de tus móviles: «Bajar y jugar» la trae de tu kDrive.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))

            // ---- 01 Tu partida: la barra de la historia, tus horas, el estado (cambiarlo) y el favorito
            SeccionNumerada(sig(), "Tu partida", "Corregir horas" to { editarHoras = true })
            val avance = j.avance
            if (avance != null) Box(Modifier.fillMaxWidth().height(8.dp).background(Line)) { Box(Modifier.fillMaxWidth(avance).fillMaxHeight().background(Acento)) }
            val min = j.msJugados / 60_000L
            val linea = unidos(listOf(
                if (min >= 1) horasCortas(j.msJugados) + " JUGADAS" else "SIN EMPEZAR",
                avance?.let { "${(it * 100).toInt()} % DE LA HISTORIA" },
                if (avance != null && j.estado == Estado.JUGANDO && avance < 0.98f && min >= 5) "TE QUEDAN ~${tiempoDe((j.durHistoria - min).coerceAtLeast(1) * 60_000L).uppercase()}" else null
            ))
            Text(linea, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = if (avance != null) 7.dp else 0.dp))
            val deck = unidos(listOf(
                if (j.estado == Estado.PASADO) (if (j.finalizado > 0) "pasado el " + fechaCorta(j.finalizado) else "pasado") else null,
                if (j.ultima > 0) "jugado por última vez " + haceCuanto(j.ultima).lowercase() else null,
                if (j.veces > 0) "${j.veces} ${if (j.veces == 1) "vez" else "veces"}" else null
            ))
            if (deck.isNotBlank()) Text(deck, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
            val desglose = desgloseHoras(j)
            if (desglose.isNotBlank()) Text(desglose.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(2.dp, Charcoal)) {
                Estado.entries.forEach { e ->
                    val on = e == j.estado
                    Box(Modifier.weight(1f).fillMaxHeight().background(if (on) Charcoal else Paper).tocable(fuera = 0.dp) { Biblioteca.cambiar(j.id) { it.conEstado(e) } }.padding(vertical = 12.dp, horizontal = 2.dp), contentAlignment = Alignment.Center) {
                        // (si no cabe, la letra se encoge: nada de «ABANDONAD»)
                        FitTitle(e.nombre.uppercase(), 11.sp, 13.sp, color = if (on) Paper else Charcoal, family = Body, fontWeight = FontWeight.Black, letterSpacing = 0.4.sp, maxLines = 1, unaLinea = true, minSize = 6f,
                            align = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                EnlaceRevista(if (j.favorito) "★ Favorito" else "☆ Marcar como favorito", acento = j.favorito) { Biblioteca.poner(j.copy(favorito = !j.favorito)) }
            }

            // ---- 02 La saga: sus portadas en fila, con esta remarcada
            SeccionNumerada(sig(), if (miSaga != null) "La saga" else "Saga", accion = (if (miSaga != null) "${miSaga.juegos.indexOf(j.id) + 1} de ${miSaga.juegos.size} →" else "Añadir a una saga →") to { saga = true })
            if (miSaga != null) {
                val deSaga = remember(miSaga, Biblioteca.juegos.size) { Sagas.juegos(miSaga) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(miSaga.nombre, color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    EnlaceRevista("Ver la saga →") { alAbrirSaga(miSaga) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    deSaga.forEach { x ->
                        val esta = x.id == j.id
                        Box(Modifier.then(if (esta) Modifier.border(3.dp, Acento).padding(3.dp) else Modifier).tocable { if (esta) saga = true else alIrA(x.id) }) {
                            Box(Modifier.width(56.dp).height(84.dp)) { PortadaArco(x, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = 22) }
                        }
                    }
                }
            } else Text("No está en ninguna saga.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp)

            // ---- 03 Cómo se abre: cada lanzador (emulador, app, web, PC)
            if (!j.sinLanzador) {
                SeccionNumerada(sig(), "Cómo se abre", "+ Añadir" to { alVincular(j) })
                j.lanzadores.forEachIndexed { i, l ->
                    if (i > 0) { Spacer(Modifier.height(8.dp)); Filete(1.dp, Line); Spacer(Modifier.height(8.dp)) }
                    when (l.tipo) {
                        TipoLanzador.ROM -> {
                            val e = remember(j, l) { Lanzar.elegir(ctx, j) }
                            val aqui = Archivos.tiene(l.clave)
                            Text((Catalogo.sistema(l.sistema)?.nombre ?: l.sistema) + " · " + (e?.etiqueta ?: "sin emulador"), color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            val r = e?.resultado
                            Text((if (!aqui) "La ROM está en otro de tus móviles" else if (Lanzar.esInterno(e)) "Se juega en Bastet · " + Nucleos.describir(Nucleos.para(l.sistema)!!, Nucleos.instalado(ctx, Nucleos.para(l.sistema)!!.id)) + " · guarda al salir" else when (r) { is Resultado.Ok -> if (Comando.esRetroArch(e.plantilla)) "Instalado · necesita el núcleo descargado en RetroArch" else "Instalado y listo"; is Resultado.Falta -> r.motivo; null -> "" }).uppercase(),
                                color = if (aqui && r is Resultado.Ok) Muted else Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 14.sp)
                        }
                        TipoLanzador.APP -> { Text("App de Android", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(l.paquete, color = Muted, fontFamily = Body, fontSize = 12.sp) }
                        TipoLanzador.WEB -> { Text(l.url, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2); Text(("Pantalla: " + listOf("automática", "horizontal", "vertical")[l.orientacion.coerceIn(0, 2)]).uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                        TipoLanzador.PC -> BotonesJugarPc(l.pcKey)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (l.tipo) {
                            TipoLanzador.ROM -> EnlaceRevista("Emulador →") { selector = true }
                            TipoLanzador.WEB -> EnlaceRevista("Editar →") { editarWeb = true }
                            else -> {}
                        }
                        EnlaceRevista("Quitar") { Biblioteca.cambiar(j.id) { it.sinLanzador(l.clave) }; if (l.tipo == TipoLanzador.ROM) Archivos.quitar(l.clave) }
                    }
                }
            }
            // ---- Tus partidas en Bastet: el estado automático y tus huecos, con su foto (solo si el jugador de Bastet ya ha guardado alguno)
            if (j.rom != null && Nucleos.para(j.sistema) != null && hayEstados(ctx, j)) { SeccionNumerada(sig(), "Tus partidas en Bastet"); EstadosDeFicha(j) }

            // ---- Tiempo y duración
            SeccionNumerada(sig(), "Tiempo y duración")
            DosCasillas(
                izq = {
                    Text("TIEMPO", color = Charcoal, fontFamily = Grotesca, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp, modifier = Modifier.padding(bottom = 8.dp))
                    FitTitle(if (j.msJugados >= 60_000) tiempoDe(j.msJugados) else "—", 40.sp, 42.sp, unaLinea = true, maxLines = 1, family = Grotesca, fontWeight = FontWeight.ExtraBold, color = Acento)
                    Text("JUGADO EN TOTAL", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Última vez: ${if (j.ultima > 0) haceCuanto(j.ultima).lowercase() else "todavía no"}", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                },
                der = {
                    Text("SUELE DURAR", color = Charcoal, fontFamily = Grotesca, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp, modifier = Modifier.padding(bottom = 8.dp))
                    FitTitle(if (j.durHistoria > 0) tiempoDe(j.durHistoria * 60_000L) else "—", 40.sp, 42.sp, unaLinea = true, maxLines = 1, family = Grotesca, fontWeight = FontWeight.ExtraBold)
                    Text("LA HISTORIA", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    val mas = listOfNotNull(if (j.durExtras > 0) "con extras ${tiempoDe(j.durExtras * 60_000L)}" else null, if (j.durCompleto > 0) "al completo ${tiempoDe(j.durCompleto * 60_000L)}" else null).joinToString(" · ")
                    Text(mas.ifBlank { if (!datos?.duracionDe.isNullOrBlank()) "según ${datos!!.duracionDe}" else "sin dato todavía" }, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
                }
            )
            Filete(2.dp)

            // ---- En Jonsu (solo se ve; se valora allí)
            val nota = NotaAzabache.de(notas, j)
            SeccionNumerada(sig(), "En Jonsu", if (NotaAzabache.instalado(ctx)) "Abrir" to { NotaAzabache.abrir(ctx, nota) } else null)
            if (nota == null) {
                // Una línea: lo que hay y qué hacer
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (NotaAzabache.instalado(ctx)) "Sin valorar aún · " else "Sin valorar aún", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                    if (NotaAzabache.instalado(ctx)) EnlaceRevista("Valorar en Jonsu →", acento = true) { NotaAzabache.abrir(ctx, NotaAzabache.Nota(j.id, 0, "")) }
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    FitTitle("${nota.general}", 48.sp, 50.sp, unaLinea = true, maxLines = 1, family = Grotesca, fontWeight = FontWeight.ExtraBold)
                    Text(" /100", color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                }
                if (nota.resena.isNotBlank()) Text(nota.resena, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 6, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(16.dp))
            Filete(2.dp)

            // ---- Datos: lo de fuera y lo de aquí, en líneas con puntos guía
            SeccionNumerada(sig(), "Datos", "Buscar de nuevo" to { scope.launch { buscandoDatos = true; runCatching { Datos.pedir(ctx, j, forzar = true) }; buscandoDatos = false } })
            if (datos != null && !datos.nada) {
                if (datos.salida.isNotBlank()) MagFact("Salida", datos.salida)
                if (datos.desarrolladores.isNotEmpty()) MagFact(if (datos.desarrolladores.size == 1) "Estudio" else "Estudios", datos.desarrolladores.joinToString(" · "))
                if (datos.editores.isNotEmpty() && datos.editores != datos.desarrolladores) MagFact(if (datos.editores.size == 1) "Editor" else "Editores", datos.editores.joinToString(" · "))
                if (datos.generos.isNotEmpty()) MagFact("Géneros", datos.generos.joinToString(" · "))
                if (datos.metacritic > 0) MagFact("Metacritic", "${datos.metacritic}/100")
            }
            if (j.plataforma.isNotBlank()) MagFact("Plataforma", j.plataforma)
            j.lanzadores.forEach { l -> when (l.tipo) {
                TipoLanzador.ROM -> MagFact("ROM", if (Archivos.tiene(l.clave)) l.archivo.ifBlank { j.titulo } else "en otro dispositivo")
                TipoLanzador.APP -> MagFact("App", l.paquete)
                TipoLanzador.WEB -> MagFact("Web", l.url.removePrefix("https://").removePrefix("http://").removePrefix("www."))
                TipoLanzador.PC -> MagFact("En el PC", Suite.nombresPc[l.pcDev]?.ifBlank { null } ?: l.pcKey)
            } }
            if (miSaga != null) MagFact("Saga", "${miSaga.nombre} · ${miSaga.juegos.indexOf(j.id) + 1}")
            if (j.anadido > 0) MagFact("Añadido", fechaCorta(j.anadido))
            if (j.estado == Estado.PASADO && j.finalizado > 0) MagFact("Pasado", fechaCorta(j.finalizado))
            if (j.veces > 0) MagFact("Partidas", "${j.veces}")
            if (datos != null && !datos.nada && datos.fuente.isNotBlank()) MagFact("Datos de", datos.fuente)
            val pie = when {
                buscandoDatos -> "Buscando los datos del juego…"
                datos == null -> if (Datos.activo) "" else "Sin datos de fuera."
                datos.nada -> if (Servicios.claves(ctx).rawg.isBlank()) "No está en la tienda de Steam. Con una clave de RAWG (Ajustes → Cuenta → Servicios, o en Atrio) se buscan también los juegos de consola." else "No he encontrado este juego en Steam ni en RAWG. Si su nombre aquí no es el oficial, cámbialo en «Editar» y busca de nuevo."
                else -> ""
            }
            if (pie.isNotBlank()) Text(pie, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
            if (datos != null && datos.resumen.isNotBlank()) Text(datos.resumen, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 21.sp, maxLines = 8, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(16.dp))
            Filete(2.dp)

            // ---- En la nube (solo ROMs)
            if (j.rom != null) { SeccionNumerada(sig(), "En la nube"); SeccionNube(j) }
            Spacer(Modifier.height(30.dp))
        }
    }
    if (editar) DialogoEditarJuego(j, alCerrar = { editar = false }, alHoras = { editar = false; editarHoras = true }, alQuitar = { editar = false; borrar = true })
    if (saga) DialogoSagaDeJuego(j) { saga = false }
    if (imagenes) SelectorImagenes(j, fondo = imagenesFondo) { imagenes = false }
    if (borrar) AlertDialog(
        onDismissRequest = { borrar = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Quitar «${j.titulo}»?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text(if (j.rom != null) "Solo se quita de Bastet: el archivo de la ROM no se toca." else "Solo se quita de Bastet.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({ Biblioteca.quitar(j.id); borrar = false; alCerrar() }) { Text("QUITAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ borrar = false }) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
    if (selector) SelectorEmulador(j.sistema, j) { selector = false }
    if (editarWeb) DialogoWeb(j) { editarWeb = false }
    if (editarHoras) DialogoHoras(j.msJugados, alAceptar = { ms -> Biblioteca.cambiar(j.id) { it.conTotalMs(ms) }; editarHoras = false }, alCerrar = { editarHoras = false })
}

/** ¿Ha guardado ya el jugador de Bastet algún estado de este juego (el automático o un hueco)? */
fun hayEstados(c: Context, j: Juego): Boolean = Estados.huecos.any { Estados.meta(c, j.id, it) != null }

/** Un cuadro con una línea de texto para escribir. */
@Composable
fun DialogoTexto(titulo: String, inicial: String, boton: String, alCerrar: () -> Unit, alAceptar: (String) -> Unit) {
    var t by remember { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = alCerrar, containerColor = Paper, shape = esquina(4.dp),
        title = { Text(titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Campo(t, "", { t = it }) },
        confirmButton = { TextButton({ alAceptar(t) }) { Text(boton.uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton(alCerrar) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}

/** Una línea de texto con un filete debajo (la casilla de la revista). */
@Composable
fun Campo(valor: String, pista: String, alCambiar: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (valor.isEmpty()) Text(pista, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
            BasicTextField(valor, alCambiar, singleLine = true, textStyle = TextStyle(color = Charcoal, fontFamily = Display, fontSize = 18.sp), cursorBrush = SolidColor(Acento), modifier = Modifier.fillMaxWidth())
        }
        Filete(1.5.dp)
    }
}

/** Qué emulador usar: «Automático» o uno de los del sistema; ✓ si está instalado, «Instalar» si no. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorEmulador(sistemaId: String, juego: Juego?, alCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val sistema = Catalogo.sistema(sistemaId)
    val evaluados = remember(juego, sistemaId) { if (juego != null) Lanzar.evaluar(ctx, juego) else Lanzar.evaluar(ctx, sistemaId, RomRef("content://sonda", "/storage/emulated/0/sonda")) }
    var actual by remember { mutableStateOf(if (juego != null) juego.emulador else Ajustes.emuladorPorSistema[sistemaId].orEmpty()) }
    ModalBottomSheet(alCerrar, containerColor = Paper, contentColor = Charcoal, shape = RectangleShape) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Antetitulo(if (juego != null) "Emulador de este juego" else "Emulador", Modifier.padding(bottom = 4.dp), color = Acento, conCuadro = false)
            FitTitle(sistema?.nombre ?: sistemaId, 34.sp, 36.sp, letterSpacing = (-1).sp, maxLines = 2)
            Cuerpo(if (Nucleos.para(sistemaId)?.alFrente == true) "«Automático» usa Bastet: el núcleo se baja la primera vez, al salir se guarda donde lo dejaste y sigues en tu otro móvil. Los demás emuladores siguen aquí; los que tienes instalados salen marcados."
                else if (Nucleos.para(sistemaId) != null) "Bastet juega a esta consola ella misma: el núcleo se baja la primera vez, al salir se guarda donde lo dejaste y sigues en tu otro móvil. «Automático» la usa si no tienes otro emulador instalado para ella; si lo tienes, sigue siendo ese hasta que elijas «Bastet» aquí."
                else "Los que ya tienes instalados salen marcados. «Automático» usa uno propio (sin núcleos que descargar) y, si no hay, RetroArch.", tam = 12, modifier = Modifier.padding(vertical = 8.dp))
            Nucleos.para(sistemaId)?.let { n -> BloqueNucleo(n) }
            Filete(2.dp)
            fun elegir(l: String) {
                actual = l
                if (juego != null) Biblioteca.cambiar(juego.id) { x -> x.copy(lanzadores = x.lanzadores.map { if (it.tipo == TipoLanzador.ROM) it.copy(emulador = l) else it }) }
                else { if (l.isEmpty()) Ajustes.emuladorPorSistema.remove(sistemaId) else Ajustes.emuladorPorSistema[sistemaId] = l; Ajustes.guardar(ctx) }
            }
            Opcion("Automático", "Recomendado", actual.isEmpty(), true, null) { elegir("") }
            if (evaluados.isEmpty()) Cuerpo("Bastet todavía no sabe lanzar este sistema.", modifier = Modifier.padding(top = 12.dp))
            evaluados.forEach { e ->
                val r = e.resultado
                val sub = when {
                    Lanzar.esInterno(e) -> "El de Bastet · " + (if (Nucleos.para(sistemaId)!!.pruebas) "en pruebas · " else "") + Nucleos.describir(Nucleos.para(sistemaId)!!, remember(Nucleos.version) { Nucleos.instalado(ctx, Nucleos.para(sistemaId)!!.id) })
                    r is Resultado.Ok -> if (Comando.esRetroArch(e.plantilla)) "Instalado · necesita el núcleo en RetroArch" else "Instalado"
                    r is Resultado.Falta -> r.motivo
                    else -> ""
                }
                Opcion(e.etiqueta, sub, actual == e.etiqueta, r is Resultado.Ok, (r as? Resultado.Falta)?.paquete) { elegir(e.etiqueta) }
            }
        }
    }
}

@Composable
private fun Opcion(titulo: String, sub: String, elegida: Boolean, instalado: Boolean, paquetePlay: String?, alElegir: () -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().tocable(onClick = alElegir).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, color = if (elegida) Acento else Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Text(sub.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        if (!instalado && paquetePlay != null) Text("INSTALAR\u00A0→", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp,
            modifier = Modifier.tocable { Lanzar.tienda(ctx, paquetePlay) }.padding(10.dp))
        else if (elegida) Text("✓", color = Acento, fontWeight = FontWeight.Black, fontSize = 18.sp)
        else if (instalado && titulo != "Automático") Text("INSTALADO", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
    Filete(1.dp, Line)
}

/**
 * El núcleo del jugador interno para un sistema: lo que hay instalado y Descargar / Actualizar / Quitar; si está en pruebas, se dice;
 * sus BIOS (cuáles tienes, cuáles le faltan, y «Añadir BIOS» las reconoce por su contenido) y, si necesita archivos aparte (PPSSPP), si
 * están. Actualizar avisa: un estado guardado solo carga con la versión que lo hizo (la partida del cartucho no se pierde nunca).
 */
@Composable
private fun BloqueNucleo(n: Nucleos.Nucleo) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val inst = remember(Nucleos.version) { Nucleos.instalado(ctx, n.id) }
    var bajando by remember { mutableStateOf<Float?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var confirmar by remember { mutableStateOf(false) }
    var nueva by remember { mutableStateOf<Long?>(null) }
    val elegirBios = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val puestas = withContext(Dispatchers.IO) { uris.mapNotNull { u -> runCatching { Nucleos.instalarBios(ctx, u, Escaneo.nombreDe(ctx, u) ?: "", n.bios) }.getOrNull() } }
            mensaje = if (puestas.isEmpty()) "Ese archivo no es ninguna de las BIOS de ${n.nombre} (se reconocen por su contenido o por su nombre)"
                else "Lista: " + puestas.joinToString(" · ") { it.archivo }
        }
    }
    fun bajar() {
        bajando = 0f; mensaje = null
        scope.launch {
            mensaje = runCatching {
                withContext(Dispatchers.IO) {
                    val i = Nucleos.instalar(ctx, n.id) { p -> bajando = p }
                    if (!Nucleos.recursosListos(ctx, n)) { bajando = 0f; Nucleos.instalarRecursos(ctx, n) { p -> bajando = p } }
                    i
                }
            }.fold({ "Listo: ${Nucleos.describir(n, it)}" }, { "No se ha podido bajar: ${it.message ?: "sin conexión"}" })
            bajando = null; nueva = null
        }
    }
    Row(Modifier.padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Antetitulo("Núcleo de Bastet")
        if (n.pruebas) Text("EN PRUEBAS", color = Acento, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 10.dp).border(1.dp, Acento, esquina(2.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
    }
    Text(Nucleos.describir(n, inst), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    if (inst != null) Text("VERSIÓN ${inst.version}", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    if (n.pruebas) Cuerpo("Todavía no se ha podido probar en un móvil de verdad. Si un juego no arranca o va lento, elige aquí otro emulador para este sistema (o para ese juego, en su ficha).", tam = 12, modifier = Modifier.padding(top = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        when {
            bajando != null -> Text("BAJANDO · ${((bajando ?: 0f) * 100).toInt()} %", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(vertical = 10.dp))
            inst == null -> EnlaceRevista("Descargar ahora →", acento = true) { bajar() }
            else -> {
                EnlaceRevista(if (confirmar) "Sí, actualizar →" else if (nueva != null && Nucleos.hayNueva(inst, nueva!!)) "Actualizar a la del ${Formato.fecha(nueva!!)}" else "Actualizar", acento = confirmar || (nueva != null && Nucleos.hayNueva(inst, nueva!!))) {
                    if (confirmar) { confirmar = false; bajar() } else confirmar = true
                }
                if (nueva == null) EnlaceRevista("¿Hay otra?") {
                    scope.launch {
                        val f = withContext(Dispatchers.IO) { Nucleos.fechaEnBuildbot(n.id) }
                        nueva = f
                        mensaje = when { f <= 0L -> "No he podido mirarlo (¿sin conexión?)"; Nucleos.hayNueva(inst, f) -> "Hay una del ${Formato.fecha(f)}"; else -> "Tienes la última" }
                    }
                }
                EnlaceRevista("Quitar") { Nucleos.quitar(ctx, n.id); mensaje = "Quitado: se volverá a bajar al jugar" }
            }
        }
    }
    bajando?.let { LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, color = Acento, trackColor = Line, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) }
    if (confirmar) Cuerpo("Los estados guardados con esta versión pueden dejar de cargar con la nueva (en todos tus móviles, hasta que también la actualicen). La partida del cartucho no se pierde.", tam = 12)
    n.recursos?.let { r ->
        val listos = remember(Nucleos.version) { Nucleos.recursosListos(ctx, n) }
        Text((if (listos) "Archivos de ${n.nombre}: listos" else "Archivos de ${n.nombre}: se bajan con el núcleo (${Nucleos.megas(r.pesoAprox)})").uppercase(),
            color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 2.dp))
    }
    if (n.bios.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Antetitulo("BIOS", Modifier.padding(bottom = 4.dp), tam = 11.sp, conCuadro = false)
        val v = Nucleos.version
        n.bios.forEach { b ->
            val esta = remember(v, b.ruta) { Nucleos.biosEsta(ctx, b) }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (esta) "✓" else if (b.obligatoria) "!" else "·", color = if (esta) Charcoal else if (b.obligatoria) Acento else Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.archivo, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text((b.descripcion + if (esta) "" else if (b.obligatoria) (if (b.grupo != null) " · basta con una" else " · hace falta") else " · opcional").uppercase(),
                        color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                }
            }
        }
        EnlaceRevista("Añadir BIOS →", acento = Nucleos.faltan(ctx, n).isNotEmpty()) { elegirBios.launch(arrayOf("*/*")) }
    }
    mensaje?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp) }
}

/**
 * «Tus partidas en Hiedra»: el estado automático (el de al salir, con el que sigues aquí o en otro móvil) y los tres huecos, cada uno
 * con su foto, cuándo y en qué móvil. Solo sale si hay alguno.
 */
@Composable
private fun EstadosDeFicha(j: Juego) {
    val ctx = LocalContext.current
    val estados = remember(j.id, j.ultima) {
        Estados.huecos.mapNotNull { h -> Estados.meta(ctx, j.id, h)?.let { m ->
            Triple(h, m, Estados.miniatura(ctx, j.id, h).takeIf { it.exists() }?.let { f -> runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() })
        } }
    }
    if (estados.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            estados.forEach { (h, m, mini) ->
                Column(Modifier.width(132.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(esquina(3.dp)).border(1.dp, Line, esquina(3.dp)).background(Sand)) {
                        if (mini != null) androidx.compose.foundation.Image(mini, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize())
                        if (h == Estados.AUTO) Text("AL SALIR", color = SobreAcento, fontFamily = Body, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                            modifier = Modifier.align(Alignment.TopStart).padding(5.dp).background(Acento, esquina(2.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                    Text(if (h == Estados.AUTO) "Automático" else "Hueco $h", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 5.dp))
                    Text((haceCuanto(m.t) + if (m.dev != Cuenta.dispositivo && m.dn.isNotBlank()) " · ${m.dn}" else "").uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 2)
                }
            }
        }
        Cuerpo("Al jugar se sigue desde el automático; los huecos se cargan desde el menú del juego.", tam = 12, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Horas y minutos jugados, para corregirlos a mano. */
@Composable
fun DialogoHoras(ms: Long, alAceptar: (Long) -> Unit, alCerrar: () -> Unit) {
    var h by remember { mutableStateOf((ms / 3_600_000).toString()) }
    var m by remember { mutableStateOf(((ms / 60_000) % 60).toString()) }
    AlertDialog(
        onDismissRequest = alCerrar, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Horas jugadas", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) { Etiqueta("Horas"); Campo(h, "0", { h = it.filter(Char::isDigit).take(5) }) }
                    Column(Modifier.weight(1f)) { Etiqueta("Minutos"); Campo(m, "0", { m = it.filter(Char::isDigit).take(2) }) }
                }
                Cuerpo("Bastet suma solo el tiempo que pasas fuera cuando abres un juego (aproximado). Aquí lo corriges.", tam = 12)
            }
        },
        confirmButton = { TextButton({ alAceptar((h.toLongOrNull() ?: 0L) * 3_600_000L + (m.toLongOrNull() ?: 0L).coerceAtMost(59L) * 60_000L) }) { Text("GUARDAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton(alCerrar) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}

/** Una barra fina de avance, como la de Marfil. */
@Composable
fun Barra(f: Float) {
    Box(Modifier.fillMaxWidth().height(3.dp).background(Line)) { Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
}

/** De dónde salen las horas: «Torre 40 h · Steam 42 h · este móvil 1 h». Vacío si todo es de aquí. */
fun desgloseHoras(j: Juego): String {
    val partes = ArrayList<String>()
    if (j.propias.ms >= 60_000) partes += "este móvil " + tiempoDe(j.propias.ms)
    val otros = j.ajenas.values.sumOf { it.ms }
    if (otros >= 60_000) partes += "otros móviles " + tiempoDe(otros)
    for ((k, h) in j.externas) {
        if (h.ms < 60_000) continue
        partes += nombreFuente(k) + " " + tiempoDe(h.ms)
    }
    return if (partes.size <= 1) "" else partes.joinToString(" · ")
}

/** Cómo se llama una fuente de horas en las pantallas. */
fun nombreFuente(k: String): String = when {
    Fuentes.esPc(k) -> Suite.nombresPc[k.removePrefix("pc:")]?.ifBlank { null } ?: "PC"
    k == Fuentes.STEAM -> "Steam"
    Fuentes.esCarmesi(k) -> "Carmesí"
    else -> k
}

private fun fechaCorta(t: Long) = Formato.fecha(t)
fun notaTexto(n: Float) = if (n <= 0f) "—" else if (n % 1f == 0f) n.toInt().toString() else String.format(java.util.Locale("es", "ES"), "%.1f", n)

/** Una casilla fija de la ficha: su antetítulo (con una acción a la derecha, si la hay) y lo que pinta, entre filetes. */
@Composable
fun Casilla(titulo: String, accion: Pair<String, () -> Unit>? = null, contenido: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Antetitulo(titulo, tam = 12.sp, conCuadro = false) }
            if (accion != null) Text(accion.first.uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.tocable(onClick = accion.second).padding(4.dp))
        }
        contenido()
    }
    Filete(2.dp)
}

/** «Editar»: el nombre, la plataforma (en las fichas sin lanzador), corregir las horas y quitar el juego. */
@Composable
fun DialogoEditarJuego(j: Juego, alCerrar: () -> Unit, alHoras: () -> Unit, alQuitar: () -> Unit) {
    var nombre by remember { mutableStateOf(j.titulo) }
    var plataforma by remember { mutableStateOf(j.plataforma) }
    AlertDialog(
        onDismissRequest = alCerrar, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Editar", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                CampoRevista("Nombre", nombre, "nombre del juego") { nombre = it }
                if (j.sinLanzador) CampoRevista("Plataforma", plataforma, "PC, Switch, PS5…") { plataforma = it }
                Spacer(Modifier.height(12.dp))
                MagLink("Corregir las horas", "Bastet las apunta al volver de jugar; aquí las ajustas", onClick = alHoras)
                Filete(1.dp, Line)
                MagLink("Quitar de Bastet", if (j.rom != null) "La ROM no se toca" else null, onClick = alQuitar)
            }
        },
        confirmButton = { TextButton({ if (nombre.isNotBlank()) Biblioteca.cambiar(j.id) { it.copy(titulo = nombre.trim(), plataforma = plataforma.trim()) }; alCerrar() }) { Text("GUARDAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton(alCerrar) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}

/** «En la nube» (solo ROMs): carpeta de guardados del sistema, subir la ROM a tu kDrive y bajarla en otro móvil. */
@Composable
private fun SeccionNube(j: Juego) {
    val rom = j.rom ?: return
    val ctx = LocalContext.current
    val sistema = Catalogo.sistema(j.sistema)
    val listo = Cuenta.vinculada && KDrive.on(ctx)
    var mensaje by remember(j.id) { mutableStateOf<String?>(null) }
    var ocupado by remember(j.id) { mutableStateOf(false) }
    var nube by remember(j.id, j.uri) { mutableStateOf<NubeP.Ficha?>(null) }
    LaunchedEffect(j.id, j.uri, listo) { if (listo) nube = withContext(Dispatchers.IO) { NubeP.romEnNube(j) } }
    val carpeta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) NubeP.ponCarpeta(ctx, j.sistema, u) }
    var bajar by remember(j.id) { mutableStateOf(false) }
    if (bajar) DialogoBajarRom(j, jugar = false) { bajar = false; nube = null }
    if (!listo) {
        Cuerpo("Para llevarte la partida a otro móvil: vincula tu cuenta de Atrio y conecta tu kDrive (Ajustes). Bastet sube el guardado al volver de jugar y baja el último antes de jugar.", tam = 12)
    } else {
        val car = NubeP.carpetas[j.sistema]
        MagLink(if (car == null) "Elegir la carpeta de guardados de ${sistema?.corto ?: j.sistema}" else "Carpeta de guardados elegida · cambiar",
            "La carpeta donde ${sistema?.corto ?: "el emulador"} deja los .srm / .sav. Solo se sincronizan los de esta ROM (mismo nombre que el archivo).") { carpeta.launch(null) }
        Filete(1.dp, Line)
        when {
            j.enOtroDispositivo && nube != null -> MagLink("Descargar la ROM de mi kDrive", "A la carpeta de ROMs de este móvil, en la de ${sistema?.corto ?: j.sistema} («Jugar» también la baja)") { bajar = true }
            j.enOtroDispositivo -> Cuerpo("Esta ROM está en otro de tus móviles y todavía no ha llegado a tu kDrive: se sube sola, con wifi, al abrir Bastet en ese móvil.", tam = 12)
            nube != null -> Cuerpo("La ROM ya está en tu kDrive: en tus otros móviles, «Jugar» la baja.", tam = 12)
            else -> MagLink(if (ocupado) "Subiendo…" else "Subir la ROM a mi kDrive ahora", if (NubeP.romsSolas(ctx)) "Se sube sola con wifi; esto la sube ya, también con datos" else "Solo a tu kDrive, para tenerla en tus otros móviles") {
                if (!ocupado && j.uri.isNotBlank()) {
                    ocupado = true; mensaje = "Subiendo la ROM…"
                    kotlin.concurrent.thread {
                        val r = runCatching {
                            val d = androidx.documentfile.provider.DocumentFile.fromSingleUri(ctx, android.net.Uri.parse(j.uri)) ?: error("no encuentro el archivo")
                            NubeP.subirRom(ctx.applicationContext, j, DocArch(ctx.applicationContext, d))
                        }
                        android.os.Handler(android.os.Looper.getMainLooper()).post { mensaje = r.fold({ "ROM subida a tu kDrive" }, { "No se pudo subir: ${it.message}" }); ocupado = false; nube = null }
                    }
                }
            }
        }
    }
    mensaje?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
}
