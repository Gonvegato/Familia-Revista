package com.gonzalo.hiedra

import android.graphics.Color
import android.os.Build
import android.view.KeyEvent
import com.gonzalo.hiedra.Mandos.Boton as B
import com.swordfish.radialgamepad.library.config.ButtonConfig
import com.swordfish.radialgamepad.library.config.CrossConfig
import com.swordfish.radialgamepad.library.config.PrimaryDialConfig
import com.swordfish.radialgamepad.library.config.RadialGamePadConfig
import com.swordfish.radialgamepad.library.config.RadialGamePadTheme
import com.swordfish.radialgamepad.library.config.SecondaryDialConfig
import com.swordfish.radialgamepad.library.haptics.HapticConfig

/**
 * Los controles táctiles del jugador interno (con RadialGamePad, del autor de Lemuroid): una media luna a cada lado, como en la
 * consola. Cada control lleva como id la tecla del RetroPad que manda ([Mandos.Boton.tecla]); la cruceta y los sticks, los ID_ de aquí.
 * Así todo lo que se pulsa en la pantalla pasa por el mismo [Mandos.Estado] que el mando físico.
 *
 * Las de cada consola son las de Lemuroid 1.16 (la última que usó RadialGamePad 2.0.0, la misma versión que Hiedra), con dos cambios: los
 * botones llevan letra en vez de dibujo (Start, Select, los de PlayStation) y su botón de menú es aquí un hueco vacío, porque el menú de
 * Hiedra está arriba («MENÚ»). La de GBA es la de Hiedra 0.4.
 *
 * Las posiciones de los botones secundarios van en «horas» de un reloj de 12: 0 son las 3, y suben en sentido contrario a las agujas.
 */
object MandoTactil {
    const val ID_CRUZ = -10
    /** El stick izquierdo (N64, PSP, PlayStation con DualShock). */
    const val ID_STICK_IZQ = -11
    /** El stick derecho. */
    const val ID_STICK_DER = -12
    /** Los botones C de la N64: una cruceta que va al stick derecho (como en Mupen64Plus-Next). */
    const val ID_CRUZ_C = -13
    /** La palanca de las recreativas: va a la cruceta y al stick izquierdo a la vez (los juegos de FBNeo leen uno u otro). */
    const val ID_CRUZ_Y_STICK = -14

    enum class Lado { IZQUIERDO, DERECHO }

    /** El color de los botones: papel translúcido (se ven sobre cualquier juego) y el acento de Hiedra al pulsar. */
    fun tema(opacidad: Float, acento: Int): RadialGamePadTheme {
        fun a(base: Float) = (base * opacidad.coerceIn(0.1f, 1f) * 255).toInt().coerceIn(0, 255)
        return RadialGamePadTheme(
            normalColor = Color.argb(a(0.55f), 245, 240, 230),
            pressedColor = Color.argb(a(1f).coerceAtLeast(200), Color.red(acento), Color.green(acento), Color.blue(acento)),
            simulatedColor = Color.argb(a(0.55f), 245, 240, 230),
            textColor = Color.argb(a(1f).coerceAtLeast(160), 25, 22, 20),
            backgroundColor = Color.argb(a(0.18f), 245, 240, 230),
            lightColor = Color.argb(a(0.3f), 245, 240, 230),
        )
    }

    private fun boton(b: Mandos.Boton, etiqueta: String = b.nombre.uppercase()) = ButtonConfig(id = b.tecla, label = etiqueta, contentDescription = b.nombre)
    private fun sinLetra(b: Mandos.Boton) = ButtonConfig(id = b.tecla, contentDescription = b.nombre)
    private val START = boton(Mandos.Boton.START, "START")
    private val SELECT = boton(Mandos.Boton.SELECT, "SELECT")
    // PlayStation y PSP: ○ es A, △ es X, □ es Y y ✕ es B (en el RetroPad)
    private val CIRCULO = ButtonConfig(id = Mandos.Boton.A.tecla, label = "○", contentDescription = "Círculo")
    private val TRIANGULO = ButtonConfig(id = Mandos.Boton.X.tecla, label = "△", contentDescription = "Triángulo")
    private val CUADRADO = ButtonConfig(id = Mandos.Boton.Y.tecla, label = "□", contentDescription = "Cuadrado")
    private val EQUIS = ButtonConfig(id = Mandos.Boton.B.tecla, label = "✕", contentDescription = "Equis")

