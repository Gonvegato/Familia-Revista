package com.gonzalo.cobalto.pc

import com.gonzalo.cobalto.CopiasEscritos
import com.gonzalo.cobalto.Doc
import com.gonzalo.cobalto.EstadoObra
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.NubeEscritos
import com.gonzalo.cobalto.SprintEscribir
import com.gonzalo.cobalto.estadoDe
import com.gonzalo.cobalto.ultimaEdicion
import com.gonzalo.familia.nucleo.Red
import com.gonzalo.familia.nucleo.RedJava
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Sitio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

/** Los escritos en la cuenta de la familia del ordenador (`/users/<uid>/escritos` y `escritosIdx`, nube/ESCRITOS.md). */
object NubePC : NubeEscritos {
    private val red = RedJava()
    override val vinculada: Boolean get() = LaFamilia.iniciada && LaFamilia.familia.sesion.dentro
    override val uid: String get() = LaFamilia.uid
    override fun db(metodo: String, ruta: String, cuerpo: String?, consulta: String): String? {
        val s = LaFamilia.familia.sesion
        val q = consulta.removePrefix("&")
        fun pide(): Red.Respuesta = try { red.pedir(metodo, s.url(ruta, q, conPase = true), cuerpo) } catch (e: Red.SinRed) { throw java.io.IOException(e.message) }
        var r = pide()
        if (r.codigo == 401 && !r.cuerpo.contains("Permission denied", true)) { s.paseCaducado(); r = pide() }
        if (r.codigo == 401 || r.codigo == 403) throw java.io.IOException("La cuenta no deja hacer eso (reglas de la base de datos).")
        if (r.codigo !in 200..299) throw java.io.IOException("La cuenta respondió ${r.codigo}.")
        val t = r.cuerpo.trim()
        return if (t.isEmpty() || t == "null") null else t
    }
}

/**
 * Lo que escribes en este ordenador: los mismos [Escritos] que el móvil (el mismo código), con la subida en cuanto cambias algo, la
 * escucha en directo de lo que escribes en otro sitio, lo que se publica para Azabache y Marfil ([Publica]) y las copias de seguridad
 * completas en este ordenador ([Copias]).
 */
object Cuaderno {
    @Volatile private var e: Escritos? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var programada: Job? = null
    @Volatile private var fallos = 0
    @Volatile private var escuchando = false
    @Volatile private var ordenada = 0L

    /** En las pruebas: otra carpeta y sin escucha. */
    @Volatile var dirPruebas: File? = null
    /** Este ordenador para las palabras de cada día (`palabras/<dispositivo>`), como `Cuenta.dispositivo` en Android. */
    @Volatile var dispositivo: () -> String = { Sitio.dispositivo("cobalto") }

    val escritos: Escritos get() = e ?: synchronized(this) {
        e ?: Escritos(File(dirPruebas ?: Sitio.de("cobalto"), "escritos"), NubePC) { dispositivo() }.also { x ->
            x.alCambiar = { pronto() }
            e = x
        }
    }

    /** Al arrancar la app (después de LaFamilia.inicia). */
    fun inicia() {
        val x = escritos
        // Los sprints, en la cuenta (los de este ordenador de antes pasan a ella en cuanto se ha bajado lo que ya había: [VigilaSprint])
        SprintEscribir.cuenta = CuentaSprintsPC
        runCatching { LaFamilia.coleccion(SprintEscribir.COLECCION) }
        LaFamilia.alEntrar { x.cuenta(LaFamilia.uid); pronto(0) }
        if (NubePC.vinculada) { x.cuenta(LaFamilia.uid); pronto(0) }
        escucha()
        Copias.inicia(x)
    }

    /** Sube lo pendiente y baja lo nuevo dentro de [ms] (los cambios seguidos se juntan). Sin conexión se reintenta solo. */
    fun pronto(ms: Long = Escritos.ESPERA) {
        if (!NubePC.vinculada) return
        synchronized(this) {
            programada?.cancel()
            programada = scope.launch { delay(ms); withContext(NonCancellable) { sincroniza() } }
        }
    }

