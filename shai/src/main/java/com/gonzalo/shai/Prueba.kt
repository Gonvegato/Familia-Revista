package com.gonzalo.shai

import android.content.Context
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import java.io.File
import kotlin.random.Random

/**
 * El modo prueba: Shai entero sin IA y sin gastar nada. Lo de fuera del campo lo escriben unas plantillas de ejemplo (no Claude), con
 * los nombres de tu carrera, tu gente, el rival y el marcador; por lo demás, es la app de verdad (las mismas pantallas, la misma
 * lógica de dinero, decisiones, ofertas y eventos). Las carreras de prueba van aparte, en este móvil (no en tu cuenta), así que no se
 * mezclan con las de verdad; y el gasto es siempre 0. Para ver las funciones, no para jugar en serio: los textos se repiten.
 */
object Prueba {
    private const val PREFS = "shai_prueba"
    @Volatile var activo: Boolean = false
        private set
    private var real: Pair<Almacen, Claude>? = null

    fun cargar(c: Context) {
        val on = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("activo", false)
        if (on) enciende(c)
    }

    fun pon(c: Context, on: Boolean) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("activo", on).apply()
        if (on) enciende(c) else apaga()
        Carreras.avisa()
    }

    /** Borra todas las carreras de prueba. */
    fun vacia() { (Carreras.almacen as? AlmacenArchivo)?.vacia(); Carreras.avisa() }

    private fun enciende(c: Context) {
        if (activo) return
        real = Carreras.almacen to Motor.claude
        Carreras.almacen = AlmacenArchivo(File(c.filesDir, "prueba.json"))
        Motor.claude = Claude({ "sk-ant-prueba" }, TransporteDePrueba())
        activo = true
    }

    private fun apaga() {
        if (!activo) return
        real?.let { (a, cl) -> Carreras.almacen = a; Motor.claude = cl }
        real = null
        activo = false
    }
}

/** Un [Almacen] en un archivo de este móvil (las carreras de prueba). */
class AlmacenArchivo(private val archivo: File) : Almacen {
    private val datos = HashMap<String, Map<String, Any?>>()
    init {
        runCatching { Json.lee(archivo.readText()).comoMapa().forEach { (k, v) -> datos[k] = v.comoMapa() } }
    }
    @Synchronized override fun nodos(): Map<String, Map<String, Any?>> = HashMap(datos)
    @Synchronized override fun pon(ref: String, campos: Map<String, Any?>) { datos[ref] = (datos[ref].orEmpty() + campos).filterValues { it != null && it != "" }; guarda() }
    @Synchronized override fun borra(ref: String) { datos.remove(ref); guarda() }
    @Synchronized fun vacia() { datos.clear(); guarda() }
    private fun guarda() { runCatching { archivo.writeText(Json.escribe(datos)) } }
}

/**
 * La API de mentira del modo prueba: contesta como la de verdad (`/v1/messages` con salida estructurada y los lotes, que acaban al
 * momento), con 0 tokens gastados. No sale nada a la red.
 */
class TransporteDePrueba(private val azar: Random = Random.Default) : Transporte {
    private val lotes = HashMap<String, List<Map<String, Any?>>>()

    override fun post(url: String, cabeceras: Map<String, String>, cuerpo: String): Transporte.Respuesta {
        val c = Json.lee(cuerpo).comoMapa()
        return when {
            url.endsWith("/v1/messages") -> Transporte.Respuesta(200, mensaje(c))
            url.endsWith("/v1/messages/batches") -> synchronized(lotes) {
                val id = "msgbatch_prueba_${lotes.size + 1}_${System.currentTimeMillis()}"
                lotes[id] = c["requests"].comoLista().map { it.comoMapa() }
                Transporte.Respuesta(200, Json.escribe(mapOf("id" to id, "type" to "message_batch", "processing_status" to "in_progress", "results_url" to null)))
            }
            else -> Transporte.Respuesta(404, """{"type":"error","error":{"type":"not_found_error","message":"no"}}""")
        }
    }

