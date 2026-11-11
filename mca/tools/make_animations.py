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


# ================================================================== Dreams 3: mas humanos
# taparse de la lluvia
solo("rain_cover", 3.2, {
    "right_arm": {"rotation": held([-165, -35, 0], 0.4, 2.8, 3.2)},
    "left_arm": {"rotation": held([-150, 30, 0], 0.5, 2.7, 3.2)},
    "head": {"rotation": kf([(0, [0, 0, 0]), (0.4, [14, 0, 0]), (1.5, [-22, 10, 0]), (2.2, [14, 0, 0]), (3.2, [0, 0, 0])])},
    "body": {"rotation": held([10, 0, 0], 0.4, 2.8, 3.2)},
}, blend_in=0.4, blend_out=0.5)
# comer algo: la mano va a la boca tres veces
ea = [(0, [0, 0, 0])]
eh = [(0, [0, 0, 0])]
for i in range(3):
    t0 = 0.3 + i * 0.75
    ea += [(t0, [-60, -20, 0]), (t0 + 0.25, [-128, -32, 0]), (t0 + 0.5, [-70, -22, 0])]
    eh += [(t0 + 0.25, [10, 0, 0]), (t0 + 0.5, [-3, 0, 0])]
ea.append((2.8, [0, 0, 0]))
eh.append((2.8, [0, 0, 0]))
solo("eat", 2.8, {"right_arm": {"rotation": kf(ea)}, "head": {"rotation": kf(eh)},
                  "left_arm": {"rotation": held([-45, 25, 0], 0.3, 2.4, 2.8)}})
# sacudirse el polvo del hombro
du = [(0, [0, 0, 0]), (0.3, [-95, 60, 0])]
for i in range(4):
    du.append((0.45 + i * 0.17, [-95 + (12 if i % 2 else -6), 60, 0]))
du.append((1.6, [0, 0, 0]))
solo("dust_off", 1.6, {"right_arm": {"rotation": kf(du)}, "head": {"rotation": held([10, 30, 0], 0.3, 1.2, 1.6)}})
# mirar el cielo con la mano de visera
solo("look_sky", 3.0, {
    "head": {"rotation": kf([(0, [0, 0, 0]), (0.6, [-38, 12, 0]), (1.6, [-40, -14, 0]), (2.4, [-36, 6, 0]), (3.0, [0, 0, 0])])},
    "right_arm": {"rotation": held([-150, -38, 0], 0.6, 2.4, 3.0)},
    "body": {"rotation": held([-6, 0, 0], 0.6, 2.4, 3.0)},
}, blend_in=0.4, blend_out=0.5)
# brazos abiertos: bienvenida
solo("welcome", 2.0, {
    "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.35, [-80, 0, 70]), (1.3, [-75, 0, 62]), (2.0, [0, 0, 0])])},
    "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.35, [-80, 0, -70]), (1.3, [-75, 0, -62]), (2.0, [0, 0, 0])])},
    "head": {"rotation": held([-10, 0, 6], 0.35, 1.4, 2.0)},
    "root": {"position": kf([(0, [0, 0, 0]), (0.3, [0, 1.8, 0]), (0.55, [0, 0, 0]), (2.0, [0, 0, 0])])},
})
# senalar
solo("point", 1.8, {
    "right_arm": {"rotation": held([-92, -12, 0], 0.3, 1.4, 1.8)},
    "head": {"rotation": held([0, -10, 0], 0.3, 1.4, 1.8)},
    "body": {"rotation": held([0, -10, 0], 0.3, 1.4, 1.8)},
})
# hablar con las palmas abiertas, contando con los dedos
t3r = [(0, [0, 0, 0]), (0.3, [-55, 10, 28]), (0.8, [-70, 20, 20]), (1.3, [-50, 5, 30]), (1.9, [-65, 15, 22]), (2.6, [0, 0, 0])]
t3l = [(0, [0, 0, 0]), (0.4, [-50, -10, -28]), (0.9, [-62, -22, -18]), (1.4, [-48, -5, -30]), (2.0, [-60, -12, -22]), (2.6, [0, 0, 0])]
solo("talk3", 2.6, {"right_arm": {"rotation": kf(t3r)}, "left_arm": {"rotation": kf(t3l)},
                    "head": {"rotation": kf([(0, [0, 0, 0]), (0.5, [4, 8, 4]), (1.2, [-4, -8, -4]), (1.9, [5, 6, 2]), (2.6, [0, 0, 0])])},
                    "body": {"rotation": kf([(0, [0, 0, 0]), (0.9, [2, 5, 0]), (1.8, [2, -5, 0]), (2.6, [0, 0, 0])])}})
