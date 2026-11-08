# Ptah

Los widgets de la familia: un creador de widgets para la pantalla de inicio con la estética de revista de sus hermanas
([Jonsu](../azabache/HISTORIA.md), [Hathor](../mostaza/HISTORIA.md), [Nut](../cobre/HISTORIA.md), [Bastet](../hiedra/HISTORIA.md),
[Seshat](../marfil/HISTORIA.md) y [Thot](../cobalto/HISTORIA.md)): la misma interfaz «Revista», la misma cuenta y la misma firma; su paquete
es `com.gonzalo.ptah`. Solo Android (no tiene versión de escritorio).

Es de la familia y entra con tu cuenta (una vez en Jonsu o en cualquiera, y Ptah entra sola), pero va a su aire: **Jonsu no lee ni enseña
nada de Ptah**.

## 0.1.0

La primera.

- **Tus diseños.** Haces diseños de widget en un editor con la vista previa en vivo, al tamaño que quieras (1×1, 2×1, 2×2, 3×2, 4×1, 4×2,
  4×3, 4×4, 5×2 o libre, con dos barras), en claro y en oscuro. Cada diseño es un tipo y un estilo:
  - **el fondo**: papel (el de tu Apariencia), un color, un degradado (con su dirección) o transparente, y lo que se ve de él (opacidad);
  - **los colores**: la tinta, el acento, el gris y, por separado, cada pieza que lo tenga (las agujas, los números, las rayas de la esfera,
    el segundero, el día de hoy del calendario, la barra del buscador, la carga de la batería…). Cada color puede ser uno de tu Apariencia
    (papel, tinta, acento, gris: cambian con ella y con el claro y el oscuro), uno fijo o transparente;
  - **la forma**: el borde (sí o no, su grueso y su color), las esquinas (de rectas a redondas del todo) y el marco interior de revista;
  - **las letras**: la de los títulos y la del texto (las de la familia y las de Google Fonts, como en Apariencia), el tamaño del texto
    (60–160 %) y la alineación;
  - **las partes**: lo que se ve y lo que no (el antetítulo, la fecha, las rayas, los números…).
