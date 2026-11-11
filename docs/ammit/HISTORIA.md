# Ammit

La devoradora del juicio de Osiris: en la Sala de las Dos Verdades, Anubis pesaba el corazón del muerto contra la pluma de Maat, Thot
apuntaba el resultado y, si el corazón pesaba más, Ammit se lo comía. Aquí guarda la puerta de la tablet: encendida, solo pasan las apps de
la familia (Jonsu, Bastet, Seshat, Thot, Hathor y Nut).

## 0.7.0 — el candado: no se suelta sin escribir o leer

- **Encendida, Ammit solo se apaga cuando, desde que la encendiste, has escrito 300 palabras en Thot o leído un capítulo en Seshat**, en
  cualquier dispositivo (se mira en la cuenta de la familia). Si intentas apagarla antes (con el botón de la barra o en sus ajustes), sale
  «Ammit no te suelta. Todavía no.» con lo que llevas y botones para ir a Thot o a Seshat. En cuanto lo cumples, se apaga sola.
- **Palabras**: lo que Thot sube por día y dispositivo, desde el día en que la encendiste, menos lo que ya había ese día al encenderla.
- **Capítulo**: en algún libro, lo más lejos que has llegado de verdad (Seshat no lo mueve si saltas con la barra) pasa a otro capítulo más
  adelante, habiendo leído en ese libro al menos 5 minutos desde que la encendiste. Terminar un libro también vale. Las horas de juego y lo
  visto en Hathor no cuentan.
- En la pantalla de inicio, bajo la hora: cuánto falta (o «Ya puedes soltarla»).
- Sin la cuenta de la familia no se puede comprobar, así que no se suelta.

## 0.6.0 — fluida y sin un parpadeo

- **El juicio, entero en la cortina por encima de todo**: en cuanto se abre una app que no pasa, la pantalla se funde a negro, por debajo
  se va esa app y se abre la pantalla de inicio de Ammit, y encima van las cuatro cadenas, «AMMIT.» y la ráfaga. Al acabar, la cortina
  se funde sobre la pantalla de inicio (pintada igual) y salen las apps. Ya no hay ningún cambio de pantalla a la vista. También al tocar
  una app encadenada desde el inicio de Ammit.
- **Las apps salen con fluidez tras el juicio**: el paso de «tapada» a «salen las apps» rehacía la pantalla entera en un fotograma, y al
  volver al inicio se releían todos los iconos justo durante la animación. Ahora la animación solo mueve capas, y las apps se leen solo
  al instalar o quitar alguna (o cada diez minutos).
- **Las cadenas, mucho más ligeras de pintar**: cada cadena entera se graba una vez como lista de dibujo y se reutiliza; por fotograma
  son unas 80 órdenes en vez de unas 7.000. El fondo de la pantalla de inicio se pinta igual que la cortina, así que al fundirse no se
  nota nada.
- **Al apagar**, las cadenas esperan a que tu inicio de siempre termine de abrirse (y su propia animación) antes de dar el tirón e irse.
- **Mientras está la cortina, los toques no pasan** a lo de debajo (no se abre nada sin querer); como mucho dura ocho segundos.
- Con Ammit apagada, al darle a casa ya no asoman un instante la hora y las apps de Ammit antes de tu inicio.
- Un solo toque cada vez: mientras dura una animación, el botón no hace nada.

## 0.5.3 — las cadenas de detrás, quietas

- **Las cadenas del fondo ya no parpadean**: el fondo se prepara en otro hilo mientras la pantalla pinta, y los dos compartían el
  rectángulo y los pinceles con los que se copia cada eslabón; a medio fotograma, unos eslabones salían en el sitio o con el tono de
  la otra pintada. Ahora cada pintada usa los suyos.
- **El campo, anclado a la pantalla** (no a cada ventana): la cortina, el juicio y la pantalla de inicio lo pintan exactamente en el
  mismo sitio, aunque alguna ventana no empiece en la esquina.

## 0.5.2 — el juicio, también con cortina

- **Al acabar el juicio, sin el parpadeo grande**: cuando las cadenas tapan la pantalla, se pone la cortina de cadenas por encima de todo
  (igual que ese último momento) y el cambio a la pantalla de inicio de Ammit pasa por debajo, sin verse. Luego salen las apps.
- **La cortina ya no se quita de golpe**: espera un poco más a que la pantalla de inicio esté pintada debajo y se funde en un cuarto de
  segundo mientras salen las apps (el mini parpadeo del final al encender).

## 0.5.1 — sin destellos ni parpadeos

- **Al encender ya no destella**: el primer fotograma salía con el final de la animación anterior (todo tapado) y luego saltaba al
  principio, y eso se veía como un fogonazo. Ahora cada animación empieza de cero.
- **Encender y apagar van en una cortina por encima de todo** (una capa del vigilante, el servicio de accesibilidad): mientras Android
  cambia de una pantalla de inicio a otra por debajo, lo que se ve son solo las cadenas. Al encender, tapan lo que tengas delante y,
  tapado, debajo se abre Ammit (con el mismo fondo) y salen las apps; al apagar, aparece el campo de cadenas, debajo se abre tu inicio de
  siempre, dan un tirón y se van.
