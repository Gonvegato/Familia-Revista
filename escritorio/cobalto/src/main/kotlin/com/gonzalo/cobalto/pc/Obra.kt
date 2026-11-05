package com.gonzalo.cobalto.pc

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private val ESTADOS_ESCENA = listOf("Borrador", "Revisión", "Final")

/** Una obra: su manuscrito (partes, capítulos y escenas) con el editor, sus fichas y sus notas. */
@Composable
fun PantObra(pid: String, eidInicial: String?, nav: Nav) {
    val e = Cuaderno.escritos
    e.version
    val obra = e.get(pid)
    if (obra == null) { Pagina { Vacio("Esta obra ya no está", "Se ha borrado (aquí o en otro dispositivo): mira la papelera.") }; return }
    LaunchedEffect(pid) { if (e.migraPartes(pid) > 0) nav.aviso("Las partes de esta obra ya son partes de verdad") }
    var pestana by remember(pid) { mutableStateOf(0) }
    var eid by remember(pid) { mutableStateOf(eidInicial ?: e.escenasDe(pid).maxByOrNull { it.u }?.id) }
    var datos by remember { mutableStateOf(false) }
    var exportar by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(start = 36.dp, end = 24.dp, top = 24.dp)) {
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
        PestanasRevista(listOf("Manuscrito", "Fichas", "Notas"), pestana, cuentas = listOf(null, e.fichas(pid).size, e.notas(pid).size)) { pestana = it }
        when (pestana) {
            0 -> Manuscrito(obra, eid, { Editor.guardaYa(); eid = it }, nav)
            1 -> Fichas(obra, nav)
            else -> Notas(obra, nav)
        }
    }
    if (datos) DialogoObra(obra, nav) { datos = false }
    if (exportar) DialogoExportar(obra, nav) { exportar = false }
}

