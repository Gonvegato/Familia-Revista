package com.gonzalo.ammit

import android.app.StatusBarManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lo que se abre desde el icono de Ammit (y manteniendo pulsado su botón de la barra de arriba): los pasos para dejarla lista, cada uno
 * con su estado, el interruptor y la pantalla de inicio de siempre (la que se usa con Ammit apagada).
 */
class AjustesActivity : ComponentActivity() {
    companion object { const val EXTRA_FALTA = "falta" }

    /** Para repintar al volver de Ajustes de Android (los estados cambian fuera de la app). */
    private var vuelta by mutableIntStateOf(0)

    private val pedirInicio = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { vuelta++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Ammit.cargar(this)
        val falta = intent.getBooleanExtra(EXTRA_FALTA, false)
        setContent { PantAjustes(this, vuelta, falta) }
    }

    override fun onResume() { super.onResume(); refrescar() }

    fun refrescar() { Ammit.cargar(this); vuelta++ }

    fun ponerDeInicio() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME))
            runCatching { pedirInicio.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)) }.onFailure { abrirAjustesInicio() }
        else abrirAjustesInicio()
    }

    fun abrirAjustesInicio() = runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
    fun abrirInfoApp() = runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    fun abrirAccesibilidad() = runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

    fun anadirBoton() {
        if (Build.VERSION.SDK_INT < 33) return
        val sb = getSystemService(StatusBarManager::class.java) ?: return
        sb.requestAddTileService(ComponentName(this, BotonAmmit::class.java), "Ammit", Icon.createWithResource(this, R.drawable.ic_balanza), mainExecutor) { vuelta++ }
    }

    fun encender(on: Boolean) {
        Ammit.encender(this, on); BotonAmmit.avisar(this); vuelta++
        if (on) startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun PantAjustes(a: AjustesActivity, vuelta: Int, falta: Boolean) {
    val t = tonos()
    @Suppress("UNUSED_VARIABLE") val v = vuelta   // (leerlo hace que se repinte al volver)
    val esInicio = Ammit.esInicio(a)
    val vigia = Ammit.vigiaActivo(a)
    val listo = esInicio && vigia
    val otros = Ammit.otrosInicios(a)
    val normal = Ammit.inicioNormal(a)
    Column(Modifier.fillMaxSize().background(t.fondo).verticalScroll(rememberScrollState())) {
        // La cabecera de la familia: un bloque de tinta con el nombre
        Column(Modifier.fillMaxWidth().background(t.tinta).statusBarsPadding().padding(horizontal = 24.dp, vertical = 18.dp)) {
            BasicText("EL JUICIO DE OSIRIS", style = TextStyle(t.fondo.copy(alpha = 0.7f), 11.sp, FontWeight.Bold, fontFamily = Diseno.Texto, letterSpacing = 2.sp))
            BasicText(buildAnnotatedString { append("AMMIT"); withStyle(SpanStyle(color = if (t.fondo == Diseno.NEGRO) Diseno.ACENTO else Diseno.ACENTO_NOCHE)) { append(".") } },
                style = TextStyle(t.fondo, 58.sp, FontWeight.ExtraBold, fontFamily = Diseno.Grotesca, letterSpacing = (-1.5).sp))
        }
        Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 24.dp, vertical = 18.dp).navigationBarsPadding()) {
            BasicText("Encendida, en la tablet solo pasan Jonsu, Bastet, Seshat, Thot, Hathor y Nut: el resto de apps salen encadenadas en la pantalla de inicio y, si se abren por otro lado, Ammit se las come. Se enciende y se apaga con su botón de la barra de arriba.",
                style = TextStyle(t.gris, 15.sp, fontFamily = Diseno.Texto, lineHeight = 22.sp))
            if (falta && !listo) BasicText("Para encenderla falta algún paso de abajo.", style = TextStyle(t.acento, 15.sp, FontWeight.Bold, fontFamily = Diseno.Texto), modifier = Modifier.padding(top = 10.dp))

            Titulo("Encendida", t)
            Row(Modifier.fillMaxWidth().clickable(enabled = listo || Ammit.encendida) { a.encender(!Ammit.encendida) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(if (Ammit.encendida) "Solo la familia" else "Apagada", style = TextStyle(t.tinta, 24.sp, FontWeight.ExtraBold, fontFamily = Diseno.Grotesca))
                    BasicText(if (listo) "Toca para ${if (Ammit.encendida) "apagarla" else "encenderla"} (o usa el botón de la barra de arriba)" else "Primero, los pasos de abajo",
                        style = TextStyle(t.gris, 13.sp, fontFamily = Diseno.Texto))
                }
                Interruptor(Ammit.encendida, t)
            }

            Titulo("Para dejarla lista", t)
            Paso(1, "Ammit, tu pantalla de inicio", "Con Ammit apagada, el botón de casa te lleva a la tuya de siempre: no cambia nada.", esInicio, t, "ELEGIR") { a.ponerDeInicio() }
            Paso(2, "Permitir los ajustes restringidos", "Android no deja activar el vigilante de una app que no viene de la Play Store hasta que lo permites: en la pantalla que se abre, menú ⋮ (arriba a la derecha) → «Permitir ajustes restringidos». Si no sale el menú, haz antes el paso 3 una vez: Android lo enseña después de avisarte.", vigia, t, "ABRIR") { a.abrirInfoApp() }
            Paso(3, "Activar el vigilante", "Accesibilidad → Apps instaladas (o Servicios) → Ammit → activar. Solo mira qué app se abre, nunca lo que hay dentro.", vigia, t, "ABRIR") { a.abrirAccesibilidad() }
            if (Build.VERSION.SDK_INT >= 33) Paso(4, "El botón en la barra de arriba", "Lo pone con un toque; también se puede poner a mano desde el lápiz de la barra.", null, t, "PONER") { a.anadirBoton() }

            Titulo("Tu pantalla de inicio de siempre", t)
            BasicText("La que se abre con Ammit apagada.", style = TextStyle(t.gris, 13.sp, fontFamily = Diseno.Texto), modifier = Modifier.padding(bottom = 6.dp))
            if (otros.isEmpty()) BasicText("No hay otra en la tablet.", style = TextStyle(t.gris, 15.sp, fontFamily = Diseno.Texto))
            otros.forEach { (cn, nombre) ->
                Row(Modifier.fillMaxWidth().clickable { Ammit.ponInicioNormal(a, cn); a.refrescar() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(nombre, style = TextStyle(t.tinta, 20.sp, FontWeight.SemiBold, fontFamily = Diseno.Grotesca), modifier = Modifier.weight(1f))
                    if (cn == normal) BasicText("✓", style = TextStyle(t.acento, 20.sp, FontWeight.Bold))
                }
            }
            Titulo("Qué pasa con Ammit encendida", t)
            listOf(
                "Pasan las seis apps de la familia, la barra de arriba (para poder apagarla), el teclado, y las pantallas de Android que las apps de la familia necesitan (los permisos y la instalación de sus versiones nuevas).",
                "Todo lo demás, no: tampoco Ajustes, la cámara o el teléfono. Si algo se cuela, reiniciar la tablet no la apaga, pero el botón de la barra siempre funciona.",
                "En la pantalla de inicio, mantener pulsado el fondo o un icono deja colocar las apps y poner widgets de la familia.",
            ).forEach { BasicText("·  $it", style = TextStyle(t.tinta, 15.sp, fontFamily = Diseno.Texto, lineHeight = 22.sp), modifier = Modifier.padding(vertical = 4.dp)) }
            val ver = runCatching { a.packageManager.getPackageInfo(a.packageName, 0).versionName }.getOrNull().orEmpty()
            BasicText("AMMIT $ver", style = TextStyle(t.gris, 10.sp, FontWeight.Bold, fontFamily = Diseno.Texto, letterSpacing = 1.4.sp), modifier = Modifier.padding(top = 24.dp))
            BasicText("Hermana de Jonsu, Bastet, Seshat, Thot, Hathor y Nut: la que guarda la puerta.", style = TextStyle(t.gris, 14.sp, fontFamily = Diseno.Cursiva), modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
        }
    }
}

@Composable
private fun Titulo(s: String, t: Tonos) {
    Spacer(Modifier.height(22.dp))
    Box(Modifier.fillMaxWidth().height(2.dp).background(t.tinta))
    BasicText(s.uppercase(), style = TextStyle(t.tinta, 17.sp, FontWeight.ExtraBold, fontFamily = Diseno.Grotesca), modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

/** Un paso: su número (o ✓ si ya está), qué es, cómo, y su botón. [hecho] null: no se puede saber. */
@Composable
private fun Paso(n: Int, titulo: String, como: String, hecho: Boolean?, t: Tonos, boton: String, al: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(if (hecho == true) t.acento else Color.Transparent).border(2.dp, if (hecho == true) t.acento else t.tinta, CircleShape), contentAlignment = Alignment.Center) {
            BasicText(if (hecho == true) "✓" else "$n", style = TextStyle(if (hecho == true) t.fondo else t.tinta, 16.sp, FontWeight.Bold, fontFamily = Diseno.Texto))
        }
        Column(Modifier.weight(1f)) {
            BasicText(titulo, style = TextStyle(t.tinta, 20.sp, FontWeight.ExtraBold, fontFamily = Diseno.Grotesca))
            BasicText(como, style = TextStyle(t.gris, 14.sp, fontFamily = Diseno.Texto, lineHeight = 20.sp), modifier = Modifier.padding(top = 2.dp))
            if (hecho != true) BasicText("$boton  →", style = TextStyle(t.fondo, 12.sp, FontWeight.Bold, fontFamily = Diseno.Texto, letterSpacing = 1.4.sp),
                modifier = Modifier.padding(top = 8.dp).background(t.acento).clickable(onClick = al).padding(horizontal = 14.dp, vertical = 9.dp))
        }
    }
}

@Composable
private fun Interruptor(on: Boolean, t: Tonos) {
    Box(Modifier.size(width = 56.dp, height = 30.dp).background(if (on) t.acento else t.gris.copy(alpha = 0.3f)).padding(4.dp), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(22.dp).background(if (on) t.fondo else t.tinta))
    }
}
