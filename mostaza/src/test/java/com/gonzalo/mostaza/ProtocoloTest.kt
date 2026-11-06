package com.gonzalo.mostaza

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El protocolo de los addons de Stremio (stremio-addon-sdk/docs) contra un addon de mentira en el ordenador: las direcciones que se piden
 * (`/{recurso}/{tipo}/{id}.json` y, con extras, `/{recurso}/{tipo}/{id}/{extra}.json`, con encodeURIComponent), el manifiesto (recursos
 * como texto y como objeto, prefijos, catálogos con sus extras) y las respuestas (metas, ficha con episodios, fuentes de todos los tipos
 * con sus `behaviorHints`, subtítulos).
 */
class ProtocoloTest {
    private val manifiesto = """
        {"id":"org.prueba.addon","version":"1.2.3","name":"Prueba","description":"Un addon de prueba","types":["movie","series"],"idPrefixes":["tt"],
         "resources":["catalog","meta",{"name":"stream","types":["movie","series"],"idPrefixes":["tt","kitsu:"]},{"name":"subtitles","types":["movie"]}],
         "catalogs":[
           {"type":"movie","id":"top","name":"Populares","extra":[{"name":"genre","options":["Action","Drama"]},{"name":"search"},{"name":"skip"}]},
           {"type":"series","id":"buscar","name":"Buscar","extra":[{"name":"search","isRequired":true}]},
           {"type":"movie","id":"year","name":"Por año","extra":[{"name":"genre","options":["2026","2025","2024","2023","2022"],"isRequired":true}]},
           {"type":"movie","id":"viejo","name":"Viejo","genres":["Comedy"],"extraSupported":["genre","skip"]}
         ],
         "behaviorHints":{"configurable":true,"p2p":true}}
    """.trimIndent()

    @Test fun elManifiestoSeLeeEntero() {
        val m = Protocolo.manifiesto(manifiesto)
        assertEquals("org.prueba.addon", m.id); assertEquals("1.2.3", m.version); assertEquals("Prueba", m.nombre)
        assertTrue(m.configurable); assertTrue(m.p2p); assertFalse(m.configuracionObligatoria)
        assertEquals(listOf("catalog", "meta", "stream", "subtitles"), m.recursos.map { it.nombre })
        val top = m.catalogos.first { it.id == "top" }
        assertEquals(listOf("Action", "Drama"), top.generos); assertTrue(top.buscable); assertFalse(top.soloBusqueda); assertTrue(top.pagina)
        assertTrue(m.catalogos.first { it.id == "buscar" }.soloBusqueda)
        val ano = m.catalogos.first { it.id == "year" }
        assertTrue(ano.porAno); assertTrue(ano.generoObligatorio)
        // El formato de antes («extraSupported» y «genres» sueltos)
        val viejo = m.catalogos.first { it.id == "viejo" }
        assertEquals(listOf("Comedy"), viejo.generos); assertTrue(viejo.pagina)
        assertEquals(listOf("top", "buscar"), m.buscables.map { it.id })
    }

    @Test fun aQueRespondeCadaRecurso() {
        val m = Protocolo.manifiesto(manifiesto)
        // Como texto: los tipos y prefijos del manifiesto
        assertTrue(m.soporta("meta", "movie", "tt0111161")); assertFalse(m.soporta("meta", "movie", "kitsu:1")); assertFalse(m.soporta("meta", "channel", "tt1"))
        // Como objeto: los suyos
        assertTrue(m.soporta("stream", "series", "kitsu:1:5")); assertTrue(m.soporta("stream", "movie", "tt1")); assertFalse(m.soporta("stream", "movie", "yt:1"))
        // Como objeto sin prefijos: todos los ids
        assertTrue(m.soporta("subtitles", "movie", "loquesea")); assertFalse(m.soporta("subtitles", "series", "tt1"))
        // Los catálogos no miran el id
        assertTrue(m.soporta("catalog", "movie")); assertFalse(m.soporta("catalog", "channel"))
        assertFalse(m.soporta("addon_catalog", "movie", "tt1"))
    }

