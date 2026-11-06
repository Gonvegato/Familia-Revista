@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.cobalto

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Coleccion
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/*
 * Los sprints en Android: dónde viven (la cuenta de la familia de esta app), lo que este móvil guardaba aparte hasta la 0.4.1 y las
 * pantallas. Lo que es un sprint y cómo se cuenta está en EscribirSprint.kt (el mismo archivo que compila Cobalto para Linux y Mac).
 */

/** La cuenta de los sprints en el móvil: la colección `sprints` de [LaFamilia] y este dispositivo (el de sus palabras de cada día). */
object CuentaSprintsMovil : CuentaSprints {
    override fun coleccion(): Coleccion? = runCatching { LaFamilia.coleccion(SprintEscribir.COLECCION) }.getOrNull()
    override val dentro: Boolean get() = Cuenta.vinculada
    override val dispositivo: String get() = Cuenta.dispositivo.ifBlank { "movil" }
}

/**
 * Los sprints que este móvil guardaba aparte hasta Cobalto 0.4.1 (preferencias «escribir»: «sprints», los terminados, y «sprint», el que
 * estaba en marcha): pasan a la cuenta una vez por cuenta, juntándose con los de tus otros dispositivos ([SprintEscribir.migra]). Se
 * quedan donde estaban, como copia.
 */
object SprintsDeAntes {
    private const val HECHO = "sprints_en_cuenta"
    /** La cuenta para la que ya se ha hecho mientras la app está abierta ("" = sin cuenta). */
    @Volatile private var hechaPara: String? = null
    private val enMarcha = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var otraVez = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("escribir", Context.MODE_PRIVATE)

    /** ¿Ya están en esta cuenta? */
    fun hecha(ctx: Context): Boolean {
        val uid = Cuenta.uid
        return hechaPara == uid || (uid.isNotBlank() && prefs(ctx).getString(HECHO, "") == uid)
    }

    /** Los pasa ahora (bloquea). Devuelve cuántos ha pasado. */
    @Synchronized
    fun migra(ctx: Context): Int {
        if (hecha(ctx)) return 0
        val p = prefs(ctx)
        val uid = Cuenta.uid
        val hay = p.contains("sprints") || p.contains("sprint")
        if (hay && !SprintEscribir.listoParaMigrar()) return 0
        val n = if (!hay) 0 else SprintEscribir.migra(
            runCatching { JSONArray(p.getString("sprints", "[]")) }.getOrNull(),
            runCatching { JSONObject(p.getString("sprint", "") ?: "") }.getOrNull())
        if (hay && uid.isNotBlank()) p.edit().putString(HECHO, uid).apply()
        hechaPara = uid
        return n
    }

    /** Los pasa en segundo plano, si aún no están (se llama al abrir y cada vez que cambian los sprints de la cuenta). */
    fun pronto(ctx: Context) {
        val a = ctx.applicationContext
        if (runCatching { hecha(a) }.getOrDefault(true)) return
        otraVez = true
        if (!enMarcha.compareAndSet(false, true)) return
        kotlin.concurrent.thread(name = "cobalto-sprints") {
            try { while (otraVez) { otraVez = false; runCatching { migra(a) } } } finally { enMarcha.set(false) }
        }
    }

    fun reiniciaParaPruebas() { hechaPara = null }
}

/**
 * Mira cada segundo si el sprint en marcha ha terminado (va en la pantalla de Escribir, para que siga fuera del editor) y, cada vez que
 * cambia la cuenta, se pone al día con ella (un sprint empezado o terminado en otro dispositivo).
 */
@Composable
fun VigilaSprint(e: Escritos, ctx: Context) {
    val cambios = LaFamilia.cambios
    LaunchedEffect(cambios) { SprintEscribir.mira(e) }
    val hasta = SprintEscribir.hasta
    LaunchedEffect(hasta) {
        if (hasta == 0L) return@LaunchedEffect
        delay((hasta - System.currentTimeMillis()).coerceAtLeast(0))
        SprintEscribir.termina(e)
    }
    SprintEscribir.terminado?.let { s -> ResultadoSprint(s, e) { SprintEscribir.terminado = null } }
}

