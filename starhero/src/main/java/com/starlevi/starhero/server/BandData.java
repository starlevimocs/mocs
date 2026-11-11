package com.starlevi.starhero.server;

import com.starlevi.starhero.content.Instrument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Que aldeanos estan en la banda y con que instrumento (se guarda con el mundo). */
public final class BandData extends SavedData {
    private final Map<UUID, Instrument> members = new HashMap<>();

    public static BandData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BandData::load, BandData::new, "starhero_band");
    }

    private static BandData load(CompoundTag tag) {
        BandData data = new BandData();
        CompoundTag m = tag.getCompound("members");
        for (String key : m.getAllKeys()) {
            try {
                data.members.put(UUID.fromString(key), Instrument.byId(m.getString(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag m = new CompoundTag();
        members.forEach((k, v) -> m.putString(k.toString(), v.id()));
        tag.put("members", m);
        return tag;
    }

    public Instrument get(UUID id) {
        return members.get(id);
    }

    public void set(UUID id, Instrument instrument) {
        if (instrument == null) {
            members.remove(id);
        } else {
            members.put(id, instrument);
        }
        setDirty();
    }
}
