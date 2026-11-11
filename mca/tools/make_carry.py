"""Dreams: dialogos de ir de la mano / llevar en la espalda / en brazos (aceptan o no segun corazones y personalidad)."""
import json, os
ROOT = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources")
D = os.path.join(ROOT, "data", "mca", "dialogues")
LANG = os.path.join(ROOT, "assets", "mca_dialogue", "lang")

def r(base, conds, **actions):
    x = {"baseChance": base, "actions": actions}
    if conds:
        x["conditions"] = conds
    return x

def c(chance, **k):
    return dict(chance=chance, **k)

nodes = {
    "hold_hand": [
        r(2, [c(12, hearts={"add": -0.1, "max": 1.5, "dividend": 100}), c(10, constraints="spouse"), c(8, constraints="engaged"),
              c(4, personality="friendly"), c(4, personality="flirty"), c(6, constraints="!adult")],
          positive=1, attach="hand", next="hold_hand.success"),
        r(2, [c(6, hearts_max=5), c(4, personality="shy"), c(4, personality="grumpy"), c(3, personality="odd")],
          gesture="no", next="hold_hand.fail"),
    ],
    "carry_back": [
        r(2, [c(12, hearts={"add": -0.1, "max": 1.5, "dividend": 100}), c(12, constraints="!adult"), c(5, personality="peppy"),
              c(4, personality="athletic"), c(4, personality="friendly"), c(6, constraints="spouse")],
          positive=1, attach="back", next="carry_back.success"),
        r(2, [c(6, hearts_max=10), c(5, personality="grumpy"), c(4, personality="confident"), c(3, personality="shy")],
          gesture="no", next="carry_back.fail"),
    ],
    "carry_arms": [
        r(1, [c(14, hearts={"add": -0.3, "max": 1.5, "dividend": 100}), c(12, constraints="spouse"), c(9, constraints="engaged"),
              c(5, personality="flirty"), c(3, personality="peppy")],
          positive=2, attach="arms", next="carry_arms.success"),
        r(3, [c(8, hearts_max=30), c(4, personality="shy"), c(5, personality="grumpy"), c(3, personality="sensitive")],
          gesture="shy", next="carry_arms.fail"),
    ],
}
for n, results in nodes.items():
    json.dump({"auto": True, "answers": [{"results": results}]}, open(os.path.join(D, n + ".json"), "w"), indent=2, ensure_ascii=False)

T = {
    "dialogue.main.hold_hand": "Tomarle la mano",
    "dialogue.main.carry_back": "Llevar en la espalda",
    "dialogue.main.carry_arms": "Alzar en brazos",
    "dialogue.main.talk": "Conversar",
}
def pool(k, *v):
    for i, s in enumerate(v, 1):
        T[f"{k}/{i}"] = s
pool("dialogue.hold_hand.success", "*te toma la mano* ¿A dónde vamos?", "¡Dale! Pero no me sueltes, ¿eh?", "Qué cálida tu mano...", "Vamos, te sigo a donde sea.")
pool("dialogue.hold_hand.fail", "Eh... no, gracias.", "Mejor no, que la gente habla.", "No te conozco tanto todavía.", "Ni lo sueñes.")
pool("dialogue.carry_back.success", "¡Upa! ¡A caballito!", "¡Ja, ja! ¡Más rápido, más rápido!", "¡Agarrate fuerte, que salto!", "Esto es lo más divertido del mundo.")
pool("dialogue.carry_back.fail", "¡No! Ya estoy grande para eso.", "¿Estás loco? Me vas a tirar.", "Ni en un millón de años.")
pool("dialogue.carry_arms.success", "¡Ay! *se agarra de tu cuello* Avisá antes...", "Como en los cuentos... no me bajes nunca.", "Me siento una princesa. O un príncipe. ¡Me encanta!", "*se ríe* ¡Estás loco! No me sueltes.")
pool("dialogue.carry_arms.fail", "¡Ey! ¡Bajá las manos!", "Q-qué haces... no, no, ¡qué vergüenza!", "No me toques así, por favor.")
for lang in ["es_mx", "es_es", "es_cl", "es_ar", "en_us"]:
    p = os.path.join(LANG, lang + ".json")
    cur = json.load(open(p, encoding="utf-8"))
    for k, v in T.items():
        if lang == "en_us":
            cur.setdefault(k, v)
        else:
            cur[k] = v
    json.dump(cur, open(p, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
print("ok")
