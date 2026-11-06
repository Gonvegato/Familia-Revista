# Las versiones de escritorio (Linux y Mac)

**Azabache, Cobalto, Hiedra y Marfil para Linux y Mac**, en `escritorio/`. Son ventanas a la misma cuenta que las del móvil: todo lo
tuyo está en la cuenta (Firebase, el proyecto de siempre) y en el ordenador solo hay una caché para funcionar sin conexión y lo que es
de ese ordenador (dónde está un archivo, la ventana). Lo que haces en una lo ves al momento en las demás, en el ordenador y en el móvil.

## Instalar

- **Mac (Apple Silicon)**: `Azabache-1.0.1-mac.dmg` (y las otras tres). Abre el .dmg y arrastra la app a Aplicaciones. No está firmada por
  Apple (esa firma es de pago), así que la primera vez macOS la bloquea: ábrela una vez (avisa y no la abre), ve a **Ajustes del Sistema →
  Privacidad y seguridad → Seguridad**, pulsa **«Abrir igualmente»** (sale durante una hora después del aviso) y pon tu contraseña. Desde
  entonces se abre con doble clic. En los macOS de ahora (Sequoia y los siguientes) el truco de clic derecho → Abrir ya no vale.
- **Linux** (ordenadores x86-64, los de siempre; no ARM):
  - **CachyOS, Arch y cualquier otro**: `Azabache-1.0.1-linux.tar.gz`. Se descomprime y se ejecuta su `instalar.sh` (sin sudo):
    `tar xzf Azabache-1.0.1-linux.tar.gz && ./instalar.sh`. Queda en `~/.local/share/familia-apps/Azabache`, con su icono en el menú
    de aplicaciones. Con `./instalar.sh --al-iniciar` se abre sola al entrar en tu sesión (Hiedra, ya en la sala: el launcher del PC);
    `./instalar.sh --quitar` la desinstala (lo tuyo está en la cuenta: no se toca).
  - **Ubuntu, Debian, Mint…**: también vale el `Azabache-1.0.1-linux.deb` (`sudo apt install ./Azabache-1.0.1-linux.deb`), pero así no
    se actualiza sola (la del .deb es del sistema): mejor el .tar.gz.
- **Entrar una vez**: entras en cualquiera de las cuatro y las otras tres de ese ordenador entran solas. La contraseña no se guarda: queda
  la sesión, en el llavero del sistema (el Llavero del Mac; en Linux, el Secret Service —GNOME Keyring, KWallet— si hay `secret-tool`, y si
  no, un archivo que solo lee tu usuario).
- **Se actualizan solas**: al abrirse y cada 6 horas, cada app mira tu kDrive («Mi carpeta/versiones»: `<App>-<versión>-mac.dmg` /
  `-linux.tar.gz`); si hay una versión nueva, la baja sin molestar, avisa con una banda abajo («lista: se pone sola al cerrar ·
  Reiniciar ahora») y se pone sola al cerrar la app. En el Mac el .dmg se monta sin enseñarlo y la app nueva se copia encima, sin la marca de
  cuarentena (es la misma app que ya abriste: no vuelve a pedir «Abrir igualmente»); en Linux la pone su `instalar.sh`.
  Hace falta tu kDrive puesto en Azabache (Ajustes → Servicios). Los actualizadores de Android solo cogen `<App>-<versión>.apk`: no se
  confunden.

Los paquetes los hace GitHub (`.github/workflows/escritorio.yml`): las pruebas en Linux contra el emulador de Firebase, los `.deb` y
`.tar.gz` en Linux y los `.dmg` en un Mac de GitHub (Apple Silicon). Los artefactos de cada ejecución son los paquetes; se suben a tu
kDrive con `herramientas/escritorio/subir-escritorio.py`.

## Qué hace cada una

- **Azabache**: tus fichas de la cuenta (los juegos y los libros son los nodos de Hiedra y Marfil, sin copia; tus categorías —películas,
  series…—, el diario y las sagas en `azabache/<lista>~<id>`), con la misma lógica que `Cosas.kt` y `Propias.kt` del móvil. Portada,
  colección, ficha (estado, nota y criterios, reseña, fechas, tiempo jugado o leído), añadir (SteamGridDB, Open Library, TMDB, TVmaze),
  portadas (de un archivo, una dirección o internet), diario, sagas, cifras del año y ajustes (las claves de servicios de la cuenta y tus
  categorías). Sin valorar nunca sale con un 0. La ficha es la página de revista del móvil (a dos columnas), con tus maquetas del móvil
  tal cual; la vista de tu pareja («Vosotros dos», sus fichas con «Y tú»), y las cifras, la portada y el diario como en Android.
  Desde la 1.2.0, **diseñar la página de una ficha** como en el móvil («Diseñar», junto a «Editar»: cada casilla con lo que quieras, un
  texto o una imagen de SteamGridDB, TMDB, TVmaze, Open Library o una dirección, encuadrada; filas que se parten, se juntan y se estiran;
  «Usar en todos los…» y «Restablecer»; se guarda sola, en las mismas maquetas de la cuenta que el móvil), las **recomendaciones** a tu
  pareja y las suyas («Lucía te recomienda», con «A mi lista»), «En portada» que va pasando como el carrusel del móvil, **«Buscar en
  todo»** y **«Tal día como hoy»**.
