package com.starlevi.dreams.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Seleccion A-B de cada jugador con la varita (como WorldEdit). Vive solo en memoria. */
public final class Selections {
    private static final Map<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();

    private Selections() {
    }

    public record Selection(ResourceLocation dimension, BlockPos a, BlockPos b) {
        public boolean complete() {
            return a != null && b != null;
        }
    }

    public static Selection get(UUID player) {
        return SELECTIONS.get(player);
    }

    public static void setA(ServerPlayer player, BlockPos pos) {
        ResourceLocation dim = player.level().dimension().location();
        Selection old = SELECTIONS.get(player.getUUID());
        BlockPos keepB = old != null && old.dimension().equals(dim) ? old.b() : null;
        SELECTIONS.put(player.getUUID(), new Selection(dim, pos, keepB));
    }

    public static void setB(ServerPlayer player, BlockPos pos) {
        ResourceLocation dim = player.level().dimension().location();
        Selection old = SELECTIONS.get(player.getUUID());
        BlockPos keepA = old != null && old.dimension().equals(dim) ? old.a() : null;
        SELECTIONS.put(player.getUUID(), new Selection(dim, keepA, pos));
    }

    public static void clear(UUID player) {
        SELECTIONS.remove(player);
    }
}
