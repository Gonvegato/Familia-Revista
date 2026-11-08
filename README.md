# La familia

Las apps de la familia en un solo sitio, alrededor de **una sola cuenta**:

| Módulo | App | Paquete | Para qué |
|---|---|---|---|
| `hiedra/` | Bastet | `com.gonzalo.hiedra` | Tus juegos: biblioteca, lanzador y jugador |
| `marfil/` | Seshat | `com.gonzalo.marfil` | Tus libros: biblioteca y lector |
| `cobalto/` | Thot | `com.gonzalo.cobalto` | Lo que escribes |
| `azabache/` | Jonsu | `com.gonzalo.gamerate` | La revista: valoraciones, diario, cifras, y donde se ponen las claves |
| `mostaza/` | Hathor | `com.gonzalo.mostaza` | Tus películas y series: biblioteca y reproductor (con los addons de Stremio) |
| `cobre/` | Nut | `com.gonzalo.cobre` | La tele (IPTV): tus listas M3U y tus logins de Xtream, con su guía y su videoclub; en el móvil, la tablet y Android TV |
| `ptah/` | Ptah | `com.gonzalo.ptah` | Tus widgets de la pantalla de inicio, diseñados a tu manera (relojes, el tiempo, accesos, fotos…) |
| `shai/` | Shai | `com.gonzalo.shai` | Tus carreras de EA Sports FC: la prensa, la red, los mensajes, las ofertas y la vida de fuera del campo, escritas por Claude |

Cada app conserva su paquete de siempre, así que cada versión nueva se instala encima de la que ya tienes.

## Una sola cuenta

Todas son una sola cosa alrededor de tu cuenta (`nube/ESQUEMA.md`): cada juego, libro, obra, película o serie existe una vez, campo a
campo, y todas lo leen y lo escriben ahí, también sin conexión. Las portadas son imágenes de la cuenta. Las claves (kDrive, SteamGridDB, Steam,
IGDB, RAWG, TMDB) se ponen una vez y las tienen todas. Entras una vez (en Jonsu o en cualquiera) y las demás entran solas. Todas
llevan la misma firma y se actualizan solas.

## Hathor (películas y series)

La app nueva de la familia: tu biblioteca de películas y series y verlas, como Stremio (habla el protocolo abierto de sus addons:
catálogos, fichas, fuentes y subtítulos), con la interfaz de la revista. De serie solo trae Cinemeta (el oficial de Stremio, para las
fichas); los addons de fuentes los pones tú, con tu Real-Debrid configurado en el suyo, y se guardan en la parte secreta de tu cuenta
(`/claves`: sus direcciones llevan tu clave). «Reproducir» elige la mejor fuente para ti (tu idioma primero, luego la mejor calidad que
cabe en tu conexión y que sabe reproducir el dispositivo) y la pone en su reproductor (Media3), o en otro si el dispositivo no puede. Nada
entra solo en tu biblioteca hasta que llevas 10 minutos viéndolo; lo que borras no vuelve solo. Lo que tenía Jonsu de películas y
series pasó a Hathor (y Jonsu las valora allí, sin copia). Los torrents llegan en la próxima versión. Todo en
`docs/mostaza/HISTORIA.md`.

## Nut (la tele)

La más nueva de la familia: un reproductor de IPTV, como Televizo, para el móvil, la tablet y la tele (Android TV y Google TV, todo con el
mando). No trae ningún canal: pones tu lista M3U (una dirección o un archivo) o el login de tu servidor de Xtream Codes, y tienes tus canales
en directo con su guía (XMLTV, o la del propio servidor) y, con Xtream, sus películas y sus series. Tus listas van solo en la parte secreta
de tu cuenta (`/claves`: llevan tus contraseñas); tus favoritos, los grupos que escondes, el último canal, lo visto y «Seguir viendo», en la
cuenta como todo lo demás. Los canales y la guía son caché de cada dispositivo (se bajan a trozos: listas y guías enormes no se tienen
enteras en memoria). Lo que no es pantalla está en `iptv/`, en Kotlin puro, y lo usa también Nut del ordenador (1.0.0, en `escritorio/cobre`). Todo en `docs/cobre/HISTORIA.md`.

## Ptah (los widgets)

La más nueva: un creador de widgets para la pantalla de inicio, con la revista de la familia. Haces tus diseños (relojes digitales y de
agujas, el calendario del mes, un buscador, accesos a tus apps y webs, el tiempo de Open-Meteo, la batería, notas, cuentas atrás y fotos)
con todos los colores, letras, bordes y esquinas que quieras, y los pones en la pantalla de inicio del tamaño que quieras. Los relojes se
mueven solos (con `TextClock` y `AnalogClock`). Tus diseños van en tu cuenta (`ajustes/ptah_disenos`); las fotos se quedan en el móvil.
Entra con tu cuenta como las demás, pero Jonsu no enseña nada suyo. Solo Android. Todo en `docs/ptah/HISTORIA.md`.

## Shai (tu carrera de EA Sports FC)