    // La cruceta con su forma de cruz (la de círculos de Lemuroid lleva un dibujo de flechas encima que aquí no hay: sin él parecían cuatro botones)
    private val CRUZ = PrimaryDialConfig.Cross(CrossConfig(ID_CRUZ, useDiagonals = true))
    private val PALANCA = PrimaryDialConfig.Cross(CrossConfig(ID_CRUZ_Y_STICK, useDiagonals = true))

    /** Los sticks de Lemuroid se giran un poco hacia fuera (8°) para que caigan bajo el pulgar. */
    private fun giro(grados: Float) = object : SecondaryDialConfig.RotationProcessor() { override fun getRotation(rotation: Float) = rotation + grados }
    private fun hueco(i: Int) = SecondaryDialConfig.Empty(i, 1, 1f, 0f)

    private fun izquierda(primario: PrimaryDialConfig, vararg s: SecondaryDialConfig) = RadialGamePadConfig(sockets = 12, primaryDial = primario, secondaryDials = s.toList())
    private fun derecha(botones: List<ButtonConfig>, rot: Float = 0f, centro: ButtonConfig? = null, vararg s: SecondaryDialConfig) =
        RadialGamePadConfig(sockets = 12, primaryDial = PrimaryDialConfig.PrimaryButtons(botones, center = centro, rotationInDegrees = rot), secondaryDials = s.toList())

