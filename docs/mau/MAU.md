# Mau, el gato de la suite

Mau acompaña en las seis apps (móvil y ordenador): Seshat (y SesKobo), Bastet, Thot, Jonsu, Hathor y Nut.

- **Tocarlo**: el menú. Las seis apps (abrir una se lleva a Mau con ella), esconderse, acariciar, silencio 1 hora, lo propio de cada app (el sprint de Thot) y sus ajustes.
- **Dos toques**: lo acaricias y ronronea (vibra suave en el móvil).
- **Arrastrarlo**: se mueve por abajo, a izquierda o derecha.
- **Escondido**: se ven solo las orejas. Se arrastran a cualquier borde (abajo, arriba, izquierda, derecha) y, si las tocas, vuelve.
- **Si no haces nada, se duerme**: primero bosteza, luego parpadea tres veces y se queda dormido. Al tocar algo se despierta. De noche, en casa, se duerme antes. Viendo algo (Hathor, Nut) nunca se duerme ni habla.
- **Habla poco**: como mucho una cosa cada 4 minutos por su cuenta, nunca si está callado, escondido o dormido. Lo importante (un libro acabado, la meta de palabras) sí lo dice.
- **Ajustes de Mau** (en su menú): si habla, si se mueve, si se duerme y el tamaño (pequeño, normal o grande). Se guardan en cada app.

## Dónde está el código

| Qué | Dónde |
|---|---|
| Dibujos (SVG originales) y tabla de animación | `docs/mau/svg/`, `docs/mau/ANIMACION.md` |
| Dibujos en Kotlin (generado: `python3 -I herramientas/mau/genera_dibujos.py`) | `mau/src/main/kotlin/com/gonzalo/mau/Dibujos.kt` |
| Cómo se mueve (pivotes, curvas, parpadeos al azar) | `mau/.../Animador.kt`, `Curvas.kt`, `Modelo.kt` |
| Lo que piensa (sueño, callado, escondido, bocadillos, sprint) y lo que dice | `mau/.../Cerebro.kt`, `Frases.kt` |
| Lo que se ve, común a móvil y ordenador (Compose) | `mau/compose/com/gonzalo/mau/ui/` (lo compilan `familia` y `escritorio:familia`) |
| Móvil | `familia/.../MauMovil.kt` |
| Ordenador (lo pone la ventana de cada app) | `escritorio/familia/.../pc/MauPc.kt`, `Ventana.kt` |

## Cómo se usa en una app

**Móvil**: en cada actividad donde deba salir, después de `setContent` o `setContentView`:

```kotlin
MauMovil.pon(this, "marfil", Lugar.LEYENDO)          // "hiedra", "cobalto", "azabache", "mostaza", "cobre"
override fun onNewIntent(i: Intent) { super.onNewIntent(i); MauMovil.miraSiLlega(i) }
```

**Ordenador**: no hay que hacer nada. `appDeLaFamilia` ya lo pone encima de todo.

**En las dos**:

- Las pantallas dicen dónde está Mau con `MauLugar(Lugar.ESCRIBIENDO)`. En el móvil es `com.gonzalo.familia.MauLugar`; en el ordenador, `com.gonzalo.familia.pc.MauLugar`.
- `MauFuera()` hace que no salga en una pantalla: un juego o la sala de la tele.
- `MauMargenAbajo(alto)` (en `familia` y en `familia.pc`): esa pantalla ocupa tanto abajo (una banda de reproductor) y Mau se sienta encima, sin taparla.
- Para hablarle:
  - `Mau.di(texto)`: lo dice por su cuenta, si toca.
  - `Mau.celebra(texto)`: salta y lo dice.
  - `Mau.responde(texto)`: siempre.
  - `Mau.toque()`: cuenta como «haces algo». Úsalo, por ejemplo, al teclear en un campo de texto del móvil, donde el teclado no pasa por la ventana.
- Acciones propias en su menú: `Mau.acciones += AccionMenu("Sprint de 25 min conmigo", visible = { … }) { … }`.
- Cosas una vez al día: `Mau.cerebro.primeraVezHoy("clave", Mau.ahora())`.
