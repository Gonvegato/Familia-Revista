#!/usr/bin/env python3
"""
Rehace hiedra/src/main/assets/emulators.json a partir de los dos archivos de ES-DE para Android (licencia MIT):
resources/systems/android/es_systems.xml y es_find_rules.xml (https://gitlab.com/es-de/emulationstation-de).

    python3 herramientas/hiedra/emuladores-es-de.py es_systems.xml es_find_rules.xml > hiedra/src/main/assets/emulators.json

Los comandos se copian tal cual. Se quedan los que Hiedra sabe interpretar (Comando.kt): los que usan algo que solo existe dentro de ES-DE
(%ROMPATH%, %GAMEDIRRAW%, %ANDROIDAPP%…) se dejan fuera, y un sistema sin ningún comando que se pueda usar no sale. %INJECT% vale solo
para leer la propia ROM (%INJECT%=%BASENAME%.<su extensión>, como el .psvita de Vita3K).
"""
import json
import re
import sys
import xml.etree.ElementTree as ET

# Las apps de Android son otra cosa en Hiedra (se añaden como apps): estos «sistemas» de ES-DE no se traen
FUERA = {"android", "androidapps", "androidgames", "emulators"}
NO_SOPORTADOS = {"GAMEDIRRAW", "ROMPATHRAW", "ROMRAW", "ANDROIDAPP", "FILEINJECT", "ROMPATH"}
TOKEN = re.compile(r"%([A-Za-z_]+?)%")


def soportado(cmd: str, extensiones: set) -> bool:
    # %INJECT%=%BASENAME%.ext solo si .ext es una extensión del propio sistema (el archivo inyectado es la ROM); %INJECT%=%ROM%, siempre
    for m in re.finditer(r"%INJECT%=%BASENAME%\.([A-Za-z0-9]+)", cmd):
        if m.group(1).lower() not in extensiones:
            return False
    resto = re.sub(r"%INJECT%=(%BASENAME%\.[A-Za-z0-9]+|%ROM%)", "", cmd)
    for t in TOKEN.findall(resto):
        if t in NO_SOPORTADOS or t in ("INJECT", "BASENAME"):
            return False
    return True


def main(sistemas_xml: str, reglas_xml: str) -> None:
    emus = {}
    for e in ET.parse(reglas_xml).getroot().iter("emulator"):
        entradas = [x.text.strip() for r in e.iter("rule") if r.get("type") == "androidpackage" for x in r.iter("entry") if x.text]
        if entradas:
            emus[e.get("name")] = entradas
    sistemas = []
    for s in ET.parse(sistemas_xml).getroot().iter("system"):
        ext = sorted({x.lower().lstrip(".") for x in (s.findtext("extension") or "").split() if x})
        cmds = [[c.get("label") or "Predeterminado", c.text.strip()] for c in s.iter("command") if c.text]
        cmds = [c for c in cmds if soportado(c[1], set(ext))]
        if cmds and s.findtext("name") not in FUERA:
            sistemas.append({"id": s.findtext("name"), "name": s.findtext("fullname"), "ext": ext, "cmds": cmds})
    json.dump({"emus": emus, "systems": sistemas}, sys.stdout, ensure_ascii=False, separators=(",", ":"))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
