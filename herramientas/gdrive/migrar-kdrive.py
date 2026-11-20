#!/usr/bin/env python3
"""Migra TODOS tus archivos de kDrive a Google Drive sin borrar nada ni perder ningún dato.

Copia de forma recursiva las carpetas de la familia desde «Mi carpeta» de tu kDrive a «<carpeta>»
en Google Drive (por defecto «Familia»):
  - Atrio/        (fotos, capturas, libros antiguos de Jonsu)
  - Marfil/       (libros de Seshat)
  - Hiedra/       (ROMs, partidas guardadas y estados rápidos de Bastet)
  - Mostaza/      (películas y series privadas de Hathor)
  - versiones/    (APKs y paquetes de escritorio)

Es 100 % segura y reanudable:
  - Nunca borra ni modifica nada en kDrive.
  - Si un archivo ya está en Google Drive con el mismo nombre y tamaño, lo salta.
  - Guarda un mapa persistente (migracion-kdrive-gdrive.json: kdrive_id → gdrive_id) tras cada archivo.
  - Opcionalmente (--actualizar-firebase), añade el campo aditivo `gd` en Firebase junto a `f`/`id`
    existentes (sin borrar nunca `f`/`id`). Por defecto corre en modo simulación (--dry-run); añade
    --aplicar para escribir en Firebase.

Uso:
  # 1) Copiar archivos de kDrive → Google Drive:
  export KDRIVE_TOKEN="..."
  python3 herramientas/gdrive/migrar-kdrive.py

  # 2) Ver qué nodos de Firebase recibirían el campo `gd` (simulación, no escribe nada):
  python3 herramientas/gdrive/migrar-kdrive.py --solo-firebase --actualizar-firebase

  # 3) Aplicar los cambios aditivos (`gd`) en Firebase:
  python3 herramientas/gdrive/migrar-kdrive.py --solo-firebase --actualizar-firebase --aplicar
"""
import argparse
import getpass
import json
import os
import shutil
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request

# Reutilizamos el cliente GDrive de subir-version.py
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import importlib.util

_spec = importlib.util.spec_from_file_location(
    "subir_version", os.path.join(os.path.dirname(os.path.abspath(__file__)), "subir-version.py")
)
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
GDrive = _mod.GDrive
cargar_credenciales = _mod.cargar_credenciales

KDRIVE_API = "https://api.infomaniak.com"
CARPETAS_DEFECTO = ["Atrio", "Marfil", "Hiedra", "Mostaza", "versiones"]
MAPA_DEFECTO = "migracion-kdrive-gdrive.json"

# Mismo proyecto Firebase de la familia (ver nucleo/src/main/kotlin/com/gonzalo/familia/nucleo/Sesion.kt)
FIREBASE_DB = os.environ.get("FIREBASE_DB", "https://app-ratear-default-rtdb.europe-west1.firebasedatabase.app").rstrip("/")
FIREBASE_AUTH_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword"
FIREBASE_DOMINIO = "ronron.example.com"


class KDriveClient:
    def __init__(self, token, drive_id=None):
        self.token = token.strip()
        self.drive_id = (drive_id or "").strip() or self._detecta_drive()

    def _headers(self):
        h = {}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        return h

    def get_json(self, url):
        req = urllib.request.Request(url, headers=self._headers())
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read().decode("utf-8") or "{}")
        except urllib.error.HTTPError as e:
            raise SystemExit(f"kDrive HTTP {e.code} en {url.split('?')[0]}: {e.read().decode('utf-8', 'replace')[:300]}")

    def _detecta_drive(self):
        cuentas = self.get_json(f"{KDRIVE_API}/1/account").get("data", [])
        drives = []
        for c in cuentas:
            drives += self.get_json(f"{KDRIVE_API}/2/drive?account_id={c['id']}").get("data", [])
        if len(drives) != 1:
            lista = ", ".join(f"{d['id']}={d.get('name')}" for d in drives)
            raise SystemExit(f"Hay {len(drives)} kDrive; indica KDRIVE_ID ({lista})")
        return str(drives[0]["id"])

    def carpeta_privada(self):
        for x in self.get_json(f"{KDRIVE_API}/3/drive/{self.drive_id}/files/1/files").get("data", []):
            if x.get("type") == "dir" and x.get("visibility") == "is_private_space":
                return int(x["id"])
        raise SystemExit("No encuentro «Mi carpeta» en kDrive")

    def listar(self, dir_id):
        out = []
        cursor = None
        while True:
            url = f"{KDRIVE_API}/3/drive/{self.drive_id}/files/{dir_id}/files?limit=200"
            if cursor:
                url += f"&cursor={urllib.parse.quote(cursor, safe='')}"
            res = self.get_json(url)
            out.extend(res.get("data", []))
            if not res.get("has_more") or not res.get("cursor"):
                break
            cursor = res["cursor"]
        return out

    def descargar(self, file_id, destino):
        url = f"{KDRIVE_API}/2/drive/{self.drive_id}/files/{file_id}/download"
        req = urllib.request.Request(url, headers=self._headers())
        with urllib.request.urlopen(req, timeout=600) as r, open(destino, "wb") as out:
            shutil.copyfileobj(r, out, length=1 << 20)


