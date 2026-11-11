package com.starlevi.starcompat.client;

import com.starlevi.starcompat.StarCompat;
import traben.entity_model_features.EMFAnimationApi;

/**
 * Se engancha al final de cada animacion de EMF (API oficial de EMF) y deja que Cinematic Interactions
 * vuelva a poner su pose encima. EMF nunca se apaga: sigue animando todo lo que Cinematic no toca.
 * Esta clase solo se carga si EMF esta instalado.
 */
final class EmfCinematicHook extends EMFAnimationApi.EMFAnimationHook {

    private EmfCinematicHook() {
    }

    static void register() {
        if (!CinematicPose.available()) {
            StarCompat.LOGGER.warn("[Starcompat] Cinematic Interactions cambio por dentro; la compatibilidad con EMF queda apagada");
            return;
        }
        try {
            EMFAnimationApi.registerAnimationHook(new EmfCinematicHook());
            StarCompat.LOGGER.info("[Starcompat] Compatibilidad Cinematic Interactions + EMF activa");
        } catch (Throwable t) {
            StarCompat.LOGGER.warn("[Starcompat] No se pudo registrar el hook de EMF", t);
        }
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        CinematicPose.reapply();
    }
}
