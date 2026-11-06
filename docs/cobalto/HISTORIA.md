# Cobalto

La app de escritura del móvil. Hermana de [Atrio](https://github.com/gvegagato-prog/Atrio-Suite-2.0), [Marfil](https://github.com/Gonvegato/lectura-app)
y [Carmesí](https://github.com/gvegagato-prog/Android-Launcher): mismo sistema de temas (las mismas paletas, letras y «estilos»), otro paquete
(`com.gonzalo.cobalto`), así que **conviven en el mismo móvil** y se ven como programas de la misma suite.

Cobalto es **Escribir de Atrio, entero y con casa propia**: tus obras, y dentro de cada una el manuscrito (partes, capítulos y escenas), las
fichas, el árbol, las notas, la línea temporal, las cifras y la guía de la novela; sprints, sinónimos, papelera, borradores e historial de
versiones, comparar y mezclar, exportar (texto, Markdown, Word, PDF) e importar, y lo de la pareja (compartirle capítulos y que te los anote).
Sin migración: usa los mismos documentos que Atrio y Atrio del PC (`/users/<uid>/escritos`), con el formato de `docs/escribir.md` de Atrio.
Lo que escribas en el PC aparece aquí al momento, y al revés.

## 0.4.1

- **Una versión nueva ya no te saca de lo que estás haciendo.** El actualizador instala en silencio cuando no usas la app, y Android la cierra para instalarla. Pero no siempre sabía que la estabas usando: si se entraba directamente a una pantalla que no es la portada (desde un widget, o cuando Android vuelve a abrir la app tras cerrarla por detrás), creía que no estabas; y con la pantalla apagada un momento, también. Ahora se lo pregunta a Android, nunca instala con la pantalla apagada o bloqueada.
- **Los títulos grandes ya no salen cortados** (se cortaban por el final hasta cambiar de pantalla y volver; en Marfil salía «Marfi»). Si tu apariencia usa una letra de Google Fonts, llega un momento después de abrir la app; el título se medía con la de mientras, más estrecha, y no se volvía a medir. Ahora sí.

## 0.4.0 — el modo escritor

- **Concentración de verdad**: a pantalla completa (sin barra de estado ni de navegación; vuelven un momento si deslizas
  desde el borde) y la pantalla no se apaga mientras escribes. Si quieres, todo se apaga salvo el párrafo que escribes, y
  abajo quedan las cuentas, muy discretas. «Salir» arriba a la derecha, o Esc.
- **Las cuentas siempre a la vista**: palabras (o las elegidas, «57 de 1.234»), minutos de lectura, lo de esta sesión, lo que
  llevas de la meta de la escena y, a la derecha, lo de hoy frente a la meta del día, con su barrita.
- **La página a tu gusto** (Aa en la barra de abajo): la letra (la de la revista, palo seco, máquina, Literata, Garamond, Lora,
  Merriweather o Courier Prime), el tamaño, el interlineado (junto, normal, amplio, doble), el ancho en la tableta, la
  **sangría de libro**, la máquina de escribir y la tipografía automática, viendo un párrafo de prueba al momento. Se guarda en
  cada dispositivo (en la tableta puedes tener otra letra que en el móvil).
- **Máquina de escribir** mejor: también las primeras líneas se quedan a media altura, y sin saltos al escribir deprisa. Sin
  ella, el cursor ya no se esconde debajo de la barra o del teclado.
- **Buscar y cambiar**, con «palabra entera» (para cambiar «Ana» sin tocar «Anabel») y cuántas veces sale en **toda la obra**:
  la lista de escenas con cada vez resaltada, y al tocar una se abre esa escena justo ahí. «Todos en la obra» pregunta antes
  y guarda una versión de cada escena que cambia. Desde «Buscar» de la obra, una escena se abre con lo buscado resaltado.
- **La ficha que nombras**: con el cursor en un nombre subrayado sale su vista previa (qué es, la frase, unos datos, con quién
  está unida); al tocarla se lee la ficha.
- **Fichas y notas, primero para leer**: se abren como una página de revista (el nombre a lo grande, la frase en cursiva, la
  letra capitular, los datos en su recuadro y, en la tableta, en una columna al lado) y al tocar lo escrito pasan a editarse.
  Al guardar se vuelve a leerlas. Igual los sucesos de la línea y las uniones del árbol.
- **Comparar borradores y versiones palabra a palabra**: lo que cambia va resaltado (lo que se va, tachado) y, en la tableta,
  los dos textos uno al lado del otro.
- **De escena en escena sin salir**: al final del texto, la anterior, la siguiente y «+ Escena nueva a continuación».
- **Atajos con teclado físico** (en la tableta): Ctrl+B/I, deshacer y rehacer, Ctrl+F y Ctrl+H (buscar y cambiar), F3,
  Ctrl+S (guardar una versión), Ctrl+1/2/3 (títulos), Ctrl+Intro (salto de escena), Ctrl+D (raya), Alt+↑/↓ (subir o bajar
  el párrafo), Ctrl+RePág/AvPág (otra escena), F11 (concentración), Ctrl+T (máquina), Ctrl+P (panel), Ctrl++/− (letra),
  Ctrl+K (sinónimos)… y Ctrl+/ los enseña todos.
- **Arreglo importante**: al pasar de una escena a otra sin salir del editor (por ejemplo, desde «Sale en» de una ficha), la
  escena de antes se guardaba con el texto de la nueva. Ya no (y una prueba lo vigila).
- **Lo escrito, más a salvo**: si Android pausa la app (otra app, la pantalla apagada), lo último se guarda en el momento, sin
  esperar al medio segundo de calma. Y si llega un cambio de otro dispositivo mientras escribes, se juntan los dos a tres bandas
  (antes ganaba lo de aquí y lo de fuera se perdía); si chocan en la misma frase, lo de fuera se guarda como borrador.
- **En la tableta**: la portada en dos columnas en horizontal («Ahora» y, al lado, lo que tienes sobre la mesa y en el cajón),
  las barras de la semana finas (antes eran ladrillos), los cuadrados del manuscrito del mismo alto (cada fila, pareja), las
  fichas como un reparto (las que quepan a lo ancho, cada una con la franja de su color) y el tablón de notas en tres columnas.
- «Leerlo entero» (un capítulo) empieza con letra capitular, como un libro.
- En Ajustes → Escritura, «La página del editor» y «Atajos de teclado».
- **Ninguna pantalla se queda recomponiéndose sola** (lo que gasta batería con la app abierta): una prueba recorre la portada,
  las secciones, cada pestaña de la obra, una ficha, un capítulo, el editor y la concentración, en el móvil y en la tableta, y
  cuenta lo que se repinta en un segundo sin tocar nada: nada.
- Las cifras de las pestañas de las secciones, en tinta (como las de la obra; el rojo, para lo que se toca).
- **La portada ya no se repinta entera a cada píxel que bajas**: la cabecera plegada miraba el desplazamiento en cada fotograma;
  ahora solo cuando cruza el umbral (como en Hiedra). Bajar por la portada es más suave y gasta menos.
- «4 borradores · 0 en revisión · 2 finales» (antes «4 borrador · 0 revisión · 2 final»).
- La versión de debajo de la portada es la de verdad (la 0.3.3 decía 0.3.1).
- Nada cambia en lo que se guarda en la cuenta: los mismos documentos y campos de siempre.

## 0.3.3

- **Una pantalla que no dejaba de redibujarse**: los títulos que se encogen para caber (FitTitle) apuntaban su ancho desde dentro de la
  medida; en una fila de dos casillas Compose mide dos veces (para saber el alto y para pintar) y, si los dos anchos diferían en un píxel
  por redondeo, se pisaban en cada fotograma: la pantalla se recomponía sin parar (CPU y batería) mientras estaba abierta. Pasaba en la
  tablet en la ficha de un juego «Jugando» de Azabache; la misma pieza está en las cuatro apps y en las de escritorio, y se arregla en todas.

## 0.3.2

- **Los capítulos terminados vuelven a llegar a Marfil y a Azabache, y las palabras del día también.** Al publicar un capítulo
  terminado se mandaba su hora de edición en el campo `u`, que en la cuenta es la hora del servidor y no se puede escribir: la
  publicación fallaba sin avisar en el primer capítulo terminado, y detrás de él se quedaban sin publicar las palabras de cada día.
  Ahora va en `ue` (ver `nube/ESQUEMA.md`), con una prueba contra la cuenta de verdad.
- El núcleo de los escritos (`Escritos.kt`) ya no tiene nada de Android: es el mismo archivo que compila **Cobalto para Linux y Mac**.

## 0.3.1

- **Las pestañas de arriba se dejan deslizar** (antes volvían solas a la elegida mientras las arrastrabas) y **las opciones de
  orden se ven todas** (si no caben en una línea, siguen en la siguiente; antes la última salía cortada y, al intentar deslizarla,
  pasabas de página).
- **Entrar una vez, de verdad.** Si entras en otra app de la familia mientras esta sigue abierta, esta entra sola en cuanto
  vuelves a ella (antes solo lo miraba al arrancar, y podía quedarse sin cuenta hasta que Android la cerrara).
- **Salir y volver a entrar ya no deja la app sorda.** Al salir de la cuenta se tiraban las colecciones y lo que las escuchaba se
  quedaba oyendo unas que ya no cambiaban: ahora son las mismas, vacías, y se siguen oyendo.

## 0.3.0 — la familia

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
- Lo que escribes sigue en Escribir como siempre (compartido con Atrio del PC). Lo que leen Azabache y Marfil (tus obras, los capítulos
  terminados y las palabras de cada día) va a la cuenta.

## 0.2.16

- **Fechas y cifras como en las otras tres** (`Formato.kt`, con las mismas pruebas en las cuatro): «16 oct 2026» en fichas y textos, «16/10/2026» en las tablas de datos y siempre con punto de millar («4.010»). Los meses van escritos en la app (Android, según la versión, les ponía punto: «oct.»).
- **«Seguir escribiendo» es un botón grande** en «Ahora», como «Seguir leyendo» en Marfil y «Jugar» en Hiedra (el sprint, al lado, en pequeño).
- En las listas (Escribiendo…), cada obra enseña **las palabras de hoy** en el acento («412 HOY») y, debajo, lo que lleva de su meta. Antes solo el «0 %».
- Los contadores de las pestañas van en tinta (más oscura la de la pestaña elegida), no en rojo: el rojo queda para lo que se toca.
- En las listas, la línea de debajo del título un punto más grande y más legible.
- **La cabecera de la portada se pliega al bajar**: arriba solo se quedan el nombre y «Buscar · + Nueva»; el número, la fecha, las barras y el menú se van con la página.
- Debajo de «Cobalto · hecho en casa», en gris, la versión instalada.

## 0.2.15

- **Cambiar qué obra sigue un widget sin quitarlo**: déjalo pulsado en el escritorio y sale «Reconfigurar» (Android 12 o más, en los
  lanzadores que lo ofrecen, como el de Pixel): abre la misma lista que al ponerlo (el widget de la obra). El menú del dedo pulsado es del escritorio,
  no de la app: lo único que una app puede hacer es decir que su widget se puede reconfigurar, y eso es lo que se ha hecho.

## 0.2.14

- Los «días seguidos escribiendo» siguen la misma regla en Hiedra, Marfil, Cobalto y Azabache (`Racha.kt`, con las mismas pruebas en las cuatro, y en el contrato): cuenta un día con al menos una palabra; si hoy aún no, desde ayer. Para ti no cambia nada (era la misma).

## 0.2.13

- En las filas de datos (etiqueta · puntos · cifra), si no cabe todo en una línea, la etiqueta va entera arriba y la cifra debajo, en vez
  de cortar la etiqueta con puntos suspensivos (como en Azabache 1.2.8).

## 0.2.12

- La fila de orden y filtros (Recientes · Nombre · …) lleva el mismo degradado en los bordes que las pestañas.
- El degradado de los bordes de las pestañas se pintaba fuera de la vista (no se veía): ahora sí.

## 0.2.11

- Las pestañas (secciones, Ajustes) se desplazan solas solo cuando la elegida no se ve entera, y entonces la centran: antes se
  pegaba al borde izquierdo y la de al lado salía cortada.
  Y en los bordes, un degradado cuando hay más pestañas por ese lado.

## 0.2.10

- Lo último que escribes antes de dejar Cobalto llega a tu cuenta (y a Marfil y Azabache) aunque Android congele la app nada más salir:
  al salir se programa un trabajo de fondo que sube lo pendiente y publica el capítulo que quedó esperando los 20 segundos de calma. Si la
  app ya lo había subido, el trabajo no hace nada.

## 0.2.9

- Textos al día: lo que seguía diciendo «Atrio» para hablar del móvil (seguir sus colores en directo, copiar su apariencia de tu cuenta,
  «los mismos que en Atrio») dice ahora Azabache, que es quien está en el móvil. La cuenta sigue siendo la cuenta de Atrio.

## 0.2.8

- **Arreglo**: en Android 13 o más, los avisos del actualizador («Cobalto tiene una versión nueva», «Cobalto se ha actualizado, toca para abrirla»)
  no se veían nunca: faltaba el permiso de avisos. Ahora se declara y se concede desde Ajustes → Avanzado («Avisos de actualización»).
- **Arreglo**: en Android 11 o más, «Leer en Marfil» no salía nunca y Ajustes decía «Marfil no está en este móvil» aunque estuviera (Cobalto no
  declaraba que puede ver a Marfil). Ahora el manifiesto declara a Hiedra y Marfil, y un test lo vigila.
- Los widgets (palabras de hoy, obra en curso, cita) se repintan en cuanto llega algo escrito en el PC o en otro móvil, no al cerrar la app.

## 0.2.5 a 0.2.7: lo terminado llega a Marfil y Azabache sin esperar

- Al dar un capítulo por terminado (o al escribir), la sincronización normal también publica los capítulos terminados y las palabras del día.
  Antes solo lo hacía el trabajo de fondo o «Sincronizar ahora», así que Marfil y Azabache podían tardar horas en verlo. Un capítulo terminado
  que sigues tocando se publica cuando lleva 20 segundos quieto (no a cada frase); las palabras del día, igual (0.2.7): así Marfil y Azabache
  no vuelven a bajar lo tuyo a cada frase que escribes.

## 0.2.4

- En el manuscrito, «+ Nuevo capítulo» y «+ Nueva parte» son enlaces de la revista (como «+ Añadir» en Hiedra y Marfil), no botones.

## 0.2.3: la portada como la de sus hermanas

- Arriba, «Buscar» y «+ Nueva» como en Hiedra y Marfil (antes solo «+ Nueva obra»); «Buscar» abre las secciones con el buscador abierto.
- Las barras de escritura son las de Marfil y Azabache: la semana, una barra por día con su inicial (en el acento, los días que cumpliste
  el objetivo), y la línea de arriba en una sola línea.
- Los textos de la guía y del árbol que decían «Atrio» dicen Cobalto; lo de la pareja, «en su Cobalto (o en Atrio)».

## 0.2.2

- «Leer en Marfil» abre Marfil **leyendo esa obra** (Marfil 0.12.3 o más nueva), no solo la lista de Escritos.

## 0.2.1: Azabache y Marfil se enteran al momento

- Al publicar un capítulo terminado o las palabras del día, Cobalto **sella también `stamps`** (antes solo lo sellaba la apariencia). Azabache y
  Marfil escuchan ese sello: el Diario y los Escritos se actualizan en cuanto sincronizas, no la próxima vez que se abran.

## 0.2.0: «Leer en Marfil» y Ajustes en pestañas

- **Leer en Marfil**: en el manuscrito de una obra (y en «Exportar»), si Marfil está en el móvil y la obra tiene capítulos terminados, un enlace
  abre Marfil en sus «Escritos», donde esa obra se lee con el lector (Marfil 0.12.0 o más nueva). No se exporta nada: los capítulos llegan por tu cuenta.
- **Ajustes** en pestañas, como Atrio: Apariencia · Escritura (papelera, y qué llega a Marfil y a Azabache) · Cuenta (Atrio y pareja) ·
  Avanzado (kDrive y actualizaciones, copias).

## 0.1.0: la primera

- **Portada de revista**, con las mismas medidas que las de Atrio, Marfil y Carmesí: la cabecera, el número del día y la fecha, las barras de
  **escritura** (las palabras de hoy, tu objetivo diario y las dos últimas semanas) y el menú.
- **Ahora**: la obra en la que estás (la de la última escena que tocaste), enmarcada, con sus palabras de hoy, lo que lleva de su meta, la escena
  por la que vas, «Seguir escribiendo →» (directo al editor) y «Sprint →». Debajo, **Sobre la mesa** (las demás obras en marcha) y **En el cajón**
  (las que están en pausa). Si tu pareja te comparte capítulos, salen aquí para leerlos y anotarlos.
- **Menú**: Escribiendo · En pausa · Terminadas · Archivo, como páginas por las que te mueves, con buscador, «Importar» (Word, Markdown o texto como
  obra nueva) y «+ Nueva». El estado de una obra se cambia en sus ajustes (dentro de la obra). Cobalto lo guarda en la obra como `est`
  (`pausa`/`fin`); `arch` (archivada) ya existía en Atrio.
- **El estudio** de cada obra es el de Atrio tal cual (los archivos `Escribir*.kt` se copian sin cambios; `Compat.kt` les da las piezas de Atrio
  con las de la revista): Manuscrito, Fichas, Árbol, Notas, Línea, Cifras y Guía; la página de cada parte y de cada capítulo; el editor con su
  formato, buscar y cambiar, fichas y notas al lado, borradores e historial.
- **Para Azabache** (ver `docs/CONTRATO-DATOS.md`): al marcar un capítulo (o su parte) como terminado se publica una copia de solo lectura de
  su texto en `/users/<uid>/cobalto/lectura/<obra>/<capítulo>`, y las palabras escritas cada día en este dispositivo en
  `/users/<uid>/cobalto/palabras/<dispositivo>`. Se publica tras cada sincronización.
- **Cuenta de Atrio** (la misma) y **pareja** (su usuario de Atrio, para compartir y leer lo suyo); **apariencia** como la de Atrio, Marfil y
  Carmesí (temas, fondos, acento, letras, colores de Atrio en directo o copiados de tu cuenta), y viaja entre tus Cobaltos.
- **Se actualiza sola** desde la carpeta «versiones» de tu kDrive (`Cobalto-<versión>.apk`), como las demás.
- **Widgets**: *Palabras de hoy* (las de hoy, tu racha y dos semanas en barras con tu objetivo), *Sigue escribiendo* (la obra en la que estás,
  con «Seguir» y «Sprint»; al ponerlo eliges si abre la que toque o una fija; tocando la esquina pasa a otra), *Una frase tuya* (un párrafo de tu
  manuscrito, uno cada día, que abre esa escena), *COBALTO* y *COBALTO en papel*.
- **Icono**: el átomo sobre azul cobalto, adaptable (`herramientas/icono-cobalto.py`).

## Capturas

Generadas en el ordenador con obras de ejemplo (`CapturasTest`, `-Proborazzi.test.record=true`): [portada](docs/capturas/1_portada.png) ·
[portada abajo](docs/capturas/1b_portada_abajo.png) · [en oscuro](docs/capturas/2_portada_oscuro.png) · [Escribiendo](docs/capturas/3_escribiendo.png) ·
[En pausa](docs/capturas/3b_en_pausa.png) · [Archivo](docs/capturas/3c_archivo.png) · [manuscrito](docs/capturas/4_obra_manuscrito.png) ·
[fichas](docs/capturas/4b_obra_fichas.png) · [notas](docs/capturas/4c_obra_notas.png) · [editor](docs/capturas/4d_editor.png) ·
[ajustes](docs/capturas/5_ajustes.png) · [ajustes: escritura](docs/capturas/5b_ajustes_escritura.png) · [ajustes: cuenta](docs/capturas/5c_ajustes_cuenta.png) · [ajustes: avanzado](docs/capturas/5d_ajustes_avanzado.png) · [vacía](docs/capturas/6_portada_vacia.png). Los widgets, en `docs/widgets/`.

## Cómo se trabaja

- Pruebas: `gradle :app:testDebugUnitTest` (0 fallos). `ContratoTest` deja en `app/build/outputs/contrato/cobalto.json` lo que Cobalto publica
  tal cual; se copia a `azabache/herramientas/casos/hermanas/` y la prueba `ContratoHermanasTest` de Azabache comprueba que lo lee entero. Con `-Proborazzi.test.record=true` se graban las capturas (`app/build/outputs/roborazzi/`)
  y los widgets (`app/build/outputs/widgets/`, de donde salen las vistas previas `w_prev_*.png`).
- Los casos compartidos con el PC (`herramientas/casos/*.json`: mezcla, estructura, tipografía, sinónimos) son copia de los de Atrio y los pasan
  `EscribirMezclaTest`, `EscribirEstructuraTest` y `EscribirCasosTest`.
- Versión nueva: subir `versionCode`/`versionName` (`app/build.gradle.kts`) y `VERSION` (`PantEstilo.kt`), `gradle :app:assembleRelease`, y
  `python3 herramientas/subir-version.py Cobalto-<versión>.apk` la deja en tu kDrive para que los móviles se actualicen solos.
- Para traer cambios de Escribir de Atrio: copiar `Escritos.kt` y `Escribir*.kt` de `Atrio-Suite-2.0/movil/...` cambiando el paquete, y volver
  a aplicar los pocos cambios de aquí (`Store`/`Account` → `Cuenta`, `CloudSave` → `NubeTarea`, el estado de la obra en `ObraDialogo`).
