package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/*
 * La portada de Jonsu, la revista que junta a las hermanas: «La portada del día» (lo que más tiempo se llevó hoy entre todas), «Los retos de
 * todas» (los de cada hermana, de la cuenta, solo para leer) y «Esta semana» (todo junto, en barras apiladas por hermana). La lógica va
 * aparte de lo que se pinta, sin red, para poder probarla.
 */

private val ES = Locale("es", "ES")

/** Las hermanas que cuentan algo cada día: su nombre, qué haces en ella, su paquete y su color (el de su icono). */
enum class Hermana(val nombre: String, val verbo: String, val paquete: String, val color: Long) {
    BASTET("Bastet", "jugando", Hermanas.PAQ_HIEDRA, 0xFF6E7458),
    SESHAT("Seshat", "leyendo", Hermanas.PAQ_MARFIL, 0xFFC68B00),
    HATHOR("Hathor", "viendo", Hermanas.PAQ_MOSTAZA, 0xFF8C6F4E),
    THOT("Thot", "escribiendo", Hermanas.PAQ_COBALTO, 0xFF8696AE),
}

/** Un rato de un día en una hermana: qué (su ficha aquí, [id], o su ref allí, [ref]), cuánto tiempo y, en Thot, cuántas palabras. */
data class Rato(val hermana: Hermana, val id: Long, val ref: String, val titulo: String, val ms: Long, val palabras: Int = 0)

/**
 * Todo lo de cada día entre las hermanas: lo jugado en Bastet (por juego), lo leído en Seshat (por libro), lo visto en Hathor (por título)
 * y lo escrito en Thot (palabras del día, de la obra en la que escribes: [obra] = id y título).
 */
fun ratosPorDia(
    jugado: Map<Long, Map<JuegoPc, Long>>, leido: Map<Long, Map<Long, Long>>, visto: Map<Long, Map<String, Long>>, palabras: Map<Long, Int>,
    tituloLibro: (Long) -> String, tituloVisto: (String) -> String, obra: Pair<String, String>?
): Map<Long, List<Rato>> {
    val out = HashMap<Long, ArrayList<Rato>>()
    jugado.forEach { (d, m) ->
        m.entries.groupBy { if (it.key.sg != 0L) "#${it.key.sg}" else it.key.nombre }.forEach { (_, l) ->
            val j = l.first().key
            out.getOrPut(d) { ArrayList() } += Rato(Hermana.BASTET, j.sg, "", j.nombre, l.sumOf { it.value })
        }
    }
    leido.forEach { (d, m) -> m.forEach { (id, ms) -> out.getOrPut(d) { ArrayList() } += Rato(Hermana.SESHAT, id, "", tituloLibro(id), ms) } }
    visto.forEach { (d, m) -> m.forEach { (ref, ms) -> out.getOrPut(d) { ArrayList() } += Rato(Hermana.HATHOR, 0L, ref, tituloVisto(ref), ms) } }
    palabras.forEach { (d, n) -> if (n > 0) out.getOrPut(d) { ArrayList() } += Rato(Hermana.THOT, 0L, obra?.first.orEmpty(), obra?.second.orEmpty().ifBlank { "Escribiendo" }, 0L, n) }
    return out.mapValues { (_, l) -> l.filter { it.ms > 0 || it.palabras > 0 } }.filterValues { it.isNotEmpty() }
}

/** La portada de un día: lo principal ([principal]), de qué día es y lo demás de ese día. */
class PortadaDia(val principal: Rato, val dia: Long, val resto: List<Rato>)

/**
 * Lo más destacado: lo que más tiempo se llevó hoy (un libro, un juego, una película o serie). Lo escrito no tiene tiempo: solo es la portada
 * si ese día no hay nada con tiempo. Si hoy no hay nada, lo de ayer; si tampoco, lo del último día de la semana con algo.
 */
fun portadaDelDia(dias: Map<Long, List<Rato>>, hoy: Long, atras: Int = 7): PortadaDia? {
    for (d in hoy downTo hoy - atras) {
        val r = dias[d].orEmpty()
        if (r.isEmpty()) continue
        val top = r.filter { it.ms > 0 }.maxByOrNull { it.ms } ?: r.maxBy { it.palabras }
        return PortadaDia(top, d, r - top)
    }
    return null
}

