package com.starlevi.starinteractions.client;

import com.starlevi.starinteractions.StarInteractions;
import traben.entity_model_features.EMFAnimationApi;

/** Despues de que EMF anima un modelo, las poses de las interacciones se vuelven a poner encima. */
final class StarEmfHook extends EMFAnimationApi.EMFAnimationHook {
    private StarEmfHook() {
    }

    static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new StarEmfHook());
            StarInteractions.LOGGER.info("[Star Interactions] Hook de EMF activo");
        } catch (Throwable t) {
            StarInteractions.LOGGER.warn("[Star Interactions] No se pudo registrar el hook de EMF", t);
        }
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        ClientEvents.afterEmf();
    }
}
