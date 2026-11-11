package net.mca.mixin.client;

import net.mca.client.dreams.DreamsPoses;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dreams: durante una animacion de pareja, cada uno se dibuja en su lugar exacto respecto del otro. */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinDreamsLivingEntityRenderer {
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
    private void mca$dreamsFace(LivingEntity entity, float f, float g, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        DreamsPoses.beforeRender(entity, g, matrices);
    }

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("RETURN"))
    private void mca$dreamsFaceEnd(LivingEntity entity, float f, float g, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        DreamsPoses.afterRender(entity, matrices);
    }
}
