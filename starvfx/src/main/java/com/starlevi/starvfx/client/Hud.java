package com.starlevi.starvfx.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que va sobre la pantalla: barras de cine, fundidos y destellos, vineta y los textos de la historia.
 * Se dibuja como parte de la interfaz, asi que no depende de los shaders.
 */
public final class Hud {
    private record Fade(int color, long start, float in, float hold, float out) {
        float alpha(long now) {
            float t = (now - start) / 1000F;
            if (t < 0) {
                return 0;
            }
            if (t < in) {
                return in <= 0 ? 1 : t / in;
            }
            if (t < in + hold) {
                return 1;
            }
            return out <= 0 ? 0 : Math.max(0, 1 - (t - in - hold) / out);
        }

        boolean done(long now) {
            return (now - start) / 1000F > in + hold + out;
        }
    }

    private static final List<Fade> FADES = new ArrayList<>();
    private static String text = "";
    private static String sub = "";
    private static long textStart;
    private static float textSeconds;
    private static int textColor;
    private static float bars;
    private static long lastFrame;

    private Hud() {
    }

    static void reset() {
        FADES.clear();
        text = "";
    }

    static void fade(int color, float in, float hold, float out, float elapsed) {
        FADES.add(new Fade(color, Util.getMillis() - (long) (elapsed * 1000), in, hold, out));
    }

    static void text(String title, String subtitle, float seconds, int color) {
        text = title;
        sub = subtitle;
        textStart = Util.getMillis();
        textSeconds = Math.max(0.5F, seconds);
        textColor = color;
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        long now = Util.getMillis();
        float dt = Mth.clamp((now - lastFrame) / 1000F, 0, 0.1F);
        lastFrame = now;
        boolean wantBars = ClientScene.letterboxed();
        bars = Mth.clamp(bars + (wantBars ? dt : -dt) / 0.8F, 0, 1);
        float b = bars * bars * (3 - 2 * bars);

        if (b > 0.001F) {
            // vineta suave en los bordes
            int va = (int) (b * 0x70) << 24;
            g.fillGradient(0, 0, width, height / 3, va, 0);
            g.fillGradient(0, height - height / 3, width, height, 0, va);
        }

        // aura del Arbol Madre: los bordes de la pantalla se tinen de su luz y laten despacio
        float aura = Tree.aura;
        if (aura > 0.01F && !Minecraft.getInstance().options.hideGui) {
            float breath = 0.8F + 0.2F * Mth.sin((now % 600_000L) / 1000F * 0.9F);
            int col = Tree.auraColor;
            int edge = (int) (Mth.clamp(aura * breath * 0.42F, 0, 1) * 255) << 24 | col;
            g.fillGradient(0, 0, width, height * 2 / 5, edge, col);
            g.fillGradient(0, height - height * 2 / 5, width, height, col, edge);
            g.fill(0, 0, width, height, (int) (aura * 0.07F * 255) << 24 | col);
            if (Tree.talkable != null && Minecraft.getInstance().screen == null && !ClientScene.hudHidden()) {
                g.drawCenteredString(Minecraft.getInstance().font, net.minecraft.network.chat.Component.translatable("gui.starvfx.tree_talk"),
                        width / 2, height / 2 + 18, 0xFFFFFF);
            }
        }

        FADES.removeIf(f -> f.done(now));
        for (Fade f : FADES) {
            int a = (int) (Mth.clamp(f.alpha(now), 0, 1) * 255);
            if (a > 0) {
                g.fill(0, 0, width, height, a << 24 | f.color);
            }
        }

        if (b > 0.001F) {
            int bar = (int) (height * 0.115F * b);
            g.fill(0, 0, width, bar, 0xFF000000);
            g.fill(0, height - bar, width, height, 0xFF000000);
        }

        if (!text.isEmpty() || !sub.isEmpty()) {
            float t = (now - textStart) / 1000F;
            if (t > textSeconds) {
                text = "";
                sub = "";
                return;
            }
            float a = Mth.clamp(Math.min(t / 0.6F, (textSeconds - t) / 0.9F), 0, 1);
            int alpha = Math.max(5, (int) (a * 255));
            Font font = Minecraft.getInstance().font;
            int y = (int) (height * 0.70F);
            if (!text.isEmpty()) {
                // se escribe letra por letra
                int shown = Mth.clamp((int) (t * 28), 0, text.length());
                String part = text.substring(0, shown);
                float scale = Math.min(2.6F, (width - 40) / (float) Math.max(1, font.width(text)));
                g.pose().pushPose();
                g.pose().translate(width / 2F, y, 0);
                g.pose().scale(scale, scale, 1);
                g.drawString(font, part, -font.width(text) / 2, 0, alpha << 24 | textColor, true);
                g.pose().popPose();
                y += (int) (9 * scale) + 6;
            }
            if (!sub.isEmpty()) {
                float sa = Mth.clamp(Math.min((t - (text.isEmpty() ? 0 : 0.7F)) / 0.8F, (textSeconds - t) / 0.9F), 0, 1);
                int salpha = (int) (sa * 255);
                if (salpha > 4) {
                    g.drawCenteredString(font, sub, width / 2, y, salpha << 24 | (text.isEmpty() ? textColor & 0xFFFFFF : 0xE6E0D0));
                }
            }
        }
    }
}