def cargar_mapa(ruta):
    if os.path.isfile(ruta):
        with open(ruta, "r", encoding="utf-8") as f:
            return json.load(f)
    return {}


def guardar_mapa(ruta, mapa):
    tmp = ruta + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(mapa, f, indent=2, ensure_ascii=False, sort_keys=True)
    os.replace(tmp, ruta)


def migrar_carpeta(kd, gd, kd_dir_id, gd_dir_id, prefijo, mapa, ruta_mapa, stats):
    for item in kd.listar(kd_dir_id):
        nombre = item.get("name", "")
        if not nombre or nombre.startswith("."):
            continue
        tipo = item.get("type")
        kd_id = str(item["id"])
        ruta_rel = f"{prefijo}/{nombre}" if prefijo else nombre
        if tipo == "dir":
            sub_gd = gd.asegura_carpeta(gd_dir_id, nombre)
            migrar_carpeta(kd, gd, int(kd_id), sub_gd, ruta_rel, mapa, ruta_mapa, stats)
            continue
        if tipo != "file":
            continue

        tam = int(item.get("size") or 0)
        existente = gd.busca_hijo(gd_dir_id, nombre, solo_carpeta=False)
        if existente and int(existente.get("size") or -1) == tam:
            mapa[kd_id] = {"gd": existente["id"], "ruta": ruta_rel, "tam": tam}
            guardar_mapa(ruta_mapa, mapa)
            stats["saltados"] += 1
            print(f"  [ya existe] {ruta_rel} ({tam} B) → {existente['id']}")
            continue

        print(f"  [copiando]  {ruta_rel} ({tam / 1e6:.2f} MB)…", flush=True)
        with tempfile.NamedTemporaryFile(prefix="fam-mig-", delete=False) as tf:
            tmp_path = tf.name
        try:
            kd.descargar(kd_id, tmp_path)
            subido = gd.sube_archivo(gd_dir_id, nombre, tmp_path)
            gd_id = subido["id"]
            mapa[kd_id] = {"gd": gd_id, "ruta": ruta_rel, "tam": tam}
            guardar_mapa(ruta_mapa, mapa)
            stats["copiados"] += 1
            print(f"              OK → Google Drive id {gd_id}")
        except Exception as e:
            stats["errores"] += 1
            print(f"              ERROR en {ruta_rel}: {e}", file=sys.stderr)
        finally:
            if os.path.exists(tmp_path):
                os.unlink(tmp_path)


def firebase_login(api_key, email, password):
    body = json.dumps({"email": email, "password": password, "returnSecureToken": True}).encode("utf-8")
    req = urllib.request.Request(
        f"{FIREBASE_AUTH_URL}?key={urllib.parse.quote(api_key, safe='')}",
        data=body,
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            d = json.loads(r.read().decode("utf-8"))
            return d["localId"], d["idToken"]
    except urllib.error.HTTPError as e:
        raise SystemExit(f"Firebase Auth HTTP {e.code}: {e.read().decode('utf-8', 'replace')[:300]}")


def fb_req(method, path, id_token, payload=None):
    url = f"{FIREBASE_DB}{path}.json?auth={urllib.parse.quote(id_token, safe='')}"
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    headers = {"Content-Type": "application/json"} if data is not None else {}
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read().decode("utf-8") or "null")
    except urllib.error.HTTPError as e:
        raise SystemExit(f"Firebase DB HTTP {e.code} en {method} {path}: {e.read().decode('utf-8', 'replace')[:300]}")


def busca_api_key_firebase():
    """Lee la clave web pública de Firebase desde /config/webKey.json (como Sesion.kt)."""
    env = os.environ.get("FIREBASE_API_KEY", "").strip()
    if env:
        return env
    try:
        with urllib.request.urlopen(f"{FIREBASE_DB}/config/webKey.json", timeout=30) as r:
            k = json.loads(r.read().decode("utf-8") or "null")
            if isinstance(k, str) and k.strip():
                return k.strip()
    except Exception:
        pass
    return input("Firebase Web API Key: ").strip()


