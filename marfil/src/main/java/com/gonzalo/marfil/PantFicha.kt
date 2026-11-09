package com.gonzalo.marfil

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** El encabezado de una sección de la ficha: su número (01, 02…) en el acento y el nombre en grotesca; a la derecha, su acción si la tiene. */
@Composable
private fun SeccionNumerada(n: Int, texto: String, accion: Pair<String, () -> Unit>? = null, arriba: Dp = 26.dp) {
    Row(Modifier.fillMaxWidth().padding(top = arriba, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text("%02d".format(n), color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, maxLines = 1)
        Text(texto, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 10.dp, bottom = 1.dp))
        accion?.let { (t, f) -> Text(t.uppercase(), color = Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1,
            modifier = Modifier.clickable(onClick = f).padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) }
    }
}

/** Dos casillas de revista, una al lado de la otra, con su filete en medio. */
@Composable
private fun DosCasillas(izq: @Composable ColumnScope.() -> Unit, der: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.weight(1f).padding(top = 16.dp, bottom = 18.dp, end = 14.dp), content = izq)
        Box(Modifier.fillMaxHeight().width(2.dp).background(Charcoal))
        Column(Modifier.weight(1f).padding(top = 16.dp, bottom = 18.dp, start = 16.dp), content = der)
    }
}

/** El antetítulo de una casilla (en versalitas) con su acción a la derecha, si la tiene. */
@Composable
private fun CabezaCasilla(texto: String, accion: Pair<String, () -> Unit>? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto.uppercase(), color = Charcoal, fontFamily = Grotesca, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp, modifier = Modifier.weight(1f))
        accion?.let { (t, f) -> Text(t.uppercase(), color = Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.clickable(onClick = f).padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) }
    }
}

private fun fecha(ms: Long): String = Formato.fecha(ms)

private fun haceCuando(ms: Long): String {
    if (ms <= 0) return "todavía no"
    val d = ((System.currentTimeMillis() - ms) / 86_400_000L).toInt()
    return when { d <= 0 -> "hoy"; d == 1 -> "ayer"; d < 7 -> "hace $d días"; else -> "el " + fecha(ms) }
}

fun tamanoLegible(b: Long): String = when { b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0); b >= 1024 -> "${b / 1024} KB"; else -> "$b B" }