    /** La disposición de un lado para un mando (sin tema ni vibración: eso lo pone [configuracion]). */
    fun disposicion(pad: Mandos.Pad, lado: Lado): RadialGamePadConfig {
        val izq = lado == Lado.IZQUIERDO
        return when (pad) {
            Mandos.Pad.GBA -> if (izq) izquierda(PrimaryDialConfig.Cross(CrossConfig(ID_CRUZ, useDiagonals = true)),
                SecondaryDialConfig.SingleButton(3, 1f, 0f, boton(B.L)), SecondaryDialConfig.SingleButton(1, 0.8f, 0f, SELECT),
                // Huecos vacíos: mantienen la media luna simétrica con la del otro lado
                SecondaryDialConfig.Empty(5, 1, 1f, 0f), SecondaryDialConfig.Empty(9, 1, 1f, 0f))
            else derecha(listOf(boton(B.A), boton(B.B)), 30f, null,
                SecondaryDialConfig.SingleButton(3, 1f, 0f, boton(B.R)), SecondaryDialConfig.SingleButton(5, 0.8f, 0f, START),
                SecondaryDialConfig.Empty(1, 1, 1f, 0f), SecondaryDialConfig.Empty(9, 1, 1f, 0f))
            Mandos.Pad.GB -> if (izq) izquierda(CRUZ, SecondaryDialConfig.SingleButton(4, 1f, 0f, SELECT), hueco(8))
                else derecha(listOf(boton(B.A), boton(B.B)), 30f, null, SecondaryDialConfig.SingleButton(2, 1f, 0f, START), hueco(10))
            Mandos.Pad.NES -> if (izq) izquierda(CRUZ, SecondaryDialConfig.SingleButton(4, 1f, 0f, SELECT), hueco(8))
                else derecha(listOf(boton(B.A), boton(B.B)), 0f, null, SecondaryDialConfig.SingleButton(2, 1f, 0f, START), hueco(10))
            Mandos.Pad.SNES -> if (izq) izquierda(CRUZ, SecondaryDialConfig.SingleButton(2, 1f, 0f, SELECT), SecondaryDialConfig.DoubleButton(3, 0f, boton(B.L)), hueco(8))
                else derecha(listOf(boton(B.A), boton(B.X), boton(B.Y), boton(B.B)), 0f, null,
                    SecondaryDialConfig.DoubleButton(2, 0f, boton(B.R)), SecondaryDialConfig.SingleButton(4, 1f, 0f, START), hueco(10))
            // Mega Drive de tres botones: C es A, B es B y A es Y; Select hace de Mode
            Mandos.Pad.MEGADRIVE -> if (izq) izquierda(CRUZ, SecondaryDialConfig.SingleButton(4, 1f, 0f, boton(B.SELECT, "MODE")), hueco(8))
                else derecha(listOf(boton(B.A, "C"), boton(B.B, "B"), boton(B.Y, "A")), 0f, null, SecondaryDialConfig.SingleButton(2, 1f, 0f, START), hueco(10))
            Mandos.Pad.MASTERSYSTEM -> if (izq) izquierda(CRUZ, hueco(4), hueco(8))
                else derecha(listOf(boton(B.A, "2"), boton(B.B, "1")), 0f, null, SecondaryDialConfig.SingleButton(2, 1f, 0f, boton(B.START, "PAUSA")), hueco(10))
            Mandos.Pad.GAMEGEAR -> if (izq) izquierda(CRUZ, hueco(4), hueco(8))
                else derecha(listOf(boton(B.A, "2"), boton(B.B, "1")), 30f, null, SecondaryDialConfig.SingleButton(2, 1f, 0f, START), hueco(10))
            Mandos.Pad.PCENGINE -> if (izq) izquierda(CRUZ, SecondaryDialConfig.SingleButton(4, 1f, 0f, SELECT), hueco(8))
                else derecha(listOf(boton(B.A, "II"), boton(B.B, "I")), 0f, null, SecondaryDialConfig.SingleButton(2, 1f, 0f, boton(B.START, "RUN")), hueco(10))
            Mandos.Pad.NEOGEOPOCKET -> if (izq) izquierda(CRUZ, hueco(4), hueco(8))
                else derecha(listOf(boton(B.A, "B"), boton(B.B, "A")), 0f, null, SecondaryDialConfig.SingleButton(2, 1f, 0f, boton(B.START, "OPTION")), hueco(10))
            Mandos.Pad.WONDERSWAN -> if (izq) izquierda(CRUZ, hueco(4), hueco(8))
                else derecha(listOf(boton(B.A), boton(B.B)), 30f, null, SecondaryDialConfig.SingleButton(2, 1f, 0f, START), hueco(10))
            Mandos.Pad.LYNX -> if (izq) izquierda(CRUZ, SecondaryDialConfig.SingleButton(4, 1f, 0f, boton(B.L, "OPT 1")), SecondaryDialConfig.SingleButton(8, 1f, 0f, boton(B.R, "OPT 2")))
                else derecha(listOf(boton(B.A), boton(B.B)), 15f, null, SecondaryDialConfig.SingleButton(2, 1f, 0f, boton(B.START, "PAUSA")), hueco(10))
            Mandos.Pad.ATARI2600 -> if (izq) izquierda(CRUZ, SecondaryDialConfig.SingleButton(4, 1f, 0f, boton(B.L, "DIF A")), SecondaryDialConfig.SingleButton(2, 1f, 0f, boton(B.L2, "DIF B")), hueco(8))
                else derecha(emptyList(), 0f, ButtonConfig(id = B.B.tecla, contentDescription = "Disparo"),
                    SecondaryDialConfig.SingleButton(2, 1f, 0f, boton(B.START, "RESET")), SecondaryDialConfig.SingleButton(4, 1f, 0f, SELECT), hueco(10))
            Mandos.Pad.ATARI7800 -> if (izq) izquierda(CRUZ, SecondaryDialConfig.SingleButton(4, 1f, 0f, SELECT), hueco(8))
                else derecha(listOf(boton(B.A, "2"), boton(B.B, "1")), 0f, null, SecondaryDialConfig.SingleButton(2, 1f, 0f, START), hueco(10))
            Mandos.Pad.ARCADE4 -> if (izq) izquierda(PALANCA, SecondaryDialConfig.SingleButton(4, 1f, 0f, boton(B.SELECT, "MONEDA")), hueco(8))
                else derecha(listOf(sinLetra(B.X), sinLetra(B.Y), sinLetra(B.B), sinLetra(B.A)), 60f, null, SecondaryDialConfig.SingleButton(2, 1f, 0f, START), hueco(10))
            Mandos.Pad.ARCADE6 -> if (izq) izquierda(PALANCA, SecondaryDialConfig.SingleButton(4, 1f, 0f, boton(B.SELECT, "MONEDA")), SecondaryDialConfig.SingleButton(3, 1f, 0f, START), hueco(8), hueco(9))
                else derecha(listOf(sinLetra(B.R), sinLetra(B.L), sinLetra(B.X), sinLetra(B.Y), sinLetra(B.B), ButtonConfig(id = KeyEvent.KEYCODE_UNKNOWN, visible = false)), 0f, sinLetra(B.A),
                    SecondaryDialConfig.Empty(9, 1, 0.5f, 0f), SecondaryDialConfig.Empty(3, 1, 0.5f, 0f))
            // N64: A es B, B es Y y Z es L2 en el RetroPad; los botones C, el stick derecho
            Mandos.Pad.N64 -> if (izq) izquierda(CRUZ, SecondaryDialConfig.SingleButton(2, 1f, 0f, boton(B.L2, "Z")), SecondaryDialConfig.DoubleButton(3, 0f, boton(B.L)),
                SecondaryDialConfig.Stick(9, 2, 2.2f, 0.1f, ID_STICK_IZQ, contentDescription = "Stick", rotationProcessor = giro(-8f)), hueco(8))
                else derecha(listOf(boton(B.L2, "Z"), boton(B.Y, "B"), boton(B.B, "A")), 60f, null,
                    SecondaryDialConfig.DoubleButton(2, 0f, boton(B.R)), SecondaryDialConfig.SingleButton(4, 1f, 0f, START), SecondaryDialConfig.Empty(10, 1, 1f, -0.1f),
                    SecondaryDialConfig.Cross(8, 2, 2.2f, 0.1f, CrossConfig(ID_CRUZ_C, shape = CrossConfig.Shape.CIRCLE, useDiagonals = false), rotationProcessor = giro(8f)))
            Mandos.Pad.PSX -> if (izq) izquierda(CRUZ, SecondaryDialConfig.SingleButton(2, 1f, 0f, SELECT), SecondaryDialConfig.SingleButton(3, 1f, 0f, boton(B.L, "L1")),
                SecondaryDialConfig.SingleButton(4, 1f, 0f, boton(B.L2)), hueco(8))
                else derecha(listOf(CIRCULO, TRIANGULO, CUADRADO, EQUIS), 0f, null, SecondaryDialConfig.SingleButton(2, 1f, 0f, boton(B.R2)),
                    SecondaryDialConfig.SingleButton(3, 1f, 0f, boton(B.R, "R1")), SecondaryDialConfig.SingleButton(4, 1f, 0f, START), hueco(10))
            Mandos.Pad.PSP -> if (izq) izquierda(CRUZ, SecondaryDialConfig.SingleButton(2, 1f, 0f, SELECT), SecondaryDialConfig.DoubleButton(3, 0f, boton(B.L)),
                SecondaryDialConfig.Stick(9, 2, 2.2f, 0f, ID_STICK_IZQ, contentDescription = "Stick", rotationProcessor = giro(-8f)), hueco(8))
                else derecha(listOf(CIRCULO, TRIANGULO, CUADRADO, EQUIS), 0f, null, SecondaryDialConfig.DoubleButton(2, 0f, boton(B.R)),
                    SecondaryDialConfig.SingleButton(4, 1f, 0f, START), hueco(10), SecondaryDialConfig.Empty(8, 2, 2.2f, 0f, giro(8f)))
            // DS (melonDS): el micrófono es L2 (soplar) y cerrar la tapa, L3; la pantalla de abajo se toca en la imagen
            Mandos.Pad.NDS -> if (izq) izquierda(CRUZ, SecondaryDialConfig.SingleButton(8, 1f, 0f, boton(B.L2, "MIC")), SecondaryDialConfig.SingleButton(10, 1f, 0f, boton(B.L3, "TAPA")),
                SecondaryDialConfig.SingleButton(2, 1f, 0f, SELECT), SecondaryDialConfig.SingleButton(4, 1f, 0f, boton(B.L)))
                else derecha(listOf(boton(B.A), boton(B.X), boton(B.Y), boton(B.B)), 0f, null, SecondaryDialConfig.SingleButton(2, 1f, 0f, boton(B.R)),
                    SecondaryDialConfig.SingleButton(4, 1f, 0f, START), hueco(10))
        }
    }

