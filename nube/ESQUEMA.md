# La cuenta de la familia

Una sola cuenta de Firebase (Realtime Database y Authentication con usuario y contraseña), el mismo proyecto de siempre. Todo
lo de la familia cuelga de `/users/<uid>/` y de `/claves/<uid>`. Este documento es el contrato: cualquier app de la familia, en
el móvil o en el PC, que lo cumpla se sincroniza con las demás. El núcleo (`nucleo/`, Kotlin puro) lo implementa y sus pruebas
lo comprueban contra el emulador oficial de Firebase con las reglas de `reglas.json`.

## Las reglas del juego

1. **Cada cosa existe una sola vez.** Un juego, un libro o una obra es un nodo: `familia/<colección>/<ref>`. No hay copias por
   dispositivo ni copias entre apps: Hiedra y Azabache leen y escriben el mismo nodo del mismo juego.
2. **Campo a campo, gana el cambio más reciente.** Cada campo lleva su hora en `m/<campo>` (milisegundos, hora del dispositivo
   corregida con la del servidor). Las reglas del servidor rechazan escribir un campo con una hora que no sea más nueva que la
   que tiene: un móvil que vuelve a tener red después de días no pisa lo que cambiaste luego en otro. Dos apps que tocan campos
   distintos de la misma cosa (Hiedra el estado, Azabache la nota) nunca se pisan.
3. **`u` es la hora del servidor del último cambio** del nodo (`{".sv":"timestamp"}`). Con ella se pide solo lo que cambió:
   `orderBy="u"&startAt=<la última u vista>` (las reglas tienen `.indexOn: ["u"]`).
4. **`familia_sello/<colección>`** = la hora del servidor del último cambio de esa colección. Es lo único que se escucha en
   directo (unos bytes): al cambiar, se baja lo nuevo de esa colección.
5. **Nada se borra de verdad**: borrar es poner `b` = la hora del borrado. Así ningún dispositivo lo resucita sin querer, y si
   otro lo cambia después de borrarlo, vuelve. Recuperar es `b = 0`.
6. **Nunca `null`**: un campo vacío es `""`, `0` o `false` (Firebase no comprueba las reglas al borrar).
7. **Escritura**: un solo PATCH multi-ruta sobre `/users/<uid>` con, por cada cosa tocada, sus campos, sus `m/<campo>`, su `u` y el
   sello de su colección. Si las reglas rechazan algo (Firebase responde 401 «Permission denied»), se reintenta campo a campo y
   se descarta solo lo que era más viejo.
8. **Lo de un solo dispositivo** (dónde está el archivo de un libro en este móvil, la ruta de una ROM…) no va a la cuenta.

## Imágenes

`familia_img/<hash>` = `{ d: base64, t: tipo, s: tamaño }`, donde `hash` son los 40 primeros caracteres hexadecimales del
SHA-256 de los bytes. Nunca cambian (las reglas no dejan reescribir una que existe). Una cosa apunta a su portada con el campo
`portada` = el hash. Cambiar de portada es guardar otra imagen y cambiar ese campo; quitarla es `portada = ""`. Las portadas se
guardan ya reducidas (unos 600×900, la proporción 2:3 de las cajas de SteamGridDB y las tapas de libro) para no pasar de unos
100 KB.

## Claves de servicios

`/claves/<uid>` (fuera de `users`: tu pareja puede leer tu cuenta, pero esto no). Se ponen una vez en Azabache y las usan todas:

| Clave | Para qué |
|---|---|
| `kdrive_token`, `kdrive_id` | Tu kDrive: los archivos grandes (libros, ROMs, partidas) y las versiones de las apps |
| `steamgriddb` | Portadas de juegos |
| `steam_key`, `steam_id` | Horas y biblioteca de Steam |
| `igdb_id`, `igdb_secreto` | Datos de juegos |
| `rawg` | Datos de juegos |
| `tmdb` | Películas y series (Azabache) |

## Colecciones

Los estados son los mismos en todas: `pendiente`, `en_curso`, `terminado`, `abandonado`.
La valoración de Azabache va en el mismo nodo de la cosa: `nota` (0–100; sin el campo o 0 = **sin valorar**), `resena` y lo
demás de la valoración (ver Azabache). Solo Azabache escribe esos campos; las demás apps los leen.

