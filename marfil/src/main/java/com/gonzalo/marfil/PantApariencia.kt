package com.gonzalo.marfil

import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
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
 * Ajustes → Apariencia, como la de Atrio: Tema, Fondo, Tamaño del texto, Acento y «A tu manera» (colores y letras a mano), más
 * «Colores de Atrio» (seguir en directo los de la app instalada) y «Copiar de Atrio» (lo que Atrio tiene guardado en tu cuenta).
 */

@Composable
private fun Seccion(titulo: String) {
    Spacer(Modifier.height(18.dp)); Filete(1.dp, Line); Spacer(Modifier.height(12.dp))
    Antetitulo(titulo, Modifier.padding(bottom = 4.dp))
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SeccionApariencia() {
    val ctx = LocalContext.current
    fun guarda() = Skin.guardar(ctx)
    // Dirección D: una sola cara. Se elige el claro u oscuro, el tamaño del texto y el color de acento.
    MagChoice(listOf("Como el móvil", "Claro", "Oscuro"), Modo.eleccion.coerceIn(0, 2), "Tema") { i -> Modo.eleccion = i; guarda() }

    Seccion("Tamaño del texto")
    val escalas = listOf(90 to "Pequeño", 100 to "Normal", 112 to "Grande", 125 to "Muy grande")
    MagChoice(escalas.map { it.second }, escalas.indexOfFirst { it.first == Skin.escala }.coerceAtLeast(1)) { i -> Skin.escala = escalas[i].first; guarda() }

    Seccion("Color de acento")
    Text("El color de los botones, los enlaces y las barras de avance. El primero es el de Seshat.", color = Muted, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp)
    Spacer(Modifier.height(12.dp))
    val noche = Skin.tones.darkBg
    val deCasa = Color((if (noche) Skin.palette.dark else Skin.palette.light).general or 0xFF000000)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (listOf<Accent?>(null) + ACCENT_COLORS.filter { it.id != "tinta" }).forEach { a ->
            val on = Skin.acentoPropio == 0L && Skin.accentId == (a?.id ?: "paleta")
            val c = if (a == null) deCasa else Color((if (noche) a.dark else a.light) or 0xFF000000)
            Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c).then(if (on) Modifier.border(3.dp, Charcoal, androidx.compose.foundation.shape.CircleShape) else Modifier)
                .clickable { Skin.accentId = a?.id ?: "paleta"; Skin.acentoPropio = 0L; guarda() }.semantics { contentDescription = a?.name ?: "El de Seshat" }, contentAlignment = Alignment.Center) {
                if (on) Text("✓", color = if (c.luminance2() > 0.5f) Color(0xFF14151A) else Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
    SelectorColor("Otro color", Skin.acentoPropio, Acento, { Skin.acentoPropio = it }, { guarda() })
}
