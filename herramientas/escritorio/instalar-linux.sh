#!/bin/sh
# Instala __APP__ para tu usuario, sin sudo ni contraseña: la app en ~/.local/share/familia-apps/__APP__ y su icono en el menú de
# aplicaciones. Vale para cualquier Linux de 64 bits (CachyOS, Arch, Fedora, Ubuntu…): la app lleva dentro todo lo que necesita.
#
#   ./instalar.sh               instala (o pone al día) __APP__
#   ./instalar.sh --al-iniciar  además, que se abra sola al entrar en tu sesión
#   ./instalar.sh --quitar      la desinstala (lo tuyo está en la cuenta y en ~/.local/share/familia: eso no se toca)
#
# Una vez instalada, se actualiza sola desde tu kDrive (la app usa este mismo archivo para ponerse la versión nueva).
# (Plantilla: herramientas/escritorio/instalar-linux.sh; el empaquetado de GitHub pone el nombre de cada app.)
set -e
APP="__APP__"
ID="familia-__ID__"
ORIGEN="$(cd "$(dirname "$0")" && pwd)/$APP"
DATOS="${XDG_DATA_HOME:-$HOME/.local/share}"
DESTINO="$DATOS/familia-apps/$APP"
MENU="$DATOS/applications/$ID.desktop"
INICIO="${XDG_CONFIG_HOME:-$HOME/.config}/autostart/$ID.desktop"
AL_INICIAR=0
CALLADA=0
for a in "$@"; do
  case "$a" in
    --al-iniciar) AL_INICIAR=1 ;;
    --callada) CALLADA=1 ;;
    --destino=*) DESTINO="${a#--destino=}" ;;
    --quitar)
      rm -rf "$DESTINO" "$MENU" "$INICIO"
      command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$DATOS/applications" >/dev/null 2>&1 || true
      echo "$APP quitada."; exit 0 ;;
    *) echo "No entiendo «$a». Mira las primeras líneas de este archivo." >&2; exit 2 ;;
  esac
done

if [ ! -x "$ORIGEN/bin/$APP" ]; then
  echo "No encuentro $ORIGEN/bin/$APP: descomprime el .tar.gz entero y ejecuta instalar.sh desde su carpeta." >&2
  exit 1
fi

mkdir -p "$(dirname "$DESTINO")" "$DATOS/applications"
# La nueva al lado y luego el cambio de golpe: si algo falla a medias, la de antes sigue entera
rm -rf "$DESTINO.nueva" "$DESTINO.vieja"
cp -a "$ORIGEN" "$DESTINO.nueva"
if [ -d "$DESTINO" ]; then mv "$DESTINO" "$DESTINO.vieja"; fi
mv "$DESTINO.nueva" "$DESTINO"
rm -rf "$DESTINO.vieja"

entrada() {
  cat <<FIN
[Desktop Entry]
Type=Application
Name=$APP
GenericName=__GENERICO__
Comment=__DESCRIPCION__
Exec="$DESTINO/bin/$APP"${1:-}
Icon=$DESTINO/lib/$APP.png
Terminal=false
Categories=__CATEGORIAS__
StartupWMClass=__WMCLASS__
FIN
}
entrada > "$MENU"
chmod 644 "$MENU"
# Si ya se abría al entrar en la sesión, eso también se pone al día (al actualizarse sola, por ejemplo)
if [ -f "$INICIO" ]; then AL_INICIAR=1; fi
if [ "$AL_INICIAR" = 1 ]; then
  mkdir -p "$(dirname "$INICIO")"
  # (al entrar en la sesión, con lo suyo de esta app: Hiedra se abre ya en la sala, para la tele y el mando)
  entrada "__AL_INICIAR__" > "$INICIO"
  echo "X-GNOME-Autostart-enabled=true" >> "$INICIO"
fi
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$DATOS/applications" >/dev/null 2>&1 || true

if [ "$CALLADA" = 0 ]; then
  echo "$APP instalada en $DESTINO."
  echo "Está en el menú de aplicaciones; también se abre con: \"$DESTINO/bin/$APP\""
  if [ "$AL_INICIAR" = 1 ]; then echo "Se abrirá sola al entrar en tu sesión (para quitarlo: borra $INICIO)."; fi
fi
