package com.gonzalo.azabache.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import java.time.LocalDate
import java.time.YearMonth

/*
 * Las cifras de un año (o de siempre), como las del Azabache del móvil: de todo o de una categoría, el resumen, las notas, el reparto,
 * el tiempo jugando o leyendo (lo que cuentan Hiedra y Marfil), el ritmo de cada mes, quién y dónde, y lo mejor. A dos columnas.
 */

/** Las cifras de unas fichas en un año ([ano] = null: de siempre). Pura: se prueba sin pantalla. */
class CifrasAno(
    val terminados: Int, val enCurso: Int, val pendientes: Int, val abandonados: Int, val tasaAbandono: Int?,
    val media: Double?, val criterios: List<Double>, val mejor: Ficha?, val peor: Ficha?, val de90: Int, val conResena: Int, val favoritos: Int,
    val reparto: List<Int>, val porMes: List<Int>, val creadores: List<Pair<String, Int>>, val plataformas: List<Pair<String, Int>>
)

fun cifrasDe(fichas: List<Ficha>, ano: Int?, hoy: LocalDate = LocalDate.now()): CifrasAno {
    fun delAno(d: Long) = d > 0 && (ano == null || LocalDate.ofEpochDay(d).year == ano)
    val hechas = fichas.filter { it.valorada && (ano == null || delAno(it.terminado)) }
    val valoradas = hechas.filter { !it.sinValorar }
    val dejadas = fichas.filter { !it.valorada && it.estado == 2 && (ano == null || delAno(it.terminado)) }
    val reparto = IntArray(5).also { r -> valoradas.forEach { r[(it.general / 20).coerceIn(0, 4)]++ } }.toList()
    val meses = if (ano != null) (1..12).map { YearMonth.of(ano, it) } else (11 downTo 0).map { YearMonth.from(hoy).minusMonths(it.toLong()) }
    val porMes = meses.map { m -> fichas.count { it.valorada && it.terminado > 0 && YearMonth.from(LocalDate.ofEpochDay(it.terminado)) == m } }
    fun ranking(f: (Ficha) -> String) = hechas.map { f(it).trim() }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
    return CifrasAno(
        hechas.size, fichas.count { !it.valorada && it.estado == 1 }, fichas.count { !it.valorada && it.estado == 0 }, dejadas.size,
        (hechas.size + dejadas.size).takeIf { it > 0 }?.let { Math.round(dejadas.size * 100.0 / it).toInt() },
        valoradas.takeIf { it.isNotEmpty() }?.map { it.general }?.average(),
        (1..4).map { i -> valoradas.map { it.notas[i] }.takeIf { it.isNotEmpty() }?.average() ?: 0.0 },
        valoradas.maxByOrNull { it.general }, valoradas.minByOrNull { it.general }, valoradas.count { it.general >= 90 },
        hechas.count { it.resena.isNotBlank() }, hechas.count { it.fav }, reparto, porMes, ranking { it.creador }, ranking { it.plataforma }
    )
}

/** Lo jugado o leído en un año: total, días, la racha más larga y lo de cada mes (en ms), y lo que más tiempo te ha llevado. */
class TiempoAno(val totalMs: Long, val dias: Int, val racha: Int, val porMes: List<Long>, val top: List<Pair<String, Long>>)

fun tiempoDe(porDia: Map<String, Map<String, Long>>, refs: Set<String>, ano: Int?, hoy: LocalDate = LocalDate.now()): TiempoAno {
    val dias = porDia.mapNotNull { (d, m) -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { it to m.filterKeys { r -> r in refs } } }
        .filter { (d, _) -> ano == null || d.year == ano }
    val conTiempo = dias.filter { it.second.values.sum() >= 60_000L }.map { it.first.toEpochDay() }.sorted()
    var racha = 0; var cur = 0; var prev = Long.MIN_VALUE
    for (d in conTiempo) { cur = if (d == prev + 1) cur + 1 else 1; racha = maxOf(racha, cur); prev = d }
    val meses = if (ano != null) (1..12).map { YearMonth.of(ano, it) } else (11 downTo 0).map { YearMonth.from(hoy).minusMonths(it.toLong()) }
    val porMes = meses.map { m -> dias.filter { YearMonth.from(it.first) == m }.sumOf { it.second.values.sum() } }
    val porRef = HashMap<String, Long>().also { t -> dias.forEach { (_, m) -> m.forEach { (r, ms) -> t.merge(r, ms, Long::plus) } } }
    return TiempoAno(dias.sumOf { it.second.values.sum() }, conTiempo.size, racha, porMes, porRef.entries.sortedByDescending { it.value }.take(3).map { it.key to it.value })
}

