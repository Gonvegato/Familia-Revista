#!/usr/bin/env python3
"""Saca el icono adaptable de Cobalto (fondo, primer plano y monocromo).

Con docs/iconos/cobalto-original.jpg (la tarjeta azul cobalto con esquinas redondas sobre un fondo oscuro y el átomo —tres elipses y
sus electrones— en líneas claras) se saca de la imagen, como en Marfil y Carmesí:
  - la tarjeta se detecta (píxeles azules) y se mete un 9 % por cada lado;
  - el fondo es el degradado de la tarjeta (el interior muy desenfocado, sin líneas);
  - el primer plano son solo las líneas claras del átomo (lo que es más claro que el fondo), en blanco, escaladas para que quede
    dentro de la zona segura (66 % central);
  - el monocromo es el mismo átomo en negro (para los iconos con tema).
Sin la imagen, se dibuja el mismo átomo a mano (tres elipses a 60°, con sus electrones) sobre el azul cobalto.
Uso: python3 herramientas/icono-cobalto.py     (necesita Pillow y numpy)
"""
import math, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

RAIZ = __file__.rsplit('/herramientas/', 1)[0]
SRC = RAIZ + '/docs/iconos/cobalto-original.jpg'
RES = RAIZ + '/app/src/main/res/drawable-nodpi/'
C = 432


def capa(alfa, color):
    out = Image.new('RGBA', (C, C), (0, 0, 0, 0))
    esc = int(C * 0.66)
    al = Image.fromarray((alfa * 255).astype(np.uint8)).resize((esc, esc), Image.LANCZOS)
    col = Image.new('RGBA', (esc, esc), tuple(int(v) for v in color) + (255,)); col.putalpha(al)
    out.paste(col, ((C - esc) // 2, (C - esc) // 2), col)
    return out


def de_la_imagen():
    im = Image.open(SRC).convert('RGB')
    a = np.asarray(im).astype(float)
    m = (a[..., 2] > 90) & (a[..., 2] > a[..., 0] * 1.6) & (a[..., 2] > a[..., 1] * 1.25)
    ys = np.where(m.mean(1) > 0.5)[0]; xs = np.where(m.mean(0) > 0.5)[0]
    if len(xs) == 0 or len(ys) == 0: return None
    x0, x1, y0, y1 = xs[0], xs[-1], ys[0], ys[-1]
    ins = int((x1 - x0) * 0.09)
    inter = im.crop((x0 + ins, y0 + ins, x1 - ins, y1 - ins)).resize((1200, 1200), Image.LANCZOS)
    L = np.asarray(inter).astype(float) @ np.array([.299, .587, .114])
    bg = inter.filter(ImageFilter.GaussianBlur(120))
    bgL = np.asarray(bg).astype(float) @ np.array([.299, .587, .114])
    alfa = np.clip((L - bgL) / 40.0, 0, 1) ** 0.8
    return bg, alfa


def a_mano():
    S = 1200
    # El fondo: azul cobalto, algo más claro arriba a la izquierda y más hondo abajo a la derecha
    y, x = np.mgrid[0:S, 0:S].astype(float) / S
    t = np.clip((x + y) / 2, 0, 1)
    arriba = np.array([26., 64., 196.]); abajo = np.array([8., 28., 128.])
    rgb = arriba[None, None, :] * (1 - t[..., None]) + abajo[None, None, :] * t[..., None]
    bg = Image.fromarray(rgb.astype(np.uint8), 'RGB')
    # El átomo: tres elipses a 60° y sus electrones, en líneas blancas con un halo suave
    lin = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(lin)
    cx = cy = S / 2
    rx, ry, grosor = S * 0.40, S * 0.145, int(S * 0.016)
    puntos = {0: [0.02, 0.56], 60: [0.3, 0.78], 120: [0.47, 0.9]}
    for ang in (0, 60, 120):
        a = math.radians(ang)
        pts = []
        for k in range(720):
            u = 2 * math.pi * k / 720
            ex, ey = rx * math.cos(u), ry * math.sin(u)
            pts.append((cx + ex * math.cos(a) - ey * math.sin(a), cy + ex * math.sin(a) + ey * math.cos(a)))
        d.line(pts + [pts[0]], fill=255, width=grosor, joint='curve')
        for f in puntos[ang]:
            u = 2 * math.pi * f
            ex, ey = rx * math.cos(u), ry * math.sin(u)
            px, py = cx + ex * math.cos(a) - ey * math.sin(a), cy + ex * math.sin(a) + ey * math.cos(a)
            r = S * 0.022
            d.ellipse((px - r, py - r, px + r, py + r), fill=255)
    halo = lin.filter(ImageFilter.GaussianBlur(S * 0.02))
    alfa = np.clip(np.asarray(lin).astype(float) / 255 + np.asarray(halo).astype(float) / 255 * 0.35, 0, 1)
    return bg, alfa


r = de_la_imagen() if os.path.exists(SRC) else None
origen = 'de la imagen'
if r is None:
    r = a_mano(); origen = 'dibujado a mano'
bg, alfa = r
bg.resize((C, C), Image.BICUBIC).save(RES + 'ic_launcher_cobalto_bg.png')
capa(alfa, (255, 255, 255)).save(RES + 'ic_launcher_cobalto_fg.png')
capa(alfa, (0, 0, 0)).save(RES + 'ic_launcher_cobalto_mono.png')
print('Hecho (' + origen + '): fondo, primer plano y monocromo en', RES)
