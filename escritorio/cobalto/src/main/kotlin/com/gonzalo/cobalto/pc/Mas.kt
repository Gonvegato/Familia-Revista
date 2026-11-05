package com.gonzalo.cobalto.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

private val TIPOS_FICHA = listOf("personaje" to "Personaje", "lugar" to "Lugar", "objeto" to "Objeto", "mundo" to "Mundo", "otro" to "Otro")
private val TIPOS_NOTA = listOf("nota" to "Nota", "idea" to "Idea", "resumen" to "Resumen", "esquema" to "Esquema", "investigacion" to "Investigación", "plantilla" to "Plantilla")

/** Una lista a la izquierda y lo elegido a la derecha (las fichas y las notas). */
@Composable
private fun ListaYEdicion(docs: List<Doc>, sel: String?, titulo: (Doc) -> String, sub: (Doc) -> String, onSel: (String) -> Unit, nuevo: () -> Unit, nuevoTexto: String, edicion: @Composable (Doc) -> Unit) {
    Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 14.dp)) {
            EnlaceRevista(nuevoTexto, acento = true, modifier = Modifier.padding(vertical = 8.dp)) { nuevo() }
            docs.forEach { d ->
                val on = d.id == sel
                Column(Modifier.fillMaxWidth().background(if (on) Sand else Color.Transparent).clickable { onSel(d.id) }.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(titulo(d), color = Charcoal, fontFamily = Display, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(sub(d), color = Muted, fontFamily = Body, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (docs.isEmpty()) Text("Nada todavía.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
        }
        Filete(1.dp, Line, Modifier.fillMaxHeight().width(1.dp))
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 24.dp, bottom = 40.dp)) {
            val d = docs.firstOrNull { it.id == sel }
            if (d == null) Vacio("Elige una de la lista") else edicion(d)
        }
    }
}

/** Las fichas de la obra (personajes, lugares, objetos…): las mismas que en el móvil. */
@Composable
fun Fichas(obra: Doc, nav: Nav) {
    val e = Cuaderno.escritos
    e.version
    val fichas = e.fichas(obra.id)
    var sel by remember(obra.id) { mutableStateOf(fichas.firstOrNull()?.id) }
    ListaYEdicion(fichas, sel, { it.s("nombre").ifBlank { "Sin nombre" } }, { d -> (TIPOS_FICHA.firstOrNull { it.first == d.s("tipo") }?.second ?: "Ficha") + d.s("resumen").takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty() },
        { sel = it }, { sel = e.nuevo(Escritos.T_FICHA, obra.id) { put("tipo", "personaje"); put("nombre", "") }.id }, "+ Nueva ficha") { f ->
        Campos(f, listOf("nombre" to "Nombre", "alias" to "Alias (separados por comas)", "resumen" to "En una frase"), "texto" to "Todo lo que sabes de ella") {
            MagChoice(TIPOS_FICHA.map { it.second }, TIPOS_FICHA.indexOfFirst { it.first == f.s("tipo") }.coerceAtLeast(0), "Tipo") { i -> e.edita(f.id) { put("tipo", TIPOS_FICHA[i].first) } }
        }
        val donde = e.escenasDe(obra.id).filter { s -> e.fichasEn(obra.id, s.s("texto")).any { it.id == f.id } }
        if (donde.isNotEmpty()) {
            Spacer(Modifier.height(16.dp)); Antetitulo("Sale en ${donde.size} ${if (donde.size == 1) "escena" else "escenas"}")
            donde.take(30).forEach { s -> EnlaceRevista(s.s("titulo").ifBlank { "Escena" } + " · " + e.get(s.s("cid"))?.s("titulo").orEmpty(), modifier = Modifier.padding(top = 8.dp)) { nav.ir(Pantalla.Obra(obra.id, s.id)) } }
        }
        Spacer(Modifier.height(18.dp))
        EnlaceRevista("A la papelera", acento = true) { e.borra(f.id); sel = null }
    }
}

/** Las notas de la obra (ideas, investigación, esquemas…). */
@Composable
fun Notas(obra: Doc, nav: Nav) {
    val e = Cuaderno.escritos
    e.version
    val notas = e.notas(obra.id).sortedWith(compareByDescending<Doc> { it.b("fija") }.thenByDescending { it.u })
    var sel by remember(obra.id) { mutableStateOf(notas.firstOrNull()?.id) }
    ListaYEdicion(notas, sel, { it.s("titulo").ifBlank { "Nota" } }, { d -> (if (d.b("fija")) "Fijada · " else "") + (TIPOS_NOTA.firstOrNull { it.first == d.s("tipo") }?.second ?: "Nota") },
        { sel = it }, { sel = e.nuevo(Escritos.T_NOTA, obra.id) { put("tipo", "nota"); put("titulo", "") }.id }, "+ Nueva nota") { n ->
        Campos(n, listOf("titulo" to "Título"), "texto" to "La nota") {
            MagChoice(TIPOS_NOTA.map { it.second }, TIPOS_NOTA.indexOfFirst { it.first == n.s("tipo") }.coerceAtLeast(0), "Tipo") { i -> e.edita(n.id) { put("tipo", TIPOS_NOTA[i].first) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fijada arriba", Modifier.weight(1f), color = Charcoal, fontFamily = Body, fontSize = 14.sp)
                Interruptor(n.b("fija")) { on -> e.edita(n.id) { if (on) put("fija", 1) else remove("fija") } }
            }
        }
        Spacer(Modifier.height(18.dp))
        EnlaceRevista("A la papelera", acento = true) { e.borra(n.id); sel = null }
    }
}

/** Los campos de texto de un documento (se guardan al escribir, un momento después de parar). */
@Composable
private fun Campos(d: Doc, cortos: List<Pair<String, String>>, largo: Pair<String, String>, extra: @Composable () -> Unit = {}) {
    val e = Cuaderno.escritos
    cortos.forEach { (k, etq) ->
        var v by remember(d.id, k) { mutableStateOf(d.s(k)) }
        OutlinedTextField(v, { v = it; e.edita(d.id) { put(k, it) } }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text(etq) }, singleLine = true)
    }
    extra()
    var t by remember(d.id) { mutableStateOf(d.s(largo.first)) }
    OutlinedTextField(t, { t = it; e.edita(d.id) { put(largo.first, it) } }, Modifier.fillMaxWidth().heightIn(min = 260.dp).padding(vertical = 4.dp), label = { Text(largo.second) })
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
            val tipo = when (d.t) { Escritos.T_OBRA -> "Obra"; Escritos.T_CAP -> "Capítulo"; Escritos.T_ESCENA -> "Escena"; Escritos.T_PARTE -> "Parte"; Escritos.T_FICHA -> "Ficha"; Escritos.T_NOTA -> "Nota"; else -> "Documento" }
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
        PestanasRevista(listOf("Cuenta", "Copias", "Apariencia", "Versión"), pestana) { pestana = it }
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
            else -> AjustesVersion("cobalto", "Cobalto")
        }
    }
}
