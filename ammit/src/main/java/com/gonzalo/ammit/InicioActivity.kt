package com.gonzalo.ammit

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La pantalla de inicio. Con Ammit encendida: tu fondo de pantalla, la hora, los widgets de la familia que pongas y tus apps donde las
 * coloques (la familia en color; el resto, en gris y encadenadas: tocarlas es el juicio). Apagada: pasa sin más a tu pantalla de inicio
 * de siempre ([Ammit.inicioNormal]).
 */
class InicioActivity : ComponentActivity() {
    companion object { const val HOST_ID = 7_001; private const val PIDE_CONFIGURAR = 71 }

    lateinit var host: AppWidgetHost
    private val mgr by lazy { AppWidgetManager.getInstance(this) }

    var apps by mutableStateOf<List<AppDeInicio>>(emptyList())
    /** Las apps ya se han leído al menos una vez. */
    @Volatile var cargadas = false
    var widgets by mutableStateOf<List<Ammit.Widget>>(emptyList())
    var colocando by mutableStateOf(false)
    var eligiendoWidget by mutableStateOf(false)
    /**
     * La animación que toca ahora: «entrar» (el sinfín de cadenas tapa la pantalla y salen las apps), «aparecer» (al volver del juicio:
     * solo salen las apps) o «salir» (las apps se van, antes de pasar a [SalidaActivity]); null, ninguna.
     */
    var animacion: String?
        get() = animEstado.value
        set(v) { ms.floatValue = 0f; animEstado.value = v }
    private val animEstado = mutableStateOf<String?>(null)
    /** Por dónde va la animación (ms), a cada fotograma. */
    val ms = androidx.compose.runtime.mutableFloatStateOf(0f)
    /** Al apagar (sin cortina): ya se han ido las apps; hasta irse, solo el campo de cadenas (nada de destellos). */
    var tapada by mutableStateOf(false)

    /** El widget a medio poner (esperando el permiso para ponerlo o su configuración). */
    private var pendiente = -1

    private val pedirPermiso = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendiente
        if (id < 0) return@registerForActivityResult
        if (r.resultCode == Activity.RESULT_OK) mgr.getAppWidgetInfo(id)?.let { configurar(id, it) } ?: cancelar(id) else cancelar(id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Barras transparentes con iconos claros: encima de tu fondo de pantalla
        enableEdgeToEdge(SystemBarStyle.dark(AColor.TRANSPARENT), SystemBarStyle.dark(AColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        Ammit.cargar(this)
        // Las cadenas del fondo, grabadas ya
        window.decorView.post { runCatching { Campo.dePantalla(this).precalentar() } }
        host = AppWidgetHost(applicationContext, HOST_ID)
        runCatching {
            val f = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package")
            }
            androidx.core.content.ContextCompat.registerReceiver(this, cambiosDeApps, f, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        }
        widgets = Ammit.widgets(this)
        setContent {
            BackHandler { if (eligiendoWidget) eligiendoWidget = false else if (colocando) colocando = false }
            PantInicio(this)
        }
    }

    /** Al instalar, quitar o actualizar una app, la lista se vuelve a leer. */
    private val cambiosDeApps = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: Intent?) { recargar(forzar = true) }
    }

    override fun onDestroy() { runCatching { unregisterReceiver(cambiosDeApps) }; super.onDestroy() }

    override fun onStart() { super.onStart(); runCatching { host.startListening() } }
    override fun onStop() { super.onStop(); runCatching { host.stopListening() } }

    override fun onResume() {
        super.onResume()
        Ammit.cargar(this)
        tapada = false
        // Cuando se quite la cortina de cadenas de encima, salen las apps
        Cortina.alQuitar = { runOnUiThread { if (animacion == "cortina") animacion = "aparecer" } }
        val anim = Ammit.tomaAnimacion(this)
        if (!Ammit.encendida) {
            // Al apagar: las apps se van, las cadenas dan un tirón y se pasa a tu inicio de siempre con las cadenas yéndose encima
            if (anim == "salir") { animacion = "salir"; return }
            if (pasarAlInicioNormal()) return
        } else if (anim == "entrar" || anim == "aparecer" || anim == "cortina") animacion = anim
        recargar()
    }

