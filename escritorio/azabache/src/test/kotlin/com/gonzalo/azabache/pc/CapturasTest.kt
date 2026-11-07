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
        // Leído un día como hoy, hace tres años («Tal día como hoy»)
        libro("Fundación", "Isaac Asimov", "terminado", mapOf("fin" to listOf(ms(hoy.minusYears(3))), "nota" to 85L, "crit" to listOf(80L, 90L, 85L, 75L), "azid" to 15L))
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
        // Lo que te recomienda Lucía (desde su móvil)
        lucia.coleccion("azabache").pon("recs~5", mapOf("o" to """{"id":5,"kind":1,"item":112,"name":"La sombra del viento","cover":"","creator":"Carlos Ruiz Zafón","note":"Barcelona, los libros olvidados y un misterio que no te suelta. Léelo en otoño.","time":${ms(hoy.minusDays(1))}}""", "b" to 0L))
        lucia.coleccion("azabache").pon("recs~6", mapOf("o" to """{"id":6,"kind":0,"item":103,"name":"Animal Crossing","cover":"","creator":"Nintendo","note":"","time":${ms(hoy.minusDays(4))}}""", "b" to 0L))
        check(lucia.sincroniza().ok)
        // Lo tuyo: una recomendación que le hiciste
        az.pon("recs~9", mapOf("o" to """{"id":9,"kind":0,"item":1,"name":"Hades","cover":"","creator":"Supergiant Games","note":"La música te va a encantar","time":${ms(hoy.minusDays(6))}}""", "b" to 0L))
        check(otra.sincroniza().ok)

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

        Local.dirPruebas = File(LaFamilia.dirPruebas, "local"); Local.olvidaParaPruebas()
        /** [haz]: lo que se hace con el ratón antes de la foto (un clic, desplazar), con el tiempo de la escena. */
        fun captura(nombre: String, p: Pantalla, oscuro: Boolean = false, haz: (ImageComposeScene, Long) -> Unit = { _, _ -> }) {
            com.gonzalo.familia.pc.revista.Modo.eleccion = if (oscuro) 2 else 1
            val escena = ImageComposeScene(1440, 900, Density(1f)) {
                TemaFamilia { TextoAireado { Box(Modifier.fillMaxSize().background(Paper)) { AppAzabache(p) } } }
            }
            var img = escena.render(0)
            repeat(6) { i -> Thread.sleep(150); img = escena.render((i + 1) * 500_000_000L) }
            haz(escena, 3_500_000_000L)
            repeat(5) { i -> Thread.sleep(150); img = escena.render(4_000_000_000L + i * 500_000_000L) }
            File(dir, "$nombre.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            escena.close()
        }
        fun clic(x: Float, y: Float): (ImageComposeScene, Long) -> Unit = { e, t ->
            e.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, androidx.compose.ui.geometry.Offset(x, y), timeMillis = t / 1_000_000)
            e.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, androidx.compose.ui.geometry.Offset(x, y), timeMillis = t / 1_000_000 + 10, button = androidx.compose.ui.input.pointer.PointerButton.Primary)
            e.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, androidx.compose.ui.geometry.Offset(x, y), timeMillis = t / 1_000_000 + 60, button = androidx.compose.ui.input.pointer.PointerButton.Primary)
        }
        fun baja(x: Float, y: Float, cuanto: Float): (ImageComposeScene, Long) -> Unit = { e, t ->
            e.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Scroll, androidx.compose.ui.geometry.Offset(x, y), scrollDelta = androidx.compose.ui.geometry.Offset(0f, cuanto), timeMillis = t / 1_000_000)
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
        // 1.2.0: la portada con lo que te recomienda tu pareja, diseñar la página, buscar en todo
        captura("azabache-portada-oscuro", Pantalla.Inicio, oscuro = true)
        captura("azabache-portada-recomienda", Pantalla.Inicio, haz = baja(800f, 500f, 30f))
        captura("azabache-portada-recomienda-oscuro", Pantalla.Inicio, oscuro = true, haz = baja(800f, 500f, 30f))
        captura("azabache-pareja-recomendaciones", Pantalla.Pareja, haz = baja(800f, 500f, 30f))
        captura("azabache-buscar-en-todo", Pantalla.Inicio, haz = clic(340f, 42f))
        captura("azabache-buscar-en-todo-oscuro", Pantalla.Inicio, oscuro = true, haz = clic(340f, 42f))
        captura("azabache-disenar", Pantalla.Detalle(GAMES.id, 6L, Pantalla.Lista(GAMES.id), disenar = true))
        captura("azabache-disenar-oscuro", Pantalla.Detalle(GAMES.id, 6L, Pantalla.Lista(GAMES.id), disenar = true), oscuro = true)
        captura("azabache-disenar-texto", Pantalla.Detalle(GAMES.id, 6L, Pantalla.Lista(GAMES.id), disenar = true), haz = clic(480f, 720f))
        captura("azabache-disenar-casilla-oscuro", Pantalla.Detalle(GAMES.id, 1L, Pantalla.Lista(GAMES.id), disenar = true), oscuro = true, haz = clic(800f, 240f))
        captura("azabache-disenar-jugando", Pantalla.Detalle(GAMES.id, 2L, Pantalla.Lista(GAMES.id), disenar = true), haz = clic(480f, 240f))
        LaFamilia.reiniciaParaPruebas(); Local.dirPruebas = null; Local.olvidaParaPruebas()
    }

    /** Un póster de mentira (formas y colores, sin letras), como JPEG de 600×900. */
    private fun poster(fondo: Int, forma: Int, otra: Int, semilla: Int): ByteArray {
        val s = org.jetbrains.skia.Surface.makeRasterN32Premul(600, 900)
        val c = s.canvas
        c.clear(fondo)
        val p = org.jetbrains.skia.Paint().apply { isAntiAlias = true }
        p.color = forma
        when (semilla % 3) {
            0 -> c.drawCircle(300f, 380f, 210f, p)
            1 -> c.drawRect(org.jetbrains.skia.Rect.makeXYWH(90f, 140f, 420f, 520f), p)
            else -> { c.drawCircle(220f, 330f, 150f, p); c.drawCircle(400f, 470f, 120f, p) }
        }
        p.color = otra
        for (i in 0 until 4) c.drawRect(org.jetbrains.skia.Rect.makeXYWH(0f, 700f + i * 34f, 600f, 14f), p)
        c.drawCircle(120f + (semilla * 97 % 360), 160f, 36f, p)
        return s.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 90)!!.bytes
    }

    /**
     * Desde la 1.2.1, las películas y las series son las de Mostaza (`titulos`, `progreso`, `tiempo`): «Películas», «Series», la ficha de
     * una película vista y la de una serie a medias (con lo visto en Mostaza), en claro y en oscuro. Solo con `-Dcapturas=<carpeta>`.
     */
    @Test fun capturasMostaza() {
        val dir = System.getProperty("capturas")?.let { File(it) }
        Assume.assumeTrue("Solo con -Dcapturas=<carpeta>", dir != null)
        dir!!.mkdirs()
        Emulador.limpia(); Emulador.cuenta("gonzalo")
        val otra = Emulador.dispositivo("gonzalo")
        val hoy = LocalDate.now()
        fun ms(d: LocalDate) = d.atTime(21, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val az = otra.coleccion("azabache"); val tit = otra.coleccion("titulos"); val t = otra.coleccion("tiempo"); val pr = otra.coleccion("progreso")
        listOf("juegos", "libros", "ajustes").forEach { otra.coleccion(it) }
        az.pon("kinds~2", mapOf("o" to """{"id":2,"title":"Películas","criteria":["Guion","Actuación","Dirección","Música"],"progress":"Viendo","color":0,"source":"movie"}""", "b" to 0L))
        az.pon("kinds~3", mapOf("o" to """{"id":3,"title":"Series","criteria":["Guion","Personajes","Actuación","Ritmo"],"progress":"Viendo","color":0,"source":"tv"}""", "b" to 0L))
        // Lo de antes (se queda como copia, y ya no se lee)
        az.pon("items_2~999", mapOf("o" to """{"id":999,"name":"Solo en lo de antes","cover":"","general":10,"gfx":10,"story":10,"music":10,"gameplay":10,"review":"","hero":"","start":0,"end":0}""", "b" to 0L))
        var n = 0
        fun img(fondo: Long, forma: Long, otro: Long) = otra.imagenes.guarda(poster(fondo.toInt(), forma.toInt(), otro.toInt(), n++), "image/jpeg")
        fun titulo(ref: String, campos: Map<String, Any?>) = tit.pon(ref, mapOf("id" to ref, "imdb" to ref) + campos)
        titulo("tt0111161", mapOf("tipo" to "movie", "t" to "Cadena perpetua", "ano" to "1994", "estado" to "terminado", "nota" to 92L, "crit" to listOf(90L, 95L, 85L, 80L),
            "resena" to "La esperanza es algo bueno, quizá lo mejor de todo. Morgan Freeman narra y la cárcel se vuelve un sitio donde se puede vivir.",
            "fin" to ms(hoy.minusDays(2)), "an" to ms(hoy.minusDays(30)), "azid" to 278L, "azk" to 2L, "tmdb" to 278L, "portada" to img(0xFF2F4B7C, 0xFFE8C872, 0xFF1B2A44),
            "az" to """{"creator":"Frank Darabont","platform":"Filmin"}"""))
        titulo("tt0137523", mapOf("tipo" to "movie", "t" to "El club de la lucha", "ano" to "1999", "estado" to "terminado", "nota" to 85L, "crit" to listOf(80L, 90L, 70L, 88L),
            "fin" to ms(hoy.minusDays(40)), "azid" to 550L, "azk" to 2L, "tmdb" to 550L, "portada" to img(0xFFB23A48, 0xFFF4E3D7, 0xFF3B0D11), "az" to """{"creator":"David Fincher","platform":"Netflix"}"""))
        titulo("tt0133093", mapOf("tipo" to "movie", "t" to "Matrix", "ano" to "1999", "estado" to "terminado", "fin" to ms(hoy.minusDays(5)), "tmdb" to 603L, "portada" to img(0xFF0E3B2E, 0xFF5EDB8C, 0xFF04140F)))
        titulo("tt6751668", mapOf("tipo" to "movie", "t" to "Parásitos", "ano" to "2019", "estado" to "en_curso", "tmdb" to 496243L, "an" to ms(hoy.minusDays(1)), "portada" to img(0xFF56483A, 0xFFE0D6C2, 0xFF2A221B)))
        titulo("tt0068646", mapOf("tipo" to "movie", "t" to "El padrino", "ano" to "1972", "estado" to "pendiente", "tmdb" to 238L))
        titulo("tt1375666", mapOf("tipo" to "movie", "t" to "Origen", "ano" to "2010", "estado" to "abandonado", "tmdb" to 27205L, "portada" to img(0xFF3C4F76, 0xFFD1D5DE, 0xFF161D2D)))
        titulo("tt0944947", mapOf("tipo" to "series", "t" to "Juego de tronos", "ano" to "2011–2019", "estado" to "en_curso", "tvmaze" to 82L, "azid" to 82L, "azk" to 3L, "an" to ms(hoy.minusDays(9)),
            "portada" to img(0xFF4A4E5A, 0xFFC9A227, 0xFF1E2027),
            "az" to """{"creator":"David Benioff y D. B. Weiss","platform":"HBO Max","parts":[{"n":"1×01 · Se acerca el invierno","d":true},{"n":"1×02 · El camino real","d":false},{"n":"1×03 · Lord Nieve","d":false},{"n":"1×04 · Tullidos, bastardos y cosas rotas","d":false},{"n":"1×05 · El lobo y el león","d":false},{"n":"1×06 · Una corona de oro","d":false},{"n":"1×07 · Ganas o mueres","d":false},{"n":"1×08 · Por el lado de la punta","d":false}]}"""))
        listOf(1, 2, 3).forEach { e -> pr.pon("tt0944947:1:$e", mapOf("ref" to "tt0944947", "v" to "tt0944947:1:$e", "temp" to 1L, "ep" to e.toLong(), "visto" to true, "cuando" to ms(hoy.minusDays(4L - e)))) }
        pr.pon("tt0944947:1:4", mapOf("ref" to "tt0944947", "v" to "tt0944947:1:4", "temp" to 1L, "ep" to 4L, "pos" to 1_200_000L, "dur" to 3_300_000L, "cuando" to ms(hoy)))
        pr.pon("tt6751668", mapOf("ref" to "tt6751668", "v" to "tt6751668", "pos" to 3_120_000L, "dur" to 7_920_000L, "cuando" to ms(hoy)))
        titulo("tt0903747", mapOf("tipo" to "series", "t" to "Breaking Bad", "ano" to "2008–2013", "estado" to "terminado", "nota" to 97L, "crit" to listOf(98L, 99L, 97L, 90L), "fin" to ms(hoy.minusDays(100)),
            "tvmaze" to 169L, "azid" to 169L, "azk" to 3L, "portada" to img(0xFF3E6B48, 0xFFF2D16B, 0xFF16261A), "resena" to "Lo mejor que he visto en una tele."))
        titulo("tt2861424", mapOf("tipo" to "series", "t" to "Rick y Morty", "ano" to "2013–", "estado" to "en_curso", "tvmaze" to 216L, "an" to ms(hoy.minusDays(3)), "portada" to img(0xFF2E8B9A, 0xFFB9F18D, 0xFF0D2E33)))
        listOf(1, 2, 3, 4, 5).forEach { e -> pr.pon("tt2861424:1:$e", mapOf("ref" to "tt2861424", "v" to "tt2861424:1:$e", "temp" to 1L, "ep" to e.toLong(), "visto" to true, "cuando" to ms(hoy.minusDays(3)))) }
        titulo("tt0386676", mapOf("tipo" to "series", "t" to "The Office", "ano" to "2005–2013", "estado" to "pendiente", "tvmaze" to 526L))
        // Lo visto, por día y dispositivo
        t.pon("tt0111161~mostaza-tele", mapOf("ref" to "tt0111161", "fuente" to "mostaza-tele", "t" to "Cadena perpetua", "total" to 142 * 60_000L, "d${hoy.minusDays(2)}" to 142 * 60_000L))
        t.pon("tt0944947~mostaza-tele", mapOf("ref" to "tt0944947", "fuente" to "mostaza-tele", "t" to "Juego de tronos", "total" to 190 * 60_000L,
            "d${hoy.minusDays(3)}" to 60 * 60_000L, "d${hoy.minusDays(2)}" to 58 * 60_000L, "d${hoy.minusDays(1)}" to 52 * 60_000L, "d$hoy" to 20 * 60_000L))
        t.pon("tt6751668~mostaza-movil", mapOf("ref" to "tt6751668", "fuente" to "mostaza-movil", "t" to "Parásitos", "total" to 52 * 60_000L, "d$hoy" to 52 * 60_000L))
        // El diario y una saga siguen encontrando las fichas por su número de siempre
        az.pon("diario~1", mapOf("o" to """{"id":1,"dia":${hoy.minusDays(2).toEpochDay()},"x":"Cadena perpetua otra vez. Sigue siendo perfecta.","sobre":["2:278"],"updated":1}""", "b" to 0L))
        az.pon("sagas~8", mapOf("o" to """{"id":8,"name":"Las de Fincher","kind":2,"items":[550],"updated":2}""", "b" to 0L))
        az.pon(CosasMostaza.MARCA, mapOf("v" to "[1]", "por" to "mostaza", "cuantas" to 3L))
        check(otra.sincroniza().ok)

        LaFamilia.reiniciaParaPruebas()
        LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg
        LaFamilia.dirPruebas = File(System.getProperty("java.io.tmpdir"), "azabache-capturas-mostaza-" + System.nanoTime())
        LaFamilia.llaveroPruebas = LlaveroMemoria()
        val aqui = LaFamilia.inicia("azabache").also { it.automatico = false }
        LaFamilia.entrar("gonzalo", "secreto1")
        Cuenta.olvidaParaPruebas(); CosasMostaza.olvidaParaPruebas()
        listOf("juegos", "libros", "tiempo", "azabache", "ajustes", "titulos", "progreso").forEach { LaFamilia.coleccion(it) }
        check(aqui.sincroniza().ok)
        check(CosasMostaza.activa()) { "sin la marca de Hathor" }
        check(Cuenta.fichas(2).none { it.nombre == "Solo en lo de antes" }) { "se lee lo de antes" }
        Apariencia.inicia("azabache")
        Local.dirPruebas = File(LaFamilia.dirPruebas, "local"); Local.olvidaParaPruebas()
        // Las portadas, ya en este ordenador (las trae la cuenta)
        (Cuenta.fichas(2) + Cuenta.fichas(3)).forEach { f -> if (f.portada.isNotBlank()) com.gonzalo.familia.pc.Portadas.trae(f.portada) }

        fun captura(nombre: String, p: Pantalla, oscuro: Boolean = false, haz: (ImageComposeScene, Long) -> Unit = { _, _ -> }) {
            com.gonzalo.familia.pc.revista.Modo.eleccion = if (oscuro) 2 else 1
            val escena = ImageComposeScene(1440, 900, Density(1f)) {
                TemaFamilia { TextoAireado { Box(Modifier.fillMaxSize().background(Paper)) { AppAzabache(p) } } }
            }
            var img = escena.render(0)
            repeat(6) { i -> Thread.sleep(150); img = escena.render((i + 1) * 500_000_000L) }
            haz(escena, 3_500_000_000L)
            repeat(5) { i -> Thread.sleep(150); img = escena.render(4_000_000_000L + i * 500_000_000L) }
            File(dir, "$nombre.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            escena.close()
        }
        fun clic(x: Float, y: Float): (ImageComposeScene, Long) -> Unit = { e, t0 ->
            e.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, androidx.compose.ui.geometry.Offset(x, y), timeMillis = t0 / 1_000_000)
            e.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, androidx.compose.ui.geometry.Offset(x, y), timeMillis = t0 / 1_000_000 + 10, button = androidx.compose.ui.input.pointer.PointerButton.Primary)
            e.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, androidx.compose.ui.geometry.Offset(x, y), timeMillis = t0 / 1_000_000 + 60, button = androidx.compose.ui.input.pointer.PointerButton.Primary)
        }
        val viendo = clic(395f, 183f)   // la pestaña «Viendo»
        captura("azabache-mostaza-peliculas", Pantalla.Lista(2))
        captura("azabache-mostaza-peliculas-oscuro", Pantalla.Lista(2), oscuro = true)
        captura("azabache-mostaza-peliculas-viendo", Pantalla.Lista(2), haz = viendo)
        captura("azabache-mostaza-series", Pantalla.Lista(3), haz = viendo)
        captura("azabache-mostaza-series-oscuro", Pantalla.Lista(3), oscuro = true, haz = viendo)
        captura("azabache-mostaza-ficha", Pantalla.Detalle(2, 278L, Pantalla.Lista(2)))
        captura("azabache-mostaza-ficha-oscuro", Pantalla.Detalle(2, 278L, Pantalla.Lista(2)), oscuro = true)
        captura("azabache-mostaza-ficha-serie", Pantalla.Detalle(3, 82L, Pantalla.Lista(3)))
        captura("azabache-mostaza-ficha-serie-oscuro", Pantalla.Detalle(3, 82L, Pantalla.Lista(3)), oscuro = true)
        captura("azabache-mostaza-ficha-viendo", Pantalla.Detalle(2, 496243L, Pantalla.Lista(2)))
        captura("azabache-mostaza-ficha-sin-valorar", Pantalla.Detalle(2, 603L, Pantalla.Lista(2)))
        captura("azabache-mostaza-portada", Pantalla.Inicio)
        LaFamilia.reiniciaParaPruebas(); Local.dirPruebas = null; Local.olvidaParaPruebas(); Cuenta.olvidaParaPruebas(); CosasMostaza.olvidaParaPruebas()
    }
}
