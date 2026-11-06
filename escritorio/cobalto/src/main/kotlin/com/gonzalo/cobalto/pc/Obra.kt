package com.gonzalo.cobalto.pc

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.cobalto.Doc
import com.gonzalo.cobalto.EscribirEstructura
import com.gonzalo.cobalto.EscribirExport
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.EstadoObra
import com.gonzalo.cobalto.Formato
import com.gonzalo.cobalto.OpcionesExport
import com.gonzalo.cobalto.estadoDe
import com.gonzalo.cobalto.metaObra
import com.gonzalo.cobalto.ponEstado
import com.gonzalo.familia.pc.revista.*

/** Las pestañas de una obra (las mismas que en el móvil y en Atrio, y «Leer»: la obra entera, como un libro). */
val PESTANAS_OBRA = listOf("Manuscrito", "Fichas", "Árbol", "Notas", "Línea", "Cifras", "Guía", "Leer")
const val TAB_FICHAS = 1
const val TAB_ARBOL = 2
const val TAB_GUIA = 6

/**
 * Una obra: su manuscrito (partes, capítulos y escenas) con el editor, sus fichas, el árbol, las notas, la línea temporal, las
 * cifras, la guía y la lectura de corrido. ⌘1…⌘8 (Ctrl en Linux) cambian de pestaña. En la concentración solo queda la página.
 */
@Composable
fun PantObra(pid: String, eidInicial: String?, nav: Nav, pestanaInicial: Int = 0) {
    val e = Cuaderno.escritos
    e.version
    val obra = e.get(pid)
    if (obra == null) { Pagina { Vacio("Esta obra ya no está", "Se ha borrado (aquí o en otro dispositivo): mira la papelera.") }; return }
    LaunchedEffect(pid) { if (e.migraPartes(pid) > 0) nav.aviso("Las partes de esta obra ya son partes de verdad") }
    var pestana by remember(pid) { mutableStateOf(pestanaInicial.coerceIn(0, PESTANAS_OBRA.lastIndex)) }
    var eid by remember(pid) { mutableStateOf(eidInicial ?: e.escenasDe(pid).maxByOrNull { it.u }?.id) }
    LaunchedEffect(eidInicial) { if (eidInicial != null && eidInicial != eid) { eid = eidInicial; pestana = 0 } }
    var datos by remember { mutableStateOf(false) }
    var exportar by remember { mutableStateOf(false) }
    var focoArbol by remember(pid) { mutableStateOf<String?>(null) }
    var fraseGuia by remember { mutableStateOf(false) }
    var metaGuia by remember { mutableStateOf(false) }
    val modoFichas = remember(pid) { ModoPagina() }
    val modoNotas = remember(pid) { ModoPagina() }
    val abre = remember(pid) { AbreDoc() }
    val conc = Estudio.concentracion && pestana == 0
    val abrirEscena: (String) -> Unit = { id -> Editor.guardaYa(); eid = id; pestana = 0 }
    val verArbol: (String) -> Unit = { id -> focoArbol = id; pestana = TAB_ARBOL }
    val guia = AccionesGuia(abre, irManuscrito = { pestana = 0 }, abrirEscena = abrirEscena, exportar = { exportar = true }, frase = { fraseGuia = true }, meta = { metaGuia = true })
    val raiz = remember { FocusRequester() }
    // (al salir de la pestaña del manuscrito, se acaba la concentración)
    LaunchedEffect(pestana) { if (pestana != 0 && Estudio.concentracion) Estudio.concentra(false) }
    // (el foco, a la obra: así los atajos van aunque no hayas pulsado nada; antes que lo de dentro, que el editor se lo queda)
    LaunchedEffect(pid, pestana) { runCatching { raiz.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(start = if (conc) 0.dp else 36.dp, end = if (conc) 0.dp else 24.dp, top = if (conc) 0.dp else 24.dp)
        .focusRequester(raiz).focusable()
        .onPreviewKeyEvent { k ->
            if (k.type != KeyEventType.KeyDown || !Atajo.principal(k) || k.isShiftPressed || k.isAltPressed) return@onPreviewKeyEvent false
            val i = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight).indexOf(k.key)
            if (i >= 0) { Editor.guardaYa(); pestana = i; true } else false
        }) {
        if (!conc) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(estadoDe(obra).nombre.uppercase() + (obra.s("genero").takeIf { it.isNotBlank() }?.let { " · ${it.uppercase()}" } ?: ""), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    FitTitle(obra.s("titulo").ifBlank { "Sin título" }, 52.sp, 54.sp, maxLines = 1, letterSpacing = (-1).sp)
                    Text(metaObra(e, obra), color = Muted, fontFamily = Body, fontSize = 13.sp)
                }
                Spacer(Modifier.width(20.dp))
                EnlaceRevista("Datos de la obra") { datos = true }
                Spacer(Modifier.width(16.dp))
                EnlaceRevista("Exportar", acento = true) { exportar = true }
            }
            Spacer(Modifier.height(6.dp))
            MagChoice(EstadoObra.values().map { it.nombre }, estadoDe(obra).ordinal) { e.ponEstado(pid, EstadoObra.values()[it]) }
            PestanasRevista(PESTANAS_OBRA, pestana, cuentas = listOf(null, e.fichas(pid).size, e.relaciones(pid).size, e.notas(pid).size, e.hitos(pid).size, null, null, null)) { Editor.guardaYa(); pestana = it }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (pestana) {
                0 -> Manuscrito(obra, eid, { Editor.guardaYa(); eid = it }, nav, abre, guia, verGuia = { pestana = TAB_GUIA })
                1 -> Fichas(obra, nav, modoFichas, abrirEscena, verArbol)
                2 -> ArbolObra(obra, nav, abre, focoArbol, abrirEscena)
                3 -> Notas(obra, nav, modoNotas)
                4 -> LineaObra(obra, abre, abrirEscena)
                5 -> CifrasObra(obra, abrirEscena)
                6 -> GuiaObra(e, obra) { haceGuia(it, e, obra, guia) }
                else -> LecturaObra(obra, abrirEscena)
            }
        }
    }
    VentanaDoc(e, abre, nav, abrirEscena, verArbol = { id -> verArbol(id) })
    if (datos) DialogoObra(obra, nav) { datos = false }
    if (exportar) DialogoExportar(obra, nav) { exportar = false }
    DialogosGuia(e, obra, fraseGuia, metaGuia) { fraseGuia = false; metaGuia = false }
}

