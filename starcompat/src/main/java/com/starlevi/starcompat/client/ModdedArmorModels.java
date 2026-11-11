package com.starlevi.starcompat.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import traben.entity_model_features.EMFAnimationApi;

/**
 * Armaduras de mods + paquetes de armaduras 3D de EMF (por ejemplo "Just 3D Armors").
 * <p>
 * Esos paquetes cambian el modelo de armadura del jugador por uno 3D pensado solo para las texturas de las
 * armaduras vanilla (hierro, diamante...). Una armadura de otro mod, como la ropa de granjero de Kaleidoscope,
 * usa una textura con el formato clasico, asi que sobre el modelo 3D queda invisible o con pedazos sueltos.
 * Para las armaduras que no son de Minecraft se usa el modelo clasico de armadura (sin EMF), que siempre coincide
 * con su textura. Sigue la pose del jugador igual que cualquier armadura.
 */
public final class ModdedArmorModels {
    private static final boolean EMF = ModList.get().isLoaded("entity_model_features");

    private static HumanoidModel<?> inner;
    private static HumanoidModel<?> outer;

    private ModdedArmorModels() {
    }

    public static Model replace(ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original, Model chosen) {
        if (!EMF || chosen != original || !(stack.getItem() instanceof ArmorItem)) {
            return chosen; // el item ya trae su propio modelo (por ejemplo el sombrero de paja)
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || "minecraft".equals(id.getNamespace())) {
            return chosen;
        }
        try {
            if (!EMFAnimationApi.isModelCustomizedByEMF(original)) {
                return chosen;
            }
        } catch (Throwable t) {
            return chosen;
        }
        HumanoidModel<?> plain = slot == EquipmentSlot.LEGS ? inner() : outer();
        copyPose(original, plain);
        return plain;
    }

    private static HumanoidModel<?> inner() {
        if (inner == null) {
            inner = build(0.5F);
        }
        return inner;
    }

    private static HumanoidModel<?> outer() {
        if (outer == null) {
            outer = build(1.0F);
        }
        return outer;
    }

    /** Igual que el modelo vanilla de armadura, armado a mano para que EMF no lo reemplace. */
    private static HumanoidModel<?> build(float inflate) {
        ModelPart root = LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(inflate), 0.0F), 64, 32).bakeRoot();
        return new HumanoidModel<>(root);
    }

    private static void copyPose(HumanoidModel<?> from, HumanoidModel<?> to) {
        to.young = from.young;
        to.riding = from.riding;
        to.attackTime = from.attackTime;
        to.crouching = from.crouching;
        to.leftArmPose = from.leftArmPose;
        to.rightArmPose = from.rightArmPose;
        copyPart(from.head, to.head);
        copyPart(from.hat, to.hat);
        copyPart(from.body, to.body);
        copyPart(from.rightArm, to.rightArm);
        copyPart(from.leftArm, to.leftArm);
        copyPart(from.rightLeg, to.rightLeg);
        copyPart(from.leftLeg, to.leftLeg);
    }

    private static void copyPart(ModelPart from, ModelPart to) {
        to.copyFrom(from);
        to.xScale = from.xScale;
        to.yScale = from.yScale;
        to.zScale = from.zScale;
        to.visible = from.visible;
    }
}
