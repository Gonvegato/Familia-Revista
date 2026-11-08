package com.gonzalo.ptah

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/*
 * El editor de un diseño: arriba, la vista previa en vivo (al tamaño que elijas: 1×1, 2×1, 4×2, 4×4… o el que quieras con las barras, en
 * claro y en oscuro); debajo, en pestañas, lo de su tipo («Contenido»), los colores, la forma (el borde, las esquinas, el marco), las
 * letras y las partes que se ven. Nada se guarda hasta «Guardar».
 */

const val PESTANA_COLORES = 1

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PantEditor(inicial: Diseno, nuevo: Boolean, eligiendo: Boolean = false, alVolver: () -> Unit, alGuardar: (Diseno) -> Unit, pestanaInicial: Int = 0) {
    val ctx = LocalContext.current
    val tipo = Tipos.de(inicial.tipo)
    var d by remember(inicial.id) { mutableStateOf(inicial) }
    var pestana by rememberSaveable { mutableIntStateOf(pestanaInicial) }
    val muestra = Plantillas.celdasDe(inicial)
    var celdas by remember { mutableStateOf<Tamanos.Celdas?>(muestra) }
    var w by remember { mutableFloatStateOf(Tamanos.dp(muestra).first) }
    var h by remember { mutableFloatStateOf(Tamanos.dp(muestra).second) }
    var noche by remember { mutableStateOf(Modo.oscuro) }
    var salir by remember { mutableStateOf(false) }
    val cambiado = d != inicial || nuevo
    val mg = margen()
    fun guarda() { Disenos.guarda(ctx, d); alGuardar(Disenos.de(ctx, d.id) ?: d) }

    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = mg, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            VolverRevista({ if (cambiado) salir = true else alVolver() }, "Ptah")
            Spacer(Modifier.weight(1f))
            EnlaceRevista(if (eligiendo) "Guardar y ponerlo aquí" else "Guardar diseño", acento = true) { guarda() }
        }
        // ---- La vista previa (fija arriba: se ve mientras cambias lo de abajo)
        BoxWithConstraints(Modifier.fillMaxWidth().height(262.dp).background(FondoDePantalla)) {
            val k = minOf(1f, (maxWidth.value - 24f) / w, 222f / h)
            Box(Modifier.fillMaxWidth().height(238.dp), contentAlignment = Alignment.Center) { VistaPrevia(d, w, h, noche, mostrar = k) }
            Text("${w.roundToInt()} × ${h.roundToInt()} dp · ${celdas ?: Tamanos.celdas(w, h)}".uppercase(), color = androidx.compose.ui.graphics.Color(0xCCFFFFFF), fontFamily = Body,
                fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = mg)) {
            // ---- El tamaño de la vista previa
            Etiqueta("El tamaño (para ver cómo queda)", Modifier.padding(top = 10.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Tamanos.PRESETS.forEach { c ->
                    FiltroRevista(c.toString(), celdas == c) { celdas = c; val (a, b) = Tamanos.dp(c); w = a; h = b }
                }
                FiltroRevista("Libre", celdas == null) { celdas = null }
            }
            if (celdas == null) {
                Barra("Ancho", w, Tamanos.MIN..420f, { "${it.roundToInt()} dp" }) { w = it }
                Barra("Alto", h, Tamanos.MIN..480f, { "${it.roundToInt()} dp" }) { h = it }
            }
            if (d.estilo.delTema) MagChoice(listOf("Claro", "Oscuro"), if (noche) 1 else 0, "Con tu Apariencia en") { noche = it == 1 }

            // ---- El nombre
            Campo("El nombre", d.nombre, tipo?.nombre ?: "Sin nombre") { d = d.copy(nombre = it) }
            Antetitulo(tipo?.nombre ?: "De otra versión de Ptah", Modifier.padding(top = 12.dp), color = Acento, conCuadro = false)
            if (tipo != null) Text(tipo.descripcion, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(8.dp))
            PestanasRevista(listOf("Contenido", "Colores", "Forma", "Letras", "Partes"), pestana) { pestana = it }
            Spacer(Modifier.height(6.dp))
            if (tipo == null) Nota("Este diseño es de una versión más nueva de Ptah: actualízala para cambiarlo.")
            else when (pestana) {
                0 -> tipo.Editor(d) { d = it }
                1 -> EditorColores(d, tipo, noche) { d = it }
                2 -> EditorForma(d, noche) { d = it }
                3 -> EditorLetras(d) { d = it }
                else -> EditorPartes(d, tipo) { d = it }
            }

            Spacer(Modifier.height(22.dp))
            AccionGrande(null, if (eligiendo) "Guardar y ponerlo aquí" else "Guardar diseño", Modifier.fillMaxWidth()) { guarda() }
            Text(if (eligiendo) "Se guarda en «Tus diseños» y se pone en el widget que estás añadiendo."
                else "Se guarda en «Tus diseños» (y en tu cuenta). Si ya está en tu pantalla de inicio, se cambia allí al momento.",
                color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(30.dp))
        }
    }
    androidx.activity.compose.BackHandler(enabled = cambiado) { salir = true }
    if (salir) AlertDialog(onDismissRequest = { salir = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Salir sin guardar?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text("Lo que has cambiado no está guardado.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({ salir = false; guarda() }) { Text("GUARDAR DISEÑO", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ salir = false; alVolver() }) { Text("SALIR SIN GUARDAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } })
}

