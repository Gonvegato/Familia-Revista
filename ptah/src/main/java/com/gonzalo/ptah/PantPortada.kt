package com.gonzalo.ptah

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/*
 * La portada: el nombre de la app y los ajustes, «Tus widgets» con la miniatura de cada diseño, «+ Nuevo» y,
 * para empezar, las plantillas. Al poner un widget en la pantalla de inicio es la misma, para elegir cuál va allí.
 */

/** La explicación de la primera vez (3 pasos): se cierra y no vuelve. Es de este móvil. */
object Intro {
    private fun p(ctx: android.content.Context) = ctx.getSharedPreferences("ptah_ui", android.content.Context.MODE_PRIVATE)
    private val ver = mutableIntStateOf(0)
    fun vista(ctx: android.content.Context): Boolean { ver.intValue; return p(ctx).getBoolean("intro", false) }
    fun cierra(ctx: android.content.Context) { p(ctx).edit().putBoolean("intro", true).apply(); ver.intValue++ }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PantPortada(eligiendo: Boolean, guardado: String? = null, alCerrarGuardado: () -> Unit = {}, alAbrir: (Diseno) -> Unit, alPlantilla: (Diseno) -> Unit, alNuevo: () -> Unit, alAjustes: () -> Unit) {
    val ctx = LocalContext.current
    val mg = margen()
    val disenos = Disenos.todos(ctx)
    val usos = remember(disenos) { Widgets.usos(ctx) }
    val cols = if (esAncha()) 3 else 2
    val recien = guardado?.let { id -> disenos.firstOrNull { it.id == id } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = mg)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Cabecera(30.sp, Modifier.weight(1f))
            if (!eligiendo) BotonIcono(Icons.Outlined.Settings, "Ajustes") { alAjustes() }
        }
        if (Migracion.aviso.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Tarjeta(borde = Acento) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(Migracion.aviso, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                    Text("Vale", color = Acento, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp).tocable { Migracion.olvidaAviso(ctx) })
                }
                Text("Desde ahora todo se guarda solo en este móvil.", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (eligiendo) {
            Text("¿Qué va en este widget?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, modifier = Modifier.padding(top = 14.dp))
            Text("Un toque en uno de tus diseños y se pone. O empieza con una plantilla, o crea uno nuevo: al guardarlo, va a este widget.",
                color = Muted, fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
        } else {
            Text("Tus widgets, a tu manera. Un toque y lo cambias.", color = Muted, fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 2.dp))
            if (!Intro.vista(ctx)) Explicacion { Intro.cierra(ctx) }
        }

        // ---- Si los relojes no pueden ir con tu letra, o se quedan atrás: dicho aquí, con los botones para arreglarlo
        if (!eligiendo) AvisoReloj()

        // ---- Lo que se acaba de guardar
        if (recien != null && !eligiendo) Guardado(recien, usos[recien.id] ?: 0, alCerrarGuardado)

        // ---- Tus diseños
        Spacer(Modifier.height(10.dp)); Filete(3.dp); Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Antetitulo("Tus diseños", Modifier.weight(1f))
            if (disenos.isNotEmpty()) Text("${disenos.size}", color = Acento, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }
        Text(if (eligiendo) "Los que has hecho." else "Los que has hecho. Un toque en uno: ponerlo en la pantalla de inicio, editarlo, copiarlo o borrarlo.",
            color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        if (disenos.isEmpty()) Nota("Aún no tienes ninguno: crea uno o empieza con una plantilla de las de abajo.", Modifier.padding(top = 8.dp))
        Rejilla(disenos, cols) { d ->
            val t = Tipos.de(d.tipo)
            val n = usos[d.id] ?: 0
            Tarjeta(d, Plantillas.celdasDe(d), t?.nombre ?: "De otra versión", if (n > 0) "En tu pantalla de inicio: $n" else "Aún no está en tu pantalla",
                enPantalla = n > 0, resaltada = d.id == guardado) { alAbrir(d) }
        }
        Spacer(Modifier.height(16.dp))
        AccionGrande(null, "+ Crear uno nuevo", Modifier.fillMaxWidth()) { alNuevo() }

        // ---- Plantillas para empezar (aparte: sobre su banda de color)
        Spacer(Modifier.height(26.dp))
        Column(Modifier.fillMaxWidth().background(Sand).padding(horizontal = 14.dp, vertical = 16.dp)) {
            Antetitulo("Plantillas para empezar")
            Text("Ya hechas. Un toque en una y se hace una copia tuya que puedes cambiar; al guardarla, va a «Tus diseños».",
                color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
            Rejilla(Plantillas.TODAS, cols) { (p, c) -> Tarjeta(p, c, Tipos.de(p.tipo)?.nombre.orEmpty(), "Usar esta →", enPantalla = false, resaltada = false, deSerie = true) { alPlantilla(p) } }
        }

        Spacer(Modifier.height(26.dp)); Filete(1.dp); Spacer(Modifier.height(10.dp))
        Etiqueta("Widgets $VERSION")
        Text(OpenMeteo.LICENCIA, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(30.dp))
    }
}

/** Cómo va esto, en tres pasos (la primera vez). */
@Composable
private fun Explicacion(alCerrar: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).border(1.5.dp, Charcoal).padding(16.dp)) {
        Antetitulo("Cómo va")
        listOf(
            "Crea un diseño o empieza con una plantilla" to "Un reloj, el tiempo, tus apps, una foto… con tus colores y tus letras.",
            "Guárdalo" to "Va a «Tus diseños», en este móvil.",
            "Ponlo en tu pantalla de inicio" to "Con «Ponerlo en la pantalla de inicio» o, desde el lanzador, en Widgets → Widgets.",
        ).forEachIndexed { i, (t, x) ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("${i + 1}", color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 30.sp, modifier = Modifier.width(34.dp))
                Column(Modifier.weight(1f)) {
                    Text(t, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp)
                    Text(x, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
        EnlaceRevista("Entendido", Modifier.padding(top = 6.dp), acento = true) { alCerrar() }
    }
}

/** «Guardado en Tus diseños», con el botón para ponerlo en la pantalla de inicio. */
@Composable
private fun Guardado(d: Diseno, usos: Int, alCerrar: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp).background(Acento.copy(alpha = 0.12f)).border(2.dp, Acento).padding(16.dp)) {
        Text("✓ Guardado en Tus diseños", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
        Text("«${d.nombre.ifBlank { "Sin nombre" }}»" + if (usos > 0) " (y ya puesto en $usos widget${if (usos > 1) "s" else ""}: se ha actualizado)" else "",
            color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        PonerEnPantalla(d)
        EnlaceRevista("Cerrar", Modifier.padding(top = 4.dp)) { alCerrar() }
    }
}

/** Unas tarjetas en [cols] columnas (todas a la vista: la página entera se desliza). */
@Composable
private fun <T> Rejilla(cosas: List<T>, cols: Int, tarjeta: @Composable (T) -> Unit) {
    cosas.chunked(cols).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            fila.forEach { Box(Modifier.weight(1f)) { tarjeta(it) } }
            repeat(cols - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Un diseño: su miniatura, su nombre (en dos líneas si hace falta), de qué tipo es y dónde está. */
@Composable
private fun Tarjeta(d: Diseno, c: Tamanos.Celdas, tipo: String, estado: String, enPantalla: Boolean, resaltada: Boolean, deSerie: Boolean = false, alTocar: () -> Unit) {
    Column(Modifier.fillMaxWidth().then(if (resaltada) Modifier.border(3.dp, Acento).padding(4.dp) else Modifier).tocable { alTocar() }) {
        Miniatura(d, c, Modo.oscuro, alto = 124f)
        Text(d.nombre.ifBlank { "Sin nombre" }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 20.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text("$tipo · $c", color = Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, lineHeight = 12.sp, maxLines = 3)
        Text(estado, color = if (enPantalla || deSerie) Acento else Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp,
            fontWeight = if (enPantalla || deSerie) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * «Ponerlo en la pantalla de inicio»: se le pide al lanzador un widget nuevo que ya lleva este diseño (sin pasar por elegirlo). Si el
 * lanzador no lo deja, se explica cómo ponerlo a mano.
 */
@Composable
fun PonerEnPantalla(d: Diseno, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var pasos by remember { mutableStateOf(false) }
    var pedido by remember { mutableStateOf(false) }
    AccionGrande(null, "Ponerlo en la pantalla de inicio", modifier.fillMaxWidth()) {
        if (AlFijar.puede(ctx)) { AlFijar.pide(ctx, d.id); pedido = true } else pasos = true
    }
    if (pedido) Text("Tu lanzador te pregunta dónde ponerlo. Si no sale nada, mira abajo cómo ponerlo a mano.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
    if (pedido || !AlFijar.puede(ctx)) EnlaceRevista("Cómo ponerlo a mano") { pasos = true }
    if (pasos) androidx.compose.material3.AlertDialog(onDismissRequest = { pasos = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Ponerlo a mano", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                listOf("Mantén pulsado un hueco del fondo de tu pantalla de inicio.", "Toca «Widgets».", "Busca «Widgets» y arrastra su widget a la pantalla.",
                    "Elige «${d.nombre.ifBlank { "este diseño" }}» en la lista de tus diseños.").forEachIndexed { i, t ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("${i + 1}", color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.width(26.dp))
                        Text(t, color = Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 20.sp)
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton({ pasos = false }) { Text("Entendido", color = Acento, fontFamily = Body, fontWeight = FontWeight.Bold) } })
}

/** La ficha de un diseño: cómo se ve, dónde está, y ponerlo en la pantalla de inicio, editarlo, copiarlo o borrarlo. */
@Composable
fun PantFicha(id: String, alVolver: () -> Unit, alEditar: (Diseno) -> Unit, alCopiar: (Diseno) -> Unit) {
    val ctx = LocalContext.current
    val d = Disenos.todos(ctx).firstOrNull { it.id == id }
    if (d == null) { LaunchedEffect(Unit) { alVolver() }; return }
    val t = Tipos.de(d.tipo)
    val n = remember(d) { Widgets.usos(ctx)[d.id] ?: 0 }
    var borrar by remember { mutableStateOf(false) }
    val mg = margen()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = mg)) {
        RevistaTopBar(alVolver, "Tus diseños")
        Antetitulo(t?.nombre ?: "De otra versión", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle(d.nombre.ifBlank { "Sin nombre" }, 44.sp, 48.sp, letterSpacing = (-1).sp, maxLines = 3)
        Filete(3.dp)
        Spacer(Modifier.height(12.dp))
        Miniatura(d, Plantillas.celdasDe(d), Modo.oscuro, alto = 240f)
        Text(if (n > 0) "En tu pantalla de inicio: $n widget${if (n > 1) "s" else ""}. Si lo cambias, se cambian solos." else "Aún no está en tu pantalla de inicio.",
            color = if (n > 0) Acento else Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(12.dp))
        PonerEnPantalla(d)
        Spacer(Modifier.height(10.dp))
        Filete(1.dp, Line)
        MagLink("Editar", "Los colores, las letras, lo que lleva") { alEditar(d) }
        Filete(1.dp, Line)
        MagLink("Hacer una copia", "Otro diseño igual, para cambiarlo") { alCopiar(d) }
        Filete(1.dp, Line)
        MagLink("Borrar", if (n > 0) "Los widgets que lo llevan te dejarán elegir otro" else null) { borrar = true }
        Spacer(Modifier.height(30.dp))
    }
    if (borrar) androidx.compose.material3.AlertDialog(onDismissRequest = { borrar = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Borrar «${d.nombre}»?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text("Se borra de este móvil. Los widgets que lo llevan te dejarán elegir otro.", color = Muted, fontFamily = Body) },
        confirmButton = { androidx.compose.material3.TextButton({ Disenos.borra(ctx, d.id); borrar = false; alVolver() }) { Text("Borrar", color = Acento, fontFamily = Body, fontWeight = FontWeight.Bold) } },
        dismissButton = { androidx.compose.material3.TextButton({ borrar = false }) { Text("NO", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold) } })
}

/** «+ Nuevo»: los tipos, por grupos, cada uno con su muestra y lo que hace. */
@Composable
fun PantNuevo(alVolver: () -> Unit, alElegir: (TipoWidget) -> Unit) {
    val mg = margen()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = mg)) {
        RevistaTopBar(alVolver, "Widgets")
        Antetitulo("Nuevo", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle("¿Qué widget?", 48.sp, 52.sp, letterSpacing = (-1.2).sp)
        Filete(3.dp)
        Tipos.GRUPOS.forEach { g ->
            Spacer(Modifier.height(14.dp))
            Antetitulo(g)
            Tipos.TODOS.filter { it.grupo == g }.forEach { t ->
                val muestra = remember(t) { Plantillas.TODAS.firstOrNull { it.first.tipo == t.id }?.first ?: t.nuevo() }
                Row(Modifier.fillMaxWidth().tocable { alElegir(t) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(132.dp)) { Miniatura(muestra, t.muestra, Modo.oscuro, alto = 92f) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 24.sp)
                        Text(t.descripcion, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Filete(1.dp, Line)
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

/**
 * El aviso de la portada para los relojes: si van con la letra del móvil porque falta un permiso, si se han quedado atrás o si Android
 * frena a Widgets en segundo plano. Con los botones que llevan a cada sitio (los de ColorOS incluidos).
 */
@Composable
fun AvisoReloj() {
    val ctx = LocalContext.current
    var vez by remember { mutableIntStateOf(0) }
    // (se vuelve a mirar al volver a Widgets: el permiso se da fuera)
    DisposableEffect(Unit) {
        val owner = androidx.lifecycle.ProcessLifecycleOwner.get()
        val ob = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vez++ }
        owner.lifecycle.addObserver(ob); onDispose { owner.lifecycle.removeObserver(ob) }
    }
    val hayRelojes = remember(vez) { Disenos.todos(ctx).any { Minutero.esReloj(it) } }
    if (!hayRelojes) return
    val sinPermiso = remember(vez) { !Minutero.puedeExactas(ctx) && !Minutero.letraDelMovil(ctx) }
    val atrasado = remember(vez) { Minutero.atrasado(ctx) }
    val restringida = remember(vez) { Minutero.restringida(ctx) }
    if (!sinPermiso && !atrasado && !restringida) return
    Column(Modifier.fillMaxWidth().padding(top = 14.dp).background(Acento.copy(alpha = 0.12f)).border(2.dp, Acento).padding(16.dp)) {
        Text("Tus relojes", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
        Text(when {
            sinPermiso -> "Van con la letra del móvil: para dibujar la hora con la tuya, Widgets tiene que poder despertarse cada minuto, y Android no le deja poner alarmas exactas."
            restringida -> "Android frena a Widgets en segundo plano: tus relojes pueden quedarse atrás."
            else -> "Se han quedado atrás: el ahorro de batería no deja a Widgets ponerlos en hora cada minuto."
        }, color = Charcoal, fontFamily = Display, fontSize = 17.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        if (sinPermiso && android.os.Build.VERSION.SDK_INT >= 31) AccionGrande(null, "Permitir las alarmas", Modifier.fillMaxWidth()) { Minutero.abreAlarmas(ctx) }
        MagLink("Que la batería no frene a Widgets", "Sin restricciones de batería") { Minutero.abreBateria(ctx) }
        MagLink("Inicio automático y segundo plano", "En OPPO, Realme y OnePlus; si no, los ajustes de Widgets") { Minutero.abreInicioAutomatico(ctx) }
        if (sinPermiso) MagLink("Dejarlos con la letra del móvil", "Se ponen en hora solos y no gastan; este aviso no vuelve") { Minutero.ponLetraDelMovil(ctx, true); vez++ }
    }
}
