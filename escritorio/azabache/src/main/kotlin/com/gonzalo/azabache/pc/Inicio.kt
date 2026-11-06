package com.gonzalo.azabache.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/*
 * La portada, como la del Azabache del móvil: la cabecera con su número y la fecha, «Hoy» (lo leído, jugado y escrito entre las hermanas,
 * los días seguidos y la semana en barras), «En portada» (lo último que tienes entre manos, a lo grande), «Ahora mismo», las notas más
 * altas en cifras enormes y lo último que has terminado.
 */

/** Lo de un día entre las tres hermanas: leído (Marfil), jugado (Hiedra) y escrito (Cobalto). */
class DiaHoy(val leidoMs: Long, val jugadoMs: Long, val palabras: Int) {
    /** La regla común de los «días seguidos»: un minuto leyendo o jugando, o una palabra. */
    val cuenta: Boolean get() = leidoMs >= 60_000L || jugadoMs >= 60_000L || palabras >= 1
    /** Para la altura de su barra: los minutos leídos y jugados (y unas 20 palabras el minuto). */
    val peso: Long get() = (leidoMs + jugadoMs) / 60_000L + palabras / 20
}

/** «40 min leyendo · 2 h jugando · 500 palabras» (lo que haya; vacío si nada). */
fun textoHoy(d: DiaHoy): String = listOfNotNull(
    if (d.leidoMs >= 60_000L) "${duracion((d.leidoMs + 30_000L) / 60_000L)} leyendo" else null,
    if (d.jugadoMs >= 60_000L) "${duracion((d.jugadoMs + 30_000L) / 60_000L)} jugando" else null,
    if (d.palabras > 0) "${Formato.cifra(d.palabras)} ${if (d.palabras == 1) "palabra" else "palabras"}" else null,
).joinToString(" · ")

/** Días seguidos que cuentan hasta hoy (si hoy todavía no cuenta, hasta ayer). La misma regla en las cuatro. */
fun rachaDe(hoy: Long, cuenta: (Long) -> Boolean): Int {
    var d = if (cuenta(hoy)) hoy else hoy - 1
    var n = 0
    while (cuenta(d)) { n++; d-- }
    return n
}

/** Lo de cada día (día → [DiaHoy]) de la cuenta: `tiempo/` (libros y juegos, por sus refs) y `palabras/` (Cobalto). */
fun diasDeLaCuenta(): Map<Long, DiaHoy> {
    val libros = Cuenta.fichas(BOOKS.id).mapNotNull { it.ref }.toSet()
    val porDia = Cuenta.tiempoPorDia()
    val palabras = HashMap<Long, Int>()
    runCatching {
        LaFamilia.coleccion("palabras").vivos().forEach { n -> n.campos.forEach { (k, v) ->
            if (k.length == 11 && k.startsWith("d2")) runCatching { LocalDate.parse(k.substring(1)).toEpochDay() }.getOrNull()?.let { palabras.merge(it, (v as? Number)?.toInt() ?: 0, Int::plus) }
        } }
    }
    val dias = porDia.keys.mapNotNull { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() } + palabras.keys
    return dias.distinct().associateWith { d ->
        val m = porDia[LocalDate.ofEpochDay(d).toString()].orEmpty()
        DiaHoy(m.filterKeys { it in libros }.values.sum(), m.filterKeys { it !in libros }.values.sum(), palabras[d] ?: 0)
    }
}

