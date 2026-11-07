package com.gonzalo.iptv

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Cómo se guardan en el disco los archivos con secretos (los canales de una lista M3U llevan su dirección, que suele llevar tu usuario y tu
 * contraseña; el archivo M3U, igual). En Android, cifrados con una clave del almacén de claves del móvil; en las pruebas, tal cual.
 */
interface Envoltorio {
    fun escribe(out: OutputStream): OutputStream
    fun lee(inp: InputStream): InputStream
    object Nada : Envoltorio { override fun escribe(out: OutputStream) = out; override fun lee(inp: InputStream) = inp }
}

/** Cómo fue la última vez que se bajó algo de una lista: cuándo, cuántos y, si falló, por qué (sin secretos). */
data class Estado(val cuando: Long = 0L, val cuantos: Int = 0, val error: String = "") {
    override fun toString() = "Estado($cuando, $cuantos, $error)"
}

/**
 * Lo de cada lista en el dispositivo: sus canales, su videoclub (Xtream) y su guía. Todo es caché (se vuelve a bajar cuando hace falta) y
 * nada va a la cuenta: una lista puede tener decenas de miles de canales y una guía, cientos de megas. Se baja y se guarda a trozos, sin
 * tenerlo entero en memoria; los canales se leen enteros (para cambiar de canal al momento), las películas y las series se recorren en el
 * archivo cuando hacen falta (por categoría o al buscar).
 *
 * En [dir]/<lista>/: `canales.bin` (con [envoltorio]: lleva las direcciones), `lista.m3u` (las de archivo, también con [envoltorio]),
 * `peliculas.bin`, `series.bin` y `categorias_*.bin` (Xtream: sin direcciones ni secretos), `guia.bin` ([Guia]) y `estado_*.json`.
 */
class Almacen(private val dir: File, private val red: Red, private val envoltorio: Envoltorio = Envoltorio.Nada, private val reloj: () -> Long = System::currentTimeMillis) {
    private fun d(l: Lista) = File(dir, l.id).also { it.mkdirs() }
    private fun f(l: Lista, n: String) = File(d(l), n)

    /** Olvida todo lo de una lista (al quitarla). */
    fun olvida(l: Lista) { File(dir, l.id).deleteRecursively() }

    // ---------------------------------------------------------------- el estado de cada cosa

    fun estado(l: Lista, que: String): Estado {
        val m = runCatching { JsonFlujo.lee(f(l, "estado_$que.json").readText()).mapa() }.getOrNull() ?: return Estado()
        return Estado(m["cuando"].largo(), m["cuantos"].largo().toInt(), m["error"].txt())
    }
    private fun ponEstado(l: Lista, que: String, e: Estado) =
        f(l, "estado_$que.json").writeText(JsonFlujo.escribe(mapOf("cuando" to e.cuando, "cuantos" to e.cuantos, "error" to e.error)))

    /** Las guías que dice la cabecera de la lista M3U (pueden llevar tu usuario y tu contraseña: con [envoltorio], como los canales). */
    fun guiasDeLaLista(l: Lista): List<String> = runCatching {
        envoltorio.lee(java.io.FileInputStream(f(l, "guias.json"))).use { JsonFlujo.lee(it.readBytes().toString(Charsets.UTF_8)).lista().map { x -> x.txt() } }
    }.getOrDefault(emptyList())
    private fun ponGuias(l: Lista, g: List<String>) = aparte(f(l, "guias.json")) { t -> envoltorio.escribe(FileOutputStream(t)).use { it.write(JsonFlujo.escribe(g).toByteArray()) } }

    /** Un archivo que se escribe aparte y sustituye al de verdad solo si todo ha ido bien (un fallo a medias no deja la lista vacía). */
    private inline fun <T> aparte(destino: File, hacer: (File) -> T): T {
        val tmp = File(destino.path + ".nuevo")
        try { val r = hacer(tmp); if (!tmp.renameTo(destino)) { destino.delete(); tmp.renameTo(destino) }; return r } finally { tmp.delete() }
    }

    // ---------------------------------------------------------------- canales

    /** Guarda el archivo M3U de una lista de archivo (lo que elegiste en este dispositivo). */
    fun guardaArchivo(l: Lista, entrada: InputStream) = aparte(f(l, "lista.m3u")) { t -> envoltorio.escribe(FileOutputStream(t)).use { o -> entrada.copyTo(o, 64 * 1024) } }