# los chicos juegan: saltitos con los brazos como avion
kp_root = [(0, [0, 0, 0])]
for i in range(5):
    kp_root += [(0.2 + i * 0.5, [0, 0, 0]), (0.45 + i * 0.5, [0, 3.2, 0])]
kp_root.append((2.9, [0, 0, 0]))
solo("kid_play", 2.9, {
    "root": {"position": kf(kp_root)},
    "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, 0, 80]), (1.0, [0, 0, 100]), (1.7, [0, 0, 70]), (2.4, [0, 0, 95]), (2.9, [0, 0, 0])])},
    "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, 0, -80]), (1.0, [0, 0, -60]), (1.7, [0, 0, -100]), (2.4, [0, 0, -75]), (2.9, [0, 0, 0])])},
    "body": {"rotation": kf([(0, [0, 0, 0]), (0.8, [0, 0, 10]), (1.6, [0, 0, -10]), (2.4, [0, 0, 8]), (2.9, [0, 0, 0])])},
    "head": {"rotation": held([-12, 0, 0], 0.3, 2.5, 2.9)},
})
# ---- oficios
# martillar (herreros, albaniles)
hm_a = [(0, [0, 0, 0])]
for i in range(4):
    t0 = 0.2 + i * 0.55
    hm_a += [(t0 + 0.22, [-120, -10, 0]), (t0 + 0.36, [-48, -14, 0])]
hm_a.append((2.6, [0, 0, 0]))
solo("hammer", 2.6, {"right_arm": {"interpolation": "linear", "rotation": kf(hm_a)},
                     "left_arm": {"rotation": held([-45, 25, 0], 0.2, 2.2, 2.6)},
                     "body": {"rotation": held([14, 0, 0], 0.2, 2.2, 2.6)},
                     "head": {"rotation": held([18, 0, 0], 0.2, 2.2, 2.6)}})
# rezar (clerigos)
solo("pray", 3.4, {
    "right_arm": {"rotation": held([-78, -42, 0], 0.5, 2.9, 3.4)},
    "left_arm": {"rotation": held([-78, 42, 0], 0.5, 2.9, 3.4)},
    "head": {"rotation": held([26, 0, 0], 0.5, 2.9, 3.4)},
    "body": {"rotation": held([8, 0, 0], 0.5, 2.9, 3.4)},
}, blend_in=0.5, blend_out=0.5)
# leer (bibliotecarios, cartografos): sostiene un libro y pasa la pagina
solo("read", 3.6, {
    "left_arm": {"rotation": held([-58, 28, 0], 0.4, 3.2, 3.6)},
    "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-58, -28, 0]), (1.6, [-58, -28, 0]), (1.85, [-70, 8, 0]), (2.1, [-58, -28, 0]), (3.2, [-58, -28, 0]), (3.6, [0, 0, 0])])},
    "head": {"rotation": kf([(0, [0, 0, 0]), (0.4, [26, 8, 0]), (1.6, [26, -8, 0]), (2.2, [26, 8, 0]), (3.2, [26, -6, 0]), (3.6, [0, 0, 0])])},
}, blend_in=0.4, blend_out=0.4)
# secarse la frente (granjeros)
solo("wipe_brow", 2.0, {
    "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-150, -52, 0]), (1.0, [-150, -30, 0]), (1.4, [-150, -52, 0]), (2.0, [0, 0, 0])])},
    "head": {"rotation": held([-8, 0, 6], 0.4, 1.5, 2.0)},
    "left_arm": {"rotation": held([10, 0, -30], 0.4, 1.5, 2.0)},
})
# saludo militar (guardias)
solo("salute", 1.8, {
    "right_arm": {"rotation": held([-140, -62, 0], 0.25, 1.4, 1.8)},
    "head": {"rotation": held([-4, 0, 0], 0.25, 1.4, 1.8)},
    "body": {"rotation": held([-4, 0, 0], 0.25, 1.4, 1.8)},
})
# acariciar un animal (pastores): se agacha y mueve la mano
solo("pet", 2.6, {
    "body": {"rotation": held([34, 0, 0], 0.5, 2.1, 2.6)},
    "head": {"rotation": held([14, 0, 0], 0.5, 2.1, 2.6), "position": held([0, -1.8, -4.5], 0.5, 2.1, 2.6)},
    "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-50, 0, 0]), (0.9, [-40, -14, 0]), (1.3, [-52, 10, 0]), (1.7, [-40, -14, 0]), (2.1, [-50, 0, 0]), (2.6, [0, 0, 0])]),
                  "position": held([0, -1.4, -4.2], 0.5, 2.1, 2.6)},
    "left_arm": {"rotation": held([20, 0, -8], 0.5, 2.1, 2.6), "position": held([0, -1.4, -4.2], 0.5, 2.1, 2.6)},
}, blend_in=0.45, blend_out=0.45)


