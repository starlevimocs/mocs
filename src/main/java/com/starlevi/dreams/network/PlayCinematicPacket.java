package com.starlevi.dreams.network;

import com.starlevi.dreams.cinematic.Cinematic;
import com.starlevi.dreams.client.CinematicClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: empieza a reproducir una cinematica. Con loop, una orbita da vueltas hasta que se corte. */
public record PlayCinematicPacket(Cinematic cinematic, boolean loop) {

    public static void encode(PlayCinematicPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.cinematic.toTag());
        buf.writeBoolean(packet.loop);
    }

    public static PlayCinematicPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        boolean loop = buf.readBoolean();
        return new PlayCinematicPacket(Cinematic.fromTag(tag == null ? new CompoundTag() : tag), loop);
    }

    public static void handle(PlayCinematicPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> CinematicClient.play(packet.cinematic(), packet.loop())));
        context.setPacketHandled(true);
    }
}