    fun tieneArchivo(l: Lista) = f(l, "lista.m3u").isFile

    /**
     * Baja (o lee, si es de archivo) los canales de la lista y los guarda. Devuelve cuántos. Si falla, lanza [IptvError] (sin secretos) y
     * deja los de antes.
     */
    fun actualizaCanales(l: Lista): Int {
        try {
            val (n, guias) = aparte(f(l, "canales.bin")) { t ->
                DataOutputStream(BufferedOutputStream(envoltorio.escribe(FileOutputStream(t)), 64 * 1024)).use { o ->
                    var n = 0
                    val guias: List<String>
                    when (l.tipo) {
                        TipoLista.XTREAM -> {
                            val x = Xtream(l, red)
                            val cats = x.categoriasDirecto()
                            val nombres = cats.associate { it.id to it.nombre }
                            // (en el orden de las categorías del servidor: así salen los grupos como los ordena tu proveedor)
                            o.writeInt(cats.size); cats.forEach { o.writeUTF(it.nombre) }
                            x.canales(nombres) { c -> escribeCanal(o, c); n++ }
                            guias = emptyList()
                        }
                        TipoLista.M3U -> {
                            o.writeInt(0)
                            val r = try { red.abre(l.url, if (l.agente.isNotBlank()) mapOf("User-Agent" to l.agente) else emptyMap()) } catch (e: Exception) { throw RedJava.traduce(e) }
                            r.use {
                                if (!r.ok) throw IptvError(if (r.codigo == 401 || r.codigo == 403) "El servidor de la lista no te deja entrar (${r.codigo}): ¿ha caducado?" else "El servidor de la lista ha contestado con un error (${r.codigo}).", r.codigo)
                                val (cab, k) = try { M3u.lee(r.cuerpo) { c -> escribeCanal(o, c) } } catch (e: IptvError) { throw e } catch (e: Exception) { throw IptvError("Se ha cortado la conexión al bajar la lista.") }
                                n = k; guias = cab.guias
                            }
                        }
                        TipoLista.ARCHIVO -> {
                            o.writeInt(0)
                            if (!tieneArchivo(l)) throw IptvError("Esta lista es un archivo de otro dispositivo: aquí no está.")
                            val (cab, k) = envoltorio.lee(FileInputStream(f(l, "lista.m3u"))).use { M3u.lee(it) { c -> escribeCanal(o, c) } }
                            n = k; guias = cab.guias
                        }
                    }
                    if (n == 0) throw IptvError("La lista no tiene ningún canal (¿es una lista M3U?).")
                    n to guias
                }
            }
            ponGuias(l, guias)
            ponEstado(l, "canales", Estado(reloj(), n, ""))
            return n
        } catch (e: Exception) {
            val err = (e as? IptvError ?: RedJava.traduce(e)).let { IptvError(Secretos.tapa(it.message.orEmpty(), l.secretos), it.codigo) }
            ponEstado(l, "canales", estado(l, "canales").copy(error = err.message.orEmpty()))
            throw err
        }
    }

    /** Los grupos en el orden del servidor (Xtream) y los canales guardados (vacío si aún no se han bajado). */
    fun canales(l: Lista): Pair<List<String>, List<Canal>> {
        val a = f(l, "canales.bin")
        if (!a.isFile) return emptyList<String>() to emptyList()
        return runCatching {
            DataInputStream(BufferedInputStream(envoltorio.lee(FileInputStream(a)), 64 * 1024)).use { i ->
                val grupos = List(i.readInt()) { i.readUTF() }
                val l2 = ArrayList<Canal>()
                while (true) { l2 += leeCanal(i) ?: break }
                grupos to l2
            }
        }.getOrDefault(emptyList<String>() to emptyList())
    }

    private fun escribeCanal(o: DataOutputStream, c: Canal) {
        o.writeByte(1)
        for (s in listOf(c.clave, c.nombre, c.grupo, c.logo, c.tvgId, c.tvgNombre, c.url)) o.writeUTF(s.take(8000))
        o.writeInt(c.numero); o.writeLong(c.idXtream); o.writeBoolean(c.radio); o.writeDouble(c.desfase)
        o.writeInt(c.cabeceras.size); c.cabeceras.forEach { (k, v) -> o.writeUTF(k.take(500)); o.writeUTF(v.take(4000)) }
        o.writeInt(c.propiedades.size); c.propiedades.forEach { (k, v) -> o.writeUTF(k.take(500)); o.writeUTF(v.take(4000)) }
        val ca = c.catchup
        o.writeBoolean(ca != null); if (ca != null) { o.writeUTF(ca.modo); o.writeInt(ca.dias); o.writeUTF(ca.fuente.take(4000)) }
    }

