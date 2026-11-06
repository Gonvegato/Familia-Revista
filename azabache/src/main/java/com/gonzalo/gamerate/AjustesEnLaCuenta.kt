package com.gonzalo.gamerate

import org.json.JSONObject

/**
 * Las preferencias de Azabache que son tuyas y no de este móvil, y que no viajaban (las listas ya están en la cuenta con [Propias] y la
 * apariencia con [Store.ajustesJson]): cada grupo, en `ajustes/<nodo>`, un campo por ajuste con el nombre que tiene aquí ([EspejoCuenta]).
 * Lo que se queda en cada dispositivo (la sesión, lo ya subido, las cachés, las capturas bajadas, el sprint que está corriendo, la copia de
 * Google Drive de este móvil…) no está en ninguno.
 */
object AjustesEnLaCuenta {
    private val GOAL = Regex("""^goal_\d+_\d+$""")
    private val TEXTOS = setOf("recsdone", "suitepc", "myname")
    private val SI_NO = setOf("diario_todo", "shotsgal", "pnotify", "knows2tap")

    /**
     * De las preferencias de siempre («gamerate»): las recomendaciones de tu pareja que ya añadiste o descartaste, el PC de Atrio elegido,
     * las capturas en la galería, el diario con todo, los avisos (de tu pareja, de lectura y del diario), la pista del doble toque, los
     * objetivos de cada año y tu nombre. (Las guías no: se quitaron de Azabache.)
     */
    val GENERAL: EspejoCuenta.Grupo = EspejoCuenta.Grupo("gamerate", "azabache_general", tipo = { k ->
        when {
            k in TEXTOS -> EspejoCuenta.Tipo.TEXTO
            k in SI_NO -> EspejoCuenta.Tipo.SI_NO
            k == "aviso_lectura" || k == "aviso_diario" || GOAL.matches(k) -> EspejoCuenta.Tipo.ENTERO
            else -> null
        }
    }, juntar = { k ->
        when {
            k == "recsdone" -> EspejoCuenta::juntaComas
            else -> null
        }
    }, alLlegar = { c ->
        AjustesHub.version++
        runCatching { LecturaAviso.programa(c) }; runCatching { DiarioAviso.programa(c) }
        runCatching { val s = Store(c); if (s.paired && s.notifyPartner) PartnerWatch.schedule(c) }
        runCatching { Widgets.pedir(c) }
    })

    // Descubrir, los duelos de libros y Escribir se quitaron de Azabache a propósito (HISTORIA, 1.0.0, «Lo que se va»): lo que quede de ellos
    // en el móvil no viaja (los grupos siguen para el código que aún los lee, pero ninguna clave sube ni baja).
    val DESCUBRIR: EspejoCuenta.Grupo = EspejoCuenta.Grupo("descubrir", "azabache_descubrir", tipo = { null })
    val DUELOS: EspejoCuenta.Grupo = EspejoCuenta.Grupo("duelos", "azabache_duelos", tipo = { null })
    val ESCRIBIR: EspejoCuenta.Grupo = EspejoCuenta.Grupo("escribir", "azabache_escribir", tipo = { null })

    /** La web de imágenes que usaste la última vez. */
    val INTERNET: EspejoCuenta.Grupo = EspejoCuenta.Grupo("internet", "azabache_internet", { k -> if (k == "fuente") EspejoCuenta.Tipo.TEXTO else null })

    /** Subir las capturas del PC a tu kDrive (el token es de `/claves`; lo demás de kDrive, de este móvil). */
    val NUBE: EspejoCuenta.Grupo = EspejoCuenta.Grupo("nube", "azabache_nube", { k -> if (k == "shots") EspejoCuenta.Tipo.SI_NO else null })

    /** «Actualizar sola» (lo que pasó en la última comprobación es de aquí). */
    val ACTUALIZADOR: EspejoCuenta.Grupo = EspejoCuenta.Grupo("actualizador", "azabache_actualizador", { k -> if (k == "on") EspejoCuenta.Tipo.SI_NO else null })

    val TODOS: List<EspejoCuenta.Grupo> get() = listOf(GENERAL, INTERNET, NUBE, ACTUALIZADOR)
}
