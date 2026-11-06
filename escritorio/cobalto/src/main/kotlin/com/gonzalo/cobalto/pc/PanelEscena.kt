package com.gonzalo.cobalto.pc

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.cobalto.Doc
import com.gonzalo.cobalto.EscribirDiff
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.Parte
import com.gonzalo.cobalto.Version
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ES = Locale.forLanguageTag("es-ES")
private fun fechaHora(t: Long) = SimpleDateFormat("d MMM · HH:mm", ES).format(Date(t)).replace(".", "")

/**
 * El panel de la escena, al lado del texto (como el de Atrio y el del móvil): la escena (estado, resumen, punto de vista, lugar,
 * cuándo, etiquetas y meta), las fichas (las que salen aquí, primero), las notas (para insertarlas), los borradores (otra versión
 * de la escena, para compararla y mezclar) y el historial (las versiones que se guardan solas en este ordenador).
 */
@Composable
fun PanelEscena(e: Escritos, esc: Doc, textoActual: String, modifier: Modifier, abre: AbreDoc, abrirEscena: (String) -> Unit, inserta: (String) -> Unit, pon: (String) -> Unit, onCierra: () -> Unit) {
    val pestanas = listOf("Escena", "Fichas", "Notas", "Borradores", "Historial")
    Column(modifier.background(Paper)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pestanas.forEachIndexed { i, n -> Opcion(n, PrefsEditor.pestanaPanel == i) { PrefsEditor.pestanaPanel = i; PrefsEditor.guarda() } }
            }
            Tecla("✕", "Cerrar el panel") { onCierra() }
        }
        Filete(1.dp, Line)
        val scroll = rememberScrollState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 18.dp, end = 16.dp, top = 12.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (PrefsEditor.pestanaPanel) {
                    0 -> PestanaEscena(e, esc)
                    1 -> PestanaFichas(e, esc.pid, textoActual, abre, inserta)
                    2 -> PestanaNotas(e, esc.pid, abre, inserta)
                    3 -> PestanaBorradores(e, esc, textoActual, pon)
                    else -> PestanaHistorial(e, esc, textoActual, pon)
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

/** Una tarjeta del panel: el borde fino, el punto de color y lo de dentro. */
@Composable
private fun Tarjeta(borde: Color = Line, contenido: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().clip(esquina(6.dp)).border(1.dp, borde, esquina(6.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = contenido)

@Composable
private fun TextoPanel(t: String, color: Color = Muted, lineas: Int = Int.MAX_VALUE) =
    Text(t, color = color, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, maxLines = lineas, overflow = TextOverflow.Ellipsis)

@Composable
private fun PestanaEscena(e: Escritos, esc: Doc) {
    val fichas = e.fichas(esc.pid)
    var resumen by remember(esc.id) { mutableStateOf(esc.s("resumen")) }
    var cuando by remember(esc.id) { mutableStateOf(esc.s("cuando")) }
    var etq by remember(esc.id) { mutableStateOf(esc.lista("etq").joinToString(", ")) }
    var meta by remember(esc.id) { mutableStateOf(esc.i("meta").takeIf { it > 0 }?.toString().orEmpty()) }
    LaunchedEffect(resumen, cuando, etq, meta) {
        delay(500)
        val d = e.get(esc.id) ?: return@LaunchedEffect
        val etiquetas = etq.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (resumen != d.s("resumen") || cuando != d.s("cuando") || etiquetas != d.lista("etq") || (meta.toIntOrNull() ?: 0) != d.i("meta"))
            e.edita(esc.id) { put("resumen", resumen); put("cuando", cuando); put("etq", JSONArray(etiquetas)); put("meta", meta.toIntOrNull() ?: 0) }
    }
    EtiquetaCampo("Estado")
    FilaOpciones { ESTADOS_ESCENA.forEachIndexed { i, n -> Opcion(n, esc.i("estado") == i) { e.edita(esc.id) { put("estado", i) } } } }
    CampoRevista(resumen, { resumen = it }, "Resumen de la escena", lineas = 4, pista = "Qué pasa aquí, en dos líneas")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Desplegable("Punto de vista", esc.s("pov"), listOf("" to "—") + fichas.filter { it.s("tipo") == "personaje" }.map { it.id to it.s("nombre").ifBlank { "Sin nombre" } }, Modifier.weight(1f)) { v ->
            e.edita(esc.id) { if (v.isBlank()) remove("pov") else put("pov", v) } }
        Desplegable("Lugar", esc.s("lugar"), listOf("" to "—") + fichas.filter { it.s("tipo") == "lugar" }.map { it.id to it.s("nombre").ifBlank { "Sin nombre" } }, Modifier.weight(1f)) { v ->
            e.edita(esc.id) { if (v.isBlank()) remove("lugar") else put("lugar", v) } }
    }
    CampoRevista(cuando, { cuando = it }, "Cuándo pasa (para la línea temporal)", pista = "Día 3, primavera de 1887…")
    CampoRevista(etq, { etq = it }, "Etiquetas (separadas por comas)", pista = "flashback, Aldara…")
    CampoRevista(meta, { meta = it.filter(Char::isDigit).take(6) }, "Meta de palabras de esta escena")
}

@Composable
private fun PestanaFichas(e: Escritos, pid: String, texto: String, abre: AbreDoc, inserta: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var abierta by remember { mutableStateOf<String?>(null) }
    val aqui = remember(texto.length / 40, e.version) { e.fichasEn(pid, texto).map { it.id }.toSet() }
    val todas = e.fichas(pid).filter { q.isBlank() || it.s("nombre").contains(q, true) || it.s("alias").contains(q, true) }.sortedWith(compareByDescending<Doc> { it.id in aqui }.thenBy { it.s("nombre").lowercase() })
    CampoRevista(q, { q = it }, "", pista = "Buscar una ficha…")
    if (todas.isEmpty()) TextoPanel(if (e.fichas(pid).isEmpty()) "Aún no hay fichas. Crea la de tu protagonista: quedará subrayada en el texto cada vez que la nombres." else "Nada con «$q».")
    todas.forEach { f ->
        val on = abierta == f.id
        Tarjeta {
            Row(Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand).clickableSinFoco { abierta = if (on) null else f.id }, verticalAlignment = Alignment.CenterVertically) {
                Punto(colorEscrito(f.s("color")))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.s("nombre").ifBlank { "Sin nombre" }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(singularFicha(f.s("tipo")).uppercase() + if (f.id in aqui) " · SALE AQUÍ" else "", color = if (f.id in aqui) Acento else Muted, fontFamily = Body, fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                }
            }
            if (on) {
                if (f.s("resumen").isNotBlank()) Text(f.s("resumen"), color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 19.sp)
                camposDe(f).filter { it.second.isNotBlank() }.forEach { (k, v) -> TextoPanel("$k: $v") }
                if (f.s("texto").isNotBlank()) TextoPanel(f.s("texto"), Charcoal, 6)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AccionPeque("Poner el nombre", acento = true) { inserta(f.s("nombre")) }
                    AccionPeque("Leer la ficha") { abre.ver(f.id) }
                }
            }
        }
    }
    AccionPeque("+ Nueva ficha", acento = true) { abre.editar(Nuevos.ficha(e, pid).id, esNuevo = true) }
}

