package com.starlevi.dreams.dialogue;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Un personaje que habla: nombre, color, retratos por expresion y su "voz" de bips.
 */
public record Speaker(ResourceLocation id, String displayName, int color,
                      Map<String, ResourceLocation> portraits, Voice voice) {

    /**
     * @param sound    id del sonido (vanilla o propio, definido en sounds.json)
     * @param pitch    tono base
     * @param variance variacion aleatoria del tono por bip
     * @param volume   volumen
     */
    public record Voice(ResourceLocation sound, float pitch, float variance, float volume) {
        public static final Voice DEFAULT =
                new Voice(new ResourceLocation("minecraft", "block.note_block.bit"), 1.0F, 0.1F, 0.6F);
    }

    public static Speaker unknown(ResourceLocation id) {
        return new Speaker(id, id.getPath(), 0xFFFFFF, Map.of(), Voice.DEFAULT);
    }

    /** Devuelve el retrato de la expresion, o el "neutral" si no existe, o null. */
    public ResourceLocation portrait(String expression) {
        ResourceLocation tex = portraits.get(expression);
        return tex != null ? tex : portraits.get("neutral");
    }
}
