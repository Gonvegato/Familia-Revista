# Mostaza

Las películas y las series de la familia, y verlas. Como Hiedra es tu biblioteca de juegos y además juegas en ella, Mostaza es tu
biblioteca de películas y series y además las ves en ella. Hermana de [Azabache](../azabache/HISTORIA.md) (la revista: valorar),
[Hiedra](../hiedra/HISTORIA.md) (juegos), [Marfil](../marfil/HISTORIA.md) (libros) y [Cobalto](../cobalto/HISTORIA.md) (escribir): la misma
interfaz «Revista», la misma cuenta y la misma firma; su paquete es `com.gonzalo.mostaza`.

Funciona como Stremio: habla el protocolo abierto de los **addons de Stremio** (catálogos, fichas, fuentes y subtítulos). Los catálogos y
las fichas salen de Cinemeta (el oficial de Stremio, el único que viene puesto) y de los que pongas tú; las fuentes, de tus addons de
fuentes, con tu Real-Debrid configurado en el suyo (esas dan enlaces directos, que Mostaza reproduce al momento). Mostaza no trae ningún
addon de fuentes ni de torrents: los pones tú.

## Capturas

Hechas en el ordenador con catálogos y fuentes de mentira (`CapturasTest`, solo con `-Proborazzi.test.record=true`), en el móvil, en la
tablet tumbada y de pie, en claro y en oscuro: portada, Viendo, Visto, Explorar, Buscar, la ficha de una película y la de una serie, las
fuentes, los addons, Ajustes (también la pestaña de las fuentes) y el reproductor (sus controles, los subtítulos y el episodio siguiente).

## 0.1.1

- **Con datos del móvil se ve igual.** Las fuentes directas y las de tu Real-Debrid no tienen ningún límite con datos (con «Ahorrar
  datos del móvil», Mostaza prefiere hasta 720p y lo que menos pese; si quieres la mejor calidad también con datos, apágalo en Ajustes →
  Elector). Arreglo: con «Solo con wifi» puesto y yendo con datos, «Reproducir» podía elegir un torrent que el motor no iba a abrir; ahora
  elige la mejor fuente directa o de tu debrid. Si quieres torrents con datos, quita «Solo con wifi» en Ajustes → Torrent.

## 0.1.0 — la primera

**Lo que hay**

- **Portada** de revista: la cabecera, el número y la fecha, el menú (Viendo · Pendiente · Visto · Abandonado · Explorar · Addons),
  **«Seguir viendo»** (lo que tienes a medias, con lo que llevas: «T2 · E5 · quedan 23 min»), **«Pendientes»** y las filas de los
  catálogos de tus addons (las primeras, o las que elijas). Al bajar, la cabecera se pliega.
- **Tus secciones**: Viendo · Pendiente · Visto · Abandonado, cada una con películas y series (o solo unas u otras), en rejilla, ordenadas
  por recientes, añadidas, título o año (lo que eliges se recuerda, en tu cuenta).
- **Explorar** (los catálogos de tus addons): por tipo (películas, series…), cada catálogo con sus filtros de **género** o de **año** (los
  que da el propio catálogo: los de Cinemeta, «Populares» por género y «Por año»), por páginas.
- **Buscar** en todos tus addons que buscan (cada uno en su fila) y en tu biblioteca.
- **La ficha**: el fondo, el póster, el titular, el año, la duración, la nota de IMDb, los géneros, la dirección, el reparto y la
  descripción; dónde está en tu biblioteca (Pendiente · Viendo · Visto · Abandonado, favorita, quitar) o «+ Añadir»; **«Reproducir»** (o
  «Seguir viendo», o «Ver · T2·E6» en una serie: el episodio que toca); y en una serie, sus **temporadas y episodios** con lo que llevas y
  la marca de visto (que se pone y se quita a mano). «En Azabache»: la nota y la reseña que le has puesto allí, y «Valorar en Azabache».
- **Las fuentes** de una película o un episodio: todas las de tus addons, agrupadas por addon (en tu orden), con sus líneas tal como las
  da el addon, lo que Mostaza entiende de cada una («1080p · Castellano · 4,4 GB») y si es **directa**, **torrent**, YouTube, una web,
  Usenet o un comprimido. La que pondría Mostaza va marcada («La mejor para ti»). Tocar una la pone.
