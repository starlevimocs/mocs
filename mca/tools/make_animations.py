"""
Dreams: genera las animaciones de MCA (estilo Blockbench / Bedrock) en
common/src/main/resources/assets/mca/dreams_animations/<nombre>.json

Formato (lo lee net.mca.client.dreams.anim.DreamsAnimation):
{
  "length": segundos, "loop": false, "blend_in": s, "blend_out": s,
  "distance": px entre los dos (16 px = 1 bloque), "camera": "third_person" (opcional),
  "participants": {
    "player":   {"bones": {"<hueso>": {"rotation": {"t": [x,y,z]}, "position": {"t": [x,y,z]}}}},
    "villager": {...},     # en animaciones de pareja
    "self":     {...}      # en animaciones de una sola persona (saludar, hablar)
  }
}
Huesos: root, head, body, right_arm, left_arm, right_leg, left_leg.
Rotaciones en grados con los ejes del modelo de Java: x = pitch (positivo = hacia adelante/abajo),
y = yaw, z = roll. Brazo derecho: x -90 = al frente, z + = hacia afuera. Brazo izquierdo: z - = hacia afuera.
"position" de un hueso: px, x = derecha, y = arriba, z = hacia adelante.
"root": posicion en px en el espacio de la pareja (x = derecha del jugador, z = hacia el aldeano) y rotacion y
(grados). Por defecto el jugador esta en [0,0,0] mirando a 0 y el aldeano en [0,0,distance] mirando a 180.
Los valores se interpolan suave (catmull-rom); "interpolation": "linear" en el hueso para lineal.
"""
import json, math, os

OUT = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources", "assets", "mca", "dreams_animations")


def kf(points):
    return {f"{t:.3f}".rstrip("0").rstrip(".") if t else "0": [round(v, 2) for v in val] for t, val in points}


def save(name, data):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name + ".json"), "w") as f:
        json.dump(data, f, indent=1)
    print("ok", name)


def mirror(rot):  # izquierda <-> derecha
    x, y, z = rot
    return [x, -y, -z]


# ------------------------------------------------------------------ beso
# Cuentas (px): la cabeza mide 8 de profundidad, asi que a 8 px las caras se tocan. El cuerpo mide 4.
# Brazo: ~10 px desde el hombro.
D = 8
def hold(a, b, t_in, t_out, L):
    return kf([(0, [0, 0, 0]), (t_in, a), (t_out, b), (L, [0, 0, 0])])
L = 3.0
save("kiss", {
    "length": L, "blend_in": 0.35, "blend_out": 0.45, "distance": D, "camera": "third_person",
    "participants": {
        "player": {"bones": {
            # manos en la cintura del aldeano
            "right_arm": {"rotation": hold([-38, -22, 4], [-36, -22, 4], 0.6, 2.4, L)},
            "left_arm": {"rotation": hold([-38, 22, -4], [-36, 22, -4], 0.6, 2.4, L)},
            "body": {"rotation": hold([6, 0, 0], [6, 0, 0], 0.6, 2.4, L)},
            # cabeza ladeada hacia un lado, cara contra cara
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.6, [4, -10, -16]), (1.4, [6, -12, -18]), (2.4, [5, -10, -16]), (L, [0, 0, 0])]),
                     "position": kf([(0, [0, 0, 0]), (0.7, [0, 0, 0.6]), (2.3, [0, 0, 0.6]), (L, [0, 0, 0])])},
        }},
        "villager": {"bones": {
            "root": {"position": kf([(0, [0, 0, D + 1.5]), (0.6, [0, 0, D]), (2.4, [0, 0, D]), (L, [0, 0, D + 1.5])]),
                     "rotation": kf([(0, [0, 180, 0])])},
            # brazos alrededor del cuello del jugador
            "right_arm": {"rotation": hold([-112, -42, 12], [-110, -42, 12], 0.6, 2.4, L)},
            "left_arm": {"rotation": hold([-112, 42, -12], [-110, 42, -12], 0.6, 2.4, L)},
            "body": {"rotation": hold([6, 0, 0], [6, 0, 0], 0.6, 2.4, L)},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.6, [4, 10, 16]), (1.4, [6, 12, 18]), (2.4, [5, 10, 16]), (L, [0, 0, 0])]),
                     "position": kf([(0, [0, 0, 0]), (0.7, [0, 0, 0.6]), (2.3, [0, 0, 0.6]), (L, [0, 0, 0])])},
            # el pie de atras se levanta, como en las peliculas
            "left_leg": {"rotation": kf([(0, [0, 0, 0]), (0.8, [0, 0, 0]), (1.1, [38, 0, 0]), (2.1, [38, 0, 0]), (2.5, [0, 0, 0])])},
        }},
    }})

