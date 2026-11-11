package com.gonzalo.familia

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Al poner un widget de cualquiera de las seis apps (o al cambiarlo: mantenerlo pulsado → Ajustes del widget): cómo lleva el acento.
 * Enseña el widget de verdad con cada estilo, de día o de noche. Cada app la hereda (ConfigWidgetActivity) y pone sus colores y su dibujo.
 */
abstract class EstiloWidgetActivity : ComponentActivity() {
    protected var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        private set

    /** El nombre de la app («Bastet»). */
    abstract val nombreApp: String
    /** Papel, tinta y acento de la app (de día), para la pantalla. */
    abstract val papelApp: Int
    abstract val tintaApp: Int
    abstract val acentoApp: Int

    /** Lo que haga falta antes de dibujar (cargar el tema, la biblioteca…). */
    open fun preparar() {}
    /** El widget con cada estilo; null si no se puede dibujar (entonces se elige sin ver). */
    abstract suspend fun previas(id: Int): PreviasWidget?
    /** Repinta el widget ya con su estilo. */
    abstract fun repintar(id: Int)
    /** Tras elegir el estilo, si el widget pide algo más (qué juego abre): lo enseña y devuelve true; luego hay que llamar a [listo]. */
    open fun despues(id: Int): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        preparar()
        val id = widgetId
        setContent {
            ElegirEstiloWidget(nombreApp, papelApp, tintaApp, acentoApp, EstilosWidget.de(this, id), { previas(id) }) { e ->
                EstilosWidget.pon(this, id, e)
                if (!despues(id)) listo()
            }
        }
    }

    /** Termina: repinta el widget y le dice al escritorio que ya está. */
    protected fun listo() {
        repintar(widgetId)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@Composable
fun ElegirEstiloWidget(
    app: String, papelApp: Int, tintaApp: Int, acentoApp: Int, actual: EstiloWidget,
    cargar: suspend () -> PreviasWidget?, alElegir: (EstiloWidget) -> Unit
) {
    val oscuroSistema = isSystemInDarkTheme()
    var noche by remember { mutableStateOf(oscuroSistema) }
    var previas by remember { mutableStateOf<PreviasWidget?>(null) }
    var cargado by remember { mutableStateOf(false) }
    val dibujos = remember { mutableStateMapOf<String, Bitmap>() }
    LaunchedEffect(Unit) { previas = withContext(Dispatchers.Default) { runCatching { cargar() }.getOrNull() }; cargado = true }
    val p = previas
    LaunchedEffect(p, noche) {
        if (p == null) return@LaunchedEffect
        p.estilos.forEach { e ->
            val k = "${e.name}|$noche"
            if (k !in dibujos) withContext(Dispatchers.Default) { runCatching { p.dibujo(e, noche) }.getOrNull() }?.let { dibujos[k] = it }
        }
    }
    val fondo = Color(if (oscuroSistema) tintaApp else papelApp)
    val texto = Color(if (oscuroSistema) papelApp else tintaApp)
    val gris = texto.copy(alpha = 0.62f)
    val acento = Color(EstilosWidget.bloqueVisible(acentoApp, if (oscuroSistema) tintaApp else papelApp, if (oscuroSistema) papelApp else tintaApp, 4.5))
    // Los estilos que en este widget no cambian nada (sobre una foto a sangre, la cabecera y el papel teñido no se ven) no se ofrecen
    val base = dibujos["${EstiloWidget.COMO_AHORA.name}|$noche"]
    val estilos = (p?.estilos ?: if (cargado) EstiloWidget.entries else emptyList())
        .filter { e -> e == EstiloWidget.COMO_AHORA || e == actual || base == null || dibujos["${e.name}|$noche"]?.sameAs(base) != true }
    Column(Modifier.fillMaxSize().background(fondo).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
            BasicText("WIDGET DE ${app.uppercase()}", style = TextStyle(color = acento, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp))
            BasicText("¿Cómo lo quieres?", style = TextStyle(color = texto, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp), modifier = Modifier.padding(top = 2.dp))
            BasicText("Lo puedes cambiar luego manteniendo pulsado el widget.", style = TextStyle(color = gris, fontSize = 13.sp), modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "De día", true to "De noche").forEach { (n, t) ->
                    val sel = n == noche
                    BasicText(t.uppercase(), style = TextStyle(color = if (sel) fondo else texto, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) texto else Color.Transparent)
                            .border(1.dp, texto, RoundedCornerShape(50)).clickable { noche = n }.padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
        }
        if (!cargado) BasicText("Preparando el widget…", style = TextStyle(color = gris, fontSize = 15.sp), modifier = Modifier.padding(24.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)) {
            items(estilos, key = { it.name }) { e ->
                val bm = dibujos["${e.name}|$noche"]
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { alElegir(e) }.padding(vertical = 12.dp)) {
                    if (p != null) {
                        // El widget, sobre un fondo de pantalla neutro (gris), como se verá en el escritorio
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF8A8478)).padding(12.dp), contentAlignment = Alignment.Center) {
                            val ratio = (p.ancho / p.alto).coerceIn(0.4f, 6f)
                            if (bm != null) Image(bm.asImageBitmap(), contentDescription = e.nombre, modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(ratio))
                            else Spacer(Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(ratio))
                        }
                    }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(if (p?.transparente == true && e == EstiloWidget.ACENTO) "Nombre en color" else e.nombre,
                            style = TextStyle(color = texto, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold), modifier = Modifier.weight(1f, fill = false))
                        if (e == actual) BasicText("AHORA", style = TextStyle(color = acento, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp), modifier = Modifier.padding(start = 10.dp))
                    }
                    BasicText(if (p?.transparente == true && e == EstiloWidget.ACENTO) "El nombre de la app, en el color del acento." else e.explica,
                        style = TextStyle(color = gris, fontSize = 13.sp), modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
