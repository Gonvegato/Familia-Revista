package com.gonzalo.azabache.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Sitio
import java.io.File

/*
 * Lo que es solo de este ordenador (como en el móvil, que lo guarda en sus preferencias y no en la cuenta): las recomendaciones de tu
 * pareja que ya has añadido o descartado (`recsdone` en el móvil). En `local.json` de la carpeta de Azabache.
 */
object Local {
    const val ARCHIVO = "local.json"
    @Volatile var dirPruebas: File? = null

    private fun archivo(): File = File(dirPruebas ?: LaFamilia.dirPruebas?.let { File(it, "azabache") } ?: Sitio.de("azabache"), ARCHIVO)

    private var datos: MutableMap<String, Any?>? = null

    private fun mapa(): MutableMap<String, Any?> = datos ?: runCatching { LinkedHashMap(Json.lee(archivo().readText()).comoMapa()) }.getOrNull()
        .orEmpty().toMutableMap().also { datos = it }

    private fun guarda() {
        val f = archivo()
        runCatching { f.parentFile?.mkdirs(); val t = File(f.path + ".tmp"); t.writeText(Json.escribe(datos.orEmpty())); t.renameTo(f) || run { f.writeText(t.readText()); t.delete() } }
    }

    @Synchronized private fun numeros(k: String): List<Long> = mapa()[k].comoLista().map { it.comoLong() }
    @Synchronized private fun ponNumeros(k: String, v: List<Long>) { mapa()[k] = v; guarda() }

    /** Las recomendaciones de tu pareja que ya has añadido a tu lista o descartado. */
    val recsHechas: Set<Long> get() = numeros("recsdone").toSet()
    fun recHecha(id: Long) { ponNumeros("recsdone", (numeros("recsdone") + id).distinct().takeLast(400)) }

    /** (Para las pruebas.) */
    @Synchronized fun olvidaParaPruebas() { datos = null }
}
