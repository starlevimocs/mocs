package com.starlevi.starinteractions.client;

import com.starlevi.starinteractions.Catalog;
import com.starlevi.starinteractions.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Menu de interacciones. Mirando a un jugador: saludos, carino, diversion y "juntos" (de la mano, cargar, tocar);
 * mirando a un aldeano: el modo toque; sin nadie enfrente: los emotes.
 */
public class InteractionScreen extends Screen {
    private static final String[] TABS = {"greet", "care", "fun", "together", "emote"};
    private static String lastTab = "greet";

    private final Entity target;
    private final boolean targetPlayer;
    private final boolean targetVillager;
    private String tab;

    public InteractionScreen(Entity target) {
        super(Component.translatable("gui.starinteractions.title"));
        this.target = target;
        this.targetPlayer = target instanceof Player;
        this.targetVillager = target != null && !targetPlayer;
        this.tab = targetPlayer ? lastTab : "emote";
    }

    private int panelW() {
        return Math.min(width - 40, 330);
    }

    private int panelX() {
        return (width - panelW()) / 2;
    }

    private int panelY() {
        return Math.max(20, height / 2 - 100);
    }

    @Override
    protected void init() {
        clearWidgets();
        int x = panelX();
        int y = panelY();
        int w = panelW();
        Minecraft mc = Minecraft.getInstance();
        boolean linked = mc.player != null && Poses.linkOf(mc.player.getId()) != null;

        if (targetPlayer) {
            int tw = (w - 16) / TABS.length;
            for (int i = 0; i < TABS.length; i++) {
                String t = TABS[i];
                Button b = addRenderableWidget(Button.builder(Component.translatable("gui.starinteractions.tab." + t)
                        .withStyle(t.equals(tab) ? ChatFormatting.YELLOW : ChatFormatting.WHITE), btn -> {
                    tab = t;
                    lastTab = t.equals("emote") ? lastTab : t;
                    init();
                }).bounds(x + 8 + i * tw, y + 26, tw - 2, 18).build());
                b.active = !t.equals(tab);
            }
        }
        int top = y + (targetPlayer ? 50 : 30);
        if (targetVillager) {
            addRenderableWidget(Button.builder(Component.translatable("interaction.starinteractions.touch").withStyle(ChatFormatting.LIGHT_PURPLE), b -> {
                TouchClient.request(target.getId());
                onClose();
            }).bounds(x + 8, top, w - 16, 20).build());
            top += 36;
        }
        List<Catalog.Entry> entries = Catalog.category(tab);
        int cols = 3;
        int bw = (w - 16) / cols;
        int i = 0;
        for (Catalog.Entry e : entries) {
            int bx = x + 8 + (i % cols) * bw;
            int by = top + (i / cols) * 21;
            addRenderableWidget(Button.builder(Component.translatable(e.label()), b -> {
                if (e.kind() == Catalog.Kind.SOLO) {
                    Net.toServer(new Net.Emote(e.id()));
                } else if (target != null) {
                    Net.toServer(new Net.Request(target.getId(), e.id()));
                }
                onClose();
            }).bounds(bx, by, bw - 2, 19).build());
            i++;
        }
        if (linked) {
            addRenderableWidget(Button.builder(Component.translatable("gui.starinteractions.release").withStyle(ChatFormatting.GOLD), b -> {
                Net.toServer(new Net.Release());
                onClose();
            }).bounds(x + 8, y + panelH() - 26, w - 16, 20).build());
        }
    }

    private int panelH() {
        int rows = (Catalog.category(tab).size() + 2) / 3;
        return (targetPlayer ? 50 : 30) + (targetVillager ? 36 : 0) + rows * 21 + 40;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();
        g.fill(0, 0, width, height, 0x60000000);
        g.fillGradient(x, y, x + w, y + h, 0xF01A1030, 0xF00A0614);
        g.fill(x, y, x + w, y + 1, 0xFFE2C98F);
        g.fill(x, y + h - 1, x + w, y + h, 0x80E2C98F);
        g.fill(x, y, x + 1, y + h, 0x60E2C98F);
        g.fill(x + w - 1, y, x + w, y + h, 0x60E2C98F);
        Component title = target == null ? Component.translatable("gui.starinteractions.title_emotes")
                : Component.translatable("gui.starinteractions.title_with", target.getDisplayName());
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), x + 10, y + 9, 0xFFFFE9C0, true);
        if (targetVillager) {
            Component hint = Component.translatable("gui.starinteractions.villager_hint");
            int hy = y + 54;
            for (var line : font.split(hint, w - 20)) {
                g.drawString(font, line, x + 10, hy, 0xFF9A90B8, false);
                hy += 9;
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
}