    @Test fun lasDirecciones() {
        val b = "https://addon.example/abc/def"
        assertEquals("$b/meta/series/tt0944947.json", Protocolo.url(b, "meta", "series", "tt0944947"))
        assertEquals("$b/stream/series/tt0944947%3A1%3A2.json", Protocolo.url(b, "stream", "series", "tt0944947:1:2"))
        assertEquals("$b/catalog/movie/top/search=game%20of%20thrones&skip=100.json", Protocolo.url(b, "catalog", "movie", "top", listOf("search" to "game of thrones", "skip" to "100")))
        assertEquals("$b/catalog/movie/top/genre=Ciencia%20ficci%C3%B3n.json", Protocolo.url(b, "catalog", "movie", "top", listOf("genre" to "Ciencia ficción")))
        assertEquals("$b/catalog/movie/top/search=it's%20(1)!.json", Protocolo.url(b, "catalog", "movie", "top", listOf("search" to "it's (1)!")))
        assertEquals("https://addon.example/x/manifest.json", Protocolo.normaliza("stremio://addon.example/x/manifest.json"))
        assertEquals("https://addon.example/x/manifest.json", Protocolo.normaliza("addon.example/x/"))
        assertEquals("https://addon.example/x", Protocolo.base("https://addon.example/x/manifest.json"))
        assertEquals("https://addon.example/x/configure", Protocolo.configurar("https://addon.example/x/manifest.json"))
    }

    @Test fun conUnAddonDeMentira() {
        AddonDeMentira().use { a ->
            a.respuestas["/clave123/manifest.json"] = manifiesto
            a.respuestas["/clave123/catalog/movie/top.json"] = """{"metas":[{"id":"tt0111161","type":"movie","name":"Cadena perpetua","poster":"https://img/1.jpg","releaseInfo":"1994","genres":["Drama"],"imdbRating":"9.3"},{"id":"","name":"sin id"}]}"""
            a.respuestas["/clave123/catalog/movie/top/genre=Drama&skip=20.json"] = """{"metas":[{"id":"tt0068646","type":"movie","name":"El padrino"}]}"""
            a.respuestas["/clave123/catalog/series/buscar/search=juego%20de%20tronos.json"] = """{"metas":[{"id":"tt0944947","type":"series","name":"Juego de tronos","releaseInfo":"2011–2019"}]}"""
            // Una ficha como las de Cinemeta: los episodios con «name», «number» y «firstAired» además de lo del documento
            a.respuestas["/clave123/meta/series/tt0944947.json"] = """{"meta":{"id":"tt0944947","type":"series","name":"Juego de tronos","releaseInfo":"2011–2019","runtime":"57 min",
                "cast":["Emilia Clarke"],"director":null,"genres":["Drama","Fantasy"],"moviedb_id":1399,"tvdb_id":121361,"imdb_id":"tt0944947","background":"https://img/fondo.jpg",
                "videos":[{"id":"tt0944947:1:1","title":"Se acerca el invierno","season":1,"episode":1,"released":"2011-04-17T00:00:00.000Z"},
                          {"id":"tt0944947:1:2","name":"El camino real","season":1,"number":2,"firstAired":"2011-04-24T00:00:00.000Z","overview":"…"},
                          {"id":"tt0944947:0:1","name":"Especial","season":0,"number":1}],
                "trailers":[{"source":"abc","type":"Trailer"}],"behaviorHints":{"defaultVideoId":null}}}"""
            a.respuestas["/clave123/stream/series/tt0944947%3A1%3A1.json"] = """{"streams":[
                {"name":"Directa\n1080p","description":"Juego.de.Tronos.S01E01.1080p.mkv\n💾 2.1 GB","url":"https://debrid.example/d/1.mkv","behaviorHints":{"notWebReady":true,"bingeGroup":"prueba-1080p","proxyHeaders":{"request":{"User-Agent":"Stremio","Referer":"https://x"}},"videoSize":2100000000,"filename":"Juego.de.Tronos.S01E01.1080p.mkv"}},
                {"name":"Torrent","title":"👤 120","infoHash":"ABCDEF0123456789ABCDEF0123456789ABCDEF01","fileIdx":3,"sources":["tracker:udp://tracker.example:1337","dht:abcdef"]},
                {"name":"Tráiler","ytId":"dQw4w9WgXcQ"},
                {"name":"En la web","externalUrl":"https://cine.example/ver"},
                {"name":"Usenet","nzbUrl":"https://nzb.example/x.nzb"},
                {"name":"Comprimido","rarUrls":[{"url":"https://rar.example/x.rar","bytes":10}]},
                {"name":"Vacía"}]}"""
            a.respuestas["/clave123/subtitles/movie/tt0111161/videoSize=123&filename=peli.mkv.json"] = """{"subtitles":[{"id":"1","url":"https://subs/1.srt","lang":"spa"},{"url":"https://subs/2.vtt","lang":"eng","label":"English [CC]"},{"lang":"fre"}]}"""
            val url = a.base + "/clave123/manifest.json"
            val c = ClienteStremio(HttpJava())
            val m = c.manifiesto(url)
            assertEquals("Prueba", m.nombre)
            val top = m.catalogos.first { it.id == "top" }
            val metas = c.catalogo(url, top)
            assertEquals(1, metas.size); assertEquals("Cadena perpetua", metas[0].nombre); assertEquals("1994", metas[0].ano); assertEquals("9.3", metas[0].nota)
            assertEquals("El padrino", c.catalogo(url, top, listOf("genre" to "Drama", "skip" to "20")).single().nombre)
            assertEquals("Juego de tronos", c.catalogo(url, m.catalogos.first { it.id == "buscar" }, listOf("search" to "juego de tronos")).single().nombre)
            val meta = c.meta(url, "series", "tt0944947")!!
            assertEquals(57, meta.minutos); assertEquals(1399L, meta.tmdb); assertEquals("tt0944947", meta.imdb); assertTrue(meta.esSerie)
            assertEquals(listOf(1, 0), meta.temporadas)
            val e2 = meta.episodios(1)[1]
            assertEquals("El camino real", e2.titulo); assertEquals(2, e2.episodio); assertTrue(e2.estreno > 0); assertEquals("…", e2.resumen)
            assertEquals(listOf("abc"), meta.trailers); assertEquals(emptyList<String>(), meta.direccion)
            val fs = c.fuentes(url, "series", "tt0944947:1:1")
            assertEquals("la que no trae nada, fuera", 6, fs.size)
            assertEquals(listOf(TipoFuente.DIRECTA, TipoFuente.TORRENT, TipoFuente.YOUTUBE, TipoFuente.EXTERNA, TipoFuente.NZB, TipoFuente.ARCHIVO), fs.map { it.tipo })
            val d = fs[0]
            assertTrue(d.noWeb); assertEquals("prueba-1080p", d.grupo); assertEquals(mapOf("User-Agent" to "Stremio", "Referer" to "https://x"), d.cabeceras)
            assertEquals(2_100_000_000L, d.tamano); assertEquals("Juego.de.Tronos.S01E01.1080p.mkv", d.archivo)
            val t = fs[1]
            assertEquals("el infoHash, en minúsculas", "abcdef0123456789abcdef0123456789abcdef01", t.infoHash); assertEquals(3, t.fileIdx)
            assertEquals(listOf("tracker:udp://tracker.example:1337", "dht:abcdef"), t.trackers); assertEquals("«title», el de antes de «description»", "👤 120", t.descripcion)
            assertEquals(listOf("https://rar.example/x.rar"), fs[5].archivos)
            val subs = c.subtitulos(url, "movie", "tt0111161", Fuente(url = "x", tamano = 123, archivo = "peli.mkv"))
            assertEquals(2, subs.size); assertEquals("spa", subs[0].idioma); assertEquals("English [CC]", subs[1].etiqueta); assertEquals("https://subs/2.vtt", subs[1].id)
            // Lo que se ha pedido: con el id codificado y los extras en orden
            assertTrue(a.pedidos.contains("/clave123/stream/series/tt0944947%3A1%3A1.json"))
            assertTrue(a.pedidos.contains("/clave123/catalog/movie/top/genre=Drama&skip=20.json"))
        }
    }

