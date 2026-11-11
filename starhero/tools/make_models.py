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


# ---------------------------------------------------------------- guitarra electrica
def guitar(body="red_gloss", guard="white_pearl", strings=6, bass=False):
    m = Model()
    z0, z1 = 7.0, 9.6       # frente / espalda del cuerpo
    side = {"all": body}
    # cuerpo (se va angostando en escalones, como una guitarra de verdad)
    m.box([13, 2, z0], [19.5, 14, z1], side)        # parte de abajo, la mas ancha
    m.box([19.5, 3.5, z0], [20.5, 12.5, z1], side)  # borde redondeado
    m.box([9.5, 3.8, z0], [13, 12.2, z1], side)     # cintura
    m.box([5.5, 3, z0], [9.5, 13, z1], side)        # parte de arriba
    m.box([2.5, 11, z0], [5.5, 13.6, z1], side)     # cuerno de arriba
    m.box([3.5, 2.6, z0], [5.5, 4.6, z1], side)     # cuerno de abajo
    # golpeador (pickguard) apenas adelante
    m.box([7, 4.6, z0 - 0.15], [16.5, 11.4, z0], {"north": guard, "all": guard})
    m.box([16.5, 5.4, z0 - 0.15], [18, 10.6, z0], {"north": guard, "all": guard})
    # microfonos
    pk = [10.2, 12.8, 15.2] if not bass else [11.5, 14.6]
    for x in pk:
        w = 0.9 if not bass else 1.4
        m.box([x, 5.6, z0 - 0.45], [x + w, 10.4, z0 - 0.15], "black_gloss")
        for k in range(3):  # tornillitos de los microfonos
            m.box([x + 0.25, 6.1 + k * 1.5, z0 - 0.55], [x + w - 0.25, 6.5 + k * 1.5, z0 - 0.45], "chrome")
    # puente y cordal
    m.box([17.2, 5.4, z0 - 0.6], [18.4, 10.6, z0 - 0.15], "chrome")
    # perillas y selector
    for i, (x, y) in enumerate([(16.4, 3.1), (18.2, 3.6), (19.4, 4.9)]):
        m.box([x, y, z0 - 0.7], [x + 0.9, y + 0.9, z0], "cream" if not bass else "black_gloss")
    m.box([15.6, 11.5, z0 - 0.5], [16.6, 12.2, z0], "black_gloss")
    # mastil y diapason
    nl = -10.0 if not bass else -12.5
    m.box([nl, 7.0, 7.6], [5.6, 9.0, 9.0], "maple")
    m.box([nl, 6.85, 7.15], [6.0, 9.15, 7.6], {"all": "rosewood"})
    # trastes
    x = 5.0
    gap = 1.9
    while x > nl + 0.6:
        m.box([x, 6.85, 7.05], [x + 0.22, 9.15, 7.15], "chrome")
        x -= gap
        gap *= 0.94
    # marcadores del diapason
    for xm in (-1.1, 1.8, -4.4, -7.4):
        if xm > nl + 1:
            m.box([xm, 7.85, 7.08], [xm + 0.45, 8.15, 7.15], "white_pearl")
    # clavijero (cabeza) con un poco de angulo
    hl = nl - 5.5
    m.box([hl, 6.4, 7.7], [nl, 9.9, 8.8], "maple" if not bass else body)
    m.box([hl + 0.8, 9.9, 7.7], [nl - 0.6, 10.6, 8.8], "maple" if not bass else body)
    m.box([hl - 0.6, 7.2, 7.7], [hl, 9.6, 8.8], "maple" if not bass else body)
    # clavijas
    n_tuners = strings
    for i in range(n_tuners):
        tx = hl + 0.4 + i * ((nl - hl - 0.8) / max(1, n_tuners - 1)) - 0.3
        m.box([tx, 10.0, 7.95], [tx + 0.6, 11.2, 8.55], "chrome")
        m.box([tx + 0.05, 7.6 + (i % 2) * 0.9, 7.45], [tx + 0.55, 8.1 + (i % 2) * 0.9, 7.7], "chrome")
    # cuerdas (finitas, por delante de todo)
    for i in range(strings):
        y = 7.15 + i * (1.7 / max(1, strings - 1))
        m.box([nl - 0.3, y, 6.32], [17.6, y + 0.1, 6.42], "string", shade=False)
    # boton de la correa
    m.box([20.5, 7.6, 8.0], [21.0, 8.4, 8.6], "chrome")
    return m.fit_x()


# ---------------------------------------------------------------- microfono
def microphone():
    m = Model()
    m.box([7.1, 0, 7.1], [8.9, 8.6, 8.9], "black_matte")       # mango
    m.box([6.9, 6.2, 6.9], [9.1, 7.0, 9.1], "chrome")          # anillo
    m.box([7.3, -1.2, 7.3], [8.7, 0, 8.7], "gray")              # cable
    m.box([7.0, 1.6, 6.85], [9.0, 2.2, 7.1], "led_green", shade=False)  # luz de encendido
    # cabeza (esfera de rejilla, hecha con tres cubos cruzados)
    m.box([6.3, 9.0, 6.6], [9.7, 12.6, 9.4], "chrome")
    m.box([6.6, 8.7, 6.3], [9.4, 12.9, 9.7], "chrome")
    m.box([6.6, 9.3, 6.6], [9.4, 13.4, 9.4], "chrome")
    m.box([6.15, 10.4, 7.3], [9.85, 11.2, 8.7], "black_gloss")  # aro del medio
    return m


