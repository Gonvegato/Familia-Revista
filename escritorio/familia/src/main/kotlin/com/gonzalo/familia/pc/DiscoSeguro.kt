package com.gonzalo.familia.pc

import com.gonzalo.familia.nucleo.Disco
import com.gonzalo.familia.nucleo.DiscoArchivos
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Lo delicado de cada app (el pase de la cuenta, tus claves de servicios) cifrado (AES-GCM) con una clave que vive en el llavero
 * del sistema ([Llavero]): sin el llavero de tu usuario, los archivos no se pueden leer. Es lo que en Android hace `DiscoCifrado`.
 */
class DiscoSeguro(dir: File, llavero: Llavero) : Disco {
    private val base = DiscoArchivos(dir)
    private val clave: SecretKey = claveDe(llavero)

    override fun lee(nombre: String): ByteArray? {
        val datos = base.lee(nombre) ?: return null
        if (datos.size < 13) return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, clave, GCMParameterSpec(128, datos, 0, 12))
            c.doFinal(datos, 12, datos.size - 12)
        }.getOrNull()
    }

    override fun escribe(nombre: String, datos: ByteArray) {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, clave, GCMParameterSpec(128, iv))
        base.escribe(nombre, iv + c.doFinal(datos))
    }

    override fun borra(nombre: String) = base.borra(nombre)

    companion object {
        const val NOMBRE_CLAVE = "clave-disco"

        private fun claveDe(l: Llavero): SecretKey {
            l.lee(NOMBRE_CLAVE)?.let { t -> runCatching { Base64.getDecoder().decode(t) }.getOrNull()?.takeIf { it.size == 32 }?.let { return SecretKeySpec(it, "AES") } }
            val nueva = ByteArray(32).also { SecureRandom().nextBytes(it) }
            l.guarda(NOMBRE_CLAVE, Base64.getEncoder().encodeToString(nueva))
            // (si otra app la creó a la vez, gana la que quedó en el llavero)
            l.lee(NOMBRE_CLAVE)?.let { t -> runCatching { Base64.getDecoder().decode(t) }.getOrNull()?.takeIf { it.size == 32 }?.let { return SecretKeySpec(it, "AES") } }
            return SecretKeySpec(nueva, "AES")
        }
    }
}
