package com.gonzalo.ptah

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tus diseños viajan con tu cuenta (contra el emulador oficial de Firebase, con las reglas de verdad): `ajustes/ptah_disenos`, un campo por
 * diseño con su JSON; lo que cambias en otro móvil llega aquí (y lo que borras, se borra). Las fotos no se suben: ni el archivo, ni su
 * ruta. La apariencia, en `ajustes/ptah`; los colores de hace poco, en `ajustes/ptah_general`. Nada en `/claves`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CuentaTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var otro: Familia
    private var uid = ""

    private fun espera(que: String, segundos: Int = 10, cond: () -> Boolean) {
        val hasta = System.currentTimeMillis() + segundos * 1000L
        while (System.currentTimeMillis() < hasta) { shadowOf(Looper.getMainLooper()).idle(); if (cond()) return; Thread.sleep(50) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(que, cond())
    }

    private fun sincroniza() {
        assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otro.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Before fun prepara() {
        Emulador.limpia()
        uid = Emulador.cuenta("gonzalo")
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.configPruebas = Emulador.cfg
        EspejoCuenta.olvidaParaPruebas()
        Skin.cargar(ctx); Cuenta.cargar(ctx)
        kotlinx.coroutines.runBlocking { Cuenta.entrar("gonzalo", "secreto1") }
        otro = Emulador.dispositivo("gonzalo")
        otro.coleccion("ajustes"); LaFamilia.coleccion("ajustes")
        EspejoCuenta.inicia(ctx, SincroP.GRUPOS)
        val hasta = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < hasta) { LaFamilia.sincronizaYa(); if (EspejoCuenta.intenta(ctx)) break; Thread.sleep(100) }
        assertTrue(EspejoCuenta.intenta(ctx))
        sincroniza()
    }

    @After fun suelta() { LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = null; Disenos.fijos = null }

    @Test fun tusDisenosViajanConLaCuenta() {
        val reloj = RelojAgujas.nuevo().copy(nombre = "Esfera del salón").conEstilo(Estilo(fondo = TipoFondo.COLOR, colorFondo = "#12393A", tinta = "#F2E6CF", piezas = mapOf("segundero" to "#D98A3D")))
        Disenos.guarda(ctx, reloj)
        sincroniza()
        val n = otro.coleccion("ajustes").nodo("ptah_disenos")!!
        val enLaCuenta = Diseno.deJson(n.texto(reloj.id))!!
        assertEquals("Esfera del salón", enLaCuenta.nombre); assertEquals(reloj.estilo, enLaCuenta.estilo); assertEquals(reloj.opciones, enLaCuenta.opciones)

        // En tu otro móvil cambias el nombre y haces uno nuevo: llegan aquí
        val nota = TipoNota.nuevo().copy(nombre = "La compra", cambiado = System.currentTimeMillis())
        otro.coleccion("ajustes").pon("ptah_disenos", mapOf(reloj.id to enLaCuenta.copy(nombre = "Esfera de la cocina").aJson(), nota.id to nota.aJson()))
        sincroniza()
        espera("llega el cambio") { Disenos.de(ctx, reloj.id)?.nombre == "Esfera de la cocina" }
        espera("y el nuevo") { Disenos.todos(ctx).any { it.id == nota.id && it.nombre == "La compra" } }

        // Lo borras aquí: se borra allí
        Disenos.borra(ctx, nota.id)
        sincroniza()
        assertEquals("", otro.coleccion("ajustes").nodo("ptah_disenos")!!.texto(nota.id))
        assertNull(Disenos.de(ctx, nota.id))
        // Y lo que se borra allí, aquí
        otro.coleccion("ajustes").pon("ptah_disenos", mapOf(reloj.id to ""))
        sincroniza()
        espera("se borra aquí") { Disenos.de(ctx, reloj.id) == null }
    }

    @Test fun unoViejoQueLlegaDeLaCuentaNoPisaAlNuevo() {
        // (lo que pasaba en el OPPO, o lo que podría pasar: la cuenta, o un móvil que se quedó atrás, manda el diseño de antes de tus cambios)
        val viejo = RelojDigital.nuevo().copy(nombre = "Antes")
        Disenos.guarda(ctx, viejo)
        sincroniza()
        val antes = Disenos.de(ctx, viejo.id)!!
        Thread.sleep(5)
        Disenos.guarda(ctx, antes.copy(nombre = "Después").conEstilo(antes.estilo.copy(fondo = TipoFondo.COLOR, colorFondo = "#12393A")))
        // El otro móvil escribe ahora (más tarde, en la cuenta) la versión vieja
        otro.coleccion("ajustes").pon("ptah_disenos", mapOf(viejo.id to antes.aJson()))
        sincroniza(); Thread.sleep(300); sincroniza()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("aquí sigue lo nuevo", "Después", Disenos.de(ctx, viejo.id)!!.nombre)
        assertEquals("#12393A", Disenos.de(ctx, viejo.id)!!.estilo.colorFondo)
        espera("y la cuenta vuelve a tenerlo") { otro.sincroniza(); Diseno.deJson(otro.coleccion("ajustes").nodo("ptah_disenos")!!.texto(viejo.id))?.nombre == "Después" }
        // Uno de verdad más nuevo de otro móvil sí llega
        val deOtro = Disenos.de(ctx, viejo.id)!!.copy(nombre = "Del otro móvil", cambiado = System.currentTimeMillis() + 1000)
        otro.coleccion("ajustes").pon("ptah_disenos", mapOf(viejo.id to deOtro.aJson()))
        sincroniza()
        espera("llega") { Disenos.de(ctx, viejo.id)?.nombre == "Del otro móvil" }
    }

    @Test fun lasFotosNoSeSuben() {
        val foto = Foto.nuevo().copy(nombre = "La playa").con("pie", "El verano en Cádiz")
        Fotos.guarda(ctx, foto.id, DatosDeMentira.foto)
        assertTrue(Fotos.hay(ctx, foto.id))
        Disenos.guarda(ctx, foto)
        sincroniza()
        val aj = otro.coleccion("ajustes")
        val texto = aj.nodo("ptah_disenos")!!.texto(foto.id)
        // El diseño sí (con su pie), la foto no: ni su archivo, ni su ruta, ni sus bytes
        assertEquals("El verano en Cádiz", Diseno.deJson(texto)!!.texto("pie"))
        assertFalse(texto.contains(".jpg")); assertFalse(texto.contains("fotos")); assertFalse(texto.contains(ctx.filesDir.path))
        assertTrue("el diseño es pequeño (sin la foto dentro)", texto.length < 2_000)
        assertTrue(Fotos.archivo(ctx, foto.id).length() > 2_000)
        // Ni en ningún otro sitio de la cuenta: todo lo de Ptah va en `ajustes`, y nada pesa como una foto
        val todo = Emulador.pide("GET", "${Emulador.db}/users/$uid.json?${Emulador.ns}", admin = true).second
        assertFalse(todo.contains(".jpg")); assertFalse(todo.contains("/fotos/"))
        assertTrue("toda la cuenta es pequeña", todo.length < 20_000)
        val claves = Emulador.pide("GET", "${Emulador.db}/claves/$uid.json?${Emulador.ns}", admin = true).second
        assertFalse("nada de Ptah en /claves", claves.contains("ptah"))
        // En otro móvil, el diseño llega sin foto: «Elige la foto»
        Fotos.borra(ctx, foto.id)
        assertNull(DatosAndroid(ctx).foto(foto.id))
        // Y al borrar el diseño se borra su foto de este móvil
        Fotos.guarda(ctx, foto.id, DatosDeMentira.foto)
        Disenos.borra(ctx, foto.id)
        assertFalse(Fotos.hay(ctx, foto.id))
    }

    @Test fun laAparienciaYLosColoresDeHacePoco() {
        Skin.accentId = "verde"; Skin.guardar(ctx)
        Recientes.pon(ctx, "#12393A"); Recientes.pon(ctx, "#d98a3d"); Recientes.pon(ctx, "no es un color")
        sincroniza()
        val aj = otro.coleccion("ajustes")
        assertEquals("verde", (aj.nodo("ptah")!!.mapa("skin")["skin"] as Map<*, *>)["acento"])
        assertEquals("#D98A3D,#12393A", aj.nodo("ptah_general")!!.texto("recientes"))
        assertEquals(listOf("#D98A3D", "#12393A"), Recientes.lista(ctx))
        // «Actualizar sola», en `ajustes/ptah_actualizador`
        Actualizador.ponActivo(ctx, false)
        sincroniza()
        assertEquals(false, aj.nodo("ptah_actualizador")!!.si("on"))
    }
}
