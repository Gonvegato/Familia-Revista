#!/usr/bin/env python3
"""Resumen de las pruebas de Gradle (build/test-results): cuántas pasan, cuántas se saltan (y cuáles) y cuántas fallan, por clase.
Sirve para ver en GitHub que las pruebas que importan en cada sistema (el Llavero del Mac, la cuenta contra el emulador) se han
hecho de verdad y no se han saltado. Uso: python3 herramientas/escritorio/resumen-pruebas.py [carpeta]"""
import glob, os, sys, xml.etree.ElementTree as ET

raiz = sys.argv[1] if len(sys.argv) > 1 else "."
total = [0, 0, 0]
for f in sorted(glob.glob(os.path.join(raiz, "**/build/test-results/*/TEST-*.xml"), recursive=True)):
    s = ET.parse(f).getroot()
    n, salt, mal = int(s.get("tests", 0)), int(s.get("skipped", 0)), int(s.get("failures", 0)) + int(s.get("errors", 0))
    total[0] += n; total[1] += salt; total[2] += mal
    print(f"{s.get('name')}: {n - salt - mal} bien, {salt} saltadas, {mal} mal")
    for c in s.findall("testcase"):
        if c.find("skipped") is not None:
            print(f"    saltada: {c.get('name')}")
        if c.find("failure") is not None or c.find("error") is not None:
            print(f"    MAL: {c.get('name')}")
print(f"TOTAL: {total[0] - total[1] - total[2]} bien, {total[1]} saltadas, {total[2]} mal")
