package com.starlevi.starcombat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/** Los Colmillos de Brasa. El golpe normal del juego no se usa: los ataques los maneja el mod. */
public final class BladesItem extends Item {
    public BladesItem() {
        super(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        return true;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.starcombat.ember_fangs.lore").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        lines.add(Component.empty());
        for (int i = 1; i <= 5; i++) {
            lines.add(Component.translatable("item.starcombat.ember_fangs.tip" + i).withStyle(ChatFormatting.GRAY));
        }
    }
}
