package com.gonzalo.shai

/**
 * Dentro de la historia, el videojuego no existe: nadie habla de la «media», de atributos con número, de cartas ni de EA Sports FC. Claude
 * lo tiene prohibido ([Narrador.REGLAS], regla 17) y recibe su nivel en palabras ([Narrador.enElCampo]); por si aun así se le escapa,
 * aquí se quitan las frases que lo nombran (y los posts enteros) de todo lo que escribe.
 */
object SinJuego {
    private val JUEGO = Regex(listOf(
        """\bmedia\s+(?:general\s+)?(?:de\s+)?[4-9]\d\b""",
        """\b(?:su|tu|mi|nuestra|vuestra)\s+media\b(?!\s+(?:de\s+goles|goleadora|de\s+edad|de\s+minutos|de\s+asistencias|de\s+puntos|hora|tarde|mañana|noche|parte|sonrisa|vuelta|naranja|luz|altura|distancia|melena|docena|plantilla|ciudad|vida))""",
        """\bvaloraci[oó]n\s+(?:general|global)\b""", """\b(?:ritmo|tiro|disparo|pase|regate|defensa|f[ií]sico)\s+(?:de\s+)?[4-9]\d\b""",
        """\bEA\s*Sports\s*FC\b""", """\bEA\s*FC\b""", """\bFC\s*2[4-9]\b""", """\bFIFA\s*\d{2}\b""", """\bvideojuego""", """\bmodo\s+carrera\b""",
        """\bUltimate\s+Team\b""", """\bpuntos\s+de\s+habilidad\b""", """\bcarta\s+(?:oro|plata|bronce|TOTW|TOTS|especial)\b""", """\bsube\s+de\s+media\b""", """\bpotencial\s+(?:de\s+)?[4-9]\d\b""",
    ).joinToString("|"), RegexOption.IGNORE_CASE)

    /** Las claves de nombres (no se tocan). */
    private val NOMBRES = setOf("de", "nombre", "chat", "id", "usuario", "autor", "hashtag", "rol", "tipo", "club", "cabecera", "nivel_tras", "estado")

    fun nombra(t: String): Boolean = JUEGO.containsMatchIn(t)

    /** El texto sin las frases que nombran el juego (si no queda nada, el de antes con la cifra fuera). */
    fun texto(t: String): String {
        if (!nombra(t)) return t
        val frases = Regex("""(?<=[.!?…»])\s+|\n+""").split(t)
        val quedan = frases.filter { !nombra(it) }
        val r = if (t.contains("\n\n")) t.split(Regex("""\n\s*\n""")).map { p -> Regex("""(?<=[.!?…»])\s+""").split(p).filter { !nombra(it) }.joinToString(" ") }.filter { it.isNotBlank() }.joinToString("\n\n")
            else quedan.joinToString(" ")
        return r.ifBlank { JUEGO.replace(t, "").replace(Regex("""\s{2,}"""), " ").trim() }
    }

    /** Todo lo que vuelve de Claude, limpio: los posts que nombran el juego fuera; en lo demás, sus frases. */
    @Suppress("UNCHECKED_CAST")
    fun limpia(v: Any?, clave: String = ""): Any? = when (v) {
        is String -> if (clave in NOMBRES) v else texto(v)
        is Map<*, *> -> (v as Map<String, Any?>).mapValues { (k, x) -> limpia(x, k) }
        is List<*> -> if (clave == "posts" || clave == "respuestas") v.filter { p -> (p as? Map<*, *>)?.get("texto")?.let { it is String && nombra(it) } != true }.map { limpia(it, clave) }
            else v.map { limpia(it, clave) }
        else -> v
    }

    private fun post(p: Post): Post? = if (nombra(p.texto)) null else p.copy(respuestas = p.respuestas.mapNotNull { post(it) }, cita = p.cita?.let { c -> c.copy(texto = texto(c.texto)) })

    /** Lo ya guardado (de antes de la 0.9.1), limpio. Devuelve la misma entrada si no había nada que quitar. */
    fun entrada(e: Entrada): Entrada {
        val n = e.copy(titulo = texto(e.titulo), texto = texto(e.texto), posts = e.posts.mapNotNull { post(it) }, dms = e.dms.map { if (it.mio) it else it.copy(texto = texto(it.texto)) },
            prensa = e.prensa?.let { p -> p.copy(titular = texto(p.titular), entradilla = texto(p.entradilla), articulo = texto(p.articulo), otros = p.otros.map { texto(it) },
                entradillas = p.entradillas.map { texto(it) }) },
            ofertas = e.ofertas.map { it.copy(texto = texto(it.texto)) },
            decision = e.decision?.let { d -> d.copy(contexto = texto(d.contexto), texto = texto(d.texto), consecuencia = texto(d.consecuencia)) })
        return if (n == e) e else n
    }

    /** Todas las carreras, limpias (al abrir la app; solo se guarda lo que cambia). */
    fun limpiaTodo(): Int {
        var n = 0
        for (u in Carreras.todas()) {
            Carreras.entradasDe(u.id, todas = true).forEach { e -> val l = entrada(e); if (l !== e) { Carreras.guarda(l); n++ } }
            val m = texto(u.memoria); if (m != u.memoria) Carreras.guarda(u.copy(memoria = m))
        }
        return n
    }
}
