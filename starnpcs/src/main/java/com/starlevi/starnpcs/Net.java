package com.starlevi.starnpcs;

import com.starlevi.starnpcs.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/** Mensajes entre cliente y servidor. */
public final class Net {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StarNpcs.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private Net() {
    }

    public static void register() {
        int id = 0;
        Optional<NetworkDirection> up = Optional.of(NetworkDirection.PLAY_TO_SERVER);
        Optional<NetworkDirection> down = Optional.of(NetworkDirection.PLAY_TO_CLIENT);
        CHANNEL.registerMessage(id++, AssetPart.class, AssetPart::encode, AssetPart::decode, AssetPart::handle, down);
        CHANNEL.registerMessage(id++, Trigger.class, Trigger::encode, Trigger::decode, Trigger::handle, down);
        CHANNEL.registerMessage(id++, Bubble.class, Bubble::encode, Bubble::decode, Bubble::handle, down);
        CHANNEL.registerMessage(id++, OpenEditor.class, OpenEditor::encode, OpenEditor::decode, OpenEditor::handle, down);
        CHANNEL.registerMessage(id++, Action.class, Action::encode, Action::decode, Action::handle, up);
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void toWatchers(Entity entity, Object msg) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), msg);
    }

    public static void openEditor(ServerPlayer player, NpcEntity npc, int tab) {
        toPlayer(player, new OpenEditor(npc.getId(), npc.profile().toJson(), tab, String.join("\n", Presets.ids())));
    }

    private static void client(Supplier<NetworkEvent.Context> ctx, Runnable run) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> run));
        ctx.get().setPacketHandled(true);
    }

    /** Un trozo de un archivo de config/starnpcs (comprimido). path vacio = empieza una tanda; "#done" = termino. */
    public record AssetPart(String path, int part, int parts, byte[] data) {
        static void encode(AssetPart m, FriendlyByteBuf buf) {
            buf.writeUtf(m.path);
            buf.writeVarInt(m.part);
            buf.writeVarInt(m.parts);
            buf.writeByteArray(m.data);
        }

        static AssetPart decode(FriendlyByteBuf buf) {
            return new AssetPart(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(40000));
        }

        static void handle(AssetPart m, Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> ClientHooks.assetPart(m.path, m.part, m.parts, m.data));
        }
    }

    /** Animacion de una vez que arranca en ese tick del mundo. */
    public record Trigger(int entity, String anim, long tick) {
        static void encode(Trigger m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeUtf(m.anim);
            buf.writeLong(m.tick);
        }

        static Trigger decode(FriendlyByteBuf buf) {
            return new Trigger(buf.readVarInt(), buf.readUtf(), buf.readLong());
        }

        static void handle(Trigger m, Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> ClientHooks.trigger(m.entity, m.anim, m.tick));
        }
    }

    public record Bubble(int entity, String text) {
        static void encode(Bubble m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeUtf(m.text);
        }

        static Bubble decode(FriendlyByteBuf buf) {
            return new Bubble(buf.readVarInt(), buf.readUtf());
        }

        static void handle(Bubble m, Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> ClientHooks.bubble(m.entity, m.text));
        }
    }

    public record OpenEditor(int entity, String json, int tab, String presets) {
        static void encode(OpenEditor m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeUtf(m.json);
            buf.writeVarInt(m.tab);
            buf.writeUtf(m.presets);
        }

        static OpenEditor decode(FriendlyByteBuf buf) {
            return new OpenEditor(buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readUtf());
        }

        static void handle(OpenEditor m, Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> ClientHooks.openEditor(m.entity, m.json, m.tab, m.presets));
        }
    }

    /** Lo que pide el editor: guardar, borrar, sincronizar, comercio... Siempre lleva el perfil en edicion. */
    public record Action(int entity, String action, String arg, String json, int tab) {
        static void encode(Action m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeUtf(m.action);
            buf.writeUtf(m.arg);
            buf.writeUtf(m.json);
            buf.writeVarInt(m.tab);
        }

        static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt());
        }

        static void handle(Action m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.hasPermissions(2)) {
                    NpcActions.handle(player, m);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
