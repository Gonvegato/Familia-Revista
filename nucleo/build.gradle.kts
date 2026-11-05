// El núcleo de la familia: Kotlin puro, sin Android (el mismo código servirá en el PC). Cuenta, sincronización, imágenes y claves.
plugins { id("org.jetbrains.kotlin.jvm") }

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { kotlinOptions.jvmTarget = "17" }

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // Las pruebas de verdad van contra el emulador de Firebase (herramientas/emulador.sh): si no está encendido, se saltan
    systemProperty("familia.emulador", System.getProperty("familia.emulador") ?: "http://127.0.0.1")
    systemProperty("familia.reglas", rootProject.file("nube/reglas.json").absolutePath)
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
