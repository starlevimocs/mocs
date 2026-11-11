package com.starlevi.starvfx.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starlevi.starvfx.Geo;
import com.starlevi.starvfx.net.Net;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Lo que queda puesto en el mundo: los efectos permanentes (una grieta en el cielo que sigue ahi dia tras dia)
 * y las zonas de camara fija. Se guarda con el mundo y se le manda a cada jugador cuando entra.
 */
public final class WorldData extends SavedData {
    /** efectos: [{id, dim, type, pos:[x,y,z], params:{...}}] */
    private JsonArray effects = new JsonArray();
    /** zonas: [{name, dim, box:[x1,y1,z1,x2,y2,z2], cam:[x,y,z], yaw, pitch, fov, mode}] */
    private JsonArray zones = new JsonArray();

    public static WorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WorldData::load, WorldData::new, "starvfx");
    }

    private static WorldData load(CompoundTag tag) {
        WorldData d = new WorldData();
        try {
            d.effects = JsonParser.parseString(tag.getString("effects")).getAsJsonArray();
        } catch (RuntimeException ignored) {
            d.effects = new JsonArray();
        }
        try {
            d.zones = JsonParser.parseString(tag.getString("zones")).getAsJsonArray();
        } catch (RuntimeException ignored) {
            d.zones = new JsonArray();
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString("effects", effects.toString());
        tag.putString("zones", zones.toString());
        return tag;
    }

    private static String dim(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static boolean removeNamed(JsonArray list, String key, String name, String dim) {
        boolean removed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            JsonObject o = list.get(i).getAsJsonObject();
            if (Geo.str(o, key, "").equals(name) && Geo.str(o, "dim", "").equals(dim)) {
                list.remove(i);
                removed = true;
            }
        }
        return removed;
    }

    // ---------------------------------------------------------------- efectos

    public void addEffect(ServerLevel level, String id, String type, double x, double y, double z, JsonObject params) {
        removeNamed(effects, "id", id, dim(level));
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("dim", dim(level));
        o.addProperty("type", type);
        o.add("pos", Geo.arr(x, y, z));
        o.add("params", params);
        effects.add(o);
        setDirty();
    }

    public boolean removeEffect(ServerLevel level, String id) {
        boolean removed = removeNamed(effects, "id", id, dim(level));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public int clearEffects(ServerLevel level) {
        int n = 0;
        for (int i = effects.size() - 1; i >= 0; i--) {
            if (Geo.str(effects.get(i).getAsJsonObject(), "dim", "").equals(dim(level))) {
                effects.remove(i);
                n++;
            }
        }
        setDirty();
        return n;
    }

    public JsonArray effects() {
        return effects;
    }

    // ---------------------------------------------------------------- zonas

    public void addZone(ServerLevel level, JsonObject zone) {
        removeNamed(zones, "name", Geo.str(zone, "name", ""), dim(level));
        zone.addProperty("dim", dim(level));
        zones.add(zone);
        setDirty();
    }

    public boolean removeZone(ServerLevel level, String name) {
        boolean removed = removeNamed(zones, "name", name, dim(level));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public JsonArray zones() {
        return zones;
    }

    public void syncZones(MinecraftServer server) {
        Net.Zones msg = new Net.Zones(zones.toString());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Net.toPlayer(p, msg);
        }
    }

    /** Todo lo de la dimension donde esta el jugador (al entrar o al cambiar de dimension). */
    public void sync(ServerPlayer player) {
        Net.toPlayer(player, new Net.Zones(zones.toString()));
        Net.toPlayer(player, new Net.Fx("*", true, "", 0, 0, 0, "{}", 0));
        String dim = dim(player.serverLevel());
        for (JsonElement e : effects) {
            JsonObject o = e.getAsJsonObject();
            if (!Geo.str(o, "dim", "").equals(dim)) {
                continue;
            }
            var pos = Geo.vec(o, "pos", net.minecraft.world.phys.Vec3.ZERO);
            JsonObject params = o.has("params") && o.get("params").isJsonObject() ? o.getAsJsonObject("params") : new JsonObject();
            // ya estaba ahi: aparece sin la animacion de entrada
            Net.toPlayer(player, new Net.Fx(Geo.str(o, "id", "fx"), false, Geo.str(o, "type", ""), pos.x, pos.y, pos.z, params.toString(), 60));
        }
    }
}
