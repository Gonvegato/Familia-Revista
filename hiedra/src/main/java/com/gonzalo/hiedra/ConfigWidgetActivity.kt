package com.gonzalo.hiedra

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gonzalo.familia.EstiloWidgetActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Al poner cualquier widget: primero su estilo (familia/EstiloWidgetActivity); luego, en un widget «Sigue jugando» o «En portada» en el escritorio: ¿qué juego abre? El que toque (lo último que hayas jugado, pasando de uno
 * a otro) o uno fijo que eliges aquí. Es lo que antes hacían los accesos directos, pero con el dibujo de la revista.
 */
class ConfigWidgetActivity : EstiloWidgetActivity() {
    override val nombreApp = "Bastet"
    override val papelApp = 0xFFF8F0D4.toInt()
    override val tintaApp = 0xFF000000.toInt()
    override val acentoApp = 0xFF7A8A3A.toInt()
    override fun preparar() = iniciar(this)
    override suspend fun previas(id: Int) = Widgets.previas(this, id)
    override fun repintar(id: Int) = Widgets.repintar(this, id)

    /** Después del estilo, los widgets de una ficha preguntan cuál abre. */
    override fun despues(id: Int): Boolean {
        if (Widgets.tipoDe(this, id) !in setOf(WTipo.JUGANDO, WTipo.PORTADA)) return false
        setContent { HiedraTheme { ElegirJuegoWidget { juegoId -> Widgets.ponFijo(this, id, juegoId); listo() } } }
        return true
    }
}

@Composable
fun ElegirJuegoWidget(alElegir: (String?) -> Unit) {
    var busca by remember { mutableStateOf("") }
    val q = Catalogo.norm(busca)
    val lista = recientes(Biblioteca.juegos.toList()).filter { q.isEmpty() || Catalogo.norm(it.titulo).contains(q) }
    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Antetitulo("Widget", Modifier.padding(top = 14.dp), color = Acento, conCuadro = false)
        FitTitle("¿Qué juego abre?", 36.sp, 38.sp, letterSpacing = (-1).sp, maxLines = 2)
        Filete(3.dp)
        Column(Modifier.fillMaxWidth().clickable { alElegir(null) }.padding(vertical = 12.dp)) {
            Text("El que toque", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Text("LO ÚLTIMO QUE HAYAS JUGADO; TOCANDO LA ESQUINA PASA AL SIGUIENTE", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Filete(2.dp)
        CampoRevista("Buscar", busca, "un juego fijo") { busca = it }
        LazyColumn(Modifier.fillMaxSize()) {
            if (lista.isEmpty()) item { Text("Todavía no hay juegos en Bastet.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(top = 20.dp)) }
            items(lista, key = { it.id }) { j ->
                Row(Modifier.fillMaxWidth().clickable { alElegir(j.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(40.dp).aspectRatio(PROPORCION_PORTADA).clip(esquina(3.dp))) { PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = 18) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(j.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 2)
                        Text(metaJuego(j, conEstado = true).uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
                Filete(1.dp, Line)
            }
        }
    }
}
