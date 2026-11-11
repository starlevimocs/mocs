package com.starlevi.starhero.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Un instrumento: lo tocas en el escenario, o se lo das a un aldeano para que se sume a la banda. */
public class InstrumentItem extends Item {
    public final Instrument instrument;

    public InstrumentItem(Instrument instrument, Properties properties) {
        super(properties);
        this.instrument = instrument;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.starhero.instrument.play").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starhero.instrument.npc").withStyle(ChatFormatting.DARK_AQUA));
    }
}
