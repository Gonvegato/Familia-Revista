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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.cobalto.Doc
import com.gonzalo.cobalto.Escritos
import com.gonzalo.familia.pc.AjustesCuenta
import com.gonzalo.familia.pc.AjustesVersion
import com.gonzalo.familia.pc.Sitio
import com.gonzalo.familia.pc.revista.*

/**
 * Una lista a la izquierda (con «+ Nueva…», los filtros y la búsqueda) y, a la derecha, lo elegido en una página que se lee y, con
 * un clic o «Editar», se edita (las fichas y las notas).
 */
@Composable
private fun ListaYPagina(
    docs: List<Doc>, modo: ModoPagina, nuevoTexto: String, nuevo: () -> Unit, filtros: @Composable () -> Unit,
    fila: @Composable (Doc, Boolean) -> Unit, pagina: @Composable (Doc) -> Unit, vacio: @Composable () -> Unit,
) {
    // Lo elegido ya no está (borrado aquí o en otro sitio): el primero
    LaunchedEffect(docs.map { it.id }) { if (modo.sel != null && docs.none { it.id == modo.sel }) modo.desaparece(docs.firstOrNull()?.id) else if (modo.sel == null && docs.isNotEmpty()) modo.elige(docs.first().id) }
    val foco = remember { FocusRequester() }
    // (al pasar a editar, el foco a la página: así Esc vuelve a leer aunque aún no hayas pulsado en un campo; va antes que lo de
    // dentro, para que una ficha nueva se quede con el foco en su nombre)
    LaunchedEffect(modo.editando, modo.sel) { if (modo.editando) runCatching { foco.requestFocus() } }
    Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
        val scrollLista = rememberScrollState()
        Box(Modifier.width(310.dp).fillMaxHeight()) {
            Column(Modifier.fillMaxSize().verticalScroll(scrollLista).padding(end = 16.dp, bottom = 40.dp)) {
                EnlaceRevista(nuevoTexto, acento = true, modifier = Modifier.padding(vertical = 6.dp)) { nuevo() }
                filtros()
                Spacer(Modifier.height(6.dp))
                docs.forEach { d -> fila(d, d.id == modo.sel) }
                if (docs.isEmpty()) Text("Nada todavía.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
            }
            VerticalScrollbar(rememberScrollbarAdapter(scrollLista), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
        Filete(1.dp, Line, Modifier.fillMaxHeight().width(1.dp))
        val d = docs.firstOrNull { it.id == modo.sel }
        Box(Modifier.weight(1f).fillMaxHeight().focusRequester(foco).focusable()
            .onPreviewKeyEvent { k -> k.type == KeyEventType.KeyDown && k.key == Key.Escape && modo.esc() }) {
            key(modo.sel, modo.editando) {
                val scroll = rememberScrollState()
                Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 1000.dp).fillMaxWidth().padding(start = 40.dp, end = 36.dp, top = 14.dp, bottom = 50.dp)) {
                        if (d == null) vacio() else pagina(d)
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

/** Una línea de la lista de la izquierda: su punto de color, el título y debajo lo demás; la elegida, con la raya de acento. */
@Composable
private fun FilaLista(titulo: String, sub: String, color: androidx.compose.ui.graphics.Color, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (on) Sand else Color.Transparent).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(30.dp).background(if (on) Acento else Color.Transparent))
        Spacer(Modifier.width(8.dp))
        Punto(color, 10.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Charcoal, fontFamily = Display, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Muted, fontFamily = Body, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Las fichas de la obra (personajes, lugares, objetos…), las mismas que en el móvil: para leerlas y, con un clic, editarlas. */
@Composable
fun Fichas(obra: Doc, nav: Nav, modo: ModoPagina, abrirEscena: (String) -> Unit, verArbol: (String) -> Unit) {
    val e = Cuaderno.escritos
    val ver = e.version
    var tipo by remember(obra.id) { mutableStateOf("") }
    var q by remember(obra.id) { mutableStateOf("") }
    val todas = e.fichas(obra.id)
    val sale = remember(ver, obra.id) { val m = HashMap<String, Int>(); e.escenasDe(obra.id).forEach { s -> e.fichasEn(obra.id, s.s("texto")).forEach { m[it.id] = (m[it.id] ?: 0) + 1 } }; m }
    val fichas = todas.filter { (tipo.isEmpty() || it.s("tipo") == tipo) && (q.isBlank() || (it.s("nombre") + " " + it.s("alias")).contains(q.trim(), true)) }.sortedBy { it.s("nombre").lowercase() }
    val abre = remember { AbreDoc() }
    ListaYPagina(fichas, modo, "+ Nueva ficha", nuevo = { modo.nuevo(Nuevos.ficha(e, obra.id, tipo.ifEmpty { "personaje" }).id) }, filtros = {
        FilaOpciones {
            Opcion("Todas ${todas.size}", tipo.isEmpty()) { tipo = "" }
            TIPOS_FICHA.forEach { (k, n) -> val c = todas.count { it.s("tipo") == k }; if (c > 0 || tipo == k) Opcion("$n $c", tipo == k) { tipo = k } }
        }
        if (todas.size > 6) CampoRevista(q, { q = it }, "", pista = "Buscar una ficha…")
    }, fila = { f, on ->
        FilaLista(f.s("nombre").ifBlank { "Sin nombre" }, singularFicha(f.s("tipo")) + (sale[f.id]?.let { " · sale en $it ${if (it == 1) "escena" else "escenas"}" } ?: " · aún no sale"),
            colorEscrito(f.s("color")), on) { modo.elige(f.id) }
    }, pagina = { f ->
        if (modo.editando) EdicionFicha(e, f, onListo = { modo.listo() }, onBorrar = { e.borra(f.id); modo.desaparece(null); nav.aviso("En la papelera, con sus uniones (30 días)") }, focoNombre = f.s("nombre").isBlank())
        else LecturaFicha(e, f, AccionesLectura(editar = { modo.edita() }, verDoc = { x -> if (e.get(x)?.t == Escritos.T_FICHA) { tipo = ""; q = ""; modo.elige(x) } else abre.ver(x) }, abrirEscena = abrirEscena, verArbol = verArbol))
    }, vacio = {
        Column(Modifier.padding(top = 30.dp)) {
            Text("El mundo de tu historia", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp)
            Spacer(Modifier.height(10.dp))
            Text("Personajes, lugares, objetos y reglas del mundo en fichas. Cuando escribas su nombre (o un alias) en una escena, quedará subrayado: pasa el ratón por encima para ver su ficha, o haz doble clic para abrirla.",
                color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 27.sp)
        }
    })
    VentanaDoc(e, abre, nav, abrirEscena)
}

/** Las notas de la obra (ideas, investigación, esquemas, plantillas…): para leerlas y, con un clic, editarlas. */
@Composable
fun Notas(obra: Doc, nav: Nav, modo: ModoPagina) {
    val e = Cuaderno.escritos
    e.version
    var tipo by remember(obra.id) { mutableStateOf("") }
    val todas = e.notas(obra.id)
    val notas = todas.filter { tipo.isEmpty() || it.s("tipo") == tipo }.sortedWith(compareByDescending<Doc> { it.b("fija") }.thenByDescending { it.u })
    ListaYPagina(notas, modo, "+ Nueva nota", nuevo = { modo.nuevo(Nuevos.nota(e, obra.id, tipo.ifEmpty { "nota" }).id) }, filtros = {
        FilaOpciones {
            Opcion("Todas ${todas.size}", tipo.isEmpty()) { tipo = "" }
            TIPOS_NOTA.forEach { (k, n) -> val c = todas.count { it.s("tipo") == k }; if (c > 0 || tipo == k) Opcion("$n $c", tipo == k) { tipo = k } }
        }
    }, fila = { n, on ->
        FilaLista(n.s("titulo").ifBlank { singularNota(n.s("tipo")) }, (if (n.b("fija")) "Fijada · " else "") + singularNota(n.s("tipo")) + " · " + hace(n.u), colorEscrito(n.s("color")), on) { modo.elige(n.id) }
    }, pagina = { n ->
        if (modo.editando) EdicionNota(e, n, onListo = { modo.listo() }, onBorrar = { e.borra(n.id); modo.desaparece(null); nav.aviso("En la papelera (30 días)") }, focoTitulo = n.s("titulo").isBlank())
        else LecturaNota(e, n, AccionesLectura(editar = { modo.edita() }))
    }, vacio = {
        Column(Modifier.padding(top = 30.dp)) {
            Text("El tablón", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp)
            Spacer(Modifier.height(10.dp))
            Text("Ideas sueltas, investigación, cosas por arreglar y plantillas (como una ficha de escena) que puedes tener junto al texto mientras escribes, en el panel del editor.",
                color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 27.sp)
        }
    })
}

/** La papelera: lo borrado aquí o en tus otros dispositivos, durante 30 días. */
@Composable
fun PantPapelera(nav: Nav) {
    val e = Cuaderno.escritos
    e.version
    val l = e.papelera()
    var vaciar by remember { mutableStateOf(false) }
    Pagina(ancho = 900.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { Cabecera(84.sp, texto = "PAPELERA") }
            if (l.isNotEmpty()) BotonSuave("Vaciar", peligro = true) { vaciar = true }
        }
        FileteCabecera()
        Spacer(Modifier.height(8.dp))
        Text("Lo borrado se queda 30 días (aquí y en tus otros dispositivos); luego se va solo.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
        if (l.isEmpty()) Vacio("La papelera está vacía")
        l.forEach { d ->
            val tipo = when (d.t) { Escritos.T_OBRA -> "Obra"; Escritos.T_CAP -> "Capítulo"; Escritos.T_ESCENA -> "Escena"; Escritos.T_PARTE -> "Parte"; Escritos.T_FICHA -> "Ficha"; Escritos.T_NOTA -> "Nota"; Escritos.T_REL -> "Unión"; Escritos.T_HITO -> "Suceso"; Escritos.T_RAMA -> "Rama"; Escritos.T_BORRADOR -> "Borrador"; else -> "Documento" }
            val nombre = d.s("titulo").ifBlank { d.s("nombre") }.ifBlank { "Sin título" }
            val con = e.conElBorrado(d.id).size
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(nombre, color = Charcoal, fontFamily = Display, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("$tipo · ${e.cualquiera(d.pid)?.s("titulo").orEmpty()}" + (if (con > 0) " · con $con más dentro" else "") + " · " + hace(d.n("dt", d.u)), color = Muted, fontFamily = Body, fontSize = 12.sp)
                }
                EnlaceRevista("Recuperar", acento = true) { e.restaura(d.id); nav.aviso("Recuperado: $nombre") }
                Spacer(Modifier.width(16.dp))
                EnlaceRevista("Borrar del todo") { e.purga(d.id) }
            }
            Filete(1.dp, Line)
        }
    }
    if (vaciar) Dialogo("¿Vaciar la papelera?", { vaciar = false }, botones = {
        BotonSuave("Cancelar") { vaciar = false }
        BotonSuave("Vaciar", peligro = true) { e.vaciaPapelera(); vaciar = false }
    }) { Cuerpo("Lo que hay en ella se borra para siempre, aquí y en tus otros dispositivos.", tam = 14) }
}

@Composable
fun PantAjustes(nav: Nav) {
    var pestana by remember { mutableStateOf(0) }
    Pagina(ancho = 900.dp) {
        Cabecera(84.sp, texto = "AJUSTES")
        FileteCabecera()
        Spacer(Modifier.height(14.dp))
        PestanasRevista(listOf("Cuenta", "Copias", "Apariencia", "Escribir", "Versión"), pestana) { pestana = it }
        Spacer(Modifier.height(22.dp))
        when (pestana) {
            0 -> {
                AjustesCuenta("cobalto")
                val e = Cuaderno.escritos
                Spacer(Modifier.height(18.dp)); Antetitulo("Escritos")
                MagFact("Por subir", e.pendientes().size.toString())
                MagFact("Última vez", if (e.ultima > 0) hace(e.ultima) else "—")
                if (e.error.isNotBlank()) MagFact("Último problema", e.error)
            }
            1 -> {
                Antetitulo("Copias de seguridad")
                Spacer(Modifier.height(6.dp))
                Cuerpo("Además de la cuenta, este ordenador guarda copias completas de todo lo que escribes (cada 15 minutos si algo cambia y al cerrar; las últimas ${Copias.GUARDA}).", tam = 14)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BotonSuave("Hacer una copia ahora") { Copias.haz(Cuaderno.escritos, "a mano")?.let { nav.aviso("Copia hecha: ${it.name}") } }
                    BotonSuave("Abrir la carpeta") { Sitio.abre(Copias.carpeta().absolutePath) }
                }
                Spacer(Modifier.height(10.dp))
                Copias.lista().take(12).forEach { f -> MagFact(f.name.removePrefix("copia-").removeSuffix(".json"), "${f.length() / 1024} KB") }
            }
            2 -> {
                Antetitulo("Apariencia")
                Spacer(Modifier.height(6.dp))
                Cuerpo("Cobalto del ordenador tiene la apariencia de tu Cobalto del móvil (la que pones allí viaja con la cuenta). Aquí puedes elegir claro u oscuro.", tam = 14)
                Spacer(Modifier.height(10.dp))
                MagChoice(listOf("Como el sistema", "Claro", "Oscuro"), Modo.eleccion) { Modo.eleccion = it; Apariencia.guarda() }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Con los colores de Azabache", Modifier.weight(1f), color = Charcoal, fontFamily = Body, fontSize = 14.sp)
                    Interruptor(Skin.origenAtrio) { Skin.origenAtrio = it; Apariencia.guarda() }
                }
            }
            3 -> {
                PrefsEditor.asegura()
                Antetitulo("El editor en este ordenador")
                Spacer(Modifier.height(6.dp))
                Cuerpo("Cómo se ve y cómo se comporta el editor aquí (lo eliges también en su barra). Lo que escribes es lo mismo en todos tus dispositivos; esto no viaja.", tam = 14)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Tamaño de la letra", Modifier.weight(1f), color = Charcoal, fontFamily = Body, fontSize = 14.sp)
                    BotonSuave("−") { PrefsEditor.letra(-1) }
                    Text("${PrefsEditor.tam.toInt()}", color = Charcoal, fontFamily = Display, fontSize = 22.sp)
                    BotonSuave("+") { PrefsEditor.letra(+1) }
                }
                MagChoice(listOf("Con serifa", "De palo seco", "De máquina"), PrefsEditor.fuente, "Tipo de letra") { PrefsEditor.fuente = it; PrefsEditor.guarda() }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Máquina de escribir", color = Charcoal, fontFamily = Body, fontSize = 14.sp)
                        Text("La línea donde escribes se queda a media altura de la ventana.", color = Muted, fontFamily = Body, fontSize = 12.sp)
                    }
                    Interruptor(PrefsEditor.maquina) { PrefsEditor.maquina = it; PrefsEditor.guarda() }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Tipografía automática", color = Charcoal, fontFamily = Body, fontSize = 14.sp)
                        Text("-- es una raya, ... son puntos suspensivos y las comillas son « ».", color = Muted, fontFamily = Body, fontSize = 12.sp)
                    }
                    Interruptor(PrefsEditor.tipografia) { PrefsEditor.tipografia = it; PrefsEditor.guarda() }
                }
                Spacer(Modifier.height(22.dp))
                Antetitulo("Atajos de teclado")
                Spacer(Modifier.height(6.dp))
                ListaAtajos()
            }
            else -> AjustesVersion("cobalto", "Cobalto")
        }
    }
}
