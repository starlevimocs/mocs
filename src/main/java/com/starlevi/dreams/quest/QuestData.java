package com.starlevi.dreams.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/**
 * El avance de las misiones, guardado con el mundo.
 * players: {uuid: {mision: {active, done, progress:[...]}}}; community: {mision: {active, done, progress:[...]}}.
 */
public final class QuestData extends SavedData {
    private JsonObject players = new JsonObject();
    private JsonObject community = new JsonObject();

    public static QuestData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(QuestData::load, QuestData::new, "dreams_quests");
    }

    private static QuestData load(CompoundTag tag) {
        QuestData d = new QuestData();
        try {
            d.players = JsonParser.parseString(tag.getString("players")).getAsJsonObject();
            d.community = JsonParser.parseString(tag.getString("community")).getAsJsonObject();
        } catch (RuntimeException e) {
            d.players = new JsonObject();
            d.community = new JsonObject();
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString("players", players.toString());
        tag.putString("community", community.toString());
        return tag;
    }

    private static JsonObject child(JsonObject parent, String key, boolean create) {
        if (parent.has(key) && parent.get(key).isJsonObject()) {
            return parent.getAsJsonObject(key);
        }
        if (!create) {
            return null;
        }
        JsonObject o = new JsonObject();
        parent.add(key, o);
        return o;
    }

    /** Estado de una mision personal de ese jugador (null si nunca la toco y create es false). */
    public JsonObject personal(UUID player, String quest, boolean create) {
        JsonObject p = child(players, player.toString(), create);
        return p == null ? null : child(p, quest, create);
    }

    /** Estado de una mision de comunidad. */
    public JsonObject shared(String quest, boolean create) {
        return child(community, quest, create);
    }

    public void resetPersonal(UUID player, String quest) {
        JsonObject p = child(players, player.toString(), false);
        if (p != null) {
            p.remove(quest);
            setDirty();
        }
    }

    public void resetShared(String quest) {
        community.remove(quest);
        setDirty();
    }

    static boolean flag(JsonObject state, String key) {
        return state != null && state.has(key) && state.get(key).getAsBoolean();
    }

    static int progress(JsonObject state, int index) {
        if (state == null || !state.has("progress")) {
            return 0;
        }
        JsonArray a = state.getAsJsonArray("progress");
        return index < a.size() ? a.get(index).getAsInt() : 0;
    }

    static void setProgress(JsonObject state, int index, int value) {
        JsonArray a = state.has("progress") ? state.getAsJsonArray("progress") : new JsonArray();
        while (a.size() <= index) {
            a.add(0);
        }
        a.set(index, new com.google.gson.JsonPrimitive(value));
        state.add("progress", a);
    }
}
