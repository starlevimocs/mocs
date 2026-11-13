package com.starlevi.starnpcs.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Una animacion de Blockbench: por hueso, canales de rotacion, posicion y escala, mas sonidos y particulas. */
final class Anim {
    static final int ROT = 0, POS = 1, SCALE = 2;

    String name = "";
    float length;
    /** 0 = una vez, 1 = bucle, 2 = se queda en el ultimo cuadro */
    int loop;
    final Map<String, Channel[]> bones = new HashMap<>();
    final List<Fx> fx = new ArrayList<>();

    static final class Fx {
        float time;
        /** false = sonido, true = particula */
        boolean particle;
        String id = "";
        float a = 1F, b = 1F;
        float x, y, z;
        int count = 1;
    }

    static final class Channel {
        float[] t = new float[0];
        float[][] in = new float[0][];
        float[][] out = new float[0][];
        /** 0 lineal, 1 suave (catmull-rom), 2 escalon */
        byte[] mode = new byte[0];

        void sample(float time, float[] dst, int off) {
            int n = t.length;
            if (n == 0) {
                return;
            }
            if (time <= t[0]) {
                put(dst, off, in[0]);
                return;
            }
            if (time >= t[n - 1]) {
                put(dst, off, out[n - 1]);
                return;
            }
            int i = 0;
            while (i < n - 2 && t[i + 1] <= time) {
                i++;
            }
            float span = t[i + 1] - t[i];
            float k = span <= 0 ? 1 : (time - t[i]) / span;
            float[] a = out[i], b = in[i + 1];
            if (mode[i] == 2) {
                put(dst, off, a);
            } else if (mode[i] == 1 || mode[i + 1] == 1) {
                float[] p0 = i > 0 ? out[i - 1] : a;
                float[] p3 = i + 2 < n ? in[i + 2] : b;
                for (int c = 0; c < 3; c++) {
                    dst[off + c] = catmull(p0[c], a[c], b[c], p3[c], k);
                }
            } else {
                for (int c = 0; c < 3; c++) {
                    dst[off + c] = a[c] + (b[c] - a[c]) * k;
                }
            }
        }

        private static void put(float[] dst, int off, float[] v) {
            dst[off] = v[0];
            dst[off + 1] = v[1];
            dst[off + 2] = v[2];
        }

        private static float catmull(float p0, float p1, float p2, float p3, float k) {
            float k2 = k * k, k3 = k2 * k;
            return 0.5F * (2 * p1 + (p2 - p0) * k + (2 * p0 - 5 * p1 + 4 * p2 - p3) * k2 + (3 * p1 - p0 - 3 * p2 + p3) * k3);
        }
    }

    boolean has(String bone) {
        return bones.containsKey(bone);
    }

    /** Tiempo dentro de la animacion para ese tiempo transcurrido. */
    float wrap(double seconds) {
        if (length <= 0) {
            return 0;
        }
        if (loop == 1) {
            double m = seconds % length;
            return (float) (m < 0 ? m + length : m);
        }
        return (float) Math.max(0, Math.min(length, seconds));
    }

