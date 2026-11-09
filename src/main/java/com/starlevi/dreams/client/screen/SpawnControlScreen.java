package com.starlevi.dreams.client.screen;

import com.starlevi.dreams.client.ClientSpawnState;
import com.starlevi.dreams.network.DreamsNetwork;
import com.starlevi.dreams.network.SpawnActionPacket;
import com.starlevi.dreams.spawn.SpawnConfig;
import com.starlevi.dreams.spawn.SpawnMode;
import com.starlevi.dreams.spawn.SpawnZone;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pantalla de administracion del spawn natural.
 * <ul>
 *   <li>Mobs: modo de cada mob en todo el mundo.</li>
 *   <li>Mods: modo de un mod entero.</li>
 *   <li>Zonas: crear zonas (caja A-B o bioma) y elegir que puede aparecer en cada una.</li>
 * </ul>
 */
public class SpawnControlScreen extends Screen {
    private enum Tab {
        MOBS("Mobs"),
        MODS("Mods"),
        ZONES("Zonas");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    /** Una fila de las listas. */
    private record Row(String id, String title, String sub, EntityType<?> type, SpawnZone zone) {
    }

    private static final int PAD = 12;
    private static final int ROW_H = 18;
    private static final int RIGHT_W = 130;
    private static final int DELETE_W = 64;
    private static final int HINT_Y = 66;
    private static final int LIST_TOP = 86;

    private static final SpawnMode[] CYCLE_ORDER = {null, SpawnMode.NATURAL, SpawnMode.ZONES, SpawnMode.NEVER};

    private static List<Row> mobCatalog;
    private static List<Row> modCatalog;

    private Tab tab = Tab.MOBS;
    private String openZone;
    private String boxText = "";
    private boolean fullHeight = true;
    private int scroll;

    private EditBox box;
    private List<Row> rows = List.of();
    private int lastVersion = -1;
    private String lastFilter;
    private Tab lastTab;
    private String lastZone;

    private String pendingDelete;
    private long pendingDeleteAt;

    public SpawnControlScreen() {
        super(Component.literal("Control de spawn"));
    }

    @Override
    public boolean isPauseScreen() {
        return false; // si pausara el mundo, el servidor integrado no procesaria los cambios
    }

    // ---------------------------------------------------------------- catalogo

    private static void buildCatalog() {
        if (mobCatalog != null) {
            return;
        }
        List<Row> mobs = new ArrayList<>();
        Set<String> namespaces = new TreeSet<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            if (type.getCategory() == MobCategory.MISC) {
                continue; // el spawn natural solo usa las categorias de mobs
            }
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null) {
                continue;
            }
            mobs.add(new Row(key.toString(), type.getDescription().getString(), key.toString(), type, null));
            namespaces.add(key.getNamespace());
        }
        mobs.sort(Comparator.comparing(Row::sub));

        List<Row> mods = new ArrayList<>();
        for (String ns : namespaces) {
            mods.add(new Row(ns + ":*", modName(ns), ns + ":*", null, null));
        }
        mods.sort(Comparator.comparing(row -> row.title().toLowerCase(Locale.ROOT)));

