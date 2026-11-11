package com.starlevi.starhero.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Los efectos del show: haces de luz de colores que barren el escenario al compas, un foco sobre cada musico,
 * chispas en cada nota acertada, humo de escenario, llamaradas y fuegos artificiales cuando el publico esta
 * encendido, y papelitos de colores en el final. Todo del lado del cliente (cada jugador lo ve en su pantalla).
 */
public final class StageFx {
    private static final Random RANDOM = new Random();
    private static long lastSpark;
    private static long lastFinaleBurst;

    private StageFx() {
    }

    private static Vec3 stage(double side, double up, double forward) {
        Vec3 f = GigClient.forward();
        Vec3 s = GigClient.side();
        return GigClient.center.add(s.scale(side)).add(0, up, 0).add(f.scale(forward));
    }

    /** Medio ancho de la banda (para saber donde estan los bordes del escenario). */
    private static double halfWidth() {
        Minecraft mc = Minecraft.getInstance();
        double w = 2.5;
        if (mc.level != null) {
            Vec3 s = GigClient.side();
            for (GigClient.Performer p : GigClient.PERFORMERS.values()) {
                Entity e = mc.level.getEntity(p.entityId);
                if (e != null) {
                    w = Math.max(w, Math.abs(e.position().subtract(GigClient.center).dot(s)) + 1.6);
                }
            }
        }
        return w;
    }

    // ================================================================ particulas

    static void onBeat(long beat) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        float e = GigClient.hype();
        double w = halfWidth();