/** El manuscrito: a la izquierda el índice (partes, capítulos y escenas, en el orden del libro); a la derecha la escena que escribes. */
@Composable
private fun Manuscrito(obra: Doc, eid: String?, abre: (String?) -> Unit, nav: Nav) {
    val e = Cuaderno.escritos
    e.version
    val pid = obra.id
    val est = EscribirEstructura.estructura(e.deTipo(Escritos.T_CAP, pid), e.partes(pid))
    val esc = eid?.let { e.get(it) }?.takeIf { it.t == Escritos.T_ESCENA && it.pid == pid }
    var borrar by remember { mutableStateOf<Doc?>(null) }
    var renombrar by remember { mutableStateOf<Doc?>(null) }
    Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
        // ---- El índice
        val scrollIndice = rememberScrollState()
        Box(Modifier.width(300.dp).fillMaxHeight()) {
            Column(Modifier.fillMaxSize().verticalScroll(scrollIndice).padding(end = 14.dp, bottom = 40.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 8.dp)) {
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
                        Text("$n. " + c.s("titulo").ifBlank { "Capítulo sin título" }, Modifier.weight(1f).clickable { renombrar = c }, color = Charcoal, fontFamily = Display,
                            fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (hecho) Text("✓", color = Acento, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(" " + Formato.cifra(e.palabrasCapitulo(c.id)), color = Muted, fontFamily = Body, fontSize = 11.sp)
                    }
                    e.escenas(c.id).forEach { s ->
                        val sel = s.id == esc?.id
                        Row(Modifier.fillMaxWidth().padding(start = (sangria + 12).dp).background(if (sel) Sand else androidx.compose.ui.graphics.Color.Transparent).clickable { abre(s.id) }.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(14.dp).background(if (sel) Acento else Line))
                            Spacer(Modifier.width(8.dp))
                            Text(s.s("titulo").ifBlank { "Escena" }, Modifier.weight(1f), color = if (sel) Charcoal else Muted, fontFamily = Body, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Formato.cifra(s.i("palabras")), color = Muted, fontFamily = Body, fontSize = 10.sp)
                        }
                    }
                    Text("+ escena", Modifier.padding(start = (sangria + 22).dp, top = 2.dp).clickable { abre(e.nuevo(Escritos.T_ESCENA, pid) { put("cid", c.id); put("titulo", "") }.id) }, color = Muted, fontFamily = Body, fontSize = 11.sp)
                }
                var n = 0
                est.items.forEach { it ->
                    when (it) {
                        is EscribirEstructura.Item.Parte -> {
                            Text(it.parte.s("titulo").ifBlank { "Parte" }.uppercase(), Modifier.padding(top = 16.dp).clickable { renombrar = it.parte }, color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
                            Filete(1.dp, Line)
                            it.caps.forEach { c -> n++; Capitulo(c, n, 6) }
                            Text("+ capítulo en esta parte", Modifier.padding(start = 6.dp, top = 6.dp).clickable {
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
        Filete(1.dp, Line, Modifier.fillMaxHeight().width(1.dp))
        // ---- La escena
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 28.dp)) {
            if (esc == null) { Vacio("Elige una escena", "O crea un capítulo: la primera escena se abre sola."); return@Column }
            val cap = e.get(esc.s("cid"))
            var titulo by remember(esc.id) { mutableStateOf(esc.s("titulo")) }
            androidx.compose.foundation.text.BasicTextField(titulo, { titulo = it; e.edita(esc.id) { put("titulo", it) } }, Modifier.fillMaxWidth().padding(top = 6.dp, end = 24.dp), singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Charcoal),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Acento),
                decorationBox = { inner -> Box { if (titulo.isEmpty()) Text("Escena sin título", color = Muted, fontFamily = Display, fontSize = 26.sp, fontStyle = FontStyle.Italic); inner() } })
            Row(Modifier.padding(vertical = 2.dp).padding(end = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${Formato.cifra(esc.i("palabras"))} palabras · ${cap?.s("titulo").orEmpty()}", color = Muted, fontFamily = Body, fontSize = 12.sp)
                Box(Modifier.width(260.dp)) { MagChoice(ESTADOS_ESCENA, esc.i("estado").coerceIn(0, 2)) { i -> e.edita(esc.id) { put("estado", i) } } }
                Spacer(Modifier.weight(1f))
                if (cap != null) {
                    Text("Capítulo terminado", color = Charcoal, fontFamily = Body, fontSize = 12.sp)
                    Interruptor(cap.b("hecho")) { on -> e.edita(cap.id) { if (on) put("hecho", 1) else remove("hecho") } }
                }
                EnlaceRevista("Duplicar") { e.duplicaEscena(esc.id)?.let { abre(it.id) } }
                EnlaceRevista("Borrar", acento = true) { borrar = esc }
            }
            Filete(1.dp, Line)
            Spacer(Modifier.height(14.dp))
            EditorEscena(esc, e.fichas(obra.id), Modifier.weight(1f).fillMaxWidth().padding(end = 24.dp))
        }
    }
    borrar?.let { d -> Dialogo("¿A la papelera?", { borrar = null }, botones = {
        BotonSuave("Cancelar") { borrar = null }
        BotonSuave("A la papelera", peligro = true) { e.borra(d.id); borrar = null; abre(null); nav.aviso("En la papelera (30 días)") }
    }) { Cuerpo("«${d.s("titulo").ifBlank { "Sin título" }}» va a la papelera, aquí y en tus otros dispositivos. Se puede recuperar durante 30 días.", tam = 14) } }
    renombrar?.let { d -> DialogoNombre(d, nav) { renombrar = null } }
}

/** Cambiar el título de una parte o un capítulo (y lo demás que se hace con ellos). */
@Composable
private fun DialogoNombre(d: Doc, nav: Nav, onCerrar: () -> Unit) {
    val e = Cuaderno.escritos
    var t by remember { mutableStateOf(d.s("titulo")) }
    val esParte = d.t == Escritos.T_PARTE
    Dialogo(if (esParte) "La parte" else "El capítulo", onCerrar, botones = {
        EnlaceRevista("A la papelera", acento = true) { e.borra(d.id); onCerrar(); nav.aviso("En la papelera (con lo que tiene dentro)") }
        Spacer(Modifier.weight(1f))
        BotonSuave("Cancelar") { onCerrar() }
        BotonAcento("Guardar", modifier = Modifier.width(120.dp), alto = 44.dp) { e.edita(d.id) { put("titulo", t.trim()) }; onCerrar() }
    }) {
        OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth(), label = { Text("Título") }, singleLine = true)
        Spacer(Modifier.height(10.dp))
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
    var meta by remember { mutableStateOf(obra?.i("meta")?.takeIf { it > 0 }?.toString().orEmpty()) }
    var diaria by remember { mutableStateOf(obra?.i("diaria")?.takeIf { it > 0 }?.toString().orEmpty()) }
    Dialogo(if (obra == null) "Nueva obra" else "La obra", onCerrar, ancho = 640.dp, botones = {
        if (obra != null) EnlaceRevista("A la papelera", acento = true) { e.borra(obra.id); onCerrar(); nav.ir(Pantalla.Portada); nav.aviso("La obra está en la papelera (30 días)") }
        Spacer(Modifier.weight(1f))
        BotonSuave("Cancelar") { onCerrar() }
        BotonAcento(if (obra == null) "Crear" else "Guardar", modifier = Modifier.width(120.dp), alto = 44.dp) {
            val campos: org.json.JSONObject.() -> Unit = {
                put("titulo", titulo.trim()); put("autor", autor.trim()); put("genero", genero.trim()); put("sinopsis", sinopsis)
                put("meta", meta.toIntOrNull() ?: 0); put("diaria", diaria.toIntOrNull() ?: 0)
            }
            if (obra == null) { val o = e.nuevo(Escritos.T_OBRA, "", campos); onCerrar(); nav.ir(Pantalla.Obra(o.id)) } else { e.edita(obra.id, campos); onCerrar() }
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
