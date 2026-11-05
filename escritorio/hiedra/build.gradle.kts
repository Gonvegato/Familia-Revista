// Hiedra para Linux y Mac: tus juegos de la cuenta, y jugar a los de este ordenador (Steam y tus programas). La misma cuenta que el
// Hiedra del móvil (juegos/<id>, tiempo/<id>~<fuente>; ver nube/ESQUEMA.md), y hace de «Tu PC» para el móvil (suite/).
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val version = "1.0.1"

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":escritorio:familia"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation(testFixtures(project(":nucleo")))
}

compose.desktop {
    application {
        mainClass = "com.gonzalo.hiedra.pc.MainKt"
        jvmArgs += listOf("-Dfamilia.version=$version")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Hiedra"
            packageVersion = version
            description = "Tus juegos, en la cuenta de la familia"
            vendor = "Gonzalo"
            modules("java.net.http", "jdk.crypto.ec")
            linux { packageName = "hiedra"; menuGroup = "Game"; shortcut = true; iconFile.set(project.file("icono/hiedra.png")) }
            macOS { bundleID = "com.gonzalo.hiedra"; iconFile.set(project.file("icono/hiedra.icns")) }
        }
    }
}

tasks.test {
    systemProperty("familia.emulador", System.getProperty("familia.emulador") ?: "http://127.0.0.1")
    systemProperty("familia.reglas", rootProject.file("nube/reglas.json").absolutePath)
    System.getProperty("capturas")?.let { systemProperty("capturas", it); outputs.upToDateWhen { false } }
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
