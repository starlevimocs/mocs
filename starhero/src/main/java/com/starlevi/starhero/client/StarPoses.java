package com.starlevi.starhero.client;

import com.starlevi.starhero.content.Instrument;
import net.minecraft.Util;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * La coreografia de la banda. Cada integrante toca su instrumento de verdad (las manos van al mastil, a los
 * parches, a las teclas, el microfono a la boca) y ademas se mueve con todo el cuerpo al compas: rebote de
 * rodillas, balanceo, headbang, inclinarse hacia atras en los solos, caminar al frente, saltos, molinete...
 * Cada 8 tiempos cambia de "movida" (distinta para cada uno), con mas intensidad cuanto mas encendido esta el
 * publico. Todo pasa por un suavizado, asi los cambios son fluidos y nunca a los saltos; los golpes y rasgueos
 * usan un suavizado mas corto para no perder el ritmo.
 * <p>
 * {@link #update} calcula el cuadro (una vez por cuadro, antes de dibujar); {@link #apply} lo pone en el modelo, al
 * final de la pose normal y otra vez despues de EMF (Fresh Animations), asi queda encima.
 */
public final class StarPoses {
    private static final Map<Class<?>, Optional<Method>> SYNC = new HashMap<>();

    // ---- donde van los instrumentos, en px del modelo (x = izquierda del personaje, y = abajo, z = atras)
    static final float GUITAR_SCALE = 0.62F;
    static final float BASS_SCALE = 0.64F;
    static final float GUITAR_ANGLE = 0.42F;
    static final float BASS_ANGLE = 0.36F;
    static final float[] GUITAR_AT = {-1.6F, 9.3F, -4.9F};
    static final float[] BASS_AT = {-1.6F, 9.9F, -4.9F};

    /** Bateria: escala y distancia; el baterista va sentado en la banqueta. */
    static final float KIT_SCALE = 1.3F;
    static final float KIT_Z = -13F;
    static final float SIT = 0.30F;                 // bloques que baja al sentarse
    static final float SIT_PX = SIT * 16F / 0.9375F;

    /** Teclado: ancho/profundidad y alto por separado (mas ancho, a la altura de la cintura). */
    static final float KEYS_SCALE = 1.18F;
    static final float KEYS_SCALE_Y = 0.8F;
    static final float KEYS_Z = -8.4F;

    private StarPoses() {
    }

    // ================================================================ utilidades

    private static float sq(float v) {
        return v * v;
    }

    private static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x & 0x7fffffff;
    }

    private static float since(long now, long t, float ms) {
        return t == 0 ? 1 : Mth.clamp((now - t) / ms, 0, 1);
    }

    private static float ease(float k) {
        return k * k * (3 - 2 * k);
    }

    /** Acerca un valor a su objetivo; tau = cuanto tarda (segundos). */
    private static float approach(float cur, float target, float dt, float tau) {
        if (dt <= 0) {
            return cur;
        }
        return cur + (target - cur) * (1 - (float) Math.exp(-dt / tau));
    }

    private static void approach(float[] cur, float[] target, float dt, float tau) {
        for (int i = 0; i < 3; i++) {
            cur[i] = approach(cur[i], target[i], dt, tau);
        }
    }

    /** Punto del kit de bateria (coordenadas del modelo del item) -> px del modelo del baterista sentado. */
    private static float[] kit(float ix, float iy, float iz) {
        return new float[]{-(ix - 8) * KIT_SCALE, 24 - iy - SIT_PX, KIT_Z + (iz - 8) * KIT_SCALE};
    }

    private static final float[][] DRUM_TARGETS = {
            kit(-4.4F, 13.0F, 11.0F),   // 0: hi-hat (izquierda)
            kit(1.6F, 11.0F, 12.1F),    // 1: tambor
            kit(10.7F, 14.2F, 5.7F),    // 2: tom de arriba (derecha)
            kit(15.2F, 9.6F, 12.2F),    // 3: tom de piso
    };
    private static final float[] DRUM_CRASH = kit(-3.0F, 17.6F, 4.6F);
    private static final float[] DRUM_RIDE = kit(19.4F, 15.4F, 6.5F);

    // ================================================================ el cuadro

    /** Calcula la coreografia de este cuadro (si ya se calculo, no hace nada). */
    public static void update(GigClient.Performer p) {
        long now = Util.getMillis();
        if (p.frameTime == now) {
            return;
        }
        float dt = p.frameTime == 0 ? 0.1F : Mth.clamp((now - p.frameTime) / 1000F, 0, 0.1F);
        p.frameTime = now;

        double beat = GigClient.beat(now);
        boolean playing = beat >= 0 && !GigClient.finale();
        float e = GigClient.hype();
        float ph = (float) (beat - Math.floor(beat));
        float hit = beat >= 0 ? sq(0.5F + 0.5F * Mth.cos(Mth.TWO_PI * ph)) : 0;   // 1 en el beat, 0 en el medio (suave)
        float sway4 = (float) Math.sin(beat * Math.PI / 2);   // va y vuelve cada 4 tiempos
        float sway2 = (float) Math.sin(beat * Math.PI);       // cada 2
        int phrase = (int) Math.floor(beat / 8);
        float pb = (float) (beat - phrase * 8.0);
        int seed = hash(phrase * 7919 + p.entityId * 31);

        if (!p.player && playing) {
            npcPlays(p, beat, now, seed);
        }

        // ---- objetivos de este cuadro
        float[] l = {6.5F, 11.5F, -1.5F};
        float[] r = {-6.5F, 11.5F, -1.5F};
        float bodyX = 0, bodyY = 0, bodyZ = 0;
        float headX = 0.05F + 0.2F * hit * (0.5F + e);
        float headY = 0.1F * (float) Math.sin(beat * Math.PI / 4);
        float headZ = 0.05F * sway4;
        float legA = 0.1F + 0.2F * hit * (0.4F + e);
        float legSpread = 0.07F;
        float legYaw = 0;
        float ox = 0, oy = 0, oz = 0, oyaw = 0;
        float gAngle = p.instrument == Instrument.BASS ? BASS_ANGLE : GUITAR_ANGLE;
        float tauL = 0.08F, tauR = 0.08F;
        boolean sitting = false;

        if (GigClient.finale()) {
            // saludo final: brazos al cielo y saltos si el publico quedo contento; reverencia si no
            float t = (float) ((now - GigClient.endingAt) / 1000.0);
            if (e > 0.45F) {
                float wave = (float) Math.sin(t * 7);
                l = new float[]{8 + 2 * wave, -9, -1};
                r = new float[]{-8 + 2 * wave, -9, -1};
                oy = 0.28F * Math.abs((float) Math.sin(t * 5.2));
                headX = -0.25F;
            } else {
                bodyX = 0.55F * ease(Mth.clamp(t / 0.8F, 0, 1));
                headX = 0.4F;
            }
            sitting = p.instrument == Instrument.DRUMS;
        } else {
            switch (p.instrument) {
                case GUITAR, BASS -> {
                    boolean bass = p.instrument == Instrument.BASS;
                    float[] at = bass ? BASS_AT : GUITAR_AT;
                    float scale = bass ? BASS_SCALE : GUITAR_SCALE;
                    int move = playing ? seed % 5 : 0;
                    boolean windmill = false;
                    switch (move) {
                        case 1 -> {   // headbang con todo
                            headX = 0.1F + 0.65F * hit * (0.5F + 0.5F * e);
                            bodyX = 0.1F + 0.17F * hit;
                            legA += 0.08F;
                            windmill = !bass && e > 0.6F && pb < 1.0F;
                        }
                        case 2 -> {   // solo: se tira para atras con el mastil al cielo
                            float in = ease(Mth.clamp(Math.min(pb, 8 - pb) / 1.2F, 0, 1));
                            bodyX = -0.3F * in;
                            headX = -0.32F * in + 0.1F * hit;
                            gAngle += 0.4F * in;
                            legSpread = 0.07F + 0.1F * in;
                            oz = -0.08F * in;
                        }
                        case 3 -> {   // camina hasta el borde del escenario y vuelve
                            float w = sq((float) Math.sin(Math.PI * pb / 8));
                            oz = 0.75F * w;
                            oyaw = 16F * (float) Math.sin(Math.PI * pb / 4);
                            headX += 0.08F;
                            legA += 0.1F * w * Math.abs(sway2);
                        }
                        case 4 -> {   // saltos en el primer y quinto tiempo
                            float t = pb % 4;
                            if (e > 0.45F && t < 1) {
                                oy = 0.5F * (float) Math.sin(Math.PI * t);
                                legA = 0.25F * (1 - (float) Math.sin(Math.PI * t));
                                gAngle += 0.15F;
                            } else {
                                ox = 0.12F * sway4;
                                oyaw = 10F * sway4;
                            }
                        }
                        default -> {   // balanceo de costado
                            ox = 0.13F * sway4;
                            bodyZ = 0.08F * sway4;
                            oyaw = 10F * sway4;
                        }
                    }
                    bodyY = 0.06F * sway4;
                    float c = Mth.cos(gAngle);
                    float s = Mth.sin(gAngle);
                    // mano izquierda en el mastil: se corre por los trastes segun la nota
                    float neck = 24F * scale;
                    float t = neck * (0.55F + 0.1F * (3 - Math.min(3, p.fret)));
                    t += (1 - since(now, p.lastHit, 180)) * 0.5F;
                    l = new float[]{at[0] + c * t, at[1] - s * t - 0.5F, at[2] - 1.7F};
                    // mano derecha: pua alternada (un golpe baja, el siguiente sube)
                    float k = ease(since(now, p.lastHit, bass ? 170 : 120));
                    float stroke = (p.strokes & 1) == 0 ? k : 1 - k;
                    float held = p.pressed != 0 ? 0.3F : 0;
                    float b = 3.8F * scale;
                    r = new float[]{at[0] - c * b, at[1] + s * b - 2.0F + stroke * 4.0F + held, at[2] - 3.0F};
                    tauL = 0.07F;
                    tauR = 0.028F;
                    if (windmill) {
                        // molinete: el brazo da toda la vuelta en el primer tiempo
                        float th = Mth.TWO_PI * pb;
                        r = new float[]{-6.5F, 2 - 9.5F * Mth.cos(th), -9.5F * Mth.sin(th)};
                        tauR = 0.02F;
                    }
                }
                case DRUMS -> {
                    sitting = true;
                    int move = playing ? seed % 3 : 0;
                    int left = p.laneHit[0] >= p.laneHit[1] ? 0 : 1;
                    int right = p.laneHit[2] >= p.laneHit[3] ? 2 : 3;
                    l = stick(DRUM_TARGETS[left], since(now, p.laneHit[left], 210));
                    r = stick(DRUM_TARGETS[right], since(now, p.laneHit[right], 210));
                    if (playing && pb < 0.45F && e > 0.5F) {
                        // arranque de frase: los dos palillos al cielo y al platillo
                        float up = 1 - pb / 0.45F;
                        l = mix(DRUM_CRASH, new float[]{7.5F, -9, -2}, up);
                        r = mix(DRUM_RIDE, new float[]{-7.5F, -9, -2}, up);
                    }
                    headX = 0.12F + (move == 1 ? 0.55F : 0.3F) * hit * (0.5F + 0.5F * e);
                    bodyX = 0.1F + (move == 1 ? 0.14F : 0.06F) * hit;
                    bodyY = 0.1F * sway2;
                    bodyZ = 0.05F * sway4;
                    oy = -0.02F * hit;
                    tauL = tauR = 0.03F;
                    legA = 0;
                }
                case KEYS -> {
                    int move = playing ? seed % 3 : 0;
                    int left = p.laneHit[0] >= p.laneHit[1] ? 0 : 1;
                    int right = p.laneHit[2] >= p.laneHit[3] ? 2 : 3;
                    float kl = since(now, p.laneHit[left], 130);
                    float kr = since(now, p.laneHit[right], 130);
                    float keyY = 24 - 16.3F * KEYS_SCALE_Y - 0.6F;
                    l = new float[]{3.5F + left * 3.0F, keyY + (1 - kl) * 1.5F - 1.2F * kl, KEYS_Z + 1.4F};
                    r = new float[]{-9.5F + (right - 2) * 3.0F, keyY + (1 - kr) * 1.5F - 1.2F * kr, KEYS_Z + 1.4F};
                    bodyX = 0.12F + 0.05F * hit;
                    switch (move) {
                        case 1 -> {   // una mano al aire, arengando
                            l = new float[]{7.5F, -8.5F + 3.5F * (1 - hit), -1.5F};
                            headX = -0.1F + 0.2F * hit;
                        }
                        case 2 -> {   // metido en el teclado
                            bodyX = 0.26F + 0.1F * hit;
                            headX = 0.25F + 0.4F * hit * (0.5F + 0.5F * e);
                        }
                        default -> {
                            bodyZ = 0.12F * sway2;
                            ox = 0.05F * sway4;
                        }
                    }
                    bodyY = 0.08F * sway4;
                    tauL = tauR = 0.04F;
                    legA = 0.06F + 0.12F * hit;
                }
                case VOICE -> {
                    int move = playing ? seed % 5 : 0;
                    float vib = 0.2F * Mth.sin(now / 90F);
                    r = new float[]{-0.6F, -2.2F + vib, -6.9F};
                    float wave = playing ? Math.max(0, sway4) : 0;
                    float k = since(now, p.lastHit, 600);
                    switch (move) {
                        case 1 -> {   // senala al publico, de lado a lado
                            l = new float[]{4 + 7 * (float) Math.sin(beat * Math.PI / 4), 1.5F, -10};
                            oyaw = 12F * (float) Math.sin(beat * Math.PI / 4);
                        }
                        case 2 -> {   // puno en alto con cada tiempo
                            l = new float[]{7.5F, -9.5F + 4 * (1 - hit), -1};
                            oy = 0.1F * hit * e;
                        }
                        case 3 -> {   // recorre el escenario
                            ox = 0.95F * (float) Math.sin(Math.PI * pb / 4);
                            oyaw = -28F * (float) Math.cos(Math.PI * pb / 4);
                            l = new float[]{7, 9 - 2 * Math.abs(sway2), -3.5F * sway2};
                            legA += 0.12F * Math.abs(sway2);
                        }
                        case 4 -> {   // nota larga: se tira para atras, microfono arriba
                            float in = ease(Mth.clamp(Math.min(pb, 8 - pb) / 1.2F, 0, 1));
                            bodyX = -0.28F * in;
                            headX = -0.35F * in;
                            r = new float[]{-0.6F, -3.6F + vib, -6.4F};
                            l = new float[]{10, 3 - 2 * in, -2};
                            float t = pb - 4;
                            if (e > 0.55F && t >= 0 && t < 1) {
                                oy = 0.45F * (float) Math.sin(Math.PI * t);
                            }
                        }
                        default -> l = new float[]{7 + 2 * sway2, 8 - 5 * wave - 4 * (1 - k), -5};
                    }
                    bodyY = 0.1F * sway4;
                    if (move != 4) {
                        headX = -0.04F + 0.12F * hit;
                    }
                    tauL = 0.09F;
                    tauR = 0.06F;
                }
            }
        }

        // rodillas: al flexionar baja un poquito todo el cuerpo (los pies quedan en el piso)
        if (!sitting) {
            oy -= 0.75F * (1 - Mth.cos(legA));
        } else {
            oy -= SIT;
        }

        // ---- suavizado
        approach(p.handL, l, dt, tauL);
        approach(p.handR, r, dt, tauR);
        p.bodyX = approach(p.bodyX, bodyX, dt, 0.09F);
        p.bodyY = approach(p.bodyY, bodyY, dt, 0.12F);
        p.bodyZ = approach(p.bodyZ, bodyZ, dt, 0.12F);
        p.headX = approach(p.headX, headX, dt, 0.05F);
        p.headY = approach(p.headY, headY, dt, 0.12F);
        p.headZ = approach(p.headZ, headZ, dt, 0.12F);
        p.legA = approach(p.legA, legA, dt, 0.05F);
        p.legSpread = approach(p.legSpread, legSpread, dt, 0.12F);
        p.legYaw = approach(p.legYaw, legYaw, dt, 0.12F);
        p.ox = approach(p.ox, ox, dt, 0.16F);
        p.oy = approach(p.oy, oy, dt, 0.035F);
        p.oz = approach(p.oz, oz, dt, 0.16F);
        p.oyaw = approach(p.oyaw, oyaw, dt, 0.16F);
        p.guitarAngle = approach(p.guitarAngle, gAngle, dt, 0.12F);
        p.sitting = sitting;
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{Mth.lerp(t, a[0], b[0]), Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2])};
    }

    /** En el golpe la punta esta en el parche; despues sube y queda lista para el proximo. */
    private static float[] stick(float[] target, float k) {
        float lift = 5.5F * Mth.sin(Math.min(1, k) * Mth.HALF_PI);
        return new float[]{target[0], target[1] - lift, target[2]};
    }

    /** Los aldeanos tocan solos, al ritmo, con un patron distinto cada uno (y redobles al final de la frase). */
    private static void npcPlays(GigClient.Performer p, double beat, long now, int seed) {
        float pb = (float) (beat - Math.floor(beat / 8) * 8.0);
        boolean fill = p.instrument == Instrument.DRUMS && pb >= 6.5F && (seed % 3) != 0;
        int div = fill ? 4 : 2;
        int sub = (int) Math.floor(beat * div);
        if (sub == p.lastSub) {
            return;
        }
        p.lastSub = sub;
        int id = p.entityId * 31;
        switch (p.instrument) {
            case DRUMS -> {
                if (fill) {
                    p.hit(new int[]{1, 2, 3, 2}[Math.floorMod(sub, 4)], now);
                } else if (sub % 2 == 0) {
                    p.hit(sub % 4 == 0 ? 3 : 1, now);   // bombo/tom y tambor
                    p.hit(0, now);
                } else {
                    p.hit(0, now);                       // hi-hat en las corcheas
                }
            }
            case BASS -> {
                if (sub % 2 == 0 || Math.floorMod(sub + id, 5) == 0) {
                    p.hit(Math.floorMod(sub / 2 + id, 4), now);
                }
                p.fret = Math.floorMod(sub / 4 + id, 4);
            }
            case KEYS -> {
                if (sub % 2 == 0 || Math.floorMod(sub + id, 3) == 0) {
                    p.hit(Math.floorMod(sub * 3 + id, 4), now);
                }
            }
            case VOICE -> {
                if (Math.floorMod(sub + id, 4) != 3) {
                    p.hit(Math.floorMod(sub + id, 4), now);
                }
            }
            default -> {
                p.hit(Math.floorMod(sub * 7 + id, 4), now);
                p.fret = Math.floorMod(sub / 4 + id, 4);
            }
        }
    }

    // ================================================================ al modelo

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

    public static void apply(LivingEntity e, HumanoidModel<?> m) {
        GigClient.Performer p = GigClient.performer(e.getId());
        if (p == null || m == null) {
            return;
        }
        update(p);

        // torso: se inclina desde la cadera (no desde el cuello), y la cabeza y los hombros lo siguen
        float cx = Mth.cos(p.bodyX), sx = Mth.sin(p.bodyX);
        float sz = Mth.sin(p.bodyZ);
        float topY = 12 - 12 * cx;
        float topZ = -12 * sx;
        float topX = 12 * sz;
        m.body.x = topX;
        m.body.y = topY;
        m.body.z = topZ;
        m.body.xRot = p.bodyX;
        m.body.yRot = p.bodyY;
        m.body.zRot = p.bodyZ;
        m.head.x = topX;
        m.head.y = topY;
        m.head.z = topZ;
        m.head.xRot = p.bodyX * 0.5F + p.headX;
        m.head.yRot = p.headY;
        m.head.zRot = p.headZ + p.bodyZ * 0.5F;
        float shY = topY + 2 * cx;
        float shZ = topZ + 2 * sx;
        m.rightArm.x = -5 + topX;
        m.rightArm.y = shY;
        m.rightArm.z = shZ;
        m.leftArm.x = 5 + topX;
        m.leftArm.y = shY;
        m.leftArm.z = shZ;

        // los instrumentos colgados van con el torso: los puntos de las manos se inclinan igual
        boolean strapped = p.instrument == Instrument.GUITAR || p.instrument == Instrument.BASS;
        float[] l = p.handL;
        float[] r = p.handR;
        if (strapped && !GigClient.finale()) {
            l = withBody(l, cx, sx, topY, topZ, topX);
            r = withBody(r, cx, sx, topY, topZ, topX);
        }
        aim(m.leftArm, l[0], l[1], l[2]);
        aim(m.rightArm, r[0], r[1], r[2]);

        // piernas
        if (p.sitting) {
            m.rightLeg.xRot = -1.35F;
            m.leftLeg.xRot = -1.35F;
            m.rightLeg.yRot = 0.38F;
            m.leftLeg.yRot = -0.38F;
            m.rightLeg.zRot = 0.06F;
            m.leftLeg.zRot = -0.06F;
        } else {
            m.rightLeg.xRot = p.legA;
            m.leftLeg.xRot = -p.legA;
            m.rightLeg.yRot = p.legYaw;
            m.leftLeg.yRot = -p.legYaw;
            m.rightLeg.zRot = p.legSpread;
            m.leftLeg.zRot = -p.legSpread;
        }
        m.rightLeg.y = 12;
        m.leftLeg.y = 12;
        m.rightLeg.z = 0;
        m.leftLeg.z = 0;
        sync(m);
    }

    /** Lleva un punto pensado para el torso derecho al torso inclinado. */
    private static float[] withBody(float[] pt, float cx, float sx, float topY, float topZ, float topX) {
        float y = pt[1];
        float z = pt[2];
        return new float[]{pt[0] + topX, topY + y * cx - z * sx, topZ + y * sx + z * cx};
    }

    // ---------------------------------------------------------------- segunda capa

    /** Mangas, chaqueta, pantalon y sombrero siguen al cuerpo (y la ropa de MCA tambien). */
    private static void sync(HumanoidModel<?> m) {
        m.hat.copyFrom(m.head);
        if (m instanceof PlayerModel<?> pm) {
            pm.leftSleeve.copyFrom(pm.leftArm);
            pm.rightSleeve.copyFrom(pm.rightArm);
            pm.jacket.copyFrom(pm.body);
            pm.leftPants.copyFrom(pm.leftLeg);
            pm.rightPants.copyFrom(pm.rightLeg);
        }
        Method method = SYNC.computeIfAbsent(m.getClass(), c -> Optional.ofNullable(findSync(c))).orElse(null);
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
