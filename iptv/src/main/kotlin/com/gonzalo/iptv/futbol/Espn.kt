package com.gonzalo.iptv.futbol

import com.gonzalo.iptv.IptvError
import com.gonzalo.iptv.JsonFlujo
import com.gonzalo.iptv.Red
import com.gonzalo.iptv.lista
import com.gonzalo.iptv.mapa
import com.gonzalo.iptv.txt
import java.io.BufferedInputStream
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.GZIPInputStream

/**
 * Los datos de fútbol de la API JSON pública de ESPN (no oficial, sin clave): `site.api.espn.com`. Se piden con [red] (la de Nut, que
 * pide el cuerpo comprimido) y cada respuesta se lee aquí y se convierte en lo de [Futbol.kt]: lo de ESPN no sale de este archivo. Sus
 * textos libres (el «commentary» en inglés) no se usan nunca: las frases se hacen con los campos ([Textos.frase]).
 *
 * Lo que se pide (`{liga}`: `esp.1`, `uefa.champions`…; `{equipo}`: su id, el Barça es 83):
 *  - `…/site/v2/sports/soccer/{liga}/teams/{equipo}/schedule[?fixture=true]` — los partidos jugados (o, con `fixture`, los que vienen)
 *  - `…/site/v2/sports/soccer/{liga}/scoreboard?dates=AAAAMMDD` — los de un día, con el marcador
 *  - `…/v2/sports/soccer/{liga}/standings` — la clasificación
 *  - `…/site/v2/sports/soccer/{liga}/statistics` — goleadores y asistentes
 *  - `…/site/v2/sports/soccer/{liga}/summary?event={id}` — la ficha del partido
 *  - `…/site/v2/sports/soccer/{liga}/teams` — los equipos de la competición (para elegir los tuyos)
 */
class Espn(private val pedir: (url: String, edad: Long) -> String) : FuenteFutbol {
    /** Sin caché: cada vez, a la red. */
    constructor(red: Red) : this({ u, _ -> pide(red, u) })
    override val nombre = "ESPN"

