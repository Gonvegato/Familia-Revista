package com.gonzalo.mostaza

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El catálogo propio (TMDB): se leen respuestas con la forma que da su documentación (developer.themoviedb.org: trending, discover,
 * detalles con append_to_response, temporadas, find, watch/providers, genre/list).
 */
class TmdbTest {
    private val tendencias = """{"page":1,"results":[
        {"adult":false,"backdrop_path":"/fondo1.jpg","id":550,"title":"El club de la lucha","original_title":"Fight Club","overview":"Un oficinista…","poster_path":"/p1.jpg","media_type":"movie","genre_ids":[18,53],"popularity":61.4,"release_date":"1999-10-15","video":false,"vote_average":8.438,"vote_count":27000},
        {"adult":false,"backdrop_path":"/fondo2.jpg","id":1396,"name":"Breaking Bad","original_name":"Breaking Bad","overview":"Walter White…","poster_path":"/p2.jpg","media_type":"tv","genre_ids":[18],"popularity":300.1,"first_air_date":"2008-01-20","vote_average":8.9,"vote_count":13000,"origin_country":["US"]},
        {"adult":false,"id":287,"name":"Brad Pitt","media_type":"person","profile_path":"/bp.jpg"},
        {"adult":false,"id":9,"title":"Sin póster","media_type":"movie","poster_path":null}
    ],"total_pages":500,"total_results":10000}"""

    @Test fun lasListas() {
        val l = TmdbDatos.breves(tendencias, tvPorDefecto = false, generos = mapOf(18L to "Drama", 53L to "Suspense"))
        assertEquals("sin personas ni cosas sin póster", 2, l.size)
        val peli = l[0]
        assertEquals("tmdb:movie:550", peli.id); assertEquals("movie", peli.tipo); assertEquals("El club de la lucha", peli.nombre)
        assertEquals("https://image.tmdb.org/t/p/w342/p1.jpg", peli.poster); assertEquals("https://image.tmdb.org/t/p/w1280/fondo1.jpg", peli.fondo)
        assertEquals("1999", peli.ano); assertEquals(listOf("Drama", "Suspense"), peli.generos); assertEquals("8.4", peli.nota)
        val serie = l[1]
        assertEquals("tmdb:tv:1396", serie.id); assertEquals("series", serie.tipo); assertEquals("Breaking Bad", serie.nombre); assertEquals("2008", serie.ano)
        assertEquals(500, TmdbDatos.paginas(tendencias))
        // Una lista de solo series (sin media_type)
        val soloSeries = """{"results":[{"id":1399,"name":"Juego de tronos","poster_path":"/got.jpg","first_air_date":"2011-04-17"}]}"""
        assertEquals("tmdb:tv:1399", TmdbDatos.breves(soloSeries, tvPorDefecto = true).single().id)
    }

    @Test fun losIds() {
        assertEquals(TmdbDatos.IdTmdb(false, 550), TmdbDatos.deId("tmdb:movie:550"))
        assertEquals("tv/1396", TmdbDatos.deId("tmdb:tv:1396")!!.ruta)
        assertNull(TmdbDatos.deId("tt0137523")); assertNull(TmdbDatos.deId("tmdb:person:1")); assertNull(TmdbDatos.deId("tmdb:movie:x"))
        assertEquals(TmdbDatos.IdTmdb(true, 1396), TmdbDatos.encontrado("""{"movie_results":[],"person_results":[],"tv_results":[{"id":1396}],"tv_episode_results":[],"tv_season_results":[]}"""))
        assertEquals(TmdbDatos.IdTmdb(false, 550), TmdbDatos.encontrado("""{"movie_results":[{"id":550,"title":"x"}],"tv_results":[]}"""))
        assertNull(TmdbDatos.encontrado("""{"movie_results":[],"tv_results":[]}"""))
    }

