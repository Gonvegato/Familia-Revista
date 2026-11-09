package com.gonzalo.shai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.nucleo.Config
import com.gonzalo.familia.nucleo.DiscoArchivos
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.nucleo.RedJava
import java.io.File

/**
 * MIGRACIÓN ÚNICA (quitar en una versión futura, cuando ya no quede ningún móvil con la versión de antes).
 *
 * Hasta la 0.5.1 esta app guardaba tus carreras en una cuenta en la nube, y tu clave de la API y el token de kDrive en esa misma
 * cuenta. Desde la 0.6.0 todo vive solo en este móvil. La primera vez que se abre la versión nueva, se lee lo que dejó aquí la de antes
 * (su copia local de la cuenta, en `files/familia`, y su caja cifrada, `files/familia_seguro`), se baja lo último si hay red y
 * se copia: las carreras, lo que pasa en ellas, el gasto y el tope a `carreras.json`; la clave de la API y el token de kDrive, a la
 * caja cifrada de esta app ([Secretos]). Después no se vuelve a tocar la cuenta. Sin sesión de antes, no hay nada que traer.
 */
object Migracion {
    private const val PREFS = "fcfeed_migracion"
    /** Lo que se trajo («He traído 2 carreras de tu cuenta»), para enseñarlo una vez. */
    var aviso by mutableStateOf("")
    /** En las pruebas: la cuenta de antes (en vez de leerla de los archivos). */
    @Volatile var fuente: ((Context) -> Familia?)? = null

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun hecha(c: Context) = p(c).getBoolean("hecha", false)

    fun olvidaAviso(c: Context) { aviso = ""; p(c).edit().putString("aviso", "").apply() }
    fun cargaAviso(c: Context) { aviso = p(c).getString("aviso", "").orEmpty() }

    private fun deAntes(c: Context): Familia? {
        fuente?.let { return it(c) }
        val dir = File(c.filesDir, "familia")
        if (!dir.isDirectory) return null
        val seguro = Caja.abre(File(c.filesDir, "familia_seguro"), "familia_seguro", crear = false) ?: return null
        return runCatching { Familia(Config(), RedJava(), DiscoArchivos(dir), seguro) }.getOrNull()
    }

    /** El token de kDrive de antes podía estar solo en este móvil, sin cifrar: pasa a la caja. */
    private fun traeNubeLocal(c: Context) {
        val n = c.getSharedPreferences("nube", Context.MODE_PRIVATE)
        val t = n.getString("token", "").orEmpty(); val d = n.getString("drive", "").orEmpty()
        if (n.getBoolean("probando", false) || t.isBlank() || d.isBlank()) return
        if (!KDrive.on(c)) KDrive.ponLocal(c, t, d)
        n.edit().remove("token").remove("drive").apply()
    }

    /** Hace la migración si no está hecha (bloquea: llamar fuera del hilo principal). Devuelve cuántas carreras se trajeron. */
    @Synchronized
    fun haz(c: Context): Int {
        if (hecha(c)) return 0
        val f = deAntes(c)
        if (f == null || !f.sesion.dentro) { traeNubeLocal(c); p(c).edit().putBoolean("hecha", true).apply(); return 0 }
        // Lo último de la cuenta (si no hay red, lo que ya tenía este móvil)
        val col = runCatching { f.coleccion(Carreras.COLECCION) }.getOrNull()
        runCatching { f.sincroniza() }
        var carreras = 0
        col?.vivos()?.forEach { n ->
            if (n.campos.isEmpty() || Carreras.real.nodo(n.ref) != null) return@forEach
            Carreras.real.pon(n.ref, n.campos)
            if (n.ref.startsWith("u")) carreras++
        }
        var clave = false
        f.clave(ClaveApi.CLAVE).trim().takeIf { it.isNotBlank() && Secretos.lee(ClaveApi.CLAVE).isBlank() }?.let { Secretos.pon(ClaveApi.CLAVE, it); clave = true }
        val t = f.clave("kdrive_token").trim(); val d = f.clave("kdrive_id").trim()
        if (t.isNotBlank() && d.isNotBlank() && !KDrive.on(c)) KDrive.ponLocal(c, t, d)
        traeNubeLocal(c)
        val texto = buildList {
            if (carreras > 0) add("He traído ${if (carreras == 1) "1 carrera" else "$carreras carreras"} de tu cuenta")
            if (clave) add("tu clave de la API (ahora, cifrada en este móvil)")
        }.joinToString(" y ").let { if (it.isBlank()) "" else "$it." }
        p(c).edit().putBoolean("hecha", true).putString("aviso", texto).apply()
        aviso = texto
        Carreras.avisa()
        // La cuenta de antes ya no se usa: sus trabajos de fondo, fuera
        runCatching { androidx.work.WorkManager.getInstance(c).apply { cancelUniqueWork("familia_subida"); cancelUniqueWork("familia_periodica") } }
        return carreras
    }
}
