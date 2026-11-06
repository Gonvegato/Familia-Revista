package com.gonzalo.hiedra.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Sitio

/**
 * Este ordenador como «Tu PC» para Hiedra del móvil (el protocolo de siempre de Atrio del PC, `suite/` en la cuenta; ver Suite.kt de
 * Hiedra de Android):
 *
 *  - `suite/live/<dispositivo>`: que está encendido y a qué se juega (cada minuto). Lleva `tiempo: true`: este PC cuenta él mismo su
 *    tiempo (en `tiempo/<juego>~<dispositivo>`), así el móvil no lo vuelve a contar.
 *  - `suite/lib/<dispositivo>`: su biblioteca (Steam, Heroic, Lutris, tus ROMs y tus programas; `s` dice de dónde, como en Atrio del PC:
 *    `steam`, `heroic`, `lutris`, `emulators` o `local`).
 *  - `suite/cmd/<dispositivo>/<id>`: órdenes del móvil (`launch`: jugar a uno; `ping`); la respuesta, en `res`.
 */
object TuPc {
    @Volatile private var activo = false
    private val hechas = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun base() = "/users/${LaFamilia.uid}/suite"
    private fun dev() = Juegos.dispositivo()
    private fun ahora() = mapOf(".sv" to "timestamp")

    /** Lo que publica de sí mismo (lo que lee el móvil: `app`, `name`, `os`, `on`, `at`, `game`). */
    fun presencia(on: Boolean): Map<String, Any?> {
        val p = Pc.ahora
        val m = linkedMapOf<String, Any?>("app" to "atrio", "cliente" to "hiedra", "name" to "Hiedra · ${Sitio.nombreEquipo}", "dn" to Sitio.nombreEquipo,
            "os" to if (Sitio.sistema == Sitio.Sistema.MAC) "mac" else "linux", "on" to on, "at" to ahora(), "shot" to false, "tiempo" to true)
        if (p != null && on) m["game"] = mapOf("key" to p.clave, "name" to p.nombre, "s" to p.desde, "cover" to p.portada, "sg" to 0L, "hero" to "", "logo" to "")
        return m
    }

    /** La biblioteca de este PC tal como la lee el móvil (`k`, `n`, `c`, `lp`, `m`, `f`, `s`). Los minutos van a 0: las horas ya están en la cuenta. */
    fun biblioteca(): Map<String, Any?> = mapOf("name" to "Hiedra · ${Sitio.nombreEquipo}",
        "games" to Pc.lista().map { j -> mapOf("k" to j.clave, "n" to j.nombre, "c" to j.portada.takeIf { it.startsWith("http") }.orEmpty(), "lp" to 0L, "m" to 0L, "f" to 0L, "s" to j.fuente.id, "sg" to 0L) })

    fun publica(on: Boolean = true) {
        runCatching { LaFamilia.db("PUT", "${base()}/live/${dev()}", Json.escribe(presencia(on)), "print=silent") }
    }
    fun publicaBiblioteca() { runCatching { LaFamilia.db("PUT", "${base()}/lib/${dev()}", Json.escribe(biblioteca()), "print=silent") } }

    /** Atiende una orden del móvil. */
    fun atiende(id: String, c: Map<String, Any?>): Map<String, Any?> = when (c["op"].comoTexto()) {
        "ping" -> mapOf("ok" to true, "msg" to "Hiedra en ${Sitio.nombreEquipo}")
        "launch" -> {
            val key = c["a"].comoMapa()["key"].comoTexto()
            val j = Pc.por(key)
            if (j == null) mapOf("ok" to false, "msg" to "Ese juego no está en ${Sitio.nombreEquipo}.")
            else { val jid = Juegos.mete(j, jugando = true); mapOf("ok" to true, "msg" to Pc.juega(j, jid)) }
        }
        else -> mapOf("ok" to false, "msg" to "Hiedra del ordenador no hace eso todavía (${c["op"].comoTexto()}).")
    }

    private fun mira() {
        val t = runCatching { LaFamilia.db("GET", "${base()}/cmd/${dev()}") }.getOrNull() ?: return
        Json.lee(t).comoMapa().forEach { (id, v) ->
            val c = v.comoMapa()
            if (c.containsKey("res") || id in hechas) return@forEach
            if (System.currentTimeMillis() - (c["t"] as? Number ?: 0L).toLong() > 10 * 60_000L) return@forEach
            hechas += id
            val res = runCatching { atiende(id, c) }.getOrElse { mapOf("ok" to false, "msg" to (it.message ?: "Error")) } + ("t" to ahora())
            runCatching { LaFamilia.db("PUT", "${base()}/cmd/${dev()}/$id/res", Json.escribe(res), "print=silent") }
        }
    }

    /** Al arrancar: se publica, se escuchan las órdenes y cada minuto se dice que sigue encendido. */
    fun inicia() {
        if (activo) return
        activo = true
        Pc.alCambiarPartida = { Thread { publica() }.start() }
        Thread({ while (activo) { if (LaFamilia.dentro) { publica(); runCatching { mira() } }; try { Thread.sleep(60_000) } catch (_: InterruptedException) { break } } }, "hiedra-presencia").apply { isDaemon = true; start() }
        Thread({ try { Thread.sleep(5_000) } catch (_: Exception) {}; if (LaFamilia.dentro) publicaBiblioteca() }, "hiedra-biblioteca").apply { isDaemon = true; start() }
        // Y otra vez cada vez que se vuelve a mirar lo de este ordenador (una ROM nueva, un juego instalado)
        Pc.alMirar = { Thread { if (LaFamilia.dentro) publicaBiblioteca() }.start() }
        Thread({ LaFamilia.escuchaRuta({ "${base()}/cmd/${dev()}" }, { activo }) { _, _ -> runCatching { mira() } } }, "hiedra-ordenes").apply { isDaemon = true; start() }
    }

    fun alCerrar() { activo = false; publica(false) }
}
