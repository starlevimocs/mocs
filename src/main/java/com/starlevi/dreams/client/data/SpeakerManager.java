package com.starlevi.dreams.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.starlevi.dreams.dialogue.Speaker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Carga los personajes desde assets/&lt;namespace&gt;/speakers/&lt;nombre&gt;.json (cliente).
 */
public class SpeakerManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();

    private static Map<ResourceLocation, Speaker> speakers = Map.of();

    public SpeakerManager() {
        super(GSON, "speakers");
    }

    public static Speaker get(ResourceLocation id) {
        Speaker speaker = speakers.get(id);
        return speaker != null ? speaker : Speaker.unknown(id);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Speaker> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            try {
                loaded.put(entry.getKey(), parse(entry.getKey(), GsonHelper.convertToJsonObject(entry.getValue(), "personaje")));
            } catch (Exception e) {
                LOGGER.error("[Dreams] Error en el personaje {}: {}", entry.getKey(), e.getMessage());
            }
        }
        speakers = loaded;
        LOGGER.info("[Dreams] {} personaje(s) cargados.", loaded.size());
    }

    private static Speaker parse(ResourceLocation id, JsonObject json) {
        String name = GsonHelper.getAsString(json, "name", id.getPath());
        int color = parseColor(GsonHelper.getAsString(json, "color", "#FFFFFF"));

        Map<String, ResourceLocation> portraits = new HashMap<>();
        if (json.has("portraits")) {
            JsonObject p = GsonHelper.getAsJsonObject(json, "portraits");
            for (Map.Entry<String, JsonElement> e : p.entrySet()) {
                portraits.put(e.getKey(), new ResourceLocation(GsonHelper.convertToString(e.getValue(), "retrato")));
            }
        }

        Speaker.Voice voice = Speaker.Voice.DEFAULT;
        if (json.has("voice")) {
            JsonObject v = GsonHelper.getAsJsonObject(json, "voice");
            voice = new Speaker.Voice(
                    new ResourceLocation(GsonHelper.getAsString(v, "sound", Speaker.Voice.DEFAULT.sound().toString())),
                    GsonHelper.getAsFloat(v, "pitch", 1.0F),
                    GsonHelper.getAsFloat(v, "variance", 0.1F),
                    GsonHelper.getAsFloat(v, "volume", 0.6F));
        }
        return new Speaker(id, name, color, Map.copyOf(portraits), voice, GsonHelper.getAsBoolean(json, "tree", false));
    }

    public static int parseColor(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        return Integer.parseInt(hex, 16) & 0xFFFFFF;
    }
}
