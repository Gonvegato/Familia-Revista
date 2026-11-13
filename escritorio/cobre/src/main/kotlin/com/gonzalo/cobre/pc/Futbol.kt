package com.gonzalo.cobre.pc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.iptv.Buscar
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.IptvError
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Red
import com.gonzalo.iptv.RedJava
import com.gonzalo.iptv.futbol.CacheFutbol
import com.gonzalo.iptv.futbol.Calidad
import com.gonzalo.iptv.futbol.Calidades
import com.gonzalo.iptv.futbol.CanalesFutbol
import com.gonzalo.iptv.futbol.Clasificacion
import com.gonzalo.iptv.futbol.Competiciones
import com.gonzalo.iptv.futbol.Conexion
import com.gonzalo.iptv.futbol.Entrada
import com.gonzalo.iptv.futbol.Equipo
import com.gonzalo.iptv.futbol.Espn
import com.gonzalo.iptv.futbol.FichaPartido
import com.gonzalo.iptv.futbol.FuenteFutbol
import com.gonzalo.iptv.futbol.GrupoCanales
import com.gonzalo.iptv.futbol.Hora
import com.gonzalo.iptv.futbol.Lideres
import com.gonzalo.iptv.futbol.Orden
import com.gonzalo.iptv.futbol.Partido
import com.gonzalo.iptv.futbol.RefCanal
import java.io.File
import com.gonzalo.familia.pc.Actualizador
import java.util.concurrent.ConcurrentHashMap

/*
 * El fútbol de Nut en el ordenador: lo mismo que Nut del móvil (cobre/Futbol.kt), con los mismos datos y la misma caché (el módulo iptv,
 * compartido), leyendo de la cuenta lo que eliges en el móvil (`ajustes/cobre_futbol`: tus equipos, tus rivales, tus grupos de canales…).
 */

/** Lo que eliges del fútbol, de la cuenta (lo mismo que en el móvil: `ajustes/cobre_futbol`, un campo de texto por ajuste). */
object AjustesFutbol {
    const val NODO = "cobre_futbol"
    private fun texto(k: String): String? = Futbol.fijo?.let { null } ?: AjustesCuenta.texto(NODO, k, "").ifBlank { null }
    private fun pon(k: String, v: String) { AjustesCuenta.pon(NODO, mapOf(k to v)); Futbol.cambio() }

    fun equipos(): List<Pair<Equipo, String>> { Futbol.fijo?.let { return it.equipos }; return CanalesFutbol.equiposDeTexto(texto("equipos")) }
    fun rivales(): List<Pair<Equipo, String>> { Futbol.fijo?.let { return it.rivales }; return CanalesFutbol.equiposDeTexto(texto("rivales")) }
    fun esMio(id: String) = equipos().any { it.first.id == id }
    fun esRival(id: String) = rivales().any { it.first.id == id }

    /** Pone o quita [e] en tus equipos ([rival] = false) o en tus rivales; si estaba en la otra lista, sale de ella. */
    fun alterna(e: Equipo, liga: String, rival: Boolean = false) {
        val (m, r) = CanalesFutbol.alterna(equipos(), rivales(), e, liga, rival)
        AjustesCuenta.pon(NODO, mapOf("equipos" to CanalesFutbol.equiposATexto(m), "rivales" to CanalesFutbol.equiposATexto(r)))
        Futbol.cambio()
    }

    fun grupos(): List<GrupoCanales> { Futbol.fijo?.let { return it.grupos }; return CanalesFutbol.gruposDeTexto(texto("grupos")) }
    fun asignacion(): Map<String, List<String>> = CanalesFutbol.asignacionDeTexto(texto("asignacion"))
    fun calidad(): Calidad? = Calidad.de(texto("calidad").orEmpty())

