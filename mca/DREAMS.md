# MCA de Dreams

Fork de [Minecraft Comes Alive](https://github.com/Luke100000/minecraft-comes-alive) 7.6.26+1.20.1 para el servidor Dreams.
Se distribuye bajo la misma licencia (GPL-3.0, ver `LICENSE`). Compila solo la version de Forge.

## Cambios respecto al original

- **Conversacion estilo novela visual** (`client/gui/InteractScreen.java`, `VnStyle.java`): caja abajo con el nombre
  del aldeano, texto letra por letra, respuestas numeradas arriba de la caja (clic o teclas 1-9), espacio/clic para
  completar el texto. La ficha del aldeano (profesion, animo, personalidad, rasgos) pasa a un panel.
- **Botones del menu** con el mismo estilo (`AbstractDynamicScreen.ButtonEx`).
- **Voces** (`client/dreams/VillagerVoice.java`, sonidos en `assets/mca/sounds/dreams/voice`, generados con
  `tools/make_voices.py`): reemplazan al TTS. Cada silaba suena con su vocal sintetizada; el tono cae durante la
  frase, sube al final de las preguntas y cambia con la emocion (feliz, triste, enojado). Cada aldeano tiene su voz
  fija (genero, edad, gen de tono y su UUID). Config: `enableBipVoices`.
- **Animaciones con EMF / Fresh Animations** (`VillagerEntityMCARenderer`, `VillagerEntityBaseModelMCA`,
  `client/dreams/EmfBridge.java`): el cuerpo del aldeano usa la capa de modelo del jugador, asi EMF le aplica las
  animaciones del jugador. MCA mantiene lo suyo: bebes, panico, pechos y segunda capa de ropa siguiendo al cuerpo.
  Config: `useAnimatedPlayerModel` (en `false` vuelve al modelo original).
- **Pechos que siguen al cuerpo** (`VillagerEntityBaseModelMCA.placeBreasts`): posicion y rotacion completas del
  cuerpo, con un resorte suave de fisica.
- **Interacciones visibles** (`client/dreams/DreamsPoses.java`, `server/dreams/DreamsAnimations.java`,
  mensaje `DreamsAnimationMessage`, accion de dialogo `"animate"`): beso, abrazo y cachetada se ven en el aldeano y
  en el jugador, y los dos se ponen de frente. **El servidor tambien tiene que usar este jar.**
- Con EMF, Starcompat llama a `DreamsPoses.afterEmfAnimation` despues de cada animacion, asi las poses de MCA
  (panico, bebes, interacciones) quedan encima de Fresh Animations.
- **Animaciones estilo Blockbench** (`client/dreams/anim/DreamsAnimation.java`, archivos en
  `assets/mca/dreams_animations`, generados con `tools/make_animations.py`): beso, abrazo, cachetada, chocar los
  cinco, baile lento y baile divertido. Las de pareja usan un ancla comun: el jugador queda quieto, el aldeano se
  pone enfrente a la distancia justa y los dos se dibujan desde la misma ancla, asi no se separan. La camara pasa
  a tercera persona mientras dura. Opciones nuevas en "Hablar": chocar los cinco, bailar (divertido) y bailar lento.
- **Aldeanos mas vivos** (`client/dreams/VillageLife.java`): saludan con la mano al pasar, gesticulan al hablar y
  charlan entre ellos cuando estan quietos y de frente.

## Dreams 2
- **Conversar** (sin IA): árbol de 90 nodos generado por `tools/make_talk.py`; depende de personalidad, ánimo, corazones, relación, profesión y hora; el aldeano también te pregunta cosas y se cansa si hablan mucho.
- **De la mano / en la espalda / en brazos**: `server/dreams/DreamsAttach` (el aldeano va montado en el jugador y se lo ubica cada tick); poses en `DreamsPoses.applyAttach`, encima de EMF. Se suelta con shift o "Soltar".
- **Procrear**: animación de pareja `procreate` con fundido a negro (`MixinDreamsInGameHud`).
- **Gestos**: acción de diálogo `gesture` y gestos de ocio/trabajo en `VillageLife` (`tools/make_animations.py`).
- **Armaduras 3D**: la armadura del aldeano usa las capas de armadura del jugador, así EMF le pone el modelo 3D.
- Textos en `es_ar` y líneas de personalidad activas también en español.
