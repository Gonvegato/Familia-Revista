package com.gonzalo.cobalto

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val VERSION = "0.6.7"

/** «Ajustes»: Apariencia (como la de Atrio), cuenta de Atrio y pareja, actualizaciones, escritura y copias. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantEstilo(alVolver: () -> Unit, pestanaInicial: Int = 0) {
    val ctx = LocalContext.current
    val e = remember { Escritos.get(ctx) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var papelera by remember { mutableStateOf(false) }
    var hojaPagina by remember { mutableStateOf(false) }
    var atajos by remember { mutableStateOf(false) }
    val pagina = remember { PaginaEscribir.de(ctx) }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        if (u != null) mensaje = runCatching { ctx.contentResolver.openOutputStream(u)!!.use { it.write(e.volcado().toString().toByteArray()) }; "Copia guardada" }.getOrElse { "No se pudo guardar la copia" }
    }
    var pestana by rememberSaveable { mutableIntStateOf(pestanaInicial) }
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        // Dirección C: la cabecera en un bloque de tinta con «AJUSTES.» en grotesca y las pestañas en bloques
        Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 14.dp)) {
            EnlaceRevista("← Thot", Modifier.padding(top = 6.dp), color = Paper.copy(alpha = 0.75f)) { alVolver() }
            NombreConPunto("Ajustes", 52.sp, Paper, Modifier.padding(top = 2.dp))
        }
        PestanasRevista(listOf("Apariencia", "Escritura", "Cuenta", "Avanzado"), pestana) { pestana = it }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))

        when (pestana) {
            0 -> SeccionApariencia()
            1 -> {
                Antetitulo("Meta del día", Modifier.padding(bottom = 4.dp))
                var meta by remember { mutableIntStateOf(MetaDiaria.de(ctx)) }
                val metas = remember { (listOf(250, 500, 750, 1000, 1500, 2000, 3000) + MetaDiaria.de(ctx)).distinct().sorted() }
                MagChoice(metas.map { "${cifraE(it)} palabras" }, metas.indexOf(meta).coerceAtLeast(0), "Palabras al día") { i -> meta = metas[i]; MetaDiaria.pon(ctx, meta) }
                Text("Una para todas tus obras y la misma en todos tus dispositivos. La portada enseña cómo vas hoy, cuántos de los últimos 30 días la cumpliste y la semana con la meta como raya.",
                    color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.height(10.dp))
                Filete(2.dp)
                Spacer(Modifier.height(12.dp))
                Antetitulo("Escritura", Modifier.padding(bottom = 4.dp))
                val enPapelera = remember(e.version) { e.papelera().size }
                MagLink("La página del editor", "Letra, tamaño, interlineado, sangría, máquina de escribir y concentración") { hojaPagina = true }
                MagLink("Atajos de teclado", "Con un teclado físico, como el de la tableta") { atajos = true }
                MagLink("Papelera" + if (enPapelera > 0) " · $enPapelera" else "", "Lo borrado se guarda 30 días, también lo borrado desde el PC") { papelera = true }
                Filete(1.dp, Line)
                Text("Thot escribe en los mismos documentos que Escribir de Atrio y de Atrio del PC: tus obras salen aquí tal cual, y lo que cambies aquí llega allí.",
                    color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(14.dp))
                Filete(2.dp)
                Spacer(Modifier.height(12.dp))
                Antetitulo("Hermanas", Modifier.padding(bottom = 4.dp))
                Text(if (Hermanas.hayMarfil(ctx)) "Seshat está en este móvil: los capítulos que das por terminados se leen allí con su lector («Leer en Seshat», en el manuscrito y en Exportar). Jonsu enseña en su Diario las palabras de cada día."
                    else "Los capítulos que das por terminados se publican en tu cuenta: Seshat los lee con su lector y Jonsu enseña en su Diario las palabras de cada día. Seshat no está en este móvil.",
                    color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
            }
            2 -> {
                Antetitulo("Cuenta de Atrio", Modifier.padding(bottom = 4.dp))
                SeccionCuenta()
            }
            else -> {
                com.gonzalo.familia.UltimoFallo("Thot", Charcoal, Muted, Body) { Antetitulo("Último fallo", Modifier.padding(bottom = 4.dp)) }
                Antetitulo("kDrive y actualizaciones", Modifier.padding(bottom = 4.dp))
                SeccionActualizaciones()
                Spacer(Modifier.height(14.dp))
                Filete(2.dp)
                Spacer(Modifier.height(12.dp))
                Antetitulo("Copias", Modifier.padding(bottom = 4.dp))
                SeccionCopias(e) { mensaje = it }
                Spacer(Modifier.height(8.dp))
                MagLink("Guardar copia de mis obras", "Un archivo con todo lo escrito (obras, capítulos, escenas, fichas, notas…), también la papelera") { exportar.launch("cobalto-obras.json") }
                Text("Cada obra se puede exportar aparte (texto, Markdown, Word, libro o PDF) desde «Exportar», dentro de la obra.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
                mensaje?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        }

        Spacer(Modifier.height(26.dp))
        Filete(3.dp)
        Spacer(Modifier.height(10.dp))
        Etiqueta("Thot $VERSION")
        Text("Thot es hermana de Jonsu, Seshat y Bastet. El tesauro de sinónimos es el de RLA-ES (LGPL 2.1).", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(30.dp))
        }
    }
    if (papelera) PapeleraEscribir(e, onCerrar = { papelera = false })
    if (hojaPagina) HojaPagina(pagina, androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700) { hojaPagina = false }
    if (atajos) AtajosDialogo { atajos = false }
}

/** Una casilla de texto con el estilo de la revista: etiqueta en versalitas, texto en cursiva sobre un filete. */
@Composable
fun CampoRevista(etiqueta: String, valor: String, pista: String, secreto: Boolean = false, alCambiar: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(etiqueta.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.width(96.dp).padding(bottom = 3.dp))
            Box(Modifier.weight(1f)) {
                if (valor.isEmpty()) Text(pista, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
                BasicTextField(valor, alCambiar, singleLine = true, textStyle = TextStyle(color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp),
                    cursorBrush = SolidColor(Acento), visualTransformation = if (secreto) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (secreto) KeyboardType.Password else KeyboardType.Text, autoCorrect = false), modifier = Modifier.fillMaxWidth())
            }
        }
        Filete(2.dp)
    }
}

