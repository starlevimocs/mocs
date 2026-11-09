package com.starlevi.dreams.item;

import com.starlevi.dreams.network.SpawnNet;
import com.starlevi.dreams.spawn.Selections;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Varita de control de spawn.
 * <ul>
 *   <li>Clic izquierdo en un bloque: punto A.</li>
 *   <li>Clic derecho en un bloque: punto B.</li>
 *   <li>Clic derecho al aire, o agachado + clic derecho: abre el menu.</li>
 * </ul>
 */
public class SpawnWandItem extends Item {

    public SpawnWandItem(Properties properties) {
        super(properties);
    }

    public static boolean canUse(ServerPlayer player) {
        if (player.hasPermissions(2)) {
            return true;
        }
        message(player, "Solo los operadores pueden usar la varita de spawn.");
        return false;
    }

    public static void message(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false; // en creativo, el clic izquierdo no rompe bloques con la varita
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return InteractionResult.PASS; // agachado: cae a use() y abre el menu
        }
        Level level = context.getLevel();
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && canUse(serverPlayer)) {
            BlockPos pos = context.getClickedPos();
            Selections.setB(serverPlayer, pos);
            message(serverPlayer, "Punto B: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && canUse(serverPlayer)) {
            SpawnNet.sendSync(serverPlayer, true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.dreams.spawn_wand.tooltip.a"));
        tooltip.add(Component.translatable("item.dreams.spawn_wand.tooltip.b"));
        tooltip.add(Component.translatable("item.dreams.spawn_wand.tooltip.menu"));
    }
}