@Composable
private fun PestanaNotas(e: Escritos, pid: String, abre: AbreDoc, inserta: (String) -> Unit) {
    var abierta by remember { mutableStateOf<String?>(null) }
    val notas = e.notas(pid).sortedWith(compareByDescending<Doc> { it.b("fija") }.thenByDescending { it.s("tipo") == "plantilla" }.thenByDescending { it.u })
    if (notas.isEmpty()) TextoPanel("Notas, ideas, investigación y plantillas de la obra: aquí las tienes al lado mientras escribes.")
    notas.forEach { n ->
        val on = abierta == n.id
        Tarjeta {
            Row(Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand).clickableSinFoco { abierta = if (on) null else n.id }, verticalAlignment = Alignment.CenterVertically) {
                Punto(colorEscrito(n.s("color")))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.s("titulo").ifBlank { "Sin título" }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(singularNota(n.s("tipo")).uppercase() + if (n.b("fija")) " · FIJADA" else "", color = Muted, fontFamily = Body, fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                }
            }
            if (on) {
                if (n.s("texto").isNotBlank()) TextoPanel(n.s("texto"), Charcoal, 14)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (n.s("texto").isNotBlank()) AccionPeque(if (n.s("tipo") == "plantilla") "Insertar la plantilla" else "Insertar el texto", acento = true) { inserta(n.s("texto")) }
                    AccionPeque("Leer la nota") { abre.ver(n.id) }
                }
            }
        }
    }
    AccionPeque("+ Nueva nota", acento = true) { abre.editar(Nuevos.nota(e, pid).id, esNuevo = true) }
}

