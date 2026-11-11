package net.mca.mixin;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Dreams: para que el aldeano que va de la mano camine al mismo paso que el jugador. */
@Mixin(LimbAnimator.class)
public interface MixinDreamsLimbAnimator {
    @Accessor("prevSpeed")
    float dreams$getPrevSpeed();

    @Accessor("prevSpeed")
    void dreams$setPrevSpeed(float value);

    @Accessor("speed")
    float dreams$getSpeed();

    @Accessor("speed")
    void dreams$setSpeed(float value);

    @Accessor("pos")
    float dreams$getPos();

    @Accessor("pos")
    void dreams$setPos(float value);
}
