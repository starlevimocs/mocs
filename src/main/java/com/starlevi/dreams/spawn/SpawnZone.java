package com.starlevi.dreams.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Una zona donde ciertos mobs (o mods enteros) pueden aparecer.
 * Puede ser una caja entre dos puntos (como WorldEdit) o un bioma completo.
 *
 * @param allowed ids permitidos: "mod:mob" para un mob o "mod:*" para todo un mod
 */
public record SpawnZone(String name, Kind kind, ResourceLocation dimension, BlockPos min, BlockPos max,
                        ResourceLocation biome, Set<String> allowed) {

    public enum Kind {
        BOX,
        BIOME
    }

    public static SpawnZone box(String name, ResourceLocation dimension, BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        return new SpawnZone(name, Kind.BOX, dimension, min, max, null, Set.of());
    }

    public static SpawnZone biome(String name, ResourceLocation biome) {
        return new SpawnZone(name, Kind.BIOME, null, null, null, biome, Set.of());
    }

    public boolean allowsEntity(String entityId, String modWildcard) {
        return allowed.contains(entityId) || allowed.contains(modWildcard);
    }

    public boolean contains(ServerLevelAccessor level, BlockPos pos) {
        if (kind == Kind.BOX) {
            return level.getLevel().dimension().location().equals(dimension)
                    && pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
        return level.getBiome(pos).unwrapKey()
                .map(key -> key.location().equals(biome))
                .orElse(false);
    }

    public SpawnZone withAllowed(String id, boolean on) {
        Set<String> copy = new LinkedHashSet<>(allowed);
        if (on) {
            copy.add(id);
        } else {
            copy.remove(id);
        }
        return new SpawnZone(name, kind, dimension, min, max, biome, Collections.unmodifiableSet(copy));
    }

    /** Texto corto para mostrar en la pantalla. */
    public String describe() {
        if (kind == Kind.BIOME) {
            return "Bioma " + biome;
        }
        return "Caja " + min.getX() + "," + min.getY() + "," + min.getZ()
                + " -> " + max.getX() + "," + max.getY() + "," + max.getZ()
                + " (" + dimension + ")";
    }

    // ---------------------------------------------------------------- NBT

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("kind", kind.name());
        if (kind == Kind.BOX) {
            tag.putString("dim", dimension.toString());
            tag.putInt("minX", min.getX());
            tag.putInt("minY", min.getY());
            tag.putInt("minZ", min.getZ());
            tag.putInt("maxX", max.getX());
            tag.putInt("maxY", max.getY());
            tag.putInt("maxZ", max.getZ());
        } else {
            tag.putString("biome", biome.toString());
        }
        ListTag list = new ListTag();
        for (String id : allowed) {
            list.add(StringTag.valueOf(id));
        }
        tag.put("allowed", list);
        return tag;
    }

    public static SpawnZone fromTag(CompoundTag tag) {
        String name = tag.getString("name");
        Set<String> allowed = new LinkedHashSet<>();
        ListTag list = tag.getList("allowed", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            allowed.add(list.getString(i));
        }
        Set<String> frozen = Collections.unmodifiableSet(allowed);

        if ("BIOME".equals(tag.getString("kind"))) {
            return new SpawnZone(name, Kind.BIOME, null, null, null, new ResourceLocation(tag.getString("biome")), frozen);
        }
        BlockPos min = new BlockPos(tag.getInt("minX"), tag.getInt("minY"), tag.getInt("minZ"));
        BlockPos max = new BlockPos(tag.getInt("maxX"), tag.getInt("maxY"), tag.getInt("maxZ"));
        return new SpawnZone(name, Kind.BOX, new ResourceLocation(tag.getString("dim")), min, max, null, frozen);
    }
}
