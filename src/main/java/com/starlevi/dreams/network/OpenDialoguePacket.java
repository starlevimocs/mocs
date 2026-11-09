package com.starlevi.dreams.network;

import com.starlevi.dreams.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: abre el dialogo indicado. */
public record OpenDialoguePacket(ResourceLocation dialogueId) {

    public static void encode(OpenDialoguePacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.dialogueId);
    }

    public static OpenDialoguePacket decode(FriendlyByteBuf buf) {
        return new OpenDialoguePacket(buf.readResourceLocation());
    }

    public static void handle(OpenDialoguePacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientHooks.openDialogue(packet.dialogueId())));
        context.setPacketHandled(true);
    }
}
