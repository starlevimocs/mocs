"""
Dreams: conversaciones sin IA ("Conversar" en el menu).

Genera:
  common/src/main/resources/data/mca/dialogues/talk*.json   (el arbol: preguntas, respuestas, chances)
  common/src/main/resources/assets/mca_dialogue/lang/<idioma>.json  (los textos; se mezclan con los que ya hay)

Como funciona el arbol de MCA:
  - cada nodo es una "pregunta": el aldeano dice "dialogue.<id>" y el jugador elige una respuesta
    (el boton dice "dialogue.<id>.<respuesta>").
  - cada respuesta tiene "results": se elige uno al azar, con peso = baseChance + condiciones que se cumplen
    (personalidad, animo, corazones, relacion, hora, cansancio de tanto hablar...).
  - las acciones de un resultado: next (siguiente nodo), positive/negative (corazones), gesture (animacion del
    aldeano), animate (animacion de pareja), attach (de la mano), command.
  - "/1", "/2"... son variantes del mismo texto (se elige una al azar cada vez).
  - "<personalidad>.dialogue.<id>" y "<profesion>.dialogue.<id>" son variantes que MCA prefiere si existen;
    "spouse.dialogue.<id>" para la pareja.
  - %1$s = el nombre del jugador.
"""
import json
import os
import random

ROOT = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources")
DIALOGUES = os.path.join(ROOT, "data", "mca", "dialogues")
LANG_DIR = os.path.join(ROOT, "assets", "mca_dialogue", "lang")
LANGS = ["es_mx", "es_es", "es_cl", "es_ar", "en_us"]

NODES = {}
TEXT = {}


def lines(key, *variants):
    """Textos con variantes: key/1, key/2..."""
    if len(variants) == 1:
        TEXT[key] = variants[0]
    else:
        for i, v in enumerate(variants, 1):
            TEXT[f"{key}/{i}"] = v


def node(qid, say=None, answers=(), auto=False, silent=False):
    data = {"answers": [a["json"] for a in answers]}
    if auto:
        data["auto"] = True
    if silent:
        data["silent"] = True
    NODES[qid] = data
    if say:
        lines(f"dialogue.{qid}", *say)
    for a in answers:
        if a.get("label"):
            TEXT[f"dialogue.{qid}.{a['json']['name']}"] = a["label"]


def answer(name, label, results, constraints=None):
    j = {"name": name, "results": results}
    if constraints:
        j["constraints"] = constraints
    return {"json": j, "label": label}


def res(next_=None, base=1, cond=(), fatigue=False, **actions):
    r = {"baseChance": base, "actions": {}}
    if cond:
        r["conditions"] = list(cond)
    if fatigue:
        r["applyFatigue"] = True
    for k, v in actions.items():
        r["actions"][k] = v
    if next_:
        r["actions"]["next"] = next_
    return r


def pers(p, chance):
    return {"chance": chance, "personality": p}


def mood(m, chance):
    return {"chance": chance, "mood": m}


def hearts_min(h, chance):
    return {"chance": chance, "hearts_min": h}


def hearts_max(h, chance):
    return {"chance": chance, "hearts_max": h}


def rel(c, chance):
    return {"chance": chance, "constraints": c}


def tired():
    """Si ya hablaron mucho (cansancio de MCA), a veces el aldeano corta la charla."""
    return res("talk.tired", base=0, fatigue=True)


# =====================================================================================================
# el "centro" de la charla: los temas que se pueden sacar en cualquier momento
# =====================================================================================================

def topics():
    return [
        answer("how", "¿Cómo estás?", [res("talk.how", base=1)]),
        answer("about", "Contame algo de vos", [res("talk.about", base=6), res("talk.ask", base=1), tired()]),
        answer("work", "¿Cómo va el trabajo?", [res("talk.work", base=1)], "adult"),
        answer("village", "¿Qué se cuenta en el pueblo?", [res("talk.village", base=1)]),
        answer("day", "¿Qué tal tu día?", [res("talk.day", base=1)]),
        answer("compliment", "Hoy te ves muy bien", [
            res("talk.compliment.happy", base=5, cond=[hearts_min(10, 3), pers("flirty", 6), pers("peppy", 3), pers("friendly", 2)], positive=2, gesture="shy"),
            res("talk.compliment.flirty", base=0, cond=[pers("flirty", 8), pers("confident", 4)], positive=2, gesture="flirty"),
            res("talk.compliment.suspicious", base=1, cond=[pers("odd", 4), pers("greedy", 4), pers("grumpy", 5), hearts_max(5, 3)], gesture="think"),
            res("talk.compliment.again", base=0, fatigue=True, negative=1, gesture="no"),
        ]),
        answer("romance", "Quería decirte algo...", [
            res("talk.love.spouse", base=0, cond=[rel("spouse", 100)]),
            res("talk.love.lover", base=0, cond=[rel("engaged", 100), rel("promised", 100)]),
            res("talk.love.crush", base=0, cond=[hearts_min(40, 10)]),
            res("talk.love.friend", base=1),
        ], "adult,!kids,!parent"),
        answer("joke", "Te cuento un chiste", [res("joke", base=1)]),
        answer("bye", "Bueno, me tengo que ir", [res("talk.bye", base=1)]),
    ]


def hub(qid, say, extra=()):
    """Un nodo donde el aldeano dice algo y despues se puede seguir con cualquier tema."""
    node(qid, say, list(extra) + topics())


# =====================================================================================================
# apertura: depende de la relacion y del animo
# =====================================================================================================

node("talk", answers=[answer("", None, [
    res("talk.open.spouse", base=0, cond=[rel("spouse", 100)]),
    res("talk.open.lover", base=0, cond=[rel("engaged", 100), rel("promised", 100)]),
    res("talk.open.cold", base=0, cond=[hearts_max(-10, 60)]),
    res("talk.open.happy", base=0, cond=[mood("happy", 8), mood("overjoyed", 12)]),
    res("talk.open.sad", base=0, cond=[mood("sad", 8), mood("depressed", 12), mood("unhappy", 4)]),
    res("talk.open.neutral", base=4),
])], auto=True, silent=True)
TEXT["dialogue.main.talk"] = "Conversar"

