package com.starlevi.dreams.mixin;

import com.starlevi.dreams.compat.CinematicEmfCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Compatibilidad Cinematic Interactions + Entity Model Features.
 * <p>
 * Cinematic acomoda las partes del modelo del jugador al final de {@code HumanoidModel.setupAnim}.
 * EMF despues corre sus animaciones del .jem y pisa esas rotaciones. Mientras el jugador esta en una
 * estacion de Cinematic, se salta la animacion de EMF para que la pose de Cinematic quede.
 * <p>
 * {@code @Pseudo}: si EMF no esta instalado, el mixin simplemente no se aplica.
 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartRoot", remap = false)
public abstract class EmfModelPartRootMixin {

    @Inject(method = "animate", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void dreams$keepCinematicPose(CallbackInfo ci) {
        if (CinematicEmfCompat.cinematicIsPosing()) {
            ci.cancel();
        }
    }

    @Inject(method = "animateNoPause", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void dreams$keepCinematicPoseNoPause(CallbackInfo ci) {
        if (CinematicEmfCompat.cinematicIsPosing()) {
            ci.cancel();
        }
    }
}
