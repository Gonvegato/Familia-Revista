package com.gonzalo.ptah

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.HandlerThread
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * Las letras de los dibujos (la vista previa del editor y los widgets): las que vienen con la app (las de la familia: Atrio de títulos y de
 * texto, y Vitrina) y las de Google Fonts, que se piden a los servicios de Google y se guardan para la próxima vez (como hace la app para
 * sus pantallas). Hasta que llega una de Google Fonts se usa la de la app.
 */
object Letras {
    private val fuentes = HashMap<Int, Typeface>()
    private val bajadas = HashMap<String, Typeface>()

    @Synchronized fun fuente(ctx: Context, id: Int): Typeface =
        fuentes.getOrPut(id) { runCatching { ctx.resources.getFont(id) }.getOrDefault(Typeface.SERIF) }

    /** La letra de títulos que toca: la del diseño ([nombre]) o, si no tiene, la de tu Apariencia. */
    fun nombreTitulos(nombre: String) = nombre.ifBlank { Skin.display }
    fun nombreTexto(nombre: String) = nombre.ifBlank { Skin.body }

    fun titular(ctx: Context, nombre: String): Typeface = de(ctx, nombreTitulos(nombre), 600, false, DISPLAY_FONTS) ?: fuente(ctx, R.font.display_semibold)
    fun cursiva(ctx: Context, nombre: String): Typeface {
        val n = nombreTitulos(nombre)
        if (n == "Atrio") return fuente(ctx, R.font.display_italic)
        return de(ctx, n, 500, true, DISPLAY_FONTS) ?: de(ctx, n, 400, false, DISPLAY_FONTS) ?: fuente(ctx, R.font.display_italic)
    }
    fun texto(ctx: Context, nombre: String): Typeface = de(ctx, nombreTexto(nombre), 500, false, BODY_FONTS) ?: fuente(ctx, R.font.body_medium)
    /** Los antetítulos en versalitas: siempre la negrita de la app, como en las hermanas. */
    fun negrita(ctx: Context): Typeface = fuente(ctx, R.font.body_bold)

    /** Una que ya esté (la de la app, Vitrina o una de Google Fonts ya bajada); null si aún no ha llegado. */
    private fun de(ctx: Context, nombre: String, peso: Int, cursiva: Boolean, lista: List<FontChoice>): Typeface? {
        if (nombre.isBlank() || nombre == "Atrio") return null
        if (nombre == "Vitrina") return vitrina(ctx, if (peso >= 600) 700 else 500)
        synchronized(bajadas) { return bajadas[clave(nombre, peso, cursiva, lista)] }
    }

    private fun pesoDe(nombre: String, peso: Int, lista: List<FontChoice>): Int {
        val pesos = (lista + DISPLAY_FONTS + BODY_FONTS).firstOrNull { it.name == nombre }?.weights?.takeIf { it.isNotEmpty() } ?: intArrayOf(400, 700)
        return pesos.minByOrNull { kotlin.math.abs(it - peso) } ?: 400
    }
    private fun clave(nombre: String, peso: Int, cursiva: Boolean, lista: List<FontChoice>) = "$nombre|${pesoDe(nombre, peso, lista)}|$cursiva"

    /** Pide (si hace falta) las letras de estos diseños: después, [titular] y [texto] ya las dan. */
    suspend fun prepara(ctx: Context, disenos: Collection<Diseno>) {
        val tit = disenos.map { nombreTitulos(it.estilo.letraTitulos) }.toSet()
        val tex = disenos.map { nombreTexto(it.estilo.letraTexto) }.toSet()
        for (n in tit) { pide(ctx, n, 600, false, DISPLAY_FONTS); pide(ctx, n, 500, true, DISPLAY_FONTS) }
        for (n in tex) pide(ctx, n, 500, false, BODY_FONTS)
    }

    private suspend fun pide(ctx: Context, nombre: String, peso: Int, cursiva: Boolean, lista: List<FontChoice>) {
        if (nombre.isBlank() || nombre == "Atrio" || nombre == "Vitrina") return
        val k = clave(nombre, peso, cursiva, lista)
        synchronized(bajadas) { if (bajadas.containsKey(k)) return }
        val t = bajar(ctx, nombre, pesoDe(nombre, peso, lista), cursiva) ?: return
        synchronized(bajadas) { bajadas[k] = t }
    }

