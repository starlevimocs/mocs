package com.starlevi.starcompat.mixin;

import com.starlevi.starcompat.client.ModdedArmorModels;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ver {@link ModdedArmorModels}. {@code getArmorModelHook} es un metodo de Forge (no lleva nombre SRG). */
@Mixin(value = HumanoidArmorLayer.class, remap = false)
public abstract class HumanoidArmorLayerModelMixin {

    @Inject(method = "getArmorModelHook", at = @At("RETURN"), cancellable = true, remap = false)
    private void starcompat$plainModelForModdedArmor(LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                                                     HumanoidModel<?> model, CallbackInfoReturnable<Model> cir) {
        Model replaced = ModdedArmorModels.replace(stack, slot, model, cir.getReturnValue());
        if (replaced != cir.getReturnValue()) {
            cir.setReturnValue(replaced);
        }
    }
}
