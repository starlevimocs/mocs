package com.starlevi.starcompat;

import com.mojang.logging.LogUtils;
import com.starlevi.starcompat.client.ClientSetup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Starcompat: parches de compatibilidad de animaciones para el servidor Dreams.
 * Todo es del lado del cliente; en el servidor el mod no hace nada.
 */
@Mod(StarCompat.MOD_ID)
public class StarCompat {
    public static final String MOD_ID = "starcompat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StarCompat() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));
    }
}
