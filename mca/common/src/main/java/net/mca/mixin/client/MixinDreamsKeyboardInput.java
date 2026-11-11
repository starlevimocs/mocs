package net.mca.mixin.client;

import net.mca.client.dreams.DreamsPoses;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dreams: durante una animacion de pareja el jugador no se mueve (si no, se separarian). */
@Mixin(KeyboardInput.class)
public abstract class MixinDreamsKeyboardInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mca$dreamsLock(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (DreamsPoses.isLocalPlayerLocked()) {
            KeyboardInput input = (KeyboardInput) (Object) this;
            input.movementForward = 0;
            input.movementSideways = 0;
            input.pressingForward = false;
            input.pressingBack = false;
            input.pressingLeft = false;
            input.pressingRight = false;
            input.jumping = false;
            input.sneaking = false;
        }
    }
}