- **Cobalto**: el núcleo de los escritos (formato, sincronización, mezcla a tres bandas), la estructura, el texto (Markdown ligero,
  tipografía automática, deshacer), exportar e importar son **los mismos archivos** que compila Cobalto de Android (se copian de
  `cobalto/src` al compilar; las pruebas de la mezcla y de la estructura, también). Portada (palabras de hoy, racha, últimos días, seguir
  escribiendo), la obra con su índice (partes, capítulos, escenas) y el editor, fichas, notas, papelera, exportar (Word como libro o como
  manuscrito, EPUB, Markdown, texto), copias de seguridad completas en el ordenador (cada 15 min y al cerrar; las últimas 40) y lo que se
  publica para Azabache y Marfil (`obras/`, `capitulos/`, `palabras/`).
  Desde la 1.1.0, **el estudio de escritura**: las fichas y las notas se abren como una página para leer (datos, uniones, en qué escenas
  salen) y con un clic se editan (se guarda sola; «Listo» o Esc vuelve a leer). El editor tiene barra de herramientas, **modo
  concentración** (solo la página; Esc sale; con pantalla completa), modo máquina de escribir, buscar y cambiar (en la escena o en toda la
  obra), las **palabras al momento** en el pie (de la escena, la selección, esta sesión, hoy, el capítulo y la obra), los nombres de las
  fichas subrayados (al pasar, su resumen; doble clic, la ficha), sinónimos, borradores e historial al lado. Y las pestañas **Árbol**
  (arrastrar, unir con Mayús, la rueda acerca), **Línea**, **Cifras**, **Guía** (los 11 pasos), **Leer** (la obra entera como un libro),
  los **sprints** y el manuscrito en **cuadros** (como un corcho). Atajos: ⌘ en el Mac y Ctrl en Linux (⌘1–⌘8 cambian de pestaña; «?»
  en la barra los enseña todos). Lo que es solo de este ordenador (el tamaño de letra y la letra del editor, y el historial de sprints) se
  guarda en `editor.json` de la carpeta de la app; las obras, fichas y notas, en la cuenta como siempre.
  Desde la 1.1.1, **los sprints van en la cuenta** (también el que está en marcha: lo empiezas en el móvil y aquí se ve y se para), y
  Ajustes → Copias **saca una obra de cualquier copia como obra nueva** («… (copia)», sin tocar nada de lo que hay). Las copias de cada
  15 minutos se hacen solo si algo ha cambiado (las de al cerrar y las de a mano, siempre).
- **Marfil**: tus libros de la cuenta y leerlos por donde ibas en el móvil. EPUB (leído como XML, sin reordenar: la posición que guarda es
  el mismo EPUB CFI que guarda foliate en el móvil, y la del móvil se abre aquí en el mismo párrafo), PDF (Apache PDFBox) y cómics (CBZ).
  Lo leído (`lectura/<kid>`), «Leyendo» y «Leído» con su fecha (las reglas del móvil), el tiempo de este ordenador, el reto del año, los
  archivos por tu kDrive (`Marfil/Libros/…`: se suben al añadirlos y se bajan al abrirlos) y los capítulos terminados de Cobalto.
  Desde la 1.1.0, **el lector como el del móvil**: tus subrayados y notas de la cuenta se pintan (y se crean aquí: arrastrar o doble clic,
  y Subrayar · Nota · Copiar · Buscar · Definir, con el Wikcionario como el móvil), los marcadores, páginas de verdad (a dos páginas si
  la ventana es ancha, o seguido), el índice con el % de cada capítulo, notas, buscar (sin mirar acentos), las notas al pie como tarjeta
  y «Volver a donde estabas». Los ajustes de lectura viajan con las mismas claves que el móvil (los de cada libro y los de siempre; el
  brillo y lo del PDF, no). Las posiciones se escriben como las escribe foliate en el móvil, letra por letra (probado contra el lector
  del móvil de verdad): antes, desde el capítulo 2, el ordenador abría un párrafo antes de donde ibas en el móvil, y al revés.
