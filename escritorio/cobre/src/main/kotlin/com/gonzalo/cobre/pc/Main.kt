package com.gonzalo.cobre.pc

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.appDeLaFamilia
import com.gonzalo.familia.pc.revista.Skin

fun main(args: Array<String>) {
    val icono = Thread.currentThread().contextClassLoader.getResourceAsStream("icono.png")?.use { it.readBytes() }
        ?.let { runCatching { BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap()) }.getOrNull() }
    appDeLaFamilia("cobre", "Nut", icono, args = args,
        alIniciar = {
            // (sin apariencia tuya todavía, el acento de Cobre, como en el móvil)
            if (Skin.ajustesAt == 0L) Skin.accentId = "cobre"
            Mando.inicia()
            // Cada lista se pone al día si le toca (sin molestar); y, si lo elegiste, el último canal, como una tele
            LaFamilia.alEntrar { Tele.alAbrir() }
            if (LaFamilia.dentro) Tele.alAbrir()
            AlAbrir.ultimoCanal()
        },
        // Al cerrar: lo que se estaba viendo se apunta (antes de subir lo pendiente a la cuenta)
        alCerrar = { Reproductor.alCerrarLaApp(); Mando.para() }) { AppCobre(paginaInicial = 0) }
}

/** «Al abrir Cobre, el último canal que viste» (`ajustes/cobre_directo` → `alAbrir`), como una tele. */
object AlAbrir {
    fun ultimoCanal() {
        Thread({
            runCatching {
                // (la cuenta puede tardar un poco en llegar: se espera a estar dentro)
                val hasta = System.currentTimeMillis() + 15_000
                while (!LaFamilia.dentro && System.currentTimeMillis() < hasta) Thread.sleep(300)
                if (!LaFamilia.dentro || !AjustesDirecto.alAbrir()) return@runCatching
                val u = MiCuenta.ultimo() ?: return@runCatching
                val l = Listas.usables().firstOrNull { it.id == u.lista } ?: return@runCatching
                val cg = Tele.cargada(l) ?: return@runCatching
                val c = cg.porClave[u.canal] ?: return@runCatching
                Reproductor.canal(l, Tele.visibles(cg, c.grupo).ifEmpty { listOf(c) }, c)
            }
        }, "cobre-al-abrir").apply { isDaemon = true; start() }
    }
}
