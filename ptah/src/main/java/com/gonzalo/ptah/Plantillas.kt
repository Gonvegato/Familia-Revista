package com.gonzalo.ptah

/**
 * Las plantillas para empezar: diseños ya hechos (con los colores de tu Apariencia o con los suyos), que se copian a «Tus widgets» al
 * elegirlos. Sus ids empiezan por «p» (no son tuyos hasta que los eliges).
 */
object Plantillas {
    private const val PETROLEO = "#12393A"
    private const val CREMA = "#F2E6CF"
    private const val COBRE = "#D98A3D"

    private fun de(id: String, nombre: String, t: TipoWidget, estilo: Estilo = Estilo(), vararg op: Pair<String, Any?>) =
        Diseno("p$id", nombre, t.id, estilo, t.opcionesDeSerie() + op.toMap(), 0L, 0L)

    val TODAS: List<Pair<Diseno, Tamanos.Celdas>> by lazy {
        listOf(
            de("portada", "Portada", RelojDigital, Estilo(radio = 22f)) to Tamanos.Celdas(4, 2),
            de("columna", "Columna", RelojDigital, Estilo(fondo = TipoFondo.COLOR, colorFondo = PETROLEO, tinta = CREMA, acento = COBRE, secundario = "#B9C9C4", marco = true, borde = false, radio = 18f),
                "cabecera" to "columna", "ante" to "Ptah", "letraHora" to LetraFija.VITRINA.name) to Tamanos.Celdas(2, 2),
            de("titular", "Titular con fecha", RelojDigital, Estilo(fondo = TipoFondo.COLOR, colorFondo = Col.TINTA, tinta = Col.PAPEL, secundario = "", acento = Col.ACENTO, borde = false, radio = 26f),
                "cabecera" to "titular", "ante" to "Hoy") to Tamanos.Celdas(4, 2),
            de("esfera", "Esfera de revista", RelojAgujas, Estilo(marco = true, radio = 22f)) to Tamanos.Celdas(2, 2),
            de("noche", "Esfera de noche", RelojAgujas, Estilo(fondo = TipoFondo.DEGRADADO, colorFondo = "#1C2A2B", colorFondo2 = "#0B1414", angulo = 0, tinta = CREMA, acento = COBRE, borde = false, radio = 80f,
                piezas = mapOf("marcas" to "#9FB3AE")), "numeros" to "rayas", "forma" to FormaAgujas.FINA.name, "segundero" to true, "texto" to "") to Tamanos.Celdas(2, 2),
            de("hoy", "La hora y el día", RelojAgujas, Estilo(radio = 22f), "numeros" to "cuatro", "forma" to FormaAgujas.BASTON.name, "texto" to "", "ante" to "Hoy") to Tamanos.Celdas(4, 2),
            de("mes", "El mes", Calendario, Estilo(radio = 22f)) to Tamanos.Celdas(3, 2),
            de("buscador", "Buscador", Buscador, Estilo(fondo = TipoFondo.TRANSPARENTE, borde = false, radio = 26f, tinta = "#F6F1E7", secundario = "#E6DED0", piezas = mapOf("dentro" to "#E6141210"))) to Tamanos.Celdas(4, 1),
            de("apps", "Tus webs", Accesos, Estilo(radio = 22f), "items" to listOf("web:elpais.com|El País", "web:es.wikipedia.org|Wikipedia", "web:www.aemet.es|Aemet", "web:www.rtve.es/play|RTVE Play"), "ante" to "A mano") to Tamanos.Celdas(4, 1),
            de("tiempo", "El tiempo", Tiempo, Estilo(radio = 22f)) to Tamanos.Celdas(4, 2),
            de("bateria", "La batería", TipoBateria, Estilo(fondo = TipoFondo.COLOR, colorFondo = PETROLEO, tinta = CREMA, acento = COBRE, borde = false, radio = 18f)) to Tamanos.Celdas(2, 1),
            de("navidad", "Cuenta atrás", CuentaAtras, Estilo(fondo = TipoFondo.COLOR, colorFondo = Col.ACENTO, tinta = Col.PAPEL, secundario = Col.PAPEL, borde = false, radio = 22f,
                piezas = mapOf("cifra" to Col.PAPEL, "cuadro" to Col.PAPEL, "fecha" to Col.PAPEL))) to Tamanos.Celdas(2, 2),
            de("nota", "Nota de papel", TipoNota, Estilo(fondo = TipoFondo.COLOR, colorFondo = CREMA, tinta = PETROLEO, acento = COBRE, secundario = "#5B6E6B", radio = 10f, marco = true)) to Tamanos.Celdas(3, 2),
            de("foto", "Foto con pie", Foto, Estilo(radio = 18f), "pie" to "El verano en la playa") to Tamanos.Celdas(2, 2),
        )
    }

    /** Un diseño tuyo nuevo a partir de una plantilla. */
    fun copia(p: Diseno): Diseno {
        val c = TODAS.firstOrNull { it.first.id == p.id }?.second
        return p.copy(id = Diseno.nuevoId(), creado = System.currentTimeMillis(), cambiado = System.currentTimeMillis()).let { if (c != null) it.con("celdas", "${c.col}x${c.fil}") else it }
    }

    /** El tamaño con el que se enseña un diseño (el de su plantilla, o el de su tipo). */
    fun celdasDe(d: Diseno): Tamanos.Celdas {
        Regex("""(\d)x(\d)""").matchEntire(d.texto("celdas"))?.let { m -> return Tamanos.Celdas(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        return TODAS.firstOrNull { it.first.id == d.id }?.second ?: Tipos.de(d.tipo)?.muestra ?: Tamanos.Celdas(2, 2)
    }
}