    static Anim parse(JsonObject o) {
        Anim a = new Anim();
        a.name = str(o, "name");
        String loop = str(o, "loop");
        a.loop = loop.equals("loop") ? 1 : loop.equals("hold") ? 2 : 0;
        a.length = o.has("length") && o.get("length").isJsonPrimitive() ? o.get("length").getAsFloat() : 0;
        float last = 0;
        if (o.has("animators") && o.get("animators").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("animators").entrySet()) {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject an = e.getValue().getAsJsonObject();
                JsonArray keys = an.has("keyframes") && an.get("keyframes").isJsonArray() ? an.getAsJsonArray("keyframes") : new JsonArray();
                String type = str(an, "type");
                if (e.getKey().equals("effects") || type.equals("effect")) {
                    for (JsonElement k : keys) {
                        Fx f = fx(k.getAsJsonObject());
                        if (f != null) {
                            a.fx.add(f);
                            last = Math.max(last, f.time);
                        }
                    }
                    continue;
                }
                String bone = str(an, "name");
                if (bone.isEmpty()) {
                    continue;
                }
                List<List<JsonObject>> by = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                for (JsonElement k : keys) {
                    JsonObject kf = k.getAsJsonObject();
                    String ch = str(kf, "channel");
                    int c = ch.equals("rotation") ? ROT : ch.equals("position") ? POS : ch.equals("scale") ? SCALE : -1;
                    if (c >= 0) {
                        by.get(c).add(kf);
                    }
                }
                Channel[] chans = new Channel[3];
                for (int c = 0; c < 3; c++) {
                    chans[c] = channel(by.get(c), c == SCALE ? 1F : 0F);
                    if (chans[c].t.length > 0) {
                        last = Math.max(last, chans[c].t[chans[c].t.length - 1]);
                    }
                }
                a.bones.put(bone, chans);
            }
        }
        if (a.length <= 0) {
            a.length = last;
        }
        a.fx.sort(Comparator.comparingDouble(f -> f.time));
        return a;
    }

    private static Channel channel(List<JsonObject> keys, float def) {
        keys.sort(Comparator.comparingDouble(k -> num(k.get("time"), 0)));
        Channel c = new Channel();
        int n = keys.size();
        c.t = new float[n];
        c.in = new float[n][];
        c.out = new float[n][];
        c.mode = new byte[n];
        for (int i = 0; i < n; i++) {
            JsonObject k = keys.get(i);
            c.t[i] = num(k.get("time"), 0);
            String mode = str(k, "interpolation");
            c.mode[i] = (byte) (mode.equals("catmullrom") || mode.equals("bezier") ? 1 : mode.equals("step") ? 2 : 0);
            JsonArray dp = k.has("data_points") && k.get("data_points").isJsonArray() ? k.getAsJsonArray("data_points") : new JsonArray();
            c.in[i] = point(dp.size() > 0 ? dp.get(0) : null, def);
            c.out[i] = dp.size() > 1 ? point(dp.get(dp.size() - 1), def) : c.in[i];
        }
        return c;
    }

    private static float[] point(JsonElement e, float def) {
        float[] v = {def, def, def};
        if (e != null && e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            v[0] = num(o.get("x"), def);
            v[1] = num(o.get("y"), def);
            v[2] = num(o.get("z"), def);
        }
        return v;
    }

    /** sonido: "minecraft:block.anvil.place|volumen|tono". particula: "minecraft:crit|cantidad|x|y|z" (en pixeles del modelo). */
    private static Fx fx(JsonObject kf) {
        String ch = str(kf, "channel");
        if (!ch.equals("sound") && !ch.equals("particle")) {
            return null;
        }
        JsonArray dp = kf.has("data_points") && kf.get("data_points").isJsonArray() ? kf.getAsJsonArray("data_points") : new JsonArray();
        if (dp.size() == 0 || !dp.get(0).isJsonObject()) {
            return null;
        }
        return fx(ch.equals("particle"), num(kf.get("time"), 0), str(dp.get(0).getAsJsonObject(), "effect"));
    }

    static Fx fx(boolean particle, float time, String spec) {
        String[] parts = spec.trim().split("\\|");
        if (parts[0].isBlank()) {
            return null;
        }
        Fx f = new Fx();
        f.particle = particle;
        f.time = time;
        f.id = parts[0].trim();
        if (particle) {
            f.count = Math.max(1, Math.min(64, (int) part(parts, 1, 1)));
            f.x = part(parts, 2, 0);
            f.y = part(parts, 3, 16);
            f.z = part(parts, 4, 0);
        } else {
            f.a = part(parts, 1, 1);
            f.b = part(parts, 2, 1);
        }
        return f;
    }

    private static float part(String[] parts, int i, float def) {
        if (i >= parts.length) {
            return def;
        }
        try {
            return Float.parseFloat(parts[i].trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    static float num(JsonElement e, float def) {
        if (e == null || !e.isJsonPrimitive()) {
            return def;
        }
        try {
            String s = e.getAsString().trim();
            return s.isEmpty() ? def : Float.parseFloat(s);
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}