/** «52′», «1 h», «1 h 40» (en mayúsculas para los antetítulos: «1 H 40»). */
fun duracionCorta(ms: Long, mayus: Boolean = false): String {
    val min = ((ms + 30_000L) / 60_000L).coerceAtLeast(1L)
    val t = if (min < 60) "$min′" else if (min % 60 == 0L) "${min / 60} h" else "${min / 60} h ${"%02d".format(min % 60)}"
    return if (mayus) t.uppercase() else t
}

/** «812 palabras», «1.204 palabras». */
fun palabrasTexto(n: Int): String = "%,d".format(ES, n) + if (n == 1) " palabra" else " palabras"

/** El día dicho como en un periódico: «HOY», «AYER», «EL JUEVES». */
fun cuandoFue(dia: Long, hoy: Long): String = when (dia) {
    hoy -> "HOY"
    hoy - 1 -> "AYER"
    else -> "EL " + LocalDate.ofEpochDay(dia).dayOfWeek.getDisplayName(TextStyle.FULL, ES).uppercase()
}

/** «HOY · 1 H 40 EN BASTET», «AYER · 812 PALABRAS EN THOT». */
fun antetituloPortada(p: PortadaDia, hoy: Long): String {
    val r = p.principal
    val cuanto = if (r.ms > 0) duracionCorta(r.ms, mayus = true) else palabrasTexto(r.palabras).uppercase()
    return "${cuandoFue(p.dia, hoy)} · $cuanto EN ${r.hermana.nombre.uppercase()}"
}

/** Lo demás del día, por hermana: «y 52′ leyendo, 812 palabras» (vacío si no hubo nada más). */
fun restoDelDia(p: PortadaDia): String {
    val partes = Hermana.entries.mapNotNull { h ->
        val l = p.resto.filter { it.hermana == h }
        when {
            h == Hermana.THOT -> l.sumOf { it.palabras }.takeIf { it > 0 }?.let { palabrasTexto(it) }
            else -> l.sumOf { it.ms }.takeIf { it > 0 }?.let { "${duracionCorta(it)} ${h.verbo}" }
        }
    }
    return if (partes.isEmpty()) "" else "y " + partes.joinToString(", ")
}

// ───── los retos de todas ─────

/** Un reto de una hermana: lo hecho, la meta (0: no tiene) y cómo va. */
class RetoHermana(val hermana: Hermana, val titulo: String, val hechos: Int, val meta: Int, val linea: String)

/** Cómo vas con un reto del año (como en Seshat, Bastet y Hathor): «VAS 2 POR DETRÁS · TE QUEDAN 17 EN 52 DÍAS», «¡RETO CUMPLIDO!». */
fun ritmoAnual(meta: Int, hechos: Int, hoy: LocalDate = LocalDate.now()): String {
    if (meta <= 0) return "SIN RETO ESTE AÑO"
    if (hechos >= meta) return if (hechos > meta) "¡RETO CUMPLIDO! Y ${hechos - meta} DE PROPINA" else "¡RETO CUMPLIDO!"
    val diasAno = hoy.lengthOfYear()
    val esperado = (meta.toLong() * hoy.dayOfYear / diasAno).toInt()
    val dif = hechos - esperado
    val quedan = meta - hechos
    val dias = diasAno - hoy.dayOfYear + 1
    val vas = when { dif > 0 -> "VAS $dif POR DELANTE"; dif < 0 -> "VAS ${-dif} POR DETRÁS"; else -> "VAS AL DÍA" }
    return "$vas · ${if (quedan == 1) "TE QUEDA 1" else "TE QUEDAN $quedan"} EN $dias ${if (dias == 1) "DÍA" else "DÍAS"}"
}

/** Cómo vas con la meta de palabras de hoy (la de Thot): «TE FALTAN 188», «¡META CUMPLIDA!». */
fun ritmoPalabras(meta: Int, hoy: Int): String = when {
    meta <= 0 -> "SIN META"
    hoy >= meta -> "¡META CUMPLIDA!"
    hoy == 0 -> "AÚN NADA HOY"
    else -> "TE FALTAN ${"%,d".format(ES, meta - hoy)}"
}

