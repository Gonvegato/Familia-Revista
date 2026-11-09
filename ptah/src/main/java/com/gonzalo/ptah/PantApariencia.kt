package com.gonzalo.ptah

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Ajustes → Estilo: el estilo de serie de tus widgets (claro u oscuro, fondo, acento y «A tu manera»: colores y letras a mano). Es lo que
 * lleva un diseño en lo que no tenga sus propios colores o su propia letra. La app tiene su propio aspecto, fijo.
 */

@Composable
private fun Seccion(titulo: String) {
    Spacer(Modifier.height(18.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo(titulo, Modifier.padding(bottom = 4.dp))
}

@Composable
fun SeccionApariencia() {
    val ctx = LocalContext.current
    fun guarda() = Skin.guardar(ctx)
    // Elegir algo aquí manda sobre los colores fijados de antes
    fun propio() { Skin.origenAtrio = false; Skin.invertido = false }

    Text("El estilo de serie de tus widgets: lo que lleva un diseño en lo que no tenga sus propios colores o su propia letra.", color = Muted, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp)
    if (Skin.origenAtrio && TemaAtrio.tonos != null) {
        Spacer(Modifier.height(12.dp))
        Tarjeta(borde = Acento) {
            Text("Colores fijados", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Tus widgets usan los colores que se fijaron con la versión de antes. Se quedan así hasta que elijas otros aquí abajo.", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
            MagLink("Dejar de usarlos", "Vuelven los de este estilo") { propio(); guarda() }
        }
    }
    Spacer(Modifier.height(16.dp))

    // ---- Tema
    Antetitulo("Claro u oscuro", Modifier.padding(bottom = 4.dp))
    listOf(1 to "Claro", 2 to "Oscuro", 0 to "Como el móvil").forEachIndexed { i, (modo, nombre) ->
        if (i > 0) Filete(1.dp, Line)
        val on = Modo.eleccion == modo
        Row(Modifier.fillMaxWidth().clickable { if (!on) { Modo.eleccion = modo; Skin.fondoId = ""; propio(); guarda() } }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(nombre, fontFamily = Display, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = 24.sp, color = if (on) Charcoal else Muted, modifier = Modifier.weight(1f))
            if (on) Text("✓", color = Acento, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }

    // ---- Fondo
    Seccion("Fondo")
    Text("Colores hondos y sobrios para tus widgets, en lugar de papel claro u oscuro. «Papel» vuelve a lo de arriba.", color = Muted, fontFamily = Body, fontSize = 13.sp)
    Spacer(Modifier.height(12.dp))
    listOf<Fondo?>(null).plus(FONDOS).chunked(4).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            fila.forEach { f ->
                val on = Skin.fondoId == (f?.id ?: "")
                Column(Modifier.weight(1f).clickable { if (!on) { Skin.fondoId = f?.id ?: ""; Skin.fondoPropio = 0L; propio(); guarda() } }, horizontalAlignment = Alignment.CenterHorizontally) {
                    val papel = f?.tones?.cPaper ?: Color(if (Modo.eleccion == 1) 0xFFF3EEE4 else 0xFF141210)
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(papel).border(if (on) 3.dp else 1.dp, if (on) Acento else Line), contentAlignment = Alignment.Center) {
                        Text("Aa", color = f?.tones?.cInk ?: Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        if (on) Text("✓", color = f?.tones?.cInk ?: Charcoal, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                    }
                    Text((f?.name ?: "Papel"), color = if (on) Charcoal else Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 11.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp), maxLines = 2)
                }
            }
            repeat(4 - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }

    // ---- Acento
    Seccion("Acento")
    Text("El color de los antetítulos, las cifras y los detalles de tus widgets.", color = Muted, fontFamily = Body, fontSize = 13.sp)
    Spacer(Modifier.height(12.dp))
    ACCENT_COLORS.chunked(4).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            fila.forEach { a ->
                val on = Skin.accentId == a.id || (a.id == "rojo" && Skin.accentId == "paleta")
                val c = Color((if (Skin.tones.darkBg) a.dark else a.light) or 0xFF000000)
                Column(Modifier.weight(1f).clickable { if (!on) { Skin.accentId = a.id; Skin.acentoPropio = 0L; propio(); guarda() } }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(c).then(if (on) Modifier.border(3.dp, Charcoal) else Modifier), contentAlignment = Alignment.Center) {
                        if (on) Text("✓", color = if (a.id == "tinta") Paper else Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    }
                    Text(a.name, color = if (on) Charcoal else Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp), maxLines = 2, textAlign = TextAlign.Center)
                }
            }
            repeat(4 - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }

    // ---- A tu manera
    Seccion("A tu manera")
    Text("El color que quieras para el fondo, el texto y el acento, y la letra de los títulos y del texto. Mandan sobre lo de arriba; «Quitar» vuelve a ello.", color = Muted, fontFamily = Body, fontSize = 13.sp)
    SelectorColor("Fondo", Skin.fondoPropio, Skin.tones.cPaper, { Skin.fondoPropio = it }, { guarda() })
    Filete(1.dp, Line)
    SelectorColor("Texto", Skin.tintaPropia, Skin.tones.cInk, { Skin.tintaPropia = it }, { guarda() })
    Filete(1.dp, Line)
    SelectorColor("Acento", Skin.acentoPropio, Color(Skin.tones.general), { Skin.acentoPropio = it }, { guarda() })
    Filete(1.dp, Line)
    val letras = remember { (DISPLAY_FONTS + BODY_FONTS).map { it.name }.distinct() }
    SelectorLetra("Letra de los títulos", Skin.display.takeIf { it != "Atrio" }.orEmpty(), letras) { Skin.display = it.ifBlank { "Atrio" }; guarda() }
    Filete(1.dp, Line)
    SelectorLetra("Letra del texto", Skin.body.takeIf { it != "Atrio" }.orEmpty(), remember { (BODY_FONTS + DISPLAY_FONTS).map { it.name }.distinct() }) { Skin.body = it.ifBlank { "Atrio" }; guarda() }

}

@Composable
fun SelectorLetra(nombre: String, valor: String, opciones: List<String>, onCambia: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { abierto = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Aa", fontFamily = valor.takeIf { it.isNotBlank() }?.let { Skin.family(it) } ?: Display, fontSize = 28.sp, color = Charcoal, modifier = Modifier.width(50.dp))
        Column(Modifier.weight(1f)) {
            Text(nombre, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Charcoal)
            Text(valor.ifEmpty { "La de Widgets" }, color = Muted, fontFamily = Body, fontSize = 12.sp)
        }
        if (valor.isNotEmpty()) EnlaceRevista("Quitar", Modifier.padding(end = 12.dp)) { onCambia("") }
        Text("▾", color = Muted, fontSize = 16.sp)
    }
    if (abierto) AlertDialog(
        onDismissRequest = { abierto = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text(nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item {
                    Text("La de Widgets", fontSize = 18.sp, color = if (valor.isEmpty()) Acento else Charcoal, modifier = Modifier.fillMaxWidth().clickable { onCambia(""); abierto = false }.padding(vertical = 10.dp))
                }
                items(opciones) { o ->
                    // (cada letra se baja de Google Fonts la primera vez: hasta entonces sale con la del sistema)
                    Text(o, fontFamily = Skin.family(o), fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (o == valor) Acento else Charcoal,
                        modifier = Modifier.fillMaxWidth().clickable { onCambia(o); abierto = false }.padding(vertical = 10.dp))
                }
            }
        },
        confirmButton = { TextButton({ abierto = false }) { Text("Cerrar", color = Acento, fontFamily = Body, fontWeight = FontWeight.Bold) } }
    )
}