hub("talk.open.neutral", [
    "¡Hola, %1$s! ¿Querés charlar un rato?",
    "Ah, %1$s. Justo estaba pensando en sentarme a descansar un poco. ¿Qué contás?",
    "¡Qué bueno verte! Dale, contame algo.",
    "Hola. ¿Viniste a charlar? Me viene bien un poco de compañía.",
    "Mirá quién apareció. ¿De qué querés hablar?",
    "¡Ey, %1$s! Siempre es lindo cruzarte por acá.",
])
hub("talk.open.happy", [
    "¡%1$s! ¡Hoy es un día buenísimo, no sabés! ¿Charlamos?",
    "Ay, llegaste justo, ¡estoy de tan buen humor que podría hablar todo el día!",
    "¡Hola, hola! Hoy todo me sale bien. ¿Qué me contás vos?",
    "¡Qué lindo verte! Hoy hasta el pan me salió perfecto.",
])
hub("talk.open.sad", [
    "Ah... hola, %1$s. Perdón, hoy no ando muy bien.",
    "Hola... ¿querías hablar? No sé si voy a ser muy buena compañía hoy.",
    "*suspira* Hola. Está bien, podemos charlar un poco.",
    "Hola, %1$s. Tuve un día medio feo, la verdad.",
])
hub("talk.open.cold", [
    "¿Qué querés ahora?",
    "Ah, sos vos. Habla rápido, tengo cosas que hacer.",
    "Mmm. ¿Y ahora qué?",
    "No tengo muchas ganas de hablar con vos, pero bueno... decí.",
])
hub("talk.open.lover", [
    "¡%1$s! Justo pensaba en vos... ¿querés quedarte un ratito conmigo?",
    "Hola, mi vida. Cada vez que te veo se me arregla el día.",
    "¿Sabés que te extrañé? Vení, contame todo.",
    "Ay, %1$s... me encanta cuando venís a buscarme.",
])
hub("talk.open.spouse", [
    "¡Mi amor! ¿Cómo te fue hoy?",
    "Hola, cariño. Te guardé un poco de la comida, por si tenías hambre.",
    "Ahí estás. Ya me tenías preocupado... ¿todo bien?",
    "¡%1$s! Vení, sentate conmigo un rato.",
    "Hola, mi vida. ¿Charlamos un poco antes de seguir con las cosas de la casa?",
])

# la continuacion generica ("¿y qué más?") con muchas variantes
hub("talk.more", [
    "¿Y qué más me contás?",
    "Bueno, ¿de qué más querés hablar?",
    "Ja, me encanta charlar con vos. ¿Qué más?",
    "Mm-hm. ¿Y vos qué andás haciendo?",
    "¿Algo más que quieras contarme?",
    "Contame más, que hoy tengo tiempo.",
    "Qué lindo esto de charlar así, tranqui.",
])

hub("talk.tired", [
    "Bueno, perdón, me tengo que ir a hacer unas cosas. ¡Otro día seguimos!",
    "Uf, hablamos un montón hoy, ja. Me voy a descansar un poco.",
    "Me encantó la charla, pero me están esperando. ¿Seguimos mañana?",
    "Ya me duele la garganta de tanto hablar, ja, ja.",
])

node("talk.bye", [
    "¡Dale, nos vemos, %1$s!",
    "Bueno, cuidate mucho. ¡Volvé pronto!",
    "Chau, chau. Me alegró el día hablar con vos.",
    "Andá tranqui. Acá voy a estar.",
    "¡Hasta luego! Y no te metas en líos, ¿eh?",
], [answer("leave", "¡Chau!", [res(None, base=1)])])

# =====================================================================================================
# "¿Cómo estás?"
# =====================================================================================================

node("talk.how", answers=[answer("", None, [
    res("talk.how.good", base=0, cond=[mood("happy", 10), mood("overjoyed", 10), mood("fine", 4)]),
    res("talk.how.bad", base=0, cond=[mood("sad", 10), mood("depressed", 10), mood("unhappy", 8)]),
    res("talk.how.ok", base=3),
])], auto=True, silent=True)

hub("talk.how.good", [
    "¡Re bien! Dormí como un tronco y el sol está hermoso.",
    "¡Muy bien! La verdad que no me puedo quejar.",
    "¡Feliz! Hoy me pasaron cosas lindas.",
    "Bien, bien. Con ganas de hacer de todo.",
], [
    answer("glad", "¡Me alegro mucho!", [res("talk.react.warm", base=5, positive=1, gesture="hum"), tired()]),
    answer("why", "¿Y eso? ¿Qué pasó?", [res("talk.how.good.why", base=1, gesture="talk")]),
])
hub("talk.how.good.why", [
    "Es que ayer me regalaron una torta entera. ¡Una torta, %1$s!",
    "Encontré una piedrita brillante en el río. Ya sé, es una tontera, pero me puso contento.",
    "Me dijeron que hago muy bien mi trabajo. Esas cosas te alegran el día, ¿no?",
    "Soñé que volaba. Me desperté con una sonrisa.",
    "Vi un arcoíris enterito arriba del pueblo. ¡Enterito!",
])
hub("talk.how.ok", [
    "Bien, tranqui. Un día como cualquier otro.",
    "Ahí ando, ni bien ni mal.",
    "Normal, ya sabés. ¿Y vos?",
    "Mmm, bien, supongo. Un poco cansado.",
], [
    answer("me_too", "Yo también, tranqui", [res("talk.react.agree", base=1, positive=1)]),
    answer("tell", "Contame algo que te haya pasado", [res("talk.about", base=1)]),
])
hub("talk.how.bad", [
    "No muy bien, la verdad...",
    "Mal. Hoy todo me sale al revés.",
    "Estoy un poco triste. No sé, son cosas mías.",
    "Cansado... y con la cabeza en otro lado.",
], [
    answer("listen", "Si querés, te escucho", [
        res("talk.how.bad.open", base=5, cond=[pers("friendly", 3), pers("sensitive", 3), pers("peppy", 2), hearts_min(20, 4)], gesture="sad"),
        res("talk.how.bad.closed", base=2, cond=[pers("grumpy", 5), pers("shy", 4), pers("gloomy", 3), hearts_max(5, 4)], gesture="no"),
    ]),
    answer("hug", "¿Querés un abrazo?", [
        res("talk.how.bad.hugged", base=4, cond=[hearts_min(15, 5), pers("friendly", 3), pers("sensitive", 3)], positive=3, animate="hug"),
        res("talk.how.bad.nohug", base=1, cond=[hearts_max(5, 5), pers("grumpy", 4)]),
    ]),
    answer("cheer", "¡Arriba ese ánimo!", [
        res("talk.react.cheered", base=3, cond=[pers("peppy", 3), pers("athletic", 3)], positive=1, gesture="cheer"),
        res("talk.react.annoyed", base=1, cond=[pers("gloomy", 4), pers("sensitive", 3)], gesture="angry"),
    ]),
])
hub("talk.how.bad.open", [
    "Es que discutí con alguien que quiero mucho, y no sé cómo arreglarlo.",
    "Extraño a mi familia. A veces el pueblo se siente muy grande y muy vacío.",
    "Siento que trabajo y trabajo y nadie lo nota, ¿viste?",
    "Perdí algo que era muy importante para mí. Era de mi abuela.",
    "No sé... hay días que me levanto así, con una nube encima.",
], [
    answer("advice", "Mañana va a ser un día mejor", [res("talk.how.bad.better", base=1, positive=2, gesture="nod")]),
    answer("hug", "Vení, un abrazo", [res("talk.how.bad.hugged", base=1, positive=3, animate="hug")]),
    answer("joke", "¿Te cuento un chiste para que te rías?", [res("joke", base=1)]),
])
hub("talk.how.bad.closed", [
    "Prefiero no hablar de eso, perdón.",
    "Son cosas mías. No te preocupes.",
    "No es nada... de verdad.",
], [
    answer("respect", "Está bien. Acá estoy si me necesitás", [res("talk.react.respect", base=1, positive=1)]),
    answer("insist", "Dale, contame, no pasa nada", [
        res("talk.how.bad.open", base=3, cond=[hearts_min(30, 5)]),
        res("talk.react.annoyed", base=2, cond=[pers("grumpy", 4)], negative=1, gesture="angry"),
    ]),
])
hub("talk.how.bad.better", [
    "Gracias... de verdad. Me hace bien que alguien me lo diga.",
    "Tenés razón. Un día malo no es una vida mala.",
    "Ja... ojalá. Pero gracias por escucharme, en serio.",
])
hub("talk.how.bad.hugged", [
    "... Gracias. Lo necesitaba más de lo que pensaba.",
    "Ay, qué lindo. Ya me siento un poquito mejor.",
    "No me sueltes todavía, ¿sí? Un ratito más.",
])
hub("talk.how.bad.nohug", [
    "Eh... no, gracias. Mejor no.",
    "No soy mucho de abrazos, perdón.",
])

