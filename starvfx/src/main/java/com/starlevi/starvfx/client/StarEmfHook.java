package com.starlevi.starvfx.client;

import com.starlevi.starvfx.StarVfx;
import traben.entity_model_features.EMFAnimationApi;

/** Despues de que EMF anima un modelo, las poses de Star VFX se vuelven a poner encima. */
final class StarEmfHook extends EMFAnimationApi.EMFAnimationHook {
    private StarEmfHook() {
    }

    static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new StarEmfHook());
            StarVfx.LOGGER.info("[Star VFX] Hook de EMF activo");
        } catch (Throwable t) {
            StarVfx.LOGGER.warn("[Star VFX] No se pudo registrar el hook de EMF", t);
        }
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        ClientEvents.afterEmf();
    }
}
