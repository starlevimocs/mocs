package com.starlevi.starvfx.mixin;

import com.starlevi.starvfx.client.PlayerFx;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Poses de brotar / dormitar al final de la pose normal (sin EMF). Con EMF se vuelven a poner desde su hook. */
@Mixin(value = HumanoidModel.class, priority = 2300)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void starvfx$pose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                              float netHeadYaw, float headPitch, CallbackInfo ci) {
        PlayerFx.pose(entity, (HumanoidModel<?>) (Object) this);
    }
}
