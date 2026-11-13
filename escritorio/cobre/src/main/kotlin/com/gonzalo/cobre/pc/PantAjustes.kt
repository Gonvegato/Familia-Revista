package com.gonzalo.cobre.pc

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
import com.gonzalo.iptv.DatosCuenta
import com.gonzalo.iptv.IptvError
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Perfil
import com.gonzalo.iptv.Secretos
import com.gonzalo.iptv.Servidor
import com.gonzalo.iptv.TipoLista
import com.gonzalo.iptv.Xtream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ajustes (como los del móvil y los de las hermanas del ordenador): tus listas (en la parte secreta de tu cuenta), la guía, el reproductor,
 * la cuenta, la apariencia y la versión. Lo que eliges viaja con la cuenta (`ajustes/cobre_*`, los mismos campos que el móvil); lo que es de
 * este ordenador (mpv a pantalla completa) se dice que es de aquí.
 */
@Composable
fun PantAjustes(nav: Nav, inicial: Int = 0, editar: Lista? = null) {
    var pestana by remember { mutableStateOf(inicial) }
    Pagina(ancho = 900.dp) {
        EnlaceCabecera("← Volver", Charcoal, atajo = "esc") { nav.volver() }
        NombreConPunto("Ajustes", 72.sp, Charcoal, Modifier.padding(top = 10.dp, bottom = 6.dp))
        Filete(3.dp)
        Spacer(Modifier.height(14.dp))
        PestanasRevista(listOf("Listas", "Guía", "Reproductor", "Cuenta", "Apariencia", "Versión"), pestana) { pestana = it }
        Spacer(Modifier.height(22.dp))
        when (pestana) {
            0 -> AjustesListas(nav, editar)
            1 -> AjustesDeLaGuia(nav)
            2 -> AjustesDelReproductor()
            3 -> {
                AjustesCuentaFamilia("cobre")
                Spacer(Modifier.height(18.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
                Antetitulo("Lo de Nut en tu cuenta", Modifier.padding(bottom = 4.dp))
                Cuerpo("Tus listas van en la parte secreta de tu cuenta (ni tu pareja la ve). Tus favoritos y su orden, los grupos que escondes, el último canal, lo visto de cada película y episodio y lo que eliges van también con la cuenta: lo que haces aquí lo sigues en el móvil, en la tablet y en la tele, y al revés. Los canales, el videoclub y la guía se bajan en cada dispositivo (aquí, cifrados con tu llavero).", tam = 13)
            }
            4 -> {
                Antetitulo("Apariencia")
                Spacer(Modifier.height(6.dp))
                Cuerpo("Nut del ordenador tiene la apariencia de tu Nut del móvil (viaja con la cuenta). Aquí puedes elegir claro u oscuro y el acento.", tam = 14)
                Spacer(Modifier.height(10.dp))
                MagChoice(listOf("Como el sistema", "Claro", "Oscuro"), Modo.eleccion) { Modo.eleccion = it; Apariencia.guarda() }
                Spacer(Modifier.height(10.dp))
                Etiqueta("Acento")
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (listOf("paleta" to "El de la paleta") + ACCENT_COLORS.map { it.id to it.name }).forEach { (id, n) -> FiltroRevista(n, Skin.accentId == id) { Skin.accentId = id; Apariencia.guarda() } }
                }
            }
            else -> AjustesVersion("cobre", "Nut")
        }
    }
}

/** Cómo se enseña una lista sin enseñar sus secretos: el servidor y dos letras del usuario; de una dirección M3U, solo su servidor. */
fun descripcion(l: Lista): String = Tele.tipoNombre(l.tipo) + when (l.tipo) {
    TipoLista.XTREAM -> " · " + Servidor.normaliza(l.url).substringAfter("://") + " · " + l.usuario.take(2) + "…"
    TipoLista.M3U -> " · " + Secretos.tapa(l.url, l.secretos.filter { it != l.url }).substringAfter("://").substringBefore('/').substringAfter('@') + "/…"
    TipoLista.ARCHIVO -> if (l.dispositivo == Local.dispositivo) " · en este ordenador" else " · en otro dispositivo"
}

/** «Listas»: las tuyas, cómo van, ponerlas al día, cambiarlas, ordenarlas y quitarlas; y añadir otra. */
@Composable
private fun AjustesListas(nav: Nav, editar: Lista?) {
    Tele.version
    var editando by remember { mutableStateOf(editar) }
    var quitar by remember { mutableStateOf<Lista?>(null) }
    val listas = Listas.lista()
    val e = editando
    if (e != null) { FormularioLista(e) { editando = null }; return }
    Cuerpo("Tus listas van en tu cuenta, en su parte secreta (ni tu pareja la ve): las tienes en todos tus dispositivos. Los canales, el videoclub y la guía se bajan en cada uno (no van a la cuenta). Nut no trae ninguna lista ni ningún canal: los pones tú.", tam = 13)
    Spacer(Modifier.height(10.dp))
    BotonAcento("Añadir una lista") { editando = Lista(DatosCuenta.idNuevo(), "", TipoLista.XTREAM, creada = System.currentTimeMillis()) }
    Spacer(Modifier.height(18.dp))
    if (listas.isEmpty()) Nota("Aún no tienes ninguna.")
    listas.forEachIndexed { i, l ->
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Etiqueta(descripcion(l))
                }
                if (i > 0) EnlaceRevista("↑") { Listas.mueve(l.id, -1) }
            }
            val est = Tele.almacen.estado(l, "canales"); val g = Tele.almacen.estado(l, "guia")
            val estado = Tele.ocupada[l.id] ?: Tele.errores[l.id]?.let { "No se ha podido: $it" } ?: if (est.cuando > 0) "${Formato.cifra(est.cuantos)} canales · ${Formato.fecha(est.cuando)}" + (if (g.cuando > 0) " · guía: ${Formato.cifra(g.cuantos)} programas" else "") else "Sin bajar todavía"
            Text(estado, color = if (Tele.errores.containsKey(l.id)) Acento else Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            if (g.error.isNotBlank() && !Tele.ocupada.containsKey(l.id)) Text("Guía: ${g.error}", color = Muted, fontFamily = Body, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (l.tipo != TipoLista.ARCHIVO || l.dispositivo == Local.dispositivo) EnlaceRevista(if (Tele.ocupada.containsKey(l.id)) "Actualizando…" else "Poner al día", acento = true) { Tele.actualiza(l, todo = true) }
                EnlaceRevista("Cambiar") { editando = l }
                EnlaceRevista("Quitar") { quitar = l }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    quitar?.let { l ->
        Panel {
            Text("¿Quitar «${l.nombre}»?", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Cuerpo("Se quita de tu cuenta (en todos tus dispositivos) con lo que se ha bajado aquí. Tus favoritos y lo visto de esa lista se quedan en tu cuenta por si la vuelves a poner.")
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                EnlaceRevista("Sí, quitarla", acento = true) { Listas.quita(l.id); Tele.olvida(l); quitar = null }
                EnlaceRevista("No") { quitar = null }
            }
        }
    }
}

/** Lo que dice tu servidor de Xtream de tu cuenta (al comprobarla). */
private fun perfilTexto(p: Perfil): String = listOfNotNull(
    "Cuenta: ${if (p.activo) "activa" else p.estado.ifBlank { "no activa" }}",
    p.caduca.takeIf { it > 0 }?.let { "caduca el ${Formato.fecha(it)}" },
    p.conexionesMax.takeIf { it > 0 }?.let { "${p.conexionesActivas} de $it conexiones en uso" },
    if (p.prueba) "de prueba" else null,
).joinToString(" · ")

/** Añadir o cambiar una lista. */
@Composable
private fun FormularioLista(inicial: Lista, alAcabar: () -> Unit) {
    val ambito = rememberCoroutineScope()
    val nueva = Listas.por(inicial.id) == null
    var tipo by remember { mutableStateOf(inicial.tipo) }
    var nombre by remember { mutableStateOf(inicial.nombre) }
    var url by remember { mutableStateOf(inicial.url) }
    var usuario by remember { mutableStateOf(inicial.usuario) }
    var clave by remember { mutableStateOf(inicial.contrasena) }
    var epg by remember { mutableStateOf(inicial.epg) }
    var agente by remember { mutableStateOf(inicial.agente) }
    var formato by remember { mutableStateOf(inicial.formato) }
    var archivo by remember { mutableStateOf<File?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    fun lista() = Lista(inicial.id, nombre.trim().ifBlank { when (tipo) { TipoLista.XTREAM -> Servidor.normaliza(url).substringAfter("://").substringBefore(':'); TipoLista.ARCHIVO -> archivo?.nameWithoutExtension ?: "Mi lista"; else -> "Mi lista" } }, tipo,
        if (tipo == TipoLista.XTREAM) Servidor.normaliza(url) else url.trim(), usuario.trim(), clave, epg.trim(), agente.trim(),
        if (tipo == TipoLista.ARCHIVO) (inicial.dispositivo.ifBlank { Local.dispositivo }) else "", formato, inicial.creada)

    Antetitulo(if (nueva) "Añadir una lista" else "Cambiar «${inicial.nombre}»", Modifier.padding(bottom = 6.dp))
    val tipos = listOf(TipoLista.XTREAM, TipoLista.M3U, TipoLista.ARCHIVO)
    MagChoice(tipos.map { Tele.tipoNombre(it) }, tipos.indexOf(tipo), "De dónde") { tipo = tipos[it]; aviso = null }
    Spacer(Modifier.height(6.dp))
    CampoRevista("Nombre", nombre, "cómo la llamas") { nombre = it }
    Spacer(Modifier.height(6.dp))
    when (tipo) {
        TipoLista.XTREAM -> {
            CampoRevista("Servidor", url, "http://servidor:puerto") { url = it }
            Spacer(Modifier.height(6.dp))
            CampoRevista("Usuario", usuario, "tu usuario", secreto = true) { usuario = it }
            Spacer(Modifier.height(6.dp))
            CampoRevista("Contraseña", clave, "tu contraseña", secreto = true) { clave = it }
            MagChoice(listOf("MPEG-TS (ts)", "HLS (m3u8)"), if (formato == "m3u8") 1 else 0, "Los canales en directo, como") { formato = if (it == 1) "m3u8" else "ts" }
            Cuerpo("Si con uno los canales se cortan o no se ven, prueba con el otro.", tam = 12)
        }
        TipoLista.M3U -> {
            CampoRevista("Dirección", url, "http://…/lista.m3u", secreto = true) { url = it }
            Servidor.deEnlace(url)?.let { (s, u, p) ->
                Spacer(Modifier.height(6.dp))
                MagLink("Es la lista de un servidor de Xtream: añadirlo como login", "Así tienes también sus películas, sus series y su guía", accent = true) { tipo = TipoLista.XTREAM; url = s; usuario = u; clave = p }
            }
        }
        TipoLista.ARCHIVO -> {
            Spacer(Modifier.height(8.dp))
            MagLink(if (archivo != null || Tele.almacen.tieneArchivo(inicial)) "Elegir otro archivo" else "Elegir el archivo M3U", "Se queda en este ordenador (cifrado); en los demás, esta lista sale como «en otro dispositivo»") {
                ambito.launch { withContext(Dispatchers.IO) { com.gonzalo.familia.pc.revista.eligeArchivo("La lista M3U", listOf("m3u", "m3u8", "txt")) }?.let { archivo = it; if (nombre.isBlank()) nombre = it.nameWithoutExtension } }
            }
            archivo?.let { Etiqueta("Elegido: ${it.name}") }
            if (inicial.dispositivo.isNotBlank() && inicial.dispositivo != Local.dispositivo) Cuerpo("Esta lista es un archivo de otro dispositivo: si eliges aquí el archivo, pasa a ser de este ordenador.", tam = 12)
        }
    }
    Spacer(Modifier.height(10.dp))
    Etiqueta("Si hace falta")
    CampoRevista("Guía XMLTV", epg, "una dirección .xml o .xml.gz (opcional)", secreto = true) { epg = it }
    Spacer(Modifier.height(6.dp))
    CampoRevista("User-Agent", agente, "el que pida tu proveedor (opcional)") { agente = it }
    Spacer(Modifier.height(12.dp))
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp)) }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        BotonAcento(if (ocupado) "Comprobando…" else "Guardar") {
            if (ocupado) return@BotonAcento
            val l0 = lista()
            val l = if (l0.tipo == TipoLista.ARCHIVO && archivo != null) l0.copy(dispositivo = Local.dispositivo) else l0
            when {
                l.tipo == TipoLista.XTREAM && (l.url.isBlank() || l.usuario.isBlank() || l.contrasena.isBlank()) -> { aviso = "Faltan el servidor, el usuario o la contraseña."; return@BotonAcento }
                l.tipo == TipoLista.M3U && !(l.url.startsWith("http://", true) || l.url.startsWith("https://", true)) -> { aviso = "La dirección tiene que empezar por http:// o https://."; return@BotonAcento }
                l.tipo == TipoLista.ARCHIVO && archivo == null && !Tele.almacen.tieneArchivo(l) -> { aviso = "Elige el archivo."; return@BotonAcento }
            }
            ocupado = true; aviso = null
            ambito.launch {
                val r = withContext(Dispatchers.IO) {
                    try {
                        // Un login de Xtream se comprueba antes de guardarlo (así un error de escritura no se queda en tu cuenta)
                        if (l.tipo == TipoLista.XTREAM) {
                            val p = Xtream(l, Tele.red).perfil()
                            if (!p.activo) return@withContext "El servidor dice que tu cuenta está «${p.estado}»."
                        }
                        archivo?.let { f -> f.inputStream().use { Tele.almacen.guardaArchivo(l, it) } }
                        null
                    } catch (e: IptvError) { Secretos.tapa(e.message.orEmpty(), l.secretos) } catch (e: Exception) { "No se ha podido comprobar (sin conexión)." }
                }
                ocupado = false
                if (r != null) { aviso = r; return@launch }
                Listas.pon(l)
                AjustesDirecto.ponDonde(l.id, "")
                Tele.suelta(l)
                Tele.actualiza(l, todo = true)
                alAcabar()
            }
        }
        if (tipo == TipoLista.XTREAM) BotonSuave("Comprobar") {
            val l = lista()
            if (l.url.isBlank() || l.usuario.isBlank() || l.contrasena.isBlank()) { aviso = "Faltan el servidor, el usuario o la contraseña."; return@BotonSuave }
            ambito.launch {
                aviso = "Comprobando…"
                aviso = withContext(Dispatchers.IO) {
                    try { perfilTexto(Xtream(l, Tele.red).perfil()) } catch (e: IptvError) { Secretos.tapa(e.message.orEmpty(), l.secretos) } catch (e: Exception) { "No se ha podido comprobar (sin conexión)." }
                }
            }
        }
        BotonSuave("Cancelar") { alAcabar() }
    }
}

