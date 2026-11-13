package com.gonzalo.cobalto.pc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.cobalto.Doc
import com.gonzalo.cobalto.Escritos
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/*
 * Los formularios para editar una ficha, una nota, un suceso, una rama o una unión: todos los campos del móvil
 * (EscribirDialogos.kt), con los mismos nombres en el documento. Lo que escribes se guarda solo un momento después de parar
 * (y al salir), así cada tecla no es un cambio en la cuenta; lo que se elige (tipo, color…) se guarda al momento.
 */

private const val ESPERA_FORM = 450L

/** La cabecera de un formulario: «Editando…», borrar y «Listo». */
@Composable
private fun CabeceraEdicion(que: String, onListo: () -> Unit, onBorrar: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AnteLectura("Editando · $que")
        Spacer(Modifier.weight(1f))
        if (onBorrar != null) { EnlaceRevista("A la papelera") { onBorrar() }; Spacer(Modifier.width(18.dp)) }
        BotonAcento("Listo", alto = 40.dp, modifier = Modifier.width(110.dp)) { onListo() }
    }
    Text("Se guarda solo mientras escribes. Esc o «Listo» para volver a leer.", color = Muted, fontFamily = Body, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
}

/** Guarda [guarda] un momento después del último cambio de [claves], y al salir. */
@Composable
private fun GuardaAlParar(id: String, vararg claves: Any?, guarda: () -> Unit) {
    val g by rememberUpdatedState(guarda)
    LaunchedEffect(id, *claves) { delay(ESPERA_FORM); g() }
    DisposableEffect(id) { onDispose { g() } }
}

/** Elegir varios (vincular a partes y capítulos): opciones que se encienden y se apagan. */
@Composable
fun VinculosEdicion(e: Escritos, pid: String, elegidos: List<String>, onCambia: (List<String>) -> Unit) {
    val destinos = remember(e.version, pid) {
        e.partes(pid).map { it.id to "Parte · ${it.s("titulo").ifBlank { "Sin título" }}" } +
            e.capitulos(pid).mapIndexed { i, c -> c.id to "Cap. ${i + 1} · ${c.s("titulo").ifBlank { "Sin título" }}" }
    }
    if (destinos.isEmpty()) return
    EtiquetaCampo("Vinculada a")
    FilaOpciones { destinos.forEach { (id, n) -> Opcion(n, id in elegidos) { onCambia(if (id in elegidos) elegidos - id else elegidos + id) } } }
    Text("Sale también con esa parte o ese capítulo.", color = Muted, fontFamily = Body, fontSize = 11.sp)
}

private val estiloNombre get() = TextStyle(fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 44.sp)
private val estiloLargo get() = TextStyle(fontFamily = Grotesca, fontSize = 17.sp, lineHeight = 26.sp)

