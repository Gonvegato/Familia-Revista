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
| `tmdb` | Películas y series (Azabache; Mostaza, para pasar los ids de TMDB a los de IMDb) |
| `mostaza_addons` | Los addons de Mostaza, en tu orden: un texto JSON `[{"u": <dirección del manifiesto>, "n": <nombre>}]`. Van aquí y en ningún otro sitio de la cuenta porque **sus direcciones pueden llevar la clave de tu debrid** (tu pareja puede leer `/users/<uid>`, esto no). Sin la clave, Mostaza usa solo Cinemeta (`https://v3-cinemeta.strem.io/manifest.json`); `"[]"` = ninguno. De la lista entera gana el último cambio (moverlos o quitarlos es poner la lista otra vez). |
| `cobre_listas` | Tus listas de Cobre (la tele), en tu orden: un texto JSON `[{"id", "n", "t", "u", "us", "pw", "epg", "ua", "dev", "f", "c"}]`: `id` (`l` + 10 hexadecimales al azar, el que usan los nodos de abajo), `n` (el nombre que le pones), `t` (`m3u` una dirección M3U, `archivo` un archivo M3U de un dispositivo, `xtream` un login de Xtream Codes), `u` (la dirección de la lista o, en Xtream, el servidor), `us` y `pw` (el usuario y la contraseña de Xtream), `epg` (una guía XMLTV tuya), `ua` (el user-agent que pida tu proveedor), `dev` (en las de archivo, el dispositivo donde está el archivo: en los demás sale como «en otro dispositivo»), `f` (`ts` o `m3u8`: cómo se piden los canales de Xtream) y `c` (cuándo se añadió, ms). Van aquí y en ningún otro sitio de la cuenta porque **son secretos**: las direcciones llevan tu usuario y tu contraseña (tu pareja puede leer `/users/<uid>`, esto no). Sin la clave o `"[]"`: ninguna (Cobre no trae ninguna). De la lista entera gana el último cambio. |
| `shai_anthropic` | Tu clave de la API de Claude (la de tu organización de Claude Console, la de los créditos de tu plan), texto: la usa Shai para escribir lo de fuera del campo. Va aquí porque es una clave tuya: no la lee tu pareja; nunca va a `/users`, ni a un registro, ni a un mensaje. `""` o sin campo = Shai no pide nada |
| `mostaza_tmdb` | Tu clave de TMDB (el «token de lectura de la API», texto), para el catálogo propio de Mostaza (portada, Explorar, Buscar y fichas en castellano). Va aquí porque es una clave tuya: no la lee tu pareja. `""` o sin campo = sin catálogo propio (se usan los catálogos de tus addons) |

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

Lo leído en un escrito de Cobalto (una obra que lees en Marfil) va igual en `lectura`, `marcas` y `tiempo`, con `escrito_<obra>` en lugar
del `kid` (el libro en sí no está en `libros`: cada dispositivo lo rehace de `capitulos`). Quien suma lo leído de los libros, cuenta solo lo
que está en `libros`.

### `marcas/<kid>~<id>` (Marfil)

Un subrayado, una nota o un marcador: `ref` (el `kid`), `tipo` (`notes` / `marks`: el nombre de la lista del lector de donde sale), `n` (el objeto del lector tal cual: `value`,
`created`, `text`, `note`, `color`…) y `b` si se quitó. `id` sale de su sitio y su hora de creación.

### `tiempo/<ref>~<dispositivo>` (Marfil, Hiedra, Mostaza)

Lo leído, jugado o visto en un dispositivo (o fuente): `ref`, `fuente`, `total` (ms) y un campo por día `d2026-11-05` = ms. Cada
dispositivo escribe solo los suyos: para saber lo de un día se **suman** los de todos. Mostaza añade `t` (el título) y `veces` (las
sesiones; Mostaza del ordenador escribe como dispositivo `mostaza-linux-…` o `mostaza-mac-…`), y apunta el tiempo de una serie en el título (no en cada episodio); solo el tiempo de verdad reproduciendo (sin pausas ni
saltos), y solo de lo que está en tu biblioteca (ver `titulos`). Lo que Azabache tenía apuntado de una película o serie (sin días) pasó a
`tiempo/<ref>~azabache` (`fuente` = `azabache`).

