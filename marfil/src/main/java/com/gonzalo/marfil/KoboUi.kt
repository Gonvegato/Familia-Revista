package com.gonzalo.marfil

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
    var pregunta by remember { mutableStateOf<String?>(null) }   // «preparar» o «quitar»
    var accion by remember { mutableStateOf("leer") }

    fun recuerda(u: Uri) = runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u == null || ocupado) return@rememberLauncherForActivityResult
        recuerda(u)
        ocupado = true; mensaje = null; lista = emptyList()
        ambito.launch {
            mensaje = try {
                withContext(Dispatchers.IO) {
                    when (accion) {
                        "preparar" -> {
                            val p = KoboCable.prepara(ctx, u)
                            preparado = true
                            listOfNotNull(
                                "Kobo preparado.",
                                if (p.plugins) "Desconéctalo: se reiniciará para instalar NickelMenu y NickelInkSync." else null,
                                "Luego pulsa «Enviar a Seshat» en el menú «Más» del Kobo una vez.",
                                if (KoSync.activo(ctx)) "Posición de ${p.conPosicion} ${if (p.conPosicion == 1) "libro" else "libros"} entre el Kobo y SesKobo." else "Sin usuario de posiciones: solo libros, porcentaje y tiempo.",
                            ).joinToString(" ")
                        }
                        "quitar" -> { KoboCable.quita(ctx, u); preparado = false; "Quitado del Kobo. NickelMenu se queda; NickelInkSync se desinstala al reiniciar el Kobo." }
                        else -> {
                            val r = KoboCable.lee(ctx, u)
                            lista = r.resumen
                            if (r.resumen.isEmpty()) "El Kobo no tiene nada nuevo (${r.libros} ${if (r.libros == 1) "libro" else "libros"} abiertos)."
                            else "Del Kobo: ${r.resumen.size} ${if (r.resumen.size == 1) "libro" else "libros"} al día."
                        }
                    }
                }
            } catch (e: KoboCable.Error) { e.message } catch (e: Exception) { "No se pudo: ${e.message ?: e.javaClass.simpleName}" }
            ocupado = false
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
        MagLink("Quitar del Kobo", "Borra del Kobo el programa, tu acceso y NickelInkSync") { if (!ocupado) pregunta = "quitar" }
    }
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
