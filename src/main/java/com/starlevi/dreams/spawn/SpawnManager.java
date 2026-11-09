package com.starlevi.dreams.spawn;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.function.UnaryOperator;

/**
 * Acceso a la configuracion de spawn del servidor. Se carga de forma perezosa la primera vez
 * que hace falta (incluso durante la generacion del mundo) y se guarda con el mundo.
 */
public final class SpawnManager {
    private static final Object LOCK = new Object();
    private static final SpawnConfig FALLBACK = SpawnConfig.initial();

    private static volatile SpawnSavedData data;

    private SpawnManager() {
    }

    public static SpawnConfig config(MinecraftServer server) {
        SpawnSavedData current = data;
        if (current == null) {
            ServerLevel overworld = server == null ? null : server.overworld();
            if (overworld == null) {
                return FALLBACK;
            }
            synchronized (LOCK) {
                if (data == null) {
                    data = overworld.getDataStorage().computeIfAbsent(
                            SpawnSavedData::load, SpawnSavedData::new, SpawnSavedData.NAME);
                }
                current = data;
            }
        }
        return current.config;
    }

    public static void update(MinecraftServer server, UnaryOperator<SpawnConfig> change) {
        config(server); // fuerza la carga
        SpawnSavedData current = data;
        if (current == null) {
            return;
        }
        synchronized (LOCK) {
            current.config = change.apply(current.config);
            current.setDirty();
        }
    }

    /** Al cerrar el servidor/mundo, para no arrastrar datos al siguiente mundo. */
    public static void reset() {
        synchronized (LOCK) {
            data = null;
        }
    }
}
