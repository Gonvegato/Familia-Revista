package com.gonzalo.marfil

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
        rule.setContent { MarfilTheme { Surface(Modifier.fillMaxSize(), color = Paper) { PantPrincipal(pagina = Destino.Leidos.ordinal, alPagina = {}, alAbrir = {}, alLeer = {}, alAbrirSaga = {}, alAnadir = {}, alAjustes = {}) } } }
        toca("AUTOR")
        assertEquals(OrdenLibros.AUTOR, OrdenGuardado.de(ctx, Estado.LEIDO))
    }

    /** Una sola fila de pestañas: la portada es la primera página y cada sección es una página más (sin submenús ni otra cabecera). */
    @Test fun portadaYSeccionesSonPaginas() {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Biblioteca.cargar(ctx); Biblioteca.libros.clear(); Sagas.cargar(ctx)
        Biblioteca.fijaParaPruebas(listOf(Libro("a", "Dune", "Frank Herbert", estado = Estado.PENDIENTE, anadido = 1), Libro("b", "Ubik", "Philip K. Dick", estado = Estado.LEYENDO, anadido = 2, ultima = 5)))
        var pagina by androidx.compose.runtime.mutableIntStateOf(0)
        rule.setContent { MarfilTheme { Surface(Modifier.fillMaxSize(), color = Paper) { PantPrincipal(pagina = pagina, alPagina = { pagina = it }, alAbrir = {}, alLeer = {}, alAbrirSaga = {}, alAnadir = {}, alAjustes = {}) } } }
        // Se abre en la portada; la fila de pestañas tiene «PORTADA» y una sola cabecera «SESHAT.»
        assertEquals(1, rule.onAllNodesWithText("PORTADA").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("SESHAT.").fetchSemanticsNodes().size)
        toca("PENDIENTES")
        assertEquals(Destino.Pendientes.ordinal, pagina)
        // Sigue habiendo una sola cabecera y una sola fila de pestañas (no se abre otra pantalla)
        assertEquals(1, rule.onAllNodesWithText("SESHAT.").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("LEYENDO").fetchSemanticsNodes().size)
        // Lo de fuera (Atrás, un widget) mueve la página
        pagina = 0; rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithText("PORTADA").fetchSemanticsNodes().size)
        toca("LEYENDO"); assertEquals(Destino.Leyendo.ordinal, pagina)
    }
}