- **Hiedra**: tus juegos de la cuenta (los mismos de Hiedra del móvil), los de Steam instalados en el ordenador (se abren con Steam: sus
  horas las cuenta Steam y llegan por su API) y tus programas (los añades a mano; Hiedra cuenta el tiempo hasta que los cierras, en
  `tiempo/<juego>~<dispositivo>`). Y hace de **«Tu PC»** para Hiedra del móvil: su biblioteca, a qué juegas y abrir un juego desde el móvil
  (el protocolo `suite/` de Atrio del PC). Su presencia dice `tiempo: true`, y Hiedra del móvil (0.5.3) no vuelve a contar ese tiempo.
  Desde la 1.1.0, **el launcher del PC de jugar**: además de Steam (también el de Flatpak y sus otras bibliotecas), **Heroic** (Epic, GOG,
  Amazon y los que añades a mano, con sus portadas), **Lutris**, tus **carpetas de ROMs** (25 sistemas, con las mismas claves que en el
  móvil: una ROM es el mismo juego aquí y allí) y los **emuladores** (RetroArch con sus núcleos, Dolphin, PCSX2, DuckStation, PPSSPP,
  RPCS3, Ryujinx, Cemu, melonDS, mGBA y Flycast, normales o de Flatpak; cuál para cada sistema, en Ajustes → Emulación). Y el **modo
  sala**: a pantalla completa, para la tele y **el mando** (Xbox, PlayStation o Nintendo, con sus botones dibujados; también con teclado):
  Inicio con el juego grande arriba y filas (Seguir jugando, Favoritos, por tienda y por consola…), Biblioteca, En este PC, la página de
  cada juego con JUGAR, buscar con teclado en pantalla, y un menú para suspender o apagar. Al abrir un juego, Hiedra se aparta y vuelve
  sola al cerrarlo. Nada entra en tu cuenta solo por estar instalado: entra al jugarlo o al añadirlo (y si lo habías quitado, vuelve el
  mismo, con lo suyo). El tiempo de Heroic, Lutris, emuladores y programas se cuenta mientras el juego está abierto; el de Steam, Steam.
  Desde la 1.1.1, como «Tu PC», **hace capturas para el móvil** (de la pantalla entera, a su tamaño: con Spectacle en KDE, grim, GNOME o
  `import` en X11; en el Mac, `screencapture`), guarda una copia en Imágenes/Hiedra/<juego> y el móvil la recibe tal cual; y **prepara
  el streaming con Steam Link** (abre Steam si no lo está y el juego que pidas). Ajustes → Este PC dice si se permite cada cosa.

## Lo que todavía no hacen (para no engañar)

- **Azabache**: las casillas de películas y series «Crítica», «Dónde verla» y «Progreso» (se colocan, pero se dibujan solo en el móvil), las
  imágenes de tus archivos en una maqueta, las reacciones de tu pareja y «Lo último de tu pareja», y los próximos episodios. («Descubrir»
  no está a propósito: se quitó de Azabache.)
- **Cobalto**: compartir con tu pareja (la pareja ya está en la cuenta, pero en el ordenador aún no hay pantallas para ello).
- **Marfil**: los subrayados de los PDF (el móvil los guarda sobre la capa de texto de pdf.js), leer en voz alta y traducir. Los ajustes
  que cambies en otro dispositivo se aplican aquí la próxima vez que abras el libro, no al momento. Otros formatos (MOBI, FB2, TXT): en
  el móvil.
- **Hiedra**: las órdenes de equipo del móvil (traer Hiedra delante, volver a buscar, un aviso en pantalla, apagar) responden que todavía
  no. En el Mac, sin el permiso de «Grabación de pantalla», la captura sale solo con el fondo (y no hay forma de notarlo). En el Mac no se sabe con seguridad cuándo
  se cierra un juego de Steam (Hiedra no vuelve sola). Sin carpeta de instalación de Heroic o Lutris, ese juego no cuenta tiempo. Los
  emuladores de Flatpak necesitan permiso para leer tus carpetas de ROMs (Flatseal). El mando se ha probado sin un mando de verdad.
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

Versiones de Android a día de hoy: Hiedra 0.6.2, Marfil 0.14.2, Cobalto 0.4.2, Azabache 1.6.2. De escritorio: Azabache 1.2.0; Cobalto y Hiedra 1.1.1; Marfil 1.1.0.
