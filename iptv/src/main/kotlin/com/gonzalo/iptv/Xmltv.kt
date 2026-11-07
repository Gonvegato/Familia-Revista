package com.gonzalo.iptv

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream

/**
 * Las guías XMLTV (el formato de `xmltv.dtd`, el de casi todas las guías de IPTV y el de `xmltv.php` de Xtream), leídas a trozos: una
 * guía de 100 MB o más (y comprimida con gzip, `.xml.gz`) no se tiene nunca entera en memoria. Se lee con un lector de XML propio, pequeño
 * y en Kotlin puro (Android no trae el de Java —StAX— y el ordenador no trae el de Android): elementos, atributos, texto, entidades,
 * CDATA, comentarios y la declaración del principio (con su codificación).
 *
 *  - `<channel id="…"><display-name>…</display-name><icon src="…"/></channel>` → [alCanal].
 *  - `<programme start="20260115203000 +0100" stop="…" channel="…"><title>…</title><sub-title>…</sub-title><desc>…</desc>
 *    <category>…</category><icon src="…"/><episode-num system="onscreen">…</episode-num></programme>` → [alPrograma], si [quiero] el
 *    canal y cae en la ventana [desde]–[hasta] (para no guardar días de canales que no tienes).
 *  - Horas: `AAAAMMDDhhmmss` (o un trozo del principio) y su zona (`+0100`, `-0600`, `UTC`, `GMT`, `BST`…); sin zona, UTC (lo dice el DTD).
 */
object Xmltv {
    class Opciones(
        val desde: Long = Long.MIN_VALUE,
        val hasta: Long = Long.MAX_VALUE,
        /** Se pregunta al llegar el primer programa de cada canal (ya se han visto los `<channel>`, que van antes). */
        val quiero: (String) -> Boolean = { true },
        /** Lo más largo que se guarda de una descripción (caracteres). */
        val maxDescripcion: Int = 1200,
    )

    /** Abre una guía (comprimida o no) con su codificación. */
    fun lector(entrada: InputStream): Reader {
        val b = BufferedInputStream(entrada, 64 * 1024)
        b.mark(4)
        val m1 = b.read(); val m2 = b.read()
        b.reset()
        val datos = if (m1 == 0x1f && m2 == 0x8b) BufferedInputStream(GZIPInputStream(b, 64 * 1024), 64 * 1024) else b
        // La codificación de la declaración (<?xml version="1.0" encoding="ISO-8859-1"?>); si no dice, UTF-8
        datos.mark(512)
        val cab = ByteArray(512); var n = 0
        while (n < cab.size) { val r = datos.read(cab, n, cab.size - n); if (r <= 0) break; n += r }
        datos.reset()
        val txt = String(cab, 0, n, Charsets.ISO_8859_1)
        val cod = Regex("""<\?xml[^>]*encoding\s*=\s*["']([A-Za-z0-9_\-.]+)["']""").find(txt)?.groupValues?.get(1)
        val cs = cod?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return InputStreamReader(datos, cs)
    }

    /** Lee una guía. Devuelve cuántos programas se han dado. */
    fun lee(entrada: InputStream, op: Opciones = Opciones(), alCanal: (CanalGuia) -> Unit, alPrograma: (Programa) -> Unit): Int =
        lee(lector(entrada), op, alCanal, alPrograma)