- **El elector de fuentes** (lo que hace «Reproducir»): de todas las fuentes de todos tus addons, la mejor para ti. El protocolo no dice
  ni el idioma, ni la calidad, ni las semillas: Mostaza lo lee del texto de cada fuente (la resolución, el HDR o Dolby Vision, el códec, el
  idioma del audio —por las palabras y por las banderas: 🇪🇸 es castellano; 🇲🇽 o 🇦🇷, latino—, si lleva subtítulos en español, el tamaño,
  las semillas de un torrent y si tu debrid ya la tiene). Y elige así:
  1. **tu idioma primero**: una en un idioma que prefieres gana a cualquier calidad. El orden lo pones tú (de serie: castellano, latino,
     y la original con subtítulos); «Español» sin decir cuál va detrás del castellano de verdad y delante del latino;
  2. luego **la mejor calidad que cabe en tu conexión** (lo que pesa por minuto frente a lo que ha medido este dispositivo viendo cosas,
     por wifi y por datos aparte) y que **este dispositivo sabe reproducir** (el códec, el HDR, su resolución), sin pasar de la que pongas
     como máxima; con datos del móvil, si ahorras, como mucho 720p y lo que menos pese. Un torrent, con semillas (o ya en tu debrid);
  3. luego las **directas o de tu debrid** antes que los torrents;
  4. y al final, el orden de tus addons.
  Al pulsar, se pone la elegida y se dice cuál ha sido («1080p · Castellano · 4,4 GB · Fuentes de casa · Cambiar»); la lista entera está a
  un toque. Si se corta una y otra vez, Mostaza apunta que la conexión da menos y te ofrece la siguiente. En el episodio siguiente prefiere
  la fuente de la misma clase que la del anterior (el «bingeGroup» del addon, como Stremio).
- **El reproductor** (AndroidX Media3): los enlaces directos (también HLS y DASH), con las cabeceras que pida la fuente; subtítulos de tus
  addons de subtítulos y los que trae el vídeo (sin subtítulos si el audio ya está en tu idioma, salvo que los quieras siempre; el tamaño y
  la banda, a tu gusto); elegir audio, subtítulos y fuente; sigue donde lo dejaste (aquí o en otro dispositivo); el **episodio siguiente**,
  solo, con una cuenta atrás; y **«Abrir con otro reproductor»** (VLC, Just Player, mpv, MX…, con el título, por dónde ibas y las
  cabeceras) para lo que este dispositivo no sabe decodificar: Mostaza lo dice cuando pasa (también si es solo el sonido: DTS, TrueHD…).
  Con el mando: la cruceta salta adelante y atrás, OK enseña los controles.
- Las fuentes de **YouTube** (los tráileres) se abren en YouTube y las de una **web**, en el navegador. Las de **Usenet** (NZB) y las de
  **comprimidos** dicen, con la verdad, que todavía no.
- **Los torrents se ven mientras se bajan**, con el motor de la familia (libtorrent4j, de código abierto): eliges un torrent, se prepara
  (unos segundos: buscar a quién lo tiene y leer su lista de archivos) y se reproduce mientras baja, y puedes saltar a cualquier punto (se
  baja primero lo que vas a ver). Al cerrar el vídeo se deja de compartir y se borra. Mientras lo ves, lo que bajas se comparte con otros
  (así van los torrents), con la subida limitada a 100 KB/s de partida. Con «Solo con wifi», con datos del móvil no se baja nada. Todo eso
  (solo con wifi, subida, bajada, compartir) viaja con tu cuenta; lo que pueden ocupar los torrents en el móvil es de cada dispositivo.
  Una fuente de tu Real-Debrid no es un torrent para el móvil: se ve como cualquier vídeo, sin compartir nada.
- **Tus addons**: instalar uno por la dirección de su manifiesto (o desde el botón «Install» de su página: los `stremio://` se abren
  aquí), subirlos y bajarlos (el de arriba manda en las fichas), quitarlos y **configurarlos** (la página del propio addon: lo que pongas
  allí, como tu debrid, se queda en la dirección nueva). Si quitas Cinemeta, «Poner Cinemeta» lo devuelve; OpenSubtitles (el oficial de
  Stremio para los subtítulos) se pone con un toque, si quieres.
