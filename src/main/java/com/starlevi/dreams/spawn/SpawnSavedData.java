package com.starlevi.dreams.spawn;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/** Guarda la configuracion de spawn dentro del mundo (data/dreams_spawn.dat). */
public class SpawnSavedData extends SavedData {
    public static final String NAME = "dreams_spawn";

    volatile SpawnConfig config;

    public SpawnSavedData() {
        this.config = SpawnConfig.initial();
    }

    public static SpawnSavedData load(CompoundTag tag) {
        SpawnSavedData data = new SpawnSavedData();
        data.config = SpawnConfig.fromTag(tag);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag saved = config.toTag();
        for (String key : saved.getAllKeys()) {
            tag.put(key, saved.get(key));
        }
        return tag;
    }
}
