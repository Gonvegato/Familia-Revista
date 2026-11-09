package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/*
 * El modelo de Hiedra (docs/MODELO.md): un juego es una cosa con identidad propia (su id viaja con la cuenta), y cómo se abre es una lista
 * de lanzadores (la ROM, la app, la web, el juego del PC). Las horas se guardan por fuente: las de este dispositivo, las de tus otros
 * móviles (se suman) y las externas, Steam y el PC (se queda el máximo, nunca se suman dos veces).
 */

/** En qué punto estás con el juego. «Pasado» = terminado; «Abandonado» = lo dejaste. */
enum class Estado(val nombre: String) { JUGANDO("Jugando"), PENDIENTE("Pendiente"), PASADO("Pasado"), ABANDONADO("Abandonado") }

/** Cómo se abre un juego: una ROM (con su emulador), una app de Android, una web o un juego del PC (de la biblioteca que Atrio del PC publica). */
enum class TipoLanzador { ROM, APP, WEB, PC }

/** Los menús pequeños de las secciones: de dónde es el juego. */
enum class Menu(val nombre: String) { PC("PC"), EMULACION("Emulación"), ANDROID("Android"), WEB("Web") }

/**
 * Una manera de abrir un juego. Su [clave] es lo que identifica al lanzador en todos tus dispositivos (sirve para no duplicar al añadir, para
 * importar y para que Azabache enlace lo que valoró por esa clave). Dónde está el archivo de una ROM NO va aquí: es de cada dispositivo ([Archivos]).
 */
data class Lanzador(
    val tipo: TipoLanzador,
    /** ROM: el sistema (id del catálogo), el nombre del archivo sin extensión y el emulador elegido solo para este juego ("" = el del sistema). */
    val sistema: String = "", val archivo: String = "", val emulador: String = "",
    /** App: el paquete. */
    val paquete: String = "",
    /** Web: la dirección y la pantalla (0 automática, 1 horizontal, 2 vertical). */
    val url: String = "", val orientacion: Int = 0,
    /** PC: su clave en la biblioteca del PC (la de Atrio) y el PC donde está. */
    val pcKey: String = "", val pcDev: String = "",
) {
    val clave: String get() = when (tipo) {
        TipoLanzador.ROM -> "rom:$sistema:${Catalogo.norm(archivo)}"
        TipoLanzador.APP -> "app:$paquete"
        TipoLanzador.WEB -> "web:" + url.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
        TipoLanzador.PC -> "pc:$pcKey"
    }
    val menu: Menu get() = when (tipo) { TipoLanzador.ROM -> Menu.EMULACION; TipoLanzador.APP -> Menu.ANDROID; TipoLanzador.WEB -> Menu.WEB; TipoLanzador.PC -> Menu.PC }

    fun aJson(): JSONObject = JSONObject().put("tp", tipo.name).apply {
        if (sistema.isNotBlank()) put("s", sistema); if (archivo.isNotBlank()) put("a", archivo); if (emulador.isNotBlank()) put("em", emulador)
        if (paquete.isNotBlank()) put("pk", paquete); if (url.isNotBlank()) put("u", url); if (orientacion != 0) put("o", orientacion)
        if (pcKey.isNotBlank()) put("pck", pcKey); if (pcDev.isNotBlank()) put("pcd", pcDev)
    }

    companion object {
        fun deJson(o: JSONObject): Lanzador? {
            val t = TipoLanzador.entries.firstOrNull { it.name == o.optString("tp") } ?: return null
            return Lanzador(t, o.optString("s"), o.optString("a"), o.optString("em"), o.optString("pk"), o.optString("u"), o.optInt("o"), o.optString("pck"), o.optString("pcd"))
        }
        fun rom(sistema: String, archivo: String) = Lanzador(TipoLanzador.ROM, sistema = sistema, archivo = archivo)
        fun app(paquete: String) = Lanzador(TipoLanzador.APP, paquete = paquete)
        fun web(url: String, orientacion: Int = 0) = Lanzador(TipoLanzador.WEB, url = url, orientacion = orientacion)
        fun pc(key: String, dev: String) = Lanzador(TipoLanzador.PC, pcKey = key, pcDev = dev)
    }
}

/** Lo jugado desde una fuente: tiempo, veces y cada día («AAAA-MM-DD» → ms). */
data class Horas(val ms: Long = 0L, val veces: Int = 0, val dias: Map<String, Long> = emptyMap()) {
    val vacias: Boolean get() = ms <= 0L && veces <= 0 && dias.isEmpty()
    operator fun plus(o: Horas) = Horas(ms + o.ms, veces + o.veces, sumaDias(dias, o.dias))
    /** De dos lecturas de la misma fuente externa, la mayor (día a día): nunca se suma dos veces. */
    fun max(o: Horas) = Horas(maxOf(ms, o.ms), maxOf(veces, o.veces), (dias.keys + o.dias.keys).associateWith { maxOf(dias[it] ?: 0L, o.dias[it] ?: 0L) })

