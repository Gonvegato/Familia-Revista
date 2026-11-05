pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        // JitPack: LibretroDroid y RadialGamePad (el jugador interno de Hiedra; GPL-3.0, del autor de Lemuroid)
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "Familia"
// Las cuatro apps de la familia, cada una con su paquete de siempre (se instalan encima de las que ya tienes)
include(":nucleo", ":familia")
include(":hiedra", ":marfil", ":cobalto", ":azabache")
