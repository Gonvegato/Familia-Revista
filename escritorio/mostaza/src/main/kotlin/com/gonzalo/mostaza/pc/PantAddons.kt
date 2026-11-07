package com.gonzalo.mostaza.pc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.Sitio
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun nombreRecurso(r: String) = when (r) { "catalog" -> "catálogos"; "meta" -> "fichas"; "stream" -> "fuentes"; "subtitles" -> "subtítulos"; "addon_catalog" -> "addons"; else -> r }

/**
 * Tus addons (los mismos que en el móvil): los que tienes, en tu orden (el de arriba manda en las fichas y sale antes en las fuentes),
 * subirlos y bajarlos, quitarlos, configurarlos (la página del propio addon, en el navegador: luego se pega aquí la dirección nueva) e
 * instalar uno nuevo por la dirección de su manifiesto. Las direcciones nunca se enseñan enteras (llevan tu clave del debrid): solo su sitio.
 */
@Composable
fun PantAddons(nav: Nav) {
    val ambito = rememberCoroutineScope()
    val lista = Addons.lista()
    val manifiestos by produceState<Map<String, Manifiesto>>(emptyMap(), Addons.version) {
        value = withContext(Dispatchers.IO) { lista.mapNotNull { a -> Addons.manifiesto(a.url)?.let { a.url to it } }.toMap() }
    }
    var url by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var quitar by remember { mutableStateOf<AddonInstalado?>(null) }
    var configurando by remember { mutableStateOf<AddonInstalado?>(null) }
    fun instala(u: String) {
        if (ocupado || u.isBlank()) return
        ocupado = true; aviso = null
        ambito.launch {
            aviso = try { val m = Addons.instala(u); url = ""; "Instalado: ${m.nombre}." } catch (e: Exception) { "No se ha podido instalar: ${e.message ?: "no responde"}." }
            ocupado = false
        }
    }
    Pagina(ancho = 960.dp) {
        CabeceraPagina("De dónde sale todo", "Addons")
        Cuerpo("Hathor habla con los addons de Stremio: los catálogos, las fichas, las fuentes y los subtítulos salen de ellos. El de arriba manda en las fichas y sale antes en las fuentes. Tus addons viajan con tu cuenta (son los mismos que en el móvil), pero en su parte secreta (la que solo puedes leer tú: ni siquiera tu pareja), porque sus direcciones pueden llevar la clave de tu debrid.",
            Modifier.padding(top = 10.dp), tam = 13)
        Spacer(Modifier.height(10.dp))
        lista.forEachIndexed { i, a ->
            val m = manifiestos[a.url]
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.width(38.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m?.nombre ?: a.nombre.ifBlank { Addons.sitioDe(a.url) }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(m?.version?.takeIf { it.isNotBlank() }?.let { "v$it" }, m?.tipos?.joinToString(", ") { tiposNombre(it) }?.takeIf { it.isNotBlank() },
                            m?.recursos?.joinToString(", ") { nombreRecurso(it.nombre) }, if (m == null) "sin respuesta ahora" else null, Addons.sitioDe(a.url)).joinToString(" · ").uppercase(),
                            color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 2)
                    }
                }
                m?.descripcion?.takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 38.dp, top = 2.dp)) }
                if (m?.p2p == true) Text("Da torrents: ver con ellos comparte con otros lo que bajas.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.padding(start = 38.dp))
                Row(Modifier.padding(start = 38.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (i > 0) EnlaceRevista("↑ Subir") { Addons.mueve(a.url, -1) }
                    if (i < lista.size - 1) EnlaceRevista("↓ Bajar") { Addons.mueve(a.url, 1) }
                    if (m?.configurable == true || m?.configuracionObligatoria == true) EnlaceRevista("Configurar", acento = true) { configurando = a; abreEnNavegador(Protocolo.configurar(a.url)) }
                    EnlaceRevista("Quitar") { quitar = a }
                }
            }
            Filete(1.dp, Line)
        }
        if (lista.isEmpty()) Nota("No tienes ningún addon. Sin ellos no hay catálogos ni fichas: vuelve a poner Cinemeta (abajo).", Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(18.dp))
        Antetitulo("Instalar un addon", Modifier.padding(bottom = 6.dp))
        CampoRevista("Dirección del manifiesto", url, "https://…/manifest.json o stremio://…", secreto = true) { url = it }
        MagLink(if (ocupado) "Instalando…" else "Instalar", "Pega la dirección del manifiesto (la de «Install» o «Copy link» en su página)", accent = true) { instala(url) }
        if (lista.none { it.url == Addons.CINEMETA }) { Filete(1.dp, Line); MagLink("Poner Cinemeta", "El oficial de Stremio: catálogos y fichas de películas y series") { instala(Addons.CINEMETA) } }
        if (lista.none { it.url == Addons.OPENSUBTITLES }) { Filete(1.dp, Line); MagLink("Poner OpenSubtitles", "El oficial de Stremio para los subtítulos (no viene puesto)") { instala(Addons.OPENSUBTITLES) } }
        aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
    quitar?.let { a ->
        Dialogo("¿Quitar ${manifiestos[a.url]?.nombre ?: a.nombre.ifBlank { "este addon" }}?", { quitar = null }, botones = {
            BotonSuave("Cancelar") { quitar = null }
            BotonAcento("Quitar", alto = 44.dp) { Addons.quita(a.url); quitar = null }
        }) { Cuerpo("Deja de usarse en todos tus dispositivos. Para volver a ponerlo hará falta su dirección (si la configuraste, con tu clave).", tam = 14) }
    }
    configurando?.let { a ->
        var nueva by remember { mutableStateOf("") }
        var fallo by remember { mutableStateOf("") }
        Dialogo("Configurar ${manifiestos[a.url]?.nombre ?: a.nombre}", { configurando = null }, botones = {
            BotonSuave("Cancelar") { configurando = null }
            BotonAcento("Guardar", alto = 44.dp) {
                ambito.launch {
                    val r = runCatching { Addons.reemplaza(a.url, nueva) }
                    r.onSuccess { configurando = null; nav.aviso("Configurado: ${it.nombre}") }.onFailure { fallo = "No se ha podido: ${it.message ?: "no responde"}." }
                }
            }
        }) {
            Cuerpo("Se ha abierto su página en el navegador. Configúralo allí (tu debrid, lo que quieras) y copia la dirección de instalar que te da («Copy link» o el botón de instalar): pégala aquí y sustituye a la de ahora, en el mismo sitio de tu lista.", tam = 13)
            Spacer(Modifier.height(10.dp))
            CampoRevista("Dirección nueva", nueva, "https://…/manifest.json o stremio://…", secreto = true) { nueva = it; fallo = "" }
            if (fallo.isNotBlank()) Cuerpo(fallo, color = Acento, tam = 13)
        }
    }
}
