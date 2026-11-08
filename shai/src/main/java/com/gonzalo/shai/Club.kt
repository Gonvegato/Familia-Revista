package com.gonzalo.shai

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto

/**
 * Lo que se sabe de un club de verdad, sacado con una búsqueda web (la herramienta de servidor `web_search` de la API): para que Claude
 * escriba con su estadio, su ciudad, su gente y su momento de verdad en vez de inventárselos. Lo confirmas tú («Sí, es este»).
 */
data class InfoClub(
    val nombre: String,
    val apodo: String = "",
    val ciudad: String = "",
    val pais: String = "",
    val estadio: String = "",
    val aforo: Long = 0,
    val liga: String = "",
    val division: String = "",
    val entrenador: String = "",
    val presidente: String = "",
    val colores: String = "",
    val rivales: List<String> = emptyList(),
    val palmares: String = "",
    val contexto: String = "",
    val aficion: String = "",
    /** Medios locales de verdad que lo cubren: solo para que Claude invente medios con ese estilo, nunca para atribuirles nada. */
    val medios: List<String> = emptyList(),
    /** Lo que no ha quedado claro (dos clubes con nombre parecido, datos que no cuadran…). */
    val dudas: String = "",
    /** Las páginas de la búsqueda: (título, dirección). */
    val fuentes: List<Pair<String, String>> = emptyList(),
    /** Cuándo se buscó (aaaa-mm-dd). */
    val buscado: String = "",
    /** Lo que escribiste tú (el club de tu partida) y lo que precisaste al buscar otra vez. */
    val consulta: String = "",
    /** Lo has confirmado («Sí, es este»). */
    val confirmado: Boolean = false,
    /** Modo prueba: datos de ejemplo, no se ha buscado nada. */
    val deMentira: Boolean = false,
    /** Lo que costó la búsqueda (dólares) y cuántas búsquedas hizo. */
    val coste: Double = 0.0,
    val busquedas: Int = 0,
) {
    /** «Real Betis Balompié · Sevilla · Benito Villamarín (60.721) · LaLiga · Entrenador: …» */
    val resumen: String get() = listOfNotNull(nombre.ifBlank { null }, ciudad.ifBlank { null },
        estadio.ifBlank { null }?.let { e -> if (aforo > 0) "$e (${Formato.cifra(aforo)})" else e },
        listOf(liga, division).filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { null }, entrenador.ifBlank { null }?.let { "Entrenador: $it" }).joinToString(" · ")

    /** ¿Es el club de [club]? (el que tienes ahora en la carrera) */
    fun es(club: String): Boolean = Vida.mismo(club, consulta.substringBefore("\n")) || Vida.mismo(club, nombre) || (apodo.isNotBlank() && Vida.mismo(club, apodo))

    fun aMapa(): Map<String, Any?> = mapOf(
        "no" to nombre, "ap" to apodo, "ci" to ciudad, "pa" to pais, "es" to estadio, "af" to aforo, "li" to liga, "di" to division, "en" to entrenador, "pr" to presidente,
        "co" to colores, "ri" to rivales, "pl" to palmares, "cx" to contexto, "fi" to aficion, "me" to medios, "du" to dudas,
        "fu" to fuentes.map { listOf(it.first, it.second) }, "bu" to buscado, "cs" to consulta, "ok" to confirmado, "dm" to deMentira, "ct" to coste, "nb" to busquedas.toLong(),
    )

    companion object {
        fun de(m: Map<String, Any?>): InfoClub? = if (m.isEmpty() || m["no"].comoTexto().isBlank()) null else InfoClub(
            m["no"].comoTexto(), m["ap"].comoTexto(), m["ci"].comoTexto(), m["pa"].comoTexto(), m["es"].comoTexto(), m["af"].comoLong(), m["li"].comoTexto(), m["di"].comoTexto(),
            m["en"].comoTexto(), m["pr"].comoTexto(), m["co"].comoTexto(), m["ri"].comoLista().map { it.comoTexto() }.filter { it.isNotBlank() }, m["pl"].comoTexto(),
            m["cx"].comoTexto(), m["fi"].comoTexto(), m["me"].comoLista().map { it.comoTexto() }.filter { it.isNotBlank() }, m["du"].comoTexto(),
            m["fu"].comoLista().mapNotNull { x -> x.comoLista().takeIf { it.size == 2 }?.let { it[0].comoTexto() to it[1].comoTexto() } }.filter { it.second.isNotBlank() },
            m["bu"].comoTexto(), m["cs"].comoTexto(), m["ok"].comoBool(), m["dm"].comoBool(), (m["ct"] as? Number)?.toDouble() ?: 0.0, m["nb"].comoLong().toInt(),
        )
    }
}

