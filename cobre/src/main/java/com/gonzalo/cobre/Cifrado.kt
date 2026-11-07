package com.gonzalo.cobre

import android.content.Context
import com.gonzalo.familia.DiscoCifrado
import com.gonzalo.iptv.Envoltorio
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Lo de tus listas que se guarda en el dispositivo y lleva secretos (los canales de una lista M3U llevan su dirección, y la dirección suele
 * llevar tu usuario y tu contraseña): cifrado con AES (en modo CTR, que se lee y se escribe a trozos, para listas de decenas de megas).
 * La clave es de este dispositivo, al azar, y se guarda a su vez cifrada con la del almacén de claves de Android ([DiscoCifrado]): no sale
 * del móvil ni se puede copiar a otro. Cada archivo lleva su propio vector al principio.
 */
class Cifrado private constructor(private val clave: SecretKeySpec) : Envoltorio {
    override fun escribe(out: OutputStream): OutputStream {
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        out.write(iv)
        val c = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.ENCRYPT_MODE, clave, IvParameterSpec(iv)) }
        return CipherOutputStream(out, c)
    }

    override fun lee(inp: InputStream): InputStream {
        val iv = ByteArray(16)
        var n = 0
        while (n < 16) { val r = inp.read(iv, n, 16 - n); if (r < 0) throw java.io.EOFException(); n += r }
        val c = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.DECRYPT_MODE, clave, IvParameterSpec(iv)) }
        return CipherInputStream(inp, c)
    }

    companion object {
        @Volatile private var uno: Cifrado? = null

        fun de(c: Context): Cifrado = uno ?: synchronized(this) {
            uno ?: run {
                val disco = DiscoCifrado.de(c, File(c.filesDir, "cobre_seguro"))
                val k = disco.lee("cache.clave")?.takeIf { it.size == 32 } ?: ByteArray(32).also { SecureRandom().nextBytes(it); disco.escribe("cache.clave", it) }
                Cifrado(SecretKeySpec(k, "AES")).also { uno = it }
            }
        }
    }
}
