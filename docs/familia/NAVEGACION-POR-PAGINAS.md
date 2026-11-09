# Navegación por páginas (hecho en Seshat 0.16.1 y Bastet 0.8.0; pendiente en las demás)

Lo que pidió Gonzalo: la pantalla de entrada es la **portada**, y las secciones (leyendo, pendientes…) no abren submenús ni otra pantalla:
son **páginas a la derecha** de la portada, como pasar hojas.

## El patrón (ver `marfil/.../Secciones.kt`, `PantPrincipal`)

1. **Una lista de páginas** en el orden de las pestañas, con la portada la primera (`PAGINAS = listOf(Destino.Portada, …)`). El índice de
   cada página es el de su `Destino`, así que el estado de la app sigue siendo un entero (`destino`, `rememberSaveable`).
2. **Una sola pantalla** (`PantPrincipal`) con:
   - la **cabecera fija** de la dirección C (fila pequeña BUSCAR · + AÑADIR · fecha · ⚙ y el nombre grande con su punto); el nombre se
     recoge al bajar con un `NestedScrollConnection` (lo que la página aún no ha movido lo consume la cabecera) y vuelve al subir;
   - **una sola fila de pestañas** (`PestanasPaginas`): la elegida en bloque de acento, contadores donde los hay, se desliza sola para que la
     elegida se vea entera, degradado en los bordes si hay más;
   - un **`HorizontalPager`** con una página por pestaña (tocar la pestaña → `animateScrollToPage`; deslizar → el pager cambia solo).
3. **Sincronía con fuera**: `LaunchedEffect(pagina) { animateScrollToPage(pagina) }` para lo que pide la app (Atrás, widgets, avisos, otras
   apps con `go=`), y `LaunchedEffect(pager.settledPage) { alPagina(it) }` para contar fuera lo que mueve el dedo.
4. **Atrás**: si no estás en la portada, a la portada (`BackHandler(destino != 0) { destino = 0 }`); desde la portada, sale.
5. **Cada página sin cabecera propia**: la portada es solo su contenido (`PaginaPortada`); cada sección empieza con su nombre en pequeño
   (antetítulo en acento). Las pantallas de sección que tenían su propia cabecera/pestañas se quitan (o tienen un modo `enPagina`).
6. **Fichas por encima** (libro, saga…): se pintan encima del `PantPrincipal`, que sigue compuesto debajo, así que al cerrar se vuelve a la
   misma página y con el mismo scroll (los `rememberSaveable` de cada página los guarda el pager).
7. **Ajustes** sigue siendo una pantalla aparte (no es una sección).
8. Sin páginas vecinas precompuestas (`beyondViewportPageCount` por defecto): con ellas, los toques de la cabecera fallaban en Seshat.

## Para cada app

| App | Páginas |
|---|---|
| Seshat | Portada · Leyendo · Pendientes · Leídos · Abandonados · Autores · Sagas · Escritos (hecho) |
| Bastet | Portada · Jugando · Pendientes · Pasados · Abandonados · Sagas · Sistemas (hecho; Tu PC y Ajustes, encima) |
| Thot | Portada · Escribiendo · En pausa · Terminadas · Archivo |
| Jonsu | Portada · las secciones de su diario |
| Hathor | Portada · Viendo · Pendientes · Vistas · Abandonadas · Próximos (hecho, 0.7.0; Explorar, Buscar, Addons y Ajustes, encima) |
| Nut | Hoy · Competiciones · Tus equipos · IPTV… |

Test de referencia: `marfil/src/test/.../SeccionesTest.kt` (`portadaYSeccionesSonPaginas`).

## Criterios de la dirección C aprendidos en Seshat 0.17.0 (aplicar igual en las demás)

- **Cifras y titulares en grotesca, sin espaciado negativo fuerte.** Las cifras van con `letterSpacing = 0` (nunca −5 sp: los dígitos se
  tocaban, «15» pegado). Los nombres con punto («SESHAT.») a −0,015 em como mucho.
- **La grotesca con su tamaño óptico.** Bricolage es variable: fijar `opsz 48` y ancho 100 en todos los pesos (`FontVariation`). Sin eso se
  dibuja con el de cartel (96), con remates de distinta altura que se ven irregulares en mayúsculas.
- **La cabecera se pliega sin cortar.** Al recogerse, el nombre grande encoge (escala desde arriba a la izquierda) y se desvanece con su
  hueco; nunca un `clip` que lo corte a media letra. Plegada del todo, la fila queda compacta: el nombre pequeño a la izquierda, Buscar /
  Añadir y ⚙ (sin la fecha), y ningún hueco negro debajo.
- **Pestañas de la misma altura.** La fila con `height(IntrinsicSize.Max)` y cada pestaña `fillMaxHeight()` centrada: si una lleva número
  y otra no, la activa (en bloque de acento) llega igual hasta arriba, sin rendija.
