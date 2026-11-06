#!/bin/bash
# Para una app de escritorio ya empaquetada (createDistributable y, si se quiere, packageDeb): hace <App>-<versión>-linux.tar.gz
# (la app y su instalar.sh, con el nombre, el icono y la categoría de esa app) y copia el .deb con su nombre de versión.
# Uso: herramientas/escritorio/empaqueta-linux.sh escritorio/hiedra dist
set -euo pipefail
d="${1%/}"; dist="$2"
raiz="$(cd "$(dirname "$0")/../.." && pwd)"
a="$(basename "$d")"; A="${a^}"
inicio=""  # lo que se añade a la orden de abrir al entrar en la sesión (instalar.sh --al-iniciar)
v="$(grep -m1 '^val version' "$d/build.gradle.kts" | cut -d'"' -f2)"
clase="$(grep -m1 'mainClass' "$d/build.gradle.kts" | cut -d'"' -f2 | tr '.' '-')"
case "$a" in
  hiedra)   gen="Juegos";           desc="Tus juegos: biblioteca y lanzador, también con mando"; cat="Game;"; inicio=" --sala" ;;
  marfil)   gen="Lector de libros"; desc="Tus libros: biblioteca y lector";                       cat="Office;Viewer;" ;;
  cobalto)  gen="Escritura";        desc="Lo que escribes: obras, fichas y notas";                cat="Office;WordProcessor;" ;;
  azabache) gen="Revista";          desc="La revista de la familia: lo que juegas, lees, ves y escribes"; cat="Utility;" ;;
  mostaza)  gen="Películas y series"; desc="Tus películas y series: biblioteca y verlas (con mpv)"; cat="AudioVideo;Video;Player;" ;;
  *) echo "No sé qué app es $a" >&2; exit 1 ;;
esac
app="$d/build/compose/binaries/main/app"
[ -x "$app/$A/bin/$A" ] || { echo "Falta $app/$A/bin/$A (¿createDistributable?)" >&2; exit 1; }
sed -e "s|__APP__|$A|g" -e "s|__ID__|$a|g" -e "s|__GENERICO__|$gen|g" -e "s|__DESCRIPCION__|$desc|g" \
    -e "s|__CATEGORIAS__|$cat|g" -e "s|__WMCLASS__|$clase|g" -e "s|__AL_INICIAR__|$inicio|g" "$raiz/herramientas/escritorio/instalar-linux.sh" > "$app/instalar.sh"
chmod +x "$app/instalar.sh"
mkdir -p "$dist"
tar -C "$app" -czf "$dist/$A-$v-linux.tar.gz" "$A" instalar.sh
if compgen -G "$d/build/compose/binaries/main/deb/*.deb" > /dev/null; then cp "$d"/build/compose/binaries/main/deb/*.deb "$dist/$A-$v-linux.deb"; fi
echo "$dist/$A-$v-linux.tar.gz"
