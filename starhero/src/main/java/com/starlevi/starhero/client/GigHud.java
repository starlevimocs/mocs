package com.starlevi.starhero.client;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Lo que se ve en pantalla durante el show: titulo, cuenta regresiva, euforia del publico y el cartel final. */
public final class GigHud {
    private GigHud() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        long now = Util.getMillis();

        if (GigClient.active()) {
            // titulo arriba al centro
            Component top = Component.literal("★ STAR HERO ★").withStyle(ChatFormatting.BOLD);
            int tw = font.width(top);
            g.drawString(font, top, (w - tw) / 2, 6, 0xFFFFD34D, true);
            Component song = Component.literal(GigClient.title);
            g.drawString(font, song, (w - font.width(song)) / 2, 18, 0xFFE8E2FF, true);

            // cuenta regresiva
            long left = GigClient.startMs - now;
            if (left > -800) {
                String txt = left > 2000 ? "3" : left > 1000 ? "2" : left > 0 ? "1" : Component.translatable("hud.starhero.go").getString();
                float frac = left > 0 ? (left % 1000) / 1000F : (left + 800) / 800F;
                float scale = 3.0F + 1.5F * frac;
                g.pose().pushPose();
                g.pose().translate(w / 2F, h / 2F - 30, 0);
                g.pose().scale(scale, scale, 1);
                int cw = font.width(txt);
                int alpha = (int) (255 * Mth.clamp(left > 0 ? 1 : frac, 0, 1));
                g.drawString(font, txt, -cw / 2, -4, (alpha << 24) | 0xFFD34D, true);
                g.pose().popPose();
            }

            // euforia del publico (arriba a la derecha)
            int bw = 90;
            int bx = w - bw - 10;
            int by = 8;
            g.drawString(font, Component.translatable("hud.starhero.crowd"), bx, by, 0xFFE8E2FF, true);
            g.fill(bx - 1, by + 11, bx + bw + 1, by + 19, 0xC0101018);
            float hype = GigClient.hype();
            int fillW = (int) (bw * hype);
            int col = hype > 0.75F ? 0xFF5CFF8A : hype > 0.5F ? 0xFFFFD34D : hype > 0.3F ? 0xFFFF9A3C : 0xFFFF4D5E;
            g.fillGradient(bx, by + 12, bx + fillW, by + 18, col, (col & 0x00FFFFFF) | 0x90000000);
            String face = hype > 0.75F ? "★★★" : hype > 0.5F ? "★★" : hype > 0.3F ? "★" : "…";
            g.drawString(font, face, bx + bw - font.width(face), by, col, true);

            // puntaje de la banda (arriba a la izquierda)
            g.drawString(font, Component.translatable("hud.starhero.band_score", String.format("%,d", GigClient.bandScore)), 10, 8, 0xFFE8E2FF, true);
            if (GigClient.bandCombo > 1) {
                g.drawString(font, Component.translatable("hud.starhero.band_combo", GigClient.bandCombo), 10, 19, 0xFFB9A8FF, true);
            }
            if (GigClient.localPerformer && !StarCamera.enabled) {
                g.drawString(font, Component.translatable("hud.starhero.camera_hint"), 10, h - 12, 0x80FFFFFF, false);
            }
        }

        // cartel del final
        if (GigClient.ending != null) {
            long t = now - GigClient.endingAt;
            if (t > 8000) {
                GigClient.ending = null;
                return;
            }
            float a = Mth.clamp(Math.min(t / 300F, (8000 - t) / 500F), 0, 1);
            int alpha = (int) (a * 255);
            int bw = 240;
            int bh = 74;
            int x = (w - bw) / 2;
            int y = h / 2 - bh - 10;
            g.fillGradient(x, y, x + bw, y + bh, (int) (a * 0xD0) << 24 | 0x1A0F2E, (int) (a * 0xD0) << 24 | 0x0A0612);
            g.fill(x, y, x + bw, y + 1, (alpha << 24) | 0xFFD34D);
            g.fill(x, y + bh - 1, x + bw, y + bh, (alpha << 24) | 0xB9A8FF);
            Component head = Component.translatable(GigClient.ending.headline()).withStyle(ChatFormatting.BOLD);
            g.drawString(font, head, (w - font.width(head)) / 2, y + 10, (alpha << 24) | 0xFFD34D, true);
            String[] d = GigClient.ending.detail().split("\\|");
            if (d.length >= 3) {
                Component l1 = Component.translatable("hud.starhero.end_score", d[0]);
                Component l2 = Component.translatable("hud.starhero.end_stats", d[1], d[2]);
                g.drawString(font, l1, (w - font.width(l1)) / 2, y + 28, (alpha << 24) | 0xE8E2FF, true);
                g.drawString(font, l2, (w - font.width(l2)) / 2, y + 40, (alpha << 24) | 0xB9A8FF, true);
            }
            if (GigClient.ending.tips() > 0) {
                Component tips = Component.translatable("hud.starhero.end_tips", GigClient.ending.tips());
                g.drawString(font, tips, (w - font.width(tips)) / 2, y + 56, (alpha << 24) | 0x5CFF8A, true);
            }
        }
    }
}
