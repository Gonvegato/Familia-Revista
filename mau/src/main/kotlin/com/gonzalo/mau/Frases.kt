package com.gonzalo.mau

/** Lo que dice Mau. Corto, cálido y sin agobiar: se dice poco y se elige al azar para que no se repita. */
object Frases {
    val buenosDias = listOf("¡Buenos días! Aquí estoy.", "Buenos días. ¿Qué hacemos hoy?", "¡Hola! He dormido como un gato.")
    val buenasTardes = listOf("¡Buenas tardes!", "Hola de nuevo. Te estaba esperando.", "¡Ya era hora! Te echaba de menos.")
    val buenasNoches = listOf("Buenas noches. Yo ya tengo sueño…", "¡Hola! Qué tarde es, ¿no?", "Buenas noches. Un ratito y a dormir.")
    val volver = listOf("¡Ya estoy!", "¡Aquí otra vez!", "Ya salgo, ya salgo.")
    val llegar = listOf("¡Ya estoy aquí!", "Me vengo contigo.", "¡Llegué!")
    val despertar = listOf("¡Ay! Me había quedado dormido.", "Mmm… ¿ya? Vale, vale.", "Solo descansaba los ojos.")
    val callar = "Vale, me callo una hora. Solo te miro."
    val hablarOtraVez = "¡Ya puedo hablar otra vez!"
    val esconderse = "Me escondo. Sigo aquí, en el borde."

    fun saludo(hora: Int): List<String> = when (hora) {
        in 6..13 -> buenosDias
        in 14..20 -> buenasTardes
        else -> buenasNoches
    }

    // ---- Leer (Seshat) ----
    fun quedanPaginas(n: Int) = if (n == 1) "Te queda 1 página para acabar el capítulo. ¿Seguimos?" else "Te quedan $n páginas para acabar el capítulo. ¿Seguimos?"
    val capituloAcabado = listOf("¡Capítulo terminado! ¿Otro?", "Fin del capítulo. ¿Uno más?", "¡Otro capítulo menos!")
    fun libroAcabado(titulo: String?) = if (titulo.isNullOrBlank()) "¡Libro terminado! ¿Cuál es el siguiente?" else "¡Has terminado «$titulo»! ¿Cuál es el siguiente?"
    fun llevasLeyendo(min: Int) = when {
        min >= 120 -> "Llevas $min minutos leyendo. ¿Un vaso de agua?"
        min >= 60 -> "¡Una hora leyendo! Estira un poco las piernas."
        else -> "Media hora leyendo. Qué a gusto se está."
    }
    val tardeLeyendo = listOf("Es tarde. ¿Un capítulo más y a dormir?", "Yo ya bostezo… ¿lo dejamos en este capítulo?")
    val objetivoLectura = "¡Objetivo de lectura de hoy cumplido!"

    // ---- Escribir (Thot) ----
    val sinEscribirHoy = listOf("Hoy aún no has escrito. ¿Diez minutos conmigo?", "¿Escribimos un poco? Aunque sea una frase.", "La página en blanco no muerde. Yo sí, un poco.")
    val atascada = listOf("¿Atascada? Escribe una frase cualquiera; luego la arreglas.", "Si no sale, describe lo que ve tu personaje.", "¿Y si pasa algo inesperado justo ahora?", "Escribe mal, pero escribe. Lo bueno sale después.")
    fun mitadMeta(hechas: Int, meta: Int) = "$hechas de $meta. ¡Vas por la mitad!"
    fun casiMeta(faltan: Int) = "¡Te faltan $faltan palabras para la meta!"
    fun metaCumplida(meta: Int) = "¡Meta de hoy cumplida: $meta palabras!"
    fun sprintAcabado(palabras: Int?) = when {
        palabras == null -> "¡Sprint terminado! Buen trabajo."
        palabras <= 0 -> "Sprint terminado. A veces se piensa más que se escribe, y también vale."
        else -> "¡Sprint terminado: $palabras palabras!"
    }

    // ---- Jugar (Bastet) ----
    fun hasJugado(min: Int, juego: String?) = (if (juego.isNullOrBlank()) "" else "$juego: ") +
        (if (min >= 60) "${min / 60} h ${min % 60} min jugando. ¡Qué partida!" else "$min minutos jugando. ¿Ganaste?")
    val jugar = listOf("¿Jugamos a algo?", "Yo ya tengo mi ovillo. ¿Y tú?")

    // ---- Ver (Hathor, Nut) ----
    val pelisVista = listOf("¿Qué te ha parecido?", "Fin. Yo me he enterado de todo… creo.")
    val verAlgo = listOf("¿Vemos algo?", "Traigo palomitas.")

    // ---- Jonsu ----
    val diario = listOf("¿Miramos qué tal va la semana?", "Aquí está todo lo que has hecho. ¡Mira cuánto!")
}
