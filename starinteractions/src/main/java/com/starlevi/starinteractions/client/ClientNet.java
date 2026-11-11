package com.starlevi.starinteractions.client;

import com.starlevi.starinteractions.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Mensajes del servidor, del lado del cliente. */
public final class ClientNet {
    static Net.Prompt prompt;
    static long promptAt;

    private ClientNet() {
    }

    public static void prompt(Net.Prompt msg) {
        prompt = msg;
        promptAt = Util.getMillis();
    }

    public static void pairStart(Net.PairStart msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof InteractionScreen) {
            mc.setScreen(null);
        }
        Poses.startPair(msg);
    }

    public static void pairStop(Net.PairStop msg) {
        Poses.stopPair(msg.aId(), msg.bId());
    }

    public static void emote(Net.EmoteState msg) {
        Poses.emote(msg.entityId(), msg.id());
    }

    public static void link(Net.Link msg) {
        Poses.link(msg.aId(), msg.bId(), msg.mode());
    }

    public static void touchState(Net.TouchState msg) {
        Poses.touch(msg);
    }

    public static void touchSession(Net.TouchSession msg) {
        Minecraft mc = Minecraft.getInstance();
        if (msg.open()) {
            mc.setScreen(new TouchScreen(msg.targetId()));
        } else {
            if (mc.screen instanceof TouchScreen) {
                mc.setScreen(null);
            }
            if (!msg.message().isEmpty() && mc.player != null) {
                mc.player.displayClientMessage(Component.translatable(msg.message()).withStyle(ChatFormatting.GRAY), true);
            }
        }
    }
}
