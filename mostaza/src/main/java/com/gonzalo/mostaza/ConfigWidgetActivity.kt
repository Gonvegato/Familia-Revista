package com.gonzalo.mostaza

import com.gonzalo.familia.EstiloWidgetActivity

/** Al poner cualquier widget de Hathor (o al cambiarlo: mantenerlo pulsado → Ajustes del widget): cómo lleva el acento (familia/EstiloWidgetActivity). */
class ConfigWidgetActivity : EstiloWidgetActivity() {
    override val nombreApp = "Hathor"
    override val papelApp = 0xFFF8F0D4.toInt()
    override val tintaApp = 0xFF000000.toInt()
    override val acentoApp = 0xFFB56576.toInt()
    override fun preparar() = iniciar(this)
    override suspend fun previas(id: Int) = Widgets.previas(this, id)
    override fun repintar(id: Int) = Widgets.repintar(this, id)
}
