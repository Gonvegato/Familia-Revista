package com.gonzalo.marfil.pc

import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.nucleo.comoDouble
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.LaFamilia
import java.security.MessageDigest

/**
 * Un subrayado (con su nota, si la tiene) o un marcador, tal como lo guarda el lector del móvil: el objeto del lector (`n`) en
 * `marcas/<kid>~<id>`. Subrayado: `{value: el CFI del tramo, color: "subrayado", text, note, created, index: el capítulo, label, frac, mod}`;
 * marcador: `{value: el CFI de la página, label, fraction, index, created, mod}`. `m: "r"` es del texto adaptado de un PDF (aquí no se pinta).
 */
class Marca(val ref: String, val tipo: String, val n: Map<String, Any?>, val mod: Long) {
    val valor: String get() = n["value"].comoTexto()
    val texto: String get() = n["text"].comoTexto()
    val nota: String get() = n["note"].comoTexto()
    val creada: Long get() = n["created"].comoLong()
    val etiqueta: String get() = n["label"].comoTexto()
    /** El capítulo (la sección del libro, desde 0), si lo dice. */
    val indice: Int? get() = (n["index"] as? Number)?.toInt()
    /** Por dónde va en el libro (0–1), si lo dice. */
    val fraccion: Double? get() = (n["frac"] ?: n["fraction"])?.takeIf { it is Number }?.comoDouble()
    val esSubrayado: Boolean get() = tipo == Marcas.SUBRAYADOS
    val delTextoDeUnPdf: Boolean get() = n["m"] == "r"
}

/**
 * Los subrayados, las notas y los marcadores de la cuenta (`marcas/<kid>~<id>`), con las mismas reglas que `LecturaNube` y `MezclaLectura`
 * del móvil: cada uno es un nodo (dos dispositivos nunca se pisan), se reconoce por su sitio y su hora de creación, la edición más reciente
 * gana (la hora va en `m/n`) y quitar es poner `b` con la hora (si alguien lo vuelve a poner después, vuelve).
 */
object Marcas {
    const val SUBRAYADOS = "notes"
    const val MARCADORES = "marks"

    fun col(): Coleccion = LaFamilia.coleccion("marcas")

    /** El nombre del nodo: el libro y lo que identifica la marca (su sitio y su hora de creación), como `LecturaNube.idMarca`. */
    fun idDe(kid: String, valor: String, creada: Long): String =
        kid + "~" + MessageDigest.getInstance("SHA-1").digest("$valor|$creada".toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    /** Las de un libro que siguen puestas. */
    fun de(kid: String, tipo: String? = null): List<Marca> = col().vivos().filter { it.texto("ref") == kid && (tipo == null || it.texto("tipo") == tipo) }
        .map { Marca(it.ref, it.texto("tipo"), it.mapa("n"), it.m["n"] ?: it.mapa("n")["mod"].comoLong()) }
        .filter { it.valor.isNotBlank() && (it.tipo != SUBRAYADOS || it.texto.isNotBlank()) }

    private fun pon(kid: String, tipo: String, n: Map<String, Any?>, hora: Long): Marca {
        val ref = idDe(kid, n["value"].comoTexto(), n["created"].comoLong())
        col().pon(ref, linkedMapOf("ref" to kid, "tipo" to tipo, "n" to n, "b" to 0L), hora)
        return Marca(ref, tipo, n, hora)
    }

    /** Subraya un tramo ([valor]: su CFI) del capítulo [indice]. */
    fun subraya(kid: String, valor: String, texto: String, indice: Int, etiqueta: String, fraccion: Double, nota: String = "", ahora: Long = System.currentTimeMillis()): Marca {
        val n = linkedMapOf<String, Any?>("value" to valor, "color" to "subrayado", "text" to texto, "note" to nota, "created" to ahora,
            "index" to indice.toLong(), "label" to etiqueta, "frac" to Math.round(fraccion * 10000.0) / 10000.0, "mod" to ahora)
        return pon(kid, SUBRAYADOS, n, ahora)
    }

    /** Escribe (o borra, con «») la nota de un subrayado. */
    fun ponNota(m: Marca, nota: String, ahora: Long = System.currentTimeMillis()): Marca =
        pon(m.ref.substringBefore('~'), m.tipo, LinkedHashMap(m.n).also { it["note"] = nota.trim(); it["mod"] = ahora }, ahora)

    /** Marca la página ([valor]: el CFI de su principio). */
    fun marca(kid: String, valor: String, etiqueta: String, fraccion: Double, indice: Int, ahora: Long = System.currentTimeMillis()): Marca {
        val n = linkedMapOf<String, Any?>("value" to valor, "label" to etiqueta, "fraction" to Math.round(fraccion * 10000.0) / 10000.0, "index" to indice.toLong(), "created" to ahora, "mod" to ahora)
        return pon(kid, MARCADORES, n, ahora)
    }

    /** La quita (en todos tus dispositivos). */
    fun quita(m: Marca, ahora: Long = System.currentTimeMillis()) = col().pon(m.ref, mapOf("b" to ahora), ahora)

    /** «Deshacer»: la vuelve a poner tal cual (con su nota). */
    fun recupera(m: Marca, ahora: Long = System.currentTimeMillis()): Marca = pon(m.ref.substringBefore('~'), m.tipo, LinkedHashMap(m.n).also { it["mod"] = ahora }, ahora)
}