/** Los terminados en [ano] de una categoría: lo valorado o terminado con la fecha de fin de ese año (las fechas de fin van en días). */
fun terminadosEnAno(games: List<Game>, ano: Int): Int = games.count { it.end > 0L && runCatching { LocalDate.ofEpochDay(it.end).year }.getOrNull() == ano }

/** Los retos de la cuenta (`ajustes/lectura` de Seshat, `juego_reto` de Bastet, `cine_reto` de Hathor y `cobalto_meta` de Thot). */
class MetasCuenta(val libros: Int, val juegos: Int, val cine: Int, val palabras: Int) {
    /** (para las capturas: unas metas fijas en vez de las de la cuenta) */
    companion object { var fijas: MetasCuenta? = null }
}

/** Lee las metas de la cuenta de la familia (sin sesión, todas a 0 salvo la de palabras, que de serie es la de Thot: 500). */
fun metasDeLaCuenta(): MetasCuenta {
    fun n(nodo: String, campo: String): Int = runCatching {
        com.gonzalo.familia.LaFamilia.coleccion("ajustes").nodo(nodo)?.takeIf { !it.borrado && it.campos.isNotEmpty() }?.entero(campo)?.toInt()
    }.getOrNull() ?: 0
    return MetasCuenta(n("lectura", "ano"), n("juego_reto", "ano"), n("cine_reto", "ano"), n("cobalto_meta", "diaria").takeIf { it > 0 } ?: META_PALABRAS_DE_SERIE)
}
/** La meta de palabras de Thot si no has puesto otra (la misma que su DE_SERIE). */
const val META_PALABRAS_DE_SERIE = 500

fun retosDeTodas(m: MetasCuenta, libros: Int, juegos: Int, cine: Int, palabrasHoy: Int, hoy: LocalDate = LocalDate.now()): List<RetoHermana> = listOf(
    RetoHermana(Hermana.SESHAT, "Libros ${hoy.year}", libros, m.libros, ritmoAnual(m.libros, libros, hoy)),
    RetoHermana(Hermana.BASTET, "Juegos ${hoy.year}", juegos, m.juegos, ritmoAnual(m.juegos, juegos, hoy)),
    RetoHermana(Hermana.HATHOR, "Pelis y series ${hoy.year}", cine, m.cine, ritmoAnual(m.cine, cine, hoy)),
    RetoHermana(Hermana.THOT, "Palabras de hoy", palabrasHoy, m.palabras, ritmoPalabras(m.palabras, palabrasHoy)),
)

// ───── la semana ─────

/** Un día de la semana: el tiempo de cada hermana (ms) y las palabras de Thot. */
class DiaSemana(val dia: Long, val ms: Map<Hermana, Long>, val palabras: Int) {
    val total: Long get() = ms.values.sum()
    val algo: Boolean get() = total > 0 || palabras > 0
}

/** Los siete días hasta hoy, con lo de cada hermana. */
fun semanaApilada(dias: Map<Long, List<Rato>>, hoy: Long): List<DiaSemana> = (hoy - 6..hoy).map { d ->
    val r = dias[d].orEmpty()
    DiaSemana(d, Hermana.entries.filter { it != Hermana.THOT }.associateWith { h -> r.filter { it.hermana == h }.sumOf { it.ms } }, r.sumOf { it.palabras })
}

/** El reparto de un día, para cuando lo tocas: «J · 1 h 40 jugando · 52′ leyendo · 812 palabras». */
fun repartoDelDia(d: DiaSemana): String {
    val letra = LocalDate.ofEpochDay(d.dia).dayOfWeek.getDisplayName(TextStyle.NARROW, ES).uppercase()
    val partes = Hermana.entries.mapNotNull { h ->
        if (h == Hermana.THOT) d.palabras.takeIf { it > 0 }?.let { palabrasTexto(it) }
        else d.ms[h]?.takeIf { it > 0 }?.let { "${duracionCorta(it)} ${h.verbo}" }
    }
    return "$letra · " + (if (partes.isEmpty()) "nada" else partes.joinToString(" · "))
}