/** «Guía»: cada cuánto se baja y cuántos días (viajan con la cuenta), y bajarla ya. */
@Composable
private fun AjustesDeLaGuia(nav: Nav) {
    Tele.version
    Cuerpo("La guía sale de lo que diga la lista (`url-tvg`), de la del servidor de Xtream o de la tuya (en la lista, «Guía XMLTV»). Se baja sola y se queda solo con los programas de tus canales.", tam = 13)
    val horas = listOf(6, 12, 24, 48)
    MagChoice(horas.map { "Cada $it h" }, horas.indexOf(AjustesGuia.horas()).coerceAtLeast(1), "Se vuelve a bajar") { AjustesGuia.ponHoras(horas[it]) }
    val dias = listOf(1, 2, 4, 7)
    MagChoice(dias.map { if (it == 1) "1 día" else "$it días" }, dias.indexOf(AjustesGuia.dias()).coerceAtLeast(2), "Días por delante") { AjustesGuia.ponDias(dias[it]) }
    Cuerpo("(«Solo con wifi» es del móvil: un ordenador no tiene datos móviles.)", tam = 12)
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Bajarla ahora", Modifier.padding(bottom = 4.dp))
    val ls = Listas.usables()
    if (ls.isEmpty()) Nota("Aún no tienes ninguna lista en este ordenador.")
    ls.forEach { l ->
        val g = Tele.almacen.estado(l, "guia")
        Fila(l.nombre, Tele.ocupada[l.id] ?: (if (g.cuando > 0) "${Formato.cifra(g.cuantos)} programas · ${Formato.fecha(g.cuando)} a las ${Formato.hora(g.cuando)}" else "Sin guía todavía") + (if (g.error.isNotBlank()) " · ${g.error}" else "")) {
            EnlaceRevista(if (Tele.ocupada.containsKey(l.id)) "Bajando…" else "Bajar", acento = true) { Tele.actualizaGuia(l) { e -> if (e != null) nav.aviso(e) } }
        }
        Filete(1.dp, Line)
    }
}

