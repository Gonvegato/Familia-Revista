package com.gonzalo.shai

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto

/**
 * La API de Claude de mentira: no sale nada a la red ni se gasta nada. Contesta como la de verdad (`/v1/messages` con salida
 * estructurada, los lotes y sus resultados en JSONL), según el esquema que se le pide, y apunta todo lo que le llega.
 */
class ApiDeMentira : Transporte {
    class Llamada(val metodo: String, val url: String, val cabeceras: Map<String, String>, val cuerpo: Map<String, Any?>)
    val llamadas = mutableListOf<Llamada>()
    /** El próximo `/v1/messages` contesta esto (código, cuerpo) en vez de lo de siempre. */
    var siguiente: Transporte.Respuesta? = null
    var estadoLote = "in_progress"
    /** La búsqueda web del club: el texto final (el JSON) y si la primera vuelta se para (`pause_turn`). */
    var textoClub: String = """Aquí va lo que he encontrado: {"encontrado": true, "nombre": "Real Betis Balompié", "apodo": "Los verdiblancos", "ciudad": "Sevilla", "pais": "España",
        |"estadio": "Benito Villamarín", "aforo": 60721, "liga": "LaLiga", "division": "Primera", "entrenador": "Entrenador de verdad", "presidente": "Presidente de verdad",
        |"colores": "verde y blanco", "rivales": ["Sevilla FC"], "palmares": "Una Liga y tres Copas del Rey.", "contexto": "En Europa y con la economía saneada.",
        |"aficion": "Fiel y ruidosa.", "medios": ["Diario de ejemplo"], "competiciones": ["LaLiga EA Sports", "Copa del Rey", "UEFA Europa League"], "dudas": ""}""".trimMargin()
    var pausaClub = false
    private val lotes = LinkedHashMap<String, List<Map<String, Any?>>>()

    fun mensajes(): List<Llamada> = llamadas.filter { it.url.endsWith("/v1/messages") }

    override fun post(url: String, cabeceras: Map<String, String>, cuerpo: String): Transporte.Respuesta {
        val c = Json.lee(cuerpo).comoMapa()
        llamadas += Llamada("POST", url, cabeceras, c)
        siguiente?.let { siguiente = null; return it }
        return when {
            url.endsWith("/v1/messages") && c["tools"].comoLista().any { it.comoMapa()["name"] == "web_search" } -> Transporte.Respuesta(200, busqueda(c))
            url.endsWith("/v1/messages") -> Transporte.Respuesta(200, mensaje(c))
            url.endsWith("/v1/messages/batches") -> {
                val id = "msgbatch_${lotes.size + 1}"
                lotes[id] = c["requests"].comoLista().map { it.comoMapa() }
                Transporte.Respuesta(200, Json.escribe(mapOf("id" to id, "type" to "message_batch", "processing_status" to "in_progress", "results_url" to null)))
            }
            else -> Transporte.Respuesta(404, """{"type":"error","error":{"type":"not_found_error","message":"no"}}""")
        }
    }

    override fun get(url: String, cabeceras: Map<String, String>): Transporte.Respuesta {
        llamadas += Llamada("GET", url, cabeceras, emptyMap())
        val id = url.substringAfter("/v1/messages/batches/").substringBefore("/")
        val peticiones = lotes[id] ?: return Transporte.Respuesta(404, """{"type":"error","error":{"type":"not_found_error","message":"no"}}""")
        if (url.endsWith("/results")) return Transporte.Respuesta(200, peticiones.joinToString("\n") { r ->
            Json.escribe(mapOf("custom_id" to r["custom_id"], "result" to mapOf("type" to "succeeded", "message" to Json.lee(mensaje(r["params"].comoMapa())))))
        })
        return Transporte.Respuesta(200, Json.escribe(mapOf("id" to id, "type" to "message_batch", "processing_status" to estadoLote,
            "results_url" to if (estadoLote == "ended") "https://api.anthropic.com/v1/messages/batches/$id/results" else null)))
    }

