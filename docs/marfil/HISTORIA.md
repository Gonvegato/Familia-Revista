# Seshat

La app de leer del móvil. Hermana de [Jonsu](https://github.com/Gonvegato/azabache) (la revista: valorar), [Bastet](https://github.com/Gonvegato/hiedra)
(juegos) y [Thot](https://github.com/Gonvegato/cobalto) (escribir): misma interfaz «Revista» de Atrio, mismos temas, otro paquete
(`com.gonzalo.marfil`), así que conviven en el mismo móvil. Aquí se **añaden y se leen** los libros (EPUB, PDF y cómics); Jonsu los
refleja para valorarlos, y los escritos de Thot se leen aquí.

## 0.21.2 — «Colores de Seshat», de un toque

- **Ajustes → Apariencia → «Poner los colores de Seshat»**, arriba del todo: pone los últimos colores diseñados para Seshat (papel amarillo, tinta casi negra y mostaza) y
  quita lo elegido a mano (fondo de color, acento, «A tu manera», seguir a Jonsu). El tema y el tamaño del texto se quedan. Llega a tus
  otros dispositivos.
- **El fondo que pones aquí sale en Jonsu.** Jonsu enseñaba el fondo que guardaba para sí, no el tuyo: ahora usa el de la cuenta (y si lo cambias en Jonsu, cambia aquí).

## Capturas

Generadas en el ordenador con libros de ejemplo (`CapturasTest`, solo con `-Proborazzi.test.record=true`): [portada](docs/capturas/1_portada.png) ·
[en oscuro](docs/capturas/2_portada_oscuro.png) · [Leyendo](docs/capturas/3_lista.png) · [Pendientes](docs/capturas/3b_pendientes.png) ·
[saga abierta](docs/capturas/3c_saga_abierta.png) · [página de una saga](docs/capturas/3d_pagina_saga.png) · [Sagas](docs/capturas/3h_sagas.png) ·
[ficha](docs/capturas/4_ficha.png) · [ficha sin archivo](docs/capturas/4c_ficha_sin_archivo.png) · [ficha de un leído](docs/capturas/4d_ficha_leido.png) ·
[tapas](docs/capturas/6_tapas.png) · [elegir portada](docs/capturas/7_selector_portada.png) · [Escritos](docs/capturas/9_escritos.png) ·
[Ajustes](docs/capturas/8_ajustes.png) · [Lectura](docs/capturas/8b_ajustes_lectura.png) · [Cuenta](docs/capturas/8c_ajustes_cuenta.png) ·
[Avanzado](docs/capturas/8d_ajustes_avanzado.png). Los widgets, en [`docs/widgets/hoja.png`](docs/widgets/hoja.png).

## Qué hace

- **Portada** de revista: cabecera, número y fecha, las barras de los días que cumples tu objetivo, «Ahora» (lo último que abriste),
  «En la mesilla» (lo que lees a medias) y «En la estantería» (lo pendiente), el reto del año, y el menú de secciones.
- **Secciones** como páginas que se recorren con el dedo: Leyendo · Pendientes · Leídos · Abandonados · Autores · Sagas · Escritos. Cada
  estante se ordena (Recientes, Añadidos, Título, Autor, Páginas, Terminado) y se recuerda; «Buscar» mira en todos.
- **Ficha** con la estética de Atrio: estado arriba (Pendiente · Leyendo · Leído · Abandonado), saga y favorito, la casilla de lectura
  («Seguir leyendo», «Bajar de tu kDrive», «Elegir archivo»), portada, tiempo y páginas, subrayados y notas, fecha de terminado (y «2.ª vez»),
  y «En Jonsu» (la valoración, solo se ve, y «Abrir en Jonsu →»).
- **Lector** (foliate-js para EPUB y cómics, pdf.js para PDF): páginas o desplazamiento continuo, letra, tamaño, tema y márgenes (iguales en
  todos tus dispositivos), subrayados, notas y marcadores, diccionario (Wikcionario), botones de capítulo siguiente y anterior al deslizar.
- **Portadas**: de la galería, de internet (iTunes Libros, Open Library y el buscador de imágenes de Atrio) o ninguna (una tapa de tela con el
  título). Seshat no saca portadas de los archivos. Las que eliges viajan: las de internet por su dirección, las tuyas por kDrive.
- **Sagas**: en las listas los libros de una saga salen juntos; su página dice qué toca leer ahora, y se ordenan, añaden y quitan libros.
- **Escritos**: las obras de Thot con capítulos terminados, convertidas en EPUB, para leerlas con el lector (no salen en los estantes ni viajan).
- **Páginas** de cada libro, de la fuente más fiable: lo que escribes tú → Atrio / el PDF → Open Library → calculadas por el texto del EPUB.
- **Widgets** (los de Atrio, con libros): Sigue leyendo, En la mesilla, En portada, ¿Y ahora qué?, Lectura de la semana, Una cita y la cabecera.
  Eligen libro al ponerlos («el que toque» o uno fijo) y se repintan con el tema.
- Objetivo diario de minutos y reto anual de libros; atajo «Seguir leyendo» al mantener pulsado el icono; copia de la biblioteca en un archivo.

## Ajustes

En pestañas, como Atrio: **Apariencia** (tema claro/oscuro/como el móvil, fondo de color, tamaño del texto, acento, «A tu manera» con los colores y
las letras a mano, cada color con «Copiar de Atrio», y los colores de Atrio en directo, invertidos o no) · **Lectura** (objetivo diario y libros al
año; el lector se ajusta desde dentro, con «Letra») · **Cuenta** (la cuenta de Atrio y los libros en la nube: tener todos aquí, traer los de Atrio,
subir a kDrive ahora) · **Avanzado** (actualizarse sola, el token y el número de kDrive, y las copias).

## Cuenta de Atrio y sincronización entre tus Marfiles

Entras con el mismo usuario y contraseña que en Atrio (Firebase Authentication por REST; se guarda el pase de acceso, no la contraseña). Seshat no
crea cuentas. Cada dispositivo escribe en su hueco bajo `/users/<uid>/marfil/` con sellos de hora y escucha en directo: libros (por clave:
título + autor), estado, favorito, saga, portada, páginas, fechas de terminado y los ajustes del lector. Gana el cambio más reciente; lo que
quitas viaja como «borrado»; el **tiempo leído se suma** por dispositivo. Un libro que viene de otro móvil aparece «sin archivo» hasta que se baja
de kDrive o eliges su archivo. Tu pareja de Atrio puede leer esa parte de la cuenta (las reglas de Atrio dejan leer a la pareja todo `/users/<uid>`).
Lo que lee Jonsu está en `docs/CONTRATO-DATOS.md`. Probado en el ordenador contra el Firebase de mentira (`MezclaTest`, `MezclaLecturaTest`,
`ImportarAtrioTest`); no con tu cuenta real.

## Actualizaciones automáticas (kDrive, como Atrio)

Cada versión se sube a **«Mi carpeta/versiones»** de tu kDrive como `Seshat-<versión>.apk` (`python3 herramientas/subir-version.py`). Seshat mira
al abrir, cada minuto mientras se ve y, cerrada, cada 4 horas (WorkManager); si hay una más nueva se baja, se comprueba que es Seshat con la misma
firma y se instala sin preguntar cuando no la usas (Android 12+, con el permiso de instalar apps); si Android no lo deja, te pregunta. El token
de kDrive es de tu cuenta (se pone una vez en Jonsu → Ajustes → Avanzado → Tu nube) y lo usan las cuatro apps; tu pareja no lo ve.

## Compilar

```
gradle :app:assembleDebug
gradle :app:testDebugUnitTest                                                   # 0 fallos; ContratoTest deja en app/build/outputs/contrato/marfil.json lo que sube Seshat, para la prueba ContratoHermanasTest de Jonsu; NotaAzabacheContratoTest lee herramientas/casos/azabache.json (lo que sube Jonsu)
gradle :app:testDebugUnitTest --tests '*CapturasTest*' -Proborazzi.test.record=true   # capturas en app/build/outputs/roborazzi
gradle :app:assembleRelease                                                     # firmado con app/marfil.keystore
```

Necesita JDK 17+ y el SDK de Android (`local.properties` con `sdk.dir`). Al sacar versión: subir `versionCode`/`versionName`
(`app/build.gradle.kts`) y `VERSION` (`PantEstilo.kt`), apuntar los cambios aquí y subir el APK a kDrive.

## Privacidad

- Las portadas de internet se buscan por título y autor en iTunes Libros y Open Library; el diccionario pregunta a Wikcionario.
- Con la cuenta vinculada, la biblioteca (títulos, estados, tiempo, subrayados) se sube a tu Firebase, legible por ti y por tu pareja de Atrio.
  Los archivos de los libros y las portadas tuyas van a tu kDrive. Sin vincular, nada sale del móvil.

## Versiones

## 0.21.1

Al cambiar una imagen (portada o fondo), la lista de libros (y la de sagas) salía duplicada: cada libro aparecía dos veces (en Bastet se veía en «También juegas»). La causa:
la lista se rehacía a la vez desde dos sitios (el cambio de imagen, en segundo plano, y el aviso de la cuenta o la bajada del archivo) y los dos
«vaciar + llenar» se cruzaban: vacía uno, vacía el otro, llena uno, llena el otro. Ahora el cambio de lista va con candado y sin repetidos.

## 0.21.0

- **El lector, en la dirección C («bloques de tinta»)**, como la app (solo la interfaz: la paginación, el progreso, las notas, la voz y el
  diccionario no cambian):
  - Arriba, con el menú abierto, un bloque de tinta con la fila pequeña «← SESHAT · BUSCAR · MARCAR» (con ★ si la página está marcada),
    el título del libro en la grotesca grande con su punto de acento y el capítulo en versalitas.
  - Abajo, el % en grande en el acento, la barra gruesa con lo leído en el acento y una marca por capítulo, «página · quedan X» del
    libro, y cuatro bloques ÍNDICE · NOTAS · LETRA · VOZ separados por filetes de 2 px.
  - Paneles con su título en la grotesca y su punto («LETRA Y PÁGINA.», «ÍNDICE.», «TUS NOTAS.»…), opciones en bloques con filete (la
    elegida, rellena de tinta), temas en cuadros, pasos − / + en bloques, la rejilla de lo leído con filetes, el índice numerado 01, 02…
    con el capítulo actual resaltado, y las acciones de un subrayado o una palabra en bloques.
  - La tinta es la letra del tema del lector y el papel su fondo: en Gris, Noche y Negro los bloques se invierten solos.
  - En reposo, la línea de abajo en una sola línea (lo de la izquierda, con «…» si no cabe).
  - Letras: la grotesca de la app (Bricolage, `opsz` 48 y ancho 100) y la de cuerpo (Inter) van ahora en el lector; la serif sigue para
    el texto y las citas.
- Capturas del lector (papel y noche: en reposo, menú, Letra, Índice, Notas, subrayado) en `docs/marfil/capturas/lector/`, hechas con
  `herramientas/marfil/capturas-lector.js` (Chromium, un `window.Atrio` de mentira y un EPUB de prueba).

## 0.20.3

- **El paspartú, siempre crema.** El marco de la portada en el cartel y en la ficha deja de ir del color de la página: es un crema fijo
  (#F4EBD6), igual de día y de noche y con cualquier «A tu manera», en todas las apps de la familia. Solo si la portada es muy clara
  (luminancia media ≥ 0,78: casi blanca o crema) pasa a un oscuro fijo (#141210). En Bastet, con la página verde oscura, el marco antes casi
  no se veía sobre fondos oscuros.

## 0.20.2 — la portada entera en el cartel y la cabecera sin hueco

- **Cartel:** la portada (con su paspartú) va entera en el hueco de arriba y acaba antes del texto; el oscurecido de abajo queda detrás
  del texto y nunca encima de la portada (antes se comía el pie del marco y parecía cortado). El hueco se ajusta al tamaño de letra. Igual
  con fondo elegido.
- **Cabecera al plegarse:** el nombre ya no se apaga a media altura; encoge a la vez que su hueco, así que la banda negra nunca queda vacía.

## 0.20.1 — el reto y la semana en la portada, y el icono centrado

- **Debajo de «Seguir leyendo»**, el reto del año: «Reto 2026 · 13 / 30», su barra y cómo vas («VAS 2 POR DETRÁS · TE QUEDAN 17 EN
  52 DÍAS», «VAS AL DÍA», «¡RETO CUMPLIDO!»). Lo que toca a estas alturas es el reto repartido por igual entre los días del año (con hoy,
  hacia abajo). Sin reto, «Ponerte un reto →» (lleva a Ajustes); con reto, tocarlo lleva a Leídos.
- **«Esta semana»**, con el total y lo de hoy («4 H 01 · HOY 52′»): siete barras grandes que se tocan; la tocada se marca en el acento y
  encima sale lo que leíste ese día («V · 1 h 10», «S · nada»). De serie, marcada hoy si has leído. Debajo, los días seguidos.
  La etiqueta se pega a su lado en los extremos (no se sale) y deja sitio de sobra con la letra grande.
- **«También lees» y «Pendientes» lado a lado** cuando entre los dos hay tres portadas o menos (tan grandes como quepan, hasta
  120 dp); con más, cada uno en su fila como antes. «Tu año» queda con el mosaico (el reto ya va arriba). Fuera la línea pequeña de
  hoy/racha del final.
- **Icono**: la «S.» (letra y punto como conjunto) centrada con la tinta real de la fuente: en horizontal en el centro y en vertical en el
  hueco entre la zona segura y la franja «LIBROS» (en el monocromo, en el centro de la zona segura). Lámina de comprobación en
  `docs/marfil/iconos/comprobacion-icono.png` (círculo, squircle, redondeado, monocromo y tamaños pequeños).

## 0.20.0 — encuadrar la portada y el fondo, paspartú y velo

- **Encuadrar**, como en Bastet: la imagen se guarda entera y tú eliges qué parte se ve. En «Imágenes», en cada pestaña, «Encuadrar» abre
  la imagen en una ventana con la forma en que se verá (la portada a 2:3; el fondo, con la forma del cartel «Ahora lees»): arrastra para
  moverla y pellizca (o la barra de zoom) para acercarla; «Listo» guarda y «Restablecer» la vuelve a centrar.
- El encuadre es el **punto de la imagen que queda en el centro** y el zoom: así vale para huecos de otra forma. El del fondo se elige con la
  forma del cartel y la cabecera de la ficha (más baja) enseña una franja centrada en el mismo punto. La imagen siempre cubre el hueco entero.
- **Cada dispositivo encuadra a su manera** (un móvil y una tableta tienen otra forma): las imágenes viajan con la cuenta como siempre, pero el
  encuadre se guarda solo en el dispositivo, por libro y por imagen. Si la imagen cambia, en todos vuelve a verse centrada.
- Sale en todas partes: cartel, ficha, listas, estantes, mosaico, sagas y widgets (que recortan el bitmap con el mismo encuadre).
- **Paspartú**: la portada que va encima del cartel «Ahora lees» (con y sin fondo) y la que asoma en la apertura de la ficha llevan un marco
  liso del color de la página (crema de serie; sigue «A tu manera» y la noche), de unos 8 dp en una tapa de 150; con una portada casi blanca
  (luminancia media > 0,85), el marco va en la tinta. Se dibuja en un solo sitio (`PortadaSobreFondo`).
- **Velo** arriba de la ficha: negro al 80 % arriba, transparente a los 110 dp, para que «Volver · Imágenes · Editar · Borrar» se lean sobre
  cualquier foto.
- Arreglo: un libro en Leyendo con una fecha de fin de antes decía «terminado el …» en «01 Tu lectura»; ahora eso solo sale en un libro Leído
  y en los demás, «leído por última vez …».

## 0.19.0 — portada y fondo elegibles, como en Jonsu

- **Fondo por libro**: una imagen a lo ancho, además de la portada. Se guarda como la portada: en la cuenta, por su hash (`fondo`) y de dónde
  salió (`fu`); baja sola a tus otros dispositivos. Quitarlo deja `fondo = "-"` (en la cuenta un campo vacío no existe). Si un libro nunca ha
  tenido fondo en Seshat y en Jonsu le pusiste uno de internet (`az.hero`), se usa ese. En la copia de seguridad, `fondo` y `fu`; las copias
  viejas siguen valiendo.
- **IMÁGENES.** (Ficha → «Imágenes», o tocando la tapa o el fondo de arriba): pestañas LA PORTADA / EL FONDO, lo que hay ahora, «Buscar en
  internet» (la misma búsqueda de imágenes; para el fondo, con «fondo» detrás), «De tu móvil», «Quitar»; en la portada, «La del libro» y las
  propuestas de iTunes y Open Library; en el fondo, «Usar la portada». Sustituye a la hoja «Portada».
- **Cartel «Ahora lees»** con fondo: el fondo ocupa el cartel y la portada (150×225) va encima, arriba a la izquierda. Sin fondo, como antes.
- **Ficha** con fondo: el fondo es la foto de apertura y la portada (120×180) asoma abajo a la derecha pisando el borde; el título se aparta.
  Sin fondo, como antes.

## 0.18.0 — la portada «cartel», listas con portadas grandes y la ficha a doble página

Gonzalo: «se le da muy poca importancia a la portada del libro que te estás leyendo… quiero que las portadas en todas las partes de la app
sean más grandes». Vio maquetas y eligió el cartel, las listas con portadas grandes y, para la ficha, la «doble página».

- **Portada · cartel**: lo que lees ahora (el último que has abierto) de protagonista, en un bloque de 470 dp sobre el color medio de su tapa,
  con la portada a 220×330 dp centrada y con sombra, y encima, abajo, «AHORA LEES · N %», el título en grotesca, la barra gruesa y
  autor · páginas · tiempo; pegado debajo, «SEGUIR LEYENDO →» en el acento a todo el ancho. La portada acaba donde empieza el texto (lo que lleve
  pintado abajo no se pisa con el del cartel) y, si es de las generadas, sin autor. Si no lees nada, un cartel negro «Nada, de momento» con
  «Añadir un libro». Debajo: «También lees» (los demás, 120×180), «Pendientes» (104×156) y «Tu año» con el mosaico a 4 columnas.
- **Listas con portadas grandes**: en Leyendo, Pendientes, Leídos, Abandonados, autores, sagas y búsqueda cada libro va con su portada de
  84×126 dp, el número (01…) pequeño en el acento encima del título, el título en grotesca, el autor en versalitas y, abajo, cómo va: el % y
  las páginas con su barra (Leyendo, Abandonados), las páginas (Pendientes) o cuándo lo terminaste (Leídos). Las sagas agrupadas siguen igual,
  con el abanico al mismo tamaño. Las portadas generadas se leen a ese tamaño (título más grande en proporción y sin autor).
- **Ficha · doble página**: la tapa ampliada como foto de apertura (300 dp, a todo el ancho y recortada, sobre su color medio) con «← Volver» y
  «Portada · Editar · Borrar» encima; una etiqueta en el acento pisando el borde con el estado y el % («LEYENDO · 80 %», «LEÍDO»…); el título
  enorme en grotesca; «de <autor> · <saga>, n» en cursiva; el botón negro ancho de la acción principal (Seguir leyendo, Empezar a leer, Volver a
  leer, Bajar de tu kDrive y leer, Elegir su archivo) con el menú del archivo al lado; y las secciones numeradas en el acento: **01 Tu lectura**
  (barra gruesa, «486 de 608 págs · 9 h · te quedan ~2 h», la línea en cursiva, el estante para cambiar de estado y el favorito), **02 La saga**
  (sus tapas en fila con esta remarcada, «1 de 3 →»), **03 Tiempo y páginas**, **En Jonsu**, **Subrayados y notas** y **Datos**. No se ha
  quitado nada de lo que hacía la ficha.

## 0.17.0 — portada de estantes, cifras sin pegarse y la cabecera que se pliega sin cortar

- **Portada en «estantes de portadas»** (la idea 3 de las maquetas, `docs/familia/maquetas-revista/revista-seshat-portadas.png`): fuera
  «En la mesilla» y «En la estantería» como listas y el bloque del porcentaje gigante. Ahora:
  - **Leyendo · N →**: todo lo que lees en portadas grandes (96 × 144 dp, sin esquinas) que se deslizan, cada una con su barra de avance y
    el % en el acento; un toque abre la ficha; el título lleva a la página Leyendo.
  - El botón negro **«SEGUIR CON <título> →»** (el último que abriste).
  - **Pendientes · N →**: portadas medianas en el orden que tengas elegido en Pendientes; de cada saga, el siguiente que te queda
    («+N DE LA SAGA» debajo).
  - **Tu año · X de Y →**: la barra gruesa del reto y el mosaico de portadas de lo leído este año (6 por fila, hasta 3 filas; si hay
    más, la última casilla dice «+N» y lleva a Leídos).
  - Debajo, en pequeño: hoy, la racha y la semana en barras.
  - Vacíos con su mensaje y un botón para añadir.
- **Cifras que no se pegan**: las cifras de grotesca tenían el espaciado demasiado negativo (el «15» de la portada iba a −5 sp y los
  dígitos se tocaban). Las cifras van ahora sin espaciado negativo y los titulares, mucho menos apretados.
- **La cabecera se pliega sin cortar**: al bajar, el nombre «SESHAT.» se recortaba por arriba y se veía la mitad inferior de las letras
  (cada letra cortada a una altura distinta: parecía que «unas subían y otras bajaban»). Ahora el nombre encoge y se desvanece con el
  hueco; nunca se corta a media letra.
- **La grotesca, más regular**: Bricolage Grotesque es variable y, sin decirle el tamaño óptico, se dibujaba con el de cartel (96), con
  remates de distinta altura y un ancho estrecho (92). Ahora va con el de titular (opsz 48) y el ancho normal: las mayúsculas quedan
  parejas («AJUSTES.» incluida).
- **Escritos**, del todo en la dirección C: títulos en grotesca, lista numerada, filetes de 2 px y botones rectos («LEER →» / «ESCRIBIR →»).

## 0.16.1 — navegación por páginas

- **Una sola fila de pestañas**: PORTADA · LEYENDO · PENDIENTES · LEÍDOS · ABANDONADOS · AUTORES · SAGAS · ESCRITOS. La portada es la
  primera página (y la que se abre); cada sección es una página más a su derecha: se pasa tocando la pestaña o deslizando el dedo, sin
  abrir otra pantalla ni otra cabecera. Antes, la portada tenía su fila de pestañas (con «LEYENDO» marcada aunque estabas en la portada) y
  cada sección se abría en otra pantalla con otra fila igual: de ahí el lío.
- La cabecera (BUSCAR · + AÑADIR · fecha · ⚙ y «SESHAT.») es la misma en todas las páginas; el nombre grande se recoge al bajar y vuelve al
  subir. Cada página dice su nombre en pequeño. La fila de pestañas se desliza sola para que la elegida se vea siempre.
- Atrás, desde cualquier sección (o desde Ajustes), vuelve a la portada; desde la portada, sale. Las páginas conservan por dónde ibas, y
  al cerrar una ficha (libro o saga) se vuelve a la misma página.
- Lo que llevaba a una sección («Ver los que lees →», los widgets, los avisos, Jonsu y Thot con `go=…`) lleva a su página.
- Escritos es también una página (con «Actualizar» arriba a la derecha).

## 0.16.0 — dirección C («editorial moderna»), la prueba de la familia

La primera app de la familia con la estética nueva elegida en las maquetas (`docs/familia/maquetas-revista/`). Solo cambia cómo se ve; la
lógica es la misma.

- **Cabecera en bloque de tinta** con la fila pequeña (Buscar · + Añadir · fecha · ⚙) y «SESHAT.» en grotesca enorme (Bricolage Grotesque,
  la letra que ya venía con la app) con el punto del acento. Igual en las secciones («LEÍDOS.») y en Ajustes («AJUSTES.»). Al bajar, se
  pliega en una barra de tinta con «SESHAT.».
- **Pestañas en bloques**: la elegida, rellena del acento; los filtros (orden de un estante), bloques con borde de 2 px.
- **Portada**: la portada del libro, el porcentaje gigante en grotesca, «% · 200/540 PÁGS», el título y el botón negro recto de ancho
  completo «SEGUIR LEYENDO →»; la rejilla dura de cifras (hoy · reto · días seguidos, en acento, papel y azul); las barras planas de la
  semana con hoy en el acento; «En la mesilla» y «En la estantería» como listas numeradas (01, 02…) con filetes de 2 px.
- **Secciones**: rejilla de cifras (libros · horas · páginas) y listas numeradas.
- **Ficha**: cabecera del color de la tapa con el título en grotesca en mayúsculas y la portada; el estante en bloques (el elegido, de
  tinta); rejilla de tres cifras; el botón ancho del acento con la flecha.
- **Botones** rectos (sin píldoras), **paneles** con borde de 2 px, **titulares** en grotesca; la serif queda para citas y líneas en cursiva.
- **Widgets**: titulares en grotesca, sin el reborde fino, botones rectos, portadas sin esquinas, barra de progreso más gruesa; «SESHAT.»
  con el punto del acento, y «SESHAT en papel» como bloque de tinta. Previsualizaciones del selector regeneradas con los colores de Seshat.
- **Icono nuevo**: la «S.» en grotesca enorme sobre #F8F0D4 con el punto #C68B00 y, abajo, la franja del acento con «LIBROS»
  (adaptable y monocromo; lo genera `herramientas/familia-c/icono-c.py`, listo para las demás apps).
- Los colores siguen saliendo del tema (Apariencia / «A tu manera»); el azul de los bloques de cifras (#1F3A5F) es el único color fijo.

## 0.14.2 — lo que eliges, en todos tus dispositivos

- **El orden de cada estante, «tener aquí los libros de tu kDrive», la web de imágenes y «Actualizar sola» ya están en tu cuenta**, no
  solo en cada dispositivo (la apariencia, los ajustes del lector y los objetivos ya viajaban). La primera vez, lo que ya tenías pasa sin
  perder nada: lo que solo tenía uno, tal cual; lo que tenían distinto, el del dispositivo donde lo cambiaste más tarde. Después, de cada
  ajuste gana el último cambio, venga de donde venga.
- **Lo que lees de Thot sigue donde lo dejaste, en cualquier dispositivo.** Por dónde vas en una obra, sus subrayados, notas y
  marcadores, sus ajustes y el tiempo leído se quedaban en el móvil donde la leías; ahora van con tu cuenta, como los de cualquier libro. La
  obra sigue sin ser un libro de tu biblioteca (ni va a Jonsu). Lo que ya habías leído en cada móvil sube la próxima vez que la abras ahí.
- Seshat de Linux y Mac todavía no lee el orden de los estantes ni lo leído en los escritos: llegará en su próxima versión.

## 0.14.1

- **El lector ya no se cierra solo.** Android puede cerrar lo que dibuja la página del libro (con un libro grande o el móvil justo de memoria), y si la app no lo atiende, Android cierra la app entera. Ahora se vuelve a abrir el libro donde ibas.
- **Una versión nueva ya no te saca de lo que estás haciendo.** El actualizador instala en silencio cuando no usas la app, y Android la cierra para instalarla. Pero no siempre sabía que la estabas usando: si se entraba directamente a un libro (desde un widget, o cuando Android vuelve a abrir la app tras cerrarla por detrás), creía que no estabas; y con la pantalla apagada un momento, también. Ahora se lo pregunta a Android, nunca instala con la pantalla apagada o bloqueada, y con un libro abierto espera a que lleves un rato fuera.
- **Los títulos grandes ya no salen cortados** (salía «Marfi» en vez de «Seshat» hasta entrar y salir de Ajustes). Si tu apariencia usa una letra de Google Fonts, llega un momento después de abrir la app; el título se medía con la de mientras, más estrecha, y no se volvía a medir. Ahora sí.
- Si Android cierra lo que dibuja una página web de la app (la búsqueda de portadas), se cierra esa pantalla y no la app entera.

## 0.14.0

- **Lo que subrayas en el ordenador sale en el móvil, y al revés.** Seshat para Linux y Mac (1.1.0) ya pinta tus subrayados y tus notas
  y los crea (seleccionas con el ratón y eliges Subrayar, Nota o, para una palabra, Definir: el mismo Wikcionario que aquí), con marcadores,
  y todo va a la cuenta exactamente como lo guarda el lector
  del móvil (`marcas/<kid>~<id>`, el mismo id, la misma hora de edición): una nota editada en los dos sitios es la misma nota, y lo que
  quitas en uno se quita en el otro. Lo comprueba una prueba de cada lado: la del ordenador escribe en el emulador de Firebase y la del
  móvil (`MarcasEscritorioTest`) lee esos mismos nodos con el código de Android.
- **Las posiciones, letra a letra.** Para que un subrayado caiga en las mismas letras se comparó el ordenador con el lector del móvil de
  verdad (foliate en Chromium, `herramientas/marfil/lector-prueba/cfi.js`). Así salió que el botón oculto «← Capítulo anterior» que el
  lector pone arriba de cada capítulo cuenta en las posiciones: el ordenador no lo contaba y, del segundo capítulo en adelante, lo que
  guardaba abría en el móvil un párrafo antes (y lo del móvil, en el ordenador, uno después). Ya cuenta igual. Tampoco contaba bien el
  porcentaje en los libros con páginas que no se leen seguido (portadas, notas finales): ahora es el mismo que en el móvil.
- **Los ajustes del lector viajan también al ordenador**: tema (Papel, Sepia, Gris, Noche, Negro, Como la app), letra (las mismas: Literata,
  Garamond, Fraunces, Atkinson…), tamaño, interlineado, márgenes, justificado, párrafos, páginas o continuo, dos columnas y luz cálida, con
  los del libro mandando sobre los de partida, como aquí. Y tu ritmo de lectura (el que calcula lo que te queda) se comparte.
- **En la tableta tumbada, el panel va de lado**: Índice, Notas, Letra y Buscar salen a la derecha, de arriba abajo, y el libro se sigue
  viendo (antes eran hojas de todo el ancho de la pantalla, con las filas estiradas de punta a punta). En vertical (o en un móvil ancho),
  las hojas y la de las notas al pie van al centro, del ancho de una columna.
- **El índice dice dónde empieza cada capítulo** (su porcentaje) y marca «Estás aquí» en el que lees.
- **La barra de lo leído tiene una marca donde empieza cada capítulo**, como en el ordenador.
- **Los subrayados con nota llevan una raya del acento** debajo: se ve dónde hay nota sin tocar nada (en el ordenador, una raya en el margen).
- La portada ya no se repinta entera a cada píxel al bajar (gastaba batería), y la versión de debajo de la portada es la de verdad (la
  0.13.5 decía 0.13.3; ahora lo comprueba una prueba).

## 0.13.5

- **Una pantalla que no dejaba de redibujarse**: los títulos que se encogen para caber (FitTitle) apuntaban su ancho desde dentro de la
  medida; en una fila de dos casillas Compose mide dos veces (para saber el alto y para pintar) y, si los dos anchos diferían en un píxel
  por redondeo, se pisaban en cada fotograma: la pantalla se recomponía sin parar (CPU y batería) mientras estaba abierta. Pasaba en la
  tablet en la ficha de un juego «Jugando» de Jonsu; la misma pieza está en las cuatro apps y en las de escritorio, y se arregla en todas.

## 0.13.4

- Los capítulos de Thot llegan con su hora de edición (`ue`; antes llegaba vacía y «capítulos nuevos desde la última vez» no
  contaba ninguno).

## 0.13.3

- **Las fechas que llegaron de Atrio estaban en 1970.** Atrio guarda el día de empezar y de terminar como número de día, y al
  traerlo se tomó por milisegundos: los libros terminados en Atrio quedaron terminados el 1 de enero de 1970, y por eso el reto del año
  no los contaba. Ahora se leen
  bien, se corrigen una vez en tu cuenta (así las ven igual Jonsu y tus otros dispositivos) y lo que se traiga de Atrio llega bien.

## 0.13.2

- **El reto del año cuenta todos tus leídos.** Un libro leído sin fecha de terminado (llegado de Atrio, de otro dispositivo o de
  antes) no contaba: ahora cuenta desde que pasó a «terminado» en la cuenta (que guarda la hora de cada cambio), o desde la última
  vez que lo abriste si eso no se sabe.
- **Las pestañas de arriba se dejan deslizar** (antes volvían solas a la elegida mientras las arrastrabas) y **las opciones de
  orden se ven todas** (si no caben en una línea, siguen en la siguiente; antes la última salía cortada y, al intentar deslizarla,
  pasabas de página).
- **Entrar una vez, de verdad.** Si entras en otra app de la familia mientras esta sigue abierta, esta entra sola en cuanto
  vuelves a ella (antes solo lo miraba al arrancar, y podía quedarse sin cuenta hasta que Android la cerrara).
- **Salir y volver a entrar ya no deja la app sorda.** Al salir de la cuenta se tiraban las colecciones y lo que las escuchaba se
  quedaba oyendo unas que ya no cambiaban: ahora son las mismas, vacías, y se siguen oyendo.

## 0.13.1

- **Los libros vuelven a subirse solos a tu kDrive, como en Atrio.** En la 0.13.0 se había perdido: solo se subían con «Subir a tu
  kDrive ahora». Ahora, en cuanto añades un libro (o le cambias el archivo), se sube solo a «Mi carpeta/Marfil/Libros/<título>/» con la
  conexión que haya, aunque cierres Seshat, y tus otros dispositivos lo bajan al abrirlo. «Tener todos mis libros aquí» vuelve a
  bajarlos solos, con Wi-Fi. También se mira al abrir Seshat y tras cada sincronización. Si una subida falla, se reintenta sola a la
  media hora (a mano, cuando quieras).

## 0.13.0 — la familia

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
- **Las portadas viajan con la cuenta** (no por kDrive): las mismas en todos tus dispositivos y en Jonsu.
- **Lo leído de cada libro** (por dónde vas, subrayados, notas, marcadores y tiempo) viaja por separado: un subrayado hecho en la tablet
  y otro en el móvil se juntan siempre.
- Los objetivos de lectura son de la cuenta; los Escritos de Thot llegan de la cuenta.

## 0.12.20

- **Fechas y cifras como en las otras tres** (`Formato.kt`, con las mismas pruebas en las cuatro): «16 oct 2026» en fichas y textos, «16/10/2026» en las tablas de datos y siempre con punto de millar («4.010»). Los meses van escritos en la app (Android, según la versión, les ponía punto: «oct.»).
- **Escritos: «N capítulos nuevos»** en el acento cuando Thot ha publicado capítulos desde la última vez que abriste la obra, y debajo, por dónde vas («vas por el 34 % · Capítulo 5»).
- «En Jonsu» sin nota: una línea, «Sin valorar aún · Valorar en Jonsu →».
- Al elegir el libro de un widget, los terminados dicen «TERMINADO».
- Los contadores de las pestañas van en tinta (más oscura la de la pestaña elegida), no en rojo: el rojo queda para lo que se toca.
- En las listas, la línea de debajo del título un punto más grande y más legible.
- **La cabecera de la portada se pliega al bajar**: arriba solo se quedan el nombre y «Buscar · + Añadir»; el número, la fecha, las barras y el menú se van con la página.
- Debajo de «Seshat · hecho en casa», en gris, la versión instalada.

## 0.12.19

- **El lector, más fluido.** Mientras leías, cada poco (cada 0,4 % avanzado) Seshat reescribía la biblioteca entera y, un segundo
  después, repintaba todos los widgets (decodificando portadas) justo mientras pasabas página o abrías el menú. Ahora, con el libro
  abierto, los widgets no se repintan (se hace al cerrarlo) y la ficha se pone al día cada 2 % o cada 20 s, y siempre al cerrar. Las
  barras del menú van en su propia capa: aparecer ya no obliga a repintar la página que hay debajo.
- **Cambiar qué libro sigue un widget sin quitarlo**: déjalo pulsado en el escritorio y sale «Reconfigurar» (Android 12 o más, en los
  lanzadores que lo ofrecen, como el de Pixel): abre la misma lista que al ponerlo («Sigue leyendo» y «En portada»: el que toque o uno fijo). El menú del dedo pulsado es del escritorio,
  no de la app: lo único que una app puede hacer es decir que su widget se puede reconfigurar, y eso es lo que se ha hecho.
- **Un libro terminado dice «Terminado»**, en el acento, en vez de su porcentaje: en su ficha (en grande, con la barra llena), en las
  listas (a la derecha, donde van las páginas) y en los widgets.

## 0.12.18

- **Arreglo de las portadas que se perdían.** Al abrir Seshat se mezcla tu biblioteca con la de tus otros dispositivos y, a la vez, se bajan
  las portadas. La mezcla se hacía con una copia de la biblioteca de unos segundos antes y, al aplicarla, quitaba la portada que acababa
  de llegar (y el archivo de un libro recién bajado). Ahora la portada y el archivo se toman de como están en ese momento.
- Las portadas que ya estaban bajadas en el móvil pero el libro había dejado de apuntar a ellas **se vuelven a poner solas** al abrir
  Seshat (sin bajar nada). Las que no estén en el móvil se bajan de tu kDrive como siempre.
- **«¿Vas por aquí?» tras un salto largo** en el lector (un cuarto del libro o más de golpe: del 98 % al 2 %, o al revés, con la barra o el
  índice): sale un aviso con «Voy por aquí». Si lo pulsas, esa es tu posición de verdad aunque sea menos de lo que llegaste: el libro
  abre ahí, el porcentaje de la ficha baja (y vuelve a crecer al leer) y lo mismo en tus otros dispositivos y en Jonsu. Marcar el
  principio deja el libro en Pendientes; marcar antes del final uno que salía Leído lo pasa a Leyendo.
- Mientras no lo confirmes, un salto largo **ya no cuenta como leído**: mirar el final no sube el porcentaje ni se queda como «lo más lejos».

## 0.12.17

- Los «días seguidos» leyendo siguen la misma regla en Bastet, Seshat, Thot y Jonsu (`Racha.kt`, con las mismas pruebas en las cuatro, y en el contrato): cuenta un día con al menos un minuto leído; si hoy aún no, desde ayer. Para ti no cambia nada (era la misma).

## 0.12.16

- **Un solo objetivo de lectura**: los minutos al día y los libros del año los fija Seshat (Ajustes → Lectura) y viajan con tu cuenta
  (`marfil/objetivos`) a tus otros Seshat y a Jonsu, que los enseña sin poder cambiarlos. Antes cada Seshat tenía el suyo y Jonsu
  otro, y podían no coincidir. La primera vez, si Seshat no tenía ninguno, se traen los que tenías puestos en Jonsu.

## 0.12.15

- En las filas de datos (etiqueta · puntos · cifra), si no cabe todo en una línea, la etiqueta va entera arriba y la cifra debajo, en vez
  de cortar la etiqueta con puntos suspensivos (como en Jonsu 1.2.8).

## 0.12.14

- El widget «Sigue leyendo» alto y estrecho ya no corta su rótulo («SIGUE LE…»): ahí dice «Leyendo».

## 0.12.13

- La fila de orden y filtros (Recientes · Nombre · …) lleva el mismo degradado en los bordes que las pestañas.
- En los bordes de la fila de pestañas, un degradado cuando hay más pestañas por ese lado (para que no parezcan cortadas).

## 0.12.12

- Las pestañas (secciones, Ajustes) se desplazan solas solo cuando la elegida no se ve entera, y entonces la centran: antes se
  pegaba al borde izquierdo y la de al lado salía cortada.

## 0.12.11

- Lo leído llega a tu cuenta aunque no haya red al cerrar el libro o Android congele Seshat nada más salir: queda un trabajo de fondo
  (SubidaWorker) que sube lo pendiente en cuanto hay red, aunque la app siga congelada. Si Seshat ya lo subió, no hace nada.

## 0.12.10

- Textos al día: lo que seguía diciendo «Atrio» para hablar del móvil (seguir sus colores en directo, copiar su apariencia de tu cuenta,
  «los mismos que en Atrio») dice ahora Jonsu, que es quien está en el móvil. La cuenta sigue siendo la cuenta de Atrio.

## 0.12.9

- **Arreglo**: en Android 13 o más, los avisos del actualizador («Seshat tiene una versión nueva», «Seshat se ha actualizado, toca para abrirla»)
  no se veían nunca: faltaba el permiso de avisos. Ahora se declara y se concede desde Ajustes → Avanzado («Avisos de actualización»).
- **Arreglo**: en Android 11 o más, «Escribir en Thot» no salía nunca (Seshat no declaraba que puede ver a Thot; los tests no lo
  detectan porque Robolectric no filtra paquetes). Ahora el manifiesto declara a Bastet y Thot, y un test lo vigila.
- Un subrayado o una nota hechos en otro dispositivo repintan los widgets («La cita») en cuanto llegan, no solo cuando cambia la biblioteca.

## 0.12.8

- Mientras lees, lo leído (posición, tiempo, subrayados) sube a tu cuenta como mucho una vez por minuto, no cada pocos segundos; al salir del
  lector sube al momento, como antes. Jonsu y tus otros Marfiles lo escuchan, así que con eso basta y se gasta mucho menos.

## 0.12.7

- Lo que Jonsu sube (sus fichas, para «En Jonsu» y para traer libros) no se vuelve a bajar si sus sellos no han cambiado.

## 0.12.6: la ficha de un escrito

- La ficha de una obra de Thot (Escritos) no ofrece portada, editar, borrar, estante, saga, favorito ni «En Jonsu» (nada de eso es
  suyo): arriba lleva «Escribir en Thot». Y quitarla de la biblioteca no deja rastro en lo que viaja a tus otros dispositivos.

## 0.12.5: la nota de Jonsu, al momento

- Seshat escucha el sello de Jonsu: en cuanto valoras un libro allí, «En Jonsu» en su ficha cambia sin salir ni esperar los 15 minutos.
- Las fechas de la ficha salen siempre en español.

## 0.12.4

- En la portada, «+ Añadir» en el acento, como en las listas y como en Bastet y Thot; el pie de Ajustes dice de quién es hermana.

## 0.12.3: Jonsu abre el libro exacto, y la obra exacta de Thot

- Seshat entiende el extra `clave` (la clave del libro, la misma en todos tus Marfiles): «Leer» en Jonsu abre Seshat en la ficha de ese libro.
  Los widgets siguen usando `ficha` (por id) y `leer` (directo al lector).
- En Escritos, «Escribir en Thot» abre Thot **en esa obra** (no en su portada). Y al revés: Jonsu y Thot pueden pedir
  «lee esta obra» (`go=escritos` + `obra`), y Seshat la prepara y la abre en el lector.

## 0.12.1: los Escritos llegan solos

- Seshat escucha el sello de Thot: en cuanto das un capítulo por terminado allí (y Thot sincroniza), aquí se traen los Escritos y se
  rehace su EPUB, sin entrar en la pantalla (Thot 0.2.1 o más nueva).
- Al subir lo leído de un libro, los nodos ligeros que lee Jonsu (`tiempo`, `subrayados`) van antes del sello: el Diario de Jonsu ya no
  puede leerlos viejos.

## 0.12.0: los Escritos de Thot, portadas para Jonsu y ajustes en pestañas

- **Escritos** (en el menú): las obras de Thot con capítulos terminados se leen aquí, con el lector de Seshat. Cada obra se convierte
  en un EPUB (portada, partes y capítulos, con las cursivas, negritas, citas y saltos de escena de Thot) que se rehace solo cuando
  Thot cambia algo. Son libros «de casa»: no salen en las listas, ni en los estantes, ni viajan a otros dispositivos ni a la nube.
  «Escribir en Thot» abre Thot si está en el móvil; Jonsu abre esta pantalla desde su «Escritos».
- **Portadas**: la dirección de la portada que elegiste de internet viaja con el libro (`pu`), así Jonsu (y tus otros móviles)
  enseñan la misma portada que tienes aquí. Las que subiste de tu galería siguen yendo por kDrive.
- **Ajustes** en pestañas, como Atrio: Apariencia · Lectura (objetivos y el lector) · Cuenta (Atrio y los libros en la nube) ·
  Avanzado (actualizaciones y copias). Lo mismo, pero sin tenerlo todo a la vista.

## 0.11.1

- **Jonsu**: una ficha que Jonsu refleja de Seshat y que aún no has valorado (todas las notas a 0, sin reseña) ya no sale en «En
  Jonsu» como un 0: sigue «sin valorar».

## 0.11.0: el estado arriba y los widgets eligen libro

- En la ficha, las pestañas de estado (Pendiente · Leyendo · Leído · Abandonado) van arriba, pegadas a la barra; debajo quedan la saga y el favorito.
- **Los widgets eligen libro**: al poner «Sigue leyendo» o «En portada» en el escritorio, Android pregunta qué libro abre: «el que toque» (lo
  que estés leyendo, como hasta ahora) o uno fijo.

## 0.10.1: los filtros de orden en todas las secciones

- Las secciones ya no precomponen las páginas vecinas: en las pruebas del ordenador, con ellas precompuestas, los filtros de orden («Título»,
  «Autor»…) de las páginas que no eran la primera no recibían el toque. Si en el móvil te pasaba, esto lo arregla; si no, no cambia nada visible.

## 0.10.0: sección Sagas

- Nueva pestaña **Sagas** al final de las secciones (Leyendo · Pendientes · Leídos · Abandonados · Autores · Sagas), también en el
  menú de la portada. Lista todas tus sagas por nombre, con su abanico de portadas y su resumen; cada una se despliega para ver sus
  libros en orden y abrir su página.
- Desde ahí se crea una saga nueva («+ Nueva saga»): se abre su página para ir metiendo libros con «Editar».

## 0.9.2: portadas solo las tuyas

- Seshat ya no saca portadas de los archivos (ni de los EPUB ni la primera página de un PDF). Si no has puesto tú una portada,
  el libro se queda con la «tela» generada (color según el título, con la letra o el título en crema).
- Solo se sincronizan por kDrive las portadas que pones tú; «Quitar portada» también se propaga a tus otros dispositivos.
- Al actualizar, las portadas automáticas que había se limpian una sola vez; las que pusiste tú se conservan.

## Lo leído entre dispositivos

Además de la biblioteca, cada libro sincroniza su estado de lectura por la cuenta: `/users/<uid>/marfil/lec/<libro>/<dispositivo>` (el estado entero) y `/lstamps/<libro>/<dispositivo>` (su hora, que es lo que se escucha en directo). Se une así (ver `MezclaLectura`): posición, la más reciente; «lo más lejos que has llegado», el mayor; ajustes del libro, los últimos; subrayados, notas y marcadores se juntan (los quitados viajan como borrados con hora); el tiempo leído se suma por dispositivo. Al abrir un libro se baja antes lo último de los demás (hasta 2,5 s de espera) y al salir se sube.

## Libros por kDrive e importar de Atrio

- Cada libro que añades se sube solo (con Wi-Fi) a `Mi carpeta/Marfil/Libros/<título>/`; el índice (`/users/<uid>/marfil/nube/<libro>`: id de kDrive, nombre, tamaño) viaja por tu cuenta. En otro dispositivo el libro sale «sin archivo» y se baja al abrirlo (hace falta conectar el kDrive en Ajustes → Avanzado).
- Ajustes → Libros → «Traer mis libros de Atrio»: lee (sin cambiar nada en Atrio) lo que Atrio subió a tu cuenta y crea cada libro con su ficha, lo leído (posición, subrayados, notas, marcadores, tiempo) y, si Atrio lo subió a kDrive, el enlace a su archivo (no se duplica). Los «abandonados» de Atrio entran como pendientes. Se puede repetir sin duplicar.

## Portadas, ficha y widgets (0.7)

- **Ficha** con la estética de la de Atrio: titular, línea en cursiva, acciones (Leer / Bajar y leer / Elegir archivo, Portada, Editar, Archivo), estante y tiempo, subrayados y notas, datos. Se puede cambiar el título y el autor, elegir o cambiar el archivo, quitar el archivo de este dispositivo y quitar el libro.
- **Portada**: de la galería, de internet (iTunes Libros y Open Library, sin clave), la del propio libro, o ninguna (entonces, una tapa de tela con el título; en pequeño, la inicial). La que eliges se sube a `Marfil/Portadas/` de tu kDrive (`/marfil/portadas/<libro>` en la cuenta) y sale en tus otros dispositivos.
- **Tus libros en todos tus dispositivos**: un trabajo en segundo plano (WorkManager, con Wi-Fi) sube libros y portadas y, si lo dejas activo (Ajustes → Libros), baja los que faltan.
- **Widgets** (los mismos de Atrio y Carmesí, con libros): Sigue leyendo, En la mesilla, En portada, ¿Y ahora qué?, Lectura de la semana (con tu objetivo), Una cita (de tus subrayados; un toque abre el libro en esa frase) y la cabecera (sola o en papel). Vista previa en `docs/widgets/hoja.png`.

## Seshat 0.8

- **Secciones como páginas**: Leyendo, Pendientes, Leídos, Abandonados y Autores se recorren con el dedo (o con las pestañas); Buscar mira en todos los estantes.
- **Sagas** (las de Atrio, de vuelta): en las listas los libros de una saga salen juntos (un toque los abre; «Ver la saga →» va a su página, con lo que toca leer ahora, ordenar, añadir y quitar libros). Se crean desde la ficha («Saga →»), viajan con tu cuenta y se traen de Atrio al importar.
- **Inicio**: «Ahora» (lo último que abriste), «En la mesilla» (lo que lees a medias, menos «Ahora») y «En la estantería» (lo pendiente). A la derecha, en acento, las páginas del libro (`320 págs`; `118/320` si lo llevas empezado).
- **Páginas de cada libro**, de la fuente más fiable: lo que escribes tú → Atrio / el PDF → Open Library → calculadas por el texto del EPUB. Ver `Paginas`.
- **Ficha** con la casilla de lectura de Atrio (cuadrados anchos: «Bajar de tu kDrive», «Elegir archivo», «Seguir leyendo»).
- **Portada**: además de la galería, iTunes y Open Library, el buscador de imágenes de Atrio con **Pinterest** en cuadrados (`Internet.kt`).
- **Abandonados** como estado propio (y un abandonado que vuelves a leer pasa a Leyendo).
- **Widgets**: las etiquetas llevan siempre la negrita de la app, como en Atrio.

## Seshat 0.8.1: leer deslizando

- Deslizando (PDF o cómic «hacia abajo», o «Desplazamiento continuo»): en vez de mantener pulsado, **dos toques seguidos** seleccionan la palabra (subrayar, nota, diccionario); un toque suelto espera una fracción de segundo por si es el primero de dos.
- La línea de abajo (lo que queda y el %) no se enseña en ese modo: se solapaba con el texto.
- Al llegar al final de un capítulo se puede **seguir deslizando**: arrastrar o el impulso del dedo pasa al siguiente capítulo (y al principio, al anterior). Probado con un navegador con pantalla táctil: `herramientas/lector-prueba`.

## Seshat 0.8.2

- **Ordenar** cada estante (Recientes, Añadidos, Título, Autor, Páginas); se recuerda por estante.
- **Atajo «Seguir leyendo»** al mantener pulsado el icono de la app: abre el libro que estás leyendo en tu página.
- **APK más pequeño**: solo arm64 y x86_64 (el traductor del diccionario se copiaba para cuatro procesadores).

## Seshat 0.9.0

- **Fecha de terminado** (y «2.ª vez» si lo relees): se apunta sola cuando un libro pasa a Leído, viaja entre dispositivos, sale en la ficha y es el orden «Terminado» de Leídos. De Atrio se trae la fecha en que lo terminaste.
- **Reto anual** (Ajustes → Lectura → «Libros al año»): en la portada («RETO 2026 · 7 DE 12 LIBROS») y en el widget de la semana.
- **Ajustes del lector iguales en todos tus dispositivos** (letra, tamaño, tema, márgenes…); el brillo y lo propio de los PDF son de cada dispositivo.
- **Subrayados en un nodo ligero** de tu cuenta (`/marfil/subrayados`) para la cita del Diario de Jonsu.
- **«En Jonsu»** en la ficha: la valoración (solo se ve) y «Abrir en Jonsu →».
- **Portadas que en Atrio eran fotos tuyas**: al importar se enlazan con su archivo de tu kDrive y se bajan a todos tus dispositivos.
- El botón de un libro ya leído dice «Volver a leer».

## Seshat 0.9.1: pasar de capítulo con un botón

- Leyendo deslizando («Desplazamiento continuo», EPUB y PDF adaptado), **ya no se pasa de capítulo solo** (el salto parpadeaba y no se sabía dónde se llegaba). Al final de cada capítulo hay un botón **«Siguiente capítulo → nombre del capítulo»** y al principio **«← Capítulo anterior»** (que lleva al final del anterior). Al pasar, un aviso dice el capítulo al que se llega.
- En el modo de páginas los botones no se ven. En el último capítulo no hay botón de siguiente.
- Probado con un navegador táctil: `herramientas/lector-prueba/botones.js`.
- **Icono nuevo**: la flor de líneas sobre el degradado crema (fondo, primer plano y monocromo para los iconos con tema). Se genera con `herramientas/icono-marfil.py` a partir de `docs/iconos/marfil-original.jpg`.
