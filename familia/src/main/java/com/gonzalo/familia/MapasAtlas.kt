package com.gonzalo.familia

import android.content.Context
import com.gonzalo.familia.nucleo.Nodo
import com.gonzalo.familia.nucleo.comoDouble
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import org.json.JSONArray
import java.io.File
import java.text.Normalizer

/** Un lugar de un mapa de ATLAS: su nombre, su tipo («capital», «aldea»…) y dónde está sobre la lámina (0..1; 0,0 arriba a la izquierda). */
data class LugarMapa(val nombre: String, val tipo: String, val x: Float, val y: Float)

/** La lámina de un mapa: el mapa 2D entero con sus rótulos, una WebP en el Drive de la familia (carpeta `atlas`). */
data class LaminaAtlas(val gd: String, val nombre: String, val tamano: Long, val w: Int, val h: Int)

/** Un mapa de ATLAS tal como está en la cuenta (`familia/atlas/<mapa>`). Solo lo escribe ATLAS: aquí solo se lee. */
data class MapaAtlas(
    val id: String,
    val nombre: String,
    /** De qué es (campo `k`): "mundo", "continente", "region", "ciudad", "lugar", "otro" o "" (los subidos antes de ATLAS 2.4.0). */
    val tipo: String,
    val obras: List<String>,
    val w: Int,
    val h: Int,
    val portada: String,
    val lamina: LaminaAtlas?,
    /** Cuándo se puso la lámina (`m/lam`): cambia cada vez que ATLAS la vuelve a subir, aunque su id de Drive sea el mismo. */
    val laminaHora: Long,
    val lugares: List<LugarMapa>,
    val ul: Long,
) {
    /** Lo que identifica esta versión de la lámina: si cambia, hay que volver a bajarla. */
    val versionLamina: String get() = lamina?.let { "${it.gd}@${if (laminaHora > 0) laminaHora else ul}" } ?: ""
}

/**
 * Los mapas de ATLAS en las apps de la familia (solo lectura: el visor de los escritos). Contrato en `nube/ESQUEMA.md`, sección
 * `atlas/<mapa>`.
 */
object MapasAtlas {
    const val COLECCION = "atlas"

