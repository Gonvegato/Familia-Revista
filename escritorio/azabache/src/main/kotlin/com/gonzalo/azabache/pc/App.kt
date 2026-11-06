package com.gonzalo.azabache.pc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Dónde estás en Azabache. */
sealed class Pantalla {
    data object Inicio : Pantalla()
    data class Lista(val kind: Int) : Pantalla()
    data class Detalle(val kind: Int, val id: Long, val volver: Pantalla) : Pantalla()
    data object Diario : Pantalla()
    data object Sagas : Pantalla()
    data class UnaSaga(val id: Long) : Pantalla()
    data object Cifras : Pantalla()
    /** Lo de tu pareja: su revista y una ficha suya (solo para leer). */
    data object Pareja : Pantalla()
    data class DeLaPareja(val uid: String, val kind: Int, val id: Long) : Pantalla()
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
    // Tu pareja (si la tienes): se mira en la cuenta una vez, sin parar la ventana
    var pareja by remember { mutableStateOf(Cuenta.uidParejaSabido()) }
    LaunchedEffect(LaFamilia.dentro) { if (LaFamilia.dentro) pareja = withContext(Dispatchers.IO) { runCatching { Cuenta.uidPareja() }.getOrDefault("") } }
    // La columna de la izquierda: la portada, tus categorías, y lo demás
    val destinos = buildList<Pair<Entrada, Pantalla>> {
        add(Entrada("Portada", grupo = "Azabache") to Pantalla.Inicio)
        kinds.forEach { k -> add(Entrada(k.title, Cuenta.fichas(k.id).size, grupo = "Colección") to Pantalla.Lista(k.id)) }
        add(Entrada("Diario", grupo = "Tu año") to Pantalla.Diario); add(Entrada("Sagas", grupo = "Tu año") to Pantalla.Sagas); add(Entrada("Cifras", grupo = "Tu año") to Pantalla.Cifras)
        if (pareja.isNotBlank()) add(Entrada("Pareja", grupo = "Más") to Pantalla.Pareja)
        add(Entrada("Ajustes", grupo = "Más") to Pantalla.Ajustes)
    }
    val sel = destinos.indexOfFirst { (_, d) ->
        when (val p = pantalla) {
            is Pantalla.Detalle -> d == Pantalla.Lista(p.kind)
            is Pantalla.UnaSaga -> d == Pantalla.Sagas
            is Pantalla.DeLaPareja -> d == Pantalla.Pareja
            else -> d == p
        }
    }.coerceAtLeast(0)
    Row(Modifier.fillMaxSize()) {
        Lateral("Azabache", destinos.map { it.first }, sel) { i -> pantalla = destinos[i].second }
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 0.dp)) { Filete(1.dp, Line, Modifier.fillMaxHeight()) }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            // Como en el móvil: abrir una ficha la sube un poco desde abajo; entre secciones, un fundido rápido
            AnimatedContent(pantalla, transitionSpec = {
                if (targetState is Pantalla.Detalle || targetState is Pantalla.DeLaPareja)
                    (fadeIn(tween(240)) + slideInVertically(tween(300)) { it / 16 }) togetherWith fadeOut(tween(160))
                else fadeIn(tween(200)) togetherWith fadeOut(tween(140))
            }, label = "pantalla") { p ->
                when (p) {
                    Pantalla.Inicio -> PantInicio(nav)
                    is Pantalla.Lista -> PantLista(Cuenta.kind(p.kind), nav)
                    is Pantalla.Detalle -> PantDetalle(Cuenta.kind(p.kind), p.id, p.volver, nav)
                    Pantalla.Diario -> PantDiario(nav)
                    Pantalla.Sagas -> PantSagas(nav)
                    is Pantalla.UnaSaga -> PantSaga(p.id, nav)
                    Pantalla.Cifras -> PantCifras(nav)
                    Pantalla.Pareja -> if (pareja.isNotBlank()) PantPareja(pareja, nav) else Pagina { Vacio("Sin pareja", "Se pone en el Azabache del móvil (Ajustes → Cuenta).") }
                    is Pantalla.DeLaPareja -> PantDeLaPareja(p.uid, p.kind, p.id, nav)
                    Pantalla.Ajustes -> PantAjustes(nav)
                }
            }
            SnackbarHost(snack, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(16.dp))
        }
    }
}