    override fun get(url: String, cabeceras: Map<String, String>): Transporte.Respuesta {
        val id = url.substringAfter("/v1/messages/batches/").substringBefore("/")
        val ps = synchronized(lotes) { lotes[id] } ?: return Transporte.Respuesta(404, """{"type":"error","error":{"type":"not_found_error","message":"Ese lote ya no está (el modo prueba se reinició)"}}""")
        if (url.endsWith("/results")) return Transporte.Respuesta(200, ps.joinToString("\n") { r ->
            Json.escribe(mapOf("custom_id" to r["custom_id"], "result" to mapOf("type" to "succeeded", "message" to Json.lee(mensaje(r["params"].comoMapa())))))
        })
        return Transporte.Respuesta(200, Json.escribe(mapOf("id" to id, "type" to "message_batch", "processing_status" to "ended",
            "results_url" to "https://api.anthropic.com/v1/messages/batches/$id/results")))
    }

    // ---------------------------------------------------------------- lo que se sabe de la carrera (del propio mensaje)

    private class Datos(val nombre: String, val apodo: String, val club: String, val rival: String, val marcador: String, val gano: Boolean?, val gente: List<Pair<String, String>>)

    private fun datos(t: String): Datos {
        val nombre = Regex("""- (?:Jugador|Entrenador): ([^(,\n]+)""").find(t)?.groupValues?.get(1)?.trim().orEmpty().ifBlank { "el chaval" }
        val apodo = Regex("""\(«([^»]+)»\)""").find(t)?.groupValues?.get(1).orEmpty()
        val club = Regex("""- Club: ([^(,\n]+)""").find(t)?.groupValues?.get(1)?.trim().orEmpty().ifBlank { "su club" }
        val partido = t.substringAfter("ACABA DE JUGAR ESTE PARTIDO:", "")
        val rival = Regex("""(?:En casa contra|En el campo del) ([^(\n]+?) \(""").find(partido)?.groupValues?.get(1)?.trim().orEmpty()
        val marcador = Regex("""\): (\d+-\d+)""").find(partido)?.groupValues?.get(1).orEmpty()
        val gano = when { "(victoria)" in partido -> true; "(derrota)" in partido -> false; else -> null }
        val gente = Regex("""  · ([^,\n]+), ([^,\n]+), afinidad""").findAll(t).map { it.groupValues[1].trim() to it.groupValues[2].trim() }.toList()
        return Datos(nombre, apodo, club, rival, marcador, gano, gente)
    }

    private fun <T> List<T>.uno(): T = this[azar.nextInt(size)]
    private fun <T> List<T>.varios(n: Int): List<T> = shuffled(azar).take(n)
    private fun alias(d: Datos) = d.apodo.ifBlank { d.nombre.substringBefore(' ') }
    private fun alguien(d: Datos, vararg roles: String): String =
        d.gente.firstOrNull { (_, r) -> roles.any { r.contains(it, ignoreCase = true) } }?.first ?: d.gente.map { it.first }.ifEmpty { listOf("Toni Vidal") }.uno()
    private fun usuario(n: String) = n.lowercase().replace(Regex("[^a-z0-9ñ]"), "").take(15)

    private fun post(autor: String, texto: String, tipo: String) =
        mapOf("autor" to autor, "usuario" to usuario(autor), "texto" to texto, "megusta" to azar.nextInt(80, 25_000), "reposts" to azar.nextInt(3, 2_000), "tipo" to tipo)

    private fun efectos(dinero: Long = 0, rango: Int = 3, gente: List<Pair<String, Int>> = emptyList()) = mapOf(
        "dinero" to dinero, "fama" to azar.nextInt(-1, rango + 1), "aficion" to azar.nextInt(-rango, rango + 1), "disciplina" to azar.nextInt(-rango, rango + 1),
        "carisma" to azar.nextInt(-1, rango + 1), "relaciones" to gente.map { mapOf("nombre" to it.first, "cambio" to it.second) })

    private val NADA_DECISION = mapOf("hay" to false, "de" to "", "texto" to "", "opciones" to emptyList<String>())

