package com.starlevi.dreams.compat;

import com.starlevi.dreams.DreamsMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Sabe a que entidad se esta dibujando y si Cinematic Interactions la tiene posando en una estacion.
 * No depende de Cinematic en tiempo de compilacion: lo consulta por reflexion.
 */
@Mod.EventBusSubscriber(modid = DreamsMod.MOD_ID, value = Dist.CLIENT)
public final class CinematicEmfCompat {
    private static int renderingId = -1;

    private static boolean resolved;
    private static MethodHandle atStation; // (int) -> StationKind|null

    private CinematicEmfCompat() {
    }

    /** true mientras se dibuja a una entidad que Cinematic tiene en una estacion. */
    public static boolean cinematicIsPosing() {
        if (renderingId < 0) {
            return false;
        }
        MethodHandle handle = handle();
        if (handle == null) {
            return false;
        }
        try {
            return handle.invoke(renderingId) != null;
        } catch (Throwable t) {
            atStation = null; // algo cambio en Cinematic: deja de intentar
            return false;
        }
    }

    private static MethodHandle handle() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> payloads = Class.forName("dev.dyslexic.cinematic.client.ClientPayloads");
                Class<?> kind = Class.forName("dev.dyslexic.cinematic.station.StationKind");
                atStation = MethodHandles.publicLookup()
                        .findStatic(payloads, "atStation", MethodType.methodType(kind, int.class))
                        .asType(MethodType.methodType(Object.class, int.class));
            } catch (Throwable ignored) {
                atStation = null; // Cinematic no esta instalado
            }
        }
        return atStation;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        renderingId = event.isCanceled() ? -1 : entity.getId();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        renderingId = -1;
    }
}