    companion object {
        const val SITIO = "https://site.api.espn.com/apis/site/v2/sports/soccer"
        const val TABLAS = "https://site.api.espn.com/apis/v2/sports/soccer"

        /** En español de España: los nombres de las selecciones («España», «Chequia») y de algunos clubes («Atlético de Madrid»). */
        const val IDIOMA = "lang=es&region=es"
        fun urlCalendario(liga: String, equipo: String, proximos: Boolean) = "$SITIO/$liga/teams/$equipo/schedule?" + (if (proximos) "fixture=true&" else "") + IDIOMA
        fun urlMarcadores(liga: String, dia: LocalDate) = "$SITIO/$liga/scoreboard?dates=%04d%02d%02d&$IDIOMA".format(dia.year, dia.monthValue, dia.dayOfMonth)
        fun urlClasificacion(liga: String) = "$TABLAS/$liga/standings?$IDIOMA"
        fun urlLideres(liga: String) = "$SITIO/$liga/statistics?$IDIOMA"
        fun urlFicha(liga: String, partido: String) = "$SITIO/$liga/summary?event=$partido&$IDIOMA"
        fun urlEquipos(liga: String) = "$SITIO/$liga/teams?$IDIOMA"

        // ------------------------------------------------------------ leer las respuestas (sin red: se prueban con respuestas de verdad)

        private fun Any?.m() = mapa()
        private fun Any?.l() = lista()
        private fun entero(v: Any?): Int? = when (v) { is Number -> v.toInt(); is String -> v.trim().toDoubleOrNull()?.toInt(); else -> null }

        /** Un equipo de ESPN (`team`): su escudo, el «default» (el de fondo claro). */
        fun equipo(t: Map<String, Any?>): Equipo {
            val logos = t["logos"].l().map { it.m() }
            val escudo = logos.firstOrNull { x -> x["rel"].l().any { it == "default" } }?.get("href").txt().ifBlank { logos.firstOrNull()?.get("href").txt() }.ifBlank { t["logo"].txt() }
            val nombre = t["displayName"].txt().ifBlank { t["name"].txt() }.ifBlank { t["location"].txt() }
            return Equipo(t["id"].txt(), nombre, t["shortDisplayName"].txt().ifBlank { nombre }, t["abbreviation"].txt(), escudo)
        }

        /** Un partido (un `event` de un calendario o de un marcador, o el `header` de una ficha). */
        fun partido(e: Map<String, Any?>, ligaPorDefecto: String): Partido? {
            val c = e["competitions"].l().firstOrNull().m()
            val cs = c["competitors"].l().map { it.m() }
            val loc = cs.firstOrNull { it["homeAway"] == "home" } ?: cs.getOrNull(0) ?: return null
            val vis = cs.firstOrNull { it["homeAway"] == "away" } ?: cs.getOrNull(1) ?: return null
            val st = c["status"].m().ifEmpty { e["status"].m() }
            val tipo = st["type"].m()
            val estado = Textos.estado(tipo["name"].txt(), tipo["state"].txt())
            fun goles(x: Map<String, Any?>): Int? {
                val s = x["score"]
                val v = if (s is Map<*, *>) entero(s["displayValue"]) ?: entero(s["value"]) else entero(s)
                return if (estado == Estado.POR_JUGAR || estado == Estado.APLAZADO || estado == Estado.CANCELADO) null else v
            }
            fun tanda(x: Map<String, Any?>): Int? = entero(x["shootoutScore"]).takeIf { it != null && it > 0 } ?: x["score"].m()["shootoutScore"]?.let { entero(it) }
            val pl = tanda(loc); val pv = tanda(vis)
            val liga = e["league"].m()["slug"].txt().ifBlank { ligaPorDefecto }
            return Partido(
                id = e["id"].txt().ifBlank { c["id"].txt() },
                liga = liga,
                inicio = Hora.lee(c["date"].txt().ifBlank { e["date"].txt() }),
                local = equipo(loc["team"].m()), visitante = equipo(vis["team"].m()),
                estado = estado, golesLocal = goles(loc), golesVisitante = goles(vis),
                reloj = if (estado.enDirecto) st["displayClock"].txt().takeIf { it != "0'" }.orEmpty() else "",
                penaltis = if (pl != null && pv != null) "$pl-$pv" else "",
                estadio = c["venue"].m()["fullName"].txt(),
            )
        }

        /** Los partidos de un calendario o de un marcador (`events`). */
        fun partidos(json: Any?, liga: String): List<Partido> = json.m()["events"].l().mapNotNull { runCatching { partido(it.m(), liga) }.getOrNull() }

        fun clasificacion(json: Any?, liga: String): Clasificacion {
            val raiz = json.m()
            val hijos = raiz["children"].l().map { it.m() }.ifEmpty { listOf(raiz) }
            val tablas = hijos.map { h ->
                val filas = h["standings"].m()["entries"].l().map { it.m() }.map { en ->
                    val st = en["stats"].l().map { it.m() }.associate { it["name"].txt() to it }
                    fun n(k: String) = entero(st[k]?.get("value")) ?: entero(st[k]?.get("displayValue")) ?: 0
                    Fila(n("rank"), equipo(en["team"].m()), n("gamesPlayed"), n("wins"), n("ties"), n("losses"), n("pointsFor"), n("pointsAgainst"), n("pointDifferential"), n("points"),
                        Textos.nota(en["note"].m()["description"].txt()))
                }.let { fs -> fs.mapIndexed { i, f -> if (f.puesto <= 0) f.copy(puesto = i + 1) else f }.sortedBy { it.puesto } }
                Textos.tabla(h["name"].txt()) to filas
            }.filter { it.second.isNotEmpty() }
            return Clasificacion(liga, tablas)
        }

        fun lideres(json: Any?, liga: String): Lideres {
            val st = json.m()["stats"].l().map { it.m() }
            fun de(nombre: String) = st.firstOrNull { it["name"] == nombre }?.get("leaders").l().map { it.m() }.orEmpty().map { x ->
                val a = x["athlete"].m()
                val partidos = Regex("(?:Matches|Partidos|Juegos): (\\d+)").find(x["displayValue"].txt())?.groupValues?.get(1)?.toIntOrNull() ?: 0
                Lider(a["displayName"].txt(), equipo(a["team"].m()), entero(x["value"]) ?: 0, partidos)
            }
            return Lideres(liga, de("goalsLeaders"), de("assistsLeaders"))
        }

        fun equipos(json: Any?): List<Equipo> = json.m()["sports"].l().flatMap { s -> s.m()["leagues"].l().flatMap { lg -> lg.m()["teams"].l().map { equipo(it.m()["team"].m()) } } }
            .filter { it.id.isNotBlank() }.sortedBy { java.text.Normalizer.normalize(it.nombre, java.text.Normalizer.Form.NFD).lowercase() }

        fun ficha(json: Any?, liga: String): FichaPartido {
            val r = json.m()
            val h = r["header"].m()
            val p = partido(h, h["league"].m()["slug"].txt().ifBlank { liga }) ?: throw IptvError("La ficha del partido no se entiende.")
            val eventos = r["keyEvents"].l().map { it.m() }.mapNotNull { k ->
                val t = k["type"].m()
                val tipo = Textos.tipo(t["id"].txt(), t["text"].txt().ifBlank { t["type"].txt() })
                if (tipo == TipoEvento.OTRO) return@mapNotNull null
                val gente = k["participants"].l().map { it.m()["athlete"].m()["displayName"].txt() }.filter { it.isNotBlank() }
                Evento(k["clock"].m()["displayValue"].txt(), tipo, k["team"].m()["id"].txt(), gente.getOrNull(0).orEmpty(), gente.getOrNull(1).orEmpty())
            }.let { l ->
                // (sin el «empieza la segunda parte» si ya va el descanso, y sin repetir hitos)
                l.filterIndexed { i, e -> !(e.tipo.esHito && l.subList(0, i).any { it.tipo == e.tipo }) && e.tipo != TipoEvento.SEGUNDA_PARTE }
            }
            val alineaciones = r["rosters"].l().map { it.m() }.mapNotNull { ro ->
                val eq = equipo(ro["team"].m())
                val js = ro["roster"].l().map { it.m() }.map { x ->
                    val a = x["athlete"].m()
                    Jugador(a["displayName"].txt(), x["jersey"].txt(), x["position"].m()["abbreviation"].txt(), x["starter"] == true, x["subbedIn"] == true, x["subbedOut"] == true)
                }
                if (js.isEmpty()) null else Alineacion(eq, ro["formation"].txt(), js.filter { it.titular }, js.filter { !it.titular })
            }.let { a -> a.sortedBy { if (it.equipo.id == p.local.id) 0 else 1 } }
            val equipos = r["boxscore"].m()["teams"].l().map { it.m() }
            val loc = equipos.firstOrNull { it["team"].m()["id"].txt() == p.local.id } ?: equipos.getOrNull(0)
            val vis = equipos.firstOrNull { it["team"].m()["id"].txt() == p.visitante.id } ?: equipos.getOrNull(1)
            fun stats(x: Map<String, Any?>?) = x?.get("statistics").l().map { it.m() }.associate { it["name"].txt() to it["displayValue"].txt() }
            val sl = stats(loc); val sv = stats(vis)
            val cifras = if (sl.isEmpty() && sv.isEmpty()) emptyList() else Textos.CIFRAS.mapNotNull { (k, nombre) ->
                val a = sl[k]; val b = sv[k]
                if (a == null && b == null) null else Cifra(nombre, Textos.cifra(k, a.orEmpty()), Textos.cifra(k, b.orEmpty()))
            }
            return FichaPartido(p, eventos, alineaciones, cifras)
        }

        /** Pide [url] a [red] y devuelve el texto (JSON). Lanza [IptvError] sin la dirección si no se puede. */
        fun pide(red: Red, url: String): String {
            val r = red.abre(url, mapOf("Accept" to "application/json"))
            return r.use { if (!it.ok) throw IptvError("ESPN no contesta ahora (${it.codigo}).", it.codigo) else texto(it.cuerpo) }
        }

        /** El cuerpo de una respuesta, como texto (descomprimido si viene en gzip aunque no lo diga la cabecera). */
        fun texto(i: InputStream): String {
            val b = BufferedInputStream(i, 64 * 1024)
            b.mark(2)
            val m1 = b.read(); val m2 = b.read()
            b.reset()
            val cuerpo: InputStream = if (m1 == 0x1f && m2 == 0x8b) GZIPInputStream(b, 64 * 1024) else b
            return cuerpo.use { String(it.readBytes(), Charsets.UTF_8) }
        }
    }

    private fun json(url: String, edad: Long): Any? = JsonFlujo.lee(pedir(url, edad))

    override fun calendario(liga: String, equipo: String, proximos: Boolean, edad: Long) = partidos(json(urlCalendario(liga, equipo, proximos), edad), liga)
    override fun marcadores(liga: String, dia: LocalDate, edad: Long) = partidos(json(urlMarcadores(liga, dia), edad), liga)
    override fun clasificacion(liga: String, edad: Long) = clasificacion(json(urlClasificacion(liga), edad), liga)
    override fun lideres(liga: String, edad: Long) = lideres(json(urlLideres(liga), edad), liga)
    override fun ficha(liga: String, partido: String, edad: Long) = ficha(json(urlFicha(liga, partido), edad), liga)
    override fun equipos(liga: String, edad: Long) = equipos(json(urlEquipos(liga), edad))
}
