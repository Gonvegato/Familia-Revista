package com.gonzalo.hiedra.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Sitio

/**
 * Este ordenador como «Tu PC» para Hiedra del móvil (el protocolo de siempre de Atrio del PC, `suite/` en la cuenta; ver Suite.kt de
 * Hiedra de Android):
 *
 *  - `suite/live/<dispositivo>`: que está encendido y a qué se juega (cada minuto). Lleva `tiempo: true`: este PC cuenta él mismo su
 *    tiempo (en `tiempo/<juego>~<dispositivo>`), así el móvil no lo vuelve a contar; `shot`: si deja pedirle capturas; `st`: su Steam,
 *    para el streaming ([SteamLink.presencia]).
 *  - `suite/shots/<id>` y `shotdata/<id>/<i>`: las capturas ([Capturas]).
 *  - `suite/lib/<dispositivo>`: su biblioteca (Steam, Heroic, Lutris, tus ROMs y tus programas; `s` dice de dónde, como en Atrio del PC:
 *    `steam`, `heroic`, `lutris`, `emulators` o `local`).
 *  - `suite/cmd/<dispositivo>/<id>`: órdenes del móvil; la respuesta, en `res` (`ok`, `msg` y lo que toque). `launch` (`a.key`): jugar a uno;
 *    `shot`: una captura de la pantalla (ver [Capturas]; responde con su id en `shot`); `stream` (`a.key`, si viene): prepara el
 *    streaming con Steam Link (ver [SteamLink]: abre Steam y, si se dice, el juego); `ping`. Cada orden se atiende en su hilo (una
 *    captura o abrir Steam tardan) y las ya contestadas se borran a los cinco minutos, como en Atrio.
 */
object TuPc {
    @Volatile private var activo = false
    private val hechas = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun base() = "/users/${LaFamilia.uid}/suite"
    private fun dev() = Juegos.dispositivo()
    private fun ahora() = mapOf(".sv" to "timestamp")

    /** Lo que publica de sí mismo (lo que lee el móvil: `app`, `name`, `dn`, `os`, `on`, `at`, `game`, `shot`, `st`, `tiempo`). */
    fun presencia(on: Boolean): Map<String, Any?> {
        val p = Pc.ahora
        val m = linkedMapOf<String, Any?>("app" to "atrio", "cliente" to "hiedra", "name" to "Bastet · ${Sitio.nombreEquipo}", "dn" to Sitio.nombreEquipo,
            "os" to if (Sitio.sistema == Sitio.Sistema.MAC) "mac" else "linux", "on" to on, "at" to ahora(), "shot" to AjustesTuPc.capturasDesdeMovil, "tiempo" to true,
            "st" to runCatching { SteamLink.presencia() }.getOrNull())
        if (p != null && on) m["game"] = mapOf("key" to p.clave, "name" to p.nombre, "s" to p.desde, "cover" to p.portada, "sg" to 0L, "hero" to "", "logo" to "")
        return m
    }

    /** La biblioteca de este PC tal como la lee el móvil (`k`, `n`, `c`, `lp`, `m`, `f`, `s`). Los minutos van a 0: las horas ya están en la cuenta. */
    fun biblioteca(): Map<String, Any?> = mapOf("name" to "Bastet · ${Sitio.nombreEquipo}",
        "games" to Pc.lista().map { j -> mapOf("k" to j.clave, "n" to j.nombre, "c" to j.portada.takeIf { it.startsWith("http") }.orEmpty(), "lp" to 0L, "m" to 0L, "f" to 0L, "s" to j.fuente.id, "sg" to 0L) })

    fun publica(on: Boolean = true) {
        runCatching { LaFamilia.db("PUT", "${base()}/live/${dev()}", Json.escribe(presencia(on)), "print=silent") }
    }
    fun publicaBiblioteca() { runCatching { LaFamilia.db("PUT", "${base()}/lib/${dev()}", Json.escribe(biblioteca()), "print=silent") } }

