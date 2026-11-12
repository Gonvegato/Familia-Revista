package com.gonzalo.ammit

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.core.content.res.ResourcesCompat
import kotlin.math.sin

/**
 * La cortina de cadenas: una capa del vigilante (servicio de accesibilidad) que se pinta por encima de TODO, también mientras Android
 * cambia de una pantalla a otra. Así lo que pasa debajo (abrirse un inicio u otro, cerrarse una app) no se ve nunca.
 *
 * - Encender: las cadenas tapan lo que tengas delante; tapado del todo, debajo se abre la pantalla de inicio de Ammit (con ese mismo
 *   campo de fondo, pintado igual) y, cuando está lista, la cortina se funde y salen las apps.
 * - Apagar: aparece el campo entero, debajo se abre tu inicio de siempre y, cuando ya está quieto, las cadenas dan un tirón y se van.
 * - El juicio: entero en la cortina (cuatro cadenas, «AMMIT.», la ráfaga). Nada más empezar, debajo se va la app que no pasa y se abre
 *   la pantalla de inicio de Ammit; al final la ráfaga es su fondo, la cortina se funde y salen las apps.
 *
 * Solo hay una cortina a la vez; mientras está, los toques del botón no hacen nada. Pase lo que pase, se quita sola a los pocos segundos.
 */
object Cortina {
    /** La pantalla de inicio de Ammit ya está pintada debajo, con sus apps (la cortina se puede quitar). */
    @Volatile var inicioListo = false
    /** La cortina «tapar» ya está pintada (el juicio a la antigua se puede ir por debajo). */
    @Volatile var tapado = false
    /** Lo que hace la pantalla de inicio cuando la cortina empieza a irse (sacar las apps). */
    @Volatile var alQuitar: (() -> Unit)? = null

    private var activa: VistaCortina? = null
    private val principal = Handler(Looper.getMainLooper())

    /** ¿Hay una cortina puesta ahora? */
    val ocupada: Boolean get() = activa != null

    /** Enciende o apaga Ammit con la cortina. Devuelve false si el vigilante no está (entonces se hace a la antigua). */
    fun cambiar(c: Context, on: Boolean, abrir: (Intent) -> Unit): Boolean {
        val v = Vigia.vivo ?: return false
        if (ocupada) return true   // (a media animación, otro toque no hace nada)
        if (on) {
            if (!poner(v, "entrar")) return false
            Ammit.encender(c, true, animar = false)
            BotonAmmit.avisar(c)
            // Plegar la barra de arriba sin abrir nada (lo de debajo se queda; las cadenas lo tapan)
            abrir(Intent(c, PlegarActivity::class.java))
        } else {
            val inicio = Ammit.inicioNormal(c)
            if (!poner(v, "salir")) return false
            Ammit.encender(c, false, animar = false)
            BotonAmmit.avisar(c)
            abrir(if (inicio != null) Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(inicio).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                  else Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        }
        return true
    }

    /** Pone una cortina (desde el vigilante): «entrar», «salir», «juicio» (con el nombre de la [app]) o «tapar». */
    fun poner(s: AccessibilityService, modo: String, app: String = ""): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) { principal.post { poner(s, modo, app) }; return true }
        if (activa != null) return false
        return runCatching {
            val wm = s.getSystemService(WindowManager::class.java)
            inicioListo = false; tapado = false
            val vista = VistaCortina(s, modo, app)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // (se queda con los toques mientras está: así no se abre sin querer nada de lo que hay debajo; como mucho 8 s)
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT)
            lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            lp.windowAnimations = 0
            lp.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (Build.VERSION.SDK_INT >= 30) lp.fitInsetsTypes = 0
            // (del tamaño de la pantalla entera, como el campo)
            val (w, h) = Campo.pantalla(s); lp.width = w; lp.height = h
            vista.quitar = {
                runCatching { wm.removeView(vista) }
                if (activa === vista) activa = null
            }
            wm.addView(vista, lp)
            activa = vista
            // Pase lo que pase (la pantalla de debajo no avisa, algo falla…), a los 8 s no queda nada encima
            principal.postDelayed({ if (activa === vista) vista.quitar() }, 8_000)
            true
        }.getOrDefault(false)
    }

    /** Solo para las pruebas: sin cortina ni nada pendiente. */
    internal fun reiniciar() { activa?.let { runCatching { it.quitar() } }; activa = null; inicioListo = false; tapado = false; alQuitar = null }

    /** Quita la cortina que haya, ya (si el vigilante se apaga). */
    fun quitarYa() { principal.post { activa?.quitar?.invoke() } }
}

