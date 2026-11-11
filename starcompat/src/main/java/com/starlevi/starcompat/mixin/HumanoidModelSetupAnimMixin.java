package com.starlevi.starcompat.mixin;

import com.starlevi.starcompat.client.PoseKeeper;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ver {@link PoseKeeper}. Nombre SRG: {@code m_6973_} = setupAnim (sin refmap).
 * La prioridad alta hace que corra despues de los mixins de otros mods en el mismo metodo.
 */
@Mixin(value = HumanoidModel.class, remap = false, priority = 2000)
public abstract class HumanoidModelSetupAnimMixin {

    @Inject(method = "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"), remap = false)
    private void starcompat$keepAnimatedPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                             float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        PoseKeeper.onSetupAnim((HumanoidModel<?>) (Object) this);
    }
}
