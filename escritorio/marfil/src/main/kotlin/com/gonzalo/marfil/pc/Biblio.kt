package com.gonzalo.marfil.pc

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.AjustesCuenta
import com.gonzalo.familia.pc.AjustesVersion
import com.gonzalo.familia.pc.KDrive
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Portadas
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale.forLanguageTag("es-ES")
fun fechaDe(ms: Long): String = if (ms <= 0) "" else java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy", ES))
fun tiempoDe(min: Long): String = if (min < 60) "$min min" else "${min / 60} h" + if (min % 60 > 0) " ${min % 60} min" else ""
fun avance(l: Libro) = if (l.estado == Estado.LEIDO) "Terminado" else "${(l.fraccion * 100).toInt()} %"

/** Un libro en la cuadrícula: su portada, lo que llevas y su título. Al pasar el ratón, se levanta un poco (como al cogerlo de la estantería). */
@Composable
fun TarjetaLibro(l: Libro, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val h = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val encima by h.collectIsHoveredAsState()
    val alza by androidx.compose.animation.core.animateFloatAsState(if (encima) 1f else 0f, androidx.compose.animation.core.tween(160), label = "alza")
    Column(modifier.hoverable(h).pointerHoverIcon(PointerIcon.Hand).graphicsLayer { translationY = -5f * density * alza }.clickable(onClick = onClick)) {
        PortadaCosa(l.portada, l.portadaUrl, l.titulo, Modifier.fillMaxWidth(), pie = l.autor)
        if (l.estado == Estado.LEYENDO || (l.fraccion > 0 && l.estado != Estado.LEIDO)) {
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).background(Sand)) { Box(Modifier.fillMaxWidth(l.fraccion).fillMaxHeight().background(Acento)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(l.titulo, color = Charcoal, fontFamily = Display, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOf(l.autor, if (l.estado == Estado.LEIDO) fechaDe(l.terminados.lastOrNull() ?: 0L) else avance(l)).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontFamily = Body, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * La ficha de un libro, a doble página (como en el móvil, abierta): a la izquierda su portada con su paspartú sobre la tinta; a la derecha
 * cómo va, leerlo, tu lectura, tus subrayados y lo que dice Jonsu. Nace de la fila que pulsaste; «← Volver» o Esc la cierran.
 */
@Composable
fun PantFicha(id: String, nav: Nav, alCerrar: () -> Unit) {
    LaFamilia.cambios
    val l = Libros.por(id) ?: run { PaginaC { EnlaceCabecera("← Volver", Charcoal) { alCerrar() }; Vacio("Este libro ya no está") }; return }
    val volver = Pantalla.Ficha(id, Pantalla.Portada)
    val scope = rememberCoroutineScope()
    var borrar by remember { mutableStateOf(false) }
    var trabajo by remember { mutableStateOf("") }
    val aqui = Libros.archivo(id)
    fun leer() { if (aqui != null || l.nube != null) nav.ir(Pantalla.Leer(id, volver)) else nav.aviso("Este libro no está en este ordenador ni en tu kDrive: súbelo desde el móvil donde lo tienes.") }
    Row(Modifier.fillMaxSize().background(Paper)) {
        // ---- La página de la izquierda: la portada sobre la tinta
        Column(Modifier.width(500.dp).fillMaxHeight().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF3A2418), androidx.compose.ui.graphics.Color(0xFF0C0A09))))
            .padding(horizontal = 44.dp, vertical = 30.dp)) {
            EnlaceCabecera("← Volver", Paper, atajo = "esc") { alCerrar() }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { TapaConPaspartu(l, 290.dp) }
            Text(listOfNotNull(l.formato.uppercase().ifBlank { null }, if (l.paginas > 0) "${l.paginas} páginas" else null, if (l.anadido > 0) "añadido el " + fechaDe(l.anadido) else null).joinToString(" · ").uppercase(),
                color = Paper.copy(alpha = 0.7f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.3.sp)
        }
        // ---- La página de la derecha
        val scroll = rememberScrollState()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 56.dp, vertical = 44.dp).widthIn(max = 760.dp)) {
                Text(l.estado.nombre.uppercase(), color = SobreAcento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.6.sp, modifier = Modifier.background(Acento).padding(horizontal = 10.dp, vertical = 6.dp))
                Text(l.titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, lineHeight = 54.sp, modifier = Modifier.padding(top = 16.dp))
                if (l.autor.isNotBlank()) Text(l.autor.uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 10.dp))
                if (l.estado != Estado.PENDIENTE) Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(8.dp).background(Line)) { Box(Modifier.fillMaxWidth(if (l.estado == Estado.LEIDO) 1f else l.fraccion).fillMaxHeight().background(Acento)) }
                Spacer(Modifier.height(20.dp))
                BotonC(if (l.fraccion > 0 && l.estado != Estado.LEIDO) "Seguir leyendo" else "Leer", fondo = Charcoal, letra = Paper) { leer() }
                Text(when { aqui != null -> "El archivo está en este ordenador" + if (l.nube != null) " y en tu kDrive." else "."; l.nube != null -> "Está en tu kDrive: se baja al abrirlo."; else -> "El archivo no está aquí ni en tu kDrive." },
                    color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (aqui != null && l.nube == null && KDrive().activo) EnlaceRevista(if (trabajo.isNotBlank()) trabajo else "Subir a tu kDrive", modifier = Modifier.padding(top = 6.dp)) {
                        trabajo = "Subiendo…"; scope.launch { val r = withContext(Dispatchers.IO) { runCatching { NubeLibros.sube(l, aqui) } }; trabajo = ""; nav.aviso(r.fold({ "En tu kDrive" }, { it.message ?: "No se ha podido" })) }
                    }
                    if (aqui == null && l.nube != null) EnlaceRevista(if (trabajo.isNotBlank()) trabajo else "Bajar a este ordenador", modifier = Modifier.padding(top = 6.dp)) {
                        trabajo = "Bajando…"; scope.launch { val r = withContext(Dispatchers.IO) { runCatching { NubeLibros.baja(l) } }; trabajo = ""; nav.aviso(r.fold({ "Ya está aquí" }, { it.message ?: "No se ha podido" })) }
                    }
                }
                Spacer(Modifier.height(26.dp))
                TituloC("Cómo va")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Estado.entries.forEach { e -> FiltroC(e.nombre, e == l.estado) { Libros.ponEstado(l, e) } } }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                    Text("Favorito", Modifier.weight(1f), color = Charcoal, fontFamily = Grotesca, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Interruptor(l.fav) { Libros.cambia(id, mapOf("fav" to it)) }
                }
                Spacer(Modifier.height(26.dp))
                TituloC("Tu lectura")
                MagFact("Llevas", avance(l))
                Libros.posicion(id)?.let { p -> if (p.etiqueta.isNotBlank()) MagFact("Ibas por", p.etiqueta); if (p.cuando > 0) MagFact("Última vez", fechaDe(p.cuando)) }
                MagFact("Tiempo leyendo", tiempoDe(l.minutos))
                if (l.paginas > 0) MagFact("Páginas", l.paginas.toString())
                l.terminados.takeIf { it.isNotEmpty() }?.let { MagFact(if (it.size > 1) "Lo terminaste" else "Terminado", it.joinToString(", ") { t -> fechaDe(t) }) }
                SubrayadosDeLaFicha(l, nav, volver, aqui != null || l.nube != null)
                Spacer(Modifier.height(26.dp))
                TituloC("En Jonsu")
                Text(l.nota?.toString() ?: "Sin valorar", color = if (l.nota != null) Acento else Muted, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = if (l.nota != null) 44.sp else 22.sp)
                l.n.texto("resena").takeIf { it.isNotBlank() }?.let { Text("«$it»", color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 26.sp) }
                Spacer(Modifier.height(30.dp))
                EnlaceRevista("Borrar de tu biblioteca", acento = true) { borrar = true }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
    if (borrar) Dialogo("¿Borrar «${l.titulo}»?", { borrar = false }, botones = {
        BotonSuave("Cancelar") { borrar = false }
        BotonSuave("Borrar", peligro = true) { Libros.quita(id); borrar = false; alCerrar(); nav.aviso("Borrado") }
    }) { Cuerpo("Sale de tu biblioteca en todos tus dispositivos (y de Jonsu). El archivo de tu kDrive no se borra.", tam = 14) }
}