/**
 * Identificar el club con la búsqueda web de la API (documentación: platform.claude.com/docs/en/agents-and-tools/tool-use/web-search-tool):
 *  - Sonnet 5.5 con `{"type": "web_search_20260209", "name": "web_search", "max_uses": 3}` (con el filtrado dinámico: Claude filtra los
 *    resultados antes de leerlos). Cada búsqueda cuesta 10 $ los 1.000 (1 céntimo), además de los tokens.
 *  - La búsqueda web viene encendida en la organización, salvo que el administrador la apague en Claude Console (Settings → Capabilities,
 *    platform.claude.com/settings/capabilities): entonces la API responde 400 «web search is not enabled» ([Desactivada]).
 *  - Con la búsqueda web las citas van siempre, y la salida estructurada no admite citas: el JSON se pide en el texto y se valida; si no se
 *    puede leer, Haiku lo pasa a JSON (con su esquema).
 *  - Si la API para la vuelta (`pause_turn`), se le devuelve lo que llevaba para que siga (como mucho dos veces).
 */
object Clubes {
    /** Lo que cuesta cada búsqueda (dólares): 10 $ los 1.000. */
    const val PRECIO_BUSQUEDA = 0.01
    const val MAX_BUSQUEDAS = 3L
    /** Lo que podría costar, como mucho (para el tope): tres búsquedas y sus resultados. */
    const val COSTE_MAXIMO = 0.20

    class Desactivada : Claude.Fallo("La búsqueda web está apagada en tu organización de Claude Console. Actívala en platform.claude.com → Settings → Capabilities (Web search) " +
        "o pídeselo a quien la administre, y vuelve a probar. La carrera se puede empezar igual, sin identificar el club.")

    val SISTEMA = """
Identificas clubes de fútbol de verdad para una app que acompaña carreras de EA Sports FC 26. Te dan el nombre del club tal como lo escribe alguien (puede venir abreviado, con faltas o con el nombre del juego) y, si la hay, la liga. Busca en la web (pocas búsquedas: una o dos suelen bastar) y averigua qué club es y lo esencial de él HOY.
Reglas:
- Solo datos que encuentres o sepas con seguridad; lo que no esté claro, vacío (y dilo en «dudas»). No inventes nada.
- Si hay varios clubes posibles (por ejemplo, un filial o dos clubes con nombre parecido), elige el que mejor encaje con la liga dada y explica la duda en «dudas».
- Si no existe ningún club de fútbol que encaje, «encontrado»: false.
- Personas: solo el cargo y el nombre (entrenador, presidente), sin opiniones ni polémicas.
- Escribe en español de España.
Al terminar, responde SOLO con un objeto JSON (sin texto antes ni después, sin ```), con estas claves:
{"encontrado": true/false, "nombre": nombre oficial, "apodo": cómo se le llama, "ciudad": "", "pais": "", "estadio": "", "aforo": número entero (0 si no se sabe), "liga": competición de liga en la que juega esta temporada, "division": "Primera", "Segunda"…, "entrenador": "", "presidente": "", "colores": "", "rivales": [derbis y rivales clásicos], "palmares": resumen de una o dos frases, "contexto": su momento deportivo y económico ahora mismo (dos o tres frases), "aficion": cómo es su afición (una o dos frases), "medios": [medios locales que lo cubren], "dudas": ""}
""".trim()

