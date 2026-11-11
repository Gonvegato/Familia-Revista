# Hathor

Las películas y las series de la familia, y verlas. Como Bastet es tu biblioteca de juegos y además juegas en ella, Hathor es tu
biblioteca de películas y series y además las ves en ella. Hermana de [Jonsu](../azabache/HISTORIA.md) (la revista: valorar),
[Bastet](../hiedra/HISTORIA.md) (juegos), [Seshat](../marfil/HISTORIA.md) (libros) y [Thot](../cobalto/HISTORIA.md) (escribir): la misma
interfaz «Revista», la misma cuenta y la misma firma; su paquete es `com.gonzalo.mostaza`.

Funciona como Stremio: habla el protocolo abierto de los **addons de Stremio** (catálogos, fichas, fuentes y subtítulos). Los catálogos y
las fichas salen de Cinemeta (el oficial de Stremio, el único que viene puesto) y de los que pongas tú; las fuentes, de tus addons de
fuentes, con tu Real-Debrid configurado en el suyo (esas dan enlaces directos, que Hathor reproduce al momento). Hathor no trae ningún
addon de fuentes ni de torrents: los pones tú.


## 0.7.12 — un torrent que se ve roto, desde cero

- Si un torrent se ve roto (bloques grises) en cualquier reproductor, también en VLC, lo que está mal es lo bajado en ese dispositivo
  (libtorrent lo da por bueno). Con un torrent, **«¿Se ve mal?»** ahora borra lo bajado de él en ese dispositivo y lo vuelve a bajar
  desde cero, en el mismo punto. Con las demás fuentes sigue cambiando de decodificador.

## 0.7.11 — si el decodificador de Android no puede, se vuelve solo

- Con «¿Se ve mal?» puesto para un formato, si el decodificador de Android no puede con un vídeo, Hathor lo olvida y vuelve a abrirlo con
  el del aparato en el mismo punto (antes se quedaba recordado y ese formato ya no se abría).
- Hathor reconoce los vídeos de 10 bits (H.264 «Hi10», HEVC Main 10) y lo dice arriba; si no se pueden decodificar, el aviso lo explica y
  propone VLC con la aceleración por hardware desactivada.

## 0.7.10 — «¿Se ve mal?»: otro decodificador

- **Si la imagen sale rota (bloques grises, trozos de otros fotogramas)** con un vídeo que en otro aparato se ve bien, es el decodificador de
  vídeo de ese aparato, que se equivoca con ese archivo. Abajo en el reproductor, **«¿Se ve mal?»** lo vuelve a abrir en el mismo punto con
  el decodificador de Android (por programa: más lento, pero lee bien casi todo lo que no es 4K), y se recuerda para ese formato en ese
  dispositivo. Otra vez «¿Se ve mal?», y vuelve al del aparato.
- Arriba, bajo la fuente, qué es el vídeo y quién lo lee («H.264 · 720×404 · decodificador del aparato»).
- Si un decodificador no arranca, prueba solo con el siguiente.

## 0.7.9 — sin la raya bajo la cabecera de color

- En el estilo «cabecera de color», los widgets con antetítulo arriba (Recientes y los demás) ya no llevan la raya fina debajo: la franja
  ya separa la cabecera.
- Reconoce a Ammit como de la familia (le puede pasar la sesión de la cuenta).

## 0.7.8 — la cabecera de color, de lado a lado

- En el estilo «cabecera de color» la franja del acento llega de un borde al otro del widget (antes, junto a una portada, empezaba a su
  derecha); la portada queda encima. Lo que va sobre una foto (la fecha de «En portada») no cambia de color.

## 0.7.7 — los widgets, con el estilo que elijas

- **Al poner cualquier widget de Hathor** (también la cabecera con el nombre) se abre una pantalla para
  elegir cómo lleva el acento: **como ahora**, **cabecera de color** (una franja del acento arriba), **papel teñido** (todo el fondo con un
  poco del acento), **marco de color** (un borde grueso del acento) o **todo acento** (el fondo es el acento y el texto va en papel o negro,
  el que se lea mejor). Cada opción se ve con el widget de verdad, de día y de noche.
- La cabecera sin papel (solo el nombre sobre el fondo de pantalla) tiene dos: como ahora o **el nombre en el color del acento**. Si un
  estilo no cambia nada en un widget (sobre una foto a sangre la franja y el papel teñido no se ven), no se ofrece.
- **Para cambiarlo después**: mantener pulsado el widget → ajustes o «configurar» del widget (el nombre depende del lanzador; en algunos
  lanzadores no existe y hay que quitar el widget y volver a ponerlo). Los widgets que ya tenías puestos siguen como estaban.
- El granate oscuro de Nut se aclara un poco en la franja y el marco de noche, para que se vea sobre el negro.

## 0.7.6 — los colores de la familia, de día y de noche

