package com.gonzalo.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * El cliente de Xtream Codes contra un servidor de mentira con respuestas de la forma de las de verdad (`resources/xtream`): campos que
 * llegan como texto o como número, vacíos como `""` o `null`, episodios por temporada en un objeto o en listas, la guía en base64.
 */
class XtreamTest {
    private val red = servidorXtream()
    private val x = Xtream(LISTA_XTREAM, red)

    @Test fun laCuenta() {
        val p = x.perfil()
        assertTrue(p.activo); assertEquals(2, p.conexionesMax); assertEquals(1_798_761_600_000L, p.caduca)
        assertEquals(listOf("m3u8", "ts", "rtmp"), p.formatos); assertEquals("Europe/Madrid", p.zonaHoraria); assertFalse(p.prueba)
        assertEquals("http://tv.ejemplo.test:8080/player_api.php?username=pepe&password=s3cr3t0", red.pedidas.last().first)
    }

    @Test fun conLaContrasenaMalNoEntraYNoLaDice() {
        val mal = Xtream(LISTA_XTREAM.copy(contrasena = "otraClaveMala"), red)
        val e = runCatching { mal.perfil() }.exceptionOrNull() as IptvError
        assertTrue(e.message!!.contains("no valen"))
        assertFalse(e.message!!.contains("otraClaveMala")); assertFalse(e.message!!.contains("pepe"))
        // Un 401 o un 403 (cuenta caducada), y un servidor que no existe: sin la dirección en el texto
        for (r in listOf(Xtream(LISTA_XTREAM, RedDeMentira { 401 }), Xtream(LISTA_XTREAM, RedDeMentira { throw java.io.FileNotFoundException("http://tv.ejemplo.test:8080/player_api.php?username=pepe&password=s3cr3t0") }))) {
            val m = runCatching { r.perfil() }.exceptionOrNull()!!.message!!
            assertFalse(m, m.contains("s3cr3t0") || m.contains("pepe") || m.contains("ejemplo"))
        }
        // Algo que no es Xtream
        assertTrue(runCatching { Xtream(LISTA_XTREAM, RedDeMentira { "<html>hola</html>" }).perfil() }.exceptionOrNull() is IptvError)
        assertTrue(runCatching { Xtream(LISTA_XTREAM, RedDeMentira { "{}" }).perfil() }.exceptionOrNull()!!.message!!.contains("no parece"))
    }

    @Test fun losCanalesConLosCamposComoVengan() {
        val cats = x.categoriasDirecto()
        assertEquals(listOf("ES | Nacionales", "ES | Deportes", "Radio"), cats.map { it.nombre })
        val l = ArrayList<Canal>()
        assertEquals(4, x.canales(cats.associate { it.id to it.nombre }) { l += it })
        val la1 = l[0]
        assertEquals("x101", la1.clave); assertEquals(101L, la1.idXtream); assertEquals("ES: La 1 HD", la1.nombre); assertEquals("ES | Nacionales", la1.grupo)
        assertEquals("La1.es", la1.tvgId); assertEquals(Catchup("xc", 3), la1.catchup); assertEquals("", la1.url)
        assertEquals("el id como texto", 102L, l[1].idXtream); assertEquals("y el número", 2, l[1].numero); assertEquals("", l[1].tvgId); assertNull(l[1].catchup)
        assertTrue(l.first { it.idXtream == 700L }.radio)
    }

