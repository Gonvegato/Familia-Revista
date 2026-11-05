package com.gonzalo.gamerate

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.Configuration
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Los colores de Atrio, para otras apps tuyas (Pórtico): content://com.gonzalo.gamerate.tema/colores
 * Devuelve una sola fila con los colores que se ven AHORA en Atrio (los de tu paleta, tu fondo de color, tus colores a mano
 * y tu acento, ya mezclados y en claro u oscuro según lo que tengas elegido): solo colores, nada de tus datos.
 * Se calcula leyendo los ajustes guardados, sin tocar la pantalla ni el estado de la app.
 */
class TemaProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val ctx = context ?: return null
        val p = ctx.getSharedPreferences("gamerate", android.content.Context.MODE_PRIVATE)
        val t = calcula(p, Store(ctx).themeMode, (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
        val c = MatrixCursor(arrayOf("version", "oscuro", "paper", "crudo", "ink", "acento", "sand", "line", "edge", "muted", "oro"), 1)
        c.addRow(arrayOf<Any>(1, if (t.second) 1 else 0, t.first.cPaper.toArgb(), t.first.cCrudo.toArgb(), t.first.cInk.toArgb(), t.first.cGeneral().toArgb(),
            t.first.cSand.toArgb(), t.first.cLine.toArgb(), t.first.cEdge.toArgb(), t.first.cMuted.toArgb(), Color(t.first.gold).toArgb()))
        return c
    }

    override fun getType(uri: Uri) = "vnd.android.cursor.item/vnd.com.gonzalo.gamerate.tema"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        /**
         * Los tonos que se ven en Atrio y si es oscuro, a partir de los ajustes guardados (las mismas reglas que Skin.tones y
         * MainActivity: un fondo a mano o de color fija el modo; si no, el tema elegido o el del sistema).
         */
        fun calcula(p: android.content.SharedPreferences, themeMode: Int, sistemaOscuro: Boolean): Pair<Tones, Boolean> {
            val own = when (themeMode) { 1 -> false; 2 -> true; else -> sistemaOscuro }
            val fondo = FONDOS.firstOrNull { it.id == p.getString("skin_fondo", "") }
            val fondoPropio = p.getLong("skin_fondo_propio", 0L)
            val tintaPropia = p.getLong("skin_tinta_propia", 0L)
            val acentoPropio = p.getLong("skin_acento_propio", 0L)
            val oscuro = (if (fondoPropio != 0L) Color(fondoPropio).luminance() < 0.4f else null) ?: fondo?.oscuro ?: own
            val paleta = PALETTES.first { it.id == "revista" }
            val base = fondo?.tones ?: if (oscuro) paleta.dark else paleta.light
            val accentId = p.getString("skin_accent_id", "edicion") ?: "edicion"
            val conAcento = when {
                fondo != null && (accentId == "edicion" || accentId == "rojo") -> base
                else -> ACCENT_COLORS.firstOrNull { it.id == accentId }?.let { a -> base.copy(general = if (oscuro) a.dark else a.light) } ?: base
            }
            return Skin.conPropios(conAcento, fondoPropio, tintaPropia, acentoPropio) to oscuro
        }
    }
}
