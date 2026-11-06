package com.gonzalo.azabache.pc

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
import com.gonzalo.familia.pc.revista.Apariencia
import com.gonzalo.familia.pc.revista.Paper
import com.gonzalo.familia.pc.revista.TemaFamilia
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Capturas de Azabache del ordenador, pintado de verdad (sin ventana) con una cuenta de ejemplo en el emulador. Solo con
 * `-Dcapturas=<carpeta>`: `./gradlew :escritorio:azabache:test --tests '*Capturas*' -Dcapturas=/tmp/capturas`.
 */
class CapturasTest {
    @Test fun capturas() {
        val dir = System.getProperty("capturas")?.let { File(it) }
        Assume.assumeTrue("Solo con -Dcapturas=<carpeta>", dir != null)
        dir!!.mkdirs()
        Emulador.limpia(); val yo = Emulador.cuenta("gonzalo")
        val otra = Emulador.dispositivo("gonzalo")
        // Tu pareja (Lucía): os tenéis puestos el uno al otro, y lo suyo está en su cuenta
        val ella = Emulador.cuenta("lucia")
        Emulador.escribe("/users/$yo/partner", ella); Emulador.escribe("/users/$ella/partner", yo); Emulador.escribe("/users/$ella/name", "Lucía")
        val hoy = LocalDate.now()
        fun ms(d: LocalDate) = d.atTime(20, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val j = otra.coleccion("juegos"); val l = otra.coleccion("libros"); val t = otra.coleccion("tiempo"); val az = otra.coleccion("azabache")
        j.pon("j_000000000001", mapOf("t" to "Hades", "pl" to "PC", "estado" to "terminado", "nota" to 95L, "crit" to listOf(92L, 88L, 100L, 97L), "fin" to ms(hoy.minusDays(3)), "resena" to "La mejor vuelta al infierno.", "azid" to 1L, "an" to ms(hoy.minusDays(40))))
        j.pon("j_000000000002", mapOf("t" to "Hollow Knight", "pl" to "Switch", "estado" to "en_curso", "an" to ms(hoy.minusDays(10)), "azid" to 2L))
        j.pon("j_000000000003", mapOf("t" to "Outer Wilds", "pl" to "PC", "estado" to "en_curso", "an" to ms(hoy.minusDays(5)), "azid" to 3L))
        j.pon("j_000000000004", mapOf("t" to "Celeste", "pl" to "PC", "estado" to "pendiente", "azid" to 4L))
        j.pon("j_000000000005", mapOf("t" to "The Legend of Zelda: Tears of the Kingdom", "pl" to "Switch", "estado" to "terminado", "fin" to ms(hoy.minusDays(30)), "azid" to 5L))
        j.pon("j_000000000006", mapOf("t" to "Disco Elysium", "pl" to "PC", "estado" to "terminado", "nota" to 91L, "crit" to listOf(80L, 98L, 90L, 85L), "fin" to ms(hoy.minusDays(60)), "azid" to 6L))
        fun libro(t0: String, a: String, estado: String, extra: Map<String, Any?> = emptyMap()) {
            val c = Cuenta.claveLibro(t0, a); l.pon(Cuenta.kidDe(c), mapOf("clave" to c, "t" to t0, "a" to a, "estado" to estado) + extra)
        }
        libro("Dune", "Frank Herbert", "en_curso", mapOf("fr" to 0.42, "pg" to 712L, "azid" to 11L))
        libro("El nombre del viento", "Patrick Rothfuss", "terminado", mapOf("fin" to listOf(ms(hoy.minusDays(1))), "azid" to 12L))
        libro("Rayuela", "Julio Cortázar", "terminado", mapOf("fin" to listOf(ms(hoy.minusDays(20))), "nota" to 88L, "crit" to listOf(85L, 80L, 95L, 70L), "azid" to 13L))
        libro("El temor de un hombre sabio", "Patrick Rothfuss", "pendiente", mapOf("azid" to 14L))
        t.pon("j_000000000002~hiedra-1", mapOf("ref" to "j_000000000002", "fuente" to "hiedra-1", "total" to 9_000_000L, "d$hoy" to 5_400_000L, "d${hoy.minusDays(1)}" to 3_600_000L))
        t.pon("${Cuenta.kidDe(Cuenta.claveLibro("Dune", "Frank Herbert"))}~marfil-1", mapOf("ref" to Cuenta.kidDe(Cuenta.claveLibro("Dune", "Frank Herbert")), "fuente" to "marfil-1", "total" to 4_200_000L, "d$hoy" to 2_400_000L))
        az.pon("kinds~2", mapOf("o" to """{"id":2,"title":"Películas","criteria":["Guion","Actuación","Dirección","Música"],"progress":"Viendo","color":0,"source":"movie"}""", "b" to 0L))
        az.pon("items_2~603", mapOf("o" to """{"id":603,"name":"Matrix","cover":"","general":90,"gfx":85,"story":88,"music":80,"gameplay":92,"review":"","hero":"","start":0,"end":${hoy.minusDays(7).toEpochDay()},"creator":"Lana y Lilly Wachowski"}""", "b" to 0L))
        az.pon("diario~1", mapOf("o" to """{"id":1,"dia":${hoy.toEpochDay()},"x":"Por fin el Palacio de la Reina Blanca. Qué música.","sobre":["0:2"],"updated":1}""", "b" to 0L))
        az.pon("sagas~7", mapOf("o" to """{"id":7,"name":"Crónica del asesino de reyes","kind":1,"items":[12,14],"updated":2}""", "b" to 0L))
        otra.coleccion("ajustes").pon("lectura", mapOf("dia" to 30L, "ano" to 24L, "anio" to hoy.year.toLong()))
        // Lo jugado de Hades y de Hollow Knight, día a día (sus «Sesiones»), y una maqueta hecha en el móvil para Disco Elysium
        t.pon("j_000000000001~hiedra-1", mapOf<String, Any?>("ref" to "j_000000000001", "fuente" to "hiedra-1", "total" to 50_400_000L) +
            listOf(40L to 90, 38L to 150, 33L to 60, 30L to 200, 26L to 45, 21L to 120, 15L to 80, 12L to 30, 8L to 140, 4L to 95).associate { (n, m) -> "d${hoy.minusDays(n)}" to m * 60_000L })
        t.pon("j_000000000002~hiedra-1", mapOf("ref" to "j_000000000002", "fuente" to "hiedra-1", "d${hoy.minusDays(3)}" to 2_700_000L, "d${hoy.minusDays(6)}" to 4_000_000L, "d${hoy.minusDays(9)}" to 1_800_000L))
        val maqueta = """[{"s":0.6,"a":0,"c":[{"t":"texto","h":"Mi veredicto","x":"El mejor juego de rol que he jugado: se lee como una novela y se juega como una conversación."},{"t":"nota"}]},{"s":0.5,"a":0,"c":[{"t":"datos"},{"t":"enlaces"}]}]"""
        az.pon("maquetas~42", mapOf("o" to com.gonzalo.familia.nucleo.Json.escribe(mapOf("id" to 42L, "k" to "v:0:6", "j" to maqueta, "updated" to 3L)), "b" to 0L))
        check(otra.sincroniza().ok)
        val lucia = Emulador.dispositivo("lucia")
        lucia.coleccion("juegos").pon("j_0000000000a1", mapOf("t" to "Hades", "pl" to "Switch", "estado" to "terminado", "nota" to 88L, "crit" to listOf(90L, 80L, 95L, 85L), "fin" to ms(hoy.minusDays(12)), "resena" to "Adictivo. La música, lo mejor.", "azid" to 101L))
        lucia.coleccion("juegos").pon("j_0000000000a2", mapOf("t" to "Disco Elysium", "pl" to "PC", "estado" to "terminado", "nota" to 70L, "crit" to listOf(60L, 85L, 75L, 55L), "fin" to ms(hoy.minusDays(80)), "azid" to 102L))
        lucia.coleccion("juegos").pon("j_0000000000a3", mapOf("t" to "Animal Crossing", "pl" to "Switch", "estado" to "en_curso", "an" to ms(hoy.minusDays(20)), "azid" to 103L))
        fun libroDe(t0: String, a: String, extra: Map<String, Any?>) { val c = Cuenta.claveLibro(t0, a); lucia.coleccion("libros").pon(Cuenta.kidDe(c), mapOf("clave" to c, "t" to t0, "a" to a) + extra) }
        libroDe("Rayuela", "Julio Cortázar", mapOf("estado" to "terminado", "fin" to listOf(ms(hoy.minusDays(40))), "nota" to 92L, "crit" to listOf(90L, 85L, 98L, 80L), "azid" to 111L))
        libroDe("La sombra del viento", "Carlos Ruiz Zafón", mapOf("estado" to "en_curso", "fr" to 0.6, "pg" to 576L, "azid" to 112L))
        check(lucia.sincroniza().ok)

        LaFamilia.reiniciaParaPruebas()
        LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg
        LaFamilia.dirPruebas = File(System.getProperty("java.io.tmpdir"), "azabache-capturas-" + System.nanoTime())
        LaFamilia.llaveroPruebas = LlaveroMemoria()
        val aqui = LaFamilia.inicia("azabache").also { it.automatico = false }
        LaFamilia.entrar("gonzalo", "secreto1")
        Cuenta.olvidaParaPruebas()
        listOf("juegos", "libros", "tiempo", "azabache", "ajustes").forEach { LaFamilia.coleccion(it) }
        check(aqui.sincroniza().ok)
        // Lo de tu pareja: su uid y sus colecciones (de su cuenta, solo para leer)
        check(Cuenta.uidPareja() == ella) { "no se encuentra a la pareja" }
        listOf("juegos", "libros", "tiempo", "azabache").forEach { LaFamilia.ajena(ella, it) }
        check(aqui.sincroniza().ok)
        Apariencia.inicia("azabache")

        fun captura(nombre: String, p: Pantalla, oscuro: Boolean = false) {
            com.gonzalo.familia.pc.revista.Modo.eleccion = if (oscuro) 2 else 1
            val escena = ImageComposeScene(1440, 900, Density(1f)) {
                TemaFamilia { TextoAireado { Box(Modifier.fillMaxSize().background(Paper)) { AppAzabache(p) } } }
            }
            var img = escena.render(0)
            repeat(6) { i -> Thread.sleep(150); img = escena.render((i + 1) * 500_000_000L) }
            File(dir, "$nombre.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            escena.close()
        }
        captura("azabache-portada", Pantalla.Inicio)
        captura("azabache-juegos", Pantalla.Lista(GAMES.id))
        captura("azabache-libros-oscuro", Pantalla.Lista(BOOKS.id), oscuro = true)
        ListaPrefs.indice = true; captura("azabache-juegos-indice", Pantalla.Lista(GAMES.id)); ListaPrefs.indice = false
        captura("azabache-ficha", Pantalla.Detalle(GAMES.id, 1L, Pantalla.Lista(GAMES.id)))
        captura("azabache-ficha-sin-valorar", Pantalla.Detalle(BOOKS.id, 12L, Pantalla.Lista(BOOKS.id)))
        captura("azabache-ficha-jugando", Pantalla.Detalle(GAMES.id, 2L, Pantalla.Lista(GAMES.id)))
        captura("azabache-libro-leyendo", Pantalla.Detalle(BOOKS.id, 11L, Pantalla.Lista(BOOKS.id)))
        captura("azabache-ficha-maqueta", Pantalla.Detalle(GAMES.id, 6L, Pantalla.Lista(GAMES.id)))
        captura("azabache-ficha-oscuro", Pantalla.Detalle(GAMES.id, 1L, Pantalla.Lista(GAMES.id)), oscuro = true)
        captura("azabache-pareja", Pantalla.Pareja)
        captura("azabache-pareja-ficha", Pantalla.DeLaPareja(ella, GAMES.id, 101L))
        captura("azabache-diario", Pantalla.Diario)
        captura("azabache-sagas", Pantalla.Sagas)
        captura("azabache-cifras", Pantalla.Cifras)
        captura("azabache-ajustes", Pantalla.Ajustes)
        LaFamilia.reiniciaParaPruebas()
    }
}
