package com.starlevi.starcombat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sonidos propios. Se pueden cambiar con un resource pack (assets/starcombat/sounds/*.ogg). */
public final class Sounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, StarCombat.MOD_ID);
    public static final RegistryObject<SoundEvent> SWING = reg("swing");
    public static final RegistryObject<SoundEvent> SWING_HEAVY = reg("swing_heavy");
    public static final RegistryObject<SoundEvent> CHAIN = reg("chain");
    public static final RegistryObject<SoundEvent> HIT = reg("hit");
    public static final RegistryObject<SoundEvent> SLAM = reg("slam");
    public static final RegistryObject<SoundEvent> IGNITE = reg("ignite");
    public static final RegistryObject<SoundEvent> GRAPPLE = reg("grapple");
    public static final RegistryObject<SoundEvent> LATCH = reg("latch");

    private Sounds() {
    }

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(StarCombat.MOD_ID, name)));
    }
}