- **Ajustes** en pestañas: Apariencia (la de la familia, con el acento mostaza de serie) · Reproductor · Fuentes (el elector, tu
  conexión, lo que sabe este dispositivo y los torrents) · Cuenta (y las hermanas) · Avanzado (actualizarse sola y la portada).
- **Se actualiza sola** desde tu kDrive («Mi carpeta/versiones», `Mostaza-<versión>.apk`), como las hermanas: nunca con algo en marcha
  (el reproductor abierto) ni con la pantalla apagada.
- **Entrar una vez**: si ya has entrado en Azabache o en otra hermana, Mostaza entra sola (y al revés, con Azabache 1.6.3; Hiedra,
  Marfil y Cobalto, desde su próxima versión).
- En la tablet (tumbada y de pie) y en la tele: la ficha a dos columnas, los estantes más grandes, y todo se mueve con el mando
  (lo que tiene el foco lleva el marco del acento).

**Las tres reglas**

1. **Nada entra en tu biblioteca hasta que llevas 10 minutos viéndolo de verdad, seguidos, en una misma sesión del reproductor.** Cuenta
   solo el tiempo reproduciendo: las pausas no cuentan, saltar adelante no cuenta, y salir del reproductor termina la sesión (si vuelves,
   empieza otra). Hasta entonces **no se escribe nada en tu cuenta** (solo un punto para seguir en ese dispositivo). A los 10 minutos entra
   en «Viendo» (o pasa a «Viendo» si estaba pendiente o abandonada), con todo lo de esa sesión; si en esa misma sesión llega al final,
   queda en «Visto».
2. **El tiempo que ves se apunta** por día y por dispositivo (lo lee Azabache para su diario, como lo de Hiedra y Marfil), y **por dónde
   vas** en cada película y cada episodio viaja (sigues en otro dispositivo). Una película o un episodio está visto al 90 %; una serie
   pasa a «Visto» cuando tienes vistos todos sus episodios estrenados (los especiales no cuentan) y, si no, sigue en «Viendo».
3. **Borrar es para siempre**: queda borrada en tu cuenta (en todos tus dispositivos y en Azabache) y lo que entra solo **nunca** la
   devuelve, ni aunque la vuelvas a ver entera (y de lo que ves de ella no se apunta nada). Solo «Añadir» la recupera: la misma, con lo
   suyo (su estado, sus fechas, su nota, lo visto). Es lo que se aprendió en Hiedra 0.5.4.

**Lo que tenía Azabache de películas y series ya es de Mostaza**

Azabache guardaba tus películas y tus series en sus categorías (las de TMDB y TVmaze). Ahora son de Mostaza, como los juegos pasaron a
Hiedra y los libros a Marfil: la primera vez que abres Mostaza con tu cuenta, cada ficha pasa a tu biblioteca con todo lo suyo (el estado,
cuándo la añadiste y cuándo la terminaste, la favorita, el póster y el fondo, la nota con sus criterios, la reseña, tus apuntes y lo demás
de la ficha, el tiempo que tenía apuntado y, en las series, los episodios marcados), con su código de IMDb (el que usan los addons de
Stremio: sale de TMDB con la clave de TMDB de tu cuenta, o de TVmaze) y con sus ids de antes. Nada se borra ni se cambia en Azabache (sus
fichas de antes se quedan donde estaban, como copia), y desde ese momento **Azabache valora las de Mostaza**: la misma ficha, sin copia, con
su número de siempre (tus sagas, tu diario y tus maquetas la siguen encontrando). En otro dispositivo no se repite nada, y lo que
hayas cambiado o borrado en Mostaza no se pisa.

**Todo en tu cuenta** (lo que viaja y lo que no)

