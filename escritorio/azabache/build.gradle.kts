// Azabache para Linux y Mac: tus valoraciones (juegos, libros, películas, series…), el diario y las sagas. La misma cuenta que el
// Azabache del móvil: sus fichas son los juegos y libros de la cuenta, sin copia (ver nube/ESQUEMA.md).
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val version = "1.2.1"

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":escritorio:familia"))
    testImplementation("junit:junit:4.13.2")
    testImplementation(testFixtures(project(":nucleo")))
    // El org.json de Android (AOSP): las pruebas escriben con el mismo código del móvil y comparan (maquetas y recomendaciones)
    testImplementation(project(":escritorio:json"))
}

compose.desktop {
    application {
        mainClass = "com.gonzalo.azabache.pc.MainKt"
        jvmArgs += listOf("-Dfamilia.version=$version")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Azabache"
            packageVersion = version
            description = "Tus valoraciones, en la cuenta de la familia"
            vendor = "Gonzalo"
            modules("java.net.http", "jdk.crypto.ec")
            linux { packageName = "azabache"; menuGroup = "Office"; shortcut = true; iconFile.set(project.file("icono/azabache.png")) }
            macOS { bundleID = "com.gonzalo.azabache"; iconFile.set(project.file("icono/azabache.icns")) }
        }
    }
}

tasks.test {
    systemProperty("familia.emulador", System.getProperty("familia.emulador") ?: "http://127.0.0.1")
    systemProperty("familia.reglas", rootProject.file("nube/reglas.json").absolutePath)
    System.getProperty("capturas")?.let { systemProperty("capturas", it); outputs.upToDateWhen { false } }
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
