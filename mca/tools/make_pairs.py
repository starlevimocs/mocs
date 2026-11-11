"""Dreams: mas interacciones de a dos con los aldeanos (saludos, mimos, bailes) y las frases del modo toque
de Star Interactions (touch.*). Cada una se acepta o no segun corazones, personalidad y relacion."""
import json, os
ROOT = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources")
D = os.path.join(ROOT, "data", "mca", "dialogues")
LANG = os.path.join(ROOT, "assets", "mca_dialogue", "lang")
GUI = os.path.join(ROOT, "assets", "mca", "lang")

def r(base, conds, **actions):
    x = {"baseChance": base, "actions": actions}
    if conds:
        x["conditions"] = conds
    return x

def c(chance, **k):
    return dict(chance=chance, **k)

H = lambda add=-0.1, ch=14: c(ch, hearts={"add": add, "max": 1.5, "dividend": 100})

def node(name, ok, fail, positive=2, fail_gesture="no", negative=0):
    acts = dict(next=name + ".fail", gesture=fail_gesture)
    if negative:
        acts["negative"] = negative
    return [r(ok[0], ok[1], next=name + ".success", positive=positive, animate=name), r(fail[0], fail[1], **acts)]

nodes = {
    # saludos: casi siempre salen
    "handshake": node("handshake", (14, [c(5, personality="friendly"), c(4, personality="confident"), H(-0.05, 10)]),
                      (1, [c(5, hearts_max=-10), c(3, personality="grumpy")]), positive=1),
    "fist_bump": node("fist_bump", (10, [c(6, personality="peppy"), c(5, personality="athletic"), c(4, personality="friendly"), H(-0.05, 12)]),
                      (2, [c(5, hearts_max=0), c(4, personality="grumpy"), c(3, personality="gloomy")]), positive=2),
    "bow_each": node("bow_each", (16, [c(4, personality="shy"), c(4, personality="confident")]),
                     (1, [c(4, hearts_max=-20)]), positive=1),
    # mimos: hace falta confianza
    "pat_head": node("pat_head", (3, [H(-0.1, 16), c(14, constraints="!adult"), c(8, constraints="spouse"), c(4, personality="sensitive"), c(4, personality="peppy")]),
                     (3, [c(8, hearts_max=10), c(6, personality="grumpy"), c(4, personality="confident")]), positive=3, fail_gesture="angry"),
    "comfort": node("comfort", (6, [H(-0.05, 14), c(8, mood="sad"), c(8, mood="depressed"), c(5, personality="sensitive"), c(5, personality="gloomy"), c(8, constraints="spouse")]),
                    (2, [c(6, hearts_max=0), c(4, personality="grumpy")]), positive=4, fail_gesture="shrug"),
    "forehead_kiss": node("forehead_kiss", (1, [H(-0.25, 16), c(14, constraints="spouse"), c(10, constraints="engaged"), c(12, constraints="!adult"), c(4, personality="sensitive")]),
                          (3, [c(9, hearts_max=30), c(5, personality="shy"), c(5, personality="grumpy")]), positive=4, fail_gesture="shy"),
    "cheek_kiss": node("cheek_kiss", (1, [H(-0.25, 16), c(14, constraints="spouse"), c(10, constraints="engaged"), c(6, personality="flirty"), c(4, personality="friendly")]),
                       (3, [c(9, hearts_max=30), c(6, personality="shy"), c(5, personality="grumpy")]), positive=4, fail_gesture="shy"),
    # diversion
    "tickle": node("tickle", (4, [H(-0.1, 14), c(12, constraints="!adult"), c(6, personality="peppy"), c(4, personality="witty"), c(6, constraints="spouse")]),
                   (3, [c(8, hearts_max=15), c(7, personality="grumpy"), c(4, personality="gloomy")]), positive=3, fail_gesture="angry", negative=1),
    "toast": node("toast", (8, [H(-0.05, 12), c(6, personality="peppy"), c(5, personality="friendly"), c(5, time_min=12000)]),
                  (2, [c(6, hearts_max=0), c(4, personality="gloomy")]), positive=2),
    "photo": node("photo", (7, [H(-0.05, 12), c(6, personality="confident"), c(5, personality="flirty"), c(5, personality="peppy")]),
                  (2, [c(6, hearts_max=0), c(6, personality="shy"), c(4, personality="grumpy")]), positive=2, fail_gesture="shy"),
    "lift_spin": node("lift_spin", (1, [H(-0.2, 16), c(14, constraints="!adult"), c(12, constraints="spouse"), c(8, constraints="engaged"), c(5, personality="peppy")]),
                      (3, [c(9, hearts_max=25), c(6, personality="grumpy"), c(5, personality="confident"), c(4, personality="shy")]), positive=4, fail_gesture="no"),
    "dance_tango": node("dance_tango", (1, [H(-0.3, 16), c(14, constraints="spouse"), c(10, constraints="engaged"), c(7, personality="flirty"), c(5, personality="confident")]),
                        (3, [c(9, hearts_max=35), c(6, personality="shy"), c(5, personality="lazy"), c(4, personality="grumpy")]), positive=5, fail_gesture="shy"),
}
ADULT = {"cheek_kiss", "dance_tango"}
for n, results in nodes.items():
    json.dump({"auto": True, "answers": [{"results": results}]}, open(os.path.join(D, n + ".json"), "w"), indent=2, ensure_ascii=False)

