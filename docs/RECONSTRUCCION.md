# La reconstrucción (plan y estado)

Lo que se pidió: sincronización total en la cuenta (sin copias entre apps), las claves en la cuenta y gestionadas desde
Azabache, entrar una vez, una sola firma, actualizaciones automáticas limpias. «Sin valorar» (nunca 0), la portada de la ficha de
Azabache pequeña, y Hiedra/Azabache/Cobalto preparadas para pasar al PC más adelante.

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
- **Escribir (Cobalto)**: los documentos de `escritos/` siguen como están (los comparte Atrio del PC y tienen su mezcla a tres
  bandas); lo que leen las demás (obras, capítulos terminados, palabras) pasa a colecciones.
- **Atrio del PC**: sigue con sus nodos (`suite/`); «Tu PC» de Hiedra no cambia.

## Estado

- [x] Monorepo con las cuatro apps
- [x] Núcleo + pruebas contra el emulador (18/18)
- [x] Reglas (`nube/reglas.json`) y esquema (`nube/ESQUEMA.md`), a completar con cada app
- [x] Módulo `familia` (cuenta, acceso, trabajos de fondo)
- [x] Clave común y linajes
- [x] Marfil sobre el núcleo (+ migración) — 5 pruebas de extremo a extremo
- [x] Hiedra sobre el núcleo (+ migración, repetidos unidos) — 4 pruebas de extremo a extremo
- [x] Cobalto: obras, capítulos y palabras en colecciones
- [x] Azabache: sin copias, valoración en el nodo de cada cosa, «Sin valorar», claves en la cuenta, portada pequeña en la ficha — 6 pruebas (también: un token mal pegado no pisa el bueno; una clave cambiada en otra app no vuelve a la vieja)
- [x] Actualizaciones sin preguntar: targetSdk 35 (Android 17 lo exige) y el permiso UPDATE_PACKAGES_WITHOUT_USER_ACTION (faltaba en
      Hiedra, Marfil y Cobalto); el token de kDrive sale de la cuenta
- [x] Firma con rotación (`herramientas/firmar.py`), comprobada con `apksigner verify` y `apksigner lineage`
- [x] Versiones Hiedra 0.5.0, Marfil 0.13.1, Cobalto 0.3.0, Azabache 1.3.0 (la 0.13.0 de Marfil había perdido la subida sola de los
      libros a kDrive: la 0.13.1 la devuelve, con una prueba de extremo a extremo)
- [x] Hiedra 0.5.1, Marfil 0.13.2, Cobalto 0.3.1, Azabache 1.3.1, tras probarlas en el móvil: Azabache se quedaba sin la cuenta si
      entrabas en otra app con ella abierta (y enseñaba lo de la 1.2); salir de la cuenta dejaba las apps oyendo colecciones muertas;
      el reto de Marfil no contaba los leídos sin fecha; las pestañas volvían solas y la fila de orden no se dejaba mover; los
      estantes de Hiedra saltaban con los títulos de dos líneas. Cada cosa con su prueba (las de la cuenta, contra el emulador).

## Lo que no se ha podido probar aquí

- En un móvil de verdad: instalar encima de la versión vieja (la rotación de firma solo se ha comprobado con apksigner), entrar una
  vez y que las demás entren solas (necesita las cuatro instaladas con la firma nueva), la instalación sin preguntar, los widgets.
- Contra tu Firebase de verdad: todo se ha probado contra el emulador oficial con las mismas reglas.
- Lo de tu pareja en Azabache: se lee de su cuenta nueva (lo suyo de Azabache); sus juegos y libros reflejados aún no salen en su página.
