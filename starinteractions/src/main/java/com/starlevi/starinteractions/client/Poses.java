package com.starlevi.starinteractions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starlevi.starinteractions.Catalog;
import com.starlevi.starinteractions.net.Net;
import net.minecraft.client.CameraType;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * El motor del lado del cliente: reproduce las animaciones (de a dos, con "ancla", y emotes), pone las poses de ir
 * de la mano y de cargar, y la del modo toque (cabeza que se inclina con la caricia, brazos que siguen al mouse).
 * <p>
 * Todo se aplica al final de la pose normal y otra vez despues de EMF (Fresh Animations), asi queda encima sin
 * apagar nada: los huesos que una interaccion no usa siguen con su animacion de siempre.
 */
public final class Poses {
    private static final class Active {
        Anim anim;
        String name;
        String participant;
        long startMs;
        boolean anchored;
        double ax, ay, az;
        float ayaw;
    }

    static final class LinkState {
        int a, b, mode;
    }

    /** El modo toque visto desde este cliente (suavizado). */
    static final class TouchView {
        int toucher, target;
        boolean active;
        Net.TouchPose pose = new Net.TouchPose();
        float headX, headY, headZ, wR, wL, wToucher, wiggle;
        final float[] r = new float[3];
        final float[] l = new float[3];
        double hx, hy, hz;
        long last;
        int fx;
        long fxAt;
    }

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();
    private static final Map<Integer, LinkState> LINKS = new HashMap<>();
    private static final Map<Integer, TouchView> TOUCH_TARGET = new HashMap<>();
    private static final Map<Integer, TouchView> TOUCH_TOUCHER = new HashMap<>();
    private static final Map<Class<?>, Optional<Method>> SYNC = new HashMap<>();
    private static CameraType savedCamera;
    private static boolean localLocked;
    private static int pushed = Integer.MIN_VALUE;

    private Poses() {
    }

    // ================================================================ estado

    static void clear() {
        ACTIVE.clear();
        LINKS.clear();
        TOUCH_TARGET.clear();
        TOUCH_TOUCHER.clear();
        localLocked = false;
    }

    static void startPair(Net.PairStart msg) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Anim> anim = Anim.get(msg.interaction());
        if (anim.isEmpty()) {
            return;
        }
        long now = Util.getMillis();
        for (int i = 0; i < 2; i++) {
            Active a = new Active();
            a.anim = anim.get();
            a.name = msg.interaction();
            a.participant = i == 0 ? "player" : "villager";
            a.startMs = now;
            a.anchored = true;
            a.ax = msg.x();
            a.ay = msg.y();
            a.az = msg.z();
            a.ayaw = msg.yaw();
            ACTIVE.put(i == 0 ? msg.aId() : msg.bId(), a);
        }
        if (mc.player != null && (mc.player.getId() == msg.aId() || mc.player.getId() == msg.bId())) {
            localLocked = true;
            if (anim.get().thirdPerson && savedCamera == null) {
                savedCamera = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        }
    }

    static void stopPair(int a, int b) {
        for (int id : new int[]{a, b}) {
            Active x = ACTIVE.get(id);
            if (x != null && x.anchored) {
                ACTIVE.remove(id);
            }
        }
    }

    static void emote(int entityId, String id) {
        Active cur = ACTIVE.get(entityId);
        if (cur != null && cur.anchored) {
            return;
        }
        if (id.isEmpty()) {
            ACTIVE.remove(entityId);
            return;
        }
        Anim.get(id).ifPresent(anim -> {
            Active a = new Active();
            a.anim = anim;
            a.name = id;
            a.participant = "self";
            a.startMs = Util.getMillis();
            ACTIVE.put(entityId, a);
        });
    }

    static void link(int a, int b, int mode) {
        LINKS.values().removeIf(l -> l.a == a || l.b == b || l.a == b || l.b == a);
        if (mode != Catalog.LINK_NONE && a >= 0 && b >= 0) {
            LinkState l = new LinkState();
            l.a = a;
            l.b = b;
            l.mode = mode;
            LINKS.put(a, l);
            LINKS.put(b, l);
        }
    }

    static LinkState linkOf(int entityId) {
        return LINKS.get(entityId);
    }

