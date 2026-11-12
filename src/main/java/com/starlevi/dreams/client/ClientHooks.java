package com.starlevi.dreams.client;

import com.starlevi.dreams.client.data.DialogueManager;
import com.starlevi.dreams.client.screen.DialogueScreen;
import com.starlevi.dreams.dialogue.Dialogue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Punto de entrada del lado cliente; solo se llama desde codigo que corre en el cliente. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openDialogue(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        Dialogue dialogue = DialogueManager.get(id);
        if (dialogue == null) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("[Dreams] No existe el dialogo: " + id), false);
            }
            return;
        }
        mc.setScreen(new DialogueScreen(dialogue));
    }

    /** Dialogo que viene escrito desde el servidor. */
    public static void openDialogueJson(ResourceLocation id, String json) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Dialogue dialogue = DialogueManager.parse(id, com.google.gson.JsonParser.parseString(json).getAsJsonObject());
            mc.setScreen(new DialogueScreen(dialogue));
        } catch (RuntimeException e) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("[Dreams] El dialogo " + id + " esta mal escrito: " + e.getMessage()), false);
            }
        }
    }
}
