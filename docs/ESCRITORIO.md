# Las versiones de escritorio (Linux y Mac)

**Azabache, Cobalto, Hiedra y Marfil para Linux y Mac**, en `escritorio/`. Son ventanas a la misma cuenta que las del móvil: todo lo
tuyo está en la cuenta (Firebase, el proyecto de siempre) y en el ordenador solo hay una caché para funcionar sin conexión y lo que es
de ese ordenador (dónde está un archivo, la ventana). Lo que haces en una lo ves al momento en las demás, en el ordenador y en el móvil.

## Instalar

- **Mac (Apple Silicon)**: `Azabache-1.0.1-mac.dmg` (y las otras tres). Abre el .dmg y arrastra la app a Aplicaciones. No está firmada por
  Apple (esa firma es de pago), así que la primera vez macOS la bloquea: ábrela una vez (avisa y no la abre), ve a **Ajustes del Sistema →
  Privacidad y seguridad → Seguridad**, pulsa **«Abrir igualmente»** (sale durante una hora después del aviso) y pon tu contraseña. Desde
  entonces se abre con doble clic. En los macOS de ahora (Sequoia y los siguientes) el truco de clic derecho → Abrir ya no vale.
- **Linux** (ordenadores x86-64, los de siempre; no ARM): `Azabache-1.0.1-linux.deb` (Ubuntu, Debian, Mint…: doble clic, o `sudo apt install ./Azabache-1.0.1-linux.deb`) o, en
  cualquier otro, `Azabache-1.0.1-linux.tar.gz` (se descomprime y se abre `Azabache/bin/Azabache`).
- **Entrar una vez**: entras en cualquiera de las cuatro y las otras tres de ese ordenador entran solas. La contraseña no se guarda: queda
  la sesión, en el llavero del sistema (el Llavero del Mac; en Linux, el Secret Service —GNOME Keyring, KWallet— si hay `secret-tool`, y si
  no, un archivo que solo lee tu usuario).
- **Actualizarse**: cada app mira tu kDrive («Mi carpeta/versiones»: `<App>-<versión>-mac.dmg` / `-linux.deb`) y en Ajustes → Versión
  la baja y la abre. Los actualizadores de Android solo cogen `<App>-<versión>.apk`: no se confunden.

Los paquetes los hace GitHub (`.github/workflows/escritorio.yml`): las pruebas en Linux contra el emulador de Firebase, los `.deb` y
`.tar.gz` en Linux y los `.dmg` en un Mac de GitHub (Apple Silicon). Los artefactos de cada ejecución son los paquetes; se suben a tu
kDrive con `herramientas/escritorio/subir-escritorio.py`.

## Qué hace cada una

- **Azabache**: tus fichas de la cuenta (los juegos y los libros son los nodos de Hiedra y Marfil, sin copia; tus categorías —películas,
  series…—, el diario y las sagas en `azabache/<lista>~<id>`), con la misma lógica que `Cosas.kt` y `Propias.kt` del móvil. Portada,
  colección, ficha (estado, nota y criterios, reseña, fechas, tiempo jugado o leído), añadir (SteamGridDB, Open Library, TMDB, TVmaze),
  portadas (de un archivo, una dirección o internet), diario, sagas, cifras del año y ajustes (las claves de servicios de la cuenta y tus
  categorías). Sin valorar nunca sale con un 0.
- **Cobalto**: el núcleo de los escritos (formato, sincronización, mezcla a tres bandas), la estructura, el texto (Markdown ligero,
  tipografía automática, deshacer), exportar e importar son **los mismos archivos** que compila Cobalto de Android (se copian de
  `cobalto/src` al compilar; las pruebas de la mezcla y de la estructura, también). Portada (palabras de hoy, racha, últimos días, seguir
  escribiendo), la obra con su índice (partes, capítulos, escenas) y el editor, fichas, notas, papelera, exportar (Word como libro o como
  manuscrito, EPUB, Markdown, texto), copias de seguridad completas en el ordenador (cada 15 min y al cerrar; las últimas 40) y lo que se
  publica para Azabache y Marfil (`obras/`, `capitulos/`, `palabras/`).
