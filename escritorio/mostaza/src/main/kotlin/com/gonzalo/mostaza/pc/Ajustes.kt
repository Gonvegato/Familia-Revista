package com.gonzalo.mostaza.pc

import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.LaFamilia

/*
 * Todo lo que eliges en Mostaza es tuyo, no de este ordenador: viaja con la cuenta, en `ajustes/mostaza_<grupo>`, un campo por ajuste, con
 * los mismos nombres y valores que escribe Mostaza del móvil (`nube/ESQUEMA.md`). En el móvil hay además una copia en las preferencias de
 * Android (EspejoCuenta); aquí no hace falta: la colección de la cuenta ya tiene su copia en el ordenador (y lo que cambies sin conexión
 * sube solo), así que se lee y se escribe en ella directamente. De cada campo gana el cambio más reciente, venga de donde venga.
 * Lo que es de verdad de este ordenador (lo que mide su conexión, el punto para seguir de lo que aún no está en tu biblioteca, lo
 * comprobado de cada fuente) se queda en él: está aparte ([Local]).
 */
object AjustesCuenta {
    const val COLECCION = "ajustes"
    /** En las pruebas sin cuenta: los ajustes en memoria. */
    @Volatile var enMemoria: MutableMap<String, MutableMap<String, Any?>>? = null

    private fun col(): Coleccion? = runCatching { LaFamilia.coleccion(COLECCION) }.getOrNull()

    /** El valor de un ajuste ("" o null = no está: se usa el de por defecto). Leído desde Compose, repinta con los cambios de la cuenta. */
    fun lee(nodo: String, campo: String): Any? {
        enMemoria?.let { return it[nodo]?.get(campo) }
        LaFamilia.cambios
        return col()?.nodo(nodo)?.get(campo)?.takeIf { it != "" }
    }

    fun texto(nodo: String, campo: String, d: String): String = lee(nodo, campo)?.comoTexto() ?: d
    fun entero(nodo: String, campo: String, d: Int): Int = lee(nodo, campo)?.let { it.comoLong().toInt() } ?: d
    fun largo(nodo: String, campo: String, d: Long): Long = lee(nodo, campo)?.comoLong() ?: d
    fun si(nodo: String, campo: String, d: Boolean): Boolean = lee(nodo, campo)?.comoBool() ?: d

    /** Cambia ajustes (solo los que de verdad cambian viajan). Los enteros van como números enteros, como los escribe Android. */
    fun pon(nodo: String, campos: Map<String, Any?>) {
        val limpios = campos.mapValues { (_, v) -> when (v) { is Int -> v.toLong(); null -> ""; else -> v } }
        enMemoria?.let { m -> m.getOrPut(nodo) { LinkedHashMap() }.putAll(limpios); return }
        col()?.cambia(nodo, limpios)
    }

    /** Todos los campos de un nodo (para las fuentes rotas). */
    fun todos(nodo: String): Map<String, Any?> = enMemoria?.get(nodo)?.toMap() ?: (col()?.nodo(nodo)?.campos.orEmpty())
}

/** El reproductor: subtítulos, audio y el episodio siguiente (`ajustes/mostaza_reproductor`). */
object AjustesReproductor {
    const val NODO = "mostaza_reproductor"
    /** El idioma de los subtítulos (ISO 639: «spa», «eng»…; "" = sin subtítulos salvo que el audio no sea el tuyo). */
    fun subs() = AjustesCuenta.texto(NODO, "subs", "spa")
    fun ponSubs(v: String) = AjustesCuenta.pon(NODO, mapOf("subs" to v))
    /** Poner subtítulos siempre (aunque el audio ya sea en tu idioma). */
    fun subsSiempre() = AjustesCuenta.si(NODO, "subsSiempre", false)
    fun ponSubsSiempre(v: Boolean) = AjustesCuenta.pon(NODO, mapOf("subsSiempre" to v))
    /** El tamaño de los subtítulos (por ciento). */
    fun subTam() = AjustesCuenta.entero(NODO, "subTam", 100).coerceIn(50, 250)
    fun ponSubTam(v: Int) = AjustesCuenta.pon(NODO, mapOf("subTam" to v))
    /** Subtítulos sobre una banda oscura (si no, con borde). */
    fun subFondo() = AjustesCuenta.si(NODO, "subFondo", false)
    fun ponSubFondo(v: Boolean) = AjustesCuenta.pon(NODO, mapOf("subFondo" to v))
    /** El idioma de audio preferido cuando una fuente trae varios. */
    fun audio() = AjustesCuenta.texto(NODO, "audio", "spa")
    fun ponAudio(v: String) = AjustesCuenta.pon(NODO, mapOf("audio" to v))
    /** Al terminar un episodio, el siguiente solo. */
    fun siguiente() = AjustesCuenta.si(NODO, "siguiente", true)
    fun ponSiguiente(v: Boolean) = AjustesCuenta.pon(NODO, mapOf("siguiente" to v))
    /** Cuánto salta adelante o atrás (segundos). */
    fun salto() = AjustesCuenta.entero(NODO, "salto", 10).coerceIn(5, 60)
    fun ponSalto(v: Int) = AjustesCuenta.pon(NODO, mapOf("salto" to v))
    // (`externo`, el reproductor de fuera del móvil, es un paquete de Android: aquí no se usa ni se toca)
}

