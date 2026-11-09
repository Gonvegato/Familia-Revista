package com.gonzalo.hiedra

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun arriba(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp

/** Estado de un sistema: cuántos juegos tiene y con qué emulador se abren. */
private class EstadoSistema(val s: Sistema, val juegos: Int, val emu: String, val listo: Boolean)

/** Cómo está una consola de Hiedra en este móvil (lo que dice su casilla). */
private class EstadoConsola(val id: String, val s: Sistema, val n: Nucleos.Nucleo, val juegos: Int, val bajado: Boolean, val faltaBios: Boolean, val usaHiedra: Boolean)

/**
 * «Sistemas»: primero las consolas que juega Hiedra ella misma, en casillas (de un vistazo: cuáles tienen el núcleo ya bajado, cuáles
 * están en pruebas, a cuáles les falta la BIOS); un toque abre su emulador. Después las carpetas de ROMs y la lista de todos los
 * sistemas con el emulador de fuera que se usa en cada uno.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantSistemas(alVolver: () -> Unit, alAnadirCarpeta: () -> Unit, enPagina: Boolean = false) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val todos = Biblioteca.juegos.toList()
    val conteo = todos.filter { it.rom != null }.groupBy { it.sistema }.mapValues { it.value.size }
    var verTodos by remember { mutableStateOf(false) }
    var busca by remember { mutableStateOf("") }
    var abierto by remember { mutableStateOf<String?>(null) }
    var escaneando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val lista = remember(conteo, verTodos, busca, abierto) {
        Catalogo.sistemas.filter { s -> (verTodos || busca.isNotBlank() || (conteo[s.id] ?: 0) > 0) && (busca.isBlank() || Catalogo.norm(s.nombre).contains(Catalogo.norm(busca)) || s.id == Catalogo.norm(busca)) }
            .map { s ->
                val e = Lanzar.elegirSistema(ctx, s.id)
                EstadoSistema(s, conteo[s.id] ?: 0, if (e == null) "Sin comandos" else if (e.resultado is Resultado.Ok) e.etiqueta else "Sin emulador instalado", e?.resultado is Resultado.Ok)
            }
    }
    // Las de siempre y, si tienes juegos en una carpeta con otro nombre de la misma consola («sfc», «genesis»…), también esa. Se mira
    // fuera del hilo de la pantalla (cada consola pregunta a Android por sus emuladores): la página entra sin esperar
    fun consolasAhora(conEmuladores: Boolean) = (Nucleos.principales + Nucleos.catalogo.keys.filter { (conteo[it] ?: 0) > 0 }).distinct().mapNotNull { id ->
        val s = Catalogo.sistema(id) ?: return@mapNotNull null
        val n = Nucleos.para(id) ?: return@mapNotNull null
        EstadoConsola(id, s, n, conteo[id] ?: 0, Nucleos.instalado(ctx, n.id) != null, Nucleos.faltan(ctx, n).isNotEmpty(), conEmuladores && Lanzar.esInterno(Lanzar.elegirSistema(ctx, id)))
    }
    val consolas by produceState(initialValue = remember { consolasAhora(false) }, conteo, abierto, Nucleos.version) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { consolasAhora(true) }
    }
    val anchoPantalla = LocalConfiguration.current.screenWidthDp
    // Las casillas: dos por fila en el móvil, más en la tablet (sin pasar de 190 dp de ancho cada una)
    val margen = if (anchoPantalla >= 700) 40.dp else 20.dp
    // (2 dp de holgura: al redondear a píxeles, dos casillas justas no cabían y bajaba una)
    val util = minOf(anchoPantalla.dp - margen * 2, 1180.dp) - 2.dp
    val porFila = maxOf(2, (util / 190.dp).toInt())
    val anchoCasilla = (util - 12.dp * (porFila - 1)) / porFila
    // Como página de Bastet (enPagina), sin cabecera propia: la cabecera y las pestañas son las de la app
    LazyColumn(Modifier.fillMaxSize().background(Paper).let { if (enPagina) it else it.statusBarsPadding() }, contentPadding = PaddingValues(start = margen, end = margen, bottom = 40.dp)) {
        item {
            Column(Modifier.widthIn(max = 1180.dp)) {
                if (enPagina) {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Antetitulo("Sistemas · ${conteo.size} con juegos", Modifier.weight(1f), color = Acento, conCuadro = false)
                        EnlaceRevista("+ Carpeta", acento = true, onClick = alAnadirCarpeta)
                    }
                    Filete(2.dp, modifier = Modifier.padding(top = 8.dp))
                } else {
                    RevistaTopBar(alVolver, "Bastet") { EnlaceRevista("+ Carpeta", acento = true, onClick = alAnadirCarpeta) }
                    Antetitulo("Sección · ${Catalogo.sistemas.size} sistemas", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
                    FitTitle("Sistemas", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
                    Filete(3.dp)
                }
                Text("Bastet juega ella misma a ${Nucleos.principales.size} consolas: baja el núcleo la primera vez, guarda donde lo dejas al salir y lo sube a tu nube para seguir en otro móvil. " +
                    "Para el resto, abre los emuladores que ya tienes instalados.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(vertical = 12.dp))
                Filete(2.dp)
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Antetitulo("Se juegan en Bastet") }
                    Text("${consolas.count { it.bajado }} BAJADAS", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    consolas.forEach { e -> CasillaConsola(e, anchoCasilla) { abierto = e.id } }
                }
                Text("«En pruebas»: núcleos que aún no se han podido probar en un móvil de verdad (3D, discos, arcade). Si tienes otro emulador para ese sistema, «Automático» sigue usándolo; Bastet, cuando la elijas.",
                    color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp, bottom = 18.dp))
                Filete(2.dp)
                Spacer(Modifier.height(12.dp))
                Antetitulo("Carpetas de ROMs", Modifier.padding(bottom = 6.dp))
                if (Biblioteca.carpetas.isEmpty()) Text("Todavía no has añadido ninguna. Añade la carpeta donde guardas tus ROMs y Bastet la recorre.", color = Muted, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp)
                else Biblioteca.carpetas.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Text("QUITAR", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.tocable { Biblioteca.carpetas.remove(c); Biblioteca.guardar() }.padding(8.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    EnlaceRevista("Añadir carpeta →", acento = true, onClick = alAnadirCarpeta)
                    if (Biblioteca.carpetas.isNotEmpty()) {
                        if (escaneando) CircularProgressIndicator(Modifier.size(20.dp), color = Acento, strokeWidth = 2.dp)
                        else EnlaceRevista("Buscar nuevos →") {
                            escaneando = true
                            scope.launch { val i = reescanearCarpetas(ctx); escaneando = false; aviso = if (i.anadidos == 0) "No hay juegos nuevos" else "${i.anadidos} juegos nuevos" }
                        }
                    }
                }
                aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp) }
                Spacer(Modifier.height(10.dp))
                Filete(2.dp)
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) { Antetitulo("Todos los sistemas") }
                CampoBusquedaRevista(busca, "Buscar un sistema") { busca = it }
                Filete(1.dp, Line)
            }
        }
        items(lista, key = { it.s.id }) { e ->
            FilaRevista(e.s.nombre, "${if (e.juegos == 0) "sin juegos" else if (e.juegos == 1) "1 juego" else "${e.juegos} juegos"} · ${e.emu}",
                modifier = Modifier.widthIn(max = 1180.dp),
                derecha = { Text(if (e.listo) "LISTO" else "FALTA", color = if (e.listo) Charcoal else Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.padding(start = 10.dp)) }) { abierto = e.s.id }
        }
        item {
            if (busca.isBlank()) Text((if (verTodos) "Ver solo los míos" else "Ver los ${Catalogo.sistemas.size} sistemas").uppercase() + " →", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.4.sp,
                modifier = Modifier.tocable { verTodos = !verTodos }.padding(vertical = 18.dp))
        }
    }
    abierto?.let { SelectorEmulador(it, null) { abierto = null } }
}

/**
 * Una consola de Hiedra en su casilla: una franja de su color, su nombre corto en grande (como el lomo de una caja), el nombre entero y
 * cómo está: «Bajado», «Se baja al jugar», «En pruebas», «Falta la BIOS»; y cuántos juegos tienes de ella.
 */
@Composable
private fun CasillaConsola(e: EstadoConsola, ancho: Dp, alAbrir: () -> Unit) {
    val estado = when {
        e.faltaBios -> "Falta la BIOS"
        e.bajado -> if (e.usaHiedra) "Lista · se juega aquí" else "Bajada"
        else -> "Se baja al jugar · " + Nucleos.megas(e.n.pesoAprox + (e.n.recursos?.pesoAprox ?: 0L))
    }
    Column(Modifier.width(ancho).testTag("consola-${e.id}").clip(esquina(4.dp)).border(1.dp, Line, esquina(4.dp)).background(Crudo).tocable(fuera = 3.dp, onClick = alAbrir)) {
        Box(Modifier.fillMaxWidth().height(5.dp).background(e.s.color))
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(e.s.corto, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = 0.sp, maxLines = 1, modifier = Modifier.weight(1f))
                if (e.n.pruebas) Text("PRUEBAS", color = Acento, fontFamily = Body, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 6.dp).border(1.dp, Acento, esquina(2.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
            }
            Text(e.s.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Text(e.n.nombre.uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (e.bajado && !e.faltaBios) { Box(Modifier.size(6.dp).clip(esquina(3.dp)).background(Color(0xFF3E8E5A))); Spacer(Modifier.width(5.dp)) }
                Text(estado.uppercase(), color = if (e.faltaBios) Acento else Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (e.juegos > 0) Text(if (e.juegos == 1) "1 JUEGO" else "${e.juegos} JUEGOS", color = Acento, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
