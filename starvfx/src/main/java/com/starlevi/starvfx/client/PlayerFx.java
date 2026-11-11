package com.starlevi.starvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Animaciones especiales de los jugadores: brotar del suelo (al entrar, al reaparecer, en la escena de la hoja)
 * y dormitar sentado cuando alguien se queda quieto mucho rato.
 */
public final class PlayerFx {
    private static final class Anim {
        String name;
        long startMs;
        float duration;
    }

    private static final Map<Integer, Anim> ANIMS = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static int pushed = Integer.MIN_VALUE;

    private PlayerFx() {
    }

    static void clear() {
        ANIMS.clear();
    }

    static void set(int entityId, String name, float delay, float duration) {
        if (name.equals("none")) {
            ANIMS.remove(entityId);
            return;
        }
        Anim a = new Anim();
        a.name = name;
        a.startMs = Util.getMillis() + (long) (delay * 1000);
        a.duration = duration;
        ANIMS.put(entityId, a);
    }

    private static float elapsed(Anim a) {
        return (Util.getMillis() - a.startMs) / 1000F;
    }

    private static Anim get(Entity e) {
        Anim a = ANIMS.get(e.getId());
        if (a != null && a.duration > 0 && elapsed(a) > a.duration) {
            ANIMS.remove(e.getId());
            return null;
        }
        return a;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** El jugador local esta brotando: no se mueve hasta terminar. */
    static boolean localBusy() {
        LocalPlayer p = Minecraft.getInstance().player;
        Anim a = p == null ? null : get(p);
        return a != null && a.name.equals("sprout");
    }

    // ================================================================ al dibujar

    /** Antes de dibujar a alguien. Devuelve false si todavia no se lo tiene que ver. */
    static boolean beforeRender(LivingEntity e, PoseStack ps) {
        Anim a = get(e);
        if (a == null || Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
            return true;
        }
        float t = elapsed(a);
        if (a.name.equals("sprout")) {
            if (t < 0) {
                return false;
            }
            float u = Mth.clamp(t / Math.max(0.1F, a.duration), 0, 1);
            float rise = 1 - (float) Math.pow(1 - Mth.clamp(u * 1.3F, 0, 1), 3);
            ps.pushPose();
            ps.translate(0, -(e.getBbHeight() + 0.15F) * (1 - rise), 0);
            pushed = e.getId();
        } else if (a.name.equals("afk")) {
            ps.pushPose();
            ps.translate(0, -0.6F * smooth(t / 0.7F), 0);
            pushed = e.getId();
        }
        return true;
    }

    static void afterRender(LivingEntity e, PoseStack ps) {
        if (pushed == e.getId()) {
            ps.popPose();
            pushed = Integer.MIN_VALUE;
        }
    }

    /** La pose (encima de la animacion normal y de la de EMF). */
    public static void pose(LivingEntity e, HumanoidModel<?> m) {
        Anim a = ANIMS.get(e.getId());
        if (a == null) {
            return;
        }
        float t = elapsed(a);
        if (t < 0) {
            return;
        }
        if (a.name.equals("sprout")) {
            float u = Mth.clamp(t / Math.max(0.1F, a.duration), 0, 1);
            // sale con los brazos hacia el cielo, mirando arriba; al final se acomoda
            float w = 1 - smooth((u - 0.55F) / 0.45F);
            float sway = Mth.sin(t * 3) * 0.08F;
            m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -2.85F + sway);
            m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -2.85F - sway);
            m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, -0.22F);
            m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, 0.22F);
            m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, 0);
            m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, 0);
            m.head.xRot = Mth.lerp(w, m.head.xRot, -0.75F);
            m.head.yRot = Mth.lerp(w, m.head.yRot, 0);
            m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, 0);
            m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, 0);
        } else if (a.name.equals("afk")) {
            float w = smooth(t / 0.7F);
            float breath = Mth.sin(t * 1.3F);
            m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, -1.42F);
            m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, -1.42F);
            m.rightLeg.yRot = Mth.lerp(w, m.rightLeg.yRot, 0.32F);
            m.leftLeg.yRot = Mth.lerp(w, m.leftLeg.yRot, -0.32F);
            m.rightLeg.zRot = Mth.lerp(w, m.rightLeg.zRot, 0.08F);
            m.leftLeg.zRot = Mth.lerp(w, m.leftLeg.zRot, -0.08F);
            m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -0.62F + breath * 0.02F);
            m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -0.62F + breath * 0.02F);
            m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.05F);
            m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.05F);
            // cabecea
            m.head.xRot = Mth.lerp(w, m.head.xRot, 0.5F + breath * 0.06F);
            m.head.yRot = Mth.lerp(w, m.head.yRot, 0.12F);
        } else {
            return;
        }
        m.hat.copyFrom(m.head);
        if (m instanceof PlayerModel<?> pm) {
            pm.leftSleeve.copyFrom(pm.leftArm);
            pm.rightSleeve.copyFrom(pm.rightArm);
            pm.leftPants.copyFrom(pm.leftLeg);
            pm.rightPants.copyFrom(pm.rightLeg);
        }
    }

    // ================================================================ camara propia al brotar

    /** Mientras uno brota fuera de una escena, se ve a si mismo desde enfrente saliendo del suelo. */
    static Cine.View selfView(LocalPlayer p, float pt) {
        Anim a = get(p);
        if (a == null || !a.name.equals("sprout")) {
            return null;
        }
        float t = elapsed(a);
        if (t < -0.5F) {
            return null;
        }
        float u = Mth.clamp(t / Math.max(0.1F, a.duration), 0, 1);
        Vec3 base = p.getPosition(pt);
        float ang = (p.yBodyRot + 28 - 56 * smooth(u)) * Mth.DEG_TO_RAD;
        Vec3 target = base.add(0, 0.95 + 0.25 * u, 0);
        Vec3 want = base.add(-Mth.sin(ang) * 3.8, 1.2 + 0.9 * u, Mth.cos(ang) * 3.8);
        // que no quede metida en una pared
        HitResult hit = p.level().clip(new ClipContext(target, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
        Vec3 cam = want;
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 d = hit.getLocation().subtract(target);
            cam = target.add(d.scale(Math.max(0.2, 1 - 0.35 / Math.max(0.35, d.length()))));
        }
        Vec3 d = target.subtract(cam);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        return new Cine.View(cam.x, cam.y, cam.z, (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F,
                (float) -(Mth.atan2(d.y, h) * Mth.RAD_TO_DEG), 62, 0);
    }

    // ================================================================ particulas y adornos

    static void tick(Minecraft mc) {
        if (mc.level == null || ANIMS.isEmpty()) {
            return;
        }
        ANIMS.entrySet().removeIf(en -> {
            Anim a = en.getValue();
            return a.duration > 0 && elapsed(a) > a.duration;
        });
        for (Map.Entry<Integer, Anim> en : ANIMS.entrySet()) {
            Entity e = mc.level.getEntity(en.getKey());
            Anim a = en.getValue();
            if (e == null || !a.name.equals("sprout")) {
                continue;
            }
            float t = elapsed(a);
            float u = t / Math.max(0.1F, a.duration);
            if (t < -0.6F || u > 0.8F) {
                continue;
            }
            // la tierra se abre y sube luz
            BlockState ground = mc.level.getBlockState(e.blockPosition().below());
            for (int i = 0; i < 4; i++) {
                double ang = RANDOM.nextDouble() * Math.PI * 2, r = 0.2 + RANDOM.nextDouble() * 0.5;
                if (!ground.isAir() && i < 2) {
                    mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), e.getX() + Math.cos(ang) * r, e.getY() + 0.1,
                            e.getZ() + Math.sin(ang) * r, Math.cos(ang) * 0.1, 0.25, Math.sin(ang) * 0.1);
                } else {
                    mc.level.addParticle(i == 2 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.END_ROD, e.getX() + Math.cos(ang) * r, e.getY() + 0.1,
                            e.getZ() + Math.sin(ang) * r, 0, 0.06 + RANDOM.nextDouble() * 0.1, 0);
                }
            }
        }
    }

    static boolean hasWorldVisuals() {
        for (Anim a : ANIMS.values()) {
            if (a.name.equals("afk")) {
                return true;
            }
        }
        return false;
    }

    /** Las "Z" que suben sobre quien dormita. */
    static void renderWorld(Fx.Ctx c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float pt = mc.getFrameTime();
        for (Map.Entry<Integer, Anim> en : ANIMS.entrySet()) {
            Anim a = en.getValue();
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null || !a.name.equals("afk") || e.isInvisible()) {
                continue;
            }
            float t = elapsed(a);
            if (t < 1) {
                continue;
            }
            Vec3 base = e.getPosition(pt).add(0, e.getBbHeight() * 0.62 + 0.25, 0);
            for (int i = 0; i < 3; i++) {
                float h = (t * 0.3F + i / 3F) % 1F;
                float k = Mth.sin(h * Mth.PI);
                float s = 0.07F + 0.1F * h;
                int v = (int) (235 * k);
                c.billboard(Fx.glow("zzz"), base.add(0.25 + 0.35 * h, h * 0.9, 0), s, s, 0.2F - 0.3F * h, v, v, v, 255);
            }
        }
    }
}
