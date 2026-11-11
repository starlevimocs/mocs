package net.mca.server.dreams;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.network.s2c.DreamsAnimationMessage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Dreams: animaciones de pareja entre aldeano y jugador, del lado del servidor.
 * <p>
 * El jugador queda donde esta (es el "ancla"). El aldeano se pone exactamente enfrente, a la distancia que pide la
 * animacion, mirando al jugador, y se queda quieto mientras dura. A los clientes se les manda el ancla, asi todos
 * dibujan a los dos en el mismo lugar y la animacion no se separa.
 */
public final class DreamsAnimations {
    /** distancia en px (16 = 1 bloque) y duracion en ticks; tiene que coincidir con assets/mca/dreams_animations. */
    private record Spec(float distance, int ticks) {
    }

    private static final Map<String, Spec> SPECS = Map.of(
            "kiss", new Spec(8, 62),
            "hug", new Spec(5.5F, 70),
            "slap", new Spec(12, 20),
            "high_five", new Spec(13, 32),
            "dance_slow", new Spec(6, 202),
            "dance_fun", new Spec(15, 160),
            "procreate", new Spec(5.5F, 140)
    );

    private record Lock(double x, double y, double z, float yaw, long until) {
    }

    private static final Map<VillagerEntityMCA, Lock> LOCKS = new WeakHashMap<>();

    /** Lo completa el cliente: true si esa entidad esta en una animacion (para no empujarla). */
    public static java.util.function.Predicate<net.minecraft.entity.Entity> CLIENT_ANIMATING = e -> false;

    private DreamsAnimations() {
    }

    public static boolean isLocked(VillagerEntityMCA villager) {
        return LOCKS.containsKey(villager);
    }

    public static void play(VillagerEntityMCA villager, PlayerEntity player, String type) {
        type = type.toLowerCase(Locale.ROOT);
        Spec spec = SPECS.get(type);
        if (spec == null || !(villager.getWorld() instanceof ServerWorld world)) {
            return;
        }

        // ancla: el jugador, mirando hacia donde esta el aldeano (en horizontal)
        Vec3d anchor = player.getPos();
        Vec3d toVillager = villager.getPos().subtract(anchor).multiply(1, 0, 1);
        float yaw = toVillager.lengthSquared() < 1.0E-4
                ? player.getYaw()
                : (float) (Math.toDegrees(Math.atan2(toVillager.z, toVillager.x)) - 90.0);

        // el aldeano, exactamente enfrente
        double rad = Math.toRadians(yaw);
        double d = spec.distance() / 16.0;
        double vx = anchor.x - Math.sin(rad) * d;
        double vz = anchor.z + Math.cos(rad) * d;
        float villagerYaw = yaw + 180.0F;
        villager.getNavigation().stop();
        boolean free = world.isSpaceEmpty(villager, villager.getBoundingBox()
                .offset(vx - villager.getX(), anchor.y - villager.getY(), vz - villager.getZ()));
        if (!free) {
            // no entra ahi (pared, mueble): se queda donde esta; en pantalla igual se ve en su lugar
            vx = villager.getX();
            vz = villager.getZ();
        }
        double vy = free ? anchor.y : villager.getY();
        villager.refreshPositionAndAngles(vx, vy, vz, villagerYaw, 0);
        villager.setHeadYaw(villagerYaw);
        villager.setBodyYaw(villagerYaw);
        LOCKS.put(villager, new Lock(vx, vy, vz, villagerYaw, world.getTime() + spec.ticks()));
        // mientras dura no decide nada (VillagerEntityMCA.mobTick lo saltea), no camina, no se va
        // el jugador queda en el ancla
        if (player instanceof ServerPlayerEntity sp) {
            sp.networkHandler.requestTeleport(anchor.x, anchor.y, anchor.z, yaw, sp.getPitch());
        }

        DreamsAnimationMessage msg = new DreamsAnimationMessage(type, villager.getId(), player.getId(), anchor.x, anchor.y, anchor.z, yaw);
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(villager) < 64 * 64) {
                NetworkHandler.sendToPlayer(msg, p);
            }
        }
    }

    /** Gesto de una sola persona (reir, encogerse de hombros, pensar...) que ven todos los que estan cerca. */
    public static void gesture(VillagerEntityMCA villager, String type) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return;
        }
        DreamsAnimationMessage msg = new DreamsAnimationMessage(type.toLowerCase(Locale.ROOT), villager.getId(), -1, 0, 0, 0, 0);
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(villager) < 48 * 48) {
                NetworkHandler.sendToPlayer(msg, p);
            }
        }
    }

    /** Cada tick del aldeano: mientras dura la animacion se queda en su lugar. */
    public static void tick(VillagerEntityMCA villager) {
        Lock lock = LOCKS.get(villager);
        if (lock == null) {
            return;
        }
        if (villager.getWorld().getTime() > lock.until() || villager.isRemoved()) {
            LOCKS.remove(villager);
            return;
        }
        villager.getNavigation().stop();
        villager.setVelocity(0, villager.getVelocity().y, 0);
        if (villager.squaredDistanceTo(lock.x(), lock.y(), lock.z()) > 0.01) {
            villager.refreshPositionAndAngles(lock.x(), lock.y(), lock.z(), lock.yaw(), 0);
        }
        villager.setHeadYaw(lock.yaw());
        villager.setBodyYaw(lock.yaw());
    }
}