- **Portada en «estantes de portadas»** (la elegida): Leyendo (portadas grandes con avance) → botón «SEGUIR CON …» → Pendientes (medianas,
  el siguiente de cada saga) → «Tu año · X de Y» (barra del reto + mosaico de lo leído, 6 por fila, hasta 3 filas y «+N») → en pequeño hoy,
  racha y semana. Cada título lleva a su página.
- **Órdenes solo los que tienen sentido en cada página**, con nombre que diga qué ordenan («Última lectura», «Más avanzado», «Último
  añadido», «Fecha de fin», «Título A-Z», «Autor A-Z», «Más páginas»); nada repetido (en Leídos, «Recientes» era la fecha de fin otra vez).

## Portadas grandes y protagonistas (Seshat 0.18.0; aplicar igual en las demás: juegos, series, obras…)

- **Portada «cartel»**: lo actual (el libro que lees, el juego que juegas, la serie que ves…) de protagonista arriba, en un bloque de ~470 dp
  sobre el color medio de su imagen, con la portada a ~220×330 dp centrada y con sombra; encima, abajo, sobre un degradado oscuro: «AHORA …
  · N %», el título en grotesca mayúsculas, la barra gruesa y una línea de datos; pegado debajo, el botón de la acción en el acento a todo el
  ancho. La portada termina donde empieza el texto (que lo pintado en la imagen no choque con el del cartel). Sin nada en curso: cartel negro
  con un mensaje claro y el botón para añadir. Debajo, los demás en filas de portadas grandes (120×180, 104×156) y el mosaico del año a 4.
- **Listas**: portada de 84×126 dp en cada fila; el número (01…) pequeño en el acento encima del título; título en grotesca; autor/creador en
  versalitas; abajo, cómo va según el estado (%, barra y unidades; fecha de fin si está terminado).
- **Ficha «doble página»**: la imagen ampliada como foto de apertura a todo el ancho (~300 dp, recortada), con las acciones encima sobre un velo;
  una etiqueta en el acento pisando el borde con el estado; el título enorme; la línea en cursiva; el botón negro ancho de la acción principal;
  y las secciones numeradas en el acento (01 Tu …, 02 La saga, …) con todo lo que la ficha ya tenía.

## Portada + fondo elegibles (Seshat 0.19.0; aplicar igual en las demás)

- Cada cosa (libro, juego, serie, obra…) tiene **portada** (2:3) y **fondo** (a lo ancho), las dos elegibles en una hoja «IMÁGENES.» con
  pestañas LA PORTADA / EL FONDO: buscar en internet, de tu móvil, quitar (y propuestas cuando la fuente las dé; el fondo puede «usar la portada»).
- El fondo va a la cuenta como la portada (hash en `fondo`, origen en `fu`; quitado = `"-"`), y se lee el `az.hero` de Jonsu si no hay.
- Con fondo: el cartel de la portada lleva el fondo detrás y la portada encima a la izquierda; la cabecera de la ficha lleva el fondo de
  apertura y la portada asomando abajo a la derecha (en una caja que no estira el hueco). Sin fondo, la portada sobre su color, como antes.

## Encuadre, paspartú y velo (Seshat 0.20.0; aplicar igual en las demás)

- Encuadre — patrón: `marfil/.../Encuadre.kt`. La imagen se guarda entera; aparte, el encuadre = punto de la imagen en el centro del hueco
  (`cx`, `cy`, fracciones) + `zoom` (1 = cubre el hueco). `colocarEncuadre` lo aplica a cualquier hueco y ajusta para que nunca quede borde
  vacío; `recortarEncuadre` hace lo mismo con bitmaps (widgets).
- El encuadre es POR DISPOSITIVO (`Encuadres`, SharedPreferences): clave libro|portada-o-fondo|nombre del archivo de la imagen (su hash). La
  imagen viaja con la cuenta; si cambia, el encuadre viejo no casa y vuelve a verse centrada en todos.
- El editor (`EditorEncuadre`) enseña la ventana con la forma real en que se verá: la portada a 2:3 y el fondo con la forma del sitio más
  grande donde sale (el cartel); los huecos de otra forma (la cabecera de la ficha) se centran en el mismo punto. Dice «solo para este
  dispositivo».
- Paspartú: la portada sobre una imagen o un color va en `PortadaSobreFondo`: marco liso del color de la página (token `Paper`), ~5,4 % del
  ancho (4–14 dp); si la portada es casi blanca (luminancia > 0,85), en la tinta (`Charcoal`). Sombra por fuera.
- Velo: sobre una foto con botones encima, `veloArriba()` (negro 80 % → transparente en 110 dp) y los botones en crema.

