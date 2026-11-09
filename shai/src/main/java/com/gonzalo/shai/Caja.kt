package com.gonzalo.shai

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
 * Lo delicado (tu clave de la API de Claude, el token de kDrive) se guarda cifrado en este móvil con una clave del almacén de claves
 * de Android ([alias]), que no sale del móvil ni se puede copiar a otro. Si el almacén no existe (las pruebas en el ordenador), sin cifrar.
 */
class Caja private constructor(private val base: DiscoArchivos, private val clave: SecretKey?) : Disco {
    override fun lee(nombre: String): ByteArray? {
        val datos = base.lee(nombre) ?: return null
        val k = clave ?: return datos
        if (datos.size < 13) return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, datos, 0, 12))
            c.doFinal(datos, 12, datos.size - 12)
        }.getOrNull()
    }

    override fun escribe(nombre: String, datos: ByteArray) {
        val k = clave ?: return base.escribe(nombre, datos)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, k)
        base.escribe(nombre, c.iv + c.doFinal(datos))
    }

    override fun borra(nombre: String) = base.borra(nombre)

    companion object {
        /** La caja de esta app. */
        fun de(ctx: Context): Caja = abre(File(ctx.filesDir, "seguro"), "fcfeed_seguro", crear = true)!!

        /** Una caja con la clave [alias]; sin [crear], null si la clave no existe (la del dispositivo de antes, para leer una vez). */
        fun abre(dir: File, alias: String, crear: Boolean): Caja? {
            val k = runCatching { clave(alias, crear) }.getOrElse { if (crear) null else return null }
            if (k == null && !crear) return null
            return Caja(DiscoArchivos(dir), k)
        }

        private fun clave(alias: String, crear: Boolean): SecretKey? {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            if (!crear) return null
            val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            g.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            return g.generateKey()
        }
    }
}

/** Las claves de esta app (texto), en su [Caja]. En las pruebas, en memoria. */
object Secretos {
    @Volatile var enMemoria: MutableMap<String, String>? = null
    @Volatile private var caja: Caja? = null
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun inicia(ctx: Context) { if (caja == null && enMemoria == null) caja = Caja.de(ctx.applicationContext) }

    fun lee(n: String): String {
        enMemoria?.let { return it[n].orEmpty() }
        cache[n]?.let { return it }
        val v = runCatching { caja?.leeTexto("$n.txt") }.getOrNull().orEmpty()
        cache[n] = v
        return v
    }

    fun pon(n: String, v: String) {
        enMemoria?.let { it[n] = v; return }
        val c = caja ?: error("Secretos.inicia no se ha llamado")
        if (v.isBlank()) c.borra("$n.txt") else c.escribeTexto("$n.txt", v)
        cache[n] = v
    }
}