        mobCatalog = mobs;
        modCatalog = mods;
    }

    private static String modName(String namespace) {
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace);
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        buildCatalog();

        int x = PAD;
        for (Tab t : Tab.values()) {
            Button button = Button.builder(Component.literal(t.label), b -> switchTab(t))
                    .bounds(x, 20, 80, 20).build();
            button.active = t != tab;
            addRenderableWidget(button);
            x += 84;
        }

        box = new EditBox(font, PAD, 44, Math.min(260, width - 2 * PAD), 18, Component.literal("texto"));
        box.setMaxLength(32);
        box.setValue(boxText);
        box.setResponder(text -> {
            boxText = text;
            scroll = 0;
        });
        box.setHint(Component.literal(zoneListMode() ? "Nombre de la zona nueva" : "Buscar...")
                .withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(box);

        int full = width - 2 * PAD;
        int row2 = height - 26;
        if (zoneListMode()) {
            int row1 = height - 50;
            int half = (full - 4) / 2;
            addRenderableWidget(Button.builder(Component.literal("Crear zona A-B"), b -> createBox())
                    .bounds(PAD, row1, half, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Crear zona de mi bioma"), b -> createBiome())
                    .bounds(PAD + half + 4, row1, half, 20).build());

            int third = (full - 8) / 3;
            addRenderableWidget(Button.builder(heightLabel(), b -> {
                fullHeight = !fullHeight;
                b.setMessage(heightLabel());
            }).bounds(PAD, row2, third, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Borrar seleccion"),
                            b -> send(SpawnActionPacket.Action.CLEAR_SELECTION, "", "", 0))
                    .bounds(PAD + third + 4, row2, third, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose())
                    .bounds(PAD + 2 * (third + 4), row2, third, 20).build());
        } else if (openZone != null) {
            int half = (full - 4) / 2;
            addRenderableWidget(Button.builder(Component.literal("< Volver a las zonas"), b -> {
                openZone = null;
                boxText = "";
                scroll = 0;
                rebuildWidgets();
            }).bounds(PAD, row2, half, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose())
                    .bounds(PAD + half + 4, row2, half, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose())
                    .bounds(width - PAD - 100, row2, 100, 20).build());
        }
    }

    private boolean zoneListMode() {
        return tab == Tab.ZONES && openZone == null;
    }

    private Component heightLabel() {
        return Component.literal("Altura completa: " + (fullHeight ? "Si" : "No"));
    }

    private void switchTab(Tab newTab) {
        tab = newTab;
        openZone = null;
        boxText = "";
        scroll = 0;
        rebuildWidgets();
    }

    private static void send(SpawnActionPacket.Action action, String a, String b, int n) {
        DreamsNetwork.CHANNEL.sendToServer(new SpawnActionPacket(action, a, b, n));
    }

    private String zoneNameOrDefault() {
        String name = boxText.trim();
        if (!name.isEmpty()) {
            return name;
        }
        int n = ClientSpawnState.config.zones().size() + 1;
        while (ClientSpawnState.config.zone("zona_" + n) != null) {
            n++;
        }
        return "zona_" + n;
    }

    private void createBox() {
        send(SpawnActionPacket.Action.CREATE_BOX, zoneNameOrDefault(), "", fullHeight ? 1 : 0);
        boxText = "";
        box.setValue("");
    }

    private void createBiome() {
        send(SpawnActionPacket.Action.CREATE_BIOME, zoneNameOrDefault(), "", 0);
        boxText = "";
        box.setValue("");
    }

    // ---------------------------------------------------------------- filas

    private void refreshRows() {
        if (lastVersion == ClientSpawnState.version && boxText.equals(lastFilter)
                && lastTab == tab && java.util.Objects.equals(lastZone, openZone)) {
            return;
        }
        lastVersion = ClientSpawnState.version;
        lastFilter = boxText;
        lastTab = tab;
        lastZone = openZone;

        String filter = boxText.toLowerCase(Locale.ROOT).trim();
        List<Row> out = new ArrayList<>();
        if (zoneListMode()) {
            for (SpawnZone zone : ClientSpawnState.config.zones()) {
                if (filter.isEmpty() || zone.name().toLowerCase(Locale.ROOT).contains(filter)) {
                    out.add(new Row(zone.name(), zone.name(), zone.describe(), null, zone));
                }
            }
        } else if (tab == Tab.MOBS) {
            addMatching(out, mobCatalog, filter);
        } else if (tab == Tab.MODS) {
            addMatching(out, modCatalog, filter);
        } else {
            addMatching(out, modCatalog, filter);
            addMatching(out, mobCatalog, filter);
        }
        rows = out;
    }

    private static void addMatching(List<Row> out, List<Row> source, String filter) {
        for (Row row : source) {
            if (filter.isEmpty()
                    || row.title().toLowerCase(Locale.ROOT).contains(filter)
                    || row.sub().toLowerCase(Locale.ROOT).contains(filter)) {
                out.add(row);
            }
        }
    }

    @Override
    public void tick() {
        // si la zona abierta fue borrada, vuelve a la lista
        if (openZone != null && ClientSpawnState.config.zone(openZone) == null) {
            openZone = null;
            boxText = "";
            scroll = 0;
            rebuildWidgets();
        }
    }

    // ---------------------------------------------------------------- layout

    private int listBottom() {
        return height - (zoneListMode() ? 56 : 32);
    }

    private int maxScroll() {
        return Math.max(0, rows.size() * ROW_H - (listBottom() - LIST_TOP));
    }

    private String hintText() {
        if (zoneListMode()) {
            return selectionText() + "  |  Clic en una zona para elegir que puede aparecer ahi. "
                    + "La varita marca A (clic izq.) y B (clic der.).";
        }
        if (openZone != null) {
            SpawnZone zone = ClientSpawnState.config.zone(openZone);
            String desc = zone == null ? "" : zone.describe();
            return "Zona '" + openZone + "' - " + desc + ". Marca lo que puede aparecer dentro; "
                    + "lo que esta en 'Nunca' sigue bloqueado.";
        }
        if (tab == Tab.MODS) {
            return "Regla para un mod entero. Un mob con regla propia tiene prioridad sobre la de su mod. "
                    + "Clic = siguiente modo, clic derecho = anterior.";
        }
        return "Clic = siguiente modo, clic derecho = anterior. Auto: animales de vanilla naturales, "
                + "el resto solo en zonas (salvo que su mod tenga regla).";
    }

    private String selectionText() {
        if (ClientSpawnState.a == null && ClientSpawnState.b == null) {
            return "Sin seleccion";
        }
        return "A: " + posText(ClientSpawnState.a) + "  B: " + posText(ClientSpawnState.b)
                + (ClientSpawnState.selectionDimension != null ? " (" + ClientSpawnState.selectionDimension + ")" : "");
    }

    private static String posText(net.minecraft.core.BlockPos pos) {
        return pos == null ? "-" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        refreshRows();
        scroll = Mth.clamp(scroll, 0, maxScroll());

        g.drawString(font, "Control de spawn natural", PAD, 7, 0xFFFFFFFF, true);
        String counter = ClientSpawnState.config.zones().size() + " zona(s)";
        g.drawString(font, counter, width - PAD - font.width(counter), 7, 0xFFAAAAAA, true);

        List<FormattedCharSequence> hint = font.split(Component.literal(hintText()), width - 2 * PAD);
        for (int i = 0; i < Math.min(2, hint.size()); i++) {
            g.drawString(font, hint.get(i), PAD, HINT_Y + i * 10, 0xFFAAAAAA, false);
        }

        int top = LIST_TOP;
        int bottom = listBottom();
        g.fill(PAD - 2, top - 2, width - PAD + 2, bottom + 2, 0x80000000);

        g.enableScissor(PAD, top, width - PAD, bottom);
        int y = top - scroll;
        for (int i = 0; i < rows.size(); i++, y += ROW_H) {
            if (y + ROW_H < top) {
                continue;
            }
            if (y > bottom) {
                break;
            }
            boolean hover = mouseX >= PAD && mouseX < width - PAD && mouseY >= Math.max(y, top)
                    && mouseY < Math.min(y + ROW_H - 2, bottom);
            g.fill(PAD, y, width - PAD, y + ROW_H - 2, hover ? 0x60FFFFFF : (i % 2 == 0 ? 0x50000000 : 0x28000000));
            drawRow(g, rows.get(i), y);
        }
        g.disableScissor();

        if (rows.isEmpty()) {
            String empty = zoneListMode() ? "Todavia no hay zonas. Marca A y B con la varita y crea una."
                    : "Sin resultados.";
            g.drawString(font, empty, PAD + 6, top + 8, 0xFF888888, false);
        }

        int max = maxScroll();
        if (max > 0) {
            int trackH = bottom - top;
            int barH = Math.max(16, trackH * trackH / (trackH + max));
            int barY = top + (trackH - barH) * scroll / max;
            g.fill(width - PAD - 3, barY, width - PAD, barY + barH, 0xFFAAAAAA);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawRow(GuiGraphics g, Row row, int y) {
        int textY = y + 5;
        int rightEdge = width - PAD;

        if (row.zone() != null) {
            // fila de la lista de zonas: nombre, descripcion y boton de borrar
            int deleteX = rightEdge - DELETE_W - 2;
            boolean confirming = row.id().equals(pendingDelete) && Util.getMillis() - pendingDeleteAt < 3000;
            g.fill(deleteX, y + 1, deleteX + DELETE_W, y + ROW_H - 3, confirming ? 0xFF803030 : 0xFF302028);
            g.drawCenteredString(font, confirming ? "Seguro?" : "Borrar", deleteX + DELETE_W / 2, textY, 0xFFFF7777);
            drawTitleAndSub(g, row, PAD + 4, deleteX - 6, textY);
            return;
        }

        int boxX = rightEdge - RIGHT_W - 2;
        String label;
        int color;
        if (openZone != null) {
            SpawnZone zone = ClientSpawnState.config.zone(openZone);
            boolean allowed = zone != null && zone.allowed().contains(row.id());
            label = allowed ? "[x] Permitido" : "[ ]";
            color = allowed ? 0xFF55FF55 : 0xFF888888;
        } else {
            SpawnMode own = ClientSpawnState.config.rules().get(row.id());
            if (own != null) {
                label = modeLabel(own);
                color = modeColor(own);
            } else {
                SpawnMode inherited = row.type() == null
                        ? SpawnMode.ZONES
                        : (ClientSpawnState.config.explicitMode(row.id()) != null
                                ? ClientSpawnState.config.explicitMode(row.id())
                                : SpawnConfig.defaultMode(row.type()));
                label = row.type() == null ? "Auto" : "Auto (" + modeLabel(inherited) + ")";
                color = 0xFFAAAAAA;
            }
        }
        g.fill(boxX, y + 1, boxX + RIGHT_W, y + ROW_H - 3, 0xFF1C1C28);
        g.drawCenteredString(font, label, boxX + RIGHT_W / 2, textY, color);
        drawTitleAndSub(g, row, PAD + 4, boxX - 6, textY);
    }

    private void drawTitleAndSub(GuiGraphics g, Row row, int x, int maxX, int textY) {
        int avail = maxX - x;
        String title = row.title();
        if (font.width(title) > avail) {
            g.drawString(font, font.plainSubstrByWidth(title, avail), x, textY, 0xFFFFFFFF, false);
            return;
        }
        g.drawString(font, title, x, textY, 0xFFFFFFFF, false);
        int subX = x + font.width(title) + 8;
        int subAvail = maxX - subX;
        if (subAvail > 30 && !row.sub().equals(row.title())) {
            g.drawString(font, font.plainSubstrByWidth(row.sub(), subAvail), subX, textY, 0xFF777777, false);
        }
    }

    private static String modeLabel(SpawnMode mode) {
        return switch (mode) {
            case NATURAL -> "Natural";
            case ZONES -> "Solo zonas";
            case NEVER -> "Nunca";
        };
    }

    private static int modeColor(SpawnMode mode) {
        return switch (mode) {
            case NATURAL -> 0xFF55FF55;
            case ZONES -> 0xFFFFFF55;
            case NEVER -> 0xFFFF5555;
        };
    }

    // ---------------------------------------------------------------- entrada

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - (int) (delta * ROW_H * 2), 0, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (mouseX >= PAD && mouseX < width - PAD && mouseY >= LIST_TOP && mouseY < listBottom()) {
            int index = (int) ((mouseY - LIST_TOP + scroll) / ROW_H);
            if (index >= 0 && index < rows.size() && (mouseY - LIST_TOP + scroll) % ROW_H < ROW_H - 2) {
                clickRow(rows.get(index), mouseX, button);
                return true;
            }
        }
        return false;
    }

    private void clickRow(Row row, double mouseX, int button) {
        if (row.zone() != null) {
            if (mouseX >= width - PAD - DELETE_W - 2) {
                boolean confirming = row.id().equals(pendingDelete) && Util.getMillis() - pendingDeleteAt < 3000;
                if (confirming) {
                    pendingDelete = null;
                    send(SpawnActionPacket.Action.DELETE_ZONE, row.id(), "", 0);
                } else {
                    pendingDelete = row.id();
                    pendingDeleteAt = Util.getMillis();
                }
            } else {
                openZone = row.id();
                boxText = "";
                scroll = 0;
                rebuildWidgets();
            }
            return;
        }

        if (openZone != null) {
            SpawnZone zone = ClientSpawnState.config.zone(openZone);
            boolean allowed = zone != null && zone.allowed().contains(row.id());
            send(SpawnActionPacket.Action.TOGGLE_ALLOW, openZone, row.id(), allowed ? 0 : 1);
            return;
        }

        SpawnMode own = ClientSpawnState.config.rules().get(row.id());
        int index = 0;
        for (int i = 0; i < CYCLE_ORDER.length; i++) {
            if (CYCLE_ORDER[i] == own) {
                index = i;
            }
        }
        int step = button == 1 ? CYCLE_ORDER.length - 1 : 1;
        SpawnMode next = CYCLE_ORDER[(index + step) % CYCLE_ORDER.length];
        send(SpawnActionPacket.Action.SET_RULE, row.id(), "", next == null ? -1 : next.ordinal());
    }
}
