package com.starlevi.starhero.client;

import com.starlevi.starhero.content.Instrument;
import com.starlevi.starhero.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu del escenario: elegir cancion (las de osu!mania importadas en CubOsu), armar la banda (solo con los
 * aldeanos, o con amigos), elegir instrumento y arrancar el show.
 */
public class StageScreen extends Screen {
    private static final int ROW = 22;
    private List<CubOsuBridge.Song> songs = new ArrayList<>();
    private int scroll;
    private int hovered = -1;

    public StageScreen() {
        super(Component.literal("Star Hero"));
    }

    private Net.LobbyState state() {
        return GigClient.lobby;
    }

    private boolean isLeader() {
        Net.LobbyState s = state();
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.player == null) {
            return false;
        }
        return s.members().stream().anyMatch(m -> m.leader() && m.entityId() == mc.player.getId());
    }

    private Instrument myInstrument() {
        Net.LobbyState s = state();
        Minecraft mc = Minecraft.getInstance();
        if (s != null && mc.player != null) {
            for (Net.Member m : s.members()) {
                if (m.entityId() == mc.player.getId()) {
                    return Instrument.byId(m.instrument());
                }
            }
        }
        return Instrument.GUITAR;
    }

    private void send(String action, String arg) {
        Net.LobbyState s = state();
        if (s != null) {
            Net.toServer(new Net.LobbyAction(s.amp(), action, arg));
        }
    }

    // ---------------------------------------------------------------- disposicion

    private int leftX() {
        return 14;
    }

    private int leftW() {
        return Math.min(300, width / 2 - 20);
    }

    private int listTop() {
        return 56;
    }

    private int listBottom() {
        return height - 40;
    }

    private int rightX() {
        return leftX() + leftW() + 16;
    }

    private int rightW() {
        return width - rightX() - 14;
    }

    @Override
    protected void init() {
        songs = CubOsuBridge.songs();
        boolean leader = isLeader();
        Net.LobbyState s = state();
        int rx = rightX();
        int rw = rightW();
        int by = height - 40 - 24 * 4;

        addRenderableWidget(Button.builder(Component.translatable("gui.starhero.instrument",
                        Component.translatable(myInstrument().translationKey())), b -> send("instrument", myInstrument().next().id()))
                .bounds(rx, by, rw, 20).build());
        Button mode = addRenderableWidget(Button.builder(Component.translatable(s != null && s.bandMode()
                        ? "gui.starhero.mode_band" : "gui.starhero.mode_solo"), b -> send("mode", s != null && s.bandMode() ? "solo" : "band"))
                .bounds(rx, by + 24, rw, 20).build());
        mode.active = leader;
        Button start = addRenderableWidget(Button.builder(Component.translatable(leader ? "gui.starhero.start" : "gui.starhero.waiting")
                        .withStyle(ChatFormatting.BOLD), b -> send("start", ""))
                .bounds(rx, by + 48, rw, 20).build());
        start.active = leader && s != null && !s.song().isEmpty();
        addRenderableWidget(Button.builder(Component.translatable("gui.starhero.leave"), b -> {
            send("leave", "");
            onClose();
        }).bounds(rx, by + 72, rw / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.starhero.import"), b -> CubOsuBridge.openSongMenu())
                .bounds(rx + rw / 2 + 2, by + 72, rw - rw / 2 - 2, 20).build());
    }

    public void refresh() {
        clearWidgets();
        init();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- dibujo

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, 0xE01A1030, 0xE00A0614);
        g.fill(x, y, x + w, y + 1, 0xFFB9A8FF);
        g.fill(x, y + h - 1, x + w, y + h, 0x80B9A8FF);
        g.fill(x, y, x + 1, y + h, 0x60B9A8FF);
        g.fill(x + w - 1, y, x + w, y + h, 0x60B9A8FF);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Net.LobbyState s = state();
        boolean leader = isLeader();

        // titulo
        g.pose().pushPose();
        g.pose().translate(width / 2F, 12, 0);
        g.pose().scale(2, 2, 1);
        Component title = Component.literal("★ STAR HERO ★").withStyle(ChatFormatting.BOLD);
        g.drawString(font, title, -font.width(title) / 2, 0, 0xFFFFD34D, true);
        g.pose().popPose();

        // canciones
        int lx = leftX();
        int lw = leftW();
        panel(g, lx, 40, lw, listBottom() - 40 + 4);
        g.drawString(font, Component.translatable("gui.starhero.songs"), lx + 8, 45, 0xFFE8E2FF, true);
        hovered = -1;
        if (songs.isEmpty()) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.translatable(
                    CubOsuBridge.available() ? "gui.starhero.no_songs" : "gui.starhero.no_cubosu"), lw - 20);
            int y = listTop() + 10;
            for (var line : lines) {
                g.drawString(font, line, lx + 10, y, 0xFFB0A8C8, false);
                y += 11;
            }
        } else {
            int visible = (listBottom() - listTop()) / ROW;
            scroll = Mth.clamp(scroll, 0, Math.max(0, songs.size() - visible));
            g.enableScissor(lx + 2, listTop(), lx + lw - 2, listBottom());
            for (int i = 0; i < visible + 1 && i + scroll < songs.size(); i++) {
                CubOsuBridge.Song song = songs.get(i + scroll);
                int y = listTop() + i * ROW;
                boolean selected = s != null && song.name().equals(s.song());
                boolean over = mouseX >= lx + 4 && mouseX <= lx + lw - 4 && mouseY >= y && mouseY < y + ROW - 2 && mouseY < listBottom();
                if (over) {
                    hovered = i + scroll;
                }
                int bg = selected ? 0xC05A2E9A : over && leader ? 0x803A2466 : 0x40000000;
                g.fill(lx + 4, y, lx + lw - 4, y + ROW - 2, bg);
                if (selected) {
                    g.fill(lx + 4, y, lx + 6, y + ROW - 2, 0xFFFFD34D);
                }
                String t = font.plainSubstrByWidth(song.title(), lw - 70);
                g.drawString(font, t, lx + 10, y + 2, selected ? 0xFFFFD34D : 0xFFFFFFFF, false);
                String sub = (song.artist().isEmpty() ? "" : song.artist() + " · ") + song.lanes() + "K · " + Math.round(song.bpm()) + " BPM";
                g.drawString(font, font.plainSubstrByWidth(sub, lw - 20), lx + 10, y + 11, 0xFF9A90B8, false);
            }
            g.disableScissor();
            if (!leader) {
                g.drawString(font, Component.translatable("gui.starhero.leader_picks"), lx + 8, listBottom() - 10, 0xFF9A90B8, false);
            }
        }

        // banda
        int rx = rightX();
        int rw = rightW();
        panel(g, rx - 6, 40, rw + 12, height - 40 - 24 * 4 - 46);
        g.drawString(font, Component.translatable("gui.starhero.band"), rx, 45, 0xFFE8E2FF, true);
        if (s != null) {
            Component chosen = s.song().isEmpty() ? Component.translatable("gui.starhero.no_song_chosen").withStyle(ChatFormatting.GRAY)
                    : Component.literal("♫ " + s.songTitle()).withStyle(ChatFormatting.YELLOW);
            g.drawString(font, font.split(chosen, rw).get(0), rx, 58, 0xFFFFFFFF, false);
            int y = 74;
            for (Net.Member m : s.members()) {
                if (y > height - 40 - 24 * 4 - 26) {
                    break;
                }
                Instrument inst = Instrument.byId(m.instrument());
                g.fill(rx, y, rx + rw, y + 20, m.player() ? 0x50402A80 : 0x50204A40);
                g.renderItem(inst.stack(), rx + 2, y + 2);
                String name = (m.leader() ? "★ " : "") + m.name();
                g.drawString(font, font.plainSubstrByWidth(name, rw - 30), rx + 22, y + 2, m.leader() ? 0xFFFFD34D : 0xFFFFFFFF, false);
                Component role = Component.translatable(inst.roleKey()).append(m.player() ? Component.empty()
                        : Component.literal(" · ").append(Component.translatable("gui.starhero.npc")));
                g.drawString(font, role, rx + 22, y + 11, 0xFF9A90B8, false);
                y += 22;
            }
            List<net.minecraft.util.FormattedCharSequence> hint = font.split(Component.translatable("gui.starhero.hint"), rw);
            int hy = height - 40 - 24 * 4 - 8 - hint.size() * 10;
            for (var line : hint) {
                g.drawString(font, line, rx, hy, 0xFF7FD8C8, false);
                hy += 10;
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered >= 0 && hovered < songs.size() && isLeader()) {
            CubOsuBridge.Song song = songs.get(hovered);
            send("song", song.name() + "\t" + song.title() + "\t" + song.bpm() + "\t" + song.offset() + "\t" + song.leadIn());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll -= (int) Math.signum(delta);
        return true;
    }
}
