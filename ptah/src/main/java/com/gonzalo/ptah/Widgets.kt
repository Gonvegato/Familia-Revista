package com.gonzalo.ptah

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.SearchManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.speech.RecognizerIntent
import android.util.TypedValue
import android.view.Gravity
import android.widget.RemoteViews
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * El widget de Ptah: uno solo para todos los diseños ([WPtah]), redimensionable a cualquier tamaño. Al ponerlo en la pantalla de inicio,
 * [ConfigWidgetActivity] te deja elegir uno de tus diseños (o hacer uno). Cada widget se dibuja entero para su tamaño de verdad, en claro y
 * en oscuro ([Pintor], el mismo dibujo que la vista previa del editor), y encima lleva:
 *  - los relojes: la hora, la fecha y las agujas van **dibujadas** con la letra de verdad del diseño, y el widget se vuelve a pintar
 *    cada minuto con una alarma exacta que no despierta al móvil ([Minutero]: con la pantalla apagada no gasta nada; al encenderla, la
 *    alarma pendiente salta y se pone en hora). Si Android no deja alarmas exactas, o eliges en Ajustes «la letra del móvil», van como
 *    `TextClock` y `AnalogClock` ([Vivo]), que se mueven solos pero con la letra del sistema (un `android:fontFamily` en un widget no lo
 *    respetan todos los lanzadores: en ColorOS sale Roboto);
 *  - las zonas para tocar ([Zona]): lo de cada tipo (abrir una app, buscar, las alarmas…).
 * Se vuelve a pintar al cambiar el diseño o la apariencia, a medianoche (lo que cambia cada día: el mes, la cuenta atrás), cada 15–30
 * minutos ([Refresco]: el tiempo y la batería) y al abrir Ptah. La configuración de cada widget (qué diseño lleva) es de este móvil: no viaja.
 */

class WPtah : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.pintar(ctx, ids, goAsync())
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle) = Widgets.pintar(ctx, intArrayOf(id), goAsync())
    override fun onDeleted(ctx: Context, ids: IntArray) = Widgets.olvidar(ctx, ids)
    override fun onEnabled(ctx: Context) { Refresco.programa(ctx) }
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.action) {
            Minutero.ACCION -> Widgets.pintarRelojes(ctx, goAsync())
            Widgets.ACCION_MEDIANOCHE, Intent.ACTION_BOOT_COMPLETED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED", Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_LOCALE_CHANGED ->
                Widgets.pintar(ctx, Widgets.ids(ctx), goAsync())
            else -> super.onReceive(ctx, i)
        }
    }
}

object Widgets {
    const val ACCION_MEDIANOCHE = "com.gonzalo.ptah.MEDIANOCHE"
    /** Abrir Ptah en el editor de un diseño. */
    const val EXTRA_EDITAR = "editar"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var pendiente: Job? = null
    /** En las pruebas: lo que dibujan los widgets (la hora, el tiempo…). */
    @Volatile var datos: ((Context) -> Datos)? = null
    private fun datosDe(ctx: Context): Datos = datos?.invoke(ctx) ?: DatosAndroid(ctx)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ptah_widgets", Context.MODE_PRIVATE)

    /** El diseño que lleva ese widget (su id), o null si aún no tiene. */
    fun disenoDe(ctx: Context, id: Int): String? = prefs(ctx).getString("w$id", null)
    fun asigna(ctx: Context, id: Int, diseno: String) { prefs(ctx).edit().putString("w$id", diseno).commit() }
    fun olvidar(ctx: Context, ids: IntArray) { prefs(ctx).edit().apply { ids.forEach { remove("w$it") } }.apply() }
    /** Cuántos widgets llevan cada diseño (para la portada). */
    fun usos(ctx: Context): Map<String, Int> = ids(ctx).toList().mapNotNull { disenoDe(ctx, it) }.groupingBy { it }.eachCount()