/**
 * La ficha de un libro, con la estética de la ficha de Atrio: titular enorme, línea en cursiva, una fila de acciones y casillas con filetes.
 * Desde aquí: leerlo (bajándolo antes de tu kDrive si hace falta), cambiar su portada (galería o internet), renombrarlo, elegir o quitar
 * su archivo y quitarlo de la biblioteca.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantFicha(id: String, alCerrar: () -> Unit, alLeer: (Libro) -> Unit) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    val l = Biblioteca.por(id)
    if (l == null) { LaunchedEffect(Unit) { alCerrar() }; return }
    var quitar by remember { mutableStateOf(false) }
    var quitarArchivo by remember { mutableStateOf(false) }
    var renombrar by remember { mutableStateOf(false) }
    var portada by remember { mutableStateOf(false) }
    var imagenesFondo by remember { mutableStateOf(false) }
    var saga by remember { mutableStateOf(false) }
    var menuArchivo by remember { mutableStateOf(false) }
    var trabajando by remember { mutableStateOf<String?>(null) }
    fun dice(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()
    val st = remember(l.id, l.ultima, l.msPropios, l.fraccion) { EstadoLectura.estado(ctx, l.id) }
    val enNube = NubeLibros.de(ctx, l)
    val ficheroLocal = remember(l.uri) { runCatching { File(Uri.parse(l.uri).path.orEmpty()) }.getOrNull()?.takeIf { l.uri.isNotBlank() && it.exists() } }

    val elegirArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { trabajando = "Copiando el archivo…"; ambito.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Metadatos.adjuntar(ctx, l, u) }.getOrNull() }
            trabajando = null
            dice(if (r == null) "No he podido usar ese archivo (vale EPUB, PDF, MOBI, AZW3, FB2, CBZ y TXT)." else "Archivo puesto: ${r.archivo}")
            if (r != null) NubeLibros.programar(ctx)
        } }
    }
    fun bajar(leer: Boolean) {
        if (trabajando != null) return
        if (!KDrive.on(ctx)) { dice("Conecta tu kDrive (Ajustes → Avanzado) para bajarlo."); return }
        trabajando = "Bajando de tu kDrive…"
        ambito.launch {
            val r = withContext(Dispatchers.IO) { runCatching { NubeLibros.leerIndice(ctx); NubeLibros.bajar(ctx, Biblioteca.por(l.id) ?: l) } }
            trabajando = null
            r.onFailure { dice("No he podido bajarlo: ${it.message}") }.onSuccess { n -> if (leer) alLeer(n) else dice("Libro bajado.") }
        }
    }
    fun principal() = when {
        !l.enOtroDispositivo -> alLeer(l)
        enNube != null -> bajar(true)
        else -> elegirArchivo.launch(arrayOf("*/*"))
    }

    val deck = lineaDeLectura(l, { fecha(it) }, { haceCuando(it) })
    val miSaga = Sagas.de(l.clave)
    val frac = l.fraccion

    // Las secciones van numeradas (01, 02…) en el orden en que salen
    var nSec = 0
    fun sig() = ++nSec

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        // ---- «Doble página»: la tapa ampliada como foto de apertura, a todo el ancho y recortada, sobre su color medio
        val tela = remember(l.portada, l.titulo) { colorDePortada(l.portada) ?: telaDe(l.titulo) }
        val conFondo = l.fondo.isNotBlank()
        Box(Modifier.fillMaxWidth().height(ALTO_APERTURA).clipToBounds()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(lerpColor(tela, androidx.compose.ui.graphics.Color.White, 0.12f), lerpColor(tela, androidx.compose.ui.graphics.Color.Black, 0.55f))))) {
            // (más ancha que la pantalla y recortada: la tapa como foto de apertura)
            // Con fondo elegido: el fondo es la foto de apertura (y la portada asoma abajo a la derecha, más abajo)
            if (conFondo) ImagenEncuadrada(l.fondo, l.encFondo, Modifier.fillMaxSize().clickable(enabled = !l.esEscrito) { imagenesFondo = true; portada = true }, "Fondo de ${l.titulo}")
            else BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth + 24.dp
                PortadaLibro(l, Modifier.align(Alignment.TopCenter).padding(top = 30.dp).requiredWidth(w).requiredHeight(w * 1.5f).clickable(enabled = !l.esEscrito) { imagenesFondo = false; portada = true }, radio = 0, conAutor = false)
            }
            // Un velo arriba para que se lean «Volver» y las acciones sobre cualquier tapa
            Box(Modifier.fillMaxWidth().height(VELO_APERTURA).background(veloArriba()))
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                EnlaceRevista("← Volver", color = PAPEL_CLARO) { alCerrar() }
                Spacer(Modifier.weight(1f))
                // Un escrito de Cobalto se edita allí: aquí no se renombra, ni se le pone portada, ni se borra (volvería al refrescar)
                if (l.esEscrito) { if (Escritos.instaladoCobalto(ctx)) EnlaceRevista("Escribir en Thot", color = PAPEL_CLARO) { Escritos.abrirCobalto(ctx, l.clave.removePrefix("escrito:")) } }
                else Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    EnlaceRevista("Imágenes", color = PAPEL_CLARO) { imagenesFondo = false; portada = true }
                    EnlaceRevista("Editar", color = PAPEL_CLARO) { renombrar = true }
                    EnlaceRevista("Borrar", color = PAPEL_CLARO) { quitar = true }
                }
            }
        }
        // La etiqueta del estado, en el acento, pisando el borde de la foto
        val pct = (frac * 100).toInt().coerceIn(0, 100)
        val etiqueta = when (l.estado) { Estado.LEYENDO, Estado.ABANDONADO -> "${l.estado.nombre.uppercase()} · $pct %"; else -> l.estado.nombre.uppercase() }
        // Con fondo, la portada (120×180) asoma abajo a la derecha pisando el borde de la foto; el título se aparta para no chocar con ella
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(start = 20.dp).offset(y = (-16).dp).background(Acento).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(etiqueta, color = SobreAcento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp, maxLines = 1)
                }
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = if (conFondo) 156.dp else 20.dp)) {
                    FitTitle(l.titulo.uppercase(), 38.sp, 40.sp, color = Charcoal, family = Grotesca, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, maxLines = 4, minSize = 20f)
                    val de = listOfNotNull(l.autor.ifBlank { null }?.let { "de $it" }, miSaga?.let { "${it.nombre}, ${it.claves.indexOf(l.clave) + 1}" }).joinToString(" · ")
                    if (de.isNotBlank()) Text(de, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            // (en una caja de 116 dp que la deja salir 64 dp por arriba: así no estira el hueco hasta el botón)
            if (conFondo) Box(Modifier.align(Alignment.TopEnd).padding(end = 20.dp).width(120.dp).height(116.dp)) {
                PortadaSobreFondo(l, Modifier.align(Alignment.BottomCenter).wrapContentHeight(Alignment.Bottom, unbounded = true).requiredHeight(180.dp).fillMaxWidth(), sombra = 14.dp,
                    alTocar = if (l.esEscrito) null else ({ imagenesFondo = false; portada = true }))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            // La acción principal, ancha y negra; a su lado, lo del archivo
            Row(verticalAlignment = Alignment.CenterVertically) {
                val texto = when {
                    l.enOtroDispositivo && enNube != null -> if (trabajando != null) "Bajando…" else "Bajar de tu kDrive (${tamanoLegible(enNube.s)}) y leer"
                    l.enOtroDispositivo -> "Elegir su archivo"
                    l.estado == Estado.LEIDO && frac >= 0.99f -> "Volver a leer"
                    frac > 0.005f -> "Seguir leyendo"
                    else -> "Empezar a leer"
                }
                BotonC(texto, Modifier.weight(1f)) { principal() }
                if (!l.enOtroDispositivo) Box {
                    Icon(Icons.Rounded.MoreVert, "Opciones del archivo", tint = Charcoal, modifier = Modifier.size(54.dp).clickable { menuArchivo = true }.padding(14.dp))
                    DropdownMenu(menuArchivo, { menuArchivo = false }) {
                        DropdownMenuItem({ Text("Cambiar de archivo") }, { menuArchivo = false; elegirArchivo.launch(arrayOf("*/*")) }, leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) })
                        if (enNube != null) DropdownMenuItem({ Text("Quitar del móvil (sigue en tu kDrive)") }, { menuArchivo = false; quitarArchivo = true }, leadingIcon = { Icon(Icons.Rounded.CloudDone, null) })
                        else {
                            DropdownMenuItem({ Text("Quitar el archivo") }, { menuArchivo = false; quitarArchivo = true }, leadingIcon = { Icon(Icons.Rounded.LinkOff, null) })
                            if (KDrive.on(ctx)) DropdownMenuItem({ Text("Subir a tu kDrive ahora") }, {
                                menuArchivo = false; trabajando = "Subiendo a tu kDrive…"
                                ambito.launch { val n = withContext(Dispatchers.IO) { runCatching { NubeLibros.subirPendientes(ctx, aMano = true) } }; trabajando = null; dice(n.fold({ if (it > 0) "Subido a tu kDrive." else "No he podido subirlo." }, { e -> e.message ?: "No he podido subirlo." })) }
                            }, leadingIcon = { Icon(Icons.Rounded.CloudDownload, null) })
                        }
                    }
                }
            }
            if (l.enOtroDispositivo && enNube != null) {
                Spacer(Modifier.height(8.dp))
                BotonC("O elegir otro archivo", fondo = Paper, letra = Charcoal, modifier = Modifier.drawBehind { drawRect(Charcoal, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())) }) { elegirArchivo.launch(arrayOf("*/*")) }
            }
            if (l.enOtroDispositivo) Text(if (KDrive.on(ctx)) "No está en este dispositivo. Bájalo de tu kDrive o elige su EPUB o PDF (se guarda también en tu kDrive)." else "No está en este dispositivo: elige su EPUB o PDF del móvil (no se sube a ningún sitio).",
                color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
            trabajando?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }

            // ---- 01 Tu lectura: la barra gruesa, cómo vas, el estante (cambiar de estado) y el favorito
            SeccionNumerada(sig(), if (l.esEscrito) "Tu lectura" else "Tu lectura")
            Box(Modifier.fillMaxWidth().height(8.dp).background(Line)) { Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
            val min = l.msLeidos / 60_000L
            val notas = st.optJSONArray("notes")?.length() ?: 0
            val linea = listOfNotNull(
                if (l.paginas > 0) (if (l.estado == Estado.LEIDO || frac <= 0.001f) "${Formato.cifra(l.paginas)} págs" else "${Formato.cifra(Math.round(frac * l.paginas).coerceIn(0, l.paginas))} de ${Formato.cifra(l.paginas)} págs") else if (l.estado != Estado.LEIDO) "$pct %" else null,
                if (min >= 1) (if (frac >= 0.99f) "leído en ${readTime(min)}" else readTime(min)) else null,
                if (frac in 0.02f..0.97f && min >= 5) "te quedan ~${readTime((min * (1 - frac) / frac).toLong().coerceAtLeast(1))}" else null
            ).joinToString(" · ")
            if (linea.isNotBlank()) Text(linea.uppercase(), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 7.dp))
            val otra = listOfNotNull(deck.ifBlank { null }, st.optString("label").ifBlank { null }, if (notas > 0) "$notas subrayados" else null).joinToString(" · ")
            if (otra.isNotBlank()) Text(otra, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
            if (!l.esEscrito) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).drawBehind { drawRect(Charcoal, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())) }) {
                    Estado.entries.forEach { e ->
                        val on = e == l.estado
                        Box(Modifier.weight(1f).fillMaxHeight().background(if (on) Charcoal else Paper).clickable { Biblioteca.poner(l.copy(estado = e)) }.padding(vertical = 12.dp, horizontal = 2.dp), contentAlignment = Alignment.Center) {
                            // (si no cabe, la letra se encoge: nada de «ABANDONAD»)
                            FitTitle(e.nombre.uppercase(), 11.sp, 13.sp, color = if (on) Paper else Charcoal, family = Body, fontWeight = FontWeight.Black, letterSpacing = 0.4.sp, maxLines = 1, unaLinea = true, minSize = 6f,
                                align = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    EnlaceRevista(if (l.favorito) "★ Favorito" else "☆ Marcar como favorito", acento = l.favorito) { Biblioteca.poner(l.copy(favorito = !l.favorito)) }
                }
            }

            // ---- 02 La saga: sus tapas en fila, con esta remarcada
            if (!l.esEscrito) {
                SeccionNumerada(sig(), if (miSaga != null) "La saga" else "Saga", accion = (if (miSaga != null) "${miSaga.claves.indexOf(l.clave) + 1} de ${miSaga.claves.size} →" else "Añadir a una saga →") to { saga = true })
                if (miSaga != null) {
                    val deSaga = remember(miSaga, Biblioteca.libros.size) { Sagas.libros(miSaga) }
                    Text(miSaga.nombre, color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        deSaga.forEach { x ->
                            val esta = x.id == l.id
                            Box(Modifier.then(if (esta) Modifier.border(3.dp, Acento) .padding(3.dp) else Modifier).clickable { saga = true }) {
                                PortadaLibro(x, Modifier.width(56.dp).height(84.dp), radio = 0, conTitulo = false)
                            }
                        }
                    }
                } else Text("No está en ninguna saga.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp)
            }
        // Tiempo y páginas
            SeccionNumerada(sig(), "Tiempo y páginas")
        DosCasillas(
            izq = {
                CabezaCasilla("Tiempo")
                FitTitle(if (l.msLeidos >= 60_000) tiempoDe(l.msLeidos) else "—", 40.sp, 42.sp, unaLinea = true, maxLines = 1, family = Grotesca, fontWeight = FontWeight.ExtraBold)
                Text("LEÍDO EN TOTAL", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Última vez: ${haceCuando(l.ultima)}", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            },
            der = {
                CabezaCasilla("Páginas", "Editar" to { renombrar = true })
                FitTitle(if (l.paginas > 0) "${l.paginas}" else "—", 40.sp, 42.sp, unaLinea = true, maxLines = 1, family = Grotesca, fontWeight = FontWeight.ExtraBold)
                Text("EN TOTAL", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(when (l.paginasOrigen) { "manual" -> "las que has puesto tú"; "atrio" -> "las de Atrio"; "pdf" -> "las del PDF"; "openlibrary" -> "las de su edición (Open Library)"; "estimado" -> "calculadas por el texto"; else -> "sin dato: ponlas en Editar" },
                    color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        )
        Filete(2.dp)

        // La valoración de Azabache (solo se ve; se hace allí). Un escrito de Cobalto no se valora.
        if (Cuenta.vinculada && !l.esEscrito) {
            var nota by remember(l.clave, NotaAzabache.version) { mutableStateOf(NotaAzabache.cargar(ctx)[l.clave]) }
            LaunchedEffect(l.clave) {
                if (NotaAzabache.vieja(ctx)) withContext(Dispatchers.IO) { NotaAzabache.refrescar(ctx) }.let { nota = NotaAzabache.cargar(ctx)[l.clave] }
            }
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp)) {
                SeccionNumerada(sig(), "En Jonsu", arriba = 0.dp)
                if (nota != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        FitTitle("${nota!!.general}", 48.sp, 50.sp, unaLinea = true, maxLines = 1, family = Grotesca, fontWeight = FontWeight.ExtraBold)
                        Text(" /100", color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    if (nota!!.resena.isNotBlank()) Text(nota!!.resena, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    if (NotaAzabache.instalado(ctx)) EnlaceRevista("Abrir en Jonsu →", acento = true) { NotaAzabache.abrir(ctx, nota) }
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    // Una línea: lo que hay y qué hacer
                    Text(if (NotaAzabache.instalado(ctx)) "Sin valorar aún · " else "Sin valorar aún", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                    if (NotaAzabache.instalado(ctx)) EnlaceRevista("Valorar en Jonsu →", acento = true) { NotaAzabache.abrir(ctx, NotaAzabache.Nota(l.id, 0, "")) }
                }
            }
            Filete(2.dp)
        }

        // Subrayados y notas
        SeccionNumerada(sig(), "Subrayados y notas")
        SeccionNotas(l, conTitulo = false)
        Spacer(Modifier.height(18.dp))
        Filete(2.dp)

        // Datos
        Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)) {
            SeccionNumerada(sig(), "Datos", "Editar" to { renombrar = true }, arriba = 0.dp)
            MagFact("Título", l.titulo)
            MagFact("Autor", l.autor.ifBlank { "—" })
            if (miSaga != null) MagFact("Saga", "${miSaga.nombre} · ${miSaga.claves.indexOf(l.clave) + 1}")
            MagFact("Formato", l.formato.uppercase().ifBlank { "—" })
            if (l.terminado > 0) MagFact("Terminado", fecha(l.terminado))
            if (l.terminados.size > 1) MagFact("Lecturas", "${l.terminados.size}")
            if (l.paginas > 0) MagFact("Páginas", "${l.paginas}")
            MagFact("Archivo", if (l.enOtroDispositivo) "no está en este dispositivo" else l.archivo)
            if (ficheroLocal != null) MagFact("Tamaño", tamanoLegible(ficheroLocal.length()))
            MagFact("En tu kDrive", when { enNube != null -> "sí"; KDrive.on(ctx) -> "todavía no"; else -> "kDrive sin conectar" })
            if (l.anadido > 0) MagFact("Añadido", fecha(l.anadido))
        }
        Spacer(Modifier.height(30.dp))
        }
    }

    if (portada) SelectorImagenes(l, fondo = imagenesFondo) { portada = false }

    if (saga) DialogoSagaDeLibro(l) { saga = false }

    if (renombrar) {
        var t by remember { mutableStateOf(l.titulo) }; var a by remember { mutableStateOf(l.autor) }; var pg by remember { mutableStateOf(if (l.paginas > 0) l.paginas.toString() else "") }
        AlertDialog(
            onDismissRequest = { renombrar = false }, containerColor = Paper, shape = esquina(4.dp),
            title = { Text("Datos del libro", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
            text = { Column { CampoRevista("Título", t, "título") { t = it }; CampoRevista("Autor", a, "autor") { a = it }; CampoRevista("Páginas", pg, "nº de páginas") { pg = it.filter { c -> c.isDigit() }.take(5) }
                Text("El libro sigue siendo el mismo en todos tus dispositivos: lo leído y tus subrayados no se tocan.", color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) } },
            confirmButton = { TextButton({
                if (t.isNotBlank()) {
                    val n = pg.toIntOrNull() ?: 0
                    val base = l.copy(titulo = t.trim(), autor = a.trim())
                    Biblioteca.poner(if (n == l.paginas) base else if (n > 0) base.copy(paginas = n, paginasOrigen = "manual") else base.copy(paginas = 0, paginasOrigen = ""))
                    renombrar = false
                }
            }) { Text("GUARDAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
            dismissButton = { TextButton({ renombrar = false }) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
        )
    }

    if (quitarArchivo) AlertDialog(
        onDismissRequest = { quitarArchivo = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Quitar el archivo?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text(if (enNube != null) "Se borra de este dispositivo y puedes volver a bajarlo de tu kDrive cuando quieras. Lo leído y tus subrayados se quedan." else "Ojo: este libro no está en tu kDrive, así que no podrás recuperarlo si no tienes el archivo. Lo leído y tus subrayados se quedan.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({ Metadatos.quitarArchivo(ctx, l); quitarArchivo = false }) { Text("QUITAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ quitarArchivo = false }) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )

    if (quitar) AlertDialog(
        onDismissRequest = { quitar = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Quitar «${l.titulo}»?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text("Se quita de tu biblioteca (en tus otros dispositivos también) y se borra su archivo de este móvil. En tu kDrive no se toca nada.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({
            runCatching { File(Uri.parse(l.uri).path.orEmpty()).takeIf { it.exists() }?.delete() }
            Biblioteca.quitar(l.id); quitar = false; alCerrar()
        }) { Text("QUITAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ quitar = false }) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}


/** El color medio de la imagen de una portada (para la cabecera de la ficha), o null si no hay imagen o no se puede leer. */
fun colorDePortada(ruta: String): androidx.compose.ui.graphics.Color? = runCatching {
    val f = File(ruta).takeIf { ruta.isNotBlank() && it.exists() } ?: return null
    val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 16 }
    val b = android.graphics.BitmapFactory.decodeFile(f.path, o) ?: return null
    val p = android.graphics.Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)
    androidx.compose.ui.graphics.Color(p)
}.getOrNull()


/**
 * La línea en cursiva de «Tu lectura». «Terminado el …» solo en un libro Leído (uno que vuelves a leer, o que pasó a Leyendo, puede guardar
 * fechas de fin de antes: ahí manda cuándo lo leíste por última vez).
 */
fun lineaDeLectura(l: Libro, fecha: (Long) -> String, hace: (Long) -> String): String {
    val terminado = l.estado == Estado.LEIDO && l.terminado > 0
    return listOfNotNull(
        if (terminado) "terminado el ${fecha(l.terminado)}" + if (l.terminados.size > 1) " · ${l.terminados.size}.ª vez" else "" else null,
        // Si ya lo terminaste, la fecha de terminado dice lo importante: no se repite «leído por última vez»
        if (l.ultima > 0 && !terminado) "leído por última vez ${hace(l.ultima)}" else null,
        if (l.msLeidos >= 60_000) "${tiempoDe(l.msLeidos)} leídos" else null
    ).joinToString(" · ")
}

/** El alto del velo de arriba de la apertura (para que se lean «Volver» y las acciones sobre cualquier foto). */
val VELO_APERTURA = 110.dp

/** El velo: negro al 80 % arriba, transparente abajo. */
fun veloArriba() = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f), androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f), androidx.compose.ui.graphics.Color.Transparent))
