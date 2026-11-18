# Mau · animación, pivotes y paleta

Coordenadas en unidades del viewBox (200 × 220; «escondido», 200 × 80). izq/der = como se ve en pantalla.

## Reglas generales

- Todo lo que se aplique a un id se aplica igual a `contorno-<id>` (misma transformación y mismo pivote). Lo que se aplique a `cuerpo`, se aplica también a `contorno`.
- `cuerpo` contiene el gato entero (cola, patas, collar, cabeza y accesorios), así que respirar o ronronear lo mueve todo junto.
- En VectorDrawable un `<group>` no tiene opacidad (solo giro, pivote, escala y desplazamiento). Si al convertir los ojos cerrados aparecen encima de los abiertos, controla `ojos-abiertos` / `ojos-cerrados` con escala Y 0 ↔ 1 (pivote «ojos» de la tabla de pivotes).
- Para apagar el contorno: escala 0 del grupo `contorno`, o quitar `<g id="contorno">…</g>` al generar la versión de fondo claro.
- El vector no dibuja fuera de su viewBox: un movimiento que tenga que salirse de él se aplica a la vista entera, no dentro del SVG.
- Android no admite guiones en nombres de recurso: al importar, `mau-sentado.svg` → `mau_sentado.xml`. Los ids con guion (`oreja-izq`) sí valen como `android:name`.

## Movimientos

| Movimiento | id (+ su contorno) | Pivote (en «sentado») | Tipo | Valores | Duración | Curva | Repetición |
|---|---|---|---|---|---|---|---|
| Parpadeo | ojos-abiertos → ojos-cerrados → ojos-abiertos | ojos (100, 80) | cambio de visibilidad (opacidad; en Android, escala Y 0 ↔ 1) | abiertos 1→0 y cerrados 0→1; luego al revés | 130 ms cerrado | instantáneo | cada 3–6 s al azar; 1 de cada 5, doble (con 120 ms entre ambos) |
| Parpadeo lento (ronroneando) | igual | (101.1, 81) | igual | igual | 600 ms cerrado | instantáneo | cada 4–5 s |
| Respirar | cuerpo + contorno | (100, 214) | escala Y | 1 → 1.015 → 1 | 3200 ms | cubic-bezier(.45, 0, .55, 1) | infinita (no en «escondido») |
| Respirar dormido | cuerpo + contorno | (104, 214) | escala Y | 1 → 1.03 → 1 | 4500 ms | cubic-bezier(.45, 0, .55, 1) | infinita |
| Cola (vaivén) | cola + contorno-cola | (138, 206) | giro | 0° → +6° → −4° → 0° | 2400 ms | cubic-bezier(.45, 0, .55, 1) | infinita, con pausa al azar de 0,8–2 s; en «leyendo» y «jugando», mitad de amplitud; en «durmiendo», sin vaivén |
| Cola (temblor, «tapándose») | cola + contorno-cola | (146, 196) | giro | 0 → +1.5° → −1.5° (×3) → 0 | 360 ms | lineal | cada 2,5 s |
| Orejas (tic) | oreja-izq u oreja-der + su contorno | (77, 61) / (123, 61) | giro | izq 0 → −12° → 0; der 0 → +12° → 0 | 90 ms ida + 140 ms vuelta | ida cubic-bezier(.2, .8, .4, 1); vuelta cubic-bezier(.4, 0, .6, 1) | una sola oreja cada 5–9 s al azar |
| Maullido («celebrando») | cabeza + contorno-cabeza, y cuerpo + contorno | cabeza (102.7, 108.9); cuerpo (100, 214) | giro + escala Y | cabeza 0 → −6° → −4° → 0; cuerpo 1 → 1.02 → 1 a la vez | 520 ms | cubic-bezier(.3, 1.4, .5, 1) | 2 maullidos seguidos (con 300 ms entre ellos) y pausa de 1500 ms, mientras dure la celebración |
| Ronroneo | cuerpo + contorno | (100, 214) | desplazamiento X | 0 → +0.6 → −0.6 → 0 | 80 ms por ciclo | lineal | mientras dure la caricia |
| Ronroneo (cabeza) | cabeza + contorno-cabeza | (96.7, 112.7) | giro | 0 → +3° → 0 | 1200 ms | cubic-bezier(.45, 0, .55, 1) | mientras dure la caricia |
| Asomarse («escondido») | cabeza + contorno-cabeza | — | desplazamiento Y | +80 → 0 | 380 ms | cubic-bezier(.2, .9, .3, 1.15) (rebote leve) | a demanda |
| Esconderse («escondido») | cabeza + contorno-cabeza | — | desplazamiento Y | 0 → +80 | 220 ms | cubic-bezier(.5, 0, .9, .4) | a demanda |
| De «escondido» a «sentado» | en mau-sentado: cuerpo + contorno | — | desplazamiento Y | +142 → 0 | 520 ms | cubic-bezier(.2, .9, .25, 1.08) | a demanda |
| De «sentado» a «escondido» | en mau-sentado: cuerpo + contorno | — | desplazamiento Y | 0 → +142, y al acabar se cambia a mau-escondido | 320 ms | cubic-bezier(.5, 0, .9, .4) | a demanda |
| Dormirse (de «sentado» a «durmiendo») | 1) ojos; 2) cabeza + contorno-cabeza; 3) cambio de dibujo | ojos (100, 80); cabeza (100, 112) | parpadeo + giro + desplazamiento + fundido | 1) tres parpadeos de 300, 450 y 600 ms con 500 ms entre ellos; 2) cabeza 0 → +10° y Y 0 → +6, quedándose con ojos-cerrados; 3) fundido cruzado a mau-durmiendo, que empieza a respirar dormido | 1) 2,9 s; 2) 900 ms; 3) 250 ms | 2) cubic-bezier(.45, 0, .55, 1); 3) lineal | una vez |
| Escribir | pata-der + contorno-pata-der, y pluma | (112, 150) los dos | giro | 0 → −5° → +3° → 0 | 700 ms | cubic-bezier(.45, 0, .55, 1) | infinita |
| Zarpazo («jugando») | pata-der + contorno-pata-der | (114, 168) | giro | 0 → −12° (levanta) → +3° (golpea) → 0 | 360 ms | cubic-bezier(.45, 0, .55, 1) | cada 1,4–2 s al azar |
| Ovillo rueda («jugando») | ovillo | (170, 197) | giro + desplazamiento X | giro 0 → +30° → 0; X 0 → +4 → 0 | 400 ms, empieza cuando la zarpa golpea | cubic-bezier(.2, .8, .3, 1) | con cada zarpazo |

