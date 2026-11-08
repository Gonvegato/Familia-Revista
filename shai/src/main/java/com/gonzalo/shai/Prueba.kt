package com.gonzalo.shai

import android.content.Context
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import java.io.File
import kotlin.random.Random

/**
 * El modo prueba: Shai entero sin IA y sin gastar nada. Lo de fuera del campo lo escriben unas plantillas de ejemplo (no Claude), con
 * los nombres de tu carrera, tu gente, el rival y el marcador; por lo demás, es la app de verdad (las mismas pantallas, la misma
 * lógica de dinero, decisiones, ofertas y eventos). Las carreras de prueba van aparte, en este móvil (no en tu cuenta), así que no se
 * mezclan con las de verdad; y el gasto es siempre 0. Para ver las funciones, no para jugar en serio: los textos se repiten.
 */
object Prueba {
    private const val PREFS = "shai_prueba"
    @Volatile var activo: Boolean = false
        private set
    private var real: Pair<Almacen, Claude>? = null

    fun cargar(c: Context) {
        val on = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("activo", false)
        if (on) enciende(c)
    }

    fun pon(c: Context, on: Boolean) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("activo", on).apply()
        if (on) enciende(c) else apaga()
        Carreras.avisa()
    }

    /** Borra todas las carreras de prueba. */
    fun vacia() { (Carreras.almacen as? AlmacenArchivo)?.vacia(); Carreras.avisa() }

    private fun enciende(c: Context) {
        if (activo) return
        real = Carreras.almacen to Motor.claude
        Carreras.almacen = AlmacenArchivo(File(c.filesDir, "prueba.json"))
        Motor.claude = Claude({ "sk-ant-prueba" }, TransporteDePrueba())
        activo = true
    }

    private fun apaga() {
        if (!activo) return
        real?.let { (a, cl) -> Carreras.almacen = a; Motor.claude = cl }
        real = null
        activo = false
    }
}

/** Un [Almacen] en un archivo de este móvil (las carreras de prueba). */
class AlmacenArchivo(private val archivo: File) : Almacen {
    private val datos = HashMap<String, Map<String, Any?>>()
    init {
        runCatching { Json.lee(archivo.readText()).comoMapa().forEach { (k, v) -> datos[k] = v.comoMapa() } }
    }
    @Synchronized override fun nodos(): Map<String, Map<String, Any?>> = HashMap(datos)
    @Synchronized override fun pon(ref: String, campos: Map<String, Any?>) { datos[ref] = (datos[ref].orEmpty() + campos).filterValues { it != null && it != "" }; guarda() }
    @Synchronized override fun borra(ref: String) { datos.remove(ref); guarda() }
    @Synchronized fun vacia() { datos.clear(); guarda() }
    private fun guarda() { runCatching { archivo.writeText(Json.escribe(datos)) } }
}

/**
 * La API de mentira del modo prueba: contesta como la de verdad (`/v1/messages` con salida estructurada y los lotes, que acaban al
 * momento), con 0 tokens gastados y sin salir nada a la red. Lo que escribe sale del banco de [BancoPrueba], rellenado con lo de tu carrera
 * (que viene en el propio mensaje: tu nombre, tu gente, el rival, el marcador, tu nivel y tus seguidores), y no se repite: lo que ya ha
 * salido en una carrera se apunta en ella ([Carreras.usadas]).
 */
class TransporteDePrueba(private val azar: kotlin.random.Random = kotlin.random.Random.Default) : Transporte {
    private val lotes = HashMap<String, List<Map<String, Any?>>>()

    override fun post(url: String, cabeceras: Map<String, String>, cuerpo: String): Transporte.Respuesta {
        val c = Json.lee(cuerpo).comoMapa()
        return when {
            url.endsWith("/v1/messages") -> Transporte.Respuesta(200, mensaje(c, Motor.actual.get()))
            url.endsWith("/v1/messages/batches") -> synchronized(lotes) {
                val id = "msgbatch_prueba_${lotes.size + 1}_${System.currentTimeMillis()}"
                lotes[id] = c["requests"].comoLista().map { it.comoMapa() }
                Transporte.Respuesta(200, Json.escribe(mapOf("id" to id, "type" to "message_batch", "processing_status" to "in_progress", "results_url" to null)))
            }
            else -> Transporte.Respuesta(404, """{"type":"error","error":{"type":"not_found_error","message":"no"}}""")
        }
    }

    override fun get(url: String, cabeceras: Map<String, String>): Transporte.Respuesta {
        val id = url.substringAfter("/v1/messages/batches/").substringBefore("/")
        val ps = synchronized(lotes) { lotes[id] } ?: return Transporte.Respuesta(404, """{"type":"error","error":{"type":"not_found_error","message":"Ese lote ya no está (el modo prueba se reinició)"}}""")
        if (url.endsWith("/results")) return Transporte.Respuesta(200, ps.joinToString("\n") { r ->
            val uid = r["custom_id"].comoTexto().substringBefore('_').takeIf { it.startsWith("u") }
            Json.escribe(mapOf("custom_id" to r["custom_id"], "result" to mapOf("type" to "succeeded", "message" to Json.lee(mensaje(r["params"].comoMapa(), uid)))))
        })
        return Transporte.Respuesta(200, Json.escribe(mapOf("id" to id, "type" to "message_batch", "processing_status" to "ended",
            "results_url" to "https://api.anthropic.com/v1/messages/batches/$id/results")))
    }

    // ---------------------------------------------------------------- lo que se sabe de la carrera (del propio mensaje)

    private class Datos(
        val nombre: String, val apodo: String, val club: String, val rival: String, val marcador: String, val resultado: Char?, val goles: Int, val asistencias: Int,
        val nota: String, val competicion: String, val local: Boolean, val gente: List<Pair<String, String>>, val nivel: Int, val seguidores: Long, val megusta: Long,
        val sueldo: Long, val pendientes: Int, val mister: Boolean,
    ) { val alias: String get() = apodo.ifBlank { nombre.substringBefore(' ') } }

    private fun cifra(t: String, unidad: String): Long {
        val n = t.replace(".", "").replace(',', '.').toDoubleOrNull() ?: return 0
        return Math.round(n * when (unidad.trim()) { "M" -> 1_000_000.0; "mil" -> 1_000.0; else -> 1.0 })
    }

    private fun datos(t: String): Datos {
        val nombre = Regex("""- (?:Jugador|Entrenador): ([^(,\n]+)""").find(t)?.groupValues?.get(1)?.trim().orEmpty().ifBlank { "el chaval" }
        val apodo = Regex("""- (?:Jugador|Entrenador): [^\n]*?\(«([^»]+)»\)""").find(t)?.groupValues?.get(1).orEmpty()
        val club = Regex("""- Club: ([^(,\n]+)""").find(t)?.groupValues?.get(1)?.trim().orEmpty().ifBlank { "su club" }
        val partido = t.substringAfter("ACABA DE JUGAR ESTE PARTIDO:", "").substringBefore("\n\n")
        val rival = Regex("""(?:En casa contra|En el campo del) ([^(\n]+?) \(""").find(partido)?.groupValues?.get(1)?.trim().orEmpty()
        val comp = Regex("""(?:En casa contra|En el campo del) [^(\n]+? \(([^,)]+)""").find(partido)?.groupValues?.get(1)?.trim().orEmpty()
        val marcador = Regex("""\): (\d+-\d+)""").find(partido)?.groupValues?.get(1).orEmpty()
        val res = when { "(victoria)" in partido -> 'V'; "(derrota)" in partido -> 'D'; "(empate)" in partido -> 'E'; else -> null }
        val goles = Regex(""", (\d+) gol""").find(partido)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val asist = Regex(""", (\d+) asistencia""").find(partido)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val nota = Regex("""nota ([\d,]+)""").find(partido)?.groupValues?.get(1).orEmpty()
        val gente = Regex("""  · ([^,\n]+), ([^,\n]+), afinidad""").findAll(t).map { it.groupValues[1].trim() to it.groupValues[2].trim() }.toList()
        val nivelTxt = Regex("""Nivel de AHORA: ([^(.;]+)""").find(t)?.groupValues?.get(1)?.trim().orEmpty()
        val nivel = Nivel.entries.indexOfFirst { nivelTxt.startsWith(it.nombre) }.takeIf { it >= 0 } ?: 1
        val seg = Regex("""Seguidores en la red: ([\d.,]+)( M| mil)?""").find(t)?.let { cifra(it.groupValues[1], it.groupValues[2]) } ?: 5_000
        val mg = Regex("""unos ([\d.,]+)( M| mil)? me gusta""").find(t)?.let { cifra(it.groupValues[1], it.groupValues[2]) } ?: maxOf(12, seg / 60)
        val sueldo = Regex("""([\d.]+) € a la semana""").find(t)?.groupValues?.get(1)?.replace(".", "")?.toLongOrNull() ?: 5_000
        val pend = Regex("""DECISIONES PENDIENTES: (\d+)""").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return Datos(nombre, apodo, club, rival, marcador, res, goles, asist, nota, comp, "En casa contra" in partido, gente, nivel, seg, mg, sueldo, pend, "- Entrenador:" in t)
    }

    // ---------------------------------------------------------------- rellenar los huecos

    private fun alguien(d: Datos, porDefecto: String, vararg roles: String): String =
        d.gente.firstOrNull { (_, r) -> roles.any { Regex("""\b$it""", RegexOption.IGNORE_CASE).containsMatchIn(r) } }?.first ?: porDefecto

    private fun medio(d: Datos) = when (d.nivel) { 0 -> "El Eco del Barrio"; 1, 2 -> "Tribuna Ancha"; else -> "Planeta Fútbol" }
    private fun medioRosa(d: Datos) = if (d.nivel <= 1) "La Grada Rosa" else "Corazón de Papel"

