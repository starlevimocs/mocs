package com.starlevi.starhero.client;

import com.starlevi.starhero.StarHero;
import traben.entity_model_features.EMFAnimationApi;

/**
 * Despues de que EMF (Fresh Animations) anima un modelo, Star Hero vuelve a poner la pose de la banda
 * encima. EMF sigue animando todo lo demas. Solo se carga si EMF esta instalado.
 */
final class StarEmfHook extends EMFAnimationApi.EMFAnimationHook {
    private StarEmfHook() {
    }

    static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new StarEmfHook());
            StarHero.LOGGER.info("[Star Hero] Hook de EMF activo");
        } catch (Throwable t) {
            StarHero.LOGGER.warn("[Star Hero] No se pudo registrar el hook de EMF", t);
        }
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        ClientEvents.afterEmf();
    }
}