/** Los colores con los que se dibuja ahora (para enseñar el efectivo de cada pieza). */
@Composable
private fun lienzoDe(d: Diseno, noche: Boolean): Lienzo {
    val ctx = LocalContext.current
    return remember(d, noche, Skin.accentId, Skin.palette.id, Skin.fondoId) { Lienzo(ctx, d, 10f, 10f, noche, 1f, datosApp(ctx)) }
}

@Composable
private fun EditorColores(d: Diseno, tipo: TipoWidget, noche: Boolean, cambia: (Diseno) -> Unit) {
    val e = d.estilo
    val l = lienzoDe(d, noche)
    fun pon(x: Estilo) = cambia(d.conEstilo(x))
    BloqueEditor("El fondo", primero = true)
    Elige("Qué lleva", TipoFondo.entries.map { it.name to it.nombre }, e.fondo.name) { n -> pon(e.copy(fondo = TipoFondo.valueOf(n))) }
    when (e.fondo) {
        TipoFondo.COLOR -> FilaColor("El color del fondo", e.colorFondo, l.papelDeVerdad(), conSerie = false) { pon(e.copy(colorFondo = it)) }
        TipoFondo.DEGRADADO -> {
            FilaColor("Desde", e.colorFondo, Col.resuelve(e.colorFondo, l.tonos, l.papel), conSerie = false) { pon(e.copy(colorFondo = it)) }
            FilaColor("Hasta", e.colorFondo2, Col.resuelve(e.colorFondo2, l.tonos, l.acento), conSerie = false) { pon(e.copy(colorFondo2 = it)) }
            Barra("La dirección", e.angulo.toFloat(), 0f..315f, { when (it.roundToInt()) { 0 -> "De arriba abajo"; 90 -> "De izquierda a derecha"; 180 -> "De abajo arriba"; 270 -> "De derecha a izquierda"; else -> "En diagonal (${it.roundToInt()}°)" } }, pasos = 6) { pon(e.copy(angulo = it.roundToInt())) }
        }
        TipoFondo.PAPEL -> Nota("El papel de tu Apariencia (cambia con ella, también con el claro y el oscuro).", Modifier.padding(top = 4.dp))
        TipoFondo.TRANSPARENTE -> Nota("Sin fondo: lo de encima, sobre tu fondo de pantalla.", Modifier.padding(top = 4.dp))
    }
    if (e.fondo != TipoFondo.TRANSPARENTE) Barra("Lo que se ve del fondo", e.opacidad.toFloat(), 0f..100f, { "${it.roundToInt()} %" }) { pon(e.copy(opacidad = it.roundToInt())) }
    BloqueEditor("La tinta")
    FilaColor("La tinta", e.tinta, l.tinta, "La de tu Apariencia") { pon(e.copy(tinta = it)) }
    FilaColor("El acento", e.acento, l.acento, "El de tu Apariencia") { pon(e.copy(acento = it)) }
    FilaColor("El gris (lo secundario)", e.secundario, l.gris, "Entre el fondo y la tinta") { pon(e.copy(secundario = it)) }
    if (tipo.piezas.isNotEmpty()) {
        BloqueEditor("Cada pieza")
        Text("Si no le das uno, cada pieza va con la tinta o el acento.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
        tipo.piezas.forEach { (id, nombre) ->
            FilaColor(nombre, e.pieza(id), l.pieza(id, l.tinta), "Con la tinta o el acento") { v -> pon(e.copy(piezas = if (v.isBlank()) e.piezas - id else e.piezas + (id to v))) }
        }
    }
}

@Composable
private fun EditorForma(d: Diseno, noche: Boolean, cambia: (Diseno) -> Unit) {
    val e = d.estilo
    val l = lienzoDe(d, noche)
    fun pon(x: Estilo) = cambia(d.conEstilo(x))
    BloqueEditor("El borde", primero = true)
    SiNo("Con borde", "Una línea alrededor, como los widgets de las hermanas.", e.borde) { pon(e.copy(borde = it)) }
    if (e.borde) {
        Barra("Lo grueso", e.grosorBorde, 0.5f..6f, { "%.1f dp".format(it) }) { pon(e.copy(grosorBorde = (it * 10).roundToInt() / 10f)) }
        FilaColor("Su color", e.colorBorde, Col.resuelve(e.colorBorde, l.tonos, l.tinta), "El de la tinta") { pon(e.copy(colorBorde = it)) }
    }
    BloqueEditor("Las esquinas")
    Barra("Lo redondas", e.radio, 0f..60f, { if (it < 0.5f) "Rectas" else "${it.roundToInt()} dp" }) { pon(e.copy(radio = it.roundToInt().toFloat())) }
    BloqueEditor("El marco de revista")
    SiNo("Marco interior", "Un filete fino un poco por dentro del borde, como en las páginas de una revista.", e.marco) { pon(e.copy(marco = it)) }
    if (e.marco) FilaColor("Su color", e.pieza("marco"), l.pieza("marco", l.tinta), "El de la tinta") { v -> pon(e.copy(piezas = if (v.isBlank()) e.piezas - "marco" else e.piezas + ("marco" to v))) }
}

@Composable
private fun EditorLetras(d: Diseno, cambia: (Diseno) -> Unit) {
    val e = d.estilo
    fun pon(x: Estilo) = cambia(d.conEstilo(x))
    BloqueEditor("Las letras", primero = true)
    val titulos = remember { listOf("Atrio", "Vitrina") + (DISPLAY_FONTS + BODY_FONTS).map { it.name }.distinct() }
    val textos = remember { listOf("Atrio", "Vitrina") + (BODY_FONTS + DISPLAY_FONTS).map { it.name }.distinct() }
    SelectorLetraDiseno("De los títulos", e.letraTitulos, titulos) { pon(e.copy(letraTitulos = it)) }
    Filete(1.dp, Line)
    SelectorLetraDiseno("Del texto", e.letraTexto, textos) { pon(e.copy(letraTexto = it)) }
    Filete(1.dp, Line)
    Barra("El tamaño del texto", e.escala, 0.6f..1.6f, { "${(it * 100).roundToInt()} %" }) { pon(e.copy(escala = (it * 20).roundToInt() / 20f)) }
    Elige("La alineación", Alineacion.entries.map { it.name to it.nombre }, e.alineacion.name) { pon(e.copy(alineacion = Alineacion.valueOf(it))) }
}

@Composable
private fun EditorPartes(d: Diseno, tipo: TipoWidget, cambia: (Diseno) -> Unit) {
    val e = d.estilo
    BloqueEditor("Lo que se ve", primero = true)
    if (tipo.partes.isEmpty()) Nota("Este tipo no tiene partes que esconder.")
    tipo.partes.forEach { (id, nombre) ->
        SiNo(nombre, null, e.ve(id)) { v -> cambia(d.conEstilo(e.copy(ocultas = if (v) e.ocultas - id else e.ocultas + id))) }
    }
    if (tipo.id == Tiempo.id) Nota("«${OpenMeteo.ATRIBUCION}» sale siempre, pequeño, en el widget: lo pide su licencia.", Modifier.padding(top = 6.dp))
}

/** Una letra de un diseño: «La de tu Apariencia» (vacía), «Atrio», «Vitrina» o una de Google Fonts. */
@Composable
fun SelectorLetraDiseno(nombre: String, valor: String, opciones: List<String>, onCambia: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().tocable { abierto = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Aa", fontFamily = valor.takeIf { it.isNotBlank() }?.let { if (it == "Atrio") AtrioDisplay else Skin.family(it) } ?: Display, fontSize = 28.sp, color = Charcoal, modifier = Modifier.width(54.dp))
        Column(Modifier.weight(1f)) {
            Text(nombre, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Charcoal)
            Text((valor.ifEmpty { "La de tu Apariencia" }).uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text("▾", color = Muted, fontSize = 16.sp)
    }
    if (abierto) AlertDialog(
        onDismissRequest = { abierto = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text(nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item { Text("La de tu Apariencia", fontSize = 18.sp, color = if (valor.isEmpty()) Acento else Charcoal, modifier = Modifier.fillMaxWidth().tocable { onCambia(""); abierto = false }.padding(vertical = 10.dp)) }
                items(opciones.size) { i ->
                    val o = opciones[i]
                    Text(o, fontFamily = if (o == "Atrio") AtrioDisplay else Skin.family(o), fontSize = 20.sp, maxLines = 1, color = if (o == valor) Acento else Charcoal,
                        modifier = Modifier.fillMaxWidth().tocable { onCambia(o); abierto = false }.padding(vertical = 10.dp))
                }
            }
        },
        confirmButton = { TextButton({ abierto = false }) { Text("CERRAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}

/** «Ponerlo en la pantalla de inicio»: se le pide al lanzador (Android 8 o más) y, cuando lo pone, ese widget lleva este diseño. */
class AlFijar : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: android.content.Context, i: android.content.Intent) {
        val id = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val d = i.getStringExtra("diseno") ?: return
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        Widgets.asigna(ctx, id, d)
        pendiente(ctx, null)
        Widgets.pintar(ctx, intArrayOf(id), goAsync())
        Refresco.programa(ctx)
    }

    companion object {
        /** ¿Deja el lanzador pedirle un widget nuevo? */
        fun puede(ctx: android.content.Context): Boolean = runCatching { AppWidgetManager.getInstance(ctx).isRequestPinAppWidgetSupported }.getOrDefault(false)

        private fun p(ctx: android.content.Context) = ctx.getSharedPreferences("ptah_widgets", android.content.Context.MODE_PRIVATE)
        /** El diseño pedido hace un momento (si el lanzador abre también el selector al ponerlo, ya sabe cuál es). */
        fun pendiente(ctx: android.content.Context): String? {
            val p = p(ctx)
            return p.getString("fijar", null)?.takeIf { System.currentTimeMillis() - p.getLong("fijar_t", 0L) < 5 * 60_000L }
        }
        fun pendiente(ctx: android.content.Context, d: String?) { p(ctx).edit().putString("fijar", d).putLong("fijar_t", System.currentTimeMillis()).apply() }

        fun pide(ctx: android.content.Context, diseno: String) {
            pendiente(ctx, diseno)
            val mgr = AppWidgetManager.getInstance(ctx)
            val pi = android.app.PendingIntent.getBroadcast(ctx, diseno.hashCode(), android.content.Intent(ctx, AlFijar::class.java).putExtra("diseno", diseno),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE)
            runCatching { mgr.requestPinAppWidget(ComponentName(ctx, WPtah::class.java), null, pi) }
        }
    }
}
