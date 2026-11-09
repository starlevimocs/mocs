package com.starlevi.dreams.item;

import com.starlevi.dreams.DreamsMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DreamsMod.MOD_ID);

    public static final RegistryObject<Item> SPAWN_WAND = ITEMS.register("spawn_wand",
            () -> new SpawnWandItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    private ModItems() {
    }
}
