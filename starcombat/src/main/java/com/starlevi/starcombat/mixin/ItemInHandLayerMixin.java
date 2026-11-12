package com.starlevi.starcombat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starlevi.starcombat.StarCombat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** En tercera persona los Colmillos no se dibujan como un objeto normal: los dibuja BladeLayer, hoja por hoja. */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void starcombat$hide(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                 PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (entity instanceof Player && stack.is(StarCombat.EMBER_FANGS.get())) {
            ci.cancel();
        }
    }
}
