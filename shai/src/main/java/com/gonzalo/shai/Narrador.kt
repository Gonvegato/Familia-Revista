package com.gonzalo.shai

import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoDouble
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto

/**
 * Lo que se le pide a Claude y cómo se lee lo que contesta. Todo en salida estructurada (un esquema JSON por cosa), para que lo que vuelve
 * se pueda leer siempre igual. Haiku 5.5 (barato) para la red y los mensajes y para leer la foto; Sonnet 5.5 para la prensa, las
 * ofertas, las decisiones y lo que pasa entre partidos.
 *
 * El prompt de sistema es siempre el mismo (va en caché); lo de tu carrera va detrás, en el mensaje.
 */
object Narrador {
    /** Las reglas de siempre (iguales en todas las peticiones: van en caché). */
    val REGLAS = """
Eres el narrador de Shai, una app que acompaña las carreras que una persona juega en EA Sports FC 26 (modo carrera, de jugador o de míster). Tú escribes todo lo que pasa FUERA del campo: la prensa, la red social, los mensajes privados, las ofertas, la vida, el dinero y la gente. Escribes en español de España, con gracia, mala leche y mucho salseo, como la prensa deportiva y la prensa rosa de verdad.

Reglas que no se rompen nunca:
1. El campo es del jugador. Los resultados, los minutos, los goles, las asistencias, las notas, la media y las cifras del juego son los que él te da: no los cambies ni inventes otros. No inventes lesiones, suplencias, sanciones, alineaciones, resultados ni nada que pase dentro del campo o que él tenga que hacer en el juego. Puedes comentar y exagerar lo que te da, no añadir hechos del campo.
2. Personas reales (futbolistas, entrenadores, periodistas, famosos): solo de pasada y en público, en tono neutro o amable (un saludo, un comentario de un partido, una foto en un evento). Nunca les pongas romances, sexo, escándalos, delitos, insultos, problemas de salud ni frases polémicas. Todo lo íntimo, lo escandaloso y lo de pareja es SIEMPRE con personajes inventados.
3. El protagonista es un mujeriego (a no ser que sus rasgos digan otra cosa): ligues, rumores, fotos con chicas, invitaciones, celos y exparejas, siempre con mujeres inventadas y mayores de edad, a nivel de prensa rosa y de cotilleo. Nada de contenido sexual explícito: se insinúa, no se describe.
4. Clubes y competiciones: los reales se pueden nombrar (son el juego), sin inventarles escándalos institucionales graves. Medios de comunicación, marcas, empresas, discotecas, restaurantes e influencers: inventados (con nombres creíbles), nunca marcas reales.
5. El dinero en euros, con cifras creíbles para el nivel del protagonista y de su club. Las ofertas son propuestas que él decide en la app (si acepta, lo hará en el juego): de clubes reales que encajen con su nivel y su momento, o de marcas inventadas.
6. Coherencia: respeta lo que ya ha pasado (lo de su gente, sus relaciones, lo que eligió). Usa a su gente por su nombre. Si algo viene de una decisión suya, que se note.
7. Sé breve en cada pieza: los posts como tuits de verdad (hasta 280 caracteres), los mensajes como mensajes de móvil, los titulares como titulares.
""".trim()

    // ---------------------------------------------------------------- el contexto de la carrera

