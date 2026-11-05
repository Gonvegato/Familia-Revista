#!/usr/bin/env python3
"""
Los iconos de escritorio (Linux .png y Mac .icns) a partir de los de Android: el fondo y el dibujo de cada app (PNG en Hiedra,
Marfil y Cobalto; el vector de la piedra en Azabache), recortados a la zona visible del icono adaptable y con las esquinas
redondeadas de los iconos del Mac. Uso: python3 herramientas/escritorio/iconos.py   (escribe escritorio/<app>/icono/)
"""
import os, re, struct, io
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw
import numpy as np

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
A = '{http://schemas.android.com/apk/res/android}'
LADO = 1024

def color(s, alfa=1.0):
    s = s.lstrip('#')
    if len(s) == 8: a, s = int(s[:2], 16) / 255, s[2:]
    else: a = 1.0
    return tuple(int(s[i:i+2], 16) for i in (0, 2, 4)) + (int(255 * a * alfa),)

def caminos(d):
    """Solo lo que usan estos iconos: M, L, H, V, Z (absolutas y relativas)."""
    figuras, actual, x, y = [], [], 0.0, 0.0
    for cmd, args in re.findall(r'([MLHVZmlhvz])([^MLHVZmlhvz]*)', d):
        n = [float(v) for v in re.findall(r'-?\d*\.?\d+', args)]
        if cmd in 'Mm':
            if actual: figuras.append(actual)
            x, y = (n[0], n[1]) if cmd == 'M' else (x + n[0], y + n[1]); actual = [(x, y)]
            rest = n[2:]
            for i in range(0, len(rest), 2):
                x, y = (rest[i], rest[i+1]) if cmd == 'M' else (x + rest[i], y + rest[i+1]); actual.append((x, y))
        elif cmd in 'Ll':
            for i in range(0, len(n), 2):
                x, y = (n[i], n[i+1]) if cmd == 'L' else (x + n[i], y + n[i+1]); actual.append((x, y))
        elif cmd in 'Hh':
            for v in n: x = v if cmd == 'H' else x + v; actual.append((x, y))
        elif cmd in 'Vv':
            for v in n: y = v if cmd == 'V' else y + v; actual.append((x, y))
        elif cmd in 'Zz':
            if actual: figuras.append(actual + [actual[0]]); actual = []
    if actual: figuras.append(actual)
    return figuras

def vector(archivo, lado):
    raiz = ET.parse(archivo).getroot()
    vw = float(raiz.get(A + 'viewportWidth')); k = lado / vw
    img = Image.new('RGBA', (lado, lado), (0, 0, 0, 0))
    for p in raiz.iter('path'):
        figs = [[(x * k, y * k) for x, y in f] for f in caminos(p.get(A + 'pathData'))]
        grad = p.find('.//gradient')
        capa = Image.new('RGBA', (lado, lado), (0, 0, 0, 0))
        dib = ImageDraw.Draw(capa)
        if p.get(A + 'fillColor') or grad is not None:
            if grad is not None:
                mascara = Image.new('L', (lado, lado), 0); md = ImageDraw.Draw(mascara)
                for f in figs: md.polygon(f, fill=255)
                x0, y0 = float(grad.get(A + 'startX')) * k, float(grad.get(A + 'startY')) * k
                x1, y1 = float(grad.get(A + 'endX')) * k, float(grad.get(A + 'endY')) * k
                c0, c1 = np.array(color(grad.get(A + 'startColor')), float), np.array(color(grad.get(A + 'endColor')), float)
                yy, xx = np.mgrid[0:lado, 0:lado]
                dx, dy = x1 - x0, y1 - y0
                t = np.clip(((xx - x0) * dx + (yy - y0) * dy) / (dx * dx + dy * dy), 0, 1)[..., None]
                rgba = (c0 + (c1 - c0) * t).astype(np.uint8)
                capa = Image.fromarray(rgba, 'RGBA'); capa.putalpha(mascara)
            else:
                c = color(p.get(A + 'fillColor'), float(p.get(A + 'fillAlpha', '1')))
                for f in figs: dib.polygon(f, fill=c)
        if p.get(A + 'strokeColor'):
            c = color(p.get(A + 'strokeColor'), float(p.get(A + 'strokeAlpha', '1')))
            w = max(1, int(round(float(p.get(A + 'strokeWidth', '1')) * k)))
            for f in figs: dib.line(f, fill=c, width=w, joint='curve')
        img = Image.alpha_composite(img, capa)
    return img

def capas(app):
    res = os.path.join(RAIZ, app, 'src', 'main', 'res')
    if app == 'azabache':
        d = os.path.join(res, 'drawable')
        return vector(os.path.join(d, 'icon_atrio_bg.xml'), LADO * 2), vector(os.path.join(d, 'icon_atrio_fg.xml'), LADO * 2)
    d = os.path.join(res, 'drawable-nodpi')
    ab = lambda n: Image.open(os.path.join(d, f'ic_launcher_{app}_{n}.png')).convert('RGBA').resize((LADO * 2, LADO * 2), Image.LANCZOS)
    return ab('bg'), ab('fg')

def icono(app):
    fondo, dibujo = capas(app)
    todo = Image.alpha_composite(fondo, dibujo)
    # Lo visible de un icono adaptable: los 72 dp del centro de 108 (y un poco más, como los lanzadores)
    lado = todo.width; corte = int(lado * 18 / 108)
    todo = todo.crop((corte, corte, lado - corte, lado - corte)).resize((LADO, LADO), Image.LANCZOS)
    # Las esquinas del Mac (un 22 % del lado), dentro de un margen como el de los iconos del sistema
    margen = int(LADO * 0.09); interior = LADO - 2 * margen
    todo = todo.resize((interior, interior), Image.LANCZOS)
    m = Image.new('L', (interior * 4, interior * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, interior * 4 - 1, interior * 4 - 1), radius=int(interior * 4 * 0.225), fill=255)
    m = m.resize((interior, interior), Image.LANCZOS)
    todo.putalpha(Image.fromarray(np.minimum(np.array(todo.getchannel('A')), np.array(m)), 'L'))
    final = Image.new('RGBA', (LADO, LADO), (0, 0, 0, 0)); final.paste(todo, (margen, margen), todo)
    return final

def icns(img):
    """Un .icns con las PNG que pide el Mac (128, 256, 512 y 1024)."""
    trozos = b''
    for tipo, t in ((b'ic07', 128), (b'ic08', 256), (b'ic09', 512), (b'ic10', 1024)):
        b = io.BytesIO(); img.resize((t, t), Image.LANCZOS).save(b, 'PNG'); d = b.getvalue()
        trozos += tipo + struct.pack('>I', len(d) + 8) + d
    return b'icns' + struct.pack('>I', len(trozos) + 8) + trozos

for app in ('azabache', 'cobalto', 'hiedra', 'marfil'):
    dest = os.path.join(RAIZ, 'escritorio', app, 'icono'); os.makedirs(dest, exist_ok=True)
    img = icono(app)
    img.resize((512, 512), Image.LANCZOS).save(os.path.join(dest, f'{app}.png'))
    open(os.path.join(dest, f'{app}.icns'), 'wb').write(icns(img))
    print(app, 'listo')