# ================================================================== Star Interactions: mas interacciones de a dos
# (las usa el fork de MCA con los aldeanos y el mod Star Interactions entre jugadores;
#  "player" = quien empieza, "villager" = la otra persona)

def pair(name, L, D, a, b, blend_in=0.35, blend_out=0.45, camera=True):
    data = {"length": L, "blend_in": blend_in, "blend_out": blend_out, "distance": D,
            "participants": {"player": {"bones": a}, "villager": {"bones": b}}}
    if camera:
        data["camera"] = "third_person"
    save(name, data)


def stay(z, yaw=0):
    return {"position": kf([(0, [0, 0, z])]), "rotation": kf([(0, [0, yaw, 0])])}


def h3(pose, t_in, t_out, L):
    return kf([(0, [0, 0, 0]), (t_in, pose), (t_out, pose), (L, [0, 0, 0])])


# ---- apreton de manos
D = 17
L = 2.4
shake = [(0, [0, 0, 0]), (0.45, [-78, -20, 0])] + [(0.6 + i * 0.16, [-78 + (9 if i % 2 else -9), -20, 0]) for i in range(7)] + [(1.9, [-78, -20, 0]), (L, [0, 0, 0])]
hs = lambda z, yaw: {"root": stay(z, yaw), "right_arm": {"rotation": kf(shake)},
                     "head": {"rotation": kf([(0, [0, 0, 0]), (0.6, [8, 0, 0]), (1.0, [0, 0, 0]), (1.4, [8, 0, 0]), (L, [0, 0, 0])])},
                     "body": {"rotation": h3([5, 0, 0], 0.45, 1.9, L)}}
pair("handshake", L, D, hs(0, 0), hs(D, 180), camera=False)

# ---- chocar los punos
D = 19
L = 1.6
bump = [(0, [0, 0, 0]), (0.3, [-40, -10, 0]), (0.5, [-92, -18, 0]), (0.62, [-80, -18, 0]), (0.9, [-150, -10, 20]), (1.2, [-140, -10, 30]), (L, [0, 0, 0])]
fb = lambda z, yaw: {"root": {"position": kf([(0, [0, 0, z]), (0.5, [0, 0, z + (1.2 if yaw == 0 else -1.2)]), (0.9, [0, 0, z]), (L, [0, 0, z])]),
                              "rotation": kf([(0, [0, yaw, 0])])},
                     "right_arm": {"interpolation": "linear", "rotation": kf(bump)},
                     "head": {"rotation": h3([-8, 0, 6], 0.9, 1.3, L)}}
pair("fist_bump", L, D, fb(0, 0), fb(D, 180), camera=False)

# ---- acariciar la cabeza
D = 11
L = 3.2
pat = [(0, [0, 0, 0]), (0.5, [-128, -18, 0])] + [(0.7 + i * 0.3, [-128 + (10 if i % 2 else -4), -18 + (8 if i % 2 else -8), 0]) for i in range(7)] + [(L, [0, 0, 0])]
bob = [(0, [0, 0, 0]), (0.5, [12, 0, 0])] + [(0.7 + i * 0.3, [12 + (5 if i % 2 else 0), 0, 4 if i % 2 else -4]) for i in range(7)] + [(L, [0, 0, 0])]
pair("pat_head", L, D,
     {"root": stay(0), "right_arm": {"rotation": kf(pat)}, "head": {"rotation": h3([12, 0, 0], 0.5, 2.8, L)},
      "body": {"rotation": h3([6, 0, 0], 0.5, 2.8, L)}},
     {"root": stay(D, 180), "head": {"rotation": kf(bob)}, "body": {"rotation": h3([4, 0, 0], 0.5, 2.8, L)},
      "right_arm": {"rotation": h3([-12, 0, 10], 0.5, 2.8, L)}, "left_arm": {"rotation": h3([-12, 0, -10], 0.5, 2.8, L)}})

