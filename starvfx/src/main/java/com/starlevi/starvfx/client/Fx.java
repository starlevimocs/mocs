package com.starlevi.starvfx.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starlevi.starvfx.Geo;
import com.starlevi.starvfx.StarVfx;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Los efectos del mundo: la hoja gigante, grietas en el cielo y en el suelo, anomalias (agujero negro, planeta,
 * galaxia, ondas en el cielo), onda de choque, pilar de luz, remolino de hojas y orbes.
 * <p>
 * Todo es geometria con texturas, dibujada con los mismos tipos que usan las entidades del juego (las partes
 * que brillan, como los ojos de una arana): asi se ve tambien con shaders de Oculus puestos.
 */
public final class Fx {
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final Map<String, RenderType> GLOW_TYPES = new java.util.HashMap<>();
    private static final Map<String, RenderType> SOLID_TYPES = new java.util.HashMap<>();
    private static final Map<String, RenderType> CUTOUT_TYPES = new java.util.HashMap<>();

    /** Efectos puestos en el mundo fuera de una escena (por nombre). */
    static final Map<String, Effect> WORLD = new LinkedHashMap<>();

    private Fx() {
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(StarVfx.MOD_ID, "textures/fx/" + name + ".png");
    }

    /** Brillo (suma luz, no tapa). */
    static RenderType glow(String name) {
        return GLOW_TYPES.computeIfAbsent(name, n -> RenderType.eyes(tex(n)));
    }

    /** Con transparencia (para lo oscuro y lo que se desvanece). */
    static RenderType solid(String name) {
        return SOLID_TYPES.computeIfAbsent(name, n -> RenderType.entityTranslucent(tex(n)));
    }

    /** Recortado (hojitas). */
    static RenderType cutout(String name) {
        return CUTOUT_TYPES.computeIfAbsent(name, n -> RenderType.entityCutoutNoCull(tex(n)));
    }

    // ================================================================ contexto de dibujo

    /** Lo que hace falta para dibujar en un cuadro. */
    static final class Ctx {
        PoseStack ps;
        MultiBufferSource buffers;
        Vec3 cam;
        /** hasta donde se puede dibujar sin que lo tape la niebla */
        double far;
        Matrix4f pose;
        Matrix3f normal;

