package com.gonzalo.azabache.pc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * El diario: lo que escribes (las notas, varias por día) y lo que hiciste cada día (lo que terminaste, lo que jugaste en Hiedra o en
 * Steam, lo que leíste en Marfil). Las notas son de la cuenta (`azabache/diario~<id>`): las ves igual en el móvil.
 */
@Composable
fun PantDiario(nav: Nav) {
    LaFamilia.cambios
    var escribir by remember { mutableStateOf(false) }
    var editar by remember { mutableStateOf<Nota?>(null) }
    var escribirDia by remember { mutableStateOf<Long?>(null) }
    val kinds = Cuenta.kinds()
    val porRef = HashMap<String, Pair<Kind, Ficha>>()
    val porClave = HashMap<String, Pair<Kind, Ficha>>()
    kinds.forEach { k -> Cuenta.fichas(k.id).forEach { f -> f.ref?.let { porRef[it] = k to f }; porClave["${k.id}:${f.id}"] = k to f } }
    val notas = Cuenta.notas().groupBy { it.dia }
    val terminados = kinds.flatMap { k -> Cuenta.fichas(k.id).filter { it.valorada && it.terminado > 0 }.map { Triple(it.terminado, k, it) } }.groupBy { it.first }
    val tiempo = Cuenta.tiempoPorDia()
    val hoy = LocalDate.now().toEpochDay()
    val dias = (notas.keys + terminados.keys + tiempo.keys.mapNotNull { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() } + hoy)
        .filter { it in (hoy - 365)..hoy }.distinct().sortedDescending().take(120)
    Pagina(ancho = 900.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(diaLargo(LocalDate.now()).uppercase(), color = Acento, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Cabecera(84.sp, texto = "DIARIO")
            }
            BotonAcento("Escribir", alto = 44.dp, modifier = Modifier.padding(bottom = 12.dp).width(150.dp)) { escribir = true }
        }
        FileteCabecera()
        Spacer(Modifier.height(8.dp))
        // Los días seguidos (la regla de las cuatro: un minuto leyendo o jugando, o una palabra)
        val delDia = diasDeLaCuenta()
        val racha = rachaDe(hoy) { delDia[it]?.cuenta == true }
        Entradilla(if (racha >= 2) "$racha días seguidos leyendo, jugando o escribiendo." else "Lo que escribes y lo que hiciste cada día: lo terminado, lo jugado, lo leído.")
        // Por meses, como en el móvil: el mes en cursiva con su resumen y, debajo, cada día con su número grande
        var mes: java.time.YearMonth? = null
        dias.forEach { d ->
            val ns = notas[d].orEmpty(); val ts = terminados[d].orEmpty(); val tm = tiempo[LocalDate.ofEpochDay(d).toString()].orEmpty()
            if (ns.isEmpty() && ts.isEmpty() && tm.isEmpty() && d != hoy) return@forEach
            val ym = java.time.YearMonth.from(LocalDate.ofEpochDay(d))
            if (ym != mes) {
                mes = ym
                val delMes = dias.filter { java.time.YearMonth.from(LocalDate.ofEpochDay(it)) == ym }
                val nTerm = delMes.sumOf { terminados[it].orEmpty().size }
                val leido = delMes.sumOf { delDia[it]?.leidoMs ?: 0L }; val jugado = delMes.sumOf { delDia[it]?.jugadoMs ?: 0L }
                Column(Modifier.fillMaxWidth().padding(top = 30.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(ym.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es", "ES")).replaceFirstChar { it.uppercase() }, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 36.sp, lineHeight = 40.sp)
                        Text("${ym.year}", color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp, bottom = 7.dp))
                    }
                    val resumen = listOfNotNull(
                        if (nTerm > 0) "$nTerm ${if (nTerm == 1) "terminado" else "terminados"}" else null,
                        if (leido >= 60_000L) "${duracion(leido / 60_000L)} leyendo" else null,
                        if (jugado >= 60_000L) "${duracion(jugado / 60_000L)} jugando" else null
                    ).joinToString(" · ")
                    if (resumen.isNotBlank()) Text(resumen, color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(8.dp)); Filete(2.dp)
                }
            }
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(if (d == hoy) "Hoy" else "${LocalDate.ofEpochDay(d).dayOfMonth}", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 34.sp)
                    Text(LocalDate.ofEpochDay(d).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es", "ES")), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
                    Spacer(Modifier.weight(1f))
                    EnlaceRevista("Escribir") { escribirDia = d }
                }
                if (ns.isEmpty() && ts.isEmpty() && tm.isEmpty()) MagVacio("Nada todavía. ¿Escribes algo?")
                ns.forEach { n ->
                    Column(Modifier.fillMaxWidth().clickable { editar = n }.padding(vertical = 8.dp)) {
                        Text(n.texto, color = Charcoal, fontFamily = Display, fontSize = 19.sp, lineHeight = 27.sp)
                        val sobre = n.sobre.mapNotNull { porClave[it] }
                        if (sobre.isNotEmpty()) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            sobre.forEach { (k, f) -> Text("${f.nombre} →", color = Acento, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { nav.ir(Pantalla.Detalle(k.id, f.id, Pantalla.Diario)) }) }
                        }
                    }
                }
                ts.forEach { (_, k, f) ->
                    FilaFicha(f, k, (if (k.isMovie || k.isTv) "La terminaste" else "Lo terminaste"), derecha = {
                        if (!f.sinValorar) Text("${f.general}", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                    }) { nav.ir(Pantalla.Detalle(k.id, f.id, Pantalla.Diario)) }
                }
                tm.entries.sortedByDescending { it.value }.forEach { (ref, ms) ->
                    val par = porRef[ref]
                    if (par != null && ms >= 60_000L) FilaFicha(par.second, par.first, "${duracion(ms / 60_000L)} ${if (par.first.isBook) "leyendo" else "jugando"}") { nav.ir(Pantalla.Detalle(par.first.id, par.second.id, Pantalla.Diario)) }
                }
                Spacer(Modifier.height(8.dp))
                Filete(1.dp, Line)
            }
        }
    }
    if (escribir) DialogoNota(null, emptyList(), nav) { escribir = false }
    escribirDia?.let { d -> DialogoNota(null, emptyList(), nav, diaInicial = d) { escribirDia = null } }
    editar?.let { n -> DialogoNota(n, n.sobre, nav) { editar = null } }
}

