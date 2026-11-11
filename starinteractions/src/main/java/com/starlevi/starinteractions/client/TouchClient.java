package com.starlevi.starinteractions.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.starlevi.starinteractions.net.Net;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Modo toque, del lado de quien toca: sabe donde esta cada parte del cuerpo en la pantalla (la captura mientras
 * se dibuja al personaje) y convierte el cursor en un rayo para ver que se esta tocando: la parte de arriba de la
 * cabeza (acariciar), la mejilla, la nariz, un brazo (agarrarlo y moverlo) o el cuerpo (cosquillas).
 */
public final class TouchClient {
    public enum Zone { NONE, HEAD, CHEEK, NOSE, RIGHT_ARM, LEFT_ARM, BODY }

    /** Resultado de apuntar con el cursor. */
    public static final class Hit {
        public Zone zone = Zone.NONE;
        /** punto tocado, en la vista de la camara. */
        public final Vector3f view = new Vector3f();
        /** punto tocado, en el mundo. */
        public Vec3 world = Vec3.ZERO;
        /** de que lado de la cara (para la mejilla): +1 su izquierda, -1 su derecha. */
        public int side = 1;
    }

    static int targetId = -1;
    static long openedAt;

    // lo que se captura al dibujar al personaje
    private static final Matrix4f ROOT = new Matrix4f();
    private static final Matrix4f[] PARTS = {new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f()};
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW_ROT = new Matrix4f();
    private static Vec3 cameraPos = Vec3.ZERO;
    private static long capturedAt;

    // cajas de cada parte en px: cabeza, cuerpo, brazo derecho, brazo izquierdo
    private static final float[][] BOXES = {
            {-4.5F, -8.5F, -4.5F, 4.5F, 0.3F, 4.5F},
            {-4.3F, 0, -2.5F, 4.3F, 12, 2.5F},
            {-3.6F, -2.4F, -2.6F, 1.6F, 10.6F, 2.6F},
            {-1.6F, -2.4F, -2.6F, 3.6F, 10.6F, 2.6F},
    };

    private TouchClient() {
    }

    public static boolean open() {
        return targetId >= 0 && Minecraft.getInstance().screen instanceof TouchScreen;
    }

    /** Pide empezar el modo toque con esa entidad (lo llama la tecla y el menu de los aldeanos del fork de MCA). */
    public static void request(int entityId) {
        Net.toServer(new Net.Request(entityId, "touch"));
    }

    static boolean captured() {
        return Util.getMillis() - capturedAt < 250;
    }

    /** Mientras se dibuja al personaje que se esta tocando: guarda donde quedo cada parte. */
    static void capture(PoseStack ps, HumanoidModel<?> model) {
        ROOT.set(ps.last().pose());
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm};
        for (int i = 0; i < parts.length; i++) {
            ps.pushPose();
            parts[i].translateAndRotate(ps);
            PARTS[i].set(ps.last().pose());
            ps.popPose();
        }
        PROJECTION.set(RenderSystem.getProjectionMatrix());
        capturedAt = Util.getMillis();
    }

    static void captureCamera(Matrix4f viewRotation, Vec3 camera) {
        VIEW_ROT.set(viewRotation);
        cameraPos = camera;
    }

    /** Rayo del cursor en la vista de la camara: origen y direccion. */
    private static Vector3f[] ray(double mouseX, double mouseY, int width, int height) {
        float nx = (float) (2 * mouseX / width - 1);
        float ny = (float) (1 - 2 * mouseY / height);
        Matrix4f inv = new Matrix4f(PROJECTION).invert();
        Vector3f near = inv.transformProject(new Vector3f(nx, ny, -1));
        Vector3f far = inv.transformProject(new Vector3f(nx, ny, 1));
        Vector3f dir = far.sub(near, new Vector3f()).normalize();
        return new Vector3f[]{near, dir};
    }

    private static Vec3 toWorld(Vector3f view) {
        Vector3f w = new Matrix4f(VIEW_ROT).invert().transformDirection(new Vector3f(view));
        return cameraPos.add(w.x, w.y, w.z);
    }

    /** Que parte del cuerpo esta debajo del cursor. */
    static Hit pick(double mouseX, double mouseY, int width, int height) {
        Hit hit = new Hit();
        if (!captured()) {
            return hit;
        }
        Vector3f[] ray = ray(mouseX, mouseY, width, height);
        float best = Float.MAX_VALUE;
        int bestPart = -1;
        Vector3f bestLocal = new Vector3f();
        for (int i = 0; i < PARTS.length; i++) {
            Matrix4f inv = new Matrix4f(PARTS[i]).invert();
            Vector3f o = inv.transformPosition(new Vector3f(ray[0]));
            Vector3f d = inv.transformDirection(new Vector3f(ray[1]));
            float[] b = BOXES[i];
            float t = slab(o, d, b[0] / 16F, b[1] / 16F, b[2] / 16F, b[3] / 16F, b[4] / 16F, b[5] / 16F);
            if (t >= 0 && t < best) {
                best = t;
                bestPart = i;
                bestLocal.set(o).add(new Vector3f(d).mul(t)).mul(16F);
            }
        }
        if (bestPart < 0) {
            return hit;
        }
        hit.view.set(ray[0]).add(new Vector3f(ray[1]).mul(best));
        hit.world = toWorld(hit.view);
        switch (bestPart) {
            case 0 -> {
                if (bestLocal.y < -5.4F) {
                    hit.zone = Zone.HEAD;
                } else if (bestLocal.z < -3.4F && Math.abs(bestLocal.x) < 1.5F && bestLocal.y > -4.6F && bestLocal.y < -2.0F) {
                    hit.zone = Zone.NOSE;
                } else {
                    hit.zone = Zone.CHEEK;
                    hit.side = bestLocal.x >= 0 ? 1 : -1;
                }
            }
            case 1 -> hit.zone = Zone.BODY;
            case 2 -> hit.zone = Zone.RIGHT_ARM;
            default -> hit.zone = Zone.LEFT_ARM;
        }
        return hit;
    }

    private static float slab(Vector3f o, Vector3f d, float x0, float y0, float z0, float x1, float y1, float z1) {
        float tmin = 0, tmax = Float.MAX_VALUE;
        float[] oo = {o.x, o.y, o.z}, dd = {d.x, d.y, d.z}, lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dd[i]) < 1.0E-7F) {
                if (oo[i] < lo[i] || oo[i] > hi[i]) {
                    return -1;
                }
            } else {
                float t1 = (lo[i] - oo[i]) / dd[i];
                float t2 = (hi[i] - oo[i]) / dd[i];
                if (t1 > t2) {
                    float tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) {
                    return -1;
                }
            }
        }
        return tmin;
    }

    /**
     * Mientras se arrastra un brazo: el punto del cursor a la misma profundidad que el punto agarrado, en px del
     * modelo (para apuntar el brazo ahi) y en el mundo (para la mano de quien toca).
     */
    static float[] dragPoint(double mouseX, double mouseY, int width, int height, float viewDepth, Vec3[] worldOut) {
        Vector3f[] ray = ray(mouseX, mouseY, width, height);
        float dz = ray[1].z;
        if (Math.abs(dz) < 1.0E-5F) {
            return null;
        }
        float t = (viewDepth - ray[0].z) / dz;
        Vector3f view = new Vector3f(ray[0]).add(new Vector3f(ray[1]).mul(t));
        worldOut[0] = toWorld(view);
        Vector3f model = new Matrix4f(ROOT).invert().transformPosition(new Vector3f(view)).mul(16F);
        return new float[]{model.x, model.y, model.z};
    }
}
