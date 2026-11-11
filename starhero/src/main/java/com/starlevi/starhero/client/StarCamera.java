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
 * Camara de concierto. Cada toma es un movimiento de camara (no una camara quieta):
 * <ul>
 *   <li>entrada: grua que baja desde arriba del publico hasta la banda (cuenta regresiva);</li>
 *   <li>vuelo sobre el publico hacia el escenario, travelling de frente y de costado, grua desde atras de la
 *       banda mostrando al publico, giro alrededor del escenario, vista de arriba girando;</li>
 *   <li>primeros planos en arco alrededor del musico que esta tocando, contrapicado que se acerca, detalle de
 *       las manos, paneo rapido de un musico a otro, y alejarse desde un musico hasta ver toda la banda;</li>
 *   <li>final: la camara se abre y sube mientras la banda saluda.</li>
 * </ul>
 * Los cortes caen en el beat (cada 8 tiempos; cada 4 con el publico encendido), con un golpe de zoom en el
 * corte, un pulso en cada tiempo, un poco de camara en mano e inclinacion en los planos cortos.
 */
public final class StarCamera {
    public record View(double x, double y, double z, float yaw, float pitch, float fov, float roll) {
    }

    private enum Shot {
        WIDE_DOLLY(true), FLYOVER(true), ORBIT(true), BEHIND(true), TOP(true), SIDE_TRACK(true), PULL_BACK(true),
        CLOSE_ARC(false), LOW_PUSH(false), DETAIL(false), WHIP(false);

        final boolean wide;

        Shot(boolean wide) {
            this.wide = wide;
        }
    }

    public static boolean enabled = true;

    private static final Random RANDOM = new Random();
    private static Shot shot = Shot.WIDE_DOLLY;
    private static Shot lastShot;
    private static int member;
    private static int member2;
    private static double shotStart = -1000;
    private static double shotLen = 8;
    private static int sign = 1;
    private static double variant;
    static long cutAt;

    /** La ultima vista calculada (la usan el FOV y el giro, que se piden despues en el mismo cuadro). */
    public static View last;

    private StarCamera() {
    }

    static void reset() {
        shot = Shot.WIDE_DOLLY;
        lastShot = null;
        shotStart = -1000;
        shotLen = 8;
        member = 0;
        cutAt = 0;
    }

    public static boolean active() {
        return enabled && GigClient.active() && GigClient.cameraOwner && Minecraft.getInstance().player != null;
    }