    /** Sube y baja ahora (y publica para las hermanas). Devuelve false si no ha podido (se reintenta solo). */
    suspend fun sincroniza(): Boolean {
        if (!NubePC.vinculada) return true
        val x = escritos
        return try {
            x.sincroniza()
            fallos = 0
            val diferido = runCatching { Publica.todo(x) }.getOrDefault(false)
            if (System.currentTimeMillis() - ordenada > 6 * 3_600_000L) { ordenada = System.currentTimeMillis(); x.purga(); runCatching { x.limpia() } }
            if (x.pendientes().isNotEmpty()) pronto() else if (diferido) pronto(Publica.QUIETO)
            true
        } catch (ex: Exception) {
            fallos++
            x.error = ex.message ?: "sin conexión"
            if (fallos <= 12) pronto(minOf(300_000L, 5_000L * Math.pow(3.0, (fallos - 1).coerceAtMost(6).toDouble()).toLong()))
            false
        }
    }

    /** Escucha en directo el índice de los escritos (unos bytes): en cuanto cambian en el móvil, se bajan. */
    private fun escucha() {
        if (escuchando || dirPruebas != null) return
        escuchando = true
        Thread({
            var espera = 2_000L
            val red = RedJava()
            while (escuchando) {
                try {
                    if (!NubePC.vinculada) { Thread.sleep(5_000); continue }
                    val url = LaFamilia.familia.sesion.url("/users/${NubePC.uid}/escritosIdx", conPase = true)
                    red.escuchar(url, { escuchando }) { evento, _ ->
                        when (evento) {
                            "put", "patch" -> { espera = 2_000L; pronto(300) }
                            "auth_revoked", "cancel" -> { LaFamilia.familia.sesion.paseCaducado(); throw Red.SinRed("El flujo se cerró ($evento)") }
                        }
                    }
                } catch (e: InterruptedException) { break } catch (e: Exception) { }
                if (!escuchando) break
                try { Thread.sleep(espera) } catch (e: InterruptedException) { break }
                espera = (espera * 2).coerceAtMost(60_000L)
            }
        }, "cobalto-escucha").apply { isDaemon = true; start() }
    }

    /** Al cerrar: lo que falte, a la cuenta (como mucho unos segundos) y una copia de seguridad. */
    fun alCerrar() {
        escuchando = false
        val x = e ?: return
        runCatching { Copias.haz(x, "al cerrar") }
        runBlocking { withTimeoutOrNull(6_000) { if (x.pendientes().isNotEmpty()) sincroniza() } }
        x.vacia()
    }

    fun reiniciaParaPruebas() { synchronized(this) { e?.parar(); e = null; programada?.cancel(); fallos = 0; ordenada = 0L } }
}

/**
 * Lo que Cobalto pone en la cuenta de la familia para Azabache y Marfil (lo mismo que `Publicar.todo` de Android, ver nube/ESQUEMA.md):
 * `obras/<obra>`, `capitulos/<obra>~<capítulo>` (la copia de solo lectura de cada capítulo terminado) y `palabras/<dispositivo>`.
 */
object Publica {
    const val QUIETO = 20_000L

    fun hecho(e: Escritos, c: Doc): Boolean = c.b("hecho") || (e.get(c.s("aid"))?.b("hecho") == true)

    fun textoCapitulo(e: Escritos, cid: String): String = e.escenas(cid).map { it.s("texto").trim() }.filter { it.isNotEmpty() }.joinToString("\n\n***\n\n")

    fun capitulosTerminados(e: Escritos): Map<String, Map<String, JSONObject>> {
        val out = HashMap<String, HashMap<String, JSONObject>>()
        for (o in e.obras()) {
            e.capitulos(o.id).forEachIndexed { i, c ->
                if (!hecho(e, c)) return@forEachIndexed
                val texto = textoCapitulo(e, c.id)
                val parte = e.get(c.s("aid"))
                out.getOrPut(o.id) { HashMap() }[c.id] = JSONObject()
                    .put("t", o.s("titulo")).put("a", o.s("autor")).put("c", c.s("titulo")).put("n", i + 1)
                    .apply { if (parte != null) put("pa", parte.s("titulo")) }
                    .put("u", maxOf(c.u, e.escenas(c.id).maxOfOrNull { it.u } ?: 0L)).put("p", Escritos.palabras(texto)).put("x", texto)
            }
        }
        return out
    }

