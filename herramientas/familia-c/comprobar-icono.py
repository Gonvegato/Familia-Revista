"""Hoja de comprobación del icono C (círculo, squircle, redondeado, monocromo y pequeños) y cartel de la tele (320×180).
Uso: python3 comprobar-icono.py <app> <hoja.png> <banner.png>   (p. ej. hathor docs/mostaza/iconos/comprobacion-icono.png mostaza/src/main/res/drawable-nodpi/banner_mostaza.png)"""
import sys, importlib.util
from PIL import Image, ImageDraw, ImageFont
sp = importlib.util.spec_from_file_location("ic", "/home/user/familia-revista/herramientas/familia-c/icono-c.py"); ic = importlib.util.module_from_spec(sp); sp.loader.exec_module(ic)
app, salida, banner = sys.argv[1], sys.argv[2], sys.argv[3]
bg, fg, mo = ic.capas(app)
S = ic.S
full = bg.convert("RGBA"); full.alpha_composite(fg)
def recorte(im, forma):
    m = int(S * 18 / 108); v = im.crop((m, m, S - m, S - m)).resize((288, 288), Image.LANCZOS)
    mask = Image.new("L", v.size, 0); d = ImageDraw.Draw(mask)
    if forma == "c": d.ellipse((0, 0, 287, 287), fill=255)
    elif forma == "s": d.rounded_rectangle((0, 0, 287, 287), radius=110, fill=255)
    else: d.rounded_rectangle((0, 0, 287, 287), radius=60, fill=255)
    out = Image.new("RGBA", v.size, (0, 0, 0, 0)); out.paste(v, (0, 0), mask); return out
H = (36, 34, 30); hoja = Image.new("RGB", (1352, 548), H); d = ImageDraw.Draw(hoja)
f = ic.grotesca(18, 700)
for i, (t, forma) in enumerate([("CIRCULO", "c"), ("SQUIRCLE", "s"), ("REDONDEADO", "r")]):
    x = 40 + i * 328; d.text((x, 25), t, font=f, fill=(230, 196, 110))
    im = recorte(full, forma); hoja.paste(im, (x, 60), im)
    # la zona segura (66 dp de 72): el círculo rojo
    r = 288 * 33 / 36 / 2; cx, cy = x + 144, 60 + 144; d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(255, 0, 0))
# monocromo, como lo pinta el sistema (un tono sobre otro)
x = 40 + 3 * 328; d.text((x, 25), "MONOCROMO", font=f, fill=(230, 196, 110))
mono = Image.new("RGBA", (S, S), (60, 74, 64, 255)); col = Image.new("RGBA", (S, S), (200, 230, 200, 255)); col.putalpha(mo.split()[3]); mono.alpha_composite(col)
im = recorte(mono, "c"); hoja.paste(im, (x, 60), im)
d.text((40, 392), "PEQUEÑO (48 y 32 px)", font=f, fill=(230, 196, 110))
for i, forma in enumerate(["c", "s", "r"]):
    for j, t in enumerate([48, 32]):
        im = recorte(full, forma).resize((t, t), Image.LANCZOS); hoja.paste(im, (40 + i * 158 + j * 72, 428), im)
hoja.save(salida)
# El cartel de la tele (320×180): «HATHOR.» y la franja del acento con lo que hace
fondo, tinta, acento, que = ic.APPS[app]
b = Image.new("RGB", (320, 180), ic.rgb(fondo)); d = ImageDraw.Draw(b)
d.rectangle((0, 180 * ic.BANDA, 320, 180), fill=ic.rgb(acento))
nombre = app.upper(); g = ic.grotesca(64)
w = d.textlength(nombre + ".", font=g); x0 = (320 - w) / 2
bb = d.textbbox((0, 0), nombre, font=g); yc = 180 * ic.BANDA / 2 + 4
d.text((x0, yc - (bb[1] + bb[3]) / 2), nombre, font=g, fill=ic.rgb(tinta))
d.text((x0 + d.textlength(nombre, font=g), yc - (bb[1] + bb[3]) / 2), ".", font=g, fill=ic.rgb(acento))
gl = ic.grotesca(24, 800); bb = d.textbbox((0, 0), que, font=gl); w = bb[2] - bb[0]
d.text(((320 - w) / 2 - bb[0], 180 * (1 + ic.BANDA) / 2 - (bb[1] + bb[3]) / 2), que, font=gl, fill=ic.sobre(acento))
b.save(banner); print("ok")
