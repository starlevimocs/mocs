package com.starlevi.starnpcs;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/** Star NPCs: personajes configurables con animaciones de Blockbench para dar vida al mundo. */
@Mod(StarNpcs.MOD_ID)
public class StarNpcs {
    public static final String MOD_ID = "starnpcs";
    public static final Logger LOG = LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<EntityType<NpcEntity>> NPC = ENTITIES.register("npc",
            () -> EntityType.Builder.<NpcEntity>of(NpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).clientTrackingRange(10).updateInterval(3).build(MOD_ID + ":npc"));

    public static final RegistryObject<Item> WAND = ITEMS.register("npc_wand", () -> new NpcWandItem(new Item.Properties().stacksTo(1)));

    public StarNpcs() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus);
        ITEMS.register(bus);
        bus.addListener(this::attributes);
        bus.addListener(this::tabs);
        bus.addListener(this::setup);
        MinecraftForge.EVENT_BUS.addListener(this::commands);
        MinecraftForge.EVENT_BUS.addListener(this::login);
        MinecraftForge.EVENT_BUS.addListener(this::serverStarting);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(NPC.get(), NpcEntity.attributes().build());
    }

    private void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WAND);
        }
    }

    private void commands(RegisterCommandsEvent event) {
        NpcCommands.register(event.getDispatcher());
    }

    private void serverStarting(ServerStartingEvent event) {
        Assets.reload();
        Presets.reload();
    }

    private void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Assets.sendTo(player);
        }
    }
}
