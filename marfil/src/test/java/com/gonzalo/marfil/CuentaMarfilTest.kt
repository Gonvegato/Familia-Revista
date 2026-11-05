package com.gonzalo.marfil

import android.graphics.Bitmap
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * Marfil con la cuenta de la familia de verdad (el emulador oficial de Firebase con las reglas de `nube/reglas.json`): lo que hace
 * Marfil llega a la cuenta tal como dice `nube/ESQUEMA.md`, y lo que hace otra app u otro dispositivo (aquí, [otro]: el núcleo a
 * secas, como Azabache u otro móvil) llega a Marfil.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CuentaMarfilTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var otro: Familia

    private fun espera(que: String, cond: () -> Boolean) {
        val hasta = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < hasta) { shadowOf(Looper.getMainLooper()).idle(); if (cond()) return; Thread.sleep(50) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(que, cond())
    }

    @Before fun prepara() {
        Emulador.limpia()
        Emulador.cuenta("gonzalo")
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.configPruebas = Emulador.cfg
        Skin.cargar(ctx); Cuenta.cargar(ctx)
        runBlocking { Cuenta.entrar("gonzalo", "secreto1") }
        Biblioteca.cargada = false; Biblioteca.cargar(ctx); Sagas.cargada = false; Sagas.cargar(ctx)
        otro = Emulador.dispositivo("gonzalo")
        listOf("libros", "lectura", "marcas", "tiempo", "ajustes", "sagas").forEach { otro.coleccion(it) }
    }

    @After fun suelta() { LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = null }

    private fun sincroniza() { assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otro.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun unLibroQueAnadesSeSubeSoloATuKDriveYElOtroMovilLoBaja() {
        val kd = FakeKDrive()
        try {
            KDrive.api = kd.url
            androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(ctx,
                androidx.work.Configuration.Builder().setExecutor(androidx.work.testing.SynchronousExecutor()).build())
            KDrive.save(ctx, "tok", "777"); KDrive.confirma(ctx)
            // Añades un libro con su archivo: se programa solo su subida, con cualquier conexión (como en Atrio)
            val f = java.io.File(ctx.filesDir, "libros/solaris.epub").apply { parentFile!!.mkdirs(); writeBytes("EPUB de Solaris".toByteArray()) }
            Biblioteca.poner(Libro("x", "Solaris", "Stanisław Lem", archivo = "solaris.epub", uri = android.net.Uri.fromFile(f).toString(), anadido = 5L))
            val kid = Biblioteca.kid(claveDe("Solaris", "Stanisław Lem"))
            val wm = androidx.work.WorkManager.getInstance(ctx)
            val w = wm.getWorkInfosForUniqueWork("marfil-libros-subir").get().single()
            assertEquals(androidx.work.WorkInfo.State.ENQUEUED, w.state)
            assertEquals("sin esperar al Wi-Fi", androidx.work.NetworkType.CONNECTED, w.constraints.requiredNetworkType)
            // Hay conexión: se sube
            androidx.work.testing.WorkManagerTestInitHelper.getTestDriver(ctx)!!.run { setAllConstraintsMet(w.id); setInitialDelayMet(w.id) }
            espera("subido a tu kDrive") { kd.byPath("Marfil/Libros/Solaris/Solaris.epub") != null }
            assertEquals("EPUB de Solaris", String(kd.byPath("Marfil/Libros/Solaris/Solaris.epub")!!.bytes))
            espera("apuntado en el libro de la cuenta") { Biblioteca.nube(kid) != null }
            assertTrue("no queda nada por subir", NubeLibros.porSubir(ctx).isEmpty())
            sincroniza()
            val n = otro.coleccion("libros").nodo(kid)!!.mapa("nube")
            assertEquals("Solaris.epub", n["n"])
            // En tu otro móvil (aquí, el mismo Marfil sin el archivo): se baja de tu kDrive
            Biblioteca.ponUri(kid, "")
            Biblioteca.poner(Biblioteca.por(kid)!!.copy(uri = ""))
            espera("sin archivo aquí") { Biblioteca.por(kid)?.enOtroDispositivo == true }
            assertEquals("está para bajar", listOf(kid), NubeLibros.porBajar(ctx).map { it.id })
            assertEquals(1, NubeLibros.bajarTodos(ctx))
            espera("bajado") { Biblioteca.por(kid)?.enOtroDispositivo == false }
            assertEquals("EPUB de Solaris", java.io.File(android.net.Uri.parse(Biblioteca.por(kid)!!.uri).path!!).readText())
        } finally { kd.stop(); KDrive.api = "https://api.infomaniak.com" }
    }

    @Test fun unLibroLeidoSinFechaCuentaParaElRetoDelAno() {
        // Leído hace unos meses en otro sitio (Atrio, otro dispositivo…), sin fecha de terminado: cuenta desde que pasó a «terminado»
        val marzo = java.time.LocalDate.of(2026, 3, 14).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val kid = Biblioteca.kid(claveDe("Solo Leveling", "Sung Jin Woo"))
        otro.coleccion("libros").pon(kid, mapOf("clave" to claveDe("Solo Leveling", "Sung Jin Woo"), "t" to "Solo Leveling", "a" to "Sung Jin Woo", "estado" to "terminado"), marzo)
        // Y otro con su fecha, como siempre
        val kid2 = Biblioteca.kid(claveDe("Dune", "Frank Herbert"))
        otro.coleccion("libros").pon(kid2, mapOf("clave" to claveDe("Dune", "Frank Herbert"), "t" to "Dune", "a" to "Frank Herbert", "estado" to "terminado", "fin" to listOf(marzo + 86_400_000L)))
        sincroniza()
        espera("llegan los dos") { Biblioteca.por(kid) != null && Biblioteca.por(kid2) != null }
        assertEquals(listOf(marzo), Biblioteca.por(kid)!!.terminados)
        assertEquals("cuentan los dos", 2, terminadosEn(Biblioteca.propios(), 2026))
        assertEquals(0, terminadosEn(Biblioteca.propios(), 2025))
    }

    @Test fun lasFechasDeAtrioQuedaronEn1970YSeCorrigenEnLaCuenta() {
        // Lo que dejó la importación de Atrio: el día de terminar como número de día (20 de mayo de 2026), tomado por milisegundos
        val dia = java.time.LocalDate.of(2026, 5, 20).toEpochDay()
        val kid = Biblioteca.kid(claveDe("Coiling Dragon 9", "I Eat Tomatoes"))
        otro.coleccion("libros").pon(kid, mapOf("clave" to claveDe("Coiling Dragon 9", "I Eat Tomatoes"), "t" to "Coiling Dragon 9", "a" to "I Eat Tomatoes", "estado" to "terminado", "fin" to listOf(dia), "an" to dia - 30))
        sincroniza()
        espera("llega") { Biblioteca.por(kid) != null }
        val l = Biblioteca.por(kid)!!
        assertEquals(java.time.LocalDate.of(2026, 5, 20), java.time.Instant.ofEpochMilli(l.terminado).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
        assertEquals("cuenta para el reto de 2026", 1, terminadosEn(Biblioteca.propios(), 2026))
        // Y queda corregido en la cuenta (para Azabache y tus otros dispositivos)
        sincroniza()
        val fin = otro.coleccion("libros").nodo(kid)!!.lista("fin").map { (it as Number).toLong() }
        assertEquals(listOf(l.terminado), fin)
        assertTrue(otro.coleccion("libros").nodo(kid)!!.entero("an") > 1_000_000_000_000L)
    }

    @Test fun unLibroDeMarfilEsElMismoEnLaCuentaYEnAzabache() {
        Biblioteca.poner(Libro("x", "Dune", "Frank Herbert", estado = Estado.LEYENDO, archivo = "dune.epub", anadido = 5L))
        val kid = Biblioteca.kid(claveDe("Dune", "Frank Herbert"))
        assertEquals("se llama por su kid", kid, Biblioteca.existente("Dune", "Frank Herbert")!!.id)
        sincroniza()
        val n = otro.coleccion("libros").nodo(kid)!!
        assertEquals("Dune", n.texto("t")); assertEquals("Frank Herbert", n.texto("a")); assertEquals("en_curso", n.texto("estado")); assertEquals("dune.epub", n.texto("archivo"))
        // Azabache lo valora y lo da por terminado: Marfil lo ve tal cual (y nunca con una nota que no le has puesto)
        otro.coleccion("libros").pon(kid, mapOf("nota" to 88L, "resena" to "La especia.", "estado" to "terminado"))
        sincroniza()
        espera("llega el estado de fuera") { Biblioteca.por(kid)?.estado == Estado.LEIDO }
        assertEquals(88, NotaAzabache.cargar(ctx)[claveDe("Dune", "Frank Herbert")]!!.general)
        // Un libro sin valorar no tiene nota
        Biblioteca.poner(Libro("y", "Ubik", "Philip K. Dick"))
        assertEquals(null, NotaAzabache.cargar(ctx)[claveDe("Ubik", "Philip K. Dick")])
    }

    @Test fun laPortadaVaYViene() {
        Biblioteca.poner(Libro("x", "Dune", "Frank Herbert"))
        val kid = Biblioteca.kid(claveDe("Dune", "Frank Herbert"))
        val png = ByteArrayOutputStream().also { Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF8C2A22.toInt()) }.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        Portadas.poner(ctx, Biblioteca.por(kid)!!, png)
        sincroniza()
        val h = otro.coleccion("libros").nodo(kid)!!.texto("portada")
        assertTrue(h.isNotBlank())
        assertArrayEquals("la misma imagen en el otro dispositivo", LaFamilia.familia.imagenes.local(h), otro.imagenes.baja(h))
        // Otro dispositivo la cambia: llega aquí y se baja sola
        val otra = ByteArrayOutputStream().also { Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2F5D8A.toInt()) }.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        val h2 = otro.imagenes.guarda(otra, "image/jpeg")
        otro.coleccion("libros").pon(kid, mapOf("portada" to h2))
        sincroniza()
        espera("la portada nueva se ve aquí") { Biblioteca.por(kid)?.portada?.endsWith(h2) == true }
        // Y se quita en todas partes
        Portadas.quitar(ctx, Biblioteca.por(kid)!!)
        sincroniza()
        assertEquals("", otro.coleccion("libros").nodo(kid)!!.texto("portada"))
    }

    @Test fun loLeidoViajaPorSeparado() {
        Biblioteca.poner(Libro("x", "Dune", "Frank Herbert"))
        val kid = Biblioteca.kid(claveDe("Dune", "Frank Herbert"))
        val hoy = java.time.LocalDate.now().toString()
        val st = JSONObject().put("cfi", "epubcfi(/6/4)").put("fraction", 0.3).put("read", 1000L)
            .put("notes", JSONArray().put(JSONObject().put("value", "v1").put("created", 10L).put("text", "El miedo mata la mente.").put("mod", 10L)))
            .put("stats", ReadStats.derived(JSONObject().put(Cuenta.dispositivo, JSONObject().put("total", 600_000L).put("days", JSONObject().put(hoy, 600_000L)))))
        EstadoLectura.ponerSinAvisar(ctx, kid, st)
        LecturaNube.sube(ctx, kid, st)
        sincroniza()
        assertEquals("epubcfi(/6/4)", otro.coleccion("lectura").nodo(kid)!!.texto("cfi"))
        val marcas = otro.coleccion("marcas").vivos().filter { it.texto("ref") == kid }
        assertEquals(1, marcas.size); assertEquals("El miedo mata la mente.", marcas[0].mapa("n")["text"])
        val t = otro.coleccion("tiempo").nodo("$kid~${Cuenta.dispositivo}")!!
        assertEquals(600_000L, t.entero("total")); assertEquals(600_000L, t.entero("d$hoy"))
        // Un subrayado hecho en otro dispositivo y el tiempo que leíste allí llegan al lector de aquí
        otro.coleccion("marcas").pon("$kid~otra", mapOf("ref" to kid, "tipo" to "notes", "n" to mapOf("value" to "v2", "created" to 20L, "text" to "Quien controla la especia…", "mod" to 20L)), 20L)
        otro.coleccion("tiempo").pon("$kid~tablet", mapOf("ref" to kid, "fuente" to "tablet", "total" to 300_000L, "d$hoy" to 300_000L))
        sincroniza()
        espera("el subrayado de fuera está en el lector") { EstadoLectura.estado(ctx, kid).optJSONArray("notes")?.length() == 2 }
        espera("el tiempo se suma") { Biblioteca.por(kid)?.msLeidos == 900_000L }
    }

    @Test fun losObjetivosSonDeLaCuenta() {
        Ajustes.ponMetaLectura(ctx, 30); Ajustes.ponMetaAnual(ctx, 12)
        sincroniza()
        val n = otro.coleccion("ajustes").nodo("lectura")!!
        assertEquals(30L, n.entero("dia")); assertEquals(12L, n.entero("ano"))
        otro.coleccion("ajustes").pon("lectura", mapOf("ano" to 20L))
        sincroniza()
        assertEquals(20, Ajustes.metaAnual(ctx)); assertEquals(30, Ajustes.metaLectura(ctx))
    }

    @Test fun laMigracionTraeLoDeAntesConSuHora() {
        val uid = LaFamilia.familia.sesion.uid
        // Lo de antes: otro Marfil subió su biblioteca (formato viejo) y este móvil tenía la suya en biblioteca.json
        val viejo = Libro("viejo1", "Rayuela", "Julio Cortázar", estado = Estado.LEIDO, mod = 1_000L, terminados = listOf(500L), anadido = 100L)
        val blob = Mezcla.aJson(Mezcla.aBlob("tablet", listOf(viejo), emptyMap()))
        Emulador.escribe("/users/$uid/marfil/dev/tablet", mapOf("name" to "Tablet", "data" to blob))
        val local = Libro("loc1", "Dune", "Frank Herbert", estado = Estado.LEYENDO, uri = "file:///x/dune.epub", mod = 2_000L, anadido = 200L)
        java.io.File(ctx.filesDir, "biblioteca.json").writeText(JSONObject().put("libros", JSONArray().put(Biblioteca.aJson(local))).toString())
        EstadoLectura.ponerSinAvisar(ctx, "loc1", JSONObject().put("cfi", "c1").put("read", 2_500L))
        Migracion.migra(ctx)
        sincroniza()
        val rayuela = otro.coleccion("libros").nodo(Biblioteca.kid(claveDe("Rayuela", "Julio Cortázar")))!!
        assertEquals("terminado", rayuela.texto("estado")); assertEquals(1_000L, rayuela.m["estado"])
        val dune = Biblioteca.kid(claveDe("Dune", "Frank Herbert"))
        assertEquals("en_curso", otro.coleccion("libros").nodo(dune)!!.texto("estado"))
        assertEquals("el archivo de este móvil sigue aquí", "file:///x/dune.epub", Biblioteca.por(dune)!!.uri)
        assertEquals("el estado de lectura se llama ahora por su kid", "c1", EstadoLectura.estado(ctx, dune).optString("cfi"))
        // Un cambio de la versión nueva es más nuevo que lo migrado: migrar otra vez (otro móvil) no lo pisa
        Biblioteca.poner(Biblioteca.por(dune)!!.copy(estado = Estado.ABANDONADO))
        sincroniza()
        Migracion.migra(ctx)
        sincroniza()
        assertEquals("abandonado", otro.coleccion("libros").nodo(dune)!!.texto("estado"))
    }
}
