# La familia

Las apps de la familia en un solo sitio, alrededor de **una sola cuenta**:

| Módulo | App | Paquete | Para qué |
|---|---|---|---|
| `hiedra/` | Orion | `com.gonzalo.hiedra` | Tus juegos: biblioteca, lanzador y jugador |
| `marfil/` | Lyra | `com.gonzalo.marfil` | Tus libros: biblioteca y lector |
| `cobalto/` | Nova | `com.gonzalo.cobalto` | Lo que escribes |
| `azabache/` | Polaris | `com.gonzalo.gamerate` | La revista: valoraciones, diario, cifras, y donde se ponen las claves |
| `mostaza/` | Sirius | `com.gonzalo.mostaza` | Tus películas y series: biblioteca y reproductor (con los addons de Stremio) |
| `cobre/` | Pulsar | `com.gonzalo.cobre` | La tele (IPTV): tus listas M3U y tus logins de Xtream, con su guía y su videoclub; en el móvil, la tablet y Android TV |
| `ammit/` | Eclipse | `com.gonzalo.ammit` | Modo enfoque: cuando está encendido, solo pasan las seis estrellas de la familia |
| `ptah/` | Vega | `com.gonzalo.ptah` | Creador de widgets editoriales con las cinco variaciones estéticas de la familia |

Cada app conserva su paquete de siempre, así que cada versión nueva se instala encima de la que ya tienes.

## Una sola cuenta

Todas son una sola cosa alrededor de tu cuenta (`nube/ESQUEMA.md`): cada juego, libro, obra, película o serie existe una vez, campo a
campo, y todas lo leen y lo escriben ahí, también sin conexión. Las portadas son imágenes de la cuenta. Las claves (**Google Drive**, kDrive de respaldo, SteamGridDB, Steam,
IGDB, RAWG, TMDB) se ponen una vez y las tienen todas. Entras una vez (en Polaris o en cualquiera) y las demás entran solas. Todas
llevan la misma firma y se actualizan solas.

## Sirius (películas y series)

La app nueva de la familia: tu biblioteca de películas y series y verlas, como Stremio (habla el protocolo abierto de sus addons:
catálogos, fichas, fuentes y subtítulos), con la interfaz de la revista. De serie solo trae Cinemeta (el oficial de Stremio, para las
fichas); los addons de fuentes los pones tú, con tu Real-Debrid configurado en el suyo, y se guardan en la parte secreta de tu cuenta
(`/claves`: sus direcciones llevan tu clave). «Reproducir» elige la mejor fuente para ti (tu idioma primero, luego la mejor calidad que
cabe en tu conexión y que sabe reproducir el dispositivo) y la pone en su reproductor (Media3), o en otro si el dispositivo no puede. Nada
entra solo en tu biblioteca hasta que llevas 10 minutos viéndolo; lo que borras no vuelve solo. Lo que tenía Polaris de películas y
series pasó a Sirius (y Polaris las valora allí, sin copia). Los torrents llegan en la próxima versión. Todo en
`docs/mostaza/HISTORIA.md`.

## Pulsar (la tele)

La más nueva de la familia: un reproductor de IPTV, como Televizo, para el móvil, la tablet y la tele (Android TV y Google TV, todo con el
mando). No trae ningún canal: pones tu lista M3U (una dirección o un archivo) o el login de tu servidor de Xtream Codes, y tienes tus canales
en directo con su guía (XMLTV, o la del propio servidor) y, con Xtream, sus películas y sus series. Tus listas van solo en la parte secreta
de tu cuenta (`/claves`: llevan tus contraseñas); tus favoritos, los grupos que escondes, el último canal, lo visto y «Seguir viendo», en la
cuenta como todo lo demás. Los canales y la guía son caché de cada dispositivo (se bajan a trozos: listas y guías enormes no se tienen
enteras en memoria). Lo que no es pantalla está en `iptv/`, en Kotlin puro, y lo usa también Pulsar del ordenador (1.0.0, en `escritorio/cobre`). Todo en `docs/cobre/HISTORIA.md`.

## ATLAS (los mapas de las novelas)

