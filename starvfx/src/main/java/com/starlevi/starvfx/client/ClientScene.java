package com.starlevi.starvfx.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starlevi.starvfx.Geo;
import com.starlevi.starvfx.StarVfx;
import com.starlevi.starvfx.net.Net;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Una escena corriendo en este cliente: su linea de tiempo (efectos, fundidos, textos, sonidos, temblores) y su
 * camara. Cada cliente lleva su propio reloj desde que el servidor avisa, asi va fluido aunque haya lag.
 */
public final class ClientScene {
    /** Una toma de camara en la linea de tiempo. */
    record Key(float t, Vec3 pos, Vec3 look, boolean angles, float yaw, float pitch, float fov, float roll, boolean cut,
               String ease, String attach, String follow, Vec3 followOffset, boolean release) {
    }

    static final List<ClientScene> ACTIVE = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static ClientLevel lastLevel;

    int id;
    Vec3 origin;
    float yaw;
    long startMs;
    float duration;
    boolean lock;
    boolean hideHud;
    boolean letterbox;
    final List<JsonObject> events = new ArrayList<>();
    int fired;
    final List<Key> camera = new ArrayList<>();
    final List<Fx.Effect> effects = new ArrayList<>();
    final Map<String, Fx.Effect> named = new HashMap<>();

    float time() {
        return (Util.getMillis() - startMs) / 1000F;
    }

    Vec3 world(Vec3 local) {
        return Geo.toWorld(origin, yaw, local.x, local.y, local.z);
    }

    static void play(Net.PlayScene msg) {
        try {
            JsonObject json = JsonParser.parseString(msg.json()).getAsJsonObject();
            ClientScene s = new ClientScene();
            s.id = msg.id();
            s.origin = new Vec3(msg.x(), msg.y(), msg.z());
            s.yaw = msg.yaw();
            s.startMs = Util.getMillis() - (long) (msg.elapsed() * 1000);
            s.duration = Math.max(0.5F, Geo.num(json, "duration", 10));
            s.lock = Geo.bool(json, "lock", false);
            s.hideHud = Geo.bool(json, "hide_hud", false);
            s.letterbox = Geo.bool(json, "letterbox", false);
            if (json.has("events") && json.get("events").isJsonArray()) {
                for (JsonElement e : json.getAsJsonArray("events")) {
                    if (e.isJsonObject()) {
                        s.events.add(e.getAsJsonObject());
                    }
                }
            }
            s.events.sort(Comparator.comparingDouble(e -> Geo.num(e, "t", 0)));
            if (json.has("camera") && json.get("camera").isJsonArray()) {
                for (JsonElement e : json.getAsJsonArray("camera")) {
                    if (!e.isJsonObject()) {
                        continue;
                    }
                    JsonObject k = e.getAsJsonObject();
                    s.camera.add(new Key(Geo.num(k, "t", 0), Geo.vec(k, "pos", Vec3.ZERO), Geo.vec(k, "look", null),
                            k.has("yaw") || k.has("pitch"), Geo.num(k, "yaw", 0), Geo.num(k, "pitch", 0), Geo.num(k, "fov", 70),
                            Geo.num(k, "roll", 0), Geo.bool(k, "cut", false), Geo.str(k, "ease", "smooth"), Geo.str(k, "attach", null),
                            Geo.str(k, "follow", null), Geo.vec(k, "follow_offset", Vec3.ZERO), Geo.bool(k, "release", false)));
                }
                s.camera.sort(Comparator.comparingDouble(Key::t));
            }
            // una escena con camara reemplaza a la anterior con camara
            if (s.id != 0) {
                ACTIVE.removeIf(o -> o.id == s.id || (!s.camera.isEmpty() && !o.camera.isEmpty()));
            }
            ACTIVE.add(s);
        } catch (RuntimeException e) {
            StarVfx.LOGGER.warn("[Star VFX] Escena mal formada", e);
        }
    }

