package com.starlevi.dreams.client;

import com.starlevi.dreams.DreamsMod;
import com.starlevi.dreams.client.data.DialogueManager;
import com.starlevi.dreams.client.data.SpeakerManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DreamsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // Los personajes se cargan antes que los dialogos que los usan.
        event.registerReloadListener(new SpeakerManager());
        event.registerReloadListener(new DialogueManager());
    }
}
