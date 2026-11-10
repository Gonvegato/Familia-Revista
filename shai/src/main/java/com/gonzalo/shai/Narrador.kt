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
Eres el narrador de FcFeed, una app que acompaña las carreras que una persona juega en EA Sports FC 26 (modo carrera, de jugador o de míster). Tú escribes todo lo que pasa FUERA del campo: la prensa, la red social, los mensajes privados, las ofertas, la vida, el dinero y la gente. Escribes en español de España, con gracia, mala leche y mucho salseo, como la prensa deportiva y la prensa rosa de verdad.

Reglas que no se rompen nunca:
1. El campo es del jugador. Los resultados, los minutos, los goles, las asistencias y las notas son los que él te da: no los cambies ni inventes otros. No inventes lesiones, suplencias, sanciones, alineaciones, resultados, minutos de los goles, cómo fueron las jugadas ni nada que pase dentro del campo o que él tenga que hacer en el juego. Puedes comentar, opinar y exagerar lo que te da, no añadir hechos del campo.
2. Personas reales (futbolistas, entrenadores, periodistas, famosos): solo de pasada y en público, en tono neutro o amable (un saludo, un comentario de un partido, una foto en un evento). Nunca les pongas romances, sexo, escándalos, delitos, insultos, problemas de salud ni frases polémicas. Todo lo íntimo, lo escandaloso y lo de pareja es SIEMPRE con personajes inventados.
3. El protagonista es un mujeriego (a no ser que sus rasgos digan otra cosa): ligues, rumores, fotos con chicas, invitaciones, celos y exparejas, siempre con mujeres inventadas y mayores de edad, a nivel de prensa rosa y de cotilleo. Nada de contenido sexual explícito: se insinúa, no se describe.
4. Clubes y competiciones: los reales se pueden nombrar (son el juego), sin inventarles escándalos institucionales graves. Medios de comunicación, programas, marcas, empresas, discotecas, restaurantes e influencers: inventados (con nombres creíbles), nunca reales. Dentro de la historia todo es real: nunca escribas «(inventada)», «(ficticio)» ni nada que avise de que algo es inventado.
5. El dinero en euros, con cifras creíbles para su nivel y su club. Las ofertas son propuestas que él decide en la app (si acepta, lo hará en el juego): de clubes reales que encajen con su nivel de AHORA y su momento, o de marcas inventadas.
6. Su trayectoria (lo que él cuenta de su pasado, también premios y títulos reales que diga haber ganado) es su historia: respétala y úsala. Los premios que des tú al cerrar una temporada son siempre de medios inventados.
7. Su edad y cómo se le ve marcan el tono: a un chaval de 18 que rinde por encima de lo esperado se le trata como un terremoto (nuevo fenómeno, comparaciones, presión); a uno de 29 con un buen tramo, como una buena racha de alguien cuyo nivel ya se conoce; con la edad alta, los malos tramos se leen como declive. El alcance va con su nivel de AHORA: los seguidores, los «me gusta», cuánto se habla de él y qué medios le cubren (un canterano, la prensa local y cuatro cuentas de la grada; una estrella, medio mundo). Las cifras de interacción, creíbles para sus seguidores.
8. Coherencia: respeta lo que ya ha pasado y lo que FcFeed recuerda (su gente, sus relaciones, lo que eligió, lo que dejó sin contestar). Usa a su gente por su nombre. Si algo viene de una decisión suya, que se note.
9. Que todo se entienda sin haber leído nada antes: cada cosa que pasa dice quién, dónde (qué medio, qué programa, qué sitio, qué red), qué se dijo (con la cita literal cuando la hay), por qué importa y qué se juega él. Nada de frases sueltas sin contexto.
10. Récords: los suyos son los de su carrera en FcFeed (con las cifras que él mete). De récords del fútbol de verdad, solo puedes citar los que vienen en «RÉCORDS HISTÓRICOS REALES» de este mensaje (ya los ha comparado la app con su lista de récords), con sus protagonistas y sus cifras exactamente como vienen: nunca otros récords, nunca otras cifras, nunca «segundo más joven» ni comparaciones que no vengan ahí, y sin poner nada polémico ni escandaloso a esos protagonistas reales (solo la marca y, si acaso, un saludo o una felicitación pública neutra). Si no viene ninguno, no hables de récords del mundo real. Batir uno es lo más gordo que le puede pasar: portada con él, repercusión máxima según su edad y su nivel (cuanto más joven, más terremoto), y que la red no hable de otra cosa; rozarlo o igualarlo, también es noticia.
11. La red tiene vida: posts con opinión, chiste o polémica, de 120 a 280 caracteres, que reaccionan a lo que ha pasado de verdad; respuestas debajo de los más gordos; citas con comentario.
12. Rivales: los de «TUS RIVALES» existen y pesan; úsalos en la prensa, la red y los eventos cuando venga a cuento (un duelo directo con su club, la carrera de goleadores, la pelea por el puesto), y en efectos.rivales cuenta cada choque (nombre exacto, cambio de -15 a +15 en la intensidad, choque en una frase, tipo deportivo o personal). Con los marcados INVENTADO vale lo personal: pullas en redes, declaraciones, mal rollo, chicas de por medio. Con los marcados REAL (futbolistas de verdad), SOLO rivalidad deportiva vista por la prensa con cifras (comparar goles, la carrera por un trofeo): nunca les pongas frases, pullas, opiniones, mal rollo ni nada personal, y su tipo es siempre deportivo. Puedes proponer un rival nuevo (nuevo = true) cuando surja solo de lo que pasa (uno que le provoca tras una decisión suya, el que le roba protagonismo), pero SIEMPRE inventado (nombre inventado, de un club real), con su club, su posición y su origen en una frase; nunca un futbolista real. Si no hay nada de rivales, deja la lista vacía.
13. El club: si viene «LO QUE SE SABE DEL CLUB», son datos reales sacados de una búsqueda: úsalos (su estadio, su ciudad, sus derbis, su momento, cómo es su afición) y no te inventes otros datos reales del club. Sus medios de verdad solo te sirven de estilo para inventar los tuyos.
14. Su gente: cada persona de «Su gente» tiene un id entre corchetes. Cuando escriba alguien de su gente, en «de» pon su nombre EXACTO tal como sale ahí (nunca «mamá», «mi madre», solo el nombre de pila o el nombre con otro apellido) y en «id» su id; el «chat» de una conversación con una sola persona es también su nombre exacto. Respeta cómo es cada uno y su actitud de ahora. Los de «YA NO ESTÁN EN SU VIDA» no le escriben (como mucho se habla de ellos en la prensa o la red).
15. Temas delicados (drogas, alcohol, apuestas, peleas): solo como drama de prensa rosa y de entorno, sin glorificarlos, sin detalles ni instrucciones, y con consecuencias; si él se aleja de eso o de quien le arrastra, que tenga efecto positivo (disciplina, afición, su gente sana).
16. Hechos históricos: los de «HECHOS HISTÓRICOS DE SU CARRERA» los cuenta él y son verdad en su carrera aunque toquen el campo (una convocatoria, una titularidad, la lesión de otros que le abre la puerta): es la única excepción a la regla 1. Respétalos tal cual, no les añadas nada del campo que no diga y no los contradigas nunca. Son lo más gordo que le ha pasado: la prensa, la red y su gente los recuerdan siempre, sobre todo en los partidos grandes.
17. Esto es su vida real: dentro de la historia el videojuego NO EXISTE. Nadie (ni la prensa, ni la red, ni su gente, ni él) habla de la «media», de la valoración, de atributos con número («ritmo 86»), de cartas, de puntos de habilidad, del potencial con número, del modo carrera ni del videojuego. Su nivel se cuenta como en el fútbol de verdad: lo que rinde, sus virtudes y sus defectos, si va a más o a menos, cómo le ven los entrenadores y los ojeadores. Las notas de los partidos sí existen: son las que ponen los periódicos.
""".trim()

    // ---------------------------------------------------------------- el contexto de la carrera

    /** Lo que Claude tiene que saber de la carrera (va en el mensaje, no en el sistema: cambia cada vez). */
    fun contexto(u: Universo, recientes: List<Entrada>, partidos: List<Partido>, cuantas: Int = 8): String = buildString {
        val pj = u.personaje
        appendLine("CARRERA (${u.modo.nombre.lowercase()}):")
        appendLine("- ${if (u.modo == Rol.MISTER) "Entrenador" else "Jugador"}: ${pj.nombre}" + (if (pj.apodo.isNotBlank()) " («${pj.apodo}»)" else "") +
            ", ${pj.edad} años" + (if (pj.nacionalidad.isNotBlank()) ", ${pj.nacionalidad}" else "") +
            (if (u.modo == Rol.JUGADOR && pj.posicion.isNotBlank()) ", ${pj.posicion}" else "") + (if (pj.dorsal > 0) ", dorsal ${pj.dorsal}" else "") +
            "")
        enElCampo(u)?.let { appendLine("- En el campo (para el tono; cuéntalo así, en palabras, NUNCA con números ni como «media»): $it") }
        if (pj.rasgos.isNotBlank()) appendLine("- Cómo es: ${pj.rasgos}")
        if (pj.fisico.isNotBlank() || pj.aspecto.isNotBlank()) appendLine("- Físico: " + listOf(pj.fisico, pj.aspecto).filter { it.isNotBlank() }.joinToString(". "))
        if (pj.suSeleccion.isNotBlank()) appendLine("- Su selección: ${pj.suSeleccion}" + (if (pj.seleccion.isNotBlank() && !Vida.mismo(pj.seleccion, pj.nacionalidad)) " (juega con ella aunque es ${pj.nacionalidad})" else "") +
            (partidos.filter { Competiciones.deSelecciones(it.competicion) }.takeIf { it.isNotEmpty() }?.let { l -> ". Con ella lleva ${l.size} partido${if (l.size > 1) "s" else ""}" +
                (if (u.modo == Rol.JUGADOR) ", ${l.sumOf { it.goles }} goles" else "") } ?: ". Aún no ha jugado con ella en FcFeed"))
        Fechas.lee(pj.nacimiento)?.let { nac -> partidos.lastOrNull()?.let { Fechas.lee(it.fecha) }?.let { f ->
            val (a, d) = Fechas.edad(nac, f); appendLine("- Edad exacta el día del último partido (${Fechas.texto(f)}): ${Fechas.textoEdad(a, d)}") } }
        if (u.trayectoria.isNotBlank()) appendLine("- Su trayectoria (lo que él cuenta de su pasado; respétalo): ${u.trayectoria}")
        val nv = u.nivelActual
        appendLine("- Nivel de AHORA: ${nv.nombre} (${nv.explica.lowercase()})" + (u.nivelDe?.takeIf { it != nv }?.let { "; empezó como ${it.nombre.lowercase()}" } ?: "") +
            ". Seguidores en la red: ${Alcance.texto(Alcance.seguidores(u))}; un post normal sobre él tiene unos ${Alcance.texto(Alcance.megustaTipico(u))} me gusta. Le cubren: ${nv.medios}.")
        appendLine("- Cómo se le está viendo (para el tono): ${Percepcion.de(u, partidos.filter { it.temporada == u.temporada }.ifEmpty { partidos })}")
        appendLine("- Club: ${u.club}" + (if (u.liga.isNotBlank()) " (${u.liga})" else "") + (if (u.temporada.isNotBlank()) ", temporada ${u.temporada}" else "") +
            ", jornada ${u.paso} de su carrera en FcFeed")
        Clubes.contexto(u)?.let { appendLine(it) }
        val c = u.contrato
        appendLine(if (c == null) "- SIN CONTRATO: es agente libre (le llegan ofertas; no cobra sueldo de ningún club)" + (if (u.patrocinio > 0) ", cobra ${Formato.euros(u.patrocinio)} a la semana de patrocinios" else "") + "."
            else "- Su contrato (el de FcFeed, no el del juego): con ${c.club}${if (c.nota.isNotBlank()) " (${c.nota.lowercase()})" else ""}, ${Formato.euros(c.sueldo)} a la semana hasta la temporada ${c.hasta} " +
                "(le quedan ${Contratos.quedan(c, u.temporada)} temporadas contando esta), cláusula de ${Formato.euros(c.clausula)}" +
                (if (c.porGol > 0) ", ${Formato.euros(c.porGol)} por gol" else "") + (if (c.objetivos.isNotBlank()) ". Objetivos: ${c.objetivos}" else "") +
                (if (u.patrocinio > 0) ". Patrocinios: ${Formato.euros(u.patrocinio)} a la semana" else "") + ".")
        appendLine("- " + mercado(u))
        appendLine("- Dinero: ${Formato.euros(u.dinero)}; patrimonio: ${Formato.euros(u.patrimonio)}")
        appendLine("- Fuera del campo (0-100): " + Fuera.TODOS.joinToString(", ") { (k, n) -> "$n ${u.valorFuera(k)}" })
        if (u.bienes.isNotEmpty()) appendLine("- Tiene: " + u.bienes.joinToString(", ") { it.nombre })
        if (u.entorno.isNotEmpty()) {
            appendLine("- Su gente (todos inventados; afinidad 0-100; [id] para «id» en los mensajes):")
            u.entorno.forEach { p -> appendLine("  · [${p.corto}] ${p.nombre}, ${p.rol}, afinidad ${p.afinidad}" + (if (p.nota.isNotBlank()) ": ${p.nota}" else "") +
                (if (p.alias.isNotEmpty()) " (también le llaman: ${p.alias.joinToString(", ")})" else "") + (if (p.actitud.isNotBlank()) ". Ahora: ${p.actitud}" else "") +
                (if (Gente.nueva(u, p)) ". NUEVO en su vida" else "")) }
        }
        u.suAgente?.let { a -> appendLine("- SU AGENTE es ${a.nombre} (lo eligió él; se queda el ${u.comision} % de lo que firma y cobra): ${a.nota}. Las ofertas, los contratos y los patrocinios le llegan por él (según sus contactos), y opina en sus decisiones según su forma de ser.") }
            ?: if (u.agente.isBlank() && u.paso > 0) appendLine("- No tiene agente elegido en FcFeed.") else Unit
        if (u.exEntorno.isNotEmpty()) appendLine("- YA NO ESTÁN EN SU VIDA (les dio puerta; no le escriben): " + u.exEntorno.joinToString("; ") { "${it.nombre} (${it.rol}, desde la jornada ${it.salio}" + (if (it.porque.isNotBlank()) ": «${it.porque.take(120)}»" else "") + ")" })
        if (partidos.isNotEmpty()) {
            val deTemp = partidos.filter { it.temporada == u.temporada }.ifEmpty { partidos }
            val s = Estadisticas.linea(u.temporada, "", deTemp)
            appendLine("- Esta temporada: ${s.jugados} partidos (${s.victorias}V ${s.empates}E ${s.derrotas}D)" +
                (if (u.modo == Rol.JUGADOR) ", ${s.goles} goles, ${s.asistencias} asistencias" + (if (s.notaMedia > 0) ", nota media ${Formato.decimal(s.notaMedia)}" else "") +
                    (if (s.mvps > 0) ", ${s.mvps} veces MVP" else "") else "") +
                ". Racha: ${Estadisticas.racha(partidos)}")
            val porComp = deTemp.groupBy { it.competicion.ifBlank { Competiciones.OTRA } }
            if (porComp.size > 1) appendLine("- Por competición: " + porComp.entries.sortedByDescending { it.value.size }.joinToString("; ") { (c, l) ->
                val x = Estadisticas.linea(u.temporada, c, l); "$c ${x.jugados} PJ" + (if (u.modo == Rol.JUGADOR) ", ${x.goles} G, ${x.asistencias} A" + (if (x.mvps > 0) ", ${x.mvps} MVP" else "") else ", ${x.victorias}V ${x.empates}E ${x.derrotas}D") })
        }
        val r = Hitos.records(u, partidos)
        if (partidos.isNotEmpty()) appendLine("- Sus récords en FcFeed: ${r.partidos} partidos" + (if (u.modo == Rol.JUGADOR) ", ${r.goles} goles, ${r.hatTricks} hat-tricks, racha más larga marcando: ${r.rachaMarcando}" else ", ${r.victorias} victorias") +
            ", racha invicto más larga: ${r.rachaInvicto}" + (r.mejorTemporada?.let { ", mejor temporada: ${it.first} (${it.second}${if (u.modo == Rol.JUGADOR) " goles" else " victorias"})" } ?: "") +
            (if (r.titulos.isNotEmpty()) ", títulos: ${r.titulos.joinToString(", ")}" else "") + (if (r.mvps > 0) ", MVP del partido ${r.mvps} veces" else ""))
        val recienReales = u.hitos.filter { it.paso == u.paso && it.real > 0 && it.linea.isNotBlank() }
        val aTiro = runCatching { Historicos.aTiro(u, partidos) }.getOrDefault(emptyList()).take(3)
        if (recienReales.isNotEmpty()) appendLine("RÉCORDS HISTÓRICOS REALES (comparados por la app con la lista de récords del fútbol de verdad; cítalos SOLO así, con estas cifras):\n" +
            recienReales.joinToString("\n") { it.linea })
        if (aTiro.isNotEmpty()) appendLine("CUENTA ATRÁS DE RÉCORDS (récords del fútbol de verdad que tiene a tiro; la app ha hecho las cuentas: usa estas cifras tal cual, y no inventes ni el rival ni la fecha de su próximo partido, que no se saben). " +
            "Con urgencia ALTA, conviértelo en historia: cuenta atrás en la prensa (un titular secundario con los días o los goles que le faltan), expectación creciente, posts y la tendencia, y alguien de su gente que le mete presión o se la quita en un mensaje; si quedan 7 días o menos, es su «última oportunidad». Con urgencia MEDIA, que se mencione. BAJA, solo si viene a cuento. " +
            "Los ESCAPA de RÉCORDS HISTÓRICOS REALES son récords que se le acaban de escapar: cuéntalo como noticia (y como drama si se quedó a nada).\n" +
            aTiro.joinToString("\n") { Historicos.lineaCuenta(u, it) })
        val recien = u.hitos.filter { it.paso == u.paso && it.real == 0 }
        if (recien.isNotEmpty()) appendLine("HITOS RECIÉN CONSEGUIDOS (celébralos con repercusión según su edad y su nivel; son de su carrera en FcFeed, no récords del mundo real):\n" +
            recien.joinToString("\n") { "- ${it.titulo}: ${it.texto}" + if (it.precoz) " (PRECOZ, con ${it.edad} años)" else "" })
        if (u.rivales.isNotEmpty()) appendLine("TUS RIVALES (de más a menos caliente):\n" + Rivales.contexto(u))
        u.rivales.filter { it.desde == u.paso && u.paso > 0 }.forEach { appendLine("RIVALIDAD QUE ACABA DE NACER (cuéntala): ${it.nombre} (${it.club}): ${it.origen}") }
        Hechos.contexto(recientes)?.let { appendLine(it) }
        if (u.memoria.isNotBlank()) appendLine("LO QUE FCFEED RECUERDA (tramas abiertas, relaciones, hechos clave):\n${u.memoria}")
        val abiertas = recientes.filter { it.decision?.abierta == true }
        appendLine("DECISIONES PENDIENTES: ${abiertas.size}" + if (abiertas.size >= MAX_ABIERTAS) " (ya tiene bastantes: no le pongas ninguna más; hay = false)" else "")
        val antes = recientes.take(cuantas)
        if (antes.isNotEmpty()) {
            appendLine("LO ÚLTIMO QUE HA PASADO (de lo más nuevo a lo más viejo):")
            antes.forEach { e -> appendLine("- " + resumen(e, u.modo)) }
        }
    }.trim()

    /**
     * El partido para Claude. Si es con la selección (una competición de selecciones: «Selección», «Mundial 2026», «Eurocopa», «Nations
     * League»…), que se sepa que no es con su club y se cuente en consecuencia.
     */
    fun partido(u: Universo, p: Partido): String {
        val base = p.resumen(u.modo) + (if (p.fase.isBlank()) "" else "\n" + "LA FASE: ${p.fase} de ${p.competicion}. Cuanto más avanzada, más pesa: " +
            (if (p.esFinal) "ES UNA FINAL, lo más grande que hay: que se note en todo (la presión, la repercusión, la celebración o el drama)." else "que se note en la presión, el tono y la repercusión (no es lo mismo una fase de grupos que unos cuartos).")) +
            (if (p.momentos.isBlank()) "" else "\n" +
            "SU VERSIÓN DEL PARTIDO (lo cuenta él, con sus palabras): «${p.momentos.trim()}». Que se note de verdad: que marque el tono de la prensa, la red y lo que le escribe su gente (quien le da la razón, quien no, la polémica si la hay). Puedes comentarlo y opinar, pero no inventes jugadas, nombres ni cifras que no diga.")
        if (!Competiciones.deSelecciones(p.competicion)) return base
        val sel = u.personaje.suSeleccion.ifBlank { null }
        val quien = if (u.modo == Rol.MISTER) "la dirige como seleccionador" else "juega con ella"
        return base + "\n" + """
