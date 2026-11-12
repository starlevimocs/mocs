package com.starlevi.starvfx.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starlevi.starvfx.Geo;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * El Arbol Madre: un coloso hecho de hebras de luz. Un tronco de hebras trenzadas, raices que se abren por el
 * suelo y ramas que se dividen una y otra vez hasta perderse en el cielo. La luz le corre por dentro de abajo
 * hacia arriba; cuando habla, un color nuevo le sube desde las raices; cuando se marchita, las ramas mas finas
 * se apagan primero y lo que queda se vuelve gris.
 */
public final class Tree {
    static final Map<String, Tree> TREES = new LinkedHashMap<>();
    private static final int STRIDE = 11;
    private static final int LEAF = 5;
    private static final long WHISPER_EVERY_MS = 240_000;
    private static final Random RANDOM = new Random();

    /** Lo que siente el jugador local ahora: 0..1 y el color del arbol que tiene cerca. */
    static float aura;
    static int auraColor = 0x6FC8FF;
    /** Arbol al que se le puede hablar ahora mismo (cerca y mirandolo), o null. */
    static Tree talkable;
    private static long lastHum;
    private static long lastWhisper;

    final String id;
    String dim;
    Vec3 pos;
    float size;
    float radius;
    float vitalityTarget;
    float vitality;
    int color;
    /** sonidos: zumbido, campanita y musica ("none" = sin ese sonido) */
    String hum = "minecraft:block.beacon.ambient";
    String chime = "minecraft:block.amethyst_block.chime";
    String music = "";
    float musicVolume = 0.7F;
    private float[] seg = new float[0];
    private int count;
    /** luces de la copa: x, y, z, orden en que se apagan, fase */
    private float[] leaves = new float[0];
    private int leafCount;
    private List<float[]> tips;
    private long speakStart;
    private long speakEnd;
    private int speakColor;
    private long pulseAt;
    private boolean built;

    private Tree(String id) {
        this.id = id;
    }

    // ================================================================ estado

