"""
Star Hero: modelos 3D de los instrumentos (formato de modelos de Java / Blockbench) y su textura.

  python3 tools/make_models.py            -> genera modelos y textura
  python3 tools/make_models.py --preview  -> ademas dibuja vistas previas en tools/preview/

Todos los instrumentos comparten una textura de 64x64 hecha de "materiales" (celdas de 4x4 px):
madera con veta, laca brillante, cromo, parches de bateria, rejilla del amplificador, etc.
Ejes del modelo: X = derecha, Y = arriba, -Z = el frente (la cara que mira al publico).
"""
import json
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources", "assets", "starhero")
TEX = "starhero:item/instruments"

# ---------------------------------------------------------------- materiales (celdas de la textura)
MATERIALS = {}
_cells = []


def material(name, fn):
    idx = len(_cells)
    _cells.append((name, fn))
    MATERIALS[name] = idx


def solid(c, noise=6, grad=0):
    def f(x, y, r):
        k = 1 + grad * (0.5 - y / 3.0) * 0.5
        n = r.randint(-noise, noise)
        return tuple(max(0, min(255, int(v * k) + n)) for v in c)
    return f


def stripes(c1, c2):
    def f(x, y, r):
        c = c1 if (x + (y % 2)) % 3 else c2
        n = r.randint(-5, 5)
        return tuple(max(0, min(255, v + n)) for v in c)
    return f


def checker(c1, c2):
    def f(x, y, r):
        return c1 if (x + y) % 2 else c2
    return f


def sparkle(c, spark):
    def f(x, y, r):
        if r.random() < 0.18:
            return spark
        n = r.randint(-12, 12)
        return tuple(max(0, min(255, v + n)) for v in c)
    return f


def rings(c1, c2):
    def f(x, y, r):
        return c1 if (x + y) % 3 else c2
    return f


material("red_gloss", solid((196, 28, 36), 8, 1))
material("red_dark", solid((120, 14, 22), 6))
material("white_pearl", solid((236, 232, 222), 5, 0.4))
material("black_gloss", solid((26, 24, 30), 4, 1))
material("black_matte", solid((40, 38, 42), 8))
material("chrome", solid((200, 204, 212), 10, 2))
material("gold", solid((222, 176, 60), 10, 1.2))
material("maple", stripes((222, 182, 120), (200, 158, 98)))
material("rosewood", stripes((72, 40, 26), (58, 30, 20)))
material("blue_gloss", solid((32, 70, 170), 8, 1))
material("blue_dark", solid((18, 34, 92), 6))
material("cream", solid((240, 226, 190), 5))
material("string", solid((228, 228, 236), 2))
material("grille", checker((86, 80, 72), (58, 54, 50)))
material("tolex", sparkle((28, 26, 28), (48, 46, 48)))
material("led_green", solid((90, 255, 140), 0))
material("led_red", solid((255, 70, 70), 0))
material("drum_head", solid((252, 250, 244), 3))
material("sparkle_red", sparkle((170, 20, 40), (255, 140, 160)))
material("brass", rings((214, 170, 70), (180, 136, 46)))
material("wood_dark", stripes((96, 62, 36), (80, 50, 28)))
material("gray", solid((110, 112, 118), 8))
material("yellow", solid((255, 214, 40), 4))
material("purple", solid((150, 60, 210), 6, 1))
material("sunburst", solid((210, 120, 30), 10, 2.5))
material("black_key", solid((18, 18, 20), 2))
material("white_key", solid((248, 248, 246), 2))
material("screen", solid((40, 200, 220), 6))
material("rubber", solid((22, 22, 22), 4))
material("orange", solid((240, 120, 30), 6))


