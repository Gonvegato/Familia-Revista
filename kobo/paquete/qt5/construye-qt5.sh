#!/bin/sh
# Compila libseshat-qt5.so (ARM de 32 bits, firmware 4.x con Qt 5.2.1). Hace falta clang y ld.lld.
# Con GUION=/otra/ruta.sh se puede compilar una copia de prueba que lance otro guion.
set -e
cd "$(dirname "$0")"
T=$(mktemp -d)
cat > "$T/libc-stub.c" <<'C'
int fork(void){return 0;} int setsid(void){return 0;} int execl(const char*a,const char*b,...){return 0;} void _exit(int c){for(;;);} int waitpid(int a,int*b,int c){return 0;}
C
# (una biblioteca de mentira solo para enlazar: en el Kobo se usa la de verdad, que ya tiene cargada la interfaz)
F="--target=armv7a-linux-gnueabihf -mfloat-abi=hard -fPIC -O2 -nostdlib -shared -fuse-ld=lld"
clang $F -Wl,-soname,libc.so.6 -o "$T/libc.so.6" "$T/libc-stub.c"
SALIDA=${SALIDA:-libseshat-qt5.so}
clang $F -Wl,-z,now -Wl,-soname,libseshat.so ${GUION:+-DGUION=\"$GUION\"} -o "$SALIDA" libseshat-qt5.c "$T/libc.so.6"
llvm-strip "$SALIDA"
rm -rf "$T"
echo "Listo: $SALIDA"