private fun f1(x: Double) = String.format(java.util.Locale("es", "ES"), "%.1f", x)

@Composable
fun PantCifras(nav: Nav) {
    LaFamilia.cambios
    val kinds = Cuenta.kinds()
    val todas = kinds.flatMap { k -> Cuenta.fichas(k.id).map { k to it } }
    val esteAno = LocalDate.now().year
    val anos = (todas.filter { it.second.valorada && it.second.terminado > 0 }.map { LocalDate.ofEpochDay(it.second.terminado).year } + esteAno).distinct().sortedDescending()
    var ano by remember { mutableStateOf<Int?>(esteAno) }
    var cat by remember { mutableStateOf(-1) }
    val k = kinds.firstOrNull { it.id == cat }
    val fichas = if (k == null) todas.map { it.second } else Cuenta.fichas(k.id)
    val c = remember(fichas, ano) { cifrasDe(fichas, ano) }
    Pagina {
        TituloPagina("CIFRAS")
        FileteCabecera()
        Spacer(Modifier.height(10.dp))
        PestanasRevista(listOf("Todo") + kinds.map { it.title }, if (k == null) 0 else kinds.indexOf(k) + 1) { cat = if (it == 0) -1 else kinds[it - 1].id }
        Spacer(Modifier.height(6.dp))
        FiltrosRevista(anos.take(8).map { it.toString() } + "De siempre", if (ano == null) anos.take(8).size else anos.indexOf(ano).coerceAtLeast(0)) { i -> ano = anos.take(8).getOrNull(i) }
        Spacer(Modifier.height(10.dp))
        // Las cuatro cifras de la cabecera
        val palabra = k?.doneWord?.lowercase() ?: "terminados"
        Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Cifra(Formato.cifra(c.terminados), if (ano != null) "$palabra en $ano" else palabra, acento = true)
            Cifra(c.media?.let { Math.round(it).toString() } ?: "—", "nota media")
            Cifra(Formato.cifra(c.enCurso), k?.statuses?.get(1)?.lowercase() ?: "entre manos")
            Cifra(Formato.cifra(c.pendientes), "pendientes")
        }
        Spacer(Modifier.height(18.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            ColumnasRevista(if (maxWidth >= 860.dp) 2 else 1) {
                // El reto de lectura (lo fija Marfil)
                val (objetivo, deAno) = Cuenta.objetivoLibros()
                if ((k == null || k.isBook) && ano == esteAno && objetivo > 0 && (deAno == 0 || deAno == esteAno)) Bloque("Reto de lectura de $esteAno") {
                    val leidos = Cuenta.fichas(BOOKS.id).count { it.valorada && it.terminado > 0 && LocalDate.ofEpochDay(it.terminado).year == esteAno }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$leidos", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 56.sp)
                        Text(" de $objetivo libros", color = Muted, fontFamily = Grotesca, fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    BarraAvance(leidos.toFloat() / objetivo, Modifier.padding(top = 8.dp))
                    val dia = LocalDate.now().dayOfYear; val dif = leidos - objetivo * dia / LocalDate.now().lengthOfYear().toDouble()
                    Pie(when { leidos >= objetivo -> "Reto cumplido"; dif >= 0.5 -> "Vas ${f1(dif)} por delante del ritmo"; dif <= -0.5 -> "Vas ${f1(-dif)} por detrás del ritmo"; else -> "Vas justo al ritmo" },
                        acento = leidos >= objetivo, modifier = Modifier.padding(top = 6.dp))
                }
                Bloque("Resumen") {
                    MagFact(if (k != null) k.statuses[2] + "s" else "Abandonados", Formato.cifra(c.abandonados))
                    MagFact("Tasa de abandono", c.tasaAbandono?.let { "$it %" } ?: "—")
                    MagFact("Favoritos", Formato.cifra(c.favoritos))
                    MagFact("Con opinión escrita", Formato.cifra(c.conResena))
                }
                if (c.media != null) Bloque("Notas") {
                    MagFact("Nota media", f1(c.media))
                    MagFact("Con 90 o más", Formato.cifra(c.de90))
                    c.mejor?.let { DatoLargo("Mejor valorado", "${it.nombre} (${it.general})") }
                    c.peor?.let { if (it !== c.mejor) DatoLargo("Peor valorado", "${it.nombre} (${it.general})") }
                    if (k != null && k.crit > 1) {
                        Spacer(Modifier.height(8.dp)); Pie("Media por criterio")
                        for (i in 0 until k.crit) DatoConBarra(k.labels[i + 1], f1(c.criterios[i]), (c.criterios[i] / 100.0).toFloat())
                    }
                }
                if (c.media != null) Bloque("Reparto de notas") {
                    val mx = (c.reparto.maxOrNull() ?: 1).coerceAtLeast(1)
                    listOf("0–19", "20–39", "40–59", "60–79", "80–100").forEachIndexed { i, l -> DatoConBarra(l, "${c.reparto[i]}", c.reparto[i].toFloat() / mx) }
                }
                // El tiempo (Hiedra y Marfil, de todos tus dispositivos)
                val porDia = Cuenta.tiempoPorDia()
                for (kt in listOf(GAMES, BOOKS)) {
                    if (k != null && k.id != kt.id) continue
                    val fs = Cuenta.fichas(kt.id)
                    val nombres = fs.mapNotNull { f -> f.ref?.let { it to f.nombre } }.toMap()
                    val t = remember(porDia, ano, kt.id) { tiempoDe(porDia, nombres.keys, ano) }
                    if (t.totalMs < 60_000L) continue
                    val verbo = if (kt.isBook) "leyendo" else "jugando"
                    Bloque("Tiempo $verbo") {
                        val min = t.totalMs / 60_000L
                        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                            Cifra(if (min >= 600) "${Math.round(min / 60.0)} h" else duracion(min), "$verbo ${if (ano != null) "en $ano" else "en total"}")
                            Cifra(Formato.cifra(t.dias), if (t.dias == 1) "día $verbo" else "días $verbo")
                        }
                        Spacer(Modifier.height(10.dp))
                        if (t.dias > 0) MagFact(if (kt.isBook) "Media los días que lees" else "Media los días que juegas", duracion(t.totalMs / t.dias / 60_000L))
                        if (t.racha > 1) MagFact("Racha más larga", "${t.racha} días seguidos")
                        t.top.forEachIndexed { i, (r, ms) -> nombres[r]?.let { DatoLargo(if (i == 0) "Lo que más tiempo te ha llevado" else "", "$it (${duracion(ms / 60_000L)})") } }
                        Spacer(Modifier.height(10.dp))
                        Pie(if (ano != null) "Horas $verbo cada mes de $ano" else "Horas $verbo, los últimos 12 meses")
                        BarrasMes(t.porMes.map { it / 3_600_000.0 }, ano)
                    }
                }
                Bloque("Ritmo") {
                    val mejorMes = c.porMes.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }
                    if (mejorMes != null) MagFact("Mejor mes", "${Formato.mes(if (ano != null) mejorMes.index + 1 else YearMonth.now().minusMonths(11L - mejorMes.index).monthValue)} (${mejorMes.value})")
                    Pie(if (ano != null) "Terminados cada mes de $ano" else "Terminados, los últimos 12 meses", modifier = Modifier.padding(top = 6.dp))
                    BarrasMes(c.porMes.map { it.toDouble() }, ano, enteros = true)
                }
                if (c.creadores.isNotEmpty()) Bloque(k?.let { if (it.isBook) "Autores" else if (it.isMovie) "Directores" else if (it.isGame) "Estudios" else "Creadores" } ?: "Quién") {
                    val mx = c.creadores.first().second.toFloat()
                    c.creadores.forEach { (n, v) -> DatoConBarra(n, "$v", v / mx) }
                }
                if (c.plataformas.isNotEmpty()) Bloque(k?.let { if (it.isMovie || it.isTv) "Dónde ves" else it.platformWord + "s" } ?: "Dónde") {
                    val mx = c.plataformas.first().second.toFloat()
                    c.plataformas.forEach { (n, v) -> DatoConBarra(n, "$v", v / mx) }
                }
            }
        }
        // Lo mejor de cada categoría (las portadas)
        val del = todas.filter { (kk, f) -> (k == null || kk.id == k.id) && f.valorada && !f.sinValorar && (ano == null || (f.terminado > 0 && LocalDate.ofEpochDay(f.terminado).year == ano)) }
        kinds.filter { k == null || it.id == k.id }.forEach { kk ->
            val mejores = del.filter { it.first.id == kk.id }.sortedByDescending { it.second.general }.take(6)
            if (mejores.isNotEmpty()) { Seccion("Lo mejor · ${kk.title}"); FilaDeTarjetas(mejores, nav, Pantalla.Cifras) }
        }
        if (c.terminados == 0 && c.enCurso == 0) Vacio("Aún no hay cifras", "Termina y valora algo y aparecerá aquí.")
        Spacer(Modifier.height(30.dp))
    }
}