    static void sync(String json) {
        Map<String, Tree> old = new LinkedHashMap<>(TREES);
        TREES.clear();
        try {
            for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                String id = Geo.str(o, "id", "");
                Tree t = old.getOrDefault(id, new Tree(id));
                Vec3 pos = Geo.vec(o, "pos", Vec3.ZERO);
                float size = Geo.num(o, "size", 90);
                if (t.pos == null || !t.pos.equals(pos) || t.size != size) {
                    t.built = false;
                    t.vitality = Geo.num(o, "vitality", 1);
                }
                t.dim = Geo.str(o, "dim", "");
                t.pos = pos;
                t.size = size;
                t.radius = Geo.num(o, "radius", 40);
                t.vitalityTarget = Mth.clamp(Geo.num(o, "vitality", 1), 0, 1);
                t.color = Geo.color(o, "color", 0x6FC8FF);
                t.hum = Geo.str(o, "hum", "minecraft:block.beacon.ambient");
                t.chime = Geo.str(o, "chime", "minecraft:block.amethyst_block.chime");
                t.music = Geo.str(o, "music", "");
                t.musicVolume = Geo.num(o, "music_volume", 0.7F);
                TREES.put(id, t);
            }
        } catch (RuntimeException ignored) {
            TREES.clear();
        }
    }

    void speak(int color, float seconds) {
        long now = Util.getMillis();
        // si ya venia hablando, el color nuevo vuelve a subir desde las raices
        speakStart = now;
        speakEnd = now + (long) (Math.max(1, seconds) * 1000);
        speakColor = color;
        pulseAt = now;
    }

    void pulse() {
        pulseAt = Util.getMillis();
        if (speakEnd > Util.getMillis()) {
            speakEnd = Math.max(speakEnd, Util.getMillis() + 3500);
        }
    }

    /** El arbol mas cercano al jugador en esta dimension (para que "hable" el que uno tiene enfrente). */
    static Tree nearest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        String dim = mc.level.dimension().location().toString();
        Tree best = null;
        double bestD = Double.MAX_VALUE;
        for (Tree t : TREES.values()) {
            if (!t.dim.equals(dim)) {
                continue;
            }
            double d = t.pos.distanceToSqr(mc.player.position());
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    // ================================================================ forma

    private void add(List<float[]> out, double x0, double y0, double z0, double x1, double y1, double z1, float width, float h, float rank, float phase) {
        out.add(new float[]{(float) x0, (float) y0, (float) z0, (float) x1, (float) y1, (float) z1, width, h, rank, phase, 0});
    }

    /** Tramo de "cuerpo": una banda ancha y tenue que rellena el tronco y las raices grandes. */
    private void body(List<float[]> out, double x0, double y0, double z0, double x1, double y1, double z1, float width, float h, float rank) {
        out.add(new float[]{(float) x0, (float) y0, (float) z0, (float) x1, (float) y1, (float) z1, width, h, rank, 0.3F, 1});
    }

    private static double groundY(Vec3 base, double x, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        int bx = Mth.floor(base.x + x), bz = Mth.floor(base.z + z);
        if (level == null || !level.hasChunk(bx >> 4, bz >> 4)) {
            return 0;
        }
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - base.y;
        return Math.abs(y) > 8 ? 0 : y;
    }

    /** Una rama: avanza torciendose y al final se divide. */
    private void branch(List<float[]> out, Random rnd, double x, double y, double z, double dx, double dy, double dz,
                        double len, float width, int depth, int maxDepth, float phase) {
        int steps = depth >= maxDepth ? 3 : 5;
        double step = len / steps;
        for (int i = 0; i < steps; i++) {
            dx += (rnd.nextDouble() - 0.5) * 0.42;
            dz += (rnd.nextDouble() - 0.5) * 0.42;
            dy += (rnd.nextDouble() - 0.35) * 0.3;
            double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= n;
            dy /= n;
            dz /= n;
            double nx = x + dx * step, ny = y + dy * step, nz = z + dz * step;
            float w = width * (1 - 0.35F * i / steps);
            float rank = Math.min(1, 0.12F + 0.72F * (depth + i / (float) steps) / (maxDepth + 1) + rnd.nextFloat() * 0.16F);
            add(out, x, y, z, nx, ny, nz, w, (float) (y / size), rank, phase);
            if (depth <= 1) {
                body(out, x, y, z, nx, ny, nz, w * (depth == 0 ? 5.5F : 3.6F), (float) (y / size), rank);
            }
            x = nx;
            y = ny;
            z = nz;
        }
        if (depth >= maxDepth) {
            tips.add(new float[]{(float) x, (float) y, (float) z, Math.min(1, 0.8F + rnd.nextFloat() * 0.2F), rnd.nextFloat()});
            return;
        }
        if (depth == maxDepth - 1) {
            tips.add(new float[]{(float) x, (float) y, (float) z, Math.min(1, 0.7F + rnd.nextFloat() * 0.2F), rnd.nextFloat()});
        }
        int kids = 2 + (rnd.nextFloat() < 0.4F ? 1 : 0);
        for (int k = 0; k < kids; k++) {
            // se abre hacia un costado al azar, siempre algo hacia arriba
            double ax = rnd.nextDouble() * 2 - 1, ay = rnd.nextDouble() * 0.9 + 0.1, az = rnd.nextDouble() * 2 - 1;
            double spread = 0.55 + rnd.nextDouble() * 0.45;
            branch(out, rnd, x, y, z, dx + ax * spread, dy + ay * spread * 0.7, dz + az * spread, len * (0.66 + rnd.nextDouble() * 0.12),
                    width * 0.62F, depth + 1, maxDepth, phase + rnd.nextFloat());
        }
    }

    private void root(List<float[]> out, Random rnd, double x, double z, double angle, double len, float width, int depth, float phase) {
        int steps = 7;
        double step = len / steps;
        double y = groundY(pos, x, z) + 0.2 + size * 0.012 * (2 - depth);
        for (int i = 0; i < steps; i++) {
            angle += (rnd.nextDouble() - 0.5) * 0.5;
            double nx = x + Math.cos(angle) * step, nz = z + Math.sin(angle) * step;
            // cerca del tronco salen levantadas, despues se pegan al suelo
            double lift = depth == 0 ? size * 0.05 * Math.max(0, 1 - (i + 1) / 4.0) : 0;
            double ny = groundY(pos, nx, nz) + 0.2 + lift + Math.sin(i * 1.7 + phase * 6) * size * 0.004;
            float w = width * (1 - 0.6F * i / steps);
            double dist = Math.sqrt(x * x + z * z);
            add(out, x, y, z, nx, ny, nz, w, (float) (-dist / size * 0.5), Math.min(1, 0.06F + 0.3F * depth + rnd.nextFloat() * 0.2F), phase);
            if (depth == 0 && i < 4) {
                body(out, x, y, z, nx, ny, nz, w * (5.5F - i), (float) (-dist / size * 0.5), 0.05F);
            }
            if (depth < 2 && i >= 2 && rnd.nextFloat() < 0.4F) {
                root(out, rnd, nx, nz, angle + (rnd.nextBoolean() ? 1 : -1) * (0.5 + rnd.nextDouble() * 0.6), len * 0.55, w * 0.7F, depth + 1, phase + rnd.nextFloat());
            }
            x = nx;
            y = ny;
            z = nz;
        }
    }

    /** Radio del tronco a esa altura (0 = base, 1 = donde nacen las ramas): ancho abajo, cintura, y se abre arriba. */
    private double trunkRadius(double t) {
        return size * (0.105 * Math.pow(1 - t, 2.2) + 0.034 + 0.06 * t * t * t);
    }

    private void build() {
        built = true;
        Random rnd = new Random(id.hashCode() * 31L + 7);
        List<float[]> out = new ArrayList<>();
        tips = new ArrayList<>();
        int strands = 13;
        double trunkTop = size * 0.36;
        float trunkWidth = size * 0.02F;
        int steps = 16;
        for (int s = 0; s < strands; s++) {
            double a0 = Math.PI * 2 * s / strands + rnd.nextDouble() * 0.3;
            float phase = rnd.nextFloat();
            double twist = 2.2 + rnd.nextDouble() * 0.8;
            double px = 0, py = 0, pz = 0;
            double lastA = a0;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double r = trunkRadius(t);
                double a = a0 + t * twist;
                double x = Math.cos(a) * r, y = t * trunkTop, z = Math.sin(a) * r;
                if (i > 0) {
                    add(out, px, py, pz, x, y, z, trunkWidth * (float) (1.3 - 0.5 * t), (float) (y / size), 0, phase);
                }
                px = x;
                py = y;
                pz = z;
                lastA = a;
            }
            // de cada hebra nace una rama mayor
            branch(out, rnd, px, py, pz, Math.cos(lastA) * 0.75, 0.75, Math.sin(lastA) * 0.75, size * 0.2, trunkWidth * 0.7F, 0, 4, phase);
            // y una raiz
            root(out, rnd, Math.cos(a0) * size * 0.12, Math.sin(a0) * size * 0.12, a0, size * 0.5, trunkWidth * 1.1F, 0, phase);
        }
        // hebras de adentro, torcidas al reves: el tronco se ve tejido
        for (int s = 0; s < 8; s++) {
            double a0 = Math.PI * 2 * s / 8 + 0.2;
            float phase = rnd.nextFloat();
            double px = 0, py = 0, pz = 0;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double r = trunkRadius(t) * 0.55;
                double a = a0 - t * 3.1;
                double x = Math.cos(a) * r, y = t * trunkTop, z = Math.sin(a) * r;
                if (i > 0) {
                    add(out, px, py, pz, x, y, z, trunkWidth * 0.75F, (float) (y / size), 0, phase);
                }
                px = x;
                py = y;
                pz = z;
            }
        }
        // eje central y cuerpo del tronco
        for (int i = 0; i < steps; i++) {
            double t0 = i / (double) steps, t1 = (i + 1) / (double) steps;
            add(out, 0, trunkTop * t0, 0, 0, trunkTop * t1, 0, trunkWidth * 2.2F, (float) (trunkTop * t0 / size), 0, 0.5F);
            body(out, 0, trunkTop * t0, 0, 0, trunkTop * t1, 0, (float) (trunkRadius((t0 + t1) * 0.5) * 2.1), (float) (trunkTop * t0 / size), 0);
        }
        branch(out, rnd, 0, trunkTop, 0, 0.05, 1, 0.05, size * 0.24, trunkWidth * 0.9F, 0, 4, 0.5F);
        count = out.size();
        seg = new float[count * STRIDE];
        for (int i = 0; i < count; i++) {
            System.arraycopy(out.get(i), 0, seg, i * STRIDE, STRIDE);
        }
        // la copa: tres luces alrededor de cada punta
        List<float[]> lv = new ArrayList<>();
        for (float[] tip : tips) {
            for (int j = 0; j < 3; j++) {
                float spread = size * 0.035F;
                lv.add(new float[]{tip[0] + (rnd.nextFloat() - 0.5F) * spread * 2, tip[1] + (rnd.nextFloat() - 0.3F) * spread * 2,
                        tip[2] + (rnd.nextFloat() - 0.5F) * spread * 2, Math.min(1, tip[3] + j * 0.03F), rnd.nextFloat()});
            }
        }
        leafCount = lv.size();
        leaves = new float[leafCount * LEAF];
        for (int i = 0; i < leafCount; i++) {
            System.arraycopy(lv.get(i), 0, leaves, i * LEAF, LEAF);
        }
    }

    /**
     * De donde se desprende la hoja de la escena: una rama alta del arbol mas cercano, del lado donde va a caer.
     * {@code null} si no hay ningun arbol cerca.
     */
    static Vec3 leafStart(Vec3 landing) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        String dim = mc.level.dimension().location().toString();
        Tree best = null;
        double bestD = 900 * 900;
        for (Tree t : TREES.values()) {
            double dx = t.pos.x - landing.x, dz = t.pos.z - landing.z;
            if (t.dim.equals(dim) && dx * dx + dz * dz < bestD) {
                bestD = dx * dx + dz * dz;
                best = t;
            }
        }
        if (best == null) {
            return null;
        }
        double dx = landing.x - best.pos.x, dz = landing.z - best.pos.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1.0E-3) {
            dx = 1;
            dz = 0;
            d = 1;
        }
        double out = Math.min(best.size * 0.24, d * 0.6);
        return new Vec3(best.pos.x + dx / d * out, best.pos.y + best.size * 0.8, best.pos.z + dz / d * out);
    }

    /** El arbol mas cercano a ese punto brilla un momento (la escena de la hoja lo usa cuando la suelta). */
    static void shine(Vec3 near, int color, float seconds) {
        Tree best = null;
        double bestD = Double.MAX_VALUE;
        for (Tree t : TREES.values()) {
            double d = t.pos.distanceToSqr(near);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        if (best != null) {
            best.speak(color, seconds);
        }
    }

    // ================================================================ dibujo

    private static float smooth(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    static void renderAll(Fx.Ctx c) {
        Minecraft mc = Minecraft.getInstance();
        if (TREES.isEmpty() || mc.level == null) {
            return;
        }
        String dim = mc.level.dimension().location().toString();
        for (Tree t : TREES.values()) {
            if (t.dim.equals(dim)) {
                t.render(c);
            }
        }
    }

    private void render(Fx.Ctx c) {
        double cx = c.cam.x - pos.x, cy = c.cam.y - pos.y, cz = c.cam.z - pos.z;
        double dist = Math.sqrt(cx * cx + (cy - size * 0.4) * (cy - size * 0.4) + cz * cz);
        if (dist > 1600) {
            return;
        }
        if (!built) {
            if (!Minecraft.getInstance().level.hasChunk(Mth.floor(pos.x) >> 4, Mth.floor(pos.z) >> 4) && dist < 200) {
                return;
            }
            build();
        }
        long now = Util.getMillis();
        float time = (now % 3_600_000L) / 1000F;
        // lejos: se lo acerca achicandolo (se ve igual) para que la niebla no se lo coma
        double k = dist + size * 0.7 > c.far ? c.far / (dist + size * 0.7) : 1;
        float v = vitality;
        float sat = Mth.clamp(v * 1.4F, 0, 1);
        float pulse = (float) Math.exp(-(now - pulseAt) / 220.0);
        float speakW = speakEnd > now ? Mth.clamp((speakEnd - now) / 1500F, 0, 1) : 0;
        float front = (now - speakStart) / 1000F * 0.9F - 0.3F;
        float flowSpeed = 0.5F + 1.3F * v;
        // apagandose: parpadea
        float flicker = v < 0.35F ? 0.75F + 0.25F * Mth.sin(time * 13) * Mth.sin(time * 5.3F) : 1;

        float br = ((color >> 16) & 255) / 255F, bg = ((color >> 8) & 255) / 255F, bb = (color & 255) / 255F;
        float sr = ((speakColor >> 16) & 255) / 255F, sg = ((speakColor >> 8) & 255) / 255F, sb = (speakColor & 255) / 255F;
        // marchito: gris azulado
        float gr = 0.34F, gg = 0.38F, gb = 0.46F;

        VertexConsumer vc = c.buffers.getBuffer(Fx.glow("line"));
        org.joml.Matrix4f pose = c.pose;
        org.joml.Matrix3f normal = c.normal;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < count; i++) {
                int o = i * STRIDE;
                float width = seg[o + 6], h = seg[o + 7], rank = seg[o + 8], phase = seg[o + 9];
                boolean body = seg[o + 10] > 0.5F;
                if (pass == 1 && (body || width < size * 0.006F)) {
                    continue;
                }
                float vis = Mth.clamp((v * 1.08F + 0.03F - rank) / 0.14F, 0, 1);
                if (vis <= 0.01F) {
                    continue;
                }
                // punto medio, relativo a la camara
                double ax = (seg[o] - cx) * k, ay = (seg[o + 1] - cy) * k, az = (seg[o + 2] - cz) * k;
                double bx = (seg[o + 3] - cx) * k, by = (seg[o + 4] - cy) * k, bz = (seg[o + 5] - cz) * k;
                double dx = bx - ax, dy = by - ay, dz = bz - az;
                double mx = -(ax + bx) * 0.5, my = -(ay + by) * 0.5, mz = -(az + bz) * 0.5;
                double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
                double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (sl < 1.0E-7) {
                    continue;
                }
                float flow = 0.62F + 0.38F * Mth.sin(h * 15 - time * flowSpeed + phase * 6.28F);
                float bright = vis * flicker * (0.25F + 0.75F * sat) * (flow + pulse * 0.55F);
                float w = speakW * smooth((front - h) / 0.18F);
                float r = Mth.lerp(sat, gr, Mth.lerp(w, br, sr));
                float g = Mth.lerp(sat, gg, Mth.lerp(w, bg, sg));
                float b = Mth.lerp(sat, gb, Mth.lerp(w, bb, sb));
                double half;
                if (body) {
                    // cuerpo: banda ancha y tenue, que respira despacio
                    half = width * k * 0.5;
                    bright = vis * flicker * (0.2F + 0.8F * sat) * (0.26F + 0.05F * Mth.sin(time * 0.7F + h * 5) + pulse * 0.14F);
                } else if (pass == 0) {
                    half = width * 2.6 * k * 0.5;
                } else {
                    // corazon casi blanco en las hebras gruesas
                    half = width * 0.8 * k * 0.5;
                    r = r * 0.35F + 0.65F;
                    g = g * 0.35F + 0.65F;
                    b = b * 0.35F + 0.65F;
                    bright *= 0.75F * sat;
                }
                int ir = Mth.clamp((int) (r * bright * 255), 0, 255), ig = Mth.clamp((int) (g * bright * 255), 0, 255),
                        ib = Mth.clamp((int) (b * bright * 255), 0, 255);
                if (ir + ig + ib < 6) {
                    continue;
                }
                float hx = (float) (sx / sl * half), hy = (float) (sy / sl * half), hz = (float) (sz / sl * half);
                float fax = (float) ax, fay = (float) ay, faz = (float) az, fbx = (float) bx, fby = (float) by, fbz = (float) bz;
                vert(vc, pose, normal, fax - hx, fay - hy, faz - hz, 0, 0, ir, ig, ib);
                vert(vc, pose, normal, fbx - hx, fby - hy, fbz - hz, 1, 0, ir, ig, ib);
                vert(vc, pose, normal, fbx + hx, fby + hy, fbz + hz, 1, 1, ir, ig, ib);
                vert(vc, pose, normal, fax + hx, fay + hy, faz + hz, 0, 1, ir, ig, ib);
                vert(vc, pose, normal, fax + hx, fay + hy, faz + hz, 0, 1, ir, ig, ib);
                vert(vc, pose, normal, fbx + hx, fby + hy, fbz + hz, 1, 1, ir, ig, ib);
                vert(vc, pose, normal, fbx - hx, fby - hy, fbz - hz, 1, 0, ir, ig, ib);
                vert(vc, pose, normal, fax - hx, fay - hy, faz - hz, 0, 0, ir, ig, ib);
            }
        }

        float cr0 = Mth.lerp(sat, gr, br), cg0 = Mth.lerp(sat, gg, bg), cb0 = Mth.lerp(sat, gb, bb);
        float life = (0.2F + 0.8F * sat) * flicker;

        // columna de luz que sube por el eje hasta perderse en el cielo
        strip(vc, pose, normal, -cx * k, -cy * k, -cz * k, -cx * k, (size * 1.45 - cy) * k, -cz * k, size * 0.055 * k,
                cr0, cg0, cb0, life * sat * (0.2F + pulse * 0.12F));

        // cintas de luz que giran alrededor del tronco
        if (v > 0.15F) {
            int ribbons = 3, n = 44;
            for (int j = 0; j < ribbons; j++) {
                double px = 0, py = 0, pz = 0;
                for (int i = 0; i <= n; i++) {
                    double t = i / (double) n;
                    double ang = time * 0.22 * (j == 1 ? -1 : 1) + j * Math.PI * 2 / ribbons + t * 7.5;
                    double rad = size * (0.15 + 0.13 * Math.sin(Math.PI * t) + 0.01 * Math.sin(time * 0.9 + t * 11 + j));
                    double x = Math.cos(ang) * rad, y = size * (0.03 + 0.8 * t), z = Math.sin(ang) * rad;
                    if (i > 0) {
                        float fade = (float) Math.sin(Math.PI * t) * (0.6F + 0.4F * Mth.sin((float) (t * 19 - time * 1.6 + j * 2)));
                        strip(vc, pose, normal, (px - cx) * k, (py - cy) * k, (pz - cz) * k, (x - cx) * k, (y - cy) * k, (z - cz) * k,
                                size * 0.007 * k, cr0 * 0.6F + 0.4F, cg0 * 0.6F + 0.4F, cb0 * 0.6F + 0.4F, life * sat * 0.55F * fade);
                    }
                    px = x;
                    py = y;
                    pz = z;
                }
            }
        }

        // la copa: miles de luces que titilan en las puntas, y algunas que se sueltan y bajan flotando
        org.joml.Vector3f camLeft = Minecraft.getInstance().gameRenderer.getMainCamera().getLeftVector();
        org.joml.Vector3f camUp = Minecraft.getInstance().gameRenderer.getMainCamera().getUpVector();
        VertexConsumer dots = c.buffers.getBuffer(Fx.glow("glow"));
        float lw = speakW * smooth((front - 0.75F) / 0.2F);
        float lr = Mth.lerp(sat, gr, Mth.lerp(lw, br, sr)), lg = Mth.lerp(sat, gg, Mth.lerp(lw, bg, sg)), lb = Mth.lerp(sat, gb, Mth.lerp(lw, bb, sb));
        for (int i = 0; i < leafCount; i++) {
            int o = i * LEAF;
            float vis = Mth.clamp((v * 1.08F + 0.03F - leaves[o + 3]) / 0.14F, 0, 1);
            if (vis <= 0.01F) {
                continue;
            }
            float ph = leaves[o + 4];
            float tw = 0.5F + 0.5F * Mth.sin(time * (1.1F + ph * 1.4F) + ph * 40);
            double sway = Math.sin(time * 0.6 + ph * 20) * size * 0.004;
            double x = (leaves[o] + sway - cx) * k, y = (leaves[o + 1] - cy) * k, z = (leaves[o + 2] + sway - cz) * k;
            if (i % 5 == 0) {
                // bruma de la copa: le da volumen
                dot(dots, pose, normal, camLeft, camUp, x, y, z, size * 0.085 * k, size * 0.085 * k, 0, lr, lg, lb, vis * life * (0.11F + pulse * 0.05F));
            }
            float s = size * (0.009F + 0.008F * ph) * (float) k;
            dot(dots, pose, normal, camLeft, camUp, x, y, z, s, s, 0, lr * 0.55F + 0.45F, lg * 0.55F + 0.45F, lb * 0.55F + 0.45F,
                    vis * life * (0.35F + 0.65F * tw));
        }
        if (v > 0.2F && leafCount > 0 && k == 1) {
            int falling = (int) (90 * Mth.clamp(v * 1.3F, 0, 1));
            for (int j = 0; j < falling; j++) {
                int o = (j * 37 % leafCount) * LEAF;
                float period = 13 + (j % 9);
                float u = (time / period + j * 0.137F) % 1;
                double drop = leaves[o + 1] * Math.pow(u, 1.25);
                double x = leaves[o] + Math.sin(u * 9 + j) * size * 0.035 + Math.sin(j * 1.7) * size * 0.06 * u - cx;
                double y = leaves[o + 1] - drop + size * 0.01 - cy;
                double z = leaves[o + 2] + Math.cos(u * 7 + j * 2.1) * size * 0.035 + Math.cos(j * 1.3) * size * 0.06 * u - cz;
                float fade = (float) Math.pow(Math.sin(Math.PI * u), 0.6);
                float s = size * 0.014F;
                dot(dots, pose, normal, camLeft, camUp, x, y, z, s, s * 0.42F, time * 1.3F + j, lr * 0.5F + 0.5F, lg * 0.5F + 0.5F, lb * 0.5F + 0.5F,
                        life * fade * 0.9F);
            }
        }

        // resplandores: base, copa y halo
        float w = speakW * smooth(front / 0.3F);
        float r = Mth.lerp(sat, gr, Mth.lerp(w, br, sr)), g = Mth.lerp(sat, gg, Mth.lerp(w, bg, sg)), b = Mth.lerp(sat, gb, Mth.lerp(w, bb, sb));
        float glowK = (0.2F + 0.8F * sat) * flicker * (0.8F + 0.2F * Mth.sin(time * 0.8F) + pulse * 0.4F);
        billboard(c, k, 0, size * 0.1, 0, size * 0.42F, r, g, b, glowK * 0.75F);
        billboard(c, k, 0, size * 0.36, 0, size * 0.34F, r, g, b, glowK * 0.5F);
        billboard(c, k, 0, size * 0.74, 0, size * 1.1F, r, g, b, glowK * 0.26F * Mth.clamp(v * 1.3F, 0, 1));
        billboard(c, k, 0, size * 0.74, 0, size * 0.5F, r * 0.5F + 0.5F, g * 0.5F + 0.5F, b * 0.5F + 0.5F, glowK * 0.16F * Mth.clamp(v * 1.3F, 0, 1));
        // circulo de luz en el suelo
        if (k == 1 && v > 0.03F) {
            float rad = radius * 0.55F;
            float gk = glowK * 0.5F * Mth.clamp(v * 2, 0, 1);
            int ir = (int) (255 * Mth.clamp(r * gk, 0, 1)), ig = (int) (255 * Mth.clamp(g * gk, 0, 1)), ib = (int) (255 * Mth.clamp(b * gk, 0, 1));
            double y = pos.y + 0.08;
            double ca = Math.cos(time * 0.05) * rad, sa = Math.sin(time * 0.05) * rad;
            c.quad(c.buffers.getBuffer(Fx.glow("rings")), new Vec3(pos.x - ca + sa, y, pos.z - sa - ca), new Vec3(pos.x + ca + sa, y, pos.z + sa - ca),
                    new Vec3(pos.x + ca - sa, y, pos.z + sa + ca), new Vec3(pos.x - ca - sa, y, pos.z - sa + ca), 0, 0, 1, 1, ir, ig, ib, 255, true);
            // ondas de luz que salen del tronco por el suelo
            for (int i = 0; i < 2; i++) {
                float u = (time / 7F + i * 0.5F) % 1;
                float rr = radius * (0.15F + 0.95F * u);
                float wk = gk * (1 - u) * (1 - u) * 1.3F;
                int wr = (int) (255 * Mth.clamp(r * wk, 0, 1)), wg = (int) (255 * Mth.clamp(g * wk, 0, 1)), wb = (int) (255 * Mth.clamp(b * wk, 0, 1));
                c.quad(c.buffers.getBuffer(Fx.glow("ring")), new Vec3(pos.x - rr, y + 0.03, pos.z - rr), new Vec3(pos.x + rr, y + 0.03, pos.z - rr),
                        new Vec3(pos.x + rr, y + 0.03, pos.z + rr), new Vec3(pos.x - rr, y + 0.03, pos.z + rr), 0, 0, 1, 1, wr, wg, wb, 255, true);
            }
        }
    }

    private void billboard(Fx.Ctx c, double k, double x, double y, double z, float half, float r, float g, float b, float bright) {
        Vec3 world = new Vec3(pos.x + x, pos.y + y, pos.z + z);
        Vec3 at = c.cam.add(world.subtract(c.cam).scale(k));
        float s = (float) (half * k);
        c.billboard(Fx.glow("glow"), at, s, s, 0, Mth.clamp((int) (r * bright * 255), 0, 255), Mth.clamp((int) (g * bright * 255), 0, 255),
                Mth.clamp((int) (b * bright * 255), 0, 255), 255);
    }

    /** Tramo de luz entre dos puntos (ya relativos a la camara), de cara a ella. */
    private static void strip(VertexConsumer vc, org.joml.Matrix4f pose, org.joml.Matrix3f normal, double ax, double ay, double az,
                              double bx, double by, double bz, double half, float r, float g, float b, float bright) {
        int ir = Mth.clamp((int) (r * bright * 255), 0, 255), ig = Mth.clamp((int) (g * bright * 255), 0, 255), ib = Mth.clamp((int) (b * bright * 255), 0, 255);
        if (ir + ig + ib < 6) {
            return;
        }
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double mx = -(ax + bx) * 0.5, my = -(ay + by) * 0.5, mz = -(az + bz) * 0.5;
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0E-7) {
            return;
        }
        float hx = (float) (sx / sl * half), hy = (float) (sy / sl * half), hz = (float) (sz / sl * half);
        float fax = (float) ax, fay = (float) ay, faz = (float) az, fbx = (float) bx, fby = (float) by, fbz = (float) bz;
        vert(vc, pose, normal, fax - hx, fay - hy, faz - hz, 0, 0, ir, ig, ib);
        vert(vc, pose, normal, fbx - hx, fby - hy, fbz - hz, 1, 0, ir, ig, ib);
        vert(vc, pose, normal, fbx + hx, fby + hy, fbz + hz, 1, 1, ir, ig, ib);
        vert(vc, pose, normal, fax + hx, fay + hy, faz + hz, 0, 1, ir, ig, ib);
        vert(vc, pose, normal, fax + hx, fay + hy, faz + hz, 0, 1, ir, ig, ib);
        vert(vc, pose, normal, fbx + hx, fby + hy, fbz + hz, 1, 1, ir, ig, ib);
        vert(vc, pose, normal, fbx - hx, fby - hy, fbz - hz, 1, 0, ir, ig, ib);
        vert(vc, pose, normal, fax - hx, fay - hy, faz - hz, 0, 0, ir, ig, ib);
    }

    /** Punto de luz de cara a la camara (posicion ya relativa a ella). */
    private static void dot(VertexConsumer vc, org.joml.Matrix4f pose, org.joml.Matrix3f normal, org.joml.Vector3f left, org.joml.Vector3f up,
                            double x, double y, double z, double sx, double sy, float roll, float r, float g, float b, float bright) {
        int ir = Mth.clamp((int) (r * bright * 255), 0, 255), ig = Mth.clamp((int) (g * bright * 255), 0, 255), ib = Mth.clamp((int) (b * bright * 255), 0, 255);
        if (ir + ig + ib < 6) {
            return;
        }
        float cs = Mth.cos(roll), sn = Mth.sin(roll);
        float rx = (float) ((left.x() * cs + up.x() * sn) * sx), ry = (float) ((left.y() * cs + up.y() * sn) * sx), rz = (float) ((left.z() * cs + up.z() * sn) * sx);
        float ux = (float) ((up.x() * cs - left.x() * sn) * sy), uy = (float) ((up.y() * cs - left.y() * sn) * sy), uz = (float) ((up.z() * cs - left.z() * sn) * sy);
        float fx = (float) x, fy = (float) y, fz = (float) z;
        vert(vc, pose, normal, fx - rx - ux, fy - ry - uy, fz - rz - uz, 0, 1, ir, ig, ib);
        vert(vc, pose, normal, fx + rx - ux, fy + ry - uy, fz + rz - uz, 1, 1, ir, ig, ib);
        vert(vc, pose, normal, fx + rx + ux, fy + ry + uy, fz + rz + uz, 1, 0, ir, ig, ib);
        vert(vc, pose, normal, fx - rx + ux, fy - ry + uy, fz - rz + uz, 0, 0, ir, ig, ib);
        vert(vc, pose, normal, fx - rx + ux, fy - ry + uy, fz - rz + uz, 0, 0, ir, ig, ib);
        vert(vc, pose, normal, fx + rx + ux, fy + ry + uy, fz + rz + uz, 1, 0, ir, ig, ib);
        vert(vc, pose, normal, fx + rx - ux, fy + ry - uy, fz + rz - uz, 1, 1, ir, ig, ib);
        vert(vc, pose, normal, fx - rx - ux, fy - ry - uy, fz - rz - uz, 0, 1, ir, ig, ib);
    }

    private static void vert(VertexConsumer vc, org.joml.Matrix4f pose, org.joml.Matrix3f normal, float x, float y, float z, float u, float v, int r, int g, int b) {
        vc.vertex(pose, x, y, z).color(r, g, b, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0, 1, 0).endVertex();
    }

    // ================================================================ cada tick: vida, aura, sonido, motas

    static void tickAll(Minecraft mc) {
        aura = 0;
        talkable = null;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || TREES.isEmpty()) {
            TreeMusic.update("", 0, 0);
            return;
        }
        Tree near = null;
        String dim = mc.level.dimension().location().toString();
        for (Tree t : TREES.values()) {
            // la vida cambia de a poco: se ve marchitar o revivir
            t.vitality += Mth.clamp(t.vitalityTarget - t.vitality, -0.004F, 0.004F);
            if (!t.dim.equals(dim)) {
                continue;
            }
            double dx = p.getX() - t.pos.x, dz = p.getZ() - t.pos.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < t.radius * 1.6 && p.getY() > t.pos.y - 12 && p.getY() < t.pos.y + t.size * 1.2) {
                float a = smooth((float) (1 - d / (t.radius * 1.6))) * (0.25F + 0.75F * Mth.clamp(t.vitality * 1.5F, 0, 1));
                if (a > aura) {
                    aura = a;
                    near = t;
                    long now = Util.getMillis();
                    float w = t.speakEnd > now ? Mth.clamp((t.speakEnd - now) / 1500F, 0, 1) : 0;
                    auraColor = t.vitality < 0.12F ? 0x66707F : blend(t.color, t.speakColor, w);
                }
                // hablarle: cerca del tronco y mirandolo
                float reach = t.size * 0.2F + 8;
                if (d < reach && mc.screen == null) {
                    Vec3 to = new Vec3(t.pos.x - p.getX(), 0, t.pos.z - p.getZ());
                    Vec3 look = p.getLookAngle();
                    Vec3 flat = new Vec3(look.x, 0, look.z);
                    if (d < t.size * 0.06 + 1.5 || (flat.lengthSqr() > 1.0E-4 && to.lengthSqr() > 1.0E-4 && flat.normalize().dot(to.normalize()) > 0.82)) {
                        talkable = t;
                    }
                }
                t.motes(mc.level, d);
            }
        }
        // al llegar, el arbol saluda en voz baja (no mas de una vez cada tanto)
        if (near != null && aura > 0.3F && mc.screen == null && !ClientScene.locked()) {
            long now = Util.getMillis();
            if (lastWhisper == 0 || now - lastWhisper > WHISPER_EVERY_MS) {
                lastWhisper = now;
                String group = near.vitality < 0.2F ? "dying" : near.vitality < 0.55F ? "weak" : "strong";
                int n = group.equals("dying") ? 3 : group.equals("weak") ? 5 : 8;
                String line = net.minecraft.client.resources.language.I18n.get("starvfx.tree.whisper." + group + "." + RANDOM.nextInt(n));
                int tint = near.vitality < 0.2F ? 0x8A93A6 : blend(near.color, 0xFFFFFF, 0.45F);
                Hud.text("", line, 5.5F, tint);
                near.speak(tint, 4);
            }
        }
        // musica de calma: sube al acercarse, baja al alejarse
        TreeMusic.update(near == null ? "" : near.music, aura, near == null ? 0 : near.musicVolume);
        // zumbido grave del arbol y alguna campanita
        if (near != null && aura > 0.08F) {
            long now = Util.getMillis();
            if (now - lastHum > 3600) {
                lastHum = now;
                play(mc, near.hum, 0.55F + RANDOM.nextFloat() * 0.1F, 0.5F * aura);
                if (RANDOM.nextInt(3) == 0) {
                    play(mc, near.chime, 0.6F + RANDOM.nextFloat() * 0.8F, 0.45F * aura);
                }
            }
        }
    }

    private static void play(Minecraft mc, String sound, float pitch, float volume) {
        net.minecraft.resources.ResourceLocation id = sound.isEmpty() || sound.equals("none") ? null : net.minecraft.resources.ResourceLocation.tryParse(sound);
        if (id != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id), pitch, volume));
        }
    }

    private static int blend(int a, int b, float w) {
        int r = (int) Mth.lerp(w, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(w, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(w, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    /** Motas de luz que suben despacio alrededor (menos cuanto mas apagado esta). */
    private void motes(ClientLevel level, double playerDist) {
        int n = (int) (7 * Mth.clamp(vitality * 1.4F, 0, 1));
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2, r = Math.sqrt(RANDOM.nextDouble()) * radius;
            double x = pos.x + Math.cos(a) * r, z = pos.z + Math.sin(a) * r;
            double y = pos.y + groundY(pos, Math.cos(a) * r, Math.sin(a) * r) + RANDOM.nextDouble() * size * 0.35;
            level.addParticle(i % 3 == 0 ? ParticleTypes.GLOW : ParticleTypes.END_ROD, true, x, y, z, 0, 0.015 + RANDOM.nextDouble() * 0.04, 0);
        }
    }
}