    static void stop(int id) {
        ACTIVE.removeIf(s -> id < 0 || s.id == id);
        if (ACTIVE.isEmpty()) {
            Hud.reset();
            Cine.reset();
        }
    }

    static boolean locked() {
        for (ClientScene s : ACTIVE) {
            if (s.lock) {
                return true;
            }
        }
        return false;
    }

    static boolean hudHidden() {
        for (ClientScene s : ACTIVE) {
            if (s.hideHud) {
                return true;
            }
        }
        return false;
    }

    static boolean letterboxed() {
        for (ClientScene s : ACTIVE) {
            if (s.letterbox && s.time() < s.duration - 1.2F) {
                return true;
            }
        }
        return false;
    }

    static void tickAll(Minecraft mc) {
        if (mc.level != lastLevel) {
            // otra dimension u otro mundo: lo que estaba corriendo ya no vale
            lastLevel = mc.level;
            ACTIVE.clear();
            Hud.reset();
            Cine.reset();
            if (mc.level == null) {
                Fx.WORLD.clear();
                PlayerFx.clear();
                Cine.ZONES.clear();
            }
        }
        if (mc.level == null) {
            return;
        }
        for (Iterator<ClientScene> it = ACTIVE.iterator(); it.hasNext(); ) {
            ClientScene s = it.next();
            float t = s.time();
            while (s.fired < s.events.size() && Geo.num(s.events.get(s.fired), "t", 0) <= t) {
                try {
                    s.fire(mc, s.events.get(s.fired), t);
                } catch (RuntimeException e) {
                    StarVfx.LOGGER.warn("[Star VFX] Fallo un evento de la escena", e);
                }
                s.fired++;
            }
            if (t >= s.duration) {
                it.remove();
            }
        }
        Fx.tickAll(mc.level);
    }

