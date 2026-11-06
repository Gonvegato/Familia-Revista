package com.gonzalo.cobalto.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.cobalto.EstadoObra
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.Formato
import com.gonzalo.cobalto.metaObra
import com.gonzalo.cobalto.obrasDe
import com.gonzalo.cobalto.resumen
import com.gonzalo.cobalto.ultimaEdicion
import com.gonzalo.familia.pc.revista.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale.forLanguageTag("es-ES")

/** La portada: lo de hoy (palabras, racha, lo que te propones), los últimos días, lo que estás escribiendo y seguir donde lo dejaste. */
@Composable
fun PantPortada(nav: Nav) {
    val e = Cuaderno.escritos
    e.version
    var nueva by remember { mutableStateOf(false) }
    val r = e.resumen(21)
    val ultima = e.ultimaEscena()
    Pagina {
        Text(("Número de " + LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES))).uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { Cabecera(96.sp, texto = "COBALTO") }
            BotonAcento("+ Nueva obra", alto = 44.dp, modifier = Modifier.width(170.dp)) { nueva = true }
        }
        FileteCabecera()
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
            Cifra(Formato.cifra(r.hoy), "palabras hoy", acento = true)
            Cifra(r.racha.toString(), if (r.racha == 1) "día seguido" else "días seguidos")
            if (r.diaria > 0) Cifra("${(r.hoy * 100 / r.diaria).coerceAtMost(999)} %", "de tu meta de ${Formato.cifra(r.diaria)}")
            Cifra(Formato.cifra(r.total), "palabras en marcha")
        }
        Spacer(Modifier.height(18.dp))
        Barras(r.dias, r.diaria)
        if (ultima != null) {
            val obra = e.get(ultima.pid)
            Spacer(Modifier.height(26.dp)); Filete(2.dp)
            Column(Modifier.fillMaxWidth().clickable { nav.ir(Pantalla.Obra(ultima.pid, ultima.id)) }.padding(vertical = 14.dp)) {
                Antetitulo("Sigue escribiendo")
                Spacer(Modifier.height(6.dp))
                Text(ultima.s("titulo").ifBlank { "Escena sin título" }, color = Charcoal, fontFamily = Display, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                Text("${obra?.s("titulo").orEmpty()} · ${e.get(ultima.s("cid"))?.s("titulo").orEmpty()}", color = Muted, fontFamily = Body, fontSize = 13.sp)
                val final = ultima.s("texto").trim().split('\n').lastOrNull { it.isNotBlank() && it.trim() != "***" }.orEmpty().replace("*", "")
                if (final.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text("«…${final.takeLast(220)}»", color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 27.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    EnlaceRevista("Seguir →", acento = true) { nav.ir(Pantalla.Obra(ultima.pid, ultima.id)) }
                    // (solo la página, sin nada alrededor: Esc para volver)
                    EnlaceRevista("Seguir en concentración") { nav.ir(Pantalla.Obra(ultima.pid, ultima.id)); Estudio.concentracion = true }
                }
            }
        }
        EstadoObra.values().forEach { est ->
            val l = e.obrasDe(est)
            if (l.isEmpty()) return@forEach
            Spacer(Modifier.height(22.dp)); Filete(2.dp)
            Antetitulo(est.plural, Modifier.padding(top = 10.dp, bottom = 6.dp))
            l.forEach { o ->
                Column(Modifier.fillMaxWidth().clickable { nav.ir(Pantalla.Obra(o.id)) }.padding(vertical = 10.dp)) {
                    Text(o.s("titulo").ifBlank { "Sin título" }, color = Charcoal, fontFamily = Display, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text(metaObra(e, o) + " · " + hace(e.ultimaEdicion(o.id)), color = Muted, fontFamily = Body, fontSize = 13.sp)
                    val meta = o.i("meta")
                    if (meta > 0) {
                        val f = (e.palabrasObra(o.id).toFloat() / meta).coerceIn(0f, 1f)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).background(Sand)) { Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Acento)) }
                    }
                }
                Filete(1.dp, Line)
            }
        }
        if (e.obras().isEmpty()) Vacio("Aún no hay ninguna obra", "Empieza una con «+ Nueva obra»: estará también en Cobalto del móvil.")
    }
    if (nueva) DialogoObra(null, nav) { nueva = false }
}

/** «hace 3 días». */
fun hace(ms: Long): String {
    if (ms <= 0) return ""
    val d = (System.currentTimeMillis() - ms) / 60_000L
    return when { d < 2 -> "ahora mismo"; d < 60 -> "hace $d min"; d < 24 * 60 -> "hace ${d / 60} h"; d < 48 * 60 -> "ayer"; else -> "hace ${d / (24 * 60)} días" }
}

/** Las palabras de cada día, en barras (la línea, tu meta diaria). */
@Composable
fun Barras(dias: List<Int>, meta: Int) {
    val max = (dias.maxOrNull() ?: 0).coerceAtLeast(meta).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        dias.forEachIndexed { i, n ->
            val hoy = i == dias.size - 1
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (n > 0) Text(Formato.cifra(n), color = Muted, fontFamily = Body, fontSize = 9.sp, maxLines = 1)
                Box(Modifier.fillMaxWidth().fillMaxHeight((n.toFloat() / max).coerceIn(0.015f, 0.8f)).background(if (n == 0) Line else if (hoy) Acento else Charcoal))
                Spacer(Modifier.height(3.dp))
                Text(LocalDate.ofEpochDay(Escritos.hoy() - (dias.size - 1 - i)).dayOfMonth.toString(), color = if (hoy) Acento else Muted, fontFamily = Body, fontSize = 9.sp)
            }
        }
    }
}
