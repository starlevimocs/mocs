package com.starlevi.starnpcs.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Un modelo de Blockbench (.bbmodel) listo para dibujar: huesos, cubos, texturas y animaciones. */
final class BbModel {
    static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    final String id;
    final List<Bone> roots = new ArrayList<>();
    final List<Bone> all = new ArrayList<>();
    final Map<String, Bone> bones = new HashMap<>();
    final Map<String, Anim> anims = new LinkedHashMap<>();
    final List<Tex> textures = new ArrayList<>();
    float resW = 64, resH = 64;

    static final class Tex {
        ResourceLocation loc;
        /** la reemplaza la skin del NPC */
        boolean skin;
        float uw = 64, uh = 64;
    }

    static final class Bone {
        String name = "";
        int index;
        float ox, oy, oz, rx, ry, rz;
        final List<Bone> kids = new ArrayList<>();
        final List<Cube> cubes = new ArrayList<>();
        /** prop_*: solo se ve si la animacion en curso lo anima */
        boolean prop;
        boolean skip;
        /** 1 = lleva el objeto de la mano derecha, 2 = el de la izquierda */
        int item;
    }

    static final class Cube {
        float x0, y0, z0, x1, y1, z1;
        float ox, oy, oz, rx, ry, rz;
        boolean rotated;
        final Face[] faces = new Face[6];
    }

    static final class Face {
        float u0, v0, u1, v1;
        int tex;
        int rot;
    }

    BbModel(String id) {
        this.id = id;
    }

