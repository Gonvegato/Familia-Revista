package com.gonzalo.cobalto

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.nucleo.Nodo
import org.json.JSONArray
import org.json.JSONObject

/** Un sprint ya terminado: de qué obra, cuándo empezó, cuántos minutos, cuántas palabras y la meta (0 si no había). */
class Sprint(val pid: String, val desde: Long, val minutos: Int, val palabras: Int, val meta: Int) {
    val porMinuto: Int get() = if (minutos > 0) palabras / minutos else 0
}

/** Dónde viven los sprints en cada app: la colección `sprints` de su cuenta de la familia, si hay sesión y qué dispositivo es este. */
interface CuentaSprints {
    /** La colección `sprints` de la cuenta (null si la cuenta de la app aún no está lista). */
    fun coleccion(): Coleccion?
    /** ¿Hay sesión? Sin ella, lo hecho espera en la cola de la cuenta y sube al entrar. */
    val dentro: Boolean
    /** Este dispositivo (el mismo id con el que apunta sus palabras de cada día). */
    val dispositivo: String

    object Ninguna : CuentaSprints {
        override fun coleccion(): Coleccion? = null
        override val dentro = false
        override val dispositivo = ""
    }
}

/**
 * Sprints de escritura: escribir sin parar durante un rato (10, 25, 45 min…), con una meta de palabras si quieres. Cuentan las
 * palabras nuevas de la obra (en cualquier escena y en cualquier dispositivo: las que apunta [Escritos.escribe], así no se cuenta nada
 * dos veces). Viven en la cuenta (`sprints/<obra>~<desde>`, nube/ESQUEMA.md): los terminados están en todos tus dispositivos y el que
 * está en marcha también (lo empiezas en el móvil y sigue en el ordenador). Sigue aunque cierres la app.
 *
 * Al cumplirse la hora, lo da por terminado cualquier dispositivo que lo tenga abierto, y todos escriben su fin con la misma hora (la
 * del fin): gana el primero que llega a la cuenta y los demás no cambian nada. Terminarlo antes, a mano, gana siempre a eso. Si la hora
 * pasa con todo cerrado, lo termina el dispositivo donde empezó al volver a abrirse (como antes).
 *
 * Sin nada de Android: es el MISMO archivo que compila Cobalto para Linux y Mac. Las pantallas, aparte (EscribirSprintUi.kt en el móvil,
 * escritorio/cobalto/…/pc/Sprint.kt en el ordenador). Hasta Cobalto 0.4.1 y 1.1.0 se guardaban en cada dispositivo («sprints»: pid,
 * desde, min, pal, meta; «sprint»: el que estaba en marcha): [migra] los pasa a la cuenta, una vez, sin perder ni repetir ninguno.
 */
open class MotorSprints {
    @Volatile var cuenta: CuentaSprints = CuentaSprints.Ninguna
    /** La hora y el día (para las pruebas). */
    var reloj: () -> Long = { System.currentTimeMillis() }
    var hoy: () -> Long = { Escritos.hoy() }

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

    /** La referencia de un sprint en la cuenta: su obra y cuándo empezó (con eso es único, venga del dispositivo que venga). */
    fun ref(obra: String, desde: Long) = "$obra~$desde"

    private fun col(): Coleccion? = runCatching { cuenta.coleccion() }.getOrNull()

    private fun abierto(n: Nodo) = n.texto("ref").isNotEmpty() && n.entero("fin") == 0L && n.entero("hasta") > 0L

    private fun adopta(n: Nodo) {
        pid = n.texto("ref"); desde = n.entero("desde"); base = n.entero("base").toInt(); dia = n.entero("dia")
        meta = n.entero("meta").toInt(); minutos = n.entero("min").toInt()
        hasta = n.entero("hasta")
    }

