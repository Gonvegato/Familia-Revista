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
import com.gonzalo.cobalto.CuentaSprints
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.Formato
import com.gonzalo.cobalto.Sprint
import com.gonzalo.cobalto.SprintEscribir
import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.delay

/** La cuenta de los sprints en el ordenador: la colección `sprints` de [LaFamilia] y este ordenador (el de sus palabras de cada día). */
object CuentaSprintsPC : CuentaSprints {
    override fun coleccion(): Coleccion? = if (LaFamilia.iniciada) runCatching { LaFamilia.coleccion(SprintEscribir.COLECCION) }.getOrNull() else null
    override val dentro: Boolean get() = NubePC.vinculada
    override val dispositivo: String get() = Cuaderno.dispositivo()
}

/**
 * Los sprints que este ordenador guardaba aparte hasta Cobalto 1.1.0 (`sprints.json` de la carpeta de Cobalto: «sprints», los
 * terminados, y «sprint», el que estaba en marcha, con el formato del móvil): pasan a la cuenta una vez por cuenta, juntándose con los de
 * tus otros dispositivos ([SprintEscribir.migra]). El archivo se queda como estaba, como copia (con «enCuenta»: la cuenta a la que ya pasó).
 */
object SprintsDeAntes {
    const val ARCHIVO = "sprints.json"
    /** La cuenta para la que ya se ha hecho en esta sesión de la app ("" = sin cuenta). */
    @Volatile private var hechaPara: String? = null

    private fun uid() = if (NubePC.vinculada) NubePC.uid else ""

    fun hecha(): Boolean { val uid = uid(); return hechaPara == uid || (uid.isNotBlank() && Local.lee(ARCHIVO).optString("enCuenta") == uid) }

    /** Los pasa si aún no están (al abrir y cada vez que cambia la cuenta). Devuelve cuántos ha pasado. */
    @Synchronized
    fun migra(): Int {
        if (hecha()) return 0
        val uid = uid()
        val o = Local.lee(ARCHIVO)
        val hay = o.has("sprints") || o.has("sprint")
        if (hay && !SprintEscribir.listoParaMigrar()) return 0
        val n = if (!hay) 0 else SprintEscribir.migra(o.optJSONArray("sprints"), o.optJSONObject("sprint"))
        if (hay && uid.isNotBlank()) Local.graba(ARCHIVO, o.put("enCuenta", uid))
        hechaPara = uid
        return n
    }

    fun reiniciaParaPruebas() { hechaPara = null }
}

/*
 * Los sprints en el ordenador: dónde viven (la cuenta de la familia de esta app), lo que este ordenador guardaba aparte hasta la 1.1.0 y
 * las pantallas. Lo que es un sprint y cómo se cuenta es lo del móvil: EscribirSprint.kt, el mismo archivo, copiado al compilar.
 */

/**
 * Vigila el sprint en marcha (va en la raíz de la app, para que siga fuera del editor) y enseña el resultado al acabar. Cada vez que
 * cambia la cuenta se pone al día con ella (un sprint empezado o terminado en otro dispositivo).
 */
@Composable
fun VigilaSprint(e: Escritos) {
    val cambios = LaFamilia.cambios
    LaunchedEffect(cambios) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { SprintsDeAntes.migra() } }; SprintEscribir.mira(e) }
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
        Text(Formato.cifra(s.palabras), color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 74.sp)
        Text(if (s.minutos >= 1) "PALABRAS EN ${s.minutos} MIN · ${s.porMinuto} POR MINUTO" else "PALABRAS EN MENOS DE UN MINUTO", color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(10.dp))
        if (s.meta > 0) Text(if (s.palabras >= s.meta) "Meta de ${Formato.cifra(s.meta)} conseguida." else "Te quedaste a ${Formato.cifra(s.meta - s.palabras)} de la meta (${Formato.cifra(s.meta)}).",
            color = Charcoal, fontFamily = Grotesca, fontSize = 21.sp)
        if (record) Text("Es tu mejor ritmo en esta obra.", color = Acento, fontFamily = Body, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        e.get(s.pid)?.let { Text("En «${it.s("titulo")}»", color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

/** Los sprints de la obra (para las cifras). */
@Composable
fun SprintsObra(pid: String) {
    val cambios = LaFamilia.cambios
    val l = remember(SprintEscribir.terminado, SprintEscribir.hasta, cambios) { SprintEscribir.historial(pid) }
    if (l.isEmpty()) return
    val f = java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.forLanguageTag("es-ES"))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Antetitulo("Sprints", tam = 12.sp)
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
