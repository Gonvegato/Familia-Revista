package com.gonzalo.marfil

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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Al poner cualquier widget: primero su estilo (familia/EstiloWidgetActivity); luego, en un widget «Sigue leyendo» o «En portada» en el escritorio: ¿qué libro abre? El que toque (lo que estés leyendo, pasando de uno a
 * otro) o uno fijo que eliges aquí.
 */
class ConfigWidgetActivity : EstiloWidgetActivity() {
    override val nombreApp = "Seshat"
    override val papelApp = 0xFFF8F0D4.toInt()
    override val tintaApp = 0xFF000000.toInt()
    override val acentoApp = 0xFFB0791E.toInt()
    override fun preparar() = iniciar(this)
    override suspend fun previas(id: Int) = Widgets.previas(this, id)
    override fun repintar(id: Int) = Widgets.repintar(this, id)

    /** Después del estilo, los widgets de una ficha preguntan cuál abre. */
    override fun despues(id: Int): Boolean {
        if (Widgets.tipoDe(this, id) !in setOf(WTipo.LEYENDO, WTipo.PORTADA)) return false
        setContent { MarfilTheme { ElegirLibroWidget { libroId -> Widgets.ponFijo(this, id, libroId); listo() } } }
        return true
    }
}

@Composable
fun ElegirLibroWidget(alElegir: (String?) -> Unit) {
    var busca by remember { mutableStateOf("") }
    val q = norm(busca)
    val lista = Biblioteca.libros.toList().sortedWith(compareByDescending<Libro> { it.ultima }.thenByDescending { it.anadido }).filter { q.isEmpty() || norm(it.titulo).contains(q) || norm(it.autor).contains(q) }
    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Antetitulo("Widget", Modifier.padding(top = 14.dp), color = Acento, conCuadro = false)
        FitTitle("¿Qué libro abre?", 36.sp, 38.sp, letterSpacing = (-1).sp, maxLines = 2)
        Filete(3.dp)
        Column(Modifier.fillMaxWidth().clickable { alElegir(null) }.padding(vertical = 12.dp)) {
            Text("El que toque", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Text("LO QUE ESTÉS LEYENDO; TOCANDO LA ESQUINA PASA AL SIGUIENTE", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Filete(2.dp)
        CampoRevista("Buscar", busca, "un libro fijo") { busca = it }
        LazyColumn(Modifier.fillMaxSize()) {
            if (lista.isEmpty()) item { Text("Todavía no hay libros en Seshat.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(top = 20.dp)) }
            items(lista, key = { it.id }) { l ->
                Row(Modifier.fillMaxWidth().clickable { alElegir(l.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PortadaLibro(l, Modifier.width(40.dp).aspectRatio(0.68f), radio = 3, conTitulo = false)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(l.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 2)
                        Text(metaLibro(l, conEstado = true).uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        // Los terminados, como en las listas: «TERMINADO» en el acento
                        if (l.estado == Estado.LEIDO) Text("TERMINADO", color = Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }
                Filete(1.dp, Line)
            }
        }
    }
}
