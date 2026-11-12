package com.starlevi.starcombat;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Star Combat: armas con combate vistoso para Dreams. La primera son los Colmillos de Brasa: dos hojas unidas
 * a las manos por cadenas al rojo, con combos encadenados y gancho para trepar.
 */
@Mod(StarCombat.MOD_ID)
public final class StarCombat {
    public static final String MOD_ID = "starcombat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> EMBER_FANGS = ITEMS.register("ember_fangs", BladesItem::new);
    /** Solo para dibujar el brillo de la hoja; no se consigue. */
    public static final RegistryObject<Item> EMBER_GLOW = ITEMS.register("ember_fangs_glow", () -> new Item(new Item.Properties()));

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.starcombat"))
            .icon(() -> new ItemStack(EMBER_FANGS.get()))
            .displayItems((params, out) -> out.accept(EMBER_FANGS.get()))
            .build());

    public StarCombat() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        TABS.register(bus);
        bus.addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Net.register();
            Config.load();
        });
    }

    /** Tiene los Colmillos en la mano principal. */
    public static boolean holds(LivingEntity e) {
        return e != null && e.getMainHandItem().is(EMBER_FANGS.get());
    }
}
