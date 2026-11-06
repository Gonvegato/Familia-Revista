package com.gonzalo.mostaza

import android.content.Context
import com.gonzalo.mostaza.EspejoCuenta.Tipo

/**
 * Las fuentes que no dan la película sino un **vídeo de aviso**: cuando tu debrid se niega a darte un archivo (Real-Debrid, por ejemplo,
 * con «infringing_file»), algunos addons (MediaFusion en ElfHosted y otros) no devuelven un error, sino un vídeo corto que lo explica.
 * Se abre bien, así que hay que reconocerlo por lo que dura: una película o un episodio no dura menos de [MAX_AVISO_MS].
 *
 * Las que lo dan se apuntan **en tu cuenta** (`ajustes/mostaza_rotas`, un campo por fuente con la hora en que se vio): lo que tu debrid
 * no te da no te lo da en ningún dispositivo. El campo es una huella de [Elector.clave] (sin secretos y sin caracteres que la base de
 * datos no admite). Pasados [CADUCA_MS] se vuelven a probar (el debrid puede cambiar de idea).
 */
object FuentesRotas {
    const val MAX_AVISO_MS = 4 * 60_000L
    const val CADUCA_MS = 30L * 24 * 3600_000L

    val GRUPO = EspejoCuenta.Grupo("mostaza_rotas", "mostaza_rotas", { k -> if (k.startsWith("r") && k.length == 25) Tipo.LARGO else null })

    /**
     * ¿Es un vídeo de aviso y no lo que se quería ver? Dura algo y menos de [MAX_AVISO_MS], y lo que se quería ver (según su addon, en
     * minutos; 0 = no se sabe) dura más de 10 minutos o no se sabe.
     */
    fun esAviso(duracionMs: Long, minutosEsperados: Int): Boolean =
        duracionMs in 1 until MAX_AVISO_MS && (minutosEsperados <= 0 || minutosEsperados > 10)

    /** El campo de la cuenta de una fuente: «r» y 24 cifras hexadecimales de la huella SHA-256 de su clave. */
    fun campo(clave: String): String {
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(clave.toByteArray())
        return "r" + d.take(12).joinToString("") { "%02x".format(it) }
    }

    /** ¿Está rota (vista hace menos de [CADUCA_MS])? */
    fun rota(cuando: Long, ahora: Long) = cuando > 0 && ahora - cuando < CADUCA_MS

    private fun p(c: Context) = EspejoCuenta.prefs(c, GRUPO)

    /** Para el elector: clave de una fuente → ¿da un aviso? */
    fun busca(c: Context): (String) -> Boolean {
        val x = p(c); val ahora = System.currentTimeMillis()
        return { k -> k.isNotBlank() && rota(x.getLong(campo(k), 0L), ahora) }
    }

    fun apunta(c: Context, clave: String) {
        if (clave.isBlank()) return
        p(c).edit().putLong(campo(clave), System.currentTimeMillis()).apply()
    }
}
