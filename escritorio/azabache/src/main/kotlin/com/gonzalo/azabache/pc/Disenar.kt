package com.gonzalo.azabache.pc

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.rememberImagenWeb
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext

/*
 * Diseñar la página de una ficha, como «Editar» en el Azabache del móvil (Maqueta.kt): la página en filas; cada fila, una casilla a todo lo
 * ancho o dos con una línea que se arrastra; en cada casilla, lo que quieras de la ficha (la nota, la opinión, las horas…), un texto tuyo o
 * una imagen (de SteamGridDB, TMDB, TVmaze u Open Library, encuadrada). A la izquierda, la página tal como se lee (una de sus dos columnas);
 * a la derecha, lo que se hace con la casilla elegida. Se guarda sola, en la cuenta, con el mismo formato que el móvil: la ves igual allí.
 */

/** El ancho de una columna de la página al leerla (la ficha se lee a dos columnas): el editor la enseña a ese ancho. */
private val ANCHO_COLUMNA = 540.dp

@OptIn(FlowPreview::class)
@Composable
fun EditorDePagina(k: Kind, f: Ficha, casillas: Map<String, Casilla>, onListo: () -> Unit, onDatos: () -> Unit) {
    val enCurso = !f.valorada
    val clave = Maquetas.clave(k.id, f.id, enCurso)
    val plantilla = Maquetas.plantilla(k.id, enCurso)
    val modulos = remember(k.id, enCurso) { Maquetas.modulos(k, enCurso) }
    var filas by remember(clave) { mutableStateOf(Maquetas.paraVer(k, f.id, enCurso)) }
    var sel by remember(clave) { mutableStateOf<Pair<Int, Int>?>(null) }
    var aviso by remember { mutableStateOf("") }
    val tamanos = remember { mutableStateMapOf<String, IntSize>() }
    // Lo que cambias va a la cuenta solo (al poco de dejar de tocar, y al salir): así un arrastre no son cien escrituras
    val pendiente = remember(clave) { MutableStateFlow<List<FilaMaqueta>?>(null) }
    var guardadas by remember(clave) { mutableStateOf(filas) }
    LaunchedEffect(clave) {
        // (el null de «Restablecer» pasa por el debounce: así lo que quedaba pendiente de antes ya no se guarda)
        pendiente.debounce(350).filterNotNull().collect { n -> if (n != guardadas) { withContext(Dispatchers.IO) { Maquetas.guarda(clave, n) }; guardadas = n } }
    }
    DisposableEffect(clave) {
        onDispose { val n = filas; if (n != guardadas) Thread({ runCatching { Maquetas.guarda(clave, n) } }, "azabache-maqueta").start() }
    }
    fun cambia(n: List<FilaMaqueta>) { filas = n; pendiente.value = n; sel = sel?.takeIf { (i, j) -> i < n.size && j < n[i].casillas.size } }
    fun alSoltar() { pendiente.value = filas }

    var confirmar by remember { mutableStateOf<String?>(null) }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    Column(Modifier.fillMaxSize().background(Paper).focusRequester(foco).focusable()
        .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { if (sel != null) sel = null else onListo(); true } else false }) {
        // Arriba: qué página es, «Datos y nota» y «Listo»
        Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 22.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("DISEÑANDO LA PÁGINA · ${k.title.uppercase()} · ${if (enCurso) k.statuses.getOrElse(f.estado) { "" }.uppercase() else k.doneWord.removeSuffix("s").uppercase()}",
                    color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(f.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 33.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            EnlaceRevista("Datos y nota") { onDatos() }
            Spacer(Modifier.width(22.dp))
            BotonAcento("Listo", alto = 44.dp, modifier = Modifier.width(130.dp)) { onListo() }
        }
        Filete(3.dp, modifier = Modifier.padding(horizontal = 40.dp))
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // La página, tal como se lee (una columna)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val scroll = rememberScrollState()
                Box(Modifier.fillMaxSize().verticalScroll(scroll).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { sel = null },
                    contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.width(ANCHO_COLUMNA).padding(top = 22.dp, bottom = 60.dp)) {
                        Pie("Así se lee una columna de la página. Al leerla, las filas se reparten en dos columnas, en este orden.")
                        Spacer(Modifier.height(14.dp))
                        filas.forEachIndexed { i, r ->
                            FilaEditable(i, r, filas.size, k, enCurso, casillas, sel, tamanos,
                                onElegir = { j -> sel = i to j }, onCambia = { cambia(it(filas)) }, onArrastra = { filas = it(filas) }, onSuelta = { alSoltar() })
                        }
                        Filete(2.dp)
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            EnlaceRevista("+ Añadir fila", acento = true) { val n = Maquetas.anade(filas); cambia(n); sel = n.lastIndex to 0 }
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
            // Lo que se hace con la casilla elegida (o con la página)
            Inspector(k, f, enCurso, filas, sel, modulos, tamanos, aviso,
                onCambia = { cambia(it) }, onSel = { sel = it },
                onPlantilla = { confirmar = "plantilla" }, onRestablecer = { confirmar = "restablecer" }, onDatos = onDatos)
        }
    }

    when (confirmar) {
        "plantilla" -> Dialogo("¿Este diseño en todos los ${k.units}?", { confirmar = null }, botones = {
            BotonSuave("Cancelar") { confirmar = null }
            BotonAcento("Usar en todos", alto = 44.dp, modifier = Modifier.width(180.dp)) {
                val n = filas
                Thread({ runCatching { Maquetas.guardaPlantilla(plantilla, n) } }, "azabache-maqueta").start()
                aviso = "Guardado: lo verás en las demás páginas de ${k.title.lowercase()} que no tengan el suyo (sin tus textos ni imágenes)."
                confirmar = null
            }
        }) {
            Cuerpo("Las demás páginas de ${k.title.lowercase()} ${if (enCurso) "que llevas entre manos" else "terminadas"} que no tengan un diseño suyo se verán así, aquí y en el móvil. " +
                "Tus textos y tus imágenes se quedan solo en esta.", tam = 14)
        }
        "restablecer" -> Dialogo("¿El diseño de siempre?", { confirmar = null }, botones = {
            BotonSuave("Cancelar") { confirmar = null }
            BotonSuave("Restablecer", peligro = true) {
                Thread({ runCatching { Maquetas.restablece(clave, plantilla) } }, "azabache-maqueta").start()
                val n = Maquetas.deSiempre(k, enCurso)
                filas = n; guardadas = n; pendiente.value = null; sel = null
                aviso = "Diseño de siempre."
                confirmar = null
            }
        }) {
            Cuerpo("Se quitan el diseño de esta página y el de todos los ${k.units} (como en el móvil), también en tus otros dispositivos.", tam = 14)
        }
    }
}

