package com.gonzalo.marfil

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
/** Las secciones: los filtros de orden de la cabecera responden también en las páginas que no son la primera. */
class SeccionesTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun toca(t: String) { val n = rule.onAllNodesWithText(t); n[n.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.left >= 0f && it.boundsInRoot.right <= 1080f }].performClick(); rule.waitForIdle() }
    @Test fun elOrdenEnLeidos() {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Biblioteca.cargar(ctx); Biblioteca.libros.clear(); Sagas.cargar(ctx)
        Biblioteca.fijaParaPruebas(Biblioteca.libros + Libro("a", "Dune", "Frank Herbert", estado = Estado.LEIDO, anadido = 1))
        Biblioteca.fijaParaPruebas(Biblioteca.libros + Libro("b", "Ubik", "Philip K. Dick", estado = Estado.LEIDO, anadido = 2))
        rule.setContent { MarfilTheme { Surface(Modifier.fillMaxSize(), color = Paper) { PaginaBiblioteca(Estante.Leidos, {}, alAbrir = {}, alLeer = {}, alAbrirSaga = {}, alAnadir = {}) } } }
        // El orden se despliega al tocar «Orden: …» y se recuerda
        rule.onNodeWithText("Orden:", substring = true).performClick(); rule.waitForIdle()
        toca("Autor A-Z")
        assertEquals(OrdenLibros.AUTOR, OrdenGuardado.de(ctx, Estado.LEIDO))
    }

    /** Cada estante, solo con los órdenes que tienen sentido (en Leídos no hay «Última lectura», que era la fecha de fin repetida). */
    @Test fun losOrdenesDeCadaEstante() {
        assertEquals(listOf(OrdenLibros.TERMINADO, OrdenLibros.TITULO, OrdenLibros.AUTOR, OrdenLibros.PAGINAS), ordenesDe(Estado.LEIDO))
        assertFalse(OrdenLibros.TERMINADO in ordenesDe(Estado.PENDIENTE) || OrdenLibros.RECIENTES in ordenesDe(Estado.PENDIENTE) || OrdenLibros.AVANCE in ordenesDe(Estado.PENDIENTE))
        assertTrue(OrdenLibros.AVANCE in ordenesDe(Estado.LEYENDO))
        Estado.entries.forEach { e -> assertEquals(ordenesDe(e).first(), ordenInicial(e)); assertEquals(ordenesDe(e).size, ordenesDe(e).distinct().size) }
        // Un orden guardado que ya no vale en ese estante vuelve al de serie
        OrdenGuardado.pon(ctx, Estado.LEIDO, OrdenLibros.RECIENTES)
        assertEquals(OrdenLibros.TERMINADO, OrdenGuardado.de(ctx, Estado.LEIDO))
    }

    /** Dirección D: la barra de abajo lleva a Hoy, Biblioteca y Tú; los estantes de antes son filtros de la Biblioteca (no pantallas aparte). */
    @Test fun laBarraYLosFiltros() {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Biblioteca.cargar(ctx); Biblioteca.libros.clear(); Sagas.cargar(ctx)
        Biblioteca.fijaParaPruebas(listOf(Libro("a", "Dune", "Frank Herbert", estado = Estado.PENDIENTE, anadido = 1), Libro("b", "Ubik", "Philip K. Dick", estado = Estado.LEYENDO, anadido = 2, ultima = 5, uri = "file:///x", archivo = "b.epub")))
        rule.setContent { MarfilTheme { Surface(Modifier.fillMaxSize(), color = Paper) { MarfilApp() } } }
        // Se abre en Hoy, con lo que lees ahora y su botón
        rule.onNodeWithText("Seguir leyendo").assertExists()
        rule.onNodeWithText("Lo siguiente").assertExists()
        rule.onNodeWithContentDescription("Biblioteca").performClick(); rule.waitForIdle()
        // En la biblioteca, el filtro de serie es «Leyendo»: sale Ubik y no Dune; con «Pendientes», al revés
        rule.onNodeWithContentDescription("Ubik").assertExists(); rule.onNodeWithContentDescription("Dune").assertDoesNotExist()
        toca("Pendientes")
        rule.onNodeWithContentDescription("Dune").assertExists(); rule.onNodeWithContentDescription("Ubik").assertDoesNotExist()
        // El buscador encuentra en todos los estantes
        rule.onNodeWithContentDescription("Tú").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Reto", substring = true).assertExists()
    }

    /** «Lo siguiente»: lo que pones el siguiente va delante de los demás pendientes; quitarlo lo devuelve a su sitio. */
    @Test fun laCola() {
        Cola.ids.clear()
        val a = Libro("a", "Dune", "Frank Herbert", estado = Estado.PENDIENTE); val b = Libro("b", "Ubik", "Philip K. Dick", estado = Estado.PENDIENTE); val c = Libro("c", "Solaris", "Stanislaw Lem", estado = Estado.PENDIENTE)
        assertEquals(listOf("a", "b", "c"), Cola.ordena(listOf(a, b, c)).map { it.id })
        Cola.alterna(ctx, c.clave); Cola.alterna(ctx, b.clave)
        assertEquals(listOf("b", "c", "a"), Cola.ordena(listOf(a, b, c)).map { it.id })
        Cola.alterna(ctx, b.clave)
        assertEquals(listOf("c", "a", "b"), Cola.ordena(listOf(a, b, c)).map { it.id })
        // Se recuerda
        Cola.ids.clear(); Cola.cargar(ctx); assertTrue(Cola.esta(c.clave)); assertFalse(Cola.esta(b.clave))
        Cola.alterna(ctx, c.clave)
    }
}