    fun aJson(): JSONObject = JSONObject().put("ms", ms).put("v", veces).put("dd", JSONObject().also { d -> dias.entries.sortedByDescending { it.key }.take(400).forEach { (k, v) -> d.put(k, v) } })
    companion object {
        fun deJson(o: JSONObject?): Horas = if (o == null) Horas() else Horas(o.optLong("ms"), o.optInt("v"), o.optJSONObject("dd")?.let { d -> d.keys().asSequence().associateWith { k -> d.optLong(k) } } ?: emptyMap())
        fun sumaDias(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> = (a.keys + b.keys).associateWith { (a[it] ?: 0L) + (b[it] ?: 0L) }
    }
}

/**
 * Las fuentes externas de horas: la API de Steam, cada PC con Atrio (`pc:<dispositivo del PC>`) y lo jugado en cada dispositivo de Carmesí
 * antes de pasarse a Hiedra (`carmesi:<dispositivo>`: se importa una vez y ya no cambia; viaja con la cuenta y se mezcla por máximo).
 */
object Fuentes {
    const val STEAM = "steam"
    fun pc(dev: String) = "pc:$dev"
    fun esPc(f: String) = f.startsWith("pc:")
    fun carmesi(dev: String) = "carmesi:$dev"
    fun esCarmesi(f: String) = f.startsWith("carmesi:")
}

/** Un juego de la biblioteca. Lo que no se dice aquí (dónde está cada ROM en este móvil) está en [Archivos]. */
data class Juego(
    val id: String,
    val titulo: String,
    /** En qué se juega (PC, Switch, PS5…): decide el menú pequeño si no hay lanzador. */
    val plataforma: String = "",
    val estado: Estado = Estado.PENDIENTE,
    val favorito: Boolean = false,
    /** Cuándo lo pasaste (ms; 0 = no). */
    val finalizado: Long = 0L,
    val anadido: Long = 0L,
    val ultima: Long = 0L,
    /** Última vez que se cambió lo editable (ms): al sincronizar gana el cambio más reciente. */
    val mod: Long = 0L,
    /** Tu portada: archivo en la carpeta de la app (de este móvil; viaja por kDrive). */
    val portada: String = "",
    /** Una portada de internet (SteamGridDB, la biblioteca del PC…): se usa cuando no has puesto ninguna. */
    val portadaUrl: String = "",
    /**
     * El encuadre de antes de Bastet 0.8 (zoom y desplazamiento, en la cuenta e igual en todos los dispositivos). Ya no se cambia: se lee una
     * vez para pasarlo a [encPortada], que es de cada dispositivo.
     */
    val zoom: Float = 1f, val ox: Float = 0f, val oy: Float = 0f,
    /**
     * El fondo: una imagen a lo ancho para el cartel de la portada y la apertura de la ficha (archivo de la app, o una dirección de internet
     * mientras no ha bajado); "" = sin fondo (se usa el color de la portada). En la cuenta, como la portada: `fondo` (hash) y `fu` (de dónde salió).
     */
    val fondo: String = "",
    val fondoUrl: String = "",
    /**
     * Qué parte se ve de la portada y del fondo ([Encuadre]): la imagen se guarda entera y viaja con la cuenta; el encuadre es de CADA
     * dispositivo ([Encuadres]) y vuelve al centro si la imagen cambia.
     */
    val encPortada: Encuadre = Encuadre(),
    val encFondo: Encuadre = Encuadre(),
    /** Lo que suele durar (minutos; 0 = no se sabe): la historia, con extras y al completo. */
    val durHistoria: Int = 0, val durExtras: Int = 0, val durCompleto: Int = 0,
    /** Claves por las que se conoció al juego además de las de sus lanzadores (`juego:<título>` de Atrio, las de lanzadores que ya no tiene…). */
    val otrasClaves: List<String> = emptyList(),
    val lanzadores: List<Lanzador> = emptyList(),
    /** Lo jugado en ESTE dispositivo; en tus otros móviles (por dispositivo; se suma); y las fuentes externas (Steam, cada PC; máximo). */
    val propias: Horas = Horas(),
    val ajenas: Map<String, Horas> = emptyMap(),
    val externas: Map<String, Horas> = emptyMap(),
) {
    /** Todas las claves por las que se le conoce: las de sus lanzadores y las de antes. */
    val claves: List<String> get() = (lanzadores.map { it.clave } + otrasClaves).distinct()
    val msJugados: Long get() = propias.ms + ajenas.values.sumOf { it.ms } + externas.values.sumOf { it.ms }
    val veces: Int get() = propias.veces + ajenas.values.sumOf { it.veces } + externas.values.sumOf { it.veces }
    /** Lo jugado cada día, sumando todas las fuentes. */
    fun dias(): Map<String, Long> = (ajenas.values + externas.values).fold(propias.dias) { acc, h -> Horas.sumaDias(acc, h.dias) }
    /** Las horas de una fuente externa (Steam, un PC), o nada. */
    fun externa(f: String): Horas = externas[f] ?: Horas()

    val sinLanzador: Boolean get() = lanzadores.isEmpty()
    val rom: Lanzador? get() = lanzadores.firstOrNull { it.tipo == TipoLanzador.ROM }
    val app: Lanzador? get() = lanzadores.firstOrNull { it.tipo == TipoLanzador.APP }
    val web: Lanzador? get() = lanzadores.firstOrNull { it.tipo == TipoLanzador.WEB }
    val pc: Lanzador? get() = lanzadores.firstOrNull { it.tipo == TipoLanzador.PC }
    /** Es (también) del PC: se juega en el PC o en streaming. */
    val enPc: Boolean get() = pc != null
    /** Los menús pequeños donde sale: los de sus lanzadores; sin ninguno, el de su plataforma. */
    val menus: Set<Menu> get() = lanzadores.map { it.menu }.toSet().ifEmpty { setOf(menuDePlataforma(plataforma)) }
    /** El menú principal (para el nodo «tiempo»): el primero de los suyos. */
    val menu: Menu get() = menus.first()
    /** Tiene una ROM, pero su archivo no está en este móvil (y nada más con que abrirlo aquí). */
    val enOtroDispositivo: Boolean get() = rom != null && !Archivos.tiene(rom!!.clave) && lanzadores.none { it.tipo == TipoLanzador.APP || it.tipo == TipoLanzador.WEB }
    /** ¿Se puede abrir desde este móvil ahora? */
    val abrible: Boolean get() = lanzadores.any { it.tipo == TipoLanzador.APP || it.tipo == TipoLanzador.WEB || (it.tipo == TipoLanzador.ROM && Archivos.tiene(it.clave)) }
    /** ¿Has movido o ampliado la portada? */
    val encuadrada: Boolean get() = !encPortada.neutro
    /** Cuánto llevas de la historia (0–1), si se sabe lo que dura. */
    val avance: Float? get() = if (durHistoria > 0 && msJugados > 0) (msJugados / 60_000f / durHistoria).coerceIn(0f, 1f) else null
    /** El lanzador con el que se abre desde el móvil por defecto: la ROM que está aquí, la app, la web; si no, el del PC. */
    val principal: Lanzador? get() = lanzadores.firstOrNull { it.tipo == TipoLanzador.ROM && Archivos.tiene(it.clave) } ?: app ?: web ?: lanzadores.firstOrNull()
    fun lanzador(clave: String): Lanzador? = lanzadores.firstOrNull { it.clave == clave }
}

fun menuDePlataforma(plataforma: String): Menu {
    val p = Catalogo.norm(plataforma)
    return when {
        p.isEmpty() -> Menu.PC
        listOf("pc", "steam", "epic", "gog", "windows", "linux", "mac", "ordenador").any { p.startsWith(it) || p.endsWith(it) } || p.contains("steamdeck") -> Menu.PC
        listOf("android", "movil", "ios", "iphone", "ipad", "tablet").any { p.contains(it) } -> Menu.ANDROID
        listOf("web", "navegador", "browser").any { p.contains(it) } -> Menu.WEB
        else -> Menu.EMULACION
    }
}

/** La clave de ficha de un título (la que Atrio y Carmesí daban a un juego sin lanzador): sirve para importar y para Azabache. */
fun claveFicha(titulo: String) = "juego:" + Catalogo.norm(titulo)

/** Un juego nuevo con un título (y, si se quiere, un lanzador). */
fun nuevoJuego(titulo: String, plataforma: String = "", lanzador: Lanzador? = null, anadido: Long = System.currentTimeMillis()): Juego =
    Juego(Biblioteca.nuevoId(), titulo.trim(), plataforma = plataforma.trim(), anadido = anadido, lanzadores = listOfNotNull(lanzador))

/** Cambia el estado (y lo que va con él: «Pasado» lleva su fecha). */
fun Juego.conEstado(e: Estado, ahora: Long = System.currentTimeMillis()): Juego =
    copy(estado = e, finalizado = if (e == Estado.PASADO) (if (finalizado > 0) finalizado else ahora) else 0L)

/** Añade un lanzador (si no lo tenía ya, por su clave). */
fun Juego.conLanzador(l: Lanzador): Juego = if (lanzadores.any { it.clave == l.clave }) this else copy(lanzadores = lanzadores + l)

/** Quita un lanzador: su clave se queda entre las de antes (así Azabache y la importación siguen reconociendo el juego). */
fun Juego.sinLanzador(clave: String): Juego = copy(lanzadores = lanzadores.filter { it.clave != clave }, otrasClaves = (otrasClaves + clave).distinct())

/** Corrige las horas jugadas a mano: lo que falta o sobra lo pone este dispositivo (nunca por debajo de lo que sumen las demás fuentes). */
fun Juego.conTotalMs(total: Long): Juego {
    val otros = ajenas.values.sumOf { it.ms } + externas.values.sumOf { it.ms }
    return copy(propias = propias.copy(ms = (total - otros).coerceAtLeast(0L)))
}

/** Lo que se edita de un juego y viaja con la cuenta (gana el cambio más reciente). */
fun compartidos(j: Juego) = listOf(j.titulo, j.plataforma, j.estado, j.favorito, j.finalizado, j.portadaUrl, j.zoom, j.ox, j.oy, j.durHistoria, j.durExtras, j.durCompleto, j.otrasClaves, j.lanzadores)

/**
 * Une dos juegos que son el mismo: [destino] se queda con los lanzadores y las claves de [origen], y las horas se juntan (las propias se suman;
 * las de cada dispositivo y fuente, se suman si son de dispositivos distintos y se queda el máximo si son de la misma fuente externa).
 */
fun unirJuegos(destino: Juego, origen: Juego): Juego {
    val peso = mapOf(Estado.PENDIENTE to 0, Estado.ABANDONADO to 1, Estado.JUGANDO to 2, Estado.PASADO to 3)
    val estado = if ((peso[origen.estado] ?: 0) > (peso[destino.estado] ?: 0)) origen.estado else destino.estado
    val ajenas = (destino.ajenas.keys + origen.ajenas.keys).associateWith { (destino.ajenas[it] ?: Horas()) + (origen.ajenas[it] ?: Horas()) }
    val externas = (destino.externas.keys + origen.externas.keys).associateWith { (destino.externas[it] ?: Horas()).max(origen.externas[it] ?: Horas()) }
    var u = destino.copy(
        titulo = destino.titulo.ifBlank { origen.titulo }, plataforma = destino.plataforma.ifBlank { origen.plataforma }, estado = estado,
        finalizado = if (estado == Estado.PASADO) maxOf(destino.finalizado, origen.finalizado) else 0L, favorito = destino.favorito || origen.favorito,
        portada = destino.portada.ifBlank { origen.portada }, portadaUrl = destino.portadaUrl.ifBlank { origen.portadaUrl },
        zoom = if (destino.portada.isBlank()) origen.zoom else destino.zoom, ox = if (destino.portada.isBlank()) origen.ox else destino.ox, oy = if (destino.portada.isBlank()) origen.oy else destino.oy,
        durHistoria = if (destino.durHistoria > 0) destino.durHistoria else origen.durHistoria, durExtras = if (destino.durExtras > 0) destino.durExtras else origen.durExtras, durCompleto = if (destino.durCompleto > 0) destino.durCompleto else origen.durCompleto,
        propias = destino.propias + origen.propias, ajenas = ajenas, externas = externas,
        ultima = maxOf(destino.ultima, origen.ultima), anadido = listOf(destino.anadido, origen.anadido).filter { it > 0 }.minOrNull() ?: destino.anadido,
        otrasClaves = (destino.otrasClaves + origen.otrasClaves + origen.lanzadores.map { it.clave }).distinct()
    )
    origen.lanzadores.forEach { u = u.conLanzador(it) }
    return u.copy(otrasClaves = u.otrasClaves.filter { k -> u.lanzadores.none { it.clave == k } })
}

/** Una carpeta de ROMs que diste (para volver a escanearla). De este dispositivo. */
data class Carpeta(val uri: String, val nombre: String)

/** Dónde está cada ROM en ESTE móvil (clave del lanzador → documento y ruta real). No viaja con la cuenta. */
object Archivos {
    private class Sitio(val uri: String, val ruta: String)
    private val sitios = HashMap<String, Sitio>()
    private var archivo: File? = null

