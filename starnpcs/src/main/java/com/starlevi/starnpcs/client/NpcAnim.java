package com.starlevi.starnpcs.client;

import com.starlevi.starnpcs.NpcEntity;
import com.starlevi.starnpcs.Profile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.ForgeRegistries;

/** Estado de animacion de un NPC en este cliente. Todo sale del reloj del mundo, asi todos ven lo mismo a la vez. */
final class NpcAnim {
    static final int STRIDE = 9;
    private static final float BLEND_TICKS = 5F;

    /** animacion de una vez en curso */
    String once;
    double onceStart;
    private double pausedAt = Double.NaN;

    String bubble;
    long bubbleUntil;

    private String key = "";
    private float[] last;
    private float[] from;
    private double blendStart;
    private float[] pose = new float[0];
    boolean[] visible = new boolean[0];

    private String fxKey = "";
    private float fxTime;

    Anim anim;
    float time;
    private String selKey = "";

    static NpcAnim of(NpcEntity e) {
        if (!(e.clientState instanceof NpcAnim)) {
            e.clientState = new NpcAnim();
        }
        return (NpcAnim) e.clientState;
    }

    private static boolean has(BbModel m, String name) {
        return name != null && !name.isEmpty() && m.anims.containsKey(name);
    }

    /** Decide que animacion suena ahora y en que segundo va. Deja el resultado en anim, time y selKey. */
    private void select(NpcEntity e, BbModel m, double now) {
        Profile p = e.profile();
        if (once != null) {
            Anim a = m.anims.get(once);
            double t = (now - onceStart) / 20.0;
            if (a != null && t >= -0.05 && t < a.length) {
                anim = a;
                time = (float) Math.max(0, t);
                selKey = "once:" + once;
                return;
            }
            once = null;
        }
        boolean talking = e.talkId() != 0;
        boolean moving = e.walkAnimation.speed() > 0.08F;
        String name = moving && has(m, p.walk) ? p.walk : talking && has(m, p.talk) ? p.talk : p.idle;
        Anim a = m.anims.get(name);
        if (a == null) {
            a = m.anims.get("idle");
        }
        anim = a;
        if (a == null) {
            selKey = "";
            time = 0;
            return;
        }
        double t = (now - p.epoch) / 20.0;
        boolean pause = talking && p.stopOnTalk && !moving && !has(m, p.talk);
        if (pause) {
            if (Double.isNaN(pausedAt)) {
                pausedAt = t;
            }
            t = pausedAt;
        } else {
            pausedAt = Double.NaN;
        }
        time = a.wrap(t);
        selKey = a.name + (pause ? "|pausa" : "");
    }

    /** Pose de todos los huesos (9 valores por hueso: rotacion, posicion y escala, ya en el espacio del dibujo). */
    float[] pose(NpcEntity e, BbModel m, double now) {
        int n = m.all.size();
        if (pose.length != n * STRIDE) {
            pose = new float[n * STRIDE];
            visible = new boolean[n];
            last = null;
            from = null;
        }
        select(e, m, now);
        float[] tmp = new float[3];
        for (int i = 0; i < n; i++) {
            BbModel.Bone b = m.all.get(i);
            int o = i * STRIDE;
            for (int c = 0; c < 6; c++) {
                pose[o + c] = 0;
            }
            pose[o + 6] = pose[o + 7] = pose[o + 8] = 1;
            Anim.Channel[] ch = anim == null ? null : anim.bones.get(b.name);
            visible[i] = !b.prop || ch != null;
            if (ch == null) {
                continue;
            }
            if (ch[Anim.ROT].t.length > 0) {
                ch[Anim.ROT].sample(time, tmp, 0);
                pose[o] = -tmp[0] * Mth.DEG_TO_RAD;
                pose[o + 1] = -tmp[1] * Mth.DEG_TO_RAD;
                pose[o + 2] = tmp[2] * Mth.DEG_TO_RAD;
            }
            if (ch[Anim.POS].t.length > 0) {
                ch[Anim.POS].sample(time, tmp, 0);
                pose[o + 3] = -tmp[0];
                pose[o + 4] = tmp[1];
                pose[o + 5] = tmp[2];
            }
            if (ch[Anim.SCALE].t.length > 0) {
                ch[Anim.SCALE].sample(time, pose, o + 6);
            }
        }
        if (!selKey.equals(key)) {
            key = selKey;
            from = last == null ? null : last.clone();
            blendStart = now;
        }
        if (from != null && from.length == pose.length) {
            float w = (float) ((now - blendStart) / BLEND_TICKS);
            if (w >= 1 || w < 0) {
                from = null;
            } else {
                w = w * w * (3 - 2 * w);
                for (int i = 0; i < pose.length; i++) {
                    pose[i] = from[i] + (pose[i] - from[i]) * w;
                }
            }
        }
        if (last == null || last.length != pose.length) {
            last = new float[pose.length];
        }
        System.arraycopy(pose, 0, last, 0, pose.length);
        return pose;
    }

    /** Una vez por tick: sonidos y particulas que caen entre el tick anterior y este. */
    void tickFx(NpcEntity e, BbModel m, ClientLevel level) {
        select(e, m, level.getGameTime());
        if (anim == null || anim.fx.isEmpty()) {
            fxKey = selKey;
            fxTime = time;
            return;
        }
        if (selKey.equals(fxKey) && time != fxTime) {
            boolean wrapped = time < fxTime;
            for (Anim.Fx f : anim.fx) {
                boolean hit = wrapped ? f.time > fxTime || f.time <= time : f.time > fxTime && f.time <= time;
                if (hit) {
                    fire(e, f, level);
                }
            }
        } else if (!selKey.equals(fxKey)) {
            for (Anim.Fx f : anim.fx) {
                if (f.time <= time && f.time >= time - 0.06F) {
                    fire(e, f, level);
                }
            }
        }
        fxKey = selKey;
        fxTime = time;
    }

    private static void fire(NpcEntity e, Anim.Fx f, ClientLevel level) {
        ResourceLocation id = ResourceLocation.tryParse(f.id);
        if (id == null) {
            return;
        }
        if (!f.particle) {
            level.playLocalSound(e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                    SoundEvent.createVariableRangeEvent(id), SoundSource.NEUTRAL, f.a, f.b, false);
            return;
        }
        ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(id);
        if (!(type instanceof SimpleParticleType simple)) {
            return;
        }
        // de pixeles del modelo (mira al norte) a mundo
        float s = e.profile().scale * 0.9375F / 16F;
        float rad = (180F - e.yBodyRot) * Mth.DEG_TO_RAD;
        double x = f.x * s, z = f.z * s;
        double wx = x * Mth.cos(rad) + z * Mth.sin(rad);
        double wz = -x * Mth.sin(rad) + z * Mth.cos(rad);
        for (int i = 0; i < f.count; i++) {
            level.addParticle(simple, e.getX() + wx, e.getY() + f.y * s, e.getZ() + wz,
                    (level.random.nextDouble() - 0.5) * 0.25, level.random.nextDouble() * 0.2, (level.random.nextDouble() - 0.5) * 0.25);
        }
    }
}
