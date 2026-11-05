# Contrato de datos de Cobalto (lo que otras apps pueden leer)

Cobalto es la app de escritura. Es el hermano del contrato de Marfil (`lectura-app/docs/CONTRATO-DATOS.md`) y de Carmesí
(`Android-Launcher/docs/CONTRATO-DATOS.md`): mismos nombres y mismas ideas. Cobalto escribe; Azabache (y quien venga) **lee**.

## Las obras: los nodos de Escribir, sin migración

Lo que escribes vive donde ya lo tenía Atrio: `/users/<uid>/escritos/<id>` (un documento por obra, parte, capítulo, escena, ficha,
nota…) y `/users/<uid>/escritosIdx/<id>` (su hora de edición, el índice que se escucha en directo). El formato es el de
`Atrio-Suite-2.0/docs/escribir.md` y lo comparten Cobalto, Atrio del móvil y Atrio del PC: los tres escriben en los mismos documentos y
mezclan a tres bandas. **Nadie más debe escribir ahí**, y Azabache no debería leer de ahí (son los documentos de trabajo, pesados y con
todo lo privado: notas, fichas, metas…). Lo que Azabache necesita está en los nodos ligeros de abajo.

Cobalto añade a la obra (`p`) un campo propio, `est`: `"pausa"` (en pausa) o `"fin"` (terminada); sin él, se está escribiendo. `arch` = 1
(archivada) ya existía. Atrio del PC no mira `est` y no le molesta.

## Identidad de una obra

La obra se identifica por el `id` de Escribir (`p_` + 12 hex), el mismo en todos los dispositivos. Un capítulo, por su `id` (`c_…`).
Azabache debe enlazar la valoración o la reseña de una obra por ese `id`.

## Nodos bajo `/users/<uid>/cobalto/`

| Nodo | Contenido | Quién escribe |
|---|---|---|
| `dev/<dispositivo>/data` | Lo de ese dispositivo (texto JSON `v:1`): por ahora solo `aj`, la apariencia (gana la más reciente entre tus Cobaltos). | cada Cobalto, solo el suyo |
| `stamps/<dispositivo>` | Hora de la última subida de ese dispositivo (servidor). También se sella cada vez que cambia `lectura` o `palabras` (0.2.1): quien lo escucha (Azabache, Marfil) se entera al momento. | cada Cobalto |
| `lectura/<obra>/<capítulo>` | Una copia de **solo lectura** del texto de cada capítulo marcado como **terminado** («hecho» en el capítulo o en su parte): `{t, a, c, n, pa, u, p, x}` = título de la obra, autor, título del capítulo, su número en el libro, el título de su parte (si la tiene), hora de la última edición, palabras y `x` el texto (Markdown ligero: `# `, `## `, `> `, `*cursiva*`, `**negrita**`, `***` = salto de escena). Al quitar la marca de terminado, la copia se quita. Es lo que lee Azabache para «Escribir» (solo los capítulos completados). | cualquier Cobalto |
| `palabras/<dispositivo>` | Las palabras escritas cada día en ese dispositivo (`"AAAA-MM-DD"` → palabras). **Ligero**: es lo que debe leer un Diario. Cada dispositivo escribe solo lo suyo (y lo escrito en Atrio del PC va con el dispositivo del PC de Atrio, no aquí: ver abajo). | cada Cobalto |

## Cómo leer las palabras para un Diario

Para cada día, lee `palabras/*` y **suma** los dispositivos (cada uno trae solo lo suyo). Lo que se escribió en Atrio del PC o en Atrio
del móvil antes de Cobalto está en los registros del día de Escribir (`escritos/l_<obra>_<día>`, campo `dev`), que Cobalto no vuelve a
publicar; Azabache ya sabe leerlos.

## La valoración

En Cobalto no se valora. Azabache es la dueña de la valoración y de la reseña de una obra, enlazadas por el `id` de la obra.

## Lo que aún no está (se añadirá cuando Azabache lo pida)

- Las copias de seguridad completas en el móvil (el PC las tiene en `escritos-copias`): por ahora, «Guardar copia de mis obras» en Ajustes.

## Verse entre hermanas

- **Verse entre hermanas**: desde Android 11 una app no ve a otra (`getLaunchIntentForPackage` da null) si no la declara en `<queries>` de su
  manifiesto. Cada hermana declara a las demás (`com.gonzalo.hiedra`, `com.gonzalo.marfil`, `com.gonzalo.cobalto`, `com.gonzalo.gamerate`);
  un test (`ManifiestoHermanasTest`) lo vigila. Sin esto, «Jugar en Hiedra», «Leer en Marfil» y «Escribir en Cobalto» no salen nunca en un móvil.

## Días seguidos (la racha)

La misma regla en Hiedra, Marfil, Cobalto y Azabache (`Racha.kt` en cada una, con las mismas pruebas, `RachaTest`):

- Un día **cuenta** si ese día jugaste o leíste **al menos un minuto**, o escribiste **al menos una palabra**. Cada app mira lo suyo
  (Hiedra lo jugado, Marfil lo leído, Cobalto lo escrito); Azabache, las tres a la vez (basta con una).
- La racha son los días seguidos que cuentan **hasta hoy**; si hoy todavía no cuenta, **hasta ayer** (no se rompe a primera hora).
- Los días son los del calendario del móvil (`"AAAA-MM-DD"` en los nodos de tiempo y de palabras).
