// Lo de Cobre que no es pantalla: listas M3U, el cliente de Xtream Codes, la guía XMLTV, emparejar canales con la guía y lo que va a la
// cuenta. Kotlin puro, sin Android (como el núcleo): lo mismo servirá a Cobre del ordenador (Linux y Mac).
plugins { id("org.jetbrains.kotlin.jvm") }

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // Con poca memoria, a propósito: las pruebas de listas y guías enormes (decenas de miles de canales, una guía de más de 100 MB)
    // comprueban que se leen a trozos, sin tenerlas enteras en memoria (como en un móvil o una tele con poca)
    maxHeapSize = "256m"
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