    /** Las competiciones de los partidos del día, en tu orden; [ocultas], las que escondes. */
    fun competiciones(): List<com.gonzalo.iptv.futbol.Competicion> =
        Competiciones.ordenadas(texto("competiciones").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() })
    fun ocultas(): Set<String> = texto("ocultas").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** «Dónde verlo» de un partido de [liga]. */
    fun donde(liga: String): List<com.gonzalo.iptv.futbol.Donde> = com.gonzalo.iptv.futbol.DondeVer.opciones(liga, grupos(), asignacion())
    fun gruposDe(liga: String): List<GrupoCanales> = CanalesFutbol.gruposDe(liga, grupos(), asignacion())

    /** Guarda [e] en el grupo [id] (el que estaba vacío). */
    fun guardaEn(id: String, e: Entrada): GrupoCanales? {
        pon("grupos", CanalesFutbol.gruposATexto(com.gonzalo.iptv.futbol.DondeVer.guarda(id, e, grupos())))
        return grupos().firstOrNull { it.id == id }
    }

    /** La competición no tiene grupo: se crea uno con su nombre, con [e] dentro, y se le asigna. */
    fun creaGrupoPara(liga: String, e: Entrada?): GrupoCanales {
        val (g, a, id) = com.gonzalo.iptv.futbol.DondeVer.crea(liga, Competiciones.de(liga).nombre, e, grupos(), asignacion())
        AjustesCuenta.pon(NODO, mapOf("grupos" to CanalesFutbol.gruposATexto(g), "asignacion" to CanalesFutbol.asignacionATexto(a)))
        Futbol.cambio()
        return g.first { it.id == id }
    }
}

/**
 * Los partidos, las clasificaciones, los goleadores y las fichas, de ESPN con su caché en el disco (`cobre_futbol/` en la carpeta de Nut
 * de este ordenador: no va a la cuenta). Todo bloquea: se llama fuera del hilo de la ventana. [version] sube cuando algo cambia.
 */
object Futbol {
    var version by mutableIntStateOf(0)
        private set
    @Volatile var red: Red = RedJava("Nut/${Actualizador.version}")
    @Volatile var dirPruebas: File? = null
    @Volatile private var cacheProp: CacheFutbol? = null
    private val fallos = ConcurrentHashMap<String, Long>()

    /** En las pruebas y las capturas: los datos, fijos (sin red). */
    @Volatile var fijo: Fijo? = null
    class Fijo(
        val equipos: List<Pair<Equipo, String>> = emptyList(), val rivales: List<Pair<Equipo, String>> = emptyList(), val grupos: List<GrupoCanales> = CanalesFutbol.GRUPOS,
        val partidos: List<Partido> = emptyList(), val fichas: Map<String, FichaPartido> = emptyMap(), val clasificaciones: Map<String, Clasificacion> = emptyMap(),
        val lideres: Map<String, Lideres> = emptyMap(), val partidosDia: List<Partido> = emptyList(), val plantillas: Map<String, List<com.gonzalo.iptv.futbol.Jugador>> = emptyMap(),
    )

    const val MIN = 60_000L
    const val HORA = 60 * MIN
    const val NUNCA = Long.MAX_VALUE
    const val FUENTE = "Datos: ESPN"

    /** Las horas, en la zona de este ordenador (en Irlanda, una menos que en España). */
    fun cargar() { Hora.zona = java.time.ZoneId.systemDefault() }

    private val cache: CacheFutbol get() = cacheProp ?: CacheFutbol((dirPruebas ?: File(Local.dir, "cobre_futbol")).also { it.mkdirs() }, { u -> Espn.pide(red, u) }).also { cacheProp = it }

    val fuente: FuenteFutbol = Espn { u, edad ->
        val f = fallos[u]
        val guardado = cache.guardado(u)
        if (f != null && System.currentTimeMillis() - f < 10 * MIN && guardado == null) throw IptvError("ESPN no contesta ahora.")
        try { cache.texto(u, edad) } catch (e: Exception) { fallos[u] = System.currentTimeMillis(); throw e }
    }

    fun cambio() { java.awt.EventQueue.invokeLater { version++ } }

    private fun mejor(a: Partido?, b: Partido): Partido = when {
        a == null -> b
        b.estado.enDirecto || b.estado.terminado && !a.estado.terminado -> b
        else -> a
    }

