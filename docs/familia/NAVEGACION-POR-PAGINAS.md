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