/** «Cuenta de Atrio»: entrar con tu usuario y contraseña de Atrio, ver cómo va la sincronización, tu pareja, sincronizar ahora o salir. */
@Composable
private fun SeccionCuenta() {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var pareja by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    if (!Cuenta.vinculada) {
        Text("Entra con la misma cuenta que usas en Atrio y tus obras se sincronizan con Atrio del PC y con tus otros móviles: cada cambio sube en cuanto hay red y llega al momento a los demás.",
            color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        CampoRevista("Usuario", usuario, "tu usuario de Atrio") { usuario = it }
        CampoRevista("Contraseña", clave, "••••••••", secreto = true) { clave = it }
        MagLink(if (ocupado) "Entrando…" else "Vincular cuenta", "Se guarda un pase de acceso, no tu contraseña") {
            if (!ocupado) {
                ocupado = true; aviso = null
                ambito.launch {
                    aviso = try { Cuenta.entrar(ctx, usuario, clave); clave = ""; SincroP.sincronizar(ctx, desdeCero = true); SincroP.escuchar(ctx); null }
                    catch (e: Cuenta.Fail) { e.message } catch (e: Exception) { "No se pudo entrar: ${e.message ?: "sin conexión"}" }
                    ocupado = false
                }
            }
        }
    } else {
        val e = remember { Escritos.get(ctx) }
        Etiqueta("Vinculada a", Modifier.padding(top = 8.dp))
        Text(Cuenta.usuario, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 22.sp)
        val cuando = SincroP.ultima.takeIf { it > 0 }?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it)) }
        val pend = remember(e.version) { e.pendientes().size }
        Text(listOfNotNull(
            when {
                e.error.isNotEmpty() && pend > 0 -> "Sin conexión: $pend ${if (pend == 1) "cambio" else "cambios"} por subir (se suben solos)"
                pend > 0 -> "Subiendo $pend ${if (pend == 1) "cambio" else "cambios"}…"
                else -> SincroP.estado.ifBlank { "Sin sincronizar todavía" }
            }, cuando?.let { "última vez $it" }).joinToString(" · "),
            color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(6.dp))
        MagLink(if (ocupado) "Sincronizando…" else "Sincronizar ahora", "Baja lo de tus otros dispositivos y sube lo de este") {
            if (!ocupado) { ocupado = true; ambito.launch { SincroP.sincronizar(ctx, desdeCero = true); ocupado = false } }
        }
        Filete(1.dp, Line)
        // ---- Tu pareja (la misma que en Atrio): para compartirle capítulos y leer lo que te comparte
        if (Cuenta.parejaUid.isBlank()) {
            Text("Tu pareja: pon su usuario de Atrio y podrás compartirle capítulos (los lee y anota en su Thot o en su Atrio) y leer lo que te comparta.",
                color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
            CampoRevista("Pareja", pareja, "su usuario de Atrio") { pareja = it }
            MagLink(if (ocupado) "Buscando…" else "Enlazar con mi pareja") {
                if (!ocupado) {
                    ocupado = true; aviso = null
                    ambito.launch {
                        aviso = try { Cuenta.ponPareja(pareja); pareja = ""; "Enlazado con ${Cuenta.parejaUsuario}." } catch (x: Cuenta.Fail) { x.message } catch (x: Exception) { "No se pudo: ${x.message ?: "sin conexión"}" }
                        ocupado = false
                    }
                }
            }
        } else if (Cuenta.parejaSoloAqui) {
            // La de antes de este móvil, y tu cuenta sin pareja: no se pone sola (dejaría a esa persona leer tu cuenta)
            Etiqueta("Tu pareja", Modifier.padding(top = 8.dp))
            Text(Cuenta.parejaUsuario.ifBlank { "la de este móvil" }, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 22.sp)
            Text("Estaba guardada solo en este móvil y tu cuenta no tiene pareja, así que ahora mismo no os compartís nada. Si la pones en tu cuenta, " +
                "podrá leer tu cuenta (como la pareja de Jonsu) y estará en todos tus dispositivos.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
            MagLink(if (ocupado) "Poniéndola…" else "Ponerla en mi cuenta", "Desde ahora la tendrás en todas tus apps") {
                if (!ocupado) {
                    ocupado = true; aviso = null
                    ambito.launch {
                        aviso = try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Cuenta.subeParejaDeAqui() }; null } catch (x: Exception) { "No se pudo: ${x.message ?: "sin conexión"}" }
                        ocupado = false
                    }
                }
            }
            MagLink("Olvidarla", "Tu cuenta sigue sin pareja") { ambito.launch { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Cuenta.olvidaParejaDeAqui() } } }
        } else {
            Etiqueta("Tu pareja", Modifier.padding(top = 8.dp))
            Text(Cuenta.parejaUsuario.ifBlank { "Enlazada en tu cuenta" }, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 22.sp)
            Text("Es la de tu cuenta: la misma en todos tus dispositivos y en las demás apps de la familia.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
            MagLink(if (ocupado) "Quitando…" else "Quitar a mi pareja", "Dejas de compartir y de ver lo suyo, en todas tus apps") {
                if (!ocupado) {
                    ocupado = true; aviso = null
                    ambito.launch {
                        aviso = try { Cuenta.quitarPareja(); null } catch (x: Exception) { "No se pudo: ${x.message ?: "sin conexión"}" }
                        ocupado = false
                    }
                }
            }
        }
        Filete(1.dp, Line)
        MagLink("Salir de la cuenta", "Tus obras se quedan aquí; dejan de sincronizarse") { SincroP.parar(); Cuenta.salir(); Escritos.get(ctx).olvidaNube(); Publicar.olvidar(ctx) }
    }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
}

