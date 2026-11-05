package com.gonzalo.azabache.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import java.time.LocalDate

/** Las cifras de un año: lo terminado de cada categoría, tu nota media, lo mejor, y las horas de cada mes. */
@Composable
fun PantCifras(nav: Nav) {
    LaFamilia.cambios
    val kinds = Cuenta.kinds()
    val todas = kinds.flatMap { k -> Cuenta.fichas(k.id).map { k to it } }
    val anos = (todas.filter { it.second.valorada && it.second.terminado > 0 }.map { LocalDate.ofEpochDay(it.second.terminado).year } + LocalDate.now().year).distinct().sortedDescending()
    var ano by remember { mutableStateOf(LocalDate.now().year) }
    val delAno = todas.filter { it.second.valorada && it.second.terminado > 0 && LocalDate.ofEpochDay(it.second.terminado).year == ano }
    val tiempo = Cuenta.tiempoPorDia().filterKeys { it.startsWith("$ano-") }
    val refsLibros = Cuenta.fichas(BOOKS.id).mapNotNull { it.ref }.toSet()
    Pagina {
        Cabecera(84.sp, texto = "CIFRAS")
        FileteCabecera()
        Spacer(Modifier.height(12.dp))
        FiltrosRevista(anos.take(8).map { it.toString() }, anos.indexOf(ano).coerceAtLeast(0)) { ano = anos[it] }
        Spacer(Modifier.height(16.dp))
        val notas = delAno.filter { !it.second.sinValorar }.map { it.second.general }
        Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
            Cifra(delAno.size.toString(), "terminados", acento = true)
            Cifra(notas.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toString() } ?: "—", "nota media")
            val jugado = tiempo.values.sumOf { m -> m.filterKeys { it !in refsLibros }.values.sum() }
            val leido = tiempo.values.sumOf { m -> m.filterKeys { it in refsLibros }.values.sum() }
            Cifra(duracion(jugado / 60_000L), "jugando")
            Cifra(duracion(leido / 60_000L), "leyendo")
        }
        kinds.forEach { k ->
            val l = delAno.filter { it.first.id == k.id }.map { it.second }
            if (l.isEmpty()) return@forEach
            Seccion("${k.title} · ${l.size} ${k.doneWord.lowercase()}")
            val mejores = l.filter { !it.sinValorar }.sortedByDescending { it.general }.take(6)
            if (mejores.isNotEmpty()) FilaDeTarjetas(mejores.map { k to it }, nav, Pantalla.Cifras)
            else Cuerpo("Sin valorar todavía.", tam = 14)
        }
        // Las horas de cada mes (juegos y libros), en barras
        Seccion("Horas de cada mes")
        val porMes = (1..12).map { m -> tiempo.filterKeys { it.substring(5, 7).toInt() == m }.values.sumOf { it.values.sum() } / 3_600_000.0 }
        val max = porMes.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            val meses = listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC")
            porMes.forEachIndexed { i, h ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (h > 0) Text(if (h >= 10) "${h.toInt()}" else String.format("%.1f", h), color = Muted, fontFamily = Body, fontSize = 10.sp)
                    Box(Modifier.fillMaxWidth().fillMaxHeight((h / max).toFloat().coerceIn(0.01f, 0.85f)).background(if (h > 0) Acento else Line))
                    Spacer(Modifier.height(4.dp))
                    Text(meses[i], color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}
