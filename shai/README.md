# Shai (`com.gonzalo.shai`)

Tu carrera de EA Sports FC: tú metes los partidos y Claude escribe lo de fuera del campo (la prensa, la red, los mensajes, las ofertas, las
decisiones, la vida). Su historia, en `docs/shai/HISTORIA.md`.

- `Modelo.kt`: la carrera (`Universo`), el personaje, su gente, sus cosas, el partido y lo que pasa (`Entrada`: posts, mensajes, portada,
  ofertas, decisión, efectos), con su JSON. Kotlin puro.
- `Vida.kt`: las cifras por temporada y competición, la bolsa y la tienda (todo inventado), la jornada (sueldo, rentas, precios) y los
  efectos con sus límites.
- `Claude.kt`: la API de Claude por HTTP directo (mensajes, lotes y sus resultados), la versión `2026-10-21` y los errores.
- `Narrador.kt`: el prompt de sistema (las reglas: el campo es tuyo; personas reales solo de pasada; lo íntimo, con inventados), el contexto
  de cada carrera, las peticiones (con su esquema JSON) y cómo se lee lo que vuelve.
- `Gasto.kt`: los precios, lo gastado por mes (en la cuenta) y el tope.
- `Motor.kt`: lo que pasa al meter un partido, al decidir, al aceptar una oferta, al pedir un evento; los lotes; y tu clave (`ClaveApi`,
  solo en `/claves`).
- `Carreras.kt`: dónde se guarda (la colección `shai` de la cuenta). `Eventos.kt`: el trabajo de fondo y los avisos.
- Pantallas: `PantPortada.kt`, `PantNueva.kt`, `PantCarrera.kt` (portada, red, mensajes, ofertas), `PantVida.kt` (cifras, vida, perfil),
  `PantPartido.kt` (las tres plantillas), `PantAjustes.kt` (y lo común de la familia: tema, cuenta, actualizador).

Pruebas: `./gradlew :shai:testDebugUnitTest` (con una API de mentira: nada sale a la red ni se gasta; la cuenta, contra el emulador de
Firebase: `herramientas/emulador.sh`). Capturas: `-Proborazzi.test.record=true --rerun` (y `-Dcapturas=<carpeta>`).
Versión: `herramientas/firmar.py shai` y `herramientas/shai/subir-version.py Shai-<versión>.apk`. Icono: `herramientas/escritorio/icono-marca.py`.