    /** Destello de corte para el HUD (1 recien cortado, 0 despues). */
    public static float cutFlash() {
        return active() ? 1 - Mth.clamp((Util.getMillis() - cutAt) / 140F, 0, 1) : 0;
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

    /** Prefiere al que mas esta tocando ahora. */
    private static int pickMember(List<Entity> members, long now) {
        if (members.isEmpty()) {
            return 0;
        }
        if (RANDOM.nextFloat() < 0.6F) {
            int best = 0;
            long bestT = Long.MIN_VALUE;
            for (int i = 0; i < members.size(); i++) {
                GigClient.Performer p = GigClient.PERFORMERS.get(members.get(i).getId());
                long t = p == null ? 0 : p.lastHit + RANDOM.nextInt(400);
                if (t > bestT && i != member) {
                    bestT = t;
                    best = i;
                }
            }
            return best;
        }
        return RANDOM.nextInt(members.size());
    }

    private static void nextShot(double beat, List<Entity> members, long now) {
        float e = GigClient.hype();
        Shot[] pool = e > 0.7F
                ? new Shot[]{Shot.CLOSE_ARC, Shot.CLOSE_ARC, Shot.LOW_PUSH, Shot.LOW_PUSH, Shot.DETAIL, Shot.WHIP, Shot.FLYOVER, Shot.ORBIT, Shot.BEHIND, Shot.PULL_BACK}
                : new Shot[]{Shot.WIDE_DOLLY, Shot.FLYOVER, Shot.ORBIT, Shot.BEHIND, Shot.TOP, Shot.SIDE_TRACK, Shot.PULL_BACK,
                Shot.CLOSE_ARC, Shot.CLOSE_ARC, Shot.LOW_PUSH, Shot.DETAIL, Shot.WHIP};
        Shot pick;
        int guard = 0;
        do {
            pick = pool[RANDOM.nextInt(pool.length)];
            // no dos planos generales seguidos, ni repetir la toma
        } while ((pick == lastShot || (lastShot != null && pick.wide && lastShot.wide)) && ++guard < 12);
        if (members.size() < 2 && pick == Shot.WHIP) {
            pick = Shot.CLOSE_ARC;
        }
        lastShot = pick;
        shot = pick;
        member = pickMember(members, now);
        member2 = members.size() < 2 ? member : (member + 1 + RANDOM.nextInt(members.size() - 1)) % members.size();
        sign = RANDOM.nextBoolean() ? 1 : -1;
        variant = RANDOM.nextDouble();
        shotStart = Math.floor(beat);
        double base = e > 0.72F ? 4 : 8;
        shotLen = pick.wide ? Math.max(8, base * 1.5) : base;
        if (pick == Shot.WHIP) {
            shotLen = 4;
        }
        cutAt = now;
    }

    private static double smooth(double p) {
        return p * p * (3 - 2 * p);
    }

    private static double easeOut(double p) {
        return 1 - (1 - p) * (1 - p) * (1 - p);
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
        float e = GigClient.hype();

        Vec3 pos;
        Vec3 target;
        float fov;
        float roll = 0;
        boolean close = false;

        if (GigClient.finale()) {
            // final: se abre y sube, con la banda saludando
            double p = easeOut(Mth.clamp((now - GigClient.endingAt) / 5200.0, 0, 1));
            pos = c.add(f.scale(4.5 + 8.0 * p)).add(s.scale(1.5 - 3.0 * p)).add(up.scale(1.6 + 4.6 * p));
            target = c.add(up.scale(1.2 + 0.6 * p));
            fov = 62 + 10 * (float) p;
        } else if (beat < 0) {
            // entrada: grua que baja desde arriba del publico
            double p = smooth(Mth.clamp(1 + beat / 6.0, 0, 1));
            pos = c.add(f.scale(13.0 - 5.5 * p)).add(s.scale(3.0 - 3.0 * p)).add(up.scale(9.0 - 6.8 * p));
            target = c.add(up.scale(1.1));
            fov = 70 - 6 * (float) p;
            shot = Shot.WIDE_DOLLY;
            shotStart = -1000;
        } else {
            if (beat >= shotStart + shotLen || beat < shotStart) {
                nextShot(beat, members, now);
            }
            double lin = Mth.clamp((beat - shotStart) / shotLen, 0, 1);
            double p = smooth(lin);
            Entity m = members.get(Math.min(member, members.size() - 1));
            Vec3 mp = m.getPosition(partialTick);
            switch (shot) {
                case FLYOVER -> {
                    // vuela sobre el publico hacia la banda
                    double q = easeOut(lin);
                    pos = c.add(f.scale(15.0 - 10.5 * q)).add(s.scale(sign * (2.5 - 2.5 * q))).add(up.scale(3.6 - 1.7 * q));
                    target = c.add(up.scale(1.2));
                    fov = 76 - 10 * (float) q;
                }
                case ORBIT -> {
                    double a = (-1.1 + 2.2 * p) * sign;
                    Vec3 dir = f.scale(Math.cos(a)).add(s.scale(Math.sin(a)));
                    pos = c.add(dir.scale(6.4 - 0.8 * p)).add(up.scale(2.9 - 1.3 * p));
                    target = c.add(up.scale(1.1));
                    fov = 64;
                    roll = (float) (Math.sin(a) * 5);
                }
                case BEHIND -> {
                    // desde atras de la banda, subiendo: se ve al publico
                    pos = c.subtract(f.scale(2.6 + 0.6 * p)).add(s.scale(sign * (2.0 - 2.6 * p))).add(up.scale(1.6 + 2.4 * p));
                    target = c.add(f.scale(9)).add(up.scale(1.0 - 0.6 * p));
                    fov = 76;
                }
                case TOP -> {
                    double a = sign * (0.4 + 1.2 * p);
                    Vec3 dir = f.scale(Math.cos(a)).add(s.scale(Math.sin(a)));
                    pos = c.add(dir.scale(2.6)).add(up.scale(8.0 - 2.0 * p));
                    target = c.add(up.scale(0.4));
                    fov = 62;
                    roll = (float) (sign * (10 - 20 * p));
                }
                case SIDE_TRACK -> {
                    // de perfil, recorriendo la fila de la banda
                    pos = c.add(s.scale(sign * (7.5 - 4.0 * p))).add(f.scale(0.6 + 0.6 * p)).add(up.scale(1.55));
                    target = c.subtract(s.scale(sign * 2.5)).add(up.scale(1.15));
                    fov = 48;
                }
                case PULL_BACK -> {
                    // arranca pegado a un musico y se aleja hasta ver a toda la banda
                    double q = easeOut(lin);
                    Vec3 from = mp.add(f.scale(1.5)).add(up.scale(1.4));
                    Vec3 to = c.add(f.scale(8.5)).add(s.scale(sign * 1.5)).add(up.scale(3.0));
                    pos = from.lerp(to, q);
                    target = mp.add(up.scale(1.2)).lerp(c.add(up.scale(1.1)), q);
                    fov = 50 + 18 * (float) q;
                }
                case CLOSE_ARC -> {
                    // arco alrededor del musico
                    double a = sign * (-0.75 + 1.5 * p);
                    Vec3 dir = f.scale(Math.cos(a)).add(s.scale(Math.sin(a)));
                    pos = mp.add(dir.scale(2.1 - 0.5 * p)).add(up.scale(1.45 - 0.1 * p));
                    target = mp.add(up.scale(1.15));
                    fov = 50;
                    roll = (float) (sign * (7 - 14 * p));
                    close = true;
                }
                case LOW_PUSH -> {
                    // contrapicado que se acerca
                    double q = easeOut(lin);
                    pos = mp.add(f.scale(3.2 - 1.6 * q)).add(s.scale(sign * (0.9 - 0.5 * q))).add(up.scale(0.25));
                    target = mp.add(up.scale(1.5));
                    fov = 66 - 6 * (float) q;
                    roll = sign * 4;
                    close = true;
                }
                case DETAIL -> {
                    // las manos y el instrumento
                    pos = mp.add(f.scale(1.25 - 0.2 * p)).add(s.scale(sign * (-0.8 + 0.5 * p))).add(up.scale(1.0 + 0.15 * p));
                    target = mp.add(up.scale(0.85)).add(s.scale(sign * 0.2));
                    fov = 42;
                    roll = sign * -5;
                    close = true;
                }
                case WHIP -> {
                    // paneo rapido de un musico a otro
                    Entity m2 = members.get(Math.min(member2, members.size() - 1));
                    Vec3 mp2 = m2.getPosition(partialTick);
                    double q = smooth(Mth.clamp((lin - 0.42) / 0.16, 0, 1));
                    Vec3 mid = mp.lerp(mp2, 0.5);
                    pos = mid.add(f.scale(3.4)).add(up.scale(1.5));
                    target = mp.lerp(mp2, q).add(up.scale(1.2));
                    fov = 46;
                    close = true;
                }
                default -> {
                    // travelling de frente con algo de grua
                    pos = c.add(f.scale(8.5 - 1.5 * p)).add(s.scale(sign * (-3.5 + 7.0 * p))).add(up.scale(1.9 + 1.2 * variant + 0.8 * p));
                    target = c.add(up.scale(1.15));
                    fov = 66;
                }
            }
        }

        // camara en mano: un poco de vida, mas en los planos cortos
        double t = now / 1000.0;
        double amp = close ? 0.035 : 0.02;
        pos = pos.add(Math.sin(t * 1.7) * amp + Math.sin(t * 3.1) * amp * 0.4, Math.sin(t * 2.3 + 1) * amp, Math.cos(t * 1.9 + 2) * amp);

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

        // pulso en cada tiempo, golpe de zoom en el corte y un temblor chiquito con el bombo
        double ph = beat - Math.floor(beat);
        float pulse = beat >= 0 ? (float) Math.exp(-ph * 6) : 0;
        float cut = 1 - Mth.clamp((now - cutAt) / 320F, 0, 1);
        fov *= 1 - 0.04F * pulse * (0.5F + e);
        fov += 9F * cut * cut;
        pitch -= 0.5F * pulse * (0.3F + e);
        yaw += (float) (Math.sin(t * 2.9) * (close ? 0.35 : 0.15));
        roll += (float) (Math.sin(beat * Math.PI / 4) * (close ? 1.8 : 0.8));
        return new View(pos.x, pos.y, pos.z, yaw, pitch, fov, roll);
    }

    /** Lo llama el mixin de la camara en cada cuadro. */
    public static View frame(float partialTick) {
        last = view(partialTick);
        return last;
    }
}