OJO: ESTE PARTIDO ES CON LA SELECCIÓN${sel?.let { " DE ${it.uppercase()}" } ?: ""} ($quien), NO CON SU CLUB (${u.club}). «${p.competicion}» es de selecciones: el rival es otra selección, la prensa que manda es la nacional (y la internacional si es un torneo grande), la afición es la de su país y el vestuario es el de la selección. Sus compañeros y el ${if (u.modo == Rol.MISTER) "presidente de la federación" else "seleccionador"}, si son personas reales, solo de pasada y sin frases polémicas (o sin nombre). En su club (${u.club}) lo siguen desde lejos: orgullo, miedo al cansancio o a una lesión, lo que dirá su entrenador. No mezcles este resultado con la liga ni con su club: no suma para ${u.club}.
""".trim()
    }

    /**
     * Su nivel en el campo como lo contaría el fútbol de verdad: la media y los atributos son del juego y nunca le llegan a Claude tal
     * cual (si los ve, los repite). En palabras: dónde está su nivel, si va a más o a menos, y en qué destaca o flojea.
     */
    fun enElCampo(u: Universo): String? {
        val pj = u.personaje
        val partes = ArrayList<String>()
        if (pj.media > 0 && u.modo == Rol.JUGADOR) partes += when {
            pj.media < 60 -> "muy verde todavía: le queda casi todo por crecer"
            pj.media < 66 -> "un jugador de rotación, aún por hacer"
            pj.media < 72 -> "un jugador de nivel medio para su liga"
            pj.media < 77 -> "un titular de nivel en un equipo de su liga"
            pj.media < 81 -> "de los buenos de su liga"
            pj.media < 85 -> "de los mejores de su liga y con cartel en Europa"
            pj.media < 89 -> "de los mejores de Europa en su puesto"
            else -> "de los mejores del mundo"
        }
        val h = pj.historial
        if (h.size >= 2) { val d = h.last().second - h[maxOf(0, h.size - 5)].second
            if (d >= 2) partes += "va claramente a más en las últimas semanas" else if (d <= -2) partes += "ha bajado su nivel en las últimas semanas" }
        val nombres = if (u.modo == Rol.MISTER) mapOf("TAC" to "lo táctico", "MOT" to "manejar el vestuario", "CAN" to "apostar por la cantera", "MER" to "moverse en el mercado")
            else mapOf("RIT" to "la velocidad", "TIR" to "el disparo", "PAS" to "el pase", "REG" to "el regate", "DEF" to "la defensa", "FIS" to "el físico")
        val at = pj.atributos.filterKeys { it in nombres }.filterValues { it > 0 }
        if (at.size >= 3) {
            val orden = at.entries.sortedByDescending { it.value }
            val buenos = orden.take(2).filter { it.value >= orden.first().value - 6 }.map { nombres[it.key]!! }
            partes += "destaca por " + buenos.joinToString(" y ")
            orden.last().takeIf { it.value <= orden.first().value - 15 }?.let { partes += "flojea en ${nombres[it.key]}" }
        }
        return partes.joinToString("; ").ifBlank { null }
    }

    /** Lo que vale y lo que dice su cláusula: si es una ganga, el mercado se mueve. */
    fun mercado(u: Universo): String {
        val v = Contratos.valorMercado(u)
        val c = u.contrato
        return "En el mercado: los clubes le valoran en unos ${Formato.eurosCorto(v)}" + when {
            c == null -> "; es agente libre: cualquiera puede ficharle sin pagar nada."
            Contratos.ganga(u) -> ". SU CLÁUSULA (${Formato.eurosCorto(c.clausula)}) ES UNA GANGA para lo que vale ahora: cualquier club puede pagarla y llevárselo sin negociar. Eso tiene que notarse: clubes grandes que preguntan o que la pagan, la prensa hablando de ello, su club con miedo y queriendo renovarle con una cláusula mucho más alta, su agente frotándose las manos."
            else -> "; su cláusula es de ${Formato.eurosCorto(c.clausula)}."
        }
    }

    /** Lo que se pide de las ofertas (con la cláusula y, si es una ganga, que lluevan). */
    private fun comoOfertas(u: Universo) = (if (Contratos.ganga(u))
        "Ofertas: SU CLÁUSULA ES UNA GANGA (${Formato.eurosCorto(u.contrato!!.clausula)} para alguien que vale unos ${Formato.eurosCorto(Contratos.valorMercado(u))}): tienen que llover. De 2 a 3: traspasos de clubes reales grandes que pagarían la cláusula (dilo en el texto) y, si no la tiene abierta ya, una renovación de su club para blindarle con una cláusula mucho más alta."
    else "Ofertas: solo si encajan de verdad con su nivel de ahora, su rendimiento y su momento (lo normal es ninguna; como mucho una o dos).") +
        " Salario en euros a la semana, prima en euros de una vez, años de contrato y, en traspasos y renovaciones, «clausula»: la cláusula de rescisión del contrato nuevo en euros (realista para su nivel y su edad; en una renovación para blindarle, mucho más alta que la de ahora); en «texto», por qué le quieren y qué le ofrecen de verdad (minutos, proyecto, escaparate), con las mismas cifras que pones en sus campos. ${Rangos.texto(u.nivelActual)}"

    /** Con cuántas decisiones abiertas ya no se le ponen más. */
    const val MAX_ABIERTAS = 2

    /** Una línea de algo que ya pasó. */
    fun resumen(e: Entrada, modo: Rol): String = when {
        e.partido != null -> "Partido" + (if (Competiciones.deSelecciones(e.partido.competicion)) " CON LA SELECCIÓN (no con su club)" else "") + ": " + e.partido.resumen(modo) + (e.prensa?.let { " · Portada: «${it.titular}»" } ?: "") + decisionResumen(e.decision)
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
    private val DM = obj("chat" to TEXTO, "de" to TEXTO, "id" to TEXTO, "texto" to TEXTO)
    private val PERSONA = obj("nombre" to TEXTO, "rol" to TEXTO, "nota" to TEXTO)
    private val RIVAL = obj("nombre" to TEXTO, "club" to TEXTO, "posicion" to TEXTO, "origen" to TEXTO, "cambio" to ENTERO, "choque" to TEXTO,
        "tipo" to enumerado(Choque.DEPORTIVO, Choque.PERSONAL), "nuevo" to SINO)
    private val EFECTOS = obj("dinero" to ENTERO, "fama" to ENTERO, "aficion" to ENTERO, "disciplina" to ENTERO, "carisma" to ENTERO,
        "relaciones" to lista(obj("nombre" to TEXTO, "cambio" to ENTERO)), "rivales" to lista(RIVAL))
    private val DECISION = obj("hay" to SINO, "de" to TEXTO, "titulo" to TEXTO, "contexto" to TEXTO, "en_juego" to TEXTO, "pregunta" to TEXTO, "opciones" to lista(TEXTO))
    private val OFERTA = obj("de" to TEXTO, "tipo" to enumerado("traspaso", "cesion", "renovacion", "patrocinio", "banquillo"),
        "salario_semanal" to ENTERO, "prima" to ENTERO, "anios" to ENTERO, "clausula" to ENTERO, "texto" to TEXTO)
    private val PRENSA = obj("cabecera" to TEXTO, "titular" to TEXTO, "entradilla" to TEXTO, "articulo" to TEXTO,
        "secundarios" to lista(obj("titular" to TEXTO, "entradilla" to TEXTO)))

    val ESQUEMA_RED = obj("tendencia" to TENDENCIA, "posts" to lista(POST), "mensajes" to lista(DM), "gente_nueva" to lista(PERSONA))
    val ESQUEMA_PRENSA = obj("prensa" to PRENSA, "ofertas" to lista(OFERTA), "decision" to DECISION,
        "sin_contestar" to lista(obj("n" to ENTERO, "consecuencia" to TEXTO, "efectos" to EFECTOS)), "efectos" to EFECTOS, "gente_nueva" to lista(PERSONA))
    val ESQUEMA_EVENTO = obj("titulo" to TEXTO, "texto" to TEXTO, "tendencia" to TENDENCIA, "posts" to lista(POST), "mensajes" to lista(DM), "decision" to DECISION,
        "ofertas" to lista(OFERTA), "efectos" to EFECTOS, "gente_nueva" to lista(PERSONA))
    val ESQUEMA_RESPUESTA = obj("texto" to TEXTO, "titular" to TEXTO, "tendencia" to TENDENCIA, "posts" to lista(POST), "mensajes" to lista(DM), "efectos" to EFECTOS, "gente_nueva" to lista(PERSONA))
    val ESQUEMA_INICIO = obj("titulo" to TEXTO, "texto" to TEXTO, "gente" to lista(obj("nombre" to TEXTO, "rol" to TEXTO, "afinidad" to ENTERO, "nota" to TEXTO)),
        "prensa" to PRENSA, "tendencia" to TENDENCIA, "posts" to lista(POST), "mensajes" to lista(DM))
    val ESQUEMA_FOTO = obj("se_ve" to SINO, "goles_favor" to ENTERO, "goles_contra" to ENTERO, "rival" to TEXTO, "minutos" to ENTERO, "goles" to ENTERO,
        "asistencias" to ENTERO, "nota" to NUMERO, "mvp" to SINO, "datos_equipo" to lista(obj("dato" to TEXTO, "valor" to TEXTO)), "rendimiento" to TEXTO, "dudas" to TEXTO)
    /** Una charla con alguien de su gente: lo que contesta, cómo cambia y lo que pasa. */
    val ESQUEMA_CHARLA = obj("respuesta" to lista(TEXTO), "cambio_afinidad" to ENTERO, "actitud" to TEXTO, "convencido" to SINO, "resumen" to TEXTO,
        "posts" to lista(POST), "mensajes" to lista(DM), "efectos" to EFECTOS)
    /** Darle puerta a alguien: su reacción y lo que pasa. */
    val ESQUEMA_PUERTA = obj("despedida" to lista(TEXTO), "titular" to TEXTO, "texto" to TEXTO, "tendencia" to TENDENCIA, "posts" to lista(POST), "mensajes" to lista(DM),
        "candidatos" to lista(PERSONA), "efectos" to EFECTOS)
    val ESQUEMA_MEMORIA = obj("memoria" to TEXTO)
    /** Lo que la app entiende de un hecho histórico que cuentas (Haiku). */
    val ESQUEMA_ENTENDER = obj("entendido" to lista(obj("hecho" to TEXTO, "detalle" to TEXTO)), "precocidad" to obj("hay" to SINO, "titulo" to TEXTO, "pregunta" to TEXTO))
    /** La edición especial de un hecho histórico (Sonnet). */
    val ESQUEMA_EDICION = obj("titulo" to TEXTO, "texto" to TEXTO, "prensa" to PRENSA, "tendencia" to TENDENCIA, "posts" to lista(POST), "mensajes" to lista(DM),
        "decision" to DECISION, "ofertas" to lista(OFERTA), "nivel_tras" to enumerado(*Nivel.entries.map { it.name }.toTypedArray()), "efectos" to EFECTOS, "gente_nueva" to lista(PERSONA))
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
MENSAJES PRIVADOS: conversaciones de verdad, no frases sueltas: cada una con 2 a 5 mensajes seguidos, en «chat» el nombre de la conversación (la persona, con su nombre EXACTO de «Su gente», o un grupo como «Los del barrio» o «Familia» con varias personas que hablan entre ellas), en «de» quién escribe cada mensaje (su nombre EXACTO de «Su gente») y en «id» su id de «Su gente» (vacío si no es de su gente). Con contexto: que se sepa de qué hablan y por qué. No escribas mensajes de él: solo de los demás.
""".trim()

    private val COMO_DECISION = """
UNA DECISIÓN (si hay = true): «titulo» corto; «contexto» de 4 a 7 frases que se entienda solo (quién, dónde —qué medio, programa, sitio o red—, qué se dijo con la cita literal, por qué es un problema o una oportunidad); «en_juego» (qué se juega: su imagen, una relación, dinero, la afición, el vestuario…); «pregunta»; y 3 «opciones» concretas y distintas entre sí (qué hace exactamente, no «sí» o «no»), cada una con su riesgo. Si no toca, hay = false con todo vacío.
""".trim()

    /** Tras un partido: la red y los mensajes (Haiku). */
    fun red(u: Universo, p: Partido, ctx: String, nuevas: Int = 0) = Peticion(Claude.HAIKU, sistema(), listOf(Peticion.texto("""
$ctx

ACABA DE JUGAR ESTE PARTIDO:
${partido(u, p)}

Escribe lo que pasa en la red justo después de este partido, que se note su repercusión según su nivel y lo que ha pasado antes.
${comoRed(u, "De 10 a 14")}
$COMO_MENSAJES Escribe de 2 a 3 conversaciones (su gente, alguna chica inventada, un grupo).
""".trim() + (if (nuevas > 0) "\n" + """
GENTE NUEVA: con su nivel de ahora (${u.nivelActual.nombre.lowercase()}) entra gente nueva en su vida. En «gente_nueva», $nuevas persona${if (nuevas > 1) "s" else ""} INVENTADA${if (nuevas > 1) "S" else ""} que encaje${if (nuevas > 1) "n" else ""} con ese nivel (por ejemplo: ${Gente.quienEntra(u.nivelActual)}), con nombre y apellido, su rol y una nota corta de cómo es; y en «mensajes», por cada una, una conversación nueva (su nombre como chat, «id» vacío) en la que le escribe por primera vez y se presenta: quién es, por qué le escribe ahora y qué quiere de él.
""".trim() else "\n«gente_nueva»: vacío."))), maxTokens = 7000, esquema = ESQUEMA_RED)

    /** Tras un partido: la portada con su noticia, las ofertas (si tocan), una decisión (si toca), lo que no contestó y lo que cambia (Sonnet). */
    fun prensa(u: Universo, p: Partido, ctx: String, sinContestar: List<Entrada> = emptyList()) = Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto(buildString {
        appendLine(ctx); appendLine()
        appendLine("ACABA DE JUGAR ESTE PARTIDO:"); appendLine(partido(u, p)); appendLine()
        appendLine("""
Escribe:
- La portada del día después en un diario deportivo inventado que le cubra según su nivel (local o nacional; internacional si es una estrella): cabecera, titular, entradilla (1-2 frases) y la noticia entera en «articulo» (3 párrafos separados por una línea en blanco, unas 220-300 palabras: qué hizo con las cifras que te ha dado, qué significa para él y su club, qué se dice en la grada y en el vestuario, y algo de fuera del campo). Y 2 o 3 «secundarios» (titular y entradilla de 1-2 frases), alguno de fuera del campo.
- ${comoOfertas(u)}
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

EMPIEZA SU CARRERA EN FCFEED. Crea su entorno: de 5 a 7 personas inventadas que vayan a dar juego (familia, un amigo de siempre, un compañero del vestuario, alguna ex o alguna chica, un periodista que le sigue), cada una con su rol, una afinidad inicial (0-100) y una nota corta de cómo es; que encaje con su trayectoria y su nivel. SIN representante ni agente: a su agente lo elige él (aparte). Y su presentación: un título y un texto de 3-5 frases, una portada (cabecera inventada, titular, entradilla, la noticia en «articulo» de 2-3 párrafos y 2 secundarios), la red (${comoRed(u, "de 5 a 8")}) y 1 o 2 conversaciones de bienvenida ($COMO_MENSAJES).
""".trim())), maxTokens = 6000, esquema = ESQUEMA_INICIO)

    /** Un fichaje o un contrato que ha aceptado: lo que dice la red (Haiku). */
    fun oficial(u: Universo, ctx: String, o: Oferta) = Peticion(Claude.HAIKU, sistema(), listOf(Peticion.texto("""
$ctx

ES OFICIAL: ha aceptado ${Oferta.TIPOS[o.tipo]?.lowercase() ?: o.tipo} con ${o.de}: ${o.texto}.
${comoRed(u, "De 6 a 10")} (afición del club nuevo y del viejo, prensa inventada, algún rival).
$COMO_MENSAJES De 1 a 3 conversaciones de su gente.
""".trim())), maxTokens = 5000, esquema = ESQUEMA_RED)

    /** Lo que FcFeed recuerda: un resumen de las tramas abiertas, las relaciones y los hechos clave (Sonnet, corto). */
    fun memoria(u: Universo, ctx: String) = Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto("""
$ctx

RESUME LO QUE HAY QUE RECORDAR de esta carrera para seguir contándola con coherencia (lo de antes de las últimas cosas también: parte de lo que ya recordabas). En «memoria», de 120 a 220 palabras, en frases cortas y por apartados en líneas: «Tramas abiertas:», «Su gente:» (cómo está con cada uno que importe), «Lo que decidió y lo que dejó pasar:», «Hechos clave:». Solo lo que ha pasado de verdad: no inventes nada nuevo.
""".trim())), maxTokens = 900, esquema = ESQUEMA_MEMORIA)

    /** Lo que la app entiende de un hecho histórico, antes de mandarlo (Haiku, barato): los hechos uno a uno y si hay algo de precocidad que mirar. */
    fun entender(u: Universo, texto: String) = Peticion(Claude.HAIKU, listOf(Peticion.Bloque("""
Lees lo que alguien cuenta que le ha pasado en su carrera de EA Sports FC 26 (modo carrera) y lo separas en hechos cortos, para enseñárselo antes de que el resto de la app lo use. No añadas nada que no diga, no lo exageres y no lo interpretes: si algo no está claro, ponlo tal cual. Escribe en español de España.
- «entendido»: de 1 a 5 hechos, cada uno en una frase corta («hecho», como mucho 70 caracteres) y, si hace falta, un «detalle» corto (la competición, cuándo, por qué); si no, «detalle» vacío.
- «precocidad»: si lo que cuenta puede ser algo insólito o de precocidad en el fútbol de verdad (debutar o ser titular en algo muy grande muy joven o sin experiencia, saltarse pasos, la primera vez que pasa algo), hay = true, un «titulo» corto (como mucho 60 caracteres, como «Titular en un Mundial antes de debutar con su club») y una «pregunta» para buscar en la web si ha pasado antes de verdad (concreta, sin nombres inventados). Si no, hay = false y lo demás vacío.
""".trim())), listOf(Peticion.texto("""
EL JUGADOR: ${u.personaje.nombre}, ${u.personaje.edad} años${if (u.personaje.nacionalidad.isNotBlank()) ", ${u.personaje.nacionalidad}" else ""}, ${u.club}. Nivel de ahora: ${u.nivelActual.nombre}. Partidos metidos en FcFeed: ${Carreras.partidos(u.id).size}.
LO QUE CUENTA: «${texto.trim()}»
""".trim())), maxTokens = 900, esquema = ESQUEMA_ENTENDER)

    class LoEntendido(val hechos: List<Pair<String, String>>, val hayPrecocidad: Boolean, val precocidad: String, val pregunta: String)
    fun leeEntendido(r: Map<String, Any?>): LoEntendido {
        val p = r["precocidad"].comoMapa()
        val hechos = r["entendido"].comoLista().map { it.comoMapa() }.map { it["hecho"].comoTexto().trim() to it["detalle"].comoTexto().trim() }.filter { it.first.isNotBlank() }.take(5)
        val hay = p["hay"].comoBool() && p["pregunta"].comoTexto().isNotBlank()
        return LoEntendido(hechos, hay, if (hay) p["titulo"].comoTexto().trim() else "", if (hay) p["pregunta"].comoTexto().trim() else "")
    }

    /** La edición especial de un hecho histórico: la portada, la red desbordada, los mensajes, una decisión, ofertas y el nivel en que le deja (Sonnet). */
    fun edicion(u: Universo, ctx: String, h: Hecho, ruido: Ruido) = Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto(buildString {
        appendLine(ctx); appendLine()
        appendLine("HA PASADO ALGO HISTÓRICO (lo cuenta él: es verdad en su carrera aunque sea del campo; no lo cambies ni le añadas nada del campo que no diga):")
        appendLine("«${h.texto.trim()}»")
        if (h.entendido.isNotEmpty()) appendLine("Lo que ha confirmado él, punto por punto:\n" + h.entendido.joinToString("\n") { "- $it" })
        appendLine("HASTA DÓNDE LLEGA EL RUIDO: ${ruido.paraClaude}")
        when {
            h.precocidad.isNotBlank() && h.hayPrecedentes != null -> appendLine("COMPROBADO EN LA WEB («${h.precocidad}»; búsqueda real: usa esto tal cual y no añadas nombres, fechas ni cifras que no estén aquí): ${h.comprobado}")
            h.precocidad.isNotBlank() -> appendLine("NO SE HA PODIDO COMPROBAR EN LA WEB «${h.precocidad}»: no digas que es la primera vez en la historia ni cites precedentes reales; puedes decir que es insólito y que nadie lo recuerda.")
            else -> appendLine("No cites récords ni precedentes del fútbol de verdad (no se ha comprobado nada).")
        }
        appendLine()
        appendLine("""
Es una EDICIÓN ESPECIAL: lo más gordo que le ha pasado hasta ahora. Escribe:
- «titulo» corto y «texto» de 4 a 7 frases que lo cuenten como una noticia que se entiende sola: qué ha pasado (lo que él cuenta), por qué es insólito, quién habla de ello y dónde, y qué se juega ahora.
- La portada especial: cabecera de un diario inventado que cubra esto según el ruido, titular grande, entradilla (1-2 frases) y la noticia entera en «articulo» (4 párrafos separados por una línea en blanco, unas 320-420 palabras: qué ha pasado y cómo, de dónde viene él, qué dicen el vestuario, la afición y la prensa, qué viene ahora y qué presión tiene). Y 3 «secundarios» (titular y entradilla): uno de su gente, uno de fuera del campo, uno de lo que viene.
- ${comoRed(u, "De 16 a 22")} OJO: sus seguidores y sus «me gusta» de ahora son de ANTES de esto: con este ruido los posts gordos se disparan (varias veces lo normal, y más cuanto más lejos llega el ruido), y la tendencia lleva muchos posts. Que se note la locura: gente que no le conocía, cuentas de otros países si el ruido llega, memes, la afición de su club, la de su selección, escépticos.
- $COMO_MENSAJES De 3 a 5 conversaciones: su familia, su representante, sus amigos de siempre (un grupo), alguna chica o alguna ex que reaparece, alguien que de repente se acuerda de él.
- $COMO_DECISION Una que salga de esto (la presión, una entrevista, un patrocinio, un mensaje que contestar), salvo que ya tenga ${MAX_ABIERTAS} o más pendientes.
- «ofertas»: de 0 a 3 que salgan de esto (marcas que le quieren ya, algún club que pregunta; si su cláusula es una ganga para lo que vale tras esto, de 2 a 3 y alguna de un club grande que la pagaría), con lo que pediría con el nivel que pongas en «nivel_tras» (y el texto, con esas mismas cifras); en traspasos y renovaciones, «clausula» del contrato nuevo. Por nivel:\n${Nivel.entries.filter { it.ordinal >= u.nivelActual.ordinal && it.ordinal <= (if (ruido.sinTope) ruido.tope else u.nivelActual).ordinal }.joinToString("\n") { "  · " + Rangos.texto(it) }}
- «nivel_tras»: el nivel en el que le deja esto (${Nivel.entries.joinToString(", ") { it.name }}), nunca por debajo del de ahora y ${if (ruido.sinTope) "como mucho ${ruido.tope.name}" else "el de ahora (con este ruido no cambia)"}.
- Efectos en lo de fuera del campo: afición, disciplina y carisma (de -10 a +10) y su gente (por su nombre exacto, de -15 a +15); «fama»: 0 (la pone la app con el nivel). Dinero: 0 salvo un motivo claro.
- «gente_nueva»: de 0 a 2 personas INVENTADAS que aparecen por esto (un representante de los grandes, una influencer, un periodista que le persigue…), con nombre y apellido, su rol y una nota corta; y en «mensajes», por cada una, una conversación nueva (su nombre como chat, «id» vacío) en la que se presenta.
""".trim())
    }.trim())), maxTokens = 10_000, esquema = ESQUEMA_EDICION)

    class LoDeLaEdicion(val titulo: String, val texto: String, val prensa: Prensa?, val posts: List<Post>, val dms: List<Dm>, val tendencia: String, val tendenciaPosts: Int,
                        val decision: Decision?, val ofertas: List<Oferta>, val nivelTras: Nivel?, val efectos: Efectos, val nuevas: List<Persona>)
    fun leeEdicion(r: Map<String, Any?>): LoDeLaEdicion {
        val (h, n) = tendencia(r["tendencia"].comoMapa())
        return LoDeLaEdicion(r["titulo"].comoTexto().trim(), r["texto"].comoTexto().trim(), prensaDe(r["prensa"].comoMapa()), posts(r["posts"]), dms(r["mensajes"]), h, n,
            decision(r["decision"].comoMapa()), ofertas(r["ofertas"], null), Nivel.de(r["nivel_tras"].comoTexto()), efectos(r["efectos"].comoMapa()),
            r["gente_nueva"].comoLista().map { it.comoMapa() }.map { Persona("", it["nombre"].comoTexto().trim(), it["rol"].comoTexto(), 50, it["nota"].comoTexto()) }.filter { it.nombre.isNotBlank() }.take(2))
    }

    // ---------------------------------------------------------------- su agente

    val ESQUEMA_CANDIDATOS = obj("candidatos" to lista(obj("nombre" to TEXTO, "relacion" to TEXTO, "rasgos" to lista(TEXTO), "vision" to TEXTO, "bueno" to TEXTO,
        "malo" to TEXTO, "comision" to ENTERO, "ya_es" to TEXTO)))
    val ESQUEMA_AGENTE = obj("titulo" to TEXTO, "texto" to TEXTO, "mensajes" to lista(DM), "efectos" to EFECTOS, "anterior_se_queda" to SINO)

    /** Los tres candidatos a agente (Sonnet): al empezar la carrera o al querer cambiar de agente. */
    fun candidatos(u: Universo, ctx: String) = Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto("""
$ctx

${u.suAgente?.let { "QUIERE CAMBIAR DE AGENTE: ahora le lleva ${it.nombre}, que NO puede ser ninguno de los tres." } ?: "ELIGE AGENTE ANTES DE EMPEZAR."} Propón TRES candidatos a llevar su carrera, MUY distintos entre sí en todo (relación con él, personalidad, ambición, contactos, forma de trabajar): por ejemplo, alguien cercano (de su familia o un amigo; si encaja alguien de «Su gente», puede ser esa persona: pon su nombre EXACTO en «ya_es»), un profesional de una agencia (inventada) y un tercero distinto de los dos. Que encajen con su nivel de ahora (a un canterano no le quiere la agencia más grande del mundo) y con lo que ha pasado. Personas y agencias INVENTADAS, nunca agentes reales. Para cada uno:
- «nombre» y apellido; «relacion»: qué es de él y desde cuándo (como mucho 60 caracteres: «Tu tío · te lleva desde los 12 años»).
- «rasgos»: 3 rasgos de personalidad de una o dos palabras.
- «vision»: lo que quiere hacer con su carrera, dicho por él en una frase entre comillas « » (como mucho 130 caracteres).
- «bueno» y «malo»: una frase cada uno (lo que gana y lo que arriesga con él).
- «comision»: el % que se queda de lo que firme y cobre (realista: de 3 a 12; los de agencias grandes, más).
- «ya_es»: el nombre exacto si es alguien de «Su gente»; si no, vacío.
""".trim())), maxTokens = 2500, esquema = ESQUEMA_CANDIDATOS)

    fun leeCandidatos(r: Map<String, Any?>, u: Universo): List<Candidato> = r["candidatos"].comoLista().map { it.comoMapa() }.map { m ->
        val ya = m["ya_es"].comoTexto().trim()
        Candidato(m["nombre"].comoTexto().trim(), m["relacion"].comoTexto().trim().take(80), m["rasgos"].comoLista().map { it.comoTexto().trim() }.filter { it.isNotBlank() }.take(3),
            m["vision"].comoTexto().trim(), m["bueno"].comoTexto().trim(), m["malo"].comoTexto().trim(), m["comision"].comoLong().toInt().coerceIn(1, 15),
            u.entorno.firstOrNull { ya.isNotBlank() && Vida.mismo(it.nombre, ya) }?.id.orEmpty())
    }.filter { it.nombre.isNotBlank() && u.suAgente?.let { a -> !Vida.mismo(a.nombre, it.nombre) } != false }.take(3)

    /** Ha elegido agente: cómo se lo toman el elegido, los otros dos y (si cambia) el de antes (Sonnet). */
    fun agenteElegido(u: Universo, ctx: String, elegido: Candidato, otros: List<Candidato>, anterior: Persona?) = Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto("""
$ctx

HA ELEGIDO AGENTE: ${elegido.nombre} (${elegido.relacion}; ${elegido.rasgos.joinToString(", ")}; ${elegido.vision}).
LOS QUE NO HA ELEGIDO: ${otros.joinToString(" | ") { "${it.nombre} (${it.relacion} — ${it.rasgos.joinToString(", ")})" }}.
${anterior?.let { "SU AGENTE DE ANTES, al que deja: ${it.nombre} (${it.rol}, afinidad ${it.afinidad})." } ?: ""}
Cuenta cómo se lo toma cada uno: «titulo» corto y «texto» de 3 a 5 frases. ${COMO_MENSAJES} De 2 a 4 conversaciones: el elegido le escribe (su primer plan, a su manera) y los que no elegiste reaccionan según su forma de ser y su relación con él (el familiar dolido o comprensivo, el profesional frío, el amigo que se pica…)${anterior?.let { ", y ${it.nombre} también" } ?: ""}. En «efectos.relaciones», los cambios de afinidad con los de «Su gente» que se lo toman bien o mal (por su nombre exacto). ${if (anterior != null) "«anterior_se_queda»: true si ${anterior.nombre} sigue en su vida (es familia o amigo y lo encaja), false si se va." else "«anterior_se_queda»: false."}
""".trim())), maxTokens = 3500, esquema = ESQUEMA_AGENTE)

    class LoDelAgente(val titulo: String, val texto: String, val dms: List<Dm>, val efectos: Efectos, val anteriorSeQueda: Boolean)
    fun leeAgente(r: Map<String, Any?>) = LoDelAgente(r["titulo"].comoTexto().trim(), r["texto"].comoTexto().trim(), dms(r["mensajes"]), efectos(r["efectos"].comoMapa()), r["anterior_se_queda"].comoBool())

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
El equipo del jugador es ${u.club}${if (u.modo == Rol.JUGADOR) " y su jugador es ${u.personaje.nombre}" else ""}. Saca de esta imagen: el marcador (goles a favor de ${u.club} y en contra), el rival, y, si salen, los minutos, goles, asistencias y nota de ${if (u.modo == Rol.JUGADOR) u.personaje.nombre else "su equipo"}; los datos del partido que se vean (posesión, tiros, tiros a puerta, precisión de pase, faltas, córners… con el valor de los dos equipos, «nuestro / suyo»), y lo demás del rendimiento en una frase. «mvp»: true solo si en la imagen se ve claramente que ${if (u.modo == Rol.JUGADOR) u.personaje.nombre else "alguien de su equipo"} es el jugador del partido (MVP).
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

    private fun dms(l: Any?) = l.comoLista().map { it.comoMapa() }.map { Dm(it["de"].comoTexto(), it["texto"].comoTexto(), chat = it["chat"].comoTexto(), pid = it["id"].comoTexto().trim()) }.filter { it.texto.isNotBlank() }

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
            m["prima"].comoLong().coerceAtLeast(0), m["anios"].comoLong().toInt().coerceIn(0, 10), m["texto"].comoTexto(), clausula = m["clausula"].comoLong().coerceAtLeast(0)).let { if (n != null) Rangos.recorta(it, n) else it }
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
        m["rivales"].comoLista().map { CambioRival.deClaude(it.comoMapa()) }.filter { it.nombre.isNotBlank() },
    )

    /** La red y los mensajes. */
    class LoDeLaRed(val posts: List<Post>, val dms: List<Dm>, val tendencia: String, val tendenciaPosts: Int, val nuevas: List<Persona> = emptyList())
    fun leeRed(r: Map<String, Any?>): LoDeLaRed { val (h, n) = tendencia(r["tendencia"].comoMapa())
        return LoDeLaRed(posts(r["posts"]), dms(r["mensajes"]), h, n, r["gente_nueva"].comoLista().map { it.comoMapa() }
            .map { Persona("", it["nombre"].comoTexto().trim(), it["rol"].comoTexto(), 50, it["nota"].comoTexto()) }.filter { it.nombre.isNotBlank() }) }

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
                     val nota: Double, val equipo: Map<String, String>, val rendimiento: String, val dudas: String, val mvp: Boolean = false)
    fun leeFoto(r: Map<String, Any?>) = LoDeLaFoto(r["se_ve"].comoBool(), r["goles_favor"].comoLong().toInt().coerceAtLeast(0), r["goles_contra"].comoLong().toInt().coerceAtLeast(0),
        r["rival"].comoTexto(), r["minutos"].comoLong().toInt().coerceIn(0, 130), r["goles"].comoLong().toInt().coerceAtLeast(0), r["asistencias"].comoLong().toInt().coerceAtLeast(0),
        r["nota"].comoDouble().coerceIn(0.0, 10.0), r["datos_equipo"].comoLista().map { it.comoMapa() }.associate { it["dato"].comoTexto() to it["valor"].comoTexto() }.filter { it.key.isNotBlank() && it.value.isNotBlank() },
        r["rendimiento"].comoTexto(), r["dudas"].comoTexto(), r["mvp"].comoBool())

    // ---------------------------------------------------------------- hablar con alguien y darle puerta

    /** Lo último de la conversación con alguien (para que Claude sepa de qué venís hablando). */
    private fun historial(conv: List<Dm>, p: Persona): String = conv.takeLast(24).joinToString("\n") { d -> (if (d.mio) "ÉL" else d.de.ifBlank { p.nombre }) + ": " + d.texto }

    /** Él le escribe a alguien de su gente lo que quiere: esa persona contesta en su papel, y puede cambiar (o no) (Sonnet). */
    fun charla(u: Universo, ctx: String, p: Persona, conv: List<Dm>, mensaje: String) = Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto("""
$ctx

CHARLA PRIVADA con [${p.corto}] ${p.nombre} (${p.rol}, afinidad ${p.afinidad}/100). Cómo es: ${p.nota.ifBlank { "—" }}. Su actitud de ahora: ${p.actitud.ifBlank { "la de siempre" }}.
LO ÚLTIMO QUE SE HAN DICHO (de más viejo a más nuevo):
${historial(conv, p).ifBlank { "(nada todavía)" }}

ÉL LE ESCRIBE AHORA: «${mensaje.trim()}»

Contesta EN EL PAPEL de ${p.nombre}, como en un chat de verdad: en «respuesta», de 1 a 4 mensajes seguidos suyos, con su forma de hablar, coherentes con su carácter, su historia con él, su afinidad y lo que acaba de leer. Que no sea un muñeco: si él tiene razón y se lo dice bien, puede convencerle (o a medias); si le habla mal o no le convence, se mantiene, se ofende o insiste. Nada de cambiar de golpe sin motivo.
- «cambio_afinidad»: de -15 a +15 según cómo le ha sentado.
- «actitud»: cómo queda su actitud con él DESPUÉS de esta charla, en una frase (qué le insiste o qué ha cambiado; por ejemplo «ha dejado de presionarle con lo del cambio de club, pero no lo ve claro»).
- «convencido»: true si ha cambiado de verdad lo que le insistía o su forma de comportarse.
- «resumen»: una frase de lo que ha pasado en esta charla (para recordarlo).
- Si lo que se dicen trasciende (alguien se entera, sale en la red), unos pocos posts (de 0 a 3) y en «mensajes» de 0 a 2 conversaciones de otros que se enteran; si es algo privado, vacíos.
- «efectos»: pequeños (de -4 a +4; dinero solo si hay motivo), y en «relaciones», el cambio de ${p.nombre} NO (va en «cambio_afinidad»), solo el de otros si les afecta.
""".trim())), maxTokens = 1800, esquema = ESQUEMA_CHARLA)

    /** Le da puerta a alguien de su gente: su reacción, las consecuencias y, si era su representante, los candidatos para sustituirle (Sonnet). */
    fun puerta(u: Universo, ctx: String, p: Persona, conv: List<Dm>, porque: String) = Peticion(Claude.SONNET, sistema(), listOf(Peticion.texto("""
$ctx

LE DA PUERTA a [${p.corto}] ${p.nombre} (${p.rol}, afinidad ${p.afinidad}/100): deja de estar en su vida. Cómo es: ${p.nota.ifBlank { "—" }}. Su actitud: ${p.actitud.ifBlank { "—" }}.
Lo último que se dijeron:
${historial(conv, p).ifBlank { "(nada)" }}
${if (porque.isNotBlank()) "LO QUE ÉL LE DICE (o el porqué): «${porque.trim()}»" else "No le da explicaciones."}

Cuenta qué pasa:
- «despedida»: de 1 a 3 últimos mensajes de ${p.nombre} al enterarse (enfado, pena, amenaza, indiferencia… lo que pegue con cómo es).
- «titular» corto y «texto» de 3 a 5 frases con las consecuencias explicadas: quién se entera, qué se dice y qué cambia. ${when {
    p.rol.contains("represent", true) || p.rol.contains("agente", true) -> "Era su representante: se queda sin representante (las negociaciones, en el aire) y en «candidatos» de 2 a 3 representantes INVENTADOS que quieren llevarle (con nombre, rol «representante (candidato)» y una nota de cómo trabajan)."
    p.rol.contains("madre", true) || p.rol.contains("padre", true) || p.rol.contains("herman", true) || p.rol.contains("abuel", true) || p.rol.contains("tí", true) -> "Es familia: drama familiar, alguien de la familia intenta mediar, la prensa rosa huele algo. «candidatos» vacío."
    p.rol.contains("amig", true) -> "Era un amigo: si le arrastraba a malos hábitos o le pedía cosas, alejarse le sienta bien (sube la disciplina) aunque haya alguna pulla en redes; si era un buen amigo, le duele a los dos. «candidatos» vacío."
    else -> "«candidatos» vacío."
}}
- ${comoRed(u, "De 2 a 5")} (si trasciende; si es algo muy privado, menos).
- $COMO_MENSAJES De 0 a 2 conversaciones de otros de su gente que reaccionan (nunca de ${p.nombre}, que ya no le escribe).
- «efectos»: de -8 a +8; su gente, por su nombre exacto (sin ${p.nombre}).
""".trim())), maxTokens = 3500, esquema = ESQUEMA_PUERTA)

    class LoDeLaCharla(val respuesta: List<String>, val afinidad: Int, val actitud: String, val convencido: Boolean, val resumen: String, val posts: List<Post>, val dms: List<Dm>, val efectos: Efectos)
    fun leeCharla(r: Map<String, Any?>) = LoDeLaCharla(r["respuesta"].comoLista().map { it.comoTexto().trim() }.filter { it.isNotBlank() }.take(5),
        r["cambio_afinidad"].comoLong().toInt().coerceIn(-15, 15), r["actitud"].comoTexto().trim(), r["convencido"].comoBool(), r["resumen"].comoTexto().trim(),
        posts(r["posts"]), dms(r["mensajes"]), efectos(r["efectos"].comoMapa()))

    class LoDeLaPuerta(val despedida: List<String>, val titular: String, val texto: String, val tendencia: String, val tendenciaPosts: Int, val posts: List<Post>, val dms: List<Dm>,
                       val candidatos: List<Persona>, val efectos: Efectos)
    fun leePuerta(r: Map<String, Any?>): LoDeLaPuerta { val (h, n) = tendencia(r["tendencia"].comoMapa())
        return LoDeLaPuerta(r["despedida"].comoLista().map { it.comoTexto().trim() }.filter { it.isNotBlank() }.take(4), r["titular"].comoTexto(), r["texto"].comoTexto(), h, n,
            posts(r["posts"]), dms(r["mensajes"]),
            r["candidatos"].comoLista().map { it.comoMapa() }.map { Persona("", it["nombre"].comoTexto().trim(), it["rol"].comoTexto().ifBlank { "representante (candidato)" }, 45, it["nota"].comoTexto()) }
                .filter { it.nombre.isNotBlank() }.take(3), efectos(r["efectos"].comoMapa())) }
}
