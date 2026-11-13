"""Animaciones (mismo formato y misma cuenta que el mod) y cinematica para calcular donde queda cada hueso."""
import math

import numpy as np

from rig import uid


class Anim:
    def __init__(self, name, length, loop='loop'):
        self.name, self.length, self.loop = name, length, loop
        self.tracks = {}   # (hueso, canal) -> [(t, [x, y, z], modo)]
        self.fx = []       # (t, canal, texto)

    def _add(self, bone, chan, keys, mode):
        tr = self.tracks.setdefault((bone, chan), [])
        for k in keys:
            tr.append((float(k[0]), [float(k[1]), float(k[2]), float(k[3])], mode))
        tr.sort(key=lambda k: k[0])

    def r(self, bone, *keys, mode='catmullrom'):
        self._add(bone, 'rotation', keys, mode)
        return self

    def p(self, bone, *keys, mode='catmullrom'):
        self._add(bone, 'position', keys, mode)
        return self

    def s(self, bone, *keys, mode='linear'):
        self._add(bone, 'scale', keys, mode)
        return self

    def show(self, *props):
        """Los props solo se ven en las animaciones que los animan: una clave de escala alcanza."""
        for prop in props:
            if (prop, 'scale') not in self.tracks:
                self.s(prop, (0, 1, 1, 1))
        return self

    def sound(self, t, spec):
        self.fx.append((float(t), 'sound', spec))
        return self

    def particle(self, t, spec):
        self.fx.append((float(t), 'particle', spec))
        return self

    def close(self):
        """En un bucle, el ultimo cuadro tiene que ser igual al primero."""
        if self.loop != 'loop':
            return self
        for tr in self.tracks.values():
            if len(tr) > 1 and tr[0][0] == 0 and tr[-1][0] < self.length - 1e-6:
                tr.append((self.length, list(tr[0][1]), tr[0][2]))
            elif len(tr) > 1 and abs(tr[-1][0] - self.length) < 1e-6:
                assert all(abs(a - b) < 1e-6 for a, b in zip(tr[0][1], tr[-1][1])), (self.name, tr[0], tr[-1])
        return self

    def copy(self, name):
        a = Anim(name, self.length, self.loop)
        a.tracks = {k: [(t, list(v), m) for t, v, m in tr] for k, tr in self.tracks.items()}
        a.fx = list(self.fx)
        return a

    def shifted(self, name, dt):
        """La misma animacion en bucle, corrida dt segundos (para parejas que se turnan)."""
        a = Anim(name, self.length, self.loop)
        n = 48
        for key, tr in self.tracks.items():
            if len(tr) == 1:
                a.tracks[key] = [(0.0, list(tr[0][1]), tr[0][2])]
                continue
            out = []
            for i in range(n + 1):
                t = i * self.length / n
                out.append((t, sample(tr, (t + dt) % self.length), 'linear'))
            out[-1] = (self.length, list(out[0][1]), 'linear')
            a.tracks[key] = out
        a.fx = [((t - dt) % self.length, c, s) for t, c, s in self.fx]
        return a

    def export(self, bones_by_name):
        animators = {}
        for (bone, chan), tr in self.tracks.items():
            b = bones_by_name[bone]
            an = animators.setdefault(b.uuid, {'name': bone, 'type': 'bone', 'keyframes': []})
            for t, v, mode in tr:
                an['keyframes'].append({'channel': chan, 'data_points': [{'x': fmt(v[0]), 'y': fmt(v[1]), 'z': fmt(v[2])}],
                                        'uuid': uid(), 'time': round(t, 5), 'color': -1, 'interpolation': mode})
        if self.fx:
            keys = []
            for t, chan, spec in self.fx:
                keys.append({'channel': chan, 'data_points': [{'effect': spec, 'file': ''}] if chan == 'sound'
                             else [{'effect': spec, 'locator': '', 'script': '', 'file': ''}],
                             'uuid': uid(), 'time': round(t, 5), 'color': -1, 'interpolation': 'linear'})
            animators['effects'] = {'name': 'Effects', 'type': 'effect', 'keyframes': keys}
        return {'uuid': uid(), 'name': self.name, 'loop': self.loop, 'override': False, 'length': self.length, 'snapping': 24,
                'selected': False, 'anim_time_update': '', 'blend_weight': '', 'start_delay': '', 'loop_delay': '',
                'animators': animators}


def fmt(v):
    s = ('%.4f' % v).rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s


def sample(tr, t):
    n = len(tr)
    if t <= tr[0][0]:
        return list(tr[0][1])
    if t >= tr[-1][0]:
        return list(tr[-1][1])
    i = 0
    while i < n - 2 and tr[i + 1][0] <= t:
        i += 1
    a, b = tr[i], tr[i + 1]
    span = b[0] - a[0]
    k = 1 if span <= 0 else (t - a[0]) / span
    if a[2] == 'step':
        return list(a[1])
    if a[2] == 'catmullrom' or b[2] == 'catmullrom':
        p0 = tr[i - 1][1] if i > 0 else a[1]
        p3 = tr[i + 2][1] if i + 2 < n else b[1]
        k2, k3 = k * k, k * k * k
        return [0.5 * (2 * a[1][c] + (b[1][c] - p0[c]) * k + (2 * p0[c] - 5 * a[1][c] + 4 * b[1][c] - p3[c]) * k2
                       + (3 * a[1][c] - p0[c] - 3 * b[1][c] + p3[c]) * k3) for c in range(3)]
    return [a[1][c] + (b[1][c] - a[1][c]) * k for c in range(3)]


def rot_zyx(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    mx = np.array([[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]])
    my = np.array([[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]])
    mz = np.array([[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]])
    return mz @ my @ mx


def trans(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def fk(roots, anim, t):
    """Matriz de cada hueso (y si se ve) en ese segundo, igual que la arma el mod."""
    out, vis = {}, {}

    def walk(b, parent, shown):
        rot = pos = None
        scl = [1, 1, 1]
        animated = False
        if anim is not None:
            for chan in ('rotation', 'position', 'scale'):
                tr = anim.tracks.get((b.name, chan))
                if tr:
                    animated = True
                    v = sample(tr, t)
                    if chan == 'rotation':
                        rot = v
                    elif chan == 'position':
                        pos = v
                    else:
                        scl = v
        rot = rot or [0, 0, 0]
        pos = pos or [0, 0, 0]
        o = b.origin
        m = parent @ trans(o[0] - pos[0], o[1] + pos[1], o[2] + pos[2]) \
            @ rot_zyx(math.radians(b.rotation[0] - rot[0]), math.radians(b.rotation[1] - rot[1]), math.radians(b.rotation[2] + rot[2])) \
            @ np.diag([scl[0], scl[1], scl[2], 1.0]) @ trans(-o[0], -o[1], -o[2])
        out[b.name] = m
        shown = shown and (not b.name.startswith('prop_') or animated)
        vis[b.name] = shown
        for k in b.kids:
            walk(k, m, shown)

    for r in roots:
        walk(r, np.eye(4), True)
    return out, vis


def point(roots, anim, t, bone, p):
    m, _ = fk(roots, anim, t)
    return (m[bone] @ np.array([p[0], p[1], p[2], 1.0]))[:3]


def bisect(fn, lo, hi, steps=40):
    """Raiz de fn entre lo y hi (fn cambia de signo)."""
    flo = fn(lo)
    for _ in range(steps):
        mid = (lo + hi) / 2
        fm = fn(mid)
        if (fm > 0) == (flo > 0):
            lo, flo = mid, fm
        else:
            hi = mid
    return (lo + hi) / 2