    private fun leeCanal(i: DataInputStream): Canal? {
        val marca = try { i.readByte() } catch (e: EOFException) { return null }
        if (marca.toInt() != 1) return null
        val s = List(7) { i.readUTF() }
        val num = i.readInt(); val idx = i.readLong(); val radio = i.readBoolean(); val desf = i.readDouble()
        val cab = List(i.readInt()) { i.readUTF() to i.readUTF() }.toMap()
        val props = List(i.readInt()) { i.readUTF() to i.readUTF() }.toMap()
        val ca = if (i.readBoolean()) Catchup(i.readUTF(), i.readInt(), i.readUTF()) else null
        return Canal(s[0], s[1], s[2].intern(), s[3], s[4], s[5], num, s[6], idx, cab, ca, radio, props, desf)
    }

    // ---------------------------------------------------------------- el videoclub (Xtream)

    /** Baja las categorías y las películas de un login de Xtream. Devuelve cuántas. */
    fun actualizaPeliculas(l: Lista): Int = videoclub(l, "peliculas") { x, o ->
        val cats = x.categoriasPeliculas(); escribeCategorias(l, "peliculas", cats)
        var n = 0
        x.peliculas { p -> o.writeByte(1); escribePelicula(o, p); n++ }
        n
    }

    fun actualizaSeries(l: Lista): Int = videoclub(l, "series") { x, o ->
        val cats = x.categoriasSeries(); escribeCategorias(l, "series", cats)
        var n = 0
        x.series { s -> o.writeByte(1); escribeSerie(o, s); n++ }
        n
    }

    private fun videoclub(l: Lista, que: String, hacer: (Xtream, DataOutputStream) -> Int): Int {
        if (!l.esXtream) return 0
        try {
            val n = aparte(f(l, "$que.bin")) { t -> DataOutputStream(BufferedOutputStream(FileOutputStream(t), 64 * 1024)).use { o -> hacer(Xtream(l, red), o) } }
            ponEstado(l, que, Estado(reloj(), n))
            return n
        } catch (e: Exception) {
            val err = (e as? IptvError ?: RedJava.traduce(e)).let { IptvError(Secretos.tapa(it.message.orEmpty(), l.secretos), it.codigo) }
            ponEstado(l, que, estado(l, que).copy(error = err.message.orEmpty()))
            throw err
        }
    }

    private fun escribeCategorias(l: Lista, que: String, c: List<Categoria>) =
        f(l, "categorias_$que.json").writeText(JsonFlujo.escribe(c.map { mapOf("i" to it.id, "n" to it.nombre) }))

    fun categorias(l: Lista, que: String): List<Categoria> =
        runCatching { JsonFlujo.lee(f(l, "categorias_$que.json").readText()).lista().map { it.mapa() }.map { Categoria(it["i"].txt(), it["n"].txt()) } }.getOrDefault(emptyList())

    private fun escribePelicula(o: DataOutputStream, p: Pelicula) {
        o.writeLong(p.id); for (s in listOf(p.nombre, p.categoria, p.poster, p.extension, p.tmdb, p.ano)) o.writeUTF(s.take(4000)); o.writeDouble(p.nota); o.writeLong(p.anadida)
    }
    private fun leePelicula(i: DataInputStream): Pelicula { val id = i.readLong(); val s = List(6) { i.readUTF() }; return Pelicula(id, s[0], s[1], s[2], s[3], i.readDouble(), i.readLong(), s[4], s[5]) }
    private fun escribeSerie(o: DataOutputStream, s: Serie) {
        o.writeLong(s.id); for (x in listOf(s.nombre, s.categoria, s.poster, s.ano, s.generos, s.trama.take(1500), s.fondo)) o.writeUTF(x.take(4000)); o.writeDouble(s.nota); o.writeLong(s.modificada)
    }
    private fun leeSerie(i: DataInputStream): Serie { val id = i.readLong(); val x = List(7) { i.readUTF() }; return Serie(id, x[0], x[1], x[2], i.readDouble(), x[3], x[4], x[5], x[6], i.readLong()) }