# ------------------------------------------------------------------ abrazo
# Cada uno corrido un poco a su izquierda, asi las cabezas pasan una al lado de la otra (sobre el hombro).
D = 5.5
S = 3.5
L = 3.4
sway_t = [0.7 + i * 0.3 for i in range(7)]
sway = [(t, [5, 6 * math.sin((t - 0.7) / 1.2 * math.pi * 2), 0]) for t in sway_t]
def hug_side(who):
    base_z = 0 if who == "player" else D
    x = -S if who == "player" else S
    yaw = 0 if who == "player" else 180
    return {"bones": {
        "root": {"position": kf([(0, [0, 0, base_z + (-2 if who == "player" else 2)]), (0.6, [x, 0, base_z]), (2.8, [x, 0, base_z]), (L, [0, 0, base_z + (-2 if who == "player" else 2)])]),
                 "rotation": kf([(0, [0, yaw, 0])])},
        "body": {"rotation": kf([(0, [0, 0, 0])] + sway + [(L, [0, 0, 0])])},
        # brazo derecho por debajo del brazo del otro, izquierdo por encima del hombro
        "right_arm": {"rotation": hold([-72, -58, 10], [-72, -58, 10], 0.6, 2.8, L)},
        "left_arm": {"rotation": hold([-104, 40, -14], [-104, 40, -14], 0.6, 2.8, L)},
        # cabeza apoyada de costado
        "head": {"rotation": hold([12, 28, 10], [12, 28, 10], 0.6, 2.8, L)},
    }}
save("hug", {"length": L, "blend_in": 0.45, "blend_out": 0.5, "distance": D, "camera": "third_person",
             "participants": {"player": hug_side("player"), "villager": hug_side("villager")}})

# ------------------------------------------------------------------ cachetada
D = 12
save("slap", {
    "length": 1.0, "blend_in": 0.1, "blend_out": 0.25, "distance": D,
    "participants": {
        "villager": {"bones": {
            "root": {"position": kf([(0, [0, 0, D])]), "rotation": kf([(0, [0, 180, 0])])},
            "right_arm": {"interpolation": "linear", "rotation": kf([(0, [0, 0, 0]), (0.3, [-100, 55, 20]), (0.42, [-80, -55, 10]), (0.7, [-60, -40, 5]), (1.0, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, 15, 0]), (0.42, [0, -15, 0]), (1.0, [0, 0, 0])])},
        }},
        "player": {"bones": {
            "head": {"interpolation": "linear", "rotation": kf([(0, [0, 0, 0]), (0.42, [0, 0, 0]), (0.5, [5, 45, -10]), (0.8, [0, 30, -5]), (1.0, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.45, [0, 0, 0]), (0.55, [0, 12, 0]), (1.0, [0, 0, 0])])},
        }},
    }})

# ------------------------------------------------------------------ chocar los cinco
D = 13
save("high_five", {
    "length": 1.6, "blend_in": 0.25, "blend_out": 0.3, "distance": D, "camera": "third_person",
    "participants": {
        "player": {"bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (0.5, [0, 0, 1.5]), (0.62, [0, 0, 2.2]), (1.0, [0, 0, 1]), (1.6, [0, 0, 0])])},
            "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.45, [-160, -12, 12]), (0.62, [-150, -8, 8]), (1.1, [-150, -8, 8]), (1.6, [0, 0, 0])])},
        }},
        "villager": {"bones": {
            "root": {"position": kf([(0, [0, 0, D]), (0.5, [0, 0, D - 1.5]), (0.62, [0, 0, D - 2.2]), (1.0, [0, 0, D - 1]), (1.6, [0, 0, D])]),
                     "rotation": kf([(0, [0, 180, 0])])},
            "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.45, [-160, 12, -12]), (0.62, [-150, 8, -8]), (1.1, [-150, 8, -8]), (1.6, [0, 0, 0])])},
        }},
    }})