/** El manuscrito: a la izquierda el índice (partes, capítulos y escenas, en el orden del libro); a la derecha la escena que escribes. */
@Composable
private fun Manuscrito(obra: Doc, eid: String?, abre: (String?) -> Unit, nav: Nav, docs: AbreDoc, guia: AccionesGuia, verGuia: () -> Unit) {
    val e = Cuaderno.escritos
    e.version
    val pid = obra.id
    val conc = Estudio.concentracion
    val est = EscribirEstructura.estructura(e.deTipo(Escritos.T_CAP, pid), e.partes(pid))
    val esc = eid?.let { e.get(it) }?.takeIf { it.t == Escritos.T_ESCENA && it.pid == pid }
    var borrar by remember { mutableStateOf<Doc?>(null) }
    var renombrar by remember { mutableStateOf<Doc?>(null) }
    Row(Modifier.fillMaxSize().padding(top = if (conc) 0.dp else 8.dp)) {
        // ---- El índice
        if (!conc) {
            IndiceManuscrito(e, obra, est, esc, abre, guia, verGuia) { renombrar = it }
            Filete(1.dp, Line, Modifier.fillMaxHeight().width(1.dp))
        }
        // ---- La escena
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = if (conc) 0.dp else 28.dp)) {
            if (esc == null) { Vacio("Elige una escena", "O crea un capítulo: la primera escena se abre sola."); return@Column }
            val cap = e.get(esc.s("cid"))
            if (!conc) {
                var titulo by remember(esc.id) { mutableStateOf(esc.s("titulo")) }
                LaunchedEffect(titulo) { if (titulo != esc.s("titulo")) { kotlinx.coroutines.delay(400); e.edita(esc.id) { put("titulo", titulo) } } }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, end = 4.dp)) {
                    androidx.compose.foundation.text.BasicTextField(titulo, { titulo = it.replace("\n", "") }, Modifier.weight(1f), singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Charcoal),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Acento),
                        decorationBox = { inner -> Box { if (titulo.isEmpty()) Text("Escena sin título", color = Muted, fontFamily = Display, fontSize = 28.sp, fontStyle = FontStyle.Italic); inner() } })
                    Text((cap?.let { c -> "Capítulo ${e.capitulos(pid).indexOfFirst { it.id == c.id } + 1} · " + c.s("titulo") } ?: "").uppercase(), color = Acento, fontFamily = Body, fontSize = 10.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 280.dp))
                }
                Row(Modifier.padding(bottom = 2.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.width(260.dp)) { MagChoice(ESTADOS_ESCENA, esc.i("estado").coerceIn(0, 2)) { i -> e.edita(esc.id) { put("estado", i) } } }
                    Spacer(Modifier.weight(1f))
                    if (cap != null) {
                        Text("Capítulo terminado", color = Charcoal, fontFamily = Body, fontSize = 12.sp)
                        Interruptor(cap.b("hecho")) { on -> e.edita(cap.id) { if (on) put("hecho", 1) else remove("hecho") } }
                    }
                    EnlaceRevista("Duplicar") { e.duplicaEscena(esc.id)?.let { abre(it.id) } }
                    EnlaceRevista("Borrar", acento = true) { borrar = esc }
                }
            }
            EditorEscena(esc, obra, nav, docs, abrirEscena = { abre(it) }, modifier = Modifier.weight(1f).fillMaxWidth().padding(end = if (conc) 0.dp else 4.dp))
        }
    }
    borrar?.let { d -> Dialogo("¿A la papelera?", { borrar = null }, botones = {
        BotonSuave("Cancelar") { borrar = null }
        BotonSuave("A la papelera", peligro = true) { e.borra(d.id); borrar = null; abre(null); nav.aviso("En la papelera (30 días)") }
    }) { Cuerpo("«${d.s("titulo").ifBlank { "Sin título" }}» va a la papelera, aquí y en tus otros dispositivos. Se puede recuperar durante 30 días.", tam = 14) } }
    renombrar?.let { d -> DialogoNombre(d, nav) { renombrar = null } }
}

