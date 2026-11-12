package com.starlevi.starcombat.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starlevi.starcombat.StarCombat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Eventos del cliente. */
public final class ClientEvents {
    static final KeyMapping KEY_GRAPPLE = new KeyMapping("key.starcombat.grapple", InputConstants.KEY_R, "key.categories.starcombat");
    private static LivingEntity rendering;
    private static HumanoidModel<?> renderingModel;
    private static int spun = Integer.MIN_VALUE;

    private ClientEvents() {
    }

    /** Lo llama el hook de EMF cuando termina de animar el modelo que se esta dibujando. */
    static void afterEmf() {
        if (rendering != null && renderingModel != null) {
            Pose.apply(rendering, renderingModel);
        }
    }

    @Mod.EventBusSubscriber(modid = StarCombat.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                if (ModList.get().isLoaded("entity_model_features")) {
                    StarEmfHook.register();
                }
                if (ModList.get().isLoaded("punchy")) {
                    PunchyCompat.init();
                }
            });
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(KEY_GRAPPLE);
        }

        @SubscribeEvent
        public static void layers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                EntityRenderer<?> renderer = event.getSkin(skin);
                if (renderer instanceof PlayerRenderer pr) {
                    pr.addLayer(new BladeLayer(pr));
                }
            }
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "grapple", ClientEvents::grappleMark);
        }
    }

    /** Marca junto a la mira cuando hay algo a lo que engancharse. */
    private static void grappleMark(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientCombat.canGrapple || mc.options.hideGui || mc.player == null || !StarCombat.holds(mc.player)
                || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        int cx = width / 2, cy = height / 2;
        int c = 0xFFFF9A2E;
        g.fill(cx - 8, cy - 8, cx - 4, cy - 7, c);
        g.fill(cx - 8, cy - 8, cx - 7, cy - 4, c);
        g.fill(cx + 4, cy - 8, cx + 8, cy - 7, c);
        g.fill(cx + 7, cy - 8, cx + 8, cy - 4, c);
        g.fill(cx - 8, cy + 7, cx - 4, cy + 8, c);
        g.fill(cx - 8, cy + 4, cx - 7, cy + 8, c);
        g.fill(cx + 4, cy + 7, cx + 8, cy + 8, c);
        g.fill(cx + 7, cy + 4, cx + 8, cy + 8, c);
    }

    @Mod.EventBusSubscriber(modid = StarCombat.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().isPaused()) {
                ClientCombat.tick();
            }
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientCombat.clear();
        }

        /** Con los Colmillos en la mano, los clics son ataques del mod y no el golpe normal. */
        @SubscribeEvent
        public static void click(InputEvent.InteractionKeyMappingTriggered event) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p == null || mc.screen != null || !StarCombat.holds(p) || p.isSpectator()) {
                return;
            }
            if (event.isAttack()) {
                event.setCanceled(true);
                event.setSwingHand(false);
                ClientCombat.input(0);
            } else if (event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND && !ClientCombat.useIsInteraction(mc)) {
                event.setCanceled(true);
                event.setSwingHand(false);
                ClientCombat.input(1);
            }
        }

        @SubscribeEvent
        public static void level(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                Render.world(event.getPoseStack(), event.getCamera().getPosition(), event.getPartialTick());
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void renderPre(RenderLivingEvent.Pre<?, ?> event) {
            rendering = event.getEntity();
            renderingModel = event.getRenderer().getModel() instanceof HumanoidModel<?> h ? h : null;
            if (renderingModel != null && !event.isCanceled()) {
                float spin = ClientCombat.spin(ClientCombat.STATES.get(rendering.getId()));
                if (spin != 0) {
                    event.getPoseStack().pushPose();
                    event.getPoseStack().mulPose(Axis.YP.rotationDegrees(-spin));
                    spun = rendering.getId();
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void renderPost(RenderLivingEvent.Post<?, ?> event) {
            if (spun == event.getEntity().getId()) {
                event.getPoseStack().popPose();
                spun = Integer.MIN_VALUE;
            }
            rendering = null;
            renderingModel = null;
        }

        /** Primera persona sin Punchy: se esconde la hoja que vuela y se dibuja la segunda hoja en la otra mano. */
        @SubscribeEvent
        public static void hand(RenderHandEvent event) {
            if (PunchyCompat.active) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p == null || !StarCombat.holds(p)) {
                return;
            }
            int side = ClientCombat.armSide(p, event.getHand());
            boolean flying = ClientCombat.flying(p, side);
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                if (flying) {
                    event.setCanceled(true);
                }
            } else if (p.getOffhandItem().isEmpty() && !flying && !p.isInvisible()) {
                PoseStack ps = event.getPoseStack();
                ps.pushPose();
                ps.translate(side * 0.56F, -0.52F - event.getEquipProgress() * 0.6F, -0.72F);
                mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(p, p.getMainHandItem(),
                        side > 0 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                        side < 0, ps, event.getMultiBufferSource(), event.getPackedLight());
                ps.popPose();
            }
        }

        @SubscribeEvent
        public static void camera(ViewportEvent.ComputeCameraAngles event) {
            float s = ClientCombat.shake;
            if (s > 0) {
                double t = net.minecraft.Util.getMillis() / 1000.0;
                event.setRoll(event.getRoll() + (float) Math.sin(t * 71) * s * 1.6F);
                event.setPitch(event.getPitch() + (float) Math.sin(t * 53 + 1.7) * s * 0.9F);
            }
        }
    }
}