    /** Los partidos de tus equipos y tus rivales (todas sus competiciones), ordenados; lo que puede estar en juego, con el marcador del día. */
    fun partidos(fresco: Boolean = true, vivoCada: Long = MIN): List<Partido> {
        cargar()
        fijo?.let { return Orden.ordena(it.partidos, System.currentTimeMillis()) }
        val eqs = AjustesFutbol.equipos() + AjustesFutbol.rivales()
        if (eqs.isEmpty()) return emptyList()
        val porId = LinkedHashMap<String, Partido>()
        for ((e, liga) in eqs) for (comp in Competiciones.para(liga)) for (prox in listOf(true, false)) {
            val edad = if (!fresco) NUNCA else if (prox) 3 * HORA else 6 * HORA
            runCatching { fuente.calendario(comp.slug, e.id, prox, edad) }.getOrNull()?.forEach { p -> porId[p.id] = mejor(porId[p.id], p) }
        }
        val ahora = System.currentTimeMillis()
        porId.values.filter { Orden.quizaEnDirecto(it, ahora) }.map { it.liga to Hora.diaDe(it.inicio) }.distinct().forEach { (liga, dia) ->
            runCatching { fuente.marcadores(liga, dia, if (fresco) vivoCada else NUNCA) }.getOrNull()?.forEach { p -> if (porId.containsKey(p.id)) porId[p.id] = p }
        }
        return Orden.ordena(porId.values, ahora)
    }

    private val hilos = java.util.concurrent.Executors.newFixedThreadPool(4) { r -> Thread(r, "nut-futbol").apply { isDaemon = true } }

    /** Los partidos de un día: un marcador por cada competición que no escondes, y los de tus equipos ese día. */
    fun dia(dia: java.time.LocalDate, fresco: Boolean = true, vivoCada: Long = MIN): List<Partido> {
        cargar()
        val ahora = System.currentTimeMillis()
        fijo?.let { f -> return (f.partidosDia + f.partidos).filter { Hora.diaDe(it.inicio) == dia }.distinctBy { it.id } }
        val ocultas = AjustesFutbol.ocultas()
        val comps = AjustesFutbol.competiciones().filter { it.slug !in ocultas }
        val tareas = comps.map { comp -> java.util.concurrent.Callable {
            runCatching { fuente.marcadores(comp.slug, dia, if (fresco) com.gonzalo.iptv.futbol.Dia.edad(dia, ahora, false) else NUNCA) }.getOrDefault(emptyList())
        } }
        var l = hilos.invokeAll(tareas).flatMap { runCatching { it.get() }.getOrDefault(emptyList()) }
        if (fresco && l.any { Orden.quizaEnDirecto(it, ahora) }) {
            val conVivo = l.filter { Orden.quizaEnDirecto(it, ahora) }.map { it.liga }.toSet()
            val vivos = hilos.invokeAll(conVivo.map { liga -> java.util.concurrent.Callable { runCatching { fuente.marcadores(liga, dia, vivoCada) }.getOrDefault(emptyList()) } })
                .flatMap { runCatching { it.get() }.getOrDefault(emptyList()) }.associateBy { it.id }
            l = l.map { vivos[it.id] ?: it }
        }
        val deTusEquipos = runCatching { partidos(fresco = false) }.getOrDefault(emptyList()).filter { Hora.diaDe(it.inicio) == dia }
        val ids = l.map { it.id }.toSet()
        return l + deTusEquipos.filter { it.id !in ids }
    }

    /** Los partidos de una competición un día (su página). */
    fun marcadoresDe(liga: String, dia: java.time.LocalDate, fresco: Boolean = true): List<Partido>? {
        cargar()
        fijo?.let { f -> return (f.partidosDia + f.partidos).filter { it.liga == liga && Hora.diaDe(it.inicio) == dia }.distinctBy { it.id }.sortedBy { it.inicio } }
        return runCatching { fuente.marcadores(liga, dia, if (fresco) com.gonzalo.iptv.futbol.Dia.edad(dia, System.currentTimeMillis(), false) else NUNCA) }.getOrNull()?.sortedBy { it.inicio }
    }

    /** Los partidos de un solo equipo (los que vienen y los jugados), ordenados. */
    fun deEquipo(e: Equipo, liga: String, fresco: Boolean = true): List<Partido> {
        cargar()
        fijo?.let { f -> return Orden.ordena((f.partidos + f.partidosDia).filter { it.juega(e.id) }.distinctBy { it.id }, System.currentTimeMillis()) }
        val r = LinkedHashMap<String, Partido>()
        for (comp in Competiciones.para(liga)) for (prox in listOf(true, false))
            runCatching { fuente.calendario(comp.slug, e.id, prox, if (!fresco) NUNCA else if (prox) 3 * HORA else 6 * HORA) }.getOrNull()?.forEach { p -> r[p.id] = mejor(r[p.id], p) }
        return Orden.ordena(r.values, System.currentTimeMillis())
    }

