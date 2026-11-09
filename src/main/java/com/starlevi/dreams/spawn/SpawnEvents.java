package com.starlevi.dreams.spawn;

import com.starlevi.dreams.DreamsMod;
import com.starlevi.dreams.item.SpawnWandItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos del lado servidor: filtra el spawn natural y maneja el clic izquierdo de la varita. */
@Mod.EventBusSubscriber(modid = DreamsMod.MOD_ID)
public final class SpawnEvents {
    private SpawnEvents() {
    }

    /** Bloquea los spawns naturales que la configuracion no permite. */
    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        ServerLevelAccessor level = event.getLevel();
        SpawnConfig config = SpawnManager.config(level.getLevel().getServer());
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (!config.isAllowed(event.getEntity().getType(), level, pos)) {
            event.setSpawnCancelled(true);
        }
    }

    /** Clic izquierdo con la varita sobre un bloque = punto A (y no rompe el bloque). */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof SpawnWandItem)) {
            return;
        }
        event.setCanceled(true);
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        if (!SpawnWandItem.canUse(player)) {
            return;
        }
        BlockPos pos = event.getPos();
        Selections.setA(player, pos);
        SpawnWandItem.message(player, "Punto A: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Selections.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SpawnManager.reset();
    }
}
