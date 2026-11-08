# Shai

Shai (el dios egipcio del destino, «lo que está ordenado») es el acompañante de tus modos carrera de **EA Sports FC 26**: tú juegas el
campo y Shai escribe lo de fuera. De la familia (la cuenta, la revista, el actualizador de kDrive), pero Jonsu no enseña nada suyo. Solo
Android. Paquete `com.gonzalo.shai`; icono «SHAI · CARRERA» en césped de noche (#0F2A1D), crema (#F2E8D0) y el dorado de un trofeo (#C9A227).

## 0.2.0

Lo que pidió Gonzalo tras probar el modo prueba («todo tiene que estar más currado») y lo que fue añadiendo:

- **Todo con contexto.** Los eventos y las decisiones dicen quién, dónde (qué medio, qué programa, qué sitio), qué se dijo (con la cita) y por qué
  importa, y qué te juegas; las opciones son frases concretas, cada una con su consecuencia. La red tiene posts largos con respuestas debajo,
  citas con comentario y la tendencia del día con sus posts; los mensajes son conversaciones (también de grupo: «Los del barrio», «Familia»,
  «Vestuario»); la portada trae su noticia entera (se lee al tocarla) y titulares secundarios con su entradilla. Prompts y esquemas nuevos
  (Sonnet para la prensa, los eventos y las decisiones; Haiku para la red, los mensajes y la foto).
- **El modo prueba, reescrito a mano**: un banco de 16 eventos personales, 16 de imagen y dinero, 12 decisiones de prensa (según el resultado,
  si marcó y su nivel), 5 de vacaciones, cada uno con tres opciones y lo que pasa con cada una (y si no contestas); posts, respuestas,
  conversaciones y noticias según el resultado; sin repetir (lo que ya salió se apunta en la carrera, en `k<id>`).
- **Las decisiones caducan** al meter el partido siguiente (las que ya habías visto). No contestar también tiene consecuencias: las cuenta la prensa
  del partido siguiente («No contestaste»). Con dos abiertas, no se ponen más.
- **Lo que Shai recuerda**: cada 3 jornadas y al cerrar la temporada, un resumen (tramas abiertas, su gente, lo que decidió y lo que dejó pasar,
  hechos clave) que va siempre en el contexto. En Perfil, con «Rehacerlo ahora».
- **Cerrar la temporada** (en Cifras, con confirmación): el balance con tus cifras, premios de medios inventados, las vacaciones con su decisión
  y el mercado de verano; pasa a la temporada siguiente con un año más.
- **Quién eres**: al crear la carrera, el nivel de salida (canterano, promesa, titular, estrella, leyenda mundial: fama, afición y carisma de
  salida y dinero sugerido) y tu historia con tus palabras (que Claude respeta como tu pasado, premios reales incluidos). Editable en Perfil.
- **El alcance sube poco a poco**: el nivel de ahora sale de la fama, que sube con lo que haces (partidos, goles, notas, decisiones, líos), con
  tope por jornada; los seguidores van de unos mil a unos doscientos millones. En Perfil: nivel, seguidores, para el siguiente y cómo ha subido.
- **La edad pesa**: a los 21 o menos un buen tramo es un terremoto (sube mucho más, tope de 7 por jornada); a los 28 o más, una buena racha (sube
  poco) y los malos tramos se leen como declive. Claude recibe la edad y cómo se le está viendo (sorpresa, confirmación, declive…).
- **Ofertas según tu nivel** (rangos de sueldo y prima por nivel, que se pasan a Claude y se recortan si se salen) y **la tienda según tu
  dinero** (lo que puedes pagar y lo que tienes cerca; lo que no te llega, marcado; de un estudio y un coche de segunda mano a islas y aviones).
- **Contratos de Shai** (no los de FC): el primero según nivel y club (sueldo, hasta qué temporada, prima, cláusula, prima por gol, derechos de
  imagen; objetivos de la directiva si eres míster). De ahí sale lo que cobras cada jornada. Renovaciones con lógica (al quedar una temporada o
  temporada y media, o cuando tu nivel ya está muy por encima de lo que cobras: entonces tu representante te pide permiso), con «pedir más» una
  vez; un traspaso trae su contrato; si se acaba sin renovar, quedas libre; una cesión vuelve a su club.
- **Récords e hitos** de tu carrera en Shai (debut, primer gol, 10/25/50/100… goles, hat-tricks, rachas marcando e invicto, partidos 50/100…,
  primeras veces en competiciones grandes, récord de goles en una temporada, títulos que metas en la plantilla) y la **precocidad** (los que
  consigues muy joven, marcados). La red y la prensa los celebran; nunca se dice que bata un récord del mundo real. En Cifras, «Récords e hitos».
- **Coste estimado** (no medido: no se ha hecho ninguna llamada real): unos 0,13 $ por jornada —la red con Haiku ~0,003 $ (≈3.500 tokens de
  entrada a 0,10 $/M y ≈5.000 de salida a 0,50 $/M), la prensa con Sonnet ~0,04 $ (≈4.500 de entrada a 2 $/M y ≈3.000 de salida a 10 $/M), los
  dos eventos por lotes ~0,035 $ (≈2 × 0,034, a mitad de precio), contestar una decisión y media ~0,045 $ y la memoria ~0,006 $—; cerrar una
  temporada, unos 0,05 $. Con 100 $ al mes, unas 750 jornadas.

## 0.1.1

- **Modo prueba (sin IA)**, en Ajustes → IA: toda la app sin clave y sin gastar. Lo de fuera del campo lo escriben unas plantillas de
  ejemplo (no Claude) con los nombres de tu carrera, tu gente, el rival y el marcador; lo demás es la app de verdad (dinero, bolsa,
  decisiones, ofertas, los dos eventos de entre partidos, que en este modo llegan al momento). Sus carreras van aparte, solo en este móvil
  (no tocan las de tu cuenta), con un aviso visible en cada pantalla y un botón para borrarlas. No hace falta ni entrar en la cuenta.

## 0.1.0

Lo que pidió Gonzalo, tal cual quedó hablado:

- **Varias carreras**, cada una un universo aparte: de jugador o de míster, con su club, su liga y su temporada.
- **El personaje**: nombre, apodo, edad, nacionalidad, posición, dorsal, su foto (solo en el móvil), la media y las cifras de su carta de FC
  (las pones tú, de tu partida: Shai no se inventa nada del campo; la media nueva se apunta en la plantilla completa o en el perfil y queda su
  historial), su forma de ser (de serie, «ambicioso, fiestero y mujeriego») y su gente (familia, representante, amigos, ex, ligues…:
  siempre inventada, con su afinidad).
- **Cada partido**, de tres maneras: **rápido** (resultado, rival, competición, en casa o fuera, minutos, goles, asistencias y nota; de míster,
  resultado y goleadores), **completo** (además, los datos del partido de los dos equipos —posesión, tiros, tiros a puerta, precisión de pase,
  faltas, córners: los que se ven en «Datos del partido» de FC; no hay lista oficial de EA de todas sus cifras, así que no se inventan
  categorías—, lo demás del rendimiento con tus palabras, lo que pasó, la media, la temporada y la fecha) o **con una foto** de esa pantalla
  (la lee Haiku y tú lo revisas antes de guardar).
- **Lo de después**, escrito por Claude: la red (como X: posts con su @, megusta y reposts), los mensajes privados, la portada del día
  siguiente (un diario inventado), las ofertas («la app manda»: si aceptas aquí, lo haces en FC; aceptar un traspaso cambia el club y el
  sueldo y la red reacciona) y, si toca, una decisión.
- **Entre partidos**, dos cosas que pasan mientras tanto (una de su vida y su gente, otra de su imagen y su dinero), casi siempre con una
  decisión. Van por **lotes** (Batch API, a mitad de precio): un trabajo de fondo mira cada cuarto de hora si han vuelto; la primera sale al
  llegar y la segunda seis horas después (si metes otro partido antes, salen ya), con su aviso. Y el botón «¿Qué ha pasado esta semana?»
  para pedir otra al momento.
- **Decisiones con consecuencias**: dos o tres opciones o lo que tú escribas; Claude cuenta lo que pasa y cambia la fama, la afición, la
  disciplina, el carisma, tu gente y el dinero (con límites: de −10 a +10 cada cosa, tu gente de −15 a +15, el dinero hasta el 20 % de tu
  patrimonio).
- **La vida**: el dinero en euros (cada partido es una jornada: cobras el sueldo de la semana, tus negocios dan lo suyo y tus casas y
  coches cuestan lo suyo), la bolsa (ocho empresas inventadas que se mueven cada jornada, siempre igual para la misma carrera y jornada),
  casas, coches y negocios que comprar y vender (todo inventado, ni una marca real).
- **Las cifras**: por temporada y por competición (partidos, V-E-D, goles, asistencias, minutos, nota media), la racha y cada partido.

### Las reglas del narrador (el prompt de sistema, en `Narrador.REGLAS`)

El campo es tuyo (nada de lesiones, suplencias, sanciones ni resultados inventados); las personas reales solo de pasada y en público, en tono
neutro o amable; lo íntimo, lo escandaloso y lo de pareja, siempre con personajes inventados; el protagonista, mujeriego (con mujeres
inventadas y mayores de edad, a nivel de prensa rosa, sin contenido sexual explícito); medios, marcas y empresas inventados.

### La IA y el gasto

- Por HTTP directo a la API (`Claude.kt`, la de «cURL / raw HTTP» de la documentación), versión `2026-10-21`. El SDK oficial de Java
  (`anthropic-java` 3.3.0) se probó: compila y pasa R8 (con `-dontwarn com.google.auto.value.AutoValue`), pero lleva Jackson (reflexión) y
  OpenTelemetry, y aquí no se puede probar en un Android de verdad; con HTTP, lo que prueban los tests es lo que corre en el móvil.
- **Haiku 5.5** (`claude-haiku-5-5`; 0,10 $ / 0,50 $ por millón de tokens de entrada y salida con prompts de hasta 100.000) para la red,
  los mensajes y la foto, con el pensamiento apagado y esfuerzo bajo. **Sonnet 5.5** (`claude-sonnet-5-5`; 2 $ / 10 $) para la portada,
  las ofertas, las decisiones, lo de entre partidos y el principio de cada carrera, con esfuerzo bajo (en Sonnet 5.5 el pensamiento no se
  puede apagar con `disabled`). Todo en **salida estructurada** (`output_config.format`, un esquema JSON por cosa) y con el prompt de
  sistema en **caché** (5 minutos; una hora en los lotes).
- El gasto se cuenta con el `usage` de cada respuesta y esos precios (caché escrita 1,25× o 2×, leída 0,1×; lotes, la mitad), por mes, en
  tu cuenta (`shai/gasto`, un campo por mes, dispositivo y modelo). Antes de cada petición se mira que, con lo que podría costar como
  mucho, no se pase del **tope** (de serie 100 $, en Ajustes → IA); al llegar, no se pide nada más (los partidos se guardan igual, con lo
  que falta para pedirlo luego). Es la cuenta de Shai: la de verdad está en Claude Console.
- La clave: la pegas en Ajustes → IA y va solo a `/claves/<uid>` (`shai_anthropic`), en la cabecera `x-api-key`.

### Lo que no se ha podido probar aquí

Las llamadas de verdad a la API (las pruebas usan una API de mentira: nada sale ni se gasta), el trabajo de fondo y los avisos en un móvil
de verdad, y la lectura de fotos reales de FC 26.
