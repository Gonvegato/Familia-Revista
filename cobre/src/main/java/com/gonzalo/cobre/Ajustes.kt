package com.gonzalo.cobre

import android.content.Context
import com.gonzalo.cobre.EspejoCuenta.Tipo

/*
 * Todo lo que eliges en Cobre es tuyo, no de este dispositivo: viaja con la cuenta, en `ajustes/cobre_<grupo>`, un campo por ajuste
 * ([EspejoCuenta], como en Mostaza, Hiedra, Marfil y Azabache). Lo de un solo dispositivo (su id, las cachés de las listas y de la guía)
 * se queda en él.
 */

/** El reproductor. */
object AjustesReproductor {
    enum class Aspecto(val nombre: String) { AJUSTAR("Ajustar"), LLENAR("Llenar"), ZOOM("Zoom"), ANCHO("16:9"), ESTRECHO("4:3") }

    val GRUPO = EspejoCuenta.Grupo("cobre_reproductor", "cobre_reproductor", { k ->
        when (k) { "aspecto", "subs", "audio", "externo" -> Tipo.TEXTO; "salto" -> Tipo.ENTERO; "ahorro", "reconectar", "siguiente", "invertirCanales", "segundoPlano" -> Tipo.SI_NO; else -> null }
    })
    private fun p(c: Context) = EspejoCuenta.prefs(c, GRUPO)
    fun aspecto(c: Context) = Aspecto.entries.firstOrNull { it.name == p(c).getString("aspecto", null) } ?: Aspecto.AJUSTAR
    fun ponAspecto(c: Context, a: Aspecto) = p(c).edit().putString("aspecto", a.name).apply()
    /** El idioma de los subtítulos («spa», «eng»…; "" = sin ellos). */
    fun subs(c: Context) = p(c).getString("subs", "").orEmpty()
    fun ponSubs(c: Context, v: String) = p(c).edit().putString("subs", v).apply()
    /** El idioma de audio preferido cuando un canal o una película trae varios. */
    fun audio(c: Context) = p(c).getString("audio", "spa").orEmpty()
    fun ponAudio(c: Context, v: String) = p(c).edit().putString("audio", v).apply()
    /** Con datos del móvil (o una wifi con límite), la calidad más baja que dé el canal. */
    fun ahorro(c: Context) = p(c).getBoolean("ahorro", true)
    fun ponAhorro(c: Context, v: Boolean) = p(c).edit().putBoolean("ahorro", v).apply()
    /** Si se corta un canal, se vuelve a conectar solo. */
    fun reconectar(c: Context) = p(c).getBoolean("reconectar", true)
    fun ponReconectar(c: Context, v: Boolean) = p(c).edit().putBoolean("reconectar", v).apply()
    /** Al terminar un episodio, el siguiente solo. */
    fun siguiente(c: Context) = p(c).getBoolean("siguiente", true)
    fun ponSiguiente(c: Context, v: Boolean) = p(c).edit().putBoolean("siguiente", v).apply()
    /** Arriba en el mando es el canal anterior (como en muchas teles) en vez del siguiente. */
    fun invertirCanales(c: Context) = p(c).getBoolean("invertirCanales", false)
    fun ponInvertirCanales(c: Context, v: Boolean) = p(c).edit().putBoolean("invertirCanales", v).apply()
    /** Cuánto salta adelante o atrás en una película (segundos). */
    fun salto(c: Context) = p(c).getInt("salto", 10).coerceIn(5, 60)
    fun ponSalto(c: Context, v: Int) = p(c).edit().putInt("salto", v).apply()
    /** «Abrir con otro reproductor»: el paquete del que usas siempre ("" = preguntar). Es de Android: Cobre del ordenador no lo lee. */
    fun externo(c: Context) = p(c).getString("externo", "").orEmpty()
    fun ponExterno(c: Context, v: String) = p(c).edit().putString("externo", v).apply()
    /** «Seguir sonando al salir»: al salir del reproductor (inicio, otra app, la pantalla apagada) el sonido sigue. Apagado de serie; en la tele no se usa. */
    fun segundoPlano(c: Context) = p(c).getBoolean("segundoPlano", false)
    fun ponSegundoPlano(c: Context, v: Boolean) = p(c).edit().putBoolean("segundoPlano", v).apply()
}

