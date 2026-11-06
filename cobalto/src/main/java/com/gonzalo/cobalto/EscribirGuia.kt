package com.gonzalo.cobalto

import org.json.JSONObject

/** Un paso de la guía: qué es, cómo, el botón de acción y si se marca solo (auto) al detectar lo hecho. */
class Paso(val id: Int, val titulo: String, val auto: Boolean, val accion: String, val que: String, val como: List<String>, val hecho: String)

/** Lo que la guía necesita saber de una obra (se junta igual en el PC y aquí). */
class DatosGuia(val obra: JSONObject, val personajes: Int, val resumenes: Int, val dias: Int, val palabras: Int, val ahora: Long)

/** El estado de un paso: [estado] es «hecho», «saltado», «curso» (el reposo empezado) o «pendiente». */
class EstadoPaso(val id: Int, val estado: String, val aMano: Boolean, val dias: Int = 0)

/**
 * La guía para escribir una novela paso a paso: los pasos, sus textos y cómo se sabe lo que ya llevas hecho. Es la misma
 * que escritorio/src/renderer/revista/js/escribir-guia.js de Atrio (los casos de herramientas/casos/guia.json los prueban los dos) y,
 * sin nada de Android, el MISMO archivo que compila Cobalto para Linux y Mac (las pantallas de cada uno, aparte: EscribirGuiaUi.kt y
 * escritorio/cobalto/…/pc/Guia.kt). En la obra se guarda gOn (1 = activada) y g1…g11 (1 = hecho a mano, 2 = saltado) y, en el reposo,
 * g7t (cuándo empezó): viaja con la obra, en todos tus dispositivos.
 */
object GuiaNovela {
    const val DIA = 86_400_000L
    const val FUENTES = "Basada en guías de Grammarly, MasterClass y Escritor Flexible, el método Snowflake de Randy Ingermanson y las clases de Brandon Sanderson. Las cantidades y plazos son orientativos."
    val PASOS = listOf(
        Paso(1, "Una idea pequeña", true, "Escribir mi frase",
            "No hace falta tener la historia entera. Basta una frase: quién quiere qué y qué se lo impide.",
            listOf("Escríbela en una sola frase, corta y sin nombres propios.",
                "Si solo tienes una imagen o una escena, anótala igual: de ahí sale la frase.",
                "No busques la idea perfecta: se puede cambiar más adelante."),
            "Tienes tu frase."),
        Paso(2, "Tus personajes", true, "Crear un personaje",
            "Para cada protagonista: qué quiere, qué se lo impide y qué pasa si fracasa.",
            listOf("Empieza solo por quien lleva la historia.",
                "Rellena «Quiere», «Se lo impide» y «Si fracasa» en su ficha.",
                "Los demás personajes pueden esperar a que los necesites."),
            "Ya tienes al menos un personaje."),
        Paso(3, "Un esquema ligero", true, "Crear los tres actos",
            "Una lista corta de lo que pasa, no un guion detallado. Lo habitual son tres actos: planteamiento, nudo y desenlace.",
            listOf("Cobalto crea una nota «Esquema de la historia», fuera del manuscrito, con tres apartados: Planteamiento, Nudo y Desenlace.",
                "Escribe bajo cada uno una o dos líneas: qué pasa.",
                "Si planificar te agobia, haz un esquema mínimo y sigue: se puede ampliar mientras escribes."),
            "Tienes el esquema (al menos tres resúmenes)."),
        Paso(4, "Promesa, progreso y recompensa", false, "Anotar mis respuestas",
            "Una idea de las clases de Brandon Sanderson: el principio promete (tono, género, conflicto), el medio avanza y el final cumple lo prometido.",
            listOf("¿Qué le prometes al lector en las primeras páginas?",
                "¿Qué cambia en cada tramo para que el medio avance y no dé vueltas?",
                "¿Cómo cumple el final esas promesas?",
                "No es una plantilla rígida: es una brújula."),
            "Ya lo tienes pensado."),
        Paso(5, "Un hábito realista", true, "Poner mi meta diaria",
            "Una meta pequeña que cumplas todos los días pesa más que una grande que abandones.",
            listOf("Elige una cantidad que te parezca casi demasiado fácil (por ejemplo 300 palabras; es una sugerencia de Cobalto, no de las guías).",
                "Si puedes, escribe a la misma hora.",
                "Un sprint de 10 o 25 minutos ayuda a empezar."),
            "Ya has escrito en tres días distintos."),
        Paso(6, "El primer borrador", true, "Seguir escribiendo",
            "Escribir hasta el final sin corregir. Será imperfecto, y está bien: se arregla después.",
            listOf("No releas ni corrijas mientras escribes: apunta una nota y sigue.",
                "Si te atascas, salta a una escena que sí sepas escribir.",
                "El objetivo es llegar al final, no que quede bonito."),
            "Has llegado a la meta de palabras."),
        Paso(7, "Dejarlo reposar", false, "Empezar el reposo",
            "Aparta el borrador unas semanas antes de releerlo, para leerlo casi como un lector (consejo de Cobalto, sin una fuente concreta).",
            listOf("Pulsa «Empezar el reposo»: Cobalto cuenta los días.",
                "Mientras tanto no lo abras: lee, descansa o piensa en otra historia.",
                "Cuando quieras volver, márcalo como hecho."),
            "Ya has descansado del borrador."),
        Paso(8, "Revisar lo grande", false, "Ver el manuscrito",
            "Primera pasada: lo que afecta a la historia. La gramática se deja para la siguiente.",
            listOf("Agujeros de trama: ¿algo no tiene sentido o se olvida por el camino?",
                "Personajes: ¿cambian y quieren cosas claras?",
                "Ritmo: ¿hay tramos que se arrastran o que van demasiado deprisa?",
                "Marca cada escena como «Revisión» (y luego «Final») según la repasas."),
            "Has revisado la historia."),
        Paso(9, "Revisar lo pequeño", false, "Ver el manuscrito",
            "Segunda pasada: frases, gramática y repeticiones.",
            listOf("Lee en voz alta: lo que suena mal, suele fallar.",
                "Cambia las palabras repetidas; el editor te da sinónimos en el menú del botón derecho (PC) o en «⋯» (móvil).",
                "Mira la ortografía."),
            "Has revisado el texto."),
        Paso(10, "Lectores de prueba", false, "Exportar el manuscrito",
            "Que alguien lea tu novela y te diga qué funciona: lectores beta o un editor profesional.",
            listOf("Exporta el manuscrito en Word o PDF.",
                "Pide a dos o tres personas que lo lean y te digan dónde se aburrieron o se perdieron.",
                "Pregunta y escucha: no te defiendas."),
            "Ya te han leído."),
        Paso(11, "Corrección final y publicar", false, "Exportar el manuscrito",
            "Última lectura para las erratas y decidir si buscas editorial o publicas tú.",
            listOf("Corrige las erratas con calma.",
                "El Word «manuscrito» sigue el formato que suelen pedir las editoriales, pero cada una tiene sus normas: léelas antes de enviar.",
                "Decide: editorial o autopublicación."),
            "Tu novela está lista.")
    )

