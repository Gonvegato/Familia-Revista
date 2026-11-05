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

/** Capturas de Marfil del ordenador con una biblioteca de ejemplo. Solo con `-Dcapturas=<carpeta>`. */
class CapturasTest {
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
        listOf("libros", "lectura", "tiempo", "ajustes", "capitulos").forEach { LaFamilia.coleccion(it) }
        check(aqui.sincroniza().ok)
        // Un EPUB de verdad en la biblioteca, por la mitad
        val f = File(tmp, "dune.epub")
        ZipOutputStream(f.outputStream()).use { z ->
            fun pon(n: String, t: String) { z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
            pon("META-INF/container.xml", """<?xml version="1.0"?><container><rootfiles><rootfile full-path="c.opf"/></rootfiles></container>""")
            pon("c.opf", """<?xml version="1.0"?><package><metadata><title>Dune</title><creator>Frank Herbert</creator></metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""")
            val p = (1..14).joinToString("") { "<p>${listOf("Una semana antes de que partieran hacia Arrakis, cuando la actividad de los preparativos había alcanzado una intensidad casi insoportable, una vieja vino a visitar a la madre del muchacho, Paul.", "Era una noche cálida en el castillo de Caladan, y las antiguas piedras que habían servido de hogar a la familia Atreides durante veintiséis generaciones desprendían esa sensación de frescura que precede a los cambios de tiempo.", "La vieja fue admitida por la puerta lateral, a lo largo del pasadizo abovedado, junto a la habitación de Paul, y se le permitió echar un vistazo al muchacho tendido en su cama.")[it % 3]}</p>" }
            pon("c1.xhtml", """<?xml version="1.0"?><html><head><title>1</title></head><body><h1>Libro primero</h1><h2>Dune</h2><blockquote><p>El principio es el momento de tener el cuidado de que el equilibrio sea el correcto.</p></blockquote>$p</body></html>""")
        }
        val id = anadeArchivo(f)
        Libros.leido(id, "epubcfi(/6/2[c1]!/4/2/1:0)", 0.42, "Dune")
        fun captura(nombre: String, p: Pantalla, oscuro: Boolean = false) {
            com.gonzalo.familia.pc.revista.Modo.eleccion = if (oscuro) 2 else 1
            val escena = ImageComposeScene(1440, 900, Density(1f)) { TemaFamilia { TextoAireado { Box(Modifier.fillMaxSize().background(Paper)) { AppMarfil(p) } } } }
            var img = escena.render(0)
            repeat(8) { i -> Thread.sleep(150); img = escena.render((i + 1) * 500_000_000L) }
            File(dir, "$nombre.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            escena.close()
        }
        captura("marfil-portada", Pantalla.Portada)
        captura("marfil-ficha", Pantalla.Ficha(id, Pantalla.Portada))
        captura("marfil-lector", Pantalla.Leer(id, Pantalla.Portada))
        captura("marfil-leidos-oscuro", Pantalla.Lista(Estado.LEIDO), oscuro = true)
        LaFamilia.reiniciaParaPruebas()
    }
}