@Composable
fun PantInicio(nav: Nav) {
    LaFamilia.cambios
    val kinds = Cuenta.kinds()
    val todas = kinds.flatMap { k -> Cuenta.fichas(k.id).map { k to it } }
    val ahora = todas.filter { !it.second.valorada && it.second.estado == 1 }.sortedByDescending { it.second.actividad }
    val hoy = LocalDate.now()
    val ano = hoy.year
    val inicioAno = LocalDate.of(ano, 1, 1).toEpochDay()
    val delAno = todas.filter { it.second.valorada && it.second.terminado >= inicioAno }
    val recientes = todas.filter { it.second.valorada }.sortedByDescending { it.second.terminado.takeIf { d -> d > 0 }?.times(86_400_000L) ?: it.second.actividad }.take(12)
    val mejores = todas.filter { it.second.valorada && !it.second.sinValorar }.sortedByDescending { it.second.general }.take(5)
    Pagina {
        // La cabecera: el nombre de lado a lado, el número (el día del año) y la fecha
        // (grande: encoge hasta llegar justo de lado a lado, como en el quiosco)
        Cabecera(240.sp, texto = "AZABACHE")
        FileteCabecera()
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("Nº ${hoy.dayOfYear}", color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(diaLargo(hoy).uppercase(), color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Filete(1.dp)
        HoyRevista()

        // En portada: lo último que tienes entre manos, a lo grande
        val primero = ahora.firstOrNull()
        if (primero != null) {
            Spacer(Modifier.height(26.dp))
            val (k, f) = primero
            Row(Modifier.fillMaxWidth().border(1.5.dp, Charcoal).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(260.dp).clickable { nav.ir(Pantalla.Detalle(k.id, f.id, Pantalla.Inicio)) }) { PortadaCosa(f.portada, f.portadaWeb, f.nombre, Modifier.fillMaxWidth(), pie = k.unit) }
                Spacer(Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    Antetitulo("En portada")
                    Spacer(Modifier.height(10.dp))
                    val (fs, lh) = headlineSize(f.nombre)
                    FitTitle(f.nombre, (fs.value * 1.3f).sp, (lh.value * 1.3f).sp, letterSpacing = (-1.5).sp, modifier = Modifier.fillMaxWidth().clickable { nav.ir(Pantalla.Detalle(k.id, f.id, Pantalla.Inicio)) })
                    Text(listOf(k.title, f.creador, f.plataforma, if (!k.isBook && f.minutos > 0) duracion(f.minutos.toLong()) else "").filter { it.isNotBlank() }.joinToString(" · "),
                        color = Muted, fontFamily = Body, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                    val ses = remember(f.ref, LaFamilia.cambios) { sesionesDe(f.ref?.let { Cuenta.porDia(it) }.orEmpty().mapNotNull { (d, ms) -> runCatching { LocalDate.parse(d).toEpochDay() }.getOrNull()?.let { it to ms } }.toMap(), hoy.toEpochDay()) }
                    if (ses != null) Text("${ses.dias} ${if (ses.dias == 1) "día" else "días"} ${if (k.isBook) "leyendo" else "jugando"} · la última, ${haceDias(ses.ultima, hoy.toEpochDay())}",
                        color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("Abrir su página →", color = Acento, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        modifier = Modifier.padding(top = 14.dp).clickable { nav.ir(Pantalla.Detalle(k.id, f.id, Pantalla.Inicio)) }.padding(vertical = 4.dp))
                }
            }
        }

        // Ahora mismo (lo demás que llevas entre manos, numerado) y las notas más altas, en cifras enormes
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            ColumnasRevista(if (maxWidth >= 860.dp) 2 else 1) {
                if (ahora.size > 1) Column(Modifier.fillMaxWidth()) {
                    Seccion("Ahora mismo")
                    ahora.take(8).forEachIndexed { i, (k, f) ->
                        Row(Modifier.fillMaxWidth().clickable { nav.ir(Pantalla.Detalle(k.id, f.id, Pantalla.Inicio)) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("%02d".format(i + 1), color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, modifier = Modifier.width(60.dp))
                            MiniPortada(f, Modifier.width(44.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listOf(k.title, f.creador).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontFamily = Body, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                        Filete(1.dp, Line)
                    }
                }
                if (mejores.isNotEmpty()) Column(Modifier.fillMaxWidth()) {
                    Seccion("Las notas más altas")
                    mejores.forEach { (k, f) ->
                        Row(Modifier.fillMaxWidth().clickable { nav.ir(Pantalla.Detalle(k.id, f.id, Pantalla.Inicio)) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${f.general}", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 58.sp, lineHeight = 58.sp, maxLines = 1, softWrap = false, modifier = Modifier.width(120.dp))
                            MiniPortada(f, Modifier.width(44.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.nombre, color = Charcoal, fontFamily = Display, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(k.title.uppercase(), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            }
                        }
                    }
                }
            }
        }
        // Tu año, en cifras
        Seccion("Tu $ano", "Cifras →") { nav.ir(Pantalla.Cifras) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Cifra(delAno.size.toString(), "terminados en $ano", acento = true)
            kinds.take(4).forEach { k -> Cifra(delAno.count { it.first.id == k.id }.toString(), k.doneWord.lowercase() + " · " + k.title.lowercase()) }
            val (objetivo, deAno) = Cuenta.objetivoLibros()
            if (objetivo > 0 && (deAno == 0 || deAno == ano)) Cifra("${delAno.count { it.first.id == BOOKS.id }} / $objetivo", "reto de lectura")
        }
        if (ahora.isEmpty()) { Seccion("Ahora"); Vacio("Nada entre manos", "Lo que empieces en Hiedra, Marfil o aquí sale en esta portada.") }
        Seccion("Lo último que has terminado")
        if (recientes.isEmpty()) Vacio("Aún nada terminado") else FilaDeTarjetas(recientes, nav, Pantalla.Inicio)
    }
}

/**
 * «Hoy», debajo de la cabecera: lo que llevas hoy entre las tres hermanas, los días seguidos y la semana en barras (la de hoy, en el
 * acento; un día sin nada, una raya fina). Como en las portadas del móvil.
 */
@Composable
fun HoyRevista() {
    LaFamilia.cambios
    val dias = diasDeLaCuenta()
    val hoy = LocalDate.now()
    val h = dias[hoy.toEpochDay()] ?: DiaHoy(0, 0, 0)
    val racha = rachaDe(hoy.toEpochDay()) { dias[it]?.cuenta == true }
    val semana = (6 downTo 0).map { hoy.minusDays(it.toLong()) }.map { d -> d to (dias[d.toEpochDay()] ?: DiaHoy(0, 0, 0)) }
    val algo = semana.any { it.second.cuenta }
    if (!algo && racha < 2) return
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Acento)); Spacer(Modifier.width(8.dp))
            Text(listOfNotNull("HOY", textoHoy(h).ifBlank { "aún nada" }.uppercase(), if (racha >= 2) "$racha DÍAS SEGUIDOS" else null).joinToString(" · "),
                color = Charcoal, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        }
        if (algo) {
            val tope = maxOf(semana.maxOf { it.second.peso }, 1L).toFloat()
            Row(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                semana.forEach { (d, x) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth(0.62f).height(30.dp), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().height(if (x.cuenta) maxOf(3f, 30f * x.peso / tope).dp else 1.dp).background(if (x.cuenta) (if (d == hoy) Acento else Charcoal) else Line))
                        }
                        Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("es", "ES")).uppercase(), color = if (d == hoy) Acento else Muted, fontFamily = Body, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/** Unas tarjetas en filas de seis. */
@Composable
fun FilaDeTarjetas(l: List<Pair<Kind, Ficha>>, nav: Nav, volver: Pantalla, porFila: Int = 6) {
    l.chunked(porFila).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            fila.forEach { (k, f) -> Tarjeta(f, k, Modifier.weight(1f)) { nav.ir(Pantalla.Detalle(k.id, f.id, volver)) } }
            repeat(porFila - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
