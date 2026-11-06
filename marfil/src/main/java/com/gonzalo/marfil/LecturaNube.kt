package com.gonzalo.marfil

import android.content.Context
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.Nodo
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Lo leído de cada libro en la cuenta, para abrirlo donde lo dejaste en cualquier dispositivo (y para Azabache):
 *  - `lectura/<kid>`: por dónde vas y los ajustes del lector para ese libro;
 *  - `marcas/<kid>~<id>`: cada subrayado, nota o marcador, por separado (así dos dispositivos nunca se pisan);
 *  - `tiempo/<kid>~<dispositivo>`: el tiempo leído en cada dispositivo, por días (cada uno escribe solo el suyo).
 * Lo mismo con los escritos de Cobalto que lees aquí ([Escritos]), con `escrito_<obra>` en lugar del `kid`.
 *
 * El lector sigue trabajando con su estado de siempre en el móvil ([EstadoLectura]); aquí se traduce en los dos sentidos:
 * lo que cambia el lector se sube ([sube]) y lo que llega de fuera se une con [MezclaLectura] ([baja]).
 */
object LecturaNube {
    /** Lo que se guarda de la posición y los ajustes (los mismos nombres que usa el lector). */
    private val POSICION = listOf("cfi", "fraction", "label", "read", "bestCfi", "bestFrac", "bestLabel", "bestSet", "settings", "settingsAt", "autoLeyendo")
    private val hilo = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "marfil-lectura").apply { isDaemon = true } }
    private val programadas = HashMap<String, ScheduledFuture<*>>()
    @Volatile private var app: Context? = null
    /** La cuenta a la que se está escuchando (si cambia —otra sesión, las pruebas—, se vuelve a escuchar). */
    @Volatile private var de: Any? = null

    private fun lectura() = LaFamilia.coleccion("lectura")
    private fun marcas() = LaFamilia.coleccion("marcas")
    private fun tiempo() = LaFamilia.coleccion("tiempo")

    /** Al arrancar: escucha lo que llega de fuera y lo une con lo de este móvil. */
    fun inicia(c: Context) {
        app = c.applicationContext
        val f = LaFamilia.familia
        if (de === f) return
        de = f
        val alLlegar: (Set<String>) -> Unit = { refs -> programaBajada(refs.map { it.substringBefore('~') }.toSet()) }
        lectura().alCambiar(alLlegar); marcas().alCambiar(alLlegar); tiempo().alCambiar(alLlegar)
        programaBajada(null)
    }

    @Volatile private var porBajar: MutableSet<String>? = HashSet()
    @Volatile private var bajadaProgramada = false

    private fun programaBajada(kids: Set<String>?) {
        synchronized(this) {
            if (kids == null) porBajar = null else porBajar?.addAll(kids)
            if (bajadaProgramada) return
            bajadaProgramada = true
        }
        hilo.schedule({
            val k = synchronized(this) { val x = porBajar; porBajar = HashSet(); bajadaProgramada = false; x }
            app?.let { runCatching { baja(it, k) } }
        }, 300, TimeUnit.MILLISECONDS)
    }

    /** El nombre de una marca: su libro y lo que la identifica (su sitio y su hora de creación). */
    fun idMarca(kid: String, n: JSONObject): String = kid + "~" + MessageDigest.getInstance("SHA-1").digest(MezclaLectura.clave(n).toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    private fun idMarcaDeClave(kid: String, clave: String): String = kid + "~" + MessageDigest.getInstance("SHA-1").digest(clave.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    /** Ha cambiado lo leído de un libro: se sube dentro de [ms] (las páginas seguidas se juntan en una sola subida). */
    fun cambio(c: Context, id: String, ms: Long = 20_000L) {
        app = c.applicationContext
        synchronized(programadas) {
            programadas[id]?.cancel(false)
            programadas[id] = hilo.schedule({ runCatching { sube(c, id, EstadoLectura.estado(c, id)) } }, ms, TimeUnit.MILLISECONDS)
        }
    }

    /** Sube ya lo de un libro (al cerrar el lector). */
    fun yaMismo(c: Context, id: String) = cambio(c, id, 0L)

    /** Sube lo que el lector tiene de un libro y la cuenta aún no: solo lo que ha cambiado. */
    fun sube(c: Context, id: String, st: JSONObject, hora: Long? = null) {
        val l = Biblioteca.por(id)
        // (también lo leído en un escrito de Cobalto: `escrito_<obra>`, el mismo en todos tus dispositivos; el libro en sí se rehace en cada uno)
        if (st.length() == 0) return
        val kid = id
        // Por dónde vas y los ajustes del libro
        val pos = LinkedHashMap<String, Any?>()
        pos["ref"] = kid
        for (k in POSICION) if (st.has(k)) pos[k] = aKotlin(st.get(k))
        lectura().cambia(kid, pos, hora)
        // Subrayados, notas y marcadores: cada uno con la hora de su último cambio (gana la edición más reciente)
        val col = marcas()
        for (campo in listOf("notes", "marks")) {
            st.optJSONArray(campo)?.let { a ->
                for (i in 0 until a.length()) {
                    val n = a.optJSONObject(i) ?: continue
                    val mid = idMarca(kid, n)
                    val mod = n.optLong("mod", n.optLong("created", 0L)).coerceAtLeast(1L)
                    val actual = col.nodo(mid)
                    if (actual != null && (actual.m["n"] ?: 0L) >= mod && !actual.borrado) continue
                    col.pon(mid, mapOf("ref" to kid, "tipo" to campo, "n" to aKotlin(n), "b" to 0L), mod)
                }
            }
            st.optJSONObject("borr_$campo")?.let { b ->
                for (k in b.keys()) {
                    val t = b.optLong(k).coerceAtLeast(1L)
                    val mid = idMarcaDeClave(kid, k)
                    val actual = col.nodo(mid) ?: continue
                    if (actual.borrado || (actual.m["n"] ?: 0L) > t) continue
                    col.pon(mid, mapOf("b" to t), t)
                }
            }
        }
        // El tiempo de este dispositivo (y el de otros que aún no estén en la cuenta: lo importado de Atrio, lo de antes)
        val dev = st.optJSONObject("stats")?.optJSONObject("dev")
        if (dev != null) for (d in dev.keys()) {
            val b = dev.optJSONObject(d) ?: continue
            val ref = "$kid~$d"
            if (d != Cuenta.dispositivo && tiempo().nodo(ref) != null) continue
            val campos = LinkedHashMap<String, Any?>()
            campos["ref"] = kid; campos["fuente"] = d; campos["total"] = b.optLong("total", 0L)
            b.optJSONObject("days")?.let { days -> for (k in days.keys()) campos["d$k"] = days.optLong(k) }
            b.optJSONObject("best")?.let { best -> for (k in best.keys()) campos["x$k"] = best.optLong(k) }
            l?.let { campos["t"] = it.titulo; campos["a"] = it.autor }
            tiempo().cambia(ref, campos, hora)
        }
    }

    /** El estado de un libro tal como está en la cuenta (con la forma del de [EstadoLectura]). */
    fun estadoRemoto(kid: String): JSONObject {
        val o = JSONObject()
        lectura().nodo(kid)?.let { n -> for (k in POSICION) n[k]?.let { v -> o.put(k, aJson(v)) } }
        for (campo in listOf("notes", "marks")) {
            val vivas = JSONArray(); val borr = JSONObject()
            marcas().todos().values.filter { it.texto("ref") == kid && it.texto("tipo") == campo }.forEach { n ->
                val x = JSONObject(Json.escribe(n.mapa("n")))
                if (n.borrado) borr.put(MezclaLectura.clave(x), n.entero(Nodo.B)) else vivas.put(x.put("mod", n.m["n"] ?: x.optLong("mod")))
            }
            if (vivas.length() > 0) o.put(campo, vivas)
            if (borr.length() > 0) o.put("borr_$campo", borr)
        }
        val dev = JSONObject()
        tiempo().vivos().filter { it.texto("ref") == kid }.forEach { n ->
            val days = JSONObject(); val best = JSONObject()
            n.campos.forEach { (k, v) ->
                if (k.length == 11 && k[0] == 'd' && k[5] == '-') days.put(k.substring(1), (v as? Number)?.toLong() ?: 0L)
                if (k.length == 11 && k[0] == 'x' && k[5] == '-') best.put(k.substring(1), (v as? Number)?.toLong() ?: 0L)
            }
            dev.put(n.texto("fuente"), JSONObject().put("total", n.entero("total")).put("days", days).apply { if (best.length() > 0) put("best", best) })
        }
        if (dev.length() > 0) o.put("stats", ReadStats.derived(dev))
        return o
    }

    /** Une lo que llega de la cuenta con lo de este móvil ([kids]: solo esos libros; null: todos). */
    fun baja(c: Context, kids: Set<String>?) {
        val libros = Biblioteca.libros.toList().filter { kids == null || it.id in kids }
        for (l in libros) {
            val remoto = estadoRemoto(l.id)
            if (remoto.length() == 0) continue
            val local = EstadoLectura.estado(c, l.id)
            val unido = MezclaLectura.mezclar(local, remoto, Cuenta.dispositivo)
            if (!MezclaLectura.igual(unido, local)) android.os.Handler(android.os.Looper.getMainLooper()).post { EstadoLectura.ponerSinAvisar(c, l.id, unido) }
        }
    }

    /** El tiempo leído de cada libro según la cuenta: kid → (dispositivo → ms). */
    fun tiempos(): Map<String, Map<String, Long>> =
        tiempo().vivos().filter { it.texto("fuente").isNotBlank() }.groupBy { it.texto("ref") }.mapValues { (_, ns) -> ns.associate { it.texto("fuente") to it.entero("total") } }

    /** De org.json a lo de Kotlin (para la cuenta). */
    fun aKotlin(v: Any?): Any? = when (v) {
        is JSONObject -> LinkedHashMap<String, Any?>().also { m -> v.keys().forEach { k -> m[k] = aKotlin(v.get(k)) } }
        is JSONArray -> (0 until v.length()).map { aKotlin(v.get(it)) }
        JSONObject.NULL -> ""
        is Int -> v.toLong()
        is Float -> v.toDouble()
        else -> v
    }

    /** De lo de Kotlin a org.json. */
    fun aJson(v: Any?): Any? = when (v) {
        is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), aJson(x)) } }
        is List<*> -> JSONArray().also { a -> v.forEach { a.put(aJson(it)) } }
        null -> JSONObject.NULL
        else -> v
    }
}
