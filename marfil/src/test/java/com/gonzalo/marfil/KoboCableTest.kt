package com.gonzalo.marfil

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** La base de datos del Kobo se lee igual que en el programa del Kobo (kobo_test.go): mismos libros, mismas columnas que pueden faltar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KoboCableTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun base(conTiempo: Boolean): File {
        val f = File(tmp.root, "KoboReader-$conTiempo.sqlite")
        SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
            db.execSQL("CREATE TABLE content (ContentID TEXT, ContentType TEXT, Title TEXT, BookTitle TEXT, Attribution TEXT, ___PercentRead INTEGER, " +
                "ReadStatus INTEGER, DateLastRead TEXT, LastTimeFinishedReading TEXT, ImageId TEXT" + (if (conTiempo) ", TimeSpentReading INTEGER" else "") + ")")
            val filas = listOf(
                arrayOf<Any?>("file:///mnt/onboard/rojo.epub", "6", "Amanecer rojo", "", "Pierce Brown", 43, 1, "2026-11-15T21:10:05Z", null, "x", 3600),
                arrayOf<Any?>("file:///mnt/onboard/palomino.epub", "6", "¿Quién mató a Palomino Molero?", "", "Mario Vargas Llosa", 100, 2, "2026-11-10T08:00:00.000", "2026-11-10 08:00:00.000+00:00", "x", 25000),
                arrayOf<Any?>("file:///mnt/onboard/nuevo.epub", "6", "Sin abrir", "", "Nadie", 0, 0, null, null, "x", 0),
                arrayOf<Any?>("file:///mnt/onboard/rojo.epub!cap1", "9", "Capítulo 1", "Amanecer rojo", "", 50, 1, null, null, "x", 0),
                arrayOf<Any?>("kepub-id", "6", "", "Solo BookTitle", "Alguien", 5, 1, "2026-11-01T10:00:00Z", null, "x", 60),
            )
            for (f0 in filas) {
                val v = if (conTiempo) f0 else f0.copyOfRange(0, 10)
                db.execSQL("INSERT INTO content VALUES (" + v.joinToString(",") { "?" } + ")", v)
            }
        }
        return f
    }

    @Test fun leeLosLibrosAbiertos() {
        val l = KoboCable.leeBase(base(true))
        assertEquals(listOf("Amanecer rojo", "¿Quién mató a Palomino Molero?", "Solo BookTitle"), l.map { it.titulo })
        val rojo = l[0]
        assertEquals("Pierce Brown", rojo.autor); assertEquals(43.0, rojo.porcentaje, 0.0); assertEquals(1, rojo.estado); assertEquals(3600L, rojo.segundos)
        assertEquals(Kobo.fecha("2026-11-15T21:10:05Z"), rojo.ul)
        assertEquals(2, l[1].estado); assertEquals(Kobo.fecha("2026-11-10T08:00:00Z"), l[1].terminado)
    }

    /** En un kepub copiado al Kobo se lee también por dónde vas (ChapterIDBookmarked); en un EPUB o un libro de la tienda, no. */
    @Test fun laMarcaDeLosKepub() {
        val f = File(tmp.root, "KoboReader-kepub.sqlite")
        SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
            db.execSQL("CREATE TABLE content (ContentID TEXT, ContentType TEXT, Title TEXT, Attribution TEXT, ___PercentRead INTEGER, ReadStatus INTEGER, DateLastRead TEXT, ChapterIDBookmarked TEXT)")
            db.execSQL("INSERT INTO content VALUES ('file:///mnt/onboard/Libros/Rojo.kepub.epub','6','Amanecer rojo','Pierce Brown',43,1,'2026-11-15T21:10:05Z','OEBPS/c4.xhtml#kobo.17.1:204')")
            db.execSQL("INSERT INTO content VALUES ('file:///mnt/onboard/Libros/Dune.epub','6','Dune','Frank Herbert',10,1,'2026-11-15T21:10:05Z','OEBPS/c1.xhtml#point(/1/4/2/1:0)')")
            db.execSQL("INSERT INTO content VALUES ('0a1b2c3d-tienda','6','De la tienda','Alguien',5,1,'2026-11-15T21:10:05Z','text/part0004.html#kobo.2.1')")
        }
        val l = KoboCable.leeBase(f).associateBy { it.titulo }
        assertEquals("OEBPS/c4.xhtml#kobo.17.1:204", l["Amanecer rojo"]!!.marca)
        assertEquals("file:///mnt/onboard/Libros/Rojo.kepub.epub", l["Amanecer rojo"]!!.contentId)
        assertEquals("", l["Dune"]!!.marca)
        assertEquals("", l["De la tienda"]!!.marca)
    }

    /** Vincular: cada kepub con el libro de SesKobo del mismo título (y autor); las dudas, sin marcar; sin EPUB, sin pareja. */
    @Test fun parejasKepubEpub() {
        val libros = listOf(
            Libro("1", "Amanecer rojo", "Pierce Brown", uri = "content://x/1", archivo = "Amanecer rojo.epub"),
            Libro("2", "Poemas", "Gloria Fuertes", uri = "content://x/2", archivo = "Poemas.epub"),
            Libro("3", "Poemas", "Otro", uri = "content://x/3", archivo = "Poemas (2).epub"),
            Libro("4", "Dune", "Frank Herbert", uri = "content://x/4", archivo = "Dune.pdf"),
            Libro("5", "La sombra del viento", "", uri = "content://x/5", archivo = "sombra.epub"),
            Libro("6", "Sin archivo", "Nadie"),
        )
        val p = KoboCable.parejas(listOf(
            Triple("file:///mnt/onboard/a/Rojo.kepub.epub", "AMANECER ROJO", "Brown, Pierce"),
            Triple("file:///mnt/onboard/a/Poemas.kepub.epub", "Poemas", "Gloria Fuertes"),
            Triple("file:///mnt/onboard/a/P2.kepub.epub", "Poemas", ""),
            Triple("file:///mnt/onboard/a/Dune.kepub.epub", "Dune", "Frank Herbert"),
            Triple("file:///mnt/onboard/a/Sombra.kepub.epub", "La sombra del viento", "Carlos Ruiz Zafón"),
            Triple("file:///mnt/onboard/a/Nada.kepub.epub", "Sin archivo", "Nadie"),
        ), libros).associateBy { it.archivoKobo }
        assertEquals("1", p["Rojo.kepub.epub"]!!.libro!!.id); assertTrue(p["Rojo.kepub.epub"]!!.seguro)
        assertEquals("2", p["Poemas.kepub.epub"]!!.libro!!.id); assertTrue("hay dos «Poemas»: duda", !p["Poemas.kepub.epub"]!!.seguro)
        assertTrue(!p["P2.kepub.epub"]!!.seguro)
        assertEquals("un PDF no se vincula", null, p["Dune.kepub.epub"]!!.libro)
        assertEquals("5", p["Sombra.kepub.epub"]!!.libro!!.id); assertTrue("sin autor en SesKobo: vale", p["Sombra.kepub.epub"]!!.seguro)
        assertEquals("sin archivo en SesKobo, nada", null, p["Nada.kepub.epub"]!!.libro)
    }

    @Test fun sinLaColumnaDelTiempoTambien() {
        val l = KoboCable.leeBase(base(false))
        assertEquals(3, l.size)
        assertTrue(l.all { it.segundos == 0L })
    }

    @Test fun losArchivosParaElKoboVanSoloEnSesKobo() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        if (!BuildConfig.SESKOBO) {
            // La Seshat normal no lleva nada del Kobo (ni pesa más)
            assertTrue(runCatching { ctx.assets.open("kobo/seshat-kobo").close() }.isFailure)
            return
        }
        for (n in listOf("seshat-kobo", "seshat.sh", "nm-seshat", "Kobo.tgz", "LICENSE-NickelMenu", "LICENSE-NickelInkSync", "nickelinksync")) {
            val b = ctx.assets.open("kobo/$n").use { it.readBytes() }
            assertTrue("falta kobo/$n (kobo/construye.sh)", b.isNotEmpty())
        }
        // El programa es para el Kobo: ARM de 32 bits
        val elf = ctx.assets.open("kobo/seshat-kobo").use { it.readNBytes(20) }
        assertEquals(0x7f, elf[0].toInt()); assertEquals('E'.code, elf[1].toInt()); assertEquals(1, elf[4].toInt()); assertEquals(40, elf[18].toInt())
        // El instalador lleva los dos plugins
        val tgz = ctx.assets.open("kobo/Kobo.tgz").use { java.util.zip.GZIPInputStream(it).readBytes() }.decodeToString(throwOnInvalidSequence = false)
        assertTrue(tgz.contains("imageformats/libnm.so")); assertTrue(tgz.contains("imageformats/libnickelinksync.so"))
    }

    @Test fun archivosDeNickelInkSync() {
        val ini = KoboCable.configNis("https://kosync.eu", "ana", "5ebe2294ecd0e0f08eab7690d2a6ee69")
        for (l in listOf("server = https://kosync.eu", "user = ana", "key = 5ebe2294ecd0e0f08eab7690d2a6ee69", "confirm = never", "notify = errors")) assertTrue(l, ini.lines().contains(l))
        assertTrue("sin la contraseña", !ini.contains("password"))
        val ix = org.json.JSONObject(KoboCable.indiceNis(listOf(KoboCable.LibroNis("bb", "Dos", ""), KoboCable.LibroNis("aa", "Uno", "Ana"), KoboCable.LibroNis("aa", "Uno", "Ana"))))
        assertEquals(1, ix.getInt("format"))
        val b = ix.getJSONArray("books")
        assertEquals(2, b.length())
        assertEquals("aa", b.getJSONObject(0).getString("hash")); assertEquals("Ana", b.getJSONObject(0).getJSONArray("authors").getString(0))
        assertTrue(!b.getJSONObject(1).has("authors"))
        // Lo que ya había en el índice se lee de vuelta (para no perderlo al preparar otra vez)
        assertEquals(listOf("aa", "bb"), KoboCable.librosDeIndice(KoboCable.indiceNis(listOf(KoboCable.LibroNis("bb", "Dos", ""), KoboCable.LibroNis("aa", "Uno", "Ana")))).map { it.huella })
        assertTrue(KoboCable.librosDeIndice("basura").isEmpty())
        assertTrue(Regex("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ").matches(ix.getString("created")))
    }
}
