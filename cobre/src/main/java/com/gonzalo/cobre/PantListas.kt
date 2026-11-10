package com.gonzalo.cobre

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.gonzalo.iptv.DatosCuenta
import com.gonzalo.iptv.IptvError
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Secretos
import com.gonzalo.iptv.Servidor
import com.gonzalo.iptv.TipoLista
import com.gonzalo.iptv.Xtream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «Listas»: las tuyas (en tu cuenta, en la parte secreta), cómo van, ponerlas al día, cambiarlas, ordenarlas y quitarlas; y añadir otra:
 * un login de Xtream Codes (servidor, usuario y contraseña), una dirección M3U o un archivo M3U de este dispositivo. Las contraseñas y las
 * direcciones (que suelen llevarlas) se ven tapadas.
 */
@Composable
fun PantListas(alVolver: () -> Unit) {
    val ctx = LocalContext.current
    Listas.version; Tele.version
    var editando by remember { mutableStateOf<Lista?>(null) }
    var quitar by remember { mutableStateOf<Lista?>(null) }
    val listas = Listas.lista()
    val m = margen()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = m)) {
        CabeceraSeccion("Listas", "Tus canales", alVolver)
        Column(Modifier.widthIn(max = 820.dp)) {
            val e = editando
            if (e != null) { FormularioLista(e, alAcabar = { editando = null }); return@Column }
            Text("Tus listas van en tu cuenta, en su parte secreta (ni tu pareja la ve): las tienes en todos tus dispositivos. Los canales, el videoclub y la guía se bajan en cada uno (no van a la cuenta).",
                color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(10.dp))
            BotonAcento("Añadir una lista", modifier = Modifier.focoInicial("listas")) { editando = Lista(DatosCuenta.idNuevo(), "", TipoLista.XTREAM, creada = System.currentTimeMillis()) }
            Spacer(Modifier.height(18.dp))
            if (listas.isEmpty()) Nota("Aún no tienes ninguna. Nut no trae ninguna lista ni ningún canal: los pones tú.")
            listas.forEachIndexed { i, l ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            Etiqueta(Tele.tipoNombre(l.tipo) + when (l.tipo) {
                                TipoLista.XTREAM -> " · " + Servidor.normaliza(l.url).substringAfter("://") + " · " + l.usuario.take(2) + "…"
                                TipoLista.M3U -> " · " + Secretos.tapa(l.url, l.secretos.filter { it != l.url }).substringAfter("://").substringBefore('/') + "/…"
                                TipoLista.ARCHIVO -> if (l.dispositivo == Cuenta.dispositivo) " · en este dispositivo" else " · en otro dispositivo"
                            })
                        }
                        if (i > 0) EnlaceRevista("↑") { Listas.mueve(l.id, -1) }
                    }
                    val est = Tele.almacen.estado(l, "canales"); val g = Tele.almacen.estado(l, "guia")
                    val estado = Tele.ocupada[l.id] ?: Tele.errores[l.id]?.let { "No se ha podido: $it" } ?: if (est.cuando > 0) "${Formato.cifra(est.cuantos)} canales · ${Formato.fecha(est.cuando)}" + (if (g.cuando > 0) " · guía: ${Formato.cifra(g.cuantos)} programas" else "") else "Sin bajar todavía"
                    Text(estado, color = if (Tele.errores.containsKey(l.id)) Acento else Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    if (g.error.isNotBlank() && !Tele.ocupada.containsKey(l.id)) Text("Guía: ${g.error}", color = Muted, fontFamily = Body, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (l.tipo != TipoLista.ARCHIVO || l.dispositivo == Cuenta.dispositivo) EnlaceRevista(if (Tele.ocupada.containsKey(l.id)) "Actualizando…" else "Poner al día", acento = true) { Tele.actualiza(l, todo = true) }
                        EnlaceRevista("Cambiar") { editando = l }
                        EnlaceRevista("Quitar") { quitar = l }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            quitar?.let { l ->
                Panel {
                    Text("¿Quitar «${l.nombre}»?", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Cuerpo("Se quita de tu cuenta (en todos tus dispositivos) con lo que se ha bajado aquí. Tus favoritos y lo visto de esa lista se quedan en tu cuenta por si la vuelves a poner.")
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        EnlaceRevista("Sí, quitarla", Modifier.focoInicial(l.id), acento = true) { Listas.quita(l.id); Tele.olvida(l); quitar = null }
                        EnlaceRevista("No") { quitar = null }
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

/** Una casilla de texto de la revista; las secretas, tapadas (con «Ver» para comprobar lo escrito). */
@Composable
fun CampoRevista(etiqueta: String, valor: String, pista: String, secreto: Boolean = false, teclado: KeyboardType = KeyboardType.Uri, modifier: Modifier = Modifier, alCambiar: (String) -> Unit) {
    var ver by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(etiqueta, color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp).padding(bottom = 3.dp))
            Box(Modifier.weight(1f)) {
                if (valor.isEmpty()) Text(pista, color = Muted, fontFamily = Body, fontSize = 18.sp)
                BasicTextField(valor, alCambiar, singleLine = true, textStyle = TextStyle(color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp),
                    cursorBrush = SolidColor(Acento), visualTransformation = if (secreto && !ver) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (secreto) KeyboardType.Password else teclado, autoCorrect = false), modifier = Modifier.fillMaxWidth())
            }
            if (secreto && valor.isNotEmpty()) EnlaceRevista(if (ver) "Tapar" else "Ver", Modifier.padding(start = 8.dp)) { ver = !ver }
        }
        Filete(1.dp, Line)
    }
}

/** Añadir o cambiar una lista. */
@Composable
private fun FormularioLista(inicial: Lista, alAcabar: () -> Unit) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    val nueva = Listas.por(inicial.id) == null
    var tipo by remember { mutableStateOf(inicial.tipo) }
    var nombre by remember { mutableStateOf(inicial.nombre) }
    var url by remember { mutableStateOf(inicial.url) }
    var usuario by remember { mutableStateOf(inicial.usuario) }
    var clave by remember { mutableStateOf(inicial.contrasena) }
    var epg by remember { mutableStateOf(inicial.epg) }
    var agente by remember { mutableStateOf(inicial.agente) }
    var formato by remember { mutableStateOf(inicial.formato) }
    var archivo by remember { mutableStateOf<Uri?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) { archivo = u; if (nombre.isBlank()) nombre = u.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.').orEmpty() } }
    fun lista() = Lista(inicial.id, nombre.trim().ifBlank { when (tipo) { TipoLista.XTREAM -> Servidor.normaliza(url).substringAfter("://").substringBefore(':'); else -> "Mi lista" } }, tipo,
        if (tipo == TipoLista.XTREAM) Servidor.normaliza(url) else url.trim(), usuario.trim(), clave, epg.trim(), agente.trim(),
        if (tipo == TipoLista.ARCHIVO) (inicial.dispositivo.ifBlank { Cuenta.dispositivo }) else "", formato, inicial.creada)

    Antetitulo(if (nueva) "Añadir una lista" else "Cambiar «${inicial.nombre}»", Modifier.padding(top = 14.dp, bottom = 4.dp))
    val tipos = listOf(TipoLista.XTREAM, TipoLista.M3U, TipoLista.ARCHIVO)
    MagChoice(tipos.map { Tele.tipoNombre(it) }, tipos.indexOf(tipo), "De dónde") { tipo = tipos[it] }
    CampoRevista("Nombre", nombre, "cómo la llamas", teclado = KeyboardType.Text, modifier = Modifier.focoInicial(inicial.id)) { nombre = it }
    when (tipo) {
        TipoLista.XTREAM -> {
            CampoRevista("Servidor", url, "http://servidor:puerto") { url = it }
            CampoRevista("Usuario", usuario, "tu usuario", secreto = true) { usuario = it }
            CampoRevista("Contraseña", clave, "tu contraseña", secreto = true) { clave = it }
            MagChoice(listOf("MPEG-TS (ts)", "HLS (m3u8)"), if (formato == "m3u8") 1 else 0, "Los canales en directo, como") { formato = if (it == 1) "m3u8" else "ts" }
            Text("Si con uno los canales se cortan o no se ven, prueba con el otro.", color = Muted, fontFamily = Body, fontSize = 12.sp)
        }
        TipoLista.M3U -> {
            CampoRevista("Dirección", url, "http://…/lista.m3u", secreto = true) { url = it }
            Servidor.deEnlace(url)?.let { (s, u, p) ->
                Spacer(Modifier.height(6.dp))
                MagLink("Es la lista de un servidor de Xtream: añadirlo como login", "Así tienes también sus películas, sus series y su guía", accent = true) { tipo = TipoLista.XTREAM; url = s; usuario = u; clave = p }
            }
        }
        TipoLista.ARCHIVO -> {
            Spacer(Modifier.height(8.dp))
            MagLink(if (archivo != null || Tele.almacen.tieneArchivo(inicial)) "Elegir otro archivo" else "Elegir el archivo M3U", "Se queda en este dispositivo (cifrado); en los demás, esta lista sale como «en otro dispositivo»") { elegir.launch(arrayOf("*/*")) }
            archivo?.let { Etiqueta("Elegido: " + (it.lastPathSegment?.substringAfterLast('/') ?: "un archivo")) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Etiqueta("Si hace falta")
    CampoRevista("Guía XMLTV", epg, "una dirección .xml o .xml.gz (opcional)", secreto = true) { epg = it }
    CampoRevista("User-Agent", agente, "el que pida tu proveedor (opcional)", teclado = KeyboardType.Text) { agente = it }
    Spacer(Modifier.height(12.dp))
    aviso?.let { Text(it, color = Acento, fontFamily = Body, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp)) }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        BotonAcento(if (ocupado) "Comprobando…" else "Guardar") {
            if (ocupado) return@BotonAcento
            val l = lista()
            when {
                l.tipo == TipoLista.XTREAM && (l.url.isBlank() || l.usuario.isBlank() || l.contrasena.isBlank()) -> { aviso = "Faltan el servidor, el usuario o la contraseña."; return@BotonAcento }
                l.tipo == TipoLista.M3U && !(l.url.startsWith("http://", true) || l.url.startsWith("https://", true)) -> { aviso = "La dirección tiene que empezar por http:// o https://."; return@BotonAcento }
                l.tipo == TipoLista.ARCHIVO && archivo == null && !Tele.almacen.tieneArchivo(l) -> { aviso = "Elige el archivo."; return@BotonAcento }
            }
            ocupado = true; aviso = null
            ambito.launch {
                val r = withContext(Dispatchers.IO) {
                    try {
                        // Un login de Xtream se comprueba antes de guardarlo (así un error de escritura no se queda en tu cuenta)
                        if (l.tipo == TipoLista.XTREAM) {
                            val p = Xtream(l, Tele.red).perfil()
                            if (!p.activo) return@withContext "El servidor dice que tu cuenta está «${p.estado}»."
                        }
                        archivo?.let { u -> ctx.contentResolver.openInputStream(u)?.use { Tele.almacen.guardaArchivo(l, it) } ?: return@withContext "No se puede leer ese archivo." }
                        null
                    } catch (e: IptvError) { Secretos.tapa(e.message.orEmpty(), l.secretos) } catch (e: Exception) { "No se ha podido comprobar (sin conexión)." }
                }
                ocupado = false
                if (r != null) { aviso = r; return@launch }
                Listas.pon(l)
                AjustesDirecto.ponDonde(ctx, l.id, "")
                Tele.suelta(l)
                Tele.actualiza(l, todo = true)
                alAcabar()
            }
        }
        BotonSuave("Cancelar") { alAcabar() }
    }
}
