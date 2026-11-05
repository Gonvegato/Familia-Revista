package com.gonzalo.familia.nucleo

/**
 * Una cosa de la cuenta (un juego, un libro, una obra…): sus campos tal cual están, con la hora de cada uno (`m`) y la
 * hora del servidor de su último cambio (`u`). Los campos vacíos no existen (vacío = "" / 0 / false: nunca `null`).
 */
class Nodo(val ref: String, datos: Map<String, Any?>) {
    val campos: Map<String, Any?> = datos.filterKeys { it != M && it != U }
    val m: Map<String, Long> = datos[M].comoMapa().mapValues { it.value.comoLong() }
    val u: Long = datos[U].comoLong()

    operator fun get(campo: String): Any? = campos[campo]
    fun texto(campo: String) = campos[campo].comoTexto()
    fun entero(campo: String) = campos[campo].comoLong()
    fun decimal(campo: String) = campos[campo].comoDouble()
    fun si(campo: String) = campos[campo].comoBool()
    fun mapa(campo: String) = campos[campo].comoMapa()
    fun lista(campo: String) = campos[campo].comoLista()
    /** Borrado (con su hora en `b`): no se enseña, pero se queda para que ningún dispositivo lo resucite sin querer. */
    val borrado: Boolean get() = entero(B) > 0

    /** Todo junto, como se guarda (para el disco y las pruebas). */
    fun datos(): Map<String, Any?> = LinkedHashMap<String, Any?>(campos).also { if (m.isNotEmpty()) it[M] = m; if (u > 0) it[U] = u }

    override fun toString() = "Nodo($ref, $campos)"

    companion object {
        const val M = "m"
        const val U = "u"
        const val B = "b"
    }
}

/**
 * Una colección de la cuenta (`/users/<uid>/familia/<nombre>/<ref>`): lo que dice el servidor más lo que este dispositivo
 * ha cambiado y aún no ha subido. Cada campo es independiente y **gana el cambio más reciente** (por la hora de su `m`, que
 * también comprueban las reglas del servidor): dos dispositivos que tocan campos distintos de la misma cosa nunca se pisan.
 */
class Coleccion internal constructor(val nombre: String, internal val archivo: String, private val disco: Disco, private val soloLectura: Boolean, private val motor: Familia?) {
    /** Lo confirmado por el servidor. */
    private val servidor = LinkedHashMap<String, Map<String, Any?>>()
    /** La hora del servidor (`u`) más alta que se ha bajado: la próxima vez se pide solo lo que cambió desde ahí. */
    @Volatile internal var ultimaU = 0L
    @Volatile internal var cargadaEntera = false
    /** ¿Se ha bajado ya entera de la cuenta (al menos una vez)? Antes de eso, que algo no esté aquí no quiere decir que no exista. */
    val completa: Boolean get() = cargadaEntera
    private val oyentes = java.util.concurrent.CopyOnWriteArrayList<(Set<String>) -> Unit>()
    private val cerrojo = Any()
    @Volatile private var vista: Map<String, Nodo>? = null

    init { carga() }

    private fun carga() {
        val m = disco.leeTexto(archivo)?.let { runCatching { Json.lee(it) }.getOrNull() }.comoMapa()
        ultimaU = m["u"].comoLong(); cargadaEntera = m["entera"].comoBool()
        m["nodos"].comoMapa().forEach { (k, v) -> servidor[k] = v.comoMapa() }
    }

    internal fun guarda() {
        val texto = synchronized(cerrojo) { Json.escribe(mapOf("u" to ultimaU, "entera" to cargadaEntera, "nodos" to servidor)) }
        disco.escribeTexto(archivo, texto)
    }

    /** Todas las cosas (también las borradas, que llevan `b`). */
    fun todos(): Map<String, Nodo> = vista ?: synchronized(cerrojo) { calculaVista().also { vista = it } }

    /** Las que no están borradas. */
    fun vivos(): List<Nodo> = todos().values.filter { !it.borrado }

    fun nodo(ref: String): Nodo? = todos()[ref]