# ---- beso en la frente
D = 8
L = 3.2
pair("forehead_kiss", L, D,
     {"root": {"position": kf([(0, [0, 0, -1.5]), (0.6, [0, 0, 0]), (2.6, [0, 0, 0]), (L, [0, 0, -1.5])]), "rotation": kf([(0, [0, 0, 0])])},
      "right_arm": {"rotation": h3([-92, -16, 0], 0.6, 2.6, L)}, "left_arm": {"rotation": h3([-92, 16, 0], 0.6, 2.6, L)},
      "body": {"rotation": h3([8, 0, 0], 0.6, 2.6, L)},
      "head": {"rotation": kf([(0, [0, 0, 0]), (0.6, [10, 0, 0]), (1.2, [24, 0, 0]), (2.2, [24, 0, 0]), (2.6, [8, 0, 0]), (L, [0, 0, 0])]),
               "position": kf([(0, [0, 0, 0]), (1.2, [0, 0, 0.9]), (2.2, [0, 0, 0.9]), (L, [0, 0, 0])])}},
     {"root": {"position": kf([(0, [0, 0, D + 1.5]), (0.6, [0, 0, D]), (2.6, [0, 0, D]), (L, [0, 0, D + 1.5])]), "rotation": kf([(0, [0, 180, 0])])},
      "right_arm": {"rotation": h3([-36, -22, 4], 0.6, 2.6, L)}, "left_arm": {"rotation": h3([-36, 22, -4], 0.6, 2.6, L)},
      "head": {"rotation": h3([14, 0, 0], 0.9, 2.4, L)}})

# ---- beso en la mejilla
D = 8.5
L = 2.0
pair("cheek_kiss", L, D,
     {"root": {"position": kf([(0, [0, 0, -1]), (0.5, [-1.8, 0, 0.4]), (1.3, [-1.8, 0, 0.4]), (L, [0, 0, -1])]), "rotation": kf([(0, [0, 0, 0])])},
      "head": {"rotation": h3([4, -32, -6], 0.5, 1.3, L), "position": h3([0, 0, 0.8], 0.6, 1.2, L)},
      "right_arm": {"rotation": h3([-45, -22, 0], 0.5, 1.3, L)}, "body": {"rotation": h3([5, 0, 0], 0.5, 1.3, L)}},
     {"root": stay(D, 180),
      "head": {"rotation": kf([(0, [0, 0, 0]), (0.5, [0, 18, 0]), (1.0, [-4, 18, 6]), (1.5, [6, 0, 8]), (L, [0, 0, 0])])},
      "right_arm": {"rotation": kf([(0, [0, 0, 0]), (1.3, [0, 0, 0]), (1.6, [-120, -40, 0]), (1.85, [-120, -40, 0]), (L, [0, 0, 0])])}})

