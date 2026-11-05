package com.gonzalo.cobalto

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Lo que Cobalto publica para Azabache (y quien venga), bajo `/users/<uid>/cobalto/` (ver docs/CONTRATO-DATOS.md):
 *  - `lectura/<obra>/<capítulo>`: una copia de SOLO LECTURA del texto de cada capítulo marcado como terminado («hecho» en el
 *    capítulo o en su parte). Al quitar la marca, se quita la copia. Es lo que lee Azabache: nunca los documentos de Escribir.
 *  - `palabras/<dispositivo>`: las palabras escritas cada día en este dispositivo (`"AAAA-MM-DD"` → palabras), ligero, para las
 *    barras y el Diario. Cada dispositivo escribe solo lo suyo.
 * Se publica tras cada sincronización de Escribir (con la app abierta o desde la tarea de fondo); solo lo que ha cambiado.
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

    /** Publica lo que haya cambiado. Devuelve si quedó algo diferido (un capítulo aún en marcha: volver a llamar en un rato). Bloqueante (red). */
    fun todo(c: Context): Boolean {
        if (!Cuenta.vinculada) return false
        val e = Escritos.get(c)
        val p = prefs(c)
        val firmas = runCatching { JSONObject(p.getString("lectura", "{}")!!) }.getOrDefault(JSONObject())
        val env = envio(e, firmas, p.getString("palabras", "") ?: "", Cuenta.dispositivo)
        if (env.cuerpo.length() > 0) Cuenta.db("PATCH", Cuenta.raiz, env.cuerpo.toString(), "print=silent")
        p.edit().putString("lectura", env.firmasLectura.toString()).putString("palabras", env.firmaPalabras).apply()
        return env.diferido
    }

    /** Al salir de la cuenta: se olvida lo publicado (en la siguiente cuenta se publica todo). */
    fun olvidar(c: Context) = prefs(c).edit().clear().apply()
}