/** Los días seguidos con algo (jugado, leído, visto o escrito) hasta hoy (si hoy aún no, hasta ayer). */
fun diasSeguidosFamilia(dias: Map<Long, List<Rato>>, hoy: Long): Int = Racha.dias(hoy) { dias[it].orEmpty().isNotEmpty() }

// ───── lo que se pinta ─────

/** El color de una hermana para leerse sobre la página (las barras, la leyenda): el de su icono, aclarado si hace falta (3:1). */
@Composable
fun colorHermana(h: Hermana): Color = acentoLegible(Color(h.color), Paper, Charcoal, 3f)

/** Todo lo que la portada lee de las hermanas, de una vez (se vuelve a leer al llegar algo de la cuenta y al volver a la app). */
class DatosFamilia(val dias: Map<Long, List<Rato>>, val metas: MetasCuenta)

@Composable
fun rememberDatosFamilia(tituloLibro: (Long) -> String): DatosFamilia {
    val ctx = LocalContext.current
    val vuelve = rememberResumeTick()
    val v = Hermanas.version
    return remember(vuelve, v) {
        val titulos = Hermanas.titulosVistos(ctx)
        val obra = Hermanas.capitulos(ctx).maxByOrNull { it.u }?.let { it.obra to it.titulo }
        DatosFamilia(
            ratosPorDia(Hermanas.jugadoPorDia(ctx), Hermanas.leidoPorDia(ctx), Hermanas.vistoPorDia(ctx), Hermanas.palabras(ctx), tituloLibro, { titulos[it].orEmpty() }, obra),
            MetasCuenta.fijas ?: metasDeLaCuenta()
        )
    }
}

/**
 * «La portada del día»: la imagen grande de lo más destacado (su portada con paspartú sobre su fondo, si lo tiene, o su color), el antetítulo
 * («HOY · 1 H 40 EN BASTET»), el título y lo demás del día; debajo, «ABRIR EN BASTET →», que abre esa ficha en su app.
 */
