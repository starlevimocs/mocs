package com.starlevi.starnpcs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.starlevi.starnpcs.NpcEntity;
import com.starlevi.starnpcs.Profile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;

/**
 * Dibuja al NPC. Con huesos propios recorre el modelo de Blockbench y le aplica la animacion; sin ellos usa el
 * modelo de jugador vanilla (con armadura y objetos), que es el que pueden tocar otros mods y packs.
 */
public class NpcRenderer extends EntityRenderer<NpcEntity> {
    private final Vanilla wide;
    private final Vanilla slim;

    public NpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.5F;
        wide = new Vanilla(ctx, false);
        slim = new Vanilla(ctx, true);
    }

    static String modelId(Profile p) {
        return p.slim && p.model.equals("human") ? "human_slim" : p.model;
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity e) {
        return Skins.get(e.profile());
    }

    @Override
    public void render(NpcEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Profile p = e.profile();
        shadowRadius = 0.5F * p.scale;
        BbModel model = p.starBones ? ClientAssets.model(modelId(p)) : null;
        if (model == null) {
            (p.slim ? slim : wide).render(e, yaw, pt, ps, buf, light);
        } else {
            star(e, p, model, pt, ps, buf, light);
        }
        labels(e, p, ps, buf, light);
    }

    // ------------------------------------------------------------------ modelo de Blockbench

    private void star(NpcEntity e, Profile p, BbModel model, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        double now = e.level().getGameTime() + (double) pt;
        NpcAnim st = NpcAnim.of(e);
        float[] pose = st.pose(e, model, now);
        // la cabeza sigue al jugador encima de la animacion
        BbModel.Bone head = model.bones.get("head");
        if (head != null) {
            float bodyYaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
            float headYaw = Mth.wrapDegrees(Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot) - bodyYaw);
            float pitch = Mth.lerp(pt, e.xRotO, e.getXRot());
            int o = head.index * NpcAnim.STRIDE;
            pose[o] -= Mth.clamp(pitch, -45F, 45F) * Mth.DEG_TO_RAD;
            pose[o + 1] -= Mth.clamp(headYaw, -75F, 75F) * Mth.DEG_TO_RAD;
        }
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180F - Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot)));
        float s = p.scale * 0.9375F / 16F;
        ps.scale(s, s, s);
        int overlay = OverlayTexture.pack(OverlayTexture.u(0F), OverlayTexture.v(e.hurtTime > 0 || e.deathTime > 0));
        ResourceLocation skin = Skins.get(p);
        for (int t = 0; t < model.textures.size(); t++) {
            BbModel.Tex tex = model.textures.get(t);
            ResourceLocation loc = tex.skin ? skin : tex.loc;
            if (loc == null) {
                continue;
            }
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(loc));
            for (BbModel.Bone root : model.roots) {
                bone(root, pose, st.visible, ps, vc, t, light, overlay, null, null);
            }
        }
        // objetos en las manos
        if (!e.getMainHandItem().isEmpty() || !e.getOffhandItem().isEmpty()) {
            for (BbModel.Bone root : model.roots) {
                bone(root, pose, st.visible, ps, null, -1, light, overlay, e, buf);
            }
        }
        ps.popPose();
    }

    private void bone(BbModel.Bone b, float[] pose, boolean[] visible, PoseStack ps, VertexConsumer vc, int tex,
                      int light, int overlay, NpcEntity items, MultiBufferSource buf) {
        if (b.skip || !visible[b.index]) {
            return;
        }
        int o = b.index * NpcAnim.STRIDE;
        ps.pushPose();
        ps.translate(b.ox + pose[o + 3], b.oy + pose[o + 4], b.oz + pose[o + 5]);
        float rx = b.rx + pose[o], ry = b.ry + pose[o + 1], rz = b.rz + pose[o + 2];
        if (rx != 0 || ry != 0 || rz != 0) {
            ps.mulPose(new Quaternionf().rotationZYX(rz, ry, rx));
        }
        if (pose[o + 6] != 1 || pose[o + 7] != 1 || pose[o + 8] != 1) {
            ps.scale(pose[o + 6], pose[o + 7], pose[o + 8]);
        }
        ps.translate(-b.ox, -b.oy, -b.oz);
        if (vc != null) {
            for (BbModel.Cube c : b.cubes) {
                cube(c, ps, vc, tex, light, overlay);
            }
        } else if (b.item != 0 && items != null) {
            boolean left = b.item == 2;
            ItemStack stack = left ? items.getOffhandItem() : items.getMainHandItem();
            if (!stack.isEmpty()) {
                ps.pushPose();
                ps.translate(b.ox, b.oy, b.oz);
                // al espacio de las partes vanilla (bloques, Y hacia abajo) y de ahi como ItemInHandLayer
                ps.scale(-16F, -16F, 16F);
                ps.mulPose(Axis.XP.rotationDegrees(-90F));
                ps.mulPose(Axis.YP.rotationDegrees(180F));
                ps.translate((left ? -1 : 1) / 16F, 0.125F, 0F);
                Minecraft.getInstance().getItemRenderer().renderStatic(items, stack,
                        left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        left, ps, buf, items.level(), light, OverlayTexture.NO_OVERLAY, items.getId());
                ps.popPose();
            }
        }
        for (BbModel.Bone k : b.kids) {
            bone(k, pose, visible, ps, vc, tex, light, overlay, items, buf);
        }
        ps.popPose();
    }

    private static void cube(BbModel.Cube c, PoseStack ps, VertexConsumer vc, int tex, int light, int overlay) {
        boolean any = false;
        for (BbModel.Face f : c.faces) {
            if (f != null && f.tex == tex) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        if (c.rotated) {
            ps.pushPose();
            ps.translate(c.ox, c.oy, c.oz);
            ps.mulPose(new Quaternionf().rotationZYX(c.rz, c.ry, c.rx));
            ps.translate(-c.ox, -c.oy, -c.oz);
        }
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float x0 = c.x0, y0 = c.y0, z0 = c.z0, x1 = c.x1, y1 = c.y1, z1 = c.z1;
        // orden de caras: norte, sur, este, oeste, arriba, abajo. Esquinas: arriba-izq, arriba-der, abajo-der, abajo-izq
        face(c.faces[0], tex, vc, m, n, light, overlay, 0, 0, -1, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0);
        face(c.faces[1], tex, vc, m, n, light, overlay, 0, 0, 1, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1);
        face(c.faces[2], tex, vc, m, n, light, overlay, 1, 0, 0, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1);
        face(c.faces[3], tex, vc, m, n, light, overlay, -1, 0, 0, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0);
        face(c.faces[4], tex, vc, m, n, light, overlay, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        face(c.faces[5], tex, vc, m, n, light, overlay, 0, -1, 0, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
        if (c.rotated) {
            ps.popPose();
        }
    }

    private static void face(BbModel.Face f, int tex, VertexConsumer vc, Matrix4f m, Matrix3f n, int light, int overlay,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        if (f == null || f.tex != tex) {
            return;
        }
        // uv de las cuatro esquinas, girado de a 90 grados si la cara lo pide
        float[] us = {f.u0, f.u1, f.u1, f.u0};
        float[] vs = {f.v0, f.v0, f.v1, f.v1};
        int r = f.rot;
        vertex(vc, m, n, ax, ay, az, us[(4 - r) & 3], vs[(4 - r) & 3], light, overlay, nx, ny, nz);
        vertex(vc, m, n, bx, by, bz, us[(5 - r) & 3], vs[(5 - r) & 3], light, overlay, nx, ny, nz);
        vertex(vc, m, n, cx, cy, cz, us[(6 - r) & 3], vs[(6 - r) & 3], light, overlay, nx, ny, nz);
        vertex(vc, m, n, dx, dy, dz, us[(7 - r) & 3], vs[(7 - r) & 3], light, overlay, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                               int light, int overlay, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    // ------------------------------------------------------------------ nombre, titulo y globo de texto

    private void labels(NpcEntity e, Profile p, PoseStack ps, MultiBufferSource buf, int light) {
        double dist = entityRenderDispatcher.distanceToSqr(e);
        if (dist > 32 * 32) {
            return;
        }
        Font font = getFont();
        float y = e.getBbHeight() + 0.3F;
        NpcAnim st = NpcAnim.of(e);
        boolean hasTitle = !p.title.isBlank();
        if (p.showName && !p.name.isBlank()) {
            if (hasTitle) {
                line(font, Component.literal(p.title).getVisualOrderText(), y, 0.7F, 0xFFE9C46A, ps, buf, light);
                y += 0.2F;
            }
            line(font, Component.literal(p.name).getVisualOrderText(), y, 1F, 0xFFFFFFFF, ps, buf, light);
            y += 0.3F;
        }
        if (st.bubble != null) {
            if (e.level().getGameTime() > st.bubbleUntil) {
                st.bubble = null;
            } else {
                List<FormattedCharSequence> lines = font.split(Component.literal(st.bubble), 170);
                for (int i = lines.size() - 1; i >= 0; i--) {
                    line(font, lines.get(i), y, 0.9F, 0xFFFFF4D6, ps, buf, light);
                    y += 0.235F;
                }
            }
        }
    }

    private void line(Font font, FormattedCharSequence text, float y, float scale, int color, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        ps.translate(0F, y, 0F);
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        ps.scale(-0.025F * scale, -0.025F * scale, 0.025F * scale);
        Matrix4f m = ps.last().pose();
        int bg = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255F) << 24;
        float x = -font.width(text) / 2F;
        font.drawInBatch(text, x, 0F, 0x20FFFFFF, false, m, buf, Font.DisplayMode.SEE_THROUGH, bg, light);
        font.drawInBatch(text, x, 0F, color, false, m, buf, Font.DisplayMode.NORMAL, 0, light);
        ps.popPose();
    }

    // ------------------------------------------------------------------ modelo vanilla

    private static final class Vanilla extends HumanoidMobRenderer<NpcEntity, PlayerModel<NpcEntity>> {
        Vanilla(EntityRendererProvider.Context ctx, boolean slim) {
            super(ctx, new PlayerModel<>(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5F);
            addLayer(new HumanoidArmorLayer<>(this,
                    new HumanoidModel<>(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                    new HumanoidModel<>(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
                    ctx.getModelManager()));
        }

        @Override
        public ResourceLocation getTextureLocation(NpcEntity e) {
            return Skins.get(e.profile());
        }

        @Override
        protected void scale(NpcEntity e, PoseStack ps, float pt) {
            float s = 0.9375F * e.profile().scale;
            ps.scale(s, s, s);
        }

        @Override
        protected boolean shouldShowName(NpcEntity e) {
            return false;
        }
    }
}
