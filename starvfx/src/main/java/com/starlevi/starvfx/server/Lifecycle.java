package com.starlevi.starvfx.server;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starlevi.starvfx.Geo;
import com.starlevi.starvfx.StarVfx;
import com.starlevi.starvfx.net.Net;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Animaciones de la vida del jugador, a tono con la historia: al entrar y al reaparecer brota del suelo entre
 * hojas y luz; al irse se deshace en hojas que suben; si se queda quieto un rato largo se sienta a dormitar.
 * Se ajusta en {@code config/starvfx/config.json}.
 */
public final class Lifecycle {
    private static final class Activity {
        Vec3 pos = Vec3.ZERO;
        float yaw, pitch;
        long lastActive;
        boolean afk;
    }

    private record Later(long tick, Runnable run) {
    }

    private static final Map<UUID, Activity> ACTIVITY = new HashMap<>();
    private static final List<Later> LATER = new ArrayList<>();
    private static JsonObject config;
    private static int fxCounter;

    private Lifecycle() {
    }

    public static JsonObject config() {
        if (config == null) {
            reload();
        }
        return config;
    }

    public static void reload() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("starvfx").resolve("config.json");
        JsonObject def = new JsonObject();
        def.addProperty("join", true);
        def.addProperty("quit", true);
        def.addProperty("respawn", true);
        def.addProperty("afk", true);
        def.addProperty("afk_seconds", 180);
        def.addProperty("color", "#8dffb0");
        try {
            if (Files.isRegularFile(file)) {
                JsonObject read = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                for (String k : read.keySet()) {
                    def.add(k, read.get(k));
                }
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(def), StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException e) {
            StarVfx.LOGGER.warn("[Star VFX] No se pudo leer config.json, se usan los valores por defecto", e);
        }
        config = def;
    }

    private static void later(MinecraftServer server, int ticks, Runnable run) {
        LATER.add(new Later(server.getTickCount() + ticks, run));
    }

    /** Efecto pasajero (dura lo que diga y se va solo). */
    static void burst(ServerLevel level, String type, Vec3 pos, float duration, float size) {
        JsonObject p = new JsonObject();
        p.addProperty("duration", duration);
        p.addProperty("size", size);
        p.addProperty("color", Geo.str(config(), "color", "#8dffb0"));
        Net.toLevel(level, new Net.Fx("~" + (fxCounter++), false, type, pos.x, pos.y, pos.z, p.toString(), 0));
    }

    /** Brota del suelo: lo ven todos los de la dimension. */
    public static void sprout(ServerPlayer player, float duration) {
        ServerLevel level = player.serverLevel();
        Net.toLevel(level, new Net.PlayerAnim(player.getId(), "sprout", 0, duration));
        burst(level, "shockwave", player.position().add(0, 0.05, 0), 1.6F, 3.5F);
        burst(level, "swarm", player.position(), duration + 1.5F, 1.6F);
        burst(level, "pillar", player.position(), duration, 1.2F);
    }

    static void onJoin(ServerPlayer player) {
        Activity a = new Activity();
        a.lastActive = player.server.getTickCount();
        ACTIVITY.put(player.getUUID(), a);
        if (Geo.bool(config(), "join", true) && !Director.anyRunning()) {
            UUID id = player.getUUID();
            later(player.server, 50, () -> {
                ServerPlayer p = player.server.getPlayerList().getPlayer(id);
                if (p != null && p.isAlive() && !p.isSpectator() && !Director.isLocked(p)) {
                    sprout(p, 3.2F);
                }
            });
        }
    }

    static void onQuit(ServerPlayer player) {
        ACTIVITY.remove(player.getUUID());
        if (Geo.bool(config(), "quit", true) && !player.isSpectator()) {
            ServerLevel level = player.serverLevel();
            burst(level, "pillar", player.position(), 2.6F, 1.4F);
            burst(level, "swarm", player.position(), 3.5F, 1.8F);
            burst(level, "shockwave", player.position().add(0, 0.05, 0), 1.4F, 3F);
        }
    }

    static void onRespawn(ServerPlayer player) {
        if (Geo.bool(config(), "respawn", true)) {
            UUID id = player.getUUID();
            later(player.server, 12, () -> {
                ServerPlayer p = player.server.getPlayerList().getPlayer(id);
                if (p != null && p.isAlive() && !p.isSpectator()) {
                    sprout(p, 2.6F);
                }
            });
        }
    }

    /** El que empieza a ver a un jugador dormitando tiene que enterarse. */
    static void onStartTracking(ServerPlayer watcher, ServerPlayer target) {
        Activity a = ACTIVITY.get(target.getUUID());
        if (a != null && a.afk) {
            Net.toPlayer(watcher, new Net.PlayerAnim(target.getId(), "afk", 0, 0));
        }
    }

    public static void setAfk(ServerPlayer player, boolean afk) {
        Activity a = ACTIVITY.computeIfAbsent(player.getUUID(), k -> new Activity());
        if (a.afk == afk) {
            return;
        }
        a.afk = afk;
        a.lastActive = player.server.getTickCount();
        Net.toLevel(player.serverLevel(), new Net.PlayerAnim(player.getId(), afk ? "afk" : "none", 0, 0));
    }

    static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (!LATER.isEmpty()) {
            List<Runnable> due = new ArrayList<>();
            for (Iterator<Later> it = LATER.iterator(); it.hasNext(); ) {
                Later l = it.next();
                if (l.tick <= now) {
                    due.add(l.run);
                    it.remove();
                }
            }
            due.forEach(Runnable::run);
        }
        if (now % 5 != 0) {
            return;
        }
        boolean afkOn = Geo.bool(config(), "afk", true);
        int limit = (int) (Math.max(20, Geo.num(config(), "afk_seconds", 180)) * 20);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Activity a = ACTIVITY.computeIfAbsent(p.getUUID(), k -> {
                Activity n = new Activity();
                n.lastActive = now;
                return n;
            });
            boolean moved = p.position().distanceToSqr(a.pos) > 0.0004 || Math.abs(p.getYRot() - a.yaw) > 0.5F
                    || Math.abs(p.getXRot() - a.pitch) > 0.5F || p.swinging || p.isUsingItem();
            a.pos = p.position();
            a.yaw = p.getYRot();
            a.pitch = p.getXRot();
            if (moved) {
                a.lastActive = now;
                if (a.afk) {
                    setAfk(p, false);
                }
            } else if (afkOn && !a.afk && now - a.lastActive > limit && p.isAlive() && !p.isSpectator() && p.onGround()
                    && !p.isPassenger() && !p.isSleeping() && !Director.isLocked(p)) {
                setAfk(p, true);
            }
        }
    }
}
