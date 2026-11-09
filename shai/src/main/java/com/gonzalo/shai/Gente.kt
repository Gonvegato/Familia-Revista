package com.gonzalo.shai

import java.text.Normalizer

/**
 * Tu gente: quién es quién aunque le llamen de varias maneras («Marisa», «Marisa Bloise», «mamá» → la misma persona y el mismo chat), la
 * gente a la que le diste puerta, y la gente nueva que entra en tu vida a medida que te haces más famoso.
 */
object Gente {
    /** Con cuánta gente activa como mucho (para que la lista no se desborde: la nueva entra si hay sitio). */
    const val MAX = 16
    /** Cuántas jornadas se marca a alguien como «nuevo». */
    const val NUEVO_JORNADAS = 5

    /** «Mamá», «mi madre» → «mama», «madre» (sin tildes, sin «mi», sin signos). */
    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9ñ ]+"), " ").replace(Regex("\\s+"), " ").trim()
        .removePrefix("mi ").removePrefix("tu ").removePrefix("el ").removePrefix("la ").trim()

    /** Cómo se llama a cada rol sin decir el nombre: lo que se dice → la palabra que tiene que salir en su rol. */
    private val ROLES: List<Pair<Set<String>, List<String>>> = listOf(
        setOf("mama", "madre", "mami", "ama", "vieja") to listOf("madre"),
        setOf("papa", "padre", "papi", "viejo") to listOf("padre"),
        setOf("agente", "representante", "repre", "manager") to listOf("representante", "agente"),
        setOf("hermano", "brother") to listOf("hermano"),
        setOf("hermana") to listOf("hermana"),
        setOf("abuela", "yaya", "abu") to listOf("abuela"),
        setOf("abuelo", "yayo") to listOf("abuelo"),
        setOf("tio") to listOf("tio"),
        setOf("tia") to listOf("tia"),
        setOf("novia", "pareja", "chica") to listOf("novia", "pareja"),
        setOf("ex", "exnovia") to listOf("ex"),
        setOf("abogado", "abogada") to listOf("abogad"),
        setOf("asesor", "asesora", "asesor financiero") to listOf("asesor"),
    )

    private fun palabras(s: String) = norm(s).split(' ').filter { it.isNotBlank() }

    /**
     * Quién de [gente] es [quien] (un nombre, un apodo o «mamá»), o null si no se sabe o hay dudas (dos posibles): primero el nombre o un
     * alias exactos, luego el rol («mamá» → la que tiene rol de madre, si es una sola), luego el nombre de pila o el apellido (si solo
     * encaja una persona), y el nombre con o sin apellido («Marisa» y «Marisa Bloise»).
     */
    fun quien(gente: List<Persona>, quien: String): Persona? {
        val n = norm(quien)
        if (n.isBlank() || n == "tu" || n == "yo") return null
        val validas = gente.filter { p -> p.noEs.none { norm(it) == n } }
        // Exacto (nombre o alias)
        validas.firstOrNull { norm(it.nombre) == n || it.alias.any { a -> norm(a) == n } }?.let { return it }
        // Por el rol («mamá», «mi agente»)
        ROLES.firstOrNull { (dichos, _) -> n in dichos }?.let { (_, enRol) ->
            // Primero quien lo es (su rol empieza por ello: «madre»), luego quien lo lleva en el rol («tía, hermana de su madre» no es la madre)
            val primero = validas.filter { p -> enRol.any { r -> norm(p.rol).split(' ').firstOrNull()?.startsWith(r) == true } }
            if (primero.size == 1) return primero.first()
            val c = validas.filter { p -> enRol.any { r -> norm(p.rol).split(' ').any { it.startsWith(r) } } }
            if (primero.isEmpty() && c.size == 1) return c.first()
        }
        val ps = palabras(quien)
        if (ps.isEmpty()) return null
        // El nombre con o sin apellido: las palabras de uno están en el otro y empiezan igual
        val incluidas = validas.filter { p ->
            val q = palabras(p.nombre); q.isNotEmpty() && q.first() == ps.first() && (q.containsAll(ps) || ps.containsAll(q))
        }
        if (incluidas.size == 1) return incluidas.first()
        // Solo el nombre de pila o solo el apellido, si es único
        if (ps.size == 1) {
            val pila = validas.filter { palabras(it.nombre).firstOrNull() == ps[0] }
            if (pila.size == 1) return pila.first()
            val apellido = validas.filter { p -> palabras(p.nombre).drop(1).contains(ps[0]) }
            if (apellido.size == 1 && pila.isEmpty()) return apellido.first()
        }
        return null
    }

    /** Los roles que solo tiene una persona («madre», «padre», «representante»): para no meter una segunda madre. */
    fun rolUnico(rol: String): String? = norm(rol).split(' ').firstOrNull()?.let { w ->
        when { w.startsWith("madre") -> "madre"; w.startsWith("padre") -> "padre"; w.startsWith("representante") || w == "agente" -> if ("candidat" in norm(rol)) null else "representante"; else -> null }
    }

    /** Por su id (el corto que se le da a Claude, o el entero). */
    fun porId(gente: List<Persona>, id: String): Persona? {
        val t = id.trim()
        if (t.length < 4) return null
        return gente.firstOrNull { it.id == t } ?: gente.firstOrNull { it.id.startsWith(t) }
    }

    /** Toda la gente que ha pasado por tu vida (la de ahora y a la que le diste puerta). */
    fun todos(u: Universo): List<Persona> = u.entorno + u.exEntorno

    /** Quién escribe un mensaje (por su id si lo trae, si no por su nombre). */
    fun deMensaje(u: Universo, d: Dm): Persona? =
        (if (d.pid.isNotBlank()) porId(todos(u), d.pid)?.takeIf { p -> p.noEs.none { norm(it) == norm(d.de) } } else null) ?: quien(todos(u), d.de)

    /** Lo nuevo que llega de Claude, sin mensajes de la gente a la que le diste puerta (no vuelven a escribirte). */
    fun sinExpulsados(u: Universo, dms: List<Dm>): List<Dm> = dms.filter { d ->
        if (d.mio) return@filter true
        val p = (if (d.pid.isNotBlank()) porId(u.exEntorno, d.pid) else null) ?: quien(u.exEntorno, d.de)
        p == null || quien(u.entorno, d.de) != null
    }

    /**
     * El chat de un mensaje: si es de una persona de su gente (o de un chat con su nombre), el de esa persona (con su nombre de verdad);
     * si es un grupo («Los del barrio»), el grupo.
     */
    fun chatDe(u: Universo, d: Dm): String {
        val chat = d.chat.trim()
        if (chat.isNotBlank() && !Vida.mismo(chat, d.de)) {
            // Un chat con nombre propio: el de una persona (por si Claude pone «mamá» como chat) o un grupo
            return quien(todos(u), chat)?.nombre ?: chat
        }
        if (d.mio) return quien(todos(u), chat.ifBlank { d.de })?.nombre ?: chat.ifBlank { d.de }
        return deMensaje(u, d)?.nombre ?: d.conversacion
    }

    /** El chat de alguien de una decisión. */
    fun chatDeNombre(u: Universo, nombre: String): String = quien(todos(u), nombre)?.nombre ?: nombre

    /** ¿Es de los nuevos? (entró hace poco, y no es de los de la presentación). */
    fun nueva(u: Universo, p: Persona): Boolean = p.desde > 0 && u.paso - p.desde < NUEVO_JORNADAS

    // ---------------------------------------------------------------- juntar y separar

    /** «Es la misma persona que…»: [nombre] (un chat o un nombre suelto) pasa a ser [p] (se apunta como alias). */
    fun junta(u: Universo, nombre: String, p: Persona): Universo {
        val t = nombre.trim()
        if (t.isBlank() || Vida.mismo(t, p.nombre)) return u
        // Si el otro nombre es otra persona de su gente, se fusiona con ella (sus alias pasan a esta; la afinidad, la media)
        val otra = u.entorno.firstOrNull { it.id != p.id && (Vida.mismo(it.nombre, t)) }
        val nueva = p.copy(alias = (p.alias + t + (otra?.alias ?: emptyList()) + listOfNotNull(otra?.nombre)).distinctBy { norm(it) }.filter { !Vida.mismo(it, p.nombre) },
            noEs = p.noEs.filter { norm(it) != norm(t) }, afinidad = if (otra != null) (p.afinidad + otra.afinidad) / 2 else p.afinidad)
        return u.copy(entorno = u.entorno.filter { it.id != otra?.id }.map { if (it.id == p.id) nueva else it },
            exEntorno = u.exEntorno.map { if (it.id == p.id) nueva else it })
    }

    /** «Separar»: [nombre] ya no es [p] (aunque se parezca). */
    fun separa(u: Universo, nombre: String, p: Persona): Universo {
        val nueva = p.copy(alias = p.alias.filter { norm(it) != norm(nombre) }, noEs = (p.noEs + nombre.trim()).distinctBy { norm(it) })
        return u.copy(entorno = u.entorno.map { if (it.id == p.id) nueva else it }, exEntorno = u.exEntorno.map { if (it.id == p.id) nueva else it })
    }

    /** Los nombres con los que ha salido alguien en los chats que no son el suyo (los que se han juntado con él): para poder separarlos. */
    fun nombresJuntados(u: Universo, p: Persona, entradas: List<Entrada>): List<String> =
        entradas.flatMap { e -> e.dms.flatMap { listOf(it.de, it.chat) } + listOfNotNull(e.decision?.de) }.map { it.trim() }
            .filter { it.isNotBlank() && !Vida.mismo(it, p.nombre) && !Vida.mismo(it, "Tú") && quien(listOf(p), it) != null }.distinctBy { norm(it) }

    // ---------------------------------------------------------------- dar puerta y recuperar

    /** Le das puerta: sale de tu gente y pasa a «Ya no está en tu vida». */
    fun fuera(u: Universo, p: Persona, porque: String): Universo =
        u.copy(entorno = u.entorno.filter { it.id != p.id }, exEntorno = u.exEntorno.filter { it.id != p.id } + p.copy(salio = maxOf(1, u.paso), porque = porque.trim()))

    /** Vuelve a tu vida (con la relación tocada: la afinidad, a la mitad). */
    fun recupera(u: Universo, p: Persona): Universo =
        u.copy(exEntorno = u.exEntorno.filter { it.id != p.id }, entorno = u.entorno + p.copy(salio = 0, porque = "", afinidad = (p.afinidad / 2).coerceAtLeast(10)))

    /** Añade gente nueva (inventada) si hay sitio y no estaba ya (ni en la lista de los que se fueron). */
    fun anade(u: Universo, nuevas: List<Persona>, max: Int = 3): Universo {
        var ent = u.entorno
        nuevas.filter { n -> n.nombre.isNotBlank() && quien(todos(u), n.nombre) == null }.take(max).forEach { n ->
            if (ent.size < MAX) ent = ent + n.copy(id = n.id.ifBlank { Universo.nuevoId("p") }, afinidad = n.afinidad.coerceIn(0, 100), desde = if (n.desde > 0) n.desde else maxOf(1, u.paso))
        }
        return u.copy(entorno = ent)
    }

    // ---------------------------------------------------------------- gente nueva con la fama

    /** Si toca que entre gente nueva (ha subido de nivel desde la última vez). */
    fun tocaNueva(u: Universo): Boolean = u.nivelGente >= 0 && u.nivelActual.ordinal > u.nivelGente

    /** Cuánta gente nueva entra al subir a un nivel. */
    fun cuantas(n: Nivel): Int = when (n) { Nivel.CANTERANO -> 0; Nivel.PROMESA -> 1; Nivel.TITULAR -> 2; Nivel.ESTRELLA -> 3; Nivel.LEYENDA -> 3 }

    /** Qué gente nueva encaja con un nivel (para Claude y para las plantillas). */
    fun quienEntra(n: Nivel): String = when (n) {
        Nivel.CANTERANO, Nivel.PROMESA -> "un representante con más nombre que quiere ficharle, un periodista de la prensa nacional que empieza a seguirle o una influencer de su ciudad"
        Nivel.TITULAR -> "un representante de una gran agencia, un compañero de la selección, una presentadora de televisión o un empresario que quiere invertir con él"
        Nivel.ESTRELLA -> "un superagente internacional, una cantante o actriz famosa (inventada), un empresario de los gordos, un estilista o un periodista internacional"
        Nivel.LEYENDA -> "un magnate (inventado) que quiere un club con él, una estrella de la música (inventada), un director de cine que quiere un documental o un jeque de los negocios (inventado)"
    }

    /** La gente nueva de las plantillas (sin IA, o si Claude no trae a nadie): nombre, rol, nota y lo primero que te escriben. */
    class Nueva(val nombre: String, val rol: String, val nota: String, val mensajes: List<String>)

    val NUEVAS: Map<Nivel, List<Nueva>> = mapOf(
        Nivel.PROMESA to listOf(
            Nueva("Ramón Ferrer", "representante (quiere ficharte)", "Lleva a tres internacionales; viste trajes de 2.000 € y no da puntada sin hilo",
                listOf("Hola, soy Ramón Ferrer, de Ferrer Sports. Te sigo desde hace meses.", "Lo que estás haciendo vale el triple de lo que cobras. Si algún día quieres hablar de verdad de tu carrera, me llamas.", "Sin compromiso. Un café y te cuento cómo trabajamos.")),
            Nueva("Paula Ortega", "periodista de la prensa nacional", "De «Tribuna Ancha»; hace las entrevistas largas de los domingos",
                listOf("Buenas, soy Paula Ortega, de Tribuna Ancha.", "En la redacción ya hablamos de ti más que de algunos titulares. Me encantaría hacerte la entrevista del domingo.", "Nada de polémicas: tú, tu barrio, tu familia y lo que sueñas. ¿Te animas?")),
            Nueva("Nerea Vidal", "influencer de tu ciudad", "Moda y vida nocturna; un millón de seguidores y amigas en todas partes",
                listOf("Holaaa 👋 soy Nerea, a lo mejor me conoces de Instagram jaja", "Mis seguidores me han pedido que te escriba, que si hacemos algo juntos un día", "Una colaboración, un vídeo… o un café, que no muerdo 😇")),
        ),
        Nivel.TITULAR to listOf(
            Nueva("Íñigo Larrea", "representante de una gran agencia", "Socio de Élite Management; negocia con media Europa",
                listOf("Buenas tardes. Íñigo Larrea, de Élite Management.", "En la agencia creemos que estás en el punto exacto en el que una buena decisión vale una carrera entera.", "Tenemos contactos en tres ligas. Si te apetece, hablamos con calma.")),
            Nueva("Sergio Montes", "compañero de la selección", "Central veterano, el que organiza las cenas de la concentración",
                listOf("¡Ey, crack! Soy Sergio, el del grupo de la selección", "Te he metido en el chat de la concentración, que ahí se cuece todo", "Prepárate, que al nuevo le toca cantar en la cena 😂")),
            Nueva("Lucía Navarro", "presentadora de televisión", "Presenta «Noches de Corazón»; mitad periodista, mitad tiburón",
                listOf("Hola, soy Lucía Navarro, de «Noches de Corazón».", "Nos encantaría tenerte en el plató un día: una charla relajada, nada de pillar a nadie.", "Bueno… casi nada 😉 Piénsatelo.")),
        ),
        Nivel.ESTRELLA to listOf(
            Nueva("Marco Bellini", "superagente internacional", "Lleva a estrellas en cuatro países; dicen que mueve mercados con una llamada",
                listOf("Buonasera. Marco Bellini.", "No suelo escribir yo directamente. Si lo hago es porque lo que viene para ti es muy grande.", "Cuando quieras, en Milán o donde tú digas.")),
            Nueva("Alba Reyes", "cantante famosa", "Número uno en ventas; sus fans ya os han emparejado en la red",
                listOf("Hola 🙈 soy Alba", "Mis fans dicen que tenemos que conocernos. Yo no digo nada…", "Pero tengo concierto en tu ciudad el mes que viene. Hay entradas para ti y para quien quieras.")),
            Nueva("Gonzalo Arteaga", "empresario e inversor", "Hoteles, restaurantes y un fondo de inversión; quiere tu nombre en su próximo negocio",
                listOf("Buenos días. Gonzalo Arteaga, de Grupo Arteaga.", "Estamos abriendo una cadena de restaurantes y queremos que sea tuya a medias.", "Te dejo los números cuando quieras. Son buenos.")),
        ),
        Nivel.LEYENDA to listOf(
            Nueva("Khalid Al-Rashid", "magnate de los negocios", "Inversor con fortuna incalculable; quiere comprar un club y que tú seas la cara",
                listOf("Saludos. Mi nombre es Khalid Al-Rashid.", "Estoy preparando la compra de un club europeo y quiero construirlo alrededor de una figura como la tuya.", "Mi avión puede recogerte cuando lo desees.")),
            Nueva("Hugo Varela", "director de cine", "Ganador de varios premios; quiere rodar el documental de tu vida",
                listOf("Hola, soy Hugo Varela, director.", "Llevo meses pensando en tu historia. Hay una película ahí, de las buenas.", "Un documental de verdad, sin maquillaje. ¿Lo hablamos?")),
            Nueva("Valentina Costa", "estrella de la música internacional", "Su gira llena estadios en tres continentes",
                listOf("Hi! Bueno, hola 😄 Soy Valentina", "Me dijeron que eres fan de mi música… yo soy fan de tu fútbol", "Ven a verme a Madrid. Te guardo el mejor sitio.")),
        ),
    )

    /** Las de las plantillas para este nivel que no están ya. */
    fun plantillas(u: Universo, n: Nivel, cuantas: Int): List<Nueva> =
        NUEVAS[n].orEmpty().filter { quien(todos(u), it.nombre) == null }.take(cuantas)
}

