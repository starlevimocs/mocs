package com.starlevi.dreams.cinematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Guarda las cinematicas dentro del mundo (data/dreams_cinematics.dat). Solo se usa en el hilo del servidor. */
public class CinematicSavedData extends SavedData {
    public static final String NAME = "dreams_cinematics";

    private final Map<String, Cinematic> cinematics = new LinkedHashMap<>();

    public static CinematicSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CinematicSavedData::load, CinematicSavedData::new, NAME);
    }

    public Cinematic find(String name) {
        return cinematics.get(name);
    }

    public void put(Cinematic cinematic) {
        cinematics.put(cinematic.name(), cinematic);
        setDirty();
    }

    public boolean remove(String name) {
        boolean removed = cinematics.remove(name) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public List<String> names() {
        return new ArrayList<>(cinematics.keySet());
    }

    public static CinematicSavedData load(CompoundTag tag) {
        CinematicSavedData data = new CinematicSavedData();
        ListTag list = tag.getList("cinematics", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                Cinematic cinematic = Cinematic.fromTag(list.getCompound(i));
                data.cinematics.put(cinematic.name(), cinematic);
            } catch (RuntimeException ignored) {
                // cinematica danada: se ignora
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Cinematic cinematic : cinematics.values()) {
            list.add(cinematic.toTag());
        }
        tag.put("cinematics", list);
        return tag;
    }
}