    /** La disposición de un lado para un sistema, con su tema y la vibración. */
    fun configuracion(sistema: String, lado: Lado, opacidad: Float, acento: Int, vibracion: Boolean): RadialGamePadConfig =
        disposicion(Nucleos.para(sistema)?.pad ?: Mandos.Pad.GBA, lado).copy(
            haptic = if (vibracion) HapticConfig.PRESS else HapticConfig.OFF, theme = tema(opacidad, acento),
            // Como Lemuroid: las coordenadas de pantalla fallan en apaisado desde Android 13, y las de la vista en algunos Samsung con Android 11
            preferScreenTouchCoordinates = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
        )

    /** A qué va un control del táctil (por su id). */
    fun destinoDe(id: Int): Mandos.Destino? = Mandos.Boton.entries.firstOrNull { it.tecla == id }?.let { Mandos.Destino.Pad(it) }

    /** Los ids de todos los botones de un lado (para las pruebas: que cada uno vaya a un botón que el núcleo usa). */
    fun botones(c: RadialGamePadConfig): List<Int> {
        val p = c.primaryDial
        val prim = if (p is PrimaryDialConfig.PrimaryButtons) p.dials.filter { it.visible }.map { it.id } + listOfNotNull(p.center?.id) else emptyList()
        val sec = c.secondaryDials.mapNotNull { d -> when (d) { is SecondaryDialConfig.SingleButton -> d.buttonConfig.id; is SecondaryDialConfig.DoubleButton -> d.buttonConfig.id; else -> null } }
        return prim + sec
    }
}
