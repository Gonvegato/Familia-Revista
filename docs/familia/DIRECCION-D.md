# Dirección D: «la mesa y la estantería»

La cara de las seis apps de la familia (Jonsu, Bastet, Seshat, Thot, Hathor y Nut) desde noviembre de 2026. Sustituye a la dirección C
(la «revista»: versalitas, bloques de tinta, bordes de 2 px, pestañas arriba). Maqueta aprobada: `docs/familia/maqueta-direccion-d.html`.
**La app de referencia es Seshat (`marfil/`)**: lo que aquí se describe está hecho allí; las demás lo copian adaptándolo a lo suyo.

## Qué es

- **Barra abajo** (`com.gonzalo.mesa.Andamio`): Hoy · la estantería (Biblioteca, Obras, Directo…) · el botón del medio (añadir, explorar o
  buscar, relleno del acento) · Tú. En pantallas anchas y apaisadas (tablet, tele) la barra va a la izquierda sola. Sin pager ni
  pestañas arriba; Atrás desde otra pestaña vuelve a Hoy.
- **Hoy** (la mesa): `CabeceraM` (nombre + punto del acento, la fecha a la derecha), **una** `TarjetaAhora` con lo que tienes a medias y
  un solo botón, `Semana` (tus siete últimos días), y filas de portadas que se deslizan («También…», «Lo siguiente»). Vacía: `VacioM`.
- **La estantería**: `Buscador` arriba, `Filtros` (los estados y lo que antes eran páginas: sagas, sistemas, autores…) y una rejilla de
  portadas (`LazyVerticalGrid(GridCells.Adaptive(96.dp))` con `PortadaConAvance`). El orden, en un enlace «Orden: …» que despliega sus
  opciones. Lo que no es una rejilla de cosas (sagas, autores) reutiliza la pantalla que ya había, dentro del filtro.
- **Tú**: el reto, las cifras (`RejillaCifras`), lo del año y la entrada a Ajustes; abajo, la versión.
- **La ficha**: `Lavado` (el color de la portada fundido al fondo) con volver y las acciones en iconos (`IconoM`), la portada, el título
  centrado y el estado en el acento; debajo `BotonM`, `AvanceM`, `Estados`, `BotonTextoM`, `Rotulo` por sección y `Dato` por línea.
- **Aspecto**: texto en minúsculas normales (nada de `.uppercase()`, `FontWeight.Black` ni `letterSpacing`), sin cursivas serif, tarjetas
  redondas en superficie sin borde, líneas de 1 px en `Line`, botones redondos. Títulos en Bricolage (`Grotesca`), texto en `Body`.
- **Apariencia** (Ajustes): solo tema (como el móvil / claro / oscuro), tamaño del texto y color de acento (el de la app, los de la
  lista u «otro color»). Fuera: paletas, estilos, formas, letras, fondos de color, «a tu manera» (fondo y texto), los colores de Jonsu
  en directo y «copiar de Jonsu» (`CopiaAtrio.kt` y sus pruebas se borran). `Skin.soloMesa()` deja en eso lo que venga de antes o de
  un dispositivo sin actualizar. La imagen de fondo de cada cosa y el encuadre de portadas siguen.

## Los colores de cada app (paleta «mesa», acento claro / oscuro)

Fondo `F3F4F1` / `111417`, superficie `FFFFFF` / `1B1F23`, tinta `16191C` / `ECEEE9`, chip (`sand`) `E8EAE6` / `252A2F`, línea `DDE0DC` /
`2C3238`, borde `C4C8C3` / `444B52`, apagado `666D73` / `9AA2A8`. Acentos: Jonsu `8F3232` / `D46A6A` · Bastet `2E6B4E` / `5DB58A` ·
Seshat `5A4A8C` / `A493E0` · Thot `2A4FA0` / `7FA2F0` · Hathor `9A7400` / `E0B63A` · Nut `B0532A` / `E58A5C`.

## La receta (lo hecho en Seshat, en orden)

1. `build.gradle.kts`: `implementation(project(":mesa"))`.
2. `Tema.kt`: la paleta `mesa` la primera de `PALETTES` (mira la de `marfil/…/Tema.kt` y cambia el acento y su variante).
3. `TemaMotor.kt`: la forma `mesa`; los valores de serie de `Skin` (paleta mesa, display «Vitrina», forma mesa, `origenAtrio` false);
   en `cargar`, la migración `mesa_v1` (el acento vuelve al de la app) y `soloMesa()`; `soloMesa()` también en `aplicarAjustes`;
   `tintaMesa()` al final; y en el tema raíz, `ConTinta(tintaMesa(), content)` envolviendo el contenido.
4. Las piezas básicas con cara nueva y las mismas firmas: `python3 herramientas/mesa/piezas.py <carpeta de fuentes>` (cambia
   `Revista.kt` y `UiBase.kt`; avisa de las que no encuentra: esas, a mano mirando las de Seshat). Luego
   `python3 herramientas/mesa/minusculas.py <carpeta de fuentes>` (quita versalitas, negras, espaciados y cursivas de todas las
   pantallas). Después, a mano: `Filete` (1 px, `Line`), y lo que quede en mayúsculas dentro de cadenas
   (`grep -n '"[A-ZÁÉÍÓÚÑ ]\{6,\}' *.kt`).
5. Las pantallas: un archivo `PantMesa.kt` con la barra, Hoy, la estantería y Tú (copia el de Seshat y adáptalo); `Raiz.kt` con el
   estado nuevo (pestaña, filtro, ajustes, ficha) y los `BackHandler`; lo pedido desde fuera (`go=…`, widgets, otras apps) sigue
   funcionando con los nombres de siempre. Se borra el pager, la cabecera plegable y la portada de la dirección C.
6. La ficha (`PantFicha.kt`): el bloque de pantalla rehecho como en Seshat; los diálogos se quedan (ya salen redondos).
7. Ajustes: la cabecera nueva (`IconoM` volver + `CabeceraM("Ajustes")`), `SeccionApariencia` como la de Seshat.
8. Lo que cada app hace por dentro (lector, emulador, reproductor, editor, guía) **no cambia de funcionamiento**: solo hereda la
   cara nueva por las piezas básicas y por `minusculas.py`. Si algo se ve roto (un bloque de tinta, un borde grueso), se arregla.
9. Pruebas: las que comprobaban la interfaz de antes se adaptan (no se borran salvo que prueben algo que ya no existe, como
   `CopiaAtrio`); `CapturasTest` se rehace como el de Seshat: un recorrido (Hoy, estantería y un par de filtros, ficha, Tú, Ajustes)
   en claro, de noche, con fondo, con letra grande (`qualifiers = "w360dp-h740dp-xhdpi", fontScale = 1.3f`) y tablet apaisada.
   **Nada de texto cortado**: se miran las capturas de letra grande una a una.
10. Widgets: funcionan igual; sus colores salen de `Skin.tonosPara`, así que cambian solos. Si alguno pinta bordes o versalitas a
    mano, se pone al día.

## Reglas

- No se toca la cuenta, la sincronización, los paquetes ni nada que guarde datos. Lo nuevo que se guarde es de cada dispositivo
  (como la cola «Lo siguiente» de Seshat, en `SharedPreferences`).
- `mesa/` es común: si le falta algo, se le añade un parámetro con valor de serie (sin romper a las demás apps); no se copian sus
  piezas a una app.
- Accesibilidad: cada portada lleva su título (`PortadaConAvance(descripcion = …)`), cada icono su descripción.