    fun peticion(club: String, liga: String, precisa: String = ""): Map<String, Any?> = linkedMapOf(
        "model" to Claude.SONNET,
        "max_tokens" to 3000L,
        "system" to listOf(mapOf("type" to "text", "text" to SISTEMA)),
        "messages" to listOf(mapOf("role" to "user", "content" to listOf(Peticion.texto(buildString {
            append("El club: «${club.trim()}»")
            if (liga.isNotBlank()) append(", en ${liga.trim()}")
            append(".")
            if (precisa.isNotBlank()) append(" Ojo, no es el que encontraste antes: ${precisa.trim()}")
        })))),
        "tools" to listOf(linkedMapOf("type" to "web_search_20260209", "name" to "web_search", "max_uses" to MAX_BUSQUEDAS,
            "user_location" to mapOf("type" to "approximate", "country" to "ES", "timezone" to "Europe/Madrid"))),
        "output_config" to mapOf("effort" to "low"),
    )

    /** Lo que vuelve de la API (una o varias vueltas, si se paró): el texto, las fuentes, lo gastado y los errores de búsqueda. */
    class Vuelta(val texto: String, val fuentes: List<Pair<String, String>>, val uso: Uso, val busquedas: Int, val errores: List<String>, val modelo: String)

    /** Lee las respuestas (bloques `text`, `server_tool_use`, `web_search_tool_result` y los del filtrado dinámico). */
    fun lee(respuestas: List<Map<String, Any?>>): Vuelta {
        val fuentes = LinkedHashMap<String, String>()
        val errores = ArrayList<String>()
        var uso = Uso(); var busquedas = 0; var modelo = ""
        val texto = StringBuilder()
        respuestas.forEach { m ->
            modelo = m["model"].comoTexto().ifBlank { modelo }
            val u = m["usage"].comoMapa()
            uso += Uso.de(u)
            busquedas += u["server_tool_use"].comoMapa()["web_search_requests"].comoLong().toInt()
            m["content"].comoLista().map { it.comoMapa() }.forEach { b ->
                when (b["type"].comoTexto()) {
                    "text" -> {
                        texto.append(b["text"].comoTexto())
                        b["citations"].comoLista().map { it.comoMapa() }.forEach { c -> c["url"].comoTexto().takeIf { it.isNotBlank() }?.let { fuentes.putIfAbsent(it, c["title"].comoTexto()) } }
                    }
                    "web_search_tool_result" -> {
                        val c = b["content"]
                        if (c is Map<*, *>) errores += c["error_code"].comoTexto().ifBlank { "unavailable" }
                        else c.comoLista().map { it.comoMapa() }.filter { it["type"] == "web_search_result" }.forEach { r ->
                            r["url"].comoTexto().takeIf { it.isNotBlank() }?.let { fuentes.putIfAbsent(it, r["title"].comoTexto()) }
                        }
                    }
                }
            }
        }
        return Vuelta(texto.toString(), fuentes.entries.map { it.value.ifBlank { it.key } to it.key }.take(8), uso, busquedas, errores, modelo)
    }

    /** El JSON del final del texto (del primer «{» al último «}»), o null. */
    fun jsonDe(texto: String): Map<String, Any?>? {
        val i = texto.indexOf('{'); val j = texto.lastIndexOf('}')
        if (i < 0 || j <= i) return null
        return runCatching { Json.lee(texto.substring(i, j + 1)).comoMapa() }.getOrNull()?.takeIf { it.containsKey("nombre") || it.containsKey("encontrado") }
    }

