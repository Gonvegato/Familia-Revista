# org.json de Android (para escritorio)

Las fuentes de `org.json` de Android (AOSP, `libcore/json`), con su licencia Apache 2.0 (la cabecera de cada archivo). Vienen del
paquete de fuentes de `com.vaadin.external.google:android-json:0.0.20131108.vaadin1` (Maven Central).

Por qué: el núcleo de los escritos de Cobalto (`cobalto/src/main/java/com/gonzalo/cobalto/Escritos.kt`, `EscribirMezcla.kt`…) es el
mismo archivo en Android y en el escritorio, y usa `org.json`. Con esta implementación se comporta igual en los dos (el `org.json` de
Maven convierte los decimales en `BigDecimal` y escribe los números de otra manera).

Único cambio: `JSONObject.keys()` devuelve `Iterator<String>` (como en el SDK de Android de ahora; la versión de 2013 lo devolvía sin tipo).
