package com.gonzalo.shai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** ¿Pantalla ancha? (la tablet) */
@Composable
fun esAncha(): Boolean = LocalConfiguration.current.screenWidthDp >= 700

/** El margen de las páginas: en la tablet, más. */
@Composable
fun margen(): Dp = if (LocalConfiguration.current.screenWidthDp >= 900) 48.dp else if (esAncha()) 36.dp else 22.dp

/** Un texto de espera o de aviso, en cursiva (como en la revista). */
@Composable
fun Nota(t: String, modifier: Modifier = Modifier, color: Color = Muted) =
    Text(t, color = color, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp, modifier = modifier)