    /** Lo que Claude tiene que saber de la carrera (va en el mensaje, no en el sistema: cambia cada vez). */
    fun contexto(u: Universo, recientes: List<Entrada>, partidos: List<Partido>): String = buildString {
        val pj = u.personaje
        appendLine("CARRERA (${u.modo.nombre.lowercase()}):")
        appendLine("- ${if (u.modo == Rol.MISTER) "Entrenador" else "Jugador"}: ${pj.nombre}" + (if (pj.apodo.isNotBlank()) " («${pj.apodo}»)" else "") +
            ", ${pj.edad} años" + (if (pj.nacionalidad.isNotBlank()) ", ${pj.nacionalidad}" else "") +
            (if (u.modo == Rol.JUGADOR && pj.posicion.isNotBlank()) ", ${pj.posicion}" else "") + (if (pj.dorsal > 0) ", dorsal ${pj.dorsal}" else "") +
            (if (pj.media > 0) ", media ${pj.media} en el juego" else ""))
        if (pj.rasgos.isNotBlank()) appendLine("- Cómo es: ${pj.rasgos}")
        appendLine("- Club: ${u.club}" + (if (u.liga.isNotBlank()) " (${u.liga})" else "") + (if (u.temporada.isNotBlank()) ", temporada ${u.temporada}" else "") +
            ", jornada ${u.paso} de su carrera en Shai")
        appendLine("- Cobra ${Formato.euros(u.salario)} a la semana" + (if (u.contratoHasta.isNotBlank()) ", contrato: ${u.contratoHasta}" else "") +
            ". Dinero: ${Formato.euros(u.dinero)}; patrimonio: ${Formato.euros(u.patrimonio)}")
        appendLine("- Fuera del campo (0-100): " + Fuera.TODOS.joinToString(", ") { (k, n) -> "$n ${u.valorFuera(k)}" })
        if (u.bienes.isNotEmpty()) appendLine("- Tiene: " + u.bienes.joinToString(", ") { it.nombre })
        if (u.entorno.isNotEmpty()) {
            appendLine("- Su gente (todos inventados; afinidad 0-100):")
            u.entorno.forEach { p -> appendLine("  · ${p.nombre}, ${p.rol}, afinidad ${p.afinidad}" + (if (p.nota.isNotBlank()) ": ${p.nota}" else "")) }
        }
        if (partidos.isNotEmpty()) {
            val s = Estadisticas.linea(u.temporada, "", partidos.filter { it.temporada == u.temporada }.ifEmpty { partidos })
            appendLine("- Esta temporada: ${s.jugados} partidos (${s.victorias}V ${s.empates}E ${s.derrotas}D)" +
                (if (u.modo == Rol.JUGADOR) ", ${s.goles} goles, ${s.asistencias} asistencias" + (if (s.notaMedia > 0) ", nota media ${Formato.decimal(s.notaMedia)}" else "") else "") +
                ". Racha: ${Estadisticas.racha(partidos)}")
        }
        val antes = recientes.take(8)
        if (antes.isNotEmpty()) {
            appendLine("LO ÚLTIMO QUE HA PASADO (de lo más nuevo a lo más viejo):")
            antes.forEach { e -> appendLine("- " + resumen(e, u.modo)) }
        }
    }.trim()

    /** Una línea de algo que ya pasó. */
    fun resumen(e: Entrada, modo: Rol): String = when {
        e.partido != null -> "Partido: " + e.partido.resumen(modo) + (e.prensa?.let { " · Portada: «${it.titular}»" } ?: "")
        else -> listOf(e.titulo, e.texto.take(220)).filter { it.isNotBlank() }.joinToString(": ") +
            (e.decision?.takeIf { !it.abierta }?.let { d -> " · Eligió: «${d.elegida}»" + (if (d.consecuencia.isNotBlank()) " → ${d.consecuencia.take(160)}" else "") } ?: "") +
            (e.decision?.takeIf { it.abierta }?.let { " · (decisión pendiente: ${it.texto.take(100)})" } ?: "") +
            e.ofertas.filter { it.estado != Oferta.ABIERTA }.joinToString("") { " · Oferta de ${it.de}: ${it.estado}" }
    }

    // ---------------------------------------------------------------- los esquemas

    private fun obj(vararg campos: Pair<String, Map<String, Any?>>): Map<String, Any?> =
        mapOf("type" to "object", "properties" to campos.toMap(), "required" to campos.map { it.first }, "additionalProperties" to false)
    private fun lista(de: Map<String, Any?>) = mapOf("type" to "array", "items" to de)
    private val TEXTO = mapOf<String, Any?>("type" to "string")
    private val ENTERO = mapOf<String, Any?>("type" to "integer")
    private val NUMERO = mapOf<String, Any?>("type" to "number")
    private val SINO = mapOf<String, Any?>("type" to "boolean")
    private fun enumerado(vararg v: String) = mapOf<String, Any?>("type" to "string", "enum" to v.toList())