/** El índice del manuscrito: el paso de la guía, las partes, los capítulos y sus escenas (con el color de su estado). */
@Composable
private fun IndiceManuscrito(e: Escritos, obra: Doc, est: EscribirEstructura.Estructura, esc: Doc?, abre: (String?) -> Unit, guia: AccionesGuia, verGuia: () -> Unit, renombrar: (Doc) -> Unit) {
    val pid = obra.id
    val scrollIndice = rememberScrollState()
    Box(Modifier.width(300.dp).fillMaxHeight()) {
        Column(Modifier.fillMaxSize().verticalScroll(scrollIndice).padding(end = 14.dp, bottom = 40.dp)) {
            Spacer(Modifier.height(6.dp))
            TarjetaGuia(e, obra, hacer = { haceGuia(it, e, obra, guia) }, verGuia = verGuia)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                EnlaceRevista("+ Capítulo", acento = true) {
                    val c = e.nuevo(Escritos.T_CAP, pid) { put("titulo", "Capítulo ${e.capitulos(pid).size + 1}") }
                    abre(e.nuevo(Escritos.T_ESCENA, pid) { put("cid", c.id); put("titulo", "") }.id)
                }
                EnlaceRevista("+ Parte") { e.nuevo(Escritos.T_PARTE, pid) { put("titulo", "Parte ${e.partes(pid).size + 1}") } }
            }
            @Composable
            fun Capitulo(c: Doc, n: Int, sangria: Int) {
                val hecho = Publica.hecho(e, c)
                Row(Modifier.fillMaxWidth().padding(start = sangria.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (c.s("color").isNotBlank()) { Punto(colorEscrito(c.s("color")), 8.dp); Spacer(Modifier.width(6.dp)) }
                    Text("$n. " + c.s("titulo").ifBlank { "Capítulo sin título" }, Modifier.weight(1f).pointerHoverIcon(PointerIcon.Hand).clickable { renombrar(c) }, color = Charcoal, fontFamily = Display,
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (hecho) Text("✓", color = Acento, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(" " + Formato.cifra(e.palabrasCapitulo(c.id)), color = Muted, fontFamily = Body, fontSize = 11.sp)
                }
                e.escenas(c.id).forEach { s ->
                    val sel = s.id == esc?.id
                    Row(Modifier.fillMaxWidth().padding(start = (sangria + 12).dp).background(if (sel) Sand else androidx.compose.ui.graphics.Color.Transparent).pointerHoverIcon(PointerIcon.Hand).clickable { abre(s.id) }.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(14.dp).background(if (sel) Acento else colorEstado(s.i("estado")).copy(alpha = 0.7f)))
                        Spacer(Modifier.width(8.dp))
                        Text(s.s("titulo").ifBlank { "Escena" }, Modifier.weight(1f), color = if (sel) Charcoal else Muted, fontFamily = Body, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Formato.cifra(s.i("palabras")), color = Muted, fontFamily = Body, fontSize = 10.sp)
                    }
                }
                Text("+ escena", Modifier.padding(start = (sangria + 22).dp, top = 2.dp).pointerHoverIcon(PointerIcon.Hand).clickable { abre(e.nuevo(Escritos.T_ESCENA, pid) { put("cid", c.id); put("titulo", "") }.id) }, color = Muted, fontFamily = Body, fontSize = 11.sp)
            }
            var n = 0
            est.items.forEach { it ->
                when (it) {
                    is EscribirEstructura.Item.Parte -> {
                        Row(Modifier.padding(top = 16.dp).pointerHoverIcon(PointerIcon.Hand).clickable { renombrar(it.parte) }, verticalAlignment = Alignment.CenterVertically) {
                            if (it.parte.s("color").isNotBlank()) { Punto(colorEscrito(it.parte.s("color")), 8.dp); Spacer(Modifier.width(6.dp)) }
                            Text(it.parte.s("titulo").ifBlank { "Parte" }.uppercase(), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
                        }
                        Filete(1.dp, Line)
                        it.caps.forEach { c -> n++; Capitulo(c, n, 6) }
                        Text("+ capítulo en esta parte", Modifier.padding(start = 6.dp, top = 6.dp).pointerHoverIcon(PointerIcon.Hand).clickable {
                            val c = e.nuevo(Escritos.T_CAP, pid) { put("titulo", "Capítulo ${e.capitulos(pid).size + 1}"); put("aid", it.parte.id) }
                            abre(e.nuevo(Escritos.T_ESCENA, pid) { put("cid", c.id); put("titulo", "") }.id)
                        }, color = Muted, fontFamily = Body, fontSize = 11.sp)
                    }
                    is EscribirEstructura.Item.Cap -> { n++; Capitulo(it.cap, n, 0) }
                }
            }
            if (est.items.isEmpty()) Text("Aún no hay capítulos. Empieza con «+ Capítulo».", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
        }
        VerticalScrollbar(rememberScrollbarAdapter(scrollIndice), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** Cambiar el título de una parte o un capítulo (y lo demás que se hace con ellos). */
@Composable
private fun DialogoNombre(d: Doc, nav: Nav, onCerrar: () -> Unit) {
    val e = Cuaderno.escritos
    var t by remember { mutableStateOf(d.s("titulo")) }
    var resumen by remember { mutableStateOf(d.s("resumen")) }
    var color by remember { mutableStateOf(d.s("color")) }
    val esParte = d.t == Escritos.T_PARTE
    Dialogo(if (esParte) "La parte" else "El capítulo", onCerrar, ancho = 620.dp, botones = {
        EnlaceRevista("A la papelera", acento = true) { e.borra(d.id); onCerrar(); nav.aviso("En la papelera (con lo que tiene dentro)") }
        Spacer(Modifier.weight(1f))
        BotonSuave("Cancelar") { onCerrar() }
        BotonAcento("Guardar", modifier = Modifier.width(120.dp), alto = 44.dp) {
            e.edita(d.id) { put("titulo", t.trim()); put("resumen", resumen.trim()); if (color.isNotBlank()) put("color", color) else remove("color") }; onCerrar()
        }
    }) {
        CampoRevista(t, { t = it }, "Título", estilo = androidx.compose.ui.text.TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp))
        CampoRevista(resumen, { resumen = it }, if (esParte) "Qué pasa en esta parte" else "Resumen", lineas = 3, pista = "Cuenta para la guía (el esquema)")
        EtiquetaCampo(if (esParte) "Color" else "Color de su cuadrado")
        Muestras(color, conNinguno = true) { color = it }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (esParte) "Terminada (sus capítulos llegan a Marfil)" else "Terminado (llega a Marfil, para leerlo)", Modifier.weight(1f), color = Charcoal, fontFamily = Body, fontSize = 14.sp)
            Interruptor(d.b("hecho")) { on -> e.edita(d.id) { if (on) put("hecho", 1) else remove("hecho") } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Compartido con tu pareja", Modifier.weight(1f), color = Charcoal, fontFamily = Body, fontSize = 14.sp)
            Interruptor(d.b("comp")) { on -> e.edita(d.id) { if (on) put("comp", 1) else remove("comp") } }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EnlaceRevista("↑ Subir") { mueve(d, -1) }
            EnlaceRevista("↓ Bajar") { mueve(d, +1) }
        }
    }
}

/** Sube o baja una parte o un capítulo entre los suyos. */
private fun mueve(d: Doc, paso: Int) {
    val e = Cuaderno.escritos
    val hermanos = if (d.t == Escritos.T_CAP && e.get(d.s("aid")) != null) e.deTipo(Escritos.T_CAP, d.pid).filter { it.s("aid") == d.s("aid") }
        else EscribirEstructura.estructura(e.deTipo(Escritos.T_CAP, d.pid), e.partes(d.pid)).items.map { when (it) { is EscribirEstructura.Item.Parte -> it.parte; is EscribirEstructura.Item.Cap -> it.cap } }
    val i = hermanos.indexOfFirst { it.id == d.id }
    val j = i + paso
    if (i < 0 || j !in hermanos.indices) return
    if (paso < 0) e.mueve(d.id, hermanos.getOrNull(j - 1)?.id, hermanos[j].id) else e.mueve(d.id, hermanos[j].id, hermanos.getOrNull(j + 1)?.id)
}

/** Los datos de una obra (o una nueva). */
@Composable
fun DialogoObra(obra: Doc?, nav: Nav, onCerrar: () -> Unit) {
    val e = Cuaderno.escritos
    var titulo by remember { mutableStateOf(obra?.s("titulo").orEmpty()) }
    var autor by remember { mutableStateOf(obra?.s("autor").orEmpty()) }
    var genero by remember { mutableStateOf(obra?.s("genero").orEmpty()) }
    var sinopsis by remember { mutableStateOf(obra?.s("sinopsis").orEmpty()) }
    var meta by remember { mutableStateOf(obra?.i("meta")?.takeIf { it > 0 }?.toString() ?: if (obra == null) "80000" else "") }
    var diaria by remember { mutableStateOf(obra?.i("diaria")?.takeIf { it > 0 }?.toString() ?: if (obra == null) "500" else "") }
    var color by remember { mutableStateOf(obra?.s("color")?.ifBlank { null } ?: COLORES_ESCRITO[0]) }
    var guiada by remember { mutableStateOf(obra == null || GuiaNovela.activa(obra)) }
    Dialogo(if (obra == null) "Nueva obra" else "La obra", onCerrar, ancho = 660.dp, botones = {
        if (obra != null) EnlaceRevista("A la papelera", acento = true) { e.borra(obra.id); onCerrar(); nav.ir(Pantalla.Portada); nav.aviso("La obra está en la papelera (30 días)") }
        Spacer(Modifier.weight(1f))
        BotonSuave("Cancelar") { onCerrar() }
        BotonAcento(if (obra == null) "Crear" else "Guardar", modifier = Modifier.width(120.dp), alto = 44.dp) {
            if (titulo.isBlank()) return@BotonAcento
            val campos: org.json.JSONObject.() -> Unit = {
                put("titulo", titulo.trim()); put("autor", autor.trim()); put("genero", genero.trim()); put("sinopsis", sinopsis); put("color", color)
                put("meta", meta.toIntOrNull() ?: 0); put("diaria", diaria.toIntOrNull() ?: 0); put("gOn", if (guiada) 1 else 0)
            }
            if (obra == null) {
                val o = e.nuevo(Escritos.T_OBRA, "", campos)
                // Un capítulo y una escena para empezar a escribir ya (como en el móvil)
                val c = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Capítulo 1") }
                val s = e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("titulo", "Escena 1") }
                onCerrar(); nav.ir(Pantalla.Obra(o.id, s.id))
            } else { e.edita(obra.id, campos); onCerrar() }
        }
    }) {
        OutlinedTextField(titulo, { titulo = it }, Modifier.fillMaxWidth(), label = { Text("Título") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(autor, { autor = it }, Modifier.weight(1f), label = { Text("Autor") }, singleLine = true)
            OutlinedTextField(genero, { genero = it }, Modifier.weight(1f), label = { Text("Género") }, singleLine = true)
        }
        OutlinedTextField(sinopsis, { sinopsis = it }, Modifier.fillMaxWidth().heightIn(min = 110.dp), label = { Text("Sinopsis") })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(meta, { meta = it.filter { c -> c.isDigit() }.take(7) }, Modifier.weight(1f), label = { Text("Palabras de la obra (meta)") }, singleLine = true)
            OutlinedTextField(diaria, { diaria = it.filter { c -> c.isDigit() }.take(5) }, Modifier.weight(1f), label = { Text("Palabras al día") }, singleLine = true)
        }
        Text("Las metas son tuyas: sirven para ver cuánto llevas, no para presionarte.", color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        EtiquetaCampo("Color")
        Muestras(color) { color = it }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Guiarme paso a paso", color = Charcoal, fontFamily = Body, fontSize = 14.sp)
                Text("Una guía de 11 pasos, de la idea al manuscrito final. Puedes quitarla cuando quieras.", color = Muted, fontFamily = Body, fontSize = 12.sp)
            }
            Interruptor(guiada) { guiada = it }
        }
    }
}

/** Exportar la obra: Word (como libro o como manuscrito para editoriales), EPUB, Markdown o texto. Todo aquí, sin mandar el texto a ningún sitio. */
@Composable
fun DialogoExportar(obra: Doc, nav: Nav, onCerrar: () -> Unit) {
    val e = Cuaderno.escritos
    var formato by remember { mutableStateOf(0) }
    var tituloEscenas by remember { mutableStateOf(false) }
    val nombre = obra.s("titulo").ifBlank { "obra" }.replace(Regex("[\\\\/:*?\"<>|]"), "")
    Dialogo("Exportar «${obra.s("titulo")}»", onCerrar, botones = {
        BotonSuave("Cancelar") { onCerrar() }
        BotonAcento("Exportar…", modifier = Modifier.width(140.dp), alto = 44.dp) {
            val (ext, datos) = when (formato) {
                0 -> "docx" to { EscribirExport.docx(e, obra.id, OpcionesExport(titulosDeEscena = tituloEscenas)) }
                1 -> "docx" to { EscribirExport.docx(e, obra.id, OpcionesExport(titulosDeEscena = tituloEscenas, estilo = "manuscrito")) }
                2 -> "epub" to { EscribirExport.epub(e, obra.id, OpcionesExport(titulosDeEscena = tituloEscenas)) }
                3 -> "md" to { EscribirExport.markdown(e, obra.id, OpcionesExport(titulosDeEscena = tituloEscenas)).toByteArray() }
                else -> "txt" to { EscribirExport.texto(e, obra.id, OpcionesExport(titulosDeEscena = tituloEscenas)).toByteArray() }
            }
            val f = eligeDondeGuardar("Guardar la obra", "$nombre${if (formato == 1) " (manuscrito)" else ""}.$ext")
            if (f != null) { runCatching { f.writeBytes(datos()) }.onSuccess { nav.aviso("Exportada: ${f.name}") }.onFailure { nav.aviso(it.message ?: "No se ha podido") }; onCerrar() }
        }
    }) {
        MagChoice(listOf("Word (libro)", "Word (manuscrito)", "EPUB", "Markdown", "Texto"), formato, "Formato") { formato = it }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            Text("Con los títulos de las escenas", Modifier.weight(1f), color = Charcoal, fontFamily = Body, fontSize = 14.sp)
            Interruptor(tituloEscenas) { tituloEscenas = it }
        }
        Cuerpo("El manuscrito es el formato de enviar a editoriales (Times 12, interlineado 1,5, portada con el recuento). Si una editorial pide otra cosa, sus normas mandan.", tam = 12)
    }
}