# ------------------------------------------------------------------ baile lento (giran juntos, abrazados)
D = 6
L = 10.0
steps = [i * 0.5 for i in range(int(L / 0.5) + 1)]
def turn(t):
    # arranca y termina suave, 1 vuelta y media en total
    s = t / L
    return 540 * (s * s * (3 - 2 * s))
def pos(t, who):
    a = math.radians(turn(t))
    sgn = -1 if who == "player" else 1
    x = sgn * (D / 2) * math.sin(a)
    z = D / 2 + sgn * (D / 2) * math.cos(a)
    return [x, 0, z]
pose_p = {"right_arm": [-45, -32, 0], "left_arm": [-70, 35, -12], "body": [4, 0, 0], "head": [10, 0, -8]}
pose_v = {"left_arm": [-95, 30, -6], "right_arm": [-70, -35, 12], "body": [4, 0, 0], "head": [12, 0, 10]}
def held(pose, t_in=0.8, t_out=L - 0.8):
    return kf([(0, [0, 0, 0]), (t_in, pose), (t_out, pose), (L, [0, 0, 0])])
def sway_roll(t):
    return 5 * math.sin(t / 2.0 * math.pi * 2)
def legs(side):
    pts = [(0, [0, 0, 0])]
    for i, t in enumerate(steps[1:-1]):
        v = 14 if (i % 2 == 0) == (side == "r") else -6
        pts.append((t, [v, 0, 0]))
    pts.append((L, [0, 0, 0]))
    return kf(pts)
save("dance_slow", {
    "length": L, "blend_in": 0.8, "blend_out": 0.8, "distance": D, "camera": "third_person",
    "participants": {
        "player": {"bones": {
            "root": {"position": kf([(t, pos(t, "player")) for t in steps]), "rotation": kf([(t, [0, turn(t), 0]) for t in steps])},
            "body": {"rotation": kf([(t, [4, 0, sway_roll(t)]) for t in steps])},
            "head": {"rotation": held(pose_p["head"])},
            "right_arm": {"rotation": held(pose_p["right_arm"])},
            "left_arm": {"rotation": held(pose_p["left_arm"])},
            "right_leg": {"rotation": legs("r")}, "left_leg": {"rotation": legs("l")},
        }},
        "villager": {"bones": {
            "root": {"position": kf([(t, pos(t, "villager")) for t in steps]), "rotation": kf([(t, [0, 180 + turn(t), 0]) for t in steps])},
            "body": {"rotation": kf([(t, [4, 0, -sway_roll(t)]) for t in steps])},
            "head": {"rotation": held(pose_v["head"])},
            "right_arm": {"rotation": held(pose_v["right_arm"])},
            "left_arm": {"rotation": held(pose_v["left_arm"])},
            "right_leg": {"rotation": legs("l")}, "left_leg": {"rotation": legs("r")},
        }},
    }})

