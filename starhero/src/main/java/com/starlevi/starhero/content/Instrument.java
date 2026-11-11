package com.starlevi.starhero.content;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/** Los instrumentos de la banda. */
public enum Instrument {
    GUITAR("guitar"),
    BASS("bass"),
    DRUMS("drum_kit"),
    VOICE("microphone"),
    KEYS("keyboard");

    public final String itemId;

    Instrument(String itemId) {
        this.itemId = itemId;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "instrument.starhero." + id();
    }

    public String roleKey() {
        return "role.starhero." + id();
    }

    public Item item() {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("starhero", itemId));
        return item == null ? Items.AIR : item;
    }

    public ItemStack stack() {
        return new ItemStack(item());
    }

    public Instrument next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Instrument byId(String id) {
        for (Instrument i : values()) {
            if (i.id().equals(id)) {
                return i;
            }
        }
        return GUITAR;
    }

    public static Instrument fromItem(Item item) {
        if (item instanceof InstrumentItem ii) {
            return ii.instrument;
        }
        return null;
    }
}
