package com.starlevi.starcombat;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Los movimientos: por donde vuela cada hoja y cuando pega. Lo usan igual el servidor (golpes) y los clientes
 * (dibujo), asi lo que se ve coincide con lo que pega.
 */
public final class Moves {
    /** Recorrido de una hoja: un arco alrededor del jugador, con la cadena que se estira y vuelve. */
    public static final class Arc {
        /** angulo inicial y final en grados: 0 = adelante, 90 = a la derecha */
        float a0, a1;
        /** inclinacion del plano del arco: 0 = horizontal, 90 = vertical (por encima de la cabeza) */
        float tilt;
        float rMin = 0.55F, rMax = 3.5F;
        /** altura al empezar y al terminar */
        float h0 = 1.2F, h1 = 1.2F;
        /** corrimiento lateral fijo */
        float side;
        /** tramo del movimiento en que la hoja esta en el aire (0..1) */
        float start = 0.05F, end = 0.92F;
        /** cuanto del tramo tarda en estirarse y en volver */
        float rise = 0.5F, fall = 0.5F;
        /** en que parte del tramo termina de girar (1 = gira todo el tramo) */
        float turn = 1;

        Arc angles(float a0, float a1) { this.a0 = a0; this.a1 = a1; return this; }
        Arc tilt(float t) { this.tilt = t; return this; }
        Arc reach(float r) { this.rMax = r; return this; }
        Arc height(float h0, float h1) { this.h0 = h0; this.h1 = h1; return this; }
        Arc side(float s) { this.side = s; return this; }
        Arc window(float s, float e) { this.start = s; this.end = e; return this; }
        Arc shape(float rise, float fall, float turn) { this.rise = rise; this.fall = fall; this.turn = turn; return this; }
    }

    /** Un golpe: en que tick, a quienes alcanza y que les hace. */
    public static final class Hit {
        public final int tick;
        /** centro del golpe: bloques por delante del jugador */
        public final float fwd;
        public final float range;
        /** medio angulo del abanico en grados (180 = todo alrededor) */
        public final float half;
        public final float damage, knock, lift;
        public final int fire;
        /** efecto: 0 nada, 1 estallido adelante, 2 anillo de fuego */
        public final int fx;

        Hit(int tick, float fwd, float range, float half, float damage, float knock, float lift, int fire, int fx) {
            this.tick = tick;
            this.fwd = fwd;
            this.range = range;
            this.half = half;
            this.damage = damage;
            this.knock = knock;
            this.lift = lift;
            this.fire = fire;
            this.fx = fx;
        }
    }

    public static final class Move {
        public final int idx;
        public final String id;
        /** duracion en ticks y desde que tick se puede encadenar el siguiente */
        public final int duration, chainAt;
        public final Arc right, left;
        public final Hit[] hits;
        /** empujon hacia adelante al empezar */
        public float lunge;
        /** vueltas del cuerpo en grados (negativo = hacia la izquierda) */
        public float bodySpin;
        public boolean heavy;
        /** espera en el aire hasta tocar el suelo */
        public boolean prep;

        Move(int idx, String id, int duration, int chainAt, Arc right, Arc left, Hit... hits) {
            this.idx = idx;
            this.id = id;
            this.duration = duration;
            this.chainAt = chainAt;
            this.right = right;
            this.left = left;
            this.hits = hits;
        }
    }

    public static final int L1 = 0, L2 = 1, L3 = 2, L4 = 3, H1 = 4, H2 = 5, CYCLONE = 6, PLUNGE = 7, PLUNGE_LAND = 8;
    public static final Move[] ALL = new Move[9];

