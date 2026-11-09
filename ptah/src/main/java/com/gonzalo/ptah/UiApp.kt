package com.gonzalo.ptah

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Las piezas de la app (minimal oscuro): tarjetas con una línea fina, números en píldora. Los colores son los de la app (fijos), no los
 * de los widgets.
 */

/** Lo que hay que mirar (un aviso, borrar). */
val Coral = Color(0xFFFF6B6B)

/** Una tarjeta: el fondo un pelo más claro y una línea fina. */
@Composable
fun Tarjeta(modifier: Modifier = Modifier, borde: Color? = null, relleno: Dp = 16.dp, alHacer: (() -> Unit)? = null, contenido: @Composable ColumnScope.() -> Unit) {
    val forma = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxWidth().clip(forma).background(Crudo).border(1.dp, borde ?: Line, forma)
            .let { if (alHacer != null) it.tocable(onClick = alHacer) else it }.padding(relleno),
        content = contenido
    )
}

/** Un número en una píldora. */
@Composable
fun Insignia(n: Int, modifier: Modifier = Modifier, color: Color = Acento) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 7.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
        Text(if (n > 99) "99+" else "$n", color = SobreAcento, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp)
    }
}
