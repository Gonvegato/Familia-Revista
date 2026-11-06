#!/usr/bin/env python3
"""Saca el icono adaptable de Mostaza (fondo, primer plano y monocromo) y el cartel de la tele (banner), dibujados a mano.

Como los de la familia (Marfil, Cobalto): un fondo de color con un degradado suave y, encima, un dibujo de líneas claras dentro de
la zona segura (el 66 % central), con un halo suave; el monocromo es el mismo dibujo en negro (para los iconos con tema).
  - el fondo: mostaza, algo más claro arriba a la izquierda y más hondo abajo a la derecha;
  - el dibujo: un fotograma de película (un marco con las perforaciones arriba y abajo) con el triángulo de «reproducir» dentro.
Uso: python3 herramientas/mostaza/icono-mostaza.py     (necesita Pillow y numpy)
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

RAIZ = __file__.rsplit('/herramientas/', 1)[0]
RES = RAIZ + '/mostaza/src/main/res/drawable-nodpi/'
C = 432
S = 1200
ARRIBA = np.array([226., 176., 70.])
ABAJO = np.array([150., 98., 12.])


def fondo(ancho, alto):
    y, x = np.mgrid[0:alto, 0:ancho].astype(float)
    t = np.clip((x / ancho + y / alto) / 2, 0, 1)
    rgb = ARRIBA[None, None, :] * (1 - t[..., None]) + ABAJO[None, None, :] * t[..., None]
    return Image.fromarray(rgb.astype(np.uint8), 'RGB')


def dibujo():
    lin = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(lin)
    g = int(S * 0.034)
    # El fotograma: un marco apaisado con las esquinas un poco redondas
    x0, x1, y0, y1 = S * 0.08, S * 0.92, S * 0.20, S * 0.80
    d.rounded_rectangle((x0, y0, x1, y1), radius=S * 0.06, outline=255, width=g)
    # Las perforaciones, arriba y abajo (cuadraditos rellenos)
    n = 6
    lado = S * 0.052
    for fila in (y0 + S * 0.055, y1 - S * 0.055 - lado):
        for i in range(n):
            cx = x0 + (x1 - x0) * (i + 0.5) / n
            d.rounded_rectangle((cx - lado / 2, fila, cx + lado / 2, fila + lado), radius=lado * 0.22, fill=255)
    # El triángulo de «reproducir», en el centro (algo a la derecha, para que se vea centrado)
    cy = (y0 + y1) / 2
    h = S * 0.20
    cx = S / 2 + h * 0.12
    d.polygon([(cx - h * 0.45, cy - h / 2), (cx - h * 0.45, cy + h / 2), (cx + h * 0.55, cy)], fill=255)
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
fondo(S, S).resize((C, C), Image.BICUBIC).save(RES + 'ic_launcher_mostaza_bg.png')
capa(alfa, (255, 250, 238), C, 0.66).save(RES + 'ic_launcher_mostaza_fg.png')
capa(alfa, (0, 0, 0), C, 0.66).save(RES + 'ic_launcher_mostaza_mono.png')
# El cartel de Android TV (320×180): el fondo y el dibujo a la izquierda, con el nombre
b = fondo(320, 180).convert('RGBA')
icono = capa(alfa, (255, 250, 238), 150, 0.9)
b.alpha_composite(icono, (8, 15))
d = ImageDraw.Draw(b)
try:
    from PIL import ImageFont
    letra = ImageFont.truetype(RAIZ + '/mostaza/src/main/res/font/display_semibold.ttf', 40)
except Exception:
    letra = None
d.text((160, 66), 'Mostaza', fill=(255, 250, 238), font=letra)
b.convert('RGB').save(RES + 'banner_mostaza.png')
print('Hecho: fondo, primer plano, monocromo y cartel en', RES)