    fun ids(ctx: Context): IntArray = runCatching { AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, WPtah::class.java)) }.getOrNull() ?: IntArray(0)

    /** Repinta esos widgets (en segundo plano: leer las fotos y las letras lleva un momento). */
    fun pintar(ctx: Context, ids: IntArray, res: BroadcastReceiver.PendingResult?) {
        val app = ctx.applicationContext
        scope.launch {
            try {
                withTimeoutOrNull(9_000) {
                    preparar(app)
                    ids.forEach { id -> try { pintarUno(app, id) } catch (e: CancellationException) { throw e } catch (e: Exception) { } }
                }
            } finally { res?.finish() }
            runCatching { programaMedianoche(app) }
            runCatching { Minutero.programa(app) }
        }
    }

    /** Algo cambió (un diseño, la apariencia, lo que llega de otro móvil): se repintan todos, sin prisa. */
    fun pedir(ctx: Context) {
        val app = ctx.applicationContext
        val hay = ids(app)
        if (hay.isEmpty()) return
        pendiente?.cancel()
        pendiente = scope.launch {
            delay(150)
            preparar(app)
            hay.forEach { id -> try { pintarUno(app, id) } catch (e: CancellationException) { throw e } catch (e: Exception) { } }
            runCatching { programaMedianoche(app) }
            runCatching { Minutero.programa(app) }
        }
    }

    /** Lee lo guardado (la apariencia): un widget puede pintarse sin que la app esté abierta. */
    fun preparar(ctx: Context) {
        Skin.cargar(ctx)
        if (TemaAtrio.tonos == null) TemaAtrio.cargarGuardado(ctx)
        Cuenta.cargar(ctx)
    }

    /** De uno en uno: dos repintados a la vez (el del minuto y el de un cambio) se pisaban. */
    private val cerrojo = kotlinx.coroutines.sync.Mutex()

    suspend fun pintarUno(ctx: Context, id: Int) = cerrojo.withLockCompat { pintarUnoYa(ctx, id) }

    private suspend fun <T> kotlinx.coroutines.sync.Mutex.withLockCompat(f: suspend () -> T): T { lock(); try { return f() } finally { unlock() } }

    private suspend fun pintarUnoYa(ctx: Context, id: Int) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val d = disenoDe(ctx, id)?.let { Disenos.de(ctx, it) }
        val notas = ArrayList<String>()
        try {
            if (d != null) Letras.prepara(ctx, listOf(d))
            mgr.updateAppWidget(id, vistasDe(ctx, id, d, mgr.getAppWidgetOptions(id), notas))
            Registro.pintado(ctx, id, notas)
        } catch (e: CancellationException) { throw e } catch (e: Throwable) {
            Registro.pintado(ctx, id, notas, e)
            if (e is Exception) throw e
        }
    }

    /**
     * Los tamaños a los que se pinta un widget. Android 12 o más: los que dice el lanzador (OPTION_APPWIDGET_SIZES, en dp: uno por
     * orientación, normalmente); si no los dice, el de vertical (ancho mínimo × alto máximo) y el de apaisado (ancho máximo × alto mínimo)
     * y, por si acaso, los de siempre (1×1, 1×2 de pie, 2×1, 2×2, 4×2, 4×4): el lanzador elige él solo el que le va, aunque no avise al
     * cambiarlo de tamaño. Antes de Android 12, uno: el de la orientación de ahora.
     */
    fun tamanosPara(ctx: Context, o: Bundle?): List<Pair<Float, Float>> {
        if (Build.VERSION.SDK_INT < 31) return listOf(tamano(ctx, o))
        @Suppress("DEPRECATION")
        val dados = o?.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
            .filter { it.width > 20f && it.height > 20f }.map { it.width to it.height }
        val l = ArrayList<Pair<Float, Float>>()
        if (dados.isNotEmpty()) l += dados
        else {
            val minW = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0; val maxW = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0
            val minH = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0; val maxH = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
            if (minW > 20 && maxH > 20) l += minW.toFloat() to maxH.toFloat()
            if (maxW > 20 && minH > 20) l += maxW.toFloat() to minH.toFloat()
            l += listOf(Tamanos.Celdas(1, 1), Tamanos.Celdas(1, 2), Tamanos.Celdas(2, 1), Tamanos.Celdas(2, 2), Tamanos.Celdas(4, 2), Tamanos.Celdas(4, 4)).map { Tamanos.dp(it) }
        }
        return l.map { (w, h) -> Math.round(w).toFloat() to Math.round(h).toFloat() }.distinct().take(8)
    }

    /** Lo que se manda al lanzador: con varios tamaños (Android 12 o más), un RemoteViews por tamaño y que él elija. [notas]: para el diagnóstico. */
    fun vistasDe(ctx: Context, id: Int, d: Diseno?, o: Bundle?, notas: MutableList<String> = ArrayList()): RemoteViews {
        val ts = tamanosPara(ctx, o)
        if (Build.VERSION.SDK_INT < 31 || ts.size == 1) { val (w, h) = ts.first(); return vistas(ctx, id, d, w, h, notas = notas) }
        val area = ts.sumOf { (w, h) -> (w * h).toDouble() }.toFloat()
        return RemoteViews(ts.associate { (w, h) -> android.util.SizeF(w, h) to vistas(ctx, id, d, w, h, area, notas) })
    }

    /** Claro, oscuro, o null = como el sistema (se mandan los dos y Android enseña el que toca). Con colores que no dependen de eso, uno. */
    fun noche(d: Diseno?): Boolean? = when {
        d != null && !d.estilo.delTema -> false
        Skin.coloresFijos -> false
        else -> when (Modo.eleccion) { 1 -> false; 2 -> true; else -> null }
    }

    /** El tamaño del widget en dp (en vertical: el ancho mínimo y el alto máximo, como hace Android). */
    fun tamano(ctx: Context, o: Bundle?): Pair<Float, Float> {
        val vertical = ctx.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val w = o?.getInt(if (vertical) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0
        val h = o?.getInt(if (vertical) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        val (dw, dh) = Tamanos.dp(Tamanos.Celdas(2, 2))
        return (if (w > 20) w.toFloat() else dw) to (if (h > 20) h.toFloat() else dh)
    }

    /** Píxeles por dp del dibujo: los de la pantalla, sin pasarse de memoria (Android limita lo que pesa un widget). */
    fun escala(ctx: Context, w: Float, h: Float, copias: Int, area: Float = w * h): Float {
        val d = ctx.resources.displayMetrics.density
        // (con varios tamaños, entre todos: Android limita lo que pesan todos los dibujos de un widget)
        val tope = (if (copias == 2) 1_100_000f else 1_800_000f) * (if (area > w * h * 1.01f) 1.4f else 1f)
        return min(d, sqrt(tope / area))
    }

    /** ¿El móvil está ahora en oscuro? (para los relojes dibujados, que se repintan cada minuto: un solo dibujo) */
    fun nocheAhora(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /**
     * Lo que se manda al lanzador para ese widget: el dibujo (o los dos), lo que se mueve solo (si va como `TextClock`/`AnalogClock`) y las
     * zonas. Todo de cero cada vez: muchos lanzadores no vuelven a inflar el widget, sino que aplican lo nuevo encima de lo que ya tenían
     * (`RemoteViews.reapply`), y cada `addView` se sumaba a las vistas de antes (en un OPPO, la hora salía tres veces, una por cada tamaño
     * al que se había pintado). Por eso primero se vacían los huecos y se quitan los dibujos de antes.
     */
    fun vistas(ctx: Context, id: Int, d: Diseno?, w: Float, h: Float, area: Float = w * h, notas: MutableList<String>? = null): RemoteViews {
        val datos = datosDe(ctx)
        val diseno = d ?: sinDiseno(id)
        val reloj = Minutero.esReloj(diseno)
        // La hora: dibujada con la letra del diseño (y repintada cada minuto) o, si no se puede, TextClock/AnalogClock con la del móvil
        val relojVivo = reloj && Minutero.relojVivo(ctx)
        var n = noche(d)
        if (reloj && !relojVivo && n == null) n = nocheAhora(ctx)
        val rv = RemoteViews(ctx.packageName, R.layout.w_marco)
        listOf(R.id.w_vivos, R.id.w_vivos_dia, R.id.w_vivos_noche, R.id.w_zonas).forEach { rv.removeAllViews(it) }
        listOf(R.id.w_img, R.id.w_dia, R.id.w_noche).forEach { rv.setImageViewResource(it, 0) }
        val dens = ctx.resources.displayMetrics.density
        val l: Lienzo
        if (n == null) {
            val s = escala(ctx, w, h, 2, area)
            l = Pintor.pinta(ctx, diseno, w, h, false, s, datos, enWidget = true, relojVivo = relojVivo)
            val ln = Pintor.pinta(ctx, diseno, w, h, true, s, datos, enWidget = true, relojVivo = relojVivo)
            rv.setImageViewBitmap(R.id.w_dia, l.bm)
            rv.setImageViewBitmap(R.id.w_noche, ln.bm)
            l.vivos.forEach { rv.addView(R.id.w_vivos_dia, vivo(ctx, it, w, h, dens, 2)) }
            ln.vivos.forEach { rv.addView(R.id.w_vivos_noche, vivo(ctx, it, w, h, dens, 2)) }
        } else {
            l = Pintor.pinta(ctx, diseno, w, h, n, escala(ctx, w, h, 1, area), datos, enWidget = true, relojVivo = relojVivo)
            rv.setImageViewBitmap(R.id.w_img, l.bm)
            l.vivos.forEach { rv.addView(R.id.w_vivos, vivo(ctx, it, w, h, dens, 1)) }
        }
        l.zonas.forEachIndexed { i, z -> rv.addView(R.id.w_zonas, zona(ctx, z, id, i, w, h, dens)) }
        notas?.add("${w.roundToInt()}×${h.roundToInt()} dp: ${l.proporcion.name.lowercase()}, ${l.bm.width}×${l.bm.height} px" + (if (n == null) " ×2 (claro y oscuro)" else "") +
            if (!reloj) "" else if (relojVivo) ", hora con TextClock (${l.vivos.size} vivas)" else ", hora dibujada")
        return rv
    }

    /** Repinta solo los widgets de reloj (lo que hace el [Minutero] cada minuto). */
    fun pintarRelojes(ctx: Context, res: BroadcastReceiver.PendingResult?) {
        val app = ctx.applicationContext
        scope.launch {
            try {
                withTimeoutOrNull(9_000) {
                    preparar(app)
                    Minutero.relojes(app).forEach { id -> try { pintarUno(app, id) } catch (e: CancellationException) { throw e } catch (e: Exception) { } }
                    Minutero.apunta(app)
                }
            } finally { res?.finish() }
            runCatching { Minutero.programa(app) }
        }
    }

    /** Lo que sale en un widget sin diseño (recién puesto sin elegir, o con el suyo borrado): un toque y eliges otro. */
    private fun sinDiseno(id: Int) = Diseno("sin$id", "Elige un diseño", TipoNota.id, Estilo(radio = 22f), mapOf("ante" to "Ptah", "titulo" to "Elige un diseño", "texto" to "Un toque y eliges cuál de tus widgets va aquí."))

    private fun huecos(r: android.graphics.RectF, w: Float, h: Float, d: Float): IntArray {
        fun px(v: Float) = (max(0f, v) * d).roundToInt()
        return intArrayOf(px(r.left), px(r.top), px(w - r.right), px(h - r.bottom))
    }

    /** Una zona para tocar, en su sitio. */
    private fun zona(ctx: Context, z: Zona, id: Int, i: Int, w: Float, h: Float, d: Float): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.w_zona)
        val (l, t, r, b) = huecos(z.r, w, h, d).toList()
        v.setViewPadding(R.id.w_hueco, l, t, r, b)
        intent(ctx, z.accion, id)?.let { v.setOnClickPendingIntent(R.id.w_toque, PendingIntent.getActivity(ctx, id * 64 + i, it, PI)) }
        if (z.desc.isNotEmpty()) v.setContentDescription(R.id.w_toque, z.desc)
        return v
    }

    /** Lo que se mueve solo, en su sitio. */
    fun vivo(ctx: Context, x: Vivo, w: Float, h: Float, d: Float, copias: Int): RemoteViews {
        val (l, t, r, b) = huecos(x.r, w, h, d).toList()
        return when (x) {
            is Vivo.Texto -> RemoteViews(ctx.packageName, x.letra.diseno).apply {
                coloca(this, x.r, w, h, l, t, r, b)
                setTextColor(R.id.w_reloj, x.color)
                // (el mismo formato con el móvil en 12 o en 24 horas, salvo el «como el móvil», que lleva los dos)
                setCharSequence(R.id.w_reloj, "setFormat24Hour", x.formato)
                setCharSequence(R.id.w_reloj, "setFormat12Hour", x.formato12)
                setInt(R.id.w_reloj, "setGravity", Gravity.CENTER_VERTICAL or when (x.al) { Alineacion.IZQUIERDA -> Gravity.START; Alineacion.CENTRO -> Gravity.CENTER_HORIZONTAL; Alineacion.DERECHA -> Gravity.END })
            }
            is Vivo.Agujas -> agujas(ctx, x, l, t, r, b, d, copias, w, h)
        }
    }

    /**
     * Pone lo vivo en su sitio (con márgenes: un widget no deja cambiar pesos desde fuera). Para que caiga donde toca, el widget se pinta a
     * los tamaños que da el propio lanzador ([vistasDe]); y el `TextClock` ajusta su letra a su hueco (autoSize).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun coloca(v: RemoteViews, r: android.graphics.RectF, w: Float, h: Float, l: Int, t: Int, rr: Int, b: Int, centro: Int = R.id.w_reloj) {
        v.setViewPadding(R.id.w_hueco, l, t, rr, b)
    }

    /** El lado (px) de las máscaras de las agujas: el de la esfera, sin pasarse (cada una pesa un byte por píxel). */
    fun ladoAgujas(r: android.graphics.RectF, d: Float, copias: Int) = min(r.width() * d, if (copias == 2) 560f else 760f).roundToInt().coerceAtLeast(16)

    private fun agujas(ctx: Context, x: Vivo.Agujas, l: Int, t: Int, r: Int, b: Int, d: Float, copias: Int, wW: Float, hW: Float): RemoteViews {
        if (Build.VERSION.SDK_INT < 31) {
            // Android 10 y 11: las agujas de bastón del diseño XML (no se pueden cambiar desde fuera), del color que mejor se acerque
            val claras = Lienzo.luz(x.minutera) > 0.5f
            return RemoteViews(ctx.packageName, if (claras) R.layout.w_vivo_agujas_claras else R.layout.w_vivo_agujas).apply { setViewPadding(R.id.w_hueco, l, t, r, b) }
        }
        val lado = ladoAgujas(x.r, d, copias)
        return RemoteViews(ctx.packageName, R.layout.w_vivo_agujas).apply {
            coloca(this, x.r, wW, hW, l, t, r, b, R.id.w_agujas)
            // La esfera, vacía (la de verdad va en el dibujo de debajo) y del mismo tamaño que las agujas: así el reloj las escala igual
            setIcon(R.id.w_agujas, "setDial", Icon.createWithBitmap(Bitmap.createBitmap(lado, lado, Bitmap.Config.ALPHA_8)))
            setIcon(R.id.w_agujas, "setHourHand", Icon.createWithBitmap(Agujas.mascara(lado, Agujas.Pieza.HORA, x.forma)))
            setColorStateList(R.id.w_agujas, "setHourHandTintList", ColorStateList.valueOf(x.horaria))
            setIcon(R.id.w_agujas, "setMinuteHand", Icon.createWithBitmap(Agujas.mascara(lado, Agujas.Pieza.MINUTO, x.forma)))
            setColorStateList(R.id.w_agujas, "setMinuteHandTintList", ColorStateList.valueOf(x.minutera))
            if (x.segundero != null) {
                setIcon(R.id.w_agujas, "setSecondHand", Icon.createWithBitmap(Agujas.mascara(lado, Agujas.Pieza.SEGUNDO, x.forma)))
                setColorStateList(R.id.w_agujas, "setSecondHandTintList", ColorStateList.valueOf(x.segundero))
            }
        }
    }

    private const val PI = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    /** Lo que abre cada [Accion] (null: nada). */
    fun intent(ctx: Context, a: Accion, widget: Int = 0): Intent? {
        val nueva = Intent.FLAG_ACTIVITY_NEW_TASK
        return when (a) {
            Accion.AbrirPtah -> Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addFlags(nueva or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            is Accion.Editar -> if (a.id.startsWith("sin")) intent(ctx, Accion.Configurar, widget) else Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("ptah://editar/${a.id}")).putExtra(EXTRA_EDITAR, a.id).addFlags(nueva or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            is Accion.App -> ctx.packageManager.getLaunchIntentForPackage(a.paquete)?.addFlags(nueva) ?: intent(ctx, Accion.AbrirPtah)
            is Accion.Web -> Intent(Intent.ACTION_VIEW, Uri.parse(a.url)).addFlags(nueva)
            is Accion.Buscar -> if (a.navegador.isNotBlank()) ctx.packageManager.getLaunchIntentForPackage(a.navegador)?.addFlags(nueva) ?: intent(ctx, Accion.Buscar(""))
                else Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, "").addFlags(nueva)
            Accion.BuscarVoz -> Intent(RecognizerIntent.ACTION_WEB_SEARCH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH).addFlags(nueva)
            Accion.Alarmas -> Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(nueva)
            Accion.Calendario -> Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")).addFlags(nueva)
            Accion.Bateria -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(nueva)
            Accion.Configurar -> Intent(ctx, ConfigWidgetActivity::class.java).setData(Uri.parse("ptah://widget/$widget"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget).putExtra(ConfigWidgetActivity.EXTRA_CAMBIAR, true).addFlags(nueva)
        }
    }

    /** ¿Algún widget necesita esto? (para programar lo que haga falta) */
    fun necesitan(ctx: Context): Set<Necesidad> = ids(ctx).toList().mapNotNull { disenoDe(ctx, it)?.let { d -> Disenos.de(ctx, d) } }
        .flatMap { d -> Tipos.de(d.tipo)?.necesita(d).orEmpty() }.toSet()

    /** A medianoche (y un poco después, por si acaso), lo que cambia cada día se vuelve a pintar. Inexacto: no hace falta el permiso de alarmas exactas. */
    fun programaMedianoche(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, WPtah::class.java).setAction(ACCION_MEDIANOCHE), PI)
        if (Necesidad.MEDIANOCHE !in necesitan(ctx)) { am.cancel(pi); return }
        val manana = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 20_000L
        am.setWindow(AlarmManager.RTC, manana, 10 * 60_000L, pi)
    }
}