/**
 * «Subrayados y notas» en la ficha (como en la del móvil): los de todos tus dispositivos, en el orden del libro; tocar uno abre el libro
 * justo ahí (si el archivo está en tu kDrive, se baja antes).
 */
@Composable
private fun SubrayadosDeLaFicha(l: Libro, nav: Nav, volver: Pantalla, sePuedeAbrir: Boolean) {
    val notas = Marcas.de(l.id, Marcas.SUBRAYADOS).filter { !it.delTextoDeUnPdf }
        .sortedWith { a, b -> val x = EpubCfi.lee(a.valor); val y = EpubCfi.lee(b.valor); (if (x != null && y != null) EpubCfi.compara(x, y) else 0).takeIf { it != 0 } ?: a.creada.compareTo(b.creada) }
    var todas by remember(l.id) { mutableStateOf(false) }
    Spacer(Modifier.height(26.dp))
    TituloC("Subrayados y notas")
    if (notas.isEmpty()) {
        Text("Aún no has subrayado nada. En el lector, selecciona un trozo con el ratón y elige Subrayar o Nota (lo que subrayes en el móvil sale aquí).",
            color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp)
        return
    }
    val conNota = notas.count { it.nota.isNotBlank() }
    Etiqueta(listOfNotNull("${notas.size} subrayado${if (notas.size == 1) "" else "s"}", if (conNota > 0) "$conNota con nota" else null).joinToString(" · "))
    fun abre(m: Marca?) {
        if (sePuedeAbrir) nav.ir(Pantalla.Leer(l.id, Pantalla.Ficha(l.id, volver), ir = m?.valor.orEmpty(), panel = if (m == null) Panel.NOTAS else null))
        else nav.aviso("Este libro no está en este ordenador ni en tu kDrive: súbelo desde el móvil donde lo tienes.")
    }
    (if (todas) notas else notas.take(4)).forEachIndexed { i, m ->
        if (i > 0) Filete(1.dp, Line)
        Column(Modifier.fillMaxWidth().clickable { abre(m) }.padding(vertical = 10.dp)) {
            Text("«${m.texto}»", color = Charcoal, fontFamily = Display, fontSize = 17.sp, lineHeight = 23.sp, maxLines = if (todas) 12 else 5, overflow = TextOverflow.Ellipsis)
            if (m.nota.isNotBlank()) Text(m.nota, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
            Etiqueta(listOfNotNull(m.etiqueta.ifBlank { null }, if (m.creada > 0) fechaDe(m.creada) else null).joinToString(" · "), Modifier.padding(top = 4.dp))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        if (notas.size > 4) EnlaceRevista(if (todas) "Ver menos" else "Ver los ${notas.size} →", acento = true) { todas = !todas }
        EnlaceRevista("Abrir tus notas en el lector →") { abre(null) }
    }
}

/** Añadir un libro desde un archivo de este ordenador: EPUB, PDF o cómic (CBZ). */
@Composable
fun DialogoAnadir(nav: Nav, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var trabajo by remember { mutableStateOf("") }
    fun elige() {
        val f = eligeArchivo("Elige un libro", listOf("epub", "pdf", "cbz")) ?: return
        trabajo = "Añadiendo…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { anadeArchivo(f) } }
            trabajo = ""
            r.onSuccess { id -> onCerrar(); nav.aviso("Añadido"); nav.ir(Pantalla.Ficha(id, Pantalla.Lista(Estado.PENDIENTE))) }.onFailure { nav.aviso(it.message ?: "No se ha podido añadir") }
        }
    }
    Dialogo("Añadir un libro", onCerrar, botones = {
        BotonSuave("Cancelar") { onCerrar() }
        BotonAcento(trabajo.ifBlank { "Elegir el archivo…" }, modifier = Modifier.width(200.dp), alto = 44.dp) { if (trabajo.isBlank()) elige() }
    }) {
        Cuerpo("EPUB, PDF o cómic (CBZ). El libro pasa a tu biblioteca (la de la cuenta: lo verás también en el móvil y en Jonsu) y el archivo sube a tu kDrive, a «Marfil/Libros», para leerlo en cualquier dispositivo.", tam = 14)
    }
}

/** Mete un archivo en la biblioteca: sus datos, su portada (a la cuenta), una copia aquí y, si hay kDrive, arriba. Devuelve el id. */
fun anadeArchivo(f: File): String {
    val ext = f.extension.lowercase()
    var titulo = ""; var autor = ""; var portada: ByteArray? = null; var paginas = 0
    when (ext) {
        "epub" -> Epub(f).use { e -> titulo = e.titulo; autor = e.autor; portada = runCatching { e.portada() }.getOrNull() }
        "pdf" -> Pdf(f).use { p -> titulo = p.titulo; autor = p.autor; paginas = p.total; portada = runCatching { p.primeraJpeg() }.getOrNull() }
        "cbz" -> Comic(f).use { c -> paginas = c.total }
        else -> error("Seshat del ordenador lee EPUB, PDF y cómics (CBZ).")
    }
    if (titulo.isBlank()) {
        val base = f.nameWithoutExtension.replace('_', ' ').replace(Regex("\\s+"), " ").trim()
        val partes = base.split(" - ").map { it.trim() }.filter { it.isNotEmpty() }
        if (partes.size >= 2) { titulo = partes[0]; autor = partes.drop(1).joinToString(" - ") } else titulo = base
    }
    val id = Libros.anade(titulo, autor, "${titulo.take(80)}.$ext", paginas)
    Libros.guardaCopia(id, f)
    portada?.let { b -> if (Libros.por(id)?.portada.isNullOrBlank()) Portadas.guardar(b)?.let { h -> Libros.cambia(id, mapOf("portada" to h)) } }
    Thread({ runCatching { Libros.por(id)?.let { l -> if (l.nube == null && NubeLibros.kdrive.activo) Libros.archivo(id)?.let { NubeLibros.sube(l, it) } } } }, "marfil-sube").apply { isDaemon = true; start() }
    return id
}

@Composable
fun PantAjustes(nav: Nav, alCerrar: () -> Unit) {
    var pestana by remember { mutableStateOf(0) }
    PaginaC(ancho = 960.dp) {
        EnlaceCabecera("← Volver", Charcoal, atajo = "esc") { alCerrar() }
        NombreConPunto("Ajustes", 72.sp, Charcoal, Modifier.padding(top = 10.dp, bottom = 6.dp))
        Filete(3.dp)
        Spacer(Modifier.height(14.dp))
        PestanasRevista(listOf("Cuenta", "Libros", "Apariencia", "Versión"), pestana) { pestana = it }
        Spacer(Modifier.height(22.dp))
        when (pestana) {
            0 -> AjustesCuenta("marfil")
            1 -> {
                Antetitulo("Tus libros en este ordenador")
                Spacer(Modifier.height(6.dp))
                MagFact("Carpeta", Libros.carpeta().absolutePath)
                MagFact("Tu kDrive", if (KDrive().activo) "conectado (las claves son de la cuenta)" else "sin poner (en Jonsu: Ajustes → Servicios)")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BotonSuave("Abrir la carpeta") { com.gonzalo.familia.pc.Sitio.abre(Libros.carpeta().absolutePath) }
                    val scope = rememberCoroutineScope()
                    BotonSuave("Subir lo que falte a tu kDrive") { scope.launch { val n = withContext(Dispatchers.IO) { NubeLibros.subePendientes() }; nav.aviso(if (n == 0) "No faltaba nada" else "Subidos: $n") } }
                }
            }
            2 -> {
                Antetitulo("Apariencia")
                Spacer(Modifier.height(6.dp))
                Cuerpo("Seshat del ordenador tiene la apariencia de tu Seshat del móvil (viaja con la cuenta). Aquí puedes elegir claro u oscuro.", tam = 14)
                Spacer(Modifier.height(10.dp))
                MagChoice(listOf("Como el sistema", "Claro", "Oscuro"), Modo.eleccion) { Modo.eleccion = it; Apariencia.guarda() }
            }
            else -> AjustesVersion("marfil", "Seshat")
        }
    }
}