# ------------------------------------------------------------------ baile divertido (frente a frente, al ritmo)
D = 15
L = 8.0
B = 0.5  # un pulso cada medio segundo (120 bpm)
beats = [i * B for i in range(int(L / B) + 1)]
def fun(who):
    s = 1 if who == "player" else -1   # espejados
    ra, la, body, head, root_y, rl, ll, spin = [], [], [], [], [], [], [], []
    for i, t in enumerate(beats):
        phase = i % 8
        bounce = 1.6 if i % 2 == 1 else 0
        root_y.append((t, bounce))
        body.append((t, [6 if i % 2 else 0, 0, (10 if i % 4 < 2 else -10) * s]))
        head.append((t, [8 if i % 2 else -4, 0, (8 if i % 4 < 2 else -8) * s]))
        if phase < 4:   # disco: alterna apuntar arriba / abajo
            up = i % 2 == 0
            ra.append((t, [-165, 0, 25] if up else [-35, 0, 40]))
            la.append((t, [-20, 0, -15] if up else [-15, 0, -10]))
        else:           # brazos arriba moviendo de lado a lado
            ra.append((t, [-170, 0, 30 if i % 2 else 5]))
            la.append((t, [-170, 0, -30 if i % 2 else -5]))
        rl.append((t, [-18 if i % 2 else 0, 0, 0]))
        ll.append((t, [0 if i % 2 else -18, 0, 0]))
    ra[-1] = (L, [0, 0, 0]); la[-1] = (L, [0, 0, 0]); body[-1] = (L, [0, 0, 0]); head[-1] = (L, [0, 0, 0])
    ra[0] = (0, [0, 0, 0]); la[0] = (0, [0, 0, 0])
    base_z = 0 if who == "player" else D
    base_yaw = 0 if who == "player" else 180
    # vuelta completa al final (de 6 a 7 segundos)
    rot = [(0, [0, base_yaw, 0]), (6.0, [0, base_yaw, 0]), (6.5, [0, base_yaw + 180, 0]), (7.0, [0, base_yaw + 360, 0]), (L, [0, base_yaw + 360, 0])]
    pos_ = []
    for t, by in root_y:
        side = (2 if (int(t / B) // 4) % 2 == 0 else -2) * s
        pos_.append((t, [side, by, base_z]))
    pos_[0] = (0, [0, 0, base_z]); pos_[-1] = (L, [0, 0, base_z])
    return {"bones": {
        "root": {"position": kf(pos_), "rotation": kf(rot)},
        "body": {"rotation": kf(body)}, "head": {"rotation": kf(head)},
        "right_arm": {"rotation": kf(ra if s == 1 else [(t, mirror(v)) for t, v in la])},
        "left_arm": {"rotation": kf(la if s == 1 else [(t, mirror(v)) for t, v in ra])},
        "right_leg": {"rotation": kf(rl)}, "left_leg": {"rotation": kf(ll)},
    }}
save("dance_fun", {"length": L, "blend_in": 0.4, "blend_out": 0.5, "distance": D, "camera": "third_person",
                   "participants": {"player": fun("player"), "villager": fun("villager")}})

# ------------------------------------------------------------------ una sola persona
wave = [(0, [0, 0, 0]), (0.3, [-165, 0, 25])]
for i in range(4):
    wave.append((0.45 + i * 0.25, [-165, 0, 45 if i % 2 == 0 else 15]))
wave.append((1.7, [0, 0, 0]))
save("wave", {"length": 1.7, "blend_in": 0.2, "blend_out": 0.3,
              "participants": {"self": {"bones": {
                  "right_arm": {"rotation": kf(wave)},
                  "head": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-4, 0, 6]), (1.3, [-4, 0, 6]), (1.7, [0, 0, 0])])}}}}})

def talk(seed, L=2.4):
    import random
    r = random.Random(seed)
    ra, la, head = [(0, [0, 0, 0])], [(0, [0, 0, 0])], [(0, [0, 0, 0])]
    t = 0.3
    while t < L - 0.4:
        ra.append((t, [-35 - r.random() * 30, -r.random() * 25, 8 + r.random() * 12]))
        la.append((t, [-10 - r.random() * 25, r.random() * 15, -5 - r.random() * 8]))
        head.append((t, [r.uniform(-6, 6), r.uniform(-8, 8), r.uniform(-5, 5)]))
        t += 0.35 + r.random() * 0.3
    for lst in (ra, la, head):
        lst.append((L, [0, 0, 0]))
    return {"length": L, "blend_in": 0.3, "blend_out": 0.3,
            "participants": {"self": {"bones": {"right_arm": {"rotation": kf(ra)}, "left_arm": {"rotation": kf(la)},
                                                "head": {"rotation": kf(head)}}}}}
save("talk", talk(1))
save("talk2", talk(7))
save("nod", {"length": 0.9, "blend_in": 0.1, "blend_out": 0.2,
             "participants": {"self": {"bones": {"head": {"rotation": kf([(0, [0, 0, 0]), (0.2, [18, 0, 0]), (0.4, [-4, 0, 0]), (0.6, [14, 0, 0]), (0.9, [0, 0, 0])])}}}}})


# ================================================================== Dreams 2: procrear, gestos y vida diaria

