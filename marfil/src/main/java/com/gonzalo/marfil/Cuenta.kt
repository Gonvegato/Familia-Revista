package com.gonzalo.marfil

import android.content.Context
import android.os.Build
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Red
import com.gonzalo.familia.nucleo.RedJava
import com.gonzalo.familia.nucleo.Sesion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * La cuenta de la familia en Marfil: la de siempre (usuario y contraseña), pero ahora una sola para las cuatro apps
 * ([LaFamilia]): si ya has entrado en Azabache (o en cualquier otra de la familia), Marfil entra sola.
 * Lo de Marfil vive en las colecciones de la cuenta (`nube/ESQUEMA.md`); [db] queda para leer lo de antes (la migración).
 */
object Cuenta {
    class Fail(msg: String) : Exception(msg)

    private lateinit var p: android.content.SharedPreferences
    private val red = RedJava()

    /** Este dispositivo (el mismo id de siempre: con él se apuntan su tiempo leído y sus estados de lectura). */
    var dispositivo = ""; private set

    private val sesion: Sesion get() = LaFamilia.familia.sesion

    /** ¿Hay cuenta? (Leído desde Compose, repinta al entrar o salir.) */
    val vinculada: Boolean get() { LaFamilia.cambios; return runCatching { sesion.dentro }.getOrDefault(false) }
    val uid: String get() = runCatching { sesion.uid }.getOrDefault("")
    val usuario: String get() { LaFamilia.cambios; return runCatching { sesion.usuario }.getOrDefault("") }

    fun cargar(c: Context) {
        LaFamilia.inicia(c)
        p = c.getSharedPreferences("marfil_cuenta", Context.MODE_PRIVATE)
        dispositivo = p.getString("dispositivo", null) ?: ("marfil-" + UUID.randomUUID().toString().take(8)).also { p.edit().putString("dispositivo", it).apply() }
    }

    val nombreDispositivo: String get() = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"

    fun normUser(s: String) = s.trim().lowercase()

    /** Entra en tu cuenta (la de la familia). Devuelve el uid. */
    suspend fun entrar(user: String, pass: String): String = withContext(Dispatchers.IO) {
        try { LaFamilia.entrar(user, pass) } catch (e: Sesion.Error) { throw Fail(e.message ?: "No se ha podido entrar.") }
        uid
    }

    fun salir() = LaFamilia.salir()

    /** Una petición a la base de datos con tu sesión (para leer lo de antes). Devuelve el texto, o null si no hay nada. */
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

    /** La dirección (con el pase) para escuchar un sitio de tu cuenta en directo. */
    fun urlFlujo(path: String, renovar: Boolean): String? {
        if (!vinculada) return null
        if (renovar) sesion.paseCaducado()
        return sesion.url(path, conPase = true)
    }

    /** Donde vivía lo de Marfil antes de la familia (solo para la migración). */
    val raiz: String get() = "/users/$uid/marfil"
}
