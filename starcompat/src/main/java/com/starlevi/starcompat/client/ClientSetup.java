package com.starlevi.starcompat.client;

import com.starlevi.starcompat.StarCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.nio.file.Path;

public final class ClientSetup {
    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::onClientSetup);
        modBus.addListener(ClientSetup::onAddPacks);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        ModList mods = ModList.get();
        if (mods.isLoaded("entity_model_features") && mods.isLoaded("cinematic")) {
            event.enqueueWork(EmfCinematicHook::register);
        }
    }

    /**
     * Paquete de recursos interno, siempre activo y arriba de todo: texturas de armaduras de otros mods
     * corregidas para Fresh Animations Player (la cara de abajo del pantalon, como FA hace con las vanilla).
     */
    private static void onAddPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Path path = ModList.get().getModFileById(StarCompat.MOD_ID).getFile().findResource("resourcepacks/fa_armor");
        Pack pack = Pack.readMetaAndCreate(
                StarCompat.MOD_ID + "_fa_armor",
                Component.literal("Starcompat: armaduras para Fresh Animations"),
                true,
                id -> new PathPackResources(id, path, true),
                PackType.CLIENT_RESOURCES,
                Pack.Position.TOP,
                PackSource.BUILT_IN);
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        } else {
            StarCompat.LOGGER.warn("[Starcompat] No se pudo cargar el paquete interno fa_armor");
        }
    }
}
