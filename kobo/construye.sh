#!/bin/bash
# Prepara lo que Seshat copia al Kobo (marfil/src/main/assets/kobo/): el programa compilado para el Kobo (ARM, sin dependencias), su
# lanzador, la entrada del menú de NickelMenu y el instalador de NickelMenu (fork para el firmware 5/6, MIT). Hay que lanzarlo antes
# de compilar Seshat cuando cambie algo de kobo/.
set -euo pipefail
cd "$(dirname "$0")"
export GOTOOLCHAIN=local
go vet ./...
go test ./...
DEST=../marfil/src/main/assets/kobo
mkdir -p "$DEST"
GOOS=linux GOARCH=arm GOARM=7 CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o "$DEST/seshat-kobo" .
cp paquete/seshat.sh paquete/nm-seshat paquete/NickelMenu-Kobo.tgz paquete/LICENSE-NickelMenu "$DEST/"
grep -o 'const version = "[^"]*"' main.go | cut -d'"' -f2 > "$DEST/version"
echo "Listo: $DEST ($(cat "$DEST/version"), $(du -h "$DEST/seshat-kobo" | cut -f1))"