    @Test fun lasDirecciones() {
        val c = Canal("x101", "La 1", idXtream = 101)
        assertEquals("http://tv.ejemplo.test:8080/live/pepe/s3cr3t0/101.ts", x.urlDirecto(c))
        assertEquals("http://tv.ejemplo.test:8080/live/pepe/s3cr3t0/101.m3u8", x.urlDirecto(c, "m3u8"))
        assertEquals("http://tv.ejemplo.test:8080/movie/pepe/s3cr3t0/5001.mkv", x.urlPelicula(Pelicula(5001, "Dune", extension = "mkv")))
        assertEquals("http://tv.ejemplo.test:8080/series/pepe/s3cr3t0/9011.mkv", x.urlEpisodio(Episodio(9011, 1, 1, "x", "mkv")))
        assertEquals("http://tv.ejemplo.test:8080/xmltv.php?username=pepe&password=s3cr3t0", x.urlGuia())
        // Lo ya emitido, en la hora del servidor
        val inicio = LocalDateTime.of(2026, 11, 7, 10, 0).atZone(ZoneId.of("Europe/Madrid")).toInstant().toEpochMilli()
        assertEquals("http://tv.ejemplo.test:8080/timeshift/pepe/s3cr3t0/60/2026-11-07:10-00/101.ts", x.urlTimeshift(c, inicio, 60, "Europe/Madrid"))
        // Un usuario con espacios o con «/» no rompe la dirección
        val raro = Xtream(LISTA_XTREAM.copy(usuario = "ana maría", contrasena = "a/b+c"), red)
        assertEquals("http://tv.ejemplo.test:8080/live/ana%20mar%C3%ADa/a%2Fb%2Bc/101.ts", raro.urlDirecto(c))
    }

    @Test fun elServidorComoLoEscribas() {
        assertEquals("http://tv.ejemplo.test:8080", Servidor.normaliza("tv.ejemplo.test:8080/"))
        assertEquals("https://tv.ejemplo.test", Servidor.normaliza(" https://tv.ejemplo.test/player_api.php?username=a&password=b "))
        assertEquals("http://tv.ejemplo.test:8080/sub", Servidor.normaliza("http://tv.ejemplo.test:8080/sub/get.php"))
        // La dirección M3U de un servidor de Xtream sirve como login
        assertEquals(Triple("http://tv.ejemplo.test:8080", "pepe", "s3cr3t0"), Servidor.deEnlace("http://tv.ejemplo.test:8080/get.php?username=pepe&password=s3cr3t0&type=m3u_plus&output=ts"))
        assertEquals(Triple("http://h:1", "a b", "c&d"), Servidor.deEnlace("http://h:1/get.php?username=a%20b&password=c%26d"))
        assertNull(Servidor.deEnlace("http://h/lista.m3u")); assertNull(Servidor.deEnlace("http://h/get.php?username=a"))
    }

    @Test fun lasPeliculasYSuFicha() {
        assertEquals(listOf("Estrenos", "Clásicos"), x.categoriasPeliculas().map { it.nombre })
        val l = ArrayList<Pelicula>(); x.peliculas { l += it }
        assertEquals(3, l.size)
        val dune = l[0]
        assertEquals(5001L, dune.id); assertEquals("mkv", dune.extension); assertEquals(8.2, dune.nota, 0.001); assertEquals(1_714_000_000_000L, dune.anadida); assertEquals("693134", dune.tmdb)
        assertEquals(5002L, l[1].id); assertEquals(8.7, l[1].nota, 0.001)
        assertEquals("sin extensión: mp4", "mp4", l[2].extension); assertEquals(0L, l[2].anadida)
        val f = x.fichaPelicula(dune)
        assertEquals(9960L, f.duracionSeg); assertEquals("Denis Villeneuve", f.director); assertEquals("https://image.tmdb.org/t/p/w1280/dune2-fondo.jpg", f.fondo)
        assertEquals("Paul Atreides se une a Chani y a los Fremen mientras busca venganza.", f.descripcion); assertEquals("693134", f.pelicula.tmdb)
        assertEquals("2024-02-27", f.estreno); assertEquals("U2Qp5pL3ovA", f.trailer)
        assertEquals(3600L, Xtream.duracion("01:00:00")); assertEquals(5400L, Xtream.duracion("90 min")); assertEquals(1800L, Xtream.duracion("30:00"))
    }

    @Test fun lasSeriesYSusEpisodios() {
        val l = ArrayList<Serie>(); x.series { l += it }
        assertEquals(2, l.size)
        assertEquals("2011", l[0].ano); assertEquals("https://image.tmdb.org/t/p/w1280/jdt-fondo.jpg", l[0].fondo); assertEquals("2022", l[1].ano)
        val f = x.fichaSerie(l[0])
        assertEquals(listOf(1, 2, 0), f.temporadas.map { it.numero })
        assertEquals("Especiales", f.temporadas.last().nombre)
        assertEquals("en orden aunque vengan desordenados", listOf(1, 2, 3), f.episodios[1]!!.map { it.numero })
        val e = f.episodios[1]!![0]
        assertEquals(9011L, e.id); assertEquals("mkv", e.extension); assertEquals(3420L, e.duracionSeg); assertEquals("Trama 1x1", e.trama)
        assertEquals("el siguiente, también de una temporada a otra", 9021L, f.siguiente(f.episodios[1]!!.last())!!.id)
        assertEquals("los especiales, al final", 9999L, f.todos.last().id)
        // Episodios en una lista de listas y sin temporadas
        val bear = x.fichaSerie(l[1])
        assertEquals(listOf(1), bear.temporadas.map { it.numero }); assertEquals(2, bear.episodios[1]!!.size); assertEquals(1800L, bear.episodios[1]!![0].duracionSeg)
    }

