// El org.json de Android (AOSP, libcore/json; Apache 2.0) para las versiones de escritorio: el mismo comportamiento que en el móvil
// (números, nulos, el orden de los campos al escribir), para que el núcleo de los escritos de Cobalto haga lo mismo en los dos.
// Sacado de com.vaadin.external.google:android-json 0.0.20131108 (fuentes); único cambio: keys() devuelve Iterator<String>,
// como en el SDK de Android de ahora. Ver LEEME.md.
plugins { `java-library` }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-Xlint:-unchecked"); options.encoding = "UTF-8" }