    fun cargar(c: Context) {
        archivo = File(c.filesDir, "archivos.json")
        sitios.clear()
        runCatching { JSONObject(archivo!!.readText()).let { o -> o.keys().forEach { k -> o.optJSONObject(k)?.let { s -> sitios[k] = Sitio(s.optString("uri"), s.optString("ruta")) } } } }
    }
    private fun guardar() { archivo?.let { f -> runCatching { f.writeText(JSONObject().also { o -> sitios.forEach { (k, s) -> o.put(k, JSONObject().put("uri", s.uri).put("ruta", s.ruta)) } }.toString()) } } }

    fun tiene(clave: String): Boolean = sitios[clave]?.uri?.isNotBlank() == true
    fun uri(clave: String): String = sitios[clave]?.uri.orEmpty()
    fun ruta(clave: String): String = sitios[clave]?.ruta.orEmpty()
    fun poner(clave: String, uri: String, ruta: String) { sitios[clave] = Sitio(uri, ruta); guardar() }
    fun quitar(clave: String) { sitios.remove(clave); guardar() }
    fun limpiarParaPruebas() { sitios.clear() }
    /** Las claves de las ROMs que están aquí. */
    fun claves(): Set<String> = sitios.filterValues { it.uri.isNotBlank() }.keys
}

/** El estado en la cuenta (el mismo en toda la familia: ver nube/ESQUEMA.md). */
val Estado.enCuenta: String get() = when (this) { Estado.PENDIENTE -> "pendiente"; Estado.JUGANDO -> "en_curso"; Estado.PASADO -> "terminado"; Estado.ABANDONADO -> "abandonado" }
fun estadoDeCuenta(s: String): Estado = when (s) { "en_curso" -> Estado.JUGANDO; "terminado" -> Estado.PASADO; "abandonado" -> Estado.ABANDONADO; else -> Estado.PENDIENTE }

/**
 * Tu biblioteca de juegos: la colección `juegos` de la cuenta (la misma en todos tus dispositivos y en Azabache), con las horas de la
 * colección `tiempo` (un nodo por juego y fuente: este dispositivo, tus otros móviles, Steam, cada PC) y lo que es solo de este
 * móvil: dónde está cada ROM ([Archivos]) y las carpetas de ROMs.
 */
object Biblioteca {
    val juegos = mutableStateListOf<Juego>()
    val carpetas = mutableStateListOf<Carpeta>()
    var cargada by mutableStateOf(false)
    private var appCtx: Context? = null
    private var col: com.gonzalo.familia.nucleo.Coleccion? = null
    private var tiempo: com.gonzalo.familia.nucleo.Coleccion? = null
    /** Solo para leer lo de antes (la migración y las pruebas de la mezcla vieja). */
    val borrados = HashMap<String, Long>()
    private val principal by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    /** En las pruebas de pantallas: una lista fija (sin la cuenta debajo). */
    @Volatile private var congelada = false
    fun fijaParaPruebas(l: List<Juego>) { congelada = true; juegos.clear(); juegos.addAll(l) }

