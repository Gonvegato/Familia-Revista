"""Iconos de la dirección C («editorial moderna») para Android: la inicial en grotesca enorme con el punto del acento y, abajo, una franja
del acento con lo que hace la app. Capas del icono adaptable (fondo + delante + monocromo) a 432 px.
Uso: python3 icono-c.py <app> <carpeta drawable-nodpi> <prefijo>   (p. ej. seshat marfil/src/main/res/drawable-nodpi ic_launcher_marfil)"""
import sys
from PIL import Image, ImageDraw, ImageFont
F = "/home/user/familia-revista/marfil/src/main/res/font"
APPS = {  # nombre: (fondo, texto, acento, lo que hace)
    "seshat": ("#F8F0D4", "#050404", "#C68B00", "LIBROS"),
    "bastet": ("#1C241B", "#F1E1C3", "#6A6D2C", "JUEGOS"),
    "thot":   ("#171E2B", "#F1EBE0", "#C8703F", "ESCRITOS"),
    "jonsu":  ("#1C1F22", "#F1EBE0", "#8F3232", "DIARIO"),
    "hathor": ("#715021", "#FFFFFF", "#D4C1C0", "CINE"),
    "nut":    ("#3D0E19", "#F1DCB5", "#D4A06A", "FÚTBOL"),
}
def rgb(h): return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))
def sobre(h):
    r, g, b = rgb(h); return (26, 20, 16) if 0.299 * r + 0.587 * g + 0.114 * b > 150 else (255, 255, 255)
def grotesca(tam, peso=800):
    f = ImageFont.truetype(f"{F}/bricolage.ttf", tam)
    # Bricolage Grotesque es variable: tamaño óptico, grueso y anchura (como en la app: 800 y un poco estrecha)
    f.set_variation_by_axes([96, peso, 92])
    return f

S = 432            # el lienzo del icono adaptable (108 dp)
BANDA = 0.645      # la franja del acento empieza aquí (de alto)
def capas(app):
    fondo, tinta, acento, que = APPS[app]
    # Fondo: el color de la app y, abajo, la franja del acento (la máscara del lanzador la recorta)
    bg = Image.new("RGB", (S, S), rgb(fondo)); d = ImageDraw.Draw(bg)
    d.rectangle((0, S * BANDA, S, S), fill=rgb(acento))
    def delante(mono=False):
        im = Image.new("RGBA", (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
        T = (255, 255, 255, 255) if mono else rgb(tinta) + (255,)
        A = (255, 255, 255, 255) if mono else rgb(acento) + (255,)
        letra = app[0].upper()
        f = grotesca(int(S * 0.44))
        bx0, by0, bx1, by1 = d.textbbox((0, 0), letra, font=f)
        pw = d.textlength(".", font=f)
        total = (bx1 - bx0) + pw * 0.9
        x = (S - total) / 2 - bx0
        cap_top = S * 0.215                    # dentro de la zona segura (66 dp de 108)
        y = cap_top - by0
        d.text((x, y), letra, font=f, fill=T)
        d.text((x + (bx1 - bx0) + bx0 - pw * 0.05, y), ".", font=f, fill=A)
        if not mono:
            # Lo que hace la app, en la franja (centrado en la parte visible de la franja)
            fl = grotesca(int(S * 0.085))
            tw = d.textlength(que, font=fl) + 0.08 * S * 0.085 * (len(que) - 1)
            ty0, ty1 = fl.getbbox("H")[1], fl.getbbox("H")[3]
            yc = S * (BANDA + 0.08)
            xx = (S - tw) / 2
            for ch in que:
                d.text((xx, yc - (ty0 + ty1) / 2), ch, font=fl, fill=sobre(acento) + (255,)); xx += d.textlength(ch, font=fl) + 0.08 * S * 0.085
        return im
    return bg, delante(), delante(True)

if __name__ == "__main__":
    app, carpeta, prefijo = sys.argv[1:4]
    bg, fg, mo = capas(app)
    bg.save(f"{carpeta}/{prefijo}_bg.png"); fg.save(f"{carpeta}/{prefijo}_fg.png"); mo.save(f"{carpeta}/{prefijo}_mono.png")
    # Una vista del icono (redondeado, como en el lanzador) para revisarlo
    v = bg.convert("RGBA"); v.alpha_composite(fg)
    m = int(S * 18 / 108); v = v.crop((m, m, S - m, S - m))
    mask = Image.new("L", v.size, 0); ImageDraw.Draw(mask).rounded_rectangle((0, 0, v.size[0] - 1, v.size[1] - 1), radius=int(v.size[0] * 0.22), fill=255)
    out = Image.new("RGBA", v.size, (0, 0, 0, 0)); out.paste(v, (0, 0), mask)
    out.save(f"/tmp/claude-0/-home-user/7af7ca95-170e-5969-8fd3-8fda6db34e1c/scratchpad/c-seshat/icono-{app}.png"); print("ok")
