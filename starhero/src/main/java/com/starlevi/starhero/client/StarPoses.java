package com.starlevi.starhero.client;

import net.minecraft.Util;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Las poses de la banda: cada instrumento se toca de verdad. Los brazos apuntan a donde esta el instrumento
 * (las manos van al mastil, a los parches, a las teclas, el microfono a la boca) y cada nota que se acierta se ve:
 * rasgueo, golpe, tecla. Los aldeanos tocan al ritmo de la cancion. Todo al compas: cabeceo y balanceo.
 * <p>
 * Se aplica al final de la pose normal y otra vez despues de EMF (Fresh Animations), asi queda encima.
 */
public final class StarPoses {
    private static final Map<Class<?>, java.util.Optional<Method>> SYNC = new HashMap<>();

    // ---- donde estan los instrumentos (en px del modelo: x = izquierda del personaje, y = abajo, z = atras)
    static final float GUITAR_ANGLE = 0.45F;
    static final float BASS_ANGLE = 0.38F;
    static final float[] GUITAR_AT = {-1.0F, 8.4F, -4.4F};
    static final float[] BASS_AT = {-1.0F, 9.0F, -4.4F};

    private StarPoses() {
    }

    public static void apply(LivingEntity e, HumanoidModel<?> m) {
        GigClient.Performer p = GigClient.performer(e.getId());
        if (p == null || m == null) {
            return;
        }
        long now = Util.getMillis();
        double beat = GigClient.beat(now);
        boolean playing = beat >= 0;
        float ph = (float) (beat - Math.floor(beat));
        float pulse = playing ? (float) Math.exp(-ph * 6.0) : 0;
        float energy = 0.55F + 0.45F * GigClient.hype();
        float sway = playing ? Mth.sin((float) (beat * Math.PI / 2)) : 0;

        if (!p.player && playing) {
            npcPlays(p, beat, now);
        }

        // la cabeza va al compas
        m.head.xRot = 0.06F + 0.2F * pulse * energy;
        m.head.yRot = 0.12F * Mth.sin((float) (beat * Math.PI / 4));
        m.head.zRot = 0.07F * sway;

        switch (p.instrument) {
            case GUITAR -> strings(m, p, now, GUITAR_AT, GUITAR_ANGLE, 12.5F, sway);
            case BASS -> strings(m, p, now, BASS_AT, BASS_ANGLE, 14.0F, sway);
            case DRUMS -> drums(m, p, now, pulse);
            case KEYS -> keys(m, p, now, sway);
            case VOICE -> voice(m, p, now, beat, pulse);
        }
        sync(m);
    }

    /** Los aldeanos tocan solos, al ritmo (corcheas), con un patron distinto cada uno. */
    private static void npcPlays(GigClient.Performer p, double beat, long now) {
        int sub = (int) Math.floor(beat * 2);
        if (sub == p.lastSub) {
            return;
        }
        p.lastSub = sub;
        int seed = p.entityId * 31;
        switch (p.instrument) {
            case DRUMS -> {
                if (sub % 8 == 0) {
                    p.hit(0, now);           // platillo con la izquierda
                    p.hit(3, now);           // y tom de piso
                } else {
                    p.hit(sub % 2 == 0 ? 2 : 1, now);
                }
            }
            case BASS -> {
                if (sub % 2 == 0) {
                    p.hit(Math.floorMod(sub / 2 + seed, 4), now);
                }
                p.fret = Math.floorMod(sub / 4 + seed, 4);
            }
            case KEYS -> {
                if (sub % 2 == 0 || Math.floorMod(sub + seed, 3) == 0) {
                    p.hit(Math.floorMod(sub * 3 + seed, 4), now);
                }
            }
            case VOICE -> {
                if (Math.floorMod(sub + seed, 4) != 3) {
                    p.hit(Math.floorMod(sub + seed, 4), now);
                }
            }
            default -> {
                p.hit(Math.floorMod(sub * 7 + seed, 4), now);
                p.fret = Math.floorMod(sub / 4 + seed, 4);
            }
        }
    }

    private static float since(long now, long t, float ms) {
        return t == 0 ? 1 : Mth.clamp((now - t) / ms, 0, 1);
    }