/** Editar una ficha: tipo, nombre, alias, entradilla, datos, el texto largo, color, tamaño en el árbol y vínculos. */
@Composable
fun EdicionFicha(e: Escritos, f: Doc, onListo: () -> Unit, onBorrar: (() -> Unit)? = null, focoNombre: Boolean = false) {
    val id = f.id
    var nombre by remember(id) { mutableStateOf(f.s("nombre")) }
    var alias by remember(id) { mutableStateOf(f.s("alias")) }
    var resumen by remember(id) { mutableStateOf(f.s("resumen")) }
    var texto by remember(id) { mutableStateOf(f.s("texto")) }
    val campos = remember(id) { mutableStateListOf<Pair<String, String>>().also { l -> f.o.optJSONArray("campos")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { l.add(it.optString("k") to it.optString("v")) } } } }
    val camposClave = campos.toList()
    fun camposJson() = JSONArray().also { a -> campos.filter { it.first.isNotBlank() || it.second.isNotBlank() }.forEach { a.put(JSONObject().put("k", it.first.trim()).put("v", it.second)) } }
    GuardaAlParar(id, nombre, alias, resumen, texto, camposClave) {
        val d = e.get(id) ?: return@GuardaAlParar
        val cj = camposJson()
        val antes = d.o.optJSONArray("campos")?.toString() ?: "[]"
        if (d.s("nombre") != nombre.trim() || d.s("alias") != alias.trim() || d.s("resumen") != resumen.trim() || d.s("texto") != texto || antes != cj.toString())
            e.edita(id) { put("nombre", nombre.trim()); put("alias", alias.trim()); put("resumen", resumen.trim()); put("texto", texto); put("campos", cj) }
    }
    val d = e.get(id) ?: f
    val fNombre = remember { FocusRequester() }
    Column(Modifier.fillMaxWidth()) {
        CabeceraEdicion(singularFicha(d.s("tipo")).lowercase(), onListo, onBorrar)
        CampoRevista(nombre, { nombre = it }, "Nombre", estilo = estiloNombre, pista = "Cómo se llama", foco = fNombre)
        EtiquetaCampo("Tipo")
        FilaOpciones { TIPOS_FICHA.forEach { (k, _) -> Opcion(singularFicha(k), d.s("tipo") == k) { e.edita(id) { put("tipo", k) } } } }
        CampoRevista(alias, { alias = it }, "Otros nombres (separados por comas): así se reconoce en tus escenas", pista = "Marti, la de los Arroyo…")
        CampoRevista(resumen, { resumen = it }, "En una frase", lineas = 2, estilo = TextStyle(fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 26.sp))
        EtiquetaCampo("Datos")
        campos.forEachIndexed { i, (k, v) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CampoRevista(k, { campos[i] = it to v }, "Dato", Modifier.weight(0.8f), pista = "Edad, aspecto, quiere…")
                CampoRevista(v, { campos[i] = k to it }, "Valor", Modifier.weight(1.6f))
                Tecla("✕", "Quitar este dato") { campos.removeAt(i) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            AccionPeque("+ Añadir dato", acento = true) { campos.add("" to "") }
            if (campos.isEmpty() && d.s("tipo") == "personaje") AccionPeque("+ Los de un personaje") { listOf("Edad", "Aspecto", "Quiere", "Teme").forEach { campos.add(it to "") } }
            if (campos.isEmpty() && d.s("tipo") == "lugar") AccionPeque("+ Los de un lugar") { listOf("Clima", "Gente").forEach { campos.add(it to "") } }
        }
        CampoRevista(texto, { texto = it }, "Todo lo que sabes (historia, personalidad, detalles…)", lineas = 10, estilo = estiloLargo,
            pista = "Puedes usar # títulos, > citas, *cursiva* y **negrita**.")
        EtiquetaCampo("Color")
        Muestras(d.s("color")) { c -> e.edita(id) { put("color", c) } }
        EtiquetaCampo("Tamaño en el árbol")
        FilaOpciones { listOf(0 to "Según el tipo", 1 to "Pequeña", 2 to "Mediana", 3 to "Grande").forEach { (k, n) -> Opcion(n, (d.i("tam").takeIf { it in 1..3 } ?: 0) == k) { e.edita(id) { if (k > 0) put("tam", k) else remove("tam") } } } }
        VinculosEdicion(e, d.pid, d.vin()) { l -> e.edita(id) { ponVin(l) } }
        Spacer(Modifier.height(30.dp))
    }
    LaunchedEffect(id) { if (focoNombre) runCatching { fNombre.requestFocus() } }
}

/** Editar una nota: tipo, título, texto, color, fijada y vínculos. */
@Composable
fun EdicionNota(e: Escritos, n: Doc, onListo: () -> Unit, onBorrar: (() -> Unit)? = null, focoTitulo: Boolean = false) {
    val id = n.id
    var titulo by remember(id) { mutableStateOf(n.s("titulo")) }
    var texto by remember(id) { mutableStateOf(n.s("texto")) }
    GuardaAlParar(id, titulo, texto) {
        val d = e.get(id) ?: return@GuardaAlParar
        if (d.s("titulo") != titulo.trim() || d.s("texto") != texto) e.edita(id) { put("titulo", titulo.trim()); put("texto", texto) }
    }
    val d = e.get(id) ?: n
    val fTitulo = remember { FocusRequester() }
    Column(Modifier.fillMaxWidth()) {
        CabeceraEdicion(singularNota(d.s("tipo")).lowercase(), onListo, onBorrar)
        CampoRevista(titulo, { titulo = it }, "Título", estilo = estiloNombre, pista = "De qué va", foco = fTitulo)
        EtiquetaCampo("Tipo")
        FilaOpciones { TIPOS_NOTA.forEach { (k, _) -> Opcion(singularNota(k), d.s("tipo").ifBlank { "nota" } == k) { e.edita(id) { put("tipo", k) } } } }
        if (d.s("tipo") == "plantilla") Text("Una plantilla es un texto que puedes tener al lado mientras escribes o insertar en una escena (una ficha de escena, una lista de comprobación…).",
            color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        CampoRevista(texto, { texto = it }, "Texto", lineas = 14, estilo = estiloLargo, pista = "Puedes usar # títulos, > citas, - listas, *cursiva* y **negrita**.")
        EtiquetaCampo("Color")
        Muestras(d.s("color")) { c -> e.edita(id) { put("color", c) } }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Text("Fijada arriba", Modifier.weight(1f), color = Charcoal, fontFamily = Body, fontSize = 14.sp)
            Interruptor(d.b("fija")) { on -> e.edita(id) { if (on) put("fija", 1) else remove("fija") } }
        }
        VinculosEdicion(e, d.pid, d.vin()) { l -> e.edita(id) { ponVin(l) } }
        Spacer(Modifier.height(30.dp))
    }
    LaunchedEffect(id) { if (focoTitulo) runCatching { fTitulo.requestFocus() } }
}

