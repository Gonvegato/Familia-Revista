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
// (con -PsoloEscritorio, sin las de Android: para compilar las de escritorio en un Mac o en Linux sin el SDK de Android)
val soloEscritorio = providers.gradleProperty("soloEscritorio").isPresent
include(":nucleo")
if (!soloEscritorio) {
    include(":familia")
    include(":hiedra", ":marfil", ":cobalto", ":azabache")
    include(":torrent")
}
// Las versiones de escritorio (Linux y Mac), con Compose Multiplatform: lo común y las cuatro apps (ver docs/ESCRITORIO.md)
include(":escritorio:json", ":escritorio:familia", ":escritorio:azabache", ":escritorio:cobalto", ":escritorio:marfil", ":escritorio:hiedra")
