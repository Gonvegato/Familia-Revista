package com.gonzalo.familia.nucleo

import java.net.URLEncoder

/** Dónde está la cuenta. En las pruebas, el emulador de Firebase. */
data class Config(
    val baseDatos: String = "https://app-ratear-default-rtdb.europe-west1.firebasedatabase.app",
    val baseAuth: String = "https://identitytoolkit.googleapis.com",
    val baseToken: String = "https://securetoken.googleapis.com",
    /** Parámetros que se añaden a cada dirección de la base de datos (el emulador pide `ns=<proyecto>`). */
    val extra: String = "",
    /** La clave web del proyecto (pública). Vacía: se lee de `/config/webKey`. */
    val claveWeb: String = "",
    /** Firebase pide un correo: el usuario se convierte en uno que no existe (el de siempre de la familia). */
    val dominio: String = "ronron.example.com",
)

/**
 * La sesión de la cuenta (Firebase Authentication por su API REST). Se guarda solo el pase que se renueva (`refresh token`),
 * nunca la contraseña: eso lo hace, si se quiere, la plataforma (en Android, cifrado y solo entre las apps de la familia).
 */
class Sesion(private val cfg: Config, private val red: Red, private val seguro: Disco, private val reloj: Reloj) {
    class Error(msg: String, val sinRed: Boolean = false) : Exception(msg)

    @Volatile var uid = ""; private set
    @Volatile var usuario = ""; private set
    @Volatile private var pase = ""
    @Volatile private var refresco = ""
    @Volatile private var caduca = 0L
    @Volatile private var claveWeb = cfg.claveWeb

    val dentro: Boolean get() = uid.isNotBlank() && refresco.isNotBlank()

    init { carga() }

    private fun carga() {
        val m = seguro.leeTexto(ARCHIVO)?.let { runCatching { Json.lee(it) }.getOrNull() }.comoMapa()
        uid = m["uid"].comoTexto(); usuario = m["usuario"].comoTexto(); refresco = m["refresco"].comoTexto()
        pase = m["pase"].comoTexto(); caduca = m["caduca"].comoLong()
        if (claveWeb.isBlank()) claveWeb = m["claveWeb"].comoTexto()
    }

    private fun guarda() {
        seguro.escribeTexto(ARCHIVO, Json.escribe(mapOf("uid" to uid, "usuario" to usuario, "refresco" to refresco, "pase" to pase, "caduca" to caduca, "claveWeb" to claveWeb)))
    }

    fun normUsuario(s: String) = s.trim().lowercase()
    private fun correo(u: String) = if (u.contains('@')) u else "$u@${cfg.dominio}"

    private fun clave(): String {
        if (claveWeb.isNotBlank()) return claveWeb
        val r = try { red.pedir("GET", url("/config/webKey")) } catch (e: Red.SinRed) { throw Error("No hay conexión con la cuenta.", true) }
        val k = runCatching { Json.lee(r.cuerpo) }.getOrNull().comoTexto()
        if (!r.ok || k.isBlank()) throw Error("La cuenta no está preparada (falta la clave web).")
        claveWeb = k
        return k
    }

    /** Una dirección de la base de datos con el pase (si hay sesión) y los parámetros extra. */
    fun url(ruta: String, consulta: String = "", conPase: Boolean = false): String {
        val p = ArrayList<String>()
        if (cfg.extra.isNotBlank()) p += cfg.extra
        if (consulta.isNotBlank()) p += consulta
        if (conPase) p += "auth=" + URLEncoder.encode(paseValido(), "UTF-8")
        return cfg.baseDatos.trimEnd('/') + ruta + ".json" + if (p.isEmpty()) "" else "?" + p.joinToString("&")
    }

