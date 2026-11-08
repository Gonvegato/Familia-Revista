package com.gonzalo.ptah

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.text.Layout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/*
 * Lo tuyo: una nota, una cuenta atrás a una fecha y una foto (con su marco de revista y su pie). La foto se queda en este móvil.
 */

object TipoNota : TipoWidget {
    override val id = "nota"
    override val nombre = "Nota"
    override val grupo = "Lo tuyo"
    override val descripcion = "Un titular y unas líneas, para tenerlo a la vista. Un toque y la cambias."
    override val muestra = Tamanos.Celdas(3, 2)
    override val piezas = listOf("titulo" to "El titular", "texto" to "El texto")
    override val partes = listOf("ante" to "El antetítulo", "titulo" to "El titular", "rayas" to "La raya")
    override fun opcionesDeSerie() = mapOf("ante" to "Nota", "titulo" to "Lo de hoy", "texto" to "Comprar pan, llamar a mamá y regar las plantas.")
    override fun descripcionToque(d: Diseno) = if (d.id.startsWith("sin")) "Elige un diseño para este widget" else "Cambiar la nota en Ptah"

    override fun pinta(l: Lienzo) {
        val a = l.area; val e = l.e; val d = l.d
        var y = a.top
        if (e.ve("ante") && d.texto("ante").isNotBlank() && a.height() >= 70f) y += l.seccion(d.texto("ante"), a.left, y, a.width()) + 5f
        val titulo = d.texto("titulo")
        val texto = d.texto("texto")
        if (e.ve("titulo") && titulo.isNotBlank()) {
            val maxAlto = if (texto.isBlank()) a.bottom - y else (a.bottom - y) * 0.5f
            val t = l.titular(titulo, a.width(), maxAlto, 30f, 11f, 3, l.pieza("titulo", l.tinta))
            y += l.pon(t, a.left, y) + 3f
            if (e.ve("rayas") && texto.isNotBlank() && a.bottom - y > 20f) { l.filete(y, 1.2f); y += 5f }
        }
        val p = l.tpk(l.fTexto, 12f, l.pieza("texto", l.tinta))
        val alto = p.fontMetrics.let { it.descent - it.ascent } * 1.1f
        // (el texto, si hay sitio para algo más que un par de letras)
        if (texto.isNotBlank() && a.bottom - y > alto && a.width() >= 80f) {
            l.pon(l.capa(texto, p, a.width(), max(1, ((a.bottom - y) / alto).toInt()), 1.1f), a.left, y)
        }
    }

    @Composable override fun Editor(d: Diseno, cambia: (Diseno) -> Unit) {
        Campo("Antetítulo", d.texto("ante"), "Nota") { cambia(d.con("ante", it)) }
        Campo("El titular", d.texto("titulo"), "Lo de hoy") { cambia(d.con("titulo", it)) }
        Campo("El texto", d.texto("texto"), "Lo que quieras tener a la vista", lineas = 6) { cambia(d.con("texto", it)) }
    }
}

object CuentaAtras : TipoWidget {
    override val id = "cuentaatras"
    override val nombre = "Cuenta atrás"
    override val grupo = "Lo tuyo"
    override val descripcion = "Los días que faltan para lo que esperas (o los que han pasado). Un toque y la cambias."
    override val muestra = Tamanos.Celdas(2, 2)
    override val piezas = listOf("cifra" to "La cifra", "titulo" to "Para qué", "fecha" to "La fecha")
    override val partes = listOf("ante" to "Faltan / Hace", "titulo" to "Para qué", "fecha" to "La fecha")
    override fun necesita(d: Diseno) = setOf(Necesidad.MEDIANOCHE)
    override fun descripcionToque(d: Diseno) = "Cambiar la cuenta atrás en Ptah"

    override fun opcionesDeSerie(): Map<String, Any?> {
        val hoy = LocalDate.now()
        val navidad = LocalDate.of(hoy.year, 12, 25).let { if (it.isBefore(hoy)) it.plusYears(1) else it }
        return mapOf("fecha" to navidad.toEpochDay(), "titulo" to "Navidad")
    }

    /** Lo que se dice: los días (siempre en positivo), «Faltan», «Falta», «Es hoy» o «Hace», y «días» o «día». */
    data class Cuenta(val dias: Long, val ante: String, val unidad: String, val pasada: Boolean)

    fun cuenta(hoy: LocalDate, fecha: LocalDate): Cuenta {
        val n = ChronoUnit.DAYS.between(hoy, fecha)
        return when {
            n == 0L -> Cuenta(0, "Es hoy", "", false)
            n > 0 -> Cuenta(n, if (n == 1L) "Falta" else "Faltan", if (n == 1L) "día" else "días", false)
            else -> Cuenta(-n, "Hace", if (n == -1L) "día" else "días", true)
        }
    }

    fun fecha(d: Diseno): LocalDate = LocalDate.ofEpochDay(d.largo("fecha", LocalDate.now().toEpochDay()))

