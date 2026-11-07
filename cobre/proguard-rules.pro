# Media3, Compose y el decodificador de FFmpeg (Jellyfin) traen sus propias reglas. Media3 crea el renderizador de FFmpeg por su nombre
# (Class.forName) y su regla solo guarda el constructor, no el nombre de la clase: aquí se guarda entero, para que R8 no lo cambie y
# el AC3 y el DTS se oigan también en la versión firmada.
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { <init>(...); }
