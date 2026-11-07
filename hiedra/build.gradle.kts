import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}
val firmaProps = Properties().apply { rootProject.file("firma/claves.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
fun clave(n: String): String = firmaProps.getProperty("$n.pass").orEmpty()

android {
    namespace = "com.gonzalo.hiedra"
    compileSdk = 35
    defaultConfig {
        // Convive con Atrio (com.gonzalo.gamerate), Marfil (com.gonzalo.marfil), Carmesí (com.gonzalo.portico) y Cobalto (com.gonzalo.cobalto): otro paquete, otra app, misma familia visual.
        applicationId = "com.gonzalo.hiedra"
        minSdk = 29
        targetSdk = 35
        versionCode = 35
        versionName = "0.7.0"
        // El jugador interno (LibretroDroid y los núcleos del buildbot de libretro) solo se usa en arm64: así el APK no lleva
        // las bibliotecas nativas de otras arquitecturas que ningún móvil actual necesita.
        ndk { abiFilters += "arm64-v8a" }
    }
    // Firma fija: cada versión nueva se instala encima de la anterior sin borrar tus juegos.
    signingConfigs {
        create("release") { storeFile = rootProject.file("firma/hiedra.keystore"); storePassword = clave("hiedra"); keyAlias = "hiedra"; keyPassword = clave("hiedra") }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sin firmar aquí: la firma de la familia (la clave nueva con su linaje desde la vieja) la pone herramientas/firmar.py
            signingConfig = null
        }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3g"
                it.forkEvery = 1
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.systemProperty("robolectric.dependency.dir", layout.buildDirectory.dir("robolectric-deps").get().asFile.absolutePath)
                it.dependsOn("copyRobolectricDeps")
            }
        }
    }
}
val robolectricDeps: Configuration by configurations.creating
tasks.register<Copy>("copyRobolectricDeps") { from(robolectricDeps); into(layout.buildDirectory.dir("robolectric-deps")) }

dependencies {
    robolectricDeps("org.robolectric:android-all-instrumented:14-robolectric-10818077-i6")
    implementation(project(":familia"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    // El jugador interno: núcleos libretro con LibretroDroid y los mandos táctiles de RadialGamePad (GPL-3.0, ver LICENSE)
    implementation("com.github.swordfish90:libretrodroid:0.14.0")
    implementation("com.github.swordfish90:radialgamepad:2.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation(testFixtures(project(":nucleo")))
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.20.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.20.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
