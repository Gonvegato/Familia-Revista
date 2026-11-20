#!/usr/bin/env python3
"""Sube versiones (Android .apk o escritorio -mac.dmg / -linux.deb / -linux.tar.gz) a Google Drive.

Uso:
  python3 herramientas/gdrive/subir-version.py Jonsu-1.3.0.apk Bastet-0.5.0-mac.dmg …

Cada archivo se sube a «<carpeta>/versiones» (por defecto «Familia/versiones») mediante subida
reanuable (resumable upload) y actualiza el archivo existente si ya hay uno con el mismo nombre.
Además calcula y sube automáticamente «<archivo>.sha256» para que el actualizador de escritorio
verifique la integridad antes de instalar.

Credenciales (por orden de prioridad):
  1. Variables de entorno: GDRIVE_CLIENT_ID, GDRIVE_CLIENT_SECRET, GDRIVE_REFRESH_TOKEN,
     y opcionalmente GDRIVE_CARPETA (por defecto «Familia») y GDRIVE_SUBCARPETA («versiones»).
  2. Archivo ~/.config/familia-gdrive.json (creado con herramientas/gdrive/autorizar.py --guardar).
"""
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

TOKEN_URL = "https://oauth2.googleapis.com/token"
DRIVE_API = "https://www.googleapis.com/drive/v3"
UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
FOLDER_MIME = "application/vnd.google-apps.folder"
CONFIG_PATH = os.path.expanduser("~/.config/familia-gdrive.json")
CHUNK = 4 * 1024 * 1024  # 4 MB (múltiplo de 256 KB requerido por Google Drive)

NOMBRE_VALIDO = re.compile(
    r"^(?:Polaris|Lyra|LyraKobo|Orion|Nova|Sirius|Pulsar|Azabache|Cobalto|Hiedra|Marfil|Mostaza|Cobre|Jonsu|Thot|Bastet|Seshat|SesKobo|Hathor|Nut)"
    r"-\d+(?:\.\d+)*(?:-kobo(?:\.\d+)?)?(?:\.apk|-(?:mac\.dmg|linux\.deb|linux\.tar\.gz))$"
)


def cargar_credenciales():
    cfg = {}
    if os.path.isfile(CONFIG_PATH):
        with open(CONFIG_PATH, "r", encoding="utf-8") as f:
            cfg = json.load(f)
    cid = (os.environ.get("GDRIVE_CLIENT_ID") or cfg.get("client_id") or "").strip()
    csec = (os.environ.get("GDRIVE_CLIENT_SECRET") or cfg.get("client_secret") or "").strip()
    rtok = (os.environ.get("GDRIVE_REFRESH_TOKEN") or cfg.get("refresh_token") or "").strip()
    carpeta = (os.environ.get("GDRIVE_CARPETA") or cfg.get("carpeta") or "Familia").strip() or "Familia"
    subcarpeta = (os.environ.get("GDRIVE_SUBCARPETA") or "versiones").strip("/") or "versiones"
    if not (cid and csec and rtok):
        raise SystemExit(
            "Faltan credenciales de Google Drive. Ejecuta:\n"
            "  python3 herramientas/gdrive/autorizar.py --guardar\n"
            "o define GDRIVE_CLIENT_ID, GDRIVE_CLIENT_SECRET y GDRIVE_REFRESH_TOKEN."
        )
    return cid, csec, rtok, carpeta, subcarpeta


