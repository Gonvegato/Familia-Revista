package com.gonzalo.marfil

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SesKobo, Ajustes → Lectura → «Kobo»: leer el Kobo por cable, prepararlo para que mande solo por Wi-Fi (o quitarlo) y el usuario del
 * servidor de posiciones (KOSync), para seguir el libro donde lo dejaste en el Kobo y al revés.
 */
@Composable
fun SeccionKobo() {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var ocupado by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var lista by remember { mutableStateOf<List<String>>(emptyList()) }
    var preparado by remember { mutableStateOf(KoboCable.preparado(ctx)) }
    // (se guardan si la pantalla se rehace —al girar el móvil— mientras eliges la carpeta del Kobo: si no, lo que confirmaste se haría como «leer»)
    var pregunta by rememberSaveable { mutableStateOf<String?>(null) }   // «preparar» o «quitar»
    var accion by rememberSaveable { mutableStateOf("leer") }
    // Vincular kepub con EPUB: el Kobo elegido y las parejas encontradas (mientras se ve la pantalla de vincular)
    var arbolVincular by remember { mutableStateOf<Uri?>(null) }
    var parejas by remember { mutableStateOf<List<KoboCable.Pareja>?>(null) }
    var vinculados by remember { mutableStateOf(KoboCable.vinculados(ctx)) }

    // (Sin permiso persistente: el Kobo se elige cada vez y solo se usa mientras dura la operación)
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u == null || ocupado) return@rememberLauncherForActivityResult
        ocupado = true; mensaje = null; lista = emptyList()
        ambito.launch {
            // El trabajo, en segundo plano; lo que cambia en la pantalla, aquí (en el hilo principal)
            mensaje = try {
                when (accion) {
                    "preparar" -> {
                        val p = withContext(Dispatchers.IO) { KoboCable.prepara(ctx, u) }
                        preparado = true
                        listOfNotNull(
                            "Kobo preparado.",
                            if (p.plugins) "Desconéctalo: se reiniciará para instalar NickelMenu y NickelInkSync." else null,
                            "Luego pulsa «Enviar a Seshat» en el menú «Más» del Kobo una vez.",
                            if (KoSync.activo(ctx)) "Posición de ${p.conPosicion} ${if (p.conPosicion == 1) "libro" else "libros"} entre el Kobo y SesKobo." else "Sin usuario de posiciones: solo libros, porcentaje y tiempo.",
                        ).joinToString(" ")
                    }
                    "quitar" -> {
                        withContext(Dispatchers.IO) { KoboCable.quita(ctx, u) }
                        preparado = false; vinculados = 0
                        "Quitado del Kobo. NickelMenu se queda; NickelInkSync se desinstala al reiniciar el Kobo."
                    }
                    "vincular" -> {
                        val ps = withContext(Dispatchers.IO) { KoboCable.buscaParejas(ctx, u) }
                        arbolVincular = u; parejas = ps
                        null
                    }
                    else -> {
                        val r = withContext(Dispatchers.IO) { KoboCable.lee(ctx, u) }
                        lista = r.resumen
                        if (r.resumen.isEmpty()) "El Kobo no tiene nada nuevo (${r.libros} ${if (r.libros == 1) "libro" else "libros"} abiertos)."
                        else "Del Kobo: ${r.resumen.size} ${if (r.resumen.size == 1) "libro" else "libros"} al día."
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: KoboCable.Error) { e.message } catch (e: Exception) { "No se pudo: ${e.message ?: e.javaClass.simpleName}" }
            finally { ocupado = false }
        }
    }

    Text("Lo que lees en el Kobo (libros, por dónde vas, terminados y tiempo leído) pasa a Seshat y a Jonsu. Si después lees ese libro aquí, gana lo más reciente.",
        color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 4.dp))
    MagLink(if (ocupado && accion == "leer") "Leyendo el Kobo…" else "Leer el Kobo por cable", "Enchufa el Kobo al móvil, pulsa «Conectar» en el Kobo y elige su memoria (KOBOeReader)") {
        if (!ocupado) { accion = "leer"; elegir.launch(null) }
    }
    Filete(1.dp, Line)
    MagLink(if (ocupado && accion == "preparar") "Preparando el Kobo…" else "Preparar el Kobo para Wi-Fi",
        if (preparado) "Ya está preparado. Vuelve a hacerlo si cambias de cuenta o de usuario de posiciones" else "Una sola vez: después manda solo cada vez que el Kobo tiene Wi-Fi (experimental)") {
        if (!ocupado) pregunta = "preparar"
    }
    if (preparado) {
        Filete(1.dp, Line)
        MagLink(if (ocupado && accion == "vincular") "Buscando en el Kobo…" else "Vincular kepub con EPUB", "Para seguir en el Kobo lo que lees aquí, y al revés, con kepub en el Kobo") {
            if (!ocupado) { accion = "vincular"; elegir.launch(null) }
        }
        Filete(1.dp, Line)
        MagLink("Quitar del Kobo", "Borra del Kobo el programa, tu acceso y NickelInkSync") { if (!ocupado) pregunta = "quitar" }
    }
    if (vinculados > 0 && mensaje == null) Text("$vinculados ${if (vinculados == 1) "libro vinculado" else "libros vinculados"}.", color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
    val ultima = KoboCable.ultima(ctx)
    val texto = mensaje ?: ultima?.let { (t, como) -> "Última vez: " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(t)) + if (como == "cable") ", por cable." else "." }
    texto?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp)) }
    for (l in lista) {
        val (t, d) = l.split(": ", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text(t, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 10.dp))
            Text(d, color = Muted, fontFamily = Body, fontSize = 12.5.sp)
        }
        Filete(1.dp, Line)
    }

    Spacer(Modifier.height(14.dp))
    Antetitulo("Posición en el Kobo", Modifier.padding(bottom = 4.dp))
    SeccionKoSync()

    parejas?.let { ps ->
        val arbol = arbolVincular
        PantVincular(ps, alCerrar = { parejas = null; arbolVincular = null }) { elegidas ->
            if (arbol == null) return@PantVincular null
            try {
                val n = withContext(Dispatchers.IO) { KoboCable.vincula(ctx, arbol, elegidas) }
                "$n ${if (n == 1) "libro vinculado" else "libros vinculados"}."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: KoboCable.Error) { e.message } catch (e: Exception) { "No se pudo: ${e.message ?: e.javaClass.simpleName}" }
            finally { vinculados = KoboCable.vinculados(ctx) }
        }
    }

    pregunta?.let { p ->
        AlertDialog(onDismissRequest = { pregunta = null },
            title = { Text(if (p == "preparar") "Preparar el Kobo" else "Quitar del Kobo", fontFamily = Display, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (p == "preparar") {
                        Text("SesKobo copiará al Kobo:", fontFamily = Body, fontSize = 14.sp)
                        for (l in listOf(
                            "NickelMenu y NickelInkSync (para el firmware 6; no oficiales). El Kobo se reinicia al desconectarlo para instalarlos.",
                            "«Enviar a Seshat» y «Seshat · último envío» en el menú «Más».",
                            "Tu acceso a la cuenta, para que el Kobo pueda mandar. Queda guardado sin cifrar en el Kobo.",
                            "Si tienes usuario de posiciones: el usuario y su clave (el MD5 de la contraseña) para NickelInkSync.",
                        )) Text("• $l", fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                        Text("Después, pulsa «Enviar a Seshat» una vez. Desde ahí manda solo cada vez que tiene Wi-Fi, hasta que se apague del todo. Es experimental: no está probado en un Kobo de verdad.",
                            fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                    } else Text("Se borra del Kobo el programa de Seshat, tu acceso a la cuenta y NickelInkSync (se desinstala al reiniciar el Kobo). NickelMenu se queda.", fontFamily = Body, fontSize = 14.sp, lineHeight = 19.sp)
                }
            },
            confirmButton = { TextButton({ accion = p; pregunta = null; elegir.launch(null) }) { Text("ELEGIR EL KOBO", color = Acento, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ pregunta = null }) { Text("CANCELAR", color = Charcoal) } })
    }
}

/** El usuario del servidor de posiciones (KOSync): el mismo en SesKobo y en el Kobo. Si no existe, se crea (como en KOReader). */
@Composable
private fun SeccionKoSync() {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var dentro by remember { mutableStateOf(KoSync.activo(ctx)) }
    var servidor by remember { mutableStateOf(KoSync.servidor(ctx)) }
    var usuario by remember { mutableStateOf(KoSync.usuario(ctx)) }
    var contrasena by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    Text("Para abrir aquí el libro por donde ibas en el Kobo, y al revés. La posición pasa por un servidor público de KOReader: guarda la huella de cada libro y por dónde vas, nunca el libro. Tiene que ser el mismo archivo en el Kobo y aquí.",
        color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 2.dp))
    if (!dentro) {
        CampoRevista("Servidor", servidor, KoSync.SERVIDOR) { servidor = it }
        CampoRevista("Usuario", usuario, "inventa uno") { usuario = it }
        CampoRevista("Contraseña", contrasena, "••••••••", secreto = true) { contrasena = it }
        MagLink(if (ocupado) "Entrando…" else "Entrar o crear usuario", "Si el usuario no existe, se crea. Usa una contraseña que no uses en otro sitio") {
            if (!ocupado) {
                ocupado = true; aviso = null
                ambito.launch {
                    aviso = try {
                        val creada = withContext(Dispatchers.IO) { KoSync.entra(ctx, servidor, usuario, contrasena) }
                        contrasena = ""; dentro = true
                        (if (creada) "Usuario creado." else "Dentro.") + " Prepara el Kobo otra vez para que lo use también."
                    } catch (e: KoSync.Error) { e.message } catch (e: Exception) { "No se pudo: ${e.message ?: "sin conexión"}" }
                    ocupado = false
                }
            }
        }
    } else {
        Etiqueta("Usuario", Modifier.padding(top = 8.dp))
        Text(KoSync.usuario(ctx), color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 22.sp)
        Text(KoSync.servidor(ctx).removePrefix("https://"), color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(6.dp))
        MagLink("Salir", "Deja de llevar la posición entre el Kobo y SesKobo") { KoSync.sal(ctx); dentro = false; aviso = null }
    }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp)) }
}

