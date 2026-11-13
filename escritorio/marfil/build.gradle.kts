// Marfil para Linux y Mac: tus libros (los de la cuenta, `libros/<kid>`), leerlos aquí (EPUB, PDF y cómics) por donde ibas en el
// móvil, con tus subrayados, notas, marcadores y los ajustes del lector de la cuenta, y lo leído, los tiempos y los archivos (tu kDrive)
// igual que Marfil de Android. Ver nube/ESQUEMA.md.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val version = "1.3.0"

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":escritorio:familia"))
    // Leer los EPUB (XHTML) tal cual vienen, sin «arreglarlos»: así las posiciones (EPUB CFI) son las mismas que en el móvil
    implementation("org.jsoup:jsoup:1.24.1")
    // Los PDF (Apache PDFBox)
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation(testFixtures(project(":nucleo")))
}

compose.desktop {
    application {
        mainClass = "com.gonzalo.marfil.pc.MainKt"
        jvmArgs += listOf("-Dfamilia.version=$version")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Seshat"
            packageVersion = version
            description = "Tus libros, en la cuenta de la familia"
            vendor = "Gonzalo"
            modules("java.net.http", "jdk.crypto.ec", "java.xml")
            linux { packageName = "marfil"; menuGroup = "Office"; shortcut = true; iconFile.set(project.file("icono/marfil.png")) }
            macOS { bundleID = "com.gonzalo.marfil"; iconFile.set(project.file("icono/marfil.icns")) }
        }
    }
}

tasks.test {
    systemProperty("familia.emulador", System.getProperty("familia.emulador") ?: "http://127.0.0.1")
    systemProperty("familia.reglas", rootProject.file("nube/reglas.json").absolutePath)
    System.getProperty("capturas")?.let { systemProperty("capturas", it); outputs.upToDateWhen { false } }
    // Para rehacer los casos que comparten el móvil y el ordenador (herramientas/casos): -Dcasos.escribe=<carpeta>, -Dcasos.imprime
    listOf("casos.escribe", "casos.imprime").forEach { k -> System.getProperty(k)?.let { systemProperty(k, it); outputs.upToDateWhen { false } } }
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