@Composable
fun CartelDelDia(p: PortadaDia?, hoy: Long, ficha: ShellEntry?, alAbrirAqui: (() -> Unit)?) {
    val ctx = LocalContext.current
    if (p == null) {
        Column(Modifier.fillMaxWidth().testTag("cartel").background(Charcoal).padding(horizontal = 20.dp, vertical = 26.dp)) {
            Text("LA PORTADA DEL DÍA", color = acentoSobreTinta(), fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.6.sp)
            Text("Aún no hay nada esta semana.", color = Paper, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 31.sp, modifier = Modifier.padding(top = 4.dp))
            Text("Lo que juegues en Bastet, leas en Seshat, veas en Hathor o escribas en Thot sale aquí en cuanto llega a tu cuenta: lo que más tiempo se lleve cada día, en grande.",
                color = Paper.copy(alpha = 0.8f), fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
        }
        return
    }
    val r = p.principal
    val color = Color(r.hermana.color)
    val portada = ficha?.cover.orEmpty()
    val fondo = ficha?.hero.orEmpty()
    val acentoClaro = acentoLegible(AcentoBloque, Color(0xFF050404), Color.White, 4.5f)
    val resto = restoDelDia(p)
    Column(Modifier.fillMaxWidth().testTag("cartel")) {
        Box(Modifier.fillMaxWidth().height(400.dp).background(Brush.verticalGradient(listOf(lerpC(color, Color.White, 0.10f), lerpC(color, Color.Black, 0.72f))))
            .then(if (alAbrirAqui != null) Modifier.clickable(onClick = alAbrirAqui) else Modifier)) {
            val conFondo = fondo.isNotBlank()
            if (conFondo) {
                AsyncImage(fondo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = Ediciones.filtro)
                Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))))
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(190.dp).background(Brush.verticalGradient(0f to Color.Transparent, 0.25f to Color(0xCC050404), 1f to Color(0xF2050404))))
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 20.dp, bottom = 14.dp, start = 20.dp, end = 20.dp), contentAlignment = if (conFondo) Alignment.TopStart else Alignment.TopCenter) {
                    BoxWithConstraints(Modifier.fillMaxHeight()) {
                        val alto = minOf(maxHeight, if (conFondo) 220.dp else 250.dp)
                        PortadaConPaspartu(portada, r.titulo, alto * (2f / 3f), alto, color = color, sombra = 22.dp)
                    }
                }
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
                    Text(antetituloPortada(p, hoy), color = acentoClaro, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.6.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(r.titulo.uppercase(), color = Color.White, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 33.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    if (resto.isNotBlank()) Text(resto, color = Color.White.copy(alpha = 0.85f), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        BotonC("Abrir en ${r.hermana.nombre}", fondo = Acento, letra = SobreAcento) { abrirEnHermana(ctx, r) }
    }
}

/** Abre ese rato en su hermana: la ficha exacta (Bastet y Seshat por su ficha, Hathor por su título, Thot por su obra). */
fun abrirEnHermana(ctx: android.content.Context, r: Rato) {
    when (r.hermana) {
        Hermana.BASTET -> if (r.id != 0L) Hermanas.abrirEnHiedra(ctx, r.id) else Hermanas.abrir(ctx, Hermanas.PAQ_HIEDRA)
        Hermana.SESHAT -> Hermanas.leerEnMarfil(ctx, r.id)
        Hermana.HATHOR -> Hermanas.verEnMostaza(ctx, r.ref)
        Hermana.THOT -> Hermanas.escribirEnCobalto(ctx, r.ref)
    }
}

private fun lerpC(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

/** «Los retos de todas»: cada reto con su barra y cómo va. Solo se leen: tocar uno abre su hermana. */
@Composable
fun RetosDeTodas(retos: List<RetoHermana>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(modifier.fillMaxWidth().testTag("retos")) {
        Text("Los retos de todas", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, modifier = Modifier.padding(top = 22.dp, bottom = 4.dp))
        retos.forEach { r ->
            val c = colorHermana(r.hermana)
            Column(Modifier.fillMaxWidth().clickable { Hermanas.abrir(ctx, r.hermana.paquete) }.padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(r.titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (r.meta > 0) {
                        Text("%,d".format(ES, r.hechos), color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, letterSpacing = 0.sp)
                        Text(" / ${"%,d".format(ES, r.meta)}", color = Muted, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.sp)
                    }
                }
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).background(Line)) {
                    if (r.meta > 0) Box(Modifier.fillMaxWidth((r.hechos.toFloat() / r.meta).coerceIn(0f, 1f)).fillMaxHeight().background(c))
                }
                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(c)); Spacer(Modifier.width(6.dp))
                    Text("${r.hermana.nombre.uppercase()} · ${r.linea}", color = Muted, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}

/** «Esta semana»: barras apiladas por hermana (una por color, con su leyenda) que se tocan para ver el reparto del día, y los días seguidos. */
@Composable
fun SemanaFamilia(semana: List<DiaSemana>, racha: Int, hoy: Long, modifier: Modifier = Modifier) {
    var sel by remember(hoy) { mutableStateOf<Long?>(null) }
    val tope = maxOf(semana.maxOfOrNull { it.total } ?: 0L, 1L).toFloat()
    val total = semana.sumOf { it.total }
    Column(modifier.fillMaxWidth().testTag("semana")) {
        Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.Bottom) {
            Text("Esta semana", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, maxLines = 1, modifier = Modifier.weight(1f))
            if (total > 0) Text(duracionCorta(total, mayus = true), color = Acento, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1)
        }
        // El reparto del día tocado (o un aviso de que se pueden tocar), siempre a la misma altura: no mueve las barras
        Box(Modifier.fillMaxWidth().heightIn(min = 34.dp).padding(top = 6.dp), contentAlignment = Alignment.CenterStart) {
            val d = semana.firstOrNull { it.dia == sel }
            if (d != null) Text(repartoDelDia(d), color = Paper, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.6.sp, lineHeight = 15.sp,
                modifier = Modifier.background(Charcoal).padding(horizontal = 8.dp, vertical = 5.dp))
            else Text("TOCA UN DÍA PARA VER SU REPARTO", color = Muted, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp)
        }
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            semana.forEach { d ->
                val esHoy = d.dia == hoy
                val tocado = d.dia == sel
                Column(Modifier.weight(1f).clickable { sel = if (sel == d.dia) null else d.dia }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.fillMaxWidth().height(96.dp), verticalArrangement = Arrangement.Bottom) {
                        if (d.total == 0L) Box(Modifier.fillMaxWidth().height(if (d.palabras > 0) 0.dp else 2.dp).background(Line))
                        // De abajo arriba: Bastet, Seshat, Hathor
                        Hermana.entries.filter { it != Hermana.THOT }.reversed().forEach { h ->
                            val ms = d.ms[h] ?: 0L
                            if (ms > 0) Box(Modifier.fillMaxWidth().height(maxOf(3f, 96f * ms / tope).dp).background(colorHermana(h)))
                        }
                    }
                    // Lo escrito (sin tiempo): un cuadrado del color de Thot bajo la barra
                    Box(Modifier.padding(top = 4.dp).size(8.dp).background(if (d.palabras > 0) colorHermana(Hermana.THOT) else Color.Transparent))
                    Text(LocalDate.ofEpochDay(d.dia).dayOfWeek.getDisplayName(TextStyle.NARROW, ES).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black,
                        color = if (esHoy || tocado) Acento else Muted, modifier = Modifier.padding(top = 2.dp))
                    Box(Modifier.padding(top = 2.dp).fillMaxWidth(0.6f).height(2.dp).background(if (tocado) Acento else Color.Transparent))
                }
            }
        }
        // La leyenda
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Hermana.entries.forEach { h ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(colorHermana(h))); Spacer(Modifier.width(5.dp))
                    Text("${h.nombre.uppercase()}${if (h == Hermana.THOT) " (PALABRAS)" else ""}", color = Charcoal, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 0.8.sp, maxLines = 1)
                }
            }
        }
        if (racha > 0) Text("$racha ${if (racha == 1) "DÍA SEGUIDO" else "DÍAS SEGUIDOS"} CON ALGO", color = Muted, fontWeight = FontWeight.Black, fontSize = 10.5.sp, letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 10.dp))
    }
}