### `titulos/<ref>` (Mostaza)

Las películas y las series de tu biblioteca (Mostaza las ve; Azabache las valora en el mismo nodo). `ref` = el id de Stremio con el que se
añadió, normalmente el de IMDb (`tt0111161`), con lo que Firebase no admite como `%XX`; lo que vino de Azabache sin código de IMDb,
`tmdb:<id>` o `tvmaze:<id>` (y `azabache:<id>` lo que se añadió a mano allí), y la ref ya no cambia aunque luego se sepa el código.

| Campo | Qué es |
|---|---|
| `id` | El id de Stremio (el de IMDb en cuanto se sabe) |
| `tipo` | `movie` / `series` (los tipos de Stremio) |
| `t`, `ano` | Título y año (`releaseInfo`: «1994», «2011–2019») |
| `estado` | `pendiente` / `en_curso` (Viendo) / `terminado` (Visto) / `abandonado` |
| `fav`, `an`, `ul`, `fin` | Favorita, cuándo se añadió, la última vez que la viste y cuándo la terminaste (ms) |
| `pu`, `portada`, `fondo` | El póster (dirección), la portada (hash de `familia_img`, como las demás) y la imagen de fondo |
| `dur`, `gen` | La duración (minutos: la de la película o la de un episodio) y los géneros |
| `imdb`, `tmdb`, `tvmaze` | Los ids de fuera (`tmdb` y `tvmaze`, los que usaba Azabache: se quedan) |
| `azid`, `azk` | El número de la ficha en Azabache y su categoría (los pone Azabache; ver «La valoración») |
| `b` | Borrada (con su hora). **Borrar es para siempre**: lo que entra solo (abajo) nunca la devuelve; solo «Añadir» la recupera, el mismo nodo con lo suyo |

Qué entra solo: nada hasta llevar **10 minutos viéndolo de verdad en una sesión del reproductor** (las pausas y los saltos no cuentan;
salir del reproductor termina la sesión). Antes no se escribe nada en la cuenta (ni aquí, ni en `progreso`, ni en `tiempo`): solo un punto
para seguir en ese dispositivo. A los 10 minutos entra en `en_curso` (o pasa a él desde `pendiente` o `abandonado`). Una película está
vista al 90 %; una serie está `terminado` cuando tiene vistos todos sus episodios estrenados (sin la temporada 0, la de los especiales) y,
si no, `en_curso`. De lo borrado no se escribe nada al verlo.

### `progreso/<vídeo>` (Mostaza)

Lo visto de cada película y de cada episodio, para seguir en cualquier dispositivo. `vídeo` = el id del vídeo en Stremio (el de la película,
o `tt0944947:1:3` en una serie), con lo que Firebase no admite como `%XX`.

| Campo | Qué es |
|---|---|
| `ref`, `v` | El título (`titulos/<ref>`) y el id del vídeo |
| `temp`, `ep` | Temporada y episodio (en las series) |
| `pos`, `dur` | Por dónde vas y lo que dura (ms) |
| `visto`, `cuando` | Visto (al 90 %, o marcado a mano) y cuándo |
| `ul` | La última vez que se vio (ms): gana lo más reciente entre esto y lo de cada dispositivo |

### `cobre_favoritos/<lista>~<canal>`, `cobre_grupos/<lista>~g<hash>`, `cobre_estado/ultimo` y `cobre_progreso/<lista>~<vídeo>` (Cobre)

