package net.mca.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dreams: fundido a negro de la animacion de procrear. */
@Mixin(InGameHud.class)
public class MixinDreamsInGameHud {
    @Inject(method = "render", at = @At("TAIL"))
    private void dreams$overlay(DrawContext context, float tickDelta, CallbackInfo ci) {
        net.mca.client.dreams.DreamsPoses.renderOverlay(context);
    }
}
