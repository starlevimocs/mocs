# Dreams (mod de diálogos)

Mod para **Forge 1.20.1** con diálogos estilo novela visual (texto letra por letra y voces de bips), cinemáticas y control de spawn para el servidor Dreams. No depende de ningún otro mod.

## Compilar

1. Copiá `gradlew`, `gradlew.bat` y la carpeta `gradle/` del MDK de Forge 1.20.1 a la raíz del proyecto (o ejecutá `gradle wrapper` si tenés Gradle instalado).
2. Necesitás **Java 17**.
3. `./gradlew build` genera el `.jar` en `build/libs/`. Copialo a la carpeta `mods` de tu instancia.
4. Para probar sin instalar: `./gradlew runClient`.

La versión de Forge se cambia en `gradle.properties` (`forge_version`).

## Probar el primer diálogo

Dentro del juego, con permisos de operador:

```
/dreams dialogue dreams:tree_intro
/dreams dialogue dreams:tree_intro @a
```

Controles: clic izquierdo, `Espacio` o `Enter` para avanzar o saltar el efecto de escritura; `Esc` para cerrar.

## Cómo escribir diálogos

Todo vive en `src/main/resources/assets/dreams/` (también funciona en un resource pack con la misma estructura, y se recarga con `F3 + T`).

### Personajes: `speakers/<nombre>.json`

```json
{
  "name": "Árbol Madre",
  "color": "#7FD99A",
  "voice": { "sound": "minecraft:block.note_block.didgeridoo", "pitch": 0.75, "variance": 0.08, "volume": 0.7 }
}
```

- `voice`: el sonido que se repite por cada letra, con tono base y variación aleatoria. Sirve cualquier sonido de Minecraft; para sonidos propios, agregá un `.ogg` y declaralo en `assets/dreams/sounds.json`.

### Diálogos: `dialogues/<nombre>.json`

Un diálogo es un grafo de nodos. Cada nodo es una línea de un personaje:

| Campo | Qué hace |
|---|---|
| `cinematic` | (a nivel del diálogo, junto a `start`) nombre de una cinemática que se reproduce mientras dura el diálogo. |
| `speaker` | Personaje (`dreams:tree` apunta a `speakers/tree.json`). |
| `expression` | Reservado para más adelante (por ahora no se muestra). |
| `text` | La línea. Entran unas 5 líneas de texto en la caja. |
| `next` | Nodo siguiente. `null` o ausente: termina la conversación. |
| `choices` | Lista de opciones `{ "text", "next" }`. Si existe, se ignora `next`. |

El texto hace pausas en `,` `;` `:` y pausas largas en `.` `!` `?` y `…`.

## Cinemáticas

Una cámara que se mueve sola mientras los jugadores miran. Se reproduce para **todos a la vez** (o para quienes elijas). Durante la cinemática el jugador no se mueve, no recibe daño, no ve el HUD y aparecen barras negras de cine. Con **Esc** cada jugador puede saltarla.

Todo se maneja con comandos de operador, y las cinemáticas se guardan en el mundo.

### Camino por puntos

Parate donde empieza la cámara, mirando hacia donde querés, y marcá el primer punto. Después andá al siguiente lugar y marcá otro, y así.

```
/dreams cinematic point add hoja 3     # punto 1 (el tiempo no cuenta en el primero)
/dreams cinematic point add hoja 4     # punto 2: la cámara tarda 4 s en llegar acá
/dreams cinematic point add hoja 6     # punto 3: tarda 6 s
/dreams cinematic preview hoja         # verla solo vos
/dreams cinematic play hoja            # verla todos
```

La cámara pasa por todos los puntos con una curva suave, y también interpola hacia dónde mirás. `point undo <nombre>` quita el último punto y `point clear <nombre>` los borra todos.

### Órbita (dar vueltas alrededor de algo)

Mirá el bloque central (por ejemplo, la base del Árbol Madre) y escribí:

```
/dreams cinematic orbit tree_orbit <radio> <segundos> [altura] [grados] [alturaMirada]
/dreams cinematic orbit tree_orbit 25 30 12 360 15
```

Da una vuelta completa (360°) a 25 bloques de distancia, 12 bloques por encima, mirando 15 bloques arriba del centro (la copa).

### Con un diálogo

