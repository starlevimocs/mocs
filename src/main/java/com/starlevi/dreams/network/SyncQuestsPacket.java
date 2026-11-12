package com.starlevi.dreams.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: las misiones del jugador y como va cada una (json). */
public record SyncQuestsPacket(String json) {
    private static final int MAX = 1 << 17;

    public static void encode(SyncQuestsPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.json, MAX);
    }

    public static SyncQuestsPacket decode(FriendlyByteBuf buf) {
        return new SyncQuestsPacket(buf.readUtf(MAX));
    }

    public static void handle(SyncQuestsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.starlevi.dreams.client.ClientQuests.apply(packet.json())));
        context.setPacketHandled(true);
    }
}