# ---------------------------------------------------------------- bateria
def drum(m, x0, x1, y0, y1, z0, z1, shell="sparkle_red", top=True):
    """Tambor redondeado: dos cubos cruzados + parche + aro cromado."""
    dx, dz = (x1 - x0) * 0.15, (z1 - z0) * 0.15
    m.box([x0, y0, z0 + dz], [x1, y1, z1 - dz], shell)
    m.box([x0 + dx, y0, z0], [x1 - dx, y1, z1], shell)
    if top:
        m.box([x0 + dx * 0.5, y1, z0 + dz * 0.5], [x1 - dx * 0.5, y1 + 0.2, z1 - dz * 0.5], "drum_head")
        m.box([x0, y1 - 0.4, z0 + dz], [x1, y1 + 0.25, z0 + dz + 0.3], "chrome")
        m.box([x0, y1 - 0.4, z1 - dz - 0.3], [x1, y1 + 0.25, z1 - dz], "chrome")


def pole(m, x, z, y0, y1, w=0.5):
    m.box([x - w / 2, y0, z - w / 2], [x + w / 2, y1, z + w / 2], "chrome")


def cymbal(m, cx, cz, y, r, mat="brass"):
    m.box([cx - r, y, cz - r * 0.7], [cx + r, y + 0.25, cz + r * 0.7], mat)
    m.box([cx - r * 0.7, y, cz - r], [cx + r * 0.7, y + 0.25, cz + r], mat)
    m.box([cx - 0.6, y + 0.25, cz - 0.6], [cx + 0.6, y + 0.6, cz + 0.6], mat)


def drum_kit():
    m = Model()
    # bombo: acostado, el parche con el logo mira al publico (-Z)
    m.box([3, 1.2, 2], [13, 8.8, 9.5], "sparkle_red")
    m.box([4.2, 0.2, 2], [11.8, 9.8, 9.5], "sparkle_red")
    m.box([4.4, 1.4, 1.75], [11.6, 8.6, 2], "drum_head")             # parche delantero
    # logo: disco violeta con una estrella amarilla de 8 puntas
    m.box([5.6, 2.8, 1.62], [10.4, 7.2, 1.75], "purple", shade=False)
    m.box([6.2, 2.2, 1.62], [9.8, 7.8, 1.75], "purple", shade=False)
    m.box([7.6, 3.0, 1.55], [8.4, 7.0, 1.62], "yellow", shade=False)
    m.box([6.0, 4.6, 1.55], [10.0, 5.4, 1.62], "yellow", shade=False)
    for a in (45, -45):
        m.box([7.65, 3.4, 1.55], [8.35, 6.6, 1.62], "yellow", rot={"angle": a, "axis": "z", "origin": [8, 5, 1.6]}, shade=False)
    for z in (1.6, 9.5):
        m.box([2.8, 1.0, z], [13.2, 9.0, z + 0.4], "chrome")
    m.box([3.5, 0, 3], [4.0, 1.2, 3.5], "chrome")   # patas del bombo
    m.box([12.0, 0, 3], [12.5, 1.2, 3.5], "chrome")
    # pedal
    m.box([7.2, 0, 9.5], [8.8, 0.6, 13], "black_matte")
    # toms de arriba
    drum(m, 3.2, 7.6, 10.2, 13.6, 3.5, 7.5)
    drum(m, 8.4, 12.8, 10.2, 13.6, 3.5, 7.5)
    m.box([7.6, 9.8, 5.0], [8.4, 11.6, 6.0], "chrome")  # soporte
    # tambor (caja) a la izquierda del baterista
    pole(m, 1.6, 12.0, 0, 8.6)
    m.box([0.0, 0, 11.6], [3.2, 0.4, 12.4], "chrome")
    drum(m, -1.4, 4.6, 8.6, 10.6, 9.6, 14.6, shell="chrome")
    # hi-hat
    pole(m, -4.0, 11.0, 0, 13.2)
    m.box([-5.4, 0, 10.6], [-2.6, 0.4, 11.4], "chrome")
    cymbal(m, -4.0, 11.0, 12.2, 3.0)
    cymbal(m, -4.0, 11.0, 12.7, 3.0)
    # tom de piso a la derecha
    drum(m, 12.0, 17.6, 3.0, 9.0, 9.6, 14.8)
    for x, z in ((12.4, 10.0), (17.0, 10.0), (12.4, 14.2)):
        pole(m, x, z, 0, 3.0, 0.4)
    # platillos de choque y ride
    pole(m, -3.0, 3.0, 0, 17.2)
    cymbal(m, -3.0, 3.0, 17.0, 4.4)
    pole(m, 19.0, 5.0, 0, 15.2)
    cymbal(m, 19.0, 5.0, 15.0, 4.6)
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
    # ruedas de pitch
    m.box([-4.7, 15.8, 3.2], [-4.2, 16.6, 5.0], "chrome")
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