    fun cargar(c: Context) {
        if (cargada) return
        appCtx = c.applicationContext
        Cuenta.cargar(c)
        Archivos.cargar(c)
        leeLocal(c)
        col = com.gonzalo.familia.LaFamilia.coleccion("juegos").also { it.alCambiar { principal.post { refresca() } } }
        tiempo = com.gonzalo.familia.LaFamilia.coleccion("tiempo").also { it.alCambiar { principal.post { refresca() } } }
        refresca()
        cargada = true
        runCatching { unirRepetidos() }
    }

    private fun archivoLocal(c: Context) = File(c.filesDir, "biblioteca_local.json")
    private fun leeLocal(c: Context) {
        val o = runCatching { JSONObject(archivoLocal(c).readText()) }.getOrNull() ?: return
        val cs = o.optJSONArray("carpetas") ?: JSONArray()
        carpetas.clear()
        for (i in 0 until cs.length()) cs.optJSONObject(i)?.let { x -> carpetas.add(Carpeta(x.optString("uri"), x.optString("nombre"))) }
    }
    private fun guardaLocal() {
        val c = appCtx ?: return
        val o = JSONObject().put("carpetas", JSONArray().apply { carpetas.forEach { put(JSONObject().put("uri", it.uri).put("nombre", it.nombre)) } })
        runCatching { val f = archivoLocal(c); val t = File(f.path + ".tmp"); t.writeText(o.toString()); t.renameTo(f) }
    }

    /** ¿Es esta fuente un dispositivo (las horas de un móvil con Hiedra) o una fuente externa (Steam, un PC, lo de Carmesí)? */
    private fun esExterna(f: String) = f == Fuentes.STEAM || Fuentes.esPc(f) || Fuentes.esCarmesi(f)

    private fun horasDe(n: com.gonzalo.familia.nucleo.Nodo): Horas {
        val dias = HashMap<String, Long>()
        n.campos.forEach { (k, v) -> if (k.length == 11 && k[0] == 'd' && k[5] == '-') dias[k.substring(1)] = (v as? Number)?.toLong() ?: 0L }
        return Horas(n.entero("total"), n.entero("veces").toInt(), dias)
    }

    private fun camposHoras(j: Juego, f: String, h: Horas): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["ref"] = j.id; m["fuente"] = f; m["total"] = h.ms; m["veces"] = h.veces.toLong(); m["t"] = j.titulo; m["menu"] = j.menu.name.lowercase()
        h.dias.forEach { (d, ms) -> m["d$d"] = ms }
        return m
    }

