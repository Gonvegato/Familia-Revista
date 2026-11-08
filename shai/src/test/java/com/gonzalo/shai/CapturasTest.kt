package com.gonzalo.shai

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de las pantallas de Shai (solo con -Proborazzi.test.record=true), con una carrera de ejemplo montada con la API de mentira: tus
 * carreras, cada pestaña de una carrera, meter un partido (rápido y completo), empezar una carrera y los ajustes; con la letra normal y con
 * la grande (130 %). En un móvil alto (411 × 2000 dp), para ver cada pantalla entera. En -Dcapturas (o build/outputs/roborazzi): `shai-<tema>-<pantalla>.png`.
 */
abstract class CapturasBase {
    abstract val tema: String
    open val largas: Boolean = false
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val dir: String get() = System.getProperty("capturas")?.takeIf { it.isNotBlank() } ?: "build/outputs/roborazzi"

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(300); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$dir/shai-$tema-$nombre.png")
    }

    @Test fun pantallas() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Cuenta.cargar(ctx); Skin.cargar(ctx)
        Ejemplo.prepara()
        Cuenta.dentroFijo = true
        // Con las plantillas del modo prueba (lo más parecido a lo que escribe Claude), sin el aviso del modo prueba
        Motor.claude = Claude({ ClaveApi.lee() }, TransporteDePrueba(kotlin.random.Random(11)))
        Motor.separacion = 0
        val (u0, _) = Motor.crea(Ejemplo.universo().copy(trayectoria = "Canterano del Betis desde los nueve años; debutó el año pasado en Copa. Su abuelo jugó en el filial.",
            nivel = Nivel.PROMESA.name, fuera = Fuera.INICIAL + (Fuera.FAMA to 24)))
        listOf(Ejemplo.partido(2, 2, "Athletic Club"), Ejemplo.partido(1, 2, "Valencia CF"), Ejemplo.partido(3, 1, "Sevilla FC")).forEach { p ->
            Motor.partido(u0.id, p); Motor.miraLotes(); Motor.revela(forzar = true)
        }
        Carreras.entradasDe(u0.id).firstOrNull { it.decision?.abierta == true }?.let { Motor.decide(it.id, it.decision!!.opciones.first()) }
        // El club, identificado (lo de una búsqueda de ejemplo: las personas, sin nombre de verdad) y confirmado
        val betis = InfoClub("Real Betis Balompié", "Los verdiblancos", "Sevilla", "España", "Benito Villamarín", 60_721, "LaLiga EA Sports", "Primera",
            "Nombre del entrenador", "Nombre del presidente", "Verde y blanco", listOf("Sevilla FC"), "Una Liga, tres Copas del Rey.",
            "Ejemplo de captura: aquí va su momento deportivo y económico, tal cual lo encuentra la búsqueda.", "Ejemplo de captura: aquí va cómo es su afición.",
            listOf("Un diario local"), "", listOf("Real Betis Balompié - Wikipedia" to "https://es.wikipedia.org/wiki/Real_Betis_Balompi%C3%A9", "Web oficial" to "https://www.realbetisbalompie.es/"),
            "2026-11-08", "Real Betis", coste = 0.07, busquedas = 2)
        Motor.confirmaClub(u0.id, betis)
        val u = Carreras.de(u0.id)!!
        Gasto.apunta(Claude.SONNET, 3.42); Gasto.apunta(Claude.HAIKU, 0.37)
        // (otra carrera, de míster, para la portada)
        Carreras.guarda(Universo(Universo.nuevoId(), Rol.MISTER, Personaje("Marcelo Ruiz", edad = 44, nacionalidad = "Argentina"), "Real Valladolid CF", "LaLiga Hypermotion", "2026/27", 9_000, dinero = 1_200_000))
        var cual by mutableIntStateOf(0)
        var otra by androidx.compose.runtime.mutableStateOf(u.id)
        var chatAbierto by androidx.compose.runtime.mutableStateOf<String?>(null)
        rule.setContent {
            ShaiTheme {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    key(cual) {
                        when (cual) {
                            0 -> PantPortada({}, {}, {})
                            in 10..16 -> PantCarrera(otra, {}, {}, {}, inicial = cual - 10)
                            17 -> PantCarrera(otra, {}, {}, {}, inicial = 0, articuloAbierto = true)
                            18 -> PaginaRevista { CerrarTemporada(Carreras.de(otra)!!, preguntando0 = true) }
                            20 -> PantPartido(u.id, {}, {})
                            21 -> PantPartido(u.id, {}, {}, comoInicial = 1)
                            30 -> PantNueva({}, {})
                            40 -> PantAjustes({}, inicial = 2)
                            41 -> PantAjustes({}, inicial = 0)
                            in 60..61 -> PantCarrera(otra, {}, {}, {}, inicial = 0, articuloAbierto = cual == 61)
                            63 -> PaginaRevista { Carreras.de(otra)?.let { TusRivales(it) } }
                            64 -> PantCarrera(otra, {}, {}, {}, inicial = 1)
                            in 70..71 -> PantCarrera(otra, {}, {}, {}, chatInicial = chatAbierto)
                            80 -> PaginaRevista { ConfirmaClub(betis, false, {}, {}) }
                            62 -> PaginaRevista { Carreras.de(otra)?.let { RecordsHistoricos(it, Carreras.partidos(otra).map { x -> x.second }) } }
                        }
                    }
                }
            }
        }
        foto("01-portada")
        PESTANAS.forEachIndexed { i, n -> cual = 10 + i; foto("1$i-carrera-${n.lowercase()}") }
        cual = 80; foto("80-club-confirmacion")
        // (en un móvil larguísimo: Cifras y Perfil enteros, y nada más)
        if (largas) { cual = 14; foto("14-cifras-entera"); cual = 16; foto("16-perfil-entero"); Cuenta.dentroFijo = null; ClaveApi.fija = null; Gasto.mesFijo = null; return }
        cual = 17; foto("17-carrera-noticia")
        // Un chat con una decisión abierta y uno sin ella (un grupo, si lo hay)
        val convs = conversaciones(Carreras.entradasDe(u.id))
        chatAbierto = convs.firstOrNull { it.abierta != null }?.nombre ?: convs.first().nombre
        cual = 70; foto("70-chat-decision")
        chatAbierto = (convs.firstOrNull { it.grupo } ?: convs.firstOrNull { it.abierta == null } ?: convs.last()).nombre
        cual = 71; foto("71-chat")
        cual = 0
        cual = 18; foto("18-cerrar-temporada")
        cual = 20; foto("20-partido-rapido")
        cual = 21; foto("21-partido-completo")
        cual = 30; foto("30-nueva-carrera")
        cual = 40; foto("40-ajustes-ia")
        cual = 41; foto("41-ajustes-apariencia")
        // Un récord histórico (el goleador más joven de la Champions, con 17 años y 30 días) y la sección de récords reales
        val dia = java.time.LocalDate.of(2026, 10, 21)
        val (rc, _) = Motor.crea(Ejemplo.universo().let { it.copy(personaje = it.personaje.copy(edad = 17, nacimiento = Fechas.iso(dia.minusDays(30).minusYears(17)))) })
        Motor.partido(rc.id, Partido("2026/27", "LaLiga EA Sports", "Girona FC", true, 2, 0, 90, 1, 0, 7.6, fecha = Fechas.iso(dia.minusDays(4))))
        Motor.partido(rc.id, Partido("2026/27", "UEFA Champions League", "Inter de Milán", true, 2, 1, 90, 1, 1, 8.8, fecha = Fechas.iso(dia)))
        otra = rc.id
        cual = 60; foto("60-record-portada")
        cual = 61; foto("61-record-noticia")
        cual = 62; foto("62-records-historicos")
        // Los rivales: uno que surge solo (dos duelos con el Sevilla y te marcan), uno inventado más y uno real (solo deportivo)
        val (rv, _) = Motor.crea(Ejemplo.universo())
        Carreras.guarda(Rivales.anade(Carreras.de(rv.id)!!, "Bruno Lafuente", "Real Madrid", real = false, origen = "Se conocen desde la selección sub-17: nunca se han soportado"))
        Motor.partido(rv.id, Partido("2026/27", "LaLiga", "Sevilla FC", true, 1, 0, 90, 1, 0, 7.4))
        Motor.partido(rv.id, Partido("2026/27", "LaLiga", "Sevilla FC", false, 1, 2, 90, 0, 0, 6.1))
        Carreras.guarda(Rivales.anade(Carreras.de(rv.id)!!, "Un futbolista de verdad", "Real Madrid", real = true))
        Motor.partido(rv.id, Partido("2026/27", "LaLiga", "Real Madrid", true, 2, 1, 90, 1, 1, 8.2))
        otra = rv.id
        cual = 63; foto("63-rivales")
        cual = 64; foto("64-rivales-red")
        // La cuenta atrás: le quedan 20 días para el récord de Ansu Fati (goleador más joven de la Champions)
        val dct = java.time.LocalDate.of(2026, 10, 1)
        val (ct, _) = Motor.crea(Ejemplo.universo().let { it.copy(personaje = it.personaje.copy(edad = 17, nacimiento = Fechas.iso(dct.minusDays(20).minusYears(17)))) })
        Motor.partido(ct.id, Partido("2026/27", "LaLiga EA Sports", "Girona FC", true, 1, 0, 90, 0, 0, 7.0, fecha = Fechas.iso(dct)))
        otra = ct.id
        cual = 10; foto("65-cuenta-atras-inicio")
        cual = 11; foto("66-cuenta-atras-red")
        otra = u.id
        // El cierre de la temporada
        Motor.cierraTemporada(u.id)
        cual = 10; foto("19-carrera-fin-de-temporada")
        // El modo prueba (sin IA): una carrera nueva con sus plantillas de ejemplo
        Prueba.pon(ctx, true)
        val (p, _) = Motor.crea(Ejemplo.universo())
        Motor.partido(p.id, Ejemplo.partido(1, 0, "Valencia CF")); Motor.partido(p.id, Ejemplo.partido(0, 2, "Girona FC"))
        cual = 0; foto("50-prueba-portada")
        otra = p.id
        cual = 10; foto("51-prueba-carrera")
        cual = 11; foto("52-prueba-red")
        cual = 12; foto("53-prueba-mensajes")
        cual = 40; foto("54-prueba-ajustes-ia")
        Prueba.vacia(); Prueba.pon(ctx, false); Motor.separacion = 6 * 3600_000L
        Cuenta.dentroFijo = null; ClaveApi.fija = null; Gasto.mesFijo = null
    }
}

private const val ALTO = "w411dp-h2000dp-xhdpi"

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = ALTO)
class CapturasNormalTest : CapturasBase() { override val tema = "normal" }

/** Con la letra grande (el tamaño de texto del móvil al 130 %): nada cortado. */
@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = ALTO, fontScale = 1.3f)
class CapturasLetraGrandeTest : CapturasBase() { override val tema = "letra-grande" }

/** Cifras y Perfil enteros (un móvil de 411 × 6000 dp), con la letra normal y con la grande. */
@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = "w411dp-h6000dp-xhdpi")
class CapturasLargasTest : CapturasBase() { override val tema = "larga"; override val largas = true }

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = "w411dp-h6000dp-xhdpi", fontScale = 1.3f)
class CapturasLargasLetraGrandeTest : CapturasBase() { override val tema = "larga-letra-grande"; override val largas = true }
