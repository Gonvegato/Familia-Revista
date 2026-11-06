package com.gonzalo.hiedra.pc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.Sitio
import com.gonzalo.familia.pc.revista.*
import java.io.File

/** Elegir una carpeta con la ventana del sistema (en el Mac la suya; en Linux la de Java, que sí deja elegir carpetas). */
fun eligeCarpeta(titulo: String): File? {
    if (Sitio.sistema == Sitio.Sistema.MAC) {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        try {
            val d = java.awt.FileDialog(null as java.awt.Frame?, titulo, java.awt.FileDialog.LOAD)
            d.isVisible = true
            val f = d.file ?: return null
            return File(d.directory, f).takeIf { it.isDirectory }
        } finally { System.setProperty("apple.awt.fileDialogForDirectories", "false") }
    }
    val c = javax.swing.JFileChooser(System.getProperty("user.home")).apply { dialogTitle = titulo; fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY }
    return if (c.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) c.selectedFile?.takeIf { it.isDirectory } else null
}

/** Ajustes → Emulación: tus carpetas de ROMs de este ordenador y con qué se juega a cada sistema. */
@Composable
fun AjustesEmulacion(nav: Nav) {
    Pc.version
    var cambios by remember { mutableStateOf(0) }
    val carpetas = remember(cambios) { Emulacion.carpetas() }
    val roms = Pc.yaMirado().orEmpty().filter { it.fuente == Fuente.EMULACION }
    val porSistema = roms.groupBy { it.sistema }
    var verTodos by remember { mutableStateOf(false) }
    var abierto by remember { mutableStateOf<String?>(null) }
    var propio by remember { mutableStateOf<String?>(null) }
    Column {
        Antetitulo("Carpetas de ROMs")
        Spacer(Modifier.height(6.dp))
        Cuerpo("Las carpetas de este ordenador donde guardas tus ROMs (no viajan con la cuenta: como en el móvil, cada dispositivo tiene las suyas). Hiedra las recorre (también las de dentro) y reconoce cada juego por su carpeta («gba», «PlayStation 2»…) o por su extensión. Una ROM que también tienes en el móvil es el mismo juego, con sus horas.", tam = 14)
        Spacer(Modifier.height(8.dp))
        if (carpetas.isEmpty()) Cuerpo("Todavía no has añadido ninguna.", tam = 13)
        carpetas.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(c.path, Modifier.weight(1f), color = if (c.isDirectory) Charcoal else Muted, fontFamily = Display, fontSize = 18.sp, maxLines = 1)
                if (!c.isDirectory) Etiqueta("No está", Modifier.padding(end = 12.dp), color = Acento)
                EnlaceRevista("Quitar") { Emulacion.quitaCarpeta(c); cambios++; Pc.refresca(escanear = true) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            EnlaceRevista("+ Añadir carpeta", acento = true) {
                eligeCarpeta("Elige la carpeta de tus ROMs")?.let { Emulacion.anadeCarpeta(it); cambios++; Pc.refresca(escanear = true) { nav.aviso("${Pc.yaMirado().orEmpty().count { j -> j.fuente == Fuente.EMULACION }} ROMs en tus carpetas") } }
            }
            if (carpetas.isNotEmpty()) EnlaceRevista(if (Pc.mirando) "Buscando…" else "Buscar ROMs nuevas") { Pc.refresca(escanear = true) { nav.aviso("${Pc.yaMirado().orEmpty().count { j -> j.fuente == Fuente.EMULACION }} ROMs en tus carpetas") } }
            if (roms.isNotEmpty()) Etiqueta("${roms.size} ROMs")
        }
        Spacer(Modifier.height(18.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
        Antetitulo("Sistemas")
        Spacer(Modifier.height(6.dp))
        Cuerpo("Con qué se abre cada sistema: lo eliges tú; si no, el mejor que tengas instalado (en el sistema o en Flatpak). Los emuladores se abren ya a pantalla completa y con el juego. RetroArch necesita el núcleo de ese sistema (en CachyOS: el paquete libretro-… o el «Actualizador de núcleos» de RetroArch).", tam = 14)
        Spacer(Modifier.height(6.dp))
        val lista = Emulacion.SISTEMAS.filter { verTodos || it.id in porSistema }
        if (lista.isEmpty()) Cuerpo("Cuando haya ROMs en tus carpetas, salen aquí sus sistemas.", tam = 13)
        lista.forEach { s ->
            val e = remember(s.id, cambios, Pc.version) { Emulacion.elegida(s.id) }
            val n = porSistema[s.id]?.size ?: 0
            FilaRevista(s.nombre, "${if (n == 0) "sin juegos" else if (n == 1) "1 juego" else "$n juegos"} · ${e?.nombre ?: "sin emulador instalado"}",
                derecha = { Text(if (e != null) "LISTO" else "FALTA", color = if (e != null) Charcoal else Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.padding(start = 10.dp)) }) {
                abierto = if (abierto == s.id) null else s.id
            }
            if (abierto == s.id) {
                val ops = remember(s.id, cambios) { Emulacion.opciones(s.id) }
                val elegida = Emulacion.eleccion(s.id)
                Column(Modifier.padding(start = 16.dp, bottom = 12.dp)) {
                    MagChoice(listOf("Automático") + ops.map { it.nombre + if (it.listo) "" else " (no está)" }, if (elegida.isBlank()) 0 else ops.indexOfFirst { it.id == elegida } + 1, "Con qué se juega") { i ->
                        Emulacion.elige(s.id, if (i == 0) "" else ops[i - 1].id); cambios++
                    }
                    EnlaceRevista("Otro programa…") { propio = s.id }
                }
            }
        }
        EnlaceRevista(if (verTodos) "Ver solo los míos" else "Ver los ${Emulacion.SISTEMAS.size} sistemas") { verTodos = !verTodos }
    }
    propio?.let { sis -> DialogoPropio(sis, { cambios++ }) { propio = null } }
}

/** Un emulador que Hiedra no conoce, para un sistema: el programa y sus opciones, con {rom} donde va el juego. */
@Composable
private fun DialogoPropio(sistema: String, alGuardar: () -> Unit, onCerrar: () -> Unit) {
    val antes = remember { Emulacion.propio(sistema) }
    var programa by remember { mutableStateOf(antes?.first.orEmpty()) }
    var args by remember { mutableStateOf(antes?.second ?: "{rom}") }
    Dialogo("Otro programa para ${Emulacion.sistema(sistema)?.nombre ?: sistema}", onCerrar, botones = {
        BotonSuave("Cancelar") { onCerrar() }
        BotonAcento("Guardar", modifier = Modifier.width(130.dp), alto = 44.dp) {
            if (programa.isNotBlank()) { Emulacion.ponPropio(sistema, programa, args); alGuardar() }
            onCerrar()
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(programa.ifBlank { "Ningún programa elegido" }, Modifier.weight(1f), color = Muted, fontFamily = Body, fontSize = 13.sp, maxLines = 2)
            BotonSuave("Elegir…") { eligeArchivo("Elige el emulador (su ejecutable o su .AppImage)")?.let { programa = it.path } }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(args, { args = it }, Modifier.fillMaxWidth(), label = { Text("Opciones ({rom} es el juego)") }, singleLine = true)
        Cuerpo("Por ejemplo, «-f {rom}». Hiedra cuenta el tiempo hasta que el programa se cierra.", tam = 12)
    }
}

/** Ajustes → Sala y mando: entrar en la sala, cómo se ve, qué hace al jugar, y los mandos conectados. */
@Composable
fun AjustesSalaMando(alSala: () -> Unit) {
    Column {
        Antetitulo("La sala")
        Spacer(Modifier.height(6.dp))
        Cuerpo("Hiedra para la tele: a pantalla completa, con portadas grandes y todo con el mando (también con el teclado y el ratón). LB y RB cambian de sección, A abre y juega, B vuelve, X busca, Y da las opciones de un juego, ☰ el menú (y desde él, suspender o apagar el ordenador) y ⧉ vuelve a la ventana.", tam = 14)
        Spacer(Modifier.height(10.dp))
        BotonAcento("▶  Entrar en la sala", alto = 48.dp) { alSala() }
        Spacer(Modifier.height(12.dp))
        Fila("Abrir Hiedra ya en la sala", "Al encender el PC de jugar, directamente la sala (o abriéndola con «--sala», lo que va en el inicio de sesión)") { Interruptor(AjustesSala.alAbrir) { AjustesSala.ponAlAbrir(it) } }
        Fila("Sala oscura", "Los colores de tu Hiedra, en su versión de noche (mejor para la tele). Si no, como la app") { Interruptor(AjustesSala.oscura) { AjustesSala.ponOscura(it) } }
        Fila("Al jugar, Hiedra se aparta", "Se minimiza para que el juego quede delante, y vuelve sola al cerrarlo") { Interruptor(AjustesSala.apartar) { AjustesSala.ponApartar(it) } }
        Spacer(Modifier.height(18.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
        Antetitulo("Mandos")
        Spacer(Modifier.height(6.dp))
        val l = Mando.mandos
        if (l.isEmpty()) Cuerpo("Ningún mando conectado ahora. " + (if (Mando.estado == "Listo" || Mando.estado == "Mirando…") "Enchúfalo o conéctalo por Bluetooth: sale solo." else Mando.estado), tam = 14)
        l.forEach { m -> MagFact(m.nombre, when (m.tipo) { Mando.Tipo.XBOX -> "Xbox"; Mando.Tipo.PLAYSTATION -> "PlayStation"; Mando.Tipo.NINTENDO -> "Nintendo"; else -> "Mando" }) }
        Spacer(Modifier.height(8.dp))
        Cuerpo("Los botones van por su sitio: el de abajo pulsa y el de la derecha vuelve (en el de Switch, B pulsa y A vuelve, como en Steam). " +
            (if (Sitio.sistema == Sitio.Sistema.LINUX) "Si un mando de PlayStation o de Switch no aparece, le faltan permisos: vienen con Steam (o con el paquete game-devices-udev). Con Steam abierto, si Steam convierte el mando en teclado («configuración de escritorio»), Hiedra lo entiende igual y no cuenta dos veces cada pulsación."
            else "En el Mac, si un mando que ya estaba conectado al abrir Hiedra no aparece, desconéctalo y vuelve a conectarlo."), tam = 12)
        Spacer(Modifier.height(18.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
        Antetitulo("Con el teclado")
        Spacer(Modifier.height(6.dp))
        listOf("Moverse" to "Flechas", "Abrir · jugar" to "Intro", "Volver" to "Esc o Retroceso", "Buscar" to "F o /", "Opciones" to "Espacio",
            "Sección anterior · siguiente" to "Q · E (o RePág · AvPág)", "Letra anterior · siguiente" to "Inicio · Fin", "Menú" to "S o F10", "Salir de la sala" to "F11").forEach { (a, b) -> MagFact(a, b) }
    }
}
