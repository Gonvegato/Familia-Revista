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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * La página de una ficha en modo lectura, como en el Azabache del móvil: «Volver», la estrella y «Editar» arriba; el antetítulo, el
 * titular enorme y la entradilla; y la página en filas de casillas (la maqueta que diseñaste en el móvil o, si no, la de siempre), aquí a
 * dos columnas, como una revista abierta. «Editar» abre los datos y la nota (la ficha de siempre del ordenador).
 */

/** Un apunte de algo que llevas entre manos (los hace el móvil): su nota y lo que escribiste. */
private class Apunte(val hora: Long, val notas: List<Int>, val texto: String, val capitulo: Int)

private fun apuntesDe(f: Ficha): List<Apunte> = f.o["entries"].comoLista().map { it.comoMapa() }.map { e ->
    Apunte(e["time"].comoLong(), NOTAS.map { n -> e[n].comoLong().toInt() }, e["note"].comoTexto(), e["chapter"].comoLong().toInt())
}.sortedByDescending { it.hora }

/** Lo jugado o leído de cada día de una ficha (día → ms), de `tiempo/<ref>~<fuente>`. */
private fun porDiaDe(f: Ficha): Map<Long, Long> = f.ref?.let { Cuenta.porDia(it) }.orEmpty()
    .mapNotNull { (d, ms) -> runCatching { LocalDate.parse(d).toEpochDay() }.getOrNull()?.let { it to ms } }.toMap()