# ------------------------------------------------------------------ procrear: se acercan, abrazo, beso largo y
# fundido a negro (lo dibuja DreamsPoses.renderOverlay entre 3.6 s y 7 s). Nada explicito: es "la escena se corta".
D = 5.5
L = 7.0
def tender(who):
    me = who == "player"
    base_z = 0 if me else D
    yaw = 0 if me else 180
    side = 1 if me else -1
    sway = [(0.9 + i * 0.45, [4, 4 * math.sin(i * 1.3), 2 * math.sin(i * 0.9)]) for i in range(9)]
    return {"bones": {
        "root": {"position": kf([(0, [0, 0, base_z + (-2.5 if me else 2.5)]), (0.8, [0, 0, base_z]),
                                 (6.3, [0, 0, base_z]), (L, [0, 0, base_z + (-2.5 if me else 2.5)])]),
                 "rotation": kf([(0, [0, yaw, 0])])},
        "body": {"rotation": kf([(0, [0, 0, 0])] + sway + [(L, [0, 0, 0])])},
        # primero de la cintura, despues un brazo sube a la nuca / mejilla
        "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.8, [-50, -35, 6]), (2.4, [-55, -38, 6]),
                                      (3.0, [-120, -30, 10]), (6.3, [-118, -30, 10]), (L, [0, 0, 0])])},
        "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.8, [-50, 35, -6]), (6.3, [-52, 35, -6]), (L, [0, 0, 0])])},
        # se miran, y en el beso la cabeza se ladea
        "head": {"rotation": kf([(0, [0, 0, 0]), (0.8, [8, 0, 0]), (2.4, [6, 0, 4 * side]),
                                 (3.0, [6, -12 * side, -16 * side]), (6.3, [6, -12 * side, -16 * side]), (L, [0, 0, 0])]),
                 "position": kf([(0, [0, 0, 0]), (2.8, [0, 0, 0]), (3.1, [0, 0, 0.7]), (6.3, [0, 0, 0.7]), (L, [0, 0, 0])])},
        # el pie de atras se levanta en el beso
        "left_leg": {"rotation": kf([(0, [0, 0, 0]), (3.0, [0, 0, 0]), (3.4, [0 if me else 40, 0, 0]),
                                     (6.3, [0 if me else 40, 0, 0]), (L, [0, 0, 0])])},
    }}
save("procreate", {"length": L, "blend_in": 0.5, "blend_out": 0.6, "distance": D, "camera": "third_person",
                   "participants": {"player": tender("player"), "villager": tender("villager")}})


def solo(name, L, bones, blend_in=0.25, blend_out=0.35, loop=False):
    data = {"length": L, "blend_in": blend_in, "blend_out": blend_out, "participants": {"self": {"bones": bones}}}
    if loop:
        data["loop"] = True
    save(name, data)


def held(pose, t_in, t_out, L):
    return kf([(0, [0, 0, 0]), (t_in, pose), (t_out, pose), (L, [0, 0, 0])])