    @Test fun laFichaDeUnaPelicula() {
        val j = """{"adult":false,"backdrop_path":"/f.jpg","genres":[{"id":18,"name":"Drama"}],"id":550,"imdb_id":"tt0137523","original_language":"en","original_title":"Fight Club",
            "overview":"Un oficinista…","poster_path":"/p.jpg","production_countries":[{"iso_3166_1":"US","name":"Estados Unidos"}],"release_date":"1999-10-15","runtime":139,
            "tagline":"Travesura. Caos. Jabón.","title":"El club de la lucha","vote_average":8.438,
            "credits":{"cast":[{"name":"Edward Norton","character":"El narrador","order":1,"profile_path":"/en.jpg"},{"name":"Brad Pitt","character":"Tyler Durden","order":0,"profile_path":"/bp.jpg"}],
                       "crew":[{"name":"David Fincher","job":"Director","department":"Directing"},{"name":"Jim Uhls","job":"Screenplay","department":"Writing"}]},
            "videos":{"results":[{"iso_639_1":"en","key":"ingles","site":"YouTube","type":"Trailer","official":true},{"iso_639_1":"es","key":"teaserEs","site":"YouTube","type":"Teaser","official":true},
                                 {"iso_639_1":"es","key":"trailerEs","site":"YouTube","type":"Trailer","official":true},{"iso_639_1":"es","key":"vimeo","site":"Vimeo","type":"Trailer"}]},
            "recommendations":{"results":[{"id":680,"title":"Pulp Fiction","poster_path":"/pf.jpg","media_type":"movie","release_date":"1994-09-10"}]},
            "watch/providers":{"id":550,"results":{"ES":{"link":"https://www.themoviedb.org/movie/550/watch?locale=ES","flatrate":[{"logo_path":"/n.jpg","provider_id":8,"provider_name":"Netflix","display_priority":1},{"logo_path":"/d.jpg","provider_id":337,"provider_name":"Disney Plus","display_priority":0}],
                                                   "rent":[{"logo_path":"/a.jpg","provider_id":2,"provider_name":"Apple TV","display_priority":2}]},"US":{"flatrate":[{"provider_id":9,"provider_name":"Amazon","display_priority":0}]}}}}"""
        val (m, e) = TmdbDatos.meta(j, tv = false)!!
        assertEquals("el id es el de IMDb (el de tus addons y tu biblioteca)", "tt0137523", m.id)
        assertEquals("movie", m.tipo); assertEquals("El club de la lucha", m.nombre); assertEquals(139, m.minutos); assertEquals("1999", m.ano)
        assertEquals(listOf("Brad Pitt", "Edward Norton"), m.reparto); assertEquals(listOf("David Fincher"), m.direccion); assertEquals(listOf("Jim Uhls"), m.guion)
        assertEquals("Estados Unidos", m.pais); assertEquals(550L, m.tmdb); assertEquals("tt0137523", m.imdb); assertFalse(m.esSerie)
        assertEquals("primero el tráiler en castellano", listOf("trailerEs", "teaserEs", "ingles"), m.trailers)
        assertEquals("Travesura. Caos. Jabón.", e.lema)
        assertEquals("solo las de España y de suscripción, en su orden", listOf("Disney Plus", "Netflix"), e.plataformas.map { it.nombre })
        assertEquals("https://image.tmdb.org/t/p/w92/d.jpg", e.plataformas[0].logo)
        assertEquals("tmdb:movie:680", e.similares.single().id)
        assertEquals("Tyler Durden", e.personas.first().papel)
    }

