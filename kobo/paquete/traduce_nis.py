#!/usr/bin/env python3
"""
Traduce al español los avisos del plugin de NickelInkSync (libnickelinksync.so, v0.1.0, MIT) que salen en la pantalla del Kobo.

El plugin los lleva escritos en alemán dentro del archivo (QStringLiteral: texto UTF-16 con su longitud fija en el código). Aquí se
cambia cada texto por su traducción ocupando exactamente lo mismo (si es más corta, se rellena con espacios al final, que no se ven):
no se mueve ni un byte de código, solo cambian letras. Antes se comprueba que el archivo es justo el de la v0.1.0 (su SHA-256) y que
cada texto está una vez; si algo no cuadra, no se toca nada.

Uso: traduce_nis.py <libnickelinksync.so>   (lo reescribe traducido)
"""
import hashlib
import sys

SHA256_V010 = "f9413c1c914de5cb120c0be68f878f07a4c7360016f22d1b733a9a8d1098f9ec"  # el de imageformats/libnickelinksync.so del Kobo.tgz de la v0.1.0

TRADUCCION = {
    # Al abrir un libro
    "Fortschritt übernommen": "Sigues donde ibas",
    "%1 %, von %2": "%1 %, de %2",
    "Fortschritt ist aktuell": "Posición al día",
    "Server hat nichts Neueres": "Nada más nuevo",
    "Nicht übernommen": "No se aplicó",
    "Laden fehlgeschlagen": "No se pudo cargar",
    "Konflikt, nicht geladen": "Conflicto: no cargada",
    "Andere Stelle im Kapitel geöffnet": "Otro punto del capítulo",
    "Anderes Kapitel geöffnet": "Abierto otro capítulo",
    "Nickel schreibt nicht, siehe Log": "El Kobo no escribe: mira el log",
    "Server hat einen neueren Stand": "Hay una posición más nueva",
    "Beim Öffnen nicht abgeglichen": "Sin sincronizar al abrir",
    # Al salir de un libro
    "Fortschritt gesendet": "Posición enviada",
    "Fortschritt vorgemerkt": "Posición pendiente",
    "Senden fehlgeschlagen": "No se pudo enviar",
    "Nicht gesendet": "No se envió",
    "Server hat schon diesen Stand": "El servidor ya la tenía",
    "Noch auf der Seite vom Server": "Aún en la página recibida",
    "Konflikt: Server und Gerät bewegt": "Conflicto: servidor y Kobo",
    "Serverfehler, wird nachgeholt": "Fallo del servidor: reintento",
    "Kein WLAN/Server, wird nachgeholt": "Sin wifi/servidor: se reintentará",
    # Lo pendiente
    "Vorgemerktes gesendet": "Pendientes enviados",
    "1 Buch": "uno",
    "%1 Bücher": "%1 libros",
    "Vorgemerkt: Konflikt": "Pendiente: conflicto",
    "Server und Gerät bewegt, bleibt liegen": "Servidor y Kobo cambiaron: se deja así",
    "Konflikt, bleibt liegen": "Conflicto: se deja así",
    # Por qué no
    "Position passt nicht zum Buch": "La posición no es del libro",
    "Buch nicht zugeordnet": "Libro sin vincular",
    "Zuordnung nicht vorbereitet": "Vínculo sin preparar",
    "Buch oder Position nicht lesbar": "No se lee el libro o posición",
    "Braucht Bestätigung (confirm)": "Hay que confirmar (confirm)",
    "Gerät nicht vorbereitet": "Kobo sin preparar",
    "Am Rechner „Gerät vorbereiten“": "Prepara el Kobo desde SesKobo",
    # Fallos
    "Server antwortet nicht": "Servidor no responde",
    "Keine Verbindung zum Server": "Sin conexión con servidor",
    "Anmeldung abgelehnt": "Usuario rechazado",
    "Unerwartete Antwort vom Server": "Respuesta rara del servidor",
    "Fehler, siehe NickelInkSync.log": "Error: mira NickelInkSync.log",
    "Kein WLAN oder kein Server": "Sin wifi o sin servidor",
    "Keine Antwort innerhalb von %1 s": "Sin respuesta en %1 s",
    "NickelInkSync startet nicht": "NickelInkSync no arranca",
    "Siehe NickelInkSync.log": "Mira NickelInkSync.log",
    "NickelInkSync ist aus": "NickelInkSync apagado",
    "Kobalt ist installiert, siehe README": "Kobalt instalado: mira el README",
}


def traduce(datos: bytes) -> bytes:
    b = bytearray(datos)
    for de, es in TRADUCCION.items():
        if len(es) > len(de):
            raise SystemExit(f"no cabe: «{es}» ({len(es)}) en lugar de «{de}» ({len(de)})")
        # El texto alemán tal cual en el archivo: UTF-16LE terminado en 0 (y no en medio de otro más largo)
        aleman = de.encode("utf-16le") + b"\x00\x00"
        donde = [i for i in range(len(b)) if b.startswith(aleman, i) and i % 2 == 0]
        if len(donde) != 1:
            raise SystemExit(f"«{de}» está {len(donde)} veces (debería estar una): ¿es otra versión del plugin?")
        i = donde[0]
        if i >= 2 and b[i - 2:i] != b"\x00\x00" and 0x20 <= b[i - 2] < 0x7f and b[i - 1] == 0:
            raise SystemExit(f"«{de}» es el final de otro texto: no se toca")
        b[i:i + len(aleman)] = es.ljust(len(de)).encode("utf-16le") + b"\x00\x00"
    return bytes(b)


if __name__ == "__main__":
    ruta = sys.argv[1]
    datos = open(ruta, "rb").read()
    h = hashlib.sha256(datos).hexdigest()
    if h != SHA256_V010:
        raise SystemExit(f"{ruta} no es el plugin de NickelInkSync v0.1.0 (SHA-256 {h}): no se traduce")
    nuevo = traduce(datos)
    assert len(nuevo) == len(datos)
    open(ruta, "wb").write(nuevo)
    print(f"traducidos {len(TRADUCCION)} avisos; cambian {sum(1 for a, c in zip(datos, nuevo) if a != c)} bytes, el tamaño es el mismo")
