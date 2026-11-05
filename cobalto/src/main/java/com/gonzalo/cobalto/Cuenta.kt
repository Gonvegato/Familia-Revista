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

    /** Tu pareja: su usuario y su uid, para compartirle capítulos y leer lo que te comparte. */
    var parejaUsuario by mutableStateOf("")
        private set
    var parejaUid by mutableStateOf("")
        private set

    /** Busca a tu pareja por su usuario (en /usernames) y la guarda. */
    suspend fun ponPareja(user: String) = withContext(Dispatchers.IO) {
        val u = normUser(user)
        if (u.isBlank()) throw Fail("Escribe el usuario de tu pareja.")
        if (!vinculada) throw Fail("Primero entra en tu cuenta.")
        val raw = db("GET", "/usernames/$u", null) ?: throw Fail("No hay nadie con el usuario «$u».")
        val pu = (JSONTokener(raw).nextValue() as? String).orEmpty().ifBlank { throw Fail("No hay nadie con el usuario «$u».") }
        if (pu == uid) throw Fail("Ese usuario eres tú.")
        parejaUsuario = u; parejaUid = pu
        p.edit().putString("parejaUsuario", u).putString("parejaUid", pu).apply()
    }

    fun quitarPareja() { parejaUsuario = ""; parejaUid = ""; if (::p.isInitialized) p.edit().remove("parejaUsuario").remove("parejaUid").apply() }

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
        if (previo.isNotBlank() && previo != uid) quitarPareja()
        uid
    }

    suspend fun entrar(@Suppress("UNUSED_PARAMETER") c: Context, user: String, pass: String): String = entrar(user, pass)

    fun salir() { LaFamilia.salir(); quitarPareja() }

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