# reacciones cortas que se reusan
hub("talk.react.warm", ["Ja, gracias. Sos muy amable.", "¡Gracias! Me encanta que te alegres por mí.", "Sos un sol, %1$s."])
hub("talk.react.agree", ["Así me gusta, días tranquilos.", "Eso, sin apuro.", "Ja, somos dos entonces."])
hub("talk.react.respect", ["Gracias por entender.", "Lo voy a tener en cuenta. Gracias.", "Sos buena persona, %1$s."])
hub("talk.react.cheered", ["¡Tenés razón! ¡Vamos con todo!", "Ja, ¡bueno, bueno! Me contagiaste.", "¡Eso! Arriba."])
hub("talk.react.annoyed", ["No me digas cómo tengo que sentirme.", "Ay, dejá. No ayuda.", "Mmm... no sé si eso ayuda mucho."])
hub("talk.react.relate", ["¿En serio? ¡Pensé que era el único!", "Ja, ¡ves que nos parecemos!", "Qué lindo encontrar a alguien que entiende."])
hub("talk.react.laugh", ["¡Ja, ja! Sos terrible.", "¡Jajaja! Bueno, tenés un punto.", "Ja, ¡no te puedo creer!"])
hub("talk.react.hurt", ["Eso fue un poco feo...", "Ah... bueno. Gracias, eh.", "No me causa gracia."])

# =====================================================================================================
# "Contame algo de vos"
# =====================================================================================================

ABOUT = {
    "family": [
        "Mi mamá hacía el mejor guiso del mundo. Todavía lo sueño a veces.",
        "Tengo un hermano que vive lejos. Nos escribimos cartas, pero no es lo mismo.",
        "Mi abuelo me enseñó a silbar con una hoja. Es lo único que sé hacer bien, ja.",
        "En mi familia somos muchos. Las cenas son un lío hermoso.",
    ],
    "dream": [
        "Siempre quise ver el mar de cerca. Dicen que no se termina nunca.",
        "Me gustaría tener una casa con un jardín enorme, lleno de flores.",
        "Sueño con abrir mi propio negocio algún día.",
        "Quiero aprender a leer las estrellas, como los cartógrafos.",
    ],
    "fear": [
        "Le tengo miedo a las arañas. Las grandes. Bueno, y a las chiquitas también.",
        "Me da miedo la noche... con todo lo que anda suelto ahí afuera.",
        "Mi mayor miedo es quedarme solo, la verdad.",
        "Las tormentas. Cada trueno me hace saltar.",
    ],
    "hobby": [
        "En mis ratos libres tallo figuritas de madera. ¡Ya tengo como veinte!",
        "Me encanta juntar flores y secarlas entre las páginas de un libro.",
        "Canto cuando nadie me escucha. Bueno, ahora ya sabés.",
        "Salgo a caminar al atardecer. Es mi momento favorito del día.",
    ],
    "childhood": [
        "De chico me trepé a un árbol y no me podía bajar. Me tuvieron que rescatar con una escalera.",
        "Cuando era chico quería ser caballero. Me hice una espada de palo y todo.",
        "Me escapaba al bosque a buscar hadas. Nunca encontré ninguna... todavía.",
        "Una vez me comí todas las manzanas de la cosecha del vecino. Me dolió la panza tres días.",
    ],
    "food": [
        "Si tuviera que comer una sola cosa para siempre, sería pan recién horneado.",
        "Odio las remolachas. No me importa lo que digan, son tierra con forma.",
        "La sopa de hongos de las noches de frío... no hay nada mejor.",
        "Me encanta el pastel de calabaza. ¡Podría comerme uno entero!",
    ],
    "secret": [
        "Te voy a contar algo que no sabe nadie... a veces hablo con las gallinas. Y siento que me entienden.",
        "Secreto: escondí unas esmeraldas debajo de una piedra, por si algún día las necesito.",
        "No le digas a nadie, pero me da vergüenza bailar en público. Por eso nunca voy a las fiestas.",
        "Tengo un diario donde escribo todo. Hasta escribí sobre vos, ja.",
    ],
}
node("talk.about", answers=[answer("", None,
    [res(f"talk.about.{k}", base=3) for k in ABOUT if k != "secret"]
    + [res("talk.about.secret", base=0, cond=[hearts_min(50, 6)])])], auto=True, silent=True)

