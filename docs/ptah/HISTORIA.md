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

Cada widget se dibuja entero (como los de las hermanas) para su tamaño de verdad y en claro y en oscuro, con el mismo dibujo que la vista
previa del editor (`Lienzo.kt`, `Pintor`). Pero un dibujo no se vuelve a pintar cada minuto de forma fiable (Android no despierta la app
para eso), así que **lo que se mueve no va en el dibujo**: va encima, y lo pone al día el propio lanzador.

- **La hora y la fecha** son un `TextClock` (con su formato y su color, y el tamaño calculado para que quepa lo más ancho que puede salir:
  «23:59», «miércoles 30 de septiembre»). Su letra va en el diseño XML (`android:fontFamily="@font/…"`, `w_vivo_*.xml`): Android solo deja
  poner en un widget las letras que vienen dentro de la app, no las de Google Fonts; si tus títulos son de Google Fonts, la hora va con la
  de la familia que más se le parece (Atrio de títulos, en cursiva, de texto, en negrita o Vitrina; se puede elegir). Comprobado inflando
  en las pruebas lo que se manda al lanzador: la letra es la del recurso y el tamaño el calculado. Los nombres de los días y los meses los
  pone Android en el idioma del móvil.
- **Las agujas** son un `AnalogClock`. En Android 12 o más llevan las del diseño: cada aguja es una máscara (`setIcon`) del tamaño de una
  esfera vacía, con su color (`setColorStateList`), y la esfera de verdad (con sus números y sus rayas) va en el dibujo de debajo. En
  Android 10 y 11, que no dejan cambiarlas, unas de bastón oscuras o claras (según el color de la minutera) y sin segundero.
- Lo demás se vuelve a pintar al cambiar el diseño o la apariencia, **a medianoche** (el mes, la cuenta atrás, la fecha dibujada: una
  alarma inexacta, sin el permiso de alarmas exactas), al cambiar la hora o el idioma, **cada 15 minutos** con un widget de batería o
  **cada 30** con uno del tiempo (WorkManager), y al abrir Ptah.

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

## Capturas

Hechas en el ordenador con datos de mentira (`CapturasTest`, solo con `-Proborazzi.test.record=true`; las de los widgets, `WidgetsTest`),
en claro, en oscuro y con la letra al 130 %: la portada, «+ Nuevo», el editor de cada tipo y sus pestañas, el selector de color, los ajustes
y elegir el diseño de un widget. En `capturas/` van unas cuantas; en `widgets/`, las plantillas a su tamaño (y alguna en otros).
