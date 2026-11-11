package net.mca.server.dreams;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.network.s2c.DreamsAttachMessage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Dreams: aldeano "enganchado" al jugador: de la mano, en brazos o en la espalda.
 * <p>
 * Por debajo el aldeano va montado en el jugador (como el "levantar" de MCA), asi que nunca se pueden separar:
 * cada tick se lo pone exactamente al costado, delante o detras del jugador, girado como corresponde. Los
 * huesos (las manos que se agarran, los brazos que sostienen) los pone {@link net.mca.client.dreams.DreamsPoses}
 * encima de EMF. Se suelta agachandose (shift) o con "Soltar" en el menu.
 */
public final class DreamsAttach {
    public static final int NONE = 0;
    public static final int HAND = 1;
    public static final int ARMS = 2;
    public static final int BACK = 3;

    /** Distancia entre los dos al ir de la mano (bloques): las manos quedan justo juntas. */
    public static final double HAND_SIDE = 0.97;

    private DreamsAttach() {
    }

    public static int parse(String mode) {
        return switch (mode) {
            case "hand" -> HAND;
            case "arms" -> ARMS;
            case "back" -> BACK;
            default -> NONE;
        };
    }

    public static boolean isAttachedTo(VillagerEntityMCA villager, Entity player) {
        return villager.dreamsAttach != NONE && villager.dreamsAttachTo == player.getId();
    }

    /** Engancha al aldeano con el jugador en ese modo (si ya estaba igual, lo suelta). */
    public static void toggle(VillagerEntityMCA villager, ServerPlayerEntity player, int mode) {
        if (isAttachedTo(villager, player) && villager.dreamsAttach == mode) {
            release(villager);
            return;
        }
        // un solo aldeano enganchado a la vez
        for (VillagerEntityMCA other : player.getWorld().getEntitiesByClass(VillagerEntityMCA.class, player.getBoundingBox().expand(16),
                v -> v != villager && v.dreamsAttach != NONE && v.dreamsAttachTo == player.getId())) {
            release(other);
        }
        if (villager.dreamsAttach != NONE) {
            release(villager);
        }
        if (villager.getVehicle() != null) {
            villager.stopRiding();
        }
        villager.getNavigation().stop();
        villager.dreamsAttach = mode;
        villager.dreamsAttachTo = player.getId();
        if (mode == HAND) {
            // de la mano: no va montado; camina de verdad al lado del jugador (asi EMF lo anima caminando)
            position(villager, player);
        } else {
            villager.startRiding(player, true);
            player.networkHandler.sendPacket(new EntityPassengersSetS2CPacket(player));
        }
        villager.calculateDimensions();
        broadcast(villager);
    }

    public static void release(VillagerEntityMCA villager) {
        Entity vehicle = villager.getVehicle();
        villager.dreamsAttach = NONE;
        villager.dreamsAttachTo = -1;
        if (vehicle instanceof PlayerEntity) {
            villager.stopRiding();
        }
        villager.calculateDimensions();
        if (vehicle instanceof ServerPlayerEntity sp) {
            sp.networkHandler.sendPacket(new EntityPassengersSetS2CPacket(sp));
        }
        broadcast(villager);
    }

    private static PlayerEntity partner(VillagerEntityMCA villager) {
        return villager.getWorld().getEntityById(villager.dreamsAttachTo) instanceof PlayerEntity p ? p : null;
    }

    /** Servidor, cada tick del aldeano (antes de moverse). */
    public static void serverTick(VillagerEntityMCA villager) {
        if (villager.dreamsAttach == NONE) {
            return;
        }
        if (villager.dreamsAttach == HAND) {
            PlayerEntity p = partner(villager);
            if (p == null || !p.isAlive() || p.isSneaking() || p.hasVehicle() || villager.isSleeping()
                    || p.squaredDistanceTo(villager) > 12 * 12) {
                release(villager);
                return;
            }
        } else if (!(villager.getVehicle() instanceof PlayerEntity) || villager.isSleeping()) {
            release(villager);
            return;
        }
        // para los que llegan despues o se alejaron y volvieron
        if (villager.age % 40 == 0) {
            broadcast(villager);
        }
    }

    /** Servidor, al final del tick del aldeano: de la mano queda exactamente al lado del jugador. */
    public static void afterTick(VillagerEntityMCA villager) {
        if (villager.dreamsAttach == HAND) {
            PlayerEntity p = partner(villager);
            if (p != null) {
                position(villager, p);
                villager.setOnGround(p.isOnGround());
            }
        }
    }

    private static void broadcast(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return;
        }
        DreamsAttachMessage msg = new DreamsAttachMessage(villager.getId(), villager.dreamsAttach, villager.dreamsAttachTo);
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(villager) < 96 * 96) {
                NetworkHandler.sendToPlayer(msg, p);
            }
        }
    }

    /**
     * Pone al aldeano en su lugar respecto del jugador (servidor y cliente, cada tick, despues de que el
     * jugador se movio). Devuelve false si no esta enganchado.
     */
    public static boolean position(VillagerEntityMCA villager, PlayerEntity player) {
        int mode = villager.dreamsAttach;
        if (mode == NONE) {
            return false;
        }
        float b = player.bodyYaw;
        double rad = Math.toRadians(b);
        double fx = -Math.sin(rad), fz = Math.cos(rad);   // adelante del jugador
        double rx = -Math.cos(rad), rz = -Math.sin(rad);  // a su derecha
        double x = player.getX(), y = player.getY(), z = player.getZ();
        float yaw = b;
        float scale = villager.getRawScaleFactor();
        switch (mode) {
            case HAND -> {
                // al costado derecho, mirando para el mismo lado
                x += rx * HAND_SIDE;
                z += rz * HAND_SIDE;
            }
            case ARMS -> {
                // delante, de costado (mira hacia la izquierda del jugador); la cadera a la altura de los brazos
                x += fx * 0.42;
                z += fz * 0.42;
                y += 0.95 - 0.75 * scale;
                // el giro de costado (-90) y el recostarse se hacen al dibujarlo (DreamsPoses.beforeRender)
            }
            case BACK -> {
                // detras y mas arriba, abrazado a los hombros
                x -= fx * 0.30;
                z -= fz * 0.30;
                y += 0.58;
            }
            default -> {
                return false;
            }
        }
        villager.setPosition(x, y, z);
        villager.setVelocity(0, 0, 0);
        villager.setYaw(yaw);
        villager.setBodyYaw(yaw);
        if (mode != HAND) {
            villager.setHeadYaw(mode == BACK ? player.getHeadYaw() : yaw);
        } else {
            // la cabeza puede mirar alrededor, pero no mas de lo natural
            float rel = net.minecraft.util.math.MathHelper.wrapDegrees(villager.getHeadYaw() - yaw);
            villager.setHeadYaw(yaw + net.minecraft.util.math.MathHelper.clamp(rel, -60, 60));
        }
        villager.fallDistance = 0;
        return true;
    }
}
