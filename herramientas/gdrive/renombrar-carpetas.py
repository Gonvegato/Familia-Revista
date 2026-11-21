#!/usr/bin/env python3
"""Renombra en Google Drive las carpetas de la familia a los nombres actuales (Polaris, Lyra, Orion, Nova, Sirius, Pulsar).

Si la carpeta nueva no existe, simplemente renombra la antigua (instantáneo, conserva todos los IDs de archivo en Firebase).
Si ya existía una carpeta con el nombre nuevo (p. ej. porque alguna app ya subió algo), fusiona todo el contenido de la
carpeta antigua dentro de la nueva (conservando también todos los IDs de archivo) y manda la carpeta antigua vacía a la papelera.

Credenciales (por orden de prioridad):
  1. Variables de entorno: GDRIVE_CLIENT_ID, GDRIVE_CLIENT_SECRET, GDRIVE_REFRESH_TOKEN,
     y opcionalmente GDRIVE_CARPETA (por defecto «Familia»).
  2. Archivo ~/.config/familia-gdrive.json.
"""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

TOKEN_URL = "https://oauth2.googleapis.com/token"
DRIVE_API = "https://www.googleapis.com/drive/v3"
FOLDER_MIME = "application/vnd.google-apps.folder"
CONFIG_PATH = os.path.expanduser("~/.config/familia-gdrive.json")

RENOMBRES = [
    ("Atrio", "Polaris"),
    ("Jonsu", "Polaris"),
    ("Marfil", "Lyra"),
    ("Seshat", "Lyra"),
    ("Hiedra", "Orion"),
    ("Bastet", "Orion"),
    ("Mostaza", "Sirius"),
    ("Hathor", "Sirius"),
    ("Cobalto", "Nova"),
    ("Thot", "Nova"),
    ("Cobre", "Pulsar"),
    ("Nut", "Pulsar"),
]


def cargar_credenciales():
    cfg = {}
    if os.path.isfile(CONFIG_PATH):
        with open(CONFIG_PATH, "r", encoding="utf-8") as f:
            cfg = json.load(f)
    cid = (os.environ.get("GDRIVE_CLIENT_ID") or cfg.get("client_id") or "").strip()
    csec = (os.environ.get("GDRIVE_CLIENT_SECRET") or cfg.get("client_secret") or "").strip()
    rtok = (os.environ.get("GDRIVE_REFRESH_TOKEN") or cfg.get("refresh_token") or "").strip()
    carpeta = (os.environ.get("GDRIVE_CARPETA") or cfg.get("carpeta") or "Familia").strip() or "Familia"
    if not (cid and csec and rtok):
        raise SystemExit("Faltan credenciales de Google Drive (GDRIVE_CLIENT_ID, GDRIVE_CLIENT_SECRET, GDRIVE_REFRESH_TOKEN).")
    return cid, csec, rtok, carpeta


