// El motor de torrents de Mostaza en el ordenador (Linux y Mac): LOS MISMOS archivos que el del móvil (torrent/src/main), copiados al
// compilar, salvo lo que es de Android (la red y el disco de Android, y su constructor con un Context), que aquí es del ordenador.
// libtorrent4j (MIT) trae la biblioteca nativa de cada sistema en su propio .jar: la de Linux x86-64 y la del Mac con Apple Silicon.
// Las pruebas del móvil que no son de Android (un sembrador en este ordenador y el motor viéndolo por su dirección local), también.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

val libtorrent4j = "2.1.0-39"
val android = rootProject.file("torrent/src")
val mac = System.getProperty("os.name").lowercase().contains("mac")

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// Lo del móvil, tal cual; de MotorTorrent.kt se quitan solo sus dos líneas de Android (el import de Context y el constructor con él), y
// su Log es el de aquí (Log.kt)
val compartido = tasks.register<Sync>("compartidoDeAndroid") {
    from(File(android, "main/java/com/gonzalo/torrent")) {
        exclude("Disco.kt", "Red.kt")
        filesMatching("MotorTorrent.kt") {
            var saltar = 0
            filter { l ->
                when {
                    saltar > 0 -> { saltar--; null }
                    l.trim() == "import android.content.Context" || l.trim() == "import android.util.Log" -> null
                    l.trim().startsWith("constructor(context: Context") -> { saltar = 1; null }
                    else -> l
                }
            }
        }
    }
    into(layout.buildDirectory.dir("compartido/main/com/gonzalo/torrent"))
}
val pruebasCompartidas = tasks.register<Sync>("pruebasDeAndroid") {
    from(File(android, "test/java/com/gonzalo/torrent"))
    from(File(android, "compartido/java/com/gonzalo/torrent"))
    into(layout.buildDirectory.dir("compartido/test/com/gonzalo/torrent"))
}
sourceSets["main"].kotlin.srcDir(layout.buildDirectory.dir("compartido/main"))
sourceSets["test"].kotlin.srcDir(layout.buildDirectory.dir("compartido/test"))
tasks.named("compileKotlin") { dependsOn(compartido) }
tasks.named("compileTestKotlin") { dependsOn(pruebasCompartidas) }

dependencies {
    api("org.libtorrent4j:libtorrent4j:$libtorrent4j")
    // La biblioteca nativa del sistema en que se empaqueta (como las de los mandos): Linux (x86-64; el .jar trae también la de ARM)
    // o el Mac con Apple Silicon. La app la saca a su carpeta al arrancar ([NativaTorrent])
    runtimeOnly("org.libtorrent4j:libtorrent4j-${if (mac) "macos" else "linux"}:$libtorrent4j")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
}

// Para las pruebas, la biblioteca nativa sacada de su .jar a la carpeta de la compilación (la app la saca a la suya al arrancar)
val nativa = if (mac) "libtorrent4j.dylib" else "libtorrent4j.so"
val nativasPruebas: Configuration by configurations.creating { isTransitive = false }
dependencies { nativasPruebas("org.libtorrent4j:libtorrent4j-${if (mac) "macos" else "linux"}:$libtorrent4j") }
val sacaNativa = tasks.register<Copy>("nativaPruebas") {
    from(nativasPruebas.elements.map { e -> e.map { zipTree(it) } }) { include(if (mac) "lib/arm64/$nativa" else "lib/x86_64/$nativa"); eachFile { path = name } }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("nativa-pruebas"))
}

tasks.test {
    dependsOn(sacaNativa)
    maxHeapSize = "1g"
    jvmArgs("-XX:ErrorFile=${layout.buildDirectory.get().asFile.absolutePath}/hs_err_%p.log")
    systemProperty("libtorrent4j.jni.path", layout.buildDirectory.file("nativa-pruebas/$nativa").get().asFile.absolutePath)
    testLogging { events("failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
