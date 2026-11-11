package com.gonzalo.gamerate

import com.gonzalo.familia.EstiloWidgetActivity

/** Al poner cualquier widget de Jonsu (o al cambiarlo: mantenerlo pulsado → Ajustes del widget): cómo lleva el acento (familia/EstiloWidgetActivity). */
class ConfigWidgetActivity : EstiloWidgetActivity() {
    override val nombreApp = "Jonsu"
    override val papelApp = 0xFFF8F0D4.toInt()
    override val tintaApp = 0xFF000000.toInt()
    override val acentoApp = 0xFF8E5A78.toInt()
    override suspend fun previas(id: Int) = Widgets.previas(this, id)
    override fun repintar(id: Int) = Widgets.repintar(this, id)
}