| Lo que hay en Mostaza | ¿Viaja? | Dónde / por qué |
|---|---|---|
| Tu biblioteca (películas, series, su estado, fechas, favoritas) | Sí | `titulos` |
| Por dónde vas en cada película y episodio, y lo visto | Sí | `progreso` (de lo que está en tu biblioteca) |
| El tiempo que ves, por día | Sí | `tiempo/<título>~<dispositivo>` |
| Lo que quitas | Sí | `titulos` (borrado, con su hora) |
| Tus addons y su orden | Sí | `/claves` (lo que no lee ni tu pareja: llevan la clave de tu debrid) |
| La apariencia | Sí | `ajustes/mostaza` |
| Subtítulos (idioma, tamaño, banda, siempre), audio, episodio siguiente, el salto, el otro reproductor | Sí | `ajustes/mostaza_reproductor` |
| Tus idiomas, con subtítulos o no, la calidad máxima, directas antes, ahorrar datos, elegir sola | Sí | `ajustes/mostaza_elector` |
| Torrents: solo con wifi, límites, compartir | Sí | `ajustes/mostaza_torrent` |
| El orden y el filtro de cada sección | Sí | `ajustes/mostaza_orden` |
| Las filas de la portada y lo último de Explorar | Sí | `ajustes/mostaza_portada` |
| «Actualizar sola» | Sí | `ajustes/mostaza_actualizador` |
| Lo que puede ocupar la caché de torrents | No | es el almacenamiento de cada dispositivo |
| Lo que mide la conexión, lo que sabe decodificar | No | es de cada aparato (y de cada red) |
| El punto para seguir de lo que aún no está en tu biblioteca | No | la regla 1: antes de los 10 minutos, nada a la cuenta |
| Los manifiestos y catálogos de los addons | No | es lo que dice cada addon: se vuelve a pedir |
| El id del dispositivo, la sesión, lo pendiente de subir, lo de la última comprobación del actualizador, los colores de Azabache leídos | No | es lo que lleva cada dispositivo de la cuenta (la sesión, cifrada) |

**Lo que no se ha podido probar (honesto)**

- **Nada de esto se ha probado en un móvil, una tablet ni una tele de verdad.** Las pruebas del ordenador comprueban las tres reglas con la
  cuenta de verdad (el emulador de Firebase), el protocolo contra un addon de mentira, el elector con títulos como los de verdad, que las
  direcciones de tus addons no salen de `/claves`, el paso de lo de Azabache en dos dispositivos, que Azabache lee lo que escribe Mostaza
  y que lo que eliges viaja. Las capturas enseñan las pantallas. Pero el reproductor no se ha probado con vídeos de verdad: qué formatos
  abre depende de cada dispositivo (para eso está «Abrir con otro reproductor»).
- El elector lee texto libre: un addon que escriba de otra manera puede confundirlo (la lista entera siempre está a un toque).
- La página de configurar de cada addon es la suya: si en vez de su botón de instalar te da la dirección para copiarla, pégala en
  «Instalar un addon» (sustituye a la de antes si es el mismo addon… o se añade otro: quita el viejo).
- Sin TMDB en tu cuenta, lo que venía de Azabache como película entra sin código de IMDb (`tmdb:<id>`) hasta que pongas la clave: su ficha
  no sale de Cinemeta hasta entonces.
- Hiedra, Marfil y Cobalto aún no le dan la cuenta a Mostaza (lo hará su próxima versión): entra con Azabache 1.6.3, o con tu usuario.
- Usenet y los comprimidos: más adelante. Los torrents están probados de verdad (un torrent compartido en el mismo ordenador, visto
  entero y saltando, byte a byte), pero todavía no en un móvil de verdad ni con gente de internet.

## Compilar

```
./gradlew :mostaza:assembleDebug
./gradlew :mostaza:testDebugUnitTest                                   # con el emulador de Firebase encendido (herramientas/emulador.sh)
./gradlew :mostaza:testDebugUnitTest --tests '*Capturas*' -Proborazzi.test.record=true -Dcapturas=<carpeta>
./gradlew :mostaza:assembleRelease && python3 herramientas/firmar.py mostaza   # la firma de la familia, sin linaje (nace con ella)
python3 herramientas/mostaza/subir-version.py mostaza/build/outputs/apk/release/Mostaza-0.1.0.apk
```

El reproductor es AndroidX Media3 1.9.4: la última que se compila con el SDK 35 de la familia (desde la 1.10 pide el 36). El contrato con
Azabache: `ContratoTest` deja en `build/outputs/contrato/mostaza.json` lo que escribe Mostaza, y Azabache lo lee en su `ContratoHermanasTest`
(copia en `herramientas/casos/hermanas/mostaza.json`). Al sacar versión: `versionCode`/`versionName` (`build.gradle.kts`), `VERSION`
(`PantEstilo.kt`) y esto.