def actualizar_firebase(mapa, aplicar):
    if not mapa:
        raise SystemExit("El mapa de migración está vacío. Ejecuta primero la copia de archivos.")
    import time

    api_key = busca_api_key_firebase()
    usuario = os.environ.get("FIREBASE_USER", "").strip() or os.environ.get("FIREBASE_EMAIL", "").strip()
    if not usuario:
        usuario = input("Usuario (o correo) de tu cuenta Familia: ").strip()
    email = usuario if "@" in usuario else f"{usuario.lower()}@{FIREBASE_DOMINIO}"
    password = os.environ.get("FIREBASE_PASSWORD", "") or getpass.getpass("Contraseña de tu cuenta Familia: ")
    uid, id_token = firebase_login(api_key, email, password)
    print(f"\nConectado a Firebase como {uid}. Leyendo nodos…")

    ahora = int(time.time() * 1000)
    lotes = []  # lista de dicts (cada uno un PATCH atómico sobre /users/<uid>)
    resumen_cambios = []
    tocado_libros = False
    tocado_azabache = False

    # 1. Libros de Seshat: /users/<uid>/familia/libros/<kid>/nube/{f, gd}
    # Reglas de nube/reglas.json: cualquier cambio en un campo de familia/<col>/<ref> exige m/<campo> > anterior y u numérico.
    libros = fb_req("GET", f"/users/{uid}/familia/libros", id_token) or {}
    if isinstance(libros, dict):
        for kid, nodo in libros.items():
            if not isinstance(nodo, dict):
                continue
            nube = nodo.get("nube")
            if isinstance(nube, dict) and not nube.get("gd"):
                f_id = str(nube.get("f") or "")
                if f_id in mapa:
                    prev_m = (nodo.get("m") or {}).get("nube") or 0
                    nuevo_m = max(int(prev_m) + 1, ahora)
                    lotes.append(
                        {
                            f"familia/libros/{kid}/nube/gd": mapa[f_id]["gd"],
                            f"familia/libros/{kid}/m/nube": nuevo_m,
                            f"familia/libros/{kid}/u": {".sv": "timestamp"},
                        }
                    )
                    resumen_cambios.append(f"familia/libros/{kid}/nube/gd = {mapa[f_id]['gd']}")
                    tocado_libros = True

    # 2. Bastet: /users/<uid>/hiedra/nube/{roms, saves, states}/<kid>/f/<slot>/{id, gd}
    hiedra_nube = fb_req("GET", f"/users/{uid}/hiedra/nube", id_token) or {}
    if isinstance(hiedra_nube, dict):
        for seccion in ("roms", "saves", "states"):
            bloque = hiedra_nube.get(seccion)
            if not isinstance(bloque, dict):
                continue
            for kid, juego in bloque.items():
                files = juego.get("f") if isinstance(juego, dict) else None
                if not isinstance(files, dict):
                    continue
                for slot, ficha in files.items():
                    if not isinstance(ficha, dict) or ficha.get("gd"):
                        continue
                    kd_id = str(ficha.get("id") or "")
                    if kd_id in mapa:
                        ruta = f"hiedra/nube/{seccion}/{kid}/f/{slot}/gd"
                        lotes.append({ruta: mapa[kd_id]["gd"]})
                        resumen_cambios.append(f"{ruta} = {mapa[kd_id]['gd']}")

    # 3. Jonsu (azabache): /users/<uid>/familia/azabache/nube (array JSON dentro de campo `v`)
    az_nube = fb_req("GET", f"/users/{uid}/familia/azabache/nube", id_token)
    if isinstance(az_nube, dict) and isinstance(az_nube.get("v"), str):
        try:
            envuelto = json.loads(az_nube["v"])
            if isinstance(envuelto, list) and envuelto and isinstance(envuelto[0], list):
                cambiados = 0
                for entry in envuelto[0]:
                    if isinstance(entry, dict) and not entry.get("gd"):
                        kd_id = str(entry.get("f") or "")
                        if kd_id in mapa:
                            entry["gd"] = mapa[kd_id]["gd"]
                            cambiados += 1
                if cambiados:
                    prev_m = (az_nube.get("m") or {}).get("v") or 0
                    nuevo_m = max(int(prev_m) + 1, ahora)
                    lotes.append(
                        {
                            "familia/azabache/nube/v": json.dumps(envuelto, ensure_ascii=False),
                            "familia/azabache/nube/m/v": nuevo_m,
                            "familia/azabache/nube/u": {".sv": "timestamp"},
                        }
                    )
                    resumen_cambios.append(f"familia/azabache/nube/v (+{cambiados} entradas con gd)")
                    tocado_azabache = True
        except Exception as e:
            print(f"Aviso: no se pudo leer familia/azabache/nube: {e}", file=sys.stderr)

    print(f"Encontrados {len(resumen_cambios)} nodos para añadir el campo aditivo `gd` (sin borrar ningún `f` ni `id`).")
    for linea in resumen_cambios[:25]:
        print(f"  + {linea}")
    if len(resumen_cambios) > 25:
        print(f"  … y {len(resumen_cambios) - 25} más.")

    if not lotes:
        print("Nada que actualizar en Firebase.")
        return

    if not aplicar:
        print("\nModo simulación (--dry-run): no se ha escrito nada en Firebase.")
        print("Si todo cuadra, vuelve a ejecutar añadiendo --aplicar.")
        return

    # Agrupamos nodos completos (sin partir nunca un nodo a medias) y actualizamos los sellos al final
    for i in range(0, len(lotes), 40):
        parche = {}
        for item in lotes[i : i + 40]:
            parche.update(item)
        fb_req("PATCH", f"/users/{uid}", id_token, parche)

    sellos = {}
    if tocado_libros:
        sellos["familia_sello/libros"] = {".sv": "timestamp"}
    if tocado_azabache:
        sellos["familia_sello/azabache"] = {".sv": "timestamp"}
    if sellos:
        fb_req("PATCH", f"/users/{uid}", id_token, sellos)

    print("Firebase actualizado correctamente (todos los campos antiguos `f`/`id` se han conservado intactos).")


