package com.starlevi.starvfx.api;

import com.starlevi.starvfx.client.TreeHooks;

/**
 * Para otros mods (el de dialogos de Dreams): hacer que el Arbol Madre que el jugador tiene cerca "hable".
 * Solo del lado del cliente. Del lado del servidor se usa el comando /starvfx tree.
 */
public final class TreeApi {
    private TreeApi() {
    }

    /** El arbol se tine de ese color (0xRRGGBB): el color nuevo le sube desde las raices. */
    public static void speak(int color) {
        TreeHooks.speak(color);
    }

    /** Un latido de luz (por ejemplo, con cada silaba que dice). */
    public static void pulse() {
        TreeHooks.pulse();
    }
}
