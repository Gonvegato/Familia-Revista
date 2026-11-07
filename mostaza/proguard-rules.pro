# Media3 y Compose traen sus propias reglas. La página de configurar de los addons no llama a la app desde JavaScript.

# El decodificador de FFmpeg (Jellyfin): Media3 crea su renderizador por el nombre (Class.forName) y su regla solo guarda el
# constructor, no el nombre; sin esto R8 lo renombraba y en la versión firmada el AC3 y el DTS no se oían (las tablets sin Dolby).
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { <init>(...); }
