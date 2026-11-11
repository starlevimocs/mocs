package com.starlevi.starvfx.client;

import com.starlevi.starvfx.StarVfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Eventos del cliente. */
public final class ClientEvents {
    private static LivingEntity rendering;
    private static HumanoidModel<?> renderingModel;

    private ClientEvents() {
    }

    /** Lo llama el hook de EMF cuando termina de animar el modelo que se esta dibujando. */
    static void afterEmf() {
        if (rendering != null && renderingModel != null) {
            PlayerFx.pose(rendering, renderingModel);
        }
    }

    @Mod.EventBusSubscriber(modid = StarVfx.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                if (ModList.get().isLoaded("entity_model_features")) {
                    StarEmfHook.register();
                }
            });
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("cine", Hud::render);
        }
    }

    @Mod.EventBusSubscriber(modid = StarVfx.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            ClientScene.tickAll(mc);
            Cine.tick(mc);
            PlayerFx.tick(mc);
        }

        @SubscribeEvent
        public static void level(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                Fx.renderAll(event.getPoseStack(), event.getCamera().getPosition());
            }
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void renderPre(RenderLivingEvent.Pre<?, ?> event) {
            rendering = event.getEntity();
            renderingModel = event.getRenderer().getModel() instanceof HumanoidModel<?> h ? h : null;
            if (renderingModel != null && !PlayerFx.beforeRender(rendering, event.getPoseStack())) {
                event.setCanceled(true);
                rendering = null;
                renderingModel = null;
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void renderPost(RenderLivingEvent.Post<?, ?> event) {
            PlayerFx.afterRender(event.getEntity(), event.getPoseStack());
            rendering = null;
            renderingModel = null;
        }

        /** En una cinematica nadie camina; en una zona de camara fija, uno se mueve segun lo que ve en pantalla. */
        @SubscribeEvent
        public static void input(MovementInputUpdateEvent event) {
            Input in = event.getInput();
            if (ClientScene.locked() || PlayerFx.localBusy()) {
                in.forwardImpulse = 0;
                in.leftImpulse = 0;
                in.up = false;
                in.down = false;
                in.left = false;
                in.right = false;
                in.jumping = false;
                in.shiftKeyDown = false;
                return;
            }
            if (Cine.inZone() && event.getEntity() instanceof LocalPlayer p) {
                float f = in.forwardImpulse, l = in.leftImpulse;
                float mag = Mth.sqrt(f * f + l * l);
                if (mag > 0.01F) {
                    float yaw = Cine.zoneYaw() * Mth.DEG_TO_RAD;
                    float dx = l * Mth.cos(yaw) - f * Mth.sin(yaw);
                    float dz = f * Mth.cos(yaw) + l * Mth.sin(yaw);
                    float target = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
                    p.setYRot(Mth.approachDegrees(p.getYRot(), target, 30));
                    p.setXRot(Mth.approach(p.getXRot(), 0, 6));
                    in.forwardImpulse = Math.min(mag, Math.max(Math.abs(f), Math.abs(l)));
                    in.leftImpulse = 0;
                }
            }
        }

        @SubscribeEvent
        public static void click(InputEvent.InteractionKeyMappingTriggered event) {
            if (ClientScene.locked() || PlayerFx.localBusy()) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void overlay(RenderGuiOverlayEvent.Pre event) {
            if (ClientScene.hudHidden() && "minecraft".equals(event.getOverlay().id().getNamespace())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void hand(RenderHandEvent event) {
            if (Cine.last != null) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void fov(ViewportEvent.ComputeFov event) {
            Cine.View v = Cine.last;
            if (v != null && event.usedConfiguredFov()) {
                event.setFOV(v.fov());
            }
        }

        @SubscribeEvent
        public static void angles(ViewportEvent.ComputeCameraAngles event) {
            Cine.View v = Cine.last;
            if (v != null) {
                event.setRoll(v.roll());
            } else {
                float[] s = Cine.shakeOffsets();
                if (s != null) {
                    event.setYaw(event.getYaw() + s[0] * 0.6F);
                    event.setPitch(event.getPitch() + s[1] * 0.6F);
                    event.setRoll(event.getRoll() + s[2] * 0.5F);
                }
            }
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientScene.ACTIVE.clear();
            Fx.WORLD.clear();
            PlayerFx.clear();
            Cine.ZONES.clear();
            Cine.zone = null;
            Cine.last = null;
            Hud.reset();
        }
    }
}
