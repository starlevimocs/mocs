package com.starlevi.starhero.net;

import com.starlevi.starhero.StarHero;
import com.starlevi.starhero.server.Stages;
import net.minecraft.core.BlockPos;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Mensajes entre el cliente y el servidor. */
public final class Net {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StarHero.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private Net() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, LobbyAction.class, LobbyAction::encode, LobbyAction::decode, LobbyAction::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, PerformerUpdate.class, PerformerUpdate::encode, PerformerUpdate::decode, PerformerUpdate::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, PerformerResult.class, PerformerResult::encode, PerformerResult::decode, PerformerResult::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, LobbyState.class, LobbyState::encode, LobbyState::decode, LobbyState::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, GigStart.class, GigStart::encode, GigStart::decode, GigStart::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, GigNotes.class, GigNotes::encode, GigNotes::decode, GigNotes::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, GigState.class, GigState::encode, GigState::decode, GigState::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, GigEnd.class, GigEnd::encode, GigEnd::decode, GigEnd::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
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

    // ================================================================ datos compartidos

    /** Integrante de la banda (jugador o aldeano). */
    public record Member(int entityId, String name, String instrument, boolean player, boolean leader) {
        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeUtf(name);
            buf.writeUtf(instrument);
            buf.writeBoolean(player);
            buf.writeBoolean(leader);
        }

        static Member read(FriendlyByteBuf buf) {
            return new Member(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readBoolean());
        }
    }

    private static void writeMembers(FriendlyByteBuf buf, List<Member> members) {
        buf.writeVarInt(members.size());
        members.forEach(m -> m.write(buf));
    }

    private static List<Member> readMembers(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Member> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(Member.read(buf));
        }
        return list;
    }

    // ================================================================ cliente -> servidor

    /** Algo que se toca en el menu: instrumento, modo, cancion, empezar, salir. */
    public record LobbyAction(BlockPos amp, String action, String arg) {
        void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(amp);
            buf.writeUtf(action);
            buf.writeUtf(arg, 2048);
        }

        static LobbyAction decode(FriendlyByteBuf buf) {
            return new LobbyAction(buf.readBlockPos(), buf.readUtf(), buf.readUtf(2048));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null) {
                    Stages.onLobbyAction(player, this);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Lo que esta tocando un jugador: teclas apretadas, notas acertadas, combo. */
    public record PerformerUpdate(int pressed, int hits, int fret, int combo, int score, int misses, int newHits, int newMisses) {
        void encode(FriendlyByteBuf buf) {
            buf.writeByte(pressed);
            buf.writeByte(hits);
            buf.writeByte(fret);
            buf.writeVarInt(combo);
            buf.writeVarInt(score);
            buf.writeVarInt(misses);
            buf.writeVarInt(newHits);
            buf.writeVarInt(newMisses);
        }

        static PerformerUpdate decode(FriendlyByteBuf buf) {
            return new PerformerUpdate(buf.readByte(), buf.readByte(), buf.readByte(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null) {
                    Stages.onPerformerUpdate(player, this);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** El jugador termino la cancion (o no la tenia, o la abandono). */
    public record PerformerResult(int score, float accuracy, int maxCombo, int misses, String rank, boolean completed) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(score);
            buf.writeFloat(accuracy);
            buf.writeVarInt(maxCombo);
            buf.writeVarInt(misses);
            buf.writeUtf(rank);
            buf.writeBoolean(completed);
        }

        static PerformerResult decode(FriendlyByteBuf buf) {
            return new PerformerResult(buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readBoolean());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null) {
                    Stages.onPerformerResult(player, this);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ================================================================ servidor -> cliente

    /** Estado del grupo antes de tocar (y abre el menu si openScreen). */
    public record LobbyState(BlockPos amp, boolean openScreen, boolean bandMode, String song, String songTitle, List<Member> members) {
        void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(amp);
            buf.writeBoolean(openScreen);
            buf.writeBoolean(bandMode);
            buf.writeUtf(song);
            buf.writeUtf(songTitle);
            writeMembers(buf, members);
        }

        static LobbyState decode(FriendlyByteBuf buf) {
            return new LobbyState(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(), buf.readUtf(), readMembers(buf));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starhero.client.ClientNet.lobby(this));
        }
    }

    /** Arranca el show: todos los que estan cerca lo ven; los jugadores de la banda empiezan a tocar. */
    public record GigStart(BlockPos amp, int facing, String song, String songTitle, int countdownMs, float bpm, int offsetMs,
                           int leadInMs, double cx, double cy, double cz, List<Member> members) {
        void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(amp);
            buf.writeByte(facing);
            buf.writeUtf(song);
            buf.writeUtf(songTitle);
            buf.writeVarInt(countdownMs);
            buf.writeFloat(bpm);
            buf.writeVarInt(offsetMs);
            buf.writeVarInt(leadInMs);
            buf.writeDouble(cx);
            buf.writeDouble(cy);
            buf.writeDouble(cz);
            writeMembers(buf, members);
        }

        static GigStart decode(FriendlyByteBuf buf) {
            return new GigStart(buf.readBlockPos(), buf.readByte(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readFloat(),
                    buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), readMembers(buf));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starhero.client.ClientNet.gigStart(this));
        }
    }

    /** Lo que toca otro integrante (para animarlo en pantalla). */
    public record GigNotes(int entityId, int pressed, int hits, int fret) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeByte(pressed);
            buf.writeByte(hits);
            buf.writeByte(fret);
        }

        static GigNotes decode(FriendlyByteBuf buf) {
            return new GigNotes(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readByte());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starhero.client.ClientNet.notes(this));
        }
    }

    /** Euforia del publico y puntaje de la banda. */
    public record GigState(float hype, int bandScore, int bandCombo) {
        void encode(FriendlyByteBuf buf) {
            buf.writeFloat(hype);
            buf.writeVarInt(bandScore);
            buf.writeVarInt(bandCombo);
        }

        static GigState decode(FriendlyByteBuf buf) {
            return new GigState(buf.readFloat(), buf.readVarInt(), buf.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starhero.client.ClientNet.state(this));
        }
    }

    /** Termino el show. */
    public record GigEnd(String headline, String detail, int tips, float hype) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(headline);
            buf.writeUtf(detail);
            buf.writeVarInt(tips);
            buf.writeFloat(hype);
        }

        static GigEnd decode(FriendlyByteBuf buf) {
            return new GigEnd(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readFloat());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            client(ctx, () -> com.starlevi.starhero.client.ClientNet.end(this));
        }
    }
}
