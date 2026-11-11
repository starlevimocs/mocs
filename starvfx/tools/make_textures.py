"""Texturas de Star VFX (todas hechas por codigo). Las de brillo van sobre negro: se dibujan sumando luz."""
import os
import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'starvfx', 'textures', 'fx')
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(7)


def save(name, rgba):
    a = np.clip(rgba, 0, 1)
    Image.fromarray((a * 255 + 0.5).astype(np.uint8), 'RGBA').save(os.path.join(OUT, name + '.png'))


def grid(w, h):
    y, x = np.mgrid[0:h, 0:w]
    return (x + 0.5) / w, (y + 0.5) / h


def noise(w, h, octaves=5, seed=0):
    r = np.random.default_rng(seed)
    out = np.zeros((h, w))
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        n = 2 ** (o + 2)
        g = r.random((n + 1, n + 1))
        g[-1, :] = g[0, :]
        g[:, -1] = g[:, 0]
        im = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        out += np.asarray(im) / 255.0 * amp
        tot += amp
        amp *= 0.5
    return out / tot


def glow_tex(rgb, a):
    """brillo: color * intensidad, alfa = intensidad"""
    return np.dstack([rgb[..., 0] * a, rgb[..., 1] * a, rgb[..., 2] * a, a])


def white(a):
    return np.dstack([a, a, a, a])


# ---------------------------------------------------------------- hoja (u = largo, v = ancho; el borde lo da la malla)
def leaf():
    w, h = 512, 256
    u, v = grid(w, h)
    d = np.abs(v - 0.5) * 2            # 0 en el nervio central, 1 en el borde
    n = noise(w, h, 5, 3)
    base = np.array([0.16, 0.52, 0.20])
    light = np.array([0.52, 0.86, 0.34])
    dark = np.array([0.05, 0.27, 0.13])
    t = np.clip(0.55 - d * 0.35 + (n - 0.5) * 0.5 + u * 0.12, 0, 1)[..., None]
    col = dark + (light - dark) * t * 0.9 + base * 0.1
    # nervio central y nervios laterales en espiga
    mid = np.exp(-(d / (0.035 + 0.03 * (1 - u))) ** 2)
    veins = np.zeros_like(u)
    for k in range(13):
        u0 = 0.06 + k * 0.07
        line = u - (u0 + d * 0.16)                       # salen hacia la punta
        wv = 0.006 + 0.004 * (1 - d)
        veins = np.maximum(veins, np.exp(-(line / wv) ** 2) * (1 - d * 0.55))
    fine = np.zeros_like(u)
    for k in range(60):
        u0 = 0.02 + k * 0.0165
        line = u - (u0 + d * 0.16)
        fine = np.maximum(fine, np.exp(-(line / 0.0022) ** 2) * 0.35 * (1 - d * 0.4))
    vein = np.clip(np.maximum(mid, veins) + fine, 0, 1)[..., None]
    col = col * (1 - vein * 0.55) + np.array([0.80, 0.97, 0.55]) * vein * 0.55
    edge = np.clip((d - 0.86) / 0.14, 0, 1)[..., None]
    col = col * (1 - edge * 0.45) + np.array([0.78, 0.92, 0.38]) * edge * 0.25
    rgba = np.dstack([col, np.ones((h, w))])
    save('leaf', rgba)
    g = np.clip(np.maximum(mid, veins) * 1.0 + fine * 0.6, 0, 1) * (0.55 + 0.45 * n)
    rim = np.clip((d - 0.9) / 0.1, 0, 1) * 0.5
    a = np.clip(g + rim, 0, 1)
    save('leaf_veins', glow_tex(np.dstack([0.62 + 0 * u, 1.0 + 0 * u, 0.62 + 0 * u]), a))


def leaflet():
    w = h = 32
    u, v = grid(w, h)
    x, y = u * 2 - 1, v * 2 - 1
    hw = 0.62 * np.sin(np.pi * np.clip((x + 1) / 2, 0, 1)) ** 0.8
    inside = (np.abs(y) < hw).astype(float)
    mid = (np.abs(y) < 0.07).astype(float)
    col = np.dstack([0.35 + 0.35 * mid, 0.85 + 0.12 * mid, 0.38 + 0.2 * mid])
    save('leaflet', np.dstack([col, inside]))


# ---------------------------------------------------------------- brillos
def radial(w=128):
    u, v = grid(w, w)
    return np.hypot(u - 0.5, v - 0.5) * 2, np.arctan2(v - 0.5, u - 0.5)


def glow():
    r, _ = radial()
    a = np.clip(1 - r, 0, 1) ** 2.2
    save('glow', white(a))


def line():
    u, v = grid(16, 64)
    d = np.abs(v - 0.5) * 2
    a = np.exp(-(d / 0.12) ** 2) + 0.5 * np.clip(1 - d, 0, 1) ** 2
    save('line', white(np.clip(a, 0, 1)))
    core = np.clip(1 - (d / 0.5) ** 6, 0, 1)
    save('void_line', np.dstack([0 * u + 0.02, 0 * u, 0 * u + 0.05, core]))


