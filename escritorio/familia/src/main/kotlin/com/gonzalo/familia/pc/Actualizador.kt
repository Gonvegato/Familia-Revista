package com.gonzalo.familia.pc

import java.io.File

/**
 * Actualizarse sola desde tu kDrive, como en Android: cada versión de escritorio se sube a «Mi carpeta/versiones» con su sistema en
 * el nombre (`Azabache-1.2.0-mac.dmg`, `Azabache-1.2.0-linux.deb`, `Azabache-1.2.0-linux.tar.gz`). Los actualizadores de Android solo
 * cogen `^<App>-<versión>\.apk$`, así que nunca se confunden.
 *
 * En el Mac se baja el .dmg a Descargas y se abre (arrastras la app a Aplicaciones, encima de la vieja). En Linux, el .deb se abre con
 * el instalador del sistema. No hay firma de Apple (es de pago): la primera vez, Ajustes del Sistema → Privacidad y seguridad → «Abrir igualmente».
 */
object Actualizador {
    class Nueva(val version: String, val archivo: KDrive.Archivo)

    /** La versión de esta app (la pone el empaquetado; en desarrollo, «0»). */
    val version: String get() = System.getProperty("familia.version")?.takeIf { it.isNotBlank() } ?: "0"

    private fun partes(v: String) = v.split('.').map { it.toIntOrNull() ?: 0 }
    fun mayor(a: String, b: String): Boolean {
        val x = partes(a); val y = partes(b)
        for (i in 0 until maxOf(x.size, y.size)) { val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }; if (p != q) return p > q }
        return false
    }

    /** Lo que vale para este sistema: `<App>-<versión>-mac.dmg` o `<App>-<versión>-linux.deb`. */
    fun patron(app: String, sistema: Sitio.Sistema = Sitio.sistema): Regex {
        val nombre = Regex.escape(app.replaceFirstChar { it.uppercase() })
        val fin = if (sistema == Sitio.Sistema.MAC) "mac\\.dmg" else "linux\\.deb"
        return Regex("^$nombre-(\\d+(?:\\.\\d+)*)-$fin$")
    }

    /** La versión más nueva que hay en tu kDrive para esta app y este sistema, si es más nueva que la de ahora. Bloquea (red). */
    fun busca(app: String, kdrive: KDrive = KDrive()): Nueva? {
        if (!kdrive.activo) return null
        val p = patron(app)
        return kdrive.versiones().mapNotNull { a -> p.find(a.nombre)?.groupValues?.get(1)?.let { Nueva(it, a) } }
            .filter { mayor(it.version, version) }.maxWithOrNull { a, b -> if (mayor(a.version, b.version)) 1 else if (mayor(b.version, a.version)) -1 else 0 }
    }

    /** Baja la versión nueva a Descargas y la abre con el sistema (el instalador de Linux, o el .dmg en el Mac). Bloquea (red). */
    fun instala(n: Nueva, kdrive: KDrive = KDrive(), progreso: (Long, Long) -> Unit = { _, _ -> }): File {
        val casa = File(System.getProperty("user.home"))
        val descargas = listOf("Descargas", "Downloads").map { File(casa, it) }.firstOrNull { it.isDirectory } ?: casa
        val destino = File(descargas, n.archivo.nombre)
        kdrive.baja(n.archivo.id, destino, progreso)
        Sitio.abre(destino.absolutePath)
        return destino
    }
}
