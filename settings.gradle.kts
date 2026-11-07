pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        // JitPack: LibretroDroid y RadialGamePad (el jugador interno de Hiedra; GPL-3.0, del autor de Lemuroid)
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "Familia"
// Las apps de la familia, cada una con su paquete de siempre (se instalan encima de las que ya tienes); Mostaza, la de películas y series, y Cobre, la de la tele (IPTV), son las nuevas
// (con -PsoloEscritorio, sin las de Android: para compilar las de escritorio en un Mac o en Linux sin el SDK de Android)
val soloEscritorio = providers.gradleProperty("soloEscritorio").isPresent
include(":nucleo")
// Lo de Cobre que no es pantalla (listas M3U, Xtream Codes, la guía XMLTV): Kotlin puro, para el móvil y el ordenador
include(":iptv")
if (!soloEscritorio) {
    include(":familia")
    include(":hiedra", ":marfil", ":cobalto", ":azabache", ":mostaza", ":cobre")
    // El motor de torrents de Mostaza (libtorrent4j): ver un torrent mientras se baja
    include(":torrent")
}
// Las versiones de escritorio (Linux y Mac), con Compose Multiplatform: lo común y las cinco apps (ver docs/ESCRITORIO.md); y el motor
// de torrents de Mostaza para el ordenador (los mismos archivos que el del móvil)
include(":escritorio:json", ":escritorio:familia", ":escritorio:azabache", ":escritorio:cobalto", ":escritorio:marfil", ":escritorio:hiedra", ":escritorio:mostaza")
include(":escritorio:torrent")
