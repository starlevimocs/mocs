package com.starlevi.starcombat.client;

import com.starlevi.starcombat.StarCombat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import punchy.api.PunchyApi;

/**
 * Compatibilidad con Punchy (animaciones en primera persona). Punchy dibuja las manos, asi que en vez de
 * dibujarlas nosotros se le pide por su API: que muestre la segunda hoja en la otra mano, que esconda la hoja
 * que salio volando y que haga su animacion de golpe con cada ataque.
 */
final class PunchyCompat {
    static boolean active;

    private PunchyCompat() {
    }

    static void init() {
        try {
            PunchyApi.heldItemLook(StarCombat.MOD_ID, PunchyCompat::look);
            // Punchy reconoce las espadas por el nombre; a los Colmillos hay que decirle que lo son
            PunchyApi.itemKind(StarCombat.MOD_ID, stack ->
                    stack instanceof ItemStack st && st.is(StarCombat.EMBER_FANGS.get()) ? "SWORD" : null);
            active = true;
            StarCombat.LOGGER.info("[Star Combat] Compatibilidad con Punchy activa");
        } catch (Throwable t) {
            StarCombat.LOGGER.warn("[Star Combat] No se pudo conectar con Punchy", t);
        }
    }

    private static Object look(Object player, boolean mainHand, Object stack) {
        if (!(player instanceof Player p) || !StarCombat.holds(p)) {
            return null;
        }
        int side = ClientCombat.armSide(p, mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (ClientCombat.flying(p, side)) {
            return mainHand || p.getOffhandItem().isEmpty() ? ItemStack.EMPTY : null;
        }
        if (!mainHand && p.getOffhandItem().isEmpty()) {
            return p.getMainHandItem();
        }
        return null;
    }

    static void attack(boolean mainHand, boolean strong) {
        if (!active) {
            return;
        }
        try {
            PunchyApi.attack(mainHand, strong);
        } catch (Throwable t) {
            active = false;
            StarCombat.LOGGER.warn("[Star Combat] Punchy no acepto el golpe; se desactiva la compatibilidad", t);
        }
    }
}
