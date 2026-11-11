package com.starlevi.starvfx;

import com.mojang.logging.LogUtils;
import com.starlevi.starvfx.net.Net;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Star VFX: cinematicas y efectos para contar la historia de Dreams. El servidor dirige (que escena, donde y
 * cuando) y cada cliente la dibuja con su propio reloj, asi todos ven lo mismo a la vez.
 */
@Mod(StarVfx.MOD_ID)
public final class StarVfx {
    public static final String MOD_ID = "starvfx";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StarVfx() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }
}
