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
    private static final int STRIDE = 10;
    private static final Random RANDOM = new Random();

    /** Lo que siente el jugador local ahora: 0..1 y el color del arbol que tiene cerca. */
    static float aura;
    static int auraColor = 0x6FC8FF;
    /** Arbol al que se le puede hablar ahora mismo (cerca y mirandolo), o null. */
    static Tree talkable;
    private static long lastHum;

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
        out.add(new float[]{(float) x0, (float) y0, (float) z0, (float) x1, (float) y1, (float) z1, width, h, rank, phase});
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
            x = nx;
            y = ny;
            z = nz;
        }
        if (depth >= maxDepth) {
            return;
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
            double lift = depth == 0 ? size * 0.03 * Math.max(0, 1 - (i + 1) / 3.0) : 0;
            double ny = groundY(pos, nx, nz) + 0.2 + lift + Math.sin(i * 1.7 + phase * 6) * size * 0.004;
            float w = width * (1 - 0.6F * i / steps);
            double dist = Math.sqrt(x * x + z * z);
            add(out, x, y, z, nx, ny, nz, w, (float) (-dist / size * 0.5), Math.min(1, 0.06F + 0.3F * depth + rnd.nextFloat() * 0.2F), phase);
            if (depth < 2 && i >= 2 && rnd.nextFloat() < 0.4F) {
                root(out, rnd, nx, nz, angle + (rnd.nextBoolean() ? 1 : -1) * (0.5 + rnd.nextDouble() * 0.6), len * 0.55, w * 0.7F, depth + 1, phase + rnd.nextFloat());
            }
            x = nx;
            y = ny;
            z = nz;
        }
    }

    private void build() {
        built = true;
        Random rnd = new Random(id.hashCode() * 31L + 7);
        List<float[]> out = new ArrayList<>();
        int strands = 9;
        double trunkTop = size * 0.36;
        float trunkWidth = size * 0.02F;
        for (int s = 0; s < strands; s++) {
            double a0 = Math.PI * 2 * s / strands + rnd.nextDouble() * 0.3;
            float phase = rnd.nextFloat();
            double twist = 2.2 + rnd.nextDouble() * 0.8;
            int steps = 14;
            double px = 0, py = 0, pz = 0;
            double lastA = a0;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                // ancho en la base, cintura al medio, se abre arriba
                double r = size * (0.075 * Math.pow(1 - t, 2) + 0.022 + 0.05 * t * t * t);
                double a = a0 + t * twist;
                double x = Math.cos(a) * r, y = t * trunkTop, z = Math.sin(a) * r;
                if (i > 0) {
                    add(out, px, py, pz, x, y, z, trunkWidth * (float) (1.25 - 0.5 * t), (float) (y / size), 0, phase);
                }
                px = x;
                py = y;
                pz = z;
                lastA = a;
            }
            // de cada hebra nace una rama mayor
            branch(out, rnd, px, py, pz, Math.cos(lastA) * 0.75, 0.75, Math.sin(lastA) * 0.75, size * 0.2, trunkWidth * 0.7F, 0, 4, phase);
            // y una raiz
            root(out, rnd, Math.cos(a0) * size * 0.09, Math.sin(a0) * size * 0.09, a0, size * 0.42, trunkWidth * 0.9F, 0, phase);
        }
        // eje central
        for (int i = 0; i < 10; i++) {
            add(out, 0, trunkTop * i / 10, 0, 0, trunkTop * (i + 1) / 10, 0, trunkWidth * 2.2F, (float) (trunkTop * i / 10 / size), 0, 0.5F);
        }
        branch(out, rnd, 0, trunkTop, 0, 0.05, 1, 0.05, size * 0.24, trunkWidth * 0.9F, 0, 4, 0.5F);
        count = out.size();
        seg = new float[count * STRIDE];
        for (int i = 0; i < count; i++) {
            System.arraycopy(out.get(i), 0, seg, i * STRIDE, STRIDE);
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
                if (pass == 1 && width < size * 0.006F) {
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
                if (pass == 0) {
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

        // resplandores: base, copa y halo
        float w = speakW * smooth(front / 0.3F);
        float r = Mth.lerp(sat, gr, Mth.lerp(w, br, sr)), g = Mth.lerp(sat, gg, Mth.lerp(w, bg, sg)), b = Mth.lerp(sat, gb, Mth.lerp(w, bb, sb));
        float glowK = (0.2F + 0.8F * sat) * flicker * (0.8F + 0.2F * Mth.sin(time * 0.8F) + pulse * 0.4F);
        billboard(c, k, 0, size * 0.1, 0, size * 0.34F, r, g, b, glowK * 0.75F);
        billboard(c, k, 0, size * 0.36, 0, size * 0.3F, r, g, b, glowK * 0.5F);
        billboard(c, k, 0, size * 0.72, 0, size * 0.95F, r, g, b, glowK * 0.22F * Mth.clamp(v * 1.3F, 0, 1));
        // circulo de luz en el suelo
        if (k == 1 && v > 0.03F) {
            float rad = radius * 0.55F;
            float gk = glowK * 0.5F * Mth.clamp(v * 2, 0, 1);
            int ir = (int) (255 * Mth.clamp(r * gk, 0, 1)), ig = (int) (255 * Mth.clamp(g * gk, 0, 1)), ib = (int) (255 * Mth.clamp(b * gk, 0, 1));
            double y = pos.y + 0.08;
            double ca = Math.cos(time * 0.05) * rad, sa = Math.sin(time * 0.05) * rad;
            c.quad(c.buffers.getBuffer(Fx.glow("rings")), new Vec3(pos.x - ca + sa, y, pos.z - sa - ca), new Vec3(pos.x + ca + sa, y, pos.z + sa - ca),
                    new Vec3(pos.x + ca - sa, y, pos.z + sa + ca), new Vec3(pos.x - ca - sa, y, pos.z - sa + ca), 0, 0, 1, 1, ir, ig, ib, 255, true);
        }
    }

    private void billboard(Fx.Ctx c, double k, double x, double y, double z, float half, float r, float g, float b, float bright) {
        Vec3 world = new Vec3(pos.x + x, pos.y + y, pos.z + z);
        Vec3 at = c.cam.add(world.subtract(c.cam).scale(k));
        float s = (float) (half * k);
        c.billboard(Fx.glow("glow"), at, s, s, 0, Mth.clamp((int) (r * bright * 255), 0, 255), Mth.clamp((int) (g * bright * 255), 0, 255),
                Mth.clamp((int) (b * bright * 255), 0, 255), 255);
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