La más nueva: el acompañante de tus modos carrera de EA Sports FC 26 (de jugador o de míster), con varias carreras a la vez. Cada partido que
juegas lo metes (rápido, completo o con una foto de la pantalla de estadísticas, que lee Claude y tú revisas) y Shai monta lo de fuera del
campo: la portada del día siguiente, la red (como X), los mensajes, las ofertas («la app manda»: si aceptas aquí, lo haces en el juego), las
decisiones con consecuencias, el dinero en euros, la bolsa y las compras (todo inventado), tu gente y tu fama. Entre partido y partido, dos
cosas que pasan mientras tanto (por lotes, a mitad de precio). Lo del campo lo pones tú: Shai no se inventa ni lesiones, ni suplencias, ni
resultados. Las personas reales solo salen de pasada; lo íntimo y lo escandaloso, siempre con personajes inventados.

Lo escribe Claude con **tu clave de la API** (la de los créditos de tu plan, en Claude Console), que va solo a `/claves` (`shai_anthropic`):
Haiku 5.5 para la red, los mensajes y las fotos; Sonnet 5.5 para lo demás. Shai cuenta lo gastado con lo que dice cada respuesta y se para al
llegar al tope del mes (de serie, 100 $). Tus carreras van en tu cuenta (la colección `shai`); la foto de tu personaje se queda en el móvil.
Entra con tu cuenta como las demás, pero Jonsu no enseña nada suyo. Solo Android. Desde la 0.2.0: contratos de Shai, nivel y alcance que suben poco a poco (la edad pesa), récords e hitos, decisiones que caducan, memoria y cierre de temporada. Compara tu carrera con 200 **récords del fútbol de verdad** (con fecha de nacimiento y de partido, la edad exacta) y lleva tus **rivales**, que surgen solos. Con un **modo prueba** (Ajustes → IA) para ver
todas las funciones sin IA y sin gastar (plantillas de ejemplo; carreras aparte, solo en el móvil). Todo en `docs/shai/HISTORIA.md`.

## En el ordenador (Linux y Mac)

Jonsu, Thot, Bastet, Seshat, Hathor y Nut también son apps de escritorio, en tu kDrive («Mi carpeta/versiones»): `.dmg` para el Mac (Apple
Silicon) y `.tar.gz` para Linux (CachyOS, Arch y cualquier otro: se descomprime y `./instalar.sh`, sin sudo; `--al-iniciar` para que
Bastet se abra al entrar en la sesión). No guardan nada suyo: leen y escriben la misma cuenta, así que lo que haces en el ordenador está al
momento en el móvil y al revés. **Se actualizan solas** desde tu kDrive (la versión nueva se pone al cerrar la app). Una sola copia de cada
una abierta a la vez, y desde cualquiera se abren las otras. Hathor y Nut ven con mpv (se instala aparte). Todo en
`docs/ESCRITORIO.md`.

## Pasar a la familia (una sola vez)

1. **Las reglas nuevas de Firebase**: copia `nube/reglas.json` (también en Jonsu → Ajustes → Cuenta → «Reglas de Firebase») y
   pégalas en la consola de Firebase → Realtime Database → Reglas → Publicar. Sin ellas, las claves no se pueden guardar en la cuenta.
2. **Instala las versiones nuevas a mano** (la última de cada una; hoy Bastet 0.6.2, Seshat 0.14.2, Thot 0.4.2, Jonsu 1.6.3, y
   Hathor 0.1.0 y Nut 0.1.0, que son nuevas), encima de las que
   tienes: no hace falta desinstalar nada. Están en tu kDrive, en «Mi carpeta/familia-a-mano» (siempre la última). El actualizador de
   antes no las acepta porque la firma es nueva; las que ya llevan la firma de la familia se actualizan solas desde «versiones». Un
   dispositivo que aún tenga las apps de antes (la tablet) las ve en «versiones», las baja y las rechaza cada 4 horas hasta que las
   instales a mano: hazlo en cuanto lo enciendas.
3. **Abre cada app una vez en cada móvil**: lo de antes pasa a la cuenta nueva (con la hora real de cada cambio). Lo de antes no se
   borra.

## Qué hay aquí

- `nucleo/`: la cuenta de la familia en Kotlin puro (sirve también para el PC). Sus pruebas van contra el emulador oficial de Firebase
  con las reglas de verdad (`herramientas/emulador.sh`).
- `familia/`: lo común de Android (la cuenta en cada app, entrar una vez, los trabajos de fondo, las portadas).
- `hiedra/`, `marfil/`, `cobalto/`, `azabache/`, `mostaza/`, `cobre/`, `ptah/`, `shai/`: las apps de Android.
- `iptv/`: lo de Nut que no es pantalla (listas M3U, Xtream Codes, la guía XMLTV), en Kotlin puro (sirve también para el PC).
- `escritorio/`: las seis para **Linux y Mac** (Compose Multiplatform), contra la misma cuenta (`docs/ESCRITORIO.md`), y el motor de
  torrents de Hathor para el ordenador (`escritorio/torrent`, los mismos archivos que `torrent/`).
- `nube/`: las reglas (`reglas.json`), el esquema de la cuenta (`ESQUEMA.md`) y el de los escritos (`ESCRITOS.md`): el contrato que
  cumple cualquier app de la familia, en el sistema que sea.
- `docs/<app>/`: el historial de cada app (`HISTORIA.md`) y sus capturas; `docs/RECONSTRUCCION.md`: cómo se hizo y qué falta;
  `docs/ESCRITORIO.md`: las versiones de Linux y Mac (cómo se instalan, qué hacen y qué todavía no).
- `firma/`: las claves de firma (no están en el repositorio); `herramientas/firmar.py` firma cada versión con la de la familia.
