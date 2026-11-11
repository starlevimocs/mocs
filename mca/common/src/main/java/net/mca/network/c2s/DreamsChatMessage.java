package net.mca.network.c2s;

import net.mca.Config;
import net.mca.cobalt.network.Message;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.chatAI.ChatAI;
import net.mca.resources.Dialogues;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.Serial;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Dreams: lo que el jugador le escribe al aldeano desde la conversacion (texto libre).
 * Con la IA de MCA activada, el aldeano contesta con ella; si no, sigue la charla con los dialogos de MCA.
 */
public class DreamsChatMessage implements Message {
    @Serial
    private static final long serialVersionUID = 4419027715830016520L;

    private final UUID villagerUUID;
    private final String text;

    public DreamsChatMessage(UUID villagerUUID, String text) {
        this.villagerUUID = villagerUUID;
        this.text = text.length() > 256 ? text.substring(0, 256) : text;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        Entity e = player.getServerWorld().getEntity(villagerUUID);
        if (!(e instanceof VillagerEntityMCA villager) || villager.squaredDistanceTo(player) > 16 * 16 || text.isBlank()) {
            return;
        }
        if (Config.getInstance().enableVillagerChatAI) {
            CompletableFuture.runAsync(() -> {
                Optional<String> answer = ChatAI.answer(player, villager, text);
                if (answer.isPresent()) {
                    villager.conversationManager.addMessage(player, Text.literal(answer.get()));
                } else {
                    player.getServer().execute(() -> Dialogues.getInstance().selectAnswer(villager, player, "main", "chat"));
                }
            });
        } else {
            Dialogues.getInstance().selectAnswer(villager, player, "main", "chat");
        }
    }
}
