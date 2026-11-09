package com.gonzalo.shai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.nucleo.Emulador
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La migración única: lo que la versión de antes tenía en la cuenta (las carreras, lo que pasa en ellas, el gasto, la clave de la API y el
 * token de kDrive) se trae una vez a este móvil y ya no se vuelve a tocar la cuenta. La cuenta, contra el emulador oficial de Firebase.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigracionTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun prepara() {
        Secretos.enMemoria = HashMap()
        Carreras.real = AlmacenMemoria(); Carreras.almacen = Carreras.real
        ctx.getSharedPreferences("fcfeed_migracion", Context.MODE_PRIVATE).edit().clear().apply()
        ctx.getSharedPreferences("nube", Context.MODE_PRIVATE).edit().clear().apply()
        Migracion.aviso = ""
    }
    @After fun suelta() { Migracion.fuente = null; Secretos.enMemoria = null; Carreras.almacen = AlmacenMemoria(); Carreras.real = Carreras.almacen }

    @Test fun sinCuentaDeAntesNoHayNadaQueTraer() {
        Migracion.fuente = { null }
        // (el token de kDrive que había solo en este móvil, sin cifrar, sí pasa a la caja)
        ctx.getSharedPreferences("nube", Context.MODE_PRIVATE).edit().putString("token", "tok-local").putString("drive", "123").apply()
        assertEquals(0, Migracion.haz(ctx))
        assertTrue(Migracion.hecha(ctx))
        assertEquals("", Migracion.aviso)
        assertEquals("tok-local", Secretos.lee("kdrive_token")); assertEquals("123", KDrive.drive(ctx))
        assertNull(ctx.getSharedPreferences("nube", Context.MODE_PRIVATE).getString("token", null))
    }

    @Test fun traeLasCarrerasLaClaveYElTokenUnaVez() {
        Emulador.exige()
        Emulador.limpia()
        Emulador.cuenta("gonzalo")
        // El otro móvil (o la versión de antes en este) dejó en la cuenta dos carreras, una entrada, el gasto y las claves
        val antes = Emulador.dispositivo("gonzalo")
        val u1 = Ejemplo.universo(); val u2 = Ejemplo.universo().copy(club = "Sevilla FC")
        val col = antes.coleccion(Carreras.COLECCION)
        col.pon(u1.id, mapOf("o" to u1.aJson())); col.pon(u2.id, mapOf("o" to u2.aJson()))
        col.pon("e0000000001", mapOf("un" to u1.id, "o" to "{}"))
        col.pon("gasto", mapOf("202611_shai-x_s" to 1_500_000L))
        antes.ponClave(ClaveApi.CLAVE, "sk-ant-api03-DE-ANTES-1234")
        antes.ponClave("kdrive_token", "tok-cuenta"); antes.ponClave("kdrive_id", "4481105")
        assertTrue(antes.sincroniza().ok)
        // La versión de antes en este móvil: su sesión, con su copia (aún vacía) de la cuenta
        val aqui = Emulador.dispositivo("gonzalo")
        Migracion.fuente = { aqui }
        Gasto.mesFijo = java.time.YearMonth.of(2026, 11)

        assertEquals(2, Migracion.haz(ctx))
        assertNotNull(Carreras.de(u1.id)); assertEquals("Sevilla FC", Carreras.de(u2.id)!!.club)
        assertNotNull(Carreras.real.nodo("e0000000001"))
        assertEquals(1.5, Gasto.mes(), 0.001)
        assertEquals("sk-ant-api03-DE-ANTES-1234", ClaveApi.lee())
        assertEquals("tok-cuenta", KDrive.token(ctx)); assertEquals("4481105", KDrive.drive(ctx))
        assertTrue(Migracion.aviso, Migracion.aviso.startsWith("He traído 2 carreras de tu cuenta"))

        // Una sola vez: lo que cambie después en la cuenta ya no llega
        col.pon(Ejemplo.universo().id, mapOf("o" to Ejemplo.universo().aJson())); assertTrue(antes.sincroniza().ok)
        assertEquals(0, Migracion.haz(ctx))
        assertEquals(2, Carreras.todas().size)
        Gasto.mesFijo = null
    }
}
