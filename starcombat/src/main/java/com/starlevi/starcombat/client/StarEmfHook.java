package com.starlevi.starcombat.client;

import com.starlevi.starcombat.StarCombat;
import traben.entity_model_features.EMFAnimationApi;

/** Despues de que EMF anima un modelo, las poses de Star Combat se vuelven a poner encima. */
final class StarEmfHook extends EMFAnimationApi.EMFAnimationHook {
    private StarEmfHook() {
    }

    static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new StarEmfHook());
            StarCombat.LOGGER.info("[Star Combat] Hook de EMF activo");
        } catch (Throwable t) {
            StarCombat.LOGGER.warn("[Star Combat] No se pudo registrar el hook de EMF", t);
        }
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        ClientEvents.afterEmf();
    }
}
