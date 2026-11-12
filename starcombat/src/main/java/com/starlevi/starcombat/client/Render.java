package com.starlevi.starcombat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starlevi.starcombat.StarCombat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;

/** Dibujo en el mundo: las hojas en el aire, las cadenas y la estela de fuego. */
final class Render {
    private static final ResourceLocation CHAIN = new ResourceLocation(StarCombat.MOD_ID, "textures/fx/chain.png");
    private static final ResourceLocation CHAIN_GLOW = new ResourceLocation(StarCombat.MOD_ID, "textures/fx/chain_glow.png");
    private static final ResourceLocation TRAIL = new ResourceLocation(StarCombat.MOD_ID, "textures/fx/trail.png");
    private static final long TRAIL_MS = 230;
    private static ItemStack blade, glow;

    private Render() {
    }

    static ItemStack bladeStack() {
        if (blade == null) {
            blade = new ItemStack(StarCombat.EMBER_FANGS.get());
            glow = new ItemStack(StarCombat.EMBER_GLOW.get());
        }
        return blade;
    }

    /** La hoja (y su brillo encima) con la matriz que ya tenga {@code ps}. */
    static void bladeModel(PoseStack ps, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack stack = bladeStack();
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, ps, buffers, mc.level, 0);
        mc.getItemRenderer().renderStatic(glow, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buffers, mc.level, 0);
    }

    static void world(PoseStack ps, Vec3 cam, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientCombat.STATES.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        long now = Util.getMillis();
        for (Map.Entry<Integer, ClientCombat.St> en : ClientCombat.STATES.entrySet()) {
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null) {
                continue;
            }
            ClientCombat.St s = en.getValue();
            for (int side = -1; side <= 1; side += 2) {
                ArrayDeque<ClientCombat.Sample> trail = s.trails[side > 0 ? 0 : 1];
                Vec3 pos = ClientCombat.blade(e, s, side, partial);
                if (pos != null) {
                    Vec3 hand = ClientCombat.hand(e, s, side, partial, pos);
                    Vec3 dir = pos.subtract(hand);
                    double len = dir.length();
                    dir = len < 1.0E-4 ? new Vec3(0, 1, 0) : dir.scale(1 / len);
                    boolean grapple = s.grapple || s.retracting();
                    int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(pos));

                    ps.pushPose();
                    ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
                    ps.mulPose(new Quaternionf().rotationTo(0, 1, 0, (float) dir.x, (float) dir.y, (float) dir.z));
                    ps.scale(0.85F, 0.85F, 0.85F);
                    bladeModel(ps, buffers, light);
                    ps.popPose();

                    chain(ps, buffers, cam, hand, pos.subtract(dir.scale(0.45)), grapple && s.arrived ? 0.01 : 0.07, light, now);
                    if (!grapple) {
                        ClientCombat.Sample last = trail.peekLast();
                        if (last == null || now - last.ms >= 6) {
                            trail.addLast(new ClientCombat.Sample(pos.add(dir.scale(1.15)), pos.subtract(dir.scale(0.1)), now));
                        }
                    }
                }
                while (!trail.isEmpty() && now - trail.peekFirst().ms > TRAIL_MS) {
                    trail.pollFirst();
                }
                if (trail.size() >= 2) {
                    trail(ps, buffers, cam, trail, now);
                }
            }
        }
    }

    private static void v(VertexConsumer vc, Matrix4f pose, Matrix3f normal, Vec3 cam, Vec3 p, float u, float v, int r, int g, int b, int light) {
        vc.vertex(pose, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).color(r, g, b, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0, 1, 0).endVertex();
    }

    private static void quad(VertexConsumer vc, Matrix4f pose, Matrix3f normal, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             float u0, float u1, int r, int g, int bl, int light) {
        v(vc, pose, normal, cam, a, u0, 0, r, g, bl, light);
        v(vc, pose, normal, cam, b, u1, 0, r, g, bl, light);
        v(vc, pose, normal, cam, c, u1, 1, r, g, bl, light);
        v(vc, pose, normal, cam, d, u0, 1, r, g, bl, light);
        v(vc, pose, normal, cam, d, u0, 1, r, g, bl, light);
        v(vc, pose, normal, cam, c, u1, 1, r, g, bl, light);
        v(vc, pose, normal, cam, b, u1, 0, r, g, bl, light);
        v(vc, pose, normal, cam, a, u0, 0, r, g, bl, light);
    }

    /** Cadena de eslabones de {@code from} a {@code to}, con un poco de comba. */
    private static void chain(PoseStack ps, MultiBufferSource buffers, Vec3 cam, Vec3 from, Vec3 to, double sag, int light, long now) {
        Vec3 span = to.subtract(from);
        double len = span.length();
        if (len < 0.05) {
            return;
        }
        int n = Mth.clamp((int) Math.ceil(len / 0.32), 1, 160);
        Matrix4f pose = ps.last().pose();
        Matrix3f normal = ps.last().normal();
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(CHAIN));
        Vec3[] pts = new Vec3[n + 1];
        double wave = now / 90.0;
        for (int i = 0; i <= n; i++) {
            double f = i / (double) n;
            double drop = Math.sin(Math.PI * f) * sag * len;
            double wig = Math.sin(f * 9 + wave) * Math.sin(Math.PI * f) * sag * 0.6;
            pts[i] = from.add(span.scale(f)).add(wig, -drop, wig);
        }
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = pass == 0 ? solid : buffers.getBuffer(RenderType.eyes(CHAIN_GLOW));
            float width = pass == 0 ? 0.16F : 0.2F;
            for (int i = 0; i < n; i++) {
                Vec3 a = pts[i], b = pts[i + 1];
                Vec3 dir = b.subtract(a);
                Vec3 side = dir.cross(cam.subtract(a));
                if (side.lengthSqr() < 1.0E-8) {
                    continue;
                }
                // los eslabones se alternan de frente y de canto
                side = side.normalize().scale(width * 0.5 * ((i & 1) == 0 ? 1 : 0.55));
                if (pass == 0) {
                    quad(vc, pose, normal, cam, a.subtract(side), b.subtract(side), b.add(side), a.add(side), 0, 1, 255, 255, 255, light);
                } else {
                    quad(vc, pose, normal, cam, a.subtract(side), b.subtract(side), b.add(side), a.add(side), 0, 1, 255, 120, 30, LightTexture.FULL_BRIGHT);
                }
            }
        }
    }

    /** Estela de fuego: una cinta entre las ultimas posiciones de la hoja, que se apaga hacia atras. */
    private static void trail(PoseStack ps, MultiBufferSource buffers, Vec3 cam, ArrayDeque<ClientCombat.Sample> trail, long now) {
        Matrix4f pose = ps.last().pose();
        Matrix3f normal = ps.last().normal();
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(TRAIL));
        Iterator<ClientCombat.Sample> it = trail.iterator();
        ClientCombat.Sample prev = it.next();
        int n = trail.size();
        int i = 0;
        while (it.hasNext()) {
            ClientCombat.Sample cur = it.next();
            i++;
            float fa = 1 - Mth.clamp((now - prev.ms) / (float) TRAIL_MS, 0, 1);
            float fb = 1 - Mth.clamp((now - cur.ms) / (float) TRAIL_MS, 0, 1);
            float f = (fa + fb) * 0.5F;
            f = f * f;
            int r = (int) (255 * f), g = (int) (150 * f), b = (int) (45 * f);
            float u0 = (i - 1) / (float) (n - 1), u1 = i / (float) (n - 1);
            quad(vc, pose, normal, cam, prev.tip, cur.tip, cur.base, prev.base, u0, u1, r, g, b, LightTexture.FULL_BRIGHT);
            prev = cur;
        }
    }
}
