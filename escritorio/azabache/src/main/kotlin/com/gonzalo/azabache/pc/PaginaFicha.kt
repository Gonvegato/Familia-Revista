package com.gonzalo.azabache.pc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.rememberImagenWeb
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/*
 * La página de una ficha en modo lectura, como en el Azabache del móvil: «Volver», la estrella, «Diseñar» y «Editar» arriba; el antetítulo,
 * el titular enorme y la entradilla; y la página en filas de casillas (la maqueta que diseñaste, aquí o en el móvil, o la de siempre), aquí a
 * dos columnas, como una revista abierta. «Diseñar» abre el editor de la maqueta (Disenar.kt) y «Editar», los datos y la nota.
 */

/** Un apunte de algo que llevas entre manos (los hace el móvil): su nota y lo que escribiste. */
private class Apunte(val hora: Long, val notas: List<Int>, val texto: String, val capitulo: Int)

private fun apuntesDe(f: Ficha): List<Apunte> = f.o["entries"].comoLista().map { it.comoMapa() }.map { e ->
    Apunte(e["time"].comoLong(), NOTAS.map { n -> e[n].comoLong().toInt() }, e["note"].comoTexto(), e["chapter"].comoLong().toInt())
}.sortedByDescending { it.hora }

/** Lo jugado o leído de cada día de una ficha (día → ms), de `tiempo/<ref>~<fuente>`. */
private fun porDiaDe(f: Ficha): Map<Long, Long> = f.ref?.let { Cuenta.porDia(it) }.orEmpty()
    .mapNotNull { (d, ms) -> runCatching { LocalDate.parse(d).toEpochDay() }.getOrNull()?.let { it to ms } }.toMap()

/** Lo que se hace desde las casillas de una página (en la de tu pareja, nada). */
class AccionesFicha(
    val editar: () -> Unit = {}, val valorar: () -> Unit = {}, val portada: () -> Unit = {}, val diario: () -> Unit = {},
    val saga: () -> Unit = {}, val nota: (Nota) -> Unit = {}, val recomendar: (() -> Unit)? = null, val pareja: String = "",
)

