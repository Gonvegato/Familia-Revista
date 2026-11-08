package com.gonzalo.ptah

import androidx.compose.runtime.Composable

/*
 * Los tipos de widget. Cada uno es un [TipoWidget]: su nombre, lo que trae de serie ([TipoWidget.nuevo]), cómo se dibuja
 * ([TipoWidget.pinta], sobre un [Lienzo]), su editor ([TipoWidget.Editor]), las piezas con color propio y las partes que se pueden
 * ocultar, y lo que necesita para estar al día ([Necesidad]). Para añadir uno: un `object` que lo cumpla y su línea en [Tipos.TODOS];
 * nada más (el widget, el editor, la portada y las pruebas lo cogen de ahí).
 */

/** Lo que necesita un tipo para estar al día en la pantalla de inicio. */
enum class Necesidad {
    /** Cambia cada día (la fecha dibujada, el mes, la cuenta atrás): se repinta a medianoche. */
    MEDIANOCHE,
    /** El tiempo (Open-Meteo): se baja cada 30–60 minutos. */
    TIEMPO,
    /** La batería: se repinta cada 15 minutos (y al abrir Ptah). */
    BATERIA,
}

interface TipoWidget {
    val id: String
    val nombre: String
    /** Para agruparlos al elegir: «Relojes», «Buscar y abrir», «Datos», «Lo tuyo». */
    val grupo: String
    /** Una frase: lo que hace (con el tono de la familia). */
    val descripcion: String
    /** Las piezas que se pueden pintar de su color (id → nombre), además de la tinta, el acento y el gris del diseño. */
    val piezas: List<Pair<String, String>> get() = emptyList()
    /** Las partes que se pueden ocultar (id → nombre). */
    val partes: List<Pair<String, String>> get() = emptyList()
    /** Lo que necesita para estar al día. */
    fun necesita(d: Diseno): Set<Necesidad> = emptySet()
    /** El tamaño con el que se enseña de muestra (en «+ Nuevo» y en la portada). */
    val muestra: Tamanos.Celdas get() = Tamanos.Celdas(4, 2)

    /** Un diseño nuevo de este tipo, con lo de serie. */
    fun nuevo(): Diseno = Diseno(Diseno.nuevoId(), nombre, id, Estilo(), opcionesDeSerie(), System.currentTimeMillis(), System.currentTimeMillis())
    fun opcionesDeSerie(): Map<String, Any?> = emptyMap()

    /** Lo que pasa al tocar el widget (donde no haya otra zona). */
    fun accion(d: Diseno): Accion = Accion.Editar(d.id)
    fun descripcionToque(d: Diseno): String = "Abrir ${d.nombre.ifBlank { nombre }} en Ptah"

    /** Dibuja el diseño (el fondo y el borde los pone quien llama: [Pintor]). */
    fun pinta(l: Lienzo)

    /** Lo propio de este tipo en el editor (la pestaña «Contenido»). */
    @Composable fun Editor(d: Diseno, cambia: (Diseno) -> Unit)
}

object Tipos {
    val TODOS: List<TipoWidget> = listOf(
        RelojDigital, RelojAgujas, Calendario,
        Buscador, Accesos,
        Tiempo, TipoBateria,
        TipoNota, CuentaAtras, Foto,
    )
    val GRUPOS = listOf("Relojes", "Buscar y abrir", "Datos", "Lo tuyo")

    fun de(id: String): TipoWidget? = TODOS.firstOrNull { it.id == id }
}

/** Dibuja un diseño entero: el fondo, lo de su tipo, el borde y el toque de serie. */
object Pintor {
    /** El dibujo de [d] en [w] × [h] dp, en claro u oscuro, a [s] píxeles por dp. */
    fun pinta(ctx: android.content.Context, d: Diseno, w: Float, h: Float, noche: Boolean, s: Float, datos: Datos, enWidget: Boolean = false, relojVivo: Boolean = false): Lienzo {
        val l = Lienzo(ctx, d, w, h, noche, s, datos, enWidget, relojVivo)
        val t = Tipos.de(d.tipo)
        l.fondo()
        if (t == null) {
            // Un tipo de una versión más nueva de Ptah: se dice, sin romper nada
            val tl = l.titular("Este widget es de una versión más nueva de Ptah", l.area.width(), l.area.height(), 16f, 9f, 4, l.tinta)
            l.pon(tl, l.area.left, l.area.top)
            l.toque(android.graphics.RectF(0f, 0f, w, h), Accion.AbrirPtah, "Abrir Ptah")
        } else {
            runCatching { if (!Chico.pinta(l, t)) t.pinta(l) }.onFailure { x -> if (x is OutOfMemoryError) throw x }
            // Lo que no tenga zona propia, abre lo de su tipo (lo de encima se toca antes: las zonas van detrás de esta)
            l.zonas.add(0, Zona(android.graphics.RectF(0f, 0f, w, h), t.accion(d), t.descripcionToque(d)))
        }
        l.terminar()
        return l
    }
}