    fun lee(r: Reader, op: Opciones, alCanal: (CanalGuia) -> Unit, alPrograma: (Programa) -> Unit): Int {
        val x = LectorXml(r)
        val decidido = HashMap<String, Boolean>()
        var dados = 0
        // Lo del elemento abierto
        var enCanal = false; var cId = ""; val cNombres = ArrayList<String>(); var cIcono = ""
        var enProg = false; var pCanal = ""; var pIni = 0L; var pFin = 0L; var pVale = false
        var titulo = ""; var sub = ""; var desc = ""; var cat = ""; var icono = ""; var ep = ""
        var campo = ""
        val texto = StringBuilder()
        while (true) {
            when (x.siguiente()) {
                LectorXml.FIN -> break
                LectorXml.ABRE -> {
                    val n = x.nombre
                    when {
                        n == "channel" -> { enCanal = true; cId = x.atributo("id"); cNombres.clear(); cIcono = "" }
                        n == "programme" -> {
                            enProg = true; pCanal = x.atributo("channel")
                            pIni = hora(x.atributo("start")); pFin = hora(x.atributo("stop"))
                            val ventana = pIni > 0 && (pFin <= 0 || pFin > op.desde) && pIni < op.hasta
                            pVale = ventana && pCanal.isNotEmpty() && decidido.getOrPut(pCanal) { op.quiero(pCanal) }
                            titulo = ""; sub = ""; desc = ""; cat = ""; icono = ""; ep = ""
                        }
                        n == "icon" -> { val s = x.atributo("src"); if (enProg && pVale) { if (icono.isEmpty()) icono = s } else if (enCanal && cIcono.isEmpty()) cIcono = s }
                        enCanal && n == "display-name" -> { campo = n; texto.setLength(0) }
                        enProg && pVale && (n == "title" || n == "sub-title" || n == "desc" || n == "category" || n == "episode-num") -> { campo = n; texto.setLength(0) }
                    }
                    // (un elemento vacío, <icon …/>, llega luego como CIERRA: lo cierra lo de abajo)
                }
                LectorXml.TEXTO -> if (campo.isNotEmpty() && texto.length < op.maxDescripcion + 50) texto.append(x.texto)
                LectorXml.CIERRA -> {
                    val n = x.nombre
                    if (n == campo) {
                        val t = texto.toString().trim()
                        when (n) {
                            "display-name" -> if (t.isNotEmpty()) cNombres += t
                            "title" -> if (titulo.isEmpty()) titulo = t
                            "sub-title" -> if (sub.isEmpty()) sub = t
                            "desc" -> if (desc.isEmpty()) desc = if (t.length > op.maxDescripcion) t.take(op.maxDescripcion).trimEnd() + "…" else t
                            "category" -> if (cat.isEmpty()) cat = t
                            "episode-num" -> if (ep.isEmpty()) ep = t
                        }
                        campo = ""
                    }
                    if (n == "channel" && enCanal) { enCanal = false; if (cId.isNotEmpty()) alCanal(CanalGuia(cId, cNombres.toList(), cIcono)) }
                    if (n == "programme" && enProg) {
                        enProg = false
                        if (pVale) {
                            val fin = if (pFin > pIni) pFin else pIni + 30 * 60_000L
                            alPrograma(Programa(pCanal, pIni, fin, titulo, sub, desc, cat, icono, ep)); dados++
                        }
                    }
                }
            }
        }
        return dados
    }

    private val ZONAS = mapOf("UTC" to 0, "GMT" to 0, "Z" to 0, "UT" to 0, "BST" to 60, "CET" to 60, "CEST" to 120, "WET" to 0, "WEST" to 60, "EET" to 120, "EEST" to 180,
        "EST" to -300, "EDT" to -240, "CST" to -360, "CDT" to -300, "MST" to -420, "MDT" to -360, "PST" to -480, "PDT" to -420)

    /** «20260115203000 +0100» (o un trozo del principio, con o sin zona) en milisegundos UTC; 0 si no se entiende. */
    fun hora(s: String): Long {
        val t = s.trim()
        if (t.length < 4) return 0L
        val digitos = t.takeWhile { it.isDigit() }
        if (digitos.length < 4) return 0L
        fun parte(i: Int, l: Int, def: Int) = if (digitos.length >= i + l) digitos.substring(i, i + l).toInt() else def
        val ldt = runCatching { LocalDateTime.of(parte(0, 4, 1970), parte(4, 2, 1), parte(6, 2, 1), parte(8, 2, 0), parte(10, 2, 0), parte(12, 2, 0)) }.getOrNull() ?: return 0L
        val resto = t.substring(digitos.length).trim().let { if (it.startsWith('.')) it.dropWhile { c -> c == '.' || c.isDigit() }.trim() else it }
        val minutos = when {
            resto.isEmpty() -> 0
            resto[0] == '+' || resto[0] == '-' -> {
                val d = resto.drop(1).filter { it.isDigit() }
                val h = d.take(2).toIntOrNull() ?: 0; val m = d.drop(2).take(2).toIntOrNull() ?: 0
                (h * 60 + m) * if (resto[0] == '-') -1 else 1
            }
            else -> ZONAS[resto.uppercase()] ?: 0
        }
        return ldt.toEpochSecond(ZoneOffset.UTC) * 1000L - minutos * 60_000L
    }
}

/**
 * Un lector de XML a trozos, lo justo para las guías: da [ABRE] (con [nombre], sus atributos y si es `<… />`), [TEXTO] y [CIERRA]. Se
 * salta la declaración, los comentarios, el DOCTYPE y las instrucciones; el CDATA es texto; las entidades (`&amp;`, `&#233;`, `&#xE9;`…)
 * se traducen.
 */
class LectorXml(private val r: Reader) {
    companion object { const val FIN = 0; const val ABRE = 1; const val TEXTO = 2; const val CIERRA = 3 }

    private val buf = CharArray(64 * 1024)
    private var n = 0
    private var i = 0
    var nombre = ""; private set
    var vacio = false; private set
    private val atributos = HashMap<String, String>()
    private val sb = StringBuilder()
    val texto: CharSequence get() = sb
    private var cierraPendiente: String? = null

    fun atributo(k: String): String = atributos[k].orEmpty()

    private fun lee(): Int {
        if (i >= n) { n = r.read(buf); i = 0; if (n <= 0) { n = 0; return -1 } }
        return buf[i++].code
    }
    private var devuelto = -2
    private fun c(): Int { if (devuelto != -2) { val x = devuelto; devuelto = -2; return x }; return lee() }
    private fun devuelve(x: Int) { devuelto = x }

