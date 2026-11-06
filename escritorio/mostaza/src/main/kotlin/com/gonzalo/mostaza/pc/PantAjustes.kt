package com.gonzalo.mostaza.pc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.AjustesCuenta as AjustesCuentaFamilia
import com.gonzalo.familia.pc.AjustesVersion
import com.gonzalo.familia.pc.Sitio
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ajustes (como los del móvil y los de las hermanas del ordenador): lo que eliges viaja con la cuenta (`ajustes/mostaza_*`, los mismos campos
 * que el móvil); lo que es de este ordenador (mpv a pantalla completa, lo que puede ocupar la caché de torrents, lo medido de la conexión)
 * se dice que es de aquí.
 */
@Composable
fun PantAjustes(nav: Nav) {
    var pestana by remember { mutableStateOf(0) }
    Pagina(ancho = 900.dp) {
        Cabecera(84.sp, texto = "AJUSTES")
        FileteCabecera()
        Spacer(Modifier.height(14.dp))
        PestanasRevista(listOf("Catálogo", "Fuentes", "Reproductor", "Cuenta", "Apariencia", "Versión"), pestana) { pestana = it }
        Spacer(Modifier.height(22.dp))
        when (pestana) {
            0 -> AjustesCatalogo(nav)
            1 -> AjustesFuentes(nav)
            2 -> AjustesDelReproductor()
            3 -> {
                AjustesCuentaFamilia("mostaza")
                Spacer(Modifier.height(18.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
                Antetitulo("Las hermanas", Modifier.padding(bottom = 4.dp))
                Cuerpo("Azabache valora lo que ves aquí (la nota y la reseña van en la misma ficha de tu cuenta). Lo que ves aquí, lo sigues en el móvil y al revés: tu biblioteca, por dónde vas de cada película y episodio, el tiempo visto, tus addons y lo que eliges van con la cuenta.", tam = 13)
            }
            4 -> {
                Antetitulo("Apariencia")
                Spacer(Modifier.height(6.dp))
                Cuerpo("Mostaza del ordenador tiene la apariencia de tu Mostaza del móvil (viaja con la cuenta). Aquí puedes elegir claro u oscuro.", tam = 14)
                Spacer(Modifier.height(10.dp))
                MagChoice(listOf("Como el sistema", "Claro", "Oscuro"), Modo.eleccion) { Modo.eleccion = it; Apariencia.guarda() }
            }
            else -> AjustesVersion("mostaza", "Mostaza")
        }
    }
}

/** «Catálogo»: tu clave de TMDB (va con tu cuenta, en `/claves`), cómo sacarla, y lo que TMDB y JustWatch piden que se diga. */
@Composable
private fun AjustesCatalogo(nav: Nav) {
    val ambito = rememberCoroutineScope()
    val puesta = Tmdb.activo
    var texto by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf<String?>(null) }
    var probando by remember { mutableStateOf(false) }
    Antetitulo("El catálogo de Mostaza", Modifier.padding(bottom = 4.dp))
    Cuerpo(if (puesta) "Con TMDB: la portada, «Explorar», «Buscar» y las fichas son las de una plataforma (tendencias, estrenos, géneros, lo que hay en cada plataforma, reparto, tráileres, en castellano). Tus addons quedan para las fuentes."
        else "Sin TMDB, lo que ves es lo de los catálogos de tus addons. Con tu clave de TMDB (gratis), Mostaza tiene su propio catálogo, como una plataforma.", color = Charcoal, tam = 14)
    Spacer(Modifier.height(8.dp))
    Etiqueta(if (puesta) "Tu clave está puesta (en tu cuenta, solo la puedes leer tú; la misma que en el móvil)" else "Cómo sacar tu clave")
    if (!puesta) Cuerpo("1. Hazte una cuenta en themoviedb.org.  2. En tu perfil → Ajustes → API, pide una clave (uso personal).  3. Copia el «Token de lectura de la API» (el largo, empieza por «eyJ») y pégalo aquí.", tam = 12)
    Spacer(Modifier.height(6.dp))
    CampoRevista("Token", texto, if (puesta) "Pegar otra para cambiarla" else "eyJhbGciOi…", secreto = true) { texto = it; aviso = null }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        EnlaceRevista(if (probando) "Probando…" else "Guardar", acento = true) {
            val t = texto.trim(); if (t.isBlank() || probando) return@EnlaceRevista
            probando = true
            ambito.launch {
                val r = Tmdb.prueba(t)
                probando = false
                if (r == null) { withContext(Dispatchers.IO) { Tmdb.guarda(t) }; texto = ""; aviso = "Hecho: el catálogo ya es el de TMDB, aquí y en tus otros dispositivos." } else aviso = r
            }
        }
        if (puesta) EnlaceRevista("Quitarla") { Tmdb.guarda(""); aviso = "Quitada: vuelven los catálogos de tus addons." }
    }
    aviso?.let { Nota(it, Modifier.padding(top = 8.dp)) }
    if (!puesta) {
        Spacer(Modifier.height(18.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
        Antetitulo("La portada sin TMDB", Modifier.padding(bottom = 4.dp))
        val cuantas = listOf(2, 4, 6, 8, 12)
        MagChoice(cuantas.map { "$it" }, cuantas.indexOf(AjustesPortada.cuantas()).coerceAtLeast(1), "Cuántos catálogos de tus addons") { i -> AjustesPortada.ponCuantas(cuantas[i]) }
        Cuerpo("Los primeros de tus addons, en su orden (los mismos que en el móvil).", tam = 12)
    }
    Spacer(Modifier.height(12.dp)); Filete(1.dp, Line); Spacer(Modifier.height(8.dp))
    Text(Tmdb.AVISO_TMDB + " Lo de dónde ver cada cosa es de JustWatch.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 12.sp, lineHeight = 17.sp)
}

/** «Fuentes»: el elector (tu orden de idiomas, la calidad…), la conexión de aquí y los torrents. */
@Composable
private fun AjustesFuentes(nav: Nav) {
    val p = AjustesElector.prefs()
    fun pon(n: PrefsElector) = AjustesElector.pon(n)
    Cuerpo("Al pulsar «Reproducir», Mostaza mira las fuentes de todos tus addons y pone la mejor para ti: primero tu idioma (en el orden de abajo), luego la mejor calidad que cabe en tu conexión, luego las directas o de tu debrid antes que los torrents, y por último el orden de tus addons. La lista entera está a un clic («Cambiar»). Es lo mismo que en el móvil (viaja con tu cuenta).", tam = 13)
    Fila("Elegir sola", "Si no, «Reproducir» te enseña la lista.") { Interruptor(AjustesElector.auto()) { AjustesElector.ponAuto(it) } }
    Spacer(Modifier.height(8.dp)); Antetitulo("Idiomas, por orden", Modifier.padding(bottom = 4.dp))
    val todos = PrefsElector.IDIOMAS.map { it.first }
    val orden = p.idiomas + todos.filter { it !in p.idiomas }
    orden.forEachIndexed { i, id ->
        val on = id in p.idiomas
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (on) "${p.idiomas.indexOf(id) + 1}" else "·", color = if (on) Acento else Muted, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.width(32.dp))
            Text(PrefsElector.nombre(id), color = if (on) Charcoal else Muted, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (on && p.idiomas.indexOf(id) > 0) EnlaceRevista("↑") { val l = p.idiomas.toMutableList(); val k = l.indexOf(id); l.removeAt(k); l.add(k - 1, id); pon(p.copy(idiomas = l)) }
            Spacer(Modifier.width(10.dp))
            EnlaceRevista(if (on) "Quitar" else "Poner", acento = !on) { pon(p.copy(idiomas = if (on) (p.idiomas - id).ifEmpty { listOf("castellano") } else p.idiomas + id)) }
        }
        if (i < orden.size - 1) Filete(1.dp, Line)
    }
    Fila("Vale con subtítulos", "Una fuente sin audio en español, con subtítulos (de la fuente o de tus addons de subtítulos).") { Interruptor(p.subtitulados) { pon(p.copy(subtitulados = it)) } }
    val maximas = listOf(2160 to "4K", 1080 to "1080p", 720 to "720p", 480 to "SD")
    MagChoice(maximas.map { it.second }, maximas.indexOfFirst { it.first == p.maxima }.coerceAtLeast(0), "Calidad máxima") { i -> pon(p.copy(maxima = maximas[i].first)) }
    Fila("Directas antes que torrents", "Las de tu debrid (o un enlace directo) se ponen al momento.") { Interruptor(p.directasPrimero) { pon(p.copy(directasPrimero = it)) } }
    Cuerpo("(«Ahorrar datos del móvil» es del móvil: un ordenador no tiene datos móviles.)", tam = 12)
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Tu conexión, aquí", Modifier.padding(bottom = 4.dp))
    val red = Conexion.red()
    MagFact("Medido al ver", if (red.kbps > 0) String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f Mbit/s", red.kbps / 1000.0) else "aún no se sabe")
    MagFact("Este ordenador", "con mpv: hasta 4K · HEVC · AV1 · HDR · Dolby Vision")
    Cuerpo("Lo de la conexión es de este ordenador (no viaja): se mide al ver y baja en cuanto algo se corta.", tam = 12)
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Torrents", Modifier.padding(bottom = 4.dp))
    val hay by produceState<Boolean?>(null) { value = withContext(Dispatchers.IO) { Torrents.disponible } }
    Cuerpo(when (hay) {
        null -> "Mirando el motor de torrents…"
        true -> "Con el motor de torrents de la familia (el mismo que el del móvil): se ve mientras se baja. Si tienes un debrid (TorBox, Real-Debrid…) puesto en tu addon, sus fuentes directas van antes y se ponen al momento."
        false -> "El motor de torrents no se ha podido poner en marcha en este ordenador (va en Linux con procesador x86-64 y en los Mac con Apple Silicon): se ven las fuentes directas o de tu debrid, y los torrents salen en la lista pero no se eligen solos."
    }, tam = 12)
    Fila("Compartir mientras ves", "Lo que bajas se comparte con otros mientras lo ves (es como van los torrents).") {
        Interruptor(AjustesTorrents.compartir()) { AjustesTorrents.pon(AjustesTorrents.subida(), AjustesTorrents.bajada(), it); Torrents.ajustesCambiados() }
    }
    val subidas = listOf(0 to "Sin límite", 100 to "100 KB/s", 500 to "500 KB/s", 1000 to "1 MB/s")
    MagChoice(subidas.map { it.second }, subidas.indexOfFirst { it.first == AjustesTorrents.subida() }.coerceAtLeast(0), "Subida") { i -> AjustesTorrents.pon(subidas[i].first, AjustesTorrents.bajada(), AjustesTorrents.compartir()); Torrents.ajustesCambiados() }
    val bajadas = listOf(0 to "Sin límite", 2000 to "2 MB/s", 5000 to "5 MB/s", 10000 to "10 MB/s")
    MagChoice(bajadas.map { it.second }, bajadas.indexOfFirst { it.first == AjustesTorrents.bajada() }.coerceAtLeast(0), "Bajada") { i -> AjustesTorrents.pon(AjustesTorrents.subida(), bajadas[i].first, AjustesTorrents.compartir()); Torrents.ajustesCambiados() }
    var cache by remember { mutableStateOf(Torrents.cacheMB()) }
    val caches = listOf(1024 to "1 GB", 4096 to "4 GB", 8192 to "8 GB", 16384 to "16 GB")
    MagChoice(caches.map { it.second }, caches.indexOfFirst { it.first == cache }.coerceAtLeast(1), "Lo que puede ocupar aquí (de este ordenador)") { i -> cache = caches[i].first; Torrents.ponCacheMB(cache) }
    Spacer(Modifier.height(12.dp)); Filete(2.dp)
    MagLink("Tus addons", "Instalar, ordenar, configurar o quitar", accent = true) { nav.ir(Pantalla.Addons) }
}

/** «Reproductor»: mpv (si está y cómo ponerlo), los subtítulos y el audio (viajan), y lo de mientras ves. */
@Composable
private fun AjustesDelReproductor() {
    val mpv = remember { Mpv.busca() }
    Antetitulo("mpv", Modifier.padding(bottom = 4.dp))
    if (mpv != null) {
        MagFact("Reproductor", mpv.absolutePath)
        Cuerpo("Mostaza abre las películas y los episodios con mpv, en su ventana, y desde aquí sabe por dónde vas (como en el móvil: lo visto, el tiempo, seguir donde lo dejaste). En la ventana de mpv: espacio para pausa, flechas para saltar, «f» pantalla completa, «#» cambia el audio, «j» los subtítulos y «q» cierra.", tam = 13)
    } else {
        Cuerpo(Mpv.comoInstalar(), color = Acento, tam = 14)
        Cuerpo("Sin mpv también se puede abrir una fuente con el reproductor del sistema, pero así no se sabe por dónde vas (ni entra en tu biblioteca, ni se apunta lo visto).", tam = 12)
    }
    Fila("A pantalla completa", "Al abrir mpv (de este ordenador: no viaja).") { var v by remember { mutableStateOf(PantallaCompleta.si()) }; Interruptor(v) { v = it; PantallaCompleta.pon(it) } }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Subtítulos", Modifier.padding(bottom = 4.dp))
    val subs = listOf("spa" to "Español", "eng" to "Inglés", "" to "Ninguno")
    MagChoice(subs.map { it.second }, subs.indexOfFirst { it.first == AjustesReproductor.subs() }.coerceAtLeast(0), "Idioma") { i -> AjustesReproductor.ponSubs(subs[i].first) }
    Fila("Siempre", "Aunque el audio ya sea en tu idioma (si no, solo cuando no lo es).") { Interruptor(AjustesReproductor.subsSiempre()) { AjustesReproductor.ponSubsSiempre(it) } }
    val tams = listOf(75, 100, 125, 150, 200)
    MagChoice(tams.map { "$it %" }, tams.indexOf(AjustesReproductor.subTam()).coerceAtLeast(1), "Tamaño") { i -> AjustesReproductor.ponSubTam(tams[i]) }
    Fila("Sobre una banda oscura", "Si no, letra blanca con borde.") { Interruptor(AjustesReproductor.subFondo()) { AjustesReproductor.ponSubFondo(it) } }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Audio", Modifier.padding(bottom = 4.dp))
    val audios = listOf("spa" to "Español", "eng" to "Inglés", "" to "El original")
    MagChoice(audios.map { it.second }, audios.indexOfFirst { it.first == AjustesReproductor.audio() }.coerceAtLeast(0), "Si la fuente trae varios") { i -> AjustesReproductor.ponAudio(audios[i].first) }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Mientras ves", Modifier.padding(bottom = 4.dp))
    Fila("El episodio siguiente, solo", "Al terminar uno, el siguiente empieza en 8 segundos (con la fuente de la misma clase).") { Interruptor(AjustesReproductor.siguiente()) { AjustesReproductor.ponSiguiente(it) } }
    val saltos = listOf(5, 10, 15, 30)
    MagChoice(saltos.map { "$it s" }, saltos.indexOf(AjustesReproductor.salto()).coerceAtLeast(1), "Saltar (desde la banda de abajo y con el mando)") { i -> AjustesReproductor.ponSalto(saltos[i]) }
    Cuerpo("Lo de los subtítulos y el audio se le dice a mpv al abrirlo (si lo cambias con algo puesto, vale para lo siguiente).", tam = 12)
    if (Sitio.sistema == Sitio.Sistema.MAC) Cuerpo("En el Mac vale el mpv de Homebrew. IINA (que lleva mpv dentro) no deja que Mostaza sepa por dónde vas: con ella no se apuntaría nada.", tam = 12)
}