/** Una fila en el editor: sus herramientas, sus casillas (se elige una con un clic), la línea que se arrastra y el alto. */
@Composable
private fun FilaEditable(
    i: Int, r: FilaMaqueta, total: Int, k: Kind, enCurso: Boolean, casillas: Map<String, Casilla>, sel: Pair<Int, Int>?, tamanos: MutableMap<String, IntSize>,
    onElegir: (Int) -> Unit, onCambia: ((List<FilaMaqueta>) -> List<FilaMaqueta>) -> Unit,
    onArrastra: ((List<FilaMaqueta>) -> List<FilaMaqueta>) -> Unit, onSuelta: () -> Unit,
) {
    val den = LocalDensity.current
    var anchoPx by remember { mutableStateOf(1) }
    var altoPx by remember { mutableStateOf(1) }
    Column(Modifier.fillMaxWidth()) {
        Filete(2.dp)
        // Las herramientas de la fila, en una línea (como en el móvil)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("FILA ${i + 1}", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
            Herramienta("↑", i > 0) { onCambia { Maquetas.sube(it, i) } }
            Herramienta("↓", i < total - 1) { onCambia { Maquetas.baja(it, i) } }
            if (r.casillas.size == 1) Herramienta("Dividir") { onCambia { Maquetas.divide(it, i) } } else Herramienta("Unir") { onCambia { Maquetas.une(it, i) } }
            if (r.alto > 0f) Herramienta("Alto auto") { onCambia { Maquetas.ponAlto(it, i, 0f) } }
            Herramienta("Quitar", acento = true) { onCambia { Maquetas.quita(it, i) } }
        }
        val fija = r.alto > 0f
        Row(Modifier.fillMaxWidth().then(if (fija) Modifier.height(r.alto.dp) else Modifier.height(IntrinsicSize.Min)).onSizeChanged { anchoPx = it.width.coerceAtLeast(1); altoPx = it.height.coerceAtLeast(1) }) {
            r.casillas.forEachIndexed { n, c ->
                val w = if (r.casillas.size == 2) (if (n == 0) r.split.coerceIn(0.2f, 0.8f) else 1f - r.split.coerceIn(0.2f, 0.8f)) else 1f
                if (n == 1) {
                    // La línea de en medio: se arrastra
                    Box(Modifier.fillMaxHeight().width(22.dp).pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)))
                        .pointerInput(i) {
                            detectHorizontalDragGestures(onDragEnd = onSuelta, onDragCancel = onSuelta) { ch, dx ->
                                ch.consume(); onArrastra { l -> Maquetas.ponSplit(l, i, (l.getOrNull(i)?.split ?: 0.5f) + dx / anchoPx) }
                            }
                        }, contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxHeight().width(1.5.dp).background(Charcoal))
                        Box(Modifier.size(22.dp).clip(CircleShape).background(Acento), contentAlignment = Alignment.Center) {
                            Text("⇔", color = SobreAcento, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                val elegida = sel == (i to n)
                val hover = remember { MutableInteractionSource() }
                val encima by hover.collectIsHoveredAsState()
                val fondo by animateColorAsState(if (elegida) Acento.copy(alpha = 0.05f) else if (encima) Sand.copy(alpha = 0.5f) else Sand.copy(alpha = 0f), tween(140))
                Box(Modifier.weight(w).fillMaxHeight().clipToBounds()) {
                    Column(Modifier.fillMaxSize().background(fondo).padding(top = 12.dp, bottom = 18.dp, start = if (n == 1) 6.dp else 8.dp, end = 8.dp)) {
                        CompositionLocalProvider(LocalCellFraction provides w) { CasillaEnElEditor(c, k, enCurso, casillas, fija) { tamanos["$i:$n"] = it } }
                    }
                    val acento = Acento
                    Box(Modifier.matchParentSize().hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable { onElegir(n) }.drawBehind {
                        val grosor = if (elegida) 2.5.dp.toPx() else 1.5.dp.toPx()
                        drawRoundRect(acento.copy(alpha = if (elegida) 1f else if (encima) 0.8f else 0.45f), cornerRadius = CornerRadius(3.dp.toPx()),
                            style = Stroke(grosor, pathEffect = if (elegida) null else PathEffect.dashPathEffect(floatArrayOf(12f, 9f))))
                    })
                }
            }
        }
        // El alto de la fila: se arrastra el borde de abajo
        Box(Modifier.fillMaxWidth().height(22.dp).pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.N_RESIZE_CURSOR)))
            .pointerInput(i) {
                detectVerticalDragGestures(onDragEnd = onSuelta, onDragCancel = onSuelta) { ch, dy ->
                    ch.consume()
                    onArrastra { l -> val a = l.getOrNull(i)?.alto ?: 0f; Maquetas.ponAlto(l, i, (if (a > 0f) a else altoPx / den.density) + dy / den.density) }
                }
            }, contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            Row(Modifier.clip(esquina(11.dp)).background(Paper).border(1.dp, Line, esquina(11.dp)).padding(horizontal = 10.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⇕", color = Acento, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(if (fija) "${r.alto.toInt()} DP" else "ALTO AUTOMÁTICO", color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

/** Lo que se ve de una casilla en el editor: lo de verdad si lo hay; si no, su nombre y por qué no se ve. */
@Composable
private fun ColumnScope.CasillaEnElEditor(c: CasillaMaqueta, k: Kind, enCurso: Boolean, casillas: Map<String, Casilla>, fija: Boolean, alMedir: (IntSize) -> Unit) {
    when (c.tipo) {
        "texto" -> {
            if (c.titulo.isNotBlank()) MagLabel(c.titulo)
            if (c.texto.isNotBlank()) Text(c.texto, color = Charcoal, fontFamily = Display, fontSize = 18.sp, lineHeight = 27.sp)
            else MagVacio("Texto libre: escríbelo a la derecha.")
        }
        "imagen" -> {
            if (c.titulo.isNotBlank()) MagLabel(c.titulo)
            if (c.imagen.startsWith("http")) ImagenDeCasilla(c, fija, alMedir)
            else Box(Modifier.fillMaxWidth().aspectRatio(1.6f).background(Sand), contentAlignment = Alignment.Center) { MagVacio("Imagen: elígela a la derecha.") }
        }
        "vacio" -> MagVacio("Vacía: haz clic para elegir qué va.")
        else -> {
            val m = Maquetas.modulos(k, enCurso).firstOrNull { it.tipo == c.tipo }
            val cas = casillas[c.tipo]
            when {
                cas != null && m != null -> { if (cas.label != null) MagLabel(cas.label, cas.action?.first, null); cas.content(this) }
                m != null && !m.aqui -> { MagLabel(m.nombre); MagVacio("Se ve en el móvil.") }
                m != null -> { MagLabel(m.nombre); MagVacio("Aún sin nada que enseñar: sale cuando lo haya.") }
                else -> { MagLabel(Maquetas.nombreDe(c.tipo, k, enCurso)); MagVacio("No es de esta página: no se ve (cámbiala o quítala).") }
            }
        }
    }
}

/** La columna de la derecha: la casilla elegida (qué va, su texto, su imagen) o, sin elegir, la página entera. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Inspector(
    k: Kind, f: Ficha, enCurso: Boolean, filas: List<FilaMaqueta>, sel: Pair<Int, Int>?, modulos: List<Maquetas.Modulo>, tamanos: Map<String, IntSize>, aviso: String,
    onCambia: (List<FilaMaqueta>) -> Unit, onSel: (Pair<Int, Int>?) -> Unit, onPlantilla: () -> Unit, onRestablecer: () -> Unit, onDatos: () -> Unit,
) {
    val scroll = rememberScrollState()
    Box(Modifier.width(400.dp).fillMaxHeight().background(Crudo)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 26.dp, vertical = 24.dp)) {
            val p = sel
            val c = p?.let { (i, j) -> filas.getOrNull(i)?.casillas?.getOrNull(j) }
            if (p == null || c == null) {
                Antetitulo("La página")
                Spacer(Modifier.height(10.dp))
                Cuerpo("Haz clic en una casilla para elegir qué va en ella: lo de la ficha, un texto tuyo o una imagen. Arrastra la línea de en medio de una fila para moverla, y el borde de abajo para darle un alto fijo.", tam = 14)
                Spacer(Modifier.height(14.dp))
                MagFact("Filas", "${filas.size}")
                MagFact("Diseño", when {
                    Maquetas.tienePropia(k.id, f.id, enCurso) -> "el de esta página"
                    Maquetas.hayPlantilla(k.id, enCurso) -> "el de ${k.title.lowercase()}"
                    else -> "el de siempre"
                })
                Spacer(Modifier.height(16.dp))
                MagLink("Añadir una fila") { val n = Maquetas.anade(filas); onCambia(n); onSel(n.lastIndex to 0) }
                MagLink("Usar este diseño en todos los ${k.units}", "Sin tus textos ni tus imágenes") { onPlantilla() }
                MagLink("Restablecer el diseño de siempre") { onRestablecer() }
                MagLink("Datos y nota", accent = true) { onDatos() }
                if (aviso.isNotBlank()) Text(aviso, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 14.dp))
                Spacer(Modifier.height(20.dp))
                NotaCasilla("Se guarda solo, en tu cuenta: la página se ve igual en el Azabache del móvil. Esc o «Listo» vuelven a leerla.")
                return@Column
            }
            val (i, j) = p
            val r = filas[i]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Antetitulo("Fila ${i + 1}" + if (r.casillas.size == 2) (if (j == 0) " · izquierda" else " · derecha") else "", Modifier.weight(1f))
                EnlaceRevista("Cerrar") { onSel(null) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Herramienta("↑ Subir", i > 0) { onCambia(Maquetas.sube(filas, i)); onSel(i - 1 to j) }
                Herramienta("↓ Bajar", i < filas.size - 1) { onCambia(Maquetas.baja(filas, i)); onSel(i + 1 to j) }
                if (r.casillas.size == 1) Herramienta("Dividir") { onCambia(Maquetas.divide(filas, i)) }
                else Herramienta("Unir") { onCambia(Maquetas.une(filas, i)); onSel(i to 0) }
                if (r.alto > 0f) Herramienta("Alto auto") { onCambia(Maquetas.ponAlto(filas, i, 0f)) }
                Herramienta("Quitar la fila", acento = true) { onCambia(Maquetas.quita(filas, i)); onSel(null) }
            }
            Spacer(Modifier.height(10.dp)); Filete(1.dp, Line); Spacer(Modifier.height(12.dp))
            Antetitulo("¿Qué va aquí?", tam = 12.sp)
            Spacer(Modifier.height(4.dp))
            fun pon(n: CasillaMaqueta) = onCambia(Maquetas.ponCasilla(filas, i, j, n))
            modulos.forEach { m -> Opcion(m.nombre, c.tipo == m.tipo, if (m.aqui) null else "en el móvil") { if (c.tipo != m.tipo) pon(CasillaMaqueta(m.tipo)) } }
            Opcion("Texto libre", c.tipo == "texto") { if (c.tipo != "texto") pon(CasillaMaqueta("texto")) }
            Opcion("Imagen", c.tipo == "imagen") { if (c.tipo != "imagen") pon(CasillaMaqueta("imagen", forma = "llenar")) }
            Opcion("Vacía", c.tipo == "vacio") { if (c.tipo != "vacio") pon(CasillaMaqueta("vacio")) }
            when (c.tipo) {
                "texto" -> key(i, j) { EditorDeTexto(c) { pon(it) } }
                "imagen" -> key(i, j) { EditorDeImagen(k, f, c, tamanos["$i:$j"], filas[i].alto > 0f) { pon(it) } }
            }
            Spacer(Modifier.height(30.dp))
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
    }
}

/** El texto libre de una casilla: un título (opcional) y el texto. */
@Composable
private fun EditorDeTexto(c: CasillaMaqueta, onCambia: (CasillaMaqueta) -> Unit) {
    var titulo by remember { mutableStateOf(c.titulo) }
    var texto by remember { mutableStateOf(c.texto) }
    Spacer(Modifier.height(14.dp)); Filete(1.dp, Line); Spacer(Modifier.height(12.dp))
    Antetitulo("Tu texto", tam = 12.sp)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(titulo, { titulo = it; onCambia(c.copy(titulo = it.trim(), texto = texto.trim())) }, Modifier.fillMaxWidth(), label = { Text("Título (opcional)") }, singleLine = true)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(texto, { texto = it; onCambia(c.copy(titulo = titulo.trim(), texto = it.trim())) }, Modifier.fillMaxWidth().heightIn(min = 160.dp), label = { Text("Texto") })
}

/** La imagen de una casilla: de dónde (los tipos de la categoría o una dirección), su título y su encuadre. */
@Composable
private fun EditorDeImagen(k: Kind, f: Ficha, c: CasillaMaqueta, tamano: IntSize?, filaFija: Boolean, onCambia: (CasillaMaqueta) -> Unit) {
    val tipos = Buscar.tiposDeImagen(k) + "Dirección"
    var tipo by remember { mutableStateOf(tipos.first()) }
    var lista by remember(tipo) { mutableStateOf<List<Pair<String, String>>?>(null) }
    var error by remember(tipo) { mutableStateOf("") }
    var url by remember { mutableStateOf(c.imagen.takeIf { it.startsWith("http") }.orEmpty()) }
    var titulo by remember { mutableStateOf(c.titulo) }
    var encuadrar by remember { mutableStateOf(false) }
    LaunchedEffect(tipo) {
        if (tipo == "Dirección") return@LaunchedEffect
        lista = withContext(Dispatchers.IO) { runCatching { Buscar.imagenes(k, f, tipo) }.onFailure { error = it.message ?: "No se han podido cargar." }.getOrDefault(emptyList()) }
    }
    Spacer(Modifier.height(14.dp)); Filete(1.dp, Line); Spacer(Modifier.height(12.dp))
    Antetitulo("La imagen", tam = 12.sp)
    if (c.imagen.startsWith("http")) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Herramienta("Encuadrar", acento = true) { encuadrar = true }
            Herramienta(if (c.entera) "Recortarla" else "Entera, sin recortar") { onCambia(c.copy(entera = !c.entera, zoom = 1f, ox = 0f, oy = 0f)) }
            if (c.zoom != 1f || c.ox != 0f || c.oy != 0f) Herramienta("Centrar") { onCambia(c.copy(zoom = 1f, ox = 0f, oy = 0f)) }
        }
    }
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(titulo, { titulo = it; onCambia(c.copy(titulo = it.trim())) }, Modifier.fillMaxWidth(), label = { Text("Título encima (opcional)") }, singleLine = true)
    Spacer(Modifier.height(8.dp))
    FiltrosRevista(tipos, tipos.indexOf(tipo)) { tipo = tipos[it] }
    if (tipo == "Dirección") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(url, { url = it }, Modifier.weight(1f), placeholder = { Text("https://…") }, singleLine = true)
            Spacer(Modifier.width(10.dp))
            BotonSuave("Poner") { if (url.trim().startsWith("http")) onCambia(CasillaMaqueta("imagen", titulo = titulo.trim(), imagen = url.trim(), forma = "llenar")) }
        }
        NotaCasilla("Una imagen de internet: la ven también el móvil y tu pareja. (Las de tus archivos se ponen desde el móvil.)")
    } else {
        val l = lista
        when {
            l == null -> MagVacio("Buscando imágenes…")
            l.isEmpty() -> MagVacio(error.ifBlank { "No hay imágenes de este tipo." })
            else -> LazyVerticalGrid(GridCells.Fixed(if (tipo == "Fondo") 2 else 3), Modifier.fillMaxWidth().heightIn(max = 460.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(l) { (mini, grande) ->
                    val img = rememberImagenWeb(mini)
                    val esta = c.imagen == grande
                    Box(Modifier.fillMaxWidth().aspectRatio(if (tipo == "Fondo") 1.6f else if (tipo == "Logo") 1.6f else 0.67f).clip(esquina(4.dp)).background(Sand)
                        .then(if (esta) Modifier.border(2.5.dp, Acento, esquina(4.dp)) else Modifier).pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onCambia(CasillaMaqueta("imagen", titulo = titulo.trim(), imagen = grande, entera = tipo == "Logo", forma = "llenar")) }) {
                        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = if (tipo == "Logo") ContentScale.Fit else ContentScale.Crop)
                    }
                }
            }
        }
    }
    if (encuadrar && c.imagen.startsWith("http")) {
        // El marco con la forma que tiene la imagen en su casilla (la que tendrá en la página)
        val medida = tamano?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height }
        val forma = if (filaFija || c.proporcion <= 0f) (medida ?: formaDe(c)) else c.proporcion
        DialogoEncuadre(c, forma, { onCambia(it); encuadrar = false }) { encuadrar = false }
    }
}

