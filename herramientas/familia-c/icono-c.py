"""Iconos de la dirección C («editorial moderna») para Android: la inicial en grotesca enorme con el punto del acento y, abajo, una franja
del acento con lo que hace la app. Capas del icono adaptable (fondo + delante + monocromo) a 432 px.
Uso: python3 icono-c.py <app> <carpeta drawable-nodpi> <prefijo>   (p. ej. seshat marfil/src/main/res/drawable-nodpi ic_launcher_marfil)"""
import sys
from PIL import Image, ImageDraw, ImageFont
F = "/home/user/familia-revista/marfil/src/main/res/font"
APPS = {  # nombre: (fondo, texto, acento, lo que hace)
    "seshat": ("#F8F0D4", "#050404", "#C68B00", "LIBROS"),
    "bastet": ("#E7E1D3", "#2A2824", "#6E7458", "JUEGOS"),
    "thot":   ("#1C2129", "#E4E6E9", "#8696AE", "ESCRITOS"),
    "jonsu":  ("#2B2529", "#EAE2DD", "#9C7B86", "DIARIO"),
    "hathor": ("#E9E5DE", "#1F1E1C", "#8C6F4E", "CINE"),
    "nut":    ("#2C1C1F", "#EADFD2", "#A9876A", "FÚTBOL"),
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
        # La «S.» (letra + punto) como un conjunto, centrado con la tinta real de las glifas (no la caja de la línea):
        #  - en horizontal, en el centro del icono (la zona segura del adaptable es un círculo de 66 dp centrado);
        #  - en vertical, en el hueco que queda entre la parte de arriba de la zona segura y la franja (en el monocromo, que no
        #    tiene franja, en el centro de la zona segura).
        f = grotesca(int(S * 0.40))
        lx0, ly0, lx1, ly1 = f.getbbox(letra, anchor="ls")     # tinta de la letra, desde la línea base
        px0, py0, px1, py1 = f.getbbox(".", anchor="ls")       # tinta del punto
        hueco = S * 0.018
        ancho = (lx1 - lx0) + hueco + (px1 - px0)
        alto = max(ly1, py1) - min(ly0, py0)
        seguro_arriba = S * (54 - 33) / 108                     # el borde de arriba de la zona segura
        abajo = S * (54 + 33) / 108 if mono else S * BANDA
        x0 = (S - ancho) / 2                                    # donde empieza la tinta de la letra
        top = seguro_arriba + ((abajo - seguro_arriba) - alto) / 2
        base = top - min(ly0, py0)                              # la línea base
        d.text((x0 - lx0, base), letra, font=f, fill=T, anchor="ls")
        d.text((x0 + (lx1 - lx0) + hueco - px0, base), ".", font=f, fill=A, anchor="ls")
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

NOMBRES = {"seshat": "SESHAT", "bastet": "BASTET", "thot": "THOT", "jonsu": "JONSU", "hathor": "HATHOR", "nut": "NUT"}
def banner(app, w=640, h=360):
    """El cartel de la tele (Android TV): el nombre con su punto sobre el fondo y la franja del acento con lo que hace, como el icono."""
    fondo, tinta, acento, que = APPS[app]
    im = Image.new("RGB", (w, h), rgb(fondo)); d = ImageDraw.Draw(im)
    d.rectangle((0, h * 0.70, w, h), fill=rgb(acento))
    nombre = NOMBRES[app]
    f = grotesca(int(h * 0.36))
    nb = f.getbbox(nombre, anchor="ls"); pb = f.getbbox(".", anchor="ls")
    ancho = (nb[2] - nb[0]) + (pb[2] - pb[0]) + h * 0.01
    x0 = (w - ancho) / 2; base = h * 0.70 / 2 + (nb[3] - nb[1]) / 2
    d.text((x0 - nb[0], base), nombre, font=f, fill=rgb(tinta), anchor="ls")
    d.text((x0 + (nb[2] - nb[0]) + h * 0.01 - pb[0], base), ".", font=f, fill=rgb(acento), anchor="ls")
    fl = grotesca(int(h * 0.10))
    tw = d.textlength(que, font=fl)
    d.text(((w - tw) / 2, h * 0.85), que, font=fl, fill=sobre(acento), anchor="lm")
    return im

if __name__ == "__main__" and sys.argv[1] == "banner":
    # python3 icono-c.py banner <app> <archivo.png>
    banner(sys.argv[2], 320, 180).save(sys.argv[3]); print("ok"); sys.exit()

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
