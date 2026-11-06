package com.gonzalo.cobalto.pc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.Sinonimos
import com.gonzalo.familia.pc.Sitio
import org.json.JSONObject
import java.io.File

/*
 * Lo que el editor del ordenador hace sin pantalla (para probarlo aparte): buscar y reemplazar, las cuentas del pie, las
 * preferencias de este ordenador y dónde se guarda lo que es solo de aquí.
 */

/** Lo que es solo de este ordenador (las preferencias del editor, los sprints): la carpeta de Cobalto (o la de las pruebas). */
object Local {
    fun dir(): File = (Cuaderno.dirPruebas ?: Sitio.de("cobalto")).also { it.mkdirs() }
    fun archivo(nombre: String): File = File(dir(), nombre)

    fun lee(nombre: String): JSONObject = runCatching { JSONObject(archivo(nombre).readText()) }.getOrDefault(JSONObject())
    fun graba(nombre: String, o: JSONObject) {
        runCatching {
            val f = archivo(nombre); val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(o.toString()); if (!tmp.renameTo(f)) { f.writeText(o.toString()); tmp.delete() }
        }
    }
}

/**
 * Las preferencias del editor en este ordenador (como las de Atrio, `escribir:prefs`): tamaño de la letra, tipo de letra
 * (0 con serifa, 1 de palo seco, 2 de máquina), máquina de escribir, tipografía automática y el panel abierto o cerrado.
 */
object PrefsEditor {
    const val ARCHIVO = "editor.json"
    const val TAM_MIN = 14f; const val TAM_MAX = 34f; const val TAM_NORMAL = 20f
    val FUENTES = listOf("Serif", "Sans", "Mono")

    var tam by mutableStateOf(TAM_NORMAL)
    var fuente by mutableStateOf(0)
    var maquina by mutableStateOf(false)
    var tipografia by mutableStateOf(true)
    var panel by mutableStateOf(false)
    var pestanaPanel by mutableStateOf(0)
    @Volatile private var cargadas = false

    fun carga() {
        val o = Local.lee(ARCHIVO)
        tam = o.optDouble("tam", TAM_NORMAL.toDouble()).toFloat().coerceIn(TAM_MIN, TAM_MAX)
        fuente = o.optInt("fuente", 0).coerceIn(0, 2)
        maquina = o.optBoolean("maquina", false)
        tipografia = o.optBoolean("tipografia", true)
        panel = o.optBoolean("panel", false)
        pestanaPanel = o.optInt("pestanaPanel", 0).coerceIn(0, 4)
        cargadas = true
    }

    /** Las carga la primera vez que hacen falta. */
    fun asegura() { if (!cargadas) carga() }

    fun guarda() {
        Local.graba(ARCHIVO, JSONObject().put("tam", tam.toDouble()).put("fuente", fuente).put("maquina", maquina).put("tipografia", tipografia)
            .put("panel", panel).put("pestanaPanel", pestanaPanel))
    }

    fun letra(paso: Int) { tam = if (paso == 0) TAM_NORMAL else (tam + paso).coerceIn(TAM_MIN, TAM_MAX); guarda() }
    fun otraFuente() { fuente = (fuente + 1) % 3; guarda() }

    fun reiniciaParaPruebas() { cargadas = false; tam = TAM_NORMAL; fuente = 0; maquina = false; tipografia = true; panel = false; pestanaPanel = 0 }
}

/**
 * El tesauro de sinónimos de la app (th_es_v2.dat, el mismo archivo que lleva el móvil en sus assets, copiado al compilar). La búsqueda
 * es la del móvil ([Sinonimos], EscribirSinonimos.kt, el mismo archivo): aquí solo se dice de dónde se lee.
 */
fun Sinonimos.carga() = cargaDe { Sinonimos::class.java.getResourceAsStream("/sinonimos/th_es_v2.dat") }

/** Buscar y reemplazar dentro de una escena (lo de toda la obra lo hace [Escritos.reemplaza], el mismo del móvil). */
object Busqueda {
    fun rx(q: String, mayus: Boolean): Regex? = if (q.isEmpty()) null else Regex(Regex.escape(q), if (mayus) emptySet() else setOf(RegexOption.IGNORE_CASE))

    /** Dónde sale [q] en [texto] (sin mirar mayúsculas, salvo con [mayus]). */
    fun coincidencias(texto: String, q: String, mayus: Boolean = false): List<IntRange> = rx(q, mayus)?.findAll(texto)?.map { it.range }?.toList().orEmpty()

