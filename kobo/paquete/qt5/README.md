# Paquete para firmware 4.x (Qt 5.2.1): NickelMenu + NickelInkSync + libseshat

En 4.x los complementos se instalan con `.kobo/KoboRoot.tgz`, que se descomprime sobre `/` (rutas
`./usr/local/Kobo/imageformats/…`), no con `Kobo.tgz`. Las librerías Qt6 de `../` (NickelMenu-Kobo.tgz,
NickelInkSync-Kobo.tgz, `kobo/diagnostico/libseshat.so`) **no** valen en 4.x.

## Contenido

| Archivo | Qué es | SHA-256 |
|---|---|---|
| `KoboRoot.tgz` | El combinado para `.kobo/`: `./usr/local/Kobo/imageformats/{libnm.so, libnickelinksync.so, libseshat.so}`, root:root (0/0), 0755 | `f3758213fdec68afaa26f61de891a8397a47c6e5f45b2f32dd87fb7e25d26e9c` |
| `oficial/NickelMenu-v0.6.0/KoboRoot.tgz` | NickelMenu v0.6.0 oficial, tal cual | `322ff9aa863860e8f5f7e0b55cae561c54bf95983b9bce1d19819d1225d064af` |
| `oficial/NickelInkSync-v0.1.0/NickelInkSync-0.1.0-device.zip` | Paquete de dispositivo oficial de NickelInkSync v0.1.0, tal cual | `bfc28e4c26dd2b38e8276d503fa0c9cb44a52de1bb4772f42726739295195ba6` (coincide con su `SHA256SUMS`) |
| `oficial/NickelInkSync-v0.1.0/SHA256SUMS` | Las sumas publicadas en la release | `2de888e14798a2b62ac354f48ebbb9bd0203e9cb0c529418d230df8a1dce332d` |
| `oficial/NickelInkSync-v0.1.0/kobo-4.x/KoboRoot.tgz` | El build Qt5 sacado del zip anterior (`NickelInkSync-0.1.0-device/kobo-4.x/KoboRoot.tgz`) | `3dff2a3aea6b1bbaf856729360a3686b13d2cb09774f716b6ceacf51e257d2f2` |
| `libseshat-qt5.c`, `construye-qt5.sh` | Fuente y compilación del gancho de arranque para Qt 5 | |
| `libseshat-qt5.so` | El gancho compilado (en el combinado va como `libseshat.so`) | `84bdfe138b016f89ea2ad21d82215008482d1dae9f9eb8d09baaae3fda43c5cb` |
| `traduce_nis_qt5.py` | Traduce los avisos del plugin Qt5 de NickelInkSync (usa las traducciones de `../traduce_nis.py`) | |
| `pruebas/carga_qt5.cpp` | Prueba con Qt 5 real (QFactoryLoader, como QImageReader) | |
| `pruebas/comprueba_qt521.py` | Port a Python de `QElfParser::parse` + `findPatternUnloaded` de Qt 5.2.1 | |

Librerías dentro del combinado:

| Ruta | Origen | SHA-256 |
|---|---|---|
| `usr/local/Kobo/imageformats/libnm.so` | NickelMenu v0.6.0, sin tocar | `49f9f4dc312467d7bb096a5f5032b3cd79b369ee299061b184fb7e6d5d4f6475` |
| `usr/local/Kobo/imageformats/libnickelinksync.so` | NickelInkSync v0.1.0 Qt5 (original `f48fb64885baf7d89f76b90b4369ce57f453247d434dec81f4d4d1167adb6728`) **traducida** con `traduce_nis_qt5.py` | `6118f26e3ea25a831801178b39b58c78232c364db3dd3d8bf2e039931d4f48ec` |
| `usr/local/Kobo/imageformats/libseshat.so` | `libseshat-qt5.so` | `84bdfe138b016f89ea2ad21d82215008482d1dae9f9eb8d09baaae3fda43c5cb` |

No lleva `./mnt/onboard/.adds/nm/doc` (sí viene en el oficial de NickelMenu): sin carpeta `.adds/nm`
NickelMenu muestra un error de configuración, así que la configuración (`../nm-seshat`) hay que copiarla
aparte, como en 6.0. El programa `nickelinksync` de `.adds/NickelInkSync/` es el mismo binario en 4.x y 6.0
(viene en el zip).

## De dónde sale

- NickelMenu: <https://github.com/pgaskin/NickelMenu/releases/download/v0.6.0/KoboRoot.tgz> (última etiqueta,
  v0.6.0; `releases/latest/download/KoboRoot.tgz` da el mismo archivo). `libnm.so` con fecha 2025-12-07, enlaza
  `libQt5{Widgets,Gui,Core}.so.5`, RPATH `/usr/local/Kobo:/usr/local/Qt-5.2.1-arm/lib`, flags ELF `0x5000400`.