    @Test fun laGuiaCortaEnBase64() {
        val p = x.guiaCorta(Canal("x101", "La 1", tvgId = "La1.es", idXtream = 101), 4, "Europe/Madrid")
        assertEquals(listOf("Telediario 1", "El tiempo", "Saber y ganar: ¿quién sabe más?"), p.map { it.titulo })
        assertEquals("Las noticias del día, con Ana Blanco.", p[0].descripcion)
        assertEquals(1_794_063_600_000L, p[0].inicio); assertEquals(1_794_066_600_000L, p[0].fin)
        assertTrue(red.pedidas.last().first.contains("action=get_short_epg&stream_id=101&limit=4"))
        // En texto (sin base64) y sin las horas en segundos: la hora del servidor
        val t = Xtream.programasDe(JsonFlujo.lee(recursoTexto("xtream/short_epg_texto.json")).mapa()["epg_listings"].lista().map { it.mapa() }, "x", "Europe/Madrid")
        assertEquals(listOf("News", "Late Show"), t.map { it.titulo }); assertEquals("Daily news", t[0].descripcion)
        assertEquals(LocalDateTime.of(2026, 11, 7, 15, 0).atZone(ZoneId.of("Europe/Madrid")).toInstant().toEpochMilli(), t[0].inicio)
    }

    @Test fun elBase64SoloSiTodoLoEs() {
        assertEquals("Hola", Base64Guia.descodifica("SG9sYQ=="))
        assertNull(Base64Guia.descodifica("Hola mundo")); assertNull(Base64Guia.descodifica("News"))
        assertFalse(Base64Guia.todoEnBase64(listOf("SG9sYQ==", "News y más")))
        assertTrue(Base64Guia.todoEnBase64(listOf("SG9sYQ==", "", "w7Fhbw==")))
    }

    @Test fun unaListaEnormeDeXtreamATrozos() {
        // 120 000 películas (más de 50 MB de JSON) sin tenerlas enteras en memoria: cada una se suelta en cuanto se lee
        val n = 120_000
        val json = sequenceOf("[") + (1..n).asSequence().map { i ->
            (if (i > 1) "," else "") + """{"num":$i,"name":"Película número $i con su título","stream_type":"movie","stream_id":"$i","stream_icon":"https://image.tmdb.org/t/p/w600/$i.jpg","rating":"7.${i % 10}","rating_5based":3.5,"added":"1714000000","category_id":"${i % 40}","category_ids":[${i % 40}],"container_extension":"mkv","custom_sid":null,"direct_source":"","plot":"Una trama larga para que el JSON pese lo que pesa el de un servidor grande de verdad, con su texto."}"""
        } + sequenceOf("]")
        val datos = BytesQueSeInventan(json)
        val g = Xtream(LISTA_XTREAM, RedDeMentira { u -> if (u.contains("get_vod_streams")) datos else null })
        var cuantas = 0; var suma = 0L
        g.peliculas { cuantas++; suma += it.id }
        assertEquals(n, cuantas); assertEquals(n.toLong() * (n + 1) / 2, suma)
        assertTrue("más de 50 MB: ${datos.leidos}", datos.leidos > 50_000_000)
    }

    @Test fun unJsonCortadoDaUnErrorSinSecretos() {
        val g = Xtream(LISTA_XTREAM, RedDeMentira { """[{"stream_id":1,"name":"a"},{"stream_id":2,"na""" })
        try { g.canales(emptyMap()) {}; fail() } catch (e: IptvError) { assertFalse(e.message!!.contains("s3cr3t0")) }
    }
}