    fun plantilla(e: Equipo, liga: String): List<com.gonzalo.iptv.futbol.Jugador>? {
        fijo?.let { return it.plantillas[e.id] }
        return runCatching { fuente.plantilla(Competiciones.para(liga).first().slug, e.id, 24 * HORA) }.getOrNull()
    }

    fun ficha(p: Partido, fresco: Boolean = true): FichaPartido? {
        fijo?.let { return it.fichas[p.id] }
        val ahora = System.currentTimeMillis()
        val edad = when { !fresco -> NUNCA; Orden.quizaEnDirecto(p, ahora) -> 30_000L; p.estado.terminado -> 24 * HORA; else -> 10 * MIN }
        return runCatching { fuente.ficha(p.liga, p.id, edad) }.getOrNull()
    }

    fun clasificacion(liga: String, fresco: Boolean = true): Clasificacion? {
        fijo?.let { return it.clasificaciones[liga] }
        return runCatching { fuente.clasificacion(liga, if (fresco) 6 * HORA else NUNCA) }.getOrNull()
    }

    fun lideres(liga: String, fresco: Boolean = true): Lideres? {
        fijo?.let { return it.lideres[liga] }
        return runCatching { fuente.lideres(liga, if (fresco) 12 * HORA else NUNCA) }.getOrNull()
    }

    /** Borra lo bajado de hace más de dos semanas (al abrir). */
    fun limpia() { runCatching { cache.limpia(14) } }

    // ---------------------------------------------------------------- poner un canal de un grupo (en mpv)

    /** El canal de tus listas que es [r] (por su clave o, si la lista ha cambiado las claves, por su nombre). Bloquea. */
    fun resuelve(r: RefCanal): Pair<Lista, Canal>? {
        val l = Listas.usables().firstOrNull { it.id == r.lista } ?: return null
        val cg = runCatching { Tele.cargada(l) }.getOrNull() ?: return null
        val c = cg.porClave[r.canal] ?: Buscar.normal(r.nombre).takeIf { it.isNotBlank() }?.let { n -> cg.canales.firstOrNull { Buscar.normal(it.nombre) == n } } ?: return null
        return l to c
    }

    /** La calidad que toca: la tuya, si la has elegido; si no, en el ordenador (con cable o wifi de casa), la mejor. */
    fun calidad(): Calidad = Calidades.elige(Conexion(sinLimite = true), AjustesFutbol.calidad())

    /**
     * Pone la entrada [i] del grupo [g] en mpv, en la calidad que toca (si no la tiene, la siguiente que haya; si no está en tus listas de
     * aquí, la siguiente entrada que sí). En mpv, Re Pág / Av Pág pasan por las entradas del grupo. Devuelve un aviso si no se puede. Bloquea.
     */
    fun pon(g: GrupoCanales, i: Int = 0, quiero: Calidad = calidad()): String? {
        if (g.canales.isEmpty()) return "«${g.nombre}» no tiene canales: elígelos en Nut del móvil (Ajustes → Fútbol)."
        val resueltas = g.canales.map { e -> Calidad.entries.mapNotNull { q -> e.en(q)?.let { r -> resuelve(r)?.let { q to it } } }.toMap() }
        val n = g.canales.size
        val primera = (0 until n).map { (i + it).mod(n) }.firstOrNull { resueltas[it].isNotEmpty() } ?: return "Los canales de «${g.nombre}» no están en tus listas de este ordenador."
        val l = resueltas[primera].values.first().first
        val idx = (0 until n).filter { k -> resueltas[k].values.any { it.first.id == l.id } }
        val elegidos = idx.map { k ->
            val deEsta = resueltas[k].filterValues { it.first.id == l.id }
            val e = Calidad.entries.fold(Entrada()) { acc, q -> deEsta[q]?.let { acc.con(q, g.canales[k].en(q)) } ?: acc }
            deEsta[Calidades.de(e, quiero) ?: e.calidades.first()]!!.second
        }
        return Reproductor.pon(Encargo.Directo(l, elegidos, idx.indexOf(primera)))
    }

    fun olvidaParaPruebas() { fijo = null; dirPruebas = null; cacheProp = null; fallos.clear() }
}