- **Las seis apps comparten colores**: de día, papel #F8F0D4 con texto negro; de noche, al revés (negro con texto #F8F0D4), siguiendo el
  modo del móvil o el que elijas en Apariencia. A cada app la distingue su acento: el de Hathor, **rosa viejo (#B56576)**. Seshat ocre, Jonsu
  ciruela, Bastet oliva, Thot pizarra azul, Hathor rosa viejo y Nut granate oscuro.
- **Una vez, sola**: lo que tuvieras elegido a mano en Apariencia (fondo de color, acento, «A tu manera») se quita para que todo quede así;
  viaja a tus otros dispositivos. Se puede volver a elegir lo que quieras.
- **«Poner los colores de Hathor»**, arriba del todo en Ajustes → Apariencia: vuelve a estos colores de un toque (el tema y el tamaño del
  texto se quedan).
- **Icono y widgets, de día y de noche**: el icono tiene versión clara y oscura (`drawable-night`); cuál se ve depende del lanzador (muchos
  no cambian el icono con el modo oscuro: Android no tiene una forma oficial de hacerlo). Los widgets cambian solos con el modo, y sus
  vistas previas del selector también tienen las dos versiones.
- **El fondo que eliges aquí sale en Jonsu** (y si lo cambias en Jonsu, cambia aquí).

## 0.7.5 — los widgets, también con los colores nuevos

- Las vistas previas de los widgets (lo que sale al elegir un widget en la pantalla de inicio) estaban hechas con los colores de antes:
  rehechas con los nuevos. Los widgets ya puestos se pintaban con los colores nuevos desde la versión anterior (los leen del tema).

## 0.7.4 — colores nuevos, apagados

- **Colores de serie nuevos**, sin tocar nada más de la dirección C: «Celuloide»: gris cálido (#E9E5DE), casi negro (#1F1E1C) y sepia (#8C6F4E); de noche, casi negro (#1C1B19) con el gris de texto y el sepia aclarado (#A88B66). Elegidos entre tres propuestas sin colores brillantes.
- **Una vez, sola**: quien tenía los de serie de antes (la paleta «Palomitas» sin nada elegido encima) pasa a los nuevos, y viaja a tus otros dispositivos. Lo que
  hubieras elegido tú a mano (otros colores, otro acento, un fondo de color, otra paleta) se queda como está. Si los ves iguales que antes,
  es que tenías algo elegido a mano: en Ajustes → Apariencia, quita «A tu manera» y elige la paleta «Celuloide».
- **Icono** con los colores nuevos (`herramientas/familia-c/icono-c.py`) y **cartel de la tele** igual que el icono.

## 0.7.3

Al cambiar una imagen (portada o fondo), la lista de títulos salía duplicada: cada título aparecía dos veces (en Bastet se veía en «También juegas»). La causa:
la lista se rehacía a la vez desde dos sitios (el cambio de imagen, en segundo plano, y el aviso de la cuenta o la bajada del archivo) y los dos
«vaciar + llenar» se cruzaban: vacía uno, vacía el otro, llena uno, llena el otro. Ahora el cambio de lista va con candado y sin repetidos. De paso, `MigracionTest` ya no falla al recoger cuando no hay emulador de cuenta (se saltaba antes de crear su servidor de mentira).

## 0.7.2

- **Ajustes, como Seshat y Bastet**: la cabecera es el bloque de tinta con «AJUSTES.» en grotesca (antes, «Ajustes» en serif).
- **Ficha**: el estado (Pendiente · Viendo · Visto · Abandonado) va en bloque, con borde de 2 dp y el elegido relleno de tinta, como en las hermanas (antes, texto subrayado).

## Capturas

Hechas en el ordenador con catálogos y fuentes de mentira (`CapturasTest`, solo con `-Proborazzi.test.record=true`), en el móvil, en la
tablet tumbada y de pie, en claro y en oscuro: portada, Viendo, Visto, Explorar, Buscar, la ficha de una película y la de una serie, las
fuentes, los addons, Ajustes (también la pestaña de las fuentes) y el reproductor (sus controles, los subtítulos y el episodio siguiente).
Y en la tele, la sala (`CapturasTeleTest`, 1920 × 1080 en modo de tele).

## 0.7.1 · Arreglos de las capturas de 0.7.0

- **La apertura de la ficha sin fondo:** el póster ampliado no pinta el título de la tapa generada, y su parte de abajo se funde con su
  color: lo que lleve escrito un póster a esa altura ya no asoma cortado junto a la etiqueta de estado.
- **Pestañas de Ajustes (y todas las filas de pestañas):** en bloque de la dirección C, como las páginas, y al desplazarse la fila
  empieza siempre en el borde de una pestaña (si hace falta, deja un hueco al final para poder llegar): nada de «…TOR» cortado a la
  izquierda con la letra grande. El degradado de los bordes, más estrecho.
- **La semana:** la etiqueta del día tocado va por encima de todas las barras (antes, una barra vecina más alta la tapaba).
- **Viendo:** en la lista, la línea de abajo dice solo lo que queda («QUEDAN 23 MIN»: el capítulo ya va arriba) y cabe junto a
  «SEGUIR →»; bajo los pósters de la portada, «T1 · E3» o lo que queda, sin cortarse.
- **Capturas:** el fondo de prueba de las fichas es ahora una imagen apaisada sin texto, como los de TMDB (antes era el póster).

## 0.7.0 · La cara de Seshat y Bastet (dirección C) y los colores «Palomitas»

Hathor pasa a la misma altura que Seshat 0.20.3 y Bastet 0.8.1: toda la app en una pantalla, el cartel «Ahora ves», la ficha a doble página,
imágenes propias con encuadre por dispositivo, el reto del año y los widgets en C. El reproductor, «se corta» / «no cambiar», torrents,
Real-Debrid, addons, las reglas de 10 minutos y del 90 %, TMDB, la migración desde Jonsu y la sala de la tele no cambian.

- **Colores «Palomitas»**: papel crema #F4ECDD, tinta casi negra #1B1A1F y el rojo de la caja de palomitas #D7263D, como paleta propia de
  Hathor (con su noche: negro #16131A y el mismo rojo). Son los de serie. Quien tenía los de antes (marrón #715021, blanco y rosa #D4C1C0
  puestos en «A tu manera», o la paleta Revista con acento mostaza que venía de serie) pasa a Palomitas una vez, y viaja a tus otros
  dispositivos; lo elegido a mano distinto se queda. El rojo tal cual, sobre el crema, da 4,2:1: para el texto se oscurece lo justo
  (`acentoLegible`, 4,5:1); en los bloques (pestaña, botón, etiqueta) va tal cual con letra blanca (5:1).
- **Una pantalla, seis páginas**: la cabecera de tinta («HATHOR.» en grotesca, BUSCAR · EXPLORAR · fecha · ⚙) que se pliega sin cortar ni
  dejar hueco, y una fila de pestañas: PORTADA · VIENDO · PENDIENTES · VISTAS · ABANDONADAS · PRÓXIMOS, que se pasan con el dedo. Explorar,
  Buscar, Addons y Ajustes van encima; la ficha, encima de todo. Atrás vuelve a la portada. Los widgets y los avisos llevan a su página
  (`seccion=` con el nombre del destino o de la pestaña; «Próximos capítulos» abre Próximos).
- **Portada**: el cartel «AHORA VES» (lo último que viste: la película, o la serie con «T2 · E5» y lo que queda) con su portada grande y
  paspartú crema sobre su fondo (el tuyo o el de TMDB) o su color; debajo, «SEGUIR VIENDO →», que abre la ficha y hace lo de su botón (la
  mejor fuente, el episodio que toca). Luego el reto del año, «Esta semana» en barras que se tocan (sale lo que viste ese día) y los días
  seguidos; los próximos capítulos; «También ves» y «Pendientes» (lado a lado si hay pocos); «Tu 2026», el mosaico de lo terminado; y lo de
  siempre: tu kDrive y el catálogo (TMDB o tus addons).
- **Reto del año**: películas y series terminadas (una película vista cuenta 1; una serie, 1 cuando la terminas). En Ajustes → Avanzado; va
  con tu cuenta (`ajustes/cine_reto`), como el de Seshat y Bastet. «VAS N POR DELANTE/DETRÁS · TE QUEDAN X EN Y DÍAS» o «PONERTE UN RETO →».
- **Listas**: portadas de 84×126, número, título en grotesca, «película · año» o «serie · T2 E5» y, abajo, según el estado: por dónde vas y
  su barra (y «SEGUIR →»), cuánto dura y cuándo la añadiste, cuándo la terminaste y lo visto, dónde la dejaste. Arriba, la rejilla de
  cifras (películas · series · horas vistas), el menú pequeño de siempre (Películas · Series, en bloques) y solo los órdenes que dicen algo
  en cada página (Viendo: última vez, título; Pendientes: añadidos, año, título; Vistas: terminadas, año, título; Abandonadas: última vez,
  título). Próximos: cada capítulo que viene, con su fecha.
- **Ficha a doble página**: el fondo (o el póster ampliado) como foto de apertura con un velo arriba (← Volver · Imágenes · Editar · Borrar),
  el póster con paspartú asomando, la etiqueta del estado (en Viendo nunca dice «terminado»), el título enorme, «de <dirección o
  creadores> · año», el botón principal (VER, SEGUIR VIENDO · T2 · E5, VOLVER A VER) y las secciones numeradas: 01 Tu visionado (barra,
  visto en total, capítulos, estado, favorita), 02 Temporadas y capítulos, 03 Dónde verlo (tus fuentes y las plataformas), En Jonsu y la
  película o la serie (sinopsis, tráiler, datos, reparto, «Más como esta»). «Editar» cambia el estado y la favorita; «Borrar» es el «Quitar
  de Hathor» de siempre.
- **Imágenes**: la portada y el fondo de cada título (`Imagenes.kt`): buscar en internet, de tu móvil, usar la portada como fondo, la
  automática / «El de TMDB», quitar, y las propuestas de TMDB (sus pósteres y sus fondos). Viajan con tu cuenta (portada: `portada` + `pp`;
  fondo: `fimg` + `fu`; "-" = quitado). **El encuadre es de cada dispositivo** (`Encuadre.kt`): la imagen es la misma en todos, y en cada uno
  eliges qué parte se ve; si la imagen cambia, vuelve al centro. (Hathor no tenía encuadre antes: no hay nada que pasar.)
- **Icono «H.» con la franja «CINE»** en los colores nuevos (adaptable y monocromo, `herramientas/familia-c/icono-c.py`), el cartel de la tele
  igual, y la hoja de comprobación en `docs/mostaza/iconos/comprobacion-icono.png` (`herramientas/familia-c/comprobar-icono.py`).
- **Widgets en C**: sin reborde fino ni esquinas en las portadas, antetítulos en el acento legible, titulares en grotesca; la cabecera
  «HATHOR.» en un bloque de tinta; las portadas, con su encuadre; el fondo, el que eliges.
- Capturas: `CapturasCTest` (normal, noche y letra 1,3): portada (con y sin fondo, con un día tocado, con pocos, vacía), plegado por pasos,
  cada página, fichas (serie viendo, película vista, pendiente, con fondo, abandonada), Imágenes, Encuadre y el reto en Ajustes. Pruebas:
  `DireccionCTest` (Palomitas y el paso desde los de antes, acento AA, reto, semana, encuadre por dispositivo, paspartú, órdenes, páginas,
  estado, fondo).

## 0.4.0 · En la tele: la sala

**Hathor también en Android TV y Google TV**, con el mismo APK que el móvil (en la tele sale en la fila de las apps, con su banner).
Al abrirse en una tele no se ve la Hathor del móvil sino **la sala**, como la de Bastet en el ordenador (`--sala`): a pantalla completa,
oscura, con portadas grandes y todo con la cruceta del mando a distancia, sin nada que haya que tocar. Es la misma Hathor: la misma
biblioteca, los mismos addons, el mismo elector y el mismo reproductor; solo cambia la cara (`Sala.kt`, `SalaFoco.kt`, `SalaPiezas.kt`,
`SalaSecciones.kt`, `SalaFicha.kt`).

- **Arriba**, el nombre, las secciones (Inicio · Biblioteca · Explorar · Buscar), el menú y la hora; **abajo**, qué hace cada tecla.
- **Inicio**: lo enfocado en grande (título, año, lo que llevas, géneros y su fondo) y debajo las filas de la portada del móvil, con los
  mismos datos (`rememberPortada`): «Seguir viendo» (con la barra de lo visto), «Pendientes», «En tu kDrive» y el catálogo de TMDB si
  tienes la clave (si no, los catálogos de tus addons).
- **Biblioteca**: Viendo · Pendiente · Visto · Abandonado, con el filtro y el orden de cada una (los mismos que en el móvil, que viajan).
- **Explorar**: el de TMDB (películas o series, orden, plataforma y género) o el de tus addons (catálogo y género), en rejilla y por páginas.
- **Buscar**: el campo se escribe con el teclado de la tele (OK en él; el de Android TV trae también el micrófono) o con «Hablar», el
  reconocimiento de voz del sistema si la tele lo tiene.
- **La ficha**: «Reproducir» (o «Seguir · T2·E5») pone la fuente que elige Hathor sola, igual que en el móvil (`Busqueda.reproduce`, la
  misma función para los dos), y dice cuál ha sido; «Todas las fuentes», en un panel a la derecha con la mejor para ti marcada y
  enfocada; dónde está en tu biblioteca (y cambiarlo, favorita, o quitarla, con la misma pregunta de siempre: quitar es para siempre); en
  una serie, las temporadas y los episodios (OK pone uno; al lado, sus fuentes y la marca de visto); y «Más como esta».
- **El foco** se ve desde el sofá: lo enfocado crece y un marco del acento se desliza de una cosa a otra. Lo que se abre encima (la ficha,
  las fuentes, el menú, una pregunta) se queda el foco, y al cerrarlo vuelves a donde estabas. ATRÁS vuelve (de la ficha a donde
  estabas; de una sección a Inicio; en Inicio sale de Hathor); MENÚ abre el menú (Ajustes y Addons, las páginas de siempre; Biblioteca;
  Buscar); la tecla de buscar va a Buscar; con un mando de juegos, L1 y R1 cambian de sección.
- **A salvo del borde**: se compone sobre 1080 líneas y se escala a cualquier tele, con los márgenes del 5 % que pide Google para el
  «overscan» (48 × 27 dp de 960 × 540).
- **El reproductor, con el mando**: al enseñar los controles el foco va a reproducir/pausa; la cruceta va de un botón a otro (audio,
  subtítulos, fuente, otro reproductor) y en la barra salta adelante o atrás; con los controles escondidos, izquierda y derecha saltan y OK
  los enseña. Las teclas del mando: reproducir/pausa, adelante y atrás, parar, el episodio siguiente, la de subtítulos y la de audio. Lo
  que se abre encima (audio, subtítulos, fuente, el aviso, «se corta», el episodio siguiente) se lleva el foco, y ATRÁS cierra primero la
  lista abierta. En la tele, más grande y lejos del borde. (También en el móvil y la tablet: ATRÁS cierra la lista de audio o subtítulos.)
- **En una tablet con un mando** (o conectada a la tele), la sala se pide en Ajustes → Avanzado → «Modo sala» (es de ese dispositivo, no
  viaja); se sale desde su menú. En el móvil y la tablet de siempre no cambia nada.
- No se usa Compose for TV (`androidx.tv`): sus filas se retiraron en favor de las normales de Compose, que ya saben moverse con la
  cruceta, y sus botones y tarjetas son los de Material, no los de la revista. Tampoco `focusRestorer` de Compose: en esta versión se cae
  al quitar una fila (suelta dos veces lo que guarda); la sala recuerda lo último enfocado por su cuenta.
- Pruebas: `SalaTest` (en una tele de 1920 × 1080 de mentira, en modo de tele): con las teclas del mando, de verdad, se entra con el foco
  en «Seguir viendo», la cruceta va al siguiente y lo pasa arriba, OK abre la ficha con el foco en «Reproducir», en la ficha la cruceta
  no se sale a lo de debajo, ATRÁS vuelve al mismo póster, arriba están las secciones y OK abre una; en una serie, bajando se llega a un
  episodio y OK lo pone (se abre el reproductor con la fuente elegida); y en el reproductor, el foco en pausa, la barra salta con la
  cruceta. Capturas: `CapturasTeleTest` (solo con `-Proborazzi.test.record=true`), `mostaza-tele-*.png`: Inicio, Biblioteca, Explorar,
  Buscar, las fichas, las fuentes, el menú, quitar, Inicio y Explorar con TMDB, y el reproductor (controles, subtítulos, el siguiente).
- Sin probar todavía en una tele de verdad ni con un mando a distancia de verdad: todo lo de arriba se ha probado en el ordenador.

## 0.3.3

Para saber de quién es la culpa cuando un torrent va lento, su estado dice ahora también cuánta gente conoce el motor (conectada o
no) y cuántas semillas dice el tracker que hay en todo el enjambre: «1 par de 35 conocidos · el tracker dice 120 semillas» (es el
motor o tu conexión, que no consigue conectar) frente a «1 par · el tracker dice 1 semilla» (es que el torrent tiene poca gente).

## 0.3.2

- **El sonido AC3, E-AC3, DTS o TrueHD en cualquier aparato.** Hay tablets (una OPPO, por ejemplo) que no traen la licencia de Dolby
  y no saben decodificar el AC3 que llevan muchas fuentes en castellano. Hathor lleva ahora su propio decodificador (FFmpeg, el que
  precompila Jellyfin para Media3, `org.jellyfin.media3:media3-ffmpeg-decoder`, GPL-3.0), que solo se usa si el aparato no puede.
- **Torrents con más gente.** A un torrent público (con `dht:`) se le suman los trackers públicos de siempre además de los suyos:
  con los pocos que traía la fuente a veces solo se encontraba un par. Uno privado (sin `dht:`) sigue solo con los suyos.
- Si un torrent que aún no ha empezado casi no baja nada (menos de 8 KB/s) durante 45 s, se pasa a otra fuente (o te la propone).
- El estado del torrent («Cargando el torrent… · 1 par · 44 KB/s») sale en el centro, debajo de «Cargando…», y no tapa el título; sin
  los botones de reproducir mientras carga. Lo de los trackers también en el ordenador (1.0.2).

## 0.3.1

Un torrent ya no puede quedarse en «Preparando el torrent…» sin decir nada: mientras arranca enseña los segundos, cuánta gente hay, a
qué velocidad baja y cuánto lleva («Preparando el torrent… 23 s · 4 pares · 1,2 MB/s»), y si en 2 minutos y medio no ha empezado, se
deja y se pasa a otra fuente (si la eligió Hathor sola) o te propone otra. Lo mismo en el ordenador (1.0.1).

## 0.3.0

**Tus películas y series en tu kDrive.** Lo que subas a «Mi carpeta/Mostaza/Películas» y «Mi carpeta/Mostaza/Series» (las crea la
familia, con un LEEME.txt que dice cómo llamar a los archivos) sale como una fuente más, «Tu kDrive», la primera de la lista, y en la
portada, en «En tu kDrive». Hathor averigua qué es cada archivo por su nombre (título y año; en las series, la carpeta de la serie y
S01E02 o 1x02; si el nombre lleva su número de IMDb, manda ese), con TMDB si tienes la clave y si no con tus addons. Se ve desde kDrive
mientras se baja (a trozos), con los subtítulos .srt que tenga al lado. Tu token de kDrive solo va a kDrive: el reproductor ya no manda
las cabeceras de una fuente a otros servidores (tampoco las de tu debrid a los de los subtítulos), y «Abrir con otro reproductor» no se
ofrece con lo de tu kDrive.

## En el ordenador (Linux y Mac): Hathor 1.0.0

**Hathor también en el ordenador**, como sus hermanas (`escritorio/mostaza`, ver [ESCRITORIO](../ESCRITORIO.md)): la misma cuenta (entra
sola si otra app de la familia de ese ordenador ya ha entrado), la apariencia de tu Hathor del móvil, se actualiza sola desde tu kDrive
(`Hathor-<versión>-linux.tar.gz` / `-linux.deb` / `-mac.dmg`) y lo mismo de la cuenta, campo a campo: tu biblioteca (`titulos`), lo
visto de cada película y episodio (`progreso`), el tiempo por día y dispositivo (`tiempo/<ref>~mostaza-linux-…`), lo que eliges
(`ajustes/mostaza_*`: el elector, el reproductor, el orden de cada sección, la portada, los torrents y las fuentes rotas) y tus addons y tu
clave de TMDB en `/claves`. Lo que ves en el ordenador lo sigues en el móvil, y al revés. El protocolo de los addons, el elector, las
pistas, TMDB y las tres reglas son el mismo código que aquí, con las mismas pruebas (`ElectorTest`, `PistasTest`, `ProtocoloTest`,
`TmdbTest`, `RotasTest` y `ReglasTest`, esta contra la cuenta de verdad).

- **Se ve con mpv** (`sudo pacman -S mpv` en CachyOS y Arch, `brew install mpv` en el Mac). Hathor lo abre en su ventana y le habla por su
  socket: la dirección de la fuente y sus cabeceras van por ahí, nunca en la línea de órdenes (que cualquier programa puede leer), y mpv no
  guarda nada suyo (ni «seguir donde lo dejé» ni yt-dlp). Le pregunta cada segundo por dónde va y si está reproduciendo de verdad, así
  que las tres reglas funcionan como en el móvil; y lee sus pistas (`track-list`): pone el audio en tu idioma, los subtítulos en español
  si hacen falta, prueba otra fuente si la que eligió sola no trae tu primer idioma, y recuerda lo de cada fuente. El vídeo de aviso del
  debrid se reconoce igual (menos de 4 minutos para algo de más de 10) y se pasa a otra. Sin mpv, se ofrece el reproductor del sistema
  (sin contar nada). Probado con mpv de verdad (`MpvDeVerdadTest`: vídeos hechos con ffmpeg y un servidor que pide la cabecera de la fuente).
- **Torrents** con el mismo motor que el del móvil (`escritorio/torrent`, que compila `torrent/src/main` tal cual, con libtorrent4j y su
  biblioteca de Linux x86-64 o del Mac con Apple Silicon); con tu debrid (TorBox, Real-Debrid…), las directas van antes.
- **Teclado y mando**: las flechas (o la cruceta) mueven el foco, Intro (o A) pulsa, Esc (o B) vuelve.
- **Tu kDrive**, como en el móvil: lo de «Mi carpeta/Mostaza/Películas» y «…/Series» sale como la fuente «Tu kDrive», la primera, y en
  la portada («En tu kDrive»). Tu token solo va a kDrive: a mpv por su socket, y los subtítulos de fuera se bajan antes en Hathor (sin
  las cabeceras de la fuente) para que mpv no se las mande a otros servidores; el reproductor del sistema no se ofrece con estas fuentes.
- Lo que aún no: los widgets y traer lo de Jonsu (lo hace el móvil).
- Capturas: `CapturasTest` (con `-Dcapturas=<carpeta>`): portada (clara y oscura), Viendo, Visto, la ficha de una película y la de una
  serie, las fuentes, los addons, Ajustes (fuentes y reproductor) y la banda de mpv.

## 0.2.0

**Un catálogo propio, como una plataforma.** Con tu clave de TMDB (gratis, se pega una vez en Ajustes → Catálogo y va con tu cuenta,
en `/claves`), la portada ya no son los catálogos de tus addons: un destacado arriba, «Seguir viendo», «Pendientes», «Tendencias de la
semana», «Porque viste…», estrenos en cines, series del momento, populares, lo que hay en cada plataforma de suscripción en España
(«En Netflix», «En Prime Video»…, en el orden de JustWatch), las más valoradas, por género y lo que llega a los cines. «Explorar» es
películas o series por orden, plataforma y género; «Buscar», todo TMDB. Las fichas salen en castellano con el lema, el tráiler, dónde
verla en España, el reparto con fotos y «Más como esta»; en las series, los episodios son los de tus addons (es su numeración la que
usan las fuentes) con el título, la imagen y el resumen de TMDB. Tus addons quedan para las fuentes; sin clave, todo sigue como antes.
Y los dos widgets de abajo.

## 0.1.3

Cuando tu debrid no da un archivo (Real-Debrid con «infringing_file»), algunos addons mandan un vídeo de aviso de unos dos minutos en vez
de un error: Hathor lo reconoce (menos de 4 minutos para algo que dura más de 10), no lo cuenta para nada y pasa sola a otra fuente; la
rota se apunta en tu cuenta (`ajustes/mostaza_rotas`) y no se elige sola en 30 días.

## Widgets

Dos widgets para la pantalla de inicio, como los de las hermanas (la misma revista: papel y tinta, los colores y las letras que tengas
en Apariencia, en claro y en oscuro): **Seguir viendo** (lo último que has tocado de lo que estás viendo, con su póster, «T2 · E5 ·
quedan 20 min» y su barra; un toque abre su ficha, y la esquina pasa a otro si ves varios) y **Pendientes** (tus pendientes en fila, lo
último que añadiste primero; cada póster abre su ficha y la cabecera, la sección). Se repintan solos cuando cambia tu biblioteca o lo
visto, aquí o desde otro dispositivo por la cuenta, y cuando cambia la apariencia; con el reproductor abierto esperan a que lo cierres.
Dibujados en el ordenador en [widgets/](widgets/) (`WidgetsTest`, con `-Proborazzi.test.record=true`).

## 0.1.2

Arreglo de un fallo de verdad: con tres películas, «Reproducir» eligió para una una fuente **sin ningún audio en español**, para otra
una en **latino** (y tu orden era castellano antes que latino), y solo acertó con la tercera. El elector decidía el idioma solo por el
texto de la fuente, y ese texto muchas veces no dice nada fiable.

- **Ahora mira las pistas de verdad.** Al abrir el vídeo, el reproductor lee el idioma y el nombre de cada pista de audio («es-ES»,
  «es-419», «spa», «Castellano», «Latino», «Spanish (Latin America)»…) y sabe si es castellano, latino, español sin saber cuál (cuenta
  para castellano a medias, como en el texto) u otro idioma. Pone **la mejor pista de audio** del archivo según tu orden y, si el audio no
  es español y aceptas la original con subtítulos, **pone unos subtítulos en español** si los hay.
- **Si la que eligió sola no tiene tu primer idioma, prueba otra**: «Esta no tiene audio en castellano: probando otra…». Como mucho 4
  fuentes y unos 45 segundos; solo las que podrían ser mejores y se abren rápido (directas o de tu debrid ya bajadas; un torrent, solo si
  tu debrid lo tiene o tiene semillas). Mientras prueba no empieza a reproducir. Si ninguna es mejor, vuelve a la mejor y lo dice tal
  cual: «No hay ninguna en castellano; la mejor es en latino» (o «… en versión original con subtítulos»); si se ha parado por el
  límite, dice cuántas ha probado. **Si tocas tú una fuente concreta, no se cambia nunca.** Si una pista no dice su idioma («und»), no
  se puede saber si le falta el tuyo: entonces no prueba otra.
- **Se acuerda.** Lo que trae cada fuente se guarda en este dispositivo (es saber de la fuente, no tuyo; por el `infoHash` y el archivo,
  o por una huella de la dirección: las claves de tu debrid no se guardan) y la próxima vez cuenta antes que el texto. En la lista de
  fuentes se ve: «Castellano ✓».
- **El texto, mejor leído** (con fuentes de Torrentio de verdad): con 🇲🇽 u otra bandera latinoamericana, o «LATINO» en el texto, la 🇪🇸
  ya no se toma por castellano (Torrentio la pone para cualquier español). Y el idioma lleva **lo seguro que es**, que cuenta antes que
  la calidad y el tamaño: seguro («[Esp]», «Castellano», o solo 🇪🇸), a medias (🇪🇸 entre pocas banderas, «Dual Audio», «Spanish English
  Subs») y poco seguro (🇪🇸 en una lista larga de banderas: casi siempre son los subtítulos). El poco seguro va como «MULTI»: detrás del
  español seguro (también del latino) y delante de lo que seguro que no lo tiene; en la lista se ve «¿Castellano?». «MULTI» y «DUAL» sin
  más son «puede tener español», no «no se sabe». Con «Ahorrar datos», el caso de verdad elige ya «[Bluray][Esp] 🇪🇸 1,09 GB» y no la de
  12 banderas y 584 MB.
- **Nunca una grabada en el cine** (CAM, TS…) ni una reescalada («AI Upscale») si hay otra en el mismo idioma. 4320p cuenta como 4K.

Lo que no hace todavía: no abre la fuente por adelantado para mirar sus pistas antes de elegir (solo la que se pone); y un archivo con
una sola pista sin idioma sigue dependiendo de lo que diga su texto.

## 0.1.1

- **Con datos del móvil se ve igual.** Las fuentes directas y las de tu Real-Debrid no tienen ningún límite con datos (con «Ahorrar
  datos del móvil», Hathor prefiere hasta 720p y lo que menos pese; si quieres la mejor calidad también con datos, apágalo en Ajustes →
  Elector). Arreglo: con «Solo con wifi» puesto y yendo con datos, «Reproducir» podía elegir un torrent que el motor no iba a abrir; ahora
  elige la mejor fuente directa o de tu debrid. Si quieres torrents con datos, quita «Solo con wifi» en Ajustes → Torrent.

## 0.1.0 — la primera

**Lo que hay**

- **Portada** de revista: la cabecera, el número y la fecha, el menú (Viendo · Pendiente · Visto · Abandonado · Explorar · Addons),
  **«Seguir viendo»** (lo que tienes a medias, con lo que llevas: «T2 · E5 · quedan 23 min»), **«Pendientes»** y las filas de los
  catálogos de tus addons (las primeras, o las que elijas). Al bajar, la cabecera se pliega.
- **Tus secciones**: Viendo · Pendiente · Visto · Abandonado, cada una con películas y series (o solo unas u otras), en rejilla, ordenadas
  por recientes, añadidas, título o año (lo que eliges se recuerda, en tu cuenta).
- **Explorar** (los catálogos de tus addons): por tipo (películas, series…), cada catálogo con sus filtros de **género** o de **año** (los
  que da el propio catálogo: los de Cinemeta, «Populares» por género y «Por año»), por páginas.
- **Buscar** en todos tus addons que buscan (cada uno en su fila) y en tu biblioteca.
- **La ficha**: el fondo, el póster, el titular, el año, la duración, la nota de IMDb, los géneros, la dirección, el reparto y la
  descripción; dónde está en tu biblioteca (Pendiente · Viendo · Visto · Abandonado, favorita, quitar) o «+ Añadir»; **«Reproducir»** (o
  «Seguir viendo», o «Ver · T2·E6» en una serie: el episodio que toca); y en una serie, sus **temporadas y episodios** con lo que llevas y
  la marca de visto (que se pone y se quita a mano). «En Jonsu»: la nota y la reseña que le has puesto allí, y «Valorar en Jonsu».
- **Las fuentes** de una película o un episodio: todas las de tus addons, agrupadas por addon (en tu orden), con sus líneas tal como las
  da el addon, lo que Hathor entiende de cada una («1080p · Castellano · 4,4 GB») y si es **directa**, **torrent**, YouTube, una web,
  Usenet o un comprimido. La que pondría Hathor va marcada («La mejor para ti»). Tocar una la pone.
- **El elector de fuentes** (lo que hace «Reproducir»): de todas las fuentes de todos tus addons, la mejor para ti. El protocolo no dice
  ni el idioma, ni la calidad, ni las semillas: Hathor lo lee del texto de cada fuente (la resolución, el HDR o Dolby Vision, el códec, el
  idioma del audio —por las palabras y por las banderas: 🇪🇸 es castellano; 🇲🇽 o 🇦🇷, latino—, si lleva subtítulos en español, el tamaño,
  las semillas de un torrent y si tu debrid ya la tiene). Y elige así:
  1. **tu idioma primero**: una en un idioma que prefieres gana a cualquier calidad. El orden lo pones tú (de serie: castellano, latino,
     y la original con subtítulos); «Español» sin decir cuál va detrás del castellano de verdad y delante del latino;
  2. luego **la mejor calidad que cabe en tu conexión** (lo que pesa por minuto frente a lo que ha medido este dispositivo viendo cosas,
     por wifi y por datos aparte) y que **este dispositivo sabe reproducir** (el códec, el HDR, su resolución), sin pasar de la que pongas
     como máxima; con datos del móvil, si ahorras, como mucho 720p y lo que menos pese. Un torrent, con semillas (o ya en tu debrid);
  3. luego las **directas o de tu debrid** antes que los torrents;
  4. y al final, el orden de tus addons.
  Al pulsar, se pone la elegida y se dice cuál ha sido («1080p · Castellano · 4,4 GB · Fuentes de casa · Cambiar»); la lista entera está a
  un toque. Si se corta una y otra vez, Hathor apunta que la conexión da menos y te ofrece la siguiente. En el episodio siguiente prefiere
  la fuente de la misma clase que la del anterior (el «bingeGroup» del addon, como Stremio).
- **El reproductor** (AndroidX Media3): los enlaces directos (también HLS y DASH), con las cabeceras que pida la fuente; subtítulos de tus
  addons de subtítulos y los que trae el vídeo (sin subtítulos si el audio ya está en tu idioma, salvo que los quieras siempre; el tamaño y
  la banda, a tu gusto); elegir audio, subtítulos y fuente; sigue donde lo dejaste (aquí o en otro dispositivo); el **episodio siguiente**,
  solo, con una cuenta atrás; y **«Abrir con otro reproductor»** (VLC, Just Player, mpv, MX…, con el título, por dónde ibas y las
  cabeceras) para lo que este dispositivo no sabe decodificar: Hathor lo dice cuando pasa (también si es solo el sonido: DTS, TrueHD…).
  Con el mando: la cruceta salta adelante y atrás, OK enseña los controles.
- Las fuentes de **YouTube** (los tráileres) se abren en YouTube y las de una **web**, en el navegador. Las de **Usenet** (NZB) y las de
  **comprimidos** dicen, con la verdad, que todavía no.
- **Los torrents se ven mientras se bajan**, con el motor de la familia (libtorrent4j, de código abierto): eliges un torrent, se prepara
  (unos segundos: buscar a quién lo tiene y leer su lista de archivos) y se reproduce mientras baja, y puedes saltar a cualquier punto (se
  baja primero lo que vas a ver). Al cerrar el vídeo se deja de compartir y se borra. Mientras lo ves, lo que bajas se comparte con otros
  (así van los torrents), con la subida limitada a 100 KB/s de partida. Con «Solo con wifi», con datos del móvil no se baja nada. Todo eso
  (solo con wifi, subida, bajada, compartir) viaja con tu cuenta; lo que pueden ocupar los torrents en el móvil es de cada dispositivo.
  Una fuente de tu Real-Debrid no es un torrent para el móvil: se ve como cualquier vídeo, sin compartir nada.
- **Tus addons**: instalar uno por la dirección de su manifiesto (o desde el botón «Install» de su página: los `stremio://` se abren
  aquí), subirlos y bajarlos (el de arriba manda en las fichas), quitarlos y **configurarlos** (la página del propio addon: lo que pongas
  allí, como tu debrid, se queda en la dirección nueva). Si quitas Cinemeta, «Poner Cinemeta» lo devuelve; OpenSubtitles (el oficial de
  Stremio para los subtítulos) se pone con un toque, si quieres.
- **Ajustes** en pestañas: Apariencia (la de la familia, con el acento mostaza de serie) · Reproductor · Fuentes (el elector, tu
  conexión, lo que sabe este dispositivo y los torrents) · Cuenta (y las hermanas) · Avanzado (actualizarse sola y la portada).
- **Se actualiza sola** desde tu kDrive («Mi carpeta/versiones», `Hathor-<versión>.apk`), como las hermanas: nunca con algo en marcha
  (el reproductor abierto) ni con la pantalla apagada.
- **Entrar una vez**: si ya has entrado en Jonsu o en otra hermana, Hathor entra sola (y al revés, con Jonsu 1.6.3; Bastet,
  Seshat y Thot, desde su próxima versión).
- En la tablet (tumbada y de pie) y en la tele: la ficha a dos columnas, los estantes más grandes, y todo se mueve con el mando
  (lo que tiene el foco lleva el marco del acento).

**Las tres reglas**

1. **Nada entra en tu biblioteca hasta que llevas 10 minutos viéndolo de verdad, seguidos, en una misma sesión del reproductor.** Cuenta
   solo el tiempo reproduciendo: las pausas no cuentan, saltar adelante no cuenta, y salir del reproductor termina la sesión (si vuelves,
   empieza otra). Hasta entonces **no se escribe nada en tu cuenta** (solo un punto para seguir en ese dispositivo). A los 10 minutos entra
   en «Viendo» (o pasa a «Viendo» si estaba pendiente o abandonada), con todo lo de esa sesión; si en esa misma sesión llega al final,
   queda en «Visto».
2. **El tiempo que ves se apunta** por día y por dispositivo (lo lee Jonsu para su diario, como lo de Bastet y Seshat), y **por dónde
   vas** en cada película y cada episodio viaja (sigues en otro dispositivo). Una película o un episodio está visto al 90 %; una serie
   pasa a «Visto» cuando tienes vistos todos sus episodios estrenados (los especiales no cuentan) y, si no, sigue en «Viendo».
3. **Borrar es para siempre**: queda borrada en tu cuenta (en todos tus dispositivos y en Jonsu) y lo que entra solo **nunca** la
   devuelve, ni aunque la vuelvas a ver entera (y de lo que ves de ella no se apunta nada). Solo «Añadir» la recupera: la misma, con lo
   suyo (su estado, sus fechas, su nota, lo visto). Es lo que se aprendió en Bastet 0.5.4.

**Lo que tenía Jonsu de películas y series ya es de Hathor**

Jonsu guardaba tus películas y tus series en sus categorías (las de TMDB y TVmaze). Ahora son de Hathor, como los juegos pasaron a
Bastet y los libros a Seshat: la primera vez que abres Hathor con tu cuenta, cada ficha pasa a tu biblioteca con todo lo suyo (el estado,
cuándo la añadiste y cuándo la terminaste, la favorita, el póster y el fondo, la nota con sus criterios, la reseña, tus apuntes y lo demás
de la ficha, el tiempo que tenía apuntado y, en las series, los episodios marcados), con su código de IMDb (el que usan los addons de
Stremio: sale de TMDB con la clave de TMDB de tu cuenta, o de TVmaze) y con sus ids de antes. Nada se borra ni se cambia en Jonsu (sus
fichas de antes se quedan donde estaban, como copia), y desde ese momento **Jonsu valora las de Hathor**: la misma ficha, sin copia, con
su número de siempre (tus sagas, tu diario y tus maquetas la siguen encontrando). En otro dispositivo no se repite nada, y lo que
hayas cambiado o borrado en Hathor no se pisa.

**Todo en tu cuenta** (lo que viaja y lo que no)

| Lo que hay en Hathor | ¿Viaja? | Dónde / por qué |
|---|---|---|
| Tu biblioteca (películas, series, su estado, fechas, favoritas) | Sí | `titulos` |
| Por dónde vas en cada película y episodio, y lo visto | Sí | `progreso` (de lo que está en tu biblioteca) |
| El tiempo que ves, por día | Sí | `tiempo/<título>~<dispositivo>` |
| Lo que quitas | Sí | `titulos` (borrado, con su hora) |
| Tus addons y su orden | Sí | `/claves` (lo que no lee ni tu pareja: llevan la clave de tu debrid) |
| La apariencia | Sí | `ajustes/mostaza` |
| Subtítulos (idioma, tamaño, banda, siempre), audio, episodio siguiente, el salto, el otro reproductor | Sí | `ajustes/mostaza_reproductor` |
| Tus idiomas, con subtítulos o no, la calidad máxima, directas antes, ahorrar datos, elegir sola | Sí | `ajustes/mostaza_elector` |
| Torrents: solo con wifi, límites, compartir | Sí | `ajustes/mostaza_torrent` |
| El orden y el filtro de cada sección | Sí | `ajustes/mostaza_orden` |
| Las filas de la portada y lo último de Explorar | Sí | `ajustes/mostaza_portada` |
| «Actualizar sola» | Sí | `ajustes/mostaza_actualizador` |
| Lo que puede ocupar la caché de torrents | No | es el almacenamiento de cada dispositivo |
| Lo que mide la conexión, lo que sabe decodificar | No | es de cada aparato (y de cada red) |
| El punto para seguir de lo que aún no está en tu biblioteca | No | la regla 1: antes de los 10 minutos, nada a la cuenta |
| Los manifiestos y catálogos de los addons | No | es lo que dice cada addon: se vuelve a pedir |
| El id del dispositivo, la sesión, lo pendiente de subir, lo de la última comprobación del actualizador, los colores de Jonsu leídos | No | es lo que lleva cada dispositivo de la cuenta (la sesión, cifrada) |

**Lo que no se ha podido probar (honesto)**

- **Nada de esto se ha probado en un móvil, una tablet ni una tele de verdad.** Las pruebas del ordenador comprueban las tres reglas con la
  cuenta de verdad (el emulador de Firebase), el protocolo contra un addon de mentira, el elector con títulos como los de verdad, que las
  direcciones de tus addons no salen de `/claves`, el paso de lo de Jonsu en dos dispositivos, que Jonsu lee lo que escribe Hathor
  y que lo que eliges viaja. Las capturas enseñan las pantallas. Pero el reproductor no se ha probado con vídeos de verdad: qué formatos
  abre depende de cada dispositivo (para eso está «Abrir con otro reproductor»).
- El elector lee texto libre: un addon que escriba de otra manera puede confundirlo (la lista entera siempre está a un toque).
- La página de configurar de cada addon es la suya: si en vez de su botón de instalar te da la dirección para copiarla, pégala en
  «Instalar un addon» (sustituye a la de antes si es el mismo addon… o se añade otro: quita el viejo).
- Sin TMDB en tu cuenta, lo que venía de Jonsu como película entra sin código de IMDb (`tmdb:<id>`) hasta que pongas la clave: su ficha
  no sale de Cinemeta hasta entonces.
- Bastet, Seshat y Thot aún no le dan la cuenta a Hathor (lo hará su próxima versión): entra con Jonsu 1.6.3, o con tu usuario.
- Usenet y los comprimidos: más adelante. Los torrents están probados de verdad (un torrent compartido en el mismo ordenador, visto
  entero y saltando, byte a byte), pero todavía no en un móvil de verdad ni con gente de internet.

## Compilar

```
./gradlew :mostaza:assembleDebug
./gradlew :mostaza:testDebugUnitTest                                   # con el emulador de Firebase encendido (herramientas/emulador.sh)
./gradlew :mostaza:testDebugUnitTest --tests '*Capturas*' -Proborazzi.test.record=true -Dcapturas=<carpeta>
./gradlew :mostaza:assembleRelease && python3 herramientas/firmar.py mostaza   # la firma de la familia, sin linaje (nace con ella)
python3 herramientas/mostaza/subir-version.py mostaza/build/outputs/apk/release/Mostaza-0.1.0.apk
```

El reproductor es AndroidX Media3 1.9.4: la última que se compila con el SDK 35 de la familia (desde la 1.10 pide el 36). El contrato con
Jonsu: `ContratoTest` deja en `build/outputs/contrato/mostaza.json` lo que escribe Hathor, y Jonsu lo lee en su `ContratoHermanasTest`
(copia en `herramientas/casos/hermanas/mostaza.json`). Al sacar versión: `versionCode`/`versionName` (`build.gradle.kts`), `VERSION`
(`PantEstilo.kt`) y esto.
