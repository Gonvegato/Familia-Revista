#!/usr/bin/env python3
"""Pasa los dibujos de Mau (docs/mau/svg/*.svg) a Kotlin: mau/src/main/kotlin/com/gonzalo/mau/Dibujos.kt.

Cada postura queda como un árbol de grupos (con su id, para animarlos) y trazos (todo como un `d` de path: los círculos, elipses,
rectángulos, líneas y polígonos se pasan a path aquí). El color «#COLLAR» es el acento de la app (el collar o la bufanda).
Uso: python3 -I herramientas/mau/genera_dibujos.py   (desde la raíz del repositorio)
"""
import os, re, sys
import xml.etree.ElementTree as ET

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SVG = os.path.join(RAIZ, 'docs', 'mau', 'svg')
SALIDA = os.path.join(RAIZ, 'mau', 'src', 'main', 'kotlin', 'com', 'gonzalo', 'mau', 'Dibujos.kt')
POSES = ['sentado', 'leyendo', 'escribiendo', 'viendo', 'celebrando', 'tapandose', 'durmiendo', 'jugando', 'ronroneando', 'bostezando', 'escondido']
PUNTAS = {'butt': 0, 'round': 1, 'square': 2}
UNIONES = {'miter': 0, 'round': 1, 'bevel': 2}

def n(v):
    f = float(v)
    return ('%g' % f)

def color(v):
    """(argb, acento): 0 = sin color."""
    if v is None or v == 'none': return 0, False
    if v == '#COLLAR': return 0, True
    m = re.fullmatch(r'#([0-9A-Fa-f]{6})', v)
    if not m: sys.exit('color raro: %r' % v)
    return 0xFF000000 | int(m.group(1), 16), False

def d_de(e, tag):
    a = e.attrib
    if tag == 'path': return a['d']
    if tag == 'circle':
        cx, cy, r = float(a['cx']), float(a['cy']), float(a['r'])
        return f'M{n(cx-r)} {n(cy)}A{n(r)} {n(r)} 0 1 0 {n(cx+r)} {n(cy)}A{n(r)} {n(r)} 0 1 0 {n(cx-r)} {n(cy)}Z'
    if tag == 'ellipse':
        cx, cy, rx, ry = float(a['cx']), float(a['cy']), float(a['rx']), float(a['ry'])
        return f'M{n(cx-rx)} {n(cy)}A{n(rx)} {n(ry)} 0 1 0 {n(cx+rx)} {n(cy)}A{n(rx)} {n(ry)} 0 1 0 {n(cx-rx)} {n(cy)}Z'
    if tag == 'rect':
        if 'rx' in a or 'ry' in a: sys.exit('rect con esquinas redondas: falta pasarlo a path')
        x, y, w, h = float(a.get('x', 0)), float(a.get('y', 0)), float(a['width']), float(a['height'])
        return f'M{n(x)} {n(y)}H{n(x+w)}V{n(y+h)}H{n(x)}Z'
    if tag == 'line':
        return f'M{n(a["x1"])} {n(a["y1"])}L{n(a["x2"])} {n(a["y2"])}'
    if tag == 'polygon':
        p = [float(v) for v in re.split(r'[\s,]+', a['points'].strip())]
        pts = [f'{n(p[i])} {n(p[i+1])}' for i in range(0, len(p), 2)]
        return 'M' + 'L'.join(pts) + 'Z'
    sys.exit('elemento no admitido: ' + tag)

def kt(e, sangria):
    tag = e.tag.split('}')[-1]
    a = e.attrib
    op = float(a.get('opacity', 1))
    s = '    ' * sangria
    if tag == 'g':
        hijos = [kt(h, sangria + 1) for h in e]
        ident = '"%s"' % a['id'] if 'id' in a else 'null'
        return f'{s}Grupo({ident}, {n(op)}f, listOf(\n' + ',\n'.join(hijos) + f'\n{s}))'
    if 'transform' in a: sys.exit('transform sin pasar: ' + str(a))
    rell, rellA = color(a.get('fill', '#000000'))
    lin, linA = color(a.get('stroke'))
    ancho = float(a.get('stroke-width', 1)) if (lin or linA) else 0
    punta = PUNTAS[a.get('stroke-linecap', 'butt')]
    union = UNIONES[a.get('stroke-linejoin', 'miter')]
    d = d_de(e, tag)
    return (f'{s}Trazo("{d}", 0x{rell:08X}, {str(rellA).lower()}, 0x{lin:08X}, {str(linA).lower()}, {n(ancho)}f, {punta}, {union}, {n(op)}f)')

def main():
    partes = []
    for p in POSES:
        r = ET.parse(os.path.join(SVG, f'mau-{p}.svg')).getroot()
        vb = [float(v) for v in r.attrib['viewBox'].split()]
        hijos = [kt(h, 3) for h in r]
        partes.append(f'    private fun {p}() = Dibujo({n(vb[2])}f, {n(vb[3])}f, Grupo(null, 1f, listOf(\n' + ',\n'.join(hijos) + '\n        )))\n')
    casos = '\n'.join(f'        Pose.{p.upper()} -> {p}()' for p in POSES)
    txt = ('// Generado por herramientas/mau/genera_dibujos.py desde docs/mau/svg: no lo edites a mano.\n'
           'package com.gonzalo.mau\n\n'
           '@Suppress("LargeClass")\n'
           'internal object Dibujos {\n'
           '    /** El dibujo de cada postura (se arma una vez y se guarda). */\n'
           '    fun de(p: Pose): Dibujo = cache.getOrPut(p) { arma(p) }\n'
           '    private val cache = java.util.concurrent.ConcurrentHashMap<Pose, Dibujo>()\n'
           '    private fun arma(p: Pose): Dibujo = when (p) {\n' + casos + '\n    }\n\n' + '\n'.join(partes) + '}\n')
    open(SALIDA, 'w').write(txt)
    print('escrito', SALIDA, len(txt))

main()