/** Editar un suceso de la línea temporal: qué pasa, cuándo, en qué línea, qué más, la escena y el color. */
@Composable
fun EdicionHito(e: Escritos, h: Doc, onListo: () -> Unit, onBorrar: (() -> Unit)? = null) {
    val id = h.id
    var titulo by remember(id) { mutableStateOf(h.s("titulo")) }
    var cuando by remember(id) { mutableStateOf(h.s("cuando")) }
    var nota by remember(id) { mutableStateOf(h.s("nota")) }
    GuardaAlParar(id, titulo, cuando, nota) {
        val d = e.get(id) ?: return@GuardaAlParar
        if (d.s("titulo") != titulo.trim() || d.s("cuando") != cuando.trim() || d.s("nota") != nota) e.edita(id) { put("titulo", titulo.trim()); put("cuando", cuando.trim()); put("nota", nota) }
    }
    val d = e.get(id) ?: h
    val pid = d.pid
    val escenas = remember(e.version) { listOf("" to "Ninguna") + e.escenasDe(pid).map { it.id to it.s("titulo").ifBlank { "Sin título" } } }
    val ramas = remember(e.version) { listOf("" to "La línea principal") + e.ramas(pid).map { it.id to "Rama · " + it.s("titulo").ifBlank { "Sin título" } } }
    Column(Modifier.fillMaxWidth()) {
        CabeceraEdicion("suceso", onListo, onBorrar)
        CampoRevista(titulo, { titulo = it }, "Qué pasa", estilo = estiloNombre)
        CampoRevista(cuando, { cuando = it }, "Cuándo", pista = "Día 3, primavera del año 12, 1887…")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            if (ramas.size > 1) Desplegable("En qué línea", d.s("rama"), ramas, Modifier.weight(1f)) { v -> e.edita(id) { if (v.isBlank()) remove("rama") else put("rama", v) } }
            Desplegable("Se cuenta en la escena", d.s("eid"), escenas, Modifier.weight(1f)) { v -> e.edita(id) { if (v.isBlank()) remove("eid") else put("eid", v) } }
        }
        CampoRevista(nota, { nota = it }, "Qué más", lineas = 8, estilo = estiloLargo)
        EtiquetaCampo("Color")
        Muestras(d.s("color"), conNinguno = true) { c -> e.edita(id) { if (c.isBlank()) remove("color") else put("color", c) } }
        Spacer(Modifier.height(30.dp))
    }
}

/** Editar una rama: nombre, de qué va, de qué suceso nace, si vuelve a la principal (después de cuál) y su color. */
@Composable
fun EdicionRama(e: Escritos, r: Doc, onListo: () -> Unit, onBorrar: (() -> Unit)? = null) {
    val id = r.id
    var titulo by remember(id) { mutableStateOf(r.s("titulo")) }
    var texto by remember(id) { mutableStateOf(r.s("texto")) }
    GuardaAlParar(id, titulo, texto) {
        val d = e.get(id) ?: return@GuardaAlParar
        if (d.s("titulo") != titulo.trim().ifBlank { "Rama" } || d.s("texto") != texto) e.edita(id) { put("titulo", titulo.trim().ifBlank { "Rama" }); put("texto", texto) }
    }
    val d = e.get(id) ?: r
    val principal = remember(e.version) { e.hitos(d.pid).filter { it.s("rama").isEmpty() || e.get(it.s("rama")) == null } }
    val opciones = principal.map { it.orden.toString() to it.s("titulo").ifBlank { "Sin título" } }
    Column(Modifier.fillMaxWidth()) {
        CabeceraEdicion("rama", onListo, onBorrar)
        CampoRevista(titulo, { titulo = it }, "Nombre", estilo = estiloNombre, pista = "La huida de Juan, el pasado de Ana…")
        CampoRevista(texto, { texto = it }, "De qué va", lineas = 3, estilo = estiloLargo)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            Desplegable("Nace después de", d.o.opt("desde")?.toString()?.toDoubleOrNull()?.toString().orEmpty(), listOf("" to "Desde el principio") + opciones, Modifier.weight(1f)) { v ->
                val x = v.toDoubleOrNull() ?: ((principal.firstOrNull()?.orden ?: 1000.0) - 1)
                e.edita(id) { put("desde", x) }
            }
            Desplegable("Vuelve a la principal después de", d.o.opt("hasta")?.toString()?.toDoubleOrNull()?.toString().orEmpty(), listOf("" to "No vuelve") + opciones, Modifier.weight(1f)) { v ->
                e.edita(id) { val x = v.toDoubleOrNull(); if (x == null) remove("hasta") else put("hasta", x) }
            }
        }
        EtiquetaCampo("Color")
        Muestras(d.s("color")) { c -> e.edita(id) { put("color", c) } }
        Spacer(Modifier.height(30.dp))
    }
}

