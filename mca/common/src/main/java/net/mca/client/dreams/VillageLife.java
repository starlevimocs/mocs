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
    private static final Map<UUID, Long> NEXT_IDLE = new HashMap<>();
    private static final Map<UUID, Long> NEXT_WORK = new HashMap<>();
    private static final String[] GREETINGS = {"¡Hola!", "¡Buenas!", "¡Hola, qué tal!", "¡Hey!", "¡Buen día!"};
    private static final String[] CHATTER = {
            "¿Viste lo que pasó en el mercado?", "Hoy hace un lindo día.", "Ja, ¡no te puedo creer!",
            "Mañana tengo que trabajar temprano.", "¿Y vos qué opinás?", "Sí, sí, tenés razón.",
            "Escuché que vieron algo raro en el bosque.", "¡Qué buena idea!"
    };
    private static final String[] TALKS = {"talk", "talk2", "talk3"};
    private static final String[] STARE_LINES = {"¿Sí?", "¿Me buscabas?", "¿Pasa algo?", "¿Qué mirás?", "Hola, hola.", "¿Necesitás algo?"};
    private static final String[] WELCOME_LINES = {"¡Volviste!", "¡Por fin! Te extrañé.", "¡Mirá quién llegó!", "¡Qué bueno verte de nuevo!"};
    private static final Map<UUID, Long> STARE = new HashMap<>();
    private static final Map<UUID, Long> REACTED = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_FAR = new HashMap<>();
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
                DreamsPoses.startSolo(v, TALKS[RANDOM.nextInt(TALKS.length)]);
                continue;
            }

            // reacciones a lo que hace el jugador: si te lo quedas mirando, si volves a casa
            if (reactions(client, player, v, now)) {
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

            // trabajando: hachazos, cosechar, picar, pescar
            if (!isMoving(v) && !DreamsPoses.isAnimating(v) && workTick(v, now)) {
                continue;
            }

            // quieto sin hacer nada: gestos segun su animo, su personalidad y la hora
            if (clock % 20 == 0 && !isMoving(v) && !DreamsPoses.isAnimating(v)
                    && !net.mca.client.gui.InteractScreen.isTalkingTo(v.getUuid())) {
                Long next = NEXT_IDLE.get(v.getUuid());
                if (next == null) {
                    NEXT_IDLE.put(v.getUuid(), now + 100 + RANDOM.nextInt(500));
                } else if (now >= next) {
                    NEXT_IDLE.put(v.getUuid(), now + 20L * (14 + RANDOM.nextInt(30)));
                    DreamsPoses.startSolo(v, pickIdle(v));
                    continue;
                }
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
                    DreamsPoses.startSolo(v, TALKS[RANDOM.nextInt(TALKS.length)]);
                    // el otro reacciona: asiente, se rie, se sorprende...
                    float r = RANDOM.nextFloat();
                    if (r < 0.45F) {
                        DreamsPoses.startSolo(partner, "nod");
                    } else if (r < 0.62F) {
                        DreamsPoses.startSolo(partner, "laugh");
                    } else if (r < 0.72F) {
                        DreamsPoses.startSolo(partner, "surprised");
                    } else if (r < 0.80F) {
                        DreamsPoses.startSolo(partner, "shrug");
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

    /** Te quedas mirandolo -> reacciona segun su personalidad. Volves despues de estar lejos -> te recibe (si te quiere). */
    private static boolean reactions(MinecraftClient client, ClientPlayerEntity player, VillagerEntityMCA v, long now) {
        if (DreamsPoses.isAnimating(v) || net.mca.client.gui.InteractScreen.isTalkingTo(v.getUuid()) || client.currentScreen != null) {
            return false;
        }
        UUID id = v.getUuid();
        double dist = v.squaredDistanceTo(player);
        int hearts = v.getVillagerBrain().getMemoriesForPlayer(player).getHearts();

        // bienvenida
        boolean far = dist > 16 * 16;
        Boolean wasFar = WAS_FAR.get(id);
        if (far) {
            WAS_FAR.put(id, true);
        } else if (dist < 6 * 6 && Boolean.TRUE.equals(wasFar)) {
            WAS_FAR.put(id, false);
            Long last = REACTED.get(id);
            if (hearts >= 60 && v.canSee(player) && (last == null || now - last > 20 * 90)) {
                REACTED.put(id, now);
                DreamsPoses.startSolo(v, "welcome");
                String line = WELCOME_LINES[RANDOM.nextInt(WELCOME_LINES.length)];
                VillagerVoice.speakAmbient(v, Text.literal(line), line);
                return true;
            }
        }

        // mirada fija
        if (client.targetedEntity == v && dist < 5 * 5) {
            Long since = STARE.get(id);
            if (since == null) {
                STARE.put(id, now);
            } else if (now - since > 34) {
                Long last = REACTED.get(id);
                if (last == null || now - last > 20 * 25) {
                    REACTED.put(id, now);
                    STARE.remove(id);
                    String gesture = switch (v.getVillagerBrain().getPersonality()) {
                        case SHY, SENSITIVE -> "shy";
                        case FLIRTY -> hearts >= 20 ? "flirty" : "wave";
                        case GRUMPY -> "hips";
                        case GLOOMY -> "shrug";
                        case CONFIDENT -> "hips";
                        case WITTY, PEPPY -> "laugh";
                        case ODD -> "look_around";
                        default -> "wave";
                    };
                    if (hearts < -10) {
                        gesture = "angry";
                    }
                    DreamsPoses.startSolo(v, gesture);
                    String line = STARE_LINES[RANDOM.nextInt(STARE_LINES.length)];
                    VillagerVoice.speakAmbient(v, Text.literal(line), line);
                    return true;
                }
            }
        } else {
            STARE.remove(id);
        }
        return false;
    }

    private static boolean isMoving(Entity e) {
        if (e instanceof net.minecraft.entity.LivingEntity l && l.limbAnimator.getSpeed() > 0.12F) {
            return true;
        }
        return e.getVelocity().horizontalLengthSquared() > 0.0009;
    }

    private static boolean workTick(VillagerEntityMCA v, long now) {
        net.mca.entity.ai.Chore job = v.getVillagerBrain().getCurrentJob();
        if (job == null || job == net.mca.entity.ai.Chore.NONE) {
            return false;
        }
        Long next = NEXT_WORK.get(v.getUuid());
        if (next != null && now < next) {
            return false;
        }
        String anim = switch (job) {
            case CHOP -> near(v, 2, st -> st.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) ? "chop" : null;
            case HARVEST -> near(v, 2, st -> st.getBlock() instanceof net.minecraft.block.CropBlock) ? "harvest" : null;
            case PROSPECT -> near(v, 2, st -> st.isIn(net.minecraft.registry.tag.BlockTags.BASE_STONE_OVERWORLD)) ? "mine" : null;
            case FISH -> near(v, 4, st -> st.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER)) ? "fish" : null;
            default -> null;
        };
        NEXT_WORK.put(v.getUuid(), now + 20 + RANDOM.nextInt(30));
        if (anim == null) {
            return false;
        }
        DreamsPoses.startSolo(v, anim);
        return true;
    }

    private static boolean near(VillagerEntityMCA v, int r, java.util.function.Predicate<net.minecraft.block.BlockState> test) {
        net.minecraft.util.math.BlockPos base = v.getBlockPos();
        for (net.minecraft.util.math.BlockPos p : net.minecraft.util.math.BlockPos.iterate(base.add(-r, -1, -r), base.add(r, 2, r))) {
            if (test.test(v.getWorld().getBlockState(p))) {
                return true;
            }
        }
        return false;
    }

    /** Elige un gesto de ocio: el animo pesa mas, la personalidad le da el toque propio. */
    private static String pickIdle(VillagerEntityMCA v) {
        List<String> pool = new ArrayList<>();
        long dayTime = v.getWorld().getTimeOfDay() % 24000L;
        // bajo la lluvia se tapa
        if (v.getWorld().isRaining() && v.getWorld().isSkyVisible(v.getBlockPos().up())) {
            return RANDOM.nextFloat() < 0.75F ? "rain_cover" : "look_sky";
        }
        // los chicos juegan
        AgeState age = v.getAgeState();
        if (age == AgeState.CHILD || age == AgeState.TEEN) {
            add(pool, age == AgeState.CHILD ? 6 : 2, "kid_play");
            add(pool, 2, "cheer", "look_around", "laugh");
        }
        // en horario de trabajo, cada uno a lo suyo
        if (age == AgeState.ADULT && dayTime > 2000 && dayTime < 9500) {
            String path = net.minecraft.registry.Registries.VILLAGER_PROFESSION.getId(v.getProfession()).getPath();
            switch (path) {
                case "armorer", "toolsmith", "weaponsmith", "mason" -> add(pool, 5, "hammer");
                case "cleric" -> add(pool, 5, "pray");
                case "librarian", "cartographer" -> add(pool, 5, "read");
                case "farmer" -> add(pool, 4, "wipe_brow", "harvest");
                case "shepherd", "butcher", "leatherworker" -> add(pool, 4, "pet");
                case "fisherman" -> add(pool, 4, "look_sky", "wipe_brow");
                case "fletcher" -> add(pool, 4, "point", "look_around");
                case "guard", "archer" -> add(pool, 4, "salute", "look_around");
                default -> {
                }
            }
        }
        // al mediodia y a la tardecita, comen algo
        if ((dayTime > 5500 && dayTime < 7000) || (dayTime > 11000 && dayTime < 12000)) {
            add(pool, 5, "eat");
        }
        add(pool, 1, "dust_off", "look_sky");
        String mood = v.getVillagerBrain().getMood().getName();
        switch (mood) {
            case "depressed", "sad" -> add(pool, 4, "sad", "scratch_head");
            case "unhappy" -> add(pool, 2, "angry", "scratch_head", "look_around", "sad");
            case "happy", "overjoyed" -> add(pool, 2, "hum", "stretch", "cheer", "clap", "look_around");
            default -> add(pool, 2, "look_around", "stretch", "scratch_head", "hips");
        }
        switch (v.getVillagerBrain().getPersonality()) {
            case LAZY -> add(pool, 3, "yawn");
            case PEPPY -> add(pool, 2, "cheer", "hum", "clap");
            case SHY -> add(pool, 2, "shy", "look_around");
            case GRUMPY -> add(pool, 3, "angry", "hips");
            case ATHLETIC -> add(pool, 3, "stretch");
            case FLIRTY -> add(pool, 3, "flirty");
            case WITTY -> add(pool, 2, "laugh");
            case GLOOMY -> add(pool, 3, "sad");
            case CONFIDENT -> add(pool, 3, "hips");
            case ODD -> add(pool, 2, "look_around", "no", "surprised");
            case GREEDY -> add(pool, 3, "rub_hands");
            case FRIENDLY -> add(pool, 2, "wave", "hum");
            case SENSITIVE -> add(pool, 2, "sad", "shy");
            default -> {
            }
        }
        long time = v.getWorld().getTimeOfDay() % 24000L;
        if (time > 12500 || time < 500) {
            add(pool, 3, "yawn");
        }
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    private static void add(List<String> pool, int weight, String... names) {
        for (String n : names) {
            for (int i = 0; i < weight; i++) {
                pool.add(n);
            }
        }
    }
}
