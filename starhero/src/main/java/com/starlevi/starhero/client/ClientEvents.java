package com.starlevi.starhero.client;

import com.mojang.brigadier.Command;
import com.starlevi.starhero.StarHero;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Eventos del cliente. */
public final class ClientEvents {
    private static LivingEntity rendering;
    private static HumanoidModel<?> renderingModel;

    private ClientEvents() {
    }

    /** Lo llama el hook de EMF cuando termina de animar el modelo que se esta dibujando. */
    static void afterEmf() {
        if (rendering != null && renderingModel != null) {
            StarPoses.apply(rendering, renderingModel);
        }
    }

    @Mod.EventBusSubscriber(modid = StarHero.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                if (ModList.get().isLoaded("entity_model_features")) {
                    StarEmfHook.register();
                }
                CubOsuBridge.listen(GigClient::onCubOsuFinish);
            });
        }

        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static void layers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                EntityRenderer<?> r = event.getSkin(skin);
                if (r instanceof LivingEntityRenderer lr && lr.getModel() instanceof HumanoidModel) {
                    lr.addLayer(new InstrumentLayer(lr));
                }
            }
            for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
                if (type == EntityType.PLAYER) {
                    continue;
                }
                try {
                    Object r = event.getRenderer((EntityType) type);
                    if (r instanceof LivingEntityRenderer lr && lr.getModel() instanceof HumanoidModel) {
                        lr.addLayer(new InstrumentLayer(lr));
                    }
                } catch (ClassCastException ignored) {
                    // no es una entidad viva
                }
            }
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("gig", GigHud::render);
        }
    }

    @Mod.EventBusSubscriber(modid = StarHero.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                GigClient.tick();
            }
        }

        @SubscribeEvent
        public static void renderPre(RenderLivingEvent.Pre<?, ?> event) {
            rendering = event.getEntity();
            renderingModel = event.getRenderer().getModel() instanceof HumanoidModel<?> h ? h : null;
        }

        @SubscribeEvent
        public static void renderPost(RenderLivingEvent.Post<?, ?> event) {
            rendering = null;
            renderingModel = null;
        }

        @SubscribeEvent
        public static void hand(RenderHandEvent event) {
            if (StarCamera.last != null && StarCamera.active()) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void fov(ViewportEvent.ComputeFov event) {
            StarCamera.View v = StarCamera.last;
            if (v != null && StarCamera.active() && event.usedConfiguredFov()) {
                event.setFOV(v.fov());
            }
        }

        @SubscribeEvent
        public static void angles(ViewportEvent.ComputeCameraAngles event) {
            StarCamera.View v = StarCamera.last;
            if (v != null && StarCamera.active()) {
                event.setRoll(v.roll());
            }
        }

        @SubscribeEvent
        public static void commands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("camara_show").executes(ctx -> {
                StarCamera.enabled = !StarCamera.enabled;
                Minecraft.getInstance().gui.getChat().addMessage(Component.translatable(
                        StarCamera.enabled ? "message.starhero.camera_on" : "message.starhero.camera_off"));
                return Command.SINGLE_SUCCESS;
            }));
        }
    }
}
