"""Dibuja poses de las animaciones fuera del juego (proyeccion simple) para revisarlas. Uso: preview.py salida.png anim[:n] ..."""
import math
import sys

import numpy as np
from PIL import Image, ImageDraw

import anims
import rig
from kin import fk, rot_zyx, trans

CELL_W, CELL_H, SCALE = 230, 250, 4.4
FACES = {
    'north': ((1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)), 'south': ((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)),
    'east': ((1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)), 'west': ((0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)),
    'up': ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)), 'down': ((0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)),
}


def avg(img, uv):
    x0, x1 = sorted((int(uv[0]), int(math.ceil(uv[2]))))
    y0, y1 = sorted((int(uv[1]), int(math.ceil(uv[3]))))
    x1, y1 = max(x1, x0 + 1), max(y1, y0 + 1)
    px = np.asarray(img.crop((x0, y0, x1, y1)).convert('RGBA'), dtype=float).reshape(-1, 4)
    px = px[px[:, 3] > 0]
    return px[:, :3].mean(axis=0) if len(px) else None


def draw(roots, texs, anim, t, view=(-0.62, -0.38, 1.0)):
    img = Image.new('RGB', (CELL_W, CELL_H), (24, 28, 38))
    d = np.array(view, dtype=float)
    d /= np.linalg.norm(d)
    right = np.cross(d, (0, 1, 0))
    right /= np.linalg.norm(right)
    up = np.cross(right, d)
    light = np.array((0.3, 0.8, -0.5))
    light /= np.linalg.norm(light)
    mats, vis = fk(roots, anim, t)
    polys = []
    # piso
    g = [(-22, 0, -40), (22, 0, -40), (22, 0, 22), (-22, 0, 22)]
    polys.append((1e9, [np.array(p, dtype=float) for p in g], (44, 52, 60)))
    for b in rig.all_bones(roots):
        if not vis[b.name]:
            continue
        m = mats[b.name]
        for c in b.cubes:
            if c.inflate:
                continue
            lo = np.array(c.frm, dtype=float)
            hi = np.array(c.to, dtype=float)
            cm = m
            if c.rotation:
                o = c.origin
                cm = m @ trans(*o) @ rot_zyx(*[math.radians(v) for v in c.rotation]) @ trans(-o[0], -o[1], -o[2])
            for name, corners in FACES.items():
                f = c.faces.get(name)
                if not f:
                    continue
                pts = [(cm @ np.append(lo + (hi - lo) * np.array(k), 1.0))[:3] for k in corners]
                n = np.cross(pts[1] - pts[0], pts[3] - pts[0])
                ln = np.linalg.norm(n)
                if ln < 1e-9:
                    continue
                n /= ln
                # orientar la normal hacia afuera del cubo
                center = (cm @ np.append((lo + hi) / 2, 1.0))[:3]
                if np.dot(n, np.mean(pts, axis=0) - center) < 0:
                    n = -n
                if np.dot(n, d) > 0:
                    continue
                col = avg(texs[f['texture']], f['uv'])
                if col is None:
                    continue
                shade = 0.55 + 0.45 * max(0.0, float(np.dot(n, light)))
                polys.append((float(np.dot(np.mean(pts, axis=0), d)), pts, tuple(int(v * shade) for v in col)))
    polys.sort(key=lambda p: -p[0])
    dr = ImageDraw.Draw(img)
    cx, cy = CELL_W / 2 + 12, CELL_H - 62
    for _, pts, col in polys:
        dr.polygon([(cx + float(np.dot(p, right)) * SCALE, cy - float(np.dot(p, up)) * SCALE) for p in pts], fill=col)
    dr.text((4, 3), '%s %.2fs' % (anim.name if anim else 'rest', t), fill=(220, 226, 236))
    return img


def sheet(out, specs, view=(-0.62, -0.38, 1.0)):
    roots = rig.build(False)
    table = anims.make(roots)
    texs = [rig.default_skin(), rig.props_texture()]
    rows = []
    for spec in specs:
        name, _, n = spec.partition(':')
        n = int(n) if n else 6
        a = table[name]
        rows.append([draw(roots, texs, a, a.length * i / n, view) for i in range(n)])
    w = max(len(r) for r in rows)
    img = Image.new('RGB', (w * CELL_W, len(rows) * CELL_H), (10, 12, 16))
    for y, r in enumerate(rows):
        for x, cell in enumerate(r):
            img.paste(cell, (x * CELL_W, y * CELL_H))
    img.save(out)


if __name__ == '__main__':
    args = sys.argv[2:]
    view = (-0.62, -0.38, 1.0)
    if args and args[0].startswith('view='):
        view = tuple(float(v) for v in args[0][5:].split(','))
        args = args[1:]
    sheet(sys.argv[1], args, view)
