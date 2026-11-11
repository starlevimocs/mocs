package com.starlevi.starinteractions.net;

import com.starlevi.starinteractions.StarInteractions;
import com.starlevi.starinteractions.server.Sessions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Mensajes entre el cliente y el servidor. */
public final class Net {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StarInteractions.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private Net() {
    }

    public static void register() {
        int id = 0;
        Optional<NetworkDirection> up = Optional.of(NetworkDirection.PLAY_TO_SERVER);
        Optional<NetworkDirection> down = Optional.of(NetworkDirection.PLAY_TO_CLIENT);
        CHANNEL.registerMessage(id++, Request.class, Request::encode, Request::decode, Request::handle, up);
        CHANNEL.registerMessage(id++, Answer.class, Answer::encode, Answer::decode, Answer::handle, up);
        CHANNEL.registerMessage(id++, Emote.class, Emote::encode, Emote::decode, Emote::handle, up);
        CHANNEL.registerMessage(id++, Release.class, Release::encode, Release::decode, Release::handle, up);
        CHANNEL.registerMessage(id++, TouchInput.class, TouchInput::encode, TouchInput::decode, TouchInput::handle, up);
        CHANNEL.registerMessage(id++, Prompt.class, Prompt::encode, Prompt::decode, Prompt::handle, down);
        CHANNEL.registerMessage(id++, PairStart.class, PairStart::encode, PairStart::decode, PairStart::handle, down);
        CHANNEL.registerMessage(id++, PairStop.class, PairStop::encode, PairStop::decode, PairStop::handle, down);
        CHANNEL.registerMessage(id++, EmoteState.class, EmoteState::encode, EmoteState::decode, EmoteState::handle, down);
        CHANNEL.registerMessage(id++, Link.class, Link::encode, Link::decode, Link::handle, down);
        CHANNEL.registerMessage(id++, TouchState.class, TouchState::encode, TouchState::decode, TouchState::handle, down);
        CHANNEL.registerMessage(id++, TouchSession.class, TouchSession::encode, TouchSession::decode, TouchSession::handle, down);
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    private static void client(Supplier<NetworkEvent.Context> ctx, Runnable run) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> run));
        ctx.get().setPacketHandled(true);
    }

    private static <T> void server(Supplier<NetworkEvent.Context> ctx, T msg, BiConsumer<ServerPlayer, T> run) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player != null) {
                run.accept(player, msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // ================================================================ lo que se toca en el modo toque

    /**
     * La pose que resulta de tocar a alguien: cuanto se inclina la cabeza, a donde va cada brazo (si lo tienen
     * agarrado) y donde esta la mano de quien toca. La calcula el cliente que toca; el servidor la reparte.
     */
    public static final class TouchPose {
        public float headX, headY, headZ;
        /** bit 0 = brazo derecho agarrado, bit 1 = brazo izquierdo. */
        public int arms;
        public float rx, ry, rz, lx, ly, lz;
        /** donde esta la mano de quien toca (mundo). */
        public double hx, hy, hz;
        public boolean touching;
        public float wiggle;

        public void write(FriendlyByteBuf buf) {
            buf.writeFloat(headX);
            buf.writeFloat(headY);
            buf.writeFloat(headZ);
            buf.writeByte(arms);
            buf.writeFloat(rx);
            buf.writeFloat(ry);
            buf.writeFloat(rz);
            buf.writeFloat(lx);
            buf.writeFloat(ly);
            buf.writeFloat(lz);
            buf.writeDouble(hx);
            buf.writeDouble(hy);
            buf.writeDouble(hz);
            buf.writeBoolean(touching);
            buf.writeFloat(wiggle);
        }

        public static TouchPose read(FriendlyByteBuf buf) {
            TouchPose p = new TouchPose();
            p.headX = clamp(buf.readFloat(), 1);
            p.headY = clamp(buf.readFloat(), 1);
            p.headZ = clamp(buf.readFloat(), 1);
            p.arms = buf.readByte() & 3;
            p.rx = clamp(buf.readFloat(), 40);
            p.ry = clamp(buf.readFloat(), 40);
            p.rz = clamp(buf.readFloat(), 40);
            p.lx = clamp(buf.readFloat(), 40);
            p.ly = clamp(buf.readFloat(), 40);
            p.lz = clamp(buf.readFloat(), 40);
            p.hx = buf.readDouble();
            p.hy = buf.readDouble();
            p.hz = buf.readDouble();
            p.touching = buf.readBoolean();
            p.wiggle = clamp(buf.readFloat(), 1);
            return p;
        }

        private static float clamp(float v, float max) {
            return Float.isFinite(v) ? Math.max(-max, Math.min(max, v)) : 0;
        }

        public TouchPose copy() {
            TouchPose p = new TouchPose();
            p.headX = headX;
            p.headY = headY;
            p.headZ = headZ;
            p.arms = arms;
            p.rx = rx;
            p.ry = ry;
            p.rz = rz;
            p.lx = lx;
            p.ly = ly;
            p.lz = lz;
            p.hx = hx;
            p.hy = hy;
            p.hz = hz;
            p.touching = touching;
            p.wiggle = wiggle;
            return p;
        }
    }

    // ================================================================ cliente -> servidor

    /** Pedirle una interaccion a quien tenes enfrente (o empezar el modo toque). */
    public record Request(int targetId, String interaction) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(targetId);
            buf.writeUtf(interaction, 64);
        }

        static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readVarInt(), buf.readUtf(64));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            server(ctx, this, Sessions::onRequest);
        }
    }

    public record Answer(boolean accept) {
        void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(accept);
        }

        static Answer decode(FriendlyByteBuf buf) {
            return new Answer(buf.readBoolean());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            server(ctx, this, Sessions::onAnswer);
        }
    }

    /** Emote de una persona ("" = cortar el que esta haciendo). */
    public record Emote(String id) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id, 64);
        }

        static Emote decode(FriendlyByteBuf buf) {
            return new Emote(buf.readUtf(64));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            server(ctx, this, Sessions::onEmote);
        }
    }

    /** Soltar: la mano, a quien cargas, o terminar el modo toque. */
    public record Release() {
        void encode(FriendlyByteBuf buf) {
        }

        static Release decode(FriendlyByteBuf buf) {
            return new Release();
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            server(ctx, this, (p, m) -> Sessions.onRelease(p));
        }
    }

    /** Modo toque: la pose actual y, si paso algo (una caricia, un toque en la nariz...), que fue. */
    public record TouchInput(TouchPose pose, int event) {
        void encode(FriendlyByteBuf buf) {
            pose.write(buf);
            buf.writeByte(event);
        }

        static TouchInput decode(FriendlyByteBuf buf) {
            return new TouchInput(TouchPose.read(buf), buf.readByte());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            server(ctx, this, Sessions::onTouchInput);
        }
    }

    // ================================================================ servidor -> cliente

    /** Alguien te pide una interaccion (se contesta con Y / N). */
    public record Prompt(int requesterId, String name, String interaction, int seconds) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(requesterId);
            buf.writeUtf(name);
            buf.writeUtf(interaction);
            buf.writeVarInt(seconds);
        }

        static Prompt decode(FriendlyByteBuf buf) {
            return new Prompt(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starinteractions.client.ClientNet.prompt(this));
        }
    }

    /** Empieza una interaccion de a dos; el ancla es donde esta parado quien la pidio. */
    public record PairStart(String interaction, int aId, int bId, double x, double y, double z, float yaw) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(interaction);
            buf.writeVarInt(aId);
            buf.writeVarInt(bId);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeFloat(yaw);
        }

        static PairStart decode(FriendlyByteBuf buf) {
            return new PairStart(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starinteractions.client.ClientNet.pairStart(this));
        }
    }

    public record PairStop(int aId, int bId) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(aId);
            buf.writeVarInt(bId);
        }

        static PairStop decode(FriendlyByteBuf buf) {
            return new PairStop(buf.readVarInt(), buf.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starinteractions.client.ClientNet.pairStop(this));
        }
    }

    public record EmoteState(int entityId, String id) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeUtf(id);
        }

        static EmoteState decode(FriendlyByteBuf buf) {
            return new EmoteState(buf.readVarInt(), buf.readUtf());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starinteractions.client.ClientNet.emote(this));
        }
    }

    /** Dos jugadores enganchados: de la mano o uno cargando al otro (mode 0 = se soltaron). */
    public record Link(int aId, int bId, int mode) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(aId);
            buf.writeVarInt(bId);
            buf.writeByte(mode);
        }

        static Link decode(FriendlyByteBuf buf) {
            return new Link(buf.readVarInt(), buf.readVarInt(), buf.readByte());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starinteractions.client.ClientNet.link(this));
        }
    }

    /** La pose del modo toque, para todos los que miran. fx: 0 nada, 1 le gusta, 2 no le gusta, 3 corazon. */
    public record TouchState(int toucherId, int targetId, boolean active, TouchPose pose, int fx) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(toucherId);
            buf.writeVarInt(targetId);
            buf.writeBoolean(active);
            pose.write(buf);
            buf.writeByte(fx);
        }

        static TouchState decode(FriendlyByteBuf buf) {
            return new TouchState(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), TouchPose.read(buf), buf.readByte());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starinteractions.client.ClientNet.touchState(this));
        }
    }

    /** Para quien toca: abre (o cierra) el modo toque. */
    public record TouchSession(int targetId, boolean open, String message) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(targetId);
            buf.writeBoolean(open);
            buf.writeUtf(message);
        }

        static TouchSession decode(FriendlyByteBuf buf) {
            return new TouchSession(buf.readVarInt(), buf.readBoolean(), buf.readUtf());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starinteractions.client.ClientNet.touchSession(this));
        }
    }
}