Lo tuyo de Cobre, la tele de la familia (IPTV: tus listas M3U y tus logins de Xtream Codes, que van en `/claves`, arriba). `<lista>` es el
`id` de la lista; `<canal>`, la clave del canal: `x<stream_id>` en Xtream y `c` + 16 hexadecimales del SHA-256 de su `tvg-id` y su nombre
en M3U (`c…~2` si se repite), que no cambian aunque la lista se vuelva a bajar con otras direcciones; lo que Firebase no admite, como `%XX`.
**Aquí no va nada secreto** (ni direcciones, ni usuarios, ni contraseñas, ni imágenes del servidor de la lista: pueden ir por él): solo
ids, nombres y lo visto. Los canales, el videoclub y la guía no van a la cuenta (pueden ser decenas de miles de canales y cientos de megas
de guía): son caché de cada dispositivo. Sin cuenta, Cobre no escribe nada de esto.

| Nodo | Campos |
|---|---|
| `cobre_favoritos/<lista>~<canal>` | `lista`, `canal`, `n` (el nombre del canal, para enseñarlo sin la lista) y `o` (su sitio en tus favoritos: 1, 2, 3…; mover uno es cambiar los `o`). Quitarlo es `b`; volver a ponerlo, `b = 0` |
| `cobre_grupos/<lista>~g<16 hex del SHA-256 del nombre del grupo>` | `lista`, `g` (el nombre del grupo, tal cual) y `oculto` (sí/no: volver a enseñarlo es `false`) |
| `cobre_estado/ultimo` | El último canal que viste: `lista`, `canal`, `n` y `ul` (cuándo, ms) |
| `cobre_progreso/<lista>~p<id>` y `…~e<id>` | Lo visto de una película (`p` + su `stream_id`) o de un episodio (`e` + su id) del videoclub de Xtream: `lista`, `v` (el vídeo, `p…`/`e…`), `tipo` (`pelicula` / `episodio`), `id`, `ext` (la extensión del archivo), `t` (el título), y en los episodios `serie` (el `series_id`), `st` (el nombre de la serie), `temp` y `ep`; `pos` y `dur` (ms), `visto` (al 90 %, o a mano) y `ul` (la última vez; gana lo más reciente entre esto y lo de cada dispositivo). «Seguir viendo» sale de aquí |

Lo que ves en Cobre se queda en Cobre: no pasa a `titulos`, `progreso` ni `tiempo` de Mostaza (el videoclub de un servidor de Xtream no
trae ids de Stremio, y emparejarlo con la biblioteca de Mostaza con sus reglas —10 minutos, visto al 90 %, borrar es para siempre— sin
equivocarse no es seguro todavía).

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

### `sprints/<obra>~<desde>` (Cobalto)

Cada sprint de escritura, uno por nodo: la obra y la hora en que empezó (ms), así el mismo sprint es el mismo nodo venga del dispositivo
que venga. Lo escribe Cobalto (el móvil desde la 0.4.2 y el ordenador desde la 1.1.1) al empezar y al terminar; no se borra. Las reglas
de siempre (`familia/$col/$ref`) ya lo cubren: solo tú lo escribes y, como lo demás de tu cuenta, tu pareja puede leerlo.

| Campo | Qué es |
|---|---|
| `ref` | La obra (el id de su documento de Escribir) |
| `desde`, `hasta` | Cuándo empezó y cuándo acaba (o acababa) según lo elegido (ms) |
| `min` | Los minutos: los elegidos mientras está en marcha; al terminar, los que duró |
| `meta` | La meta de palabras (0 = sin meta) |
| `pal` | Las palabras nuevas de la obra mientras duró (las de todos los dispositivos) |
| `dia`, `base` | El día en que empezó (días desde 1970, en la hora del dispositivo) y las palabras que llevaba la obra ese día al empezar: con eso cualquier dispositivo cuenta lo que se lleva |
| `dev` | El dispositivo donde empezó (el mismo id de `palabras/<dispositivo>`) |
| `fin` | Cuándo terminó (ms); 0 = en marcha |
| `b` | Si duró menos de un minuto: no cuenta (como siempre) |

