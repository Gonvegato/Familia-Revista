# Las versiones de escritorio (Linux y Mac)

**Jonsu, Thot, Bastet, Seshat, Hathor y Nut para Linux y Mac**, en `escritorio/`. Son ventanas a la misma cuenta que las del móvil: todo lo
tuyo está en la cuenta (Firebase, el proyecto de siempre) y en el ordenador solo hay una caché para funcionar sin conexión y lo que es
de ese ordenador (dónde está un archivo, la ventana). Lo que haces en una lo ves al momento en las demás, en el ordenador y en el móvil.

## Instalar

- **Mac (Apple Silicon)**: `Azabache-1.0.1-mac.dmg` (y las demás). Abre el .dmg y arrastra la app a Aplicaciones. No está firmada por
  Apple (esa firma es de pago), así que la primera vez macOS la bloquea: ábrela una vez (avisa y no la abre), ve a **Ajustes del Sistema →
  Privacidad y seguridad → Seguridad**, pulsa **«Abrir igualmente»** (sale durante una hora después del aviso) y pon tu contraseña. Desde
  entonces se abre con doble clic. En los macOS de ahora (Sequoia y los siguientes) el truco de clic derecho → Abrir ya no vale.
- **Linux** (ordenadores x86-64, los de siempre; no ARM):
  - **CachyOS, Arch y cualquier otro**: `Azabache-1.0.1-linux.tar.gz`. Se descomprime y se ejecuta su `instalar.sh` (sin sudo):
    `tar xzf Azabache-1.0.1-linux.tar.gz && ./instalar.sh`. Queda en `~/.local/share/familia-apps/Azabache`, con su icono en el menú
    de aplicaciones. Con `./instalar.sh --al-iniciar` se abre sola al entrar en tu sesión (Bastet, ya en la sala: el launcher del PC);
    `./instalar.sh --quitar` la desinstala (lo tuyo está en la cuenta: no se toca).
  - **Ubuntu, Debian, Mint…**: también vale el `Azabache-1.0.1-linux.deb` (`sudo apt install ./Azabache-1.0.1-linux.deb`), pero así no
    se actualiza sola (la del .deb es del sistema): mejor el .tar.gz.
