package com.starlevi.starcombat.mixin;

import com.starlevi.starcombat.client.Pose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pose de combate al final de la pose normal (sin EMF). Con EMF se vuelve a poner desde su hook. */
@Mixin(value = HumanoidModel.class, priority = 2400)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void starcombat$pose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                 float netHeadYaw, float headPitch, CallbackInfo ci) {
        Pose.apply(entity, (HumanoidModel<?>) (Object) this);
    }
}