// ---------------------------------------------------------------- las conversaciones (los mensajes y las decisiones, por persona o grupo)

/** Lo de una vez en una conversación: los mensajes de una entrada y, si es de aquí, su decisión. */
class ItemChat(val entrada: Entrada, val dms: List<Dm>, val decision: Decision?)

/**
 * Una conversación: la de una persona de tu gente ([persona]; con todos los nombres con los que ha salido, juntos) o un grupo. [personas]:
 * quién escribe (sin ti), ya con su nombre de verdad.
 */
class Conversacion(val nombre: String, val items: List<ItemChat>, val persona: Persona? = null, val personas: List<String> = emptyList()) {
    val ultima: Long get() = items.maxOfOrNull { maxOf(it.entrada.creado, it.entrada.visibleDesde) } ?: 0L
    /** Los mensajes que te han mandado (para lo de «sin leer»). */
    val recibidos: Int get() = items.sumOf { i -> i.dms.count { !it.mio } }
    val abierta: Pair<Entrada, Decision>? get() = items.lastOrNull { it.decision?.abierta == true }?.let { it.entrada to it.decision!! }
    val grupo: Boolean get() = personas.size > 1
    /** Todos sus mensajes, en orden. */
    val dms: List<Dm> get() = items.flatMap { it.dms }
}

