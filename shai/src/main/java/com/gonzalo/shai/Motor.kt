package com.gonzalo.shai

import com.gonzalo.familia.LaFamilia

/**
 * Tu clave de la API de Claude (la de tu organización de Claude Console, la de los créditos del plan): es tuya y va con tu cuenta,
 * **solo en `/claves/<uid>`** (`shai_anthropic`), que no puede leer nadie más (ni tu pareja). Nunca va a `/users`, ni a un registro, ni a
 * un mensaje de error. Se manda en la cabecera `x-api-key`.
 */
object ClaveApi {
    const val CLAVE = "shai_anthropic"
    /** En las pruebas: la clave, sin cuenta. */
    @Volatile var fija: String? = null

    fun lee(): String { fija?.let { return it }; return runCatching { LaFamilia.familia.clave(CLAVE) }.getOrDefault("").trim() }
    /** ¿Se puede pedir? (en modo prueba, siempre: no hace falta clave). */
    val hay: Boolean get() = Prueba.activo || hayDeVerdad
    /** ¿Hay una clave puesta de verdad? */
    val hayDeVerdad: Boolean get() { runCatching { LaFamilia.cambios }; return lee().isNotBlank() }

    fun guarda(k: String) {
        val t = k.trim()
        if (fija != null) { fija = t; return }
        val f = LaFamilia.familia
        check(f.sesion.dentro) { "Entra en tu cuenta primero (Ajustes → Cuenta)." }
        f.ponClave(CLAVE, t)
    }

    /** «sk-ant-…a1B2» (para enseñar cuál tienes puesta sin enseñarla). */
    fun tapada(): String = lee().let { k -> if (k.length < 12) "" else k.take(7) + "…" + k.takeLast(4) }
}

/**
 * Lo que hace Shai con lo que metes: guardar el partido, pedir la red, la prensa y lo demás, aplicarlo a tu carrera y mandar los dos
 * eventos de entre partidos (por lotes). Todo bloquea: se llama fuera del hilo principal. Si algo no llega (sin conexión, el tope…), lo
 * del partido queda guardado igual y se puede volver a pedir lo que falta ([reintenta]).
 */
object Motor {
    @Volatile var claude: Claude = Claude({ ClaveApi.lee() })
    /** En las pruebas: otro reloj. */
    @Volatile var reloj: () -> Long = { System.currentTimeMillis() }
    /** Cuánto tarda en salir el segundo evento de entre partidos tras el primero. */
    @Volatile var separacion: Long = 6 * 3600_000L
    /** Cada cuántas jornadas se rehace lo que Shai recuerda. */
    const val CADA_MEMORIA = 3

    /** La carrera de la que se está pidiendo algo ahora (en este hilo): el modo prueba la usa para no repetir. */
    val actual = ThreadLocal<String?>()

    private val cerrojo = Any()

    /** Pide, con el tope y el gasto apuntado. */
    fun pide(p: Peticion): Map<String, Any?> {
        Gasto.compruebaAntes(Precios.coste(p.modelo, p.usoMaximo()))
        val r = try { claude.mensaje(p) } catch (e: Claude.Rechazo) { Gasto.apunta(e.modelo.ifBlank { p.modelo }, Precios.coste(e.modelo.ifBlank { p.modelo }, e.uso)); throw e }
        Gasto.apunta(r.modelo.ifBlank { p.modelo }, Precios.coste(r.modelo.ifBlank { p.modelo }, r.uso))
        return r.json()
    }

    private fun <T> en(universo: String, f: () -> T): T { val antes = actual.get(); actual.set(universo); try { return f() } finally { actual.set(antes) } }

    private fun contexto(u: Universo, cuantas: Int = 8) =
        Narrador.contexto(u, Carreras.entradasDe(u.id, ahora = reloj()), Carreras.partidos(u.id).map { it.second }, cuantas)

