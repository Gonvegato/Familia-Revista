#!/bin/sh
# Compila libseshat.so (ARM de 32 bits, el del Kobo) y deja Kobo.tgz y Seshat-arranque.zip. Hace falta clang y ld.lld.
set -e
cd "$(dirname "$0")"
cat > /tmp/libc-stub.c <<'C'
int fork(void){return 0;} int setsid(void){return 0;} int execl(const char*a,const char*b,...){return 0;} void _exit(int c){for(;;);} int waitpid(int a,int*b,int c){return 0;}
C
# (una biblioteca de mentira solo para enlazar: en el Kobo se usa la de verdad, que ya tiene cargada la interfaz)
clang --target=armv7a-linux-gnueabihf -mfloat-abi=hard -fPIC -O2 -nostdlib -shared -fuse-ld=lld -Wl,-soname,libc.so.6 -o /tmp/libc.so.6 /tmp/libc-stub.c
clang --target=armv7a-linux-gnueabihf -mfloat-abi=hard -fPIC -O2 -nostdlib -shared -fuse-ld=lld -Wl,-z,now -Wl,-soname,libseshat.so -o libseshat.so libseshat.c /tmp/libc.so.6
llvm-strip libseshat.so
T=$(mktemp -d); mkdir -p "$T/imageformats"; cp libseshat.so "$T/imageformats/"; chmod 755 "$T/imageformats/libseshat.so"
tar czf Kobo.tgz --owner=root --group=root -C "$T" imageformats/libseshat.so
rm -rf "$T" Seshat-arranque.zip; zip -q -X Seshat-arranque.zip Kobo.tgz arranque.sh LEEME.txt
echo "Listo: Kobo.tgz y Seshat-arranque.zip"
