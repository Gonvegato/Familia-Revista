package com.gonzalo.ammit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.LaFamilia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * «Ammit no te suelta»: al intentar apagarla sin haber escrito 300 palabras ni leído un capítulo desde que la encendiste. Mira la cuenta
 * (lo último), te dice cuánto llevas y te deja ir a Thot o a Seshat. Si ya lo has hecho, la apaga sola.
 */
class CandadoActivity : ComponentActivity() {
    companion object {
        fun abrir(c: Context) {
            c.startActivity(Intent(c, CandadoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    private var apagada = false

    /** Ya se puede: se apaga (con la cortina de cadenas, si está el vigilante) y esta pantalla se va. */
    fun apagar() {
        if (apagada) return
        apagada = true
        if (!Cortina.cambiar(this, false) { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
            Ammit.encender(this, false); BotonAmmit.avisar(this)
            runCatching { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { PantCandado(this) }
    }
}

private val PAPEL = Diseno.PAPEL
private val ACENTO = Diseno.ACENTO_NOCHE

@Composable
fun PantCandado(a: CandadoActivity?, fijo: Candado.Progreso? = null) {
    val ctx = LocalContext.current
    var comprobando by remember { mutableStateOf(fijo == null) }
    // (se vuelve a calcular cada vez que cambia algo en la cuenta)
    val cambios = runCatching { LaFamilia.cambios }.getOrDefault(0)
    val p = fijo ?: remember(cambios, comprobando) { Candado.progreso(ctx) }
    LaunchedEffect(Unit) {
        if (fijo != null) return@LaunchedEffect
        withContext(Dispatchers.IO) { Candado.sincronizar(); Candado.tomarBase(ctx) }
        comprobando = false
    }
    // Hecho: se suelta sola
    LaunchedEffect(p.puede, comprobando) { if (!comprobando && p.puede) a?.apagar() }

    Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 640.dp).border(2.dp, PAPEL).padding(horizontal = 32.dp, vertical = 28.dp)) {
            BasicText("AMMIT NO TE SUELTA", style = TextStyle(ACENTO, 15.sp, FontWeight.Bold, fontFamily = Diseno.Texto, letterSpacing = 3.sp))
            BasicText(buildAnnotatedString { append("Todavía no"); withStyle(SpanStyle(color = ACENTO)) { append(".") } },
                style = TextStyle(PAPEL, 64.sp, FontWeight.ExtraBold, fontFamily = Diseno.Grotesca, lineHeight = 66.sp),
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
            BasicText("Para soltarla, desde que la encendiste: escribe ${Candado.PALABRAS} palabras en Thot o lee un capítulo en Seshat, en cualquier dispositivo.",
                style = TextStyle(PAPEL, 21.sp, fontFamily = Diseno.Cursiva, lineHeight = 29.sp))
            androidx.compose.foundation.layout.Spacer(Modifier.height(22.dp))
            when {
                !p.conCuenta -> Linea("Ammit no ve la cuenta de la familia: entra en los ajustes de Ammit (o en una hermana) para que pueda comprobarlo.")
                comprobando || !p.listo -> Linea("Mirando la cuenta…")
                else -> {
                    Barra("PALABRAS EN THOT", "${minOf(p.palabras, Candado.PALABRAS)} / ${Candado.PALABRAS}", p.palabras / Candado.PALABRAS.toFloat())
                    androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
                    val lect = when {
                        p.capitulo -> "Capítulo leído."
                        p.minutos > 0 && p.libro.isNotBlank() -> "Llevas ${p.minutos} min con «${p.libro}»: termina el capítulo (y al menos ${Candado.MINUTOS} min leyendo)."
                        else -> "Aún ninguno (cuenta pasar a otro capítulo habiendo leído al menos ${Candado.MINUTOS} min)."
                    }
                    Barra("UN CAPÍTULO EN SESHAT", lect, if (p.capitulo) 1f else (p.minutos / Candado.MINUTOS.toFloat()).coerceAtMost(0.95f))
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Boton("ESCRIBIR EN THOT", acento = true) { abrirApp(ctx, "com.gonzalo.cobalto"); a?.finish() }
                Boton("LEER EN SESHAT", acento = true) { abrirApp(ctx, "com.gonzalo.marfil"); a?.finish() }
                Boton("AHORA NO") { a?.finish() }
            }
        }
    }
}

private fun abrirApp(c: Context, paquete: String) {
    c.packageManager.getLaunchIntentForPackage(paquete)?.let { runCatching { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

@Composable
private fun Linea(t: String) = BasicText(t, style = TextStyle(PAPEL.copy(alpha = 0.8f), 16.sp, fontFamily = Diseno.Texto, lineHeight = 23.sp))

@Composable
private fun Barra(titulo: String, valor: String, f: Float) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(titulo, style = TextStyle(PAPEL.copy(alpha = 0.7f), 12.sp, FontWeight.Bold, fontFamily = Diseno.Texto, letterSpacing = 2.sp))
        }
        BasicText(valor, style = TextStyle(PAPEL, 17.sp, FontWeight.Medium, fontFamily = Diseno.Texto, lineHeight = 23.sp), modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).background(PAPEL.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).height(6.dp).background(ACENTO))
        }
    }
}

@Composable
private fun Boton(t: String, acento: Boolean = false, al: () -> Unit) {
    BasicText(t, style = TextStyle(if (acento) Color.Black else PAPEL, 12.sp, FontWeight.Bold, fontFamily = Diseno.Texto, letterSpacing = 1.4.sp),
        modifier = Modifier.background(if (acento) ACENTO else Color.Transparent).border(1.5.dp, if (acento) ACENTO else PAPEL)
            .clickable(onClick = al).padding(horizontal = 16.dp, vertical = 12.dp))
}