        // humo bajo en el frente del escenario
        if (beat % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                Vec3 p = stage((RANDOM.nextDouble() * 2 - 1) * w, 0.15, 1.4 + RANDOM.nextDouble());
                level.addParticle(ParticleTypes.CLOUD, p.x, p.y, p.z, (RANDOM.nextDouble() - 0.5) * 0.04, 0.005, (RANDOM.nextDouble() - 0.5) * 0.04);
            }
        }
        // llamaradas en las puntas del escenario al empezar cada compas
        if (e > 0.62F && beat % 4 == 0) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 p = stage(side * (w + 0.4), 0.1, 1.2);
                for (int i = 0; i < 26; i++) {
                    level.addParticle(i % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME,
                            p.x + (RANDOM.nextDouble() - 0.5) * 0.25, p.y + RANDOM.nextDouble() * 0.4, p.z + (RANDOM.nextDouble() - 0.5) * 0.25,
                            (RANDOM.nextDouble() - 0.5) * 0.03, 0.18 + RANDOM.nextDouble() * 0.32, (RANDOM.nextDouble() - 0.5) * 0.03);
                }
                level.addParticle(ParticleTypes.LAVA, p.x, p.y + 0.3, p.z, 0, 0, 0);
            }
        }
        // fuegos artificiales sobre la banda con el publico enloquecido
        if (e > 0.82F && beat % 8 == 0) {
            burst(level, stage((RANDOM.nextDouble() * 2 - 1) * w, 5.5 + RANDOM.nextDouble() * 2, -0.5), 46, 0.32);
        }
        // chispas de colores cayendo del techo en cada tiempo fuerte
        if (e > 0.5F && beat % 4 == 2) {
            float hue = (beat % 24) / 24F;
            int rgb = Mth.hsvToRgb(hue, 0.85F, 1F);
            DustParticleOptions dust = new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F), 1.3F);
            for (int i = 0; i < 14; i++) {
                Vec3 p = stage((RANDOM.nextDouble() * 2 - 1) * w, 4.5 + RANDOM.nextDouble(), (RANDOM.nextDouble() - 0.3) * 2);
                level.addParticle(dust, p.x, p.y, p.z, 0, -0.05, 0);
            }
        }
    }

    private static void burst(ClientLevel level, Vec3 p, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double b = Math.acos(RANDOM.nextDouble() * 2 - 1);
            double v = speed * (0.6 + RANDOM.nextDouble() * 0.5);
            level.addParticle(ParticleTypes.FIREWORK, p.x, p.y, p.z, Math.sin(b) * Math.cos(a) * v, Math.cos(b) * v, Math.sin(b) * Math.sin(a) * v);
        }
        level.addParticle(ParticleTypes.FLASH, p.x, p.y, p.z, 0, 0, 0);
    }

    /** Chispas en el instrumento cuando acierta una nota. */
    static void onHit(GigClient.Performer p) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        if (mc.level == null || now - lastSpark < 40) {
            return;
        }
        lastSpark = now;
        Entity e = mc.level.getEntity(p.entityId);
        if (e == null) {
            return;
        }
        Vec3 f = GigClient.forward();
        double d = switch (p.instrument) {
            case DRUMS -> 0.85;
            case KEYS -> 0.6;
            default -> 0.4;
        };
        double h = switch (p.instrument) {
            case VOICE -> 1.55;
            case DRUMS -> 0.95;
            default -> 1.0;
        };
        double x = e.getX() + f.x * d + (RANDOM.nextDouble() - 0.5) * 0.4;
        double y = e.getY() + h + (RANDOM.nextDouble() - 0.5) * 0.2;
        double z = e.getZ() + f.z * d + (RANDOM.nextDouble() - 0.5) * 0.4;
        mc.level.addParticle(p.instrument == com.starlevi.starhero.content.Instrument.VOICE ? ParticleTypes.NOTE : ParticleTypes.ELECTRIC_SPARK,
                x, y, z, (RANDOM.nextDouble() - 0.5) * 0.3, 0.15 + RANDOM.nextDouble() * 0.2, (RANDOM.nextDouble() - 0.5) * 0.3);
        if (GigClient.hype() > 0.7F && RANDOM.nextFloat() < 0.35F) {
            mc.level.addParticle(ParticleTypes.END_ROD, x, y, z, f.x * 0.08, 0.06, f.z * 0.08);
        }
    }

    static void tick(long now) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !GigClient.finale()) {
            return;
        }
        // final: fuegos artificiales y papelitos si el show gusto
        if (GigClient.hype() > 0.45F && now - lastFinaleBurst > 380) {
            lastFinaleBurst = now;
            double w = halfWidth();
            burst(level, stage((RANDOM.nextDouble() * 2 - 1) * (w + 1), 4.5 + RANDOM.nextDouble() * 3, RANDOM.nextDouble() * 2 - 1), 40, 0.3);
            for (int i = 0; i < 30; i++) {
                int rgb = Mth.hsvToRgb(RANDOM.nextFloat(), 0.8F, 1F);
                DustParticleOptions dust = new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F), 1.1F);
                Vec3 p = stage((RANDOM.nextDouble() * 2 - 1) * (w + 2), 3.5 + RANDOM.nextDouble() * 2.5, RANDOM.nextDouble() * 5 - 1);
                level.addParticle(dust, p.x, p.y, p.z, 0, -0.03, 0);
            }
        }
    }

    // ================================================================ luces

    /** Haces de luz y focos (se dibujan despues de las particulas, con brillo aditivo). */
    public static void renderLights(PoseStack ps, Vec3 camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!GigClient.active() || mc.level == null) {
            return;
        }
        long now = Util.getMillis();
        double beat = GigClient.beat(now);
        float e = GigClient.hype();
        double w = halfWidth();
        Vec3 f = GigClient.forward();
        Vec3 s = GigClient.side();
        float ph = (float) (beat - Math.floor(beat));
        float pulse = beat >= 0 ? (float) Math.exp(-ph * 4) : 0;

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        ps.pushPose();
        ps.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f mat = ps.last().pose();

        // haces de colores desde arriba y atras de la banda, barriendo al compas
        int beams = 6;
        for (int i = 0; i < beams; i++) {
            double off = (i - (beams - 1) / 2.0) * (2 * w / (beams - 1));
            Vec3 top = stage(off, 6.2, -1.6);
            double a = 0.55 * Math.sin(beat * Math.PI / 4 + i * 1.3) + (i - 2.5) * 0.12;
            double b = 0.35 + 0.35 * Math.sin(beat * Math.PI / 2 + i * 0.9);
            Vec3 dir = new Vec3(0, -1, 0).add(s.scale(Math.sin(a))).add(f.scale(b)).normalize();
            float hue = (float) ((beat / 16.0 + i / (double) beams) % 1.0);
            if (hue < 0) {
                hue += 1;
            }
            int rgb = Mth.hsvToRgb(hue, 0.75F, 1F);
            float strobe = e > 0.88F ? (((int) Math.floor(beat * 2) + i) % 2 == 0 ? 1.0F : 0.35F) : 1.0F;
            float alpha = (0.10F + 0.16F * e + 0.10F * pulse) * strobe;
            cone(vc, mat, top, dir, 9.0, 1.25, rgb, alpha);
        }
        // foco blanco sobre cada musico (mas fuerte sobre el que acaba de tocar)
        for (GigClient.Performer p : GigClient.PERFORMERS.values()) {
            Entity en = mc.level.getEntity(p.entityId);
            if (en == null) {
                continue;
            }
            Vec3 feet = en.getPosition(partialTick);
            Vec3 top = feet.add(0, 6.0, 0).subtract(f.scale(0.8));
            Vec3 dir = feet.add(0, 0.9, 0).subtract(top).normalize();
            float recent = 1 - Mth.clamp((now - p.lastHit) / 500F, 0, 1);
            cone(vc, mat, top, dir, 6.0, 0.85, 0xFFF4D8, 0.07F + 0.10F * recent);
        }
        ps.popPose();
        buffers.endBatch(RenderType.lightning());
    }

    /** Cono de luz: brillante en la punta, se desvanece hacia la base. */
    private static void cone(VertexConsumer vc, Matrix4f mat, Vec3 apex, Vec3 dir, double length, double radius, int rgb, float alpha) {
        Vec3 u = dir.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1.0E-4) {
            u = dir.cross(new Vec3(1, 0, 0));
        }
        u = u.normalize();
        Vec3 v = dir.cross(u).normalize();
        Vec3 base = apex.add(dir.scale(length));
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        int a = (int) (Mth.clamp(alpha, 0, 1) * 255);
        int seg = 10;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            Vec3 p0 = base.add(u.scale(Math.cos(a0) * radius)).add(v.scale(Math.sin(a0) * radius));
            Vec3 p1 = base.add(u.scale(Math.cos(a1) * radius)).add(v.scale(Math.sin(a1) * radius));
            // las dos caras, asi se ve de adentro y de afuera
            quad(vc, mat, apex, apex, p0, p1, r, g, b, a);
            quad(vc, mat, apex, apex, p1, p0, r, g, b, a);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, Vec3 a0, Vec3 a1, Vec3 b0, Vec3 b1, int r, int g, int b, int a) {
        vc.vertex(mat, (float) a0.x, (float) a0.y, (float) a0.z).color(r, g, b, a).endVertex();
        vc.vertex(mat, (float) a1.x, (float) a1.y, (float) a1.z).color(r, g, b, a).endVertex();
        vc.vertex(mat, (float) b0.x, (float) b0.y, (float) b0.z).color(r, g, b, 0).endVertex();
        vc.vertex(mat, (float) b1.x, (float) b1.y, (float) b1.z).color(r, g, b, 0).endVertex();
    }
}
