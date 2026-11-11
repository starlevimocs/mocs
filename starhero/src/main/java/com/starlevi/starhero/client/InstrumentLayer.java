package com.starlevi.starhero.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starlevi.starhero.content.Instrument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Dibuja el instrumento de cada integrante mientras toca: la guitarra y el bajo colgados del cuerpo (siguen al
 * torso), el microfono en la mano, la bateria y el teclado delante, apoyados en el piso.
 * Los modelos son los items 3D del mod (assets/starhero/models/item).
 */
public class InstrumentLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final Map<Instrument, ItemStack> STACKS = new EnumMap<>(Instrument.class);

    public InstrumentLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    private static ItemStack stack(Instrument i) {
        return STACKS.computeIfAbsent(i, Instrument::stack);
    }

    private static float px(float v) {
        return v / 16F;
    }

    /** Corre el modelo para que el punto (ax, ay, az) del item quede en el origen. */
    private static void anchor(PoseStack ps, float ax, float ay, float az) {
        ps.translate(-(ax - 8) / 16F, -(ay - 8) / 16F, -(az - 8) / 16F);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        Instrument instrument = GigClient.instrumentOf(entity.getId());
        if (instrument == null) {
            return;
        }
        ItemStack stack = stack(instrument);
        if (stack.isEmpty()) {
            return;
        }
        M model = getParentModel();
        ps.pushPose();
        switch (instrument) {
            case GUITAR -> {
                model.body.translateAndRotate(ps);
                float[] at = StarPoses.GUITAR_AT;
                ps.translate(px(at[0]), px(at[1]), px(at[2]));
                ps.mulPose(Axis.ZP.rotation(Mth.PI - StarPoses.GUITAR_ANGLE));
                ps.mulPose(Axis.YP.rotation(-0.12F));
                ps.scale(0.48F, 0.48F, 0.48F);
                anchor(ps, 14.6F, 8.0F, 8.3F);
            }
            case BASS -> {
                model.body.translateAndRotate(ps);
                float[] at = StarPoses.BASS_AT;
                ps.translate(px(at[0]), px(at[1]), px(at[2]));
                ps.mulPose(Axis.ZP.rotation(Mth.PI - StarPoses.BASS_ANGLE));
                ps.mulPose(Axis.YP.rotation(-0.12F));
                ps.scale(0.46F, 0.46F, 0.46F);
                anchor(ps, 17.1F, 8.0F, 8.3F);
            }
            case VOICE -> {
                model.rightArm.translateAndRotate(ps);
                ps.translate(px(-1.0F), px(9.6F), px(-0.4F));
                ps.mulPose(Axis.XP.rotation(-Mth.HALF_PI));
                ps.scale(0.55F, 0.55F, 0.55F);
                anchor(ps, 8.0F, 3.0F, 8.0F);
            }
            case DRUMS -> {
                ps.translate(0, px(24), px(-11));
                ps.mulPose(Axis.ZP.rotation(Mth.PI));
                ps.scale(0.95F, 0.95F, 0.95F);
                anchor(ps, 8.0F, 0.0F, 8.0F);
            }
            case KEYS -> {
                ps.translate(0, px(24), px(-8));
                ps.mulPose(Axis.XP.rotation(Mth.PI));
                ps.scale(0.75F, 0.75F, 0.75F);
                anchor(ps, 8.0F, 0.0F, 6.0F);
            }
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                ps, buffers, entity.level(), entity.getId());
        ps.popPose();
    }
}
