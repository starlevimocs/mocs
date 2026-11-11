package com.starlevi.starvfx.mixin;

import com.starlevi.starvfx.client.Cine;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Camara de cinematica / de zona fija (al final, asi queda encima de la de otros mods). */
@Mixin(value = Camera.class, priority = 2100)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void starvfx$cinematicCamera(BlockGetter level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
        Cine.View v = Cine.frame(partialTick);
        if (v != null) {
            setRotation(v.yaw(), v.pitch());
            setPosition(v.x(), v.y(), v.z());
            this.detached = true;
        }
    }
}
