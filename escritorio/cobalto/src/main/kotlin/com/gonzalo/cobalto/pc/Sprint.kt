@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.cobalto.pc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.Formato
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** Un sprint ya terminado: cuándo empezó, cuántos minutos, cuántas palabras y la meta (0 si no había). */
class Sprint(val pid: String, val desde: Long, val minutos: Int, val palabras: Int, val meta: Int) {
    val porMinuto: Int get() = if (minutos > 0) palabras / minutos else 0
}

/**
 * Sprints de escritura: escribir sin parar durante un rato (10, 25, 45 min…), con una meta de palabras si quieres. Cuentan las
 * palabras nuevas de la obra (en cualquier escena: las que apunta [Escritos.escribe], así no se cuenta nada dos veces). Sigue
 * aunque cambies de pantalla o cierres la app, y lo hecho se guarda en este ordenador. Lo mismo que EscribirSprint.kt del móvil,
 * con lo mismo guardado igual: el sprint en marcha («sprint»: pid, desde, hasta, base, dia, meta, min) y los terminados
 * («sprints»: pid, desde, min, pal, meta; los últimos 200), en `sprints.json` de la carpeta de Cobalto.
 */
object SprintEscribir {
    const val ARCHIVO = "sprints.json"
    var pid by mutableStateOf("")
        private set
    var hasta by mutableLongStateOf(0L)
        private set
    private var desde = 0L
    private var base = 0
    private var dia = 0L
    var meta by mutableIntStateOf(0)
        private set
    var minutos = 0
        private set
    /** El último que terminó (para enseñarlo); se pone a null al verlo. */
    var terminado by mutableStateOf<Sprint?>(null)
    /** La hora (para las pruebas). */
    var reloj: () -> Long = { System.currentTimeMillis() }
    var hoy: () -> Long = { Escritos.hoy() }

    private fun lee() = Local.lee(ARCHIVO)
    private fun graba(o: JSONObject) = Local.graba(ARCHIVO, o)

    /** Lo recupera si se cerró la app con uno en marcha (y lo termina si ya pasó su hora). */
    fun recupera(e: Escritos) {
        if (hasta > 0L) return
        val o = lee().optJSONObject("sprint") ?: return
        pid = o.optString("pid"); desde = o.optLong("desde"); hasta = o.optLong("hasta"); base = o.optInt("base"); dia = o.optLong("dia")
        meta = o.optInt("meta"); minutos = o.optInt("min")
        if (hasta in 1..reloj()) termina(e)
    }

    fun activo(p: String? = null) = hasta > reloj() && (p == null || p == pid)

    fun empieza(e: Escritos, obra: String, min: Int, metaPalabras: Int) {
        pid = obra; minutos = min; meta = metaPalabras
        desde = reloj(); hasta = desde + min * 60_000L
        dia = hoy(); base = e.escritas(dia, obra)
        graba(lee().put("sprint", JSONObject().put("pid", pid).put("desde", desde).put("hasta", hasta).put("base", base).put("dia", dia).put("meta", meta).put("min", minutos)))
    }

    /** Palabras escritas en la obra desde que empezó. */
    fun llevas(e: Escritos): Int {
        if (hasta == 0L) return 0
        val h = hoy()
        return ((e.escritas(dia, pid) + if (h != dia) e.escritas(h, pid) else 0) - base).coerceAtLeast(0)
    }

    /** Lo da por terminado (antes de tiempo si [ya]) y lo guarda en el historial. */
    fun termina(e: Escritos, ya: Boolean = false) {
        if (hasta == 0L) return
        val fin = if (ya) reloj().coerceAtMost(hasta) else hasta
        val min = (((fin - desde) + 30_000L) / 60_000L).toInt().coerceAtLeast(if (ya) 0 else minutos)
        val s = Sprint(pid, desde, min, llevas(e), meta)
        hasta = 0L
        val o = lee(); o.remove("sprint")
        if (s.minutos >= 1) {
            val a = o.optJSONArray("sprints") ?: JSONArray()
            a.put(JSONObject().put("pid", s.pid).put("desde", s.desde).put("min", s.minutos).put("pal", s.palabras).put("meta", s.meta))
            o.put("sprints", JSONArray().also { n -> (maxOf(0, a.length() - 200) until a.length()).forEach { n.put(a.get(it)) } })
        }
        graba(o)
        if (s.minutos >= 1 || s.palabras > 0) terminado = s
    }

    fun historial(obra: String? = null): List<Sprint> = runCatching {
        val a = lee().optJSONArray("sprints") ?: JSONArray()
        (0 until a.length()).map { a.getJSONObject(it) }.map { Sprint(it.optString("pid"), it.optLong("desde"), it.optInt("min"), it.optInt("pal"), it.optInt("meta")) }
            .filter { obra == null || it.pid == obra }.sortedByDescending { it.desde }
    }.getOrDefault(emptyList())

    fun reiniciaParaPruebas() { hasta = 0L; pid = ""; terminado = null; reloj = { System.currentTimeMillis() }; hoy = { Escritos.hoy() } }
}

