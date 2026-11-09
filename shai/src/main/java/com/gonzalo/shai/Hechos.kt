package com.gonzalo.shai

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto

/**
 * Los hechos históricos: algo que le pasa en el juego y que lo cambia todo (le convocan para un Mundial sin haber debutado, es titular
 * en octavos…). Lo cuentas tú con tus palabras, la app te enseña lo que ha entendido, comprueba en la web si ha pasado antes de verdad
 * (sin inventarse precedentes) y el mundo reacciona en una edición especial. A diferencia de lo de cada jornada, aquí la fama puede
 * saltar de golpe ([Ruido]).
 */
enum class Ruido(val nombre: String, val explica: String, val tope: Nivel, val sinTope: Boolean, val minimo: Int) {
    CLUB("Su club", "Se habla en su club y en su ciudad: la fama sube como siempre, poco a poco.", Nivel.TITULAR, false, 0),
    PAIS("Su país", "Abre los informativos de todo el país: la fama salta de golpe (como mucho, hasta estrella).", Nivel.ESTRELLA, true, 8),
    MUNDO("Todo el mundo", "Da la vuelta al planeta: la fama salta de golpe hasta donde lo lleve la historia.", Nivel.LEYENDA, true, 15);

    /** Cómo se le cuenta a Claude. */
    val paraClaude: String get() = when (this) {
        CLUB -> "SU CLUB Y SU CIUDAD: lo cubre la prensa local y la de su club; los nacionales, de pasada."
        PAIS -> "TODO SU PAÍS: abre los informativos, las tertulias y las portadas nacionales; la prensa internacional lo recoge."
        MUNDO -> "TODO EL MUNDO: portadas en varios países, la prensa internacional, los programas de televisión y la red de medio planeta."
    }

    companion object { fun de(t: String) = entries.firstOrNull { it.name == t } ?: MUNDO }
}

/** Un hecho histórico, tal como quedó: lo que contaste, lo que entendió la app, hasta dónde llegó, la fama de antes y de después, y lo comprobado. */
data class Hecho(
    val texto: String,
    val entendido: List<String>,
    val ruido: String,
    val famaAntes: Int = 0,
    val famaDespues: Int = 0,
    /** Lo de precocidad que se ha mirado en la web («Titular en un Mundial antes de debutar con su club»), o vacío. */
    val precocidad: String = "",
    /** Lo que dice la búsqueda (o por qué no se pudo comprobar). */
    val comprobado: String = "",
    /** true: hay precedentes reales; false: no se encontraron; null: no se comprobó. */
    val hayPrecedentes: Boolean? = null,
    val fuentes: List<Pair<String, String>> = emptyList(),
) {
    val nivelAntes: Nivel get() = Nivel.deFama(famaAntes)
    val nivelDespues: Nivel get() = Nivel.deFama(famaDespues)
    fun aMapa(): Map<String, Any?> = mapOf("tx" to texto, "en" to entendido, "ru" to ruido, "fa" to famaAntes.toLong(), "fd" to famaDespues.toLong(),
        "pc" to precocidad, "co" to comprobado, "hp" to hayPrecedentes, "fu" to fuentes.map { listOf(it.first, it.second) }).filterValues { it != null }
    companion object {
        fun de(m: Map<String, Any?>) = Hecho(m["tx"].comoTexto(), m["en"].comoLista().map { it.comoTexto() }.filter { it.isNotBlank() }, m["ru"].comoTexto(),
            m["fa"].comoLong().toInt(), m["fd"].comoLong().toInt(), m["pc"].comoTexto(), m["co"].comoTexto(), if (m.containsKey("hp")) m["hp"].comoBool() else null,
            m["fu"].comoLista().mapNotNull { x -> x.comoLista().takeIf { it.size == 2 }?.let { it[0].comoTexto() to it[1].comoTexto() } })
    }
}

object Hechos {
    /** Lo que la app ha entendido de lo que cuentas: los hechos uno a uno y, si lo hay, algo de precocidad que mirar en la web. */
    class Entendido(val hechos: List<Pair<String, String>>, val precocidad: String, val pregunta: String)

    /** Lo que ha dicho la búsqueda web. */
    class Comprobacion(val hay: Boolean?, val texto: String, val fuentes: List<Pair<String, String>>)

    const val MAX_BUSQUEDAS = 2L
    /** Lo que podría costar la comprobación, como mucho (para el tope): dos búsquedas y sus resultados. */
    const val COSTE_MAXIMO = 0.15

