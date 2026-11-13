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
    namespace = "com.gonzalo.marfil"
    compileSdk = 35
    defaultConfig {
        // Convive con Atrio (com.gonzalo.gamerate) y Pórtico: otro paquete, otra app, misma familia visual.
        applicationId = "com.gonzalo.marfil"
        minSdk = 29
        targetSdk = 35
        versionCode = 67
        versionName = "0.22.0"
        // Solo los procesadores que se usan (el traductor del diccionario pesa ~16 MB por cada uno): móviles y tablets (arm64) y Waydroid en el PC (x86_64)
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    // Firma fija: cada versión nueva se instala encima de la anterior sin borrar tus libros.
    signingConfigs {
        create("release") { storeFile = rootProject.file("firma/marfil.keystore"); storePassword = clave("marfil"); keyAlias = "marfil"; keyPassword = clave("marfil") }
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
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    // Diccionario del lector: idioma de la frase y traducción (el idioma se baja la primera vez, ~30 MB)
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")
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
