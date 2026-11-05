package com.gonzalo.cobalto

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Al poner el widget «Sigue escribiendo» en el escritorio: ¿qué obra abre? La que toque (la que estés escribiendo) o una fija que eliges aquí. */
class ConfigWidgetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(Activity.RESULT_CANCELED)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        iniciar(this)
        setContent { CobaltoTheme { ElegirObraWidget { obraId -> Widgets.ponFijo(this, id, obraId); Widgets.pedir(this); setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish() } } }
    }
}

@Composable
fun ElegirObraWidget(alElegir: (String?) -> Unit) {
    val ctx = LocalContext.current
    val e = remember { Escritos.get(ctx) }
    var busca by remember { mutableStateOf("") }
    val q = norm(busca)
    val lista = e.obras().filter { estadoDe(it) != EstadoObra.ARCHIVO }.sortedByDescending { e.ultimaEdicion(it.id) }.filter { q.isEmpty() || norm(it.s("titulo")).contains(q) || norm(it.s("autor")).contains(q) }
    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Antetitulo("Widget", Modifier.padding(top = 14.dp), color = Acento, conCuadro = false)
        FitTitle("¿Qué obra abre?", 36.sp, 38.sp, letterSpacing = (-1).sp, maxLines = 2)
        Filete(3.dp)
        Column(Modifier.fillMaxWidth().clickable { alElegir(null) }.padding(vertical = 12.dp)) {
            Text("La que toque", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Text("LA OBRA EN LA QUE ESTÉS ESCRIBIENDO", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Filete(2.dp)
        CampoRevista("Buscar", busca, "una obra fija") { busca = it }
        LazyColumn(Modifier.fillMaxSize()) {
            if (lista.isEmpty()) item { Text("Todavía no hay obras en Cobalto.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(top = 20.dp)) }
            items(lista, key = { it.id }) { o ->
                Row(Modifier.fillMaxWidth().clickable { alElegir(o.id) }.padding(vertical = 10.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(8.dp).fillMaxHeight().background(colorEscrito(o.s("color"))))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(o.s("titulo").ifBlank { "Sin título" }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 2)
                        Text(metaObra(e, o, conEstado = true).uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
                Filete(1.dp, Line)
            }
        }
    }
}