/** «Actualizaciones»: Cobalto se actualiza sola desde la carpeta «versiones» de tu kDrive (la de Atrio), con tu token de kDrive. */
@Composable
private fun SeccionActualizaciones() {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var conectado by remember { mutableStateOf(KDrive.on(ctx)) }
    var token by remember { mutableStateOf("") }
    var unidad by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var auto by remember { mutableStateOf(Actualizador.activo(ctx)) }
    var sobre by remember { mutableStateOf(Actualizador.puedeReabrirSola(ctx)) }
    val estado = Actualizador.estado.ifBlank { Actualizador.estadoGuardado(ctx) }
    DisposableEffect(Unit) {
        val owner = androidx.lifecycle.ProcessLifecycleOwner.get()
        val ob = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) sobre = Actualizador.puedeReabrirSola(ctx) }
        owner.lifecycle.addObserver(ob); onDispose { owner.lifecycle.removeObserver(ob) }
    }
    Fila("Actualizar sola", "Mira la carpeta «versiones» de tu kDrive (Thot-<versión>.apk) y se instala sin preguntar cuando no la usas.") {
        Interruptor(auto) { auto = it; Actualizador.ponActivo(ctx, it) }
    }
    // Android 13 o más: sin el permiso de avisos, «tienes una versión nueva» y «se ha actualizado, toca para abrirla» no se ven nunca
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        var avisos by remember { mutableStateOf(ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
        val pedirAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { avisos = it }
        if (!avisos) MagLink("Avisos de actualización: dar permiso", "Para avisarte cuando hay una versión nueva que Android no deja instalar sola, y para volver a abrir Thot al terminar de actualizarla.") { pedirAvisos.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }
    if (!conectado) {
        Text("El token de kDrive es de tu cuenta: se pone una vez en Jonsu (Ajustes → Avanzado → Tu nube) y lo tienen todas las apps. Si no lo has puesto aún, puedes pegarlo aquí: en cuanto funcione, pasa a tu cuenta.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        CampoRevista("Token", token, "token de kDrive", secreto = true) { token = it }
        CampoRevista("kDrive", unidad, "número o dirección") { unidad = it }
        MagLink(if (ocupado) "Comprobando…" else "Conectar", "Comprueba el token y busca tu «Mi carpeta»") {
            if (!ocupado) {
                ocupado = true; aviso = null
                ambito.launch {
                    aviso = try { KDrive.save(ctx, token, unidad); KDrive.test(ctx); KDrive.confirma(ctx); conectado = true; token = ""; withContext(Dispatchers.IO) { runCatching { Actualizador.comprueba(ctx, manual = true) } }; null }
                    catch (e: Exception) { KDrive.descarta(ctx); e.message ?: "No se pudo conectar" }
                    ocupado = false
                }
            }
        }
    } else {
        val vers = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
        Text("Versión instalada: $vers" + if (estado.isNotBlank()) " · última comprobación: $estado" else "", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        MagLink(if (ocupado) "Actualizando…" else "Actualizar ahora", "Busca la última versión en tu kDrive y la instala ahora", accent = true) {
            if (!ocupado) { ocupado = true; aviso = null; ambito.launch { aviso = withContext(Dispatchers.IO) { Actualizador.actualizarAhora(ctx) }; ocupado = false } }
        }
        Filete(1.dp, Line)
        MagLink("Solo comprobar") {
            if (!ocupado) { ocupado = true; ambito.launch { withContext(Dispatchers.IO) { Actualizador.comprueba(ctx, manual = true) }; ocupado = false } }
        }
        Filete(1.dp, Line)
        MagLink(if (sobre) "Tras actualizar, Thot se vuelve a abrir sola" else "Permitir que se reabra sola tras actualizar", if (sobre) null else "Hace falta el permiso «Mostrar sobre otras apps»; si no, te avisa para abrirla") {
            if (!sobre) runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))) }
        }
        Filete(1.dp, Line)
        Text("Para cambiar o quitar el token: Jonsu → Ajustes → Avanzado → Tu nube (vale para las cuatro apps).", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
    }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
}

/** Si hay una versión nueva ya bajada que Android no deja instalar en silencio, se pregunta (o se lleva al ajuste de «instalar apps»). */
@Composable
fun AvisoActualizacion() {
    val ctx = LocalContext.current
    val n = Actualizador.nueva?.takeIf { !it.silenciosa } ?: return
    var luego by remember(n.nombre) { mutableStateOf(false) }
    if (luego) return
    AlertDialog(
        onDismissRequest = { luego = true }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Thot tiene una versión nueva", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text(if (Actualizador.faltaPermiso) "Hay una versión nueva en tu kDrive. Para instalarla, Android tiene que dejar a Thot instalar apps: te llevo a ese ajuste." else "Hay una versión nueva en tu kDrive, ya descargada. Android te pedirá confirmar la instalación.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({
            if (Actualizador.faltaPermiso) { Actualizador.faltaPermiso = false; ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}"))) }
            else runCatching { Actualizador.instala(ctx, n) }.onFailure { android.widget.Toast.makeText(ctx, "No se pudo instalar: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
        }) { Text(if (Actualizador.faltaPermiso) "PERMITIR" else "ACTUALIZAR AHORA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ luego = true }) { Text("LUEGO", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}
