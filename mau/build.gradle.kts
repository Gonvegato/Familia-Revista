// Mau, el gato de la familia: sus dibujos, cómo se mueve y lo que piensa (Kotlin puro, sin Android, para el móvil y el ordenador).
// Lo que se ve (Compose) está en mau/compose: lo comparten familia (Android) y escritorio:familia.
plugins { id("org.jetbrains.kotlin.jvm") }

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
