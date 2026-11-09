#!/usr/bin/env python3
"""Los iconos de las dos apps que no son de la familia (FcFeed y Widgets): adaptables (fondo, dibujo y monocromo), 432 px (108 dp a xxxhdpi).

Uso:  python3 herramientas/apartes/iconos.py
FcFeed: fondo negro, un bocadillo blanco (una red social) con un balón dentro.
Widgets: fondo casi negro, una rejilla de cuatro piezas redondeadas (tres grises y una con el acento de la app).
"""
import math, os
from PIL import Image, ImageDraw

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
N = 432
S = 4  # supermuestreo


def lienzo(color=(0, 0, 0, 0)):
    return Image.new("RGBA", (N * S, N * S), color)


def baja(im):
    return im.resize((N, N), Image.LANCZOS)


def guarda(im, ruta, rgb=False):
    im = baja(im)
    if rgb:
        im = im.convert("RGB")
    os.makedirs(os.path.dirname(ruta), exist_ok=True)
    im.save(ruta, optimize=True)
    print(ruta)


def bocadillo(d, color):
    # Bocadillo redondeado, centrado en la zona segura (66 de 108 dp), con su pico abajo a la izquierda
    x0, y0, x1, y1 = 128 * S, 132 * S, 304 * S, 276 * S
    d.rounded_rectangle((x0, y0, x1, y1), radius=46 * S, fill=color)
    d.polygon([(150 * S, 262 * S), (140 * S, 312 * S), (196 * S, 270 * S)], fill=color)


def balon(d, cx, cy, r, color, fondo):
    """Un balón: el círculo, un pentágono en medio y las cinco costuras hacia el borde."""
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fondo)
    w = int(r * 0.13)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=color, width=w)
    rp = r * 0.42
    pts = [(cx + rp * math.cos(math.radians(-90 + 72 * i)), cy + rp * math.sin(math.radians(-90 + 72 * i))) for i in range(5)]
    d.polygon(pts, fill=color)
    for (x, y) in pts:
        ang = math.atan2(y - cy, x - cx)
        d.line([(x, y), (cx + r * 0.98 * math.cos(ang), cy + r * 0.98 * math.sin(ang))], fill=color, width=w)


def fcfeed():
    base = os.path.join(RAIZ, "shai", "src", "main", "res", "drawable-nodpi")
    guarda(lienzo((0, 0, 0, 255)), os.path.join(base, "ic_launcher_fcfeed_bg.png"), rgb=True)
    fg = lienzo(); d = ImageDraw.Draw(fg)
    bocadillo(d, (255, 255, 255, 255))
    balon(d, 216 * S, 204 * S, 50 * S, (0, 0, 0, 255), (255, 255, 255, 255))
    guarda(fg, os.path.join(base, "ic_launcher_fcfeed_fg.png"))
    # Monocromo: el bocadillo, con el balón calado
    mono = lienzo(); d = ImageDraw.Draw(mono)
    bocadillo(d, (255, 255, 255, 255))
    balon(d, 216 * S, 204 * S, 50 * S, (0, 0, 0, 0), (255, 255, 255, 255))
    guarda(mono, os.path.join(base, "ic_launcher_fcfeed_mono.png"))


WIDGETS_FONDO = (14, 14, 16, 255)
WIDGETS_PIEZA = (228, 228, 231, 255)
WIDGETS_ACENTO = (139, 156, 255, 255)  # el acento de la app (#8B9CFF)


def rejilla(d, colores):
    lado, hueco = 78 * S, 16 * S
    x0 = y0 = (N * S - (2 * lado + hueco)) // 2
    for i in range(4):
        x = x0 + (i % 2) * (lado + hueco); y = y0 + (i // 2) * (lado + hueco)
        d.rounded_rectangle((x, y, x + lado, y + lado), radius=22 * S, fill=colores[i])


def widgets():
    base = os.path.join(RAIZ, "ptah", "src", "main", "res", "drawable-nodpi")
    guarda(lienzo(WIDGETS_FONDO), os.path.join(base, "ic_launcher_widgets_bg.png"), rgb=True)
    fg = lienzo(); d = ImageDraw.Draw(fg)
    rejilla(d, [WIDGETS_PIEZA, WIDGETS_PIEZA, WIDGETS_PIEZA, WIDGETS_ACENTO])
    guarda(fg, os.path.join(base, "ic_launcher_widgets_fg.png"))
    mono = lienzo(); d = ImageDraw.Draw(mono)
    rejilla(d, [(255, 255, 255, 255)] * 4)
    guarda(mono, os.path.join(base, "ic_launcher_widgets_mono.png"))


if __name__ == "__main__":
    fcfeed()
    widgets()
