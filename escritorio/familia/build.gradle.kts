// Lo común de las versiones de escritorio (Linux y Mac): la cuenta (sobre el núcleo), el llavero del sistema, las portadas,
// kDrive, el actualizador y el aspecto de revista. Lo usan Azabache, Cobalto, Hiedra y Marfil de escritorio.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api(project(":nucleo"))
    api(compose.desktop.currentOs)
    api(compose.material3)
    api(compose.materialIconsExtended)
    testImplementation("junit:junit:4.13.2")
    testImplementation(testFixtures(project(":nucleo")))
}

tasks.test {
    systemProperty("familia.emulador", System.getProperty("familia.emulador") ?: "http://127.0.0.1")
    systemProperty("familia.reglas", rootProject.file("nube/reglas.json").absolutePath)
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