    private void fire(Minecraft mc, JsonObject e, float now) {
        float t = Geo.num(e, "t", 0);
        float late = now - t;
        String type = Geo.str(e, "type", "");
        switch (type) {
            case "effect" -> {
                Vec3 at = world(Geo.vec(e, "pos", Vec3.ZERO));
                Fx.Effect fx = Fx.create(Geo.str(e, "effect", ""), at, e, late);
                if (fx != null && !fx.done()) {
                    effects.add(fx);
                    String name = Geo.str(e, "id", null);
                    if (name != null) {
                        named.put(name, fx);
                    }
                }
            }
            case "fade" -> {
                if (late < 3) {
                    Hud.fade(Geo.color(e, "color", 0x000000), Geo.num(e, "in", 1), Geo.num(e, "hold", 0.5F), Geo.num(e, "out", 1), late);
                }
            }
            case "flash" -> {
                if (late < 1) {
                    Hud.fade(Geo.color(e, "color", 0xFFFFFF), Geo.num(e, "in", 0.05F), Geo.num(e, "hold", 0.05F), Geo.num(e, "out", 0.8F), late);
                }
            }
            case "shake" -> {
                if (late < 1) {
                    Cine.shake(Geo.num(e, "strength", 1), Geo.num(e, "duration", 1.5F));
                }
            }
            case "text" -> {
                if (late < 2) {
                    Hud.text(Geo.str(e, "text", ""), Geo.str(e, "sub", ""), Geo.num(e, "duration", 4), Geo.color(e, "color", 0xFFFFFF));
                }
            }
            case "sound" -> {
                if (late < 1) {
                    ResourceLocation id = ResourceLocation.tryParse(Geo.str(e, "sound", ""));
                    if (id != null) {
                        SoundEvent sound = SoundEvent.createVariableRangeEvent(id);
                        float volume = Geo.num(e, "volume", 1), pitch = Geo.num(e, "pitch", 1);
                        if (e.has("pos")) {
                            Vec3 at = world(Geo.vec(e, "pos", Vec3.ZERO));
                            mc.level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.AMBIENT, volume, pitch, false);
                        } else {
                            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
                        }
                    }
                }
            }
            case "particles" -> {
                if (late < 1) {
                    ResourceLocation id = ResourceLocation.tryParse(Geo.str(e, "particle", "minecraft:end_rod"));
                    ParticleType<?> pt = id == null ? null : ForgeRegistries.PARTICLE_TYPES.getValue(id);
                    if (pt instanceof SimpleParticleType simple) {
                        Vec3 at = world(Geo.vec(e, "pos", Vec3.ZERO));
                        int count = Mth.clamp((int) Geo.num(e, "count", 40), 1, 600);
                        float spread = Geo.num(e, "spread", 1), speed = Geo.num(e, "speed", 0.1F);
                        for (int i = 0; i < count; i++) {
                            mc.level.addParticle(simple, true, at.x + RANDOM.nextGaussian() * spread, at.y + RANDOM.nextGaussian() * spread * 0.5,
                                    at.z + RANDOM.nextGaussian() * spread, RANDOM.nextGaussian() * speed, Math.abs(RANDOM.nextGaussian()) * speed,
                                    RANDOM.nextGaussian() * speed);
                        }
                    }
                }
            }
            default -> {
                // command y sprout los maneja el servidor
            }
        }
    }

    // ================================================================ camara

    private Vec3 anchor(Key k, float now) {
        if (k.attach != null) {
            Fx.Effect fx = named.get(k.attach);
            if (fx != null) {
                return Geo.toWorld(fx.center(fx.age()), yaw, k.pos.x, k.pos.y, k.pos.z);
            }
        }
        return world(k.pos);
    }

    private Vec3 target(Key k, Vec3 camPos) {
        if (k.follow != null) {
            Fx.Effect fx = named.get(k.follow);
            if (fx != null) {
                return fx.position(fx.age()).add(k.followOffset);
            }
        }
        if (k.look != null) {
            return world(k.look);
        }
        float yawRad = (k.yaw + yaw) * Mth.DEG_TO_RAD, pitchRad = k.pitch * Mth.DEG_TO_RAD;
        float cp = Mth.cos(pitchRad);
        return camPos.add(-Mth.sin(yawRad) * cp * 30, -Mth.sin(pitchRad) * 30, Mth.cos(yawRad) * cp * 30);
    }

    /** La toma de este instante, o null si la escena no esta manejando la camara ahora. */
    Cine.View view() {
        if (camera.isEmpty()) {
            return null;
        }
        float now = time();
        int i = -1;
        for (int k = 0; k < camera.size(); k++) {
            if (camera.get(k).t <= now) {
                i = k;
            }
        }
        if (i < 0 || camera.get(i).release) {
            return null;
        }
        Key a = camera.get(i);
        Vec3 pos = anchor(a, now);
        Vec3 look = target(a, pos);
        float fov = a.fov, roll = a.roll;
        if (i + 1 < camera.size() && !camera.get(i + 1).cut && !camera.get(i + 1).release) {
            Key b = camera.get(i + 1);
            float u = Mth.clamp((now - a.t) / Math.max(0.001F, b.t - a.t), 0, 1);
            u = switch (a.ease) {
                case "linear" -> u;
                case "in" -> u * u;
                case "out" -> 1 - (1 - u) * (1 - u);
                default -> u * u * (3 - 2 * u);
            };
            Vec3 posB = anchor(b, now);
            Vec3 lookB = target(b, posB);
            pos = pos.lerp(posB, u);
            look = look.lerp(lookB, u);
            fov = Mth.lerp(u, a.fov, b.fov);
            roll = Mth.lerp(u, a.roll, b.roll);
        }
        Vec3 d = look.subtract(pos);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yawOut = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float pitchOut = (float) -(Mth.atan2(d.y, h) * Mth.RAD_TO_DEG);
        return new Cine.View(pos.x, pos.y, pos.z, yawOut, pitchOut, fov, roll);
    }
}
