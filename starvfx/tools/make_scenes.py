"""Escenas que trae Star VFX. Tiempos en segundos; posiciones relativas al punto donde se dispara la escena."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'data', 'starvfx', 'scenes')
os.makedirs(OUT, exist_ok=True)
GREEN = '#b8ffd0'


def ev(t, type, **k):
    return dict(t=t, type=type, **k)


def fx(t, effect, pos, **k):
    return dict(t=t, type='effect', effect=effect, pos=pos, **k)


def snd(t, sound, volume=1.0, pitch=1.0):
    return ev(t, 'sound', sound='minecraft:' + sound, volume=volume, pitch=pitch)


def save(name, scene):
    scene['events'].sort(key=lambda e: e['t'])
    with open(os.path.join(OUT, name + '.json'), 'w', encoding='utf-8') as f:
        json.dump(scene, f, ensure_ascii=False, indent=2)


# ------------------------------------------------------------------ la hoja: el comienzo de todo
FALL_START, FALL_TIME = 6.5, 26.0
LAND = FALL_START + FALL_TIME          # 32.5
hoja = {
    'duration': 48.4, 'lock': True, 'hide_hud': True, 'letterbox': True,
    'camera': [
        # 1) desde el suelo, el cielo se abre
        dict(t=0, pos=[0, 1.7, -24], look=[0, 60, 0], fov=70),
        dict(t=6.4, pos=[0, 2.2, -20], look=[0, 118, 0], fov=62),
        # 2) junto a la hoja, girando a su alrededor mientras baja
        dict(t=6.6, cut=True, attach='hoja', pos=[27, 9, -21], follow='hoja', fov=56),
        dict(t=14, attach='hoja', pos=[-23, 5, -25], follow='hoja', fov=50),
        # 3) desde arriba: la hoja y el mundo alla abajo
        dict(t=14, cut=True, attach='hoja', pos=[0, 24, 7], follow='hoja', fov=76, roll=-6),
        dict(t=21, attach='hoja', pos=[5, 15, -4], follow='hoja', fov=70, roll=7),
        # 4) desde el suelo, lejos: baja hamacandose contra el cielo
        dict(t=21, cut=True, pos=[-36, 2.2, 24], follow='hoja', fov=60),
        dict(t=30, pos=[-26, 1.8, 16], follow='hoja', fov=48),
        # 5) a ras del pasto: se posa
        dict(t=30, cut=True, pos=[11, 0.7, 9], look=[0, 3, 0], fov=66),
        dict(t=35, pos=[8, 1.3, 11], look=[0, 1.2, 0], fov=60),
        # 6) la hoja se deshace y brotan
        dict(t=35, cut=True, pos=[0, 2.6, -12], look=[0, 1.2, 0], fov=60),
        dict(t=43, pos=[0, 8, -17], look=[0, 1, 0], fov=62),
        dict(t=47.6, pos=[0, 15, -25], look=[0, 3, 0], fov=64, ease='out'),
    ],
    'events': [
        ev(0, 'fade', color='#000000', **{'in': 0, 'hold': 0.7, 'out': 2.6}),
        snd(1.0, 'block.conduit.activate', 1.0, 0.6),
        fx(1.0, 'sky_rings', [0, 135, 0], size=150, duration=31, grow=5, color='#d6f5e4', fade_in=2.5, fade_out=4),
        snd(3.0, 'item.trident.thunder', 0.9, 0.6),
        ev(3.0, 'shake', strength=0.6, duration=2.2),
        fx(3.0, 'sky_crack', [0, 128, 0], size=70, grow=3.5, duration=27, color=GREEN, fade_out=3, seed=11),
        ev(6.3, 'flash', color='#eafff0', **{'in': 0.1, 'hold': 0.1, 'out': 1.1}),
        snd(6.4, 'block.beacon.activate', 1.0, 0.7),
        fx(FALL_START, 'leaf', [0, 0, 0], id='hoja', size=24, fall=125, fall_time=FALL_TIME, hold=2.5, dissolve=3.5, color=GREEN),
        snd(10, 'block.amethyst_block.chime', 1.0, 0.7),
        snd(15, 'block.amethyst_block.chime', 1.0, 0.9),
        snd(20, 'block.amethyst_block.chime', 1.0, 0.8),
        snd(25, 'block.amethyst_block.chime', 1.0, 1.0),
        snd(29, 'block.amethyst_block.chime', 1.0, 1.2),
        # impacto
        snd(LAND, 'block.end_portal.spawn', 0.8, 1.0),
        snd(LAND, 'entity.generic.explode', 0.6, 0.5),
        ev(LAND, 'flash', color='#eafff0', **{'in': 0.05, 'hold': 0.12, 'out': 1.3}),
        ev(LAND, 'shake', strength=1.7, duration=2.6),
        fx(LAND, 'shockwave', [0, 0, 0], size=28, duration=2.8, color=GREEN),
        fx(LAND + 0.25, 'shockwave', [0, 0, 0], size=13, duration=1.8, color='#ffffff'),
        fx(LAND + 0.1, 'ground_crack', [0, 0, 0], size=13, grow=1.2, duration=15, color='#8dffb0', fade_out=3, seed=5),
        # la hoja se vuelve luz
        fx(35, 'swarm', [0, 0, 0], size=6, duration=10),
        fx(35, 'pillar', [0, 0, 0], size=3, duration=7, color=GREEN),
        snd(35, 'block.respawn_anchor.charge', 0.8, 0.8),
        # nacen
        ev(36.5, 'sprout', duration=4, radius=5),
        snd(36.6, 'block.rooted_dirt.break', 1.0, 0.7),
        snd(38.0, 'block.amethyst_block.chime', 1.0, 1.4),
        snd(40.6, 'ui.toast.challenge_complete', 0.7, 0.9),
        ev(40.8, 'text', text='DREAMS', sub='Todo empieza con una hoja', duration=5.2, color='#eafff0'),
        ev(47.2, 'fade', color='#000000', **{'in': 0.5, 'hold': 0.5, 'out': 1.4}),
    ],
}
save('hoja', hoja)

# ------------------------------------------------------------------ una grieta se abre en el cielo
save('grieta_cielo', {
    'duration': 24, 'lock': False, 'hide_hud': False, 'letterbox': True,
    'camera': [],
    'events': [
        snd(0.2, 'item.trident.thunder', 1.0, 0.5),
        ev(0.3, 'shake', strength=1.0, duration=2.5),
        ev(0.3, 'flash', color='#d8ccff', **{'in': 0.05, 'hold': 0.05, 'out': 0.9}),
        fx(0.3, 'sky_crack', [0, 110, 40], size=90, grow=4, duration=23, fade_out=4),
        fx(2.0, 'sky_rings', [0, 110, 40], size=120, grow=6, duration=18, color='#b9a2ff', fade_out=4),
        snd(4.5, 'entity.lightning_bolt.thunder', 0.8, 0.6),
        ev(4.5, 'shake', strength=0.5, duration=1.5),
    ],
})

# ------------------------------------------------------------------ un agujero negro asoma
save('agujero_negro', {
    'duration': 40, 'lock': False, 'hide_hud': False, 'letterbox': True,
    'camera': [],
    'events': [
        snd(0.5, 'block.portal.trigger', 0.7, 0.5),
        fx(0.5, 'black_hole', [0, 140, 60], size=70, duration=39, fade_in=6, fade_out=5),
        ev(3, 'shake', strength=0.4, duration=6),
        ev(8, 'text', text='Algo mira desde arriba', duration=4),
    ],
})

# ------------------------------------------------------------------ la tierra se parte
save('temblor', {
    'duration': 16, 'lock': False, 'hide_hud': False, 'letterbox': False,
    'camera': [],
    'events': [
        snd(0, 'entity.generic.explode', 0.8, 0.5),
        ev(0, 'shake', strength=2.0, duration=4),
        fx(0.1, 'ground_crack', [0, 0, 0], size=16, grow=2, duration=15, color='#ff9a5c', fade_out=3),
        fx(0.1, 'shockwave', [0, 0, 0], size=20, duration=2.2, color='#ffb080'),
        ev(1.5, 'shake', strength=0.8, duration=3),
    ],
})
print(os.listdir(OUT))