/**
 * La cabeza de la portada de Jonsu: la portada del día, los retos de todas y la semana. [entradas]: todo lo tuyo (para la portada y el fondo de
 * lo destacado y para abrirlo aquí); [libros], [juegos], [cine]: lo de cada categoría (para contar los terminados del año).
 */
@Composable
fun PortadaFamilia(entradas: List<ShellEntry>, libros: List<Game>, juegos: List<Game>, cine: List<Game>, abrir: (ShellEntry) -> Unit) {
    val hoy = LocalDate.now()
    val hoyD = hoy.toEpochDay()
    val porLibro = remember(entradas) { entradas.filter { it.kind.isBook }.associateBy { it.id } }
    val datos = rememberDatosFamilia { id -> porLibro[id]?.name.orEmpty() }
    val p = remember(datos) { portadaDelDia(datos.dias, hoyD) }
    val ficha = remember(p, entradas) { p?.let { fichaDe(it.principal, entradas) } }
    CartelDelDia(p, hoyD, ficha, ficha?.let { f -> { abrir(f) } })
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val palabrasHoy = datos.dias[hoyD].orEmpty().sumOf { it.palabras }
        RetosDeTodas(retosDeTodas(datos.metas, terminadosEnAno(libros, hoy.year), terminadosEnAno(juegos, hoy.year), terminadosEnAno(cine, hoy.year), palabrasHoy, hoy))
        SemanaFamilia(semanaApilada(datos.dias, hoyD), diasSeguidosFamilia(datos.dias, hoyD), hoyD)
    }
}

/** La ficha de Jonsu de un rato (para su portada, su fondo y abrirla aquí): por su número; en Hathor, por su nombre. */
fun fichaDe(r: Rato, entradas: List<ShellEntry>): ShellEntry? = when (r.hermana) {
    Hermana.BASTET -> entradas.firstOrNull { it.kind.isGame && it.id == r.id } ?: entradas.firstOrNull { it.kind.isGame && normName(it.name) == normName(r.titulo) }
    Hermana.SESHAT -> entradas.firstOrNull { it.kind.isBook && it.id == r.id }
    Hermana.HATHOR -> entradas.firstOrNull { (it.kind.isMovie || it.kind.isTv) && normName(it.name) == normName(r.titulo) }
    Hermana.THOT -> null
}
