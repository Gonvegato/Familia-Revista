package com.gonzalo.familia.nucleo

import org.junit.Assume
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * El emulador oficial de Firebase (base de datos y cuentas), con las reglas de verdad de `nube/reglas.json`. Se enciende con
 * `herramientas/emulador.sh`; si no está encendido, las pruebas que lo necesitan se saltan (no fallan).
 */
object Emulador {
    val host: String = System.getProperty("familia.emulador") ?: "http://127.0.0.1"
    const val PROYECTO = "demo-familia"
    val db get() = "$host:9000"
    val auth get() = "$host:9099"
    val ns = "ns=$PROYECTO-default-rtdb"

    val cfg get() = Config(
        baseDatos = db, extra = ns,
        baseAuth = "$auth/identitytoolkit.googleapis.com", baseToken = "$auth/securetoken.googleapis.com",
        claveWeb = "clave-de-prueba",
    )

    fun encendido(): Boolean = runCatching {
        (URL("$db/.json?$ns").openConnection() as HttpURLConnection).run { connectTimeout = 800; readTimeout = 2000; responseCode in 200..499 }
    }.getOrDefault(false) && runCatching {
        (URL("$auth/").openConnection() as HttpURLConnection).run { connectTimeout = 800; readTimeout = 2000; responseCode in 200..499 }
    }.getOrDefault(false)

    fun exige() = Assume.assumeTrue("El emulador de Firebase no está encendido (herramientas/emulador.sh)", encendido())

    fun pide(metodo: String, url: String, cuerpo: String? = null, admin: Boolean = false): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        if (metodo == "PATCH") { c.requestMethod = "POST"; c.setRequestProperty("X-HTTP-Method-Override", "PATCH") } else c.requestMethod = metodo
        if (admin) c.setRequestProperty("Authorization", "Bearer owner")
        if (cuerpo != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(cuerpo.toByteArray()) } }
        val code = c.responseCode
        val t = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        c.disconnect()
        return code to t
    }

    /** Todo de cero: reglas de verdad, base de datos vacía (con la clave web) y sin cuentas. */
    fun limpia() {
        exige()
        val reglas = File(System.getProperty("familia.reglas") ?: "../nube/reglas.json").readText()
        check(pide("PUT", "$db/.settings/rules.json?$ns", reglas, admin = true).first == 200) { "No se pudieron poner las reglas" }
        pide("PUT", "$db/.json?$ns", """{"config":{"webKey":"clave-de-prueba"}}""", admin = true)
        pide("DELETE", "$auth/emulator/v1/projects/$PROYECTO/accounts")
    }

    /** Crea una cuenta (usuario@ronron.example.com) y devuelve su uid. */
    fun cuenta(usuario: String, contrasena: String = "secreto1"): String {
        val (code, t) = pide("POST", "$auth/identitytoolkit.googleapis.com/v1/accounts:signUp?key=x",
            """{"email":"$usuario@ronron.example.com","password":"$contrasena","returnSecureToken":true}""")
        check(code == 200) { "No se pudo crear la cuenta: $t" }
        return Json.lee(t).comoMapa()["localId"].comoTexto()
    }

    /** Lo que hay en una ruta, visto por el administrador (sin reglas). */
    fun lee(ruta: String): Any? = Json.lee(pide("GET", "$db$ruta.json?$ns", admin = true).second)

    fun escribe(ruta: String, valor: Any?) = pide("PUT", "$db$ruta.json?$ns", Json.escribe(valor), admin = true)

    /** Un dispositivo nuevo con la sesión ya abierta. */
    fun dispositivo(usuario: String, disco: Disco = DiscoMemoria(), seguro: Disco = DiscoMemoria()): Familia =
        Familia(cfg, RedJava(), disco, seguro).also { it.automatico = false; if (!it.sesion.dentro) it.sesion.entrar(usuario, "secreto1") }
}
