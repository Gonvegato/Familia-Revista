package com.gonzalo.marfil.pc

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.launch

sealed class Pantalla {
    data object Portada : Pantalla()
    data class Lista(val estado: Estado) : Pantalla()
    data class Ficha(val id: String, val volver: Pantalla) : Pantalla()
    data class Leer(val id: String, val volver: Pantalla) : Pantalla()
    data object Escritos : Pantalla()
    data class Capitulo(val ref: String) : Pantalla()
    data object Ajustes : Pantalla()
}

class Nav(val ir: (Pantalla) -> Unit, val aviso: (String) -> Unit)

@Composable
fun AppMarfil(inicial: Pantalla = Pantalla.Portada) {
    LaFamilia.cambios
    var pantalla by remember { mutableStateOf(inicial) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val nav = remember { Nav({ pantalla = it }) { m -> scope.launch { snack.showSnackbar(m, withDismissAction = true) } } }
    // El lector, a pantalla completa (sin la columna)
    (pantalla as? Pantalla.Leer)?.let { p -> Box(Modifier.fillMaxSize()) { PantLector(p.id, p.volver, nav); SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp)) }; return }
    (pantalla as? Pantalla.Capitulo)?.let { p -> Box(Modifier.fillMaxSize()) { PantCapitulo(p.ref, nav) }; return }
    val libros = Libros.todos()
    val estados = listOf(Estado.LEYENDO, Estado.PENDIENTE, Estado.LEIDO, Estado.ABANDONADO)
    val entradas = buildList {
        add(Entrada("Portada", grupo = "Marfil"))
        estados.forEach { e -> add(Entrada(if (e == Estado.LEIDO) "Leídos" else if (e == Estado.LEYENDO) "Leyendo" else e.nombre + "s", libros.count { it.estado == e }, grupo = "Tus libros")) }
        add(Entrada("Escritos de Cobalto", grupo = "Más")); add(Entrada("Ajustes", grupo = "Más"))
    }
    val sel = when (val p = pantalla) {
        Pantalla.Portada -> 0
        is Pantalla.Lista -> 1 + estados.indexOf(p.estado)
        is Pantalla.Ficha -> (p.volver as? Pantalla.Lista)?.let { 1 + estados.indexOf(it.estado) } ?: 0
        Pantalla.Escritos -> 5
        Pantalla.Ajustes -> 6
        else -> 0
    }
    Row(Modifier.fillMaxSize()) {
        Lateral("Marfil", entradas, sel) { i -> pantalla = when (i) { 0 -> Pantalla.Portada; in 1..4 -> Pantalla.Lista(estados[i - 1]); 5 -> Pantalla.Escritos; else -> Pantalla.Ajustes } }
        Filete(1.dp, Line, Modifier.fillMaxHeight().width(1.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Crossfade(pantalla, label = "pantalla") { p ->
                when (p) {
                    Pantalla.Portada -> PantPortada(nav)
                    is Pantalla.Lista -> PantLista(p.estado, nav)
                    is Pantalla.Ficha -> PantFicha(p.id, p.volver, nav)
                    Pantalla.Escritos -> PantEscritos(nav)
                    Pantalla.Ajustes -> PantAjustes(nav)
                    else -> {}
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}
