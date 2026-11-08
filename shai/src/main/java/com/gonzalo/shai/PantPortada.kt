package com.gonzalo.shai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Una página de la revista: con scroll, márgenes y las barras del sistema. */
@Composable
fun PaginaRevista(pie: androidx.compose.ui.unit.Dp = 36.dp, contenido: @Composable ColumnScope.() -> Unit) {
    val mg = margen()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = mg)) {
        Column(Modifier.widthIn(max = 760.dp)) { contenido() }
        Spacer(Modifier.height(pie))
    }
}

/** El gasto del mes en la API: lo gastado, el tope y una barra. */
@Composable
fun BarraGasto(modifier: Modifier = Modifier) {
    Carreras.version
    val g = Gasto.mes(); val t = Gasto.tope()
    val f = (g / t).toFloat().coerceIn(0f, 1f)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            Etiqueta("Gasto de la IA este mes", Modifier.weight(1f))
            Text("${Formato.dolares(g)} de ${Formato.dolares(t)}", color = if (g >= t) Acento else Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).background(Line)) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(if (g >= t) Acento else Dorado))
        }
    }
}

@Composable
fun PantPortada(alAbrir: (String) -> Unit, alNueva: () -> Unit, alAjustes: (Int) -> Unit) {
    val ctx = LocalContext.current
    Carreras.version
    val carreras = Carreras.todas()
    PaginaRevista {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Antetitulo("Tu carrera en EA Sports FC", Modifier.weight(1f), color = Acento, conCuadro = false)
            EnlaceRevista("Ajustes") { alAjustes(0) }
        }
        Cabecera(120.sp, Modifier.padding(top = 2.dp))
        FileteCabecera()
        Text("La prensa, la red, los mensajes, las ofertas y la vida de tus carreras. El campo lo juegas tú; lo de fuera lo escribe Shai.",
            color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(14.dp))
        when {
            Prueba.activo -> AvisoPrueba(alAjustes)
            !Cuenta.vinculada -> Panel(alHacer = { alAjustes(1) }) {
                Antetitulo("Primero, tu cuenta")
                Cuerpo("Shai guarda tus carreras en tu cuenta de la familia (la de Jonsu y las demás): entra con tu usuario y tu contraseña.", Modifier.padding(top = 4.dp))
                MagLink("Entrar en tu cuenta", accent = true) { alAjustes(1) }
            }
            !ClaveApi.hay -> Panel(alHacer = { alAjustes(2) }) {
                Antetitulo("Falta tu clave de la API")
                Cuerpo("Lo de fuera del campo lo escribe Claude con los créditos de la API de tu plan. Pon tu clave en Ajustes → IA (va solo a tu cuenta, a un sitio que no ve nadie más).", Modifier.padding(top = 4.dp))
                MagLink("Poner la clave", accent = true) { alAjustes(2) }
            }
            else -> BarraGasto()
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 && carreras.isNotEmpty()) {
            var avisos by remember { mutableStateOf(ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
            val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { avisos = it }
            if (!avisos) MagLink("Avisarte de lo que pase entre partidos", "Dar permiso de avisos") { pedir.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        }
        Spacer(Modifier.height(18.dp))
        Antetitulo(if (carreras.isEmpty()) "Ninguna carrera todavía" else "Tus carreras")
        if (carreras.isEmpty()) {
            Text("Empieza una: tu jugador o tu míster, su club y su temporada. Después, cada partido que juegues en FC lo metes aquí y Shai monta el resto.",
                color = Muted, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 12.dp))
        }
        carreras.forEach { u ->
            val ps = Carreras.partidos(u.id).map { it.second }
            val pend = Carreras.entradasDe(u.id).count { e -> e.decision?.abierta == true || e.ofertas.any { it.estado == Oferta.ABIERTA } }
            Spacer(Modifier.height(10.dp))
            Tarjeta(alHacer = { alAbrir(u.id) }) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Avatar(u.nombre, 48.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp)
                        Text(listOfNotNull(u.modo.nombre, u.club, u.temporada.ifBlank { null }).joinToString(" · "), color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    if (pend > 0) { Spacer(Modifier.width(8.dp)); Insignia(pend) }
                }
                Text(listOf("${ps.size} partido${if (ps.size == 1) "" else "s"}", "patrimonio ${Formato.eurosCorto(u.patrimonio)}").joinToString(" · ") +
                    (if (pend > 0) " · $pend por decidir" else ""), color = Charcoal, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                val r = Estadisticas.racha(ps)
                if (r.isNotEmpty()) Row(Modifier.padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Últimos partidos: ", color = Muted, fontFamily = Body, fontSize = 12.sp)
                    r.forEach { c ->
                        Text("$c ", color = when (c) { 'V' -> Acento; 'D' -> Coral; else -> Muted }, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        BotonAcento("Empezar una carrera", Icons.Filled.Add, Modifier.fillMaxWidth()) { alNueva() }
    }
}

/** El aviso del modo prueba: que se vea siempre que lo de fuera del campo no lo escribe Claude. */
@Composable
fun AvisoPrueba(alAjustes: (Int) -> Unit, corto: Boolean = false) {
    Panel(Modifier.padding(top = if (corto) 8.dp else 0.dp), alHacer = { alAjustes(2) }) {
        Antetitulo("Modo prueba · sin IA, sin gasto", color = Acento)
        if (!corto) Cuerpo("Lo de fuera del campo lo escriben unas plantillas de ejemplo (no Claude) y los textos se repiten: es para ver cómo funciona todo. Estas carreras van aparte, solo en este móvil. Se quita en Ajustes → IA.", Modifier.padding(top = 4.dp))
    }
}
