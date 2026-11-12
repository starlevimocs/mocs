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

    private static Dialogue.Cond cond(JsonObject o) {
        if (!o.has("if")) {
            return null;
        }
        JsonObject c = GsonHelper.getAsJsonObject(o, "if");
        return new Dialogue.Cond(GsonHelper.getAsString(c, "quest"), GsonHelper.getAsString(c, "state"), GsonHelper.getAsBoolean(c, "not", false));
    }

    private static Dialogue.Action action(JsonObject o) {
        if (!o.has("quest")) {
            return null;
        }
        JsonObject q = GsonHelper.getAsJsonObject(o, "quest");
        for (String type : List.of("accept", "turn_in", "contribute")) {
            if (q.has(type)) {
                return new Dialogue.Action(type, GsonHelper.getAsString(q, type));
            }
        }
        return null;
    }

    /** Lee un dialogo de su json (tambien lo usa el servidor cuando manda uno de su carpeta de config). */
    public static Dialogue parse(ResourceLocation id, JsonObject root) {
        String start = GsonHelper.getAsString(root, "start");
        JsonObject nodesJson = GsonHelper.getAsJsonObject(root, "nodes");

        Map<String, Dialogue.Node> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : nodesJson.entrySet()) {
            String nodeId = entry.getKey();
            JsonObject n = GsonHelper.convertToJsonObject(entry.getValue(), "nodo " + nodeId);

            ResourceLocation speaker = new ResourceLocation(GsonHelper.getAsString(n, "speaker", "dreams:narrator"));
            String expression = GsonHelper.getAsString(n, "expression", "neutral");
            String text = GsonHelper.getAsString(n, "text", "");
            String next = n.has("next") && !n.get("next").isJsonNull() ? GsonHelper.getAsString(n, "next") : null;
            Integer color = n.has("color") ? SpeakerManager.parseColor(GsonHelper.getAsString(n, "color")) : null;

            List<Dialogue.Choice> choices = new ArrayList<>();
            if (n.has("choices")) {
                JsonArray arr = GsonHelper.getAsJsonArray(n, "choices");
                for (JsonElement el : arr) {
                    JsonObject c = GsonHelper.convertToJsonObject(el, "opcion");
                    choices.add(new Dialogue.Choice(
                            GsonHelper.getAsString(c, "text"),
                            c.has("next") && !c.get("next").isJsonNull() ? GsonHelper.getAsString(c, "next") : null,
                            cond(c), action(c)));
                }
            }
            List<Dialogue.Branch> branches = new ArrayList<>();
            if (n.has("branch")) {
                for (JsonElement el : GsonHelper.getAsJsonArray(n, "branch")) {
                    JsonObject b = GsonHelper.convertToJsonObject(el, "desvio");
                    Dialogue.Cond cond = cond(b);
                    if (cond != null) {
                        branches.add(new Dialogue.Branch(cond, GsonHelper.getAsString(b, "next")));
                    }
                }
            }
            nodes.put(nodeId, new Dialogue.Node(nodeId, speaker, expression, text, next, List.copyOf(choices), color,
                    List.copyOf(branches), action(n)));
        }

        if (!nodes.containsKey(start)) {
            throw new IllegalArgumentException("el nodo inicial '" + start + "' no existe");
        }
        for (Dialogue.Node node : nodes.values()) {
            if (node.next() != null && !nodes.containsKey(node.next())) {
                throw new IllegalArgumentException("el nodo '" + node.id() + "' apunta a '" + node.next() + "', que no existe");
            }
            for (Dialogue.Choice choice : node.choices()) {
                if (choice.next() != null && !nodes.containsKey(choice.next())) {
                    throw new IllegalArgumentException("una opcion del nodo '" + node.id() + "' apunta a '" + choice.next() + "', que no existe");
                }
            }
            for (Dialogue.Branch branch : node.branches()) {
                if (!nodes.containsKey(branch.next())) {
                    throw new IllegalArgumentException("un desvio del nodo '" + node.id() + "' apunta a '" + branch.next() + "', que no existe");
                }
            }
        }
        String cinematic = root.has("cinematic") && !root.get("cinematic").isJsonNull()
                ? GsonHelper.getAsString(root, "cinematic") : null;
        return new Dialogue(id, start, Map.copyOf(nodes), cinematic);
    }
}
