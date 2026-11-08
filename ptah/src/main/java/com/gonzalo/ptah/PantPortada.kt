package com.gonzalo.ptah

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/*
 * La portada: la cabecera de la revista (PTAH, el número del día y la fecha), «Tus widgets» con la miniatura de cada diseño, «+ Nuevo» y,
 * para empezar, las plantillas. Al poner un widget en la pantalla de inicio es la misma, para elegir cuál va allí.
 */

@Composable
fun PantPortada(eligiendo: Boolean, alAbrir: (Diseno) -> Unit, alPlantilla: (Diseno) -> Unit, alNuevo: () -> Unit, alAjustes: () -> Unit) {
    val ctx = LocalContext.current
    val mg = margen()
    val disenos = Disenos.todos(ctx)
    val usos = remember(disenos) { Widgets.usos(ctx) }
    val cols = if (esAncha()) 3 else 2
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = mg)) {
        Spacer(Modifier.height(14.dp))
        Cabecera(104.sp)
        val hoy = LocalDate.now()
        FileteCabecera()
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Etiqueta("Nº ${hoy.dayOfYear}", color = Charcoal)
            Spacer(Modifier.width(12.dp))
            Text("${Formatos.dia(hoy.dayOfWeek)} ${hoy.dayOfMonth} de ${Formatos.mes(hoy.monthValue)}".uppercase(), color = Charcoal, fontFamily = Body, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
        }
        Filete(1.dp)
        Text(if (eligiendo) "¿Qué va en este widget? Un toque en uno de tus diseños y ya está." else "Tus widgets, a tu manera. Un toque y lo cambias.",
            color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 12.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            EnlaceRevista("+ Nuevo", acento = true) { alNuevo() }
            if (!eligiendo) EnlaceRevista("Ajustes") { alAjustes() }
        }

        // ---- Tus widgets
        Spacer(Modifier.height(10.dp)); Filete(3.dp); Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Antetitulo("Tus widgets", Modifier.weight(1f))
            if (disenos.isNotEmpty()) Text("${disenos.size}", color = Acento, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }
        if (disenos.isEmpty()) Nota("Aún no tienes ninguno. Empieza con una de las de abajo o con «+ Nuevo»: luego lo pones en tu pantalla de inicio como cualquier widget.", Modifier.padding(top = 8.dp))
        Rejilla(disenos, cols) { d ->
            val t = Tipos.de(d.tipo)
            val n = usos[d.id] ?: 0
            Tarjeta(d, t?.muestra ?: Tamanos.Celdas(2, 2), (t?.nombre ?: "De otra versión") + (if (n > 0) " · en $n widget${if (n > 1) "s" else ""}" else "")) { alAbrir(d) }
        }

        // ---- Para empezar
        Spacer(Modifier.height(18.dp)); Filete(3.dp); Spacer(Modifier.height(10.dp))
        Antetitulo("Para empezar")
        Text("Plantillas hechas con los colores de tu Apariencia (o con los suyos). Un toque y es tuya: la cambias como quieras.",
            color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        Rejilla(Plantillas.TODAS, cols) { (p, c) -> Tarjeta(p, c, Tipos.de(p.tipo)?.nombre.orEmpty()) { alPlantilla(p) } }

        Spacer(Modifier.height(26.dp)); Filete(3.dp); Spacer(Modifier.height(10.dp))
        Etiqueta("Ptah $VERSION")
        Text("Ptah es hermana de Jonsu, Hathor, Nut, Bastet, Seshat y Thot. ${OpenMeteo.LICENCIA}", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(30.dp))
    }
}

/** Unas tarjetas en [cols] columnas (todas a la vista: la página entera se desliza). */
@Composable
private fun <T> Rejilla(cosas: List<T>, cols: Int, tarjeta: @Composable (T) -> Unit) {
    cosas.chunked(cols).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            fila.forEach { Box(Modifier.weight(1f)) { tarjeta(it) } }
            repeat(cols - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Un diseño en la portada: su miniatura, su nombre (en dos líneas si hace falta) y de qué tipo es. */
@Composable
private fun Tarjeta(d: Diseno, c: Tamanos.Celdas, meta: String, alTocar: () -> Unit) {
    Column(Modifier.fillMaxWidth().tocable { alTocar() }) {
        Miniatura(d, c, Modo.oscuro, alto = 124f)
        Text(d.nombre.ifBlank { "Sin nombre" }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 20.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text("$meta · $c".uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, lineHeight = 12.sp, maxLines = 3)
    }
}

/** «+ Nuevo»: los tipos, por grupos, cada uno con su muestra y lo que hace. */
@Composable
fun PantNuevo(alVolver: () -> Unit, alElegir: (TipoWidget) -> Unit) {
    val mg = margen()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = mg)) {
        RevistaTopBar(alVolver, "Ptah")
        Antetitulo("Nuevo", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle("¿Qué widget?", 48.sp, 52.sp, letterSpacing = (-1.2).sp)
        Filete(3.dp)
        Tipos.GRUPOS.forEach { g ->
            Spacer(Modifier.height(14.dp))
            Antetitulo(g)
            Tipos.TODOS.filter { it.grupo == g }.forEach { t ->
                val muestra = remember(t) { Plantillas.TODAS.firstOrNull { it.first.tipo == t.id }?.first ?: t.nuevo() }
                Row(Modifier.fillMaxWidth().tocable { alElegir(t) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(132.dp)) { Miniatura(muestra, t.muestra, Modo.oscuro, alto = 92f) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 24.sp)
                        Text(t.descripcion, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Filete(1.dp, Line)
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}
