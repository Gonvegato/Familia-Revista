#!/usr/bin/env python3
"""Obtiene un refresh_token de Google Drive (OAuth2) para las apps de la familia.

Uso:
  python3 herramientas/gdrive/autorizar.py
  python3 herramientas/gdrive/autorizar.py --client-id XXX --client-secret YYY [--carpeta Familia] [--guardar]

Antes (una sola vez en Google Cloud Console, gratis):
  1. Crea un proyecto (p. ej. "Familia") y activa "Google Drive API".
  2. En "Google Auth Platform / Pantalla de consentimiento OAuth":
     - Tipo: Externo (o Interno si usas Workspace).
     - Añade tu correo como usuario de prueba y, muy importante, pulsa
       "Publicar aplicación" (en modo "Testing" Google caduca el refresh_token a los 7 días).
  3. En "Credenciales" → "Crear credenciales" → "ID de cliente de OAuth" → "Aplicación de escritorio".
  4. Copia el Client ID y el Client Secret y ejecuta este script.

Al terminar te dará los 4 valores listos para pegar en Jonsu (Ajustes → Avanzado → Tu nube);
al guardarlos allí, se sincronizan solos con todas las apps (Android y escritorio).
"""
import argparse
import http.server
import json
import os
import secrets
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request
import webbrowser

AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
TOKEN_URL = "https://oauth2.googleapis.com/token"
DRIVE_API = "https://www.googleapis.com/drive/v3"
SCOPE = "https://www.googleapis.com/auth/drive"
CONFIG_PATH = os.path.expanduser("~/.config/familia-gdrive.json")


def post_form(url, data):
    body = urllib.parse.urlencode(data).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body,
        method="POST",
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raise SystemExit(f"Google OAuth HTTP {e.code}: {e.read().decode('utf-8', 'replace')}")


def ensure_root_folder(access_token, folder_name):
    q = (
        f"name = '{folder_name.replace(chr(39), chr(92) + chr(39))}' "
        "and 'root' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
    )
    url = f"{DRIVE_API}/files?q={urllib.parse.quote(q, safe='')}&fields=files(id,name)&pageSize=5"
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {access_token}"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        files = json.loads(resp.read().decode("utf-8")).get("files", [])
    if files:
        return files[0]["id"], False

    body = json.dumps(
        {
            "name": folder_name,
            "mimeType": "application/vnd.google-apps.folder",
            "parents": ["root"],
        }
    ).encode("utf-8")
    req = urllib.request.Request(
        f"{DRIVE_API}/files?fields=id,name",
        data=body,
        method="POST",
        headers={
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json; charset=UTF-8",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        created = json.loads(resp.read().decode("utf-8"))
    return created["id"], True


def run_loopback_flow(client_id, state):
    result = {"code": None, "error": None}
    done = threading.Event()

    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, fmt, *args):
            pass

        def do_GET(self):
            parsed = urllib.parse.urlparse(self.path)
            qs = urllib.parse.parse_qs(parsed.query)
            if qs.get("state", [""])[0] != state:
                result["error"] = "estado OAuth no coincide"
            elif "error" in qs:
                result["error"] = qs["error"][0]
            elif "code" in qs:
                result["code"] = qs["code"][0]
            else:
                result["error"] = "no llegó el código de autorización"
            html = (
                "<!doctype html><meta charset='utf-8'>"
                "<body style='font-family:sans-serif;padding:2rem'>"
                "<h2>Listo</h2><p>Ya puedes cerrar esta pestaña y volver a la terminal.</p>"
                "</body>"
            ).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(html)))
            self.end_headers()
            self.wfile.write(html)
            done.set()

    server = http.server.HTTPServer(("127.0.0.1", 0), Handler)
    port = server.server_address[1]
    redirect_uri = f"http://127.0.0.1:{port}"
    t = threading.Thread(target=server.serve_forever, daemon=True)
    t.start()

    params = {
        "client_id": client_id,
        "redirect_uri": redirect_uri,
        "response_type": "code",
        "scope": SCOPE,
        "access_type": "offline",
        "prompt": "consent",
        "state": state,
    }
    url = f"{AUTH_URL}?{urllib.parse.urlencode(params)}"
    print("\nAbre este enlace en tu navegador e inicia sesión con tu cuenta de Google:\n")
    print(f"  {url}\n")
    try:
        webbrowser.open(url)
    except Exception:
        pass
    print("Esperando autorización en 127.0.0.1… (Ctrl+C para cancelar)")
    try:
        done.wait()
    finally:
        server.shutdown()
        server.server_close()

    if result["error"]:
        raise SystemExit(f"Autorización fallida: {result['error']}")
    return result["code"], redirect_uri


