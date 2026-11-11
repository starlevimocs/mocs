package net.mca.server.dreams;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.network.s2c.DreamsAnimationMessage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * Dreams: animaciones entre aldeano y jugador, del lado del servidor.
 * El aldeano se acerca al jugador y se manda la animacion a todos los que esten cerca.
 */
public final class DreamsAnimations {
    private DreamsAnimations() {
    }

    public static int duration(String type) {
        return switch (type) {
            case "kiss" -> 50;
            case "hug" -> 60;
            case "slap" -> 16;
            default -> 40;
        };
    }

    public static void play(VillagerEntityMCA villager, PlayerEntity player, String type) {
        type = type.toLowerCase(Locale.ROOT);
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return;
        }

        if (!"slap".equals(type)) {
            // que quede frente al jugador, a un paso
            Vec3d look = player.getRotationVector().multiply(1, 0, 1);
            if (look.lengthSquared() < 1.0E-4) {
                look = new Vec3d(0, 0, 1);
            }
            Vec3d spot = player.getPos().add(look.normalize().multiply(0.85));
            villager.getNavigation().startMovingTo(spot.x, spot.y, spot.z, 0.6);
        } else {
            villager.swingHand(Hand.MAIN_HAND);
        }
        villager.getLookControl().lookAt(player, 30.0F, 30.0F);

        DreamsAnimationMessage msg = new DreamsAnimationMessage(type, villager.getId(), player.getId(), duration(type));
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(villager) < 64 * 64) {
                NetworkHandler.sendToPlayer(msg, p);
            }
        }
    }
}
