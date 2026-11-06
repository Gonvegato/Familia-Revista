package com.gonzalo.mostaza

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** El sitio (host) de una dirección, para enseñarla sin lo que lleva dentro (la clave del debrid va en el camino). */
fun sitioDe(url: String): String = runCatching { Uri.parse(Protocolo.normaliza(url)).host.orEmpty() }.getOrDefault("").ifBlank { "otro sitio" }

/**
 * Tus addons: los que tienes (en tu orden: el de arriba manda en las fichas y sale antes en las fuentes), subirlos y bajarlos, quitarlos,
 * configurarlos (la página del propio addon, si la tiene) e instalar uno nuevo por la dirección de su manifiesto.
 */
@Composable
fun PantAddons(alVolver: () -> Unit, instalar: String? = null, alInstalado: () -> Unit = {}) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    val lista = Addons.lista()
    val manifiestos by produceState<Map<String, Manifiesto>>(emptyMap(), Addons.version) {
        value = kotlinx.coroutines.withContext(Dispatchers.IO) { lista.mapNotNull { a -> Addons.manifiesto(a.url)?.let { a.url to it } }.toMap() }
    }
    var url by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var quitar by remember { mutableStateOf<AddonInstalado?>(null) }
    var pedido by remember(instalar) { mutableStateOf(instalar) }
    fun instala(u: String) {
        if (ocupado || u.isBlank()) return
        ocupado = true; aviso = null
        ambito.launch {
            aviso = try { val m = Addons.instala(u); url = ""; "Instalado: ${m.nombre}." } catch (e: Exception) { "No se ha podido instalar: ${e.message ?: "no responde"}." }
            ocupado = false
        }
    }
    val mg = margen()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = mg)) {
        RevistaTopBar(alVolver, "Mostaza")
        Antetitulo("De dónde sale todo", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle("Addons", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
        Filete(3.dp)
        Text("Mostaza habla con los addons de Stremio: los catálogos, las fichas, las fuentes y los subtítulos salen de ellos. El de arriba manda en las fichas y sale antes en las fuentes. Tus addons viajan con tu cuenta, pero en su parte secreta (la que solo puedes leer tú: ni siquiera tu pareja), porque sus direcciones pueden llevar la clave de tu debrid.",
            color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
        if (!Cuenta.vinculada) Nota("Sin cuenta todavía: lo que instales se queda en este dispositivo y pasa a tu cuenta al entrar.", Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp))
        lista.forEachIndexed { i, a ->
            val m = manifiestos[a.url]
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.width(38.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m?.nombre ?: a.nombre.ifBlank { sitioDe(a.url) }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(m?.version?.takeIf { it.isNotBlank() }?.let { "v$it" }, m?.tipos?.joinToString(", ") { tiposNombre(it) }?.takeIf { it.isNotBlank() }, m?.recursos?.joinToString(", ") { nombreRecurso(it.nombre) }, if (m == null) "sin respuesta ahora" else null, sitioDe(a.url)).joinToString(" · ").uppercase(),
                            color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 2)
                    }
                }
                m?.descripcion?.takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 38.dp, top = 2.dp)) }
                if (m?.p2p == true) Text("Da torrents: ver con ellos comparte con otros lo que bajas.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.padding(start = 38.dp))
                Row(Modifier.padding(start = 38.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (i > 0) EnlaceRevista("↑ Subir") { Addons.mueve(a.url, -1) }
                    if (i < lista.size - 1) EnlaceRevista("↓ Bajar") { Addons.mueve(a.url, 1) }
                    if (m?.configurable == true || m?.configuracionObligatoria == true) EnlaceRevista("Configurar", acento = true) { ConfigurarActivity.abre(ctx, a.url) }
                    EnlaceRevista("Quitar") { quitar = a }
                }
            }
            Filete(1.dp, Line)
        }
        if (lista.isEmpty()) Nota("No tienes ningún addon. Sin ellos no hay catálogos ni fichas: vuelve a poner Cinemeta (abajo).", Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(18.dp))
        Antetitulo("Instalar un addon", Modifier.padding(bottom = 2.dp))
        CampoRevista("Dirección", url, "https://…/manifest.json") { url = it }
        MagLink(if (ocupado) "Instalando…" else "Instalar", "Pega la dirección del manifiesto (la de «Install» o «Copy link» en su página)", accent = true) { instala(url) }
        if (lista.none { it.url == Addons.CINEMETA }) { Filete(1.dp, Line); MagLink("Poner Cinemeta", "El oficial de Stremio: catálogos y fichas de películas y series") { instala(Addons.CINEMETA) } }
        if (lista.none { it.url == Addons.OPENSUBTITLES }) { Filete(1.dp, Line); MagLink("Poner OpenSubtitles", "El oficial de Stremio para los subtítulos (no viene puesto)") { instala(Addons.OPENSUBTITLES) } }
        aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(40.dp))
    }
    quitar?.let { a ->
        AlertDialog(onDismissRequest = { quitar = null }, containerColor = Paper, shape = esquina(4.dp),
            title = { Text("¿Quitar ${manifiestos[a.url]?.nombre ?: a.nombre.ifBlank { "este addon" }}?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
            text = { Text("Deja de usarse en todos tus dispositivos. Para volver a ponerlo hará falta su dirección (si la configuraste, con tu clave).", color = Muted, fontFamily = Body) },
            confirmButton = { TextButton({ Addons.quita(a.url); quitar = null }) { Text("QUITAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black) } },
            dismissButton = { TextButton({ quitar = null }) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black) } })
    }
    pedido?.let { u ->
        AlertDialog(onDismissRequest = { pedido = null; alInstalado() }, containerColor = Paper, shape = esquina(4.dp),
            title = { Text("¿Instalar este addon?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
            text = { Text("Viene de ${sitioDe(u)}. Se guarda en la parte secreta de tu cuenta.", color = Muted, fontFamily = Body) },
            confirmButton = { TextButton({ pedido = null; alInstalado(); instala(u) }) { Text("INSTALAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black) } },
            dismissButton = { TextButton({ pedido = null; alInstalado() }) { Text("NO", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black) } })
    }
}

private fun nombreRecurso(r: String) = when (r) { "catalog" -> "catálogos"; "meta" -> "fichas"; "stream" -> "fuentes"; "subtitles" -> "subtítulos"; "addon_catalog" -> "addons"; else -> r }

/**
 * La página de configurar de un addon (`/configure`, la del propio addon). Cuando la página da su botón de instalar (`stremio://…`), esa
 * dirección nueva (con lo que hayas puesto: tu debrid…) sustituye a la de antes, en el mismo sitio de tu lista. Si Android cierra lo que
 * dibuja la página, se cierra esta pantalla (no la app).
 */
class ConfigurarActivity : ComponentActivity() {
    companion object {
        /** (En memoria, no en el Intent: la dirección puede llevar secretos.) */
        @Volatile private var pendiente: String? = null
        fun abre(c: Context, url: String) { pendiente = url; c.startActivity(Intent(c, ConfigurarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private val ambito: CoroutineScope = MainScope()
    private var web: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vieja = pendiente ?: run { finish(); return }
        pendiente = null
        val w = WebView(this)
        web = w
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url.toString()
                if (u.startsWith("stremio://", true) || (u.substringBefore('?').endsWith("/manifest.json") && !u.startsWith(Protocolo.base(vieja)))) {
                    ambito.launch {
                        val r = runCatching { Addons.reemplaza(vieja, u) }
                        Toast.makeText(this@ConfigurarActivity, r.fold({ "Configurado: ${it.nombre}" }, { "No se ha podido: ${it.message ?: "no responde"}" }), Toast.LENGTH_LONG).show()
                        finish()
                    }
                    return true
                }
                return false
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // Android ha cerrado lo que dibuja la página: esta pantalla ya no sirve (si no se atiende, Android cierra la app entera)
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                view.destroy(); web = null
                finish()
                return true
            }
        }
        setContentView(w)
        w.loadUrl(Protocolo.configurar(vieja))
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { val w = web; if (w != null && w.canGoBack()) w.goBack() else @Suppress("DEPRECATION") super.onBackPressed() }

    override fun onDestroy() { ambito.cancel(); web?.let { (it.parent as? android.view.ViewGroup)?.removeView(it); it.destroy() }; web = null; super.onDestroy() }
}
