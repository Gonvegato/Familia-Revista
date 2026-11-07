package com.gonzalo.cobre

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import com.gonzalo.iptv.Almacen
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.Emparejador
import com.gonzalo.iptv.Guia
import com.gonzalo.iptv.IptvError
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Programa
import com.gonzalo.iptv.Red
import com.gonzalo.iptv.RedJava
import com.gonzalo.iptv.Secretos
import com.gonzalo.iptv.TipoLista
import com.gonzalo.iptv.Xtream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Una lista con lo suyo ya leído del disco: los grupos (en su orden), los canales y su guía, con qué canal de la guía es cada uno. */
class Cargada(val lista: Lista, val grupos: List<String>, val canales: List<Canal>, val guia: Guia?) {
    val porClave: Map<String, Canal> = canales.associateBy { it.clave }
    /** Los grupos en orden: los del servidor (Xtream) y, si no, como salen en la lista. */
    val todosLosGrupos: List<String> = (grupos + canales.map { it.grupo }).distinct()
    private val emparejados: Map<String, String> = guia?.let { g -> val e = Emparejador(g.canales); canales.mapNotNull { c -> e.busca(c)?.let { c.clave to it } }.toMap() }.orEmpty()
    /** El id de la guía de un canal (o null si no tiene). */
    fun idGuia(c: Canal): String? = emparejados[c.clave]
}

/**
 * La tele: lo de tus listas en este dispositivo ([Almacen]: los canales, el videoclub y la guía, que son caché y no van a la cuenta). Baja
 * a trozos y en segundo plano (una cosa a la vez por lista), y cada cuánto: los canales una vez al día (o cuando lo pides), la guía cada
 * [AjustesGuia.horas] horas (con datos del móvil, solo si lo pides, salvo que digas lo contrario), el videoclub al entrar en él si tiene
 * más de un día. [version] sube cuando cambia algo (las pantallas lo leen y se repintan).
 */
object Tele {
    var version by mutableIntStateOf(0)
        private set
    /** Lo que se está haciendo con cada lista («Bajando los canales…»), para enseñarlo. */
    val ocupada = mutableStateMapOf<String, String>()
    /** El último error de cada lista (sin secretos). */
    val errores = mutableStateMapOf<String, String>()

