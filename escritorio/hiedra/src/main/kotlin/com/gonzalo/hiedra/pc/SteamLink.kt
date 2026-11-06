package com.gonzalo.hiedra.pc

import com.gonzalo.familia.pc.Sitio
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Jugar en el móvil en streaming, con Steam Link (la app de Valve), como en Atrio del PC (steamlink.js): el streaming lo hace Steam (su
 * Remote Play), no Hiedra. Aquí solo:
 *
 *  - Se mira si Steam está instalado y si está abierto (Steam Link no conecta con un PC sin Steam abierto).
 *  - Se abre Steam (en segundo plano, `-silent`) cuando el móvil va a jugar en streaming, y se espera a que arranque.
 *  - Se publican las direcciones de red y las MAC de este ordenador (para encenderlo por red desde el móvil).
 *
 * Lo que lee el móvil, en la presencia (`st`): `on` (Steam instalado), `run` (abierto), `name`, `lan` (IPv4), `mac`; y `ok` (este PC deja
 * prepararlo desde el móvil) y `host: "steamlink"`, como Atrio.
 */
object SteamLink {
    private const val FLATPAK = "com.valvesoftware.Steam"
    private val casa get() = File(System.getProperty("user.home"))

    /** (En las pruebas: cómo se abre Steam, si está abierto y qué pasa al abrirlo.) */
    @Volatile var ordenPruebas: (() -> List<String>?)? = null
    @Volatile var abiertoPruebas: (() -> Boolean)? = null
    @Volatile var abrePruebas: ((List<String>) -> Unit)? = null

    class Info(val instalado: Boolean, val abierto: Boolean, val nombre: String, val ips: List<String>, val macs: List<String>)

    /** Cómo se abre Steam en este ordenador (en segundo plano), o null si no está instalado. */
    fun orden(): List<String>? {
        ordenPruebas?.let { return it() }
        if (Sitio.sistema == Sitio.Sistema.MAC) {
            val app = listOf(File("/Applications/Steam.app"), File(casa, "Applications/Steam.app")).firstOrNull { it.isDirectory } ?: return null
            return listOf("/usr/bin/open", "-g", "-a", app.path, "--args", "-silent")
        }
        Programas.donde("steam")?.let { return listOf(it.path, "-silent") }
        if (Programas.enPath("flatpak") && (File(casa, ".var/app/$FLATPAK").isDirectory || FLATPAK in Programas.flatpaks())) return listOf("flatpak", "run", FLATPAK, "-silent")
        return null
    }

    /** ¿Está Steam abierto? En Linux, un proceso «steam» (el cliente y su lanzador se llaman así, también el de Flatpak); en el Mac, steam_osx. */
    fun abierto(): Boolean {
        abiertoPruebas?.let { return it() }
        if (Sitio.sistema == Sitio.Sistema.MAC) return Programas.salida(listOf("/usr/bin/pgrep", "-x", "steam_osx"), 4)?.isNotBlank() == true
        return File("/proc").listFiles { f -> f.name.all(Char::isDigit) }.orEmpty().any { d -> runCatching { File(d, "comm").readText().trim() == "steam" }.getOrDefault(false) }
    }

    /** Interfaces que no son la red de casa (Docker, máquinas virtuales, VPN…). */
    fun esVirtual(nombre: String) = Regex("^(lo|docker|br-|veth|virbr|vmnet|vboxnet|tailscale|zt|wg|tun|tap|utun|awdl|llw|anpi|bridge)", RegexOption.IGNORE_CASE).containsMatchIn(nombre)
    /** 100.64.0.0/10: las redes privadas virtuales (Tailscale…), que no sirven para encender el PC por red. */
    fun esCgnat(ip: String): Boolean = Regex("^100\\.(6[4-9]|[7-9]\\d|1[01]\\d|12[0-7])\\.").containsMatchIn(ip)
    /** «aa:bb:cc:dd:ee:ff» (como la escribía Atrio; el móvil la lee así), o null si no es una MAC de verdad. */
    fun mac(b: ByteArray?): String? = b?.takeIf { it.size == 6 && it.any { x -> x.toInt() != 0 } }?.joinToString(":") { "%02x".format(it) }

    /** Las IPv4 de la red de casa y las MAC de sus tarjetas. */
    fun red(): Pair<List<String>, List<String>> {
        val ips = ArrayList<String>(); val macs = ArrayList<String>()
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { ni ->
                if (runCatching { !ni.isUp || ni.isLoopback || ni.isVirtual }.getOrDefault(true) || esVirtual(ni.name)) return@forEach
                val v4 = ni.inetAddresses.toList().filterIsInstance<Inet4Address>().map { it.hostAddress }.filter { !esCgnat(it) }
                if (v4.isEmpty()) return@forEach
                ips += v4
                mac(runCatching { ni.hardwareAddress }.getOrNull())?.let { if (it !in macs) macs += it }
            }
        }
        return ips to macs
    }

    fun info(): Info { val (ips, macs) = red(); return Info(orden() != null, abierto(), Sitio.nombreEquipo, ips, macs) }

    /** Lo que va en la presencia (`st`). */
    fun presencia(i: Info = info(), permitido: Boolean = AjustesTuPc.streamingDesdeMovil): Map<String, Any?> =
        linkedMapOf("on" to i.instalado, "run" to i.abierto, "name" to i.nombre, "lan" to i.ips, "mac" to i.macs, "ok" to permitido, "host" to "steamlink")

    /** Abre Steam si no lo está (en segundo plano) y espera a que arranque (hasta [esperaMs]). Falla, en español, si no puede. */
    fun abreSteam(esperaMs: Long = 30_000, paso: Long = 750) {
        if (abierto()) return
        val o = orden() ?: throw IOException("Steam no está instalado en ${Sitio.nombreEquipo}: Steam Link necesita Steam abierto en el ordenador.")
        runCatching { abrePruebas?.invoke(o) ?: Programas.abre(o) }
        val hasta = System.currentTimeMillis() + esperaMs
        while (System.currentTimeMillis() < hasta) {
            if (abierto()) return
            Thread.sleep(paso)
        }
        if (!abierto()) throw IOException("No he podido abrir Steam en ${Sitio.nombreEquipo}. Ábrelo a mano y vuelve a probar.")
    }
}
