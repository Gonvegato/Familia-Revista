# Lo que Jonsu lee (y lo que escribe)

Jonsu es la revista de la familia: **no añade juegos ni libros**. Los lee de lo que sus hermanas suben a tu cuenta de Firebase y los
refleja en sus propias listas, donde se valoran. Los contratos de cada una están en sus repos:

- Bastet (juegos): `hiedra/docs/CONTRATO-DATOS.md` → `/users/<uid>/hiedra/`
- Seshat (libros): `lectura-app/docs/CONTRATO-DATOS.md` → `/users/<uid>/marfil/`
- Thot (lo escrito): `cobalto/docs/CONTRATO-DATOS.md` → `/users/<uid>/cobalto/`

## Lo que lee

| De | Nodo | Para qué |
|---|---|---|
| Bastet | `stamps`, `dev/<dispositivo>/data` | La biblioteca de juegos: se mezclan los dispositivos por `id` (gana el `mod` más reciente; las horas `msp` se suman por dispositivo y las `ex` por máximo). Cada juego pasa a una ficha de Jonsu: `PASADO` → valorado; `JUGANDO`/`PENDIENTE`/`ABANDONADO` → en curso (estados 1/0/2). |
| Bastet | `tiempo/<id>/<fuente>` | Lo jugado cada día (las fuentes se suman), para el Diario y el calendario. |
| Bastet | `nube/portadas/<id>` | La portada que pusiste tú de la galería (se baja de kDrive si Jonsu tiene tu token). Si no hay, la dirección que viaja en la biblioteca: `pu` (la que elegiste de internet) o `pa` (la caja automática de libretro, Bastet 0.3.0). |
| Seshat | `stamps`, `dev/<dispositivo>/data` | La biblioteca de libros: por clave `k`; `LEIDO` → valorado (con la última fecha de `tm`), `LEYENDO`/`PENDIENTE` → en curso. |
| Seshat | `tiempo/<kid>/<escritor>` | Lo leído cada día: por dispositivo y día el máximo entre escritores, y luego se suman los dispositivos. |
| Seshat | `subrayados/<kid>/<escritor>` | Los subrayados de cada libro (`n`: `x` texto, `o` nota, `c` creado, `l` etiqueta, `k` sitio); se unen por `k`+`c` entre escritores. La cita, el Diario, la ficha y el buscador. |
| Seshat | `portadas/<kid>` | Tu portada en kDrive (`f`, `u`; `x` = quitada). Si no hay (o no hay token), `pu` de la biblioteca: la dirección de la que elegiste de internet (Seshat 0.12.0). |
| Thot | `palabras/<dispositivo>` | Las palabras escritas cada día (se suman los dispositivos): el Diario. |
| Thot | `lectura/<obra>/<capítulo>` | Los capítulos terminados, para «Escritos» (con Seshat en el móvil, se leen allí: Jonsu abre Seshat con el extra `go=escritos`). |

Se mira al abrir la app, al vincular la cuenta, al entrar en el Diario y en cuanto una hermana sube algo (se escucha `stamps` de las tres y `lstamps` de Seshat; al oír el sello de una, solo se baja lo de esa; al oír `lstamps` de Seshat, solo `tiempo` y `subrayados`). Cada hermana escribe sus nodos ligeros **antes** de sellar.

## Cómo se enlaza

- Una ficha reflejada lleva un **id fijo que sale del nombre** (el de siempre de las fichas propias, `customItemId`): dos móviles con
  Jonsu crean la misma y al sincronizar no se duplican. Lo que ya tenías con el mismo nombre se enlaza y se completa.
- El enlace id de Bastet → id de aquí (y clave de Seshat → id) se guarda en el móvil (`hermanas`), y es lo que usan el Diario, las portadas y «Jugar»/«Leer» (abren Bastet con los extras `ficha=<id>` y `jugar=true`, y Seshat con `clave=<clave>`).
- Lo que manda la hermana: nombre, plataforma, autor, estado, horas, páginas, duraciones, fechas de empezado/pasado, favorito y la portada
  (solo si no la has cambiado tú). De Seshat también lo que llevas de cada libro (`fr`, 0–1), que se guarda aparte (`avance_marfil`) para el
  widget «Leyendo» y el aviso de lectura. Lo que es de Jonsu y nunca se toca: la nota, los criterios, la reseña, las casillas de la página.
- Una ficha reflejada sin nota tiene todas las notas a **0**: es «sin valorar» (se ve así en su página, no cuenta en las cifras). Una ficha ya
  valorada no vuelve a «en curso» aunque la hermana diga que la rejuegas o relees: la nota manda.
- **Verse entre hermanas**: desde Android 11 una app no ve a otra (`getLaunchIntentForPackage` da null) si no la declara en `<queries>` de su
  manifiesto. Cada hermana declara a las demás (`com.gonzalo.hiedra`, `com.gonzalo.marfil`, `com.gonzalo.cobalto`, `com.gonzalo.gamerate`);
  un test (`ManifiestoHermanasTest`) lo vigila. Sin esto, «Jugar en Bastet», «Leer en Seshat» y «Escribir en Thot» no salen nunca en un móvil.

## Lo que escribe

Lo de siempre de Atrio, en `/users/<uid>/dev/<dispositivo>/data` y `stamps`: las fichas (con las reflejadas), las notas, el diario, las sagas,
las maquetas, la lista negra, las claves de servicios (`keys`: SteamGridDB, Steam, RAWG, IGDB; las usan Bastet y Seshat). Atrio del PC
sigue leyendo este mismo nodo. Jonsu **no escribe** en los nodos de las hermanas.

## Lo que Bastet y Seshat leen de Jonsu

La valoración: `general` y `review` de cada ficha (por nombre y por la clave `juego:<título normalizado>`), como hasta ahora con Atrio.

## Días seguidos (la racha)

La misma regla en Bastet, Seshat, Thot y Jonsu (`Racha.kt` en cada una, con las mismas pruebas, `RachaTest`):

- Un día **cuenta** si ese día jugaste o leíste **al menos un minuto**, o escribiste **al menos una palabra**. Cada app mira lo suyo
  (Bastet lo jugado, Seshat lo leído, Thot lo escrito); Jonsu, las tres a la vez (basta con una).
- La racha son los días seguidos que cuentan **hasta hoy**; si hoy todavía no cuenta, **hasta ayer** (no se rompe a primera hora).
- Los días son los del calendario del móvil (`"AAAA-MM-DD"` en los nodos de tiempo y de palabras).
