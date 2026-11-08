# Ptah (`com.gonzalo.ptah`)

Los widgets de la familia: diseñas tus widgets de la pantalla de inicio (relojes, calendario, buscador, accesos, el tiempo, la batería,
notas, cuentas atrás y fotos) con la estética de revista, y los pones del tamaño que quieras. Su historia, en `docs/ptah/HISTORIA.md`.

- `Diseno.kt`: el modelo (un diseño = tipo + estilo + opciones), su JSON y los tamaños (celdas ↔ dp).
- `Tipos.kt` y `Tipos*.kt`: el registro de tipos; cada uno, su dibujo y su editor. Para añadir uno: un `object` que cumpla `TipoWidget` y su
  línea en `Tipos.TODOS`.
- `Lienzo.kt`: el dibujo (el mismo para la vista previa y el widget) y lo que se mueve solo (`Vivo`: `TextClock` y `AnalogClock`).
- `Widgets.kt`: el widget (`WPtah`), lo que se manda al lanzador, las zonas para tocar, la medianoche y el refresco de fondo.
- `Tiempo.kt`: Open-Meteo (geocoding y previsión), los códigos WMO y la caché.
- `Disenos.kt`: tus diseños en la cuenta (`ajustes/ptah_disenos`), los colores de hace poco y los datos del móvil para dibujar.
- Pantallas: `PantPortada.kt`, `PantEditor.kt`, `SelectorColor.kt`, `PantAjustes.kt` (y lo común de la familia: tema, cuenta, actualizador).

Pruebas: `./gradlew :ptah:testDebugUnitTest` (la cuenta, contra el emulador de Firebase: `herramientas/emulador.sh`). Capturas y vistas
previas: `-Proborazzi.test.record=true --rerun` (y `-Dcapturas=<carpeta>`); `w_prev_ptah.png` va a `src/main/res/drawable-nodpi/`.
Versión: `herramientas/firmar.py ptah` y `herramientas/ptah/subir-version.py Ptah-<versión>.apk`. Icono: `herramientas/escritorio/icono-marca.py`.
