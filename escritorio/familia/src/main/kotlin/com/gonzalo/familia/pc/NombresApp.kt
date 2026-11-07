package com.gonzalo.familia.pc

/**
 * Cómo se llama cada app de la familia. Por dentro (la carpeta de datos, la cuenta, los paquetes) siguen con su nombre de siempre
 * («hiedra», «marfil»…); el que se ve, el de la carpeta donde se instala y el de los archivos de cada versión es el nuevo. Las
 * versiones de antes del cambio se instalaron (y se subían) con el nombre viejo: se reconocen las dos, para pasar sin romper nada.
 */
object NombresApp {
    private val nuevos = mapOf(
        "hiedra" to "Bastet", "marfil" to "Seshat", "cobalto" to "Thot", "azabache" to "Jonsu", "mostaza" to "Hathor", "cobre" to "Nut",
    )

    /** El nombre que se ve (y el de su carpeta y sus archivos): «Bastet» para hiedra. */
    fun de(app: String): String = nuevos[app] ?: app.replaceFirstChar { it.uppercase() }

    /** El de antes del cambio («Hiedra» para hiedra), con el que siguen instaladas y subidas las versiones viejas. */
    fun viejo(app: String): String = app.replaceFirstChar { it.uppercase() }

    /** Los dos, el nuevo primero (uno solo si no ha cambiado). */
    fun ambos(app: String): List<String> = listOf(de(app), viejo(app)).distinct()
}
