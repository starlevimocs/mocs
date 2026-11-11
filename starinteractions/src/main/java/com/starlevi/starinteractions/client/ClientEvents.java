package com.starlevi.starinteractions.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.starlevi.starinteractions.StarInteractions;
import com.starlevi.starinteractions.net.Net;
import com.starlevi.starinteractions.server.McaBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.Input;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Eventos del cliente: teclas, pedido en pantalla, poses al dibujar y bloqueo de movimiento. */
public final class ClientEvents {
    private static final String CATEGORY = "key.categories.starinteractions";
    static final KeyMapping KEY_MENU = new KeyMapping("key.starinteractions.menu", InputConstants.KEY_G, CATEGORY);
    static final KeyMapping KEY_TOUCH = new KeyMapping("key.starinteractions.touch", InputConstants.KEY_H, CATEGORY);
    static final KeyMapping KEY_ACCEPT = new KeyMapping("key.starinteractions.accept", InputConstants.KEY_Y, CATEGORY);
    static final KeyMapping KEY_DECLINE = new KeyMapping("key.starinteractions.decline", InputConstants.KEY_N, CATEGORY);

    private static LivingEntity rendering;
    private static HumanoidModel<?> renderingModel;
    private static long emoteStopAt;

    private ClientEvents() {
    }

    /** Lo llama el hook de EMF cuando termina de animar el modelo que se esta dibujando. */
    static void afterEmf() {
        if (rendering != null && renderingModel != null) {
            Poses.apply(rendering, renderingModel);
        }
    }

    /** Persona (jugador o aldeano de MCA) a la que se esta mirando, o la mas cercana enfrente. */
    static Entity findTarget(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        if (mc.hitResult instanceof EntityHitResult hit && valid(hit.getEntity()) && hit.getEntity() != mc.player) {
            return hit.getEntity();
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        Entity best = null;
        double bestScore = 0.80;
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(4.5))) {
            if (!valid(e)) {
                continue;
            }
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > 4.5 || dist < 0.01) {
                continue;
            }
            double score = to.scale(1 / dist).dot(look) - dist * 0.02;
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private static boolean valid(Entity e) {
        return e.isAlive() && !e.isInvisible() && (e instanceof Player p && !p.isSpectator() || McaBridge.isVillager(e));
    }

    private static boolean promptActive() {
        Net.Prompt p = ClientNet.prompt;
        return p != null && Util.getMillis() - ClientNet.promptAt < p.seconds() * 1000L;
    }

    private static void answer(boolean accept) {
        Net.toServer(new Net.Answer(accept));
        ClientNet.prompt = null;
    }