    /** Bricolage Grotesque (la letra de Vitrina) es una letra variable: se pide su grueso y su anchura. */
    private fun vitrina(ctx: Context, peso: Int): Typeface? = runCatching {
        val k = "vitrina|$peso"
        synchronized(bajadas) { bajadas[k]?.let { return it } }
        val f = File(ctx.cacheDir, "bricolage.ttf")
        if (!f.exists()) ctx.resources.openRawResource(R.font.bricolage).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        Typeface.Builder(f).setFontVariationSettings("'wght' $peso, 'wdth' ${if (peso >= 700) 92 else 100}").build()
            .also { synchronized(bajadas) { bajadas[k] = it } }
    }.getOrNull()

    /** Pide una letra de Google Fonts a los servicios de Google. */
    private suspend fun bajar(ctx: Context, nombre: String, peso: Int, cursiva: Boolean): Typeface? = withTimeoutOrNull(5_000) {
        suspendCancellableCoroutine { k ->
            val th = HandlerThread("letras-ptah").also { it.start() }
            try {
                val pet = FontRequest("com.google.android.gms.fonts", "com.google.android.gms", "name=$nombre&weight=$peso" + if (cursiva) "&italic=1" else "", R.array.com_google_android_gms_fonts_certs)
                FontsContractCompat.requestFont(ctx, pet, object : FontsContractCompat.FontRequestCallback() {
                    override fun onTypefaceRetrieved(typeface: Typeface) { if (k.isActive) k.resume(typeface); th.quitSafely() }
                    override fun onTypefaceRequestFailed(reason: Int) { if (k.isActive) k.resume(null); th.quitSafely() }
                }, Handler(th.looper))
            } catch (e: Exception) { th.quitSafely(); if (k.isActive) k.resume(null) }
            k.invokeOnCancellation { th.quitSafely() }
        }
    }
}

/**
 * Las letras que puede llevar lo que se mueve solo en un widget (la hora y la fecha, con `TextClock`): Android solo deja poner allí letras
 * que vengan dentro de la app (con `android:fontFamily` en su diseño XML), no las de Google Fonts. Cada una tiene su diseño (`w_vivo_*.xml`).
 * La hora de un diseño con una letra de Google Fonts va con la de la familia que más se le parece.
 */
enum class LetraFija(val nombre: String, val fuente: Int, val diseno: Int, val mayusculas: Boolean = false, val espaciado: Float = 0f) {
    TITULOS("Clásica de títulos", R.font.display_semibold, R.layout.w_vivo_titulos),
    CURSIVA("Clásica en cursiva", R.font.display_italic, R.layout.w_vivo_cursiva),
    TEXTO("Clásica de texto", R.font.body_medium, R.layout.w_vivo_texto),
    NEGRITA("Clásica en negrita", R.font.body_bold, R.layout.w_vivo_negrita),
    VITRINA("Vitrina", R.font.bricolage, R.layout.w_vivo_vitrina),
    /** Versalitas (mayúsculas espaciadas, como los antetítulos): para la fecha. */
    VERSALITAS("Versalitas", R.font.body_bold, R.layout.w_vivo_versalitas, mayusculas = true, espaciado = 0.16f);

    companion object {
        /** La de la familia para la hora de un diseño con esta letra de títulos (la de Google Fonts no puede ir en un widget). */
        fun paraTitulos(nombre: String): LetraFija {
            val n = Letras.nombreTitulos(nombre)
            return when {
                n == "Vitrina" -> VITRINA
                n == "Atrio" -> TITULOS
                // Las de palo seco (sin remates), con la de texto; las de remates, con la de títulos
                BODY_FONTS.any { it.name == n } && DISPLAY_FONTS.none { it.name == n } -> NEGRITA
                n in setOf("Bebas Neue", "Oswald", "Anton", "Archivo Black", "Space Grotesk", "Bricolage Grotesque", "Unbounded", "Syne", "Teko", "Russo One",
                    "Orbitron", "Audiowide", "Righteous", "Bungee", "Black Ops One", "Chakra Petch", "Fredoka") -> VITRINA
                else -> TITULOS
            }
        }
    }
}