# ---- tango con caida
D = 6
L = 9.0
def tango(who):
    me = who == "a"
    s = 1 if me else -1
    z0 = 0 if me else D
    yaw = 0 if me else 180
    # pasos de costado juntos, vuelta, y la caida
    posk = [(0, [0, 0, z0]), (1.0, [5, 0, z0]), (2.0, [-5, 0, z0]), (3.0, [5, 0, z0]), (4.0, [0, 0, z0]),
            (5.0, [0, 0 if me else -3.0, z0 + (1.0 if me else -1.0)]), (7.0, [0, 0 if me else -3.0, z0 + (1.0 if me else -1.0)]),
            (8.0, [0, 0, z0]), (L, [0, 0, z0])]
    legs_r = [(0, [0, 0, 0])] + [(0.5 + i * 0.5, [16 if i % 2 == 0 else -10, 0, 0]) for i in range(7)] + [(4.5, [0, 0, 0]), (L, [0, 0, 0])]
    legs_l = [(0, [0, 0, 0])] + [(0.5 + i * 0.5, [-10 if i % 2 == 0 else 16, 0, 0]) for i in range(7)] + [(4.5, [0, 0, 0]), (L, [0, 0, 0])]
    if me:
        bones = {
            "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.6, [-50, -30, 0]), (4.4, [-50, -30, 0]), (5.0, [-62, -34, 0]), (7.0, [-62, -34, 0]), (8.0, [-50, -30, 0]), (L, [0, 0, 0])])},
            "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.6, [-88, 30, -10]), (4.4, [-88, 30, -10]), (5.0, [-70, 26, 0]), (7.0, [-70, 26, 0]), (8.0, [-88, 30, -10]), (L, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.6, [4, 0, 0]), (4.4, [4, 0, 0]), (5.0, [26, 0, 0]), (7.0, [26, 0, 0]), (8.0, [4, 0, 0]), (L, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.6, [6, 0, -6]), (4.4, [6, 0, -6]), (5.0, [30, 0, 0]), (7.0, [30, 0, 0]), (8.0, [6, 0, 0]), (L, [0, 0, 0])])},
            "right_leg": {"rotation": kf(legs_r[:-2] + [(5.0, [-28, 0, 0]), (7.0, [-28, 0, 0]), (8.0, [0, 0, 0]), (L, [0, 0, 0])])},
            "left_leg": {"rotation": kf(legs_l[:-2] + [(5.0, [22, 0, 0]), (7.0, [22, 0, 0]), (8.0, [0, 0, 0]), (L, [0, 0, 0])])},
        }
    else:
        bones = {
            "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.6, [-95, 30, -6]), (4.4, [-95, 30, -6]), (5.0, [-70, 30, 0]), (7.0, [-70, 30, 0]), (8.0, [-95, 30, -6]), (L, [0, 0, 0])])},
            "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.6, [-70, -35, 12]), (4.4, [-70, -35, 12]), (5.0, [-175, 0, 30]), (6.0, [-185, 0, 34]), (7.0, [-175, 0, 30]), (8.0, [-70, -35, 12]), (L, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.6, [4, 0, 0]), (4.4, [4, 0, 0]), (5.0, [-42, 0, 0]), (7.0, [-42, 0, 0]), (8.0, [4, 0, 0]), (L, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.6, [8, 0, 8]), (4.4, [8, 0, 8]), (5.0, [-30, 0, 0]), (7.0, [-30, 0, 0]), (8.0, [8, 0, 0]), (L, [0, 0, 0])]),
                     "position": kf([(0, [0, 0, 0]), (4.4, [0, 0, 0]), (5.0, [0, -2.5, -7.5]), (7.0, [0, -2.5, -7.5]), (8.0, [0, 0, 0]), (L, [0, 0, 0])])},
            "right_leg": {"rotation": kf(legs_l[:-2] + [(5.0, [-58, 0, 0]), (7.0, [-58, 0, 0]), (8.0, [0, 0, 0]), (L, [0, 0, 0])])},
            "left_leg": {"rotation": kf(legs_r[:-2] + [(5.0, [8, 0, 0]), (7.0, [8, 0, 0]), (8.0, [0, 0, 0]), (L, [0, 0, 0])])},
        }
        # al caer hacia atras, los brazos van con el torso
        for arm in ("left_arm", "right_arm"):
            bones[arm]["position"] = kf([(0, [0, 0, 0]), (4.4, [0, 0, 0]), (5.0, [0, -2, -6.5]), (7.0, [0, -2, -6.5]), (8.0, [0, 0, 0]), (L, [0, 0, 0])])
    bones["root"] = {"position": kf(posk), "rotation": kf([(0, [0, yaw, 0])])}
    return bones
pair("dance_tango", L, D, tango("a"), tango("b"), blend_in=0.6, blend_out=0.6)

# ---- alzar y girar
D = 5.5
L = 5.2
ts = [i * 0.2 for i in range(int(L / 0.2) + 1)]
def spin_angle(t):
    s = min(1, max(0, (t - 1.4) / 2.4))
    return 360 * (s * s * (3 - 2 * s))
def lift_y(t):
    if t < 1.0:
        return 0
    if t < 1.5:
        return 11 * (t - 1.0) / 0.5
    if t < 3.9:
        return 11
    if t < 4.5:
        return 11 * (1 - (t - 3.9) / 0.6)
    return 0
def b_pos(t):
    a = math.radians(spin_angle(t))
    # x = derecha de quien empieza, z = hacia adelante; gira con quien la/lo levanta
    return [-D * math.sin(a), lift_y(t), D * math.cos(a)]