    private fun num(o: JSONObject, k: String): Double = o.opt(k)?.toString()?.toDoubleOrNull() ?: 0.0

    /** El esquema vive en una nota de la obra (no en el manuscrito): tres apartados con su etiqueta. */
    const val TITULO_ESQUEMA = "Esquema de la historia"
    const val PLANTILLA_ESQUEMA = "Planteamiento:\n\n\nNudo:\n\n\nDesenlace:\n"
    const val TITULO_PROMESAS = "Promesas de la historia"
    const val PLANTILLA_PROMESAS = "Promesa: ¿qué le prometo al lector en las primeras páginas (tono, género, conflicto)?\n\nProgreso: ¿qué cambia en cada tramo para que el medio avance?\n\nRecompensa: ¿cómo cumple el final lo que prometí?\n"
    private val ETIQUETA_ESQUEMA = Regex("^\\s*(Planteamiento|Nudo|Desenlace)\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE)

    /** Cuántos de los tres apartados tienen algo escrito debajo. */
    fun seccionesEsquema(texto: String): Int {
        val cuerpo = LinkedHashMap<String, String>()
        var actual: String? = null
        for (l in texto.split("\n")) {
            val m = ETIQUETA_ESQUEMA.matchEntire(l)
            if (m != null) { actual = m.groupValues[1].lowercase(); cuerpo[actual] = (cuerpo[actual] ?: "") + m.groupValues[2] }
            else if (actual != null) cuerpo[actual] = cuerpo[actual] + l
        }
        return cuerpo.values.count { it.isNotBlank() }
    }

    fun automatico(id: Int, d: DatosGuia): Boolean = when (id) {
        1 -> d.obra.optString("idea").trim().isNotEmpty()
        2 -> d.personajes >= 1
        3 -> d.resumenes >= 3
        5 -> d.dias >= 3
        6 -> num(d.obra, "meta") > 0 && d.palabras >= num(d.obra, "meta")
        else -> false
    }

    fun estados(d: DatosGuia): List<EstadoPaso> = PASOS.map { p ->
        val g = num(d.obra, "g${p.id}").toInt()
        when {
            g == 1 -> EstadoPaso(p.id, "hecho", true)
            g == 2 -> EstadoPaso(p.id, "saltado", true)
            automatico(p.id, d) -> EstadoPaso(p.id, "hecho", false)
            p.id == 7 && num(d.obra, "g7t") > 0 -> EstadoPaso(p.id, "curso", false, maxOf(0L, ((d.ahora - num(d.obra, "g7t").toLong()) / DIA)).toInt())
            else -> EstadoPaso(p.id, "pendiente", false)
        }
    }

    /** El primer paso que falta (el que toca ahora), o null si ya está todo. */
    fun siguiente(est: List<EstadoPaso>): Int? = est.firstOrNull { it.estado == "pendiente" || it.estado == "curso" }?.id
    fun hechos(est: List<EstadoPaso>): Int = est.count { it.estado == "hecho" }
    fun activa(obra: Doc): Boolean = obra.i("gOn") == 1
}

/** Lo que la guía mira de una obra (igual en el móvil y en el ordenador). */
fun datosGuia(e: Escritos, pid: String): DatosGuia {
    val o = e.get(pid)?.o ?: JSONObject()
    val res = e.capitulos(pid).count { it.s("resumen").isNotBlank() } + e.escenasDe(pid).count { it.s("resumen").isNotBlank() } + e.hitos(pid).size +
        e.notas(pid).filter { it.s("titulo") == GuiaNovela.TITULO_ESQUEMA }.sumOf { GuiaNovela.seccionesEsquema(it.s("texto")) }
    return DatosGuia(o, e.fichas(pid).count { it.s("tipo") == "personaje" }, res, e.diasEscritos(pid), e.palabrasObra(pid), System.currentTimeMillis())
}
