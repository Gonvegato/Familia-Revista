package com.gonzalo.cobalto.pc

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gonzalo.cobalto.Escritos
import com.gonzalo.familia.pc.revista.*

/**
 * Lo que está abierto encima de una pantalla (una ficha desde el editor o el árbol, un suceso de la línea, una unión…): su id y
 * si se está editando. Se abre para leer; «Editar» (o un clic en la página) lo pasa a editar, y Esc o «Listo» vuelven a leer.
 */
class AbreDoc {
    var id by mutableStateOf<String?>(null)
        private set
    var editando by mutableStateOf(false)
    /** Lo recién creado: el foco va al primer campo. */
    var nuevo by mutableStateOf(false)
        private set
    fun ver(x: String) { id = x; editando = false; nuevo = false }
    fun editar(x: String, esNuevo: Boolean = false) { id = x; editando = true; nuevo = esNuevo }
    fun cierra() { id = null; editando = false; nuevo = false }
}

/** La hoja de lo abierto en [a] (si hay algo), con su lectura o su formulario. */
@Composable
fun VentanaDoc(e: Escritos, a: AbreDoc, nav: Nav, abrirEscena: (String) -> Unit, verArbol: ((String) -> Unit)? = null) {
    e.version
    val id = a.id ?: return
    val d = e.get(id)
    if (d == null) { LaunchedEffect(id) { a.cierra() }; return }
    val borrar = { e.borra(id); a.cierra(); nav.aviso("En la papelera (30 días)") }
    val acc = AccionesLectura(editar = { a.editando = true }, verDoc = { a.ver(it) }, abrirEscena = { a.cierra(); abrirEscena(it) }, verArbol = verArbol?.let { v -> { x: String -> a.cierra(); v(x) } })
    Hoja(onCerrar = { a.cierra() }, ancho = 980.dp, alCerrarConEsc = { if (a.editando) { a.editando = false; true } else false }) {
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(start = 44.dp, end = 44.dp, top = 18.dp, bottom = 30.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { EnlaceRevista("Cerrar ✕") { a.cierra() } }
                key(id, a.editando) {
                    when (d.t) {
                        Escritos.T_FICHA -> if (a.editando) EdicionFicha(e, d, onListo = { a.editando = false }, onBorrar = borrar, focoNombre = a.nuevo) else LecturaFicha(e, d, acc)
                        Escritos.T_NOTA -> if (a.editando) EdicionNota(e, d, onListo = { a.editando = false }, onBorrar = borrar, focoTitulo = a.nuevo) else LecturaNota(e, d, acc)
                        Escritos.T_HITO -> if (a.editando) EdicionHito(e, d, onListo = { a.editando = false }, onBorrar = borrar) else LecturaHito(e, d, acc)
                        Escritos.T_REL -> if (a.editando) EdicionRelacion(e, d, onListo = { a.editando = false }, onBorrar = borrar, focoTitulo = a.nuevo) else LecturaRelacion(e, d, acc)
                        Escritos.T_RAMA -> EdicionRama(e, d, onListo = { a.cierra() }, onBorrar = borrar)
                        else -> LaunchedEffect(id) { a.cierra() }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 6.dp))
        }
    }
}
