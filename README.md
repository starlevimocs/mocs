# Dreams (mod de diálogos)

Mod para **Forge 1.20.1** con diálogos estilo novela visual (retratos, texto letra por letra y voces de bips) para el servidor Dreams. No depende de ningún otro mod.

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
  "portraits": { "neutral": "dreams:textures/gui/portraits/tree_neutral.png" },
  "voice": { "sound": "minecraft:block.note_block.didgeridoo", "pitch": 0.75, "variance": 0.08, "volume": 0.7 }
}
```

- `portraits`: un PNG por expresión. Si no existe la imagen, se muestra un recuadro de color con la inicial. Si una expresión no tiene retrato, usa `neutral`.
- `voice`: el sonido que se repite por cada letra, con tono base y variación aleatoria. Sirve cualquier sonido de Minecraft; para sonidos propios, agregá un `.ogg` y declaralo en `assets/dreams/sounds.json`.

### Diálogos: `dialogues/<nombre>.json`

Un diálogo es un grafo de nodos. Cada nodo es una línea de un personaje:

| Campo | Qué hace |
|---|---|
| `speaker` | Personaje (`dreams:tree` apunta a `speakers/tree.json`). |
| `expression` | Retrato a mostrar (por defecto `neutral`). |
| `text` | La línea. Entran unas 5 líneas de texto en la caja. |
| `next` | Nodo siguiente. `null` o ausente: termina la conversación. |
| `choices` | Lista de opciones `{ "text", "next" }`. Si existe, se ignora `next`. |

El texto hace pausas en `,` `;` `:` y pausas largas en `.` `!` `?` y `…`.

## Estructura

```
src/main/java/com/starlevi/dreams/
  DreamsMod.java              entrada del mod y comando /dreams
  network/                    paquete servidor -> cliente que abre el diálogo
  dialogue/                   modelos (Dialogue, Speaker)
  client/data/                cargadores de JSON (cliente)
  client/screen/              DialogueScreen: la pantalla de novela visual
```

## Próximos pasos

- Abrir diálogos al hacer clic derecho en una entidad (de cualquier mod).
- Marcas de progreso por jugador (`set_flag`) y diálogos que cambian según ellas.
- Modo "globo" para la pelea final, sin interrumpir el combate.
- Retratos y bips propios para cada guardián.
