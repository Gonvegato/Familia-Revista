# Nut

La tele de la familia (IPTV), como Televizo: tus canales en directo con su guía y, si tu proveedor es un servidor de Xtream Codes, sus
películas y sus series. Hermana de [Jonsu](../azabache/HISTORIA.md), [Hathor](../mostaza/HISTORIA.md), [Bastet](../hiedra/HISTORIA.md),
[Seshat](../marfil/HISTORIA.md) y [Thot](../cobalto/HISTORIA.md): la misma interfaz «Revista», la misma cuenta y la misma firma; su paquete
es `com.gonzalo.cobre`. Un solo APK para el móvil, la tablet y la tele (Android TV y Google TV, con el mando).

**Nut no trae ningún canal ni ninguna lista**: es un reproductor. Pones tú tu lista M3U (una dirección o un archivo) o el login de tu
servidor de Xtream Codes (servidor, usuario y contraseña).


## 0.8.3 — los colores de la familia, de día y de noche

- **Las seis apps comparten colores**: de día, papel #F8F0D4 con texto negro; de noche, al revés (negro con texto #F8F0D4), siguiendo el
  modo del móvil o el que elijas en Apariencia. A cada app la distingue su acento: el de Nut, **granate oscuro (#5D1414)**. Seshat ocre, Jonsu
  ciruela, Bastet oliva, Thot pizarra azul, Hathor rosa viejo y Nut granate oscuro.
- **Una vez, sola**: lo que tuvieras elegido a mano en Apariencia (fondo de color, acento, «A tu manera») se quita para que todo quede así;
  viaja a tus otros dispositivos. Se puede volver a elegir lo que quieras.
- **«Poner los colores de Nut»**, arriba del todo en Ajustes → Apariencia: vuelve a estos colores de un toque (el tema y el tamaño del
  texto se quedan).
- **Icono y widgets, de día y de noche**: el icono tiene versión clara y oscura (`drawable-night`); cuál se ve depende del lanzador (muchos
  no cambian el icono con el modo oscuro: Android no tiene una forma oficial de hacerlo). Los widgets cambian solos con el modo, y sus
  vistas previas del selector también tienen las dos versiones.

## 0.8.2 — los widgets, también con los colores nuevos

- Las vistas previas de los widgets (lo que sale al elegir un widget en la pantalla de inicio) estaban hechas con los colores de antes:
  rehechas con los nuevos. Los widgets ya puestos se pintaban con los colores nuevos desde la versión anterior (los leen del tema).

## 0.8.1 — colores nuevos, apagados

- **Colores de serie nuevos**, sin tocar nada más de la dirección C: «Vino viejo»: vino muy oscuro (#2C1C1F), crema (#EADFD2) y caramelo apagado (#A9876A), igual de día y de noche. Elegidos entre tres propuestas sin colores brillantes.
- **Una vez, sola**: quien tenía los de serie de antes (fondo granate #3D0E19, texto #F1DCB5 y acento #D4A06A) pasa a los nuevos, y viaja a tus otros dispositivos. Lo que
  hubieras elegido tú a mano (otros colores, otro acento, un fondo de color, otra paleta) se queda como está. Si los ves iguales que antes,
  es que tenías algo elegido a mano: en Ajustes → Apariencia, quita «A tu manera» y elige la paleta «Vino viejo».
- **Icono** con los colores nuevos (`herramientas/familia-c/icono-c.py`) y **cartel de la tele** igual que el icono.

## 0.8.0 · La cara de Seshat, Bastet y Hathor (dirección C)

Nut pasa a la misma altura que Seshat, Bastet y Hathor: toda la app en una pantalla, el cartel de tu próximo partido, la racha de tus
equipos, lo que has visto esta semana, las fichas a doble página, imágenes propias de tus equipos con encuadre por dispositivo, icono «N.»
y widgets en C. ESPN, las listas M3U y Xtream, la guía, la calidad automática, el sonido en segundo plano, «Dónde verlo», tus equipos y
rivales, las selecciones y el mando de la tele no cambian.

- **Colores**: los de Nut (en «A tu manera»): fondo granate #3D0E19, letra crema #F1DCB5 y acento dorado #D4A06A. La cabecera de tinta
  sale crema con las letras granate. El dorado sobre el granate da 7,1:1 y el crema 12,3:1 (se leen tal cual); si eliges otro acento que no llegue al 4,5:1,
  el texto en acento se aclara u oscurece lo justo (`acentoLegible`); en los bloques va tal cual.
- **Una pantalla, cinco páginas**: la cabecera («NUT.» en grotesca, BUSCAR CANAL · + EQUIPOS · fecha · ⚙) que se pliega sin cortar ni dejar
  hueco, y una fila de pestañas: PORTADA · HOY · COMPETICIONES · TUS EQUIPOS · IPTV, que se pasan con el dedo. HOY son los partidos del
  día (la portada de antes, con su tira de días); IPTV es la «Tele» de antes (favoritos, directo, guía, listas, películas y series).
  Directo, Guía, Buscar, Listas y Ajustes van encima (y al volver, lo de la tele vuelve a IPTV); las fichas (un partido, un equipo, una
  competición, una película), encima de todo. Atrás vuelve a la portada. El widget de los partidos de hoy abre HOY; el de tu próximo
  partido, TUS EQUIPOS.
- **Portada**: el cartel de **tu próximo partido** (o el que se juega ahora: «EN DIRECTO · 67'» y el marcador) sobre el fondo de tu
  equipo (el que elijas) o el color de su club, con los dos escudos enfrentados en su paspartú, la competición, el día y la hora, el
  estadio y tu canal («EN MOVISTAR»); debajo, «VER EN TU CANAL →» (lo mismo que «Ver» de «Dónde verlo»: el primer canal de tus grupos,
  en la calidad que toque) o «ELEGIR CANAL →» si aún no tienes. Sin equipos, el cartel te invita a elegirlos. Luego las cifras (visto
  hoy · en juego · días seguidos), **«La racha de tus equipos»** (los cinco últimos de cada uno en bloques: V verde, E gris, D rojo, con
  su escudo y su puesto en la liga), **«Esta semana»** (las horas que has visto en Nut, en barras que se tocan, con el total), **«Hoy»**
  (los partidos de hoy, lo que está en juego primero) y **«Tus equipos»** en grande.
- **Lo que ves**: el reproductor apunta, día a día, el tiempo que suena con la imagen delante (canales, partidos, películas y series); se
  guarda en el dispositivo y en tu cuenta (`cobre_visto/<dispositivo>`, campos `d<día>` en ms) y «Esta semana» suma tus dispositivos.
  Días seguidos con la regla de la familia (un minuto; hasta hoy o hasta ayer). Lo que suena en segundo plano con la pantalla apagada no
  cuenta.
- **Listas**: escudos más grandes (30 dp en los partidos, 26 dp en las tablas), goles y puntos en grotesca.
- **Ficha de un partido a doble página**: la apertura con el fondo del equipo de casa (o su color) y un velo oscuro, los escudos
  enfrentados con su paspartú, la etiqueta del estado (PRÓXIMO · MAÑANA · 21:00 / EN DIRECTO · 67' / FINAL) y el marcador enorme;
  «VER EN TU CANAL →»; y por secciones numeradas, todo lo de antes: 01 El partido · 02 Dónde verlo · 03 Alineaciones · 04 Estadísticas ·
  05 Clasificación · 06 Cara a cara.
- **Ficha de un equipo** igual: su fondo, el escudo con paspartú, su liga y su puesto, el nombre enorme, «SU PRÓXIMO PARTIDO →»; 01 Su
  racha (los cinco últimos y los jugados) · 02 Lo que viene · 03 Plantilla · 04 Clasificación. Arriba, + Seguir / Rival / IMÁGENES.
- **IMÁGENES de un equipo**: su escudo (el tuyo, de tu móvil, o las propuestas de ESPN: el de siempre y el de fondo oscuro si lo tiene) y
  su fondo (de tu móvil; ESPN no da fondos de equipos), con «Encuadrar» (qué parte se ve, solo en este dispositivo) y «Quitar». Las
  imágenes van con tu cuenta (`cobre_equipos/<id>`: `esc` y `fimg`). El **paspartú** es el de la familia: crema fijo #F4EBD6, oscuro
  #141210 si el escudo es muy claro (luminancia ≥ 0,78).
- **Icono «N.» FÚTBOL** (adaptable, monocromo y el cartel de la tele) y **widgets en C** (titulares en grotesca, sin el reborde fino).

### Capturas de la 0.8.0

En `capturas/c/` (`CapturasCTest`, con `-Proborazzi.test.record=true`; con los colores de Nut, de noche y con la letra al 130 %): la
portada en directo, con el próximo partido y fondo, sin equipos y con un día de la semana tocado; el plegado de la cabecera; cada página;
la ficha de un partido en directo, próximo con fondo y terminado; la de un equipo; IMÁGENES y el encuadre.

## 0.2.0

Los favoritos, lo primero: es donde se está casi todo el rato. Una página propia, «Favoritos» (la primera del menú), con solo tus
canales favoritos de todas tus listas, uno debajo de otro y en grande (lo que echan ahora, con lo que va, y lo siguiente; a dos
columnas en la tablet y en la tele). En la portada, tus favoritos también así, todos y deslizando hacia abajo, en vez de la fila de
tarjetas; «Seguir viendo» ya no sale en la portada (lo empezado de películas y series sigue en sus páginas) y tus listas quedan al final.

## 0.1.1

Con el texto grande del móvil no se leía el nombre de ningún canal: salía en una sola línea, enorme y cortado («ES: MOVISTAR LA…»), y
el número se partía en dos («2169» / «8»). Ahora el nombre ocupa hasta tres líneas, el número va en una sola y tan ancho como haga
falta, el logo es algo más pequeño; lo mismo en la lista de canales del reproductor y en su título. Captura nueva con la letra al 130 %.

## Capturas

Hechas en el ordenador con listas, canales, guía y videoclub de mentira (`CapturasTest`, solo con `-Proborazzi.test.record=true`), en el
móvil, en la tablet y en la tele (1920×1080, en modo televisión), en claro y en oscuro (en `capturas/` van las del móvil en claro, las de la
tele en oscuro y unas de la tablet; las demás salen igual al volver a hacerlas):
portada, Directo, Guía, Películas, Series, la ficha de una película y la de una serie, Listas (y añadir una, con la contraseña tapada),
Ajustes y el reproductor (en directo, la lista de canales encima del vídeo, «Abrir con otro reproductor», una serie y el episodio
siguiente).

## En el ordenador (Linux y Mac): Nut 1.0.0

**Nut también en el ordenador**, como sus hermanas (`escritorio/cobre`, ver [ESCRITORIO](../ESCRITORIO.md)): la misma cuenta (entra sola
si otra app de la familia de ese ordenador ya ha entrado), la apariencia de tu Nut del móvil (con el acento cobre si aún no has elegido
otra), se actualiza sola desde tu kDrive (`Nut-<versión>-linux.tar.gz` / `-linux.deb` / `-mac.dmg`) y su icono es el mismo (la tele de
cobre de `herramientas/cobre/icono-cobre.py`, recortada para el ordenador por `herramientas/escritorio/iconos.py`). Las versiones del
ordenador llevan su propio número: empieza en la 1.0.0.

- **El mismo código para lo que no es pantalla**: el módulo `iptv/` tal cual (sin copiarlo): las listas M3U, Xtream Codes, la guía XMLTV a
  trozos, emparejar canales y guía, el almacén de cada lista, lo visto (`Visto`: el 90 %, por dónde seguir, «Seguir viendo») y lo que va a
  la cuenta (`DatosCuenta`). Así el ordenador escribe lo mismo que el móvil, campo a campo: tus favoritos y su orden (`cobre_favoritos`),
  los grupos escondidos (`cobre_grupos`), el último canal (`cobre_estado/ultimo`, y «al abrir, el último canal» también aquí), lo visto
  (`cobre_progreso`), lo que eliges (`ajustes/cobre_reproductor`, `cobre_guia`, `cobre_directo`, con los mismos nombres y valores; lo que es
  solo del móvil —`ahorro`, `soloWifi`, `externo`— ni se lee ni se toca) y la apariencia (`ajustes/cobre`). Tus listas, solo en
  `/claves/<uid>/cobre_listas`. Probado contra la cuenta de verdad con «el móvil» escribiendo con su formato (`CuentaTest`).
- **En el ordenador, caché cifrada**: los canales (y el archivo de una lista de archivo, y las guías que dice la lista) van cifrados con
  AES (CTR, a trozos, como en el móvil) con una clave al azar de este ordenador, guardada a su vez con la del llavero del sistema (el
  Llavero del Mac; el Secret Service en Linux o, si no hay, un archivo que solo lee tu usuario). Sin tu llavero, no se leen. Las listas de
  archivo son del ordenador donde elegiste el archivo (`cobre-linux-…` / `cobre-mac-…`); en los demás, «en otro dispositivo».
- **Directo, Guía, Películas, Series, Buscar y Ajustes**, como en la tablet y en la tele: los grupos a la izquierda y los canales con su
  logo, lo de ahora (con lo que va) y lo siguiente; la parrilla de tres horas; el videoclub con «Seguir viendo», «Lo último» y las
  categorías del servidor; las fichas; Listas (Xtream —se comprueba antes de guardarlo, y «Comprobar» dice si está activa, cuándo caduca y
  cuántas conexiones lleva—, una dirección M3U —con «añadirlo como login» si es la de un servidor de Xtream— o un archivo M3U de este
  ordenador; la guía XMLTV y el user-agent de cada una; contraseñas y direcciones tapadas, con «Ver»), la guía (cada cuánto y cuántos días,
  y bajarla ya), el reproductor, la cuenta, la apariencia y la versión. Con el teclado y el mando (como Hathor del ordenador).
- **Se ve con mpv** (`sudo pacman -S mpv` en CachyOS y Arch, `brew install mpv` en el Mac), con el mismo código de hablar con mpv que
  Hathor del ordenador (copiado: lo de Hathor que no sirve aquí, sus pistas y su elector, se queda en Hathor). La dirección (con tu
  usuario y tu contraseña) y las cabeceras van por su socket, en una carpeta que solo abre tu usuario, nunca en su línea de órdenes; sin
  yt-dlp y sin que mpv guarde nada. En la ventana de mpv, Nut pone sus teclas (su `input.conf`, que mpv le cuenta por el socket como
  `client-message`): ↑ ↓, Re Pág / Av Pág, los botones de canal y la cruceta del mando cambian de canal (se puede dar la vuelta: arriba el
  anterior), los números van al canal por su número, Intro o A abre la lista de canales encima (con lo que echan en cada uno), «i» dice
  qué se emite y qué viene después (y sale solo al cambiar de canal), Esc sale de la pantalla completa. Si un canal se corta, vuelve a
  conectar solo (hasta seis veces, esperando cada vez el doble; si nunca llegó a verse, dos). En las películas y los episodios, sigue donde
  lo dejaste (lo más reciente entre lo de aquí y lo de la cuenta), apunta por dónde vas cada 10 segundos y al parar, visto al 90 % y el
  episodio siguiente en 8 segundos. La imagen como la elegiste (ajustar, llenar, zoom, 16:9, 4:3), el audio y los subtítulos en tu idioma.
  Un canal con DRM, no (lo dice). Probado con mpv de verdad (`MpvDeVerdadTest`: canales y vídeos hechos con ffmpeg, un servidor con las
  direcciones de Xtream, las teclas pulsadas en la ventana de mpv, el corte y la vuelta a conectar, seguir donde lo dejaste en el móvil y
  el episodio siguiente).
- Capturas: `CapturasTest` (con `-Dcapturas=<carpeta>`), con un servidor de Xtream de mentira: Directo (claro y oscuro), la Guía,
  Películas, Series, las fichas, Listas (y añadir una, con la contraseña tapada), el reproductor y la banda de mpv con la lista de canales.
  Unas cuantas en `capturas/` (`cobre-escritorio-*`).
- Lo que aún no, en el ordenador: lo ya emitido y grabar (como en el móvil), «Abrir con otro reproductor» (aquí todo va con mpv) y la sala
  a pantalla completa. Las cabeceras de un canal van a todo lo que mpv pide para ese canal (en el móvil, solo a su servidor). Con un canal
  que no se ve, mpv no dice el código del error: el aviso es uno para todo. `cobre_actualizador` no se usa (en el ordenador, como en sus
  hermanas, se actualiza sola siempre). El mando y un proveedor de IPTV de verdad, sin probar; el Mac solo lo compila GitHub.

## 0.1.0

La primera.

- **Tus listas**: Xtream Codes (servidor, usuario y contraseña; se comprueban antes de guardarlas y se ve si la cuenta está activa), una
  dirección M3U (si pegas la de un servidor de Xtream, `…/get.php?username=…&password=…`, Nut te ofrece ponerla como login, con su
  videoclub y su guía) o un archivo M3U de este dispositivo. A cada una le puedes poner una guía XMLTV tuya y el user-agent que pida tu
  proveedor. Van **solo** en la parte secreta de tu cuenta (`/claves/<uid>/cobre_listas`): las tienes en todos tus dispositivos y ni tu
  pareja las ve. Las de archivo se quedan en el dispositivo donde elegiste el archivo (en los demás salen como «en otro dispositivo»).
- **Directo**: los grupos de la lista (en el orden de tu proveedor), con tus favoritos primero; cada canal con su número, su logo, lo que
  echan ahora (con lo que va) y lo siguiente. ★ para tus favoritos, esconder los grupos que no quieres ver, y el orden de los canales
  (como la lista, por número o por nombre).
- **Guía**: la parrilla, con una ventana de tres horas (dos en el móvil) que se mueve media hora a media hora. La guía sale de lo que diga
  la lista (`url-tvg`), de la del servidor de Xtream (`xmltv.php`) o de la tuya; se baja sola cada 12 horas (con datos del móvil, solo si
  la pides), y se queda solo con los programas de tus canales y unos días (4, o los que elijas). Qué canal de la guía es cada uno: por su
  `tvg-id` y, si no, por su nombre (sin acentos, sin «HD», sin el «ES:» del principio…). Con Xtream y sin guía, lo de ahora y lo siguiente
  salen de la guía del propio servidor (`get_short_epg`).
- **Películas y Series** (con Xtream): las categorías del servidor y «Lo último», sus pósteres, la ficha (de qué va, quién sale, cuánto
  dura) y verla. En las series, sus temporadas y sus episodios, con lo visto de cada uno y «Seguir» con el que toca.
- **El reproductor** (Media3, como Hathor, con el FFmpeg de Jellyfin para el AC3 y el DTS que tu aparato no sepa): MPEG-TS, HLS y DASH (una
  dirección sin extensión que resulta ser HLS se vuelve a probar como HLS). En directo, arriba y abajo del mando (y los botones de canal)
  cambian de canal (puedes darle la vuelta: arriba el anterior), los números van al canal por su número, OK abre la lista de canales
  encima del vídeo (con lo que echan en cada uno) y la banda dice qué se emite y qué viene después. Si se corta, vuelve a conectar solo
  (hasta seis veces, esperando cada vez un poco más), y si se queda atrás del directo, vuelve al directo. Audio, subtítulos y la imagen
  (ajustar, llenar, zoom, 16:9, 4:3). En las películas y las series, sigue donde lo dejaste (aquí o en otro dispositivo), visto al 90 % y
  el episodio siguiente en 8 segundos. Con datos del móvil, la calidad más baja que dé el canal (si da varias). Las cabeceras que pide un
  canal (user-agent, referer) van solo a su servidor. Un canal con DRM (Widevine…) no se puede ver aquí: lo dice.
- **«Abrir con otro reproductor»** (VLC, Just Player, mpv…): **le pasa a esa app la dirección de lo que ves, que lleva tu usuario y tu
  contraseña**; por eso Nut lo pregunta antes, cada vez.
- **Todo en la cuenta** (campo a campo, en todos tus dispositivos; `nube/ESQUEMA.md`): tus favoritos y su orden (`cobre_favoritos`), los
  grupos escondidos (`cobre_grupos`), el último canal (`cobre_estado/ultimo`; si quieres, Nut se abre con él, como una tele), lo visto de
  cada película y episodio y «Seguir viendo» (`cobre_progreso`), lo que eliges (`ajustes/cobre_reproductor`, `cobre_guia`,
  `cobre_directo`, `cobre_actualizador`) y la apariencia (`ajustes/cobre`). En `/users` no hay nada secreto: ni direcciones, ni usuarios,
  ni contraseñas.
- **En el dispositivo, solo caché** (se vuelve a bajar): los canales (cifrados: sus direcciones llevan tu contraseña), el videoclub y la
  guía. Todo se baja y se lee a trozos: una lista de cientos de miles de entradas o una guía de más de 100 MB (también `.xml.gz`) no se
  tienen nunca enteras en memoria. Sin copia de seguridad de Android (`allowBackup="false"`).
- **En la tele**: en el menú de Android TV (con su cartel), sin pantalla táctil obligatoria, todo con el mando y el foco siempre visible
  (el marco del acento de la familia); en cada página, el foco empieza en lo primero. A dos columnas en la tele y en la tablet.
- La cuenta de la familia (entras una vez, en Jonsu o en cualquiera), la apariencia de la revista (con el acento cobre), se actualiza
  sola desde tu kDrive (`Nut-<versión>.apk` en «versiones») y va firmada con la clave de la familia (sin linaje: nace con ella).

Lo que ves en Nut **no pasa a la biblioteca de Hathor** (ni a Jonsu): el videoclub de un servidor de Xtream no trae ids de Stremio, y
con TMDB se podría sacar el de IMDb de algunas películas, pero meterlas en `titulos`/`progreso`/`tiempo` con las reglas de Hathor (10
minutos en una sesión, visto al 90 %, lo borrado no vuelve, los episodios con su número de verdad) sin equivocarse no es seguro todavía.
Se queda en los nodos de Nut.

### Por dentro

- `iptv/` (Kotlin puro, sin Android: lo usa tal cual Nut del ordenador): las listas M3U (`#EXTINF` con `tvg-id`, `tvg-name`, `tvg-logo`,
  `group-title`, `tvg-chno`, `tvg-shift`, `catchup`, `catchup-days`, `catchup-source`, `radio`; `#EXTGRP`; `#EXTVLCOPT`; `#KODIPROP`; las
  cabeceras detrás de la barra), el cliente de la API de Xtream (`player_api.php`: la cuenta, `get_live_categories`, `get_live_streams`,
  `get_vod_categories`, `get_vod_streams`, `get_vod_info`, `get_series_categories`, `get_series`, `get_series_info`, `get_short_epg`; las
  direcciones de `/live/`, `/movie/`, `/series/` y `/timeshift/`), las guías XMLTV (con un lector de XML propio, a trozos; las horas con
  su zona, y sin zona, UTC, como dice el DTD), la guía guardada por canal, emparejar canales y guía, el almacén de cada lista y lo que va a
  la cuenta. La API de Xtream no tiene una especificación oficial: se ha seguido lo que hacen los paneles y lo que esperan los
  reproductores, contrastado con varias referencias de ahora (los tipos de `crispy-xtream`, la biblioteca `@iptv/xtream-api`, la
  documentación de la comunidad) y con lo que es distinto según el panel (números que llegan como texto, `episodes` como objeto o como
  lista, la guía corta en base64 o en texto).
- Las pruebas: `iptv` (43, con listas, respuestas y guías con la forma de las de verdad, y enormes: 150 000 canales, 120 000 películas y
  más de 200 MB de guía en 256 MB de memoria) y las de Nut contra el emulador de Firebase con las reglas de verdad (`CuentaTest`,
  `SecretosTest`, `AjustesCuentaTest`).

### Lo que todavía no

- Lo ya emitido (catchup / timeshift): se lee de las listas y se sabe construir la dirección de Xtream, pero aún no hay botón para verlo.
- Los widgets (los de Hathor no se han traído todavía).
- Grabar, el control parental y varias listas mezcladas en una sola vista de canales.
- Nut no se ha probado con un proveedor de IPTV de verdad ni en una tele de verdad: todo, contra servidores y guías de mentira con la
  forma de los de verdad, y las pantallas, en capturas hechas en el ordenador.
- Para que Nut entre sola con la sesión de una hermana (y al revés), las hermanas tienen que llevar la versión de `familia/` que ya conoce
  a Nut: hasta que salgan versiones nuevas de las demás, en Nut se entra una vez a mano (Ajustes → Cuenta).