# respuestas del dialogo principal
p = os.path.join(D, "main.json")
main = json.load(open(p, encoding="utf-8"))
have = {a["name"] for a in main["answers"]}
for n in nodes:
    if n not in have:
        a = {"name": n, "results": [{"actions": {"next": n}}]}
        if n in ADULT:
            a = {"name": n, "constraints": "!kids,adult", "results": [{"actions": {"next": n}}]}
        main["answers"].append(a)
json.dump(main, open(p, "w", encoding="utf-8"), indent=2, ensure_ascii=False)

LABELS = {
    "handshake": "Dar la mano", "fist_bump": "Chocar puños", "bow_each": "Reverencia", "pat_head": "Acariciar la cabeza",
    "comfort": "Consolar", "forehead_kiss": "Beso en la frente", "cheek_kiss": "Beso en la mejilla", "tickle": "Cosquillas",
    "toast": "Brindar", "photo": "Foto juntos", "lift_spin": "Alzar y girar", "dance_tango": "Bailar tango",
    "touch": "Tocar (con el mouse)",
}
T = {"dialogue.main." + k: v for k, v in LABELS.items() if k != "touch"}

def pool(k, *v):
    for i, s in enumerate(v, 1):
        T[f"{k}/{i}"] = s

pool("dialogue.handshake.success", "Un gusto, %1$s.", "*aprieta firme* Así se saluda.", "¡Buenas! ¿Todo bien?", "Siempre es un placer verte.")
pool("dialogue.handshake.fail", "Tengo las manos sucias, mejor no.", "No hace falta tanta formalidad.", "Mmm... paso.")
pool("confident.dialogue.handshake.success", "Buen apretón. Me gusta la gente con manos firmes.", "Entre gente seria nos entendemos.")
pool("shy.dialogue.handshake.success", "*te da la mano bajito* H-hola...", "Ah... sí, un gusto.")
pool("dialogue.fist_bump.success", "¡Pum! ¡Esa!", "¡Buena, %1$s!", "*choca y hace la explosión con los dedos* ¡Fshhh!", "¡Equipo!")
pool("dialogue.fist_bump.fail", "¿Qué se supone que haga con eso?", "No estoy para jueguitos.", "*te mira el puño* ...No.")
pool("peppy.dialogue.fist_bump.success", "¡PUM! ¡Otra, otra, otra!", "¡Somos imparables!")
pool("grumpy.dialogue.fist_bump.fail", "Bajá ese puño antes de que me arrepienta.", "No soy tu compinche.")
pool("dialogue.bow_each.success", "*se inclina* El honor es mío.", "Qué modales. Así da gusto.", "*te devuelve la reverencia con una sonrisa*")
pool("dialogue.bow_each.fail", "No te inclines ante mí, me da cosa.", "¿Te estás burlando?")
pool("dialogue.pat_head.success", "*cierra los ojos* Mmm... no pares.", "Je... me hacés sentir chiquito otra vez.", "*se le escapa una sonrisa* Bueno, un ratito más.",
     "No le cuentes a nadie que me gusta esto.", "Ay, qué lindo...")
pool("dialogue.pat_head.fail", "¡Ey! No soy un perro.", "Las manos quietas, por favor.", "Me despeinás. No.", "*se corre* ¿Qué hacés?")
pool("shy.dialogue.pat_head.success", "*se pone colorado* ...g-gracias.", "*se queda quietito, mirando el piso*")
pool("grumpy.dialogue.pat_head.success", "...Solo porque sos vos.", "Hmpf. Ya está, ya está.")
pool("peppy.dialogue.pat_head.success", "¡Jiji! ¡Más, más!", "¡Me encanta! ¡Ahora yo a vos!")
pool("childp.dialogue.pat_head.success", "¡Jeje! ¿Me porté bien?", "*se te pega a la pierna*")
pool("dialogue.comfort.success", "Gracias... necesitaba esto.", "*suspira hondo* Ya pasa. Gracias por quedarte.", "No sabía cuánto lo necesitaba hasta ahora.",
     "Con vos cerca todo pesa un poco menos.", "*apoya la cabeza en tu hombro*")
