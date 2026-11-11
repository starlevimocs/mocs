package com.starlevi.starhero.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starlevi.starhero.client.GigClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mientras tocan, no se dibuja lo que tienen en la mano (el instrumento lo dibuja Star Hero). */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void starhero$hideHeld(PoseStack poseStack, MultiBufferSource buffer, int light, LivingEntity entity, float a, float b,
                                   float c, float d, float e, float f, CallbackInfo ci) {
        if (GigClient.isPerformer(entity.getId())) {
            ci.cancel();
        }
    }
}
