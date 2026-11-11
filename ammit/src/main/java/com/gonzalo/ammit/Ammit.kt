package com.gonzalo.ammit

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Una app de la familia: su paquete, su nombre y su acento. */
data class DeLaFamilia(val paquete: String, val nombre: String, val acento: Long)

/**
 * Ammit, la devoradora del juicio de Osiris. Encendida, la pantalla de inicio enseña tus apps con la familia en color y las demás
 * encadenadas, y el [Vigia] cierra con [JuicioActivity] cualquier app que no sea de la familia. Apagada, la pantalla de inicio pasa a
 * la de siempre ([inicioNormal]) y no se vigila nada.
 */
object Ammit {
    const val PROPIO = "com.gonzalo.ammit"

    /** La familia: las seis que pasan el juicio. */
    val FAMILIA = listOf(
        DeLaFamilia("com.gonzalo.gamerate", "Jonsu", 0xFF8E5A78),
        DeLaFamilia("com.gonzalo.hiedra", "Bastet", 0xFF7A8A3A),
        DeLaFamilia("com.gonzalo.marfil", "Seshat", 0xFFB0791E),
        DeLaFamilia("com.gonzalo.cobalto", "Thot", 0xFF5A7896),
        DeLaFamilia("com.gonzalo.mostaza", "Hathor", 0xFFB56576),
        DeLaFamilia("com.gonzalo.cobre", "Nut", 0xFF5D1414),
    )
    val PAQUETES_FAMILIA = FAMILIA.map { it.paquete }.toSet()

    /** El acento de Ammit: verde fayenza (el de los amuletos). */
    const val ACENTO = 0xFF2F6B63
    /** El mismo, aclarado para leerse sobre negro. */
    const val ACENTO_NOCHE = 0xFF8FC3BA

    /**
     * Lo que siempre pasa, aunque no sea de la familia: Ammit, la barra de arriba (para poder apagarla) y lo de Android sin lo que las
     * apps de la familia no funcionan (los permisos que piden y el instalador de sus actualizaciones). El teclado se añade aparte ([pasa]).
     */
    val SIEMPRE = setOf(
        PROPIO, "com.android.systemui", "android",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
    )

    /** ¿Pasa el juicio? (la familia, lo de [SIEMPRE] y los teclados que tengas). */
    fun pasa(paquete: String, teclados: Set<String> = emptySet()): Boolean =
        paquete in PAQUETES_FAMILIA || paquete in SIEMPRE || paquete in teclados

    private fun prefs(c: Context) = c.getSharedPreferences("ammit", Context.MODE_PRIVATE)

    /** Encendida (se ve en la pantalla de inicio y en el botón al momento). */
    var encendida by mutableStateOf(false)
        private set

    fun cargar(c: Context) { encendida = prefs(c).getBoolean("on", false) }

    fun encender(c: Context, on: Boolean) {
        val antes = prefs(c).getBoolean("on", false)
        encendida = on
        // La próxima vez que se vea la pantalla de inicio, la animación de las cadenas: cerrándose al encender, cayéndose al apagar
        prefs(c).edit().putBoolean("on", on).apply { if (antes != on) putString("animar", if (on) "entrar" else "salir") }.apply()
    }

    /** La animación pendiente («entrar» o «salir»), una sola vez: se borra al leerla. */
    fun tomaAnimacion(c: Context): String? {
        val p = prefs(c)
        val a = p.getString("animar", null) ?: return null
        p.edit().remove("animar").apply()
        return a
    }

    /** La pantalla de inicio de siempre (la de Samsung u otra), a la que se pasa con Ammit apagada. */
    fun inicioNormal(c: Context): ComponentName? {
        val guardada = prefs(c).getString("inicio_normal", null)?.let { ComponentName.unflattenFromString(it) }
        val otras = otrosInicios(c)
        return guardada?.takeIf { g -> otras.any { it.first == g } } ?: otras.firstOrNull()?.first
    }

    fun ponInicioNormal(c: Context, cn: ComponentName) { prefs(c).edit().putString("inicio_normal", cn.flattenToString()).apply() }

    /** Las otras pantallas de inicio que hay en la tablet (nombre visible incluido), con la del fabricante primero. */
    fun otrosInicios(c: Context): List<Pair<ComponentName, String>> {
        val pm = c.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(q, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != PROPIO && it.activityInfo.packageName != "com.android.settings" }
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) to it.loadLabel(pm).toString() }
            .sortedBy { (cn, _) -> if (cn.packageName.contains("sec.android") || cn.packageName.contains("launcher")) 0 else 1 }
    }

    /** ¿Es Ammit la pantalla de inicio de la tablet? */
    fun esInicio(c: Context): Boolean {
        val rm = c.getSystemService(android.app.role.RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) return rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
        val r = c.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.packageName == PROPIO
    }

    /** ¿Está activado el vigilante (el servicio de accesibilidad)? */
    fun vigiaActivo(c: Context): Boolean {
        val am = c.getSystemService(AccessibilityManager::class.java) ?: return false
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == PROPIO }
    }

    /** Los teclados que tienes activados (escribir en las apps de la familia no debe saltar el juicio). */
    fun teclados(c: Context): Set<String> = runCatching {
        Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS).orEmpty()
            .split(':').mapNotNull { it.substringBefore('/').takeIf { p -> p.isNotBlank() } }.toSet()
    }.getOrDefault(emptySet())

    // ------------------------------------------------------------------ cómo has colocado la pantalla de inicio

    /** El orden de tus apps (claves «paquete/actividad»): las que no estén, al final, en orden alfabético. */
    fun orden(c: Context): List<String> = runCatching {
        JSONArray(prefs(c).getString("orden", "[]")).let { a -> List(a.length()) { a.getString(it) } }
    }.getOrDefault(emptyList())

    fun ponOrden(c: Context, l: List<String>) { prefs(c).edit().putString("orden", JSONArray(l).toString()).apply() }

    /** Los widgets puestos: su id y su alto (en dp). */
    data class Widget(val id: Int, val alto: Int)

    fun widgets(c: Context): List<Widget> = runCatching {
        JSONArray(prefs(c).getString("widgets", "[]")).let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { Widget(it.getInt("id"), it.optInt("alto", 170)) } }
        }
    }.getOrDefault(emptyList())

    fun ponWidgets(c: Context, l: List<Widget>) {
        prefs(c).edit().putString("widgets", JSONArray(l.map { JSONObject().put("id", it.id).put("alto", it.alto) }).toString()).apply()
    }

    /** Coloca [lista] (claves) según [orden]: las colocadas, en su sitio; las nuevas, detrás y por nombre. */
    fun <T> ordena(lista: List<T>, clave: (T) -> String, nombre: (T) -> String, orden: List<String>): List<T> {
        val pos = orden.withIndex().associate { (i, k) -> k to i }
        val (puestas, nuevas) = lista.partition { clave(it) in pos }
        return puestas.sortedBy { pos[clave(it)] } + nuevas.sortedBy { nombre(it).lowercase() }
    }
}
