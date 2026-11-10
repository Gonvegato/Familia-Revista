package com.gonzalo.shai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Tu clave de la API de Claude (la de tu organización de Claude Console, la de los créditos del plan): se guarda **cifrada en este
 * móvil** ([Secretos], con una clave del almacén de Android que no sale de él). Nunca va a un registro ni a un mensaje de error. Se
 * manda en la cabecera `x-api-key`.
 */
object ClaveApi {
    const val CLAVE = "shai_anthropic"
    /** En las pruebas: la clave, sin caja. */
    @Volatile var fija: String? = null
    /** Sube al cambiarla (las pantallas lo leen para volver a pintarse). */
    var version by androidx.compose.runtime.mutableIntStateOf(0)

    fun lee(): String { fija?.let { return it }; return runCatching { Secretos.lee(CLAVE) }.getOrDefault("").trim() }
    /** ¿Se puede pedir? (en modo prueba, siempre: no hace falta clave). */
    val hay: Boolean get() = Prueba.activo || hayDeVerdad
    /** ¿Hay una clave puesta de verdad? */
    val hayDeVerdad: Boolean get() { version; return lee().isNotBlank() }

    fun guarda(k: String) {
        val t = k.trim()
        if (fija != null) { fija = t } else Secretos.pon(CLAVE, t)
        version++
    }

    /** «sk-ant-…a1B2» (para enseñar cuál tienes puesta sin enseñarla). */
    fun tapada(): String = lee().let { k -> if (k.length < 12) "" else k.take(7) + "…" + k.takeLast(4) }
}

/**
 * Lo que hace FcFeed con lo que metes: guardar el partido, pedir la red, la prensa y lo demás, aplicarlo a tu carrera y mandar los dos
 * eventos de entre partidos (por lotes). Todo bloquea: se llama fuera del hilo principal. Si algo no llega (sin conexión, el tope…), lo
 * del partido queda guardado igual y se puede volver a pedir lo que falta ([reintenta]).
 */
object Motor {
    @Volatile var claude: Claude = Claude({ ClaveApi.lee() })
    /** En las pruebas: otro reloj. */
    @Volatile var reloj: () -> Long = { System.currentTimeMillis() }
    /** Cuánto tarda en salir el segundo evento de entre partidos tras el primero. */
    @Volatile var separacion: Long = 6 * 3600_000L
    /** Al crear una carrera, pedir los candidatos a agente (las capturas de antes lo apagan). */
    @Volatile var pideAgentes: Boolean = true
    /** Cada cuántas jornadas se rehace lo que FcFeed recuerda. */
    const val CADA_MEMORIA = 3

    /** La carrera de la que se está pidiendo algo ahora (en este hilo): el modo prueba la usa para no repetir. */
    val actual = ThreadLocal<String?>()

    private val cerrojo = Any()

    /** Pide, con el tope y el gasto apuntado. */
    fun pide(p: Peticion, intento: Int = 0): Map<String, Any?> {
        Gasto.compruebaAntes(Precios.coste(p.modelo, p.usoMaximo()))
        val r = try { claude.mensaje(p) } catch (e: Claude.Rechazo) { Gasto.apunta(e.modelo.ifBlank { p.modelo }, Precios.coste(e.modelo.ifBlank { p.modelo }, e.uso)); throw e }
        Gasto.apunta(r.modelo.ifBlank { p.modelo }, Precios.coste(r.modelo.ifBlank { p.modelo }, r.uso))
        // (el JSON va pedido en el mensaje, no forzado: si no se pudo leer, una vez más; si se cortó, con más sitio)
        // (lo que escribe el narrador, sin el videojuego: ni la media ni nada que lo nombre)
        return try { r.json().let { j -> if (p.sistema.firstOrNull()?.texto == Narrador.REGLAS) SinJuego.limpia(j) as Map<String, Any?> else j } } catch (e: Claude.Fallo) {
            if (intento > 0) throw e
            pide(if (r.parada == "max_tokens") p.copy(maxTokens = (p.maxTokens * 3 / 2).coerceAtMost(16_000)) else p, intento + 1)
        }
    }

    private fun <T> en(universo: String, f: () -> T): T { val antes = actual.get(); actual.set(universo); try { return f() } finally { actual.set(antes) } }

    private fun contexto(u: Universo, cuantas: Int = 8) =
        Narrador.contexto(u, Carreras.entradasDe(u.id, ahora = reloj()), Carreras.partidos(u.id).map { it.second }, cuantas)

    /** ¿Cabe otra decisión? (con [Narrador.MAX_ABIERTAS] abiertas, ya no se ponen más). */
    fun cabeDecision(universo: String): Boolean = Carreras.entradasDe(universo, ahora = reloj()).count { it.decision?.abierta == true } < Narrador.MAX_ABIERTAS

    // ---------------------------------------------------------------- una carrera nueva

