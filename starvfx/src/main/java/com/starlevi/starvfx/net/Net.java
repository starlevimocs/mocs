package com.starlevi.starvfx.net;

import com.starlevi.starvfx.StarVfx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/** Mensajes del servidor a los clientes (el cliente no manda nada: todo se maneja con comandos). */
public final class Net {
    private static final String VERSION = "2";
    private static final int MAX_JSON = 1 << 18;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StarVfx.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private Net() {
    }

    public static void register() {
        int id = 0;
        Optional<NetworkDirection> down = Optional.of(NetworkDirection.PLAY_TO_CLIENT);
        CHANNEL.registerMessage(id++, PlayScene.class, PlayScene::encode, PlayScene::decode, PlayScene::handle, down);
        CHANNEL.registerMessage(id++, StopScene.class, StopScene::encode, StopScene::decode, StopScene::handle, down);
        CHANNEL.registerMessage(id++, Fx.class, Fx::encode, Fx::decode, Fx::handle, down);
        CHANNEL.registerMessage(id++, PlayerAnim.class, PlayerAnim::encode, PlayerAnim::decode, PlayerAnim::handle, down);
        CHANNEL.registerMessage(id++, Zones.class, Zones::encode, Zones::decode, Zones::handle, down);
        CHANNEL.registerMessage(id++, Trees.class, Trees::encode, Trees::decode, Trees::handle, down);
        CHANNEL.registerMessage(id++, TreeSpeak.class, TreeSpeak::encode, TreeSpeak::decode, TreeSpeak::handle, down);
        CHANNEL.registerMessage(id++, TreeUse.class, TreeUse::encode, TreeUse::decode, TreeUse::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    /** A todos los que estan en esa dimension. */
    public static void toLevel(ServerLevel level, Object msg) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    private static void client(Supplier<NetworkEvent.Context> ctx, Runnable run) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> run));
        ctx.get().setPacketHandled(true);
    }

    /**
     * Empieza una escena.
     *
     * @param id      numero de la escena en curso (para poder cortarla)
     * @param elapsed segundos que ya pasaron (para el que llega tarde)
     */
    public record PlayScene(int id, String json, double x, double y, double z, float yaw, float elapsed) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeUtf(json, MAX_JSON);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeFloat(yaw);
            buf.writeFloat(elapsed);
        }

        public static PlayScene decode(FriendlyByteBuf buf) {
            return new PlayScene(buf.readVarInt(), buf.readUtf(MAX_JSON), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starvfx.client.ClientNet.playScene(this));
        }
    }

    /** Corta una escena (-1 = todas). */
    public record StopScene(int id) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
        }

        public static StopScene decode(FriendlyByteBuf buf) {
            return new StopScene(buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starvfx.client.ClientNet.stopScene(this));
        }
    }

    /**
     * Pone o quita un efecto del mundo (grieta, anomalia...) fuera de una escena.
     *
     * @param id     nombre del efecto; al quitar, "*" = todos
     * @param remove true = quitarlo
     * @param params json con duration, size, color, etc.
     */
    public record Fx(String id, boolean remove, String type, double x, double y, double z, String params, float elapsed) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id, 64);
            buf.writeBoolean(remove);
            buf.writeUtf(type, 64);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeUtf(params, 4096);
            buf.writeFloat(elapsed);
        }

        public static Fx decode(FriendlyByteBuf buf) {
            return new Fx(buf.readUtf(64), buf.readBoolean(), buf.readUtf(64), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readUtf(4096), buf.readFloat());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starvfx.client.ClientNet.fx(this));
        }
    }

    /**
     * Animacion especial de un jugador: "sprout" (brota del suelo), "afk" (sentado, dormitando), "none".
     *
     * @param delay    segundos hasta que empieza (antes de eso, en "sprout" todavia no se lo ve)
     * @param duration segundos que dura (0 = hasta nuevo aviso)
     */
    public record PlayerAnim(int entityId, String anim, float delay, float duration) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeUtf(anim, 32);
            buf.writeFloat(delay);
            buf.writeFloat(duration);
        }

        public static PlayerAnim decode(FriendlyByteBuf buf) {
            return new PlayerAnim(buf.readVarInt(), buf.readUtf(32), buf.readFloat(), buf.readFloat());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starvfx.client.ClientNet.playerAnim(this));
        }
    }

    /** Todas las zonas de camara fija (json). */
    public record Zones(String json) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(json, MAX_JSON);
        }

        public static Zones decode(FriendlyByteBuf buf) {
            return new Zones(buf.readUtf(MAX_JSON));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starvfx.client.ClientNet.zones(this));
        }
    }

    /** Todos los arboles madre (json). */
    public record Trees(String json) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(json, MAX_JSON);
        }

        public static Trees decode(FriendlyByteBuf buf) {
            return new Trees(buf.readUtf(MAX_JSON));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starvfx.client.ClientNet.trees(this));
        }
    }

    /** El arbol "habla": se tine de ese color durante esos segundos (lo ven todos). */
    public record TreeSpeak(String id, int color, float seconds) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id, 64);
            buf.writeInt(color);
            buf.writeFloat(seconds);
        }

        public static TreeSpeak decode(FriendlyByteBuf buf) {
            return new TreeSpeak(buf.readUtf(64), buf.readInt(), buf.readFloat());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starvfx.client.ClientNet.treeSpeak(this));
        }
    }

    /** Cliente -> servidor: el jugador le hablo al arbol (clic derecho mirandolo de cerca). */
    public record TreeUse(String id) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id, 64);
        }

        public static TreeUse decode(FriendlyByteBuf buf) {
            return new TreeUse(buf.readUtf(64));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null) {
                    com.starlevi.starvfx.server.Trees.onUse(player, id);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
