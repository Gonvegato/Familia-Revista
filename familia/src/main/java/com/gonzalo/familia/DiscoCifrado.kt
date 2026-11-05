package com.gonzalo.familia

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.gonzalo.familia.nucleo.Disco
import com.gonzalo.familia.nucleo.DiscoArchivos
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Lo delicado (el pase de la cuenta, tus claves de servicios, tu contraseña para tus otras apps) se guarda cifrado con una clave
 * del almacén de claves de Android, que no sale del móvil ni se puede copiar a otro.
 */
class DiscoCifrado private constructor(private val base: DiscoArchivos, private val clave: SecretKey) : Disco {
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
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, clave)
        base.escribe(nombre, c.iv + c.doFinal(datos))
    }

    override fun borra(nombre: String) = base.borra(nombre)

    companion object {
        private const val ALIAS = "familia_seguro"

        /** El disco cifrado; si el almacén de claves no existe (las pruebas en el ordenador), uno normal. */
        fun de(ctx: Context, dir: File): Disco = runCatching { DiscoCifrado(DiscoArchivos(dir), clave()) }.getOrElse { DiscoArchivos(dir) }

        private fun clave(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            return g.generateKey()
        }
    }
}
