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
