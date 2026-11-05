# Azabache

La revista de la familia: lo que juegas, lees, ves y escribes, valorado y contado. Es **Atrio del móvil con casa propia y menos cosas**:
mismo paquete (`com.gonzalo.gamerate`) y misma firma, así que **se instala encima de Atrio** y se queda con todo (tus notas, tu cuenta, tu
pareja, tus maquetas). Las otras apps la siguen encontrando donde estaba (los colores de Atrio, «Abrir en Azabache»).

Hermana de [Hiedra](https://github.com/Gonvegato/hiedra) (juegos), [Marfil](https://github.com/Gonvegato/lectura-app) (libros) y
[Cobalto](https://github.com/Gonvegato/cobalto) (escribir). Azabache **no añade juegos ni libros**: se añaden, se juegan y se leen en sus apps,
y aquí aparecen solos, con su estado, sus horas y sus portadas, para valorarlos. Las películas, las series y tus categorías propias siguen
aquí tal cual (con su «Añadir»).

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
