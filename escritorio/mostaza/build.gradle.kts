// Mostaza para Linux y Mac: tus películas y series de la cuenta, y verlas (con mpv). La misma cuenta que el Mostaza del móvil
// (titulos/<ref>, progreso/<vídeo>, tiempo/<ref>~<dispositivo>, ajustes/mostaza_*; tus addons y tu clave de TMDB en /claves; ver
// nube/ESQUEMA.md), con la misma lógica: el protocolo de los addons, el elector de fuentes, las pistas de verdad y las tres reglas.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val version = "1.1.0"

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// Los mandos: SDL 3 por LWJGL (solo su parte de mandos, como en Hiedra). Las bibliotecas nativas, las del sistema en que se empaqueta
// (Linux x86-64 en Linux; Apple Silicon en el Mac)
val lwjgl = "3.4.3"
val mac = System.getProperty("os.name").lowercase().contains("mac")
val nativas = if (mac) "natives-macos-arm64" else "natives-linux"

dependencies {
    implementation(project(":escritorio:familia"))
    // El motor de torrents de la familia (el mismo código que el del móvil, con libtorrent4j y su biblioteca de este sistema)
    implementation(project(":escritorio:torrent"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjgl"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-sdl")
    runtimeOnly("org.lwjgl:lwjgl::$nativas")
    runtimeOnly("org.lwjgl:lwjgl-sdl::$nativas")
    testImplementation("junit:junit:4.13.2")
    testImplementation(testFixtures(project(":nucleo")))
}

compose.desktop {
    application {
        mainClass = "com.gonzalo.mostaza.pc.MainKt"
        jvmArgs += listOf("-Dfamilia.version=$version")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Hathor"
            packageVersion = version
            description = "Tus películas y series, en la cuenta de la familia"
            vendor = "Gonzalo"
            // (jdk.unsupported: LWJGL lo necesita para los mandos, y libtorrent4j para su biblioteca nativa)
            modules("java.net.http", "jdk.crypto.ec", "jdk.unsupported")
            linux { packageName = "mostaza"; menuGroup = "AudioVideo"; shortcut = true; iconFile.set(project.file("icono/mostaza.png")) }
            macOS { bundleID = "com.gonzalo.mostaza"; iconFile.set(project.file("icono/mostaza.icns")) }
        }
    }
}

tasks.test {
    systemProperty("familia.emulador", System.getProperty("familia.emulador") ?: "http://127.0.0.1")
    systemProperty("familia.reglas", rootProject.file("nube/reglas.json").absolutePath)
    systemProperty("familia.version", version)
    System.getProperty("capturas")?.let { systemProperty("capturas", it); outputs.upToDateWhen { false } }
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