def make_texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    r = random.Random(7)
    for i, (name, fn) in enumerate(_cells):
        cx, cy = (i % 16) * 4, (i // 16) * 4
        for y in range(4):
            for x in range(4):
                img.putpixel((cx + x, cy + y), fn(x, y, r) + (255,))
    os.makedirs(os.path.join(RES, "textures", "item"), exist_ok=True)
    img.save(os.path.join(RES, "textures", "item", "instruments.png"))
    return img


def cell_uv(name):
    i = MATERIALS[name]
    u, v = (i % 16), (i // 16)
    # cada celda de 4 px = 1 unidad de UV (textura de 64 px); se deja medio pixel de margen
    return [u + 0.125, v + 0.125, u + 0.875, v + 0.875]


# ---------------------------------------------------------------- modelos

class Model:
    def __init__(self):
        self.elements = []
        self.shift_x = 0

    def box(self, frm, to, mat, faces=None, rot=None, shade=True):
        """Cubo de frm a to; mat = material o dict cara->material."""
        f = {}
        for face in ("north", "south", "east", "west", "up", "down"):
            m = mat.get(face, mat.get("all")) if isinstance(mat, dict) else mat
            if m is None:
                continue
            if faces and face not in faces:
                continue
            f[face] = {"uv": cell_uv(m), "texture": "#all"}
        el = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": f}
        if not shade:
            el["shade"] = False
        if rot:
            el["rotation"] = rot
        self.elements.append(el)
        return el

    def fit_x(self, lo=-15.5):
        """Corre el modelo en X para que entre en el rango permitido (-16..32)."""
        mn = min(min(e["from"][0], e["to"][0]) for e in self.elements)
        if mn < lo:
            d = lo - mn
            for e in self.elements:
                e["from"][0] = round(e["from"][0] + d, 3)
                e["to"][0] = round(e["to"][0] + d, 3)
                if "rotation" in e:
                    e["rotation"]["origin"][0] += d
            self.shift_x = d
        return self

    def json(self, display, parent=None):
        data = {"textures": {"all": TEX, "particle": TEX}, "elements": self.elements, "display": display}
        if parent:
            data["parent"] = parent
        return data


def clamp_check(m, name):
    for e in m.elements:
        for v in e["from"] + e["to"]:
            if v < -16 or v > 32:
                raise SystemExit(f"{name}: elemento fuera de rango {e['from']} {e['to']}")


def save(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=1)



# ---------------------------------------------------------------- formas redondas (octogonos con cubos girados)
T8 = 0.4142  # tan(22.5 grados)


def cyl_y(m, cx, cz, r, y0, y1, mat, top=None):
    """Cilindro vertical (eje Y) de 8 lados."""
    w = r * T8
    faces = {"all": mat, "up": top or mat}
    m.box([cx - w, y0, cz - r], [cx + w, y1, cz + r], faces)
    m.box([cx - r, y0, cz - w], [cx + r, y1, cz + w], faces)
    for a in (45, -45):
        m.box([cx - w, y0, cz - r], [cx + w, y1, cz + r], faces, rot={"angle": a, "axis": "y", "origin": [cx, (y0 + y1) / 2, cz]})


def cyl_z(m, cx, cy, r, z0, z1, mat, front=None):
    """Cilindro acostado (eje Z) de 8 lados."""
    w = r * T8
    faces = {"all": mat, "north": front or mat}
    m.box([cx - w, cy - r, z0], [cx + w, cy + r, z1], faces)
    m.box([cx - r, cy - w, z0], [cx + r, cy + w, z1], faces)
    for a in (45, -45):
        m.box([cx - w, cy - r, z0], [cx + w, cy + r, z1], faces, rot={"angle": a, "axis": "z", "origin": [cx, cy, (z0 + z1) / 2]})

# ---------------------------------------------------------------- guitarra electrica
def guitar(body="red_gloss", guard="white_pearl", strings=6, bass=False):
    m = Model()
    z0, z1 = 7.0, 9.6       # frente / espalda del cuerpo
    # silueta del cuerpo, columna por columna (x, [abajo, arriba]); los cuernos van aparte
    outline = [
        (5.5, 2.8, 13.6), (6.5, 3.2, 13.3), (7.5, 3.6, 12.9), (8.5, 3.9, 12.5), (9.5, 3.9, 12.2),
        (10.5, 3.7, 12.2), (11.5, 3.3, 12.5), (12.5, 2.8, 13.0), (13.5, 2.3, 13.5), (14.5, 2.0, 13.9),
        (15.5, 1.9, 14.1), (16.5, 2.0, 14.1), (17.5, 2.3, 13.8), (18.5, 2.9, 13.2), (19.5, 3.9, 12.2),
    ]
    for x, lo, hi in outline:
        m.box([x, lo, z0], [x + 1.0, hi, z1], body)
        # canto redondeado: una franja mas fina adelante y atras
        m.box([x, lo + 0.35, z0 - 0.12], [x + 1.0, hi - 0.35, z0], body)
    m.box([20.5, 5.2, z0], [21.0, 10.8, z1], body)
    # cuernos (el de arriba mas largo)
    for x, lo, hi in [(2.5, 11.6, 13.0), (3.5, 11.1, 13.5), (4.5, 10.7, 13.7)]:
        m.box([x, lo, z0], [x + 1.0, hi, z1], body)
    for x, lo, hi in [(3.7, 3.0, 4.2), (4.5, 2.7, 4.8)]:
        m.box([x, lo, z0], [x + 1.0, hi, z1], body)
    # golpeador (pickguard)
    for x, lo, hi in [(6.6, 5.0, 11.2), (8.0, 4.7, 11.4), (10.0, 4.5, 11.5), (13.0, 4.3, 11.2), (16.0, 4.8, 10.6)]:
        m.box([x, lo, z0 - 0.3], [x + (2.0 if x < 10 else 3.0) if x < 16 else x + 1.6, hi, z0 - 0.12], guard)
    # microfonos
    pk = [9.6, 12.4, 15.0] if not bass else [11.0, 14.4]
    for x in pk:
        w = 0.9 if not bass else 1.5
        m.box([x, 5.6, z0 - 0.6], [x + w, 10.4, z0 - 0.3], "black_gloss")
        for k in range(strings):
            y = 5.95 + k * (4.1 / max(1, strings - 1)) - 0.15
            m.box([x + 0.3, y, z0 - 0.68], [x + w - 0.3, y + 0.3, z0 - 0.6], "chrome")
    # puente, selletas y cordal
    m.box([17.0, 5.4, z0 - 0.7], [18.3, 10.6, z0 - 0.3], "chrome")
    m.box([18.3, 6.0, z0 - 0.5], [18.9, 10.0, z0 - 0.3], "gray")
    if not bass:
        # palanca de tremolo
        m.box([17.4, 4.2, z0 - 1.0], [17.7, 5.6, z0 - 0.7], "chrome")
        m.box([13.4, 3.9, z0 - 1.0], [17.7, 4.2, z0 - 0.8], "chrome")
        m.box([13.0, 3.7, z0 - 1.05], [13.6, 4.4, z0 - 0.75], "white_pearl")
    # perillas y selector
    for x, y in [(15.9, 3.0), (17.7, 3.4), (19.1, 4.8)]:
        m.box([x, y, z0 - 0.85], [x + 0.9, y + 0.9, z0 - 0.3], "cream" if not bass else "chrome")
        m.box([x + 0.3, y + 0.3, z0 - 0.95], [x + 0.6, y + 0.6, z0 - 0.85], "black_gloss")
    m.box([15.0, 11.6, z0 - 0.75], [16.2, 12.0, z0 - 0.3], "black_gloss")
    m.box([15.4, 11.4, z0 - 1.1], [15.7, 12.2, z0 - 0.75], "white_pearl")
    # ficha del cable
    m.box([19.2, 2.2, z0 + 0.6], [19.9, 2.9, z0 + 1.4], "chrome")
    # mastil (con el lomo redondeado) y diapason
    nl = -10.0 if not bass else -12.5
    m.box([nl, 7.0, 7.6], [5.8, 9.0, 8.7], "maple")
    m.box([nl, 7.3, 8.7], [5.8, 8.7, 9.1], "maple")
    m.box([nl, 6.85, 7.15], [6.2, 9.15, 7.6], "rosewood")
    m.box([5.8, 6.6, 7.6], [7.0, 9.4, 9.4], "maple")   # talon
    # trastes
    x = 5.2
    gap = 1.9
    while x > nl + 0.6:
        m.box([x, 6.85, 7.03], [x + 0.2, 9.15, 7.15], "chrome")
        x -= gap
        gap *= 0.94
    for xm in (2.4, -0.9, -4.2, -7.2):
        if xm > nl + 1:
            m.box([xm, 7.8, 7.08], [xm + 0.5, 8.2, 7.15], "white_pearl")
    m.box([nl - 0.35, 6.8, 7.0], [nl, 9.2, 7.6], "white_pearl")   # cejuela
    # clavijero con forma (mas ancho en la punta)
    hl = nl - 5.6
    head = "maple" if not bass else body
    cols = [(nl - 1.4, 6.6, 9.5), (nl - 2.8, 6.3, 10.0), (nl - 4.2, 6.2, 10.4), (hl, 6.5, 10.6)]
    for x, lo, hi in cols:
        m.box([x, lo, 7.7], [x + 1.4, hi, 8.7], head)
    m.box([hl - 0.5, 7.4, 7.7], [hl, 10.2, 8.7], head)
    m.box([hl + 0.6, 8.6, 7.6], [hl + 3.2, 9.4, 7.7], "black_gloss", shade=False)  # logo
    # clavijas: 6 en linea (guitarra) o 2+2 (bajo)
    for i in range(strings):
        tx = hl + 0.3 + i * ((nl - hl - 1.2) / max(1, strings - 1))
        up = (not bass) or i < 2
        y0 = 10.2 if up else 5.0
        m.box([tx, y0 if up else y0, 7.9], [tx + 0.55, (y0 + 1.5) if up else 6.4, 8.5], "chrome")
        m.box([tx - 0.25, (y0 + 1.1) if up else (y0 - 0.1), 7.8], [tx + 0.8, (y0 + 1.7) if up else (y0 + 0.5), 8.6], "chrome")
        m.box([tx + 0.05, 7.4 + (i % 3) * 0.6, 7.45], [tx + 0.5, 7.85 + (i % 3) * 0.6, 7.7], "chrome")
    # cuerdas
    for i in range(strings):
        y = 7.15 + i * (1.7 / max(1, strings - 1))
        th = 0.08 + (0.05 if bass else 0.0) + 0.02 * (strings - i) / strings
        m.box([nl - 0.3, y, 6.3], [17.6, y + th, 6.3 + th], "string", shade=False)
    # botones de la correa
    m.box([21.0, 7.6, 8.0], [21.5, 8.4, 8.6], "chrome")
    m.box([2.2, 12.0, 8.0], [2.5, 12.6, 8.6], "chrome")
    return m.fit_x()


# ---------------------------------------------------------------- microfono
def microphone():
    m = Model()
    cyl_y(m, 8, 8, 0.95, 0, 8.4, "black_matte")                 # mango
    cyl_y(m, 8, 8, 1.15, 6.4, 7.2, "chrome")                    # anillo
    cyl_y(m, 8, 8, 0.75, -1.4, 0, "gray")                       # ficha del cable
    m.box([7.5, 2.0, 6.95], [8.5, 3.2, 7.1], "gray")             # interruptor
    m.box([7.7, 2.6, 6.9], [8.3, 3.0, 6.95], "led_green", shade=False)
    # cabeza de rejilla: esfera hecha con tres aros octogonales
    cyl_y(m, 8, 8, 1.35, 8.4, 9.2, "gray")
    cyl_y(m, 8, 8, 1.9, 9.2, 12.6, "chrome")
    cyl_y(m, 8, 8, 1.45, 12.6, 13.4, "chrome")
    cyl_y(m, 8, 8, 2.02, 10.6, 11.3, "black_gloss")              # aro del medio
    return m


# ---------------------------------------------------------------- palillo de bateria
def drumstick():
    m = Model()
    m.box([7.6, 0, 7.6], [8.4, 9.5, 8.4], "maple")
    m.box([7.5, 9.5, 7.5], [8.5, 10.6, 8.5], "cream")   # punta
    m.box([7.55, 0, 7.55], [8.45, 2.4, 8.45], "red_dark")  # empunadura
    return m


# ---------------------------------------------------------------- bateria
def drum(m, x0, x1, y0, y1, z0, z1, shell="sparkle_red", top=True):
    """Tambor de 8 lados con parche, aros cromados y torres de afinacion."""
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    r = min(x1 - x0, z1 - z0) / 2
    cyl_y(m, cx, cz, r, y0, y1, shell)
    if top:
        cyl_y(m, cx, cz, r * 0.93, y1, y1 + 0.18, "drum_head")
    cyl_y(m, cx, cz, r * 1.05, y1 - 0.35, y1 + 0.05, "chrome")
    cyl_y(m, cx, cz, r * 1.05, y0, y0 + 0.35, "chrome")
    for a in range(4):
        dx = r * 1.0 * math.cos(math.pi / 4 + a * math.pi / 2)
        dz = r * 1.0 * math.sin(math.pi / 4 + a * math.pi / 2)
        m.box([cx + dx - 0.25, y0 + 0.6, cz + dz - 0.25], [cx + dx + 0.25, y1 - 0.6, cz + dz + 0.25], "chrome")


def pole(m, x, z, y0, y1, w=0.5):
    m.box([x - w / 2, y0, z - w / 2], [x + w / 2, y1, z + w / 2], "chrome")
    for a in (45, -45):   # tripode
        m.box([x - 0.15, y0, z - 1.6], [x + 0.15, y0 + 0.3, z + 1.6], "chrome", rot={"angle": a, "axis": "y", "origin": [x, y0, z]})


def cymbal(m, cx, cz, y, r, mat="brass", tilt=0):
    rot = lambda: ({"angle": tilt, "axis": "x", "origin": [cx, y, cz]} if tilt else None)
    w = r * T8
    m.box([cx - w, y, cz - r], [cx + w, y + 0.22, cz + r], mat, rot=rot())
    m.box([cx - r, y, cz - w], [cx + r, y + 0.22, cz + w], mat, rot=rot())
    if not tilt:
        for a in (45, -45):
            m.box([cx - w, y, cz - r], [cx + w, y + 0.22, cz + r], mat, rot={"angle": a, "axis": "y", "origin": [cx, y, cz]})
    else:
        m.box([cx - r * 0.72, y, cz - r * 0.72], [cx + r * 0.72, y + 0.22, cz + r * 0.72], mat, rot=rot())
    m.box([cx - 0.7, y + 0.2, cz - 0.7], [cx + 0.7, y + 0.6, cz + 0.7], mat, rot=rot())   # campana
    m.box([cx - 0.2, y + 0.6, cz - 0.2], [cx + 0.2, y + 1.0, cz + 0.2], "black_matte")    # mariposa


def drum_kit():
    m = Model()
    # bombo acostado: el parche con el logo mira al publico (-Z)
    cyl_z(m, 8, 5, 4.9, 2, 9.5, "sparkle_red")
    cyl_z(m, 8, 5, 4.5, 1.78, 2.0, "drum_head", front="drum_head")
    for z in (1.86, 9.3):   # aros de madera (el de adelante queda detras del parche, se ve el borde)
        cyl_z(m, 8, 5, 5.15, z, z + 0.5, "wood_dark")
    # logo: disco violeta con una estrella amarilla de 8 puntas
    cyl_z(m, 8, 5, 2.6, 1.66, 1.78, "purple", front="purple")
    m.box([7.6, 3.0, 1.58], [8.4, 7.0, 1.66], "yellow", shade=False)
    m.box([6.0, 4.6, 1.58], [10.0, 5.4, 1.66], "yellow", shade=False)
    for a in (45, -45):
        m.box([7.65, 3.4, 1.58], [8.35, 6.6, 1.66], "yellow", rot={"angle": a, "axis": "z", "origin": [8, 5, 1.62]}, shade=False)
    for x in (3.4, 12.6):   # patas del bombo
        m.box([x - 0.2, 0, 2.6], [x + 0.2, 2.2, 3.0], "chrome")
    # pedal del bombo
    m.box([7.0, 0, 9.5], [9.0, 0.35, 13.2], "black_matte")
    m.box([7.3, 0.35, 10.2], [8.7, 0.7, 13.0], "chrome", rot={"angle": 22.5, "axis": "x", "origin": [8, 0.35, 13.0]})
    m.box([7.8, 0.4, 9.5], [8.2, 4.2, 9.9], "chrome")
    cyl_z(m, 8, 4.4, 0.7, 9.1, 9.6, "cream")
    # toms de arriba, inclinados hacia el baterista
    drum(m, 3.0, 7.6, 10.6, 14.0, 3.4, 8.0)
    drum(m, 8.4, 13.0, 10.6, 14.0, 3.4, 8.0)
    m.box([7.7, 9.8, 5.2], [8.3, 12.2, 5.8], "chrome")
    m.box([6.6, 11.6, 5.3], [9.4, 12.0, 5.7], "chrome")
    # tambor (caja) a la izquierda del baterista
    pole(m, 1.6, 12.1, 0, 8.6)
    drum(m, -1.4, 4.6, 8.6, 10.8, 9.1, 15.1, shell="chrome")
    # hi-hat con pedal
    pole(m, -4.4, 11.0, 0, 13.6, 0.4)
    cymbal(m, -4.4, 11.0, 12.2, 3.1)
    cymbal(m, -4.4, 11.0, 12.75, 3.1)
    m.box([-5.0, 0, 12.0], [-3.8, 0.4, 14.6], "black_matte")
    # tom de piso a la derecha
    drum(m, 12.2, 18.2, 3.2, 9.4, 9.2, 15.2)
    for x, z in ((12.6, 9.8), (17.8, 9.8), (12.6, 14.6), (17.8, 14.6)):
        m.box([x - 0.18, 0, z - 0.18], [x + 0.18, 4.0, z + 0.18], "chrome")
    # platillos: crash a la izquierda, ride a la derecha, splash chico al centro
    pole(m, -3.0, 3.0, 0, 17.6, 0.45)
    cymbal(m, -3.0, 3.0, 17.4, 4.6, tilt=-22.5)
    pole(m, 19.4, 5.0, 0, 15.4, 0.45)
    cymbal(m, 19.4, 5.0, 15.2, 4.9, tilt=-22.5)
    m.box([7.8, 14.0, 4.2], [8.2, 16.6, 4.6], "chrome")
    cymbal(m, 8.0, 4.4, 16.6, 2.2)
    # banqueta
    cyl_y(m, 8, 17.5, 2.4, 6.0, 7.4, "black_matte")
    cyl_y(m, 8, 17.5, 2.5, 5.8, 6.1, "chrome")
    m.box([7.7, 0.3, 17.2], [8.3, 5.8, 17.8], "chrome")
    for a in (45, -45):
        m.box([7.8, 0, 15.2], [8.2, 0.35, 19.8], "chrome", rot={"angle": a, "axis": "y", "origin": [8, 0.2, 17.5]})
    return m


# ---------------------------------------------------------------- teclado
def keyboard():
    m = Model()
    # soporte en X (visto de frente)
    for z in (5.0, 8.0):
        for a in (45, -45):
            m.box([7.6, -2.6, z], [8.4, 16.6, z + 0.7], "black_matte", rot={"angle": a, "axis": "z", "origin": [8, 7, z]})
    m.box([7.4, 6.6, 5.0], [8.6, 7.4, 8.7], "chrome")              # tornillo del medio
    for x in (0.6, 13.6):
        m.box([x, 0, 4.4], [x + 1.8, 0.5, 9.3], "rubber")           # pies
    m.box([-1.0, 13.4, 4.0], [17.0, 14.0, 9.8], "black_matte")     # bandeja
    # cuerpo del teclado
    m.box([-5.0, 14.0, 2.6], [21.0, 15.8, 10.0], "black_gloss")
    m.box([-5.0, 15.8, 7.0], [21.0, 17.6, 10.0], "black_gloss")   # panel de atras
    # teclas blancas (una tira) y negras
    m.box([-3.8, 15.8, 2.8], [19.8, 16.2, 7.0], "white_key")
    x = -3.8
    i = 0
    pattern = [1, 1, 0, 1, 1, 1, 0]
    while x + 1.6 < 19.8:
        m.box([x, 15.8, 2.75], [x + 0.1, 16.21, 7.0], "gray")   # separacion entre teclas
        if pattern[i % 7]:
            m.box([x + 1.15, 16.2, 4.6], [x + 1.95, 16.75, 7.0], "black_key")
        x += 1.6
        i += 1
    # pantalla y botones
    m.box([4.5, 16.6, 6.85], [11.5, 17.3, 7.0], "screen", shade=False)
    for k, col in enumerate(["led_red", "led_green", "yellow", "purple", "orange"]):
        m.box([-3.8 + k * 1.4, 16.8, 6.85], [-3.0 + k * 1.4, 17.3, 7.0], col, shade=False)
    for k in range(4):
        m.box([13.0 + k * 1.6, 16.6, 6.9], [13.9 + k * 1.6, 17.4, 7.0], "chrome")
    # ruedas de pitch y modulacion
    m.box([-4.75, 15.8, 3.2], [-4.3, 16.7, 4.9], "chrome")
    m.box([-4.75, 15.8, 5.2], [-4.3, 16.6, 6.8], "gray")
    # laterales de madera, perillas, deslizadores y parlantes
    for x in (-5.4, 21.0):
        m.box([x, 13.9, 2.5], [x + 0.4, 17.8, 10.1], "wood_dark")
    for k in range(5):
        cyl_y(m, 13.4 + k * 1.5, 8.6, 0.42, 17.6, 18.25, "cream")
    for k in range(4):
        m.box([-3.4 + k * 1.2, 17.6, 7.6], [-3.1 + k * 1.2, 17.7, 9.6], "black_key")
        m.box([-3.55 + k * 1.2, 17.6, 8.0 + (k % 3) * 0.5], [-2.95 + k * 1.2, 17.95, 8.5 + (k % 3) * 0.5], "chrome")
    for x in (2.2, 11.6):
        m.box([x, 17.6, 7.6], [x + 1.9, 17.68, 9.6], "grille")
    m.box([4.5, 17.6, 7.4], [11.5, 17.7, 9.7], "black_matte")
    m.box([4.9, 17.7, 7.7], [11.1, 17.78, 9.4], "screen", shade=False)
    m.box([8.5, 14.6, 10.0], [12.0, 15.3, 10.08], "gold", shade=False)   # logo atras
    # atril con una partitura
    m.box([7.7, 17.6, 9.6], [8.3, 20.2, 9.9], "black_matte")
    m.box([4.6, 19.4, 9.3], [11.4, 23.4, 9.5], "cream", rot={"angle": 22.5, "axis": "x", "origin": [8, 19.4, 9.4]})
    for k in range(4):
        m.box([5.2, 20.2 + k * 0.7, 9.22], [10.8, 20.32 + k * 0.7, 9.3], "black_key",
              rot={"angle": 22.5, "axis": "x", "origin": [8, 19.4, 9.4]}, shade=False)
    return m


# ---------------------------------------------------------------- amplificador (bloque)
def amplifier():
    m = Model()
    m.box([1, 0, 3], [15, 14, 13], {"all": "tolex", "north": "tolex"})
    m.box([2, 1.2, 2.85], [14, 10.2, 3], "grille")                  # rejilla
    for y in (1.0, 10.0):
        m.box([1.8, y, 2.75], [14.2, y + 0.3, 2.9], "chrome")       # ribete
    for x in (1.8, 13.9):
        m.box([x, 1.0, 2.75], [x + 0.3, 10.3, 2.9], "chrome")
    m.box([1.6, 10.6, 2.8], [14.4, 13.4, 3], "black_gloss")        # panel de controles
    m.box([6.2, 11.3, 2.7], [9.8, 12.7, 2.8], "gold", shade=False) # logo
    for k in range(6):
        x = 2.4 + k * 1.15 if k < 3 else 10.3 + (k - 3) * 1.15
        m.box([x, 11.4, 2.45], [x + 0.8, 12.2, 2.8], "cream")      # perillas
    m.box([2.2, 12.6, 2.6], [2.8, 13.0, 2.8], "led_red", shade=False)
    for x in (1, 14.2):                                             # esquineros
        for y in (0, 13.2):
            m.box([x - 0.1, y, 2.9], [x + 0.9, y + 0.9, 3.8], "chrome")
    for x in (1.0, 14.1):                                           # esquineros de atras y patas
        for y in (0, 13.2):
            m.box([x - 0.1, y, 12.2], [x + 0.9, y + 0.9, 13.1], "chrome")
    for x in (2.0, 12.6):
        for z in (4.0, 11.0):
            m.box([x, -0.0, z], [x + 1.4, 0.3, z + 1.2], "rubber")
    for k in range(5):                                              # rejilla de ventilacion con valvulas encendidas
        m.box([3.4 + k * 2.0, 14.0, 9.6], [4.6 + k * 2.0, 14.06, 12.0], "orange", shade=False)
    m.box([10.4, 12.5, 2.6], [10.8, 12.9, 2.8], "chrome")           # entrada del cable
    m.box([11.6, 12.5, 2.6], [12.0, 12.9, 2.8], "chrome")
    m.box([5, 14, 7.2], [11, 14.7, 8.8], "black_matte")             # manija
    m.box([4.6, 14, 7.6], [5.2, 15.2, 8.4], "black_matte")
    m.box([10.8, 14, 7.6], [11.4, 15.2, 8.4], "black_matte")
    return m


# ---------------------------------------------------------------- display (como se ve en la mano y en el inventario)
def display_guitar(scale):
    return {
        "gui": {"rotation": [0, 0, -40], "translation": [-1.5, 0.5, 0], "scale": [scale, scale, scale]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [scale * 0.8] * 3},
        "fixed": {"rotation": [0, 0, -40], "translation": [0, 0, 0], "scale": [scale * 1.1] * 3},
        "thirdperson_righthand": {"rotation": [0, 90, 55], "translation": [0, 3, 1], "scale": [scale * 1.2] * 3},
        "thirdperson_lefthand": {"rotation": [0, -90, -55], "translation": [0, 3, 1], "scale": [scale * 1.2] * 3},
        "firstperson_righthand": {"rotation": [0, 90, 30], "translation": [1, 2, 0], "scale": [scale * 1.1] * 3},
        "firstperson_lefthand": {"rotation": [0, -90, -30], "translation": [1, 2, 0], "scale": [scale * 1.1] * 3},
        "head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [scale] * 3},
    }


def display_small():
    return {
        "gui": {"rotation": [25, -30, -20], "translation": [0, 0, 0], "scale": [1.1, 1.1, 1.1]},
        "ground": {"translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
        "fixed": {"scale": [1.0, 1.0, 1.0]},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2, 1.5], "scale": [0.75, 0.75, 0.75]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2, 1.5], "scale": [0.75, 0.75, 0.75]},
        "firstperson_righthand": {"rotation": [0, -20, 10], "translation": [1, 3, -1], "scale": [0.7, 0.7, 0.7]},
        "firstperson_lefthand": {"rotation": [0, 20, -10], "translation": [1, 3, -1], "scale": [0.7, 0.7, 0.7]},
    }