    override fun pinta(l: Lienzo) {
        val a = l.area; val e = l.e; val d = l.d
        val f = fecha(d)
        val c = cuenta(l.datos.ahora().toLocalDate(), f)
        val titulo = d.texto("titulo")
        var y = a.top
        if (e.ve("ante") && a.height() >= 70f) y += l.seccion(c.ante, a.left, y, a.width()) + 4f
        // Debajo de la cifra: «días para Navidad» (en cursiva) y la fecha; lo que no quepa entero, no se pone
        val frase = listOf(c.unidad, if (e.ve("titulo") && titulo.isNotBlank()) (if (c.pasada) "desde " else if (c.dias == 0L) "" else "para ") + titulo else "")
            .filter { it.isNotBlank() }.joinToString(" ")
        val fraseH = if (frase.isNotBlank()) l.altoLinea(frase, l.fCursiva, 13f, a.width()).let { if (it == 0f && c.unidad.isNotBlank()) l.altoLinea(c.unidad, l.fCursiva, 13f, a.width()) else it } else 0f
        val fraseT = if (fraseH > 0f && l.altoLinea(frase, l.fCursiva, 13f, a.width()) > 0f) frase else c.unidad
        val fechaH = if (e.ve("fecha") && a.height() >= 100f) 12f * l.k else 0f
        val pie = fraseH + fechaH
        val cifra = if (c.dias == 0L) "¡Hoy!" else Formato.cifra(c.dias)
        val sitio = a.bottom - y - pie
        val t = l.titular(cifra, a.width(), sitio, 120f, 12f, 1, l.pieza("cifra", l.acento), inter = 0.9f)
        l.pon(t, a.left, y + (sitio - t.height) / 2)
        var py = a.bottom - pie
        if (fraseH > 0f) py += l.linea(fraseT, l.fCursiva, 13f, l.pieza("titulo", l.tinta), a.left, py, a.width())
        if (fechaH > 0f) l.ante(Formato.fecha(f), a.left, py + 1f, a.width(), l.pieza("fecha", l.gris), 8f)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable override fun Editor(d: Diseno, cambia: (Diseno) -> Unit) {
        var abierto by remember { mutableStateOf(false) }
        Campo("Para qué", d.texto("titulo"), "Navidad, las vacaciones, tu cumpleaños…") { cambia(d.con("titulo", it)) }
        Etiqueta("La fecha", Modifier.padding(top = 10.dp))
        MagLink(Formato.fecha(fecha(d)), "Un toque y la cambias", accent = true) { abierto = true }
        if (abierto) {
            val st = rememberDatePickerState(initialSelectedDateMillis = fecha(d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(onDismissRequest = { abierto = false }, colors = DatePickerDefaults.colors(containerColor = Paper),
                confirmButton = { TextButton({
                    st.selectedDateMillis?.let { ms -> cambia(d.con("fecha", java.time.Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())) }
                    abierto = false
                }) { Text("PONERLA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
                dismissButton = { TextButton({ abierto = false }) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
            ) { DatePicker(st, colors = DatePickerDefaults.colors(containerColor = Paper, selectedDayContainerColor = Acento, todayDateBorderColor = Acento, todayContentColor = Acento)) }
        }
        Nota("Se vuelve a contar sola cada medianoche.", Modifier.padding(top = 4.dp))
    }
}

/**
 * Las fotos de los widgets de foto: copiadas (en pequeño, como mucho de 1600 px) a la memoria de la app en este móvil, una por diseño
 * (`fotos/<id>.jpg`). No van a la cuenta: en tus otros móviles, el diseño sale con «Elige la foto».
 */
object Fotos {
    private fun dir(ctx: Context) = File(ctx.filesDir, "fotos").apply { mkdirs() }
    fun archivo(ctx: Context, id: String) = File(dir(ctx), "$id.jpg")
    fun hay(ctx: Context, id: String) = archivo(ctx, id).isFile

    /** Copia la foto elegida (bloquea). */
    fun guarda(ctx: Context, id: String, uri: Uri): Boolean = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var muestra = 1
        while (max(o.outWidth, o.outHeight) / (muestra * 2) >= 1600) muestra *= 2
        val bm = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra }) } ?: return false
        val giro = runCatching { ctx.contentResolver.openInputStream(uri)?.use { when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90; 3 -> 180; 8 -> 270; else -> 0 } } ?: 0 }.getOrDefault(0)
        val escala = min(1f, 1600f / max(bm.width, bm.height))
        val m = android.graphics.Matrix().apply { postScale(escala, escala); postRotate(giro.toFloat()) }
        val final = Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, m, true)
        archivo(ctx, id).outputStream().use { final.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    }.getOrDefault(false)

    fun guarda(ctx: Context, id: String, bm: Bitmap) { archivo(ctx, id).outputStream().use { bm.compress(Bitmap.CompressFormat.JPEG, 90, it) } }

    fun carga(ctx: Context, id: String, maxLado: Int = 1200): Bitmap? {
        val f = archivo(ctx, id)
        if (!f.isFile) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var m = 1
        while (max(o.outWidth, o.outHeight) / (m * 2) >= maxLado) m *= 2
        return runCatching { BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = m }) }.getOrNull()
    }

