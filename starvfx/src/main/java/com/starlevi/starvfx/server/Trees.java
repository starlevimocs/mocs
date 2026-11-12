package com.starlevi.starvfx.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.starlevi.starvfx.Geo;
import com.starlevi.starvfx.StarVfx;
import com.starlevi.starvfx.net.Net;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * El Arbol Madre del lado del servidor: donde esta, cuanta vida tiene, de que color brilla y que pasa cuando
 * alguien le habla. Cerca de un arbol vivo la gravedad es liviana (se cae lento, se salta alto, no hay dano de
 * caida); a medida que se marchita, ese efecto se va.
 */
public final class Trees {
    private static final UUID GRAVITY = UUID.fromString("5b0e7c1e-6f0a-4f3e-9d0b-57a12f0c7e11");
    private static final Map<UUID, Long> LAST_USE = new HashMap<>();

    private Trees() {
    }

    public static JsonObject create(ServerLevel level, String id, Vec3 pos, float size) {
        JsonObject t = new JsonObject();
        t.addProperty("id", id);
        t.addProperty("dim", level.dimension().location().toString());
        t.add("pos", Geo.arr(pos.x, pos.y, pos.z));
        t.addProperty("size", size);
        t.addProperty("radius", Math.max(16, size * 0.6F));
        t.addProperty("vitality", 1.0F);
        t.addProperty("color", "#6fc8ff");
        t.addProperty("on_use", "dreams dialogue dreams:tree_hub %player%");
        return t;
    }

    /** Cuanto pesa el aura de algun arbol sobre ese jugador (0 = lejos o arbol apagado, 1 = pegado a un arbol pleno). */
    public static float aura(ServerPlayer player) {
        WorldData data = WorldData.get(player.server);
        String dim = player.serverLevel().dimension().location().toString();
        float best = 0;
        for (JsonElement e : data.trees()) {
            JsonObject t = e.getAsJsonObject();
            if (!Geo.str(t, "dim", "").equals(dim)) {
                continue;
            }
            Vec3 pos = Geo.vec(t, "pos", Vec3.ZERO);
            float radius = Geo.num(t, "radius", 30), size = Geo.num(t, "size", 80);
            double dx = player.getX() - pos.x, dz = player.getZ() - pos.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > radius || player.getY() < pos.y - 12 || player.getY() > pos.y + size * 1.2) {
                continue;
            }
            float a = (float) (1 - d / radius);
            a = Mth.clamp(a * 2.2F, 0, 1) * Mth.clamp(Geo.num(t, "vitality", 1) * 1.6F, 0, 1);
            best = Math.max(best, a);
        }
        return best;
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 4 != 0) {
            return;
        }
        boolean none = WorldData.get(server).trees().isEmpty();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            AttributeInstance gravity = p.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
            if (gravity == null) {
                continue;
            }
            float a = none || p.isSpectator() ? 0 : aura(p);
            AttributeModifier current = gravity.getModifier(GRAVITY);
            // de 0.08 (normal) hasta 0.022 pegado al arbol
            double want = -0.058 * Math.round(a * 8) / 8.0;
            if (current != null && Math.abs(current.getAmount() - want) < 1.0E-4) {
                continue;
            }
            if (current != null) {
                gravity.removeModifier(GRAVITY);
            }
            if (want < -1.0E-4) {
                gravity.addTransientModifier(new AttributeModifier(GRAVITY, "starvfx_tree", want, AttributeModifier.Operation.ADDITION));
            }
        }
    }

    /** Un jugador le hablo al arbol: corre el comando que tenga anotado (por defecto, abre su dialogo). */
    public static void onUse(ServerPlayer player, String id) {
        JsonObject t = WorldData.get(player.server).tree(id);
        if (t == null || !Geo.str(t, "dim", "").equals(player.serverLevel().dimension().location().toString())) {
            return;
        }
        Vec3 pos = Geo.vec(t, "pos", Vec3.ZERO);
        double dx = player.getX() - pos.x, dz = player.getZ() - pos.z;
        float reach = Geo.num(t, "size", 80) * 0.2F + 8;
        if (dx * dx + dz * dz > reach * reach) {
            return;
        }
        long now = player.server.getTickCount();
        Long last = LAST_USE.get(player.getUUID());
        if (last != null && now - last < 20) {
            return;
        }
        LAST_USE.put(player.getUUID(), now);
        String command = Geo.str(t, "on_use", "").replace("%player%", player.getGameProfile().getName());
        if (command.isBlank()) {
            return;
        }
        try {
            CommandSourceStack source = player.server.createCommandSourceStack().withLevel(player.serverLevel()).withPosition(pos)
                    .withPermission(2).withSuppressedOutput();
            player.server.getCommands().performPrefixedCommand(source, command);
        } catch (RuntimeException e) {
            StarVfx.LOGGER.warn("[Star VFX] Fallo el comando del arbol: {}", command, e);
        }
    }

    /** Lo ven todos: el arbol se tine de un color mientras "habla". */
    public static void speak(MinecraftServer server, String id, int color, float seconds) {
        Net.TreeSpeak msg = new Net.TreeSpeak(id, color, seconds);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Net.toPlayer(p, msg);
        }
    }

    static void reset() {
        LAST_USE.clear();
    }
}
