package com.gonzalo.cobalto

import android.content.Context
import com.gonzalo.familia.LaFamilia
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lo de antes pasa a la cuenta de la familia, una vez en cada dispositivo: la apariencia, el token de kDrive de este móvil y lo que
 * leen Azabache y Marfil (obras, capítulos terminados y palabras: se publica de nuevo desde los documentos de Escribir, que no cambian).
 */
object Migracion {
    private val enMarcha = AtomicBoolean(false)
    private fun p(c: Context) = c.getSharedPreferences("cobalto_migracion", Context.MODE_PRIVATE)
    fun hecha(c: Context) = p(c).getBoolean("hecha", false)

    fun siHaceFalta(c: Context) {
        if (!Cuenta.vinculada || hecha(c) || !enMarcha.compareAndSet(false, true)) return
        val app = c.applicationContext
        kotlin.concurrent.thread(name = "cobalto-migracion") {
            try {
                SincroP.cambio(app)
                KDrive.migraALaCuenta(app)
                if (kotlinx.coroutines.runBlocking { Escritos.sincroniza(app) }) Publicar.todo(app)
                p(app).edit().putBoolean("hecha", true).apply()
                LaFamilia.familia.programa(0)
            } catch (e: Exception) { } finally { enMarcha.set(false) }
        }
    }
}
