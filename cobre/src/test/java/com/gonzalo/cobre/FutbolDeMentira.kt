package com.gonzalo.cobre

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.gonzalo.iptv.JsonFlujo
import com.gonzalo.iptv.futbol.CanalesFutbol
import com.gonzalo.iptv.futbol.Entrada
import com.gonzalo.iptv.futbol.Equipo
import com.gonzalo.iptv.futbol.Espn
import com.gonzalo.iptv.futbol.Estado
import com.gonzalo.iptv.futbol.Partido
import com.gonzalo.iptv.futbol.RefCanal
import java.io.File

/**
 * El fútbol de mentira para las capturas y los widgets (sin red): la clasificación, los goleadores, los jugados y la ficha salen de
 * respuestas de verdad de ESPN (`resources/espn`); el partido en juego y los que vienen se ponen a partir de ahora. Los escudos son
 * círculos de colores con las siglas, hechos aquí (en el disco, donde los buscan Coil y los widgets).
 */
object FutbolDeMentira {
    private fun json(n: String) = JsonFlujo.lee(String(javaClass.classLoader!!.getResourceAsStream("espn/$n")!!.readBytes(), Charsets.UTF_8))

    private val colores = mapOf("83" to (0xFF004D98.toInt() to 0xFFA50044.toInt()), "86" to (0xFFF2F2F2.toInt() to 0xFFC9A227.toInt()), "1068" to (0xFFCB3524.toInt() to 0xFF272E61.toInt()),
        "94" to (0xFFF4F4F4.toInt() to 0xFF111111.toInt()), "96" to (0xFF1D4C9C.toInt() to 0xFFFFFFFF.toInt()))