MORE = {
    "family": ["Algún día te los presento. Les va a encantar conocerte.", "Ellos me enseñaron a ser como soy. Les debo todo."],
    "dream": ["¿Te imaginás? Capaz algún día lo cumplo. Capaz con tu ayuda, ja.", "Mientras tanto, sigo soñando. Eso es gratis."],
    "fear": ["Por eso me gusta tener a alguien cerca cuando oscurece...", "Pero bueno, de a poco lo voy superando."],
    "hobby": ["Si querés, un día te enseño. Es más fácil de lo que parece.", "Me relaja mucho. Te lo recomiendo."],
    "childhood": ["¡Qué tiempos! Todo era más simple.", "Ja, ¡mi mamá todavía me lo recuerda!"],
    "food": ["Algún día te invito a comer a casa, ¿dale?", "Ahora me dio hambre, ja."],
    "secret": ["Ahora es nuestro secreto, ¿eh?", "Confío en vos. No sé por qué, pero confío."],
}
for k, say in ABOUT.items():
    hub(f"talk.about.{k}", say, [
        answer("more", "¿En serio? Contame más", [res(f"talk.about.{k}.more", base=6, positive=1), res("talk.ask", base=1)]),
        answer("relate", "A mí me pasa algo parecido", [res("talk.react.relate", base=5, positive=1), res("talk.ask", base=2)]),
        answer("tease", "Ja, ¡qué raro!", [
            res("talk.react.laugh", base=3, cond=[pers("witty", 5), pers("peppy", 3), pers("odd", 4), pers("confident", 3)], positive=1, gesture="laugh"),
            res("talk.react.hurt", base=2, cond=[pers("sensitive", 6), pers("shy", 4), pers("grumpy", 4)], negative=1, gesture="sad"),
        ]),
    ])
    hub(f"talk.about.{k}.more", MORE[k])

# =====================================================================================================
# el aldeano te pregunta a vos
# =====================================================================================================

node("talk.ask", answers=[answer("", None, [
    res("talk.ask.origin", base=2), res("talk.ask.travel", base=2), res("talk.ask.food", base=2),
    res("talk.ask.dream", base=2), res("talk.ask.love", base=0, cond=[hearts_min(25, 3)]),
])], auto=True, silent=True)
node("talk.ask.origin", ["Che, y vos, ¿de dónde sos?", "Nunca te pregunté... ¿de dónde venís?"], [
    answer("far", "De muy lejos, más allá de las montañas", [res("talk.ask.react.wow", base=1, positive=1, gesture="surprised")]),
    answer("here", "De acá nomás, de por la zona", [res("talk.ask.react.neighbor", base=1, positive=1)]),
    answer("secret", "Es un secreto...", [res("talk.ask.react.mystery", base=3, gesture="think"), res("talk.react.annoyed", base=1, cond=[pers("grumpy", 4)])]),
])
node("talk.ask.travel", ["¿Viajaste mucho? Tenés cara de haber visto cosas.", "¿Es verdad que los aventureros duermen en cuevas?"], [
    answer("yes", "¡Un montón! Vi el Nether y todo", [res("talk.ask.react.wow", base=1, positive=1, gesture="surprised")]),
    answer("little", "Un poco, pero quiero ver más", [res("talk.ask.react.adventure", base=1, positive=1)]),
    answer("home", "Prefiero quedarme acá", [res("talk.ask.react.neighbor", base=1, positive=1, gesture="hum")]),
])
node("talk.ask.food", ["¿Y a vos qué comida te gusta?", "Pregunta importante: ¿pan o torta?"], [
    answer("bread", "Pan recién hecho, sin dudas", [res("talk.ask.react.same", base=1, positive=1, gesture="clap")]),
    answer("cake", "Torta, siempre torta", [res("talk.ask.react.same", base=1, positive=1, gesture="cheer")]),
    answer("fish", "Pescado asado", [res("talk.ask.react.ok", base=1)]),
])
node("talk.ask.dream", ["¿Qué querés hacer con tu vida, %1$s?", "Si pudieras ser lo que quisieras, ¿qué serías?"], [
    answer("hero", "Proteger a todos los pueblos", [res("talk.ask.react.hero", base=1, positive=2, gesture="clap")]),
    answer("builder", "Construir el castillo más grande", [res("talk.ask.react.wow", base=1, positive=1)]),
    answer("family", "Tener una familia y una casa linda", [res("talk.ask.react.sweet", base=1, positive=2, gesture="shy")]),
])
node("talk.ask.love", ["Oye... ¿y vos tenés a alguien especial?", "¿Hay alguien que te robe el corazón, %1$s?"], [
    answer("you", "Capaz que sí... y lo tengo enfrente", [
        res("talk.ask.react.blush", base=3, cond=[hearts_min(50, 8), pers("flirty", 4)], positive=3, gesture="shy"),
        res("talk.ask.react.awkward", base=2, cond=[hearts_max(30, 5)], gesture="surprised"),
    ], "adult"),
    answer("no", "No, por ahora no", [res("talk.ask.react.ok", base=1)]),
    answer("secret", "Eso es un secreto", [res("talk.ask.react.mystery", base=1, gesture="think")]),
])
hub("talk.ask.react.wow", ["¡Guau! Qué envidia, de verdad.", "¿En serio? ¡Tenés que contarme todo algún día!"])
hub("talk.ask.react.neighbor", ["¡Ah, mirá! Entonces somos casi vecinos.", "Qué lindo. Es bueno tener a alguien de acá."])
hub("talk.ask.react.mystery", ["Ah, ¿te hacés el misterioso? Me gusta, ja.", "Bueno, bueno... algún día me lo vas a contar."])
hub("talk.ask.react.adventure", ["Si algún día te vas de viaje, traeme un recuerdo, ¿sí?", "Ojalá yo tuviera tu valentía."])
hub("talk.ask.react.same", ["¡Sabía que teníamos buen gusto!", "¡Eso! Vos sí que sabés."])
hub("talk.ask.react.ok", ["Mm, bueno, cada uno con lo suyo, ja.", "Ah, ¡mirá vos!"])
hub("talk.ask.react.hero", ["Eso es muy noble. El mundo necesita gente así.", "Con vos cerca me siento más seguro."])
hub("talk.ask.react.sweet", ["Ay... qué lindo. Te lo merecés.", "Yo también quiero eso. Algún día."])
hub("talk.ask.react.blush", ["¿Q-qué? ¿Yo? Ay, no sé qué decir...", "No me digas esas cosas que me pongo colorado...", "*se tapa la cara* Sos tremendo, %1$s."])
hub("talk.ask.react.awkward", ["Ah... eh... ¡mirá qué lindo está el cielo hoy!", "Ja, ja... qué gracioso. Muy gracioso."])