### `juegos/<id>` (Hiedra)

`id` = `j_` + 12 hexadecimales, el de siempre de Hiedra (se crea una vez y viaja).

| Campo | Qué es |
|---|---|
| `t`, `pl` | Título y plataforma |
| `estado` | `pendiente` / `en_curso` / `terminado` / `abandonado` |
| `fav`, `fin`, `an`, `ul` | Favorito, cuándo lo pasaste, cuándo se añadió, última vez (ms) |
| `menu` | `pc` / `emulacion` / `android` / `web` |
| `k` | Las claves de antes (`juego:<título>`, lanzadores que ya no tiene) |
| `ln` | Los lanzadores: `[{tp, s, a, em, pk, u, o, pck, pcd}]` (dónde está la ROM en cada móvil NO va aquí) |
| `portada`, `pu`, `ez`/`eox`/`eoy` | La portada (hash), de dónde salió si fue de internet, y su encuadre |
| `dh`, `de`, `dc` | Lo que suele durar (minutos): historia, con extras, al completo |

Las horas, en `tiempo/<id>~<fuente>` (`fuente` = el dispositivo —en el ordenador, `hiedra-linux-…` o `hiedra-mac-…`—, `steam`,
`pc:<pc>` o `carmesi:<dispositivo>`): `total` (ms),
`veces`, `menu`, `t` y `d<AAAA-MM-DD>` = ms. Lo de un juego es la **suma** de sus fuentes.

### `libros/<kid>` (Marfil)

`kid` = los 20 primeros hexadecimales del SHA-1 de la clave del libro (`libro:<título normalizado>:<autor normalizado>`, la de
siempre de Marfil): el mismo libro en cualquier dispositivo tiene el mismo `kid` sin tener que ponerse de acuerdo.

| Campo | Qué es |
|---|---|
| `clave` | La clave del libro (fija desde que se creó) |
| `t`, `a` | Título y autor |
| `estado` | `pendiente` / `en_curso` / `terminado` / `abandonado` |
| `fav` | Favorito |
| `an`, `ul` | Cuándo se añadió y cuándo se abrió por última vez (ms) |
| `fr` | Por dónde vas (0–1): la última posición leída, venga del dispositivo que venga |
| `pg`, `po` | Páginas y de dónde sale ese número |
| `fin` | Las veces que lo terminaste (lista de fechas en ms) |
| `portada`, `pu` | La portada (hash de `familia_img`) y, si salió de internet, su dirección |
| `archivo` | Nombre del archivo (con extensión) |
| `nube` | Dónde está el archivo en tu kDrive: `{f: id, n: nombre, s: tamaño}` |

### `lectura/<kid>` (Marfil)

Dónde vas dentro del libro, para abrirlo en el mismo sitio en cualquier dispositivo: `cfi`, `fraction`, `label`, `read`
(cuándo), `bestCfi`/`bestFrac`/`bestLabel`/`bestSet` (la posición marcada a mano), `settings` y `settingsAt` (los ajustes del
lector para ese libro).

### `marcas/<kid>~<id>` (Marfil)

Un subrayado, una nota o un marcador: `ref` (el `kid`), `tipo` (`note` / `mark`), `n` (el objeto del lector tal cual: `value`,
`created`, `text`, `note`, `color`…) y `b` si se quitó. `id` sale de su sitio y su hora de creación.

### `tiempo/<ref>~<dispositivo>` (Marfil, Hiedra)

Lo leído o jugado en un dispositivo (o fuente): `ref`, `fuente`, `total` (ms) y un campo por día `d2026-11-05` = ms. Cada
dispositivo escribe solo los suyos: para saber lo de un día se **suman** los de todos.

### `obras/<obra>`, `capitulos/<obra>~<capítulo>` y `palabras/<dispositivo>` (Cobalto)

Lo que escribes vive en los documentos de Escribir (`/users/<uid>/escritos`, `escritosIdx`), los mismos que Atrio del PC, con su
mezcla a tres bandas (para que un texto escrito a la vez en dos sitios no se pierda): su contrato está en **`ESCRITOS.md`**. Para las
demás apps, Cobalto publica:

- `obras/<obra>`: `t`, `a`, `estado` (`en_curso` / `pausa` / `terminado` / `archivada`), `palabras`, `ul`. Es la ficha que valora
  Azabache (nota y reseña en el mismo nodo).
- `capitulos/<obra>~<capítulo>`: la copia de solo lectura de cada capítulo terminado (`ref` = la obra, `t`, `a`, `c`, `n`, `pa`, `ue` la
  última edición del capítulo en ms, `p` palabras, `x` el texto). Al quitar la marca de terminado, `b`. Es lo que lee Marfil. (Hasta Cobalto 0.3.1
  se mandaba como `u`, que es la hora del servidor y el núcleo no deja escribir: no llegaba nada. Quien lee usa `ue` o, si falta, la `u` del nodo.)
- `palabras/<dispositivo>`: `d<AAAA-MM-DD>` = palabras escritas ese día en ese dispositivo (se suman los dispositivos).

### La valoración (Azabache)

En el nodo de la cosa (`juegos/<id>`, `libros/<kid>`, `obras/<obra>`): `nota` (0–100; sin el campo = **sin valorar**), `crit` (los
cuatro criterios), `resena`, `az` (lo propio de Azabache de esa ficha, como texto JSON: apuntes, capítulos marcados, imagen de fondo,
cuándo empezaste…) y `azid` (el número de la ficha en Azabache, el mismo en todos tus dispositivos: lo usan tus sagas, tu diario y
tus maquetas).

**Azabache no guarda copia de los juegos ni de los libros**: sus fichas son estos nodos, leídos tal cual (Azabache 1.4.0). Lo que
cambias en Azabache va al nodo, y solo lo que has cambiado tú (si cambias el estado en Azabache, cambia en Hiedra o Marfil; si no
lo tocas, no se escribe). Lo que solo estaba en Azabache (lo valorado en Atrio que nunca pasó por Hiedra ni Marfil) pasó, la
primera vez, a ser juegos y libros de la cuenta como los demás.

### `azabache/<lista>~<id>` y `azabache/<nombre>` (Azabache)

Lo que es solo de Azabache: cada elemento de cada lista (`items_<categoría>`, `iplays_<categoría>`, `diario`, `sagas`, `maquetas`…)
es un nodo con `o` = el elemento tal cual (como texto JSON); lo que no es una lista (`goals`, `ajustes`…) va en `azabache/<nombre>`
con `v`. Un elemento borrado lleva `b`. Los juegos y los libros no van aquí (`games`, `plays`, `books`, `bplays`: son `juegos` y
`libros`); si quedó alguno de la 1.3, no se lee.

### `ajustes/<nombre>`

`ajustes/marfil`, `ajustes/hiedra`, `ajustes/cobalto` (la apariencia de cada app: campo `skin`; Marfil también `lector`, los ajustes
del lector; Hiedra también `ini`, su portada), `ajustes/azabache` (`at` y `skin`: la apariencia de Azabache, que las demás pueden
copiar) y `ajustes/lectura` (los objetivos: `dia` minutos al día, `ano` libros del año, `anio` el año en que se fijó; los fija Marfil).

### `sagas/<id>`

`n` nombre, `tipo` (`libros`, `juegos`), `refs` (lista ordenada de las cosas de la saga).

## Lo que se queda como estaba

- Atrio del PC sigue con sus nodos (`suite/`: el PC en directo, sus órdenes, sus capturas y su biblioteca): «Tu PC» de Hiedra no cambia.
  Hiedra del ordenador (Linux y Mac) habla el mismo protocolo (`suite/live/<dispositivo>` con `app: "atrio"` y `cliente: "hiedra"`,
  `suite/lib/<dispositivo>`, `suite/cmd/<dispositivo>`) y añade `tiempo: true` en su presencia: ese PC cuenta él mismo lo que se juega en
  él (en `tiempo/<juego>~<dispositivo>`), y el móvil no lo vuelve a contar.
- El índice de las ROMs, partidas y estados de Hiedra en kDrive (`hiedra/nube/…`): son archivos de kDrive y su índice no ha cambiado.
- Los nodos de antes (`hiedra/dev`, `marfil/dev`, `cobalto/dev`, `dev`, `stamps`, `tiempo`…) no se tocan ni se borran: la migración
  los lee una vez en cada dispositivo y quedan como copia.