    /** Un juego tal como está en la cuenta (con sus horas). */
    fun deNodo(n: com.gonzalo.familia.nucleo.Nodo, horas: List<com.gonzalo.familia.nucleo.Nodo>): Juego {
        val c = appCtx
        val propias = horas.firstOrNull { it.texto("fuente") == Cuenta.dispositivo }?.let { horasDe(it) } ?: Horas()
        val ajenas = horas.filter { val f = it.texto("fuente"); f.isNotBlank() && f != Cuenta.dispositivo && !esExterna(f) }.associate { it.texto("fuente") to horasDe(it) }
        val externas = horas.filter { esExterna(it.texto("fuente")) }.associate { it.texto("fuente") to horasDe(it) }
        val portada = c?.let { com.gonzalo.familia.PortadasFamilia.archivo(it, n.texto("portada")) }.orEmpty()
        val zoom = n.decimal("ez").toFloat().takeIf { n["ez"] != null } ?: 1f; val ox = n.decimal("eox").toFloat(); val oy = n.decimal("eoy").toFloat()
        // El encuadre de antes (en la cuenta) pasa una vez a ser el de este dispositivo
        if (c != null && portada.isNotBlank()) Encuadres.migrarDeAntes(c, n.ref, portada, zoom, ox, oy)
        val (fondo, fondoUrl) = fondoDe(c, n)
        return Juego(
            id = n.ref, titulo = n.texto("t"), plataforma = n.texto("pl"), estado = estadoDeCuenta(n.texto("estado")), favorito = n.si("fav"),
            finalizado = com.gonzalo.familia.nucleo.Fechas.ms(n.entero("fin")), anadido = com.gonzalo.familia.nucleo.Fechas.ms(n.entero("an")), ultima = com.gonzalo.familia.nucleo.Fechas.ms(n.entero("ul")), mod = n.m.values.maxOrNull() ?: 0L,
            portada = portada, portadaUrl = n.texto("pu"), zoom = zoom, ox = ox, oy = oy, fondo = fondo, fondoUrl = fondoUrl,
            encPortada = c?.let { Encuadres.de(it, n.ref, false, portada) } ?: Encuadre(), encFondo = c?.let { Encuadres.de(it, n.ref, true, fondo) } ?: Encuadre(),
            durHistoria = n.entero("dh").toInt(), durExtras = n.entero("de").toInt(), durCompleto = n.entero("dc").toInt(),
            otrasClaves = n.lista("k").map { it.toString() }.filter { it.isNotBlank() },
            lanzadores = n.lista("ln").mapNotNull { (it as? Map<*, *>)?.let { m -> Lanzador.deJson(JSONObject(m)) } },
            propias = propias, ajenas = ajenas, externas = externas,
        )
    }

    /** El hash de la portada de un juego en la cuenta ("" si no tiene). */
    fun hashPortada(id: String): String = col?.nodo(id)?.texto("portada").orEmpty()