pair("lift_spin", L, D,
     {"root": {"position": kf([(0, [0, 0, 0])]), "rotation": kf([(t, [0, spin_angle(t), 0]) for t in ts])},
      "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.8, [-70, -12, 0]), (1.5, [-150, -10, 0]), (3.9, [-150, -10, 0]), (4.5, [-70, -12, 0]), (L, [0, 0, 0])])},
      "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.8, [-70, 12, 0]), (1.5, [-150, 10, 0]), (3.9, [-150, 10, 0]), (4.5, [-70, 12, 0]), (L, [0, 0, 0])])},
      "head": {"rotation": kf([(0, [0, 0, 0]), (1.5, [-28, 0, 0]), (3.9, [-28, 0, 0]), (4.6, [0, 0, 0]), (L, [0, 0, 0])])},
      "body": {"rotation": kf([(0, [0, 0, 0]), (1.5, [-8, 0, 0]), (3.9, [-8, 0, 0]), (L, [0, 0, 0])])}},
     {"root": {"position": kf([(t, b_pos(t)) for t in ts]), "rotation": kf([(t, [0, 180 + spin_angle(t), 0]) for t in ts])},
      "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.8, [-40, -25, 0]), (1.5, [-10, 0, 95]), (3.9, [-10, 0, 100]), (4.5, [-40, -25, 0]), (L, [0, 0, 0])])},
      "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.8, [-40, 25, 0]), (1.5, [-10, 0, -95]), (3.9, [-10, 0, -100]), (4.5, [-40, 25, 0]), (L, [0, 0, 0])])},
      "right_leg": {"rotation": kf([(0, [0, 0, 0]), (1.5, [35, 0, 6]), (3.9, [40, 0, 6]), (4.5, [0, 0, 0]), (L, [0, 0, 0])])},
      "left_leg": {"rotation": kf([(0, [0, 0, 0]), (1.5, [18, 0, -6]), (3.9, [24, 0, -6]), (4.5, [0, 0, 0]), (L, [0, 0, 0])])},
      "head": {"rotation": kf([(0, [0, 0, 0]), (1.5, [18, 0, 0]), (3.9, [18, 0, 0]), (4.6, [0, 0, 0]), (L, [0, 0, 0])])},
      "body": {"rotation": kf([(0, [0, 0, 0]), (1.5, [10, 0, 0]), (3.9, [10, 0, 0]), (L, [0, 0, 0])])}},
     blend_in=0.4, blend_out=0.5)

# ---- brindis
D = 16
L = 3.0
toast = [(0, [0, 0, 0]), (0.5, [-70, -10, 0]), (0.9, [-112, -24, 0]), (1.05, [-104, -24, 0]), (1.5, [-70, -30, 0]), (1.9, [-138, -44, 0]), (2.5, [-138, -44, 0]), (L, [0, 0, 0])]
tb = lambda z, yaw: {"root": stay(z, yaw), "right_arm": {"rotation": kf(toast)},
                     "head": {"rotation": kf([(0, [0, 0, 0]), (0.9, [-6, 0, 0]), (1.5, [0, 0, 0]), (1.9, [-24, 0, 0]), (2.5, [-24, 0, 0]), (L, [0, 0, 0])])}}
pair("toast", L, D, tb(0, 0), tb(D, 180), camera=False)

# ---- pose para la foto: uno al lado del otro, abrazados, con el signo de la paz
D = 12
L = 5.0
pair("photo", L, D,
     {"root": {"position": kf([(0, [0, 0, 0]), (0.8, [-4.5, 0, 0]), (4.2, [-4.5, 0, 0]), (L, [0, 0, 0])]), "rotation": kf([(0, [0, 0, 0])])},
      "right_arm": {"rotation": h3([-62, 0, 78], 0.9, 4.2, L)},
      "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.9, [-150, 0, -28]), (2.0, [-150, 0, -18]), (3.0, [-150, 0, -30]), (4.2, [-150, 0, -24]), (L, [0, 0, 0])])},
      "head": {"rotation": h3([-4, 0, -12], 0.9, 4.2, L)}, "body": {"rotation": h3([0, 0, -5], 0.9, 4.2, L)}},
     {"root": {"position": kf([(0, [0, 0, D]), (0.8, [4.5, 0, 0]), (4.2, [4.5, 0, 0]), (L, [0, 0, D])]),
               "rotation": kf([(0, [0, 180, 0]), (0.8, [0, 360, 0]), (4.2, [0, 360, 0]), (L, [0, 180, 0])])},
      "left_arm": {"rotation": h3([-62, 0, -78], 0.9, 4.2, L)},
      "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.9, [-150, 0, 28]), (2.0, [-150, 0, 18]), (3.0, [-150, 0, 30]), (4.2, [-150, 0, 24]), (L, [0, 0, 0])])},
      "head": {"rotation": h3([-4, 0, 12], 0.9, 4.2, L)}, "body": {"rotation": h3([0, 0, 5], 0.9, 4.2, L)}},
     blend_in=0.5, blend_out=0.5, camera=False)

