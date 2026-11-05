# Las versiones de escritorio (Linux y Mac)

Para quien vaya a hacer **Hiedra, Marfil, Cobalto y Azabache para Linux y Mac**. Qué hay ya, qué se comparte, qué hay que hacer en
escritorio y cómo trabajar a la vez que las versiones de Android sin pisarse.

## La idea (lo que no se negocia)

- **La cuenta es la única verdad.** Firebase (Realtime Database + Authentication con usuario y contraseña), el proyecto de siempre,
  plan gratuito. Todo lo de la familia está ahí; cada app, en el sistema que sea, es una ventana a la cuenta. En el dispositivo solo
  hay una caché para funcionar sin conexión (y lo que es de ese dispositivo: dónde está un archivo, la apariencia…).
- **El contrato** es lo que hace que todas vean lo mismo:
  - `nube/ESQUEMA.md`: toda la cuenta (colecciones campo a campo, imágenes, claves, lo de cada app).
  - `nube/ESCRITOS.md`: lo que escribes (Cobalto; su propio formato, con mezcla a tres bandas).
  - `nube/reglas.json`: las reglas del servidor. **Comprueban el contrato**: lo que no lo cumple (una hora de campo vieja, una imagen
    que ya existe, escribir en las claves de otro…) se rechaza. Se ponen a mano en la consola de Firebase.
- **Sin copias entre apps.** Un juego es uno (`juegos/<id>`), un libro es uno (`libros/<kid>`): Hiedra lo cambia, Azabache lo valora,
  en el mismo nodo, cada una sus campos. Una versión de escritorio que guarde «su» copia de algo de otra app rompe esto.

## Tecnología: Kotlin y Compose Multiplatform

- **Compose Multiplatform 1.12** (escritorio: macOS 13 o más en arm64; Linux tipo Ubuntu 20.04 o más, x86-64 y arm64). JDK 17 o más
  para empaquetar (`jpackage`); 11 o más para ejecutar. Ver la [tabla de compatibilidad](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html).
- **Kotlin 2.2.20**: el repositorio ya está en esa versión (Compose Multiplatform 1.12 pide al menos la 2.1.0 y recomienda la 2.2.20
  para las plataformas que más cambian), con el compilador de
  Compose como plugin (`org.jetbrains.kotlin.plugin.compose`), Gradle 8.14.3 y el plugin de Android 8.13.2 (el primero cuyo lint
  entiende Kotlin 2.2). Un mismo build de Gradle: los módulos de escritorio van aquí.
- Las apps de Android ya están hechas con Compose: mucha interfaz se puede llevar casi tal cual.

## Lo que ya sirve en escritorio

- **`nucleo/`** (Kotlin puro, sin nada de Android): la cuenta entera. `Sesion` (entrar, renovar el pase), `Coleccion` (campo a
  campo con la hora de cada campo), `Familia` (cola en disco, subida en lote, bajada por `u`, escucha en directo de los sellos,
  imágenes por hash, claves de `/claves/<uid>`), `Fechas`, `Json`, `Red`/`RedJava`, `Disco`/`DiscoArchivos`. Se usa igual que en
  Android: `Familia(Config(), RedJava(), DiscoArchivos(dir), discoSeguro)`. `Config()` trae ya el proyecto de siempre.
- Sus pruebas van contra el **emulador oficial de Firebase** con las reglas de verdad: `herramientas/emulador.sh` y
  `testFixtures(project(":nucleo"))` (el `Emulador`: crear cuentas, simular otro dispositivo, leer y escribir en crudo). Las pruebas de
  escritorio deberían ir igual.
- **`cobalto/…/EscribirMezcla.kt`** (la mezcla a tres bandas de los escritos) no usa nada de Android: se puede pasar a un módulo
  común y usarla en los dos. `EscribirTexto.kt` y `EscribirEstructura.kt` casi igual. Los casos compartidos de `herramientas/casos/`
  (`mezcla.json`, `estructura.json`, `tipografia.json`, `sinonimos.json`, `guia.json`) los tiene que pasar cualquier versión.
- **Azabache**: cómo se ve un juego o un libro de la cuenta como ficha de Azabache, y cómo se escribe solo lo que cambias, está en
  `azabache/…/Cosas.kt`; lo propio de Azabache (categorías, diario, sagas…), en `Propias.kt`. Son el modelo a seguir (o a pasar a un
  módulo común).

## Lo que hay que hacer en escritorio (lo que en Android hace `familia/`)

