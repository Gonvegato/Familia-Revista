package com.gonzalo.cobalto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** El alto del velo de arriba de la apertura (para que se lean «Volver» y las acciones sobre cualquier foto). */
val VELO_APERTURA = 110.dp

/** El velo: negro al 80 % arriba, transparente abajo. */
fun veloArriba() = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.35f), Color.Transparent))

/** El encabezado de una sección de la ficha: su número (01, 02…) en el acento y el nombre en grotesca; a la derecha, su acción si la tiene. */
@Composable
private fun SeccionNumerada(n: Int, texto: String, accion: Pair<String, () -> Unit>? = null, arriba: Dp = 26.dp) {
    Row(Modifier.fillMaxWidth().padding(top = arriba, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text("%02d".format(n), color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1)
        Text(texto, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp, bottom = 1.dp))
        accion?.let { (t, f) -> Text(t, color = Acento, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.clickable(onClick = f).padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) }
    }
}

/** Dos casillas de revista, una al lado de la otra, con su filete en medio. */
@Composable
private fun DosCasillas(izq: @Composable ColumnScope.() -> Unit, der: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.weight(1f).padding(top = 16.dp, bottom = 18.dp, end = 14.dp), content = izq)
        Box(Modifier.fillMaxHeight().width(2.dp).background(Charcoal))
        Column(Modifier.weight(1f).padding(top = 16.dp, bottom = 18.dp, start = 16.dp), content = der)
    }
}