    /** La búsqueda web (la herramienta de servidor): sus bloques como los de verdad, con 2 búsquedas en `usage`. */
    private fun busqueda(c: Map<String, Any?>): String {
        val continua = c["messages"].comoLista().size > 1
        val bloques = if (pausaClub && !continua) listOf(
            mapOf("type" to "server_tool_use", "id" to "srvtoolu_1", "name" to "web_search", "input" to mapOf("query" to "Real Betis estadio")),
            mapOf("type" to "web_search_tool_result", "tool_use_id" to "srvtoolu_1", "content" to listOf(
                mapOf("type" to "web_search_result", "url" to "https://es.wikipedia.org/wiki/Real_Betis_Balompi%C3%A9", "title" to "Real Betis Balompié - Wikipedia", "encrypted_content" to "x", "page_age" to "1 de enero de 2026"))),
        ) else listOf(
            mapOf("type" to "server_tool_use", "id" to "srvtoolu_2", "name" to "web_search", "input" to mapOf("query" to "Real Betis entrenador")),
            mapOf("type" to "web_search_tool_result", "tool_use_id" to "srvtoolu_2", "content" to listOf(
                mapOf("type" to "web_search_result", "url" to "https://www.realbetisbalompie.es/", "title" to "Web oficial", "encrypted_content" to "y"))),
            mapOf("type" to "text", "text" to textoClub, "citations" to listOf(mapOf("type" to "web_search_result_location", "url" to "https://www.laliga.com/", "title" to "LaLiga", "cited_text" to "Betis"))),
        )
        return Json.escribe(mapOf("id" to "msg_${llamadas.size}", "type" to "message", "role" to "assistant", "model" to c["model"].comoTexto(), "content" to bloques,
            "stop_reason" to if (pausaClub && !continua) "pause_turn" else "end_turn",
            "usage" to mapOf("input_tokens" to 10_000, "output_tokens" to 800, "cache_creation_input_tokens" to 0, "cache_read_input_tokens" to 0,
                "server_tool_use" to mapOf("web_search_requests" to 1))))
    }