class GDrive:
    def __init__(self, client_id, client_secret, refresh_token):
        self.client_id = client_id
        self.client_secret = client_secret
        self.refresh_token = refresh_token
        self._token = None

    def token(self, force=False):
        if self._token and not force:
            return self._token
        body = urllib.parse.urlencode({
            "client_id": self.client_id,
            "client_secret": self.client_secret,
            "refresh_token": self.refresh_token,
            "grant_type": "refresh_token",
        }).encode("utf-8")
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
                    raw = r.read().decode("utf-8")
                    return json.loads(raw) if raw.strip() else {}
            except urllib.error.HTTPError as e:
                if e.code == 401 and intento == 0:
                    continue
                raise SystemExit(f"Drive HTTP {e.code} en {method} {url.split('?')[0]}: {e.read().decode('utf-8', 'replace')[:300]}")

    def listar_hijos(self, parent_id, solo_carpetas=False):
        q = f"'{parent_id}' in parents and trashed = false"
        if solo_carpetas:
            q += f" and mimeType = '{FOLDER_MIME}'"
        out = []
        page_token = None
        while True:
            url = f"{DRIVE_API}/files?q={urllib.parse.quote(q, safe='')}&fields=nextPageToken,files(id,name,mimeType)&pageSize=1000"
            if page_token:
                url += f"&pageToken={urllib.parse.quote(page_token, safe='')}"
            res = self.json_req("GET", url)
            out.extend(res.get("files", []))
            page_token = res.get("nextPageToken")
            if not page_token:
                break
        return out

    def busca_carpeta(self, parent_id, nombre):
        esc = nombre.replace("\\", "\\\\").replace("'", "\\'")
        q = f"'{parent_id}' in parents and name = '{esc}' and mimeType = '{FOLDER_MIME}' and trashed = false"
        url = f"{DRIVE_API}/files?q={urllib.parse.quote(q, safe='')}&fields=files(id,name)&pageSize=5"
        files = self.json_req("GET", url).get("files", [])
        return files[0] if files else None

    def renombra(self, file_id, nuevo_nombre):
        return self.json_req("PATCH", f"{DRIVE_API}/files/{file_id}?fields=id,name", {"name": nuevo_nombre})

    def mueve(self, file_id, viejo_padre, nuevo_padre):
        url = f"{DRIVE_API}/files/{file_id}?addParents={urllib.parse.quote(nuevo_padre, safe='')}&removeParents={urllib.parse.quote(viejo_padre, safe='')}&fields=id,name"
        return self.json_req("PATCH", url, {})

    def a_papelera(self, file_id):
        return self.json_req("PATCH", f"{DRIVE_API}/files/{file_id}?fields=id,trashed", {"trashed": True})

    def fusiona_carpeta(self, src_id, dst_id):
        movidos = 0
        hijos_src = self.listar_hijos(src_id)
        hijos_dst = {h["name"]: h for h in self.listar_hijos(dst_id) if h.get("mimeType") == FOLDER_MIME}
        for h in hijos_src:
            if h.get("mimeType") == FOLDER_MIME and h["name"] in hijos_dst:
                movidos += self.fusiona_carpeta(h["id"], hijos_dst[h["name"]]["id"])
            else:
                self.mueve(h["id"], src_id, dst_id)
                movidos += 1
        self.a_papelera(src_id)
        return movidos


def main():
    cid, csec, rtok, carpeta = cargar_credenciales()
    gd = GDrive(cid, csec, rtok)

    raiz = gd.busca_carpeta("root", carpeta)
    if not raiz:
        raise SystemExit(f"No se encontró la carpeta raíz «{carpeta}» en Google Drive.")
    raiz_id = raiz["id"]
    print(f"Carpeta raíz: «{carpeta}» (id {raiz_id})")

    antes = sorted(c["name"] for c in gd.listar_hijos(raiz_id, solo_carpetas=True))
    print(f"Carpetas antes: {', '.join(antes) or '(ninguna)'}")

    cambios = 0
    for viejo, nuevo in RENOMBRES:
        src = gd.busca_carpeta(raiz_id, viejo)
        if not src:
            continue
        dst = gd.busca_carpeta(raiz_id, nuevo)
        if dst is None:
            gd.renombra(src["id"], nuevo)
            print(f"  Renombrada: {carpeta}/{viejo} → {carpeta}/{nuevo}")
        else:
            movidos = gd.fusiona_carpeta(src["id"], dst["id"])
            print(f"  Fusionada: {carpeta}/{viejo} → {carpeta}/{nuevo} ({movidos} elementos movidos; carpeta antigua a la papelera)")
        cambios += 1

    if cambios == 0:
        print("No había carpetas con nombres antiguos por renombrar.")

    despues = sorted(c["name"] for c in gd.listar_hijos(raiz_id, solo_carpetas=True))
    print(f"Carpetas después: {', '.join(despues) or '(ninguna)'}")


if __name__ == "__main__":
    main()
