package com.gonzalo.cobalto

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Las copias de seguridad del móvil (como las del ordenador y las de Atrio del PC): copias completas de todo lo que escribes, también la
 * papelera, en la memoria de la app (`files/copias`: no salen del móvil, no ocupan tus archivos y se van si desinstalas Cobalto). La
 * primera a los 20 s de abrir, luego cada 15 minutos mientras Cobalto se ve y al salir de la app, siempre que algo haya cambiado. Se
 * quedan las últimas [GUARDA], comprimidas, y nunca más de [TOPE] entre todas. En Ajustes → Avanzado → Copias se ven, se guarda una fuera
 * y de cualquiera se saca una obra como obra nueva. La lógica es la del ordenador (EscribirCopias.kt, el mismo archivo).
 */
object CopiasMovil {
    const val GUARDA = 40
    const val TOPE = 100L * 1024 * 1024
    @Volatile private var copias: CopiasEscritos? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.gonzalo.familia.Fallos.paraCorrutinas("copias"))
    private var trabajo: Job? = null

    fun carpeta(ctx: Context) = File(ctx.applicationContext.filesDir, "copias")

    fun de(ctx: Context): CopiasEscritos {
        val a = ctx.applicationContext
        return copias ?: synchronized(this) { copias ?: CopiasEscritos({ carpeta(a) }, GUARDA, comprimidas = true, tope = TOPE).also { copias = it } }
    }

    /** Al verse la app: la primera a los 20 s y luego cada 15 minutos (si algo ha cambiado). */
    @Synchronized
    fun alVerse(ctx: Context) {
        val a = ctx.applicationContext
        trabajo?.cancel()
        trabajo = scope.launch {
            delay(20_000)
            while (isActive) { runCatching { de(a).haz(Escritos.get(a)) }; delay(15 * 60_000L) }
        }
    }

    /** Al dejar de verse (Android puede cerrarla en cualquier momento a partir de aquí): una más, si algo ha cambiado. */
    @Synchronized
    fun alOcultarse(ctx: Context) {
        val a = ctx.applicationContext
        trabajo?.cancel(); trabajo = null
        scope.launch { runCatching { val e = Escritos.get(a); e.vacia(); de(a).haz(e) } }
    }

    /** Solo para las pruebas. */
    fun reiniciaParaPruebas() { synchronized(this) { trabajo?.cancel(); trabajo = null; copias = null } }
}

private fun fechaCopia(ms: Long): String = java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale("es")).format(java.util.Date(ms))

/** «Copias» de Ajustes: las copias de este móvil, hacer una ahora y, de cualquiera, sacar una obra como obra nueva o guardarla fuera. */
@Composable
fun SeccionCopias(e: Escritos, aviso: (String) -> Unit) {
    val ctx = LocalContext.current
    val copias = remember { CopiasMovil.de(ctx) }
    var ver by remember { mutableIntStateOf(0) }
    var lista by remember { mutableStateOf<List<CopiasEscritos.Copia>>(emptyList()) }
    LaunchedEffect(ver) { lista = withContext(Dispatchers.IO) { copias.lista() } }
    var abierta by remember { mutableStateOf<CopiasEscritos.Copia?>(null) }
    val scope = rememberCoroutineScope()
    Text("Además de la cuenta, este móvil guarda copias completas de todo lo que escribes, también la papelera: cada 15 minutos mientras Thot está abierto y al salir, si algo ha cambiado. Se quedan las últimas ${CopiasMovil.GUARDA}, dentro de la app (no salen del móvil). De cualquiera se saca una obra como obra nueva.",
        color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 4.dp))
    MagLink("Hacer una copia ahora", "Aunque no haya cambiado nada") {
        scope.launch {
            val f = withContext(Dispatchers.IO) { runCatching { e.vacia(); copias.haz(e, forzar = true) }.getOrNull() }
            aviso(if (f != null) "Copia hecha" else "No había nada que copiar"); ver++
        }
    }
    if (lista.isEmpty()) Text("Aún no hay ninguna.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp))
    lista.take(12).forEach { c ->
        MagLink(fechaCopia(c.hecha), "${c.obras} ${if (c.obras == 1) "obra" else "obras"} · ${cifraE(c.palabras)} palabras · ${maxOf(1L, c.tam / 1024)} KB") { abierta = c }
    }
    if (lista.size > 12) Text("Y ${lista.size - 12} más antiguas.", color = Muted, fontSize = 12.sp)
    abierta?.let { c -> HojaCopia(e, copias, c, onCerrar = { abierta = null }, aviso = aviso) }
}

/** Una copia: sus obras, para sacar una como obra nueva, y guardarla fuera del móvil (sin comprimir: como las del ordenador). */
@Composable
private fun HojaCopia(e: Escritos, copias: CopiasEscritos, c: CopiasEscritos.Copia, onCerrar: () -> Unit, aviso: (String) -> Unit) {
    val ctx = LocalContext.current
    var docs by remember(c) { mutableStateOf<List<JSONObject>?>(null) }
    LaunchedEffect(c) { docs = withContext(Dispatchers.IO) { copias.lee(c.archivo) } }
    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        if (u != null) aviso(runCatching { ctx.contentResolver.openOutputStream(u)!!.use { it.write(CopiasEscritos.json(c.archivo)) }; "Copia guardada" }.getOrElse { "No se pudo guardar la copia" })
    }
    HojaEscribir("Copia del " + fechaCopia(c.hecha), onCerrar) {
        Text("Elige una obra para sacarla como obra nueva, «… (copia)», con todo lo suyo: capítulos, escenas, fichas, notas, el árbol, la línea y los borradores. No se toca nada de lo que tienes.",
            color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        val d = docs
        val obras = remember(d) { d?.let { CopiasEscritos.obras(it) }.orEmpty() }
        when {
            d == null -> Text("Leyendo la copia…", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
            obras.isEmpty() -> Text("Esta copia no tiene obras (o no se puede leer).", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
        }
        obras.forEach { o ->
            val (palabras, escenas) = remember(d, o.id) { CopiasEscritos.resumen(d!!, o.id) }
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Text(o.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 24.sp, color = Charcoal, modifier = Modifier.padding(top = 8.dp))
                Text("${cifraE(palabras)} palabras · $escenas ${if (escenas == 1) "escena" else "escenas"}" + if (o.borrado) " · estaba en la papelera" else "", color = Muted, fontSize = 12.sp)
                BotonEscribir("Sacar como obra nueva", Modifier.padding(top = 4.dp)) {
                    val n = CopiasEscritos.recupera(e, d!!, o.id)
                    aviso(if (n != null) "«${n.s("titulo")}» está en tus obras" else "No se pudo sacar esa obra")
                    onCerrar()
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        BotonEscribir("Guardar esta copia fuera del móvil", suave = true) { guardar.launch(c.archivo.name.removeSuffix(".gz")) }
    }
}
