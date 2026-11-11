package net.mca.client.gui;

import net.minecraft.client.gui.DrawContext;

/**
 * Dreams: colores y paneles compartidos por la interfaz estilo novela visual de MCA.
 */
public final class VnStyle {
    public static final int PANEL = 0xE0181421;
    public static final int PANEL_SOFT = 0xC0181421;
    public static final int BORDER = 0x80E8D8B0;
    public static final int BORDER_HOVER = 0xFFF2D27A;
    public static final int TEXT = 0xFFF4EFE6;
    public static final int TEXT_DIM = 0xFFA79FB3;
    public static final int ACCENT = 0xFFF2D27A;

    public static final int NAME_MALE = 0xFF8EC5F2;
    public static final int NAME_FEMALE = 0xFFF2A2C2;
    public static final int NAME_OTHER = 0xFFE6D3A3;

    private VnStyle() {
    }

    /** Panel con esquinas "recortadas" (1 px) y borde fino. */
    public static void panel(DrawContext context, int x, int y, int w, int h, int fill, int border) {
        context.fill(x + 1, y, x + w - 1, y + h, fill);
        context.fill(x, y + 1, x + 1, y + h - 1, fill);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, fill);

        context.fill(x + 1, y, x + w - 1, y + 1, border);
        context.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
        context.fill(x, y + 1, x + 1, y + h - 1, border);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
    }

    public static int alpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, alpha)));
        return (a << 24) | (color & 0xFFFFFF);
    }
}
