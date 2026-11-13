// Cobre para Linux y Mac: la tele de la familia (IPTV), con tus listas M3U y tus logins de Xtream Codes, su guía y su videoclub, y verlo con
// mpv. La misma cuenta que Cobre del móvil (cobre_favoritos, cobre_grupos, cobre_estado, cobre_progreso, ajustes/cobre*; tus listas solo en
// /claves; ver nube/ESQUEMA.md) y lo mismo que no es pantalla: el módulo `iptv` tal cual (listas, Xtream, la guía, lo que va a la cuenta).
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val version = "1.2.0"

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// Los mandos: SDL 3 por LWJGL (solo su parte de mandos, como en Hiedra y Mostaza). Las bibliotecas nativas, las del sistema en que se
// empaqueta (Linux x86-64 en Linux; Apple Silicon en el Mac)
val lwjgl = "3.4.3"
val mac = System.getProperty("os.name").lowercase().contains("mac")
val nativas = if (mac) "natives-macos-arm64" else "natives-linux"

dependencies {
    implementation(project(":escritorio:familia"))
    // Lo de Cobre que no es pantalla: el mismo módulo que usa Cobre del móvil
    implementation(project(":iptv"))
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
        mainClass = "com.gonzalo.cobre.pc.MainKt"
        // (en la app empaquetada del Mac la huella SHA-1 de la biblioteca nativa no coincide con la del jar; LWJGL solo lo avisa y sigue: se calla el aviso)
        jvmArgs += listOf("-Dfamilia.version=$version", "-Dorg.lwjgl.util.NoHashChecks=true")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Nut"
            packageVersion = version
            description = "La tele de la familia (IPTV): tus listas, tus canales y su guía"
            vendor = "Gonzalo"
            // (jdk.unsupported: LWJGL lo necesita para los mandos)
            modules("java.net.http", "jdk.crypto.ec", "jdk.unsupported")
            linux { packageName = "cobre"; menuGroup = "AudioVideo"; shortcut = true; iconFile.set(project.file("icono/cobre.png")) }
            macOS { bundleID = "com.gonzalo.cobre"; iconFile.set(project.file("icono/cobre.icns")) }
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