@Composable
fun PaginaDeFicha(
    k: Kind, f: Ficha, volver: Pantalla, nav: Nav, sucio: Boolean,
    acciones: AccionesFicha, onGuardar: () -> Unit = {}, onDescartar: () -> Unit = {},
    /** La ficha de tu pareja (de su cuenta): se lee y nada más. */
    deUid: String? = null,
    /** Diseñando la maqueta de la página (el editor ocupa la pantalla). */
    disenando: Boolean = false, onDisenar: (Boolean) -> Unit = {},
) {
    LaFamilia.cambios
    val soloLectura = deUid != null
    val scope = rememberCoroutineScope()
    val enCurso = !f.valorada
    fun guardaYa(cambios: Map<String, Any?>) { scope.launch { withContext(Dispatchers.IO) { Cuenta.guarda(k.id, f, f.con(cambios), f.valorada) } } }
    val casillas = casillasDeFicha(k, f, nav, acciones, deUid, ::guardaYa)

    if (disenando && !soloLectura) { EditorDePagina(k, f, casillas, onListo = { onDisenar(false) }, onDatos = { onDisenar(false); acciones.editar() }); return }

    // La maqueta de esta página (la que diseñaste) o la de siempre
    val filas = remember(f.id, enCurso, LaFamilia.cambios) {
        if (soloLectura) Maquetas.deSiempre(k, enCurso).let { l -> l.take(1) + FilaMaqueta(listOf(CasillaMaqueta("tuya"))) + l.drop(1) }
        else Maquetas.paraVer(k, f.id, enCurso)
    }

    Pagina {
        // Arriba: volver, la estrella, «Diseñar» y «Editar»
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { VolverRevista({ nav.ir(volver) }, texto = if (volver is Pantalla.Lista) k.title else if (volver is Pantalla.Pareja) "Pareja" else "Volver") }
            if (soloLectura) Text("DE TU PAREJA · SOLO PARA LEER", color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            else {
                Text(if (f.fav) "★" else "☆", color = if (f.fav) Acento else Charcoal, fontFamily = Body, fontSize = 20.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable { guardaYa(mapOf("fav" to !f.fav)) }.padding(horizontal = 10.dp, vertical = 6.dp))
                Spacer(Modifier.width(14.dp))
                EnlaceRevista("Diseñar") { onDisenar(true) }
                Spacer(Modifier.width(18.dp))
                EnlaceRevista("Editar") { acciones.editar() }
            }
        }
        Filete(3.dp)
        if (sucio) Row(Modifier.fillMaxWidth().padding(top = 10.dp).background(Sand).padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Hay cambios sin guardar en los datos.", Modifier.weight(1f), color = Charcoal, fontFamily = Body, fontSize = 13.sp)
            EnlaceRevista("Descartar") { onDescartar() }
            Spacer(Modifier.width(18.dp))
            EnlaceRevista("Guardar", acento = true) { onGuardar() }
        }
        Spacer(Modifier.height(16.dp))
        CabeceraDeFicha(k, f)
        Spacer(Modifier.height(22.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            ColumnasRevista(if (maxWidth >= 860.dp) 2 else 1) {
                filas.forEach { fila ->
                    val vistas = fila.casillas.filter { visibleEnLectura(it, casillas) }
                    if (vistas.isEmpty()) return@forEach
                    FilaDeCasillas(vistas.map { c -> casillaDe(c, casillas, fila.alto > 0f) }, if (vistas.size == 2) fila.split else 1f, fila.alto)
                }
            }
        }
        if (!soloLectura) MagFooter("Editar") { acciones.editar() }
    }
}

/** El antetítulo en el acento, el titular enorme (encoge si no cabe) y la entradilla en cursiva. */
@Composable
fun CabeceraDeFicha(k: Kind, f: Ficha, escala: Float = 1.45f) {
    val enCurso = !f.valorada
    val antetitulo = listOfNotNull(if (enCurso) k.statuses.getOrNull(f.estado) else null, k.title, f.creador.ifBlank { null }).joinToString(" · ")
    Text(antetitulo.uppercase(), color = Acento, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    val (fs, lh) = headlineSize(f.nombre.ifBlank { "Sin nombre" })
    FitTitle(f.nombre.ifBlank { "Sin nombre" }, (fs.value * escala).sp, (lh.value * escala).sp, letterSpacing = (-1.5).sp, modifier = Modifier.fillMaxWidth(), family = Grotesca)
    val entradilla = listOfNotNull(
        f.plataforma.ifBlank { null },
        if (!enCurso && f.terminado > 0) "terminado el ${Formato.fechaDia(f.terminado)}" else null,
        if (enCurso && f.empezado > 0) "desde el ${Formato.fechaDia(f.empezado)}" else null,
        if (enCurso && !k.isBook && f.minutos > 0) duracion(f.minutos.toLong()) else null
    ).joinToString(" · ")
    if (entradilla.isNotBlank()) Text(entradilla, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 19.sp, modifier = Modifier.padding(top = 4.dp))
}

/** ¿Se ve esta casilla al leer? (un texto vacío, una imagen sin dirección, una vacía o lo que aquí no hay, no). */
fun visibleEnLectura(c: CasillaMaqueta, casillas: Map<String, Casilla>) = when (c.tipo) {
    "texto" -> c.titulo.isNotBlank() || c.texto.isNotBlank()
    "imagen" -> c.imagen.startsWith("http")
    "vacio" -> false
    else -> c.tipo in casillas
}

/** Lo que pinta una casilla de la maqueta (las tuyas: texto e imagen; las de la ficha, de [casillas]). */
fun casillaDe(c: CasillaMaqueta, casillas: Map<String, Casilla>, filaFija: Boolean): Casilla = when (c.tipo) {
    "texto" -> Casilla(c.titulo.ifBlank { null }) { if (c.texto.isNotBlank()) Text(c.texto, color = Charcoal, fontFamily = Grotesca, fontSize = 18.sp, lineHeight = 27.sp) }
    "imagen" -> Casilla(c.titulo.ifBlank { null }) { ImagenDeCasilla(c, filaFija) }
    else -> casillas.getValue(c.tipo)
}

/**
 * Las casillas que hay para esta ficha (las que no, como la crítica o dónde verla, son del móvil). Las comparten la página para leer y el
 * editor de la maqueta.
 */
@Composable
fun casillasDeFicha(k: Kind, f: Ficha, nav: Nav, a: AccionesFicha, deUid: String?, guardaYa: (Map<String, Any?>) -> Unit): Map<String, Casilla> {
    val soloLectura = deUid != null
    val enCurso = !f.valorada
    val hoy = LocalDate.now().toEpochDay()
    val casillas = linkedMapOf<String, Casilla>()
    if (k.isGame || k.isBook) casillas["portada"] = Casilla("Portada") { MagPortada(f, k, deUid) }
    if (!enCurso) casillas["nota"] = Casilla("Nota") {
        if (f.sinValorar) {
            Text("Sin valorar", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 34.sp)
            MagVacio(if (soloLectura) "Aún no le ha puesto nota." else if (k.isBook) "Lo diste por leído en Seshat." else if (k.isGame) "Lo diste por pasado en Bastet." else if (f.mostaza) "La viste entera en Hathor." else "Aún sin nota.")
            if (!soloLectura) MagLink("Valorar", accent = true) { a.valorar() }
        } else MagScore(f.general, f.notas, k)
    }
    val apuntes = apuntesDe(f)
    if (enCurso) casillas["estado"] = Casilla(if (apuntes.isNotEmpty()) "Nota" else "Estado") {
        if (apuntes.isNotEmpty()) MagScore(apuntes.map { it.notas[0] }.average().toInt(), NOTAS.indices.map { i -> apuntes.map { it.notas[i] }.average().toInt() }, k)
        else {
            FitTitle(k.statuses.getOrElse(f.estado) { "" }, 32.sp, 34.sp, family = Grotesca)
            MagVacio("Aún sin nota")
        }
        Spacer(Modifier.height(8.dp))
        // Cambiar de estado aquí mismo (va a la cuenta al momento: lo ve Hiedra o Marfil)
        if (!soloLectura) MagChoice(k.statuses, f.estado) { i ->
            guardaYa(when (i) {
                1 -> mapOf("status" to 1L, "start" to (f.empezado.takeIf { it > 0 } ?: hoy), "end" to 0L)
                2 -> mapOf("status" to 2L, "end" to (f.terminado.takeIf { it > 0 } ?: hoy))
                else -> mapOf("status" to 0L)
            })
        }
        if (!soloLectura) MagLink(if (apuntes.isNotEmpty()) "Pasar a valorados" else "Terminar y valorar", accent = true) { a.valorar() }
    }
    casillas["opinion"] = Casilla("Opinión", if (soloLectura) null else "Escribir" to a.editar) {
        if (f.resena.isNotBlank()) MagBody(f.resena) else MagVacio(if (soloLectura) "No ha escrito nada." else "Aún no has escrito nada.", 18)
    }
    if (k.isGame) casillas["horas"] = Casilla("Horas") { MagHours(f.minutos, f.ttb.first, f.ttb.second, f.ttb.third, enCurso = enCurso && f.estado == 1) }
    if (k.isGame && enCurso) casillas["jugar"] = Casilla("Jugar") { MagVacio("Se juega desde Bastet: en este ordenador, en el móvil o en streaming.") }
    // Lo que llevas visto (los episodios, por dónde vas): de Mostaza y, si no, de los capítulos que marcaste en Azabache
    if ((k.isMovie || k.isTv) && enCurso) casillas["progreso"] = Casilla("Progreso") { CasillaProgreso(k, f, soloLectura) }
    if (k.isBook) casillas["lectura"] = Casilla("Lectura") { CasillaLectura(k, f, enCurso && !soloLectura) }
    // (como en el móvil: de los juegos y los libros, lo que cuentan Hiedra y Marfil)
    val ses = if (!k.isGame && !k.isBook) null else remember(f.ref, LaFamilia.cambios, f.terminado, f.estado) {
        val porDia = if (deUid != null) f.ref?.let { r -> Cuenta.porDiaDe(deUid, r).mapNotNull { (d, ms) -> runCatching { LocalDate.parse(d).toEpochDay() }.getOrNull()?.let { it to ms } }.toMap() }.orEmpty() else porDiaDe(f)
        sesionesDe(porDia, hoy, if (!enCurso) f.terminado else if (f.estado == 2) f.terminado else 0L)
    }
    if (ses != null) casillas["sesiones"] = Casilla("Sesiones") { MagSesiones(ses, k, terminado = !enCurso || f.estado == 2) }
    if (enCurso) casillas["apuntes"] = Casilla("Apuntes") {
        if (apuntes.isEmpty()) MagVacio("Aún no has apuntado nada (los apuntes se hacen en el móvil).")
        apuntes.take(6).forEach { ap ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${ap.notas[0]}", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 30.sp, modifier = Modifier.padding(end = 10.dp))
                    Pie(listOfNotNull(if (ap.hora > 0) Formato.fecha(ap.hora) else null, if (k.isBook && ap.capitulo > 0) "cap. ${ap.capitulo}" else null).joinToString(" · "), modifier = Modifier.padding(bottom = 6.dp))
                }
                if (ap.texto.isNotBlank()) Text(ap.texto, color = Charcoal, fontFamily = Grotesca, fontSize = 17.sp, lineHeight = 25.sp)
            }
        }
    }
    val clave = "${k.id}:${f.id}"
    val delDiario = if (soloLectura) emptyList() else Cuenta.notas().filter { clave in it.sobre }
    if (delDiario.isNotEmpty()) casillas["diario"] = Casilla("En tu diario", "Escribir" to a.diario) {
        delDiario.take(4).forEach { n ->
            Column(Modifier.fillMaxWidth().clickable { a.nota(n) }.padding(vertical = 6.dp)) {
                Pie(if (n.dia == hoy) "Hoy" else diaLargo(LocalDate.ofEpochDay(n.dia)))
                if (n.texto.isNotBlank()) Text(n.texto.let { if (it.length > 320) it.take(320).trimEnd() + "…" else it }, color = Charcoal, fontFamily = Grotesca, fontSize = 17.sp, lineHeight = 25.sp)
            }
        }
        if (delDiario.size > 4) NotaCasilla("y ${delDiario.size - 4} más en tu diario")
    }
    casillas["datos"] = Casilla("Datos") {
        var algo = false
        if (f.creador.isNotBlank()) { MagFact(k.creatorWord, f.creador); algo = true }
        if (f.plataforma.isNotBlank()) { MagFact(k.platformWord, f.plataforma); algo = true }
        if (k.isBook && f.paginas > 0) { MagFact("Páginas", Formato.cifra(f.paginas)); algo = true }
        if (f.empezado > 0) { MagFact("Empezado", Formato.fechaTablaDia(f.empezado)); algo = true }
        if (!enCurso && f.terminado > 0) { MagFact("Terminado", Formato.fechaTablaDia(f.terminado)); algo = true }
        if (!algo) MagVacio("Sin datos.")
    }
    if (!soloLectura) casillas["enlaces"] = Casilla("Enlaces") {
        MagLink("Escribir en el diario") { a.diario() }
        val saga = Cuenta.sagas().firstOrNull { it.kind == k.id && f.id in it.items }
        if (saga != null) {
            val vivas = fichasDe(saga).map { it.id }
            MagLink("Saga · ${saga.name}", "Nº ${vivas.indexOf(f.id) + 1} de ${vivas.size}") { nav.ir(Pantalla.UnaSaga(saga.id)) }
        } else MagLink("Añadir a una saga") { a.saga() }
        MagLink("Cambiar portada") { a.portada() }
        MagLink("Datos de la ficha") { a.editar() }
        // Recomendar a tu pareja (como en el móvil: los juegos y los libros)
        a.recomendar?.let { r -> MagLink("Recomendar", "a ${a.pareja}", accent = true) { r() } }
        if (f.ref != null) NotaCasilla(mismaQueEnSuApp(k, f))
        // «Ver en Mostaza» (en el móvil la abre allí): aquí no hay Mostaza, que aún no tiene versión para el ordenador
        if (f.mostaza) NotaCasilla("Se ve en Hathor, en el móvil o en la tele: aún no hay Hathor para el ordenador.")
    }

    // De tu pareja: y tú, ¿qué le diste? (la misma cosa en tu colección, por su nombre)
    val tuya = if (soloLectura) Cuenta.kinds().firstOrNull { it.id == k.id || it.title == k.title }?.let { mk -> Cuenta.fichas(mk.id).firstOrNull { normName(it.nombre) == normName(f.nombre) }?.let { mk to it } } else null
    if (tuya != null) casillas["tuya"] = Casilla("Y tú") {
        val (mk, t) = tuya
        if (t.valorada && !t.sinValorar) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${t.general}", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 56.sp)
                if (!f.sinValorar && f.valorada) Pie(if (t.general == f.general) "lo mismo" else if (t.general > f.general) "${t.general - f.general} más que tu pareja" else "${f.general - t.general} menos que tu pareja", modifier = Modifier.padding(start = 10.dp, bottom = 10.dp))
            }
        } else MagVacio(if (t.valorada) "Tú aún no le has puesto nota." else "Lo tienes como «${estadoDe(t, mk).lowercase()}».")
        MagLink("Tu página") { nav.ir(Pantalla.Detalle(mk.id, t.id, Pantalla.Pareja)) }
    }
    return casillas
}