/**
 * El refresco de fondo (WorkManager): cada 30 minutos, o cada 15 si hay un widget de batería, baja el tiempo que toque (si ha pasado su
 * rato: [CacheTiempo]) y repinta los widgets del tiempo y de la batería.
 */
class Refresco(ctx: Context, p: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        Widgets.preparar(c)
        Refresco.tiempo(c)
        Widgets.pedir(c)
        return Result.success()
    }

    companion object {
        /** Baja el tiempo de los diseños de los widgets que hay (y de [extra]). Bloquea (red). */
        fun tiempo(c: Context, extra: List<Diseno> = emptyList(), forzar: Boolean = false) {
            val ds = Widgets.ids(c).toList().mapNotNull { Widgets.disenoDe(c, it)?.let { d -> Disenos.de(c, d) } } + extra
            ds.filter { it.tipo == Tiempo.id }.mapNotNull { sitioDe(c, it) }.distinctBy { CacheTiempo.clave(it.first, it.second) }
                .forEach { (la, lo) -> runCatching { CacheTiempo.refresca(c, la, lo, forzar) } }
        }

        fun programa(ctx: Context) {
            val nec = runCatching { Widgets.necesitan(ctx) }.getOrDefault(emptySet())
            val wm = runCatching { androidx.work.WorkManager.getInstance(ctx) }.getOrNull() ?: return
            if (Widgets.ids(ctx).isEmpty() || (Necesidad.TIEMPO !in nec && Necesidad.BATERIA !in nec)) { wm.cancelUniqueWork("refresco"); return }
            val minutos = if (Necesidad.BATERIA in nec) 15L else 30L
            val req = androidx.work.PeriodicWorkRequestBuilder<Refresco>(minutos, java.util.concurrent.TimeUnit.MINUTES).build()
            // (UPDATE: si cambia de 30 a 15 minutos, se queda con el nuevo)
            wm.enqueueUniquePeriodicWork("refresco", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
