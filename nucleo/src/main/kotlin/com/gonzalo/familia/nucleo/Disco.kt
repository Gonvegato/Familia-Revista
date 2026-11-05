package com.gonzalo.familia.nucleo

import java.io.File

/**
 * Dónde guarda el núcleo lo suyo en el dispositivo: la copia local de la cuenta, lo pendiente de subir y las imágenes.
 * Los nombres son relativos («libros.json», «img/ab12…»). Escribir es atómico: o queda el archivo entero nuevo o el viejo.
 */
interface Disco {
    fun lee(nombre: String): ByteArray?
    fun escribe(nombre: String, datos: ByteArray)
    fun borra(nombre: String)
    fun existe(nombre: String): Boolean = lee(nombre) != null
    fun leeTexto(nombre: String): String? = lee(nombre)?.toString(Charsets.UTF_8)
    fun escribeTexto(nombre: String, texto: String) = escribe(nombre, texto.toByteArray(Charsets.UTF_8))
}

/** Un directorio de verdad (en Android, el de la app; en el PC, el de la familia). */
class DiscoArchivos(private val dir: File) : Disco {
    init { dir.mkdirs() }
    private fun f(n: String) = File(dir, n)
    override fun lee(nombre: String): ByteArray? = f(nombre).takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }
    override fun existe(nombre: String) = f(nombre).isFile
    override fun escribe(nombre: String, datos: ByteArray) {
        val destino = f(nombre)
        destino.parentFile?.mkdirs()
        val tmp = File(destino.parentFile, destino.name + ".tmp")
        tmp.outputStream().use { o -> o.write(datos); o.fd.sync() }
        if (!tmp.renameTo(destino)) { destino.delete(); if (!tmp.renameTo(destino)) throw java.io.IOException("No se pudo guardar $nombre") }
    }
    override fun borra(nombre: String) { f(nombre).delete() }
}

/** En memoria (para las pruebas). */
class DiscoMemoria : Disco {
    val archivos = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    override fun lee(nombre: String) = archivos[nombre]?.copyOf()
    override fun escribe(nombre: String, datos: ByteArray) { archivos[nombre] = datos.copyOf() }
    override fun borra(nombre: String) { archivos.remove(nombre) }
}

/**
 * La hora para las marcas de cada campo ([m]): la del dispositivo, corregida con la del servidor en cuanto se conoce
 * (así un móvil con la hora mal no gana ni pierde cambios que no le tocan).
 */
interface Reloj {
    fun ahora(): Long
}

class RelojSistema : Reloj {
    @Volatile var desfase = 0L
    override fun ahora() = System.currentTimeMillis() + desfase
    /** Lo que dice el servidor de su hora en este momento (por ejemplo, la marca `u` que acaba de poner). */
    fun ajusta(horaServidor: Long, horaLocalCuandoLlego: Long = System.currentTimeMillis()) {
        if (horaServidor > 0) desfase = horaServidor - horaLocalCuandoLlego
    }
}
