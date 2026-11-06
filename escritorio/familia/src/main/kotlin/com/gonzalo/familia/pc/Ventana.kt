package com.gonzalo.familia.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * La ventana de una app de la familia en el ordenador: arranca la cuenta, sigue a la ventana (escucha en directo mientras está abierta,
 * sube lo pendiente al cerrar), recuerda su tamaño y su sitio, y si aún no has entrado en la cuenta enseña cómo entrar.
 */
fun appDeLaFamilia(app: String, titulo: String, icono: Painter? = null, alIniciar: () -> Unit = {}, alCerrar: () -> Unit = {}, contenido: @Composable () -> Unit) {
    // Una sola copia de cada app: si ya estaba abierta, sale al frente aquella y esta no abre nada
    if (!Instancia.unica(Sitio.de(app))) return
    ventanaDeLaFamilia(app, titulo, icono, alIniciar, alCerrar, contenido)
}

private fun ventanaDeLaFamilia(app: String, titulo: String, icono: Painter?, alIniciar: () -> Unit, alCerrar: () -> Unit, contenido: @Composable () -> Unit) = application {
    remember { LaFamilia.inicia(app); Apariencia.inicia(app); runCatching { alIniciar() }; true }
    val guardada = remember { leeVentana(app) }
    val estado = rememberWindowState(
        placement = if (guardada["max"] == true) WindowPlacement.Maximized else WindowPlacement.Floating,
        position = guardada["x"]?.let { WindowPosition(it.comoLong().toInt().dp, guardada["y"].comoLong().toInt().dp) } ?: WindowPosition.PlatformDefault,
        size = DpSize((guardada["w"]?.comoLong()?.toInt() ?: 1280).dp, (guardada["h"]?.comoLong()?.toInt() ?: 840).dp)
    )
    // Cerrar (y, si había una versión nueva preparada, ponerla; con [reiniciar], volver a abrir la app ya nueva)
    fun cierra(reiniciar: Boolean = false) {
        guardaVentana(app, estado.placement == WindowPlacement.Maximized, estado.position, estado.size)
        runCatching { alCerrar() }
        LaFamilia.alCerrar()
        val programa = runCatching { Actualizador.ponLaPreparada(app) }.getOrNull()
        if (reiniciar) {
            // (la copia nueva no debe encontrar el cerrojo de esta, que ya se va: si no, creería que hay otra abierta y no se abriría)
            Instancia.suelta()
            Actualizador.abreOtraVez(programa ?: Actualizador.instalada()?.let { lanzadorDe(app, it) } ?: return exitApplication())
        }
        exitApplication()
    }
    Window(
        onCloseRequest = { cierra() },
        title = titulo, state = estado, icon = icono,
        // Pantalla completa: F11 (y en el Mac también ⌃⌘F, el de siempre allí); Esc no sale, que lo usan las apps
        onPreviewKeyEvent = { k ->
            val mac = Sitio.sistema == Sitio.Sistema.MAC
            if (k.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && (k.key == androidx.compose.ui.input.key.Key.F11 ||
                    (mac && k.isCtrlPressed && k.isMetaPressed && k.key == androidx.compose.ui.input.key.Key.F))) {
                VentanaFamilia.pantallaCompleta = !VentanaFamilia.pantallaCompleta; true
            } else false
        }
    ) {
        // (lo pide el atajo o una app: el modo concentración de Cobalto, el lector de Marfil, la sala de Hiedra…)
        LaunchedEffect(VentanaFamilia.pantallaCompleta) {
            val quiere = VentanaFamilia.pantallaCompleta
            if (quiere && estado.placement != WindowPlacement.Fullscreen) { VentanaFamilia.antes = estado.placement; estado.placement = WindowPlacement.Fullscreen }
            else if (!quiere && estado.placement == WindowPlacement.Fullscreen) estado.placement = VentanaFamilia.antes
        }
        // Actualizarse sola: al poco de abrir y luego cada 6 horas mira tu kDrive; lo nuevo se baja y se pone al cerrar
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(20_000)
            while (true) {
                withContext(Dispatchers.IO) { runCatching { Actualizador.automatico(app) } }
                kotlinx.coroutines.delay(6 * 3_600_000L)
            }
        }
        DisposableEffect(Unit) {
            // Si otra copia de la app pide salir al frente (la abriste otra vez, o desde una hermana)
            Instancia.alFrente = {
                java.awt.EventQueue.invokeLater {
                    if (window.extendedState and java.awt.Frame.ICONIFIED != 0) window.extendedState = window.extendedState and java.awt.Frame.ICONIFIED.inv()
                    window.isVisible = true; window.toFront(); window.requestFocus()
                }
            }
            LaFamilia.alVerse()
            val oyente = object : java.awt.event.WindowAdapter() {
                override fun windowGainedFocus(e: java.awt.event.WindowEvent?) { LaFamilia.alVerse() }
            }
            window.addWindowFocusListener(oyente)
            onDispose { window.removeWindowFocusListener(oyente) }
        }
        TemaFamilia {
            TextoAireado {
                Column(Modifier.fillMaxSize().background(Paper)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (!LaFamilia.dentro) PantallaEntrar(titulo) else contenido()
                    }
                    Actualizador.preparada?.let { p -> BandaVersionNueva(titulo, p.version) { cierra(reiniciar = true) } }
                }
            }
        }
    }
}

