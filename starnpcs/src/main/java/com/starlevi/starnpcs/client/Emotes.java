package com.starlevi.starnpcs.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee emotes de Emotecraft (.json) y los pasa a los huesos del modelo humano. Experimental: las curvas se
 * simplifican a lineal o suave y los codos y rodillas se doblan solo hacia adelante y atras.
 */
final class Emotes {
    private static final Map<String, String> PARTS = Map.of(
            "head", "head", "torso", "waist", "rightArm", "right_arm", "leftArm", "left_arm",
            "rightLeg", "right_leg", "leftLeg", "left_leg");
    private static final Map<String, String> BENDS = Map.of(
            "right_arm", "right_forearm", "left_arm", "left_forearm", "right_leg", "right_shin", "left_leg", "left_shin", "waist", "chest");
    /** posicion de reposo de cada parte en el modelo vanilla */
    private static final Map<String, float[]> REST = Map.of(
            "head", new float[]{0, 0, 0}, "torso", new float[]{0, 0, 0},
            "rightArm", new float[]{-5, 2, 0}, "leftArm", new float[]{5, 2, 0},
            "rightLeg", new float[]{-1.9F, 12, 0.1F}, "leftLeg", new float[]{1.9F, 12, 0.1F});

    private Emotes() {
    }

    private record Key(float time, float value, boolean smooth) {
    }

    static Anim parse(String name, JsonObject root) {
        if (!root.has("emote") || !root.get("emote").isJsonObject()) {
            return null;
        }
        JsonObject emote = root.getAsJsonObject("emote");
        boolean degrees = emote.has("degrees") && emote.get("degrees").getAsBoolean();
        float end = Anim.num(emote.get("stopTick"), Anim.num(emote.get("endTick"), 20));
        Anim a = new Anim();
        a.name = name;
        a.loop = emote.has("isLoop") && emote.get("isLoop").getAsString().equalsIgnoreCase("true") ? 1 : 0;
        a.length = Math.max(0.05F, end / 20F);
        // hueso -> canal (0..8: rot xyz, pos xyz) -> claves
        Map<String, List<List<Key>>> tracks = new HashMap<>();
        if (emote.has("moves") && emote.get("moves").isJsonArray()) {
            for (JsonElement el : emote.getAsJsonArray("moves")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject move = el.getAsJsonObject();
                float time = Anim.num(move.get("tick"), 0) / 20F;
                boolean smooth = !Anim.str(move, "easing").toLowerCase().contains("linear");
                for (Map.Entry<String, String> part : PARTS.entrySet()) {
                    JsonElement pe = move.get(part.getKey());
                    if (pe == null || !pe.isJsonObject()) {
                        continue;
                    }
                    JsonObject p = pe.getAsJsonObject();
                    float[] rest = REST.get(part.getKey());
                    String bone = part.getValue();
                    float k = degrees ? 1F : (float) (180 / Math.PI);
                    put(tracks, bone, 0, p, "pitch", time, smooth, k, 0);
                    put(tracks, bone, 1, p, "yaw", time, smooth, k, 0);
                    put(tracks, bone, 2, p, "roll", time, smooth, k, 0);
                    put(tracks, bone, 3, p, "x", time, smooth, 1, -rest[0]);
                    put(tracks, bone, 4, p, "y", time, smooth, -1, rest[1]);
                    put(tracks, bone, 5, p, "z", time, smooth, 1, -rest[2]);
                    String bend = BENDS.get(bone);
                    if (bend != null && p.has("bend")) {
                        boolean arm = bone.endsWith("arm");
                        put(tracks, bend, 0, p, "bend", time, smooth, arm ? -k : k, 0);
                    }
                }
            }
        }
        for (Map.Entry<String, List<List<Key>>> e : tracks.entrySet()) {
            Anim.Channel[] chans = {channel(e.getValue(), 0), channel(e.getValue(), 3), new Anim.Channel()};
            a.bones.put(e.getKey(), chans);
        }
        return a.bones.isEmpty() ? null : a;
    }

    private static void put(Map<String, List<List<Key>>> tracks, String bone, int chan, JsonObject p, String key,
                            float time, boolean smooth, float mul, float add) {
        if (!p.has(key)) {
            return;
        }
        List<List<Key>> list = tracks.computeIfAbsent(bone, b -> {
            List<List<Key>> l = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                l.add(new ArrayList<>());
            }
            return l;
        });
        list.get(chan).add(new Key(time, Anim.num(p.get(key), 0) * mul + add, smooth));
    }

    /** Junta tres ejes sueltos en un canal: en cada tiempo con alguna clave, toma el valor de cada eje ahi. */
    private static Anim.Channel channel(List<List<Key>> tracks, int first) {
        java.util.TreeSet<Float> times = new java.util.TreeSet<>();
        for (int c = 0; c < 3; c++) {
            tracks.get(first + c).sort(java.util.Comparator.comparingDouble(Key::time));
            for (Key k : tracks.get(first + c)) {
                times.add(k.time());
            }
        }
        Anim.Channel ch = new Anim.Channel();
        int n = times.size();
        ch.t = new float[n];
        ch.in = new float[n][];
        ch.out = new float[n][];
        ch.mode = new byte[n];
        int i = 0;
        for (float t : times) {
            float[] v = new float[3];
            boolean smooth = false;
            for (int c = 0; c < 3; c++) {
                List<Key> keys = tracks.get(first + c);
                v[c] = at(keys, t);
                for (Key k : keys) {
                    if (k.time() == t && k.smooth()) {
                        smooth = true;
                    }
                }
            }
            ch.t[i] = t;
            ch.in[i] = v;
            ch.out[i] = v;
            ch.mode[i] = (byte) (smooth ? 1 : 0);
            i++;
        }
        return ch;
    }

    private static float at(List<Key> keys, float t) {
        if (keys.isEmpty()) {
            return 0;
        }
        Key prev = keys.get(0);
        if (t <= prev.time()) {
            return prev.value();
        }
        for (Key k : keys) {
            if (k.time() >= t) {
                float span = k.time() - prev.time();
                return span <= 0 ? k.value() : prev.value() + (k.value() - prev.value()) * (t - prev.time()) / span;
            }
            prev = k;
        }
        return prev.value();
    }
}
