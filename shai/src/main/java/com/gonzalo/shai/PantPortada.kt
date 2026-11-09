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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/** Una página: con scroll, márgenes y las barras del sistema. */
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
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(if (g >= t) Coral else Acento))
        }
    }
}

/** La barra de arriba de las pantallas principales: a la izquierda lo que se pase (el avatar), el logotipo en medio y los ajustes a la derecha. */
@Composable
fun BarraArriba(alAjustes: () -> Unit, izquierda: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().height(54.dp).padding(horizontal = 12.dp)) {
        Box(Modifier.align(androidx.compose.ui.Alignment.CenterStart)) { izquierda() }
        Cabecera(24.sp, Modifier.align(androidx.compose.ui.Alignment.Center))
        BotonIcono(Icons.Outlined.Settings, "Ajustes", Modifier.align(androidx.compose.ui.Alignment.CenterEnd), tam = 44.dp) { alAjustes() }
    }
    Filete(1.dp)
}

@Composable
fun PantPortada(alAbrir: (String) -> Unit, alNueva: () -> Unit, alAjustes: (Int) -> Unit) {
    val ctx = LocalContext.current
    Carreras.version
    val carreras = Carreras.todas()
    val mg = margen()
    Box(Modifier.fillMaxSize().background(Paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            BarraArriba({ alAjustes(0) })
            Column(Modifier.padding(horizontal = mg).widthIn(max = 760.dp)) {
                Spacer(Modifier.height(14.dp))
                if (Migracion.aviso.isNotBlank()) {
                    Tarjeta(borde = Acento) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                            Text(Migracion.aviso, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                            Text("Vale", color = Acento, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp).tocable { Migracion.olvidaAviso(ctx) })
                        }
                        Text("Desde ahora todo se guarda solo en este móvil.", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                when {
                    Prueba.activo -> AvisoPrueba(alAjustes)
                    !ClaveApi.hay -> Tarjeta(alHacer = { alAjustes(1) }) {
                        TituloTarjeta("Falta tu clave de la API")
                        TextoTarjeta("Lo de fuera del campo lo escribe Claude con los créditos de la API de tu plan. Ponla en Ajustes → IA (se guarda cifrada en este móvil).", Modifier.padding(top = 4.dp))
                        Text("Poner la clave", color = Acento, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    else -> Tarjeta(alHacer = { alAjustes(1) }) { BarraGasto() }
                }
                if (android.os.Build.VERSION.SDK_INT >= 33 && carreras.isNotEmpty()) {
                    var avisos by remember { mutableStateOf(ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
                    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { avisos = it }
                    if (!avisos) MagLink("Avisarte de lo que pase entre partidos", "Dar permiso de avisos") { pedir.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                }
                Spacer(Modifier.height(18.dp))
                Rotulo(if (carreras.isEmpty()) "Ninguna carrera todavía" else "Tus carreras")
                if (carreras.isEmpty()) {
                    Text("Empieza una con el «+»: tu jugador o tu míster, su club y su temporada. Después, cada partido que juegues en FC lo metes aquí y FcFeed monta el resto: la red, los mensajes, la prensa, las ofertas.",
                        color = Muted, fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
            carreras.forEach { u ->
                val ps = Carreras.partidos(u.id).map { it.second }
                val pend = Carreras.entradasDe(u.id).count { e -> e.decision?.abierta == true || e.ofertas.any { it.estado == Oferta.ABIERTA } }
                Row(Modifier.fillMaxWidth().tocable { alAbrir(u.id) }.padding(horizontal = mg, vertical = 14.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                    Avatar(u.nombre, 52.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(u.nombre, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f, fill = false))
                            if (pend > 0) { Spacer(Modifier.width(8.dp)); Insignia(pend) }
                        }
                        Text(listOfNotNull(u.modo.nombre, u.club, u.temporada.ifBlank { null }).joinToString(" · "), color = Muted, fontFamily = Body, fontSize = 14.sp, lineHeight = 19.sp)
                        Text(listOf("${ps.size} partido${if (ps.size == 1) "" else "s"}", "${Alcance.texto(Alcance.seguidores(u))} seguidores", "patrimonio ${Formato.eurosCorto(u.patrimonio)}").joinToString(" · ") +
                            (if (pend > 0) " · $pend por decidir" else ""), color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
                        val r = Estadisticas.racha(ps)
                        if (r.isNotEmpty()) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            r.forEach { c ->
                                Box(Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(when (c) { 'V' -> Color(0xFF00BA7C); 'D' -> Coral; else -> Muted }), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    Text("$c", color = Color.White, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                Filete(1.dp)
            }
            Spacer(Modifier.height(100.dp))
        }
        BotonFlotante("Empezar una carrera", Modifier.align(androidx.compose.ui.Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 22.dp)) { alNueva() }
    }
}

/** El aviso del modo prueba: que se vea siempre que lo de fuera del campo no lo escribe Claude. */
@Composable
fun AvisoPrueba(alAjustes: (Int) -> Unit, corto: Boolean = false) {
    Tarjeta(Modifier.padding(top = if (corto) 8.dp else 0.dp), borde = Dorado, alHacer = { alAjustes(1) }) {
        Text("Modo prueba · sin IA, sin gasto", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (!corto) Cuerpo("Lo de fuera del campo lo escriben unas plantillas de ejemplo (no Claude): es para ver cómo funciona todo. Estas carreras van aparte de las de verdad. Se quita en Ajustes → IA.", Modifier.padding(top = 4.dp))
    }
}
