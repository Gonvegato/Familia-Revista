package com.gonzalo.mau.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.gonzalo.mau.Dibujo
import com.gonzalo.mau.Grupo
import com.gonzalo.mau.Mov
import com.gonzalo.mau.Pose
import com.gonzalo.mau.Trazo
import com.gonzalo.mau.dibujoDe

/** Un dibujo de Mau con sus paths ya leídos (se arma una vez por postura). */
internal class DibujoListo(val d: Dibujo) {
    sealed interface P
    class G(val id: String?, val clave: String?, val opacidad: Float, val hijos: List<P>) : P
    class T(val t: Trazo, val path: Path, val stroke: Stroke?) : P

    val raiz: G = arma(d.raiz)

    private fun arma(g: Grupo): G = G(
        g.id,
        // contorno-X se mueve como X; contorno, como cuerpo
        g.id.let { id -> when { id == null -> null; id == "contorno" -> "cuerpo"; id.startsWith("contorno-") -> id.removePrefix("contorno-"); else -> id } },
        g.opacidad,
        g.hijos.map { h ->
            when (h) {
                is Grupo -> arma(h)
                is Trazo -> T(
                    h, PathParser().parsePathString(h.d).toPath(),
                    if (h.linea != 0L || h.lineaAcento) Stroke(
                        width = h.ancho,
                        cap = when (h.punta) { 1 -> StrokeCap.Round; 2 -> StrokeCap.Square; else -> StrokeCap.Butt },
                        join = when (h.union) { 1 -> StrokeJoin.Round; 2 -> StrokeJoin.Bevel; else -> StrokeJoin.Miter }
                    ) else null
                )
            }
        }
    )

    companion object {
        private val cache = HashMap<Pose, DibujoListo>()
        fun de(p: Pose): DibujoListo = synchronized(cache) { cache.getOrPut(p) { DibujoListo(dibujoDe(p)) } }
    }
}

/**
 * Pinta Mau en [p] a todo el ancho del lienzo (el alto, en proporción: 220/200; escondido, 80/200), con [movs] (de
 * [com.gonzalo.mau.Animador]) y el collar del color [acento].
 */
internal fun DrawScope.pintaMau(p: Pose, movs: Map<String, Mov>, acento: Color, alfa: Float = 1f, dy: Float = 0f) = pintaMauAncho(size.width, p, movs, acento, alfa, dy)

/** Como [pintaMau], a [ancho] (el lienzo puede estar girado). [dy]: bajado tanto (en unidades del dibujo). */
internal fun DrawScope.pintaMauAncho(ancho: Float, p: Pose, movs: Map<String, Mov>, acento: Color, alfa: Float = 1f, dy: Float = 0f) {
    val dib = DibujoListo.de(p)
    val esc = ancho / dib.d.ancho
    withTransform({ translate(0f, dy * esc); scale(esc, esc, Offset.Zero) }) { pintaGrupo(dib.raiz, movs, acento, alfa) }
}

private fun DrawScope.pintaGrupo(g: DibujoListo.G, movs: Map<String, Mov>, acento: Color, alfa: Float) {
    val m = g.clave?.let { movs[it] }
    val a = alfa * (m?.alfa ?: g.opacidad)
    if (a <= 0.001f) return
    val dibuja: DrawScope.() -> Unit = {
        for (h in g.hijos) when (h) {
            is DibujoListo.G -> pintaGrupo(h, movs, acento, a)
            is DibujoListo.T -> {
                val t = h.t
                val at = a * t.opacidad
                if (t.relleno != 0L || t.rellenoAcento) drawPath(h.path, if (t.rellenoAcento) acento else Color(t.relleno.toInt()), alpha = at)
                if (h.stroke != null) drawPath(h.path, if (t.lineaAcento) acento else Color(t.linea.toInt()), alpha = at, style = h.stroke)
            }
        }
    }
    if (m == null || (m.giro == 0f && m.ex == 1f && m.ey == 1f && m.dx == 0f && m.dy == 0f)) dibuja()
    else withTransform({
        translate(m.dx, m.dy)
        if (m.giro != 0f) rotate(m.giro, Offset(m.px, m.py))
        if (m.ex != 1f || m.ey != 1f) scale(m.ex, m.ey, Offset(m.px, m.py))
    }, dibuja)
}

/** Mau quieto en [pose] (para listas, ajustes, capturas): sin animar. */
@Composable
fun MauQuieto(pose: Pose, acento: Color, modifier: Modifier = Modifier) {
    remember(pose) { DibujoListo.de(pose) }
    Canvas(modifier) { pintaMau(pose, emptyMap(), acento) }
}
