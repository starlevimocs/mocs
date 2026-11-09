package com.starlevi.dreams.network;

import com.starlevi.dreams.client.ClientSpawnHooks;
import com.starlevi.dreams.spawn.SpawnConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor -> cliente: manda la configuracion de spawn completa, la seleccion A-B del jugador
 * y el bioma donde esta parado. Si {@code open} es true, ademas abre la pantalla.
 */
public record SyncSpawnPacket(SpawnConfig config, boolean open, ResourceLocation selectionDimension,
                              BlockPos a, BlockPos b, String currentBiome) {

    public static void encode(SyncSpawnPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.config.toTag());
        buf.writeBoolean(packet.open);
        buf.writeBoolean(packet.selectionDimension != null);
        if (packet.selectionDimension != null) {
            buf.writeResourceLocation(packet.selectionDimension);
        }
        buf.writeBoolean(packet.a != null);
        if (packet.a != null) {
            buf.writeBlockPos(packet.a);
        }
        buf.writeBoolean(packet.b != null);
        if (packet.b != null) {
            buf.writeBlockPos(packet.b);
        }
        buf.writeUtf(packet.currentBiome);
    }

    public static SyncSpawnPacket decode(FriendlyByteBuf buf) {
        SpawnConfig config = SpawnConfig.fromTag(buf.readNbt());
        boolean open = buf.readBoolean();
        ResourceLocation dim = buf.readBoolean() ? buf.readResourceLocation() : null;
        BlockPos a = buf.readBoolean() ? buf.readBlockPos() : null;
        BlockPos b = buf.readBoolean() ? buf.readBlockPos() : null;
        String biome = buf.readUtf();
        return new SyncSpawnPacket(config, open, dim, a, b, biome);
    }

    public static void handle(SyncSpawnPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientSpawnHooks.handleSync(packet)));
        context.setPacketHandled(true);
    }
}