    /**
     * Se pone al día con la cuenta (al abrir la app y cada vez que cambia algo en ella): termina los de este dispositivo que pasaron su
     * hora con la app cerrada, sigue el que esté en marcha (también si lo empezaste en otro) y deja de enseñar el que se terminó en otro.
     */
    fun mira(e: Escritos) {
        val c = col() ?: return
        val ahora = reloj()
        val abiertos = c.vivos().filter { abierto(it) }
        abiertos.filter { it.entero("hasta") <= ahora && it.texto("dev") == cuenta.dispositivo }.sortedBy { it.entero("desde") }.forEach { adopta(it); termina(e) }
        val enMarcha = abiertos.filter { it.entero("hasta") > ahora }.maxByOrNull { it.entero("desde") }
        if (enMarcha != null) {
            if (hasta == 0L || ref(pid, desde) != enMarcha.ref) adopta(enMarcha)
        } else if (hasta > 0L) {
            val n = c.nodo(ref(pid, desde))
            // Se terminó en otro dispositivo: aquí se deja de ver (el resultado ya se vio allí)
            if (n != null && (n.entero("fin") > 0L || n.borrado)) hasta = 0L
        }
    }

    /** Lo de siempre al abrir la app: [mira]. */
    fun recupera(e: Escritos) = mira(e)

    fun activo(p: String? = null) = hasta > reloj() && (p == null || p == pid)

    fun empieza(e: Escritos, obra: String, min: Int, metaPalabras: Int) {
        pid = obra; minutos = min; meta = metaPalabras
        desde = reloj(); hasta = desde + min * 60_000L
        dia = hoy(); base = e.escritas(dia, obra)
        col()?.pon(ref(pid, desde), camposDe(pid, desde, hasta, min, meta, dia, base.toLong()) + mapOf("pal" to 0L, "fin" to 0L), desde)
    }

    private fun camposDe(p: String, d: Long, h: Long, min: Int, m: Int, di: Long, b: Long): Map<String, Any?> =
        mapOf("ref" to p, "desde" to d, "hasta" to h, "min" to min.toLong(), "meta" to m.toLong(), "dia" to di, "base" to b, "dev" to cuenta.dispositivo)

    /** Palabras escritas en la obra desde que empezó (en todos tus dispositivos: las de cada día ya se suman). */
    fun llevas(e: Escritos): Int {
        if (hasta == 0L) return 0
        val h = hoy()
        return ((e.escritas(dia, pid) + if (h != dia) e.escritas(h, pid) else 0) - base).coerceAtLeast(0)
    }

    /** Lo da por terminado (antes de tiempo si [ya]) y lo guarda en el historial (en la cuenta). */
    fun termina(e: Escritos, ya: Boolean = false) {
        if (hasta == 0L) return
        val fin = if (ya) reloj().coerceAtMost(hasta) else hasta
        val min = (((fin - desde) + 30_000L) / 60_000L).toInt().coerceAtLeast(if (ya) 0 else minutos)
        val s = Sprint(pid, desde, min, llevas(e), meta)
        val previsto = hasta
        hasta = 0L
        guardaFin(s, fin, previsto, ya)
        if (s.minutos >= 1 || s.palabras > 0) terminado = s
    }

    private fun guardaFin(s: Sprint, fin: Long, previsto: Long, ya: Boolean) {
        val c = col() ?: return
        val r = ref(s.pid, s.desde)
        val n = c.nodo(r)
        val mFin = n?.m?.get("fin") ?: 0L
        // Al cumplirse la hora, todos escriben con la misma (gana el primero en llegar); a mano, siempre una más (gana a eso)
        val hora = if (ya) maxOf(previsto, mFin) + 1 else maxOf(previsto, mFin + 1)
        val campos = LinkedHashMap<String, Any?>()
        if (n == null) campos += camposDe(s.pid, s.desde, previsto, minutos, s.meta, dia, base.toLong())
        campos["fin"] = fin; campos["min"] = s.minutos.toLong(); campos["pal"] = s.palabras.toLong()
        // Uno de menos de un minuto no queda en el historial (como siempre): se quita
        if (s.minutos < 1) campos[Nodo.B] = fin
        c.pon(r, campos, hora)
    }