/** «Es el mismo juego que en Hiedra…»: lo que se dice de una ficha que es la cosa de una hermana (sin copia). */
fun mismaQueEnSuApp(k: Kind, f: Ficha): String = when {
    f.mostaza -> "Es la misma ${k.unit} que en Hathor: lo que valoras aquí lo ve allí, y lo que ves allí (${if (k.isTv) "los episodios" else "por dónde vas"}, el tiempo, si la terminas) llega aquí."
    k.isGame -> "Es el mismo juego que en Bastet: lo que cambies aquí lo ve allí, y al revés."
    else -> "Es el mismo libro que en Seshat: lo que cambies aquí lo ve allí, y al revés."
}

/**
 * «Progreso» en la ficha de una película o una serie que estás viendo: los episodios vistos (los de Mostaza, `progreso`, mandan sobre los
 * que marcaste en Azabache; si la serie no tiene su lista de episodios, los que has visto en Mostaza) o, en una película, por dónde vas.
 * Solo se lee: los episodios se marcan en Mostaza.
 */
@Composable
private fun CasillaProgreso(k: Kind, f: Ficha, soloLectura: Boolean) {
    val partes = f.o["parts"].comoLista().map { it.comoMapa() }.map { it["n"].comoTexto() to (it["d"] == true) }
    val vistos = remember(f.ref, LaFamilia.cambios) { if (f.mostaza && !soloLectura) f.ref?.let { CosasMostaza.vistosDe(it) }.orEmpty() else emptyList() }
    when {
        partes.isNotEmpty() -> {
            val hechos = partes.count { it.second }
            val pct = hechos * 100 / partes.size
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$pct %", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 34.sp)
                Pie("$hechos de ${partes.size} ${if (k.isTv) "episodios" else "completados"}", modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
            }
            BarraAvance(pct / 100f, Modifier.padding(top = 8.dp, bottom = 6.dp))
            partes.take(14).forEach { (n, d) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (d) "✓" else "·", Modifier.width(22.dp), color = if (d) Acento else Muted, fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(n, color = if (d) Muted else Charcoal, fontFamily = Body, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (d) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                }
            }
            if (partes.size > 14) Pie("y ${partes.size - 14} más")
        }
        k.isTv && vistos.any { it.episodio > 0 && it.visto } -> {
            val eps = vistos.filter { it.episodio > 0 && it.visto }
            Text("${eps.size}", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 42.sp)
            Pie(if (eps.size == 1) "episodio visto en Hathor" else "episodios vistos en Hathor")
            val ultimo = vistos.filter { it.episodio > 0 }.maxByOrNull { it.cuando }
            if (ultimo != null) MagFact("El último", "T${ultimo.temporada} · E${ultimo.episodio}" + if (!ultimo.visto && ultimo.durMs > 0 && ultimo.posMs > 0) " (a medias)" else "")
        }
        !k.isTv && vistos.firstOrNull()?.let { it.posMs > 0 && it.durMs > 0 } == true -> {
            val v = vistos.first()
            val frac = (v.posMs.toFloat() / v.durMs).coerceIn(0f, 1f)
            Text("${(frac * 100).toInt()} %", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 34.sp)
            BarraAvance(frac, Modifier.padding(top = 8.dp))
            Pie(if (v.visto) "Vista en Hathor" else "Por ${duracion(v.posMs / 60_000L)} de ${duracion(v.durMs / 60_000L)} · quedan ${duracion(((v.durMs - v.posMs) / 60_000L).coerceAtLeast(1L))}", modifier = Modifier.padding(top = 5.dp))
        }
        else -> MagVacio(if (f.mostaza) "Lo que veas en Hathor (los episodios, por dónde vas) sale aquí." else "Sin capítulos.")
    }
    if (f.mostaza && !soloLectura) NotaCasilla("Se marca en Hathor, al verla.")
}

