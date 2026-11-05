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
  El actualizador viejo rechaza otra firma: el primer salto se instala a mano.
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
- [ ] Marfil sobre el núcleo (+ migración)
- [ ] Hiedra sobre el núcleo (+ migración)
- [ ] Cobalto: obras, capítulos y palabras en colecciones
- [ ] Azabache: sin copias, valoración en el nodo de cada cosa, «Sin valorar», gestor de claves, portada pequeña en la ficha
- [ ] Actualizador común (token de la cuenta, targetSdk alto para instalar en silencio en Android 17)
- [ ] Firma con rotación en la compilación de release
- [ ] Pruebas de extremo a extremo y versiones nuevas de las cuatro
