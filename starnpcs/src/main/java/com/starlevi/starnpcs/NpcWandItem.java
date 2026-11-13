package com.starlevi.starnpcs;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/** Vara de NPC: clic en un bloque crea un NPC y abre su editor; clic en un NPC lo edita. */
public class NpcWandItem extends Item {
    public NpcWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(ctx.getPlayer() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
            return InteractionResult.FAIL;
        }
        Vec3 at = ctx.getClickLocation();
        float yaw = player.getYRot() + 180F;
        NpcEntity npc = NpcEntity.spawn((ServerLevel) ctx.getLevel(), at, yaw, new Profile());
        if (npc != null) {
            Net.openEditor(player, npc, 0);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.starnpcs.npc_wand.tip1").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.starnpcs.npc_wand.tip2").withStyle(ChatFormatting.GRAY));
    }
}