/** Editar una unión del árbol: las dos fichas, la palabra de la línea, lo que son el uno para el otro, la flecha y el color. */
@Composable
fun EdicionRelacion(e: Escritos, r: Doc, onListo: () -> Unit, onBorrar: (() -> Unit)? = null, focoTitulo: Boolean = false) {
    val id = r.id
    var titulo by remember(id) { mutableStateOf(r.s("titulo")) }
    var texto by remember(id) { mutableStateOf(r.s("texto")) }
    GuardaAlParar(id, titulo, texto) {
        val d = e.get(id) ?: return@GuardaAlParar
        if (d.s("titulo") != titulo.trim() || d.s("texto") != texto) e.edita(id) { put("titulo", titulo.trim()); put("texto", texto) }
    }
    val d = e.get(id) ?: r
    val fichas = remember(e.version) { e.fichas(d.pid).map { it.id to it.s("nombre").ifBlank { "Sin nombre" } } }
    fun nombre(x: String) = fichas.find { it.first == x }?.second ?: "…"
    val a = d.s("a"); val b = d.s("b")
    val fTitulo = remember { FocusRequester() }
    Column(Modifier.fillMaxWidth()) {
        CabeceraEdicion("unión", onListo, onBorrar)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            Desplegable("Una", a, fichas, Modifier.weight(1f)) { v -> if (v != b) e.edita(id) { put("a", v) } }
            Desplegable("Con", b, fichas, Modifier.weight(1f)) { v -> if (v != a) e.edita(id) { put("b", v) } }
        }
        CampoRevista(titulo, { titulo = it }, "La palabra de la línea", estilo = estiloNombre, pista = "Enemigos, amantes, hogar…", foco = fTitulo)
        CampoRevista(texto, { texto = it }, "Qué son el uno para el otro (opcional)", lineas = 7, estilo = estiloLargo)
        EtiquetaCampo("Flecha")
        val flecha = d.i("flecha") == 1
        FilaOpciones {
            Opcion("Sin flecha", !flecha) { e.edita(id) { put("flecha", 0) } }
            Opcion("De ${nombre(a)} hacia ${nombre(b)}", flecha) { e.edita(id) { put("flecha", 1) } }
            // (la flecha se guarda siempre de «a» hacia «b»: al revés, se cambian)
            Opcion("De ${nombre(b)} hacia ${nombre(a)}", false) { e.edita(id) { put("a", b); put("b", a); put("flecha", 1) } }
        }
        EtiquetaCampo("Color de la línea")
        Muestras(d.s("color"), conNinguno = true) { c -> e.edita(id) { if (c.isBlank()) remove("color") else put("color", c) } }
        Spacer(Modifier.height(30.dp))
    }
    LaunchedEffect(id) { if (focoTitulo) runCatching { fTitulo.requestFocus() } }
}

/** Lo que se crea nuevo: una ficha (con los datos de su tipo) y una nota, como en el móvil. */
object Nuevos {
    fun ficha(e: Escritos, pid: String, tipo: String = "personaje", campos: List<String>? = null): Doc = e.nuevo(Escritos.T_FICHA, pid) {
        put("tipo", tipo); put("nombre", ""); put("color", COLORES_ESCRITO[5])
        val l = campos ?: when (tipo) { "personaje" -> listOf("Edad", "Aspecto", "Quiere", "Teme"); "lugar" -> listOf("Clima", "Gente"); else -> emptyList() }
        put("campos", JSONArray().also { a -> l.forEach { a.put(JSONObject().put("k", it).put("v", "")) } })
    }
    fun nota(e: Escritos, pid: String, tipo: String = "nota", vin: List<String> = emptyList()): Doc = e.nuevo(Escritos.T_NOTA, pid) {
        put("tipo", tipo); put("titulo", ""); put("texto", ""); put("color", COLORES_ESCRITO[2]); if (vin.isNotEmpty()) ponVin(vin)
    }
}
