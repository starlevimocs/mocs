package com.starlevi.dreams.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente -> servidor: una accion pedida desde la pantalla de control.
 * <ul>
 *   <li>SET_RULE: a = id ("mod:mob" o "mod:*"), n = ordinal de SpawnMode o -1 para "automatico".</li>
 *   <li>CREATE_BOX: a = nombre, n = 1 si ocupa toda la altura.</li>
 *   <li>CREATE_BIOME: a = nombre (usa el bioma donde esta parado el jugador).</li>
 *   <li>DELETE_ZONE: a = nombre.</li>
 *   <li>TOGGLE_ALLOW: a = zona, b = id ("mod:mob" o "mod:*"), n = 1 permitir / 0 quitar.</li>
 *   <li>CLEAR_SELECTION y REFRESH: sin datos.</li>
 * </ul>
 */
public record SpawnActionPacket(Action action, String a, String b, int n) {

    public enum Action {
        SET_RULE,
        CREATE_BOX,
        CREATE_BIOME,
        DELETE_ZONE,
        TOGGLE_ALLOW,
        CLEAR_SELECTION,
        REFRESH
    }

    public static void encode(SpawnActionPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action);
        buf.writeUtf(packet.a, 256);
        buf.writeUtf(packet.b, 256);
        buf.writeVarInt(packet.n + 1); // n puede ser -1
    }

    public static SpawnActionPacket decode(FriendlyByteBuf buf) {
        return new SpawnActionPacket(buf.readEnum(Action.class), buf.readUtf(256), buf.readUtf(256), buf.readVarInt() - 1);
    }

    public static void handle(SpawnActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> SpawnNet.handleAction(player, packet));
        }
        context.setPacketHandled(true);
    }
}
