package com.gonzalo.cobre

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.cobre.EspejoCuenta.Tipo
import com.gonzalo.iptv.Buscar
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.IptvError
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Red
import com.gonzalo.iptv.RedJava
import com.gonzalo.iptv.futbol.CacheFutbol
import com.gonzalo.iptv.futbol.Calidad
import com.gonzalo.iptv.futbol.Calidades
import com.gonzalo.iptv.futbol.Conexion
import com.gonzalo.iptv.futbol.Entrada
import com.gonzalo.iptv.futbol.CanalesFutbol
import com.gonzalo.iptv.futbol.Clasificacion
import com.gonzalo.iptv.futbol.Competiciones
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Lo del fútbol que eliges (Ajustes → Fútbol), en tu cuenta como lo demás de Nut (`ajustes/cobre_futbol`, ver [EspejoCuenta]): tus
 * equipos, tus grupos de canales y qué grupos tiene cada competición. **Nada secreto**: de cada canal, solo el id de su lista, su clave
 * (`x<id>` o `c<hash>`, nunca su dirección) y su nombre ([CanalesFutbol.claveValida]); de cada equipo, su id, su nombre y su escudo
 * (una imagen pública de ESPN). Tu pareja puede leer `/users/<uid>`: aquí no hay nada que no pueda ver.
 */
object AjustesFutbol {
    val GRUPO = EspejoCuenta.Grupo("cobre_futbol", "cobre_futbol", { k -> when (k) { "equipos", "grupos", "asignacion", "calidad" -> Tipo.TEXTO; else -> null } },
        alLlegar = { c -> Futbol.cambio(c) })
    private fun p(c: Context) = EspejoCuenta.prefs(c, GRUPO)

    /** Tus equipos (y la competición donde los elegiste). Sin elegir, ninguno: el Barça sale sugerido, pero lo eliges tú. */
    fun equipos(c: Context): List<Pair<Equipo, String>> { Futbol.fijo?.let { return it.equipos }; return CanalesFutbol.equiposDeTexto(p(c).getString("equipos", null)) }
    fun ponEquipos(c: Context, l: List<Pair<Equipo, String>>) { p(c).edit().putString("equipos", CanalesFutbol.equiposATexto(l)).apply(); Futbol.cambio(c) }
    fun esMio(c: Context, id: String) = equipos(c).any { it.first.id == id }
    fun alterna(c: Context, e: Equipo, liga: String) = ponEquipos(c, if (esMio(c, e.id)) equipos(c).filter { it.first.id != e.id } else equipos(c) + (e to liga))
    fun mueve(c: Context, id: String, d: Int) {
        val l = equipos(c).toMutableList(); val i = l.indexOfFirst { it.first.id == id }; val j = i + d
        if (i < 0 || j !in l.indices) return
        l.add(j, l.removeAt(i)); ponEquipos(c, l)
    }

    /** Tus grupos de canales (de serie, los cinco vacíos). */
    fun grupos(c: Context): List<GrupoCanales> { Futbol.fijo?.let { return it.grupos }; return CanalesFutbol.gruposDeTexto(p(c).getString("grupos", null)) }
    fun ponGrupos(c: Context, l: List<GrupoCanales>) { p(c).edit().putString("grupos", CanalesFutbol.gruposATexto(l)).apply(); Futbol.cambio(c) }
    fun cambiaGrupo(c: Context, id: String, f: (GrupoCanales) -> GrupoCanales) = ponGrupos(c, grupos(c).map { if (it.id == id) f(it) else it })

    /** Qué grupos tiene cada competición (lo que no hayas tocado, como de serie). */
    fun asignacion(c: Context): Map<String, List<String>> = CanalesFutbol.asignacionDeTexto(p(c).getString("asignacion", null))
    fun ponAsignacion(c: Context, a: Map<String, List<String>>) { p(c).edit().putString("asignacion", CanalesFutbol.asignacionATexto(a)).apply(); Futbol.cambio(c) }
    fun alternaAsignado(c: Context, liga: String, grupo: String) {
        val a = asignacion(c); val l = a[liga].orEmpty()
        ponAsignacion(c, a + (liga to if (grupo in l) l - grupo else l + grupo))
    }

