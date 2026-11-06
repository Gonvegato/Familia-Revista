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
    private val TEXTOS = setOf("recsdone", "suitepc", "guide_last", "myname")
    private val SI_NO = setOf("diario_todo", "shotsgal", "pnotify", "knows2tap")

    /**
     * De las preferencias de siempre («gamerate»): las recomendaciones de tu pareja que ya añadiste o descartaste, el PC de Atrio elegido,
     * las capturas en la galería, el diario con todo, los avisos (de tu pareja, de lectura y del diario), la pista del doble toque, los
     * objetivos de cada año, tus guías de cada juego, dónde te quedaste en cada guía y tu nombre.
     */
    val GENERAL: EspejoCuenta.Grupo = EspejoCuenta.Grupo("gamerate", "azabache_general", tipo = { k ->
        when {
            k in TEXTOS || k.startsWith("guides_") -> EspejoCuenta.Tipo.TEXTO
            k in SI_NO -> EspejoCuenta.Tipo.SI_NO
            k == "aviso_lectura" || k == "aviso_diario" || GOAL.matches(k) -> EspejoCuenta.Tipo.ENTERO
            else -> null
        }
    }, juntar = { k ->
        when {
            k == "recsdone" -> EspejoCuenta::juntaComas
            k == "guide_last" -> ::juntaUltimas
            k.startsWith("guides_") -> { a: String, b: String -> EspejoCuenta.juntaObjetos(a, b) { it.optString("u") } }
            else -> null
        }
    }, alLlegar = { c ->
        AjustesHub.version++
        runCatching { LecturaAviso.programa(c) }; runCatching { DiarioAviso.programa(c) }
        runCatching { val s = Store(c); if (s.paired && s.notifyPartner) PartnerWatch.schedule(c) }
        runCatching { Widgets.pedir(c) }
    })

    /** Descubrir: las preguntas que ya te han salido (para no repetirlas) y los filtros de juegos (años y plataformas). */
    val DESCUBRIR: EspejoCuenta.Grupo = EspejoCuenta.Grupo("descubrir", "azabache_descubrir", tipo = { k ->
        if (k.startsWith("recent_") || k == "f_years" || k == "f_plats") EspejoCuenta.Tipo.TEXTO else null
    }, juntar = { k -> if (k.startsWith("recent_")) EspejoCuenta::juntaComas else null })

    /** Los duelos de libros que llevas a medias (y los libros ya ordenados, y cómo estaban las notas para deshacerlo). */
    val DUELOS: EspejoCuenta.Grupo = EspejoCuenta.Grupo("duelos", "azabache_duelos", tipo = { k ->
        if (k in setOf("pool", "ordenados", "duelos", "saltados", "copia")) EspejoCuenta.Tipo.TEXTO else null
    }, juntar = { k -> if (k == "ordenados") EspejoCuenta::juntaLista else null })

    /** Escribir: el tamaño y la letra del editor, la máquina de escribir, la tipografía automática y tus sprints (el que corre, no). */
    val ESCRIBIR: EspejoCuenta.Grupo = EspejoCuenta.Grupo("escribir", "azabache_escribir", tipo = { k ->
        when (k) {
            "tam" -> EspejoCuenta.Tipo.DECIMAL
            "fuente" -> EspejoCuenta.Tipo.ENTERO
            "maquina", "tipografia" -> EspejoCuenta.Tipo.SI_NO
            "sprints" -> EspejoCuenta.Tipo.TEXTO
            else -> null
        }
    }, juntar = { k -> if (k == "sprints") { a: String, b: String -> EspejoCuenta.juntaObjetos(a, b) { "${it.optString("pid")}@${it.optLong("desde")}" } } else null })

    /** La web de imágenes que usaste la última vez. */
    val INTERNET: EspejoCuenta.Grupo = EspejoCuenta.Grupo("internet", "azabache_internet", { k -> if (k == "fuente") EspejoCuenta.Tipo.TEXTO else null })

    /** Subir las capturas del PC a tu kDrive (el token es de `/claves`; lo demás de kDrive, de este móvil). */
    val NUBE: EspejoCuenta.Grupo = EspejoCuenta.Grupo("nube", "azabache_nube", { k -> if (k == "shots") EspejoCuenta.Tipo.SI_NO else null })

    /** «Actualizar sola» (lo que pasó en la última comprobación es de aquí). */
    val ACTUALIZADOR: EspejoCuenta.Grupo = EspejoCuenta.Grupo("actualizador", "azabache_actualizador", { k -> if (k == "on") EspejoCuenta.Tipo.SI_NO else null })

    val TODOS: List<EspejoCuenta.Grupo> get() = listOf(GENERAL, DESCUBRIR, DUELOS, ESCRIBIR, INTERNET, NUBE, ACTUALIZADOR)

    /** Dónde te quedaste en cada guía, de los dos lados: de cada una, lo más reciente. */
    fun juntaUltimas(a: String, b: String): String {
        val x = runCatching { JSONObject(a) }.getOrDefault(JSONObject())
        val y = runCatching { JSONObject(b) }.getOrDefault(JSONObject())
        for (k in y.keys()) { val o = y.optJSONObject(k) ?: continue; if ((x.optJSONObject(k)?.optLong("at") ?: -1L) < o.optLong("at")) x.put(k, o) }
        return x.toString()
    }
}
