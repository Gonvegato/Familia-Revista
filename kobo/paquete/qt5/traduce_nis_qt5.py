#!/usr/bin/env python3
"""
traduce_nis.py para el plugin de Qt 5 (firmware 4.x): kobo-4.x/KoboRoot.tgz de NickelInkSync v0.1.0.

En la compilación de Qt 5 (Qt 5.2.1 y GCC 4.9 de NickelTC) QStringLiteral no guarda UTF-16 como en la de Qt 6, sino que acaba en
QString::fromUtf8("texto", bytes): los avisos están en UTF-8 terminados en 0, y su longitud en BYTES va fija en el código. Por eso
traduce_nis.py no encuentra ninguno (los busca en UTF-16) y además se para antes, porque el SHA-256 no es el del plugin de Qt 6.

Aquí se usan las mismas traducciones (se leen de ../traduce_nis.py), pero cada una tiene que caber en los mismos BYTES de UTF-8
(una letra con tilde ocupa dos); si es más corta, se rellena con espacios. Solo una no cabía y lleva aquí otra frase (CAMBIOS).
Antes se comprueba el SHA-256 y que cada texto esté una sola vez y empiece justo detrás de un 0; si algo no cuadra, no se toca nada.

Uso: traduce_nis_qt5.py <libnickelinksync.so de Qt 5>   (lo reescribe traducido)
"""
import hashlib
import importlib.util
import os
import sys

SHA256_V010_QT5 = "f48fb64885baf7d89f76b90b4369ce57f453247d434dec81f4d4d1167adb6728"  # usr/local/Kobo/imageformats/libnickelinksync.so de kobo-4.x/KoboRoot.tgz (v0.1.0)

# «Sin wifi/servidor: se reintentará» son 33 letras pero 34 bytes en UTF-8 (el original, 33): no cabe.
CAMBIOS = {"Kein WLAN/Server, wird nachgeholt": "Sin wifi/servidor: reintento"}


def traducciones():
    ruta = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "traduce_nis.py")
    spec = importlib.util.spec_from_file_location("traduce_nis", ruta)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    t = dict(mod.TRADUCCION)
    t.update(CAMBIOS)
    return t


def traduce(datos: bytes) -> bytes:
    b = bytearray(datos)
    for de, es in traducciones().items():
        de8, es8 = de.encode("utf-8"), es.encode("utf-8")
        if len(es8) > len(de8):
            raise SystemExit(f"no cabe: «{es}» ({len(es8)} bytes) en lugar de «{de}» ({len(de8)} bytes)")
        aleman = de8 + b"\x00"
        donde = []
        i = b.find(aleman)
        while i != -1:
            donde.append(i)
            i = b.find(aleman, i + 1)
        if len(donde) != 1:
            raise SystemExit(f"«{de}» está {len(donde)} veces (debería estar una): ¿es otra versión del plugin?")
        i = donde[0]
        if i == 0 or b[i - 1] != 0:
            raise SystemExit(f"«{de}» es el final de otro texto: no se toca")
        b[i:i + len(aleman)] = es8 + b" " * (len(de8) - len(es8)) + b"\x00"
    return bytes(b)


if __name__ == "__main__":
    ruta = sys.argv[1]
    datos = open(ruta, "rb").read()
    h = hashlib.sha256(datos).hexdigest()
    if h != SHA256_V010_QT5:
        raise SystemExit(f"{ruta} no es el plugin de Qt 5 de NickelInkSync v0.1.0 (SHA-256 {h}): no se traduce")
    nuevo = traduce(datos)
    assert len(nuevo) == len(datos)
    open(ruta, "wb").write(nuevo)
    print(f"traducidos {len(traducciones())} avisos; cambian {sum(1 for a, c in zip(datos, nuevo) if a != c)} bytes, el tamaño es el mismo")
