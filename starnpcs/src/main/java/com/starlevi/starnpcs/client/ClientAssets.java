package com.starlevi.starnpcs.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.starlevi.starnpcs.StarNpcs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

/** Modelos, skins y sonidos en el cliente: los que trae el mod y los que manda el servidor. */
final class ClientAssets {
    static final String[] BUILT_IN = {"human", "human_slim"};
    static int generation;

    private static final Map<String, byte[]> files = new LinkedHashMap<>();
    private static final Map<String, ByteArrayOutputStream> pending = new HashMap<>();
    private static final Map<String, BbModel> models = new HashMap<>();
    private static final Map<String, ResourceLocation> skins = new HashMap<>();
    private static final List<ResourceLocation> made = new ArrayList<>();
    private static boolean built;

    private ClientAssets() {
    }

    static void part(String path, int part, int parts, byte[] data) {
        if (path.isEmpty()) {
            files.clear();
            pending.clear();
            return;
        }
        if (path.equals("#done")) {
            pending.clear();
            rebuild();
            return;
        }
        ByteArrayOutputStream out = pending.computeIfAbsent(path, k -> new ByteArrayOutputStream());
        out.writeBytes(data);
        if (part >= parts - 1) {
            pending.remove(path);
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(out.toByteArray()))) {
                files.put(path, gz.readAllBytes());
            } catch (Exception e) {
                StarNpcs.LOG.warn("[Star NPCs] Llego mal el archivo {}", path, e);
            }
        }
    }

    /** Al salir de un mundo: vuelve a lo que trae el mod. */
    static void reset() {
        files.clear();
        pending.clear();
        built = false;
    }

    static BbModel model(String id) {
        if (!built) {
            rebuild();
        }
        return models.get(id);
    }

    static List<String> modelIds() {
        if (!built) {
            rebuild();
        }
        return new ArrayList<>(new TreeSet<>(models.keySet()));
    }

    static ResourceLocation skinFile(String name) {
        if (!built) {
            rebuild();
        }
        return skins.get(name.toLowerCase());
    }

    static String safe(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
    }

    private static void rebuild() {
        built = true;
        generation++;
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation loc : made) {
            mc.getTextureManager().release(loc);
        }
        made.clear();
        models.clear();
        skins.clear();
        for (String id : BUILT_IN) {
            try (InputStream in = ClientAssets.class.getResourceAsStream("/assets/starnpcs/npcmodels/" + id + ".bbmodel")) {
                if (in != null) {
                    add(id, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
                }
            } catch (Exception e) {
                StarNpcs.LOG.error("[Star NPCs] No se pudo leer el modelo {}", id, e);
            }
        }
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String path = e.getKey();
            String name = path.substring(path.indexOf('/') + 1);
            try {
                if (path.startsWith("models/")) {
                    add(name.substring(0, name.length() - ".bbmodel".length()),
                            JsonParser.parseString(new String(e.getValue(), StandardCharsets.UTF_8)).getAsJsonObject());
                } else if (path.startsWith("skins/")) {
                    NativeImage img = NativeImage.read(new ByteArrayInputStream(e.getValue()));
                    String key = name.substring(0, name.length() - 4).toLowerCase();
                    ResourceLocation loc = new ResourceLocation("starnpcs", "dyn/skin_" + safe(key) + "_" + generation);
                    mc.getTextureManager().register(loc, new DynamicTexture(img));
                    made.add(loc);
                    skins.put(key, loc);
                }
            } catch (Exception ex) {
                StarNpcs.LOG.warn("[Star NPCs] No se pudo leer {}", path, ex);
            }
        }
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            if (e.getKey().startsWith("emotes/")) {
                String name = e.getKey().substring(7).replaceAll("\\.json$", "");
                try {
                    Anim a = Emotes.parse(safe(name), JsonParser.parseString(new String(e.getValue(), StandardCharsets.UTF_8)).getAsJsonObject());
                    if (a != null) {
                        for (String id : BUILT_IN) {
                            BbModel m = models.get(id);
                            if (m != null) {
                                m.anims.put(a.name, a);
                            }
                        }
                    }
                } catch (Exception ex) {
                    StarNpcs.LOG.warn("[Star NPCs] No se pudo leer el emote {}", name, ex);
                }
            }
        }
        byte[] sounds = files.get("sounds.json");
        if (sounds != null) {
            try {
                JsonObject root = JsonParser.parseString(new String(sounds, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    int slash = e.getKey().indexOf('/');
                    if (slash < 0 || !e.getValue().isJsonArray()) {
                        continue;
                    }
                    BbModel m = models.get(e.getKey().substring(0, slash));
                    Anim a = m == null ? null : m.anims.get(e.getKey().substring(slash + 1));
                    if (a == null) {
                        continue;
                    }
                    JsonArray arr = e.getValue().getAsJsonArray();
                    for (JsonElement el : arr) {
                        JsonObject o = el.getAsJsonObject();
                        Anim.Fx f = Anim.fx(false, Anim.num(o.get("time"), 0), Anim.str(o, "sound"));
                        if (f != null) {
                            f.a = Anim.num(o.get("volume"), 1);
                            f.b = Anim.num(o.get("pitch"), 1);
                            a.fx.add(f);
                        }
                    }
                    a.fx.sort(java.util.Comparator.comparingDouble(f -> f.time));
                }
            } catch (Exception ex) {
                StarNpcs.LOG.warn("[Star NPCs] sounds.json esta mal escrito", ex);
            }
        }
        // el modelo de brazos finos comparte sonidos con el normal
        Skins.clearFiles();
    }

    private static void add(String id, JsonObject root) {
        models.put(id, BbModel.parse(id, root, made));
    }
}