@Composable
private fun PestanaBorradores(e: Escritos, esc: Doc, actual: String, pon: (String) -> Unit) {
    var comparar by remember { mutableStateOf<Doc?>(null) }
    var borrar by remember { mutableStateOf<String?>(null) }
    val bs = e.borradores(esc.id).reversed()
    TextoPanel("Un borrador es otra versión de esta escena: pruébala sin perder la buena, compárala y mezcla lo mejor de cada una.")
    AccionPeque("+ Guardar el texto actual como borrador", acento = true) {
        Editor.guardaYa()
        e.nuevo(Escritos.T_BORRADOR, esc.pid) { put("eid", esc.id); put("nombre", "Borrador del " + fechaHora(System.currentTimeMillis())); put("texto", actual) }
    }
    bs.forEach { b ->
        Tarjeta(if (b.b("conflicto")) Acento else Line) {
            Text(b.s("nombre").ifBlank { "Borrador" }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(palabrasTexto(Escritos.palabras(b.s("texto"))) + if (b.b("conflicto")) " · se guardó al juntar dos cambios a la vez" else "", color = if (b.b("conflicto")) Acento else Muted, fontFamily = Body, fontSize = 11.sp)
            TextoPanel(b.s("texto").take(220).replace('\n', ' ') + if (b.s("texto").length > 220) "…" else "", Charcoal, 4)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AccionPeque("Comparar y mezclar", acento = true) { comparar = b }
                AccionPeque("Usar este") {
                    e.nuevo(Escritos.T_BORRADOR, esc.pid) { put("eid", esc.id); put("nombre", "Antes de usar «${b.s("nombre")}»"); put("texto", actual) }
                    pon(b.s("texto"))
                }
                AccionPeque(if (borrar == b.id) "Sí, borrar" else "Borrar", apagada = borrar != b.id, acento = borrar == b.id) { if (borrar == b.id) { e.borra(b.id); borrar = null } else borrar = b.id }
            }
        }
    }
    comparar?.let { b -> DialogoComparar(actual, b.s("nombre"), b.s("texto"), onCerrar = { comparar = null }, onAplica = { t -> e.instantanea(esc.id, actual, fuerza = true); pon(t); comparar = null }) }
}