/** «Reproductor»: mpv (si está y cómo ponerlo), lo de los canales, la imagen, el audio y los subtítulos (viajan), y lo de este ordenador. */
@Composable
private fun AjustesDelReproductor() {
    val mpv = remember { Mpv.busca() }
    Antetitulo("mpv", Modifier.padding(bottom = 4.dp))
    if (mpv != null) {
        MagFact("Reproductor", mpv.absolutePath)
        Cuerpo("Nut pone los canales, las películas y los episodios en mpv, en su ventana. Allí: ↑ y ↓ (o Re Pág / Av Pág, o la cruceta del mando) cambian de canal, los números van a un canal, Intro (o A) abre la lista de canales con lo que echan, «i» dice qué se emite, Esc sale de la pantalla completa y «q» cierra. En una película: espacio para pausa, ← y → saltan y ↑ y ↓ saltan un minuto.", tam = 13)
    } else {
        Cuerpo(Mpv.comoInstalar(), color = Acento, tam = 14)
    }
    Fila("A pantalla completa", "Al abrir mpv (de este ordenador: no viaja).") { var v by remember { mutableStateOf(PantallaCompleta.si()) }; Interruptor(v) { v = it; PantallaCompleta.pon(it) } }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("En directo", Modifier.padding(bottom = 4.dp))
    Fila("Volver a conectar solo", "Si un canal se corta, Nut vuelve a conectar (hasta seis veces, esperando cada vez un poco más).") { Interruptor(AjustesReproductor.reconectar()) { AjustesReproductor.ponReconectar(it) } }
    Fila("Arriba, el canal anterior", "Como en muchas teles (si no, arriba es el siguiente).") { Interruptor(AjustesReproductor.invertirCanales()) { AjustesReproductor.ponInvertirCanales(it) } }
    val ordenes = AjustesDirecto.Orden.entries
    MagChoice(ordenes.map { it.nombre }, ordenes.indexOf(AjustesDirecto.orden()), "El orden de los canales") { AjustesDirecto.ponOrden(ordenes[it]) }
    Fila("Al abrir Nut, el último canal", "Como una tele.") { Interruptor(AjustesDirecto.alAbrir()) { AjustesDirecto.ponAlAbrir(it) } }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("La imagen, el audio y los subtítulos", Modifier.padding(bottom = 4.dp))
    val aspectos = AjustesReproductor.Aspecto.entries
    MagChoice(aspectos.map { it.nombre }, aspectos.indexOf(AjustesReproductor.aspecto()), "La imagen") { AjustesReproductor.ponAspecto(aspectos[it]) }
    val audios = listOf("spa" to "Español", "eng" to "Inglés", "cat" to "Catalán", "" to "El que venga")
    MagChoice(audios.map { it.second }, audios.indexOfFirst { it.first == AjustesReproductor.audio() }.coerceAtLeast(0), "Audio, si trae varios") { AjustesReproductor.ponAudio(audios[it].first) }
    val subs = listOf("" to "Sin subtítulos", "spa" to "Español", "eng" to "Inglés")
    MagChoice(subs.map { it.second }, subs.indexOfFirst { it.first == AjustesReproductor.subs() }.coerceAtLeast(0), "Subtítulos") { AjustesReproductor.ponSubs(subs[it].first) }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Películas y series", Modifier.padding(bottom = 4.dp))
    Fila("El episodio siguiente, solo", "Al terminar uno, el siguiente empieza en 8 segundos.") { Interruptor(AjustesReproductor.siguiente()) { AjustesReproductor.ponSiguiente(it) } }
    val saltos = listOf(5, 10, 15, 30)
    MagChoice(saltos.map { "$it s" }, saltos.indexOf(AjustesReproductor.salto()).coerceAtLeast(1), "Saltar (desde la banda de abajo)") { AjustesReproductor.ponSalto(saltos[it]) }
    Cuerpo("Lo de la imagen, el audio y los subtítulos se le dice a mpv con cada canal; lo de los idiomas, al abrirlo (si lo cambias con algo puesto, vale para lo siguiente). «Ahorrar datos» y «Abrir con otro reproductor» son del móvil.", tam = 12)
    if (Sitio.sistema == Sitio.Sistema.MAC) Cuerpo("En el Mac vale el mpv de Homebrew. IINA (que lleva mpv dentro) no deja que Nut le hable: con ella no se cambiaría de canal ni se apuntaría lo visto.", tam = 12)
}