    /** Entra con usuario y contraseña. */
    fun entrar(usuarioEscrito: String, contrasena: String) {
        val u = normUsuario(usuarioEscrito)
        if (u.isBlank() || contrasena.isBlank()) throw Error("Escribe tu usuario y tu contraseña.")
        val cuerpo = Json.escribe(mapOf("email" to correo(u), "password" to contrasena, "returnSecureToken" to true))
        val r = try { red.pedir("POST", "${cfg.baseAuth.trimEnd('/')}/v1/accounts:signInWithPassword?key=${clave()}", cuerpo) } catch (e: Red.SinRed) { throw Error("No hay conexión con la cuenta.", true) }
        val m = runCatching { Json.lee(r.cuerpo) }.getOrNull().comoMapa()
        if (!r.ok) {
            val msg = m["error"].comoMapa()["message"].comoTexto()
            throw Error(when {
                msg.startsWith("EMAIL_NOT_FOUND") || msg.startsWith("INVALID_PASSWORD") || msg.startsWith("INVALID_LOGIN_CREDENTIALS") -> "Usuario o contraseña incorrectos."
                msg.startsWith("USER_DISABLED") -> "Esta cuenta está desactivada."
                msg.startsWith("TOO_MANY_ATTEMPTS") -> "Demasiados intentos. Espera un poco y vuelve a probar."
                else -> "La cuenta no ha dejado entrar ($msg)."
            })
        }
        uid = m["localId"].comoTexto(); usuario = u
        pase = m["idToken"].comoTexto(); refresco = m["refreshToken"].comoTexto()
        caduca = System.currentTimeMillis() + m["expiresIn"].comoLong().coerceAtLeast(60) * 1000L
        guarda()
    }

    /**
     * Lo que necesita otra app de la familia del mismo ordenador para entrar sin la contraseña (en el PC se guarda en el llavero
     * del sistema). Nunca lleva la contraseña.
     */
    fun comparte(): Map<String, Any?> = if (!dentro) emptyMap() else mapOf("uid" to uid, "usuario" to usuario, "refresco" to refresco, "claveWeb" to claveWeb)

    /**
     * Entra con la sesión que comparte otra app de la familia ([comparte]): se comprueba con la cuenta pidiendo un pase nuevo.
     * Bloquea (red). Si la cuenta no la acepta, no se entra.
     */
    @Synchronized
    fun adopta(otra: Map<String, Any?>) {
        val u = otra["uid"].comoTexto(); val r = otra["refresco"].comoTexto()
        if (u.isBlank() || r.isBlank()) throw Error("No hay sesión que compartir.")
        val antes = listOf(uid, usuario, refresco, pase, caduca)
        uid = u; usuario = otra["usuario"].comoTexto(); refresco = r; pase = ""; caduca = 0L
        if (claveWeb.isBlank()) claveWeb = otra["claveWeb"].comoTexto()
        try { renueva() } catch (e: Error) {
            if (e.sinRed) { uid = antes[0] as String; usuario = antes[1] as String; refresco = antes[2] as String; pase = antes[3] as String; caduca = antes[4] as Long }
            else if (dentro) salir()
            throw e
        }
    }

    fun salir() {
        uid = ""; usuario = ""; pase = ""; refresco = ""; caduca = 0L
        seguro.borra(ARCHIVO)
    }

    /** El pase de acceso, renovado si va a caducar (dura una hora). */
    @Synchronized
    fun paseValido(): String {
        if (!dentro) throw Error("No has entrado en la cuenta.")
        if (pase.isNotBlank() && System.currentTimeMillis() < caduca - 120_000) return pase
        renueva()
        return pase
    }

    /** Renueva el pase con el `refresh token` (también cuando el servidor dice que el pase ya no vale). */
    @Synchronized
    fun renueva() {
        val cuerpo = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresco, "UTF-8")
        val r = try { red.pedir("POST", "${cfg.baseToken.trimEnd('/')}/v1/token?key=${clave()}", cuerpo, "application/x-www-form-urlencoded") } catch (e: Red.SinRed) { throw Error("No hay conexión con la cuenta.", true) }
        val m = runCatching { Json.lee(r.cuerpo) }.getOrNull().comoMapa()
        if (!r.ok) {
            val msg = m["error"].comoMapa()["message"].comoTexto()
            // El pase ya no sirve (contraseña cambiada, cuenta borrada): hay que volver a entrar
            if (msg.contains("TOKEN_EXPIRED") || msg.contains("INVALID_REFRESH_TOKEN") || msg.contains("USER_NOT_FOUND") || msg.contains("USER_DISABLED")) salir()
            throw Error("La cuenta pide volver a entrar ($msg).")
        }
        pase = m["id_token"].comoTexto(); refresco = m["refresh_token"].comoTexto().ifBlank { refresco }
        caduca = System.currentTimeMillis() + m["expires_in"].comoLong().coerceAtLeast(60) * 1000L
        guarda()
    }

    /** Fuerza que el próximo pase se pida de nuevo (el servidor ha respondido 401). */
    fun paseCaducado() { caduca = 0L }

    companion object { const val ARCHIVO = "sesion.json" }
}
