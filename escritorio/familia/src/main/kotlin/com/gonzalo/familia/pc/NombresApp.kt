package com.gonzalo.familia.pc

/**
 * Cómo se llama cada app de la familia. Por dentro (la carpeta de datos, la cuenta, los paquetes) siguen con su nombre de siempre
 * («hiedra», «marfil»…); el que se ve, el de la carpeta donde se instala y el de los archivos de cada versión es el nuevo. Las
 * versiones de antes del cambio se instalaron (y se subían) con el nombre viejo: se reconocen las dos, para pasar sin romper nada.
 */
object NombresApp {
    private val nuevos = mapOf(
        "hiedra" to "Orion", "marfil" to "Lyra", "cobalto" to "Nova", "azabache" to "Polaris", "mostaza" to "Sirius", "cobre" to "Pulsar",
    )
    private val anteriores = mapOf(
        "hiedra" to listOf("Bastet", "Hiedra"),
        "marfil" to listOf("Seshat", "Marfil"),
        "cobalto" to listOf("Thot", "Cobalto"),
        "azabache" to listOf("Jonsu", "Azabache"),
        "mostaza" to listOf("Hathor", "Mostaza"),
        "cobre" to listOf("Nut", "Cobre"),
    )

    /** El nombre que se ve (y el de su carpeta y sus archivos): «Orion» para hiedra. */
    fun de(app: String): String = nuevos[app] ?: app.replaceFirstChar { it.uppercase() }

    /** El de antes del cambio («Bastet» para hiedra), con el que siguen instaladas y subidas las versiones viejas. */
    fun viejo(app: String): String = anteriores[app]?.first() ?: app.replaceFirstChar { it.uppercase() }

    /** Todos los nombres (el nuevo primero y los anteriores después, para reconocer versiones y carpetas viejas). */
    fun ambos(app: String): List<String> = (listOf(de(app)) + (anteriores[app] ?: listOf(app.replaceFirstChar { it.uppercase() }))).distinct()
}
