package com.starlevi.starinteractions;

import com.mojang.logging.LogUtils;
import com.starlevi.starinteractions.net.Net;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Star Interactions: interacciones entre jugadores (saludos, abrazos, besos, bailes, ir de la mano, cargar) y
 * con los aldeanos (modo toque: acariciar la cabeza, la mejilla, mover los brazos con el mouse), mas emotes.
 * El servidor manda: pide permiso al otro jugador, acomoda a los dos y le avisa a todos los que estan cerca,
 * asi todos ven lo mismo.
 */
@Mod(StarInteractions.MOD_ID)
public final class StarInteractions {
    public static final String MOD_ID = "starinteractions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StarInteractions() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }
}