@Composable
fun PaginaDeFicha(
    k: Kind, f: Ficha, volver: Pantalla, nav: Nav, sucio: Boolean,
    onEditar: () -> Unit, onValorar: () -> Unit, onGuardar: () -> Unit, onDescartar: () -> Unit,
    onPortada: () -> Unit, onDiario: () -> Unit, onSaga: () -> Unit, onNota: (Nota) -> Unit,
    /** La ficha de tu pareja (de su cuenta): se lee y nada más. */
    deUid: String? = null
) {
    LaFamilia.cambios
    val soloLectura = deUid != null
    val scope = rememberCoroutineScope()
    val enCurso = !f.valorada
    val hoy = LocalDate.now().toEpochDay()
    fun guardaYa(cambios: Map<String, Any?>) { scope.launch { withContext(Dispatchers.IO) { Cuenta.guarda(k.id, f, f.con(cambios), f.valorada) } } }

    Pagina {
        // Arriba: volver, la estrella y «Editar»
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { VolverRevista({ nav.ir(volver) }, texto = if (volver is Pantalla.Lista) k.title else if (volver is Pantalla.Pareja) "Pareja" else "Volver") }
            if (soloLectura) Text("DE TU PAREJA · SOLO PARA LEER", color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            else {
                Text(if (f.fav) "★" else "☆", color = if (f.fav) Acento else Charcoal, fontFamily = Body, fontSize = 20.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable { guardaYa(mapOf("fav" to !f.fav)) }.padding(horizontal = 10.dp, vertical = 6.dp))
                Spacer(Modifier.width(8.dp))
                EnlaceRevista("Editar") { onEditar() }
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
        // El antetítulo en el acento, el titular enorme (encoge si no cabe) y la entradilla en cursiva
        val antetitulo = listOfNotNull(if (enCurso) k.statuses.getOrNull(f.estado) else null, k.title, f.creador.ifBlank { null }).joinToString(" · ")
        Text(antetitulo.uppercase(), color = Acento, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val (fs, lh) = headlineSize(f.nombre.ifBlank { "Sin nombre" })
        FitTitle(f.nombre.ifBlank { "Sin nombre" }, (fs.value * 1.45f).sp, (lh.value * 1.45f).sp, letterSpacing = (-1.5).sp, modifier = Modifier.fillMaxWidth())
        val entradilla = listOfNotNull(
            f.plataforma.ifBlank { null },
            if (!enCurso && f.terminado > 0) "terminado el ${Formato.fechaDia(f.terminado)}" else null,
            if (enCurso && f.empezado > 0) "desde el ${Formato.fechaDia(f.empezado)}" else null,
            if (enCurso && !k.isBook && f.minutos > 0) duracion(f.minutos.toLong()) else null
        ).joinToString(" · ")
        if (entradilla.isNotBlank()) Text(entradilla, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 19.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(22.dp))

        // Las casillas que hay para esta ficha (las que no, como la crítica o dónde verla, son del móvil)
        val casillas = linkedMapOf<String, Casilla>()
        if (k.isGame || k.isBook) casillas["portada"] = Casilla("Portada") { MagPortada(f, k, deUid) }
        if (!enCurso) casillas["nota"] = Casilla("Nota") {
            if (f.sinValorar) {
                Text("Sin valorar", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 34.sp)
                MagVacio(if (soloLectura) "Aún no le ha puesto nota." else if (k.isBook) "Lo diste por leído en Marfil." else if (k.isGame) "Lo diste por pasado en Hiedra." else "Aún sin nota.")
                if (!soloLectura) MagLink("Valorar", accent = true) { onValorar() }
            } else MagScore(f.general, f.notas, k)
        }
        val apuntes = apuntesDe(f)
        if (enCurso) casillas["estado"] = Casilla(if (apuntes.isNotEmpty()) "Nota" else "Estado") {
            if (apuntes.isNotEmpty()) MagScore(apuntes.map { it.notas[0] }.average().toInt(), NOTAS.indices.map { i -> apuntes.map { it.notas[i] }.average().toInt() }, k)
            else {
                FitTitle(k.statuses.getOrElse(f.estado) { "" }, 32.sp, 34.sp)
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
            if (!soloLectura) MagLink(if (apuntes.isNotEmpty()) "Pasar a valorados" else "Terminar y valorar", accent = true) { onValorar() }
        }
        casillas["opinion"] = Casilla("Opinión", if (soloLectura) null else "Escribir" to onEditar) {
            if (f.resena.isNotBlank()) MagBody(f.resena) else MagVacio(if (soloLectura) "No ha escrito nada." else "Aún no has escrito nada.", 18)
        }
        if (k.isGame) casillas["horas"] = Casilla("Horas") { MagHours(f.minutos, f.ttb.first, f.ttb.second, f.ttb.third, enCurso = enCurso && f.estado == 1) }
        if (k.isGame && enCurso) casillas["jugar"] = Casilla("Jugar") { MagVacio("Se juega desde Hiedra: en este ordenador, en el móvil o en streaming.") }
        if (k.isBook) casillas["lectura"] = Casilla("Lectura") { CasillaLectura(k, f, enCurso && !soloLectura) }
        val ses = remember(f.ref, LaFamilia.cambios, f.terminado, f.estado) {
            val porDia = if (deUid != null) f.ref?.let { r -> Cuenta.porDiaDe(deUid, r).mapNotNull { (d, ms) -> runCatching { LocalDate.parse(d).toEpochDay() }.getOrNull()?.let { it to ms } }.toMap() }.orEmpty() else porDiaDe(f)
            sesionesDe(porDia, hoy, if (!enCurso) f.terminado else if (f.estado == 2) f.terminado else 0L)
        }
        if (ses != null) casillas["sesiones"] = Casilla("Sesiones") { MagSesiones(ses, k, terminado = !enCurso || f.estado == 2) }
        if (enCurso) casillas["apuntes"] = Casilla("Apuntes") {
            if (apuntes.isEmpty()) MagVacio("Aún no has apuntado nada (los apuntes se hacen en el móvil).")
            apuntes.take(6).forEach { a ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${a.notas[0]}", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, modifier = Modifier.padding(end = 10.dp))
                        Pie(listOfNotNull(if (a.hora > 0) Formato.fecha(a.hora) else null, if (k.isBook && a.capitulo > 0) "cap. ${a.capitulo}" else null).joinToString(" · "), modifier = Modifier.padding(bottom = 6.dp))
                    }
                    if (a.texto.isNotBlank()) Text(a.texto, color = Charcoal, fontFamily = Display, fontSize = 17.sp, lineHeight = 25.sp)
                }
            }
        }
        val clave = "${k.id}:${f.id}"
        val delDiario = if (soloLectura) emptyList() else Cuenta.notas().filter { clave in it.sobre }
        if (delDiario.isNotEmpty()) casillas["diario"] = Casilla("En tu diario", "Escribir" to onDiario) {
            delDiario.take(4).forEach { n ->
                Column(Modifier.fillMaxWidth().clickable { onNota(n) }.padding(vertical = 6.dp)) {
                    Pie(if (n.dia == hoy) "Hoy" else diaLargo(LocalDate.ofEpochDay(n.dia)))
                    if (n.texto.isNotBlank()) Text(n.texto.let { if (it.length > 320) it.take(320).trimEnd() + "…" else it }, color = Charcoal, fontFamily = Display, fontSize = 17.sp, lineHeight = 25.sp)
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
            MagLink("Escribir en el diario") { onDiario() }
            val saga = Cuenta.sagas().firstOrNull { it.kind == k.id && f.id in it.items }
            if (saga != null) {
                val vivas = fichasDe(saga).map { it.id }
                MagLink("Saga · ${saga.name}", "Nº ${vivas.indexOf(f.id) + 1} de ${vivas.size}") { nav.ir(Pantalla.UnaSaga(saga.id)) }
            } else MagLink("Añadir a una saga") { onSaga() }
            MagLink("Cambiar portada") { onPortada() }
            MagLink("Datos de la ficha") { onEditar() }
            if (f.ref != null) NotaCasilla(if (k.isGame) "Es el mismo juego que en Hiedra: lo que cambies aquí lo ve allí, y al revés." else "Es el mismo libro que en Marfil: lo que cambies aquí lo ve allí, y al revés.")
        }

        // De tu pareja: y tú, ¿qué le diste? (la misma cosa en tu colección, por su nombre)
        val tuya = if (soloLectura) Cuenta.kinds().firstOrNull { it.id == k.id || it.title == k.title }?.let { mk -> Cuenta.fichas(mk.id).firstOrNull { normName(it.nombre) == normName(f.nombre) }?.let { mk to it } } else null
        if (tuya != null) casillas["tuya"] = Casilla("Y tú") {
            val (mk, t) = tuya
            if (t.valorada && !t.sinValorar) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${t.general}", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 56.sp)
                    if (!f.sinValorar && f.valorada) Pie(if (t.general == f.general) "lo mismo" else if (t.general > f.general) "${t.general - f.general} más que tu pareja" else "${f.general - t.general} menos que tu pareja", modifier = Modifier.padding(start = 10.dp, bottom = 10.dp))
                }
            } else MagVacio(if (t.valorada) "Tú aún no le has puesto nota." else "Lo tienes como «${estadoDe(t, mk).lowercase()}».")
            MagLink("Tu página") { nav.ir(Pantalla.Detalle(mk.id, t.id, Pantalla.Pareja)) }
        }

        // La maqueta de esta página (la que diseñaste en el móvil) o la de siempre
        val filas = remember(f.id, enCurso, LaFamilia.cambios) {
            if (soloLectura) Maquetas.deSiempre(k, enCurso).let { l -> l.take(1) + FilaMaqueta(listOf(CasillaMaqueta("tuya"))) + l.drop(1) }
            else Maquetas.de(k.id, f.id, enCurso) ?: Maquetas.deSiempre(k, enCurso)
        }
        fun visible(c: CasillaMaqueta) = when (c.tipo) {
            "texto" -> c.titulo.isNotBlank() || c.texto.isNotBlank()
            "imagen" -> c.imagen.startsWith("http")
            "vacio" -> false
            else -> c.tipo in casillas
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            ColumnasRevista(if (maxWidth >= 860.dp) 2 else 1) {
                filas.forEach { fila ->
                    val vistas = fila.casillas.filter { visible(it) }
                    if (vistas.isEmpty()) return@forEach
                    FilaDeCasillas(vistas.map { c ->
                        when (c.tipo) {
                            "texto" -> Casilla(c.titulo.ifBlank { null }) { if (c.texto.isNotBlank()) Text(c.texto, color = Charcoal, fontFamily = Display, fontSize = 18.sp, lineHeight = 27.sp) }
                            "imagen" -> Casilla(c.titulo.ifBlank { null }) { ImagenDeCasilla(c, fila.alto > 0f) }
                            else -> casillas.getValue(c.tipo)
                        }
                    }, if (vistas.size == 2) fila.split else 1f, fila.alto)
                }
            }
        }
        if (!soloLectura) MagFooter("Editar") { onEditar() }
    }
}

/** «Lectura» en la ficha de un libro: lo que Marfil cuenta (el tiempo, por dónde vas, las páginas). */
@Composable
private fun CasillaLectura(k: Kind, f: Ficha, enCurso: Boolean) {
    if (f.minutos > 0) {
        Text(duracion(f.minutos.toLong()), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 34.sp)
        Pie("Leyendo, según Marfil")
    } else MagVacio("Marfil aún no ha contado tiempo de este libro.")
    val avance = remember(f.ref, LaFamilia.cambios) { Cuenta.nodo(k.id, f)?.decimal("fr")?.toFloat() ?: 0f }
    if (enCurso && f.estado == 1 && avance > 0f && avance < 1f) {
        val pct = (avance * 100).toInt().coerceIn(1, 99)
        BarraAvance(avance, Modifier.padding(top = 10.dp))
        Pie(if (f.paginas > 0) "Vas por el $pct % · página ${(avance * f.paginas).toInt().coerceAtLeast(1)} de ${f.paginas}" else "Vas por el $pct %", modifier = Modifier.padding(top = 5.dp))
    } else if (f.paginas > 0) Text("${Formato.cifra(f.paginas)} páginas", color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    NotaCasilla("Se lee en Marfil (en este ordenador o en el móvil), por donde ibas.")
}

/** Una imagen de una casilla (las de internet: las que pusiste desde la galería del móvil se quedan allí), con su encuadre. */
@Composable
private fun ColumnScope.ImagenDeCasilla(c: CasillaMaqueta, filaFija: Boolean) {
    val img = rememberImagenWeb(c.imagen)
    val forma = when {
        c.proporcion > 0f -> c.proporcion.coerceIn(0.2f, 5f)
        c.forma == "vertical" -> 0.75f
        c.forma == "cuadrada" -> 1f
        c.forma == "apaisada" -> 1.4f
        img != null -> img.width.toFloat() / img.height.coerceAtLeast(1)
        else -> if (c.entera) 2.2f else 1.4f
    }
    Box(Modifier.fillMaxWidth().then(if (filaFija) Modifier.weight(1f) else Modifier.aspectRatio(forma)).clipToBounds().background(if (img == null) Sand else Paper)) {
        if (img != null) Image(img, c.titulo, Modifier.fillMaxSize().graphicsLayer {
            val z = if (c.entera) c.zoom else c.zoom.coerceAtLeast(1f)
            scaleX = z; scaleY = z
            translationX = c.ox * size.width; translationY = c.oy * size.height
        }, contentScale = if (c.entera) ContentScale.Fit else ContentScale.Crop)
    }
}
