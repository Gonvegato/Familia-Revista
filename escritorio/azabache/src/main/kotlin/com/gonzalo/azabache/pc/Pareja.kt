package com.gonzalo.azabache.pc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Tu pareja, como en el Azabache del móvil: su revista (lo que tiene entre manos, lo último que ha terminado, sus notas más altas) y
 * «Vosotros dos» (lo que habéis valorado los dos). Sale de su cuenta, solo para leer: la de tu pareja te deja verla si te ha puesto
 * como pareja en su Azabache (`/users/<suyo>/partner`).
 */

/** Lo que habéis valorado los dos (por el nombre, sin tildes ni mayúsculas): cuánto os parecéis. Pura: se prueba sin pantalla. */
class Afinidad(val comunes: Int, val distancia: Double, val masDeAcuerdo: Triple<String, Int, Int>?, val masLejos: Triple<String, Int, Int>?, val osEncantan: List<String>)

fun afinidadDe(mias: List<Ficha>, suyas: List<Ficha>): Afinidad? {
    val suyasPorNombre = suyas.filter { it.valorada && !it.sinValorar }.associateBy { normName(it.nombre) }
    val pares = mias.filter { it.valorada && !it.sinValorar }.mapNotNull { m -> suyasPorNombre[normName(m.nombre)]?.let { s -> Triple(m.nombre, m.general, s.general) } }
    if (pares.isEmpty()) return null
    val dif = pares.map { Math.abs(it.second - it.third) }
    return Afinidad(pares.size, dif.average(), pares.minByOrNull { Math.abs(it.second - it.third) }, pares.maxByOrNull { Math.abs(it.second - it.third) },
        pares.filter { it.second >= 85 && it.third >= 85 }.map { it.first })
}

@Composable
fun PantPareja(uid: String, nav: Nav) {
    LaFamilia.cambios
    var nombre by remember(uid) { mutableStateOf("Tu pareja") }
    LaunchedEffect(uid) { nombre = withContext(Dispatchers.IO) { runCatching { Cuenta.nombreDe(uid) }.getOrDefault("Tu pareja") } }
    val kinds = Cuenta.kindsDe(uid)
    val todas = kinds.flatMap { k -> Cuenta.fichasDe(uid, k.id).map { k to it } }
    val ahora = todas.filter { !it.second.valorada && it.second.estado == 1 }.sortedByDescending { it.second.actividad }
    val terminadas = todas.filter { it.second.valorada }.sortedByDescending { it.second.terminado.takeIf { d -> d > 0 }?.times(86_400_000L) ?: it.second.actividad }
    val mejores = terminadas.filter { !it.second.sinValorar }.sortedByDescending { it.second.general }.take(5)
    fun abre(k: Kind, f: Ficha) = nav.ir(Pantalla.DeLaPareja(uid, k.id, f.id))
    Pagina {
        Text("LA REVISTA DE TU PAREJA · SOLO PARA LEER", color = Acento, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        TituloPagina(nombre.uppercase())
        FileteCabecera()
        if (todas.isEmpty()) {
            Vacio("Aún no se ve nada de $nombre", "Sale en cuanto te ponga como pareja en su Jonsu (Ajustes → Cuenta) y llegue lo suyo de su cuenta.")
            return@Pagina
        }
        Seccion("Ahora")
        if (ahora.isEmpty()) Vacio("Nada entre manos") else Tarjetas(ahora.take(12), uid) { k, f -> abre(k, f) }
        Seccion("Lo último que ha terminado")
        if (terminadas.isEmpty()) Vacio("Aún nada terminado") else Tarjetas(terminadas.take(12), uid) { k, f -> abre(k, f) }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            ColumnasRevista(if (maxWidth >= 860.dp) 2 else 1) {
                if (mejores.isNotEmpty()) Column(Modifier.fillMaxWidth()) {
                    Seccion("Sus notas más altas")
                    mejores.forEach { (k, f) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${f.general}", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 58.sp, lineHeight = 58.sp, maxLines = 1, softWrap = false, modifier = Modifier.width(120.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.nombre, color = Charcoal, fontFamily = Grotesca, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(k.title.uppercase(), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    Seccion("Vosotros dos")
                    val misKinds = Cuenta.kinds()
                    val porKind = kinds.mapNotNull { k -> misKinds.firstOrNull { it.title == k.title || it.id == k.id && (k.id == GAMES.id || k.id == BOOKS.id) }?.let { mk -> afinidadDe(Cuenta.fichas(mk.id), Cuenta.fichasDe(uid, k.id)) } }
                    val comunes = porKind.sumOf { it.comunes }
                    if (comunes == 0) MagVacio("Todavía no habéis valorado nada en común.")
                    else {
                        val dist = porKind.sumOf { it.distancia * it.comunes } / comunes
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${Math.round(100 - dist).coerceIn(0, 100)} %", color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 64.sp)
                            Spacer(Modifier.width(14.dp))
                            Pie("de afinidad", modifier = Modifier.padding(bottom = 12.dp))
                        }
                        Text("Os separan ${String.format(java.util.Locale("es", "ES"), "%.1f", dist)} puntos de media en $comunes en común.", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
                        porKind.mapNotNull { it.masDeAcuerdo }.minByOrNull { Math.abs(it.second - it.third) }?.let { (n, a, b) -> MagFact("Más de acuerdo", "$n ($a y $b)".let { if (it.length > 34) it.take(33) + "…" else it }) }
                        porKind.mapNotNull { it.masLejos }.maxByOrNull { Math.abs(it.second - it.third) }?.let { (n, a, b) -> MagFact("Más lejos", "$n ($a y $b)".let { if (it.length > 34) it.take(33) + "…" else it }) }
                        val encantan = porKind.flatMap { it.osEncantan }
                        if (encantan.isNotEmpty()) NotaCasilla("Os han encantado a los dos (85 o más): " + encantan.take(8).joinToString(", ") + ".")
                    }
                }
            }
        }
        // Las recomendaciones: lo que te recomienda (a tu lista o descartar) y lo que le has recomendado tú
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            ColumnasRevista(if (maxWidth >= 860.dp) 2 else 1) {
                Column(Modifier.fillMaxWidth()) { TeRecomienda(uid, nombre, nav, maximo = 10, volver = Pantalla.Pareja) }
                Column(Modifier.fillMaxWidth()) { LeHasRecomendado(nombre, nav) }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

/** Unas tarjetas de tu pareja en filas de seis (sus portadas salen de su cuenta). */
@Composable
private fun Tarjetas(l: List<Pair<Kind, Ficha>>, uid: String, porFila: Int = 6, abre: (Kind, Ficha) -> Unit) {
    l.chunked(porFila).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            fila.forEach { (k, f) -> Tarjeta(f, k, Modifier.weight(1f), deUid = uid) { abre(k, f) } }
            repeat(porFila - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** La página de una ficha de tu pareja: la misma de revista, solo para leer. */
@Composable
fun PantDeLaPareja(uid: String, kind: Int, id: Long, nav: Nav) {
    LaFamilia.cambios
    val k = Cuenta.kindsDe(uid).firstOrNull { it.id == kind } ?: GAMES
    val f = Cuenta.fichasDe(uid, kind).firstOrNull { it.id == id }
    if (f == null) { Pagina { VolverRevista({ nav.ir(Pantalla.Pareja) }, "Pareja"); Vacio("Esta ficha ya no está") }; return }
    PaginaDeFicha(k, f, Pantalla.Pareja, nav, false, AccionesFicha(), deUid = uid)
}
