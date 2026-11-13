package com.gonzalo.cobalto.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.cobalto.Doc
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.Formato
import com.gonzalo.cobalto.GuiaNovela
import com.gonzalo.cobalto.datosGuia
import com.gonzalo.familia.pc.revista.*

// Los pasos de la guía, sus textos y lo que se da por hecho (Paso, DatosGuia, EstadoPaso, GuiaNovela, datosGuia) son los del móvil:
// EscribirGuia.kt, el mismo archivo, copiado al compilar. Aquí, solo lo que hace cada paso y las pantallas del ordenador.

/** Lo que hace el botón de cada paso (en la obra): abrir un diálogo, crear una nota y abrirla, ir a un sitio… */
class AccionesGuia(val abre: AbreDoc, val irManuscrito: () -> Unit, val abrirEscena: (String) -> Unit, val exportar: () -> Unit, val frase: () -> Unit, val meta: () -> Unit)

fun haceGuia(id: Int, e: Escritos, obra: Doc, a: AccionesGuia) {
    val pid = obra.id
    when (id) {
        1 -> a.frase()
        2 -> a.abre.editar(Nuevos.ficha(e, pid, "personaje", listOf("Quiere", "Se lo impide", "Si fracasa", "Edad")).id, esNuevo = true)
        3 -> { val n = e.notas(pid).firstOrNull { it.s("titulo") == GuiaNovela.TITULO_ESQUEMA } ?: e.nuevo(Escritos.T_NOTA, pid) {
            put("tipo", "idea"); put("titulo", GuiaNovela.TITULO_ESQUEMA); put("color", COLORES_ESCRITO[1]); put("texto", GuiaNovela.PLANTILLA_ESQUEMA) }; a.abre.editar(n.id) }
        4 -> { val n = e.notas(pid).firstOrNull { it.s("titulo") == GuiaNovela.TITULO_PROMESAS } ?: e.nuevo(Escritos.T_NOTA, pid) {
            put("tipo", "idea"); put("titulo", GuiaNovela.TITULO_PROMESAS); put("color", COLORES_ESCRITO[2]); put("texto", GuiaNovela.PLANTILLA_PROMESAS) }; a.abre.editar(n.id) }
        5 -> a.meta()
        6 -> { val s = e.escenasDe(pid).maxByOrNull { it.u }; if (s != null) a.abrirEscena(s.id) else a.irManuscrito() }
        7 -> e.edita(pid) { put("g7t", System.currentTimeMillis()) }
        8, 9 -> a.irManuscrito()
        10, 11 -> a.exportar()
    }
}

