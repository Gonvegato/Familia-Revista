package com.gonzalo.ammit

import android.app.Application

/** Al arrancar: la caja de claves (el token de tu kDrive, cifrado) y el actualizador (mira tu kDrive y se instala sola). */
class AmmitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { Secretos.inicia(this) }
        Ammit.cargar(this)
        runCatching { Actualizador.init(this) }
    }
}
