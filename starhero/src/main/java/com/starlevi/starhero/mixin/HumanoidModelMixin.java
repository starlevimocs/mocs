package com.starlevi.starhero.mixin;

import com.starlevi.starhero.client.StarPoses;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pose de la banda al final de la pose normal (sin EMF). Con EMF se vuelve a poner desde el hook de EMF. */
@Mixin(value = HumanoidModel.class, priority = 2100)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void starhero$bandPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                   float netHeadYaw, float headPitch, CallbackInfo ci) {
        StarPoses.apply(entity, (HumanoidModel<?>) (Object) this);
    }
}