# ------------------------------------------------------------------ gestos de charla
# reirse: se echa para atras, sacude los hombros, una mano a la panza
lt = [(0, [0, 0, 0])] + [(0.25 + i * 0.16, [-6 + (3 if i % 2 else -3), 0, 0]) for i in range(9)] + [(1.9, [0, 0, 0])]
solo("laugh", 1.9, {
    "head": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-22, 0, 4])] + [(0.4 + i * 0.16, [-22 + (6 if i % 2 else 0), 0, 4]) for i in range(8)] + [(1.9, [0, 0, 0])])},
    "body": {"rotation": kf(lt)},
    "right_arm": {"rotation": held([-40, 25, -12], 0.3, 1.5, 1.9)},
    "left_arm": {"rotation": held([-10, 0, -16], 0.3, 1.5, 1.9)},
})
# encogerse de hombros
solo("shrug", 1.3, {
    "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-25, 30, 35]), (0.9, [-25, 30, 35]), (1.3, [0, 0, 0])]),
                  "position": kf([(0, [0, 0, 0]), (0.3, [0, 1.2, 0]), (0.9, [0, 1.2, 0]), (1.3, [0, 0, 0])])},
    "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-25, -30, -35]), (0.9, [-25, -30, -35]), (1.3, [0, 0, 0])]),
                 "position": kf([(0, [0, 0, 0]), (0.3, [0, 1.2, 0]), (0.9, [0, 1.2, 0]), (1.3, [0, 0, 0])])},
    "head": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, 0, 12]), (0.9, [0, 0, 12]), (1.3, [0, 0, 0])])},
})
# pensar: mano al menton, mira para arriba
solo("think", 2.4, {
    "right_arm": {"rotation": held([-125, -40, 0], 0.4, 2.0, 2.4)},
    "left_arm": {"rotation": held([-35, 40, -10], 0.4, 2.0, 2.4)},
    "head": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-14, 14, 6]), (1.2, [-16, -10, 4]), (2.0, [-14, 12, 6]), (2.4, [0, 0, 0])])},
})
# vergüenza: tapa la cara con las manos y mira al costado
solo("shy", 2.0, {
    "right_arm": {"rotation": held([-140, -28, 0], 0.35, 1.6, 2.0)},
    "left_arm": {"rotation": held([-140, 28, 0], 0.35, 1.6, 2.0)},
    "head": {"rotation": held([22, -18, -6], 0.35, 1.6, 2.0)},
    "body": {"rotation": held([6, -10, 0], 0.35, 1.6, 2.0)},
    "right_leg": {"rotation": held([0, 0, -6], 0.35, 1.6, 2.0)},
})
# enojo: brazos cruzados, da vuelta la cara
solo("angry", 2.4, {
    "right_arm": {"rotation": held([-70, -45, 0], 0.3, 2.0, 2.4)},
    "left_arm": {"rotation": held([-62, 45, 0], 0.3, 2.0, 2.4)},
    "head": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-8, 25, 0]), (2.0, [-8, 30, 0]), (2.4, [0, 0, 0])])},
})
# alegria: salta con los brazos arriba
solo("cheer", 1.4, {
    "root": {"position": kf([(0, [0, 0, 0]), (0.25, [0, 0, 0]), (0.45, [0, 4, 0]), (0.65, [0, 0, 0]), (0.85, [0, 2.5, 0]), (1.05, [0, 0, 0])])},
    "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-170, 0, 25]), (0.6, [-160, 0, 35]), (0.9, [-170, 0, 20]), (1.4, [0, 0, 0])])},
    "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-170, 0, -25]), (0.6, [-160, 0, -35]), (0.9, [-170, 0, -20]), (1.4, [0, 0, 0])])},
    "head": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-14, 0, 0]), (1.0, [-10, 0, 0]), (1.4, [0, 0, 0])])},
})
# tristeza: cabeza gacha, hombros caidos, un suspiro
solo("sad", 3.0, {
    "head": {"rotation": kf([(0, [0, 0, 0]), (0.6, [32, 0, 0]), (1.6, [28, 6, 0]), (2.5, [32, -4, 0]), (3.0, [0, 0, 0])])},
    "body": {"rotation": held([10, 0, 0], 0.6, 2.5, 3.0)},
    "right_arm": {"rotation": held([-8, 0, -2], 0.6, 2.5, 3.0), "position": held([0, -0.8, 0], 0.6, 2.5, 3.0)},
    "left_arm": {"rotation": held([-8, 0, 2], 0.6, 2.5, 3.0), "position": held([0, -0.8, 0], 0.6, 2.5, 3.0)},
})
# reverencia
solo("bow", 1.6, {
    "body": {"rotation": held([30, 0, 0], 0.4, 1.1, 1.6)},
    "head": {"rotation": held([25, 0, 0], 0.4, 1.1, 1.6), "position": held([0, -1.5, -3.5], 0.4, 1.1, 1.6)},
    "right_arm": {"rotation": held([-60, -40, 0], 0.4, 1.1, 1.6), "position": held([0, -1, -2.5], 0.4, 1.1, 1.6)},
    "left_arm": {"rotation": held([30, 0, -5], 0.4, 1.1, 1.6), "position": held([0, -1, -2.5], 0.4, 1.1, 1.6)},
})
# aplaudir
cl = [(0, [0, 0, 0])]
cr = [(0, [0, 0, 0])]
for i in range(7):
    t = 0.25 + i * 0.17
    open_ = i % 2 == 0
    cr.append((t, [-70, -20 if open_ else -42, 0]))
    cl.append((t, [-70, 20 if open_ else 42, 0]))
cr.append((1.7, [0, 0, 0]))
cl.append((1.7, [0, 0, 0]))
solo("clap", 1.7, {"right_arm": {"rotation": kf(cr)}, "left_arm": {"rotation": kf(cl)},
                   "head": {"rotation": held([-6, 0, 0], 0.25, 1.4, 1.7)}})