/** Las conversaciones de una carrera, de la más reciente a la más vieja (con la gente de [u]: «mamá» y «Marisa» van al mismo chat). */
fun conversaciones(u: Universo, entradas: List<Entrada>): List<Conversacion> {
    val mapa = LinkedHashMap<String, MutableList<ItemChat>>()
    val quien = HashMap<String, MutableSet<String>>()
    entradas.sortedBy { maxOf(it.creado, it.visibleDesde) }.forEach { e ->
        val porChat = e.dms.groupBy { Gente.chatDe(u, it).ifBlank { "Alguien" } }
        val dc = e.decision?.let { Gente.chatDeNombre(u, it.de.ifBlank { "Tu gente" }) }
        porChat.forEach { (c, ms) ->
            mapa.getOrPut(c) { mutableListOf() }.add(ItemChat(e, ms, if (c == dc) e.decision else null))
            quien.getOrPut(c) { linkedSetOf() }.addAll(ms.filter { !it.mio }.map { Gente.deMensaje(u, it)?.nombre ?: it.de })
        }
        if (dc != null && dc !in porChat) mapa.getOrPut(dc) { mutableListOf() }.add(ItemChat(e, emptyList(), e.decision))
    }
    return mapa.map { (c, items) -> Conversacion(c, items, Gente.todos(u).firstOrNull { Vida.mismo(it.nombre, c) }, quien[c]?.toList().orEmpty()) }
        .sortedByDescending { it.ultima }
}