    // ---------------------------------------------------------------- las respuestas, según lo que se pide (su esquema)

    private fun mensaje(c: Map<String, Any?>): String {
        val props = c["output_config"].comoMapa()["format"].comoMapa()["schema"].comoMapa()["properties"].comoMapa().keys
        val texto = c["messages"].comoLista().flatMap { it.comoMapa()["content"].comoLista() }.joinToString("\n") { it.comoMapa()["text"].comoTexto() }
        val d = datos(texto)
        val r: Any = when {
            "se_ve" in props -> foto(d)
            "gente" in props -> inicio(d)
            "prensa" in props -> prensa(d)
            "titular" in props -> respuesta(d, texto)
            "titulo" in props -> evento(d, texto)
            "ES OFICIAL" in texto -> red(d, oficial = true)
            else -> red(d, oficial = false)
        }
        // «de el Niño» → «del Niño», «a el Niño» → «al Niño» (los apodos con artículo)
        val limpio = Json.lee(Json.escribe(r).replace(Regex("""\b([Dd])e el\b"""), "$1el").replace(Regex("""\b([Aa]) el\b"""), "$1l"))
        return Json.escribe(mapOf("id" to "msg_prueba", "type" to "message", "role" to "assistant", "model" to c["model"].comoTexto(),
            "content" to listOf(mapOf("type" to "text", "text" to Json.escribe(limpio))), "stop_reason" to "end_turn",
            "usage" to mapOf("input_tokens" to 0, "output_tokens" to 0, "cache_creation_input_tokens" to 0, "cache_read_input_tokens" to 0)))
    }

    private fun foto(d: Datos) = mapOf("se_ve" to true, "goles_favor" to 2, "goles_contra" to 1, "rival" to "Valencia CF", "minutos" to 90, "goles" to 1, "asistencias" to 1, "nota" to 7.8,
        "datos_equipo" to listOf(mapOf("dato" to "Posesión", "valor" to "55 / 45"), mapOf("dato" to "Tiros", "valor" to "14 / 9"), mapOf("dato" to "Tiros a puerta", "valor" to "6 / 3")),
        "rendimiento" to "4 tiros, 2 a puerta, 31 pases (modo prueba: la foto no se ha leído de verdad)", "dudas" to "Modo prueba: estos datos son de ejemplo, no salen de tu foto.")

    private fun inicio(d: Datos): Map<String, Any?> {
        val a = alias(d)
        val ape = d.nombre.substringAfter(' ', "").ifBlank { "Ruiz" }
        val gente = listOf(
            mapOf("nombre" to "Loli $ape", "rol" to "madre", "afinidad" to 85, "nota" to "Lo ve todo y lo comenta todo en el grupo de la familia"),
            mapOf("nombre" to "Toni Vidal", "rol" to "representante", "afinidad" to 60, "nota" to "Siempre al teléfono, siempre con un negocio"),
            mapOf("nombre" to "Kike Lozano", "rol" to "amigo de siempre", "afinidad" to 75, "nota" to "Vive de ti un poco más de lo que debería"),
            mapOf("nombre" to "Vera Salinas", "rol" to "ex", "afinidad" to 30, "nota" to "Influencer de moda; no te ha olvidado"),
            mapOf("nombre" to "Marta Gil", "rol" to "periodista", "afinidad" to 45, "nota" to "Te sigue desde el filial y huele las exclusivas"),
            mapOf("nombre" to "Dani Prieto", "rol" to "compañero del vestuario", "afinidad" to 55, "nota" to "El que organiza todas las cenas"))
        return mapOf("titulo" to "Llega $a", "texto" to "Presentado en ${d.club} ante cuatro mil personas y una pancarta con faltas de ortografía. Su madre lloró dos veces.",
            "gente" to gente,
            "prensa" to mapOf("cabecera" to "Diario Gol", "titular" to "Ya está aquí", "entradilla" to "$a aterriza en ${d.club} con ganas de comerse el mundo.", "otros_titulares" to listOf("Su madre ya ha pedido entradas", "La grada, dividida con su peinado")),
            "posts" to listOf(post("Peña El Bombo", "Bienvenido, $a. Aquí se suda la camiseta.", "aficion"), post("Marta Gil", "Primera exclusiva: $a ya tiene casa en la ciudad. Y vistas.", "prensa"),
                post("Vera Salinas", "Hay gente que cambia de ciudad y no cambia nada más 🙃", "entorno"), post("Kike Lozano", "¡Ese es mi hermano! 🔥", "entorno")),
            "mensajes" to listOf(mapOf("de" to "Loli $ape", "texto" to "Abrígate, que en ese estadio hace un frío que pela."), mapOf("de" to "Toni Vidal", "texto" to "Tengo dos marcas interesadas. Llámame.")))
    }

