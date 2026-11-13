"""Rig humano de Star NPCs: huesos con codos, rodillas y cintura, props y texturas. Genera la parte de modelo del .bbmodel."""
import base64
import io
import random
import uuid

from PIL import Image

RNG = random.Random(20261113)


def uid():
    return str(uuid.UUID(int=RNG.getrandbits(128), version=4))


# ---------------------------------------------------------------- texturas

SWATCHES = ['wood', 'wood_dark', 'iron', 'iron_dark', 'stone', 'red', 'white', 'straw',
            'rope', 'hot', 'leaf', 'gold', 'blue', 'bark', 'cloth', 'black']
SWATCH_RGB = {
    'wood': (150, 111, 66), 'wood_dark': (96, 66, 38), 'iron': (176, 180, 188), 'iron_dark': (74, 78, 88),
    'stone': (120, 120, 124), 'red': (176, 52, 48), 'white': (232, 226, 210), 'straw': (214, 178, 84),
    'rope': (196, 178, 140), 'hot': (255, 150, 40), 'leaf': (86, 140, 62), 'gold': (240, 196, 64),
    'blue': (70, 120, 190), 'bark': (84, 62, 40), 'cloth': (130, 96, 150), 'black': (36, 34, 40),
}


def props_texture():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px = img.load()
    r = random.Random(7)
    for i, name in enumerate(SWATCHES):
        sx, sy = (i % 8) * 8, (i // 8) * 8
        base = SWATCH_RGB[name]
        for y in range(8):
            for x in range(8):
                k = 0.86 + r.random() * 0.2
                if name in ('wood', 'wood_dark', 'bark') and x % 3 == 0:
                    k *= 0.88
                if name == 'straw' and x % 2 == 0:
                    k *= 0.85
                px[sx + x, sy + y] = tuple(min(255, int(c * k)) for c in base) + (255,)
    return img


def swatch_uv(name):
    i = SWATCHES.index(name)
    sx, sy = (i % 8) * 8, (i // 8) * 8
    return [sx + 1, sy + 1, sx + 7, sy + 7]


def default_skin():
    """Una skin sencilla para que el modelo no se vea vacio en Blockbench. En el juego la reemplaza la del NPC."""
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    r = random.Random(3)

    def fill(x0, y0, x1, y1, rgb, noise=0.08):
        for y in range(y0, y1):
            for x in range(x0, x1):
                k = 1 - noise + r.random() * noise * 2
                img.putpixel((x, y), tuple(min(255, int(c * k)) for c in rgb) + (255,))

    skin, hair, tunic, pants, boots, belt = (224, 172, 132), (92, 60, 36), (70, 112, 96), (74, 66, 92), (60, 44, 34), (120, 84, 44)
    fill(0, 0, 32, 16, skin)          # cabeza
    fill(8, 0, 16, 8, hair)           # arriba
    fill(0, 8, 8, 11, hair)
    fill(16, 8, 32, 11, hair)
    fill(24, 8, 32, 16, hair)         # nuca
    fill(8, 8, 16, 10, hair)          # flequillo
    for x, c in ((10, (255, 255, 255)), (13, (255, 255, 255)), (9, (60, 90, 140)), (14, (60, 90, 140))):
        img.putpixel((x, 12), c + (255,))
    fill(11, 14, 13, 15, (170, 110, 96), 0)
    fill(16, 16, 40, 32, tunic)       # torso
    fill(16, 28, 40, 30, belt)
    fill(40, 16, 56, 32, tunic)       # brazo derecho
    fill(40, 28, 56, 32, skin)
    fill(32, 48, 48, 64, tunic)       # brazo izquierdo
    fill(32, 60, 48, 64, skin)
    fill(0, 16, 16, 32, pants)        # pierna derecha
    fill(0, 28, 16, 32, boots)
    fill(16, 48, 32, 64, pants)       # pierna izquierda
    fill(16, 60, 32, 64, boots)
    return img


def data_url(img):
    buf = io.BytesIO()
    img.save(buf, 'PNG')
    return 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()


# ---------------------------------------------------------------- modelo

# inclinacion de cada herramienta respecto de la mano (grados; 0 = perpendicular al antebrazo, apuntando adelante)
TILT = {'prop_hammer': (-45, 0, 0), 'prop_rod': (-30, 0, 0), 'prop_sword': (-75, 0, 0), 'prop_axe': (-70, 0, 0)}


def shift(bone, dx, dy, dz):
    """Mueve un prop entero (hueso y cubos)."""
    d = (dx, dy, dz)
    bone.origin = [bone.origin[i] + d[i] for i in range(3)]
    for c in bone.cubes:
        c.frm = [c.frm[i] + d[i] for i in range(3)]
        c.to = [c.to[i] + d[i] for i in range(3)]
        c.origin = [c.origin[i] + d[i] for i in range(3)]
    for k in bone.kids:
        shift(k, dx, dy, dz)


class Bone:
    def __init__(self, name, origin, rotation=(0, 0, 0)):
        self.name, self.origin, self.rotation = name, list(origin), list(rotation)
        self.kids, self.cubes, self.uuid = [], [], uid()

    def add(self, *kids):
        self.kids.extend(kids)
        return self


class Cube:
    def __init__(self, name, frm, to, faces, inflate=0, origin=None, rotation=None):
        self.name, self.frm, self.to, self.faces, self.inflate = name, list(frm), list(to), faces, inflate
        self.origin = list(origin) if origin else [0, 0, 0]
        self.rotation = list(rotation) if rotation else None
        self.uuid = uid()


def skin_faces(u, v, w, h, d, y0=0.0, y1=1.0, cap_top=True, cap_bottom=True):
    """Caras de una caja con el reparto de una skin de jugador. y0..y1 = tramo vertical (0 arriba, 1 abajo)."""
    a, b = v + d + h * y0, v + d + h * y1
    f = {
        'north': [u + d, a, u + d + w, b],
        'east': [u, a, u + d, b],
        'south': [u + d + w + d, a, u + d + w + d + w, b],
        'west': [u + d + w, a, u + d + w + d, b],
    }
    strip = [u + d, max(v + d, a - 1 if not cap_top else a), u + d + w, 0]
    if cap_top:
        f['up'] = [u + d + w, v + d, u + d, v]
    else:
        f['up'] = [u + d, a, u + d + w, a + 1]
    if cap_bottom:
        f['down'] = [u + d + w + w, v, u + d + w, v + d]
    else:
        f['down'] = [u + d, b - 1, u + d + w, b]
    return {k: {'uv': uv, 'texture': 0} for k, uv in f.items()}


def prop_faces(name):
    return {k: {'uv': swatch_uv(name), 'texture': 1} for k in ('north', 'east', 'south', 'west', 'up', 'down')}


def pbox(name, frm, to, swatch, origin=None, rotation=None):
    return Cube(name, frm, to, prop_faces(swatch), 0, origin, rotation)


def limb(bone_up, bone_low, name, x0, x1, top, mid, bottom, z0, z1, u, v, w, lu, lv):
    """Brazo o pierna partido en dos: el tramo de arriba va en bone_up y el de abajo en bone_low, con su capa exterior."""
    d = z1 - z0
    h = top - bottom
    bone_up.cubes.append(Cube(name + ' upper', [x0, mid, z0], [x1, top, z1], skin_faces(u, v, w, h, d, 0, 0.5, True, False)))
    bone_up.cubes.append(Cube(name + ' upper layer', [x0, mid, z0], [x1, top, z1], skin_faces(lu, lv, w, h, d, 0, 0.5, True, False), 0.25))
    bone_low.cubes.append(Cube(name + ' lower', [x0, bottom, z0], [x1, mid, z1], skin_faces(u, v, w, h, d, 0.5, 1, False, True)))
    bone_low.cubes.append(Cube(name + ' lower layer', [x0, bottom, z0], [x1, mid, z1], skin_faces(lu, lv, w, h, d, 0.5, 1, False, True), 0.25))


def build(slim=False):
    aw = 3 if slim else 4
    root = Bone('npc', (0, 0, 0))
    waist = Bone('waist', (0, 12, 0))
    torso = Bone('torso', (0, 12, 0))
    chest = Bone('chest', (0, 18, 0))
    head = Bone('head', (0, 24, 0))
    r_arm, r_fore = Bone('right_arm', (5, 22, 0)), Bone('right_forearm', (4 + aw / 2, 18, 0))
    l_arm, l_fore = Bone('left_arm', (-5, 22, 0)), Bone('left_forearm', (-4 - aw / 2, 18, 0))
    r_leg, r_shin = Bone('right_leg', (2, 12, 0)), Bone('right_shin', (2, 6, 0))
    l_leg, l_shin = Bone('left_leg', (-2, 12, 0)), Bone('left_shin', (-2, 6, 0))
    item_r, item_l = Bone('item_right', (4 + aw / 2, 12, 0)), Bone('item_left', (-4 - aw / 2, 12, 0))

    head.cubes.append(Cube('Head', [-4, 24, -4], [4, 32, 4], skin_faces(0, 0, 8, 8, 8)))
    head.cubes.append(Cube('Hat', [-4, 24, -4], [4, 32, 4], skin_faces(32, 0, 8, 8, 8), 0.5))
    chest.cubes.append(Cube('Chest', [-4, 18, -2], [4, 24, 2], skin_faces(16, 16, 8, 12, 4, 0, 0.5, True, False)))
    chest.cubes.append(Cube('Chest layer', [-4, 18, -2], [4, 24, 2], skin_faces(16, 32, 8, 12, 4, 0, 0.5, True, False), 0.25))
    torso.cubes.append(Cube('Belly', [-4, 12, -2], [4, 18, 2], skin_faces(16, 16, 8, 12, 4, 0.5, 1, False, True)))
    torso.cubes.append(Cube('Belly layer', [-4, 12, -2], [4, 18, 2], skin_faces(16, 32, 8, 12, 4, 0.5, 1, False, True), 0.25))
    limb(r_arm, r_fore, 'Right arm', 4, 4 + aw, 24, 18, 12, -2, 2, 40, 16, aw, 40, 32)
    limb(l_arm, l_fore, 'Left arm', -4 - aw, -4, 24, 18, 12, -2, 2, 32, 48, aw, 48, 48)
    limb(r_leg, r_shin, 'Right leg', 0, 4, 12, 6, 0, -2, 2, 0, 16, 4, 0, 32)
    limb(l_leg, l_shin, 'Left leg', -4, 0, 12, 6, 0, -2, 2, 16, 48, 4, 0, 48)

    # ---- props en las manos (definidos con el brazo colgando; la mano esta en y=12)
    hx = 4 + aw / 2
    hammer = Bone('prop_hammer', (hx, 12.5, 0), TILT['prop_hammer'])
    hammer.cubes += [pbox('Hammer handle', [hx - 0.5, 12, -10], [hx + 0.5, 13, 1.5], 'wood'),
                     pbox('Hammer head', [hx - 1.5, 10.5, -13], [hx + 1.5, 14.5, -9.5], 'iron_dark')]
    broom = Bone('prop_broom', (hx, 12.5, -2.5))
    broom.cubes += [pbox('Broom stick', [hx - 0.5, -6, -3], [hx + 0.5, 22, -2], 'wood'),
                    pbox('Broom tie', [hx - 1.5, -6.5, -4], [hx + 1.5, -5, -1], 'rope'),
                    pbox('Broom bristles', [hx - 2.5, -12, -4.5], [hx + 2.5, -6.5, -0.5], 'straw')]
    rod = Bone('prop_rod', (hx, 12.5, 0), TILT['prop_rod'])
    rod.cubes += [pbox('Rod grip', [hx - 0.6, 11.9, -3], [hx + 0.6, 13.1, 3], 'wood_dark'),
                  pbox('Rod', [hx - 0.35, 12.15, -30], [hx + 0.35, 12.85, -3], 'wood'),
                  pbox('Reel', [hx - 1.4, 11, -5], [hx - 0.4, 13, -3.2], 'iron')]
    sword = Bone('prop_sword', (hx, 12.5, 0), TILT['prop_sword'])
    sword.cubes += [pbox('Sword grip', [hx - 0.5, 12, -2], [hx + 0.5, 13, 2.5], 'wood_dark'),
                    pbox('Sword guard', [hx - 2.5, 11.75, -3], [hx + 2.5, 13.25, -2], 'gold'),
                    pbox('Sword blade', [hx - 1, 12.25, -17], [hx + 1, 12.75, -3], 'iron'),
                    pbox('Sword tip', [hx - 0.5, 12.25, -18.5], [hx + 0.5, 12.75, -17], 'iron')]
    axe = Bone('prop_axe', (hx, 12.5, 0), TILT['prop_axe'])
    axe.cubes += [pbox('Axe handle', [hx - 0.5, 12, -13], [hx + 0.5, 13, 2.5], 'wood'),
                  pbox('Axe head', [hx - 0.5, 8.5, -13.5], [hx + 0.5, 13.5, -9.5], 'iron'),
                  pbox('Axe edge', [hx - 0.3, 7.5, -13], [hx + 0.3, 8.5, -10], 'white')]
    r_fore.add(item_r, hammer, broom, rod, sword, axe)
    l_fore.add(item_l)

    chest.add(head, r_arm, l_arm)
    r_arm.add(r_fore)
    l_arm.add(l_fore)
    r_leg.add(r_shin)
    l_leg.add(l_shin)
    torso.add(chest)
    waist.add(torso, r_leg, l_leg)

    # ---- bote: se mece junto con el personaje (los dos cuelgan de 'npc')
    boat = Bone('prop_boat', (0, 0, 0))
    boat.cubes += [pbox('Boat floor', [-6, -4, -17], [6, -3, 15], 'wood_dark'),
                   pbox('Boat side R', [6, -4, -17], [7.5, 3, 15], 'wood'),
                   pbox('Boat side L', [-7.5, -4, -17], [-6, 3, 15], 'wood'),
                   pbox('Boat stern', [-6, -4, 15], [6, 3, 16.5], 'wood'),
                   pbox('Boat bow', [-5, -4, -20], [5, 3, -17], 'wood'),
                   pbox('Boat bow tip', [-3, -3, -23], [3, 3.5, -20], 'wood'),
                   pbox('Boat seat', [-6, 0.5, -3], [6, 1.5, 3], 'wood_dark'),
                   pbox('Boat seat front', [-6, 0.5, -13], [6, 1.5, -10], 'wood_dark'),
                   pbox('Boat rim R', [6.5, 3, -17], [8, 3.8, 15], 'wood_dark'),
                   pbox('Boat rim L', [-8, 3, -17], [-6.5, 3.8, 15], 'wood_dark'),
                   pbox('Bucket', [-4.5, -3, 8], [-0.5, 1, 12], 'iron_dark')]
    root.add(waist, boat)

    roots = [root]

    # ---- props fijos en el mundo (hermanos de 'npc': no se mueven con el)
    line = Bone('prop_line', (hx, 0, -34))
    line.cubes += [pbox('Line', [hx - 0.12, -3, -34.12], [hx + 0.12, 21, -33.88], 'white'),
                   pbox('Bobber top', [hx - 0.8, -3.2, -34.8], [hx + 0.8, -2, -33.2], 'red'),
                   pbox('Bobber', [hx - 0.8, -4.4, -34.8], [hx + 0.8, -3.2, -33.2], 'white')]
    anvil = Bone('prop_anvil', (0, 0, -13))
    anvil.cubes += [pbox('Anvil stump', [-5, 0, -18], [5, 6, -8], 'bark'),
                    pbox('Anvil stump top', [-4.5, 6, -17.5], [4.5, 6.5, -8.5], 'wood'),
                    pbox('Anvil foot', [-4, 6.5, -16], [4, 8, -10], 'iron_dark'),
                    pbox('Anvil waist', [-2.5, 8, -15], [2.5, 9.5, -11], 'iron_dark'),
                    pbox('Anvil top', [-5.5, 9.5, -16], [5.5, 12, -10], 'iron_dark'),
                    pbox('Anvil horn', [5.5, 10.2, -14.5], [8.5, 11.8, -11.5], 'iron_dark'),
                    pbox('Hot blade', [-8, 12, -13.6], [3, 12.5, -12.4], 'hot'),
                    pbox('Blade tang', [-10.5, 12, -13.4], [-8, 12.5, -12.6], 'iron')]
    block = Bone('prop_log', (0, 0, -15))
    block.cubes += [pbox('Chop block', [-5.5, 0, -20.5], [5.5, 7, -9.5], 'bark'),
                    pbox('Chop block top', [-5, 7, -20], [5, 7.4, -10], 'wood'),
                    pbox('Log', [-2.5, 7.4, -17.5], [2.5, 15, -12.5], 'bark'),
                    pbox('Log top', [-2, 15, -17], [2, 15.3, -13], 'wood'),
                    pbox('Split log A', [8, 0, -14], [12, 3, -6], 'wood', [10, 1.5, -10], [0, 25, 0]),
                    pbox('Split log B', [-13, 0, -16], [-9, 3, -8], 'wood', [-11, 1.5, -12], [0, -40, 0]),
                    pbox('Split log C', [9, 3, -13], [12.5, 5.5, -6], 'bark', [10.5, 4, -9.5], [0, -20, 0])]
    stall = Bone('prop_stall', (0, 0, -13))
    stall.cubes += [pbox('Counter', [-15, 0, -17], [15, 10, -9], 'wood'),
                    pbox('Counter top', [-16, 10, -18], [16, 11, -8], 'wood_dark'),
                    pbox('Counter cloth', [-16.2, 6, -18.2], [16.2, 10.2, -17.8], 'red'),
                    pbox('Pole R', [14, 11, -9.5], [15.5, 36, -8], 'wood_dark'),
                    pbox('Pole L', [-15.5, 11, -9.5], [-14, 36, -8], 'wood_dark')]
    for i in range(8):
        stall.cubes.append(pbox('Awning %d' % i, [-16 + i * 4, 35, -20], [-12 + i * 4, 36.5, -4], 'red' if i % 2 == 0 else 'white',
                                [0, 36, -8], [-12, 0, 0]))
    stall.cubes += [pbox('Goods gold', [-12, 11, -15.5], [-9, 13, -12.5], 'gold'),
                    pbox('Goods jar', [-6, 11, -15], [-3.5, 15, -12.5], 'blue'),
                    pbox('Goods jar lid', [-5.6, 15, -14.6], [-3.9, 15.6, -12.9], 'wood_dark'),
                    pbox('Goods bread', [-1, 11, -16], [4, 12.6, -13], 'straw'),
                    pbox('Goods apple A', [6.5, 11, -15], [8.5, 13, -13], 'red'),
                    pbox('Goods apple B', [9, 11, -15.6], [11, 13, -13.6], 'leaf'),
                    pbox('Goods cloth', [10.5, 11, -12.5], [14, 12, -9.5], 'cloth')]
    ball = Bone('prop_ball', (0, 16, -10))
    ball.cubes += [pbox('Ball', [-2, 14, -12], [2, 18, -8], 'red'),
                   pbox('Ball stripe', [-2.05, 15.4, -12.05], [2.05, 16.6, -7.95], 'white')]
    roots += [line, anvil, block, stall, ball]
    return roots


# ---------------------------------------------------------------- a formato de Blockbench

def export_model(roots):
    elements = []

    def node(b):
        o = {'name': b.name, 'origin': b.origin, 'color': 0, 'uuid': b.uuid, 'export': True, 'mirror_uv': False,
             'isOpen': b.name in ('npc', 'waist', 'torso', 'chest'), 'locked': False, 'visibility': True, 'autouv': 0, 'selected': False}
        if any(b.rotation):
            o['rotation'] = b.rotation
        kids = []
        for c in b.cubes:
            e = {'name': c.name, 'box_uv': False, 'rescale': False, 'locked': False, 'light_emission': 0,
                 'render_order': 'default', 'allow_mirror_modeling': True, 'from': c.frm, 'to': c.to, 'autouv': 0,
                 'color': 0, 'origin': c.origin, 'faces': c.faces, 'type': 'cube', 'uuid': c.uuid}
            if c.inflate:
                e['inflate'] = c.inflate
            if c.rotation:
                e['rotation'] = c.rotation
            elements.append(e)
            kids.append(c.uuid)
        for k in b.kids:
            kids.append(node(k))
        o['children'] = kids
        return o

    outliner = [node(r) for r in roots]
    return elements, outliner


def texture(name, img, index):
    return {'path': '', 'name': name, 'folder': '', 'namespace': '', 'id': str(index), 'group': '', 'width': 64, 'height': 64,
            'uv_width': 64, 'uv_height': 64, 'particle': False, 'use_as_default': False, 'layers_enabled': False,
            'sync_to_project': '', 'render_mode': 'default', 'render_sides': 'auto', 'pbr_channel': 'color',
            'frame_time': 1, 'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False, 'visible': True,
            'internal': True, 'saved': False, 'uuid': uid(), 'source': data_url(img)}


def all_bones(roots):
    out = []

    def walk(b):
        out.append(b)
        for k in b.kids:
            walk(k)

    for r in roots:
        walk(r)
    return out
