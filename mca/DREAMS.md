# MCA de Dreams

Fork de [Minecraft Comes Alive](https://github.com/Luke100000/minecraft-comes-alive) 7.6.26+1.20.1 para el servidor Dreams.
Se distribuye bajo la misma licencia (GPL-3.0, ver `LICENSE`). Compila solo la version de Forge.

## Cambios respecto al original

- **Conversacion estilo novela visual** (`client/gui/InteractScreen.java`, `VnStyle.java`): caja abajo con el nombre
  del aldeano, texto letra por letra, respuestas numeradas arriba de la caja (clic o teclas 1-9), espacio/clic para
  completar el texto. La ficha del aldeano (profesion, animo, personalidad, rasgos) pasa a un panel.
- **Botones del menu** con el mismo estilo (`AbstractDynamicScreen.ButtonEx`).
- **Voces tipo bip** (`client/dreams/VillagerVoice.java`): reemplazan al TTS. Cada aldeano tiene su instrumento
  (segun su UUID) y su tono (genero, edad y el gen de tono de voz). Config: `enableBipVoices`.
- **Animaciones con EMF / Fresh Animations** (`VillagerEntityMCARenderer`, `VillagerEntityBaseModelMCA`,
  `client/dreams/EmfBridge.java`): el cuerpo del aldeano usa la capa de modelo del jugador, asi EMF le aplica las
  animaciones del jugador. MCA mantiene lo suyo: bebes, panico, pechos y segunda capa de ropa siguiendo al cuerpo.
  Config: `useAnimatedPlayerModel` (en `false` vuelve al modelo original).