/**
 * Encuadrar: la imagen dentro de un marco con la forma que tendrá en la página; se arrastra para moverla y la rueda la amplía o la reduce
 * (o los botones). Alrededor, la foto entera apagada. Es el «Encuadrar» del móvil, con ratón.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DialogoEncuadre(c: CasillaMaqueta, formaMarco: Float, onGuardar: (CasillaMaqueta) -> Unit, onCerrar: () -> Unit) {
    var zoom by remember { mutableStateOf(c.zoom) }
    var ox by remember { mutableStateOf(c.ox) }
    var oy by remember { mutableStateOf(c.oy) }
    val forma = formaMarco.coerceIn(0.2f, 5f)
    val img = rememberImagenWeb(c.imagen)
    val ratio = img?.let { it.width.toFloat() / it.height.coerceAtLeast(1) }
    val minZoom = if (c.entera) 0.2f else 1f
    var marco by remember { mutableStateOf(IntSize(1, 1)) }
    fun actual() = c.copy(zoom = zoom, ox = ox, oy = oy, proporcion = forma)
    fun ajusta() { val (z, tx, ty) = colocaImagen(actual(), ratio, marco.width.toFloat(), marco.height.toFloat()); zoom = z; ox = tx / marco.width.coerceAtLeast(1); oy = ty / marco.height.coerceAtLeast(1) }
    Dialogo("Encuadrar", onCerrar, ancho = 760.dp, botones = {
        Herramienta("− Reducir") { zoom = (zoom / 1.15f).coerceIn(minZoom, 6f); ajusta() }
        Herramienta("+ Ampliar") { zoom = (zoom * 1.15f).coerceIn(minZoom, 6f); ajusta() }
        Herramienta("Centrar") { zoom = 1f; ox = 0f; oy = 0f }
        Spacer(Modifier.weight(1f))
        BotonSuave("Cancelar") { onCerrar() }
        BotonAcento("Guardar", alto = 44.dp, modifier = Modifier.width(140.dp)) { onGuardar(actual()) }
    }) {
        Cuerpo("Arrastra la foto dentro del marco y usa la rueda para acercarla o alejarla: lo que queda dentro es lo que se verá.", tam = 13)
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val escW = maxWidth; val escH = 430.dp
            val dispW = escW - 48.dp; val dispH = escH - 48.dp
            val marcoW = if (dispW / dispH > forma) dispH * forma else dispW
            val marcoH = marcoW / forma
            val cubre = !c.entera
            val imgW = if (ratio == null) marcoW else if ((ratio > forma) == cubre) marcoH * ratio else marcoW
            val imgH = if (ratio == null) marcoH else if ((ratio > forma) == cubre) marcoH else marcoW / ratio
            val capa: @Composable (Float) -> Unit = { alfa ->
                if (img != null) Image(img, null, Modifier.requiredSize(imgW, imgH).graphicsLayer {
                    val (z, tx, ty) = colocaImagen(actual(), ratio, marcoW.toPx(), marcoH.toPx())
                    scaleX = z; scaleY = z; translationX = tx; translationY = ty; alpha = alfa
                }, contentScale = ContentScale.FillBounds)
            }
            Box(Modifier.size(escW, escH).clipToBounds().background(Sand).border(2.dp, Charcoal)
                .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.MOVE_CURSOR)))
                .pointerInput(Unit) { detectDragGestures { ch, d -> ch.consume(); ox += d.x / marco.width.coerceAtLeast(1); oy += d.y / marco.height.coerceAtLeast(1); ajusta() } }
                .onPointerEvent(PointerEventType.Scroll) { e ->
                    val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (dy != 0f) { zoom = (zoom * if (dy < 0) 1.08f else 1f / 1.08f).coerceIn(minZoom, 6f); ajusta() }
                }, contentAlignment = Alignment.Center) {
                capa(0.3f)
                Box(Modifier.size(marcoW, marcoH).clipToBounds().border(2.dp, Charcoal).onSizeChanged { marco = it }, contentAlignment = Alignment.Center) { capa(1f) }
                if (img == null) MagVacio("Cargando la imagen…")
            }
        }
    }
}

/** Una herramienta en versalitas («↑», «DIVIDIR», «QUITAR»…). */
@Composable
private fun Herramienta(t: String, habilitada: Boolean = true, acento: Boolean = false, onClick: () -> Unit) =
    Text(t.uppercase(), color = if (!habilitada) Line else if (acento) Acento else Charcoal, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
        modifier = Modifier.then(if (habilitada) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier).clickable(enabled = habilitada, onClick = onClick).padding(vertical = 7.dp))

/** Una opción de «¿Qué va aquí?»: la elegida en el acento, con su punto; [nota], algo pequeño al lado («en el móvil»). */
@Composable
private fun Opcion(t: String, on: Boolean, nota: String? = null, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val encima by hover.collectIsHoveredAsState()
    Row(Modifier.fillMaxWidth().clip(esquina(4.dp)).background(if (encima && !on) Sand.copy(alpha = 0.6f) else Sand.copy(alpha = 0f)).hoverable(hover)
        .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t, color = if (on) Acento else Charcoal, fontFamily = Display, fontSize = 18.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (nota != null) Text(nota.uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(end = 8.dp))
        if (on) Text("●", color = Acento, fontSize = 12.sp)
    }
}
