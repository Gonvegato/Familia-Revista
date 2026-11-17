/*
 * libseshat.so: lo mínimo para que el Kobo (firmware 5 y 6) ejecute algo al arrancar sin NickelMenu.
 *
 * El Kobo carga los complementos de imágenes de /usr/local/Kobo/imageformats (así entra también NickelMenu). Al cargarse, esto lanza
 * en segundo plano /mnt/onboard/.adds/seshat/arranque.sh (en la memoria normal del Kobo, donde se cambia o se borra sin instalar nada)
 * y no hace nada más: no engancha nada de la interfaz del Kobo. Sin ese archivo, no hace nada.
 *
 * Sin bibliotecas de Qt: el Kobo solo necesita encontrar la nota «qt.metadata» (la de abajo) para tomarlo por un complemento de
 * imágenes; al no tener qt_plugin_instance, lo descarta después de cargarlo (el arranque ya está lanzado).
 */
#include <stddef.h>

typedef int pid_t;
extern pid_t fork(void);
extern pid_t setsid(void);
extern int execl(const char *path, const char *arg, ...);
extern void _exit(int);
extern pid_t waitpid(pid_t, int *, int);

#ifndef GUION
#define GUION "/mnt/onboard/.adds/seshat/arranque.sh"
#endif

__attribute__((constructor)) static void arranca(void) {
    pid_t p = fork();
    if (p == 0) {
        // (el nieto, fuera de la sesión del Kobo: el hijo sale enseguida y el Kobo no se queda esperando)
        setsid();
        if (fork() == 0) {
            execl("/bin/sh", "sh", "-c", "[ -f " GUION " ] && exec /bin/sh " GUION, (char *)0);
        }
        _exit(0);
    }
    if (p > 0) waitpid(p, NULL, 0);
}

/* La nota de Qt 6 (copiada de NickelMenu, con otra clave: «nh.sk»). */
/* (4 bytes de alineación en 32 bits, como la lleva NickelMenu en el Kobo; 8 en 64 bits, solo para probarlo en un ordenador) */
#if defined(__LP64__)
#define ALINEA ".balign 8\n"
#else
#define ALINEA ".balign 4\n"
#endif
__asm__(
    ".section .note.qt.metadata,\"a\",%note\n"
    ALINEA
    ".incbin \"nota.bin\"\n"
    ALINEA
    ".previous\n");
