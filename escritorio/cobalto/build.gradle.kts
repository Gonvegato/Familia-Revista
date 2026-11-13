// Cobalto para Linux y Mac: lo que escribes, en la cuenta (nube/ESCRITOS.md). El núcleo de los escritos (formato, sincronización y
// mezcla a tres bandas), el texto, exportar e importar son LOS MISMOS archivos que compila Cobalto de Android: se copian de
// cobalto/src/main al compilar (no hay dos versiones que puedan separarse): también el árbol, la guía, los sinónimos y los sprints, sin sus
// pantallas, y las copias de seguridad. Las pruebas de la mezcla, la estructura, el árbol y la guía, los sprints en la cuenta y las copias, también.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val version = "1.3.1"
val android = rootProject.file("cobalto/src")

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// Lo que es igual en el móvil y en el ordenador (Kotlin sin nada de Android: org.json y Compose, que existen en los dos)
val compartido = tasks.register<Sync>("compartidoDeAndroid") {
    from(File(android, "main/java/com/gonzalo/cobalto")) {
        include("Escritos.kt", "EscribirMezcla.kt", "EscribirEstructura.kt", "EscribirTexto.kt", "EscribirExport.kt", "EscribirDiff.kt", "Racha.kt", "Formato.kt", "Obras.kt",
            // El árbol (medidas y «Ordenar»), la guía de la novela, los sinónimos y los sprints (en la cuenta), sin sus pantallas
            "EscribirArbol.kt", "EscribirGuia.kt", "EscribirSinonimos.kt", "EscribirSprint.kt",
            // Las copias de seguridad en el dispositivo (y sacar una obra de una)
            "EscribirCopias.kt")
    }
    into(layout.buildDirectory.dir("compartido/main/com/gonzalo/cobalto"))
}
val pruebasCompartidas = tasks.register<Sync>("pruebasDeAndroid") {
    from(File(android, "test/java/com/gonzalo/cobalto")) { include("EscribirMezclaTest.kt", "EscribirEstructuraTest.kt", "EscribirArbolGuiaTest.kt", "EscribirSprintTest.kt", "EscribirCopiasTest.kt") }
    into(layout.buildDirectory.dir("compartido/test/com/gonzalo/cobalto"))
}
// El diccionario de sinónimos (RLA-ES, LGPL 2.1, sin cambios): el mismo archivo que lleva el móvil en sus assets
val sinonimos = tasks.register<Sync>("sinonimosDeAndroid") {
    from(File(android, "main/assets/sinonimos")) { include("th_es_v2.dat", "COPYING", "LEEME.txt") }
    into(layout.buildDirectory.dir("compartido/recursos/sinonimos"))
}
sourceSets["main"].kotlin.srcDir(layout.buildDirectory.dir("compartido/main"))
sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("compartido/recursos"))
sourceSets["test"].kotlin.srcDir(layout.buildDirectory.dir("compartido/test"))
tasks.named("compileKotlin") { dependsOn(compartido) }
tasks.named("processResources") { dependsOn(sinonimos) }
tasks.named("compileTestKotlin") { dependsOn(pruebasCompartidas) }

dependencies {
    implementation(project(":escritorio:familia"))
    // El org.json de Android (el mismo comportamiento que en el móvil), para el núcleo de los escritos
    implementation(project(":escritorio:json"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation(testFixtures(project(":nucleo")))
}

compose.desktop {
    application {
        mainClass = "com.gonzalo.cobalto.pc.MainKt"
        jvmArgs += listOf("-Dfamilia.version=$version")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Thot"
            packageVersion = version
            description = "Lo que escribes, en la cuenta de la familia"
            vendor = "Gonzalo"
            modules("java.net.http", "jdk.crypto.ec")
            linux { packageName = "cobalto"; menuGroup = "Office"; shortcut = true; iconFile.set(project.file("icono/cobalto.png")) }
            macOS { bundleID = "com.gonzalo.cobalto"; iconFile.set(project.file("icono/cobalto.icns")) }
        }
    }
}

tasks.test {
    systemProperty("familia.emulador", System.getProperty("familia.emulador") ?: "http://127.0.0.1")
    systemProperty("familia.reglas", rootProject.file("nube/reglas.json").absolutePath)
    System.getProperty("capturas")?.let { systemProperty("capturas", it); outputs.upToDateWhen { false } }
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
