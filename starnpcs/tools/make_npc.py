"""Genera los modelos humanos (.bbmodel, editables en Blockbench) con todas las animaciones."""
import json
import os
import sys

import anims
import rig

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'starnpcs', 'npcmodels')


def model(name, slim):
    rig.RNG.seed(20261113 + slim)
    roots = rig.build(slim)
    table = anims.make(roots)
    elements, outliner = rig.export_model(roots)
    by_name = {b.name: b for b in rig.all_bones(roots)}
    return {
        'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False},
        'name': name, 'model_identifier': '', 'visible_box': [4, 4, 0],
        'variable_placeholders': '', 'variable_placeholder_buttons': [], 'timeline_setups': [], 'unhandled_root_fields': {},
        'resolution': {'width': 64, 'height': 64},
        'elements': elements, 'outliner': outliner,
        'textures': [rig.texture('npc_skin.png', rig.default_skin(), 0), rig.texture('props.png', rig.props_texture(), 1)],
        'animations': [a.export(by_name) for a in table.values()],
    }


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for name, slim in (('human', False), ('human_slim', True)):
        data = model(name, slim)
        path = os.path.join(OUT, name + '.bbmodel')
        with open(path, 'w') as f:
            json.dump(data, f, separators=(',', ':'))
        print(name, len(data['elements']), 'cubos,', len(data['animations']), 'animaciones,', os.path.getsize(path) // 1024, 'KB')
