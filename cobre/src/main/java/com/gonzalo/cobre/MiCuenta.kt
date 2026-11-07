package com.gonzalo.cobre

import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.DatosCuenta
import com.gonzalo.iptv.Progreso
import com.gonzalo.iptv.Visto

/** Un favorito tuyo (un nodo `cobre_favoritos/<lista>~<canal>`). */
data class Favorito(val ref: String, val lista: String, val canal: String, val nombre: String, val orden: Long)

/**
 * Lo tuyo de Cobre en la cuenta (`nube/ESQUEMA.md`), campo a campo y en todos tus dispositivos: tus favoritos y su orden, los grupos que
 * escondes, el último canal que viste y lo visto de cada película y episodio. Nada de esto es secreto (ni direcciones, ni usuarios, ni
 * contraseñas: solo ids y nombres): lo de las listas, en [Listas]. Las listas enormes y la guía no van a la cuenta: son caché ([Tele]).
 * Sin cuenta no se escribe nada (como en Mostaza).
 */
object MiCuenta {
    /** En las pruebas de pantallas: lo de la cuenta, fijo. */
    @Volatile var fijo: Fijo? = null
    class Fijo(val favoritos: List<Favorito> = emptyList(), val progresos: List<Progreso> = emptyList(), val ocultos: Map<String, Set<String>> = emptyMap(), val ultimo: Favorito? = null)

    private fun col(n: String): Coleccion? = runCatching { LaFamilia.coleccion(n) }.getOrNull()
    private val dentro: Boolean get() = runCatching { LaFamilia.familia.sesion.dentro }.getOrDefault(false)

    // ---------------------------------------------------------------- favoritos

    fun favoritos(): List<Favorito> {
        LaFamilia.cambios
        fijo?.let { return it.favoritos }
        return col(DatosCuenta.FAVORITOS)?.vivos().orEmpty().map { n -> Favorito(n.ref, n.texto("lista"), n.texto("canal"), n.texto("n"), n.entero("o")) }
            .filter { it.lista.isNotBlank() && it.canal.isNotBlank() }.sortedWith(compareBy({ it.orden }, { it.nombre }))
    }

    fun esFavorito(lista: String, clave: String) = favoritos().any { it.lista == lista && it.canal == clave }

    /** Lo pone o lo quita (quitar es `b`: si otro dispositivo lo vuelve a poner después, vuelve). */
    fun alternaFavorito(lista: String, c: Canal) {
        if (!dentro) return
        val col = col(DatosCuenta.FAVORITOS) ?: return
        val ref = DatosCuenta.refFavorito(lista, c.clave)
        if (esFavorito(lista, c.clave)) col.borra(ref)
        else {
            val ultimo = favoritos().maxOfOrNull { it.orden } ?: 0L
            col.pon(ref, DatosCuenta.camposFavorito(lista, c, ultimo + 1) + mapOf("b" to 0L))
        }
    }

    /** Cambia el sitio de un favorito (arriba o abajo): se renumeran todos (solo cambia el campo `o` de los que se mueven). */
    fun mueveFavorito(ref: String, d: Int) {
        if (!dentro) return
        val col = col(DatosCuenta.FAVORITOS) ?: return
        val l = favoritos().toMutableList()
        val i = l.indexOfFirst { it.ref == ref }; val j = i + d
        if (i < 0 || j !in l.indices) return
        l.add(j, l.removeAt(i))
        l.forEachIndexed { k, f -> if (f.orden != (k + 1).toLong()) col.cambia(f.ref, mapOf("o" to (k + 1).toLong())) }
    }

    // ---------------------------------------------------------------- grupos escondidos

    fun ocultos(lista: String): Set<String> {
        LaFamilia.cambios
        fijo?.let { return it.ocultos[lista].orEmpty() }
        return col(DatosCuenta.GRUPOS)?.vivos().orEmpty().filter { it.texto("lista") == lista && it.si("oculto") }.map { it.texto("g") }.toSet()
    }

    fun ocultaGrupo(lista: String, grupo: String, oculto: Boolean) {
        if (!dentro) return
        col(DatosCuenta.GRUPOS)?.pon(DatosCuenta.refGrupo(lista, grupo), DatosCuenta.camposGrupo(lista, grupo, oculto))
    }

    // ---------------------------------------------------------------- el último canal

    fun ultimo(): Favorito? {
        LaFamilia.cambios
        fijo?.let { return it.ultimo }
        val n = col(DatosCuenta.ESTADO)?.nodo(DatosCuenta.ULTIMO) ?: return null
        if (n.texto("lista").isBlank()) return null
        return Favorito(n.ref, n.texto("lista"), n.texto("canal"), n.texto("n"), n.entero("ul"))
    }

    fun ponUltimo(lista: String, c: Canal) {
        if (!dentro || fijo != null) return
        val u = ultimo()
        if (u != null && u.lista == lista && u.canal == c.clave && System.currentTimeMillis() - u.orden < 60_000L) return
        col(DatosCuenta.ESTADO)?.pon(DatosCuenta.ULTIMO, DatosCuenta.camposUltimo(lista, c, System.currentTimeMillis()))
    }

    // ---------------------------------------------------------------- lo visto del videoclub

    fun progresos(): List<Progreso> {
        LaFamilia.cambios
        fijo?.let { return it.progresos }
        return col(DatosCuenta.PROGRESO)?.vivos().orEmpty().mapNotNull { n -> DatosCuenta.progresoDe(n.campos) }
    }

    fun progreso(lista: String, video: String): Progreso? = progresos().firstOrNull { it.lista == lista && it.video == video }

    fun ponProgreso(p: Progreso) {
        if (!dentro || fijo != null) return
        col(DatosCuenta.PROGRESO)?.pon(DatosCuenta.refProgreso(p.lista, p.video), DatosCuenta.camposProgreso(p))
    }

    /** «Seguir viendo», de las listas que tienes. */
    fun seguir(): List<Progreso> = Visto.seguir(progresos(), Listas.lista().map { it.id }.toSet())

    /** Marca visto (o no) a mano. */
    fun marcaVisto(p: Progreso, visto: Boolean) = ponProgreso(p.copy(visto = visto, pos = if (visto) p.dur.coerceAtLeast(p.pos) else 0L, ultima = System.currentTimeMillis()))
}