    /**
     * El fondo de un juego: la imagen de la cuenta (`fondo`, por su hash) si ya está en el móvil; si aún no ha bajado, la dirección de la que
     * salió (`fu`). Si el juego nunca ha tenido fondo puesto aquí (no hay campo `fondo`), el que le pusieras en Jonsu (`az.hero`, si es de internet).
     */
    fun fondoDe(c: Context?, n: com.gonzalo.familia.nucleo.Nodo): Pair<String, String> {
        val fu = n.texto("fu")
        if (n["fondo"] != null) {
            val h = n.texto("fondo")
            if (h.isBlank() || h == SIN_FONDO) return "" to ""
            val f = c?.let { com.gonzalo.familia.PortadasFamilia.archivo(it, h) }.orEmpty()
            return f.ifBlank { fu.takeIf { it.startsWith("http") }.orEmpty() } to fu
        }
        val hero = n.texto("az").takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it).optString("hero") }.getOrNull() }.orEmpty()
        return if (hero.startsWith("http")) hero to hero else "" to ""
    }

    /** «Sin fondo, a propósito» (en la cuenta un campo vacío no existe, y entonces volvería el de Jonsu). */
    const val SIN_FONDO = "-"

    /** El hash del fondo de un juego en la cuenta ("" si no tiene). */
    fun hashFondo(id: String): String = col?.nodo(id)?.texto("fondo").orEmpty().takeIf { it != SIN_FONDO }.orEmpty()

    /** El fondo de un juego (el hash de una imagen ya guardada en la cuenta; "" = sin fondo) y de dónde salió. */
    fun ponerFondo(id: String, hash: String, url: String) {
        col?.cambia(id, mapOf("fondo" to hash.ifBlank { SIN_FONDO }, "fu" to url))
        if (congelada || col == null) { val i = juegos.indexOfFirst { it.id == id }; val c = appCtx
            if (i >= 0) juegos[i] = juegos[i].copy(fondo = if (hash.isBlank()) "" else c?.let { com.gonzalo.familia.PortadasFamilia.archivo(it, hash) }.orEmpty().ifBlank { url }, fondoUrl = url, encFondo = Encuadre()) }
        else refresca()
    }

    /** Guarda el encuadre de la portada ([fondo] = false) o del fondo de un juego, en ESTE dispositivo (la cuenta no se toca). */
    fun ponerEncuadre(id: String, fondo: Boolean, e: Encuadre) {
        val j = por(id) ?: return
        val c = appCtx ?: return
        Encuadres.poner(c, id, fondo, if (fondo) j.fondo else j.portada, e)
        val i = juegos.indexOfFirst { it.id == id }
        if (i >= 0) juegos[i] = if (fondo) juegos[i].copy(encFondo = e) else juegos[i].copy(encPortada = e)
        appCtx?.let { Widgets.pedir(it) }
    }

    /** Lo que Azabache dice de este juego: su nota (0–100; 0 = sin valorar) y su reseña. */
    fun valoracion(id: String): Pair<Int, String> = col?.nodo(id)?.let { it.entero("nota").toInt() to it.texto("resena") } ?: (0 to "")

    /** Vuelve a montar la lista con lo que hay en la cuenta (se llama sola cuando cambia algo, venga de donde venga). */
    fun refresca() {
        if (congelada) return
        val cc = col ?: return
        val porJuego = tiempo?.vivos()?.groupBy { it.texto("ref") } ?: emptyMap()
        val vivos = cc.vivos()
        // Las fechas que llegaron de Atrio como número de día (y quedaron en 1970): se corrigen en la cuenta, una vez
        for (n in vivos) {
            val c = LinkedHashMap<String, Any?>()
            for (k in listOf("fin", "an", "ul")) n.entero(k).let { if (com.gonzalo.familia.nucleo.Fechas.esDia(it)) c[k] = com.gonzalo.familia.nucleo.Fechas.ms(it) }
            if (c.isNotEmpty()) runCatching { cc.cambia(n.ref, c) }
        }
        val nueva = vivos.filter { it.texto("t").isNotBlank() }.map { deNodo(it, porJuego[it.ref].orEmpty()) }.sortedByDescending { it.anadido }
        if (nueva != juegos.toList()) { juegos.clear(); juegos.addAll(nueva) }
        appCtx?.let { c ->
            Widgets.pedir(c)
            // Las portadas y los fondos de la cuenta que aún no están en este móvil
            val faltan = cc.vivos().filter { com.gonzalo.familia.PortadasFamilia.archivo(c, it.texto("portada")).isBlank() }.map { it.texto("portada") } +
                cc.vivos().map { it.texto("fondo") }.filter { it.isNotBlank() && it != SIN_FONDO && com.gonzalo.familia.PortadasFamilia.archivo(c, it).isBlank() }
            com.gonzalo.familia.PortadasFamilia.baja(faltan) { refresca() }
        }
    }

    /** Lo que se guarda de un juego en la cuenta (campo → valor). La portada va aparte (es una imagen de la cuenta). */
    fun campos(j: Juego): Map<String, Any?> = mapOf(
        "t" to j.titulo, "pl" to j.plataforma, "estado" to j.estado.enCuenta, "fav" to j.favorito, "fin" to j.finalizado, "an" to j.anadido, "ul" to j.ultima,
        "pu" to j.portadaUrl, "ez" to j.zoom.toDouble(), "eox" to j.ox.toDouble(), "eoy" to j.oy.toDouble(),
        "dh" to j.durHistoria.toLong(), "de" to j.durExtras.toLong(), "dc" to j.durCompleto.toLong(),
        "k" to j.otrasClaves, "ln" to j.lanzadores.map { com.gonzalo.familia.nucleo.Json.lee(it.aJson().toString()) }, "menu" to j.menu.name.lowercase(),
    )

    fun guardar() { guardaLocal(); refresca() }

    /** Guarda un juego: solo sube a la cuenta lo que ha cambiado de verdad (campo a campo), y sus horas por fuente. */
    fun poner(j: Juego) {
        val cc = col
        if (congelada || cc == null) {
            val i = juegos.indexOfFirst { it.id == j.id }
            if (i >= 0) juegos[i] = j else juegos.add(0, j)
            cc?.cambia(j.id, campos(j))
            return
        }
        val previo = por(j.id)
        val cambios = campos(j).toMutableMap()
        if (cc.nodo(j.id)?.borrado == true) cambios["b"] = 0L
        // La portada: un archivo de este móvil que no es la imagen de la cuenta es una portada nueva (de la galería, de internet…)
        val c = appCtx
        if (c != null && j.portada != previo?.portada) {
            if (j.portada.isBlank()) cambios["portada"] = ""
            else if (!j.portada.contains("/familia/img/")) runCatching { File(j.portada).takeIf { it.isFile }?.readBytes()?.let { com.gonzalo.familia.PortadasFamilia.guardar(it) } }.getOrNull()?.let { cambios["portada"] = it }
        }
        cc.cambia(j.id, cambios)
        // Las horas: las de este dispositivo y las externas que se conozcan aquí (Steam, el PC)
        val t = tiempo
        if (t != null) {
            if (!j.propias.vacias) t.cambia("${j.id}~${Cuenta.dispositivo}", camposHoras(j, Cuenta.dispositivo, j.propias))
            j.externas.forEach { (f, h) -> if (!h.vacias) t.cambia("${j.id}~$f", camposHoras(j, f, h)) }
        }
        refresca()
    }

    /** Las horas de otras fuentes que eran de otro juego pasan a este (al unir dos juegos que eran el mismo). */
    private fun pasaHoras(destino: String, origen: String) {
        val t = tiempo ?: return
        for (n in t.vivos().filter { it.texto("ref") == origen }) {
            val f = n.texto("fuente")
            if (f == Cuenta.dispositivo || t.nodo("$destino~$f") != null) continue
            t.pon("$destino~$f", n.campos.filterKeys { it != "b" } + ("ref" to destino))
        }
    }

    /** Cambia un juego a partir de su versión más reciente (así dos cambios seguidos no se pisan). */
    fun cambiar(id: String, f: (Juego) -> Juego) { por(id)?.let { poner(f(it)) } }

    fun quitar(id: String) {
        por(id)?.let { j -> j.lanzadores.forEach { if (it.tipo == TipoLanzador.ROM) Archivos.quitar(it.clave) } }
        if (congelada || col == null) { por(id)?.let { borradosSinCuenta += it }; juegos.removeAll { it.id == id }; col?.borra(id); return }
        col?.borra(id)
        refresca()
    }

    /** (Sin la cuenta debajo, en las pruebas: lo que se ha borrado.) */
    private val borradosSinCuenta = mutableListOf<Juego>()
    fun olvidaBorradosParaPruebas() { borradosSinCuenta.clear() }

    private fun clavesDe(n: com.gonzalo.familia.nucleo.Nodo): List<String> =
        n.lista("ln").mapNotNull { (it as? Map<*, *>)?.let { m -> Lanzador.deJson(JSONObject(m))?.clave } } + n.lista("k").map { it.toString() }

    /**
     * El último juego que borraste con esta clave (o con este nombre), tal como estaba: un juego borrado sigue en la cuenta como borrado,
     * con sus datos. Sirve para que lo que entra solo (lo del PC) no vuelva después de borrarlo, y para recuperarlo si lo añades a mano.
     */
    fun borradoCon(clave: String, titulo: String = ""): Juego? {
        val n = Catalogo.norm(titulo)
        fun esEse(claves: List<String>, t: String) = clave in claves || (n.isNotEmpty() && Catalogo.norm(t) == n)
        val cc = col ?: return borradosSinCuenta.lastOrNull { esEse(it.claves, it.titulo) }
        val nodo = cc.todos().values.filter { it.borrado && it.texto("t").isNotBlank() && esEse(clavesDe(it), it.texto("t")) }
            .maxByOrNull { it.entero(com.gonzalo.familia.nucleo.Nodo.B) } ?: return null
        return deNodo(nodo, tiempo?.vivos()?.filter { it.texto("ref") == nodo.ref }.orEmpty())
    }

    /** ¿Borraste un juego con esta clave (o con este nombre)? */
    fun loBorraste(clave: String, titulo: String = ""): Boolean = borradoCon(clave, titulo) != null

    /**
     * ¿Está este juego tal como entró, sin que nadie lo haya tocado? Ni su estado, ni su nombre, ni su portada, ni la nota o la reseña de
     * Azabache, ni nada de lo que se elige a mano ha cambiado después de añadirlo (lo que se actualiza solo —las horas del PC, la última
     * vez, la portada de internet— no cuenta).
     */
    fun sinTocar(id: String): Boolean {
        val cc = col ?: return por(id) != null
        val n = cc.nodo(id)?.takeIf { !it.borrado } ?: return false
        val alta = n.m["an"] ?: return false
        return listOf("estado", "fav", "t", "pl", "fin", "ez", "eox", "eoy", "dh", "de", "dc", "k", "portada", "nota", "resena")
            .all { (n.m[it] ?: 0L) <= alta + 5_000L }
    }
    fun por(id: String): Juego? = juegos.firstOrNull { it.id == id }

    /** Añade una carpeta de ROMs (de este móvil). */
    fun ponerCarpeta(cp: Carpeta) { if (carpetas.none { it.uri == cp.uri }) { carpetas.add(cp); guardaLocal() } }

    /** El juego al que corresponde una clave (la de uno de sus lanzadores, o una de antes). */
    fun porClave(k: String): Juego? = juegos.firstOrNull { it.lanzadores.any { l -> l.clave == k } } ?: juegos.firstOrNull { k in it.otrasClaves }

    /** El juego que se llama así (por su título normalizado), si lo hay. */
    fun porTitulo(titulo: String): Juego? = Catalogo.norm(titulo).takeIf { it.isNotEmpty() }?.let { n -> juegos.firstOrNull { Catalogo.norm(it.titulo) == n } }

    /** El juego al que iría un lanzador nuevo: el que ya lo tiene (por su clave) o el que se llama igual que [titulo]. */
    fun destinoDe(l: Lanzador, titulo: String): Juego? = porClave(l.clave) ?: porTitulo(titulo)

    /**
     * Añade un lanzador: si ya hay un juego con esa clave, se queda como está; si hay uno que se llama igual, se le añade; si no, nace un juego
     * nuevo con ese lanzador. Devuelve el juego y si era nuevo.
     */
    fun anadirLanzador(titulo: String, l: Lanzador, plataforma: String = "", anadido: Long = System.currentTimeMillis()): Pair<Juego, Boolean> {
        porClave(l.clave)?.let { return it to false }
        porTitulo(titulo)?.let { j -> val u = j.conLanzador(l); poner(u); return (por(j.id) ?: u) to false }
        val n = nuevoJuego(titulo, plataforma, l, anadido)
        poner(n)
        return (por(n.id) ?: n) to true
    }

    /**
     * Juegos repetidos: dos fichas con una clave en común (la misma ROM, la misma app, el mismo juego del PC o de Steam, el mismo título) y la
     * misma plataforma (o una sin plataforma) son el mismo juego. Pasaba al importar de Atrio o de Carmesí en dos móviles: cada uno creaba su
     * copia, con su propio id. Se juntan en la de id más bajo (la misma en todos tus móviles, así no se pisan) y las demás se quitan (también
     * en tus otros móviles). Devuelve cuántas ha juntado.
     */
    fun unirRepetidos(): Int {
        val grupos = gruposRepetidos(juegos.toList())
        var n = 0
        for (g in grupos) {
            val queda = g.minOf { it }
            for (otro in g) if (otro != queda && unir(queda, otro) != null) n++
        }
        return n
    }

    /** Une dos juegos (ver [unirJuegos]): [origenId] desaparece dentro de [destinoId]. Devuelve el resultado o null si falta alguno. */
    fun unir(destinoId: String, origenId: String): Juego? {
        val d = por(destinoId) ?: return null; val o = por(origenId) ?: return null
        if (d.id == o.id) return d
        val u = unirJuegos(d, o)
        pasaHoras(destinoId, origenId)
        quitar(o.id)
        poner(u.copy(ajenas = d.ajenas, externas = u.externas))
        return por(destinoId) ?: u
    }

    /** La copia de seguridad (Ajustes → Copia): tus juegos tal cual. */
    fun aTexto(): String = JSONObject().apply {
        put("version", 1)
        put("juegos", JSONArray().apply { juegos.forEach { put(aJson(it)) } })
        put("carpetas", JSONArray().apply { carpetas.forEach { put(JSONObject().put("uri", it.uri).put("nombre", it.nombre)) } })
    }.toString()

    /** Importa una copia de seguridad: los juegos que no tengas se añaden. */
    fun leer(txt: String, reemplazar: Boolean = true) {
        val o = JSONObject(txt)
        val js = o.optJSONArray("juegos") ?: JSONArray()
        val ids = juegos.map { it.id }.toSet()
        for (i in 0 until js.length()) runCatching { aJuego(js.getJSONObject(i)) }.getOrNull()?.takeIf { reemplazar || it.id !in ids }?.let { poner(it) }
        val cs = o.optJSONArray("carpetas") ?: JSONArray()
        for (i in 0 until cs.length()) cs.optJSONObject(i)?.let { x -> ponerCarpeta(Carpeta(x.getString("uri"), x.optString("nombre"))) }
    }

    fun aJson(j: Juego) = JSONObject().apply {
        put("id", j.id); put("titulo", j.titulo); put("pl", j.plataforma); put("est", j.estado.name); put("fav", j.favorito); put("fin", j.finalizado)
        put("anadido", j.anadido); put("ultima", j.ultima); put("mod", j.mod); put("portada", j.portada); put("pu", j.portadaUrl)
        put("ez", j.zoom.toDouble()); put("eox", j.ox.toDouble()); put("eoy", j.oy.toDouble()); put("dh", j.durHistoria); put("de", j.durExtras); put("dc", j.durCompleto)
        put("k", JSONArray().also { a -> j.otrasClaves.forEach { a.put(it) } })
        put("ln", JSONArray().also { a -> j.lanzadores.forEach { a.put(it.aJson()) } })
        put("hp", j.propias.aJson())
        put("ha", JSONObject().also { o -> j.ajenas.forEach { (k, v) -> o.put(k, v.aJson()) } })
        put("hx", JSONObject().also { o -> j.externas.forEach { (k, v) -> o.put(k, v.aJson()) } })
    }

    fun aJuego(o: JSONObject) = Juego(
        id = o.getString("id"), titulo = o.getString("titulo"), plataforma = o.optString("pl"),
        estado = Estado.entries.firstOrNull { it.name == o.optString("est") } ?: Estado.PENDIENTE, favorito = o.optBoolean("fav"), finalizado = o.optLong("fin"),
        anadido = o.optLong("anadido"), ultima = o.optLong("ultima"), mod = o.optLong("mod"), portada = o.optString("portada"), portadaUrl = o.optString("pu"),
        zoom = o.optDouble("ez", 1.0).toFloat(), ox = o.optDouble("eox", 0.0).toFloat(), oy = o.optDouble("eoy", 0.0).toFloat(),
        durHistoria = o.optInt("dh"), durExtras = o.optInt("de"), durCompleto = o.optInt("dc"),
        otrasClaves = o.optJSONArray("k")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList(),
        lanzadores = o.optJSONArray("ln")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Lanzador::deJson) } } ?: emptyList(),
        propias = Horas.deJson(o.optJSONObject("hp")),
        ajenas = o.optJSONObject("ha")?.let { a -> a.keys().asSequence().associateWith { k -> Horas.deJson(a.optJSONObject(k)) } } ?: emptyMap(),
        externas = o.optJSONObject("hx")?.let { a -> a.keys().asSequence().associateWith { k -> Horas.deJson(a.optJSONObject(k)) } } ?: emptyMap(),
    )

    fun nuevoId(): String = "j_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)

    /** (La migración: los juegos de antes con la hora en que se cambiaron de verdad; sus horas, con la de ahora.) */
    fun migra(j: Juego, hora: Long) {
        val cc = col ?: return
        cc.pon(j.id, campos(j), hora.coerceAtLeast(1L))
        val t = tiempo ?: return
        if (!j.propias.vacias && t.nodo("${j.id}~${Cuenta.dispositivo}") == null) t.pon("${j.id}~${Cuenta.dispositivo}", camposHoras(j, Cuenta.dispositivo, j.propias))
        (j.ajenas + j.externas).forEach { (f, h) -> if (!h.vacias && t.nodo("${j.id}~$f") == null) t.pon("${j.id}~$f", camposHoras(j, f, h), 1L) }
    }

    /** (La migración: una portada de antes —un archivo de este móvil— pasa a la cuenta.) */
    fun migraPortada(id: String, bytes: ByteArray, hora: Long) {
        val h = com.gonzalo.familia.PortadasFamilia.guardar(bytes) ?: return
        col?.pon(id, mapOf("portada" to h), hora.coerceAtLeast(1L))
    }

    /** (La migración: un juego borrado antes.) */
    fun migraBorrado(id: String, hora: Long) { col?.pon(id, mapOf("b" to hora), hora.coerceAtLeast(1L)) }
}

