package com.starlevi.starvfx.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starlevi.starvfx.Geo;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * La camara: la de la escena en curso, la del jugador brotando (se ve a si mismo salir del suelo) o la de una
 * zona de camara fija (como en los primeros Resident Evil). Mas el temblor.
 */
public final class Cine {
    public record View(double x, double y, double z, float yaw, float pitch, float fov, float roll) {
    }

    record Zone(String name, String dim, AABB box, Vec3 cam, float yaw, float pitch, float fov, boolean follow) {
    }

    static final List<Zone> ZONES = new ArrayList<>();
    /** La toma del ultimo cuadro (para el zoom, la inclinacion y esconder la mano). */
    public static View last;
    static Zone zone;
    private static float zoneYaw, zonePitch;
    private static boolean zoneFresh;

    private static long shakeStart;
    private static float shakeStrength;
    private static float shakeSeconds;

    private Cine() {
    }

    static void reset() {
        shakeStrength = 0;
    }

    static void shake(float strength, float seconds) {
        shakeStart = Util.getMillis();
        shakeStrength = strength;
        shakeSeconds = Math.max(0.05F, seconds);
    }

    /** Cuanto tiembla ahora (0 = nada). */
    static float shakeNow() {
        if (shakeStrength <= 0) {
            return 0;
        }
        float t = (Util.getMillis() - shakeStart) / 1000F / shakeSeconds;
        if (t >= 1) {
            shakeStrength = 0;
            return 0;
        }
        return shakeStrength * (1 - t) * (1 - t);
    }

    static float[] shakeOffsets() {
        float s = shakeNow();
        if (s <= 0) {
            return null;
        }
        float t = Util.getMillis() / 1000F;
        return new float[]{Mth.sin(t * 47) * s * 0.9F + Mth.sin(t * 83) * s * 0.4F, Mth.sin(t * 59 + 1) * s * 0.8F, Mth.sin(t * 71 + 2) * s * 1.2F};
    }

    static void setZones(String json) {
        ZONES.clear();
        try {
            for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                var b = o.getAsJsonArray("box");
                ZONES.add(new Zone(Geo.str(o, "name", ""), Geo.str(o, "dim", ""),
                        new AABB(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(), b.get(3).getAsDouble(), b.get(4).getAsDouble(), b.get(5).getAsDouble()),
                        Geo.vec(o, "cam", Vec3.ZERO), Geo.num(o, "yaw", 0), Geo.num(o, "pitch", 0), Geo.num(o, "fov", 60), Geo.bool(o, "follow", true)));
            }
        } catch (RuntimeException ignored) {
            ZONES.clear();
        }
    }

    /** Cada tick: en que zona de camara fija esta el jugador. */
    static void tick(Minecraft mc) {
        Zone found = null;
        LocalPlayer p = mc.player;
        if (p != null && mc.level != null && !ZONES.isEmpty() && !p.isSpectator()) {
            String dim = mc.level.dimension().location().toString();
            Vec3 at = p.position().add(0, 0.5, 0);
            // un poco de margen para no parpadear en el borde
            for (Zone z : ZONES) {
                if (z.dim.equals(dim) && (z == zone ? z.box.inflate(0.4) : z.box).contains(at)) {
                    found = z;
                    break;
                }
            }
        }
        if (found != zone) {
            zone = found;
            zoneFresh = true;
        }
    }

    static boolean inZone() {
        return zone != null && ClientScene.ACTIVE.stream().noneMatch(s -> !s.camera.isEmpty());
    }

    /** Hacia donde mira la camara de la zona en el plano (para mover al jugador segun la pantalla). */
    static float zoneYaw() {
        return zoneYaw;
    }

    /** La toma de este cuadro, o null si la camara es la normal del juego. Lo llama el mixin de la camara. */
    public static View frame(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        View v = null;
        if (mc.player != null && mc.level != null) {
            for (int i = ClientScene.ACTIVE.size() - 1; i >= 0 && v == null; i--) {
                v = ClientScene.ACTIVE.get(i).view();
            }
            if (v == null) {
                v = PlayerFx.selfView(mc.player, partialTick);
            }
            if (v == null && zone != null) {
                v = zoneView(mc.player, partialTick);
            }
        }
        if (v != null) {
            float[] s = shakeOffsets();
            if (s != null) {
                v = new View(v.x + s[0] * 0.05, v.y + s[1] * 0.05, v.z, v.yaw + s[0] * 0.6F, v.pitch + s[1] * 0.6F, v.fov, v.roll + s[2] * 0.5F);
            }
        }
        last = v;
        return v;
    }

    private static View zoneView(LocalPlayer p, float pt) {
        Zone z = zone;
        float yaw = z.yaw, pitch = z.pitch;
        if (z.follow) {
            Vec3 target = p.getPosition(pt).add(0, p.getBbHeight() * 0.6, 0);
            Vec3 d = target.subtract(z.cam);
            double h = Math.sqrt(d.x * d.x + d.z * d.z);
            float ty = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
            float tp = (float) -(Mth.atan2(d.y, h) * Mth.RAD_TO_DEG);
            if (zoneFresh) {
                zoneYaw = ty;
                zonePitch = tp;
                zoneFresh = false;
            } else {
                // sigue al jugador con suavidad
                zoneYaw += Mth.wrapDegrees(ty - zoneYaw) * 0.12F;
                zonePitch += (tp - zonePitch) * 0.12F;
            }
            yaw = zoneYaw;
            pitch = zonePitch;
        } else {
            zoneYaw = yaw;
            zonePitch = pitch;
            zoneFresh = false;
        }
        return new View(z.cam.x, z.cam.y, z.cam.z, yaw, pitch, z.fov, 0);
    }
}
