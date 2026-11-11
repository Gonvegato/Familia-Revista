package com.gonzalo.ammit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** «X no pasa el juicio»: las cadenas un segundo y medio y, después, a la pantalla de inicio (Ammit). */
class JuicioActivity : ComponentActivity() {
    companion object {
        const val EXTRA_APP = "app"
        /** Lo que dura el juicio: cuatro cadenas, Ammit, el montón de cadenas, y a la pantalla de inicio. */
        const val ESPERA_MS = 3600L

        fun abrir(c: Context, nombreApp: String) {
            c.startActivity(Intent(c, JuicioActivity::class.java).putExtra(EXTRA_APP, nombreApp)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }

        fun alInicio(c: Context) {
            c.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = intent.getStringExtra(EXTRA_APP).orEmpty().ifBlank { "Esa app" }
        setContent {
            BackHandler { alInicio(this); finish() }
            LaunchedEffect(Unit) { delay(ESPERA_MS); alInicio(this@JuicioActivity); finish() }
            PantJuicio(app)
        }
    }
}

/** Las cuatro cadenas del principio: (x0 relativo, y0 relativo, ángulo). */
private val CUATRO = listOf(Triple(0.5f, 0.12f, 0.36f), Triple(0.5f, 0.88f, -0.32f), Triple(0.22f, 0.5f, 1.25f), Triple(0.8f, 0.5f, -1.2f))

/** El montón de cadenas del final: posiciones y ángulos fijos (pseudoaleatorios), para que siempre sea igual. */
private val MONTON = List(16) { i ->
    val r = java.util.Random(7_301L + i * 97L)
    Triple(r.nextFloat(), r.nextFloat(), (r.nextFloat() - 0.5f) * 3.1f)
}

/**
 * El juicio, en tres tiempos: cuatro cadenas, una tras otra (cada una con su golpe); luego «AMMIT.» con la frase; y al final un montón de
 * cadenas que tapan la pantalla. Después, a la pantalla de inicio.
 */
@Composable
fun PantJuicio(app: String, tFijo: Float? = null) {
    val t = tonos()
    val ancho = LocalConfiguration.current.screenWidthDp
    val grande = (ancho * 0.16f).coerceIn(72f, 170f)
    val reloj = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(tFijo) {
        if (tFijo != null) { reloj.snapTo(tFijo); return@LaunchedEffect }
        reloj.animateTo(1f, androidx.compose.animation.core.tween(JuicioActivity.ESPERA_MS.toInt() - 200, easing = androidx.compose.animation.core.LinearEasing))
    }
    val v = reloj.value
    // 0–0,36: las cuatro cadenas (0,09 cada una); 0,36–0,52: Ammit; 0,52–0,95: el montón
    val golpe = (0 until 4).sumOf { k ->
        val d = v - (0.06f + k * 0.09f)
        if (d in 0f..0.05f) (kotlin.math.sin(d * 320f) * 9f * (1f - d / 0.05f)).toDouble() else 0.0
    }.toFloat()
    val texto = ((v - 0.36f) / 0.1f).coerceIn(0f, 1f)
    val montonP = ((v - 0.52f) / 0.4f).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize().background(t.fondo).graphicsLayer { translationX = golpe * density }) {
        Cadenas(t.tinta, alfa = 0.06f)
        Canvas(Modifier.fillMaxSize()) {
            CUATRO.forEachIndexed { k, (x, y, a) ->
                val p = ((v - k * 0.09f) / 0.08f).coerceIn(0f, 1f)
                cadenaQueEntra(size.width * x, size.height * y, a, p, t.tinta.copy(alpha = 0.55f), 1.8f)
            }
            MONTON.forEachIndexed { k, (x, y, a) ->
                val p = ((montonP - k * 0.04f) / 0.25f).coerceIn(0f, 1f)
                cadenaQueEntra(size.width * x, size.height * y, a, p, t.tinta.copy(alpha = 0.32f + 0.3f * ((k % 3) / 2f)), 1.2f + (k % 4) * 0.25f)
            }
        }
        // (el texto, sobre un recuadro del fondo: el montón de cadenas pasa por detrás y no lo tapa)
        Box(Modifier.fillMaxSize().padding(horizontal = (ancho * 0.08f).dp).graphicsLayer {
            alpha = texto; val e = 1.25f - 0.25f * texto; scaleX = e; scaleY = e
        }, contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
        Column(Modifier.background(t.fondo.copy(alpha = 0.88f)).padding(horizontal = 22.dp, vertical = 18.dp)) {
            BasicText("${app.uppercase()} NO PASA EL JUICIO",
                style = TextStyle(color = t.acento, fontFamily = Diseno.Texto, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 3.sp))
            BasicText(buildAnnotatedString { append("AMMIT"); withStyle(SpanStyle(color = t.acento)) { append(".") } },
                style = TextStyle(color = t.tinta, fontFamily = Diseno.Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = grande.sp,
                    lineHeight = (grande * 0.95f).sp, letterSpacing = (-grande * 0.03f).sp),
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            BasicText(AnnotatedString("Su corazón pesa más que la pluma. Ammit se lo come."),
                style = TextStyle(color = t.tinta, fontFamily = Diseno.Cursiva, fontSize = 22.sp, lineHeight = 30.sp), modifier = Modifier.widthIn(max = 520.dp))
            BasicText("VUELVES AL INICIO…",
                style = TextStyle(color = t.gris, fontFamily = Diseno.Texto, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.5.sp),
                modifier = Modifier.padding(top = 28.dp))
        }
        }
    }
}
