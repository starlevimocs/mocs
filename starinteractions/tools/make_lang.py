"""Genera los archivos de idioma de Star Interactions."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'starinteractions', 'lang')

INTER = {
    'handshake': ('Apretón de manos', 'Handshake'), 'fist_bump': ('Chocar puños', 'Fist bump'),
    'high_five': ('Chocar los cinco', 'High five'), 'bow_each': ('Reverencia', 'Bow'),
    'hug': ('Abrazo', 'Hug'), 'pat_head': ('Acariciar la cabeza', 'Head pat'), 'comfort': ('Consolar', 'Comfort'),
    'cheek_kiss': ('Beso en la mejilla', 'Cheek kiss'), 'forehead_kiss': ('Beso en la frente', 'Forehead kiss'),
    'kiss': ('Beso', 'Kiss'), 'dance_fun': ('Bailar', 'Dance'), 'dance_slow': ('Baile lento', 'Slow dance'),
    'dance_tango': ('Tango', 'Tango'), 'lift_spin': ('Alzar y girar', 'Lift and spin'), 'toast': ('Brindis', 'Toast'),
    'photo': ('Foto juntos', 'Photo together'), 'tickle': ('Cosquillas', 'Tickle'), 'slap': ('Cachetada', 'Slap'),
    'hold_hands': ('Ir de la mano', 'Hold hands'), 'carry_back': ('Llevar a caballito', 'Piggyback'),
    'carry_arms': ('Cargar en brazos', 'Carry in arms'), 'carry_shoulders': ('Subir a los hombros', 'Carry on shoulders'),
    'touch': ('Tocar (con el mouse)', 'Touch (with the mouse)'),
    'wave': ('Saludar', 'Wave'), 'bow': ('Reverencia', 'Bow'), 'clap': ('Aplaudir', 'Clap'), 'cheer': ('Festejar', 'Cheer'),
    'laugh': ('Reír', 'Laugh'), 'cry': ('Llorar', 'Cry'), 'facepalm': ('Facepalm', 'Facepalm'),
    'shrug': ('Encogerse de hombros', 'Shrug'), 'think': ('Pensar', 'Think'), 'no': ('No', 'No'), 'nod': ('Sí', 'Nod'),
    'point': ('Señalar', 'Point'), 'salute': ('Saludo militar', 'Salute'), 'shy': ('Timidez', 'Shy'),
    'angry': ('Enojo', 'Angry'), 'sad': ('Tristeza', 'Sad'), 'surprised': ('Sorpresa', 'Surprised'),
    'stretch': ('Estirarse', 'Stretch'), 'yawn': ('Bostezar', 'Yawn'), 'hips': ('Manos a la cintura', 'Hands on hips'),
    'welcome': ('Bienvenida', 'Welcome'), 'pray': ('Rezar', 'Pray'), 'flirty': ('Coquetear', 'Flirty'),
    'look_around': ('Mirar alrededor', 'Look around'), 'dance_solo': ('Bailar solo', 'Dance alone'),
    'sit_ground': ('Sentarse', 'Sit down'),
}

TEXT = {
    'key.categories.starinteractions': ('Star Interactions', 'Star Interactions'),
    'key.starinteractions.menu': ('Menú de interacciones', 'Interaction menu'),
    'key.starinteractions.touch': ('Tocar a quien tengo enfrente', 'Touch whoever is in front'),
    'key.starinteractions.accept': ('Aceptar interacción', 'Accept interaction'),
    'key.starinteractions.decline': ('Rechazar interacción', 'Decline interaction'),
    'gui.starinteractions.title': ('Interacciones', 'Interactions'),
    'gui.starinteractions.title_emotes': ('Emotes', 'Emotes'),
    'gui.starinteractions.title_with': ('Interactuar con %s', 'Interact with %s'),
    'gui.starinteractions.tab.greet': ('Saludos', 'Greetings'),
    'gui.starinteractions.tab.care': ('Cariño', 'Affection'),
    'gui.starinteractions.tab.fun': ('Diversión', 'Fun'),
    'gui.starinteractions.tab.together': ('Juntos', 'Together'),
    'gui.starinteractions.tab.emote': ('Emotes', 'Emotes'),
    'gui.starinteractions.release': ('Soltar', 'Let go'),
    'gui.starinteractions.villager_hint': (
        'Con el mouse: acariciale la cabeza o la mejilla, tocale la nariz, hacele cosquillas o agarrale un brazo y movelo. Abajo, tus emotes.',
        'With the mouse: pet their head or cheek, boop their nose, tickle them or grab an arm and move it. Below, your emotes.'),
    'gui.starinteractions.prompt': ('%1$s quiere: %2$s', '%1$s wants: %2$s'),
    'gui.starinteractions.prompt_keys': ('[%1$s] Aceptar     [%2$s] Rechazar', '[%1$s] Accept     [%2$s] Decline'),
    'gui.starinteractions.touch': ('Modo toque', 'Touch mode'),
    'gui.starinteractions.touching': ('Con %s', 'With %s'),
    'gui.starinteractions.touch_exit': ('Esc o clic derecho para salir', 'Esc or right click to leave'),
    'gui.starinteractions.zone.none': ('Mové la mano sobre la cabeza, la cara, los brazos o el cuerpo', 'Move the hand over the head, face, arms or body'),
    'gui.starinteractions.zone.head': ('Mantené apretado y mové para acariciar la cabeza', 'Hold and move to pet the head'),
    'gui.starinteractions.zone.cheek': ('Mantené apretado para acariciar la mejilla', 'Hold to caress the cheek'),
    'gui.starinteractions.zone.nose': ('Clic para tocarle la nariz', 'Click to boop the nose'),
    'gui.starinteractions.zone.right_arm': ('Agarrá el brazo y arrastralo', 'Grab the arm and drag it'),
    'gui.starinteractions.zone.left_arm': ('Agarrá el brazo y arrastralo', 'Grab the arm and drag it'),
    'gui.starinteractions.zone.body': ('Clic para hacer cosquillas', 'Click to tickle'),
    'message.starinteractions.too_far': ('Está muy lejos, acercate un poco', 'Too far away, get closer'),
    'message.starinteractions.busy': ('Ahora no se puede: alguno de los dos está ocupado', 'Not now: one of you is busy'),
    'message.starinteractions.cant_touch': ('A eso no se lo puede tocar', "You can't touch that"),
    'message.starinteractions.players_only': ('Eso es solo entre jugadores', 'That one is for players only'),
    'message.starinteractions.no_target': ('No hay nadie enfrente', 'Nobody in front of you'),
    'message.starinteractions.accept': ('[Aceptar]', '[Accept]'),
    'message.starinteractions.decline': ('[Rechazar]', '[Decline]'),
    'message.starinteractions.asks': ('%1$s quiere: %2$s', '%1$s wants: %2$s'),
    'message.starinteractions.asked': ('Le preguntaste a %s...', 'You asked %s...'),
    'message.starinteractions.expired': ('El pedido se venció', 'The request expired'),
    'message.starinteractions.declined': ('%s no quiso', '%s declined'),
    'message.starinteractions.no_room': ('No hay lugar acá para eso', 'There is no room here for that'),
    'message.starinteractions.hands_hint': ('Van de la mano. Agachate para soltar', 'Holding hands. Sneak to let go'),
    'message.starinteractions.carry_hint': ('Agachate para bajarlo', 'Sneak to put them down'),
    'message.starinteractions.carried_hint': ('Te llevan. Agachate para bajarte', 'You are being carried. Sneak to get down'),
    'message.starinteractions.touch_closer': ('Te alejaste demasiado', 'You moved too far away'),
    'message.starinteractions.touch_rejected': ('No quiere que la toquen ahora', "They don't want to be touched right now"),
}

# español neutro para los que no vosean
NEUTRO = {'acariciale': 'acaríciale', 'tocale': 'tócale', 'hacele': 'hazle', 'agarrale': 'agárrale', 'movelo': 'muévelo',
          'Mové': 'Mueve', 'Mantené': 'Mantén', 'mové': 'mueve', 'Agarrá': 'Agarra', 'arrastralo': 'arrástralo',
          'acercate': 'acércate', 'Agachate': 'Agáchate', ' acá ': ' aquí '}


def build(idx, neutral):
    out = {}
    for k, v in TEXT.items():
        out[k] = v[idx]
    for k, v in INTER.items():
        out['interaction.starinteractions.' + k] = v[idx]
    if neutral:
        for k in out:
            for a, b in NEUTRO.items():
                out[k] = out[k].replace(a, b)
    return out


os.makedirs(OUT, exist_ok=True)
for lang, idx, neutral in [('es_ar', 0, False), ('es_uy', 0, False), ('es_mx', 0, True), ('es_es', 0, True),
                           ('es_cl', 0, True), ('es_ve', 0, True), ('en_us', 1, False)]:
    with open(os.path.join(OUT, lang + '.json'), 'w', encoding='utf-8') as f:
        json.dump(build(idx, neutral), f, ensure_ascii=False, indent=2, sort_keys=True)
print('ok')
