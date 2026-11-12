package com.starlevi.starvfx.server;

import com.starlevi.starvfx.StarVfx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos del servidor. */
@Mod.EventBusSubscriber(modid = StarVfx.MOD_ID)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        VfxCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Director.tick(event.getServer());
            Lifecycle.tick(event.getServer());
            Trees.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            WorldData.get(p.server).sync(p);
            Director.onJoin(p);
            Lifecycle.onJoin(p);
        }
    }

    @SubscribeEvent
    public static void quit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Lifecycle.onQuit(p);
        }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            WorldData.get(p.server).sync(p);
            if (!event.isEndConquered()) {
                Lifecycle.onRespawn(p);
            }
        }
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            WorldData.get(p.server).sync(p);
        }
    }

    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer watcher && event.getTarget() instanceof ServerPlayer target) {
            Lifecycle.onStartTracking(watcher, target);
        }
    }

    /** En medio de una cinematica nadie recibe dano. */
    @SubscribeEvent
    public static void attack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && Director.isLocked(p)) {
            event.setCanceled(true);
        }
    }

    /** Cerca del Arbol Madre nadie se lastima al caer. */
    @SubscribeEvent
    public static void fall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && Trees.aura(p) > 0.05F) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        Director.reset();
        Trees.reset();
    }
}
