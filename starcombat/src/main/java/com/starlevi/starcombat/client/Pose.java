package com.starlevi.starcombat.client;

import com.starlevi.starcombat.Moves;
import com.starlevi.starcombat.StarCombat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Pose de los brazos: en guardia con las hojas, y cuando una hoja vuela, el brazo la sigue. */
public final class Pose {
    private Pose() {
    }

    public static void apply(LivingEntity entity, HumanoidModel<?> m) {
        if (!(entity instanceof Player p) || !StarCombat.holds(p)) {
            return;
        }
        ClientCombat.St s = ClientCombat.STATES.get(p.getId());
        float partial = Minecraft.getInstance().getFrameTime();
        boolean offFree = p.getOffhandItem().isEmpty();
        for (int side = -1; side <= 1; side += 2) {
            ModelPart arm = side > 0 ? m.rightArm : m.leftArm;
            Vec3 blade = ClientCombat.blade(p, s, side, partial);
            if (blade != null) {
                float yaw = ClientCombat.bodyYaw(p, s, partial);
                Vec3 d = blade.subtract(ClientCombat.shoulder(p, s, side, partial));
                double len = d.length();
                if (len < 1.0E-4) {
                    continue;
                }
                Vec3 f = Moves.forward(yaw), r = Moves.right(yaw);
                double lx = (d.x * r.x + d.z * r.z) / len, lz = (d.x * f.x + d.z * f.z) / len, ly = d.y / len;
                arm.xRot = (float) (-Math.PI / 2 - Math.asin(Math.max(-1, Math.min(1, ly))));
                arm.yRot = (float) Math.atan2(lx, lz);
                arm.zRot = 0;
            } else if (side == ClientCombat.mainSide(p) || offFree) {
                // en guardia: brazos algo abiertos y adelantados
                arm.xRot = arm.xRot * 0.55F - 0.3F;
                arm.zRot += side * 0.14F;
            }
        }
        if (m instanceof PlayerModel<?> pm) {
            pm.rightSleeve.copyFrom(pm.rightArm);
            pm.leftSleeve.copyFrom(pm.leftArm);
        }
    }
}
