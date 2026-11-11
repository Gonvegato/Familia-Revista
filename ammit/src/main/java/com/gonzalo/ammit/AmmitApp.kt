package com.gonzalo.ammit

import android.app.Application

/** Al arrancar: la caja de claves (el token de tu kDrive, cifrado) y el actualizador (mira tu kDrive y se instala sola). */
class AmmitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { Secretos.inicia(this) }
        // La cuenta de la familia: la misma que las hermanas (entra sola si alguna la tiene) — de ella saca el token de tu kDrive
        runCatching { com.gonzalo.familia.LaFamilia.inicia(this); com.gonzalo.familia.LaFamilia.pideSesion() }
        Ammit.cargar(this)
        runCatching { Actualizador.init(this) }
    }
}
