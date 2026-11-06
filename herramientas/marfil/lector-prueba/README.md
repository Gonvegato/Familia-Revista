# Pruebas del lector en un navegador (Chromium con pantalla táctil)

Abren el lector de `app/src/main/assets/lector` (con un `window.Atrio` de mentira) y un EPUB de tres capítulos, y simulan dedos de verdad:

- `botones.js`: en «Desplazamiento continuo», al final de cada capítulo hay un botón «Siguiente capítulo → <nombre>» (y al principio «← Capítulo anterior»); seguir deslizando NO pasa solo de capítulo; en «Páginas» los botones no se ven (`FLOW=paginated`).
- `toques.js`: deslizando, mantener pulsado NO selecciona palabra y dos toques seguidos SÍ; la línea de abajo (% y lo que queda) no se ve. En «Páginas» todo sigue como antes (`FLOW=paginated`).

- `escrito.js`: el EPUB que Marfil hace con los Escritos de Cobalto (lo deja `EscritosTest` en `app/build/outputs/escritos/escrito.epub`) se abre con el lector de verdad: índice (portada, partes, capítulos), metadatos, cursivas y negritas, y se pasa de capítulo. `gradle :app:testDebugUnitTest --tests '*EscritosTest*' && node escrito.js`.

- `cfi.js` (Marfil 0.14.0): las posiciones (EPUB CFI) que calcula el lector de verdad para un caso (`herramientas/casos/marfil-cfi.json`: un
  EPUB y unas frases), y lo que lee en los CFI que escribe Marfil del ordenador. Lo comprueba `CfiTest` de `escritorio/marfil`. No necesita
  `crear_epub.py`: el EPUB va en el caso. `NODE_PATH=<carpeta de playwright> node cfi.js ../../casos/marfil-cfi.json`.
- `tableta.js` (Marfil 0.14.0): en la tableta tumbada el panel va de lado; de pie, al centro; en el móvil, de todo el ancho; el índice con
  el % de cada capítulo y «Estás aquí»; la barra con una marca por capítulo. `NODE_PATH=<carpeta de playwright> node tableta.js` (con
  `FOTOS=<carpeta>` guarda las capturas). Estos dos usan el Chrome del sistema (`CHROME=…` si está en otro sitio).

Uso: `python3 crear_epub.py` genera `/tmp/lt/t.epub` (tres capítulos con índice), copia `t.html` a `/tmp/lt/` y `node botones.js` / `node toques.js` (hace falta `playwright`).
