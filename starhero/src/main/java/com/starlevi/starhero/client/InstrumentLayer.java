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

    private static ItemStack sticks;

    private static ItemStack sticks() {
        if (sticks == null) {
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                    new net.minecraft.resources.ResourceLocation("starhero", "drumstick"));
            sticks = item == null ? ItemStack.EMPTY : new ItemStack(item);
        }
        return sticks;
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
        GigClient.Performer p = GigClient.performer(entity.getId());
        if (p == null) {
            return;
        }
        // lo que esta apoyado en el piso no sube ni baja con el musico (rebote, sentarse)
        float lift = p.oy / (entity instanceof net.minecraft.world.entity.player.Player ? 0.9375F : 1F);
        ps.pushPose();
        switch (instrument) {
            case GUITAR, BASS -> {
                boolean bass = instrument == Instrument.BASS;
                float[] at = bass ? StarPoses.BASS_AT : StarPoses.GUITAR_AT;
                float scale = bass ? StarPoses.BASS_SCALE : StarPoses.GUITAR_SCALE;
                model.body.translateAndRotate(ps);
                ps.translate(px(at[0]), px(at[1]), px(at[2]));
                ps.mulPose(Axis.ZP.rotation(Mth.PI - p.guitarAngle));
                ps.mulPose(Axis.YP.rotation(-0.12F));
                ps.scale(scale, scale, scale);
                anchor(ps, bass ? 17.6F : 15.1F, 8.0F, 8.3F);
            }
            case VOICE -> {
                model.rightArm.translateAndRotate(ps);
                ps.translate(px(-1.0F), px(9.4F), px(-0.6F));
                ps.mulPose(Axis.XP.rotation(-Mth.HALF_PI));
                ps.scale(0.8F, 0.8F, 0.8F);
                anchor(ps, 8.0F, 3.0F, 8.0F);
            }
            case DRUMS -> {
                // palillos en las dos manos (siguen la linea del brazo hasta el parche)
                ItemStack stick = sticks();
                for (int side = 0; side < 2 && !stick.isEmpty(); side++) {
                    ps.pushPose();
                    (side == 0 ? model.rightArm : model.leftArm).translateAndRotate(ps);
                    ps.translate(px(side == 0 ? -1.0F : 1.0F), px(9.0F), px(-0.3F));
                    ps.mulPose(Axis.XP.rotation(Mth.PI));
                    ps.mulPose(Axis.ZP.rotation(Mth.PI));
                    ps.scale(0.9F, 0.9F, 0.9F);
                    anchor(ps, 8.0F, 1.0F, 8.0F);
                    Minecraft.getInstance().getItemRenderer().renderStatic(stick, ItemDisplayContext.NONE, light,
                            OverlayTexture.NO_OVERLAY, ps, buffers, entity.level(), entity.getId());
                    ps.popPose();
                }
                ps.translate(0, px(24) + lift, px(StarPoses.KIT_Z));
                ps.mulPose(Axis.ZP.rotation(Mth.PI));
                ps.scale(StarPoses.KIT_SCALE, 1.0F, StarPoses.KIT_SCALE);
                anchor(ps, 8.0F, 0.0F, 8.0F);
            }
            case KEYS -> {
                ps.translate(0, px(24) + lift, px(StarPoses.KEYS_Z));
                ps.mulPose(Axis.XP.rotation(Mth.PI));
                ps.scale(StarPoses.KEYS_SCALE, StarPoses.KEYS_SCALE_Y, StarPoses.KEYS_SCALE);
                anchor(ps, 8.0F, 0.0F, 6.0F);
            }
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                ps, buffers, entity.level(), entity.getId());
        ps.popPose();
    }
}