    /** Atiende una orden del móvil. */
    fun atiende(id: String, c: Map<String, Any?>): Map<String, Any?> = when (c["op"].comoTexto()) {
        "ping" -> mapOf("ok" to true, "msg" to "Bastet en ${Sitio.nombreEquipo}")
        "launch" -> {
            val key = c["a"].comoMapa()["key"].comoTexto()
            val j = Pc.por(key)
            if (j == null) mapOf("ok" to false, "msg" to "Ese juego no está en ${Sitio.nombreEquipo}.")
            else { val jid = Juegos.mete(j, jugando = true); mapOf("ok" to true, "msg" to Pc.juega(j, jid)) }
        }
        "shot" -> {
            if (!AjustesTuPc.capturasDesdeMovil) mapOf("ok" to false, "msg" to "En ${Sitio.nombreEquipo} no se pueden pedir capturas desde el móvil (se cambia en Bastet del ordenador: Ajustes → Este PC).")
            else {
                // (las que pide el móvil van siempre al móvil, aunque las de aquí no vayan)
                val h = Capturas.haz(por = c["by"].comoTexto().ifBlank { "Móvil" }, alMovil = true)
                if (!h.subida) mapOf("ok" to false, "msg" to "Captura hecha en ${Sitio.nombreEquipo}, pero no ha podido subir.")
                else mapOf("ok" to true, "msg" to "Captura de ${h.w}×${h.h}", "shot" to h.id)
            }
        }
        "stream" -> streaming(c["a"].comoMapa()["key"].comoTexto())
        else -> mapOf("ok" to false, "msg" to "Bastet del ordenador no hace eso todavía (${c["op"].comoTexto()}).")
    }

    /**
     * Jugar en streaming: Steam abierto y, si viene [key], el juego (si no estaba ya en marcha); después el móvil abre Steam Link. Como en
     * Atrio, también con un juego que no es de Steam (Steam Link enseña el escritorio).
     */
    private fun streaming(key: String): Map<String, Any?> {
        if (!AjustesTuPc.streamingDesdeMovil) return mapOf("ok" to false, "msg" to "En ${Sitio.nombreEquipo} no está permitido preparar el streaming desde el móvil (se cambia en Bastet del ordenador: Ajustes → Este PC).")
        SteamLink.abreSteam()
        var msg = "Steam listo"
        if (key.isNotBlank()) {
            val ya = Pc.ahora
            if (ya != null && ya.clave == key) msg = "${ya.nombre} ya está en marcha"
            else {
                val j = Pc.por(key) ?: return mapOf("ok" to false, "msg" to "Ese juego no está en ${Sitio.nombreEquipo}.")
                msg = Pc.juega(j, Juegos.mete(j, jugando = true))
            }
        }
        runCatching { publica() }   // (ya con Steam abierto: `st.run`)
        return mapOf("ok" to true, "msg" to msg)
    }

    /** Las órdenes que quedan por atender, cada una en su hilo; las contestadas hace más de cinco minutos se borran. */
    internal fun mira(momento: Long = System.currentTimeMillis(), esperar: Boolean = false) {
        val t = runCatching { LaFamilia.db("GET", "${base()}/cmd/${dev()}") }.getOrNull() ?: return
        val hilos = ArrayList<Thread>()
        Json.lee(t).comoMapa().forEach { (id, v) ->
            val c = v.comoMapa()
            if (c.containsKey("res")) {
                if (momento - c["res"].comoMapa()["t"].comoLong() > 5 * 60_000L) runCatching { LaFamilia.db("DELETE", "${base()}/cmd/${dev()}/$id") }
                return@forEach
            }
            if (momento - c["t"].comoLong() > 10 * 60_000L) return@forEach
            if (!hechas.add(id)) return@forEach
            hilos += Thread({
                val res = runCatching { atiende(id, c) }.getOrElse { mapOf("ok" to false, "msg" to (it.message ?: "Error")) } + ("t" to ahora())
                runCatching { LaFamilia.db("PUT", "${base()}/cmd/${dev()}/$id/res", Json.escribe(res), "print=silent") }
            }, "hiedra-orden").apply { isDaemon = true; start() }
        }
        if (esperar) hilos.forEach { it.join() }
    }

    /** Al arrancar: se publica, se escuchan las órdenes y cada minuto se dice que sigue encendido. */
    fun inicia() {
        if (activo) return
        activo = true
        Pc.alCambiarPartida = { Thread { publica() }.start() }
        Thread({
            var vuelta = 0
            while (activo) {
                if (LaFamilia.dentro) { publica(); runCatching { mira() }; if (vuelta++ % 10 == 0) runCatching { Capturas.limpia() } }
                try { Thread.sleep(60_000) } catch (_: InterruptedException) { break }
            }
        }, "hiedra-presencia").apply { isDaemon = true; start() }
        Thread({ try { Thread.sleep(5_000) } catch (_: Exception) {}; if (LaFamilia.dentro) publicaBiblioteca() }, "hiedra-biblioteca").apply { isDaemon = true; start() }
        // Y otra vez cada vez que se vuelve a mirar lo de este ordenador (una ROM nueva, un juego instalado)
        Pc.alMirar = { Thread { if (LaFamilia.dentro) publicaBiblioteca() }.start() }
        Thread({ LaFamilia.escuchaRuta({ "${base()}/cmd/${dev()}" }, { activo }) { _, _ -> runCatching { mira() } } }, "hiedra-ordenes").apply { isDaemon = true; start() }
    }

    fun alCerrar() { activo = false; publica(false) }
}
