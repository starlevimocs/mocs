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