    private fun red(d: Datos, oficial: Boolean): Map<String, Any?> {
        val a = alias(d)
        val r = d.rival.ifBlank { "el rival" }
        val bien = listOf("Qué partidazo de $a, madre mía", "$a es otra cosa. Otra liga.", "Hoy $a se ha ganado la cena gratis en toda la ciudad",
            "Con $a en el campo da gusto pagar el abono", "Que alguien le haga una estatua a $a. Ya.", "$a, cásate conmigo (es broma) (no es broma)")
        val mal = listOf("$a hoy desaparecido. Ni el GPS lo encuentra.", "Mucho Instagram y poco balón, $a", "Hoy toca silencio. Y $a, a entrenar más.",
            "$a, el partido era a las 9, no a las 11", "Con todo el cariño: $a, hoy no.")
        val rival = listOf("Lo de $a hoy... no lo vamos a comentar", "Ya veremos en la vuelta, $a 😏", "Mucho ruido con $a, pocas nueces")
        val bueno = d.gano != false
        val posts = if (oficial) listOf(
            post("Peña El Bombo", "Se nos va $a. Que te vaya bonito. O no.", "aficion"), post("Diario Gol", "OFICIAL: $a cambia de aires. Todos los detalles, aquí.", "prensa"),
            post("Grada Joven", "Bienvenido, $a. Ahora a demostrar.", "aficion"), post(alguien(d, "amigo"), "Mi hermano a lo grande 🙌", "entorno"),
            post("La Grada Rosa", "¿Y Vera Salinas qué opina de la mudanza? 👀", "prensa"))
        else (if (bueno) bien.varios(4) else mal.varios(4)).map { post(listOf("Peña El Bombo", "Abonado 1902", "Grada Joven", "Señora del palco", "Cuenta de memes").uno(), it, "aficion") } +
            listOf(post("Diario Gol", "${d.club} ${d.marcador.ifBlank { "" }} ante $r. ${if (bueno) "$a, protagonista." else "Noche para olvidar."}".replace("  ", " "), "prensa"),
                post("Afición del $r", rival.uno(), "rival"), post(alguien(d, "amigo", "compañero"), if (bueno) "Esta noche invita $a 🍾" else "Cabeza alta, hermano. El domingo más.", "entorno"),
                post("La Grada Rosa", listOf("¿Quién era la rubia del palco? Preguntamos por un amigo 👀", "$a salió del estadio con gafas de sol. De noche.", "Vera Salinas ha dado like y lo ha quitado. Lo hemos visto.").uno(), "prensa"))
        val dms = listOf(
            mapOf("de" to alguien(d, "madre", "padre"), "texto" to if (bueno) "¡Ese es mi niño! Ya lo he puesto en el grupo." else "Tú tranquilo, que la culpa es del árbitro."),
            mapOf("de" to listOf("Vera Salinas", "Irene Mora", "Carla Vidal").uno(), "texto" to listOf("Te he visto en la tele. Estás más guapo.", "¿Sigues teniendo el mismo número? 😏", "Bonito gol. ¿Lo celebras con alguien?").uno()),
            mapOf("de" to alguien(d, "representante"), "texto" to listOf("Hay movimiento. No digas nada a nadie.", "Mañana a las 10, llamada importante.", "Me han preguntado por ti desde Italia.").uno()))
        return mapOf("posts" to posts, "mensajes" to dms.varios(2 + azar.nextInt(2)))
    }