    /** La calidad elegida a mano (null: automática, según la conexión: [Calidades.elige]). */
    fun calidad(c: Context): Calidad? = Calidad.de(p(c).getString("calidad", "").orEmpty())
    fun ponCalidad(c: Context, q: Calidad?) = p(c).edit().putString("calidad", q?.name ?: "").apply()

    /** Los grupos de una competición, en su orden. */
    fun gruposDe(c: Context, liga: String): List<GrupoCanales> = CanalesFutbol.gruposDe(liga, grupos(c), asignacion(c))
}

/**
 * El fútbol de Nut: los partidos de tus equipos, las clasificaciones, los goleadores y las fichas, de [fuente] (ESPN) con su caché en
 * el disco ([CacheFutbol], `cobre_futbol/`: es de este dispositivo, no va a la cuenta). Cada cosa se vuelve a pedir cuando toca: los
 * calendarios cada pocas horas; si hay un partido tuyo en juego, su marcador cada minuto mientras miras; la ficha de un partido en juego,
 * cada medio minuto, solo mientras está abierta. Todo bloquea: se llama fuera del hilo principal. [version] sube cuando llega algo.
 */
object Futbol {
    var version by mutableIntStateOf(0)
        private set
    @Volatile var red: Red = RedJava("Nut/$VERSION")
    @Volatile private var dir: File? = null
    @Volatile private var cacheProp: CacheFutbol? = null
    /** Lo que ha fallado hace poco (para no volver a pedirlo enseguida). */
    private val fallos = ConcurrentHashMap<String, Long>()
    private val principal by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    /** En las pruebas de pantallas y de widgets: los datos, fijos (sin red). */
    @Volatile var fijo: Fijo? = null
    class Fijo(
        val equipos: List<Pair<Equipo, String>> = emptyList(), val grupos: List<GrupoCanales> = CanalesFutbol.GRUPOS, val partidos: List<Partido> = emptyList(),
        val fichas: Map<String, FichaPartido> = emptyMap(), val clasificaciones: Map<String, Clasificacion> = emptyMap(), val lideres: Map<String, Lideres> = emptyMap(),
        val equiposDe: Map<String, List<Equipo>> = emptyMap(),
    )

    const val MIN = 60_000L
    const val HORA = 60 * MIN
    const val NUNCA = Long.MAX_VALUE
    const val FUENTE = "Datos: ESPN"

    fun cargar(c: Context) {
        if (dir != null) return
        dir = File(c.filesDir, "cobre_futbol")
    }

    private val cache: CacheFutbol get() = cacheProp ?: CacheFutbol(dir ?: error("Futbol.cargar no se ha llamado"), { u -> Espn.pide(red, u) }).also { cacheProp = it }

    val fuente: FuenteFutbol = Espn { u, edad ->
        val f = fallos[u]
        val guardado = cache.guardado(u)
        if (f != null && System.currentTimeMillis() - f < 10 * MIN && guardado == null) throw IptvError("ESPN no contesta ahora.")
        try { cache.texto(u, edad) } catch (e: Exception) { fallos[u] = System.currentTimeMillis(); throw e }
    }

    /** Algo de lo que eliges ha cambiado (aquí o en otro dispositivo): las pantallas y el widget, al día. */
    fun cambio(c: Context) { avisa(); Widgets.pedir(c) }
    private fun avisa() = principal.post { version++ }

    // ---------------------------------------------------------------- los partidos de tus equipos