    @Test fun unErrorNoDiceLaDireccion() {
        AddonDeMentira().use { a ->
            val r = runCatching { ClienteStremio(HttpJava()).manifiesto(a.base + "/clave-secreta-xyz/manifest.json") }
            assertTrue(r.isFailure)
            assertFalse("el error no lleva la dirección (puede llevar la clave del debrid)", r.exceptionOrNull()!!.message.orEmpty().contains("clave-secreta-xyz"))
            a.respuestas["/x/manifest.json"] = """{"hola":1}"""
            assertTrue(runCatching { ClienteStremio(HttpJava()).manifiesto(a.base + "/x/manifest.json") }.isFailure)
        }
    }

    @Test fun lasDuracionesYLasFechas() {
        assertEquals(142, Meta.minutosDe("142 min")); assertEquals(142, Meta.minutosDe("2h 22min")); assertEquals(120, Meta.minutosDe("120m")); assertEquals(0, Meta.minutosDe(""))
        assertEquals(1_302_998_400_000L, Protocolo.fecha("2011-04-17T00:00:00.000Z")); assertEquals(1_302_998_400_000L, Protocolo.fecha("2011-04-17")); assertEquals(0L, Protocolo.fecha("mañana"))
        assertNull(Protocolo.meta("""{"meta":{"id":"","name":"x"}}"""))
    }
}