/** «Lectura» en la ficha de un libro: lo que Marfil cuenta (el tiempo, por dónde vas, las páginas). */
@Composable
private fun CasillaLectura(k: Kind, f: Ficha, enCurso: Boolean) {
    if (f.minutos > 0) {
        Text(duracion(f.minutos.toLong()), color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 34.sp)
        Pie("Leyendo, según Seshat")
    } else MagVacio("Seshat aún no ha contado tiempo de este libro.")
    val avance = remember(f.ref, LaFamilia.cambios) { Cuenta.nodo(k.id, f)?.decimal("fr")?.toFloat() ?: 0f }
    if (enCurso && f.estado == 1 && avance > 0f && avance < 1f) {
        val pct = (avance * 100).toInt().coerceIn(1, 99)
        BarraAvance(avance, Modifier.padding(top = 10.dp))
        Pie(if (f.paginas > 0) "Vas por el $pct % · página ${(avance * f.paginas).toInt().coerceAtLeast(1)} de ${f.paginas}" else "Vas por el $pct %", modifier = Modifier.padding(top = 5.dp))
    } else if (f.paginas > 0) Text("${Formato.cifra(f.paginas)} páginas", color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    NotaCasilla("Se lee en Seshat (en este ordenador o en el móvil), por donde ibas.")
}

// ---------------------------------------------------------------- las imágenes de las casillas (como Maqueta.kt del móvil)

/** La forma (ancho / alto) de las imágenes ya vistas, para «la de la foto» y para encuadrar. */
val proporciones = mutableStateMapOf<String, Float>()

/** La forma del marco de una imagen según su forma elegida (la de `magAspect` del móvil, sin la casilla). */
fun formaDe(c: CasillaMaqueta): Float = when (c.forma) {
    "vertical" -> 0.75f
    "cuadrada" -> 1f
    "apaisada" -> 1.4f
    "original" -> proporciones[c.imagen] ?: 1.4f
    "llenar" -> 0.75f
    else -> if (c.entera) 2.2f else 1.4f
}

/**
 * Zoom y desplazamiento (en píxeles) con los que se pinta una imagen en una casilla de [w]×[h]: la foto nunca deja un hueco dentro de la
 * casilla (no se aleja de «llenar» ni se desplaza más allá de su borde); las «enteras» (logos) se dejan como están. Es `magPlace` del móvil:
 * lo que se ve al encuadrar es lo que sale en la página, aquí y allí.
 */
fun colocaImagen(c: CasillaMaqueta, ratio: Float?, w: Float, h: Float): Triple<Float, Float, Float> {
    if (c.entera || ratio == null || w <= 0f || h <= 0f) return Triple(c.zoom, c.ox * w, c.oy * h)
    val alta = ratio > w / h
    val dw = if (alta) h * ratio else w
    val dh = if (alta) h else w / ratio
    val z = c.zoom.coerceAtLeast(1f)
    val sx = (dw * z - w) / 2f
    val sy = (dh * z - h) / 2f
    return Triple(z, (c.ox * w).coerceIn(-sx, sx), (c.oy * h).coerceIn(-sy, sy))
}

/**
 * Una imagen de una casilla (las de internet: las que pusiste desde la galería del móvil se quedan allí), con la forma y el encuadre que le
 * diste. La misma regla que el móvil: en una fila de alto fijo (o a medias con otra casilla y «llenar» sin forma propia) llena la casilla; si
 * no, la forma que dejó el encuadre.
 */
@Composable
fun ColumnScope.ImagenDeCasilla(c: CasillaMaqueta, filaFija: Boolean, alMedir: (IntSize) -> Unit = {}) {
    val img = rememberImagenWeb(c.imagen)
    LaunchedEffect(img) { if (img != null && img.height > 0) proporciones[c.imagen] = img.width.toFloat() / img.height }
    val partida = LocalCellFraction.current < 0.99f
    val llena = filaFija || (c.proporcion <= 0f && c.forma == "llenar" && partida)
    val forma = when {
        llena -> Modifier.weight(1f).heightIn(min = 140.dp)
        c.proporcion > 0f -> Modifier.aspectRatio(c.proporcion.coerceIn(0.2f, 5f))
        c.forma == "llenar" -> Modifier.aspectRatio(proporciones[c.imagen] ?: 0.75f)
        else -> Modifier.aspectRatio(formaDe(c).coerceIn(0.3f, 4f))
    }
    Box(Modifier.fillMaxWidth().then(forma).clipToBounds().background(if (img == null) Sand else Paper).onSizeChanged(alMedir)) {
        if (img != null) ImagenEncuadrada(img, c, Modifier.matchParentSize())
    }
}

/** La imagen ENTERA, a su tamaño de «llenar», movida y ampliada; la casilla la recorta después (como `FramedImage` del móvil). */
@Composable
fun ImagenEncuadrada(img: ImageBitmap, c: CasillaMaqueta, modifier: Modifier) {
    val r = img.width.toFloat() / img.height.coerceAtLeast(1)
    if (c.entera) {
        Image(img, c.titulo, modifier.graphicsLayer { val (z, tx, ty) = colocaImagen(c, r, size.width, size.height); scaleX = z; scaleY = z; translationX = tx; translationY = ty },
            contentScale = ContentScale.Fit)
        return
    }
    var caja by remember { mutableStateOf(IntSize.Zero) }
    val den = LocalDensity.current
    Box(modifier.onSizeChanged { caja = it }, contentAlignment = Alignment.Center) {
        if (caja.width > 0 && caja.height > 0) {
            val bw = caja.width.toFloat(); val bh = caja.height.toFloat()
            val alta = r > bw / bh
            val dw = if (alta) bh * r else bw
            val dh = if (alta) bh else bw / r
            Image(img, c.titulo, Modifier.requiredSize(with(den) { dw.toDp() }, with(den) { dh.toDp() }).graphicsLayer {
                val (z, tx, ty) = colocaImagen(c, r, bw, bh); scaleX = z; scaleY = z; translationX = tx; translationY = ty
            }, contentScale = ContentScale.FillBounds)
        }
    }
}
