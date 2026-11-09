package com.gonzalo.mostaza

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lo de una sección de tu biblioteca, con su filtro (películas, series o todo) y su orden: el mismo en el móvil y en la sala. */
fun deSeccion(todos: List<Titulo>, estado: Estado, orden: OrdenGuardado.Orden, filtro: OrdenGuardado.Filtro): List<Titulo> =
    todos.filter { it.estado == estado }
        .filter { when (filtro) { OrdenGuardado.Filtro.PELICULAS -> !it.esSerie; OrdenGuardado.Filtro.SERIES -> it.esSerie; else -> true } }
        .let { l -> when (orden) {
            OrdenGuardado.Orden.RECIENTES -> l.sortedByDescending { maxOf(it.ultima, it.fin, it.anadido) }
            OrdenGuardado.Orden.ANADIDOS -> l.sortedByDescending { it.anadido }
            OrdenGuardado.Orden.TITULO -> l.sortedBy { it.nombre.lowercase() }
            OrdenGuardado.Orden.ANO -> l.sortedByDescending { it.ano.take(4) }
        } }

/** El texto de una sección vacía (lo mismo en el móvil y en la sala). */
fun seccionVacia(estado: Estado): String = when (estado) {
    Estado.VIENDO -> "Nada a medias. Lo que ves entra aquí solo cuando llevas 10 minutos viéndolo."
    Estado.PENDIENTE -> "Nada pendiente. En la ficha de cualquier película o serie, «+ Añadir»."
    Estado.VISTO -> "Aún no has terminado nada aquí. Una película está vista al 90 %; una serie, con todos sus episodios estrenados vistos."
    Estado.ABANDONADO -> "Nada abandonado."
}

