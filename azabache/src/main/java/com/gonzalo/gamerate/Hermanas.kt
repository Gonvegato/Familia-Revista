package com.gonzalo.gamerate

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/*
 * Las apps hermanas: Hiedra (los juegos), Marfil (los libros) y Cobalto (lo que escribes). Azabache es la revista: no añade nada, LEE lo que
 * cada una sube a tu cuenta (sus contratos: hiedra/docs/CONTRATO-DATOS.md, lectura-app/docs/CONTRATO-DATOS.md, cobalto/docs/CONTRATO-DATOS.md)
 * y lo refleja en sus propias listas, donde se valora:
 *  - cada juego de Hiedra es una ficha de Azabache (en curso si está jugando, pendiente o abandonado; valorada si está pasado); su nombre,
 *    plataforma, portada, horas y duraciones vienen de Hiedra en cada sincronización, y la nota, la reseña y las casillas son de aquí;
 *  - cada libro de Marfil, igual (leyendo, pendiente o leído);
 *  - una ficha que viene de Hiedra o Marfil y aún no tiene nota está «sin valorar» (todas las notas a 0): se ve así y no cuenta en las cifras;
 *  - el tiempo de cada día (jugado, leído) y las palabras escritas (Cobalto) salen de sus nodos ligeros y alimentan el Diario y las barras;
 *  - los capítulos terminados de Cobalto se leen aquí (solo lectura).
 * Las fichas reflejadas llevan un id fijo (el de siempre de las fichas propias: sale del nombre), así dos móviles con Azabache crean la misma.
 */
object Hermanas {
    const val PAQ_HIEDRA = "com.gonzalo.hiedra"
    const val PAQ_MARFIL = "com.gonzalo.marfil"
    const val PAQ_COBALTO = "com.gonzalo.cobalto"
    private const val STALE = 150L * 86_400_000L

