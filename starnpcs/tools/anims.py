"""Las animaciones del modelo humano. Valores como en Blockbench: brazo x negativo = hacia adelante, antebrazo x
negativo = dobla el codo, pierna x negativa = adelante, espinilla x positiva = dobla la rodilla, posicion z negativa = adelante."""
import math

import numpy as np

from kin import Anim, bisect, point
from rig import all_bones, pbox, shift

KID_SCALE = 0.65
KID_GAP = 2.5        # bloques entre los dos chicos
DUEL_GAP = 2.0       # bloques entre los dos guardias


def mirrored(a, name):
    """La misma animacion para el otro lado del cuerpo."""
    m = Anim(name, a.length, a.loop)
    for (bone, chan), tr in a.tracks.items():
        if bone.startswith('right_'):
            bone = 'left_' + bone[6:]
        elif bone.startswith('left_'):
            bone = 'right_' + bone[5:]
        out = []
        for t, v, mode in tr:
            if chan == 'rotation':
                v = [v[0], -v[1], -v[2]]
            elif chan == 'position':
                v = [-v[0], v[1], v[2]]
            out.append((t, v, mode))
        m.tracks[(bone, chan)] = out
    m.fx = list(a.fx)
    return m


def make(roots):
    B = {b.name: b for b in all_bones(roots)}
    hx = B['item_right'].origin[0]
    T = {}

    def add(a):
        a.close()
        T[a.name] = a
        return a

    # ------------------------------------------------------------ basicas
    a = Anim('idle', 6)
    a.r('chest', (0, 1, 0, 0), (3, -1.5, 0, 0))
    a.r('head', (0, 0, 0, 0), (1.5, 2, 7, 0), (3, -1, 0, 0), (4.5, 1, -8, 1))
    a.r('right_arm', (0, 0, 0, 3), (3, 2, 0, 5))
    a.r('left_arm', (0, 2, 0, -5), (3, 0, 0, -3))
    a.r('right_forearm', (0, -8, 0, 0), (3, -13, 0, 0))
    a.r('left_forearm', (0, -13, 0, 0), (3, -8, 0, 0))
    a.p('waist', (0, 0, 0, 0), (3, 0, -0.25, 0))
    a.r('right_leg', (0, 0, 0, 2))
    a.r('left_leg', (0, 0, 0, -2))
    add(a)

    a = Anim('walk', 1)
    a.r('right_leg', (0, -35, 0, 0), (0.25, 0, 0, 0), (0.5, 35, 0, 0), (0.75, 0, 0, 0))
    a.r('left_leg', (0, 35, 0, 0), (0.25, 0, 0, 0), (0.5, -35, 0, 0), (0.75, 0, 0, 0))
    a.r('right_shin', (0, 5, 0, 0), (0.25, 10, 0, 0), (0.5, 22, 0, 0), (0.75, 58, 0, 0))
    a.r('left_shin', (0, 22, 0, 0), (0.25, 58, 0, 0), (0.5, 5, 0, 0), (0.75, 10, 0, 0))
    a.r('right_arm', (0, 28, 0, 3), (0.5, -28, 0, 3))
    a.r('left_arm', (0, -28, 0, -3), (0.5, 28, 0, -3))
    a.r('right_forearm', (0, -12, 0, 0), (0.5, -36, 0, 0))
    a.r('left_forearm', (0, -36, 0, 0), (0.5, -12, 0, 0))
    a.p('waist', (0, 0, -0.6, 0), (0.25, 0, 0.3, 0), (0.5, 0, -0.6, 0), (0.75, 0, 0.3, 0))
    a.r('torso', (0, 3, 5, 0), (0.5, 3, -5, 0))
    a.r('chest', (0, 2, -9, 0), (0.5, 2, 9, 0))
    a.r('head', (0, -3, 4, 0), (0.5, -3, -4, 0))
    add(a)

    a = Anim('talk', 4)
    a.r('right_arm', (0, -30, 0, 8), (1, -55, -10, 12), (2, -40, 5, 8), (3, -60, -5, 14))
    a.r('right_forearm', (0, -70, 0, 0), (1, -95, 0, 0), (2, -60, 0, 0), (3, -85, 0, 0))
    a.r('left_arm', (0, -5, 0, -6), (2, -18, 0, -10))
    a.r('left_forearm', (0, -20, 0, 0), (2, -45, 0, 0))
    a.r('head', (0, 2, 0, 0), (1, -4, 6, 2), (2, 4, -4, 0), (3, -3, 3, -2))
    a.r('chest', (0, 2, 0, 0), (2, -2, 3, 0))
    a.r('torso', (0, 0, 0, 0), (2, 2, -2, 0))
    add(a)

    a = Anim('wave', 1.6, 'once')
    a.r('right_arm', (0, 0, 0, 5), (0.3, -10, 0, 150), (1.3, -10, 0, 150), (1.6, 0, 0, 5))
    a.r('right_forearm', (0, -8, 0, 0), (0.3, 0, 0, -22), (0.5, 0, 0, 26), (0.7, 0, 0, -22), (0.9, 0, 0, 26), (1.1, 0, 0, -22), (1.3, 0, 0, 0),
        (1.6, -8, 0, 0))
    a.r('head', (0, 0, 0, 0), (0.4, -6, 0, -6), (1.3, -6, 0, -6), (1.6, 0, 0, 0))
    a.r('chest', (0, 0, 0, 0), (0.4, 0, 0, -4), (1.3, 0, 0, -4), (1.6, 0, 0, 0))
    a.r('left_arm', (0, 0, 0, -4))
    add(a)

    a = Anim('nod', 1.0, 'once')
    a.r('head', (0, 0, 0, 0), (0.2, 16, 0, 0), (0.4, -2, 0, 0), (0.6, 11, 0, 0), (0.85, 0, 0, 0))
    a.r('chest', (0, 0, 0, 0), (0.25, 4, 0, 0), (0.85, 0, 0, 0))
    a.r('right_arm', (0, 0, 0, 4))
    a.r('left_arm', (0, 0, 0, -4))
    add(a)

    a = Anim('bow', 1.8, 'once')
    a.r('torso', (0, 0, 0, 0), (0.5, 28, 0, 0), (1.2, 28, 0, 0), (1.8, 0, 0, 0))
    a.r('chest', (0, 0, 0, 0), (0.5, 18, 0, 0), (1.2, 18, 0, 0), (1.8, 0, 0, 0))
    a.r('head', (0, 0, 0, 0), (0.5, 12, 0, 0), (1.2, 12, 0, 0), (1.8, 0, 0, 0))
    a.r('right_arm', (0, 0, 0, 4), (0.5, -42, -35, 0), (1.2, -42, -35, 0), (1.8, 0, 0, 4))
    a.r('right_forearm', (0, -8, 0, 0), (0.5, -105, 0, 0), (1.2, -105, 0, 0), (1.8, -8, 0, 0))
    a.r('left_arm', (0, 0, 0, -4), (0.5, 25, 0, -8), (1.2, 25, 0, -8), (1.8, 0, 0, -4))
    a.p('waist', (0, 0, 0, 0), (0.5, 0, -0.5, 1.5), (1.2, 0, -0.5, 1.5), (1.8, 0, 0, 0))
    add(a)

    # ------------------------------------------------------------ sentados
    a = Anim('sit_ground', 6)
    a.p('waist', (0, 0, -10, 0), (3, 0, -10.2, 0))
    a.r('right_leg', (0, -86, -10, 0))
    a.r('left_leg', (0, -86, 10, 0))
    a.r('right_shin', (0, 12, 0, 0))
    a.r('left_shin', (0, 6, 0, 0))
    a.r('torso', (0, -12, 0, 0), (3, -9, 0, 0))
    a.r('chest', (0, 4, 0, 0))
    a.r('right_arm', (0, 38, 0, 10))
    a.r('left_arm', (0, 38, 0, -10))
    a.r('right_forearm', (0, -6, 0, 0))
    a.r('left_forearm', (0, -6, 0, 0))
    a.r('head', (0, -4, 0, 0), (1.5, -6, 12, 0), (3, -2, 0, 0), (4.5, -6, -14, 0))
    add(a)

    a = Anim('sit_chat_a', 6)
    a.p('waist', (0, 0, -10, -2))
    a.r('right_leg', (0, -88, -6, 0))
    a.r('left_leg', (0, -88, 6, 0))
    a.r('right_shin', (0, 80, 0, 0), (1.5, 95, 0, 0), (3, 80, 0, 0), (4.5, 70, 0, 0))
    a.r('left_shin', (0, 92, 0, 0), (1.5, 78, 0, 0), (3, 92, 0, 0), (4.5, 84, 0, 0))
    a.r('torso', (0, 5, 0, 0), (3, 8, 0, 0))
    a.r('chest', (0, 0, 12, 0))
    a.r('right_arm', (0, -40, -5, 6), (0.8, -58, -12, 10), (1.6, -42, 4, 6), (2.4, -60, -8, 10), (3.2, -38, 0, 2), (5.4, -38, 0, 2), (6, -40, -5, 6))
    a.r('right_forearm', (0, -70, 0, 0), (0.8, -95, 0, 0), (1.6, -65, 0, 0), (2.4, -90, 0, 0), (3.2, -45, 0, 0), (5.4, -45, 0, 0), (6, -70, 0, 0))
    a.r('left_arm', (0, -38, 0, -2))
    a.r('left_forearm', (0, -45, 0, 0))
    a.r('head', (0, 0, 22, 0), (0.8, -4, 26, 2), (1.6, 2, 20, 0), (2.4, -3, 25, -2), (3.2, 0, 22, 0), (3.8, 10, 22, 0), (4.3, 0, 22, 0),
        (4.9, 9, 22, 0), (5.4, 0, 22, 0), (6, 0, 22, 0))
    add(a)
    add(mirrored(a, 'tmp').shifted('sit_chat_b', 3))

    # ------------------------------------------------------------ guardia
    a = Anim('guard', 8)
    a.r('right_arm', (0, -12, 0, 6), (4, -9, 0, 7))
    a.r('right_forearm', (0, -18, 0, 0), (4, -22, 0, 0))
    a.r('left_arm', (0, 12, 0, -24))
    a.r('left_forearm', (0, -58, 0, 0))
    a.r('right_leg', (0, 0, 0, 4))
    a.r('left_leg', (0, 0, 0, -4))
    a.r('head', (0, 0, 0, 0), (1.5, 0, 28, 0), (3, 0, 28, 0), (4, 2, 0, 0), (5.5, 0, -30, 0), (7, 0, -30, 0))
    a.r('chest', (0, -2, 0, 0), (4, 0, 0, 0))
    a.r('torso', (0, 0, 0, 0), (2, 0, 6, 0), (6, 0, -6, 0))
    a.show('prop_sword')
    add(a)

    # ------------------------------------------------------------ herrero
    head_pt = (hx, 10.5, -11.25)

    def smith_pose(fore):
        s = Anim('s', 1)
        s.r('torso', (0, 14, 0, 0)).r('right_arm', (0, -18, 0, 4)).r('right_forearm', (0, fore, 0, 0)).p('waist', (0, 0, -0.5, 0))
        return s

    hit = bisect(lambda f: point(roots, smith_pose(f), 0, 'prop_hammer', head_pt)[1] - 12.4, -85, -5)
    spot = point(roots, smith_pose(hit), 0, 'prop_hammer', head_pt)
    shift(B['prop_anvil'], spot[0] - 2, 0, spot[2] + 13)

    a = Anim('blacksmith', 3.2)
    arm, fore, tor = [], [], []
    for t0 in (0.0, 0.8, 1.6):
        arm += [(t0 + 0.25, -78, 0, 8), (t0 + 0.6, -18, 0, 4), (t0 + 0.72, -30, 0, 4)]
        fore += [(t0 + 0.25, -100, 0, 0), (t0 + 0.6, hit, 0, 0), (t0 + 0.72, hit - 14, 0, 0)]
        tor += [(t0 + 0.25, 2, 0, 0), (t0 + 0.6, 14, 0, 0)]
        a.sound(t0 + 0.6, 'minecraft:block.anvil.place|0.3|1.7')
        a.particle(t0 + 0.6, 'minecraft:crit|4|%.1f|13|%.1f' % (spot[0], spot[2]))
    a.r('right_arm', (0, -32, 0, 5), *arm, (2.75, -32, 0, 5))
    a.r('right_forearm', (0, -62, 0, 0), *fore, (2.75, -62, 0, 0))
    a.r('torso', (0, 9, 0, 0), *tor, (2.75, 9, 0, 0))
    a.r('chest', (0, 4, 0, 0))
    a.r('head', (0, 14, 0, 0), (2.5, 14, 0, 0), (2.9, 20, 6, 0))
    a.r('left_arm', (0, -48, 0, -4))
    a.r('left_forearm', (0, -28, 0, 0))
    a.p('waist', (0, 0, -0.5, 0))
    a.r('right_leg', (0, 6, 0, 3))
    a.r('right_shin', (0, 6, 0, 0))
    a.r('left_leg', (0, -8, 0, -3))
    a.r('left_shin', (0, 12, 0, 0))
    a.show('prop_hammer', 'prop_anvil')
    add(a)

    a = Anim('blacksmith_talk', 4)
    a.r('right_arm', (0, -15, 0, 6), (2, -18, 0, 7))
    a.r('right_forearm', (0, -66, 0, 0), (2, -60, 0, 0))
    a.r('left_arm', (0, 12, 0, -24))
    a.r('left_forearm', (0, -58, 0, 0))
    a.r('head', (0, 0, 0, 0), (1, -3, 5, 0), (2, 3, 0, 0), (3, -2, -4, 0))
    a.r('chest', (0, 0, 0, 0), (2, -2, 0, 0))
    a.show('prop_hammer', 'prop_anvil')
    add(a)

    # ------------------------------------------------------------ pescador
    def fish_base(x):
        x.p('npc', (0, 0, 0, 0), (x.length * 0.25, 0, 0.4, 0), (x.length * 0.5, 0, 0, 0), (x.length * 0.75, 0, -0.4, 0))
        x.r('npc', (0, 0, 0, -1.5), (x.length * 0.5, 0, 0, 1.5))
        x.p('waist', (0, 0, -8.5, 0))
        x.r('right_leg', (0, -82, -8, 0))
        x.r('left_leg', (0, -82, 8, 0))
        x.r('right_shin', (0, 72, 0, 0))
        x.r('left_shin', (0, 72, 0, 0))
        x.show('prop_boat', 'prop_rod', 'prop_line')

    tip = (hx, 12.5, -30)
    a = Anim('fishing', 8)
    fish_base(a)
    a.r('torso', (0, 6, 0, 0), (4, 8, 0, 0))
    a.r('chest', (0, 2, 0, 0))
    a.r('right_arm', (0, -35, 0, 4), (5.3, -35, 0, 4), (5.6, -50, 0, 4), (6.2, -42, 0, 4), (6.8, -35, 0, 4))
    a.r('right_forearm', (0, -35, 0, 0), (2, -33, 0, 0), (4, -36, 0, 0), (5.3, -35, 0, 0), (5.6, -62, 0, 0), (6.2, -48, 0, 0), (6.8, -35, 0, 0))
    a.r('left_arm', (0, -42, 0, -3))
    a.r('left_forearm', (0, -38, 0, 0))
    a.r('head', (0, 8, -6, 0), (2, 6, -10, 0), (3.5, 0, 25, 0), (4.5, 2, 20, 0), (5.3, 8, -6, 0), (5.6, 2, -8, 0), (6.8, 8, -6, 0))
    a.close()

    # la tanza: va del agua a la punta de la cana, y la sigue cuadro a cuadro
    t0 = point(roots, a, 0, 'prop_rod', tip)
    bob = np.array([t0[0], -1.0, t0[2] - 4.0])
    length = float(np.linalg.norm(t0 - bob))
    line = B['prop_line']
    line.origin = [float(bob[0]), float(bob[1]), float(bob[2])]
    line.cubes = [pbox('Line', [bob[0] - 0.12, bob[1], bob[2] - 0.12], [bob[0] + 0.12, bob[1] + length, bob[2] + 0.12], 'white'),
                  pbox('Bobber top', [bob[0] - 0.8, bob[1] - 0.2, bob[2] - 0.8], [bob[0] + 0.8, bob[1] + 1, bob[2] + 0.8], 'red'),
                  pbox('Bobber', [bob[0] - 0.8, bob[1] - 1.4, bob[2] - 0.8], [bob[0] + 0.8, bob[1] - 0.2, bob[2] + 0.8], 'white')]
    for c in line.cubes:
        c.frm, c.to = [float(v) for v in c.frm], [float(v) for v in c.to]

    def aim(x, steps):
        rot, scl = [], []
        for i in range(steps + 1):
            t = x.length * i / steps
            v = point(roots, x, t % x.length, 'prop_rod', tip) - bob
            n = float(np.linalg.norm(v))
            alpha = math.asin(max(-1, min(1, v[2] / n)))
            beta = math.atan2(-v[0], v[1])
            rot.append((t, -math.degrees(alpha), 0, math.degrees(beta)))
            scl.append((t, 1, n / length, 1))
        rot[-1] = (x.length,) + rot[0][1:]
        scl[-1] = (x.length,) + scl[0][1:]
        x.tracks.pop(('prop_line', 'scale'), None)
        x.r('prop_line', *rot, mode='linear')
        x.s('prop_line', *scl)

    aim(a, 64)
    a.sound(5.5, 'minecraft:entity.fishing_bobber.splash|0.3|1.3')
    a.particle(5.5, 'minecraft:splash|8|%.1f|0|%.1f' % (bob[0], bob[2]))
    add(a)

    a = Anim('fishing_talk', 4)
    fish_base(a)
    a.r('torso', (0, 4, 0, 0))
    a.r('right_arm', (0, -35, 0, 4))
    a.r('right_forearm', (0, -35, 0, 0), (2, -33, 0, 0))
    a.r('left_arm', (0, -40, 0, -8), (1, -55, 0, -14), (2, -40, 0, -8), (3, -50, 0, -12))
    a.r('left_forearm', (0, -50, 0, 0), (1, -85, 0, 0), (2, -55, 0, 0), (3, -80, 0, 0))
    a.r('head', (0, 0, 0, 0), (1, -3, 5, 0), (2, 3, 0, 0), (3, -2, -4, 0))
    a.close()
    aim(a, 16)
    add(a)

    # ------------------------------------------------------------ barrer
    bristle = (hx, -12, -2.5)

    def sweep_pose(yaw, fore, k):
        s = Anim('s', 1)
        s.r('torso', (0, 14, 0, 0)).r('chest', (0, 8, -10 + 20 * k, 0)).r('right_arm', (0, -50, yaw, 0)).r('right_forearm', (0, fore, 0, 0))
        s.p('waist', (0, 0.6 - 1.2 * k, -0.6, 0))
        return s

    a = Anim('sweep', 2)
    arm, fore = [], []
    for i in range(8):
        t = i * 0.25
        k = 0.5 - 0.5 * math.cos(math.pi * t)
        yaw = -30 + 62 * k
        f = bisect(lambda v: point(roots, sweep_pose(yaw, v, k), 0, 'prop_broom', bristle)[1] - 0.4, -70, 30)
        arm.append((t, -50, yaw, 0))
        fore.append((t, f, 0, 0))
    a.r('right_arm', *arm)
    a.r('right_forearm', *fore)
    a.r('torso', (0, 14, 0, 0))
    a.r('chest', (0, 8, -10, 0), (1, 8, 10, 0))
    a.r('left_arm', (0, -72, 35, 0), (1, -66, 48, 0))
    a.r('left_forearm', (0, -70, 0, 0), (1, -60, 0, 0))
    a.r('head', (0, 18, -8, 0), (1, 18, 8, 0))
    a.p('waist', (0, 0.6, -0.6, 0), (1, -0.6, -0.6, 0))
    a.r('right_leg', (0, -8, 0, 3))
    a.r('right_shin', (0, 12, 0, 0))
    a.r('left_leg', (0, 6, 0, -3))
    a.r('left_shin', (0, 8, 0, 0))
    a.sound(0.45, 'minecraft:item.brush.brushing.generic|0.5|0.7')
    a.sound(1.45, 'minecraft:item.brush.brushing.generic|0.5|0.8')
    a.show('prop_broom')
    add(a)

    # ------------------------------------------------------------ talar
    edge = (hx, 7.5, -11.5)

    def chop_pose(fore):
        s = Anim('s', 1)
        s.r('torso', (0, 22, 0, 0)).r('chest', (0, 10, 0, 0)).r('right_arm', (0, -52, 0, 0)).r('right_forearm', (0, fore, 0, 0))
        s.p('waist', (0, 0, -1.5, -1))
        return s

    cut = bisect(lambda f: point(roots, chop_pose(f), 0, 'prop_axe', edge)[1] - 15.6, -110, 0)
    spot = point(roots, chop_pose(cut), 0, 'prop_axe', edge)
    shift(B['prop_log'], spot[0], 0, spot[2] + 15)

    a = Anim('chop', 2.6)
    a.r('right_arm', (0, -60, 0, 0), (0.6, -165, 0, 0), (0.8, -160, 0, 0), (1.0, -52, 0, 0), (1.15, -56, 0, 0), (1.8, -60, 0, 0))
    a.r('right_forearm', (0, -30, 0, 0), (0.6, -55, 0, 0), (0.8, -60, 0, 0), (1.0, cut, 0, 0), (1.15, cut - 5, 0, 0), (1.8, -30, 0, 0))
    a.r('left_arm', (0, -62, 30, 0), (0.6, -160, 20, 0), (0.8, -156, 20, 0), (1.0, -58, 30, 0), (1.15, -62, 30, 0), (1.8, -62, 30, 0))
    a.r('left_forearm', (0, -40, 0, 0), (0.6, -60, 0, 0), (1.0, -30, 0, 0), (1.8, -40, 0, 0))
    a.r('torso', (0, 6, 0, 0), (0.6, -12, 0, 0), (0.8, -12, 0, 0), (1.0, 22, 0, 0), (1.8, 8, 0, 0))
    a.r('chest', (0, 2, 0, 0), (0.6, -6, 0, 0), (1.0, 10, 0, 0), (1.8, 2, 0, 0))
    a.r('head', (0, 10, 0, 0), (0.6, -8, 0, 0), (1.0, 16, 0, 0), (1.8, 10, 0, 0))
    a.p('waist', (0, 0, -0.5, 0), (0.6, 0, 0.3, 0.5), (1.0, 0, -1.5, -1), (1.8, 0, -0.5, 0))
    a.r('right_leg', (0, -10, 0, 4))
    a.r('right_shin', (0, 12, 0, 0))
    a.r('left_leg', (0, 8, 0, -4))
    a.r('left_shin', (0, 6, 0, 0))
    a.sound(1.0, 'minecraft:block.wood.break|0.7|0.9')
    a.particle(1.0, 'minecraft:crit|3|%.1f|16|%.1f' % (spot[0], spot[2]))
    a.show('prop_axe', 'prop_log')
    add(a)

    # ------------------------------------------------------------ mercader
    a = Anim('merchant', 6)
    a.r('right_arm', (0, -55, -25, 8), (1.5, -60, 20, 8), (3, -50, -10, 10), (3.5, -70, 0, 10), (4, -55, 0, 10), (4.5, -70, 0, 10), (5, -55, -10, 10))
    a.r('right_forearm', (0, -25, 0, 0), (1.5, -30, 0, 0), (3, -60, 0, 0), (3.5, -100, 0, 0), (4, -60, 0, 0), (4.5, -100, 0, 0), (5, -50, 0, 0))
    a.r('left_arm', (0, -42, 0, -6))
    a.r('left_forearm', (0, -40, 0, 0))
    a.r('head', (0, 4, -10, 0), (1.5, 4, 10, 0), (3, -4, 0, 0), (4.5, -6, 0, 3), (5.2, 0, -5, 0))
    a.r('chest', (0, 2, -6, 0), (1.5, 2, 6, 0), (3, 0, 0, 0))
    a.r('torso', (0, 3, 0, 0))
    a.show('prop_stall')
    add(a)

    a = Anim('merchant_talk', 4)
    a.r('right_arm', (0, -38, 0, 24), (2, -44, 0, 28))
    a.r('right_forearm', (0, -55, 0, 0), (2, -48, 0, 0))
    a.r('left_arm', (0, -38, 0, -24), (2, -44, 0, -28))
    a.r('left_forearm', (0, -55, 0, 0), (2, -48, 0, 0))
    a.r('head', (0, 0, 0, 0), (1, 8, 0, 0), (2, 0, 0, 0), (3, 6, 0, 0))
    a.r('chest', (0, 0, 0, 0), (2, -2, 0, 0))
    a.show('prop_stall')
    add(a)

    # ------------------------------------------------------------ chicos jugando a la pelota
    far = -(KID_GAP * 16 / (KID_SCALE * 0.9375) - 20)
    ball = [(0, 0, 0, 0), (0.3, 0, -2, 1), (0.5, 0, 3, -3)]
    for i in range(1, 7):
        u = i / 7
        ball.append((0.5 + 0.9 * u, 0, 3 - 3 * u + 64 * u * (1 - u), -3 + (far + 3) * u))
    ball += [(1.4, 0, 0, far), (2.2, 0, -2, far - 1), (2.4, 0, 3, far + 3)]
    for i in range(1, 7):
        u = i / 7
        ball.append((2.4 + 0.9 * u, 0, 3 - 3 * u + 64 * u * (1 - u), far + 3 - (far + 3) * u))
    ball += [(3.3, 0, 0, 0)]

    def kid(name, t0, with_ball):
        """t0 = segundo en que lanza; recibe 1.1 s antes de volver a lanzar (el ciclo dura 4)."""
        k = Anim(name, 4)
        w = lambda t: (t0 - 0.5 + t) % 4
        seq = sorted([
            (w(0.0), -48, -62, 0, 0), (w(0.3), -25, -78, -6, 0), (w(0.5), -115, -20, 12, 0), (w(0.8), -60, -30, 0, 0),
            (w(1.4), -12, -12, 0, 0), (w(1.6), -165, -10, 0, 0), (w(1.75), -165, -10, 0, 3), (w(1.95), -165, -10, 0, 0),
            (w(2.1), -160, -12, 0, 2.2), (w(2.3), -20, -12, 0, 0), (w(2.9), -76, -30, 0, 0), (w(3.3), -48, -62, 0, 0),
        ])
        if seq[0][0] > 0:
            seq.insert(0, (0.0,) + seq[-1][1:])
        k.r('right_arm', *[(t, ax, -16, 6) for t, ax, _, _, _ in seq])
        k.r('left_arm', *[(t, ax, 16, -6) for t, ax, _, _, _ in seq])
        k.r('right_forearm', *[(t, fx, 0, 0) for t, _, fx, _, _ in seq])
        k.r('left_forearm', *[(t, fx, 0, 0) for t, _, fx, _, _ in seq])
        k.r('torso', *[(t, tx, 0, 0) for t, _, _, tx, _ in seq])
        k.p('waist', *[(t, 0, hop, 0) for t, _, _, _, hop in seq])
        k.r('right_shin', (0, 8, 0, 0))
        k.r('left_shin', (0, 8, 0, 0))
        k.r('right_leg', (0, -4, 0, 3))
        k.r('left_leg', (0, -4, 0, -3))
        k.r('head', (0, -6, 0, 0), (2, -10, 0, 0))
        if with_ball:
            k.p('prop_ball', *ball, mode='linear')
            k.show('prop_ball')
        return k

    add(kid('kid_play_a', 0.5, True))
    add(kid('kid_play_b', 2.4, False))

    # ------------------------------------------------------------ duelo de guardias
    a = Anim('duel_a', 4)
    a.r('right_arm', (0, -30, 0, 6), (0.35, -165, 0, 10), (0.7, -78, 0, 4), (0.85, -70, 0, 4), (1.3, -30, 0, 6), (2.3, -30, 0, 6),
        (2.6, -125, -20, 10), (2.75, -118, -20, 10), (3.1, -110, -20, 10), (3.5, -30, 0, 6))
    a.r('right_forearm', (0, -60, 0, 0), (0.35, -45, 0, 0), (0.7, -8, 0, 0), (0.85, -20, 0, 0), (1.3, -60, 0, 0), (2.3, -60, 0, 0),
        (2.6, -55, 0, 0), (2.75, -62, 0, 0), (3.1, -55, 0, 0), (3.5, -60, 0, 0))
    a.r('left_arm', (0, 20, 0, -18), (0.35, 35, 0, -25), (0.7, -10, 0, -30), (1.3, 20, 0, -18), (2.3, 20, 0, -18), (2.6, 30, 0, -28), (3.5, 20, 0, -18))
    a.r('left_forearm', (0, -30, 0, 0))
    a.p('waist', (0, 0, -1.2, 0), (0.35, 0, -0.8, 1.5), (0.7, 0, -2, -4), (1.3, 0, -1.2, 0), (2.3, 0, -1.2, 0), (2.75, 0, -1.6, 2), (3.3, 0, -1.2, 0.5))
    a.r('torso', (0, 4, 0, 0), (0.35, -8, 0, 0), (0.7, 18, 0, 0), (1.3, 4, 0, 0), (2.3, 4, 0, 0), (2.6, -4, 0, 0), (2.75, -9, 0, 0), (3.4, 4, 0, 0))
    a.r('chest', (0, 0, -10, 0), (0.35, 0, -25, 0), (0.7, 0, 15, 0), (1.3, 0, -10, 0))
    a.r('head', (0, 0, 8, 0), (0.35, -6, 16, 0), (0.7, 6, -10, 0), (1.3, 0, 8, 0), (2.3, 0, 8, 0), (2.7, -5, 8, 0), (3.4, 0, 8, 0))
    a.r('right_leg', (0, -18, 0, 4), (0.35, -12, 0, 4), (0.7, -38, 0, 4), (1.3, -18, 0, 4), (2.3, -18, 0, 4), (2.75, -10, 0, 4), (3.3, -18, 0, 4))
    a.r('right_shin', (0, 18, 0, 0), (0.7, 30, 0, 0), (1.3, 18, 0, 0))
    a.r('left_leg', (0, 16, 0, -4), (0.7, 30, 0, -4), (1.3, 16, 0, -4), (2.3, 16, 0, -4), (2.75, 22, 0, -4), (3.3, 16, 0, -4))
    a.r('left_shin', (0, 10, 0, 0), (0.7, 4, 0, 0), (1.3, 10, 0, 0))
    a.sound(0.5, 'minecraft:entity.player.attack.sweep|0.5|1.1')
    a.sound(0.72, 'minecraft:item.trident.hit_ground|0.6|1.4')
    a.show('prop_sword')
    add(a)
    add(a.shifted('duel_b', 2))
    return T
