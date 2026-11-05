# La familia

Las cuatro apps de la familia en un solo sitio, alrededor de **una sola cuenta**:

| Módulo | App | Paquete | Para qué |
|---|---|---|---|
| `hiedra/` | Hiedra | `com.gonzalo.hiedra` | Tus juegos: biblioteca, lanzador y jugador |
| `marfil/` | Marfil | `com.gonzalo.marfil` | Tus libros: biblioteca y lector |
| `cobalto/` | Cobalto | `com.gonzalo.cobalto` | Lo que escribes |
| `azabache/` | Azabache | `com.gonzalo.gamerate` | La revista: valoraciones, diario, cifras, y donde se ponen las claves |

Cada app conserva su paquete de siempre, así que cada versión nueva se instala encima de la que ya tienes.

## Una sola cuenta

Las cuatro son una sola cosa alrededor de tu cuenta (`nube/ESQUEMA.md`): cada juego, libro u obra existe una vez, campo a campo, y
todas lo leen y lo escriben ahí, también sin conexión. Las portadas son imágenes de la cuenta. Las claves (kDrive, SteamGridDB, Steam,
IGDB, RAWG, TMDB) se ponen una vez y las tienen todas. Entras una vez (en Azabache o en cualquiera) y las demás entran solas. Las
cuatro llevan la misma firma y se actualizan solas.

## Pasar a la familia (una sola vez)

1. **Las reglas nuevas de Firebase**: copia `nube/reglas.json` (también en Azabache → Ajustes → Cuenta → «Reglas de Firebase») y
   pégalas en la consola de Firebase → Realtime Database → Reglas → Publicar. Sin ellas, las claves no se pueden guardar en la cuenta.
2. **Instala las cuatro versiones nuevas a mano** (Hiedra 0.5.2, Marfil 0.13.3, Cobalto 0.3.1, Azabache 1.4.0), encima de las que
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
- `hiedra/`, `marfil/`, `cobalto/`, `azabache/`: las apps.
- `nube/`: las reglas (`reglas.json`) y el esquema de la cuenta (`ESQUEMA.md`).
- `docs/<app>/`: el historial de cada app (`HISTORIA.md`) y sus capturas; `docs/RECONSTRUCCION.md`: cómo se hizo y qué falta.
- `firma/`: las claves de firma (no están en el repositorio); `herramientas/firmar.py` firma cada versión con la de la familia.
