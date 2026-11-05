package com.gonzalo.familia.pc.revista

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Una ventana encima de la app, con el aspecto de la revista: un titular, lo que se pase y, abajo, sus botones. */
@Composable
fun Dialogo(titulo: String, onCerrar: () -> Unit, ancho: Dp = 560.dp, botones: @Composable RowScope.() -> Unit = {}, contenido: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val forma = esquina(10.dp)
        Column(Modifier.width(ancho).heightIn(max = 760.dp).clip(forma).background(Paper).border(1.dp, Line, forma).padding(28.dp)) {
            Text(titulo, color = Charcoal, fontFamily = Display, fontSize = 30.sp, lineHeight = 32.sp)
            Spacer(Modifier.height(6.dp))
            Filete(2.dp)
            Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f, fill = false), content = contenido)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, content = botones)
        }
    }
}

/** Elegir un archivo con la ventana del sistema. [extensiones]: las que valen (vacío: todas). Bloquea hasta que eliges. */
fun eligeArchivo(titulo: String, extensiones: List<String> = emptyList()): File? {
    val d = FileDialog(null as Frame?, titulo, FileDialog.LOAD)
    if (extensiones.isNotEmpty()) {
        d.setFilenameFilter { _, n -> extensiones.any { n.lowercase().endsWith(".$it") } }
        // (en Linux, el filtro de arriba no siempre se aplica: se pone también el patrón)
        d.file = extensiones.joinToString(";") { "*.$it" }
    }
    d.isVisible = true
    val f = d.file?.takeIf { !it.contains('*') } ?: return null
    return File(d.directory, f).takeIf { it.isFile }
}

/** Guardar un archivo con la ventana del sistema. */
fun eligeDondeGuardar(titulo: String, nombre: String): File? {
    val d = FileDialog(null as Frame?, titulo, FileDialog.SAVE)
    d.file = nombre
    d.isVisible = true
    val f = d.file ?: return null
    return File(d.directory, f)
}
