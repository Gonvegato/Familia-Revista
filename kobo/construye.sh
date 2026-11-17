#!/bin/bash
# Prepara lo que Seshat copia al Kobo (marfil/src/kobo/assets/kobo/, solo en SesKobo): el programa compilado para el Kobo (ARM, sin dependencias), su
# lanzador, la entrada del menú de NickelMenu y el instalador de NickelMenu (fork para el firmware 5/6, MIT) y NickelInkSync (MIT), juntos. Hay que lanzarlo antes
# de compilar Seshat cuando cambie algo de kobo/.
set -euo pipefail
cd "$(dirname "$0")"
export GOTOOLCHAIN=local
go vet ./...
go test ./...
DEST=../marfil/src/kobo/assets/kobo
mkdir -p "$DEST"
GOOS=linux GOARCH=arm GOARM=7 CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o "$DEST/seshat-kobo" .
cp paquete/seshat.sh paquete/nm-seshat paquete/LICENSE-NickelMenu paquete/LICENSE-NickelInkSync paquete/nickelinksync "$DEST/"
# Un solo instalador con los dos plugins (el Kobo lo descomprime en /usr/local/Kobo: imageformats/libnm.so y libnickelinksync.so)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
tar xzf paquete/NickelMenu-Kobo.tgz -C "$T"; tar xzf paquete/NickelInkSync-Kobo.tgz -C "$T"
# Los avisos de NickelInkSync en la pantalla del Kobo, en español (vienen en alemán)
python3 paquete/traduce_nis.py "$T/imageformats/libnickelinksync.so"
tar czf "$DEST/Kobo.tgz" --owner=root --group=root --mode="u=rwX,go=rX" -C "$T" imageformats/libnm.so imageformats/libnickelinksync.so
rm -f "$DEST/NickelMenu-Kobo.tgz"
grep -o 'const version = "[^"]*"' main.go | cut -d'"' -f2 > "$DEST/version"
echo "Listo: $DEST ($(cat "$DEST/version"), $(du -h "$DEST/seshat-kobo" | cut -f1))"