/** La ventana de la app, para lo que las pantallas necesiten de ella (de momento, la pantalla completa). */
object VentanaFamilia {
    /** Pantalla completa (F11; en el Mac también ⌃⌘F). Una app puede ponerla y quitarla (p. ej. un modo concentración). */
    var pantallaCompleta by mutableStateOf(false)
    internal var antes: WindowPlacement = WindowPlacement.Floating
}

/** El programa de una app instalada: `<App>/bin/<App>` en Linux; el `<App>.app` en el Mac. */
private fun lanzadorDe(app: String, sitio: File): File =
    if (Sitio.sistema == Sitio.Sistema.MAC) sitio else File(sitio, "bin/" + app.replaceFirstChar { it.uppercase() })

/** Abajo, fina: hay una versión nueva ya bajada; se pone sola al cerrar, o ahora mismo reiniciando. */
@Composable
private fun BandaVersionNueva(titulo: String, version: String, reiniciar: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Sand)) {
        Filete(1.dp, Line)
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$titulo $version está lista: se pone sola al cerrar.", Modifier.weight(1f), color = Charcoal, fontFamily = Body, fontSize = 13.sp)
            EnlaceRevista("Reiniciar ahora", acento = true) { reiniciar() }
        }
    }
}

/** El estilo por defecto de los textos, con el interlineado aireado (el de las apps de Android). */
@Composable
fun TextoAireado(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(lineHeight = androidx.compose.ui.unit.TextUnit(1.45f, androidx.compose.ui.unit.TextUnitType.Em)), content = content)

private fun archivoVentana(app: String) = File(Sitio.de(app), "ventana.json")
private fun leeVentana(app: String): Map<String, Any?> = runCatching { Json.lee(archivoVentana(app).readText()).comoMapa() }.getOrDefault(emptyMap())
private fun guardaVentana(app: String, max: Boolean, p: WindowPosition, s: DpSize) {
    val m = LinkedHashMap<String, Any?>()
    m["max"] = max
    if (p is WindowPosition.Absolute) { m["x"] = p.x.value.toLong(); m["y"] = p.y.value.toLong() }
    m["w"] = s.width.value.toLong(); m["h"] = s.height.value.toLong()
    runCatching { archivoVentana(app).writeText(Json.escribe(m)) }
}

/** Entrar en la cuenta de la familia (la misma de las apps del móvil). Una vez: las otras tres de este ordenador entran solas. */
@Composable
fun PantallaEntrar(titulo: String) {
    var usuario by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var entrando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun entra() {
        if (entrando) return
        entrando = true; error = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { LaFamilia.entrar(usuario, contrasena) } }
            entrando = false
            r.exceptionOrNull()?.let { error = it.message ?: "No se ha podido entrar." }
        }
    }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 520.dp).padding(horizontal = 32.dp, vertical = 56.dp)) {
            Cabecera(88.sp, texto = titulo.uppercase())
            FileteCabecera()
            Spacer(Modifier.height(28.dp))
            Antetitulo("La cuenta de la familia")
            Spacer(Modifier.height(10.dp))
            Cuerpo("La misma que en el móvil. Todo lo tuyo está en la cuenta: lo que hagas aquí lo verás en tus otros dispositivos y en tus otras apps. " +
                "Entra una vez: Azabache, Cobalto, Hiedra y Marfil de este ordenador entran solas.", tam = 15)
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(contrasena, { contrasena = it }, label = { Text("Contraseña") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { entra() }))
            if (error.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(error, color = Acento, fontFamily = Body, fontSize = 14.sp) }
            Spacer(Modifier.height(20.dp))
            BotonAcento(if (entrando) "Entrando…" else "Entrar") { entra() }
            Spacer(Modifier.height(16.dp))
            Cuerpo("La contraseña no se guarda. En este ordenador queda solo la sesión, en: ${LaFamilia.nombreLlavero}.", tam = 12)
        }
    }
}

