"""Idiomas de Star VFX."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'starvfx', 'lang')
T = {
    'playing': ('Escena "%s" en marcha (%s s)', 'Scene "%s" playing (%s s)'),
    'stopped': ('Escenas cortadas: %s', 'Scenes stopped: %s'),
    'scenes': ('Escenas: %s', 'Scenes: %s'),
    'reloaded': ('Configuración recargada', 'Config reloaded'),
    'no_scene': ('No existe la escena "%s"', 'There is no scene "%s"'),
    'no_effect': ('No existe el efecto "%s"', 'There is no effect "%s"'),
    'fx_added': ('Efecto "%s" (%s) puesto en el mundo. Queda hasta que lo quites con /starvfx fx remove',
                 'Effect "%s" (%s) placed in the world. It stays until removed with /starvfx fx remove'),
    'fx_played': ('Efecto "%s" (%s)', 'Effect "%s" (%s)'),
    'fx_removed': ('Efecto "%s" quitado', 'Effect "%s" removed'),
    'fx_missing': ('No hay un efecto guardado llamado "%s"', 'There is no saved effect called "%s"'),
    'fx_cleared': ('Efectos quitados: %s', 'Effects removed: %s'),
    'fx_list': ('Efectos puestos: %s', 'Placed effects: %s'),
    'zone_added': ('Zona de cámara fija "%s" creada. La cámara queda donde estás parado', 'Fixed camera zone "%s" created. The camera stays where you stand'),
    'zone_removed': ('Zona "%s" quitada', 'Zone "%s" removed'),
    'zone_missing': ('No hay una zona llamada "%s" en esta dimensión', 'There is no zone called "%s" in this dimension'),
    'zone_list': ('Zonas: %s', 'Zones: %s'),
    'bad_name': ('El nombre solo puede tener minúsculas, números y guion bajo', 'The name may only have lowercase letters, digits and underscores'),
    'scene_new': ('Escena "%s" creada. Su origen es donde estás parado. Usá /starvfx scene cam, fx, text... y /starvfx scene test',
                  'Scene "%s" created. Its origin is where you stand. Use /starvfx scene cam, fx, text... and /starvfx scene test'),
    'scene_edit': ('Editando "%s". Su origen es donde estás parado', 'Editing "%s". Its origin is where you stand'),
    'not_editing': ('Primero elegí una escena con /starvfx scene new o /starvfx scene edit', 'First pick a scene with /starvfx scene new or /starvfx scene edit'),
    'scene_saved': ('Escena "%s" guardada', 'Scene "%s" saved'),
    'scene_added': ('Anotado: %s en el segundo %s', 'Added: %s at second %s'),
    'nothing_to_undo': ('No hay nada para deshacer', 'Nothing to undo'),
    'scene_undone': ('Se quitó lo último que anotaste', 'Removed the last thing you added'),
}
T.update({
    'tree_placed': ('Árbol Madre "%s" plantado', 'Mother Tree "%s" placed'),
    'tree_removed': ('Árbol "%s" quitado', 'Tree "%s" removed'),
    'tree_missing': ('No hay un árbol llamado "%s"', 'There is no tree called "%s"'),
    'tree_vitality': ('Vida del árbol "%s": %s%%', 'Tree "%s" vitality: %s%%'),
    'tree_changed': ('Árbol "%s" actualizado', 'Tree "%s" updated'),
    'tree_list': ('Árboles: %s', 'Trees: %s'),
})
EXTRA = {'gui.starvfx.tree_talk': ('Clic derecho: hablar con el Árbol Madre', 'Right click: speak with the Mother Tree')}
NEUTRO = {'Usá': 'Usa', 'elegí': 'elige', 'estás parado': 'estás parado'}
os.makedirs(OUT, exist_ok=True)
for lang, idx, neutral in [('es_ar', 0, False), ('es_uy', 0, False), ('es_mx', 0, True), ('es_es', 0, True), ('es_cl', 0, True),
                           ('es_ve', 0, True), ('en_us', 1, False)]:
    out = {}
    for k, v in T.items():
        s = v[idx]
        if neutral:
            for a, b in NEUTRO.items():
                s = s.replace(a, b)
        out['message.starvfx.' + k] = s
    for k, v in EXTRA.items():
        out[k] = v[idx]
    json.dump(out, open(os.path.join(OUT, lang + '.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=2, sort_keys=True)
print('ok')
