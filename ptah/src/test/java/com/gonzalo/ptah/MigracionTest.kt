package com.gonzalo.ptah

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
 * La migración única: los diseños (gana el más nuevo), los colores recientes, «actualizar sola» y el token de kDrive que la versión de antes
 * tenía en la cuenta se traen una vez a este móvil, y ya no se vuelve a tocar la cuenta. La cuenta, contra el emulador de Firebase.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigracionTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun prepara() {
        Secretos.enMemoria = HashMap()
        Migracion.aviso = ""
        Disenos.fijos = null
        listOf("widgets_migracion", "nube", Disenos.GRUPO.archivo, Recientes.GRUPO.archivo, "ptah_actualizador").forEach { ctx.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }
    @After fun suelta() { Migracion.fuente = null; Secretos.enMemoria = null }

    @Test fun sinCuentaDeAntesNoHayNadaQueTraer() {
        Migracion.fuente = { null }
        ctx.getSharedPreferences("nube", Context.MODE_PRIVATE).edit().putString("token", "tok-local").putString("drive", "123").commit()
        assertEquals(0, Migracion.haz(ctx))
        assertTrue(Migracion.hecha(ctx)); assertEquals("", Migracion.aviso)
        assertEquals("tok-local", Secretos.lee("kdrive_token")); assertEquals("123", KDrive.drive(ctx))
    }

    @Test fun traeLosDisenosElTokenYLoDemasUnaVez() {
        Emulador.exige()
        Emulador.limpia()
        Emulador.cuenta("gonzalo")
        val d1 = Plantillas.TODAS[0].first.copy(id = "d0000000001", nombre = "Mi reloj", cambiado = 2000)
        val d2 = Plantillas.TODAS[1].first.copy(id = "d0000000002", nombre = "Mi columna", cambiado = 2000)
        val antes = Emulador.dispositivo("gonzalo")
        antes.coleccion("ajustes").pon(Disenos.GRUPO.nodo, mapOf(d1.id to d1.aJson(), d2.id to d2.aJson()))
        antes.coleccion("ajustes").pon(Recientes.GRUPO.nodo, mapOf("recientes" to "#FF0000,#00FF00"))
        antes.coleccion("ajustes").pon("ptah_actualizador", mapOf("on" to false))
        antes.ponClave("kdrive_token", "tok-cuenta"); antes.ponClave("kdrive_id", "4481105")
        assertTrue(antes.sincroniza().ok)
        // Este móvil ya tenía el segundo, más nuevo: se queda el de aquí
        val d2aqui = d2.copy(nombre = "Mi columna (aquí)", cambiado = 9000)
        EspejoCuenta.base(ctx, Disenos.GRUPO).edit().putString(d2.id, d2aqui.aJson()).commit()
        val aqui = Emulador.dispositivo("gonzalo")
        Migracion.fuente = { aqui }

        assertEquals(1, Migracion.haz(ctx))
        assertEquals("Mi reloj", Disenos.de(ctx, d1.id)!!.nombre)
        assertEquals("Mi columna (aquí)", Disenos.de(ctx, d2.id)!!.nombre)
        assertTrue(EspejoCuenta.base(ctx, Recientes.GRUPO).getString("recientes", "")!!.contains("#FF0000"))
        assertFalse(Actualizador.activo(ctx))
        assertEquals("tok-cuenta", KDrive.token(ctx)); assertEquals("4481105", KDrive.drive(ctx))
        assertEquals("He traído 1 diseño de tu cuenta.", Migracion.aviso)
        // Una sola vez
        assertEquals(0, Migracion.haz(ctx))
    }
}
