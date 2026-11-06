package com.gonzalo.hiedra.pc

/**
 * Un juego tal como se enseña: el de la cuenta ([juego]), lo de este ordenador con que se juega ([local]) o los dos (el mismo juego). Lo que
 * está instalado aquí y no está en tu biblioteca se ve igual (se puede jugar), pero no entra en la cuenta hasta que lo juegas o lo añades.
 */
class Ficha(val juego: Juego?, val local: JuegoPc?) {
    /** Lo que la identifica en la pantalla (el id del juego, o la clave de lo de aquí). */
    val clave: String = juego?.id ?: local?.clave.orEmpty()
    val titulo: String = juego?.titulo?.takeIf { it.isNotBlank() } ?: local?.nombre.orEmpty()
    val portada: String = juego?.portada.orEmpty()
    val portadaUrl: String = juego?.portadaUrl?.takeIf { it.isNotBlank() } ?: local?.portada.orEmpty()
    /** De dónde es, en corto: «Steam», «Epic», «GBA», «Switch»… */
    val etiqueta: String = local?.etiqueta ?: juego?.donde.orEmpty()
    val estado: Estado? = juego?.estado
    val fav: Boolean = juego?.fav == true
    val ms: Long = juego?.ms ?: 0L
    val ultima: Long = juego?.let { maxOf(it.ultima, it.anadido) } ?: 0L
    val jugable: Boolean get() = local != null
    val enCuenta: Boolean get() = juego != null
    /** La imagen grande de fondo: la de Steam (también para un juego de la cuenta con Steam en otro PC) o la de su tienda. */
    val fondo: String = local?.fondo?.takeIf { it.isNotBlank() }
        ?: juego?.lanzadores?.firstOrNull { it.tipo == "PC" && it.pcKey.startsWith("steam:") }?.pcKey?.removePrefix("steam:")?.let { Steam.fondo(it) }.orEmpty()
    /** Para ordenar por nombre (sin «The», «El»… delante). */
    val orden: String = Juegos.norm(titulo.replace(Regex("^(?i)(the|el|la|los|las|a|an) "), ""))
    /** La letra con la que se salta en la biblioteca («#» para lo que empieza por número o signo). */
    val letra: Char = orden.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'
}

/** Una fila de la sala: un título y sus juegos. */
class Hilera(val id: String, val titulo: String, val fichas: List<Ficha>, val sub: String = "")

/** Todo lo que se enseña, calculado de una vez (en segundo plano) cada vez que cambia la cuenta o lo de este ordenador. */
class Biblioteca(val fichas: List<Ficha>, val ahora: Pc.Partida?) {
    private val porClave = fichas.associateBy { it.clave }
    private val porLocal = fichas.filter { it.local != null }.associateBy { it.local!!.clave }
    fun por(clave: String): Ficha? = porClave[clave] ?: porLocal[clave]

    /** Lo que estás jugando: «Jugando», lo último primero (lo que se puede jugar aquí, antes). */
    val jugando: List<Ficha> get() = fichas.filter { it.estado == Estado.JUGANDO }.sortedWith(compareByDescending<Ficha> { it.jugable }.thenByDescending { it.ultima }.thenBy { it.orden })
    val favoritos: List<Ficha> get() = fichas.filter { it.fav }.sortedBy { it.orden }
    val pendientes: List<Ficha> get() = fichas.filter { it.estado == Estado.PENDIENTE }.sortedWith(compareByDescending<Ficha> { it.jugable }.thenByDescending { it.ultima })
    val pasados: List<Ficha> get() = fichas.filter { it.estado == Estado.PASADO }.sortedByDescending { it.juego?.fin ?: 0L }
    val aqui: List<Ficha> get() = fichas.filter { it.jugable }.sortedBy { it.orden }
    val todas: List<Ficha> get() = fichas.sortedBy { it.orden }

    /** Lo de este ordenador, por tienda o por sistema (Steam, Epic, GOG, Amazon, Lutris, cada consola, tus programas). */
    fun porOrigen(): List<Hilera> {
        val l = fichas.filter { it.jugable }
        val tiendas = listOf("Steam", "Epic", "GOG", "Amazon", "Heroic", "Lutris")
        val filas = ArrayList<Hilera>()
        for (t in tiendas) l.filter { it.local!!.etiqueta == t && it.local.fuente != Fuente.EMULACION }.takeIf { it.isNotEmpty() }?.let { filas += Hilera("o:$t", t, it.sortedBy { f -> f.orden }) }
        for (s in Emulacion.SISTEMAS) l.filter { it.local!!.fuente == Fuente.EMULACION && it.local.sistema == s.id }.takeIf { it.isNotEmpty() }?.let { filas += Hilera("o:${s.id}", s.nombre, it.sortedBy { f -> f.orden }, "Emulación") }
        l.filter { it.local!!.fuente == Fuente.PROGRAMA }.takeIf { it.isNotEmpty() }?.let { filas += Hilera("o:programas", "Tus programas", it.sortedBy { f -> f.orden }) }
        return filas
    }

    /** Las filas de la portada de la sala. */
    fun inicio(): List<Hilera> {
        val filas = ArrayList<Hilera>()
        val enMarcha = ahora?.let { p -> por(p.juego) ?: por(p.clave) }
        val seguir = (listOfNotNull(enMarcha) + jugando).distinctBy { it.clave }
        if (seguir.isNotEmpty()) filas += Hilera("seguir", "Seguir jugando", seguir.take(30))
        favoritos.takeIf { it.isNotEmpty() }?.let { filas += Hilera("favoritos", "Favoritos", it.take(40)) }
        pendientes.filter { it.jugable }.takeIf { it.isNotEmpty() }?.let { filas += Hilera("pendientes", "Pendientes en este PC", it.take(40)) }
        filas += porOrigen()
        pasados.takeIf { it.isNotEmpty() }?.let { filas += Hilera("pasados", "Lo que has pasado", it.take(40)) }
        return filas
    }

    /** Busca por el nombre (sin mirar tildes ni signos): primero lo que empieza así, luego lo que lo contiene. */
    fun busca(q: String): List<Ficha> {
        val n = Juegos.norm(q)
        if (n.isEmpty()) return emptyList()
        val (empiezan, resto) = fichas.filter { Juegos.norm(it.titulo).contains(n) }.partition { Juegos.norm(it.titulo).startsWith(n) || it.orden.startsWith(n) }
        return empiezan.sortedBy { it.orden } + resto.sortedBy { it.orden }
    }

    companion object {
        /**
         * Junta tus juegos de la cuenta con lo de este ordenador: cada juego de la cuenta con lo de aquí que corresponde a uno de sus
         * lanzadores, y lo de aquí que no está en tu biblioteca, aparte.
         */
        fun de(juegos: List<Juego>, locales: List<JuegoPc>, dev: String, ahora: Pc.Partida? = null): Biblioteca {
            val idx = locales.associateBy { it.claveCuenta }
            val usados = HashSet<String>()
            val fichas = ArrayList<Ficha>()
            for (j in juegos) {
                val l = j.aqui(dev) { idx[it] }
                if (l != null) usados += l.clave
                fichas += Ficha(j, l)
            }
            for (l in locales) if (l.clave !in usados) fichas += Ficha(null, l)
            return Biblioteca(fichas, ahora)
        }
    }
}
