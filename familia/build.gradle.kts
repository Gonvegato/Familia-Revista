// Lo común de Android para las cuatro apps: la cuenta (sobre el núcleo), entrar una vez, la subida en segundo plano y el actualizador.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.gonzalo.familia"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty("robolectric.dependency.dir", rootProject.file("azabache/build/robolectric-deps").absolutePath)
                it.dependsOn(":azabache:copyRobolectricDeps")
            }
        }
    }
}
dependencies {
    api(project(":nucleo"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.runtime:runtime")
    // La pantalla para elegir el estilo de un widget al ponerlo (EstiloWidgetActivity)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