    static BbModel parse(String id, JsonObject root, List<ResourceLocation> made) {
        BbModel m = new BbModel(id);
        if (root.has("resolution") && root.get("resolution").isJsonObject()) {
            JsonObject r = root.getAsJsonObject("resolution");
            m.resW = Math.max(1, Anim.num(r.get("width"), 64));
            m.resH = Math.max(1, Anim.num(r.get("height"), 64));
        }
        m.loadTextures(root, made);
        Map<String, JsonObject> elements = new HashMap<>();
        if (root.has("elements") && root.get("elements").isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray("elements")) {
                if (e.isJsonObject()) {
                    elements.put(Anim.str(e.getAsJsonObject(), "uuid"), e.getAsJsonObject());
                }
            }
        }
        // Blockbench 5 guarda los grupos aparte del arbol
        Map<String, JsonObject> groups = new HashMap<>();
        if (root.has("groups") && root.get("groups").isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray("groups")) {
                if (e.isJsonObject()) {
                    groups.put(Anim.str(e.getAsJsonObject(), "uuid"), e.getAsJsonObject());
                }
            }
        }
        if (root.has("outliner") && root.get("outliner").isJsonArray()) {
            Bone loose = null;
            for (JsonElement node : root.getAsJsonArray("outliner")) {
                if (node.isJsonObject()) {
                    m.roots.add(m.bone(node.getAsJsonObject(), groups, elements, false));
                } else if (node.isJsonPrimitive()) {
                    Cube c = m.cube(elements.get(node.getAsString()));
                    if (c != null) {
                        if (loose == null) {
                            loose = new Bone();
                            loose.name = "#sueltos";
                            loose.index = m.all.size();
                            m.all.add(loose);
                            m.roots.add(loose);
                        }
                        loose.cubes.add(c);
                    }
                }
            }
        }
        if (root.has("animations") && root.get("animations").isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray("animations")) {
                if (e.isJsonObject()) {
                    Anim a = Anim.parse(e.getAsJsonObject());
                    if (!a.name.isEmpty()) {
                        m.anims.put(a.name, a);
                    }
                }
            }
        }
        return m;
    }

    private Bone bone(JsonObject node, Map<String, JsonObject> groups, Map<String, JsonObject> elements, boolean skipParent) {
        JsonObject g = node;
        if (!node.has("name")) {
            JsonObject found = groups.get(Anim.str(node, "uuid"));
            if (found != null) {
                g = found;
            }
        }
        Bone b = new Bone();
        b.name = Anim.str(g, "name");
        b.index = all.size();
        all.add(b);
        bones.putIfAbsent(b.name, b);
        float[] o = vec(g.get("origin"), 0);
        b.ox = o[0];
        b.oy = o[1];
        b.oz = o[2];
        float[] r = vec(g.get("rotation"), 0);
        b.rx = r[0] * Mth.DEG_TO_RAD;
        b.ry = r[1] * Mth.DEG_TO_RAD;
        b.rz = r[2] * Mth.DEG_TO_RAD;
        String lower = b.name.toLowerCase();
        b.skip = skipParent || lower.equals("hitbox") || lower.equals("tag_name");
        b.prop = lower.startsWith("prop_");
        b.item = lower.equals("item_right") ? 1 : lower.equals("item_left") ? 2 : 0;
        JsonElement kids = node.get("children");
        if (kids != null && kids.isJsonArray()) {
            for (JsonElement k : kids.getAsJsonArray()) {
                if (k.isJsonObject()) {
                    b.kids.add(bone(k.getAsJsonObject(), groups, elements, b.skip));
                } else if (k.isJsonPrimitive() && !b.skip) {
                    Cube c = cube(elements.get(k.getAsString()));
                    if (c != null) {
                        b.cubes.add(c);
                    }
                }
            }
        }
        return b;
    }

    private Cube cube(JsonObject e) {
        if (e == null) {
            return null;
        }
        String type = Anim.str(e, "type");
        if (!type.isEmpty() && !type.equals("cube")) {
            return null;
        }
        if (!e.has("from") || !e.has("to")) {
            return null;
        }
        Cube c = new Cube();
        float[] from = vec(e.get("from"), 0), to = vec(e.get("to"), 0);
        float inf = Anim.num(e.get("inflate"), 0);
        c.x0 = Math.min(from[0], to[0]) - inf;
        c.y0 = Math.min(from[1], to[1]) - inf;
        c.z0 = Math.min(from[2], to[2]) - inf;
        c.x1 = Math.max(from[0], to[0]) + inf;
        c.y1 = Math.max(from[1], to[1]) + inf;
        c.z1 = Math.max(from[2], to[2]) + inf;
        float[] o = vec(e.get("origin"), 0), r = vec(e.get("rotation"), 0);
        c.ox = o[0];
        c.oy = o[1];
        c.oz = o[2];
        c.rx = r[0] * Mth.DEG_TO_RAD;
        c.ry = r[1] * Mth.DEG_TO_RAD;
        c.rz = r[2] * Mth.DEG_TO_RAD;
        c.rotated = r[0] != 0 || r[1] != 0 || r[2] != 0;
        JsonObject faces = e.has("faces") && e.get("faces").isJsonObject() ? e.getAsJsonObject("faces") : new JsonObject();
        for (int i = 0; i < 6; i++) {
            JsonElement fe = faces.get(FACES[i]);
            if (fe == null || !fe.isJsonObject()) {
                continue;
            }
            JsonObject fo = fe.getAsJsonObject();
            JsonElement tex = fo.get("texture");
            if (tex == null || !tex.isJsonPrimitive() || !tex.getAsJsonPrimitive().isNumber()) {
                continue;
            }
            int ti = tex.getAsInt();
            if (ti < 0 || ti >= textures.size() || !fo.has("uv")) {
                continue;
            }
            float[] uv = vec4(fo.get("uv"));
            Tex t = textures.get(ti);
            Face f = new Face();
            f.tex = ti;
            f.u0 = uv[0] / t.uw;
            f.v0 = uv[1] / t.uh;
            f.u1 = uv[2] / t.uw;
            f.v1 = uv[3] / t.uh;
            f.rot = ((int) Anim.num(fo.get("rotation"), 0) / 90) & 3;
            c.faces[i] = f;
        }
        return c;
    }

    private void loadTextures(JsonObject root, List<ResourceLocation> made) {
        if (!root.has("textures") || !root.get("textures").isJsonArray()) {
            return;
        }
        JsonArray arr = root.getAsJsonArray("textures");
        for (int i = 0; i < arr.size(); i++) {
            Tex t = new Tex();
            t.uw = resW;
            t.uh = resH;
            textures.add(t);
            if (!arr.get(i).isJsonObject()) {
                continue;
            }
            JsonObject o = arr.get(i).getAsJsonObject();
            t.uw = Math.max(1, Anim.num(o.get("uv_width"), resW));
            t.uh = Math.max(1, Anim.num(o.get("uv_height"), resH));
            String name = Anim.str(o, "name").toLowerCase();
            if (name.contains("skin")) {
                t.skin = true;
                continue;
            }
            String src = Anim.str(o, "source");
            int comma = src.indexOf(',');
            if (!src.startsWith("data:") || comma < 0) {
                continue;
            }
            try {
                byte[] png = Base64.getDecoder().decode(src.substring(comma + 1));
                NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
                ResourceLocation loc = new ResourceLocation("starnpcs", "dyn/" + ClientAssets.safe(id) + "_" + i + "_" + ClientAssets.generation);
                Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(img));
                made.add(loc);
                t.loc = loc;
            } catch (Exception e) {
                com.starlevi.starnpcs.StarNpcs.LOG.warn("[Star NPCs] No se pudo leer la textura {} del modelo {}", i, id, e);
            }
        }
    }

    private static float[] vec(JsonElement e, float def) {
        float[] v = {def, def, def};
        if (e != null && e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (int i = 0; i < 3 && i < a.size(); i++) {
                v[i] = Anim.num(a.get(i), def);
            }
        }
        return v;
    }

    private static float[] vec4(JsonElement e) {
        float[] v = new float[4];
        if (e != null && e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (int i = 0; i < 4 && i < a.size(); i++) {
                v[i] = Anim.num(a.get(i), 0);
            }
        }
        return v;
    }
}
