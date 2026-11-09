package com.starlevi.dreams.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.starlevi.dreams.dialogue.Dialogue;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga los dialogos desde assets/&lt;namespace&gt;/dialogues/&lt;nombre&gt;.json (cliente).
 */
public class DialogueManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();

    private static Map<ResourceLocation, Dialogue> dialogues = Map.of();

    public DialogueManager() {
        super(GSON, "dialogues");
    }

    public static Dialogue get(ResourceLocation id) {
        return dialogues.get(id);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Dialogue> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            try {
                Dialogue dialogue = parse(entry.getKey(), GsonHelper.convertToJsonObject(entry.getValue(), "dialogo"));
                loaded.put(entry.getKey(), dialogue);
            } catch (Exception e) {
                LOGGER.error("[Dreams] Error en el dialogo {}: {}", entry.getKey(), e.getMessage());
            }
        }
        dialogues = loaded;
        LOGGER.info("[Dreams] {} dialogo(s) cargados.", loaded.size());
    }

    private static Dialogue parse(ResourceLocation id, JsonObject root) {
        String start = GsonHelper.getAsString(root, "start");
        JsonObject nodesJson = GsonHelper.getAsJsonObject(root, "nodes");

        Map<String, Dialogue.Node> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : nodesJson.entrySet()) {
            String nodeId = entry.getKey();
            JsonObject n = GsonHelper.convertToJsonObject(entry.getValue(), "nodo " + nodeId);

            ResourceLocation speaker = new ResourceLocation(GsonHelper.getAsString(n, "speaker"));
            String expression = GsonHelper.getAsString(n, "expression", "neutral");
            String text = GsonHelper.getAsString(n, "text");
            String next = n.has("next") && !n.get("next").isJsonNull() ? GsonHelper.getAsString(n, "next") : null;

            List<Dialogue.Choice> choices = new ArrayList<>();
            if (n.has("choices")) {
                JsonArray arr = GsonHelper.getAsJsonArray(n, "choices");
                for (JsonElement el : arr) {
                    JsonObject c = GsonHelper.convertToJsonObject(el, "opcion");
                    choices.add(new Dialogue.Choice(
                            GsonHelper.getAsString(c, "text"),
                            GsonHelper.getAsString(c, "next")));
                }
            }
            nodes.put(nodeId, new Dialogue.Node(nodeId, speaker, expression, text, next, List.copyOf(choices)));
        }

        if (!nodes.containsKey(start)) {
            throw new IllegalArgumentException("el nodo inicial '" + start + "' no existe");
        }
        for (Dialogue.Node node : nodes.values()) {
            if (node.next() != null && !nodes.containsKey(node.next())) {
                throw new IllegalArgumentException("el nodo '" + node.id() + "' apunta a '" + node.next() + "', que no existe");
            }
            for (Dialogue.Choice choice : node.choices()) {
                if (!nodes.containsKey(choice.next())) {
                    throw new IllegalArgumentException("una opcion del nodo '" + node.id() + "' apunta a '" + choice.next() + "', que no existe");
                }
            }
        }
        return new Dialogue(id, start, Map.copyOf(nodes));
    }
}