def main():
    ap = argparse.ArgumentParser(description="Migra archivos de kDrive a Google Drive sin pérdida.")
    ap.add_argument("--mapa", default=MAPA_DEFECTO, help=f"Archivo JSON de correspondencia (defecto: {MAPA_DEFECTO})")
    ap.add_argument(
        "--carpetas",
        default=",".join(CARPETAS_DEFECTO),
        help=f"Carpetas de «Mi carpeta» a migrar (defecto: {','.join(CARPETAS_DEFECTO)})",
    )
    ap.add_argument("--solo-firebase", action="store_true", help="No copia archivos; solo actualiza Firebase usando --mapa")
    ap.add_argument("--actualizar-firebase", action="store_true", help="Añade el campo aditivo `gd` en Firebase")
    ap.add_argument("--aplicar", action="store_true", help="Escribe realmente en Firebase (sin esto es --dry-run)")
    args = ap.parse_args()

    mapa = cargar_mapa(args.mapa)

    if not args.solo_firebase:
        kdrive_token = os.environ.get("KDRIVE_TOKEN", "").strip()
        if not kdrive_token:
            kdrive_token = getpass.getpass("Token de kDrive (o define KDRIVE_TOKEN): ").strip()
        if not kdrive_token:
            raise SystemExit("Falta KDRIVE_TOKEN.")

        cid, csec, rtok, carpeta_raiz, _ = cargar_credenciales()
        kd = KDriveClient(kdrive_token, os.environ.get("KDRIVE_ID"))
        gd = GDrive(cid, csec, rtok)

        home_kd = kd.carpeta_privada()
        raiz_gd = gd.asegura_carpeta("root", carpeta_raiz)
        objetivos = [c.strip() for c in args.carpetas.split(",") if c.strip()]
        hijos_home = {x.get("name"): x for x in kd.listar(home_kd) if x.get("type") == "dir"}

        stats = {"copiados": 0, "saltados": 0, "errores": 0}
        for nombre in objetivos:
            if nombre not in hijos_home:
                print(f"[omitiendo] No existe «Mi carpeta/{nombre}» en kDrive")
                continue
            print(f"\n=== Migrando «{nombre}» → Google Drive «{carpeta_raiz}/{nombre}» ===")
            dest_gd = gd.asegura_carpeta(raiz_gd, nombre)
            migrar_carpeta(kd, gd, int(hijos_home[nombre]["id"]), dest_gd, nombre, mapa, args.mapa, stats)

        print(
            f"\nResumen copia: {stats['copiados']} copiados, {stats['saltados']} ya existían, "
            f"{stats['errores']} errores. Mapa guardado en {args.mapa}."
        )
        if stats["errores"]:
            print("Vuelve a ejecutar el script para reintentar los archivos fallidos (los ya copiados se saltan).")

    if args.actualizar_firebase:
        actualizar_firebase(mapa, aplicar=args.aplicar)


if __name__ == "__main__":
    main()
