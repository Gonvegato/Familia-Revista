# Shai

Shai (el dios egipcio del destino, «lo que está ordenado») es el acompañante de tus modos carrera de **EA Sports FC 26**: tú juegas el
campo y Shai escribe lo de fuera. De la familia (la cuenta, la revista, el actualizador de kDrive), pero Jonsu no enseña nada suyo. Solo
Android. Paquete `com.gonzalo.shai`; icono «SHAI · CARRERA» en césped de noche (#0F2A1D), crema (#F2E8D0) y el dorado de un trofeo (#C9A227).

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
