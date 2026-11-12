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

/**
 * Las dos hojas en las manos, en tercera persona. El dibujo normal del objeto en la mano se cancela
 * (ItemInHandLayerMixin), asi cada hoja puede soltarse de la mano cuando sale volando.
 */
final class BladeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
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
            Render.heldBlade(ps, buffers, light, player, side);
            ps.popPose();
        }
    }
}