| En Android (`familia/`) | En escritorio |
|---|---|
| `LaFamilia`: una cuenta por app, escuchar en directo mientras se ve, avisar a la interfaz de cada cambio | Igual, sin Android: una `Familia` por app; `escucha()` con la ventana a la vista y `deja()` al ocultarla; `Coleccion.alCambiar` → estado de Compose |
| `DiscoCifrado` (el pase de la cuenta y las claves, cifrados con el almacén de claves de Android) | El llavero del sistema: Llavero (Keychain) en el Mac; Secret Service (libsecret) en Linux |
| `Acceso` (entrar una vez: la contraseña pasa de una app a otra, solo entre apps con la firma de la familia) | Un solo elemento «familia» en el llavero del sistema, que leen las cuatro apps del mismo usuario |
| `SubidaFamilia` (WorkManager: lo pendiente sube aunque la app esté cerrada) | Subir lo pendiente al cerrar la ventana y al abrir; la cola está en disco, no se pierde |
| `PortadasFamilia` (reducir a 600×900 en JPEG 85; el hash, 40 hexadecimales del SHA-256 de los bytes) | Lo mismo con ImageIO o Skia. **El mismo hash**: una portada es la misma imagen en todas |
| `Actualizador` (baja `<App>-<versión>.apk` de «Mi carpeta/versiones» en kDrive y la instala) | Por sistema. Los nombres de escritorio no pueden acabar en `.apk` (los actualizadores de Android solo cogen `^<App>-<versión>\.apk$`): p. ej. `Cobalto-1.0.0-mac.dmg`, `Cobalto-1.0.0-linux.AppImage` |

- **Firmar en el Mac**: una app sin firmar y sin notarizar sale con aviso de Gatekeeper (se puede abrir igualmente, a mano). Firmarla y
  notarizarla pide el programa de desarrolladores de Apple, que es de pago: díselo al dueño antes de contar con ello.
- **kDrive**: la misma API de Infomaniak (HTTP); el token y el número del kDrive están en `/claves/<uid>` (`kdrive_token`, `kdrive_id`).
  Las demás claves de servicios, también ahí (ver `ESQUEMA.md`).

## Lo propio de cada app

- **Hiedra** (el lanzador de juegos del PC): `juegos/<id>` con sus lanzadores (`ln`); las horas de cada fuente en `tiempo/<id>~<fuente>`
  (`fuente` = el dispositivo, `steam`, `pc:<pc>`…; lo de un juego es la suma). Hoy, «Tu PC» de Hiedra Android lee lo que publica
  **Atrio del PC** en `suite/` (el PC en directo, su biblioteca, sus capturas, las órdenes). Si Hiedra de escritorio lo sustituye, o
  publica lo mismo en `suite/`, o se acuerda un formato nuevo y se cambia Hiedra Android a la vez.
- **Marfil**: `libros/<kid>` (`kid` = 20 hex del SHA-1 de `libro:<título>:<autor>` normalizados; Azabache usa la misma clave, ver la
  prueba `laClaveDeUnLibroEsLaQueUsaAzabache`), `lectura/<kid>` (por dónde vas), `marcas/<kid>~<id>` (subrayados y notas), `tiempo/…`.
  El archivo del libro está en kDrive (el campo `nube` del libro: `{f, n, s}`); dónde está en ese ordenador es solo de ese ordenador.
- **Cobalto**: los escritos según `ESCRITOS.md` (los mismos documentos que hoy usan Cobalto Android y Atrio del PC: lo que escribes en
  uno está en los otros). Para las demás apps publica `obras/`, `capitulos/` y `palabras/` (ver `ESQUEMA.md`).
- **Azabache**: valora en el nodo de cada cosa (`nota`, `crit`, `resena`, `az`, `azid`); lo suyo, en `azabache/<lista>~<id>`.

## Trabajar a la vez que Android

- **El mismo repositorio** (`Gonvegato/Familia-Revista`), **tu propia rama** y **carpetas nuevas** (por ejemplo `escritorio/hiedra`,
  `escritorio/marfil`…, y `escritorio/familia` para lo común de escritorio). No toques `hiedra/`, `marfil/`, `cobalto/`, `azabache/` ni
  `familia/`: son las de Android y las lleva otra instancia.
- **Lo compartido** (`nucleo/`, `nube/ESQUEMA.md`, `nube/ESCRITOS.md`, `nube/reglas.json`, `herramientas/casos/`) no se cambia sin
  acordarlo. Si hace falta algo nuevo en la cuenta: **se añade** un campo o una colección (nunca se cambia lo que significa uno que
  existe), se apunta en `ESQUEMA.md` y, si hace falta, en las reglas. Nunca `null` (un campo vacío es `""`, `0` o `false`).
- **Probar contra el emulador**, con las reglas del repositorio, antes de dar nada por bueno. Y entre apps: lo que escribe la versión
  de escritorio lo tiene que leer la de Android (las pruebas de Android usan el `Emulador` para hacer de «otro dispositivo»).

## Cómo está hoy la cuenta

- En colecciones campo a campo: `juegos`, `libros`, `lectura`, `marcas`, `tiempo`, `obras`, `capitulos`, `palabras`, `sagas`, `ajustes`,
  `azabache`. Imágenes en `familia_img`, claves en `/claves/<uid>`.
- Los escritos, en `escritos` y `escritosIdx` (`ESCRITOS.md`).
- De Atrio del PC, mientras siga: `suite/` (lo del PC en directo).
- Los nodos de antes de la familia (`hiedra/dev`, `marfil/dev`, `dev`, `stamps`…) siguen ahí, sin usarse: no los leas para nada nuevo.

Versiones de Android a día de hoy: Hiedra 0.5.2, Marfil 0.13.3, Cobalto 0.3.1, Azabache 1.5.0.