/** Escribir (o cambiar) una nota del diario. [sobre]: las fichas de las que habla. */
@Composable
fun DialogoNota(nota: Nota?, sobre: List<String>, nav: Nav, diaInicial: Long? = null, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var texto by remember { mutableStateOf(nota?.texto.orEmpty()) }
    var dia by remember { mutableStateOf(nota?.dia ?: diaInicial ?: LocalDate.now().toEpochDay()) }
    Dialogo(if (nota == null) "Escribir en el diario" else "La nota del ${fecha(nota.dia)}", onCerrar, ancho = 640.dp, botones = {
        if (nota != null) EnlaceRevista("Borrar", acento = true) { scope.launch { withContext(Dispatchers.IO) { Cuenta.borraNota(nota) }; onCerrar(); nav.aviso("Nota borrada") } }
        Spacer(Modifier.weight(1f))
        BotonSuave("Cancelar") { onCerrar() }
        BotonAcento("Guardar", modifier = Modifier.width(130.dp), alto = 44.dp) {
            if (texto.isNotBlank()) scope.launch {
                val n = (nota ?: Nota(System.currentTimeMillis(), dia, "", sobre)).copy(texto = texto.trim(), dia = dia)
                withContext(Dispatchers.IO) { Cuenta.guardaNota(n) }
                onCerrar(); nav.aviso("En el diario")
            }
        }
    }) {
        CampoFecha("Día", dia, Modifier.width(260.dp)) { if (it > 0) dia = it }
        OutlinedTextField(texto, { texto = it }, Modifier.fillMaxWidth().heightIn(min = 180.dp), placeholder = { Text("¿Qué tal hoy?") })
        if (sobre.isNotEmpty()) {
            val nombres = sobre.mapNotNull { s -> s.split(':').let { p -> p.getOrNull(0)?.toIntOrNull()?.let { k -> p.getOrNull(1)?.toLongOrNull()?.let { Cuenta.ficha(k, it)?.nombre } } } }
            if (nombres.isNotEmpty()) Text("Sobre " + nombres.joinToString(", "), color = Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
