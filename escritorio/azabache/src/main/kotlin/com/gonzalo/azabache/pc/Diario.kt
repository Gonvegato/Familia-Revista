package com.gonzalo.azabache.pc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            Box(Modifier.weight(1f)) { Cabecera(84.sp, texto = "DIARIO") }
            BotonAcento("Escribir", alto = 44.dp, modifier = Modifier.width(150.dp)) { escribir = true }
        }
        FileteCabecera()
        Spacer(Modifier.height(8.dp))
        Entradilla("Lo que escribes y lo que hiciste cada día: lo terminado, lo jugado, lo leído.")
        dias.forEach { d ->
            val ns = notas[d].orEmpty(); val ts = terminados[d].orEmpty(); val tm = tiempo[LocalDate.ofEpochDay(d).toString()].orEmpty()
            if (ns.isEmpty() && ts.isEmpty() && tm.isEmpty() && d != hoy) return@forEach
            Seccion(if (d == hoy) "Hoy" else diaLargo(LocalDate.ofEpochDay(d)))
            if (ns.isEmpty() && ts.isEmpty() && tm.isEmpty()) Cuerpo("Nada todavía. ¿Escribes algo?", tam = 14)
            ns.forEach { n ->
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(n.texto, color = Charcoal, fontFamily = Display, fontSize = 19.sp, lineHeight = 27.sp)
                    val sobre = n.sobre.mapNotNull { porClave[it]?.second?.nombre }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (sobre.isNotEmpty()) Text("Sobre " + sobre.joinToString(", "), color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                        EnlaceRevista("Editar") { editar = n }
                    }
                }
            }
            ts.forEach { (_, k, f) -> FilaFicha(f, k, (if (k.isBook) "Lo terminaste" else if (k.isMovie || k.isTv) "La terminaste" else "Lo terminaste") + if (f.sinValorar) "" else " · nota ${f.general}") { nav.ir(Pantalla.Detalle(k.id, f.id, Pantalla.Diario)) } }
            tm.entries.sortedByDescending { it.value }.forEach { (ref, ms) ->
                val par = porRef[ref]
                if (par != null && ms >= 60_000L) FilaFicha(par.second, par.first, "${duracion(ms / 60_000L)} ${if (par.first.isBook) "leyendo" else "jugando"}") { nav.ir(Pantalla.Detalle(par.first.id, par.second.id, Pantalla.Diario)) }
            }
        }
    }
    if (escribir) DialogoNota(null, emptyList(), nav) { escribir = false }
    editar?.let { n -> DialogoNota(n, n.sobre, nav) { editar = null } }
}

/** Escribir (o cambiar) una nota del diario. [sobre]: las fichas de las que habla. */
@Composable
fun DialogoNota(nota: Nota?, sobre: List<String>, nav: Nav, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var texto by remember { mutableStateOf(nota?.texto.orEmpty()) }
    var dia by remember { mutableStateOf(nota?.dia ?: LocalDate.now().toEpochDay()) }
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
