package com.starlevi.starvfx.client;

/** Puente publico hacia el arbol (lo usa {@code TreeApi}). */
public final class TreeHooks {
    private TreeHooks() {
    }

    public static void speak(int color) {
        Tree t = Tree.nearest();
        if (t != null) {
            t.speak(color, 6);
        }
    }

    public static void pulse() {
        Tree t = Tree.nearest();
        if (t != null) {
            t.pulse();
        }
    }
}