Cómo encajan «escondido» y «sentado»: los dos se dibujan a la misma escala. En «escondido», el borde de la pantalla es y=80; en «sentado», el suelo y=214 se coloca en ese mismo borde. Con «sentado» desplazado +142, su cabeza coincide exactamente con la de «escondido». El contenedor tiene que recortar por el borde de la pantalla. Para los bordes laterales, gira la vista entera ±90°.

## Pivotes por postura

| Postura | cola | cabeza | oreja-izq | oreja-der | ojos | otros |
|---|---|---|---|---|---|---|
| sentado | (138, 206) | (100, 112) | (77, 61) | (123, 61) | (100, 80) | patas (92, 152) / (108, 152) |
| leyendo | (138, 206) | (100, 117) | (77, 66) | (123, 66) | (100, 85) | libro (100, 200) |
| escribiendo | (138, 206) | (96.8, 115.8) | (79.2, 62.7) | (125, 67.5) | (100.1, 84) | pata-der y pluma (112, 150) |
| viendo | (104, 207) | (100, 112) | (77, 61) | (123, 61) | — | palomitas (161, 214) |
| celebrando | (138, 206) | (102.7, 108.9) | (75.3, 60.1) | (121.2, 56.1) | (99.9, 77) | bufanda (111, 120) |
| tapándose | (146, 196) | (100, 114) | (77, 63) | (123, 63) | (100, 82) | — |
| durmiendo | (163, 205) | (83.5, 208.1) | (48.8, 164.2) | (93.5, 153) | (75.8, 177) | cuerpo (104, 214) |
| jugando | (64, 198) | (95.7, 151.7) | (80, 98) | (125.6, 104.4) | (100.1, 120) | patas (88, 162) / (114, 168); ovillo (170, 197) |
| ronroneando | (138, 206) | (96.7, 112.7) | (81, 59) | (126.6, 65.4) | (101.1, 81) | — |
| bostezando | (138, 206) | (104.3, 107.7) | (74.4, 60.4) | (120, 54) | (99.9, 76) | — |
| escondido | — | (100, 120) | (77, 69) | (123, 69) | — | — |

En todas, salvo «durmiendo», el pivote de cuerpo es (100, 214).

## Paleta definitiva (sin cambios respecto al encargo)

| Uso | Color |
|---|---|
| Pelo, pupila | #141414 |
| Pelo en luz (muslos, línea entre patas, borde de la cola o de la pata cuando pasan por delante del cuerpo) | #2A2626 |
| Interior de las orejas, interior de la boca | #3A2C2C |
| Ojos, ojos cerrados, colgante, mantequilla | #E8C34A |
| Brillo del ojo, rayas de la bufanda | #FFFFFF |
| Nariz, lengua, ovillo y su hebra, tapa del libro, rayas de las palomitas | #8A5252 |
| Bigotes, boca, líneas de texto, cañón de la pluma | #6A6A6A |
| Contorno | #4A4444 |
| Accesorios crema (con filo #141414 de 1,5 para que no desaparezcan sobre el fondo crema), vueltas del ovillo, colmillos | #F8F0D4 |
| Collar y bufanda | #COLLAR |
