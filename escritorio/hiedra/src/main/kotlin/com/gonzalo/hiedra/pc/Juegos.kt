package com.gonzalo.hiedra.pc

import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.nucleo.Fechas
import com.gonzalo.familia.nucleo.Nodo
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Sitio

enum class Estado(val nombre: String, val cuenta: String) {
    JUGANDO("Jugando", "en_curso"), PENDIENTE("Pendiente", "pendiente"), PASADO("Pasado", "terminado"), ABANDONADO("Abandonado", "abandonado");
    companion object { fun de(s: String) = when (s) { "en_curso" -> JUGANDO; "terminado" -> PASADO; "abandonado" -> ABANDONADO; else -> PENDIENTE } }
}

/** Los menús pequeños de Hiedra: de dónde es el juego. */
enum class Menu(val nombre: String, val cuenta: String) { PC("PC", "pc"), EMULACION("Emulación", "emulacion"), ANDROID("Android", "android"), WEB("Web", "web") }

/** Un lanzador de la cuenta (`ln`): cómo se abre el juego (`tp` ROM, APP, WEB, PC). Dónde está su archivo en cada dispositivo NO va aquí. */
class Lanzador(val m: Map<String, Any?>) {
    val tipo: String get() = m["tp"].comoTexto()
    val pcKey: String get() = m["pck"].comoTexto()
    val pcDev: String get() = m["pcd"].comoTexto()
    val sistema: String get() = m["s"].comoTexto()
    val archivo: String get() = m["a"].comoTexto()
    val paquete: String get() = m["pk"].comoTexto()
    val url: String get() = m["u"].comoTexto()
    val menu: Menu get() = when (tipo) { "ROM" -> Menu.EMULACION; "APP" -> Menu.ANDROID; "WEB" -> Menu.WEB; else -> Menu.PC }
    /** La clave del lanzador (la misma que en Hiedra del móvil). */
    val clave: String get() = when (tipo) {
        "ROM" -> "rom:$sistema:${Emulacion.norm(archivo)}"; "APP" -> "app:$paquete"
        "WEB" -> "web:" + url.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/'); else -> "pc:$pcKey"
    }
    companion object {
        fun pc(key: String, dev: String) = Lanzador(linkedMapOf("tp" to "PC", "pck" to key, "pcd" to dev))
        /** Una ROM: su sistema y el nombre del archivo sin extensión (dónde está, en cada dispositivo, no va a la cuenta). */
        fun rom(sistema: String, archivo: String) = Lanzador(linkedMapOf("tp" to "ROM", "s" to sistema, "a" to archivo))
    }
}

/** Un juego de la cuenta (`juegos/<id>`), con sus horas (`tiempo/<id>~<fuente>`: lo de un juego es la suma de sus fuentes). */
class Juego(val n: Nodo, val horas: List<Nodo>) {
    val id: String get() = n.ref
    val titulo: String get() = n.texto("t")
    val plataforma: String get() = n.texto("pl")
    val estado: Estado get() = Estado.de(n.texto("estado"))
    val fav: Boolean get() = n.si("fav")
    val anadido: Long get() = Fechas.ms(n.entero("an"))
    val ultima: Long get() = Fechas.ms(n.entero("ul"))
    val fin: Long get() = Fechas.ms(n.entero("fin"))
    val portada: String get() = n.texto("portada")
    val portadaUrl: String get() = n.texto("pu")
    val lanzadores: List<Lanzador> get() = n.lista("ln").map { Lanzador(it.comoMapa()) }
    val menu: Menu get() = Menu.entries.firstOrNull { it.cuenta == n.texto("menu") } ?: lanzadores.firstOrNull()?.menu ?: if (plataforma.equals("PC", true)) Menu.PC else Menu.PC
    val ms: Long get() = horas.sumOf { it.entero("total") }
    val veces: Int get() = horas.sumOf { it.entero("veces") }.toInt()
    fun dias(): Map<String, Long> = HashMap<String, Long>().also { m -> horas.forEach { h -> h.campos.forEach { (k, v) -> if (k.length == 11 && k[0] == 'd' && k[5] == '-') m.merge(k.substring(1), v.comoLong(), Long::plus) } } }
    val nota: Int? get() = n["nota"]?.let { n.entero("nota").toInt() }
    /** El lanzador de PC de este ordenador, si lo tiene (o uno de PC sin dueño claro: de Steam vale en cualquiera). */
    fun lanzadorAqui(dev: String): Lanzador? = lanzadores.firstOrNull { it.tipo == "PC" && it.pcDev == dev } ?: lanzadores.firstOrNull { it.tipo == "PC" && it.pcKey.startsWith("steam:") }

