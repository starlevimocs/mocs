package com.starlevi.dreams.network;

import com.starlevi.dreams.item.SpawnWandItem;
import com.starlevi.dreams.spawn.Selections;
import com.starlevi.dreams.spawn.SpawnConfig;
import com.starlevi.dreams.spawn.SpawnManager;
import com.starlevi.dreams.spawn.SpawnMode;
import com.starlevi.dreams.spawn.SpawnZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Logica del servidor para la pantalla de control de spawn. */
public final class SpawnNet {
    private static final int MAX_NAME_LENGTH = 32;

    private SpawnNet() {
    }

    /** Manda al jugador la configuracion, su seleccion A-B y su bioma actual. */
    public static void sendSync(ServerPlayer player, boolean open) {
        SpawnConfig config = SpawnManager.config(player.server);
        Selections.Selection selection = Selections.get(player.getUUID());
        String biome = player.level().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.location().toString()).orElse("");
        DreamsNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new SyncSpawnPacket(config, open,
                        selection == null ? null : selection.dimension(),
                        selection == null ? null : selection.a(),
                        selection == null ? null : selection.b(),
                        biome));
    }

    public static void handleAction(ServerPlayer player, SpawnActionPacket packet) {
        if (!player.hasPermissions(2)) {
            SpawnWandItem.message(player, "Solo los operadores pueden cambiar el spawn.");
            return;
        }
        MinecraftServer server = player.server;

        switch (packet.action()) {
            case SET_RULE -> {
                String id = packet.a();
                if (id.indexOf(':') > 0) {
                    SpawnMode mode = SpawnMode.byOrdinal(packet.n());
                    SpawnManager.update(server, config -> config.withRule(id, mode));
                }
            }
            case CREATE_BOX -> createBox(player, packet.a().trim(), packet.n() == 1);
            case CREATE_BIOME -> createBiome(player, packet.a().trim());
            case DELETE_ZONE -> SpawnManager.update(server, config -> config.withoutZone(packet.a()));
            case TOGGLE_ALLOW -> {
                String zone = packet.a();
                String id = packet.b();
                boolean on = packet.n() == 1;
                if (id.indexOf(':') > 0) {
                    SpawnManager.update(server, config -> config.withZoneChange(zone, z -> z.withAllowed(id, on)));
                }
            }
            case CLEAR_SELECTION -> {
                Selections.clear(player.getUUID());
                SpawnWandItem.message(player, "Seleccion borrada.");
            }
            case REFRESH -> {
            }
        }
        sendSync(player, false);
    }

    private static String validateName(ServerPlayer player, String name) {
        if (name.isEmpty()) {
            SpawnWandItem.message(player, "Escribi un nombre para la zona.");
            return null;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            SpawnWandItem.message(player, "El nombre es muy largo (maximo " + MAX_NAME_LENGTH + ").");
            return null;
        }
        if (SpawnManager.config(player.server).zone(name) != null) {
            SpawnWandItem.message(player, "Ya existe una zona llamada '" + name + "'.");
            return null;
        }
        return name;
    }

    private static void createBox(ServerPlayer player, String name, boolean fullHeight) {
        if (validateName(player, name) == null) {
            return;
        }
        Selections.Selection selection = Selections.get(player.getUUID());
        if (selection == null || !selection.complete()) {
            SpawnWandItem.message(player, "Marca primero los puntos A y B con la varita.");
            return;
        }
        BlockPos a = selection.a();
        BlockPos b = selection.b();
        if (fullHeight) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, selection.dimension());
            ServerLevel level = player.server.getLevel(key);
            int minY = level != null ? level.getMinBuildHeight() : -64;
            int maxY = level != null ? level.getMaxBuildHeight() - 1 : 319;
            a = new BlockPos(a.getX(), minY, a.getZ());
            b = new BlockPos(b.getX(), maxY, b.getZ());
        }
        SpawnZone zone = SpawnZone.box(name, selection.dimension(), a, b);
        SpawnManager.update(player.server, config -> config.withZone(zone));
        SpawnWandItem.message(player, "Zona '" + name + "' creada.");
    }

    private static void createBiome(ServerPlayer player, String name) {
        if (validateName(player, name) == null) {
            return;
        }
        ResourceLocation biome = player.level().getBiome(player.blockPosition()).unwrapKey()
                .map(ResourceKey::location).orElse(null);
        if (biome == null) {
            SpawnWandItem.message(player, "No pude detectar el bioma donde estas.");
            return;
        }
        SpawnZone zone = SpawnZone.biome(name, biome);
        SpawnManager.update(player.server, config -> config.withZone(zone));
        SpawnWandItem.message(player, "Zona '" + name + "' creada (bioma " + biome + ").");
    }
}
