package com.starlevi.dreams.client;

import java.lang.reflect.Method;

/**
 * Puente con Star VFX (si esta instalado): cuando habla el Arbol Madre en un dialogo, el arbol del mundo se
 * enciende con el color de la linea y late con cada silaba. Sin Star VFX no hace nada.
 */
public final class TreeBridge {
    private static Method speak;
    private static Method pulse;
    private static boolean resolved;

    private TreeBridge() {
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> api = Class.forName("com.starlevi.starvfx.api.TreeApi");
            speak = api.getMethod("speak", int.class);
            pulse = api.getMethod("pulse");
        } catch (Throwable t) {
            speak = null;
            pulse = null;
        }
    }

    public static void speak(int color) {
        resolve();
        if (speak != null) {
            try {
                speak.invoke(null, color);
            } catch (Throwable t) {
                speak = null;
            }
        }
    }

    public static void pulse() {
        resolve();
        if (pulse != null) {
            try {
                pulse.invoke(null);
            } catch (Throwable t) {
                pulse = null;
            }
        }
    }
}
