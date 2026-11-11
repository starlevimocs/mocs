package net.mca.client.gui.dreams;

import net.mca.client.gui.VnStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Dreams: boton con el estilo de la interfaz de novela visual. */
public class VnButton extends ButtonWidget {
    private final boolean selected;

    public VnButton(int x, int y, int w, int h, Text message, boolean selected, Text tooltip, PressAction onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.selected = selected;
        if (tooltip != null) {
            setTooltip(Tooltip.of(tooltip));
        }
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hover = active && isHovered();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        if (selected) {
            context.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xF0463858, 0xF02A2138);
            context.fill(x + 1, y, x + w - 1, y + 1, VnStyle.BORDER_HOVER);
            context.fill(x + 1, y + h - 1, x + w - 1, y + h, VnStyle.BORDER_HOVER);
            context.fill(x, y + 1, x + 1, y + h - 1, VnStyle.BORDER_HOVER);
            context.fill(x + w - 1, y + 1, x + w, y + h - 1, VnStyle.BORDER_HOVER);
        } else {
            int fill = !active ? 0x90181421 : hover ? 0xF0302640 : 0xD81C1626;
            int border = !active ? 0x40E2C98F : hover ? VnStyle.BORDER_HOVER : 0x70E2C98F;
            VnStyle.panel(context, x, y, w, h, fill, border);
            if (hover) {
                context.fill(x + 2, y + 3, x + 4, y + h - 3, VnStyle.ACCENT);
            }
        }
        int color = !active ? VnStyle.TEXT_DIM : (hover || selected) ? VnStyle.ACCENT : VnStyle.TEXT;
        var font = MinecraftClient.getInstance().textRenderer;
        context.drawCenteredTextWithShadow(font, getMessage(), x + w / 2 + (hover ? 1 : 0), y + (h - 8) / 2, color);
    }
}
