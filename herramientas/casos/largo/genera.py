# Genera dos libros de prueba con capítulos largos (muchas páginas) para probar que saltar a una posición del Kobo cae en su página
# y no al principio del capítulo: largo.epub (párrafos dentro de un div, como en muchos libros) y largo.kepub.epub (con las frases
# marcadas como lo hace Kobo: span id="kobo.N.1" dentro de div#book-columns/div#book-inner). Uso: python3 genera.py
import zipfile

PALABRAS = "río casa luz viento mesa piedra árbol noche camino puerta agua fuego tierra cielo libro".split()

def parrafo(c, n):
    return f"C{c}-P{n} " + " ".join(PALABRAS[(n * 7 + i) % len(PALABRAS)] for i in range(40)) + "."

def capitulo(c, kepub):
    ps = []
    for n in range(1, 301):
        t = parrafo(c, n)
        ps.append(f'<p><span class="koboSpan" id="kobo.{n}.1">{t}</span></p>' if kepub else f"<p>{t}</p>")
    cuerpo = "\n".join(ps)
    if kepub:
        cuerpo = f'<div id="book-columns"><div id="book-inner">\n<h1><span class="koboSpan" id="kobo.0.1">Capítulo {c}</span></h1>\n{cuerpo}\n</div></div>'
    else:
        cuerpo = f'<div class="chapter">\n<h1>Capítulo {c}</h1>\n{cuerpo}\n</div>'
    return f'<?xml version="1.0" encoding="utf-8"?>\n<html xmlns="http://www.w3.org/1999/xhtml"><head><title>C{c}</title></head><body>\n{cuerpo}\n</body></html>'

def libro(nombre, kepub):
    with zipfile.ZipFile(nombre, "w") as z:
        z.writestr(zipfile.ZipInfo("mimetype"), "application/epub+zip")
        d = zipfile.ZIP_DEFLATED
        z.writestr("META-INF/container.xml", '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>', d)
        z.writestr("OEBPS/content.opf", '''<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="uid">urn:uuid:5e5b0b0e-0000-4000-8000-00000000000%d</dc:identifier><dc:title>Largo</dc:title><dc:creator>Prueba</dc:creator><dc:language>es</dc:language>
<meta property="dcterms:modified">2026-01-01T00:00:00Z</meta></metadata>
<manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
<item id="t" href="text/t.xhtml" media-type="application/xhtml+xml"/><item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="text/c2.xhtml" media-type="application/xhtml+xml"/></manifest>
<spine><itemref idref="t"/><itemref idref="c1"/><itemref idref="c2"/></spine></package>''' % (1 if kepub else 0), d)
        z.writestr("OEBPS/nav.xhtml", '<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Índice</title></head><body><nav epub:type="toc"><ol><li><a href="text/c1.xhtml">Capítulo 1</a></li><li><a href="text/c2.xhtml">Capítulo 2</a></li></ol></nav></body></html>', d)
        z.writestr("OEBPS/text/t.xhtml", '<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Largo</title></head><body><h1>Largo</h1></body></html>', d)
        for c in (1, 2):
            z.writestr(f"OEBPS/text/c{c}.xhtml", capitulo(c, kepub), d)

libro("largo.epub", False)
libro("largo.kepub.epub", True)
