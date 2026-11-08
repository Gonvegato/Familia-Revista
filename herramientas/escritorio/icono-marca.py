"""El icono de cada app como su widget «en papel» en pequeño: el nombre, la raya gruesa, lo que hace la app en versalitas
(con el cuadradito del acento, como en las cabeceras de los widgets) y la raya fina, con un marco fino; con sus colores."""
import os
from PIL import Image, ImageDraw, ImageFont
F = "/home/user/familia-revista/hiedra/src/main/res/font"
OUT = os.path.dirname(os.path.abspath(__file__)) + "/png"; os.makedirs(OUT, exist_ok=True)
APPS = {  # nombre: (fondo, texto, acento, lo que hace)
    "Bastet": ("#1C241B", "#F1E1C3", "#6A6D2C", "JUEGOS"),
    "Seshat": ("#F8F0D4", "#050404", "#C68B00", "LIBROS"),
    "Thot":   ("#171E2B", "#F1EBE0", "#A79530", "ESCRITOS"),
    "Jonsu":  ("#1C1F22", "#F1EBE0", "#8F3232", "DIARIO"),
    "Hathor": ("#715021", "#FFFFFF", "#D4C1C0", "CINE"),
    "Nut":    ("#3D0E19", "#F1DCB5", "#D4A06A", "IPTV"),
    "Ptah":   ("#12393A", "#F2E6CF", "#D98A3D", "WIDGETS"),
    "Shai":   ("#515151", "#F2E8D0", "#C59E21", "CARRERA"),
}
def rgb(h): return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))

def dibuja(nombre, fondo, tinta, acento, que, S=1024, con_fondo=True, mono=False):
    """El icono visible (S×S). mono: todo en negro sobre transparente (para el icono monocromo)."""
    im = Image.new("RGBA", (S, S), rgb(fondo) + (255,) if con_fondo else (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    T = (0, 0, 0, 255) if mono else rgb(tinta) + (255,)
    A = (0, 0, 0, 255) if mono else rgb(acento) + (255,)
    k = S / 1024
    # El marco fino, como el borde del widget
    m = 84 * k; d.rounded_rectangle((m, m, S - m, S - m), radius=150 * k, outline=T, width=round(13 * k))
    pad = 150 * k; ancho = S - 2 * pad
    # El nombre tan grande como quepa a lo ancho (y sin pasarse de alto)
    alto_max = 330 * k
    t = 400
    while True:
        f = ImageFont.truetype(f"{F}/display_semibold.ttf", round(t * k))
        x0, y0, x1, y1 = d.textbbox((0, 0), nombre.upper(), font=f)
        cap = f.getbbox("H")[3] - f.getbbox("H")[1]
        if (x1 - x0) <= ancho and cap <= alto_max: break
        t -= 4
    # Lo de debajo: la raya gruesa, la línea en versalitas y la raya fina
    fl = ImageFont.truetype(f"{F}/body_bold.ttf", round(64 * k)); trk = 0.16 * 64 * k
    capl = fl.getbbox("H")[3] - fl.getbbox("H")[1]
    g1, gruesa, g2, g3, fina = 34 * k, 18 * k, 34 * k, 40 * k, 7 * k
    bloque = cap + g1 + gruesa + g2 + capl + g3 + fina
    base_top = (S - bloque) / 2
    # nombre (centrado por sus mayúsculas)
    hy = f.getbbox("H")[1]
    d.text(((S - (x1 - x0)) / 2 - x0, base_top - hy), nombre.upper(), font=f, fill=T)
    y = base_top + cap + g1
    d.rectangle((pad, y, S - pad, y + gruesa), fill=T)
    y += gruesa + g2
    # ■ LO QUE HACE
    q = capl * 0.92
    d.rectangle((pad, y + (capl - q) / 2, pad + q, y + (capl + q) / 2), fill=A)
    x = pad + q + 26 * k; ly = fl.getbbox("H")[1]
    for ch in que:
        d.text((x, y - ly), ch, font=fl, fill=T); x += d.textlength(ch, font=fl) + trk
    y += capl + g3
    d.rectangle((pad, y, S - pad, y + fina), fill=T)
    return im

for n, (fo, ti, ac, que) in APPS.items():
    a = n.lower()
    dibuja(n, fo, ti, ac, que).convert("RGB").save(f"{OUT}/{a}-full.png")
    # Capa de delante del icono adaptable (108 con los 72 visibles en el centro): lo visible al 100 %
    fg = Image.new("RGBA", (1536, 1536), (0, 0, 0, 0)); fg.alpha_composite(dibuja(n, fo, ti, ac, que, con_fondo=False), (256, 256)); fg.save(f"{OUT}/{a}-fg.png")
    mo = Image.new("RGBA", (1536, 1536), (0, 0, 0, 0)); mo.alpha_composite(dibuja(n, fo, ti, ac, que, con_fondo=False, mono=True), (256, 256))
    w = Image.new("RGBA", mo.size, (255, 255, 255, 0)); w.putalpha(mo.getchannel("A")); w.save(f"{OUT}/{a}-mono.png")
ims = [Image.open(f"{OUT}/{n.lower()}-full.png").resize((220, 220), Image.LANCZOS) for n in APPS]
v = Image.new("RGB", (232 * len(APPS), 220), "white")
for i, im in enumerate(ims):
    mask = Image.new("L", (220, 220), 0); ImageDraw.Draw(mask).rounded_rectangle((0, 0, 219, 219), radius=50, fill=255)
    v.paste(im, (i * 232, 0), mask)
v.save(os.path.dirname(OUT) + "/vista.png"); print("ok")