Quién lo termina: al cumplirse `hasta`, cualquier dispositivo que lo tenga abierto escribe `fin`, `min` y `pal` con la hora `m` =
`hasta`; gana el primero que llega (los demás llevan la misma hora y las reglas no les dejan pisarlo). Terminarlo antes, a mano, se
escribe con `m` = `hasta` + 1: gana siempre a eso, llegue cuando llegue. Si la hora pasa con todo cerrado, lo termina el dispositivo de
`dev` al volver a abrirse. Hasta Cobalto 0.4.1 (móvil) y 1.1.0 (ordenador) los sprints se guardaban en cada dispositivo (con `pid`,
`desde`, `min`, `pal` y `meta`); cada uno los pasa aquí una vez por cuenta y, si el nodo ya existe, no lo toca: se juntan los de todos
sin perder ni repetir ninguno. Lo de antes se queda donde estaba, como copia.

### La valoración (Azabache)

En el nodo de la cosa (`juegos/<id>`, `libros/<kid>`, `obras/<obra>`, `titulos/<ref>`): `nota` (0–100; sin el campo = **sin valorar**), `crit` (los
cuatro criterios), `resena`, `az` (lo propio de Azabache de esa ficha, como texto JSON: apuntes, capítulos marcados, imagen de fondo,
cuándo empezaste…) y `azid` (el número de la ficha en Azabache, el mismo en todos tus dispositivos: lo usan tus sagas, tu diario y
tus maquetas).

**Azabache no guarda copia de los juegos ni de los libros** (desde la 1.4.0) **ni de las películas y series** (desde la 1.6.3, con
Mostaza): sus fichas son estos nodos, leídos tal cual. En `titulos`, Azabache escribe además `azk` (su categoría) y, en lo que añade él
(sin Mostaza en ese dispositivo), crea el nodo `tmdb:<id>` o `tvmaze:<id>` con `tipo`, `t`, `estado` y esos ids. Lo que
cambias en Azabache va al nodo, y solo lo que has cambiado tú (si cambias el estado en Azabache, cambia en Hiedra o Marfil; si no
lo tocas, no se escribe). Lo que solo estaba en Azabache (lo valorado en Atrio que nunca pasó por Hiedra ni Marfil) pasó, la
primera vez, a ser juegos y libros de la cuenta como los demás.

### `azabache/<lista>~<id>` y `azabache/<nombre>` (Azabache)

Lo que es solo de Azabache: cada elemento de cada lista (`items_<categoría>`, `iplays_<categoría>`, `diario`, `sagas`, `maquetas`…)
es un nodo con `o` = el elemento tal cual (como texto JSON); lo que no es una lista (`goals`, `ajustes`…) va en `azabache/<nombre>`
con `v`. Un elemento borrado lleva `b`. Los juegos y los libros no van aquí (`games`, `plays`, `books`, `bplays`: son `juegos` y
`libros`); si quedó alguno de la 1.3, no se lee. Las películas y las series de las categorías de TMDB y TVmaze (`items_<categoría>` y
`iplays_<categoría>`) pasaron a `titulos` (Mostaza, una vez en cada dispositivo, sin perder nada: estado, fechas, nota, criterios,
reseña, lo de Azabache en `az`, el tiempo y los episodios marcados); los nodos de antes **se quedan como estaban** (como copia) y
Azabache (el del móvil desde la 1.6.3 y el del ordenador desde la 1.2.1) deja de leerlos en cuanto existe `azabache/a_mostaza` (`v` = `[<hora>]`, `por`, `cuantas`:
la marca de que Mostaza ya los trajo).

### `shai/<ref>` (Shai)

Tus carreras de EA Sports FC y lo que pasa en ellas. Nada secreto (tu clave de la API va en `/claves`, arriba); la foto de tu personaje se
queda en el móvil.

