# Cobalto

La app de escritura del móvil. Hermana de [Atrio](https://github.com/gvegagato-prog/Atrio-Suite-2.0), [Marfil](https://github.com/Gonvegato/lectura-app)
y [Carmesí](https://github.com/gvegagato-prog/Android-Launcher): mismo sistema de temas (las mismas paletas, letras y «estilos»), otro paquete
(`com.gonzalo.cobalto`), así que **conviven en el mismo móvil** y se ven como programas de la misma suite.

Cobalto es **Escribir de Atrio, entero y con casa propia**: tus obras, y dentro de cada una el manuscrito (partes, capítulos y escenas), las
fichas, el árbol, las notas, la línea temporal, las cifras y la guía de la novela; sprints, sinónimos, papelera, borradores e historial de
versiones, comparar y mezclar, exportar (texto, Markdown, Word, PDF) e importar, y lo de la pareja (compartirle capítulos y que te los anote).
Sin migración: usa los mismos documentos que Atrio y Atrio del PC (`/users/<uid>/escritos`), con el formato de `docs/escribir.md` de Atrio.
Lo que escribas en el PC aparece aquí al momento, y al revés.

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
