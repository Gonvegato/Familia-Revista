# Jonsu

La revista de la familia: lo que juegas, lees, ves y escribes, valorado y contado. Es **Atrio del móvil con casa propia y menos cosas**:
mismo paquete (`com.gonzalo.gamerate`) y misma firma, así que **se instala encima de Atrio** y se queda con todo (tus notas, tu cuenta, tu
pareja, tus maquetas). Las otras apps la siguen encontrando donde estaba (los colores de Atrio, «Abrir en Jonsu»).

Hermana de [Bastet](https://github.com/Gonvegato/hiedra) (juegos), [Seshat](https://github.com/Gonvegato/lectura-app) (libros) y
[Thot](https://github.com/Gonvegato/cobalto) (escribir). Jonsu **no añade juegos ni libros**: se añaden, se juegan y se leen en sus apps,
y aquí aparecen solos, con su estado, sus horas y sus portadas, para valorarlos. Las películas, las series y tus categorías propias siguen
aquí tal cual (con su «Añadir»).

## 1.8.6 — los ajustes, como los de las hermanas

- **La cabecera de Bastet, Seshat y Thot**: el bloque negro con «← JONSU» y «AJUSTES.», y las pestañas en bloques (la elegida, en
  ciruela). Fuera el «Sección / Ajustes» y las pestañas subrayadas.
- **Cinco pestañas**: **Apariencia** (colores de Jonsu, tema, fondo, tamaño del texto, portadas en las listas, «A tu manera»),
  **Jonsu** (objetivos del año, tus categorías, repasar notas, lista negra, rellenar estudios), **Cuenta** (cuenta y pareja, tu nube,
  Google Drive), **Claves** (nueva: arriba, cuáles tienes puestas; debajo, SteamGridDB, TMDB, RAWG, IGDB y Steam) y **Avanzado**
  (actualizarse sola, copias, de dónde sale cada cosa).
- Fuera la lista larga de acentos de Apariencia (más de 30 colores, que las hermanas no tienen; además marcaba «Rojo» aunque el acento
  fuera otro). El acento a mano sigue en «A tu manera».
- El engranaje de la portada abre Apariencia, como en las hermanas. Los avisos que mandaban a «Ajustes → Avanzado → Servicios» mandan ahora
  a «Ajustes → Claves».

## 1.8.5 — la cabecera de color, de lado a lado

- En el estilo «cabecera de color» la franja del acento llega de un borde al otro del widget (antes, junto a una portada, empezaba a su
  derecha); la portada queda encima. Lo que va sobre una foto (la fecha de «En portada») no cambia de color.

## 1.8.4 — los widgets, con el estilo que elijas

- **Al poner cualquier widget de Jonsu** (también la cabecera con el nombre, el calendario, el año, la actividad…) se abre una pantalla para
  elegir cómo lleva el acento: **como ahora**, **cabecera de color** (una franja del acento arriba), **papel teñido** (todo el fondo con un
  poco del acento), **marco de color** (un borde grueso del acento) o **todo acento** (el fondo es el acento y el texto va en papel o negro,
  el que se lea mejor). Cada opción se ve con el widget de verdad, de día y de noche.
- La cabecera sin papel (solo el nombre sobre el fondo de pantalla) tiene dos: como ahora o **el nombre en el color del acento**. Si un
  estilo no cambia nada en un widget (sobre una foto a sangre la franja y el papel teñido no se ven), no se ofrece.
- **Para cambiarlo después**: mantener pulsado el widget → ajustes o «configurar» del widget (el nombre depende del lanzador; en algunos
  lanzadores no existe y hay que quitar el widget y volver a ponerlo). Los widgets que ya tenías puestos siguen como estaban.
- El granate oscuro de Nut se aclara un poco en la franja y el marco de noche, para que se vea sobre el negro.

## 1.8.3 — los colores de la familia, de día y de noche

- **Las seis apps comparten colores**: de día, papel #F8F0D4 con texto negro; de noche, al revés (negro con texto #F8F0D4), siguiendo el
  modo del móvil o el que elijas en Apariencia. A cada app la distingue su acento: el de Jonsu, **ciruela (#8E5A78)**. Seshat ocre, Jonsu
  ciruela, Bastet oliva, Thot pizarra azul, Hathor rosa viejo y Nut granate oscuro.
- **Una vez, sola**: lo que tuvieras elegido a mano en Apariencia (fondo de color, acento, «A tu manera») se quita para que todo quede así;
  viaja a tus otros dispositivos. Se puede volver a elegir lo que quieras.
- **«Poner los colores de Jonsu»**, arriba del todo en Ajustes → Apariencia: vuelve a estos colores de un toque (el tema y el tamaño del
  texto se quedan).
- **Icono y widgets, de día y de noche**: el icono tiene versión clara y oscura (`drawable-night`); cuál se ve depende del lanzador (muchos
  no cambian el icono con el modo oscuro: Android no tiene una forma oficial de hacerlo). Los widgets cambian solos con el modo, y sus
  vistas previas del selector también tienen las dos versiones.
- **El fondo que pones en Seshat, Bastet o Hathor sale aquí.** Jonsu guardaba su propio fondo de cada cosa (en `az`) y no miraba el de la
  hermana. Ahora manda el de la cuenta (`fondo`/`fu` en juegos y libros, `fimg`/`fu` en películas y series; «-» = quitado a propósito); el
  de Jonsu solo vale si no hay ninguno. Y al revés: si lo cambias aquí, sube a la cuenta y sale igual en la hermana.

## 1.8.2 — los widgets, también con los colores nuevos

- Las vistas previas de los widgets (lo que sale al elegir un widget en la pantalla de inicio) estaban hechas con los colores de antes:
  rehechas con los nuevos. Los widgets ya puestos se pintaban con los colores nuevos desde la versión anterior (los leen del tema).

## 1.8.1 — colores nuevos, apagados

- **Colores de serie nuevos** («Ciruela»): gris ciruela (#2B2529), crema (#EAE2DD) y malva gris (#9C7B86), en lugar del negro, el crema
  y el granate. Salen si no has elegido otros en Apariencia; si tienes algo elegido, se queda. Nada más cambia de la dirección C.
- **Icono** con los colores nuevos, y en la portada del día, el color de cada hermana es el de su icono nuevo (Bastet oliva apagado,
  Hathor sepia, Thot azul niebla; Seshat, el mostaza de siempre).

## 1.8.0 — la cara de sus hermanas (dirección C) y la portada del día

- **Colores de Jonsu**: fondo casi negro #1C1F22, texto crema #F1EBE0 y el granate #8F3232, los del icono. Salen si no has elegido otros en
  Apariencia (fondo de color, colores a mano o acento); lo que elegiste se queda. El granate sobre el casi negro no se lee (2,1:1): en letras,
  cifras, la pestaña elegida y el botón principal va aclarado lo justo (4,5:1); en bloques y en el icono, tal cual. La cabecera es un bloque
  crema con letra oscura y el punto granate oscurecido para leerse ahí.
- **Una sola pantalla con páginas**, como Seshat, Bastet, Hathor, Nut y Thot: la cabecera con «JONSU.» (BUSCAR · PAREJA · la fecha · ⚙), que
  se recoge al bajar sin cortarse, y una fila de pestañas —PORTADA · DIARIO · VIDEOJUEGOS · LIBROS · CINE · tus categorías · ESCRITOS— que se
  pasan con el dedo. Atrás vuelve a la portada; las fichas, encima. «Cifras» está en la portada, debajo de la semana.
- **La portada del día**: lo que más tiempo se llevó hoy entre todas (un juego de Bastet, un libro de Seshat, una película o serie de Hathor;
  lo escrito en Thot, si no hubo nada con tiempo). Si hoy aún nada, lo de ayer o el último día de la semana con algo. Su portada grande con
  paspartú sobre su fondo (o su color), «HOY · 1 H 35 EN BASTET», el título, «y 35′ leyendo, 812 palabras» y «ABRIR EN BASTET →», que abre
  esa ficha en su app.
- **Los retos de todas**, de la cuenta y solo para leer: libros del año (Seshat, `ajustes/lectura`), juegos terminados (Bastet,
  `ajustes/juego_reto`), películas y series terminadas (Hathor, `ajustes/cine_reto`) y las palabras de hoy (Thot, `ajustes/cobalto_meta`,
  de serie 500). Cada uno con su barra y cómo vas; tocar uno abre su app.
- **Esta semana**: todo junto, en barras apiladas por hermana (cada una con su color y la leyenda); lo escrito, que no tiene tiempo, es un
  cuadrado bajo la barra. Tocar un día enseña su reparto («J · 1 h 35 jugando · 35′ leyendo · 812 palabras»). Y los días seguidos con algo.
- Debajo, lo de siempre: Ahora mismo (con «APUNTAR →» o lo que toque), Escritos, lo de tu pareja, los próximos episodios, las notas más
  altas, la cita y tal día como hoy.
- **Listas con portadas grandes** (84×126), numeradas, con el título en grotesca; arriba de cada categoría, sus cifras en bloques (que
  llevan a su pestaña).
- **La ficha, a doble página**: el fondo (o la portada ampliada) como foto de apertura con un velo arriba para «← Volver · Imágenes ·
  Editar» (y Pareja y ★), la portada con su paspartú colgando del borde, la etiqueta del estado, el título enorme y el botón principal
  (JUGAR EN BASTET, LEER EN SESHAT, VER EN HATHOR o VALORAR); debajo, la página de siempre, con sus secciones numeradas.
- **Encuadrar el fondo es de cada dispositivo**: la imagen (el `hero` que leen Seshat y Bastet) es la misma en todos; qué parte se ve se elige
  en cada móvil o tableta y vuelve al centro si la imagen cambia.
- Icono nuevo: «J.» con el punto granate y la franja «DIARIO» (adaptable y monocromo). Widgets en la misma dirección: titulares en grotesca,
  sin rebordes, tapas rectas, «JONSU.» con su punto y «JONSU en papel» con la cabecera en un bloque de tinta.
- Lo de siempre, igual: lo que leen las hermanas de Jonsu (su apariencia en la cuenta para «Copiar de Jonsu», el `hero`, las notas), el
  Diario, las valoraciones, la sincronización y «entrar una vez».

## 1.6.3 — las películas y las series, de Hathor

- **Tus películas y tus series son las de Hathor**, la app nueva de la familia para verlas (como los juegos son los de Bastet y los
  libros los de Seshat). La primera vez que abres Hathor, se lleva lo que tenías aquí con todo lo suyo (el estado, las fechas, la nota y sus
  criterios, la reseña, tus apuntes, el póster, el tiempo y los episodios marcados), y desde ese momento Jonsu **valora las de Hathor**:
  la misma ficha, sin copia, con su número de siempre (tus sagas, tu diario y tus maquetas la siguen encontrando). Lo que ves en Hathor
  (lo que empiezas, lo que terminas, los episodios que llevas, el tiempo) está aquí en cuanto llega a la cuenta; lo que valoras aquí, en
  Hathor («En Jonsu», en su ficha).
- **Añadir** una película o una serie abre Hathor (si está en este móvil; si no, se añade aquí como siempre y va igual a Hathor).
- En la ficha de una película o una serie, **«Ver en Hathor»** la abre allí (con tus addons, por donde ibas). Desde Hathor, «Valorar
  en Jonsu» abre aquí su ficha.
- Quitar una película o una serie aquí la quita en Hathor (queda borrada: no vuelve sola aunque la veas). Quitar la categoría entera no
  borra nada de Hathor.
- Hasta que Hathor no ha pasado por tu cuenta, todo sigue como siempre. Lo de antes no se borra: tus fichas de antes se quedan donde
  estaban, como copia. Jonsu de Linux y Mac (1.2.1) hace lo mismo: desde que Hathor ha pasado por tu cuenta, sus películas y series
  son las de Hathor, y lo que cambies allí llega aquí y al revés.
- Entrar una vez: Hathor puede pedirle la cuenta a Jonsu (y al revés).

## 1.6.2 — lo que eliges, en todos tus dispositivos

- **Lo que eliges en Jonsu y se quedaba en el móvil ya está en tu cuenta**: las recomendaciones de tu pareja que ya añadiste o descartaste (ya no vuelven a salir en el otro); los avisos (de
  lectura, del diario y de lo que hace tu pareja); el PC de Atrio elegido; las capturas en la galería y en tu kDrive; la web de imágenes;
  «Actualizar sola». Si en dos dispositivos a la vez cambias cosas distintas, se quedan las dos; si es la misma, la última.
- **Los objetivos del año**: uno nuevo solo llegaba a un dispositivo que aún no tuviera ninguno; ahora llega siempre el último que pones.
- **Tu nombre** (el que ve tu pareja) no llegaba nunca a la cuenta, así que tu pareja no lo veía; ahora sí, y es el mismo en todos tus
  dispositivos.
- **La letra del lector es la misma en todos tus dispositivos**, como en Seshat. El brillo sigue siendo de cada pantalla.
- La primera vez en cada dispositivo, lo que ya tenía pasa a la cuenta sin perder nada: las recomendaciones vistas se juntan; lo que tenían distinto, el del dispositivo donde lo cambiaste más
  tarde. Las maquetas, las recomendaciones y todo lo que ya viajaba se guardan como antes.
- Jonsu de Linux y Mac todavía no lee estos ajustes: llegará en su próxima versión.

## 1.6.1

- **Una versión nueva ya no te saca de lo que estás haciendo.** El actualizador instala en silencio cuando no usas la app, y Android la cierra para instalarla. Pero no siempre sabía que la estabas usando: si se entraba directamente a una pantalla que no es la portada (desde un widget, o cuando Android vuelve a abrir la app tras cerrarla por detrás), creía que no estabas; y con la pantalla apagada un momento, también. Ahora se lo pregunta a Android, nunca instala con la pantalla apagada o bloqueada.
- **Los títulos grandes ya no salen cortados** (se cortaban por el final hasta cambiar de pantalla y volver; en Seshat salía «Marfi»). Si tu apariencia usa una letra de Google Fonts, llega un momento después de abrir la app; el título se medía con la de mientras, más estrecha, y no se volvía a medir. Ahora sí.
- La portada ya no se repinta entera a cada píxel al bajar (gastaba batería).
- Si Android cierra lo que dibuja una página web de la app (las páginas web, la búsqueda de portadas), se cierra esa pantalla y no la app entera.

## 1.6.0 — mejores páginas

- **La ficha, en la tableta en horizontal, a dos columnas**, como una revista abierta: las filas de la página (las tuyas, si la
  diseñaste) se reparten en dos columnas en su orden, partidas donde quedan igual de altas, con un filete fino en medio. Antes era una
  sola columna de 1080 dp con líneas larguísimas y medio hueco en cada casilla. Al editarla se ve a una columna (para mover las filas).
  En la tableta el titular crece con el sitio (en vertical, un poco; en horizontal, más).
- **«Sesiones», casilla nueva** en las fichas de juegos y libros: lo que has jugado o leído, día a día (lo que cuentan Bastet y Seshat,
  sumando tus dispositivos), en barras —por día si fue cosa de pocas semanas, por semana o por mes si fue más larga—, con los días, la
  sesión más larga y la última (o, si ya lo terminaste, cuánto duró de principio a fin). Sale sola cuando hay algo; en las maquetas que
  ya tenías entra una vez detrás de «Horas» o «Lectura» (si la quitas, no vuelve).
- **Barras de avance**: jugando, lo que llevas frente a lo que suele durar la historia («vas por el 62 %»); leyendo, por dónde vas
  según Seshat y en qué página.
- **La portada viaja de la lista a la ficha** al abrirla (en la biblioteca y en «Ahora mismo»).
- **La biblioteca, a dos columnas en la tableta** (como el índice de una revista), con los filetes a la misma altura; y las opciones de
  orden y filtros, todas a la vista (en el móvil la última salía cortada).
- **Cifras más de revista**: los datos con puntos guía y barras finas (como en la ficha), «Tiempo jugando» además de «Tiempo leyendo»,
  y en la tableta en horizontal los bloques a dos columnas.
- **La portada en horizontal**: la cabecera llega de lado a lado y las barras de «Hoy» ya no son ladrillos (como mucho media página).
- **Arreglado**: la opción elegida (Pendiente · **Jugando** · Abandonado, Todo · 2026…) llevaba el subrayado por dentro de la letra y
  parecía tachada; la estrella y «Editar» de la ficha estaban a alturas distintas.
- **Prueba nueva** (`PantallasQuietasTest`, en el móvil y en la tableta en horizontal): cada página, una vez pintada, no se vuelve a
  componer ni un fotograma si no tocas nada (la que no dejaba de redibujarse de la 1.5.2 la habría pillado).
- Nada nuevo en la cuenta: «Sesiones» lee los nodos `tiempo/` de siempre y el avance de Seshat (`fr`).
- **En el ordenador (Jonsu para Linux y Mac 1.1.0)**, lo mismo que aquí: la ficha se lee como página de revista (las mismas
  casillas y, si diseñaste la página en el móvil, tu maqueta tal cual, a dos columnas) y «Editar» abre los datos; la portada con su
  número, «Hoy» y «En portada»; las cifras del año como estas; el diario por meses y días; la biblioteca también en índice; y tu
  pareja (su revista, sus fichas para leer y «Vosotros dos»). Las maquetas se diseñan en el móvil; allí se leen.

## 1.5.2

- **La portada de la ficha ocupa su casilla** (como mucho 200 dp de ancho, en 2:3). Antes era una miniatura de 96 dp y la fila se medía
  como si la portada llenara la casilla: en la tablet quedaba una portada pequeña con un hueco enorme debajo.

- **Una pantalla que no dejaba de redibujarse**: los títulos que se encogen para caber (FitTitle) apuntaban su ancho desde dentro de la
  medida; en una fila de dos casillas Compose mide dos veces (para saber el alto y para pintar) y, si los dos anchos diferían en un píxel
  por redondeo, se pisaban en cada fotograma: la pantalla se recomponía sin parar (CPU y batería) mientras estaba abierta. Pasaba en la
  tablet en la ficha de un juego «Jugando» de Jonsu; la misma pieza está en las cuatro apps y en las de escritorio, y se arregla en todas.

## 1.5.1

- Los capítulos de Thot llegan con su hora de edición (`ue`, ver `nube/ESQUEMA.md`).

## 1.5.0 — todo de la cuenta

- **Lo tuyo de Jonsu también es de la cuenta, sin copia**: tus categorías y sus fichas (películas, series, las tuyas), el diario,
  las sagas, las maquetas, la lista negra, lo de tu nube y lo leído se leen y se escriben directamente en la cuenta, elemento a
  elemento (como ya se subían). Ya no hay una copia en el móvil que se mezcle con la de la cuenta. En el móvil se queda solo lo que
  es de este móvil (la apariencia, los avisos…).

## 1.4.0 — sin copias

- **Tus juegos y tus libros son los de la cuenta, sin copia.** Jonsu ya no guarda los suyos aparte ni los «refleja» de Bastet y
  Seshat: los lee de la cuenta, cada vez, como ellas. No hay nada que se quede atrás: lo que terminas en Seshat está terminado aquí
  en cuanto llega a la cuenta, y lo que valoras aquí está en el libro de la cuenta. Sin conexión funciona igual.
- **Solo se escribe lo que cambias tú.** Si tienes una lista en pantalla de hace un rato y Seshat cambia un libro mientras, lo que
  hagas aquí no deshace lo de Seshat.
- **Lo que solo estaba en Jonsu** (lo valorado en Atrio que nunca pasó por Bastet ni Seshat) pasa a la cuenta común, con su nota,
  su fecha y su número de siempre (tus sagas, tu diario y tus maquetas lo siguen encontrando): Seshat lo enseña en Leídos y Bastet
  como una ficha sin lanzador.
- **Lo de tu pareja**: sus juegos y libros salen de su cuenta (con sus notas), también los de Bastet y Seshat.
- Pasar un libro a leídos (o al revés) cambia su estado en Seshat; quitar una ficha la quita de la cuenta.

## 1.3.1

- **Jonsu se queda al día con Bastet y Seshat aunque hayas entrado en otra app.** Al pasar de la 1.2, Jonsu necesita la
  cuenta de la familia; si entrabas después en Seshat o Bastet, Jonsu no se enteraba hasta que Android la cerraba, y mientras
  tanto enseñaba lo de antes (libros terminados aún «leyendo», los que empezaste escondidos en su saga de pendientes, sin las
  portadas nuevas de Bastet). Ahora entra sola al volver a ella, adopta la sesión al momento y trae lo de la cuenta.
- **«Comprobar la cuenta»** (Ajustes → Cuenta) dice lo que pasa de verdad: con qué usuario está dentro, si la cuenta responde,
  cuántos juegos y libros hay en la cuenta y cuántos han llegado aquí, y si el último reflejo falló, por qué (antes miraba el nodo
  de antes de la familia, que ya no se usa).
- El reflejo de lo de las hermanas va de uno en uno (lo pedían a la vez la sincronización, la vuelta a la app y la llegada de
  portadas).
- **Las pestañas de arriba se dejan deslizar** (antes volvían solas a la elegida mientras las arrastrabas) y **las opciones de
  orden se ven todas** (si no caben en una línea, siguen en la siguiente; antes la última salía cortada y, al intentar deslizarla,
  pasabas de página).
- **Entrar una vez, de verdad.** Si entras en otra app de la familia mientras esta sigue abierta, esta entra sola en cuanto
  vuelves a ella (antes solo lo miraba al arrancar, y podía quedarse sin cuenta hasta que Android la cerrara).
- **Salir y volver a entrar ya no deja la app sorda.** Al salir de la cuenta se tiraban las colecciones y lo que las escuchaba se
  quedaba oyendo unas que ya no cambiaban: ahora son las mismas, vacías, y se siguen oyendo.

## 1.3.0 — la familia

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
- **Arreglado: libros «leyendo» que salían leídos con un 0.** Jonsu ya no copia los juegos ni los libros: los lee de la cuenta y su
  estado lo manda Bastet o Seshat (si relees un libro, está en curso; su nota se queda y vuelve al terminarlo).
- **«Sin valorar»** en vez de 0 en todo lo terminado que aún no has valorado.
- **La valoración va en el propio juego o libro de la cuenta**: la ven Bastet, Seshat y tus otros Jonsu. Solo se escribe lo que cambias
  aquí: nunca pisa el estado que pone la hermana.
- **La portada de la ficha, pequeña**, junto a la nota o el estado (fuera la fila enorme del principio de la 1.2).
- Lo propio de Jonsu (películas, series, tus categorías, diario, sagas, maquetas…) va a la cuenta elemento a elemento.
- Ajustes → Cuenta enseña las reglas nuevas de Firebase para copiarlas (`nube/reglas.json`).

## 1.2.12

- **Fechas y cifras como en sus hermanas** (`Formato.kt`, con las mismas pruebas en las cuatro): «16 oct 2026» en fichas y textos, «16/10/2026» en las tablas de datos y siempre con punto de millar («4.010»). Los meses van escritos en la app (Android, según la versión, les ponía punto: «oct.»).
- **«Hoy» en la portada**, debajo del menú: lo que llevas hoy entre las tres hermanas («40 min leyendo · 2 h jugando · 500 palabras»), los días seguidos (la regla común) y la semana en barras, como en sus portadas. Sustituye a la línea de lectura, que aún miraba el lector de Atrio.
- **Con Seshat en el móvil, los widgets «Leyendo» y «La cita» de Jonsu desaparecen** (son los mismos libros y subrayados que los de Seshat: así no hay dos iguales en el selector). Si quitas Seshat, vuelven.
- **Ajustes con las mismas pestañas que sus hermanas**: Apariencia · Cuenta · Avanzado, y la cabecera «SECCIÓN / Ajustes».
- Las listas (Videojuegos, Libros…) y Escritos llevan «SECCIÓN · 12 JUEGOS» sobre el título, como en las hermanas.
- Los contadores de las pestañas van en tinta (más oscura la de la pestaña elegida), no en rojo: el rojo queda para lo que se toca.
- **Las portadas de letra, con las telas de Seshat** (la misma paleta en las cuatro y la inicial en la letra de los títulos), en vez del fondo de arena.
- **La cabecera de la portada se pliega al bajar**: arriba solo se quedan el nombre y «Buscar» y Ajustes; el número, la fecha, las barras y el menú se van con la página.
- Debajo de «Jonsu · hecho en casa», en gris, la versión instalada.

## 1.2.11

- **Arreglo del objetivo de libros (y de juegos)**: contaba solo lo que habías valorado en Jonsu. Un libro leído en Seshat que aún no
  tiene nota (lo normal en los de una saga, que se leen seguidos) no contaba. Ahora cuenta todo lo que terminaste este año, con nota o sin
  ella, esté o no en una saga. Las demás cifras de Estadísticas (medias, géneros…) siguen usando solo lo valorado.

## 1.2.10

- **Una sola definición de «días seguidos»**, la misma regla en Bastet, Seshat, Thot y Jonsu (`Racha.kt`, con las mismas pruebas en las cuatro, y en el contrato): cuenta un día en que leíste o jugaste al menos un minuto, o escribiste al menos una palabra (en Seshat, Bastet y Thot; basta con una); si hoy aún no, desde ayer.
- El widget «Tu actividad» cuenta ahora lo que haces en las hermanas (antes contaba los apuntes y lo terminado de Atrio, que ya no se usan, y lo leído desde un segundo). El mapa de días se oscurece con lo leído más lo jugado (y lo escrito, a unas 20 palabras el minuto).
- El Diario dice «N días seguidos leyendo, jugando o escribiendo», con la misma regla: un día con solo notas o valoraciones ya no la alarga.

## 1.2.9

- **Un solo objetivo de lectura**: los libros del año y los minutos al día los fija Seshat. En Ajustes → Objetivos se ven tal cual
  («lo fija Seshat») con un enlace para cambiarlos allí; la portada, el Diario, las estadísticas, el widget y el aviso de lectura usan
  los de Seshat. El objetivo de juegos sigue siendo de Jonsu. Hasta que Seshat suba los suyos, se ven los que tenías aquí.

## 1.2.8

- La fila de orden y filtros (Recientes · Nombre · …) lleva el mismo degradado en los bordes que las pestañas.
- En las casillas estrechas de la ficha, un dato que no cabe en una línea («Terminado · 06/09/2026») pone la etiqueta entera arriba y la
  cifra debajo, en vez de cortar la etiqueta («Termin…»).
- Las pestañas de la biblioteca van en plural, como en Bastet y Seshat (Jugando · Pendientes · Pasados · Abandonados); el estado de cada
  ficha sigue en singular.
- Las pestañas (secciones, Ajustes) se desplazan solas solo cuando la elegida no se ve entera, y entonces la centran: antes se
  pegaba al borde izquierdo y la de al lado salía cortada.
  Y en los bordes, un degradado cuando hay más pestañas por ese lado.

## 1.2.7

- El widget «Leyendo»: un libro de Seshat se abre en Seshat («Leer en Seshat →») y enseña lo que llevas (lo que Seshat sube con su
  biblioteca); antes abría su ficha aquí y no sabía el avance. Sin nada en la mesilla, manda a Seshat (los libros se empiezan allí).
- El aviso del objetivo de lectura dice también por dónde vas («vas por el 43 %»).
- Dos textos que seguían hablando del lector antiguo («minutos con el lector», «subraya en el lector») dicen ahora Seshat.

## 1.2.6

- El aviso del objetivo de lectura y el atajo «Seguir leyendo» del icono proponen el último libro que leíste en Seshat (según lo que
  Seshat sube) y abren Seshat en su ficha. Antes miraban el lector antiguo (que ya no existe): el atajo abría Jonsu sin más y el
  aviso no sabía qué libro proponer.

## 1.2.5

- Textos al día: los avisos del actualizador y el de «se ha actualizado» dicen Jonsu (decían Atrio), y las ayudas que mandaban a
  «Ajustes → Servicios», «Ajustes → Cuenta» o «Ajustes → Tu nube» ahora nombran la pestaña real (General → Cuenta y pareja; Avanzado →
  Servicios / Portadas de juegos / Tu nube).
- El pie de Ajustes, como en sus hermanas: la versión como etiqueta y una línea que dice de quién es hermana.

## 1.2.4

- **Arreglo**: en Android 11 o más, «Jugar en Bastet», «Leer en Seshat» y «Escribir en Thot» no salían nunca, y el widget y el aviso de
  lectura no abrían Seshat (Jonsu no declaraba que puede ver a sus hermanas). Ahora el manifiesto las declara, y un test lo vigila.
- Los widgets se repintan en cuanto llega algo de las hermanas (horas, subrayados, portadas), no solo al cambiar lo de Jonsu.
- Al oír que Seshat sube lo leído de un libro, solo se bajan los nodos ligeros (tiempo y subrayados), no su biblioteca entera.

## 1.2.3

- En Escritos, cada obra lleva también «Escribir en Thot →» (abre Thot en esa obra, si está en el móvil).

## 1.2.2

- La segunda pestaña de Ajustes se llama «Avanzado», como en las hermanas (era «Servicios y copias»).
- En Ajustes, la cuenta y la pareja (usuario, contraseña, entrar, sincronizar, compartir…), los interruptores y «Actualizarse sola» usan
  las casillas y los enlaces de la revista, los mismos que en Bastet, Seshat y Thot, en vez de los cuadros y botones de Material.

## 1.2.0: como sus hermanas

- **La portada en la ficha**: una casilla «Portada» (la misma imagen que en Bastet o en Seshat) en la primera fila de cada ficha, junto al
  estado o a la nota, como en sus hermanas. Entra sola en las maquetas que ya tenías (si no la quieres, se quita en «Editar»).
- **Portadas en las listas**, encendidas de serie (Ajustes → Apariencia las apaga), con el tamaño de las listas de Bastet y Seshat.
- Los estados se llaman como en Bastet y Seshat: **Pendiente · Jugando/Leyendo · Abandonado** (antes «En lista» y «Dropeado»).
- «← Jonsu» en las listas (decía «← Atrio»), y los textos que aún nombraban a Atrio.

## 1.1.5: tus subrayados de Seshat, aquí

- Los subrayados que haces en Seshat (su nodo ligero `subrayados`) salen en Jonsu: en «La cita» de la portada (y su widget), en el Diario
  (el día que los subrayaste), en la casilla «Subrayados y notas» de la ficha del libro y en el buscador. Tocar una cita abre ese libro en
  Seshat. Los del lector viejo de Atrio se conservan.

## 1.1.4

- Al oír el sello de una hermana solo se baja lo de esa (antes, lo de las tres, con los capítulos de Thot enteros, cada vez). Y si dos
  hermanas sellan a la vez, no se pierde ninguna.

## 1.1.3

- «Leer en Seshat» (en la portada y en cada obra de Escritos) abre Seshat **leyendo esa obra** (Seshat 0.12.3 o más nueva).

## 1.1.2: «Jugar» y «Leer» van a la ficha exacta

- «Jugar» en una ficha que vino de Bastet abre Bastet **en ese juego** (y, si es del PC, directo a jugar); «Leer» en un libro de Seshat abre
  Seshat **en ese libro** (Seshat 0.12.2 o más nueva, por la clave del libro). Antes solo abrían la app.

## 1.1.1

- Jonsu escucha también `marfil/lstamps` (el sello de lo leído de cada libro): los minutos de hoy salen en el Diario en cuanto cierras el libro
  en Seshat, sin esperar a que Seshat suba su biblioteca.

## 1.1.0: las mismas portadas que en Bastet y Seshat, y los Escritos en Seshat

- **Portadas**: un juego o un libro enseña aquí la misma portada que tiene en Bastet o en Seshat. Las de internet viajan con la ficha (`pu`;
  Bastet manda también la caja automática de libretro, `pa`), y las que pusiste de tu galería llegan por kDrive (hace falta el token en
  Ajustes). Bastet 0.3.0 y Seshat 0.12.0 o más nuevas.
- **Escritos**: con Seshat en el móvil, «Leer en Seshat →» (en la portada y en Escritos) abre Seshat en sus Escritos, donde cada obra de
  Thot se lee con su lector. Sin Seshat, se siguen leyendo aquí.

## 1.0.0: la primera

- **Lo que se queda de Atrio**: la portada de revista (cabecera, número y fecha, las barras de lectura, «En portada», tu pareja), las
  categorías y sus páginas, la valoración (nota, criterios, reseña), las casillas de cada página y cómo se colocan («Editar»), el Diario, las
  Cifras, las sagas, la pareja (recomendaciones, reacciones, su diario), los widgets, la apariencia (temas, fondos, letras, iconos), la cuenta,
  kDrive, las copias y actualizarse sola desde tu kDrive (`Jonsu-<versión>.apk`).
- **Lo que se va** (se juega, se lee y se escribe en sus apps): el lector, todo lo del PC (streaming, encender, capturas, mando), Escribir,
  Descubrir y «Siguiente», los duelos de libros, las guías, el progreso por capítulos y partes, los apuntes rápidos, la casilla de capítulos,
  importar de Goodreads y Bookmory, y añadir juegos o libros a mano.
- **Lo que llega de las hermanas** (`docs/CONTRATO-DATOS.md`): cada juego de Bastet y cada libro de Seshat es una ficha aquí (en curso o
  valorada según su estado allí), con su nombre, plataforma o autor, horas, páginas, duraciones, fechas y portada; lo que valoras es tuyo y no se
  toca. Una ficha que llega ya pasada o leída está «sin valorar» hasta que le pongas nota. En el Diario y el calendario salen lo jugado (Bastet),
  lo leído (Seshat) y las palabras escritas (Thot) de cada día. Y **Escritos**, en el menú: los capítulos que diste por terminados en Thot,
  para leerlos aquí.
- En las páginas, «Jugar» abre Bastet y «Leer» abre Seshat (si están en el móvil), en la ficha de ese juego o de ese libro.

## Capturas

Generadas en el ordenador con fichas de ejemplo (`Capturas6Test`): [portada](docs/capturas/r1_portada.png) · [portada abajo](docs/capturas/r2_portada_abajo.png) ·
[biblioteca](docs/capturas/r3_biblioteca.png) · [ficha valorada](docs/capturas/r4_ficha_valorado.png) · [ficha en curso](docs/capturas/r5_ficha_jugando.png) ·
[libro](docs/capturas/s1_libro.png) · [Escritos](docs/capturas/p01_escritos.png) · [Diario](docs/capturas/diario_1_dias.png) · [calendario](docs/capturas/diario_2_calendario.png) ·
[Cifras](docs/capturas/p05_estadisticas.png) · [ajustes](docs/capturas/p06_perfil.png) · [avanzado](docs/capturas/p07_ajustes.png) · [apariencia](docs/capturas/p11_apariencia.png) ·
[de noche](docs/capturas/r7_noche.png) · [editando una página](docs/capturas/r8_diseno.png).

## Cómo se trabaja

- Pruebas: `gradle :app:testDebugUnitTest` (0 fallos). `HermanasTest` cubre lo que se lee de las hermanas y cómo se refleja;
  `ContratoHermanasTest` lee `herramientas/casos/hermanas/*.json`, que son lo que Bastet, Seshat y Thot suben **de verdad** (los genera la
  prueba `ContratoTest` de cada una en su `app/build/outputs/contrato/`; al cambiar lo que sube una hermana, se copian aquí y esta prueba dice
  si Jonsu lo sigue entendiendo). Y al revés, `ContratoTest` deja en `app/build/outputs/contrato/azabache.json` lo que sube Jonsu, que
  Bastet y Seshat leen en su `NotaAzabacheContratoTest` («En Jonsu»). Con
  `-Proborazzi.test.record=true` se graban las capturas (`app/build/outputs/roborazzi/`).
- Versión nueva: subir `versionCode`/`versionName` (`app/build.gradle.kts`), `gradle :app:assembleRelease` y
  `python3 herramientas/subir-version.py Jonsu-<versión>.apk` la deja en tu kDrive para que los móviles se actualicen solos. El
  `versionCode` debe seguir por encima del último de Atrio (131).
- Nace de `Atrio-Suite-2.0/movil` (commit `e2c20b7`): lo quitado sigue en el código pero sin ningún camino que lleve a ello (se irá
  borrando con calma). Atrio del PC sigue siendo Atrio y lee el mismo nodo de la cuenta.
