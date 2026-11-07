package com.gonzalo.iptv

import java.text.Normalizer

/**
 * Qué canal de la guía es cada canal de tu lista. Por este orden:
 *  1. el `tvg-id` del canal (en Xtream, su `epg_channel_id`) igual que el id de un canal de la guía;
 *  2. lo mismo sin mirar mayúsculas;
 *  3. el `tvg-id` normalizado contra los ids normalizados de la guía (`La1.es` y `la1.es`, `ES: La 1`…);
 *  4. el nombre del canal (el `tvg-name` y, si no, el que se ve) normalizado contra los nombres de la guía (`display-name`).
 * «Normalizado»: sin acentos, en minúsculas, sin el prefijo del país (`ES:`, `|ES|`, `[ES]`), sin las etiquetas de calidad (HD, FHD, UHD,
 * 4K, SD, 1080p, HEVC…) ni lo que va entre paréntesis o corchetes, y solo letras y números (el `+1` de los canales con una hora de
 * retraso se queda: es otro canal).
 */
class Emparejador(canalesGuia: Collection<CanalGuia>) {
    private val porId = HashMap<String, String>()
    private val porIdMin = HashMap<String, String>()
    private val porIdNorm = HashMap<String, String>()
    private val porNombre = HashMap<String, String>()

    init {
        for (c in canalesGuia) {
            porId.putIfAbsent(c.id, c.id)
            porIdMin.putIfAbsent(c.id.lowercase(), c.id)
            normaliza(c.id.substringBeforeLast('.').ifBlank { c.id }).takeIf { it.isNotEmpty() }?.let { porIdNorm.putIfAbsent(it, c.id) }
            for (n in c.nombres) normaliza(n).takeIf { it.isNotEmpty() }?.let { porNombre.putIfAbsent(it, c.id) }
        }
    }

    /** El id del canal de la guía para [c], o null si no se encuentra. */
    fun busca(c: Canal): String? {
        val id = c.tvgId.trim()
        if (id.isNotEmpty()) {
            porId[id]?.let { return it }
            porIdMin[id.lowercase()]?.let { return it }
            normaliza(id.substringBeforeLast('.').ifBlank { id }).takeIf { it.isNotEmpty() }?.let { n -> porIdNorm[n]?.let { return it }; porNombre[n]?.let { return it } }
        }
        for (n in listOf(c.tvgNombre, c.nombre)) {
            val k = normaliza(n)
            if (k.isEmpty()) continue
            porNombre[k]?.let { return it }
            porIdNorm[k]?.let { return it }
        }
        return null
    }

    companion object {
        private val PAIS = Regex("""^\s*(?:[|\[(]\s*[a-z]{2,3}\s*[|\])]|[a-z]{2,3}\s*[:|]|[a-z]{2}\s+-)\s*""")
        private val ENTRE = Regex("""[(\[][^)\]]*[)\]]""")
        private val CALIDAD = Regex("""\b(?:uhd|fhd|hd|sd|hq|4k|8k|2160p|1080[pi]?|720p|576p|480p|hevc|h\.?26[45]|x26[45]|50fps|60fps|backup|alt|vip|multi|audio|ott)\b""")

        fun normaliza(s: String): String {
            if (s.isBlank()) return ""
            var t = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
            t = PAIS.replace(t, "")
            t = ENTRE.replace(t, " ")
            t = CALIDAD.replace(t, " ")
            t = t.replace("&", "and")
            val sb = StringBuilder()
            for (ch in t) if (ch.isLetterOrDigit() || ch == '+') sb.append(ch)
            return sb.toString()
        }
    }
}
