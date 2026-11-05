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
fun appDeLaFamilia(app: String, titulo: String, icono: Painter? = null, contenido: @Composable () -> Unit) = application {
    remember { LaFamilia.inicia(app); Apariencia.inicia(app); true }
    val guardada = remember { leeVentana(app) }
    val estado = rememberWindowState(
        placement = if (guardada["max"] == true) WindowPlacement.Maximized else WindowPlacement.Floating,
        position = guardada["x"]?.let { WindowPosition(it.comoLong().toInt().dp, guardada["y"].comoLong().toInt().dp) } ?: WindowPosition.PlatformDefault,
        size = DpSize((guardada["w"]?.comoLong()?.toInt() ?: 1280).dp, (guardada["h"]?.comoLong()?.toInt() ?: 840).dp)
    )
    Window(
        onCloseRequest = {
            guardaVentana(app, estado.placement == WindowPlacement.Maximized, estado.position, estado.size)
            LaFamilia.alCerrar()
            exitApplication()
        },
        title = titulo, state = estado, icon = icono
    ) {
        DisposableEffect(Unit) {
            LaFamilia.alVerse()
            val oyente = object : java.awt.event.WindowAdapter() {
                override fun windowGainedFocus(e: java.awt.event.WindowEvent?) { LaFamilia.alVerse() }
            }
            window.addWindowFocusListener(oyente)
            onDispose { window.removeWindowFocusListener(oyente) }
        }
        TemaFamilia {
            TextoAireado {
                Box(Modifier.fillMaxSize().background(Paper)) {
                    if (!LaFamilia.dentro) PantallaEntrar(titulo) else contenido()
                }
            }
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
    LaunchedEffect(Unit) { nueva = withContext(Dispatchers.IO) { runCatching { Actualizador.busca(app) }.getOrNull() } }
    Column {
        Antetitulo("Versión")
        Spacer(Modifier.height(8.dp))
        MagFact(nombre, Actualizador.version)
        MagFact("Sistema", if (Sitio.sistema == Sitio.Sistema.MAC) "macOS" else "Linux")
        Spacer(Modifier.height(12.dp))
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
        if (aviso.isNotBlank()) { Spacer(Modifier.height(8.dp)); Cuerpo(aviso, tam = 13) }
    }
}