    /** Lo que contestaría Claude a esta petición (según su esquema). */
    private fun mensaje(c: Map<String, Any?>): String {
        val modelo = c["model"].comoTexto()
        val props = Peticion.esquemaDe(c).orEmpty()["properties"].comoMapa().keys
        val texto = c["messages"].comoLista().flatMap { it.comoMapa()["content"].comoLista() }.joinToString { it.comoMapa()["text"].comoTexto() }
        val respuesta: Any = when {
            "respuesta" in props && "actitud" in props -> mapOf("respuesta" to listOf("Vale, te he escuchado.", "Dejo de insistirte con lo del cambio de club. De momento."),
                "cambio_afinidad" to 8, "actitud" to "Ha dejado de presionarle con el cambio de club", "convencido" to true, "resumen" to "Le convenció para que dejara de insistir.",
                "posts" to emptyList<Any>(), "mensajes" to emptyList<Any>(), "efectos" to mapOf("dinero" to 0, "fama" to 0, "aficion" to 0, "disciplina" to 0, "carisma" to 1, "relaciones" to emptyList<Any>()))
            "despedida" in props -> mapOf("despedida" to listOf("¿Me despides por WhatsApp?", "Mis abogados lo mirarán."), "titular" to "Se queda sin representante",
                "texto" to "Rompe con su agente y llegan candidatos.", "tendencia" to mapOf("hashtag" to "#SinAgente", "posts" to 900), "posts" to listOf(post("Diario Gol", "diariogol", "Rompe con su agente")),
                "mensajes" to emptyList<Any>(), "candidatos" to listOf(mapOf("nombre" to "Elena Sanz", "rol" to "representante (candidata)", "nota" to "Abogada deportiva")),
                "efectos" to mapOf("dinero" to 0, "fama" to 1, "aficion" to 1, "disciplina" to 2, "carisma" to 0, "relaciones" to emptyList<Any>()))
            "grupos" in props -> mapOf("grupos" to texto.substringAfter("Los nombres que hay que agrupar: ").substringBefore("\n").split(" | ").map { n ->
                mapOf("nombre" to n, "competicion" to if (n.contains("Gamper", true)) "Amistoso" else if (n.contains("UCL", true) || n.contains("orejona", true)) "UEFA Europa League" else "Otra") })
            "se_ve" in props -> mapOf("se_ve" to true, "goles_favor" to 3, "goles_contra" to 1, "rival" to "Sevilla FC", "minutos" to 90, "goles" to 2, "asistencias" to 1, "nota" to 8.7,
                "datos_equipo" to listOf(mapOf("dato" to "Posesión", "valor" to "58 / 42"), mapOf("dato" to "Tiros", "valor" to "15 / 7")), "rendimiento" to "6 tiros, 4 a puerta", "dudas" to "")
            "gente" in props -> mapOf("titulo" to "Llega el Niño", "texto" to "Presentado ante cuatro mil personas y una pancarta con faltas de ortografía.",
                "gente" to listOf(mapOf("nombre" to "Loli Cortés", "rol" to "madre", "afinidad" to 85, "nota" to "Lo ve todo, lo comenta todo"),
                    mapOf("nombre" to "Toni Vidal", "rol" to "representante", "afinidad" to 60, "nota" to "Siempre al teléfono"),
                    mapOf("nombre" to "Vera Salinas", "rol" to "ex", "afinidad" to 30, "nota" to "Influencer de moda; no le ha olvidado")),
                "prensa" to mapOf("cabecera" to "Diario Gol", "titular" to "Ya está aquí", "entradilla" to "El chico que lo rompía en el filial llega al primer equipo.", "otros_titulares" to listOf("Su madre ya ha pedido entradas")),
                "posts" to listOf(post("Peña El Bombo", "penaelbombo", "Bienvenido, chaval. Aquí se suda la camiseta.")), "mensajes" to listOf(mapOf("de" to "Loli Cortés", "texto" to "Abrígate, que en ese estadio hace un frío que pela.")))
            "prensa" in props -> mapOf(
                "prensa" to mapOf("cabecera" to "Diario Gol", "titular" to "El Niño se sale", "entradilla" to "Doblete y asistencia en una noche para enmarcar.", "otros_titulares" to listOf("Vera Salinas, en el palco", "El vestuario, de fiesta")),
                "ofertas" to listOf(mapOf("de" to "Villarreal CF", "tipo" to "traspaso", "salario_semanal" to 42000, "prima" to 500000, "anios" to 4, "texto" to "Quieren hacerle titular indiscutible.")),
                "decision" to mapOf("hay" to true, "de" to "Toni Vidal", "texto" to "Hay una fiesta en el reservado de Alma esta noche. ¿Vas?", "opciones" to listOf("Voy, y que se entere todo el mundo", "Me quedo en casa", "Voy, pero de incógnito")),
                "efectos" to mapOf("dinero" to 0, "fama" to 4, "aficion" to 3, "disciplina" to 0, "carisma" to 1, "relaciones" to listOf(mapOf("nombre" to "Loli Cortés", "cambio" to 2))),
                "gente_nueva" to emptyList<Any>())
            "titular" in props -> mapOf("texto" to "Fue, y se enteró todo el mundo: la foto con dos modelos ya da la vuelta a la red.", "titular" to "Noche de reservado",
                "posts" to listOf(post("La Grada Rosa", "lagradarosa", "Qué callado se lo tenía el Niño 👀")), "mensajes" to listOf(mapOf("de" to "Loli Cortés", "texto" to "Ya hablaremos tú y yo.")),
                "efectos" to mapOf("dinero" to -3000, "fama" to 6, "aficion" to -2, "disciplina" to -5, "carisma" to 3, "relaciones" to listOf(mapOf("nombre" to "Loli Cortés", "cambio" to -6))),
                "gente_nueva" to listOf(mapOf("nombre" to "Irene Mora", "rol" to "ligue", "nota" to "Modelo; sale en la foto")))
            "titulo" in props -> mapOf("titulo" to if ("Esta vez, algo de su imagen" in texto) "Una marca de relojes quiere tu muñeca" else "Una invitación a cenar",
                "texto" to "Vera Salinas te escribe después de meses sin hablar.", "posts" to emptyList<Any>(), "mensajes" to listOf(mapOf("de" to "Vera Salinas", "texto" to "¿Cenamos? Te invito yo.")),
                "decision" to mapOf("hay" to true, "de" to "Vera Salinas", "texto" to "¿Vas a la cena?", "opciones" to listOf("Sí", "No")),
                "ofertas" to emptyList<Any>(), "efectos" to mapOf("dinero" to 0, "fama" to 1, "aficion" to 0, "disciplina" to 0, "carisma" to 0, "relaciones" to emptyList<Any>()), "gente_nueva" to emptyList<Any>())
            else -> mapOf("posts" to (1..9).map { post("Aficionado $it", "afi$it", "Qué partidazo del Niño, madre mía ($it)") },
                "mensajes" to listOf(mapOf("de" to "Loli Cortés", "texto" to "¡Ese es mi niño!"), mapOf("de" to "Vera Salinas", "texto" to "Te he visto en la tele. Estás más guapo.")))
        }
        // Como la de verdad: a veces un bloque de pensar (vacío) antes del texto
        return Json.escribe(mapOf("id" to "msg_${llamadas.size}", "type" to "message", "role" to "assistant", "model" to modelo,
            "content" to listOf(mapOf("type" to "thinking", "thinking" to "", "signature" to "x"), mapOf("type" to "text", "text" to Json.escribe(respuesta))),
            "stop_reason" to "end_turn", "usage" to mapOf("input_tokens" to 2000, "output_tokens" to 1000, "cache_creation_input_tokens" to 0, "cache_read_input_tokens" to 1000)))
    }