    /** La siguiente (o la anterior, con [paso] -1) a partir de [actual]; si aún no hay ninguna elegida, la primera después del cursor [desde]. */
    fun salta(lista: List<IntRange>, actual: Int, paso: Int, desde: Int = 0): Int {
        if (lista.isEmpty()) return -1
        if (actual !in lista.indices) {
            val i = if (paso >= 0) lista.indexOfFirst { it.first >= desde } else lista.indexOfLast { it.last < desde }
            return if (i >= 0) i else if (paso >= 0) 0 else lista.lastIndex
        }
        return ((actual + paso) % lista.size + lista.size) % lista.size
    }

    /** Cambia la coincidencia [r] por [por] (el cursor queda detrás de lo puesto). */
    fun reemplazaUna(v: TextFieldValue, r: IntRange, por: String): TextFieldValue {
        if (r.first < 0 || r.last >= v.text.length) return v
        return TextFieldValue(v.text.replaceRange(r.first, r.last + 1, por), TextRange(r.first + por.length))
    }

    /** Cambia todas en [texto]: el texto nuevo y cuántas veces. */
    fun reemplazaTodas(texto: String, q: String, por: String, mayus: Boolean = false): Pair<String, Int> {
        val r = rx(q, mayus) ?: return texto to 0
        val n = r.findAll(texto).count()
        return (if (n == 0) texto else r.replace(texto, Regex.escapeReplacement(por))) to n
    }
}

/**
 * Las cuentas del pie del editor, al momento (lo que aún no se ha guardado también cuenta): las palabras de la escena y de lo
 * elegido, los minutos de lectura, lo de esta sesión, y las de hoy en la obra. Las de hoy salen igual que las que apuntará
 * [Escritos.escribe] al guardar (lo que sube la cuenta de la escena sobre lo guardado), así no se cuenta nada dos veces.
 */
class Cuentas(val palabras: Int, val elegidas: Int, val minutos: Int, val sesion: Int, val hoy: Int, val capitulo: Int, val obra: Int) {
    companion object {
        fun de(texto: String, sel: TextRange, inicio: Int, guardadas: Int, hoyGuardado: Int, capituloGuardado: Int, obraGuardada: Int): Cuentas {
            val p = Escritos.palabras(texto)
            val a = minOf(sel.start, sel.end).coerceIn(0, texto.length); val b = maxOf(sel.start, sel.end).coerceIn(0, texto.length)
            val el = if (a == b) 0 else Escritos.palabras(texto.substring(a, b))
            val dif = p - guardadas
            return Cuentas(p, el, maxOf(1, (p + 219) / 220), p - inicio, hoyGuardado + dif.coerceAtLeast(0), capituloGuardado + dif, obraGuardada + dif)
        }
    }
}

/** Dónde poner el desplazamiento para que la línea del cursor quede a un 40 % del alto (la máquina de escribir). */
fun destinoMaquina(arribaCursor: Float, altoCursor: Float, margenArriba: Float, alto: Int, maximo: Int): Int =
    (margenArriba + arribaCursor + altoCursor / 2 - alto * 0.4f).toInt().coerceIn(0, maximo.coerceAtLeast(0))

/** Lo justo que hay que mover para que se vea el cursor (sin máquina de escribir): null si ya se ve. */
fun destinoVisible(arribaCursor: Float, altoCursor: Float, margenArriba: Float, actual: Int, alto: Int, maximo: Int, holgura: Float = 48f): Int? {
    val top = margenArriba + arribaCursor; val bottom = top + altoCursor
    return when {
        top - holgura < actual -> (top - holgura).toInt().coerceIn(0, maximo.coerceAtLeast(0))
        bottom + holgura > actual + alto -> (bottom + holgura - alto).toInt().coerceIn(0, maximo.coerceAtLeast(0))
        else -> null
    }.takeIf { it != actual }
}

/**
 * Lo que se lee y lo que se edita en una página (las fichas, las notas): lo elegido se abre para leerlo; para cambiarlo, «Editar»
 * (o un clic en la página); «Listo» o Esc vuelven a leer. Lo nuevo se abre ya para rellenarlo. Como el modo lectura de Atrio.
 */
class ModoPagina(inicial: String? = null) {
    var sel by mutableStateOf(inicial)
        private set
    var editando by mutableStateOf(false)
        private set

    /** Eliges otra cosa de la lista: se abre para leer. */
    fun elige(id: String?) { if (id != sel) { sel = id; editando = false } }
    /** Lo acabas de crear: se abre para rellenarlo. */
    fun nuevo(id: String) { sel = id; editando = true }
    fun edita() { if (sel != null) editando = true }
    fun listo() { editando = false }
    /** Esc: si estabas editando, vuelves a leer (true); si no, no hace nada (false). */
    fun esc(): Boolean = if (editando) { editando = false; true } else false
    /** Lo elegido ya no existe (se ha borrado): se elige [otro]. */
    fun desaparece(otro: String?) { sel = otro; editando = false }
}
