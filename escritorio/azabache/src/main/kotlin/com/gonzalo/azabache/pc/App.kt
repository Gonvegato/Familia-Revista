package com.gonzalo.azabache.pc

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.launch

/** Dónde estás en Azabache. */
sealed class Pantalla {
    data object Inicio : Pantalla()
    data class Lista(val kind: Int) : Pantalla()
    data class Detalle(val kind: Int, val id: Long, val volver: Pantalla) : Pantalla()
    data object Diario : Pantalla()
    data object Sagas : Pantalla()
    data class UnaSaga(val id: Long) : Pantalla()
    data object Cifras : Pantalla()
    data object Ajustes : Pantalla()
}

/** Lo que necesitan las pantallas para moverse y avisar. */
class Nav(val ir: (Pantalla) -> Unit, val aviso: (String) -> Unit)

@Composable
fun AppAzabache(inicial: Pantalla = Pantalla.Inicio) {
    LaFamilia.cambios
    var pantalla by remember { mutableStateOf(inicial) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val nav = remember { Nav({ pantalla = it }) { m -> scope.launch { snack.showSnackbar(m, withDismissAction = true) } } }
    val kinds = Cuenta.kinds()
    // La columna de la izquierda: Inicio, tus categorías, y lo demás
    val entradas = buildList {
        add(Entrada("Portada", grupo = "Azabache"))
        kinds.forEach { k -> add(Entrada(k.title, Cuenta.fichas(k.id).size, grupo = "Colección")) }
        add(Entrada("Diario", grupo = "Tu año")); add(Entrada("Sagas", grupo = "Tu año")); add(Entrada("Cifras", grupo = "Tu año"))
        add(Entrada("Ajustes", grupo = "Más"))
    }
    val sel = when (val p = pantalla) {
        Pantalla.Inicio -> 0
        is Pantalla.Lista -> 1 + kinds.indexOfFirst { it.id == p.kind }.coerceAtLeast(0)
        is Pantalla.Detalle -> 1 + kinds.indexOfFirst { it.id == p.kind }.coerceAtLeast(0)
        Pantalla.Diario -> 1 + kinds.size
        Pantalla.Sagas, is Pantalla.UnaSaga -> 2 + kinds.size
        Pantalla.Cifras -> 3 + kinds.size
        Pantalla.Ajustes -> 4 + kinds.size
    }
    Row(Modifier.fillMaxSize()) {
        Lateral("Azabache", entradas, sel) { i ->
            pantalla = when {
                i == 0 -> Pantalla.Inicio
                i <= kinds.size -> Pantalla.Lista(kinds[i - 1].id)
                i == kinds.size + 1 -> Pantalla.Diario
                i == kinds.size + 2 -> Pantalla.Sagas
                i == kinds.size + 3 -> Pantalla.Cifras
                else -> Pantalla.Ajustes
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 0.dp)) { Filete(1.dp, Line, Modifier.fillMaxHeight()) }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Crossfade(pantalla, label = "pantalla") { p ->
                when (p) {
                    Pantalla.Inicio -> PantInicio(nav)
                    is Pantalla.Lista -> PantLista(Cuenta.kind(p.kind), nav)
                    is Pantalla.Detalle -> PantDetalle(Cuenta.kind(p.kind), p.id, p.volver, nav)
                    Pantalla.Diario -> PantDiario(nav)
                    Pantalla.Sagas -> PantSagas(nav)
                    is Pantalla.UnaSaga -> PantSaga(p.id, nav)
                    Pantalla.Cifras -> PantCifras(nav)
                    Pantalla.Ajustes -> PantAjustes(nav)
                }
            }
            SnackbarHost(snack, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(16.dp))
        }
    }
}