pool("dialogue.comfort.fail", "Estoy bien, de verdad.", "No necesito que me tengan lástima.", "Dejame, ya se me pasa.")
pool("gloomy.dialogue.comfort.success", "...No va a cambiar nada. Pero gracias igual.", "Hacía mucho que nadie se quedaba.")
pool("sensitive.dialogue.comfort.success", "*se le llenan los ojos de lágrimas* Perdón... es que sos muy bueno conmigo.", "Gracias por darte cuenta.")
pool("dialogue.forehead_kiss.success", "*cierra los ojos* ...Qué tierno.", "Me hacés sentir a salvo.", "*sonríe despacito* Otra vez.", "Eso fue muy dulce, %1$s.")
pool("dialogue.forehead_kiss.fail", "¡Ey, ey! Tranquilo.", "No tenemos tanta confianza.", "*da un paso atrás* Eh... no.")
pool("spouse.dialogue.forehead_kiss.success", "Con eso me arreglaste el día.", "*te abraza la cintura* Quedate así.", "Te amo, ¿sabías?")
pool("childp.dialogue.forehead_kiss.success", "¡Jeje! Buenas noches a vos también.", "*te abraza fuerte*")
pool("dialogue.cheek_kiss.success", "*se toca la mejilla* ...Ay.", "¡Mirá vos! Qué atrevido. Me gusta.", "*se ríe* ¿Y eso por qué fue?", "Otro del otro lado, para emparejar.")
pool("dialogue.cheek_kiss.fail", "¡¿Qué hacés?!", "Guardá distancia, por favor.", "Ni lo intentes.")
pool("shy.dialogue.cheek_kiss.success", "*se tapa la cara con las manos*", "¡A-avisá antes...!")
pool("flirty.dialogue.cheek_kiss.success", "¿Solo en la mejilla? Qué tímido.", "Mmm... apuntá mejor la próxima.")
pool("spouse.dialogue.cheek_kiss.success", "Buen día para vos también, amor.", "*te devuelve el beso*")
pool("dialogue.tickle.success", "¡JA, JA, JA! ¡Basta, basta!", "¡No! ¡Ahí no! ¡Jajaja!", "*se retuerce de risa* ¡Me rindo!", "¡Pará que me hago pis! ¡Jajaja!")
pool("dialogue.tickle.fail", "No. Tengo. Cosquillas.", "¡Sacá las manos!", "*te mira muy serio*", "Qué pesado sos.")
pool("grumpy.dialogue.tickle.success", "¡Pf...! ¡Je...! ¡BASTA! ...No me reí.", "*aguanta la risa hasta ponerse rojo*")
pool("childp.dialogue.tickle.success", "¡JAJAJA! ¡Otra vez, otra vez!", "¡Nooo! ¡Jijiji!")
pool("dialogue.toast.success", "¡Salud, %1$s!", "¡Por los buenos amigos!", "¡Chin chin! Que nunca falte.", "¡Por la aldea, y por vos!")
pool("dialogue.toast.fail", "No tengo nada que festejar.", "Ahora no, estoy trabajando.", "Brindá solo.")
pool("peppy.dialogue.toast.success", "¡SALUUUD! ¡Otra ronda!", "¡Por todo! ¡Por hoy, por mañana, por las ovejas!")
pool("dialogue.photo.success", "¡Digan whisky!", "¿Salí bien? Sacá otra por las dudas.", "*hace la V con los dedos*", "Esta va para el recuerdo.")
pool("dialogue.photo.fail", "No, no, hoy estoy horrible.", "No me gustan las fotos.", "*se tapa la cara*")
pool("confident.dialogue.photo.success", "Mi lado bueno es este. Bueno, los dos.", "Imposible que salga mal conmigo adentro.")
pool("shy.dialogue.photo.success", "*sonríe apenas, mirando para otro lado*", "B-bueno... pero una sola.")
pool("dialogue.lift_spin.success", "¡Aaah! ¡Jajaja! ¡Me mareo!", "¡Más alto, más alto!", "*se ríe con los brazos abiertos*", "¡No me sueltes! ¡Jajaja!")
pool("dialogue.lift_spin.fail", "¡Bajame! ¡Bajame ya!", "Ni se te ocurra levantarme.", "¿Qué te pensás que soy, una bolsa de papas?")
pool("spouse.dialogue.lift_spin.success", "¡Como el día de la boda! ¡Jajaja!", "*te besa apenas toca el piso*")
pool("childp.dialogue.lift_spin.success", "¡Avioncito! ¡Wiiii!", "¡Otra! ¡Otra! ¡Otra!")
pool("dialogue.dance_tango.success", "*te clava la mirada* Seguime el paso.", "Así se baila. Con el cuerpo y con el alma.", "No sabía que te movías así, %1$s...", "*termina la figura sin aliento* ...Otra.")
pool("dialogue.dance_tango.fail", "Tengo dos pies izquierdos, no.", "El tango no se baila con cualquiera.", "*se ríe nervioso* No, no, qué vergüenza.")
pool("flirty.dialogue.dance_tango.success", "Cuidado, que así empiezan los romances.", "Más cerca. El tango es de a dos, y bien pegados.")
pool("spouse.dialogue.dance_tango.success", "Como la primera vez. Mejor que la primera vez.", "Con vos bailo hasta sin música.")