    private fun post(a: String, u: String, t: String) = mapOf("autor" to a, "usuario" to u, "texto" to t, "megusta" to 1234, "reposts" to 56, "tipo" to "aficion")
}

/** Una carrera de ejemplo (en memoria, con la API de mentira): para las pruebas y las capturas. */
object Ejemplo {
    fun prepara(): ApiDeMentira {
        Carreras.almacen = AlmacenMemoria()
        ClaveApi.fija = "sk-ant-api03-de-mentira-0000"
        Gasto.mesFijo = java.time.YearMonth.of(2026, 11)
        val api = ApiDeMentira()
        Motor.claude = Claude({ ClaveApi.lee() }, api)
        Motor.reloj = { System.currentTimeMillis() }
        return api
    }

    fun universo() = Universo(Universo.nuevoId(), Rol.JUGADOR, Personaje("Álex Cortés", "el Niño", 19, "España", "DC", 9, 74,
        mapOf("RIT" to 86, "TIR" to 76, "PAS" to 64, "REG" to 79, "DEF" to 31, "FIS" to 68), "Ambicioso, fiestero y mujeriego", listOf(0 to 74)),
        "Real Betis", "LaLiga", "2026/27", 18_000, "2028/29", 250_000, contrato = Contrato("Real Betis", 18_000, "2026/27", "2028/29", clausula = 30_000_000))

    fun partido(gf: Int = 3, gc: Int = 1, rival: String = "Sevilla FC") = Partido("2026/27", "LaLiga", rival, true, gf, gc, 90, 2, 1, 8.7)

    /** Una carrera con su principio, dos partidos, una decisión contestada y los dos eventos de entre partidos ya llegados. */
    fun completa(api: ApiDeMentira): Universo {
        val (u, _) = Motor.crea(universo())
        Motor.partido(u.id, partido(2, 2, "Athletic Club"))
        val (e, _) = Motor.partido(u.id, partido())
        Motor.decide(e.id, "Voy, y que se entere todo el mundo")
        api.estadoLote = "ended"
        Motor.miraLotes()
        Motor.revela(forzar = true)
        return Carreras.de(u.id)!!
    }
}
