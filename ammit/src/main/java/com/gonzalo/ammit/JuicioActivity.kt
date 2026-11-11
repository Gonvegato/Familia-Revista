package com.gonzalo.ammit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        const val ESPERA_MS = 1500L

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

@Composable
fun PantJuicio(app: String) {
    val t = tonos()
    val ancho = LocalConfiguration.current.screenWidthDp
    val grande = (ancho * 0.16f).coerceIn(72f, 170f)
    Box(Modifier.fillMaxSize().background(t.fondo)) {
        Cadenas(t.tinta)
        Column(Modifier.fillMaxSize().padding(horizontal = (ancho * 0.08f).dp), verticalArrangement = Arrangement.Center) {
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
