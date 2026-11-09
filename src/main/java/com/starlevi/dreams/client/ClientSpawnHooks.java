package com.starlevi.dreams.client;

import com.starlevi.dreams.client.screen.SpawnControlScreen;
import com.starlevi.dreams.network.SyncSpawnPacket;
import net.minecraft.client.Minecraft;

/** Punto de entrada del lado cliente para el control de spawn. */
public final class ClientSpawnHooks {
    private ClientSpawnHooks() {
    }

    public static void handleSync(SyncSpawnPacket packet) {
        ClientSpawnState.apply(packet);
        Minecraft mc = Minecraft.getInstance();
        if (packet.open() && !(mc.screen instanceof SpawnControlScreen)) {
            mc.setScreen(new SpawnControlScreen());
        }
    }
}
