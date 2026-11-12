package com.starlevi.starcombat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starlevi.starcombat.StarCombat;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;

/**
 * Las dos hojas en las manos, en tercera persona. El modelo del objeto no se dibuja solo en tercera persona
 * (su escala ahi es cero), asi cada hoja puede soltarse de la mano cuando sale volando.
 */
final class BladeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float DEG = (float) (Math.PI / 180);

    BladeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partial, float age, float headYaw, float headPitch) {
        if (!StarCombat.holds(player) || player.isInvisible()) {
            return;
        }
        boolean offFree = player.getOffhandItem().isEmpty();
        for (int side = -1; side <= 1; side += 2) {
            boolean main = side == ClientCombat.mainSide(player);
            if ((!main && !offFree) || ClientCombat.flying(player, side)) {
                continue;
            }
            HumanoidArm arm = side > 0 ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
            ps.pushPose();
            getParentModel().translateToHand(arm, ps);
            ps.mulPose(Axis.XP.rotationDegrees(-90));
            ps.mulPose(Axis.YP.rotationDegrees(180));
            ps.translate(side / 16F, 0.125F, -0.625F);
            // como se empuña: igual que una espada
            ps.translate(0, 0, 1.2F / 16F);
            ps.mulPose(new Quaternionf().rotationXYZ(0, -90 * DEG, 10 * DEG));
            ps.scale(0.7F, 0.7F, 0.7F);
            Render.bladeModel(ps, buffers, light);
            ps.popPose();
        }
    }
}