# modo toque de Star Interactions
pool("touch.head", "*cierra los ojos* Mmm...", "Je... qué lindo.", "*inclina la cabeza hacia tu mano*", "No pares...", "Me vas a malcriar.", "*sonríe* Tenés la mano calentita.")
pool("shy.touch.head", "*se pone colorado*", "E-esto da un poco de vergüenza...", "*se queda quietito*")
pool("grumpy.touch.head", "...Hmpf. Un ratito.", "No te acostumbres.", "*refunfuña, pero no se corre*")
pool("peppy.touch.head", "¡Jiji! ¡Más!", "¡Me encanta, me encanta!")
pool("flirty.touch.head", "Mmm... ¿y después qué sigue?", "Seguí, que vas bien.")
pool("touch.cheek", "*apoya la cara en tu mano*", "Qué suave...", "*te mira a los ojos* ...Hola.", "Me hacés poner colorado.", "*suspira*")
pool("shy.touch.cheek", "*baja la mirada*", "N-no me mires así...")
pool("flirty.touch.cheek", "Así empiezan los besos, ¿sabías?", "*te guiña un ojo*")
pool("spouse.touch.cheek", "Te extrañé hoy.", "*te besa la palma de la mano*", "Quedate así un rato.")
pool("touch.boop", "¡Ey! *arruga la nariz*", "¿Boop? ¿En serio?", "*bizquea mirándote el dedo*", "¡Achís! ...Casi.", "Jaja, ¡mi nariz no es un botón!")
pool("grumpy.touch.boop", "...¿Terminaste?", "Volvé a hacer eso y te muerdo el dedo.")
pool("peppy.touch.boop", "¡Boop para vos también!", "¡Jijiji!")
pool("touch.tickle", "¡Jajaja! ¡Basta!", "¡Ahí no, ahí no!", "*se retuerce* ¡Jijiji!", "¡Me rindo, me rindo!", "¡Pará, que no puedo respirar!")
pool("grumpy.touch.tickle", "¡Pf...! ¡No me... je...! ¡BASTA!", "No me estoy riendo. Es un tic.")
pool("touch.hand", "*te aprieta la mano*", "Tenés la mano fría.", "*entrelaza los dedos con los tuyos*", "¿Vamos a algún lado?")
pool("touch.arm", "¿Qué hacés con mi brazo? Jaja.", "¡Mirá, saludo sin querer!", "*te deja hacer, divertido*", "¿Soy tu marioneta ahora?", "Ojo, que ese es el brazo bueno.", "¡Arriba las manos! Jaja.")
pool("shy.touch.arm", "*se deja mover, sin saber dónde mirar*", "¿A-así...?")
pool("peppy.touch.arm", "¡Wiii! ¡Ahora el otro!", "¡Bailemos así!")
pool("touch.reject", "Ey. Las manos quietas.", "No me toques, por favor.", "*se corre para atrás*", "No tenemos esa confianza.", "¿Te di permiso yo?")
pool("grumpy.touch.reject", "Sacá. La. Mano.", "Tocame otra vez y vas a ver.")
pool("shy.touch.reject", "*se encoge* N-no, por favor...", "*da un paso atrás sin decir nada*")
pool("touch.leave", "¡Basta! Me voy.", "Se terminó. No me sigas.", "Te dije que no.", "*se da vuelta, ofendido*")

for lang in ["es_mx", "es_es", "es_cl", "es_ar", "en_us"]:
    p = os.path.join(LANG, lang + ".json")
    cur = json.load(open(p, encoding="utf-8"))
    for k, v in T.items():
        if lang == "en_us":
            cur.setdefault(k, v)
        else:
            cur[k] = v
    json.dump(cur, open(p, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
    p = os.path.join(GUI, lang + ".json")
    cur = json.load(open(p, encoding="utf-8"))
    for k, v in LABELS.items():
        cur["gui.dreams.action." + k] = v
    cur["gui.dreams.tab.fun"] = "Diversión"
    json.dump(cur, open(p, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
print("ok", len(T))
