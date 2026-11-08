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

    private val cerrojo = Any()

    /** Pide, con el tope y el gasto apuntado. */
    fun pide(p: Peticion): Map<String, Any?> {
        Gasto.compruebaAntes(Precios.coste(p.modelo, p.usoMaximo()))
        val r = try { claude.mensaje(p) } catch (e: Claude.Rechazo) { Gasto.apunta(e.modelo.ifBlank { p.modelo }, Precios.coste(e.modelo.ifBlank { p.modelo }, e.uso)); throw e }
        Gasto.apunta(r.modelo.ifBlank { p.modelo }, Precios.coste(r.modelo.ifBlank { p.modelo }, r.uso))
        return r.json()
    }

    private fun contexto(u: Universo) = Narrador.contexto(u, Carreras.entradasDe(u.id, ahora = reloj()), Carreras.partidos(u.id).map { it.second })

    // ---------------------------------------------------------------- una carrera nueva

    /** Crea la carrera y pide su principio (su gente y su presentación). Si eso falla, la carrera queda creada igual. */
    fun crea(u0: Universo): Pair<Universo, String?> {
        var u = Carreras.guarda(u0.copy(precios = u0.precios.ifEmpty { Mercado.inicial() }))
        val fallo = try {
            val r = Narrador.leeInicio(pide(Narrador.inicio(u)))
            u = Carreras.guarda(u.copy(entorno = u.entorno + r.gente.filter { g -> u.entorno.none { Vida.mismo(it.nombre, g.nombre) } }))
            Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.INICIO, reloj(), u.paso, r.titulo, r.texto, posts = r.posts, dms = r.dms, prensa = r.prensa))
            null
        } catch (e: Exception) { mensaje(e) }
        return u to fallo
    }

    // ---------------------------------------------------------------- tras un partido

    /**
     * Guarda el partido (y la jornada: sueldo, bolsa…), saca los eventos que estaban esperando y pide la red y la prensa. Luego, si toca,
     * manda los dos eventos de entre partidos por lotes. Devuelve la entrada del partido y, si algo no llegó, por qué.
     */
    fun partido(universo: String, p: Partido): Pair<Entrada, String?> = synchronized(cerrojo) {
        // Lo que estaba por salir sale ya (el partido llega antes que su hora)
        revela(universo, forzar = true)
        var u = Carreras.de(universo) ?: error("No encuentro esa carrera")
        u = Carreras.guarda(Vida.jornada(u, p))
        var e = Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.PARTIDO, reloj(), u.paso, partido = p, pendiente = "red,prensa"))
        val r = completa(e)
        e = r.first
        if (e.pendiente.isEmpty()) runCatching { mandaEventos(Carreras.de(universo) ?: u) }
        e to r.second
    }

    /** Pide lo que le falta a un partido (la red, la prensa o las dos). */
    fun completa(e0: Entrada): Pair<Entrada, String?> {
        var e = e0
        var fallo: String? = null
        val p = e.partido ?: return e to null
        if ("red" in e.pendiente) {
            try {
                val u = Carreras.de(e.universo) ?: return e to null
                val (posts, dms) = Narrador.leeRed(pide(Narrador.red(u, p, contexto(u))))
                e = Carreras.guarda(e.copy(posts = posts, dms = dms, pendiente = e.pendiente.split(',').filter { it != "red" }.joinToString(",")))
            } catch (x: Exception) { fallo = mensaje(x) }
        }
        if ("prensa" in e.pendiente && fallo == null) {
            try {
                val u = Carreras.de(e.universo) ?: return e to null
                val r = Narrador.leePrensa(pide(Narrador.prensa(u, p, contexto(u))))
                val nu = Vida.aplica(u, r.efectos)
                Carreras.guarda(nu)
                e = Carreras.guarda(e.copy(prensa = r.prensa, ofertas = r.ofertas, decision = r.decision, efectos = Vida.recorta(u, r.efectos),
                    pendiente = e.pendiente.split(',').filter { it != "prensa" }.joinToString(",")))
            } catch (x: Exception) { fallo = mensaje(x) }
        }
        return e to fallo
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
        val ctx = contexto(u)
        val ps = listOf(1, 2).map { i -> "${u.id}_${u.paso}_$i" to Narrador.evento(u, ctx, i, enLote = true) }
        Gasto.compruebaAntes(ps.sumOf { Precios.coste(it.second.modelo, it.second.usoMaximo(), lote = true, cacheHora = true) })
        val id = claude.creaLote(ps)
        val nu = Carreras.de(u.id) ?: u
        Carreras.guarda(nu.copy(lotes = nu.lotes + Lote(id, reloj(), u.paso)))
        // En modo prueba, el lote «vuelve» al momento
        if (Prueba.activo) { miraLotes(); revela(u.id) }
    }

    /** Mira los lotes que faltan; los que han vuelto, a su sitio (el primero sale ya y el segundo, horas después). Devuelve cuántos eventos llegaron. */
    fun miraLotes(): Int = synchronized(cerrojo) {
        var llegados = 0
        for (u0 in Carreras.todas()) for (l in u0.lotes) {
            val est = try { claude.lote(l.id) } catch (e: Claude.Fallo) { if (e.reintentable) continue else { quitaLote(u0.id, l.id); continue } }
            if (est.estado != "ended") {
                // (si en dos días no ha vuelto, se da por perdido: la API los caduca a las 24 horas)
                if (reloj() - l.creado > 48 * 3600_000L) quitaLote(u0.id, l.id)
                continue
            }
            val res = try { claude.resultados(l.id, est.resultados) } catch (e: Claude.Fallo) { if (e.reintentable) continue else emptyList() }
            val u = Carreras.de(u0.id) ?: continue
            val ahora = reloj()
            res.sortedBy { it.id }.forEachIndexed { i, r ->
                val resp = r.respuesta ?: return@forEachIndexed
                Gasto.apunta(resp.modelo.ifBlank { Claude.SONNET }, Precios.coste(resp.modelo.ifBlank { Claude.SONNET }, resp.uso, lote = true, cacheHora = true))
                val m = runCatching { resp.json() }.getOrNull() ?: return@forEachIndexed
                // Si ya metiste otro partido, salen ya: se quedaron atrás
                // (en modo prueba, los dos salen ya: no hay que esperar horas para ver cómo va)
                val visible = if (u.paso > l.paso || Prueba.activo) ahora else ahora + i * separacion
                Carreras.guarda(Narrador.leeEvento(m, u, ahora, visible).copy(paso = l.paso))
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
    fun eventoAhora(universo: String): Pair<Entrada?, String?> = synchronized(cerrojo) {
        val u = Carreras.de(universo) ?: return null to null
        try {
            val m = pide(Narrador.evento(u, contexto(u), 0))
            val e = Carreras.guarda(Narrador.leeEvento(m, u, reloj(), reloj()))
            revela(universo)
            (Carreras.entrada(e.id) ?: e) to null
        } catch (x: Exception) { null to mensaje(x) }
    }

    // ---------------------------------------------------------------- lo que decides

    /** Contestar a una decisión (una de sus opciones o lo que tú quieras): Claude cuenta lo que pasa y sus efectos cuentan. */
    fun decide(entrada: String, eleccion: String): String? = synchronized(cerrojo) {
        val e = Carreras.entrada(entrada) ?: return null
        val d = e.decision?.takeIf { it.abierta } ?: return null
        val u = Carreras.de(e.universo) ?: return null
        val t = eleccion.trim().ifBlank { return "Escribe qué haces o elige una opción." }
        try {
            val r = Narrador.leeRespuesta(pide(Narrador.respuesta(u, contexto(u), e, d, t)))
            Carreras.guarda(e.copy(decision = d.copy(elegida = t, consecuencia = r.texto, estado = Decision.RESUELTA)))
            val ef = Vida.recorta(u, r.efectos)
            Carreras.guarda(Vida.aplica(u, r.efectos))
            Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.RESPUESTA, reloj(), u.paso, r.titular.ifBlank { "Lo que pasó después" }, r.texto,
                posts = r.posts, dms = listOf(Dm("Tú", t, mio = true)) + r.dms, efectos = ef))
            null
        } catch (x: Exception) { mensaje(x) }
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
        val nu = Carreras.guarda(Vida.acepta(u, o))
        val titulo = when (o.tipo) {
            "traspaso" -> "Oficial: ficha por ${o.de}"; "cesion" -> "Oficial: cedido al ${o.de}"; "banquillo" -> "Oficial: nuevo entrenador del ${o.de}"
            "renovacion" -> "Oficial: renueva con ${o.de}"; else -> "Oficial: acuerdo con ${o.de}"
        }
        val texto = listOfNotNull(o.texto.ifBlank { null }, o.salario.takeIf { it > 0 }?.let { "${Formato.euros(it)} a la semana" }, o.prima.takeIf { it > 0 }?.let { "prima de ${Formato.euros(it)}" },
            o.anios.takeIf { it > 0 }?.let { "$it año${if (it > 1) "s" else ""}" }).joinToString(" · ")
        var of = Carreras.guarda(Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.OFICIAL, reloj(), u.paso, titulo, texto + "\nHazlo también en tu partida de FC."))
        return try {
            val (posts, dms) = Narrador.leeRed(pide(Narrador.oficial(nu, contexto(nu), o)))
            of = Carreras.guarda(of.copy(posts = posts, dms = dms)); null
        } catch (x: Exception) { mensaje(x) }
    }

    /** Leer la foto de la pantalla de estadísticas. */
    fun foto(universo: String, base64: String, tipo: String): Narrador.LoDeLaFoto {
        val u = Carreras.de(universo) ?: error("No encuentro esa carrera")
        return Narrador.leeFoto(pide(Narrador.foto(u, base64, tipo)))
    }

    fun mensaje(e: Throwable): String = when (e) {
        is Gasto.Tope, is Claude.Fallo, is Claude.Rechazo, is Mercado.NoLlega -> e.message.orEmpty()
        is IllegalStateException -> e.message ?: "No se pudo."
        else -> "No se pudo: ${e.message ?: e.javaClass.simpleName}"
    }
}
