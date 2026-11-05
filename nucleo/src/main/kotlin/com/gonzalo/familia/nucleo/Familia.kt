package com.gonzalo.familia.nucleo

import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * La cuenta de la familia: la única verdad que comparten Hiedra, Marfil, Cobalto, Azabache (y lo que venga, en el móvil o en
 * el PC). Todo cuelga de `/users/<uid>/`:
 *
 *  - `familia/<colección>/<ref>`: cada cosa una sola vez, campo a campo, con la hora de cada campo en `m` y la del servidor en `u`.
 *  - `familia_sello/<colección>`: la hora del último cambio de cada colección (lo único que se escucha en directo).
 *  - `familia_img/<hash>`: las imágenes (portadas), por su contenido: nunca cambian, así que no hay nada que mezclar.
 *  - `/claves/<uid>` (fuera de `users`, para que tu pareja no lo lea): las claves de los servicios, una vez para todas las apps.
 *
 * Funciona sin conexión: lo que cambias se ve al momento, se guarda en disco y se sube en cuanto hay red, en orden. Ver
 * `nube/ESQUEMA.md` y `nube/reglas.json`.
 */
class Familia(
    val cfg: Config,
    private val red: Red,
    private val disco: Disco,
    seguro: Disco = disco,
    val reloj: RelojSistema = RelojSistema(),
) {
    val sesion = Sesion(cfg, red, seguro, reloj)
    private val seguroDisco = seguro

    private val colecciones = java.util.concurrent.ConcurrentHashMap<String, Coleccion>()
    private val ajenas = java.util.concurrent.ConcurrentHashMap<String, Coleccion>()

    // ---------- Lo pendiente de subir ----------

    private val cola = ArrayList<Pendiente>()
    private var siguienteId = 1L
    private val cerrojoCola = Any()

    init {
        cargaCola()
        // Las colecciones que ya usaba esta app se abren solas: un trabajo de fondo, con la app cerrada, también las baja
        disco.leeTexto(ABIERTAS)?.let { runCatching { Json.lee(it) }.getOrNull() }.comoLista().forEach { n ->
            val t = n.comoTexto()
            if (t.contains('/')) { val (u, c) = t.split('/', limit = 2); if (Coleccion.refValida(u) && Coleccion.refValida(c)) ajenas[t] = Coleccion(c, "ajena_${u}_$c.json", disco, true, null) }
            else if (Coleccion.refValida(t)) colecciones[t] = Coleccion(t, "col_$t.json", disco, false, this)
        }
    }

    private fun guardaAbiertas() {
        disco.escribeTexto(ABIERTAS, Json.escribe((colecciones.keys + ajenas.keys).sorted()))
    }

    private fun cargaCola() {
        val l = disco.leeTexto(COLA)?.let { runCatching { Json.lee(it) }.getOrNull() }.comoLista()
        synchronized(cerrojoCola) {
            cola.clear()
            l.mapNotNullTo(cola) { Pendiente.deMapa(it.comoMapa()) }
            siguienteId = (cola.maxOfOrNull { it.id } ?: 0L) + 1
        }
    }

    private fun guardaCola() {
        val texto = synchronized(cerrojoCola) { Json.escribe(cola.map { it.aMapa() }) }
        disco.escribeTexto(COLA, texto)
    }

    internal fun encola(p: Pendiente) {
        synchronized(cerrojoCola) {
            cola += when (p) { is Pendiente.Campos -> p.con(siguienteId++); is Pendiente.Directa -> p.con(siguienteId++) }
        }
        guardaCola()
        alEncolar?.invoke()
        programa(1_500)
    }

    /** Cuántos cambios faltan por subir (para enseñarlo si no hay red). */
    val pendientes: Int get() = synchronized(cerrojoCola) { cola.size }

    internal fun pendientesDe(coleccion: String): List<Pendiente.Campos> = synchronized(cerrojoCola) {
        cola.filterIsInstance<Pendiente.Campos>().filter { it.coleccion == coleccion }
    }

    /** Se llama al encolar algo (en Android, para pedir un trabajo de fondo que lo suba aunque cierres la app). */
    @Volatile var alEncolar: (() -> Unit)? = null

    // ---------- Colecciones ----------

    /** Una colección de tu cuenta («juegos», «libros», «obras», «fichas», «tiempo»…). */
    fun coleccion(nombre: String): Coleccion {
        require(Coleccion.refValida(nombre))
        colecciones[nombre]?.let { return it }
        val c = colecciones.getOrPut(nombre) { Coleccion(nombre, "col_$nombre.json", disco, false, this) }
        guardaAbiertas()
        if (!c.cargadaEntera && sesion.dentro) programa(0)
        return c
    }

    /** Una colección de otra cuenta (tu pareja), solo para leer. */
    fun ajena(uid: String, nombre: String): Coleccion {
        require(Coleccion.refValida(uid) && Coleccion.refValida(nombre))
        ajenas["$uid/$nombre"]?.let { return it }
        val c = ajenas.getOrPut("$uid/$nombre") { Coleccion(nombre, "ajena_${uid}_$nombre.json", disco, true, null) }
        guardaAbiertas()
        if (!c.cargadaEntera && sesion.dentro) programa(0)
        return c
    }

    // ---------- Sincronizar ----------

    class Informe(val subidos: Int, val rechazados: Int, val bajados: Int, val error: String?) {
        val ok: Boolean get() = error == null
        override fun toString() = "Informe(subidos=$subidos, rechazados=$rechazados, bajados=$bajados, error=$error)"
    }

    @Volatile var ultimoInforme: Informe? = null; private set
    /** Cada vez que termina una sincronización (bien o mal). */
    @Volatile var alSincronizar: ((Informe) -> Unit)? = null

    private val cerrojoSincro = Any()

    /**
     * Sube lo pendiente y baja lo que haya cambiado (solo lo que cambió). Bloquea: llámalo fuera del hilo de la pantalla
     * (en Android, desde un trabajo de fondo o una corrutina de IO). Se puede llamar todas las veces que haga falta.
     */
    fun sincroniza(): Informe = synchronized(cerrojoSincro) {
        if (!sesion.dentro) return Informe(0, 0, 0, "Sin cuenta").also { ultimoInforme = it }
        var subidos = 0; var rechazados = 0; var bajados = 0
        val informe = try {
            val (s, r, refrescar) = subeTodo()
            subidos = s; rechazados = r
            bajados = bajaTodo(refrescar)
            Informe(subidos, rechazados, bajados, null)
        } catch (e: Red.SinRed) {
            Informe(subidos, rechazados, bajados, "Sin conexión")
        } catch (e: Sesion.Error) {
            Informe(subidos, rechazados, bajados, e.message ?: "La cuenta no responde")
        } catch (e: Exception) {
            Informe(subidos, rechazados, bajados, e.message ?: e.javaClass.simpleName)
        }
        ultimoInforme = informe
        runCatching { alSincronizar?.invoke(informe) }
        informe
    }

    /** Sube la cola, en orden. Devuelve (subidos, rechazados, cosas que hay que volver a bajar). */
    private fun subeTodo(): Triple<Int, Int, Set<Pair<String, String>>> {
        var subidos = 0; var rechazados = 0
        val refrescar = HashSet<Pair<String, String>>()
        var reintentosPase = 0
        while (true) {
            val lote = synchronized(cerrojoCola) { siguienteLote() }
            if (lote.isEmpty()) break
            val r = envia(lote).let { if (it.codigo == 401 && esRechazo(it.cuerpo)) Red.Respuesta(403, it.cuerpo, it.fecha) else it }
            when {
                r.ok -> {
                    val u = uDe(r.cuerpo)
                    if (u > 0) reloj.ajusta(u)
                    lote.forEach { p -> if (p is Pendiente.Campos) colecciones[p.coleccion]?.confirma(p, u) }
                    quita(lote)
                    subidos += lote.size
                    lote.filterIsInstance<Pendiente.Campos>().map { it.coleccion }.toSet().forEach { colecciones[it]?.let { c -> c.invalida(emptySet()); c.guarda() } }
                }
                r.codigo == 401 -> {
                    // El pase ha caducado antes de lo previsto: se renueva una vez y se reintenta
                    if (reintentosPase++ > 1) throw Sesion.Error("La cuenta no acepta el pase.")
                    sesion.paseCaducado()
                }
                r.codigo == 403 || r.codigo == 400 -> {
                    if (lote.size > 1) {
                        // Algo del lote no vale: se mandan uno a uno para saber qué
                        trocea(lote)
                        continue
                    }
                    val p = lote.first()
                    if (p is Pendiente.Campos && p.campos.size > 1 && r.codigo == 403) {
                        // Algún campo es más viejo que el del servidor: se separa para que el resto sí suba
                        synchronized(cerrojoCola) {
                            val i = cola.indexOfFirst { it.id == p.id }
                            if (i >= 0) { cola.removeAt(i); cola.addAll(i, p.campos.map { (c, v) -> Pendiente.Campos(p.coleccion, p.ref, mapOf(c to v), p.m, siguienteId++) }) }
                        }
                        guardaCola()
                        continue
                    }
                    // Rechazado del todo: el servidor tiene algo más nuevo (o la escritura no es válida). Se descarta y se baja lo bueno.
                    quita(lote)
                    rechazados++
                    if (p is Pendiente.Campos) { refrescar += p.coleccion to p.ref; colecciones[p.coleccion]?.invalida(setOf(p.ref)) }
                }
                r.codigo in 500..599 || r.codigo == 429 -> throw Red.SinRed("El servidor está ocupado (${r.codigo})")
                else -> {
                    quita(lote); rechazados += lote.size
                }
            }
        }
        return Triple(subidos, rechazados, refrescar)
    }

    /** Hasta 100 cambios de campos seguidos en un solo envío (o una escritura directa sola, o uno suelto si su lote falló). */
    private fun siguienteLote(): List<Pendiente> {
        val primero = cola.firstOrNull() ?: return emptyList()
        if (primero is Pendiente.Directa || primero.id in sueltos || primero.tamano() > 200_000) return listOf(primero)
        val lote = ArrayList<Pendiente>()
        var tam = 0
        for (p in cola) {
            if (p !is Pendiente.Campos || p.id in sueltos || lote.size >= 100) break
            tam += p.tamano()
            if (lote.isNotEmpty() && tam > 400_000) break
            lote += p
        }
        return lote
    }

    private fun Pendiente.tamano(): Int = when (this) {
        is Pendiente.Campos -> campos.values.sumOf { Json.escribe(it).length } + 64
        is Pendiente.Directa -> Json.escribe(cuerpo).length
    }

    /** Los que se mandan solos (su lote fue rechazado: así se sabe cuál tenía la culpa). */
    private val sueltos = HashSet<Long>()
    private fun trocea(lote: List<Pendiente>) { lote.forEach { sueltos += it.id } }

    private fun quita(lote: List<Pendiente>) {
        val ids = lote.map { it.id }.toSet()
        synchronized(cerrojoCola) { cola.removeAll { it.id in ids } }
        sueltos.removeAll(ids)
        guardaCola()
    }

    private fun envia(lote: List<Pendiente>): Red.Respuesta {
        val p = lote.first()
        if (p is Pendiente.Directa) {
            return red.pedir(p.metodo, sesion.url(p.ruta, conPase = true), Json.escribe(p.cuerpo))
        }
        val cuerpo = LinkedHashMap<String, Any?>()
        val ahoraServidor = mapOf(".sv" to "timestamp")
        lote.forEach { x ->
            x as Pendiente.Campos
            x.campos.forEach { (c, v) ->
                cuerpo["familia/${x.coleccion}/${x.ref}/$c"] = v
                cuerpo["familia/${x.coleccion}/${x.ref}/m/$c"] = x.m
            }
            cuerpo["familia/${x.coleccion}/${x.ref}/u"] = ahoraServidor
            cuerpo["familia_sello/${x.coleccion}"] = ahoraServidor
        }
        return red.pedir("PATCH", sesion.url("/users/${sesion.uid}", conPase = true), Json.escribe(cuerpo))
    }

    /** Firebase responde 401 tanto si el pase caducó como si las reglas rechazan la escritura: se distingue por el mensaje. */
    private fun esRechazo(cuerpo: String) = cuerpo.contains("Permission denied", ignoreCase = true)

    /** La hora del servidor que se acaba de poner (la `u` de la respuesta a un PATCH). */
    private fun uDe(cuerpo: String): Long {
        val m = runCatching { Json.lee(cuerpo) }.getOrNull().comoMapa()
        var u = 0L
        fun busca(x: Any?, prof: Int) {
            if (prof > 6) return
            x.comoMapa().forEach { (k, v) -> if (k == "u" && v is Number) u = maxOf(u, v.toLong()) else if (v is Map<*, *>) busca(v, prof + 1) }
        }
        busca(m, 0)
        return u
    }

    /** Baja lo que cambió en las colecciones abiertas (las tuyas y las de tu pareja) y lo que haya que refrescar. */
    private fun bajaTodo(refrescar: Set<Pair<String, String>>): Int {
        var bajados = 0
        val sellos = getJson("/users/${sesion.uid}/familia_sello").comoMapa()
        for (c in colecciones.values.toList()) {
            val sello = sellos[c.nombre].comoLong()
            if (c.cargadaEntera && sello in 1..c.ultimaU) continue
            bajados += bajaColeccion("/users/${sesion.uid}/familia/${c.nombre}", c)
        }
        for ((col, ref) in refrescar) {
            val c = colecciones[col] ?: continue
            val v = getJson("/users/${sesion.uid}/familia/$col/$ref")
            c.recibe(mapOf(ref to v), completa = false)
            c.guarda()
            bajados++
        }
        if (ajenas.isNotEmpty()) {
            val porUid = ajenas.entries.groupBy({ it.key.substringBefore('/') }, { it.value })
            for ((uid, cs) in porUid) {
                val sellosAjenos = runCatching { getJson("/users/$uid/familia_sello").comoMapa() }.getOrDefault(emptyMap())
                for (c in cs) {
                    val sello = sellosAjenos[c.nombre].comoLong()
                    if (c.cargadaEntera && sello in 1..c.ultimaU) continue
                    bajados += runCatching { bajaColeccion("/users/$uid/familia/${c.nombre}", c) }.getOrDefault(0)
                }
            }
        }
        if (sellos[CLAVES].comoLong() > clavesVistas) bajaClaves(sellos[CLAVES].comoLong())
        return bajados
    }

    /** La primera vez, entera; después, solo lo que cambió desde la última `u` (las reglas tienen el índice `.indexOn: u`). */
    private fun bajaColeccion(ruta: String, c: Coleccion): Int {
        if (c.cargadaEntera && c.ultimaU > 0) {
            val consulta = "orderBy=" + URLEncoder.encode("\"u\"", "UTF-8") + "&startAt=" + (c.ultimaU - MARGEN)
            val r = red.pedir("GET", sesion.url(ruta, consulta, conPase = true))
            if (r.codigo == 401) { sesion.paseCaducado(); throw Red.SinRed("Pase caducado") }
            if (r.ok) {
                val n = runCatching { Json.lee(r.cuerpo) }.getOrNull().comoMapa()
                val cambios = c.recibe(n, completa = false)
                c.guarda()
                return cambios.size
            }
            // Sin índice (reglas viejas): se baja entera
            if (r.codigo != 400) throw Red.SinRed("La cuenta respondió ${r.codigo}")
        }
        val todo = getJson(ruta).comoMapa()
        val cambios = c.recibe(todo, completa = true)
        c.guarda()
        return cambios.size
    }

    private fun getJson(ruta: String): Any? {
        val r = red.pedir("GET", sesion.url(ruta, conPase = true))
        if (r.codigo == 401) { sesion.paseCaducado(); val r2 = red.pedir("GET", sesion.url(ruta, conPase = true)); if (r2.ok) return Json.lee(r2.cuerpo) }
        if (!r.ok) throw Red.SinRed("La cuenta respondió ${r.codigo} en $ruta")
        return runCatching { Json.lee(r.cuerpo) }.getOrNull()
    }

    // ---------- Al momento ----------

    private val hilo = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "familia-sincro").apply { isDaemon = true } }
    private var programada: ScheduledFuture<*>? = null
    @Volatile private var escuchando = false
    @Volatile private var hiloEscucha: Thread? = null

    /** Sincronizar solo al cambiar algo o al abrir una colección (en las pruebas se apaga para mandar ellas). */
    @Volatile var automatico = true

    /** Sincroniza dentro de un momento (se juntan los cambios seguidos en una sola subida). */
    @Synchronized
    fun programa(retraso: Long = 0) {
        if (!automatico && !escuchando) return
        programada?.cancel(false)
        programada = hilo.schedule({ sincroniza() }, retraso, TimeUnit.MILLISECONDS)
    }

    /**
     * Escucha la cuenta en directo (solo los sellos: unos bytes) y sincroniza en cuanto cambia algo, venga de esta app, de
     * otra de la familia o de otro dispositivo. Mientras la app se ve; al ocultarse, [deja].
     */
    @Synchronized
    fun escucha() {
        if (escuchando) return
        escuchando = true
        hiloEscucha = Thread({
            var espera = 2_000L
            while (escuchando) {
                try {
                    if (!sesion.dentro) { Thread.sleep(5_000); continue }
                    programa(0)
                    red.escuchar(sesion.url("/users/${sesion.uid}/familia_sello", conPase = true), { escuchando }) { evento, _ ->
                        when (evento) {
                            "put", "patch" -> { espera = 2_000L; programa(300) }
                            "auth_revoked", "cancel" -> { sesion.paseCaducado(); throw Red.SinRed("El flujo se cerró ($evento)") }
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    // sin red, pase caducado o flujo cortado: se vuelve a intentar con calma
                }
                if (!escuchando) break
                try { Thread.sleep(espera) } catch (e: InterruptedException) { break }
                espera = (espera * 2).coerceAtMost(60_000L)
            }
        }, "familia-escucha").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun deja() {
        escuchando = false
        hiloEscucha?.interrupt()
        hiloEscucha = null
    }

    // ---------- Imágenes ----------

    /**
     * Las imágenes de la cuenta (las portadas): se guardan por su contenido (`familia_img/<hash>`), así que una imagen nunca
     * cambia y todas las apps y dispositivos ven exactamente la misma. Una cosa apunta a su imagen por el hash.
     */
    inner class Imagenes {
        /** Guarda una imagen (ya reducida: unos 600×900 en WebP o JPEG) y devuelve su hash. Se sube en cuanto se pueda. */
        fun guarda(bytes: ByteArray, tipo: String = "image/webp"): String {
            val h = hash(bytes)
            if (!disco.existe("img/$h")) disco.escribe("img/$h", bytes)
            encola(Pendiente.Directa("PUT", "/users/${sesion.uid}/familia_img/$h", mapOf("d" to Base64.getEncoder().encodeToString(bytes), "t" to tipo, "s" to bytes.size)))
            return h
        }

        /** La imagen si ya está en el dispositivo (sin red). */
        fun local(h: String): ByteArray? = if (h.isBlank()) null else disco.lee("img/$h")

        /** La imagen: del dispositivo, o de la cuenta (la tuya o la de tu pareja). Bloquea; null si no hay red o no existe. */
        fun baja(h: String, deUid: String = sesion.uid): ByteArray? {
            if (h.isBlank() || !Coleccion.refValida(h)) return null
            local(h)?.let { return it }
            if (!sesion.dentro) return null
            val m = try { getJson("/users/$deUid/familia_img/$h").comoMapa() } catch (e: Exception) { return null }
            val d = m["d"].comoTexto()
            if (d.isBlank()) return null
            val bytes = runCatching { Base64.getDecoder().decode(d) }.getOrNull() ?: return null
            if (hash(bytes) != h) return null
            disco.escribe("img/$h", bytes)
            return bytes
        }

        fun hash(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(40)
    }

    val imagenes = Imagenes()

    // ---------- Claves de servicios ----------

    @Volatile private var clavesVistas = 0L
    @Volatile private var clavesCache: Map<String, Any?> = seguro.leeTexto(CLAVES_ARCHIVO)?.let { runCatching { Json.lee(it) }.getOrNull() }.comoMapa()

    /**
     * Las claves de los servicios (kDrive, SteamGridDB, Steam, IGDB, RAWG…), una vez para toda la familia: se ponen en
     * Azabache y las demás apps las tienen solas. Viven en `/claves/<uid>`, que solo puede leer tu sesión (ni tu pareja).
     */
    fun claves(): Map<String, Any?> = clavesCache

    fun clave(nombre: String): String = clavesCache[nombre].comoTexto()

    fun ponClave(nombre: String, valor: Any?) {
        require(Coleccion.refValida(nombre))
        clavesCache = clavesCache + (nombre to (valor ?: ""))
        seguroDisco.escribeTexto(CLAVES_ARCHIVO, Json.escribe(clavesCache))
        encola(Pendiente.Directa("PATCH", "/claves/${sesion.uid}", mapOf(nombre to (valor ?: ""))))
        encola(Pendiente.Directa("PUT", "/users/${sesion.uid}/familia_sello/$CLAVES", mapOf(".sv" to "timestamp")))
    }

    private fun bajaClaves(sello: Long) {
        val r = red.pedir("GET", sesion.url("/claves/${sesion.uid}", conPase = true))
        if (!r.ok) return
        val m = runCatching { Json.lee(r.cuerpo) }.getOrNull().comoMapa()
        // Lo que aún no se ha subido de aquí manda sobre lo bajado
        val pendientes = synchronized(cerrojoCola) { cola.filterIsInstance<Pendiente.Directa>().filter { it.ruta == "/claves/${sesion.uid}" }.flatMap { it.cuerpo.comoMapa().entries }.associate { it.key to it.value } }
        clavesCache = m + pendientes
        seguroDisco.escribeTexto(CLAVES_ARCHIVO, Json.escribe(clavesCache))
        clavesVistas = sello
        runCatching { alCambiarClaves?.invoke() }
    }

    @Volatile var alCambiarClaves: (() -> Unit)? = null

    /** Al entrar o salir de la cuenta: se olvida todo lo de la sesión anterior (otra cuenta no debe ver nada de esta). */
    fun olvidaTodo() {
        synchronized(cerrojoCola) { cola.clear() }
        disco.borra(COLA)
        colecciones.values.forEach { disco.borra(it.archivo) }
        ajenas.values.forEach { disco.borra(it.archivo) }
        colecciones.clear(); ajenas.clear()
        disco.borra(ABIERTAS)
        clavesCache = emptyMap(); clavesVistas = 0L
        seguroDisco.borra(CLAVES_ARCHIVO)
    }

    companion object {
        const val COLA = "pendiente.json"
        const val ABIERTAS = "abiertas.json"
        const val CLAVES = "claves"
        const val CLAVES_ARCHIVO = "claves.json"
        /** Margen al pedir «lo que cambió desde»: se repiten unos segundos por si dos cambios llevaban casi la misma hora. */
        const val MARGEN = 5_000L
    }
}
