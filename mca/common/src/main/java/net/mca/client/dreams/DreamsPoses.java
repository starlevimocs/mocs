package net.mca.client.dreams;

import net.mca.client.dreams.anim.DreamsAnimation;
import net.mca.client.model.VillagerEntityBaseModelMCA;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Dreams: reproduce las animaciones de MCA (estilo Blockbench) en el aldeano y en el jugador.
 * <p>
 * Las de pareja (beso, abrazo, bailes...) usan un "ancla" que manda el servidor: los dos se dibujan en posiciones
 * calculadas desde esa ancla, asi que siempre quedan juntos y alineados, aunque el servidor los tenga un poco
 * corridos. Se aplican al final de la pose normal y, si EMF / Fresh Animations esta animando, otra vez despues de
 * EMF (Starcompat llama a {@link #afterEmfAnimation}); los huesos que la animacion no usa siguen con EMF.
 */
public final class DreamsPoses {
    private static final class Active {
        final DreamsAnimation anim;
        final String participant;
        final long startTick;
        final boolean anchored;
        final double ax, ay, az;
        final float ayaw;

        Active(DreamsAnimation anim, String participant, long startTick, boolean anchored, double ax, double ay, double az, float ayaw) {
            this.anim = anim;
            this.participant = participant;
            this.startTick = startTick;
            this.anchored = anchored;
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.ayaw = ayaw;
        }
    }

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();
    private static final Map<Integer, Boolean> PUSHED = new HashMap<>();
    private static Perspective savedPerspective;
    private static boolean localLocked;

    private DreamsPoses() {
    }

    // ------------------------------------------------------------------ arrancar

    /** Animacion de pareja que manda el servidor. */
    public static void startPair(String name, int villagerId, int playerId, double ax, double ay, double az, float ayaw) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.world == null) {
                return;
            }
            Optional<DreamsAnimation> anim = DreamsAnimation.get(name);
            if (anim.isEmpty()) {
                return;
            }
            long now = client.world.getTime();
            ACTIVE.put(villagerId, new Active(anim.get(), "villager", now, true, ax, ay, az, ayaw));
            ACTIVE.put(playerId, new Active(anim.get(), "player", now, true, ax, ay, az, ayaw));
            if (client.player != null && client.player.getId() == playerId) {
                localLocked = true;
                if (anim.get().thirdPerson && savedPerspective == null) {
                    savedPerspective = client.options.getPerspective();
                    client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                }
            }
        });
    }

    /** Animacion de una sola persona (saludar, hablar...). No pisa una de pareja. */
    public static void startSolo(Entity entity, String name) {
        Active current = ACTIVE.get(entity.getId());
        if (current != null && current.anchored) {
            return;
        }
        DreamsAnimation.get(name).ifPresent(anim ->
                ACTIVE.put(entity.getId(), new Active(anim, "self", entity.getWorld().getTime(), false, 0, 0, 0, 0)));
    }

    public static boolean isAnimating(Entity entity) {
        return current(entity) != null;
    }

    public static boolean isLocalPlayerLocked() {
        return localLocked;
    }

    private static Active current(Entity entity) {
        Active a = ACTIVE.get(entity.getId());
        if (a == null || entity.getWorld() == null) {
            return null;
        }
        float t = time(entity, a);
        if (!a.anim.loop && t > a.anim.length) {
            ACTIVE.remove(entity.getId());
            return null;
        }
        return a;
    }

    private static float time(Entity entity, Active a) {
        float pt = MinecraftClient.getInstance().getTickDelta();
        float t = (entity.getWorld().getTime() - a.startTick + pt) / 20.0F;
        return a.anim.loop ? t % a.anim.length : t;
    }

    /** Cada tick del cliente: libera la camara y el movimiento cuando termina la animacion del jugador. */
    public static void clientTick(MinecraftClient client) {
        if (client.player == null) {
            ACTIVE.clear();
            localLocked = false;
            return;
        }
        if (localLocked && current(client.player) == null) {
            localLocked = false;
            if (savedPerspective != null) {
                client.options.setPerspective(savedPerspective);
                savedPerspective = null;
            }
        }
    }

    // ------------------------------------------------------------------ dibujo

    private static Vec3d anchorToWorld(Active a, float[] local) {
        double yaw = Math.toRadians(a.ayaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // hacia adelante
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // a la derecha
        double x = local[0] / 16.0, y = local[1] / 16.0, z = local[2] / 16.0;
        return new Vec3d(a.ax + fx * z + rx * x, a.ay + y, a.az + fz * z + rz * x);
    }

    /**
     * Antes de dibujar: lleva a la entidad a su lugar en la pareja (sin moverla de verdad) y la gira.
     * Devuelve true si empujo una matriz (hay que sacarla al terminar de dibujar).
     */
    public static void beforeRender(LivingEntity entity, float tickDelta, MatrixStack matrices) {
        Active a = current(entity);
        if (a == null || !a.anchored) {
            return;
        }
        DreamsAnimation.Bone root = a.anim.bones(a.participant).get("root");
        float t = time(entity, a);
        float[] pos = root != null && root.position != null ? root.position.sample(t)
                : (a.participant.equals("villager") ? new float[]{0, 0, a.anim.distance} : new float[]{0, 0, 0});
        float rotY = root != null && root.rotation != null ? root.rotation.sample(t)[1]
                : (a.participant.equals("villager") ? 180 : 0);
        float w = a.anim.weight(t);

        Vec3d target = anchorToWorld(a, pos);
        Vec3d actual = entity.getLerpedPos(tickDelta);
        matrices.push();
        PUSHED.put(entity.getId(), true);
        matrices.translate((target.x - actual.x) * w, (target.y - actual.y) * w, (target.z - actual.z) * w);

        float yaw = a.ayaw + rotY;
        float body = MathHelper.lerpAngleDegrees(w, entity.bodyYaw, yaw);
        entity.bodyYaw = body;
        entity.prevBodyYaw = body;
        entity.headYaw = body;
        entity.prevHeadYaw = body;
    }

    public static void afterRender(LivingEntity entity, MatrixStack matrices) {
        if (PUSHED.remove(entity.getId()) != null) {
            matrices.pop();
        }
    }

    /** Pone los huesos de la animacion encima de la pose actual. */
    public static void apply(LivingEntity entity, BipedEntityModel<?> model) {
        Active a = current(entity);
        if (a == null) {
            return;
        }
        float t = time(entity, a);
        float w = a.anim.weight(t);
        for (Map.Entry<String, DreamsAnimation.Bone> e : a.anim.bones(a.participant).entrySet()) {
            ModelPart part = part(model, e.getKey());
            if (part == null) {
                continue;
            }
            DreamsAnimation.Bone bone = e.getValue();
            if (bone.rotation != null) {
                float[] r = bone.rotation.sample(t);
                part.pitch = MathHelper.lerp(w, part.pitch, r[0] * MathHelper.RADIANS_PER_DEGREE);
                part.yaw = MathHelper.lerp(w, part.yaw, r[1] * MathHelper.RADIANS_PER_DEGREE);
                part.roll = MathHelper.lerp(w, part.roll, r[2] * MathHelper.RADIANS_PER_DEGREE);
            }
            if (bone.position != null) {
                float[] p = bone.position.sample(t);
                part.pivotX += p[0] * w;
                part.pivotY -= p[1] * w;
                part.pivotZ += p[2] * w;
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

    private static ModelPart part(BipedEntityModel<?> m, String bone) {
        return switch (bone) {
            case "head" -> m.head;
            case "body" -> m.body;
            case "right_arm" -> m.rightArm;
            case "left_arm" -> m.leftArm;
            case "right_leg" -> m.rightLeg;
            case "left_leg" -> m.leftLeg;
            default -> null; // "root" se usa en beforeRender
        };
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
}
