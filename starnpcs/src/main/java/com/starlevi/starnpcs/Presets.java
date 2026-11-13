package com.starlevi.starnpcs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** NPCs ya armados: los que trae el mod y los guardados en config/starnpcs/presets. */
public final class Presets {
    private static final Map<String, JsonObject> PRESETS = new TreeMap<>();

    private Presets() {
    }

    public static synchronized void reload() {
        PRESETS.clear();
        try (InputStream in = Presets.class.getResourceAsStream("/starnpcs/presets.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    PRESETS.put(e.getKey(), e.getValue().getAsJsonObject());
                }
            }
        } catch (Exception e) {
            StarNpcs.LOG.error("[Star NPCs] No se pudieron leer los presets del mod", e);
        }
        Path dir = Assets.dir().resolve("presets");
        if (Files.isDirectory(dir)) {
            try (Stream<Path> list = Files.list(dir)) {
                for (Path p : (Iterable<Path>) list::iterator) {
                    String name = p.getFileName().toString();
                    if (!name.endsWith(".json")) {
                        continue;
                    }
                    try {
                        JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                        if (!o.has("npcs")) {
                            // un perfil suelto guardado con /starnpcs preset save
                            JsonObject npc = new JsonObject();
                            npc.add("profile", o);
                            JsonArray arr = new JsonArray();
                            arr.add(npc);
                            o = new JsonObject();
                            o.add("npcs", arr);
                        }
                        PRESETS.put(name.substring(0, name.length() - 5), o);
                    } catch (Exception e) {
                        StarNpcs.LOG.warn("[Star NPCs] El preset {} esta mal escrito", name, e);
                    }
                }
            } catch (Exception e) {
                StarNpcs.LOG.warn("[Star NPCs] No se pudo leer la carpeta de presets", e);
            }
        }
    }

    public static synchronized List<String> ids() {
        if (PRESETS.isEmpty()) {
            reload();
        }
        return new ArrayList<>(PRESETS.keySet());
    }

    public static synchronized boolean exists(String id) {
        return ids().contains(id);
    }

    /** Mezcla el perfil del preset (su primer NPC) sobre uno existente y le da el equipo. */
    public static synchronized boolean applyTo(NpcEntity npc, Profile current, String id) {
        if (!exists(id)) {
            return false;
        }
        JsonObject first = PRESETS.get(id).getAsJsonArray("npcs").get(0).getAsJsonObject();
        JsonObject merged = JsonParser.parseString(current.toJson()).getAsJsonObject();
        if (first.has("profile")) {
            for (Map.Entry<String, JsonElement> e : first.getAsJsonObject("profile").entrySet()) {
                if (!e.getKey().equals("name") || current.name.equals("NPC")) {
                    merged.add(e.getKey(), e.getValue());
                }
            }
        }
        npc.setProfile(Profile.fromJson(merged.toString()));
        equip(npc, first);
        return true;
    }

    /** Crea todos los NPCs del preset alrededor de ese punto. dx = a su derecha, dz = hacia adelante. */
    public static synchronized List<NpcEntity> spawn(String id, ServerLevel level, Vec3 at, float yaw) {
        List<NpcEntity> out = new ArrayList<>();
        if (!exists(id)) {
            return out;
        }
        JsonArray npcs = PRESETS.get(id).getAsJsonArray("npcs");
        String group = npcs.size() > 1 ? id + "_" + Long.toString(level.getGameTime() % 100000, 36) : "";
        float rad = yaw * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(rad), fz = Mth.cos(rad);
        double rx = -Mth.cos(rad), rz = -Mth.sin(rad);
        for (JsonElement el : npcs) {
            JsonObject o = el.getAsJsonObject();
            double dx = num(o, "dx"), dz = num(o, "dz");
            Profile p = Profile.fromJson(o.has("profile") ? o.get("profile").toString() : "{}");
            p.epoch = level.getGameTime();
            if (!group.isEmpty() && p.group.isBlank()) {
                p.group = group;
            }
            Vec3 pos = at.add(fx * dz + rx * dx, num(o, "dy"), fz * dz + rz * dx);
            NpcEntity npc = NpcEntity.spawn(level, pos, yaw + (float) num(o, "yaw"), p);
            if (npc != null) {
                equip(npc, o);
                out.add(npc);
            }
        }
        return out;
    }

    private static void equip(NpcEntity npc, JsonObject o) {
        if (o.has("mainhand")) {
            npc.setItemSlot(EquipmentSlot.MAINHAND, stack(o.get("mainhand").getAsString()));
        }
        if (o.has("offhand")) {
            npc.setItemSlot(EquipmentSlot.OFFHAND, stack(o.get("offhand").getAsString()));
        }
    }

    private static ItemStack stack(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static double num(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsDouble() : 0;
    }
}