    static void touch(Net.TouchState msg) {
        TouchView v = TOUCH_TARGET.get(msg.targetId());
        if (!msg.active()) {
            TouchView old = TOUCH_TOUCHER.get(msg.toucherId());
            if (old != null) {
                old.active = false;
                old.pose = new Net.TouchPose();
            }
            return;
        }
        if (v == null || v.toucher != msg.toucherId()) {
            v = new TouchView();
            v.toucher = msg.toucherId();
            v.target = msg.targetId();
            TOUCH_TARGET.put(v.target, v);
            TOUCH_TOUCHER.put(v.toucher, v);
        }
        v.active = true;
        Minecraft mc = Minecraft.getInstance();
        // la pose propia ya la pone este cliente al instante; del servidor solo llegan los efectos
        if (mc.player == null || mc.player.getId() != msg.toucherId()) {
            v.pose = msg.pose();
        }
        if (msg.fx() != 0) {
            v.fx = msg.fx();
            v.fxAt = Util.getMillis();
        }
    }

    /** La pose del que toca en este cliente (sin esperar al servidor). */
    static TouchView localTouch(int toucherId, int targetId) {
        TouchView v = TOUCH_TARGET.get(targetId);
        if (v == null || v.toucher != toucherId) {
            v = new TouchView();
            v.toucher = toucherId;
            v.target = targetId;
            TOUCH_TARGET.put(targetId, v);
            TOUCH_TOUCHER.put(toucherId, v);
        }
        v.active = true;
        return v;
    }

    static TouchView touchOfTarget(int id) {
        return TOUCH_TARGET.get(id);
    }

    public static boolean isLocalLocked() {
        return localLocked;
    }

    private static Active current(Entity e) {
        Active a = ACTIVE.get(e.getId());
        if (a == null) {
            return null;
        }
        if (!a.anim.loop && time(a) > a.anim.length) {
            ACTIVE.remove(e.getId());
            return null;
        }
        return a;
    }

    static boolean isAnimating(Entity e) {
        return current(e) != null;
    }

    static String emoteOf(Entity e) {
        Active a = current(e);
        return a != null && !a.anchored ? a.name : null;
    }

    private static float time(Active a) {
        float t = (Util.getMillis() - a.startMs) / 1000F;
        return a.anim.loop ? t % a.anim.length : t;
    }