    private fun prensa(d: Datos): Map<String, Any?> {
        val a = alias(d)
        val r = d.rival.ifBlank { "el rival" }
        val titular = when (d.gano) {
            true -> listOf("$a se sale", "Noche de $a", "$a manda", "El ${d.club} de $a").uno()
            false -> listOf("$a se apaga", "Mal día en la oficina", "Ni rastro de $a", "Toca reaccionar").uno()
            null -> listOf("Tablas con sabor raro", "$a, a medias", "Un punto y muchas dudas").uno()
        }
        val ofertas = if (azar.nextInt(3) == 0) listOf(listOf(
            mapOf("de" to "Villarreal CF", "tipo" to "traspaso", "salario_semanal" to 42_000, "prima" to 500_000, "anios" to 4, "texto" to "Quieren hacerle titular indiscutible."),
            mapOf("de" to "Relojes Arenal", "tipo" to "patrocinio", "salario_semanal" to 0, "prima" to 120_000, "anios" to 2, "texto" to "Una campaña de relojes con su cara en las marquesinas."),
            mapOf("de" to d.club, "tipo" to "renovacion", "salario_semanal" to 35_000, "prima" to 250_000, "anios" to 5, "texto" to "El club quiere blindarle antes de que lleguen otros."),
        ).uno()) else emptyList()
        val decision = if (azar.nextInt(2) == 0) listOf(
            mapOf("hay" to true, "de" to alguien(d, "compañero", "amigo"), "texto" to "Hay una fiesta en el reservado de Alma esta noche para celebrarlo. ¿Vas?", "opciones" to listOf("Voy, y que se entere todo el mundo", "Me quedo en casa", "Voy, pero de incógnito")),
            mapOf("hay" to true, "de" to "Marta Gil", "texto" to "Te pide unas declaraciones sobre lo de $r. ¿Qué le dices?", "opciones" to listOf("Que hablen en el campo", "Lanzar una pulla al rival", "No contestar")),
            mapOf("hay" to true, "de" to "Irene Mora", "texto" to "Una modelo te escribe: «¿Te apetece tomar algo esta semana?»", "opciones" to listOf("Quedar ya", "Hacerse el interesante", "Dejarla en visto")),
        ).uno() else NADA_DECISION
        return mapOf(
            "prensa" to mapOf("cabecera" to listOf("Diario Gol", "El Balón", "Tribuna Deportiva").uno(), "titular" to titular,
                "entradilla" to "${d.club} ${d.marcador} ante $r. ${if (d.gano == true) "$a, en el centro de todo." else "Ahora toca pasar página."}",
                "otros_titulares" to listOf("Vera Salinas, en el palco", "El vestuario, de cena", "Su representante, de reuniones", "La grada pide más minutos").varios(2)),
            "ofertas" to ofertas, "decision" to decision,
            "efectos" to efectos(gente = listOfNotNull(d.gente.firstOrNull()?.let { it.first to (if (d.gano == true) 2 else -1) })), "gente_nueva" to emptyList<Any>())
    }