/**
 * «Vincular»: las parejas kepub del Kobo ↔ EPUB de SesKobo que se han encontrado, para marcar las que quieras vincular (las dudosas
 * salen sin marcar), y el resultado. [vincular] hace el trabajo y devuelve el mensaje (o null si no ha podido empezar).
 */
@Composable
fun PantVincular(parejas: List<KoboCable.Pareja>, alCerrar: () -> Unit, vincular: suspend (List<KoboCable.Pareja>) -> String?) {
    val ambito = rememberCoroutineScope()
    val conLibro = parejas.filter { it.libro != null }
    val sinLibro = parejas.filter { it.libro == null }
    var marcadas by remember { mutableStateOf(conLibro.filter { it.seguro }.map { it.kepub }.toSet()) }
    var hecho by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismissRequest = alCerrar, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 14.dp)) {
                EnlaceRevista("← Ajustes", Modifier.padding(top = 6.dp), color = Paper.copy(alpha = 0.75f)) { alCerrar() }
                NombreConPunto("Vincular", 44.sp, Paper, Modifier.padding(top = 2.dp))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                val fin = hecho
                if (fin != null) {
                    Text(fin, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 23.sp)
                    Text("Desconecta el Kobo y pulsa «Enviar a Seshat» con Wi-Fi: el Kobo termina de prepararlos él solo.", color = Charcoal, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
                    Text("A partir de ahí: lee el EPUB en SesKobo y el kepub en el Kobo. Al abrir uno, sigue donde dejaste el otro (al principio del párrafo).", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
                    Text("SesKobo ha copiado el EPUB de cada uno a una carpeta oculta del Kobo, con un nombre que el Kobo no toma por libro: no te saldrá repetido.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
                    MagLink("Volver a Ajustes") { alCerrar() }
                    return@Column
                }
                if (conLibro.isEmpty()) {
                    Text(if (parejas.isEmpty()) "No hay kepub sin vincular en el Kobo." else "Ningún kepub del Kobo tiene su EPUB en SesKobo.", color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 19.sp)
                } else {
                    Text("Estos kepub del Kobo tienen su EPUB en SesKobo. Marca los que quieras vincular. Los que ya lo están no salen.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 4.dp))
                    for (p in conLibro) {
                        val on = p.kepub in marcadas
                        Row(Modifier.fillMaxWidth().clickable { marcadas = if (on) marcadas - p.kepub else marcadas + p.kepub }.padding(vertical = 11.dp)) {
                            Box(Modifier.padding(top = 2.dp).size(18.dp).border(2.dp, if (on) Acento else Charcoal).background(if (on) Acento else androidx.compose.ui.graphics.Color.Transparent), contentAlignment = Alignment.Center) {
                                if (on) Text("✓", color = Paper, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(p.tituloKobo.ifBlank { p.archivoKobo }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp)
                                Text("Kobo: ${p.archivoKobo}", color = Muted, fontFamily = Body, fontSize = 11.5.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 3.dp))
                                Text("SesKobo: ${p.libro!!.titulo}" + p.libro.autor.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), color = Muted, fontFamily = Body, fontSize = 11.5.sp, lineHeight = 16.sp)
                                if (!p.seguro) Text("PUEDE NO SER EL MISMO: COMPRUÉBALO", color = Acento, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                        Filete(1.dp, Line)
                    }
                    val n = marcadas.size
                    Box(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp).background(if (n > 0) Acento else Muted).clickable(enabled = n > 0 && !ocupado) {
                        ocupado = true
                        ambito.launch { hecho = vincular(conLibro.filter { it.kepub in marcadas }); ocupado = false }
                    }.padding(horizontal = 16.dp, vertical = 13.dp)) {
                        Text(if (ocupado) "VINCULANDO…" else "VINCULAR $n ${if (n == 1) "LIBRO" else "LIBROS"}  →", color = Paper, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                    }
                }
                if (sinLibro.isNotEmpty()) Text("Sin EPUB en SesKobo: " + sinLibro.joinToString(", ") { "«${it.tituloKobo.ifBlank { it.archivoKobo }}»" } + ". Añádelos a SesKobo y vuelve aquí.",
                    color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
