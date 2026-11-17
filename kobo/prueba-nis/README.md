# Prueba de «vincular kepub con EPUB» con NickelInkSync de verdad

`prueba.js` monta un Kobo de mentira (firmware 6.0 del Clara BW, con la base de datos y los libros de prueba de NickelInkSync), un
servidor KOSync de mentira y el lector de SesKobo, y comprueba en los dos sentidos que la posición pasa del kepub del Kobo al EPUB de
SesKobo (y al revés) cayendo en el mismo texto. Usa el programa de NickelInkSync compilado desde su código (v0.1.0, la que va en el Kobo).

    git clone https://github.com/dkuester/NickelInkSync && cd NickelInkSync && git checkout v0.1.0 && cargo build --bin nickelinksync
    (cd ../familia-revista/kobo && go build -o /tmp/seshat-kobo .)
    NIS=<NickelInkSync>/target/debug/nickelinksync FIXTURES=<NickelInkSync>/tests/fixtures KOBO=/tmp/seshat-kobo \
      NODE_PATH=<carpeta de playwright> node prueba.js
