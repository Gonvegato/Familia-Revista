package com.gonzalo.iptv

import java.io.BufferedReader
import java.io.InputStream
import java.io.Reader
import java.net.URLDecoder
import java.security.MessageDigest

/** Lo de la cabecera `#EXTM3U` de una lista: sus guías (`url-tvg`, `x-tvg-url`, `tvg-url`; pueden ser varias, con comas) y su desfase. */
data class CabeceraM3u(val guias: List<String> = emptyList(), val desfase: Double = 0.0, val catchup: String = "", val catchupFuente: String = "", val catchupDias: Int = 0) {
    override fun toString() = "CabeceraM3u(${guias.size} guías)"
}

/**
 * Las listas M3U de IPTV («M3U extendido», el de Kodi —PVR IPTV Simple—, VLC, TiviMate, Televizo…), leídas línea a línea: una lista de
 * cien mil entradas no se tiene nunca entera en memoria, cada canal sale por [alCanal] en cuanto se lee.
 *
 *  - `#EXTM3U url-tvg="…" x-tvg-url="…" tvg-shift="…" catchup="…"`: la cabecera.
 *  - `#EXTINF:-1 tvg-id="…" tvg-name="…" tvg-logo="…" group-title="…" tvg-chno="…" tvg-shift="…" catchup="…" catchup-days="…"
 *    catchup-source="…" radio="true",Nombre`: un canal. Los atributos pueden ir sin comillas, y el nombre puede llevar comas (el nombre
 *    empieza en la primera coma que no está dentro de comillas).
 *  - `#EXTGRP:Grupo`: el grupo de lo que sigue (como en Kodi: vale hasta otro `#EXTGRP`, también uno vacío); `group-title` manda sobre él.
 *  - `#EXTVLCOPT:http-user-agent=…`, `http-referrer=…`, `http-origin=…`: cabeceras para ese canal.
 *  - `#KODIPROP:clave=valor`: propiedades de Kodi; de ellas, `inputstream.adaptive.stream_headers` / `common_headers` (cabeceras como
 *    `User-Agent=…&Referer=…`) se usan; las de DRM se apuntan para avisar de que no se puede.
 *  - La dirección puede llevar cabeceras detrás de una barra: `http://…|User-Agent=…&Referer=…` (valores con %XX).
 */
object M3u {
    /** Lee una lista entera, canal a canal. Devuelve la cabecera (y cuántos canales). */
    fun lee(entrada: InputStream, alCanal: (Canal) -> Unit): Pair<CabeceraM3u, Int> = lee(entrada.bufferedReader(Charsets.UTF_8), alCanal)

    fun lee(r: Reader, alCanal: (Canal) -> Unit): Pair<CabeceraM3u, Int> {
        val br = r as? BufferedReader ?: BufferedReader(r, 64 * 1024)
        var cab = CabeceraM3u()
        var extinf: String? = null
        var grupoSuelto = ""
        val cabeceras = LinkedHashMap<String, String>()
        val props = LinkedHashMap<String, String>()
        val vistas = HashMap<String, Int>()
        var cuantos = 0
        var primera = true
        while (true) {
            var l = br.readLine() ?: break
            if (primera) { l = l.removePrefix("﻿"); primera = false }
            l = l.trim()
            if (l.isEmpty()) continue
            when {
                l.startsWith("#EXTM3U", true) -> cab = cabecera(l)
                l.startsWith("#EXTINF", true) -> { extinf = l; }
                l.startsWith("#EXTGRP:", true) -> grupoSuelto = l.substring(8).trim()
                l.startsWith("#EXTVLCOPT:", true) -> vlcopt(l.substring(11).trim(), cabeceras)
                l.startsWith("#KODIPROP:", true) -> kodiprop(l.substring(10).trim(), props, cabeceras)
                l.startsWith("#") -> {}
                else -> {
                    val inf = extinf
                    extinf = null
                    val c = canal(inf, l, grupoSuelto, cabeceras, props, cab, vistas)
                    cabeceras.clear(); props.clear()
                    if (c != null) { cuantos++; alCanal(c) }
                }
            }
        }
        return cab to cuantos
    }