# coqueta: se acomoda el pelo detras de la oreja y ladea la cabeza
solo("flirty", 2.0, {
    "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-150, -20, 20]), (0.9, [-140, -30, 25]), (1.3, [-20, 0, 6]), (2.0, [0, 0, 0])])},
    "head": {"rotation": held([6, -10, -14], 0.4, 1.6, 2.0)},
    "body": {"rotation": held([0, -8, 0], 0.4, 1.6, 2.0)},
})
# no: niega con la cabeza
solo("no", 1.0, {"head": {"rotation": kf([(0, [0, 0, 0]), (0.15, [0, 22, 0]), (0.35, [0, -22, 0]), (0.55, [0, 18, 0]), (0.75, [0, -12, 0]), (1.0, [0, 0, 0])])}})
# sorpresa: un pasito atras, manos arriba
solo("surprised", 1.4, {
    "root": {"position": kf([(0, [0, 0, 0]), (0.2, [0, 0, -1.5]), (1.0, [0, 0, -1.5]), (1.4, [0, 0, 0])])},
    "head": {"rotation": held([-12, 0, 0], 0.15, 1.0, 1.4)},
    "right_arm": {"rotation": held([-95, -10, 25], 0.15, 1.0, 1.4)},
    "left_arm": {"rotation": held([-95, 10, -25], 0.15, 1.0, 1.4)},
})

# ------------------------------------------------------------------ vida diaria (VillageLife los elige)
# estirarse
solo("stretch", 2.6, {
    "right_arm": {"rotation": held([-178, 0, 12], 0.7, 1.9, 2.6)},
    "left_arm": {"rotation": held([-178, 0, -12], 0.7, 1.9, 2.6)},
    "body": {"rotation": held([-6, 0, 0], 0.7, 1.9, 2.6)},
    "head": {"rotation": held([-20, 0, 0], 0.7, 1.9, 2.6)},
}, blend_in=0.5, blend_out=0.6)
# bostezar: mano a la boca
solo("yawn", 2.2, {
    "right_arm": {"rotation": held([-120, -38, 0], 0.5, 1.7, 2.2)},
    "head": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-22, 0, 0]), (1.4, [-26, 0, 4]), (1.8, [4, 0, 0]), (2.2, [0, 0, 0])])},
    "left_arm": {"rotation": held([-5, 0, -25], 0.6, 1.6, 2.2)},
})
# rascarse la cabeza
sc = [(0, [0, 0, 0]), (0.35, [-160, -40, 0])]
for i in range(5):
    sc.append((0.5 + i * 0.15, [-165 if i % 2 else -155, -40, 0]))
sc.append((1.8, [0, 0, 0]))
solo("scratch_head", 1.8, {"right_arm": {"rotation": kf(sc)}, "head": {"rotation": held([8, 0, 12], 0.35, 1.4, 1.8)}})
# mirar alrededor
solo("look_around", 3.0, {"head": {"rotation": kf([(0, [0, 0, 0]), (0.6, [0, 55, 0]), (1.2, [0, 55, 0]), (1.9, [-5, -55, 0]), (2.5, [-5, -55, 0]), (3.0, [0, 0, 0])])},
                          "body": {"rotation": kf([(0, [0, 0, 0]), (0.6, [0, 10, 0]), (1.9, [0, -10, 0]), (3.0, [0, 0, 0])])}},
     blend_in=0.4, blend_out=0.4)
# manos en la cintura
solo("hips", 3.0, {
    "right_arm": {"rotation": held([12, 0, 38], 0.4, 2.6, 3.0)},
    "left_arm": {"rotation": held([12, 0, -38], 0.4, 2.6, 3.0)},
    "head": {"rotation": held([-6, 0, 0], 0.4, 2.6, 3.0)},
})
# frotarse las manos (codicioso)
rb_r = [(0, [0, 0, 0])]
rb_l = [(0, [0, 0, 0])]
for i in range(8):
    t = 0.3 + i * 0.16
    rb_r.append((t, [-60 + (6 if i % 2 else -6), -38, 0]))
    rb_l.append((t, [-60 + (-6 if i % 2 else 6), 38, 0]))
rb_r.append((1.9, [0, 0, 0]))
rb_l.append((1.9, [0, 0, 0]))
solo("rub_hands", 1.9, {"right_arm": {"rotation": kf(rb_r)}, "left_arm": {"rotation": kf(rb_l)},
                        "head": {"rotation": held([10, 0, 0], 0.3, 1.5, 1.9)}})