- **El selector de color**: los de tu Apariencia, transparente, el de serie, los que has usado hace poco, y uno a mano con el cuadro de
  saturación y brillo, la barra del tono, la opacidad y su código (#RRGGBB o #AARRGGBB).
- **Los tipos** (`Tipos.kt`: cada uno con su dibujo y su editor; uno nuevo es un `object` y su línea en `Tipos.TODOS`):
  - **Reloj digital**, con tres cabeceras: «Portada» (la hora enorme y la fecha en versalitas), «Columna» (antetítulo, raya gruesa, la hora,
    el día en cursiva y la fecha; con el Nº del día) y «Titular con fecha» («sábado 7 / de noviembre» y la hora en el acento). En 24 horas,
    con cero, en 12 o como el móvil. Un toque: tus alarmas.
  - **Reloj de agujas**: esfera de revista con romanos, números, solo 12-3-6-9 o solo rayas; agujas de bastón, de hoja o finas; segundero;
    el nombre de la esfera; y, si hay sitio, el día al lado (ancho) o debajo (alto).
  - **Calendario del mes**, con hoy en el acento (la semana desde el lunes o el domingo); en pequeño, el día enorme.
  - **Buscador**: una barra de revista que abre el buscador de serie (ACTION_WEB_SEARCH) o el navegador que elijas, con su micrófono.
  - **Accesos directos**: las apps que elijas de las que tienes, o webs, con su icono, su nombre en versalitas o las dos cosas; o solo los
    nombres, como el índice de una revista.
  - **El tiempo** (Open-Meteo): ahora, cómo está el cielo, la máxima y la mínima y los próximos días, con iconos de línea propios para los
    códigos WMO; la ciudad se busca con su geocoding, o «donde estés» (la ubicación aproximada, con tu permiso; no sale del móvil).
  - **La batería**: el porcentaje, la pila y si está cargando.
  - **Nota**, **cuenta atrás** (los días que faltan o que han pasado, a una fecha; de serie, la próxima Navidad) y **foto** (elegida con el
    selector de fotos del sistema, copiada a la memoria de Ptah, con marco de revista y pie de foto, o a sangre).
- **Plantillas para empezar**: catorce diseños hechos (en «Para empezar», en la portada); un toque y es tuyo.
- **El widget**: uno solo para todos (`WPtah`), redimensionable a cualquier tamaño (desde 40 × 40 dp), de serie 2×2. Al ponerlo se elige el
  diseño (`ConfigWidgetActivity`: de los tuyos, de las plantillas o uno nuevo); el editor también tiene «Ponerlo en la pantalla de inicio».
  Un widget cuyo diseño se borra lo dice y deja elegir otro.
- **Ajustes**: Apariencia (como las hermanas, con el acento de Ptah, cobre anaranjado), Cuenta y Avanzado (actualizar sola desde tu kDrive,
  volver a pintar los widgets, lo que ocupan las fotos).

### Cómo se mueve un reloj en la pantalla de inicio

(Así desde la primera prueba en un móvil de verdad: ver «Lo que salió en el OPPO», abajo.)

Cada widget se dibuja entero (como los de las hermanas) para su tamaño de verdad, con el mismo dibujo que la vista previa del editor
(`Lienzo.kt`, `Pintor`). **Los relojes también: la hora, la fecha y las agujas van dibujadas con la letra de tu diseño**, y Ptah vuelve a
pintar los widgets de reloj (solo esos) al empezar cada minuto (`Minutero.kt`):

- **Una alarma exacta por minuto, que no despierta al móvil** (`setExact` del tipo `RTC`). Con la pantalla apagada no salta, así que no
  gasta nada; al encenderla, la que estaba pendiente salta enseguida y el reloj se pone en hora. También se repinta al cambiar la hora, la
  zona o el idioma, al encender el móvil y cuando cambia el permiso de las alarmas.
- **El permiso**: `USE_EXACT_ALARM` (Android 13 o más). Se concede al instalar, sin preguntar ni pantallas; Google Play solo lo deja a las
  apps de reloj y alarmas, pero Ptah se instala desde tu kDrive. En Android 12, `SCHEDULE_EXACT_ALARM` (concedido de serie; se puede quitar
  en Ajustes del móvil → «Alarmas y recordatorios»). En Android 10 y 11 no hace falta ninguno.
- **Si Android no deja alarmas exactas**, o eliges en Ajustes → Avanzado → «La hora de tus relojes» «con la letra del móvil», la hora va
  como `TextClock` y las agujas como `AnalogClock`: siempre en hora y sin gastar, pero con la letra del sistema. Ajustes lo dice y lleva al
  ajuste del permiso.
- **Sin segundero en la pantalla de inicio** (dibujado una vez por minuto se quedaría quieto): solo en la vista previa, y con «la letra
  del móvil» (`AnalogClock`, Android 12 o más).
- **Lo que cuesta**: un dibujo y un envío al lanzador por minuto y por reloj, con la pantalla encendida. Medido en el ordenador
  (`WidgetsTest.loQueCuestaUnMinuto`): unos 6 ms por dibujo de un reloj 4×4, y el dibujo pesa unos 3,7 MB a la densidad de un móvil
  (2,75 px/dp; Android limita lo que pesa un widget a la pantalla × 1,5). En un móvil será algo más lento que en el ordenador, pero son
  milisegundos por minuto: 60 dibujos por hora de pantalla encendida y por reloj.
- En algunos móviles (OPPO, Realme, Xiaomi) el ahorro de batería no deja a las apps despertarse: si un reloj se queda atrás, Ajustes dice
  cuándo se puso en hora por última vez y lleva a los ajustes de Ptah para permitirle la actividad en segundo plano.
- El resto se vuelve a pintar al cambiar el diseño o la apariencia (al momento), a medianoche, cada 15 minutos con un widget de batería o
  cada 30 con uno del tiempo (WorkManager), y al abrir Ptah.

### Dónde se guarda cada cosa

- Tus diseños: en tu cuenta, `ajustes/ptah_disenos`, un campo por diseño (su id) con el diseño en JSON; gana el último cambio de cada
  uno, y borrar uno es dejar su campo vacío. Nada secreto.
- La apariencia: `ajustes/ptah`. Los colores de hace poco: `ajustes/ptah_general` (`recientes`). «Actualizar sola»: `ajustes/ptah_actualizador`.
- Solo en el móvil: las fotos (`fotos/<id>.jpg`; en tus otros móviles el diseño sale con «Elige la foto»), qué diseño lleva cada widget
  de la pantalla de inicio, la ubicación aproximada y el tiempo bajado (caché).
- Nada de Ptah va a `/claves`.

### El tiempo: Open-Meteo

Gratis y sin clave para un uso no comercial (menos de 10.000 llamadas al día; Ptah pide cada 30–60 minutos y nunca más de una vez cada
30 minutos por sitio), con los datos bajo la licencia CC-BY 4.0: por eso «Datos: Open-Meteo» sale en la portada, en Ajustes, en el editor
y, pequeño, en el propio widget.

### Lo que no tiene todavía

- **Los pasos** (Health Connect): fuera de esta versión. Pide otra biblioteca, sus permisos y su pantalla de permisos, y Health Connect
  no se puede probar aquí; mejor en una versión propia.
- La batería no se pinta al momento al enchufar el móvil (Android no deja a un widget enterarse en segundo plano): cada 15 minutos y al
  abrir Ptah.
- «Donde estés» mira la ubicación al abrir Ptah (Android no la da en segundo plano sin un permiso más).
- Sin probar en un móvil de verdad: los widgets en un lanzador de verdad, el `AnalogClock` con las agujas del diseño y el `TextClock` con
  la letra de la familia (comprobados inflándolos en Robolectric, con Android 14), «Ponerlo en la pantalla de inicio», la ubicación.
- El tiempo de las pruebas tiene la forma de una respuesta de verdad de Open-Meteo, pero hecha a mano: el día de hacerlas, la API
  contestaba «Daily API request limit exceeded» (también guardada, de verdad: `error_limite.json`). El geocoding sí es de verdad.

## Lo que salió en el OPPO (antes de la próxima versión)

Probada en un OPPO (ColorOS, Android 14/15):

- **El reloj «Columna» salía tres veces, una encima de otra, y con Roboto.** Dos fallos:
  - *Tres veces*: muchos lanzadores no vuelven a inflar un widget al repintarlo, sino que aplican lo nuevo encima de lo que ya tenían
    (`RemoteViews.reapply`); cada `addView` de la hora se sumaba a las de antes (una por cada tamaño al que se había pintado al ponerlo y
    redimensionarlo). Ahora cada envío vacía antes los huecos (`removeAllViews`) y quita los dibujos de antes. Prueba: `WidgetsTest`
    aplica lo que se manda una y otra vez sobre la misma vista, con tamaños distintos, y cuenta que hay un solo dibujo y una sola hora.
  - *Roboto*: ColorOS no respeta `android:fontFamily` en un `TextClock` de un widget (en Robolectric sí salía bien: esa prueba engañaba). Por
    eso la hora pasa a ir dibujada y repintada cada minuto (arriba).
- **«Muy poco intuitivo»: ahora la portada tiene dos zonas**: «Tus diseños» (los tuyos, y debajo de cada uno «En tu pantalla de inicio: 2»
  o «Aún no está en tu pantalla») y, aparte, en su banda de color, «Plantillas para empezar» (un toque hace una copia tuya y abre el
  editor). «+ Crear uno nuevo», grande. La primera vez, «Cómo va» en tres pasos (se cierra y no vuelve).
- **Al guardar** («Guardar diseño»): vuelta a la portada con «✓ Guardado en Tus diseños», el diseño resaltado y el botón grande «Ponerlo en
  la pantalla de inicio», que le pide al lanzador un widget nuevo que ya lleva ese diseño (sin pasar por elegirlo). Si el lanzador no lo
  deja, «Cómo ponerlo a mano» lo explica en cuatro pasos. Salir del editor sin guardar lo pregunta.
- **Cada diseño tiene su ficha**: cómo se ve, en cuántos widgets está, «Ponerlo en la pantalla de inicio», «Editar», «Hacer una copia» y
  «Borrar». Cambiar un diseño repinta al momento los widgets que lo llevan.
- Al añadir el widget desde el lanzador: «¿Qué va en este widget?», tus diseños arriba, las plantillas debajo y «+ Crear uno nuevo»
  («Guardar y ponerlo aquí»).
- Los botones grandes ya no se cortan con la letra grande (van en dos líneas).

Sin probar todavía en el móvil: que el minutero mantenga la hora en ColorOS con la pantalla encendida un buen rato y tras apagarla y
encenderla, cuánto gasta allí de verdad, y que su lanzador acepte «Ponerlo en la pantalla de inicio» (`requestPinAppWidget`).

## Capturas

Hechas en el ordenador con datos de mentira (`CapturasTest`; también la portada con pocos diseños y las plantillas, tras guardar y la ficha de un diseño, solo con `-Proborazzi.test.record=true`; las de los widgets, `WidgetsTest`),
en claro, en oscuro y con la letra al 130 %: la portada, «+ Nuevo», el editor de cada tipo y sus pestañas, el selector de color, los ajustes
y elegir el diseño de un widget. En `capturas/` van unas cuantas; en `widgets/`, las plantillas a su tamaño (y alguna en otros).