    /**
     * Con qué se juega aquí: lo de este ordenador que corresponde a uno de sus lanzadores. Los de Steam y Heroic valen vengan del PC que vengan
     * (si está instalado aquí, es el mismo juego); los de Lutris y tus programas, solo los de este ordenador (su número es de cada uno); una ROM,
     * si su archivo está en tus carpetas.
     */
    fun aqui(dev: String, buscar: (String) -> JuegoPc? = Pc::porClaveCuenta): JuegoPc? = lanzadores.firstNotNullOfOrNull { l ->
        when {
            l.tipo == "ROM" -> buscar(l.clave)
            l.tipo != "PC" || l.pcKey.isBlank() -> null
            l.pcKey.startsWith("steam:") || l.pcKey.startsWith("heroic:") || l.pcDev == dev -> buscar(l.clave)
            else -> null
        }
    }

    /** El sistema de su ROM, si tiene. */
    val sistema: String get() = lanzadores.firstOrNull { it.tipo == "ROM" }?.sistema.orEmpty()
    /** Lo que se enseña como plataforma: la suya, o el sistema de su ROM, o su menú. */
    val donde: String get() = plataforma.ifBlank { Emulacion.sistema(sistema)?.corto ?: if (sistema.isNotBlank()) sistema.uppercase() else menu.nombre }
}

/**
 * Tus juegos: los de la cuenta, los mismos nodos que Hiedra del móvil (y que valora Azabache). Lo que cambias va al nodo, campo a campo.
 */
object Juegos {
    fun col(): Coleccion = LaFamilia.coleccion("juegos")
    fun tiempo(): Coleccion = LaFamilia.coleccion("tiempo")
    /** Este ordenador como dispositivo (sus horas, en `tiempo/<id>~<dispositivo>`; y en «Tu PC» del móvil). */
    @Volatile var dispositivo: () -> String = { Sitio.dispositivo("hiedra") }

    fun todos(): List<Juego> {
        val h = tiempo().vivos().groupBy { it.texto("ref") }
        return col().vivos().filter { it.texto("t").isNotBlank() }.map { Juego(it, h[it.ref].orEmpty()) }.sortedByDescending { maxOf(it.ultima, it.anadido) }
    }
    fun por(id: String): Juego? = col().nodo(id)?.takeIf { !it.borrado }?.let { n -> Juego(n, tiempo().vivos().filter { it.texto("ref") == id }) }
    fun porClave(clave: String): Juego? = todos().firstOrNull { j -> j.lanzadores.any { it.clave == clave } || clave in j.n.lista("k").map { it.comoTexto() } }