/** Una casilla con una cifra grande (y su etiqueta y su nota), que se toca para ir a lo suyo. */
@Composable
private fun ColumnScope.Casilla(titulo: String, cifra: String, etiqueta: String, nota: String, alTocar: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = alTocar)) {
        Text(titulo, color = Charcoal, fontFamily = Grotesca, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        FitTitle(cifra, 40.sp, 42.sp, unaLinea = true, maxLines = 1, family = Grotesca, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
        Text(etiqueta, color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        if (nota.isNotBlank()) Text(nota, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text("ABRIR →", color = Acento, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    }
}

/** El texto del botón principal de la ficha según la obra. */
fun textoPrincipal(e: Escritos, o: Doc): String = when {
    estadoDe(o) == EstadoObra.TERMINADA -> "Leer en Seshat"
    e.palabrasObra(o.id) == 0 -> "Empezar a escribir"
    else -> "Seguir escribiendo"
}

/**
 * La ficha de una obra, en «doble página» (como la de un libro en Seshat): la apertura es su fondo o su portada ampliada (sin letras), con un
 * velo arriba (Volver · Imágenes · Editar · Borrar); la etiqueta del estado pisando su borde, el título enorme, «<género> · <n> palabras», y
 * el botón principal (SEGUIR ESCRIBIENDO / EMPEZAR A ESCRIBIR / LEER EN SESHAT). Debajo, las secciones numeradas: 01 Tu escritura (y su
 * estado), 02 Capítulos, 03 Fichas y notas, 04 Línea temporal, 05 El estudio (árbol, cifras, guía, sprint) y 06 Datos.
 * [alEstudio] abre el estudio (en una escena, en un capítulo o en una pestaña); [alSprint], un sprint.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantFicha(id: String, alCerrar: () -> Unit, alEstudio: (escena: String?, capitulo: String?, pestana: Int?) -> Unit, alSprint: () -> Unit) {
    val ctx = LocalContext.current
    val e = remember { Escritos.get(ctx) }
    val ver = e.version
    val v = ImagenesObra.version
    val o = e.get(id)
    if (o == null) { LaunchedEffect(Unit) { alCerrar() }; return }
    var imagenes by remember { mutableStateOf<Boolean?>(null) }   // null: cerrada; true: abre en el fondo
    var editar by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf(false) }
    val palabras = remember(ver, id) { e.palabrasObra(id) }
    val caps = remember(ver, id) { e.capitulos(id) }
    val escenas = remember(ver, id) { e.escenasDe(id) }
    val fichas = remember(ver, id) { e.fichas(id) }
    val notas = remember(ver, id) { e.notas(id) }
    val hitos = remember(ver, id) { e.hitos(id) }
    val hoy = remember(ver, id) { e.escritas(Escritos.hoy(), id) }
    val dias = remember(ver, id) { e.diasEscritos(id) }
    val ultima = remember(ver, id) { e.ultimaEscenaDe(id) }
    val terminados = remember(ver, id) { caps.count { Compartir.hecho(e, it) } }
    val estado = estadoDe(o)
    val meta = o.i("meta")
    val titulo = o.s("titulo").ifBlank { "Sin título" }
    var nSec = 0
    fun sig() = ++nSec
    fun principal() = when {
        estado == EstadoObra.TERMINADA && Hermanas.hayMarfil(ctx) -> Hermanas.leerEnMarfil(ctx, id)
        palabras == 0 -> alEstudio(escenas.firstOrNull()?.id, null, null)
        else -> alEstudio(ultima?.id, null, null)
    }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        // ---- «Doble página»: el fondo, o la tapa ampliada (sin letras) como foto de apertura
        val base = baseDeObra(o)
        val fondo = remember(o.s(ImagenesObra.FONDO), v) { ImagenesObra.fondo(ctx, o) }
        val conFondo = fondo.isNotBlank()
        Box(Modifier.fillMaxWidth().height(ALTO_APERTURA).clipToBounds().background(Brush.verticalGradient(listOf(lerpColor(base, Color.White, 0.12f), lerpColor(base, Color.Black, 0.55f))))) {
            if (conFondo) ImagenEncuadrada(fondo, remember(fondo, v) { ImagenesObra.encFondo(ctx, o) }, Modifier.fillMaxSize().clickable { imagenes = true }, "Fondo de $titulo")
            else BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth + 24.dp
                PortadaObra(o, Modifier.align(Alignment.TopCenter).padding(top = 30.dp).requiredWidth(w).requiredHeight(w * 1.5f).clickable { imagenes = false }, conTitulo = false)
            }
            Box(Modifier.fillMaxWidth().height(VELO_APERTURA).background(veloArriba()))
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                EnlaceRevista("← Volver", color = PAPEL_CLARO) { alCerrar() }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    EnlaceRevista("Imágenes", color = PAPEL_CLARO) { imagenes = false }
                    EnlaceRevista("Editar", color = PAPEL_CLARO) { editar = true }
                    EnlaceRevista("Borrar", color = PAPEL_CLARO) { borrar = true }
                }
            }
        }
        // La etiqueta del estado, en el acento, pisando el borde de la foto; con fondo, la portada asoma abajo a la derecha
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(start = 20.dp).offset(y = (-16).dp).background(Acento).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(etiquetaEstado(e, o), color = SobreAcento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                }
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = if (conFondo) 156.dp else 20.dp)) {
                    FitTitle(titulo.uppercase(), 38.sp, 40.sp, color = Charcoal, family = Grotesca, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, maxLines = 4, minSize = 20f)
                    val de = listOfNotNull(o.s("genero").ifBlank { null }, "${cifraE(palabras)} palabras").joinToString(" · ")
                    Text(de, color = Muted, fontFamily = Body, fontSize = 17.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (conFondo) Box(Modifier.align(Alignment.TopEnd).padding(end = 20.dp).width(120.dp).height(116.dp)) {
                PortadaSobreFondo(o, Modifier.align(Alignment.BottomCenter).wrapContentHeight(Alignment.Bottom, unbounded = true).requiredHeight(180.dp).fillMaxWidth(), sombra = 14.dp,
                    alTocar = { imagenes = false })
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            BotonC(textoPrincipal(e, o), fondo = Acento, letra = SobreAcento) { principal() }
            if (estado == EstadoObra.TERMINADA) {
                Spacer(Modifier.height(8.dp))
                BotonC("Abrir el manuscrito", fondo = Paper, letra = Charcoal, modifier = Modifier.drawBehind { drawRect(Charcoal, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())) }) {
                    alEstudio(null, null, 0)
                }
                if (!Hermanas.hayMarfil(ctx)) Text("Seshat no está en este móvil: instálala para leer aquí tus capítulos terminados con su lector.", color = Muted, fontFamily = Body, fontSize = 12.sp,
                    lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
            }

            // ---- 01 Tu escritura: la barra de la meta, lo de hoy y en qué página está (cambiar de estado)
            SeccionNumerada(sig(), "Tu escritura")
            if (meta > 0) Box(Modifier.fillMaxWidth().height(8.dp).background(Line)) { Box(Modifier.fillMaxWidth(e.avance(o)).fillMaxHeight().background(Acento)) }
            val linea = listOfNotNull(
                if (meta > 0) "${cifraE(palabras)} de ${cifraE(meta)} palabras · ${(palabras * 100L / meta).coerceAtMost(999)} %" else "${cifraE(palabras)} palabras",
                if (hoy > 0) "${cifraE(hoy)} hoy" else null,
            ).joinToString(" · ")
            Text(linea, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 7.dp))
            val otra = listOfNotNull(
                if (dias > 0) "$dias ${if (dias == 1) "día escrito" else "días escritos"}" else null,
                "editada ${haceE(e.ultimaEdicion(id))}",
                o.i("diaria").takeIf { it > 0 }?.let { "su meta diaria: ${cifraE(it)}" },
            ).joinToString(" · ")
            Text(otra, color = Muted, fontFamily = Body, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).drawBehind { drawRect(Charcoal, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())) }) {
                EstadoObra.entries.forEach { x ->
                    val on = x == estado
                    Box(Modifier.weight(1f).fillMaxHeight().background(if (on) Charcoal else Paper).clickable { e.ponEstado(id, x) }.padding(vertical = 12.dp, horizontal = 2.dp), contentAlignment = Alignment.Center) {
                        FitTitle((if (x == EstadoObra.ARCHIVO) "Archivo" else x.nombre).uppercase(), 11.sp, 13.sp, color = if (on) Paper else Charcoal, family = Body, fontWeight = FontWeight.Black,
                            letterSpacing = 0.4.sp, maxLines = 1, unaLinea = true, minSize = 6f, align = TextAlign.Center)
                    }
                }
            }

            // ---- 02 Capítulos: los capítulos con su número, sus palabras y si están terminados; un toque abre el capítulo
            SeccionNumerada(sig(), "Capítulos", "Manuscrito →" to { alEstudio(null, null, 0) })
            Text("${caps.size} ${if (caps.size == 1) "capítulo" else "capítulos"} · ${escenas.size} ${if (escenas.size == 1) "escena" else "escenas"}" +
                if (terminados > 0) " · $terminados ${if (terminados == 1) "terminado" else "terminados"}" else "",
                color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
            if (caps.isEmpty()) Text("Aún no tiene capítulos: empieza a escribir y crea el primero.", color = Muted, fontFamily = Body, fontSize = 15.sp)
            val vistos = caps.take(8)
            vistos.forEachIndexed { i, c ->
                Filete(1.dp, Line)
                val pc = remember(ver, c.id) { e.palabrasCapitulo(c.id) }
                val hecho = Compartir.hecho(e, c)
                Row(Modifier.fillMaxWidth().clickable { alEstudio(null, c.id, null) }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("%02d".format(i + 1), color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, modifier = Modifier.width(34.dp))
                    Text(c.s("titulo").ifBlank { "Sin título" }, color = Charcoal, fontFamily = Grotesca, fontWeight = if (ultima?.s("cid") == c.id) FontWeight.Bold else FontWeight.Bold,
                        fontSize = 16.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                        Text(cifraE(pc), color = Muted, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                        if (hecho) Text("✓ TERMINADO", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                        else if (ultima?.s("cid") == c.id) Text("Por aquí", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
            if (caps.size > vistos.size) EnlaceRevista("Ver los ${caps.size} capítulos →", acento = true) { alEstudio(null, null, 0) }
            if (terminados > 0 && Hermanas.hayMarfil(ctx) && estado != EstadoObra.TERMINADA)
                EnlaceRevista("Leer en Seshat ($terminados ${if (terminados == 1) "capítulo terminado" else "capítulos terminados"})", acento = true) { Hermanas.leerEnMarfil(ctx, id) }
        }

        // ---- 03 Fichas y notas
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            SeccionNumerada(sig(), "Fichas y notas")
            Filete(1.dp, Line)
            DosCasillas(
                izq = {
                    val per = fichas.count { it.s("tipo") == "personaje" }
                    Casilla("Fichas", "${fichas.size}", if (fichas.size == 1) "ficha" else "fichas",
                        if (fichas.isEmpty()) "Personajes, lugares, objetos…" else listOfNotNull(if (per > 0) "$per ${if (per == 1) "personaje" else "personajes"}" else null,
                            fichas.take(3).joinToString(", ") { it.s("nombre").ifBlank { "Sin nombre" } } + if (fichas.size > 3) "…" else "").joinToString(" · ")) { alEstudio(null, null, 1) }
                },
                der = {
                    Casilla("Notas", "${notas.size}", if (notas.size == 1) "nota" else "notas",
                        if (notas.isEmpty()) "Ideas, resúmenes, investigación…" else notas.take(2).joinToString(", ") { it.s("titulo").ifBlank { "Sin título" } } + if (notas.size > 2) " y ${notas.size - 2} más" else "") { alEstudio(null, null, 3) }
                }
            )
            Filete(1.dp, Line)

            // ---- 04 Línea temporal
            SeccionNumerada(sig(), "Línea temporal", "Abrir →" to { alEstudio(null, null, 4) })
            if (hitos.isEmpty()) Text("Sin sucesos todavía: en la línea temporal ordenas lo que pasa en la historia.", color = Muted, fontFamily = Body, fontSize = 15.sp, lineHeight = 20.sp)
            hitos.take(4).forEach { h ->
                Row(Modifier.fillMaxWidth().clickable { alEstudio(null, null, 4) }.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp).size(8.dp).background(Acento))
                    Text(listOfNotNull(h.s("cuando").ifBlank { null }?, h.s("titulo").ifBlank { "Sin título" }).joinToString(" · "), color = Charcoal, fontFamily = Body,
                        fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                }
            }
            if (hitos.size > 4) Text("y ${hitos.size - 4} más", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))

            // ---- 05 El estudio: el resto de pestañas y el sprint
            SeccionNumerada(sig(), "El estudio")
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonMarco("Árbol") { alEstudio(null, null, TAB_ARBOL) }
                BotonMarco("Cifras") { alEstudio(null, null, 5) }
                BotonMarco("Guía") { alEstudio(null, null, TAB_GUIA) }
                BotonMarco("Sprint") { alSprint() }
            }

            // ---- 06 Datos
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)) {
                SeccionNumerada(sig(), "Datos", "Editar" to { editar = true }, arriba = 10.dp)
                MagFact("Título", titulo)
                if (o.s("autor").isNotBlank()) MagFact("Autor", o.s("autor"))
                MagFact("Género", o.s("genero").ifBlank { "—" })
                MagFact("Meta", if (meta > 0) "${cifraE(meta)} palabras" else "sin meta")
                if (o.i("diaria") > 0) MagFact("Al día", "${cifraE(o.i("diaria"))} palabras")
                MagFact("Estado", estado.nombre)
                o.n("creado").takeIf { it > 0 }?.let { MagFact("Creada", Formato.fecha(it)) }
                if (o.s("sinopsis").isNotBlank()) Text(o.s("sinopsis"), color = Muted, fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    imagenes?.let { f -> SelectorImagenes(id, fondo = f) { imagenes = null } }
    if (editar) ObraDialogo(e, o, onCerrar = { editar = false })
    if (borrar) AlertDialog(
        onDismissRequest = { borrar = false }, containerColor = Crudo, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = { Text("¿Borrar «$titulo»?", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold) },
        text = { Text("Irá a la papelera con sus capítulos, escenas, fichas y notas, también en tus otros dispositivos. Podrás recuperarla durante 30 días (está al final de Archivo).", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({ borrar = false; aLaPapelera(e, o); EscribirHub.obraBorrada = id; alCerrar() }) { Text("A la papelera", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton({ borrar = false }) { Text("Cancelar", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold) } }
    )
}