    /** Apunta el brazo (rigido, sin codo) hacia un punto del modelo. */
    static void aim(ModelPart arm, float tx, float ty, float tz) {
        float dx = tx - arm.x;
        float dy = ty - arm.y;
        float dz = tz - arm.z;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001F) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        arm.xRot = -(float) Math.acos(Mth.clamp(dy, -1, 1));
        arm.yRot = (float) Math.atan2(-dx, -dz);
        arm.zRot = 0;
    }

    // ---------------------------------------------------------------- guitarra y bajo

    private static void strings(HumanoidModel<?> m, GigClient.Performer p, long now, float[] at, float angle, float reach, float sway) {
        float c = Mth.cos(angle);
        float s = Mth.sin(angle);
        // mano izquierda en el mastil: se corre por los trastes segun la nota
        float t = reach - (3 - Math.min(3, p.fret)) * 1.3F;
        float slide = (1 - since(now, p.lastHit, 160)) * 0.4F;
        aim(m.leftArm, at[0] + c * (t + slide), at[1] - s * (t + slide) - 0.4F, at[2] - 1.4F);
        m.leftArm.zRot = -0.08F;
        // mano derecha rasguea sobre los microfonos: baja rapido al acertar y vuelve
        float k = since(now, p.lastHit, 140);
        float stroke = k < 1 ? Mth.sin(k * Mth.PI) : 0;
        float held = p.pressed != 0 ? 0.25F : 0;
        aim(m.rightArm, at[0] - c * 1.5F, at[1] + s * 1.5F - 1.4F + stroke * 3.2F + held, at[2] - 2.6F);
        m.rightArm.zRot = 0.05F;
        m.body.yRot = 0.06F * sway;
    }

    // ---------------------------------------------------------------- bateria

    // posiciones de los parches (calculadas del modelo de la bateria)
    private static final float[][] DRUM_TARGETS = {
            {11.0F, 11.8F, -8.2F},   // 0: hi-hat (izquierda)
            {6.1F, 13.6F, -7.2F},    // 1: tambor
            {-2.5F, 10.8F, -12.0F},  // 2: tom de arriba (derecha)
            {-6.5F, 15.0F, -7.0F},   // 3: tom de piso
    };

    private static void drums(HumanoidModel<?> m, GigClient.Performer p, long now, float pulse) {
        // izquierda: carriles 0 y 1; derecha: 2 y 3
        int left = p.laneHit[0] >= p.laneHit[1] ? 0 : 1;
        int right = p.laneHit[2] >= p.laneHit[3] ? 2 : 3;
        stick(m.leftArm, DRUM_TARGETS[left], since(now, p.laneHit[left], 200));
        stick(m.rightArm, DRUM_TARGETS[right], since(now, p.laneHit[right], 200));
        m.head.xRot = 0.15F + 0.3F * pulse;
        m.body.xRot = 0.08F + 0.05F * pulse;
    }

    private static void stick(ModelPart arm, float[] target, float k) {
        // en el golpe la mano esta en el parche; despues sube y queda lista para el proximo
        float lift = 4.5F * Mth.sin(Math.min(1, k) * Mth.PI * 0.5F);
        aim(arm, target[0], target[1] - lift, target[2]);
    }

    // ---------------------------------------------------------------- teclado

    private static void keys(HumanoidModel<?> m, GigClient.Performer p, long now, float sway) {
        int left = p.laneHit[0] >= p.laneHit[1] ? 0 : 1;
        int right = p.laneHit[2] >= p.laneHit[3] ? 2 : 3;
        float kl = since(now, p.laneHit[left], 130);
        float kr = since(now, p.laneHit[right], 130);
        aim(m.leftArm, 3.5F + left * 1.6F, 11.0F + (1 - kl) * 1.3F, -7.6F);
        aim(m.rightArm, -6.5F + (right - 2) * 1.6F, 11.0F + (1 - kr) * 1.3F, -7.6F);
        m.body.xRot = 0.1F;
        m.body.yRot = 0.05F * sway;
    }

    // ---------------------------------------------------------------- voz

    private static void voice(HumanoidModel<?> m, GigClient.Performer p, long now, double beat, float pulse) {
        float vib = 0.15F * Mth.sin(now / 90F);
        aim(m.rightArm, -0.6F, -2.0F + vib, -6.8F);
        float k = since(now, p.lastHit, 600);
        float wave = beat >= 0 ? Math.max(0, Mth.sin((float) (beat * Math.PI / 2))) : 0;
        m.leftArm.xRot = -0.35F - 0.5F * wave - 0.9F * (1 - k);
        m.leftArm.yRot = 0.15F;
        m.leftArm.zRot = -0.3F - 0.25F * pulse;
        m.head.xRot = -0.05F + 0.1F * pulse;
        m.body.yRot = 0.08F * Mth.sin((float) (beat * Math.PI / 2));
    }

    // ---------------------------------------------------------------- segunda capa

    /** Mangas, chaqueta y sombrero siguen a los brazos y la cabeza (y la ropa de MCA tambien). */
    private static void sync(HumanoidModel<?> m) {
        m.hat.copyFrom(m.head);
        if (m instanceof PlayerModel<?> pm) {
            pm.leftSleeve.copyFrom(pm.leftArm);
            pm.rightSleeve.copyFrom(pm.rightArm);
            pm.jacket.copyFrom(pm.body);
        }
        Method method = SYNC.computeIfAbsent(m.getClass(), c -> java.util.Optional.ofNullable(findSync(c))).orElse(null);
        if (method != null) {
            try {
                method.invoke(m);
            } catch (Throwable ignored) {
            }
        }
    }

    /** El modelo de los aldeanos de MCA (fork de Dreams) tiene su propio "syncDerivedParts". */
    private static Method findSync(Class<?> c) {
        while (c != null && c != Object.class) {
            try {
                Method method = c.getDeclaredMethod("syncDerivedParts");
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