    /**
     * Los partidos de tus equipos (todas las competiciones), ya ordenados ([Orden.ordena]). [fresco]: pedir lo que haya caducado (sin él,
     * solo lo guardado, salvo lo que no se tenga nunca); con algún partido que pueda estar en juego, su marcador del día ([vivoCada]).
     */
    fun partidos(c: Context, fresco: Boolean = true, vivoCada: Long = MIN): List<Partido> {
        fijo?.let { return Orden.ordena(it.partidos, System.currentTimeMillis()) }
        cargar(c)
        val eqs = AjustesFutbol.equipos(c)
        if (eqs.isEmpty()) return emptyList()
        val porId = LinkedHashMap<String, Partido>()
        for ((e, _) in eqs) for (comp in Competiciones.TODAS) for (prox in listOf(true, false)) {
            val edad = if (!fresco) NUNCA else if (prox) 3 * HORA else 6 * HORA
            runCatching { fuente.calendario(comp.slug, e.id, prox, edad) }.getOrNull()?.forEach { p -> porId[p.id] = mejor(porId[p.id], p) }
        }
        val ahora = System.currentTimeMillis()
        // Lo que puede estar jugándose ahora: el marcador del día de su competición (más al día que el calendario)
        porId.values.filter { Orden.quizaEnDirecto(it, ahora) }.map { it.liga to Hora.diaDe(it.inicio) }.distinct().forEach { (liga, dia) ->
            runCatching { fuente.marcadores(liga, dia, if (fresco) vivoCada else NUNCA) }.getOrNull()?.forEach { p -> if (porId.containsKey(p.id)) porId[p.id] = p }
        }
        return Orden.ordena(porId.values, ahora)
    }

    private fun mejor(a: Partido?, b: Partido): Partido = when {
        a == null -> b
        b.estado.enDirecto || b.estado.terminado && !a.estado.terminado -> b
        else -> a
    }

    /** ¿Hay algo que pedir? (para el widget: si los calendarios tienen más de unas horas, o hay un partido en juego). Bloquea y avisa. */
    fun refrescaSiToca(c: Context): Boolean {
        if (fijo != null || AjustesFutbol.equipos(c).isEmpty()) return false
        val antes = partidos(c, fresco = false)
        val despues = partidos(c, fresco = true, vivoCada = 2 * MIN)
        if (antes != despues) { avisa(); return true }
        return false
    }

    fun ficha(c: Context, p: Partido, fresco: Boolean = true): FichaPartido? {
        fijo?.let { return it.fichas[p.id] }
        cargar(c)
        val ahora = System.currentTimeMillis()
        val edad = when {
            !fresco -> NUNCA
            Orden.quizaEnDirecto(p, ahora) -> 30_000L
            p.estado.terminado -> 24 * HORA
            else -> 10 * MIN
        }
        return runCatching { fuente.ficha(p.liga, p.id, edad) }.getOrNull()
    }

    fun clasificacion(c: Context, liga: String, fresco: Boolean = true): Clasificacion? {
        fijo?.let { return it.clasificaciones[liga] }
        cargar(c)
        return runCatching { fuente.clasificacion(liga, if (fresco) 6 * HORA else NUNCA) }.getOrNull()
    }

    fun lideres(c: Context, liga: String, fresco: Boolean = true): Lideres? {
        fijo?.let { return it.lideres[liga] }
        cargar(c)
        return runCatching { fuente.lideres(liga, if (fresco) 12 * HORA else NUNCA) }.getOrNull()
    }

    fun equipos(c: Context, liga: String): List<Equipo>? {
        fijo?.let { return it.equiposDe[liga] }
        cargar(c)
        return runCatching { fuente.equipos(liga, 7 * 24 * HORA) }.getOrNull()
    }

    /** Borra lo bajado (Ajustes → Avanzado) y lo de hace más de dos semanas (al abrir). */
    fun limpia(todo: Boolean = false) { runCatching { if (todo) cache.borraTodo() else cache.limpia(14) } }

    // ---------------------------------------------------------------- poner un canal de un grupo

    /** El canal de tus listas que es [r] (por su clave o, si la lista ha cambiado las claves, por su nombre). Bloquea. */
    fun resuelve(r: RefCanal): Pair<Lista, Canal>? {
        val l = Listas.usables().firstOrNull { it.id == r.lista } ?: return null
        val cg = runCatching { Tele.cargada(l) }.getOrNull() ?: return null
        val c = cg.porClave[r.canal] ?: Buscar.normal(r.nombre).takeIf { it.isNotBlank() }?.let { n -> cg.canales.firstOrNull { Buscar.normal(it.nombre) == n } } ?: return null
        return l to c
    }