    fun borra(ctx: Context, id: String) { archivo(ctx, id).delete() }
}

object Foto : TipoWidget {
    override val id = "foto"
    override val nombre = "Foto"
    override val grupo = "Lo tuyo"
    override val descripcion = "Una foto tuya con su marco de revista y su pie. Se queda en este móvil."
    override val muestra = Tamanos.Celdas(2, 2)
    override val piezas = listOf("pie" to "El pie de foto", "filo" to "El filo de la foto")
    override val partes = listOf("pie" to "El pie de foto")
    override fun opcionesDeSerie() = mapOf("pie" to "", "sangre" to false)
    override fun descripcionToque(d: Diseno) = "Cambiar la foto en Ptah"

    override fun pinta(l: Lienzo) {
        val a = l.area; val e = l.e; val d = l.d
        val img = l.datos.foto(d.id)
        val pie = d.texto("pie")
        val sangre = d.si("sangre")
        val conPie = e.ve("pie") && pie.isNotBlank() && a.height() >= 80f && a.width() >= 96f
        if (sangre) {
            // A sangre: la foto entera y el pie encima, sobre un velo
            val r = RectF(0f, 0f, l.w, l.h)
            if (img != null) l.foto(img, r) else vacia(l, r)
            if (conPie) {
                val p = l.tpk(l.fCursiva, 12f, 0xFFFFFFFF.toInt())
                val t = l.capa(pie, p, a.width(), 2)
                val top = a.bottom - t.height - 4f
                l.c.drawRect(0f, top - 24f, l.w, l.h, android.graphics.Paint().apply {
                    shader = android.graphics.LinearGradient(0f, top - 24f, 0f, l.h, 0x00000000, 0xAA000000.toInt(), android.graphics.Shader.TileMode.CLAMP) })
                l.pon(t, a.left, top)
            }
            return
        }
        val pieH = if (conPie) l.capa(pie, l.tpk(l.fCursiva, 11.5f, l.tinta), a.width() - 6f * l.k - 5f, 2, al = Layout.Alignment.ALIGN_NORMAL).height + 8f else 0f
        val r = RectF(a.left, a.top, a.right, a.bottom - pieH)
        val rad = max(0f, l.radio - l.pad) * 0.4f
        if (img != null) {
            l.foto(img, r, rad)
            l.c.drawRoundRect(r, rad, rad, l.trazo(l.pieza("filo", Lienzo.conAlfa(l.tinta, 60)), 0.6f))
        } else vacia(l, r, rad)
        if (conPie) {
            val q = 6f * l.k
            l.c.drawRect(a.left, r.bottom + 6f + 2f, a.left + q, r.bottom + 6f + 2f + q, l.lleno(l.acento))
            l.pon(l.capa(pie, l.tpk(l.fCursiva, 11.5f, l.pieza("pie", l.tinta)), a.width() - q - 5f, 2, al = Layout.Alignment.ALIGN_NORMAL), a.left + q + 5f, r.bottom + 6f)
        }
    }

    private fun vacia(l: Lienzo, r: RectF, rad: Float = 0f) {
        l.c.drawRoundRect(r, rad, rad, l.lleno(Lienzo.mezcla(l.papelDeVerdad(), l.tinta, 0.08f)))
        val t = l.titular("Elige la foto", r.width() - 16f, r.height() * 0.5f, 20f, 8f, 2, l.gris, f = l.fCursiva, al = Layout.Alignment.ALIGN_CENTER)
        l.pon(t, r.left + 8f, r.centerY() - t.height / 2)
    }

    @Composable override fun Editor(d: Diseno, cambia: (Diseno) -> Unit) {
        val ctx = LocalContext.current
        var aviso by remember { mutableStateOf<String?>(null) }
        val elige = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) kotlin.concurrent.thread {
                val ok = Fotos.guarda(ctx.applicationContext, d.id, uri)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    aviso = if (ok) null else "No se ha podido leer esa foto."
                    // (cambia la hora del diseño: la vista previa y los widgets la vuelven a pintar)
                    cambia(d.copy(cambiado = System.currentTimeMillis()))
                }
            }
        }
        MagLink(if (Fotos.hay(ctx, d.id)) "Elegir otra foto" else "Elegir la foto", "De tu galería: se queda en este móvil, no va a tu cuenta", accent = true) {
            elige.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        aviso?.let { Nota(it, color = Acento) }
        Campo("El pie de foto", d.texto("pie"), "Lo que cuenta la foto", lineas = 2) { cambia(d.con("pie", it)) }
        SiNo("A sangre", "La foto ocupa todo el widget y el pie va encima.", d.si("sangre")) { cambia(d.con("sangre", it)) }
    }
}
