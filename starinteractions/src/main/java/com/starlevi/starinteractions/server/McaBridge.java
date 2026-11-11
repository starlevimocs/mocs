package com.starlevi.starinteractions.server;

import com.starlevi.starinteractions.StarInteractions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;

/**
 * Puente con el MCA de Dreams (sin depender de el para compilar): cuantos corazones te tiene un aldeano, su
 * personalidad, darle corazones, hacerle hacer un gesto y decir una frase. Si MCA no esta (o cambia por dentro),
 * todo esto simplemente no hace nada.
 */
public final class McaBridge {
    private static Method gesture;
    private static boolean gestureResolved;

    private McaBridge() {
    }

    /** Aldeano de MCA (tiene cuerpo de persona). */
    public static boolean isVillager(Entity e) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return e instanceof Mob && id != null && "mca".equals(id.getNamespace()) && id.getPath().contains("villager")
                && !id.getPath().contains("zombie");
    }

    private static Object brain(Entity villager) throws ReflectiveOperationException {
        return villager.getClass().getMethod("getVillagerBrain").invoke(villager);
    }

    private static Method find(Class<?> c, String name, int params) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == params) {
                return m;
            }
        }
        return null;
    }

    /** Corazones que le tiene al jugador (50 si no se puede saber). */
    public static int hearts(Entity villager, ServerPlayer player) {
        try {
            Object brain = brain(villager);
            Method mem = find(brain.getClass(), "getMemoriesForPlayer", 1);
            Object memories = mem.invoke(brain, player);
            return (int) memories.getClass().getMethod("getHearts").invoke(memories);
        } catch (Throwable t) {
            return 50;
        }
    }

    public static String personality(Entity villager) {
        try {
            Object brain = brain(villager);
            return String.valueOf(brain.getClass().getMethod("getPersonality").invoke(brain)).toLowerCase();
        } catch (Throwable t) {
            return "";
        }
    }

    public static void rewardHearts(Entity villager, ServerPlayer player, int hearts) {
        try {
            Object brain = brain(villager);
            Method m = find(brain.getClass(), "rewardHearts", 2);
            m.invoke(brain, player, hearts);
        } catch (Throwable t) {
            StarInteractions.LOGGER.debug("[Star Interactions] No se pudieron dar corazones", t);
        }
    }

    /** Gesto del aldeano (las animaciones del fork: shy, laugh, cheer, no, angry, surprised, hum...). */
    public static void gesture(Entity villager, String name) {
        try {
            if (!gestureResolved) {
                gestureResolved = true;
                Class<?> c = Class.forName("net.mca.server.dreams.DreamsAnimations");
                gesture = find(c, "gesture", 2);
            }
            if (gesture != null) {
                gesture.invoke(null, villager, name);
            }
        } catch (Throwable t) {
            gesture = null;
        }
    }

    /** El aldeano le dice una frase al jugador (clave de dialogo de MCA, por ejemplo "touch.head"). */
    public static void say(Entity villager, ServerPlayer player, String phrase) {
        try {
            for (Method m : villager.getClass().getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("sendChatMessage") && p.length == 3 && p[0].isAssignableFrom(Player.class)
                        && p[1] == String.class && p[2] == Object[].class) {
                    m.invoke(villager, player, phrase, new Object[0]);
                    return;
                }
            }
        } catch (Throwable t) {
            StarInteractions.LOGGER.debug("[Star Interactions] El aldeano no pudo hablar", t);
        }
    }

    public static boolean isChild(LivingEntity e) {
        return e.isBaby();
    }
}
