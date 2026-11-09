package com.starlevi.dreams.client;

import com.starlevi.dreams.network.SyncSpawnPacket;
import com.starlevi.dreams.spawn.SpawnConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Ultimo estado de spawn recibido del servidor; la pantalla de control lo lee en cada frame. */
public final class ClientSpawnState {
    public static SpawnConfig config = SpawnConfig.initial();
    public static ResourceLocation selectionDimension;
    public static BlockPos a;
    public static BlockPos b;
    public static String currentBiome = "";
    /** Sube con cada actualizacion, para que la pantalla sepa cuando rehacer sus listas. */
    public static int version;

    private ClientSpawnState() {
    }

    public static void apply(SyncSpawnPacket packet) {
        config = packet.config();
        selectionDimension = packet.selectionDimension();
        a = packet.a();
        b = packet.b();
        currentBiome = packet.currentBiome();
        version++;
    }
}
