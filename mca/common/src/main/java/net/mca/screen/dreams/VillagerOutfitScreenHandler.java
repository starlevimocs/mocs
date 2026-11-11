package net.mca.screen.dreams;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/**
 * Dreams: inventario del aldeano con ranuras de ropa (cabeza, pecho, piernas, pies) y de manos.
 * Lo que pongas ahi se lo ve puesto siempre; las ranuras vacias las sigue manejando MCA como antes.
 */
public class VillagerOutfitScreenHandler extends ScreenHandler {
    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    public final Inventory villagerInventory;
    public final Inventory outfit;
    private final VillagerEntityMCA villager;

    /** Del lado del cliente. */
    public VillagerOutfitScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(27), new SimpleInventory(6), null);
    }

    public VillagerOutfitScreenHandler(int syncId, PlayerInventory playerInventory, Inventory villagerInventory, Inventory outfit, VillagerEntityMCA villager) {
        super(DreamsScreens.VILLAGER_OUTFIT.get(), syncId);
        this.villagerInventory = villagerInventory;
        this.outfit = outfit;
        this.villager = villager;
        villagerInventory.onOpen(playerInventory.player);

        // ropa: columna a la izquierda del inventario del aldeano
        for (int i = 0; i < 4; i++) {
            final EquipmentSlot slot = SLOTS[i];
            addSlot(new Slot(outfit, i, 8, 18 + i * 18) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return slot == EquipmentSlot.HEAD || LivingEntity.getPreferredEquipmentSlot(stack) == slot;
                }

                @Override
                public int getMaxItemCount() {
                    return 1;
                }
            });
        }
        // manos: abajo de la ropa
        addSlot(new Slot(outfit, 4, 8, 18 + 4 * 18 + 4));
        addSlot(new Slot(outfit, 5, 26, 18 + 4 * 18 + 4));

        // inventario del aldeano 9x3
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(villagerInventory, col + row * 9, 44 + col * 18, 18 + row * 18));
            }
        }
        // jugador
        int py = 18 + 6 * 18 + 8;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 44 + col * 18, py + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 44 + col * 18, py + 58));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return villagerInventory.canPlayerUse(player);
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        super.onContentChanged(inventory);
        if (villager != null && inventory == outfit) {
            villager.applyDreamsOutfit();
        }
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        villagerInventory.onClose(player);
        if (villager != null) {
            villager.applyDreamsOutfit();
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        int outfitEnd = 6, villagerEnd = 6 + 27, end = slots.size();
        if (index < outfitEnd) {
            if (!insertItem(stack, villagerEnd, end, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < villagerEnd) {
            if (!insertItem(stack, villagerEnd, end, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // del jugador: primero a la ropa si corresponde, si no al inventario del aldeano
            EquipmentSlot pref = LivingEntity.getPreferredEquipmentSlot(stack);
            int target = -1;
            for (int i = 0; i < 4; i++) {
                if (SLOTS[i] == pref && !slots.get(i).hasStack()) {
                    target = i;
                }
            }
            if (target >= 0 && pref.getType() == EquipmentSlot.Type.ARMOR) {
                if (!insertItem(stack, target, target + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!insertItem(stack, outfitEnd, villagerEnd, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        return copy;
    }
}