Poné el nombre en el JSON del diálogo, junto a `start`:

```json
{ "cinematic": "tree_orbit", "start": "caida", "nodes": { } }
```

Mientras el diálogo está abierto la cinemática se repite en bucle, y se corta cuando se cierra. Si la cinemática no existe, el diálogo funciona igual, sin cámara.

### Encadenar cosas

`onend` ejecuta un comando cuando la cinemática termina, por ejemplo para abrir el diálogo del árbol justo después de ver nacer a los jugadores:

```
/dreams cinematic onend hoja dreams dialogue dreams:tree_intro @a
```

### Resto de comandos

| Comando | Qué hace |
|---|---|
| `play <nombre> [jugadores]` | La ven todos, o los que elijas (`@a`, un nombre...) |
| `preview <nombre>` | Solo para vos |
| `stop [jugadores]` | Corta la cinemática |
| `hideplayers <nombre> true` | Oculta a los jugadores mientras dura |
| `onend <nombre> <comando>` / `onend <nombre> clear` | Comando al terminar |
| `list`, `info <nombre>`, `remove <nombre>` | Consultar y borrar |

### Límites

- La cámara solo ve lo que el cliente tiene cargado, así que conviene que la cinemática transcurra cerca de los jugadores (dentro de su distancia de renderizado).
- Los puntos se marcan con comandos; una varita para marcarlos con clic puede venir después.

## Control de spawn natural (varita)

Un panel de administración para decidir qué mobs aparecen de forma natural, en todo el mundo o solo en zonas. Solo lo pueden usar operadores (nivel 2).

**Conseguir la varita:** `/dreams spawn wand` (o `/give @s dreams:spawn_wand`).

| Acción | Qué hace |
|---|---|
| Clic izquierdo en un bloque | Marca el punto A (no rompe el bloque) |
| Clic derecho en un bloque | Marca el punto B |
| Clic derecho al aire, o agachado + clic derecho | Abre el menú |
| `/dreams spawn` | Abre el menú sin varita |

### Cómo decide el spawn

Cada mob (o cada mod entero) tiene un modo:

- **Natural**: aparece en todo el mundo, como lo haría su mod.
- **Solo zonas**: aparece únicamente dentro de las zonas donde lo permitas.
- **Nunca**: no aparece en ningún lado, ni siquiera en zonas.
- **Auto** (sin regla): los animales de vanilla (vacas, ovejas, peces, etc.) aparecen normal; todo lo demás queda en "Solo zonas", o sea bloqueado hasta que lo habilites en una zona. Los caballos de *Icy's Better Horses* vienen como Natural.

La regla de un mob tiene prioridad sobre la de su mod.

### Zonas

- **Caja A-B**: entre los dos puntos de la varita. Con "Altura completa" activada, ocupa de la capa más baja a la más alta del mundo.
- **Bioma**: toma el bioma donde estás parado.

Dentro de cada zona elegís qué mobs o qué mods enteros pueden aparecer.

### Alcance y límites

- Controla los spawns **naturales y de generación de chunks**. No toca spawners, huevos, comandos, eventos (aldeanos, incursiones) ni estructuras.
- Un mod que haga aparecer mobs por su cuenta, sin pasar por el sistema normal de Forge, no se puede filtrar.
- La configuración se guarda en el mundo (`data/dreams_spawn.dat`) y se aplica al instante.
- Todos los jugadores necesitan el mod instalado.

## Estructura

```
src/main/java/com/starlevi/dreams/
  DreamsMod.java              entrada del mod y comando /dreams
  network/                    paquete servidor -> cliente que abre el diálogo
  dialogue/                   modelos (Dialogue, Speaker)
  spawn/                      reglas, zonas, persistencia y evento de spawn
  cinematic/                  cinemáticas: datos, camino, comandos y lado servidor
  item/                       varita de spawn
  client/data/                cargadores de JSON (cliente)
  client/screen/              DialogueScreen y SpawnControlScreen
  client/CinematicClient.java la cámara de las cinemáticas
```

## Próximos pasos

- Abrir diálogos al hacer clic derecho en una entidad (de cualquier mod).
- Marcas de progreso por jugador (`set_flag`) y diálogos que cambian según ellas.
- Modo "globo" para la pelea final, sin interrumpir el combate.
- Retratos y bips propios para cada guardián.
