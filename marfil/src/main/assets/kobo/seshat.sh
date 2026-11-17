#!/bin/sh
# Lanza seshat-kobo (lo llama el menú «Enviar a Seshat» de NickelMenu). Se ejecuta desde una copia en /tmp: así el programa no deja
# ocupada la memoria del Kobo y se puede seguir enchufando por USB. La primera vez deja también en marcha el vigilante, que manda
# solo cada vez que el Kobo tiene Wi-Fi (hasta que se apague el Kobo; al volver a encenderlo, basta con pulsar «Enviar a Seshat»).
PATH=/bin:/usr/bin:/sbin:/usr/sbin
D=/mnt/onboard/.adds/seshat
B=/tmp/seshat-kobo
if [ ! -x "$B" ] || [ "$D/seshat-kobo" -nt "$B" ]; then
	cp "$D/seshat-kobo" "$B.nuevo" && chmod 755 "$B.nuevo" && mv "$B.nuevo" "$B" || exit 1
fi
cd /
case "$1" in
	espera)
		if command -v setsid >/dev/null 2>&1; then
			setsid "$B" vigila </dev/null >/dev/null 2>&1 &
		else
			"$B" vigila </dev/null >/dev/null 2>&1 &
		fi
		exec "$B" espera </dev/null >/dev/null 2>&1
		;;
	*)
		exec "$B" "$@"
		;;
esac
