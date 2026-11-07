package com.gonzalo.iptv

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * La guía de una lista en el dispositivo (es caché: se vuelve a bajar; no va a la cuenta). Un archivo con los canales de la guía y, por
 * cada canal, sus programas en orden, para leer solo los de un canal cuando hace falta (con un `RandomAccessFile`): ni al hacerla ni al
 * leerla se tiene la guía entera en memoria.
 *
 * Se hace con [Escritor]: los programas llegan en el orden del XMLTV (que no tiene por qué ir canal a canal) y se apuntan en un archivo
 * de paso; en memoria solo queda, de cada uno, su canal, su hora y dónde está. Al [Escritor.cierra], se escriben ordenados por canal y hora
 * (sin repetidos) en el archivo de verdad, y el de paso se borra.
 */
class Guia private constructor(private val archivo: File, val canales: List<CanalGuia>, private val indice: Map<String, LongArray>, val hecha: Long, val fuentes: Int) {
    /** (si el archivo cambia —una guía nueva—, esta ya no vale: hay que abrir la nueva) */
    private val modificado = archivo.lastModified()
    val vigente: Boolean get() = archivo.lastModified() == modificado

    private val cache = object : LinkedHashMap<String, List<Programa>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Programa>>?) = size > 96
    }

    /** ¿Tiene programas de ese canal (el id de la guía)? */
    fun tiene(id: String) = indice.containsKey(id)

    /** Los programas de un canal de la guía, en orden. */
    @Synchronized
    fun programas(id: String): List<Programa> {
        cache[id]?.let { return it }
        val (desde, cuantos) = indice[id]?.let { it[0] to it[1].toInt() } ?: return emptyList()
        if (!vigente) return emptyList()
        val l = ArrayList<Programa>(cuantos)
        try {
            RandomAccessFile(archivo, "r").use { raf ->
                raf.seek(desde)
                val largo = raf.readInt()
                val b = ByteArray(largo); raf.readFully(b)
                val d = DataInputStream(ByteArrayInputStream(b))
                repeat(cuantos) { l += Programa(id, d.readLong(), d.readLong(), d.readUTF(), d.readUTF(), d.readUTF(), d.readUTF(), d.readUTF(), d.readUTF()) }
            }
        } catch (e: Exception) { return emptyList() }
        cache[id] = l
        return l
    }

    /** Los de [id] que se solapan con [desde]–[hasta]. */
    fun entre(id: String, desde: Long, hasta: Long): List<Programa> = programas(id).filter { it.fin > desde && it.inicio < hasta }

    /** Lo que se emite ahora y lo siguiente (o nulos). */
    fun ahoraYDespues(id: String, ahora: Long): Pair<Programa?, Programa?> {
        val l = programas(id)
        val i = l.indexOfFirst { it.fin > ahora }
        if (i < 0) return null to null
        val a = l[i].takeIf { it.inicio <= ahora }
        val s = if (a != null) l.getOrNull(i + 1) else l[i]
        return a to s
    }

    /** Desde qué hora y hasta cuál hay guía (para saber si toca bajarla otra vez). */
    val hastaCuando: Long get() = indice.values.maxOfOrNull { it[2] } ?: 0L

    companion object {
        private const val MAGIA = 0x434F4247 // «COBG»
        private const val VERSION = 1

        /** Abre una guía hecha (o null si no hay o no se entiende). */
        fun abre(archivo: File): Guia? = runCatching {
            if (!archivo.isFile) return null
            DataInputStream(BufferedInputStream(FileInputStream(archivo), 64 * 1024)).use { d ->
                if (d.readInt() != MAGIA || d.readInt() != VERSION) return null
                val hecha = d.readLong(); val fuentes = d.readInt()
                val nc = d.readInt()
                val canales = ArrayList<CanalGuia>(nc)
                repeat(nc) { val id = d.readUTF(); val ic = d.readUTF(); val nn = d.readInt(); canales += CanalGuia(id, List(nn) { d.readUTF() }, ic) }
                val ni = d.readInt()
                val ind = HashMap<String, LongArray>(ni * 2)
                repeat(ni) { ind[d.readUTF()] = longArrayOf(d.readLong(), d.readInt().toLong(), d.readLong()) }
                Guia(archivo, canales, ind, hecha, fuentes)
            }
        }.getOrNull()
    }

    /** Hace una guía en [destino] a medida que llegan sus canales y sus programas. */
    class Escritor(private val destino: File) : AutoCloseable {
        private val paso = File(destino.path + ".paso")
        private val out = DataOutputStream(BufferedOutputStream(FileOutputStream(paso), 64 * 1024))
        private var pos = 0L
        private val ids = HashMap<String, Int>()
        private val nombres = ArrayList<String>()
        private val canales = LinkedHashMap<String, CanalGuia>()
        // Por programa: canal, hora de inicio y dónde está en el de paso (arrays que crecen: nada de objetos por programa)
        private var canalDe = IntArray(4096); private var inicioDe = LongArray(4096); private var dondeDe = LongArray(4096)
        var cuantos = 0; private set
        var fuentes = 0
        private var cerrado = false

        fun canal(c: CanalGuia) {
            // De varias guías: se juntan los nombres del mismo id
            val antes = canales[c.id]
            canales[c.id] = if (antes == null) c else antes.copy(nombres = (antes.nombres + c.nombres).distinct(), icono = antes.icono.ifBlank { c.icono })
        }

        fun programa(p: Programa) {
            val k = ids.getOrPut(p.canal) { nombres += p.canal; nombres.size - 1 }
            if (cuantos == canalDe.size) { val t = cuantos * 2; canalDe = canalDe.copyOf(t); inicioDe = inicioDe.copyOf(t); dondeDe = dondeDe.copyOf(t) }
            canalDe[cuantos] = k; inicioDe[cuantos] = p.inicio; dondeDe[cuantos] = pos
            val b = java.io.ByteArrayOutputStream(256)
            DataOutputStream(b).use { d -> d.writeLong(p.inicio); d.writeLong(p.fin); for (s in listOf(p.titulo, p.subtitulo, p.descripcion, p.categoria, p.icono, p.episodio)) d.writeUTF(corta(s)) }
            val bytes = b.toByteArray()
            out.writeInt(bytes.size); out.write(bytes)
            pos += 4 + bytes.size
            cuantos++
        }

        /** writeUTF no admite más de 65 535 bytes: lo largo se recorta (nunca pasa en una guía normal). */
        private fun corta(s: String): String = if (s.length <= 8000) s else s.take(8000)

        /** Escribe la guía de verdad y borra la de paso. */
        fun cierra(hecha: Long = System.currentTimeMillis()) {
            if (cerrado) return
            cerrado = true
            out.close()
            // El orden: por canal y hora (sin repetir dos que empiezan a la misma hora en el mismo canal: de dos guías, el primero)
            val orden = (0 until cuantos).sortedWith(compareBy<Int>({ canalDe[it] }, { inicioDe[it] }, { it }))
            val tmp = File(destino.path + ".nueva")
            RandomAccessFile(paso, "r").use { raf ->
                // Primero el cuerpo (cada canal, un bloque) en un archivo aparte, para saber dónde queda cada uno
                val cuerpo = File(destino.path + ".cuerpo")
                val indice = LinkedHashMap<String, LongArray>()
                DataOutputStream(BufferedOutputStream(FileOutputStream(cuerpo), 64 * 1024)).use { o ->
                    var p = 0L; var i = 0
                    while (i < orden.size) {
                        val k = canalDe[orden[i]]
                        var j = i
                        val bloque = java.io.ByteArrayOutputStream()
                        var n = 0; var ultimo = Long.MIN_VALUE; var finMax = 0L
                        while (j < orden.size && canalDe[orden[j]] == k) {
                            val x = orden[j]
                            if (inicioDe[x] != ultimo) {
                                raf.seek(dondeDe[x]); val l = raf.readInt(); val b = ByteArray(l); raf.readFully(b)
                                bloque.write(b); n++; ultimo = inicioDe[x]
                                finMax = maxOf(finMax, DataInputStream(ByteArrayInputStream(b, 8, 8)).readLong())
                            }
                            j++
                        }
                        val bytes = bloque.toByteArray()
                        o.writeInt(bytes.size); o.write(bytes)
                        indice[nombres[k]] = longArrayOf(p, n.toLong(), finMax)
                        p += 4 + bytes.size
                        i = j
                    }
                }
                // Luego la cabecera (canales e índice) y, detrás, el cuerpo
                val cab = java.io.ByteArrayOutputStream()
                DataOutputStream(cab).use { d ->
                    d.writeInt(MAGIA); d.writeInt(VERSION); d.writeLong(hecha); d.writeInt(fuentes)
                    d.writeInt(canales.size)
                    canales.values.forEach { c -> d.writeUTF(corta(c.id)); d.writeUTF(corta(c.icono)); d.writeInt(c.nombres.size); c.nombres.forEach { d.writeUTF(corta(it)) } }
                    d.writeInt(indice.size)
                }
                // (el índice ocupa lo mismo sea cual sea el sitio de cada bloque: se mide una vez y se escribe con el de verdad)
                fun indiceCon(base: Long) = java.io.ByteArrayOutputStream().also { b ->
                    DataOutputStream(b).use { d -> indice.forEach { (id, v) -> d.writeUTF(corta(id)); d.writeLong(base + v[0]); d.writeInt(v[1].toInt()); d.writeLong(v[2]) } }
                }.toByteArray()
                val base = cab.size().toLong() + indiceCon(0L).size
                DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 64 * 1024)).use { d ->
                    d.write(cab.toByteArray())
                    d.write(indiceCon(base))
                    FileInputStream(cuerpo).use { it.copyTo(d, 64 * 1024) }
                }
                cuerpo.delete()
            }
            paso.delete()
            if (!tmp.renameTo(destino)) { destino.delete(); tmp.renameTo(destino) }
        }

        /** Sin terminar (un error a medias): se borra lo de paso y no se toca la guía que había. */
        fun descarta() { runCatching { out.close() }; cerrado = true; paso.delete() }

        override fun close() { if (!cerrado) descarta() }
    }
}
