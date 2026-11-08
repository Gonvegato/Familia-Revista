package com.gonzalo.shai

import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoDouble
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto

/**
 * Lo que se le pide a Claude y cómo se lee lo que contesta. Todo en salida estructurada (un esquema JSON por cosa), para que lo que vuelve
 * se pueda leer siempre igual. Haiku 5.5 (barato) para la red, los mensajes y para leer la foto; Sonnet 5.5 para la prensa, las
 * ofertas, las decisiones, lo que pasa entre partidos, la memoria y el cierre de temporada.
 *
 * El prompt de sistema es siempre el mismo (va en caché); lo de tu carrera va detrás, en el mensaje.
 */
object Narrador {
    /** Las reglas de siempre (iguales en todas las peticiones: van en caché). */
    val REGLAS = """
Eres el narrador de Shai, una app que acompaña las carreras que una persona juega en EA Sports FC 26 (modo carrera, de jugador o de míster). Tú escribes todo lo que pasa FUERA del campo: la prensa, la red social, los mensajes privados, las ofertas, la vida, el dinero y la gente. Escribes en español de España, con gracia, mala leche y mucho salseo, como la prensa deportiva y la prensa rosa de verdad.

Reglas que no se rompen nunca:
1. El campo es del jugador. Los resultados, los minutos, los goles, las asistencias, las notas, la media y las cifras del juego son los que él te da: no los cambies ni inventes otros. No inventes lesiones, suplencias, sanciones, alineaciones, resultados, minutos de los goles, cómo fueron las jugadas ni nada que pase dentro del campo o que él tenga que hacer en el juego. Puedes comentar, opinar y exagerar lo que te da, no añadir hechos del campo.
2. Personas reales (futbolistas, entrenadores, periodistas, famosos): solo de pasada y en público, en tono neutro o amable (un saludo, un comentario de un partido, una foto en un evento). Nunca les pongas romances, sexo, escándalos, delitos, insultos, problemas de salud ni frases polémicas. Todo lo íntimo, lo escandaloso y lo de pareja es SIEMPRE con personajes inventados.
3. El protagonista es un mujeriego (a no ser que sus rasgos digan otra cosa): ligues, rumores, fotos con chicas, invitaciones, celos y exparejas, siempre con mujeres inventadas y mayores de edad, a nivel de prensa rosa y de cotilleo. Nada de contenido sexual explícito: se insinúa, no se describe.
4. Clubes y competiciones: los reales se pueden nombrar (son el juego), sin inventarles escándalos institucionales graves. Medios de comunicación, programas, marcas, empresas, discotecas, restaurantes e influencers: inventados (con nombres creíbles), nunca reales.
5. El dinero en euros, con cifras creíbles para su nivel y su club. Las ofertas son propuestas que él decide en la app (si acepta, lo hará en el juego): de clubes reales que encajen con su nivel de AHORA y su momento, o de marcas inventadas.
6. Su trayectoria (lo que él cuenta de su pasado, también premios y títulos reales que diga haber ganado) es su historia: respétala y úsala. Los premios que des tú al cerrar una temporada son siempre de medios inventados.
7. Su edad y cómo se le ve marcan el tono: a un chaval de 18 que rinde por encima de lo esperado se le trata como un terremoto (nuevo fenómeno, comparaciones, presión); a uno de 29 con un buen tramo, como una buena racha de alguien cuyo nivel ya se conoce; con la edad alta, los malos tramos se leen como declive. El alcance va con su nivel de AHORA: los seguidores, los «me gusta», cuánto se habla de él y qué medios le cubren (un canterano, la prensa local y cuatro cuentas de la grada; una estrella, medio mundo). Las cifras de interacción, creíbles para sus seguidores.
8. Coherencia: respeta lo que ya ha pasado y lo que Shai recuerda (su gente, sus relaciones, lo que eligió, lo que dejó sin contestar). Usa a su gente por su nombre. Si algo viene de una decisión suya, que se note.
9. Que todo se entienda sin haber leído nada antes: cada cosa que pasa dice quién, dónde (qué medio, qué programa, qué sitio, qué red), qué se dijo (con la cita literal cuando la hay), por qué importa y qué se juega él. Nada de frases sueltas sin contexto.
10. Récords: los suyos son los de su carrera en Shai (con las cifras que él mete). No digas nunca que bate un récord del mundo real (no lo puedes comprobar); como mucho «de los más jóvenes en hacerlo» o comparaciones vagas, en tono periodístico.
11. La red tiene vida: posts con opinión, chiste o polémica, de 120 a 280 caracteres, que reaccionan a lo que ha pasado de verdad; respuestas debajo de los más gordos; citas con comentario.
""".trim()

    // ---------------------------------------------------------------- el contexto de la carrera

