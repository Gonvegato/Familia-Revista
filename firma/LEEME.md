# Firma

Las claves de firma no están en el repositorio (es público). Para compilar las versiones de release hace falta copiarlas
aquí: `firma/<clave>.keystore` y `firma/claves.properties` con sus contraseñas (`<clave>.pass=...`). Sin ellas, la
compilación de release se queda sin firmar (las pruebas y la versión de depuración funcionan igual).