/**
 * Ajustes de Hiedra que no son del tema: las portadas automáticas (`libretro`) y el emulador elegido para cada consola (`emu:<sistema>`).
 * Viajan con tu cuenta (`ajustes/hiedra_ajustes`, un campo por ajuste): si eliges un emulador que en otro móvil no está, allí se usa el
 * automático hasta que lo instales.
 */
object Ajustes {
    val GRUPO: EspejoCuenta.Grupo = EspejoCuenta.Grupo("hiedra_ajustes", "hiedra_ajustes", tipo = { k ->
        when { k == "libretro" -> EspejoCuenta.Tipo.SI_NO; k.startsWith("emu:") -> EspejoCuenta.Tipo.TEXTO; else -> null }
    }, alLlegar = { cargar(it); Widgets.pedir(it) })

    /** Emulador elegido por sistema: la etiqueta del comando; sin entrada = automático. */
    val emuladorPorSistema = HashMap<String, String>()
    var portadasLibretro by mutableStateOf(true)
    private fun p(c: Context) = EspejoCuenta.prefs(c, GRUPO)

    fun cargar(c: Context) {
        val p = p(c)
        // (hasta la 0.6.0 iban todos juntos en «emus»: ahora cada consola por su cuenta, para que dos móviles que eligen el de consolas
        // distintas no se pisen; el paso es aquí, sin tocar la cuenta: lo de antes sube luego con su hora)
        val b = EspejoCuenta.base(c, GRUPO)
        b.getString("emus", null)?.let { s ->
            val ed = b.edit()
            runCatching { JSONObject(s).let { o -> o.keys().forEach { k -> if (!b.contains("emu:$k") && o.optString(k).isNotBlank()) ed.putString("emu:$k", o.getString(k)) } } }
            ed.remove("emus").commit()
        }
        portadasLibretro = p.getBoolean("libretro", true)
        emuladorPorSistema.clear()
        p.all.forEach { (k, v) -> if (k.startsWith("emu:") && v is String && v.isNotBlank()) emuladorPorSistema[k.removePrefix("emu:")] = v }
    }

