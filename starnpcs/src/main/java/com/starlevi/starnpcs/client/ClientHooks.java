package com.starlevi.starnpcs.client;

import com.starlevi.starnpcs.NpcEntity;
import com.starlevi.starnpcs.Profile;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.Arrays;
import java.util.List;

/** Lo que el servidor le pide al cliente. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void assetPart(String path, int part, int parts, byte[] data) {
        ClientAssets.part(path, part, parts, data);
    }

    private static NpcEntity npc(int id) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(id);
        return e instanceof NpcEntity n ? n : null;
    }

    public static void trigger(int id, String anim, long tick) {
        NpcEntity n = npc(id);
        if (n != null) {
            NpcAnim a = NpcAnim.of(n);
            a.once = anim;
            a.onceStart = tick;
        }
    }

    public static void bubble(int id, String text) {
        NpcEntity n = npc(id);
        Minecraft mc = Minecraft.getInstance();
        if (n != null && mc.level != null) {
            NpcAnim a = NpcAnim.of(n);
            a.bubble = text;
            a.bubbleUntil = mc.level.getGameTime() + 60 + Math.min(120, text.length() * 2L);
        }
    }

    public static void openEditor(int id, String json, int tab, String presets) {
        NpcEntity n = npc(id);
        if (n != null) {
            List<String> list = presets.isEmpty() ? List.of() : Arrays.asList(presets.split("\n"));
            Minecraft.getInstance().setScreen(new EditorScreen(n, Profile.fromJson(json), tab, list));
        }
    }
}
