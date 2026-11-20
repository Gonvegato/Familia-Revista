package com.gonzalo.familia.pc

import com.gonzalo.familia.nucleo.GoogleDrive
import java.io.File
import java.io.InputStream

/**
 * Tu nube en el ordenador: **Google Drive** (principal) y **kDrive** (respaldo durante la transición, para no perder nada que aún esté allí).
 * Las credenciales viven una sola vez en `/claves/<uid>` (puestas desde Jonsu).
 */
object Nube {
    class Archivo(val id: String, val nombre: String, val carpeta: Boolean, val tamano: Long, val deGoogleDrive: Boolean)

    @Volatile var gdrivePrueba: GoogleDrive? = null
    @Volatile var kdrivePrueba: KDrive? = null

    fun gdrive(): GoogleDrive = gdrivePrueba ?: GoogleDrive(
        clientId = { LaFamilia.clave(GoogleDrive.CLAVE_CLIENT_ID) },
        clientSecret = { LaFamilia.clave(GoogleDrive.CLAVE_CLIENT_SECRET) },
        refreshToken = { LaFamilia.clave(GoogleDrive.CLAVE_REFRESH_TOKEN) },
        carpetaConfig = { LaFamilia.clave(GoogleDrive.CLAVE_CARPETA) },
    )

    fun kdrive(): KDrive = kdrivePrueba ?: KDrive()

    val gdriveActivo: Boolean get() = runCatching { gdrive().activo }.getOrDefault(false)
    val kdriveActivo: Boolean get() = runCatching { kdrive().activo }.getOrDefault(false)
    val activo: Boolean get() = gdriveActivo || kdriveActivo
    val nombre: String get() = if (gdriveActivo) "Google Drive" else "kDrive"

    /** Sube un archivo a Google Drive (si está configurado) o a kDrive. Devuelve (id, esGoogleDrive). */
    fun sube(ruta: String, nombre: String, tamano: Long, progreso: (Long) -> Unit = {}, entrada: () -> InputStream): Pair<String, Boolean> {
        val g = gdrive()
        if (g.activo) return g.sube(ruta, nombre, tamano, progreso, entrada) to true
        val k = kdrive()
        if (k.activo) return k.sube(ruta, nombre, tamano, progreso, entrada).toString() to false
        throw GoogleDrive.Fallo("No hay ninguna nube configurada (pon tu Google Drive en Polaris → Ajustes → Servicios)")
    }

    /** Baja un archivo: de Google Drive si tiene [gdId], o de kDrive si solo tiene [kdId]. */
    fun baja(gdId: String, kdId: Long, destino: File, progreso: (Long, Long) -> Unit = { _, _ -> }) {
        if (gdId.isNotBlank()) {
            val g = gdrive()
            if (g.activo) { g.baja(gdId, destino, progreso); return }
        }
        if (kdId > 0L) {
            val k = kdrive()
            if (k.activo) { k.baja(kdId, destino, progreso); return }
        }
        throw GoogleDrive.Fallo("No se puede bajar el archivo: configura tu Google Drive en Polaris")
    }

    /** Lista los archivos de `versiones` (Google Drive primero; si no está o está vacío, kDrive). */
    fun versiones(): List<Archivo> {
        val g = gdrive()
        if (g.activo) {
            val lista = runCatching { g.versiones().map { Archivo(it.id, it.nombre, it.carpeta, it.tamano, true) } }.getOrDefault(emptyList())
            if (lista.isNotEmpty()) return lista
        }
        val k = kdrive()
        if (k.activo) return k.versiones().map { Archivo(it.id.toString(), it.nombre, it.carpeta, it.tamano, false) }
        return emptyList()
    }
}
