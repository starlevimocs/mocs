package com.starlevi.starnpcs.client;

import com.starlevi.starnpcs.Net;
import com.starlevi.starnpcs.NpcEntity;
import com.starlevi.starnpcs.Profile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Editor del NPC: pestanas a la derecha y el personaje en vivo a la izquierda. */
public class EditorScreen extends Screen {
    private static final String[] TABS = {"General", "Apariencia", "Animación", "Interacción", "Comercio"};
    private static final int W = 440, H = 252;
    private static final int ACCENT = 0xFF4FD1A5, PANEL = 0xF0121722, EDGE = 0xFF34405A, SOFT = 0xFF9AA7BD;

    private final NpcEntity npc;
    private final Profile p;
    private final List<String> presets;
    private int tab;
    private String preset = "";
    private boolean confirmDelete;
    private boolean sent;
    private final List<Label> labels = new ArrayList<>();
    private int left, top;

    private record Label(String text, int x, int y, int color) {
    }

    public EditorScreen(NpcEntity npc, Profile profile, int tab, List<String> presets) {
        super(Component.literal("Star NPCs"));
        this.npc = npc;
        this.p = profile;
        this.tab = Math.max(0, Math.min(TABS.length - 1, tab));
        this.presets = presets;
        if (!presets.isEmpty()) {
            preset = presets.get(0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        npc.preview = p;
        labels.clear();
        left = (width - W) / 2;
        top = (height - H) / 2;
        int tx = left + 128;
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            Button b = Button.builder(Component.literal(TABS[i]), btn -> {
                tab = index;
                rebuildWidgets();
            }).bounds(tx + i * 61, top + 24, 59, 18).build();
            b.active = i != tab;
            addRenderableWidget(b);
        }
        int x = left + 132, y = top + 52;
        switch (tab) {
            case 0 -> general(x, y);
            case 1 -> looks(x, y);
            case 2 -> anims(x, y);
            case 3 -> talk(x, y);
            default -> shop(x, y);
        }
        int by = top + H - 26;
        addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> {
            send("save", "");
            onClose();
        }).bounds(left + W - 250, by, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> onClose()).bounds(left + W - 168, by, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal(confirmDelete ? "¿Seguro?" : "Borrar NPC"), b -> {
            if (confirmDelete) {
                send("delete", "");
                onClose();
            } else {
                confirmDelete = true;
                b.setMessage(Component.literal("¿Seguro?"));
            }
        }).bounds(left + W - 86, by, 78, 20).build());
    }

    // ------------------------------------------------------------------ pestanas

    private void general(int x, int y) {
        text(x, y, "Nombre", p.name, 64, v -> p.name = v);
        text(x, y + 22, "Título", p.title, 64, v -> p.title = v);
        slider(x, y + 44, "Escala", 0.25F, 3F, p.scale, v -> p.scale = v);
        toggle(x, y + 66, "Mostrar nombre", () -> p.showName, v -> p.showName = v);
        toggle(x + 150, y + 66, "Invulnerable", () -> p.invulnerable, v -> p.invulnerable = v);
        toggle(x, y + 88, "Mira al jugador", () -> p.lookAtPlayer, v -> p.lookAtPlayer = v);
        toggle(x + 150, y + 88, "Gira hacia él", () -> p.turnToPlayer, v -> p.turnToPlayer = v);
        label("Comportamiento", x, y + 116);
        addRenderableWidget(cycle(x + 96, y + 110, 100, List.of("stay", "wander"), () -> p.behavior, v -> p.behavior = v,
                v -> v.equals("wander") ? "Pasea" : "Quieto"));
        slider(x, y + 132, "Radio de paseo", 1F, 32F, p.wanderRadius, v -> p.wanderRadius = v);
        label("Posición", x, y + 160);
        String[][] moves = {{"Adel.", "front"}, {"Atrás", "back"}, {"Izq.", "left"}, {"Der.", "right"}, {"Sube", "up"}, {"Baja", "down"}};
        for (int i = 0; i < moves.length; i++) {
            final String dir = moves[i][1];
            addRenderableWidget(Button.builder(Component.literal(moves[i][0]), b -> send("move", dir)).bounds(x + 52 + i * 35, y + 154, 34, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("⟲"), b -> send("rotate", "-15")).bounds(x + 52 + 6 * 35, y + 154, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("⟳"), b -> send("rotate", "15")).bounds(x + 71 + 6 * 35, y + 154, 18, 18).build());
    }

    private void looks(int x, int y) {
        text(x, y, "Skin", p.skin, 400, v -> p.skin = v);
        labels.add(new Label("player:Nombre  ·  url:https://…  ·  file:archivo", x + 96, y + 21, SOFT));
        toggle(x, y + 36, "Brazos finos", () -> p.slim, v -> p.slim = v);
        label("Modelo", x, y + 64);
        List<String> models = new ArrayList<>(ClientAssets.modelIds());
        models.remove("human_slim");
        if (!models.contains(p.model)) {
            models.add(p.model);
        }
        addRenderableWidget(cycle(x + 96, y + 58, 190, models, () -> p.model, v -> p.model = v, v -> v));
        label("Huesos", x, y + 86);
        addRenderableWidget(cycle(x + 96, y + 80, 190, List.of("star", "vanilla"), () -> p.starBones ? "star" : "vanilla",
                v -> p.starBones = v.equals("star"), v -> v.equals("star") ? "Star (Blockbench)" : "Vanilla (jugador)"));
        labels.add(new Label("Star: modelos y animaciones propias.", x + 96, y + 102, SOFT));
        labels.add(new Label("Vanilla: modelo de jugador, con armadura.", x + 96, y + 112, SOFT));
        label("Equipo", x, y + 136);
        addRenderableWidget(Button.builder(Component.literal("Copiar el mío"), b -> send("equip", "")).bounds(x + 96, y + 130, 94, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Quitar"), b -> send("unequip", "")).bounds(x + 192, y + 130, 94, 18).build());
    }

    private void anims(int x, int y) {
        label("Preset", x, y + 6);
        if (!presets.isEmpty()) {
            addRenderableWidget(cycle(x + 96, y, 130, presets, () -> preset, v -> preset = v, v -> v));
            addRenderableWidget(Button.builder(Component.literal("Aplicar"), b -> send("preset", preset)).bounds(x + 228, y, 58, 18).build());
        }
        List<String> names = new ArrayList<>();
        names.add("");
        BbModel m = ClientAssets.model(NpcRenderer.modelId(p));
        if (m != null) {
            names.addAll(m.anims.keySet());
        }
        Function<String, String> show = v -> v.isEmpty() ? "—" : v;
        anim(x, y + 24, "Reposo", names, show, () -> p.idle, v -> p.idle = v, false);
        anim(x, y + 44, "Hablando", names, show, () -> p.talk, v -> p.talk = v, false);
        anim(x, y + 64, "Al ver jugador", names, show, () -> p.see, v -> p.see = v, true);
        anim(x, y + 84, "Al interactuar", names, show, () -> p.interact, v -> p.interact = v, true);
        anim(x, y + 104, "Caminando", names, show, () -> p.walk, v -> p.walk = v, false);
        toggle(x, y + 126, "Frena al hablar", () -> p.stopOnTalk, v -> p.stopOnTalk = v);
        slider(x, y + 146, "Radio de visión", 2F, 16F, p.seeRadius, v -> p.seeRadius = v);
        text(x, y + 168, "Grupo", p.group, 40, v -> p.group = v, 120);
        addRenderableWidget(Button.builder(Component.literal("Sincronizar"), b -> send("sync", "")).bounds(x + 218, y + 168, 68, 18).build());
    }

    private void anim(int x, int y, String name, List<String> names, Function<String, String> show, Supplier<String> get,
                      Consumer<String> set, boolean test) {
        label(name, x, y + 6);
        List<String> list = new ArrayList<>(names);
        if (!list.contains(get.get())) {
            list.add(get.get());
        }
        addRenderableWidget(cycle(x + 96, y, test ? 168 : 190, list, get, set, show));
        if (test) {
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
                if (!get.get().isEmpty()) {
                    send("play", get.get());
                }
            }).bounds(x + 266, y, 20, 18).build());
        }
    }

    private void talk(int x, int y) {
        text(x, y, "Diálogo", p.dialogue, 100, v -> p.dialogue = v);
        labels.add(new Label("Un diálogo de Dreams, por ejemplo tree_hub", x + 96, y + 21, SOFT));
        label("Comandos", x, y + 40);
        labels.add(new Label("uno por línea", x, y + 51, SOFT));
        labels.add(new Label("{player}", x, y + 61, SOFT));
        area(x + 96, y + 36, 190, 46, String.join("\n", p.commands), v -> p.commands = lines(v));
        label("Frases", x, y + 92);
        labels.add(new Label("una por línea", x, y + 103, SOFT));
        area(x + 96, y + 88, 190, 46, String.join("\n", p.lines), v -> p.lines = lines(v));
        slider(x, y + 140, "Cada (seg.)", 4F, 120F, p.linesEvery, v -> p.linesEvery = v);
        labels.add(new Label("Sin diálogo ni comandos: abre la tienda o dice una frase.", x, y + 166, SOFT));
    }

    private void shop(int x, int y) {
        labels.add(new Label("Mano principal: lo que vende.", x, y, 0xFFFFFFFF));
        labels.add(new Label("Mano izquierda: lo que cobra.", x, y + 11, 0xFFFFFFFF));
        addRenderableWidget(Button.builder(Component.literal("Agregar"), b -> send("trade_add", "")).bounds(x, y + 26, 92, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Quitar última"), b -> send("trade_pop", "")).bounds(x + 96, y + 26, 92, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Vaciar"), b -> send("trade_clear", "")).bounds(x + 192, y + 26, 92, 18).build());
    }

    // ------------------------------------------------------------------ piezas

    private static List<String> lines(String text) {
        List<String> out = new ArrayList<>();
        for (String s : text.split("\n")) {
            if (!s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    private void label(String text, int x, int y) {
        labels.add(new Label(text, x, y, 0xFFE6ECF5));
    }

    private void text(int x, int y, String name, String value, int max, Consumer<String> set) {
        text(x, y, name, value, max, set, 190);
    }

    private void text(int x, int y, String name, String value, int max, Consumer<String> set, int w) {
        label(name, x, y + 6);
        EditBox box = new EditBox(font, x + 97, y + 1, w - 2, 16, Component.literal(name));
        box.setMaxLength(max);
        box.setValue(value);
        box.setResponder(set);
        addRenderableWidget(box);
    }

    private void area(int x, int y, int w, int h, String value, Consumer<String> set) {
        MultiLineEditBox box = new MultiLineEditBox(font, x, y, w, h, Component.empty(), Component.empty());
        box.setCharacterLimit(4000);
        box.setValue(value);
        box.setValueListener(set);
        addRenderableWidget(box);
    }

    private void toggle(int x, int y, String name, Supplier<Boolean> get, Consumer<Boolean> set) {
        Function<Boolean, Component> msg = v -> Component.literal((v ? "✔ " : "✘ ") + name);
        addRenderableWidget(Button.builder(msg.apply(get.get()), b -> {
            set.accept(!get.get());
            b.setMessage(msg.apply(get.get()));
        }).bounds(x, y, 142, 18).build());
    }

    private Button cycle(int x, int y, int w, List<String> options, Supplier<String> get, Consumer<String> set, Function<String, String> show) {
        return Button.builder(Component.literal(show.apply(get.get())), b -> {
            if (options.isEmpty()) {
                return;
            }
            int i = options.indexOf(get.get());
            String next = options.get(Math.floorMod(i + (hasShiftDown() ? -1 : 1), options.size()));
            set.accept(next);
            b.setMessage(Component.literal(show.apply(next)));
            if (tab == 1) {
                rebuildWidgets();
            }
        }).bounds(x, y, w, 18).build();
    }

    private void slider(int x, int y, String name, float min, float max, float value, Consumer<Float> set) {
        label(name, x, y + 6);
        double start = Math.max(0, Math.min(1, (value - min) / (max - min)));
        addRenderableWidget(new AbstractSliderButton(x + 96, y, 190, 18, Component.empty(), start) {
            {
                updateMessage();
            }

            private float current() {
                return Math.round((min + (float) this.value * (max - min)) * 20F) / 20F;
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format(java.util.Locale.ROOT, "%.2f", current())));
            }

            @Override
            protected void applyValue() {
                set.accept(current());
            }
        });
    }

    private void send(String action, String arg) {
        sent = true;
        Net.CHANNEL.sendToServer(new Net.Action(npc.getId(), action, arg, p.toJson(), tab));
    }

    @Override
    public void removed() {
        npc.preview = null;
        super.removed();
    }

    // ------------------------------------------------------------------ dibujo

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g);
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, EDGE);
        g.fill(left, top, left + W, top + H, PANEL);
        g.fillGradient(left, top, left + W, top + 20, 0xFF1E5E52, 0xFF16303A);
        g.fill(left, top + 20, left + W, top + 21, ACCENT);
        g.drawString(font, "Star NPCs", left + 8, top + 6, 0xFFFFFFFF);
        String who = p.name + (p.title.isBlank() ? "" : "  ·  " + p.title);
        g.drawString(font, who, left + W - 8 - font.width(who), top + 6, 0xFFCFEFE4);
        // vista previa
        int px = left + 8, py = top + 28, pw = 114, ph = H - 36;
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, EDGE);
        g.fillGradient(px, py, px + pw, py + ph, 0xFF0C1018, 0xFF1A2334);
        int size = (int) (58 / Math.max(0.6F, p.scale));
        int cx = px + pw / 2, cy = py + ph - 34;
        try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, cy, size, cx - mouseX, cy - 70 - mouseY, npc);
        } catch (RuntimeException ignored) {
        }
        g.drawCenteredString(font, "Shift + clic: opción anterior", px + pw / 2, py + ph - 12, SOFT);
        for (Label l : labels) {
            g.drawString(font, l.text, l.x, l.y, l.color, false);
        }
        if (tab == 4) {
            trades(g, left + 132, top + 104, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, pt);
    }

    private void trades(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        if (p.trades.isEmpty()) {
            g.drawString(font, "Todavía no vende nada.", x, y, SOFT, false);
            return;
        }
        int i = 0;
        for (Profile.Trade t : p.trades) {
            if (i >= 12) {
                break;
            }
            int cx = x + (i % 3) * 100, cy = y + (i / 3) * 24;
            g.fill(cx, cy, cx + 94, cy + 20, 0x60202A3C);
            ItemStack buy = NpcEntity.textToStack(t.buy), sell = NpcEntity.textToStack(t.sell);
            g.renderItem(buy, cx + 4, cy + 2);
            g.renderItemDecorations(font, buy, cx + 4, cy + 2);
            g.drawString(font, "→", cx + 42, cy + 6, ACCENT, false);
            g.renderItem(sell, cx + 70, cy + 2);
            g.renderItemDecorations(font, sell, cx + 70, cy + 2);
            if (mouseY >= cy && mouseY < cy + 20) {
                if (mouseX >= cx + 4 && mouseX < cx + 20) {
                    g.renderTooltip(font, buy, mouseX, mouseY);
                } else if (mouseX >= cx + 70 && mouseX < cx + 86) {
                    g.renderTooltip(font, sell, mouseX, mouseY);
                }
            }
            i++;
        }
    }
}