def ring():
    r, _ = radial(256)
    a = np.exp(-((r - 0.82) / 0.035) ** 2) + 0.35 * np.exp(-((r - 0.82) / 0.12) ** 2)
    save('ring', white(np.clip(a, 0, 1)))
    # ondas concentricas
    a = np.zeros_like(r)
    for i, r0 in enumerate([0.25, 0.4, 0.55, 0.7, 0.85]):
        a += np.exp(-((r - r0) / (0.018 + 0.01 * i)) ** 2) * (1 - 0.12 * i)
    a += 0.25 * np.clip(1 - r, 0, 1) ** 1.5
    a *= np.clip((1 - r) / 0.08, 0, 1)
    save('rings', white(np.clip(a, 0, 1)))
    # onda de choque en el suelo
    a = np.exp(-((r - 0.86) / 0.05) ** 2) + 0.5 * np.clip((r - 0.3) / 0.56, 0, 1) ** 4 * (r < 0.86)
    save('shock', white(np.clip(a, 0, 1)))


def galaxy():
    w = 512
    r, th = radial(w)
    n = noise(w, w, 6, 11)
    arms = np.zeros_like(r)
    for k in range(2):
        phase = th + k * np.pi - np.log(r * 9 + 0.35) * 2.6
        arms += (0.5 + 0.5 * np.cos(phase)) ** 6
    env = np.clip(1 - r, 0, 1) ** 1.3
    a = arms * env * (0.45 + 0.9 * n)
    core = np.exp(-(r / 0.13) ** 2) * 1.6
    stars = (rng.random((w, w)) > 0.9965) * env * 1.5
    lum = np.clip(a * 0.9 + stars, 0, 1)
    blue = np.dstack([0.55 + 0 * r, 0.7 + 0 * r, 1.0 + 0 * r])
    warm = np.dstack([1.0 + 0 * r, 0.9 + 0 * r, 0.62 + 0 * r])
    c = np.clip(core, 0, 1)[..., None]
    rgb = blue * lum[..., None] + warm * c
    al = np.clip(lum + core, 0, 1)
    save('galaxy', np.dstack([np.clip(rgb, 0, 1), al]))


def black_hole():
    w = 512
    r, th = radial(w)
    n = noise(w, w, 5, 21)
    swirl = 0.6 + 0.4 * np.cos(th * 3 + r * 22 + n * 5)
    disc = np.exp(-((r - 0.55) / 0.2) ** 2) * swirl * (r > 0.34)
    rim = np.exp(-((r - 0.345) / 0.018) ** 2) * 1.5
    lum = np.clip(disc * 0.85 + rim, 0, 1)
    rgb = np.dstack([1.0 * lum, (0.72 + 0.25 * rim.clip(0, 1)) * lum, (0.38 + 0.5 * rim.clip(0, 1)) * lum])
    save('accretion', np.dstack([np.clip(rgb, 0, 1), lum]))
    a = np.clip((0.36 - r) / 0.03, 0, 1)
    save('void', np.dstack([0 * r, 0 * r, 0 * r, a]))


def planet():
    w, h = 256, 128
    u, v = grid(w, h)
    n = noise(w, h, 5, 31)
    band = 0.5 + 0.5 * np.sin(v * 26 + (n - 0.5) * 5 + np.sin(u * 2 * np.pi) * 0.4)
    c0 = np.array([0.10, 0.09, 0.10])
    c1 = np.array([0.30, 0.36, 0.42])
    c2 = np.array([0.24, 0.16, 0.12])
    t = band[..., None]
    col = c0 + (c1 - c0) * t + (c2 - c0) * (n[..., None] - 0.5) * 0.9
    save('planet', np.dstack([np.clip(col, 0, 1), np.ones((h, w))]))


def pillar():
    w, h = 32, 128
    u, v = grid(w, h)
    d = np.abs(u - 0.5) * 2
    a = (np.exp(-(d / 0.25) ** 2) + 0.4 * np.clip(1 - d, 0, 1)) * np.clip(1 - v, 0, 1) ** 0.6 * np.clip(v / 0.04, 0, 1)
    save('pillar', white(np.clip(a, 0, 1)))


def zzz():
    w = h = 16
    a = np.zeros((h, w))
    for y, row in enumerate(["................", "...#########....", "...#########....", "..........##....", ".........##.....",
                             "........##......", ".......##.......", "......##........", ".....##.........", "....##..........",
                             "...#########....", "...#########....", "................", "................", "................", "................"]):
        for x, ch in enumerate(row):
            a[y, x] = ch == '#'
    save('zzz', white(a))


for f in (leaf, leaflet, glow, line, ring, galaxy, black_hole, planet, pillar, zzz):
    f()
print(sorted(os.listdir(OUT)))