# =====================================================================================================
# trabajo (con variantes por profesion)
# =====================================================================================================

hub("talk.work", [
    "Bien, aunque hay días que no paro ni para comer.",
    "Mucho trabajo, como siempre. Pero me gusta lo que hago.",
    "Tranquilo esta semana. Raro, ¿no?",
    "Agotador, pero bueno, alguien tiene que hacerlo.",
], [
    answer("help", "¿Te puedo ayudar en algo?", [res("talk.work.help", base=1, positive=2, gesture="cheer")]),
    answer("proud", "Hacés un trabajo increíble", [
        res("talk.work.proud", base=4, positive=2, gesture="shy"),
        res("talk.compliment.again", base=0, fatigue=True),
    ]),
    answer("boring", "Suena medio aburrido...", [
        res("talk.react.laugh", base=2, cond=[pers("witty", 4), pers("lazy", 6)], gesture="laugh"),
        res("talk.work.offended", base=3, cond=[pers("confident", 3), pers("grumpy", 4), pers("sensitive", 3)], negative=1, gesture="angry"),
    ]),
])
PROF = {
    "farmer": ["Las zanahorias vienen hermosas este año. ¡Mirá qué tierra negrita!", "Los cuervos me roban las semillas, pero yo soy más vivo."],
    "fisherman": ["Ayer saqué un pescado así de grande. Bueno... así. Bueno, era chiquito.", "El río está generoso esta semana."],
    "librarian": ["Estoy ordenando los libros por color. Nadie me pidió, pero queda precioso.", "Encontré un libro de hechizos viejísimo. ¡Huele a historia!"],
    "cleric": ["Mucha gente viene a pedir consejo últimamente. Escuchar también es un trabajo.", "Las pociones no se hacen solas, ¿sabías?"],
    "armorer": ["Estoy terminando una cota de malla que va a parar hasta a un dragón. Bueno, capaz no.", "El fuego de la fragua no me deja ni un minuto."],
    "weaponsmith": ["Estoy forjando una espada nueva. El filo tiene que quedar perfecto.", "Mis hachas son las mejores del pueblo, y no lo digo yo... bueno, sí, lo digo yo."],
    "toolsmith": ["Arreglé como diez picos hoy. ¿Qué hacen con ellos, se los comen?", "Una buena herramienta dura toda la vida."],
    "butcher": ["Hoy hay chuletas frescas. Si querés, te guardo unas.", "El secreto de una buena carne es la paciencia."],
    "leatherworker": ["Estoy haciendo un sombrero de cuero nuevo. Te va a quedar pintado.", "El cuero bien curtido dura años."],
    "mason": ["Estoy tallando una piedra para la plaza. Va a quedar hermosa.", "El que sabe de piedras sabe de paciencia."],
    "shepherd": ["Mis ovejas están más esponjosas que nunca.", "Le puse nombre a cada oveja. La blanca se llama Nube. Bueno, todas son blancas."],
    "cartographer": ["Estoy dibujando un mapa de los alrededores. Hay un lugar que no sé qué es...", "Cada mapa es una aventura que todavía no viví."],
    "fletcher": ["Estoy emplumando flechas. Es más difícil de lo que parece.", "Una flecha derecha es una flecha feliz."],
    "guard": ["Mientras yo esté de guardia, nadie va a molestar al pueblo.", "Anoche escuché zombis cerca del muro. Ni se acercaron, ja."],
    "none": ["¿Trabajo? Ja. Estoy... buscando mi vocación.", "Hoy trabajé de mirar las nubes. Muy cansador."],
    "nitwit": ["¿Trabajo? Yo soy el que alegra el pueblo. Eso cuenta, ¿no?", "Me ofrecieron trabajo pero estaba ocupado mirando una mariposa."],
}
for prof, say in PROF.items():
    lines(f"{prof}.dialogue.talk.work", *say)
hub("talk.work.help", ["¡Ay, gracias! Con que me acompañes un rato ya me ayudás.", "¿En serio? Sos un amor. Otro día te tomo la palabra.", "Gracias. No muchos se ofrecen, ¿sabés?"])
hub("talk.work.proud", ["¿De verdad lo pensás? Gracias... me pone muy contento.", "Ay, basta, que me pongo colorado.", "Eso me motiva un montón. ¡Gracias!"])
hub("talk.work.offended", ["¿Aburrido? Quiero verte a vos haciéndolo.", "Mi trabajo es muy importante, para que sepas.", "Hmpf. Cada uno sabe lo que vale lo suyo."])

# =====================================================================================================
# el pueblo
# =====================================================================================================

node("talk.village", answers=[answer("", None, [
    res("talk.village.gossip", base=3, cond=[pers("peppy", 3), pers("flirty", 2)]),
    res("talk.village.love", base=2, cond=[pers("flirty", 3)]),
    res("talk.village.trouble", base=2, cond=[pers("gloomy", 3), pers("grumpy", 3)]),
    res("talk.village.event", base=2, cond=[pers("friendly", 2), pers("athletic", 2)]),
])], auto=True, silent=True)
village_ans = [
    answer("really", "¿De verdad? ¡Contame!", [res("talk.village.more", base=4, positive=1, gesture="talk"), res("talk.ask", base=1)]),
    answer("help", "¿Puedo ayudar en algo?", [res("talk.village.help", base=1, positive=1)]),
    answer("dont", "No está bien hablar de otros...", [
        res("talk.village.ashamed", base=3, cond=[pers("shy", 4), pers("sensitive", 3), pers("friendly", 2)], gesture="shy"),
        res("talk.village.defensive", base=2, cond=[pers("peppy", 3), pers("grumpy", 3), pers("confident", 3)], gesture="hips"),
    ]),
]
hub("talk.village.gossip", [
    "¿Viste al herrero? Dicen que le cocina pasteles a la panadera todas las mañanas...",
    "No sé si debería contarte, pero... el bibliotecario se quedó dormido en el pozo. ¡En el pozo!",
    "Dicen que alguien anda sacando zanahorias de la huerta de noche. Yo tengo mis sospechas...",
    "¿Sabías que el pastor le habla a sus ovejas como si fueran personas? ¡Y les canta!",
], village_ans)
hub("talk.village.love", [
    "Te tengo un chisme: dos de los vecinos se tomaron de la mano en la plaza ayer...",
    "Creo que el pescador está enamorado. Anda suspirando todo el día.",
    "Hay romance en el aire, %1$s. Se nota en cómo se miran algunos.",
], village_ans)
hub("talk.village.trouble", [
    "Las noches están cada vez más peligrosas. Escuché arañas cerca de las casas.",
    "Hubo una pelea en el mercado. Por una papa. Una papa, %1$s.",
    "Algunos dicen que vieron saqueadores cerca del bosque. Me da miedo.",
], village_ans)
hub("talk.village.event", [
    "¡Dicen que va a haber una fiesta en la plaza! Con música y todo.",
    "Están por construir una casa nueva al lado del pozo. ¡El pueblo crece!",
    "Mañana hay mercado grande. Vienen comerciantes de lejos.",
], village_ans)
hub("talk.village.more", ["Ja, y eso no es nada... pero mejor me callo, que después me retan.", "¡Eso es todo lo que sé! Te juro. Bueno, casi todo.", "No le digas a nadie que te conté, ¿eh?"])
hub("talk.village.help", ["Con que nos cuides de los monstruos ya es muchísimo.", "Si traés algo de comida para la fiesta, te van a amar.", "Ayudá a los más chicos, que siempre andan solos por ahí."])
hub("talk.village.ashamed", ["Tenés razón... perdón. No sé por qué lo dije.", "Uy. Sí, es verdad. Qué vergüenza."])
hub("talk.village.defensive", ["¡Ay, no es chisme, es información!", "Bueno, bueno, no me mires así."])