        /** vertice en coordenadas del mundo */
        void v(VertexConsumer vc, double x, double y, double z, float u, float v, int r, int g, int b, int a) {
            vc.vertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).color(r, g, b, a).uv(u, v)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LIGHT).normal(normal, 0, 1, 0).endVertex();
        }

        /** cuadrilatero de 4 puntos del mundo; {@code both} = tambien la cara de atras */
        void quad(VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float v0, float u1, float v1, int r, int g, int bl, int al, boolean both) {
            v(vc, a.x, a.y, a.z, u0, v0, r, g, bl, al);
            v(vc, b.x, b.y, b.z, u1, v0, r, g, bl, al);
            v(vc, c.x, c.y, c.z, u1, v1, r, g, bl, al);
            v(vc, d.x, d.y, d.z, u0, v1, r, g, bl, al);
            if (both) {
                v(vc, d.x, d.y, d.z, u0, v1, r, g, bl, al);
                v(vc, c.x, c.y, c.z, u1, v1, r, g, bl, al);
                v(vc, b.x, b.y, b.z, u1, v0, r, g, bl, al);
                v(vc, a.x, a.y, a.z, u0, v0, r, g, bl, al);
            }
        }

        /** Cartel que mira a la camara. {@code sx, sy} = medio ancho y medio alto; {@code roll} en radianes. */
        void billboard(RenderType type, Vec3 at, float sx, float sy, float roll, int r, int g, int b, int a) {
            Vec3 f = cam.subtract(at);
            if (f.lengthSqr() < 1.0E-6) {
                return;
            }
            f = f.normalize();
            Vec3 right = Math.abs(f.y) > 0.999 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0).cross(f).normalize();
            Vec3 up = f.cross(right);
            double c = Math.cos(roll), s = Math.sin(roll);
            Vec3 rx = right.scale(c).add(up.scale(s)).scale(sx);
            Vec3 uy = up.scale(c).subtract(right.scale(s)).scale(sy);
            quad(buffers.getBuffer(type), at.subtract(rx).subtract(uy), at.add(rx).subtract(uy), at.add(rx).add(uy), at.subtract(rx).add(uy),
                    0, 1, 1, 0, r, g, b, a, true);
        }

        /** Tramo de linea ancha entre dos puntos, de cara a la camara. */
        void ribbon(VertexConsumer vc, Vec3 a, Vec3 b, float width, int r, int g, int bl, int al) {
            Vec3 dir = b.subtract(a);
            Vec3 toCam = cam.subtract(a.add(b).scale(0.5));
            Vec3 side = dir.cross(toCam);
            if (side.lengthSqr() < 1.0E-8) {
                return;
            }
            side = side.normalize().scale(width * 0.5);
            quad(vc, a.subtract(side), b.subtract(side), b.add(side), a.add(side), 0, 0, 1, 1, r, g, bl, al, true);
        }
    }

    // ================================================================ base

    public abstract static class Effect {
        String type;
        Vec3 pos;
        JsonObject p;
        long startMs;
        /** segundos; 0 = hasta que lo saquen */
        float duration;
        float size;
        int color;
        float fadeIn;
        float fadeOut;
        long seed;
        private int lastTick = -1;

        void init() {
        }

        float age() {
            return (Util.getMillis() - startMs) / 1000F;
        }

        boolean done() {
            return duration > 0 && age() >= duration;
        }

        /** 0..1: aparece al principio y se va al final */
        float envelope(float age) {
            float e = fadeIn <= 0 ? 1 : Mth.clamp(age / fadeIn, 0, 1);
            if (duration > 0 && fadeOut > 0) {
                e *= Mth.clamp((duration - age) / fadeOut, 0, 1);
            }
            return e * e * (3 - 2 * e);
        }

        /** Donde esta ahora (la hoja se mueve; el resto se queda en su lugar). */
        public Vec3 position(float age) {
            return pos;
        }

        /** Punto de referencia para enganchar la camara (la hoja: su caida, sin el vaiven). */
        public Vec3 center(float age) {
            return position(age);
        }

        abstract void render(Ctx c, float age);

        /** 20 veces por segundo: particulas. */
        void tick(ClientLevel level, float age, Random random) {
        }

        int r(float k) {
            return Mth.clamp((int) (((color >> 16) & 255) * k), 0, 255);
        }

        int g(float k) {
            return Mth.clamp((int) (((color >> 8) & 255) * k), 0, 255);
        }

        int b(float k) {
            return Mth.clamp((int) ((color & 255) * k), 0, 255);
        }
    }

    /** Punto del cielo: si queda mas lejos de lo que se puede dibujar, se lo acerca achicandolo (se ve igual). */
    private static double[] place(Ctx c, Vec3 world) {
        Vec3 d = world.subtract(c.cam);
        double dist = d.length();
        if (dist <= c.far || dist < 1.0E-3) {
            return new double[]{world.x, world.y, world.z, 1};
        }
        double k = c.far / dist;
        return new double[]{c.cam.x + d.x * k, c.cam.y + d.y * k, c.cam.z + d.z * k, k};
    }

    public static Effect create(String type, Vec3 pos, JsonObject params, float elapsed) {
        Effect e = switch (type) {
            case "leaf" -> new Leaf();
            case "sky_crack" -> new SkyCrack();
            case "ground_crack" -> new GroundCrack();
            case "black_hole" -> new BlackHole();
            case "planet" -> new Planet();
            case "galaxy" -> new Galaxy();
            case "sky_rings" -> new SkyRings();
            case "shockwave" -> new Shockwave();
            case "pillar" -> new Pillar();
            case "swarm" -> new Swarm();
            case "glow" -> new Glow();
            default -> null;
        };
        if (e == null) {
            return null;
        }
        e.type = type;
        e.pos = pos;
        e.p = params;
        e.startMs = Util.getMillis() - (long) (elapsed * 1000);
        e.duration = Geo.num(params, "duration", 0);
        e.size = Geo.num(params, "size", -1);
        e.color = Geo.color(params, "color", 0xFFFFFF);
        e.fadeIn = Geo.num(params, "fade_in", 1);
        e.fadeOut = Geo.num(params, "fade_out", 1.5F);
        e.seed = (long) Geo.num(params, "seed", (float) (pos.x * 31 + pos.z * 17 + pos.y));
        e.init();
        return e;
    }

    // ================================================================ dibujo y tick de todos

    private static final Ctx CTX = new Ctx();
    private static final Random RANDOM = new Random();

    static void renderAll(PoseStack ps, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        boolean any = !WORLD.isEmpty() || PlayerFx.hasWorldVisuals() || !Tree.TREES.isEmpty();
        for (ClientScene s : ClientScene.ACTIVE) {
            any |= !s.effects.isEmpty();
        }
        if (!any) {
            return;
        }
        CTX.ps = ps;
        CTX.buffers = mc.renderBuffers().bufferSource();
        CTX.cam = cam;
        CTX.far = Math.max(48, mc.options.getEffectiveRenderDistance() * 16 * 0.8);
        CTX.pose = ps.last().pose();
        CTX.normal = ps.last().normal();
        for (Effect e : WORLD.values()) {
            draw(e);
        }
        for (ClientScene s : ClientScene.ACTIVE) {
            for (Effect e : s.effects) {
                draw(e);
            }
        }
        try {
            Tree.renderAll(CTX);
        } catch (RuntimeException ex) {
            StarVfx.LOGGER.warn("[Star VFX] Fallo el dibujo del arbol", ex);
        }
        PlayerFx.renderWorld(CTX);
    }

    private static void draw(Effect e) {
        try {
            float age = e.age();
            if (age >= 0 && !e.done()) {
                e.render(CTX, age);
            }
        } catch (RuntimeException ex) {
            StarVfx.LOGGER.warn("[Star VFX] Fallo el efecto {}", e.type, ex);
            e.duration = 0.001F;
        }
    }

    static void tickAll(ClientLevel level) {
        for (Iterator<Effect> it = WORLD.values().iterator(); it.hasNext(); ) {
            Effect e = it.next();
            if (e.done()) {
                it.remove();
            } else {
                e.tick(level, e.age(), RANDOM);
            }
        }
        for (ClientScene s : ClientScene.ACTIVE) {
            for (Iterator<Effect> it = s.effects.iterator(); it.hasNext(); ) {
                Effect e = it.next();
                if (e.done()) {
                    it.remove();
                } else if (e.age() >= 0) {
                    e.tick(level, e.age(), RANDOM);
                }
            }
        }
    }

    private static void particle(ClientLevel level, ParticleOptions type, double x, double y, double z, double dx, double dy, double dz) {
        level.addParticle(type, true, x, y, z, dx, dy, dz);
    }

    private static double groundY(Vec3 at, double fallback) {
        ClientLevel level = Minecraft.getInstance().level;
        int bx = Mth.floor(at.x), bz = Mth.floor(at.z);
        if (level == null || !level.hasChunk(bx >> 4, bz >> 4)) {
            return fallback;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return Math.abs(y - fallback) > 6 ? fallback : y;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    // ================================================================ la hoja

    /**
     * Hoja gigante que cae planeando desde {@code fall} bloques de altura, se posa en el suelo, late y se deshace
     * en luz. Parametros: size (largo), fall, fall_time, hold, dissolve.
     */
    static final class Leaf extends Effect {
        private static final int NS = 30, NW = 10;
        float fall, fallTime, hold, dissolve, length;
        boolean landed;
        /** si cae desde el Arbol Madre: cuanto esta corrida al empezar respecto de donde se posa */
        double fromX, fromZ;
        boolean fromTree;

        @Override
        void init() {
            length = size > 0 ? size : 22;
            fall = Geo.num(p, "fall", 120);
            fallTime = Math.max(1, Geo.num(p, "fall_time", 26));
            hold = Geo.num(p, "hold", 2);
            dissolve = Math.max(0.5F, Geo.num(p, "dissolve", 3));
            duration = fallTime + hold + dissolve;
            if (color == 0xFFFFFF) {
                color = 0x9DFFB4;
            }
            if (Geo.bool(p, "tree", false)) {
                Vec3 start = Tree.leafStart(pos);
                if (start != null && start.y - pos.y > 8) {
                    fromTree = true;
                    fromX = start.x - pos.x;
                    fromZ = start.z - pos.z;
                    fall = (float) (start.y - pos.y);
                }
            }
        }

        /** Corrimiento horizontal: sale de la rama y va derivando hasta quedar sobre el lugar donde se posa. */
        private double drift(float u) {
            return Math.pow(1 - u, 1.7);
        }

        float u(float age) {
            return Mth.clamp(age / fallTime, 0, 1);
        }

        @Override
        public Vec3 position(float age) {
            float u = u(age);
            double y = fall * Math.pow(1 - u, 1.35);
            // recien desprendida casi no se hamaca; el vaiven crece mientras baja y se calma al llegar
            double amp = length * 0.85 * Math.pow(1 - u, 0.8) * (fromTree ? Math.min(1, u * 7) : 1);
            double ph = u * Math.PI * 2 * 4.5;
            double d = drift(u);
            return pos.add(fromX * d + Math.sin(ph) * amp, y + 0.12, fromZ * d + Math.sin(ph * 0.5 + 1.0) * amp * 0.55 * (1 - u));
        }

        @Override
        public Vec3 center(float age) {
            float u = u(age);
            double d = drift(u);
            return pos.add(fromX * d, fall * Math.pow(1 - u, 1.35) + 0.12, fromZ * d);
        }

        @Override
        void render(Ctx c, float age) {
            float u = u(age);
            float after = age - fallTime;
            float born = 0.04F + 0.96F * smooth(age / 1.6F);
            float flat = smooth(after / 1.2F);
            float gone = after <= hold ? 0 : Mth.clamp((after - hold) / dissolve, 0, 1);
            Vec3 at = position(age);
            double ph = u * Math.PI * 2 * 4.5;
            // se inclina hacia donde se hamaca y gira despacio; al posarse queda plana
            float roll = (float) (Math.cos(ph) * 0.55 * (1 - u));
            float pitch = (float) (Math.sin(ph * 0.5 + 0.4) * 0.3 * (1 - u)) - 0.12F * (1 - flat);
            float yaw = 0.5F + u * 3.6F;
            double cy = Math.cos(yaw), sy = Math.sin(yaw), cr = Math.cos(roll), sr = Math.sin(roll), cp = Math.cos(pitch), sp = Math.sin(pitch);

            float halfWidth = length * 0.27F;
            float pulse = 0.75F + 0.25F * Mth.sin(age * 2.4F);
            int alpha = (int) (255 * (1 - gone));
            float veins = (0.55F + 0.45F * pulse) * (1 - gone) + (after > 0 ? (1 - gone) * 0.6F * (1 - smooth(after / 3F)) : 0);
            VertexConsumer base = alpha > 2 ? c.buffers.getBuffer(solid("leaf")) : null;
            Vec3[][] grid = new Vec3[NS + 1][NW + 1];
            for (int i = 0; i <= NS; i++) {
                float s = i / (float) NS;
                float hw = halfWidth * (float) Math.pow(Math.sin(Math.PI * Math.pow(s, 0.85)), 0.8) * (1.05F - 0.25F * s);
                for (int j = 0; j <= NW; j++) {
                    float w = j / (float) NW * 2 - 1;
                    double x = w * hw * born;
                    double z = (s - 0.5) * length * born;
                    double curve = (1 - 0.8 * flat) * born;
                    double y = (w * w) * hw * 0.32 * curve + Math.sin(Math.PI * s) * length * 0.05 * curve
                            + Math.sin(s * 7 - age * 1.7 + w * 1.2) * length * 0.012 * (0.3 + Math.abs(w)) * (1 - 0.85 * flat);
                    // inclinacion (roll sobre el largo, pitch sobre el ancho) y giro
                    double x1 = x * cr - y * sr, y1 = x * sr + y * cr;
                    double y2 = y1 * cp - z * sp, z2 = y1 * sp + z * cp;
                    grid[i][j] = new Vec3(at.x + x1 * cy - z2 * sy, at.y + y2, at.z + x1 * sy + z2 * cy);
                }
            }
            int vr = r(veins), vg = g(veins), vb = b(veins);
            VertexConsumer glow = veins > 0.02F ? c.buffers.getBuffer(glow("leaf_veins")) : null;
            for (int i = 0; i < NS; i++) {
                float u0 = i / (float) NS, u1 = (i + 1) / (float) NS;
                for (int j = 0; j < NW; j++) {
                    float v0 = j / (float) NW, v1 = (j + 1) / (float) NW;
                    if (base != null) {
                        quad(c, base, grid[i][j], grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1], u0, v0, u1, v1, 255, 255, 255, alpha, false);
                    }
                }
            }
            if (glow != null) {
                for (int i = 0; i < NS; i++) {
                    float u0 = i / (float) NS, u1 = (i + 1) / (float) NS;
                    for (int j = 0; j < NW; j++) {
                        float v0 = j / (float) NW, v1 = (j + 1) / (float) NW;
                        quad(c, glow, lift(grid[i][j]), lift(grid[i + 1][j]), lift(grid[i + 1][j + 1]), lift(grid[i][j + 1]), u0, v0, u1, v1, vr, vg, vb, 255, true);
                    }
                }
            }
            // halo
            float halo = (0.35F + 0.15F * pulse) * (1 - gone * 0.5F);
            c.billboard(glow("glow"), at.add(0, 0.5, 0), length * 1.25F, length * 1.25F, 0, r(halo), g(halo), b(halo), 255);
            if (after > 0 && after < 1.2F) {
                float k = 1 - after / 1.2F;
                c.billboard(glow("glow"), at.add(0, 1, 0), length * (1.5F + after * 3), length * (1.5F + after * 3), 0, r(k), g(k), b(k), 255);
            }
        }

        private static Vec3 lift(Vec3 v) {
            return v.add(0, 0.03, 0);
        }

        /** cuadrilatero con los u/v de la malla (u a lo largo, v a lo ancho) */
        private static void quad(Ctx c, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 cc, Vec3 d, float u0, float v0, float u1, float v1,
                                 int r, int g, int bl, int al, boolean both) {
            c.v(vc, a.x, a.y, a.z, u0, v0, r, g, bl, al);
            c.v(vc, b.x, b.y, b.z, u1, v0, r, g, bl, al);
            c.v(vc, cc.x, cc.y, cc.z, u1, v1, r, g, bl, al);
            c.v(vc, d.x, d.y, d.z, u0, v1, r, g, bl, al);
            if (both) {
                c.v(vc, d.x, d.y, d.z, u0, v1, r, g, bl, al);
                c.v(vc, cc.x, cc.y, cc.z, u1, v1, r, g, bl, al);
                c.v(vc, b.x, b.y, b.z, u1, v0, r, g, bl, al);
                c.v(vc, a.x, a.y, a.z, u0, v0, r, g, bl, al);
            }
        }

        @Override
        void tick(ClientLevel level, float age, Random random) {
            Vec3 at = position(age);
            float after = age - fallTime;
            if (after < 0) {
                // estela de luz mientras cae
                for (int i = 0; i < 5; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * length * 0.45;
                    particle(level, i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.GLOW, at.x + Math.cos(a) * r, at.y + random.nextDouble() * 0.6,
                            at.z + Math.sin(a) * r, (random.nextDouble() - 0.5) * 0.02, 0.03 + random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.02);
                }
                return;
            }
            if (!landed) {
                landed = true;
                for (int i = 0; i < 160; i++) {
                    double a = random.nextDouble() * Math.PI * 2, sp = 0.25 + random.nextDouble() * 0.6;
                    particle(level, i % 3 == 0 ? ParticleTypes.END_ROD : (i % 3 == 1 ? ParticleTypes.COMPOSTER : ParticleTypes.CLOUD),
                            pos.x + Math.cos(a) * 1.5, pos.y + 0.3, pos.z + Math.sin(a) * 1.5, Math.cos(a) * sp, 0.04 + random.nextDouble() * 0.12, Math.sin(a) * sp);
                }
            }
            int n = after > hold ? 26 : 5;
            for (int i = 0; i < n; i++) {
                double s = random.nextDouble(), w = random.nextDouble() * 2 - 1;
                double hw = length * 0.27 * Math.sin(Math.PI * s);
                double yaw = 0.5 + 3.6;
                double x = w * hw, z = (s - 0.5) * length;
                particle(level, switch (i % 4) {
                            case 0 -> ParticleTypes.END_ROD;
                            case 1 -> ParticleTypes.GLOW;
                            case 2 -> ParticleTypes.HAPPY_VILLAGER;
                            default -> ParticleTypes.COMPOSTER;
                        }, pos.x + x * Math.cos(yaw) - z * Math.sin(yaw), pos.y + 0.25, pos.z + x * Math.sin(yaw) + z * Math.cos(yaw),
                        (random.nextDouble() - 0.5) * 0.03, 0.05 + random.nextDouble() * 0.16, (random.nextDouble() - 0.5) * 0.03);
            }
        }
    }

    // ================================================================ grietas

    private record Seg(float x0, float y0, float x1, float y1, float width, float order) {
    }

    /** Rayo quebrado con ramas, en un plano (coordenadas -0.5..0.5). {@code order}: cuando aparece cada tramo (0..1). */
    private static void branch(List<Seg> out, Random rnd, float x, float y, float angle, float len, float width, float order, int depth) {
        int steps = Math.max(3, (int) (len * 22));
        float step = len / steps;
        for (int i = 0; i < steps; i++) {
            float a = angle + (rnd.nextFloat() - 0.5F) * 1.1F;
            float nx = x + Mth.cos(a) * step, ny = y + Mth.sin(a) * step;
            float w = width * (1 - 0.6F * i / steps);
            float o = order + (1 - order) * (i / (float) steps) * (depth == 0 ? 1 : 0.6F);
            out.add(new Seg(x, y, nx, ny, w, Math.min(1, o)));
            if (depth < 2 && rnd.nextFloat() < (depth == 0 ? 0.42F : 0.25F)) {
                float side = rnd.nextBoolean() ? 1 : -1;
                branch(out, rnd, nx, ny, a + side * (0.5F + rnd.nextFloat() * 0.8F), len * (0.25F + rnd.nextFloat() * 0.3F), w * 0.6F, o, depth + 1);
            }
            x = nx;
            y = ny;
        }
    }

    /**
     * Grieta en el cielo: una herida oscura con el borde encendido que se va abriendo. Parametros: size (ancho en
     * bloques), grow (segundos en abrirse), color.
     */
    static final class SkyCrack extends Effect {
        final List<Seg> segs = new ArrayList<>();
        float grow;

        @Override
        void init() {
            if (size <= 0) {
                size = 60;
            }
            grow = Math.max(0.1F, Geo.num(p, "grow", 3));
            if (color == 0xFFFFFF) {
                color = 0xB9A2FF;
            }
            Random rnd = new Random(seed);
            float tilt = (rnd.nextFloat() - 0.5F) * 0.5F;
            // dos brazos desde el centro
            branch(segs, rnd, 0, 0, tilt, 0.5F, 1, 0, 0);
            branch(segs, rnd, 0, 0, tilt + Mth.PI, 0.5F, 1, 0, 0);
        }

        @Override
        void render(Ctx c, float age) {
            double[] pl = place(c, pos);
            Vec3 at = new Vec3(pl[0], pl[1], pl[2]);
            float scale = (float) (size * pl[3]);
            float env = envelope(age);
            float open = duration > 0 ? Math.min(age / grow, Math.max(0, (duration - age) / Math.max(0.1F, fadeOut))) : age / grow;
            open = Mth.clamp(open, 0, 1);
            Vec3 f = c.cam.subtract(at).normalize();
            Vec3 right = Math.abs(f.y) > 0.999 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0).cross(f).normalize();
            Vec3 up = f.cross(right);
            float flicker = 0.8F + 0.2F * Mth.sin(age * 9) * Mth.sin(age * 3.7F);
            float k = env * flicker;
            int r = r(k), g = g(k), b = b(k);
            VertexConsumer glowBuf = c.buffers.getBuffer(glow("line"));
            for (Seg s : segs) {
                if (s.order > open) {
                    continue;
                }
                Vec3 a = at.add(right.scale(s.x0 * scale)).add(up.scale(s.y0 * scale));
                Vec3 bb = at.add(right.scale(s.x1 * scale)).add(up.scale(s.y1 * scale));
                c.ribbon(glowBuf, a, bb, scale * 0.07F * s.width + scale * 0.012F, r, g, b, 255);
            }
            VertexConsumer core = c.buffers.getBuffer(solid("void_line"));
            Vec3 near = f.scale(scale * 0.004 + 0.05);
            int ca = (int) (255 * env);
            for (Seg s : segs) {
                if (s.order > open) {
                    continue;
                }
                Vec3 a = at.add(right.scale(s.x0 * scale)).add(up.scale(s.y0 * scale)).add(near);
                Vec3 bb = at.add(right.scale(s.x1 * scale)).add(up.scale(s.y1 * scale)).add(near);
                c.ribbon(core, a, bb, scale * 0.022F * s.width + scale * 0.003F, 255, 255, 255, ca);
            }
            float halo = 0.22F * env * (0.8F + 0.2F * Mth.sin(age * 1.3F)) * open;
            c.billboard(glow("glow"), at.subtract(f.scale(0.2)), scale * 0.8F, scale * 0.45F, 0, r(halo), g(halo), b(halo), 255);
        }

        @Override
        void tick(ClientLevel level, float age, Random random) {
            if (random.nextInt(3) == 0 && !segs.isEmpty() && pos.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()) < 160 * 160) {
                Seg s = segs.get(random.nextInt(segs.size()));
                particle(level, ParticleTypes.END_ROD, pos.x + s.x0 * size, pos.y + s.y0 * size * 0.3, pos.z + (random.nextDouble() - 0.5) * 2,
                        0, -0.05 - random.nextDouble() * 0.1, 0);
            }
        }
    }

    /**
     * Grietas en el suelo que salen desde un punto, siguiendo el terreno, con luz que sube. Parametros: size
     * (radio), grow, color.
     */
    static final class GroundCrack extends Effect {
        private record Line(Vec3 a, Vec3 b, float width, float order) {
        }

        final List<Line> lines = new ArrayList<>();
        float grow;

        @Override
        void init() {
            if (size <= 0) {
                size = 10;
            }
            grow = Math.max(0.1F, Geo.num(p, "grow", 1.5F));
            if (color == 0xFFFFFF) {
                color = 0x8DFFB0;
            }
            Random rnd = new Random(seed);
            List<Seg> segs = new ArrayList<>();
            int arms = 7;
            for (int i = 0; i < arms; i++) {
                branch(segs, rnd, 0, 0, (float) (Math.PI * 2 * i / arms) + (rnd.nextFloat() - 0.5F) * 0.5F, 0.5F * (0.7F + rnd.nextFloat() * 0.3F), 1, 0, 0);
            }
            for (Seg s : segs) {
                Vec3 a = new Vec3(pos.x + s.x0 * size * 2, 0, pos.z + s.y0 * size * 2);
                Vec3 b = new Vec3(pos.x + s.x1 * size * 2, 0, pos.z + s.y1 * size * 2);
                lines.add(new Line(new Vec3(a.x, groundY(a, pos.y) + 0.03, a.z), new Vec3(b.x, groundY(b, pos.y) + 0.03, b.z), s.width, s.order));
            }
        }

        private static void flat(Ctx c, VertexConsumer vc, Vec3 a, Vec3 b, float width, double lift, int r, int g, int bl, int al) {
            Vec3 d = b.subtract(a);
            Vec3 side = new Vec3(-d.z, 0, d.x);
            if (side.lengthSqr() < 1.0E-8) {
                return;
            }
            side = side.normalize().scale(width * 0.5);
            c.quad(vc, a.subtract(side).add(0, lift, 0), b.subtract(side).add(0, lift, 0), b.add(side).add(0, lift, 0), a.add(side).add(0, lift, 0),
                    0, 0, 1, 1, r, g, bl, al, true);
        }

        @Override
        void render(Ctx c, float age) {
            float env = envelope(age);
            float open = Mth.clamp(age / grow, 0, 1);
            float k = env * (0.75F + 0.25F * Mth.sin(age * 3));
            int r = r(k), g = g(k), b = b(k);
            VertexConsumer glowBuf = c.buffers.getBuffer(glow("line"));
            for (Line l : lines) {
                if (l.order <= open) {
                    flat(c, glowBuf, l.a, l.b, 0.5F + 0.9F * l.width, 0, r, g, b, 255);
                }
            }
            VertexConsumer core = c.buffers.getBuffer(solid("void_line"));
            int ca = (int) (255 * env);
            for (Line l : lines) {
                if (l.order <= open) {
                    flat(c, core, l.a, l.b, 0.1F + 0.28F * l.width, 0.012, 255, 255, 255, ca);
                }
            }
        }

        @Override
        void tick(ClientLevel level, float age, Random random) {
            float open = Mth.clamp(age / grow, 0, 1);
            for (int i = 0; i < 3 && !lines.isEmpty(); i++) {
                Line l = lines.get(random.nextInt(lines.size()));
                if (l.order <= open) {
                    particle(level, random.nextBoolean() ? ParticleTypes.GLOW : ParticleTypes.END_ROD, l.a.x, l.a.y + 0.1, l.a.z,
                            (random.nextDouble() - 0.5) * 0.02, 0.03 + random.nextDouble() * 0.06, (random.nextDouble() - 0.5) * 0.02);
                }
            }
        }
    }

    // ================================================================ anomalias del cielo

    /** Agujero negro: disco negro, anillo de fuego que gira y halo. size = diametro. */
    static final class BlackHole extends Effect {
        @Override
        void init() {
            if (size <= 0) {
                size = 50;
            }
            if (color == 0xFFFFFF) {
                color = 0xFFD9A0;
            }
        }

        @Override
        void render(Ctx c, float age) {
            double[] pl = place(c, pos);
            Vec3 at = new Vec3(pl[0], pl[1], pl[2]);
            float s = (float) (size * 0.5 * pl[3]) * (0.2F + 0.8F * smooth(age / Math.max(0.1F, fadeIn)));
            float env = envelope(age);
            Vec3 f = c.cam.subtract(at).normalize();
            c.billboard(glow("accretion"), at, s, s, age * 0.25F, r(env), g(env), b(env), 255);
            c.billboard(glow("accretion"), at.subtract(f.scale(0.05)), s * 1.25F, s * 1.25F, -age * 0.11F, r(env * 0.4F), g(env * 0.4F), b(env * 0.4F), 255);
            c.billboard(solid("void"), at.add(f.scale(0.1 + s * 0.002)), s, s, 0, 255, 255, 255, (int) (255 * env));
            float halo = env * (0.3F + 0.08F * Mth.sin(age * 1.7F));
            c.billboard(glow("glow"), at.subtract(f.scale(0.1)), s * 2.2F, s * 2.2F, 0, r(halo), g(halo), b(halo), 255);
        }
    }

    /** Planeta que asoma en el cielo. size = diametro. */
    static final class Planet extends Effect {
        @Override
        void init() {
            if (size <= 0) {
                size = 80;
            }
        }

        @Override
        void render(Ctx c, float age) {
            double[] pl = place(c, pos);
            float rad = (float) (size * 0.5 * pl[3]);
            int a = (int) (255 * envelope(age));
            if (a < 3) {
                return;
            }
            VertexConsumer vc = c.buffers.getBuffer(solid("planet"));
            int lat = 14, lon = 24;
            float spin = age * 0.02F;
            for (int i = 0; i < lat; i++) {
                double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
                for (int j = 0; j < lon; j++) {
                    double p0 = Math.PI * 2 * j / lon + spin, p1 = Math.PI * 2 * (j + 1) / lon + spin;
                    float u0 = j / (float) lon, u1 = (j + 1) / (float) lon, v0 = i / (float) lat, v1 = (i + 1) / (float) lat;
                    sph(c, vc, pl, rad, t0, p0, u0, v0, a);
                    sph(c, vc, pl, rad, t1, p0, u0, v1, a);
                    sph(c, vc, pl, rad, t1, p1, u1, v1, a);
                    sph(c, vc, pl, rad, t0, p1, u1, v0, a);
                }
            }
            float halo = 0.25F * envelope(age);
            Vec3 at = new Vec3(pl[0], pl[1], pl[2]);
            c.billboard(glow("glow"), at.subtract(c.cam.subtract(at).normalize().scale(rad)), rad * 2.4F, rad * 2.4F, 0, r(halo), g(halo), b(halo), 255);
        }

        private static void sph(Ctx c, VertexConsumer vc, double[] pl, float rad, double theta, double phi, float u, float v, int a) {
            double st = Math.sin(theta);
            float nx = (float) (st * Math.cos(phi)), ny = (float) Math.cos(theta), nz = (float) (st * Math.sin(phi));
            // sombreado propio: mas claro arriba, como iluminado por el cielo
            int shade = (int) (255 * Mth.clamp(0.45F + 0.55F * (ny * 0.6F + nx * 0.5F + 0.3F), 0.2F, 1));
            vc.vertex(c.pose, (float) (pl[0] + nx * rad - c.cam.x), (float) (pl[1] + ny * rad - c.cam.y), (float) (pl[2] + nz * rad - c.cam.z))
                    .color(shade, shade, shade, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LIGHT).normal(c.normal, 0, 1, 0).endVertex();
        }
    }

    /** Galaxia en espiral que gira muy despacio. size = diametro. */
    static final class Galaxy extends Effect {
        @Override
        void init() {
            if (size <= 0) {
                size = 90;
            }
        }

        @Override
        void render(Ctx c, float age) {
            double[] pl = place(c, pos);
            Vec3 at = new Vec3(pl[0], pl[1], pl[2]);
            float s = (float) (size * 0.5 * pl[3]);
            float env = envelope(age);
            // inclinada: se ve como un ovalo
            Vec3 f = c.cam.subtract(at).normalize();
            Vec3 right = Math.abs(f.y) > 0.999 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0).cross(f).normalize();
            Vec3 up = f.cross(right);
            double tilt = 0.35;
            Vec3 ax = right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
            Vec3 ay = up.scale(Math.cos(tilt)).subtract(right.scale(Math.sin(tilt))).scale(0.62);
            VertexConsumer vc = c.buffers.getBuffer(glow("galaxy"));
            int n = 20;
            float spin = age * 0.03F;
            int r = r(env), g = g(env), b = b(env);
            // disco en abanico para poder girar la textura sin girar el ovalo
            for (int i = 0; i < n; i++) {
                double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                Vec3 p0 = at.add(ax.scale(Math.cos(a0) * s)).add(ay.scale(Math.sin(a0) * s));
                Vec3 p1 = at.add(ax.scale(Math.cos(a1) * s)).add(ay.scale(Math.sin(a1) * s));
                float u0 = 0.5F + 0.5F * Mth.cos((float) a0 + spin), v0 = 0.5F + 0.5F * Mth.sin((float) a0 + spin);
                float u1 = 0.5F + 0.5F * Mth.cos((float) a1 + spin), v1 = 0.5F + 0.5F * Mth.sin((float) a1 + spin);
                for (int side = 0; side < 2; side++) {
                    if (side == 0) {
                        c.v(vc, at.x, at.y, at.z, 0.5F, 0.5F, r, g, b, 255);
                        c.v(vc, p0.x, p0.y, p0.z, u0, v0, r, g, b, 255);
                        c.v(vc, p1.x, p1.y, p1.z, u1, v1, r, g, b, 255);
                        c.v(vc, at.x, at.y, at.z, 0.5F, 0.5F, r, g, b, 255);
                    } else {
                        c.v(vc, at.x, at.y, at.z, 0.5F, 0.5F, r, g, b, 255);
                        c.v(vc, p1.x, p1.y, p1.z, u1, v1, r, g, b, 255);
                        c.v(vc, p0.x, p0.y, p0.z, u0, v0, r, g, b, 255);
                        c.v(vc, at.x, at.y, at.z, 0.5F, 0.5F, r, g, b, 255);
                    }
                }
            }
        }
    }

    /** Ondas concentricas que se abren en el cielo. size = diametro. */
    static final class SkyRings extends Effect {
        @Override
        void init() {
            if (size <= 0) {
                size = 140;
            }
        }

        @Override
        void render(Ctx c, float age) {
            double[] pl = place(c, pos);
            Vec3 at = new Vec3(pl[0], pl[1], pl[2]);
            float env = envelope(age);
            float grow = 0.25F + 0.75F * smooth(age / Math.max(0.5F, Geo.num(p, "grow", 4)));
            float s = (float) (size * 0.5 * pl[3]) * grow;
            float k = env * 0.8F;
            c.billboard(glow("rings"), at, s, s, age * 0.05F, r(k), g(k), b(k), 255);
            float pulse = (age * 0.25F) % 1F;
            float k2 = env * (1 - pulse) * 0.7F;
            c.billboard(glow("ring"), at, s * (0.4F + pulse * 0.9F), s * (0.4F + pulse * 0.9F), 0, r(k2), g(k2), b(k2), 255);
        }
    }

    // ================================================================ efectos de suelo y de personaje

    /** Onda de choque: anillo que se abre a ras del suelo. size = radio final. */
    static final class Shockwave extends Effect {
        @Override
        void init() {
            if (size <= 0) {
                size = 16;
            }
            if (duration <= 0) {
                duration = 2;
            }
        }

        @Override
        void render(Ctx c, float age) {
            float t = Mth.clamp(age / duration, 0, 1);
            float rad = size * (1 - (1 - t) * (1 - t));
            float k = (1 - t) * (1 - t);
            VertexConsumer vc = c.buffers.getBuffer(glow("shock"));
            double y = pos.y + 0.06;
            c.quad(vc, new Vec3(pos.x - rad, y, pos.z - rad), new Vec3(pos.x + rad, y, pos.z - rad), new Vec3(pos.x + rad, y, pos.z + rad),
                    new Vec3(pos.x - rad, y, pos.z + rad), 0, 0, 1, 1, r(k), g(k), b(k), 255, true);
        }
    }

    /** Pilar de luz hacia el cielo. size = ancho. */
    static final class Pillar extends Effect {
        @Override
        void init() {
            if (size <= 0) {
                size = 2;
            }
            if (duration <= 0) {
                duration = 4;
            }
            fadeIn = Math.min(fadeIn, 0.4F);
        }

        @Override
        void render(Ctx c, float age) {
            float env = envelope(age);
            float w = size * (0.6F + 0.4F * env) * 0.5F;
            float h = size * 14;
            Vec3 toCam = new Vec3(c.cam.x - pos.x, 0, c.cam.z - pos.z);
            if (toCam.lengthSqr() < 1.0E-6) {
                return;
            }
            Vec3 side = new Vec3(0, 1, 0).cross(toCam.normalize()).scale(w);
            VertexConsumer vc = c.buffers.getBuffer(glow("pillar"));
            int r = r(env), g = g(env), b = b(env);
            Vec3 base = pos, top = pos.add(0, h, 0);
            c.v(vc, base.x - side.x, base.y, base.z - side.z, 0, 0.02F, r, g, b, 255);
            c.v(vc, base.x + side.x, base.y, base.z + side.z, 1, 0.02F, r, g, b, 255);
            c.v(vc, top.x + side.x, top.y, top.z + side.z, 1, 1, r, g, b, 255);
            c.v(vc, top.x - side.x, top.y, top.z - side.z, 0, 1, r, g, b, 255);
            c.v(vc, top.x - side.x, top.y, top.z - side.z, 0, 1, r, g, b, 255);
            c.v(vc, top.x + side.x, top.y, top.z + side.z, 1, 1, r, g, b, 255);
            c.v(vc, base.x + side.x, base.y, base.z + side.z, 1, 0.02F, r, g, b, 255);
            c.v(vc, base.x - side.x, base.y, base.z - side.z, 0, 0.02F, r, g, b, 255);
            c.billboard(glow("glow"), pos.add(0, 0.4, 0), size * 1.6F, size * 1.6F, 0, r(env * 0.8F), g(env * 0.8F), b(env * 0.8F), 255);
        }

        @Override
        void tick(ClientLevel level, float age, Random random) {
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                particle(level, ParticleTypes.END_ROD, pos.x + Math.cos(a) * size * 0.3, pos.y + random.nextDouble() * 2, pos.z + Math.sin(a) * size * 0.3,
                        0, 0.08 + random.nextDouble() * 0.15, 0);
            }
        }
    }

    /** Remolino de hojitas que sube girando. size = radio. */
    static final class Swarm extends Effect {
        @Override
        void init() {
            if (size <= 0) {
                size = 2;
            }
            if (duration <= 0) {
                duration = 6;
            }
        }

        @Override
        void render(Ctx c, float age) {
            float env = envelope(age);
            int n = Mth.clamp((int) (26 * size), 12, 160);
            Random rnd = new Random(seed);
            RenderType type = cutout("leaflet");
            float height = 4 + size * 1.5F;
            for (int i = 0; i < n; i++) {
                float phase = rnd.nextFloat() * Mth.TWO_PI, speed = 0.5F + rnd.nextFloat() * 0.9F, rad = size * (0.35F + rnd.nextFloat() * 0.75F);
                float off = rnd.nextFloat(), spin = (rnd.nextFloat() - 0.5F) * 8;
                float h = (age * speed * 0.35F + off) % 1F;
                float a = phase + age * (1.2F + speed);
                float rr = rad * (0.6F + 0.6F * h);
                float sc = (0.12F + 0.1F * rnd.nextFloat()) * env * Mth.sin(h * Mth.PI);
                if (sc < 0.01F) {
                    continue;
                }
                c.billboard(type, pos.add(Mth.cos(a) * rr, h * height, Mth.sin(a) * rr), sc * 1.6F, sc, age * spin + phase, 255, 255, 255, 255);
            }
        }
    }

    /** Orbe de luz que late. size = diametro. */
    static final class Glow extends Effect {
        @Override
        void init() {
            if (size <= 0) {
                size = 4;
            }
            // "tree": el brillo se pone en la rama del Arbol Madre de donde va a salir la hoja
            if (Geo.bool(p, "tree", false)) {
                Vec3 start = Tree.leafStart(pos);
                pos = start != null ? start : pos.add(0, Geo.num(p, "fall", 120), 0);
            }
        }

        @Override
        void render(Ctx c, float age) {
            double[] pl = place(c, pos);
            Vec3 at = new Vec3(pl[0], pl[1], pl[2]);
            float s = (float) (size * 0.5 * pl[3]);
            float k = envelope(age) * (0.8F + 0.2F * Mth.sin(age * 3));
            c.billboard(glow("glow"), at, s, s, 0, r(k), g(k), b(k), 255);
            c.billboard(glow("glow"), at, s * 0.45F, s * 0.45F, 0, (int) (255 * k), (int) (255 * k), (int) (255 * k), 255);
            c.billboard(glow("ring"), at, s * 0.9F, s * 0.9F, age * 0.6F, r(k * 0.6F), g(k * 0.6F), b(k * 0.6F), 255);
        }
    }
}
