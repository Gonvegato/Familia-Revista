package com.gonzalo.marfil

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.Nodo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.ZoneId

/**
 * Seshat (por cable) y el programa del Kobo (por Wi-Fi) deciden igual qué subir: los casos de kobo/testdata/casos.json los genera el
 * programa del Kobo (go test -run TestCasos -actualiza) y aquí se comprueba que Seshat da exactamente los mismos cambios.
 */
class KoboTest {
    private fun casos(): List<Map<String, Any?>> {
        val f = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "kobo/testdata/casos.json") }.first { it.isFile }
        @Suppress("UNCHECKED_CAST") return Json.lee(f.readText()) as List<Map<String, Any?>>
    }

    @Suppress("UNCHECKED_CAST")
    private fun nodos(v: Any?): Map<String, Nodo> = (v as? Map<String, Any?>).orEmpty().mapValues { (k, n) -> Nodo(k, n as Map<String, Any?>) }

    private fun Any?.n(): Double = (this as Number).toDouble()

    /** Los cambios en la forma del PATCH (ruta → valor; la hora en `m/<campo>`), para comparar con lo del Kobo. */
    private fun plano(p: Kobo.Plan): Map<String, Any?> {
        val r = LinkedHashMap<String, Any?>()
        for (c in p.cambios) for ((campo, v) in c.campos) {
            r["familia/${c.coleccion}/${c.ref}/$campo"] = v
            r["familia/${c.coleccion}/${c.ref}/m/$campo"] = c.hora
        }
        return r
    }

    private fun igual(a: Any?, b: Any?): Boolean = when {
        a is Number && b is Number -> Math.abs(a.toDouble() - b.toDouble()) < 1e-9
        a is List<*> && b is List<*> -> a.size == b.size && a.indices.all { igual(a[it], b[it]) }
        else -> a == b
    }

    @Test fun mismosCambiosQueElProgramaDelKobo() {
        val cs = casos()
        assert(cs.size >= 8)
        for (c in cs) {
            @Suppress("UNCHECKED_CAST")
            val libros = (c["libros"] as List<Map<String, Any?>>).map {
                Kobo.Libro(it["titulo"] as String, it["autor"] as String, it["porcentaje"].n(), it["estado"].n().toInt(), it["ul"].n().toLong(), it["segundos"].n().toLong(), it["terminado"].n().toLong(),
                    marca = (it["marca"] as? String).orEmpty(), huellaKepub = (it["huella"] as? String).orEmpty())
            }
            val p = Kobo.planea(libros, nodos(c["cuentaLibros"]), nodos(c["cuentaTiempo"]), c["ahora"].n().toLong(), ZoneId.of(c["zona"] as String), c["disp"] as String)
            val tengo = plano(p)
            @Suppress("UNCHECKED_CAST") val quiero = c["cambios"] as Map<String, Any?>
            assertEquals("${c["nombre"]}: rutas", quiero.keys.sorted(), tengo.keys.sorted())
            for ((k, v) in quiero) assert(igual(v, tengo[k])) { "${c["nombre"]}: $k = ${tengo[k]}, quiero $v" }
        }
    }

    @Test fun fechasDelKobo() {
        assertEquals(1794785400000L, Kobo.fecha("2026-11-15T23:30:00Z"))
        assertEquals(1794785400000L, Kobo.fecha("2026-11-15T23:30:00.000"))
        assertEquals(1794785400000L, Kobo.fecha("2026-11-15 23:30:00.000+00:00"))
        assertEquals(1794785400000L, Kobo.fecha("2026-11-16T00:30:00+01:00"))
        assertEquals(0L, Kobo.fecha(""))
        assertEquals(0L, Kobo.fecha("basura"))
    }
}