- Si el vigilante no estuviera activado, se hace como antes, pero sin dejar ver la pantalla sin cadenas entre medias.

## 0.5.0 — el sinfín de cadenas

- **El fondo de Ammit es un sinfín de cadenas**: 82 cadenas gruesas de metal, unas encima de otras (las de abajo, más oscuras), siempre
  las mismas y en el mismo sitio. Encima, la hora, los widgets y tus apps.
- **Al encender**: las cadenas cruzan la pantalla una tras otra, pa-pa-pa, cada una con su golpe, hasta taparlo todo; ese muro se queda
  de fondo y las apps salen encima, una tras otra. Sin candado.
- **Al apagar**: las apps se van, las cadenas dan un tirón y se pasa a tu inicio de siempre con una capa encima en la que las cadenas se
  retiran una a una por donde vinieron, hasta que se ve tu inicio.
- **El juicio**: cuatro cadenas, «AMMIT.» con la frase (ahora un buen rato en pantalla) y la ráfaga, que son las mismas cadenas del fondo
  y pasan por encima de todo. Cuando tapan la pantalla, ya es la pantalla de inicio de Ammit: salen las apps y estás en el inicio sin
  volver tú.
- Para que vaya fluido, cada eslabón se pinta una sola vez y luego solo se copia; el fondo quieto es una imagen ya hecha.

## 0.4.0 — el juicio animado, y con cuenta

- **La pantalla del juicio, en tres tiempos**: entran cuatro cadenas, una tras otra (1, 2, 3, 4), cada una con su golpe; luego aparece
  «AMMIT.» con «Su corazón pesa más que la pluma. Ammit se lo come.»; y al final cae un montón de cadenas que tapan la pantalla. Dura algo
  más de tres segundos y vuelve al inicio.
- **Con la cuenta de la familia**, como las hermanas: entra sola si una hermana la tiene (cuando las hermanas se actualicen para
  reconocer a Ammit) o, en sus ajustes, con tu usuario y tu contraseña de Atrio. El token de tu kDrive para actualizarse sola sale de
  la cuenta (el que pusiste en Jonsu): ya no hace falta pegarlo.

## 0.3.0 — se actualiza sola

- **Actualizarse sola desde tu kDrive**, como las hermanas: busca «Ammit-<versión>.apk» en «Mi carpeta/versiones» y se instala cuando no
  la estás viendo (y con la tablet cerrada, cada pocas horas). Como Ammit no tiene cuenta, el token y el número de tu kDrive se pegan una
  vez en sus ajustes (sección «Actualizarse sola»; se guardan cifrados en la tablet). Ahí están también «Actualizar ahora» y el
  interruptor para apagarlo.
- Esta versión todavía hay que instalarla a mano; las siguientes llegarán solas.

## 0.2.0 — las cadenas, al entrar y al salir

- **Al encender Ammit**: tres cadenas grandes entran cruzando la pantalla, una tras otra; al cerrarse, la pantalla da un golpe seco y las
  apps de fuera se van poniendo en gris mientras se les enrollan sus cadenas y sale el candado.
- **Al apagarla**: las cadenas de los iconos se sueltan y caen, los iconos recuperan el color, las cadenas grandes se caen de la pantalla
  y, después, pasa a tu pantalla de inicio de siempre.
- El candado de los iconos de la primera fila ya no sale cortado por arriba.

## 0.1.0 — la primera

- **Pantalla de inicio**: tu fondo de pantalla, la hora, tus apps donde las coloques (mantener pulsada una y arrastrarla) y los widgets de
  la familia que pongas (mantener pulsado el fondo → «+ Widget»; se suben, se agrandan, se encogen y se quitan desde ahí). La familia, en
  color; las demás, en gris, con cadenas y un candado: tocarlas es el juicio.
- **El juicio**: «X NO PASA EL JUICIO · AMMIT. · Su corazón pesa más que la pluma. Ammit se lo come.», un segundo y medio, y a la
  pantalla de inicio. De día y de noche.
- **El vigilante** (servicio de accesibilidad): si una app de fuera se abre por otro lado (una notificación, recientes), salta el juicio.
  Solo mira qué app se abre, nunca lo que hay dentro. Pasan también la barra de arriba, el teclado, los permisos de Android y su
  instalador (para que las apps de la familia puedan pedir permisos y actualizarse).
- **El botón de la barra de arriba** («Ammit», con la balanza): un toque la enciende (si está todo listo) y otro la apaga. Apagada, el
  botón de casa te lleva a tu pantalla de inicio de siempre.
- **Los pasos para dejarla lista**, desde su icono: Ammit como pantalla de inicio, permitir los ajustes restringidos, activar el
  vigilante y poner el botón; cada uno con su estado.
- El icono: «A.» con la franja «JUICIO», en verde fayenza (el de los amuletos), de día y de noche.
