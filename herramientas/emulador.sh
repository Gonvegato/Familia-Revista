#!/bin/bash
# El emulador oficial de Firebase (base de datos y cuentas) con las reglas de nube/reglas.json, para las pruebas del núcleo.
# Uso: herramientas/emulador.sh        (se queda encendido; Ctrl+C para pararlo)
# Necesita Node y Java. La primera vez baja firebase-tools y los emuladores (unos 70 MB).
cd "$(dirname "$0")/../nube"
exec npx --yes firebase-tools@14 emulators:start --only database,auth --project demo-familia
