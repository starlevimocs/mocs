package com.starlevi.starcompat.client;

import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;

/**
 * Puente con el MCA de Dreams (fork de Minecraft Comes Alive), sin depender de el para compilar.
 * Cuando EMF termina de animar el modelo de un aldeano o de un jugador, le avisa a MCA para que vuelva a poner
 * sus poses propias (panico, bebes, pechos, besos, abrazos...).
 */
final class McaBridge {
    private static boolean resolved;
    private static Method after; // net.mca.client.dreams.DreamsPoses.afterEmfAnimation(LivingEntity, Object)

    private McaBridge() {
    }

    static void afterEmfAnimation(LivingEntity entity, Object model) {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> poses = Class.forName("net.mca.client.dreams.DreamsPoses");
                after = poses.getMethod("afterEmfAnimation", LivingEntity.class, Object.class);
            } catch (Throwable t) {
                after = null; // MCA de Dreams no esta instalado
            }
        }
        if (after == null || entity == null || model == null) {
            return;
        }
        try {
            after.invoke(null, entity, model);
        } catch (Throwable t) {
            after = null;
        }
    }
}
