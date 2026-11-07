# La reconstrucción (plan y estado)

Lo que se pidió: sincronización total en la cuenta (sin copias entre apps), las claves en la cuenta y gestionadas desde
Jonsu, entrar una vez, una sola firma, actualizaciones automáticas limpias. «Sin valorar» (nunca 0), la portada de la ficha de
Jonsu pequeña, y Hiedra/Azabache/Cobalto preparadas para pasar al PC más adelante.

## Decisiones

- **Firebase de siempre** (mismo proyecto, Realtime Database + Authentication, plan gratuito). Nada de pago.
- **Núcleo en Kotlin puro** (`nucleo/`): REST de Firebase, colecciones campo a campo con la hora de cada campo (`m`) que las
  reglas comprueban, cola en disco, bajada por `u`, sellos en directo. Sirve igual en el PC. Probado contra el emulador oficial.
- **Android común** (`familia/`): `LaFamilia` (una cuenta por app), `Acceso` (entrar una vez: la contraseña cifrada en el móvil,
  solo para apps con el certificado de la familia, comprobado con `hasSigningCertificate`), trabajos de fondo.
- **Firma**: clave nueva `firma/familia.keystore` (fuera del repositorio) y linajes de rotación desde cada clave vieja
  (`apksigner rotate`), firmando con `--rotation-min-sdk-version 28`: las versiones nuevas se instalan encima de las viejas.
  El actualizador viejo rechaza otra firma (compara `apkContentsSigners`, que no incluye el linaje): el primer salto se instala a
  mano, y esas cuatro versiones van a «Mi carpeta/familia-a-mano» del kDrive, no a «versiones» (si no, las apps viejas las bajarían
  cada 4 horas para rechazarlas). El actualizador nuevo mira el historial de firmas (`signingCertificateHistory`): una rotación futura
  ya no lo rompe.
- **Portadas en la cuenta** (`familia_img/<hash>`), no en kDrive. kDrive solo para lo grande (libros, ROMs, partidas, versiones).
- **Claves** en `/claves/<uid>`; si una app tenía el token de kDrive guardado en el móvil, lo sube una vez a la cuenta.
- **Migración**: cada app, la primera vez en cada dispositivo, lee sus nodos viejos y los escribe en las colecciones con la hora
  original de cada cambio (así, migre quien migre primero, gana lo más reciente). Los nodos viejos no se tocan.
- **Escribir (Thot)**: los documentos de `escritos/` siguen como están (los comparte Atrio del PC y tienen su mezcla a tres
  bandas); lo que leen las demás (obras, capítulos terminados, palabras) pasa a colecciones.
- **Atrio del PC**: sigue con sus nodos (`suite/`); «Tu PC» de Bastet no cambia.

## Estado

- [x] Monorepo con las cuatro apps
- [x] Núcleo + pruebas contra el emulador (18/18)
- [x] Reglas (`nube/reglas.json`) y esquema (`nube/ESQUEMA.md`), a completar con cada app
- [x] Módulo `familia` (cuenta, acceso, trabajos de fondo)
- [x] Clave común y linajes
- [x] Seshat sobre el núcleo (+ migración) — 5 pruebas de extremo a extremo
- [x] Bastet sobre el núcleo (+ migración, repetidos unidos) — 4 pruebas de extremo a extremo
- [x] Thot: obras, capítulos y palabras en colecciones
- [x] Jonsu: sin copias, valoración en el nodo de cada cosa, «Sin valorar», claves en la cuenta, portada pequeña en la ficha — 6 pruebas (también: un token mal pegado no pisa el bueno; una clave cambiada en otra app no vuelve a la vieja)
- [x] Actualizaciones sin preguntar: targetSdk 35 (Android 17 lo exige) y el permiso UPDATE_PACKAGES_WITHOUT_USER_ACTION (faltaba en
      Bastet, Seshat y Thot); el token de kDrive sale de la cuenta
- [x] Firma con rotación (`herramientas/firmar.py`), comprobada con `apksigner verify` y `apksigner lineage`
- [x] Versiones Bastet 0.5.0, Seshat 0.13.1, Thot 0.3.0, Jonsu 1.3.0 (la 0.13.0 de Seshat había perdido la subida sola de los
      libros a kDrive: la 0.13.1 la devuelve, con una prueba de extremo a extremo)
- [x] Jonsu 1.5.0: lo propio (categorías, diario, sagas, maquetas, lista negra…) también se lee y se escribe en la cuenta, sin
      copia (`Propias.kt`). Los escritos de Thot ya estaban en la cuenta; su contrato, ahora en `nube/ESCRITOS.md`.
- [x] Kotlin 2.2.20 en todo el repositorio (para compartir el núcleo con Compose Multiplatform) y el plugin de Android 8.13.2 (con el
      8.5.2, el lint no entendía Kotlin 2.2). Traspaso: `docs/ESCRITORIO.md`.
- [x] Jonsu 1.4.0: sin copias. Sus juegos y libros son los nodos de la cuenta, leídos tal cual (`Cosas.kt`); escribe solo lo que
      cambias (la diferencia entre la lista que tenías y la nueva); lo que solo estaba en Jonsu pasó a la cuenta común.
- [x] Bastet 0.5.2 y Seshat 0.13.3: las fechas traídas de Atrio (número de día tomado por milisegundos: 1970) se corrigen.
- [x] Bastet 0.5.1, Seshat 0.13.2, Thot 0.3.1, Jonsu 1.3.1, tras probarlas en el móvil: Jonsu se quedaba sin la cuenta si
      entrabas en otra app con ella abierta (y enseñaba lo de la 1.2); salir de la cuenta dejaba las apps oyendo colecciones muertas;
      el reto de Seshat no contaba los leídos sin fecha; las pestañas volvían solas y la fila de orden no se dejaba mover; los
      estantes de Bastet saltaban con los títulos de dos líneas. Cada cosa con su prueba (las de la cuenta, contra el emulador).

## Lo que no se ha podido probar aquí

- En un móvil de verdad: instalar encima de la versión vieja (la rotación de firma solo se ha comprobado con apksigner), entrar una
  vez y que las demás entren solas (necesita las cuatro instaladas con la firma nueva), la instalación sin preguntar, los widgets.
- Contra tu Firebase de verdad: todo se ha probado contra el emulador oficial con las mismas reglas.
- Lo de tu pareja en Jonsu: desde la 1.4.0 sus juegos y libros salen de su cuenta (con sus notas); probado contra el emulador solo con una cuenta.
