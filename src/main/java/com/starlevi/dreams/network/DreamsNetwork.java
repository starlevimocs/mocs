package com.starlevi.dreams.network;

import com.starlevi.dreams.DreamsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class DreamsNetwork {
    private static final String PROTOCOL = "4";

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
        CHANNEL.registerMessage(id++, SyncQuestsPacket.class,
                SyncQuestsPacket::encode, SyncQuestsPacket::decode, SyncQuestsPacket::handle);
        CHANNEL.registerMessage(id++, QuestActionPacket.class,
                QuestActionPacket::encode, QuestActionPacket::decode, QuestActionPacket::handle);
        CHANNEL.registerMessage(id++, OpenDialogueJsonPacket.class,
                OpenDialogueJsonPacket::encode, OpenDialogueJsonPacket::decode, OpenDialogueJsonPacket::handle);
    }

    /**
     * Abre un dialogo. Si existe {@code config/dreams/dialogues/<nombre>.json} en el servidor, manda ese; si no,
     * el cliente usa el que trae el mod (o un resource pack).
     */
    public static void openDialogue(ServerPlayer player, ResourceLocation dialogueId) {
        com.starlevi.dreams.quest.Quests.sync(player);
        if (dialogueId.getNamespace().equals(DreamsMod.MOD_ID) && dialogueId.getPath().matches("[a-z0-9_]+")) {
            java.nio.file.Path file = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("dreams").resolve("dialogues")
                    .resolve(dialogueId.getPath() + ".json");
            if (java.nio.file.Files.isRegularFile(file)) {
                try {
                    String json = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenDialogueJsonPacket(dialogueId, json));
                    return;
                } catch (java.io.IOException e) {
                    com.mojang.logging.LogUtils.getLogger().warn("[Dreams] No se pudo leer {}", file, e);
                }
            }
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenDialoguePacket(dialogueId));
    }
}
