package com.gonzalo.ptah

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * «Qué ve tu móvil»: lo que Widgets sabe de este móvil y de sus widgets, para saber por qué algo no sale como debería, sin adivinar. Se ve
 * en pantalla y se copia o se comparte como texto. **Nada secreto**: ni tu usuario, ni tu correo, ni tu uid, ni ninguna clave; solo la
 * versión, el móvil, los tamaños que da el lanzador, los ids y los nombres de tus diseños y lo que ha pasado al guardarlos.
 */
object Diagnostico {
    class Seccion(val titulo: String, val filas: List<Pair<String, String>>)

    private fun opciones(o: android.os.Bundle?): List<Pair<String, String>> {
        if (o == null) return listOf("Opciones" to "ninguna")
        val r = ArrayList<Pair<String, String>>()
        r += "Ancho mín. / máx." to "${o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)} / ${o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)} dp"
        r += "Alto mín. / máx." to "${o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)} / ${o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)} dp"
        if (Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION")
            val ts = o.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            r += "Tamaños (OPTION_APPWIDGET_SIZES)" to (ts?.takeIf { it.isNotEmpty() }?.joinToString(", ") { "${it.width.toInt()}×${it.height.toInt()}" } ?: "no los da")
        }
        r += "Dónde está" to when (o.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1)) {
            android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN -> "pantalla de inicio"; android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD -> "pantalla de bloqueo"; -1 -> "no lo dice"; else -> "otro (${o.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY)})"
        }
        val otras = o.keySet().filter { !it.startsWith("appWidget") }.sorted()
        if (otras.isNotEmpty()) r += "Otras opciones del lanzador" to otras.joinToString(", ") { k -> "$k=${runCatching { @Suppress("DEPRECATION") o.get(k) }.getOrNull()}" }.take(400)
        return r
    }

    fun secciones(ctx: Context): List<Seccion> {
        val pm = ctx.packageManager
        val info = runCatching { pm.getPackageInfo(ctx.packageName, 0) }.getOrNull()
        val lanzador = runCatching { pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName }.getOrNull()
        val dm = ctx.resources.displayMetrics
        val r = ArrayList<Seccion>()
        r += Seccion("Widgets y este móvil", listOf(
            "Widgets" to "${info?.versionName ?: "?"} (${info?.longVersionCode ?: "?"})",
            "Móvil" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "Lanzador" to (lanzador ?: "no se sabe"),
            "Pantalla" to "${dm.widthPixels}×${dm.heightPixels} px, densidad ${dm.density} (${dm.densityDpi} dpi), letra al ${(ctx.resources.configuration.fontScale * 100).toInt()} %",
            "Modo" to if (Widgets.nocheAhora(ctx)) "oscuro" else "claro",
        ))
        val motivo = Minutero.motivo(ctx)
        r += Seccion("La hora de los relojes", listOf(
            "Ahora" to (if (motivo == null) "dibujada con la letra del diseño, repintada cada minuto" else "con la letra del móvil (TextClock), porque $motivo"),
            "Alarmas exactas" to if (Minutero.puedeExactas(ctx)) "sí (se pueden poner)" else "no",
            "Última vez que se pusieron en hora" to Registro.horaDe(Minutero.ultimo(ctx)) + if (Minutero.atrasado(ctx)) " (¡atrasado!)" else "",
            "Segundo plano restringido" to if (Minutero.restringida(ctx)) "sí (Android frena a Widgets)" else "no",
            "Sin optimización de batería" to if (Minutero.sinOptimizar(ctx)) "sí" else "no (la batería puede frenarlo)",
        ))
        val mgr = runCatching { AppWidgetManager.getInstance(ctx) }.getOrNull()
        val ids = Widgets.ids(ctx)
        if (ids.isEmpty()) r += Seccion("Tus widgets", listOf("Widgets de Widgets" to "ninguno en la pantalla de inicio"))
        ids.forEach { id ->
            val dId = Widgets.disenoDe(ctx, id)
            val d = dId?.let { Disenos.de(ctx, it) }
            val p = Registro.ultimoPintado(ctx, id)
            val filas = ArrayList<Pair<String, String>>()
            filas += "Diseño" to (if (dId == null) "ninguno" else if (d == null) "$dId (¡ya no está!)" else "${d.nombre} ($dId, ${Tipos.de(d.tipo)?.nombre ?: d.tipo})")
            filas += opciones(mgr?.getAppWidgetOptions(id))
            filas += "Tamaños a los que se pinta" to Widgets.tamanosPara(ctx, mgr?.getAppWidgetOptions(id)).joinToString(", ") { (w, h) -> "${w.toInt()}×${h.toInt()}" }
            filas += "Último pintado" to (p?.let { Registro.horaDe(it.optLong("t")) } ?: "nunca (desde esta versión)")
            if (p != null) filas += "Lo que se pintó" to p.optString("v").ifBlank { "—" }.replace(" | ", "\n")
            p?.optString("e")?.takeIf { it.isNotBlank() }?.let { filas += "Error al pintar" to it }
            p?.optString("e_antes")?.takeIf { it.isNotBlank() }?.let { filas += "Error de antes" to it }
            r += Seccion("Widget $id", filas)
        }
        val disenos = Disenos.todos(ctx)
        r += Seccion("Tus diseños", if (disenos.isEmpty()) listOf("Diseños" to "ninguno") else disenos.map { d ->
            val e = d.estilo
            "${d.nombre} (${d.id})" to listOf(
                "${Tipos.de(d.tipo)?.nombre ?: d.tipo} · guardado ${Registro.horaDe(d.cambiado)} · ${Registro.origenDe(ctx, d.id)}",
                "fondo ${e.fondo.nombre.lowercase()} ${if (e.fondo == TipoFondo.PAPEL || e.fondo == TipoFondo.TRANSPARENTE) "" else nombreColor(e.colorFondo)}${if (e.fondo == TipoFondo.DEGRADADO) " → " + nombreColor(e.colorFondo2) else ""}",
                "tinta ${nombreColor(e.tinta, "la de tu estilo")} · acento ${nombreColor(e.acento, "el de tu estilo")}" + if (e.piezas.isNotEmpty()) " · piezas ${e.piezas.entries.joinToString { "${it.key} ${it.value}" }}" else "",
                "letras ${e.letraTitulos.ifBlank { "las de tu estilo" }} / ${e.letraTexto.ifBlank { "las de tu estilo" }} · texto al ${(e.escala * 100).toInt()} %",
            ).joinToString("\n")
        })
        r += Seccion("Lo último que ha pasado (al guardar)", Registro.ultimas(ctx).map { l -> l.substringBefore("  ") to l.substringAfter("  ") }.ifEmpty { listOf("Registro" to "nada todavía") })
        return r
    }

    /** Todo, como texto plano (para copiarlo o compartirlo). */
    fun texto(ctx: Context): String = buildString {
        append("WIDGETS · QUÉ VE TU MÓVIL\n")
        secciones(ctx).forEach { s ->
            append("\n== ${s.titulo} ==\n")
            s.filas.forEach { (k, v) -> append("$k: ${v.replace("\n", "\n    ")}\n") }
        }
    }
}