    /**
     * Cómo está la conexión ahora: wifi o cable sin límite, el ahorro de datos del sistema, lo que dice Android que da el enlace y lo que
     * ha medido el reproductor hace poco ([Medida]).
     */
    fun conexion(ctx: Context): Conexion {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java) ?: return Conexion(sinLimite = false)
        val cap = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val sinLimite = cap?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true && !cm.isActiveNetworkMetered
        val ahorro = runCatching { cm.restrictBackgroundStatus == android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED }.getOrDefault(false)
        return Conexion(sinLimite, ahorro, cap?.linkDownstreamBandwidthKbps ?: 0, Medida.kbps())
    }

    /** La calidad que toca ahora (la tuya, si la has elegido; si no, por la conexión). */
    fun calidad(ctx: Context): Calidad = Calidades.elige(conexion(ctx), AjustesFutbol.calidad(ctx))

    /**
     * Pone la entrada [i] del grupo [g] en la calidad que toca (si no la tiene, la siguiente que haya; si no está en tus listas de aquí,
     * la siguiente entrada que sí). En el reproductor, arriba y abajo pasan por las entradas del grupo (las de la misma lista), «Calidad»
     * cambia de calidad, y si un canal no arranca se prueba otra calidad y luego otra entrada. Devuelve un aviso si no se puede. Bloquea.
     */
    fun pon(ctx: Context, g: GrupoCanales, i: Int = 0, quiero: Calidad = calidad(ctx)): String? {
        if (g.canales.isEmpty()) return "«${g.nombre}» no tiene canales: elígelos en Ajustes → Fútbol."
        // Cada entrada, con sus calidades resueltas a canales de tus listas
        val resueltas = g.canales.map { e -> Calidad.entries.mapNotNull { q -> e.en(q)?.let { r -> resuelve(r)?.let { q to it } } }.toMap() }
        val n = g.canales.size
        val primera = (0 until n).map { (i + it).mod(n) }.firstOrNull { resueltas[it].isNotEmpty() } ?: return "Los canales de «${g.nombre}» no están en tus listas de este dispositivo."
        val l = resueltas[primera].values.first().first
        // Las de esa lista (el reproductor va con una lista)
        val idx = (0 until n).filter { k -> resueltas[k].values.any { it.first.id == l.id } }
        val entradas = idx.map { k -> Calidad.entries.fold(Entrada()) { e, q -> resueltas[k][q]?.takeIf { it.first.id == l.id }?.let { e.con(q, g.canales[k].en(q)) } ?: e } }
        val canales = idx.map { k -> resueltas[k].filterValues { it.first.id == l.id }.mapValues { it.value.second } }
        val elegidos = entradas.mapIndexed { k, e -> canales[k][Calidades.de(e, quiero) ?: e.calidades.first()]!! }
        val dentro = idx.indexOf(primera)
        Reproducir.pon(ctx, Encargo.Directo(l, elegidos, dentro, PlanFutbol(g.nombre, entradas, canales, quiero)))
        return null
    }

    /** Lo mismo, por el id del grupo (desde el widget). */
    fun ponGrupo(ctx: Context, id: String, i: Int = 0): String? = AjustesFutbol.grupos(ctx).firstOrNull { it.id == id }?.let { pon(ctx, it, i) } ?: "Ese grupo de canales ya no existe."

    /** Buscar canales en tus listas por su nombre (sin mirar acentos ni mayúsculas), como en «Buscar». Bloquea. */
    fun buscaCanales(q: String, tope: Int = 60): List<Pair<Lista, Canal>> {
        val n = Buscar.normal(q)
        if (n.length < 2) return emptyList()
        return Listas.usables().flatMap { l -> runCatching { Tele.cargada(l) }.getOrNull()?.canales.orEmpty().asSequence().filter { Buscar.normal(it.nombre).contains(n) }.take(tope).map { l to it }.toList() }
    }

    fun olvidaParaPruebas() { fijo = null; dir = null; cacheProp = null; fallos.clear(); red = RedJava("Nut/$VERSION") }
}