/** Vigila el sprint en marcha (va en la raíz de la app, para que siga fuera del editor) y enseña el resultado al acabar. */
@Composable
fun VigilaSprint(e: Escritos) {
    LaunchedEffect(Unit) { SprintEscribir.recupera(e) }
    val hasta = SprintEscribir.hasta
    LaunchedEffect(hasta) {
        if (hasta == 0L) return@LaunchedEffect
        delay((hasta - System.currentTimeMillis()).coerceAtLeast(0))
        Editor.guardaYa()
        SprintEscribir.termina(e)
    }
    SprintEscribir.terminado?.let { s -> ResultadoSprint(s, e) { SprintEscribir.terminado = null } }
}

/** Elegir cuánto dura y la meta, y empezar. */
@Composable
fun DialogoSprint(e: Escritos, pid: String, onCerrar: () -> Unit) {
    var min by remember { mutableIntStateOf(25) }
    var meta by remember { mutableStateOf("") }
    val mejor = remember { SprintEscribir.historial(pid).maxByOrNull { it.porMinuto } }
    Dialogo("Sprint de escritura", onCerrar, ancho = 600.dp, botones = {
        BotonSuave("Cancelar") { onCerrar() }
        BotonAcento("Empezar", alto = 44.dp, modifier = Modifier.width(140.dp)) { SprintEscribir.empieza(e, pid, min, meta.toIntOrNull() ?: 0); onCerrar() }
    }) {
        Text("Escribe sin parar, sin corregir, hasta que acabe el tiempo. Cuentan las palabras nuevas de toda la obra. El tiempo corre en el pie del editor.",
            color = Muted, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp)
        EtiquetaCampo("Cuánto")
        FilaOpciones { listOf(5, 10, 15, 20, 25, 30, 45, 60).forEach { m -> Opcion("$m min", min == m) { min = m } } }
        CampoRevista(meta, { meta = it.filter(Char::isDigit).take(5) }, "Meta de palabras (opcional)", pista = "Por ejemplo, 500")
        if (mejor != null && mejor.porMinuto > 0) Text("Tu mejor ritmo en esta obra: ${mejor.porMinuto} palabras por minuto.", color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** La tira del sprint en marcha (en el pie del editor): el tiempo que queda y lo escrito. */
@Composable
fun TiraSprint(e: Escritos, onTerminar: () -> Unit) {
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(SprintEscribir.hasta) { while (SprintEscribir.hasta > 0L) { ahora = System.currentTimeMillis(); delay(1000) } }
    val queda = ((SprintEscribir.hasta - ahora) / 1000).coerceAtLeast(0)
    val llevas = SprintEscribir.llevas(e)
    val meta = SprintEscribir.meta
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SPRINT %d:%02d".format(queda / 60, queda % 60), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Text("+$llevas" + if (meta > 0) " de $meta" else "", color = if (meta in 1..llevas) Conseguido else Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("TERMINAR", Modifier.pointerHoverIcon(PointerIcon.Hand).clickableSinFoco(onTerminar).padding(4.dp), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
    }
}

@Composable
private fun ResultadoSprint(s: Sprint, e: Escritos, onCerrar: () -> Unit) {
    val antes = remember(s) { SprintEscribir.historial(s.pid).filter { it.desde != s.desde } }
    val record = antes.isNotEmpty() && s.porMinuto > antes.maxOf { it.porMinuto }
    Dialogo("Sprint terminado", onCerrar, ancho = 560.dp, botones = { BotonAcento("Seguir", alto = 44.dp, modifier = Modifier.width(130.dp)) { onCerrar() } }) {
        Text(Formato.cifra(s.palabras), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 74.sp)
        Text(if (s.minutos >= 1) "PALABRAS EN ${s.minutos} MIN · ${s.porMinuto} POR MINUTO" else "PALABRAS EN MENOS DE UN MINUTO", color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(10.dp))
        if (s.meta > 0) Text(if (s.palabras >= s.meta) "Meta de ${Formato.cifra(s.meta)} conseguida." else "Te quedaste a ${Formato.cifra(s.meta - s.palabras)} de la meta (${Formato.cifra(s.meta)}).",
            color = Charcoal, fontFamily = Display, fontSize = 21.sp)
        if (record) Text("Es tu mejor ritmo en esta obra.", color = Acento, fontFamily = Body, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        e.get(s.pid)?.let { Text("En «${it.s("titulo")}»", color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

/** Los sprints de la obra (para las cifras). */
@Composable
fun SprintsObra(pid: String) {
    val l = remember(SprintEscribir.terminado, SprintEscribir.hasta) { SprintEscribir.historial(pid) }
    if (l.isEmpty()) return
    val f = java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.forLanguageTag("es-ES"))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Antetitulo("Sprints (en este ordenador)", tam = 12.sp)
        Text("${l.size} · ${Formato.cifra(l.sumOf { it.palabras })} palabras en ${l.sumOf { it.minutos }} min · mejor ritmo ${l.maxOf { it.porMinuto }} por minuto",
            color = Muted, fontFamily = Display, fontSize = 15.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        l.take(8).forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(f.format(java.util.Date(s.desde)).replace(".", ""), color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.width(130.dp))
                Text("${s.minutos} min", color = Charcoal, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.width(60.dp))
                Text("${Formato.cifra(s.palabras)} palabras" + if (s.meta > 0) (if (s.palabras >= s.meta) " ✓" else " de ${Formato.cifra(s.meta)}") else "", color = Charcoal, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${s.porMinuto}/min", color = Muted, fontFamily = Body, fontSize = 13.sp)
            }
        }
    }
}