    private fun <T> recorre(a: File, leer: (DataInputStream) -> T, cada: (T) -> Boolean) {
        if (!a.isFile) return
        runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(a), 64 * 1024)).use { i ->
                while (true) { val m = try { i.readByte() } catch (e: EOFException) { break }; if (m.toInt() != 1) break; if (!cada(leer(i))) break }
            }
        }
    }

    /** Las películas de una categoría (o todas, con un tope), en el orden del servidor. Se recorren en el archivo. */
    fun peliculas(l: Lista, categoria: String? = null, tope: Int = Int.MAX_VALUE): List<Pelicula> {
        val r = ArrayList<Pelicula>()
        recorre(f(l, "peliculas.bin"), ::leePelicula) { p -> if (categoria == null || p.categoria == categoria) r += p; r.size < tope }
        return r
    }

    fun series(l: Lista, categoria: String? = null, tope: Int = Int.MAX_VALUE): List<Serie> {
        val r = ArrayList<Serie>()
        recorre(f(l, "series.bin"), ::leeSerie) { s -> if (categoria == null || s.categoria == categoria) r += s; r.size < tope }
        return r
    }

    fun pelicula(l: Lista, id: Long): Pelicula? { var x: Pelicula? = null; recorre(f(l, "peliculas.bin"), ::leePelicula) { p -> if (p.id == id) { x = p; false } else true }; return x }
    fun serie(l: Lista, id: Long): Serie? { var x: Serie? = null; recorre(f(l, "series.bin"), ::leeSerie) { s -> if (s.id == id) { x = s; false } else true }; return x }

    /** Buscar en el videoclub (sin mirar acentos ni mayúsculas). */
    fun buscaPeliculas(l: Lista, texto: String, tope: Int = 60): List<Pelicula> {
        val q = Buscar.normal(texto); if (q.isBlank()) return emptyList()
        val r = ArrayList<Pelicula>(); recorre(f(l, "peliculas.bin"), ::leePelicula) { p -> if (Buscar.normal(p.nombre).contains(q)) r += p; r.size < tope }; return r
    }
    fun buscaSeries(l: Lista, texto: String, tope: Int = 60): List<Serie> {
        val q = Buscar.normal(texto); if (q.isBlank()) return emptyList()
        val r = ArrayList<Serie>(); recorre(f(l, "series.bin"), ::leeSerie) { s -> if (Buscar.normal(s.nombre).contains(q)) r += s; r.size < tope }; return r
    }

    /** Las fichas (se piden al servidor y se guardan: no cambian casi nunca). */
    fun fichaPelicula(l: Lista, p: Pelicula, refrescar: Boolean = false): FichaPelicula {
        val a = f(l, "ficha_p${p.id}.json")
        if (!refrescar && a.isFile) runCatching { return Xtream.fichaPeliculaDe(JsonFlujo.lee(a.readText()).mapa(), p) }
        return pideFicha(l, "get_vod_info&vod_id=${p.id}", a) { Xtream.fichaPeliculaDe(it, p) }
    }

    fun fichaSerie(l: Lista, s: Serie, refrescar: Boolean = false): FichaSerie {
        val a = f(l, "ficha_s${s.id}.json")
        // (una serie que sigue saliendo cambia: se vuelve a pedir si la guardada tiene más de un día)
        if (!refrescar && a.isFile && reloj() - a.lastModified() < 24 * 3_600_000L) runCatching { return Xtream.fichaSerieDe(JsonFlujo.lee(a.readText()).mapa(), s) }
        return try { pideFicha(l, "get_series_info&series_id=${s.id}", a) { Xtream.fichaSerieDe(it, s) } }
        catch (e: IptvError) { if (a.isFile) runCatching { Xtream.fichaSerieDe(JsonFlujo.lee(a.readText()).mapa(), s) }.getOrNull() ?: throw e else throw e }
    }

    private fun <T> pideFicha(l: Lista, accion: String, a: File, leer: (Map<String, Any?>) -> T): T {
        val x = Xtream(l, red)
        val url = "${x.servidor}/player_api.php?username=${java.net.URLEncoder.encode(l.usuario, "UTF-8")}&password=${java.net.URLEncoder.encode(l.contrasena, "UTF-8")}&action=$accion"
        val r = try { red.abre(url, if (l.agente.isNotBlank()) mapOf("User-Agent" to l.agente) else emptyMap()) } catch (e: Exception) { throw RedJava.traduce(e) }
        val texto = r.use { if (!r.ok) throw IptvError("El servidor ha contestado con un error (${r.codigo}) al pedir la ficha.", r.codigo); r.cuerpo.readBytes().toString(Charsets.UTF_8) }
        val m = runCatching { JsonFlujo.lee(texto).mapa() }.getOrNull() ?: throw IptvError("La ficha que da el servidor no se entiende.")
        val v = leer(m)
        runCatching { a.writeText(texto) }
        return v
    }

    // ---------------------------------------------------------------- la guía

    fun guia(l: Lista): Guia? = Guia.abre(f(l, "guia.bin"))

    /** Las direcciones de guía de una lista: la tuya, las que dice la lista M3U y, en Xtream, la del servidor. */
    fun fuentesGuia(l: Lista): List<String> = (listOf(l.epg) + guiasDeLaLista(l) + if (l.esXtream) listOf(Xtream(l, red).urlGuia()) else emptyList())
        .flatMap { it.split(',') }.map { it.trim() }.filter { it.startsWith("http://", true) || it.startsWith("https://", true) }.distinct()

    /**
     * Baja la guía de una lista (de todas sus fuentes, una detrás de otra) y la guarda: solo los programas de los canales que tienes, de
     * [horasAtras] horas antes a [dias] días después. Devuelve cuántos programas. Una fuente que falla no tira las demás; si fallan todas,
     * lanza [IptvError] y se queda la guía de antes.
     */
    fun actualizaGuia(l: Lista, canales: List<Canal> = canales(l).second, dias: Int = 4, horasAtras: Int = 6): Int {
        val fuentes = fuentesGuia(l)
        if (fuentes.isEmpty()) { ponEstado(l, "guia", Estado(reloj(), 0, "Esta lista no dice de dónde sacar la guía: pon una dirección XMLTV en la lista.")); return 0 }
        val ahora = reloj()
        val destino = f(l, "guia.bin")
        val e = Guia.Escritor(File(destino.path + ".hecha"))
        var buenas = 0; var ultimoError = ""
        try {
            for (u in fuentes) {
                val vistos = ArrayList<CanalGuia>()
                var quiero: Set<String>? = null
                val op = Xmltv.Opciones(ahora - horasAtras * 3_600_000L, ahora + dias * 86_400_000L, quiero = { id ->
                    val q = quiero ?: Emparejador(vistos).let { em -> canales.mapNotNullTo(HashSet()) { em.busca(it) } + canales.mapTo(HashSet()) { it.tvgId } }.also { quiero = it }
                    id in q
                })
                try {
                    val r = red.abre(u, if (l.agente.isNotBlank()) mapOf("User-Agent" to l.agente) else emptyMap())
                    r.use {
                        if (!r.ok) throw IptvError("La guía ha contestado con un error (${r.codigo}).", r.codigo)
                        Xmltv.lee(r.cuerpo, op, { c -> vistos += c; e.canal(c) }, { p -> e.programa(p) })
                    }
                    buenas++
                } catch (x: Exception) { ultimoError = (x as? IptvError)?.message ?: RedJava.traduce(x).message.orEmpty() }
            }
            if (buenas == 0) { e.descarta(); throw IptvError(Secretos.tapa(ultimoError.ifBlank { "No se ha podido bajar la guía." }, l.secretos)) }
            e.fuentes = buenas
            e.cierra(ahora)
            val hecha = File(destino.path + ".hecha")
            if (!hecha.renameTo(destino)) { destino.delete(); hecha.renameTo(destino) }
            ponEstado(l, "guia", Estado(ahora, e.cuantos, if (buenas < fuentes.size) Secretos.tapa("Una de las guías no ha respondido: $ultimoError", l.secretos) else ""))
            return e.cuantos
        } catch (x: IptvError) {
            ponEstado(l, "guia", estado(l, "guia").copy(error = Secretos.tapa(x.message.orEmpty(), l.secretos)))
            throw x
        } finally { e.close() }
    }

    /** ¿Toca bajar la guía otra vez? (cada [horas] horas, o si se acaba en menos de un día) */
    fun guiaCaducada(l: Lista, horas: Int = 12): Boolean {
        val e = estado(l, "guia")
        if (e.cuando <= 0) return true
        if (reloj() - e.cuando > horas * 3_600_000L) return true
        val g = guia(l) ?: return true
        return g.hastaCuando in 1 until reloj() + 86_400_000L
    }
}

/** Buscar sin mirar acentos ni mayúsculas. */
object Buscar {
    fun normal(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()
}