    private fun rellena(s: String, d: Datos): String {
        val ape = d.nombre.substringAfter(' ', "").substringBefore(' ').ifBlank { "Ruiz" }
        var t = s
            .replace("{GOLES}", when (d.goles) { 0 -> "ningún gol"; 1 -> "un gol"; else -> "${d.goles} goles" })
            .replace("{ASIS}", when (d.asistencias) { 0 -> "ninguna asistencia"; 1 -> "una asistencia"; else -> "${d.asistencias} asistencias" })
            .replace("{A}", d.alias).replace("{N}", d.nombre).replace("{C}", d.club).replace("{R}", d.rival.ifBlank { "rival" })
            .replace("{MAR}", d.marcador.ifBlank { "partido" }).replace("{COMP}", d.competicion.ifBlank { "la liga" }).replace("{G}", "${d.goles}").replace("{AS}", "${d.asistencias}")
            .replace("{NOTA}", d.nota.ifBlank { "notable" })
            .replace("{M}", alguien(d, "Loli $ape", "madre", "padre"))
            .replace("{REP}", alguien(d, "Toni Vidal", "representante", "agente"))
            .replace("{AMI}", alguien(d, "Kike Lozano", "amigo"))
            .replace("{EX}", alguien(d, "Vera Salinas", "ex"))
            .replace("{PER}", alguien(d, "Marta Gil", "periodista"))
            .replace("{COMP2}", alguien(d, "Dani Prieto", "compañero"))
            .replace("{MEDR}", medioRosa(d)).replace("{MED}", medio(d)).replace("{TV}", "Noches de Corazón")
        t = Regex("""\{(\d+(?:\.\d+)?)S\}""").replace(t) { m -> Formato.euros(redondea(m.groupValues[1].toDouble() * maxOf(d.sueldo, 1_000))) }
        return t
    }

    private fun redondea(x: Double): Long = when { x >= 100_000 -> Math.round(x / 5_000) * 5_000; x >= 10_000 -> Math.round(x / 500) * 500; else -> Math.round(x / 100) * 100 }

    private fun rel(m: Map<String, Int>, d: Datos) = m.map { (k, v) -> mapOf("nombre" to rellena(k, d), "cambio" to v) }
    private fun efectos(e: EfP, d: Datos) = mapOf("dinero" to redondea(e.sueldos * maxOf(d.sueldo, 1_000)), "fama" to e.fama, "aficion" to e.aficion,
        "disciplina" to e.disciplina, "carisma" to e.carisma, "relaciones" to rel(e.rel, d))
    private val NADA = EfP()

