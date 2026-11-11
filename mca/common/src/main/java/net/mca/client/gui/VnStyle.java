package net.mca.client.gui;

import net.minecraft.client.gui.DrawContext;

/**
 * Dreams: estilo visual de novela visual para MCA (caja de dialogo, cartel de nombre, opciones y botones).
 */
public final class VnStyle {
    public static final int PANEL = 0xE81A1424;
    public static final int PANEL_SOFT = 0xD0181421;
    public static final int BORDER = 0x90E2C98F;
    public static final int BORDER_HOVER = 0xFFF5D98A;
    public static final int TEXT = 0xFFF6F0E4;
    public static final int TEXT_DIM = 0xFFAFA6BC;
    public static final int ACCENT = 0xFFF5D98A;

    public static final int NAME_MALE = 0xFF6FB3EA;
    public static final int NAME_FEMALE = 0xFFEB8DB5;
    public static final int NAME_OTHER = 0xFFD9BE7E;

    private VnStyle() {
    }

    public static int alpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, alpha)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t), g = Math.round(ag + (bg - ag) * t), bl = Math.round(ab + (bb - ab) * t);
        return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }

    /** Rectangulo con esquinas recortadas (1 px). */
    private static void rect(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x + 1, y, x + w - 1, y + h, color);
        c.fill(x, y + 1, x + 1, y + h - 1, color);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void outline(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x + 1, y, x + w - 1, y + 1, color);
        c.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        c.fill(x, y + 1, x + 1, y + h - 1, color);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void diamond(DrawContext c, int cx, int cy, int color) {
        c.fill(cx - 1, cy - 2, cx + 1, cy + 2, color);
        c.fill(cx - 2, cy - 1, cx + 2, cy + 1, color);
    }

    /** Panel simple (ficha del aldeano, botones). */
    public static void panel(DrawContext c, int x, int y, int w, int h, int fill, int border) {
        rect(c, x, y, w, h, fill);
        c.fillGradient(x + 1, y + 1, x + w - 1, y + h / 2, 0x14FFFFFF, 0x00FFFFFF);
        outline(c, x, y, w, h, border);
    }

    /** La caja de dialogo: sombra, degradado, doble borde y adornos dorados en las esquinas. */
    public static void box(DrawContext c, int x, int y, int w, int h, float a) {
        rect(c, x + 2, y + 3, w, h, alpha(0x70000000, a));
        c.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, alpha(0xF2261D33, a), alpha(0xF2110D17, a));
        c.fill(x + 1, y, x + w - 1, y + 1, alpha(BORDER, a));
        outline(c, x, y, w, h, alpha(BORDER, a));
        outline(c, x + 3, y + 3, w - 6, h - 6, alpha(0x30E2C98F, a));
        c.fill(x + 12, y + 1, x + w - 12, y + 2, alpha(0x40FFFFFF, a)); // brillo de arriba
        int g = alpha(ACCENT, a);
        diamond(c, x + 3, y + 3, g);
        diamond(c, x + w - 4, y + 3, g);
        diamond(c, x + 3, y + h - 4, g);
        diamond(c, x + w - 4, y + h - 4, g);
        diamond(c, x + w / 2, y + h - 1, g);
    }

    /** Cartel del nombre con una cinta del color del personaje. */
    public static void nameplate(DrawContext c, int x, int y, int w, int h, int color, float a) {
        rect(c, x + 1, y + 2, w, h, alpha(0x70000000, a));
        int dark = mix(0xFF1A1424, color, 0.35F);
        c.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, alpha(mix(dark, 0xFFFFFFFF, 0.08F), a), alpha(dark, a));
        outline(c, x, y, w, h, alpha(color, a));
        c.fill(x + 3, y + 3, x + 7, y + h - 3, alpha(color, a));
    }

    /** Opcion de respuesta. */
    public static void choice(DrawContext c, int x, int y, int w, int h, boolean hover, float a) {
        rect(c, x + 1, y + 2, w, h, alpha(0x60000000, a));
        if (hover) {
            c.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, alpha(0xF03A2E4A, a), alpha(0xF0241C30, a));
            outline(c, x, y, w, h, alpha(BORDER_HOVER, a));
            c.fill(x + 2, y + 3, x + 4, y + h - 3, alpha(ACCENT, a));
        } else {
            c.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, alpha(0xE0221A2E, a), alpha(0xE0141019, a));
            outline(c, x, y, w, h, alpha(BORDER, a));
        }
        c.fill(x + 16, y + 4, x + 17, y + h - 4, alpha(0x50E2C98F, a));
    }
}
