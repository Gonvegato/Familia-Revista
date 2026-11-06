package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * El avance rápido: cuánto se acelera y cómo se activa. LibretroDroid ejecuta [factor] fotogramas del núcleo por cada imagen que
 * dibuja, así que la velocidad de verdad se mide contando imágenes por segundo (si el móvil no llega, se ve).
 */
class AvanceRapido(var modo: Modo = Modo.MANTENER, var velocidad: Int = 3) {
    enum class Modo(val nombre: String) { MANTENER("Mientras lo pulsas"), CONMUTAR("Un toque lo enciende, otro lo apaga") }

    var activo = false
        private set

    /** El botón de avance rápido se pulsa ([abajo]) o se suelta. Devuelve si ha cambiado algo. */
    fun pulsar(abajo: Boolean): Boolean {
        val antes = activo
        activo = when (modo) { Modo.MANTENER -> abajo; Modo.CONMUTAR -> if (abajo) !activo else activo }
        return antes != activo
    }

    fun apagar(): Boolean { val c = activo; activo = false; return c }

    /** Cuántos fotogramas del núcleo por imagen (1 = velocidad normal). */
    val factor: Int get() = if (activo) velocidad.coerceIn(2, 8) else 1

    companion object {
        val velocidades = listOf(2, 3, 4, 8)

        /** La velocidad real: imágenes por segundo × fotogramas por imagen ÷ los que da la consola por segundo. */
        fun real(imagenesPorSegundo: Float, factor: Int, nominal: Float = 60f): Float = if (nominal <= 0f) 0f else imagenesPorSegundo * factor / nominal

        /** «▶▶ ×4 · real ×3,2» (sin la parte real si va a su velocidad). */
        fun indicador(factor: Int, real: Float?): String {
            val base = "▶▶ ×$factor"
            if (real == null || real <= 0f) return base
            return if (real >= factor - 0.15f) base else base + " · real ×" + String.format(Locale("es", "ES"), "%.1f", real)
        }
    }
}

/**
 * Cómo se juega con el jugador interno (los mandos, el avance rápido, la imagen) y lo que cambiaste de cada mando: se guarda aquí y viaja
 * con tu cuenta (`ajustes/hiedra_juego`, un campo por ajuste; los mandos, `mapa:<descriptor>`).
 */
object AjustesJuego {
    val GRUPO: EspejoCuenta.Grupo = EspejoCuenta.Grupo("hiedra_juego", "hiedra_juego", tipo = { k ->
        when (k) {
            "opacidad", "tamano", "zona" -> EspejoCuenta.Tipo.DECIMAL
            "vibracion", "pequenos", "etiqueta", "avSilencio" -> EspejoCuenta.Tipo.SI_NO
            "avVel", "autoguardado" -> EspejoCuenta.Tipo.ENTERO
            "tactil", "avModo", "orientacion", "filtro" -> EspejoCuenta.Tipo.TEXTO
            else -> if (k.startsWith("mapa:")) EspejoCuenta.Tipo.TEXTO else null
        }
    }, alLlegar = { cargar(it) })

    enum class Tactil(val nombre: String) { AUTO("Ocultos con mando"), SIEMPRE("Siempre"), NUNCA("Nunca") }
    enum class Orientacion(val nombre: String) { APAISADA("Apaisada"), VERTICAL("Vertical"), MOVIL("La del móvil") }
    enum class Filtro(val nombre: String) { NITIDO("Nítido"), SUAVE("Suave"), LCD("LCD"), CRT("CRT") }

    var tactil by mutableStateOf(Tactil.AUTO)
    var opacidad by mutableFloatStateOf(0.6f)
    var tamano by mutableFloatStateOf(1f)
    var vibracion by mutableStateOf(true)
    var botonesPequenosConMando by mutableStateOf(true)
    var porEtiqueta by mutableStateOf(false)
    var zonaMuerta by mutableFloatStateOf(0.35f)
    var avanceModo by mutableStateOf(AvanceRapido.Modo.MANTENER)
    var avanceVelocidad by mutableIntStateOf(3)
    var avanceSilencio by mutableStateOf(true)
    var autoguardadoMin by mutableIntStateOf(2)
    var orientacion by mutableStateOf(Orientacion.APAISADA)
    var filtro by mutableStateOf(Filtro.NITIDO)

    private fun p(c: Context) = EspejoCuenta.prefs(c, GRUPO)

    fun cargar(c: Context) {
        val p = p(c)
        tactil = Tactil.entries.firstOrNull { it.name == p.getString("tactil", "") } ?: Tactil.AUTO
        opacidad = p.getFloat("opacidad", 0.6f); tamano = p.getFloat("tamano", 1f)
        vibracion = p.getBoolean("vibracion", true); botonesPequenosConMando = p.getBoolean("pequenos", true)
        porEtiqueta = p.getBoolean("etiqueta", false); zonaMuerta = p.getFloat("zona", 0.35f)
        avanceModo = AvanceRapido.Modo.entries.firstOrNull { it.name == p.getString("avModo", "") } ?: AvanceRapido.Modo.MANTENER
        avanceVelocidad = p.getInt("avVel", 3); avanceSilencio = p.getBoolean("avSilencio", true)
        autoguardadoMin = p.getInt("autoguardado", 2)
        orientacion = Orientacion.entries.firstOrNull { it.name == p.getString("orientacion", "") } ?: Orientacion.APAISADA
        filtro = Filtro.entries.firstOrNull { it.name == p.getString("filtro", "") } ?: Filtro.NITIDO
    }

    /** Guarda lo que ha cambiado (lo que nunca has tocado se queda sin guardar: así no pisa en la cuenta lo que elegiste en otro dispositivo). */
    fun guardar(c: Context) {
        val p = p(c); val hay = p.all; val ed = p.edit()
        fun pon(k: String, v: Any, porDefecto: Any) {
            if (hay[k] == v || (hay[k] == null && v == porDefecto)) return
            when (v) { is Float -> ed.putFloat(k, v); is Boolean -> ed.putBoolean(k, v); is Int -> ed.putInt(k, v); else -> ed.putString(k, v.toString()) }
        }
        pon("tactil", tactil.name, Tactil.AUTO.name); pon("opacidad", opacidad, 0.6f); pon("tamano", tamano, 1f); pon("vibracion", vibracion, true)
        pon("pequenos", botonesPequenosConMando, true); pon("etiqueta", porEtiqueta, false); pon("zona", zonaMuerta, 0.35f)
        pon("avModo", avanceModo.name, AvanceRapido.Modo.MANTENER.name); pon("avVel", avanceVelocidad, 3); pon("avSilencio", avanceSilencio, true)
        pon("autoguardado", autoguardadoMin, 2); pon("orientacion", orientacion.name, Orientacion.APAISADA.name); pon("filtro", filtro.name, Filtro.NITIDO.name)
        ed.apply()
    }

    /** Lo que cambiaste de cada mando (por su descriptor de Android, que no cambia al reconectarlo). */
    fun mapa(c: Context, descriptor: String): Map<Int, Mandos.Destino> = Mandos.mapaDeTexto(p(c).getString("mapa:$descriptor", "").orEmpty())
    fun ponMapa(c: Context, descriptor: String, m: Map<Int, Mandos.Destino>) { p(c).edit().putString("mapa:$descriptor", Mandos.mapaATexto(m)).apply() }
}