class GDrive:
    def __init__(self, client_id, client_secret, refresh_token):
        self.client_id = client_id
        self.client_secret = client_secret
        self.refresh_token = refresh_token
        self._token = None

    def token(self, force=False):
        if self._token and not force:
            return self._token
        body = urllib.parse.urlencode(
            {
                "client_id": self.client_id,
                "client_secret": self.client_secret,
                "refresh_token": self.refresh_token,
                "grant_type": "refresh_token",
            }
        ).encode("utf-8")
        req = urllib.request.Request(
            TOKEN_URL,
            data=body,
            method="POST",
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                d = json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            raise SystemExit(f"OAuth HTTP {e.code}: {e.read().decode('utf-8', 'replace')[:300]}")
        self._token = d["access_token"]
        return self._token

    def json_req(self, method, url, payload=None):
        data = json.dumps(payload).encode("utf-8") if payload is not None else None
        for intento in range(2):
            headers = {"Authorization": f"Bearer {self.token(force=intento == 1)}"}
            if data is not None:
                headers["Content-Type"] = "application/json; charset=UTF-8"
            req = urllib.request.Request(url, data=data, method=method, headers=headers)
            try:
                with urllib.request.urlopen(req, timeout=60) as r:
                    return json.loads(r.read().decode("utf-8") or "{}")
            except urllib.error.HTTPError as e:
                if e.code == 401 and intento == 0:
                    continue
                raise SystemExit(f"Drive HTTP {e.code} en {method} {url.split('?')[0]}: {e.read().decode('utf-8', 'replace')[:300]}")

    def busca_hijo(self, parent_id, nombre, solo_carpeta=False):
        esc = nombre.replace("\\", "\\\\").replace("'", "\\'")
        q = f"'{parent_id}' in parents and name = '{esc}' and trashed = false"
        if solo_carpeta:
            q += f" and mimeType = '{FOLDER_MIME}'"
        url = f"{DRIVE_API}/files?q={urllib.parse.quote(q, safe='')}&fields=files(id,name,size,mimeType)&pageSize=5"
        files = self.json_req("GET", url).get("files", [])
        return files[0] if files else None

    def asegura_carpeta(self, parent_id, ruta):
        actual = parent_id
        for parte in [p.strip() for p in ruta.split("/") if p.strip()]:
            f = self.busca_hijo(actual, parte, solo_carpeta=True)
            if f:
                actual = f["id"]
            else:
                creada = self.json_req(
                    "POST",
                    f"{DRIVE_API}/files?fields=id,name",
                    {"name": parte, "mimeType": FOLDER_MIME, "parents": [actual]},
                )
                actual = creada["id"]
        return actual

    def sube_archivo(self, parent_id, nombre, ruta_o_bytes, mime="application/octet-stream"):
        existente = self.busca_hijo(parent_id, nombre, solo_carpeta=False)
        if isinstance(ruta_o_bytes, bytes):
            tam = len(ruta_o_bytes)
            stream_factory = lambda: _BytesReader(ruta_o_bytes)
        else:
            tam = os.path.getsize(ruta_o_bytes)
            stream_factory = lambda: open(ruta_o_bytes, "rb")

        if existente:
            ini_url = f"{UPLOAD_API}/files/{existente['id']}?uploadType=resumable&fields=id,name,size"
            metodo = "PATCH"
            meta = {"name": nombre}
        else:
            ini_url = f"{UPLOAD_API}/files?uploadType=resumable&fields=id,name,size"
            metodo = "POST"
            meta = {"name": nombre, "parents": [parent_id]}

        meta_bytes = json.dumps(meta).encode("utf-8")
        req = urllib.request.Request(
            ini_url,
            data=meta_bytes,
            method=metodo,
            headers={
                "Authorization": f"Bearer {self.token()}",
                "Content-Type": "application/json; charset=UTF-8",
                "X-Upload-Content-Type": mime,
                "X-Upload-Content-Length": str(tam),
            },
        )
        with urllib.request.urlopen(req, timeout=60) as r:
            upload_url = r.headers.get("Location")
        if not upload_url:
            raise SystemExit("Google Drive no devolvió cabecera Location para la subida reanuable")

        if tam == 0:
            put = urllib.request.Request(upload_url, data=b"", method="PUT", headers={"Content-Type": mime})
            with urllib.request.urlopen(put, timeout=120) as r:
                return json.loads(r.read().decode("utf-8"))

        with stream_factory() as f:
            offset = 0
            while offset < tam:
                parte = f.read(CHUNK)
                if not parte:
                    break
                fin = offset + len(parte) - 1
                put = urllib.request.Request(
                    upload_url,
                    data=parte,
                    method="PUT",
                    headers={
                        "Content-Type": mime,
                        "Content-Range": f"bytes {offset}-{fin}/{tam}",
                    },
                )
                try:
                    with urllib.request.urlopen(put, timeout=300) as r:
                        offset += len(parte)
                        if offset >= tam:
                            return json.loads(r.read().decode("utf-8"))
                except urllib.error.HTTPError as e:
                    if e.code == 308:
                        offset += len(parte)
                        continue
                    raise SystemExit(f"Drive subida HTTP {e.code}: {e.read().decode('utf-8', 'replace')[:300]}")
        raise SystemExit("Subida incompleta sin respuesta final de Google Drive")


class _BytesReader:
    def __init__(self, data):
        self._data = data
        self._pos = 0

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass

    def read(self, n):
        chunk = self._data[self._pos : self._pos + n]
        self._pos += len(chunk)
        return chunk


def sha256_archivo(ruta):
    h = hashlib.sha256()
    with open(ruta, "rb") as f:
        while True:
            b = f.read(1 << 20)
            if not b:
                break
            h.update(b)
    return h.hexdigest()


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)

    cid, csec, rtok, carpeta, subcarpeta = cargar_credenciales()
    gd = GDrive(cid, csec, rtok)
    dir_id = gd.asegura_carpeta("root", f"{carpeta}/{subcarpeta}")

    for ruta in sys.argv[1:]:
        if not os.path.isfile(ruta):
            raise SystemExit(f"No existe el archivo: {ruta}")
        nombre = os.path.basename(ruta)
        if not NOMBRE_VALIDO.fullmatch(nombre):
            raise SystemExit(
                f"Nombre no válido: {nombre}\n"
                "Debe ser <App>-<versión>.apk, -mac.dmg, -linux.deb o -linux.tar.gz"
            )
        tam = os.path.getsize(ruta)
        digest = sha256_archivo(ruta)
        print(f"Subiendo {nombre} ({tam / 1e6:.2f} MB) a «{carpeta}/{subcarpeta}»…")
        res = gd.sube_archivo(dir_id, nombre, ruta)
        print(f"  OK: {res.get('name')} (id {res.get('id')}, {res.get('size')} bytes)")

        sha_nombre = f"{nombre}.sha256"
        sha_contenido = f"{digest}  {nombre}\n".encode("utf-8")
        res_sha = gd.sube_archivo(dir_id, sha_nombre, sha_contenido, mime="text/plain")
        print(f"  SHA-256: {digest[:16]}… → {res_sha.get('name')} (id {res_sha.get('id')})")


if __name__ == "__main__":
    main()
