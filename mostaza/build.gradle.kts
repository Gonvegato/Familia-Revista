plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.gonzalo.mostaza"
    compileSdk = 35
    defaultConfig {
        // Películas y series de la familia: otro paquete, otra app, la misma cuenta y la misma revista
        applicationId = "com.gonzalo.mostaza"
        minSdk = 29
        targetSdk = 35
        versionCode = 23
        versionName = "0.7.7"
        // Solo móviles y tabletas de 64 bits (como Hiedra): el motor de torrents lleva una biblioteca nativa por arquitectura
        ndk { abiFilters += "arm64-v8a" }
    }
    // (las bibliotecas nativas, comprimidas en el APK: la del motor ocupa unos 6 MB en vez de 16)
    packaging { jniLibs { useLegacyPackaging = true } }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sin firmar aquí: la firma de la familia (sin linaje: Mostaza nace ya con ella) la pone herramientas/firmar.py
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
                // Las capturas, donde se pidan (-Dcapturas=<carpeta>); si no, en build/outputs/roborazzi
                System.getProperty("capturas")?.let { c -> it.systemProperty("capturas", c) }
                it.dependsOn("copyRobolectricDeps")
            }
        }
    }
}
val robolectricDeps: Configuration by configurations.creating
tasks.register<Copy>("copyRobolectricDeps") { from(robolectricDeps); into(layout.buildDirectory.dir("robolectric-deps")) }

// El reproductor: AndroidX Media3. La 1.9.4 es la última que se compila con el SDK 35 de la familia (desde la 1.10 pide el 36)
val media3 = "1.9.4"

dependencies {
    robolectricDeps("org.robolectric:android-all-instrumented:14-robolectric-10818077-i6")
    implementation(project(":familia"))
    // El motor de torrents de la familia (libtorrent4j): ver un torrent mientras se baja
    implementation(project(":torrent"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    // El sonido que no todos los aparatos saben decodificar (AC3, E-AC3, DTS, TrueHD…: muchas tablets no traen la licencia de Dolby):
    // FFmpeg dentro de la app, precompilado por Jellyfin para Media3 (GPL-3.0). Se usa solo si el aparato no puede.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")
    testImplementation("junit:junit:4.13.2")
    testImplementation(testFixtures(project(":nucleo")))
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.work:work-testing:2.9.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.20.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.20.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