- **Marfil**: tus libros de la cuenta y leerlos por donde ibas en el móvil. EPUB (leído como XML, sin reordenar: la posición que guarda es
  el mismo EPUB CFI que guarda foliate en el móvil, y la del móvil se abre aquí en el mismo párrafo), PDF (Apache PDFBox) y cómics (CBZ).
  Lo leído (`lectura/<kid>`), «Leyendo» y «Leído» con su fecha (las reglas del móvil), el tiempo de este ordenador, el reto del año, los
  archivos por tu kDrive (`Marfil/Libros/…`: se suben al añadirlos y se bajan al abrirlos) y los capítulos terminados de Cobalto.
- **Hiedra**: tus juegos de la cuenta (los mismos de Hiedra del móvil), los de Steam instalados en el ordenador (se abren con Steam: sus
  horas las cuenta Steam y llegan por su API) y tus programas (los añades a mano; Hiedra cuenta el tiempo hasta que los cierras, en
  `tiempo/<juego>~<dispositivo>`). Y hace de **«Tu PC»** para Hiedra del móvil: su biblioteca, a qué juegas y abrir un juego desde el móvil
  (el protocolo `suite/` de Atrio del PC). Su presencia dice `tiempo: true`, y Hiedra del móvil (0.5.3) no vuelve a contar ese tiempo.

## Lo que todavía no hacen (para no engañar)

- **Azabache**: la vista de tu pareja, las recomendaciones, «Descubrir», las maquetas y las estadísticas más finas de Android.
- **Cobalto**: el árbol de fichas, la línea temporal, la guía de la novela, los sprints, los sinónimos, el historial de versiones a la
  vista (se guarda igual) y compartir con tu pareja.
- **Marfil**: subrayados y notas nuevos (los del móvil se conservan en la cuenta; aquí no se pintan), los diccionarios y los ajustes del
  lector que viajan (`settings`). Otros formatos (MOBI, FB2, TXT): en el móvil.
- **Hiedra**: emuladores y ROMs, capturas y streaming para el móvil (`shot`, `stream` responden que todavía no).
- Las letras de Google Fonts que se eligen en el móvil: aquí van las de la app (Atrio y Vitrina) en su lugar.

## Por dentro

- `escritorio/familia`: lo común. La cuenta por app sobre el núcleo (`LaFamilia`: escucha en directo mientras la app está abierta y sube lo
  pendiente al cerrar), el llavero (`Llavero`), lo delicado cifrado con una clave del llavero (`DiscoSeguro`), entrar una vez
  (`Sesion.comparte()` / `adopta()` del núcleo: se comparte la sesión, nunca la contraseña), portadas con el mismo hash que en Android
  (Skia: 600×900, JPEG 85), kDrive, el actualizador, la ventana (tamaño y sitio recordados) y el aspecto de revista (las paletas, las
  formas y los componentes de Android; la apariencia de `ajustes/<app>`, la misma que la de la app en el móvil).
- `escritorio/json`: el `org.json` de Android (AOSP, Apache 2.0), para que el núcleo de Cobalto se comporte igual en los dos.
- Carpetas: en el Mac `~/Library/Application Support/Familia/<App>`; en Linux `~/.local/share/familia/<app>`.
- Compilar sin el SDK de Android: `./gradlew -PsoloEscritorio test`, `… :escritorio:<app>:run`, `… packageDeb` (Linux, necesita `fakeroot`)
  o `… packageDmg` (solo en un Mac).
- Pruebas: contra el emulador oficial de Firebase con las reglas del repositorio (`herramientas/emulador.sh`); «el móvil» es otro
  dispositivo de la cuenta escribiendo con el formato de Android. Capturas: `-Dcapturas=<carpeta>` en las de cada app.

## Las reglas (no se negocian)

- **La cuenta es la única verdad**; el contrato es `nube/ESQUEMA.md`, `nube/ESCRITOS.md` y `nube/reglas.json`.
- **Sin copias entre apps**: un juego es uno, un libro es uno. Lo compartido se cambia **añadiendo** (nunca cambiando lo que significa un
  campo), se apunta en `ESQUEMA.md` y se prueba contra el emulador. Nunca `null`.

Versiones de Android a día de hoy: Hiedra 0.5.4, Marfil 0.13.5, Cobalto 0.3.3, Azabache 1.5.2. De escritorio: las cuatro 1.0.1.