| Nodo | Campos |
|---|---|
| `u<10 hex>` | Una carrera: `o` = la carrera en JSON (`{"v": 1, "id", "mo" (`JUGADOR`/`MISTER`), "pj" (el personaje: nombre, apodo, edad, nacionalidad, posición, dorsal, media y cifras del juego, rasgos e historial de la media), "cl", "li", "te" (club, liga, temporada), "sa" (sueldo, € a la semana), "ch" (contrato), "di" (dinero, €), "fu" (fama, afición, disciplina y carisma, 0–100), "en" (su gente, inventada: `{id, n, r, a, t}`), "ca"/"cc" (acciones y su precio medio, céntimos), "bi" (casas, coches y negocios: `{id, t, n, c, v, r, d}`), "pr" (el precio de cada acción y los últimos, céntimos), "pa" (las jornadas), "lo" (los lotes de la API que aún no han vuelto: `{id, c, p}`), "ae" (lo de entre partidos, solo), "c", "cm", "mm"/"mp" (lo que Shai recuerda y en qué jornada), "tc" (temporadas cerradas), "nv" (el nivel de salida: `CANTERANO`…`LEYENDA`), "ty" (su trayectoria, con sus palabras), "fj"/"fh" (la fama ganada en la jornada y su historial), "ct" (su contrato de Shai: `{cl, su, de, ha, pr, cu, pg, ob, im, no}`; vacío = libre), "pt" (patrocinios, € a la semana), "rp" (jornada en que se habló de renovar), "ht" (hitos y récords: `{id, ti, tx, pa, ed, pr, te}`)}`; desde 0.2.0. Las carreras de antes sin `ct` pasan su sueldo y su «N años» a contrato al leerlas) |
| `e<10 hex>` | Algo que pasa en una carrera: `un` = la carrera, `o` = en JSON (`{"v": 1, "id", "un", "tp" (`PARTIDO`, `EVENTO`, `RESPUESTA`, `OFICIAL`, `INICIO`), "c", "pa", "ti", "tx", "pt" (el partido tal cual lo metiste), "po" (posts), "dm" (mensajes), "pr" (la portada), "of" (ofertas, con su estado), "de" (una decisión: opciones, lo que elegiste y lo que pasó), "ef" (lo que cambió: dinero, lo de fuera del campo, tu gente), "vd" (desde cuándo se ve), "ap" (sus efectos ya cuentan), "pe" (lo que falta por pedir: `red`, `prensa`), "tn"/"tp2" (la tendencia y sus posts), "pm" (premios de fin de temporada); los posts llevan `rs` (respuestas) y `ci` (cita), los mensajes `c` (la conversación), la portada `ar` (la noticia) y `oe` (entradillas), la decisión `ti`/`cx`/`ej`/`ac` y el estado `caducada`, las ofertas `ng` (ya negociada); tipos nuevos `TEMPORADA`, `SINCONTESTAR`, `HITO`}`) |
| `k<id de la carrera>` | Solo del modo prueba (que va aparte, en el móvil): `us` = las plantillas que ya han salido, separadas por `\|` |
| `gasto` | Lo gastado en la API: `<AAAAMM>_<dispositivo>_<h\|s>` = millonésimas de dólar (Haiku o Sonnet) de ese mes en ese dispositivo; el del mes es la suma |
| `ajustes` | `tope` (dólares al mes; de serie, 100) y `auto` (lo de entre partidos, solo) |

### `ajustes/<nombre>`

`ajustes/marfil`, `ajustes/hiedra`, `ajustes/cobalto`, `ajustes/mostaza`, `ajustes/cobre`, `ajustes/ptah`, `ajustes/shai` (la apariencia de cada app: campo `skin`; Marfil también `lector`, los ajustes
del lector; Hiedra también `ini`, su portada), `ajustes/azabache` (`at` y `skin`: la apariencia de Azabache, que las demás pueden
copiar) y `ajustes/lectura` (los objetivos: `dia` minutos al día, `ano` libros del año, `anio` el año en que se fijó; los fija Marfil).
Lo demás que eliges en cada app, en `ajustes/<app>_<grupo>` (abajo).

### `ajustes/<app>_<grupo>`: lo demás que eliges, un campo por ajuste