# =====================================================================================================
# el dia (segun la hora)
# =====================================================================================================

node("talk.day", answers=[answer("", None, [
    res("talk.day.morning", base=0, cond=[{"chance": 10, "time_max": 5000}]),
    res("talk.day.afternoon", base=0, cond=[{"chance": 10, "time_min": 5000, "time_max": 11500}]),
    res("talk.day.night", base=0, cond=[{"chance": 10, "time_min": 11500}]),
    res("talk.day.afternoon", base=1),
])], auto=True, silent=True)
hub("talk.day.morning", ["¡Recién empieza! Desayuné como un rey.", "Me levanté temprano para ver el amanecer. Valió la pena.", "Todavía tengo sueño, ja. ¿Vos dormiste bien?"])
hub("talk.day.afternoon", ["Bien, ya hice la mitad de las cosas. La otra mitad... mañana, ja.", "El sol está fuerte hoy. Ideal para una siesta.", "Tranquilo. Charlé con los vecinos y comí rico."])
hub("talk.day.night", ["Ya es tarde... cuidado con los monstruos al volver.", "Me encanta la noche, se ven todas las estrellas.", "Estoy por irme a dormir. Fue un día largo."])

# =====================================================================================================
# halagos
# =====================================================================================================

hub("talk.compliment.happy", ["¿Ah, sí? Ay, gracias, %1$s...", "Ja, ¿lo decís en serio? Me alegraste el día.", "No me digas eso que me pongo colorado.", "¡Gracias! Hoy me peiné distinto, ¿se nota?"])
hub("talk.compliment.flirty", ["Vos tampoco estás nada mal, ¿eh?", "Ya lo sé... pero me gusta escucharlo de vos.", "Seguí así y me vas a conquistar."])
hub("talk.compliment.suspicious", ["Mmm... ¿qué querés pedirme?", "¿Y eso? Algo tramás vos.", "Gracias, supongo. ¿Viniste a venderme algo?"])
hub("talk.compliment.again", ["Ya me lo dijiste, ja. ¿No tenés otra cosa para decir?", "Bueno, ya, ya entendí.", "Si me lo repetís tanto, pierde la gracia."])

# =====================================================================================================
# romance
# =====================================================================================================

love_ans = [
    answer("confess", "Me gustás mucho", [
        res("talk.love.yes", base=1, cond=[hearts_min(60, 12), pers("flirty", 4)], positive=4, gesture="shy"),
        res("talk.love.maybe", base=3, cond=[hearts_min(30, 3)], positive=1, gesture="think"),
        res("talk.love.no", base=2, cond=[hearts_max(25, 6), pers("odd", 3)], gesture="no"),
    ]),
    answer("walk", "¿Damos un paseo de la mano?", [
        res("talk.love.walk_yes", base=1, cond=[hearts_min(35, 10), rel("spouse", 20), rel("engaged", 20)], positive=1, attach="hand"),
        res("talk.love.walk_no", base=2, cond=[hearts_max(20, 6)]),
    ]),
    answer("memory", "¿Te acordás de cuando nos conocimos?", [res("talk.love.memory", base=1, positive=2, gesture="hum")]),
    answer("future", "¿Cómo te imaginás el futuro?", [res("talk.love.future", base=1, positive=1, gesture="think")]),
    answer("kiss", "Vení, dame un beso", [res("kiss", base=1)]),
]
hub("talk.love.friend", ["¿Qué cosa? Me dejás con intriga...", "¿Algo importante? Decime.", "Te escucho... ¿qué pasa?"], love_ans)
hub("talk.love.crush", ["¿S-sí? ¿Qué me querías decir?", "Ay... ¿qué es? Me estás poniendo nervioso.", "Te escucho, %1$s. Con toda mi atención."], love_ans)
hub("talk.love.lover", ["Decime, mi vida. Lo que sea.", "Mm... ¿algo lindo, espero?", "Cuando me mirás así me derrito, ¿sabés?"], love_ans)
hub("talk.love.spouse", ["¿Qué pasa, mi amor?", "Decime, cariño. Acá estoy.", "¿Otra vez vas a decirme que me amás? Ja, decilo, me encanta."], love_ans)
hub("talk.love.yes", ["... Vos también me gustás. Hace rato, la verdad.", "¡Ay! No sabés cuánto esperé que me dijeras eso.", "*se pone colorado* Yo... también. Mucho."])
hub("talk.love.maybe", ["Ay... no sé qué decir. Dame un poco de tiempo, ¿sí?", "Me hace muy feliz escucharlo... pero vamos despacito.", "Sos muy especial para mí. Veamos qué pasa."])
hub("talk.love.no", ["Ah... perdón, %1$s. Yo te veo como un amigo.", "Uy... no me lo esperaba. Creo que no siento lo mismo.", "Mejor seamos amigos, ¿sí? No quiero que nada cambie."])
hub("talk.love.walk_no", ["Mmm... hoy no, perdón.", "Capaz otro día, cuando nos conozcamos más."])
TEXT["dialogue.talk.love.walk_yes/1"] = "¡Me encantaría! Vamos... no me sueltes, ¿eh?"
TEXT["dialogue.talk.love.walk_yes/2"] = "*te toma la mano* Vamos a donde quieras."
TEXT["dialogue.talk.love.walk_yes/3"] = "Qué lindo plan. Vamos despacito."
hub("talk.love.memory", ["¡Claro que me acuerdo! Estabas todo despeinado, ja.", "Como si fuera ayer. Me pareciste muy raro... y muy lindo.", "Ese día cambió todo, %1$s."])
hub("talk.love.future", ["Me imagino una casa con un jardín... y vos ahí, conmigo.", "No sé, pero mientras estés vos, va a estar bien.", "Chicos corriendo por todos lados y muchas noches de estrellas."])

