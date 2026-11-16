"""Los iconos del Mac (y de Linux) de las seis apps, los mismos que en Android (dirección C: la inicial con el punto del acento y la
franja con lo que hace): se sacan de herramientas/familia-c/icono-c.py a más tamaño, con la forma redondeada y el margen de los
iconos del Mac (824 de 1024 y una sombra suave). Deja <app>/icono/<app>.icns y .png y <app>/src/main/resources/icono.png.
Uso: python3 herramientas/escritorio/iconos-mac.py"""
import importlib.util, os
from PIL import Image, ImageDraw, ImageFilter
RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
spec = importlib.util.spec_from_file_location("icono_c", os.path.join(RAIZ, "herramientas/familia-c/icono-c.py"))
c = importlib.util.module_from_spec(spec); spec.loader.exec_module(c)
MODULOS = {"marfil": "seshat", "cobre": "nut", "cobalto": "thot", "hiedra": "bastet", "mostaza": "hathor", "azabache": "jonsu"}

def mac(app):
    c.S = 1536                                   # el lienzo del adaptable, a más tamaño (lo visible sale de 1024)
    bg, fg, _ = c.capas(app)
    v = bg.convert("RGBA"); v.alpha_composite(fg)
    m = int(c.S * 18 / 108); v = v.crop((m, m, c.S - m, c.S - m)).resize((824, 824), Image.LANCZOS)
    mask = Image.new("L", (824, 824), 0); ImageDraw.Draw(mask).rounded_rectangle((0, 0, 823, 823), radius=185, fill=255)
    out = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    sombra = Image.new("L", (1024, 1024), 0); ImageDraw.Draw(sombra).rounded_rectangle((100, 112, 923, 935), radius=185, fill=90)
    out.putalpha(sombra.filter(ImageFilter.GaussianBlur(14)))
    out = Image.composite(Image.new("RGBA", (1024, 1024), (0, 0, 0, 255)), Image.new("RGBA", (1024, 1024), (0, 0, 0, 0)), out.getchannel("A"))
    pieza = Image.new("RGBA", (824, 824), (0, 0, 0, 0)); pieza.paste(v, (0, 0), mask)
    out.alpha_composite(pieza, (100, 100))
    return out

for modulo, app in MODULOS.items():
    im = mac(app)
    d = os.path.join(RAIZ, "escritorio", modulo)
    im.save(os.path.join(d, "icono", f"{modulo}.icns"), sizes=[(16, 16), (32, 32), (64, 64), (128, 128), (256, 256), (512, 512), (1024, 1024)])
    p = im.resize((512, 512), Image.LANCZOS)
    p.save(os.path.join(d, "icono", f"{modulo}.png")); p.save(os.path.join(d, "src", "main", "resources", "icono.png"))
    print("ok", modulo, app)
