package com.gonzalo.cobre

import android.content.Context
import android.os.Build
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Sesion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * La cuenta de la familia en Cobre: la de siempre (usuario y contraseña), una sola para todas las apps ([LaFamilia]): si ya has
 * entrado en Azabache (o en cualquier otra de la familia), Cobre entra sola. Lo de Cobre vive en las colecciones de la cuenta
 * (`nube/ESQUEMA.md`: `cobre_favoritos`, `cobre_grupos`, `cobre_progreso`, `cobre_estado`, `ajustes/cobre*`) y tus listas, en `/claves` ([Listas]).
 */
object Cuenta {
    class Fail(msg: String) : Exception(msg)

    private var p: android.content.SharedPreferences? = null

    /** Este dispositivo (con él se apunta el tiempo que ves aquí: `tiempo/<título>~<dispositivo>`). Es de este dispositivo: no viaja. */
    var dispositivo = ""; private set

    private val sesion: Sesion get() = LaFamilia.familia.sesion

    /** ¿Hay cuenta? (Leído desde Compose, repinta al entrar o salir.) */
    val vinculada: Boolean get() { LaFamilia.cambios; return runCatching { sesion.dentro }.getOrDefault(false) }
    val uid: String get() = runCatching { sesion.uid }.getOrDefault("")
    val usuario: String get() { LaFamilia.cambios; return runCatching { sesion.usuario }.getOrDefault("") }

    fun cargar(c: Context) {
        LaFamilia.inicia(c)
        if (p != null) return
        val pr = c.getSharedPreferences("cobre_cuenta", Context.MODE_PRIVATE)
        p = pr
        dispositivo = pr.getString("dispositivo", null) ?: ("cobre-" + UUID.randomUUID().toString().take(8)).also { pr.edit().putString("dispositivo", it).apply() }
    }

    val nombreDispositivo: String get() = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"

    /** Entra en tu cuenta (la de la familia). Devuelve el uid. */
    suspend fun entrar(user: String, pass: String): String = withContext(Dispatchers.IO) {
        try { LaFamilia.entrar(user, pass) } catch (e: Sesion.Error) { throw Fail(e.message ?: "No se ha podido entrar.") }
        uid
    }

    fun salir() = LaFamilia.salir()
}
