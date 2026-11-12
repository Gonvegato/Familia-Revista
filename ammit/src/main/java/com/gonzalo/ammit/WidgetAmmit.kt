package com.gonzalo.ammit

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import kotlin.math.roundToInt

/**
 * El widget de Ammit (para tu pantalla de inicio de siempre): un toque la enciende, con sus cadenas. Encendida enseña el sinfín de cadenas
 * y cuánto falta para poder soltarla; tocarlo entonces la apaga si ya se puede (si no, «Ammit no te suelta»), igual que el botón de la
 * barra de arriba.
 */
class WidgetAmmit : AppWidgetProvider() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != TOCAR) { super.onReceive(c, i); return }
        // El toque: sin pantallas; si hay que mirar la cuenta, unos segundos por detrás
        val p = goAsync()
        Interruptor.toca(c.applicationContext) { runCatching { p.finish() } }
    }

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { ids.forEach { pinta(c, m, it) } }
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: Bundle?) { pinta(c, m, id) }

    companion object {
        const val TOCAR = "com.gonzalo.ammit.widget.TOCAR"

        /** Repinta todos los widgets de Ammit (al encender, al apagar, cuando cambia lo que falta). */
        fun repintar(c: Context) = runCatching {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, WidgetAmmit::class.java)).forEach { pinta(c, m, it) }
        }

        private val hilo = java.util.concurrent.Executors.newSingleThreadExecutor()
        @Volatile private var ultimo = 0L

        /** Repinta en segundo plano (dibujar las cadenas lleva un momento); [cadaMs]: no más a menudo que esto. */
        fun repintarLuego(c: Context, cadaMs: Long = 0L) {
            val ahora = android.os.SystemClock.elapsedRealtime()
            if (cadaMs > 0 && ahora - ultimo < cadaMs) return
            ultimo = ahora
            val app = c.applicationContext
            hilo.execute { repintar(app) }
        }

        private fun pinta(c: Context, m: AppWidgetManager, id: Int) = runCatching {
            val o = m.getAppWidgetOptions(id)
            val horizontal = c.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val wDp = (if (horizontal) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)).takeIf { it > 0 } ?: 300
            val hDp = (if (horizontal) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)).takeIf { it > 0 } ?: 130
            val v = RemoteViews(c.packageName, R.layout.widget_ammit)
            v.setImageViewBitmap(R.id.imagen, dibujo(c, wDp, hDp))
            v.setContentDescription(R.id.imagen, if (Ammit.encendida) "Ammit encendida. Toca para soltarla" else "Ammit apagada. Toca para encenderla")
            // (un aviso a este mismo receptor, no una pantalla: abrir una pantalla de Ammit, aunque sea invisible, enseñaba un momento la
            // pantalla negra de arranque de Android)
            v.setOnClickPendingIntent(R.id.imagen, PendingIntent.getBroadcast(c, 0, Intent(c, WidgetAmmit::class.java).setAction(TOCAR),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            m.updateAppWidget(id, v)
        }

        /** El dibujo del widget ([wDp]×[hDp] dp): negro, con cadenas, «AMMIT.» y cómo está. */
        fun dibujo(c: Context, wDp: Int, hDp: Int): Bitmap {
            Ammit.cargar(c)
            val d = c.resources.displayMetrics.density.coerceAtMost(2.5f)
            val w = (wDp * d).roundToInt().coerceIn(120, 1600); val h = (hDp * d).roundToInt().coerceIn(80, 1000)
            val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bm)
            val radio = 24 * d
            cv.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radio, radio, Path.Direction.CW) })
            cv.drawColor(0xFF0A0908.toInt())
            val on = Ammit.encendida
            // (eslabones del mismo tamaño que en la pantalla de la tablet, no encogidos al tamaño del widget)
            val campo = Campo.suelto(w, h, unidad = 0.38f * d)
            if (on) campo.pinta(cv, 0.55f, 1f) { 1f }
            else {
                // Apagada: solo unas cadenas sueltas, tenues
                val p = Paint().apply { alpha = 90 }
                cv.saveLayer(null, p)
                listOf(floatArrayOf(-0.05f, 0.85f, 1.05f, 0.15f), floatArrayOf(0.25f, -0.1f, 0.75f, 1.1f)).forEach { l ->
                    campo.cadena(cv, l[0] * w, l[1] * h, l[2] * w, l[3] * h, 1f, 2.5f)
                }
                cv.restore()
            }
            // (un velo abajo a la izquierda, para leer encima de las cadenas)
            cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
                shader = android.graphics.LinearGradient(0f, 0f, w * 0.8f, 0f, 0xCC000000.toInt(), 0x00000000, android.graphics.Shader.TileMode.CLAMP)
            })
            fun fuente(id: Int, def: Typeface) = runCatching { ResourcesCompat.getFont(c, id) }.getOrNull() ?: def
            val pad = 18 * d
            val papel = 0xFFF8F0D4.toInt(); val acento = Ammit.ACENTO_NOCHE.toInt()
            val pTit = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fuente(R.font.body_bold, Typeface.DEFAULT_BOLD); textSize = 11 * d; letterSpacing = 0.2f; color = acento }
            val tam = (h * 0.36f).coerceAtMost(w * 0.2f)
            val pGrande = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = fuente(R.font.bricolage, Typeface.DEFAULT_BOLD); runCatching { fontVariationSettings = "'wght' 800, 'wdth' 92" }
                textSize = tam; letterSpacing = -0.03f; color = papel
            }
            val pPunto = Paint(pGrande).apply { color = acento }
            val pPie = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fuente(R.font.body_bold, Typeface.DEFAULT_BOLD); textSize = 10 * d; letterSpacing = 0.14f; color = papel }
            val listo = Ammit.vigiaActivo(c)
            val arriba = when { on -> "ENCENDIDA"; listo -> "APAGADA"; else -> "SIN PREPARAR" }
            cv.drawText(arriba, pad, pad + pTit.textSize, pTit)
            val base = pad + pTit.textSize + 6 * d - pGrande.ascent() * 0.92f
            cv.drawText("AMMIT", pad, base, pGrande)
            cv.drawText(".", pad + pGrande.measureText("AMMIT"), base, pPunto)
            val pie = when {
                on -> runCatching { Candado.progreso(c) }.getOrNull()?.let { p ->
                    when { !p.conCuenta -> "TOCA PARA SOLTARLA"; !p.listo -> "SE SUELTA CON ${Candado.PALABRAS} PALABRAS O UN CAPÍTULO"
                        p.puede -> "YA PUEDES SOLTARLA · TOCA"; else -> "${p.palabras}/${Candado.PALABRAS} PALABRAS O UN CAPÍTULO PARA SOLTARLA" }
                } ?: "TOCA PARA SOLTARLA"
                listo -> "TOCA PARA ENCENDERLA"
                else -> "TOCA PARA DEJARLA LISTA"
            }
            // (si no cabe, se encoge)
            val anchoPie = w - 2 * pad
            if (pPie.measureText(pie) > anchoPie) pPie.textSize *= anchoPie / pPie.measureText(pie)
            cv.drawText(pie, pad, h - pad, pPie)
            return bm
        }
    }
}

