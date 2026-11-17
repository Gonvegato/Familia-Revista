#!/bin/sh
# Lanza seshat-kobo (lo llama el menú «Enviar a Seshat» de NickelMenu). Se ejecuta desde una copia en /tmp: así el programa no deja
# ocupada la memoria del Kobo y se puede seguir enchufando por USB. La primera vez deja también en marcha el vigilante, que manda
# solo cada vez que el Kobo tiene Wi-Fi (hasta que se apague el Kobo; al volver a encenderlo, basta con pulsar «Enviar a Seshat»).
PATH=/bin:/usr/bin:/sbin:/usr/sbin
D=/mnt/onboard/.adds/seshat
B=/tmp/seshat-kobo
# (se compara el contenido, no la fecha: la que pone el móvil al copiarlo en la memoria del Kobo no es fiable; cada copia a un nombre
# propio, para que dos pulsaciones seguidas no se pisen; el vigilante que ya corre se pasa solo al programa nuevo)
if [ ! -x "$B" ] || ! cmp -s "$D/seshat-kobo" "$B"; then
	T="$B.$$"
	cp "$D/seshat-kobo" "$T" && chmod 755 "$T" && mv -f "$T" "$B" || { rm -f "$T"; exit 1; }
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
