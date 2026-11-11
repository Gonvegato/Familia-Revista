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
        // El candado mira las palabras de Thot y lo leído en Seshat: sus colecciones, abiertas (y al entrar en la cuenta)
        runCatching { Candado.abrir(); com.gonzalo.familia.LaFamilia.alEntrar { Candado.abrir() } }
        // El widget: cuánto falta para soltarla, al día cuando cambia algo de la cuenta (como mucho cada 20 s)
        runCatching { com.gonzalo.familia.LaFamilia.alCambiar { if (Ammit.encendida) WidgetAmmit.repintarLuego(this, 20_000) } }
        runCatching { Actualizador.init(this) }
    }
}
