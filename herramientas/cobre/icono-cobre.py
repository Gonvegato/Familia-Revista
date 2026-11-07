#!/usr/bin/env python3
"""Saca el icono adaptable de Cobre (fondo, primer plano y monocromo) y el cartel de la tele (banner), dibujados a mano.

Como los de la familia (Marfil, Cobalto, Mostaza): un fondo de color con un degradado suave y, encima, un dibujo de líneas claras dentro
de la zona segura (el 66 % central), con un halo suave; el monocromo es el mismo dibujo en negro (para los iconos con tema).
  - el fondo: cobre, más claro y rosado arriba a la izquierda y más hondo, casi pardo, abajo a la derecha (el color del metal);
  - el dibujo: una tele antigua (la pantalla con las esquinas redondas, un poco abombada), sus dos antenas y, dentro, la onda de la señal.
Uso: python3 herramientas/cobre/icono-cobre.py     (necesita Pillow y numpy)
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

RAIZ = __file__.rsplit('/herramientas/', 1)[0]
RES = RAIZ + '/cobre/src/main/res/drawable-nodpi/'
C = 432
S = 1200
ARRIBA = np.array([214., 132., 92.])
ABAJO = np.array([122., 58., 26.])


def fondo(ancho, alto):
    y, x = np.mgrid[0:alto, 0:ancho].astype(float)
    t = np.clip((x / ancho + y / alto) / 2, 0, 1)
    rgb = ARRIBA[None, None, :] * (1 - t[..., None]) + ABAJO[None, None, :] * t[..., None]
    return Image.fromarray(rgb.astype(np.uint8), 'RGB')


def dibujo():
    lin = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(lin)
    g = int(S * 0.034)
    # La pantalla: un rectángulo apaisado con las esquinas muy redondas (las teles de tubo)
    x0, x1, y0, y1 = S * 0.10, S * 0.90, S * 0.32, S * 0.86
    d.rounded_rectangle((x0, y0, x1, y1), radius=S * 0.11, outline=255, width=g)
    # Las dos antenas, en V, desde el centro de arriba, con su bolita
    cx, base = S / 2, y0 - g * 0.2
    for dx in (-1, 1):
        fx, fy = cx + dx * S * 0.20, S * 0.08
        d.line((cx, base, fx, fy), fill=255, width=int(g * 0.9))
        r = S * 0.028
        d.ellipse((fx - r, fy - r, fx + r, fy + r), fill=255)
    # La onda de la señal, en la pantalla
    pts = []
    for i in range(0, 1201):
        t = i / 1200
        x = x0 + (x1 - x0) * (0.18 + 0.64 * t)
        y = (y0 + y1) / 2 + np.sin(t * 2 * np.pi * 1.5) * S * 0.08 * (1 - abs(t - 0.5) * 0.6)
        pts.append((x, y))
    # (a base de puntos redondos muy juntos: una línea gruesa en curva sale con dientes)
    r = g * 0.48
    for x, y in pts:
        d.ellipse((x - r, y - r, x + r, y + r), fill=255)
    halo = lin.filter(ImageFilter.GaussianBlur(S * 0.02))
    return np.clip(np.asarray(lin).astype(float) / 255 + np.asarray(halo).astype(float) / 255 * 0.30, 0, 1)


def capa(alfa, color, lado, escala):
    out = Image.new('RGBA', (lado, lado), (0, 0, 0, 0))
    esc = int(lado * escala)
    al = Image.fromarray((alfa * 255).astype(np.uint8)).resize((esc, esc), Image.LANCZOS)
    col = Image.new('RGBA', (esc, esc), tuple(int(v) for v in color) + (255,)); col.putalpha(al)
    out.paste(col, ((lado - esc) // 2, (lado - esc) // 2), col)
    return out


alfa = dibujo()
fondo(S, S).resize((C, C), Image.BICUBIC).save(RES + 'ic_launcher_cobre_bg.png')
capa(alfa, (255, 246, 236), C, 0.66).save(RES + 'ic_launcher_cobre_fg.png')
capa(alfa, (0, 0, 0), C, 0.66).save(RES + 'ic_launcher_cobre_mono.png')
# El cartel de Android TV (320×180): el fondo y el dibujo a la izquierda, con el nombre
b = fondo(320, 180).convert('RGBA')
icono = capa(alfa, (255, 246, 236), 150, 0.9)
b.alpha_composite(icono, (8, 15))
d = ImageDraw.Draw(b)
try:
    from PIL import ImageFont
    letra = ImageFont.truetype(RAIZ + '/cobre/src/main/res/font/display_semibold.ttf', 46)
except Exception:
    letra = None
d.text((162, 62), 'Cobre', fill=(255, 246, 236), font=letra)
b.convert('RGB').save(RES + 'banner_cobre.png')
print('Hecho: fondo, primer plano, monocromo y cartel en', RES)