/** Ajustes → Cuenta: quién eres, cómo va la sincronización y salir. Lo mismo en las cuatro apps. */
@Composable
fun AjustesCuenta(app: String) {
    LaFamilia.cambios
    val scope = rememberCoroutineScope()
    var sincronizando by remember { mutableStateOf(false) }
    Column {
        Antetitulo("Cuenta")
        Spacer(Modifier.height(8.dp))
        MagFact("Usuario", LaFamilia.usuario.ifBlank { "—" })
        MagFact("Estado", LaFamilia.estado.ifBlank { "—" })
        MagFact("Por subir", runCatching { LaFamilia.familia.pendientes.toString() }.getOrDefault("0"))
        MagFact("Sesión guardada en", LaFamilia.nombreLlavero)
        MagFact("Este ordenador", "${Sitio.nombreEquipo} · ${Sitio.dispositivo(app)}")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonSuave(if (sincronizando) "Sincronizando…" else "Sincronizar ahora") {
                if (!sincronizando) { sincronizando = true; scope.launch { withContext(Dispatchers.IO) { runCatching { LaFamilia.sincronizaYa() } }; sincronizando = false } }
            }
            BotonSuave("Salir de la cuenta", peligro = true) { scope.launch { withContext(Dispatchers.IO) { runCatching { LaFamilia.salir() } } } }
        }
        Spacer(Modifier.height(8.dp))
        Cuerpo("Salir aquí no saca a las otras apps de este ordenador; sí olvida lo de la cuenta en esta.", tam = 12)
    }
}

/** Ajustes → Acerca de: la versión, y si hay una nueva en tu kDrive, instalarla. */
@Composable
fun AjustesVersion(app: String, nombre: String) {
    val scope = rememberCoroutineScope()
    var aviso by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf<Actualizador.Nueva?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    val sola = remember { Actualizador.sePoneSola() }
    LaunchedEffect(Unit) { if (!sola) nueva = withContext(Dispatchers.IO) { runCatching { Actualizador.busca(app) }.getOrNull() } }
    Column {
        Antetitulo("Versión")
        Spacer(Modifier.height(8.dp))
        MagFact(nombre, Actualizador.version)
        MagFact("Sistema", if (Sitio.sistema == Sitio.Sistema.MAC) "macOS" else "Linux")
        Actualizador.instalada()?.let { MagFact("Instalada en", it.absolutePath) }
        Spacer(Modifier.height(12.dp))
        val lista = Actualizador.preparada
        if (sola) {
            Cuerpo("Se actualiza sola: al abrirla y cada 6 horas mira tu kDrive («Mi carpeta/versiones»); si hay una versión nueva, la baja y se pone sola al cerrar la app (o al pulsar «Reiniciar ahora» en la banda de abajo).", tam = 14)
            Spacer(Modifier.height(10.dp))
            if (lista != null) Cuerpo("La $nombre ${lista.version} ya está bajada: se pone al cerrar.", tam = 14)
            else BotonSuave(if (trabajando) "Buscando…" else "Buscar ahora") {
                if (!trabajando) { trabajando = true; scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Actualizador.automatico(app) } }
                    trabajando = false
                    aviso = r.fold({ when {
                        it != null -> "La ${it.version} está lista: se pone sola al cerrar."
                        !KDrive().activo -> "Pon tu kDrive en Azabache (Ajustes → Servicios) para que se actualice sola."
                        else -> Actualizador.estado.ifBlank { "Tienes la última." }
                    } }, { it.message ?: "No se ha podido mirar." })
                } }
            }
        } else {
            val n = nueva
            if (n != null) {
                Cuerpo("Hay una versión nueva en tu kDrive: la ${n.version}.", tam = 14)
                Spacer(Modifier.height(8.dp))
                BotonAcento(if (trabajando) "Bajando…" else "Bajar e instalar la ${n.version}") {
                    if (!trabajando) { trabajando = true; scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { Actualizador.instala(n) } }
                        trabajando = false
                        aviso = r.fold({ "Está en ${it.parent}. " + if (Sitio.sistema == Sitio.Sistema.MAC) "Arrastra $nombre a Aplicaciones (encima de la que tienes)." else "Instálala con el instalador que se ha abierto." }, { it.message ?: "No se ha podido bajar." })
                    } }
                }
            } else {
                BotonSuave(if (trabajando) "Buscando…" else "Buscar versión nueva") {
                    if (!trabajando) { trabajando = true; scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { Actualizador.busca(app) } }
                        trabajando = false
                        nueva = r.getOrNull()
                        aviso = r.fold({ if (it == null) (if (KDrive().activo) "Tienes la última." else "Pon tu kDrive en Azabache (Ajustes → Servicios) para que se actualice sola.") else "" }, { it.message ?: "No se ha podido mirar." })
                    } }
                }
            }
            if (Actualizador.conDeb()) { Spacer(Modifier.height(8.dp)); Cuerpo("Instalada con el .deb (del sistema): las versiones nuevas se instalan con su instalador. Con el .tar.gz (y su instalar.sh) se actualiza sola.", tam = 12) }
        }
        if (aviso.isNotBlank()) { Spacer(Modifier.height(8.dp)); Cuerpo(aviso, tam = 13) }
    }
}