    /** Un mapa a partir de su nodo; null si está borrado o no es un mapa. */
    fun deNodo(n: Nodo): MapaAtlas? {
        if (n.borrado) return null
        val nombre = n.texto("t")
        val obra = n.texto("obra")
        val obras = n.texto("obras").split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOfNotNull(obra.takeIf { it.isNotBlank() }) }
        val lam = n.mapa("lam").takeIf { it.isNotEmpty() }?.let {
            val gd = it["gd"].comoTexto()
            if (gd.isBlank()) null else LaminaAtlas(gd, it["n"].comoTexto(), it["s"].comoLong(), it["w"].comoLong().toInt(), it["h"].comoLong().toInt())
        }
        return MapaAtlas(
            id = n.ref,
            nombre = nombre.ifBlank { "Mapa sin nombre" },
            tipo = n.texto("k"),
            obras = obras,
            w = n.entero("w").toInt(),
            h = n.entero("h").toInt(),
            portada = n.texto("portada"),
            lamina = lam,
            laminaHora = n.m["lam"] ?: 0L,
            lugares = lugares(n.campos["lug"]),
            ul = n.entero("ul"),
        )
    }

    /** Los lugares del campo `lug` (texto JSON `[{"n","k","x","y"}]`); los que no se entienden se saltan. */
    fun lugares(lug: Any?): List<LugarMapa> {
        val t = lug.comoTexto().trim()
        if (t.isEmpty()) return emptyList()
        val a = runCatching { JSONArray(t) }.getOrNull() ?: return emptyList()
        val r = ArrayList<LugarMapa>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val n = o.optString("n").trim()
            if (n.isEmpty() || !o.has("x") || !o.has("y")) continue
            val x =o.opt("x").comoDouble().toFloat(); val y = o.opt("y").comoDouble().toFloat()
            if (x.isNaN() || y.isNaN()) continue
            r += LugarMapa(n, o.optString("k"), x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }
        return r
    }

    /** Los mapas de la obra [obra] (los que la tienen en `obra` o en `obras`), sin los borrados, por nombre. */
    fun deLaObra(nodos: Collection<Nodo>, obra: String): List<MapaAtlas> {
        if (obra.isBlank()) return emptyList()
        return nodos.mapNotNull { deNodo(it) }.filter { obra in it.obras }.sortedWith(compareBy({ it.nombre.lowercase() }, { it.id }))
    }

    /** Los mapas de la obra en la cuenta (se lee lo que ya hay en el dispositivo; la colección se sincroniza sola). */
    fun deLaObra(obra: String): List<MapaAtlas> =
        runCatching { deLaObra(LaFamilia.coleccion(COLECCION).todos().values, obra) }.getOrDefault(emptyList())

    /** Todos los mapas de la cuenta (para buscar lugares en los escritos). */
    fun todos(): List<MapaAtlas> =
        runCatching { LaFamilia.coleccion(COLECCION).todos().values.mapNotNull { deNodo(it) } }.getOrDefault(emptyList())

    // ---------------------------------------------------------------- la lámina en el dispositivo

    private fun dir(ctx: Context) = File(ctx.filesDir, "atlas").apply { mkdirs() }
    private fun archivo(ctx: Context, m: MapaAtlas) = File(dir(ctx), "${seguro(m.id)}.webp")
    private fun marca(ctx: Context, m: MapaAtlas) = File(dir(ctx), "${seguro(m.id)}.version")
    private fun seguro(s: String) = s.replace(Regex("[^A-Za-z0-9_-]"), "_")

    /** La lámina guardada en el dispositivo, aunque sea de una versión anterior (para verla sin conexión); null si no hay. */
    fun laminaGuardada(ctx: Context, m: MapaAtlas): File? = archivo(ctx, m).takeIf { it.isFile && it.length() > 0 }

    /** ¿La lámina guardada es la última? */
    fun laminaAlDia(ctx: Context, m: MapaAtlas): Boolean =
        m.lamina != null && laminaGuardada(ctx, m) != null && runCatching { marca(ctx, m).readText() }.getOrNull() == m.versionLamina

    /**
     * La lámina de [m]: la guardada si está al día; si no, la baja de Google Drive (bloquea: llamar fuera del hilo principal). Si no
     * se puede bajar (sin red, sin Drive), devuelve la guardada aunque sea vieja, o null.
     */
    fun lamina(ctx: Context, m: MapaAtlas): File? {
        val lam = m.lamina ?: return null
        if (laminaAlDia(ctx, m)) return laminaGuardada(ctx, m)
        val dest = archivo(ctx, m)
        val tmp = File(dest.path + ".tmp")
        return runCatching {
            NubeFamilia.gdrive(ctx).baja(lam.gd, tmp)
            if (tmp.length() <= 0) error("vacía")
            if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
            marca(ctx, m).writeText(m.versionLamina)
            dest
        }.getOrElse { tmp.delete(); laminaGuardada(ctx, m) }
    }

    /** La miniatura (256 px, de `familia_img`): del dispositivo o de la cuenta. Bloquea. */
    fun portada(m: MapaAtlas): ByteArray? = if (m.portada.isBlank()) null else runCatching { LaFamilia.familia.imagenes.baja(m.portada) }.getOrNull()

    // ---------------------------------------------------------------- lugares en un texto (del manuscrito al mapa)

    /** Un lugar de un mapa que sale en un texto, entre [inicio] y [fin] (sin incluir). */
    data class Mencion(val inicio: Int, val fin: Int, val lugar: LugarMapa, val mapa: MapaAtlas)

    /** Para comparar sin mayúsculas ni tildes («Aldréth» = «aldreth»), conservando la longitud de cada letra. */
    internal fun plano(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            val d = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
            sb.append(d.lowercaseChar())
        }
        return sb.toString()
    }

    /**
     * Los lugares de [mapas] que salen en [texto], como palabras enteras, sin distinguir mayúsculas ni tildes. Si dos se solapan gana
     * el más largo («Aldea del Vado» antes que «Vado»). Los nombres de menos de 3 letras no se buscan (darían falsos aciertos).
     */
    fun menciones(texto: String, mapas: List<MapaAtlas>): List<Mencion> {
        if (texto.isEmpty() || mapas.isEmpty()) return emptyList()
        // Un nombre puede estar en varios mapas: se queda el primero (los mapas vienen por nombre)
        val porNombre = LinkedHashMap<String, Pair<LugarMapa, MapaAtlas>>()
        for (m in mapas) for (l in m.lugares) {
            val k = plano(l.nombre.trim())
            if (k.length >= 3 && k !in porNombre) porNombre[k] = l to m
        }
        if (porNombre.isEmpty()) return emptyList()
        val t = plano(texto)
        val encontradas = ArrayList<Mencion>()
        for ((k, lm) in porNombre) {
            var desde = 0
            while (true) {
                val i = t.indexOf(k, desde)
                if (i < 0) break
                val f = i + k.length
                val antesOk = i == 0 || !t[i - 1].isLetterOrDigit()
                val despuesOk = f >= t.length || !t[f].isLetterOrDigit()
                if (antesOk && despuesOk) encontradas += Mencion(i, f, lm.first, lm.second)
                desde = i + 1
            }
        }
        // Las más largas primero; se descartan las que pisan a otra ya elegida
        encontradas.sortWith(compareByDescending<Mencion> { it.fin - it.inicio }.thenBy { it.inicio })
        val elegidas = ArrayList<Mencion>()
        for (m in encontradas) if (elegidas.none { m.inicio < it.fin && it.inicio < m.fin }) elegidas += m
        return elegidas.sortedBy { it.inicio }
    }
}