    /** Un escudo de mentira para [e]: en un archivo (para Coil) y donde lo busca el widget. */
    private fun escudo(ctx: Context, e: Equipo): Equipo {
        val (a, b) = colores[e.id] ?: (0xFF6F8A6B.toInt() to 0xFF1F2A1E.toInt())
        val bm = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        Canvas(bm).apply {
            drawCircle(64f, 64f, 62f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = b })
            drawCircle(64f, 64f, 52f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a })
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (a == 0xFFF2F2F2.toInt() || a == 0xFFF4F4F4.toInt()) 0xFF222222.toInt() else 0xFFFFFFFF.toInt(); textSize = 34f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
            drawText(e.siglas.ifBlank { e.nombre.take(3) }.uppercase(), 64f, 76f, t)
        }
        val f = File(ctx.cacheDir, "escudo_${e.id}.png")
        f.outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Widgets.archivoEscudo(ctx, f.absolutePath).apply { parentFile?.mkdirs(); f.copyTo(this, overwrite = true) }
        return e.copy(escudo = f.absolutePath)
    }

    /** [vivo]: con un partido en juego (Atlético–Barça, 1–2, 67'); si no, ese partido es mañana a las 21:00. */
    fun datos(ctx: Context, vivo: Boolean = true): Futbol.Fijo {
        val ahora = System.currentTimeMillis()
        val h = 3_600_000L
        val bar = escudo(ctx, Equipo("83", "Barcelona", "Barcelona", "BAR"))
        val rma = escudo(ctx, Equipo("86", "Real Madrid", "Real Madrid", "RMA"))
        val atm = escudo(ctx, Equipo("1068", "Atlético Madrid", "Atlético", "ATM"))
        val val_ = escudo(ctx, Equipo("94", "Valencia", "Valencia", "VAL"))
        val ala = escudo(ctx, Equipo("96", "Alavés", "Alavés", "ALA"))
        val esp = escudo(ctx, Equipo("164", "España", "España", "ESP"))
        val ing = escudo(ctx, Equipo("448", "Inglaterra", "Inglaterra", "ENG"))
        val mañana21 = java.time.LocalDate.now(com.gonzalo.iptv.futbol.Hora.ESPANA).plusDays(1).atTime(21, 0).atZone(com.gonzalo.iptv.futbol.Hora.ESPANA).toInstant().toEpochMilli()
        val clasico = if (vivo) Partido("901", "esp.1", ahora - 67 * 60_000L, atm, bar, Estado.EN_JUEGO, 1, 2, "67'", estadio = "Riyadh Air Metropolitano")
            else Partido("901", "esp.1", mañana21, atm, bar, estadio = "Riyadh Air Metropolitano")
        val jugados = Espn.partidos(json("calendario_jugados.json"), "esp.1").map { p -> p.copy(local = if (p.local.id == "83") bar else p.local, visitante = if (p.visitante.id == "83") bar else p.visitante) }
        val partidos = listOf(
            clasico,
            // (tu rival juega hoy: sale también en la portada)
            Partido("902", "esp.1", (ahora / (15 * 60_000L) + 6) * 15 * 60_000L, rma, val_, estadio = "Santiago Bernabéu"),
            Partido("905", "uefa.nations", mañana21 + 7 * 24 * h - 15 * 60_000L, esp, ing, estadio = "La Cartuja"),
            Partido("903", "uefa.champions", mañana21 + 17 * 24 * h, Equipo("21524", "Sabah FK", "Sabah", "SAB"), bar),
            Partido("904", "esp.1", mañana21 + 14 * 24 * h - 2 * h, bar, Equipo("102", "Villarreal", "Villarreal", "VIL")),
        ) + jugados
        val ficha = Espn.ficha(json("ficha_final.json"), "esp.1")
        // Los partidos del día (de verdad, de ESPN, movidos a hoy): LaLiga (con uno en juego), la Premier; y uno de Europa League; la Champions, mañana
        val hoy = com.gonzalo.iptv.futbol.Hora.diaDe(ahora)
        fun aHoy(l: List<Partido>, mas: Long = 0) = l.map { p -> val d = hoy.toEpochDay() - com.gonzalo.iptv.futbol.Hora.diaDe(p.inicio).toEpochDay() + mas; p.copy(inicio = p.inicio + d * 24 * h) }
        val delDia = aHoy(Espn.partidos(json("dia_laliga_vivo.json"), "esp.1").filter { it.visitante.id != "86" }) + aHoy(Espn.partidos(json("dia_premier.json"), "eng.1")) +
            aHoy(Espn.partidos(json("dia_champions.json"), "uefa.champions"), 1) + aHoy(Espn.partidos(json("dia_naciones.json"), "uefa.nations"), 2) +
            Partido("906", "uefa.europa", hoy.atTime(18, 45).atZone(com.gonzalo.iptv.futbol.Hora.ESPANA).toInstant().toEpochMilli(), escudo(ctx, Equipo("244", "Real Betis", "Betis", "BET")), Equipo("167", "Olympique Lyon", "Lyon", "LYO"), estadio = "La Cartuja")
        val fichaVivo = ficha.copy(partido = clasico, eventos = ficha.eventos.filter { e -> (e.minuto.takeWhile { it.isDigit() }.toIntOrNull() ?: 0) <= 67 && e.tipo != com.gonzalo.iptv.futbol.TipoEvento.FINAL }.map { e ->
            when (e.equipo) { "83" -> e; "96" -> e.copy(equipo = "1068"); else -> e }
        }, alineaciones = ficha.alineaciones.map { a -> if (a.equipo.id == "83") a.copy(equipo = bar) else a.copy(equipo = atm) }.sortedBy { if (it.equipo.id == atm.id) 0 else 1 },
            cifras = ficha.cifras.map { c -> c.copy(local = c.visitante, visitante = c.local) })
        val l = "lcasa000001"
        fun r(k: String, n: String) = RefCanal(l, k, n)
        val grupos = CanalesFutbol.GRUPOS.map { g ->
            when (g.id) {
                "movistar" -> g.copy(canales = listOf(Entrada(r("x201", "M+ LaLiga UHD"), r("x202", "M+ LaLiga HD"), r("x203", "M+ LaLiga SD")), Entrada(hd = r("x204", "M+ LaLiga 2 HD")), Entrada(hd = r("x205", "M+ LaLiga 3 HD"))))
                "dazn" -> g.copy(canales = listOf(Entrada(r("x113", "ES: DAZN LA LIGA UHD"), r("x114", "ES: DAZN LA LIGA HD"), null)))
                "champions" -> g.copy(canales = listOf(Entrada(hd = r("x301", "M+ Liga de Campeones HD"))))
                "seleccion" -> g.copy(canales = listOf(Entrada(hd = r("x101", "La 1 HD")), Entrada(hd = r("x112", "Teledeporte HD"))))
                else -> g
            }
        }
        return Futbol.Fijo(
            equipos = listOf(bar to "esp.1", esp to "uefa.nations"), rivales = listOf(rma to "esp.1"), grupos = grupos, partidos = partidos,
            fichas = mapOf("901" to fichaVivo.copy(caraACara = Espn.ficha(json("ficha_cara_a_cara.json"), "esp.1").caraACara.map { q -> q.copy(local = if (q.local.id == "83") bar else atm, visitante = if (q.visitante.id == "83") bar else atm) })) +
                jugados.associate { it.id to ficha.copy(partido = it) },
            partidosDia = delDia,
            plantillas = mapOf("83" to Espn.plantilla(json("plantilla.json"))),
            clasificaciones = mapOf("esp.1" to Espn.clasificacion(json("clasificacion.json"), "esp.1").let { c -> c.copy(tablas = c.tablas.map { (n, fs) -> n to fs.map { f -> when (f.equipo.id) { "83" -> f.copy(equipo = bar); "86" -> f.copy(equipo = rma); "1068" -> f.copy(equipo = atm); else -> f } } }) }),
            lideres = mapOf("esp.1" to Espn.lideres(json("goleadores.json"), "esp.1")),
            equiposDe = mapOf("esp.1" to Espn.equipos(json("equipos.json")), "uefa.nations" to Espn.equipos(json("selecciones_equipos.json"))),
        )
    }
}
