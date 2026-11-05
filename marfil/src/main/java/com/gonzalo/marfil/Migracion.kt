package com.gonzalo.marfil

import android.content.Context
import com.gonzalo.familia.LaFamilia
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lo de antes pasa a la cuenta de la familia, una vez en cada dispositivo (la primera vez que se abre esta versión con la cuenta):
 * la biblioteca de este móvil y la que subieron tus otros Marfil (`/users/<uid>/marfil/dev`), los borrados, las portadas que
 * tenías puestas, lo leído de cada libro (posición, subrayados, notas, marcadores y tiempo), las sagas, los objetivos, la apariencia,
 * los ajustes del lector, dónde está cada libro en tu kDrive y el token de kDrive.
 *
 * Cada cosa se escribe con la hora en que se cambió de verdad: así, migre primero el dispositivo que migre, gana lo más reciente (y
 * lo que hagas después en la versión nueva siempre es más nuevo). Los nodos viejos no se tocan: quedan como copia.
 */
object Migracion {
    private val enMarcha = AtomicBoolean(false)
    private fun p(c: Context) = c.getSharedPreferences("marfil_migracion", Context.MODE_PRIVATE)

    fun hecha(c: Context) = p(c).getBoolean("hecha", false)

    /** Si hace falta (hay cuenta y aún no se ha hecho aquí), se hace: en segundo plano o, con [esperar], ya. */
    fun siHaceFalta(c: Context, esperar: Boolean = false) {
        if (!Cuenta.vinculada || hecha(c)) return
        val app = c.applicationContext
        if (esperar) { haz(app); return }
        kotlin.concurrent.thread(name = "marfil-migracion") { haz(app) }
    }

    private fun haz(c: Context) {
        if (!enMarcha.compareAndSet(false, true)) return
        try {
            if (hecha(c) || !Cuenta.vinculada) return
            migra(c)
            p(c).edit().putBoolean("hecha", true).putLong("cuando", System.currentTimeMillis()).apply()
            LaFamilia.familia.programa(0)
        } catch (e: Exception) {
            // Sin red o la cuenta no responde: se vuelve a intentar la próxima vez (no se marca como hecha)
        } finally { enMarcha.set(false) }
    }

    /** Lo que antes estaba en `biblioteca.json` de este móvil (con sus ids viejos, sus archivos y sus portadas). */
    fun localesViejos(c: Context): Pair<List<Libro>, Map<String, Long>> {
        val f = File(c.filesDir, "biblioteca.json")
        if (!f.exists()) return emptyList<Libro>() to emptyMap()
        val o = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return emptyList<Libro>() to emptyMap()
        val a = o.optJSONArray("libros")
        val libros = (0 until (a?.length() ?: 0)).mapNotNull { runCatching { Biblioteca.aLibro(a!!.getJSONObject(it)) }.getOrNull() }
        val b = o.optJSONObject("borrados")
        val borr = b?.keys()?.asSequence()?.associateWith { b.optLong(it) } ?: emptyMap()
        return libros to borr
    }

