package net.mca.screen.dreams;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.mca.MCA;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/** Dreams: tipos de pantalla propios. */
public interface DreamsScreens {
    DeferredRegister<ScreenHandlerType<?>> SCREEN_HANDLERS = DeferredRegister.create(MCA.MOD_ID, RegistryKeys.SCREEN_HANDLER);

    RegistrySupplier<ScreenHandlerType<VillagerOutfitScreenHandler>> VILLAGER_OUTFIT = SCREEN_HANDLERS.register(
            new Identifier(MCA.MOD_ID, "villager_outfit"),
            () -> new ScreenHandlerType<>(VillagerOutfitScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    static void bootstrap() {
        SCREEN_HANDLERS.register();
    }
}
