// Hiedra para Linux y Mac: tus juegos de la cuenta, y jugar a los de este ordenador (Steam, Heroic, Lutris, emuladores y tus programas),
// también en la sala (a pantalla completa y con el mando, para la tele). La misma cuenta que el Hiedra del móvil (juegos/<id>,
// tiempo/<id>~<fuente>; ver nube/ESQUEMA.md), y hace de «Tu PC» para el móvil (suite/).
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val version = "1.3.0"

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// Los mandos: SDL 3 por LWJGL (solo su parte de mandos, sin ventanas). Las bibliotecas nativas, las del sistema en que se empaqueta
// (Linux x86-64 en Linux; Apple Silicon en el Mac)
val lwjgl = "3.4.3"
val nativas = if (System.getProperty("os.name").lowercase().contains("mac")) "natives-macos-arm64" else "natives-linux"

dependencies {
    implementation(project(":escritorio:familia"))
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
        mainClass = "com.gonzalo.hiedra.pc.MainKt"
        jvmArgs += listOf("-Dfamilia.version=$version")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Bastet"
            packageVersion = version
            description = "Tus juegos, en la cuenta de la familia"
            vendor = "Gonzalo"
            // (jdk.unsupported: LWJGL lo necesita para los mandos)
            modules("java.net.http", "jdk.crypto.ec", "jdk.unsupported")
            linux { packageName = "hiedra"; menuGroup = "Game"; shortcut = true; iconFile.set(project.file("icono/hiedra.png")) }
            macOS { bundleID = "com.gonzalo.hiedra"; iconFile.set(project.file("icono/hiedra.icns")) }
        }
    }
}

// Probar la sala de verdad (la ventana, contra el emulador de Firebase, recorrida con el teclado y con capturas de la pantalla):
// ./gradlew -PsoloEscritorio :escritorio:hiedra:probarSala -Psalida=<carpeta>   (necesita una pantalla: en un servidor, Xvfb)
tasks.register<JavaExec>("probarSala") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.gonzalo.hiedra.pc.ProbarSalaKt")
    systemProperty("familia.emulador", System.getProperty("familia.emulador") ?: "http://127.0.0.1")
    systemProperty("familia.reglas", rootProject.file("nube/reglas.json").absolutePath)
    systemProperty("familia.version", version)
    args(providers.gradleProperty("salida").getOrElse(layout.buildDirectory.dir("probar-sala").get().asFile.path))
}

tasks.test {
    systemProperty("familia.emulador", System.getProperty("familia.emulador") ?: "http://127.0.0.1")
    systemProperty("familia.reglas", rootProject.file("nube/reglas.json").absolutePath)
    System.getProperty("capturas")?.let { systemProperty("capturas", it); outputs.upToDateWhen { false } }
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
