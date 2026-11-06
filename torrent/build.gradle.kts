// El motor de torrents de Mostaza: convierte un stream de Stremio con infoHash en una dirección local (http://127.0.0.1:…) que el
// reproductor abre y en la que salta, mientras el torrent se descarga. Por dentro, libtorrent con libtorrent4j (MIT).
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// libtorrent4j 2.1.0-39 (libtorrent 2.0): la parte Java es común y cada arquitectura trae su biblioteca nativa en su propio .jar
val libtorrent4j = "2.1.0-39"

android {
    namespace = "com.gonzalo.torrent"
    compileSdk = 35
    defaultConfig {
        minSdk = 29
        // libtorrent4j llama a Java desde C++ (SWIG): R8 no puede quitar ni renombrar nada suyo en la app
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions {
        unitTests {
            // android.util.Log y compañía no hacen nada en las pruebas del ordenador (en vez de fallar)
            isReturnDefaultValues = true
            all {
                // Las pruebas de verdad (un torrent sembrado en este ordenador y visto por el motor) usan la libtorrent de Linux x86_64
                it.systemProperty("libtorrent4j.jni.path", layout.buildDirectory.file("nativos-linux/libtorrent4j.so").get().asFile.absolutePath)
                it.dependsOn("nativosLinux")
                it.maxHeapSize = "1g"
                // Si la biblioteca nativa revienta, su informe queda aquí (y no en una carpeta cualquiera)
                it.jvmArgs("-XX:ErrorFile=${layout.buildDirectory.get().asFile.absolutePath}/hs_err_%p.log")
                it.testLogging {
                    events("failed", "skipped")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                    // -Ptorrent.salida: con lo que escriben las pruebas (para ver dónde se para una)
                    showStandardStreams = providers.gradleProperty("torrent.salida").isPresent
                }
            }
        }
    }
}

val nativosLinux: Configuration by configurations.creating { isTransitive = false }
tasks.register<Copy>("nativosLinux") {
    from(nativosLinux.elements.map { e -> e.map { zipTree(it) } }) { include("lib/x86_64/libtorrent4j.so"); eachFile { path = name } }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("nativos-linux"))
}

dependencies {
    implementation("org.libtorrent4j:libtorrent4j:$libtorrent4j")
    // Las nativas de Android: arm64-v8a (los móviles de ahora), armeabi-v7a (móviles viejos y muchas teles) y x86_64 (el emulador).
    // Cada una pesa lo suyo: la app elige las que lleva con ndk { abiFilters }.
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:$libtorrent4j")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:$libtorrent4j")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:$libtorrent4j")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
    nativosLinux("org.libtorrent4j:libtorrent4j-linux:$libtorrent4j")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
