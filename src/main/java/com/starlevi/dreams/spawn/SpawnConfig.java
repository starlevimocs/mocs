package com.starlevi.dreams.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Toda la configuracion de spawn natural. Es inmutable: cada cambio crea una copia nueva,
 * asi se puede leer sin bloqueos desde los hilos de generacion de chunks.
 *
 * <p>Orden para decidir el modo de un mob: regla del mob ("mod:mob"), regla del mod ("mod:*"),
 * y si no hay ninguna, el modo por defecto (animales vanilla: NATURAL; todo lo demas: ZONES).
 */
public final class SpawnConfig {
    /** Namespaces de los mods de caballos que deben aparecer de forma natural por defecto. */
    private static final List<String> DEFAULT_NATURAL_MODS = List.of("icys-better-horses", "icys_better_horses");

    private static final Set<MobCategory> VANILLA_ANIMAL_CATEGORIES = Set.of(
            MobCategory.CREATURE,
            MobCategory.WATER_CREATURE,
            MobCategory.WATER_AMBIENT,
            MobCategory.UNDERGROUND_WATER_CREATURE,
            MobCategory.AXOLOTLS);

    private final Map<String, SpawnMode> rules;
    private final List<SpawnZone> zones;

    public SpawnConfig(Map<String, SpawnMode> rules, List<SpawnZone> zones) {
        this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
        this.zones = List.copyOf(zones);
    }

    public static SpawnConfig initial() {
        Map<String, SpawnMode> rules = new LinkedHashMap<>();
        for (String ns : DEFAULT_NATURAL_MODS) {
            rules.put(ns + ":*", SpawnMode.NATURAL);
        }
        return new SpawnConfig(rules, List.of());
    }

    public Map<String, SpawnMode> rules() {
        return rules;
    }

    public List<SpawnZone> zones() {
        return zones;
    }

    public SpawnZone zone(String name) {
        for (SpawnZone zone : zones) {
            if (zone.name().equalsIgnoreCase(name)) {
                return zone;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- decision

    public static String idOf(EntityType<?> type) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return key == null ? null : key.toString();
    }

    public static String wildcardOf(String entityId) {
        int colon = entityId.indexOf(':');
        return (colon < 0 ? "minecraft" : entityId.substring(0, colon)) + ":*";
    }

    /** Modo por defecto: animales de vanilla pueden aparecer, todo lo demas solo en zonas. */
    public static SpawnMode defaultMode(EntityType<?> type) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (key != null && "minecraft".equals(key.getNamespace()) && VANILLA_ANIMAL_CATEGORIES.contains(type.getCategory())) {
            return SpawnMode.NATURAL;
        }
        return SpawnMode.ZONES;
    }

    /** Regla explicita para este mob (la suya o la de su mod), o null si no hay ninguna. */
    public SpawnMode explicitMode(String entityId) {
        SpawnMode mode = rules.get(entityId);
        return mode != null ? mode : rules.get(wildcardOf(entityId));
    }

    public SpawnMode effectiveMode(EntityType<?> type) {
        String id = idOf(type);
        SpawnMode explicit = id == null ? null : explicitMode(id);
        return explicit != null ? explicit : defaultMode(type);
    }

    /** Decide si este mob puede aparecer de forma natural en esa posicion. */
    public boolean isAllowed(EntityType<?> type, ServerLevelAccessor level, BlockPos pos) {
        SpawnMode mode = effectiveMode(type);
        if (mode == SpawnMode.NATURAL) {
            return true;
        }
        if (mode == SpawnMode.NEVER) {
            return false;
        }
        String id = idOf(type);
        if (id == null) {
            return true;
        }
        String wildcard = wildcardOf(id);
        for (SpawnZone zone : zones) {
            if (zone.allowsEntity(id, wildcard) && zone.contains(level, pos)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- cambios

    /** @param mode null para volver a "automatico" (quita la regla) */
    public SpawnConfig withRule(String id, SpawnMode mode) {
        Map<String, SpawnMode> copy = new LinkedHashMap<>(rules);
        if (mode == null) {
            copy.remove(id);
        } else {
            copy.put(id, mode);
        }
        return new SpawnConfig(copy, zones);
    }

    public SpawnConfig withZone(SpawnZone zone) {
        List<SpawnZone> copy = new ArrayList<>();
        boolean replaced = false;
        for (SpawnZone existing : zones) {
            if (existing.name().equalsIgnoreCase(zone.name())) {
                copy.add(zone);
                replaced = true;
            } else {
                copy.add(existing);
            }
        }
        if (!replaced) {
            copy.add(zone);
        }
        return new SpawnConfig(rules, copy);
    }

    public SpawnConfig withoutZone(String name) {
        List<SpawnZone> copy = new ArrayList<>(zones);
        copy.removeIf(zone -> zone.name().equalsIgnoreCase(name));
        return new SpawnConfig(rules, copy);
    }

    public SpawnConfig withZoneChange(String name, UnaryOperator<SpawnZone> change) {
        SpawnZone zone = zone(name);
        return zone == null ? this : withZone(change.apply(zone));
    }

    // ---------------------------------------------------------------- NBT

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag ruleList = new ListTag();
        for (Map.Entry<String, SpawnMode> entry : rules.entrySet()) {
            CompoundTag rule = new CompoundTag();
            rule.putString("id", entry.getKey());
            rule.putString("mode", entry.getValue().name());
            ruleList.add(rule);
        }
        tag.put("rules", ruleList);

        ListTag zoneList = new ListTag();
        for (SpawnZone zone : zones) {
            zoneList.add(zone.toTag());
        }
        tag.put("zones", zoneList);
        return tag;
    }

    public static SpawnConfig fromTag(CompoundTag tag) {
        Map<String, SpawnMode> rules = new LinkedHashMap<>();
        ListTag ruleList = tag.getList("rules", Tag.TAG_COMPOUND);
        for (int i = 0; i < ruleList.size(); i++) {
            CompoundTag rule = ruleList.getCompound(i);
            try {
                rules.put(rule.getString("id"), SpawnMode.valueOf(rule.getString("mode")));
            } catch (IllegalArgumentException ignored) {
                // regla vieja o danada: se ignora
            }
        }

        List<SpawnZone> zones = new ArrayList<>();
        ListTag zoneList = tag.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < zoneList.size(); i++) {
            try {
                zones.add(SpawnZone.fromTag(zoneList.getCompound(i)));
            } catch (RuntimeException ignored) {
                // zona danada: se ignora
            }
        }
        return new SpawnConfig(rules, zones);
    }
}
