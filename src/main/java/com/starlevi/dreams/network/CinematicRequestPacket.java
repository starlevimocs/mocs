package com.starlevi.dreams.network;

import com.starlevi.dreams.cinematic.CinematicServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente -> servidor.
 * <ul>
 *   <li>start = true: pide la cinematica "name" (la usa un dialogo que tiene una cinematica asociada).</li>
 *   <li>start = false: avisa que su cinematica termino o la salto.</li>
 * </ul>
 */
public record CinematicRequestPacket(String name, boolean start) {

    public static void encode(CinematicRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.name, 64);
        buf.writeBoolean(packet.start);
    }

    public static CinematicRequestPacket decode(FriendlyByteBuf buf) {
        return new CinematicRequestPacket(buf.readUtf(64), buf.readBoolean());
    }

    public static void handle(CinematicRequestPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> CinematicServer.handleRequest(player, packet.name(), packet.start()));
        }
        context.setPacketHandled(true);
    }
}