    /**
     * El último juego que borraste con esta clave (o con este nombre): un juego borrado sigue en la cuenta como borrado, con sus datos (como en
     * Hiedra del móvil). Lo que entra solo no lo resucita; lo que añades o juegas tú desde aquí, sí: vuelve el de antes, con su estado y su nota.
     */
    fun borradoCon(clave: String, titulo: String = ""): Nodo? {
        val n = norm(titulo)
        return col().todos().values.filter { it.borrado && it.texto("t").isNotBlank() }.filter { b ->
            b.lista("ln").any { Lanzador(it.comoMapa()).clave == clave } || clave in b.lista("k").map { it.comoTexto() } || (n.isNotEmpty() && norm(b.texto("t")) == n)
        }.maxByOrNull { it.entero(Nodo.B) }
    }
    fun porTitulo(t: String): Juego? = norm(t).let { k -> todos().firstOrNull { norm(it.titulo) == k } }
    fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), "")

    fun cambia(id: String, campos: Map<String, Any?>) = col().cambia(id, campos)

    fun ponEstado(j: Juego, e: Estado) {
        val c = linkedMapOf<String, Any?>("estado" to e.cuenta)
        if (e == Estado.PASADO && j.fin <= 0) c["fin"] = System.currentTimeMillis()
        cambia(j.id, c)
    }

    /**
     * Un juego de este ordenador en Hiedra: si ya hay uno con ese lanzador (o con ese nombre: una ficha de Atrio, su ROM…), gana el lanzador
     * de aquí; si no, uno nuevo (`j_` + 12 hexadecimales, como en el móvil). Devuelve su id.
     */
    fun mete(clavePc: String, titulo: String, portadaUrl: String = "", jugando: Boolean = false): String =
        mete(Lanzador.pc(clavePc, dispositivo()), titulo, portadaUrl, jugando)

    /** Un juego de este ordenador en Hiedra (lo has añadido o lo vas a jugar: si lo habías borrado, vuelve el de antes). Devuelve su id. */
    fun mete(j: JuegoPc, jugando: Boolean = false): String =
        mete(j.lanzador(dispositivo()), j.nombre, j.portada.takeIf { it.startsWith("http") }.orEmpty(), jugando)

    /**
     * Un juego en Hiedra con un lanzador de este ordenador: si ya hay uno con ese lanzador (o con ese nombre: una ficha de Atrio, su ROM del
     * móvil…), gana el lanzador de aquí; si lo borraste, vuelve el de antes; si no, uno nuevo (`j_` + 12 hexadecimales, como en el móvil).
     */
    fun mete(ln: Lanzador, titulo: String, portadaUrl: String = "", jugando: Boolean = false): String {
        val clave = ln.clave
        val previo = porClave(clave) ?: porTitulo(titulo)
        val c = LinkedHashMap<String, Any?>()
        val id: String
        val lanzadores: List<Lanzador>
        val estado: Estado
        if (previo != null) { id = previo.id; lanzadores = previo.lanzadores; estado = previo.estado; if (previo.portadaUrl.isBlank() && portadaUrl.isNotBlank()) c["pu"] = portadaUrl }
        else {
            val b = borradoCon(clave, titulo)
            if (b != null) {
                // El de antes vuelve (con su estado, su nota, sus horas)
                id = b.ref; lanzadores = b.lista("ln").map { Lanzador(it.comoMapa()) }; estado = Estado.de(b.texto("estado")); c[Nodo.B] = 0L
                if (b.texto("pu").isBlank() && portadaUrl.isNotBlank()) c["pu"] = portadaUrl
            } else {
                id = "j_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
                val rom = ln.tipo == "ROM"
                col().cambia(id, linkedMapOf("t" to titulo, "pl" to if (rom) "" else "PC", "estado" to if (jugando) Estado.JUGANDO.cuenta else Estado.PENDIENTE.cuenta,
                    "an" to System.currentTimeMillis(), "menu" to if (rom) "emulacion" else "pc", "ln" to listOf(ln.m), "pu" to portadaUrl))
                return id
            }
        }
        // El lanzador de aquí: el de PC con la misma clave se cambia por el de este ordenador; una ROM, si ya estaba, se queda como está
        val ya = lanzadores.any { it.clave == clave && (ln.tipo != "PC" || it.pcDev == ln.pcDev) }
        if (!ya) c["ln"] = lanzadores.filterNot { it.clave == clave }.map { it.m } + ln.m
        // Lo que juegas pasa a «Jugando» (los de Steam no pasan por sumaPartida: su tiempo lo cuenta Steam)
        if (jugando && (estado == Estado.PENDIENTE || estado == Estado.ABANDONADO)) c["estado"] = Estado.JUGANDO.cuenta
        if (c.isNotEmpty()) cambia(id, c)
        return id
    }

    /** Quita un juego de Hiedra (queda en la cuenta como borrado: no vuelve solo). */
    fun quita(id: String) = col().borra(id)

    /** Suma una partida jugada en este ordenador (a las horas de este dispositivo: el total, las veces y las del día). */
    fun sumaPartida(id: String, ms: Long, ahora: java.time.LocalDate = java.time.LocalDate.now()) {
        if (ms < 60_000L) return
        val j = por(id) ?: return
        val dev = dispositivo()
        val ref = "$id~$dev"
        val n = tiempo().nodo(ref)
        val dia = "d$ahora"
        tiempo().cambia(ref, linkedMapOf("ref" to id, "fuente" to dev, "total" to (n?.entero("total") ?: 0L) + ms, "veces" to (n?.entero("veces") ?: 0L) + 1,
            dia to (n?.entero(dia) ?: 0L) + ms, "t" to j.titulo, "menu" to j.menu.cuenta))
        val c = linkedMapOf<String, Any?>("ul" to System.currentTimeMillis())
        if (j.estado == Estado.PENDIENTE || j.estado == Estado.ABANDONADO) c["estado"] = Estado.JUGANDO.cuenta
        cambia(id, c)
    }
}