def main():
    ap = argparse.ArgumentParser(description="Autoriza Google Drive para la suite Familia.")
    ap.add_argument("--client-id", default=os.environ.get("GDRIVE_CLIENT_ID", ""))
    ap.add_argument("--client-secret", default=os.environ.get("GDRIVE_CLIENT_SECRET", ""))
    ap.add_argument("--carpeta", default=os.environ.get("GDRIVE_CARPETA", "Familia"))
    ap.add_argument(
        "--guardar",
        action="store_true",
        help=f"Guardar credenciales en {CONFIG_PATH} (permisos 0600) para subir-version.py y migrar-kdrive.py",
    )
    args = ap.parse_args()

    client_id = args.client_id.strip() or input("Client ID de Google OAuth: ").strip()
    client_secret = args.client_secret.strip() or input("Client Secret de Google OAuth: ").strip()
    carpeta = args.carpeta.strip() or "Familia"
    if not client_id or not client_secret:
        raise SystemExit("Faltan Client ID o Client Secret.")

    state = secrets.token_urlsafe(24)
    code, redirect_uri = run_loopback_flow(client_id, state)

    tok = post_form(
        TOKEN_URL,
        {
            "code": code,
            "client_id": client_id,
            "client_secret": client_secret,
            "redirect_uri": redirect_uri,
            "grant_type": "authorization_code",
        },
    )
    refresh_token = tok.get("refresh_token", "").strip()
    access_token = tok.get("access_token", "").strip()
    if not refresh_token or not access_token:
        raise SystemExit(
            "Google no devolvió refresh_token. Revoca el acceso previo en "
            "https://myaccount.google.com/permissions y vuelve a ejecutar este script."
        )

    folder_id, created = ensure_root_folder(access_token, carpeta)
    accion = "creada" if created else "encontrada"
    print(f"\nConexión verificada. Carpeta raíz «{carpeta}» {accion} (id: {folder_id}).")
    print("\n--- Copia estos valores en Jonsu (Ajustes → Avanzado → Tu nube) ---")
    print(f"Client ID:     {client_id}")
    print(f"Client Secret: {client_secret}")
    print(f"Refresh Token: {refresh_token}")
    print(f"Carpeta raíz:  {carpeta}")
    print("-------------------------------------------------------------------\n")

    if args.guardar:
        os.makedirs(os.path.dirname(CONFIG_PATH), exist_ok=True)
        cfg = {
            "client_id": client_id,
            "client_secret": client_secret,
            "refresh_token": refresh_token,
            "carpeta": carpeta,
        }
        fd = os.open(CONFIG_PATH, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(cfg, f, indent=2)
        print(f"Guardado en {CONFIG_PATH} (solo lectura/escritura para tu usuario).")
    else:
        print("Para usarlo también en los scripts de terminal, añade --guardar o exporta:")
        print(f"  export GDRIVE_CLIENT_ID={json.dumps(client_id)}")
        print(f"  export GDRIVE_CLIENT_SECRET={json.dumps(client_secret)}")
        print(f"  export GDRIVE_REFRESH_TOKEN={json.dumps(refresh_token)}")
        print(f"  export GDRIVE_CARPETA={json.dumps(carpeta)}")


if __name__ == "__main__":
    main()
