package com.gonzalo.mau

/** Las posturas de Mau (una por dibujo de docs/mau/svg). */
enum class Pose { SENTADO, LEYENDO, ESCRIBIENDO, VIENDO, CELEBRANDO, TAPANDOSE, DURMIENDO, JUGANDO, RONRONEANDO, BOSTEZANDO, ESCONDIDO }

/** Un dibujo de Mau: su tamaño (el viewBox: 200 × 220; escondido, 200 × 80) y su árbol de grupos y trazos. */
class Dibujo(val ancho: Float, val alto: Float, val raiz: Grupo)

sealed interface Pieza

/** Un grupo del dibujo. [id] es el que se anima («cola», «cabeza», «contorno-cola»…); null si no se anima. */
class Grupo(val id: String?, val opacidad: Float, val hijos: List<Pieza>) : Pieza

/**
 * Un trazo: [d] es un path de SVG. Colores en ARGB (0: sin color). [rellenoAcento] / [lineaAcento]: el color es el acento de la app
 * (el collar, la bufanda). [punta]: 0 recta, 1 redonda, 2 cuadrada. [union]: 0 en pico, 1 redonda, 2 biselada.
 */
class Trazo(
    val d: String, val relleno: Long, val rellenoAcento: Boolean, val linea: Long, val lineaAcento: Boolean,
    val ancho: Float, val punta: Int, val union: Int, val opacidad: Float
) : Pieza

/**
 * Cómo se mueve un grupo en un instante: girado [giro] grados y escalado ([ex], [ey]) alrededor de ([px], [py]) (en unidades del
 * dibujo), desplazado ([dx], [dy]) y con [alfa] (null: el suyo).
 */
class Mov(
    var giro: Float = 0f, var px: Float = 0f, var py: Float = 0f, var ex: Float = 1f, var ey: Float = 1f,
    var dx: Float = 0f, var dy: Float = 0f, var alfa: Float? = null
) {
    val quieto: Boolean get() = giro == 0f && ex == 1f && ey == 1f && dx == 0f && dy == 0f && alfa == null
    override fun toString() = "Mov(giro=$giro, p=($px,$py), e=($ex,$ey), d=($dx,$dy), alfa=$alfa)"
}

/** Un punto del dibujo. */
data class Punto(val x: Float, val y: Float)

/** Los pivotes de cada postura (docs/mau/ANIMACION.md, «Pivotes por postura»). */
internal class Pivotes(
    val cola: Punto?, val cabeza: Punto, val orejaIzq: Punto, val orejaDer: Punto, val cuerpo: Punto = Punto(100f, 214f)
)

internal val PIVOTES: Map<Pose, Pivotes> = mapOf(
    Pose.SENTADO to Pivotes(Punto(138f, 206f), Punto(100f, 112f), Punto(77f, 61f), Punto(123f, 61f)),
    Pose.LEYENDO to Pivotes(Punto(138f, 206f), Punto(100f, 117f), Punto(77f, 66f), Punto(123f, 66f)),
    Pose.ESCRIBIENDO to Pivotes(Punto(138f, 206f), Punto(96.8f, 115.8f), Punto(79.2f, 62.7f), Punto(125f, 67.5f)),
    Pose.VIENDO to Pivotes(Punto(104f, 207f), Punto(100f, 112f), Punto(77f, 61f), Punto(123f, 61f)),
    Pose.CELEBRANDO to Pivotes(Punto(138f, 206f), Punto(102.7f, 108.9f), Punto(75.3f, 60.1f), Punto(121.2f, 56.1f)),
    Pose.TAPANDOSE to Pivotes(Punto(146f, 196f), Punto(100f, 114f), Punto(77f, 63f), Punto(123f, 63f)),
    Pose.DURMIENDO to Pivotes(Punto(163f, 205f), Punto(83.5f, 208.1f), Punto(48.8f, 164.2f), Punto(93.5f, 153f), Punto(104f, 214f)),
    Pose.JUGANDO to Pivotes(Punto(64f, 198f), Punto(95.7f, 151.7f), Punto(80f, 98f), Punto(125.6f, 104.4f)),
    Pose.RONRONEANDO to Pivotes(Punto(138f, 206f), Punto(96.7f, 112.7f), Punto(81f, 59f), Punto(126.6f, 65.4f)),
    Pose.BOSTEZANDO to Pivotes(Punto(138f, 206f), Punto(104.3f, 107.7f), Punto(74.4f, 60.4f), Punto(120f, 54f)),
    Pose.ESCONDIDO to Pivotes(null, Punto(100f, 120f), Punto(77f, 69f), Punto(123f, 69f))
)

/** El dibujo de una postura. */
fun dibujoDe(p: Pose): Dibujo = Dibujos.de(p)

/**
 * Cuánto se baja «sentado» para que su cabeza coincida con la de «escondido» (en unidades del dibujo; ANIMACION.md: «Con "sentado"
 * desplazado +142, su cabeza coincide exactamente con la de "escondido"»).
 */
const val BAJADA_ESCONDER = 142f