Lo que eliges en cada app y no es la apariencia (el orden de cada sección, cómo se juega, los filtros de Descubrir…) también es de la
cuenta: un nodo por grupo de ajustes, **un campo por ajuste**, con el mismo nombre que tiene en las preferencias de Android (lo que
Firebase no admite en un nombre va como `%XX`: `.` es `%2E`, `/` es `%2F`; y `m`, `u`, `b` serían `%6D`, `%75`, `%62`). De cada campo gana el
cambio más reciente, como en todo lo demás: dos dispositivos que cambian ajustes distintos no se pisan. El valor es un sí/no, un número
o un texto; `""` es «quitado» (vuelve a lo de por defecto). Lo que es una lista va como texto JSON (o separado por comas, como en Android).
La primera vez en cada dispositivo, lo que tenía de antes pasa a la cuenta sin perder nada: lo que la cuenta no tenía, tal cual; lo que
tenía distinto, con la hora a la que ese dispositivo cambió esos ajustes por última vez (gana lo más reciente); y las listas que solo crecen
(marcadas abajo con «se juntan») se juntan. Lo de un solo dispositivo (abajo, «no viaja») no está en ningún nodo.

| Nodo | Campos |
|---|---|
| `hiedra_orden` | `PENDIENTE`, `JUGANDO`, `PASADO`, `ABANDONADO` = el orden de esa sección: `Recientes`, `Nombre`, `Jugados`, `Anadidos`, `Pasados` (este, solo en Pasado) |
| `hiedra_juego` | El jugador de Hiedra: `tactil` (`AUTO` / `SIEMPRE` / `NUNCA`), `opacidad`, `tamano`, `zona` (decimales), `vibracion`, `pequenos`, `etiqueta`, `avSilencio` (sí/no), `avModo` (`MANTENER` / `CONMUTAR`), `avVel` (2, 3, 4, 8), `autoguardado` (minutos; 0 = solo al salir), `orientacion` (`APAISADA` / `VERTICAL` / `MOVIL`), `filtro` (`NITIDO` / `SUAVE` / `LCD` / `CRT`) y `mapa:<descriptor del mando>` = lo que cambiaste de ese mando (`tecla=destino;…`, como lo guarda Hiedra) |
| `hiedra_ajustes` | `libretro` (portadas automáticas) y `emu:<sistema>` = la etiqueta del emulador elegido para esa consola (sin campo o `""`: el automático; si ese emulador no está en un dispositivo, allí se usa el automático) |
| `hiedra_nucleos` | `op:<sistema>:<opción del núcleo>` = el valor que elegiste |
| `hiedra_suite` | `pc` (el PC de Atrio al que se mandan las órdenes), `galeria` (las capturas del PC también en la galería) |
| `hiedra_nube_p` | `roms_solas` (subir tus ROMs a kDrive solas) |
| `hiedra_internet`, `marfil_internet`, `azabache_internet` | `fuente`: la web de imágenes que usaste la última vez (`google`, `artstation`, `pinterest`…) |
| `hiedra_actualizador`, `marfil_actualizador`, `azabache_actualizador` | `on`: «actualizar sola» |
| `marfil_orden` | `LEYENDO`, `PENDIENTE`, `LEIDO`, `ABANDONADO` = `RECIENTES`, `ANADIDOS`, `TERMINADO` (solo en Leídos), `TITULO`, `AUTOR`, `PAGINAS` |
| `marfil_nube` | `auto_bajar` (tener en cada dispositivo los libros de tu kDrive) |
| `azabache_general` | `recsdone` (ids de las recomendaciones de tu pareja que ya añadiste o descartaste, con comas; se juntan), `suitepc` (el PC de Atrio elegido), `shotsgal`, `diario_todo`, `pnotify` (avisos de lo que hace tu pareja), `knows2tap`, `aviso_lectura` y `aviso_diario` (la hora del aviso, 0–23; −1 sin aviso), `goal_<categoría>_<año>` (el objetivo de ese año: 0 juegos, 1 libros…) y `myname` (tu nombre). Las guías no: se quitaron de Azabache |
| `azabache_nube` | `shots` (subir las capturas del PC a tu kDrive) |
| `mostaza_reproductor` | `subs` (el idioma de los subtítulos: `spa`, `eng`… ; `""` sin), `subsSiempre` (también con el audio en tu idioma), `subTam` (por ciento), `subFondo` (sobre una banda), `audio` (el idioma de audio preferido), `siguiente` (el episodio siguiente, solo), `salto` (segundos), `externo` (el paquete del reproductor de fuera; `""` = preguntar; es de Android: Mostaza del ordenador no lo lee ni lo cambia) |
| `mostaza_elector` | El elector de fuentes: `idiomas` (el orden, con comas: `castellano`, `latino`, `vose` —original con subtítulos— y `original`), `subtitulados` (vale una sin audio en español), `maxima` (la calidad máxima: 2160, 1080, 720, 480), `directas` (las directas o de tu debrid antes que los torrents), `ahorro` (con datos del móvil, como mucho 720p; el ordenador no tiene datos móviles: lo deja como está), `auto` («Reproducir» elige sola) |
| `mostaza_torrent` | Para el motor de torrents: `soloWifi`, `subida` y `bajada` (KB/s; 0 = sin límite), `compartir` (mientras ves). Lo que puede ocupar en cada dispositivo, no (no viaja). El ordenador no mira `soloWifi` (no tiene datos móviles) |
| `mostaza_rotas` | Las fuentes que, en vez de la película, dan un vídeo de aviso de tu debrid (Real-Debrid con «infringing_file», por ejemplo): `r<24 hex>` (una huella SHA-256 de la clave de la fuente, sin secretos) = la hora en que se vio. «Reproducir» no las elige solas durante 30 días; siguen en la lista |
| `mostaza_orden` | `VIENDO`, `PENDIENTE`, `VISTO`, `ABANDONADO` = `RECIENTES`, `ANADIDOS`, `TITULO`, `ANO`; `filtro_<sección>` = `TODO`, `PELICULAS`, `SERIES` |
| `mostaza_portada` | `filas` (los catálogos de la portada, `<addon>\|<tipo>\|<catálogo>` con comas; `""` = los primeros), `cuantas`, y lo último de «Explorar»: `explorarTipo`, `explorarCatalogo`, `explorarGenero` |
| `mostaza_actualizador` | `on`: «actualizar sola» |
| `cobre_reproductor` | `aspecto` (`AJUSTAR`, `LLENAR`, `ZOOM`, `ANCHO` —16:9—, `ESTRECHO` —4:3—), `subs` (el idioma de los subtítulos: `spa`, `eng`…; `""` sin), `audio` (el idioma de audio preferido), `ahorro` (con datos del móvil, la calidad más baja), `reconectar` (volver a conectar un canal que se corta), `siguiente` (el episodio siguiente, solo), `invertirCanales` (arriba en el mando es el canal anterior), `salto` (segundos), `externo` (el paquete del reproductor de fuera; `""` = preguntar; es de Android), `segundoPlano` («seguir sonando al salir» del reproductor) |
| `cobre_guia` | `horas` (cada cuántas se vuelve a bajar la guía), `dias` (cuántos días por delante), `soloWifi` (con datos del móvil, solo si la pides) |
| `cobre_directo` | `orden` (`LISTA`, `NUMERO`, `NOMBRE`: el de los canales), `lista` y `grupo` (dónde estabas: el id de la lista y el nombre del grupo), `alAbrir` (al abrir Cobre, el último canal), `videoclub` («pelis y series del proveedor»: el videoclub de Xtream en el menú; apagado de serie) |
| `cobre_futbol` | El fútbol de Cobre (Nut), textos JSON: `equipos` (tus equipos: `[{"id", "n", "c", "s", "e", "l"}]` — el id de ESPN, el nombre, el corto, las siglas, el escudo, una imagen pública de ESPN, y la competición donde lo elegiste: una de selecciones —`uefa.nations`, `fifa.world`, `fifa.friendly`…— si es una selección), `rivales` (tus rivales, igual que `equipos`; un equipo está en una lista o en la otra), `grupos` (tus grupos de canales: `[{"id", "n", "c": [{"u"\|"h"\|"s": {"l", "k", "n"}}]}]` — cada entrada, el mismo canal en Ultra HD, HD y SD: el id de la lista, la **clave** del canal (`x<id>` o `c<hash>`, nunca su dirección) y su nombre), un grupo de serie borrado queda como `{"id", "x": true}` para que no vuelva), `asignacion` (qué grupos tiene cada competición: `{"esp.1": ["movistar", "dazn"], …}`) `calidad` (`""` automática, `UHD`, `HD`, `SD`), `competiciones` (tu orden de las competiciones de los partidos del día: sus claves de ESPN con comas; las que falten, detrás en el orden de serie) y `ocultas` (las que escondes, con comas: no se piden). Nada secreto: tu pareja lo puede leer |
| `cobre_actualizador` | `on`: «actualizar sola» |
| `ptah_disenos` | Tus diseños de widget de Ptah: un campo por diseño, su id (`d` + 10 hexadecimales) = el diseño en JSON: `{"v": 1, "id", "n" (su nombre), "tipo" (`digital`, `agujas`, `calendario`, `buscador`, `accesos`, `tiempo`, `bateria`, `nota`, `cuentaatras`, `foto`…), "estilo": {"fondo" (`PAPEL`/`COLOR`/`DEGRADADO`/`TRANSPARENTE`), "cf", "cf2", "ang", "op", "tinta", "acento", "sec", "piezas" ({pieza: color}), "borde", "gb", "cb", "radio", "marco", "lt", "lx", "esc", "al", "ocultas"}, "op" (lo de su tipo: la cabecera, la ciudad y sus coordenadas, las apps de los accesos —sus paquetes— o webs, la fecha de la cuenta atrás, el pie de foto…), "c" y "cr" (cuándo se cambió y se creó, ms)}`. Un color es `@papel`, `@tinta`, `@acento`, `@gris` (los de tu Apariencia), `@transparente`, `#RRGGBB` o `#AARRGGBB`, o `""` (el de serie). Borrado = `""`. Ni la foto ni dónde estás: se quedan en el móvil. Nada secreto |
| `ptah_general` | `recientes`: los colores que has usado hace poco en Ptah (`#RRGGBB` con comas) |
| `ptah_actualizador` | `on`: «actualizar sola» |
| `shai_actualizador` | `on`: «actualizar sola» |

