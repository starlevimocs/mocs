package com.starlevi.starvfx.server;

import com.google.gson.JsonArray;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dirige las escenas del lado del servidor: les avisa a todos los de la dimension que empieza, deja quietos e
 * intocables a los jugadores mientras dura (si la escena lo pide), los acomoda en ronda para "brotar" y corre los
 * comandos que la escena tenga anotados en su linea de tiempo.
 */
public final class Director {
    private static final class Running {
        int id;
        String json;
        ServerLevel level;
        Vec3 origin;
        float yaw;
        long start;
        float duration;
        boolean lock;
        final Map<UUID, Vec3> hold = new HashMap<>();
        final Map<UUID, float[]> holdRot = new HashMap<>();
        final List<JsonObject> commands = new ArrayList<>();
        int fired;
    }

    private static final List<Running> RUNNING = new ArrayList<>();
    private static int nextId = 1;

    private Director() {
    }

    public static boolean isLocked(ServerPlayer player) {
        for (Running r : RUNNING) {
            if (r.lock && r.hold.containsKey(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    public static boolean anyRunning() {
        return !RUNNING.isEmpty();
    }

    /** Altura del suelo en ese punto (si el chunk no esta cargado, la del origen). */
    static double ground(ServerLevel level, double x, double z, double fallback) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) {
            return fallback;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return Math.abs(y - fallback) > 12 ? fallback : y;
    }

    /** Empieza una escena para todos los que estan en esa dimension. Devuelve cuanto dura. */
    public static float play(ServerLevel level, JsonObject scene, Vec3 origin, float yaw) {
        Running r = new Running();
        r.id = nextId++;
        r.json = scene.toString();
        r.level = level;
        r.origin = origin;
        r.yaw = yaw;
        r.start = level.getGameTime();
        r.duration = Math.max(0.5F, Geo.num(scene, "duration", 10));
        r.lock = Geo.bool(scene, "lock", false);

        // una escena que traba a los jugadores no se pisa con otra
        if (r.lock) {
            for (Iterator<Running> it = RUNNING.iterator(); it.hasNext(); ) {
                Running old = it.next();
                if (old.lock && old.level == level) {
                    Net.toLevel(level, new Net.StopScene(old.id));
                    it.remove();
                }
            }
        }

        JsonObject sprout = null;
        if (scene.has("events") && scene.get("events").isJsonArray()) {
            for (JsonElement e : scene.getAsJsonArray("events")) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject ev = e.getAsJsonObject();
                String type = Geo.str(ev, "type", "");
                if (type.equals("command")) {
                    r.commands.add(ev);
                } else if (type.equals("sprout") && sprout == null) {
                    sprout = ev;
                }
            }
        }
        r.commands.sort(Comparator.comparingDouble(ev -> Geo.num(ev, "t", 0)));

        List<ServerPlayer> players = new ArrayList<>(level.players());
        players.removeIf(ServerPlayer::isSpectator);
        if (sprout != null && !players.isEmpty()) {
            // en ronda alrededor del origen, mirando al centro; brotan todos juntos
            float radius = Geo.num(sprout, "radius", 5);
            float t = Geo.num(sprout, "t", 0);
            float dur = Math.max(0.5F, Geo.num(sprout, "duration", 4));
            if (players.size() > 8) {
                radius = Math.max(radius, players.size() * 0.75F);
            }
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer p = players.get(i);
                double a = Math.PI * 2 * i / players.size();
                Vec3 pos = Geo.toWorld(origin, yaw, Math.sin(a) * radius, 0, Math.cos(a) * radius);
                double y = ground(level, pos.x, pos.z, origin.y);
                float face = (float) (Mth.atan2(origin.z - pos.z, origin.x - pos.x) * Mth.RAD_TO_DEG) - 90F;
                p.stopRiding();
                p.teleportTo(level, pos.x, y, pos.z, face, 0);
                Net.toLevel(level, new Net.PlayerAnim(p.getId(), "sprout", t, dur));
            }
        }
        if (r.lock) {
            for (ServerPlayer p : players) {
                r.hold.put(p.getUUID(), p.position());
                r.holdRot.put(p.getUUID(), new float[]{p.getYRot(), p.getXRot()});
            }
        }
        RUNNING.add(r);
        Net.toLevel(level, new Net.PlayScene(r.id, r.json, origin.x, origin.y, origin.z, yaw, 0));
        return r.duration;
    }

    /** Algo suelto en pantalla (un texto, un fundido, un temblor) sin cortar lo que este pasando. */
    public static void quick(ServerLevel level, JsonObject event, float duration) {
        JsonObject scene = new JsonObject();
        scene.addProperty("duration", duration);
        JsonArray events = new JsonArray();
        events.add(event);
        scene.add("events", events);
        Net.toLevel(level, new Net.PlayScene(0, scene.toString(), 0, 0, 0, 0, 0));
    }

    public static int stopAll() {
        int n = RUNNING.size();
        for (Running r : RUNNING) {
            Net.toLevel(r.level, new Net.StopScene(r.id));
            for (UUID id : r.hold.keySet()) {
                ServerPlayer p = r.level.getServer().getPlayerList().getPlayer(id);
                if (p != null) {
                    Net.toLevel(r.level, new Net.PlayerAnim(p.getId(), "none", 0, 0));
                }
            }
        }
        RUNNING.clear();
        return n;
    }

    /** Al apagar el servidor: no queda nada en curso. */
    static void reset() {
        RUNNING.clear();
    }

    /** Al que entra en medio de una escena se la mostramos desde donde va. */
    static void onJoin(ServerPlayer player) {
        for (Running r : RUNNING) {
            if (r.level == player.level()) {
                float elapsed = (r.level.getGameTime() - r.start) / 20F;
                Net.toPlayer(player, new Net.PlayScene(r.id, r.json, r.origin.x, r.origin.y, r.origin.z, r.yaw, elapsed));
            }
        }
    }

    static void tick(MinecraftServer server) {
        if (RUNNING.isEmpty()) {
            return;
        }
        for (Iterator<Running> it = RUNNING.iterator(); it.hasNext(); ) {
            Running r = it.next();
            float elapsed = (r.level.getGameTime() - r.start) / 20F;
            while (r.fired < r.commands.size() && Geo.num(r.commands.get(r.fired), "t", 0) <= elapsed) {
                run(server, r, Geo.str(r.commands.get(r.fired), "command", ""));
                r.fired++;
            }
            if (r.lock) {
                for (Map.Entry<UUID, Vec3> h : r.hold.entrySet()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(h.getKey());
                    if (p == null || p.level() != r.level) {
                        continue;
                    }
                    Vec3 at = h.getValue();
                    p.setDeltaMovement(Vec3.ZERO);
                    p.fallDistance = 0;
                    if (p.position().distanceToSqr(at) > 0.04) {
                        float[] rot = r.holdRot.get(h.getKey());
                        p.connection.teleport(at.x, at.y, at.z, rot[0], rot[1]);
                    }
                }
            }
            if (elapsed >= r.duration) {
                it.remove();
            }
        }
    }

    private static void run(MinecraftServer server, Running r, String command) {
        if (command.isBlank()) {
            return;
        }
        try {
            CommandSourceStack source = server.createCommandSourceStack().withLevel(r.level).withPosition(r.origin)
                    .withRotation(new Vec2(0, r.yaw)).withPermission(2).withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, command);
        } catch (RuntimeException e) {
            StarVfx.LOGGER.warn("[Star VFX] Fallo el comando de la escena: {}", command, e);
        }
    }
}
