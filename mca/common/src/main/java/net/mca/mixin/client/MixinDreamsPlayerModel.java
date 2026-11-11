package net.mca.mixin.client;

import net.mca.client.dreams.DreamsPoses;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dreams: el jugador tambien hace la pose de la interaccion (beso, abrazo...). */
@Mixin(PlayerEntityModel.class)
public abstract class MixinDreamsPlayerModel<T extends LivingEntity> {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void mca$dreamsPose(T entity, float f, float g, float h, float i, float j, CallbackInfo ci) {
        DreamsPoses.apply(entity, (PlayerEntityModel<?>) (Object) this);
    }
}