# tararear y balancearse (contento)
hm = [(0, [0, 0, 0])] + [(0.4 + i * 0.45, [0, 0, 6 if i % 2 else -6]) for i in range(6)] + [(3.2, [0, 0, 0])]
solo("hum", 3.2, {"head": {"rotation": kf(hm)}, "body": {"rotation": kf([(t, [0, 0, v[2] * 0.4]) for t, v in hm])}},
     blend_in=0.4, blend_out=0.4)

# ------------------------------------------------------------------ trabajos (mientras hacen la tarea)
# talar: hachazos con los dos brazos
ch_r, ch_l, ch_b = [(0, [0, 0, 0])], [(0, [0, 0, 0])], [(0, [0, 0, 0])]
for i in range(3):
    t0 = 0.2 + i * 0.8
    ch_r += [(t0 + 0.3, [-170, -10, 0]), (t0 + 0.55, [-55, -20, 0]), (t0 + 0.75, [-60, -20, 0])]
    ch_l += [(t0 + 0.3, [-165, 15, 0]), (t0 + 0.55, [-55, 25, 0]), (t0 + 0.75, [-60, 25, 0])]
    ch_b += [(t0 + 0.3, [-6, 12, 0]), (t0 + 0.55, [12, -6, 0]), (t0 + 0.75, [10, -6, 0])]
for lst in (ch_r, ch_l, ch_b):
    lst.append((2.8, [0, 0, 0]))
solo("chop", 2.8, {"right_arm": {"interpolation": "linear", "rotation": kf(ch_r)},
                   "left_arm": {"interpolation": "linear", "rotation": kf(ch_l)},
                   "body": {"rotation": kf(ch_b)}})
# cosechar: se agacha y junta con las manos
solo("harvest", 2.6, {
    "body": {"rotation": held([38, 0, 0], 0.5, 2.1, 2.6)},
    "head": {"rotation": held([25, 0, 0], 0.5, 2.1, 2.6), "position": held([0, -2, -5], 0.5, 2.1, 2.6)},
    "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-55, 0, 0]), (0.9, [-40, -10, 0]), (1.3, [-58, 5, 0]), (1.7, [-42, -10, 0]), (2.1, [-55, 0, 0]), (2.6, [0, 0, 0])]),
                  "position": held([0, -1.5, -4.5], 0.5, 2.1, 2.6)},
    "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-50, 0, 0]), (0.9, [-60, 10, 0]), (1.3, [-42, -5, 0]), (1.7, [-58, 10, 0]), (2.1, [-50, 0, 0]), (2.6, [0, 0, 0])]),
                 "position": held([0, -1.5, -4.5], 0.5, 2.1, 2.6)},
    "right_leg": {"rotation": held([-20, 0, 0], 0.5, 2.1, 2.6)},
    "left_leg": {"rotation": held([10, 0, 0], 0.5, 2.1, 2.6)},
}, blend_in=0.45, blend_out=0.45)
# picar piedra: golpes con un brazo, mas cortos
mi = [(0, [0, 0, 0])]
for i in range(4):
    t0 = 0.15 + i * 0.6
    mi += [(t0 + 0.2, [-150, -5, 0]), (t0 + 0.4, [-45, -10, 0])]
mi.append((2.8, [0, 0, 0]))
solo("mine", 2.8, {"right_arm": {"interpolation": "linear", "rotation": kf(mi)},
                   "body": {"rotation": held([8, 0, 0], 0.3, 2.4, 2.8)},
                   "head": {"rotation": held([12, 0, 0], 0.3, 2.4, 2.8)}})
# pescar: caña al frente y de vez en cuando recoge
fi = [(0, [0, 0, 0]), (0.5, [-50, -10, 0]), (2.0, [-48, -10, 0]), (2.2, [-80, -10, 0]), (2.6, [-52, -10, 0]), (3.6, [-50, -10, 0]), (4.0, [0, 0, 0])]
solo("fish", 4.0, {"right_arm": {"rotation": kf(fi)},
                   "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-40, 30, 0]), (2.0, [-40, 30, 0]), (2.2, [-55, 35, 0]), (2.6, [-42, 30, 0]), (4.0, [0, 0, 0])])},
                   "head": {"rotation": held([14, 0, 0], 0.5, 3.6, 4.0)}}, blend_in=0.4, blend_out=0.4)
