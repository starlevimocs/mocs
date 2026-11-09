package com.starlevi.dreams.client;

import com.starlevi.dreams.DreamsMod;
import com.starlevi.dreams.cinematic.Cinematic;
import com.starlevi.dreams.cinematic.CinematicPath;
import com.starlevi.dreams.network.CinematicRequestPacket;
import com.starlevi.dreams.network.DreamsNetwork;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Reproduce una cinematica en este cliente: mueve la camara, congela al jugador, oculta el HUD
 * y dibuja barras de cine arriba y abajo. Con Esc se salta.
 */
@Mod.EventBusSubscriber(modid = DreamsMod.MOD_ID, value = Dist.CLIENT)
public final class CinematicClient {
    private static final float BAR_FRACTION = 0.12F;
    private static final long FADE_MS = 500;

    private static boolean running;
    private static boolean loop;
    private static Cinematic current;
    private static long startMs;
    private static long stopMs;

    /** El dialogo abierto pidio una cinematica; si llega tarde (ya lo cerraron) se ignora. */
    private static boolean dialogueWantsCinematic;

    private static CameraMarker camera;
    private static float eyeHeight = 1.62F;
    private static float savedYaw;
    private static float savedPitch;

    private CinematicClient() {
    }

    /** Entidad invisible que solo existe en este cliente y hace de camara. */
    private static final class CameraMarker extends Marker {
        CameraMarker(Level level) {
            super(EntityType.MARKER, level);
        }

        @Override
        protected float getEyeHeight(Pose pose, EntityDimensions dimensions) {
            // misma altura de ojos que tenia el jugador, para que la camara no "salte" al empezar
            return eyeHeight;
        }
    }

    public static boolean isRunning() {
        return running;
    }

    // ---------------------------------------------------------------- control

    public static void play(Cinematic cinematic, boolean loopMode) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null) {
            return;
        }
        if (loopMode && !dialogueWantsCinematic) {
            // la cinematica de un dialogo llego cuando el dialogo ya se habia cerrado
            DreamsNetwork.CHANNEL.sendToServer(new CinematicRequestPacket("", false));
            return;
        }
        if (running) {
            stop(false);
        }

        current = cinematic;
        loop = loopMode;
        startMs = Util.getMillis();
        stopMs = 0;
        eyeHeight = player.getEyeHeight();
        savedYaw = player.getYRot();
        savedPitch = player.getXRot();

        camera = new CameraMarker(mc.level);
        mc.setCameraEntity(camera);
        running = true;
        applyFrame(0.0);
    }

    /** @param notifyServer true si el cliente corta por su cuenta y el servidor tiene que enterarse */
    public static void stop(boolean notifyServer) {
        if (!running) {
            return;
        }
        running = false;
        stopMs = Util.getMillis();

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            mc.setCameraEntity(player);
            player.setYRot(savedYaw);
            player.setXRot(savedPitch);
            player.yRotO = savedYaw;
            player.xRotO = savedPitch;
            player.setYHeadRot(savedYaw);
        }
        camera = null;

        if (notifyServer) {
            DreamsNetwork.CHANNEL.sendToServer(new CinematicRequestPacket("", false));
        }
    }

    // ---------------------------------------------------------------- dialogos

    /** Un dialogo con "cinematic" en su JSON la pide al servidor; se repite en bucle mientras dure. */
    public static void requestForDialogue(String name) {
        dialogueWantsCinematic = true;
        DreamsNetwork.CHANNEL.sendToServer(new CinematicRequestPacket(name, true));
    }

    public static void dialogueClosed() {
        dialogueWantsCinematic = false;
        if (running && loop) {
            stop(true);
        }
    }

    // ---------------------------------------------------------------- camara

    private static void applyFrame(double ticks) {
        if (camera == null || current == null) {
            return;
        }
        CinematicPath.Pose pose = CinematicPath.evaluate(current, ticks, loop);
        double y = pose.y() - eyeHeight;
        camera.setPos(pose.x(), y, pose.z());
        camera.xo = pose.x();
        camera.yo = y;
        camera.zo = pose.z();
        camera.xOld = pose.x();
        camera.yOld = y;
        camera.zOld = pose.z();
        camera.setYRot(pose.yaw());
        camera.setXRot(pose.pitch());
        camera.yRotO = pose.yaw();
        camera.xRotO = pose.pitch();
    }

    private static void reset() {
        running = false;
        camera = null;
        current = null;
        stopMs = 0;
        dialogueWantsCinematic = false;
    }

    // ---------------------------------------------------------------- eventos

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !running) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        double ticks = (Util.getMillis() - startMs) / 50.0;
        if (!loop && ticks >= current.durationTicks()) {
            stop(true);
            return;
        }
        applyFrame(ticks);
    }

    /** El jugador no se mueve mientras mira la cinematica. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!running) {
            return;
        }
        Input input = event.getInput();
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    /** Sin atacar, usar ni elegir bloques durante la cinematica. */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (running) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Esc salta la cinematica en lugar de abrir el menu de pausa. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (running && event.getNewScreen() instanceof PauseScreen) {
            event.setCanceled(true);
            stop(true);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (running) {
            event.setCanceled(true); // sin HUD, hotbar, chat ni mira
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (running) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (running && current != null && current.hidePlayers()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    /** Barras negras de cine; aparecen y desaparecen con un fundido corto. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        float progress;
        long now = Util.getMillis();
        if (running) {
            progress = Math.min(1.0F, (now - startMs) / (float) FADE_MS);
        } else if (stopMs > 0) {
            progress = 1.0F - (now - stopMs) / (float) FADE_MS;
        } else {
            return;
        }
        if (progress <= 0.0F) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        int bar = Math.round(height * BAR_FRACTION * progress);
        graphics.fill(0, 0, width, bar, 0xFF000000);
        graphics.fill(0, height - bar, width, height, 0xFF000000);
    }
}