    private val POST = obj("autor" to TEXTO, "usuario" to TEXTO, "texto" to TEXTO, "megusta" to ENTERO, "reposts" to ENTERO,
        "tipo" to enumerado("aficion", "prensa", "rival", "famoso", "entorno"))
    private val DM = obj("de" to TEXTO, "texto" to TEXTO)
    private val PERSONA = obj("nombre" to TEXTO, "rol" to TEXTO, "nota" to TEXTO)
    private val EFECTOS = obj("dinero" to ENTERO, "fama" to ENTERO, "aficion" to ENTERO, "disciplina" to ENTERO, "carisma" to ENTERO,
        "relaciones" to lista(obj("nombre" to TEXTO, "cambio" to ENTERO)))
    private val DECISION = obj("hay" to SINO, "de" to TEXTO, "texto" to TEXTO, "opciones" to lista(TEXTO))
    private val OFERTA = obj("de" to TEXTO, "tipo" to enumerado("traspaso", "cesion", "renovacion", "patrocinio", "banquillo"),
        "salario_semanal" to ENTERO, "prima" to ENTERO, "anios" to ENTERO, "texto" to TEXTO)

    val ESQUEMA_RED = obj("posts" to lista(POST), "mensajes" to lista(DM))
    val ESQUEMA_PRENSA = obj("prensa" to obj("cabecera" to TEXTO, "titular" to TEXTO, "entradilla" to TEXTO, "otros_titulares" to lista(TEXTO)),
        "ofertas" to lista(OFERTA), "decision" to DECISION, "efectos" to EFECTOS, "gente_nueva" to lista(PERSONA))
    val ESQUEMA_EVENTO = obj("titulo" to TEXTO, "texto" to TEXTO, "posts" to lista(POST), "mensajes" to lista(DM), "decision" to DECISION,
        "ofertas" to lista(OFERTA), "efectos" to EFECTOS, "gente_nueva" to lista(PERSONA))
    val ESQUEMA_RESPUESTA = obj("texto" to TEXTO, "titular" to TEXTO, "posts" to lista(POST), "mensajes" to lista(DM), "efectos" to EFECTOS, "gente_nueva" to lista(PERSONA))
    val ESQUEMA_INICIO = obj("titulo" to TEXTO, "texto" to TEXTO, "gente" to lista(obj("nombre" to TEXTO, "rol" to TEXTO, "afinidad" to ENTERO, "nota" to TEXTO)),
        "prensa" to obj("cabecera" to TEXTO, "titular" to TEXTO, "entradilla" to TEXTO, "otros_titulares" to lista(TEXTO)), "posts" to lista(POST), "mensajes" to lista(DM))
    val ESQUEMA_FOTO = obj("se_ve" to SINO, "goles_favor" to ENTERO, "goles_contra" to ENTERO, "rival" to TEXTO, "minutos" to ENTERO, "goles" to ENTERO,
        "asistencias" to ENTERO, "nota" to NUMERO, "datos_equipo" to lista(obj("dato" to TEXTO, "valor" to TEXTO)), "rendimiento" to TEXTO, "dudas" to TEXTO)

    // ---------------------------------------------------------------- las peticiones

    private fun sistema() = listOf(Peticion.Bloque(REGLAS, cache = true))

    /** Tras un partido: la red y los mensajes (Haiku). */
    fun red(u: Universo, p: Partido, ctx: String) = Peticion(Claude.HAIKU, sistema(), listOf(Peticion.texto("""
$ctx

ACABA DE JUGAR ESTE PARTIDO:
${p.resumen(u.modo)}

Escribe lo que pasa en la red social justo después: entre 8 y 12 posts (afición propia y rival, prensa inventada, algún rival, como mucho un famoso de pasada en tono amable, y alguien de su gente) y entre 2 y 4 mensajes privados para él (de su gente o de alguna chica inventada), que reaccionen a este partido y a lo que ha pasado antes. Usuarios con @, megusta y reposts creíbles para su fama (${u.valorFuera(Fuera.FAMA)}/100).
""".trim())), maxTokens = 3000, esquema = ESQUEMA_RED)