/**
 * Lo que hace el toque del widget, sin enseñar ninguna pantalla de Ammit: encender (con la cortina de cadenas), o soltarla si ya se puede
 * (si no, «Ammit no te suelta»), o, si falta prepararla, sus ajustes.
 */
object Interruptor {
    fun toca(c: Context, alAcabar: () -> Unit) {
        Ammit.cargar(c)
        // (desde el widget ya estás en tu inicio: ni se abre otra vez, ni hay barra que plegar; solo las pantallas de Ammit, si hacen falta)
        val abrir: (Intent) -> Unit = { i ->
            val inicio = i.hasCategory(Intent.CATEGORY_HOME); val plegar = i.component?.className?.endsWith("PlegarActivity") == true
            if (!inicio && !plegar) runCatching { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        if (!Ammit.encendida) {
            if (!Ammit.vigiaActivo(c)) abrir(Intent(c, AjustesActivity::class.java).putExtra(AjustesActivity.EXTRA_FALTA, true))
            else if (!Cortina.cambiar(c, true, abrir)) { Ammit.encender(c, true); BotonAmmit.avisar(c) }
            alAcabar(); return
        }
        if (Candado.puede(c)) { soltar(c, abrir); alAcabar(); return }
        // Lo de la cuenta puede estar viejo: se mira lo último (como mucho 2,5 s) antes de decir que no
        val principal = Handler(Looper.getMainLooper())
        var hecho = false
        fun decide() {
            if (hecho) return
            hecho = true
            if (Candado.puede(c)) soltar(c, abrir) else CandadoActivity.abrir(c)
            alAcabar()
        }
        Thread { runCatching { Candado.sincronizar() }; principal.post { decide() } }.start()
        principal.postDelayed({ decide() }, 2_500)
    }

    private fun soltar(c: Context, abrir: (Intent) -> Unit) {
        if (!Cortina.cambiar(c, false, abrir)) { Ammit.encender(c, false); BotonAmmit.avisar(c) }
    }
}

/** (De versiones anteriores: lo que hacía el toque del widget. Ahora lo hace [Interruptor] sin pantalla; esta solo lo llama y se va.) */
class InterruptorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Interruptor.toca(applicationContext) {}
        finish(); @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