    /** Lo que Claude tiene que saber de la carrera (va en el mensaje, no en el sistema: cambia cada vez). */
    fun contexto(u: Universo, recientes: List<Entrada>, partidos: List<Partido>, cuantas: Int = 8): String = buildString {
        val pj = u.personaje
        appendLine("CARRERA (${u.modo.nombre.lowercase()}):")
        appendLine("- ${if (u.modo == Rol.MISTER) "Entrenador" else "Jugador"}: ${pj.nombre}" + (if (pj.apodo.isNotBlank()) " («${pj.apodo}»)" else "") +
            ", ${pj.edad} años" + (if (pj.nacionalidad.isNotBlank()) ", ${pj.nacionalidad}" else "") +
            (if (u.modo == Rol.JUGADOR && pj.posicion.isNotBlank()) ", ${pj.posicion}" else "") + (if (pj.dorsal > 0) ", dorsal ${pj.dorsal}" else "") +
            (if (pj.media > 0) ", media ${pj.media} en el juego" else ""))
        if (pj.rasgos.isNotBlank()) appendLine("- Cómo es: ${pj.rasgos}")
        if (u.trayectoria.isNotBlank()) appendLine("- Su trayectoria (lo que él cuenta de su pasado; respétalo): ${u.trayectoria}")
        val nv = u.nivelActual
        appendLine("- Nivel de AHORA: ${nv.nombre} (${nv.explica.lowercase()})" + (u.nivelDe?.takeIf { it != nv }?.let { "; empezó como ${it.nombre.lowercase()}" } ?: "") +
            ". Seguidores en la red: ${Alcance.texto(Alcance.seguidores(u))}; un post normal sobre él tiene unos ${Alcance.texto(Alcance.megustaTipico(u))} me gusta. Le cubren: ${nv.medios}.")
        appendLine("- Cómo se le está viendo (para el tono): ${Percepcion.de(u, partidos.filter { it.temporada == u.temporada }.ifEmpty { partidos })}")
        appendLine("- Club: ${u.club}" + (if (u.liga.isNotBlank()) " (${u.liga})" else "") + (if (u.temporada.isNotBlank()) ", temporada ${u.temporada}" else "") +
            ", jornada ${u.paso} de su carrera en Shai")
        val c = u.contrato
        appendLine(if (c == null) "- SIN CONTRATO: es agente libre (le llegan ofertas; no cobra sueldo de ningún club)" + (if (u.patrocinio > 0) ", cobra ${Formato.euros(u.patrocinio)} a la semana de patrocinios" else "") + "."
            else "- Su contrato (el de Shai, no el del juego): con ${c.club}${if (c.nota.isNotBlank()) " (${c.nota.lowercase()})" else ""}, ${Formato.euros(c.sueldo)} a la semana hasta la temporada ${c.hasta} " +
                "(le quedan ${Contratos.quedan(c, u.temporada)} temporadas contando esta), cláusula de ${Formato.euros(c.clausula)}" +
                (if (c.porGol > 0) ", ${Formato.euros(c.porGol)} por gol" else "") + (if (c.objetivos.isNotBlank()) ". Objetivos: ${c.objetivos}" else "") +
                (if (u.patrocinio > 0) ". Patrocinios: ${Formato.euros(u.patrocinio)} a la semana" else "") + ".")
        appendLine("- Dinero: ${Formato.euros(u.dinero)}; patrimonio: ${Formato.euros(u.patrimonio)}")
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
        val r = Hitos.records(u, partidos)
        if (partidos.isNotEmpty()) appendLine("- Sus récords en Shai: ${r.partidos} partidos" + (if (u.modo == Rol.JUGADOR) ", ${r.goles} goles, ${r.hatTricks} hat-tricks, racha más larga marcando: ${r.rachaMarcando}" else ", ${r.victorias} victorias") +
            ", racha invicto más larga: ${r.rachaInvicto}" + (r.mejorTemporada?.let { ", mejor temporada: ${it.first} (${it.second}${if (u.modo == Rol.JUGADOR) " goles" else " victorias"})" } ?: "") +
            (if (r.titulos.isNotEmpty()) ", títulos: ${r.titulos.joinToString(", ")}" else ""))
        val recien = u.hitos.filter { it.paso == u.paso }
        if (recien.isNotEmpty()) appendLine("HITOS RECIÉN CONSEGUIDOS (celébralos con repercusión según su edad y su nivel; son de su carrera en Shai, no récords del mundo real):\n" +
            recien.joinToString("\n") { "- ${it.titulo}: ${it.texto}" + if (it.precoz) " (PRECOZ, con ${it.edad} años)" else "" })
        if (u.memoria.isNotBlank()) appendLine("LO QUE SHAI RECUERDA (tramas abiertas, relaciones, hechos clave):\n${u.memoria}")
        val abiertas = recientes.filter { it.decision?.abierta == true }
        appendLine("DECISIONES PENDIENTES: ${abiertas.size}" + if (abiertas.size >= MAX_ABIERTAS) " (ya tiene bastantes: no le pongas ninguna más; hay = false)" else "")
        val antes = recientes.take(cuantas)
        if (antes.isNotEmpty()) {
            appendLine("LO ÚLTIMO QUE HA PASADO (de lo más nuevo a lo más viejo):")
            antes.forEach { e -> appendLine("- " + resumen(e, u.modo)) }
        }
    }.trim()

    /** Con cuántas decisiones abiertas ya no se le ponen más. */
    const val MAX_ABIERTAS = 2

    /** Una línea de algo que ya pasó. */
    fun resumen(e: Entrada, modo: Rol): String = when {
        e.partido != null -> "Partido: " + e.partido.resumen(modo) + (e.prensa?.let { " · Portada: «${it.titular}»" } ?: "") + decisionResumen(e.decision)
        else -> listOf(e.titulo, e.texto.take(240)).filter { it.isNotBlank() }.joinToString(": ") + decisionResumen(e.decision) +
            e.ofertas.filter { it.estado != Oferta.ABIERTA }.joinToString("") { " · Oferta de ${it.de}: ${it.estado}" }
    }