- **Hathor** y **Nut** ven las películas, las series y los canales con **mpv**, que se instala aparte: en CachyOS y Arch `sudo pacman -S mpv` (en Ubuntu o
  Debian `sudo apt install mpv`); en el Mac `brew install mpv` (con [Homebrew](https://brew.sh)). Si no está, lo dicen.
- **Entrar una vez**: entras en cualquiera de las seis y las demás de ese ordenador entran solas. La contraseña no se guarda: queda
  la sesión, en el llavero del sistema (el Llavero del Mac; en Linux, el Secret Service —GNOME Keyring, KWallet— si hay `secret-tool`, y si
  no, un archivo que solo lee tu usuario).
- **Se actualizan solas**: al abrirse y cada 6 horas, cada app mira tu kDrive («Mi carpeta/versiones»: `<App>-<versión>-mac.dmg` /
  `-linux.tar.gz`); si hay una versión nueva, la baja sin molestar, avisa con una banda abajo («lista: se pone sola al cerrar ·
  Reiniciar ahora») y se pone sola al cerrar la app. En el Mac el .dmg se monta sin enseñarlo y la app nueva se copia encima, sin la marca de
  cuarentena (es la misma app que ya abriste: no vuelve a pedir «Abrir igualmente»); en Linux la pone su `instalar.sh`.
  Hace falta tu kDrive puesto en Jonsu (Ajustes → Servicios). Los actualizadores de Android solo cogen `<App>-<versión>.apk`: no se
  confunden.

Los paquetes los hace GitHub (`.github/workflows/escritorio.yml`): las pruebas en Linux contra el emulador de Firebase, los `.deb` y
`.tar.gz` en Linux y los `.dmg` en un Mac de GitHub (Apple Silicon). Los artefactos de cada ejecución son los paquetes; se suben a tu
kDrive con `herramientas/escritorio/subir-escritorio.py`.

## Qué hace cada una

- **Jonsu**: tus fichas de la cuenta (los juegos y los libros son los nodos de Bastet y Seshat, sin copia; tus categorías —películas,
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
  Desde la 1.2.1, **las películas y las series son las de Hathor**, como en Jonsu 1.6.3 del móvil: en cuanto Hathor ha traído lo de
  Jonsu (la marca `azabache/a_mostaza`), «Películas» y «Series» leen sus títulos (`titulos/<ref>`, con el número de ficha de siempre,
  `azid`: tus sagas, tu diario y tus maquetas las siguen encontrando), los minutos de `tiempo/<ref>~*` y los episodios vistos de
  `progreso` (y, si no, los que marcaste en Jonsu); lo que valoras o cambias va a ese mismo nodo (`nota`, `crit`, `resena`, `fin`,
  `estado`, `az`…, solo lo que cambias), lo que añades es un título nuevo (`tmdb:<id>`, `tvmaze:<id>` o `azabache:<id>`) y quitar una la
  deja borrada en Hathor. Lo de antes (`azabache/items_<categoría>~<id>`) no se toca. La casilla «Progreso» se pinta también aquí (los
  episodios, o por dónde vas en una película). Las mismas reglas que el móvil, letra a letra (`MostazaTest`, con el código del móvil y el
  contrato que deja Hathor).
- **Thot**: el núcleo de los escritos (formato, sincronización, mezcla a tres bandas), la estructura, el texto (Markdown ligero,
  tipografía automática, deshacer), exportar e importar son **los mismos archivos** que compila Thot de Android (se copian de
  `cobalto/src` al compilar; las pruebas de la mezcla y de la estructura, también). Portada (palabras de hoy, racha, últimos días, seguir
  escribiendo), la obra con su índice (partes, capítulos, escenas) y el editor, fichas, notas, papelera, exportar (Word como libro o como
  manuscrito, EPUB, Markdown, texto), copias de seguridad completas en el ordenador (cada 15 min y al cerrar; las últimas 40) y lo que se
  publica para Jonsu y Seshat (`obras/`, `capitulos/`, `palabras/`).
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
- **Seshat**: tus libros de la cuenta y leerlos por donde ibas en el móvil. EPUB (leído como XML, sin reordenar: la posición que guarda es
  el mismo EPUB CFI que guarda foliate en el móvil, y la del móvil se abre aquí en el mismo párrafo), PDF (Apache PDFBox) y cómics (CBZ).
  Lo leído (`lectura/<kid>`), «Leyendo» y «Leído» con su fecha (las reglas del móvil), el tiempo de este ordenador, el reto del año, los
  archivos por tu kDrive (`Marfil/Libros/…`: se suben al añadirlos y se bajan al abrirlos) y los capítulos terminados de Thot.
  Desde la 1.1.0, **el lector como el del móvil**: tus subrayados y notas de la cuenta se pintan (y se crean aquí: arrastrar o doble clic,
  y Subrayar · Nota · Copiar · Buscar · Definir, con el Wikcionario como el móvil), los marcadores, páginas de verdad (a dos páginas si
  la ventana es ancha, o seguido), el índice con el % de cada capítulo, notas, buscar (sin mirar acentos), las notas al pie como tarjeta
  y «Volver a donde estabas». Los ajustes de lectura viajan con las mismas claves que el móvil (los de cada libro y los de siempre; el
  brillo y lo del PDF, no). Las posiciones se escriben como las escribe foliate en el móvil, letra por letra (probado contra el lector
  del móvil de verdad): antes, desde el capítulo 2, el ordenador abría un párrafo antes de donde ibas en el móvil, y al revés.
- **Hathor** (desde la 1.0.0): tus películas y series de la cuenta, y verlas, como Hathor del móvil (0.2.0) y con su misma lógica, letra
  a letra (el protocolo de los addons de Stremio, el elector de fuentes, las pistas de verdad, TMDB y las tres reglas son el mismo código,
  con las mismas pruebas): la portada (con tu clave de TMDB, la de una plataforma: destacado, «Seguir viendo», «Pendientes», tendencias,
  «Porque viste…», estrenos, lo que hay en cada plataforma…; sin ella, los catálogos de tus addons), Viendo · Pendiente · Visto ·
  Abandonado (con su orden y su filtro, que viajan), Explorar, Buscar, la ficha (a dos columnas: «Reproducir» con la mejor fuente para ti,
  tu biblioteca, lo que dice Jonsu, el reparto, dónde verla, los episodios con lo visto, «Más como esta») y todas sus fuentes, tus
  addons (instalar por la dirección, ordenar, configurar en el navegador, quitar) y Ajustes (Catálogo, Fuentes, Reproductor, Cuenta,
  Apariencia, Versión). Todo es de la cuenta: `titulos/<ref>`, `progreso/<vídeo>`, `tiempo/<ref>~<dispositivo>` (en el ordenador,
  `mostaza-linux-…` o `mostaza-mac-…`), `ajustes/mostaza_*`; tus addons y tu clave de TMDB, solo en `/claves` (ni tu pareja los lee).
  Lo que ves aquí lo sigues en el móvil y al revés. Con el teclado (las flechas mueven el foco, Intro pulsa, Esc vuelve, Ctrl/⌘+F busca,
  Ctrl/⌘+1…8 las secciones) y con el mando (como en Bastet: A pulsa, B vuelve, Y busca, los gatillos de arriba cambian de sección).
  **Se ve con mpv** (en CachyOS y Arch, `sudo pacman -S mpv`; en el Mac, `brew install mpv`; si no está, Hathor dice cómo ponerlo y
  ofrece el reproductor del sistema, que no cuenta nada): Hathor lo abre en su ventana y habla con él por su socket (su IPC de JSON),
  así que cuenta lo visto de verdad (nada entra en tu biblioteca hasta 10 minutos en una sesión; visto al 90 %; el tiempo por día),
  sigue donde lo dejaste (aquí o en el móvil), mira las pistas de verdad del archivo (pone el audio en tu idioma y, si hace falta,
  subtítulos en español; si la que eligió sola no trae tu primer idioma, prueba otra), reconoce el vídeo de aviso del debrid y pasa sola a
  otra, propone otra fuente si se corta y pone el episodio siguiente. Abajo, mientras mpv tiene algo puesto, una banda con lo que se ve, por
  dónde va, pausa, saltar, el audio, los subtítulos, otra fuente y cerrar. La dirección de la fuente (que lleva la clave de tu debrid) y sus
  cabeceras van a mpv por su socket, nunca por su línea de órdenes, y mpv no guarda nada (sin su «seguir donde lo dejé» y sin yt-dlp).
  **Torrents**, con el motor de la familia (`escritorio/torrent`: los mismos archivos que el del móvil, con libtorrent4j y su biblioteca
  de Linux x86-64 o del Mac con Apple Silicon): se ven mientras se bajan; si no se puede cargar, solo las fuentes directas o de tu debrid.
- **Nut** (desde la 1.0.0): la tele de la familia (IPTV) en el ordenador, como Nut del móvil (0.1.0) y con su mismo código para todo lo
  que no es pantalla: **el módulo `iptv/` tal cual** (las listas M3U, el cliente de Xtream Codes, la guía XMLTV a trozos, emparejar canales y
  guía, el almacén de cada lista y lo que va a la cuenta). Nut no trae ningún canal ni ninguna lista: pones tú la tuya (Ajustes → Listas:
  un login de Xtream Codes —se comprueba antes de guardarlo, y «Comprobar» dice si la cuenta está activa y cuándo caduca—, una dirección
  M3U —si es la de un servidor de Xtream, ofrece ponerla como login— o un archivo M3U de este ordenador; a cada una, su guía XMLTV y el
  user-agent que pida tu proveedor; las contraseñas y las direcciones, tapadas, con «Ver»). **Directo** (los grupos, con tus favoritos
  primero, y cada canal con su número, su logo, lo que echan ahora con lo que va y lo siguiente; ★, subir y bajar favoritos, esconder
  grupos), **Guía** (la parrilla de tres horas, media hora a media hora), **Películas** y **Series** (con Xtream: «Seguir viendo», «Lo
  último», las categorías del servidor, la ficha, las temporadas con lo visto de cada episodio), **Buscar** (canales, películas y series) y
  Ajustes (Listas, Guía, Reproductor, Cuenta, Apariencia, Versión). Todo es de la cuenta, campo a campo, con los nombres del móvil
  (`cobre_favoritos`, `cobre_grupos`, `cobre_estado/ultimo`, `cobre_progreso`, `ajustes/cobre_reproductor`, `cobre_guia`, `cobre_directo` y
  la apariencia en `ajustes/cobre`); tus listas, **solo en `/claves/<uid>/cobre_listas`** (ni tu pareja las lee). En el ordenador, los
  canales, el videoclub y la guía son caché, y lo que lleva direcciones va **cifrado** (AES, con una clave de este ordenador guardada con
  la del llavero del sistema). Las listas de archivo son del ordenador donde está el archivo (`cobre-linux-…` / `cobre-mac-…`).
  **Se ve con mpv**, como en Hathor (la dirección —que lleva tu usuario y tu contraseña— y las cabeceras del canal van por su socket, nunca
  por su línea de órdenes; sin yt-dlp, y mpv no guarda nada). En la ventana de mpv, Nut pone sus teclas: ↑ ↓ (y Re Pág / Av Pág, y la
  cruceta del mando) cambian de canal, los números van a un canal, Intro (o A) abre la lista de canales con lo que echan en cada uno, «i»
  dice qué se emite y qué viene después (sale solo al cambiar de canal). Si un canal se corta, vuelve a conectar solo (hasta seis veces,
  esperando cada vez un poco más). En las películas y los episodios, sigue donde lo dejaste (aquí o en otro dispositivo), apunta por dónde
  vas en la cuenta, visto al 90 % y el episodio siguiente en 8 segundos. Abajo, la banda del reproductor (el canal, cambiar, la lista de
  canales, volver a conectar, audio, subtítulos, cerrar). Con el teclado y el mando como Hathor (Ctrl/⌘+1…6 las secciones; con un canal
  puesto, Re Pág / Av Pág cambian de canal también desde la ventana de Nut).
- **Bastet**: tus juegos de la cuenta (los mismos de Bastet del móvil), los de Steam instalados en el ordenador (se abren con Steam: sus
  horas las cuenta Steam y llegan por su API) y tus programas (los añades a mano; Bastet cuenta el tiempo hasta que los cierras, en
  `tiempo/<juego>~<dispositivo>`). Y hace de **«Tu PC»** para Bastet del móvil: su biblioteca, a qué juegas y abrir un juego desde el móvil
  (el protocolo `suite/` de Atrio del PC). Su presencia dice `tiempo: true`, y Bastet del móvil (0.5.3) no vuelve a contar ese tiempo.
  Desde la 1.1.0, **el launcher del PC de jugar**: además de Steam (también el de Flatpak y sus otras bibliotecas), **Heroic** (Epic, GOG,
  Amazon y los que añades a mano, con sus portadas), **Lutris**, tus **carpetas de ROMs** (25 sistemas, con las mismas claves que en el
  móvil: una ROM es el mismo juego aquí y allí) y los **emuladores** (RetroArch con sus núcleos, Dolphin, PCSX2, DuckStation, PPSSPP,
  RPCS3, Ryujinx, Cemu, melonDS, mGBA y Flycast, normales o de Flatpak; cuál para cada sistema, en Ajustes → Emulación). Y el **modo
  sala**: a pantalla completa, para la tele y **el mando** (Xbox, PlayStation o Nintendo, con sus botones dibujados; también con teclado):
  Inicio con el juego grande arriba y filas (Seguir jugando, Favoritos, por tienda y por consola…), Biblioteca, En este PC, la página de
  cada juego con JUGAR, buscar con teclado en pantalla, y un menú para suspender o apagar. Al abrir un juego, Bastet se aparta y vuelve
  sola al cerrarlo. Nada entra en tu cuenta solo por estar instalado: entra al jugarlo o al añadirlo (y si lo habías quitado, vuelve el
  mismo, con lo suyo). El tiempo de Heroic, Lutris, emuladores y programas se cuenta mientras el juego está abierto; el de Steam, Steam.
  Desde la 1.1.1, como «Tu PC», **hace capturas para el móvil** (de la pantalla entera, a su tamaño: con Spectacle en KDE, grim, GNOME o
  `import` en X11; en el Mac, `screencapture`), guarda una copia en Imágenes/Hiedra/<juego> y el móvil la recibe tal cual; y **prepara
  el streaming con Steam Link** (abre Steam si no lo está y el juego que pidas). Ajustes → Este PC dice si se permite cada cosa.

## Lo que todavía no hacen (para no engañar)

- **Jonsu**: «Ver en Hathor» (ya hay Hathor para el ordenador, pero la ficha de Jonsu aún dice que no y no la abre) y, en la vista de tu pareja, sus películas y series
  son aún las de antes (como en el móvil). Las casillas de películas y series «Crítica» y «Dónde verla» (se colocan, pero se dibujan solo en el móvil), las
  imágenes de tus archivos en una maqueta, las reacciones de tu pareja y «Lo último de tu pareja», y los próximos episodios. («Descubrir»
  no está a propósito: se quitó de Jonsu.)
- **Thot**: compartir con tu pareja (la pareja ya está en la cuenta, pero en el ordenador aún no hay pantallas para ello).
- **Seshat**: los subrayados de los PDF (el móvil los guarda sobre la capa de texto de pdf.js), leer en voz alta y traducir. Los ajustes
  que cambies en otro dispositivo se aplican aquí la próxima vez que abras el libro, no al momento. Otros formatos (MOBI, FB2, TXT): en
  el móvil.
- **Bastet**: las órdenes de equipo del móvil (traer Bastet delante, volver a buscar, un aviso en pantalla, apagar) responden que todavía
  no. En el Mac, sin el permiso de «Grabación de pantalla», la captura sale solo con el fondo (y no hay forma de notarlo). En el Mac no se sabe con seguridad cuándo
  se cierra un juego de Steam (Bastet no vuelve sola). Sin carpeta de instalación de Heroic o Lutris, ese juego no cuenta tiempo. Los
  emuladores de Flatpak necesitan permiso para leer tus carpetas de ROMs (Flatseal). El mando se ha probado sin un mando de verdad.
- **Hathor**: ver los archivos de tu kDrive («Tu kDrive», que está llegando al móvil; en `Addons.fuentesExtra` está el sitio donde
  entrará como una fuente más), «Abrir con otro reproductor» con lo visto (solo mpv cuenta; con IINA en el Mac no se puede saber por dónde
  vas), los widgets, «Valorar en Jonsu» que abra su ficha (abre Jonsu), traer lo de Jonsu a Hathor (lo hace Hathor del móvil, una
  vez: aquí se lee lo que ya está), la conexión medida a la vez que en el móvil (aquí se mide con lo que baja mpv) y los subtítulos de un
  tamaño o color que se cambian con algo puesto (valen para lo siguiente). Los torrents en el Mac con procesador Intel y en Linux ARM.
  mpv y el mando se han probado sin pantalla ni mando de verdad (mpv sí, de verdad, con `--vo=null`), y el Mac solo lo compila GitHub.
- **Nut**: lo ya emitido (catchup) y grabar, como en el móvil; «Abrir con otro reproductor» (aquí todo va con mpv); la sala a pantalla
  completa para la tele (Nut es una ventana con la columna, como Hathor). Las cabeceras de un canal van a todo lo que pide mpv para ese
  canal (en el móvil, solo a su servidor). Con un canal que no se ve, mpv no dice el código del error (401, 404…): el aviso es uno para
  todo. Probado con mpv de verdad (sin pantalla, con canales y vídeos hechos aquí) y contra la cuenta de verdad, no con un proveedor de
  IPTV de verdad ni con un mando de verdad; el Mac solo lo compila GitHub.
- Las letras de Google Fonts que se eligen en el móvil: aquí van las de la app (Atrio y Vitrina) en su lugar.

## Por dentro

- `escritorio/familia`: lo común. La cuenta por app sobre el núcleo (`LaFamilia`: escucha en directo mientras la app está abierta y sube lo
  pendiente al cerrar), el llavero (`Llavero`), lo delicado cifrado con una clave del llavero (`DiscoSeguro`), entrar una vez
  (`Sesion.comparte()` / `adopta()` del núcleo: se comparte la sesión, nunca la contraseña), portadas con el mismo hash que en Android
  (Skia: 600×900, JPEG 85), kDrive, el actualizador, la ventana (tamaño y sitio recordados) y el aspecto de revista (las paletas, las
  formas y los componentes de Android; la apariencia de `ajustes/<app>`, la misma que la de la app en el móvil).
- `escritorio/torrent`: el motor de torrents de Hathor (los mismos archivos que `torrent/src/main`, copiados al compilar; lo de Android
  —la red, el disco y su constructor con un Context— aquí es del ordenador). La biblioteca nativa sale de su .jar a la carpeta de Hathor.
- `iptv/` (fuera de `escritorio/`: es el mismo módulo del móvil): lo de Nut que no es pantalla, en Kotlin puro. Va también con
  `-PsoloEscritorio`.
- `escritorio/json`: el `org.json` de Android (AOSP, Apache 2.0), para que el núcleo de Thot se comporte igual en los dos.
- Carpetas: en el Mac `~/Library/Application Support/Familia/<App>`; en Linux `~/.local/share/familia/<app>`.
- Compilar sin el SDK de Android: `./gradlew -PsoloEscritorio test`, `… :escritorio:<app>:run`, `… packageDeb` (Linux, necesita `fakeroot`)
  o `… packageDmg` (solo en un Mac).
- Pruebas: contra el emulador oficial de Firebase con las reglas del repositorio (`herramientas/emulador.sh`); «el móvil» es otro
  dispositivo de la cuenta escribiendo con el formato de Android. Capturas: `-Dcapturas=<carpeta>` en las de cada app.

## Las reglas (no se negocian)

- **La cuenta es la única verdad**; el contrato es `nube/ESQUEMA.md`, `nube/ESCRITOS.md` y `nube/reglas.json`.
- **Sin copias entre apps**: un juego es uno, un libro es uno. Lo compartido se cambia **añadiendo** (nunca cambiando lo que significa un
  campo), se apunta en `ESQUEMA.md` y se prueba contra el emulador. Nunca `null`.

Versiones de Android a día de hoy: Bastet 0.6.2, Seshat 0.14.2, Thot 0.4.2, Jonsu 1.6.3, Hathor 0.2.0, Nut 0.1.0. De escritorio: Jonsu 1.2.1; Thot y Bastet 1.1.1; Seshat 1.1.0; Hathor 1.0.0; Nut 1.0.0 (las de escritorio empiezan en la 1.0.0 y llevan su propio número).
