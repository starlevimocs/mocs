package com.starlevi.starcompat.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * En 1.20.1, {@code ModelPart.copyFrom} copia posicion y rotacion pero no la escala.
 * Fresh Animations Player estira y encoge las piernas (y otras partes) con escala, asi que todo lo que
 * copia la pose del jugador con copyFrom / copyPropertiesTo queda desfasado: armaduras vanilla y de mods
 * (ropa de granjero y sombrero de paja de Kaleidoscope), accesorios de Artifacts (Curios), etc.
 * Esto agrega la escala a la copia. Sin EMF las escalas son siempre 1, asi que no cambia nada.
 * <p>
 * El nombre del metodo va en SRG ({@code m_104315_} = copyFrom) porque el mod no genera refmap.
 */
@Mixin(value = ModelPart.class, remap = false)
public abstract class ModelPartCopyScaleMixin {

    @Inject(method = "m_104315_(Lnet/minecraft/client/model/geom/ModelPart;)V", at = @At("TAIL"), remap = false)
    private void starcompat$copyScale(ModelPart other, CallbackInfo ci) {
        ModelPart self = (ModelPart) (Object) this;
        self.xScale = other.xScale;
        self.yScale = other.yScale;
        self.zScale = other.zScale;
    }
}