    /** Sube cuando llega algo nuevo (la app vuelve a leer sus listas). */
    var version by mutableIntStateOf(0)
        private set
    private val mirado = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val pidiendo = java.util.concurrent.atomic.AtomicBoolean(false)
    /** Lo que hay pendiente de mirar: nombres de hermanas, o «*» = todas. */
    private val pendientes = java.util.Collections.synchronizedSet(HashSet<String>())
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /**
     * Mira lo de las hermanas dentro de poco (varias peticiones seguidas se juntan en una). Con [solo], solo lo de esa hermana (es lo que
     * se pide al oír su sello: no se vuelve a bajar lo de las otras).
     */
    fun pronto(c: Context, ya: Boolean = false, solo: String? = null) {
        pendientes += solo ?: "*"
        if (!pidiendo.compareAndSet(false, true)) return
        val app = c.applicationContext
        scope.launch {
            try {
                kotlinx.coroutines.delay(400)
                val que: Set<String>; synchronized(pendientes) { que = HashSet(pendientes); pendientes.clear() }
                runCatching { refrescar(app, Store(app), ya, if ("*" in que) null else que) }
            } finally { pidiendo.set(false); if (pendientes.isNotEmpty()) pronto(app, ya) }
        }
    }
    private fun prefs(c: Context) = c.getSharedPreferences("hermanas", Context.MODE_PRIVATE)
    private fun enPantalla(f: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) f() else Handler(Looper.getMainLooper()).post(f) }

    /** ¿Está instalada esa hermana en este móvil? */
    fun instalada(c: Context, paquete: String) = runCatching { c.packageManager.getLaunchIntentForPackage(paquete) != null }.getOrDefault(false)
    /** Abre esa hermana (si está). */
    fun abrir(c: Context, paquete: String, destino: String = ""): Boolean {
        val i = c.packageManager.getLaunchIntentForPackage(paquete) ?: return false
        if (destino.isNotBlank()) i.putExtra("go", destino)
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }
    /** Abre Cobalto en esa obra (entiende el extra «obra», el id de Escribir). */
    fun escribirEnCobalto(c: Context, obra: String): Boolean {
        val i = c.packageManager.getLaunchIntentForPackage(PAQ_COBALTO) ?: return false
        if (obra.isNotBlank()) i.putExtra("obra", obra)
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }
    /** Abre Marfil en sus «Escritos» (Marfil 0.12.0 o más nueva), donde los capítulos de Cobalto se leen con su lector. */
    fun leerEnMarfil(c: Context, obra: String = ""): Boolean {
        val i = c.packageManager.getLaunchIntentForPackage(PAQ_MARFIL) ?: return false
        i.putExtra("go", "escritos"); if (obra.isNotBlank()) i.putExtra("obra", obra)
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }

    // ================================================================ lo que sube cada una (sin red: se puede probar)

    /** Un juego tal como lo tiene Hiedra (mezclando sus dispositivos). */
    class JuegoH(val id: String, val titulo: String, val plataforma: String, val estado: String, val favorito: Boolean, val finalizado: Long, val anadido: Long, val ultima: Long,
                 val portadaUrl: String, val claves: List<String>, val dh: Int, val de: Int, val dc: Int, val ms: Long, val mod: Long)

    private fun horasDe(o: JSONObject?): Pair<Long, Map<String, Long>> {
        if (o == null) return 0L to emptyMap()
        val dd = o.optJSONObject("dd")
        return o.optLong("ms") to (dd?.keys()?.asSequence()?.associateWith { dd.optLong(it) } ?: emptyMap())
    }

    /** Los juegos de Hiedra a partir de lo que subió cada dispositivo (dispositivo → su biblioteca). Gana el cambio más reciente; las horas se juntan. */
    fun juegosDe(blobs: Map<String, JSONObject>): List<JuegoH> {
        val borrados = HashMap<String, Long>()
        blobs.values.forEach { b -> b.optJSONObject("borrados")?.let { o -> for (k in o.keys()) borrados[k] = maxOf(borrados[k] ?: 0L, o.optLong(k)) } }
        val fichas = LinkedHashMap<String, JSONObject>()
        val propias = HashMap<String, HashMap<String, Long>>()      // id → dispositivo → ms
        val externas = HashMap<String, HashMap<String, Long>>()     // id → fuente → ms (máximo)
        for ((dev, b) in blobs) {
            val a = b.optJSONArray("juegos") ?: continue
            for (i in 0 until a.length()) {
                val g = a.optJSONObject(i) ?: continue
                val id = g.optString("id"); if (id.isBlank() || g.optString("t").isBlank()) continue
                if ((borrados[id] ?: 0L) > g.optLong("mod")) continue
                val cur = fichas[id]
                if (cur == null || g.optLong("mod") > cur.optLong("mod")) fichas[id] = g
                val msp = g.optLong("msp"); if (msp > 0) propias.getOrPut(id) { HashMap() }[dev] = msp
                g.optJSONObject("ex")?.let { ex -> for (f in ex.keys()) { val ms = ex.optJSONObject(f)?.optLong("ms") ?: 0L; val m = externas.getOrPut(id) { HashMap() }; m[f] = maxOf(m[f] ?: 0L, ms) } }
            }
        }
        return fichas.map { (id, g) ->
            val claves = g.optJSONArray("k")?.let { k -> (0 until k.length()).map { k.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
            val lanz = g.optJSONArray("ln")?.let { l -> (0 until l.length()).mapNotNull { l.optJSONObject(it) } } ?: emptyList()
            val clavesLanz = lanz.mapNotNull { x -> when (x.optString("tp")) {
                "ROM" -> "rom:${x.optString("s")}:${normName(x.optString("a"))}"; "APP" -> "app:${x.optString("pk")}"; "PC" -> "pc:${x.optString("pck")}"
                "WEB" -> "web:" + x.optString("u").lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/'); else -> null } }
            JuegoH(id, g.optString("t"), g.optString("pl"), g.optString("es").ifBlank { "PENDIENTE" }, g.optBoolean("f"), g.optLong("fi"), g.optLong("an"), g.optLong("ul"), g.optString("pu").ifBlank { g.optString("pa") },
                (clavesLanz + claves).distinct(), g.optInt("dh"), g.optInt("de"), g.optInt("dc"),
                (propias[id]?.values?.sum() ?: 0L) + (externas[id]?.values?.sum() ?: 0L), g.optLong("mod"))
        }.sortedBy { normName(it.titulo) }
    }

    /** Un libro tal como lo tiene Marfil. */
    class LibroM(val clave: String, val titulo: String, val autor: String, val estado: String, val favorito: Boolean, val anadido: Long, val ultima: Long, val fraccion: Float,
                 val paginas: Int, val terminados: List<Long>, val ms: Long, val mod: Long, val portadaUrl: String = "") {
        /** El nombre del libro en la cuenta (los 20 primeros hex del SHA-1 de la clave). */
        val kid: String get() = kidDe(clave)
    }
    fun kidDe(clave: String): String = java.security.MessageDigest.getInstance("SHA-1").digest(clave.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

    /** Los libros de Marfil a partir de lo que subió cada dispositivo. */
    fun librosDe(blobs: Map<String, JSONObject>): List<LibroM> {
        val borrados = HashMap<String, Long>()
        blobs.values.forEach { b -> b.optJSONObject("borrados")?.let { o -> for (k in o.keys()) borrados[k] = maxOf(borrados[k] ?: 0L, o.optLong(k)) } }
        val fichas = LinkedHashMap<String, JSONObject>()
        val ms = HashMap<String, HashMap<String, Long>>()
        for ((dev, b) in blobs) {
            val a = b.optJSONArray("libros") ?: continue
            for (i in 0 until a.length()) {
                val g = a.optJSONObject(i) ?: continue
                val k = g.optString("k"); if (k.isBlank() || g.optString("t").isBlank()) continue
                if ((borrados[k] ?: 0L) > g.optLong("mod")) continue
                val cur = fichas[k]
                if (cur == null || g.optLong("mod") > cur.optLong("mod")) fichas[k] = g
                val msp = g.optLong("msp"); if (msp > 0) ms.getOrPut(k) { HashMap() }[dev] = msp
            }
        }
        return fichas.map { (k, g) ->
            val tm = g.optJSONArray("tm")?.let { t -> (0 until t.length()).map { t.optLong(it) }.filter { it > 0 } } ?: emptyList()
            LibroM(k, g.optString("t"), g.optString("a"), g.optString("e").ifBlank { "PENDIENTE" }, g.optBoolean("f"), g.optLong("an"), g.optLong("ul"), g.optDouble("fr", 0.0).toFloat(),
                g.optInt("pg"), tm, ms[k]?.values?.sum() ?: 0L, g.optLong("mod"), g.optString("pu"))
        }.sortedBy { normName(it.titulo) }
    }

    // ================================================================ el reflejo en las listas de Azabache (sin red: se puede probar)

    /** Las fichas de una categoría tras reflejar lo de una hermana. */
    class Espejo(val games: List<Game>, val plays: List<Play>, val cambio: Boolean)

    /** ¿Una ficha reflejada a la que aún no has puesto nota? */
    fun sinValorar(g: Game) = g.general == 0 && g.gfx == 0 && g.story == 0 && g.music == 0 && g.gameplay == 0

    private fun diaDe(ms: Long): Long = if (ms > 0) diaDe(ms, ZoneId.systemDefault()) else 0L

    /**
     * Refleja los juegos de Hiedra en las listas de juegos. [enlaces]: id de Hiedra → id de la ficha de aquí (se completa). Lo que ya tenías
     * (una ficha con el mismo nombre) se enlaza y se completa; lo que no, nace con el id de siempre de las fichas propias. La nota, la reseña y
     * las casillas nunca se tocan; lo que Hiedra manda (nombre, plataforma, estado, horas, duraciones, fechas, favorito) sí. Una ficha ya
     * valorada no vuelve a «en curso» aunque Hiedra diga que estás jugando (la nota es tuya).
     */
    fun reflejarJuegos(juegos: List<JuegoH>, games: List<Game>, plays: List<Play>, enlaces: MutableMap<String, Long>, portada: (JuegoH) -> String = { it.portadaUrl }, puestas: MutableMap<String, String> = HashMap(), ahora: Long = System.currentTimeMillis()): Espejo {
        val gs = ArrayList(games); val ps = ArrayList(plays)
        var cambio = false
        fun busca(j: JuegoH): Long? {
            enlaces[j.id]?.let { return it }
            val n = normName(j.titulo)
            val hit = gs.firstOrNull { normName(it.name) == n }?.id ?: ps.firstOrNull { normName(it.name) == n }?.id
                ?: j.claves.firstOrNull { it.startsWith("juego:") }?.let { k -> (gs.map { it.name } + ps.map { it.name }).firstOrNull { "juego:" + normName(it) == k } }?.let { customItemId(it) }
            return hit
        }
        for (j in juegos) {
            val id = busca(j) ?: customItemId(j.titulo)
            if (enlaces[j.id] != id) { enlaces[j.id] = id; cambio = true }
            val gi = gs.indexOfFirst { it.id == id }
            val pi = ps.indexOfFirst { it.id == id }
            val cover = portada(j)
            if (j.estado == "PASADO") {
                val fin = diaDe(j.finalizado)
                if (gi >= 0) {
                    val g = gs[gi]
                    val n = g.copy(name = j.titulo, platform = j.plataforma.ifBlank { g.platform }, fav = j.favorito || g.fav, minutes = maxOf(g.minutes, (j.ms / 60_000L).toInt()),
                        ttbMain = if (j.dh > 0) j.dh else g.ttbMain, ttbExtra = if (j.de > 0) j.de else g.ttbExtra, ttbFull = if (j.dc > 0) j.dc else g.ttbFull,
                        end = if (fin > 0) fin else g.end, start = if (g.start > 0) g.start else diaDe(j.anadido), cover = if (cover.isNotBlank() && (g.cover.isBlank() || g.cover == puestas[j.id])) cover else g.cover)
                    if (n != g) { gs[gi] = n.copy(updated = ahora); cambio = true }
                } else {
                    // Estaba en curso (o no estaba): pasa a valorados; si tenía apuntes con nota, su media
                    val p = if (pi >= 0) ps.removeAt(pi) else null
                    val b = p?.baremo()
                    gs += Game(id, j.titulo, cover, b?.general ?: 0, b?.gfx ?: 0, b?.story ?: 0, b?.music ?: 0, b?.gameplay ?: 0, "", hero = p?.hero.orEmpty(),
                        start = p?.start?.takeIf { it > 0 } ?: diaDe(j.anadido), end = fin, creator = p?.creator.orEmpty(), fav = j.favorito, platform = j.plataforma,
                        minutes = maxOf(p?.minutes ?: 0, (j.ms / 60_000L).toInt()), updated = ahora, ttbMain = j.dh, ttbExtra = j.de, ttbFull = j.dc, logo = p?.logo.orEmpty())
                    cambio = true
                }
            } else {
                if (gi >= 0) continue     // (valorada aquí: la nota manda)
                val status = when (j.estado) { "JUGANDO" -> 1; "ABANDONADO" -> 2; else -> 0 }
                if (pi >= 0) {
                    val p = ps[pi]
                    val n = p.copy(name = j.titulo, status = status, platform = j.plataforma.ifBlank { p.platform }, minutes = maxOf(p.minutes, (j.ms / 60_000L).toInt()),
                        ttbMain = if (j.dh > 0) j.dh else p.ttbMain, ttbExtra = if (j.de > 0) j.de else p.ttbExtra, ttbFull = if (j.dc > 0) j.dc else p.ttbFull,
                        start = if (p.start > 0) p.start else if (status == 1) diaDe(j.ultima.takeIf { it > 0 } ?: j.anadido) else 0L,
                        end = if (status == 2) (if (p.end > 0) p.end else diaDe(j.ultima.takeIf { it > 0 } ?: ahora)) else 0L,
                        cover = if (cover.isNotBlank() && (p.cover.isBlank() || p.cover == puestas[j.id])) cover else p.cover)
                    if (n != p) { ps[pi] = n.copy(updated = ahora); cambio = true }
                } else {
                    ps += Play(id, j.titulo, cover, "", status, emptyList(), start = if (status == 1) diaDe(j.ultima.takeIf { it > 0 } ?: j.anadido) else 0L,
                        end = if (status == 2) diaDe(j.ultima.takeIf { it > 0 } ?: ahora) else 0L, platform = j.plataforma, minutes = (j.ms / 60_000L).toInt(), updated = ahora,
                        ttbMain = j.dh, ttbExtra = j.de, ttbFull = j.dc)
                    cambio = true
                }
            }
            if (cover.isNotBlank()) puestas[j.id] = cover   // (la portada que vino de la hermana: si la cambias tú, ya no se pisa)
        }
        return Espejo(gs, ps, cambio)
    }

    /** Refleja los libros de Marfil en las listas de libros (misma lógica que los juegos; «leído» = valorado, con la última fecha en que lo terminaste). */
    fun reflejarLibros(libros: List<LibroM>, games: List<Game>, plays: List<Play>, enlaces: MutableMap<String, Long>, portada: (LibroM) -> String = { "" }, puestas: MutableMap<String, String> = HashMap(), ahora: Long = System.currentTimeMillis()): Espejo {
        val gs = ArrayList(games); val ps = ArrayList(plays)
        var cambio = false
        for (l in libros) {
            val n = normName(l.titulo)
            val id = enlaces[l.clave] ?: gs.firstOrNull { normName(it.name) == n }?.id ?: ps.firstOrNull { normName(it.name) == n }?.id ?: customItemId(l.titulo)
            if (enlaces[l.clave] != id) { enlaces[l.clave] = id; cambio = true }
            val gi = gs.indexOfFirst { it.id == id }; val pi = ps.indexOfFirst { it.id == id }
            val cover = portada(l)
            if (l.estado == "LEIDO") {
                val fin = diaDe(l.terminados.maxOrNull() ?: l.ultima)
                if (gi >= 0) {
                    val g = gs[gi]
                    val x = g.copy(name = l.titulo, creator = l.autor.ifBlank { g.creator }, fav = l.favorito || g.fav, pages = if (l.paginas > 0) l.paginas else g.pages, minutes = maxOf(g.minutes, (l.ms / 60_000L).toInt()),
                        end = if (fin > 0) fin else g.end, start = if (g.start > 0) g.start else diaDe(l.anadido), cover = if (cover.isNotBlank() && (g.cover.isBlank() || g.cover == puestas[l.clave])) cover else g.cover)
                    if (x != g) { gs[gi] = x.copy(updated = ahora); cambio = true }
                } else {
                    val p = if (pi >= 0) ps.removeAt(pi) else null
                    val b = p?.baremo()
                    gs += Game(id, l.titulo, cover.ifBlank { p?.cover.orEmpty() }, b?.general ?: 0, b?.gfx ?: 0, b?.story ?: 0, b?.music ?: 0, b?.gameplay ?: 0, "", hero = p?.hero.orEmpty(),
                        start = p?.start?.takeIf { it > 0 } ?: diaDe(l.anadido), end = fin, creator = l.autor.ifBlank { p?.creator.orEmpty() }, fav = l.favorito, pages = if (l.paginas > 0) l.paginas else (p?.pages ?: 0),
                        minutes = maxOf(p?.minutes ?: 0, (l.ms / 60_000L).toInt()), updated = ahora)
                    cambio = true
                }
            } else {
                if (gi >= 0) continue
                val status = if (l.estado == "LEYENDO") 1 else 0
                if (pi >= 0) {
                    val p = ps[pi]
                    val x = p.copy(name = l.titulo, status = status, creator = l.autor.ifBlank { p.creator }, pages = if (l.paginas > 0) l.paginas else p.pages, minutes = maxOf(p.minutes, (l.ms / 60_000L).toInt()),
                        start = if (p.start > 0) p.start else if (status == 1) diaDe(l.ultima.takeIf { it > 0 } ?: l.anadido) else 0L, end = 0L,
                        cover = if (cover.isNotBlank() && (p.cover.isBlank() || p.cover == puestas[l.clave])) cover else p.cover)
                    if (x != p) { ps[pi] = x.copy(updated = ahora); cambio = true }
                } else {
                    ps += Play(id, l.titulo, cover, "", status, emptyList(), start = if (status == 1) diaDe(l.ultima.takeIf { it > 0 } ?: l.anadido) else 0L, creator = l.autor, pages = l.paginas,
                        minutes = (l.ms / 60_000L).toInt(), updated = ahora)
                    cambio = true
                }
            }
            if (cover.isNotBlank()) puestas[l.clave] = cover
        }
        return Espejo(gs, ps, cambio)
    }

    // ================================================================ el tiempo de cada día (sin red: se puede probar)

    /** Lo jugado según Hiedra (`tiempo/<id>/<fuente>`): día → juego → ms. Cada fuente trae solo lo suyo: se suma. */
    fun diasJuegos(tiempo: JSONObject?, enlaces: Map<String, Long>): Map<Long, Map<JuegoPc, Long>> {
        val out = HashMap<Long, HashMap<JuegoPc, Long>>()
        tiempo ?: return out
        for (id in tiempo.keys()) {
            val fuentes = tiempo.optJSONObject(id) ?: continue
            for (f in fuentes.keys()) {
                val n = fuentes.optJSONObject(f) ?: continue
                val days = n.optJSONObject("days") ?: continue
                val j = JuegoPc(enlaces[id] ?: 0L, n.optString("t"))
                for (d in days.keys()) {
                    val ep = runCatching { LocalDate.parse(d).toEpochDay() }.getOrNull() ?: continue
                    val ms = days.optLong(d); if (ms <= 0) continue
                    val m = out.getOrPut(ep) { HashMap() }; m[j] = (m[j] ?: 0L) + ms
                }
            }
        }
        return out
    }

    /** Lo leído según Marfil (`tiempo/<kid>/<escritor>.dev[<dispositivo>].days`): día → libro (id de aquí) → ms. Por dispositivo y día, el máximo entre escritores; luego se suman los dispositivos. */
    fun diasLibros(tiempo: JSONObject?, enlacesPorKid: Map<String, Long>): Map<Long, Map<Long, Long>> {
        val out = HashMap<Long, HashMap<Long, Long>>()
        tiempo ?: return out
        for (kid in tiempo.keys()) {
            val id = enlacesPorKid[kid] ?: continue
            val escritores = tiempo.optJSONObject(kid) ?: continue
            val porDevDia = HashMap<String, HashMap<Long, Long>>()
            for (e in escritores.keys()) {
                val devs = escritores.optJSONObject(e)?.optJSONObject("dev") ?: continue
                for (dev in devs.keys()) {
                    val days = devs.optJSONObject(dev)?.optJSONObject("days") ?: continue
                    val m = porDevDia.getOrPut(dev) { HashMap() }
                    for (d in days.keys()) { val ep = runCatching { LocalDate.parse(d).toEpochDay() }.getOrNull() ?: continue; m[ep] = maxOf(m[ep] ?: 0L, days.optLong(d)) }
                }
            }
            for ((_, m) in porDevDia) for ((ep, ms) in m) if (ms > 0) { val x = out.getOrPut(ep) { HashMap() }; x[id] = (x[id] ?: 0L) + ms }
        }
        return out
    }

    /** Las palabras escritas cada día según Cobalto (`palabras/<dispositivo>`): se suman los dispositivos. */
    fun palabrasPorDia(palabras: JSONObject?): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        palabras ?: return out
        for (dev in palabras.keys()) {
            val d = palabras.optJSONObject(dev) ?: continue
            for (k in d.keys()) { val ep = runCatching { LocalDate.parse(k).toEpochDay() }.getOrNull() ?: continue; out[ep] = (out[ep] ?: 0) + d.optInt(k) }
        }
        return out
    }

    /** Un capítulo terminado de Cobalto, para leerlo aquí. */
    class Capitulo(val obra: String, val id: String, val titulo: String, val autor: String, val nombre: String, val numero: Int, val parte: String, val palabras: Int, val texto: String, val u: Long)

    /** Los capítulos de `lectura/<obra>/<capítulo>`, por obra y en orden. */
    fun capitulosDe(lectura: JSONObject?): List<Capitulo> {
        lectura ?: return emptyList()
        val out = ArrayList<Capitulo>()
        for (obra in lectura.keys()) {
            val caps = lectura.optJSONObject(obra) ?: continue
            for (c in caps.keys()) {
                val o = caps.optJSONObject(c) ?: continue
                out += Capitulo(obra, c, o.optString("t"), o.optString("a"), o.optString("c"), o.optInt("n"), o.optString("pa"), o.optInt("p"), o.optString("x"), o.optLong("u"))
            }
        }
        return out.sortedWith(compareBy<Capitulo> { normName(it.titulo) }.thenBy { it.numero })
    }

    // ================================================================ lo guardado en este móvil

    private fun guarda(c: Context, k: String, v: String) = prefs(c).edit().putString(k, v).apply()
    private fun lee(c: Context, k: String): JSONObject? = prefs(c).getString(k, null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    /** id de Hiedra → id de la ficha de aquí (y clave de Marfil → id). */
    fun enlaces(c: Context, app: String): MutableMap<String, Long> {
        val o = lee(c, "enl_$app") ?: return HashMap()
        return o.keys().asSequence().associateWithTo(HashMap()) { o.optLong(it) }
    }
    private fun guardaEnlaces(c: Context, app: String, m: Map<String, Long>) = guarda(c, "enl_$app", JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, v) } }.toString())
    /** Lo que llevas de cada libro (0–1), por ficha de aquí (solo los que tienen ficha y algo leído). Sin móvil: se prueba. */
    fun avancePorFicha(libros: List<LibroM>, enl: Map<String, Long>): JSONObject =
        JSONObject().also { o -> libros.forEach { l -> enl[l.clave]?.let { id -> if (l.fraccion > 0f) o.put(id.toString(), l.fraccion.toDouble()) } } }
    /** Lo que llevas leído de un libro de Marfil (0–1), por ficha de aquí; null si no se sabe. */
    fun avanceMarfil(c: Context, id: Long): Float? = lee(c, "avance_marfil")?.optDouble(id.toString(), -1.0)?.takeIf { it >= 0.0 }?.toFloat()
    /** Al revés: la ficha de aquí → el id del juego en Hiedra (o la clave del libro en Marfil), si la ficha vino de allí. */
    fun enHermana(c: Context, app: String, id: Long): String? = enlaces(c, app).entries.firstOrNull { it.value == id }?.key
    /** «Jugar»: Hiedra, en la ficha de ese juego si es suyo. */
    fun jugarEnHiedra(c: Context, id: Long): Boolean {
        val i = c.packageManager.getLaunchIntentForPackage(PAQ_HIEDRA) ?: return false
        enHermana(c, "hiedra", id)?.let { i.putExtra("ficha", it).putExtra("jugar", true) }
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }
    /** «Leer»: Marfil, en la ficha de ese libro si es suyo (Marfil 0.12.2 entiende «clave»). */
    fun leerEnMarfil(c: Context, id: Long): Boolean {
        val i = c.packageManager.getLaunchIntentForPackage(PAQ_MARFIL) ?: return false
        enHermana(c, "marfil", id)?.let { i.putExtra("clave", it) }
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }
    /** Las portadas que se pusieron desde cada hermana (id o clave → portada), para no pisar la que cambies tú. */
    private fun puestas(c: Context, app: String): MutableMap<String, String> { val o = lee(c, "por_$app") ?: return HashMap(); return o.keys().asSequence().associateWithTo(HashMap()) { o.optString(it) } }
    private fun guardaPuestas(c: Context, app: String, m: Map<String, String>) = guarda(c, "por_$app", JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, v) } }.toString())

    /** Lo jugado por día (Hiedra), lo leído por día (Marfil) y las palabras (Cobalto), de la última vez que se miró. */
    fun jugadoPorDia(c: Context): Map<Long, Map<JuegoPc, Long>> = diasJuegos(lee(c, "tiempo_hiedra"), enlaces(c, "hiedra"))
    fun leidoPorDia(c: Context): Map<Long, Map<Long, Long>> {
        val enl = enlaces(c, "marfil")
        return diasLibros(lee(c, "tiempo_marfil"), enl.entries.associate { (k, v) -> kidDe(k) to v })
    }
    fun palabras(c: Context): Map<Long, Int> = palabrasPorDia(lee(c, "palabras_cobalto"))
    /** Los subrayados de Marfil, por ficha de aquí (solo los libros que tienen ficha). */
    fun subrayados(c: Context): Map<Long, List<BookNote>> {
        val enl = enlaces(c, "marfil")
        return subrayadosDe(lee(c, "subrayados_marfil"), enl.entries.associate { (k, v) -> kidDe(k) to v })
    }

    /**
     * `subrayados/<kid>/<escritor>` = {t, a, n: [{x, o, c, l, k}]}: cada escritor trae los de todos los dispositivos, así que se unen por
     * sitio (`k`) y hora (`c`). Salen como notas de aquí (el sitio es el «value», para abrir el libro ahí en Marfil).
     */
    fun subrayadosDe(sub: JSONObject?, enlacesPorKid: Map<String, Long>): Map<Long, List<BookNote>> {
        sub ?: return emptyMap()
        val out = HashMap<Long, LinkedHashMap<String, BookNote>>()
        for (kid in sub.keys()) {
            val id = enlacesPorKid[kid] ?: continue
            val escritores = sub.optJSONObject(kid) ?: continue
            for (w in escritores.keys()) {
                val n = escritores.optJSONObject(w)?.optJSONArray("n") ?: continue
                for (i in 0 until n.length()) {
                    val o = n.optJSONObject(i) ?: continue
                    val x = o.optString("x").trim(); if (x.isBlank()) continue
                    val k = o.optString("k"); val creado = o.optLong("c")
                    val m = out.getOrPut(id) { LinkedHashMap() }
                    val nuevo = BookNote(k, x, o.optString("o"), o.optString("l"), creado, 0)
                    // Entre escritores, el mismo subrayado: se queda el que lleva nota (un escritor puede ir por detrás del otro)
                    val previo = m["$k@$creado"]
                    if (previo == null || (previo.note.isBlank() && nuevo.note.isNotBlank())) m["$k@$creado"] = nuevo
                }
            }
        }
        return out.mapValues { (_, m) -> m.values.sortedBy { it.created } }
    }
    fun capitulos(c: Context): List<Capitulo> = capitulosDe(lee(c, "lectura_cobalto"))
    fun ultimaVez(c: Context) = prefs(c).getLong("ultima", 0L)

    /** Los objetivos de lectura que fija Marfil (`marfil/objetivos`): minutos al día y libros del año. Null si Marfil aún no ha subido ninguno. */
    class ObjetivosMarfil(val dia: Int, val ano: Int)
    fun objetivosMarfil(c: Context): ObjetivosMarfil? = objetivosDe(lee(c, "objetivos_marfil"))
    fun objetivosDe(o: JSONObject?): ObjetivosMarfil? = o?.takeIf { it.has("mod") }?.let { ObjetivosMarfil(it.optInt("dia").coerceIn(0, 600), it.optInt("ano").coerceIn(0, 999)) }

    // ================================================================ la red

    private fun blobs(store: Store, app: String): Map<String, JSONObject> {
        val uid = store.accUid
        val st = Account.db(store, "GET", "/users/$uid/$app/stamps", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptyMap()
        val ahora = System.currentTimeMillis()
        val out = LinkedHashMap<String, JSONObject>()
        for (dev in st.keys()) {
            val ts = st.optLong(dev); if (ts > 0 && ahora - ts > STALE) continue
            val raw = Account.db(store, "GET", "/users/$uid/$app/dev/$dev/data", null) ?: continue
            val datos = (JSONTokener(raw).nextValue() as? String) ?: continue
            runCatching { JSONObject(datos) }.getOrNull()?.let { out[dev] = it }
        }
        return out
    }

    /** La portada de un juego de Hiedra: la que pusiste tú (kDrive) si se puede bajar; si no, la de internet. Se guarda aquí (file:…). */
    private fun portadaJuego(c: Context, store: Store, j: JuegoH, nube: JSONObject?): String {
        val f = nube?.optJSONObject(j.id)?.optJSONObject("f")?.optJSONObject("img")
        val id = f?.optLong("id") ?: 0L; val h = f?.optString("h").orEmpty()
        if (id > 0L && KDrive.on(c)) {
            val dest = File(File(c.filesDir, "hermanas").apply { mkdirs() }, "h_${j.id}_${h.take(12)}.jpg")
            if (!dest.exists()) runCatching { KDrive.download(c, id, dest) }.onFailure { dest.delete() }
            if (dest.exists() && dest.length() > 200) return "file://" + dest.absolutePath
        }
        return j.portadaUrl
    }

    private fun portadaLibro(c: Context, l: LibroM, portadas: JSONObject?): String {
        val e = portadas?.optJSONObject(l.kid)
        if (e != null && e.optInt("x") != 1) {
            val id = e.optLong("f"); val u = e.optLong("u")
            if (id > 0L && KDrive.on(c)) {
                val dest = File(File(c.filesDir, "hermanas").apply { mkdirs() }, "m_${l.kid}_$u.jpg")
                if (!dest.exists()) runCatching { KDrive.download(c, id, dest) }.onFailure { dest.delete() }
                if (dest.exists() && dest.length() > 200) return "file://" + dest.absolutePath
            }
        }
        // Sin kDrive (o sin portada tuya en él): la dirección de la que elegiste de internet, que viaja con el libro (Marfil 0.12.0)
        return l.portadaUrl
    }

    /**
     * Mira lo que han subido las hermanas y lo refleja (como mucho cada 5 minutos, o ya con [ya]). Bloqueante (red). Devuelve si ha cambiado algo.
     * Los errores de red se tragan: se vuelve a intentar la próxima vez.
     */
    fun refrescar(c: Context, store: Store, ya: Boolean = false, solo: Set<String>? = null): Boolean {
        if (!store.paired) return false
        val ahora = System.currentTimeMillis()
        if (!ya && ahora - (mirado["x"] ?: 0L) < 5 * 60_000L) return false
        if (solo == null) mirado["x"] = ahora
        fun toca(app: String) = solo == null || app in solo
        var cambio = false
        val uid = store.accUid
        fun nodo(path: String): JSONObject? = runCatching { Account.db(store, "GET", "/users/$uid/$path", null)?.let { JSONObject(it) } }.getOrNull()
        // Hiedra
        if (toca("hiedra")) runCatching {
            val b = blobs(store, "hiedra")
            if (b.isNotEmpty()) {
                val juegos = juegosDe(b)
                val nube = nodo("hiedra/nube/portadas")
                val enl = enlaces(c, "hiedra"); val pu = puestas(c, "hiedra")
                val e = reflejarJuegos(juegos, store.load(GAMES.id), store.loadPlays(GAMES.id), enl, { j -> portadaJuego(c, store, j, nube) }, pu, ahora)
                if (e.cambio) { store.save(e.games, GAMES.id); store.savePlays(e.plays, GAMES.id); guardaEnlaces(c, "hiedra", enl); guardaPuestas(c, "hiedra", pu); cambio = true }
                nodo("hiedra/tiempo")?.let { guarda(c, "tiempo_hiedra", it.toString()); cambio = true }
            }
        }
        // Marfil: solo lo leído (el sello «lstamps» de un libro): los nodos ligeros, sin volver a bajar la biblioteca
        if (!toca("marfil") && toca("marfil-lec")) runCatching {
            nodo("marfil/tiempo")?.let { guarda(c, "tiempo_marfil", it.toString()); cambio = true }
            val sub = nodo("marfil/subrayados")?.toString() ?: "{}"
            if (sub != prefs(c).getString("subrayados_marfil", "")) { guarda(c, "subrayados_marfil", sub); cambio = true }
        }
        // Marfil
        if (toca("marfil")) runCatching {
            val b = blobs(store, "marfil")
            if (b.isNotEmpty()) {
                val libros = librosDe(b)
                val portadas = nodo("marfil/portadas")
                val enl = enlaces(c, "marfil"); val pu = puestas(c, "marfil")
                val e = reflejarLibros(libros, store.load(BOOKS.id), store.loadPlays(BOOKS.id), enl, { l -> portadaLibro(c, l, portadas) }, pu, ahora)
                if (e.cambio) { store.save(e.games, BOOKS.id); store.savePlays(e.plays, BOOKS.id); guardaEnlaces(c, "marfil", enl); guardaPuestas(c, "marfil", pu); cambio = true }
                // Lo que llevas de cada libro (0–1), por ficha: el widget «Leyendo» y el aviso de lectura lo enseñan
                val av = avancePorFicha(libros, enl).toString()
                if (av != prefs(c).getString("avance_marfil", "")) { guarda(c, "avance_marfil", av); cambio = true }
                nodo("marfil/tiempo")?.let { guarda(c, "tiempo_marfil", it.toString()); cambio = true }
                // Los subrayados (ligeros): la cita de la portada, el Diario, el buscador y el widget
                val sub = nodo("marfil/subrayados")?.toString() ?: "{}"
                if (sub != prefs(c).getString("subrayados_marfil", "")) { guarda(c, "subrayados_marfil", sub); cambio = true }
            }
        }
        // Los objetivos de lectura: los fija Marfil (aquí solo se enseñan)
        if (toca("marfil")) runCatching {
            val o = nodo("marfil/objetivos")?.toString()
            if (o != null && o != prefs(c).getString("objetivos_marfil", "")) { guarda(c, "objetivos_marfil", o); cambio = true; LecturaAviso.programa(c) }
        }
        // Cobalto
        if (toca("cobalto")) {
            runCatching { nodo("cobalto/palabras")?.let { guarda(c, "palabras_cobalto", it.toString()); cambio = true } }
            runCatching { nodo("cobalto/lectura")?.let { guarda(c, "lectura_cobalto", it.toString()); cambio = true } }
        }
        prefs(c).edit().putLong("ultima", ahora).apply()
        // Lo que llega de las hermanas (horas, subrayados, portadas) también se ve en los widgets al momento
        if (cambio) { enPantalla { version++ }; Widgets.pedir(c); AtajoLectura.pon(c) }
        return cambio
    }
}
