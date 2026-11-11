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
    // Ptah, la de los widgets (solo Android: no tiene versión de escritorio)
    include(":ptah")
    // Shai, la de tu carrera de EA Sports FC (solo Android; de la familia, pero sin nada en Jonsu)
    include(":shai")
    // Ammit, la pantalla de inicio que encendida solo deja pasar a la familia (solo Android)
    include(":ammit")
    // El motor de torrents de Mostaza (libtorrent4j): ver un torrent mientras se baja
    include(":torrent")
}
// Las versiones de escritorio (Linux y Mac), con Compose Multiplatform: lo común y las seis apps (ver docs/ESCRITORIO.md); y el motor
// de torrents de Mostaza para el ordenador (los mismos archivos que el del móvil). Cobre del ordenador usa `iptv` tal cual (va siempre, también
// con -PsoloEscritorio: es Kotlin puro)
include(":escritorio:json", ":escritorio:familia", ":escritorio:azabache", ":escritorio:cobalto", ":escritorio:marfil", ":escritorio:hiedra", ":escritorio:mostaza", ":escritorio:cobre")
include(":escritorio:torrent")