    /** ¿Cabe otra decisión? (con [Narrador.MAX_ABIERTAS] abiertas, ya no se ponen más). */
    fun cabeDecision(universo: String): Boolean = Carreras.entradasDe(universo, ahora = reloj()).count { it.decision?.abierta == true } < Narrador.MAX_ABIERTAS

    // ---------------------------------------------------------------- una carrera nueva

    /** Crea la carrera y pide su principio (su gente y su presentación). Si eso falla, la carrera queda creada igual. */
    fun crea(u0: Universo): Pair<Universo, String?> {
        var u = Carreras.guarda(u0.copy(precios = u0.precios.ifEmpty { Mercado.inicial() }, famaHist = u0.famaHist.ifEmpty { listOf(0 to u0.valorFuera(Fuera.FAMA)) }))
        val fallo = en(u.id) {
            try {
                val r = Narrador.leeInicio(pide(Narrador.inicio(u)))
                u = Carreras.guarda(u.copy(entorno = u.entorno + r.gente.filter { g -> u.entorno.none { Vida.mismo(it.nombre, g.nombre) } }))
                Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.INICIO, reloj(), u.paso, r.titulo, r.texto, posts = r.posts, dms = r.dms, prensa = r.prensa,
                    tendencia = r.tendencia, tendenciaPosts = r.tendenciaPosts))
                null
            } catch (e: Exception) { mensaje(e) }
        }
        return u to fallo
    }

    // ---------------------------------------------------------------- tras un partido

    /**
     * Guarda el partido (y la jornada: sueldo, bolsa, la fama que da…), caduca las decisiones que no contestaste, saca los eventos que
     * estaban esperando y pide la red y la prensa (que cuenta también lo que dejaste sin contestar). Luego, si toca, manda los dos eventos
     * de entre partidos por lotes y, cada [CADA_MEMORIA] jornadas, rehace lo que Shai recuerda. Devuelve la entrada del partido y, si algo
     * no llegó, por qué.
     */
    fun partido(universo: String, p0: Partido): Pair<Entrada, String?> = synchronized(cerrojo) { en(universo) {
        // Lo que tenías abierto y ya habías visto caduca con el partido nuevo (lo que estaba por salir aún no lo habías visto)
        caduca(universo)
        revela(universo, forzar = true)
        var u = Carreras.de(universo) ?: error("No encuentro esa carrera")
        // Sin fecha: la del anterior + 7 días (si la hay), marcada como estimada
        val p = if (Fechas.lee(p0.fecha) != null) p0 else fechaEstimada(universo)?.let { p0.copy(fecha = Fechas.iso(it), fechaEstimada = true) } ?: p0
        // Con fecha de nacimiento y fecha del partido, su edad (años cumplidos) es la de ese día
        Fechas.lee(u.personaje.nacimiento)?.let { nac -> Fechas.lee(p.fecha)?.let { f -> Fechas.edad(nac, f).first.takeIf { it in 10..80 && it != u.personaje.edad }?.let { a ->
            u = Carreras.guarda(u.copy(personaje = u.personaje.copy(edad = a))) } } }
        u = Carreras.guarda(Vida.jornada(u, p))
        var e = Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.PARTIDO, reloj(), u.paso, partido = p, pendiente = "red,prensa"))
        // Los hitos y récords (los de Shai, con sus cifras): se apuntan, dan algo de fama y la red y la prensa los cuentan
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
        if (e.pendiente.isEmpty()) {
            runCatching { mandaEventos(Carreras.de(universo) ?: u) }
            if (u.paso % CADA_MEMORIA == 0) runCatching { recuerda(universo) }
        }
        e to r.second
    } }

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
                val r = Narrador.leeRed(pide(Narrador.red(u, p, contexto(u))))
                e = Carreras.guarda(e.copy(posts = r.posts, dms = r.dms, tendencia = r.tendencia, tendenciaPosts = r.tendenciaPosts,
                    pendiente = e.pendiente.split(',').filter { it != "red" }.joinToString(",")))
            } catch (x: Exception) { fallo = mensaje(x) }
        }
        if ("prensa" in e.pendiente && fallo == null) {
            try {
                val u = Carreras.de(e.universo) ?: return@en e to null
                val caducadas = sinContar(u.id)
                val r = Narrador.leePrensa(pide(Narrador.prensa(u, p, contexto(u), caducadas)), u.nivelActual)
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
                val m = runCatching { resp.json() }.getOrNull() ?: return@forEachIndexed
                // Si ya metiste otro partido, salen ya: se quedaron atrás (en modo prueba, los dos salen ya: no hay que esperar horas para ver cómo va)
                val visible = if (u.paso > l.paso || Prueba.activo) ahora else ahora + i * separacion
                var ev = Narrador.leeEvento(m, u, ahora, visible).copy(paso = l.paso)
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
            Carreras.entradasDe(u.id, todas = true, ahora = ahora).filter { !it.aplicada && (forzar || it.visible(ahora)) }.sortedBy { it.visibleDesde }.forEach { e ->
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
            var ev = Narrador.leeEvento(m, u, reloj(), reloj())
            if (ev.decision != null && !cabeDecision(universo)) ev = ev.copy(decision = null)
            val e = Carreras.guarda(ev)
            revela(universo)
            (Carreras.entrada(e.id) ?: e) to null
        } catch (x: Exception) { null to mensaje(x) }
    } }

    // ---------------------------------------------------------------- lo que Shai recuerda

    /** Rehace lo que Shai recuerda de la carrera (Sonnet, corto). Devuelve por qué no se pudo, si no se pudo. */
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
     * verano; luego pasa a la temporada siguiente (y un año más) y rehace lo que Shai recuerda. Devuelve por qué no se pudo, si no se pudo.
     */
    fun cierraTemporada(universo: String): String? = synchronized(cerrojo) { en(universo) {
        val u = Carreras.de(universo) ?: return@en null
        val t = u.temporada
        if (t.isNotBlank() && t in u.cerradas) return@en "La temporada $t ya está cerrada."
        val ps = Carreras.partidos(u.id).map { it.second }.filter { it.temporada == t || t.isBlank() }
        if (ps.isEmpty()) return@en "No hay partidos en esta temporada: mete alguno antes de cerrarla."
        try {
            val r = Narrador.leeTemporada(pide(Narrador.temporada(u, contexto(u, cuantas = 12), Estadisticas.linea(t, "", ps), ps)), u.nivelActual)
            val ef = Vida.recorta(u, r.efectos)
            var nu = Vida.aplica(u, r.efectos)
            val ahora = reloj()
            Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.TEMPORADA, ahora, u.paso, r.titulo.ifBlank { "Cierre de la temporada $t" }, r.balance,
                posts = r.posts, dms = r.dms, prensa = r.prensa, ofertas = r.ofertas, efectos = ef, tendencia = r.tendencia, tendenciaPosts = r.tendenciaPosts, premios = r.premios))
            if (r.vacacionesTexto.isNotBlank()) Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.EVENTO, ahora + 1, u.paso, r.vacacionesTitulo.ifBlank { "Vacaciones" },
                r.vacacionesTexto, decision = r.vacacionesDecision?.takeIf { cabeDecision(u.id) }))
            nu = (Carreras.de(u.id) ?: nu).let { x -> x.copy(dinero = nu.dinero, fuera = nu.fuera, entorno = nu.entorno, famaJornada = nu.famaJornada, famaHist = nu.famaHist) }
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
                    posts = r.posts, dms = listOf(Dm("Tú", t, mio = true, chat = d.de)) + r.dms, efectos = ef, tendencia = r.tendencia, tendenciaPosts = r.tendenciaPosts))
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
                of = Carreras.guarda(of.copy(posts = r.posts, dms = r.dms, tendencia = r.tendencia, tendenciaPosts = r.tendenciaPosts)); null
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
        Carreras.guarda(u.copy(clubInfo = info, liga = u.liga.ifBlank { info.liga }))
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