/** Pliega la barra de arriba y se va: no enseña nada. */
class PlegarActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}

/** Lo que se ve en la cortina, fotograma a fotograma. */
internal class VistaCortina(private val c: Context, private val modo: String, private val app: String) : View(c) {
    /** Un momento fijo (ms), para probarla; si no, el reloj de verdad. */
    var relojFijo: Float? = null
    var quitar: () -> Unit = {}
    private var t0 = -1L
    private var hecho = false
    private var pedido = false
    private val capa = Paint()
    private val negro = Paint().apply { color = android.graphics.Color.BLACK }
    private val sitio = IntArray(2)

    /** Abre debajo la pantalla de inicio de Ammit, preparada para salir cuando se quite la cortina. */
    private fun abrirInicioDebajo() {
        if (pedido) return
        pedido = true
        // Debajo, tu pantalla de inicio de siempre (la de Oppo, la de Samsung…): al irse las cadenas, es lo que queda
        val casa = Ammit.inicioNormal(c)?.let { Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(it) }
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        runCatching { c.startActivity(casa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)) }
    }

    /**
     * Las cadenas se van, una tras otra, por donde vinieron (desde [r] = 0), dejando ver tu inicio. Devuelve si ya no queda ninguna.
     * [oscuro]: lo apagadas que estaban.
     */
    private fun retira(cv: Canvas, campo: Campo, r: Float, oscuro: Float): Boolean {
        val n = campo.n
        val (dx, dy) = Tiempos.temblor(r, Tiempos.golpesSalir(n))
        cv.save(); cv.translate(dx * campo.u, dy * campo.u)
        campo.pinta(cv, oscuro, Tiempos.salirBase(r)) { k -> Tiempos.salir(r, k, n) }
        cv.restore()
        return r >= Tiempos.salidaFin(n)
    }

    private fun fin() {
        if (hecho) return
        hecho = true
        post { quitar() }
    }

    /** Cuándo se vio que la pantalla de inicio estaba pintada debajo, y cuándo empezó a irse la cortina. */
    private var tListo = -1f
    private var tFundido = -1f

    /**
     * Encima de la pantalla de inicio de Ammit ya pintada: se espera un poco (por si Android aún está cambiando de pantalla) y la cortina
     * se funde (220 ms) mientras salen las apps. Debajo está pintado lo mismo, así que no se nota; y si algo difiriese, es un fundido.
     */
    private fun irse(ms: Float, minimo: Float): Boolean {
        if (tFundido < 0) {
            if (Cortina.inicioListo && tListo < 0) tListo = ms
            if ((tListo >= 0 && ms >= minimo && ms - tListo >= 150) || ms - minimo > 3000) { tFundido = ms; Cortina.alQuitar?.invoke() } else return false
        }
        val f = Tiempos.clamp((ms - tFundido) / 220)
        alpha = 1f - f
        if (f >= 1f) { fin(); return true }
        return false
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean = true

    private var campoGuardado: Campo? = null
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) { super.onConfigurationChanged(newConfig); campoGuardado = null }

    override fun onDraw(cv: Canvas) {
        if (hecho) return
        // (esto corre dentro del vigilante: un fallo al pintar no puede tumbarlo, que Android lo daría por «no funcionando»)
        try { dibuja(cv) } catch (e: Throwable) { Fallos.apunta(c, "cortina", e); fin() }
    }

    private fun dibuja(cv: Canvas) {
        val campo = campoGuardado ?: Campo.dePantalla(c).also { campoGuardado = it }
        if (t0 < 0) t0 = SystemClock.uptimeMillis()
        val ms = relojFijo ?: (SystemClock.uptimeMillis() - t0).toFloat()
        // (anclado a la pantalla, como el campo de la pantalla de inicio)
        getLocationOnScreen(sitio)
        cv.save(); cv.translate(-sitio[0].toFloat(), -sitio[1].toFloat())
        val sigue = try { pinta(cv, campo, ms) } finally { cv.restore() }
        if (sigue) postInvalidateOnAnimation()
    }

    /** Pinta el momento [ms]; devuelve si hay que seguir. */
    private fun pinta(cv: Canvas, campo: Campo, ms: Float): Boolean {
        val n = campo.n
        val u = campo.u
        when (modo) {
            "entrar" -> {
                // El sinfín de cadenas tapa lo que haya; tapado, debajo se abre tu inicio de siempre (y se le deja acabar su animación);
                // luego las cadenas se van y queda tu inicio, ya con Ammit encendida
                val r = ms - 2150
                if (r < 0) {
                    val (dx, dy) = Tiempos.temblor(ms, Tiempos.golpesEntrar(n))
                    cv.save(); cv.translate(dx * u, dy * u)
                    campo.pinta(cv, Tiempos.entrarOscuro(ms), Tiempos.entrarBase(ms)) { k -> Tiempos.entrar(ms, k) }
                    cv.restore()
                    if (ms >= 1480) abrirInicioDebajo()
                } else if (retira(cv, campo, r, Campo.OSCURO)) { fin(); return false }
            }
            "casa" -> {
                // Encendida, ibas a tu inicio de siempre: el campo entero (aparece en un instante), debajo se abre el de Ammit con sus
                // apps ya puestas y la cortina se funde
                val a = Tiempos.clamp(ms / 120)
                if (a < 1f) { capa.alpha = (255 * a).toInt(); cv.saveLayer(null, capa) } else cv.save()
                campo.pinta(cv, Campo.OSCURO, 1f) { 1f }
                cv.restore()
                if (!pedido) { pedido = true; Ammit.ponAnimacion(c, "casa")
                    runCatching { c.startActivity(Intent(c, InicioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)) } }
                if (irse(ms, 120f)) return false
            }
            "tapar" -> {
                campo.pinta(cv, Campo.OSCURO, 1f) { 1f }
                Cortina.tapado = true
                if (irse(ms, 0f)) return false
            }
            "juicio" -> {
                if (ms >= 60) abrirInicioDebajo()
                // Tapado del todo por la ráfaga, un momento quieto, y las cadenas se van: queda tu inicio de siempre (la app que no pasa,
                // detrás, ya cerrada)
                val r = ms - Tiempos.JUICIO_MS - 300
                if (r >= 0) { if (retira(cv, campo, r, Campo.OSCURO)) { fin(); return false }; return true }
                val (dx, dy) = Tiempos.temblor(ms, Tiempos.golpesJuicio(n))
                // (un fundido a negro de un instante: la app que no pasa se apaga, no se corta)
                negro.alpha = (255 * Tiempos.clamp(ms / 90)).toInt()
                cv.drawRect(-400f, -400f, campo.ancho + 400f, campo.alto + 400f, negro)
                cv.save(); cv.translate(dx * u, dy * u)
                CUATRO.forEachIndexed { k, l ->
                    campo.cadena(cv, l[0] * campo.ancho, l[1] * campo.alto, l[2] * campo.ancho, l[3] * campo.alto, Tiempos.juicioCuatro(ms, k), 3.25f)
                }
                rotulo(cv, campo, Tiempos.juicioRotulo(ms))
                if (ms >= Tiempos.JUICIO_RAFAGA) campo.pinta(cv, Tiempos.juicioOscuro(ms), Tiempos.juicioBase(ms)) { k -> Tiempos.juicio(ms, k) }
                cv.restore()
            }
            else -> {
                // Apagar. 0–150: aparece el campo entero (sobre la pantalla de Ammit no se nota: es su fondo). Debajo se abre tu inicio de
                // siempre, que hace su propia animación: se le deja terminar. 520–720: el tirón; desde 720, las cadenas se van.
                val a = Tiempos.clamp(ms / 150)
                val tiron = if (ms in 520f..720f) 14f * sin((ms - 520) / 200 * Math.PI.toFloat()) else 0f
                val r = ms - 720
                val (dx, dy) = if (r > 0) Tiempos.temblor(r, Tiempos.golpesSalir(n)) else (tiron * 0.5f * sin(ms * 0.9f)) to (tiron * 0.5f * sin(ms * 1.13f))
                if (a < 1f) { capa.alpha = (255 * a).toInt(); cv.saveLayer(null, capa) } else cv.save()
                cv.translate(dx * u, dy * u)
                campo.pinta(cv, Campo.OSCURO, Tiempos.salirBase(r)) { k -> Tiempos.salir(r, k, n) }
                cv.restore()
                if (r >= Tiempos.salidaFin(n)) { fin(); return false }
            }
        }
        return true
    }

    // ------------------------------------------------------------------ el rótulo del juicio

    private val dp = c.resources.displayMetrics.density
    private val sp = c.resources.displayMetrics.scaledDensity
    private fun fuente(id: Int, defecto: Typeface) = runCatching { ResourcesCompat.getFont(c, id) }.getOrNull() ?: defecto
    private val pTitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fuente(R.font.body_bold, Typeface.DEFAULT_BOLD); textSize = 15 * sp; letterSpacing = 0.2f; color = Ammit.ACENTO_NOCHE.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val pGrande = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fuente(R.font.bricolage, Typeface.DEFAULT_BOLD)
        runCatching { fontVariationSettings = "'wght' 800, 'wdth' 92" }
        val anchoDp = c.resources.configuration.screenWidthDp
        textSize = (anchoDp * 0.13f).coerceIn(64f, 150f) * sp; letterSpacing = -0.03f; color = 0xFFF8F0D4.toInt()
    }
    private val pPunto = Paint(pGrande).apply { color = Ammit.ACENTO_NOCHE.toInt() }
    private val pFrase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fuente(R.font.display_italic, Typeface.create(Typeface.SERIF, Typeface.ITALIC)); textSize = 22 * sp; color = 0xFFF8F0D4.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val pPlaca = Paint().apply { color = 0xE6000000.toInt() }
    private val pBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFF8F0D4.toInt() }
    private val titulo by lazy { "${app.uppercase()} NO PASA EL JUICIO" }
    private val frase = "Su corazón pesa más que la pluma. Ammit se lo come."

    /** «X NO PASA EL JUICIO», «AMMIT.» y la frase, sobre su placa negra con borde, en el centro; [t]: 0 sin ver, 1 puesto. */
    private fun rotulo(cv: Canvas, campo: Campo, t: Float) {
        if (t <= 0f) return
        val pad = 32 * dp; val padV = 24 * dp
        val wT = pTitulo.measureText(titulo); val wA = pGrande.measureText("AMMIT"); val wP = pPunto.measureText(".")
        val wF = pFrase.measureText(frase)
        val anchoMax = campo.ancho - 48 * dp
        // (si la frase o el título no caben, se encogen)
        val escala = minOf(1f, (anchoMax - 2 * pad) / maxOf(wT, wF, wA + wP))
        val fmG = pGrande.fontMetrics
        val altoG = (fmG.descent - fmG.ascent) * 0.92f
        val altoT = pTitulo.textSize * 1.3f; val altoF = pFrase.textSize * 1.4f
        val anchoCaja = (maxOf(wT, wF, wA + wP) * escala + 2 * pad).coerceAtMost(anchoMax)
        val altoCaja = (altoT + 6 * dp + altoG + 10 * dp + altoF) * escala + 2 * padV
        val cx = campo.ancho / 2f; val cy = campo.alto / 2f
        val e = 1.35f - 0.35f * Tiempos.ease(t)
        val alfa = (255 * t).toInt()
        cv.save()
        cv.translate(cx, cy); cv.scale(e, e)
        val caja = RectF(-anchoCaja / 2, -altoCaja / 2, anchoCaja / 2, altoCaja / 2)
        pPlaca.alpha = (0xE6 * t).toInt(); cv.drawRect(caja, pPlaca)
        pBorde.strokeWidth = 2 * dp; pBorde.alpha = alfa; cv.drawRect(caja, pBorde)
        cv.scale(escala, escala)
        var y = -altoCaja / 2 / escala + padV / escala
        pTitulo.alpha = alfa; cv.drawText(titulo, 0f, y + pTitulo.textSize, pTitulo)
        y += altoT + 6 * dp
        pGrande.alpha = alfa; pPunto.alpha = alfa
        val x0 = -(wA + wP) / 2
        cv.drawText("AMMIT", x0, y - fmG.ascent * 0.92f, pGrande)
        cv.drawText(".", x0 + wA, y - fmG.ascent * 0.92f, pPunto)
        y += altoG + 10 * dp
        pFrase.alpha = alfa; cv.drawText(frase, 0f, y + pFrase.textSize, pFrase)
        cv.restore()
    }

    companion object {
        /** Las cuatro cadenas del principio del juicio, de lado a lado (en fracciones de la pantalla). */
        val CUATRO = listOf(floatArrayOf(0f, 0.18f, 1f, 0.62f), floatArrayOf(1f, 0.12f, 0f, 0.78f), floatArrayOf(0.15f, -0.04f, 0.62f, 1.04f), floatArrayOf(0.9f, -0.04f, 0.35f, 1.04f))
    }
}
