#!/bin/sh
# Seshat: lo lanza libseshat.so cada vez que arranca la interfaz del Kobo (firmware 5 y 6; no hace falta NickelMenu).
#  1. El envío automático: deja en marcha el vigilante de Seshat, que manda solo cada vez que el Kobo tiene Wi-Fi.
#  2. El diagnóstico: a los 2 minutos, en .adds/seshat/diagnostico.txt (versión, complementos cargados, registro del sistema).
# Para que deje de hacer cualquier cosa, basta con borrar este archivo.
PATH=/bin:/usr/bin:/sbin:/usr/sbin
D=/mnt/onboard/.adds/seshat
# (una sola vez por encendido: si la interfaz del Kobo se reinicia, la pieza se vuelve a cargar)
mkdir /tmp/seshat-arranque 2>/dev/null || exit 0

# 1. El vigilante (si el Kobo está preparado desde SesKobo; él mismo se asegura de que solo haya uno)
VIG="no: falta algo de .adds/seshat (seshat.sh, seshat-kobo o cuenta.json)"
if [ -f "$D/seshat.sh" ] && [ -f "$D/seshat-kobo" ] && [ -f "$D/cuenta.json" ]; then
	/bin/sh "$D/seshat.sh" vigila </dev/null >"/tmp/seshat-arranque/vigila.txt" 2>&1 &
	VIG="lanzado (pid $!)"
fi

# 2. El diagnóstico
sleep 120
F="$D/diagnostico.txt"
{
	echo "== Seshat: diagnóstico del arranque ($(date))"
	echo "-- versión";            cat /mnt/onboard/.kobo/version 2>&1; uname -a 2>&1
	echo "-- complementos";       ls -la /usr/local/Kobo/imageformats 2>&1
	echo "-- a prueba de fallos"; ls -la /usr/local/Kobo/*.failsafe /usr/local/Kobo/imageformats/*.failsafe 2>&1
	echo "-- cargados en la interfaz del Kobo"
	for p in $(pidof nickel 2>/dev/null); do echo "nickel $p:"; grep -E "imageformats|libnm|nickelinksync|seshat" /proc/$p/maps 2>&1 | awk '{print $6}' | sort -u; done
	echo "-- ¿le falta algo a cada complemento? (lo que diga «not found» es lo que el Kobo no tiene)"
	LDSO=$(ls /lib/ld-linux*.so* 2>/dev/null | head -n 1)
	for f in /usr/local/Kobo/imageformats/*.so; do echo "[$f]"; "$LDSO" --list "$f" 2>&1 | grep -iE "not found|error|version" || echo "  (no le falta nada)"; done
	echo "-- Qt del Kobo";         ls -la /usr/lib/libQt6Core.so* /usr/lib/libQt6Widgets.so* /usr/lib/libQt6Gui.so* 2>&1
	echo "-- glibc del Kobo";      "$LDSO" --version 2>&1 | head -n 1
	echo "-- .adds";              ls -la /mnt/onboard/.adds /mnt/onboard/.adds/nm /mnt/onboard/.adds/seshat 2>&1
	echo "-- vigilante: $VIG";     cat /tmp/seshat-arranque/vigila.txt 2>&1
	echo "-- registro de Seshat (lo último)"; tail -n 15 "$D/registro.txt" 2>&1
	echo "-- procesos";           ps 2>&1 | grep -iE "seshat|nickel" | grep -v grep
	echo "-- registro del sistema (lo último)"
	if command -v logread >/dev/null 2>&1; then logread 2>&1 | tail -n 400; else echo "(no hay logread)"; fi
	echo "-- registro del núcleo (lo último)"; dmesg 2>&1 | tail -n 80
} > "$F.nuevo" 2>&1
mv -f "$F.nuevo" "$F"
sync