/** La pantalla «Qué ve tu móvil». */
@Composable
fun PantDiagnostico(alVolver: () -> Unit) {
    val ctx = LocalContext.current
    var aviso by remember { mutableStateOf<String?>(null) }
    var vez by remember { mutableIntStateOf(0) }
    val secciones = remember(vez) { Diagnostico.secciones(ctx) }
    val mg = margen()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = mg)) {
        RevistaTopBar(alVolver, "Ajustes")
        Antetitulo("Diagnóstico", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle("Qué ve tu móvil", 44.sp, 48.sp, letterSpacing = (-1).sp)
        Filete(3.dp)
        Text("Lo que Widgets sabe de este móvil y de tus widgets. Si algo no sale bien, cópialo y mándalo: no lleva nada secreto (ni tu usuario, ni tu correo, ni claves).",
            color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(12.dp))
        AccionGrande(null, "Copiar el informe", Modifier.fillMaxWidth()) {
            runCatching { ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Widgets: qué ve tu móvil", Diagnostico.texto(ctx))) }
            aviso = "Copiado. Pégalo donde quieras."
        }
        Spacer(Modifier.height(8.dp))
        BotonSuave("Compartir", modifier = Modifier.fillMaxWidth()) {
            runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Diagnostico.texto(ctx)), "Compartir el informe").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        EnlaceRevista("Volver a mirarlo") { vez++ }
        aviso?.let { Nota(it, color = Acento) }
        secciones.forEach { s ->
            Spacer(Modifier.height(16.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
            Antetitulo(s.titulo)
            s.filas.forEach { (k, v) ->
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(k, color = Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 13.sp)
                    Text(v, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 19.sp)
                }
                Filete(1.dp, Line, Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}