    static {
        // --- livianos: tajo, revés, cruz y latigazo final
        Move m = new Move(L1, "tajo", 9, 6,
                new Arc().angles(100, -75).tilt(-12).reach(3.7F), null,
                new Hit(4, 0, 4.3F, 80, 5, 0.25F, 0, 2, 0));
        m.lunge = 0.22F;
        ALL[L1] = m;

        m = new Move(L2, "reves", 9, 6,
                null, new Arc().angles(-100, 75).tilt(12).reach(3.7F),
                new Hit(4, 0, 4.3F, 80, 5, 0.25F, 0, 2, 0));
        m.lunge = 0.22F;
        ALL[L2] = m;

        m = new Move(L3, "cruz", 11, 7,
                new Arc().angles(70, -60).tilt(40).reach(3.9F).height(1.35F, 1.35F),
                new Arc().angles(-70, 60).tilt(-40).reach(3.9F).height(1.35F, 1.35F),
                new Hit(5, 0, 4.5F, 65, 7, 0.35F, 0, 3, 0));
        m.lunge = 0.28F;
        ALL[L3] = m;

        m = new Move(L4, "latigazo", 16, 12,
                new Arc().angles(150, -13).tilt(90).reach(5.5F).height(1.25F, 1.25F).side(0.45F).shape(0.6F, 0.4F, 0.6F),
                new Arc().angles(150, -13).tilt(90).reach(5.5F).height(1.25F, 1.25F).side(-0.45F).shape(0.6F, 0.4F, 0.6F),
                new Hit(9, 0, 6.3F, 28, 10, 0.6F, 0.15F, 4, 1));
        m.lunge = 0.15F;
        ALL[L4] = m;

        // --- pesados: barrido en giro y martillo
        m = new Move(H1, "barrido", 16, 12,
                new Arc().angles(90, -270).reach(4.7F).height(1.05F, 1.05F).window(0, 1).shape(0.22F, 0.22F, 1),
                new Arc().angles(-90, -450).reach(4.7F).height(1.05F, 1.05F).window(0, 1).shape(0.22F, 0.22F, 1),
                new Hit(4, 0, 5.2F, 180, 6, 0.45F, 0, 3, 0),
                new Hit(10, 0, 5.2F, 180, 6, 0.55F, 0.1F, 3, 0));
        m.bodySpin = -360;
        m.heavy = true;
        ALL[H1] = m;

        m = new Move(H2, "martillo", 18, 13,
                new Arc().angles(160, -20).tilt(90).reach(3.7F).height(1.25F, 1.25F).side(0.6F).shape(0.62F, 0.38F, 0.62F),
                new Arc().angles(160, -20).tilt(90).reach(3.7F).height(1.25F, 1.25F).side(-0.6F).shape(0.62F, 0.38F, 0.62F),
                new Hit(11, 3.0F, 3.8F, 180, 12, 0.7F, 0.45F, 5, 2));
        m.heavy = true;
        ALL[H2] = m;

        // --- ciclon: pesado despues de dos livianos
        m = new Move(CYCLONE, "ciclon", 26, 20,
                new Arc().angles(90, -810).reach(5.1F).height(0.9F, 1.9F).window(0, 1).shape(0.15F, 0.15F, 1),
                new Arc().angles(-90, -990).reach(5.1F).height(0.9F, 1.9F).window(0, 1).shape(0.15F, 0.15F, 1),
                new Hit(4, 0, 5.6F, 180, 5, 0.2F, 0.05F, 2, 0),
                new Hit(9, 0, 5.6F, 180, 5, 0.2F, 0.05F, 2, 0),
                new Hit(14, 0, 5.6F, 180, 5, 0.2F, 0.05F, 2, 0),
                new Hit(19, 0, 5.8F, 180, 8, 1.0F, 0.4F, 4, 2));
        m.bodySpin = -900;
        m.heavy = true;
        ALL[CYCLONE] = m;

        // --- caida: pesado en el aire; espera con las hojas en alto y al tocar el suelo estalla
        m = new Move(PLUNGE, "caida", 60, 999,
                new Arc().angles(75, 75).tilt(90).reach(1.7F).height(1.4F, 1.4F).side(0.5F).window(0, 1).shape(0.15F, 0, 1),
                new Arc().angles(75, 75).tilt(90).reach(1.7F).height(1.4F, 1.4F).side(-0.5F).window(0, 1).shape(0.15F, 0, 1));
        m.heavy = true;
        m.prep = true;
        ALL[PLUNGE] = m;

        m = new Move(PLUNGE_LAND, "impacto", 13, 9,
                new Arc().angles(95, -20).tilt(90).reach(3.7F).height(1.25F, 1.25F).side(0.6F).window(0, 0.9F).shape(0.25F, 0.75F, 0.25F),
                new Arc().angles(95, -20).tilt(90).reach(3.7F).height(1.25F, 1.25F).side(-0.6F).window(0, 0.9F).shape(0.25F, 0.75F, 0.25F),
                new Hit(2, 0, 5.5F, 180, 12, 0.9F, 0.55F, 5, 2));
        m.heavy = true;
        ALL[PLUNGE_LAND] = m;
    }

    private Moves() {
    }

    public static Move get(int idx) {
        return idx >= 0 && idx < ALL.length ? ALL[idx] : null;
    }

    public static float smooth(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /**
     * Donde esta la hoja, relativo a los pies del jugador (x = derecha, y = arriba, z = adelante).
     * {@code null} = la hoja esta en la mano.
     */
    public static Vec3 local(Arc a, float u) {
        if (a == null) {
            return null;
        }
        float s = (u - a.start) / (a.end - a.start);
        if (s < 0 || s > 1) {
            return null;
        }
        float e = smooth(Math.min(1, s / a.turn));
        double ang = Math.toRadians(Mth.lerp(e, a.a0, a.a1));
        float ext = smooth(Math.min(1, s / a.rise));
        if (a.fall > 0) {
            ext *= smooth(Math.min(1, (1 - s) / a.fall));
        }
        double r = a.rMin + (a.rMax - a.rMin) * ext;
        double tilt = Math.toRadians(a.tilt);
        double x = Math.sin(ang) * r * Math.cos(tilt) + a.side;
        double y = Mth.lerp(e, a.h0, a.h1) + Math.sin(ang) * r * Math.sin(tilt);
        double z = Math.cos(ang) * r;
        return new Vec3(x, Math.max(0.12, y), z);
    }

    public static Vec3 forward(float yawDeg) {
        double yaw = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    public static Vec3 right(float yawDeg) {
        double yaw = Math.toRadians(yawDeg);
        return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
    }

    /** De coordenadas del jugador a coordenadas del mundo. */
    public static Vec3 world(Vec3 base, float yawDeg, Vec3 local) {
        Vec3 f = forward(yawDeg), r = right(yawDeg);
        return new Vec3(base.x + r.x * local.x + f.x * local.z, base.y + local.y, base.z + r.z * local.x + f.z * local.z);
    }
}
