package com.starlevi.dreams.network;

import com.starlevi.dreams.client.CinematicClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: corta la cinematica que se este reproduciendo. */
public record StopCinematicPacket() {

    public static void encode(StopCinematicPacket packet, FriendlyByteBuf buf) {
    }

    public static StopCinematicPacket decode(FriendlyByteBuf buf) {
        return new StopCinematicPacket();
    }

    public static void handle(StopCinematicPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> CinematicClient.stop(false)));
        context.setPacketHandled(true);
    }
}
