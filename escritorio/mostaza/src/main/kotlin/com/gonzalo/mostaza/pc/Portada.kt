package com.gonzalo.mostaza.pc

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.Actualizador
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * La portada (como la del móvil): con tu clave de TMDB, la de una plataforma (el destacado, «Seguir viendo», «Pendientes» y las filas de
 * TMDB: tendencias, «Porque viste…», estrenos, lo que hay en cada plataforma…); sin ella, los catálogos de tus addons.
 */
@Composable
fun PantPortada(nav: Nav) {
    val titulos = Biblioteca.titulos()
    val viendo = Biblioteca.seguirViendo(titulos)
    val pendientes = Biblioteca.pendientes(titulos)
    val conTmdb = Tmdb.activo
    val porqueViste = titulos.filter { it.estado == Estado.VISTO || it.estado == Estado.VIENDO }.maxByOrNull { maxOf(it.ultima, it.fin) }?.breve()
    val filasTmdb by produceState<List<FilaTmdb>?>(PortadaFija.tmdb, conTmdb, porqueViste?.id) {
        if (PortadaFija.tmdb != null) return@produceState
        value = if (!conTmdb) emptyList() else withContext(Dispatchers.IO) { runCatching { Tmdb.portada(porqueViste) }.getOrDefault(emptyList()) }
    }
    val filas by produceState<List<FilaCatalogo>?>(PortadaFija.addons, Addons.version, conTmdb) {
        if (PortadaFija.addons != null) return@produceState
        value = if (conTmdb) emptyList() else withContext(Dispatchers.IO) { runCatching { Catalogos.portada() }.getOrDefault(emptyList()) }
    }
    // Lo que tienes en tu kDrive («Mi carpeta/Mostaza»), con su ficha
    val enKDrive by produceState(emptyList<MetaBreve>(), CineKDrive.version, conTmdb) {
        value = withContext(Dispatchers.IO) { runCatching {
            CineKDrive.indice()
            kotlinx.coroutines.coroutineScope { CineKDrive.titulos().take(40).map { (id, tipo) -> async { Fichas.carga(tipo, id)?.first?.breve() } }.awaitAll().filterNotNull() }
        }.getOrDefault(emptyList()) }
    }
    val hoy = remember { LocalDate.now() }
    Pagina {
        Cabecera(132.sp, texto = "HATHOR")
        FileteCabecera()
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("Nº ${hoy.dayOfYear}", color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(hoy.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-ES"))).uppercase(), color = Charcoal, fontFamily = Body, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Filete(1.dp)
        if (conTmdb) filasTmdb?.firstOrNull()?.cosas?.firstOrNull { it.fondo.isNotBlank() }?.let { Destacado(it) { b -> nav.abre(b) } }
        if (viendo.isNotEmpty()) FilaPosters("Seguir viendo", viendo.map { it.breve() }, { nav.abre(it) }, verTodo = { nav.ir(Pantalla.Seccion(Estado.VIENDO)) },
            sub = { b -> viendo.firstOrNull { it.idStremio == b.id }?.let { seguirTexto(it) }.orEmpty() },
            progreso = { b -> viendo.firstOrNull { it.idStremio == b.id }?.let { Biblioteca.ultimo(it.ref)?.takeIf { p -> !p.visto }?.fraccion } },
            portada = { b -> viendo.firstOrNull { it.idStremio == b.id }?.portada.orEmpty() })
        if (pendientes.isNotEmpty()) FilaPosters("Pendientes", pendientes.take(20).map { it.breve() }, { nav.abre(it) }, verTodo = { nav.ir(Pantalla.Seccion(Estado.PENDIENTE)) },
            sub = { b -> tipoNombre(b.tipo) + if (b.ano.isNotBlank()) " · ${b.ano}" else "" }, portada = { b -> pendientes.firstOrNull { it.idStremio == b.id }?.portada.orEmpty() })
        if (enKDrive.isNotEmpty()) FilaPosters("En tu kDrive", enKDrive, { nav.abre(it) })
        if (conTmdb) when (val f = filasTmdb) {
            null -> Nota("Mirando el catálogo…", Modifier.padding(top = 26.dp))
            else -> {
                if (f.isEmpty()) Nota("TMDB no responde ahora mismo (¿sin conexión?, ¿la clave ya no vale?). Mira en Ajustes → Catálogo.", Modifier.padding(top = 26.dp))
                f.forEach { fila -> FilaPosters(fila.titulo, fila.cosas.take(30), { nav.abre(it) }) }
            }
        } else when (val f = filas) {
            null -> Nota("Mirando tus catálogos…", Modifier.padding(top = 26.dp))
            else -> {
                Nota("Para que Hathor tenga su propio catálogo, como una plataforma, pon tu clave de TMDB (gratis) en Ajustes → Catálogo.", Modifier.padding(top = 22.dp).pulsable { nav.ir(Pantalla.Ajustes()) })
                if (f.isEmpty()) Nota("Tus addons no dan ningún catálogo ahora mismo (sin conexión, o no tienen). Añade addons en «Addons».", Modifier.padding(top = 26.dp))
                f.forEach { fila -> FilaPosters(fila.titulo, fila.cosas.take(30), { nav.abre(it) }) }
            }
        }
        Text("Hathor · hecho en casa", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 30.dp))
        Text("versión ${Actualizador.version}", color = Muted.copy(alpha = 0.7f), fontFamily = Body, fontSize = 10.sp, letterSpacing = 0.6.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
    }
}

/** En las pruebas de pantallas: la portada sin red. */
object PortadaFija {
    @Volatile var tmdb: List<FilaTmdb>? = null
    @Volatile var addons: List<FilaCatalogo>? = null
}

/** Una rejilla de pósteres con su cabecera (las secciones, «Explorar»). */
@Composable
fun Rejilla(cabecera: @Composable ColumnScope.() -> Unit, cosas: List<Pair<String, @Composable () -> Unit>>, pie: (@Composable () -> Unit)? = null) {
    val estado = rememberLazyGridState()
    Box(Modifier.fillMaxSize().background(Paper)) {
        LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), estado, contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 30.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { Column(Modifier.fillMaxWidth(), content = cabecera) }
            items(cosas, key = { it.first }) { it.second() }
            if (pie != null) item(span = { GridItemSpan(maxLineSpan) }) { pie() }
        }
        VerticalScrollbar(rememberScrollbarAdapter(estado), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
    }
}

/** Las secciones de tu biblioteca: Viendo · Pendiente · Visto · Abandonado, cada una con sus películas y series, en rejilla. */
@Composable
fun PantSeccion(estado: Estado, nav: Nav) {
    val secciones = listOf(Estado.VIENDO, Estado.PENDIENTE, Estado.VISTO, Estado.ABANDONADO)
    val orden = OrdenGuardado.de(estado)
    val filtro = OrdenGuardado.filtro(estado)
    val todos = Biblioteca.titulos()
    val lista = todos.filter { it.estado == estado }
        .filter { when (filtro) { OrdenGuardado.Filtro.PELICULAS -> !it.esSerie; OrdenGuardado.Filtro.SERIES -> it.esSerie; else -> true } }
        .let { l -> when (orden) {
            OrdenGuardado.Orden.RECIENTES -> l.sortedByDescending { maxOf(it.ultima, it.fin, it.anadido) }
            OrdenGuardado.Orden.ANADIDOS -> l.sortedByDescending { it.anadido }
            OrdenGuardado.Orden.TITULO -> l.sortedBy { it.nombre.lowercase() }
            OrdenGuardado.Orden.ANO -> l.sortedByDescending { it.ano.take(4) }
        } }
    Rejilla({
        CabeceraPagina("Tu biblioteca", estado.nombre)
        PestanasRevista(secciones.map { it.nombre }, secciones.indexOf(estado), cuentas = secciones.map { s -> todos.count { it.estado == s } }) { i -> nav.ir(Pantalla.Seccion(secciones[i])) }
        Spacer(Modifier.height(6.dp))
        FiltrosRevista(OrdenGuardado.Filtro.entries.map { it.nombre }, filtro.ordinal) { i -> OrdenGuardado.ponFiltro(estado, OrdenGuardado.Filtro.entries[i]) }
        Row(verticalAlignment = Alignment.Top) {
            Text("ORDEN  ", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 9.dp))
            FiltrosRevista(OrdenGuardado.Orden.entries.map { it.nombre }, orden.ordinal) { i -> OrdenGuardado.pon(estado, OrdenGuardado.Orden.entries[i]) }
        }
        Filete(1.dp, Line)
        if (lista.isEmpty()) Nota(when (estado) {
            Estado.VIENDO -> "Nada a medias. Lo que ves entra aquí solo cuando llevas 10 minutos viéndolo."
            Estado.PENDIENTE -> "Nada pendiente. En la ficha de cualquier película o serie, «+ Añadir»."
            Estado.VISTO -> "Aún no has terminado nada aquí. Una película está vista al 90 %; una serie, con todos sus episodios estrenados vistos."
            Estado.ABANDONADO -> "Nada abandonado."
        }, Modifier.padding(top = 18.dp))
    }, lista.map { t ->
        t.ref to @Composable {
            val p = Biblioteca.ultimo(t.ref)
            TarjetaPoster(t.portada, t.poster, t.nombre, Dp.Unspecified, if (estado == Estado.VIENDO) seguirTexto(t) else listOf(tipoNombre(t.tipo), t.ano).filter { it.isNotBlank() }.joinToString(" · "),
                if (estado == Estado.VIENDO && p != null && !p.visto) p.fraccion else null) { nav.abre(t.breve()) }
        }
    })
}