ATLAS (`com.gonzalo.atlas`) es la app de dibujo de los mapas de fantasía (Kotlin y Compose; hasta la 0.15.0 era de Godot) y vive en su propio repositorio, [Gonvegato/Atlas](https://github.com/Gonvegato/Atlas):
no se compila aquí. Guarda los mapas en la cuenta (`familia/atlas`, y sus archivos en la carpeta «Familia» de Drive), cada uno ligado a una
novela de `familia/obras`. Es de la familia (`LaFamilia.APPS` y las `<queries>` del módulo `familia`): con la firma de la familia, las hermanas le
pasan la sesión y ella a ellas.

La clave de la familia solo está aquí, así que la firma se pone aquí: `.github/workflows/atlas.yml` (a mano y cada hora) baja la última
`ATLAS.apk` de sus releases, comprueba que es la de su `ATLAS.json` (su huella `apk_sha256`, el paquete, la versión y el código), la vuelve a firmar con `familia.keystore`
(alias `familia`, sin linaje), comprueba que queda un único firmante con la huella de `Acceso.CERTIFICADO` y la sube a «Familia/versiones» como
`Atlas-<versión>.apk` (con su `.sha256`). Una versión que ya está en Drive no se vuelve a subir (salvo con «forzar»). Todo en
`herramientas/atlas/firmar_atlas.py`.

## En el ordenador (Linux y Mac)

Polaris, Nova, Orion, Lyra, Sirius y Pulsar también son apps de escritorio, en tu nube (Google Drive `Familia/versiones`, con respaldo en kDrive `Mi carpeta/versiones`): `.dmg` para el Mac (Apple
Silicon) y `.tar.gz` para Linux (CachyOS, Arch y cualquier otro: se descomprime y `./instalar.sh`, sin sudo; `--al-iniciar` para que
Orion se abra al entrar en la sesión). No guardan nada suyo: leen y escriben la misma cuenta, así que lo que haces en el ordenador está al
momento en el móvil y al revés. **Se actualizan solas** desde tu nube (con comprobación SHA-256 si hay archivo `.sha256`; la versión nueva se pone al cerrar la app). Una sola copia de cada
una abierta a la vez, y desde cualquiera se abren las otras. Sirius y Pulsar ven con mpv (se instala aparte). Todo en
`docs/ESCRITORIO.md`.

## Pasar a la familia (una sola vez)

1. **Las reglas nuevas de Firebase**: copia `nube/reglas.json` (también en Polaris → Ajustes → Cuenta → «Reglas de Firebase») y
   pégalas en la consola de Firebase → Realtime Database → Reglas → Publicar. Sin ellas, las claves no se pueden guardar en la cuenta.
2. **Instala las versiones nuevas a mano** encima de las que tienes (no hace falta desinstalar nada). Las que ya llevan la firma de la familia se actualizan solas desde «versiones» (en Google Drive o kDrive).
3. **Abre cada app una vez en cada móvil**: lo de antes pasa a la cuenta nueva (con la hora real de cada cambio). Lo de antes no se
   borra.

## Qué hay aquí

- `nucleo/`: la cuenta de la familia y el cliente de Google Drive (`GoogleDrive.kt`) en Kotlin puro (sirve también para el PC). Sus pruebas van contra el emulador oficial de Firebase
  con las reglas de verdad (`herramientas/emulador.sh`).
- `familia/`: lo común de Android (la cuenta en cada app, `NubeFamilia`, `ActualizadorComun`, entrar una vez, los trabajos de fondo, las portadas).
- `hiedra/`, `marfil/`, `cobalto/`, `azabache/`, `mostaza/`, `cobre/`: las apps de Android.
- `mau/`: la mascota compartida (Android y escritorio).
- `kobo/`: programa en Go para sincronizar lectura con el Kobo (`LyraKobo`).
- `ptah/` y `shai/`: **Widgets** y **FcFeed**, dos apps que no son de la familia (ver abajo).
- `iptv/`: lo de Pulsar que no es pantalla (listas M3U, Xtream Codes, la guía XMLTV), en Kotlin puro (sirve también para el PC).
- `escritorio/`: las seis para **Linux y Mac** (Compose Multiplatform), contra la misma cuenta (`docs/ESCRITORIO.md`), y el motor de
  torrents de Sirius para el ordenador (`escritorio/torrent`, los mismos archivos que `torrent/`).
- `herramientas/gdrive/`: utilidades sin dependencias para autorizar Google Drive (`autorizar.py`), subir versiones con `.sha256` (`subir-version.py`) y migrar todos tus archivos desde kDrive sin perder nada (`migrar-kdrive.py`).
- `nube/`: las reglas (`reglas.json`), el esquema de la cuenta (`ESQUEMA.md`) y el de los escritos (`ESCRITOS.md`): el contrato que
  cumple cualquier app de la familia, en el sistema que sea.
- `docs/<app>/`: el historial de cada app (`HISTORIA.md`) y sus capturas; `docs/RECONSTRUCCION.md`: cómo se hizo y qué falta;
  `docs/ESCRITORIO.md`: las versiones de Linux y Mac (cómo se instalan, qué hacen y qué todavía no).
- `firma/`: las claves de firma (no están en el repositorio); `herramientas/firmar.py` firma cada versión con la de la familia.

## Apps que no son de la familia

Viven en este repositorio (comparten la herramienta de firma y el código común de Kotlin, `nucleo/`, para leer una vez la cuenta de antes),
pero **no son de la familia**: sin cuenta, sin la revista, sin entrar con las demás, sin Polaris. Todo lo suyo se guarda solo en el móvil.
Se actualizan solas desde tu kDrive, cada una desde su carpeta. Conservan su paquete y su firma de siempre, así que se instalan encima.

| Módulo | App | Paquete | Para qué | Carpeta en kDrive |
|---|---|---|---|---|
| `ptah/` | Vega | `com.gonzalo.ptah` | Tus widgets editoriales, con las cinco variaciones estéticas de la familia (relojes, tiempo, accesos, fotos…) | `Familia/versiones` (`Vega-x.y.z.apk`) |
| `shai/` | FcFeed | `com.gonzalo.shai` | Tus carreras de EA Sports FC como una red social: la red, los mensajes, la prensa, las ofertas y la vida de fuera del campo, escritas por Claude | `Mi carpeta/fcfeed` (`FcFeed-x.y.z.apk`) |

Hasta la 0.5.x se llamaban Ptah y Shai y eran de la familia; desde la 0.6.0, la primera vez que se abren traen al móvil lo que tenían en la
cuenta (diseños; carreras, gasto y clave de la API; el token de kDrive) y ya no la usan. Lo de antes se queda en la cuenta sin tocar.
Todo en `docs/widgets/HISTORIA.md` y `docs/fcfeed/HISTORIA.md`; los iconos, en `herramientas/apartes/iconos.py`.

