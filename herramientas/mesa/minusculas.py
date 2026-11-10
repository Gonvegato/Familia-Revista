#!/usr/bin/env python3
"""Dirección D: quita de las pantallas de una app lo que era de la dirección C (versalitas, letra negra y espaciada, cursivas en serif,
filetes gruesos, diálogos rectos). Mecánico y conservador: solo toca líneas de interfaz (Text, FitTitle, AlertDialog…).
Uso: minusculas.py <carpeta de fuentes .kt> [archivo a saltar…]   (Widgets.kt y Actualizador.kt se saltan siempre)"""
import re, sys, os
d = sys.argv[1]; saltar = set(sys.argv[2:]) | {'Widgets.kt', 'Actualizador.kt', 'ConfigWidgetActivity.kt'}

def frase(m):
    t = m.group(1)
    return 'Text("' + t[0] + t[1:].lower() + '"'

n = 0
for f in sorted(os.listdir(d)):
    if not f.endswith('.kt') or f in saltar: continue
    p = os.path.join(d, f); s = open(p).read(); o = s
    out = []
    for l in s.split('\n'):
        ui = ('Text(' in l or 'FitTitle(' in l or 'Titulo(' in l) and 'fontFamily' in l or 'Text(' in l and '.uppercase()' in l
        if ui:
            l = re.sub(r'\.uppercase\(\)(?=[,)])', '', l)
            l = l.replace('FontWeight.Black', 'FontWeight.SemiBold').replace('FontWeight.ExtraBold', 'FontWeight.Bold')
            l = re.sub(r', letterSpacing = \(?-?[0-9.]+\)?\.sp', '', l)
            l = re.sub(r'fontSize = (9|9\.5|10|10\.5|11|11\.5)\.sp', 'fontSize = 13.sp', l)
            l = re.sub(r'lineHeight = (12|13|14|15)\.sp', 'lineHeight = 18.sp', l)
            l = l.replace('fontFamily = Display, fontStyle = FontStyle.Italic', 'fontFamily = Body').replace('fontFamily = Display, fontWeight = FontWeight.Bold', 'fontFamily = Grotesca, fontWeight = FontWeight.Bold')
            l = re.sub(r'Text\("([A-ZÁÉÍÓÚÑ¿¡][A-ZÁÉÍÓÚÑ ]{2,}[A-ZÁÉÍÓÚÑ!?])"', frase, l)
        l = l.replace('Filete(2.dp)', 'Filete(1.dp, Line)').replace('Filete(3.dp)', 'Filete(1.dp, Line)')
        l = l.replace('containerColor = Paper, shape = esquina(4.dp)', 'containerColor = Crudo, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)')
        out.append(l)
    s = '\n'.join(out)
    if s != o: open(p, 'w').write(s); n += 1
print('cambiados', n, 'archivos en', d)