No viaja (es de cada dispositivo): dónde está cada ROM, las carpetas de ROMs y de guardados, lo ya sincronizado con kDrive, los núcleos y BIOS
bajados, las capturas bajadas, el juego que está abierto ahora, «usar Hiedra como launcher» (es de Android), la configuración de cada widget,
el brillo del lector, el sprint de Escribir que está corriendo, la copia en Google Drive de ese móvil, la sesión y las cachés (datos de
juegos, páginas de libros, notas de Rotten Tomatoes, plataformas de RAWG, lo de las hermanas que se refleja en Azabache). En Mostaza: lo
que mide la conexión de ese dispositivo (por wifi y por datos) y lo que sabe decodificar, lo que puede ocupar su caché de torrents, el punto
para seguir de lo que aún no está en tu biblioteca (antes de los 10 minutos), los manifiestos de los addons (se vuelven a pedir) y su id. En Cobre:
los canales, el videoclub y la guía de cada lista (caché, cifrada la que lleva direcciones), el archivo de una lista de archivo, el punto para
seguir sin cuenta y su id (`cobre-…`; en el ordenador, `cobre-linux-…` o `cobre-mac-…`: es el `dev` de las listas de archivo). En Ptah: las fotos de
los widgets de foto, qué diseño lleva cada widget, la ubicación aproximada y el tiempo bajado.

### `/users/<uid>/name`

Tu nombre, el que ve tu pareja (lo pone Azabache; un texto). Fuera de `familia`, como `user` y `partner`.

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
