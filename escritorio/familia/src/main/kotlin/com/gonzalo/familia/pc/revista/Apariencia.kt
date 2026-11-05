package com.gonzalo.familia.pc.revista

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Sitio
import java.io.File

/**
 * La apariencia de una app del ordenador: la misma que la de esa app en Android (`ajustes/<app>` → `skin`), así que si cambias
 * los colores de Marfil en el móvil, cambian en el Marfil del ordenador, y al revés. Gana la más reciente. Aquí se guarda una copia
 * (para verla igual sin conexión). Las hermanas siguen, si quieres, los colores de Azabache (`ajustes/azabache`).
 */
object Apariencia {
    @Volatile private var app = ""
    private fun archivo() = File(Sitio.de(app), "apariencia.json")

    /** Al arrancar (después de [LaFamilia.inicia]). */
    fun inicia(nombreApp: String) {
        app = nombreApp
        Skin.soyAzabache = nombreApp == "azabache"
        runCatching { archivo().takeIf { it.isFile }?.readText()?.let { Json.lee(it).comoMapa() }?.let { aplica(it["propia"].comoMapa()); TemaAzabache.pon(it["azabache"].comoMapa()) } }
        runCatching {
            val c = LaFamilia.coleccion("ajustes")
            c.alCambiar { refs -> if (app in refs || "azabache" in refs) deLaCuenta() }
            deLaCuenta()
        }
    }

    private fun aplica(m: Map<String, Any?>) { if (m.isNotEmpty()) Skin.aplicarAjustes(m) }

    private fun deLaCuenta() {
        val c = runCatching { LaFamilia.coleccion("ajustes") }.getOrNull() ?: return
        c.nodo("azabache")?.mapa("skin")?.let { TemaAzabache.pon(it) }
        if (!Skin.soyAzabache) c.nodo(app)?.let { n -> val sk = n.mapa("skin"); if (sk.isNotEmpty()) aplica(sk) }
        guardaAqui()
    }

    private fun guardaAqui() {
        runCatching { archivo().writeText(Json.escribe(mapOf("propia" to Skin.ajustesMapa(), "azabache" to TemaAzabache.skin))) }
    }

    /** Has cambiado la apariencia aquí: se apunta la hora y va a la cuenta (a tus otros dispositivos con esta app). */
    fun guarda() {
        Skin.ajustesAt = System.currentTimeMillis()
        guardaAqui()
        if (!Skin.soyAzabache) runCatching { LaFamilia.coleccion("ajustes").cambia(app, mapOf("skin" to Skin.ajustesMapa())) }
    }
}
