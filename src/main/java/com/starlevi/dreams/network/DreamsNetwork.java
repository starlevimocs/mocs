package com.starlevi.dreams.network;

import com.starlevi.dreams.DreamsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class DreamsNetwork {
    private static final String PROTOCOL = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DreamsMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private DreamsNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenDialoguePacket.class,
                OpenDialoguePacket::encode, OpenDialoguePacket::decode, OpenDialoguePacket::handle);
        CHANNEL.registerMessage(id++, SyncSpawnPacket.class,
                SyncSpawnPacket::encode, SyncSpawnPacket::decode, SyncSpawnPacket::handle);
        CHANNEL.registerMessage(id++, SpawnActionPacket.class,
                SpawnActionPacket::encode, SpawnActionPacket::decode, SpawnActionPacket::handle);
        CHANNEL.registerMessage(id++, PlayCinematicPacket.class,
                PlayCinematicPacket::encode, PlayCinematicPacket::decode, PlayCinematicPacket::handle);
        CHANNEL.registerMessage(id++, StopCinematicPacket.class,
                StopCinematicPacket::encode, StopCinematicPacket::decode, StopCinematicPacket::handle);
        CHANNEL.registerMessage(id++, CinematicRequestPacket.class,
                CinematicRequestPacket::encode, CinematicRequestPacket::decode, CinematicRequestPacket::handle);
    }

    public static void openDialogue(ServerPlayer player, ResourceLocation dialogueId) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenDialoguePacket(dialogueId));
    }
}
