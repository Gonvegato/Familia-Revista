# Bastet

El lanzador de juegos del móvil, hecho de nuevo. Hermana de [Atrio](https://github.com/gvegagato-prog/Atrio-Suite-2.0),
[Seshat](https://github.com/Gonvegato/lectura-app) y [Thot](https://github.com/Gonvegato/cobalto): mismo sistema de temas (paletas, letras,
«estilos»), la misma cuenta de Atrio y el mismo kDrive; otro paquete (`com.gonzalo.hiedra`) y otro hueco en la cuenta (`/users/<uid>/hiedra`), así
que **convive con Carmesí** en el mismo móvil hasta que lo tengas todo aquí. Sustituye a [Carmesí](https://github.com/gvegagato-prog/Android-Launcher)
(la que nació como Pórtico): hace lo mismo y lo que Carmesí no podía, con **un modelo de datos nuevo** (`docs/MODELO.md`) y una importación
de una sola vez.

## La idea

En Carmesí un juego **era** su lanzador (su ROM, su app, su web, su clave del PC), y por eso hacían falta fichas sin lanzador, alias, gemelas y
«unir». En Bastet **un juego es una cosa con identidad propia** (un `id` que viaja con la cuenta) y **cómo se abre es una lista**: la ROM en el
móvil, la app, la web y el mismo juego en el PC, todo en una ficha, en los menús pequeños que toquen (Emulación y PC a la vez). Las horas se
guardan **por fuente**: las de este móvil, las de cada uno de tus otros móviles (se suman) y las externas (Steam, cada PC con Atrio, lo jugado en
Carmesí), que se mezclan por máximo y nunca se suman dos veces. Dónde está cada ROM es cosa de cada móvil y no viaja.

## 0.8.0 — la cara de Seshat: portada cartel, páginas y ficha a doble página

- **Una sola pantalla con páginas, como Seshat.** Arriba, la cabecera de tinta (BUSCAR · + AÑADIR · la fecha · ⚙ y «BASTET.» en grande,
  que se recoge al bajar sin cortarse); debajo, una sola fila de pestañas: Portada · Jugando · Pendientes · Pasados · Abandonados · Sagas ·
  Sistemas. Se pasan con la pestaña o con el dedo, y Atrás vuelve a la portada. Tu PC y Ajustes van encima; las fichas, también.
- **La portada, un cartel**: «AHORA JUEGAS» con la portada grande (con un paspartú del color de la página, o de la tinta si la portada se le
  parece) sobre tu fondo o su color, las horas, de dónde es y cuándo jugaste; debajo, «SEGUIR JUGANDO →» (o «BAJAR Y JUGAR», «JUGAR EN EL
  PC»…: se juega igual que antes, en Bastet, con tu emulador o en el PC). Si el PC está jugando, el cartel es eso, en directo. Luego tu PC,
  el **reto del año** (juegos terminados: «Reto 2026 · 5 / 20», la barra y cómo vas; sin reto, «PONERTE UN RETO →»), **la semana** en barras
  que se tocan (sale lo que jugaste ese día) y los días seguidos, «También juegas» y «Pendientes» (lado a lado si hay pocos), tus estantes
  de siempre (Mis elegidos, Sagas, Tu PC, Pasados; se cambian en «Editar portada →», abajo) y el mosaico de lo que has pasado este año.
- **El reto** se pone en Ajustes → Juegos y va con tu cuenta (`ajustes/juego_reto`): sale igual en tus otros Bastet.
- **Listas con portadas grandes** (84×126): número, título, de dónde es y, abajo, cómo va según la sección (horas y última partida; lo que
  suele durar; cuándo lo pasaste) con «JUGAR →». Arriba de cada sección, sus cifras (juegos, horas, plataformas), los menús pequeños (PC ·
  Emulación · Android · Web, ahora en bloques) y solo los órdenes que tienen sentido allí (en Pendientes, «Más cortos»).
- **La ficha, a doble página**: la portada ampliada (o tu fondo) como foto de apertura con un velo arriba para «← Volver · Imágenes · Editar ·
  Borrar», la etiqueta del estado (en Jugando nunca dice «terminado»), el título enorme y «de <estudio> · <saga, n>», el botón principal y
  las secciones numeradas: Tu partida (las horas, la historia, el estado y el favorito), La saga, Cómo se abre, tus partidas en Bastet,
  Tiempo y duración, En Jonsu, Datos y En la nube. No se ha quitado nada de lo que tenía.
- **Imágenes: portada y fondo**, como en Jonsu y Seshat. «Imágenes» abre una hoja con dos pestañas: la portada (SteamGridDB, buscar en
  internet, de tu móvil, la automática) y el fondo (buscar, de tu móvil, usar la portada, quitar); con tu API key de SteamGridDB salen sus
  propuestas (portadas y fondos «heroes»). El fondo va con tu cuenta; si no le pusiste ninguno y en Jonsu sí, se usa el de Jonsu.
- **Encuadrar es de cada dispositivo**: la imagen es la misma en todos, pero qué parte se ve se elige en cada móvil o tableta (tienen otra
  forma) y vuelve al centro si la imagen cambia. El encuadre que tenías se pasa una vez a este dispositivo.
- **El acento, legible**: el de Bastet (#6A6D2C) se queda en el icono y en los bloques; donde hace falta leerlo (cifras, la pestaña elegida,
  el botón principal, el texto en acento) se aclara lo justo para leerse sobre la página. Lo hace con el acento que elijas en «A tu manera».
- **Icono nuevo**: «B.» con la franja «JUEGOS» (adaptable y monocromo). Los widgets, en la misma dirección: sin rebordes, tapas rectas,
  titulares en grotesca y la cabecera en un bloque de tinta con «BASTET.».
- Con mando, todo sigue enfocándose: las pestañas, el cartel (A juega, X abre la ficha), las portadas y el botón principal de la ficha.

## 0.6.2 — lo que eliges, en todos tus dispositivos

- **Lo que eliges en Bastet ya está en tu cuenta, no solo en este móvil.** Hasta ahora viajaban los juegos, las horas, la apariencia y la
  portada; lo demás se quedaba en cada móvil. Ahora también: el orden de cada sección, cómo se juega con el jugador de Bastet (los botones
  en pantalla, el avance rápido, el filtro, la orientación, cada cuánto guarda) y lo que cambiaste de cada mando, el emulador que elegiste
  para cada consola, las portadas automáticas, las opciones de los núcleos, el PC al que mandas las órdenes, si las capturas del PC van a la
  galería, si tus ROMs suben solas a kDrive, la web de imágenes que usaste la última vez y «Actualizar sola». Lo cambias en un móvil y está
  en los demás; si en dos a la vez cambias cosas distintas, se quedan las dos, y si es la misma, la última.
- **La primera vez en cada móvil, lo que ya tenías pasa a la cuenta sin perder nada**: lo que solo tenía uno, tal cual (así se juntan las
  opciones de los núcleos y los emuladores de cada consola de los dos); lo que tenían distinto, el del móvil donde lo cambiaste más tarde.
- Si eliges un emulador que en otro móvil no está instalado, allí se usa el automático hasta que lo instales.
- Lo que es de cada móvil sigue en él: dónde están tus ROMs y sus carpetas, los guardados ya sincronizados, los núcleos y BIOS bajados,
  «usar Bastet como launcher» y los widgets.
- **La lista negra de Jonsu vuelve a llegar a Bastet.** Bastet la leía de lo que subía Atrio, que ya no cambia desde que Jonsu la
  guarda en la cuenta: un juego que vetabas (o que quitabas de la lista) en Jonsu no se enteraba aquí. Ahora la lee de la cuenta.
- Bastet de Linux y Mac todavía no lee estos ajustes: llegará en su próxima versión.

## 0.6.1

- **Una versión nueva ya no te saca de lo que estás haciendo.** El actualizador instala en silencio cuando no usas la app, y Android la cierra para instalarla. Pero no siempre sabía que la estabas usando: si se entraba directamente a un juego del jugador de Bastet (desde un widget, o cuando Android vuelve a abrir la app tras cerrarla por detrás), creía que no estabas; y con la pantalla apagada un momento, también. Ahora se lo pregunta a Android, nunca instala con la pantalla apagada o bloqueada, y con un juego abierto espera a que lleves un rato fuera.
- **Los títulos grandes ya no salen cortados** (se cortaban por el final hasta cambiar de pantalla y volver; en Seshat salía «Marfi»). Si tu apariencia usa una letra de Google Fonts, llega un momento después de abrir la app; el título se medía con la de mientras, más estrecha, y no se volvía a medir. Ahora sí.
- Si Android cierra lo que dibuja una página web de la app (los juegos web, la búsqueda de portadas), se cierra esa pantalla y no la app entera.

## 0.6.0 — veinticuatro consolas en Bastet, y una cara nueva

**El jugador de Bastet ya no es solo de GBA.** Juega ella misma a Game Boy y Game Boy Color, NES (y Famicom Disk System), Super Nintendo,
Mega Drive, Master System, Game Gear, PC Engine, Neo Geo Pocket (y Color), WonderSwan (y Color), Atari Lynx, 2600 y 7800 y, **en
pruebas**, Nintendo DS, Nintendo 64, PlayStation, PSP, Mega-CD y arcade (Neo Geo y FinalBurn Neo). Con todo lo que ya tenía la GBA:
guarda al salir y lo sube a tu nube, sigues en otro móvil, avance rápido, tres huecos de estado, mando físico y táctil.

- **Los núcleos de Lemuroid**, uno por consola, comprobados en su código (los de la versión que usaba RadialGamePad 2.0, la misma que
  Bastet): Gambatte, FCEUmm, Snes9x, Genesis Plus GX, Beetle PCE Fast, Beetle NeoPop, Beetle Cygne, Handy, Stella, ProSystem,
  FinalBurn Neo, Mupen64Plus-Next (GLES3), PCSX ReARMed, PPSSPP y melonDS; con sus mismas opciones de arranque (las paletas de la Game
  Boy, la pantalla táctil de la DS, el «frame duping» de la N64…), comprobadas una a una en el código de cada núcleo del buildbot (los
  nombres no siempre son los de Lemuroid, que compila los suyos: los de melonDS, por ejemplo). Se bajan del buildbot de libretro la
  primera vez que juegas (de 50 KB a 17 MB; se dice antes de bajarlo).
- **Un mando para cada consola**, en la pantalla y en el mando físico: los de Lemuroid (la cruceta y los botones donde van en cada una,
  con su nombre: los I y II de la PC Engine, ○ △ □ ✕ en PlayStation y PSP, Z y los botones C de la N64, Option en la Neo Geo Pocket,
  Moneda en arcade). La N64 y la PSP con su stick (en la pantalla y en el mando); la DS se toca en la pantalla de abajo, con el micrófono
  (soplar) y la tapa en botones. R2 (el gatillo derecho) sigue acelerando en todas las que no lo usan (todas menos PlayStation).
- **BIOS**: en la ficha («Emulador») cada núcleo dice qué BIOS busca, cuáles tienes y cuáles faltan; «Añadir BIOS» la reconoce por su
  contenido (aunque se llame de otra manera) y la deja donde el núcleo la busca. Solo el Mega-CD (una de las tres regiones), la Neo Geo
  (neogeo.zip: si está junto a tus ROMs, Bastet la coge sola) y los discos de la Famicom no arrancan sin la suya; si falta al jugar,
  se dice cuál y se elige ahí mismo.
- **Juegos de varios archivos**: un .cue con sus pistas, un .m3u con sus discos (y el escaneo ya no los añade como juegos sueltos), un
  .ccd. Los discos de PlayStation y PSP no se copian: el núcleo los abre donde están (como Lemuroid); si alguno no abre así, «Probar
  copiándola». Las recreativas se abren tal cual, con el nombre de su .zip.
- PPSSPP necesita sus archivos (letras, firmware): se bajan una vez con el núcleo, del buildbot de libretro (unos 10 MB).
- **«Automático» no te quita tu emulador**: en las consolas nuevas, Bastet solo es la automática si no tienes otro emulador instalado
  para ella (RetroArch no cuenta: necesita sus núcleos). En GBA, como hasta ahora. En Sistemas o en la ficha se elige «Bastet» con un toque.
- **Opciones de la consola** en el menú del juego: los colores de la Game Boy, las pantallas de la DS (una encima de otra o al lado), la
  resolución de N64 y PSP, el recompilador de PlayStation, el giro de la Lynx y la WonderSwan… Se aplican al momento.
- El estado guardado se carga tras la primera imagen (PPSSPP y Mupen64 preparan cosas al dibujarla: lo que hace Lemuroid).
- **Más emuladores de fuera**: el catálogo de ES-DE, al día (Vita3K y EmuCoreV para PS Vita, BachataS4 para PS4, aPS3e y ARMSX3 por
  número de serie, ScummVM, GameNative y GameHub Lite), con lo que hacía falta para abrirlos (`%INJECT%`, las listas de texto). Se rehace
  con `herramientas/hiedra/emuladores-es-de.py`.

**La cara nueva**

- **Sistemas**, en casillas: cada consola de Bastet con su color, su nombre corto en grande, su núcleo y cómo está (bajada, se baja al
  jugar, en pruebas, falta la BIOS); un toque abre su emulador.
- **El mando se ve en toda la app**: lo que tiene el foco (con la cruceta del mando o el teclado) lleva un marco del acento, en todas las
  pantallas, y las portadas enfocadas crecen un poco, con un rebote corto. A elige, B vuelve.
- **El menú del juego, rehecho**: en apaisado, a la izquierda la imagen del momento en que pausaste y a la derecha el menú; la fila
  enfocada se marca con una barra del acento. Con mando, abajo, qué hace cada botón. Entra con un fundido.
- **La pantalla de carga** enseña la caja del juego y de qué consola es, con una barra que se mueve siempre (nunca parece colgada).
- **Tablet** (la OPPO Pad Mini, de pie y tumbada): la portada con el destacado en doble página (la caja grande, el titular y «Jugar»),
  portadas más grandes en los estantes, la ficha en dos columnas y las secciones a un ancho de revista.
- **Transiciones**: entre páginas, un fundido corto con un poco de movimiento; la ficha entra frenando y se va más deprisa.

Lo que no se ha podido probar (honesto): **nada de esto se ha probado en un móvil de verdad**. Las pruebas del ordenador comprueban el
catálogo (cada núcleo existe en el buildbot con ese nombre y abre las extensiones que encuentra el escaneo), los mandos de cada consola
(cada botón de la pantalla va a uno que el núcleo usa), las BIOS, los discos de varios archivos y el emulador automático; las capturas
enseñan cada mando y las pantallas en el móvil y en la tablet. Pero los núcleos son código de ARM y no corren en el ordenador: si una
consola «lista» falla, es un error; las «en pruebas» pueden ir lentas o no arrancar (N64 y PSP piden mucho; PlayStation va con el
intérprete, como en Lemuroid). La DS guarda su partida dentro de Bastet, no en la carpeta de guardados. No hay segundo jugador ni
vibración.

## 0.5.4

- **Los juegos del PC ya no se meten solos en «Pendiente».** Hasta ahora, todo lo que tenía horas en la biblioteca del PC entraba solo en
  Bastet (y en Jonsu), y si no lo habías jugado en dos semanas iba a «Pendiente»; al borrarlo, el PC volvía a mandar su biblioteca y
  se creaba otra vez, con otro id. Ahora en tus secciones solo entra solo lo que juegas en el PC (lo que está ejecutando o lo jugado en
  los últimos 14 días), y en «Jugando». Lo demás está en «Tu PC» y entra cuando lo añades («Añadir a Bastet»).
- **Lo que borras no vuelve solo**: un juego borrado sigue en la cuenta como borrado, con su lanzador del PC, y Bastet lo mira antes de
  meter nada (ni aunque lo estés jugando). Si lo añades a mano desde «Tu PC», vuelve el de antes (el mismo juego, con su estado y su nota).
- **«Tu PC» → Biblioteca → «Quitarlos»**: los que entraron solos en «Pendiente» y nadie ha tocado (ni estado, ni nombre, ni portada, ni
  nota en Jonsu; solo con el lanzador del PC y fuera de sagas) se quitan de una vez, con la lista delante antes de confirmar. Siguen
  en la biblioteca del PC.

- **Una pantalla que no dejaba de redibujarse**: los títulos que se encogen para caber (FitTitle) apuntaban su ancho desde dentro de la
  medida; en una fila de dos casillas Compose mide dos veces (para saber el alto y para pintar) y, si los dos anchos diferían en un píxel
  por redondeo, se pisaban en cada fotograma: la pantalla se recomponía sin parar (CPU y batería) mientras estaba abierta. Pasaba en la
  tablet en la ficha de un juego «Jugando» de Jonsu; la misma pieza está en las cuatro apps y en las de escritorio, y se arregla en todas.

## 0.5.3

- **Bastet del ordenador (Linux y Mac) sale en «Tu PC»** como un PC más: su biblioteca (Steam y tus programas), a qué juegas y abrir
  un juego desde el móvil. Ese PC cuenta él mismo su tiempo (lo dice en su presencia, `tiempo: true`): el móvil ya no lo vuelve a apuntar,
  así sus horas no salen dobles.

## 0.5.2

- **Las fechas que llegaron de Atrio estaban en 1970.** Atrio guarda el día de empezar y de terminar como número de día, y al
  traerlo se tomó por milisegundos: los juegos terminados en Atrio quedaron terminados el 1 de enero de 1970. Ahora se leen
  bien, se corrigen una vez en tu cuenta (así las ven igual Jonsu y tus otros dispositivos) y lo que se traiga de Atrio llega bien.

## 0.5.1

- **Los estantes ya no saltan.** Cada portada reserva siempre dos líneas para el nombre: al deslizar un estante, lo de debajo
  no sube ni baja según el título que entre (también en «Tu PC»).
- **Las pestañas de arriba se dejan deslizar** (antes volvían solas a la elegida mientras las arrastrabas) y **las opciones de
  orden se ven todas** (si no caben en una línea, siguen en la siguiente; antes la última salía cortada y, al intentar deslizarla,
  pasabas de página).
- **Entrar una vez, de verdad.** Si entras en otra app de la familia mientras esta sigue abierta, esta entra sola en cuanto
  vuelves a ella (antes solo lo miraba al arrancar, y podía quedarse sin cuenta hasta que Android la cerrara).
- **Salir y volver a entrar ya no deja la app sorda.** Al salir de la cuenta se tiraban las colecciones y lo que las escuchaba se
  quedaba oyendo unas que ya no cambiaban: ahora son las mismas, vacías, y se siguen oyendo.

## 0.5.0 — la familia

- **La cuenta de la familia, de cero.** Cada cosa está una sola vez en la cuenta, campo a campo: dos apps o dos móviles que
  tocan campos distintos nunca se pisan, y un móvil que vuelve a tener red después de días no pisa lo que cambiaste luego en otro
  (las reglas del servidor lo comprueban). Funciona sin conexión: lo que cambias se ve al momento y se sube solo en cuanto hay red.
  Ver `nube/ESQUEMA.md`.
- **Entrar una vez.** Entras en Jonsu (o en cualquiera de la familia) y las demás entran solas en ese móvil.
- **Las claves, una vez.** El token de kDrive y las de los servicios se ponen una vez y las tienen todas las apps (si ya las tenías
  en este móvil, se suben solas a la cuenta). Se gestionan en Jonsu (Ajustes → Avanzado); un token mal pegado en
  cualquier app nunca pisa el bueno de la cuenta.
- **Actualizaciones sin preguntar** también en Android 17 (la app apunta a Android 15 y declara el permiso de actualizarse sola).
- **Una sola firma para las cuatro.** Esta versión se instala encima de la anterior, sin desinstalar, pero **a mano esta vez**: el
  actualizador de antes no acepta la firma nueva. A partir de aquí, se actualiza sola.
- **La primera vez** (en cada móvil), lo de antes pasa a la cuenta nueva con la hora real de cada cambio. Lo de antes no se borra.
- **Las portadas viajan con la cuenta** (no por kDrive): la que pones en un móvil sale igual en los demás y en Jonsu, y quitarla la quita en todos.
- **Las horas**, por fuente (este móvil, tus otros móviles, Steam, cada PC) y sumadas.
- **Los juegos repetidos** (el mismo importado en dos móviles con dos ids, como el Baldur's Gate 3) quedan en uno, con sus horas.
- La nota de Jonsu se lee del propio juego.

## 0.4.5

- **Juegos repetidos, juntados solos.** Al importar de Atrio o de Carmesí en dos móviles, cada uno creaba su copia del mismo juego (con
  su propio id): al quitar una, volvía la del otro móvil (pasaba, por ejemplo, con Baldur's Gate 3). Ahora, dos fichas con una clave en común
  (la misma ROM, app, juego de Steam o del PC, o el mismo título) y la misma plataforma se juntan en una (horas sumadas, el estado más
  avanzado) al abrir Bastet y al sincronizar, y la copia sobrante se quita también en tus otros móviles.
- **La misma proporción de portada en toda la app (2:3)**, la de las carátulas verticales de SteamGridDB (600×900): la pantalla de inicio y
  las listas usaban 3:4 y la ficha 2:3, por eso en el inicio se cortaban arriba y abajo.
- El orden «Pasado» salía en todas las secciones: ahora se llama «Cuándo lo pasaste» y solo está en Pasados.
- **Fechas y cifras como en las otras tres** (`Formato.kt`, con las mismas pruebas en las cuatro): «16 oct 2026» en fichas y textos, «16/10/2026» en las tablas de datos y siempre con punto de millar («4.010»). Los meses van escritos en la app (Android, según la versión, les ponía punto: «oct.»).
- **«Jugar» es un botón grande** en la ficha, como «Seguir leyendo» en Seshat y «Seguir escribiendo» en Thot (la saga, al lado, en pequeño).
- Ajustes: la pestaña «Servicios» se llama **«Cuenta»**, como en Seshat y Thot (Servicios sigue dentro, como sección).
- «En Jonsu» sin nota: una línea, «Sin valorar aún · Valorar en Jonsu →» (antes eran dos que decían lo mismo).
- Los contadores de las pestañas van en tinta (más oscura la de la pestaña elegida), no en rojo: el rojo queda para lo que se toca.
- En las listas, la línea de debajo del título (plataforma, horas…) un punto más grande y más legible.
- **Las portadas de letra, con las telas de Seshat**: la misma paleta en las cuatro apps (un título, siempre el mismo color) y la inicial en la letra de los títulos. Antes cada sistema tenía su color.
- **La cabecera de la portada se pliega al bajar**: arriba solo se quedan el nombre y «Buscar · + Añadir»; el número, la fecha, las barras y el menú se van con la página.
- Debajo de «Bastet · hecho en casa», en gris, la versión instalada.

## 0.4.4

- **Cambiar qué juego sigue un widget sin quitarlo**: déjalo pulsado en el escritorio y sale «Reconfigurar» (Android 12 o más, en los
  lanzadores que lo ofrecen, como el de Pixel): abre la misma lista que al ponerlo («Sigue jugando» y «En portada»). El menú del dedo pulsado es del escritorio,
  no de la app: lo único que una app puede hacer es decir que su widget se puede reconfigurar, y eso es lo que se ha hecho.

## 0.4.3

- **Las portadas que eliges, de verdad en todos tus móviles** (como los libros en Atrio). Antes solo se miraban cuando cambiaba la
  biblioteca de este móvil (y elegir una portada no contaba como cambio), un móvil que ya tenía una no recibía la nueva, y quitar una no
  llegaba a los demás. Ahora se miran cada vez que se sincroniza (al abrir Bastet, al llegar algo de otro móvil y al cambiar algo aquí) y
  manda el cambio más reciente: la pones o la cambias en un móvil y llega a los demás; vuelves a la automática y se quita en todos.
- **Las que se habían «perdido» vuelven solas**: si el juego dejó de apuntar a su portada pero esa portada sigue en tu kDrive, se vuelve a
  poner (del archivo que aún está en el móvil, sin bajar nada, o bajándola). Si el archivo de una portada ya no está, se ve la de internet
  o la automática en vez de un hueco vacío.
- La portada de internet (SteamGridDB…) de un juego ya no se borra al mezclar con un móvil que no la tenía.

## 0.4.2

- Arreglo: al mezclar tu biblioteca con la de tus otros móviles se podía perder la portada que acababas de poner (o que se acababa de
  bajar de tu kDrive) si llegaba justo mientras se mezclaba. Ahora la portada se toma de como está en ese momento.

## 0.4.1 — las ROMs, de un móvil a otro

- **Al añadir una ROM** (sueltas o al recorrer una carpeta) se sube sola a tu kDrive, con wifi, sin esperar a volver a abrir Bastet. Si
  añades más mientras sube, también. Desde su ficha se puede subir ya, también con datos.
- **En tu otro móvil, «Jugar» la baja**: si la ROM de un juego está en tu kDrive y no en ese móvil, «Jugar» (o su acceso directo)
  enseña de qué archivo se trata y cuánto pesa, la baja con su progreso a tu carpeta de ROMs (en la subcarpeta del sistema, «gba»,
  «snes»…, donde la encuentran tus emuladores), comprueba que ha llegado entera, la deja apuntada y abre el juego. La primera vez, si
  no tienes carpeta de ROMs en la que se pueda escribir, te pide una y se queda.
- **El jugador de Bastet (GBA) la baja él solo** al abrir el juego, con su progreso en la pantalla de carga; sin carpeta de ROMs, la
  guarda dentro de Bastet.
- **Ajustes → Avanzado → «Bajar las ROMs que faltan en este móvil»**: todas las que están en tu kDrive y aquí no, con wifi y en segundo
  plano (sigue aunque cierres Bastet; si se corta, sigue por donde iba).
- Las carpetas de ROMs se guardan ahora con permiso de escritura (para dejar ahí las que bajas). Las que ya tenías solo con lectura:
  la primera bajada te pide elegirla otra vez.
- Los «días seguidos» siguen la regla común de la familia (`Racha.kt`, documentada en el contrato): un minuto jugado cuenta. Sin
  cambios para ti: era la misma regla.

Lo que no se ha podido probar en un móvil: la bajada y la subida de verdad contra tu kDrive (la parte de red es la misma que ya usaban
las ROMs desde la ficha; lo nuevo es cuándo se lanza y dónde se deja).

## 0.4.0 — el jugador interno (Fase A: Game Boy Advance)

Bastet juega ella misma a los juegos de **GBA**, sin otra app: con el núcleo **mGBA** de libretro (vía LibretroDroid), y con lo que un
emulador de fuera no puede darle: sabe cuándo acabas, guarda donde estás al salir y lo sube a tu nube para seguir en otro móvil.

- **Bastet es un emulador más** en la lista del sistema («Bastet», el primero) y el automático para GBA. Si prefieres otro, se elige como
  siempre en la ficha o en Sistemas. En la ficha, en «Emulador», está el núcleo: cuál es, de cuándo, cuánto ocupa, y **Descargar ahora**,
  **Actualizar** (avisa de que los estados de la versión vieja pueden no cargar; la anterior se guarda) y **Quitar**. Si no lo tienes, se
  baja solo la primera vez que juegas (del buildbot de libretro, unos 400 KB).
- **Guardado rápido a la nube**: al salir del juego por cualquier camino (el menú, atrás, el botón de inicio, otra app encima) se hace un
  estado automático, se guarda la partida del cartucho y se sube a tu kDrive en segundo plano (con reintentos si no hay red). Mientras
  juegas, también cada pocos minutos. Al volver a jugar, aquí o en otro móvil, se carga el más reciente; si el de la nube lo hizo otra
  versión del núcleo, te pregunta. Además, tres huecos a mano en el menú (con miniatura).
- **La partida del cartucho** (.sav) se deja en la carpeta de guardados del sistema, con el nombre de la ROM: la misma que ya sincroniza
  Bastet y que entienden los demás emuladores.
- **Avance rápido**: ×2, ×3, ×4 u ×8, mientras lo pulsas o con un toque para encender y otro para apagar, con o sin sonido. En el mando,
  R2 o el gatillo derecho (GBA no los usa); en la pantalla, el botón ▶▶. Arriba se ve a cuánto va y, si el móvil no llega, la velocidad
  real («▶▶ ×8 · real ×5,3»).
- **Controles táctiles** (RadialGamePad, el de Lemuroid): cruceta con diagonales, A y B, L y R, Start y Select. Opacidad, tamaño y vibración.
- **Mandos físicos**: en cuanto conectas uno (Bluetooth o USB), los controles táctiles se ocultan; si tocas la pantalla vuelven hasta que
  uses el mando. Los botones van **por posición** (el de la derecha es el A, como en la consola; lo que hacen RetroArch y Lemuroid) o, si
  lo prefieres, por la letra pintada. Cruceta o stick izquierdo, gatillos analógicos como botones. Cada mando guarda su configuración: en
  «Cambiar botones del mando» eliges una función y pulsas el botón. El botón central del mando (Home/Guide) o Select + Start abren el
  menú; con el mando se navega por él.
- **Menú en pausa**: continuar, guardar y cargar estado, avance rápido, mando y controles, imagen (Nítido, Suave, LCD, CRT; apaisada,
  vertical o la del móvil), captura (a Imágenes/Hiedra), reiniciar y salir.
- **Diagnóstico de mando**: el nombre, los ids y las últimas teclas y ejes que manda tu mando, para copiarlos y pegarlos si algo no va.
- Licencia: LibretroDroid y RadialGamePad son GPL-3.0, así que **Bastet pasa a ser GPL-3.0** (archivo `LICENSE`). mGBA es MPL-2.0.

Lo que falta y lo que no se ha podido probar (honesto):

- **No se ha probado en un móvil de verdad**: ni el núcleo corriendo, ni el rendimiento, ni tus mandos (el GameSir que se divide y el
  8BitDo). Las pruebas del ordenador comprueban las reglas (mandos, avance rápido, qué estado se carga) y las capturas enseñan el táctil
  y el menú, pero el juego en sí (código nativo de ARM) no corre en el ordenador. Si un mando hace algo raro, el diagnóstico lo dice.
- La velocidad real del avance rápido se mide contando imágenes por segundo: es una aproximación.
- Solo GBA y solo móviles de 64 bits (arm64). Los demás sistemas 2D llegan en la Fase B; PSX, N64, DS, PSP y arcade, en la C.
- Sin pantalla de BIOS (GBA no la necesita), sin ajustes del núcleo y sin segundo jugador.
- Un estado solo lo entiende el mismo núcleo: no sirve para seguir en otro emulador (la partida del cartucho, sí).

## 0.3.14

- En las filas de datos (etiqueta · puntos · cifra), si no cabe todo en una línea, la etiqueta va entera arriba y la cifra debajo, en vez
  de cortar la etiqueta con puntos suspensivos (como en Jonsu 1.2.8).

## 0.3.13

- El widget «Sigue jugando» alto y estrecho ya no corta su rótulo: ahí dice «Jugando».

## 0.3.12

- La fila de orden y filtros (Recientes · Nombre · …) lleva el mismo degradado en los bordes que las pestañas.
- El degradado de los bordes de las pestañas se pintaba fuera de la vista (no se veía): ahora sí. Y el título de la sección va en plural
  como su pestaña («Pasados»).

## 0.3.11

- Las secciones se llaman en plural, como en Seshat y Jonsu: Jugando · Pendientes · Pasados · Abandonados · Sagas (también los estantes
  de la pantalla de inicio). El estado de cada juego en su ficha sigue en singular (Pendiente, Pasado…).
- Las pestañas (secciones, Ajustes) se desplazan solas solo cuando la elegida no se ve entera, y entonces la centran: antes se
  pegaba al borde izquierdo y la de al lado salía cortada.
  Y en los bordes, un degradado cuando hay más pestañas por ese lado.

## 0.3.10

- Arreglo del cierre automático de la sesión (0.3.9): la siguiente mirada se encadena detrás de la que está corriendo, en vez de
  reemplazarla (reemplazarla podía cancelar la que estaba a medias y dejar la sesión sin cerrar).

## 0.3.9

- Lo que no llegó a subirse (sin red al volver de jugar, o Android congeló Bastet antes de acabar) lo sube un trabajo de fondo
  (SubidaWorker) en cuanto hay red. Si Bastet ya lo subió, no hace nada.
- Con el permiso de uso, la sesión de juego se cierra sola cuando el emulador (o el juego) deja de estar delante, aunque no vuelvas a
  Bastet: cada cuarto de hora se mira (SesionWorker) y, al cerrarla, las horas se apuntan y suben a tu cuenta al momento (Jonsu las
  ve en su Diario). Antes solo se apuntaban al abrir Bastet otra vez.

## 0.3.8

- **Arreglo del tiempo exacto**: con el permiso de uso, un emulador con varias pantallas (Dolphin, PPSSPP, DuckStation… pasan de su menú a la
  pantalla del juego) hacía que se perdiera casi toda la partida: Android apunta «detrás» la pantalla vieja un momento después de poner
  «delante» la nueva, y eso se tomaba por salir de la app. Ahora la app cuenta como delante mientras alguna de sus pantallas lo esté.

## 0.3.7

- Textos al día: lo que seguía diciendo «Atrio» para hablar del móvil (seguir sus colores en directo, copiar su apariencia de tu cuenta,
  «los mismos que en Atrio») dice ahora Jonsu, que es quien está en el móvil. La cuenta sigue siendo la cuenta de Atrio.

## 0.3.6

- **Arreglo**: en Android 13 o más, los avisos del actualizador («Bastet tiene una versión nueva», «Bastet se ha actualizado, toca para abrirla»)
  no se veían nunca: faltaba el permiso de avisos. Ahora se declara y se concede desde Ajustes → Avanzado («Avisos de actualización»).

## 0.3.5

- Lo que Jonsu sube (sus fichas, para las claves de los servicios y «En Jonsu») no se vuelve a bajar si sus sellos no han cambiado:
  menos datos cada vez que Bastet sincroniza.

## 0.3.4: la nota de Jonsu, al momento

- Bastet escucha el sello de Jonsu: en cuanto valoras un juego allí, «En Jonsu» en su ficha cambia sin salir ni esperar los 15 minutos.

## 0.3.3

- En la portada, «+ Añadir» en el acento, como en las listas y como en Seshat y Thot; el pie de Ajustes dice de quién es hermana.

## 0.3.2: el tiempo de juego exacto

- **Ajustes → Juegos → Tiempo de juego**: con el permiso «acceso al uso» de Android (se da desde ahí), al volver a Bastet se cuenta solo lo
  que el emulador o el juego **estuvo delante**, y la sesión se reparte entre días según cuándo acabó de verdad. Sin el permiso, como siempre:
  todo el rato hasta volver (tope de 12 horas). Los juegos web ya se contaban dentro de Bastet. `UsoTest`.

## 0.3.1

- El nodo ligero `tiempo` (lo que lee el Diario de Jonsu) se sube **antes** del sello `stamps`: Jonsu refresca al oír el sello y antes
  podía leer las horas viejas.

## 0.3.0: Ajustes en pestañas y la caja automática para Jonsu

- **Ajustes** en pestañas, como Atrio: Apariencia · Juegos (sistemas y emuladores, portadas automáticas, pantalla de inicio, capturas del PC) ·
  Servicios (la cuenta de Atrio, traer de Carmesí y de Atrio, las claves) · Avanzado (kDrive y actualizaciones, copias). Lo mismo, sin tenerlo todo a la vista.
- **Portadas para Jonsu**: si un juego no tiene portada tuya y «Portadas automáticas» está encendido, la dirección de su caja de libretro viaja
  en la biblioteca (`pa`), así Jonsu enseña la misma caja que ves aquí. Las tuyas siguen viajando por `pu` (internet) o kDrive (galería).

## 0.2.1

- **Jonsu**: una ficha que Jonsu refleja de Bastet y que aún no has valorado (todas las notas a 0, sin reseña) ya no sale en «En
  Jonsu» como un 0: sigue «sin valorar».

## 0.2.0: la ficha cuenta más, y las claves se ven

- **Datos** en cada ficha, como en Seshat: cuándo salió, el estudio, el editor, los géneros, la nota de Metacritic y un resumen, más lo de aquí
  (plataforma, ROM, app, web, PC, saga, añadido, pasado, partidas). Vienen de la **tienda de Steam** (sin clave: por el appid si es un juego de
  Steam en tu PC, o buscándolo por nombre si coincide) y, si no está ahí, de **RAWG** (con tu clave; tiene también los juegos de consola de
  siempre). Se guardan dos semanas en el móvil; «Buscar de nuevo» los pide otra vez.
- **Lo que suele durar** se rellena solo en los juegos que no lo tengan: de **IGDB** (lo que registran sus jugadores: historia, con extras y al
  completo; con tu Client ID y secreto de Twitch) o, si no, la media de horas de RAWG. Se dice de dónde salió. Solo sale en los juegos que lo tienen.
- **Servicios**, en Ajustes: ahora se ve qué clave hay de cada servicio (la tuya, la de Atrio o ninguna) y el estado del kDrive. Y se arregla lo
  que fallaba: Bastet no leía las claves de Atrio nunca (la función existía pero no se llamaba); ahora se traen al vincular la cuenta, en cada
  «Sincronizar ahora» y al abrir Ajustes. Hay campos para RAWG e IGDB.
- El **token de kDrive no viene de la cuenta**, a propósito: todo lo que hay en `/users/<uid>` lo puede leer tu pareja (modo pareja), y el token
  da acceso a todos tus archivos. Atrio tiene una prueba que lo prohíbe expresamente. Se pega en Ajustes → Actualizaciones, como en Atrio.

## 0.1.1

- **Icono nuevo**: una hoja de hiedra rellena (sus cinco lóbulos, el tallo y los nervios vaciados) en verde claro sobre verde muy oscuro, dibujada
  por `herramientas/icono-hiedra.py`.
- **La importación de Carmesí trae también la nube**: los guardados y las ROMs que Carmesí subió a tu kDrive pasan a ser los de Bastet (se copia
  su índice de la cuenta al de Bastet, por el id de cada juego; los archivos de kDrive son los mismos), sin volver a subir nada. Probado de punta a
  punta contra el Firebase de mentira (`SincroTest`).

## 0.1.0: la primera

- **Lo de Carmesí, entero, sobre el modelo nuevo**: portada de revista con el destacado, los estantes (orden, tamaño, a la vista, «Mis elegidos»)
  y el mando; las secciones Jugando · Pendientes · Pasados · Abandonados · Sagas como páginas, con los menús pequeños PC · Emulación · Android · Web
  y los órdenes; la ficha (estado, favorito, portada propia o de internet con su encuadre, lo que suele durar, la saga, «En Jonsu» de solo
  lectura, la nube); añadir (carpetas de ROMs con escaneo, apps, webs, el PC); sistemas y emuladores; **Tu PC** (en directo, biblioteca, capturas,
  equipo; jugar en el PC o en streaming); las horas de Steam; guardados, ROMs y portadas por tu kDrive; el buscador de portadas; la apariencia;
  los widgets (*Sigue jugando*, *Estante*, *Portada*, *El siguiente*, *BASTET*, *BASTET en papel*).
- **La ficha enseña cada manera de abrirse** («Cómo se abre»: cada lanzador con su emulador, quitar, «+ Añadir») y el desglose de las horas por
  fuente (este móvil, otros móviles, Steam, el PC, Carmesí).
- **Traer mi biblioteca de Carmesí** (Ajustes → Cuenta): lee lo que Carmesí subió a tu cuenta (en Carmesí no cambia nada) y hace de cada juego un
  juego de Bastet con su lanzador, sus claves, estado, favorito, plataforma, portada de internet, encuadre y duraciones; las horas de cada
  dispositivo de Carmesí quedan como fuente `carmesi:<dispositivo>`; las sagas, la portada de inicio y la apariencia se traducen; tus portadas se
  bajan de kDrive. Lo que ya tengas aquí se completa sin duplicarse. Se puede repetir. **Traer mis juegos de Atrio** sigue estando, para lo que no
  estuviera en Carmesí.
- **Para Jonsu** (`docs/CONTRATO-DATOS.md`): el nodo ligero `tiempo/<id>/<fuente>` con las claves del juego (para enlazar lo valorado por la
  clave antigua), un solo nodo por fuente.
- **Icono**: una hoja de hiedra sobre verde muy oscuro, adaptable (`herramientas/icono-hiedra.py`).
- **Se actualiza sola** desde la carpeta «versiones» de tu kDrive (`Bastet-<versión>.apk`).

## Capturas

Generadas en el ordenador con juegos de ejemplo (`CapturasTest`, `-Proborazzi.test.record=true`): [portada](docs/capturas/2_portada.png) ·
[estantes](docs/capturas/20b_inicio_estantes.png) · [editando la portada](docs/capturas/21_inicio_editando.png) · [en oscuro](docs/capturas/7_portada_oscuro.png) ·
[Jugando](docs/capturas/3_juegos.png) · [Pendientes](docs/capturas/3b_pendiente.png) · [Pasados](docs/capturas/3c_pasado.png) ·
[Pasados, solo Emulación](docs/capturas/3d_pasado_emulacion.png) · [Sagas](docs/capturas/3e_sagas.png) · [una saga](docs/capturas/3g_pagina_saga.png) ·
[ficha](docs/capturas/6_ficha.png) · [ficha con dos lanzadores](docs/capturas/6c_ficha_dos_lanzadores.png) · [ficha sin lanzador](docs/capturas/6b_ficha_sin_lanzador.png) ·
[ficha, casillas](docs/capturas/12_ficha_casillas.png) · [ficha, datos (RAWG)](docs/capturas/12b_ficha_datos.png) · [ficha, datos (Steam)](docs/capturas/12c_ficha_datos_steam.png) · [servicios](docs/capturas/31_ajustes_servicios.png) · [añadir](docs/capturas/9_anadir.png) · [sistemas](docs/capturas/4_sistemas.png) ·
[Tu PC](docs/capturas/23_tu_pc.png) · [biblioteca del PC](docs/capturas/24_tu_pc_biblioteca.png) · [ficha de un juego del PC](docs/capturas/26_ficha_pc.png) ·
[ajustes](docs/capturas/5_ajustes.png) · [ajustes: juegos](docs/capturas/5b_ajustes_juegos.png) · [ajustes: servicios](docs/capturas/5c_ajustes_servicios.png) · [ajustes: avanzado](docs/capturas/5d_ajustes_avanzado.png) · [ajustes con fondo](docs/capturas/30_ajustes_fondo_oscuro.png) · [vacía](docs/capturas/1_portada_vacia.png).
Los widgets, en `docs/widgets/`.

## Cómo se trabaja

- `ContratoTest` deja en `app/build/outputs/contrato/hiedra.json` lo que Bastet sube a la cuenta tal cual; se copia a
  `azabache/herramientas/casos/hermanas/` y la prueba `ContratoHermanasTest` de Jonsu comprueba que lo lee entero. Al revés,
  `herramientas/casos/azabache.json` es lo que sube Jonsu (su `ContratoTest`) y `NotaAzabacheContratoTest` comprueba que «En Jonsu» lo lee.
- Pruebas: `gradle :app:testDebugUnitTest` (0 fallos). Con `-Proborazzi.test.record=true` se graban las capturas (`app/build/outputs/roborazzi/`)
  y los widgets (`app/build/outputs/widgets/`, de donde salen las vistas previas `w_prev_*.png`). Las de sincronización y nube usan los servidores
  de mentira de Atrio (`Atrio-Suite-2.0/herramientas/fakefirebase.js` y `fakekdrive.js`, con `node`); sin ellos se saltan.
- Versión nueva: subir `versionCode`/`versionName` (`app/build.gradle.kts`) y `VERSION` (`PantEstilo.kt`), `gradle :app:assembleRelease`, y
  `python3 herramientas/subir-version.py Bastet-<versión>.apk` la deja en tu kDrive para que los móviles se actualicen solos.
- El modelo y lo que viaja: `docs/MODELO.md`; lo que Jonsu lee: `docs/CONTRATO-DATOS.md`.