/** Elegir cuánto dura y la meta, y empezar. */
@Composable
fun SprintDialogo(e: Escritos, pid: String, onCerrar: () -> Unit) {
    var min by remember { mutableIntStateOf(25) }
    var meta by remember { mutableStateOf("") }
    val mejor = remember { SprintEscribir.historial(pid).maxByOrNull { it.porMinuto } }
    HojaEscribir("Sprint de escritura", onCerrar) {
        Text("Escribe sin parar, sin corregir, hasta que suene. Cuentan las palabras nuevas de toda la obra.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        EtiquetaEscribir("Cuánto")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 20, 25, 30, 45, 60).forEach { m -> SoftChip("$m min", min == m) { min = m } }
        }
        CampoEscribir(meta, { meta = it.filter(Char::isDigit).take(5) }, "Meta de palabras (opcional)", numero = true)
        if (mejor != null && mejor.porMinuto > 0) Text("Tu mejor ritmo en esta obra: ${mejor.porMinuto} palabras por minuto.", color = Muted, fontSize = 13.sp)
        BotonEscribir("Empezar") { SprintEscribir.empieza(e, pid, min, meta.toIntOrNull() ?: 0); onCerrar() }
    }
}

/** La tira del sprint en marcha (en los contadores del editor): el tiempo que queda y lo escrito. */
@Composable
fun TiraSprint(e: Escritos, onTerminar: () -> Unit) {
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(SprintEscribir.hasta) { while (SprintEscribir.hasta > 0L) { ahora = System.currentTimeMillis(); delay(1000) } }
    val queda = ((SprintEscribir.hasta - ahora) / 1000).coerceAtLeast(0)
    val llevas = SprintEscribir.llevas(e)
    val meta = SprintEscribir.meta
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SPRINT %d:%02d".format(queda / 60, queda % 60), color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Text("+$llevas" + if (meta > 0) " de $meta" else "", color = if (meta in 1..llevas) androidx.compose.ui.graphics.Color(0xFF5B8C5A) else Charcoal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("TERMINAR", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.press(haptic = false, onClick = onTerminar).padding(4.dp))
    }
}

@Composable
private fun ResultadoSprint(s: Sprint, e: Escritos, onCerrar: () -> Unit) {
    val antes = remember(s) { SprintEscribir.historial(s.pid).filter { it.desde != s.desde } }
    val record = antes.isNotEmpty() && s.porMinuto > antes.maxOf { it.porMinuto }
    HojaEscribir("Sprint terminado", onCerrar) {
        Text(cifraE(s.palabras), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 66.sp, color = Charcoal)
        Text(if (s.minutos >= 1) "PALABRAS EN ${s.minutos} MIN · ${s.porMinuto} POR MINUTO" else "PALABRAS EN MENOS DE UN MINUTO", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        if (s.meta > 0) Text(if (s.palabras >= s.meta) "Meta de ${cifraE(s.meta)} conseguida." else "Te quedaste a ${cifraE(s.meta - s.palabras)} de la meta (${cifraE(s.meta)}).", fontFamily = Display, fontSize = 20.sp, color = Charcoal)
        if (record) Text("Es tu mejor ritmo en esta obra.", color = ColGeneral, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        e.get(s.pid)?.let { Text("En «${it.s("titulo")}»", color = Muted, fontSize = 13.sp) }
        BotonEscribir("Seguir") { onCerrar() }
    }
}

/** Los sprints de la obra (para Cifras): los de todos tus dispositivos. */
@Composable
fun SprintsObra(pid: String) {
    val cambios = LaFamilia.cambios
    val l = remember(SprintEscribir.terminado, SprintEscribir.hasta, cambios) { SprintEscribir.historial(pid) }
    if (l.isEmpty()) return
    val f = java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("es"))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EtiquetaEscribir("Sprints")
        Text("${l.size} · ${cifraE(l.sumOf { it.palabras })} palabras en ${l.sumOf { it.minutos }} min · mejor ritmo ${l.maxOf { it.porMinuto }} por minuto", color = Muted, fontSize = 12.sp)
        l.take(8).forEach { s ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(f.format(java.util.Date(s.desde)), color = Muted, fontSize = 13.sp, modifier = Modifier.width(110.dp))
                Text("${s.minutos} min", color = Charcoal, fontSize = 13.sp, modifier = Modifier.width(56.dp))
                Text("${cifraE(s.palabras)} palabras" + if (s.meta > 0) (if (s.palabras >= s.meta) " ✓" else " de ${cifraE(s.meta)}") else "", color = Charcoal, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${s.porMinuto}/min", color = Muted, fontSize = 13.sp)
            }
        }
    }
}