# ---- consolar
D = 8
L = 4.4
cpat = [(0, [0, 0, 0]), (0.7, [-85, -30, 0])] + [(1.0 + i * 0.4, [-85 + (8 if i % 2 else -4), -30, 0]) for i in range(7)] + [(L, [0, 0, 0])]
sob = [(0, [0, 0, 0]), (0.7, [34, 0, 0])] + [(1.0 + i * 0.4, [34 + (4 if i % 2 else 0), 0, 0]) for i in range(7)] + [(L, [0, 0, 0])]
pair("comfort", L, D,
     {"root": {"position": kf([(0, [0, 0, -1.5]), (0.7, [-2.5, 0, 0]), (3.8, [-2.5, 0, 0]), (L, [0, 0, -1.5])]), "rotation": kf([(0, [0, 0, 0])])},
      "right_arm": {"rotation": kf(cpat)}, "left_arm": {"rotation": h3([-70, 30, 0], 0.7, 3.8, L)},
      "head": {"rotation": h3([14, 20, 6], 0.7, 3.8, L)}, "body": {"rotation": h3([6, 0, 0], 0.7, 3.8, L)}},
     {"root": {"position": kf([(0, [0, 0, D + 1]), (0.7, [2.5, 0, D - 2]), (3.8, [2.5, 0, D - 2]), (L, [0, 0, D + 1])]), "rotation": kf([(0, [0, 180, 0])])},
      "head": {"rotation": kf(sob)}, "body": {"rotation": h3([12, 0, 0], 0.7, 3.8, L)},
      "right_arm": {"rotation": h3([-60, -45, 0], 0.7, 3.8, L)}, "left_arm": {"rotation": h3([-60, 45, 0], 0.7, 3.8, L)}})

# ---- cosquillas
D = 10
L = 2.8
tk_r = [(0, [0, 0, 0]), (0.3, [-62, -14, 0])] + [(0.4 + i * 0.11, [-62 + (8 if i % 2 else -8), -14 + (6 if i % 2 else -6), 0]) for i in range(18)] + [(L, [0, 0, 0])]
tk_l = [(0, [0, 0, 0]), (0.3, [-62, 14, 0])] + [(0.4 + i * 0.11, [-62 + (-8 if i % 2 else 8), 14 + (-6 if i % 2 else 6), 0]) for i in range(18)] + [(L, [0, 0, 0])]
sq_b = [(0, [0, 0, 0])] + [(0.4 + i * 0.14, [4, 0, 13 if i % 2 else -13]) for i in range(14)] + [(L, [0, 0, 0])]
sq_y = [(0, [0, 0, D])] + [(0.4 + i * 0.14, [0, 1.5 if i % 2 else 0, D]) for i in range(14)] + [(L, [0, 0, D])]
pair("tickle", L, D,
     {"root": stay(0), "right_arm": {"rotation": kf(tk_r)}, "left_arm": {"rotation": kf(tk_l)},
      "body": {"rotation": h3([10, 0, 0], 0.3, 2.4, L)}, "head": {"rotation": h3([8, 0, 0], 0.3, 2.4, L)}},
     {"root": {"position": kf(sq_y), "rotation": kf([(0, [0, 180, 0])])},
      "body": {"rotation": kf(sq_b)},
      "head": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-22, 0, 0]), (1.4, [-26, 0, 10]), (2.3, [-22, 0, -10]), (L, [0, 0, 0])])},
      "right_arm": {"rotation": h3([-45, 35, 0], 0.35, 2.4, L)}, "left_arm": {"rotation": h3([-45, -35, 0], 0.35, 2.4, L)}},
     camera=False)

# ---- reverencia mutua
D = 20
L = 2.4
bw = lambda z, yaw: {"root": stay(z, yaw), "body": {"rotation": h3([34, 0, 0], 0.5, 1.6, L)},
                     "head": {"rotation": h3([20, 0, 0], 0.5, 1.6, L), "position": h3([0, -1.6, -3.8], 0.5, 1.6, L)},
                     "right_arm": {"rotation": h3([-55, -42, 0], 0.5, 1.6, L), "position": h3([0, -1, -2.6], 0.5, 1.6, L)},
                     "left_arm": {"rotation": h3([28, 0, -5], 0.5, 1.6, L), "position": h3([0, -1, -2.6], 0.5, 1.6, L)}}
