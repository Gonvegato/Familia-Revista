package com.gonzalo.cobalto

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Red
import com.gonzalo.familia.nucleo.RedJava
import com.gonzalo.familia.nucleo.Sesion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.util.UUID

/**
 * La cuenta de la familia en Cobalto: la de siempre (usuario y contraseña), una sola para las cuatro apps ([LaFamilia]): si ya has
 * entrado en Azabache (o en cualquier otra de la familia), Cobalto entra sola. Lo de Cobalto vive en las colecciones de la cuenta
 * (`nube/ESQUEMA.md`); [db] queda para lo que no es de las colecciones (los documentos de Escribir, compartidos con Atrio del PC) y la migración.
 */
object Cuenta {
    class Fail(msg: String) : Exception(msg)

    private lateinit var p: android.content.SharedPreferences
    private val red = RedJava()

    /** Este dispositivo (el mismo id de siempre: con él se apuntan sus palabras). */
    var dispositivo = ""; private set

    private val sesion: Sesion get() = LaFamilia.familia.sesion

    val vinculada: Boolean get() { LaFamilia.cambios; return runCatching { sesion.dentro }.getOrDefault(false) }
    val uid: String get() = runCatching { sesion.uid }.getOrDefault("")
    val usuario: String get() { LaFamilia.cambios; return runCatching { sesion.usuario }.getOrDefault("") }

    /** Tu pareja: su usuario (o su nombre) y su uid, para compartirle capítulos y leer lo que te comparte. La de la cuenta ([sincronizaPareja]). */
    var parejaUsuario by mutableStateOf("")
        private set
    var parejaUid by mutableStateOf("")
        private set
    /**
     * Este móvil tiene una pareja de antes (hasta la 0.4.1 se guardaba solo aquí) y tu cuenta no tiene ninguna. No se pone sola en la
     * cuenta: eso deja a esa persona leer tu cuenta. Ajustes te lo enseña y lo decides tú ([subeParejaDeAqui] o [olvidaParejaDeAqui]).
     */
    var parejaSoloAqui by mutableStateOf(false)
        private set

    /**
     * Busca a tu pareja por su usuario (en /usernames) y la pone en tu cuenta: `/users/<tú>/partner` = su uid, la misma pareja de toda la
     * familia (la que pone Azabache, y la que deja a tu pareja leer lo que le compartes). Así está en todos tus dispositivos.
     */
    suspend fun ponPareja(user: String) = withContext(Dispatchers.IO) {
        val u = normUser(user)
        if (u.isBlank()) throw Fail("Escribe el usuario de tu pareja.")
        if (!vinculada) throw Fail("Primero entra en tu cuenta.")
        val raw = db("GET", "/usernames/$u", null) ?: throw Fail("No hay nadie con el usuario «$u».")
        val pu = (JSONTokener(raw).nextValue() as? String).orEmpty().ifBlank { throw Fail("No hay nadie con el usuario «$u».") }
        if (pu == uid) throw Fail("Ese usuario eres tú.")
        db("PUT", "/users/$uid/partner", org.json.JSONObject.quote(pu))
        guardaPareja(u, pu)
    }

    /** Quita a tu pareja de tu cuenta (en todos tus dispositivos y en las demás apps de la familia): dejas de compartirle y de ver lo suyo. */
    suspend fun quitarPareja() = withContext(Dispatchers.IO) {
        // (nunca null: vacío es «sin pareja», como lo lee Azabache)
        if (vinculada) db("PUT", "/users/$uid/partner", "\"\"")
        olvidaPareja()
    }

    /** Olvida en este dispositivo a tu pareja (al salir de la cuenta o cambiar de cuenta); en la cuenta no se toca. */
    fun olvidaPareja() { parejaUsuario = ""; parejaUid = ""; if (::p.isInitialized) p.edit().remove("parejaUsuario").remove("parejaUid").apply() }

    private fun guardaPareja(usuario: String, pu: String) {
        parejaUsuario = usuario; parejaUid = pu
        p.edit().putString("parejaUsuario", usuario).putString("parejaUid", pu).putString(PAREJA_EN_CUENTA, uid).apply()
    }

    private const val PAREJA_EN_CUENTA = "parejaEnCuenta"