/** Un bloque de Cifras: filete, antetítulo con su cuadradito y lo de dentro. */
@Composable
private fun Bloque(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Filete(1.dp)
        Spacer(Modifier.height(12.dp))
        Antetitulo(titulo)
        Spacer(Modifier.height(12.dp))
        contenido()
    }
}

/** Un dato con puntos guía y, debajo, una barra fina en tinta (lo que pesa frente al mayor). */
@Composable
private fun DatoConBarra(etiqueta: String, valor: String, fraccion: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        MagFact(etiqueta, valor)
        Box(Modifier.fillMaxWidth().height(3.dp).background(Line)) { if (fraccion > 0f) Box(Modifier.fillMaxWidth(fraccion.coerceIn(0f, 1f)).fillMaxHeight().background(Charcoal)) }
    }
}

/** Un dato largo (un título): la etiqueta arriba y el valor debajo, a la derecha. */
@Composable
private fun DatoLargo(etiqueta: String, valor: String) {
    if (valor.length <= 26 && etiqueta.isNotBlank()) { MagFact(etiqueta, valor); return }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        if (etiqueta.isNotBlank()) Text(etiqueta, color = Muted, fontFamily = Body, fontSize = 14.sp)
        Text(valor, color = Charcoal, fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
    }
}

/** Doce barras, una por mes (la del mayor, en el acento), con la cifra encima y la inicial del mes debajo. */
@Composable
private fun BarrasMes(valores: List<Double>, ano: Int?, enteros: Boolean = false) {
    val mx = (valores.maxOrNull() ?: 0.0).takeIf { it > 0 } ?: 1.0
    val primerMes = if (ano != null) 1 else YearMonth.now().minusMonths(11).monthValue
    Row(Modifier.fillMaxWidth().height(130.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        valores.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (v > 0) Text(if (enteros) "${v.toInt()}" else if (v >= 10) "${v.toInt()}" else f1(v), color = Muted, fontFamily = Body, fontSize = 10.sp, maxLines = 1)
                Box(Modifier.fillMaxWidth(0.7f).height(if (v > 0) maxOf(3f, (82 * v / mx).toFloat()).dp else 1.dp).background(if (v <= 0) Line else if (v == mx) Acento else Charcoal))
                Spacer(Modifier.height(4.dp))
                Text(Formato.mes((primerMes - 1 + i) % 12 + 1).take(1).uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
