package com.gonzalo.marfil

import com.gonzalo.mesa.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.DeleteOutline

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
        Text("%02d".format(n), color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1)
        Text(texto, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 10.dp, bottom = 1.dp))
        accion?.let { (t, f) -> Text(t, color = Acento, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
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
        Text(texto, color = Charcoal, fontFamily = Grotesca, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        accion?.let { (t, f) -> Text(t, color = Acento, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = f).padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) }
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

    val tinta = com.gonzalo.mesa.LocalTinta.current
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        // ---- La cabecera: la portada sobre un lavado de su color; arriba, volver y las acciones
        val tela = remember(l.portada, l.titulo) { colorDePortada(l.portada) ?: telaDe(l.titulo) }
        val conFondo = l.fondo.isNotBlank()
        val pct = (frac * 100).toInt().coerceIn(0, 100)
        Lavado(color = tela) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconoM(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") { alCerrar() }
                Spacer(Modifier.weight(1f))
                // Un escrito de Thot se edita allí: aquí no se renombra, ni se le pone portada, ni se borra (volvería al refrescar)
                if (l.esEscrito) { if (Escritos.instaladoCobalto(ctx)) BotonTextoM("Escribir en Thot") { Escritos.abrirCobalto(ctx, l.clave.removePrefix("escrito:")) } }
                else {
                    IconoM(Icons.Rounded.Image, "Imágenes") { imagenesFondo = false; portada = true }
                    IconoM(Icons.Rounded.Edit, "Editar") { renombrar = true }
                    IconoM(Icons.Rounded.DeleteOutline, "Borrar") { quitar = true }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Con fondo elegido: el fondo a lo ancho y la portada pisándolo por abajo
            if (conFondo) {
                ImagenEncuadrada(l.fondo, l.encFondo, Modifier.padding(horizontal = 20.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).clickable(enabled = !l.esEscrito) { imagenesFondo = true; portada = true }, "Fondo de ${l.titulo}")
                PortadaLibro(l, Modifier.offset(y = (-56).dp).width(112.dp).aspectRatio(2f / 3f).clip(Medida.portada).clickable(enabled = !l.esEscrito) { imagenesFondo = false; portada = true }, radio = 0, conAutor = false)
                            } else PortadaLibro(l, Modifier.width(150.dp).aspectRatio(2f / 3f).clip(Medida.portada).clickable(enabled = !l.esEscrito) { imagenesFondo = false; portada = true }, radio = 0, conAutor = false)
            Column(Modifier.fillMaxWidth().offset(y = if (conFondo) (-44).dp else 0.dp).padding(horizontal = 20.dp).padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TituloM(l.titulo, tam = 26, lineas = 5, centro = true)
                val de = listOfNotNull(l.autor.ifBlank { null }, miSaga?.let { "${it.nombre}, ${it.claves.indexOf(l.clave) + 1}" }).joinToString(" · ")
                if (de.isNotBlank()) NotaM(de, Modifier.padding(top = 4.dp), tam = 15, centro = true)
                TextoM(when (l.estado) { Estado.LEYENDO, Estado.ABANDONADO -> "${l.estado.nombre} · $pct %"; else -> l.estado.nombre }, Modifier.padding(top = 6.dp), 14, tinta.acento, 1, FontWeight.SemiBold)
            }
        }
        Column(Modifier.fillMaxWidth().offset(y = if (conFondo) (-36).dp else 0.dp).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Spacer(Modifier.height(18.dp))
            // La acción principal; a su lado, lo del archivo
            Row(verticalAlignment = Alignment.CenterVertically) {
                val texto = when {
                    l.enOtroDispositivo && enNube != null -> if (trabajando != null) "Bajando…" else "Bajar de tu kDrive (${tamanoLegible(enNube.s)}) y leer"
                    l.enOtroDispositivo -> "Elegir su archivo"
                    l.estado == Estado.LEIDO && frac >= 0.99f -> "Volver a leer"
                    frac > 0.005f -> "Seguir leyendo"
                    else -> "Empezar a leer"
                }
                BotonM(texto, Modifier.weight(1f)) { principal() }
                if (!l.enOtroDispositivo) Box(Modifier.padding(start = 6.dp)) {
                    IconoM(Icons.Rounded.MoreVert, "Opciones del archivo", conFondo = true) { menuArchivo = true }
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
            if (l.enOtroDispositivo && enNube != null) { Spacer(Modifier.height(8.dp)); BotonM("O elegir otro archivo", principal = false, flecha = true) { elegirArchivo.launch(arrayOf("*/*")) } }
            if (l.enOtroDispositivo) NotaM(if (KDrive.on(ctx)) "No está en este dispositivo. Bájalo de tu kDrive o elige su EPUB o PDF (se guarda también en tu kDrive)." else "No está en este dispositivo: elige su EPUB o PDF del móvil (no se sube a ningún sitio).", Modifier.padding(top = 8.dp))
            trabajando?.let { TextoM(it, Modifier.padding(top = 8.dp), 15, tinta.acento, peso = FontWeight.SemiBold) }

            // ---- Tu lectura: la barra, cómo vas, el estado y lo de favorito y la cola
            Spacer(Modifier.height(22.dp))
            AvanceM(frac, alto = 8.dp)
            val min = l.msLeidos / 60_000L
            val notas = st.optJSONArray("notes")?.length() ?: 0
            val linea = listOfNotNull(
                if (l.paginas > 0) (if (l.estado == Estado.LEIDO || frac <= 0.001f) "${Formato.cifra(l.paginas)} págs" else "${Formato.cifra(Math.round(frac * l.paginas).coerceIn(0, l.paginas))} de ${Formato.cifra(l.paginas)} págs") else if (l.estado != Estado.LEIDO) "$pct %" else null,
                if (min >= 1) (if (frac >= 0.99f) "leído en ${readTime(min)}" else readTime(min)) else null,
                if (frac in 0.02f..0.97f && min >= 5) "te quedan unas ${readTime((min * (1 - frac) / frac).toLong().coerceAtLeast(1))}" else null
            ).joinToString(" · ")
            if (linea.isNotBlank()) TextoM(linea, Modifier.padding(top = 10.dp), 15, peso = FontWeight.SemiBold)
            val otra = listOfNotNull(deck.ifBlank { null }, st.optString("label").ifBlank { null }, if (notas > 0) "$notas subrayados" else null).joinToString(" · ")
            if (otra.isNotBlank()) NotaM(otra, Modifier.padding(top = 4.dp), tam = 14)
            if (!l.esEscrito) {
                Spacer(Modifier.height(16.dp))
                Estados(Estado.entries.map { it.nombre }, l.estado.ordinal) { i -> Biblioteca.poner(l.copy(estado = Estado.entries[i])) }
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonTextoM(if (l.favorito) "★ Favorito" else "☆ Marcar como favorito", peligro = l.favorito) { Biblioteca.poner(l.copy(favorito = !l.favorito)) }
                    if (l.estado == Estado.PENDIENTE) BotonTextoM(if (Cola.esta(l.clave)) "Quitar de lo siguiente" else "Poner el siguiente", peligro = Cola.esta(l.clave)) { Cola.alterna(ctx, l.clave) }
                }
            }

            // ---- La saga: sus tapas en fila, con esta remarcada
            if (!l.esEscrito) {
                Spacer(Modifier.height(26.dp))
                Rotulo(if (miSaga != null) "La saga · ${miSaga.nombre}" else "Saga", enlace = if (miSaga != null) "${miSaga.claves.indexOf(l.clave) + 1} de ${miSaga.claves.size} →" else "Añadir a una saga →", alEnlace = { saga = true })
                if (miSaga != null) {
                    val deSaga = remember(miSaga, Biblioteca.libros.size) { Sagas.libros(miSaga) }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        deSaga.forEach { x ->
                            val esta = x.id == l.id
                            Box(Modifier.clip(RoundedCornerShape(10.dp)).then(if (esta) Modifier.border(3.dp, Acento, RoundedCornerShape(10.dp)).padding(3.dp) else Modifier).clickable { saga = true }) {
                                PortadaLibro(x, Modifier.width(56.dp).height(84.dp).clip(Medida.portada), radio = 0, conTitulo = false)
                            }
                        }
                    }
                } else NotaM("No está en ninguna saga.", Modifier.padding(top = 4.dp), tam = 14)
            }

            // ---- Tiempo y páginas
            Spacer(Modifier.height(26.dp))
            RejillaCifras(listOf(
                BloqueCifra(if (l.msLeidos >= 60_000) tiempoDe(l.msLeidos) else "—", "leído en total · última vez ${haceCuando(l.ultima)}", Crudo, Charcoal),
                BloqueCifra(if (l.paginas > 0) "${l.paginas}" else "—", "páginas · " + when (l.paginasOrigen) { "manual" -> "las que has puesto tú"; "atrio" -> "las de Atrio"; "pdf" -> "las del PDF"; "openlibrary" -> "las de su edición"; "estimado" -> "calculadas por el texto"; else -> "sin dato: ponlas en Editar" }, Crudo, Charcoal) { renombrar = true },
            ))

            // La valoración de Jonsu (solo se ve; se hace allí). Un escrito de Thot no se valora.
            if (Cuenta.vinculada && !l.esEscrito) {
                var nota by remember(l.clave, NotaAzabache.version) { mutableStateOf(NotaAzabache.cargar(ctx)[l.clave]) }
                LaunchedEffect(l.clave) {
                    if (NotaAzabache.vieja(ctx)) withContext(Dispatchers.IO) { NotaAzabache.refrescar(ctx) }.let { nota = NotaAzabache.cargar(ctx)[l.clave] }
                }
                Spacer(Modifier.height(14.dp))
                val puedeAbrir = NotaAzabache.instalado(ctx)
                TarjetaM(alHacer = if (puedeAbrir) ({ NotaAzabache.abrir(ctx, nota ?: NotaAzabache.Nota(l.id, 0, "")) }) else null) {
                    Rotulo("En Jonsu")
                    if (nota != null) {
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) { TituloM("${nota!!.general}", tam = 34, lineas = 1); NotaM(" de 100", Modifier.padding(bottom = 6.dp), lineas = 1) }
                        if (nota!!.resena.isNotBlank()) NotaM(nota!!.resena, Modifier.padding(top = 4.dp), tam = 14, lineas = 3)
                        if (puedeAbrir) TextoM("Abrir en Jonsu →", Modifier.padding(top = 8.dp), 15, tinta.acento, peso = FontWeight.SemiBold)
                    } else {
                        NotaM("Sin valorar aún.", Modifier.padding(top = 4.dp), tam = 14)
                        if (puedeAbrir) TextoM("Valorar en Jonsu →", Modifier.padding(top = 8.dp), 15, tinta.acento, peso = FontWeight.SemiBold)
                    }
                }
            }

            // ---- Subrayados y notas
            Spacer(Modifier.height(26.dp))
            Rotulo("Subrayados y notas")
            Spacer(Modifier.height(6.dp))
            SeccionNotas(l, conTitulo = false)

            // ---- Datos
            Spacer(Modifier.height(26.dp))
            Rotulo("Datos", enlace = if (l.esEscrito) null else "Editar", alEnlace = { renombrar = true })
            Dato("Título", l.titulo)
            Dato("Autor", l.autor.ifBlank { "—" })
            if (miSaga != null) Dato("Saga", "${miSaga.nombre} · ${miSaga.claves.indexOf(l.clave) + 1}")
            Dato("Formato", l.formato.uppercase().ifBlank { "—" })
            if (l.terminado > 0) Dato("Terminado", fecha(l.terminado))
            if (l.terminados.size > 1) Dato("Lecturas", "${l.terminados.size}")
            Dato("Archivo", if (l.enOtroDispositivo) "no está en este dispositivo" else l.archivo)
            if (ficheroLocal != null) Dato("Tamaño", tamanoLegible(ficheroLocal.length()))
            Dato("En tu kDrive", when { enNube != null -> "sí"; KDrive.on(ctx) -> "todavía no"; else -> "kDrive sin conectar" })
            if (l.anadido > 0) Dato("Añadido", fecha(l.anadido))
            Spacer(Modifier.height(30.dp))
        }
    }

    if (portada) SelectorImagenes(l, fondo = imagenesFondo) { portada = false }

    if (saga) DialogoSagaDeLibro(l) { saga = false }

    if (renombrar) {
        var t by remember { mutableStateOf(l.titulo) }; var a by remember { mutableStateOf(l.autor) }; var pg by remember { mutableStateOf(if (l.paginas > 0) l.paginas.toString() else "") }
        AlertDialog(
            onDismissRequest = { renombrar = false }, containerColor = Crudo, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            title = { Text("Datos del libro", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold) },
            text = { Column { CampoRevista("Título", t, "título") { t = it }; CampoRevista("Autor", a, "autor") { a = it }; CampoRevista("Páginas", pg, "nº de páginas") { pg = it.filter { c -> c.isDigit() }.take(5) }
                Text("El libro sigue siendo el mismo en todos tus dispositivos: lo leído y tus subrayados no se tocan.", color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) } },
            confirmButton = { TextButton({
                if (t.isNotBlank()) {
                    val n = pg.toIntOrNull() ?: 0
                    val base = l.copy(titulo = t.trim(), autor = a.trim())
                    Biblioteca.poner(if (n == l.paginas) base else if (n > 0) base.copy(paginas = n, paginasOrigen = "manual") else base.copy(paginas = 0, paginasOrigen = ""))
                    renombrar = false
                }
            }) { Text("Guardar", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton({ renombrar = false }) { Text("Cancelar", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold) } }
        )
    }

    if (quitarArchivo) AlertDialog(
        onDismissRequest = { quitarArchivo = false }, containerColor = Crudo, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = { Text("¿Quitar el archivo?", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold) },
        text = { Text(if (enNube != null) "Se borra de este dispositivo y puedes volver a bajarlo de tu kDrive cuando quieras. Lo leído y tus subrayados se quedan." else "Ojo: este libro no está en tu kDrive, así que no podrás recuperarlo si no tienes el archivo. Lo leído y tus subrayados se quedan.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({ Metadatos.quitarArchivo(ctx, l); quitarArchivo = false }) { Text("Quitar", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton({ quitarArchivo = false }) { Text("Cancelar", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold) } }
    )

    if (quitar) AlertDialog(
        onDismissRequest = { quitar = false }, containerColor = Crudo, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = { Text("¿Quitar «${l.titulo}»?", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold) },
        text = { Text("Se quita de tu biblioteca (en tus otros dispositivos también) y se borra su archivo de este móvil. En tu kDrive no se toca nada.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({
            runCatching { File(Uri.parse(l.uri).path.orEmpty()).takeIf { it.exists() }?.delete() }
            Biblioteca.quitar(l.id); quitar = false; alCerrar()
        }) { Text("Quitar", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton({ quitar = false }) { Text("Cancelar", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold) } }
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