/** El elector de fuentes ([Elector]) (`ajustes/mostaza_elector`). */
object AjustesElector {
    const val NODO = "mostaza_elector"
    fun prefs(): PrefsElector {
        val d = PrefsElector()
        val idiomas = (AjustesCuenta.lee(NODO, "idiomas")?.comoTexto())?.split(',')?.map { it.trim() }?.filter { id -> PrefsElector.IDIOMAS.any { it.first == id } }?.distinct()
            ?.takeIf { it.isNotEmpty() } ?: d.idiomas
        return PrefsElector(idiomas, AjustesCuenta.si(NODO, "subtitulados", d.subtitulados), AjustesCuenta.entero(NODO, "maxima", d.maxima),
            AjustesCuenta.si(NODO, "directas", d.directasPrimero), AjustesCuenta.si(NODO, "ahorro", d.ahorroDatos))
    }
    fun pon(n: PrefsElector) = AjustesCuenta.pon(NODO, mapOf("idiomas" to n.idiomas.joinToString(","), "subtitulados" to n.subtitulados, "maxima" to n.maxima,
        "directas" to n.directasPrimero, "ahorro" to n.ahorroDatos))
    /** «Reproducir» pone sola la mejor fuente (si no, enseña la lista). */
    fun auto() = AjustesCuenta.si(NODO, "auto", true)
    fun ponAuto(v: Boolean) = AjustesCuenta.pon(NODO, mapOf("auto" to v))
}

/** El orden de cada sección y lo que se ve (películas, series o todo) (`ajustes/mostaza_orden`). */
object OrdenGuardado {
    const val NODO = "mostaza_orden"
    enum class Orden(val nombre: String) { RECIENTES("Recientes"), ANADIDOS("Añadidos"), TITULO("Título"), ANO("Año") }
    enum class Filtro(val nombre: String) { TODO("Todo"), PELICULAS("Películas"), SERIES("Series") }
    fun de(e: Estado): Orden = Orden.entries.firstOrNull { it.name == AjustesCuenta.texto(NODO, e.name, "") } ?: Orden.RECIENTES
    fun pon(e: Estado, o: Orden) = AjustesCuenta.pon(NODO, mapOf(e.name to o.name))
    fun filtro(e: Estado): Filtro = Filtro.entries.firstOrNull { it.name == AjustesCuenta.texto(NODO, "filtro_${e.name}", "") } ?: Filtro.TODO
    fun ponFiltro(e: Estado, f: Filtro) = AjustesCuenta.pon(NODO, mapOf("filtro_${e.name}" to f.name))
}

/** La portada (qué catálogos salen y cuántos) y lo último de «Explorar» (`ajustes/mostaza_portada`). */
object AjustesPortada {
    const val NODO = "mostaza_portada"
    /** Las filas de catálogos de la portada («<addon>|<tipo>|<catálogo>», con comas; "" = las primeras de tus addons). */
    fun filas(): List<String> = AjustesCuenta.texto(NODO, "filas", "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
    fun ponFilas(l: List<String>) = AjustesCuenta.pon(NODO, mapOf("filas" to l.joinToString(",")))
    fun cuantas() = AjustesCuenta.entero(NODO, "cuantas", 4).coerceIn(0, 12)
    fun ponCuantas(n: Int) = AjustesCuenta.pon(NODO, mapOf("cuantas" to n))
    fun explorar(): Triple<String, String, String> = Triple(AjustesCuenta.texto(NODO, "explorarTipo", "movie"), AjustesCuenta.texto(NODO, "explorarCatalogo", ""), AjustesCuenta.texto(NODO, "explorarGenero", ""))
    fun ponExplorar(tipo: String, catalogo: String, genero: String) = AjustesCuenta.pon(NODO, mapOf("explorarTipo" to tipo, "explorarCatalogo" to catalogo, "explorarGenero" to genero))
}

/** Los torrents (`ajustes/mostaza_torrent`): lo que es tuyo viaja; lo que puede ocupar la caché es de este ordenador ([Local]). */
object AjustesTorrents {
    const val NODO = "mostaza_torrent"
    /** La subida por defecto (KB/s), la misma que en el móvil. */
    const val SUBIDA_POR_DEFECTO_KBS = 100
    fun subida() = AjustesCuenta.entero(NODO, "subida", SUBIDA_POR_DEFECTO_KBS).coerceAtLeast(0)
    fun bajada() = AjustesCuenta.entero(NODO, "bajada", 0).coerceAtLeast(0)
    fun compartir() = AjustesCuenta.si(NODO, "compartir", true)
    fun pon(subida: Int, bajada: Int, compartir: Boolean) = AjustesCuenta.pon(NODO, mapOf("subida" to subida, "bajada" to bajada, "compartir" to compartir))
    // (`soloWifi` es del móvil: un ordenador no tiene datos móviles, así que aquí no se mira ni se cambia)
}
