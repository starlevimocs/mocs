package com.starlevi.dreams.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starlevi.dreams.DreamsMod;
import com.starlevi.dreams.dialogue.Dialogue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Las misiones del jugador en el cliente: para las condiciones de los dialogos y para el cartel en pantalla. */
public final class ClientQuests {
    public record Objective(String label, int have, int need) {
    }

    public record Quest(String id, String title, String description, boolean community, String state, List<Objective> objectives) {
    }

    private static List<Quest> quests = List.of();

    private ClientQuests() {
    }

    public static void apply(String json) {
        List<Quest> out = new ArrayList<>();
        try {
            for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject q = e.getAsJsonObject();
                List<Objective> objs = new ArrayList<>();
                for (JsonElement oe : q.getAsJsonArray("objectives")) {
                    JsonObject o = oe.getAsJsonObject();
                    objs.add(new Objective(label(o), o.get("have").getAsInt(), o.get("need").getAsInt()));
                }
                out.add(new Quest(q.get("id").getAsString(), q.get("title").getAsString(), q.get("description").getAsString(),
                        q.get("community").getAsBoolean(), q.get("state").getAsString(), List.copyOf(objs)));
            }
        } catch (RuntimeException ex) {
            return;
        }
        quests = List.copyOf(out);
    }

    /** Nombre del objetivo: el que diga la mision, o el del item / la criatura en el idioma del jugador. */
    private static String label(JsonObject o) {
        String label = o.get("label").getAsString();
        if (!label.isEmpty()) {
            return label;
        }
        ResourceLocation target = ResourceLocation.tryParse(o.get("target").getAsString());
        String type = o.get("type").getAsString();
        if (target != null && type.equals("item")) {
            Item item = ForgeRegistries.ITEMS.getValue(target);
            if (item != null) {
                return item.getDescription().getString();
            }
        }
        if (target != null && type.equals("kill")) {
            EntityType<?> entity = ForgeRegistries.ENTITY_TYPES.getValue(target);
            if (entity != null) {
                return Component.translatable("gui.dreams.quest_kill", entity.getDescription()).getString();
            }
        }
        return Component.translatable("gui.dreams.quest_visit").getString();
    }

    public static Quest get(String id) {
        for (Quest q : quests) {
            if (q.id.equals(id)) {
                return q;
            }
        }
        return null;
    }

    public static boolean test(Dialogue.Cond cond) {
        if (cond == null) {
            return true;
        }
        Quest q = get(cond.quest());
        boolean is = (q == null ? "locked" : q.state).equals(cond.state());
        return is != cond.not();
    }

    /** "12/32" del primer objetivo sin terminar (para escribir {progress:mision} en un dialogo). */
    public static String progressText(String id) {
        Quest q = get(id);
        if (q == null || q.objectives.isEmpty()) {
            return "0";
        }
        for (Objective o : q.objectives) {
            if (o.have < o.need) {
                return o.have + "/" + o.need;
            }
        }
        Objective last = q.objectives.get(q.objectives.size() - 1);
        return last.have + "/" + last.need;
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.options.renderDebug || quests.isEmpty()) {
            return;
        }
        Font font = gui.getFont();
        int y = 8, shown = 0;
        for (Quest q : quests) {
            if (!(q.state.equals("active") || q.state.equals("ready")) || shown >= 3) {
                continue;
            }
            shown++;
            int w = font.width(q.title) + 12;
            for (Objective o : q.objectives) {
                w = Math.max(w, font.width(o.label + " " + o.have + "/" + o.need) + 18);
            }
            String hint = q.state.equals("ready") ? Component.translatable("gui.dreams.quest_ready").getString() : null;
            if (hint != null) {
                w = Math.max(w, font.width(hint) + 12);
            }
            w = Math.min(w, width / 3);
            int h = 14 + q.objectives.size() * 10 + (hint != null ? 11 : 0) + 3;
            int x = width - w - 6;
            g.fill(x, y, x + w, y + h, 0x90101018);
            g.fill(x, y, x + 2, y + h, q.community ? 0xFF6FC8FF : 0xFF7FD99A);
            g.drawString(font, font.plainSubstrByWidth(q.title, w - 10), x + 6, y + 3, q.community ? 0xFF9ADCFF : 0xFFA8F0BE, true);
            int ly = y + 14;
            for (Objective o : q.objectives) {
                boolean ok = o.have >= o.need;
                String line = (ok ? "✔ " : "• ") + o.label + " " + o.have + "/" + o.need;
                g.drawString(font, font.plainSubstrByWidth(line, w - 10), x + 6, ly, ok ? 0xFF8FE39B : 0xFFE0E0E0, false);
                ly += 10;
            }
            if (hint != null) {
                g.drawString(font, font.plainSubstrByWidth(hint, w - 10), x + 6, ly + 1, 0xFFFFE08A, false);
            }
            y += h + 4;
        }
    }

    @Mod.EventBusSubscriber(modid = DreamsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("quests", ClientQuests::render);
        }
    }

    @Mod.EventBusSubscriber(modid = DreamsMod.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            quests = List.of();
        }
    }
}
