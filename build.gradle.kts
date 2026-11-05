plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
    // Kotlin 2: el compilador de Compose es un plugin más de Kotlin (la misma versión). Kotlin 2.2.20: el que recomienda Compose
    // Multiplatform 1.12, para compartir el núcleo con las versiones de escritorio (Linux y Mac)
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("io.github.takahirom.roborazzi") version "1.20.0" apply false
}
