package com.starlevi.starvfx.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starlevi.starvfx.net.Net;
import net.minecraft.world.phys.Vec3;

/** Mensajes del servidor, del lado del cliente. */
public final class ClientNet {
    private ClientNet() {
    }

    public static void playScene(Net.PlayScene msg) {
        ClientScene.play(msg);
    }

    public static void stopScene(Net.StopScene msg) {
        ClientScene.stop(msg.id());
    }

    public static void fx(Net.Fx msg) {
        if (msg.remove()) {
            if (msg.id().equals("*")) {
                Fx.WORLD.clear();
            } else {
                Fx.WORLD.remove(msg.id());
            }
            return;
        }
        JsonObject params;
        try {
            params = JsonParser.parseString(msg.params()).getAsJsonObject();
        } catch (RuntimeException e) {
            params = new JsonObject();
        }
        Fx.Effect fx = Fx.create(msg.type(), new Vec3(msg.x(), msg.y(), msg.z()), params, msg.elapsed());
        if (fx != null) {
            Fx.WORLD.put(msg.id(), fx);
        }
    }

    public static void playerAnim(Net.PlayerAnim msg) {
        PlayerFx.set(msg.entityId(), msg.anim(), msg.delay(), msg.duration());
    }

    public static void zones(Net.Zones msg) {
        Cine.setZones(msg.json());
    }
}
