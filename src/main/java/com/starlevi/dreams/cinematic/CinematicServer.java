package com.starlevi.dreams.cinematic;

import com.starlevi.dreams.DreamsMod;
import com.starlevi.dreams.network.DreamsNetwork;
import com.starlevi.dreams.network.PlayCinematicPacket;
import com.starlevi.dreams.network.StopCinematicPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lado servidor de las cinematicas: manda la reproduccion a los jugadores, los hace invulnerables
 * mientras la ven y ejecuta el comando "al terminar".
 */
@Mod.EventBusSubscriber(modid = DreamsMod.MOD_ID)
public final class CinematicServer {
    /** Tiempo extra de proteccion despues del final, por si el jugador tarda en reaccionar. */
    private static final int GRACE_TICKS = 60;
    /** Una cinematica "en bucle" (la de un dialogo) termina cuando el cliente avisa; esto es un tope de seguridad. */
    private static final int LOOP_SAFETY_TICKS = 20 * 60 * 10;

    private record Pending(int dueTick, String command) {
    }

    private static final Map<UUID, Integer> PROTECTED_UNTIL = new HashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();

    private CinematicServer() {
    }

    /** Reproduce la cinematica a esos jugadores, todos al mismo tiempo. */
    public static void play(MinecraftServer server, Collection<ServerPlayer> players, Cinematic cinematic, boolean loop) {
        int duration = cinematic.durationTicks();
        for (ServerPlayer player : players) {
            DreamsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PlayCinematicPacket(cinematic, loop));
            PROTECTED_UNTIL.put(player.getUUID(), server.getTickCount() + (loop ? LOOP_SAFETY_TICKS : duration + GRACE_TICKS));
        }
        String command = cinematic.onEnd();
        if (!loop && command != null && !command.isBlank()) {
            PENDING.add(new Pending(server.getTickCount() + duration, command));
        }
    }

    public static void stop(Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            DreamsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StopCinematicPacket());
            PROTECTED_UNTIL.remove(player.getUUID());
        }
    }

    /** Un cliente pide una cinematica (la de un dialogo) o avisa que termino la suya. */
    public static void handleRequest(ServerPlayer player, String name, boolean start) {
        if (!start) {
            PROTECTED_UNTIL.remove(player.getUUID());
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Cinematic cinematic = CinematicSavedData.get(server).find(name);
        if (cinematic == null || cinematic.durationTicks() <= 0) {
            return;
        }
        play(server, List.of(player), cinematic, true);
    }

    // ---------------------------------------------------------------- eventos

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        Iterator<Pending> it = PENDING.iterator();
        List<String> due = new ArrayList<>();
        while (it.hasNext()) {
            Pending pending = it.next();
            if (now >= pending.dueTick()) {
                due.add(pending.command());
                it.remove();
            }
        }
        for (String command : due) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
        }
    }

    /** Mientras ven la cinematica, los jugadores no reciben dano. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        Integer until = PROTECTED_UNTIL.get(player.getUUID());
        if (until == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server != null && server.getTickCount() <= until) {
            event.setCanceled(true);
        } else {
            PROTECTED_UNTIL.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PROTECTED_UNTIL.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PROTECTED_UNTIL.clear();
        PENDING.clear();
    }
}