    fun siguiente(): Int {
        cierraPendiente?.let { nombre = it; cierraPendiente = null; return CIERRA }
        sb.setLength(0)
        var x = c()
        if (x == -1) return FIN
        if (x != '<'.code) {
            // Texto hasta el siguiente <
            while (x != -1 && x != '<'.code) {
                if (x == '&'.code) entidad() else sb.append(x.toChar())
                x = c()
            }
            if (x == '<'.code) devuelve(x)
            return if (sb.isNotEmpty()) TEXTO else siguiente()
        }
        x = c()
        when (x) {
            '?'.code -> { saltaHasta("?>"); return siguiente() }
            '!'.code -> {
                val a = c(); val b = c()
                if (a == '-'.code && b == '-'.code) { saltaHasta("-->"); return siguiente() }
                if (a == '['.code && b == 'C'.code) {
                    // <![CDATA[ … ]]>
                    repeat(5) { c() }
                    hastaFinCdata(); return if (sb.isNotEmpty()) TEXTO else siguiente()
                }
                saltaDoctype(); return siguiente()
            }
            '/'.code -> {
                val nm = StringBuilder()
                var y = c()
                while (y != -1 && y != '>'.code) { if (!y.toChar().isWhitespace()) nm.append(y.toChar()); y = c() }
                nombre = nm.toString(); return CIERRA
            }
            else -> {
                atributos.clear(); vacio = false
                val nm = StringBuilder()
                var y = x
                while (y != -1 && !y.toChar().isWhitespace() && y != '>'.code && y != '/'.code) { nm.append(y.toChar()); y = c() }
                nombre = nm.toString()
                // Los atributos
                while (true) {
                    while (y != -1 && y.toChar().isWhitespace()) y = c()
                    if (y == -1) return FIN
                    if (y == '>'.code) break
                    if (y == '/'.code) { y = c(); if (y == '>'.code) { vacio = true; break }; continue }
                    val k = StringBuilder()
                    while (y != -1 && y != '='.code && y != '>'.code && y != '/'.code && !y.toChar().isWhitespace()) { k.append(y.toChar()); y = c() }
                    while (y != -1 && y.toChar().isWhitespace()) y = c()
                    if (y != '='.code) { atributos[k.toString()] = ""; continue }
                    y = c()
                    while (y != -1 && y.toChar().isWhitespace()) y = c()
                    val q = y
                    sb.setLength(0)
                    if (q == '"'.code || q == '\''.code) {
                        y = c()
                        while (y != -1 && y != q) { if (y == '&'.code) entidad() else sb.append(y.toChar()); y = c() }
                        y = c()
                    } else {
                        while (y != -1 && !y.toChar().isWhitespace() && y != '>'.code) { sb.append(y.toChar()); y = c() }
                    }
                    atributos[k.toString()] = sb.toString()
                    sb.setLength(0)
                }
                if (vacio) cierraPendiente = nombre
                return ABRE
            }
        }
    }

    private fun entidad() {
        val e = StringBuilder()
        var y = c()
        while (y != -1 && y != ';'.code && e.length < 12 && y != '<'.code && !y.toChar().isWhitespace()) { e.append(y.toChar()); y = c() }
        if (y != ';'.code) { sb.append('&').append(e); if (y != -1) devuelve(y); return }
        val s = e.toString()
        when {
            s == "amp" -> sb.append('&'); s == "lt" -> sb.append('<'); s == "gt" -> sb.append('>'); s == "quot" -> sb.append('"'); s == "apos" -> sb.append('\''); s == "nbsp" -> sb.append(' ')
            s.startsWith("#x") || s.startsWith("#X") -> s.drop(2).toIntOrNull(16)?.let { sb.appendCodePoint(it) } ?: sb.append("&$s;")
            s.startsWith("#") -> s.drop(1).toIntOrNull()?.let { sb.appendCodePoint(it) } ?: sb.append("&$s;")
            else -> sb.append("&$s;")
        }
    }

    private fun saltaHasta(fin: String) {
        var k = 0
        while (true) { val y = c(); if (y == -1) return; if (y == fin[k].code) { k++; if (k == fin.length) return } else k = if (y == fin[0].code) 1 else 0 }
    }

    private fun hastaFinCdata() {
        var k = 0
        while (true) {
            val y = c(); if (y == -1) return
            if (y == ']'.code) { k++; continue }
            if (y == '>'.code && k >= 2) { repeat(k - 2) { sb.append(']') }; return }
            repeat(k) { sb.append(']') }; k = 0
            sb.append(y.toChar())
        }
    }

    private fun saltaDoctype() {
        var prof = 0
        while (true) { val y = c(); if (y == -1) return; if (y == '['.code) prof++; if (y == ']'.code) prof--; if (y == '>'.code && prof <= 0) return }
    }
}