    private fun decisionResumen(d: Decision?): String = when {
        d == null -> ""
        d.abierta -> " · (decisión pendiente: ${listOf(d.titulo, d.texto).filter { it.isNotBlank() }.joinToString(": ").take(140)})"
        d.caducada -> " · No contestó a «${d.titulo.ifBlank { d.texto }.take(80)}»" + (if (d.consecuencia.isNotBlank()) " → ${d.consecuencia.take(160)}" else "")
        else -> " · Eligió: «${d.elegida}»" + (if (d.consecuencia.isNotBlank()) " → ${d.consecuencia.take(160)}" else "")
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

    private val TIPO_POST = enumerado("aficion", "prensa", "rival", "famoso", "entorno")
    private val RESPUESTA_POST = obj("autor" to TEXTO, "usuario" to TEXTO, "texto" to TEXTO, "megusta" to ENTERO, "tipo" to TIPO_POST)
    private val CITA = obj("autor" to TEXTO, "usuario" to TEXTO, "texto" to TEXTO)
    private val POST = obj("autor" to TEXTO, "usuario" to TEXTO, "texto" to TEXTO, "megusta" to ENTERO, "reposts" to ENTERO, "tipo" to TIPO_POST,
        "respuestas" to lista(RESPUESTA_POST), "cita" to CITA)
    private val TENDENCIA = obj("hashtag" to TEXTO, "posts" to ENTERO)
    private val DM = obj("chat" to TEXTO, "de" to TEXTO, "texto" to TEXTO)
    private val PERSONA = obj("nombre" to TEXTO, "rol" to TEXTO, "nota" to TEXTO)
    private val EFECTOS = obj("dinero" to ENTERO, "fama" to ENTERO, "aficion" to ENTERO, "disciplina" to ENTERO, "carisma" to ENTERO,
        "relaciones" to lista(obj("nombre" to TEXTO, "cambio" to ENTERO)))
    private val DECISION = obj("hay" to SINO, "de" to TEXTO, "titulo" to TEXTO, "contexto" to TEXTO, "en_juego" to TEXTO, "pregunta" to TEXTO, "opciones" to lista(TEXTO))
    private val OFERTA = obj("de" to TEXTO, "tipo" to enumerado("traspaso", "cesion", "renovacion", "patrocinio", "banquillo"),
        "salario_semanal" to ENTERO, "prima" to ENTERO, "anios" to ENTERO, "texto" to TEXTO)
    private val PRENSA = obj("cabecera" to TEXTO, "titular" to TEXTO, "entradilla" to TEXTO, "articulo" to TEXTO,
        "secundarios" to lista(obj("titular" to TEXTO, "entradilla" to TEXTO)))

    val ESQUEMA_RED = obj("tendencia" to TENDENCIA, "posts" to lista(POST), "mensajes" to lista(DM))
    val ESQUEMA_PRENSA = obj("prensa" to PRENSA, "ofertas" to lista(OFERTA), "decision" to DECISION,
        "sin_contestar" to lista(obj("n" to ENTERO, "consecuencia" to TEXTO, "efectos" to EFECTOS)), "efectos" to EFECTOS, "gente_nueva" to lista(PERSONA))
    val ESQUEMA_EVENTO = obj("titulo" to TEXTO, "texto" to TEXTO, "tendencia" to TENDENCIA, "posts" to lista(POST), "mensajes" to lista(DM), "decision" to DECISION,
        "ofertas" to lista(OFERTA), "efectos" to EFECTOS, "gente_nueva" to lista(PERSONA))
    val ESQUEMA_RESPUESTA = obj("texto" to TEXTO, "titular" to TEXTO, "tendencia" to TENDENCIA, "posts" to lista(POST), "mensajes" to lista(DM), "efectos" to EFECTOS, "gente_nueva" to lista(PERSONA))
    val ESQUEMA_INICIO = obj("titulo" to TEXTO, "texto" to TEXTO, "gente" to lista(obj("nombre" to TEXTO, "rol" to TEXTO, "afinidad" to ENTERO, "nota" to TEXTO)),
        "prensa" to PRENSA, "tendencia" to TENDENCIA, "posts" to lista(POST), "mensajes" to lista(DM))
    val ESQUEMA_FOTO = obj("se_ve" to SINO, "goles_favor" to ENTERO, "goles_contra" to ENTERO, "rival" to TEXTO, "minutos" to ENTERO, "goles" to ENTERO,
        "asistencias" to ENTERO, "nota" to NUMERO, "datos_equipo" to lista(obj("dato" to TEXTO, "valor" to TEXTO)), "rendimiento" to TEXTO, "dudas" to TEXTO)
    val ESQUEMA_MEMORIA = obj("memoria" to TEXTO)
    val ESQUEMA_TEMPORADA = obj("titulo" to TEXTO, "balance" to TEXTO, "premios" to lista(obj("premio" to TEXTO, "medio" to TEXTO, "ganado" to SINO, "texto" to TEXTO)),
        "prensa" to PRENSA, "tendencia" to TENDENCIA, "posts" to lista(POST), "mensajes" to lista(DM),
        "vacaciones" to obj("titulo" to TEXTO, "texto" to TEXTO, "decision" to DECISION), "ofertas" to lista(OFERTA), "efectos" to EFECTOS, "gente_nueva" to lista(PERSONA))

    // ---------------------------------------------------------------- las peticiones

    private fun sistema() = listOf(Peticion.Bloque(REGLAS, cache = true))

    /** Cómo se escribe la red (va en todas las peticiones que la llevan). */
    private fun comoRed(u: Universo, cuantos: String) = """
LA RED (tipo X): $cuantos posts, de 120 a 280 caracteres cada uno, con vida: opinión, chiste, polémica, datos del partido que él te ha dado, comparaciones, memes en texto. Mezcla afición propia, afición rival, prensa inventada, cuentas de cotilleo inventadas y alguien de su gente (como mucho un famoso real, de pasada y amable). Los 2 o 3 más gordos llevan de 2 a 4 respuestas debajo (a favor, en contra, cachondeo), y 1 o 2 posts citan otro post o un titular con su comentario (en «cita»: el autor, su @ y el texto citado; si no cita nada, «cita» con los tres textos vacíos). «tendencia»: el hashtag del día sobre él o lo que ha pasado y cuántos posts lleva. «me gusta» y reposts creíbles para sus seguidores (${Alcance.texto(Alcance.seguidores(u))}): un post normal, unos ${Alcance.texto(Alcance.megustaTipico(u))}; los gordos, varias veces más; las respuestas, bastantes menos. Usuarios con su @.
""".trim()

    private val COMO_MENSAJES = """
MENSAJES PRIVADOS: conversaciones de verdad, no frases sueltas: cada una con 2 a 5 mensajes seguidos, en «chat» el nombre de la conversación (la persona, o un grupo como «Los del barrio» o «Familia» con varias personas que hablan entre ellas) y en «de» quién escribe cada mensaje. Con contexto: que se sepa de qué hablan y por qué. No escribas mensajes de él: solo de los demás.
""".trim()

    private val COMO_DECISION = """
UNA DECISIÓN (si hay = true): «titulo» corto; «contexto» de 4 a 7 frases que se entienda solo (quién, dónde —qué medio, programa, sitio o red—, qué se dijo con la cita literal, por qué es un problema o una oportunidad); «en_juego» (qué se juega: su imagen, una relación, dinero, la afición, el vestuario…); «pregunta»; y 3 «opciones» concretas y distintas entre sí (qué hace exactamente, no «sí» o «no»), cada una con su riesgo. Si no toca, hay = false con todo vacío.
""".trim()

    /** Tras un partido: la red y los mensajes (Haiku). */
    fun red(u: Universo, p: Partido, ctx: String) = Peticion(Claude.HAIKU, sistema(), listOf(Peticion.texto("""
$ctx

ACABA DE JUGAR ESTE PARTIDO:
${p.resumen(u.modo)}

Escribe lo que pasa en la red justo después de este partido, que se note su repercusión según su nivel y lo que ha pasado antes.
${comoRed(u, "De 10 a 14")}
$COMO_MENSAJES Escribe de 2 a 3 conversaciones (su gente, alguna chica inventada, un grupo).
""".trim())), maxTokens = 7000, esquema = ESQUEMA_RED)

    /** Tras un partido: la portada con su noticia, las ofertas (si tocan), una decisión (si toca), lo que no contestó y lo que cambia (Sonnet). */
    fun prensa(u: Universo, p: Partido, ctx: String, sinContestar: List<Entrada> = emptyList()) = Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto(buildString {
        appendLine(ctx); appendLine()
        appendLine("ACABA DE JUGAR ESTE PARTIDO:"); appendLine(p.resumen(u.modo)); appendLine()
        appendLine("""
Escribe:
- La portada del día después en un diario deportivo inventado que le cubra según su nivel (local o nacional; internacional si es una estrella): cabecera, titular, entradilla (1-2 frases) y la noticia entera en «articulo» (3 párrafos separados por una línea en blanco, unas 220-300 palabras: qué hizo con las cifras que te ha dado, qué significa para él y su club, qué se dice en la grada y en el vestuario, y algo de fuera del campo). Y 2 o 3 «secundarios» (titular y entradilla de 1-2 frases), alguno de fuera del campo.
- Ofertas: solo si encajan de verdad con su nivel de ahora, su rendimiento y su momento (lo normal es ninguna; como mucho una o dos). Salario en euros a la semana, prima en euros de una vez, años de contrato; en «texto», por qué le quieren y qué le ofrecen de verdad (minutos, proyecto, escaparate). ${Rangos.texto(u.nivelActual)}
- $COMO_DECISION Que salga de este partido o de lo que ya viene pasando (unas declaraciones, una fiesta para celebrarlo, un rifirrafe en redes, un mensaje que contestar...).
- Efectos de este partido en lo de fuera del campo (fama, afición, disciplina, carisma: pequeños, de -5 a +5) y en su gente (por su nombre exacto). Dinero: 0 salvo que haya un motivo (una prima por objetivos, una multa del club por algo de fuera del campo).
- Gente nueva solo si aparece alguien que vaya a importar (inventado).
""".trim())
        if (sinContestar.isNotEmpty()) {
            appendLine()
            appendLine("NO CONTESTÓ A ESTO antes de este partido (dejarlo pasar también tiene consecuencias): en «sin_contestar», por cada una, su número, qué pasó por no contestar (2-4 frases que lo expliquen: quién se lo tomó mal o bien, qué salió en la prensa o en la red) y sus efectos (pequeños, de -6 a +6; su gente, de -10 a +10).")
            sinContestar.forEachIndexed { i, e ->
                val d = e.decision ?: return@forEachIndexed
                appendLine("${i + 1}. «${d.titulo.ifBlank { e.titulo }}» (de ${d.de.ifBlank { "nadie en concreto" }}): ${d.contexto.ifBlank { e.texto }.take(500)} · Le preguntaban: ${d.texto}")
            }
        } else { appendLine(); appendLine("«sin_contestar»: vacío.") }
    }.trim())), maxTokens = 6000, esquema = ESQUEMA_PRENSA)

    /** Algo que pasa entre partidos (Sonnet; los automáticos van por lotes, a mitad de precio). [cual]: 1 o 2 de los de esa jornada, o 0 si lo pides tú. */
    fun evento(u: Universo, ctx: String, cual: Int, enLote: Boolean = false): Peticion {
        val enfoque = when (cual) {
            1 -> "Esta vez, algo de su vida personal o de su gente: una chica, una fiesta, una invitación, su familia, su representante, un amigo que pide algo, un rumor."
            2 -> "Esta vez, algo de su imagen y su dinero: la prensa, las redes, una marca, un negocio, una inversión, un evento público, un capricho caro, un lío con la afición."
            else -> "Lo que más pegue ahora mismo con lo que viene pasando (que no repita lo último)."
        }
        return Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto("""
$ctx

ENTRE PARTIDOS (mientras tanto): cuenta UNA cosa que le pasa estos días, fuera del campo. $enfoque Que encaje con su nivel de ahora (a un canterano le pasan cosas de barrio y de la cantera; a una estrella, de portada internacional).
- «titulo» corto y «texto» de 4 a 7 frases, contado como una noticia o una escena que se entiende sola: quién, dónde (qué medio, programa, local, red), qué se dijo (cita literal) y por qué importa.
- ${comoRed(u, "De 3 a 6")}
- $COMO_MENSAJES Una o dos conversaciones que vayan con ello.
- $COMO_DECISION Casi siempre una (salvo que ya tenga ${MAX_ABIERTAS} o más pendientes).
- Ofertas solo si el evento es una oferta (como mucho una). ${Rangos.texto(u.nivelActual)}
- Efectos inmediatos (pequeños; lo gordo vendrá con lo que elija). Dinero en euros (positivo o negativo) solo si este hecho lo mueve ya.
- Gente nueva solo si aparece alguien que vaya a importar (inventado).
""".trim())), maxTokens = 5000, esquema = ESQUEMA_EVENTO, cacheHora = enLote)
    }

    /** Lo que pasa después de una decisión suya (Sonnet). */
    fun respuesta(u: Universo, ctx: String, e: Entrada, d: Decision, eleccion: String) = Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto("""
$ctx

LA SITUACIÓN: ${listOf(d.titulo.ifBlank { e.titulo }, d.contexto.ifBlank { e.texto }).filter { it.isNotBlank() }.joinToString(": ")}
LO QUE SE JUEGA: ${d.enJuego.ifBlank { "—" }}
LA DECISIÓN (${d.de.ifBlank { "sin remitente" }}): ${d.texto}
LO QUE HA DECIDIDO ÉL: $eleccion

Cuenta qué pasa por haber decidido eso: «texto» de 4 a 6 frases con consecuencias claras, buenas o malas, explicadas (quién reacciona, dónde sale, qué se dice), y un «titular» corto.
${comoRed(u, "De 3 a 6")}
$COMO_MENSAJES Una o dos conversaciones que reaccionen.
Efectos: aquí sí pueden ser más grandes (de -10 a +10 en fama, afición, disciplina y carisma; su gente, de -15 a +15 por su nombre exacto; dinero en euros si lo que eligió cuesta o da dinero). Gente nueva solo si aparece alguien que vaya a importar.
""".trim())), maxTokens = 5000, esquema = ESQUEMA_RESPUESTA)

    /** El principio de una carrera: su gente (inventada) y su presentación (Sonnet, una vez). */
    fun inicio(u: Universo) = Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto("""
${contexto(u, emptyList(), emptyList())}

EMPIEZA SU CARRERA EN SHAI. Crea su entorno: de 5 a 7 personas inventadas que vayan a dar juego (familia, representante, un amigo de siempre, un compañero del vestuario, alguna ex o alguna chica, un periodista que le sigue), cada una con su rol, una afinidad inicial (0-100) y una nota corta de cómo es; que encaje con su trayectoria y su nivel. Y su presentación: un título y un texto de 3-5 frases, una portada (cabecera inventada, titular, entradilla, la noticia en «articulo» de 2-3 párrafos y 2 secundarios), la red (${comoRed(u, "de 5 a 8")}) y 1 o 2 conversaciones de bienvenida ($COMO_MENSAJES).
""".trim())), maxTokens = 6000, esquema = ESQUEMA_INICIO)

    /** Un fichaje o un contrato que ha aceptado: lo que dice la red (Haiku). */
    fun oficial(u: Universo, ctx: String, o: Oferta) = Peticion(Claude.HAIKU, sistema(), listOf(Peticion.texto("""
$ctx

ES OFICIAL: ha aceptado ${Oferta.TIPOS[o.tipo]?.lowercase() ?: o.tipo} con ${o.de}: ${o.texto}.
${comoRed(u, "De 6 a 10")} (afición del club nuevo y del viejo, prensa inventada, algún rival).
$COMO_MENSAJES De 1 a 3 conversaciones de su gente.
""".trim())), maxTokens = 5000, esquema = ESQUEMA_RED)

    /** Lo que Shai recuerda: un resumen de las tramas abiertas, las relaciones y los hechos clave (Sonnet, corto). */
    fun memoria(u: Universo, ctx: String) = Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto("""
$ctx

RESUME LO QUE HAY QUE RECORDAR de esta carrera para seguir contándola con coherencia (lo de antes de las últimas cosas también: parte de lo que ya recordabas). En «memoria», de 120 a 220 palabras, en frases cortas y por apartados en líneas: «Tramas abiertas:», «Su gente:» (cómo está con cada uno que importe), «Lo que decidió y lo que dejó pasar:», «Hechos clave:». Solo lo que ha pasado de verdad: no inventes nada nuevo.
""".trim())), maxTokens = 900, esquema = ESQUEMA_MEMORIA)

    /** El cierre de la temporada: balance, premios inventados, vacaciones y mercado de verano (Sonnet). */
    fun temporada(u: Universo, ctx: String, cifras: Linea, partidos: List<Partido>) = Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto("""
$ctx

CIERRA LA TEMPORADA ${u.temporada.ifBlank { "actual" }}: ${cifras.jugados} partidos (${cifras.victorias}V ${cifras.empates}E ${cifras.derrotas}D)${if (u.modo == Rol.JUGADOR) ", ${cifras.goles} goles, ${cifras.asistencias} asistencias, ${cifras.minutos} minutos" + (if (cifras.notaMedia > 0) ", nota media ${Formato.decimal(cifras.notaMedia)}" else "") else ", ${cifras.golesFavor} goles a favor y ${cifras.golesContra} en contra"}.
Sus partidos: ${partidos.takeLast(40).joinToString("; ") { "${it.rival} ${it.resultado}" + (if (u.modo == Rol.JUGADOR && it.goles > 0) " (${it.goles} g)" else "") }}

Escribe:
- «titulo» y «balance»: 3 párrafos (unas 250-320 palabras) con lo que fue su temporada dentro (con SUS cifras, sin inventar otras) y fuera del campo (lo que pasó con su gente, sus líos, su dinero).
- «premios»: de 2 a 4 premios de fin de temporada de medios inventados (de verdad y de cachondeo, según su nivel: un canterano, el de la peña o la revelación del diario local; una estrella, los grandes de la prensa nacional), cada uno con su medio, si lo gana o se queda a las puertas, y 1-2 frases de por qué.
- La portada del cierre (como siempre, con «articulo») y la red (${comoRed(u, "de 6 a 10")}).
- $COMO_MENSAJES De 1 a 3 conversaciones (las vacaciones, el balance, el verano).
- «vacaciones»: lo que pasa en sus vacaciones (dónde, con quién, qué sale en la prensa rosa o en la red; 4-6 frases) y una decisión ($COMO_DECISION).
- ${u.contrato?.let { c -> if (Contratos.quedan(c, u.temporada) <= 1) "SU CONTRATO CON ${c.club.uppercase()} ACABA CON ESTA TEMPORADA y no ha renovado: queda libre; que el verano y las ofertas lo reflejen." else "Su contrato sigue: le quedan ${Contratos.quedan(c, u.temporada) - 1} temporadas más." } ?: "Es agente libre: necesita club."}
- «ofertas»: el mercado de verano: de 1 a 3 ofertas que encajen con su nivel de ahora y su temporada (clubes reales de su nivel o marcas inventadas). ${Rangos.texto(u.nivelActual)}
- Efectos y gente nueva, como siempre.
""".trim())), maxTokens = 7000, esquema = ESQUEMA_TEMPORADA)

    /** Leer una foto de la pantalla de estadísticas del juego (Haiku, que lee imágenes). */
    fun foto(u: Universo, base64: String, tipo: String) = Peticion(Claude.HAIKU, listOf(Peticion.Bloque("""
Lees capturas o fotos de pantallas de EA Sports FC 26 (datos del partido o rendimiento de un jugador) y sacas las cifras tal cual se ven. No inventes nada: lo que no se lea, a 0 o vacío, y dilo en «dudas». Si la imagen no es una pantalla de estadísticas de FC, se_ve = false.
""".trim())), listOf(Peticion.imagen(base64, tipo), Peticion.texto("""
El equipo del jugador es ${u.club}${if (u.modo == Rol.JUGADOR) " y su jugador es ${u.personaje.nombre}" else ""}. Saca de esta imagen: el marcador (goles a favor de ${u.club} y en contra), el rival, y, si salen, los minutos, goles, asistencias y nota de ${if (u.modo == Rol.JUGADOR) u.personaje.nombre else "su equipo"}; los datos del partido que se vean (posesión, tiros, tiros a puerta, precisión de pase, faltas, córners… con el valor de los dos equipos, «nuestro / suyo»), y lo demás del rendimiento en una frase.
""".trim())), maxTokens = 1500, esquema = ESQUEMA_FOTO)

    // ---------------------------------------------------------------- leer lo que contesta

    private fun usuario(t: String) = t.trim().let { if (it.isNotBlank() && !it.startsWith("@")) "@$it" else it }

    private fun posts(l: Any?): List<Post> = l.comoLista().map { it.comoMapa() }.map { m ->
        val ci = m["cita"].comoMapa().takeIf { it["texto"].comoTexto().isNotBlank() }?.let { Post(it["autor"].comoTexto(), usuario(it["usuario"].comoTexto()), it["texto"].comoTexto()) }
        Post(m["autor"].comoTexto(), usuario(m["usuario"].comoTexto()), m["texto"].comoTexto(),
            m["megusta"].comoLong().coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), m["reposts"].comoLong().coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), m["tipo"].comoTexto().ifBlank { "aficion" },
            m["respuestas"].comoLista().map { it.comoMapa() }.map { r ->
                Post(r["autor"].comoTexto(), usuario(r["usuario"].comoTexto()), r["texto"].comoTexto(), r["megusta"].comoLong().coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), 0, r["tipo"].comoTexto().ifBlank { "aficion" })
            }.filter { it.texto.isNotBlank() }.take(5), ci)
    }.filter { it.texto.isNotBlank() }

    private fun dms(l: Any?) = l.comoLista().map { it.comoMapa() }.map { Dm(it["de"].comoTexto(), it["texto"].comoTexto(), chat = it["chat"].comoTexto()) }.filter { it.texto.isNotBlank() }

    private fun tendencia(m: Map<String, Any?>): Pair<String, Int> {
        val h = m["hashtag"].comoTexto().trim().let { if (it.isNotBlank() && !it.startsWith("#")) "#$it" else it }
        return h to m["posts"].comoLong().coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun prensaDe(m: Map<String, Any?>): Prensa? {
        val sec = m["secundarios"].comoLista().map { it.comoMapa() }.filter { it["titular"].comoTexto().isNotBlank() }
        // (las respuestas viejas traían «otros_titulares», sin entradilla)
        val otros = sec.map { it["titular"].comoTexto() }.ifEmpty { m["otros_titulares"].comoLista().map { it.comoTexto() }.filter { it.isNotBlank() } }
        return Prensa(m["cabecera"].comoTexto(), m["titular"].comoTexto(), m["entradilla"].comoTexto(), otros, m["articulo"].comoTexto().trim(),
            sec.map { it["entradilla"].comoTexto() }).takeIf { it.titular.isNotBlank() }
    }

    /** Las ofertas, con el sueldo y la prima dentro de lo que pide su nivel de ahora ([Rangos]). */
    private fun ofertas(l: Any?, n: Nivel?) = l.comoLista().map { it.comoMapa() }.map { m ->
        Oferta(Universo.nuevoId("o"), m["de"].comoTexto(), m["tipo"].comoTexto().ifBlank { "traspaso" }, m["salario_semanal"].comoLong().coerceAtLeast(0),
            m["prima"].comoLong().coerceAtLeast(0), m["anios"].comoLong().toInt().coerceIn(0, 10), m["texto"].comoTexto()).let { if (n != null) Rangos.recorta(it, n) else it }
    }.filter { it.de.isNotBlank() }.take(3)

    fun decision(m: Map<String, Any?>): Decision? {
        if (!m["hay"].comoBool()) return null
        val ops = m["opciones"].comoLista().map { it.comoTexto().trim() }.filter { it.isNotBlank() }.distinct().take(3)
        // (las respuestas viejas traían la pregunta en «texto»)
        val pregunta = m["pregunta"].comoTexto().ifBlank { m["texto"].comoTexto() }
        if (pregunta.isBlank() || ops.size < 2) return null
        return Decision(Universo.nuevoId("d"), m["de"].comoTexto(), pregunta, ops, titulo = m["titulo"].comoTexto(), contexto = m["contexto"].comoTexto(), enJuego = m["en_juego"].comoTexto())
    }

    fun efectos(m: Map<String, Any?>, gente: Any? = null): Efectos = Efectos(
        m["dinero"].comoLong(), Fuera.TODOS.associate { (k, _) -> k to m[k].comoLong().toInt() }.filterValues { it != 0 },
        m["relaciones"].comoLista().map { it.comoMapa() }.associate { it["nombre"].comoTexto() to it["cambio"].comoLong().toInt() }.filter { it.key.isNotBlank() && it.value != 0 },
        gente.comoLista().map { it.comoMapa() }.map { Persona("", it["nombre"].comoTexto(), it["rol"].comoTexto(), 50, it["nota"].comoTexto()) }.filter { it.nombre.isNotBlank() },
    )

    /** La red y los mensajes. */
    class LoDeLaRed(val posts: List<Post>, val dms: List<Dm>, val tendencia: String, val tendenciaPosts: Int)
    fun leeRed(r: Map<String, Any?>): LoDeLaRed { val (h, n) = tendencia(r["tendencia"].comoMapa()); return LoDeLaRed(posts(r["posts"]), dms(r["mensajes"]), h, n) }

    class SinContestar(val n: Int, val consecuencia: String, val efectos: Efectos)
    class LoDePrensa(val prensa: Prensa?, val ofertas: List<Oferta>, val decision: Decision?, val efectos: Efectos, val sinContestar: List<SinContestar> = emptyList())
    fun leePrensa(r: Map<String, Any?>, n: Nivel? = null) = LoDePrensa(prensaDe(r["prensa"].comoMapa()), ofertas(r["ofertas"], n), decision(r["decision"].comoMapa()), efectos(r["efectos"].comoMapa(), r["gente_nueva"]),
        r["sin_contestar"].comoLista().map { it.comoMapa() }.map { SinContestar(it["n"].comoLong().toInt(), it["consecuencia"].comoTexto(), efectos(it["efectos"].comoMapa())) }.filter { it.consecuencia.isNotBlank() })

    /** Un evento de entre partidos, ya como entrada (sin aplicar: sale a su hora). */
    fun leeEvento(r: Map<String, Any?>, u: Universo, ahora: Long, visible: Long): Entrada {
        val (h, n) = tendencia(r["tendencia"].comoMapa())
        return Entrada(
            Universo.nuevoId("e"), u.id, TipoEntrada.EVENTO, ahora, u.paso, r["titulo"].comoTexto(), r["texto"].comoTexto(), null, posts(r["posts"]), dms(r["mensajes"]),
            null, ofertas(r["ofertas"], u.nivelActual), decision(r["decision"].comoMapa()), efectos(r["efectos"].comoMapa(), r["gente_nueva"]), visibleDesde = visible, aplicada = false,
            tendencia = h, tendenciaPosts = n,
        )
    }

    class LoDeDespues(val texto: String, val titular: String, val posts: List<Post>, val dms: List<Dm>, val efectos: Efectos, val tendencia: String = "", val tendenciaPosts: Int = 0)
    fun leeRespuesta(r: Map<String, Any?>): LoDeDespues {
        val (h, n) = tendencia(r["tendencia"].comoMapa())
        return LoDeDespues(r["texto"].comoTexto(), r["titular"].comoTexto(), posts(r["posts"]), dms(r["mensajes"]), efectos(r["efectos"].comoMapa(), r["gente_nueva"]), h, n)
    }

    class LoDelInicio(val titulo: String, val texto: String, val gente: List<Persona>, val prensa: Prensa?, val posts: List<Post>, val dms: List<Dm>, val tendencia: String = "", val tendenciaPosts: Int = 0)
    fun leeInicio(r: Map<String, Any?>): LoDelInicio {
        val (h, n) = tendencia(r["tendencia"].comoMapa())
        return LoDelInicio(r["titulo"].comoTexto(), r["texto"].comoTexto(),
            r["gente"].comoLista().map { it.comoMapa() }.map { Persona(Universo.nuevoId("p"), it["nombre"].comoTexto(), it["rol"].comoTexto(), it["afinidad"].comoLong().toInt().coerceIn(0, 100), it["nota"].comoTexto()) }
                .filter { it.nombre.isNotBlank() }.take(10),
            prensaDe(r["prensa"].comoMapa()), posts(r["posts"]), dms(r["mensajes"]), h, n)
    }

    fun leeMemoria(r: Map<String, Any?>): String = r["memoria"].comoTexto().trim().take(3000)

    class LoDeLaTemporada(val titulo: String, val balance: String, val premios: List<Premio>, val prensa: Prensa?, val posts: List<Post>, val dms: List<Dm>,
                          val tendencia: String, val tendenciaPosts: Int, val vacacionesTitulo: String, val vacacionesTexto: String, val vacacionesDecision: Decision?,
                          val ofertas: List<Oferta>, val efectos: Efectos)
    fun leeTemporada(r: Map<String, Any?>, nivel: Nivel? = null): LoDeLaTemporada {
        val (h, n) = tendencia(r["tendencia"].comoMapa())
        val v = r["vacaciones"].comoMapa()
        return LoDeLaTemporada(r["titulo"].comoTexto(), r["balance"].comoTexto().trim(),
            r["premios"].comoLista().map { it.comoMapa() }.map { Premio(it["premio"].comoTexto(), it["medio"].comoTexto(), it["ganado"].comoBool(), it["texto"].comoTexto()) }.filter { it.nombre.isNotBlank() }.take(5),
            prensaDe(r["prensa"].comoMapa()), posts(r["posts"]), dms(r["mensajes"]), h, n, v["titulo"].comoTexto(), v["texto"].comoTexto(), decision(v["decision"].comoMapa()),
            ofertas(r["ofertas"], nivel), efectos(r["efectos"].comoMapa(), r["gente_nueva"]))
    }

    /** Lo leído de la foto, para rellenar la plantilla (que revisas antes de guardar). */
    class LoDeLaFoto(val seVe: Boolean, val golesFavor: Int, val golesContra: Int, val rival: String, val minutos: Int, val goles: Int, val asistencias: Int,
                     val nota: Double, val equipo: Map<String, String>, val rendimiento: String, val dudas: String)
    fun leeFoto(r: Map<String, Any?>) = LoDeLaFoto(r["se_ve"].comoBool(), r["goles_favor"].comoLong().toInt().coerceAtLeast(0), r["goles_contra"].comoLong().toInt().coerceAtLeast(0),
        r["rival"].comoTexto(), r["minutos"].comoLong().toInt().coerceIn(0, 130), r["goles"].comoLong().toInt().coerceAtLeast(0), r["asistencias"].comoLong().toInt().coerceAtLeast(0),
        r["nota"].comoDouble().coerceIn(0.0, 10.0), r["datos_equipo"].comoLista().map { it.comoMapa() }.associate { it["dato"].comoTexto() to it["valor"].comoTexto() }.filter { it.key.isNotBlank() && it.value.isNotBlank() },
        r["rendimiento"].comoTexto(), r["dudas"].comoTexto())
}
