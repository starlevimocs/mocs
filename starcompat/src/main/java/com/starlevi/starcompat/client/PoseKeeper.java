package com.starlevi.starcompat.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Guarda la pose del jugador tal como quedo despues de la animacion de EMF (y de Cinematic, si estaba en una
 * estacion). Si mientras se dibujan las capas (armadura, Curios, sombreros...) algun mod vuelve a llamar a
 * {@code setupAnim} sobre el modelo del jugador, Minecraft pisa la pose animada con la vanilla y todo lo que se
 * dibuja despues queda desfasado del cuerpo. {@code HumanoidModelSetupAnimMixin} detecta esas llamadas extra y
 * vuelve a poner esta pose. Es lo mismo que EMF hace en 1.21.9+, que en 1.20.1 no existe.
 */
public final class PoseKeeper {
    private static HumanoidModel<?> model;
    private static ModelPart[] parts;
    private static final float[][] values = new float[12][10];

    private PoseKeeper() {
    }

    static void capture(HumanoidModel<?> humanoid) {
        model = humanoid;
        parts = partsOf(humanoid);
        for (int i = 0; i < parts.length; i++) {
            ModelPart p = parts[i];
            float[] v = values[i];
            v[0] = p.x; v[1] = p.y; v[2] = p.z;
            v[3] = p.xRot; v[4] = p.yRot; v[5] = p.zRot;
            v[6] = p.xScale; v[7] = p.yScale; v[8] = p.zScale;
            v[9] = p.visible ? 1.0F : 0.0F;
        }
    }

    static void clear() {
        model = null;
        parts = null;
    }

    /** Llamado al final de cada {@code HumanoidModel.setupAnim}. */
    public static void onSetupAnim(HumanoidModel<?> humanoid) {
        if (humanoid != model || parts == null) {
            return;
        }
        for (int i = 0; i < parts.length; i++) {
            ModelPart p = parts[i];
            float[] v = values[i];
            p.x = v[0]; p.y = v[1]; p.z = v[2];
            p.xRot = v[3]; p.yRot = v[4]; p.zRot = v[5];
            p.xScale = v[6]; p.yScale = v[7]; p.zScale = v[8];
            p.visible = v[9] > 0.5F;
        }
    }

    private static ModelPart[] partsOf(HumanoidModel<?> m) {
        if (m instanceof PlayerModel<?> p) {
            return new ModelPart[]{p.head, p.hat, p.body, p.rightArm, p.leftArm, p.rightLeg, p.leftLeg,
                    p.jacket, p.rightSleeve, p.leftSleeve, p.rightPants, p.leftPants};
        }
        return new ModelPart[]{m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
    }
}