    fun migra(c: Context) {
        val fam = LaFamilia.familia
        val libros = LaFamilia.coleccion("libros")
        // 1. La biblioteca: la de este móvil y la que subieron tus otros Marfil, mezcladas como antes
        val (locales, borrLocales) = localesViejos(c)
        val blobs = runCatching {
            val raw = Cuenta.db("GET", "${Cuenta.raiz}/dev", null)
            val o = raw?.let { JSONObject(it) } ?: JSONObject()
            o.keys().asSequence().mapNotNull { dev -> Mezcla.deJson(o.optJSONObject(dev)?.optString("data"))?.let { Blob(dev, it.libros, it.borrados, it.ajustes, it.sagas, it.sagasBorradas, it.lector) } }.toList()
        }.getOrDefault(emptyList())
        val r = Mezcla.mezclar(locales.filter { !it.esEscrito }, borrLocales, blobs, Cuenta.dispositivo)
        val idViejo = HashMap<String, String>()   // id viejo → kid
        // (los ids viejos de este móvil, para mover sus estados de lectura y sus widgets)
        locales.forEach { l -> idViejo[l.id] = Biblioteca.kid(l.clave) }
        for (l in r.libros) {
            if (l.titulo.isBlank() || l.esEscrito) continue
            val kid = Biblioteca.kid(l.clave)
            idViejo[l.id] = kid
            libros.pon(kid, Biblioteca.campos(l.copy(id = kid)), l.mod.coerceAtLeast(1L))
            val local = locales.firstOrNull { it.clave == l.clave }
            val uri = local?.uri?.ifBlank { null } ?: l.uri
            if (uri.isNotBlank()) Biblioteca.ponUri(kid, uri)
        }
        for ((clave, t) in r.borrados) if (t > 0) libros.pon(Biblioteca.kid(clave), mapOf("b" to t, "clave" to clave), t)

        // 2. Las portadas que tenías puestas en este móvil (y, si no hay archivo aquí, la que subiste a kDrive desde otro)
        val viejasEnKdrive = runCatching { Cuenta.db("GET", "${Cuenta.raiz}/portadas", null)?.let { JSONObject(it) } }.getOrNull() ?: JSONObject()
        for (l in locales) {
            if (l.esEscrito) continue
            val kid = Biblioteca.kid(l.clave)
            val f = l.portada.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { File(it) }?.takeIf { it.isFile && it.length() > 0 }
            val bytes = f?.readBytes() ?: (if (l.portada.startsWith("http")) Portadas.descargar(l.portada) else null)
            val red = bytes?.let { Portadas.reducir(it) } ?: continue
            val h = fam.imagenes.guarda(red, "image/jpeg")
            libros.pon(kid, mapOf("portada" to h, "pu" to l.portadaUrl), (f?.lastModified() ?: l.mod).coerceAtLeast(1L))
        }
        if (KDrive.on(c)) for (k in viejasEnKdrive.keys()) {
            val e = viejasEnKdrive.optJSONObject(k) ?: continue
            if (e.optInt("p") != 1 || e.optInt("x") == 1 || e.optLong("f") <= 0) continue
            if (libros.nodo(k)?.texto("portada")?.isNotBlank() == true) continue
            val tmp = File(c.cacheDir, "portada-$k.jpg")
            runCatching {
                KDrive.download(c, e.optLong("f"), tmp)
                Portadas.reducir(tmp.readBytes())?.let { red -> libros.pon(k, mapOf("portada" to fam.imagenes.guarda(red, "image/jpeg")), e.optLong("u").coerceAtLeast(1L)) }
            }
            tmp.delete()
        }

        // 3. Lo leído de cada libro: lo de este móvil (con su id viejo) unido con lo que subieron tus otros Marfil
        val kids = (idViejo.values + r.libros.map { Biblioteca.kid(it.clave) }).toSet()
        for (kid in kids) {
            var st = JSONObject()
            idViejo.filterValues { it == kid }.keys.forEach { viejo ->
                val local = EstadoLectura.estado(c, viejo)
                if (local.length() > 0) st = MezclaLectura.mezclar(st, local, Cuenta.dispositivo)
            }
            runCatching {
                Cuenta.db("GET", "${Cuenta.raiz}/lec/$kid", null)?.let { JSONObject(it) }?.let { devs ->
                    for (d in devs.keys()) devs.optJSONObject(d)?.optJSONObject("s")?.let { s -> st = MezclaLectura.mezclar(st, s, Cuenta.dispositivo) }
                }
            }
            if (st.length() == 0) continue
            EstadoLectura.ponerSinAvisar(c, kid, st)
            LecturaNube.sube(c, kid, st, st.optLong("read").takeIf { it > 0 })
        }
        idViejo.keys.filter { it !in kids }.forEach { EstadoLectura.olvidar(c, it) }
        idViejo.forEach { (viejo, kid) -> if (viejo != kid) EstadoLectura.olvidar(c, viejo) }

        // 4. Sagas, objetivos, apariencia y ajustes del lector
        runCatching {
            val (sagasLocales, borrSagas) = sagasViejas(c)
            val rs = Mezcla.mezclarSagas(sagasLocales, borrSagas, blobs, Cuenta.dispositivo)
            Sagas.migra(rs.sagas, rs.borradas)
        }
        runCatching { Objetivos.migra(c) }
        SincroP.cambio(c)

        // 5. kDrive: el token de este móvil a la cuenta y dónde está cada libro
        KDrive.migraALaCuenta(c)
        runCatching {
            Cuenta.db("GET", "${Cuenta.raiz}/nube", null)?.let { JSONObject(it) }?.let { o ->
                for (k in o.keys()) NubeLibros.deJson(o.optJSONObject(k))?.let { x -> if (libros.nodo(k)?.mapa("nube")?.isEmpty() != false) libros.pon(k, mapOf("nube" to mapOf("f" to x.f, "n" to x.n, "s" to x.s)), 1L) }
            }
        }

        // 6. Los widgets que seguían un libro concreto: ahora se llama por su kid
        val w = c.getSharedPreferences("marfil_widgets", Context.MODE_PRIVATE)
        val ed = w.edit()
        w.all.forEach { (k, v) -> if (k.endsWith("_fijo") && v is String) idViejo[v]?.let { ed.putString(k, it) } }
        ed.apply()
    }

    private fun sagasViejas(c: Context): Pair<List<Saga>, Map<String, Long>> {
        val f = File(c.filesDir, "sagas.json")
        if (!f.exists()) return emptyList<Saga>() to emptyMap()
        val o = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return emptyList<Saga>() to emptyMap()
        val a = o.optJSONArray("sagas")
        val sagas = (0 until (a?.length() ?: 0)).mapNotNull { a!!.optJSONObject(it)?.let(Saga::deJson) }
        val b = o.optJSONObject("borradas")
        return sagas to (b?.keys()?.asSequence()?.associateWith { b.optLong(it) } ?: emptyMap())
    }
}
