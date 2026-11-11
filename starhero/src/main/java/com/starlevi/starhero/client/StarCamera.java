package com.starlevi.starhero.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Camara de concierto: muestra a la banda con tomas que cambian al compas (cada 8 tiempos, o cada 4 cuando el
 * publico esta encendido): plano general, primeros planos de cada musico, contrapicado, travelling lateral, el
 * publico desde atras de la banda, un giro alrededor y vista de arriba. Dentro de cada toma la camara se mueve
 * suave; los cortes caen justo en el beat. En cada golpe de bombo hace un pequeno "zoom" (pulso).
 */
public final class StarCamera {
    public record View(double x, double y, double z, float yaw, float pitch, float fov, float roll) {
    }

    private enum Shot { WIDE, CLOSE, LOW, SIDE, CROWD, ORBIT, TOP, DETAIL }

    public static boolean enabled = true;

    private static final Random RANDOM = new Random();
    private static Shot shot = Shot.WIDE;
    private static int member;
    private static double shotStart;
    private static double shotLen = 8;
    private static int sideSign = 1;
    private static Shot lastShot;

    private StarCamera() {
    }

    static void reset() {
        shot = Shot.WIDE;
        shotStart = -1000;
        shotLen = 8;
        member = 0;
    }

    public static boolean active() {
        return enabled && GigClient.active() && GigClient.localPerformer && Minecraft.getInstance().player != null;
    }

    private static List<Entity> members() {
        List<Entity> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return out;
        }
        for (GigClient.Performer p : GigClient.PERFORMERS.values()) {
            Entity e = mc.level.getEntity(p.entityId);
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    private static void nextShot(double beat, List<Entity> members) {
        Shot[] pool = {Shot.WIDE, Shot.CLOSE, Shot.CLOSE, Shot.LOW, Shot.SIDE, Shot.CROWD, Shot.ORBIT, Shot.TOP, Shot.DETAIL, Shot.CLOSE};
        Shot pick;
        do {
            pick = pool[RANDOM.nextInt(pool.length)];
        } while (pick == lastShot && pool.length > 1);
        lastShot = pick;
        shot = pick;
        member = members.isEmpty() ? 0 : RANDOM.nextInt(members.size());
        sideSign = RANDOM.nextBoolean() ? 1 : -1;
        shotStart = Math.floor(beat);
        shotLen = GigClient.hype() > 0.72F ? 4 : 8;
        if (shot == Shot.ORBIT || shot == Shot.WIDE) {
            shotLen *= 1.5;
        }
    }

    /** La vista de la camara para este cuadro (null si no esta activa). */
    public static View view(float partialTick) {
        if (!active()) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        double beat = GigClient.beat(now);
        List<Entity> members = members();
        if (members.isEmpty()) {
            return null;
        }
        Vec3 c = GigClient.center;
        Vec3 f = GigClient.forward();
        Vec3 s = GigClient.side();
        Vec3 up = new Vec3(0, 1, 0);

        double p;
        if (beat < 0) {
            // cuenta regresiva: plano general que se acerca despacio
            shot = Shot.WIDE;
            double t = Mth.clamp(1 + beat / 8.0, 0, 1);
            p = t;
        } else {
            if (beat >= shotStart + shotLen || beat < shotStart) {
                nextShot(beat, members);
            }
            p = Mth.clamp((beat - shotStart) / shotLen, 0, 1);
        }
        double e = p * p * (3 - 2 * p);
        Entity m = members.get(Math.min(member, members.size() - 1));
        Vec3 mp = m.getPosition(partialTick);

        Vec3 pos;
        Vec3 target;
        float fov;
        switch (shot) {
            case CLOSE -> {
                double d = 2.0 - 0.5 * e;
                pos = mp.add(f.scale(d)).add(s.scale(0.75 * sideSign)).add(up.scale(1.4 - 0.15 * e));
                target = mp.add(up.scale(1.1));
                fov = 52;
            }
            case LOW -> {
                pos = mp.add(f.scale(2.3)).add(s.scale(-0.6 * sideSign + 0.8 * sideSign * e)).add(up.scale(0.35));
                target = mp.add(up.scale(1.55));
                fov = 62;
            }
            case SIDE -> {
                pos = c.add(s.scale(6.5 * sideSign)).add(f.scale(1.0 + 1.5 * e)).add(up.scale(1.7));
                target = c.add(s.scale(-1.5 * sideSign)).add(up.scale(1.0));
                fov = 58;
            }
            case CROWD -> {
                pos = c.subtract(f.scale(2.4)).add(s.scale(1.2 * sideSign)).add(up.scale(2.5 + 0.4 * e));
                target = c.add(f.scale(9)).add(up.scale(0.8));
                fov = 74;
            }
            case ORBIT -> {
                double a = (-0.9 + 1.8 * e) * sideSign;
                Vec3 dir = f.scale(Math.cos(a)).add(s.scale(Math.sin(a)));
                pos = c.add(dir.scale(5.8)).add(up.scale(2.4));
                target = c.add(up.scale(1.0));
                fov = 64;
            }
            case TOP -> {
                pos = c.add(f.scale(2.5 + 1.5 * e)).add(up.scale(7.0 - 1.5 * e));
                target = c.add(up.scale(0.4));
                fov = 60;
            }
            case DETAIL -> {
                pos = mp.add(f.scale(1.0)).add(s.scale(-0.7 * sideSign)).add(up.scale(0.95 + 0.2 * e));
                target = mp.add(up.scale(0.8)).add(s.scale(0.25 * sideSign));
                fov = 46;
            }
            default -> {
                pos = c.add(f.scale(9.0 - 2.0 * e)).add(s.scale((-2 + 4 * e) * (beat < 0 ? 0 : sideSign))).add(up.scale(2.3));
                target = c.add(up.scale(1.1));
                fov = 68;
            }
        }

        // que no atraviese paredes
        if (mc.level != null && mc.player != null) {
            BlockHitResult hit = mc.level.clip(new ClipContext(target, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 back = target.subtract(pos).normalize().scale(0.3);
                pos = hit.getLocation().add(back);
            }
        }

        Vec3 d = target.subtract(pos);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) (-(Mth.atan2(d.y, h) * Mth.RAD_TO_DEG));

        // pulso en cada beat
        double ph = beat - Math.floor(beat);
        float pulse = beat >= 0 ? (float) Math.exp(-ph * 7) : 0;
        fov *= 1 - 0.035F * pulse * (0.5F + GigClient.hype());
        float roll = (shot == Shot.CLOSE || shot == Shot.ORBIT) ? (float) Math.sin(beat * Math.PI / 4) * 1.6F : 0;
        return new View(pos.x, pos.y, pos.z, yaw, pitch, fov, roll);
    }

    /** La ultima vista calculada (la usan el FOV y el giro, que se piden despues en el mismo cuadro). */
    public static View last;

    /** Lo llama el mixin de la camara en cada cuadro. */
    public static View frame(float partialTick) {
        last = view(partialTick);
        return last;
    }
}