    private fun calculaVista(): Map<String, Nodo> {
        val r = LinkedHashMap<String, Map<String, Any?>>()
        servidor.forEach { (k, v) -> r[k] = v }
        motor?.pendientesDe(nombre)?.forEach { p ->
            val actual = r[p.ref] ?: emptyMap()
            r[p.ref] = superpone(actual, p.campos, p.m)
        }
        return r.mapValues { (k, v) -> Nodo(k, v) }
    }

    /**
     * Cambia campos de una cosa (la crea si no existe). Se ve al momento aquí y se sube en cuanto se pueda. [hora]: la del cambio
     * (por defecto, ahora; la migración pone la hora en que se hizo de verdad, para que gane lo más reciente venga de donde venga).
     */
    fun pon(ref: String, campos: Map<String, Any?>, hora: Long? = null) {
        check(!soloLectura) { "La colección $nombre es de solo lectura" }
        require(refValida(ref)) { "Referencia no válida: «$ref»" }
        val limpios = campos.mapValues { (_, v) -> v ?: "" }
        require(limpios.keys.all { campoValido(it) }) { "Campo no válido en ${limpios.keys}" }
        if (limpios.isEmpty()) return
        val m = (hora ?: motor!!.reloj.ahora()).coerceAtLeast(1L)
        motor!!.encola(Pendiente.Campos(nombre, ref, limpios, m))
        invalida(setOf(ref))
    }

    /** Solo los campos que de verdad cambian (comparando con lo que hay): si no cambia nada, no se sube nada. */
    fun cambia(ref: String, campos: Map<String, Any?>, hora: Long? = null) {
        val actual = nodo(ref)
        val distintos = campos.filter { (c, v) -> actual == null || !igual(actual[c], v ?: "") }
        if (distintos.isNotEmpty()) pon(ref, distintos, hora)
    }

    fun pon(ref: String, vararg campos: Pair<String, Any?>) = pon(ref, campos.toMap())

    /** Borra una cosa (se queda con la hora del borrado: si otro dispositivo la cambia después, vuelve). */
    fun borra(ref: String) = pon(ref, mapOf(Nodo.B to motor!!.reloj.ahora()))

    /** La recupera si estaba borrada. */
    fun recupera(ref: String) = pon(ref, mapOf(Nodo.B to 0L))

    fun alCambiar(oyente: (Set<String>) -> Unit): () -> Unit {
        oyentes += oyente
        return { oyentes -= oyente }
    }

    /**
     * Olvida lo de la cuenta (al salir o al cambiar de cuenta) sin dejar de ser la misma colección: quien la escuchaba sigue
     * enterándose de lo que llegue después (antes se tiraba y se creaba otra, y las apps se quedaban oyendo una que ya no cambiaba).
     */
    internal fun vacia() {
        val habia: Set<String>
        synchronized(cerrojo) { habia = servidor.keys.toSet(); servidor.clear(); ultimaU = 0L; cargadaEntera = false }
        disco.borra(archivo)
        invalida(habia)
    }

    internal fun invalida(refs: Set<String>) {
        vista = null
        if (refs.isNotEmpty()) oyentes.forEach { runCatching { it(refs) } }
    }

    /** Llega del servidor la versión entera de unas cosas: sustituyen a las que había (el servidor ya ha decidido quién gana). */
    internal fun recibe(nodos: Map<String, Any?>, completa: Boolean): Set<String> {
        val cambiados = HashSet<String>()
        synchronized(cerrojo) {
            if (completa) {
                // Lo que ya no está en el servidor (se borró a mano en la consola, por ejemplo) se quita también aquí
                (servidor.keys - nodos.keys).forEach { servidor.remove(it); cambiados += it }
                cargadaEntera = true
            }
            nodos.forEach { (k, v) ->
                val nuevo = v.comoMapa()
                if (nuevo.isEmpty()) { if (servidor.remove(k) != null) cambiados += k; return@forEach }
                if (servidor[k] != nuevo) { servidor[k] = nuevo; cambiados += k }
                ultimaU = maxOf(ultimaU, nuevo[Nodo.U].comoLong())
            }
        }
        if (cambiados.isNotEmpty()) invalida(cambiados)
        return cambiados
    }