/** La guía: cada cuánto se baja y cuántos días. */
object AjustesGuia {
    val GRUPO = EspejoCuenta.Grupo("cobre_guia", "cobre_guia", { k -> when (k) { "horas", "dias" -> Tipo.ENTERO; "soloWifi" -> Tipo.SI_NO; else -> null } })
    private fun p(c: Context) = EspejoCuenta.prefs(c, GRUPO)
    fun horas(c: Context) = p(c).getInt("horas", 12).coerceIn(3, 72)
    fun ponHoras(c: Context, v: Int) = p(c).edit().putInt("horas", v).apply()
    fun dias(c: Context) = p(c).getInt("dias", 4).coerceIn(1, 7)
    fun ponDias(c: Context, v: Int) = p(c).edit().putInt("dias", v).apply()
    /** La guía pesa (a veces más de 100 MB): con datos del móvil, solo si la pides tú. */
    fun soloWifi(c: Context) = p(c).getBoolean("soloWifi", true)
    fun ponSoloWifi(c: Context, v: Boolean) = p(c).edit().putBoolean("soloWifi", v).apply()
}

/** Los canales: su orden, dónde estabas y qué hacer al abrir. */
object AjustesDirecto {
    enum class Orden(val nombre: String) { LISTA("Como la lista"), NUMERO("Por número"), NOMBRE("Por nombre") }
    val GRUPO = EspejoCuenta.Grupo("cobre_directo", "cobre_directo", { k -> when (k) { "orden", "lista", "grupo" -> Tipo.TEXTO; "alAbrir", "videoclub" -> Tipo.SI_NO; else -> null } })
    private fun p(c: Context) = EspejoCuenta.prefs(c, GRUPO)
    fun orden(c: Context) = Orden.entries.firstOrNull { it.name == p(c).getString("orden", null) } ?: Orden.LISTA
    fun ponOrden(c: Context, o: Orden) = p(c).edit().putString("orden", o.name).apply()
    /** La lista y el grupo que estabas mirando (sus ids, sin nada secreto). */
    fun lista(c: Context) = p(c).getString("lista", "").orEmpty()
    fun grupo(c: Context) = p(c).getString("grupo", "").orEmpty()
    fun ponDonde(c: Context, lista: String, grupo: String) = p(c).edit().putString("lista", lista).putString("grupo", grupo).apply()
    /** Al abrir Cobre, el último canal que viste (como una tele). */
    fun alAbrir(c: Context) = p(c).getBoolean("alAbrir", false)
    fun ponAlAbrir(c: Context, v: Boolean) = p(c).edit().putBoolean("alAbrir", v).apply()
    /**
     * «Pelis y series del proveedor»: el videoclub de Xtream en el menú, en la portada y en «Buscar» (y se baja al poner al día la lista).
     * Apagado de serie: Nut es sobre todo la tele y el fútbol. El código sigue ahí ([PantVideoclub]); se vuelve a ver encendiéndolo.
     */
    fun videoclub(c: Context) = p(c).getBoolean("videoclub", false)
    fun ponVideoclub(c: Context, v: Boolean) = p(c).edit().putBoolean("videoclub", v).apply()
}

/** Las secciones del menú de la portada, en su orden: el fútbol lo primero; el videoclub, solo con Xtream y si lo enciendes. */
fun seccionesMenu(hayXtream: Boolean, videoclub: Boolean): List<Pair<String, Destino>> =
    listOf("Fútbol" to Destino.Futbol, "Favoritos" to Destino.Favoritos, "Directo" to Destino.Directo, "Guía" to Destino.Guia) +
        (if (hayXtream && videoclub) listOf("Películas" to Destino.Peliculas, "Series" to Destino.Series) else emptyList()) + listOf("Listas" to Destino.Listas)