    static void clientTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            clear();
            return;
        }
        Active mine = current(mc.player);
        if (localLocked && (mine == null || !mine.anchored)) {
            localLocked = false;
        }
        if (!localLocked && savedCamera != null) {
            mc.options.setCameraType(savedCamera);
            savedCamera = null;
        }
        // lo que quedo de entidades que ya no estan
        long now = Util.getMillis();
        TOUCH_TARGET.values().removeIf(v -> !v.active && now - v.last > 1500 && v.wR < 0.01F && v.wL < 0.01F);
        TOUCH_TOUCHER.values().removeIf(v -> !TOUCH_TARGET.containsValue(v));
        LINKS.values().removeIf(l -> mc.level.getEntity(l.a) == null && mc.level.getEntity(l.b) == null);
    }

    // ================================================================ lugar en el mundo (al dibujar)

    private static Vec3 anchorToWorld(Active a, float[] local) {
        double yaw = Math.toRadians(a.ayaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double x = local[0] / 16.0, y = local[1] / 16.0, z = local[2] / 16.0;
        return new Vec3(a.ax + fx * z + rx * x, a.ay + y, a.az + fz * z + rz * x);
    }

    /** Antes de dibujar: lleva a la entidad a su lugar (sin moverla de verdad). Devuelve true si empujo una matriz. */
    static boolean beforeRender(LivingEntity e, float pt, PoseStack ps) {
        Active a = current(e);
        if (a != null && a.anchored) {
            Anim.Bone root = a.anim.bones(a.participant).get("root");
            float t = time(a);
            boolean second = a.participant.equals("villager");
            float[] pos = root != null && root.position != null ? root.position.sample(t) : new float[]{0, 0, second ? a.anim.distance : 0};
            float rotY = root != null && root.rotation != null ? root.rotation.sample(t)[1] : (second ? 180 : 0);
            float w = a.anim.weight(t);
            Vec3 target = anchorToWorld(a, pos);
            Vec3 actual = e.getPosition(pt);
            ps.pushPose();
            ps.translate((target.x - actual.x) * w, (target.y - actual.y) * w, (target.z - actual.z) * w);
            float body = Mth.rotLerp(w, e.yBodyRot, a.ayaw + rotY);
            e.yBodyRot = body;
            e.yBodyRotO = body;
            e.yHeadRot = body;
            e.yHeadRotO = body;
            return true;
        }
        if (a != null) {
            Anim.Bone root = a.anim.bones(a.participant).get("root");
            if (root != null && root.position != null) {
                float t = time(a);
                float[] p = root.position.sample(t);
                float w = a.anim.weight(t);
                double yaw = Math.toRadians(Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot));
                double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -Math.cos(yaw), rz = -Math.sin(yaw);
                double x = p[0] / 16.0 * w, y = p[1] / 16.0 * w, z = p[2] / 16.0 * w;
                ps.pushPose();
                ps.translate(fx * z + rx * x, y, fz * z + rz * x);
                return true;
            }
            return false;
        }
        // el que va cargado: exactamente pegado a quien lo carga, girado como corresponde
        LinkState l = LINKS.get(e.getId());
        if (l != null && l.mode != Catalog.LINK_HANDS && l.b == e.getId() && e.getVehicle() instanceof LivingEntity carrier
                && carrier.getId() == l.a) {
            float b = Mth.rotLerp(pt, carrier.yBodyRotO, carrier.yBodyRot);
            double rad = Math.toRadians(b);
            double fx = -Math.sin(rad), fz = Math.cos(rad);
            Vec3 base = carrier.getPosition(pt);
            double ox = 0, oy = 0, oz = 0;
            float turn = 0, recline = 0;
            float hip = 0.72F;
            switch (l.mode) {
                case Catalog.LINK_ARMS -> {
                    ox = fx * 0.42;
                    oz = fz * 0.42;
                    oy = 0.95 - hip;
                    turn = 90;
                    recline = 1.2F;
                }
                case Catalog.LINK_BACK -> {
                    ox = -fx * 0.30;
                    oz = -fz * 0.30;
                    oy = 0.58;
                    recline = -0.28F;
                }
                default -> {
                    ox = -fx * 0.04;
                    oz = -fz * 0.04;
                    oy = 1.42 - hip;
                }
            }
            Vec3 actual = e.getPosition(pt);
            ps.pushPose();
            ps.translate(base.x + ox - actual.x, base.y + oy - actual.y, base.z + oz - actual.z);
            if (recline != 0) {
                double r = Math.toRadians(b - turn);
                float axX = (float) -Math.cos(r), axZ = (float) -Math.sin(r);
                ps.translate(0, hip, 0);
                ps.mulPose(new Quaternionf().rotationAxis(recline, axX, 0, axZ));
                ps.translate(0, -hip, 0);
            }
            if (turn != 0) {
                ps.mulPose(Axis.YP.rotationDegrees(turn));
            }
            return true;
        }
        return false;
    }

    static void renderPre(LivingEntity e, float pt, PoseStack ps) {
        if (Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
            return;
        }
        if (beforeRender(e, pt, ps)) {
            pushed = e.getId();
        }
    }

    static void renderPost(LivingEntity e, PoseStack ps) {
        if (pushed == e.getId()) {
            ps.popPose();
            pushed = Integer.MIN_VALUE;
        }
    }

    // ================================================================ huesos

    private static ModelPart part(HumanoidModel<?> m, String bone) {
        return switch (bone) {
            case "head" -> m.head;
            case "body" -> m.body;
            case "right_arm" -> m.rightArm;
            case "left_arm" -> m.leftArm;
            case "right_leg" -> m.rightLeg;
            case "left_leg" -> m.leftLeg;
            default -> null;
        };
    }

    private static void set(ModelPart p, float x, float y, float z) {
        p.xRot = x;
        p.yRot = y;
        p.zRot = z;
    }

    /** Apunta el brazo hacia un punto del modelo (px); w = cuanto pesa sobre la pose que ya tenia. */
    private static void aim(ModelPart arm, float tx, float ty, float tz, float w) {
        if (w <= 0.001F) {
            return;
        }
        float dx = tx - arm.x, dy = ty - arm.y, dz = tz - arm.z;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001F) {
            return;
        }
        float x = -(float) Math.acos(Mth.clamp(dy / len, -1, 1));
        float y = (float) Math.atan2(-dx / len, -dz / len);
        arm.xRot = Mth.lerp(w, arm.xRot, x);
        arm.yRot = Mth.lerp(w, arm.yRot, y);
        arm.zRot = Mth.lerp(w, arm.zRot, 0);
    }

    /** Un punto del mundo, en px del modelo de esa entidad (x = su izquierda, y = abajo, z = atras). */
    static float[] worldToModel(LivingEntity e, double wx, double wy, double wz, float pt) {
        Vec3 pos = e.getPosition(pt);
        double rad = Math.toRadians(Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot));
        double fx = -Math.sin(rad), fz = Math.cos(rad), rx = -Math.cos(rad), rz = -Math.sin(rad);
        double dx = wx - pos.x, dy = wy - pos.y, dz = wz - pos.z;
        float s = 16F / (e instanceof Player ? 0.9375F : 1F);
        return new float[]{(float) -(dx * rx + dz * rz) * s, 24F - (float) dy * s, (float) -(dx * fx + dz * fz) * s};
    }

    private static final Map<ModelPart, float[]> WRITTEN = new java.util.WeakHashMap<>();

    /** Suma a un hueso una sola vez por pose (si nadie lo recalculo desde la ultima vez, no repite). */
    private static void addOnce(ModelPart part, float x, float y, float z) {
        float[] w = WRITTEN.get(part);
        if (w != null && part.xRot == w[0] && part.yRot == w[1] && part.zRot == w[2]) {
            return;
        }
        part.xRot += x;
        part.yRot += y;
        part.zRot += z;
        if (w == null) {
            w = new float[3];
            WRITTEN.put(part, w);
        }
        w[0] = part.xRot;
        w[1] = part.yRot;
        w[2] = part.zRot;
    }

    private static float approach(float cur, float target, float dt, float tau) {
        return dt <= 0 ? cur : cur + (target - cur) * (1 - (float) Math.exp(-dt / tau));
    }

    public static void apply(LivingEntity e, HumanoidModel<?> m) {
        boolean changed = false;
        float pt = Minecraft.getInstance().getFrameTime();
        Active a = current(e);
        if (a != null) {
            float t = time(a);
            float w = a.anim.weight(t);
            for (Map.Entry<String, Anim.Bone> en : a.anim.bones(a.participant).entrySet()) {
                ModelPart part = part(m, en.getKey());
                if (part == null) {
                    continue;
                }
                Anim.Bone bone = en.getValue();
                if (bone.rotation != null) {
                    float[] r = bone.rotation.sample(t);
                    part.xRot = Mth.lerp(w, part.xRot, r[0] * Mth.DEG_TO_RAD);
                    part.yRot = Mth.lerp(w, part.yRot, r[1] * Mth.DEG_TO_RAD);
                    part.zRot = Mth.lerp(w, part.zRot, r[2] * Mth.DEG_TO_RAD);
                }
                if (bone.position != null) {
                    float[] p = bone.position.sample(t);
                    part.x -= p[0] * w;
                    part.y -= p[1] * w;
                    part.z -= p[2] * w;
                }
            }
            changed = true;
        }

        // ---- de la mano / cargar
        LinkState l = (a != null && a.anchored) ? null : LINKS.get(e.getId());
        if (l != null) {
            Entity other = e.level().getEntity(l.a == e.getId() ? l.b : l.a);
            boolean carrier = l.a == e.getId();
            if (l.mode == Catalog.LINK_HANDS) {
                if (other instanceof LivingEntity o) {
                    Vec3 me = e.getPosition(pt);
                    Vec3 them = o.getPosition(pt);
                    double dist = me.distanceTo(them);
                    float w = Mth.clamp((float) (2.1 - dist) / 0.5F, 0, 1);
                    if (w > 0) {
                        // las dos manos se encuentran a mitad de camino, a la altura de la cadera
                        float[] mid = worldToModel(e, (me.x + them.x) / 2, (me.y + them.y) / 2 + 0.78, (me.z + them.z) / 2, pt);
                        ModelPart arm = mid[0] >= 0 ? m.leftArm : m.rightArm;
                        aim(arm, mid[0], mid[1], mid[2] - 0.5F, w);
                        changed = true;
                    }
                }
            } else if (carrier) {
                switch (l.mode) {
                    case Catalog.LINK_ARMS -> {
                        set(m.rightArm, -1.05F, -0.15F, 0.05F);
                        set(m.leftArm, -1.05F, 0.15F, -0.05F);
                    }
                    case Catalog.LINK_BACK -> {
                        set(m.rightArm, 0.35F, 0, 0.3F);
                        set(m.leftArm, 0.35F, 0, -0.3F);
                    }
                    default -> {
                        set(m.rightArm, -2.5F, 0, -0.18F);
                        set(m.leftArm, -2.5F, 0, 0.18F);
                    }
                }
                changed = true;
            } else if (e.getVehicle() != null) {
                // va cargado: nada de piernas de "sentado en un caballo"
                m.body.y = 0;
                m.body.z = 0;
                set(m.body, 0, 0, 0);
                m.head.y = 0;
                m.head.z = 0;
                m.rightLeg.y = 12;
                m.rightLeg.z = 0;
                m.leftLeg.y = 12;
                m.leftLeg.z = 0;
                m.rightArm.y = 2;
                m.rightArm.z = 0;
                m.leftArm.y = 2;
                m.leftArm.z = 0;
                switch (l.mode) {
                    case Catalog.LINK_ARMS -> {
                        set(m.rightLeg, -0.44F, 0, 0.04F);
                        set(m.leftLeg, -0.5F, 0, -0.04F);
                        set(m.leftArm, -0.8F, 0, -0.6F);
                        set(m.rightArm, -0.55F, 0.2F, -0.35F);
                        set(m.head, -0.25F, -0.4F, 0);
                    }
                    case Catalog.LINK_BACK -> {
                        set(m.rightLeg, -1.25F, 0, 0.35F);
                        set(m.leftLeg, -1.25F, 0, -0.35F);
                        set(m.rightArm, -1.15F, 0, -0.3F);
                        set(m.leftArm, -1.15F, 0, 0.3F);
                        m.head.xRot += 0.1F;
                    }
                    default -> {
                        // a upa en los hombros: piernas colgando por delante, manos en la cabeza del que carga
                        set(m.rightLeg, -1.35F, 0.12F, 0.1F);
                        set(m.leftLeg, -1.35F, -0.12F, -0.1F);
                        set(m.rightArm, -0.7F, 0.25F, 0);
                        set(m.leftArm, -0.7F, -0.25F, 0);
                    }
                }
                changed = true;
            }
        }

        // ---- modo toque
        long now = Util.getMillis();
        TouchView tv = TOUCH_TARGET.get(e.getId());
        if (tv != null) {
            float dt = tv.last == 0 ? 0 : Mth.clamp((now - tv.last) / 1000F, 0, 0.1F);
            if (tv.last != now) {
                tv.last = now;
                Net.TouchPose p = tv.pose;
                boolean on = tv.active;
                tv.headX = approach(tv.headX, on ? p.headX : 0, dt, 0.09F);
                tv.headY = approach(tv.headY, on ? p.headY : 0, dt, 0.09F);
                tv.headZ = approach(tv.headZ, on ? p.headZ : 0, dt, 0.09F);
                tv.wiggle = approach(tv.wiggle, on ? p.wiggle : 0, dt, 0.12F);
                boolean gr = on && (p.arms & 1) != 0;
                boolean gl = on && (p.arms & 2) != 0;
                tv.wR = approach(tv.wR, gr ? 1 : 0, dt, gr ? 0.06F : 0.22F);
                tv.wL = approach(tv.wL, gl ? 1 : 0, dt, gl ? 0.06F : 0.22F);
                if (gr) {
                    tv.r[0] = approach(tv.r[0], p.rx, dt, 0.05F);
                    tv.r[1] = approach(tv.r[1], p.ry, dt, 0.05F);
                    tv.r[2] = approach(tv.r[2], p.rz, dt, 0.05F);
                }
                if (gl) {
                    tv.l[0] = approach(tv.l[0], p.lx, dt, 0.05F);
                    tv.l[1] = approach(tv.l[1], p.ly, dt, 0.05F);
                    tv.l[2] = approach(tv.l[2], p.lz, dt, 0.05F);
                }
                tv.wToucher = approach(tv.wToucher, on && p.touching ? 1 : 0, dt, 0.1F);
                if (on && p.touching) {
                    tv.hx = p.hx;
                    tv.hy = p.hy;
                    tv.hz = p.hz;
                }
            }
            addOnce(m.head, tv.headX, tv.headY, tv.headZ);
            if (tv.wiggle > 0.01F) {
                float wig = Mth.sin(now / 38F) * 0.14F * tv.wiggle;
                addOnce(m.body, 0, 0, wig);
            }
            // el brazo agarrado sigue a la mano; al soltarlo vuelve solo a su lugar
            aim(m.rightArm, tv.r[0], tv.r[1], tv.r[2], Mth.clamp(tv.wR, 0, 1));
            aim(m.leftArm, tv.l[0], tv.l[1], tv.l[2], Mth.clamp(tv.wL, 0, 1));
            changed = true;
        }
        TouchView mine = TOUCH_TOUCHER.get(e.getId());
        if (mine != null && mine.wToucher > 0.01F) {
            // quien toca estira el brazo hasta donde esta tocando
            float[] h = worldToModel(e, mine.hx, mine.hy, mine.hz, pt);
            aim(m.rightArm, h[0], h[1], h[2], mine.wToucher);
            changed = true;
        }

        if (changed) {
            sync(m);
        }
    }

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

    /** El modelo de los aldeanos del fork de MCA tiene su propio "syncDerivedParts" (ropa y pelo). */
    private static Method findSync(Class<?> c) {
        while (c != null && c != Object.class) {
            try {
                Method method = c.getDeclaredMethod("syncDerivedParts");
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ex) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
