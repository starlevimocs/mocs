package com.starlevi.starinteractions.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starlevi.starinteractions.StarInteractions;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Animacion por keyframes (estilo Blockbench), para una o dos personas. Mismo formato que las del fork de MCA:
 * {@code assets/starinteractions/animations/<nombre>.json}. Participantes: "player" (quien empieza),
 * "villager" (la otra persona) y "self" (emotes).
 */
public final class Anim {
    public final float length;
    public final boolean loop;
    public final float blendIn;
    public final float blendOut;
    public final float distance;
    public final boolean thirdPerson;
    private final Map<String, Map<String, Bone>> participants = new HashMap<>();

    private static final Map<String, Optional<Anim>> CACHE = new HashMap<>();

    private Anim(JsonObject json) {
        length = json.has("length") ? json.get("length").getAsFloat() : 1.0F;
        loop = json.has("loop") && json.get("loop").getAsBoolean();
        blendIn = json.has("blend_in") ? json.get("blend_in").getAsFloat() : 0.2F;
        blendOut = json.has("blend_out") ? json.get("blend_out").getAsFloat() : 0.2F;
        distance = json.has("distance") ? json.get("distance").getAsFloat() : 12.0F;
        thirdPerson = json.has("camera") && "third_person".equals(json.get("camera").getAsString());
        JsonObject parts = json.getAsJsonObject("participants");
        for (Map.Entry<String, JsonElement> p : parts.entrySet()) {
            Map<String, Bone> bones = new HashMap<>();
            JsonObject b = p.getValue().getAsJsonObject().getAsJsonObject("bones");
            for (Map.Entry<String, JsonElement> e : b.entrySet()) {
                bones.put(e.getKey(), new Bone(e.getValue().getAsJsonObject()));
            }
            participants.put(p.getKey(), bones);
        }
    }

    public static Optional<Anim> get(String name) {
        return CACHE.computeIfAbsent(name, n -> {
            try {
                Optional<Resource> res = Minecraft.getInstance().getResourceManager()
                        .getResource(new ResourceLocation(StarInteractions.MOD_ID, "star_poses/" + n + ".json"));
                if (res.isEmpty()) {
                    return Optional.empty();
                }
                try (Reader reader = res.get().openAsReader()) {
                    return Optional.of(new Anim(JsonParser.parseReader(reader).getAsJsonObject()));
                }
            } catch (Exception e) {
                StarInteractions.LOGGER.warn("[Star Interactions] No se pudo leer la animacion {}", n, e);
                return Optional.empty();
            }
        });
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public Map<String, Bone> bones(String participant) {
        return participants.getOrDefault(participant, Collections.emptyMap());
    }

    /** Peso de la animacion (entrada y salida suaves). */
    public float weight(float t) {
        if (loop) {
            return Math.min(1, blendIn <= 0 ? 1 : t / blendIn);
        }
        float a = blendIn <= 0 ? 1 : Math.min(1, t / blendIn);
        float b = blendOut <= 0 ? 1 : Math.min(1, (length - t) / blendOut);
        float w = Math.max(0, Math.min(a, b));
        return w * w * (3 - 2 * w);
    }

    public static final class Bone {
        public final Channel rotation;
        public final Channel position;

        Bone(JsonObject json) {
            boolean linear = json.has("interpolation") && "linear".equals(json.get("interpolation").getAsString());
            rotation = json.has("rotation") ? new Channel(json.getAsJsonObject("rotation"), linear) : null;
            position = json.has("position") ? new Channel(json.getAsJsonObject("position"), linear) : null;
        }
    }

    public static final class Channel {
        private final float[] times;
        private final float[][] values;
        private final boolean linear;

        Channel(JsonObject json, boolean linear) {
            this.linear = linear;
            TreeMap<Float, float[]> sorted = new TreeMap<>();
            for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                var arr = e.getValue().getAsJsonArray();
                sorted.put(Float.parseFloat(e.getKey()), new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()});
            }
            times = new float[sorted.size()];
            values = new float[sorted.size()][];
            int i = 0;
            for (Map.Entry<Float, float[]> e : sorted.entrySet()) {
                times[i] = e.getKey();
                values[i++] = e.getValue();
            }
        }

        public float[] sample(float t) {
            int n = times.length;
            if (n == 1 || t <= times[0]) {
                return values[0];
            }
            if (t >= times[n - 1]) {
                return values[n - 1];
            }
            int i = 0;
            while (i < n - 2 && t > times[i + 1]) {
                i++;
            }
            float u = (t - times[i]) / (times[i + 1] - times[i]);
            float[] p1 = values[i], p2 = values[i + 1];
            float[] out = new float[3];
            if (linear) {
                for (int k = 0; k < 3; k++) {
                    out[k] = p1[k] + (p2[k] - p1[k]) * u;
                }
                return out;
            }
            float[] p0 = i > 0 ? values[i - 1] : p1;
            float[] p3 = i + 2 < n ? values[i + 2] : p2;
            float u2 = u * u, u3 = u2 * u;
            for (int k = 0; k < 3; k++) {
                out[k] = 0.5F * ((2 * p1[k]) + (-p0[k] + p2[k]) * u
                        + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * u2
                        + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * u3);
            }
            return out;
        }
    }
}
