#!/usr/bin/env python3
"""Vuelve a firmar ATLAS con la clave de la familia (la usa .github/workflows/atlas.yml).

ATLAS (los mapas de las novelas, Godot, repositorio Gonvegato/Atlas, paquete com.gonzalo.atlas) no se compila aquí: publica
cada versión en https://github.com/Gonvegato/Atlas/releases/latest/download/ATLAS.apk, con su ATLAS.json al lado
({"version": "0.12.1", "code": 17, "sha": <el commit>}), firmada con una clave provisional. La clave de la familia solo está
en este repositorio (secreto FIRMA_B64), así que la firma de verdad se pone aquí:

  python3 herramientas/atlas/firmar_atlas.py ATLAS.apk ATLAS.json [carpeta_de_salida]

1. Comprueba que el APK es el que dice ATLAS.json: paquete com.gonzalo.atlas, mismo versionName y versionCode.
2. Lo alinea (si no lo estaba) y lo firma con firma/familia.keystore (alias familia, sin linaje: como las apps que nacen en
   la familia). apksigner sign sustituye la firma provisional.
3. Comprueba con apksigner verify --print-certs que queda un único firmante y que su huella SHA-256 es la de la familia
   (Acceso.CERTIFICADO, leída de familia/.../Acceso.kt: una sola fuente).
4. Escribe <salida>/Atlas-<versión>.apk e imprime su ruta (la primera línea de la salida).

Las contraseñas van por el entorno (env:), nunca en la línea de órdenes.
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PAQUETE = "com.gonzalo.atlas"
NOMBRE = "Atlas"


def build_tools():
    for base in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"), "/opt/android-sdk", "/usr/local/lib/android/sdk"):
        if not base:
            continue
        d = os.path.join(base, "build-tools")
        if os.path.isdir(d):
            vers = sorted((v for v in os.listdir(d) if os.path.isfile(os.path.join(d, v, "apksigner"))),
                          key=lambda v: [int(x) if x.isdigit() else 0 for x in re.split(r"[.-]", v)], reverse=True)
            if vers:
                return os.path.join(d, vers[0])
    sys.exit("No encuentro las build-tools de Android (apksigner, zipalign, aapt2)")


def certificado_familia():
    """La huella de Acceso.CERTIFICADO, en minúsculas y sin separadores."""
    t = open(os.path.join(RAIZ, "familia", "src", "main", "java", "com", "gonzalo", "familia", "Acceso.kt"), encoding="utf-8").read()
    m = re.search(r'CERTIFICADO[^"]*"([0-9a-fA-F]{64})"', t)
    if not m:
        sys.exit("No encuentro Acceso.CERTIFICADO en Acceso.kt")
    return m.group(1).lower()


def pase_familia():
    for l in open(os.path.join(RAIZ, "firma", "claves.properties"), encoding="utf-8"):
        if l.strip().startswith("familia.pass="):
            return l.strip().split("=", 1)[1]
    sys.exit("Falta familia.pass en firma/claves.properties")


def firmantes(bt, apk):
    """[(n, sha256)] de los firmantes de [apk] (apksigner verify --print-certs); sale si no verifica."""
    r = subprocess.run([f"{bt}/apksigner", "verify", "--print-certs", apk], capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit(f"apksigner verify falla:\n{r.stdout}\n{r.stderr}")
    return re.findall(r"Signer #(\d+) certificate SHA-256 digest: ([0-9a-f]{64})", r.stdout), r.stdout


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    apk, js = sys.argv[1], sys.argv[2]
    salida_dir = sys.argv[3] if len(sys.argv) > 3 else os.path.dirname(os.path.abspath(apk))
    meta = json.load(open(js, encoding="utf-8"))
    version = str(meta.get("version", "")).strip()
    codigo = int(meta.get("code", 0))
    if not re.fullmatch(r"\d+(?:\.\d+)*", version) or codigo <= 0:
        sys.exit(f"ATLAS.json no trae una versión válida: {meta}")
    bt = build_tools()

    # 1. Es el APK que dice ATLAS.json
    badging = subprocess.run([f"{bt}/aapt2", "dump", "badging", apk], capture_output=True, text=True).stdout
    m = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not m:
        sys.exit("No puedo leer el paquete del APK (¿es un APK?)")
    if (m.group(1), int(m.group(2)), m.group(3)) != (PAQUETE, codigo, version):
        sys.exit(f"El APK no coincide con ATLAS.json: APK {m.group(1)} {m.group(3)} ({m.group(2)}), JSON {PAQUETE} {version} ({codigo})")
    antes, _ = firmantes(bt, apk)
    print(f"ATLAS {version} ({codigo}); firma de antes: {len(antes)} firmante(s): " + ", ".join(h[:16] + "…" for _, h in antes), file=sys.stderr)

    # 2. Alinear (si hace falta) y firmar con la familia, sin linaje
    os.makedirs(salida_dir, exist_ok=True)
    salida = os.path.join(salida_dir, f"{NOMBRE}-{version}.apk")
    with tempfile.TemporaryDirectory() as tmp:
        alineado = os.path.join(tmp, "alineado.apk")
        if subprocess.run([f"{bt}/zipalign", "-c", "-p", "4", apk], capture_output=True).returncode == 0:
            shutil.copyfile(apk, alineado)
        else:
            subprocess.run([f"{bt}/zipalign", "-f", "-p", "4", apk, alineado], check=True)
        ks = os.path.join(RAIZ, "firma", "familia.keystore")
        entorno = dict(os.environ, PASE_FAMILIA=pase_familia())
        subprocess.run([f"{bt}/apksigner", "sign", "--ks", ks, "--ks-key-alias", "familia", "--ks-pass", "env:PASE_FAMILIA",
                        "--key-pass", "env:PASE_FAMILIA", "--out", salida, alineado], check=True, env=entorno)
    idsig = salida + ".idsig"
    if os.path.exists(idsig):
        os.remove(idsig)

    # 3. Un único firmante, el de la familia
    despues, texto = firmantes(bt, salida)
    esperado = certificado_familia()
    if len(despues) != 1:
        os.remove(salida)
        sys.exit(f"Tras firmar quedan {len(despues)} firmantes (debe quedar uno):\n{texto}")
    if despues[0][1] != esperado:
        os.remove(salida)
        sys.exit(f"La huella no es la de la familia: {despues[0][1]} (esperada {esperado})")
    print(salida)
    print(f"Firmado: un único firmante, SHA-256 {despues[0][1]} (Acceso.CERTIFICADO)", file=sys.stderr)


if __name__ == "__main__":
    main()
