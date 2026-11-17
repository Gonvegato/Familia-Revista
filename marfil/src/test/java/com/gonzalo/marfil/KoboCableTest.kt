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

    @Test fun sinLaColumnaDelTiempoTambien() {
        val l = KoboCable.leeBase(base(false))
        assertEquals(3, l.size)
        assertTrue(l.all { it.segundos == 0L })
    }

    @Test fun losArchivosParaElKoboVanEnLaApp() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        for (n in listOf("seshat-kobo", "seshat.sh", "nm-seshat", "NickelMenu-Kobo.tgz", "LICENSE-NickelMenu")) {
            val b = ctx.assets.open("kobo/$n").use { it.readBytes() }
            assertTrue("falta kobo/$n (kobo/construye.sh)", b.isNotEmpty())
        }
        // El programa es para el Kobo: ARM de 32 bits
        val elf = ctx.assets.open("kobo/seshat-kobo").use { it.readNBytes(20) }
        assertEquals(0x7f, elf[0].toInt()); assertEquals('E'.code, elf[1].toInt()); assertEquals(1, elf[4].toInt()); assertEquals(40, elf[18].toInt())
    }
}
