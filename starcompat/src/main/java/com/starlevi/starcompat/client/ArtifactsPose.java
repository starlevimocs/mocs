package com.starlevi.starcompat.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;

/**
 * Artifacts + Fresh Animations.
 * <p>
 * El paraguas de Artifacts levanta el brazo con la pose vanilla THROW_SPEAR. Fresh Animations anima los brazos
 * por su cuenta y esa pose se pierde, asi que el paraguas queda apuntando al piso. Despues de la animacion de EMF
 * se vuelve a levantar el brazo que sostiene el paraguas (igual que vanilla) y se le pega la manga.
 */
final class ArtifactsPose {
    private static final float PI = (float) Math.PI;

    private static boolean resolved;
    private static Method holdingUpright; // UmbrellaItem.isHoldingUmbrellaUpright(LivingEntity, InteractionHand)

    private ArtifactsPose() {
    }

    private static Method method() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> umbrella = Class.forName("artifacts.item.UmbrellaItem");
                holdingUpright = umbrella.getMethod("isHoldingUmbrellaUpright", LivingEntity.class, InteractionHand.class);
            } catch (Throwable t) {
                holdingUpright = null; // Artifacts no esta instalado
            }
        }
        return holdingUpright;
    }

    static void apply(LivingEntity entity, HumanoidModel<?> model) {
        Method method = method();
        if (method == null) {
            return;
        }
        try {
            boolean main = (boolean) method.invoke(null, entity, InteractionHand.MAIN_HAND);
            boolean off = (boolean) method.invoke(null, entity, InteractionHand.OFF_HAND);
            if (!main && !off) {
                return;
            }
            boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
            if ((main && rightHanded) || (off && !rightHanded)) {
                raise(model.rightArm);
                if (model instanceof PlayerModel<?> player) {
                    player.rightSleeve.copyFrom(player.rightArm);
                }
            }
            if ((main && !rightHanded) || (off && rightHanded)) {
                raise(model.leftArm);
                if (model instanceof PlayerModel<?> player) {
                    player.leftSleeve.copyFrom(player.leftArm);
                }
            }
        } catch (Throwable t) {
            holdingUpright = null;
        }
    }

    /** Pose vanilla THROW_SPEAR, con el balanceo reducido como hace Artifacts. */
    private static void raise(ModelPart arm) {
        arm.xRot = arm.xRot * 0.0625F - PI;
        arm.yRot = 0.0F;
    }
}
