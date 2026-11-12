#!/usr/bin/env python3
"""Genera el modelo, las texturas y los idiomas de Star Combat. Uso: python3 tools/make_assets.py"""
import json, math, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'starcombat')
random.seed(7)

def out(*parts):
    path = os.path.join(ROOT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path

# ------------------------------------------------------------------ textura de la hoja (32x32)
# regiones en unidades UV (16 = todo): metal u0-8, filo u8-10, brasa u10-12, cuero u12-16 v0-8, bronce u12-16 v8-16
img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
px = img.load()
def noise(base, amp):
    d = random.randint(-amp, amp)
    return tuple(max(0, min(255, c + d)) for c in base) + (255,)
for y in range(32):
    for x in range(16):      # metal oscuro: mas claro hacia el filo, tinte caliente hacia la punta (arriba)
        heat = max(0, 1 - y / 14.0) * 0.35
        k = x / 15.0
        base = (38 + int(34 * k) + int(70 * heat), 36 + int(30 * k) + int(18 * heat), 44 + int(30 * k))
        px[x, y] = noise(base, 6)
    for x in range(16, 20):  # filo
        heat = max(0, 1 - y / 18.0)
        base = (150 + int(90 * heat), 142 + int(10 * heat), 138 - int(70 * heat))
        px[x, y] = noise(base, 8)
    for x in range(20, 24):  # brasa
        t = 0.5 + 0.5 * math.sin(y * 0.9 + x)
        base = (255, 110 + int(110 * t), 20 + int(50 * t))
        px[x, y] = noise(base, 10)
for y in range(16):
    for x in range(24, 32):  # cuero en bandas
        band = (y + x // 2) % 4
        base = (92, 26, 22) if band < 2 else (58, 16, 16)
        px[x, y] = noise(base, 6)
for y in range(16, 32):
    for x in range(24, 32):  # bronce
        base = (150, 104, 52) if (x + y) % 5 else (196, 150, 84)
        px[x, y] = noise(base, 9)
for _ in range(26):          # rayones
    x, y = random.randint(0, 15), random.randint(0, 31)
    for i in range(random.randint(2, 5)):
        if 0 <= x + i < 16 and 0 <= y - i < 32:
            r, g, b, a = px[x + i, y - i]
            px[x + i, y - i] = (min(255, r + 26), min(255, g + 24), min(255, b + 22), 255)
img.save(out('textures', 'item', 'ember_fangs.png'))

# ------------------------------------------------------------------ texturas de efectos
chain = Image.new('RGBA', (16, 8), (0, 0, 0, 0))
cp = chain.load()
for x in range(16):
    for y in range(8):
        dx, dy = (x - 7.5) / 7.5, (y - 3.5) / 3.5
        d = dx * dx + dy * dy
        if 0.42 < d <= 1.0:
            shade = 0.75 + 0.25 * (-dy)
            hot = max(0.0, 1 - abs(dx))
            cp[x, y] = (int((70 + 110 * hot) * shade), int((62 + 30 * hot) * shade), int(64 * shade), 255)
chain.save(out('textures', 'fx', 'chain.png'))

glow = Image.new('RGBA', (16, 8), (0, 0, 0, 255))
gp = glow.load()
for x in range(16):
    for y in range(8):
        dx, dy = (x - 7.5) / 7.5, (y - 3.5) / 3.5
        d = dx * dx + dy * dy
        if 0.42 < d <= 1.0:
            v = int(150 * max(0.0, 1 - abs(dx) * 0.8))
            gp[x, y] = (v, v, v, 255)
glow.save(out('textures', 'fx', 'chain_glow.png'))

trail = Image.new('RGBA', (32, 16), (0, 0, 0, 255))
tp = trail.load()
for x in range(32):
    for y in range(16):
        v = y / 15.0                      # 0 = punta de la hoja
        a = (1 - v) ** 1.4 * (0.75 + 0.25 * math.sin(x * 1.7 + y * 0.6))
        edge = math.exp(-((v - 0.06) / 0.07) ** 2)
        k = max(0.0, min(1.0, a * 0.8 + edge * 0.6))
        c = int(255 * k)
        tp[x, y] = (c, c, c, 255)
trail.save(out('textures', 'fx', 'trail.png'))

# ------------------------------------------------------------------ modelo
def face(u0, v0, u1, v1):
    return {"uv": [round(u0, 3), round(v0, 3), round(u1, 3), round(v1, 3)], "texture": "#0"}

def box(frm, to, main, sides=None):
    sides = sides or main
    return {"from": [round(c, 3) for c in frm], "to": [round(c, 3) for c in to],
            "faces": {"north": face(*main), "south": face(*main), "east": face(*sides), "west": face(*sides),
                      "up": face(*sides), "down": face(*sides)}}

BRONZE = (12.5, 8.5, 15.5, 15.5)
LEATHER = (12.2, 0.2, 15.8, 7.8)
EDGE = (8.2, 1, 9.8, 15)
Y0, Y1 = 9.0, 31.0

def spine(y):
    return 5.0 + 0.010 * (y - Y0) ** 2

def width(y):
    if y <= 24:
        return 5.2 + 0.8 * math.sin((y - Y0) / 15.0 * math.pi)
    t = (y - 24) / (Y1 - 24)
    return 5.2 + (0.9 - 5.2) * (t * t * (3 - 2 * t))

def v_of(y):
    return 16 - (y - Y0) / (Y1 - Y0) * 16

base, glowels = [], []
# pomo, empuñadura y guarda
base.append(box([7.4, -3, 7.4], [8.6, -1, 8.6], BRONZE))
base.append(box([6.8, -1, 6.8], [9.2, 1, 9.2], BRONZE))
base.append(box([7.2, 1, 7.2], [8.8, 7.5, 8.8], LEATHER))
base.append(box([4.0, 7.5, 6.6], [12.6, 9, 9.4], BRONZE))
base.append(box([3.4, 8.2, 7.0], [4.6, 10.6, 9.0], BRONZE))
base.append(box([12.0, 8.2, 7.0], [13.2, 10.2, 9.0], BRONZE))
# hoja: rebanadas que siguen la curva
y = Y0
while y < Y1 - 0.01:
    y2 = min(Y1, y + 1.0)
    ym = (y + y2) / 2
    xs, w = spine(ym), width(ym)
    if 10.5 <= ym <= 12.5:          # muesca en el lomo, cerca de la guarda
        xs += 1.0
        w -= 1.0
    xe = xs + w
    u0, u1 = max(0.0, (xs - 4.5) / 9.5 * 8), min(8.0, (xe - 0.6 - 4.5) / 9.5 * 8)
    base.append(box([xs, y, 7.45], [xe - 0.6, y2, 8.55], (u0, v_of(y2), u1, v_of(y)), (0.5, v_of(y2), 1.5, v_of(y))))
    base.append(box([xe - 0.6, y, 7.6], [xe, y2, 8.4], (8.2, v_of(y2), 9.8, v_of(y)), EDGE))   # filo
    if 11 <= ym <= 27.5:            # veta de brasa
        cx = xs + w * 0.36
        base.append(box([cx - 0.45, y, 7.3], [cx + 0.45, y2, 8.7], (10.2, v_of(y2), 11.8, v_of(y))))
        glowels.append(box([cx - 0.5, y, 7.25], [cx + 0.5, y2, 8.75], (10.2, v_of(y2), 11.8, v_of(y))))
    y = y2
# punta
xt = spine(Y1)
base.append(box([xt, Y1, 7.6], [xt + 0.9, Y1 + 0.8, 8.4], EDGE))

FIRST = {"rotation": [0, 90, 20], "translation": [1.13, 0.31, 0.07], "scale": [0.6, 0.6, 0.6]}
# en tercera persona se empuña como una espada; Punchy usa esta misma pose para sus manos en primera persona
# el filo mira hacia abajo en las dos manos: la izquierda se declara aparte porque el juego le invierte los giros
THIRD = {"rotation": [0, 90, -10], "translation": [0, 0, 1.2], "scale": [0.7, 0.7, 0.7]}
THIRD_L = {"rotation": [0, -90, 10], "translation": [0, 0, 1.2], "scale": [0.7, 0.7, 0.7]}
FIRST_L = {"rotation": [0, -90, -20], "translation": [1.13, 0.31, 0.07], "scale": [0.6, 0.6, 0.6]}
display = {
    "thirdperson_righthand": THIRD, "thirdperson_lefthand": THIRD_L,
    "firstperson_righthand": FIRST, "firstperson_lefthand": FIRST_L,
    "gui": {"rotation": [0, 0, -40], "translation": [-1.8, -2.1, 0], "scale": [0.5, 0.5, 0.5]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 40], "translation": [1.8, -2.1, 0], "scale": [0.55, 0.55, 0.55]},
    "head": {"scale": [0.6, 0.6, 0.6]},
}
tex = {"0": "starcombat:item/ember_fangs", "particle": "starcombat:item/ember_fangs"}
json.dump({"credit": "Star Combat", "texture_size": [32, 32], "textures": tex, "gui_light": "front",
           "elements": base, "display": display}, open(out('models', 'item', 'ember_fangs.json'), 'w'), separators=(',', ':'))
json.dump({"credit": "Star Combat", "texture_size": [32, 32], "textures": tex, "gui_light": "front",
           "elements": glowels, "display": display}, open(out('models', 'item', 'ember_fangs_glow.json'), 'w'), separators=(',', ':'))

# ------------------------------------------------------------------ idiomas
ES = {
    "item.starcombat.ember_fangs": "Colmillos de Brasa",
    "item.starcombat.ember_fangs_glow": "Brasa",
    "item.starcombat.ember_fangs.lore": "Dos hojas atadas a quien las empuña. No se sueltan.",
    "item.starcombat.ember_fangs.tip1": "Clic izquierdo (se puede mantener): combo de cuatro golpes",
    "item.starcombat.ember_fangs.tip2": "Clic derecho: barrido en giro y martillo",
    "item.starcombat.ember_fangs.tip3": "Dos livianos y clic derecho: ciclón",
    "item.starcombat.ember_fangs.tip4": "Clic derecho en el aire: caída con estallido",
    "item.starcombat.ember_fangs.tip5": "Tecla del gancho (R): engancharse y subir, o atraer a una criatura",
    "itemGroup.starcombat": "Star Combat",
    "key.categories.starcombat": "Star Combat",
    "key.starcombat.grapple": "Gancho de cadenas",
    "commands.starcombat.give": "Colmillos de Brasa entregados a %s jugador(es)",
    "commands.starcombat.reload": "Configuración de Star Combat recargada",
}
EN = {
    "item.starcombat.ember_fangs": "Ember Fangs",
    "item.starcombat.ember_fangs_glow": "Ember",
    "item.starcombat.ember_fangs.lore": "Two blades bound to their wielder. They do not let go.",
    "item.starcombat.ember_fangs.tip1": "Left click (can be held): four-hit combo",
    "item.starcombat.ember_fangs.tip2": "Right click: spinning sweep and hammer",
    "item.starcombat.ember_fangs.tip3": "Two light hits, then right click: cyclone",
    "item.starcombat.ember_fangs.tip4": "Right click in the air: plunge with a burst",
    "item.starcombat.ember_fangs.tip5": "Grapple key (R): hook and climb, or pull a creature",
    "itemGroup.starcombat": "Star Combat",
    "key.categories.starcombat": "Star Combat",
    "key.starcombat.grapple": "Chain grapple",
    "commands.starcombat.give": "Gave Ember Fangs to %s player(s)",
    "commands.starcombat.reload": "Star Combat config reloaded",
}
for code in ("es_ar", "es_es", "es_mx", "es_cl", "es_uy", "es_ve"):
    json.dump(ES, open(out('lang', code + '.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
json.dump(EN, open(out('lang', 'en_us.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
print(len(base), 'elementos;', len(glowels), 'de brillo')
