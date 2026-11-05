package com.gonzalo.cobalto

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Lo que Cobalto pone en la cuenta de la familia para Azabache y Marfil (ver nube/ESQUEMA.md), nunca los documentos de Escribir:
 *  - `obras/<obra>`: cada obra (título, autor, estado, palabras, última edición): la misma ficha que valora Azabache;
 *  - `capitulos/<obra>~<capítulo>`: una copia de SOLO LECTURA de cada capítulo terminado («hecho» en el capítulo o en su parte),
 *    la que lee Marfil; al quitar la marca, se borra;
 *  - `palabras/<dispositivo>`: las palabras escritas cada día en este dispositivo (`d<AAAA-MM-DD>` → palabras).
 * Se publica tras cada sincronización de Escribir; solo lo que ha cambiado (campo a campo).
 */
object Publicar {
    private fun prefs(c: Context) = c.getSharedPreferences("cobalto_publicar", Context.MODE_PRIVATE)

    /** Los 16 primeros hex del SHA-1 (para que un texto largo tenga una firma corta). */
    fun firma(s: String): String = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    /** Un día (días desde 1970, en la hora de aquí) como «AAAA-MM-DD». */
    fun fecha(dia: Long): String = java.time.LocalDate.ofEpochDay(dia).toString()

    /** El texto de un capítulo tal como se lee: sus escenas, separadas por un salto de escena. */
    fun textoCapitulo(e: Escritos, cid: String): String = e.escenas(cid).map { it.s("texto").trim() }.filter { it.isNotEmpty() }.joinToString("\n\n***\n\n")

