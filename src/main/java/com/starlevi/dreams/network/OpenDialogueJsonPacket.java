package com.starlevi.dreams.network;

import com.starlevi.dreams.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor -> cliente: abre un dialogo que esta escrito en la carpeta de config del servidor
 * (asi la historia se escribe en un solo lugar y nadie tiene que bajar nada).
 */
public record OpenDialogueJsonPacket(ResourceLocation id, String json) {
    private static final int MAX = 1 << 18;

    public static void encode(OpenDialogueJsonPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.id);
        buf.writeUtf(packet.json, MAX);
    }

    public static OpenDialogueJsonPacket decode(FriendlyByteBuf buf) {
        return new OpenDialogueJsonPacket(buf.readResourceLocation(), buf.readUtf(MAX));
    }

    public static void handle(OpenDialogueJsonPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientHooks.openDialogueJson(packet.id(), packet.json())));
        context.setPacketHandled(true);
    }
}
