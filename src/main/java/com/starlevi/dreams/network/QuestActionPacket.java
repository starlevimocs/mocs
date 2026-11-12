package com.starlevi.dreams.network;

import com.starlevi.dreams.quest.Quests;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente -> servidor: desde un dialogo, el jugador acepta, entrega o aporta a una mision. El servidor comprueba
 * que de verdad se pueda (que este disponible, que tenga lo que hay que entregar).
 */
public record QuestActionPacket(String action, String quest) {

    public static void encode(QuestActionPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.action, 16);
        buf.writeUtf(packet.quest, 64);
    }

    public static QuestActionPacket decode(FriendlyByteBuf buf) {
        return new QuestActionPacket(buf.readUtf(16), buf.readUtf(64));
    }

    public static void handle(QuestActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null) {
                return;
            }
            switch (packet.action) {
                case "accept" -> Quests.accept(player, packet.quest);
                case "turn_in" -> Quests.turnIn(player, packet.quest);
                case "contribute" -> Quests.contribute(player, packet.quest);
                default -> {
                }
            }
            Quests.sync(player);
        });
        context.setPacketHandled(true);
    }
}
