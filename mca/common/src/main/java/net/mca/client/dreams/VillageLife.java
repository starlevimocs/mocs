package net.mca.client.dreams;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.*;

/**
 * Dreams: aldeanos mas vivos (solo del lado del cliente, cada jugador lo ve en su pantalla).
 * <ul>
 *   <li>Te saludan con la mano cuando pasas cerca (cada tanto, no todo el tiempo).</li>
 *   <li>Mueven las manos mientras hablan.</li>
 *   <li>Si dos aldeanos estan quietos y frente a frente, charlan entre ellos: uno habla gesticulando,
 *       el otro asiente, y se turnan.</li>
 * </ul>
 */
public final class VillageLife {
    private static final Random RANDOM = Random.create();
    private static final Map<UUID, Long> GREETED = new HashMap<>();
    private static final Map<UUID, Long> NEXT_CHAT = new HashMap<>();
    private static final String[] GREETINGS = {"¡Hola!", "¡Buenas!", "¡Hola, qué tal!", "¡Hey!", "¡Buen día!"};
    private static final String[] CHATTER = {
            "¿Viste lo que pasó en el mercado?", "Hoy hace un lindo día.", "Ja, ¡no te puedo creer!",
            "Mañana tengo que trabajar temprano.", "¿Y vos qué opinás?", "Sí, sí, tenés razón.",
            "Escuché que vieron algo raro en el bosque.", "¡Qué buena idea!"
    };
    private static int clock;

    private VillageLife() {
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.isPaused()) {
            return;
        }
        if (++clock % 5 != 0) {
            return;
        }
        long now = client.world.getTime();
        Box area = player.getBoundingBox().expand(24);
        List<VillagerEntityMCA> villagers = client.world.getEntitiesByClass(VillagerEntityMCA.class, area, v -> true);

        for (VillagerEntityMCA v : villagers) {
            if (!canAct(v)) {
                continue;
            }

            // gestos mientras habla
            if (VillagerVoice.isSpeaking(v) && !DreamsPoses.isAnimating(v)) {
                DreamsPoses.startSolo(v, RANDOM.nextBoolean() ? "talk" : "talk2");
                continue;
            }

            // saludo al pasar cerca
            double dist = v.squaredDistanceTo(player);
            Long last = GREETED.get(v.getUuid());
            if (dist < 4.5 * 4.5 && (last == null || now - last > 20 * 180) && v.canSee(player)
                    && v.getVillagerBrain().getMemoriesForPlayer(player).getHearts() >= 0
                    && !DreamsPoses.isAnimating(v) && RANDOM.nextFloat() < 0.35F) {
                GREETED.put(v.getUuid(), now);
                DreamsPoses.startSolo(v, "wave");
                String hi = GREETINGS[RANDOM.nextInt(GREETINGS.length)];
                VillagerVoice.speakAmbient(v, Text.literal(hi), hi);
                continue;
            }

            // charlas entre aldeanos
            if (clock % 20 == 0) {
                Long next = NEXT_CHAT.get(v.getUuid());
                if (next != null && now < next) {
                    continue;
                }
                VillagerEntityMCA partner = partnerFor(v, villagers);
                if (partner != null && !DreamsPoses.isAnimating(v) && !DreamsPoses.isAnimating(partner)) {
                    String line = CHATTER[RANDOM.nextInt(CHATTER.length)];
                    VillagerVoice.speakAmbient(v, Text.literal(line), line);
                    DreamsPoses.startSolo(v, RANDOM.nextBoolean() ? "talk" : "talk2");
                    if (RANDOM.nextFloat() < 0.6F) {
                        DreamsPoses.startSolo(partner, "nod");
                    }
                    long pause = 20L * (4 + RANDOM.nextInt(6));
                    NEXT_CHAT.put(v.getUuid(), now + pause);
                    NEXT_CHAT.put(partner.getUuid(), now + pause / 2 + 30);
                }
            }
        }
    }

    private static boolean canAct(VillagerEntityMCA v) {
        AgeState age = v.getAgeState();
        return age != AgeState.BABY && age != AgeState.TODDLER && !v.isSleeping()
                && !v.getVillagerBrain().isPanicking() && v.isAlive();
    }

    /** Otro aldeano quieto, cerca y de frente. */
    private static VillagerEntityMCA partnerFor(VillagerEntityMCA v, List<VillagerEntityMCA> all) {
        if (isMoving(v)) {
            return null;
        }
        Vec3d look = Vec3d.fromPolar(0, v.headYaw);
        for (VillagerEntityMCA w : all) {
            if (w == v || !canAct(w) || isMoving(w) || v.squaredDistanceTo(w) > 3.2 * 3.2) {
                continue;
            }
            Vec3d to = w.getPos().subtract(v.getPos()).multiply(1, 0, 1).normalize();
            Vec3d back = Vec3d.fromPolar(0, w.headYaw);
            if (look.dotProduct(to) > 0.6 && back.dotProduct(to.negate()) > 0.6) {
                return w;
            }
        }
        return null;
    }

    private static boolean isMoving(Entity e) {
        return e.getVelocity().horizontalLengthSquared() > 0.0009;
    }
}
