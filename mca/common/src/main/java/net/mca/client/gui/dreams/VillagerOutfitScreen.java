package net.mca.client.gui.dreams;

import net.mca.client.gui.VnStyle;
import net.mca.screen.dreams.VillagerOutfitScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/** Dreams: pantalla del inventario del aldeano con ranuras de ropa, dibujada con el estilo de la interfaz. */
public class VillagerOutfitScreen extends HandledScreen<VillagerOutfitScreenHandler> {
    private static final String[] LABELS = {"⛑", "✤", "‖", "⁂", "⚔", "⛨"};

    public VillagerOutfitScreen(VillagerOutfitScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 216;
        backgroundHeight = 18 + 6 * 18 + 8 + 76 + 8;
        playerInventoryTitleX = 44;
        playerInventoryTitleY = 18 + 6 * 18 - 2;
        titleX = 44;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x;
        int y = this.y;
        VnStyle.box(context, x, y, backgroundWidth, backgroundHeight, 1.0F);
        // separador entre ropa e inventario
        context.fill(x + 37, y + 14, x + 38, y + 18 + 6 * 18 - 6, 0x50E2C98F);
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot s = handler.slots.get(i);
            int sx = x + s.x - 1;
            int sy = y + s.y - 1;
            boolean outfit = i < 6;
            context.fill(sx, sy, sx + 18, sy + 18, outfit ? 0xFF3A2E4A : 0xFF231B2D);
            context.fill(sx + 1, sy + 1, sx + 17, sy + 17, outfit ? 0xFF2A2136 : 0xFF17121E);
            if (outfit && !s.hasStack()) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal(LABELS[i]), sx + 9, sy + 5, 0x80E2C98F);
            }
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, VnStyle.ACCENT, false);
        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, VnStyle.TEXT_DIM, false);
        context.drawText(textRenderer, Text.translatable("gui.dreams.outfit"), 6, titleY, VnStyle.TEXT_DIM, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