    @Volatile var red: Red = RedJava("Cobre/$VERSION")
    private var appCtx: Context? = null
    @Volatile private var almacenPropio: Almacen? = null
    val almacen: Almacen get() = almacenPropio ?: error("Tele.cargar no se ha llamado")
    private val cargadas = ConcurrentHashMap<String, Cargada>()
    private val hilos = Executors.newFixedThreadPool(2) { r -> Thread(r, "cobre-tele").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private val principal by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    /** En las pruebas de pantallas: listas fijas (sin bajar nada). */
    @Volatile var fijas: Map<String, Cargada>? = null

    fun cargar(c: Context) {
        if (appCtx != null) return
        appCtx = c.applicationContext
        almacenPropio = Almacen(File(c.filesDir, "cobre_listas"), object : Red {
            override fun abre(url: String, cabeceras: Map<String, String>) = red.abre(url, cabeceras)
        }, Cifrado.de(c))
    }

    private fun avisa() { principal.post { version++ } }

    /** Lo de una lista ya leído (del disco la primera vez: bloquea; llámalo fuera del hilo principal), o null si aún no hay nada. */
    fun cargada(l: Lista): Cargada? {
        fijas?.let { return it[l.id] }
        cargadas[l.id]?.let { c -> if (c.lista == l && (c.guia == null || c.guia.vigente)) return c }
        val (grupos, canales) = almacen.canales(l)
        if (canales.isEmpty()) return null
        return Cargada(l, grupos, canales, almacen.guia(l)).also { cargadas[l.id] = it }
    }

    /** Lo de una lista si ya está en memoria (sin tocar el disco: para el hilo principal). */
    fun enMemoria(id: String): Cargada? = fijas?.get(id) ?: cargadas[id]

    private fun hayWifi(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        val cap = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** ¿Se paga lo que se baja? (datos del móvil, o una wifi con límite) */
    fun datosMoviles(c: Context): Boolean = !hayWifi(c) && runCatching {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork) != null
    }.getOrDefault(false)

    /** Que la lista se ponga al día: lo que toque (o todo, si [todo]). En segundo plano; [alAcabar] en el hilo principal. */
    fun actualiza(l: Lista, todo: Boolean = false, alAcabar: (String?) -> Unit = {}) {
        if (fijas != null || ocupada.containsKey(l.id)) { alAcabar(null); return }
        ocupada[l.id] = "Poniéndose al día…"
        hilos.execute {
            var error: String? = null
            try {
                val c = appCtx!!
                val est = almacen.estado(l, "canales")
                val sinCanales = almacen.canales(l).second.isEmpty()
                if (todo || sinCanales || System.currentTimeMillis() - est.cuando > 24 * 3_600_000L) {
                    principal.post { ocupada[l.id] = "Bajando los canales…" }
                    almacen.actualizaCanales(l)
                    cargadas.remove(l.id); avisa()
                }
                val guiaToca = todo || almacen.guiaCaducada(l, AjustesGuia.horas(c))
                if (guiaToca && (todo || !AjustesGuia.soloWifi(c) || hayWifi(c)) && almacen.fuentesGuia(l).isNotEmpty()) {
                    principal.post { ocupada[l.id] = "Bajando la guía…" }
                    runCatching { almacen.actualizaGuia(l, almacen.canales(l).second, AjustesGuia.dias(c)) }.onFailure { e -> if (todo) error = (e as? IptvError)?.message }
                    cargadas.remove(l.id); avisa()
                }
                if (todo && l.esXtream) {
                    principal.post { ocupada[l.id] = "Bajando el videoclub…" }
                    almacen.actualizaPeliculas(l); almacen.actualizaSeries(l); avisa()
                }
            } catch (e: Exception) {
                error = Secretos.tapa((e as? IptvError)?.message ?: "No se ha podido poner al día.", l.secretos)
            } finally {
                val err = error
                principal.post { ocupada.remove(l.id); if (err != null) errores[l.id] = err else errores.remove(l.id); version++; alAcabar(err) }
            }
        }
    }

    /** El videoclub de un login de Xtream, si no está o tiene más de un día (en segundo plano). */
    fun videoclub(l: Lista, alAcabar: () -> Unit = {}) {
        if (fijas != null || !l.esXtream || ocupada.containsKey(l.id)) { alAcabar(); return }
        val viejo = System.currentTimeMillis() - almacen.estado(l, "peliculas").cuando > 24 * 3_600_000L
        if (!viejo) { alAcabar(); return }
        ocupada[l.id] = "Bajando el videoclub…"
        hilos.execute {
            var err: String? = null
            try { almacen.actualizaPeliculas(l); almacen.actualizaSeries(l) } catch (e: Exception) { err = Secretos.tapa((e as? IptvError)?.message ?: "No se ha podido bajar el videoclub.", l.secretos) }
            principal.post { ocupada.remove(l.id); if (err != null) errores[l.id] = err!!; version++; alAcabar() }
        }
    }

    /** Al abrir la app: cada lista se pone al día si le toca (sin molestar). */
    fun alAbrir() { if (fijas == null) Listas.usables().forEach { actualiza(it) } }

    /** Lo leído de una lista, fuera de la memoria (se vuelve a leer: ha cambiado la lista). */
    fun suelta(l: Lista) { cargadas.remove(l.id); avisa() }

    /** Al quitar una lista: lo suyo del dispositivo, fuera. */
    fun olvida(l: Lista) { cargadas.remove(l.id); runCatching { almacen.olvida(l) }; avisa() }

    // ---------------------------------------------------------------- la guía de un canal

    private val cortas = ConcurrentHashMap<String, Pair<Long, List<Programa>>>()

    /** Los programas de un canal: de la guía de la lista o, en Xtream y si no tiene, los de su guía corta (pedidos y guardados un rato). */
    fun programas(l: Lista, c: Canal): List<Programa> {
        val cg = cargada(l)
        cg?.idGuia(c)?.let { id -> cg.guia?.programas(id)?.takeIf { it.isNotEmpty() }?.let { return it } }
        if (!l.esXtream || c.idXtream <= 0 || fijas != null) return emptyList()
        val k = "${l.id}~${c.clave}"
        cortas[k]?.let { (t, p) -> if (System.currentTimeMillis() - t < 15 * 60_000L) return p }
        val p = runCatching { Xtream(l, red).guiaCorta(c, 6) }.getOrDefault(emptyList())
        cortas[k] = System.currentTimeMillis() to p
        return p
    }

    /** Lo que se emite ahora y lo siguiente (de la guía ya bajada: no pide nada a la red; para pintar listas largas). */
    fun ahoraYDespuesRapido(cg: Cargada, c: Canal, ahora: Long = System.currentTimeMillis()): Pair<Programa?, Programa?> {
        val id = cg.idGuia(c) ?: return (cortas["${cg.lista.id}~${c.clave}"]?.second?.let { l -> l.firstOrNull { it.emitiendose(ahora) } to l.firstOrNull { it.inicio > ahora } }) ?: (null to null)
        return cg.guia?.ahoraYDespues(id, ahora) ?: (null to null)
    }

    // ---------------------------------------------------------------- ver un canal (la dirección lleva secretos: no se enseña ni se apunta)

    fun url(l: Lista, c: Canal): String = if (l.esXtream) Xtream(l, red).urlDirecto(c) else c.url

    /** Las cabeceras que se mandan al servidor del canal (y solo a él): las del canal y, si tiene, el user-agent de la lista. */
    fun cabeceras(l: Lista, c: Canal): Map<String, String> {
        val m = LinkedHashMap(c.cabeceras)
        if (l.agente.isNotBlank() && m.keys.none { it.equals("User-Agent", true) }) m["User-Agent"] = l.agente
        return m
    }

    /** Los canales que se ven (sin los de los grupos que escondes), en el orden que elijas. */
    fun visibles(cg: Cargada, ctx: Context, grupo: String? = null): List<Canal> {
        val ocultos = MiCuenta.ocultos(cg.lista.id)
        val l = cg.canales.filter { (grupo == null || it.grupo == grupo) && it.grupo !in ocultos }
        return when (AjustesDirecto.orden(ctx)) {
            AjustesDirecto.Orden.LISTA -> l
            AjustesDirecto.Orden.NUMERO -> l.sortedWith(compareBy({ if (it.numero > 0) it.numero else Int.MAX_VALUE }, { it.nombre.lowercase() }))
            AjustesDirecto.Orden.NOMBRE -> l.sortedBy { com.gonzalo.iptv.Buscar.normal(it.nombre) }
        }
    }

    fun tipoNombre(t: TipoLista) = when (t) { TipoLista.M3U -> "Lista M3U"; TipoLista.ARCHIVO -> "Archivo M3U"; TipoLista.XTREAM -> "Xtream Codes" }

    /** (Para las pruebas.) */
    fun olvidaParaPruebas() { cargadas.clear(); cortas.clear(); appCtx = null; almacenPropio = null; fijas = null; ocupada.clear(); errores.clear(); red = RedJava("Cobre/$VERSION") }
}