    /** Ha terminado la animación: al apagar, a tu inicio de siempre, con la capa de las cadenas yéndose ([SalidaActivity]). */
    fun alAcabarAnimacion() {
        val era = animacion
        animacion = null
        if (era == "salir" && !Ammit.encendida) {
            tapada = true
            val cn = Ammit.inicioNormal(this)
            if (cn == null || !SalidaActivity.abrir(this, cn)) pasarAlInicioNormal()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // El botón de casa estando ya aquí: se deja de colocar
        colocando = false; eligiendoWidget = false
    }

    /** Ammit apagada: a la pantalla de inicio de siempre. Devuelve false si no hay otra (entonces se queda Ammit). */
    private fun pasarAlInicioNormal(): Boolean {
        val cn = Ammit.inicioNormal(this) ?: return false
        return runCatching {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
            true
        }.getOrDefault(false)
    }

    private var ultimaCarga = 0L
    private var cargando = false

    /**
     * Lee tus apps (y sus iconos). Al volver a la pantalla de inicio solo si hace rato o si se ha instalado o quitado algo: leer todos
     * los iconos en plena animación de las apps daba tirones.
     */
    fun recargar(forzar: Boolean = false) {
        val ahora = android.os.SystemClock.elapsedRealtime()
        if (cargando || (!forzar && cargadas && ahora - ultimaCarga < 10 * 60_000)) return
        cargando = true; ultimaCarga = ahora
        lifecycleScope.launch {
            val l = withContext(Dispatchers.Default) { Apps.todas(this@InicioActivity) }
            // (si no ha cambiado nada, no se toca: así no se repinta la rejilla al volver al inicio)
            if (l.map { it.clave + it.nombre } != apps.map { it.clave + it.nombre }) apps = l
            cargadas = true
            cargando = false
        }
    }

    fun mover(desde: String, hacia: String) {
        val l = apps.toMutableList()
        val i = l.indexOfFirst { it.clave == desde }; val j = l.indexOfFirst { it.clave == hacia }
        if (i < 0 || j < 0 || i == j) return
        val a = l.removeAt(i); l.add(j, a)
        apps = l
    }

    fun guardarOrden() = Ammit.ponOrden(this, apps.map { it.clave })

    // ------------------------------------------------------------------ widgets de la familia

    /** Los widgets que se pueden poner: los de las seis apps de la familia. */
    fun widgetsDisponibles(): List<AppWidgetProviderInfo> =
        mgr.installedProviders.filter { Ammit.pasa(it.provider.packageName) && it.provider.packageName != Ammit.PROPIO }
            .sortedWith(compareBy({ Ammit.FAMILIA.indexOfFirst { f -> f.paquete == it.provider.packageName } }, { it.loadLabel(packageManager) }))

    fun ponerWidget(info: AppWidgetProviderInfo) {
        eligiendoWidget = false
        val id = host.allocateAppWidgetId()
        pendiente = id
        if (mgr.bindAppWidgetIdIfAllowed(id, info.provider)) configurar(id, info)
        else pedirPermiso.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider))
    }

    private fun configurar(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            pendiente = id
            runCatching { host.startAppWidgetConfigureActivityForResult(this, id, 0, PIDE_CONFIGURAR, null) }.onFailure { anadir(id, info) }
        } else anadir(id, info)
    }

    @Deprecated("Lo pide startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PIDE_CONFIGURAR) return
        val id = pendiente
        if (id < 0) return
        val info = mgr.getAppWidgetInfo(id)
        if (resultCode == Activity.RESULT_OK && info != null) anadir(id, info) else cancelar(id)
    }

    private fun anadir(id: Int, info: AppWidgetProviderInfo) {
        pendiente = -1
        val dp = resources.displayMetrics.density
        val alto = (info.minHeight / dp).toInt().coerceIn(90, 340).let { if (it < 120) it + 30 else it }
        widgets = widgets + Ammit.Widget(id, alto)
        Ammit.ponWidgets(this, widgets)
    }

    private fun cancelar(id: Int) { pendiente = -1; runCatching { host.deleteAppWidgetId(id) } }

    fun quitarWidget(id: Int) {
        widgets = widgets.filterNot { it.id == id }
        Ammit.ponWidgets(this, widgets)
        runCatching { host.deleteAppWidgetId(id) }
    }

    fun cambiarAlto(id: Int, mas: Int) {
        widgets = widgets.map { if (it.id == id) it.copy(alto = (it.alto + mas).coerceIn(80, 460)) else it }
        Ammit.ponWidgets(this, widgets)
    }

    fun subirWidget(id: Int) {
        val l = widgets.toMutableList(); val i = l.indexOfFirst { it.id == id }
        if (i > 0) { val w = l.removeAt(i); l.add(i - 1, w); widgets = l; Ammit.ponWidgets(this, l) }
    }

    fun infoDe(id: Int): AppWidgetProviderInfo? = mgr.getAppWidgetInfo(id)
}
