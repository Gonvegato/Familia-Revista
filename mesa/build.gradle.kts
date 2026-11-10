// La cara común de la familia (dirección D, «la mesa y la estantería»): la barra de abajo, Hoy, la estantería y la ficha, iguales en las
// seis apps. Cada app le da sus colores y sus letras (Tinta) y pinta sus portadas.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.gonzalo.mesa"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.material3:material3")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
