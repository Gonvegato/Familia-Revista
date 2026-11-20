package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.NubeFamilia
import com.gonzalo.familia.nucleo.GoogleDrive
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.RedJava
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Migración completa desde kDrive a Google Drive directamente dentro de Jonsu (sin PC ni terminal).
 *
 * - Copia recursivamente `Atrio`, `Marfil`, `Hiedra`, `Mostaza` y `versiones` desde «Mi carpeta» de kDrive
 *   a tu carpeta raíz de Google Drive (`Familia`), saltando archivos que ya estén con el mismo nombre y tamaño.
 * - Nunca borra ni modifica nada en kDrive.
 * - Guarda un mapa persistente `kdrive_id → gdrive_id` tras cada archivo (se puede reanudar si se corta).
 * - Añade el campo aditivo `gd` en Firebase para Seshat (libros), Jonsu (fotos/capturas/libros) y Bastet
 *   (ROMs, partidas y estados rápidos), conservando siempre los ids antiguos `f`/`id`.
 */
object MigradorGDrive {
    private val CARPETAS = listOf("Atrio", "Marfil", "Hiedra", "Mostaza", "versiones")
    private val ejecutando = AtomicBoolean(false)

    var enMarcha by mutableStateOf(false); private set
    var progreso by mutableStateOf(""); private set
    var resumen by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null); private set

    private fun archivoMapa(ctx: Context) = File(ctx.filesDir, "migracion-gdrive.json")

    private fun leeMapa(ctx: Context): MutableMap<Long, String> {
        val out = LinkedHashMap<Long, String>()
        val f = archivoMapa(ctx)
        if (!f.exists()) return out
        runCatching {
            val o = JSONObject(f.readText())
            for (k in o.keys()) k.toLongOrNull()?.let { id -> o.optString(k).takeIf { it.isNotBlank() }?.let { out[id] = it } }
        }
        return out
    }

    private fun guardaMapa(ctx: Context, mapa: Map<Long, String>) {
        val o = JSONObject()
        mapa.forEach { (k, v) -> o.put(k.toString(), v) }
        val tmp = File(ctx.filesDir, "migracion-gdrive.json.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(archivoMapa(ctx))
    }

    /** Lanza la migración en segundo plano (si ya está en marcha, no duplica). */
    fun iniciar(ctx: Context) {
        if (!ejecutando.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        enMarcha = true; error = null; resumen = ""; progreso = "Preparando migración…"
        kotlin.concurrent.thread(name = "migrar-gdrive") {
            try {
                val r = ejecutar(app)
                resumen = r
                progreso = ""
            } catch (e: Throwable) {
                error = e.message ?: e.javaClass.simpleName
                progreso = ""
            } finally {
                enMarcha = false
                ejecutando.set(false)
            }
        }
    }

    /** Ejecución síncrona (bloqueante; también usable en pruebas). */
    internal fun ejecutar(ctx: Context): String {
        if (!NubeFamilia.gdriveOn(ctx)) throw NubeFamilia.Fail("Conecta primero tu Google Drive arriba")
        if (!NubeFamilia.kdriveOn(ctx)) throw NubeFamilia.Fail("No hay ningún kDrive conectado para migrar")

        val gd = NubeFamilia.gdrive(ctx)
        val raizGd = gd.carpetaRaiz()
        val homeKd = NubeFamilia.homeKDrive(ctx)
        val mapa = leeMapa(ctx)

        var copiados = 0
        var saltados = 0
        var errores = 0

        val hijosHome = NubeFamilia.childrenKDrive(ctx, homeKd).filter { it.dir }.associateBy { it.name }

        fun migraCarpeta(kdDir: Long, rutaRel: String, gdDirId: String) {
            val existentesGd = runCatching { gd.dentro(gdDirId) }.getOrDefault(emptyList())
            val porNombreGd = existentesGd.filter { !it.carpeta }.associateBy { it.nombre }
            val carpetasGd = existentesGd.filter { it.carpeta }.associateBy { it.nombre }

            for (item in NubeFamilia.childrenKDrive(ctx, kdDir)) {
                if (item.name.isBlank() || item.name.startsWith(".")) continue
                val subRuta = if (rutaRel.isBlank()) item.name else "$rutaRel/${item.name}"
                if (item.dir) {
                    val subGd = carpetasGd[item.name]?.id ?: gd.creaCarpeta(item.name, gdDirId)
                    migraCarpeta(item.id, subRuta, subGd)
                } else {
                    val ya = porNombreGd[item.name]
                    if (ya != null && (item.size <= 0L || ya.tamano == item.size)) {
                        mapa[item.id] = ya.id
                        guardaMapa(ctx, mapa)
                        saltados++
                        progreso = "Ya en Google Drive ($saltados): ${item.name}"
                        continue
                    }
                    progreso = "Copiando (${copiados + 1}): $subRuta…"
                    val tmp = File(ctx.cacheDir, "mig-${item.id}.tmp")
                    try {
                        NubeFamilia.downloadKDrive(ctx, item.id, tmp)
                        val gdId = gd.sube(rutaRel, item.name, tmp.length()) { tmp.inputStream() }
                        mapa[item.id] = gdId
                        guardaMapa(ctx, mapa)
                        copiados++
                    } catch (_: Throwable) {
                        errores++
                    } finally {
                        tmp.delete()
                    }
                }
            }
        }

        for (carpeta in CARPETAS) {
            val dir = hijosHome[carpeta] ?: continue
            progreso = "Revisando «$carpeta»…"
            val gdDir = gd.aseguraCarpeta(carpeta)
            migraCarpeta(dir.id, carpeta, gdDir)
        }

        progreso = "Enlazando archivos en tu cuenta…"
        val enlazados = enlazarEnCuenta(ctx, mapa)

        val parteErr = if (errores > 0) " · $errores con error (puedes volver a pulsar para reintentar)" else ""
        return "Listo: $copiados copiados, $saltados ya estaban en Google Drive, $enlazados enlazados en la cuenta$parteErr."
    }

    private fun enlazarEnCuenta(ctx: Context, mapa: Map<Long, String>): Int {
        if (mapa.isEmpty()) return 0
        var total = 0

        // 1. Libros de Seshat (colección `libros` de la cuenta de la familia)
        runCatching {
            val col = LaFamilia.coleccion("libros")
            for (n in col.vivos()) {
                val nube = n.mapa("nube")
                if (nube.isEmpty()) continue
                val yaGd = nube["gd"]?.toString().orEmpty()
                if (yaGd.isNotBlank()) continue
                val kdId = (nube["f"] as? Number)?.toLong() ?: 0L
                val gdId = mapa[kdId] ?: continue
                val nuevo = LinkedHashMap<String, Any?>().apply {
                    nube.forEach { (k, v) -> put(k, v) }
                    put("gd", gdId)
                }
                col.pon(n.ref, mapOf("nube" to nuevo))
                total++
            }
        }

        // 2. Fotos, capturas y libros de Jonsu (`Store.loadNube()`)
        runCatching {
            val store = Store(ctx)
            val actualizados = store.loadNube().mapNotNull { x ->
                if (x.gd.isBlank() && x.f > 0L && mapa.containsKey(x.f)) {
                    total++
                    x.copy(gd = mapa[x.f]!!, updated = System.currentTimeMillis())
                } else null
            }
            if (actualizados.isNotEmpty()) {
                store.addNube(actualizados)
                runCatching { AzabacheNube.sube(ctx, store) }
            }
        }

        // 3. ROMs, partidas y estados rápidos de Bastet (`/users/<uid>/hiedra/nube/{roms,saves,states}`)
        runCatching {
            val ses = LaFamilia.familia.sesion
            if (ses.dentro) {
                val red = RedJava()
                val r = red.pedir("GET", ses.url("/users/${ses.uid}/hiedra/nube", conPase = true))
                if (r.ok && r.cuerpo.isNotBlank() && r.cuerpo != "null") {
                    val raiz = JSONObject(r.cuerpo)
                    val parches = LinkedHashMap<String, Any>()
                    for (sec in listOf("roms", "saves", "states")) {
                        val bloque = raiz.optJSONObject(sec) ?: continue
                        for (kid in bloque.keys()) {
                            val files = bloque.optJSONObject(kid)?.optJSONObject("f") ?: continue
                            for (slot in files.keys()) {
                                val ficha = files.optJSONObject(slot) ?: continue
                                if (ficha.optString("gd").isNotBlank()) continue
                                val kdId = ficha.optLong("id")
                                val gdId = mapa[kdId] ?: continue
                                parches["$sec/$kid/f/$slot/gd"] = gdId
                                total++
                            }
                        }
                    }
                    if (parches.isNotEmpty()) {
                        val items = parches.entries.toList()
                        for (i in items.indices step 50) {
                            val lote = items.subList(i, minOf(i + 50, items.size)).associate { it.key to it.value }
                            red.pedir("PATCH", ses.url("/users/${ses.uid}/hiedra/nube", conPase = true), Json.escribe(lote))
                        }
                    }
                }
            }
        }

        runCatching { LaFamilia.familia.programa(0) }
        return total
    }
}