    // (sin los artículos sueltos: «Fenómeno el Niño» → «FenómenoNiño», no «FenómenoelNiño»)
    private fun etiqueta(t: String) = t.split(' ', '-', '\'').filter { it.isNotBlank() && it !in setOf("el", "la", "los", "las") }.joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }.filter { it.isLetterOrDigit() }

    // ---------------------------------------------------------------- no repetir

    /** Lo que ya ha salido en esta carrera (más lo de esta misma respuesta). */
    private class Uso(val uid: String?) {
        val ya: MutableSet<String> = (uid?.let { Carreras.usadas(it) } ?: emptySet()).toMutableSet()
        val nuevas = mutableListOf<String>()
        fun guarda() { if (uid != null) Carreras.apunta(uid, nuevas) }
    }

    /** Uno de [lista] que no haya salido; si ya salieron todos, se vuelve a empezar con esa lista. */
    private fun <T> elige(u: Uso, lista: List<T>, id: (T) -> String): T? {
        if (lista.isEmpty()) return null
        var libres = lista.filter { id(it) !in u.ya }
        if (libres.isEmpty()) {
            val ids = lista.map(id).toSet()
            u.ya.removeAll(ids); u.uid?.let { Carreras.olvida(it, ids) }
            libres = lista
        }
        val x = libres[azar.nextInt(libres.size)]
        u.ya += id(x); u.nuevas += id(x)
        return x
    }

    private fun <T> eligeVarios(u: Uso, lista: List<T>, n: Int, id: (T) -> String): List<T> = (1..minOf(n, lista.size)).mapNotNull { elige(u, lista.filter { x -> id(x) !in u.nuevas }, id) }

    // ---------------------------------------------------------------- la red

    private fun usuario(n: String) = "@" + n.lowercase().replace(Regex("[áàä]"), "a").replace(Regex("[éè]"), "e").replace(Regex("[íì]"), "i").replace(Regex("[óò]"), "o")
        .replace(Regex("[úùü]"), "u").replace(Regex("[^a-z0-9ñ]"), "").take(15)

    private fun likes(d: Datos, gordo: Boolean = false) = (d.megusta * (if (gordo) 3.0 + azar.nextDouble() * 5 else 0.4 + azar.nextDouble() * 1.2)).toLong().coerceAtLeast(3)

    private fun respuestas(u: Uso, d: Datos, tono: Char): List<Map<String, Any?>> {
        val pools = when (tono) { 'V' -> listOf(BancoPrueba.RESPUESTAS_FAVOR, BancoPrueba.RESPUESTAS_CACHONDEO, BancoPrueba.RESPUESTAS_CONTRA)
            'D' -> listOf(BancoPrueba.RESPUESTAS_CONTRA, BancoPrueba.RESPUESTAS_CACHONDEO, BancoPrueba.RESPUESTAS_FAVOR)
            'B' -> listOf(BancoPrueba.TRAS_BIEN, BancoPrueba.RESPUESTAS_CACHONDEO, BancoPrueba.RESPUESTAS_CONTRA)
            'M' -> listOf(BancoPrueba.TRAS_MAL, BancoPrueba.RESPUESTAS_CACHONDEO, BancoPrueba.RESPUESTAS_FAVOR)
            else -> listOf(BancoPrueba.RESPUESTAS_CACHONDEO, BancoPrueba.RESPUESTAS_FAVOR, BancoPrueba.RESPUESTAS_CONTRA) }
        val autores = listOf("Marisa_del_Fondo", "Pepe Grada Alta", "Chema Futbolero", "Lucía 23", "El del Bombo", "Abuelo Socio", "Nacho Rojiblanco", "Rocío Tribuna", "Fran del Bar")
        return (0 until 2 + azar.nextInt(3)).map { i ->
            val pool = pools[minOf(i, pools.size - 1)]
            val txt = elige(u, pool) { "r:" + it.hashCode() } ?: pool.first()
            val a = autores[azar.nextInt(autores.size)]
            mapOf("autor" to a, "usuario" to usuario(a), "texto" to rellena(txt, d), "megusta" to (likes(d) / (6 + azar.nextInt(14))).coerceAtLeast(1), "tipo" to "aficion")
        }
    }

    private val SIN_CITA = mapOf("autor" to "", "usuario" to "", "texto" to "")

    private fun post(d: Datos, autor: String, texto: String, tipo: String, gordo: Boolean = false, resp: List<Map<String, Any?>> = emptyList(), cita: Map<String, Any?> = SIN_CITA): Map<String, Any?> {
        val l = likes(d, gordo)
        val a = rellena(autor, d)
        return mapOf("autor" to a, "usuario" to usuario(a), "texto" to frases(rellena(texto, d)), "megusta" to l, "reposts" to (l / (5 + azar.nextInt(8))).coerceAtLeast(1),
            "tipo" to tipo, "respuestas" to resp, "cita" to cita)
    }

    private fun tendencia(d: Datos, base: String) = mapOf("hashtag" to "#" + etiqueta(rellena(base, d)), "posts" to (d.seguidores / (30 + azar.nextInt(90))).coerceAtLeast(35))

    /** Un post según el nivel: a un canterano le cubre la cuenta de la cantera; a una estrella, la prensa extranjera. */
    private fun delNivel(d: Datos): Pair<String, String> = when (d.nivel) {
        0 -> "Cantera {C}" to "Orgullo de cantera: {A} sigue sumando con el primer equipo. Hace dos años jugaba en el campo de tierra de al lado. Que no se nos olvide de dónde viene 💪"
        1 -> "Tribuna Ancha" to "{A} empieza a sonar fuera de su ciudad: tres tertulias nacionales han hablado de él esta semana. La pregunta ya no es si llegará, sino cuándo"
        2 -> "Tribuna Ancha" to "{A} ya es fijo en todas las quinielas: ¿convocatoria con la selección? En el {C} nadie lo descarta y él, de momento, sonríe"
        3 -> "Planeta Fútbol" to "La prensa inglesa, la italiana y la francesa hablan hoy de {A}. «Uno de los nombres del verano», titula un diario de Londres. El {C} se tapa los oídos"
        else -> "Planeta Fútbol" to "Lo de {A} ya no es noticia en España: es noticia en el mundo. Su nombre ha sido tendencia hoy en seis países. Seis"
    }

    private fun red(u: Uso, d: Datos, texto: String = ""): Map<String, Any?> {
        val r = d.resultado ?: 'E'
        val pool = when (r) { 'V' -> BancoPrueba.POSTS_VICTORIA; 'D' -> BancoPrueba.POSTS_DERROTA; else -> BancoPrueba.POSTS_EMPATE }
        val base = eligeVarios(u, pool, 4) { it.first }
        val gol = if (d.goles > 0) eligeVarios(u, BancoPrueba.POSTS_GOL, 2) { it.first } else emptyList()
        val riv = eligeVarios(u, BancoPrueba.POSTS_RIVAL, if (r == 'V' || d.goles > 0) 2 else 1) { it.first }
        val cot = eligeVarios(u, BancoPrueba.POSTS_COTILLEO, 2) { it.first }
        val (nA, nT) = delNivel(d)
        val prensa = post(d, medio(d), when (r) { 'V' -> "{C} {MAR} {R}. {A}, protagonista de una noche que la grada tardará en olvidar. La crónica, las notas y lo que se dijo en el vestuario, en nuestra web"
            'D' -> "{C} {MAR} {R}. Noche gris y {A} señalado. La crónica, las notas y la rueda de prensa, en nuestra web"
            else -> "{C} {MAR} {R}. Un punto y muchas preguntas. La crónica, las notas y lo que dijo {A} en la zona mixta, en nuestra web" }, "prensa", gordo = true)
        val tono = when (r) { 'V' -> 'V'; 'D' -> 'D'; else -> 'E' }
        val posts = mutableListOf<Map<String, Any?>>()
        (gol + base).forEachIndexed { i, (_, a, t) -> posts += post(d, a, t, "aficion", gordo = i < 2, resp = if (i < 2) respuestas(u, d, tono) else emptyList()) }
        posts.add(1.coerceAtMost(posts.size), prensa + ("respuestas" to respuestas(u, d, tono)))
        riv.forEach { (_, a, t) -> posts += post(d, a, t, "rival") }
        cot.forEach { (_, a, t) -> posts += post(d, a, t, "prensa") }
        posts += post(d, nA, nT, "prensa")
        // Los hitos de esta jornada: la red los celebra (más si es precoz)
        Regex("""(?m)^- ([^:\n]+): ([^\n]+?)( \(PRECOZ, con (\d+) años\))?$""").findAll(texto.substringAfter("HITOS RECIÉN CONSEGUIDOS", "").substringBefore("LO QUE SHAI RECUERDA").substringBefore("DECISIONES PENDIENTES"))
            .forEach { m ->
                val precoz = m.groupValues[3].isNotBlank()
                posts.add(0, post(d, medio(d), (if (precoz) "PRECOZ. " else "") + "{A}: ${m.groupValues[1].lowercase()}. " + m.groupValues[2].replace(" Con ", " con ").substringBefore(" de los más precoces") +
                    (if (precoz) " Con ${m.groupValues[4]} años, uno de los más jóvenes del {C} en hacerlo en mucho tiempo. La grada ya hace cuentas" else " Un número redondo para su carrera"), "prensa", gordo = true, resp = respuestas(u, d, 'V')))
            }
        // Los rivales del duelo de hoy: la pulla (si es inventado) o la comparación de la prensa (si es real)
        delDuelo(texto, d).forEach { r -> postsRival(u, d, r).reversed().forEach { posts.add(minOf(2, posts.size), it) } }
        // Los récords del fútbol de verdad: si bate uno, la red no habla de otra cosa
        records(texto).forEach { rc ->
            val quien = nombreDe(rc.quien)
            when (rc.estado) {
                "BATE", "IGUALA" -> {
                    val verbo = if (rc.estado == "BATE") "BATE" else "IGUALA"
                    posts.add(0, post(d, medio(d), "HISTÓRICO. {A} $verbo el récord de $quien: ${rc.record.inicialMinuscula()}. La marca era ${rc.marca}; la suya, ${rc.su}. Guardad la fecha", "prensa", gordo = true, resp = respuestas(u, d, 'V')))
                    posts.add(1, post(d, "Datos y Fútbol", "📊 ${rc.record}:\n\n1. {A} — ${rc.su}\n2. $quien — ${rc.marca}\n\nActualizad las enciclopedias", "prensa", gordo = true))
                    posts.add(2, post(d, "Grada Joven", "Que lo he visto en directo. Que {A} acaba de ${if (rc.estado == "BATE") "quitarle un récord" else "igualar un récord"} a $quien y yo estaba ahí. Se lo contaré a mis nietos aunque no me crean", "aficion", gordo = true))
                    posts.add(3, post(d, "Analista de sofá", "Para los que decían que {A} era humo: ${rc.record.inicialMinuscula()}. Ahí lo tenéis. Ahora a ver quién sale a pedir perdón", "aficion",
                        cita = mapOf("autor" to medio(d), "usuario" to usuario(medio(d)), "texto" to "HISTÓRICO. {A} $verbo el récord de $quien".let { rellena(it, d) })))
                }
                "ESCAPA" -> {}
                else -> posts.add(0, post(d, "Datos y Fútbol", "📊 Ojo al dato: {A} se ha quedado muy cerca de un récord histórico. ${rc.texto.substringAfter("y se queda a ").let { "Se queda a $it" }}", "prensa", gordo = true, resp = respuestas(u, d, 'X')))
            }
        }
        // Cómo se le ve según su edad: el chaval que lo rompe es un terremoto; el veterano, una buena racha (o el declive)
        val vista = Regex("""Cómo se le está viendo \(para el tono\): (\p{L}+)""").find(texto)?.groupValues?.get(1).orEmpty()
        val edad = Regex(""", (\d+) años""").find(texto)?.groupValues?.get(1)?.toIntOrNull() ?: 24
        when (vista) {
            "sorpresa" -> posts.add(0, post(d, medio(d), "TERREMOTO. {A}, $edad años, y un tramo que nadie esperaba. Ya hay quien le compara con las leyendas y los ojeadores de los grandes han pedido sus vídeos. Cuidado con la presión, que a esta edad pesa", "prensa", gordo = true, resp = respuestas(u, d, 'V')))
            "buena" -> posts += post(d, "Analista de sofá", "Buena racha de {A}, eso sí: a sus $edad años sabemos lo que es. Se agradece, pero nadie va a descubrir ahora a un jugador nuevo", "aficion")
            "declive" -> posts.add(0, post(d, medio(d), "¿Está acabado {A}? A sus $edad años, los números de las últimas semanas abren el debate. El club, de momento, guarda silencio sobre su futuro", "prensa", gordo = true, resp = respuestas(u, d, 'D')))
            "dudas" -> posts += post(d, "Grada Joven", "Que nadie se vuelva loco con {A}: tiene $edad años y está aprendiendo. Paciencia, que los que hoy le critican mañana le pedirán fotos", "aficion")
        }
        // Un retuit con comentario: alguien cita la crónica
        val comentarios = listOf("Por fin alguien lo dice en un periódico", "Lo de la crónica de hoy, exagerado como siempre", "Me vale con el titular, la crónica ni la he leído",
            "Guardad este tuit para dentro de dos años", "Esto lo firmo y lo enmarco")
        posts += post(d, "Analista de sofá", comentarios[azar.nextInt(comentarios.size)], "aficion",
            cita = mapOf("autor" to medio(d), "usuario" to usuario(medio(d)), "texto" to rellena(prensa["texto"].comoTexto(), d)))
        val cuentaAlta = cuentas(texto).firstOrNull { it.urgencia == "ALTA" }
        val tag = when { records(texto).any { it.estado == "BATE" || it.estado == "IGUALA" } -> "{A} Histórico"; cuentaAlta != null -> "Cuenta Atrás {A}"; "Cómo se le está viendo (para el tono): sorpresa" in texto -> "Fenómeno {A}"; d.goles > 0 && r == 'V' -> "Gol {A}"; r == 'V' -> "{A} Show"; r == 'D' -> "{C} Despierta"; else -> "{C} {R}" }
        // La cuenta atrás de los récords que tiene a tiro (y los que se le acaban de escapar): la red y su gente
        val (pc, mc) = redCuentaAtras(u, d, texto)
        pc.reversed().forEach { posts.add(minOf(1, posts.size), it) }
        return mapOf("tendencia" to tendencia(d, tag), "posts" to posts, "mensajes" to mc + conversaciones(u, d, r, 2 + azar.nextInt(2)))
    }

    // ---------------------------------------------------------------- la cuenta atrás de los récords

    /** Un récord que tiene a tiro (de «CUENTA ATRÁS DE RÉCORDS»): por días (precocidad) o por cifra. */
    private class CuentaP(val n: Int, val record: String, val quien: String, val marca: String, val dias: Int, val plazo: String, val falta: String, val lleva: String, val urgencia: String) {
        val porDias: Boolean get() = dias >= 0
    }

    private fun cuentas(texto: String): List<CuentaP> =
        Regex("""(?m)^- CUENTA ATRÁS · #(\d+) ([^·\n]+?) · récord: (.+), ([^,·\n]+?) · (?:quedan ([\d.]+) días? \(hasta el ([^)\n]+)\)|le faltan ([^(\n]+?) para batirlo \(lleva ([^)\n]+)\)) · urgencia (ALTA|MEDIA|BAJA)$""")
            .findAll(texto).map { m ->
                val g = m.groupValues
                CuentaP(g[1].toInt(), g[2].trim(), g[3].trim(), g[4].trim(), g[5].replace(".", "").toIntOrNull() ?: -1, g[6].trim(), g[7].trim(), g[8].trim(), g[9])
            }.toList()

    private fun diasT(n: Int) = if (n == 1) "1 día" else "$n días"

    /** Los posts y los mensajes de la cuenta atrás (y de lo que se le escapa). */
    private fun redCuentaAtras(u: Uso, d: Datos, texto: String): Pair<List<Map<String, Any?>>, List<Map<String, Any?>>> {
        val posts = mutableListOf<Map<String, Any?>>()
        val ms = mutableListOf<Map<String, Any?>>()
        val madre = alguien(d, "Loli", "madre", "padre")
        val rep = alguien(d, "Toni Vidal", "representante")
        records(texto).filter { it.estado == "ESCAPA" }.take(1).forEach { rc ->
            val quien = nombreDe(rc.quien)
            val nada = "Se queda a nada" in rc.texto
            posts += post(d, medio(d), (if (nada) "POR UN SUSPIRO. " else "SE LE ESCAPA. ") + "{A} ya no podrá ser el más joven: ${rc.record.inicialMinuscula()}. El récord sigue siendo de $quien (${rc.marca})." +
                (if (nada) " Le quedaban días contados y no llegó. Habrá otros, pero este duele" else " El plazo se acabó sin el gol que lo cambiaba todo"), "prensa", gordo = true, resp = respuestas(u, d, 'D'))
            posts += post(d, "Grada Joven", if (nada) "Qué rabia lo del récord de $quien. Por tan poco… Cabeza alta, {A}, que con tu edad te quedan veinte récords más" else "Lo del récord de $quien no pudo ser. Nadie se acordará de eso si {A} sigue así", "aficion")
            ms += mapOf("chat" to madre, "de" to madre, "texto" to if (nada) "Hijo, ya sé que estabas ilusionado con lo del récord. Que te quiten eso, pero no la sonrisa, ¿eh?" else "Lo del récord da igual. A mí me da igual. Tú sigue disfrutando")
        }
        cuentas(texto).filter { it.urgencia != "BAJA" }.take(2).forEachIndexed { i, c ->
            val quien = nombreDe(c.quien)
            val ultima = c.porDias && c.dias <= 7
            if (c.urgencia == "ALTA" && i == 0) {
                posts += post(d, medio(d), when {
                    ultima -> "⏳ ÚLTIMA OPORTUNIDAD. A {A} le quedan solo ${diasT(c.dias)} para batir a $quien: ${c.record.inicialMinuscula()} ($quien lo hizo con ${c.marca}). Después del ${c.plazo}, ya no podrá ser el más joven. En el {C} todos hacen cuentas"
                    c.porDias -> "⏳ CUENTA ATRÁS. {A} tiene ${diasT(c.dias)}, hasta el ${c.plazo}, para quitarle a $quien un récord histórico: ${c.record.inicialMinuscula()} (${c.marca}). La cuenta empieza ya"
                    else -> "📈 A {A} le faltan ${c.falta} para batir a $quien: ${c.record.inicialMinuscula()} (${c.marca}). Lleva ${c.lleva}. ¿Cae esta temporada?"
                }, "prensa", gordo = true, resp = respuestas(u, d, 'X'))
                posts += post(d, "Grada Joven", if (ultima) "Que nadie le hable a {A} de lo de $quien esta semana, por favor. Dejadle jugar 🙏" else if (c.porDias) "Yo ya tengo el calendario marcado: ${diasT(c.dias)}. Vamos, {A}, que el récord de $quien está ahí" else "${c.falta}. Solo ${c.falta}. Vamos, {A}", "aficion")
                posts += post(d, "Datos y Fútbol", "📊 ${c.record}\n\nAhora: $quien — ${c.marca}\n{A}: " + (if (c.porDias) "le quedan ${diasT(c.dias)}" else "lleva ${c.lleva}"), "prensa")
                // Su gente: uno le mete presión y otro se la quita
                ms += mapOf("chat" to rep, "de" to rep, "texto" to if (c.porDias) "Lo de $quien: si cae antes del ${c.plazo}, el teléfono no va a parar. Te lo digo para que lo sepas, no para que te agobies" else "Si superas lo de $quien, renegociamos sin pedir permiso a nadie")
                ms += mapOf("chat" to madre, "de" to madre, "texto" to if (ultima) "Que no me entere yo de que no duermes por lo del récord. Tú juega como en el patio del colegio" else "He visto en la tele lo del récord. Ni caso, hijo: tú a lo tuyo")
            } else posts += post(d, "Analista de sofá", "Ojo, que a {A} " + (if (c.porDias) "le quedan ${diasT(c.dias)} para el récord de $quien (${c.record.inicialMinuscula()})" else "le faltan ${c.falta} para el récord de $quien") + ". Nadie habla de esto", "aficion")
        }
        return posts to ms
    }

    /** El titular secundario de la cuenta atrás (o de lo que se le escapa), si lo hay. */
    private fun secCuentaAtras(texto: String): Pair<String, String>? {
        records(texto).firstOrNull { it.estado == "ESCAPA" }?.let { rc ->
            val quien = nombreDe(rc.quien)
            val nada = "Se queda a nada" in rc.texto
            return (if (nada) "Por un suspiro: se le escapa el récord de $quien" else "Se le escapa el récord de $quien") to
                "{A} ya no podrá ser el más joven: ${rc.record.inicialMinuscula()}. El récord sigue siendo de $quien (${rc.marca})" + (if (nada) ", y esta vez por muy poco." else ".")
        }
        val c = cuentas(texto).firstOrNull { it.urgencia == "ALTA" } ?: return null
        val quien = nombreDe(c.quien)
        return when {
            c.porDias && c.dias <= 7 -> "Última bala para el récord de $quien" to "A {A} le quedan ${diasT(c.dias)} para ser el más joven: ${c.record.inicialMinuscula()} ($quien, ${c.marca}). Después del ${c.plazo}, se acabó."
            c.porDias -> "${diasT(c.dias).replaceFirstChar { it.uppercase() }} para el récord de $quien" to "La cuenta atrás de {A}: tiene hasta el ${c.plazo} para ser el más joven en ${c.record.inicialMinuscula().removePrefix("goleador más joven en ").removePrefix("debutante más joven en ")} ($quien lo hizo con ${c.marca})."
            else -> "A ${c.falta} del récord de $quien" to "{A} lleva ${c.lleva}: ${c.record.inicialMinuscula()} ($quien, ${c.marca}) está más cerca que nunca."
        }
    }

    private fun conversaciones(u: Uso, d: Datos, r: Char, n: Int): List<Map<String, Any?>> {
        val ok = BancoPrueba.CONVERSACIONES.filter { it.cuando.isEmpty() || it.cuando == "$r" }
        return eligeVarios(u, ok, n) { it.id }.flatMap { c -> c.ms.map { (de, t) -> mapOf("chat" to rellena(c.chat, d), "de" to rellena(de, d), "texto" to rellena(t, d)) } }
    }

    // ---------------------------------------------------------------- las decisiones

    private fun decision(d: Datos, e: EvP?): Map<String, Any?> = if (e == null) mapOf("hay" to false, "de" to "", "titulo" to "", "contexto" to "", "en_juego" to "", "pregunta" to "", "opciones" to emptyList<String>())
        else mapOf("hay" to true, "de" to rellena(e.de, d), "titulo" to rellena(e.titulo, d), "contexto" to rellena(e.texto, d), "en_juego" to rellena(e.enJuego, d),
            "pregunta" to rellena(e.pregunta, d), "opciones" to e.ops.map { rellena(it.texto, d) })

    private fun todos() = BancoPrueba.PERSONALES + BancoPrueba.IMAGEN + BancoPrueba.PRENSA + BancoPrueba.VACACIONES

    /** El evento del banco cuyo título (ya rellenado) es este. */
    private fun busca(titulo: String, d: Datos): EvP? = titulo.trim().takeIf { it.isNotBlank() }?.let { t -> todos().firstOrNull { rellena(it.titulo, d) == t } }

    // ---------------------------------------------------------------- las respuestas, según lo que se pide (su esquema)

    private fun mensaje(c: Map<String, Any?>, uid: String?): String {
        val props = c["output_config"].comoMapa()["format"].comoMapa()["schema"].comoMapa()["properties"].comoMapa().keys
        val texto = c["messages"].comoLista().flatMap { it.comoMapa()["content"].comoLista() }.joinToString("\n") { it.comoMapa()["text"].comoTexto() }
        val d = datos(texto)
        val u = Uso(uid)
        val r: Any = when {
            "se_ve" in props -> foto()
            "memoria" in props -> mapOf("memoria" to memoria(texto, d))
            "balance" in props -> temporada(u, d, texto)
            "gente" in props -> inicio(u, d)
            "sin_contestar" in props -> prensa(u, d, texto)
            "titular" in props -> respuesta(u, d, texto)
            "titulo" in props -> evento(u, d, texto)
            "ES OFICIAL" in texto -> oficial(u, d, texto)
            else -> red(u, d, texto)
        }
        u.guarda()
        // «de el Niño» → «del Niño», «a el Niño» → «al Niño» (los apodos con artículo)
        val limpio = Json.escribe(r).replace(Regex("""(?<!\p{L})([Dd])e el(?!\p{L})"""), "$1el").replace(Regex("""(?<!\p{L})([Aa]) el(?!\p{L})"""), "$1l")
        return Json.escribe(mapOf("id" to "msg_prueba", "type" to "message", "role" to "assistant", "model" to c["model"].comoTexto(),
            "content" to listOf(mapOf("type" to "text", "text" to limpio)), "stop_reason" to "end_turn",
            "usage" to mapOf("input_tokens" to 0, "output_tokens" to 0, "cache_creation_input_tokens" to 0, "cache_read_input_tokens" to 0)))
    }

    private fun foto() = mapOf("se_ve" to true, "goles_favor" to 2, "goles_contra" to 1, "rival" to "Valencia CF", "minutos" to 90, "goles" to 1, "asistencias" to 1, "nota" to 7.8,
        "datos_equipo" to listOf(mapOf("dato" to "Posesión", "valor" to "55 / 45"), mapOf("dato" to "Tiros", "valor" to "14 / 9"), mapOf("dato" to "Tiros a puerta", "valor" to "6 / 3")),
        "rendimiento" to "4 tiros, 2 a puerta, 31 pases (modo prueba: la foto no se ha leído de verdad)", "dudas" to "Modo prueba: estos datos son de ejemplo, no salen de tu foto.")

    /** Un récord real de los que ha comparado la app (la línea que va a Claude): batido, igualado o rozado. */
    private class RecordP(val estado: String, val n: Int, val record: String, val quien: String, val marca: String, val su: String, val texto: String)

    private fun records(texto: String): List<RecordP> =
        Regex("""(?m)^- (BATE|IGUALA|CERCA|ESCAPA) · #(\d+) ([^·\n]+?) · récord: (.+), ([^,·\n]+?) · él: ([^·\n]+?) · ([^\n]+)$""")
            .findAll(texto.substringAfter("RÉCORDS HISTÓRICOS REALES", "")).map { m ->
                val g = m.groupValues
                RecordP(g[1], g[2].toInt(), g[3].trim(), g[4].trim(), g[5].trim(), g[6].trim(), g[7].trim())
            }.toList()

    /** Un rival de los que vienen en el contexto («TUS RIVALES»). [real]: futbolista de verdad (solo lo deportivo). */
    private class RivalP(val nombre: String, val club: String, val posicion: String, val origen: String, val intensidad: Int, val real: Boolean)

    private fun rivales(texto: String): List<RivalP> =
        Regex("""(?m)^- RIVAL · ([^·\n]+) · ([^·\n]+) · ([^·\n]+) · ([^·\n]+) · intensidad (\d+)/100 \([^)\n]*\) · (INVENTADO|REAL)""").findAll(texto).map { m ->
            val g = m.groupValues
            RivalP(g[1].trim(), g[2].trim(), g[3].trim(), g[4].trim(), g[5].toInt(), g[6] == "REAL")
        }.toList()

    /** Los rivales del club de este partido (un duelo directo). */
    private fun delDuelo(texto: String, d: Datos) = rivales(texto).filter { d.rival.isNotBlank() && it.club.equals(d.rival, ignoreCase = true) }

    // Las pullas de un rival inventado tras un duelo, y lo que dice la red (con su respuesta)
    private val PULLAS_RIVAL = listOf(
        "Hay jugadores que juegan al fútbol y otros que juegan a salir en Instagram. Hoy se ha visto quién es quién 🤫",
        "Mucho ruido en la previa. Luego, en el campo, cada uno en su sitio. Buen viaje de vuelta, chaval",
        "A algunos les queda grande la camiseta, pero bien que les queda el flequillo. Nos vemos en la vuelta",
        "Gracias a la afición por el recibimiento. Al '{AT}', ninguna: que siga contando seguidores, que goles le cuestan")
    private val PULLAS_VUELTA = listOf("Los goles hablan solos. Los tuyos, hoy, estaban afónicos 🗣️", "Te espero en la vuelta. Tráete el móvil, que vas a querer grabarlo",
        "Tranquilo, que en tu casa también hay espejos", "Dile a tu representante que me escriba cuando tengas mis números")

    /** Los posts del duelo con un rival: si es inventado, la pulla (y su respuesta); si es real, solo la comparación de la prensa con cifras. */
    private fun postsRival(u: Uso, d: Datos, r: RivalP): List<Map<String, Any?>> = if (r.real) listOf(
        post(d, "Datos y Fútbol", "📊 Duelo de nombres en el {C}-{R}: {A} contra ${r.nombre}. Esta noche, {GOLES} para {A}. La prensa ya pone sus números uno al lado del otro y el debate está servido (en la grada, no entre ellos)", "prensa", gordo = true, resp = respuestas(u, d, 'X')),
        post(d, medio(d), "El cara a cara que todos miraban: {A} frente a ${r.nombre} (${r.club}). Lo nuestro son las cifras: {GOLES} y una nota de {NOTA} para {A}. El resto, en el campo", "prensa"))
    else {
        val i = elige(u, PULLAS_RIVAL.indices.toList()) { "pulla:$it" } ?: 0
        val pulla = PULLAS_RIVAL[i].replace("{AT}", d.alias.removePrefix("el ").removePrefix("El "))
        val autorR = r.nombre
        listOf(post(d, autorR, pulla, "rival", gordo = true, resp = listOf(
                mapOf("autor" to d.alias, "usuario" to usuario(d.alias), "texto" to rellena(PULLAS_VUELTA[azar.nextInt(PULLAS_VUELTA.size)], d), "megusta" to likes(d, true), "tipo" to "entorno"),
                mapOf("autor" to "Grada Joven", "usuario" to "gradajoven", "texto" to "Que se peleen en el campo, que fuera dan vergüenza ajena. Pero que sigan, que esto es lo mejor de la semana 🍿", "megusta" to likes(d), "tipo" to "aficion"))),
            post(d, "{MEDR}", "GUERRA ABIERTA. ${r.nombre} (${r.club}) y {A} se han dicho de todo en redes tras el {C}-{R}. Lo de ellos ya no es fútbol: es un culebrón, y tenemos los capítulos", "prensa", gordo = true, resp = respuestas(u, d, 'X')))
    }

    /** Lo que cambia con los rivales en la prensa de este partido (y, a veces, uno nuevo: siempre inventado). */
    private fun cambiosRival(u: Uso, d: Datos, texto: String): List<Map<String, Any?>> {
        val l = delDuelo(texto, d).map { r ->
            if (r.real) mapOf("nombre" to r.nombre, "club" to r.club, "posicion" to r.posicion, "origen" to r.origen, "cambio" to 3,
                "choque" to "La prensa compara sus números tras el {C}-{R}: {GOLES} para {A}.".let { rellena(it, d) }, "tipo" to Choque.DEPORTIVO, "nuevo" to false)
            else mapOf("nombre" to r.nombre, "club" to r.club, "posicion" to r.posicion, "origen" to r.origen, "cambio" to 7,
                "choque" to "Se dijeron de todo en redes tras el {C}-{R}: pulla de ${r.nombre} y respuesta de {A}; {MEDR} ya lo llama «guerra abierta».".let { rellena(it, d) },
                "tipo" to Choque.PERSONAL, "nuevo" to false)
        }
        // El que le roba protagonismo (inventado) tras una derrota fea, si aún tiene pocos rivales
        val nuevo = if (d.resultado == 'D' && rivales(texto).size < 3 && d.rival.isNotBlank() && delDuelo(texto, d).isEmpty() && azar.nextInt(2) == 0) listOf(
            mapOf("nombre" to listOf("Iván Saldaña", "Rayan Ferrer", "Bastian Kroll", "Gael Montoro").let { it[azar.nextInt(it.size)] }, "club" to d.rival, "posicion" to "extremo",
                "origen" to "Le hizo el gesto de silencio a la grada del {C} tras el {MAR} y luego se rio de {A} en redes".let { rellena(it, d) }, "cambio" to 10,
                "choque" to "Celebró delante de {A} con el dedo en la boca y luego subió la foto: «Silencio en {C}».".let { rellena(it, d) }, "tipo" to Choque.PERSONAL, "nuevo" to true))
            else emptyList()
        return l + nuevo
    }

    /** El protagonista del récord, sin el contexto («Ansu Fati (FC Barcelona) – ante el Inter…» → «Ansu Fati»). */
    private fun nombreDe(quien: String) = quien.substringBefore(" (").substringBefore(" –").trim()

    /** La portada de un récord batido: lo más gordo que le puede pasar (más cuanto más joven y más conocido). */
    private fun portadaRecord(r: RecordP, d: Datos, edad: Int): Map<String, Any?> {
        val quien = nombreDe(r.quien)
        val joven = edad <= 21
        val alcance = when (d.nivel) { 0 -> "En el barrio no se habla de otra cosa y la noticia ya ha saltado a la prensa nacional"; 1, 2 -> "La noticia ha abierto todos los informativos deportivos del país"; else -> "La noticia ha dado la vuelta al mundo en una hora: portadas en Londres, Milán y Buenos Aires" }
        val titular = if (r.estado == "IGUALA") "{A} iguala a $quien" else when { joven && edad <= 18 -> "{A} rompe la historia"; joven -> "{A} ya es historia"; else -> "{A} entra en la historia" }
        val entradilla = if (r.estado == "IGUALA") "Con ${r.su}, {A} se pone a la altura de $quien en un récord que parecía intocable: ${r.record.inicialMinuscula()}."
            else "Con ${r.su}, {A} supera a $quien (${r.marca}) y se queda con un récord que llevaba años sin dueño nuevo: ${r.record.inicialMinuscula()}."
        val articulo = listOf(
            "Hay noches que se cuentan durante décadas y la de ayer ante el {R} va a ser una de ellas. {A} ${if (r.estado == "IGUALA") "igualó" else "batió"} el récord de $quien: ${r.record.inicialMinuscula()}. " +
                "La marca era ${r.marca}; la suya, ${r.su}." + (Regex("""Lo bate por [^.]+\.""").find(r.texto)?.value?.let { " $it" } ?: ""),
            "$alcance. " + (if (joven) "Y lo que más asusta es la edad: a la mayoría de los chavales de su quinta todavía les cuesta ganarse un sitio en el banquillo; {A} ya figura en los libros. En el {C} piden calma, pero hasta su entrenador sonrió al salir a la sala de prensa: «Que lo disfrute, que se lo ha ganado. Mañana, a entrenar como siempre»."
                else "A su edad ya nadie le descubre, pero esto le pone en otra liga: la de los nombres que salen en las preguntas de los concursos. En el {C} lo celebraron como un título y el vestuario le hizo pasillo en el entrenamiento de esta mañana."),
            "{M} lo vio desde la grada con la bufanda en la cara y {REP} no ha soltado el teléfono desde el pitido final: «No voy a hablar de dinero hoy», dijo. Lo dijo dos veces. La cuenta oficial de la competición ya ha publicado la efeméride y las enciclopedias de internet llevan toda la noche cambiando su nombre de sitio. Es el momento de {A}, y lo sabe todo el mundo.")
        return prensaDe(medio(d), titular, entradilla, articulo.joinToString("\n\n"), listOf(
            "La marca de $quien" to "${r.marca}: así se quedó el récord que {A} ${if (r.estado == "IGUALA") "ha igualado" else "acaba de borrar"}.",
            "Lo que viene ahora" to (if (joven) "Las comparaciones, la presión y los grandes de Europa preguntando por él: lo difícil empieza mañana." else "Una marca para la historia y una pregunta: ¿cuánto le queda por batir?"),
            "{M}, en la grada" to "Lloró, cantó y le hizo una foto a la pantalla del marcador. «Para el grupo de la familia», dice."), d)
    }

    /** Mayúscula al empezar cada frase («el Niño rompe…» → «El Niño rompe…»: los apodos con artículo). */
    private fun frases(t: String): String = Regex("""(^|[.!?]\s+|\n)(\p{Ll})""").replace(t) { m -> m.groupValues[1] + m.groupValues[2].uppercase() }

    private fun prensaDe(cab: String, titular: String, entradilla: String, articulo: String, sec: List<Pair<String, String>>, d: Datos) = mapOf(
        "cabecera" to rellena(cab, d), "titular" to frases(rellena(titular, d)), "entradilla" to frases(rellena(entradilla, d)), "articulo" to frases(rellena(articulo, d)),
        "secundarios" to sec.map { (t, e) -> mapOf("titular" to frases(rellena(t, d)), "entradilla" to frases(rellena(e, d))) })

    private fun ofertas(u: Uso, d: Datos, n: Int): List<Map<String, Any?>> {
        val nivel = d.nivel.coerceIn(0, 4)
        val r = Rangos.de(Nivel.entries[nivel])
        return eligeVarios(u, BancoPrueba.OFERTAS[nivel], n) { "of:" + it.de + it.tipo }.map { o ->
            val sueldo = if (o.tipo == "patrocinio") redondea(r.patroSemana * (0.3 + azar.nextDouble() * 0.5)) else redondea((d.sueldo * o.xSueldo).toDouble()).coerceIn(r.sueldo.first, r.sueldo.last)
            val prima = if (o.tipo == "patrocinio") redondea(r.patroPrima * (0.3 + azar.nextDouble() * 0.6)) else redondea(minOf(r.primaMax.toDouble(), sueldo * o.xPrima))
            mapOf("de" to rellena(o.de, d), "tipo" to o.tipo, "salario_semanal" to sueldo, "prima" to prima, "anios" to o.anios, "texto" to rellena(o.texto, d))
        }
    }

    private fun prensa(u: Uso, d: Datos, texto: String): Map<String, Any?> {
        val r = d.resultado ?: 'E'
        val arts = BancoPrueba.ARTICULOS.filter { it.cuando == "$r" || (it.cuando == "VG" && r == 'V' && d.goles > 0) }
        val a = elige(u, arts) { it.id } ?: BancoPrueba.ARTICULOS.first()
        val sec = eligeVarios(u, BancoPrueba.SECUNDARIOS, 3) { "s:" + it.first }
        val cuando = setOf("", "$r") + (if (d.goles > 0) setOf("G") else emptySet())
        val cabe = d.pendientes < Narrador.MAX_ABIERTAS && azar.nextInt(10) < 6
        val dec = if (cabe) elige(u, BancoPrueba.PRENSA.filter { it.cuando in cuando && d.nivel in it.nivelMin..it.nivelMax }) { it.id } else null
        // Lo que no contestó: su consecuencia (la del banco, si es suyo)
        val sinContestar = Regex("""(?m)^(\d+)\. «([^»]+)»""").findAll(texto.substringAfter("NO CONTESTÓ A ESTO", "")).map { m ->
            val ev = busca(m.groupValues[2], d)
            mapOf("n" to m.groupValues[1].toInt(),
                "consecuencia" to (ev?.let { rellena(it.ignorar, d) } ?: "Lo dejaste pasar y no sentó bien: en tu entorno lo han tomado como que no te importaba. Se ha comentado en la red y alguno no te lo va a olvidar."),
                "efectos" to efectos(ev?.efIgnorar ?: EfP(carisma = -1), d))
        }.toList()
        val ef = when (r) { 'V' -> EfP(aficion = 2 + azar.nextInt(2), carisma = 1); 'D' -> EfP(aficion = -2 - azar.nextInt(2)); else -> NADA }
        val grande = records(texto).firstOrNull { it.estado == "BATE" || it.estado == "IGUALA" }
        val edad = Regex(""", (\d+) años""").find(texto)?.groupValues?.get(1)?.toIntOrNull() ?: 24
        val duelo = delDuelo(texto, d).firstOrNull()
        val sec2 = if (duelo != null) listOf((if (duelo.real) "{A} y ${duelo.nombre}, cifra a cifra" to "La comparación de la noche: {GOLES} para {A} ante el ${duelo.club}. Los números, uno al lado del otro."
            else "{A} y ${duelo.nombre}, guerra abierta" to "Lo del ${duelo.club} ya es personal: pullas en redes, gestos en el campo y una vuelta que nadie se quiere perder.")) + sec.drop(1) else sec
        return mapOf(
            "prensa" to (grande?.let { portadaRecord(it, d, edad) } ?: prensaDe(medio(d), a.titular, a.entradilla, a.articulo, secCuentaAtras(texto)?.let { listOf(it) + sec2.take(2) } ?: sec2, d)),
            "ofertas" to if (azar.nextInt(4) == 0) ofertas(u, d, 1) else emptyList(),
            "decision" to decision(d, dec), "sin_contestar" to sinContestar,
            "efectos" to efectos(ef, d) + ("rivales" to cambiosRival(u, d, texto)), "gente_nueva" to emptyList<Any>())
    }

    private fun evento(u: Uso, d: Datos, texto: String): Map<String, Any?> {
        val tipo = when { "Esta vez, algo de su vida personal" in texto -> 1; "Esta vez, algo de su imagen" in texto -> 2; else -> 1 + azar.nextInt(2) }
        val banco = (if (tipo == 1) BancoPrueba.PERSONALES else BancoPrueba.IMAGEN).filter { d.nivel in it.nivelMin..it.nivelMax }
        val e = elige(u, banco) { it.id }!!
        val conDecision = d.pendientes < Narrador.MAX_ABIERTAS
        val (autor, t) = e.post
        val gordo = post(d, autor, t, "prensa", gordo = true, resp = respuestas(u, d, 'X'))
        val eco = listOf("Lo de {A} esta semana da para una serie. Y para dos temporadas", "¿Alguien más está siguiendo lo de {A} minuto a minuto o soy solo yo? 🍿",
            "Esto en el vestuario del {C} tiene que ser un cachondeo", "A ver cómo sale {A} de esta. Apuesto un café")
        return mapOf("titulo" to rellena(e.titulo, d), "texto" to rellena(e.texto, d), "tendencia" to tendencia(d, "{A}"),
            "posts" to listOf(gordo, post(d, "Cuenta de humor", eco[azar.nextInt(eco.size)], "aficion")),
            "mensajes" to e.dms.map { (de, x) -> mapOf("chat" to rellena(e.chat, d), "de" to rellena(de, d), "texto" to rellena(x, d)) },
            "decision" to decision(d, if (conDecision) e else null),
            "ofertas" to emptyList<Any>(), "efectos" to efectos(EfP(fama = 1), d),
            "gente_nueva" to listOfNotNull(e.nueva?.let { (n, r, nota) -> mapOf("nombre" to n, "rol" to r, "nota" to rellena(nota, d)) }))
    }

    private fun respuesta(u: Uso, d: Datos, texto: String): Map<String, Any?> {
        val situacion = texto.substringAfter("LA SITUACIÓN:", "").lineSequence().firstOrNull()?.trim().orEmpty()
        val eligio = texto.substringAfter("LO QUE HA DECIDIDO ÉL:", "").lineSequence().firstOrNull()?.trim().orEmpty()
        val ev = todos().firstOrNull { situacion.startsWith(rellena(it.titulo, d)) }
        val op = ev?.ops?.firstOrNull { rellena(it.texto, d) == eligio }
        val pasa: String; val ef: EfP
        val rep = alguien(d, "Toni Vidal", "representante")
        if (op != null) { pasa = rellena(op.pasa, d); ef = op.ef }
        else if ("quiere renegociar" in situacion) when {
            eligio.startsWith("Que la pida") -> { pasa = "$rep llamó al club esa misma tarde y no se cortó: «Mi jugador está rindiendo como uno de los buenos y cobra como un chaval». En el club no les hizo gracia el tono, pero han prometido una oferta. En el vestuario ya se sabe y hay quien te mira distinto."; ef = EfP(disciplina = -1, rel = mapOf("{REP}" to 6)) }
            eligio.startsWith("Esperar") -> { pasa = "Le dijiste a $rep que esperara al final de temporada. Lo aceptó a regañadientes: «Si sigues así, te van a pagar el doble… o te lo van a ofrecer otros». El club, de momento, no sabe nada."; ef = EfP(disciplina = 2, rel = mapOf("{REP}" to -2)) }
            else -> { pasa = "Le dijiste a $rep que no se tocaba nada: «Firmé lo que firmé». En el club lo saben y lo agradecen; $rep, no tanto. En la grada, cuando se ha sabido, te han aplaudido."; ef = EfP(aficion = 4, disciplina = 2, rel = mapOf("{REP}" to -6)) }
        }
        else {
            val bien = azar.nextBoolean()
            pasa = "Hizo lo que le pareció: «$eligio». " + if (bien) "Y le salió bien: en su entorno lo entendieron, la red lo aplaudió y en {MEDR} no supieron qué titular ponerle." else "Y no salió como esperaba: en su entorno hay quien no lo ha entendido, la red se ha dividido y {MEDR} ya tiene titular para mañana."
            ef = if (bien) EfP(fama = 2, carisma = 3) else EfP(fama = 2, carisma = -2, disciplina = -2)
        }
        val bueno = ef.aficion + ef.carisma + ef.disciplina + ef.rel.values.sum() >= 0
        val titulares = if (bueno) listOf("{A} acierta", "Jugada maestra de {A}", "{A} sale reforzado") else listOf("{A}, en el ojo del huracán", "La que ha liado {A}", "{A} no da una fuera del campo")
        val de = ev?.let { rellena(it.de, d) } ?: alguien(d, "Toni Vidal", "representante")
        return mapOf("texto" to rellena(pasa, d), "titular" to rellena(titulares[azar.nextInt(titulares.size)], d), "tendencia" to tendencia(d, "{A}"),
            "posts" to listOf(
                post(d, "{MEDR}", "Última hora sobre {A}: " + rellena(pasa, d).substringBefore(". ") + ". Lo contamos todo en nuestra web 👀", "prensa", gordo = true, resp = respuestas(u, d, if (bueno) 'B' else 'M')),
                post(d, "Peña El Bombo", if (bueno) "Así nos gusta, {A}. Con cabeza fuera y dentro del campo" else "Menos líos y más fútbol, {A}. Que la grada tiene memoria", "aficion"),
                post(d, "Cuenta de humor", if (bueno) "Plot twist: {A} ha tomado una buena decisión. Guardad el tuit por si no vuelve a pasar" else "Que alguien le quite el móvil a {A} durante una semana, por su bien y por el nuestro", "aficion")),
            "mensajes" to listOf(
                mapOf("chat" to de, "de" to de, "texto" to if (bueno) "Muy bien hecho. De verdad" else "No sé si ha sido buena idea…"),
                mapOf("chat" to de, "de" to de, "texto" to if (bueno) "Ya me han escrito tres personas para decirme que has estado a la altura" else "Mañana hablamos con calma, ¿vale?"),
                mapOf("chat" to alguien(d, "Loli", "madre", "padre"), "de" to alguien(d, "Loli", "madre", "padre"), "texto" to if (bueno) "Hijo, estoy muy orgullosa" else "Ya hablaremos tú y yo")),
            "efectos" to efectos(ef, d), "gente_nueva" to emptyList<Any>())
    }

    private fun inicio(u: Uso, d: Datos): Map<String, Any?> {
        val ape = d.nombre.substringAfter(' ', "").substringBefore(' ').ifBlank { "Ruiz" }
        val gente = listOf(
            mapOf("nombre" to "Loli $ape", "rol" to "madre", "afinidad" to 85, "nota" to "Lo ve todo y lo comenta todo en el grupo de la familia; va a todos los partidos en casa"),
            mapOf("nombre" to "Toni Vidal", "rol" to "representante", "afinidad" to 60, "nota" to "Siempre al teléfono, siempre con un negocio; se lleva el 15 %"),
            mapOf("nombre" to "Kike Lozano", "rol" to "amigo de siempre", "afinidad" to 75, "nota" to "Del barrio y del colegio; vive de ti un poco más de lo que debería"),
            mapOf("nombre" to "Vera Salinas", "rol" to "ex", "afinidad" to 30, "nota" to "Influencer de moda, 300.000 seguidores; no te ha olvidado y lo dice con indirectas"),
            mapOf("nombre" to "Marta Gil", "rol" to "periodista", "afinidad" to 45, "nota" to "Te sigue desde la cantera; huele las exclusivas y nunca te ha fallado"),
            mapOf("nombre" to "Dani Prieto", "rol" to "compañero del vestuario", "afinidad" to 55, "nota" to "El que organiza todas las cenas y todas las fiestas"))
        val (titulo, txt, titular, entr) = when (d.nivel) {
            0 -> listOf("Del filial al primer equipo", "{A} sube al primer equipo del {C} con la mochila del filial todavía a cuestas. En la ciudad deportiva le conocen todos; fuera de ella, casi nadie. Su madre ha avisado a medio barrio y la cuenta de la cantera ya ha subido su foto de cuando tenía diez años.", "Un chaval del barrio en el primer equipo", "{A} da el salto desde la cantera del {C}.")
            1 -> listOf("Empieza a sonar", "{A} llega a esta temporada en el {C} con la etiqueta de promesa colgada del cuello. Las tertulias nacionales ya le han dedicado algún minuto y su representante tiene el teléfono más ocupado que nunca.", "La promesa que ya suena", "{A} quiere dar el paso en el {C}.")
            2 -> listOf("Fijo en el {C}", "{A} empieza la temporada como uno de los fijos del {C} y una cara conocida en todo el país. Las marcas le llaman, la grada le exige y la prensa rosa ya ha empezado a fijarse en quién le acompaña.", "{A}, la cara del {C}", "Titular indiscutible y nombre conocido: empieza el año de la confirmación.")
            3 -> listOf("La estrella llega", "{A} aterriza en la temporada como una de las estrellas de su liga: portadas, marcas y una legión de seguidores que comenta cada gesto. Cada paso que da fuera del campo es noticia, y él lo sabe.", "Llega la estrella", "{A} abre una temporada con medio continente mirándole.")
            else -> listOf("El mundo mira al {C}", "{A} es uno de los mejores jugadores del planeta y empieza la temporada con el {C} como si fuera un acontecimiento mundial. Televisiones de varios países han mandado cámaras y su nombre es tendencia antes de tocar un balón.", "El mundo entero, pendiente", "{A} empieza el año como lo que es: una leyenda en activo.")
        }
        val art = "$txt\n\nEn el club insisten en que es «uno más», aunque nadie se lo cree del todo. Los compañeros le han recibido con la broma de siempre y el capitán le ha dado el primer consejo: «Aquí se habla en el campo».\n\nFuera, la vida también arranca: su gente de siempre a su alrededor, una ex que no se ha ido del todo y una periodista, Marta Gil, que no le pierde la pista. Va a ser una temporada larga."
        return mapOf("titulo" to rellena(titulo, d), "texto" to rellena(txt, d), "gente" to gente,
            "prensa" to prensaDe(medio(d), titular, entr, art, eligeVarios(u, BancoPrueba.SECUNDARIOS, 2) { "s:" + it.first }, d),
            "tendencia" to tendencia(d, "Bienvenido{A}"),
            "posts" to listOf(post(d, "Peña El Bombo", "Bienvenido a lo grande, {A}. Aquí se suda la camiseta y se canta hasta el final. La grada te espera el domingo con la bufanda y con la exigencia de siempre", "aficion", gordo = true, resp = respuestas(u, d, 'V')),
                post(d, "Marta Gil", "Primera exclusiva de la temporada: {A} ya tiene casa en la ciudad. Y vistas. Pronto, más", "prensa"),
                post(d, "Vera Salinas", "Hay gente que cambia de ciudad y no cambia nada más 🙃", "entorno", gordo = true, resp = respuestas(u, d, 'X')),
                post(d, "Kike Lozano", "¡Ese es mi hermano! Del campo de tierra del barrio a esto. Que nadie te cambie, crack 🔥", "entorno"),
                post(d, delNivel(d).first, delNivel(d).second, "prensa")),
            "mensajes" to listOf(
                mapOf("chat" to "Loli $ape", "de" to "Loli $ape", "texto" to "Abrígate, que en ese estadio hace un frío que pela"),
                mapOf("chat" to "Loli $ape", "de" to "Loli $ape", "texto" to "Y llama a tu tía, que está muy orgullosa y no para de enseñar tu foto en la peluquería"),
                mapOf("chat" to "Toni Vidal", "de" to "Toni Vidal", "texto" to "Tengo dos marcas interesadas. No firmes nada sin hablar conmigo"),
                mapOf("chat" to "Toni Vidal", "de" to "Toni Vidal", "texto" to "Y nada de fiestas la primera semana, que te conozco")))
    }

    private fun oficial(u: Uso, d: Datos, texto: String): Map<String, Any?> {
        val m = Regex("""ha aceptado (\S+) con ([^:]+):""").find(texto)
        val tipo = m?.groupValues?.get(1).orEmpty(); val club = m?.groupValues?.get(2)?.trim().orEmpty().ifBlank { "su nuevo club" }
        val sigue = tipo == "renovación" || tipo == "patrocinio" || club == d.club
        val posts = if (sigue) listOf(
            post(d, "Peña El Bombo", "¡{A} se queda! Esto sí que es una buena noticia para empezar la semana. El escudo, por encima de todo", "aficion", gordo = true, resp = respuestas(u, d, 'V')),
            post(d, medio(d), "OFICIAL: {A} firma con $club. Todos los detalles del acuerdo, en nuestra web", "prensa"),
            post(d, "Cuenta de humor", "{A} firmando el contrato con cara de «ya era hora, ¿no?» 😂", "aficion"))
        else listOf(
            post(d, "Afición de $club", "Bienvenido, {A}. Aquí te vamos a querer mucho… y a exigir más. Te esperamos en la presentación", "aficion", gordo = true, resp = respuestas(u, d, 'V')),
            post(d, "Peña El Bombo", "Se nos va {A}. Gracias por todo, aunque duela. Que te vaya bonito… pero no contra nosotros", "aficion", gordo = true, resp = respuestas(u, d, 'D')),
            post(d, medio(d), "OFICIAL: {A} deja el {C} y ficha por $club. Las cifras, las cláusulas y lo que se dijo en la firma", "prensa"),
            post(d, "{MEDR}", "¿Y {EX} qué opina de la mudanza? Nos dicen que ya ha mirado pisos en la ciudad 👀", "prensa"))
        return mapOf("tendencia" to tendencia(d, if (sigue) "{A}SeQueda" else "{A}A" + etiqueta(club)), "posts" to posts,
            "mensajes" to listOf(mapOf("chat" to "Familia", "de" to alguien(d, "Loli", "madre"), "texto" to if (sigue) "¡Qué alegría, hijo! Ya lo he puesto en el grupo" else "Hijo, ¿y ahora dónde vas a vivir? ¿Hace frío allí?"),
                mapOf("chat" to "Familia", "de" to "Tía Mari", "texto" to "Qué orgullo. Yo ya sabía que llegarías lejos"),
                mapOf("chat" to alguien(d, "Toni Vidal", "representante"), "de" to alguien(d, "Toni Vidal", "representante"), "texto" to "Firmado. Ahora a demostrar que valía cada euro")))
    }

    /** Lo que Shai recuerda, hecho a mano a partir de lo que ha pasado (sin IA). */
    private fun memoria(texto: String, d: Datos): String {
        val lineas = texto.substringAfter("LO ÚLTIMO QUE HA PASADO", "").lineSequence().map { it.trim() }.filter { it.startsWith("- ") }.map { it.removePrefix("- ") }.toList()
        val abiertas = lineas.mapNotNull { Regex("""decisión pendiente: ([^)]+)\)""").find(it)?.groupValues?.get(1) }.take(3)
        val elegidas = lineas.mapNotNull { l -> Regex("""Eligió: «([^»]+)»""").find(l)?.let { l.substringBefore(":").take(60) + " → eligió «" + it.groupValues[1] + "»" } }.take(4)
        val dejadas = lineas.mapNotNull { Regex("""No contestó a «([^»]+)»""").find(it)?.groupValues?.get(1) }.take(3)
        val partidos = lineas.filter { it.startsWith("Partido:") }
        val gente = Regex("""  · ([^,\n]+), ([^,\n]+), afinidad (\d+)""").findAll(texto).map { m ->
            val a = m.groupValues[3].toInt()
            "${m.groupValues[1]} (${m.groupValues[2]}): " + when { a >= 75 -> "muy unidos"; a >= 50 -> "bien"; a >= 30 -> "relación tensa"; else -> "casi rota" }
        }.toList().take(7)
        return buildString {
            appendLine("Tramas abiertas: " + (abiertas.ifEmpty { listOf("ninguna pendiente ahora mismo") }).joinToString("; ") + ".")
            appendLine("Su gente: " + gente.ifEmpty { listOf("aún sin gente") }.joinToString("; ") + ".")
            appendLine("Lo que decidió y lo que dejó pasar: " + (elegidas + dejadas.map { "no contestó a «$it»" }).ifEmpty { listOf("nada todavía") }.joinToString("; ") + ".")
            append("Hechos clave: ${partidos.size} partidos en lo último que ha pasado" + (partidos.firstOrNull()?.let { "; el más reciente: " + it.removePrefix("Partido: ").substringBefore(" · ").take(120) } ?: "") +
                "; nivel de ahora: ${Nivel.entries[d.nivel.coerceIn(0, 4)].nombre.lowercase()}, con ${Alcance.texto(d.seguidores)} seguidores.")
        }.trim()
    }

    private fun pl(n: Int, uno: String, varios: String = uno + "s") = "$n " + if (n == 1) uno else varios

    private fun temporada(u: Uso, d: Datos, texto: String): Map<String, Any?> {
        val m = Regex("""CIERRA LA TEMPORADA ([^:]+): (\d+) partidos \((\d+)V (\d+)E (\d+)D\)""").find(texto)
        val t = m?.groupValues?.get(1)?.trim().orEmpty()
        val pj = m?.groupValues?.get(2)?.toInt() ?: 0; val v = m?.groupValues?.get(3)?.toInt() ?: 0; val e = m?.groupValues?.get(4)?.toInt() ?: 0; val dd = m?.groupValues?.get(5)?.toInt() ?: 0
        val goles = Regex("""\), (\d+) goles""").find(texto)?.groupValues?.get(1)?.toInt() ?: 0
        val asis = Regex("""(\d+) asistencias, \d+ minutos""").find(texto)?.groupValues?.get(1)?.toInt() ?: 0
        val nota = Regex("""nota media ([\d,]+)""").find(texto)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
        val buena = v * 2 > pj || nota >= 7.2 || goles >= 12
        val mala = dd * 2 > pj || (nota in 0.1..6.0)
        val balance = buildString {
            append(when {
                buena -> "La temporada $t de {A} en el {C} se recordará. ${pl(pj, "partido")}, ${pl(v, "victoria")}, ${pl(e, "empate")} y ${pl(dd, "derrota")}" + (if (!d.mister) ", con ${pl(goles, "gol", "goles")} y ${pl(asis, "asistencia")}" + (if (nota > 0) " y un ${Formato.decimal(nota)} de nota media" else "") else "") + ": números de los que hacen que un club te mire distinto y que la grada te cante en el calentamiento."
                mala -> "La temporada $t de {A} en el {C} ha sido de las que se quieren olvidar. ${pl(pj, "partido")}, ${pl(v, "victoria")}, ${pl(e, "empate")} y ${pl(dd, "derrota")}" + (if (!d.mister) ", ${pl(goles, "gol", "goles")} y ${pl(asis, "asistencia")}" + (if (nota > 0) " con un ${Formato.decimal(nota)} de nota media" else "") else "") + ": poco para lo que se esperaba y demasiado poco para lo que él mismo se exige."
                else -> "La temporada $t de {A} en el {C} ha tenido de todo. ${pl(pj, "partido")}, ${pl(v, "victoria")}, ${pl(e, "empate")} y ${pl(dd, "derrota")}" + (if (!d.mister) ", ${pl(goles, "gol", "goles")} y ${pl(asis, "asistencia")}" + (if (nota > 0) " con un ${Formato.decimal(nota)} de nota media" else "") else "") + ": un año de crecer, de equivocarse y de aprender, que deja más preguntas que respuestas."
            })
            append("\n\nFuera del campo, el año no ha sido menos intenso. Su gente ha estado ahí, con sus más y sus menos: {M} en la grada cada domingo, {AMI} en todas las celebraciones y {REP} pegado al teléfono. La prensa rosa no le ha dado tregua y {EX} ha seguido apareciendo cuando menos se la esperaba.")
            append("\n\nAhora llega el verano: vacaciones, mercado y decisiones. En el {C} toca hacer balance y en su casa, maletas. Lo que pase en estas semanas marcará la temporada que viene.")
        }
        val n = d.nivel
        val premios = buildList {
            add(mapOf("premio" to if (n <= 1) "Revelación del año" else "Mejor jugador del año del {C}", "medio" to medio(d), "ganado" to buena, "texto" to if (buena) "Un premio a una temporada que nadie esperaba tan buena." else "Se quedó a las puertas: el jurado valoró más la regularidad de otros."))
            if (!d.mister) add(mapOf("premio" to "Pichichi de la peña", "medio" to "Peña El Bombo", "ganado" to (goles >= 5), "texto" to if (goles >= 5) "$goles goles y una cena pagada por la peña, como manda la tradición." else "Este año se lo lleva otro. La peña le pide más gol el año que viene."))
            add(mapOf("premio" to "Peor peinado de la temporada", "medio" to medioRosa(d), "ganado" to true, "texto" to "Por votación popular y con récord de participación. Él dice que es una tendencia."))
            if (n >= 3) add(mapOf("premio" to "Once ideal del año", "medio" to "Planeta Fútbol", "ganado" to buena, "texto" to if (buena) "Entre los mejores del continente, según los lectores." else "Fuera del once por primera vez en años: los lectores no perdonan."))
        }
        val vac = elige(u, BancoPrueba.VACACIONES) { it.id }!!
        return mapOf("titulo" to rellena("Así fue el año de {A}", d), "balance" to rellena(balance, d), "premios" to premios.map { p -> p.mapValues { (_, x) -> if (x is String) rellena(x, d) else x } },
            "prensa" to prensaDe(medio(d), "El año de {A}", "Balance de la temporada $t: lo que fue dentro del campo y fuera.", balance, eligeVarios(u, BancoPrueba.SECUNDARIOS, 2) { "s:" + it.first }, d),
            "tendencia" to tendencia(d, "Temporada{A}"),
            "posts" to listOf(post(d, "Peña El Bombo", if (buena) "Gracias por esta temporada, {A}. Nos has hecho disfrutar como hacía años. El año que viene, a por más" else "Temporada para olvidar. Vacaciones, cabeza y a volver con otra cara, {A}", "aficion", gordo = true, resp = respuestas(u, d, if (buena) 'V' else 'D')),
                post(d, "Cuenta de estadísticas", "{A} en $t: $pj partidos" + (if (!d.mister) ", $goles goles, $asis asistencias" else "") + ". ¿Notable, aprobado o suspenso? Votad 👇", "aficion", gordo = true, resp = respuestas(u, d, 'X')),
                post(d, "{MEDR}", "Empieza el verano de {A}. Ya sabemos dónde va de vacaciones y con quién. Mañana, todo 👀", "prensa")),
            "mensajes" to listOf(mapOf("chat" to "Familia", "de" to alguien(d, "Loli", "madre"), "texto" to "Hijo, ¿cuándo vienes a casa? Ya te he hecho la cama"),
                mapOf("chat" to "Familia", "de" to "Tía Mari", "texto" to if (buena) "¡Qué temporada, sobrino! En la peluquería no se habla de otra cosa" else "Bueno, el año que viene será mejor, ya verás"),
                mapOf("chat" to alguien(d, "Toni Vidal", "representante"), "de" to alguien(d, "Toni Vidal", "representante"), "texto" to "Este verano va a ser movido. Ten el móvil cerca")),
            "vacaciones" to mapOf("titulo" to rellena(vac.titulo, d), "texto" to rellena(vac.texto, d), "decision" to decision(d, if (d.pendientes < Narrador.MAX_ABIERTAS) vac else null)),
            "ofertas" to ofertas(u, d, 2), "efectos" to efectos(if (buena) EfP(fama = 3, aficion = 3) else if (mala) EfP(aficion = -3) else NADA, d), "gente_nueva" to emptyList<Any>())
    }
}