    /** Tras un partido: la portada, las ofertas (si tocan), una decisión (si toca) y lo que cambia (Sonnet). */
    fun prensa(u: Universo, p: Partido, ctx: String) = Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto("""
$ctx

ACABA DE JUGAR ESTE PARTIDO:
${p.resumen(u.modo)}

Escribe:
- La portada del día después en un diario deportivo inventado (cabecera, titular, entradilla de 1-2 frases y 2-3 titulares más pequeños, alguno de fuera del campo).
- Ofertas: solo si encajan de verdad con su momento (lo normal es ninguna; como mucho una o dos). Salario en euros a la semana, prima en euros de una vez, años de contrato.
- Una decisión (hay = true) solo si sale algo jugoso de este partido (unas declaraciones, una fiesta para celebrarlo, un rifirrafe en redes, un mensaje que contestar...), con 2 o 3 opciones cortas; si no, hay = false con los textos vacíos y sin opciones.
- Efectos de este partido en lo de fuera del campo (fama, afición, disciplina, carisma: pequeños, de -5 a +5) y en su gente (por su nombre exacto). Dinero: 0 salvo que haya un motivo (una prima por objetivos, una multa del club por algo de fuera del campo).
- Gente nueva solo si aparece alguien que vaya a importar (inventado).
""".trim())), maxTokens = 3000, esquema = ESQUEMA_PRENSA)

    /** Algo que pasa entre partidos (Sonnet; los automáticos van por lotes, a mitad de precio). [cual]: 1 o 2 de los de esa jornada, o 0 si lo pides tú. */
    fun evento(u: Universo, ctx: String, cual: Int, enLote: Boolean = false): Peticion {
        val enfoque = when (cual) {
            1 -> "Esta vez, algo de su vida personal o de su gente: una chica, una fiesta, una invitación, su familia, su representante, un amigo que pide algo, un rumor."
            2 -> "Esta vez, algo de su imagen y su dinero: la prensa, las redes, una marca, un negocio, una inversión, un evento público, un capricho caro, un lío con la afición."
            else -> "Lo que más pegue ahora mismo con lo que viene pasando (que no repita lo último)."
        }
        return Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto("""
$ctx

ENTRE PARTIDOS (mientras tanto): cuenta UNA cosa que le pasa estos días, fuera del campo. $enfoque
- Título corto y texto de 2-4 frases, contado como una noticia o una escena.
- De 0 a 4 posts y de 0 a 2 mensajes privados que vayan con ello.
- Casi siempre una decisión para él (hay = true) con 2 o 3 opciones cortas y distintas entre sí, con riesgo y gracia; si no hace falta, hay = false con los textos vacíos y sin opciones.
- Ofertas solo si el evento es una oferta (como mucho una).
- Efectos inmediatos (pequeños; lo gordo vendrá con lo que elija). Dinero en euros (positivo o negativo) solo si este hecho lo mueve ya.
- Gente nueva solo si aparece alguien que vaya a importar (inventado).
""".trim())), maxTokens = 2500, esquema = ESQUEMA_EVENTO, cacheHora = enLote)
    }

    /** Lo que pasa después de una decisión suya (Sonnet). */
    fun respuesta(u: Universo, ctx: String, e: Entrada, d: Decision, eleccion: String) = Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto("""
$ctx

LA SITUACIÓN: ${listOf(e.titulo, e.texto).filter { it.isNotBlank() }.joinToString(": ")}
LA DECISIÓN (${d.de.ifBlank { "sin remitente" }}): ${d.texto}
LO QUE HA DECIDIDO ÉL: $eleccion

Cuenta qué pasa por haber decidido eso (texto de 2-4 frases, con consecuencias claras, buenas o malas, y un titular corto), de 1 a 5 posts y de 0 a 2 mensajes que reaccionen, y los efectos: aquí sí pueden ser más grandes (de -10 a +10 en fama, afición, disciplina y carisma; su gente, de -15 a +15 por su nombre exacto; dinero en euros si lo que eligió cuesta o da dinero). Gente nueva solo si aparece alguien que vaya a importar.
""".trim())), maxTokens = 2500, esquema = ESQUEMA_RESPUESTA)

    /** El principio de una carrera: su gente (inventada) y su presentación (Sonnet, una vez). */
    fun inicio(u: Universo) = Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto("""
${contexto(u, emptyList(), emptyList())}

EMPIEZA SU CARRERA EN SHAI. Crea su entorno: de 5 a 7 personas inventadas que vayan a dar juego (familia, representante, un amigo de siempre, un compañero del vestuario, alguna ex o alguna chica, un periodista que le sigue), cada una con su rol, una afinidad inicial (0-100) y una nota corta de cómo es. Y su presentación: un título y un texto de 2-3 frases, una portada (cabecera inventada, titular, entradilla y 2 titulares más), de 4 a 6 posts y 1 o 2 mensajes de bienvenida.
""".trim())), maxTokens = 3000, esquema = ESQUEMA_INICIO)

    /** Un fichaje o un contrato que ha aceptado: lo que dice la red (Haiku). */
    fun oficial(u: Universo, ctx: String, o: Oferta) = Peticion(Claude.HAIKU, sistema(), listOf(Peticion.texto("""
$ctx

ES OFICIAL: ha aceptado ${Oferta.TIPOS[o.tipo]?.lowercase() ?: o.tipo} con ${o.de}: ${o.texto}. Escribe de 5 a 8 posts reaccionando (afición del club nuevo y del viejo, prensa inventada, algún rival) y de 1 a 3 mensajes privados de su gente.
""".trim())), maxTokens = 2000, esquema = ESQUEMA_RED)

    /** Leer una foto de la pantalla de estadísticas del juego (Haiku, que lee imágenes). */
    fun foto(u: Universo, base64: String, tipo: String) = Peticion(Claude.HAIKU, listOf(Peticion.Bloque("""
Lees capturas o fotos de pantallas de EA Sports FC 26 (datos del partido o rendimiento de un jugador) y sacas las cifras tal cual se ven. No inventes nada: lo que no se lea, a 0 o vacío, y dilo en «dudas». Si la imagen no es una pantalla de estadísticas de FC, se_ve = false.
""".trim())), listOf(Peticion.imagen(base64, tipo), Peticion.texto("""
El equipo del jugador es ${u.club}${if (u.modo == Rol.JUGADOR) " y su jugador es ${u.personaje.nombre}" else ""}. Saca de esta imagen: el marcador (goles a favor de ${u.club} y en contra), el rival, y, si salen, los minutos, goles, asistencias y nota de ${if (u.modo == Rol.JUGADOR) u.personaje.nombre else "su equipo"}; los datos del partido que se vean (posesión, tiros, tiros a puerta, precisión de pase, faltas, córners… con el valor de los dos equipos, «nuestro / suyo»), y lo demás del rendimiento en una frase.
""".trim())), maxTokens = 1500, esquema = ESQUEMA_FOTO)

    // ---------------------------------------------------------------- leer lo que contesta

    private fun posts(l: Any?) = l.comoLista().map { it.comoMapa() }.map { m ->
        Post(m["autor"].comoTexto(), m["usuario"].comoTexto().let { if (it.isNotBlank() && !it.startsWith("@")) "@$it" else it }, m["texto"].comoTexto(),
            m["megusta"].comoLong().toInt().coerceAtLeast(0), m["reposts"].comoLong().toInt().coerceAtLeast(0), m["tipo"].comoTexto().ifBlank { "aficion" })
    }.filter { it.texto.isNotBlank() }

    private fun dms(l: Any?) = l.comoLista().map { it.comoMapa() }.map { Dm(it["de"].comoTexto(), it["texto"].comoTexto()) }.filter { it.texto.isNotBlank() }

    private fun prensaDe(m: Map<String, Any?>): Prensa? = Prensa(m["cabecera"].comoTexto(), m["titular"].comoTexto(), m["entradilla"].comoTexto(),
        m["otros_titulares"].comoLista().map { it.comoTexto() }.filter { it.isNotBlank() }).takeIf { it.titular.isNotBlank() }

    private fun ofertas(l: Any?) = l.comoLista().map { it.comoMapa() }.map { m ->
        Oferta(Universo.nuevoId("o"), m["de"].comoTexto(), m["tipo"].comoTexto().ifBlank { "traspaso" }, m["salario_semanal"].comoLong().coerceAtLeast(0),
            m["prima"].comoLong().coerceAtLeast(0), m["anios"].comoLong().toInt().coerceIn(0, 10), m["texto"].comoTexto())
    }.filter { it.de.isNotBlank() }.take(3)

    private fun decision(m: Map<String, Any?>): Decision? {
        if (!m["hay"].comoBool()) return null
        val ops = m["opciones"].comoLista().map { it.comoTexto().trim() }.filter { it.isNotBlank() }.distinct().take(3)
        if (m["texto"].comoTexto().isBlank() || ops.size < 2) return null
        return Decision(Universo.nuevoId("d"), m["de"].comoTexto(), m["texto"].comoTexto(), ops)
    }

    private fun efectos(m: Map<String, Any?>, gente: Any?): Efectos = Efectos(
        m["dinero"].comoLong(), Fuera.TODOS.associate { (k, _) -> k to m[k].comoLong().toInt() }.filterValues { it != 0 },
        m["relaciones"].comoLista().map { it.comoMapa() }.associate { it["nombre"].comoTexto() to it["cambio"].comoLong().toInt() }.filter { it.key.isNotBlank() && it.value != 0 },
        gente.comoLista().map { it.comoMapa() }.map { Persona("", it["nombre"].comoTexto(), it["rol"].comoTexto(), 50, it["nota"].comoTexto()) }.filter { it.nombre.isNotBlank() },
    )

    /** La red y los mensajes. */
    fun leeRed(r: Map<String, Any?>): Pair<List<Post>, List<Dm>> = posts(r["posts"]) to dms(r["mensajes"])

    class LoDePrensa(val prensa: Prensa?, val ofertas: List<Oferta>, val decision: Decision?, val efectos: Efectos)
    fun leePrensa(r: Map<String, Any?>) = LoDePrensa(prensaDe(r["prensa"].comoMapa()), ofertas(r["ofertas"]), decision(r["decision"].comoMapa()), efectos(r["efectos"].comoMapa(), r["gente_nueva"]))

    /** Un evento de entre partidos, ya como entrada (sin aplicar: sale a su hora). */
    fun leeEvento(r: Map<String, Any?>, u: Universo, ahora: Long, visible: Long) = Entrada(
        Universo.nuevoId("e"), u.id, TipoEntrada.EVENTO, ahora, u.paso, r["titulo"].comoTexto(), r["texto"].comoTexto(), null, posts(r["posts"]), dms(r["mensajes"]),
        null, ofertas(r["ofertas"]), decision(r["decision"].comoMapa()), efectos(r["efectos"].comoMapa(), r["gente_nueva"]), visibleDesde = visible, aplicada = false,
    )

    class LoDeDespues(val texto: String, val titular: String, val posts: List<Post>, val dms: List<Dm>, val efectos: Efectos)
    fun leeRespuesta(r: Map<String, Any?>) = LoDeDespues(r["texto"].comoTexto(), r["titular"].comoTexto(), posts(r["posts"]), dms(r["mensajes"]), efectos(r["efectos"].comoMapa(), r["gente_nueva"]))

    class LoDelInicio(val titulo: String, val texto: String, val gente: List<Persona>, val prensa: Prensa?, val posts: List<Post>, val dms: List<Dm>)
    fun leeInicio(r: Map<String, Any?>) = LoDelInicio(r["titulo"].comoTexto(), r["texto"].comoTexto(),
        r["gente"].comoLista().map { it.comoMapa() }.map { Persona(Universo.nuevoId("p"), it["nombre"].comoTexto(), it["rol"].comoTexto(), it["afinidad"].comoLong().toInt().coerceIn(0, 100), it["nota"].comoTexto()) }
            .filter { it.nombre.isNotBlank() }.take(10),
        prensaDe(r["prensa"].comoMapa()), posts(r["posts"]), dms(r["mensajes"]))

    /** Lo leído de la foto, para rellenar la plantilla (que revisas antes de guardar). */
    class LoDeLaFoto(val seVe: Boolean, val golesFavor: Int, val golesContra: Int, val rival: String, val minutos: Int, val goles: Int, val asistencias: Int,
                     val nota: Double, val equipo: Map<String, String>, val rendimiento: String, val dudas: String)
    fun leeFoto(r: Map<String, Any?>) = LoDeLaFoto(r["se_ve"].comoBool(), r["goles_favor"].comoLong().toInt().coerceAtLeast(0), r["goles_contra"].comoLong().toInt().coerceAtLeast(0),
        r["rival"].comoTexto(), r["minutos"].comoLong().toInt().coerceIn(0, 130), r["goles"].comoLong().toInt().coerceAtLeast(0), r["asistencias"].comoLong().toInt().coerceAtLeast(0),
        r["nota"].comoDouble().coerceIn(0.0, 10.0), r["datos_equipo"].comoLista().map { it.comoMapa() }.associate { it["dato"].comoTexto() to it["valor"].comoTexto() }.filter { it.key.isNotBlank() && it.value.isNotBlank() },
        r["rendimiento"].comoTexto(), r["dudas"].comoTexto())
}