    /** Cartel del pedido: "Fulano quiere: abrazo  [Y] aceptar  [N] rechazar" con la barra del tiempo que queda. */
    private static void renderPrompt(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (!promptActive() || Minecraft.getInstance().options.hideGui) {
            return;
        }
        Net.Prompt p = ClientNet.prompt;
        Font font = gui.getFont();
        float left = 1F - (Util.getMillis() - ClientNet.promptAt) / (p.seconds() * 1000F);
        float in = Mth.clamp((Util.getMillis() - ClientNet.promptAt) / 220F, 0, 1);
        Component line1 = Component.translatable("gui.starinteractions.prompt", Component.literal(p.name()).withStyle(ChatFormatting.YELLOW),
                Component.translatable("interaction.starinteractions." + p.interaction()).withStyle(ChatFormatting.LIGHT_PURPLE));
        Component line2 = Component.translatable("gui.starinteractions.prompt_keys",
                KEY_ACCEPT.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.GREEN),
                KEY_DECLINE.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.RED));
        int w = Math.max(font.width(line1), font.width(line2)) + 24;
        int x = (width - w) / 2;
        int y = (int) (height * 0.62F + (1 - in) * 12);
        int a = (int) (in * 0xE0) << 24;
        g.fill(x, y, x + w, y + 34, a | 0x140C24);
        g.fill(x, y, x + w, y + 1, (int) (in * 255) << 24 | 0xE2C98F);
        g.fill(x, y + 32, x + (int) (w * Mth.clamp(left, 0, 1)), y + 34, (int) (in * 255) << 24 | 0xFF8FC7);
        if (in > 0.1F) {
            g.drawCenteredString(font, line1, width / 2, y + 6, 0xFFFFFF);
            g.drawCenteredString(font, line2, width / 2, y + 19, 0xC8C0DC);
        }
    }

    @Mod.EventBusSubscriber(modid = StarInteractions.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
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
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(KEY_MENU);
            event.register(KEY_TOUCH);
            event.register(KEY_ACCEPT);
            event.register(KEY_DECLINE);
        }

        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static void layers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                EntityRenderer<?> r = event.getSkin(skin);
                if (r instanceof LivingEntityRenderer lr && lr.getModel() instanceof HumanoidModel) {
                    lr.addLayer(new PartCaptureLayer(lr));
                }
            }
            for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
                if (type == EntityType.PLAYER) {
                    continue;
                }
                try {
                    Object r = event.getRenderer((EntityType) type);
                    if (r instanceof LivingEntityRenderer lr && lr.getModel() instanceof HumanoidModel) {
                        lr.addLayer(new PartCaptureLayer(lr));
                    }
                } catch (ClassCastException ignored) {
                    // no es una entidad viva
                }
            }
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("prompt", ClientEvents::renderPrompt);
        }
    }

    @Mod.EventBusSubscriber(modid = StarInteractions.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            Poses.clientTick(mc);
            if (mc.player == null || mc.level == null) {
                ClientNet.prompt = null;
                return;
            }
            if (ClientNet.prompt != null && !promptActive()) {
                ClientNet.prompt = null;
            }
            while (KEY_ACCEPT.consumeClick()) {
                if (promptActive()) {
                    answer(true);
                }
            }
            while (KEY_DECLINE.consumeClick()) {
                if (promptActive()) {
                    answer(false);
                }
            }
            while (KEY_MENU.consumeClick()) {
                if (mc.screen == null && !Poses.isLocalLocked()) {
                    mc.setScreen(new InteractionScreen(findTarget(mc)));
                }
            }
            while (KEY_TOUCH.consumeClick()) {
                if (mc.screen == null && !Poses.isLocalLocked()) {
                    Entity target = findTarget(mc);
                    if (target != null) {
                        TouchClient.request(target.getId());
                    } else {
                        mc.player.displayClientMessage(Component.translatable("message.starinteractions.no_target")
                                .withStyle(ChatFormatting.GRAY), true);
                    }
                }
            }
            // un emote se corta apenas uno se mueve
            Input in = mc.player.input;
            if (in != null && Poses.emoteOf(mc.player) != null && (in.forwardImpulse != 0 || in.leftImpulse != 0 || in.jumping)
                    && Util.getMillis() - emoteStopAt > 500) {
                emoteStopAt = Util.getMillis();
                Poses.emote(mc.player.getId(), "");
                Net.toServer(new Net.Emote(""));
            }
        }

        /** En medio de un abrazo, un baile, etc. nadie camina: los dos quedan en su lugar. */
        @SubscribeEvent
        public static void input(MovementInputUpdateEvent event) {
            if (Poses.isLocalLocked()) {
                Input in = event.getInput();
                in.forwardImpulse = 0;
                in.leftImpulse = 0;
                in.up = false;
                in.down = false;
                in.left = false;
                in.right = false;
                in.jumping = false;
                in.shiftKeyDown = false;
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void renderPre(RenderLivingEvent.Pre<?, ?> event) {
            rendering = event.getEntity();
            renderingModel = event.getRenderer().getModel() instanceof HumanoidModel<?> h ? h : null;
            if (renderingModel != null) {
                Poses.renderPre(rendering, event.getPartialTick(), event.getPoseStack());
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void renderPost(RenderLivingEvent.Post<?, ?> event) {
            Poses.renderPost(event.getEntity(), event.getPoseStack());
            rendering = null;
            renderingModel = null;
        }

        @SubscribeEvent
        public static void level(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
                TouchClient.captureCamera(event.getPoseStack().last().pose(), event.getCamera().getPosition());
            }
        }

        /** Un poco de zoom al entrar al modo toque. */
        @SubscribeEvent
        public static void fov(ViewportEvent.ComputeFov event) {
            if (TouchClient.open() && event.usedConfiguredFov()) {
                float open = Mth.clamp((Util.getMillis() - TouchClient.openedAt) / 350F, 0, 1);
                open = open * open * (3 - 2 * open);
                event.setFOV(event.getFOV() * (1 - 0.22F * open));
            }
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            Poses.clear();
            ClientNet.prompt = null;
            TouchClient.targetId = -1;
        }
    }
}
