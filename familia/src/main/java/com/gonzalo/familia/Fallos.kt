package com.gonzalo.familia

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Los fallos de cada app de la familia, apuntados en el dispositivo para verlos en Ajustes → Avanzado: si la app se cierra por un error,
 * queda dicho cuándo, en qué hilo y la traza. Solo en este dispositivo; no va a ningún sitio.
 *
 * Se pone en marcha solo, al arrancar el proceso, con [FallosProvider] (las apps no tienen clase Application).
 */
object Fallos {
    private fun p(c: Context) = c.getSharedPreferences("familia_fallos", Context.MODE_PRIVATE)
    private fun ahora() = SimpleDateFormat("d/M HH:mm", Locale("es", "ES")).format(Date())
    @Volatile private var vigilando = false
    @Volatile private var app: Context? = null

    /** Como [apunta], sin contexto a mano (el de la app, que se guarda al arrancar). */
    fun apunta(donde: String, e: Throwable) { app?.let { apunta(it, donde, e) } }

    /** Para los `CoroutineScope` de fondo: un fallo dentro se apunta y no cierra la app. */
    fun paraCorrutinas(donde: String) = kotlinx.coroutines.CoroutineExceptionHandler { _, e -> apunta(donde, e) }

    /** Un fallo que se ha podido parar (no ha cerrado la app). */
    fun apunta(c: Context, donde: String, e: Throwable) {
        runCatching { p(c).edit().putString("ultimo", "${ahora()} · $donde · " + e.stackTraceToString().take(2000)).apply() }
    }

    /** Los que cierran la app: se apuntan de golpe (el proceso se va) y siguen su camino. */
    fun vigila(c: Context) {
        if (vigilando) return
        vigilando = true
        val app = c.applicationContext ?: c
        this.app = app
        val antes = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { p(app).edit().putString("ultimo", "${ahora()} · se cerró (${t.name}) · " + e.stackTraceToString().take(2000)).commit() }
            antes?.uncaughtException(t, e)
        }
    }

    fun ultimo(c: Context): String = runCatching { p(c).getString("ultimo", "").orEmpty() }.getOrDefault("")
    fun olvida(c: Context) { runCatching { p(c).edit().clear().apply() } }
}

/** Arranca [Fallos.vigila] antes que nada en cada app que lleva el módulo (no expone nada). */
class FallosProvider : ContentProvider() {
    override fun onCreate(): Boolean { context?.let { Fallos.vigila(it) }; return true }
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}

/**
 * «Último fallo» para Ajustes → Avanzado, con los colores y letras de cada app. No sale nada si no ha habido ninguno.
 * [titulo] pinta el antetítulo al estilo de la app.
 */
@Composable
fun UltimoFallo(app: String, tinta: Color, gris: Color, letra: FontFamily, titulo: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var f by remember { mutableStateOf(Fallos.ultimo(ctx)) }
    if (f.isBlank()) return
    Column(Modifier.padding(bottom = 14.dp)) {
        titulo()
        BasicText("$app se cerró por un fallo. Mantén pulsado para copiarlo y pásamelo: dice dónde fue.",
            style = TextStyle(gris, 13.sp, fontFamily = letra, lineHeight = 18.sp))
        SelectionContainer {
            BasicText(f, style = TextStyle(tinta, 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp), modifier = Modifier.padding(vertical = 8.dp))
        }
        BasicText("Olvidarlo", style = TextStyle(gris, 13.sp, FontWeight.Bold, fontFamily = letra),
            modifier = Modifier.clickable { Fallos.olvida(ctx); f = "" }.padding(vertical = 6.dp))
    }
}
