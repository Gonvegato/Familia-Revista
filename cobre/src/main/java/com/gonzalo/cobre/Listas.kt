package com.gonzalo.cobre

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.DiscoCifrado
import com.gonzalo.familia.LaFamilia
import com.gonzalo.iptv.DatosCuenta
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.TipoLista
import java.io.File

/**
 * Tus listas (M3U y logins de Xtream). Son tuyas y viajan con la cuenta, pero **solo en `/claves/<uid>/cobre_listas`**: sus direcciones y
 * tus contraseñas son secretos, y `/claves` es lo único de la cuenta que no puede leer ni tu pareja (las reglas). Nunca van a
 * `/users/<uid>`, ni a un registro, ni a un mensaje de error. En el dispositivo, la copia de las claves va cifrada (la de la familia).
 * Sin cuenta todavía, se quedan aquí (cifradas) y pasan a la cuenta al entrar, como los addons de Mostaza.
 *
 * Cobre no trae ninguna lista ni ningún canal: las pones tú.
 */
object Listas {
    /** Sube al cambiar tus listas (aquí o en otro dispositivo). */
    var version by mutableIntStateOf(0)
        private set

    private var appCtx: Context? = null

    fun cargar(c: Context) {
        if (appCtx != null) return
        appCtx = c.applicationContext
        LaFamilia.alCambiar { version++ }
        LaFamilia.alEntrar { kotlin.concurrent.thread { runCatching { pasaLoDeSinCuenta() } } }
    }

    private fun sinCuenta() = appCtx?.let { DiscoCifrado.de(it, File(it.filesDir, "cobre_seguro")) }

    fun lista(): List<Lista> {
        version
        val t = runCatching { LaFamilia.familia.clave(DatosCuenta.CLAVE) }.getOrDefault("").ifBlank { sinCuenta()?.leeTexto("listas.json").orEmpty() }
        return if (t.isBlank()) emptyList() else DatosCuenta.listasDeTexto(t)
    }

    fun por(id: String): Lista? = lista().firstOrNull { it.id == id }

    /** Las que se pueden usar en este dispositivo (las de archivo, solo donde está el archivo). */
    fun usables(): List<Lista> = lista().filter { it.tipo != TipoLista.ARCHIVO || it.dispositivo == Cuenta.dispositivo }

    fun guarda(l: List<Lista>) {
        val t = DatosCuenta.listasATexto(l)
        val fam = runCatching { LaFamilia.familia }.getOrNull()
        if (fam != null && fam.sesion.dentro) fam.ponClave(DatosCuenta.CLAVE, t) else sinCuenta()?.escribeTexto("listas.json", t)
        version++
    }

    /** Pone una lista (nueva, o la misma con lo cambiado, en su sitio). */
    fun pon(n: Lista) { val l = lista(); guarda(if (l.any { it.id == n.id }) l.map { if (it.id == n.id) n else it } else l + n) }

    fun quita(id: String) = guarda(lista().filter { it.id != id })

    fun mueve(id: String, d: Int) {
        val l = lista().toMutableList(); val i = l.indexOfFirst { it.id == id }; val j = i + d
        if (i < 0 || j !in l.indices) return
        l.add(j, l.removeAt(i)); guarda(l)
    }

    private fun pasaLoDeSinCuenta() {
        val d = sinCuenta() ?: return
        val t = d.leeTexto("listas.json") ?: return
        val fam = LaFamilia.familia
        if (!fam.sesion.dentro) return
        // Lo de la cuenta manda; lo de aquí se junta (sin repetir)
        val cuenta = DatosCuenta.listasDeTexto(fam.clave(DatosCuenta.CLAVE))
        val aqui = DatosCuenta.listasDeTexto(t)
        val juntas = cuenta + aqui.filter { a -> cuenta.none { it.id == a.id } }
        if (juntas != cuenta) fam.ponClave(DatosCuenta.CLAVE, DatosCuenta.listasATexto(juntas))
        d.borra("listas.json")
        version++
    }

    /** (Para las pruebas.) */
    fun olvidaParaPruebas() { appCtx = null }
}