    fun guardar(c: Context) {
        val p = p(c)
        val ed = p.edit()
        if (p.contains("libretro") || !portadasLibretro) ed.putBoolean("libretro", portadasLibretro)
        p.all.keys.filter { it.startsWith("emu:") && it.removePrefix("emu:") !in emuladorPorSistema }.forEach { ed.remove(it) }
        emuladorPorSistema.forEach { (s, l) -> ed.putString("emu:$s", l) }
        ed.apply()
    }
}


/** Los grupos de ids que son el mismo juego (ver [Biblioteca.unirRepetidos]). Pura (se prueba). */
fun gruposRepetidos(juegos: List<Juego>): List<Set<String>> {
    val padre = HashMap<String, String>()
    fun raiz(x: String): String { var r = x; while (padre[r] != null && padre[r] != r) r = padre[r]!!; return r }
    fun junta(a: String, b: String) { val ra = raiz(a); val rb = raiz(b); if (ra != rb) padre[maxOf(ra, rb)] = minOf(ra, rb) }
    juegos.forEach { padre[it.id] = it.id }
    val porClave = HashMap<String, MutableList<Juego>>()
    juegos.forEach { j -> j.claves.forEach { k -> porClave.getOrPut(k) { ArrayList() }.add(j) } }
    for (l in porClave.values) for (i in l.indices) for (k in i + 1 until l.size) {
        val a = l[i]; val b = l[k]
        if (a.id == b.id) continue
        val pa = a.plataforma.trim().lowercase(); val pb = b.plataforma.trim().lowercase()
        if (pa.isBlank() || pb.isBlank() || pa == pb) junta(a.id, b.id)
    }
    return juegos.map { it.id }.groupBy { raiz(it) }.values.filter { it.size > 1 }.map { it.toSet() }
}
