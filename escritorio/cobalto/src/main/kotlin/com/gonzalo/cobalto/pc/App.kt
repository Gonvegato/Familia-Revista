package com.gonzalo.cobalto.pc

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gonzalo.cobalto.EstadoObra
import com.gonzalo.cobalto.obrasDe
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.launch

/** Dónde estás en Cobalto. */
sealed class Pantalla {
    data object Portada : Pantalla()
    data class Obra(val pid: String, val eid: String? = null) : Pantalla()
    data object Papelera : Pantalla()
    data object Ajustes : Pantalla()
}

class Nav(val ir: (Pantalla) -> Unit, val aviso: (String) -> Unit)

@Composable
fun AppCobalto(inicial: Pantalla = Pantalla.Portada) {
    LaFamilia.cambios
    val e = Cuaderno.escritos
    e.version
    var pantalla by remember { mutableStateOf(inicial) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val nav = remember { Nav({ Editor.guardaYa(); pantalla = it }) { m -> scope.launch { snack.showSnackbar(m, withDismissAction = true) } } }
    // La columna: la portada, tus obras por menú (como en el móvil) y lo demás
    val grupos = EstadoObra.values().map { it to e.obrasDe(it) }.filter { it.second.isNotEmpty() }
    val obras = grupos.flatMap { it.second }
    val entradas = buildList {
        add(Entrada("Portada", grupo = "Cobalto"))
        grupos.forEach { (est, l) -> l.forEach { o -> add(Entrada(o.s("titulo").ifBlank { "Sin título" }, grupo = est.plural)) } }
        add(Entrada("Papelera", grupo = "Más")); add(Entrada("Ajustes", grupo = "Más"))
    }
    val sel = when (val p = pantalla) {
        Pantalla.Portada -> 0
        is Pantalla.Obra -> 1 + obras.indexOfFirst { it.id == p.pid }.coerceAtLeast(0)
        Pantalla.Papelera -> 1 + obras.size
        Pantalla.Ajustes -> 2 + obras.size
    }
    Row(Modifier.fillMaxSize()) {
        Lateral("Cobalto", entradas, sel) { i ->
            nav.ir(when {
                i == 0 -> Pantalla.Portada
                i <= obras.size -> Pantalla.Obra(obras[i - 1].id)
                i == obras.size + 1 -> Pantalla.Papelera
                else -> Pantalla.Ajustes
            })
        }
        Filete(1.dp, Line, Modifier.fillMaxHeight().width(1.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Crossfade(pantalla, label = "pantalla") { p ->
                when (p) {
                    Pantalla.Portada -> PantPortada(nav)
                    is Pantalla.Obra -> PantObra(p.pid, p.eid, nav)
                    Pantalla.Papelera -> PantPapelera(nav)
                    Pantalla.Ajustes -> PantAjustes(nav)
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}