def display_big(scale):
    return {
        "gui": {"rotation": [25, 210, 0], "translation": [0, -1, 0], "scale": [scale] * 3},
        "ground": {"translation": [0, 2, 0], "scale": [scale * 0.8] * 3},
        "fixed": {"rotation": [0, 180, 0], "scale": [scale * 1.2] * 3},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [scale * 0.7] * 3},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [scale * 0.7] * 3},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [scale * 0.8] * 3},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [scale * 0.8] * 3},
    }


MODELS = {}


def build():
    MODELS["guitar"] = (guitar(), display_guitar(0.42))
    MODELS["bass"] = (guitar(body="blue_gloss", guard="black_gloss", strings=4, bass=True), display_guitar(0.4))
    MODELS["microphone"] = (microphone(), display_small())
    MODELS["drum_kit"] = (drum_kit(), display_big(0.5))
    MODELS["keyboard"] = (keyboard(), display_big(0.55))
    MODELS["drumstick"] = (drumstick(), display_small())
    for name, (m, disp) in MODELS.items():
        clamp_check(m, name)
        save(os.path.join(RES, "models", "item", name + ".json"), m.json(disp))
    amp = amplifier()
    clamp_check(amp, "amplifier")
    block = amp.json({
        "gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
        "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]},
    })
    save(os.path.join(RES, "models", "block", "amplifier.json"), block)
    save(os.path.join(RES, "models", "item", "amplifier.json"), {"parent": "starhero:block/amplifier"})
    variants = {f"facing={d}": {"model": "starhero:block/amplifier", "y": y}
                for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
    save(os.path.join(RES, "blockstates", "amplifier.json"), {"variants": variants})
    print("modelos:", ", ".join(list(MODELS) + ["amplifier"]))
    return amp


# ---------------------------------------------------------------- vista previa (para revisar sin abrir el juego)
def preview(tex, amp):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from mpl_toolkits.mplot3d.art3d import Poly3DCollection

    def color(name):
        i = MATERIALS[name]
        cx, cy = (i % 16) * 4, (i // 16) * 4
        px = [tex.getpixel((cx + x, cy + y)) for x in range(4) for y in range(4)]
        return tuple(sum(p[k] for p in px) / len(px) / 255 for k in range(3))

    def faces_of(e):
        (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
        quads = {
            "north": [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)],
            "south": [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)],
            "west": [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)],
            "east": [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)],
            "down": [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
            "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        }
        rot = e.get("rotation")
        out = []
        for face, q in quads.items():
            if face not in e["faces"]:
                continue
            if rot:
                a = math.radians(rot["angle"])
                ox, oy, oz = rot["origin"]
                rq = []
                for x, y, z in q:
                    if rot["axis"] == "x":
                        y, z = oy + (y - oy) * math.cos(a) - (z - oz) * math.sin(a), oz + (y - oy) * math.sin(a) + (z - oz) * math.cos(a)
                    elif rot["axis"] == "y":
                        x, z = ox + (x - ox) * math.cos(a) + (z - oz) * math.sin(a), oz - (x - ox) * math.sin(a) + (z - oz) * math.cos(a)
                    elif rot["axis"] == "z":
                        x, y = ox + (x - ox) * math.cos(a) - (y - oy) * math.sin(a), oy + (x - ox) * math.sin(a) + (y - oy) * math.cos(a)
                    rq.append((x, y, z))
                q = rq
            uv = e["faces"][face]["uv"]
            idx = int(uv[1]) * 16 + int(uv[0])
            name = [k for k, v in MATERIALS.items() if v == idx][0]
            shade = {"north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6, "up": 1.0, "down": 0.5}[face]
            c = tuple(min(1, v * shade) for v in color(name))
            # matplotlib: x, z(profundidad), y(arriba)
            out.append(([(x, z, y) for x, y, z in q], c))
        return out

    os.makedirs(os.path.join(HERE, "preview"), exist_ok=True)
    allm = dict(MODELS)
    allm["amplifier"] = (amp, None)
    for name, (m, _) in allm.items():
        fig = plt.figure(figsize=(10, 5))
        for k, (elev, azim) in enumerate([(12, -90), (25, -55)]):
            ax = fig.add_subplot(1, 2, k + 1, projection="3d")
            polys, cols = [], []
            for e in m.elements:
                for q, c in faces_of(e):
                    polys.append(q)
                    cols.append(c)
            pc = Poly3DCollection(polys, facecolors=cols, edgecolors=(0, 0, 0, 0.15), linewidths=0.2)
            pc.set_sort_zpos(None)
            ax.computed_zorder = True
            ax.add_collection3d(pc)
            pts = [p for q in polys for p in q]
            xs, ys, zs = zip(*pts)
            mid = [(max(v) + min(v)) / 2 for v in (xs, ys, zs)]
            r = max(max(v) - min(v) for v in (xs, ys, zs)) / 2
            ax.set_xlim(mid[0] - r, mid[0] + r)
            ax.set_ylim(mid[1] - r, mid[1] + r)
            ax.set_zlim(mid[2] - r, mid[2] + r)
            ax.view_init(elev=elev, azim=azim)
            ax.set_box_aspect((1, 1, 1))
            ax.set_axis_off()
        fig.suptitle(name)
        fig.savefig(os.path.join(HERE, "preview", name + ".png"), dpi=90, bbox_inches="tight")
        plt.close(fig)
    print("vistas previas en tools/preview/")


if __name__ == "__main__":
    tex = make_texture()
    amp = build()
    if "--preview" in sys.argv:
        preview(tex, amp)
