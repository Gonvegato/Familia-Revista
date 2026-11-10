@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.cobalto

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

// La guía en Android: las acciones de cada paso, la tarjeta del manuscrito y la pestaña Guía. Los pasos, sus textos y lo que se da por
// hecho están en EscribirGuia.kt (el mismo archivo que compila Cobalto para Linux y Mac).

// ---------------------------------------------------------------- Las acciones de cada paso

/**
 * Lo que hace el botón del paso [id]: unos abren un diálogo y otros hacen algo y ya (crear los tres actos, empezar el
 * reposo, abrir la última escena…). Se pone en la pantalla de la obra mientras [id] no sea null y se llama a [onCerrar] al acabar.
 */
@Composable
fun AccionGuia(id: Int, e: Escritos, obra: Doc, abrir: (String) -> Unit, irManuscrito: () -> Unit, exportar: () -> Unit, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val pid = obra.id
    when (id) {
        1 -> {
            var frase by remember { mutableStateOf(obra.s("idea")) }
            HojaEscribir("Tu historia en una frase", onCerrar) {
                Text("Quién quiere qué, y qué se lo impide. Corta y sin nombres propios si puedes; no tiene que ser perfecta.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                CampoEscribir(frase, { frase = it }, "La frase", lineas = 3)
                Text("Ejemplo: Una aprendiz de hechicera sube a la torre donde nadie vuelve para salvar a su hermana, y descubre que lo que la espera arriba es ella misma.", color = Muted, fontSize = 12.sp, fontStyle = FontStyle.Italic)
                BotonEscribir("Guardar") { if (frase.isNotBlank()) { e.edita(pid) { put("idea", frase.trim()) }; onCerrar() } }
            }
        }
        2 -> FichaDialogo(e, pid, null, "personaje", onCerrar, camposNuevos = listOf("Quiere", "Se lo impide", "Si fracasa", "Edad"))
        4 -> {
            var nota by remember { mutableStateOf<Doc?>(null) }
            LaunchedEffect(Unit) {
                nota = e.notas(pid).firstOrNull { it.s("titulo") == GuiaNovela.TITULO_PROMESAS } ?: e.nuevo(Escritos.T_NOTA, pid) {
                    put("tipo", "idea"); put("titulo", GuiaNovela.TITULO_PROMESAS); put("color", COLORES_ESCRITO[2])
                    put("texto", GuiaNovela.PLANTILLA_PROMESAS)
                }
            }
            nota?.let { NotaDialogo(e, pid, e.get(it.id), onCerrar = onCerrar) }
        }
        3 -> {
            var nota by remember { mutableStateOf<Doc?>(null) }
            LaunchedEffect(Unit) {
                nota = e.notas(pid).firstOrNull { it.s("titulo") == GuiaNovela.TITULO_ESQUEMA } ?: e.nuevo(Escritos.T_NOTA, pid) {
                    put("tipo", "idea"); put("titulo", GuiaNovela.TITULO_ESQUEMA); put("color", COLORES_ESCRITO[1]); put("texto", GuiaNovela.PLANTILLA_ESQUEMA)
                }
            }
            nota?.let { NotaDialogo(e, pid, e.get(it.id), onCerrar = onCerrar) }
        }
        5 -> {
            var dia by remember { mutableStateOf(obra.i("diaria").takeIf { it > 0 }?.toString() ?: "300") }
            HojaEscribir("Tu meta de cada día", onCerrar) {
                Text("Elige una cantidad que te parezca casi demasiado fácil. Mejor cumplirla todos los días que abandonar una grande.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                Row(Modifier.horizontalScrollSafe(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("150", "300", "500", "1000").forEach { v -> SoftChip(v, dia == v) { dia = v } } }
                CampoEscribir(dia, { dia = it.filter(Char::isDigit).take(6) }, "Palabras al día", numero = true)
                Text("Para empezar, unas 300 (una sugerencia de Thot). Siempre puedes subirla.", color = Muted, fontSize = 12.sp)
                BotonEscribir("Guardar") { e.edita(pid) { put("diaria", dia.toIntOrNull() ?: 0) }; onCerrar() }
            }
        }
        else -> LaunchedEffect(id) {
            when (id) {
                6 -> { val s = e.escenasDe(pid).maxByOrNull { it.u }; if (s != null) abrir(s.id) else irManuscrito() }
                7 -> e.edita(pid) { put("g7t", System.currentTimeMillis()) }
                8, 9 -> irManuscrito()
                10, 11 -> exportar()
            }
            onCerrar()
        }
    }
}

// ---------------------------------------------------------------- La tarjeta del manuscrito y la pestaña Guía

/** El paso que toca ahora, arriba del manuscrito (solo con la guía activada). */
@Composable
fun TarjetaGuia(e: Escritos, obra: Doc, ver: Int, hacer: (Int) -> Unit, verGuia: () -> Unit) {
    if (!GuiaNovela.activa(obra)) return
    val est = remember(ver, obra.u) { GuiaNovela.estados(datosGuia(e, obra.id)) }
    val id = GuiaNovela.siguiente(est) ?: return
    val p = GuiaNovela.PASOS.first { it.id == id }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shp(16.dp)).background(Crudo).border(1.dp, ColGeneral, shp(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("GUÍA · PASO $id DE ${GuiaNovela.PASOS.size}", color = ColGeneral, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        FitTitle(p.titulo, 22.sp, 25.sp, color = Charcoal, conEdicion = false)
        Text(p.que, fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            BotonEscribir(p.accion) { hacer(id) }
            BotonEscribir("Ver la guía", suave = true) { verGuia() }
        }
    }
}

/** La pestaña Guía: los 11 pasos, con el que toca abierto. */
@Composable
fun GuiaObra(e: Escritos, obra: Doc, ver: Int, hacer: (Int) -> Unit) {
    val pid = obra.id
    val d = remember(ver, obra.u) { datosGuia(e, pid) }
    val est = remember(d) { GuiaNovela.estados(d) }
    val sig = GuiaNovela.siguiente(est)
    val hechos = GuiaNovela.hechos(est)
    var abierto by rememberSaveable(pid) { mutableIntStateOf(sig ?: 0) }
    // El paso abierto sigue al que toca: al acabar uno, se abre el siguiente
    LaunchedEffect(sig) { abierto = sig ?: 0 }
    val color = colorEscrito(obra.s("color"))
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = dockSpace(40.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item("cab") {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$hechos de ${GuiaNovela.PASOS.size} pasos", color = Muted, fontSize = 13.sp)
                Bar(hechos.toFloat() / GuiaNovela.PASOS.size, color, height = 4.dp)
                if (obra.s("idea").isNotBlank()) Text(obra.s("idea"), fontFamily = Body, fontSize = 18.sp, lineHeight = 25.sp, color = Charcoal,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (GuiaNovela.activa(obra)) "La guía está activada: el paso que toca sale arriba del manuscrito." else "La guía está desactivada: no sale nada en el manuscrito.", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    SoftChip(if (GuiaNovela.activa(obra)) "Desactivar" else "Activar", false) { e.edita(pid) { put("gOn", if (GuiaNovela.activa(obra)) 0 else 1) } }
                }
            }
        }
        items(GuiaNovela.PASOS, key = { it.id }) { p ->
            val s = est[p.id - 1]
            val abre = abierto == p.id
            fun marca(v: Int) { abierto = if (p.id < GuiaNovela.PASOS.size) p.id + 1 else 0; e.edita(pid) { put("g${p.id}", v) } }
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Row(Modifier.fillMaxWidth().press(haptic = false) { abierto = if (abre) 0 else p.id }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val hecho = s.estado == "hecho"
                    Box(Modifier.size(28.dp).clip(CircleShape).background(if (hecho) Charcoal else Color.Transparent).border(1.5.dp, if (p.id == sig) ColGeneral else if (hecho) Charcoal else Muted, CircleShape), contentAlignment = Alignment.Center) {
                        Text(when (s.estado) { "hecho" -> "✓"; "saltado" -> "–"; "curso" -> "…"; else -> p.id.toString() }, color = if (hecho) Paper else if (p.id == sig) ColGeneral else Muted, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                    Text(p.titulo, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (hecho) Muted else Charcoal, modifier = Modifier.weight(1f))
                    Text(when { s.estado == "hecho" -> "HECHO"; s.estado == "saltado" -> "SALTADO"; s.estado == "curso" -> "REPOSO: ${s.dias} ${if (s.dias == 1) "DÍA" else "DÍAS"}"; p.id == sig -> "TOCA AHORA"; else -> "" },
                        color = if (p.id == sig || s.estado == "curso") ColGeneral else Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                }
                if (abre) Column(Modifier.fillMaxWidth().padding(start = 40.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.que, color = Charcoal, fontSize = 15.sp, lineHeight = 21.sp)
                    p.como.forEach { Text("•  $it", color = Charcoal, fontSize = 14.sp, lineHeight = 20.sp) }
                    if (p.id == 6) {
                        val meta = obra.i("meta")
                        if (meta > 0) Bar((d.palabras.toFloat() / meta).coerceIn(0f, 1f), color, height = 4.dp)
                        Text("${cifraE(d.palabras)}${if (meta > 0) " de ${cifraE(meta)}" else ""} palabras", color = Muted, fontSize = 12.sp)
                    }
                    if (p.id == 5) Text("Has escrito en ${d.dias} ${if (d.dias == 1) "día" else "días"} distintos (con 3 se da por hecho).", color = Muted, fontSize = 12.sp)
                    if (s.estado == "hecho") Text(p.hecho, color = ColGeneral, fontSize = 14.sp, fontStyle = FontStyle.Italic)
                    if (s.estado == "curso") Text("Llevas ${s.dias} ${if (s.dias == 1) "día" else "días"} sin abrirlo (unas semanas suele ir bien).", color = Muted, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.estado != "curso") BotonEscribir(p.accion) { hacer(p.id) }
                        if (s.estado == "curso") { BotonEscribir("Ya he descansado") { marca(1) }; BotonEscribir("Empezar de nuevo", suave = true) { e.edita(pid) { put("g7t", System.currentTimeMillis()) } } }
                        if (s.estado == "pendiente") BotonEscribir(if (p.auto) "Marcar como hecho" else "Ya lo tengo", suave = true) { marca(1) }
                        if (s.estado == "pendiente") BotonEscribir("Saltar", suave = true) { marca(2) }
                        if (s.aMano) BotonEscribir("Quitar la marca", suave = true) { e.edita(pid) { put("g${p.id}", 0) } }
                    }
                }
            }
        }
        item("fuentes") { Text(GuiaNovela.FUENTES, color = Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 18.dp)) }
    }
}