    private fun evento(d: Datos, texto: String): Map<String, Any?> {
        val a = alias(d)
        val imagen = "Esta vez, algo de su imagen" in texto
        val e = if (imagen) listOf(
            Triple("Una marca de relojes quiere tu muñeca", "Relojes Arenal busca cara para su campaña de primavera. Toni Vidal ya ha dicho que sí sin preguntarte.", listOf("Aceptar y cobrar", "Pedir el doble", "Rechazarlo")),
            Triple("Un restaurante con tu nombre", "Kike te propone abrir un bar de tapas con tu apellido en el letrero. Él pone las ganas; tú, el dinero.", listOf("Invertir 200.000 €", "Invertir, pero menos", "Ni de broma")),
            Triple("Tu coche, en la red", "Una cuenta de memes ha subido una foto de tu coche aparcado en doble fila delante del estadio.", listOf("Pedir perdón en redes", "Reírte del meme", "No decir nada")),
        ).uno() else listOf(
            Triple("Una invitación a cenar", "Vera Salinas te escribe después de meses sin hablar. Dice que solo es una cena.", listOf("Ir a la cena", "Contestar con un emoji", "Bloquearla")),
            Triple("La fiesta de cumpleaños de Dani", "Dani Prieto cumple años y ha alquilado una discoteca entera. Al día siguiente hay entrenamiento a las nueve.", listOf("Ir y quedarse hasta el final", "Pasarse un rato", "No ir")),
            Triple("Tu madre ha hablado con la prensa", "${alguien(d, "madre", "padre")} ha concedido una entrevista a una revista de cotilleo. Ha contado lo de tu primera novia.", listOf("Llamarla enfadado", "Reírte y compartirla", "No decir nada")),
        ).uno()
        return mapOf("titulo" to e.first, "texto" to e.second.replace("Toni Vidal", alguien(d, "representante")),
            "posts" to listOf(post("La Grada Rosa", "Atentos a lo de $a esta semana 👀", "prensa")),
            "mensajes" to listOf(mapOf("de" to alguien(d, if (imagen) "representante" else "amigo"), "texto" to "¿Te has enterado de lo de ${e.first.lowercase()}?")),
            "decision" to mapOf("hay" to true, "de" to when { "Vera" in e.second -> "Vera Salinas"; "Dani" in e.second -> "Dani Prieto"; "Kike" in e.second -> alguien(d, "amigo"); else -> alguien(d, if (imagen) "representante" else "madre", "padre") },
                "texto" to "¿Qué haces?", "opciones" to e.third),
            "ofertas" to emptyList<Any>(), "efectos" to efectos(rango = 1), "gente_nueva" to emptyList<Any>())
    }

    private fun respuesta(d: Datos, texto: String): Map<String, Any?> {
        val a = alias(d)
        val eligio = texto.substringAfter("LO QUE HA DECIDIDO ÉL:", "").lineSequence().firstOrNull()?.trim().orEmpty()
        val cuesta = Regex("""(\d{1,3}(?:\.\d{3})+|\d+) ?€""").find(eligio)?.groupValues?.get(1)?.replace(".", "")?.toLongOrNull() ?: 0L
        val bien = azar.nextBoolean()
        return mapOf(
            "texto" to "Eligió «$eligio». " + if (bien) "Salió mejor de lo esperado: la red lo aplaude y hasta su madre ha dado like." else "No salió como esperaba: la foto ya da la vuelta a la red y en el club no les ha hecho gracia.",
            "titular" to if (bien) "$a acierta" else "$a, en el ojo del huracán",
            "posts" to listOf(post("La Grada Rosa", if (bien) "Bien jugado, $a 👏" else "Lo de $a no hay quien lo explique 🍿", "prensa"), post("Peña El Bombo", if (bien) "Así se hace" else "Menos fiesta y más balón", "aficion")),
            "mensajes" to listOf(mapOf("de" to alguien(d, "madre", "padre"), "texto" to if (bien) "Estoy muy orgullosa de ti." else "Ya hablaremos tú y yo.")),
            "efectos" to mapOf("dinero" to -cuesta, "fama" to azar.nextInt(1, 7), "aficion" to if (bien) azar.nextInt(1, 6) else -azar.nextInt(1, 6),
                "disciplina" to if (bien) azar.nextInt(0, 4) else -azar.nextInt(2, 7), "carisma" to azar.nextInt(0, 4),
                "relaciones" to listOfNotNull(d.gente.firstOrNull()?.let { mapOf("nombre" to it.first, "cambio" to if (bien) 4 else -6) })),
            "gente_nueva" to if (!bien && azar.nextBoolean()) listOf(mapOf("nombre" to "Irene Mora", "rol" to "ligue", "nota" to "Modelo; sale en la foto")) else emptyList<Any>())
    }
}