    /** Todo de una vez (para lo pequeño y las pruebas). */
    fun leeTexto(t: String): Pair<CabeceraM3u, List<Canal>> { val l = ArrayList<Canal>(); val c = lee(t.reader()) { l += it }; return c.first to l }

    private fun cabecera(l: String): CabeceraM3u {
        val a = atributos(l.substringAfter("#EXTM3U", "").let { if (it == l) "" else it })
        val guias = listOf("url-tvg", "x-tvg-url", "tvg-url").flatMap { k -> a[k].orEmpty().split(',') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return CabeceraM3u(guias, a["tvg-shift"]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0, a["catchup"].orEmpty(), a["catchup-source"].orEmpty(), (a["catchup-days"] ?: a["timeshift"])?.toIntOrNull() ?: 0)
    }

    private fun vlcopt(o: String, cab: MutableMap<String, String>) {
        val k = o.substringBefore('=').trim().lowercase(); val v = o.substringAfter('=', "").trim()
        if (v.isEmpty()) return
        when (k) { "http-user-agent" -> cab["User-Agent"] = v; "http-referrer", "http-referer" -> cab["Referer"] = v; "http-origin" -> cab["Origin"] = v; "http-cookie" -> cab["Cookie"] = v }
    }

    private fun kodiprop(o: String, props: MutableMap<String, String>, cab: MutableMap<String, String>) {
        val k = o.substringBefore('=').trim(); val v = o.substringAfter('=', "").trim()
        if (k.isEmpty()) return
        props[k] = v
        if (k.endsWith(".stream_headers") || k.endsWith(".common_headers") || k.endsWith(".manifest_headers")) cabecerasDe(v, cab)
    }

    /** «User-Agent=x&Referer=y» (con %XX) en cabeceras. */
    fun cabecerasDe(s: String, cab: MutableMap<String, String>) {
        for (par in s.split('&')) {
            val k = par.substringBefore('=').trim(); val v = par.substringAfter('=', "").trim()
            if (k.isEmpty() || v.isEmpty()) continue
            val val_ = runCatching { URLDecoder.decode(v.replace("+", "%2B"), "UTF-8") }.getOrDefault(v)
            cab[nombreCabecera(k)] = val_
        }
    }

    private fun nombreCabecera(k: String): String = when (k.lowercase()) {
        "user-agent", "useragent", "http-user-agent" -> "User-Agent"
        "referer", "referrer", "http-referrer", "http-referer" -> "Referer"
        "origin", "http-origin" -> "Origin"
        "cookie" -> "Cookie"
        else -> k
    }

    private fun canal(inf: String?, linea: String, grupoSuelto: String, cab: Map<String, String>, props: Map<String, String>, c: CabeceraM3u, vistas: MutableMap<String, Int>): Canal? {
        // La dirección y sus cabeceras detrás de la barra
        val url = linea.substringBefore('|').trim()
        if (!(url.contains("://") || url.startsWith("/"))) return null
        val cabs = LinkedHashMap(cab)
        if ('|' in linea) cabecerasDe(linea.substringAfter('|'), cabs)
        var nombre = ""; var a: Map<String, String> = emptyMap()
        if (inf != null) {
            val cuerpo = inf.substringAfter(':', "")
            val coma = comaDelNombre(cuerpo)
            nombre = if (coma >= 0) cuerpo.substring(coma + 1).trim() else ""
            a = atributos(if (coma >= 0) cuerpo.substring(0, coma) else cuerpo)
        }
        a["user-agent"]?.takeIf { it.isNotBlank() }?.let { cabs.putIfAbsent("User-Agent", it) }
        (a["http-referrer"] ?: a["referrer"] ?: a["referer"])?.takeIf { it.isNotBlank() }?.let { cabs.putIfAbsent("Referer", it) }
        if (nombre.isBlank()) nombre = a["tvg-name"].orEmpty().ifBlank { url.substringAfterLast('/').substringBefore('?').ifBlank { "Canal" } }
        val tvgId = a["tvg-id"].orEmpty().trim()
        val grupo = a["group-title"]?.trim()?.takeIf { it.isNotEmpty() } ?: grupoSuelto
        val modo = a["catchup"] ?: a["catchup-type"] ?: c.catchup
        val dias = (a["catchup-days"] ?: a["timeshift"] ?: a["tvg-rec"])?.trim()?.toIntOrNull() ?: c.catchupDias
        val catchup = if (modo.isNullOrBlank() && dias <= 0) null else Catchup(modo.orEmpty().ifBlank { "default" }, dias, a["catchup-source"] ?: c.catchupFuente)
        // La clave: la de su tvg-id y su nombre; si se repite (el mismo canal en dos grupos), con su número de aparición
        val base = clave(tvgId, nombre)
        val n = (vistas[base] ?: 0) + 1
        vistas[base] = n
        return Canal(
            clave = if (n == 1) base else "$base~$n", nombre = nombre, grupo = grupo.intern(), logo = a["tvg-logo"] ?: a["logo"] ?: "", tvgId = tvgId,
            tvgNombre = a["tvg-name"].orEmpty(), numero = a["tvg-chno"]?.trim()?.toIntOrNull() ?: 0, url = url, cabeceras = if (cabs.isEmpty()) emptyMap() else cabs,
            catchup = catchup, radio = a["radio"].equals("true", true), propiedades = if (props.isEmpty()) emptyMap() else LinkedHashMap(props),
            desfase = a["tvg-shift"]?.replace(',', '.')?.toDoubleOrNull() ?: c.desfase,
        )
    }

    /** La clave de un canal de una lista M3U: `c` + 16 hexadecimales del SHA-256 de su tvg-id y su nombre (no de su dirección, que cambia). */
    fun clave(tvgId: String, nombre: String): String {
        val h = MessageDigest.getInstance("SHA-256").digest((tvgId.trim() + "\u0000" + nombre.trim()).toByteArray(Charsets.UTF_8))
        return "c" + h.take(8).joinToString("") { "%02x".format(it) }
    }

    /** Dónde empieza el nombre: la primera coma fuera de comillas (los atributos pueden llevar comas). */
    internal fun comaDelNombre(s: String): Int {
        var dentro = false; var q = ' '
        for (i in s.indices) {
            val ch = s[i]
            if (dentro) { if (ch == q) dentro = false }
            else if (ch == '"' || ch == '\'') {
                // (solo es una comilla de atributo si va detrás de un =)
                if (i > 0 && s[i - 1] == '=') { dentro = true; q = ch }
            } else if (ch == ',') return i
        }
        return -1
    }

    /** Los atributos `clave="valor"` (o `clave=valor`, o con comillas simples) de una línea. Las claves, en minúsculas. */
    internal fun atributos(s: String): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        var i = 0
        while (i < s.length) {
            while (i < s.length && (s[i].isWhitespace() || s[i] == ',')) i++
            val ini = i
            while (i < s.length && s[i] != '=' && !s[i].isWhitespace()) i++
            if (i >= s.length || s[i] != '=') { while (i < s.length && !s[i].isWhitespace()) i++; continue }
            val k = s.substring(ini, i).lowercase()
            i++
            val v: String
            if (i < s.length && (s[i] == '"' || s[i] == '\'')) {
                val q = s[i]; i++
                val fin = s.indexOf(q, i).let { if (it < 0) s.length else it }
                v = s.substring(i, fin); i = fin + 1
            } else {
                val ini2 = i
                while (i < s.length && !s[i].isWhitespace()) i++
                v = s.substring(ini2, i)
            }
            if (k.isNotEmpty() && k.none { it == '"' }) m[k] = v.trim()
        }
        return m
    }
}