## Lo aprendido al pasarlo a Bastet (0.8.0; para Thot, Jonsu, Hathor y Nut)

- **Copiar lo común de Seshat tal cual** (`TemaMotor`, `Revista.kt`, `UiBase.kt`, `Encuadre.kt`, la semana y el reto) con las sustituciones de
  nombre, y cambiar `.clickable` por lo que la app use para el mando (`tocable` en Bastet: enfoca, marca y responde a A/Enter).
- **Lo que solo existe en una app se queda, con la estética nueva**: en Bastet, los menús pequeños PC · Emulación · Android · Web (en bloques),
  el PC en directo (va al cartel), la tarjeta del PC, «Editar portada» (destacado elegido, estantes y «Mis elegidos»), el mando (A juega, X
  ficha) y el jugador interno. Si una sección era una pantalla con cabecera propia (Sistemas), se le da un modo `enPagina`.
- **Lo que no es página va encima** (Tu PC, Ajustes), con su propio Atrás; `go=<sección>` en el intent lleva a la página o a lo de encima.
- **El estado de fuera de la app no se pierde**: un `Destino` sigue sirviendo para pedir páginas (`paginaDe(d)`; -1 si va encima).
- **Acento legible** (`acentoLegible`): el elegido, mezclado con la tinta lo justo para leerse sobre la página con 4,5:1. Es el que llevan
  letras, cifras, la pestaña activa y el botón principal; el de serie (`AcentoBloque`) queda para los bloques de color y el icono. Sobre la
  cabecera (tinta) y sobre lo oscuro del cartel se calcula aparte, contra ese fondo. Con un acento que ya se lee, no cambia nada.
- **Los nombres de clases chocan**: antes de crear una clase (p. ej. `DatosJuego` para la semana) mirar que no exista ya en la app.
- **El encuadre de antes (en la cuenta, igual en todos)** pasa una vez a ser el de este dispositivo (`Encuadres.migrarDeAntes`); la cuenta
  no se toca, así que una versión vieja en otro móvil sigue viendo el suyo.
- **Órdenes**: solo los que dicen algo en cada sección; un orden guardado que ya no vale allí vuelve al de serie sin romper nada.
- Las capturas de cada app nueva, en una prueba aparte con sus colores «A tu manera» (normal, noche y letra 1,3) y leídas todas antes de dar
  nada por bueno.

## Paspartú: crema fijo en todas las apps (Seshat 0.20.3, Bastet 0.8.1)

El marco de la portada que va sobre un fondo (cartel de la portada y apertura de la ficha) es SIEMPRE un crema fijo #F4EBD6, sin depender
del tema, de la noche ni de «A tu manera» (en las apps de página oscura, el color de la página no se veía). Solo si la portada es muy
clara — luminancia media de la portada encuadrada ≥ 0,78 (`UMBRAL_PASPARTU`; con el crema ≈0,93, una diferencia < 0,15) — el marco
pasa a un oscuro fijo #141210. Funciones: `colorPaspartu(lPortada)`, `paspartuOscuro`, `PASPARTU_CREMA`, `PASPARTU_OSCURO`.

## Lo aprendido al pasarlo a Hathor (0.7.0; para Thot, Jonsu y Nut)

- **Los colores de serie como paleta, no como «A tu manera»**: Hathor trae «Palomitas» (#F4ECDD · #1B1A1F · #D7263D) como paleta propia
  (`PALETTES`, `palomitas`, con su versión de noche), predeterminada; los colores de antes puestos a mano (#715021 · #FFFFFF · #D4C1C0) y la
  paleta que venía de serie pasan a ella UNA vez (`Skin.aPalomitas`, también al llegar la apariencia de un Hathor viejo). Lo demás elegido a
  mano se queda.
- **El rojo sobre crema da 4,2:1**: el acento de serie no llega a AA para letra pequeña; `acentoLegible` lo oscurece lo justo para el texto y
  los bloques (pestaña activa, botón, etiqueta del estado) llevan el rojo tal cual con letra blanca (5:1).
- **Un fondo automático**: si la app ya tiene una imagen ancha de fuera (el «backdrop» de TMDB o del addon), es el fondo mientras no elijas
  otro; el elegido va aparte en la cuenta (`fimg` + `fu`, "-" = quitado a propósito, para que no vuelva el automático). «El de TMDB»
  devuelve el automático. Las propuestas de «Imágenes» salen de la misma fuente (`/images` de TMDB).
- **El botón del cartel hace lo de siempre**: «SEGUIR VIENDO →» abre la ficha y pulsa su botón principal (la mejor fuente, el episodio que
  toca); así no hay dos caminos para reanudar.
- **Una página que no es un estado** (Próximos) va en el pager igual que las demás, con su rejilla de cifras y su lista.