# =====================================================================================================
# variantes por personalidad (MCA las prefiere si existen; con idioma español tambien, ver MCAClient)
# =====================================================================================================

P = {
    "shy": {
        "talk.open.neutral": ["Eh... h-hola, %1$s. ¿Querías hablar conmigo?", "Ah... hola. Perdón, me asustaste un poquito."],
        "talk.open.happy": ["H-hola... hoy estoy contento, ¿se nota?", "Ay, hola... qué lindo que viniste."],
        "talk.more": ["¿Y... qué más? Si querés, claro.", "Me gusta escucharte... sigue, digo, seguí."],
        "talk.bye": ["Chau... ¿volvés pronto?", "B-bueno, chau. Me gustó mucho hablar."],
        "talk.compliment.happy": ["¿Y-yo? No... bueno, gracias...", "*se tapa la cara* No digas esas cosas..."],
        "talk.how.good": ["Bien... creo que bien. Sí.", "Contento, pero no le digas a nadie, ja."],
    },
    "flirty": {
        "talk.open.neutral": ["Mirá quién vino a verme... ¿me extrañabas?", "Hola, lindura. ¿Viniste por mí?"],
        "talk.open.happy": ["¡Hoy estoy radiante! Y ahora que llegaste vos, más.", "Justo pensaba en alguien... y apareciste."],
        "talk.more": ["Seguí hablando, me encanta tu voz.", "¿Y qué más? No te vayas tan rápido."],
        "talk.bye": ["¿Ya te vas? Pensá en mí, ¿eh?", "Chau, bombón. Te espero."],
        "talk.compliment.happy": ["Ya lo sé, pero decímelo otra vez.", "Vos sí que sabés cómo hablarme."],
        "talk.how.good": ["Divina, como siempre.", "Mejor ahora que te veo."],
    },
    "grumpy": {
        "talk.open.neutral": ["¿Otra vez vos? ¿Qué querés ahora?", "Hmpf. Hablá rápido."],
        "talk.open.happy": ["Hoy no estoy de mal humor. No te acostumbres.", "Bueno, bueno. Hoy te escucho."],
        "talk.more": ["¿Algo más o ya terminaste?", "Seguí, ya que estás."],
        "talk.bye": ["Por fin. Chau.", "Sí, sí, andá nomás."],
        "talk.compliment.happy": ["Hmpf. Gracias. Supongo.", "No me vas a ablandar con eso... bueno, un poco."],
        "talk.how.good": ["Bien. ¿Contento?", "No me quejo. Hoy."],
    },
    "peppy": {
        "talk.open.neutral": ["¡HOLA, %1$s! ¡¿Charlamos?! ¡Dale, dale!", "¡Ay, qué alegría verte! ¡Tengo un montón para contarte!"],
        "talk.open.happy": ["¡Hoy es el MEJOR día de la historia!", "¡Llegaste! ¡Justo cuando estaba más feliz!"],
        "talk.more": ["¡¿Y qué más?! ¡Contame todo!", "¡Me encanta esto! ¡Seguí, seguí!"],
        "talk.bye": ["¡Chau, chau! ¡Volvé YA!", "¡Nos vemos! ¡Fue genial!"],
        "talk.compliment.happy": ["¡AY! ¡Gracias! ¡Vos también!", "¡Me hiciste el día! ¡Bueno, la semana!"],
        "talk.how.good": ["¡SÚPER BIEN! ¿Y vos? ¿Y vos?", "¡Re contento! ¡Hasta canté en el desayuno!"],
    },
    "gloomy": {
        "talk.open.neutral": ["Hola... seguro viniste a darme una mala noticia.", "Ah. Hola. Igual nada importa mucho, ¿no?"],
        "talk.open.happy": ["Hoy no está tan mal. Seguro mañana empeora.", "Mirá, un día decente. Qué raro."],
        "talk.more": ["¿Algo más? Si es malo, ya lo sabía.", "Seguí... total."],
        "talk.bye": ["Chau. Seguro no nos vemos más. Ja... chiste.", "Cuidate. El mundo es peligroso."],
        "talk.compliment.happy": ["¿Bien? ¿Yo? Debe ser la luz.", "Gracias... no me lo esperaba."],
        "talk.how.good": ["Bien, por ahora.", "Mejor que ayer. Que no es mucho decir."],
    },
    "witty": {
        "talk.open.neutral": ["¡%1$s! Justo me sé un chiste nuevo. Bueno, primero charlemos.", "Hola. Te iba a saludar con un chiste, pero el chiste sos vos. Mentira, ja."],
        "talk.open.happy": ["¡Hoy estoy tan contento que hasta me río de mis propios chistes!", "¡Buenas! Hoy el sol salió solo para mí."],
        "talk.more": ["¿Más? Soy un pozo de sabiduría. Un pozo seco, pero pozo.", "Seguí, que esto se pone bueno."],
        "talk.bye": ["¡Chau! No te tropieces con una gallina.", "Nos vemos. O no, depende de la niebla."],
        "talk.compliment.happy": ["Es que me baño los martes. Hoy es martes.", "Lo sé, es un don natural."],
        "talk.how.good": ["Como pez en el agua. Mojado, pero bien.", "¡De diez! Bueno, de ocho y medio."],
    },
    "friendly": {
        "talk.open.neutral": ["¡Amigo! ¿Cómo andás? ¡Qué alegría!", "¡Hola, %1$s! Siempre es un gusto verte."],
        "talk.open.happy": ["¡Hola! Hoy quiero abrazar a todo el pueblo.", "¡Qué lindo día y qué linda compañía!"],
        "talk.more": ["Contame lo que quieras, para eso están los amigos.", "¡Me encanta charlar con vos!"],
        "talk.bye": ["¡Chau, amigo! Cuidate mucho.", "Andá tranqui. Acá tenés tu casa."],
        "talk.compliment.happy": ["¡Gracias! Vos siempre tan amable.", "Ay, ¡qué lindo que digas eso!"],
        "talk.how.good": ["¡Muy bien! Y mejor ahora.", "¡Bárbaro! ¿Y vos? Decime la verdad."],
    },
    "confident": {
        "talk.open.neutral": ["Ah, %1$s. Elegiste bien con quién hablar.", "Hola. Sí, yo también me alegro de verme."],
        "talk.open.happy": ["Hoy estoy imparable. ¿Querías algo?", "Día perfecto. Como yo."],
        "talk.more": ["Seguí, te escucho. Tengo tiempo para vos.", "¿Y? Sorprendeme."],
        "talk.bye": ["Andá. Ya vas a volver, siempre vuelven.", "Chau. No me extrañes demasiado."],
        "talk.compliment.happy": ["Lo sé. Pero gracias por notarlo.", "Por fin alguien con buen ojo."],
        "talk.how.good": ["Excelente, como siempre.", "Mejor que nunca."],
    },
    "athletic": {
        "talk.open.neutral": ["¡Ey! Recién termino de correr tres vueltas al pueblo. ¿Qué contás?", "¡Hola! ¿Hacemos unas sentadillas mientras charlamos? Ja, mentira."],
        "talk.open.happy": ["¡Hoy tengo energía para escalar una montaña!", "¡Hola! Me siento más fuerte que un gólem."],
        "talk.more": ["¡Dale, seguí, que no me canso!", "¿Qué más? Estoy re activo."],
        "talk.bye": ["¡Chau! Te ganaría corriendo hasta allá, ¿eh?", "¡Nos vemos! Estirá antes de correr."],
        "talk.compliment.happy": ["¡Es el entrenamiento! Todos los días.", "¡Gracias! Comer bien y moverse, ese es el secreto."],
        "talk.how.good": ["¡Con todo! Hice ejercicio a la mañana.", "¡Bien! Me duele todo, pero bien."],
    },
    "lazy": {
        "talk.open.neutral": ["Mmm... hola. ¿Es muy largo lo que me vas a decir?", "*bosteza* Ah, hola, %1$s."],
        "talk.open.happy": ["Hoy dormí la siesta dos veces. Estoy feliz.", "Hola... hoy no hice nada y me encanta."],
        "talk.more": ["Seguí... yo te escucho acostadito.", "Mmm, ¿y? *bosteza*"],
        "talk.bye": ["Chau... me voy a dormir un rato.", "Bueno... yo me quedo acá, total."],
        "talk.compliment.happy": ["¿Sí? Ni me peiné, ja.", "Gracias... es que descansé mucho."],
        "talk.how.good": ["Bien... con sueño, pero bien.", "Tranqui. Muy tranqui."],
    },
    "sensitive": {
        "talk.open.neutral": ["Hola, %1$s... qué lindo que pienses en venir a verme.", "Ah, hola. Justo me estaba sintiendo un poco solo."],
        "talk.open.happy": ["Hoy estoy tan feliz que me dan ganas de llorar, ja.", "¡Hola! Hoy todo me emociona."],
        "talk.more": ["Me gusta que me tengas paciencia. Contame más.", "Sos muy dulce, ¿sabés?"],
        "talk.bye": ["Chau... no te olvides de mí, ¿sí?", "Cuidate mucho, de verdad."],
        "talk.compliment.happy": ["*se emociona* Gracias... nadie me lo dice nunca.", "Ay, me vas a hacer llorar."],
        "talk.how.good": ["Bien... muy bien, gracias por preguntar.", "Contento. Me emociona que te importe."],
    },
    "greedy": {
        "talk.open.neutral": ["Hola, %1$s. ¿Traés esmeraldas? Digo... ¿cómo estás?", "Ah, hola. ¿Viniste a comprar algo?"],
        "talk.open.happy": ["¡Hoy vendí un montón! Estoy feliz.", "Hola, hola. Hoy encontré una esmeralda en el piso."],
        "talk.more": ["Seguí... pero si me contás dónde hay oro, mejor.", "¿Y qué más? ¿Algo que valga la pena?"],
        "talk.bye": ["Chau. La próxima traé algo, ¿eh?", "Nos vemos. Acordate de mi tienda."],
        "talk.compliment.happy": ["Gracias. ¿Eso cuesta algo?", "¡Lo sé! Y eso que esta ropa fue barata."],
        "talk.how.good": ["Bien. Con más esmeraldas estaría mejor.", "Bien, el negocio anda."],
    },
    "odd": {
        "talk.open.neutral": ["Hola. ¿Sabías que las gallinas sueñan? Yo sí.", "¡%1$s! Te estaba esperando. Bueno, no. Pero quedaba lindo decirlo."],
        "talk.open.happy": ["¡Hoy hablé con una piedra y me contestó!", "Hola. Hoy el cielo tiene un color raro. Me encanta."],
        "talk.more": ["¿Y si las vacas fueran de chocolate? Perdón, seguí.", "Mm-hm. Mm-hm. ¿Y los calamares qué opinan?"],
        "talk.bye": ["¡Chau! Saludame a la luna.", "Adiós. Cuidado con los cuadrados."],
        "talk.compliment.happy": ["Gracias. Me peiné con un tenedor.", "Vos también. Sobre todo las orejas."],
        "talk.how.good": ["Bien. Encontré un hongo que tiene cara.", "¡Bien! Hoy caminé para atrás todo el día."],
    },
}
for p, keys in P.items():
    for k, say in keys.items():
        lines(f"{p}.dialogue.{k}", *say)


# =====================================================================================================

def write():
    os.makedirs(DIALOGUES, exist_ok=True)
    for qid, data in NODES.items():
        with open(os.path.join(DIALOGUES, qid + ".json"), "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    for lang in LANGS:
        path = os.path.join(LANG_DIR, lang + ".json")
        cur = {}
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                cur = json.load(f)
        if lang == "en_us":
            # en ingles solo se completa lo que falta (para que nada salga como "dialogue.talk...")
            for k, v in TEXT.items():
                cur.setdefault(k, v)
        else:
            cur.update(TEXT)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(cur, f, ensure_ascii=False, indent=2)
    print(len(NODES), "nodos,", len(TEXT), "textos")


write()