    /** Crea la carrera y pide su principio (su gente y su presentación). Si eso falla, la carrera queda creada igual. */
    fun crea(u0: Universo): Pair<Universo, String?> {
        var u = Carreras.guarda(u0.copy(precios = u0.precios.ifEmpty { Mercado.inicial() }, famaHist = u0.famaHist.ifEmpty { listOf(0 to u0.valorFuera(Fuera.FAMA)) },
            nivelGente = if (u0.nivelGente < 0) u0.nivelActual.ordinal else u0.nivelGente,
            comps = u0.comps.ifEmpty { u0.clubInfo?.takeIf { it.confirmado && it.es(u0.club) }?.competiciones.orEmpty() },
            compsDe = u0.compsDe.ifBlank { if (u0.clubInfo?.competiciones?.isNotEmpty() == true) u0.club else "" }))
        val fallo = en(u.id) {
            try {
                val r = Narrador.leeInicio(pide(Narrador.inicio(u)))
                // (sin repetir a nadie, ni una segunda madre, padre o representante si ya los pusiste tú)
                // (sin representante: a su agente lo elige él entre tres; salvo que lo pusieras tú)
                val gente = if (pideAgentes) r.gente.filter { Gente.rolUnico(it.rol) != "representante" && !it.rol.contains("agente", true) } else r.gente
                u = Carreras.guarda(u.copy(entorno = u.entorno + gente.filter { g -> u.entorno.none { Vida.mismo(it.nombre, g.nombre) } &&
                    (Gente.rolUnico(g.rol) == null || u.entorno.none { Gente.rolUnico(it.rol) == Gente.rolUnico(g.rol) }) }))
                Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.INICIO, reloj(), u.paso, r.titulo, r.texto, posts = r.posts, dms = r.dms, prensa = r.prensa,
                    tendencia = r.tendencia, tendenciaPosts = r.tendenciaPosts))
                null
            } catch (e: Exception) { mensaje(e) }
        }
        // Antes de empezar, a elegir agente (tres candidatos; si no llegan, se piden desde Inicio o Perfil)
        if (fallo == null && pideAgentes) runCatching { candidatos(u.id) }
        return (Carreras.de(u.id) ?: u) to fallo
    }

    // ---------------------------------------------------------------- su agente

    /** Pide los tres candidatos a agente (al empezar o para cambiar). Devuelve por qué no se pudo, si no se pudo. */
    fun candidatos(universo: String): String? = en(universo) {
        val u = Carreras.de(universo) ?: return@en "Esta carrera ya no está."
        try {
            val l = Narrador.leeCandidatos(pide(Narrador.candidatos(u, contexto(u))), u)
            if (l.size < 2) return@en "No han llegado los candidatos: prueba otra vez."
            Carreras.guarda((Carreras.de(universo) ?: u).copy(candidatos = l)); null
        } catch (x: Exception) { mensaje(x) }
    }

    /** Sin cambiar de agente (se descartan los candidatos). */
    fun descartaCandidatos(universo: String) { Carreras.de(universo)?.let { Carreras.guarda(it.copy(candidatos = emptyList())) } }

    /**
     * Elige al candidato [i]: pasa a ser su agente (en su gente, con su comisión, que desde ya se descuenta de lo que cobra y firma); el de
     * antes deja de serlo (y sigue en su vida o se va, según se lo tome). Claude cuenta cómo se lo toma cada uno (si falla, va sin eso).
     */
    fun eligeAgente(universo: String, i: Int): String? = synchronized(cerrojo) { en(universo) {
        var u = Carreras.de(universo) ?: return@en "Esta carrera ya no está."
        val c = u.candidatos.getOrNull(i) ?: return@en null
        val otros = u.candidatos.filterIndexed { j, _ -> j != i }
        val anterior = u.suAgente ?: u.entorno.firstOrNull { Gente.rolUnico(it.rol) == "representante" && it.id != c.persona && !Vida.mismo(it.nombre, c.nombre) }
        val nota = "${c.rasgos.joinToString(", ")}. Su idea: ${c.vision} Lo bueno: ${c.bueno} Lo malo: ${c.malo}"
        val ya = u.entorno.firstOrNull { it.id == c.persona || Vida.mismo(it.nombre, c.nombre) }
        val persona = ya?.copy(rol = "Representante · ${c.relacion}", nota = nota, afinidad = maxOf(ya.afinidad, 60))
            ?: Persona(Universo.nuevoId("p"), c.nombre, "Representante · ${c.relacion}", 65, nota, desde = maxOf(1, u.paso))
        var lo: Narrador.LoDelAgente? = null
        val fallo = try { lo = Narrador.leeAgente(pide(Narrador.agenteElegido(u, contexto(u), c, otros, anterior))); null } catch (x: Exception) { mensaje(x) }
        var entorno = u.entorno.filter { it.id != persona.id } + persona
        var ex = u.exEntorno
        if (anterior != null) {
            val viejo = anterior.copy(rol = "Exrepresentante", afinidad = (anterior.afinidad - 15).coerceAtLeast(0))
            entorno = entorno.map { if (it.id == anterior.id) viejo else it }
            if (lo?.anteriorSeQueda != true) { entorno = entorno.filter { it.id != anterior.id }; ex = ex + viejo.copy(salio = u.paso, porque = "Dejó de ser tu agente: elegiste a ${c.nombre}") }
        }
        u = u.copy(entorno = entorno, exEntorno = ex, agente = persona.id, comision = c.comision, candidatos = emptyList())
        lo?.let { u = Vida.aplica(u, it.efectos.copy(fuera = emptyMap(), dinero = 0)) }
        u = Carreras.guarda(u)
        Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.AGENTE, reloj(), u.paso, lo?.titulo?.ifBlank { null } ?: "${c.nombre}, tu agente",
            lo?.texto?.ifBlank { null } ?: "Has elegido a ${c.nombre} (${c.relacion}) para llevar tu carrera. Se queda el ${c.comision} % de lo que firmes y cobres.",
            dms = lo?.dms?.let { Gente.sinExpulsados(u, it) }.orEmpty(), efectos = lo?.let { Vida.recorta(u, it.efectos.copy(fuera = emptyMap(), dinero = 0)) } ?: Efectos()))
        fallo
    } }

    // ---------------------------------------------------------------- tras un partido

    /**
     * Guarda el partido (y la jornada: sueldo, bolsa, la fama que da…), caduca las decisiones que no contestaste, saca los eventos que
     * estaban esperando y pide la red y la prensa (que cuenta también lo que dejaste sin contestar). Luego, si toca, manda los dos eventos
     * de entre partidos por lotes y, cada [CADA_MEMORIA] jornadas, rehace lo que FcFeed recuerda. Devuelve la entrada del partido y, si algo
     * no llegó, por qué.
     */
    fun partido(universo: String, p0: Partido): Pair<Entrada, String?> = synchronized(cerrojo) { en(universo) {
        // Lo que tenías abierto y ya habías visto caduca con el partido nuevo (lo que estaba por salir aún no lo habías visto)
        caduca(universo)
        revela(universo, forzar = true)
        var u = Carreras.de(universo) ?: error("No encuentro esa carrera")
        // (las carreras de antes de la gente nueva: desde ahora, al subir de nivel, entra gente nueva)
        if (u.nivelGente < 0) u = Carreras.guarda(u.copy(nivelGente = u.nivelActual.ordinal))
        // Sin fecha: la del anterior + 7 días (si la hay), marcada como estimada
        val p1 = if (Fechas.lee(p0.fecha) != null) p0 else fechaEstimada(universo)?.let { p0.copy(fecha = Fechas.iso(it), fechaEstimada = true) } ?: p0
        // La competición, la de la lista (si lo que se puso es una variante: «liga» → «LaLiga EA Sports»)
        val canon = Competiciones.sinPreguntar(p1.competicion, Competiciones.lista(u, Carreras.partidos(universo).map { it.second }), u.compAlias)
        val p = if (canon != null && canon != p1.competicion) p1.copy(competicion = canon, competicionOriginal = p1.competicion) else p1
        // Con fecha de nacimiento y fecha del partido, su edad (años cumplidos) es la de ese día
        Fechas.lee(u.personaje.nacimiento)?.let { nac -> Fechas.lee(p.fecha)?.let { f -> Fechas.edad(nac, f).first.takeIf { it in 10..80 && it != u.personaje.edad }?.let { a ->
            u = Carreras.guarda(u.copy(personaje = u.personaje.copy(edad = a))) } } }
        u = Carreras.guarda(Vida.jornada(u, p))
        // Lo que vale, tras este partido (antes de que la prensa escriba: lo sabe)
        val valor0 = Contratos.valorMercado(u)
        u = Carreras.guarda(u.copy(valorAntes = valor0, valor = Contratos.revalora(u, p)))
        var e = Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.PARTIDO, reloj(), u.paso, partido = p, pendiente = "red,prensa"))
        // Los hitos y récords (los de FcFeed, con sus cifras): se apuntan, dan algo de fama y la red y la prensa los cuentan
        val hitos = Hitos.nuevos(u, Carreras.partidos(u.id).map { it.second }, u.paso)
        if (hitos.isNotEmpty()) {
            val ef = Efectos(fuera = mapOf(Fuera.FAMA to hitos.sumOf { Hitos.fama(it) }))
            Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.HITO, reloj() - 1, u.paso, hitos.joinToString(" · ") { it.titulo },
                hitos.joinToString("\n") { it.texto }, efectos = Vida.recorta(u, ef)))
            u = Carreras.guarda(Vida.aplica(u, ef).copy(hitos = u.hitos + hitos))
        }
        // Los récords del fútbol de verdad (los de la lista): batirlo es lo más gordo que le puede pasar
        u = historicos(u)
        // Los rivales: los duelos con su club, la pelea por el puesto… y alguno que nace solo
        u = rivales(u)
        val r = completa(e)
        e = r.first
        // El contrato: si toca hablar de renovar, el club (o tu representante) mueve ficha
        runCatching { revisaContrato(universo) }
        // Lo que ya tenía sobre la mesa sube con su valor
        runCatching { subenOfertas(universo, valor0) }
        // Las pujas entre las ofertas que compiten (sin IA): salen entre jornadas
        runCatching { puja(universo) }
        if (e.pendiente.isEmpty()) {
            runCatching { mandaEventos(Carreras.de(universo) ?: u) }
            if (u.paso % CADA_MEMORIA == 0) runCatching { recuerda(universo) }
        }
        e to r.second
    } }

    /** Si ha subido lo que vale, las ofertas de clubes que tenía abiertas mejoran (sale como «Puja»: «Tu precio se dispara»). */
    fun subenOfertas(universo: String, valorAntes: Long): Entrada? {
        val u = Carreras.de(universo) ?: return null
        val ents = Carreras.entradasDe(universo, todas = true)
        val ratio = Contratos.valorMercado(u).toDouble() / maxOf(1L, valorAntes)
        // (las de esta jornada ya salen con lo que vale ahora)
        val cambios = Contratos.subenOfertas(u, ents.filter { it.paso < u.paso }, ratio)
        if (cambios.isEmpty()) return null
        // Las de antes, superadas (la nueva ocupa su sitio)
        val viejas = cambios.map { it.first.id }.toSet()
        ents.filter { e -> e.ofertas.any { it.id in viejas } }.forEach { e -> Carreras.guarda(e.copy(ofertas = e.ofertas.map { if (it.id in viejas) it.copy(estado = Oferta.MEJORADA) else it })) }
        val pct = Math.round((ratio - 1) * 100)
        return Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.PUJA, reloj(), u.paso, "Tu precio se dispara: suben las ofertas",
            "Tras el partido, lo que vales en el mercado ha subido un $pct % (de ${Formato.eurosCorto(valorAntes)} a ${Formato.eurosCorto(Contratos.valorMercado(u))}). " +
                cambios.joinToString(" ") { (a, b) -> "${Rangos.limpiaNombre(b.de)} mejora la suya: de ${Formato.eurosCorto(a.salario)} a ${Formato.eurosCorto(b.salario)} a la semana" +
                    (if (b.clausula > a.clausula && b.clausula > 0) ", con una cláusula de ${Formato.eurosCorto(b.clausula)}" else "") + "." },
            ofertas = cambios.map { it.second }))
    }

    /** Las ofertas abiertas que se pasan de lo que puede cobrar por su edad (las de antes de la 0.13.1), a su sitio (al abrir la app). */
    fun reajustaOfertas(): Int {
        var n = 0
        for (u in Carreras.todas()) Carreras.entradasDe(u.id, todas = true).forEach { e ->
            val nuevas = e.ofertas.map { o -> if (o.estado == Oferta.ABIERTA && o.tipo != "patrocinio") Contratos.recortaPara(u, o) else o }
            if (nuevas != e.ofertas) { Carreras.guarda(e.copy(ofertas = nuevas)); n++ }
        }
        return n
    }

    /** La puja de esta jornada entre las ofertas que compiten ([Pujas]), si toca y no hay otra por salir. Sale entre jornadas. */
    fun puja(universo: String): Entrada? {
        val u = Carreras.de(universo) ?: return null
        if (Carreras.entradasDe(universo, todas = true).any { it.tipo == TipoEntrada.PUJA && !it.aplicada }) return null
        val ahora = reloj()
        val e = Pujas.calcula(u, Carreras.entradasDe(universo, ahora = ahora), ahora, if (Prueba.activo) ahora else ahora + separacion / 2) ?: return null
        return Carreras.guarda(e)
    }

    /** Los rivales tras el partido: cambian con los choques y, si nace uno, sale como «Rivalidad» (la prensa y la red lo cuentan). */
    private fun rivales(u0: Universo): Universo {
        val t = runCatching { Rivales.trasPartido(u0, Carreras.partidos(u0.id).map { it.second }) }.getOrNull() ?: return u0
        val u = Carreras.guarda(u0.copy(rivales = t.rivales))
        t.nacidos.forEach { r ->
            Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.RIVAL, reloj() - 3, u.paso, "Nace una rivalidad: ${r.nombre} (${r.club})",
                "${r.origen}. ${r.nombre}, ${r.posicion.ifBlank { "jugador" }} del ${r.club}, ya está en tu lista de rivales (intensidad ${r.intensidad}/100)."))
        }
        return u
    }

    /** La fecha que tendría el próximo partido si no la pones: la del último con fecha + 7 días. */
    fun fechaEstimada(universo: String): java.time.LocalDate? =
        Carreras.partidos(universo).map { it.second }.lastOrNull { Fechas.lee(it.fecha) != null }?.let { Fechas.lee(it.fecha)!!.plusDays(7) }

    /**
     * Compara su carrera con los récords reales ([Historicos]) tras el último partido: lo que bate o iguala sale como «Récord histórico»
     * (con su fama, sin el tope de la jornada) y pasa a ser suyo; lo que roza, como hito. Todo queda en sus hitos, con la línea de cifras
     * que se le pasa a Claude.
     */
    private fun historicos(u0: Universo): Universo {
        var u = u0
        val ps = Carreras.partidos(u.id).map { it.second }
        // Lo que bate, iguala o roza con este partido, y los de precocidad que se le acaban de escapar (el plazo ha pasado)
        val cs = runCatching { Historicos.nuevos(u, ps) }.getOrDefault(emptyList()) + runCatching { Historicos.escapados(u, ps) }.getOrDefault(emptyList())
        if (cs.isEmpty()) return u
        val t = ps.lastOrNull()?.temporada ?: u.temporada
        var suyos = u.recordsSuyos
        val hitos = mutableListOf<Hito>()
        var fama = 0
        for (c in cs) {
            if (c.estado == Historicos.Estado.BATE || c.estado == Historicos.Estado.IGUALA)
                suyos = suyos.filter { it.n != c.r.n } + RecordSuyo(c.r.n, c.suValor, c.suMarca, u.paso, t, c.estado == Historicos.Estado.IGUALA)
            if (c.texto.isBlank()) continue
            fama += Historicos.fama(u, c)
            val titulo = when (c.estado) {
                Historicos.Estado.BATE -> if (c.contraSuyo) "Mejora su récord: ${c.r.record}" else "Récord histórico: ${c.r.record}"
                Historicos.Estado.IGUALA -> "Iguala un récord histórico: ${c.r.record}"
                Historicos.Estado.ESCAPA -> "Se le escapa un récord histórico: ${c.r.record}"
                else -> "Roza un récord histórico: ${c.r.record}"
            }
            hitos += Hito("real_${c.r.n}_${c.estado.name.lowercase()}_${u.paso}", titulo, c.texto, u.paso, u.personaje.edad, c.r.tipo == TipoRecord.JOVEN && c.estado != Historicos.Estado.CERCA && c.estado != Historicos.Estado.ESCAPA, t, c.r.n, c.linea)
        }
        if (hitos.isNotEmpty()) {
            val grandes = hitos.filter { !it.id.contains("_cerca_") && !it.id.contains("_escapa_") }
            val e = Entrada(Universo.nuevoId("e"), u.id, if (grandes.isNotEmpty()) TipoEntrada.RECORD else TipoEntrada.HITO, reloj() - 2, u.paso,
                (grandes.ifEmpty { hitos }).joinToString(" · ") { it.titulo }, hitos.joinToString("\n\n") { it.texto },
                efectos = Efectos(fuera = mapOf(Fuera.FAMA to fama)))
            Carreras.guarda(e)
        }
        // La fama de un récord no respeta el tope de la jornada (es lo más gordo que le puede pasar)
        val f0 = u.valorFuera(Fuera.FAMA)
        u = Carreras.guarda(u.copy(recordsSuyos = suyos, hitos = u.hitos + hitos, fuera = u.fuera + (Fuera.FAMA to (f0 + fama).coerceIn(0, 100))))
        return u
    }

    // ---------------------------------------------------------------- el contrato

    /** Los partidos de la temporada de ahora. */
    private fun partidosTemporada(u: Universo) = Carreras.partidos(u.id).map { it.second }.filter { u.temporada.isBlank() || it.temporada == u.temporada }

    /**
     * Si toca hablar de renovar (le queda poco o su nivel ya está muy por encima de lo que cobra), el club le hace una oferta de renovación
     * (o, si es por el nivel, su representante le pide permiso para renegociar). Devuelve lo que ha salido, si ha salido algo.
     */
    fun revisaContrato(universo: String): Entrada? {
        val u = Carreras.de(universo) ?: return null
        val c = u.contrato ?: return null
        if (Carreras.entradasDe(universo, todas = true).any { e -> e.ofertas.any { it.tipo == "renovacion" && it.estado == Oferta.ABIERTA } || e.decision?.let { it.abierta && it.accion == "renegociar" } == true }) return null
        val motivo = Contratos.motivoRenovacion(u, partidosTemporada(u).size) ?: return null
        Carreras.guarda(u.copy(renovacionPaso = u.paso))
        val rep = u.entorno.firstOrNull { it.rol.contains("representante", true) }?.nombre ?: "Tu representante"
        return if (motivo.startsWith("tu nivel") && cabeDecision(universo)) {
            val r = Rangos.de(u.nivelActual).sueldo
            Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.EVENTO, reloj(), u.paso, "$rep quiere renegociar",
                "$rep te ha llamado con los números delante: cobras ${Formato.euros(c.sueldo)} a la semana y alguien de tu nivel (${u.nivelActual.nombre.lowercase()}) cobra entre ${Formato.euros(r.first)} y ${Formato.euros(r.last)}. " +
                    "«Te están pagando como a un chaval del filial», te dice. Quiere pedirle al ${c.club} que te mejore el contrato ya, aunque te quedan ${Contratos.quedan(c, u.temporada)} temporadas. " +
                    "Si lo pide y el club no quiere, puede haber tensión; si esperas, el precio puede subir… o bajar.",
                decision = Decision(Universo.nuevoId("d"), rep, "¿Le dejas pedir la renovación?", listOf("Que la pida ya al club", "Esperar a final de temporada", "No tocar nada: firmé lo que firmé"),
                    titulo = "$rep quiere renegociar", enJuego = "Lo que cobras, tu relación con el club y con $rep", accion = "renegociar")))
        } else ofreceRenovacion(u, motivo.replaceFirstChar { it.uppercase() } + ", y el club ha llamado a tu representante para sentarse a hablar.")
    }

    private fun ofreceRenovacion(u: Universo, porque: String): Entrada {
        val o = Contratos.propuesta(u, partidosTemporada(u))
        return Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.EVENTO, reloj(), u.paso, "El ${u.club} quiere renovarte",
            "$porque ${o.texto} Puedes aceptarla, pedir más (una vez) o rechazarla y esperar: si el contrato se acaba sin renovar, quedarás libre.",
            ofertas = listOf(o)))
    }

    /** Pedir más a una renovación: el club sube o se planta (una vez). Devuelve qué ha pasado. */
    fun contraoferta(entrada: String, oferta: String): String? = synchronized(cerrojo) {
        val e = Carreras.entrada(entrada) ?: return null
        val o = e.ofertas.firstOrNull { it.id == oferta && it.estado == Oferta.ABIERTA && !it.negociada } ?: return null
        val u = Carreras.de(e.universo) ?: return null
        val (nueva, sube) = Contratos.contraoferta(u, o)
        Carreras.guarda(e.copy(ofertas = e.ofertas.map { if (it.id == o.id) it.copy(estado = Oferta.NEGOCIADA) else it } + nueva.copy(negociada = true)))
        if (sube) "El club ha subido su oferta." else "El club no se mueve."
    }

    /** Las decisiones abiertas que ya habías visto: caducan (lo que pase por no contestar lo cuenta la prensa del partido siguiente). */
    fun caduca(universo: String): Int {
        val ahora = reloj()
        val abiertas = Carreras.entradasDe(universo, todas = true, ahora = ahora).filter { it.aplicada && it.visible(ahora) && it.decision?.abierta == true }
        abiertas.forEach { e -> Carreras.guarda(e.copy(decision = e.decision!!.copy(estado = Decision.CADUCADA))) }
        return abiertas.size
    }

    /** Las caducadas que aún no tienen su consecuencia. */
    private fun sinContar(universo: String) = Carreras.entradasDe(universo, todas = true).filter { it.decision?.caducada == true && it.decision.consecuencia.isBlank() }
        .sortedBy { it.creado }

    /** Pide lo que le falta a un partido (la red, la prensa o las dos). */
    fun completa(e0: Entrada): Pair<Entrada, String?> = en(e0.universo) {
        var e = e0
        var fallo: String? = null
        val p = e.partido ?: return@en e to null
        if ("red" in e.pendiente) {
            try {
                val u = Carreras.de(e.universo) ?: return@en e to null
                // Gente nueva: al subir de nivel (o tras batir un récord histórico), entra gente en su vida y le escribe por primera vez
                val nuevas = when {
                    Gente.tocaNueva(u) -> maxOf(1, Gente.cuantas(u.nivelActual))
                    u.hitos.any { it.paso == u.paso && it.real > 0 && it.id.contains("_bate_") } -> 1
                    else -> 0
                }
                val r = Narrador.leeRed(pide(Narrador.red(u, p, contexto(u), nuevas)))
                val extra = if (nuevas > 0) entraGente(u, r.nuevas, nuevas) else emptyList()
                e = Carreras.guarda(e.copy(posts = r.posts, dms = Gente.sinExpulsados(u, r.dms) + extra, tendencia = r.tendencia, tendenciaPosts = r.tendenciaPosts,
                    pendiente = e.pendiente.split(',').filter { it != "red" }.joinToString(",")))
            } catch (x: Exception) { fallo = mensaje(x) }
        }
        if ("prensa" in e.pendiente && fallo == null) {
            try {
                val u = Carreras.de(e.universo) ?: return@en e to null
                val caducadas = sinContar(u.id)
                val r = Narrador.leePrensa(pide(Narrador.prensa(u, p, contexto(u), caducadas)), Contratos.nivelOfertas(u), Contratos.topeSueldo(u))
                var nu = Vida.aplica(u, r.efectos)
                val ef = Vida.recorta(u, r.efectos)
                // Lo que no contestó: su consecuencia (la que cuenta la prensa, o una corta si no la cuenta) y sus efectos
                caducadas.forEachIndexed { i, c ->
                    val d = c.decision ?: return@forEachIndexed
                    val sc = r.sinContestar.firstOrNull { it.n == i + 1 }
                    val txt = sc?.consecuencia ?: "Lo dejaste pasar y nadie volvió a sacar el tema… de momento."
                    val efc = sc?.efectos?.let { Vida.recorta(nu, it) } ?: Efectos()
                    if (sc != null) nu = Vida.aplica(nu, sc.efectos)
                    Carreras.guarda(c.copy(decision = d.copy(consecuencia = txt)))
                    Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.SINCONTESTAR, reloj() - 1, u.paso, d.titulo.ifBlank { c.titulo }.ifBlank { d.texto }, txt, efectos = efc))
                }
                Carreras.guarda(nu)
                val dec = r.decision?.takeIf { cabeDecision(u.id) }
                e = Carreras.guarda(e.copy(prensa = r.prensa, ofertas = r.ofertas, decision = dec, efectos = ef,
                    pendiente = e.pendiente.split(',').filter { it != "prensa" }.joinToString(",")))
            } catch (x: Exception) { fallo = mensaje(x) }
        }
        e to fallo
    }

    /**
     * Entra gente nueva (la que propone Claude o, si no trae a nadie, la de las plantillas de su nivel): se apunta en su gente con la
     * jornada en que llega y sale como «Gente nueva». Devuelve los mensajes de presentación de las de las plantillas (los de Claude ya vienen).
     */
    private fun entraGente(u: Universo, propuestas: List<Persona>, n: Int): List<Dm> {
        val paso = maxOf(1, u.paso)
        val extra = ArrayList<Dm>()
        var nuevas = propuestas.map { it.copy(desde = paso) }.take(n)
        if (nuevas.isEmpty()) nuevas = Gente.plantillas(u, u.nivelActual, n).map { t ->
            val q = Persona(Universo.nuevoId("p"), t.nombre, t.rol, 50, t.nota, desde = paso)
            extra += t.mensajes.map { Dm(q.nombre, it, chat = q.nombre, pid = q.id) }
            q
        }
        var nu = Gente.anade(u, nuevas, n)
        val entraron = nu.entorno.filter { x -> u.entorno.none { it.id == x.id } }
        nu = Carreras.guarda(nu.copy(nivelGente = maxOf(nu.nivelGente, nu.nivelActual.ordinal)))
        if (entraron.isNotEmpty()) Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.GENTE, reloj() - 1, nu.paso,
            if (entraron.size == 1) "Entra en tu vida: ${entraron.first().nombre}" else "Gente nueva en tu vida",
            entraron.joinToString("\n") { "${it.nombre} (${it.rol}): ${it.nota.ifBlank { "te ha escrito por primera vez" }}." } +
                "\nCon tu nivel de ahora (${nu.nivelActual.nombre.lowercase()}) te escribe otra gente. Si alguien no te interesa, en su chat puedes darle puerta."))
        return extra.filter { d -> entraron.any { it.id == d.pid } }
    }

    fun reintenta(id: String): String? = synchronized(cerrojo) {
        val e = Carreras.entrada(id) ?: return null
        val r = completa(e)
        if (r.first.pendiente.isEmpty() && r.first.partido != null) runCatching { Carreras.de(e.universo)?.let { mandaEventos(it) } }
        r.second
    }

    // ---------------------------------------------------------------- entre partidos

    /** Los dos eventos de entre partidos, por lotes (a mitad de precio). Si ya hay un lote de esta jornada, nada. */
    fun mandaEventos(u: Universo) {
        if (!Gasto.eventosSolos() || !u.autoEventos || !ClaveApi.hay) return
        if (u.lotes.any { it.paso == u.paso }) return
        en(u.id) {
            val ctx = contexto(u)
            val ps = listOf(1, 2).map { i -> "${u.id}_${u.paso}_$i" to Narrador.evento(u, ctx, i, enLote = true) }
            Gasto.compruebaAntes(ps.sumOf { Precios.coste(it.second.modelo, it.second.usoMaximo(), lote = true, cacheHora = true) })
            val id = claude.creaLote(ps)
            val nu = Carreras.de(u.id) ?: u
            Carreras.guarda(nu.copy(lotes = nu.lotes + Lote(id, reloj(), u.paso)))
        }
        // En modo prueba, el lote «vuelve» al momento
        if (Prueba.activo) { miraLotes(); revela(u.id) }
    }

    /** Mira los lotes que faltan; los que han vuelto, a su sitio (el primero sale ya y el segundo, horas después). Devuelve cuántos eventos llegaron. */
    fun miraLotes(): Int = synchronized(cerrojo) {
        var llegados = 0
        for (u0 in Carreras.todas()) for (l in u0.lotes) en(u0.id) {
            val est = try { claude.lote(l.id) } catch (e: Claude.Fallo) { if (e.reintentable) return@en else { quitaLote(u0.id, l.id); return@en } }
            if (est.estado != "ended") {
                // (si en dos días no ha vuelto, se da por perdido: la API los caduca a las 24 horas)
                if (reloj() - l.creado > 48 * 3600_000L) quitaLote(u0.id, l.id)
                return@en
            }
            val res = try { claude.resultados(l.id, est.resultados) } catch (e: Claude.Fallo) { if (e.reintentable) return@en else emptyList() }
            val u = Carreras.de(u0.id) ?: return@en
            val ahora = reloj()
            var abiertas = Carreras.entradasDe(u.id, ahora = ahora).count { it.decision?.abierta == true }
            res.sortedBy { it.id }.forEachIndexed { i, r ->
                val resp = r.respuesta ?: return@forEachIndexed
                Gasto.apunta(resp.modelo.ifBlank { Claude.SONNET }, Precios.coste(resp.modelo.ifBlank { Claude.SONNET }, resp.uso, lote = true, cacheHora = true))
                @Suppress("UNCHECKED_CAST") val m = runCatching { SinJuego.limpia(resp.json()) as Map<String, Any?> }.getOrNull() ?: return@forEachIndexed
                // Si ya metiste otro partido, salen ya: se quedaron atrás (en modo prueba, los dos salen ya: no hay que esperar horas para ver cómo va)
                val visible = if (u.paso > l.paso || Prueba.activo) ahora else ahora + i * separacion
                var ev = Narrador.leeEvento(m, u, ahora, visible).let { it.copy(paso = l.paso, dms = Gente.sinExpulsados(u, it.dms)) }
                if (ev.decision != null) { if (abiertas >= Narrador.MAX_ABIERTAS) ev = ev.copy(decision = null) else abiertas++ }
                Carreras.guarda(ev)
                llegados++
            }
            quitaLote(u.id, l.id)
        }
        llegados
    }

    private fun quitaLote(universo: String, lote: String) {
        val u = Carreras.de(universo) ?: return
        Carreras.guarda(u.copy(lotes = u.lotes.filter { it.id != lote }))
    }

    /** Los eventos cuya hora ha llegado: sus efectos cuentan desde ya. Devuelve los que acaban de salir. */
    fun revela(universo: String? = null, forzar: Boolean = false): List<Entrada> = synchronized(cerrojo) {
        val ahora = reloj()
        val salen = ArrayList<Entrada>()
        for (u0 in Carreras.todas().filter { universo == null || it.id == universo }) {
            var u = u0
            Carreras.entradasDe(u.id, todas = true, ahora = ahora).filter { !it.aplicada && (forzar || it.visible(ahora)) }.sortedBy { it.visibleDesde }.forEach { e0 ->
                // Una puja: la oferta de antes queda superada o retirada (si aún estaba abierta)
                val e = Pujas.aplica(e0, Carreras.entradasDe(u.id, todas = true, ahora = ahora))?.let { (pe, vieja) -> vieja?.let { Carreras.guarda(it) }; pe } ?: e0
                val ef = Vida.recorta(u, e.efectos)
                u = Vida.aplica(u, e.efectos)
                salen += Carreras.guarda(e.copy(aplicada = true, efectos = ef, visibleDesde = minOf(e.visibleDesde, ahora)))
            }
            if (u !== u0) Carreras.guarda(u)
        }
        salen
    }

    /** Pedir un evento ahora («¿Qué ha pasado esta semana?»): sin lote, al momento (a precio normal). */
    fun eventoAhora(universo: String): Pair<Entrada?, String?> = synchronized(cerrojo) { en(universo) {
        val u = Carreras.de(universo) ?: return@en null to null
        try {
            val m = pide(Narrador.evento(u, contexto(u), 0))
            var ev = Narrador.leeEvento(m, u, reloj(), reloj()).let { it.copy(dms = Gente.sinExpulsados(u, it.dms)) }
            if (ev.decision != null && !cabeDecision(universo)) ev = ev.copy(decision = null)
            val e = Carreras.guarda(ev)
            revela(universo)
            (Carreras.entrada(e.id) ?: e) to null
        } catch (x: Exception) { null to mensaje(x) }
    } }

    // ---------------------------------------------------------------- los hechos históricos

    /** Lo que la app entiende de un hecho histórico que cuentas (Haiku, barato), para que lo confirmes antes de mandarlo. */
    fun entiende(universo: String, texto: String): Pair<Narrador.LoEntendido?, String?> = en(universo) {
        val u = Carreras.de(universo) ?: return@en null to "Esta carrera ya no está."
        try { Narrador.leeEntendido(pide(Narrador.entender(u, texto))).let { if (it.hechos.isEmpty()) null to "No he entendido qué ha pasado: cuéntalo de otra manera." else it to null } }
        catch (x: Exception) { null to mensaje(x) }
    }

    /**
     * Un hecho histórico: se comprueba en la web lo de precocidad (si lo hay), Sonnet escribe la edición especial y el mundo reacciona.
     * Con [Ruido.PAIS] o [Ruido.MUNDO] la fama salta de golpe (sin el tope de la jornada) hasta el nivel en que le deja la historia, con un
     * mínimo; con [Ruido.CLUB], como siempre. Queda en la carrera para siempre (va en el contexto de todo lo que se escriba después).
     */
    fun hecho(universo: String, texto: String, e: Narrador.LoEntendido, ruido: Ruido): Pair<Entrada?, String?> = synchronized(cerrojo) { en(universo) {
        var u = Carreras.de(universo) ?: return@en null to "Esta carrera ya no está."
        try {
            // Lo de precocidad, en la web (si falla, se sigue sin ello y se dice)
            val comp = if (e.hayPrecocidad) try { Hechos.comprueba(e.pregunta) } catch (x: Exception) { Hechos.Comprobacion(null, "No se pudo comprobar: " + mensaje(x), emptyList()) } else null
            val f0 = u.valorFuera(Fuera.FAMA)
            var h = Hecho(texto.trim(), e.hechos.map { (a, b) -> if (b.isBlank()) a else "$a ($b)" }, ruido.name, f0, f0, e.precocidad,
                comp?.texto.orEmpty(), comp?.hay, comp?.fuentes.orEmpty())
            val r = Narrador.leeEdicion(pide(Narrador.edicion(u, contexto(u), h, ruido)))
            // La fama: de golpe hasta el nivel en que le deja (con un mínimo), o como siempre si solo llega a su club
            val sinFama = r.efectos.copy(fuera = r.efectos.fuera - Fuera.FAMA)
            var nu = Vida.aplica(u, sinFama)
            val f1 = if (ruido.sinTope) {
                val n0 = u.nivelActual
                val n1 = (r.nivelTras ?: n0).let { if (it.ordinal < n0.ordinal) n0 else it }.let { if (it.ordinal > ruido.tope.ordinal) ruido.tope else it }
                maxOf(f0 + ruido.minimo, if (n1.ordinal > n0.ordinal) n1.desde + 3 else f0).coerceAtMost(100)
            } else Vida.aplica(nu, Efectos(fuera = mapOf(Fuera.FAMA to 5))).valorFuera(Fuera.FAMA)
            nu = nu.copy(fuera = nu.fuera + (Fuera.FAMA to f1), famaJornada = maxOf(nu.famaJornada, f1 - f0), famaHist = (nu.famaHist + (nu.paso to f1)).takeLast(80))
            h = h.copy(famaDespues = f1)
            // Lo de precocidad comprobado: queda en sus hitos
            if (h.precocidad.isNotBlank() && comp?.hay != null)
                nu = nu.copy(hitos = nu.hitos + Hito("hecho_${reloj()}", "Precocidad: ${h.precocidad}", h.comprobado, nu.paso, nu.personaje.edad, precoz = true, temporada = nu.temporada))
            nu = Carreras.guarda(nu)
            val ef = Vida.recorta(u, sinFama).let { it.copy(fuera = it.fuera + (Fuera.FAMA to (f1 - f0))) }
            // Gente nueva: la que trae la historia y, si ha subido de nivel, la de su nivel nuevo
            val extra = when {
                r.nuevas.isNotEmpty() -> entraGente(nu, r.nuevas, r.nuevas.size)
                Gente.tocaNueva(nu) -> entraGente(nu, emptyList(), maxOf(1, Gente.cuantas(nu.nivelActual)))
                else -> emptyList()
            }
            nu = Carreras.de(universo) ?: nu
            val ofertas = r.ofertas.map { Contratos.recortaPara(nu, it) }
            val ent = Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.HISTORICO, reloj(), nu.paso,
                r.titulo.ifBlank { r.prensa?.titular.orEmpty() }.ifBlank { "Hecho histórico" }, r.texto, null, r.posts, Gente.sinExpulsados(nu, r.dms) + extra,
                r.prensa, ofertas, r.decision?.takeIf { cabeDecision(universo) }, ef, tendencia = r.tendencia, tendenciaPosts = r.tendenciaPosts, hecho = h))
            runCatching { recuerda(universo) }
            ent to null
        } catch (x: Exception) { null to mensaje(x) }
    } }

    // ---------------------------------------------------------------- lo que FcFeed recuerda

    /** Rehace lo que FcFeed recuerda de la carrera (Sonnet, corto). Devuelve por qué no se pudo, si no se pudo. */
    fun recuerda(universo: String): String? = en(universo) {
        val u = Carreras.de(universo) ?: return@en null
        try {
            val m = Narrador.leeMemoria(pide(Narrador.memoria(u, contexto(u, cuantas = 16))))
            if (m.isNotBlank()) { val nu = Carreras.de(universo) ?: u; Carreras.guarda(nu.copy(memoria = m, memoriaPaso = nu.paso)) }
            null
        } catch (x: Exception) { mensaje(x) }
    }

    // ---------------------------------------------------------------- el cierre de temporada

    /** «2026/27» → «2027/28»; «2026» → «2027». */
    fun siguienteTemporada(t: String): String {
        Regex("""^(\d{4})([/-])(\d{2,4})$""").find(t.trim())?.let { m ->
            val a = m.groupValues[1].toInt() + 1
            val b = m.groupValues[3]
            return "$a${m.groupValues[2]}" + if (b.length == 2) "%02d".format((a + 1) % 100) else "${a + 1}"
        }
        Regex("""^(\d{4})$""").find(t.trim())?.let { return "${it.groupValues[1].toInt() + 1}" }
        return t
    }

    /**
     * Cierra la temporada: el balance (con tus cifras), los premios (de medios inventados), las vacaciones con su decisión y el mercado de
     * verano; luego pasa a la temporada siguiente (y un año más) y rehace lo que FcFeed recuerda. Devuelve por qué no se pudo, si no se pudo.
     */
    fun cierraTemporada(universo: String): String? = synchronized(cerrojo) { en(universo) {
        val u = Carreras.de(universo) ?: return@en null
        val t = u.temporada
        if (t.isNotBlank() && t in u.cerradas) return@en "La temporada $t ya está cerrada."
        val ps = Carreras.partidos(u.id).map { it.second }.filter { it.temporada == t || t.isBlank() }
        if (ps.isEmpty()) return@en "No hay partidos en esta temporada: mete alguno antes de cerrarla."
        try {
            val r = Narrador.leeTemporada(pide(Narrador.temporada(u, contexto(u, cuantas = 12), Estadisticas.linea(t, "", ps), ps)), Contratos.nivelOfertas(u), Contratos.topeSueldo(u))
            val ef = Vida.recorta(u, r.efectos)
            var nu = Vida.aplica(u, r.efectos)
            val ahora = reloj()
            Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.TEMPORADA, ahora, u.paso, r.titulo.ifBlank { "Cierre de la temporada $t" }, r.balance,
                posts = r.posts, dms = Gente.sinExpulsados(u, r.dms), prensa = r.prensa, ofertas = r.ofertas, efectos = ef, tendencia = r.tendencia, tendenciaPosts = r.tendenciaPosts, premios = r.premios))
            if (r.vacacionesTexto.isNotBlank()) Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.EVENTO, ahora + 1, u.paso, r.vacacionesTitulo.ifBlank { "Vacaciones" },
                r.vacacionesTexto, decision = r.vacacionesDecision?.takeIf { cabeDecision(u.id) }))
            nu = (Carreras.de(u.id) ?: nu).let { x -> x.copy(dinero = nu.dinero, fuera = nu.fuera, entorno = nu.entorno, exEntorno = nu.exEntorno, famaJornada = nu.famaJornada, famaHist = nu.famaHist) }
            nu = nu.copy(temporada = siguienteTemporada(t), cerradas = (nu.cerradas + t).filter { it.isNotBlank() }.distinct(), personaje = nu.personaje.copy(edad = nu.personaje.edad + 1))
            // El contrato que se acaba: libre (o, si era una cesión, de vuelta a su club)
            val c = u.contrato
            if (c != null && Contratos.quedan(c, t) <= 1) {
                val origen = c.nota.removePrefix("Cedido por ").takeIf { c.nota.startsWith("Cedido por ") }
                if (origen != null) {
                    val vuelta = Contratos.primero(nu.nivelActual, origen, nu.temporada, nu.modo).copy(sueldo = maxOf(c.sueldo, Rangos.de(nu.nivelActual).sueldo.first))
                    nu = nu.copy(club = origen, contrato = vuelta, salario = vuelta.sueldo + nu.patrocinio)
                    Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.OFICIAL, ahora + 2, u.paso, "Vuelves al $origen", "Se acaba la cesión en el ${c.club}: vuelves al $origen con ${Formato.euros(vuelta.sueldo)} a la semana hasta la temporada ${vuelta.hasta}.\nHazlo también en tu partida de FC."))
                } else {
                    nu = nu.copy(contrato = null, salario = nu.patrocinio)
                    Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.OFICIAL, ahora + 2, u.paso, "Eres libre", "Se acaba tu contrato con el ${c.club} y no has renovado: eres agente libre. Ya no cobras sueldo de ningún club; las ofertas del verano son tu futuro.\nHazlo también en tu partida de FC."))
                }
            }
            Carreras.guarda(nu)
            runCatching { recuerda(u.id) }
            null
        } catch (x: Exception) { mensaje(x) }
    } }

    // ---------------------------------------------------------------- lo que decides

    /** Contestar a una decisión (una de sus opciones o lo que tú quieras): Claude cuenta lo que pasa y sus efectos cuentan. */
    fun decide(entrada: String, eleccion: String): String? = synchronized(cerrojo) {
        val e = Carreras.entrada(entrada) ?: return null
        val d = e.decision?.takeIf { it.abierta } ?: return null
        en(e.universo) {
            val u = Carreras.de(e.universo) ?: return@en null
            val t = eleccion.trim().ifBlank { return@en "Escribe qué haces o elige una opción." }
            try {
                val r = Narrador.leeRespuesta(pide(Narrador.respuesta(u, contexto(u), e, d, t)))
                Carreras.guarda(e.copy(decision = d.copy(elegida = t, consecuencia = r.texto, estado = Decision.RESUELTA)))
                val ef = Vida.recorta(u, r.efectos)
                Carreras.guarda(Vida.aplica(u, r.efectos))
                Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.RESPUESTA, reloj(), u.paso, r.titular.ifBlank { "Lo que pasó después" }, r.texto,
                    posts = r.posts, dms = listOf(Dm("Tú", t, mio = true, chat = Gente.chatDeNombre(u, d.de))) + Gente.sinExpulsados(u, r.dms), efectos = ef, tendencia = r.tendencia, tendenciaPosts = r.tendenciaPosts))
                // Si era pedir la renovación y dijo que sí, el club hace su oferta
                if (d.accion == "renegociar" && t == d.opciones.firstOrNull()) Carreras.de(u.id)?.let { ofreceRenovacion(it, "Tu representante lo pidió y el club ha contestado con una oferta.") }
                null
            } catch (x: Exception) { mensaje(x) }
        }
    }

    /** Aceptar o rechazar una oferta. Al aceptar: cambia tu carrera, se cae lo demás de esa vez y la red reacciona (si se puede). */
    fun oferta(entrada: String, oferta: String, acepta: Boolean): String? = synchronized(cerrojo) {
        val e = Carreras.entrada(entrada) ?: return null
        val o = e.ofertas.firstOrNull { it.id == oferta && it.estado == Oferta.ABIERTA } ?: return null
        val u = Carreras.de(e.universo) ?: return null
        val nuevas = e.ofertas.map { x ->
            when {
                x.id == o.id -> x.copy(estado = if (acepta) Oferta.ACEPTADA else Oferta.RECHAZADA)
                acepta && o.cambiaDeClub && x.cambiaDeClub && x.estado == Oferta.ABIERTA -> x.copy(estado = Oferta.RECHAZADA)
                else -> x
            }
        }
        Carreras.guarda(e.copy(ofertas = nuevas))
        if (!acepta) return null
        // (al irse a otro club, el excompañero con el que no acabó bien se queda allí y empieza la rivalidad)
        val nu = Carreras.guarda(Vida.acepta(u, o).let { n -> if (o.cambiaDeClub && !Vida.mismo(n.club, u.club)) Rivales.excompanero(n, u.club) else n })
        val titulo = when (o.tipo) {
            "traspaso" -> "Oficial: ficha por ${o.de}"; "cesion" -> "Oficial: cedido al ${o.de}"; "banquillo" -> "Oficial: nuevo entrenador del ${o.de}"
            "renovacion" -> "Oficial: renueva con ${o.de}"; else -> "Oficial: acuerdo con ${o.de}"
        }
        val texto = listOfNotNull(o.texto.ifBlank { null }, o.salario.takeIf { it > 0 }?.let { "${Formato.euros(it)} a la semana" }, o.prima.takeIf { it > 0 }?.let { "prima de ${Formato.euros(it)}" },
            o.anios.takeIf { it > 0 }?.let { "$it año${if (it > 1) "s" else ""}" }).joinToString(" · ")
        var of = Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.OFICIAL, reloj(), u.paso, titulo, texto + "\nHazlo también en tu partida de FC."))
        val fallo = en(u.id) {
            try {
                val r = Narrador.leeRed(pide(Narrador.oficial(nu, contexto(nu), o)))
                of = Carreras.guarda(of.copy(posts = r.posts, dms = Gente.sinExpulsados(nu, r.dms), tendencia = r.tendencia, tendenciaPosts = r.tendenciaPosts)); null
            } catch (x: Exception) { mensaje(x) }
        }
        // Club nuevo: se busca qué club es (queda esperando a que digas «Sí, es este»; si no se puede, lo pides tú desde Inicio o Perfil)
        if (o.cambiaDeClub && !Vida.mismo(nu.club, u.club) && ClaveApi.hay) runCatching { identifica(nu.id) }
        return fallo
    }

    // ---------------------------------------------------------------- el club de verdad

    /** Busca en la web qué club es el de la carrera (queda por confirmar). Devuelve por qué no, si no se pudo. */
    fun identifica(universo: String, precisa: String = ""): String? {
        val u = Carreras.de(universo) ?: return null
        return try {
            val c = Clubes.identifica(u.club, u.liga, precisa)
            Carreras.guarda((Carreras.de(universo) ?: u).copy(clubInfo = c)); null
        } catch (e: Clubes.NoEncontrado) { e.message } catch (e: Exception) { mensaje(e) }
    }

    /** «Sí, es este»: el club queda identificado (y su liga, si no la habías puesto). */
    fun confirmaClub(universo: String, c: InfoClub? = null) {
        val u = Carreras.de(universo) ?: return
        val info = (c ?: u.clubInfo ?: return).copy(confirmado = true)
        // Sus competiciones (las de la búsqueda) pasan a ser las del desplegable, y los partidos de antes se agrupan con ellas (sin gastar nada)
        val cs = if (info.competiciones.isNotEmpty()) info.competiciones + u.comps.filter { c0 -> info.competiciones.none { Competiciones.norm(it) == Competiciones.norm(c0) } && Vida.mismo(u.compsDe, u.club) } else u.comps
        Carreras.guarda(u.copy(clubInfo = info, liga = u.liga.ifBlank { info.liga }, comps = cs, compsDe = if (info.competiciones.isNotEmpty()) u.club else u.compsDe))
        if (info.competiciones.isNotEmpty()) normalizaCompeticiones(universo, conIa = false)
    }

    // ---------------------------------------------------------------- las competiciones

    /** ¿Faltan las competiciones de su club de ahora? (para ofrecer «Buscar sus competiciones»). */
    fun faltanCompeticiones(u: Universo): Boolean = u.comps.isEmpty() || (u.compsDe.isNotBlank() && !Vida.mismo(u.compsDe, u.club))

    /** Busca en la web qué competiciones juega su club y las pone en el desplegable (y agrupa los partidos de antes). Devuelve por qué no, si no se pudo. */
    fun buscaCompeticiones(universo: String): String? {
        val u = Carreras.de(universo) ?: return null
        return try {
            val club = u.clubInfo?.takeIf { it.es(u.club) }?.nombre ?: u.club
            val (l, _) = Clubes.competiciones(club, u.liga)
            val nu = Carreras.de(universo) ?: u
            Carreras.guarda(nu.copy(comps = l + nu.comps.filter { c0 -> l.none { Competiciones.norm(it) == Competiciones.norm(c0) } && Vida.mismo(nu.compsDe, nu.club) },
                compsDe = nu.club, clubInfo = nu.clubInfo?.let { if (it.es(nu.club)) it.copy(competiciones = l) else it }))
            normalizaCompeticiones(universo, conIa = true)
            null
        } catch (e: Exception) { mensaje(e) }
    }

    /** «Otra…»: una competición que no estaba (se añade a la lista). Devuelve cómo queda escrita. */
    fun anadeCompeticion(universo: String, nombre: String): String {
        val u = Carreras.de(universo) ?: return nombre.trim()
        val t = Competiciones.bonita(nombre)
        if (t.isBlank()) return t
        val ya = Competiciones.lista(u).firstOrNull { Competiciones.norm(it) == Competiciones.norm(t) }
        if (ya != null) return ya
        Carreras.guarda(u.copy(comps = u.comps + t, compsDe = u.compsDe.ifBlank { u.club }))
        return t
    }

    /** Corregir un partido ya guardado: su competición (y si fue el MVP). */
    fun corrigePartido(entrada: String, competicion: String, mvp: Boolean? = null) {
        val e = Carreras.entrada(entrada) ?: return
        val p = e.partido ?: return
        val c = competicion.trim().ifBlank { p.competicion }
        Carreras.guarda(e.copy(partido = p.copy(competicion = c, competicionOriginal = p.competicionOriginal.ifBlank { if (c != p.competicion) p.competicion else "" }, mvp = mvp ?: p.mvp)))
    }

    /**
     * Agrupa las competiciones de los partidos ya guardados con las de la lista: primero por reglas (gratis); lo que no encaje y aún no se
     * haya preguntado, Haiku (una vez por nombre, si [conIa] y hay clave); lo que Haiku diga que no es ninguna, a «Otra». Devuelve cuántos
     * partidos ha cambiado.
     */
    fun normalizaCompeticiones(universo: String, conIa: Boolean = false): Int = synchronized(cerrojo) {
        var u = Carreras.de(universo) ?: return 0
        val ps = Carreras.partidos(universo)
        if (ps.isEmpty()) return 0
        val base = Competiciones.lista(u)
        val propias = base.size > 2
        // Sin lista propia (club sin identificar), las variantes se agrupan entre ellas
        val lista = if (propias) base else Competiciones.lista(u, ps.map { it.second })
        fun canon(raw: String) = Competiciones.sinPreguntar(raw, lista, u.compAlias)
        val sueltos = ps.map { it.second.competicion.trim() }.filter { it.isNotBlank() && it != Competiciones.OTRA && canon(it) == null }.distinctBy { Competiciones.norm(it) }
        val porPreguntar = sueltos.filter { Competiciones.norm(it) !in u.compsPreguntadas }
        if (propias && conIa && porPreguntar.isNotEmpty() && ClaveApi.hayDeVerdad && !Prueba.activo) {
            runCatching {
                val m = Competiciones.leeAgrupa(pide(Competiciones.peticionAgrupa(u.club, lista.filter { it != Competiciones.SELECCION }, porPreguntar)), lista)
                u = Carreras.guarda((Carreras.de(universo) ?: u).let { x ->
                    x.copy(compAlias = x.compAlias + porPreguntar.associate { r -> Competiciones.norm(r) to (m[Competiciones.norm(r)] ?: Competiciones.OTRA) },
                        compsPreguntadas = (x.compsPreguntadas + porPreguntar.map { Competiciones.norm(it) }).distinct())
                })
            }
        }
        var n = 0
        ps.forEach { (e, p) ->
            val c = canon(p.competicion) ?: return@forEach
            if (c != p.competicion) { Carreras.guarda(e.copy(partido = p.copy(competicion = c, competicionOriginal = p.competicionOriginal.ifBlank { p.competicion }))); n++ }
        }
        n
    }

    /** Al abrir la app: agrupa las competiciones de todas las carreras (con Haiku, si hay clave y algo por preguntar). */
    fun normalizaTodas(conIa: Boolean) { Carreras.todas().forEach { runCatching { normalizaCompeticiones(it.id, conIa) } } }

    // ---------------------------------------------------------------- tu gente: hablar, dar puerta, juntar y separar

    private fun persona(u: Universo, id: String) = u.entorno.firstOrNull { it.id == id }

    /** La conversación con alguien (todos sus mensajes, con los nombres juntados). */
    private fun conversacionCon(u: Universo, p: Persona): List<Dm> =
        conversaciones(u, Carreras.entradasDe(u.id, ahora = reloj())).firstOrNull { Vida.mismo(it.nombre, p.nombre) }?.dms.orEmpty()

    /**
     * Le escribes a alguien de tu gente lo que quieras: contesta en su papel (Sonnet), y puede cambiar su afinidad y su actitud (si le
     * convences) y tener consecuencias. Devuelve por qué no se pudo, si no se pudo.
     */
    fun charla(universo: String, personaId: String, texto: String): String? = synchronized(cerrojo) { en(universo) {
        val u = Carreras.de(universo) ?: return@en null
        val p = persona(u, personaId) ?: return@en "Esa persona ya no está en tu vida."
        val t = texto.trim().ifBlank { return@en "Escribe algo." }
        try {
            val r = Narrador.leeCharla(pide(Narrador.charla(u, contexto(u), p, conversacionCon(u, p), t)))
            var nu = Carreras.de(universo) ?: u
            val q = nu.entorno.firstOrNull { it.id == p.id } ?: p
            val q2 = q.copy(afinidad = (q.afinidad + r.afinidad).coerceIn(0, 100), actitud = r.actitud.ifBlank { q.actitud })
            nu = nu.copy(entorno = nu.entorno.map { if (it.id == q.id) q2 else it })
            val efs = r.efectos.copy(relaciones = r.efectos.relaciones.filter { (n, _) -> Gente.quien(listOf(q), n) == null })
            val ef = Vida.recorta(nu, efs).let { it.copy(relaciones = it.relaciones + (if (r.afinidad != 0) mapOf(q.nombre to r.afinidad) else emptyMap())) }
            nu = Vida.aplica(nu, efs)
            Carreras.guarda(nu)
            Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.CHARLA, reloj(), u.paso, "Hablaste con ${q.nombre}",
                r.resumen.ifBlank { "Le escribiste a ${q.nombre}." } + (if (r.convencido) " Le has hecho cambiar." else ""),
                posts = r.posts, dms = listOf(Dm("Tú", t, mio = true, chat = q.nombre, pid = q.id)) + r.respuesta.map { Dm(q.nombre, it, chat = q.nombre, pid = q.id) } +
                    Gente.sinExpulsados(nu, r.dms).filter { !Vida.mismo(Gente.chatDe(nu, it), q.nombre) }, efectos = ef))
            null
        } catch (x: Exception) { mensaje(x) }
    } }

    /**
     * Le das puerta a alguien: sale de tu gente («Ya no está en tu vida») y no vuelve a escribirte. Claude cuenta su reacción y lo que pasa
     * (si era tu representante, llegan candidatos). Si no se puede pedir (sin clave, el tope…), sale igual, sin lo de después. Devuelve
     * por qué no se pudo contar, si no se pudo.
     */
    fun puerta(universo: String, personaId: String, porque: String): String? = synchronized(cerrojo) { en(universo) {
        val u = Carreras.de(universo) ?: return@en null
        val p = persona(u, personaId) ?: return@en null
        val conv = conversacionCon(u, p)
        val r = try { Narrador.leePuerta(pide(Narrador.puerta(u, contexto(u), p, conv, porque))) } catch (x: Exception) {
            var nu = Gente.fuera(Carreras.de(universo) ?: u, p, porque)
            nu = Carreras.guarda(nu)
            Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.PUERTA, reloj(), u.paso, "Le das puerta a ${p.nombre}",
                "${p.nombre} (${p.rol}) ya no está en tu vida." + (if (porque.isNotBlank()) " Le dijiste: «${porque.trim()}»." else ""),
                dms = if (porque.isNotBlank()) listOf(Dm("Tú", porque.trim(), mio = true, chat = p.nombre, pid = p.id)) else emptyList()))
            return@en "${p.nombre} ya no está en tu vida, pero lo de después no se ha podido escribir: ${mensaje(x)}"
        }
        var nu = Gente.fuera(Carreras.de(universo) ?: u, p, porque)
        val efs = r.efectos.copy(relaciones = r.efectos.relaciones.filter { (n, _) -> Gente.quien(listOf(p), n) == null })
        val ef = Vida.recorta(nu, efs)
        nu = Vida.aplica(nu, efs)
        val paso = maxOf(1, nu.paso)
        nu = Gente.anade(nu, r.candidatos.map { it.copy(desde = paso) }, 3)
        Carreras.guarda(nu)
        Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.PUERTA, reloj(), u.paso, r.titular.ifBlank { "Le das puerta a ${p.nombre}" },
            r.texto.ifBlank { "${p.nombre} ya no está en tu vida." }, posts = r.posts,
            dms = (if (porque.isNotBlank()) listOf(Dm("Tú", porque.trim(), mio = true, chat = p.nombre, pid = p.id)) else emptyList()) +
                r.despedida.map { Dm(p.nombre, it, chat = p.nombre, pid = p.id) } + r.dms.filter { d -> Gente.quien(listOf(p), d.de) == null },
            efectos = ef.copy(nuevas = r.candidatos), tendencia = r.tendencia, tendenciaPosts = r.tendenciaPosts))
        null
    } }

    /** Vuelve a tu vida (con la relación tocada). */
    fun recupera(universo: String, personaId: String) {
        val u = Carreras.de(universo) ?: return
        val p = u.exEntorno.firstOrNull { it.id == personaId } ?: return
        Carreras.guarda(Gente.recupera(u, p))
        Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.GENTE, reloj(), u.paso, "${p.nombre} vuelve a tu vida",
            "Le has vuelto a abrir la puerta a ${p.nombre} (${p.rol}). La relación no está como antes: tocará ganársela."))
    }

    /** «Es la misma persona que…»: el chat (o nombre) [nombre] es de [personaId]. */
    fun junta(universo: String, nombre: String, personaId: String) {
        val u = Carreras.de(universo) ?: return
        val p = Gente.todos(u).firstOrNull { it.id == personaId } ?: return
        Carreras.guarda(Gente.junta(u, nombre, p))
    }

    /** «Separar»: [nombre] no es [personaId]. */
    fun separa(universo: String, nombre: String, personaId: String) {
        val u = Carreras.de(universo) ?: return
        val p = Gente.todos(u).firstOrNull { it.id == personaId } ?: return
        Carreras.guarda(Gente.separa(u, nombre, p))
    }

    /** Leer la foto de la pantalla de estadísticas. */
    fun foto(universo: String, base64: String, tipo: String): Narrador.LoDeLaFoto {
        val u = Carreras.de(universo) ?: error("No encuentro esa carrera")
        return en(universo) { Narrador.leeFoto(pide(Narrador.foto(u, base64, tipo))) }
    }

    fun mensaje(e: Throwable): String = when (e) {
        is Gasto.Tope, is Claude.Fallo, is Claude.Rechazo, is Mercado.NoLlega, is Clubes.NoEncontrado -> e.message.orEmpty()
        is IllegalStateException -> e.message ?: "No se pudo."
        else -> "No se pudo: ${e.message ?: e.javaClass.simpleName}"
    }
}