pair("bow_each", L, D, bw(0, 0), bw(D, 180), camera=False)

# ================================================================== gestos nuevos de una persona
# taparse la cara con la mano
solo("facepalm", 2.4, {
    "right_arm": {"rotation": held([-150, -48, 0], 0.4, 1.9, 2.4)},
    "head": {"rotation": kf([(0, [0, 0, 0]), (0.4, [22, 0, 0]), (1.0, [24, 8, 0]), (1.5, [24, -8, 0]), (1.9, [22, 0, 0]), (2.4, [0, 0, 0])])},
    "body": {"rotation": held([6, 0, 0], 0.4, 1.9, 2.4)},
})
# llorar: las dos manos a la cara, hombros que tiemblan
cr = [(0, [0, 0, 0]), (0.5, [14, 0, 0])] + [(0.7 + i * 0.22, [14 + (4 if i % 2 else 0), 0, 0]) for i in range(12)] + [(3.6, [0, 0, 0])]
solo("cry", 3.6, {
    "right_arm": {"rotation": held([-142, -30, 0], 0.5, 3.1, 3.6)}, "left_arm": {"rotation": held([-142, 30, 0], 0.5, 3.1, 3.6)},
    "head": {"rotation": held([28, 0, 0], 0.5, 3.1, 3.6)}, "body": {"rotation": kf(cr)},
}, blend_in=0.4, blend_out=0.5)
# bailar solo
L = 6.0
ds_r, ds_l, ds_b, ds_h, ds_root, ds_rl, ds_ll = [], [], [], [], [], [], []
for i in range(int(L / 0.5) + 1):
    t = i * 0.5
    up = i % 2 == 0
    ds_root.append((t, [2.5 if (i // 2) % 2 == 0 else -2.5, 1.6 if not up else 0, 0]))
    ds_b.append((t, [5 if not up else 0, 0, 9 if (i // 2) % 2 == 0 else -9]))
    ds_h.append((t, [8 if not up else -4, 0, 7 if (i // 2) % 2 == 0 else -7]))
    if (i // 4) % 2 == 0:
        ds_r.append((t, [-165, 0, 22] if up else [-40, 0, 38]))
        ds_l.append((t, [-40, 0, -38] if up else [-165, 0, -22]))
    else:
        ds_r.append((t, [-90, -30 if up else 20, 0]))
        ds_l.append((t, [-90, 30 if up else -20, 0]))
    ds_rl.append((t, [-16 if up else 0, 0, 0]))
    ds_ll.append((t, [0 if up else -16, 0, 0]))
for lst in (ds_r, ds_l, ds_b, ds_h, ds_rl, ds_ll):
    lst[0] = (0, [0, 0, 0])
    lst[-1] = (L, [0, 0, 0])
ds_root[0] = (0, [0, 0, 0])
ds_root[-1] = (L, [0, 0, 0])
solo("dance_solo", L, {"root": {"position": kf(ds_root)}, "right_arm": {"rotation": kf(ds_r)}, "left_arm": {"rotation": kf(ds_l)},
                       "body": {"rotation": kf(ds_b)}, "head": {"rotation": kf(ds_h)},
                       "right_leg": {"rotation": kf(ds_rl)}, "left_leg": {"rotation": kf(ds_ll)}}, blend_in=0.4, blend_out=0.5)
# sentarse en el piso (se queda hasta que te moves)
solo("sit_ground", 4.0, {
    "root": {"position": kf([(0, [0, -10.5, 0])])},
    "right_leg": {"rotation": kf([(0, [-86, 14, 0])])}, "left_leg": {"rotation": kf([(0, [-86, -14, 0])])},
    "right_arm": {"rotation": kf([(0, [-38, 0, 0]), (2.0, [-34, 0, 4]), (4.0, [-38, 0, 0])])},
    "left_arm": {"rotation": kf([(0, [-38, 0, 0]), (2.0, [-34, 0, -4]), (4.0, [-38, 0, 0])])},
    "body": {"rotation": kf([(0, [4, 0, 0])])},
}, blend_in=0.5, blend_out=0.3, loop=True)
