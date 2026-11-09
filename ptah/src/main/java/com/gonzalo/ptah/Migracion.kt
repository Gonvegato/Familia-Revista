package com.gonzalo.ptah

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.nucleo.Config
import com.gonzalo.familia.nucleo.DiscoArchivos
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.nucleo.RedJava
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoTexto
import java.io.File

/**
 * MIGRACIÓN ÚNICA (quitar en una versión futura, cuando ya no quede ningún móvil con la versión de antes).
 *
 * Hasta la 0.5.0 tus diseños (y los colores recientes y «actualizar sola») viajaban también con una cuenta en la nube, y el token de
 * kDrive estaba en esa cuenta. Desde la 0.6.0 todo vive solo en este móvil. La primera vez que se abre la versión nueva, se lee lo que
 * dejó aquí la de antes (su copia local de la cuenta, `files/familia`, y su caja cifrada, `files/familia_seguro`), se baja lo último si
 * hay red y se copia lo que este móvil no tuviera: los diseños (gana el más nuevo), los colores recientes y el token de kDrive (a la caja
 * cifrada de esta app). Después no se vuelve a tocar la cuenta. Sin sesión de antes, no hay nada que traer.
 */
object Migracion {
    private const val PREFS = "widgets_migracion"
    var aviso by mutableStateOf("")
    /** En las pruebas: la cuenta de antes. */
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

    private fun traeNubeLocal(c: Context) {
        val n = c.getSharedPreferences("nube", Context.MODE_PRIVATE)
        val t = n.getString("token", "").orEmpty(); val d = n.getString("drive", "").orEmpty()
        if (n.getBoolean("probando", false) || t.isBlank() || d.isBlank()) return
        if (!KDrive.on(c)) KDrive.ponLocal(c, t, d)
        n.edit().remove("token").remove("drive").apply()
    }

    /** Hace la migración si no está hecha (bloquea: fuera del hilo principal). Devuelve cuántos diseños se trajeron. */
    @Synchronized
    fun haz(c: Context): Int {
        if (hecha(c)) return 0
        val f = deAntes(c)
        if (f == null || !f.sesion.dentro) { traeNubeLocal(c); p(c).edit().putBoolean("hecha", true).apply(); return 0 }
        val col = runCatching { f.coleccion("ajustes") }.getOrNull()
        runCatching { f.sincroniza() }
        var disenos = 0
        // Los diseños: un campo por diseño en `ajustes/ptah_disenos`; si este móvil ya lo tiene, gana el más nuevo
        col?.nodo(Disenos.GRUPO.nodo)?.takeIf { !it.borrado }?.campos?.forEach { (k, v) ->
            if (!k.startsWith("d") || Disenos.GRUPO.tipo(k) == null) return@forEach
            val cuenta = v.comoTexto().takeIf { it.isNotBlank() } ?: return@forEach
            val p = EspejoCuenta.base(c, Disenos.GRUPO)
            val aqui = p.getString(k, null)
            if (aqui == null || Disenos.masNuevo(cuenta, aqui)) { p.edit().putString(k, cuenta).apply(); if (aqui == null) disenos++ }
        }
        // Los colores recientes (se juntan) y «actualizar sola»
        col?.nodo(Recientes.GRUPO.nodo)?.takeIf { !it.borrado }?.campos?.get("recientes")?.comoTexto()?.takeIf { it.isNotBlank() }?.let { r ->
            val p = EspejoCuenta.base(c, Recientes.GRUPO)
            val junto = (p.getString("recientes", "").orEmpty().split(',') + r.split(',')).filter { it.isNotBlank() }.distinct().take(16)
            p.edit().putString("recientes", junto.joinToString(",")).apply()
        }
        col?.nodo("ptah_actualizador")?.takeIf { !it.borrado }?.campos?.get("on")?.let { v ->
            val p = c.getSharedPreferences("ptah_actualizador", Context.MODE_PRIVATE)
            if (!p.contains("on")) p.edit().putBoolean("on", v.comoBool()).apply()
        }
        val t = f.clave("kdrive_token").trim(); val d = f.clave("kdrive_id").trim()
        if (t.isNotBlank() && d.isNotBlank() && !KDrive.on(c)) KDrive.ponLocal(c, t, d)
        traeNubeLocal(c)
        val texto = if (disenos > 0) "He traído ${if (disenos == 1) "1 diseño" else "$disenos diseños"} de tu cuenta." else ""
        p(c).edit().putBoolean("hecha", true).putString("aviso", texto).apply()
        aviso = texto
        EspejoCuenta.avisa()
        runCatching { Widgets.pedir(c) }
        runCatching { androidx.work.WorkManager.getInstance(c).apply { cancelUniqueWork("familia_subida"); cancelUniqueWork("familia_periodica") } }
        return disenos
    }
}
