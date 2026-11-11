package com.starlevi.starhero;

import com.mojang.logging.LogUtils;
import com.starlevi.starhero.content.AmplifierBlock;
import com.starlevi.starhero.content.Instrument;
import com.starlevi.starhero.content.InstrumentItem;
import com.starlevi.starhero.net.Net;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Star Hero: tocar en banda (jugadores y aldeanos) canciones de osu!mania con CubOsu, con instrumentos 3D,
 * animaciones al ritmo, camara de concierto y publico.
 */
@Mod(StarHero.MOD_ID)
public final class StarHero {
    public static final String MOD_ID = "starhero";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final RegistryObject<Block> AMPLIFIER = BLOCKS.register("amplifier", () -> new AmplifierBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.2F).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Item> AMPLIFIER_ITEM = ITEMS.register("amplifier",
            () -> new BlockItem(AMPLIFIER.get(), new Item.Properties()));

    public static final RegistryObject<Item> GUITAR = instrument(Instrument.GUITAR);
    public static final RegistryObject<Item> BASS = instrument(Instrument.BASS);
    public static final RegistryObject<Item> MICROPHONE = instrument(Instrument.VOICE);
    public static final RegistryObject<Item> DRUM_KIT = instrument(Instrument.DRUMS);
    public static final RegistryObject<Item> KEYBOARD = instrument(Instrument.KEYS);

    /** Palillos: los dibuja el baterista mientras toca. */
    public static final RegistryObject<Item> DRUMSTICK = ITEMS.register("drumstick", () -> new Item(new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.starhero"))
            .icon(() -> new ItemStack(GUITAR.get()))
            .displayItems((params, out) -> {
                out.accept(AMPLIFIER_ITEM.get());
                out.accept(GUITAR.get());
                out.accept(BASS.get());
                out.accept(DRUM_KIT.get());
                out.accept(KEYBOARD.get());
                out.accept(MICROPHONE.get());
            })
            .build());

    private static RegistryObject<Item> instrument(Instrument instrument) {
        return ITEMS.register(instrument.itemId, () -> new InstrumentItem(instrument, new Item.Properties().stacksTo(1)));
    }

    public StarHero() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
        bus.addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }
}
