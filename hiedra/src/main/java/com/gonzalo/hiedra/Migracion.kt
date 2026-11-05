package com.gonzalo.hiedra

import android.content.Context
import com.gonzalo.familia.LaFamilia
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lo de antes pasa a la cuenta de la familia, una vez en cada dispositivo (la primera vez que se abre esta versión con la cuenta):
 * la biblioteca de este móvil y la que subieron tus otros Hiedra (`/users/<uid>/hiedra/dev`), los borrados, las horas, las portadas
 * que tenías puestas (las de este móvil y las que subiste a kDrive), las sagas, la apariencia y la portada de la app, el token de
 * kDrive y las claves de los servicios.
 *
 * Cada cosa se escribe con la hora en que se cambió de verdad: migre primero el dispositivo que migre, gana lo más reciente (y lo que
 * hagas después en la versión nueva siempre es más nuevo). Los nodos viejos no se tocan: quedan como copia.
 */
object Migracion {
    private val enMarcha = AtomicBoolean(false)
    private fun p(c: Context) = c.getSharedPreferences("hiedra_migracion", Context.MODE_PRIVATE)
    fun hecha(c: Context) = p(c).getBoolean("hecha", false)

    fun siHaceFalta(c: Context, esperar: Boolean = false) {
        if (!Cuenta.vinculada || hecha(c)) return
        val app = c.applicationContext
        if (esperar) { haz(app); return }
        kotlin.concurrent.thread(name = "hiedra-migracion") { haz(app) }
    }

    private fun haz(c: Context) {
        if (!enMarcha.compareAndSet(false, true)) return
        try {
            if (hecha(c) || !Cuenta.vinculada) return
            migra(c)
            p(c).edit().putBoolean("hecha", true).putLong("cuando", System.currentTimeMillis()).apply()
            LaFamilia.familia.programa(0)
        } catch (e: Exception) {
            // Sin red o la cuenta no responde: se vuelve a intentar la próxima vez
        } finally { enMarcha.set(false) }
    }

    /** Lo que antes estaba en `biblioteca.json` de este móvil. */
    fun localesViejos(c: Context): Triple<List<Juego>, Map<String, Long>, List<Carpeta>> {
        val f = File(c.filesDir, "biblioteca.json")
        val o = if (f.exists()) runCatching { JSONObject(f.readText()) }.getOrNull() else null
        o ?: return Triple(emptyList(), emptyMap(), emptyList())
        val a = o.optJSONArray("juegos") ?: JSONArray()
        val juegos = (0 until a.length()).mapNotNull { runCatching { Biblioteca.aJuego(a.getJSONObject(it)) }.getOrNull() }
        val b = o.optJSONObject("borrados")
        val borr = b?.keys()?.asSequence()?.associateWith { b.optLong(it) } ?: emptyMap()
        val cs = o.optJSONArray("carpetas") ?: JSONArray()
        val carpetas = (0 until cs.length()).mapNotNull { cs.optJSONObject(it)?.let { x -> Carpeta(x.optString("uri"), x.optString("nombre")) } }
        return Triple(juegos, borr, carpetas)
    }

    fun migra(c: Context) {
        val (locales, borrLocales, carpetas) = localesViejos(c)
        carpetas.forEach { Biblioteca.ponerCarpeta(it) }
        // 1. Los juegos: los de este móvil y los que subieron tus otros Hiedra, mezclados como antes
        val blobs = runCatching {
            val o = Cuenta.db("GET", "${Cuenta.raiz}/dev", null)?.let { JSONObject(it) } ?: JSONObject()
            o.keys().asSequence().mapNotNull { dev -> Mezcla.deJson(o.optJSONObject(dev)?.optString("data"))?.let { Blob(dev, it.juegos, it.borrados, it.ajustes, it.sagas, it.sagasBorradas, it.inicio) } }.toList()
        }.getOrDefault(emptyList())
        val r = Mezcla.mezclar(locales, borrLocales, blobs, Cuenta.dispositivo)
        for (j in r.juegos) if (j.titulo.isNotBlank()) Biblioteca.migra(j, j.mod)
        for ((id, t) in r.borrados) if (t > 0) Biblioteca.migraBorrado(id, t)

        // 2. Las portadas: las de este móvil (su archivo) y, si aquí no hay, la que subiste a kDrive desde otro
        for (j in locales) {
            val f = j.portada.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isFile && it.length() > 0 } ?: continue
            Biblioteca.migraPortada(j.id, f.readBytes(), f.lastModified())
        }
        if (KDrive.on(c)) runCatching {
            val idx = Cuenta.db("GET", "${Cuenta.raiz}/nube/portadas", null)?.let { JSONObject(it) } ?: JSONObject()
            for (id in idx.keys()) {
                val img = idx.optJSONObject(id)?.optJSONObject("f")?.optJSONObject("img") ?: continue
                if (Biblioteca.hashPortada(id).isNotBlank() || locales.any { it.id == id && it.portada.isNotBlank() && File(it.portada).isFile }) continue
                val tmp = File(c.cacheDir, "portada-$id.img")
                runCatching { KDrive.download(c, img.optLong("id"), tmp); Biblioteca.migraPortada(id, tmp.readBytes(), img.optLong("t").coerceAtLeast(1L)) }
                tmp.delete()
            }
        }

        // 3. Sagas, apariencia y portada de la app
        runCatching {
            val f = File(c.filesDir, "sagas.json")
            val o = if (f.exists()) JSONObject(f.readText()) else JSONObject()
            val sl = o.optJSONArray("sagas")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Saga::deJson) } } ?: emptyList()
            val sb = o.optJSONObject("borradas")?.let { b -> b.keys().asSequence().associateWith { b.optLong(it) } } ?: emptyMap()
            val rs = Mezcla.mezclarSagas(sl, sb, blobs, Cuenta.dispositivo)
            Sagas.migra(rs.sagas, rs.borradas)
        }
        blobs.map { it.inicio }.filter { it.isNotBlank() }.forEach { runCatching { Inicio.aplicarRemoto(it) } }
        SincroP.cambio(c)

        // 4. kDrive y las claves de los servicios, a la cuenta (una vez para toda la familia)
        KDrive.migraALaCuenta(c)
        runCatching { Servicios.refrescarDeAtrio(c) }
        Servicios.migraALaCuenta(c)

        // 5. Lo que estaba repetido (cada móvil había importado su copia): uno solo
        android.os.Handler(android.os.Looper.getMainLooper()).post { Biblioteca.refresca(); runCatching { Biblioteca.unirRepetidos() } }
    }
}
