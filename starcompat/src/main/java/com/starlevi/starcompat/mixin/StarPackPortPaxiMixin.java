package com.starlevi.starcompat.mixin;

import net.minecraftforge.fml.loading.FMLPaths;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

/**
 * StarPackPort + Paxi.
 * <p>
 * StarPackPort solo convierte los packs que estan en la carpeta {@code resourcepacks} del juego.
 * Paxi carga los suyos desde {@code config/paxi/resourcepacks} (con el mismo metodo de Minecraft que
 * StarPackPort intercepta), asi que esos packs de 1.20.2-1.21.11 llegaban sin convertir.
 * Aca se acepta tambien la carpeta de Paxi. La de datapacks de Paxi sigue sin tocarse.
 */
@Pseudo
@Mixin(targets = "com.starlevi.starpackport.StarPackPort", remap = false)
public abstract class StarPackPortPaxiMixin {

    @Inject(method = "isResourcePack(Ljava/nio/file/Path;)Z", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void starcompat$acceptPaxiFolder(Path pack, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || pack == null) {
            return;
        }
        Path parent = pack.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            return;
        }
        Path paxi = FMLPaths.GAMEDIR.get().resolve("config").resolve("paxi").resolve("resourcepacks")
                .toAbsolutePath().normalize();
        if (parent.equals(paxi)) {
            cir.setReturnValue(true);
        }
    }
}
