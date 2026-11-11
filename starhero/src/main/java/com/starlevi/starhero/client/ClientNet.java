package com.starlevi.starhero.client;

import com.starlevi.starhero.net.Net;
import net.minecraft.client.Minecraft;

/** Mensajes del servidor, del lado del cliente. */
public final class ClientNet {
    private ClientNet() {
    }

    public static void lobby(Net.LobbyState state) {
        GigClient.lobby = state;
        Minecraft mc = Minecraft.getInstance();
        if (state.openScreen()) {
            mc.setScreen(new StageScreen());
        } else if (mc.screen instanceof StageScreen screen) {
            screen.refresh();
        }
    }

    public static void gigStart(Net.GigStart msg) {
        GigClient.start(msg);
    }

    public static void notes(Net.GigNotes msg) {
        GigClient.notes(msg);
    }

    public static void state(Net.GigState msg) {
        GigClient.state(msg);
    }

    public static void end(Net.GigEnd msg) {
        GigClient.end(msg);
    }
}