    /** Los sprints terminados (de la obra, o todos), el último primero: los de todos tus dispositivos. */
    fun historial(obra: String? = null): List<Sprint> {
        val c = col() ?: return emptyList()
        return c.vivos().filter { it.entero("fin") > 0L && it.entero("min") >= 1 && it.texto("ref").isNotEmpty() && (obra == null || it.texto("ref") == obra) }
            .map { Sprint(it.texto("ref"), it.entero("desde"), it.entero("min").toInt(), it.entero("pal").toInt(), it.entero("meta").toInt()) }
            .sortedByDescending { it.desde }
    }

    /**
     * ¿Se pueden pasar ya a la cuenta los de antes? Con sesión, cuando la colección ya se ha bajado entera (así se sabe qué hay y no se manda
     * lo que ya está); sin sesión, ya (esperan en la cola y suben al entrar).
     */
    fun listoParaMigrar(): Boolean {
        val c = col() ?: return false
        return !cuenta.dentro || c.completa
    }

    /**
     * Pasa a la cuenta los sprints que este dispositivo guardaba aparte (hasta Cobalto 0.4.1 / 1.1.0): [viejos] (pid, desde, min, pal, meta)
     * y [enMarcha] (el que estaba en marcha: pid, desde, hasta, base, dia, meta, min). Cada uno va a `sprints/<obra>~<desde>`: si ya está
     * (lo pasó otro dispositivo, o este otra vez), no se toca. Así se juntan los de todos tus dispositivos sin perder ni repetir ninguno.
     * Devuelve cuántos ha pasado.
     */
    fun migra(viejos: JSONArray?, enMarcha: JSONObject?): Int {
        val c = col() ?: return 0
        var n = 0
        if (viejos != null) for (i in 0 until viejos.length()) {
            val o = viejos.optJSONObject(i) ?: continue
            val p = o.optString("pid"); val d = o.optLong("desde"); val min = o.optInt("min")
            if (p.isBlank() || d <= 0L || min < 1 || !Coleccion.refValida(ref(p, d))) continue
            if (c.nodo(ref(p, d)) != null) continue
            val fin = d + min * 60_000L
            c.pon(ref(p, d), camposDe(p, d, fin, min, o.optInt("meta"), diaDe(d), 0L) + mapOf("pal" to o.optInt("pal").toLong(), "fin" to fin), fin)
            n++
        }
        if (enMarcha != null) {
            val p = enMarcha.optString("pid"); val d = enMarcha.optLong("desde"); val h = enMarcha.optLong("hasta")
            if (p.isNotBlank() && d > 0L && h > d && Coleccion.refValida(ref(p, d)) && c.nodo(ref(p, d)) == null) {
                c.pon(ref(p, d), camposDe(p, d, h, enMarcha.optInt("min"), enMarcha.optInt("meta"), enMarcha.optLong("dia", diaDe(d)), enMarcha.optLong("base")) +
                    mapOf("pal" to 0L, "fin" to 0L), d)
                n++
            }
        }
        return n
    }

    /** El día (días desde 1970, en la hora de aquí) de una hora. */
    private fun diaDe(ms: Long): Long = (ms + java.util.TimeZone.getDefault().getOffset(ms)) / 86_400_000L

    fun reiniciaParaPruebas() {
        hasta = 0L; pid = ""; terminado = null; desde = 0L; base = 0; dia = 0L; meta = 0; minutos = 0
        reloj = { System.currentTimeMillis() }; hoy = { Escritos.hoy() }
    }
}

/** Los sprints de esta app (en las pruebas, cada «dispositivo» es otro [MotorSprints]). */
object SprintEscribir : MotorSprints() {
    const val COLECCION = "sprints"
}
