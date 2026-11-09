# Navegación por páginas (hecho en Seshat 0.16.1, pendiente en las demás)

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
| Bastet | Portada · Jugando · Pendientes · Pasados · Abandonados · Sagas · Sistemas… |
| Thot | Portada · Escribiendo · En pausa · Terminadas · Archivo |
| Jonsu | Portada · las secciones de su diario |
| Hathor | Portada · Viendo · Pendientes · Vistas · Próximos… |
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
