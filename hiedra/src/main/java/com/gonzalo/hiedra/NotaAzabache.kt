package com.gonzalo.hiedra

import android.content.Context
import org.json.JSONObject

/**
 * La valoración que tiene un juego en Azabache (por ahora, la de Atrio: lo mismo con otro nombre), para verla en su ficha. Solo se LEE: en Hiedra
 * no se valora nada; eso se hace en Azabache. Se enlaza por el nombre del juego (la clave «juego:<título>», que es la que Azabache usará).
 */
object NotaAzabache {
    /** [general]: de 0 a 100. [atrioId]: para abrir esa ficha en Azabache. */
    /** [ref]: el juego en la cuenta (su id), para abrirlo en Azabache. */
    class Nota(val ref: String, val general: Int, val resena: String)

    /** Una ficha reflejada por Azabache a la que aún no has puesto nota (todas a 0 y sin reseña). */
    fun sinValorar(o: JSONObject) = o.optInt("general", 0) == 0 && o.optInt("gfx", 0) == 0 && o.optInt("story", 0) == 0 && o.optInt("music", 0) == 0 && o.optInt("gameplay", 0) == 0 && o.optString("review").isBlank()

    private fun cambio(o: JSONObject) = maxOf(o.optLong("updated", 0L), o.optLong("sv", 0L))

    /** Las valoraciones de los juegos valorados, por clave «juego:<título>», a partir de lo que subieron los dispositivos de Atrio. Sin red: se puede probar. */
    fun deBlobs(blobs: List<JSONObject>): Map<String, Nota> {
        val tombs = HashMap<String, Long>()
        blobs.forEach { b -> b.optJSONObject("tombs")?.let { t -> for (k in t.keys()) tombs[k] = maxOf(tombs[k] ?: 0L, t.optLong(k)) } }
        val porId = HashMap<Long, JSONObject>()
        for (b in blobs) b.optJSONArray("games")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.optLong("id"); if (id == 0L) continue
                val t = tombs["games:$id"]
                if (t != null && t >= cambio(o)) continue
                val cur = porId[id]; if (cur == null || cambio(o) > cambio(cur)) porId[id] = o
            }
        }
        val out = HashMap<String, Nota>()
        for ((id, o) in porId) {
            val nombre = o.optString("name").trim(); if (nombre.isBlank()) continue
            // Una ficha que Azabache refleja de Hiedra sin nota aún (todo a 0) no es una valoración
            if (sinValorar(o)) continue
            out["juego:" + Catalogo.norm(nombre)] = Nota(id.toString(), o.optInt("general", 0).coerceIn(0, 100), o.optString("review").trim())
        }
        return out
    }

    /**
     * Las valoraciones: Azabache las pone en el propio juego de la cuenta (`nota`, 0–100, y `resena`), así que se ven aquí al momento.
     * Un juego sin nota ni reseña no sale (es «sin valorar»). Por id de juego.
     */
    fun cargar(c: Context): Map<String, Nota> = Biblioteca.juegos.toList().mapNotNull { j ->
        val (g, r) = Biblioteca.valoracion(j.id)
        if (g > 0 || r.isNotBlank()) j.id to Nota(j.id, g.coerceIn(0, 100), r.trim()) else null
    }.toMap()

    /** La nota de un juego. */
    fun de(notas: Map<String, Nota>, j: Juego): Nota? = notas[j.id]

    /** Cambia cuando cambia algo de la cuenta (las fichas abiertas vuelven a leer). */
    val version: Int get() = com.gonzalo.familia.LaFamilia.cambios

    fun vieja(c: Context, ahora: Long = System.currentTimeMillis()) = false
    fun refrescar(c: Context): Boolean = true
    fun pronto(c: Context) {}

    fun instalado(c: Context): Boolean = runCatching { c.packageManager.getLaunchIntentForPackage(ATRIO) != null }.getOrDefault(false)

    /** Abre ese juego en Azabache (`ref` = «juegos/<id>»: la misma ficha, valorada o por valorar). */
    fun abrir(c: Context, n: Nota?) {
        val i = c.packageManager.getLaunchIntentForPackage(ATRIO) ?: return
        if (n != null && n.ref.isNotBlank()) i.putExtra("ref", "juegos/${n.ref}")
        c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
