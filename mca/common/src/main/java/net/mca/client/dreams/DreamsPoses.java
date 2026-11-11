package net.mca.client.dreams;

import net.mca.client.model.VillagerEntityBaseModelMCA;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Dreams: poses de las interacciones (beso, abrazo, cachetada) para el aldeano y para el jugador.
 * <p>
 * Se aplican al final de la pose normal, y si EMF / Fresh Animations esta animando el modelo, otra vez despues
 * de EMF (Starcompat llama a {@link #afterEmfAnimation}). Asi las animaciones de EMF siguen funcionando en todo
 * lo demas y la interaccion siempre se ve.
 */
public final class DreamsPoses {
    private static final float PI = (float) Math.PI;

    private static final class Anim {
        final String type;
        final int partner;
        final boolean isVillager;
        final long start;
        final int duration;

        Anim(String type, int partner, boolean isVillager, long start, int duration) {
            this.type = type;
            this.partner = partner;
            this.isVillager = isVillager;
            this.start = start;
            this.duration = duration;
        }
    }

    private static final Map<Integer, Anim> ACTIVE = new HashMap<>();

    private DreamsPoses() {
    }

    public static void start(String type, int villagerId, int playerId, int duration) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.world == null) {
                return;
            }
            long now = client.world.getTime();
            ACTIVE.put(villagerId, new Anim(type, playerId, true, now, duration));
            if (!"slap".equals(type)) {
                ACTIVE.put(playerId, new Anim(type, villagerId, false, now, duration));
            } else {
                ACTIVE.put(playerId, new Anim("slapped", villagerId, false, now + 5, 14));
            }
        });
    }

    private static Anim current(Entity entity) {
        Anim a = ACTIVE.get(entity.getId());
        if (a == null || entity.getWorld() == null) {
            return null;
        }
        long now = entity.getWorld().getTime();
        if (now > a.start + a.duration) {
            ACTIVE.remove(entity.getId());
            return null;
        }
        return now < a.start ? null : a;
    }

    private static float progress(Entity entity, Anim a) {
        float pt = MinecraftClient.getInstance().getTickDelta();
        return MathHelper.clamp((entity.getWorld().getTime() - a.start + pt) / a.duration, 0, 1);
    }

    /** 0 -> 1 al empezar, 1 en el medio, 1 -> 0 al terminar. */
    private static float envelope(float t, float in, float out) {
        float a = MathHelper.clamp(t / in, 0, 1);
        float b = MathHelper.clamp((1 - t) / out, 0, 1);
        float e = Math.min(a, b);
        return e * e * (3 - 2 * e);
    }

    /** Antes de dibujar: los dos se miran de frente mientras dura la animacion. */
    public static void beforeRender(LivingEntity entity) {
        Anim a = current(entity);
        if (a == null || a.type.equals("slapped")) {
            return;
        }
        Entity partner = entity.getWorld().getEntityById(a.partner);
        if (partner == null) {
            return;
        }
        if (entity == MinecraftClient.getInstance().player && MinecraftClient.getInstance().options.getPerspective().isFirstPerson()) {
            return;
        }
        double dx = partner.getX() - entity.getX();
        double dz = partner.getZ() - entity.getZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
        float e = envelope(progress(entity, a), 0.2F, 0.2F);
        float body = MathHelper.lerpAngleDegrees(e, entity.bodyYaw, yaw);
        entity.bodyYaw = body;
        entity.prevBodyYaw = body;
        entity.headYaw = MathHelper.lerpAngleDegrees(e, entity.headYaw, yaw);
        entity.prevHeadYaw = entity.headYaw;
    }

    /** Llamado al final de setAngles (sin EMF) y despues de la animacion de EMF. */
    public static void apply(LivingEntity entity, BipedEntityModel<?> model) {
        Anim a = current(entity);
        if (a == null) {
            return;
        }
        float t = progress(entity, a);
        switch (a.type) {
            case "kiss" -> kiss(model, t, a.isVillager);
            case "hug" -> hug(model, t);
            case "slap" -> slap(model, t);
            case "slapped" -> slapped(model, t);
            default -> {
            }
        }
        if (model instanceof PlayerEntityModel<?> p) {
            p.hat.copyTransform(p.head);
            p.jacket.copyTransform(p.body);
            p.leftSleeve.copyTransform(p.leftArm);
            p.rightSleeve.copyTransform(p.rightArm);
            p.leftPants.copyTransform(p.leftLeg);
            p.rightPants.copyTransform(p.rightLeg);
        } else {
            model.hat.copyTransform(model.head);
        }
    }

    /**
     * Starcompat lo llama (por reflexion) cada vez que EMF termina de animar el modelo de la entidad que se esta
     * dibujando. Para los aldeanos de MCA tambien vuelve a poner sus poses propias (panico, bebes, pechos).
     */
    public static void afterEmfAnimation(LivingEntity entity, Object model) {
        if (model instanceof VillagerEntityBaseModelMCA<?> mca) {
            mca.dreamsAfterEmfAnimation();
        } else if (model instanceof BipedEntityModel<?> biped) {
            apply(entity, biped);
        }
    }

    private static void lerp(ModelPart part, float e, float pitch, float yaw, float roll) {
        part.pitch = MathHelper.lerp(e, part.pitch, pitch);
        part.yaw = MathHelper.lerp(e, part.yaw, yaw);
        part.roll = MathHelper.lerp(e, part.roll, roll);
    }

    private static void kiss(BipedEntityModel<?> m, float t, boolean villager) {
        float e = envelope(t, 0.25F, 0.2F);
        float side = villager ? 1 : -1; // inclinan la cabeza para lados opuestos
        m.body.pitch += 0.16F * e;
        m.head.pitch = MathHelper.lerp(e, m.head.pitch, 0.12F);
        m.head.roll = MathHelper.lerp(e, m.head.roll, 0.22F * side);
        m.head.yaw = MathHelper.lerp(e, m.head.yaw, 0.0F);
        // una mano en el hombro del otro
        lerp(villager ? m.leftArm : m.rightArm, e, -1.35F, villager ? 0.35F : -0.35F, 0.0F);
        m.head.pivotZ = MathHelper.lerp(e, m.head.pivotZ, -0.8F);
    }

    private static void hug(BipedEntityModel<?> m, float t) {
        float e = envelope(t, 0.2F, 0.2F);
        float sway = (float) Math.sin(t * PI * 3) * 0.05F * e;
        m.body.pitch += 0.1F * e;
        m.body.yaw += sway;
        lerp(m.rightArm, e, -1.45F, -0.55F, 0.15F);
        lerp(m.leftArm, e, -1.45F, 0.55F, -0.15F);
        m.head.roll = MathHelper.lerp(e, m.head.roll, 0.15F);
        m.head.pitch = MathHelper.lerp(e, m.head.pitch, 0.25F);
    }

    private static void slap(BipedEntityModel<?> m, float t) {
        // brazo atras, y despues un golpe rapido hacia el costado
        float wind = envelope(t, 0.3F, 0.7F);
        float hit = MathHelper.clamp((t - 0.35F) / 0.2F, 0, 1);
        float back = 1 - MathHelper.clamp((t - 0.7F) / 0.3F, 0, 1);
        m.rightArm.pitch = MathHelper.lerp(wind * back, m.rightArm.pitch, -1.5F);
        m.rightArm.yaw = MathHelper.lerp(back, m.rightArm.yaw, 0.9F - 1.6F * hit);
        m.rightArm.roll = MathHelper.lerp(wind * back, m.rightArm.roll, -0.3F);
        m.body.yaw += (0.25F - 0.5F * hit) * back;
    }

    private static void slapped(BipedEntityModel<?> m, float t) {
        // la cabeza se va de costado por el golpe y vuelve
        float e = (float) Math.sin(MathHelper.clamp(t, 0, 1) * PI) * (1 - t * 0.3F);
        m.head.yaw += 0.7F * e;
        m.head.roll += -0.2F * e;
        m.body.yaw += 0.12F * e;
    }
}