    @Test fun laFichaDeUnaSerieConSusEpisodios() {
        val j = """{"id":1396,"name":"Breaking Bad","first_air_date":"2008-01-20","episode_run_time":[47],"created_by":[{"name":"Vince Gilligan"}],"poster_path":"/p.jpg",
            "seasons":[{"season_number":0,"episode_count":9},{"season_number":1,"episode_count":7},{"season_number":2,"episode_count":13}],
            "external_ids":{"imdb_id":"tt0903747","tvdb_id":81189},
            "season/1":{"episodes":[{"air_date":"2008-01-20","episode_number":1,"name":"Piloto","overview":"…","season_number":1,"still_path":"/s1.jpg"},{"air_date":"2008-01-27","episode_number":2,"name":"El gato está en la bolsa","season_number":1}]},
            "season/0":{"episodes":[{"episode_number":1,"name":"Especial","season_number":0}]}}"""
        val (m, e) = TmdbDatos.meta(j, tv = true)!!
        assertEquals("tt0903747", m.id); assertEquals("series", m.tipo); assertTrue(m.esSerie); assertEquals(47, m.minutos)
        assertEquals(listOf("Vince Gilligan"), m.direccion); assertEquals(81189L, m.tvdb); assertTrue(e.tmdbTv)
        assertEquals("los episodios con el id de Stremio", listOf("tt0903747:0:1", "tt0903747:1:1", "tt0903747:1:2"), m.videos.map { it.id }.sorted())
        assertEquals("Piloto", m.episodios(1).first().titulo); assertEquals("https://image.tmdb.org/t/p/w300/s1.jpg", m.episodios(1).first().miniatura)
        assertEquals(listOf(0, 1, 2), TmdbDatos.temporadas(j))
        // Sin id de IMDb: el de TMDB (las fuentes no lo entenderán, pero la ficha se ve)
        val sin = TmdbDatos.meta("""{"id":5,"name":"Rara","season/1":{"episodes":[{"episode_number":1,"season_number":1}]}}""", tv = true)!!.first
        assertEquals("tmdb:tv:5", sin.id); assertEquals("tmdb:tv:5:1:1", sin.videos.single().id)
    }

    @Test fun losEpisodiosSonLosDeTusAddonsConLosTitulosDeTmdb() {
        val tmdb = Meta("tt0903747", "series", "Breaking Bad", videos = listOf(Video("tt0903747:1:1", "Piloto", 1, 1, resumen = "En castellano", miniatura = "es.jpg")))
        val addon = Meta("tt0903747", "series", "Breaking Bad", videos = listOf(Video("tt0903747:1:1", "Pilot", 1, 1, estreno = 5L), Video("tt0903747:1:2", "Cat's in the Bag", 1, 2)))
        val (m, _) = Fichas.junta(tmdb to Extras(), addon)!!
        assertEquals(listOf("tt0903747:1:1", "tt0903747:1:2"), m.videos.map { it.id })
        assertEquals("Piloto", m.videos[0].titulo); assertEquals("En castellano", m.videos[0].resumen); assertEquals(5L, m.videos[0].estreno)
        assertEquals("el que TMDB no tiene, como venga", "Cat's in the Bag", m.videos[1].titulo)
        assertEquals("sin TMDB, la de tus addons", addon, Fichas.junta(null, addon)!!.first)
        assertNull(Fichas.junta(null, null))
    }

    @Test fun lasPlataformasYLosGeneros() {
        val p = """{"results":[{"display_priorities":{"ES":3,"US":1},"display_priority":1,"logo_path":"/x.jpg","provider_name":"Max","provider_id":1899},
            {"display_priorities":{"ES":0},"display_priority":9,"logo_path":"/n.jpg","provider_name":"Netflix","provider_id":8},
            {"display_priorities":{"US":0},"display_priority":5,"provider_name":"Solo fuera","provider_id":77}]}"""
        assertEquals(listOf("Netflix", "Max", "Solo fuera"), TmdbDatos.plataformas(p).map { it.nombre })
        assertEquals(mapOf(28L to "Acción", 35L to "Comedia"), TmdbDatos.generos("""{"genres":[{"id":28,"name":"Acción"},{"id":35,"name":"Comedia"},{"id":0,"name":"x"}]}"""))
    }

    @Test fun laDireccionNoLlevaLaClave() {
        val u = TmdbDatos.url("discover/movie", listOf("with_watch_providers" to "8|337", "sort_by" to "popularity.desc"))
        assertTrue(u.startsWith("https://api.themoviedb.org/3/discover/movie?language=es-ES&"))
        assertTrue(u.contains("with_watch_providers=8%7C337"))
        assertFalse(u.contains("api_key"))
    }

    @Test fun alternaPeliculasYSeries() {
        val a = listOf(MetaBreve("1", "movie", "a"), MetaBreve("2", "movie", "b"))
        val b = listOf(MetaBreve("3", "series", "c"))
        assertEquals(listOf("1", "3", "2"), TmdbDatos.alterna(a, b).map { it.id })
    }
}