    /** El esquema (para que Haiku pase a JSON lo que no se pudo leer). */
    val ESQUEMA: Map<String, Any?> = run {
        val t = mapOf<String, Any?>("type" to "string")
        val l = mapOf<String, Any?>("type" to "array", "items" to t)
        val campos = linkedMapOf<String, Map<String, Any?>>("encontrado" to mapOf("type" to "boolean"), "nombre" to t, "apodo" to t, "ciudad" to t, "pais" to t, "estadio" to t,
            "aforo" to mapOf("type" to "integer"), "liga" to t, "division" to t, "entrenador" to t, "presidente" to t, "colores" to t, "rivales" to l, "palmares" to t,
            "contexto" to t, "aficion" to t, "medios" to l, "dudas" to t)
        mapOf("type" to "object", "properties" to campos, "required" to campos.keys.toList(), "additionalProperties" to false)
    }

    class NoEncontrado(msg: String) : Exception(msg)

    /** De lo leído, el club (o por qué no). */
    fun info(m: Map<String, Any?>, v: Vuelta, consulta: String, hoy: String, coste: Double): InfoClub {
        if (m.containsKey("encontrado") && !m["encontrado"].comoBool() || m["nombre"].comoTexto().isBlank())
            throw NoEncontrado("No he encontrado ningún club que sea «${consulta.substringBefore("\n")}»" + (m["dudas"].comoTexto().takeIf { it.isNotBlank() }?.let { ": $it" } ?: ".") +
                " Prueba a escribirlo de otra manera o precisa (ciudad, país, liga).")
        fun s(k: String) = m[k].comoTexto().trim()
        return InfoClub(s("nombre"), s("apodo"), s("ciudad"), s("pais"), s("estadio"), m["aforo"].comoLong().coerceIn(0, 300_000), s("liga"), s("division"), s("entrenador"), s("presidente"),
            s("colores"), m["rivales"].comoLista().map { it.comoTexto().trim() }.filter { it.isNotBlank() }.take(6), s("palmares"), s("contexto"), s("aficion"),
            m["medios"].comoLista().map { it.comoTexto().trim() }.filter { it.isNotBlank() }.take(6), s("dudas"), v.fuentes, hoy, consulta, coste = coste, busquedas = v.busquedas)
    }

    /**
     * Busca el club en la web (bloquea: fuera del hilo principal). [precisa]: lo que dices al pedir otro («el de Sevilla, no el filial»).
     * En modo prueba no busca nada: datos de ejemplo, bien marcados.
     */
    fun identifica(club: String, liga: String, precisa: String = ""): InfoClub {
        val consulta = club.trim() + (if (precisa.isNotBlank()) "\n" + precisa.trim() else "")
        val hoy = java.time.LocalDate.now().toString()
        if (Prueba.activo) return deMentira(club.trim(), liga.trim(), consulta, hoy)
        Gasto.compruebaAntes(COSTE_MAXIMO)
        val cuerpo = peticion(club, liga, precisa)
        val respuestas = ArrayList<Map<String, Any?>>()
        var r = Motor.claude.crudo(cuerpo)
        respuestas += r
        // Si la API ha parado la vuelta, se le devuelve lo que llevaba para que siga
        var vueltas = 0
        while (r["stop_reason"].comoTexto() == "pause_turn" && vueltas < 2) {
            vueltas++
            val hecho = respuestas.flatMap { it["content"].comoLista() }
            r = Motor.claude.crudo(cuerpo + ("messages" to (cuerpo["messages"].comoLista() + mapOf("role" to "assistant", "content" to hecho))))
            respuestas += r
        }
        val v = lee(respuestas)
        var coste = Precios.coste(v.modelo.ifBlank { Claude.SONNET }, v.uso) + v.busquedas * PRECIO_BUSQUEDA
        Gasto.apunta(v.modelo.ifBlank { Claude.SONNET }, coste)
        if (r["stop_reason"].comoTexto() == "refusal") throw Claude.Rechazo(Uso(), v.modelo)
        val m = jsonDe(v.texto) ?: run {
            if (v.texto.isBlank()) throw Claude.Fallo(when {
                v.errores.any { it == "too_many_requests" } -> "La búsqueda web va saturada ahora mismo: prueba en un rato."
                v.errores.isNotEmpty() -> "La búsqueda web no ha funcionado (${v.errores.distinct().joinToString()}): prueba en un rato."
                else -> "La búsqueda no devolvió nada: prueba otra vez."
            }, true)
            // No vino en JSON: Haiku lo pasa (barato)
            val p = Peticion(Claude.HAIKU, listOf(Peticion.Bloque("Pasa a JSON lo que dice este texto sobre un club de fútbol, sin añadir nada que no diga.")),
                listOf(Peticion.texto(v.texto.take(12_000))), 1500, ESQUEMA)
            val antes = Gasto.mes()
            val j = Motor.pide(p)
            coste += (Gasto.mes() - antes).coerceAtLeast(0.0)
            j
        }
        return info(m, v, consulta, hoy, coste)
    }