    /**
     * Tu pareja, la de la cuenta (`/users/<tú>/partner`), al día en este dispositivo (bloquea: llámalo fuera del hilo principal). Hasta
     * Cobalto 0.4.1 se guardaba solo en este móvil: si la cuenta aún no tenía ninguna, la de aquí pasa a ella, una vez por cuenta. Después
     * manda la cuenta: si se quitó o se cambió en otro sitio, aquí también.
     */
    fun sincronizaPareja() {
        if (!vinculada || !::p.isInitialized) return
        val enCuenta = (db("GET", "/users/$uid/partner", null)?.let { runCatching { JSONTokener(it).nextValue() as? String }.getOrNull() }).orEmpty()
        val yaPaso = p.getString(PAREJA_EN_CUENTA, "") == uid
        when {
            enCuenta.isNotBlank() && enCuenta != uid -> {
                if (enCuenta != parejaUid || parejaUsuario.isBlank()) guardaPareja(nombreDe(enCuenta).ifBlank { if (enCuenta == parejaUid) parejaUsuario else "" }, enCuenta)
                else p.edit().putString(PAREJA_EN_CUENTA, uid).apply()
            }
            // (la de antes de este móvil: se queda aquí, sin compartir nada, hasta que decidas)
            !yaPaso && parejaUid.isNotBlank() -> { parejaSoloAqui = true; return }
            else -> { if (parejaUid.isNotBlank()) olvidaPareja(); p.edit().putString(PAREJA_EN_CUENTA, uid).apply() }
        }
        parejaSoloAqui = false
    }

    /** «Ponerla en mi cuenta»: la pareja de antes de este móvil pasa a tu cuenta (desde ahí puede leerla, como la pareja de Azabache). */
    fun subeParejaDeAqui() {
        if (!vinculada || parejaUid.isBlank()) return
        db("PUT", "/users/$uid/partner", org.json.JSONObject.quote(parejaUid))
        guardaPareja(parejaUsuario, parejaUid); parejaSoloAqui = false
    }

    /** «Olvidarla»: la de este móvil se olvida y tu cuenta sigue sin pareja. */
    fun olvidaParejaDeAqui() {
        olvidaPareja(); if (::p.isInitialized) p.edit().putString(PAREJA_EN_CUENTA, uid).apply(); parejaSoloAqui = false
    }

    /** El nombre de alguien (el que puso en Azabache, o su usuario), si su cuenta te deja leerlo; "" si no. */
    private fun nombreDe(otro: String): String = listOf("name", "user").firstNotNullOfOrNull { c ->
        runCatching { db("GET", "/users/$otro/$c", null)?.let { JSONTokener(it).nextValue() as? String } }.getOrNull()?.takeIf { it.isNotBlank() }
    }.orEmpty()

    fun cargar(c: Context) {
        LaFamilia.inicia(c)
        p = c.getSharedPreferences("cobalto_cuenta", Context.MODE_PRIVATE)
        parejaUsuario = p.getString("parejaUsuario", "").orEmpty(); parejaUid = p.getString("parejaUid", "").orEmpty()
        dispositivo = p.getString("dispositivo", null) ?: ("cobalto-" + UUID.randomUUID().toString().take(8)).also { p.edit().putString("dispositivo", it).apply() }
    }

    val nombreDispositivo: String get() = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"

    fun normUser(s: String) = s.trim().lowercase()

    /** Entra en tu cuenta (la de la familia). Devuelve el uid. */
    suspend fun entrar(user: String, pass: String): String = withContext(Dispatchers.IO) {
        val previo = uid
        try { LaFamilia.entrar(user, pass) } catch (e: Sesion.Error) { throw Fail(e.message ?: "No se ha podido entrar.") }
        if (previo.isNotBlank() && previo != uid) olvidaPareja()
        runCatching { sincronizaPareja() }
        uid
    }

    suspend fun entrar(@Suppress("UNUSED_PARAMETER") c: Context, user: String, pass: String): String = entrar(user, pass)

    fun salir() { LaFamilia.salir(); olvidaPareja() }

    /** Una petición a la base de datos con tu sesión. Devuelve el texto, o null si no hay nada. */
    fun db(method: String, path: String, body: String?, query: String = ""): String? {
        val q = query.removePrefix("&")
        fun go(): Red.Respuesta = try { red.pedir(method, sesion.url(path, q, conPase = true), body) } catch (e: Red.SinRed) { throw java.io.IOException(e.message) }
        var r = go()
        if (r.codigo == 401 && !r.cuerpo.contains("Permission denied", true)) { sesion.paseCaducado(); r = go() }
        if (r.codigo == 401 || r.codigo == 403) throw Fail("La cuenta no deja hacer eso (reglas de la base de datos).")
        if (r.codigo !in 200..299) throw Fail("La cuenta respondió ${r.codigo}.")
        val t = r.cuerpo.trim()
        return if (t.isEmpty() || t == "null") null else t
    }

    /** La dirección (con el pase) para escuchar un sitio de tu cuenta en directo (el PC de Atrio). */
    fun urlFlujo(path: String, renovar: Boolean): String? {
        if (!vinculada) return null
        if (renovar) sesion.paseCaducado()
        return sesion.url(path, conPase = true)
    }

    /** Donde vivía lo de Cobalto antes de la familia (la migración). */
    val raiz: String get() = "/users/$uid/cobalto"
}
