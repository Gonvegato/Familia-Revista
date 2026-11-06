package com.gonzalo.marfil.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import com.gonzalo.familia.pc.TextoAireado
import com.gonzalo.familia.pc.revista.Paper
import com.gonzalo.familia.pc.revista.TemaFamilia
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Capturas de Marfil del ordenador con una biblioteca de ejemplo (y el lector con subrayados, notas, paneles y temas). Solo con `-Dcapturas=<carpeta>`. */
class CapturasTest {
    /** Un libro de ejemplo (texto propio): tres capítulos, una cita, una nota al pie y un índice. */
    private fun libro(f: File) {
        val p = listOf(
            "El faro se alzaba sobre la última roca del cabo, tan blanco que de lejos parecía una vela olvidada en mitad del cielo. Nadie en el pueblo recordaba quién lo había encendido por primera vez, pero todos sabían a qué hora se apagaba.",
            "Elena llegó con la marea baja, cuando el camino de piedras quedaba al descubierto y se podía cruzar sin mojarse los tobillos. Llevaba una maleta de cuero, un cuaderno de tapas verdes y la carta del ayuntamiento doblada en cuatro.",
            "El antiguo farero la esperaba en la puerta, con las manos en los bolsillos y la gorra calada hasta las cejas. No le preguntó por el viaje ni por el tiempo; le enseñó la escalera, ciento doce peldaños, y le dijo que la contara cada noche.",
            "—Si un día te salen ciento trece —le advirtió—, baja despacio y no mires atrás.",
            "Las primeras semanas fueron de aprendizaje y de silencio. Elena limpiaba los cristales de la linterna, engrasaba el mecanismo de relojería y anotaba en el cuaderno la dirección del viento, el color del agua y los barcos que pasaban a lo lejos.",
            "Por las tardes bajaba al pueblo a comprar pan y queso, y en la tienda le hablaban de la tormenta del cuarenta y siete, de los marineros que se salvaron gracias a la luz y de los que no volvieron nunca, aunque la luz estuviera encendida.",
            "Aprendió que el mar tiene memoria: devuelve lo que se le lleva, pero a su manera y cuando quiere. Una mañana encontró en la orilla una botella con un mensaje que nadie había escrito todavía, solo una fecha y un nombre que era el suyo.",
            "Guardó la botella en el cajón de la mesa, junto al cuaderno, y no se lo contó a nadie. Esa noche contó los peldaños dos veces, y las dos veces le salieron ciento doce.")
        val c1 = "<h1>Primera parte</h1><h2 id=\"llegada\">I. La llegada</h2><blockquote><p>Quien guarda la luz no duerme; solo cierra los ojos para ver mejor el mar.</p></blockquote>" +
            (0 until 16).joinToString("") { i -> if (i == 2) "<p>${p[i % p.size]}<a epub:type=\"noteref\" href=\"#n1\"><sup>1</sup></a></p>" else "<p>${p[i % p.size]}</p>" } +
            "<aside epub:type=\"footnote\" id=\"n1\"><p>Los faros de la costa norte se apagaban al amanecer, cuando el primer barco de pesca dejaba atrás la bocana.</p></aside>"
        val c2 = "<h2>II. El cuaderno del farero</h2>" + (0 until 16).joinToString("") { "<p>${p[(it + 3) % p.size]}</p>" } + "<hr/>" + (0 until 4).joinToString("") { "<p>${p[(it + 5) % p.size]}</p>" }
        val c3 = "<h2>III. La tormenta</h2>" + (0 until 18).joinToString("") { "<p>${p[(it + 1) % p.size]}</p>" }
        ZipOutputStream(f.outputStream()).use { z ->
            fun pon(n: String, t: String) { z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
            fun cap(t: String, cuerpo: String) = """<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>$t</title></head><body>$cuerpo</body></html>"""
            pon("META-INF/container.xml", """<?xml version="1.0"?><container><rootfiles><rootfile full-path="c.opf"/></rootfiles></container>""")
            pon("c.opf", """<?xml version="1.0"?><package><metadata><title>El faro de las mareas</title><creator>Lucía Arenal</creator></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/><item id="c3" href="c3.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/><itemref idref="c2"/><itemref idref="c3"/></spine></package>""")
            pon("nav.xhtml", """<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="c1.xhtml">Primera parte</a><ol><li><a href="c1.xhtml#llegada">I. La llegada</a></li></ol></li><li><a href="c2.xhtml">II. El cuaderno del farero</a></li><li><a href="c3.xhtml">III. La tormenta</a></li></ol></nav></body></html>""")
            pon("c1.xhtml", cap("I", c1)); pon("c2.xhtml", cap("II", c2)); pon("c3.xhtml", cap("III", c3))
        }
    }

    @Test fun capturas() {
        val dir = System.getProperty("capturas")?.let { File(it) }
        Assume.assumeTrue("Solo con -Dcapturas=<carpeta>", dir != null)
        dir!!.mkdirs()
        Emulador.limpia(); Emulador.cuenta("gonzalo")
        val otra = Emulador.dispositivo("gonzalo")
        val tmp = File(System.getProperty("java.io.tmpdir"), "marfil-capturas-" + System.nanoTime()).also { it.mkdirs() }
        fun libro(t: String, a: String, estado: String, extra: Map<String, Any?> = emptyMap()) { val c = Libros.clave(t, a); otra.coleccion("libros").pon(Libros.kid(c), mapOf("clave" to c, "t" to t, "a" to a, "estado" to estado, "an" to System.currentTimeMillis()) + extra) }
        libro("El nombre del viento", "Patrick Rothfuss", "terminado", mapOf("fin" to listOf(System.currentTimeMillis() - 86_400_000L), "nota" to 92L))
        libro("Rayuela", "Julio Cortázar", "terminado", mapOf("fin" to listOf(System.currentTimeMillis() - 20 * 86_400_000L)))
        libro("El temor de un hombre sabio", "Patrick Rothfuss", "pendiente")
        libro("La mano izquierda de la oscuridad", "Ursula K. Le Guin", "en_curso", mapOf("fr" to 0.18, "ul" to System.currentTimeMillis() - 3 * 86_400_000L))
        otra.coleccion("ajustes").pon("lectura", mapOf("dia" to 30L, "ano" to 24L, "anio" to java.time.LocalDate.now().year.toLong()))
        check(otra.sincroniza().ok)
        LaFamilia.reiniciaParaPruebas(); LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg; LaFamilia.dirPruebas = tmp; LaFamilia.llaveroPruebas = LlaveroMemoria()
        val aqui = LaFamilia.inicia("marfil").also { it.automatico = false }
        LaFamilia.entrar("gonzalo", "secreto1")
        Libros.dirPruebas = File(tmp, "marfil"); Libros.dispositivo = { "marfil-pc1" }
        listOf("libros", "lectura", "tiempo", "ajustes", "capitulos", "marcas").forEach { LaFamilia.coleccion(it) }
        check(aqui.sincroniza().ok)
        // Un EPUB de verdad en la biblioteca, empezado, con lo subrayado en el móvil y aquí
        val f = File(tmp, "faro.epub")
        libro(f)
        val id = anadeArchivo(f)
        Libros.sumaTiempo(id, 47 * 60_000L)
        Epub(f).use { e ->
            val bs = e.bloques(0)
            val b = bs.first { it.texto.text.startsWith("Elena llegó") }
            Libros.leido(id, e.cfiDe(bs[0], 0), 0.04, "I. La llegada")
            Marcas.subraya(id, e.cfiTramo(b, 0, b, b.texto.text.indexOf(",") )!!, b.texto.text.substring(0, b.texto.text.indexOf(",")), 0, "I. La llegada", 0.05, nota = "Como en el cuento de la abuela: siempre se llega con la marea.")
            val b2 = bs.first { it.texto.text.startsWith("Las primeras semanas") }
            val t2 = "anotaba en el cuaderno la dirección del viento, el color del agua y los barcos que pasaban a lo lejos."
            val i2 = b2.texto.text.indexOf(t2)
            Marcas.subraya(id, e.cfiTramo(b2, i2, b2, i2 + t2.length)!!, t2, 0, "I. La llegada", 0.07)
            val b3 = e.bloques(2)[3]
            Marcas.marca(id, e.cfiDe(b3, 0), "III. La tormenta", 0.7, 2)
        }
        AjustesLectura.guarda(id, AjustesLectura.de(id).con("speed", 1450L))
        check(aqui.sincroniza().ok)
        fun captura(nombre: String, p: Pantalla, oscuro: Boolean = false, w: Int = 1440, h: Int = 900, vueltas: Int = 10) {
            com.gonzalo.familia.pc.revista.Modo.eleccion = if (oscuro) 2 else 1
            val escena = ImageComposeScene(w, h, Density(1f)) { TemaFamilia { TextoAireado { Box(Modifier.fillMaxSize().background(Paper)) { AppMarfil(p) } } } }
            var img = escena.render(0)
            repeat(vueltas) { i -> Thread.sleep(150); img = escena.render((i + 1) * 500_000_000L) }
            File(dir, "$nombre.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            escena.close()
        }
        fun ajustes(vararg kv: Pair<String, Any?>) { var a = AjustesLectura.de(id); kv.forEach { (k, v) -> a = a.con(k, v) }; AjustesLectura.guarda(id, a) }
        captura("marfil-portada", Pantalla.Portada)
        captura("marfil-ficha", Pantalla.Ficha(id, Pantalla.Portada))
        val leer = Pantalla.Leer(id, Pantalla.Portada)
        captura("marfil-lector", leer, vueltas = 22)
        ajustes("columns" to true, "theme" to "papel")
        captura("marfil-lector-dos-paginas", leer, vueltas = 22)
        captura("marfil-lector-indice", Pantalla.Leer(id, Pantalla.Portada, panel = Panel.INDICE), vueltas = 22)
        captura("marfil-lector-notas", Pantalla.Leer(id, Pantalla.Portada, panel = Panel.NOTAS), vueltas = 22)
        captura("marfil-lector-buscar", Pantalla.Leer(id, Pantalla.Portada, panel = Panel.BUSCAR, busqueda = "marea"), vueltas = 22)
        ajustes("theme" to "sepia", "font" to "garamond", "size" to 110L)
        captura("marfil-lector-sepia", leer, vueltas = 22)
        captura("marfil-lector-letra", Pantalla.Leer(id, Pantalla.Portada, panel = Panel.LETRA), vueltas = 22)
        ajustes("theme" to "noche", "font" to "literata", "size" to 100L, "columns" to false)
        captura("marfil-lector-noche", leer, vueltas = 22)
        ajustes("theme" to "app", "flow" to "scrolled")
        captura("marfil-lector-continuo", leer, vueltas = 22)
        ajustes("flow" to "paginated", "columns" to true)
        captura("marfil-lector-mac", leer, w = 1280, h = 800, vueltas = 22)
        captura("marfil-leidos-oscuro", Pantalla.Lista(Estado.LEIDO), oscuro = true)
        LaFamilia.reiniciaParaPruebas()
    }
}
