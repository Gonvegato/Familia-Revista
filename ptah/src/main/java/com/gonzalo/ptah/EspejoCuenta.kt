package com.gonzalo.ptah

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf

/**
 * Lo que eliges (tus diseños, los colores recientes, «actualizar sola») se guarda en las preferencias de Android de este móvil, un
 * archivo por grupo. (Hasta la 0.5.0 viajaba también con una cuenta en la nube; lo que había allí se trae una vez: [Migracion].)
 */
object EspejoCuenta {
    enum class Tipo { SI_NO, ENTERO, LARGO, DECIMAL, TEXTO }

    /** Un grupo de preferencias: su archivo ([archivo]) y su nombre en la cuenta de antes ([nodo], para traerlo una vez). */
    class Grupo(
        val archivo: String,
        val nodo: String,
        val tipo: (String) -> Tipo?,
        val juntar: (String) -> ((String, String) -> String)? = { null },
        val alLlegar: (Context) -> Unit = {},
        val prefiereLocal: ((String, Any?, Any?) -> Boolean)? = null,
    )

    /** Sube cuando cambia algo que no se ha escrito desde una pantalla (lo traído de la cuenta de antes): las pantallas lo vuelven a leer. */
    private val ver = mutableIntStateOf(0)
    val version: Int get() = ver.intValue
    fun avisa() { ver.intValue++ }

    fun base(c: Context, g: Grupo): SharedPreferences = c.getSharedPreferences(g.archivo, Context.MODE_PRIVATE)
    fun prefs(c: Context, g: Grupo): SharedPreferences = base(c, g)
}
