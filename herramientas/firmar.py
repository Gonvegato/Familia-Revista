#!/usr/bin/env python3
"""Firma una versión con la clave de la familia (firma/familia.keystore), con el linaje desde la clave vieja de la app.

Uso:  python3 herramientas/firmar.py hiedra      (después de gradle :hiedra:assembleRelease)

Las cuatro apps se firmaban cada una con su clave. La familia usa una sola (`familia`), y Android acepta la versión nueva encima de
la vieja porque va firmada con las dos y con el linaje (apksigner rotate) que dice que la nueva sustituye a la vieja. La rotación
vale desde Android 9 (--rotation-min-sdk-version 28). Las claves y sus contraseñas están en firma/ (fuera del repositorio).
Deja el APK firmado en <app>/build/outputs/apk/release/<App>-<versión>.apk y comprueba la firma.
"""
import os, re, subprocess, sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VIEJA = {"hiedra": "hiedra", "marfil": "marfil", "cobalto": "cobalto", "azabache": "valorin"}
NOMBRE = {"hiedra": "Hiedra", "marfil": "Marfil", "cobalto": "Cobalto", "azabache": "Azabache"}
BT = "/opt/android-sdk/build-tools/35.0.0"

def pases():
    p = {}
    for l in open(os.path.join(RAIZ, "firma", "claves.properties")):
        if "=" in l:
            k, v = l.strip().split("=", 1)
            p[k.removesuffix(".pass")] = v
    return p

def version(app):
    g = open(os.path.join(RAIZ, app, "build.gradle.kts")).read()
    return re.search(r'versionName = "([^"]+)"', g).group(1)

def main(app):
    vieja = VIEJA[app]
    p = pases()
    dir_ = os.path.join(RAIZ, app, "build", "outputs", "apk", "release")
    sin = os.path.join(dir_, f"{app}-release-unsigned.apk")
    if not os.path.exists(sin):
        sys.exit(f"No encuentro {sin}: compila antes con gradle :{app}:assembleRelease")
    alineado = os.path.join(dir_, f"{app}-alineado.apk")
    subprocess.run([f"{BT}/zipalign", "-f", "-p", "4", sin, alineado], check=True)
    salida = os.path.join(dir_, f"{NOMBRE[app]}-{version(app)}.apk")
    f = lambda n: os.path.join(RAIZ, "firma", n)
    subprocess.run([f"{BT}/apksigner", "sign",
        "--ks", f(f"{vieja}.keystore"), "--ks-key-alias", vieja, "--ks-pass", f"pass:{p[vieja]}", "--key-pass", f"pass:{p[vieja]}",
        "--next-signer", "--ks", f("familia.keystore"), "--ks-key-alias", "familia", "--ks-pass", f"pass:{p['familia']}", "--key-pass", f"pass:{p['familia']}",
        "--lineage", f(f"{vieja}.lineage"), "--rotation-min-sdk-version", "28",
        "--out", salida, alineado], check=True)
    os.remove(alineado)
    r = subprocess.run([f"{BT}/apksigner", "verify", "-v", "--print-certs", salida], check=True, capture_output=True, text=True)
    print(salida)
    print("\n".join(l for l in r.stdout.splitlines() if "Verified using" in l or "Signer" in l and "SHA-256" in l))

if __name__ == "__main__":
    main(sys.argv[1])