    /** Lo que debe haber publicado de cada capítulo terminado: obra → capítulo → documento. */
    fun capitulosTerminados(e: Escritos): Map<String, Map<String, JSONObject>> {
        val out = HashMap<String, HashMap<String, JSONObject>>()
        for (o in e.obras()) {
            val caps = e.capitulos(o.id)
            caps.forEachIndexed { i, c ->
                if (!Compartir.hecho(e, c)) return@forEachIndexed
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

    /** Las palabras de cada día de este dispositivo. */
    fun palabrasPorDia(e: Escritos): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (d in e.todos().filter { it.t == Escritos.T_LOG }) {
            val n = d.o.optJSONObject("dev")?.optInt(Cuenta.dispositivo) ?: 0
            if (n > 0) out[fecha(d.n("dia"))] = (out[fecha(d.n("dia"))] ?: 0) + n
        }
        return out
    }

    /** Publica lo que haya cambiado (bloqueante, con red): llamar fuera del hilo principal. */
    /** Un capítulo terminado que se sigue tocando no se publica hasta que lleva este rato quieto (si no, cada frase sería un PATCH y un sello). */
    const val QUIETO = 20_000L

    /**
     * Lo que hay que mandar (un PATCH sobre `/users/<uid>/cobalto/`) y las firmas que quedan después. [diferido]: hay un capítulo cambiado
     * que aún no lleva [QUIETO] quieto; se manda en la siguiente. Sin red: se puede probar.
     */
    class Envio(val cuerpo: JSONObject, val firmasLectura: JSONObject, val firmaPalabras: String, val diferido: Boolean = false)

    fun envio(e: Escritos, firmas: JSONObject, firmaPalabrasPrevia: String, dispositivo: String, ahora: Long = System.currentTimeMillis()): Envio {
        val cuerpo = JSONObject()
        var diferido = false
        // ---- Los capítulos terminados
        val quiero = capitulosTerminados(e)
        val nuevas = JSONObject()
        quiero.forEach { (pid, caps) -> caps.forEach { (cid, doc) ->
            val f = firma(doc.toString())
            val previa = firmas.optString("$pid/$cid")
            if (previa == f) { nuevas.put("$pid/$cid", f); return@forEach }
            if (previa.isNotBlank() && ahora - doc.optLong("u") < QUIETO) { nuevas.put("$pid/$cid", previa); diferido = true; return@forEach }
            nuevas.put("$pid/$cid", f); cuerpo.put("lectura/$pid/$cid", doc)
        } }
        for (k in firmas.keys()) if (!nuevas.has(k)) cuerpo.put("lectura/$k", JSONObject.NULL)
        // ---- Las palabras de cada día de este dispositivo (mientras escribes cambian a cada frase: se mandan cuando llevas un rato quieto)
        val dias = palabrasPorDia(e)
        var firmaDias = firma(dias.toSortedMap().toString())
        if (firmaPalabrasPrevia != firmaDias) {
            val ultimoLog = e.todos().filter { it.t == Escritos.T_LOG }.maxOfOrNull { it.u } ?: 0L
            if (firmaPalabrasPrevia.isNotBlank() && ahora - ultimoLog < QUIETO) { firmaDias = firmaPalabrasPrevia; diferido = true }
            else cuerpo.put("palabras/$dispositivo", JSONObject().also { o -> dias.forEach { (k, v) -> o.put(k, v) } })
        }
        // ---- El sello: quien escucha `stamps` (Azabache, Marfil) se entera al momento de que hay algo nuevo
        if (cuerpo.length() > 0) cuerpo.put("stamps/$dispositivo", JSONObject().put(".sv", "timestamp"))
        return Envio(cuerpo, nuevas, firmaDias, diferido)
    }

    /** El estado de una obra en la cuenta. */
    fun estadoEnCuenta(o: Doc): String = when (estadoDe(o)) { EstadoObra.ESCRIBIENDO -> "en_curso"; EstadoObra.PAUSA -> "pausa"; EstadoObra.TERMINADA -> "terminado"; EstadoObra.ARCHIVO -> "archivada" }

    /** Publica lo que haya cambiado. Devuelve si quedó algo diferido (un capítulo aún en marcha: volver a llamar en un rato). */
    fun todo(c: Context): Boolean {
        if (!Cuenta.vinculada) return false
        val e = Escritos.get(c)
        val ahora = System.currentTimeMillis()
        var diferido = false
        val f = com.gonzalo.familia.LaFamilia
        // Las obras
        val obras = f.coleccion("obras")
        val vivas = e.obras().map { it.id }.toSet()
        for (o in e.obras()) {
            val palabras = e.palabrasObra(o.id)
            obras.cambia(o.id, mapOf("t" to o.s("titulo"), "a" to o.s("autor"), "estado" to estadoEnCuenta(o), "palabras" to palabras.toLong(), "ul" to e.ultimaEdicion(o.id), "b" to 0L))
        }
        obras.vivos().filter { it.ref !in vivas }.forEach { obras.borra(it.ref) }
        // Los capítulos terminados (uno que se sigue tocando espera a llevar un rato quieto)
        val caps = f.coleccion("capitulos")
        val quiero = HashSet<String>()
        capitulosTerminados(e).forEach { (pid, cs) -> cs.forEach { (cid, doc) ->
            val ref = "$pid~$cid"; quiero += ref
            val actual = caps.nodo(ref)
            if (actual != null && !actual.borrado && ahora - doc.optLong("u") < QUIETO && actual.texto("x") != doc.optString("x")) { diferido = true; return@forEach }
            caps.cambia(ref, mapOf("ref" to pid, "t" to doc.optString("t"), "a" to doc.optString("a"), "c" to doc.optString("c"), "n" to doc.optInt("n").toLong(),
                "pa" to doc.optString("pa"), "ue" to doc.optLong("u"), "p" to doc.optInt("p").toLong(), "x" to doc.optString("x"), "b" to 0L))
        } }
        caps.vivos().filter { it.ref !in quiero }.forEach { caps.borra(it.ref) }
        // Las palabras de cada día de este dispositivo (mientras escribes cambian a cada frase: se mandan cuando llevas un rato quieto)
        val ultimoLog = e.todos().filter { it.t == Escritos.T_LOG }.maxOfOrNull { it.u } ?: 0L
        if (ahora - ultimoLog < QUIETO && f.coleccion("palabras").nodo(Cuenta.dispositivo) != null) diferido = true
        else f.coleccion("palabras").cambia(Cuenta.dispositivo, palabrasPorDia(e).mapKeys { "d${it.key}" }.mapValues { it.value.toLong() })
        return diferido
    }

    /** Al salir de la cuenta: se olvida lo publicado (en la siguiente cuenta se publica todo). */
    fun olvidar(c: Context) = prefs(c).edit().clear().apply()
}