@Composable
private fun PestanaHistorial(e: Escritos, esc: Doc, actual: String, pon: (String) -> Unit) {
    var comparar by remember { mutableStateOf<Version?>(null) }
    val vs = remember(esc.u, actual.length / 50) { e.versiones(esc.id).reversed() }
    TextoPanel("Cada cierto tiempo (y al salir) se guarda una versión de la escena en este ordenador: las últimas ${Escritos.VERSIONES}. ${Atajo.texto("Ctrl+S")} guarda una ahora.")
    if (vs.isEmpty()) TextoPanel("Todavía no hay versiones: se guardan solas mientras escribes.")
    vs.forEach { v ->
        Tarjeta {
            Text(fechaHora(v.u) + " · " + palabrasTexto(v.palabras), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            TextoPanel(v.texto.take(160).replace('\n', ' ') + if (v.texto.length > 160) "…" else "", lineas = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AccionPeque("Comparar", acento = true) { comparar = v }
                AccionPeque("Restaurar") { e.instantanea(esc.id, actual, fuerza = true); pon(v.texto) }
            }
        }
    }
    comparar?.let { v -> DialogoComparar(actual, fechaHora(v.u), v.texto, onCerrar = { comparar = null }, onAplica = { t -> e.instantanea(esc.id, actual, fuerza = true); pon(t); comparar = null }) }
}

/** Las diferencias entre el texto de ahora y otra versión, párrafo a párrafo, para quedarte con lo que quieras de cada una. */
@Composable
fun DialogoComparar(actual: String, nombre: String, otro: String, onCerrar: () -> Unit, onAplica: (String) -> Unit) {
    val partes = remember(actual, otro) { EscribirDiff.compara(actual, otro) }
    val cambios = partes.filterIsInstance<Parte.Cambio>()
    val elige = remember(partes) { mutableStateListOf<Boolean>().also { l -> repeat(cambios.size) { l.add(false) } } }
    Hoja(onCerrar, ancho = 1100.dp) {
        Column(Modifier.fillMaxWidth().padding(start = 36.dp, end = 36.dp, top = 24.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Comparar con «$nombre»", Modifier.weight(1f), color = Charcoal, fontFamily = Display, fontSize = 30.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                EnlaceRevista("Cerrar ✕") { onCerrar() }
            }
            Filete(2.dp)
            Spacer(Modifier.height(8.dp))
            Text(if (cambios.isEmpty()) "Son iguales." else "${cambios.size} ${if (cambios.size == 1) "diferencia" else "diferencias"}. Elige en cada una qué texto te quedas: a la izquierda el de ahora, a la derecha el otro.",
                color = Muted, fontFamily = Body, fontSize = 13.sp)
        }
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(scroll).padding(horizontal = 36.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            var k = 0
            partes.forEach { p ->
                when (p) {
                    is Parte.Igual -> Text(p.parrafos.joinToString("\n\n").let { if (it.length > 220) it.take(110) + " … " + it.takeLast(90) else it }, color = Muted, fontFamily = Display, fontSize = 14.sp, lineHeight = 20.sp)
                    is Parte.Cambio -> {
                        val i = k++
                        val otroOn = elige.getOrElse(i) { false }
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            @Composable fun Lado(t: String, on: Boolean, etiqueta: String, onClick: () -> Unit) =
                                Column(Modifier.weight(1f).fillMaxHeight().clip(esquina(6.dp)).background(if (on) Acento.copy(alpha = 0.12f) else Color.Transparent)
                                    .border(if (on) 2.dp else 1.dp, if (on) Acento else Line, esquina(6.dp)).pointerHoverIcon(PointerIcon.Hand).clickableSinFoco(onClick).padding(12.dp)) {
                                    Text((if (on) "✓ " else "") + etiqueta, color = if (on) Acento else Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                                    Text(t.ifBlank { "(nada)" }, color = Charcoal, fontFamily = Display, fontSize = 15.sp, lineHeight = 22.sp)
                                }
                            Lado(p.aqui.joinToString("\n\n"), !otroOn, "EL DE AHORA") { if (i < elige.size) elige[i] = false }
                            Lado(p.otro.joinToString("\n\n"), otroOn, "EL OTRO") { if (i < elige.size) elige[i] = true }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (cambios.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            BotonSuave("Quedarme con el otro entero") { onAplica(otro) }
            BotonAcento("Aplicar la mezcla", alto = 44.dp) { onAplica(EscribirDiff.mezcla(partes, elige.toList())) }
        }
    }
}
