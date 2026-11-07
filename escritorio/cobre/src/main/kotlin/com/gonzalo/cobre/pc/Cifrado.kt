package com.gonzalo.cobre.pc

import com.gonzalo.familia.pc.DiscoSeguro
import com.gonzalo.familia.pc.Llavero
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
 * Lo de tus listas que se guarda en el ordenador y lleva secretos (los canales de una lista M3U llevan su dirección, y la dirección suele
 * llevar tu usuario y tu contraseña; el archivo M3U de una lista de archivo, igual): cifrado con AES (en modo CTR, que se lee y se escribe a
 * trozos, para listas de decenas de megas), como en Cobre del móvil. La clave es de este ordenador, al azar, y se guarda a su vez cifrada
 * ([DiscoSeguro], AES-GCM) con la clave del llavero del sistema (el Llavero del Mac; en Linux, el Secret Service —GNOME Keyring, KWallet— o,
 * si no hay, un archivo que solo lee tu usuario): sin tu llavero, la caché no se puede leer. Cada archivo lleva su propio vector al principio.
 */
class Cifrado(private val clave: SecretKeySpec) : Envoltorio {
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
        /** La de este ordenador: la crea la primera vez (en [dir], cifrada con el llavero). */
        fun de(dir: File, llavero: Llavero): Cifrado {
            val disco = DiscoSeguro(File(dir, "cobre_seguro"), llavero)
            val k = disco.lee("cache.clave")?.takeIf { it.size == 32 } ?: ByteArray(32).also { SecureRandom().nextBytes(it); disco.escribe("cache.clave", it) }
            return Cifrado(SecretKeySpec(k, "AES"))
        }
    }
}