    fun palabrasPorDia(e: Escritos, dispositivo: String): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (d in e.todos().filter { it.t == Escritos.T_LOG }) {
            val n = d.o.optJSONObject("dev")?.optInt(dispositivo) ?: 0
            if (n > 0) { val f = java.time.LocalDate.ofEpochDay(d.n("dia")).toString(); out[f] = (out[f] ?: 0) + n }
        }
        return out
    }

    fun estadoEnCuenta(o: Doc): String = when (estadoDe(o)) { EstadoObra.ESCRIBIENDO -> "en_curso"; EstadoObra.PAUSA -> "pausa"; EstadoObra.TERMINADA -> "terminado"; EstadoObra.ARCHIVO -> "archivada" }

    /** Publica lo que haya cambiado (solo eso, campo a campo). Devuelve si quedó algo para luego (un capítulo que aún estás tocando). */
    fun todo(e: Escritos, dispositivo: String = Cuaderno.dispositivo(), ahora: Long = System.currentTimeMillis()): Boolean {
        if (!NubePC.vinculada) return false
        var diferido = false
        val obras = LaFamilia.coleccion("obras")
        val vivas = e.obras().map { it.id }.toSet()
        for (o in e.obras()) obras.cambia(o.id, mapOf("t" to o.s("titulo"), "a" to o.s("autor"), "estado" to estadoEnCuenta(o), "palabras" to e.palabrasObra(o.id).toLong(), "ul" to e.ultimaEdicion(o.id), "b" to 0L))
        obras.vivos().filter { it.ref !in vivas }.forEach { obras.borra(it.ref) }
        val caps = LaFamilia.coleccion("capitulos")
        val quiero = HashSet<String>()
        capitulosTerminados(e).forEach { (pid, cs) -> cs.forEach { (cid, doc) ->
            val ref = "$pid~$cid"; quiero += ref
            val actual = caps.nodo(ref)
            if (actual != null && !actual.borrado && ahora - doc.optLong("u") < QUIETO && actual.texto("x") != doc.optString("x")) { diferido = true; return@forEach }
            caps.cambia(ref, mapOf("ref" to pid, "t" to doc.optString("t"), "a" to doc.optString("a"), "c" to doc.optString("c"), "n" to doc.optInt("n").toLong(),
                "pa" to doc.optString("pa"), "ue" to doc.optLong("u"), "p" to doc.optInt("p").toLong(), "x" to doc.optString("x"), "b" to 0L))
        } }
        caps.vivos().filter { it.ref !in quiero }.forEach { caps.borra(it.ref) }
        val ultimoLog = e.todos().filter { it.t == Escritos.T_LOG }.maxOfOrNull { it.u } ?: 0L
        val pal = LaFamilia.coleccion("palabras")
        if (ahora - ultimoLog < QUIETO && pal.nodo(dispositivo) != null) diferido = true
        else palabrasPorDia(e, dispositivo).takeIf { it.isNotEmpty() || pal.nodo(dispositivo) != null }?.let { d -> pal.cambia(dispositivo, d.mapKeys { "d${it.key}" }.mapValues { it.value.toLong() }) }
        return diferido
    }
}

/**
 * Copias de seguridad completas en este ordenador (una novela no se puede volver a escribir): la primera a los 20 s de abrir, luego cada
 * 15 minutos si algo ha cambiado y al cerrar. Se guardan las últimas 40, con todos los documentos (también la papelera). Como las de Atrio
 * del PC (nube/ESCRITOS.md → «Copias de seguridad»), y las mismas que las del móvil: la lógica es la de EscribirCopias.kt (el mismo
 * archivo), y de cualquiera se saca una obra como obra nueva (Ajustes → Copias).
 */
object Copias {
    const val GUARDA = 40
    fun carpeta(): File = File(Cuaderno.dirPruebas ?: Sitio.de("cobalto"), "copias").also { it.mkdirs() }
    private val copias = CopiasEscritos({ carpeta() }, GUARDA)

    fun inicia(e: Escritos) {
        Thread({
            try {
                Thread.sleep(20_000)
                while (true) { runCatching { haz(e, "") }; Thread.sleep(15 * 60_000L) }
            } catch (_: InterruptedException) {}
        }, "cobalto-copias").apply { isDaemon = true; start() }
    }

    /** Hace una copia si algo ha cambiado desde la última ([porque] no vacío: aunque no). Devuelve el archivo (o null si no hacía falta). */
    fun haz(e: Escritos, porque: String): File? = copias.haz(e, forzar = porque.isNotEmpty())

    fun lista(): List<File> = copias.lista().map { it.archivo }
    fun copias(): List<CopiasEscritos.Copia> = copias.lista()
    fun lee(f: File): List<JSONObject> = copias.lee(f)
}
