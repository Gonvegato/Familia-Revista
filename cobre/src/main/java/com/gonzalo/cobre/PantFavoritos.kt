package com.gonzalo.cobre

import androidx.compose.foundation.background
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.Lista
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Tus favoritos que se pueden ver ahora (su lista, el canal y el favorito), en tu orden. */
@Composable
fun rememberFavoritosVivos(): Pair<List<Triple<Lista, Canal, Favorito>>, Map<String, Cargada?>> {
    Listas.version; Tele.version; EspejoCuenta.version
    val usables = Listas.usables()
    val cargadas = rememberCargadas(usables)
    val favs = MiCuenta.favoritos()
    val vivos = favs.mapNotNull { f -> usables.firstOrNull { it.id == f.lista }?.let { l -> cargadas[l.id]?.porClave?.get(f.canal)?.let { Triple(l, it, f) } } }
    return vivos to cargadas
}

/**
 * Los favoritos uno debajo de otro, en grande: el canal, lo que echan ahora (con lo que va) y lo siguiente, como en «Directo». En la
 * tablet y en la tele, a dos columnas. Al tocar uno se ve, y arriba y abajo en el reproductor pasan por tus favoritos.
 */
@Composable
fun ListaFavoritos(favs: List<Triple<Lista, Canal, Favorito>>, cargadas: Map<String, Cargada?>) {
    val ctx = LocalContext.current
    val ahora = System.currentTimeMillis()
    val columnas = if (esAncha()) 2 else 1
    favs.chunked(columnas).forEach { fila ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            fila.forEach { (l, c, _) ->
                val cg = cargadas[l.id] ?: return@forEach
                val i = favs.indexOfFirst { it.second === c }
                Box(Modifier.weight(1f)) {
                    FilaCanal(l, cg, c, i, ahora) { Reproducir.canal(ctx, l, favs.filter { it.first.id == l.id }.map { it.second }, c) }
                }
            }
            repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** «Favoritos»: solo tus favoritos, en grande y de todas tus listas. Es la página donde se está casi todo el rato. */
@Composable
fun PantFavoritos(alVolver: () -> Unit, alIr: (Destino) -> Unit) {
    val (favs, cargadas) = rememberFavoritosVivos()
    val m = margen()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = m)) {
        CabeceraSeccion("Favoritos", "Tus canales", alVolver) {
            EnlaceRevista("Guía", acento = true) { alIr(Destino.Guia) }
            EnlaceRevista("Todos los canales") { alIr(Destino.Directo) }
        }
        if (favs.isEmpty()) Nota("Aún no tienes favoritos. En «Directo», toca la ★ de un canal y saldrá aquí.", Modifier.padding(top = 18.dp))
        else ListaFavoritos(favs, cargadas)
        Spacer(Modifier.height(30.dp))
    }
}