    /** Modo prueba: un club de ejemplo con lo que escribiste (nada sale a la red). */
    fun deMentira(club: String, liga: String, consulta: String, hoy: String) = InfoClub(
        club.ifBlank { "Tu club" }, "Los de la prueba", "Ciudad de ejemplo", "España", "Estadio de ejemplo", 42_000, liga.ifBlank { "LaLiga" }, "Primera",
        "Entrenador de ejemplo", "Presidente de ejemplo", "Los colores de ejemplo", listOf("El rival de ejemplo"), "Modo prueba: un palmarés de ejemplo.",
        "Modo prueba: datos de ejemplo, no se ha buscado nada. Con tu clave y sin el modo prueba, aquí sale lo de verdad del club.", "Una afición de ejemplo.",
        emptyList(), "Modo prueba: datos de ejemplo, no se ha buscado nada.", emptyList(), hoy, consulta, deMentira = true)

    /** Lo que va en el contexto de Claude (si está confirmado y es el club de ahora). */
    fun contexto(u: Universo): String? {
        val c = u.clubInfo?.takeIf { it.confirmado && !it.deMentira && it.es(u.club) } ?: return null
        return buildString {
            appendLine("LO QUE SE SABE DEL CLUB (búsqueda web del ${Fechas.bonita(c.buscado)}; son datos reales: úsalos tal cual cuando vengan a cuento, no inventes otros datos reales del club, y a su entrenador y su presidente, solo de forma neutra, como a cualquier persona real):")
            appendLine("- ${c.nombre}" + (if (c.apodo.isNotBlank()) " («${c.apodo}»)" else "") + listOf(c.ciudad, c.pais).filter { it.isNotBlank() }.joinToString(", ").let { if (it.isNotBlank()) ", $it" else "" })
            if (c.estadio.isNotBlank()) appendLine("- Estadio: ${c.estadio}" + (if (c.aforo > 0) " (${Formato.cifra(c.aforo)} espectadores)" else ""))
            listOf(c.liga, c.division).filter { it.isNotBlank() }.distinct().takeIf { it.isNotEmpty() }?.let { appendLine("- Liga: ${it.joinToString(", ")}") }
            if (c.entrenador.isNotBlank()) appendLine("- Entrenador: ${c.entrenador}")
            if (c.presidente.isNotBlank()) appendLine("- Presidente: ${c.presidente}")
            if (c.colores.isNotBlank()) appendLine("- Colores: ${c.colores}")
            if (c.rivales.isNotEmpty()) appendLine("- Rivales y derbis: ${c.rivales.joinToString(", ")}")
            if (c.palmares.isNotBlank()) appendLine("- Palmarés: ${c.palmares}")
            if (c.contexto.isNotBlank()) appendLine("- Momento actual: ${c.contexto}")
            if (c.aficion.isNotBlank()) appendLine("- Su afición: ${c.aficion}")
            if (c.medios.isNotEmpty()) appendLine("- Medios locales de verdad (SOLO como estilo para inventar los tuyos; no los nombres ni les atribuyas nada): ${c.medios.joinToString(", ")}")
        }.trim()
    }
}
