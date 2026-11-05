# Azabache

La revista de la familia: lo que juegas, lees, ves y escribes, valorado y contado. Es **Atrio del móvil con casa propia y menos cosas**:
mismo paquete (`com.gonzalo.gamerate`) y misma firma, así que **se instala encima de Atrio** y se queda con todo (tus notas, tu cuenta, tu
pareja, tus maquetas). Las otras apps la siguen encontrando donde estaba (los colores de Atrio, «Abrir en Azabache»).

Hermana de [Hiedra](https://github.com/Gonvegato/hiedra) (juegos), [Marfil](https://github.com/Gonvegato/lectura-app) (libros) y
[Cobalto](https://github.com/Gonvegato/cobalto) (escribir). Azabache **no añade juegos ni libros**: se añaden, se juegan y se leen en sus apps,
y aquí aparecen solos, con su estado, sus horas y sus portadas, para valorarlos. Las películas, las series y tus categorías propias siguen
aquí tal cual (con su «Añadir»).

## 1.5.2

- **La portada de la ficha ocupa su casilla** (como mucho 200 dp de ancho, en 2:3). Antes era una miniatura de 96 dp y la fila se medía
  como si la portada llenara la casilla: en la tablet quedaba una portada pequeña con un hueco enorme debajo.

- **Una pantalla que no dejaba de redibujarse**: los títulos que se encogen para caber (FitTitle) apuntaban su ancho desde dentro de la
  medida; en una fila de dos casillas Compose mide dos veces (para saber el alto y para pintar) y, si los dos anchos diferían en un píxel
  por redondeo, se pisaban en cada fotograma: la pantalla se recomponía sin parar (CPU y batería) mientras estaba abierta. Pasaba en la
  tablet en la ficha de un juego «Jugando» de Azabache; la misma pieza está en las cuatro apps y en las de escritorio, y se arregla en todas.

## 1.5.1

- Los capítulos de Cobalto llegan con su hora de edición (`ue`, ver `nube/ESQUEMA.md`).

## 1.5.0 — todo de la cuenta

- **Lo tuyo de Azabache también es de la cuenta, sin copia**: tus categorías y sus fichas (películas, series, las tuyas), el diario,
  las sagas, las maquetas, la lista negra, lo de tu nube y lo leído se leen y se escriben directamente en la cuenta, elemento a
  elemento (como ya se subían). Ya no hay una copia en el móvil que se mezcle con la de la cuenta. En el móvil se queda solo lo que
  es de este móvil (la apariencia, los avisos…).

## 1.4.0 — sin copias

- **Tus juegos y tus libros son los de la cuenta, sin copia.** Azabache ya no guarda los suyos aparte ni los «refleja» de Hiedra y
  Marfil: los lee de la cuenta, cada vez, como ellas. No hay nada que se quede atrás: lo que terminas en Marfil está terminado aquí
  en cuanto llega a la cuenta, y lo que valoras aquí está en el libro de la cuenta. Sin conexión funciona igual.
- **Solo se escribe lo que cambias tú.** Si tienes una lista en pantalla de hace un rato y Marfil cambia un libro mientras, lo que
  hagas aquí no deshace lo de Marfil.
- **Lo que solo estaba en Azabache** (lo valorado en Atrio que nunca pasó por Hiedra ni Marfil) pasa a la cuenta común, con su nota,
  su fecha y su número de siempre (tus sagas, tu diario y tus maquetas lo siguen encontrando): Marfil lo enseña en Leídos y Hiedra
  como una ficha sin lanzador.
- **Lo de tu pareja**: sus juegos y libros salen de su cuenta (con sus notas), también los de Hiedra y Marfil.
- Pasar un libro a leídos (o al revés) cambia su estado en Marfil; quitar una ficha la quita de la cuenta.

## 1.3.1

- **Azabache se queda al día con Hiedra y Marfil aunque hayas entrado en otra app.** Al pasar de la 1.2, Azabache necesita la
  cuenta de la familia; si entrabas después en Marfil o Hiedra, Azabache no se enteraba hasta que Android la cerraba, y mientras
  tanto enseñaba lo de antes (libros terminados aún «leyendo», los que empezaste escondidos en su saga de pendientes, sin las
  portadas nuevas de Hiedra). Ahora entra sola al volver a ella, adopta la sesión al momento y trae lo de la cuenta.
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
- **Entrar una vez.** Entras en Azabache (o en cualquiera de la familia) y las demás entran solas en ese móvil.
- **Las claves, una vez.** El token de kDrive y las de los servicios se ponen una vez y las tienen todas las apps (si ya las tenías
  en este móvil, se suben solas a la cuenta). Se gestionan en Azabache (Ajustes → Avanzado); un token mal pegado en
  cualquier app nunca pisa el bueno de la cuenta.
- **Actualizaciones sin preguntar** también en Android 17 (la app apunta a Android 15 y declara el permiso de actualizarse sola).
- **Una sola firma para las cuatro.** Esta versión se instala encima de la anterior, sin desinstalar, pero **a mano esta vez**: el
  actualizador de antes no acepta la firma nueva. A partir de aquí, se actualiza sola.
- **La primera vez** (en cada móvil), lo de antes pasa a la cuenta nueva con la hora real de cada cambio. Lo de antes no se borra.
- **Arreglado: libros «leyendo» que salían leídos con un 0.** Azabache ya no copia los juegos ni los libros: los lee de la cuenta y su
  estado lo manda Hiedra o Marfil (si relees un libro, está en curso; su nota se queda y vuelve al terminarlo).
- **«Sin valorar»** en vez de 0 en todo lo terminado que aún no has valorado.
- **La valoración va en el propio juego o libro de la cuenta**: la ven Hiedra, Marfil y tus otros Azabache. Solo se escribe lo que cambias
  aquí: nunca pisa el estado que pone la hermana.
- **La portada de la ficha, pequeña**, junto a la nota o el estado (fuera la fila enorme del principio de la 1.2).
- Lo propio de Azabache (películas, series, tus categorías, diario, sagas, maquetas…) va a la cuenta elemento a elemento.
- Ajustes → Cuenta enseña las reglas nuevas de Firebase para copiarlas (`nube/reglas.json`).

## 1.2.12

- **Fechas y cifras como en sus hermanas** (`Formato.kt`, con las mismas pruebas en las cuatro): «16 oct 2026» en fichas y textos, «16/10/2026» en las tablas de datos y siempre con punto de millar («4.010»). Los meses van escritos en la app (Android, según la versión, les ponía punto: «oct.»).
- **«Hoy» en la portada**, debajo del menú: lo que llevas hoy entre las tres hermanas («40 min leyendo · 2 h jugando · 500 palabras»), los días seguidos (la regla común) y la semana en barras, como en sus portadas. Sustituye a la línea de lectura, que aún miraba el lector de Atrio.
- **Con Marfil en el móvil, los widgets «Leyendo» y «La cita» de Azabache desaparecen** (son los mismos libros y subrayados que los de Marfil: así no hay dos iguales en el selector). Si quitas Marfil, vuelven.
- **Ajustes con las mismas pestañas que sus hermanas**: Apariencia · Cuenta · Avanzado, y la cabecera «SECCIÓN / Ajustes».
- Las listas (Videojuegos, Libros…) y Escritos llevan «SECCIÓN · 12 JUEGOS» sobre el título, como en las hermanas.
- Los contadores de las pestañas van en tinta (más oscura la de la pestaña elegida), no en rojo: el rojo queda para lo que se toca.
- **Las portadas de letra, con las telas de Marfil** (la misma paleta en las cuatro y la inicial en la letra de los títulos), en vez del fondo de arena.
- **La cabecera de la portada se pliega al bajar**: arriba solo se quedan el nombre y «Buscar» y Ajustes; el número, la fecha, las barras y el menú se van con la página.
- Debajo de «Azabache · hecho en casa», en gris, la versión instalada.

## 1.2.11

- **Arreglo del objetivo de libros (y de juegos)**: contaba solo lo que habías valorado en Azabache. Un libro leído en Marfil que aún no
  tiene nota (lo normal en los de una saga, que se leen seguidos) no contaba. Ahora cuenta todo lo que terminaste este año, con nota o sin
  ella, esté o no en una saga. Las demás cifras de Estadísticas (medias, géneros…) siguen usando solo lo valorado.

## 1.2.10

- **Una sola definición de «días seguidos»**, la misma regla en Hiedra, Marfil, Cobalto y Azabache (`Racha.kt`, con las mismas pruebas en las cuatro, y en el contrato): cuenta un día en que leíste o jugaste al menos un minuto, o escribiste al menos una palabra (en Marfil, Hiedra y Cobalto; basta con una); si hoy aún no, desde ayer.
- El widget «Tu actividad» cuenta ahora lo que haces en las hermanas (antes contaba los apuntes y lo terminado de Atrio, que ya no se usan, y lo leído desde un segundo). El mapa de días se oscurece con lo leído más lo jugado (y lo escrito, a unas 20 palabras el minuto).
- El Diario dice «N días seguidos leyendo, jugando o escribiendo», con la misma regla: un día con solo notas o valoraciones ya no la alarga.

## 1.2.9

- **Un solo objetivo de lectura**: los libros del año y los minutos al día los fija Marfil. En Ajustes → Objetivos se ven tal cual
  («lo fija Marfil») con un enlace para cambiarlos allí; la portada, el Diario, las estadísticas, el widget y el aviso de lectura usan
  los de Marfil. El objetivo de juegos sigue siendo de Azabache. Hasta que Marfil suba los suyos, se ven los que tenías aquí.

## 1.2.8

- La fila de orden y filtros (Recientes · Nombre · …) lleva el mismo degradado en los bordes que las pestañas.
- En las casillas estrechas de la ficha, un dato que no cabe en una línea («Terminado · 06/09/2026») pone la etiqueta entera arriba y la
  cifra debajo, en vez de cortar la etiqueta («Termin…»).
- Las pestañas de la biblioteca van en plural, como en Hiedra y Marfil (Jugando · Pendientes · Pasados · Abandonados); el estado de cada
  ficha sigue en singular.
- Las pestañas (secciones, Ajustes) se desplazan solas solo cuando la elegida no se ve entera, y entonces la centran: antes se
  pegaba al borde izquierdo y la de al lado salía cortada.
  Y en los bordes, un degradado cuando hay más pestañas por ese lado.

## 1.2.7

- El widget «Leyendo»: un libro de Marfil se abre en Marfil («Leer en Marfil →») y enseña lo que llevas (lo que Marfil sube con su
  biblioteca); antes abría su ficha aquí y no sabía el avance. Sin nada en la mesilla, manda a Marfil (los libros se empiezan allí).
- El aviso del objetivo de lectura dice también por dónde vas («vas por el 43 %»).
- Dos textos que seguían hablando del lector antiguo («minutos con el lector», «subraya en el lector») dicen ahora Marfil.

## 1.2.6

- El aviso del objetivo de lectura y el atajo «Seguir leyendo» del icono proponen el último libro que leíste en Marfil (según lo que
  Marfil sube) y abren Marfil en su ficha. Antes miraban el lector antiguo (que ya no existe): el atajo abría Azabache sin más y el
  aviso no sabía qué libro proponer.

## 1.2.5

- Textos al día: los avisos del actualizador y el de «se ha actualizado» dicen Azabache (decían Atrio), y las ayudas que mandaban a
  «Ajustes → Servicios», «Ajustes → Cuenta» o «Ajustes → Tu nube» ahora nombran la pestaña real (General → Cuenta y pareja; Avanzado →
  Servicios / Portadas de juegos / Tu nube).
- El pie de Ajustes, como en sus hermanas: la versión como etiqueta y una línea que dice de quién es hermana.

## 1.2.4

- **Arreglo**: en Android 11 o más, «Jugar en Hiedra», «Leer en Marfil» y «Escribir en Cobalto» no salían nunca, y el widget y el aviso de
  lectura no abrían Marfil (Azabache no declaraba que puede ver a sus hermanas). Ahora el manifiesto las declara, y un test lo vigila.
- Los widgets se repintan en cuanto llega algo de las hermanas (horas, subrayados, portadas), no solo al cambiar lo de Azabache.
- Al oír que Marfil sube lo leído de un libro, solo se bajan los nodos ligeros (tiempo y subrayados), no su biblioteca entera.

## 1.2.3

- En Escritos, cada obra lleva también «Escribir en Cobalto →» (abre Cobalto en esa obra, si está en el móvil).

## 1.2.2

- La segunda pestaña de Ajustes se llama «Avanzado», como en las hermanas (era «Servicios y copias»).
- En Ajustes, la cuenta y la pareja (usuario, contraseña, entrar, sincronizar, compartir…), los interruptores y «Actualizarse sola» usan
  las casillas y los enlaces de la revista, los mismos que en Hiedra, Marfil y Cobalto, en vez de los cuadros y botones de Material.

## 1.2.0: como sus hermanas

- **La portada en la ficha**: una casilla «Portada» (la misma imagen que en Hiedra o en Marfil) en la primera fila de cada ficha, junto al
  estado o a la nota, como en sus hermanas. Entra sola en las maquetas que ya tenías (si no la quieres, se quita en «Editar»).
- **Portadas en las listas**, encendidas de serie (Ajustes → Apariencia las apaga), con el tamaño de las listas de Hiedra y Marfil.
- Los estados se llaman como en Hiedra y Marfil: **Pendiente · Jugando/Leyendo · Abandonado** (antes «En lista» y «Dropeado»).
- «← Azabache» en las listas (decía «← Atrio»), y los textos que aún nombraban a Atrio.

## 1.1.5: tus subrayados de Marfil, aquí

- Los subrayados que haces en Marfil (su nodo ligero `subrayados`) salen en Azabache: en «La cita» de la portada (y su widget), en el Diario
  (el día que los subrayaste), en la casilla «Subrayados y notas» de la ficha del libro y en el buscador. Tocar una cita abre ese libro en
  Marfil. Los del lector viejo de Atrio se conservan.

## 1.1.4

- Al oír el sello de una hermana solo se baja lo de esa (antes, lo de las tres, con los capítulos de Cobalto enteros, cada vez). Y si dos
  hermanas sellan a la vez, no se pierde ninguna.

## 1.1.3

- «Leer en Marfil» (en la portada y en cada obra de Escritos) abre Marfil **leyendo esa obra** (Marfil 0.12.3 o más nueva).

## 1.1.2: «Jugar» y «Leer» van a la ficha exacta

- «Jugar» en una ficha que vino de Hiedra abre Hiedra **en ese juego** (y, si es del PC, directo a jugar); «Leer» en un libro de Marfil abre
  Marfil **en ese libro** (Marfil 0.12.2 o más nueva, por la clave del libro). Antes solo abrían la app.

## 1.1.1

- Azabache escucha también `marfil/lstamps` (el sello de lo leído de cada libro): los minutos de hoy salen en el Diario en cuanto cierras el libro
  en Marfil, sin esperar a que Marfil suba su biblioteca.

## 1.1.0: las mismas portadas que en Hiedra y Marfil, y los Escritos en Marfil

- **Portadas**: un juego o un libro enseña aquí la misma portada que tiene en Hiedra o en Marfil. Las de internet viajan con la ficha (`pu`;
  Hiedra manda también la caja automática de libretro, `pa`), y las que pusiste de tu galería llegan por kDrive (hace falta el token en
  Ajustes). Hiedra 0.3.0 y Marfil 0.12.0 o más nuevas.
- **Escritos**: con Marfil en el móvil, «Leer en Marfil →» (en la portada y en Escritos) abre Marfil en sus Escritos, donde cada obra de
  Cobalto se lee con su lector. Sin Marfil, se siguen leyendo aquí.

## 1.0.0: la primera

- **Lo que se queda de Atrio**: la portada de revista (cabecera, número y fecha, las barras de lectura, «En portada», tu pareja), las
  categorías y sus páginas, la valoración (nota, criterios, reseña), las casillas de cada página y cómo se colocan («Editar»), el Diario, las
  Cifras, las sagas, la pareja (recomendaciones, reacciones, su diario), los widgets, la apariencia (temas, fondos, letras, iconos), la cuenta,
  kDrive, las copias y actualizarse sola desde tu kDrive (`Azabache-<versión>.apk`).
- **Lo que se va** (se juega, se lee y se escribe en sus apps): el lector, todo lo del PC (streaming, encender, capturas, mando), Escribir,
  Descubrir y «Siguiente», los duelos de libros, las guías, el progreso por capítulos y partes, los apuntes rápidos, la casilla de capítulos,
  importar de Goodreads y Bookmory, y añadir juegos o libros a mano.
- **Lo que llega de las hermanas** (`docs/CONTRATO-DATOS.md`): cada juego de Hiedra y cada libro de Marfil es una ficha aquí (en curso o
  valorada según su estado allí), con su nombre, plataforma o autor, horas, páginas, duraciones, fechas y portada; lo que valoras es tuyo y no se
  toca. Una ficha que llega ya pasada o leída está «sin valorar» hasta que le pongas nota. En el Diario y el calendario salen lo jugado (Hiedra),
  lo leído (Marfil) y las palabras escritas (Cobalto) de cada día. Y **Escritos**, en el menú: los capítulos que diste por terminados en Cobalto,
  para leerlos aquí.
- En las páginas, «Jugar» abre Hiedra y «Leer» abre Marfil (si están en el móvil), en la ficha de ese juego o de ese libro.

## Capturas

Generadas en el ordenador con fichas de ejemplo (`Capturas6Test`): [portada](docs/capturas/r1_portada.png) · [portada abajo](docs/capturas/r2_portada_abajo.png) ·
[biblioteca](docs/capturas/r3_biblioteca.png) · [ficha valorada](docs/capturas/r4_ficha_valorado.png) · [ficha en curso](docs/capturas/r5_ficha_jugando.png) ·
[libro](docs/capturas/s1_libro.png) · [Escritos](docs/capturas/p01_escritos.png) · [Diario](docs/capturas/diario_1_dias.png) · [calendario](docs/capturas/diario_2_calendario.png) ·
[Cifras](docs/capturas/p05_estadisticas.png) · [ajustes](docs/capturas/p06_perfil.png) · [avanzado](docs/capturas/p07_ajustes.png) · [apariencia](docs/capturas/p11_apariencia.png) ·
[de noche](docs/capturas/r7_noche.png) · [editando una página](docs/capturas/r8_diseno.png).

## Cómo se trabaja

- Pruebas: `gradle :app:testDebugUnitTest` (0 fallos). `HermanasTest` cubre lo que se lee de las hermanas y cómo se refleja;
  `ContratoHermanasTest` lee `herramientas/casos/hermanas/*.json`, que son lo que Hiedra, Marfil y Cobalto suben **de verdad** (los genera la
  prueba `ContratoTest` de cada una en su `app/build/outputs/contrato/`; al cambiar lo que sube una hermana, se copian aquí y esta prueba dice
  si Azabache lo sigue entendiendo). Y al revés, `ContratoTest` deja en `app/build/outputs/contrato/azabache.json` lo que sube Azabache, que
  Hiedra y Marfil leen en su `NotaAzabacheContratoTest` («En Azabache»). Con
  `-Proborazzi.test.record=true` se graban las capturas (`app/build/outputs/roborazzi/`).
- Versión nueva: subir `versionCode`/`versionName` (`app/build.gradle.kts`), `gradle :app:assembleRelease` y
  `python3 herramientas/subir-version.py Azabache-<versión>.apk` la deja en tu kDrive para que los móviles se actualicen solos. El
  `versionCode` debe seguir por encima del último de Atrio (131).
- Nace de `Atrio-Suite-2.0/movil` (commit `e2c20b7`): lo quitado sigue en el código pero sin ningún camino que lleve a ello (se irá
  borrando con calma). Atrio del PC sigue siendo Atrio y lee el mismo nodo de la cuenta.