/** Los diálogos de la guía que no son una ficha ni una nota: la frase de la idea y la meta de cada día. */
@Composable
fun DialogosGuia(e: Escritos, obra: Doc, frase: Boolean, meta: Boolean, onCerrar: () -> Unit) {
    if (frase) {
        var t by remember { mutableStateOf(obra.s("idea")) }
        Dialogo("Tu historia en una frase", onCerrar, ancho = 640.dp, botones = {
            BotonSuave("Cancelar") { onCerrar() }
            BotonAcento("Guardar", alto = 44.dp, modifier = Modifier.width(120.dp)) { if (t.isNotBlank()) { e.edita(obra.id) { put("idea", t.trim()) }; onCerrar() } }
        }) {
            Text("Quién quiere qué, y qué se lo impide. Corta y sin nombres propios si puedes; no tiene que ser perfecta.", color = Muted, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp)
            CampoRevista(t, { t = it }, "La frase", lineas = 3, estilo = TextStyle(fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 28.sp))
            Text("Ejemplo: Una aprendiz de hechicera sube a la torre donde nadie vuelve para salvar a su hermana, y descubre que lo que la espera arriba es ella misma.",
                color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
    if (meta) {
        var dia by remember { mutableStateOf(obra.i("diaria").takeIf { it > 0 }?.toString() ?: "300") }
        Dialogo("Tu meta de cada día", onCerrar, ancho = 560.dp, botones = {
            BotonSuave("Cancelar") { onCerrar() }
            BotonAcento("Guardar", alto = 44.dp, modifier = Modifier.width(120.dp)) { e.edita(obra.id) { put("diaria", dia.toIntOrNull() ?: 0) }; onCerrar() }
        }) {
            Text("Elige una cantidad que te parezca casi demasiado fácil. Mejor cumplirla todos los días que abandonar una grande.", color = Muted, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp)
            FilaOpciones { listOf("150", "300", "500", "1000").forEach { x -> Opcion(x, dia == x) { dia = x } } }
            CampoRevista(dia, { dia = it.filter(Char::isDigit).take(6) }, "Palabras al día")
            Text("Para empezar, unas 300 (una sugerencia de Thot). Siempre puedes subirla.", color = Muted, fontFamily = Body, fontSize = 12.sp)
        }
    }
}

/** El paso que toca ahora, arriba del índice del manuscrito (solo con la guía activada). */
@Composable
fun TarjetaGuia(e: Escritos, obra: Doc, hacer: (Int) -> Unit, verGuia: () -> Unit) {
    val ver = e.version
    if (!GuiaNovela.activa(obra)) return
    val est = remember(ver, obra.u) { GuiaNovela.estados(datosGuia(e, obra.id)) }
    val id = GuiaNovela.siguiente(est) ?: return
    val p = GuiaNovela.PASOS.first { it.id == id }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).border(1.dp, Acento).padding(12.dp)) {
        Text("GUÍA · PASO $id DE ${GuiaNovela.PASOS.size}", color = Acento, fontFamily = Body, fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Text(p.titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 21.sp)
        Text(p.que, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AccionPeque(p.accion, acento = true) { hacer(id) }
            AccionPeque("Ver la guía") { verGuia() }
        }
    }
}

/** La pestaña Guía: los 11 pasos, con el que toca abierto. */
@Composable
fun GuiaObra(e: Escritos, obra: Doc, hacer: (Int) -> Unit) {
    val ver = e.version
    val pid = obra.id
    val d = remember(ver, obra.u) { datosGuia(e, pid) }
    val est = remember(d) { GuiaNovela.estados(d) }
    val sig = GuiaNovela.siguiente(est)
    val hechos = GuiaNovela.hechos(est)
    var abierto by remember(pid) { mutableIntStateOf(sig ?: 0) }
    LaunchedEffect(sig) { abierto = sig ?: 0 }
    val color = colorEscrito(obra.s("color"))
    Pagina(ancho = 980.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Antetitulo("La guía de la novela")
                Text("$hechos de ${GuiaNovela.PASOS.size} pasos", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (GuiaNovela.activa(obra)) "Activada: el paso que toca sale en el manuscrito" else "Desactivada: no sale nada en el manuscrito", color = Muted, fontFamily = Body, fontSize = 12.sp)
                EnlaceRevista(if (GuiaNovela.activa(obra)) "Desactivar" else "Activar", acento = !GuiaNovela.activa(obra)) { e.edita(pid) { put("gOn", if (GuiaNovela.activa(obra)) 0 else 1) } }
            }
        }
        Spacer(Modifier.height(8.dp))
        Progreso(hechos.toFloat() / GuiaNovela.PASOS.size, color, 5.dp)
        if (obra.s("idea").isNotBlank()) Text("«${obra.s("idea")}»", color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 24.sp, lineHeight = 32.sp, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
        Spacer(Modifier.height(14.dp))
        GuiaNovela.PASOS.forEach { p ->
            val s = est[p.id - 1]
            val abre = abierto == p.id
            fun marca(x: Int) { abierto = if (p.id < GuiaNovela.PASOS.size) p.id + 1 else 0; e.edita(pid) { put("g${p.id}", x) } }
            Filete(1.dp, Line)
            Row(Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand).clickable { abierto = if (abre) 0 else p.id }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val hecho = s.estado == "hecho"
                Box(Modifier.size(32.dp).clip(CircleShape).background(if (hecho) Charcoal else Color.Transparent).border(1.5.dp, if (p.id == sig) Acento else if (hecho) Charcoal else Muted, CircleShape), contentAlignment = Alignment.Center) {
                    Text(when (s.estado) { "hecho" -> "✓"; "saltado" -> "–"; "curso" -> "…"; else -> p.id.toString() }, color = if (hecho) Paper else if (p.id == sig) Acento else Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
                Text(p.titulo, Modifier.weight(1f), color = if (hecho) Muted else Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                Text(when { s.estado == "hecho" -> "HECHO"; s.estado == "saltado" -> "SALTADO"; s.estado == "curso" -> "REPOSO: ${s.dias} ${if (s.dias == 1) "DÍA" else "DÍAS"}"; p.id == sig -> "TOCA AHORA"; else -> "" },
                    color = if (p.id == sig || s.estado == "curso") Acento else Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            }
            if (abre) Column(Modifier.fillMaxWidth().padding(start = 46.dp, bottom = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.que, color = Charcoal, fontFamily = Grotesca, fontSize = 18.sp, lineHeight = 26.sp)
                p.como.forEach { Row { Text("•", Modifier.width(18.dp), color = Acento, fontFamily = Grotesca, fontSize = 16.sp); Text(it, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp) } }
                if (p.id == 6) {
                    val meta = obra.i("meta")
                    if (meta > 0) Progreso(d.palabras.toFloat() / meta, color, 4.dp, Modifier.padding(top = 4.dp))
                    Text("${Formato.cifra(d.palabras)}${if (meta > 0) " de ${Formato.cifra(meta)}" else ""} palabras", color = Muted, fontFamily = Body, fontSize = 12.sp)
                }
                if (p.id == 5) Text("Has escrito en ${d.dias} ${if (d.dias == 1) "día" else "días"} distintos (con 3 se da por hecho).", color = Muted, fontFamily = Body, fontSize = 12.sp)
                if (s.estado == "hecho") Text(p.hecho, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                if (s.estado == "curso") Text("Llevas ${s.dias} ${if (s.dias == 1) "día" else "días"} sin abrirlo (unas semanas suele ir bien).", color = Muted, fontFamily = Body, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (s.estado != "curso") BotonAcento(p.accion, alto = 40.dp) { hacer(p.id) }
                    if (s.estado == "curso") { BotonAcento("Ya he descansado", alto = 40.dp) { marca(1) }; BotonSuave("Empezar de nuevo") { e.edita(pid) { put("g7t", System.currentTimeMillis()) } } }
                    if (s.estado == "pendiente") BotonSuave(if (p.auto) "Marcar como hecho" else "Ya lo tengo") { marca(1) }
                    if (s.estado == "pendiente") EnlaceRevista("Saltar") { marca(2) }
                    if (s.aMano) EnlaceRevista("Quitar la marca") { e.edita(pid) { put("g${p.id}", 0) } }
                }
            }
        }
        Filete(1.dp, Line)
        Text(GuiaNovela.FUENTES, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 18.dp))
    }
}