    /** Lo que se acaba de subir con éxito pasa a ser del servidor (sin esperar a bajarlo). */
    internal fun confirma(p: Pendiente.Campos, u: Long) {
        synchronized(cerrojo) {
            val actual = servidor[p.ref] ?: emptyMap()
            servidor[p.ref] = superpone(actual, p.campos, p.m).let { if (u > 0) it + (Nodo.U to u) else it }
        }
    }

    companion object {
        /** Lo que Firebase no admite en una clave: . $ # [ ] / y caracteres de control. */
        fun refValida(r: String) = r.isNotBlank() && r.length <= 700 && r.none { it in ".$#[]/" || it.code < 32 || it.code == 127 }
        fun campoValido(c: String) = refValida(c) && c != Nodo.M && c != Nodo.U

        /** ¿Dicen lo mismo dos valores de JSON? (un 3 es un 3.0; un campo que no existe es "" / 0 / false.) */
        fun igual(a: Any?, b: Any?): Boolean {
            if (a == null || b == null) return vacio(a) && vacio(b)
            if (a is Number && b is Number) return a.toDouble() == b.toDouble()
            if (a is Map<*, *> && b is Map<*, *>) return a.keys == b.keys && a.keys.all { igual(a[it], b[it]) }
            if (a is List<*> && b is List<*>) return a.size == b.size && a.indices.all { igual(a[it], b[it]) }
            return a == b
        }
        private fun vacio(x: Any?) = x == null || x == "" || x == false || (x is Number && x.toDouble() == 0.0) || (x is Map<*, *> && x.isEmpty()) || (x is List<*> && x.isEmpty())

        /** Pone unos campos encima de un nodo, solo los que sean más nuevos que lo que ya hay. */
        internal fun superpone(actual: Map<String, Any?>, campos: Map<String, Any?>, m: Long): Map<String, Any?> {
            val r = LinkedHashMap(actual)
            val ms = LinkedHashMap(actual[Nodo.M].comoMapa())
            campos.forEach { (c, v) ->
                if (m > ms[c].comoLong()) { r[c] = v; ms[c] = m }
            }
            r[Nodo.M] = ms
            return r
        }
    }
}

/** Lo que este dispositivo ha cambiado y aún no está en el servidor (se guarda en disco: sobrevive a cerrar la app). */
sealed class Pendiente {
    abstract val id: Long

    /** Campos de una cosa de una colección, todos con la misma hora. */
    class Campos(val coleccion: String, val ref: String, val campos: Map<String, Any?>, val m: Long, override val id: Long = 0) : Pendiente() {
        fun con(id: Long) = Campos(coleccion, ref, campos, m, id)
    }

    /** Una escritura directa en una ruta de la cuenta (las imágenes, las claves): PUT o PATCH tal cual. */
    class Directa(val metodo: String, val ruta: String, val cuerpo: Any?, override val id: Long = 0) : Pendiente() {
        fun con(id: Long) = Directa(metodo, ruta, cuerpo, id)
    }

    fun aMapa(): Map<String, Any?> = when (this) {
        is Campos -> mapOf("t" to "c", "id" to id, "col" to coleccion, "ref" to ref, "campos" to campos, "m" to m)
        is Directa -> mapOf("t" to "d", "id" to id, "metodo" to metodo, "ruta" to ruta, "cuerpo" to cuerpo)
    }

    companion object {
        fun deMapa(m: Map<String, Any?>): Pendiente? = when (m["t"]) {
            "c" -> Campos(m["col"].comoTexto(), m["ref"].comoTexto(), m["campos"].comoMapa(), m["m"].comoLong(), m["id"].comoLong())
            "d" -> Directa(m["metodo"].comoTexto(), m["ruta"].comoTexto(), m["cuerpo"], m["id"].comoLong())
            else -> null
        }
    }
}
