package com.starlevi.starcombat;

import com.starlevi.starcombat.client.ClientCombat;
import com.starlevi.starcombat.server.Combat;
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
import java.util.function.Supplier;

/** Mensajes entre cliente y servidor. */
public final class Net {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StarCombat.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private Net() {
    }

    public static void register() {
        int id = 0;
        Optional<NetworkDirection> up = Optional.of(NetworkDirection.PLAY_TO_SERVER);
        Optional<NetworkDirection> down = Optional.of(NetworkDirection.PLAY_TO_CLIENT);
        CHANNEL.registerMessage(id++, Act.class, Act::encode, Act::decode, Act::handle, up);
        CHANNEL.registerMessage(id++, GrappleReq.class, GrappleReq::encode, GrappleReq::decode, GrappleReq::handle, up);
        CHANNEL.registerMessage(id++, GrappleEnd.class, GrappleEnd::encode, GrappleEnd::decode, GrappleEnd::handle, up);
        CHANNEL.registerMessage(id++, MoveMsg.class, MoveMsg::encode, MoveMsg::decode, MoveMsg::handle, down);
        CHANNEL.registerMessage(id++, GrappleMsg.class, GrappleMsg::encode, GrappleMsg::decode, GrappleMsg::handle, down);
        CHANNEL.registerMessage(id++, HitMsg.class, HitMsg::encode, HitMsg::decode, HitMsg::handle, down);
        CHANNEL.registerMessage(id++, ConfigMsg.class, ConfigMsg::encode, ConfigMsg::decode, ConfigMsg::handle, down);
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    /** A los que ven a ese jugador, sin contarlo a el. */
    public static void toWatchers(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), msg);
    }

    /** A los que ven a ese jugador y a el mismo. */
    public static void toWatchersAndSelf(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    private static void client(Supplier<NetworkEvent.Context> ctx, Runnable run) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> run));
        ctx.get().setPacketHandled(true);
    }

    private static void server(Supplier<NetworkEvent.Context> ctx, java.util.function.Consumer<ServerPlayer> run) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) {
                run.accept(p);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /** El jugador empieza un movimiento. */
    public record Act(int move) {
        static void encode(Act m, FriendlyByteBuf b) { b.writeByte(m.move); }
        static Act decode(FriendlyByteBuf b) { return new Act(b.readByte()); }
        static void handle(Act m, Supplier<NetworkEvent.Context> ctx) { server(ctx, p -> Combat.act(p, m.move)); }
    }

    /** El jugador lanza el gancho a un punto, o a una criatura. */
    public record GrappleReq(double x, double y, double z, int target) {
        static void encode(GrappleReq m, FriendlyByteBuf b) { b.writeDouble(m.x); b.writeDouble(m.y); b.writeDouble(m.z); b.writeInt(m.target); }
        static GrappleReq decode(FriendlyByteBuf b) { return new GrappleReq(b.readDouble(), b.readDouble(), b.readDouble(), b.readInt()); }
        static void handle(GrappleReq m, Supplier<NetworkEvent.Context> ctx) { server(ctx, p -> Combat.grapple(p, m.x, m.y, m.z, m.target)); }
    }

    public record GrappleEnd() {
        static void encode(GrappleEnd m, FriendlyByteBuf b) { }
        static GrappleEnd decode(FriendlyByteBuf b) { return new GrappleEnd(); }
        static void handle(GrappleEnd m, Supplier<NetworkEvent.Context> ctx) { server(ctx, p -> Combat.grappleEnd(p, false)); }
    }

    public record MoveMsg(int entity, int move) {
        static void encode(MoveMsg m, FriendlyByteBuf b) { b.writeInt(m.entity); b.writeByte(m.move); }
        static MoveMsg decode(FriendlyByteBuf b) { return new MoveMsg(b.readInt(), b.readByte()); }
        static void handle(MoveMsg m, Supplier<NetworkEvent.Context> ctx) { client(ctx, () -> ClientCombat.onMove(m.entity, m.move)); }
    }

    public record GrappleMsg(int entity, boolean start, double x, double y, double z, int target) {
        static void encode(GrappleMsg m, FriendlyByteBuf b) {
            b.writeInt(m.entity); b.writeBoolean(m.start); b.writeDouble(m.x); b.writeDouble(m.y); b.writeDouble(m.z); b.writeInt(m.target);
        }
        static GrappleMsg decode(FriendlyByteBuf b) {
            return new GrappleMsg(b.readInt(), b.readBoolean(), b.readDouble(), b.readDouble(), b.readDouble(), b.readInt());
        }
        static void handle(GrappleMsg m, Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> ClientCombat.onGrapple(m.entity, m.start, m.x, m.y, m.z, m.target));
        }
    }

    /** Al que pego: cuantos golpes entraron (para el temblor de camara). */
    public record HitMsg(int count, boolean heavy) {
        static void encode(HitMsg m, FriendlyByteBuf b) { b.writeByte(m.count); b.writeBoolean(m.heavy); }
        static HitMsg decode(FriendlyByteBuf b) { return new HitMsg(b.readByte(), b.readBoolean()); }
        static void handle(HitMsg m, Supplier<NetworkEvent.Context> ctx) { client(ctx, () -> ClientCombat.onHit(m.count, m.heavy)); }
    }

    public record ConfigMsg(float range, int cooldown) {
        static void encode(ConfigMsg m, FriendlyByteBuf b) { b.writeFloat(m.range); b.writeInt(m.cooldown); }
        static ConfigMsg decode(FriendlyByteBuf b) { return new ConfigMsg(b.readFloat(), b.readInt()); }
        static void handle(ConfigMsg m, Supplier<NetworkEvent.Context> ctx) { client(ctx, () -> ClientCombat.onConfig(m.range, m.cooldown)); }
    }
}