- NickelInkSync: <https://github.com/dkuester/NickelInkSync/releases/download/v0.1.0/NickelInkSync-0.1.0-device.zip>
  y `…/v0.1.0/SHA256SUMS`. El `Kobo.tgz` de ese zip es idéntico a `../NickelInkSync-Kobo.tgz`
  (`b5eeb799…`). El Qt5 lo hace `tools/build_plugin_qt5.sh` (`just plugin-qt5`): el mismo
  `plugin/src/nickelinksync.cc` con `NH_QT_MAJOR=5` en la imagen Docker de NickelTC (`strayrose/nickeltc`,
  GCC 4.9, Qt 5.2.1); el workflow de release lo mete en el zip bajo `kobo-4.x/`. Clave de plugin
  `nh.nickelinksync`, flags `0x5000400`, enlaza también `libQt5Sql.so.5`.

## libseshat para Qt 5

Qt 5.2.1 (versión verificada: RPATH de libnm.so, `NickelHook.mk`, y los docs de NickelInkSync:
"Qt 5.2.1 (`usr/local/Qt-5.2.1-arm/lib/libQt5*.so.5`)") reconoce un complemento **sin cargarlo**: busca la
sección ELF `.qtmetadata` (PROGBITS), dentro la marca `QTMETADATA  ` (dos espacios) y un JSON binario `qbjs`.
Exige versión 5.x con menor ≤ 2, `debug` igual al de su Qt, IID de imageformats y una clave **única** en la
carpeta (si se repite, QFactoryLoader lo descarta y nunca lo carga). El `dlopen` (y el constructor) llega
cuando QImageReader recorre los complementos: pasa al leer cualquier imagen cuyo formato ningún complemento
reconoce (p. ej. los `.png` de la interfaz), igual que con NickelMenu. Sin `qt_plugin_instance` Qt lo da por
inválido después de cargarlo, sin descargarlo.

Los metadatos son los 244 bytes de `.qtmetadata` de libnm.so v0.6.0 con la clave `nh.nm` → `nh.sk`:
`{"IID":"org.qt-project.Qt.QImageIOHandlerFactoryInterface","MetaData":{"Keys":["nh.sk"]},"className":"NHPlugin","debug":true,"version":328193}`
(`debug: true` igual que libnm.so: el Kobo carga libnm, luego su Qt lo acepta). Exporta también
`qt_plugin_query_metadata`, como libnm.

Compilar: `./construye-qt5.sh` (clang `--target=armv7a-linux-gnueabihf -mfloat-abi=hard`, ld.lld, libc.so.6
de mentira). Una copia de prueba con otro guion: `GUION=/ruta/guion.sh SALIDA=/tmp/x.so ./construye-qt5.sh`.

## Comprobado

- Flags ELF `0x5000400` (igual que libnm.so y la libnickelinksync Qt5), ELF32 ARM, solo `NEEDED libc.so.6`.
- `pruebas/comprueba_qt521.py` (lógica de Qt 5.2.1 copiada de `qelfparser_p.cpp`/`qlibrary.cpp` v5.2.1): OK
  para libnm.so, libseshat-qt5.so y la libnickelinksync traducida.
- qemu-arm-static + glibc armhf de Debian 13: `dlopen` bien y el guion se ejecuta (copia con `GUION` de prueba).
- Qt 5 real (Debian 13 armhf `libqt5core5t64` 5.15.15, bajo qemu, `pruebas/carga_qt5.cpp`): QPluginLoader lee los
  metadatos; QFactoryLoader con el IID de imageformats da `keyMap 0 nh.nickelinksync, 1 nh.nm, 2 nh.sk`;
  `instance(2)` carga libseshat (`loaded library …libseshat.so`) y el guion de prueba se ejecutó. Una copia con
  clave repetida (`nh.nm`) quedó fuera del keyMap y no se cargó.
- `traduce_nis.py` **no** sirve para la Qt5: busca UTF-16 y el SHA-256 es otro; en Qt5 los textos están en UTF-8
  (`QString::fromUtf8`). `traduce_nis_qt5.py` hace lo mismo en UTF-8: 45 avisos, 1001 bytes, todos dentro de
  `.rodata`; uno no cabía en bytes y cambia: «Kein WLAN/Server, wird nachgeholt» → «Sin wifi/servidor: reintento».

## Sin comprobar

- Nada se ha probado en un Kobo con 4.46. La prueba con Qt es con 5.15, no con el 5.2.1 del Kobo (que sí se
  ha comprobado contra su código fuente). No se ha probado la carga real de libnm ni de libnickelinksync
  (necesitan libnickel y las Qt del Kobo).
- NickelMenu v0.6.0 en 4.46.x: sin informes encontrados (ver el informe). Confirmado por usuarios hasta 4.45.23792.
- NickelInkSync Qt5 en 4.46: su autor lo probó en Libra 2 con 4.38.23852; sus docs dicen de 4.46 "Not checked
  yet … every symbol above has to be checked again in the 4.46 libnickel". En la Clara BW con 4.46.23836 solo
  se comprobó la base de datos y el programa (`--version`), no el plugin.
- Kobo solo instala `KoboRoot.tgz` con la batería al 20 % o más (según el autor de Relio en MobileRead).
