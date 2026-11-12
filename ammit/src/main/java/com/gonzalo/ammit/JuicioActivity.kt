package com.gonzalo.ammit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * «X no pasa el juicio»: cuatro cadenas, una tras otra; «AMMIT.» con la frase, un buen rato; y la ráfaga, que son las mismas cadenas del
 * fondo de Ammit: cuando tapan la pantalla entera, ya es la pantalla de inicio de Ammit, y se pasa a ella (las apps aparecen encima).
 */
class JuicioActivity : ComponentActivity() {
    companion object {
        const val EXTRA_APP = "app"
        /** Lo que dura el juicio, hasta que las cadenas son ya el fondo de la pantalla de inicio. */
        const val ESPERA_MS = Tiempos.JUICIO_MS.toLong()

        fun abrir(c: Context, nombreApp: String) {
            c.startActivity(Intent(c, JuicioActivity::class.java).putExtra(EXTRA_APP, nombreApp)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
    }

    private var ido = false

    /**
     * A la pantalla de inicio de Ammit. Con el vigilante: primero la cortina de cadenas por encima de todo (igual que este último
     * fotograma), y por debajo se cambia de pantalla sin que se vea; la cortina se funde cuando la pantalla de inicio ya está pintada.
     */
    private fun alInicio() {
        if (ido) return
        ido = true
        val casa = Ammit.inicioNormal(this)?.let { Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(it) }
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        runCatching { startActivity(casa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = intent.getStringExtra(EXTRA_APP).orEmpty().ifBlank { "Esa app" }
        setContent {
            BackHandler { alInicio() }
            PantJuicio(app, alAcabar = { alInicio() })
        }
    }
}

/** Las cuatro cadenas del principio, de lado a lado (en fracciones de la pantalla). */
private val CUATRO = listOf(floatArrayOf(0f, 0.18f, 1f, 0.62f), floatArrayOf(1f, 0.12f, 0f, 0.78f), floatArrayOf(0.15f, -0.04f, 0.62f, 1.04f), floatArrayOf(0.9f, -0.04f, 0.35f, 1.04f))

/** El juicio. [tFijo]: un momento fijo (ms), para las capturas; si no, corre solo y al final [alAcabar]. */
@Composable
fun PantJuicio(app: String, tFijo: Float? = null, alAcabar: () -> Unit = {}) {
    val ancho = LocalConfiguration.current.screenWidthDp
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val vista = androidx.compose.ui.platform.LocalView.current
    val reloj = remember { Animatable(tFijo ?: 0f) }
    LaunchedEffect(tFijo) {
        if (tFijo != null) { reloj.snapTo(tFijo); return@LaunchedEffect }
        reloj.animateTo(Tiempos.JUICIO_MS.toFloat(), tween(Tiempos.JUICIO_MS, easing = LinearEasing))
        alAcabar()
    }
    val papel = Diseno.PAPEL; val acento = Diseno.ACENTO_NOCHE
    Box(Modifier.fillMaxSize().background(Color.Black).graphicsLayer {
        val ms = reloj.value
        val c = Campo.paraTapar(ctx, size.width, size.height)
        val (dx, dy) = Tiempos.temblor(ms, Tiempos.golpesJuicio(c.n))
        translationX = dx * c.u; translationY = dy * c.u
    }) {
        // 1) las cuatro: lo único que queda por debajo del rótulo
        Canvas(Modifier.fillMaxSize()) {
            val ms = reloj.value
            val c = Campo.paraTapar(ctx, size.width, size.height)
            drawIntoCanvas { cv ->
                CUATRO.forEachIndexed { k, l ->
                    c.cadena(cv.nativeCanvas, l[0] * size.width, l[1] * size.height, l[2] * size.width, l[3] * size.height, Tiempos.juicioCuatro(ms, k), 3.25f)
                }
            }
        }
        // 2) «AMMIT.», sobre su placa
        val grande = (ancho * 0.13f).coerceIn(64f, 150f)
        Box(Modifier.fillMaxSize().graphicsLayer {
            val tx = Tiempos.juicioRotulo(reloj.value)
            alpha = tx; val e = 1.35f - 0.35f * Tiempos.ease(tx); scaleX = e; scaleY = e
        }, contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 760.dp).padding(horizontal = 24.dp).background(Color.Black.copy(alpha = 0.9f)).border(2.dp, papel)
                .padding(horizontal = 32.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText("${app.uppercase()} NO PASA EL JUICIO",
                    style = TextStyle(color = acento, fontFamily = Diseno.Texto, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 3.sp, textAlign = TextAlign.Center))
                BasicText(buildAnnotatedString { append("AMMIT"); withStyle(SpanStyle(color = acento)) { append(".") } },
                    style = TextStyle(color = papel, fontFamily = Diseno.Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = grande.sp,
                        lineHeight = (grande * 1.0f).sp, letterSpacing = (-grande * 0.03f).sp),
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                BasicText(AnnotatedString("Su corazón pesa más que la pluma. Ammit se lo come."),
                    style = TextStyle(color = papel, fontFamily = Diseno.Cursiva, fontSize = 22.sp, lineHeight = 30.sp, textAlign = TextAlign.Center))
            }
        }
        // 3) la ráfaga, por encima de todo: el campo del fondo de Ammit, una cadena cada 12 ms; tapado, ya es el fondo
        Canvas(Modifier.fillMaxSize()) {
            val ms = reloj.value
            if (ms < Tiempos.JUICIO_RAFAGA) return@Canvas
            val c = Campo.paraTapar(ctx, size.width, size.height)
            // (anclado a la pantalla, como la cortina que lo releva al final)
            val (ox, oy) = enPantalla(vista)
            drawIntoCanvas { cv ->
                cv.nativeCanvas.save(); cv.nativeCanvas.translate(-ox, -oy)
                c.pinta(cv.nativeCanvas, Tiempos.juicioOscuro(ms), Tiempos.juicioBase(ms)) { k -> Tiempos.juicio(ms, k) }
                cv.nativeCanvas.restore()
            }
        }
    }
}