    val SISTEMA_COMPRUEBA = """
Compruebas en la web si algo que le ha pasado a un futbolista en una carrera de videojuego (EA Sports FC 26) ha pasado alguna vez en el fútbol de verdad. Te dan lo que hay que comprobar. Busca (una o dos búsquedas bastan) y cuenta lo que encuentres.
Reglas:
- Solo lo que encuentres en las fuentes: nombres, años, selecciones y clubes tal cual. No inventes nada ni completes de memoria lo que no salga.
- Si encuentras casos parecidos pero no iguales, dilo así («parecido: …») y explica en qué se diferencian.
- Si no encuentras ningún caso, dilo: «No he encontrado ningún caso igual», sin afirmar que sea la primera vez en la historia (que no lo encuentres no lo demuestra).
- Escribe en español de España.
Al terminar, responde SOLO con un objeto JSON (sin texto antes ni después, sin ```): {"hay_precedentes": true/false, "texto": "de 2 a 4 frases con lo que has encontrado (quién, cuándo, dónde) o que no has encontrado ningún caso igual", "dudas": ""}
""".trim()

    fun peticionComprueba(pregunta: String): Map<String, Any?> = linkedMapOf(
        "model" to Claude.SONNET,
        "max_tokens" to 1500L,
        "system" to listOf(mapOf("type" to "text", "text" to SISTEMA_COMPRUEBA)),
        "messages" to listOf(mapOf("role" to "user", "content" to listOf(Peticion.texto("Comprueba esto: ${pregunta.trim()}")))),
        "tools" to listOf(linkedMapOf("type" to "web_search_20260209", "name" to "web_search", "max_uses" to MAX_BUSQUEDAS,
            "user_location" to mapOf("type" to "approximate", "country" to "ES", "timezone" to "Europe/Madrid"))),
        "output_config" to mapOf("effort" to "low"),
    )

    /**
     * Mira en la web si lo de precocidad ha pasado de verdad (Sonnet y hasta dos búsquedas web: unos céntimos). Bloquea: fuera del hilo
     * principal. En modo prueba no busca nada.
     */
    fun comprueba(pregunta: String): Comprobacion {
        if (Prueba.activo) return Comprobacion(null, "Modo prueba: no se ha buscado nada en la web. Con tu clave y sin el modo prueba, aquí sale si ha pasado antes de verdad (y quién).", emptyList())
        Gasto.compruebaAntes(COSTE_MAXIMO)
        val cuerpo = peticionComprueba(pregunta)
        val respuestas = ArrayList<Map<String, Any?>>()
        var r = Motor.claude.crudo(cuerpo)
        respuestas += r
        var vueltas = 0
        while (r["stop_reason"].comoTexto() == "pause_turn" && vueltas < 2) {
            vueltas++
            r = Motor.claude.crudo(cuerpo + ("messages" to (cuerpo["messages"].comoLista() + mapOf("role" to "assistant", "content" to respuestas.flatMap { it["content"].comoLista() }))))
            respuestas += r
        }
        val v = Clubes.lee(respuestas)
        val modelo = v.modelo.ifBlank { Claude.SONNET }
        Gasto.apunta(modelo, Precios.coste(modelo, v.uso) + v.busquedas * Clubes.PRECIO_BUSQUEDA)
        if (r["stop_reason"].comoTexto() == "refusal") throw Claude.Rechazo(Uso(), v.modelo)
        val i = v.texto.indexOf('{'); val j = v.texto.lastIndexOf('}')
        val m = if (i >= 0 && j > i) runCatching { Json.lee(v.texto.substring(i, j + 1)).comoMapa() }.getOrNull() else null
        val texto = m?.get("texto").comoTexto().trim()
        if (m == null || texto.isBlank()) throw Claude.Fallo(if (v.errores.isNotEmpty()) "La búsqueda web no ha funcionado ahora mismo." else "La búsqueda no devolvió nada claro.", true)
        return Comprobacion(m["hay_precedentes"].comoBool(), texto.take(900), v.fuentes.take(5))
    }

    /** Las líneas de los hechos históricos para el contexto de Claude (del más viejo al más nuevo). */
    fun contexto(entradas: List<Entrada>): String? {
        val hs = entradas.filter { it.tipo == TipoEntrada.HISTORICO && it.hecho != null }.sortedBy { it.creado }
        if (hs.isEmpty()) return null
        return "HECHOS HISTÓRICOS DE SU CARRERA (los ha contado él: pasaron de verdad en su carrera, aunque sean del campo; son lo más gordo que le ha pasado y se recuerdan siempre):\n" +
            hs.joinToString("\n") { e -> val h = e.hecho!!
                "- Jornada ${e.paso}: ${h.entendido.ifEmpty { listOf(h.texto) }.joinToString("; ")}" +
                    (if (h.famaDespues > h.famaAntes) " (le llevó de ${h.nivelAntes.nombre.lowercase()} a ${h.nivelDespues.nombre.lowercase()})" else "") +
                    (if (h.hayPrecedentes != null && h.comprobado.isNotBlank()) ". Comprobado en la web: ${h.comprobado}" else "")
            }
    }
}
